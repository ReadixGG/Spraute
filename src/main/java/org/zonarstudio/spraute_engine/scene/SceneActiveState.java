package org.zonarstudio.spraute_engine.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.NpcLocomotionSnapshot;
import org.zonarstudio.spraute_engine.entity.SprauteHitboxEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Live server-side state of one loaded scene (document + spawned runtime objects). */
public final class SceneActiveState {
    public final String sceneId;
    public final SceneDocument document;
    /**
     * Dimension where this scene instance was loaded. Scenes are not bound in the document —
     * they follow the world of {@code /spraute run} / auto-start.
     */
    public String runtimeDimension = "";
    /** Scene-local clock used by ANIMATOR property tracks (20 ticks per second). */
    long animationTicks;
    final Map<String, Object> animatedValues = new ConcurrentHashMap<>();
    private final Map<String, Object> runtimeObjects = new ConcurrentHashMap<>();
    private final Map<String, SceneNodeView> viewCache = new ConcurrentHashMap<>();
    private final Map<String, SceneHitboxRuntime> hitboxes = new ConcurrentHashMap<>();
    private final Map<String, SprauteHitboxEntity> hitboxColliders = new ConcurrentHashMap<>();
    private final Map<String, SceneParticleRuntime> particles = new ConcurrentHashMap<>();
    private final Map<String, SceneLightRuntime> lights = new ConcurrentHashMap<>();
    private final Map<String, SceneAudioRuntime> audioSources = new ConcurrentHashMap<>();
    private final Map<String, ScenePostProcessRuntime> postProcesses = new ConcurrentHashMap<>();
    private final Map<String, NpcLocomotionSnapshot> npcLocomotion = new ConcurrentHashMap<>();

    SceneActiveState(String sceneId, SceneDocument document) {
        this.sceneId = sceneId;
        this.document = document;
        if (document != null) document.liveRuntime = true;
    }

    public void putRuntimeObject(String nodeId, Object object) {
        if (nodeId != null && object != null) {
            runtimeObjects.put(nodeId, object);
        }
    }

    public Object getRuntimeObject(String nodeId) {
        return runtimeObjects.get(nodeId);
    }

    public Object removeRuntimeObject(String nodeId) {
        return nodeId == null ? null : runtimeObjects.remove(nodeId);
    }

    /** Live entity/object map for chunk keep + reconcile. */
    public Map<String, Object> runtimeObjectMap() {
        return runtimeObjects;
    }

    public void putNpcLocomotion(String nodeId, NpcLocomotionSnapshot snapshot) {
        if (nodeId == null) return;
        if (snapshot == null || snapshot.isIdle()) npcLocomotion.remove(nodeId);
        else npcLocomotion.put(nodeId, snapshot);
    }

    public NpcLocomotionSnapshot npcLocomotion(String nodeId) {
        return nodeId == null ? null : npcLocomotion.get(nodeId);
    }

    public void clearNpcLocomotion(String nodeId) {
        if (nodeId != null) npcLocomotion.remove(nodeId);
    }

    /** Finds the scene node id whose spawned entity matches {@code entity}. */
    public String findNodeIdByEntity(Entity entity) {
        if (entity == null) return null;
        for (var entry : runtimeObjects.entrySet()) {
            Object obj = entry.getValue();
            if (obj == entity) return entry.getKey();
            if (obj instanceof Entity e && e.getUUID().equals(entity.getUUID())) return entry.getKey();
        }
        return null;
    }

    public SceneNodeView nodeView(String pathOrId) {
        if (pathOrId == null || pathOrId.isBlank()) return null;
        String key = pathOrId.trim();
        SceneNode node = ScenePathResolver.findNode(document, key);
        if (node == null) return null;
        return viewCache.computeIfAbsent(node.id, id -> new SceneNodeView(this, node.id));
    }

    public SceneNodeView nodeView(SceneNode node) {
        if (node == null) return null;
        return viewCache.computeIfAbsent(node.id, id -> new SceneNodeView(this, node.id));
    }

    public SceneView sceneView() {
        return new SceneView(this);
    }

    public List<SceneNodeView> listNodeViews() {
        List<SceneNodeView> out = new ArrayList<>();
        document.walk((node, ignored) -> out.add(nodeView(node)));
        return Collections.unmodifiableList(out);
    }

    public SceneHitboxRuntime hitboxRuntime(String nodeId) {
        return nodeId == null ? null : hitboxes.get(nodeId);
    }

    /** Live hitbox map (for collision tick). */
    public Map<String, SceneHitboxRuntime> hitboxMap() {
        return hitboxes;
    }

    public void registerHitbox(SceneNode node) {
        if (node == null || node.type != SceneNodeType.HITBOX) return;
        hitboxes.put(node.id, new SceneHitboxRuntime(node));
        runtimeObjects.put(node.id, hitboxes.get(node.id));
    }

