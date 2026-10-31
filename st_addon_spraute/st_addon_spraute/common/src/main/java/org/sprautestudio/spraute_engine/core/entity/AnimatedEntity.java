package org.sprautestudio.spraute_engine.core.entity;

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
import org.sprautestudio.spraute_engine.core.anim.AnimationSystem;
import org.sprautestudio.spraute_engine.core.anim.ConfiguredAnimation;
import org.sprautestudio.spraute_engine.core.anim.IAnimatedObject;
import org.sprautestudio.spraute_engine.core.anim.LoopMode;
import org.sprautestudio.spraute_engine.core.anim.MovementActionType;
import org.sprautestudio.spraute_engine.runtime.Anims;

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

    private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(AnimatedEntity.class, EntityDataSerializers.STRING);

    /** Walking speed 1 in blocks a tick; a body shows a fast walk from 1.6 of it and a run from 2. */
    private static final float SPEED_1 = (float) (org.sprautestudio.spraute_engine.path.Walkers.WALK_BPS / 20);
    private static final float FAST_WALK = SPEED_1 * 1.6f, RUN = SPEED_1 * 2, STILL = 0.03f;
    /** Blinks every 2.5 to 5.5 seconds. */
    private static final int BLINK_MIN = 50, BLINK_MAX = 110;
    private static final float LOOK_SETTLED = 10, TURN_MIN = 2.5f, LOOK_DEGREES_PER_TICK = 10;
    private static final int TURN_HOLD = 2;
    private int turningShown, turnFor;
    private boolean turnLeft;
    private boolean clientBodyTurning;
    private float clientBodyYaw, clientBodyYawO;
    /** Keeps its chunk loaded, by the story's wish. */
    private boolean holdsChunk;
    private long heldChunk = Long.MIN_VALUE;

    private final AnimationSystem animations = new AnimationSystem();
    private final Map<MovementActionType, ConfiguredAnimation> overrides = new EnumMap<>(MovementActionType.class);
    public boolean canDie;
    private boolean vulnerable;
    private String movementShown = "";
    /** What the body looks at (server), null — nothing. It stays until replaced or taken off. */
    private Supplier<Vec3> lookTarget;
    private boolean lookInstant;
    private boolean bodyLocked, headLocked, noTurn;
    private float lockedBody, lockedHead;
    public boolean blinks = true;
    private int blinkIn = BLINK_MIN;

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
        entityData.define(LOOK, "");
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

    public void setLookTarget(Supplier<Vec3> target, boolean instant) {
        // taken off: it stops turning and stays looking where it looked
        if (target == null && lookTarget != null) {
            yBodyRot = yHeadRot;
            setYRot(yHeadRot);
            entityData.set(LOOK, "");
        }
        lookTarget = target;
        lookInstant = instant;
        if (target != null && !level().isClientSide) {
            try {
                Vec3 at = target.get();
                if (at != null) syncLookPoint(at);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void syncLookPoint(Vec3 at) {
        String pos = at.x + "," + at.y + "," + at.z;
        if (!pos.equals(entityData.get(LOOK))) entityData.set(LOOK, pos);
    }

    /** Where the eyes are turned (client): null — nowhere in particular. */
    public Vec3 lookPoint() {
        String[] p = entityData.get(LOOK).split(",");
        if (p.length != 3) return null;
        try {
            return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The head has turned to the target (about 10°), or there is nothing to turn to. */
    public boolean lookSettled() {
        Vec3 at = lookTarget != null ? lookTarget.get() : null;
        if (at == null) return true;
        Vec3 d = at.subtract(getEyePosition());
        float want = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(want - yHeadRot)) <= LOOK_SETTLED;
    }

    public void setBodyLocked(boolean locked) {
        bodyLocked = locked;
        lockedBody = yBodyRot;
    }

    public void setHeadLocked(boolean locked) {
        headLocked = locked;
        lockedHead = yHeadRot;
    }

    public void setHoldsChunk(boolean v) {
        holdsChunk = v;
        if (!v) releaseChunk();
    }

    private void releaseChunk() {
        if (heldChunk != Long.MIN_VALUE && level() instanceof net.minecraft.server.level.ServerLevel l) {
            var at = new net.minecraft.world.level.ChunkPos(heldChunk);
            l.setChunkForced(at.x, at.z, false);
        }
        heldChunk = Long.MIN_VALUE;
    }

    @Override
    public void remove(RemovalReason reason) {
        // only unloading with the world leaves the chunk as it is
        if (reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.UNLOADED_WITH_PLAYER) releaseChunk();
        super.remove(reason);
    }

    public void setNoTurn(boolean v) {
        noTurn = v;
    }

    /** It does not turn to the player when clicked. */
    public boolean noTurn() {
        return noTurn;
    }

    /** The clip it blinks with; subclasses give the body's own. */
    protected ConfiguredAnimation blinkAnimation() {
        return null;
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

    /** Changes when what the gaits play has to be picked anew; subclasses add what their look depends on. */
    protected String movementKey() {
        return "";
    }

    /** The clip of turning on the spot; subclasses give the body's own. */
    protected ConfiguredAnimation turnAnimation(boolean left) {
        return null;
    }

    /** Blocks a tick each gait's clip is made for: played slower or faster to match, the feet do not slide. */
    private static float gaitPace(MovementActionType type) {
        return switch (type) {
            case WALKING -> SPEED_1;
            case FAST_WALKING -> SPEED_1 * 1.75f;
            case RUNNING -> SPEED_1 * 2.4f;
            default -> 0;
        };
    }

    private float gaitRate = 1, smoothSpeed;

    public float renderedBodyYaw(float partial) {
        return clientBodyTurning ? net.minecraft.util.Mth.rotLerp(partial, clientBodyYawO, clientBodyYaw)
                : net.minecraft.util.Mth.rotLerp(partial, yBodyRotO, yBodyRot);
    }

    public float renderedHeadYaw(float partial) {
        return clientBodyTurning ? renderedBodyYaw(partial)
                : net.minecraft.util.Mth.rotLerp(partial, yHeadRotO, yHeadRot);
    }

    private void updateClientBodyYaw(Vec3 target, boolean idle) {
        if (target == null || !idle) {
            if (clientBodyTurning) {
                clientBodyYawO = clientBodyYaw;
                clientBodyYaw += net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yBodyRot - clientBodyYaw),
                        -LOOK_DEGREES_PER_TICK, LOOK_DEGREES_PER_TICK);
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(yBodyRot - clientBodyYaw)) < 1) clientBodyTurning = false;
            }
            return;
        }
        if (!clientBodyTurning) {
            clientBodyYaw = yBodyRot;
            clientBodyYawO = clientBodyYaw;
            clientBodyTurning = true;
        } else clientBodyYawO = clientBodyYaw;
        float wanted = (float) Math.toDegrees(Math.atan2(getX() - target.x, target.z - getZ()));
        clientBodyYaw += net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(wanted - clientBodyYaw),
                -LOOK_DEGREES_PER_TICK, LOOK_DEGREES_PER_TICK);
    }

    private void pickMovement() {
        float v = smoothSpeed += (getWalkingSpeed() - smoothSpeed) * 0.4f;
        MovementActionType type = v < STILL ? MovementActionType.IDLE : v < FAST_WALK ? MovementActionType.WALKING
                : v < RUN ? MovementActionType.FAST_WALKING : MovementActionType.RUNNING;
        String key = type + "|" + entityData.get(MOVES) + "|" + movementKey() + "|" + isSitting();
        if (!key.equals(movementShown)) {
            movementShown = key;
            ConfiguredAnimation ca = animationFor(type);
            // a little difference in pace, so a crowd does not march in step
            if (ca != null) startAnimation("movement", ca.loopMode(LoopMode.LOOP).transitionTime(0.2f).speed(ca.speed() * (0.95f + random.nextFloat() * 0.1f)));
            else animations.stopAnimation("movement", 0.2f);
        }
        float pace = gaitPace(type);
        float wanted = pace > 0 ? net.minecraft.util.Mth.clamp(v / pace, 0.55f, 1.7f) : 1;
        gaitRate += (wanted - gaitRate) * 0.25f;
        animations.setRate("movement", gaitRate);
        Vec3 target = lookPoint();
        updateClientBodyYaw(target, type == MovementActionType.IDLE);
        // Keep the step active while the body is still facing its look target, even between rotation packets.
        float delta = clientBodyTurning ? net.minecraft.util.Mth.wrapDegrees(clientBodyYaw - clientBodyYawO)
                : net.minecraft.util.Mth.wrapDegrees(yBodyRot - yBodyRotO);
        float remaining = target == null ? 0 : net.minecraft.util.Mth.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(getX() - target.x, target.z - getZ()))
                        - (clientBodyTurning ? clientBodyYaw : yBodyRot));
        if (type == MovementActionType.IDLE && (Math.abs(remaining) > TURN_MIN || Math.abs(delta) > TURN_MIN)) {
            turnLeft = Math.abs(remaining) > TURN_MIN ? remaining < 0 : delta < 0;
            turnFor = TURN_HOLD;
        } else if (turnFor > 0) turnFor--;
        int turning = type != MovementActionType.IDLE || turnFor <= 0 ? 0 : turnLeft ? 1 : 2;
        if (turning != turningShown) {
            turningShown = turning;
            ConfiguredAnimation turn = turning != 0 ? turnAnimation(turning == 1) : null;
            if (turn != null) startAnimation("turn", turn.loopMode(LoopMode.LOOP).transitionTime(0.2f).speed(turn.speed() * 0.75f));
            else animations.stopAnimation("turn", 0.2f);
        }
    }

    // ================================================================== life

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            pickMovement();
            return;
        }
        if (blinks && --blinkIn <= 0) {
            blinkIn = BLINK_MIN + random.nextInt(BLINK_MAX - BLINK_MIN + 1);
            ConfiguredAnimation blink = blinkAnimation();
            if (blink != null) startAnimation("EYES", blink.transitionTime(0).loopMode(LoopMode.ONCE));
        }
        if (animations.takeDirty()) entityData.set(ANIMS, animations.encode());
        if (lookTarget != null) {
            Vec3 at = null;
            try {
                at = lookTarget.get();
            } catch (RuntimeException ignored) {
                // a target that is gone just changes nothing
            }
            if (at != null) {
                Vec3 d = at.subtract(getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float step = lookInstant ? 360 : LOOK_DEGREES_PER_TICK;
                boolean walking = getNavigation().isInProgress() || org.sprautestudio.spraute_engine.path.Walkers.speedOf(this) >= 0;
                if (!bodyLocked && !walking) {
                    float next = yBodyRot + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yaw - yBodyRot), -step, step);
                    setYRot(next);
                    yBodyRot = next;
                }
                if (!headLocked) {
                    float headTarget = walking || bodyLocked
                            ? yBodyRot + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yaw - yBodyRot), -75, 75)
                            : yBodyRot;
                    yHeadRot += net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(headTarget - yHeadRot), -step, step);
                    float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
                    setXRot(getXRot() + net.minecraft.util.Mth.clamp(pitch - getXRot(), -step, step));
                }
                if (tickCount % 5 == 0) syncLookPoint(at);
            }
        }
        if (holdsChunk && level() instanceof net.minecraft.server.level.ServerLevel l && chunkPosition().toLong() != heldChunk) {
            releaseChunk();
            heldChunk = chunkPosition().toLong();
            l.setChunkForced(chunkPosition().x, chunkPosition().z, true);
        }
        if (bodyLocked) yBodyRot = yBodyRotO = lockedBody;
        if (headLocked) yHeadRot = yHeadRotO = lockedHead;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANIMS.equals(key) && level().isClientSide) animations.decode(entityData.get(ANIMS));
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
        tag.putString("SprauteAnims", animations.encodeSaved());
        tag.putBoolean("SprauteBodyLock", bodyLocked);
        tag.putBoolean("SprauteHeadLock", headLocked);
        tag.putBoolean("SprauteNoTurn", noTurn);
        tag.putBoolean("SprauteBlinks", blinks);
        tag.putString("SprauteGaits", entityData.get(MOVES));
        tag.putBoolean("SprauteChunk", holdsChunk);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        vulnerable = tag.getBoolean("SprauteVulnerable");
        canDie = tag.getBoolean("SprauteCanDie");
        setSitting(tag.getBoolean("SprauteSitting"));
        entityData.set(TEXTURE, tag.getString("SprauteTexture"));
        animations.restore(tag.getString("SprauteAnims"));
        bodyLocked = tag.getBoolean("SprauteBodyLock");
        headLocked = tag.getBoolean("SprauteHeadLock");
        noTurn = tag.getBoolean("SprauteNoTurn");
        if (tag.contains("SprauteBlinks")) blinks = tag.getBoolean("SprauteBlinks");
        entityData.set(MOVES, tag.getString("SprauteGaits"));
        holdsChunk = tag.getBoolean("SprauteChunk");
        lockedBody = yBodyRot;
        lockedHead = yHeadRot;
    }
}
