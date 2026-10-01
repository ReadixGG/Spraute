package org.zonarstudio.spraute_engine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

/**
 * Invisible, immovable scene HITBOX carrier.
 *
 * Vanilla entity collisions are push interactions, not block-like support.
 * {@code LevelCollisionMixin} exposes this entity's AABB as a VoxelShape to
 * vanilla movement collision, making it an actual solid/walkable obstacle.
 */
public final class SprauteHitboxEntity extends Entity {
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEPTH =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_X =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SOLID =
            SynchedEntityData.defineId(SprauteHitboxEntity.class, EntityDataSerializers.BOOLEAN);

    public SprauteHitboxEntity(EntityType<? extends SprauteHitboxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setInvisible(true);
        setInvulnerable(true);
        setSilent(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(WIDTH, 1f);
        entityData.define(HEIGHT, 1f);
        entityData.define(DEPTH, 1f);
        entityData.define(OFFSET_X, 0f);
        entityData.define(OFFSET_Y, 0f);
        entityData.define(OFFSET_Z, 0f);
        entityData.define(SOLID, true);
    }

    public void configure(float width, float height, float depth,
                          float offsetX, float offsetY, float offsetZ,
                          boolean solid) {
        entityData.set(WIDTH, Math.max(0.01f, width));
        entityData.set(HEIGHT, Math.max(0.01f, height));
        entityData.set(DEPTH, Math.max(0.01f, depth));
        entityData.set(OFFSET_X, offsetX);
        entityData.set(OFFSET_Y, offsetY);
        entityData.set(OFFSET_Z, offsetZ);
        entityData.set(SOLID, solid);
        refreshDimensions();
        setBoundingBox(makeBoundingBox());
    }

    public boolean isSolidCollider() {
        return entityData.get(SOLID) && isAlive();
    }

    @Override
    public void tick() {
        // Deliberately immovable. SceneRuntime updates the authored transform.
        xo = getX();
        yo = getY();
        zo = getZ();
        setDeltaMovement(0, 0, 0);
        noPhysics = true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected AABB makeBoundingBox() {
        float width = entityData.get(WIDTH);
        float height = entityData.get(HEIGHT);
        float depth = entityData.get(DEPTH);
        float offsetX = entityData.get(OFFSET_X);
        float offsetY = entityData.get(OFFSET_Y);
        float offsetZ = entityData.get(OFFSET_Z);

        double yaw = Math.toRadians(getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double centerX = getX() + offsetX * cos + offsetZ * sin;
        double centerZ = getZ() - offsetX * sin + offsetZ * cos;
        double extentX = Math.abs(cos) * width * 0.5 + Math.abs(sin) * depth * 0.5;
        double extentZ = Math.abs(sin) * width * 0.5 + Math.abs(cos) * depth * 0.5;
        double bottomY = getY() + offsetY;
        return new AABB(centerX - extentX, bottomY, centerZ - extentZ,
                centerX + extentX, bottomY + height, centerZ + extentZ);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(
                Math.max(entityData.get(WIDTH), entityData.get(DEPTH)),
                entityData.get(HEIGHT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WIDTH.equals(key) || HEIGHT.equals(key) || DEPTH.equals(key)
                || OFFSET_X.equals(key) || OFFSET_Y.equals(key) || OFFSET_Z.equals(key)) {
            refreshDimensions();
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        configure(tag.getFloat("Width"), tag.getFloat("Height"), tag.getFloat("Depth"),
                tag.getFloat("OffsetX"), tag.getFloat("OffsetY"), tag.getFloat("OffsetZ"),
                tag.getBoolean("Solid"));
    }

    @Override
    public boolean save(CompoundTag tag) {
        if (org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(this)) return false;
        return super.save(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Width", entityData.get(WIDTH));
        tag.putFloat("Height", entityData.get(HEIGHT));
        tag.putFloat("Depth", entityData.get(DEPTH));
        tag.putFloat("OffsetX", entityData.get(OFFSET_X));
        tag.putFloat("OffsetY", entityData.get(OFFSET_Y));
        tag.putFloat("OffsetZ", entityData.get(OFFSET_Z));
        tag.putBoolean("Solid", entityData.get(SOLID));
    }

    @Override
    //? if >=1.20.1 {
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
    //?} else {
    /*public Packet<?> getAddEntityPacket() {
    *///?}
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
