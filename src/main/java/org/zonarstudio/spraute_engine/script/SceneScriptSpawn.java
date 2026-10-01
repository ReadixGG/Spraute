package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.scene.NpcNodeBridge;
import org.zonarstudio.spraute_engine.scene.PlayerCameraNodes;
import org.zonarstudio.spraute_engine.scene.PlayerSkinLayers;
import org.zonarstudio.spraute_engine.scene.PlayerSkinOps;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneNodeView;
import org.zonarstudio.spraute_engine.scene.ScenePathResolver;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.scene.SceneTransform;
import org.zonarstudio.spraute_engine.scene.SceneView;
import org.zonarstudio.spraute_engine.scene.resource.SceneParticleDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneProjectileDef;
import org.zonarstudio.spraute_engine.scene.resource.ProjectileDefProps;
import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;
import org.zonarstudio.spraute_engine.registry.CustomParticleRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attaches script {@code create npc/model/hitbox/empty} into a loaded scene hierarchy
 * so {@code /spraute stop} removes them together with the scene.
 */
public final class SceneScriptSpawn {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SceneScriptSpawn() {}

    /** Resolve active scene from script locals ({@code _scene}, {@code _node}, {@code _scriptNode}). */
    public static SceneActiveState stateFromVariables(Map<String, Object> vars) {
        if (vars == null) return null;
        Object sceneVar = vars.get("_scene");
        if (sceneVar instanceof SceneView view) {
            return SceneRuntime.getActiveState(view.getSceneId());
        }
        if (!isBlank(sceneVar)) {
            SceneActiveState byId = SceneRuntime.getActiveState(String.valueOf(sceneVar).trim());
            if (byId != null) return byId;
        }
        Object nodeVar = vars.get("_node");
        if (nodeVar instanceof SceneNodeView node) {
            return SceneRuntime.getActiveState(node.getSceneId());
        }
        Object scriptNodeVar = vars.get("_scriptNode");
        if (scriptNodeVar instanceof SceneNodeView sn) {
            return SceneRuntime.getActiveState(sn.getSceneId());
        }
        return null;
    }

