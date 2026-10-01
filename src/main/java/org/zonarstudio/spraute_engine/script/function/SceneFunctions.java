package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeTags;
import org.zonarstudio.spraute_engine.scene.SceneNodeView;
import org.zonarstudio.spraute_engine.scene.ScenePathResolver;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.ArrayList;
import java.util.List;

/** Script access to scenes, hierarchy nodes and runtime objects. */
public final class SceneFunctions {
    private SceneFunctions() {}

    private static String sceneId(List<Object> args) {
        return args.isEmpty() || args.get(0) == null ? null : String.valueOf(args.get(0)).trim();
    }

    private static String path(List<Object> args, int index) {
        if (args.size() <= index || args.get(index) == null) return null;
        return ScenePathResolver.normalizePath(String.valueOf(args.get(index)));
    }

    private static SceneActiveState requireState(String sceneId) {
        if (sceneId == null || sceneId.isEmpty()) return null;
        return SceneRuntime.getActiveState(sceneId);
    }

    /**
     * Scene for lookup APIs: {@code fn(query)} uses the script's current scene;
     * {@code fn(sceneId, query)} keeps the old explicit form.
     */
    private static SceneActiveState resolveLookupState(List<Object> args) {
        if (args != null && args.size() >= 2) {
            return requireState(sceneId(args));
        }
        return ScriptExecutor.currentSceneState();
    }

    private static String lookupQuery(List<Object> args) {
        if (args == null || args.isEmpty()) return null;
        return path(args, args.size() >= 2 ? 1 : 0);
    }

    private static Object lookupRaw(List<Object> args) {
        if (args == null || args.isEmpty()) return null;
        int index = args.size() >= 2 ? 1 : 0;
        return args.size() > index ? args.get(index) : null;
    }

    public static class Load implements ScriptFunction {
        @Override public String getName() { return "sceneLoad"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String id = sceneId(args);
            return id != null && !id.isEmpty() && SceneRuntime.load(id, source);
        }
    }

    public static class Unload implements ScriptFunction {
        @Override public String getName() { return "sceneUnload"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String id = sceneId(args);
            return id != null && !id.isEmpty() && SceneRuntime.unload(id, source);
        }
    }

    public static class IsActive implements ScriptFunction {
        @Override public String getName() { return "sceneIsActive"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String id = sceneId(args);
            return id != null && SceneRuntime.isActive(id);
        }
    }

    public static class ListLoaded implements ScriptFunction {
        @Override public String getName() { return "sceneListLoaded"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new ArrayList<>(SceneRuntime.listLoaded());
        }
    }

    /** sceneGetNode(pathOrId) / sceneGetNode(sceneId, pathOrId) — id or dotted path like area.guard */
    public static class GetNode implements ScriptFunction {
        @Override public String getName() { return "sceneGetNode"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            String path = lookupQuery(args);
            return state == null || path == null || path.isEmpty() ? null : state.nodeView(path);
        }
    }

    /** sceneHasNode(pathOrId) / sceneHasNode(sceneId, pathOrId) */
    public static class HasNode implements ScriptFunction {
        @Override public String getName() { return "sceneHasNode"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            String path = lookupQuery(args);
            return state != null && path != null && !path.isEmpty() && state.nodeView(path) != null;
        }
    }

    /** sceneFindNode(name) / sceneFindNode(sceneId, name) — first node with matching display name */
    public static class FindNode implements ScriptFunction {
        @Override public String getName() { return "sceneFindNode"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            Object raw = lookupRaw(args);
            if (state == null || raw == null) return null;
            SceneNode found = ScenePathResolver.findByName(state.document.root, String.valueOf(raw));
            return found == null ? null : state.nodeView(found.id);
        }
    }

    /** sceneFindNodeById(id) / sceneFindNodeById(sceneId, id) — exact node id only */
    public static class FindNodeById implements ScriptFunction {
        @Override public String getName() { return "sceneFindNodeById"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            String id = lookupQuery(args);
            if (state == null || id == null || id.isEmpty()) return null;
            SceneNode found = state.document.findNode(id);
            return found == null ? null : state.nodeView(found.id);
        }
    }

    /** sceneListNodes() / sceneListNodes(sceneId) — flat list of all node views */
    public static class ListNodes implements ScriptFunction {
        @Override public String getName() { return "sceneListNodes"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = args != null && !args.isEmpty()
                    ? requireState(sceneId(args))
                    : ScriptExecutor.currentSceneState();
            return state == null ? List.of() : state.listNodeViews();
        }
    }

