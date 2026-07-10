package org.zonarstudio.spraute_engine.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class SprauteNpcEntity extends PathfinderMob {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ========== Synced data (model/texture/animation file) ==========
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> MODEL_RES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TEXTURE_RES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> ANIMATION_RES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** UUID игрока, чей скин накладывается на geo-модель (клиент). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> PLAYER_SKIN_OVERLAY =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

    // ========== Synced data (head bone) ==========
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HEAD_LOOK_ACTIVE =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Synced world-space yaw (degrees) toward look target on horizontal plane; client subtracts rendered body yaw for bone. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HEAD_LOOK_YAW =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HEAD_LOOK_PITCH =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> HEAD_BONE_NAME =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** Bumped when look target/mode changes so client can reset head smoothing (alwaysLookAt A→B without stopLook). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> HEAD_LOOK_TARGET_GEN =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HEAD_LOOK_UNLIMITED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SYNCED_BODY_YAW =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ALWAYS_LOOK_ACTIVE =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    // ========== Overlay animation (additive layer) ==========
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> OVERLAY_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> OVERLAY_MODE =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> OVERLAY_ADD =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> OVERLAY_CMD_ID =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** Blend weight for overlay (0=no overlay, 255=full). Allows smooth additive blend. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> OVERLAY_WEIGHT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** Procedural additive weight (breathing, hand shake): 0=off, 255=full. Lerp for smooth blend. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> ADDITIVE_WEIGHT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    // ========== Auto idle/walk animations ==========
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> IDLE_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> WALK_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_FLYING =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> FLY_IDLE_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> FLY_WALK_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_SWIMMING_SCRIPT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> SWIM_IDLE_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> SWIM_WALK_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DEATH_ANIM =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    /** Synced moving state so client can play walk/idle without server round-trip lag. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_MOVING_SYNCED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HAS_COLLISION =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public static final byte OVERLAY_NONE = 0;
    public static final byte OVERLAY_ONCE = 1;
    public static final byte OVERLAY_LOOP = 2;
    public static final byte OVERLAY_FREEZE = 3;

    // ========== Pickup ==========
    private final SimpleContainer pickupContainer = new SimpleContainer(9);
    private java.util.UUID pickupDropperFilter = null;
    private String pickupMaxItemId = null;
    private String pickupMaxTag = null;
    private int pickupMaxCount = -1;
    private java.util.UUID lastPickupThrower = null;

    // ========== Look ==========
    private net.minecraft.world.entity.Entity lookEntity = null;
    private net.minecraft.world.phys.Vec3 lookPoint = null;
    private boolean lookActive = false;

    private static final float BODY_TURN_SPEED = 6f;
    private static final int BODY_START_DELAY_TICKS = 6;
    private int bodyDelayTicks = 0;

    /** Max head yaw relative to body (°); synced to client — do not exceed natural neck range. */
    public static final float MAX_HEAD_YAW = 95f;
    public static final float MAX_HEAD_PITCH_UP = 40f;
    public static final float MAX_HEAD_PITCH_DOWN = 45f;

    /** When true, head rotation limits are disabled (full 360° look). Set via script setHeadLookUnlimited(). */
    public void setHeadLookUnlimited(boolean unlimited) {
        this.entityData.set(HEAD_LOOK_UNLIMITED, unlimited);
    }
    public boolean isHeadLookUnlimited() {
        return this.entityData.get(HEAD_LOOK_UNLIMITED);
    }

    /** Hysteresis for walk/idle: avoid flickering when limbSwingAmount hovers near threshold. */
    private int movingStateTicks = 0;
    private static final int MOVING_DEBOUNCE = 3;

    /**
     * Body yaw we authored at end of last tick. Cannot rely on yBodyRotO after super.tick():
     * LivingEntity rewrites yBodyRotO in while-loops to pair with vanilla-corrupted yBodyRot.
     */
    private float sprauteBodyYawEndOfTick = 0f;
    private boolean sprauteBodyYawHasEndOfTick = false;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HITBOX_WIDTH =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HITBOX_HEIGHT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HITBOX_OFFSET_X =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HITBOX_OFFSET_Y =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HITBOX_OFFSET_Z =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    /** Compact bone hitbox list for client debug: id|bone|w|h|d|ox|oy|oz;... */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> BONE_HITBOXES_DATA =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SHOW_HITBOX_DEBUG =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Extra Y offset applied to the rendered name tag (in blocks). Default 0. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> NAME_Y_OFFSET =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    private final java.util.List<NpcBoneHitbox> boneHitboxes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private net.minecraft.world.phys.AABB cachedHitboxBounds = null;

    public final java.util.Map<String, Object> customData = new java.util.concurrent.ConcurrentHashMap<>();

    public final java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> customDrops = new java.util.concurrent.CopyOnWriteArrayList<>();

    public String[] uiRenderBones = null;

    /** Имя группы, к которой принадлежит этот НПС (null если не в группе). */
    private String groupName = null;
    public String getGroupName() { return groupName; }
    public void setGroupName(String name) { this.groupName = name; }

    private static final double SEPARATION_RADIUS = 1.1;
    private static final double SEPARATION_STRENGTH = 0.12;

    /** Max horizontal leg for one ground path request (avoids pathfinder edge oscillation). */
    private static final double GROUND_STEP_HORIZONTAL = 12.0;
    private static final int GROUND_PATH_RECALC_COOLDOWN = 20;
    private static final int MAX_FORCED_PATH_CHUNKS = 10;
    private static final int MOVE_STUCK_THRESHOLD = 60;

    private int groundPathRecalcCooldown = 0;
    private Vec3 activeMoveGoal = null;
    /** When false during scripted moveTo/alwaysMoveTo, body keeps its yaw instead of turning toward the path. */
    private boolean walkFaceDirection = true;
    private final java.util.Set<ChunkPos> forcedPathChunks = new java.util.HashSet<>();
    private boolean pathFailedFlag = false;
    private int moveStuckTicks = 0;
    private int walkLogCooldown = 0;
    private double moveProgressAnchorX;
    private double moveProgressAnchorZ;

    public SprauteNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODEL_RES, "geo/defender.geo.json");
        this.entityData.define(TEXTURE_RES, "textures/entity/npc/npc_default.png");
        this.entityData.define(ANIMATION_RES, "animations/npc_classic.animation.json");
        this.entityData.define(PLAYER_SKIN_OVERLAY, "");
        this.entityData.define(HEAD_LOOK_ACTIVE, false);
        this.entityData.define(HEAD_LOOK_YAW, 0f);
        this.entityData.define(HEAD_LOOK_PITCH, 0f);
        this.entityData.define(HEAD_BONE_NAME, "Head");
        this.entityData.define(HEAD_LOOK_TARGET_GEN, 0);
        this.entityData.define(HEAD_LOOK_UNLIMITED, false);
        this.entityData.define(SYNCED_BODY_YAW, 0f);
        this.entityData.define(ALWAYS_LOOK_ACTIVE, false);
        this.entityData.define(OVERLAY_ANIM, "");
        this.entityData.define(OVERLAY_MODE, OVERLAY_NONE);
        this.entityData.define(OVERLAY_ADD, false);
        this.entityData.define(OVERLAY_CMD_ID, 0);
        this.entityData.define(OVERLAY_WEIGHT, (byte) 255);
        this.entityData.define(ADDITIVE_WEIGHT, (byte) 255);
        this.entityData.define(IDLE_ANIM, "idle");
        this.entityData.define(WALK_ANIM, "walk");
        this.entityData.define(IS_FLYING, false);
        this.entityData.define(FLY_IDLE_ANIM, "fly_idle");
        this.entityData.define(FLY_WALK_ANIM, "fly");
        this.entityData.define(IS_SWIMMING_SCRIPT, false);
        this.entityData.define(SWIM_IDLE_ANIM, "swim_idle");
        this.entityData.define(SWIM_WALK_ANIM, "swim");
        this.entityData.define(DEATH_ANIM, "death");
        this.entityData.define(IS_MOVING_SYNCED, false);
        this.entityData.define(HAS_COLLISION, true);
        this.entityData.define(HITBOX_WIDTH, 0.6f);
        this.entityData.define(HITBOX_HEIGHT, 1.8f);
        this.entityData.define(HITBOX_OFFSET_X, 0f);
        this.entityData.define(HITBOX_OFFSET_Y, 0f);
        this.entityData.define(HITBOX_OFFSET_Z, 0f);
        this.entityData.define(BONE_HITBOXES_DATA, "");
        this.entityData.define(SHOW_HITBOX_DEBUG, false);
        this.entityData.define(NAME_Y_OFFSET, 0f);
    }

    // ========== Model/Texture/Animation resources ==========
    public void setModel(String v) {
        net.minecraft.world.level.Level lv = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(this);
        if (v != null && !v.isEmpty() && lv != null && !lv.isClientSide()) {
            org.zonarstudio.spraute_engine.util.SprauteResourcePath.Result check =
                    org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.MODEL);
            if (!check.ok()) {
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.warnServerPlayersOnce(lv, check, getStringUUID());
                return;
            }
        }
        this.entityData.set(MODEL_RES, v);
    }
    public String getModel() { return this.entityData.get(MODEL_RES); }
    public void setTexture(String v) {
        net.minecraft.world.level.Level lv = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(this);
        if (v != null && !v.isEmpty() && !v.startsWith("player_skin:") && lv != null && !lv.isClientSide()) {
            org.zonarstudio.spraute_engine.util.SprauteResourcePath.Result check =
                    org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.TEXTURE);
            if (!check.ok()) {
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.warnServerPlayersOnce(lv, check, getStringUUID());
                return;
            }
        }
        this.entityData.set(TEXTURE_RES, v);
    }
    public String getTexture() { return this.entityData.get(TEXTURE_RES); }
    public void setPlayerSkinOverlay(java.util.UUID playerUuid) {
        this.entityData.set(PLAYER_SKIN_OVERLAY, playerUuid != null ? playerUuid.toString() : "");
    }
    public void clearPlayerSkinOverlay() {
        this.entityData.set(PLAYER_SKIN_OVERLAY, "");
    }
    public String getPlayerSkinOverlayUuid() {
        return this.entityData.get(PLAYER_SKIN_OVERLAY);
    }
    public void setAnimation(String v) {
        net.minecraft.world.level.Level lv = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(this);
        if (v != null && !v.isEmpty() && lv != null && !lv.isClientSide()) {
            org.zonarstudio.spraute_engine.util.SprauteResourcePath.Result check =
                    org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.ANIMATION);
            if (!check.ok()) {
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.warnServerPlayersOnce(lv, check, getStringUUID());
                return;
            }
        }
        this.entityData.set(ANIMATION_RES, v);
    }
    public String getAnimation() { return this.entityData.get(ANIMATION_RES); }

    public void setHitbox(float width, float height) {
        setHitbox(width, height, getHitboxOffsetX(), getHitboxOffsetY(), getHitboxOffsetZ());
    }

    public void setHitbox(float width, float height, float offsetX, float offsetY, float offsetZ) {
        this.entityData.set(HITBOX_WIDTH, Math.max(0.05f, width));
        this.entityData.set(HITBOX_HEIGHT, Math.max(0.05f, height));
        this.entityData.set(HITBOX_OFFSET_X, offsetX);
        this.entityData.set(HITBOX_OFFSET_Y, offsetY);
        this.entityData.set(HITBOX_OFFSET_Z, offsetZ);
        invalidateHitboxCache();
        this.refreshDimensions();
    }

    public void setHitboxOffset(float offsetX, float offsetY, float offsetZ) {
        this.entityData.set(HITBOX_OFFSET_X, offsetX);
        this.entityData.set(HITBOX_OFFSET_Y, offsetY);
        this.entityData.set(HITBOX_OFFSET_Z, offsetZ);
        invalidateHitboxCache();
        this.refreshDimensions();
    }

    public void resetHitbox() {
        setHitbox(0.6f, 1.8f, 0f, 0f, 0f);
    }

    public void setHitboxPreset(String preset) {
        if (preset == null) return;
        switch (preset.toLowerCase(java.util.Locale.ROOT)) {
            case "small" -> setHitbox(NpcHitboxUtil.PRESET_SMALL_W, NpcHitboxUtil.PRESET_SMALL_H);
            case "large", "big" -> setHitbox(NpcHitboxUtil.PRESET_LARGE_W, NpcHitboxUtil.PRESET_LARGE_H);
            default -> setHitbox(NpcHitboxUtil.PRESET_PLAYER_W, NpcHitboxUtil.PRESET_PLAYER_H);
        }
    }

    public float getHitboxWidth() { return this.entityData.get(HITBOX_WIDTH); }
    public float getHitboxHeight() { return this.entityData.get(HITBOX_HEIGHT); }
    public float getHitboxOffsetX() { return this.entityData.get(HITBOX_OFFSET_X); }
    public float getHitboxOffsetY() { return this.entityData.get(HITBOX_OFFSET_Y); }
    public float getHitboxOffsetZ() { return this.entityData.get(HITBOX_OFFSET_Z); }

    public float getNameYOffset() { return this.entityData.get(NAME_Y_OFFSET); }
    public void setNameYOffset(float v) { this.entityData.set(NAME_Y_OFFSET, v); }

    public java.util.List<NpcBoneHitbox> getBoneHitboxes() { return java.util.Collections.unmodifiableList(boneHitboxes); }

    public NpcBoneHitbox addBoneHitbox(String boneName, float width, float height, float depth) {
        return addBoneHitbox(boneName, boneName, width, height, depth, 0f, 0f, 0f);
    }

    public NpcBoneHitbox addBoneHitbox(String id, String boneName, float width, float height, float depth,
                                       float offsetX, float offsetY, float offsetZ) {
        NpcBoneHitbox hb = new NpcBoneHitbox(id, boneName, width, height, depth, offsetX, offsetY, offsetZ);
        boneHitboxes.removeIf(b -> b.id.equalsIgnoreCase(hb.id));
        boneHitboxes.add(hb);
        syncBoneHitboxesData();
        invalidateHitboxCache();
        refreshDimensions();
        return hb;
    }

    public boolean removeBoneHitbox(String id) {
        boolean removed = boneHitboxes.removeIf(b -> b.id.equalsIgnoreCase(id));
        if (removed) {
            syncBoneHitboxesData();
            invalidateHitboxCache();
            refreshDimensions();
        }
        return removed;
    }

    public void clearBoneHitboxes() {
        if (boneHitboxes.isEmpty()) return;
        boneHitboxes.clear();
        syncBoneHitboxesData();
        invalidateHitboxCache();
        refreshDimensions();
    }

    public void setShowHitboxDebug(boolean show) { this.entityData.set(SHOW_HITBOX_DEBUG, show); }
    public boolean isShowHitboxDebug() { return this.entityData.get(SHOW_HITBOX_DEBUG); }

    private void invalidateHitboxCache() {
        cachedHitboxBounds = null;
    }

    private void syncBoneHitboxesData() {
        if (boneHitboxes.isEmpty()) {
            this.entityData.set(BONE_HITBOXES_DATA, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (NpcBoneHitbox hb : boneHitboxes) {
            if (sb.length() > 0) sb.append(';');
            sb.append(hb.id).append('|').append(hb.boneName).append('|')
                    .append(hb.width).append('|').append(hb.height).append('|').append(hb.depth).append('|')
                    .append(hb.offsetX).append('|').append(hb.offsetY).append('|').append(hb.offsetZ);
        }
        this.entityData.set(BONE_HITBOXES_DATA, sb.toString());
    }

    private void loadBoneHitboxesFromSync() {
        if (!SprauteEntityCompat.level(this).isClientSide) return;
        String data = this.entityData.get(BONE_HITBOXES_DATA);
        boneHitboxes.clear();
        if (data == null || data.isEmpty()) return;
        for (String part : data.split(";")) {
            String[] f = part.split("\\|");
            if (f.length < 8) continue;
            try {
                boneHitboxes.add(new NpcBoneHitbox(
                        f[0], f[1],
                        Float.parseFloat(f[2]), Float.parseFloat(f[3]), Float.parseFloat(f[4]),
                        Float.parseFloat(f[5]), Float.parseFloat(f[6]), Float.parseFloat(f[7])
                ));
            } catch (NumberFormatException ignored) {}
        }
    }

    public String getBoneHitboxesData() { return this.entityData.get(BONE_HITBOXES_DATA); }

    private void tickHitboxBounds() {
        net.minecraft.world.phys.AABB bounds = NpcHitboxUtil.mainHitbox(
                this, getHitboxWidth(), getHitboxHeight(),
                getHitboxOffsetX(), getHitboxOffsetY(), getHitboxOffsetZ());

        if (!boneHitboxes.isEmpty()) {
            var server = SprauteEntityCompat.level(this).getServer();
            if (server != null) {
                org.zonarstudio.spraute_engine.core.model.SpModelInstance instance =
                        NpcBonePoseSolver.solve(this, server.getResourceManager());
                if (instance != null) {
                    for (NpcBoneHitbox hb : boneHitboxes) {
                        org.zonarstudio.spraute_engine.core.math.SpMatrix4 matrix = instance.getBoneMatrix(hb.boneName);
                        if (matrix != null) {
                            bounds = NpcHitboxUtil.union(bounds, NpcHitboxUtil.boneHitbox(this, matrix, hb));
                        }
                    }
                }
            }
        }
        cachedHitboxBounds = bounds;
        this.setBoundingBox(bounds);
    }

    // ========== Overlay animation (additive layer) ==========
    /**
     * Play overlay animation once.
     * @param additive if true, weighted samples are added on top of idle/walk (use for delta-only clips).
     *                 if false, each bone in this clip is lerped toward the keyframe (use when keyframes are
     *                 absolute poses — avoids summing the same mouth/spine bone with idle).
     */
    public void playOnce(String animName) {
        playOnce(animName, true);
    }
    public void playOnce(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_ONCE);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }
    /** Play overlay animation in loop. See {@link #playOnce(String, boolean)} for {@code additive}. */
    public void playLoop(String animName) {
        playLoop(animName, true);
    }
    public void playLoop(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_LOOP);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }
    /** Play overlay once and hold last frame. See {@link #playOnce(String, boolean)} for {@code additive}. */
    public void playFreeze(String animName) {
        playFreeze(animName, true);
    }
    public void playFreeze(String animName, boolean additive) {
        this.entityData.set(OVERLAY_ANIM, animName != null ? animName : "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_FREEZE);
        this.entityData.set(OVERLAY_ADD, additive);
        bumpOverlayCommand();
    }
    /** Stop overlay animation. */
    public void stopOverlayAnimation() {
        this.entityData.set(OVERLAY_ANIM, "");
        this.entityData.set(OVERLAY_MODE, OVERLAY_NONE);
        this.entityData.set(OVERLAY_ADD, false);
        bumpOverlayCommand();
    }
    /** Stop overlay animation only if it matches animName (for npcid.stop("animname")). */
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
    /** 0-1 blend weight for overlay (1=full). */
    public void setOverlayWeight(float w) { this.entityData.set(OVERLAY_WEIGHT, (byte) net.minecraft.util.Mth.clamp((int)(w * 255), 0, 255)); }
    public float getOverlayWeight() { return (this.entityData.get(OVERLAY_WEIGHT) & 0xFF) / 255f; }
    /** 0-1 procedural additive weight (breathing, hand shake). Lerp for smooth on/off. */
    public void setAdditiveWeight(float w) { this.entityData.set(ADDITIVE_WEIGHT, (byte) net.minecraft.util.Mth.clamp((int)(w * 255), 0, 255)); }
    public float getAdditiveWeight() { return (this.entityData.get(ADDITIVE_WEIGHT) & 0xFF) / 255f; }

    private void bumpOverlayCommand() {
        this.entityData.set(OVERLAY_CMD_ID, this.entityData.get(OVERLAY_CMD_ID) + 1);
    }

    // ========== Auto idle/walk animations ==========
    public void setIdleAnim(String name) { this.entityData.set(IDLE_ANIM, name != null ? name : ""); }
    public String getIdleAnim() { return this.entityData.get(IDLE_ANIM); }
    public void setWalkAnim(String name) { this.entityData.set(WALK_ANIM, name != null ? name : ""); }
    public String getWalkAnim() { return this.entityData.get(WALK_ANIM); }
    public boolean isMovingSynced() { return this.entityData.get(IS_MOVING_SYNCED); }

    public boolean isFlying() { return this.entityData.get(IS_FLYING); }
    public void setFlying(boolean flying) {
        if (!flying) {
            stopMove();
        }
        this.entityData.set(IS_FLYING, flying);
        this.setNoGravity(flying);
        if (flying) {
            this.moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl(this, 20, true);
            this.navigation = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, SprauteEntityCompat.level(this));
        } else {
            this.moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            this.navigation = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, SprauteEntityCompat.level(this));
        }
    }

    public String getFlyIdleAnim() { return this.entityData.get(FLY_IDLE_ANIM); }
    public void setFlyIdleAnim(String name) { this.entityData.set(FLY_IDLE_ANIM, name != null ? name : ""); }
    
    public String getFlyWalkAnim() { return this.entityData.get(FLY_WALK_ANIM); }
    public void setFlyWalkAnim(String name) { this.entityData.set(FLY_WALK_ANIM, name != null ? name : ""); }

    public boolean isSwimmingScript() { return this.entityData.get(IS_SWIMMING_SCRIPT); }
    public void setSwimmingScript(boolean swimming) {
        this.entityData.set(IS_SWIMMING_SCRIPT, swimming);
        if (swimming) {
            this.moveControl = new net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
            this.navigation = new net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation(this, SprauteEntityCompat.level(this));
        } else if (!isFlying()) {
            this.moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
            this.navigation = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, SprauteEntityCompat.level(this));
        }
    }

    public String getSwimIdleAnim() { return this.entityData.get(SWIM_IDLE_ANIM); }
    public void setSwimIdleAnim(String name) { this.entityData.set(SWIM_IDLE_ANIM, name != null ? name : ""); }
    
    public String getSwimWalkAnim() { return this.entityData.get(SWIM_WALK_ANIM); }
    public void setSwimWalkAnim(String name) { this.entityData.set(SWIM_WALK_ANIM, name != null ? name : ""); }

    public String getDeathAnim() { return this.entityData.get(DEATH_ANIM); }
    public void setDeathAnim(String name) { this.entityData.set(DEATH_ANIM, name != null ? name : ""); }

    // ========== Collision ==========
    public void setHasCollision(boolean col) { this.entityData.set(HAS_COLLISION, col); }
    public boolean getHasCollision() { return this.entityData.get(HAS_COLLISION); }

    @Override
    public boolean isPushable() {
        return getHasCollision() && super.isPushable();
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        if (getHasCollision()) super.doPush(entity);
    }

    // ========== Look control ==========

    private void bumpHeadLookTargetGen() {
        this.entityData.set(HEAD_LOOK_TARGET_GEN, this.entityData.get(HEAD_LOOK_TARGET_GEN) + 1);
    }

    private void applySyncedHeadLook(float targetYaw, float targetPitch) {
        this.entityData.set(HEAD_LOOK_ACTIVE, true);
        this.entityData.set(HEAD_LOOK_YAW, net.minecraft.util.Mth.wrapDegrees(targetYaw));
        this.entityData.set(HEAD_LOOK_PITCH, targetPitch);
    }

    /** Clamp look direction so head never turns past {@link #MAX_HEAD_YAW} / pitch limits vs current body.
     *  If head look is unlimited, skips clamping entirely. */
    private void applySyncedHeadLookClamped(float targetWorldYaw, float targetPitch) {
        if (isHeadLookUnlimited()) {
            applySyncedHeadLook(targetWorldYaw, targetPitch);
            return;
        }
        float neckDelta = net.minecraft.util.Mth.degreesDifference(this.yBodyRot, targetWorldYaw);
        neckDelta = net.minecraft.util.Mth.clamp(neckDelta, -MAX_HEAD_YAW, MAX_HEAD_YAW);
        float yaw = net.minecraft.util.Mth.wrapDegrees(this.yBodyRot + neckDelta);
        float pitch = net.minecraft.util.Mth.clamp(targetPitch, -MAX_HEAD_PITCH_UP, MAX_HEAD_PITCH_DOWN);
        applySyncedHeadLook(yaw, pitch);
    }

    private void clearSyncedHeadLook() {
        this.entityData.set(HEAD_LOOK_ACTIVE, false);
        this.entityData.set(HEAD_LOOK_YAW, net.minecraft.util.Mth.wrapDegrees(this.yBodyRot));
        this.entityData.set(HEAD_LOOK_PITCH, 0f);
    }

    /**
     * After script/API mutates {@link #lookEntity} / {@link #lookPoint} / {@link #lookActive}, push yaw/pitch so
     * watchers never get a new {@link #HEAD_LOOK_TARGET_GEN} with stale {@link #HEAD_LOOK_YAW} for a full tick.
     */
    private void refreshSyncedHeadLookNow() {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        Vec3 target = resolveLookTarget();
        if (target != null) {
            applySyncedHeadLookClamped(
                calcTargetYaw(target.x, target.z),
                calcTargetPitch(target.y, target.x, target.z));
        } else {
            clearSyncedHeadLook();
        }
    }

    /**
     * After {@code super.tick()} we restore {@link #yBodyRot} from {@code bodyStart}, but vanilla already ran its
     * range-alignment while-loops for {@code *O} vs its temporary {@code yBodyRot}. Re-run the same pairing for our
     * final yaws so client {@link net.minecraft.util.Mth#rotLerp} uses the shortest arc — without forcing
     * {@code yBodyRotO == yBodyRot} (that collapses inter-tick lerp and causes a sharp whole-model snap).
     */
    private void fixBodyYawSamplingContinuity() {
        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }
        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }
        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }
        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }
    }

    private static boolean lookPointChanged(Vec3 prev, double x, double y, double z) {
        if (prev == null) return true;
        double dx = x - prev.x, dy = y - prev.y, dz = z - prev.z;
        return dx * dx + dy * dy + dz * dz > 1.0e-6;
    }

    /** Look at a point once (body turns, then stops). */
    public void lookAt(double x, double y, double z) {
        if (lookEntity != null || lookPointChanged(lookPoint, x, y, z)) bumpHeadLookTargetGen();
        lookEntity = null;
        lookPoint = new Vec3(x, y, z);
        lookActive = true;
        bodyDelayTicks = BODY_START_DELAY_TICKS;
        this.entityData.set(ALWAYS_LOOK_ACTIVE, true);
        refreshSyncedHeadLookNow();
    }

    /**
     * One-time glance at another entity: captures their eye position now and does not follow them if they move
     * (unlike {@link #alwaysLookAtEntity}).
     */
    public void lookAtEntity(net.minecraft.world.entity.Entity e) {
        if (e == null || !e.isAlive()) return;
        lookAt(e.getX(), e.getEyeY(), e.getZ());
    }

    /** Continuously look at a point. */
    public void alwaysLookAt(double x, double y, double z) {
        alwaysLookAt(x, y, z, true);
    }
    public void alwaysLookAt(double x, double y, double z, boolean head) {
        boolean switchingFromEntity = lookEntity != null;
        boolean hadPoint = lookPoint != null;
        boolean targetChanged = switchingFromEntity || !hadPoint || lookPointChanged(lookPoint, x, y, z);
        if (targetChanged) bumpHeadLookTargetGen();
        lookEntity = null;
        lookPoint = new Vec3(x, y, z);
        lookActive = true;
        // Continuous point tracking (e.g. "look forward" while flying) must not reset body delay every tick.
        if (switchingFromEntity) {
            bodyDelayTicks = 0;
        } else if (!hadPoint) {
            bodyDelayTicks = BODY_START_DELAY_TICKS;
        }
        this.entityData.set(ALWAYS_LOOK_ACTIVE, true);
        refreshSyncedHeadLookNow();
    }

    /** Continuously look at an entity (player/npc/mob). */
    public void alwaysLookAtEntity(net.minecraft.world.entity.Entity e) {
        alwaysLookAtEntity(e, true);
    }
    public void alwaysLookAtEntity(net.minecraft.world.entity.Entity e, boolean head) {
        boolean targetChanged = lookEntity != e || lookPoint != null;
        if (targetChanged) bumpHeadLookTargetGen();
        lookEntity = e;
        lookPoint = null;
        lookActive = true;
        // Script may call this every tick — do not reset body delay or the torso never turns.
        if (targetChanged) {
            bodyDelayTicks = 0;
        }
        this.entityData.set(ALWAYS_LOOK_ACTIVE, true);
        refreshSyncedHeadLookNow();
    }

    /** Head-only look at entity. Kept for backward compat, delegates to alwaysLookAtEntity. */
    public void headLookAt(net.minecraft.world.entity.Entity t, boolean bodyFollow) {
        alwaysLookAtEntity(t, true);
    }

    /** Stop all look tracking. */
    public void stopLook() {
        if (lookActive || lookEntity != null || lookPoint != null) bumpHeadLookTargetGen();
        lookEntity = null;
        lookPoint = null;
        lookActive = false;
        bodyDelayTicks = 0;
        this.entityData.set(ALWAYS_LOOK_ACTIVE, false);
        clearSyncedHeadLook();
    }
    public void stopHeadLook() { stopLook(); }

    public void setHeadBone(String n) { this.entityData.set(HEAD_BONE_NAME, (n == null || n.isEmpty()) ? "Head" : n); }
    public boolean isHeadLookActive() { return this.entityData.get(HEAD_LOOK_ACTIVE); }
    public int getHeadLookTargetGen() { return this.entityData.get(HEAD_LOOK_TARGET_GEN); }
    /** World yaw (degrees) toward look target; use with local body yaw on client to get bone offset. */
    public float getHeadLookYaw() { return this.entityData.get(HEAD_LOOK_YAW); }
    public float getHeadLookPitch() { return this.entityData.get(HEAD_LOOK_PITCH); }
    public String getHeadBoneName() { return this.entityData.get(HEAD_BONE_NAME); }

    // ========== Hand items ==========
    public void setHandItem(String hand, net.minecraft.world.item.ItemStack item) {
        this.setItemSlot(hand.equalsIgnoreCase("left") ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND, item);
    }
    public net.minecraft.world.item.ItemStack getHandItem(String hand) {
        return this.getItemBySlot(hand.equalsIgnoreCase("left") ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }
    public void clearHandItem(String hand) { setHandItem(hand, net.minecraft.world.item.ItemStack.EMPTY); }

    /** Throw item entity in the NPC's look direction (does not consume inventory). */
    public boolean throwItem(String itemStr, int count) {
        if (count <= 0) count = 1;
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide) return false;

        String resolved = itemStr.contains(":") ? itemStr : "minecraft:" + itemStr;
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(parseItemResourceId(resolved));
        if (item == null || item == net.minecraft.world.item.Items.AIR) return false;

        spawnThrownEntity(serverLevel, new net.minecraft.world.item.ItemStack(item, count));
        return true;
    }

    private void spawnThrownEntity(ServerLevel level, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity itemEntity = new ItemEntity(level, getX(), getY() + 1.0, getZ(), stack);
        itemEntity.setDefaultPickUpDelay();
        float yaw = getYRot() * ((float) Math.PI / 180F);
        float pitch = getXRot() * ((float) Math.PI / 180F);
        float tx = -net.minecraft.util.Mth.sin(yaw) * net.minecraft.util.Mth.cos(pitch);
        float tz = net.minecraft.util.Mth.cos(yaw) * net.minecraft.util.Mth.cos(pitch);
        float ty = -net.minecraft.util.Mth.sin(pitch);
        itemEntity.setDeltaMovement(tx * 0.3F, ty * 0.3F + 0.1F, tz * 0.3F);
        level.addFreshEntity(itemEntity);
    }

    // ========== Pickup control ==========
    public void setPickupDropperFilter(java.util.UUID uuid) { pickupDropperFilter = uuid; }
    public void clearPickupDropperFilter() { pickupDropperFilter = null; }
    public void setPickupMaxCount(String itemId, int max) { setPickupMaxCount(itemId, null, max); }
    public void setPickupMaxCount(String itemId, String nbtTag, int max) {
        pickupMaxItemId = max < 0 ? null : itemId;
        pickupMaxTag = max < 0 ? null : (nbtTag != null && !nbtTag.isEmpty() ? nbtTag : null);
        pickupMaxCount = max;
    }
    public void clearPickupMaxCount() { pickupMaxItemId = null; pickupMaxTag = null; pickupMaxCount = -1; }

    public int countItem(String itemId) { return countItem(itemId, null); }
    private static net.minecraft.resources.ResourceLocation parseItemResourceId(String itemId) {
        //? if >=1.20.1 {
        return new net.minecraft.resources.ResourceLocation(itemId);
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation(itemId);
        *///?}
    }

    public int countItem(String itemId, String nbtTag) {
        net.minecraft.resources.ResourceLocation target = parseItemResourceId(itemId);
        int total = 0;
        for (int i = 0; i < pickupContainer.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = pickupContainer.getItem(i);
            if (stack.isEmpty()) continue;
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(target)) continue;
            if (nbtTag != null && !nbtTag.isEmpty()) {
                if (!stack.hasTag()) continue;
                try {
                    net.minecraft.nbt.CompoundTag req = net.minecraft.nbt.TagParser.parseTag(nbtTag);
                    net.minecraft.nbt.CompoundTag tag = stack.getTag();
                    for (String k : req.getAllKeys()) {
                        if (!tag.contains(k) || !java.util.Objects.equals(tag.get(k), req.get(k))) continue;
                    }
                } catch (Exception e) { continue; }
            }
            total += stack.getCount();
        }
        return total;
    }

    public SimpleContainer getPickupContainer() { return pickupContainer; }
    public java.util.UUID getLastPickupThrower() { return lastPickupThrower; }

    @Override
    public boolean wantsToPickUp(net.minecraft.world.item.ItemStack stack) {
        if (!super.wantsToPickUp(stack)) return false;
        if (pickupMaxItemId == null) return false;
        if (pickupMaxCount >= 0) {
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(parseItemResourceId(pickupMaxItemId))) return false;
            if (pickupMaxTag != null && !pickupMaxTag.isEmpty()) {
                if (!stack.hasTag()) return false;
                try {
                    net.minecraft.nbt.CompoundTag req = net.minecraft.nbt.TagParser.parseTag(pickupMaxTag);
                    for (String k : req.getAllKeys()) {
                        if (!stack.getTag().contains(k) || !java.util.Objects.equals(stack.getTag().get(k), req.get(k))) return false;
                    }
                } catch (Exception e) { return false; }
            }
            return countItem(pickupMaxItemId, pickupMaxTag) < pickupMaxCount;
        }
        return true;
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        java.util.UUID thrower = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.getItemThrower(itemEntity);
        if (pickupDropperFilter != null && (thrower == null || !thrower.equals(pickupDropperFilter))) return;
        lastPickupThrower = thrower;
        net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
        int toTake = stack.getCount();
        if (pickupMaxItemId != null && pickupMaxCount >= 0) {
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(parseItemResourceId(pickupMaxItemId))) return;
            if (pickupMaxTag != null && !pickupMaxTag.isEmpty() && (!stack.hasTag() || !java.util.Objects.equals(stack.getTag().get("tag"), pickupMaxTag))) return;
            int have = countItem(pickupMaxItemId, pickupMaxTag);
            if (have >= pickupMaxCount) return;
            toTake = Math.min(toTake, pickupMaxCount - have);
        }
        net.minecraft.world.item.ItemStack toAdd = stack.copy();
        toAdd.setCount(toTake);
        if (addToPickup(toAdd)) { this.take(itemEntity, toTake); stack.shrink(toTake); if (stack.isEmpty()) itemEntity.discard(); }
    }

    private boolean addToPickup(net.minecraft.world.item.ItemStack stack) {
        for (int i = 0; i < pickupContainer.getContainerSize() && !stack.isEmpty(); i++) {
            net.minecraft.world.item.ItemStack ex = pickupContainer.getItem(i);
            if (ex.isEmpty()) {
                int n = Math.min(stack.getCount(), stack.getMaxStackSize());
                net.minecraft.world.item.ItemStack c = stack.copy();
                c.setCount(n);
                pickupContainer.setItem(i, c);
                stack.shrink(n);
            } else if (net.minecraft.world.item.ItemStack.isSameItemSameTags(ex, stack)) {
                int n = Math.min(stack.getCount(), ex.getMaxStackSize() - ex.getCount());
                if (n > 0) { ex.grow(n); stack.shrink(n); }
            }
        }
        return stack.isEmpty();
    }

    public boolean isPathFailed() {
        return pathFailedFlag;
    }

    public void clearPathFailed() {
        pathFailedFlag = false;
        moveStuckTicks = 0;
        moveProgressAnchorX = this.getX();
        moveProgressAnchorZ = this.getZ();
    }

    /** Whether a ground path to the block can be built (loads chunks on the way first). */
    public boolean canReach(double x, double y, double z) {
        if (SprauteEntityCompat.level(this).isClientSide) return true;
        if (isFlying()) return true;
        ensureChunksToward(new Vec3(x, y, z));
        var path = this.getNavigation().createPath(x, y, z, 1);
        return path != null && path.canReach();
    }

    public Vec3 getScriptedMoveGoal() {
        return activeMoveGoal;
    }

    public void clearScriptedMoveGoal() {
        activeMoveGoal = null;
    }

    /** Snap body yaw toward a scripted move target before pathing starts. */
    private void snapBodyToward(double tx, double tz) {
        float yaw = calcTargetYaw(tx, tz);
        setBodyYaw(yaw);
        syncBodyYaw(yaw);
        this.setYRot(yaw);
        this.yHeadRot = yaw;
    }

    /** Integer block coords → center of block; fractional coords kept as-is. */
    private static double scriptedGoalCoord(double c) {
        double floored = net.minecraft.util.Mth.floor(c + 1.0e-4);
        if (Math.abs(c - floored) < 1.0e-3) {
            return floored + 0.5;
        }
        return c;
    }

    public void moveTo(double x, double y, double z, double speed) {
        moveTo(x, y, z, speed, true);
    }

    public void moveTo(double x, double y, double z, double speed, boolean faceWalkDirection) {
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        double standY = resolveStandY(gx, y, gz);
        activeMoveGoal = new Vec3(gx, standY, gz);
        walkFaceDirection = faceWalkDirection;
        clearPathFailed();
        moveStuckTicks = 0;
        if (faceWalkDirection) {
            snapBodyToward(gx, gz);
        }
        logWalk("moveTo START goal=" + fmtPos(gx, standY, gz) + " speed=" + speed
                + " faceWalk=" + faceWalkDirection
                + (Math.abs(standY - y) > 0.01 || gx != x || gz != z ? " (from " + x + "," + y + "," + z + ")" : ""));
        issueGroundNavigation(gx, standY, gz, speed, true);
    }

    /** Re-issue navigation during scripted move_to wait without resetting failure tracking. */
    public void retryMoveTo(double x, double y, double z, double speed) {
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        double standY = resolveStandY(gx, y, gz);
        activeMoveGoal = new Vec3(gx, standY, gz);
        moveStuckTicks = 0;
        clearPathFailed();
        double dx = getX() - gx;
        double dz = getZ() - gz;
        double distH = Math.sqrt(dx * dx + dz * dz);
        double retrySpeed = (distH > 0.6 && distH < 1.5) ? Math.max(speed, 1.2) : speed;
        logWalk("retryMoveTo goal=" + fmtPos(gx, standY, gz) + " speed=" + retrySpeed
                + " navDone=" + this.getNavigation().isDone()
                + " stuck=" + this.getNavigation().isStuck()
                + " pathFailed=" + pathFailedFlag);
        issueGroundNavigation(gx, standY, gz, retrySpeed, false);
    }

    /** Включить полёт и лететь к точке. */
    public void flyTo(double x, double y, double z, double speed) {
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        setFlying(true);
        activeFlySpeed = speed;
        flightPathRecalcCooldown = 0;
        activeMoveGoal = new Vec3(gx, y, gz);
        clearPathFailed();
        moveStuckTicks = 0;
        this.getNavigation().moveTo(gx, y, gz, speed);
    }

    /** Включить полёт и лететь к сущности. */
    public void flyToEntity(net.minecraft.world.entity.Entity target, double speed) {
        if (target == null) return;
        setFlying(true);
        activeFlySpeed = speed;
        flightPathRecalcCooldown = 0;
        this.getNavigation().moveTo(target, speed);
    }

    /** Включить полёт и постоянно следовать к точке. */
    public void alwaysFlyTo(double x, double y, double z, double speed) {
        setFlying(true);
        alwaysMoveTo(x, y, z, speed);
    }

    /** Включить полёт и постоянно следовать за сущностью. */
    public void alwaysFlyToEntity(net.minecraft.world.entity.Entity target, double speed) {
        if (target == null) return;
        setFlying(true);
        alwaysMoveToEntity(target, speed);
    }

    // ========== alwaysMoveTo ==========
    private net.minecraft.world.entity.Entity alwaysMoveEntity = null;
    private Vec3 alwaysMovePoint = null;
    private double alwaysMoveSpeed = 1.0;

    public void alwaysMoveTo(double x, double y, double z, double speed) {
        alwaysMoveTo(x, y, z, speed, true);
    }

    public void alwaysMoveTo(double x, double y, double z, double speed, boolean faceWalkDirection) {
        this.alwaysMovePoint = new Vec3(x, y, z);
        this.alwaysMoveEntity = null;
        this.alwaysMoveSpeed = speed;
        this.walkFaceDirection = faceWalkDirection;
        this.activeFlySpeed = speed;
        this.flightPathRecalcCooldown = 0;
        this.groundPathRecalcCooldown = 0;
        clearPathFailed();
    }

    public void alwaysMoveToEntity(net.minecraft.world.entity.Entity e, double speed) {
        alwaysMoveToEntity(e, speed, true);
    }

    public void alwaysMoveToEntity(net.minecraft.world.entity.Entity e, double speed, boolean faceWalkDirection) {
        this.alwaysMoveEntity = e;
        this.alwaysMovePoint = null;
        this.alwaysMoveSpeed = speed;
        this.walkFaceDirection = faceWalkDirection;
        this.activeFlySpeed = speed;
        this.flightPathRecalcCooldown = 0;
        this.groundPathRecalcCooldown = 0;
        clearPathFailed();
    }

    public void stopMove() {
        this.alwaysMoveEntity = null;
        this.alwaysMovePoint = null;
        this.activeMoveGoal = null;
        releaseForcedPathChunks();
        this.getNavigation().stop();
    }

    // ========== Combat ==========
    private java.util.UUID combatTargetUuid = null;
    private final java.util.ArrayList<String> attackAnims = new java.util.ArrayList<>(java.util.List.of("attack"));
    private int attackAnimIndex = 0;
    private String attackHand = "right";
    private double combatChaseSpeed = 1.0;
    private double attackRange = 2.5;
    private int attackCooldownTicks = 20;
    private int attackHitDelayTicks = 8;
    private int combatCooldown = 0;
    private int attackHitCountdown = -1;
    private boolean combatSwingActive = false;
    private int combatChaseRecalc = 0;
    private static final int COMBAT_CHASE_RECALC = 10;

    public double getAttackRange() { return attackRange; }

    public void setAttackRange(double range) {
        this.attackRange = Math.max(0.5, range);
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldownTicks = Math.max(1, ticks);
    }

    public void setAttackHitDelay(int ticks) {
        this.attackHitDelayTicks = Math.max(0, ticks);
    }

    public void setAttackHand(String hand) {
        if (hand != null && !hand.isBlank()) {
            this.attackHand = hand.trim().toLowerCase();
        }
    }

    public void setAttackAnims(String... raw) {
        attackAnims.clear();
        if (raw != null) {
            for (String part : raw) {
                if (part == null) continue;
                for (String piece : part.split(",")) {
                    String t = piece.trim();
                    if (!t.isEmpty()) attackAnims.add(t);
                }
            }
        }
        if (attackAnims.isEmpty()) attackAnims.add("attack");
        attackAnimIndex = 0;
    }

    public void setAttackAnims(java.util.List<?> list) {
        attackAnims.clear();
        if (list != null) {
            for (Object o : list) {
                if (o == null) continue;
                for (String piece : String.valueOf(o).split(",")) {
                    String t = piece.trim();
                    if (!t.isEmpty()) attackAnims.add(t);
                }
            }
        }
        if (attackAnims.isEmpty()) attackAnims.add("attack");
        attackAnimIndex = 0;
    }

    /** Chase and attack target. Uses {@link #setAttackHand} weapon for damage. */
    public void attackEntity(net.minecraft.world.entity.Entity target) {
        attackEntity(target, combatChaseSpeed, attackRange);
    }

    public void attackEntity(net.minecraft.world.entity.Entity target, double chaseSpeed) {
        attackEntity(target, chaseSpeed, attackRange);
    }

    public void attackEntity(net.minecraft.world.entity.Entity target, double chaseSpeed, double range) {
        if (target == null || !target.isAlive()) return;
        stopMove();
        this.combatTargetUuid = target.getUUID();
        this.combatChaseSpeed = Math.max(0.05, chaseSpeed);
        this.attackRange = Math.max(0.5, range);
        this.combatCooldown = 0;
        this.combatSwingActive = false;
        this.attackHitCountdown = -1;
        this.combatChaseRecalc = 0;
    }

    public void stopAttack() {
        combatTargetUuid = null;
        combatSwingActive = false;
        attackHitCountdown = -1;
        combatCooldown = 0;
        combatChaseRecalc = 0;
        this.getNavigation().stop();
    }

    public boolean isAttacking() {
        return combatTargetUuid != null;
    }

    private void tickCombat() {
        Level level = SprauteEntityCompat.level(this);
        if (level.isClientSide || combatTargetUuid == null) return;

        net.minecraft.world.entity.Entity target = null;
        if (level instanceof ServerLevel serverLevel) {
            target = serverLevel.getEntity(combatTargetUuid);
        }
        if (target == null || !target.isAlive()) {
            stopAttack();
            return;
        }

        if (combatCooldown > 0) combatCooldown--;

        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        boolean inRange = horiz <= attackRange && Math.abs(target.getY() - getY()) <= 3.5;

        if (inRange) {
            getNavigation().stop();
            alwaysLookAtEntity(target);
            if (!combatSwingActive && combatCooldown <= 0) {
                beginAttackSwing();
            }
        } else {
            if (combatSwingActive) {
                combatSwingActive = false;
                attackHitCountdown = -1;
            }
            if (--combatChaseRecalc <= 0) {
                getNavigation().moveTo(target, combatChaseSpeed);
                combatChaseRecalc = COMBAT_CHASE_RECALC;
            }
        }

        if (combatSwingActive && attackHitCountdown >= 0) {
            if (attackHitCountdown == 0) {
                if (inRange && target instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                    dealCombatDamage(living);
                }
                combatSwingActive = false;
                combatCooldown = attackCooldownTicks;
            } else {
                attackHitCountdown--;
            }
        }
    }

    private void beginAttackSwing() {
        if (attackAnims.isEmpty()) attackAnims.add("attack");
        String anim = attackAnims.get(attackAnimIndex % attackAnims.size());
        attackAnimIndex++;
        playOnce(anim, false);
        combatSwingActive = true;
        attackHitCountdown = attackHitDelayTicks;
    }

    private float getWeaponDamage() {
        net.minecraft.world.entity.EquipmentSlot slot = "left".equalsIgnoreCase(attackHand)
                ? net.minecraft.world.entity.EquipmentSlot.OFFHAND
                : net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        net.minecraft.world.item.ItemStack stack = getItemBySlot(slot);
        if (stack.isEmpty()) return 1.0f;
        float dmg = 1.0f;
        com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> map =
                stack.getAttributeModifiers(slot);
        for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : map.get(Attributes.ATTACK_DAMAGE)) {
            if (mod.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                dmg += mod.getAmount();
            }
        }
        return Math.max(1.0f, dmg);
    }

    private void dealCombatDamage(net.minecraft.world.entity.LivingEntity target) {
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel)) return;
        float amount = getWeaponDamage();
        //? if >=1.20.1 {
        target.hurt(new net.minecraft.world.damagesource.DamageSource(
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC),
                this), amount);
        //?} else {
        /*target.hurt(new net.minecraft.world.damagesource.EntityDamageSource("generic", this), amount);
        *///?}
    }

    /** True while flight steering applied this tick (drives walk/idle anim while flying). */
    private boolean flightSteering = false;
    private int flightPathRecalcCooldown = 0;
    private double activeFlySpeed = 1.0;

    private static final double FLIGHT_DIRECT_EPS = 0.35;

    /**
     * Flight steering smoothness (velocity lerp factor, 0..1). Lower = smoother, softer
     * acceleration/deceleration; higher = snappier. Set via script {@code flySmoothing}.
     */
    private float flySmoothing = 0.35f;
    public float getFlySmoothing() { return flySmoothing; }
    public void setFlySmoothing(float v) { this.flySmoothing = Math.max(0.02f, Math.min(1.0f, v)); }

    private void tickAlwaysMove() {
        if (SprauteEntityCompat.level(this).isClientSide) return;

        Vec3 target = null;
        if (alwaysMoveEntity != null) {
            if (alwaysMoveEntity.isAlive()) target = alwaysMoveEntity.position();
            else alwaysMoveEntity = null;
        } else if (alwaysMovePoint != null) {
            target = alwaysMovePoint;
        }

        if (target == null) {
            flightSteering = false;
            return;
        }

        if (isFlying()) {
            steerFlightToTarget(target);
        } else {
            flightSteering = false;
            ensureChunksToward(target);
            Vec3 step = resolveGroundStepTarget(target);
            var nav = this.getNavigation();
            boolean needRecalc = groundPathRecalcCooldown <= 0
                    || nav.isDone()
                    || nav.isStuck()
                    || nav.getPath() == null;
            if (needRecalc) {
                nav.moveTo(step.x, step.y, step.z, alwaysMoveSpeed);
                groundPathRecalcCooldown = GROUND_PATH_RECALC_COOLDOWN;
            } else {
                groundPathRecalcCooldown--;
            }
            trackMoveProgress(target);
        }
    }

    private void issueGroundNavigation(double x, double y, double z, double speed, boolean resetCooldown) {
        if (isFlying()) {
            this.getNavigation().moveTo(x, y, z, speed);
            logWalk("fly nav goal=" + fmtPos(x, y, z));
            return;
        }
        Vec3 goal = new Vec3(x, y, z);
        ensureChunksToward(goal);
        var nav = this.getNavigation();
        var path = nav.createPath(x, y, z, 1);
        boolean reachable = path != null && path.canReach();
        nav.moveTo(x, y, z, speed);
        logWalk("ground nav goal=" + fmtPos(x, y, z)
                + " reachable=" + reachable
                + " pathLen=" + (path != null ? path.getNodeCount() : 0));
        if (resetCooldown) {
            groundPathRecalcCooldown = GROUND_PATH_RECALC_COOLDOWN;
        }
    }

    /** Snap scripted move target to a standable block top near the requested Y. */
    private double resolveStandY(double x, double y, double z) {
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel)) return y;
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(
                net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y) + 2, net.minecraft.util.Mth.floor(z));
        for (int i = 0; i < 14; i++) {
            if (pos.getY() < level.getMinBuildHeight()) break;
            net.minecraft.world.level.block.state.BlockState below = level.getBlockState(pos.below());
            net.minecraft.world.level.block.state.BlockState feet = level.getBlockState(pos);
            net.minecraft.world.level.block.state.BlockState head = level.getBlockState(pos.above());
            //? if >=1.20.1 {
            if (below.isSolid() && !feet.isSolid() && !head.isSolid()) {
            //?} else {
            /*if (below.getMaterial().isSolid() && !feet.getMaterial().isSolid() && !head.getMaterial().isSolid()) {
            *///?}
                return pos.getY();
            }
            pos.move(net.minecraft.core.Direction.DOWN);
        }
        return y;
    }

    private String npcLabel() {
        if (getCustomName() != null) return getCustomName().getString();
        return String.valueOf(getUUID());
    }

    private static String fmtPos(double x, double y, double z) {
        return String.format("(%.1f, %.1f, %.1f)", x, y, z);
    }

    private void logWalk(String msg) {
        LOGGER.info("[NPC-WALK] {} pos={} {}", npcLabel(), fmtPos(getX(), getY(), getZ()), msg);
    }

    private Vec3 resolveGroundStepTarget(Vec3 goal) {
        double dx = goal.x - this.getX();
        double dz = goal.z - this.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz <= GROUND_STEP_HORIZONTAL) {
            return goal;
        }
        double scale = GROUND_STEP_HORIZONTAL / horiz;
        return new Vec3(this.getX() + dx * scale, goal.y, this.getZ() + dz * scale);
    }

    private void ensureChunksToward(Vec3 target) {
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel server)) return;

        ChunkPos from = new ChunkPos(this.blockPosition());
        ChunkPos to = new ChunkPos(new BlockPos(
                net.minecraft.util.Mth.floor(target.x),
                net.minecraft.util.Mth.floor(target.y),
                net.minecraft.util.Mth.floor(target.z)));
        int steps = Math.max(Math.abs(to.x - from.x), Math.abs(to.z - from.z));
        if (steps == 0) {
            forcePathChunk(server, from.x, from.z);
            return;
        }
        int limit = Math.min(steps, MAX_FORCED_PATH_CHUNKS);
        for (int i = 0; i <= limit; i++) {
            double t = (double) i / limit;
            int cx = from.x + (int) Math.round((to.x - from.x) * t);
            int cz = from.z + (int) Math.round((to.z - from.z) * t);
            forcePathChunk(server, cx, cz);
        }
    }

    private void forcePathChunk(ServerLevel server, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (forcedPathChunks.add(pos)) {
            server.setChunkForced(chunkX, chunkZ, true);
        }
    }

    private void releaseForcedPathChunks() {
        if (forcedPathChunks.isEmpty()) return;
        Level level = SprauteEntityCompat.level(this);
        if (level instanceof ServerLevel server) {
            for (ChunkPos pos : forcedPathChunks) {
                server.setChunkForced(pos.x, pos.z, false);
            }
        }
        forcedPathChunks.clear();
    }

    private void tickPathChunkTickets() {
        boolean longMove = activeMoveGoal != null
                && !isFlying()
                && this.position().distanceToSqr(activeMoveGoal) > 4.0;
        boolean following = alwaysMovePoint != null || alwaysMoveEntity != null;
        if (!longMove && !following) {
            releaseForcedPathChunks();
        }
    }

    private void trackMoveProgress(Vec3 goal) {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        if (this.position().distanceToSqr(goal) <= 4.0) {
            moveStuckTicks = 0;
            return;
        }
        double dx = this.getX() - moveProgressAnchorX;
        double dz = this.getZ() - moveProgressAnchorZ;
        if (dx * dx + dz * dz > 0.25) {
            moveStuckTicks = 0;
            moveProgressAnchorX = this.getX();
            moveProgressAnchorZ = this.getZ();
        } else if (!this.getNavigation().isDone()) {
            moveStuckTicks++;
            if (moveStuckTicks >= MOVE_STUCK_THRESHOLD) {
                pathFailedFlag = true;
                logWalk("STUCK goal=" + fmtPos(goal.x, goal.y, goal.z)
                        + " stuckTicks=" + moveStuckTicks
                        + " navDone=" + this.getNavigation().isDone());
            }
        }
    }

    /**
     * Hybrid flight: fly straight when the line to the target is clear; otherwise follow
     * {@link net.minecraft.world.entity.ai.navigation.FlyingPathNavigation} waypoints (still steering
     * with velocity lerp so mid-air targets work once the path is built).
     */
    private void steerFlightToTarget(Vec3 finalTarget) {
        Vec3 steer = resolveFlightSteerTarget(finalTarget);
        applyFlightVelocity(steer);
    }

    /** One-shot {@link #flyTo} / {@link #flyToEntity} — same hybrid rules when not on always-move. */
    private void tickPassiveFlightSteering() {
        if (!isFlying() || alwaysMovePoint != null || alwaysMoveEntity != null) return;
        var nav = this.getNavigation();
        if (nav.isDone()) return;
        var path = nav.getPath();
        if (path == null || path.isDone()) return;
        BlockPos end = path.getTarget();
        if (end == null) return;
        Vec3 finalTarget = Vec3.atCenterOf(end);
        steerFlightToTarget(finalTarget);
    }

    private Vec3 resolveFlightSteerTarget(Vec3 finalTarget) {
        if (canFlyDirectTo(finalTarget)) {
            flightPathRecalcCooldown = 0;
            return finalTarget;
        }

        var nav = this.getNavigation();
        if (flightPathRecalcCooldown <= 0 || nav.isDone()) {
            nav.moveTo(finalTarget.x, finalTarget.y, finalTarget.z, getFlightMoveSpeed());
            flightPathRecalcCooldown = 20;
        } else {
            flightPathRecalcCooldown--;
        }

        var path = nav.getPath();
        if (path != null && !path.isDone() && path.getNodeCount() > 0) {
            int idx = Math.min(path.getNextNodeIndex(), path.getNodeCount() - 1);
            Vec3 waypoint = Vec3.atCenterOf(path.getNodePos(idx));
            if (this.position().distanceToSqr(waypoint) < 0.64) {
                path.advance();
                if (!path.isDone() && path.getNodeCount() > 0) {
                    idx = Math.min(path.getNextNodeIndex(), path.getNodeCount() - 1);
                    waypoint = Vec3.atCenterOf(path.getNodePos(idx));
                }
            }
            if (canFlyDirectTo(waypoint)) {
                return waypoint;
            }
            return waypoint;
        }

        return finalTarget;
    }

    /** True when no solid blocks block a straight segment to {@code target}. */
    private boolean canFlyDirectTo(Vec3 target) {
        Level level = SprauteEntityCompat.level(this);
        Vec3 from = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ());
        double targetDist = from.distanceTo(target);
        if (targetDist < 1.0e-4) {
            return true;
        }

        BlockHitResult hit = level.clip(new ClipContext(
                from, target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        return hit.getLocation().distanceTo(from) >= targetDist - FLIGHT_DIRECT_EPS;
    }

    /**
     * Velocity steering toward {@code target}. Speed uses {@link Attributes#FLYING_SPEED} × move speed scale.
     */
    private void applyFlightVelocity(Vec3 target) {
        double dx = target.x - this.getX();
        double dy = target.y - this.getY();
        double dz = target.z - this.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double flySpeed = 0.5;
        var attr = this.getAttribute(Attributes.FLYING_SPEED);
        if (attr != null) flySpeed = attr.getValue();
        double maxStep = flySpeed * Math.max(0.1, getFlightMoveSpeed()) * 0.5; // blocks per tick

        this.setNoGravity(true);

        // Desired velocity shrinks as we approach the target; lerp gives smooth accel/decel.
        double step = Math.min(maxStep, dist);
        Vec3 desired = dist < 1.0e-8 ? Vec3.ZERO : new Vec3(dx, dy, dz).normalize().scale(step);
        Vec3 cur = this.getDeltaMovement();
        Vec3 next = cur.add(desired.subtract(cur).scale(flySmoothing));
        double cap = dist < 1.0e-8 ? 0.0 : Math.min(maxStep, dist);
        if (cap > 0 && next.length() > cap) {
            next = next.normalize().scale(cap);
        }
        this.setDeltaMovement(next);
        this.hasImpulse = true;
        flightSteering = next.lengthSqr() > 1.0e-8 || dist > 0.02;
    }

    private double getFlightMoveSpeed() {
        if (alwaysMovePoint != null || alwaysMoveEntity != null) {
            return alwaysMoveSpeed;
        }
        return activeFlySpeed;
    }

    /** Separation steering — мягко расталкивает НПС друг от друга при сближении. */
    private void tickSeparation() {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        // Scripted move_to: no lateral push — it causes left/right wobble and path failures
        if (activeMoveGoal != null && alwaysMovePoint == null && alwaysMoveEntity == null) return;
        // Применяем только если НПС активно движется
        boolean isMoving = !this.getNavigation().isDone() || alwaysMovePoint != null || alwaysMoveEntity != null;
        if (!isMoving) return;
        if (this.tickCount % 3 != 0) return; // проверяем каждые 3 тика

        double dx = 0, dz = 0;
        java.util.List<SprauteNpcEntity> nearby = SprauteEntityCompat.level(this)
            .getEntitiesOfClass(SprauteNpcEntity.class,
                this.getBoundingBox().inflate(SEPARATION_RADIUS + 0.1));
        for (SprauteNpcEntity other : nearby) {
            if (other == this) continue;
            double diffX = this.getX() - other.getX();
            double diffZ = this.getZ() - other.getZ();
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            if (dist > 0 && dist < SEPARATION_RADIUS) {
                double strength = SEPARATION_STRENGTH * (SEPARATION_RADIUS - dist) / SEPARATION_RADIUS;
                dx += (diffX / dist) * strength;
                dz += (diffZ / dist) * strength;
            }
        }
        if (dx != 0 || dz != 0) {
            Vec3 current = this.getDeltaMovement();
            this.setDeltaMovement(current.x + dx, current.y, current.z + dz);
        }
    }

    @Override
    public void tick() {
        float bodyStart = this.sprauteBodyYawHasEndOfTick ? this.sprauteBodyYawEndOfTick : this.yBodyRot;
        super.tick();
        if (SprauteEntityCompat.level(this).isClientSide) {
            float targetBody = this.entityData.get(SYNCED_BODY_YAW);
            this.yBodyRot = net.minecraft.util.Mth.approachDegrees(bodyStart, targetBody, BODY_TURN_SPEED);
            this.yHeadRot = this.yBodyRot;
            this.setYRot(this.yBodyRot);
            this.sprauteBodyYawEndOfTick = this.yBodyRot;
            this.sprauteBodyYawHasEndOfTick = true;
            return;
        }
        this.yBodyRot = bodyStart;
        this.yHeadRot = bodyStart;
        this.setYRot(bodyStart);
        tickAlwaysMove();
        tickCombat();
        tickPassiveFlightSteering();
        tickSeparation();
        if (activeMoveGoal != null && alwaysMovePoint == null && alwaysMoveEntity == null) {
            trackMoveProgress(activeMoveGoal);
            if (--walkLogCooldown <= 0) {
                walkLogCooldown = 40;
                double dx = getX() - activeMoveGoal.x;
                double dz = getZ() - activeMoveGoal.z;
                logWalk("tick goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z)
                        + " distH=" + String.format("%.1f", Math.sqrt(dx * dx + dz * dz))
                        + " navDone=" + this.getNavigation().isDone()
                        + " stuck=" + this.getNavigation().isStuck()
                        + " pathFailed=" + pathFailedFlag);
            }
        }
        tickPathChunkTickets();
        isMoving();
        tickLookSystem();
        fixBodyYawSamplingContinuity();
        tickHitboxBounds();
        this.sprauteBodyYawEndOfTick = this.yBodyRot;
        this.sprauteBodyYawHasEndOfTick = true;
    }

    // -------------------- look system (server) --------------------

    private void tickLookSystem() {
        boolean moving = isMoving();

        Vec3 target = resolveLookTarget();

        // While walking the BODY always faces the movement direction (even with an active
        // look target) — only the head tracks the target. When standing still the body
        // turns to the look target as before.
        if (moving && !(isFlying() && flightSteering)) {
            boolean scriptedMove = activeMoveGoal != null && alwaysMovePoint == null && alwaysMoveEntity == null;
            boolean alwaysMove = alwaysMovePoint != null || alwaysMoveEntity != null;
            if ((!scriptedMove && !alwaysMove) || walkFaceDirection) {
                float turnSpeed = scriptedMove ? BODY_TURN_SPEED * 4f : BODY_TURN_SPEED;
                tickBodyWalking(turnSpeed);
            }
        }

        if (target != null) {
            float targetYaw = calcTargetYaw(target.x, target.z);
            float targetPitch = calcTargetPitch(target.y, target.x, target.z);

            if (!moving) {
                tickBodyLookAtTarget(targetYaw);
            }

            applySyncedHeadLookClamped(targetYaw, targetPitch);
        } else {
            if (!moving) {
                syncBodyYaw(this.yBodyRot);
            }
            clearSyncedHeadLook();
        }
    }

    private Vec3 resolveLookTarget() {
        if (!lookActive) return null;
        if (lookEntity != null) {
            if (lookEntity.isAlive()) {
                return new Vec3(lookEntity.getX(), lookEntity.getEyeY(), lookEntity.getZ());
            }
            stopLook();
            return null;
        }
        return lookPoint;
    }

    private void tickBodyWalking() {
        tickBodyWalking(BODY_TURN_SPEED);
    }

    private void tickBodyWalking(float turnSpeed) {
        Vec3 faceTarget = null;
        var path = this.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            BlockPos next = path.getNextNodePos();
            faceTarget = new Vec3(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
        } else if (alwaysMovePoint != null) {
            faceTarget = resolveGroundStepTarget(alwaysMovePoint);
        } else if (activeMoveGoal != null) {
            faceTarget = activeMoveGoal;
        }
        if (faceTarget != null) {
            float walkYaw = calcTargetYaw(faceTarget.x, faceTarget.z);
            float nextBody = net.minecraft.util.Mth.approachDegrees(this.yBodyRot, walkYaw, turnSpeed);
            setBodyYaw(nextBody);
            syncBodyYaw(nextBody);
        }
        bodyDelayTicks = BODY_START_DELAY_TICKS;
    }

    private void tickBodyLookAtTarget(float targetYaw) {
        if (bodyDelayTicks > 0) {
            bodyDelayTicks--;
            syncBodyYaw(this.yBodyRot);
            return;
        }
        float turnSpeed = (lookEntity != null) ? BODY_TURN_SPEED * 2.5f : BODY_TURN_SPEED;
        float nextBody = net.minecraft.util.Mth.approachDegrees(this.yBodyRot, targetYaw, turnSpeed);
        setBodyYaw(nextBody);
        syncBodyYaw(nextBody);
    }

    private void setBodyYaw(float yaw) {
        yaw = net.minecraft.util.Mth.wrapDegrees(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.setYRot(yaw);
    }

    private void syncBodyYaw(float targetYaw) {
        this.entityData.set(SYNCED_BODY_YAW, net.minecraft.util.Mth.wrapDegrees(targetYaw));
    }

    private float calcTargetYaw(double targetX, double targetZ) {
        float rawYaw = (float)(Math.toDegrees(Math.atan2(targetZ - this.getZ(), targetX - this.getX())) - 90f);
        return net.minecraft.util.Mth.wrapDegrees(rawYaw);
    }

    private float calcTargetPitch(double targetY, double targetX, double targetZ) {
        double dx = targetX - this.getX();
        double dy = targetY - this.getEyeY();
        double dz = targetZ - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag c) {
        super.addAdditionalSaveData(c);
        c.putString("CustomModel", getModel());
        c.putString("CustomTexture", getTexture());
        c.putString("CustomAnimation", getAnimation());
        c.putString("HeadBoneName", getHeadBoneName());
        c.putString("IdleAnim", getIdleAnim());
        c.putString("WalkAnim", getWalkAnim());
        c.putBoolean("IsFlying", isFlying());
        c.putString("FlyIdleAnim", getFlyIdleAnim());
        c.putString("FlyWalkAnim", getFlyWalkAnim());
        c.putBoolean("IsSwimming", isSwimmingScript());
        c.putString("SwimIdleAnim", getSwimIdleAnim());
        c.putString("SwimWalkAnim", getSwimWalkAnim());
        c.putString("DeathAnim", getDeathAnim());
        c.putFloat("HitboxWidth", getHitboxWidth());
        c.putFloat("HitboxHeight", getHitboxHeight());
        c.putFloat("HitboxOffsetX", getHitboxOffsetX());
        c.putFloat("HitboxOffsetY", getHitboxOffsetY());
        c.putFloat("HitboxOffsetZ", getHitboxOffsetZ());

        if (!customDrops.isEmpty()) {
            net.minecraft.nbt.ListTag dropsList = new net.minecraft.nbt.ListTag();
            for (var rule : customDrops) {
                net.minecraft.nbt.CompoundTag ruleTag = new net.minecraft.nbt.CompoundTag();
                ruleTag.putString("item", rule.itemId);
                ruleTag.putInt("min", rule.min);
                ruleTag.putInt("max", rule.max);
                ruleTag.putInt("chance", rule.chance);
                if (rule.nbt != null) ruleTag.putString("nbt", rule.nbt);
                dropsList.add(ruleTag);
            }
            c.put("CustomDrops", dropsList);
        }

        if (!boneHitboxes.isEmpty()) {
            net.minecraft.nbt.ListTag hbList = new net.minecraft.nbt.ListTag();
            for (NpcBoneHitbox hb : boneHitboxes) {
                hbList.add(hb.toNbt());
            }
            c.put("BoneHitboxes", hbList);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag c) {
        super.readAdditionalSaveData(c);
        if (c.contains("CustomModel")) setModel(c.getString("CustomModel"));
        if (c.contains("CustomTexture")) setTexture(c.getString("CustomTexture"));
        if (c.contains("CustomAnimation")) setAnimation(c.getString("CustomAnimation"));
        if (c.contains("HeadBoneName")) setHeadBone(c.getString("HeadBoneName"));
        if (c.contains("IdleAnim")) setIdleAnim(c.getString("IdleAnim"));
        if (c.contains("WalkAnim")) setWalkAnim(c.getString("WalkAnim"));
        if (c.contains("IsFlying")) setFlying(c.getBoolean("IsFlying"));
        if (c.contains("FlyIdleAnim")) setFlyIdleAnim(c.getString("FlyIdleAnim"));
        if (c.contains("FlyWalkAnim")) setFlyWalkAnim(c.getString("FlyWalkAnim"));
        if (c.contains("IsSwimming")) setSwimmingScript(c.getBoolean("IsSwimming"));
        if (c.contains("SwimIdleAnim")) setSwimIdleAnim(c.getString("SwimIdleAnim"));
        if (c.contains("SwimWalkAnim")) setSwimWalkAnim(c.getString("SwimWalkAnim"));
        if (c.contains("DeathAnim")) setDeathAnim(c.getString("DeathAnim"));

        if (c.contains("HitboxWidth") && c.contains("HitboxHeight")) {
            float ox = c.contains("HitboxOffsetX") ? c.getFloat("HitboxOffsetX") : 0f;
            float oy = c.contains("HitboxOffsetY") ? c.getFloat("HitboxOffsetY") : 0f;
            float oz = c.contains("HitboxOffsetZ") ? c.getFloat("HitboxOffsetZ") : 0f;
            setHitbox(c.getFloat("HitboxWidth"), c.getFloat("HitboxHeight"), ox, oy, oz);
        }

        boneHitboxes.clear();
        if (c.contains("BoneHitboxes", 9)) {
            net.minecraft.nbt.ListTag hbList = c.getList("BoneHitboxes", 10);
            for (int i = 0; i < hbList.size(); i++) {
                boneHitboxes.add(NpcBoneHitbox.fromNbt(hbList.getCompound(i)));
            }
            syncBoneHitboxesData();
        }

        customDrops.clear();
        if (c.contains("CustomDrops", 9)) {
            net.minecraft.nbt.ListTag dropsList = c.getList("CustomDrops", 10);
            for (int i = 0; i < dropsList.size(); i++) {
                net.minecraft.nbt.CompoundTag ruleTag = dropsList.getCompound(i);
                String item = ruleTag.getString("item");
                int min = ruleTag.getInt("min");
                int max = ruleTag.getInt("max");
                int chance = ruleTag.getInt("chance");
                String nbt = ruleTag.contains("nbt") ? ruleTag.getString("nbt") : null;
                customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(item, min, max, chance, false, nbt));
            }
        }
        
        // Backward compatibility
        if (c.contains("DropItem")) {
            String dropItem = c.getString("DropItem");
            if (dropItem != null && !dropItem.isEmpty()) {
                int dropMin = c.contains("DropMin") ? c.getInt("DropMin") : 1;
                int dropMax = c.contains("DropMax") ? c.getInt("DropMax") : 1;
                int dropChance = c.contains("DropChance") ? c.getInt("DropChance") : 100;
                customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(dropItem, dropMin, dropMax, dropChance, false, null));
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (var rule : customDrops) {
            if (this.random.nextInt(100) < rule.chance) {
                int amount = rule.min + this.random.nextInt(Math.max(1, rule.max - rule.min + 1));
                String resolvedDropId = rule.itemId.contains(":") ? rule.itemId : "spraute_engine:" + rule.itemId;
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation(resolvedDropId)
                );
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("minecraft:" + rule.itemId)
                    );
                }
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, amount);
                    if (rule.nbt != null && !rule.nbt.isEmpty()) {
                        try {
                            stack.setTag(net.minecraft.nbt.TagParser.parseTag(rule.nbt));
                        } catch (Exception e) {}
                    }
                    this.spawnAtLocation(stack);
                }
            }
        }
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20d)
                .add(Attributes.MOVEMENT_SPEED, 0.3d)
                .add(Attributes.FLYING_SPEED, 0.4d);
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        // Flying NPCs never take fall damage
        if (isFlying()) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);
        if (HITBOX_WIDTH.equals(pKey) || HITBOX_HEIGHT.equals(pKey)
                || HITBOX_OFFSET_X.equals(pKey) || HITBOX_OFFSET_Y.equals(pKey) || HITBOX_OFFSET_Z.equals(pKey)) {
            invalidateHitboxCache();
            refreshDimensions();
        }
        if (BONE_HITBOXES_DATA.equals(pKey)) {
            loadBoneHitboxesFromSync();
            invalidateHitboxCache();
            refreshDimensions();
        }
    }

    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        if (cachedHitboxBounds != null) {
            return cachedHitboxBounds;
        }
        return NpcHitboxUtil.mainHitbox(
                this, getHitboxWidth(), getHitboxHeight(),
                getHitboxOffsetX(), getHitboxOffsetY(), getHitboxOffsetZ());
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        releaseForcedPathChunks();
        NpcBonePoseSolver.remove(this.getUUID());
        super.remove(reason);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pPose) {
        float w = this.entityData.get(HITBOX_WIDTH);
        float h = this.entityData.get(HITBOX_HEIGHT);
        return net.minecraft.world.entity.EntityDimensions.scalable(w, h);
    }

    /** @return true if this NPC is currently walking (with debounce to avoid flicker). */
    public boolean isMoving() {
        if (SprauteEntityCompat.level(this).isClientSide) {
            return this.entityData.get(IS_MOVING_SYNCED);
        }
        boolean rawMoving = flightSteering || this.getNavigation().isInProgress();
        if (rawMoving) movingStateTicks = Math.min(movingStateTicks + 1, MOVING_DEBOUNCE);
        else movingStateTicks = Math.max(movingStateTicks - 1, -MOVING_DEBOUNCE);
        boolean moving = movingStateTicks > 0;
        this.entityData.set(IS_MOVING_SYNCED, moving);
        return moving;
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (!SprauteEntityCompat.level(this).isClientSide && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onInteract(this, player);
            return net.minecraft.world.InteractionResult.sidedSuccess(SprauteEntityCompat.level(this).isClientSide);
        }
        return super.mobInteract(player, hand);
    }
}
