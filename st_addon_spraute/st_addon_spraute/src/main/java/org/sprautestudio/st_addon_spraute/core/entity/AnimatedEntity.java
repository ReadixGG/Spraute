package org.sprautestudio.st_addon_spraute.core.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.core.anim.AnimationSystem;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.IAnimatedObject;
import org.sprautestudio.st_addon_spraute.core.anim.LoopMode;
import org.sprautestudio.st_addon_spraute.core.anim.MovementActionType;
import org.sprautestudio.st_addon_spraute.runtime.Anims;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A story character's body: layered animations synced from the server, gait animations picked on the client
 * from how fast it really moves. A character takes no damage until the story allows it (see Hitboxes).
 */
public abstract class AnimatedEntity extends PathfinderMob implements IAnimatedObject {
    private static final EntityDataAccessor<String> ANIMS = SynchedEntityData.defineId(AnimatedEntity.class, EntityDataSerializers.STRING);
    /** Gait animations the story set ("IDLE=id;WALKING=id"). */
    private static final EntityDataAccessor<String> MOVES = SynchedEntityData.defineId(AnimatedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEXTURE = SynchedEntityData.defineId(AnimatedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(AnimatedEntity.class, EntityDataSerializers.BOOLEAN);

    /** Horizontal blocks per tick from which a body fast-walks and runs. */
    private static final float FAST_WALK = 0.32f, RUN = 0.42f, STILL = 0.012f;

    private final AnimationSystem animations = new AnimationSystem();
    private final Map<MovementActionType, ConfiguredAnimation> overrides = new EnumMap<>(MovementActionType.class);
    public boolean canDie;
    private boolean vulnerable;
    private String movementShown = "";
    /** What the body looks at (server), null — nothing. */
    private Supplier<Vec3> lookTarget;

    protected AnimatedEntity(EntityType<? extends AnimatedEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIMS, "");
        entityData.define(MOVES, "");
        entityData.define(TEXTURE, "");
        entityData.define(SITTING, false);
    }

    @Override
    public AnimationSystem getAnimationSystem() {
        return animations;
    }

    public void startAnimation(String layer, ConfiguredAnimation ca) {
        animations.startAnimationAt(layer, ca);
    }

    // ================================================================== what the story sets

    public void setMaxHealth(float hp) {
        var a = getAttribute(Attributes.MAX_HEALTH);
        if (a != null) a.setBaseValue(hp);
        setHealth(hp);
    }

    public void setVulnerable(boolean v) {
        vulnerable = v;
    }

    public void setLookTarget(Supplier<Vec3> target) {
        lookTarget = target;
    }

    public boolean isSitting() {
        return entityData.get(SITTING);
    }

    public void setSitting(boolean sit) {
        entityData.set(SITTING, sit);
    }

    public void setTextureOverride(ResourceLocation texture) {
        entityData.set(TEXTURE, texture == null ? "" : texture.toString());
    }

    public ResourceLocation textureOverride() {
        String t = entityData.get(TEXTURE);
        return t.isEmpty() ? null : ResourceLocation.tryParse(t);
    }

    public void setAnimationOverride(MovementActionType type, ConfiguredAnimation ca) {
        overrides.put(type, ca);
        syncOverrides();
    }

    public void clearAnimationOverrides() {
        overrides.clear();
        syncOverrides();
    }

    private void syncOverrides() {
        StringBuilder sb = new StringBuilder();
        overrides.forEach((k, v) -> sb.append(k.name()).append('=').append(v.animation().id()).append(';'));
        entityData.set(MOVES, sb.toString());
    }

    /** The gait's animation; subclasses give the body's own. */
    protected ConfiguredAnimation movementAnimation(MovementActionType type) {
        return null;
    }

    private ConfiguredAnimation animationFor(MovementActionType type) {
        for (String row : entityData.get(MOVES).split(";")) {
            int eq = row.indexOf('=');
            if (eq > 0 && row.substring(0, eq).equals(type.name())) {
                var a = Anims.resolve(row.substring(eq + 1));
                if (a.isPresent()) return a.get().configure();
            }
        }
        return movementAnimation(type);
    }

    /** How fast it really moves: horizontal blocks per tick. */
    public float getWalkingSpeed() {
        double dx = getX() - xo, dz = getZ() - zo;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void pickMovement() {
        float v = getWalkingSpeed();
        MovementActionType type = v < STILL ? MovementActionType.IDLE : v < FAST_WALK ? MovementActionType.WALKING
                : v < RUN ? MovementActionType.FAST_WALKING : MovementActionType.RUNNING;
        ConfiguredAnimation ca = animationFor(type);
        String id = ca != null ? ca.animation().id() : "";
        if (id.equals(movementShown)) return;
        movementShown = id;
        if (ca != null) startAnimation("movement", ca.loopMode(LoopMode.LOOP).transitionTime(0.2f));
        else animations.stopAnimation("movement", 0.2f);
    }

    // ================================================================== life

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            pickMovement();
            return;
        }
        if (animations.takeDirty()) entityData.set(ANIMS, animations.encode());
        if (lookTarget != null) {
            Vec3 at = lookTarget.get();
            if (at != null) getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANIMS.equals(key) && level().isClientSide) animations.decode(entityData.get(ANIMS));
        if (MOVES.equals(key)) movementShown = "\n";
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!vulnerable && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!canDie && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SprauteVulnerable", vulnerable);
        tag.putBoolean("SprauteCanDie", canDie);
        tag.putBoolean("SprauteSitting", isSitting());
        tag.putString("SprauteTexture", entityData.get(TEXTURE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        vulnerable = tag.getBoolean("SprauteVulnerable");
        canDie = tag.getBoolean("SprauteCanDie");
        setSitting(tag.getBoolean("SprauteSitting"));
        entityData.set(TEXTURE, tag.getString("SprauteTexture"));
    }
}
