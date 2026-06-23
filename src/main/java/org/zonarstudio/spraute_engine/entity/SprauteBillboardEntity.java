package org.zonarstudio.spraute_engine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class SprauteBillboardEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_TEXTURE = SynchedEntityData.defineId(SprauteBillboardEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SprauteBillboardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SprauteBillboardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_SEE_THROUGH = SynchedEntityData.defineId(SprauteBillboardEntity.class, EntityDataSerializers.BOOLEAN);

    public SprauteBillboardEntity(EntityType<? extends SprauteBillboardEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = true;
    }

    public SprauteBillboardEntity(Level pLevel, double x, double y, double z, String texture, float width, float height, boolean seeThrough) {
        this(ModEntities.SPRAUTE_BILLBOARD.get(), pLevel);
        this.setPos(x, y, z);
        this.setTexture(texture);
        this.setBillboardWidth(width);
        this.setBillboardHeight(height);
        this.setSeeThrough(seeThrough);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TEXTURE, "");
        this.entityData.define(DATA_WIDTH, 1.0f);
        this.entityData.define(DATA_HEIGHT, 1.0f);
        this.entityData.define(DATA_SEE_THROUGH, false);
    }

    public String getTexture() {
        return this.entityData.get(DATA_TEXTURE);
    }

    public void setTexture(String texture) {
        this.entityData.set(DATA_TEXTURE, texture);
    }

    public float getBillboardWidth() {
        return this.entityData.get(DATA_WIDTH);
    }

    public void setBillboardWidth(float width) {
        this.entityData.set(DATA_WIDTH, width);
    }

    public float getBillboardHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public void setBillboardHeight(float height) {
        this.entityData.set(DATA_HEIGHT, height);
    }

    public boolean isSeeThrough() {
        return this.entityData.get(DATA_SEE_THROUGH);
    }

    public void setSeeThrough(boolean seeThrough) {
        this.entityData.set(DATA_SEE_THROUGH, seeThrough);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SprauteTexture")) this.setTexture(tag.getString("SprauteTexture"));
        if (tag.contains("SprauteWidth")) this.setBillboardWidth(tag.getFloat("SprauteWidth"));
        if (tag.contains("SprauteHeight")) this.setBillboardHeight(tag.getFloat("SprauteHeight"));
        if (tag.contains("SprauteSeeThrough")) this.setSeeThrough(tag.getBoolean("SprauteSeeThrough"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("SprauteTexture", this.getTexture());
        tag.putFloat("SprauteWidth", this.getBillboardWidth());
        tag.putFloat("SprauteHeight", this.getBillboardHeight());
        tag.putBoolean("SprauteSeeThrough", this.isSeeThrough());
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
