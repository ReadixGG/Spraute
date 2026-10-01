package org.zonarstudio.spraute_engine.scene.custom;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of Java-declared {@link GenericNodeType}s.
 * <p>
 * Register once (mod/common setup) — {@link CustomNodeRegistry} merges every registered type
 * into all the lookups it already exposes to scripts and the Inspector, so a registered type
 * is immediately usable as {@code create <id> ... {}} and rendered by the Inspector exactly
 * like a {@code declare inspector} type. No other file needs to change to add a new type.
 * <p>
 * Behavior hooks ({@link GenericNodeType#onCreate}/{@code onRemove}/{@code emit}/{@code tick})
 * are fired from a few small, additive call sites: scene load and {@code create <id>} (onCreate),
 * node removal (onRemove), {@code node.emit()} (emit). {@code tick} is NOT auto-wired yet — call
 * {@link #tickAll} once from the engine's existing per-server-tick scene pass.
 */
public final class GenericNodeTypeRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, GenericNodeType> TYPES = new LinkedHashMap<>();
    private static final Map<String, CustomNodeTypeDef> DEFS = new LinkedHashMap<>();

    private GenericNodeTypeRegistry() {}

    public static synchronized void register(GenericNodeType type) {
        if (type == null || type.id() == null || type.id().isBlank()) return;
        String id = type.id().trim();
        TYPES.put(id, type);
        DEFS.put(id, toDef(id, type));
        LOGGER.info("[GenericNodeType] Registered '{}' ({} field(s))", id, type.fields().size());
    }

    public static GenericNodeType get(String id) {
        return id == null || id.isBlank() ? null : TYPES.get(id.trim());
    }

    public static boolean isRegistered(String id) {
        return get(id) != null;
    }

    /** True if {@code id} is a registered Java type that opted into a world transform. */
    public static boolean usesTransform(String id) {
        GenericNodeType type = get(id);
        return type != null && type.usesTransform();
    }

    /** Editor hook — see {@link GenericNodeType#onEditorCreate}. */
    public static void fireEditorCreate(SceneNode node) {
        if (node == null || node.type != SceneNodeType.CUSTOM) return;
        GenericNodeType type = get(node.customTypeId);
        if (type != null) type.onEditorCreate(node);
    }

    public static Collection<GenericNodeType> types() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** {@link CustomNodeTypeDef} view of a registered type — what {@link CustomNodeRegistry} and the Inspector consume. */
    public static CustomNodeTypeDef def(String id) {
        return id == null || id.isBlank() ? null : DEFS.get(id.trim());
    }

    public static Collection<CustomNodeTypeDef> defs() {
        return Collections.unmodifiableCollection(DEFS.values());
    }

    private static CustomNodeTypeDef toDef(String id, GenericNodeType type) {
        CustomNodeTypeDef def = new CustomNodeTypeDef();
        def.id = id;
        def.label = type.label();
        def.section = type.section();
        def.icon = type.icon();
        def.sourceScript = "<java>";
        def.fields.addAll(type.fields());
        return def;
    }

    /** Fires {@link GenericNodeType#onCreate}, if this CUSTOM node's type is Java-registered. */
    public static void fireCreate(SceneActiveState state, SceneNode node) {
        if (node == null || node.type != SceneNodeType.CUSTOM) return;
        GenericNodeType type = get(node.customTypeId);
        if (type != null) type.onCreate(state, node);
    }

    /** Fires {@link GenericNodeType#onRemove}, if this CUSTOM node's type is Java-registered. */
    public static void fireRemove(SceneActiveState state, SceneNode node) {
        if (node == null || node.type != SceneNodeType.CUSTOM) return;
        GenericNodeType type = get(node.customTypeId);
        if (type != null) type.onRemove(state, node);
    }

    /**
     * Fires {@link GenericNodeType#emit}, if this CUSTOM node's type is Java-registered.
     * @return true if a registered type handled the call (so the caller can fall through to
     *         whatever it did before this existed if it didn't).
     */
    public static boolean fireEmit(SceneActiveState state, ServerLevel level, SceneNode node) {
        if (node == null || node.type != SceneNodeType.CUSTOM) return false;
        GenericNodeType type = get(node.customTypeId);
        if (type == null) return false;
        type.emit(state, level, node);
        return true;
    }

    /**
     * Call once per server tick (from wherever the engine already ticks scene runtimes, e.g.
     * right next to {@code ParticleGameplayRuntime.tick(server)}'s call site) to drive every
     * loaded scene's Java-registered CUSTOM nodes' {@link GenericNodeType#tick}.
     */
    public static void tickAll(MinecraftServer server) {
        if (server == null || TYPES.isEmpty()) return;
        for (GenericNodeType type : TYPES.values()) {
            try {
                type.serverTick(server);
            } catch (Exception e) {
                LOGGER.warn("[GenericNodeType] serverTick of '{}' failed: {}", type.id(), e.toString());
            }
        }
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            ServerLevel level = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
            if (level == null) continue;
            state.document.walkActive((node, ignoredParentWorld) -> {
                if (node.type != SceneNodeType.CUSTOM || node.customTypeId == null || node.customTypeId.isBlank()) return;
                GenericNodeType type = get(node.customTypeId);
                if (type == null) return;
                try {
                    type.tick(state, level, node);
                } catch (Exception e) {
                    LOGGER.warn("[GenericNodeType] tick of '{}' node '{}' failed: {}", type.id(), node.id, e.toString());
                }
            });
        }
    }
}
