package org.zonarstudio.spraute_engine.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Runtime carrier of a {@code worldCanvas} node: a GUI canvas drawn as a plane in the world.
 * Spawned/updated only by {@link org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime};
 * the client draws it with {@code SprauteWorldCanvasRenderer}. Visual only (no clicks/hover).
 *
 * <p>Everything is plain synced entity data, so vanilla only sends what actually changed and
 * late joiners get the full state in the spawn packet. The canvas JSON is stored as-is when it
 * fits a synced string (32767 chars); bigger layouts are gzip+base64 packed (UI JSON compresses
 * ~6-10x) instead of being truncated into invalid JSON.
 */
public class SprauteWorldCanvasEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MODE_BILLBOARD = 0;
    public static final int MODE_FIXED = 1;
    public static final int MODE_YAW = 2;

    private static final int SYNC_STRING_LIMIT = 32000;
    private static final String PACKED_PREFIX = "gz:";

    private static final EntityDataAccessor<String> DATA_JSON = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RESOLUTION = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_DISTANCE = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(SprauteWorldCanvasEntity.class, EntityDataSerializers.FLOAT);

    // Server: last raw JSON handed to setJson (skips re-packing identical content every refresh).
    private String lastRawJson;
    // Client: decode cache — getJson() returns the same String instance until the data changes,
    // so per-frame equals() checks downstream short-circuit on identity.
    private String cachedEncoded;
    private String cachedDecoded = "";
    private boolean warnedTooLarge;

    public SprauteWorldCanvasEntity(EntityType<? extends SprauteWorldCanvasEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SprauteWorldCanvasEntity(Level level) {
        this(ModEntities.SPRAUTE_WORLD_CANVAS.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_JSON, "");
        this.entityData.define(DATA_WIDTH, 2.0f);
        this.entityData.define(DATA_HEIGHT, 0.0f);
        this.entityData.define(DATA_MODE, MODE_BILLBOARD);
        this.entityData.define(DATA_RESOLUTION, 2.0f);
        this.entityData.define(DATA_MAX_DISTANCE, 48.0f);
        this.entityData.define(DATA_ROLL, 0.0f);
    }

    // ---- canvas JSON -------------------------------------------------------------------------

    /** Decoded canvas JSON ("" = nothing to draw). Cheap to call every frame. */
    public String getJson() {
        String encoded = this.entityData.get(DATA_JSON);
        if (encoded == cachedEncoded) return cachedDecoded;
        cachedEncoded = encoded;
        cachedDecoded = decode(encoded);
        return cachedDecoded;
    }

    public void setJson(String json) {
        String raw = json == null ? "" : json;
        if (raw.equals(lastRawJson)) return;
        lastRawJson = raw;
        String encoded = encode(raw);
        if (encoded == null) {
            if (!warnedTooLarge) {
                warnedTooLarge = true;
                LOGGER.warn("[Spraute World Canvas] Canvas JSON is too large to sync even compressed ({} chars) — "
                        + "keeping the previous content. Split the canvas into several world canvases.", raw.length());
            }
            return;
        }
        warnedTooLarge = false;
        this.entityData.set(DATA_JSON, encoded);
    }

    private static String encode(String raw) {
        if (raw.length() <= SYNC_STRING_LIMIT && !raw.startsWith(PACKED_PREFIX)) return raw;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, raw.length() / 6));
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(raw.getBytes(StandardCharsets.UTF_8));
            }
            String packed = PACKED_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
            return packed.length() <= SYNC_STRING_LIMIT ? packed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        if (!encoded.startsWith(PACKED_PREFIX)) return encoded;
        try {
            byte[] packed = Base64.getDecoder().decode(encoded.substring(PACKED_PREFIX.length()));
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(packed))) {
                return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    // ---- presentation ------------------------------------------------------------------------

    /** Panel width in blocks. */
    public float getWorldWidth() { return this.entityData.get(DATA_WIDTH); }
    /** Panel height in blocks; {@code <= 0} = derived from the canvas' own aspect ratio. */
    public float getWorldHeight() { return this.entityData.get(DATA_HEIGHT); }

    public void setWorldSize(float width, float height) {
        this.entityData.set(DATA_WIDTH, Math.max(0.01f, width));
        this.entityData.set(DATA_HEIGHT, height <= 0f ? 0f : Math.max(0.01f, height));
    }

    public int getMode() { return this.entityData.get(DATA_MODE); }
    public void setMode(int mode) { this.entityData.set(DATA_MODE, mode); }

    /** Supersampling factor of the off-screen texture (1 = GUI pixels, 2 = twice as sharp …). */
    public float getResolution() { return this.entityData.get(DATA_RESOLUTION); }
    public void setResolution(float resolution) { this.entityData.set(DATA_RESOLUTION, Math.max(0.5f, Math.min(4f, resolution))); }

    public float getMaxDistance() { return this.entityData.get(DATA_MAX_DISTANCE); }
    public void setMaxDistance(float distance) { this.entityData.set(DATA_MAX_DISTANCE, Math.max(1f, distance)); }

    public float getRoll() { return this.entityData.get(DATA_ROLL); }
    public void setRoll(float roll) { this.entityData.set(DATA_ROLL, roll); }

    // ---- culling -----------------------------------------------------------------------------

    /** Frustum culling must see the whole panel, not the 1×1 entity box at its center. */
    @Override
    public AABB getBoundingBoxForCulling() {
        float w = getWorldWidth();
        float h = getWorldHeight() > 0f ? getWorldHeight() : w;
        double r = Math.max(w, h) * 0.5 + 0.25;
        return new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
    }

    /** Hard draw distance (the node's maxDistance) — the renderer is never even called past it. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        float max = getMaxDistance();
        return distanceSq < (double) max * max;
    }

    // ---- runtime-only, never persisted -------------------------------------------------------

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean save(CompoundTag tag) { return false; }

    @Override
    //? if >=1.20.1 {
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
    //?} else {
    /*public Packet<?> getAddEntityPacket() {
    *///?}
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
