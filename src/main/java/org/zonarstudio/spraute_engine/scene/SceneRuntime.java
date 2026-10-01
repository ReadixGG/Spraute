package org.zonarstudio.spraute_engine.scene;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.entity.BillboardManager;
import org.zonarstudio.spraute_engine.entity.ModEntities;
import org.zonarstudio.spraute_engine.entity.ModelManager;
import org.zonarstudio.spraute_engine.entity.NpcLocomotionSnapshot;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.RenderManager;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side scene lifecycle: reads the same {@code .scene.json} the editor
 * writes, spawns real NPC/MODEL entities on load and removes those on unload.
 * Editor previews never reach this class.
 */
public final class SceneRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_PREFIX = "spraute_scene_";

    /** True if this entity was spawned/owned by a scene (not a free script NPC). */
    public static boolean isSceneOwned(Entity entity) {
        if (entity == null) return false;
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(TAG_PREFIX)) return true;
        }
        return false;
    }

    /** Scene id from {@code spraute_scene_<id>} tag, or null for script-spawned billboards. */
    public static String sceneIdFromEntity(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (tag == null) continue;
            if (tag.startsWith(TAG_PREFIX) && !tag.startsWith(TAG_PREFIX + "node_")) {
                String sceneId = tag.substring(TAG_PREFIX.length());
                return sceneId.isEmpty() ? null : sceneId;
            }
        }
        return null;
    }

    /** Scene node id from {@code spraute_scene_node_<id>} tag, or null. */
    public static String nodeIdFromEntityTags(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (tag == null) continue;
            if (tag.startsWith(TAG_PREFIX + "node_")) {
                String nodeId = tag.substring((TAG_PREFIX + "node_").length());
                return nodeId.isEmpty() ? null : nodeId;
            }
        }
        return null;
    }

    /** Live scene node for this spawned entity, or null if it is not in a loaded scene. */
    public static SceneNodeView nodeViewFromEntity(Entity entity) {
        if (entity == null) return null;
        String taggedScene = sceneIdFromEntity(entity);
        String taggedNode = nodeIdFromEntityTags(entity);
        if (taggedScene != null && taggedNode != null) {
            SceneActiveState tagged = getActiveState(taggedScene);
            if (tagged != null) {
                SceneNodeView view = tagged.nodeView(taggedNode);
                if (view != null) return view;
            }
        }
        for (String sid : listLoaded()) {
            SceneActiveState state = getActiveState(sid);
            if (state == null) continue;
            String nodeId = state.findNodeIdByEntity(entity);
            if (nodeId != null) return state.nodeView(nodeId);
        }
        return null;
    }

    /**
     * Script id of an entity: hierarchy node id if it belongs to a scene,
     * otherwise the tracking key from {@code create npc}/{@code create model}/billboard.
     */
    public static String scriptIdFromEntity(Entity entity) {
        if (entity == null) return null;
        SceneNodeView view = nodeViewFromEntity(entity);
        if (view != null) return view.getNodeId();
        String tagged = nodeIdFromEntityTags(entity);
        if (tagged != null) return tagged;
        var uuid = entity.getUUID();
        String id = NpcManager.findId(uuid);
        if (id != null) return id;
        id = ModelManager.findId(uuid);
        if (id != null) return id;
        id = BillboardManager.findId(uuid);
        if (id != null) return id;
        return RenderManager.findId(uuid);
    }

    /**
     * Scene-owned objects must exist only while their scene is loaded.
     * Untagged script spawns are left to {@code remove()} / {@code removeBillboard}.
     */
    public static boolean shouldRetainSceneEntity(Entity entity) {
        if (entity == null) return false;
        String sceneId = sceneIdFromEntity(entity);
        if (sceneId == null) return true;
        if (!isActive(sceneId)) return false;
        List<UUID> live = ACTIVE.get(sceneId);
        if (live != null && live.contains(entity.getUUID())) return true;
        SceneActiveState state = ACTIVE_STATES.get(sceneId);
        if (state != null) {
            for (Object obj : state.runtimeObjectMap().values()) {
                if (obj instanceof Entity e && entity.getUUID().equals(e.getUUID())) return true;
            }
        }
        return false;
    }

    /** @deprecated use {@link #shouldRetainSceneEntity(Entity)} */
    public static boolean shouldRetainSceneBillboard(SprauteBillboardEntity billboard) {
        return shouldRetainSceneEntity(billboard);
    }

    /** Remove scene NPCs/models/billboards left in the world after an unclean stop or world rejoin. */
    public static void purgeStaleSceneEntities(MinecraftServer server) {
        if (server == null) return;
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!isSceneOwned(entity) || shouldRetainSceneEntity(entity)) continue;
                toRemove.add(entity);
            }
            for (Entity entity : toRemove) {
                discardStaleSceneEntity(entity);
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("[Spraute Scene] Purged {} stale scene entity(ies) from inactive scenes", removed);
        }
    }

    /** @deprecated use {@link #purgeStaleSceneEntities(MinecraftServer)} */
    public static void purgeStaleSceneBillboards(MinecraftServer server) {
        purgeStaleSceneEntities(server);
    }

    public static void discardStaleSceneEntity(Entity entity) {
        if (entity == null) return;
        NpcManager.removeUuid(entity.getUUID());
        ModelManager.removeUuid(entity.getUUID());
        BillboardManager.removeByUuid(entity.getUUID());
        entity.discard();
    }

    /** Emergency presentation reset (Ctrl+Shift+U): remove all billboards in the player's dimension. */
    public static int discardBillboardsForPresentationReset(ServerPlayer player) {
        if (player == null) return 0;
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        purgeStaleSceneBillboards(server);
        ServerLevel level = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player);
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SprauteBillboardEntity) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            BillboardManager.removeByUuid(entity.getUUID());
            entity.discard();
        }
        if (!toRemove.isEmpty()) {
            LOGGER.info("[Spraute Scene] Presentation reset removed {} billboard(s) in {}",
                    toRemove.size(), level.dimension().location());
        }
        return toRemove.size();
    }

    /**
     * If {@code entity} belongs to a live scene node, removes that node (and subtree) for good.
     * @return true if a scene node was removed
     */
    public static boolean removeSceneEntity(Entity entity) {
        if (entity == null) return false;
        String sceneId = sceneIdFromEntity(entity);
        String nodeId = nodeIdFromEntityTags(entity);
        if (sceneId == null || nodeId == null || sceneId.isEmpty() || nodeId.isEmpty()) {
            // Fallback: resolve via runtime object map.
            for (String sid : listLoaded()) {
                SceneActiveState state = getActiveState(sid);
                if (state == null) continue;
                String nid = state.findNodeIdByEntity(entity);
                if (nid != null) return removeLiveSubtree(sid, nid);
            }
            return false;
        }
        return removeLiveSubtree(sceneId, nodeId);
    }
    private static final Map<String, List<UUID>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ACTIVE_SCRIPT_INSTANCES = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ACTIVE_TRACKED_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, SceneActiveState> ACTIVE_STATES = new ConcurrentHashMap<>();
    /** Auto-shown GUI canvases per scene (for unload + late joiners). */
    private static final Map<String, List<SceneNode>> ACTIVE_CANVASES = new ConcurrentHashMap<>();
    /** Chunks force-loaded while a scene is active (keeps NPCs alive when players leave). */
    private static final Map<String, Set<ChunkPos>> FORCED_CHUNKS = new ConcurrentHashMap<>();

    private SceneRuntime() {}

    public static boolean isActive(String sceneId) {
        return ACTIVE_STATES.containsKey(sceneId);
    }

    public static SceneActiveState getActiveState(String sceneId) {
        return sceneId == null ? null : ACTIVE_STATES.get(sceneId);
    }

    public static List<String> listLoaded() {
        return List.copyOf(ACTIVE_STATES.keySet());
    }

    /** Tell connected clients which scenes are running (editor Play/Stop on dedicated). */
    public static void broadcastActiveScenes(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendActiveScenesTo(player);
        }
    }

    public static void sendActiveScenesTo(ServerPlayer player) {
        if (player == null) return;
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new org.zonarstudio.spraute_engine.network.SyncActiveScenesPacket(listLoaded()));
        sendStartupVideosTo(player);
    }

    /** Camera rest, audio bus and post-process for one client (late join). */
    public static void syncPresentationTo(ServerPlayer player) {
        if (player == null) return;
        for (String sceneId : listLoaded()) {
            SceneActiveState state = getActiveState(sceneId);
            if (state == null) continue;
            ScenePlayerNodes.pushPresentation(state, player);
        }
    }

    /**
     * Scene scripts belong to the loaded scene, not the player who pressed Play.
     * Restart any that were force-stopped on logout so late join / rejoin still
     * receive {@code on kill}, {@code on death}, and the rest.
     */
    public static void ensureScriptsRunning(MinecraftServer server) {
        if (server == null) return;
        ScriptManager scripts = ScriptManager.getInstance();
        if (scripts == null) return;
        for (String sceneId : listLoaded()) {
            SceneActiveState state = getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            ServerLevel level = resolveLevelForDocument(server, state.runtimeDimension);
            CommandSourceStack source = sceneScriptSource(server, level);
            List<String> scriptInstances = ACTIVE_SCRIPT_INSTANCES.computeIfAbsent(sceneId, k -> new ArrayList<>());
            state.document.walkActive((node, ignored) -> {
                if (!node.type.isScriptLeaf() || !node.runOnSceneStart
                        || node.script == null || node.script.isBlank()) return;
                String instanceId = "scene:" + sceneId + ":node:" + node.id;
                if (scripts.isInstanceRunning(instanceId)) {
                    if (!scriptInstances.contains(instanceId)) scriptInstances.add(instanceId);
                    return;
                }
                SceneNode owner = state.document.findParent(node.id);
                if (owner == null) owner = state.document.root;
                Map<String, Object> variables = new HashMap<>();
                variables.put("_scene", state.sceneView());
                variables.put("_node", state.nodeView(owner));
                variables.put("_scriptNode", state.nodeView(node));
                if (scripts.runInstance(node.script.trim(), instanceId, source, variables)) {
                    if (!scriptInstances.contains(instanceId)) scriptInstances.add(instanceId);
                }
            });
        }
    }

    private static CommandSourceStack sceneScriptSource(MinecraftServer server, ServerLevel level) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);
        return level != null ? source.withLevel(level) : source;
    }

    /** Videos from currently loaded scenes (auto-start cutscenes included). */
    public static List<String> collectActiveVideoSources() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (SceneActiveState state : ACTIVE_STATES.values()) {
            if (state == null || state.document == null || state.document.root == null) continue;
            collectVideoSources(state.document.root, out);
        }
        return new ArrayList<>(out);
    }

    public static void collectVideoSources(SceneNode node, java.util.Set<String> out) {
        if (node == null || out == null) return;
        if (node.type == SceneNodeType.UI_VIDEO && node.uiVideo != null && !node.uiVideo.isBlank()) {
            out.add(node.uiVideo.trim().replace('\\', '/'));
        }
        if (node.children == null) return;
        for (SceneNode child : node.children) collectVideoSources(child, out);
    }

    public static void sendStartupVideosTo(ServerPlayer player) {
        if (player == null) return;
        List<String> sources = collectActiveVideoSources();
        if (sources.isEmpty()) return;
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new org.zonarstudio.spraute_engine.network.PrewarmVideosPacket(sources));
    }

    /** When exactly one scene is running, return it (console / loose scripts). */
    public static SceneActiveState getSingletonActiveState() {
        List<String> ids = listLoaded();
        if (ids.size() != 1) return null;
        return getActiveState(ids.get(0));
    }

    /** {@code scene:my_scene:node:script1} → {@code my_scene}. */
    public static String sceneIdFromInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.startsWith("scene:")) return null;
        int marker = instanceId.indexOf(":node:");
        if (marker > 6) return instanceId.substring(6, marker);
        String rest = instanceId.substring(6);
        if (rest.isEmpty()) return null;
        int colon = rest.indexOf(':');
        return colon > 0 ? rest.substring(0, colon) : rest;
    }

    public static SceneActiveState stateFromInstanceId(String instanceId) {
        String sceneId = sceneIdFromInstanceId(instanceId);
        return sceneId == null ? null : getActiveState(sceneId);
    }

    /**
     * Scene context for script spawns: instance id, then the only loaded scene.
     * Used by {@link org.zonarstudio.spraute_engine.script.SceneScriptSpawn}.
     */
    public static SceneActiveState resolveActiveStateForScript(String instanceId) {
        SceneActiveState fromInstance = stateFromInstanceId(instanceId);
        if (fromInstance != null) return fromInstance;
        return getSingletonActiveState();
    }

    /** Load every scene explicitly marked for server/world startup. */
    public static void loadAutoStartScenes(MinecraftServer server) {
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4);
        Path gameDir = server.getServerDirectory().toPath();
        for (Path file : SceneRepository.listPlayableScenes(gameDir)) {
            try {
                SceneDocument doc = SceneRepository.load(file);
                if (!doc.autoStart) continue;
                String fileName = file.getFileName().toString();
                String sceneId = fileName.substring(0, fileName.length() - SceneRepository.EXTENSION.length());
                if (load(sceneId, source)) {
                    LOGGER.info("[Spraute Scene] Auto-started scene '{}'", sceneId);
                }
            } catch (Exception e) {
                LOGGER.error("[Spraute Scene] Failed to auto-start '{}': {}",
                        file.getFileName(), e.getMessage());
            }
        }
        for (var mount : org.zonarstudio.spraute_engine.resource.StoryPackRegistry.packs()) {
            String sceneId = mount.manifest().scene;
            if (sceneId == null || sceneId.isBlank() || isActive(sceneId)) continue;
            try {
                if (load(sceneId, source)) {
                    LOGGER.info("[Spraute Scene] Started story pack '{}' → '{}'",
                            mount.manifest().id, sceneId);
                }
            } catch (Exception e) {
                LOGGER.error("[Spraute Scene] Failed to start pack scene '{}': {}",
                        sceneId, e.getMessage());
            }
        }
    }

    public static SceneNodeView getNodeView(String sceneId, String pathOrId) {
        SceneActiveState state = getActiveState(sceneId);
        return state == null ? null : state.nodeView(pathOrId);
    }

    public static boolean load(String sceneId, CommandSourceStack source) {
        Path file = SceneRepository.findPlayableScene(org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir(), sceneId);
        if (file == null || !Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("§c[Spraute]§r Сцена не найдена: " + sceneId
                    + "  (воркспейс или сюжетный JAR)"));
            return false;
        }
        ScriptManager scriptsMgr = ScriptManager.getInstance();
        if (scriptsMgr != null) scriptsMgr.refreshFromDisk();
        SceneDocument doc;
        try {
            doc = SceneRepository.load(file);
        } catch (IOException e) {
            source.sendFailure(Component.literal("§c[Spraute]§r Ошибка чтения сцены '" + sceneId + "': " + e.getMessage()));
            return false;
        }

        // Scenes are world-agnostic: spawn in the level of the command / auto-start source.
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Нет доступного мира для запуска сцены"));
            return false;
        }

        // Validate first, then replace any already-loaded instance of this scene.
        if (isActive(sceneId)) {
            unload(sceneId, source);
        }

        List<UUID> spawned = new ArrayList<>();
        List<String> trackedKeys = new ArrayList<>();
        Map<String, Object> runtimeObjects = new HashMap<>();
        SceneActiveState activeState = new SceneActiveState(sceneId, doc);
        activeState.runtimeDimension = level.dimension().location().toString();
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.invalidate(sceneId);
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.ensureForDocument(doc);
        // Inactive nodes (and their subtrees) are dead at runtime: walkActive prunes them.
        doc.walkActive((node, parentWorld) -> {
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            if (node.type == SceneNodeType.NPC) {
                spawnSceneNpc(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            } else if (node.type == SceneNodeType.MODEL) {
                spawnSceneModel(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            } else if (node.type == SceneNodeType.BILLBOARD) {
                spawnSceneBillboard(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            } else if (node.type == SceneNodeType.HITBOX) {
                activeState.registerHitbox(node);
            } else if (node.type == SceneNodeType.PARTICLE) {
                activeState.registerParticle(node);
            } else if (node.type == SceneNodeType.LIGHT) {
                activeState.registerLight(node);
            } else if (node.type == SceneNodeType.AUDIO_SOURCE) {
                activeState.registerAudioSource(node);
            } else if (node.type == SceneNodeType.POST_PROCESS) {
                activeState.registerPostProcess(node);
            } else if (node.type == SceneNodeType.CUSTOM) {
                activeState.putRuntimeObject(node.id, node);
                org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.fireCreate(activeState, node);
            }
        });

        ACTIVE.put(sceneId, spawned);
        ACTIVE_TRACKED_KEYS.put(sceneId, trackedKeys);
        ACTIVE_STATES.put(sceneId, activeState);
        for (var entry : runtimeObjects.entrySet()) {
            activeState.putRuntimeObject(entry.getKey(), entry.getValue());
        }
        ScenePlayerNodes.syncServer(activeState, level.getServer(), true);

        // ANIMATOR nodes: assign their animation file/default clip to the parent entity.
        doc.walkActive((node, ignoredParentWorld) -> {
            if (node.type != SceneNodeType.ANIMATOR) return;
            if (node.animatorFile == null || node.animatorFile.isBlank()) return;
            SceneNode owner = doc.findParent(node.id);
            if (owner == null) return;
            Object obj = runtimeObjects.get(owner.id);
            if (obj instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                npc.setAnimation(node.animatorFile);
                if (node.animatorClip != null && !node.animatorClip.isBlank()) {
                    npc.setIdleAnim(node.animatorClip);
                }
            } else if (obj instanceof org.zonarstudio.spraute_engine.entity.SprauteModelEntity model) {
                model.setAnimation(node.animatorFile);
                if (node.animatorClip != null && !node.animatorClip.isBlank()) {
                    model.setIdleAnim(node.animatorClip);
                }
            }
        });
        List<String> scriptInstances = new ArrayList<>();
        ScriptManager scripts = ScriptManager.getInstance();
        if (scripts != null) {
            doc.walkActive((node, ignoredParentWorld) -> {
                if (!node.type.isScriptLeaf() || !node.runOnSceneStart
                        || node.script == null || node.script.isBlank()) return;
                SceneNode owner = doc.findParent(node.id);
                if (owner == null) owner = doc.root;

                Map<String, Object> variables = new HashMap<>();
                variables.put("_scene", activeState.sceneView());
                variables.put("_node", activeState.nodeView(owner));
                variables.put("_scriptNode", activeState.nodeView(node));

                String instanceId = "scene:" + sceneId + ":node:" + node.id;
                if (scripts.runInstance(node.script.trim(), instanceId,
                        sceneScriptSource(source.getServer(), level), variables)) {
                    scriptInstances.add(instanceId);
                } else {
                    LOGGER.warn("[Spraute Scene] Failed to start script '{}' for scene node {}",
                            node.script, node.id);
                }
            });
        }
        ACTIVE_SCRIPT_INSTANCES.put(sceneId, scriptInstances);

        // GUI canvases: auto-show to every player on the server.
        List<SceneNode> canvases = new ArrayList<>();
        doc.walkActive((node, ignoredParentWorld) -> {
            if (node.type == SceneNodeType.CANVAS) canvases.add(node);
        });
        ACTIVE_CANVASES.put(sceneId, canvases);
        for (SceneNode canvas : canvases) {
            if (!canvas.canvasAutoShow || canvas.canvasWorldSpace) continue;
            for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                showCanvasTo(sceneId, canvas, player);
            }
        }

        LOGGER.info("[Spraute Scene] Loaded scene '{}' with {} entity/entities, {} script(s), {} canvas(es)",
                sceneId, spawned.size(), scriptInstances.size(), canvases.size());
        broadcastActiveScenes(source.getServer());
        return true;
    }

    /**
     * Spawns a prefab ({@code spraute_engine/prefabs/<name>.prefab.json}) into an
     * active scene at world x/y/z. The whole subtree behaves like scene content:
     * entities spawn, hitboxes register, scripts with "run on start" launch and
     * auto-show canvases open. Returns the new node's view for scripts.
     */
    public static SceneNodeView spawnPrefab(String sceneId, String prefabName,
                                            double x, double y, double z,
                                            CommandSourceStack source) {
        return spawnPrefab(sceneId, prefabName, null, x, y, z, source);
    }

    /**
     * Like {@link #spawnPrefab(String, String, double, double, double, CommandSourceStack)},
     * but attaches the instance under the given parent node (x/y/z become local
     * coordinates relative to that parent, like in the editor hierarchy).
     */
    public static SceneNodeView spawnPrefab(String sceneId, String prefabName, String parentNodeId,
                                            double x, double y, double z,
                                            CommandSourceStack source) {
        SceneActiveState state = ACTIVE_STATES.get(sceneId);
        if (state == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Сцена не активна: " + sceneId));
            return null;
        }
        ServerLevel level = resolveLevel(source, state.runtimeDimension);
        if (level == null) return null;
        SceneNode fresh = PrefabRepository.instantiate(
                org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir(), prefabName);
        if (fresh == null) {
            source.sendFailure(Component.literal("§c[Spraute]§r Префаб не найден: " + prefabName));
            return null;
        }
        SceneNode parent = null;
        SceneTransform parentWorld = null;
        if (parentNodeId != null && !parentNodeId.isBlank()) {
            parent = state.document.findNode(parentNodeId);
            if (parent == null) {
                source.sendFailure(Component.literal("§c[Spraute]§r Нода-родитель не найдена: " + parentNodeId));
                return null;
            }
            parentWorld = state.document.worldTransform(parent.id);
        }
        // No explicit parent: land in Global World (shared), not the scene root directly —
        // root itself only accepts PLAYERS/GLOBAL_WORLD/LOCAL_WORLD since the worlds split.
        if (parent == null) parent = state.document.ensureGlobalWorldNode();
        if (fresh.transform == null) fresh.transform = new SceneTransform();
        fresh.transform.px = x;
        fresh.transform.py = y;
        fresh.transform.pz = z;

        SceneNode playerHost = playerHostOf(state.document, parent);
        SceneNode cameraPrefab = PlayerCameraNodes.findCameraIn(fresh);
        if (playerHost != null && cameraPrefab != null) {
            if (cameraPrefab.transform == null) cameraPrefab.transform = new SceneTransform();
            if (cameraPrefab != fresh) {
                cameraPrefab.transform.px = x;
                cameraPrefab.transform.py = y;
                cameraPrefab.transform.pz = z;
            }
            try {
                SceneNodeIds.assignTree(state.document, fresh);
                if (cameraPrefab != fresh) {
                    PlayerCameraNodes.detachInTree(fresh, cameraPrefab);
                }
                PlayerCameraNodes.installCamera(state, playerHost, cameraPrefab);
            } finally {
                SceneNodeIds.clearPending();
            }
            List<UUID> spawned = ACTIVE.computeIfAbsent(sceneId, k -> new ArrayList<>());
            List<String> trackedKeys = ACTIVE_TRACKED_KEYS.computeIfAbsent(sceneId, k -> new ArrayList<>());
            List<String> scriptInstances = ACTIVE_SCRIPT_INSTANCES.computeIfAbsent(sceneId, k -> new ArrayList<>());
            Map<String, Object> runtimeObjects = new HashMap<>();
            SceneTransform playerWorld = state.document.worldTransform(playerHost.id);
            spawnSubtree(level, sceneId, state, cameraPrefab, playerWorld, playerHost,
                    spawned, trackedKeys, runtimeObjects, scriptInstances, source);
            for (var entry : runtimeObjects.entrySet()) {
                state.putRuntimeObject(entry.getKey(), entry.getValue());
            }
            syncPlayerCameraAfterInstall(playerHost, cameraPrefab);
            LOGGER.info("[Spraute Prefab] Installed camera prefab '{}' on player '{}' as '{}'",
                    prefabName, playerHost.id, cameraPrefab.id);
            return state.nodeView(cameraPrefab.id);
        }

        // deepCopyWithNewIds() leaves blank ids — nodeView("") is null. Assign like the editor.
        try {
            SceneNodeIds.assignTree(state.document, fresh);
            parent.children.add(fresh);
        } finally {
            SceneNodeIds.clearPending();
        }
        if (parent.type == SceneNodeType.ROUTE && SceneRouteLayout.isLayoutChild(fresh)) {
            parentWorld = state.document.parentWorldTransform(fresh);
        }

        List<UUID> spawned = ACTIVE.computeIfAbsent(sceneId, k -> new ArrayList<>());
        List<String> trackedKeys = ACTIVE_TRACKED_KEYS.computeIfAbsent(sceneId, k -> new ArrayList<>());
        List<String> scriptInstances = ACTIVE_SCRIPT_INSTANCES.computeIfAbsent(sceneId, k -> new ArrayList<>());
        Map<String, Object> runtimeObjects = new HashMap<>();
        spawnSubtree(level, sceneId, state, fresh, parentWorld, parent == state.document.root ? null : parent,
                spawned, trackedKeys, runtimeObjects, scriptInstances, source);
        for (var entry : runtimeObjects.entrySet()) {
            state.putRuntimeObject(entry.getKey(), entry.getValue());
        }
        LOGGER.info("[Spraute Prefab] Spawned prefab '{}' into scene '{}' as '{}' at {}/{}/{}",
                prefabName, sceneId, fresh.id, x, y, z);
        return state.nodeView(fresh.id);
    }

    private static SceneNode playerHostOf(SceneDocument document, SceneNode node) {
        SceneNode cur = node;
        while (cur != null) {
            if (cur.type == SceneNodeType.PLAYER) return cur;
            cur = document.findParent(cur.id);
        }
        return null;
    }

    private static void syncPlayerCameraAfterInstall(SceneNode player, SceneNode camera) {
        if (player == null || camera == null || player.playerUuid == null || player.playerUuid.isBlank()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            ServerPlayer sp = server.getPlayerList().getPlayer(java.util.UUID.fromString(player.playerUuid));
            if (sp != null) PlayerCameraSync.send(sp, camera);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Recursive runtime spawn of a node subtree (prefab instantiation). Mirrors load().
     * Package-visible (not private) so {@link LocalWorldInstancing} can reuse the exact same
     * per-node-type dispatch to spawn a player's private Local World clone.
     */
    static void spawnSubtree(ServerLevel level, String sceneId, SceneActiveState state,
                                     SceneNode node, SceneTransform parentWorld, SceneNode parent,
                                     List<UUID> spawned, List<String> trackedKeys,
                                     Map<String, Object> runtimeObjects, List<String> scriptInstances,
                                     CommandSourceStack source) {
        if (!node.active) return;
        SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
        switch (node.type) {
            case NPC -> spawnSceneNpc(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            case MODEL -> spawnSceneModel(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            case BILLBOARD -> spawnSceneBillboard(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
            case HITBOX -> state.registerHitbox(node);
            case PARTICLE -> state.registerParticle(node);
            case LIGHT -> state.registerLight(node);
            case AUDIO_SOURCE -> state.registerAudioSource(node);
            case POST_PROCESS -> state.registerPostProcess(node);
            case ANIMATOR -> {
                if (node.animatorFile != null && !node.animatorFile.isBlank() && parent != null) {
                    Object obj = runtimeObjects.get(parent.id);
                    if (obj == null) obj = state.getRuntimeObject(parent.id);
                    if (obj instanceof SprauteNpcEntity npc) {
                        npc.setAnimation(node.animatorFile);
                        if (node.animatorClip != null && !node.animatorClip.isBlank()) npc.setIdleAnim(node.animatorClip);
                    } else if (obj instanceof SprauteModelEntity model) {
                        model.setAnimation(node.animatorFile);
                        if (node.animatorClip != null && !node.animatorClip.isBlank()) model.setIdleAnim(node.animatorClip);
                    }
                }
            }
            case SCRIPT, VISUAL_SCRIPT -> {
                ScriptManager scripts = ScriptManager.getInstance();
                if (scripts != null && node.runOnSceneStart && node.script != null && !node.script.isBlank()) {
                    SceneNode owner = parent != null ? parent : state.document.root;
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("_scene", state.sceneView());
                    variables.put("_node", state.nodeView(owner));
                    variables.put("_scriptNode", state.nodeView(node));
                    String instanceId = "scene:" + sceneId + ":node:" + node.id;
                    if (scripts.runInstance(node.script.trim(), instanceId,
                            sceneScriptSource(source.getServer(), level), variables)) {
                        scriptInstances.add(instanceId);
                    } else {
                        LOGGER.warn("[Spraute Prefab] Failed to start script '{}' for node {}", node.script, node.id);
                    }
                }
            }
            case CANVAS -> spawnOrShowCanvas(sceneId, node, parent, source, true);
            default -> {}
        }
        SceneTransform childWorld = !node.hasTransform() ? parentWorld : world;
        for (SceneNode child : node.children) {
            SceneTransform childParent = node.type == SceneNodeType.ROUTE
                    && SceneRouteLayout.isLayoutChild(child)
                    ? state.document.parentWorldTransform(child)
                    : childWorld;
            spawnSubtree(level, sceneId, state, child, childParent, node,
                    spawned, trackedKeys, runtimeObjects, scriptInstances, source);
        }
    }

    /**
     * Registers a CANVAS as active and auto-shows it on screen. A world-space canvas
     * ({@link SceneNode#canvasWorldSpace}) is still registered — so scripts can find/update it —
     * but never auto-pushed to anyone's screen: {@link SceneWorldCanvasRuntime} draws it as a
     * panel in the world instead.
     */
    private static void spawnOrShowCanvas(String sceneId, SceneNode node, SceneNode parent,
                                          CommandSourceStack source, boolean filterToOwner) {
        ACTIVE_CANVASES.computeIfAbsent(sceneId, k -> new ArrayList<>()).add(node);
        if (node.canvasWorldSpace) return;
        if (node.canvasAutoShow && source.getServer() != null) {
            for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                // Local World clone: auto-show only to its owner, never every player.
                if (filterToOwner && node.ownerPlayerUuid != null
                        && !node.ownerPlayerUuid.equals(player.getUUID().toString())) {
                    continue;
                }
                showCanvasTo(sceneId, node, player);
            }
        }
    }

    /**
     * True if this CANVAS is drawn in the world instead of on screen — it is still registered as
     * an active canvas (so scripts can find and update it), but never auto-pushed to anyone's
     * screen: {@link SceneWorldCanvasRuntime} draws it as a panel in the world.
     */
    public static boolean isWorldCanvasContent(SceneNode canvas) {
        return SceneWorldCanvasRuntime.isWorldCanvas(canvas);
    }

    /**
     * Tags an entity spawned for a scene node exactly like NPC/MODEL/BILLBOARD entities: the scene
     * tag (swept on unload), the per-node tag, and — for Local World clones — the owner tag that
     * {@code Spraute_engine.onStartTracking} uses to hide it from every other player.
     */
    public static void tagSceneEntity(Entity entity, String sceneId, SceneNode node) {
        if (entity == null || node == null) return;
        entity.addTag(TAG_PREFIX + sceneId);
        entity.addTag(TAG_PREFIX + "node_" + node.id);
        if (node.ownerPlayerUuid != null) {
            entity.addTag(SceneNode.LOCAL_OWNER_TAG_PREFIX + node.ownerPlayerUuid);
        }
    }

    /** Sends the canvas node's GUI to one player (overlay or full screen). */
    public static boolean showCanvasTo(String sceneId, SceneNode canvas, net.minecraft.server.level.ServerPlayer player) {
        if (canvas == null || canvas.type != SceneNodeType.CANVAS || player == null) return false;
        if (!canvas.active) return false;
        // A world-space canvas is a panel in the world; it has no screen form to open.
        if (canvas.canvasWorldSpace) return false;
        try {
            SceneActiveState state = ACTIVE_STATES.get(sceneId);
            SceneNode sceneRoot = state != null && state.document != null ? state.document.root : null;
            String json = SceneCanvasBuilder.buildJson(canvas, sceneRoot);
            // Resolve npc:/player entity refs to UUIDs when live; keeps modelGeo for alwaysRender fallback.
            try {
                json = org.zonarstudio.spraute_engine.ui.SprauteUiJson.prepareAndSerialize(
                        player.createCommandSourceStack().getLevel(), player.createCommandSourceStack(), json);
            } catch (Exception ignored) {}
            if (canvas.canvasOverlay) {
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new org.zonarstudio.spraute_engine.network.OpenSprauteOverlayPacket(canvasOverlayId(sceneId, canvas), json));
            } else if (json.contains("\"type\":\"container\"")) {
                // Canvas with slots: must be opened as a real container menu,
                // otherwise the slots have no backing inventory.
                final String prepared = json;
                net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                    @Override
                    public net.minecraft.network.chat.Component getDisplayName() {
                        return net.minecraft.network.chat.Component.empty();
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                            int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player p2) {
                        return new org.zonarstudio.spraute_engine.ui.SprauteContainerMenu(id, inv, prepared);
                    }
                }, buf -> buf.writeUtf(prepared));
            } else {
                org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new org.zonarstudio.spraute_engine.network.OpenSprauteUiPacket(json));
                org.zonarstudio.spraute_engine.ui.UiTracker.markOpen(player.getUUID());
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Spraute Scene] Failed to show canvas '{}' of scene '{}': {}",
                    canvas.name, sceneId, e.getMessage());
            return false;
        }
    }

    /** Closes a canvas GUI for one player. */
    public static void closeCanvasFor(String sceneId, SceneNode canvas, net.minecraft.server.level.ServerPlayer player) {
        if (canvas == null || player == null) return;
        if (canvas.canvasOverlay) {
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket(canvasOverlayId(sceneId, canvas)));
        } else {
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new org.zonarstudio.spraute_engine.network.CloseSprauteUiPacket());
        }
    }

    /** Finds a CANVAS node by name (or id) inside an active scene. */
    public static SceneNode findCanvas(String sceneId, String nameOrId) {
        List<SceneNode> canvases = ACTIVE_CANVASES.get(sceneId);
        if (canvases == null || nameOrId == null) return null;
        for (SceneNode canvas : canvases) {
            if (nameOrId.equals(canvas.name) || nameOrId.equals(canvas.id)) return canvas;
        }
        return null;
    }

    /** Called on player login: re-show auto canvases of all active scenes. */
    public static void showAutoCanvasesTo(net.minecraft.server.level.ServerPlayer player) {
        for (var entry : ACTIVE_CANVASES.entrySet()) {
            for (SceneNode canvas : entry.getValue()) {
                if (!canvas.canvasAutoShow || canvas.canvasWorldSpace) continue;
                // Local World clone: only re-show it to its own owner on late join.
                if (canvas.ownerPlayerUuid != null
                        && !canvas.ownerPlayerUuid.equals(player.getUUID().toString())) {
                    continue;
                }
                showCanvasTo(entry.getKey(), canvas, player);
            }
        }
    }

    /** Reference to a canvas node inside an active scene. */
    public record CanvasRef(String sceneId, SceneNode canvas) {}

    /**
     * Resolves a script argument to a canvas of an active scene:
     * a {@link SceneNodeView} of a CANVAS node, or a string name/id
     * (optionally prefixed with the scene id: {@code "sceneId:canvas"}).
     * Returns {@code null} if nothing matches.
     */
    public static CanvasRef resolveCanvas(Object arg) {
        if (arg instanceof SceneNodeView view) {
            SceneActiveState state = getActiveState(view.getSceneId());
            if (state == null) return null;
            SceneNode node = state.document.findNode(view.getNodeId());
            return node != null && node.type == SceneNodeType.CANVAS
                    ? new CanvasRef(view.getSceneId(), node) : null;
        }
        if (arg instanceof String s) {
            String name = s.trim();
            int sep = name.indexOf(':');
            if (sep > 0) {
                SceneNode canvas = findCanvas(name.substring(0, sep), name.substring(sep + 1));
                return canvas != null ? new CanvasRef(name.substring(0, sep), canvas) : null;
            }
            for (String sceneId : ACTIVE_CANVASES.keySet()) {
                SceneNode canvas = findCanvas(sceneId, name);
                if (canvas != null) return new CanvasRef(sceneId, canvas);
            }
        }
        return null;
    }

    private static String canvasOverlayId(String sceneId, SceneNode canvas) {
        return "scene:" + sceneId + ":" + canvas.id;
    }

    private static void spawnSceneNpc(ServerLevel level, String sceneId, SceneNode node, SceneTransform world,
                                      Map<String, Object> runtimeObjects, List<UUID> spawned, List<String> trackedKeys) {
        SprauteNpcEntity npc = ModEntities.SPRAUTE_NPC.get().create(level);
        if (npc == null) {
            LOGGER.warn("[Spraute Scene] Failed to create NPC entity for node '{}' in scene '{}'", node.name, sceneId);
            return;
        }
        NpcNodeBridge.applyNodeToNpc(node, npc);
        npc.setHealth(Math.max(1f, node.maxHealth));
        // Scene transform.ry is entity yaw; model Y overlay stays 0 (same as script NPC).
        npc.setModelRotation((float) world.rx, 0f, (float) world.rz);
        npc.setModelScale((float) world.sx, (float) world.sy, (float) world.sz);
        float yaw = (float) world.ry;
        float pitch = (float) world.rx;
        npc.moveTo(world.px, world.py, world.pz, yaw, pitch);
        npc.setYRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setXRot(pitch);
        npc.addTag(TAG_PREFIX + sceneId);
        npc.addTag(TAG_PREFIX + "node_" + node.id);
        if (node.ownerPlayerUuid != null) {
            // Local World clone: hidden from every player but its owner (PlayerEvent.StartTracking).
            npc.addTag(SceneNode.LOCAL_OWNER_TAG_PREFIX + node.ownerPlayerUuid);
        }
        level.addFreshEntity(npc);
        trackNpc(node, npc.getUUID(), trackedKeys);
        runtimeObjects.put(node.id, npc);
        spawned.add(npc.getUUID());
        SceneActiveState live = getActiveState(sceneId);
        NpcLocomotionSnapshot snap = live != null ? live.npcLocomotion(node.id) : null;
        if (snap != null && !snap.isIdle()) npc.restoreLocomotion(snap);
    }

    private static void spawnSceneModel(ServerLevel level, String sceneId, SceneNode node, SceneTransform world,
                                        Map<String, Object> runtimeObjects, List<UUID> spawned, List<String> trackedKeys) {
        SprauteModelEntity model = ModEntities.SPRAUTE_MODEL.get().create(level);
        if (model == null) {
            LOGGER.warn("[Spraute Scene] Failed to create MODEL entity for node '{}' in scene '{}'", node.name, sceneId);
            return;
        }
        model.setCustomName(Component.literal(node.name));
        model.setCustomNameVisible(node.nameVisible);
        if (node.model != null && !node.model.isEmpty()) model.setModel(node.model);
        if (node.texture != null && !node.texture.isEmpty()) model.setTexture(node.texture);
        if (node.animation != null && !node.animation.isEmpty()) model.setAnimation(node.animation);
        if (node.idleAnimation != null && !node.idleAnimation.isEmpty()) model.setIdleAnim(node.idleAnimation);
        model.setSilent(node.silent);
        model.setGlowingTag(node.glowing);
        model.setTranslucent(node.translucent);
        model.setOpacity(node.opacity);
        model.setLodEnabled(node.lodEnabled);
        model.setRenderDistance(node.renderDistance);
        model.setForceVisible(node.forceVisible);
        model.setNoGravity(node.noGravity);
        model.setInvulnerable(node.invulnerable);
        // Avoid double yaw: entity YRot carries scene yaw; modelRotationY is overlay only.
        model.setModelRotation((float) world.rx, 0f, (float) world.rz);
        model.setModelScale((float) world.sx, (float) world.sy, (float) world.sz);
        float yaw = (float) world.ry;
        float pitch = (float) world.rx;
        model.moveTo(world.px, world.py, world.pz, yaw, pitch);
        model.setYRot(yaw);
        model.setXRot(pitch);
        model.addTag(TAG_PREFIX + sceneId);
        model.addTag(TAG_PREFIX + "node_" + node.id);
        if (node.ownerPlayerUuid != null) {
            model.addTag(SceneNode.LOCAL_OWNER_TAG_PREFIX + node.ownerPlayerUuid);
        }
        level.addFreshEntity(model);
        trackModel(node, model.getUUID(), trackedKeys);
        runtimeObjects.put(node.id, model);
        spawned.add(model.getUUID());
    }

    private static void spawnSceneBillboard(ServerLevel level, String sceneId, SceneNode node, SceneTransform world,
                                            Map<String, Object> runtimeObjects, List<UUID> spawned, List<String> trackedKeys) {
        org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity billboard =
                new org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity(
                        level, world.px, world.py, world.pz,
                        resolveBillboardTexture(node.texture),
                        Math.max(0.01f, node.billboardWidth * (float) Math.abs(world.sx)),
                        Math.max(0.01f, node.billboardHeight * (float) Math.abs(world.sy)),
                        node.billboardSeeThrough);
        billboard.addTag(TAG_PREFIX + sceneId);
        billboard.addTag(TAG_PREFIX + "node_" + node.id);
        if (node.ownerPlayerUuid != null) {
            billboard.addTag(SceneNode.LOCAL_OWNER_TAG_PREFIX + node.ownerPlayerUuid);
        }
        level.addFreshEntity(billboard);
        trackBillboard(node, billboard.getUUID(), trackedKeys);
        runtimeObjects.put(node.id, billboard);
        spawned.add(billboard.getUUID());
    }

    private static void trackBillboard(SceneNode node, UUID uuid, List<String> trackedKeys) {
        BillboardManager.track(node.id, uuid);
        trackedKeys.add(node.id);
        if (node.name != null && !node.name.isBlank()) {
            String nameKey = node.name.trim();
            UUID existing = BillboardManager.get(nameKey);
            if (existing == null || existing.equals(uuid)) {
                BillboardManager.track(nameKey, uuid);
                trackedKeys.add(nameKey);
            }
        }
    }

    /** Editor stores workspace texture paths; billboard entity expects a resource location string. */
    public static String resolveBillboardTexture(String texture) {
        if (texture == null || texture.isBlank()) return "";
        var parsed = org.zonarstudio.spraute_engine.util.SprauteResourcePath.parse(
                texture, org.zonarstudio.spraute_engine.util.SprauteResourcePath.Kind.TEXTURE);
        return parsed.ok() ? parsed.location().toString() : texture;
    }

    private static void trackNpc(SceneNode node, UUID uuid, List<String> trackedKeys) {
        // Node id is the stable key. Also alias display name when free so scripts can use
        // "Kate" the same way as create npc kate — without stealing reserved keywords / other NPCs.
        NpcManager.track(node.id, uuid);
        trackedKeys.add(node.id);
        if (node.name != null && !node.name.isBlank()) {
            String nameKey = node.name.trim();
            if (!isReservedNpcAlias(nameKey)) {
                UUID existing = NpcManager.get(nameKey);
                if (existing == null || existing.equals(uuid)) {
                    NpcManager.track(nameKey, uuid);
                    trackedKeys.add(nameKey);
                }
            }
        }
    }

    private static boolean isReservedNpcAlias(String name) {
        if (name == null) return true;
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "player", "npc", "mob", "any" -> true;
            default -> false;
        };
    }

    /**
     * Resolve a live NPC/MODEL entity from an active scene by node id or display name.
     * Used so script targets treat hierarchy NPCs like {@code create npc} ids.
     */
    public static Entity findLiveEntityByRef(String ref, ServerLevel level) {
        if (ref == null || ref.isBlank() || level == null) return null;
        String key = ref.trim();
        for (SceneActiveState state : ACTIVE_STATES.values()) {
            SceneNode node = state.document.findNode(key);
            if (node == null) node = ScenePathResolver.findByName(state.document.root, key);
            if (node == null) continue;
            Entity entity = state.resolveEntity(node.id, level);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void trackModel(SceneNode node, UUID uuid, List<String> trackedKeys) {
        ModelManager.track(node.id, uuid);
        trackedKeys.add(node.id);
        if (node.name != null && !node.name.isBlank()) {
            String nameKey = node.name.trim();
            if (!isReservedNpcAlias(nameKey)) {
                UUID existing = ModelManager.get(nameKey);
                if (existing == null || existing.equals(uuid)) {
                    ModelManager.track(nameKey, uuid);
                    trackedKeys.add(nameKey);
                }
            }
        }
    }

    /** Tag entity for unload and track manager keys for a dynamically spawned scene node. */
    public static void tagAndTrack(String sceneId, SceneNode node, Entity entity) {
        if (sceneId == null || node == null || entity == null) return;
        entity.addTag(TAG_PREFIX + sceneId);
        entity.addTag(TAG_PREFIX + "node_" + node.id);
        ACTIVE.computeIfAbsent(sceneId, ignored -> new ArrayList<>()).add(entity.getUUID());
        List<String> trackedKeys = ACTIVE_TRACKED_KEYS.computeIfAbsent(sceneId, ignored -> new ArrayList<>());
        if (node.type == SceneNodeType.NPC) {
            trackNpc(node, entity.getUUID(), trackedKeys);
        } else if (node.type == SceneNodeType.MODEL) {
            trackModel(node, entity.getUUID(), trackedKeys);
        } else if (node.type == SceneNodeType.BILLBOARD && entity instanceof SprauteBillboardEntity) {
            trackBillboard(node, entity.getUUID(), trackedKeys);
        }
    }

    /** Track script/manager keys for non-entity nodes (EMPTY / HITBOX). */
    public static void trackNodeKeys(String sceneId, SceneNode node) {
        if (sceneId == null || node == null) return;
        List<String> trackedKeys = ACTIVE_TRACKED_KEYS.computeIfAbsent(sceneId, ignored -> new ArrayList<>());
        trackedKeys.add(node.id);
        if (node.name != null && !node.name.isBlank()) trackedKeys.add(node.name);
    }

    public static boolean unload(String sceneId, CommandSourceStack source) {
        SceneActiveState removedState = ACTIVE_STATES.remove(sceneId);
        // Per-player Local World script instances live outside ACTIVE_SCRIPT_INSTANCES
        // (they're keyed per player, not per scene) — stop them explicitly.
        LocalWorldInstancing.stopScriptsForScene(sceneId);
        if (removedState != null) {
            ServerLevel clearLevel = resolveLevel(source, removedState.runtimeDimension);
            clearForcedChunks(sceneId, clearLevel);
            removedState.clearHitboxes();
            removedState.document.walk((node, parentWorld) -> {
                if (node.type == SceneNodeType.PARTICLE && node.particles(false) != null) {
                    SceneParticleEmitter.clearCustomServer(node.id);
                }
            });
            removedState.clearParticles();
            removedState.clearLights(clearLevel);
            removedState.clearAudioSources(clearLevel);
            removedState.clearPostProcesses(clearLevel);
        }
        List<SceneNode> canvases = ACTIVE_CANVASES.remove(sceneId);
        if (canvases != null && !canvases.isEmpty()) {
            for (net.minecraft.server.level.ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                for (SceneNode canvas : canvases) {
                    closeCanvasFor(sceneId, canvas, player);
                }
            }
        }
        boolean wasActive = ACTIVE.remove(sceneId) != null;
        List<String> scriptInstances = ACTIVE_SCRIPT_INSTANCES.remove(sceneId);
        List<String> trackedKeys = ACTIVE_TRACKED_KEYS.remove(sceneId);

        ScriptManager scripts = ScriptManager.getInstance();
        if (scripts != null && scriptInstances != null) {
            for (String instanceId : scriptInstances) {
                scripts.stopInstance(instanceId);
            }
        }
        // Scripts may have finished earlier but left the cinematic camera up — always return it on scene stop.
        if (scriptInstances != null && !scriptInstances.isEmpty() && source.getServer() != null) {
            org.zonarstudio.spraute_engine.script.CameraScriptUtil.resetOwnedBy(source.getServer(), scriptInstances);
        }

        // Player CAMERA rest pose is scene-owned: drop it when this scene stops,
        // or fall back to another still-loaded scene for the same player.
        if (removedState != null && source.getServer() != null) {
            if (ACTIVE_STATES.isEmpty()) {
                PlayerCameraSync.clearAll(source.getServer());
            } else {
                PlayerCameraSync.clearAfterUnload(removedState, source.getServer());
            }
        }

        if (trackedKeys != null) {
            for (String key : trackedKeys) {
                NpcManager.remove(key);
                ModelManager.remove(key);
                BillboardManager.remove(key);
            }
        }

        MinecraftServer server = source.getServer();
        int removed = 0;
        String tag = TAG_PREFIX + sceneId;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(tag)) toRemove.add(entity);
            }
            for (Entity entity : toRemove) {
                NpcManager.removeUuid(entity.getUUID());
                ModelManager.removeUuid(entity.getUUID());
                BillboardManager.removeByUuid(entity.getUUID());
                entity.discard();
                removed++;
            }
        }
        LOGGER.info("[Spraute Scene] Unloaded scene '{}' ({} entities removed, {} script(s) stopped)",
                sceneId, removed, scriptInstances == null ? 0 : scriptInstances.size());
        broadcastActiveScenes(source.getServer());
        return wasActive || (scriptInstances != null && !scriptInstances.isEmpty()) || removed > 0;
    }

    /**
     * Called each server tick. Physical entity movement is captured into the
     * live hierarchy before child HITBOX transforms and collisions are evaluated.
     */
    public static void tickActiveHitboxes(MinecraftServer server) {
        if (server == null || ACTIVE_STATES.isEmpty()) return;
        for (SceneActiveState state : ACTIVE_STATES.values()) {
            ScenePlayerNodes.syncServer(state, server);
            ServerLevel level = resolveLevelForDocument(server, state.runtimeDimension);
            if (level != null) {
                reconcileSceneEntities(state, level);
                refreshForcedChunks(state, level);
                SceneLiveSync.captureRuntimeEntityTransforms(state, level);
                ScenePropertyAnimator.tick(state, level);
                state.tickHitboxes(level);
                state.tickParticles(level);
                state.tickLights(level);
                state.tickAudioSources(level);
                state.tickPostProcesses(level);
            }
        }
    }

    /**
     * Recover scene entities after chunk unload / dimension change.
     * Does <b>not</b> respawn NPCs that were killed or script-{@code remove()}'d —
     * those nodes are marked inactive so they stay gone.
     */
    private static void reconcileSceneEntities(SceneActiveState state, ServerLevel level) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        List<String> needSpawn = new ArrayList<>();
        for (var entry : new ArrayList<>(state.runtimeObjectMap().entrySet())) {
            String nodeId = entry.getKey();
            Object obj = entry.getValue();
            if (!(obj instanceof Entity entity)) continue;
            if (!entity.isRemoved()) {
                Entity live = level.getEntity(entity.getUUID());
                if (live != null && live != entity) {
                    state.putRuntimeObject(nodeId, live);
                }
                continue;
            }
            Entity restored = level.getEntity(entity.getUUID());
            if (restored != null && !restored.isRemoved()) {
                state.putRuntimeObject(nodeId, restored);
                continue;
            }
            state.removeRuntimeObject(nodeId);
            List<UUID> uuids = ACTIVE.get(state.sceneId);
            if (uuids != null) uuids.remove(entity.getUUID());
            NpcManager.removeUuid(entity.getUUID());
            ModelManager.removeUuid(entity.getUUID());
            BillboardManager.removeByUuid(entity.getUUID());

            Entity.RemovalReason reason = entity.getRemovalReason();
            // Killed by player/script or discarded via remove() — stay gone.
            if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                SceneNode node = state.document.findNode(nodeId);
                if (node != null) node.active = false;
                continue;
            }
            needSpawn.add(nodeId);
        }
        // Also respawn active nodes that should have a world entity but lost their handle
        // (chunk unload edge cases) — never inactive / intentionally cleared nodes.
        state.document.walkActive((node, ignored) -> {
            if (!needsWorldEntity(node.type)) return;
            Object obj = state.getRuntimeObject(node.id);
            if (obj instanceof Entity e && !e.isRemoved()) return;
            if (obj == null || obj instanceof Entity) {
                if (!needSpawn.contains(node.id)) needSpawn.add(node.id);
            }
        });
        for (String nodeId : needSpawn) {
            ensureLiveSpawned(state.sceneId, nodeId, level, source);
        }
    }

    private static boolean needsWorldEntity(SceneNodeType type) {
        return type == SceneNodeType.NPC
                || type == SceneNodeType.MODEL
                || type == SceneNodeType.BILLBOARD;
    }

    /** Keep only explicitly opted-in NPC chunks loaded. */
    private static void refreshForcedChunks(SceneActiveState state, ServerLevel level) {
        Set<ChunkPos> next = new HashSet<>();
        for (var entry : state.runtimeObjectMap().entrySet()) {
            SceneNode owner = state.document.findNode(entry.getKey());
            if (owner != null && owner.type == SceneNodeType.PLAYER) continue;
            Object obj = entry.getValue();
            if (owner != null && owner.type == SceneNodeType.NPC && owner.loadChunks
                    && obj instanceof SprauteNpcEntity npc && !npc.isRemoved()
                    && org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(npc) == level) {
                next.add(new ChunkPos(npc.blockPosition()));
                npc.collectKeepLoadedChunks(next);
            }
            NpcLocomotionSnapshot snap = state.npcLocomotion(entry.getKey());
            if (owner != null && owner.type == SceneNodeType.NPC && owner.loadChunks && snap != null) {
                snap.addChunks(next);
            }
        }
        state.document.walkActive((node, parentWorld) -> {
            if (node.type != SceneNodeType.NPC || !node.loadChunks) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            //? if >=1.20.1 {
            next.add(new ChunkPos(BlockPos.containing(world.px, world.py, world.pz)));
            //?} else {
            /*next.add(new ChunkPos(new BlockPos(world.px, world.py, world.pz)));
            *///?}
        });

        Set<ChunkPos> prev = FORCED_CHUNKS.getOrDefault(state.sceneId, Set.of());
        ChunkForceTickets.replace(level, prev, next);
        FORCED_CHUNKS.put(state.sceneId, next);
    }

    private static void clearForcedChunks(String sceneId, ServerLevel level) {
        Set<ChunkPos> prev = FORCED_CHUNKS.remove(sceneId);
        if (prev == null || level == null) return;
        ChunkForceTickets.releaseAll(level, prev);
    }

    /** Resolves the server level for a scene document dimension (live sync / spawn). */
    public static ServerLevel resolveLevelForSync(net.minecraft.server.MinecraftServer server, String dimension) {
        return resolveLevelForDocument(server, dimension);
    }

    private static ServerLevel resolveLevelForDocument(MinecraftServer server, String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return server.overworld();
        }
        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return server.overworld();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(loc)) return level;
        }
        return server.overworld();
    }

    private static ServerLevel resolveLevel(CommandSourceStack source, String dimension) {
        if (dimension == null || dimension.isEmpty()) return source.getLevel();
        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return source.getLevel();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (level.dimension().location().equals(loc)) return level;
        }
        return source.getLevel();
    }

    /**
     * Spawns world/runtime objects for a document subtree that was added while the
     * scene is already running (editor hierarchy, prefab drop, etc.).
     */
    public static void ensureLiveSpawned(String sceneId, String nodeId, ServerLevel level,
                                         CommandSourceStack source) {
        SceneActiveState state = getActiveState(sceneId);
        if (state == null || nodeId == null || level == null) return;
        SceneNode node = state.document.findNode(nodeId);
        if (node == null) return;
        SceneNode parent = state.document.findParent(nodeId);
        SceneTransform parentWorld = state.document.parentWorldTransform(node);

        List<UUID> spawned = ACTIVE.computeIfAbsent(sceneId, k -> new ArrayList<>());
        List<String> trackedKeys = ACTIVE_TRACKED_KEYS.computeIfAbsent(sceneId, k -> new ArrayList<>());
        List<String> scriptInstances = ACTIVE_SCRIPT_INSTANCES.computeIfAbsent(sceneId, k -> new ArrayList<>());
        Map<String, Object> runtimeObjects = new HashMap<>();
        CommandSourceStack stack = source != null ? source : level.getServer().createCommandSourceStack();
        spawnSubtreeIfMissing(level, sceneId, state, node, parentWorld,
                parent == null || parent == state.document.root ? null : parent,
                spawned, trackedKeys, runtimeObjects, scriptInstances, stack, node.id);
        for (var entry : runtimeObjects.entrySet()) {
            state.putRuntimeObject(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Despawns runtime objects and removes a subtree from the live document
     * (editor delete while scene is running).
     */
    public static boolean removeLiveSubtree(String sceneId, String nodeId) {
        SceneActiveState state = getActiveState(sceneId);
        if (state == null || nodeId == null) return false;
        SceneNode node = state.document.findNode(nodeId);
        if (node == null || node.type == SceneNodeType.SCENE) return false;

        List<SceneNode> subtree = new ArrayList<>();
        collectSubtreeNodes(node, subtree);
        List<UUID> uuids = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        String overlayOwner = null;
        if (node.type == SceneNodeType.SKIN_OVERLAY) {
            SceneNode parent = state.document.findParent(nodeId);
            if (parent != null && parent.type == SceneNodeType.PLAYER) overlayOwner = parent.id;
        }
        for (SceneNode n : subtree) {
            Object obj = state.getRuntimeObject(n.id);
            if (obj instanceof Entity entity) uuids.add(entity.getUUID());
            keys.add(n.id);
            if (n.name != null && !n.name.isBlank()) keys.add(n.name);
        }

        if (!state.removeNodeSubtree(nodeId)) return false;

        if (overlayOwner != null) {
            SceneNode owner = state.document.findNode(overlayOwner);
            if (owner != null && owner.type == SceneNodeType.PLAYER) {
                PlayerSkinOps.applyFromPlayerNode(owner);
            }
        }

        List<UUID> active = ACTIVE.get(sceneId);
        if (active != null) {
            for (UUID uuid : uuids) active.remove(uuid);
        }
        List<String> tracked = ACTIVE_TRACKED_KEYS.get(sceneId);
        for (String key : keys) {
            NpcManager.remove(key);
            ModelManager.remove(key);
            BillboardManager.remove(key);
            if (tracked != null) tracked.remove(key);
        }
        for (UUID uuid : uuids) {
            NpcManager.removeUuid(uuid);
            ModelManager.removeUuid(uuid);
            BillboardManager.removeByUuid(uuid);
        }
        return true;
    }

    private static void collectSubtreeNodes(SceneNode node, List<SceneNode> out) {
        out.add(node);
        for (SceneNode child : node.children) collectSubtreeNodes(child, out);
    }

    private static void spawnSubtreeIfMissing(ServerLevel level, String sceneId, SceneActiveState state,
                                              SceneNode node, SceneTransform parentWorld, SceneNode parent,
                                              List<UUID> spawned, List<String> trackedKeys,
                                              Map<String, Object> runtimeObjects, List<String> scriptInstances,
                                              CommandSourceStack source, String liveRootId) {
        if (!node.active) return;
        SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
        boolean missing = node.type == SceneNodeType.CANVAS
                ? ACTIVE_CANVASES.getOrDefault(sceneId, List.of()).stream()
                        .noneMatch(canvas -> node.id.equals(canvas.id))
                : state.getRuntimeObject(node.id) == null;
        if (missing) {
            switch (node.type) {
                case NPC -> spawnSceneNpc(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
                case MODEL -> spawnSceneModel(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
                case BILLBOARD -> spawnSceneBillboard(level, sceneId, node, world, runtimeObjects, spawned, trackedKeys);
                case HITBOX -> state.registerHitbox(node);
                case PARTICLE -> state.registerParticle(node);
                case LIGHT -> state.registerLight(node);
                case AUDIO_SOURCE -> state.registerAudioSource(node);
                case POST_PROCESS -> state.registerPostProcess(node);
                case ANIMATOR -> {
                    if (node.animatorFile != null && !node.animatorFile.isBlank() && parent != null) {
                        Object obj = runtimeObjects.get(parent.id);
                        if (obj == null) obj = state.getRuntimeObject(parent.id);
                        if (obj instanceof SprauteNpcEntity npc) {
                            npc.setAnimation(node.animatorFile);
                            if (node.animatorClip != null && !node.animatorClip.isBlank()) {
                                npc.setIdleAnim(node.animatorClip);
                            }
                        } else if (obj instanceof SprauteModelEntity model) {
                            model.setAnimation(node.animatorFile);
                            if (node.animatorClip != null && !node.animatorClip.isBlank()) {
                                model.setIdleAnim(node.animatorClip);
                            }
                        }
                    }
                }
                case SCRIPT, VISUAL_SCRIPT -> {
                    ScriptManager scripts = ScriptManager.getInstance();
                    if (scripts != null && node.runOnSceneStart && node.script != null && !node.script.isBlank()) {
                        SceneNode owner = parent != null ? parent : state.document.root;
                        Map<String, Object> variables = new HashMap<>();
                        variables.put("_scene", state.sceneView());
                        variables.put("_node", state.nodeView(owner));
                        variables.put("_scriptNode", state.nodeView(node));
                        String instanceId = "scene:" + sceneId + ":node:" + node.id;
                        boolean running = scripts.isInstanceRunning(instanceId);
                        boolean reload = liveRootId != null && liveRootId.equals(node.id);
                        if (running && !reload) {
                            if (!scriptInstances.contains(instanceId)) scriptInstances.add(instanceId);
                        } else {
                            if (running) scripts.stopInstance(instanceId);
                            if (scripts.runInstance(node.script.trim(), instanceId,
                                    sceneScriptSource(source.getServer(), level), variables)) {
                                if (!scriptInstances.contains(instanceId)) scriptInstances.add(instanceId);
                            } else {
                                LOGGER.warn("[Spraute Scene] Failed to start live script '{}' for node {}",
                                        node.script, node.id);
                            }
                        }
                    }
                }
                case CANVAS -> spawnOrShowCanvas(sceneId, node, parent, source, false);
                default -> {}
            }
        }
        SceneTransform childWorld = !node.hasTransform() ? parentWorld : world;
        for (SceneNode child : node.children) {
            SceneTransform childParent = node.type == SceneNodeType.ROUTE
                    && SceneRouteLayout.isLayoutChild(child)
                    ? state.document.parentWorldTransform(child)
                    : childWorld;
            spawnSubtreeIfMissing(level, sceneId, state, child, childParent, node,
                    spawned, trackedKeys, runtimeObjects, scriptInstances, source, liveRootId);
        }
    }
}
