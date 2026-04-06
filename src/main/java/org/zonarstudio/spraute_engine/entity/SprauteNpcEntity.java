package org.zonarstudio.spraute_engine.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SprauteNpcEntity extends PathfinderMob {

    // ========== Synced data (model/texture/animation file) ==========
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> MODEL_RES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TEXTURE_RES =
        net.minecraft.network.syncher.SynchedEntityData.defineId(SprauteNpcEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> ANIMATION_RES =
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

    public final java.util.Map<String, Object> customData = new java.util.concurrent.ConcurrentHashMap<>();

    public final java.util.List<org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule> customDrops = new java.util.concurrent.CopyOnWriteArrayList<>();

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
        this.entityData.define(HEAD_LOOK_ACTIVE, false);
        this.entityData.define(HEAD_LOOK_YAW, 0f);
        this.entityData.define(HEAD_LOOK_PITCH, 0f);
        this.entityData.define(HEAD_BONE_NAME, "Head");
        this.entityData.define(HEAD_LOOK_TARGET_GEN, 0);
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
        this.entityData.define(IS_MOVING_SYNCED, false);
        this.entityData.define(HAS_COLLISION, true);
        this.entityData.define(HITBOX_WIDTH, 0.6f);
        this.entityData.define(HITBOX_HEIGHT, 1.8f);
    }

    // ========== Model/Texture/Animation resources ==========
    public void setModel(String v) { this.entityData.set(MODEL_RES, v); }
    public String getModel() { return this.entityData.get(MODEL_RES); }
    public void setTexture(String v) { this.entityData.set(TEXTURE_RES, v); }
    public String getTexture() { return this.entityData.get(TEXTURE_RES); }
    public void setAnimation(String v) { this.entityData.set(ANIMATION_RES, v); }
    public String getAnimation() { return this.entityData.get(ANIMATION_RES); }

    public void setHitbox(float width, float height) {
        this.entityData.set(HITBOX_WIDTH, width);
        this.entityData.set(HITBOX_HEIGHT, height);
        this.refreshDimensions();
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

    /** Clamp look direction so head never turns past {@link #MAX_HEAD_YAW} / pitch limits vs current body. */
    private void applySyncedHeadLookClamped(float targetWorldYaw, float targetPitch) {
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
        if (this.level.isClientSide) return;
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
        if (lookEntity != null || lookPointChanged(lookPoint, x, y, z)) bumpHeadLookTargetGen();
        lookEntity = null;
        lookPoint = new Vec3(x, y, z);
        lookActive = true;
        bodyDelayTicks = BODY_START_DELAY_TICKS;
        this.entityData.set(ALWAYS_LOOK_ACTIVE, true);
        refreshSyncedHeadLookNow();
    }

    /** Continuously look at an entity (player/npc/mob). */
    public void alwaysLookAtEntity(net.minecraft.world.entity.Entity e) {
        alwaysLookAtEntity(e, true);
    }
    public void alwaysLookAtEntity(net.minecraft.world.entity.Entity e, boolean head) {
        if (lookEntity != e || lookPoint != null) bumpHeadLookTargetGen();
        lookEntity = e;
        lookPoint = null;
        lookActive = true;
        bodyDelayTicks = BODY_START_DELAY_TICKS;
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
    public int countItem(String itemId, String nbtTag) {
        net.minecraft.resources.ResourceLocation target = net.minecraft.resources.ResourceLocation.parse(itemId);
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
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(net.minecraft.resources.ResourceLocation.parse(pickupMaxItemId))) return false;
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
        java.util.UUID thrower = itemEntity.getThrower();
        if (pickupDropperFilter != null && (thrower == null || !thrower.equals(pickupDropperFilter))) return;
        lastPickupThrower = thrower;
        net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
        int toTake = stack.getCount();
        if (pickupMaxItemId != null && pickupMaxCount >= 0) {
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(net.minecraft.resources.ResourceLocation.parse(pickupMaxItemId))) return;
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

    public void moveTo(double x, double y, double z, double speed) {
        this.getNavigation().moveTo(x, y, z, speed);
    }

    // ========== alwaysMoveTo ==========
    private net.minecraft.world.entity.Entity alwaysMoveEntity = null;
    private Vec3 alwaysMovePoint = null;
    private double alwaysMoveSpeed = 1.0;

    public void alwaysMoveTo(double x, double y, double z, double speed) {
        this.alwaysMovePoint = new Vec3(x, y, z);
        this.alwaysMoveEntity = null;
        this.alwaysMoveSpeed = speed;
    }

    public void alwaysMoveToEntity(net.minecraft.world.entity.Entity e, double speed) {
        this.alwaysMoveEntity = e;
        this.alwaysMovePoint = null;
        this.alwaysMoveSpeed = speed;
    }

    public void stopMove() {
        this.alwaysMoveEntity = null;
        this.alwaysMovePoint = null;
        this.getNavigation().stop();
    }

    private void tickAlwaysMove() {
        if (this.level.isClientSide) return;
        if (alwaysMoveEntity != null) {
            if (alwaysMoveEntity.isAlive()) {
                if (this.tickCount % 10 == 0) {
                    this.getNavigation().moveTo(alwaysMoveEntity, alwaysMoveSpeed);
                }
            } else {
                alwaysMoveEntity = null;
            }
        } else if (alwaysMovePoint != null) {
            if (this.tickCount % 10 == 0) {
                this.getNavigation().moveTo(alwaysMovePoint.x, alwaysMovePoint.y, alwaysMovePoint.z, alwaysMoveSpeed);
            }
        }
    }

    @Override
    public void tick() {
        float bodyStart = this.sprauteBodyYawHasEndOfTick ? this.sprauteBodyYawEndOfTick : this.yBodyRot;
        super.tick();
        if (this.level.isClientSide) {
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
        isMoving();
        tickLookSystem();
        fixBodyYawSamplingContinuity();
        this.sprauteBodyYawEndOfTick = this.yBodyRot;
        this.sprauteBodyYawHasEndOfTick = true;
    }

    // -------------------- look system (server) --------------------

    private void tickLookSystem() {
        boolean moving = isMoving();

        Vec3 target = resolveLookTarget();

        if (moving) {
            tickBodyWalking();
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
        var path = this.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            BlockPos next = path.getNextNodePos();
            float walkYaw = calcTargetYaw(next.getX() + 0.5, next.getZ() + 0.5);
            float nextBody = net.minecraft.util.Mth.approachDegrees(this.yBodyRot, walkYaw, BODY_TURN_SPEED);
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
        float nextBody = net.minecraft.util.Mth.approachDegrees(this.yBodyRot, targetYaw, BODY_TURN_SPEED);
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
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation(rule.itemId.contains(":") ? rule.itemId : "minecraft:" + rule.itemId)
                );
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
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20d).add(Attributes.MOVEMENT_SPEED, 0.3d);
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling(); // Could be customized if needed
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);
        if (HITBOX_WIDTH.equals(pKey) || HITBOX_HEIGHT.equals(pKey)) {
            refreshDimensions();
        }
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pPose) {
        float w = this.entityData.get(HITBOX_WIDTH);
        float h = this.entityData.get(HITBOX_HEIGHT);
        return net.minecraft.world.entity.EntityDimensions.scalable(w, h);
    }

    /** @return true if this NPC is currently walking (with debounce to avoid flicker). */
    public boolean isMoving() {
        if (this.level.isClientSide) {
            return this.entityData.get(IS_MOVING_SYNCED);
        }
        boolean rawMoving = this.getNavigation().isInProgress();
        if (rawMoving) movingStateTicks = Math.min(movingStateTicks + 1, MOVING_DEBOUNCE);
        else movingStateTicks = Math.max(movingStateTicks - 1, -MOVING_DEBOUNCE);
        boolean moving = movingStateTicks > 0;
        this.entityData.set(IS_MOVING_SYNCED, moving);
        return moving;
    }
}
