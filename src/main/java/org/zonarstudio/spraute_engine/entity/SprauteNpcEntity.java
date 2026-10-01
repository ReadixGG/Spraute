package org.zonarstudio.spraute_engine.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.compat.SprautePlayerCompat;
import org.zonarstudio.spraute_engine.resource.SprauteDefaultNpcAssets;
import org.zonarstudio.spraute_engine.entity.path.AirPathNavigator;
import org.zonarstudio.spraute_engine.entity.path.AirPathSettings;
import org.zonarstudio.spraute_engine.entity.path.GroundPathNavigator;
import org.zonarstudio.spraute_engine.entity.path.GroundPathSettings;
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
    private java.util.UUID lastPickupThrower = null;
    /** Per-script item filters from {@code on pickup(...)}. Vanilla loot AI also requires mobGriefing. */
    private final java.util.Map<Object, java.util.Set<String>> pickupAllowBySource = new java.util.IdentityHashMap<>();
    /** Caps from {@code pickupMax} / {@code await pickup}. Key = {@link #pickupAllowKey}. */
    private final java.util.Map<String, Integer> pickupMaxByKey = new java.util.HashMap<>();
    /** Caps from {@code on pickup(..., max)} per script. */
    private final java.util.Map<Object, java.util.Map<String, Integer>> pickupMaxBySource = new java.util.IdentityHashMap<>();

    // ========== Look ==========
    private net.minecraft.world.entity.Entity lookEntity = null;
    private net.minecraft.world.phys.Vec3 lookPoint = null;
    private boolean lookActive = false;

    private static final float DEFAULT_WALK_TURN_SPEED = 6f;
    private static final float DEFAULT_IDLE_TURN_SPEED = 6f;
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

    /** Body turn rate while walking (°/tick). Default {@value #DEFAULT_WALK_TURN_SPEED}. */
    public void setWalkTurnSpeed(float degreesPerTick) {
        this.entityData.set(WALK_TURN_SPEED, Math.max(0.1f, degreesPerTick));
    }
    public float getWalkTurnSpeed() {
        return this.entityData.get(WALK_TURN_SPEED);
    }

    /** Body turn rate while standing and looking (°/tick). Default {@value #DEFAULT_IDLE_TURN_SPEED}. */
    public void setIdleTurnSpeed(float degreesPerTick) {
        this.entityData.set(IDLE_TURN_SPEED, Math.max(0.1f, degreesPerTick));
    }
    public float getIdleTurnSpeed() {
        return this.entityData.get(IDLE_TURN_SPEED);
    }

    /** Client catch-up rate toward synced body yaw. */
    private float clientBodyTurnSpeed() {
        return Math.max(getWalkTurnSpeed(), getIdleTurnSpeed());
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
    // Full scene/script visual transform. Y rotation is an offset on top of body yaw.
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_ROT_X =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_ROT_Y =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_ROT_Z =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_SCALE_X =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_SCALE_Y =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MODEL_SCALE_Z =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> TRANSLUCENT =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> OPACITY =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LOD_ENABLED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> RENDER_DISTANCE =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> FORCE_VISIBLE =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> WALK_TURN_SPEED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> IDLE_TURN_SPEED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    private final java.util.List<NpcBoneHitbox> boneHitboxes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private net.minecraft.world.phys.AABB cachedHitboxBounds = null;

    public final java.util.Map<String, Object> customData = new java.util.concurrent.ConcurrentHashMap<>();

    public final java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> customDrops = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Experience orbs awarded on death (create npc xp / dropXp). */
    private int dropXp;

    public void setDropXp(int xp) {
        this.dropXp = Math.max(0, xp);
        this.xpReward = this.dropXp;
    }

    public int getDropXp() {
        return dropXp;
    }

    public String[] uiRenderBones = null;

    /** Имя группы, к которой принадлежит этот НПС (null если не в группе). */
    private String groupName = null;
    public String getGroupName() { return groupName; }
    public void setGroupName(String name) { this.groupName = name; }

    private static final double SEPARATION_RADIUS = 1.1;
    private static final double SEPARATION_STRENGTH = 0.12;

    /** Fallback arrive radius for non-Spraute mobs using {@link #isCloseToPoint}. */
    private static final double MOVE_ARRIVE_HORIZ = 0.6;
    private static final double MOVE_ARRIVE_VERT = 1.5;
    private static final int MAX_FORCED_PATH_CHUNKS = 10;
    private static final int MOVE_STUCK_THRESHOLD = 60;

    private final GroundPathNavigator groundNav = new GroundPathNavigator(new GroundPathSettings());
    private final AirPathNavigator airNav = new AirPathNavigator(new AirPathSettings());
    private java.util.UUID flyFollowEntityUuid = null;
    private Vec3 activeMoveGoal = null;
    private double activeMoveSpeed = 1.0;
    private java.util.List<Vec3> followRoutePoints = java.util.List.of();
    private int followRouteIndex;
    private boolean followRouteLoop;
    private boolean followRouteFly;
    private double followRouteSpeed = 1.0;
    private boolean followRouteActive;
    private NpcLocomotionSnapshot pendingLocomotion;
    private boolean issuingRouteMove;
    /** When false during scripted moveTo/alwaysMoveTo, body keeps its yaw instead of turning toward the path. */
    private boolean walkFaceDirection = true;
    private final java.util.Set<ChunkPos> forcedPathChunks = new java.util.HashSet<>();
    private final java.util.Set<ChunkPos> keepLoadedChunks = new java.util.HashSet<>();
    private ChunkPos lastKeepLoadedChunk;
    private boolean loadChunks;
    private boolean pathFailedFlag = false;
    private int moveStuckTicks = 0;
    private int walkLogCooldown = 0;
    private double moveProgressAnchorX;
    private double moveProgressAnchorZ;

    public SprauteNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
        this.setPersistenceRequired();
        var follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(48.0d);
        }
        applyPathStepHeight();
    }

    /** Scene / script NPCs must never despawn when the player walks away. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return !org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(this);
    }

    /** Scene NPCs respawn from the scene document — do not write them into the world save. */
    @Override
    public boolean save(net.minecraft.nbt.CompoundTag tag) {
        if (org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(this)) return false;
        return super.save(tag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODEL_RES, SprauteDefaultNpcAssets.MODEL);
        this.entityData.define(TEXTURE_RES, SprauteDefaultNpcAssets.TEXTURE);
        this.entityData.define(ANIMATION_RES, SprauteDefaultNpcAssets.ANIMATION);
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
        this.entityData.define(WALK_TURN_SPEED, DEFAULT_WALK_TURN_SPEED);
        this.entityData.define(IDLE_TURN_SPEED, DEFAULT_IDLE_TURN_SPEED);
    }

    // ========== Model/Texture/Animation resources ==========
    public void setModel(String v) {
        applyAssetPath(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.MODEL, MODEL_RES);
    }
    public String getModel() { return this.entityData.get(MODEL_RES); }
    public void setTexture(String v) {
        if (v != null && v.startsWith("player_skin:")) {
            this.entityData.set(TEXTURE_RES, v);
            return;
        }
        applyAssetPath(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.TEXTURE, TEXTURE_RES);
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
        applyAssetPath(v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.ANIMATION, ANIMATION_RES);
    }
    public String getAnimation() { return this.entityData.get(ANIMATION_RES); }

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
        invalidateHitboxCache();
        refreshDimensions();
    }
    public void setModelScale(float uniform) { setModelScale(uniform, uniform, uniform); }
    public void setModelScaleX(float value) { setModelScale(value, getModelScaleY(), getModelScaleZ()); }
    public void setModelScaleY(float value) { setModelScale(getModelScaleX(), value, getModelScaleZ()); }
    public void setModelScaleZ(float value) { setModelScale(getModelScaleX(), getModelScaleY(), value); }
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

    private void applyAssetPath(String v, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind kind,
                                net.minecraft.network.syncher.EntityDataAccessor<String> accessor) {
        if (v == null || v.isEmpty()) {
            this.entityData.set(accessor, v);
            return;
        }
        org.zonarstudio.spraute_engine.util.SprauteResourcePath.Result check =
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(v, kind);
        if (!check.ok()) {
            net.minecraft.world.level.Level lv = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(this);
            if (lv != null && !lv.isClientSide()) {
                org.zonarstudio.spraute_engine.util.SprauteResourcePath.warnServerPlayersOnce(lv, check, getStringUUID());
            }
            return;
        }
        this.entityData.set(accessor, org.zonarstudio.spraute_engine.util.SprauteResourcePath.toStoragePath(check));
    }

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
        } else {
            groundNav.stop(this);
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

    public boolean isLoadChunks() { return loadChunks; }

    public void setLoadChunks(boolean loadChunks) {
        this.loadChunks = loadChunks;
        if (!loadChunks) {
            releaseKeepLoadedChunks();
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

        net.minecraft.world.item.ItemStack stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(itemStr, count);
        if (stack.isEmpty()) return false;

        spawnThrownEntity(serverLevel, stack);
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
        String key = pickupAllowKey(itemId, nbtTag);
        if (itemId == null || itemId.isEmpty() || max < 0) {
            pickupMaxByKey.remove(key);
            return;
        }
        pickupMaxByKey.put(key, max);
    }
    public void clearPickupMaxCount() { pickupMaxByKey.clear(); }
    public void clearPickupMaxCount(String itemId, String nbtTag) {
        pickupMaxByKey.remove(pickupAllowKey(itemId, nbtTag));
    }

    public static String pickupAllowKey(String itemId, String nbtTag) {
        String id = itemId == null ? "" : itemId;
        String tag = (nbtTag == null || nbtTag.isEmpty() || "null".equals(nbtTag)) ? "" : nbtTag;
        return id + "\0" + tag;
    }

    public void setPickupAllowsFrom(Object source, java.util.Set<String> keys) {
        if (source == null) return;
        if (keys == null || keys.isEmpty()) pickupAllowBySource.remove(source);
        else pickupAllowBySource.put(source, new java.util.HashSet<>(keys));
    }

    public void setPickupMaxFrom(Object source, java.util.Map<String, Integer> maxes) {
        if (source == null) return;
        if (maxes == null || maxes.isEmpty()) pickupMaxBySource.remove(source);
        else pickupMaxBySource.put(source, new java.util.HashMap<>(maxes));
    }

    public void clearPickupAllowsFrom(Object source) {
        if (source != null) {
            pickupAllowBySource.remove(source);
            pickupMaxBySource.remove(source);
        }
    }

    private static String[] splitAllowKey(String key) {
        int split = key.indexOf('\0');
        String id = split < 0 ? key : key.substring(0, split);
        String tag = split < 0 || split >= key.length() - 1 ? null : key.substring(split + 1);
        if (tag != null && tag.isEmpty()) tag = null;
        return new String[]{id, tag};
    }

    private boolean stackMatchesKey(net.minecraft.world.item.ItemStack stack, String key) {
        String[] parts = splitAllowKey(key);
        return org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.matches(stack, parts[0], parts[1]);
    }

    private boolean matchesAnyPickupAllow(net.minecraft.world.item.ItemStack stack) {
        for (java.util.Set<String> keys : pickupAllowBySource.values()) {
            for (String key : keys) {
                if (stackMatchesKey(stack, key)) return true;
            }
        }
        return false;
    }

    private Integer effectivePickupMax(net.minecraft.world.item.ItemStack stack) {
        Integer cap = null;
        for (var e : pickupMaxByKey.entrySet()) {
            if (stackMatchesKey(stack, e.getKey())) {
                cap = cap == null ? e.getValue() : Math.min(cap, e.getValue());
            }
        }
        for (var src : pickupMaxBySource.values()) {
            for (var e : src.entrySet()) {
                if (stackMatchesKey(stack, e.getKey())) {
                    cap = cap == null ? e.getValue() : Math.min(cap, e.getValue());
                }
            }
        }
        return cap;
    }

    private String[] firstMatchingFilter(net.minecraft.world.item.ItemStack stack) {
        for (String key : pickupMaxByKey.keySet()) {
            if (stackMatchesKey(stack, key)) return splitAllowKey(key);
        }
        for (var src : pickupMaxBySource.values()) {
            for (String key : src.keySet()) {
                if (stackMatchesKey(stack, key)) return splitAllowKey(key);
            }
        }
        for (java.util.Set<String> keys : pickupAllowBySource.values()) {
            for (String key : keys) {
                if (stackMatchesKey(stack, key)) return splitAllowKey(key);
            }
        }
        return null;
    }

    private boolean hasPickupRequest() {
        return !pickupMaxByKey.isEmpty() || !pickupMaxBySource.isEmpty() || !pickupAllowBySource.isEmpty();
    }

    public int countItem(String itemId) { return countItem(itemId, null); }
    private static net.minecraft.resources.ResourceLocation parseItemResourceId(String itemId) {
        //? if >=1.20.1 {
        return new net.minecraft.resources.ResourceLocation(itemId);
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation(itemId);
        *///?}
    }

    public int countItem(String itemId, String nbtTag) {
        int total = 0;
        for (int i = 0; i < pickupContainer.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = pickupContainer.getItem(i);
            if (org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.matches(stack, itemId, nbtTag)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public SimpleContainer getPickupContainer() { return pickupContainer; }
    public java.util.UUID getLastPickupThrower() { return lastPickupThrower; }

    @Override
    public boolean wantsToPickUp(net.minecraft.world.item.ItemStack stack) {
        if (!this.canPickUpLoot() || stack == null || stack.isEmpty()) return false;
        Integer cap = effectivePickupMax(stack);
        if (cap != null) {
            String[] filter = firstMatchingFilter(stack);
            int have = filter == null ? 0 : countItem(filter[0], filter[1]);
            return have < cap;
        }
        return matchesAnyPickupAllow(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        java.util.UUID thrower = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.getItemThrower(itemEntity);
        if (pickupDropperFilter != null && (thrower == null || !thrower.equals(pickupDropperFilter))) return;
        net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
        if (!wantsToPickUp(stack)) return;
        lastPickupThrower = thrower;
        int toTake = stack.getCount();
        Integer cap = effectivePickupMax(stack);
        if (cap != null) {
            String[] filter = firstMatchingFilter(stack);
            int have = filter == null ? 0 : countItem(filter[0], filter[1]);
            if (have >= cap) return;
            toTake = Math.min(toTake, cap - have);
        }
        net.minecraft.world.item.ItemStack toAdd = stack.copy();
        toAdd.setCount(toTake);
        if (addToPickup(toAdd)) { this.take(itemEntity, toTake); stack.shrink(toTake); if (stack.isEmpty()) itemEntity.discard(); }
    }

    /** Own loot scan: vanilla Mob.aiStep skips pickup when mobGriefing is false. */
    private void tickScriptPickup() {
        net.minecraft.world.level.Level level = SprauteEntityCompat.level(this);
        if (level.isClientSide || !this.isAlive() || !this.canPickUpLoot() || !hasPickupRequest()) return;
        net.minecraft.world.phys.AABB box = this.getBoundingBox().inflate(1.25, 0.75, 1.25);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.isRemoved() || item.getItem().isEmpty() || item.hasPickUpDelay()) continue;
            pickUpItem(item);
        }
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
    public GroundPathSettings groundPathSettings() {
        return groundNav.settings();
    }

    /** Applies {@link GroundPathSettings#stepHeight} to entity movement (stairs/slabs). */
    public void applyPathStepHeight() {
        SprautePlayerCompat.setStepHeight(this, (float) groundNav.settings().stepHeight);
    }

    public AirPathSettings airPathSettings() {
        return airNav.settings();
    }

    public boolean canReach(double x, double y, double z) {
        if (SprauteEntityCompat.level(this).isClientSide) return true;
        ensureChunksToward(new Vec3(x, y, z));
        if (isFlying()) {
            return airNav.canReach(this, x, y, z);
        }
        return groundNav.canReach(this, x, y, z);
    }

    public Vec3 getScriptedMoveGoal() {
        return activeMoveGoal;
    }

    public void clearScriptedMoveGoal() {
        activeMoveGoal = null;
        flyFollowEntityUuid = null;
        groundNav.stop(this);
        airNav.stop(this);
    }

    /** True when scripted wait should re-issue a move (path idle or failed). */
    public boolean needsGroundMoveRetry() {
        if (isFlying()) {
            return !airNav.isInProgress() || airNav.isFailed();
        }
        return !groundNav.isInProgress() || groundNav.isFailed();
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
        if (!issuingRouteMove) clearFollowRoute();
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        double standY = resolveStandY(gx, y, gz);
        activeMoveGoal = new Vec3(gx, standY, gz);
        activeMoveSpeed = speed;
        walkFaceDirection = faceWalkDirection;
        clearPathFailed();
        moveStuckTicks = 0;
        if (faceWalkDirection) {
            snapBodyToward(gx, gz);
        }
        logWalk("moveTo START goal=" + fmtPos(gx, standY, gz) + " speed=" + speed
                + " faceWalk=" + faceWalkDirection
                + (Math.abs(standY - y) > 0.01 || gx != x || gz != z ? " (from " + x + "," + y + "," + z + ")" : ""));
        startGroundMove(gx, standY, gz, speed, true);
    }

    /** Re-issue navigation during scripted move_to wait without resetting failure tracking. */
    public void retryMoveTo(double x, double y, double z, double speed) {
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        double standY = resolveStandY(gx, y, gz);
        activeMoveGoal = new Vec3(gx, standY, gz);
        activeMoveSpeed = speed;
        moveStuckTicks = 0;
        clearPathFailed();
        double dx = getX() - gx;
        double dz = getZ() - gz;
        double distH = Math.sqrt(dx * dx + dz * dz);
        double arrive = groundNav.settings().arriveRadius;
        double retrySpeed = (distH > arrive && distH < 1.5) ? Math.max(speed, 1.2) : speed;
        activeMoveSpeed = retrySpeed;
        logWalk("retryMoveTo goal=" + fmtPos(gx, standY, gz) + " speed=" + retrySpeed
                + " navigating=" + groundNav.isInProgress()
                + " pathFailed=" + pathFailedFlag);
        startGroundMove(gx, standY, gz, retrySpeed, false);
    }

    /**
     * Distance-based arrival for scripted {@code moveTo}/{@code await moveTo}.
     * Same on 1.19.2 and 1.20.1 — do not use {@code Navigation#isDone()} as “arrived”
     * (null/incomplete paths also report done).
     */
    public boolean hasReachedMoveGoal() {
        if (activeMoveGoal == null) return false;
        return isCloseToPoint(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z);
    }

    /**
     * Same goal normalization as {@link #moveTo} — used by {@code await moveTo} after the entity
     * already cleared {@link #activeMoveGoal} on arrival (one-shot must not resume if pushed).
     */
    public boolean isCloseToScriptedCoords(double x, double y, double z) {
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        if (isFlying()) {
            return isCloseToPoint(gx, y, gz);
        }
        double gy = resolveStandY(gx, y, gz);
        return isCloseToPoint(gx, gy, gz);
    }

    public static boolean isCloseToPoint(net.minecraft.world.entity.Entity mob, double tx, double ty, double tz) {
        double dx = mob.getX() - tx;
        double dy = mob.getY() - ty;
        double dz = mob.getZ() - tz;
        if (mob instanceof SprauteNpcEntity npc && npc.isFlying()) {
            double r = npc.airNav.settings().arriveRadius;
            return dx * dx + dy * dy + dz * dz <= r * r;
        }
        double horiz = MOVE_ARRIVE_HORIZ;
        double vert = MOVE_ARRIVE_VERT;
        if (mob instanceof SprauteNpcEntity npc) {
            horiz = npc.groundNav.settings().arriveRadius;
            vert = npc.groundNav.settings().arriveVert;
        }
        return dx * dx + dz * dz <= horiz * horiz && Math.abs(dy) <= vert;
    }

    private boolean isCloseToPoint(double tx, double ty, double tz) {
        return isCloseToPoint(this, tx, ty, tz);
    }

    /** Включить полёт и лететь к точке. */
    public void flyTo(double x, double y, double z, double speed) {
        if (!issuingRouteMove) clearFollowRoute();
        double gx = scriptedGoalCoord(x);
        double gz = scriptedGoalCoord(z);
        setFlying(true);
        activeFlySpeed = speed;
        activeMoveSpeed = speed;
        flyFollowEntityUuid = null;
        activeMoveGoal = new Vec3(gx, y, gz);
        clearPathFailed();
        moveStuckTicks = 0;
        startAirMove(gx, y, gz, speed, true);
    }

    /** Включить полёт и лететь к сущности. */
    public void flyToEntity(net.minecraft.world.entity.Entity target, double speed) {
        if (target == null) return;
        if (!issuingRouteMove) clearFollowRoute();
        setFlying(true);
        activeFlySpeed = speed;
        activeMoveSpeed = speed;
        flyFollowEntityUuid = target.getUUID();
        activeMoveGoal = target.position();
        clearPathFailed();
        moveStuckTicks = 0;
        startAirMove(target.getX(), target.getY(), target.getZ(), speed, true);
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
        clearFollowRoute();
        this.alwaysMovePoint = new Vec3(x, y, z);
        this.alwaysMoveEntity = null;
        this.alwaysMoveSpeed = speed;
        this.walkFaceDirection = faceWalkDirection;
        this.activeFlySpeed = speed;
        clearPathFailed();
    }

    public void alwaysMoveToEntity(net.minecraft.world.entity.Entity e, double speed) {
        alwaysMoveToEntity(e, speed, true);
    }

    public void alwaysMoveToEntity(net.minecraft.world.entity.Entity e, double speed, boolean faceWalkDirection) {
        clearFollowRoute();
        this.alwaysMoveEntity = e;
        this.alwaysMovePoint = null;
        this.alwaysMoveSpeed = speed;
        this.walkFaceDirection = faceWalkDirection;
        this.activeFlySpeed = speed;
        clearPathFailed();
    }

    public void stopMove() {
        clearFollowRoute();
        this.alwaysMoveEntity = null;
        this.alwaysMovePoint = null;
        this.activeMoveGoal = null;
        this.flyFollowEntityUuid = null;
        this.activeMoveSpeed = 1.0;
        releaseForcedPathChunks();
        groundNav.stop(this);
        airNav.stop(this);
        this.getNavigation().stop();
    }

    private double followRouteSavedMaxRange = -1;
    private int followRouteSavedMaxNodes = -1;

    /**
     * Walk or fly the authored route: first to point 0, then 0→1→…→last.
     * Ground: A* pathfinding between points (not along the drawn curve).
     * Loop mode continues last→first.
     */
    public void followRoute(java.util.List<Vec3> points, boolean loop, boolean fly, double speed) {
        stopMove();
        if (fly) setFlying(true);
        else if (isFlying()) setFlying(false);
        if (points == null || points.isEmpty()) return;
        followRoutePoints = java.util.List.copyOf(points);
        followRouteIndex = 0;
        followRouteLoop = loop;
        followRouteFly = fly;
        followRouteSpeed = speed > 0 ? speed : 1.0;
        followRouteActive = true;
        if (!fly) {
            GroundPathSettings gs = groundNav.settings();
            followRouteSavedMaxRange = gs.maxRange;
            followRouteSavedMaxNodes = gs.maxNodes;
            gs.skipDirectWalk = true;
        }
        issueFollowRouteMove();
    }

    public boolean isFollowingRoute() {
        return followRouteActive;
    }

    /** Current waypoint index while following a route, or {@code -1} when idle. */
    public int getRouteIndex() {
        return followRouteActive ? followRouteIndex : -1;
    }

    /** Number of waypoints on the active follow-route, or {@code 0} when idle. */
    public int getRoutePointCount() {
        return followRouteActive ? followRoutePoints.size() : 0;
    }

    public NpcLocomotionSnapshot captureLocomotion() {
        if (!followRouteActive && alwaysMovePoint == null && alwaysMoveEntity == null && activeMoveGoal == null) {
            return null;
        }
        java.util.UUID followUuid = alwaysMoveEntity != null ? alwaysMoveEntity.getUUID() : null;
        return new NpcLocomotionSnapshot(
                followRouteActive, followRoutePoints, followRouteIndex,
                followRouteLoop, followRouteFly, followRouteSpeed,
                alwaysMovePoint, followUuid, activeMoveGoal,
                followRouteActive ? followRouteSpeed
                        : alwaysMovePoint != null || alwaysMoveEntity != null ? alwaysMoveSpeed : activeMoveSpeed,
                walkFaceDirection, isFlying());
    }

    public void restoreLocomotion(NpcLocomotionSnapshot snap) {
        if (snap == null || snap.isIdle()) return;
        walkFaceDirection = snap.walkFaceDirection;
        if (snap.flying) setFlying(true);
        if (snap.followRouteActive && !snap.followRoutePoints.isEmpty()) {
            followRoutePoints = java.util.List.copyOf(snap.followRoutePoints);
            followRouteIndex = net.minecraft.util.Mth.clamp(snap.followRouteIndex, 0, followRoutePoints.size() - 1);
            followRouteLoop = snap.followRouteLoop;
            followRouteFly = snap.followRouteFly;
            followRouteSpeed = snap.followRouteSpeed > 0 ? snap.followRouteSpeed : 1.0;
            followRouteActive = true;
            if (!followRouteFly) {
                GroundPathSettings gs = groundNav.settings();
                followRouteSavedMaxRange = gs.maxRange;
                followRouteSavedMaxNodes = gs.maxNodes;
                gs.skipDirectWalk = true;
            } else {
                setFlying(true);
            }
            issueFollowRouteMove();
            logWalk("restore followRoute index=" + followRouteIndex + "/" + followRoutePoints.size()
                    + " loop=" + followRouteLoop);
            return;
        }
        if (snap.alwaysMovePoint != null) {
            alwaysMoveTo(snap.alwaysMovePoint.x, snap.alwaysMovePoint.y, snap.alwaysMovePoint.z,
                    snap.moveSpeed, snap.walkFaceDirection);
            return;
        }
        if (snap.alwaysMoveEntityUuid != null) {
            Level level = SprauteEntityCompat.level(this);
            net.minecraft.world.entity.Entity target = null;
            if (level instanceof ServerLevel server) {
                target = server.getEntity(snap.alwaysMoveEntityUuid);
            }
            if (target != null) {
                alwaysMoveToEntity(target, snap.moveSpeed, snap.walkFaceDirection);
                return;
            }
        }
        if (snap.activeMoveGoal != null) {
            if (snap.flying || isFlying()) {
                flyTo(snap.activeMoveGoal.x, snap.activeMoveGoal.y, snap.activeMoveGoal.z, snap.moveSpeed);
            } else {
                moveTo(snap.activeMoveGoal.x, snap.activeMoveGoal.y, snap.activeMoveGoal.z,
                        snap.moveSpeed, snap.walkFaceDirection);
            }
        }
    }

    public void collectKeepLoadedChunks(java.util.Set<ChunkPos> out) {
        if (out == null) return;
        ChunkPos here = new ChunkPos(this.blockPosition());
        int radius = loadChunks ? 1 : 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(new ChunkPos(here.x + dx, here.z + dz));
            }
        }
        out.addAll(forcedPathChunks);
        out.addAll(keepLoadedChunks);
        NpcLocomotionSnapshot snap = captureLocomotion();
        if (snap != null) snap.addChunks(out);
    }

    void publishLocomotion() {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        String sceneId = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(this);
        String nodeId = org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeIdFromEntityTags(this);
        if (sceneId == null || nodeId == null) return;
        var state = org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(sceneId);
        if (state == null) return;
        state.putNpcLocomotion(nodeId, captureLocomotion());
    }

    private void clearPublishedLocomotion() {
        String sceneId = org.zonarstudio.spraute_engine.scene.SceneRuntime.sceneIdFromEntity(this);
        String nodeId = org.zonarstudio.spraute_engine.scene.SceneRuntime.nodeIdFromEntityTags(this);
        if (sceneId == null || nodeId == null) return;
        var state = org.zonarstudio.spraute_engine.scene.SceneRuntime.getActiveState(sceneId);
        if (state != null) state.clearNpcLocomotion(nodeId);
    }

    private void restoreFollowRoutePathSettings() {
        groundNav.setDampLastWaypoint(true);
        airNav.setDampLastWaypoint(true);
        if (followRouteSavedMaxRange < 0) return;
        GroundPathSettings gs = groundNav.settings();
        gs.maxRange = followRouteSavedMaxRange;
        gs.maxNodes = followRouteSavedMaxNodes;
        gs.skipDirectWalk = false;
        followRouteSavedMaxRange = -1;
        followRouteSavedMaxNodes = -1;
    }

    private void clearFollowRoute() {
        if (issuingRouteMove) return;
        followRouteActive = false;
        followRoutePoints = java.util.List.of();
        followRouteIndex = 0;
        restoreFollowRoutePathSettings();
    }

    private void issueFollowRouteMove() {
        if (followRouteIndex < 0 || followRouteIndex >= followRoutePoints.size()) return;
        Vec3 point = followRoutePoints.get(followRouteIndex);
        if (!followRouteFly) {
            double dist = position().distanceTo(point);
            GroundPathSettings gs = groundNav.settings();
            gs.maxRange = Math.min(256.0, Math.max(gs.maxRange, dist + 24.0));
            gs.maxNodes = Math.max(gs.maxNodes, 16384);
            gs.skipDirectWalk = true;
        }
        issuingRouteMove = true;
        try {
            if (followRouteFly) flyTo(point.x, point.y, point.z, followRouteSpeed);
            else moveTo(point.x, point.y, point.z, followRouteSpeed);
        } finally {
            issuingRouteMove = false;
        }
        boolean lastStop = !followRouteLoop && followRouteIndex >= followRoutePoints.size() - 1;
        groundNav.setDampLastWaypoint(lastStop);
        airNav.setDampLastWaypoint(lastStop);
    }

    private boolean followRouteHasMore() {
        if (!followRouteActive || followRoutePoints.isEmpty()) return false;
        if (followRoutePoints.size() == 1) return followRouteLoop;
        return followRouteLoop || followRouteIndex + 1 < followRoutePoints.size();
    }

    /** Intermediate route points: switch before the 0.1 crawl so the NPC does not stall at corners. */
    private boolean closeEnoughToChainRoute() {
        if (activeMoveGoal == null) return false;
        double dx = getX() - activeMoveGoal.x;
        double dy = getY() - activeMoveGoal.y;
        double dz = getZ() - activeMoveGoal.z;
        if (followRouteFly) {
            double r = Math.max(0.65, airNav.settings().waypointRadius);
            return dx * dx + dy * dy + dz * dz <= r * r;
        }
        double r = Math.max(0.65, groundNav.settings().waypointRadius);
        double vert = Math.max(1.0, groundNav.settings().arriveVert);
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= vert;
    }

    private boolean tickFollowRouteChain(boolean exactArrive) {
        if (!followRouteActive) return false;
        if (!exactArrive && !followRouteHasMore()) return false;
        if (!exactArrive && !closeEnoughToChainRoute()) return false;
        if (!advanceFollowRoute()) return false;
        if (followRouteFly) airNav.tick(this, flySmoothing);
        else groundNav.tick(this);
        return true;
    }

    /** @return true when a next waypoint was issued (caller must not stop). */
    private boolean advanceFollowRoute() {
        if (!followRouteActive || followRoutePoints.isEmpty()) return false;
        if (followRoutePoints.size() == 1 && followRouteLoop) {
            Vec3 hold = followRoutePoints.get(0);
            alwaysMovePoint = hold;
            alwaysMoveEntity = null;
            alwaysMoveSpeed = followRouteSpeed;
            activeFlySpeed = followRouteSpeed;
            return true;
        }
        int next = followRouteIndex + 1;
        if (next >= followRoutePoints.size()) {
            if (!followRouteLoop) {
                followRouteActive = false;
                followRoutePoints = java.util.List.of();
                restoreFollowRoutePathSettings();
                return false;
            }
            next = 0;
        }
        followRouteIndex = next;
        issueFollowRouteMove();
        return true;
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

    public int getAttackCooldown() { return attackCooldownTicks; }

    public int getAttackHitDelay() { return attackHitDelayTicks; }

    public String getAttackHand() { return attackHand; }

    /** Comma-separated attack clip names (inspector / scene node field). */
    public String getAttackAnimsCsv() {
        if (attackAnims.isEmpty()) return "attack";
        return String.join(",", attackAnims);
    }

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
        groundNav.stop(this);
        airNav.stop(this);
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
            if (!isFlying()) groundNav.stop(this);
            else airNav.stop(this);
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
                ensureChunksToward(target.position());
                if (isFlying()) {
                    airNav.follow(this, target.getX(), target.getY(), target.getZ(), combatChaseSpeed);
                } else {
                    groundNav.follow(this, target.getX(), target.getY(), target.getZ(), combatChaseSpeed);
                }
                combatChaseRecalc = COMBAT_CHASE_RECALC;
            }
            if (isFlying()) {
                AirPathNavigator.Status st = airNav.tick(this, flySmoothing);
                flightSteering = st == AirPathNavigator.Status.MOVING;
                if (st == AirPathNavigator.Status.FAILED) pathFailedFlag = true;
            } else {
                GroundPathNavigator.Status st = groundNav.tick(this);
                if (st == GroundPathNavigator.Status.FAILED) pathFailedFlag = true;
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
    private double activeFlySpeed = 1.0;

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
            ensureChunksToward(target);
            airNav.follow(this, target.x, target.y, target.z, alwaysMoveSpeed);
            AirPathNavigator.Status st = airNav.tick(this, flySmoothing);
            flightSteering = st == AirPathNavigator.Status.MOVING;
            if (st == AirPathNavigator.Status.FAILED) pathFailedFlag = true;
            trackMoveProgress(target);
        } else {
            flightSteering = false;
            ensureChunksToward(target);
            groundNav.follow(this, target.x, target.y, target.z, alwaysMoveSpeed);
            GroundPathNavigator.Status st = groundNav.tick(this);
            if (st == GroundPathNavigator.Status.FAILED) pathFailedFlag = true;
            trackMoveProgress(target);
        }
    }

    private void startGroundMove(double x, double y, double z, double speed, boolean immediate) {
        if (isFlying()) {
            startAirMove(x, y, z, speed, immediate);
            return;
        }
        ensureChunksToward(new Vec3(x, y, z));
        this.getNavigation().stop();
        if (immediate) {
            groundNav.moveTo(this, x, y, z, speed);
        } else {
            groundNav.follow(this, x, y, z, speed);
        }
        logWalk("ground path goal=" + fmtPos(x, y, z)
                + " arrive=" + groundNav.settings().arriveRadius
                + " immediate=" + immediate);
    }

    private void startAirMove(double x, double y, double z, double speed, boolean immediate) {
        ensureChunksToward(new Vec3(x, y, z));
        this.getNavigation().stop();
        if (immediate) {
            airNav.moveTo(this, x, y, z, speed);
        } else {
            airNav.follow(this, x, y, z, speed);
        }
        logWalk("air path goal=" + fmtPos(x, y, z)
                + " arrive=" + airNav.settings().arriveRadius
                + " immediate=" + immediate);
    }

    /** Keep issuing custom ground/air moves until {@link #hasReachedMoveGoal()}. */
    private void tickScriptedMoveTo() {
        if (activeMoveGoal == null || alwaysMovePoint != null || alwaysMoveEntity != null) return;
        if (isFlying()) {
            tickScriptedFlyTo();
            return;
        }
        if (tickFollowRouteChain(false)) return;
        GroundPathNavigator.Status st = groundNav.tick(this);
        if (st == GroundPathNavigator.Status.ARRIVED || hasReachedMoveGoal()) {
            logWalk("ARRIVED goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z));
            if (tickFollowRouteChain(true)) return;
            clearScriptedMoveGoal();
            releaseForcedPathChunks();
            return;
        }
        if (st == GroundPathNavigator.Status.FAILED) {
            pathFailedFlag = true;
            logWalk("PATH FAILED goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z));
        }
        trackMoveProgress(activeMoveGoal);
    }

    private void tickScriptedFlyTo() {
        if (flyFollowEntityUuid != null) {
            Level level = SprauteEntityCompat.level(this);
            net.minecraft.world.entity.Entity target = null;
            if (level instanceof ServerLevel server) {
                target = server.getEntity(flyFollowEntityUuid);
            }
            if (target == null || !target.isAlive()) {
                flyFollowEntityUuid = null;
            } else {
                activeMoveGoal = target.position();
                airNav.follow(this, target.getX(), target.getY(), target.getZ(), activeFlySpeed);
            }
        }
        if (tickFollowRouteChain(false)) return;
        AirPathNavigator.Status st = airNav.tick(this, flySmoothing);
        flightSteering = st == AirPathNavigator.Status.MOVING;
        if (st == AirPathNavigator.Status.ARRIVED || hasReachedMoveGoal()) {
            logWalk("ARRIVED fly goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z));
            if (tickFollowRouteChain(true)) return;
            clearScriptedMoveGoal();
            releaseForcedPathChunks();
            return;
        }
        if (st == AirPathNavigator.Status.FAILED) {
            pathFailedFlag = true;
            logWalk("AIR PATH FAILED goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z));
        }
        trackMoveProgress(activeMoveGoal);
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
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }

    private void logWalk(String msg) {
        LOGGER.info("[NPC-WALK] {} pos={} {}", npcLabel(), fmtPos(getX(), getY(), getZ()), msg);
    }

    private void ensureChunksToward(Vec3 target) {
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel server)) return;
        if (!loadChunks) {
            releaseForcedPathChunks();
            return;
        }

        ChunkPos from = new ChunkPos(this.blockPosition());
        ChunkPos to = new ChunkPos(new BlockPos(
                net.minecraft.util.Mth.floor(target.x),
                net.minecraft.util.Mth.floor(target.y),
                net.minecraft.util.Mth.floor(target.z)));
        int steps = Math.max(Math.abs(to.x - from.x), Math.abs(to.z - from.z));
        java.util.Set<ChunkPos> next = new java.util.HashSet<>();
        if (steps == 0) {
            next.add(from);
        } else {
            int limit = Math.min(steps, MAX_FORCED_PATH_CHUNKS);
            for (int i = 0; i <= limit; i++) {
                double t = (double) i / limit;
                int cx = from.x + (int) Math.round((to.x - from.x) * t);
                int cz = from.z + (int) Math.round((to.z - from.z) * t);
                next.add(new ChunkPos(cx, cz));
            }
        }
        org.zonarstudio.spraute_engine.scene.ChunkForceTickets.replace(server, forcedPathChunks, next);
        forcedPathChunks.clear();
        forcedPathChunks.addAll(next);
    }

    private void releaseForcedPathChunks() {
        if (forcedPathChunks.isEmpty()) return;
        Level level = SprauteEntityCompat.level(this);
        if (level instanceof ServerLevel server) {
            org.zonarstudio.spraute_engine.scene.ChunkForceTickets.releaseAll(server, forcedPathChunks);
        }
        forcedPathChunks.clear();
    }

    private void tickKeepLoadedChunks() {
        Level level = SprauteEntityCompat.level(this);
        if (!(level instanceof ServerLevel server)) return;
        if (!loadChunks) {
            releaseKeepLoadedChunks();
            return;
        }
        ChunkPos here = new ChunkPos(this.blockPosition());
        if (here.equals(lastKeepLoadedChunk) && !keepLoadedChunks.isEmpty()) return;
        lastKeepLoadedChunk = here;
        java.util.Set<ChunkPos> next = new java.util.HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                next.add(new ChunkPos(here.x + dx, here.z + dz));
            }
        }
        org.zonarstudio.spraute_engine.scene.ChunkForceTickets.replace(server, keepLoadedChunks, next);
        keepLoadedChunks.clear();
        keepLoadedChunks.addAll(next);
    }

    private void releaseKeepLoadedChunks() {
        lastKeepLoadedChunk = null;
        if (keepLoadedChunks.isEmpty()) return;
        Level level = SprauteEntityCompat.level(this);
        if (level instanceof ServerLevel server) {
            org.zonarstudio.spraute_engine.scene.ChunkForceTickets.releaseAll(server, keepLoadedChunks);
        }
        keepLoadedChunks.clear();
    }

    private void tickPathChunkTickets() {
        boolean longMove = activeMoveGoal != null
                && !isFlying()
                && this.position().distanceToSqr(activeMoveGoal) > 4.0;
        boolean following = alwaysMovePoint != null || alwaysMoveEntity != null || followRouteActive;
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
        } else if (groundNav.isInProgress() || (isFlying() && !this.getNavigation().isDone())) {
            moveStuckTicks++;
            if (moveStuckTicks >= MOVE_STUCK_THRESHOLD) {
                pathFailedFlag = true;
                logWalk("STUCK goal=" + fmtPos(goal.x, goal.y, goal.z)
                        + " stuckTicks=" + moveStuckTicks
                        + " navigating=" + groundNav.isInProgress());
            }
        }
    }

    /** Separation steering — мягко расталкивает НПС друг от друга при сближении. */
    private void tickSeparation() {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        // Scripted move_to: no lateral push — it causes left/right wobble and path failures
        if (activeMoveGoal != null && alwaysMovePoint == null && alwaysMoveEntity == null) return;
        // Применяем только если НПС активно движется
        boolean isMoving = groundNav.isInProgress() || airNav.isInProgress()
                || alwaysMovePoint != null || alwaysMoveEntity != null;
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
        if (!SprauteEntityCompat.level(this).isClientSide) {
            applyPathStepHeight();
        }
        float bodyStart = this.sprauteBodyYawHasEndOfTick ? this.sprauteBodyYawEndOfTick : this.yBodyRot;
        super.tick();
        if (SprauteEntityCompat.level(this).isClientSide) {
            float targetBody = this.entityData.get(SYNCED_BODY_YAW);
            this.yBodyRot = net.minecraft.util.Mth.approachDegrees(bodyStart, targetBody, clientBodyTurnSpeed());
            this.yHeadRot = this.yBodyRot;
            this.setYRot(this.yBodyRot);
            this.sprauteBodyYawEndOfTick = this.yBodyRot;
            this.sprauteBodyYawHasEndOfTick = true;
            return;
        }
        this.yBodyRot = bodyStart;
        this.yHeadRot = bodyStart;
        this.setYRot(bodyStart);
        if (pendingLocomotion != null) {
            NpcLocomotionSnapshot snap = pendingLocomotion;
            pendingLocomotion = null;
            restoreLocomotion(snap);
        }
        tickAlwaysMove();
        tickScriptedMoveTo();
        tickCombat();
        tickSeparation();
        if (activeMoveGoal != null && alwaysMovePoint == null && alwaysMoveEntity == null) {
            if (--walkLogCooldown <= 0) {
                walkLogCooldown = 40;
                double dx = getX() - activeMoveGoal.x;
                double dz = getZ() - activeMoveGoal.z;
                logWalk("tick goal=" + fmtPos(activeMoveGoal.x, activeMoveGoal.y, activeMoveGoal.z)
                        + " distH=" + String.format("%.2f", Math.sqrt(dx * dx + dz * dz))
                        + " navigating=" + (isFlying() ? airNav.isInProgress() : groundNav.isInProgress())
                        + " pathFailed=" + pathFailedFlag);
            }
        }
        tickPathChunkTickets();
        tickKeepLoadedChunks();
        publishLocomotion();
        isMoving();
        tickLookSystem();
        tickScriptPickup();
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
                // Scripted moveTo turns faster so the body keeps up with sharp path corners.
                float turnSpeed = scriptedMove ? getWalkTurnSpeed() * 4f : getWalkTurnSpeed();
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
        tickBodyWalking(getWalkTurnSpeed());
    }

    private void tickBodyWalking(float turnSpeed) {
        Vec3 faceTarget = null;
        if (!isFlying()) {
            Vec3 steer = groundNav.currentSteerTarget();
            if (steer != null) faceTarget = steer;
            else if (alwaysMovePoint != null) faceTarget = alwaysMovePoint;
            else if (activeMoveGoal != null) faceTarget = activeMoveGoal;
        } else {
            Vec3 steer = airNav.currentSteerTarget();
            if (steer != null) faceTarget = steer;
            else if (alwaysMovePoint != null) faceTarget = alwaysMovePoint;
            else if (activeMoveGoal != null) faceTarget = activeMoveGoal;
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
        float nextBody = net.minecraft.util.Mth.approachDegrees(this.yBodyRot, targetYaw, getIdleTurnSpeed());
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
        c.putFloat("WalkTurnSpeed", getWalkTurnSpeed());
        c.putFloat("IdleTurnSpeed", getIdleTurnSpeed());
        var path = groundNav.settings();
        c.putDouble("PathArriveRadius", path.arriveRadius);
        c.putDouble("PathArriveVert", path.arriveVert);
        c.putDouble("PathStepHeight", path.stepHeight);
        c.putInt("PathMaxJump", path.maxJump);
        c.putInt("PathMaxDrop", path.maxDrop);
        c.putDouble("PathMaxRange", path.maxRange);
        c.putBoolean("PathDiagonal", path.allowDiagonal);
        c.putBoolean("PathAvoidWater", path.avoidWater);
        c.putBoolean("PathAvoidLava", path.avoidLava);
        c.putInt("PathMaxNodes", path.maxNodes);
        c.putBoolean("PathSnapOnArrive", path.snapOnArrive);
        var air = airNav.settings();
        c.putDouble("FlyArriveRadius", air.arriveRadius);
        c.putDouble("FlyMaxRange", air.maxRange);
        c.putBoolean("FlyDiagonal", air.allowDiagonal);
        c.putBoolean("FlyAvoidWater", air.avoidWater);
        c.putBoolean("FlyAvoidLava", air.avoidLava);
        c.putInt("FlyMaxNodes", air.maxNodes);
        c.putBoolean("FlySnapOnArrive", air.snapOnArrive);
        c.putFloat("HitboxWidth", getHitboxWidth());
        c.putFloat("HitboxHeight", getHitboxHeight());
        c.putFloat("HitboxOffsetX", getHitboxOffsetX());
        c.putFloat("HitboxOffsetY", getHitboxOffsetY());
        c.putFloat("HitboxOffsetZ", getHitboxOffsetZ());
        c.putFloat("ModelRotationX", getModelRotationX());
        c.putFloat("ModelRotationY", getModelRotationY());
        c.putFloat("ModelRotationZ", getModelRotationZ());
        c.putFloat("ModelScaleX", getModelScaleX());
        c.putFloat("ModelScaleY", getModelScaleY());
        c.putFloat("ModelScaleZ", getModelScaleZ());
        c.putBoolean("Translucent", isTranslucent());
        c.putFloat("Opacity", getOpacity());
        c.putBoolean("LodEnabled", isLodEnabled());
        c.putFloat("RenderDistance", getRenderDistance());
        c.putBoolean("ForceVisible", isForceVisible());
        c.putBoolean("LoadChunks", loadChunks);

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
        if (dropXp > 0) c.putInt("DropXp", dropXp);
        NpcLocomotionSnapshot live = captureLocomotion();
        if (live != null) live.writeNbt(c);

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
        if (c.contains("WalkTurnSpeed")) setWalkTurnSpeed(c.getFloat("WalkTurnSpeed"));
        if (c.contains("IdleTurnSpeed")) setIdleTurnSpeed(c.getFloat("IdleTurnSpeed"));
        var path = groundNav.settings();
        if (c.contains("PathArriveRadius")) path.arriveRadius = c.getDouble("PathArriveRadius");
        if (c.contains("PathArriveVert")) path.arriveVert = c.getDouble("PathArriveVert");
        if (c.contains("PathStepHeight")) path.stepHeight = c.getDouble("PathStepHeight");
        if (c.contains("PathMaxJump")) path.maxJump = c.getInt("PathMaxJump");
        if (c.contains("PathMaxDrop")) path.maxDrop = c.getInt("PathMaxDrop");
        if (c.contains("PathMaxRange")) path.maxRange = c.getDouble("PathMaxRange");
        if (c.contains("PathDiagonal")) path.allowDiagonal = c.getBoolean("PathDiagonal");
        if (c.contains("PathAvoidWater")) path.avoidWater = c.getBoolean("PathAvoidWater");
        if (c.contains("PathAvoidLava")) path.avoidLava = c.getBoolean("PathAvoidLava");
        if (c.contains("PathMaxNodes")) path.maxNodes = c.getInt("PathMaxNodes");
        if (c.contains("PathSnapOnArrive")) path.snapOnArrive = c.getBoolean("PathSnapOnArrive");
        path.clamp();
        applyPathStepHeight();
        var air = airNav.settings();
        if (c.contains("FlyArriveRadius")) air.arriveRadius = c.getDouble("FlyArriveRadius");
        if (c.contains("FlyMaxRange")) air.maxRange = c.getDouble("FlyMaxRange");
        if (c.contains("FlyDiagonal")) air.allowDiagonal = c.getBoolean("FlyDiagonal");
        if (c.contains("FlyAvoidWater")) air.avoidWater = c.getBoolean("FlyAvoidWater");
        if (c.contains("FlyAvoidLava")) air.avoidLava = c.getBoolean("FlyAvoidLava");
        if (c.contains("FlyMaxNodes")) air.maxNodes = c.getInt("FlyMaxNodes");
        if (c.contains("FlySnapOnArrive")) air.snapOnArrive = c.getBoolean("FlySnapOnArrive");
        air.clamp();
        setModelRotation(
                c.contains("ModelRotationX") ? c.getFloat("ModelRotationX") : 0f,
                c.contains("ModelRotationY") ? c.getFloat("ModelRotationY") : 0f,
                c.contains("ModelRotationZ") ? c.getFloat("ModelRotationZ") : 0f);
        setModelScale(
                c.contains("ModelScaleX") ? c.getFloat("ModelScaleX") : 1f,
                c.contains("ModelScaleY") ? c.getFloat("ModelScaleY") : 1f,
                c.contains("ModelScaleZ") ? c.getFloat("ModelScaleZ") : 1f);
        if (c.contains("Translucent")) setTranslucent(c.getBoolean("Translucent"));
        if (c.contains("Opacity")) setOpacity(c.getFloat("Opacity"));
        if (c.contains("LodEnabled")) setLodEnabled(c.getBoolean("LodEnabled"));
        if (c.contains("RenderDistance")) setRenderDistance(c.getFloat("RenderDistance"));
        if (c.contains("ForceVisible")) setForceVisible(c.getBoolean("ForceVisible"));
        if (c.contains("LoadChunks")) setLoadChunks(c.getBoolean("LoadChunks"));

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
        if (c.contains("DropXp")) setDropXp(c.getInt("DropXp"));
        this.setCanPickUpLoot(true);
        pendingLocomotion = NpcLocomotionSnapshot.readNbt(c);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (var rule : customDrops) {
            if (this.random.nextInt(100) < rule.chance) {
                int amount = rule.min + this.random.nextInt(Math.max(1, rule.max - rule.min + 1));
                net.minecraft.world.item.ItemStack stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(rule.itemId, amount, rule.nbt);
                if (stack.isEmpty()) {
                    var spec = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(rule.itemId);
                    if (!spec.id().contains(":")) {
                        stack = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(
                                org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.formatSpec(
                                        "spraute_engine:" + spec.id(), spec.nbt()),
                                amount, rule.nbt);
                    }
                }
                if (!stack.isEmpty()) this.spawnAtLocation(stack);
            }
        }
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20d)
                .add(Attributes.MOVEMENT_SPEED, 0.3d)
                .add(Attributes.FLYING_SPEED, 0.4d)
                // Path search radius; default 16 truncates long moveTo (~4 blocks short on ~20-block walks).
                .add(Attributes.FOLLOW_RANGE, 48d);
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
                || HITBOX_OFFSET_X.equals(pKey) || HITBOX_OFFSET_Y.equals(pKey) || HITBOX_OFFSET_Z.equals(pKey)
                || MODEL_SCALE_X.equals(pKey) || MODEL_SCALE_Y.equals(pKey) || MODEL_SCALE_Z.equals(pKey)) {
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
                this,
                getHitboxWidth() * Math.max(getModelScaleX(), getModelScaleZ()),
                getHitboxHeight() * getModelScaleY(),
                getHitboxOffsetX() * getModelScaleX(),
                getHitboxOffsetY() * getModelScaleY(),
                getHitboxOffsetZ() * getModelScaleZ());
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (!SprauteEntityCompat.level(this).isClientSide) {
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                clearPublishedLocomotion();
            } else {
                publishLocomotion();
            }
        }
        releaseForcedPathChunks();
        releaseKeepLoadedChunks();
        NpcBonePoseSolver.remove(this.getUUID());
        super.remove(reason);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pPose) {
        float w = this.entityData.get(HITBOX_WIDTH) * Math.max(getModelScaleX(), getModelScaleZ());
        float h = this.entityData.get(HITBOX_HEIGHT) * getModelScaleY();
        return net.minecraft.world.entity.EntityDimensions.scalable(w, h);
    }

    /** @return true if this NPC is currently walking (with debounce to avoid flicker). */
    public boolean isMoving() {
        if (SprauteEntityCompat.level(this).isClientSide) {
            return this.entityData.get(IS_MOVING_SYNCED);
        }
        boolean scriptedWalking = activeMoveGoal != null
                && alwaysMovePoint == null
                && alwaysMoveEntity == null
                && !hasReachedMoveGoal();
        boolean groundWalking = !isFlying() && groundNav.isInProgress();
        boolean airFlying = isFlying() && airNav.isInProgress();
        boolean rawMoving = flightSteering || groundWalking || airFlying || scriptedWalking;
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