    public void registerParticle(SceneNode node) {
        if (node == null || node.type != SceneNodeType.PARTICLE) return;
        SceneParticleRuntime runtime = new SceneParticleRuntime(node);
        particles.put(node.id, runtime);
        runtimeObjects.put(node.id, runtime);
    }

    public void registerLight(SceneNode node) {
        if (node == null || node.type != SceneNodeType.LIGHT) return;
        SceneLightRuntime runtime = new SceneLightRuntime(node);
        lights.put(node.id, runtime);
        runtimeObjects.put(node.id, runtime);
    }

    public void registerAudioSource(SceneNode node) {
        if (node == null || node.type != SceneNodeType.AUDIO_SOURCE) return;
        SceneAudioRuntime runtime = new SceneAudioRuntime(node);
        audioSources.put(node.id, runtime);
        runtimeObjects.put(node.id, runtime);
    }

    public void registerPostProcess(SceneNode node) {
        if (node == null || node.type != SceneNodeType.POST_PROCESS) return;
        ScenePostProcessRuntime runtime = new ScenePostProcessRuntime(sceneId, node);
        postProcesses.put(node.id, runtime);
        runtimeObjects.put(node.id, runtime);
    }

    public SceneLightRuntime lightRuntime(String nodeId) {
        return nodeId == null ? null : lights.get(nodeId);
    }

    public SceneAudioRuntime audioRuntime(String nodeId) {
        return nodeId == null ? null : audioSources.get(nodeId);
    }

    public ScenePostProcessRuntime postProcessRuntime(String nodeId) {
        return nodeId == null ? null : postProcesses.get(nodeId);
    }

    /** True if any registered light runtime currently claims {@code pos}. */
    boolean isLightPosClaimed(BlockPos pos) {
        if (pos == null) return false;
        for (SceneLightRuntime light : lights.values()) {
            if (light != null && pos.equals(light.placedPos())) return true;
        }
        return false;
    }

    public SceneParticleRuntime particleRuntime(String nodeId) {
        return nodeId == null ? null : particles.get(nodeId);
    }

    void clearHitboxes() {
        for (SprauteHitboxEntity collider : hitboxColliders.values()) {
            if (collider != null && collider.isAlive()) collider.discard();
        }
        hitboxColliders.clear();
        hitboxes.clear();
    }

    void clearParticles() {
        particles.clear();
    }

    void clearLights(ServerLevel level) {
        // Clear claims first so shared cells are freed exactly once.
        List<SceneLightRuntime> all = new ArrayList<>(lights.values());
        lights.clear();
        for (SceneLightRuntime light : all) {
            if (light != null) light.clear(level, this);
        }
    }

    void clearAudioSources(ServerLevel level) {
        List<SceneAudioRuntime> all = new ArrayList<>(audioSources.values());
        audioSources.clear();
        for (SceneAudioRuntime audio : all) {
            if (audio != null) audio.clear(level);
        }
    }

    void clearPostProcesses(ServerLevel level) {
        postProcesses.clear();
        if (level != null) ScenePostProcessRuntime.clearScene(level, sceneId);
    }

    public SprauteHitboxEntity hitboxCollider(String nodeId) {
        return nodeId == null ? null : hitboxColliders.get(nodeId);
    }

    public void putHitboxCollider(String nodeId, SprauteHitboxEntity collider) {
        if (nodeId != null && collider != null) hitboxColliders.put(nodeId, collider);
    }

    public void removeHitboxCollider(String nodeId) {
        SprauteHitboxEntity collider = nodeId == null ? null : hitboxColliders.remove(nodeId);
        if (collider != null && collider.isAlive()) collider.discard();
    }

    /** Server tick: trigger overlap + per-player solid collision. */
    public void tickHitboxes(ServerLevel level) {
        SceneHitboxCollision.tickState(this, level);
    }

    /** Server tick: emit particle bursts for PARTICLE nodes. */
    public void tickParticles(ServerLevel level) {
        if (level == null || particles.isEmpty()) return;
        for (SceneParticleRuntime runtime : particles.values()) {
            runtime.tick(this, level);
        }
    }

    /** Server tick: place/update minecraft:light for LIGHT nodes. */
    public void tickLights(ServerLevel level) {
        if (level == null || lights.isEmpty()) return;
        for (var entry : lights.entrySet()) {
            SceneNode node = document.findNode(entry.getKey());
            SceneLightRuntime runtime = entry.getValue();
            if (runtime == null) continue;
            if (node == null) {
                runtime.clear(level, this);
                continue;
            }
            runtime.tick(this, level, node);
        }
    }

    /** Server tick: sync AUDIO_SOURCE falloff sounds to clients. */
    public void tickAudioSources(ServerLevel level) {
        if (level == null || audioSources.isEmpty()) return;
        for (var entry : audioSources.entrySet()) {
            SceneNode node = document.findNode(entry.getKey());
            SceneAudioRuntime runtime = entry.getValue();
            if (runtime == null) continue;
            if (node == null) {
                runtime.clear(level);
                continue;
            }
            runtime.tick(this, level, node);
        }
    }