    /** sceneChildren(sceneId, pathOrNode) */
    public static class Children implements ScriptFunction {
        @Override public String getName() { return "sceneChildren"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneNodeView node = resolveNodeArg(args, source);
            return node == null ? List.of() : node.invokeMethod("children", List.of(), source);
        }
    }

    /** sceneParent(sceneId, pathOrNode) */
    public static class Parent implements ScriptFunction {
        @Override public String getName() { return "sceneParent"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneNodeView node = resolveNodeArg(args, source);
            return node == null ? null : node.invokeMethod("parent", List.of(), source);
        }
    }

    /** sceneNodeObject(sceneId, path) — spawned entity for NPC/MODEL node */
    public static class NodeObject implements ScriptFunction {
        @Override public String getName() { return "sceneNodeObject"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneNodeView node = resolveNodeArg(args, source);
            return node == null ? null : node.invokeMethod("object", List.of(), source);
        }
    }

    /** sceneView(sceneId) — script wrapper around loaded scene */
    public static class View implements ScriptFunction {
        @Override public String getName() { return "sceneView"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = requireState(sceneId(args));
            return state == null ? null : state.sceneView();
        }
    }

    /** sceneFindByTag(tag) / sceneFindByTag(sceneId, tag) — first node in scene with tag */
    public static class FindByTag implements ScriptFunction {
        @Override public String getName() { return "sceneFindByTag"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            Object raw = lookupRaw(args);
            if (state == null || raw == null) return null;
            SceneNode found = SceneNodeTags.findByTag(state.document.root, String.valueOf(raw));
            return found == null ? null : state.nodeView(found.id);
        }
    }

    /** sceneNodesWithTag(tag) / sceneNodesWithTag(sceneId, tag) — all nodes in scene with tag */
    public static class NodesWithTag implements ScriptFunction {
        @Override public String getName() { return "sceneNodesWithTag"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            SceneActiveState state = resolveLookupState(args);
            Object raw = lookupRaw(args);
            if (state == null || raw == null) return List.of();
            List<SceneNodeView> out = new ArrayList<>();
            for (SceneNode found : SceneNodeTags.findAllByTag(state.document.root, String.valueOf(raw))) {
                out.add(state.nodeView(found.id));
            }
            return out;
        }
    }

