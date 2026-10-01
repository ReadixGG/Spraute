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
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

/**
 * Lightweight world object that renders a geo model with texture and optional
 * animation file. No AI, health or combat — only visual transform + overlays.
 */
public class SprauteModelEntity extends Entity {
    public static final byte OVERLAY_NONE = SprauteNpcEntity.OVERLAY_NONE;
    public static final byte OVERLAY_ONCE = SprauteNpcEntity.OVERLAY_ONCE;
    public static final byte OVERLAY_LOOP = SprauteNpcEntity.OVERLAY_LOOP;
    public static final byte OVERLAY_FREEZE = SprauteNpcEntity.OVERLAY_FREEZE;

    private static final EntityDataAccessor<String> MODEL_RES =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEXTURE_RES =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION_RES =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> IDLE_ANIM =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OVERLAY_ANIM =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> OVERLAY_MODE =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> OVERLAY_ADD =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OVERLAY_CMD_ID =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> OVERLAY_WEIGHT =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> MODEL_ROT_X =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_ROT_Y =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_ROT_Z =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_SCALE_X =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_SCALE_Y =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_SCALE_Z =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TRANSLUCENT =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> OPACITY =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LOD_ENABLED =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RENDER_DISTANCE =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FORCE_VISIBLE =
            SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.BOOLEAN);

    public SprauteModelEntity(EntityType<? extends SprauteModelEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MODEL_RES, "");
        this.entityData.define(TEXTURE_RES, "");
        this.entityData.define(ANIMATION_RES, "");
        this.entityData.define(IDLE_ANIM, "idle");
        this.entityData.define(OVERLAY_ANIM, "");
        this.entityData.define(OVERLAY_MODE, OVERLAY_NONE);
        this.entityData.define(OVERLAY_ADD, false);
        this.entityData.define(OVERLAY_CMD_ID, 0);
        this.entityData.define(OVERLAY_WEIGHT, (byte) 255);
        this.entityData.define(MODEL_ROT_X, 0f);
        this.entityData.define(MODEL_ROT_Y, 0f);
        this.entityData.define(MODEL_ROT_Z, 0f);
        this.entityData.define(MODEL_SCALE_X, 1f);
        this.entityData.define(MODEL_SCALE_Y, 1f);
        this.entityData.define(MODEL_SCALE_Z, 1f);
        this.entityData.define(TRANSLUCENT, true);
        this.entityData.define(OPACITY, 1f);
        this.entityData.define(LOD_ENABLED, true);
        this.entityData.define(RENDER_DISTANCE, 0f);
        this.entityData.define(FORCE_VISIBLE, false);
    }

    public void setModel(String v) {
        applyAssetPath(v, SprauteResourcePath.Kind.MODEL, MODEL_RES);
    }

    public String getModel() {
        return this.entityData.get(MODEL_RES);
    }

    public void setTexture(String v) {
        applyAssetPath(v, SprauteResourcePath.Kind.TEXTURE, TEXTURE_RES);
    }

    public String getTexture() {
        return this.entityData.get(TEXTURE_RES);
    }

    public void setAnimation(String v) {
        applyAssetPath(v, SprauteResourcePath.Kind.ANIMATION, ANIMATION_RES);
    }

    public String getAnimation() {
        return this.entityData.get(ANIMATION_RES);
    }

    public void setIdleAnim(String v) {
        this.entityData.set(IDLE_ANIM, v == null ? "" : v);
    }

    public String getIdleAnim() {
        return this.entityData.get(IDLE_ANIM);
    }

    public void setModelRotation(float x, float y, float z) {
        this.entityData.set(MODEL_ROT_X, x);
        this.entityData.set(MODEL_ROT_Y, y);
        this.entityData.set(MODEL_ROT_Z, z);
    }

    public void setModelRotationX(float value) { this.entityData.set(MODEL_ROT_X, value); }
    public void setModelRotationY(float value) { this.entityData.set(MODEL_ROT_Y, value); }
    public void setModelRotationZ(float value) { this.entityData.set(MODEL_ROT_Z, value); }
    public float getModelRotationX() { return this.entityData.get(MODEL_ROT_X); }
    public float getModelRotationY() { return this.entityData.get(MODEL_ROT_Y); }
    public float getModelRotationZ() { return this.entityData.get(MODEL_ROT_Z); }

    public void setModelScale(float x, float y, float z) {
        this.entityData.set(MODEL_SCALE_X, Math.max(0.01f, x));
        this.entityData.set(MODEL_SCALE_Y, Math.max(0.01f, y));
        this.entityData.set(MODEL_SCALE_Z, Math.max(0.01f, z));
    }

    public void setModelScale(float uniform) { setModelScale(uniform, uniform, uniform); }
    public float getModelScaleX() { return this.entityData.get(MODEL_SCALE_X); }
    public float getModelScaleY() { return this.entityData.get(MODEL_SCALE_Y); }
    public float getModelScaleZ() { return this.entityData.get(MODEL_SCALE_Z); }

    public void setTranslucent(boolean value) { this.entityData.set(TRANSLUCENT, value); }
    public boolean isTranslucent() { return this.entityData.get(TRANSLUCENT); }
    public void setOpacity(float value) {
        this.entityData.set(OPACITY, net.minecraft.util.Mth.clamp(value, 0f, 1f));
    }
    public float getOpacity() { return this.entityData.get(OPACITY); }
    public boolean usesTranslucentGeo() { return isTranslucent() || getOpacity() < 0.999f; }
    public void setLodEnabled(boolean value) { this.entityData.set(LOD_ENABLED, value); }
    public boolean isLodEnabled() { return this.entityData.get(LOD_ENABLED); }
    public void setRenderDistance(float value) { this.entityData.set(RENDER_DISTANCE, Math.max(0f, value)); }
    public float getRenderDistance() { return this.entityData.get(RENDER_DISTANCE); }
    public void setForceVisible(boolean value) { this.entityData.set(FORCE_VISIBLE, value); }
    public boolean isForceVisible() { return this.entityData.get(FORCE_VISIBLE); }

    public void playOnce(String animName) { playOnce(animName, false); }
    public void playOnce(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_ONCE);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }

    public void playLoop(String animName) { playLoop(animName, false); }
    public void playLoop(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_LOOP);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }

    public void playFreeze(String animName) { playFreeze(animName, false); }
    public void playFreeze(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_FREEZE);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }

    public void stopOverlayAnimation() {
        this.entityData.set(OVERLAY_ANIM, "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_NONE);
        this.entityData.set(OVERLAY_ADD, false);
        bumpOverlayCommand();
    }

    public void stopOverlayAnimation(String animName) {
        String current = this.entityData.get(OVERLAY_ANIM);
        if (animName != null && !animName.isEmpty() && current != null && animName.equalsIgnoreCase(current)) {
            stopOverlayAnimation();
            return;
        }
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_NONE);
        this.entityData.set(OVERLAY_ADD, false);
        bumpOverlayCommand();
    }

    public String getOverlayAnim() { return this.entityData.get(OVERLAY_ANIM); }
    public byte getOverlayMode() { return this.entityData.get(OVERLAY_MODE); }
    public boolean isOverlayAdditive() { return this.entityData.get(OVERLAY_ADD); }
    public int getOverlayCommandId() { return this.entityData.get(OVERLAY_CMD_ID); }
    public float getOverlayWeight() { return (this.entityData.get(OVERLAY_WEIGHT) & 0xFF) / 255f; }

    private void bumpOverlayCommand() {
        this.entityData.set(OVERLAY_CMD_ID, this.entityData.get(OVERLAY_CMD_ID) + 1);
    }

    private void applyAssetPath(String v, SprauteResourcePath.Kind kind, EntityDataAccessor<String> accessor) {
        if (v == null || v.isEmpty()) {
            this.entityData.set(accessor, "");
            return;
        }
        SprauteResourcePath.Result check = SprauteResourcePath.parse(v, kind);
        if (!check.ok()) {
            net.minecraft.world.level.Level lv = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(this);
            if (lv != null && !lv.isClientSide()) {
                SprauteResourcePath.warnServerPlayersOnce(lv, check, getStringUUID());
            }
            return;
        }
        this.entityData.set(accessor, SprauteResourcePath.toStoragePath(check));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SprauteModel")) setModel(tag.getString("SprauteModel"));
        if (tag.contains("SprauteTexture")) setTexture(tag.getString("SprauteTexture"));
        if (tag.contains("SprauteAnimation")) setAnimation(tag.getString("SprauteAnimation"));
        if (tag.contains("SprauteIdleAnim")) setIdleAnim(tag.getString("SprauteIdleAnim"));
        if (tag.contains("SprauteRotX")) setModelRotationX(tag.getFloat("SprauteRotX"));
        if (tag.contains("SprauteRotY")) setModelRotationY(tag.getFloat("SprauteRotY"));
        if (tag.contains("SprauteRotZ")) setModelRotationZ(tag.getFloat("SprauteRotZ"));
        if (tag.contains("SprauteScaleX")) setModelScale(
                tag.getFloat("SprauteScaleX"),
                tag.contains("SprauteScaleY") ? tag.getFloat("SprauteScaleY") : 1f,
                tag.contains("SprauteScaleZ") ? tag.getFloat("SprauteScaleZ") : 1f);
        if (tag.contains("SprauteTranslucent")) setTranslucent(tag.getBoolean("SprauteTranslucent"));
        if (tag.contains("SprauteOpacity")) setOpacity(tag.getFloat("SprauteOpacity"));
        if (tag.contains("SprauteLodEnabled")) setLodEnabled(tag.getBoolean("SprauteLodEnabled"));
        if (tag.contains("SprauteRenderDistance")) setRenderDistance(tag.getFloat("SprauteRenderDistance"));
        if (tag.contains("SprauteForceVisible")) setForceVisible(tag.getBoolean("SprauteForceVisible"));
    }

    @Override
    public boolean save(CompoundTag tag) {
        if (org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(this)) return false;
        return super.save(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("SprauteModel", getModel());
        tag.putString("SprauteTexture", getTexture());
        tag.putString("SprauteAnimation", getAnimation());
        tag.putString("SprauteIdleAnim", getIdleAnim());
        tag.putFloat("SprauteRotX", getModelRotationX());
        tag.putFloat("SprauteRotY", getModelRotationY());
        tag.putFloat("SprauteRotZ", getModelRotationZ());
        tag.putFloat("SprauteScaleX", getModelScaleX());
        tag.putFloat("SprauteScaleY", getModelScaleY());
        tag.putFloat("SprauteScaleZ", getModelScaleZ());
        tag.putBoolean("SprauteTranslucent", isTranslucent());
        tag.putFloat("SprauteOpacity", getOpacity());
        tag.putBoolean("SprauteLodEnabled", isLodEnabled());
        tag.putFloat("SprauteRenderDistance", getRenderDistance());
        tag.putBoolean("SprauteForceVisible", isForceVisible());
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