    /** Server tick: sync POST_PROCESS volumes to clients. */
    public void tickPostProcesses(ServerLevel level) {
        if (level == null || postProcesses.isEmpty()) return;
        for (var entry : postProcesses.entrySet()) {
            SceneNode node = document.findNode(entry.getKey());
            ScenePostProcessRuntime runtime = entry.getValue();
            if (runtime == null) continue;
            if (node == null) {
                runtime.clear(level);
                continue;
            }
            runtime.tick(this, level, node);
        }
    }

    public void syncPostProcessesNow(ServerLevel level) {
        if (level == null || postProcesses.isEmpty()) return;
        for (var entry : postProcesses.entrySet()) {
            SceneNode node = document.findNode(entry.getKey());
            ScenePostProcessRuntime runtime = entry.getValue();
            if (runtime == null || node == null) continue;
            runtime.syncNow(this, level, node);
        }
    }

    /**
     * Removes a node with its whole subtree from the live scene:
     * despawns entities, unregisters hitboxes and detaches the node from its parent.
     */
    public boolean removeNodeSubtree(String nodeId) {
        SceneNode node = nodeId == null ? null : document.findNode(nodeId);
        if (node == null || node.type == SceneNodeType.SCENE
                || node.type == SceneNodeType.PLAYERS || node.type == SceneNodeType.PLAYER
                || node.type == SceneNodeType.GLOBAL_WORLD || node.type == SceneNodeType.LOCAL_WORLD) {
            return false;
        }
        List<SceneNode> subtree = new ArrayList<>();
        collectSubtree(node, subtree);
        ServerLevel level = null;
        List<SceneLightRuntime> lightsToClear = new ArrayList<>();
        List<SceneAudioRuntime> audioToClear = new ArrayList<>();
        List<ScenePostProcessRuntime> postToClear = new ArrayList<>();
        for (SceneNode n : subtree) {
            if (n.type == SceneNodeType.CUSTOM) {
                org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.fireRemove(this, n);
            }
            if (SceneWorldCanvasRuntime.isWorldCanvas(n)) {
                SceneWorldCanvasRuntime.remove(this, n);
            }
            Object obj = runtimeObjects.remove(n.id);
            // Discard even if dying (!isAlive) so reconcile cannot treat it as a missing entity.
            if (obj instanceof Entity entity && !entity.isRemoved()) {
                if (level == null && org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.level(entity) instanceof ServerLevel sl) level = sl;
                entity.discard();
            }
            hitboxes.remove(n.id);
            particles.remove(n.id);
            SceneLightRuntime light = lights.remove(n.id);
            if (light != null) lightsToClear.add(light);
            SceneAudioRuntime audio = audioSources.remove(n.id);
            if (audio != null) audioToClear.add(audio);
            ScenePostProcessRuntime post = postProcesses.remove(n.id);
            if (post != null) postToClear.add(post);
            removeHitboxCollider(n.id);
            viewCache.remove(n.id);
        }
        if (level == null && (!lightsToClear.isEmpty() || !audioToClear.isEmpty() || !postToClear.isEmpty())) {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                level = SceneRuntime.resolveLevelForSync(server, runtimeDimension);
            }
        }
        if (level != null) {
            // Remove claims first, then clear — shared cells stay if another light still uses them.
            for (SceneLightRuntime light : lightsToClear) light.clear(level, this);
            for (SceneAudioRuntime audio : audioToClear) audio.clear(level);
            for (ScenePostProcessRuntime post : postToClear) post.clear(level);
        }
        SceneNode parent = document.findParent(node.id);
        if (parent != null) parent.children.remove(node);
        return true;
    }

    private static void collectSubtree(SceneNode node, List<SceneNode> out) {
        out.add(node);
        for (SceneNode child : node.children) collectSubtree(child, out);
    }

    public Entity resolveEntity(String nodeId, ServerLevel level) {
        Object obj = runtimeObjects.get(nodeId);
        if (obj instanceof Entity entity) return entity;
        if (level == null || nodeId == null || nodeId.isBlank()) return null;
        // Fallback by node id only, and only if the entity is tagged for this scene node.
        // Never resolve by display name — that steals script NPCs / other nodes.
        String nodeTag = "spraute_scene_node_" + nodeId;
        Entity byId = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(nodeId, level);
        if (byId != null && byId.getTags().contains(nodeTag)) return byId;
        byId = org.zonarstudio.spraute_engine.entity.ModelManager.getEntity(nodeId, level);
        if (byId != null && byId.getTags().contains(nodeTag)) return byId;
        return null;
    }

    static Map<String, Object> transformMap(SceneTransform transform) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (transform == null) return value;
        value.put("x", transform.px);
        value.put("y", transform.py);
        value.put("z", transform.pz);
        value.put("rotationX", transform.rx);
        value.put("rotationY", transform.ry);
        value.put("rotationZ", transform.rz);
        value.put("scaleX", transform.sx);
        value.put("scaleY", transform.sy);
        value.put("scaleZ", transform.sz);
        return value;
    }
}
