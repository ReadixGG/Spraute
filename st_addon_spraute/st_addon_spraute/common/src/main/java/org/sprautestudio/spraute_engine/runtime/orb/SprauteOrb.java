package org.sprautestudio.spraute_engine.runtime.orb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.spraute_engine.runtime.GameEvents;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A story particle like an experience orb: floats, flies to a nearby player and is picked up
 * (event "orb"), or fades out after its lifetime (event "orbexpire"). It may give coins and
 * experience. Decorative ones ("nopickup") only fly and fade.
 */
public class SprauteOrb extends Entity {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(SprauteOrb.class, EntityDataSerializers.STRING);
    /** Texture to draw: a resource location, "item:<id>", or "" for the tinted experience orb. */
    private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(SprauteOrb.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SprauteOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SprauteOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SprauteOrb.class, EntityDataSerializers.INT);

    public int value = 1, coins, xp;
    public double radius = 6;
    public boolean pickup = true, floating;
    public int age;

    public SprauteOrb(EntityType<? extends SprauteOrb> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, "orb");
        entityData.define(LOOK, "");
        entityData.define(COLOR, 0xFFFFFFFF);
        entityData.define(SIZE, 0.25f);
        entityData.define(LIFE, 1200);
    }

    public String kind() {
        return entityData.get(KIND);
    }

    public void setKind(String v) {
        entityData.set(KIND, v);
    }

    public String look() {
        return entityData.get(LOOK);
    }

    public void setLook(String v) {
        entityData.set(LOOK, v);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public void setColor(int v) {
        entityData.set(COLOR, v);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public void setSize(float v) {
        entityData.set(SIZE, v);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public void setLife(int v) {
        entityData.set(LIFE, v);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 v = getDeltaMovement();
        if (!floating && !isNoGravity()) v = v.add(0, -0.02, 0);
        if (isInWater()) v = new Vec3(v.x * 0.9, v.y + 0.01, v.z * 0.9);
        if (!level().isClientSide) {
            int life = life();
            if (life > 0 && age > life) {
                if (pickup) GameEvents.orb("orbexpire", null, this);
                discard();
                return;
            }
            if (pickup && age > 10) {
                Player p = level().getNearestPlayer(this, radius);
                if (p != null && !p.isSpectator()) {
                    Vec3 to = new Vec3(p.getX() - getX(), p.getY() + p.getEyeHeight() / 2 - getY(), p.getZ() - getZ());
                    double d = to.length();
                    if (d < 0.7) {
                        collect(p);
                        return;
                    }
                    double pull = 1 - d / radius;
                    v = v.add(to.normalize().scale(pull * pull * 0.12));
                }
            }
        }
        setDeltaMovement(v);
        move(MoverType.SELF, getDeltaMovement());
        float drag = onGround() ? 0.7f : floating ? 0.9f : 0.98f;
        setDeltaMovement(getDeltaMovement().multiply(drag, floating ? 0.9 : 0.98, drag));
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, -0.5, 1));
    }

    private void collect(Player p) {
        if (coins > 0) {
            try {
                if (p instanceof ServerPlayer owner) org.sprautestudio.spraute_engine.core.system.Coins.add(owner, coins);
            } catch (Throwable ignored) {
            }
        }
        if (xp > 0) p.giveExperiencePoints(xp);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1f,
                0.5f * ((random.nextFloat() - random.nextFloat()) * 0.7f + 1.8f));
        if (p instanceof ServerPlayer sp) GameEvents.orb("orb", sp, this);
        discard();
    }

    /** Event data of this orb. */
    public Map<String, Object> eventData() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", kind());
        d.put("amount", (double) value);
        d.put("count", (double) coins);
        d.put("level", (double) xp);
        d.put("x", Math.floor(getX()));
        d.put("y", Math.floor(getY()));
        d.put("z", Math.floor(getZ()));
        return d;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        setKind(t.getString("kind"));
        setLook(t.getString("look"));
        setColor(t.getInt("color"));
        setSize(t.getFloat("size"));
        setLife(t.getInt("life"));
        value = t.getInt("value");
        coins = t.getInt("coins");
        xp = t.getInt("xp");
        radius = t.getDouble("radius");
        pickup = t.getBoolean("pickup");
        floating = t.getBoolean("floating");
        age = t.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putString("kind", kind());
        t.putString("look", look());
        t.putInt("color", color());
        t.putFloat("size", size());
        t.putInt("life", life());
        t.putInt("value", value);
        t.putInt("coins", coins);
        t.putInt("xp", xp);
        t.putDouble("radius", radius);
        t.putBoolean("pickup", pickup);
        t.putBoolean("floating", floating);
        t.putInt("age", age);
    }
}