    /** sceneNodeForEntity(entity) — scene node view for a spawned NPC/model, or null. */
    public static class NodeForEntity implements ScriptFunction {
        @Override public String getName() { return "sceneNodeForEntity"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || !(args.get(0) instanceof Entity entity)) return null;
            return SceneRuntime.nodeViewFromEntity(entity);
        }
    }

    /**
     * sceneResolveNode(ref) / sceneResolveNode(sceneId, ref) —
     * id, dotted path, display name, entity, or node view across loaded scenes.
     */
    public static class ResolveNode implements ScriptFunction {
        @Override public String getName() { return "sceneResolveNode"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args == null || args.isEmpty()) return null;
            if (args.size() >= 2) {
                SceneActiveState state = requireState(sceneId(args));
                Object raw = args.get(1);
                return state == null ? null : resolveNodeInState(state, raw, source);
            }
            return resolveNodeAny(args.get(0), source);
        }
    }

    /**
     * sceneFindCustomUnder(parentRef, typeId) / sceneFindCustomUnder(sceneId, parentRef, typeId) —
     * first CUSTOM node with {@code customTypeId} under {@code parentRef}.
     */
    public static class FindCustomUnder implements ScriptFunction {
        @Override public String getName() { return "sceneFindCustomUnder"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, String.class, Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args == null || args.size() < 2) return null;
            SceneNodeView parent;
            String typeId;
            if (args.size() >= 3) {
                SceneActiveState state = requireState(sceneId(args));
                if (state == null) return null;
                parent = resolveNodeInState(state, args.get(1), source);
                typeId = String.valueOf(args.get(2)).trim();
            } else {
                parent = resolveNodeAny(args.get(0), source);
                typeId = String.valueOf(args.get(1)).trim();
            }
            if (parent == null || typeId.isEmpty()) return null;
            SceneNode root = parent.node();
            if (root == null) return null;
            SceneActiveState st = SceneRuntime.getActiveState(parent.getSceneId());
            if (st == null) return null;
            SceneNode found = ScenePathResolver.findCustomInSubtree(root, typeId);
            return found == null ? null : st.nodeView(found.id);
        }
    }

    private static SceneNodeView resolveNodeAny(Object ref, CommandSourceStack source) {
        if (ref == null) return null;
        if (ref instanceof SceneNodeView view) return view;
        if (ref instanceof Entity entity) {
            return SceneRuntime.nodeViewFromEntity(entity);
        }
        String key = String.valueOf(ref).trim();
        if (key.isEmpty()) return null;
        for (String sid : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sid);
            SceneNodeView view = resolveNodeInState(state, key, source);
            if (view != null) return view;
        }
        return null;
    }

    private static SceneNodeView resolveNodeInState(SceneActiveState state, Object ref, CommandSourceStack source) {
        if (state == null || ref == null) return null;
        if (ref instanceof SceneNodeView view) {
            return view.getSceneId().equals(state.sceneId) ? view : state.nodeView(view.getNodeId());
        }
        if (ref instanceof Entity entity) {
            String nodeId = findNodeIdForEntity(state, entity);
            return nodeId == null ? null : state.nodeView(nodeId);
        }
        String key = String.valueOf(ref).trim();
        if (key.isEmpty()) return null;
        SceneNode found = ScenePathResolver.findNode(state.document, key);
        if (found != null) return state.nodeView(found.id);
        if (source != null && source.getLevel() != null) {
            Entity e = NpcManager.getEntity(key, source.getLevel());
            if (e != null) {
                String nodeId = state.findNodeIdByEntity(e);
                if (nodeId != null) return state.nodeView(nodeId);
            }
        }
        return null;
    }

    /** prefabSpawn(sceneId, prefabName, x, y, z) — spawns a prefab into an active scene, returns node view. */
    public static class PrefabSpawn implements ScriptFunction {
        @Override public String getName() { return "prefabSpawn"; }
        @Override public int getArgCount() { return 5; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, String.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String scene = sceneId(args);
            if (scene == null || scene.isEmpty() || args.size() < 5) return null;
            String prefab = args.get(1) == null ? null : String.valueOf(args.get(1)).trim();
            if (prefab == null || prefab.isEmpty()) return null;
            double x = ((Number) args.get(2)).doubleValue();
            double y = ((Number) args.get(3)).doubleValue();
            double z = ((Number) args.get(4)).doubleValue();
            return SceneRuntime.spawnPrefab(scene, prefab, x, y, z, source);
        }
    }

    /** prefabSpawnIn(sceneId, prefabName, parentNodeOrPath, x, y, z) — spawns a prefab as a child of another node. */
    public static class PrefabSpawnIn implements ScriptFunction {
        @Override public String getName() { return "prefabSpawnIn"; }
        @Override public int getArgCount() { return 6; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, String.class, Object.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String scene = sceneId(args);
            if (scene == null || scene.isEmpty() || args.size() < 6) return null;
            String prefab = args.get(1) == null ? null : String.valueOf(args.get(1)).trim();
            if (prefab == null || prefab.isEmpty()) return null;
            Object parentArg = args.get(2);
            String parentId;
            if (parentArg instanceof SceneNodeView view) {
                parentId = view.getNodeId();
            } else {
                SceneActiveState state = requireState(scene);
                if (state == null) return null;
                SceneNodeView parent = state.nodeView(ScenePathResolver.normalizePath(String.valueOf(parentArg)));
                if (parent == null) return null;
                parentId = parent.getNodeId();
            }
            double x = ((Number) args.get(3)).doubleValue();
            double y = ((Number) args.get(4)).doubleValue();
            double z = ((Number) args.get(5)).doubleValue();
            return SceneRuntime.spawnPrefab(scene, prefab, parentId, x, y, z, source);
        }
    }

    /** prefabList() — names of all saved prefabs. */
    public static class PrefabList implements ScriptFunction {
        @Override public String getName() { return "prefabList"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new ArrayList<>(org.zonarstudio.spraute_engine.scene.PrefabRepository
                    .listIds(org.zonarstudio.spraute_engine.resource.SprautePaths.gameDir()));
        }
    }

    private static SceneNodeView resolveNodeArg(List<Object> args, CommandSourceStack source) {
        if (args.isEmpty()) return null;
        SceneActiveState state = requireState(sceneId(args));
        if (state == null) return null;
        if (args.size() < 2) return null;
        Object second = args.get(1);
        if (second instanceof SceneNodeView view) return view;
        String path = ScenePathResolver.normalizePath(String.valueOf(second));
        return path.isEmpty() ? null : state.nodeView(path);
    }

    /** Runtime map first, then {@code spraute_scene_* / spraute_scene_node_*} entity tags. */
    private static String findNodeIdForEntity(SceneActiveState state, Entity entity) {
        if (state == null || entity == null) return null;
        String nodeId = state.findNodeIdByEntity(entity);
        if (nodeId != null) return nodeId;
        String taggedScene = SceneRuntime.sceneIdFromEntity(entity);
        if (taggedScene != null && !taggedScene.equals(state.sceneId)) return null;
        return SceneRuntime.nodeIdFromEntityTags(entity);
    }
}
