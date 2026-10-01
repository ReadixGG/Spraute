package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 2 of the "Global World / Local World" feature (see Сессия 13 in the project doc):
 * gives every online player their own private, independently-simulated copy of whatever
 * sits under the LOCAL_WORLD template node.
 *
 * <p>How it works: on join, the whole LOCAL_WORLD subtree is deep-copied (fresh node ids)
 * and attached as real children of that player's own live PLAYER node — reusing the exact
 * same tree ({@link SceneDocument#findNode}, {@link SceneDocument#findParent}, {@code walk}/
 * {@code walkActive}) that everything else in the engine already relies on, and reusing the
 * exact same per-node-type spawn dispatch ({@link SceneRuntime#spawnSubtree}) that prefabs use.
 * Every cloned node is tagged with {@link SceneNode#ownerPlayerUuid}; every entity spawned
 * for it additionally carries a {@value SceneNode#LOCAL_OWNER_TAG_PREFIX}-prefixed entity tag.
 * {@code Spraute_engine.onStartTracking} cancels Forge's {@code PlayerEvent.StartTracking} for
 * any player other than the owner, which is the standard, documented mechanism for hiding a
 * spawned entity from specific players — no spatial trick, no packet-pipeline hacking.
 *
 * <p>Because the clone lives under the PLAYER node, {@link SceneRepository}'s save path already
 * drops it for free ({@code persistentCopy} strips every PLAYER child before writing to disk) —
 * nothing here needs to know about persistence at all.
 *
 * <p>Known limitation (v1, documented rather than silently missing): PARTICLE / LIGHT /
 * AUDIO_SOURCE / POST_PROCESS content under Local World is cloned and simulated once per
 * online player like everything else, but their in-world side effects (burst packets, the
 * physical {@code minecraft:light} block, ambient sound) are not currently filtered per
 * player the way entities and hitboxes are — those still reach/affect every nearby player.
 * Scripts are already fully private "for free": each clone gets its own fresh node id, so
 * {@link ScriptManager#runInstance} already gives it an independent variable scope.
 */
public final class LocalWorldInstancing {
    private LocalWorldInstancing() {}

    /** Script instance ids started for one player's clone, keyed by {@code sceneId + "@" + playerUuid}. */
    private static final Map<String, List<String>> SCRIPT_INSTANCES = new ConcurrentHashMap<>();

    private static boolean hasInstance(SceneNode playerNode) {
        if (playerNode == null) return false;
        for (SceneNode child : playerNode.children) {
            if (child.ownerPlayerUuid != null) return true;
        }
        return false;
    }

    /**
     * Spawns {@code player}'s private copy of the Local World template under their own
     * PLAYER node. No-op if that player already has an instance (re-sync tick / dimension
     * change) or if Local World is empty.
     */
    public static void spawnFor(SceneActiveState state, ServerLevel level, ServerPlayer player, SceneNode playerNode) {
        if (state == null || level == null || player == null || playerNode == null) return;
        if (hasInstance(playerNode)) return;
        SceneNode localWorld = state.document.ensureLocalWorldNode();
        if (localWorld.children.isEmpty()) return;

        String ownerUuid = player.getUUID().toString();
        String key = state.sceneId + "@" + ownerUuid;
        List<String> scriptInstances = new ArrayList<>();
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        List<UUID> spawned = new ArrayList<>();
        List<String> trackedKeys = new ArrayList<>();

        for (SceneNode template : localWorld.children) {
            SceneNode clone = template.deepCopyWithNewIds();
            tagOwner(clone, ownerUuid);
            try {
                SceneNodeIds.assignTree(state.document, clone);
                playerNode.children.add(clone);
            } finally {
                SceneNodeIds.clearPending();
            }
            Map<String, Object> runtimeObjects = new HashMap<>();
            // No parent transform (null == identity): a Local World clone sits at the exact
            // same coordinates the template describes, same as its shared Global World cousin —
            // only visibility differs, never position.
            SceneRuntime.spawnSubtree(level, state.sceneId, state, clone, null, playerNode,
                    spawned, trackedKeys, runtimeObjects, scriptInstances, source);
            for (var entry : runtimeObjects.entrySet()) {
                state.putRuntimeObject(entry.getKey(), entry.getValue());
            }
        }
        if (!scriptInstances.isEmpty()) {
            SCRIPT_INSTANCES.put(key, scriptInstances);
        }
    }

    /** Tears down {@code playerNode}'s private Local World copy (player left, scene still running). */
    public static void despawnFor(SceneActiveState state, SceneNode playerNode) {
        if (state == null || playerNode == null) return;
        List<SceneNode> roots = new ArrayList<>();
        for (SceneNode child : playerNode.children) {
            if (child.ownerPlayerUuid != null) roots.add(child);
        }
        for (SceneNode root : roots) {
            state.removeNodeSubtree(root.id);
        }
        if (playerNode.playerUuid != null && !playerNode.playerUuid.isBlank()) {
            stopScripts(state.sceneId + "@" + playerNode.playerUuid);
        }
    }

    /** Stops every player's Local World script instances for a scene (scene unload). */
    public static void stopScriptsForScene(String sceneId) {
        if (sceneId == null) return;
        String prefix = sceneId + "@";
        for (String key : new ArrayList<>(SCRIPT_INSTANCES.keySet())) {
            if (key.startsWith(prefix)) stopScripts(key);
        }
    }

    private static void stopScripts(String key) {
        List<String> instances = SCRIPT_INSTANCES.remove(key);
        if (instances == null || instances.isEmpty()) return;
        ScriptManager scripts = ScriptManager.getInstance();
        if (scripts == null) return;
        for (String instanceId : instances) scripts.stopInstance(instanceId);
    }

    private static void tagOwner(SceneNode node, String ownerUuid) {
        node.ownerPlayerUuid = ownerUuid;
        for (SceneNode child : node.children) tagOwner(child, ownerUuid);
    }
}