    /** Entity tagged by {@link SceneRuntime#tagAndTrack} → its scene. */
    public static SceneActiveState stateFromEntity(Entity entity) {
        if (entity == null) return null;
        final String prefix = "spraute_scene_";
        final String nodeMarker = prefix + "node_";
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(prefix) || tag.startsWith(nodeMarker)) continue;
            String sceneId = tag.substring(prefix.length()).trim();
            if (sceneId.isEmpty()) continue;
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state != null) return state;
        }
        return null;
    }

    public static SceneActiveState resolveState(Map<String, Object> variables, Map<String, List<Object>> props) {
        Object sceneProp = first(props, "scene");
        if (sceneProp instanceof SceneView view) {
            return SceneRuntime.getActiveState(view.getSceneId());
        }
        if (!isBlank(sceneProp)) {
            SceneActiveState byId = SceneRuntime.getActiveState(String.valueOf(sceneProp).trim());
            if (byId != null) return byId;
        }
        Map<String, Object> vars = variables != null ? variables : ScriptExecutor.currentVariables();
        SceneActiveState fromVars = stateFromVariables(vars);
        if (fromVars != null) return fromVars;
        Object parentProp = first(props, "parent");
        if (parentProp instanceof SceneNodeView node) {
            return SceneRuntime.getActiveState(node.getSceneId());
        }
        return ScriptExecutor.currentSceneState();
    }

    public static String resolveParentId(SceneActiveState state, Map<String, Object> variables,
                                         Map<String, List<Object>> props) {
        if (state == null) return null;
        Map<String, Object> vars = variables != null ? variables : ScriptExecutor.currentVariables();
        Object parentProp = first(props, "parent");
        if (!isBlank(parentProp)) {
            String resolved = resolveNodeId(state, parentProp);
            if (resolved != null) return resolved;
            LOGGER.warn("[Spraute Scene] Unknown parent '{}', using script scene default", parentProp);
        }
        if (vars != null) {
            Object nodeVar = vars.get("_node");
            if (nodeVar instanceof SceneNodeView node && state.sceneId.equals(node.getSceneId())) {
                SceneNode n = state.document.findNode(node.getNodeId());
                if (n != null && n.type.acceptsChildren()) return n.id;
            }
            // Script node cannot hold children — attach next to it (under its parent).
            Object scriptNodeVar = vars.get("_scriptNode");
            if (scriptNodeVar instanceof SceneNodeView sn && state.sceneId.equals(sn.getSceneId())) {
                SceneNode scriptNode = state.document.findNode(sn.getNodeId());
                if (scriptNode != null) {
                    SceneNode scriptParent = state.document.findParent(scriptNode.id);
                    if (scriptParent != null && scriptParent.type.acceptsChildren()) {
                        return scriptParent.id;
                    }
                }
            }
        }
        // Root itself only accepts PLAYERS/GLOBAL_WORLD/LOCAL_WORLD since the worlds split —
        // a create with no parent prop and no scene-node script context (console/global script)
        // falls back to the shared Global World, not the scene root directly.
        return state.document.ensureGlobalWorldNode().id;
    }

    public static String resolveNodeId(SceneActiveState state, Object ref) {
        if (state == null || ref == null) return null;
        if (ref instanceof SceneNodeView view) {
            return state.sceneId.equals(view.getSceneId()) ? view.getNodeId() : null;
        }
        if (ref instanceof SceneView) return state.document.ensureGlobalWorldNode().id;
        String key = String.valueOf(ref).trim();
        if (key.isEmpty() || "null".equals(key)) return null;
        SceneNode byPath = ScenePathResolver.findNode(state.document, key);
        if (byPath != null) return byPath.id;
        SceneNode byName = ScenePathResolver.findByName(state.document.root, key);
        return byName == null ? null : byName.id;
    }

    /** After NpcSpawnHelper/ModelSpawnHelper spawned an entity, register it as a scene node. */
    public static SceneNodeView attachEntity(SceneActiveState state, String parentId, String scriptId,
                                             SceneNodeType type, Map<String, List<Object>> props,
                                             Entity entity, Map<String, Object> variables) {
        if (state == null || entity == null || type == null) return null;
        if (type != SceneNodeType.NPC && type != SceneNodeType.MODEL) return null;

        SceneNode node = type == SceneNodeType.NPC ? SceneNode.npc(displayName(scriptId, props))
                : SceneNode.model(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyEntityPayload(node, props, entity);
        applyWorldTransformFromProps(state, parentId, node, props, entity);

        if (!state.document.addChild(parentId, node)) {
            LOGGER.warn("[Spraute Scene] Cannot attach {} '{}' under parent {}", type, scriptId, parentId);
            return null;
        }

        SceneRuntime.tagAndTrack(state.sceneId, node, entity);
        state.putRuntimeObject(node.id, entity);
        SceneNode parent = state.document.findParent(node.id);
        if (parent != null && parent.type == SceneNodeType.ROUTE) {
            SceneTransform placed = state.document.worldTransform(node.id);
            if (placed != null) {
                entity.moveTo(placed.px, placed.py, placed.pz, (float) placed.ry, (float) placed.rx);
            }
        }
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnHitbox(SceneActiveState state, String parentId, String scriptId,
                                            Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create hitbox requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        SceneNode node = SceneNode.hitbox(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyHitboxProps(node, props);
        applyWorldTransformFromProps(state, parentId, node, props, null);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach hitbox '" + scriptId + "' under parent " + parentId);
        }
        state.registerHitbox(node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    /**
     * {@code create particle id { texture = "…" }} — same as Hierarchy → Resources → Particle (type):
     * {@code RES_PARTICLE} in the scene tree + {@link CustomParticleRegistry}.
     * Requires an active scene (like {@code create light} / {@code create empty}).
     * Boot registration from {@code .spr} / saved scenes still happens via {@link CustomParticleRegistry#parseScripts()}.
     */
    public static SceneNodeView spawnParticleDef(SceneActiveState state, String parentId, String scriptId,
                                                 Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create particle requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }

        String texture = "";
        Object tex = first(props, "texture");
        if (!isBlank(tex)) texture = String.valueOf(tex).trim();

        String preferredId = preferredNodeId(props, scriptId);
        if (preferredId == null || preferredId.isBlank()) {
            throw new RuntimeException("create particle requires an id");
        }

        SceneNode node = SceneNode.resParticle(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredId);
        if (node.particleDef == null) node.particleDef = new SceneParticleDef();
        node.particleDef.id = node.id;
        node.particleDef.texture = texture;
        applyCommonProps(node, props);

        CustomParticleRegistry.put(node.id, texture);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach particle '" + scriptId + "' under parent " + parentId);
        }
        state.putRuntimeObject(node.id, node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    /**
     * {@code create projectile id { … }} — {@code RES_PROJECTILE} in the scene tree + {@link CustomProjectileRegistry}.
     */
    public static SceneNodeView spawnProjectileDef(SceneActiveState state, String parentId, String scriptId,
                                                   Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create projectile requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }

        String preferredId = preferredNodeId(props, scriptId);
        if (preferredId == null || preferredId.isBlank()) {
            throw new RuntimeException("create projectile requires an id");
        }

        SceneNode node = SceneNode.resProjectile(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredId);
        if (node.projectileDef == null) node.projectileDef = new SceneProjectileDef();
        node.projectileDef.id = node.id;
        ProjectileDefProps.apply(node.projectileDef, props);
        applyCommonProps(node, props);

        CustomProjectileRegistry.register(node.id, node.projectileDef.toRegistryDef());

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach projectile '" + scriptId + "' under parent " + parentId);
        }
        state.putRuntimeObject(node.id, node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnParticleEmitter(SceneActiveState state, String parentId, String scriptId,
                                                     Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create particleEmitter requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        SceneNode node = SceneNode.particle(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyParticleProps(node, props);
        applyWorldTransformFromProps(state, parentId, node, props, null);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach particleEmitter '" + scriptId + "' under parent " + parentId);
        }
        state.registerParticle(node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    /**
     * Backs the imperative particle-shape primitives ({@code particleLine}, {@code particleSpawn},
     * {@code particleCircle}, …): fires an already-built [x,y,z,vx,vy,vz]*n burst as a REAL
     * {@code PARTICLE} scene node — same {@link SceneActiveState#registerParticle} /
     * {@link SceneRuntime#trackNodeKeys} path a Hierarchy-placed emitter gets — instead of the
     * old disconnected network-only burst that had no node, id, or gameplay proxy of its own.
     *
     * <p>{@code key} identifies the emitter across repeated calls: the same key re-fires the
     * same node (kept in the scene tree — e.g. a looping muzzle flash script). A blank/null
     * key fires an anonymous one-off burst whose backing node is created, used, and removed
     * again within this call — it still goes through the full node + gameplay-proxy pipeline
     * for that single burst, it just doesn't linger in the tree afterward.
     */
    public static SceneNodeView fireScriptParticleBurst(net.minecraft.server.level.ServerLevel level,
                                                        SceneActiveState state, String parentId, String key,
                                                        org.zonarstudio.spraute_engine.particle.ParticleSystemSpec spec,
                                                        float[] data) {
        if (level == null || state == null || spec == null || data == null || data.length < 6) return null;
        boolean anonymous = key == null || key.isBlank();
        String wantedId = anonymous ? null : key.trim();
        SceneNode node = wantedId == null ? null : state.document.findNode(wantedId);
        boolean created = node == null || node.type != SceneNodeType.PARTICLE;
        if (created) {
            node = SceneNode.particle(wantedId != null ? wantedId : "script_fx");
            node.id = uniqueId(state.document, wantedId != null ? wantedId : "script_fx_" + UUID.randomUUID());
            node.particleLoop = false;
        }
        node.particleSystem = spec;

        SceneNode parent = state.document.findNode(parentId);
        SceneTransform parentWorld = parent == null || parent.type == SceneNodeType.SCENE
                ? null : state.document.worldTransform(parentId);
        SceneTransform world = new SceneTransform();
        world.px = data[0];
        world.py = data[1];
        world.pz = data[2];
        node.transform = SceneTransform.toLocal(parentWorld, world);

        if (created) {
            if (!state.document.addChild(parentId, node)) return null;
            state.registerParticle(node);
            SceneRuntime.trackNodeKeys(state.sceneId, node);
        }

        org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.emitCustomPoints(level, node.id, spec, data);

        SceneNodeView view = state.nodeView(node);
        if (anonymous) state.removeNodeSubtree(node.id);
        return view;
    }

    public static SceneNodeView spawnLight(SceneActiveState state, String parentId, String scriptId,
                                           Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create light requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        SceneNode node = SceneNode.light(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyLightProps(node, props);
        applyWorldTransformFromProps(state, parentId, node, props, null);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach light '" + scriptId + "' under parent " + parentId);
        }
        state.registerLight(node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnAudioSource(SceneActiveState state, String parentId, String scriptId,
                                                 Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create audioSource requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        SceneNode node = SceneNode.audioSource(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyAudioSourceProps(node, props);
        applyWorldTransformFromProps(state, parentId, node, props, null);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach audioSource '" + scriptId + "' under parent " + parentId);
        }
        state.registerAudioSource(node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnPostProcess(SceneActiveState state, String parentId, String scriptId,
                                                 Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create postProcess requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        parentId = resolvePostProcessParent(state, parentId);
        SceneNode node = SceneNode.postProcess(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyPostProcessProps(node, props);
        node.post().global = true;
        // Camera-bound: ignore world box / transform props.
        node.transform = new SceneTransform();

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach postProcess '" + scriptId
                    + "' — parent must be a CAMERA (or PLAYER) node, got " + parentId);
        }
        state.registerPostProcess(node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    /** Prefer CAMERA; if parent is PLAYER, attach under its CAMERA child. */
    private static String resolvePostProcessParent(SceneActiveState state, String parentId) {
        if (state == null || state.document == null) return parentId;
        SceneNode parent = state.document.findNode(parentId);
        if (parent == null) return parentId;
        if (parent.type == SceneNodeType.CAMERA) return parent.id;
        if (parent.type == SceneNodeType.PLAYER) {
            SceneNode players = state.document.ensurePlayersNode();
            SceneNode camera = PlayerCameraNodes.ensureCamera(parent, players, state.document);
            return camera == null ? parentId : camera.id;
        }
        return parentId;
    }

    public static SceneNodeView spawnCustom(SceneActiveState state, String parentId, String typeId, String scriptId,
                                            Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create " + typeId + " requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.ensureForDocument(state.document);
        if (!org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.isRegistered(state.document, typeId)) {
            throw new RuntimeException("Unknown custom node type '" + typeId
                    + "' — declare it in a SCRIPT node of this scene");
        }
        SceneNode node = SceneNode.custom(typeId, displayName(scriptId, props));
        var def = org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.get(state.document, typeId);
        if (def != null) {
            node.customData = new java.util.LinkedHashMap<>(def.defaultData());
        }
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyCustomProps(node, props);
        // Data-only by default (ignore world transform even if x/y/z present) — unless a
        // Java-declared GenericNodeType opts in via usesTransform() (a hazard zone, etc.).
        var genericType = org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.get(typeId);
        if (genericType != null && genericType.usesTransform()) {
            applyWorldTransformFromProps(state, parentId, node, props, null);
        }
        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach " + typeId + " '" + scriptId + "' under parent " + parentId);
        }
        state.putRuntimeObject(node.id, node);
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.fireCreate(state, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnEmpty(SceneActiveState state, String parentId, String scriptId,
                                           Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create empty requires an active scene (_scene), parent scene-node, or scene = \"id\"");
        }
        SceneNode node = SceneNode.empty(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        applyWorldTransformFromProps(state, parentId, node, props, null);

        if (!state.document.addChild(parentId, node)) {
            throw new RuntimeException("Cannot attach empty '" + scriptId + "' under parent " + parentId);
        }
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    public static SceneNodeView spawnSkinOverlay(SceneActiveState state, String parentId, String scriptId,
                                                 Map<String, List<Object>> props, Map<String, Object> variables) {
        if (state == null) {
            throw new RuntimeException("create skin_overlay requires an active scene (_scene), parent player node, or scene = \"id\"");
        }
        SceneNode parent = resolveSkinOverlayParent(state, parentId, props, variables);
        if (parent == null || parent.type != SceneNodeType.PLAYER) {
            throw new RuntimeException("create skin_overlay requires parent = player (PLAYER node or online player)");
        }

        SceneNode node = SceneNode.skinOverlay(displayName(scriptId, props));
        node.id = uniqueId(state.document, preferredNodeId(props, scriptId));
        applyCommonProps(node, props);
        Object texture = first(props, "texture");
        if (texture == null) texture = first(props, "skin");
        if (!isBlank(texture)) node.texture = String.valueOf(texture).trim();
        Object active = first(props, "active");
        if (active != null) {
            node.active = Boolean.TRUE.equals(active) || "true".equalsIgnoreCase(String.valueOf(active));
        }
        PlayerSkinLayers.applyOverlayProps(node, props);

        if (!state.document.addChild(parent.id, node)) {
            throw new RuntimeException("Cannot attach skin_overlay '" + scriptId + "' under player " + parent.id);
        }
        SceneRuntime.trackNodeKeys(state.sceneId, node);
        PlayerSkinOps.applyFromPlayerNode(parent);
        SceneNodeView view = state.nodeView(node);
        bindScriptAliases(variables, scriptId, node, view);
        return view;
    }

    private static SceneNode resolveSkinOverlayParent(SceneActiveState state, String parentId,
                                                      Map<String, List<Object>> props, Map<String, Object> variables) {
        SceneNode parent = parentId == null ? null : state.document.findNode(parentId);
        if (parent != null && parent.type == SceneNodeType.PLAYER) return parent;
        Object playerRef = first(props, "player");
        if (playerRef == null) playerRef = first(props, "parent");
        UUID uuid = PlayerSkinOps.uuidOf(playerRef);
        if (uuid == null && variables != null) {
            uuid = PlayerSkinOps.uuidOf(variables.get("_eventPlayer"));
        }
        if (uuid != null) {
            SceneNode byUuid = PlayerSkinOps.findPlayerNode(uuid);
            if (byUuid != null) return byUuid;
        }
        return parent;
    }

    private static void applyWorldTransformFromProps(SceneActiveState state, String parentId, SceneNode node,
                                                     Map<String, List<Object>> props, Entity entity) {
        SceneTransform world = worldFromProps(props, entity);
        SceneNode parent = state.document.findNode(parentId);
        SceneTransform parentWorld = parent == null || parent.type == SceneNodeType.SCENE
                ? null
                : state.document.worldTransform(parentId);
        node.transform = SceneTransform.toLocal(parentWorld, world);
        if (entity != null) {
            float yaw = (float) world.ry;
            float pitch = (float) world.rx;
            entity.moveTo(world.px, world.py, world.pz, yaw, pitch);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            if (entity instanceof SprauteNpcEntity npc) {
                npc.setYBodyRot(yaw);
                npc.setYHeadRot(yaw);
                npc.setModelRotation((float) world.rx, 0f, (float) world.rz);
                npc.setModelScale((float) world.sx, (float) world.sy, (float) world.sz);
            } else if (entity instanceof SprauteModelEntity model) {
                model.setModelRotation((float) world.rx, 0f, (float) world.rz);
                model.setModelScale((float) world.sx, (float) world.sy, (float) world.sz);
            }
        }
    }

    private static SceneTransform worldFromProps(Map<String, List<Object>> props, Entity entity) {
        SceneTransform w = new SceneTransform();
        if (entity != null) {
            w.px = entity.getX();
            w.py = entity.getY();
            w.pz = entity.getZ();
            w.ry = entity.getYRot();
            w.rx = entity.getXRot();
        }
        Double x = number(props, "x");
        Double y = number(props, "y");
        Double z = number(props, "z");
        List<Object> pos = props.get("pos");
        if (pos != null && !pos.isEmpty()) {
            Object first = pos.get(0);
            if (first instanceof List<?> l && l.size() >= 3) {
                w.px = ((Number) l.get(0)).doubleValue();
                w.py = ((Number) l.get(1)).doubleValue();
                w.pz = ((Number) l.get(2)).doubleValue();
            } else if (pos.size() >= 3) {
                w.px = ((Number) pos.get(0)).doubleValue();
                w.py = ((Number) pos.get(1)).doubleValue();
                w.pz = ((Number) pos.get(2)).doubleValue();
            }
        }
        if (x != null) w.px = x;
        if (y != null) w.py = y;
        if (z != null) w.pz = z;

        Double yaw = number(props, "yaw");
        if (yaw == null) yaw = number(props, "rotationY");
        Double rx = number(props, "rotationX");
        Double rz = number(props, "rotationZ");
        List<Object> rot = props.get("rotate");
        if (rot != null && rot.size() >= 1) {
            Object first = rot.get(0);
            if (first instanceof List<?> l && !l.isEmpty()) {
                w.ry = ((Number) l.get(0)).doubleValue();
                if (l.size() >= 2) w.rx = ((Number) l.get(1)).doubleValue();
            } else {
                w.ry = ((Number) rot.get(0)).doubleValue();
                if (rot.size() >= 2) w.rx = ((Number) rot.get(1)).doubleValue();
            }
        }
        if (yaw != null) w.ry = yaw;
        if (rx != null) w.rx = rx;
        if (rz != null) w.rz = rz;

        Double scale = number(props, "scale");
        Double sx = number(props, "scaleX");
        Double sy = number(props, "scaleY");
        Double sz = number(props, "scaleZ");
        if (scale != null) {
            w.sx = w.sy = w.sz = scale;
        } else {
            if (sx != null) w.sx = sx;
            if (sy != null) w.sy = sy;
            if (sz != null) w.sz = sz;
        }
        return w;
    }

    private static void applyCommonProps(SceneNode node, Map<String, List<Object>> props) {
        if (props.containsKey("name")) node.name = String.valueOf(props.get("name").get(0));
        if (props.containsKey("tags") || props.containsKey("tag")) {
            List<Object> tags = props.containsKey("tags") ? props.get("tags") : props.get("tag");
            if (tags != null) {
                for (Object t : tags) {
                    if (t == null) continue;
                    if (t instanceof List<?> list) {
                        for (Object el : list) {
                            if (el != null) node.tags.add(String.valueOf(el).trim().toLowerCase());
                        }
                    } else {
                        node.tags.add(String.valueOf(t).trim().toLowerCase());
                    }
                }
            }
        }
    }

    private static void applyEntityPayload(SceneNode node, Map<String, List<Object>> props, Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) {
            NpcNodeBridge.fillNodeFromNpc(node, npc, props);
            return;
        }
        if (props.containsKey("model")) node.model = String.valueOf(props.get("model").get(0));
        if (props.containsKey("texture")) node.texture = String.valueOf(props.get("texture").get(0));
        if (props.containsKey("animation")) node.animation = String.valueOf(props.get("animation").get(0));
        if (props.containsKey("showName")) {
            Object v = props.get("showName").get(0);
            node.nameVisible = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (entity instanceof SprauteModelEntity model) {
            if (model.getCustomName() != null) node.name = model.getCustomName().getString();
            node.nameVisible = model.isCustomNameVisible();
        }
    }

    private static void applyHitboxProps(SceneNode node, Map<String, List<Object>> props) {
        Double w = number(props, "w");
        if (w == null) w = number(props, "width");
        Double h = number(props, "h");
        if (h == null) h = number(props, "height");
        Double d = number(props, "depth");
        if (w != null) node.hitboxWidth = w.floatValue();
        if (h != null) node.hitboxHeight = h.floatValue();
        if (d != null) node.hitboxDepth = d.floatValue();
        Double ox = number(props, "offsetX");
        Double oy = number(props, "offsetY");
        Double oz = number(props, "offsetZ");
        if (ox != null) node.hitboxOffsetX = ox.floatValue();
        if (oy != null) node.hitboxOffsetY = oy.floatValue();
        if (oz != null) node.hitboxOffsetZ = oz.floatValue();
        if (props.containsKey("solid")) {
            Object v = props.get("solid").get(0);
            node.hitboxSolid = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (props.containsKey("trigger")) {
            Object v = props.get("trigger").get(0);
            node.hitboxTrigger = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        Object triggerId = first(props, "triggerId");
        if (triggerId == null) triggerId = first(props, "trigger_id");
        if (triggerId != null) node.hitboxTriggerId = String.valueOf(triggerId).trim();
        if (props.containsKey("showHitbox") || props.containsKey("show_hitbox")) {
            Object v = props.containsKey("showHitbox") ? props.get("showHitbox").get(0) : props.get("show_hitbox").get(0);
            node.showHitbox = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
    }

    private static void applyParticleProps(SceneNode node, Map<String, List<Object>> props) {
        Object type = first(props, "type");
        if (type == null) type = first(props, "particle");
        if (type == null) type = first(props, "particleType");
        if (type != null) node.particleType = String.valueOf(type);

        Object shape = first(props, "shape");
        if (shape == null) shape = first(props, "particleShape");
        if (shape != null) {
            node.particleShape = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.normalizeShape(String.valueOf(shape));
        }

        Double count = number(props, "count");
        if (count == null) count = number(props, "particleCount");
        if (count != null) node.particleCount = Math.max(1, count.intValue());

        Double sx = number(props, "spreadX");
        if (sx == null) sx = number(props, "dx");
        Double sy = number(props, "spreadY");
        if (sy == null) sy = number(props, "dy");
        Double sz = number(props, "spreadZ");
        if (sz == null) sz = number(props, "dz");
        if (sx != null) node.particleSpreadX = sx.floatValue();
        if (sy != null) node.particleSpreadY = sy.floatValue();
        if (sz != null) node.particleSpreadZ = sz.floatValue();

        Double speed = number(props, "speed");
        if (speed != null) node.particleSpeed = speed.floatValue();
        Double grav = number(props, "gravity");
        if (grav != null) node.particleGravity = grav.floatValue();

        if (props.containsKey("collide") || props.containsKey("collision")
                || props.containsKey("collideBlocks") || props.containsKey("collide_blocks")
                || props.containsKey("particleCollide") || props.containsKey("hasPhysics")) {
            Object v = first(props, "collide");
            if (v == null) v = first(props, "collision");
            if (v == null) v = first(props, "collideBlocks");
            if (v == null) v = first(props, "collide_blocks");
            if (v == null) v = first(props, "particleCollide");
            if (v == null) v = first(props, "hasPhysics");
            node.particleCollide = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }

        Object emitMode = first(props, "emitMode");
        if (emitMode == null) emitMode = first(props, "mode");
        if (emitMode == null) emitMode = first(props, "particleEmitMode");
        if (emitMode != null) {
            node.particleEmitMode = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter
                    .normalizeEmitMode(String.valueOf(emitMode));
        }

        Double dirX = number(props, "dirX");
        if (dirX == null) dirX = number(props, "directionX");
        Double dirY = number(props, "dirY");
        if (dirY == null) dirY = number(props, "directionY");
        Double dirZ = number(props, "dirZ");
        if (dirZ == null) dirZ = number(props, "directionZ");
        if (dirX != null) node.particleDirX = dirX.floatValue();
        if (dirY != null) node.particleDirY = dirY.floatValue();
        if (dirZ != null) node.particleDirZ = dirZ.floatValue();

        Double cone = number(props, "cone");
        if (cone == null) cone = number(props, "particleCone");
        if (cone != null) node.particleCone = cone.floatValue();

        Double speedSpread = number(props, "speedSpread");
        if (speedSpread == null) speedSpread = number(props, "particleSpeedSpread");
        if (speedSpread != null) node.particleSpeedSpread = speedSpread.floatValue();

        Double life = number(props, "lifetime");
        if (life == null) life = number(props, "life");
        if (life == null) life = number(props, "particleLifetime");
        if (life != null) node.particleLifetime = Math.max(0, life.intValue());

        Double fade = number(props, "fade");
        if (fade == null) fade = number(props, "fadeOut");
        if (fade == null) fade = number(props, "fade_out");
        if (fade == null) fade = number(props, "particleFade");
        if (fade != null) node.particleFade = Math.max(0, fade.intValue());

        if (props.containsKey("loop")) {
            Object v = props.get("loop").get(0);
            node.particleLoop = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        Double interval = number(props, "interval");
        if (interval != null) node.particleInterval = Math.max(1, interval.intValue());

        Double radius = number(props, "radius");
        if (radius != null) node.particleRadius = radius.floatValue();
        Double height = number(props, "height");
        if (height != null) node.particleHeight = height.floatValue();

        Double lx = number(props, "lineX");
        Double ly = number(props, "lineY");
        Double lz = number(props, "lineZ");
        if (lx != null) node.particleLineX = lx.floatValue();
        if (ly != null) node.particleLineY = ly.floatValue();
        if (lz != null) node.particleLineZ = lz.floatValue();

        var spec = node.particles(false);
        if (spec != null) {
            Object texture = first(props, "texture");
            if (texture == null) texture = first(props, "particleTexture");
            if (texture != null) {
                spec.custom = true;
                spec.renderer.texture = String.valueOf(texture);
            } else if (type != null && !hasProp(props, "custom", "customEngine")) {
                // Old create particleEmitter { type: minecraft:* } remains on the legacy bridge.
                spec.custom = false;
            }
            if (hasProp(props, "custom", "customEngine")) spec.custom = boolVal(props, "custom", "customEngine");
            Object renderer = first(props, "renderer");
            if (renderer == null) renderer = first(props, "renderMode");
            if (renderer != null) spec.renderer.mode = String.valueOf(renderer);
            // Model mode: renders a real .geo mesh (same format as an NPC's model) per particle
            // instead of a billboard sprite. `texture` above is reused as the model's skin.
            Object particleModel = first(props, "model");
            if (particleModel == null) particleModel = first(props, "particleModel");
            if (particleModel != null) spec.renderer.model = String.valueOf(particleModel);
            Object blend = first(props, "blend");
            if (blend != null) spec.renderer.blend = String.valueOf(blend);
            if (hasProp(props, "fullbright", "emissive")) spec.renderer.fullbright = boolVal(props, "fullbright", "emissive");
            // The custom engine supports a wider shape vocabulary (box/shell/circle/disc/
            // cone/cylinder/text/image) than the legacy `node.particleShape` field, which
            // only understands point/line/sphere/spiral. When a shape prop was passed this
            // call, resolve it against the full custom vocabulary directly so scripts can
            // reach those extra shapes; otherwise leave the spec's shape type untouched.
            String customShapeOverride = shape != null
                    ? org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.normalizeCustomShape(String.valueOf(shape))
                    : null;
            spec.shape.type = customShapeOverride != null ? customShapeOverride : node.particleShape;
            spec.shape.radius = node.particleRadius;
            spec.shape.height = node.particleHeight;
            spec.shape.lineX = node.particleLineX;
            spec.shape.lineY = node.particleLineY;
            spec.shape.lineZ = node.particleLineZ;
            spec.shape.dirX = node.particleDirX;
            spec.shape.dirY = node.particleDirY;
            spec.shape.dirZ = node.particleDirZ;
            spec.shape.angle = node.particleCone;
            spec.shape.spreadX = node.particleSpreadX;
            spec.shape.spreadY = node.particleSpreadY;
            spec.shape.spreadZ = node.particleSpreadZ;

            Double shapeSizeX = number(props, "sizeX");
            if (shapeSizeX == null) shapeSizeX = number(props, "shapeSizeX");
            Double shapeSizeY = number(props, "sizeY");
            if (shapeSizeY == null) shapeSizeY = number(props, "shapeSizeY");
            Double shapeSizeZ = number(props, "sizeZ");
            if (shapeSizeZ == null) shapeSizeZ = number(props, "shapeSizeZ");
            if (shapeSizeX != null) spec.shape.sizeX = shapeSizeX.floatValue();
            if (shapeSizeY != null) spec.shape.sizeY = shapeSizeY.floatValue();
            if (shapeSizeZ != null) spec.shape.sizeZ = shapeSizeZ.floatValue();

            Object shapeText = first(props, "text");
            if (shapeText != null) spec.shape.text = String.valueOf(shapeText);
            Object shapeImage = first(props, "image");
            if (shapeImage == null) shapeImage = first(props, "picture");
            if (shapeImage != null) spec.shape.image = String.valueOf(shapeImage);
            Double sampleThreshold = number(props, "sampleThreshold");
            if (sampleThreshold == null) sampleThreshold = number(props, "threshold");
            if (sampleThreshold != null) {
                spec.shape.sampleThreshold = (float) Math.max(0.0, Math.min(1.0, sampleThreshold));
            }
            Double sampleResolution = number(props, "sampleResolution");
            if (sampleResolution == null) sampleResolution = number(props, "resolution");
            if (sampleResolution != null) {
                spec.shape.sampleResolution = Math.max(4, Math.min(256, sampleResolution.intValue()));
            }
            float spread = Math.max(0f, node.particleSpeedSpread);
            spec.initial.speedMin = Math.max(0f, node.particleSpeed * (1f - spread));
            spec.initial.speedMax = Math.max(spec.initial.speedMin, node.particleSpeed * (1f + spread));
            if (node.particleLifetime > 0) {
                spec.initial.lifetimeMin = node.particleLifetime / 20f;
                spec.initial.lifetimeMax = node.particleLifetime / 20f;
            }
            spec.emission.loop = node.particleLoop;
            Double rate = number(props, "rate");
            if (rate == null) rate = number(props, "rateOverTime");
            if (rate != null) spec.emission.rate = Math.max(0f, rate.floatValue());
            Double duration = number(props, "duration");
            if (duration != null) spec.emission.duration = Math.max(0.05f, duration.floatValue());
            Double maxParticles = number(props, "maxParticles");
            if (maxParticles != null) spec.emission.maxParticles = Math.max(1, maxParticles.intValue());
            Double size = number(props, "size");
            if (size != null) {
                spec.initial.sizeMin = Math.max(0f, size.floatValue());
                spec.initial.sizeMax = Math.max(0f, size.floatValue());
            }
            Double sizeMin = number(props, "sizeMin");
            Double sizeMax = number(props, "sizeMax");
            if (sizeMin != null) spec.initial.sizeMin = Math.max(0f, sizeMin.floatValue());
            if (sizeMax != null) spec.initial.sizeMax = Math.max(0f, sizeMax.floatValue());
            Double lifeMin = number(props, "lifetimeMin");
            Double lifeMax = number(props, "lifetimeMax");
            if (lifeMin != null) spec.initial.lifetimeMin = Math.max(0.05f, lifeMin.floatValue());
            if (lifeMax != null) spec.initial.lifetimeMax = Math.max(0.05f, lifeMax.floatValue());
            Double gravityY = number(props, "gravityY");
            spec.motion.gravityY = gravityY == null
                    ? -Math.max(0f, node.particleGravity) * 0.04f
                    : gravityY.floatValue();
            Double drag = number(props, "drag");
            if (drag != null) spec.motion.drag = Math.max(0f, drag.floatValue());
            Double noise = number(props, "noise");
            if (noise != null) spec.motion.noiseStrength = noise.floatValue();
            Double vortex = number(props, "vortex");
            if (vortex == null) vortex = number(props, "vortexStrength");
            if (vortex != null) spec.motion.vortexStrength = vortex.floatValue();
            // Attractor: constant pull toward a target point. Combined with vortex above, this is
            // how a stable orbiting ball of particles is built — vortex alone just spirals outward.
            Double attractor = number(props, "attractor");
            if (attractor == null) attractor = number(props, "attractorStrength");
            if (attractor != null) spec.motion.attractorStrength = attractor.floatValue();
            Object attractorTarget = first(props, "attractorTarget");
            if (attractorTarget != null) spec.motion.attractorTarget = String.valueOf(attractorTarget);
            Double glow = number(props, "glow");
            if (glow == null) glow = number(props, "glowStrength");
            if (glow != null) spec.renderer.glowStrength = Math.max(0f, glow.floatValue());
            Double softEdge = number(props, "softEdge");
            if (softEdge != null) {
                spec.renderer.softEdge = Math.max(0f, Math.min(1f, softEdge.floatValue()));
            }
            spec.collision.enabled = node.particleCollide;
            if (hasProp(props, "trail")) spec.trail.enabled = boolVal(props, "trail");
            if (hasProp(props, "gameplay", "gameplayProxy")) {
                spec.gameplay.enabled = boolVal(props, "gameplay", "gameplayProxy");
            }
            // Particle-as-light: real, moving minecraft:light on this system's gameplay proxies.
            if (hasProp(props, "light", "emitsLight")) {
                spec.light.enabled = boolVal(props, "light", "emitsLight");
                if (spec.light.enabled) spec.gameplay.enabled = true;
            }
            Double lightLevel = number(props, "lightLevel");
            if (lightLevel != null) spec.light.level = Math.max(0, Math.min(15, lightLevel.intValue()));
            Double lightCount = number(props, "lightCount");
            if (lightCount == null) lightCount = number(props, "maxLights");
            if (lightCount != null) spec.light.maxLights = Math.max(0, Math.min(16, lightCount.intValue()));

            Object startColor = first(props, "color");
            if (startColor == null) startColor = first(props, "colorStart");
            if (startColor != null) {
                spec.initial.colorMin = String.valueOf(startColor);
                spec.initial.colorMax = String.valueOf(startColor);
                spec.lifetime.colorStart = String.valueOf(startColor);
            }
            Object endColor = first(props, "colorEnd");
            if (endColor != null) spec.lifetime.colorEnd = String.valueOf(endColor);

            // Per-parameter over-lifetime curves (size/color/speed): "linear" | "ease_in" |
            // "ease_out" | "ease_in_out" | "smooth" | "pulse" | "step".
            Object sizeCurve = first(props, "sizeCurve");
            if (sizeCurve != null) spec.lifetime.sizeCurve = String.valueOf(sizeCurve);
            Object colorCurve = first(props, "colorCurve");
            if (colorCurve == null) colorCurve = first(props, "alphaCurve");
            if (colorCurve != null) spec.lifetime.colorCurve = String.valueOf(colorCurve);
            Object speedCurve = first(props, "speedCurve");
            if (speedCurve != null) spec.lifetime.speedCurve = String.valueOf(speedCurve);

            Double sizeStart = number(props, "sizeStart");
            if (sizeStart != null) spec.lifetime.sizeStart = Math.max(0f, sizeStart.floatValue());
            Double sizeEnd = number(props, "sizeEnd");
            if (sizeEnd != null) spec.lifetime.sizeEnd = Math.max(0f, sizeEnd.floatValue());
            Double speedStart = number(props, "speedStart");
            if (speedStart != null) spec.lifetime.speedStart = Math.max(0f, speedStart.floatValue());
            Double speedEnd = number(props, "speedEnd");
            if (speedEnd != null) spec.lifetime.speedEnd = Math.max(0f, speedEnd.floatValue());
        }

        if (props.containsKey("preview") || props.containsKey("previewEnabled")) {
            Object v = props.containsKey("preview") ? props.get("preview").get(0) : props.get("previewEnabled").get(0);
            node.previewEnabled = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
    }

    private static void applyLightProps(SceneNode node, Map<String, List<Object>> props) {
        Double level = number(props, "level");
        if (level == null) level = number(props, "lightLevel");
        if (level == null) level = number(props, "light");
        if (level != null) node.lightLevel = Math.max(0, Math.min(15, level.intValue()));

        if (props.containsKey("enabled") || props.containsKey("lightEnabled") || props.containsKey("on")) {
            Object v = props.containsKey("enabled") ? props.get("enabled").get(0)
                    : props.containsKey("lightEnabled") ? props.get("lightEnabled").get(0)
                    : props.get("on").get(0);
            node.lightEnabled = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (props.containsKey("showLight") || props.containsKey("show_light")) {
            Object v = props.containsKey("showLight") ? props.get("showLight").get(0) : props.get("show_light").get(0);
            node.showLight = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
    }

    private static void applyAudioSourceProps(SceneNode node, Map<String, List<Object>> props) {
        Object sound = first(props, "sound");
        if (sound == null) sound = first(props, "audioSound");
        if (sound == null) sound = first(props, "audio_sound");
        if (sound != null) node.audioSound = String.valueOf(sound);

        Object shape = first(props, "shape");
        if (shape == null) shape = first(props, "audioShape");
        if (shape == null) shape = first(props, "audio_shape");
        if (shape != null) node.audioShape = String.valueOf(shape);

        Double full = number(props, "fullRadius");
        if (full == null) full = number(props, "audioFullRadius");
        if (full == null) full = number(props, "full_radius");
        if (full != null) node.audioFullRadius = Math.max(0.05f, full.floatValue());

        Double max = number(props, "maxDistance");
        if (max == null) max = number(props, "audioMaxDistance");
        if (max == null) max = number(props, "max_distance");
        if (max != null) node.audioMaxDistance = Math.max(0.1f, max.floatValue());

        Double vol = number(props, "volume");
        if (vol == null) vol = number(props, "audioVolume");
        if (vol != null) node.audioVolume = Math.max(0f, vol.floatValue());

        Double pitch = number(props, "pitch");
        if (pitch == null) pitch = number(props, "audioPitch");
        if (pitch != null) node.audioPitch = Math.max(0.01f, pitch.floatValue());

        if (props.containsKey("loop") || props.containsKey("audioLoop") || props.containsKey("audio_loop")) {
            Object v = props.containsKey("loop") ? props.get("loop").get(0)
                    : props.containsKey("audioLoop") ? props.get("audioLoop").get(0)
                    : props.get("audio_loop").get(0);
            node.audioLoop = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (props.containsKey("replayOnEnter") || props.containsKey("audioReplayOnEnter")
                || props.containsKey("replay_on_enter") || props.containsKey("audio_replay_on_enter")
                || props.containsKey("playOnEnter") || props.containsKey("restartOnEnter")) {
            Object v = props.containsKey("replayOnEnter") ? props.get("replayOnEnter").get(0)
                    : props.containsKey("audioReplayOnEnter") ? props.get("audioReplayOnEnter").get(0)
                    : props.containsKey("replay_on_enter") ? props.get("replay_on_enter").get(0)
                    : props.containsKey("audio_replay_on_enter") ? props.get("audio_replay_on_enter").get(0)
                    : props.containsKey("playOnEnter") ? props.get("playOnEnter").get(0)
                    : props.get("restartOnEnter").get(0);
            node.audioReplayOnEnter = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (props.containsKey("enabled") || props.containsKey("audioEnabled") || props.containsKey("on")) {
            Object v = props.containsKey("enabled") ? props.get("enabled").get(0)
                    : props.containsKey("audioEnabled") ? props.get("audioEnabled").get(0)
                    : props.get("on").get(0);
            node.audioEnabled = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
        if (props.containsKey("showAudio") || props.containsKey("show_audio")) {
            Object v = props.containsKey("showAudio") ? props.get("showAudio").get(0) : props.get("show_audio").get(0);
            node.showAudio = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        }
    }

    private static void applyPostProcessProps(SceneNode node, Map<String, List<Object>> props) {
        PostProcessSettings s = node.post();
        if (hasProp(props, "enabled", "on")) s.enabled = boolVal(props, "enabled", "on");
        if (hasProp(props, "global", "isGlobal", "is_global")) s.global = boolVal(props, "global", "isGlobal", "is_global");
        Double priority = number(props, "priority");
        if (priority != null) s.priority = priority.intValue();
        Double weight = number(props, "weight");
        if (weight != null) s.weight = weight.floatValue();
        Double blend = number(props, "blendDistance");
        if (blend == null) blend = number(props, "blend_distance");
        if (blend != null) s.blendDistance = Math.max(0f, blend.floatValue());

        if (hasProp(props, "grade")) {
            Object v = first(props, "grade");
            if (v instanceof Number) s.grade = true;
            else s.grade = isTrue(v);
        }
        applyGradeFloat(s, props, "exposure", v -> s.exposure = v);
        applyGradeFloat(s, props, "contrast", v -> s.contrast = v);
        applyGradeFloat(s, props, "saturation", v -> s.saturation = v);
        applyGradeFloat(s, props, "temperature", v -> s.temperature = v);
        applyGradeFloat(s, props, "tint", v -> s.tint = v);
        applyGradeFloat(s, props, "filterR", v -> s.filterR = v);
        applyGradeFloat(s, props, "filter_r", v -> s.filterR = v);
        applyGradeFloat(s, props, "filterG", v -> s.filterG = v);
        applyGradeFloat(s, props, "filter_g", v -> s.filterG = v);
        applyGradeFloat(s, props, "filterB", v -> s.filterB = v);
        applyGradeFloat(s, props, "filter_b", v -> s.filterB = v);

        applyOverrideFloat(props, "vignette", "vignetteIntensity", "vignette_intensity",
                (on, v) -> { if (on != null) s.vignette = on; if (v != null) { s.vignette = true; s.vignetteIntensity = v; } });
        Double vs = number(props, "vignetteSmooth");
        if (vs == null) vs = number(props, "vignette_smooth");
        if (vs != null) {
            s.vignette = true;
            s.vignetteSmooth = vs.floatValue();
        }

        applyOverrideFloat(props, "bloom", "bloomIntensity", "bloom_intensity",
                (on, v) -> { if (on != null) s.bloom = on; if (v != null) { s.bloom = true; s.bloomIntensity = v; } });
        Double th = number(props, "bloomThreshold");
        if (th == null) th = number(props, "bloom_threshold");
        if (th != null) {
            s.bloom = true;
            s.bloomThreshold = th.floatValue();
        }

        applyOverrideFloat(props, "chromatic", "chromaticAmount", "chromatic_amount",
                (on, v) -> { if (on != null) s.chromatic = on; if (v != null) { s.chromatic = true; s.chromaticAmount = v; } });
        applyOverrideFloat(props, "grain", "grainAmount", "grain_amount",
                (on, v) -> { if (on != null) s.grain = on; if (v != null) { s.grain = true; s.grainAmount = v; } });
        applyOverrideFloat(props, "sharpen", "sharpenAmount", "sharpen_amount",
                (on, v) -> { if (on != null) s.sharpen = on; if (v != null) { s.sharpen = true; s.sharpenAmount = v; } });

        if (hasProp(props, "showPost", "show_post")) node.showPost = boolVal(props, "showPost", "show_post");
    }

    @FunctionalInterface
    private interface FloatSink {
        void accept(float v);
    }

    @FunctionalInterface
    private interface OverrideSink {
        void accept(Boolean on, Float value);
    }

    private static void applyGradeFloat(PostProcessSettings s, Map<String, List<Object>> props, String key, FloatSink sink) {
        Double n = number(props, key);
        if (n == null) return;
        s.grade = true;
        sink.accept(n.floatValue());
    }

    private static void applyOverrideFloat(Map<String, List<Object>> props, String flagKey, String altA, String altB,
                                           OverrideSink sink) {
        Object v = first(props, flagKey);
        if (v == null) v = first(props, altA);
        if (v == null) v = first(props, altB);
        if (v == null) return;
        if (v instanceof Number n) sink.accept(null, n.floatValue());
        else sink.accept(isTrue(v), null);
    }

    private static boolean hasProp(Map<String, List<Object>> props, String... keys) {
        for (String k : keys) {
            if (props.containsKey(k)) return true;
        }
        return false;
    }

    private static boolean boolVal(Map<String, List<Object>> props, String... keys) {
        for (String k : keys) {
            Object v = first(props, k);
            if (v != null) return isTrue(v);
        }
        return false;
    }

    private static boolean isTrue(Object v) {
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static void applyCustomProps(SceneNode node, Map<String, List<Object>> props) {
        if (node.customData == null) node.customData = new java.util.LinkedHashMap<>();
        if (props == null) return;
        for (var e : props.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            String k = key.toLowerCase(java.util.Locale.ROOT);
            if (k.equals("parent") || k.equals("scene") || k.equals("name")
                    || k.equals("id") || k.equals("nodeid") || k.equals("node_id")
                    || k.equals("x") || k.equals("y") || k.equals("z")
                    || k.equals("pos") || k.equals("rotate") || k.equals("tags")) {
                continue;
            }
            Object value = first(props, key);
            if (value == null) continue;
            node.customData.put(key, normalizeCustomValue(value));
        }
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeCustomValue(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (var e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), normalizeCustomValue(e.getValue()));
            }
            return out;
        }
        if (value instanceof java.util.List<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Object item : list) out.add(normalizeCustomValue(item));
            return out;
        }
        return value;
    }

    private static String displayName(String scriptId, Map<String, List<Object>> props) {
        if (props.containsKey("name")) return String.valueOf(props.get("name").get(0));
        return scriptId;
    }

    /**
     * Optional {@code id} / {@code nodeId} inside {@code create … { }} becomes the scene node id.
     * Script variable name ({@code create hitbox zone}) stays separate unless id is omitted.
     */
    private static String preferredNodeId(Map<String, List<Object>> props, String scriptId) {
        Object explicit = first(props, "id");
        if (explicit == null) explicit = first(props, "nodeId");
        if (explicit == null) explicit = first(props, "node_id");
        if (!isBlank(explicit)) return String.valueOf(explicit).trim();
        return scriptId;
    }

    private static void bindScriptAliases(Map<String, Object> variables, String scriptId,
                                          SceneNode node, SceneNodeView view) {
        if (variables == null || view == null) return;
        if (scriptId != null && !scriptId.isBlank()) {
            variables.put(scriptId, view);
        }
        if (node != null && node.id != null && !node.id.isBlank()
                && (scriptId == null || !node.id.equals(scriptId))) {
            variables.putIfAbsent(node.id, view);
        }
        if (node != null && node.name != null && !node.name.isBlank()
                && !node.name.equals(scriptId) && !node.name.equals(node.id)) {
            variables.putIfAbsent(node.name, view);
        }
    }

    private static String uniqueId(SceneDocument doc, String preferred) {
        String base = preferred == null || preferred.isBlank() ? UUID.randomUUID().toString() : preferred.trim();
        if (doc.findNode(base) == null) return base;
        for (int i = 2; i < 10_000; i++) {
            String candidate = base + "_" + i;
            if (doc.findNode(candidate) == null) return candidate;
        }
        return base + "_" + UUID.randomUUID();
    }

    private static boolean isBlank(Object value) {
        if (value == null) return true;
        String key = String.valueOf(value).trim();
        return key.isEmpty() || "null".equalsIgnoreCase(key);
    }

    private static Object first(Map<String, List<Object>> props, String key) {
        if (props == null || !props.containsKey(key)) return null;
        List<Object> values = props.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static Double number(Map<String, List<Object>> props, String key) {
        Object v = first(props, key);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }
}
