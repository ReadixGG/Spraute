package org.zonarstudio.spraute_engine.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.entity.SprauteWorldCanvasEntity;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of world-space canvases ("3D canvas"): a CANVAS node with
 * {@link SceneNode#canvasWorldSpace} is not pushed to anyone's screen — it is drawn as a plane in
 * the world by a {@link SprauteWorldCanvasEntity} that this runtime keeps in sync with the node.
 *
 * <p>The widgets are the canvas' own children, so the panel's content is exactly
 * {@link SceneCanvasBuilder#buildJson} of that node — the same JSON an on-screen canvas would
 * send to a player's GUI. It is rebuilt every {@code canvasWorldRefresh} ticks (or immediately on
 * {@code node.emit()}), so script edits to the widgets show up on the panel; unchanged content
 * costs nothing on the network, since synced entity data only sends diffs.
 */
public final class SceneWorldCanvasRuntime {
    /** One live panel per (scene, node). Server thread only. */
    private static final Map<String, Live> LIVE = new ConcurrentHashMap<>();

    private SceneWorldCanvasRuntime() {}

    private static final class Live {
        final String sceneId;
        final String nodeId;
        SprauteWorldCanvasEntity entity;
        String json = "";
        long lastBuildTick = -1_000_000L; // "never built" (no overflow in now - last)
        long lastSeenTick;

        Live(String sceneId, String nodeId) {
            this.sceneId = sceneId;
            this.nodeId = nodeId;
        }
    }

    /** True for a CANVAS node that lives in the world instead of on the screen. */
    public static boolean isWorldCanvas(SceneNode node) {
        return node != null && node.type == SceneNodeType.CANVAS && node.canvasWorldSpace;
    }

    /** Call once per server tick, next to the other scene runtimes. */
    public static void tickAll(MinecraftServer server) {
        if (server == null) return;
        sweepStale(server);
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            ServerLevel level = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
            if (level == null) continue;
            state.document.walkActive((node, ignoredParentWorld) -> {
                if (!isWorldCanvas(node)) return;
                try {
                    tick(state, level, node);
                } catch (Exception ignored) {
                    // A broken canvas must not take the scene tick down with it.
                }
            });
        }
    }

    private static void tick(SceneActiveState state, ServerLevel level, SceneNode node) {
        String key = state.sceneId + "|" + node.id;
        Live live = LIVE.computeIfAbsent(key, k -> new Live(state.sceneId, node.id));
        long now = level.getServer().getTickCount();
        live.lastSeenTick = now;

        SceneTransform world = state.document.worldTransform(node.id);
        if (world == null) return;

        int refresh = Mth.clamp(node.canvasWorldRefresh, 1, 200);
        if (now - live.lastBuildTick >= refresh) {
            live.lastBuildTick = now;
            live.json = SceneCanvasBuilder.buildJson(node, state.document.root);
        }

        SprauteWorldCanvasEntity entity = live.entity;
        boolean alive = entity != null && !entity.isRemoved() && SprauteEntityCompat.level(entity) == level;
        if (!alive) {
            if (live.json.isEmpty()) return;
            BlockPos pos = new BlockPos(Mth.floor(world.px), Mth.floor(world.py), Mth.floor(world.pz));
            if (!level.hasChunkAt(pos)) return; // spawn once the chunk is loaded
            entity = new SprauteWorldCanvasEntity(level);
            entity.moveTo(world.px, world.py, world.pz, (float) world.ry, (float) world.rx);
            SceneRuntime.tagSceneEntity(entity, state.sceneId, node);
            apply(entity, node, world, live.json);
            level.addFreshEntity(entity);
            live.entity = entity;
            // Without this, SceneRuntime.shouldRetainSceneEntity() never sees the entity as
            // "live" for this scene and purgeStaleSceneEntities() discards it on scene restart.
            state.putRuntimeObject(node.id, entity);
            return;
        }
        apply(entity, node, world, live.json);
    }

    /** Pushes node state onto the entity; synced data only goes over the network when it changed. */
    private static void apply(SprauteWorldCanvasEntity entity, SceneNode node, SceneTransform world, String json) {
        if (entity.distanceToSqr(world.px, world.py, world.pz) > 1.0e-6) {
            entity.setPos(world.px, world.py, world.pz);
        }
        float yaw = (float) world.ry;
        float pitch = (float) world.rx;
        if (entity.getYRot() != yaw) entity.setYRot(yaw);
        if (entity.getXRot() != pitch) entity.setXRot(pitch);
        entity.setRoll((float) world.rz);

        float sx = (float) Math.abs(world.sx);
        float sy = (float) Math.abs(world.sy);
        float width = Math.max(0.01f, node.canvasWorldWidth) * sx;
        float height = node.canvasWorldHeight;
        entity.setWorldSize(width, height <= 0f ? 0f : height * sy);
        entity.setMode(modeOf(node.canvasWorldOrientation));
        entity.setResolution(node.canvasWorldResolution);
        entity.setMaxDistance(Mth.clamp(node.canvasWorldMaxDistance, 1f, 128f));
        entity.setJson(json);
    }

    /** {@code node.emit()} on a world canvas = "rebuild the panel content right now". */
    public static void forceRebuild(SceneActiveState state, SceneNode node) {
        if (state == null || node == null) return;
        Live live = LIVE.get(state.sceneId + "|" + node.id);
        if (live != null) live.lastBuildTick = -1_000_000L;
    }

    /** Drops the panel of a node that is going away (deleted / deactivated subtree). */
    public static void remove(SceneActiveState state, SceneNode node) {
        if (state == null || node == null) return;
        state.removeRuntimeObject(node.id);
        release(LIVE.remove(state.sceneId + "|" + node.id));
    }

    /**
     * Releases panels whose node was not ticked this tick: node deactivated, deleted, moved into
     * the Local World template, switched back to screen space, or the whole scene stopped.
     */
    private static void sweepStale(MinecraftServer server) {
        if (LIVE.isEmpty()) return;
        long now = server.getTickCount();
        for (Iterator<Live> it = LIVE.values().iterator(); it.hasNext(); ) {
            Live live = it.next();
            // sweepStale runs before the node pass, so a live node was seen last tick.
            if (now - live.lastSeenTick > 2) {
                SceneActiveState state = SceneRuntime.getActiveState(live.sceneId);
                if (state != null) state.removeRuntimeObject(live.nodeId);
                release(live);
                it.remove();
            }
        }
    }

    private static void release(Live live) {
        if (live == null || live.entity == null) return;
        if (!live.entity.isRemoved()) live.entity.discard();
        live.entity = null;
    }

    public static int modeOf(String orientation) {
        String o = orientation == null ? "" : orientation.trim().toLowerCase(Locale.ROOT);
        return switch (o) {
            case "fixed", "static", "wall", "rotation" -> SprauteWorldCanvasEntity.MODE_FIXED;
            case "yaw", "y", "sign", "cylindrical" -> SprauteWorldCanvasEntity.MODE_YAW;
            default -> SprauteWorldCanvasEntity.MODE_BILLBOARD;
        };
    }
}
