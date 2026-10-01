package org.zonarstudio.spraute_engine.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** In-memory model of one {@code .scene.json} file. */
public final class SceneDocument {
    /**
     * Format 4 introduces GLOBAL_WORLD/LOCAL_WORLD as the only valid scene-root children
     * (besides PLAYERS) — see {@link SceneRepository}'s one-time migration for pre-4 scenes.
     *
     * <p>Format 5 folds the old {@code worldCanvas} CUSTOM node (a data node wrapping a child
     * CANVAS) into the CANVAS node itself: a canvas now has a render mode, and a world-space one
     * holds its widgets directly — again, see {@link SceneRepository}'s migration.
     */
    public static final int CURRENT_FORMAT = 5;

    public int format = CURRENT_FORMAT;
    /**
     * Set on the document owned by a running scene ({@link SceneActiveState}); never serialized.
     * Makes {@link #walkActive} skip the LOCAL_WORLD template (players get private clones).
     */
    public transient boolean liveRuntime;
    public String id = "";
    /**
     * Legacy field kept for old {@code .scene.json} files. Scenes are no longer bound to a
     * dimension — they load in the world where {@code /spraute run} (or auto-start) is executed.
     */
    public String dimension = "";
    /** Load this scene automatically after the integrated/dedicated server starts. */
    public boolean autoStart;
    public SceneNode root = SceneNode.sceneRoot("Scene");

    public static SceneDocument create(String id) {
        SceneDocument doc = new SceneDocument();
        doc.id = id;
        doc.root = SceneNode.sceneRoot(id);
        SceneNode.hardenChildrenLists(doc.root);
        doc.ensurePlayersNode();
        doc.ensureGlobalWorldNode();
        doc.ensureLocalWorldNode();
        return doc;
    }

    /** Returns the immutable system container, creating it for legacy scenes when needed. */
    public SceneNode ensurePlayersNode() {
        if (root == null) root = SceneNode.sceneRoot(id);
        for (SceneNode child : root.children) {
            if (child.type == SceneNodeType.PLAYERS) {
                child.id = "players";
                child.name = "Players";
                child.locked = true;
                SceneNode.ensurePlayerVarMaps(child);
                child.children.removeIf(n -> n.type != SceneNodeType.PLAYER);
                return child;
            }
        }
        SceneNode players = SceneNode.playersRoot();
        root.children.add(0, players);
        return players;
    }

    /** Returns the singleton "Глобальный мир" container, creating it if missing. */
    public SceneNode ensureGlobalWorldNode() {
        if (root == null) root = SceneNode.sceneRoot(id);
        for (SceneNode child : root.children) {
            if (child.type == SceneNodeType.GLOBAL_WORLD) {
                child.id = "global_world";
                child.name = "Глобальный мир";
                child.locked = true;
                return child;
            }
        }
        SceneNode global = SceneNode.globalWorldRoot();
        root.children.add(global);
        return global;
    }

    /** Returns the singleton "Локальный мир" container (per-player template), creating it if missing. */
    public SceneNode ensureLocalWorldNode() {
        if (root == null) root = SceneNode.sceneRoot(id);
        for (SceneNode child : root.children) {
            if (child.type == SceneNodeType.LOCAL_WORLD) {
                child.id = "local_world";
                child.name = "Локальный мир";
                child.locked = true;
                return child;
            }
        }
        SceneNode local = SceneNode.localWorldRoot();
        root.children.add(local);
        return local;
    }

    public SceneNode findNode(String nodeId) {
        return findIn(root, nodeId);
    }

    private static SceneNode findIn(SceneNode node, String nodeId) {
        if (node.id.equals(nodeId)) return node;
        for (SceneNode child : node.children) {
            SceneNode found = findIn(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    public SceneNode findParent(String nodeId) {
        return findParentIn(root, nodeId);
    }

    private static SceneNode findParentIn(SceneNode node, String nodeId) {
        for (SceneNode child : node.children) {
            if (child.id.equals(nodeId)) return node;
            SceneNode found = findParentIn(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    /** True if {@code ancestorId} is the node itself or one of its ancestors contains {@code nodeId}. */
    public boolean isSameOrDescendant(String ancestorId, String nodeId) {
        SceneNode ancestor = findNode(ancestorId);
        return ancestor != null && findIn(ancestor, nodeId) != null;
    }

    /**
     * Locked for viewport pick/edit if the node or any ancestor has {@link SceneNode#locked}.
     * Hierarchy selection still works.
     *
     * <p>The GLOBAL_WORLD / LOCAL_WORLD / PLAYERS containers are permanently {@code locked} in
     * their factories ({@link SceneNode#globalWorldRoot}, {@link SceneNode#localWorldRoot},
     * {@link SceneNode#playersRoot}) purely so the root container itself can't be dragged or
     * deleted as an object — that flag was never meant to cascade down and lock every single
     * piece of world content underneath it. Their own lock is skipped in this walk; a lock set on
     * anything else (an actual node the user locked, or a live PLAYER instance) still cascades
     * normally to its descendants.
     */
    public boolean isEffectivelyLocked(String nodeId) {
        SceneNode node = findNode(nodeId);
        while (node != null) {
            boolean structuralRoot = node.type == SceneNodeType.GLOBAL_WORLD
                    || node.type == SceneNodeType.LOCAL_WORLD
                    || node.type == SceneNodeType.PLAYERS;
            if (node.locked && !structuralRoot) return true;
            node = findParent(node.id);
        }
        return false;
    }

    public boolean isEffectivelyLocked(SceneNode node) {
        return node != null && isEffectivelyLocked(node.id);
    }

    /** World transform of a node (identity for the scene root). */
    public SceneTransform worldTransform(String nodeId) {
        SceneNode target = findNode(nodeId);
        if (target != null && target.type == SceneNodeType.CAMERA) {
            return PlayerCameraPose.worldTransform(this, target);
        }
        List<SceneNode> chain = new ArrayList<>();
        if (!buildChain(root, nodeId, chain)) return null;
        SceneTransform world = null;
        SceneNode parent = null;
        for (SceneNode node : chain) {
            if (parent != null && parent.type == SceneNodeType.ROUTE
                    && SceneRouteLayout.isLayoutChild(node)) {
                world = SceneRouteLayout.slotTransform(this, parent, node);
            }
            if (node.hasTransform()) {
                world = SceneTransform.composeWorld(world, node.transform);
            }
            parent = node;
        }
        return world == null ? new SceneTransform() : world;
    }

    /** Effective world transform inherited by a child, including a ROUTE layout slot. */
    public SceneTransform parentWorldTransform(SceneNode child) {
        if (child == null) return null;
        SceneNode parent = findParent(child.id);
        if (parent == null || parent.type == SceneNodeType.SCENE) return null;
        if (parent.type == SceneNodeType.ROUTE && SceneRouteLayout.isLayoutChild(child)) {
            return SceneRouteLayout.slotTransform(this, parent, child);
        }
        return worldTransform(parent.id);
    }

    private static boolean buildChain(SceneNode node, String nodeId, List<SceneNode> chain) {
        chain.add(node);
        if (node.id.equals(nodeId)) return true;
        for (SceneNode child : node.children) {
            if (buildChain(child, nodeId, chain)) return true;
        }
        chain.remove(chain.size() - 1);
        return false;
    }

    /** Visits every node with its parent world transform (null for direct children of the root). */
    public void walk(BiConsumer<SceneNode, SceneTransform> visitor) {
        walkIn(root, null, visitor);
    }

    /** Like {@link #walk}, but skips inactive nodes together with their whole subtree. */
    public void walkActive(BiConsumer<SceneNode, SceneTransform> visitor) {
        walkActiveIn(root, null, visitor);
    }

    private void walkActiveIn(SceneNode node, SceneTransform parentWorld,
                              BiConsumer<SceneNode, SceneTransform> visitor) {
        if (!node.active) return;
        // In a running scene the LOCAL_WORLD subtree is only a template: every player gets a
        // private clone under their PLAYER node (LocalWorldInstancing). Walking the template
        // too would spawn/tick a second, shared copy visible to everyone.
        if (liveRuntime && node.type == SceneNodeType.LOCAL_WORLD) return;
        visitor.accept(node, parentWorld);
        SceneTransform world = !node.hasTransform()
                ? parentWorld
                : SceneTransform.composeWorld(parentWorld, node.transform);
        for (SceneNode child : node.children) {
            SceneTransform childParent = node.type == SceneNodeType.ROUTE
                    && SceneRouteLayout.isLayoutChild(child)
                    ? SceneRouteLayout.slotTransform(this, node, child)
                    : world;
            walkActiveIn(child, childParent, visitor);
        }
    }

    private void walkIn(SceneNode node, SceneTransform parentWorld, BiConsumer<SceneNode, SceneTransform> visitor) {
        visitor.accept(node, parentWorld);
        SceneTransform world = !node.hasTransform()
                ? parentWorld
                : SceneTransform.composeWorld(parentWorld, node.transform);
        for (SceneNode child : node.children) {
            SceneTransform childParent = node.type == SceneNodeType.ROUTE
                    && SceneRouteLayout.isLayoutChild(child)
                    ? SceneRouteLayout.slotTransform(this, node, child)
                    : world;
            walkIn(child, childParent, visitor);
        }
    }

    /** Appends a new child under {@code parentId}. Does not rewrite transforms. */
    public boolean addChild(String parentId, SceneNode child) {
        if (child == null || parentId == null) return false;
        SceneNode parent = findNode(parentId);
        if (parent == null) return false;
        if (!parent.type.acceptsChildren() || !parent.type.allowedChildTypes().contains(child.type)) return false;
        // CAMERA under PLAYER replaces the existing player camera (prefab / create parity).
        if (parent.type == SceneNodeType.PLAYER && child.type == SceneNodeType.CAMERA) {
            SceneNode existingCam = PlayerCameraNodes.findCamera(parent);
            SceneNode idOwner = findNode(child.id);
            if (idOwner != null && idOwner != existingCam && idOwner != child) return false;
            PlayerCameraNodes.installCamera(parent, child);
            return true;
        }
        if (findNode(child.id) != null) return false;
        if (parent.type == SceneNodeType.ROUTE && SceneRouteLayout.isLayoutChild(child)) {
            child.transform.px = 0;
            child.transform.py = 0;
            child.transform.pz = 0;
            child.transform.rx = 0;
            child.transform.ry = 0;
            child.transform.rz = 0;
        }
        parent.children.add(child);
        return true;
    }

    /**
     * Moves a node under a new parent, preserving its world transform.
     * Rejects cycles, root moves and capability violations.
     *
     * @return true when the move happened.
     */
    public boolean reparent(String nodeId, String newParentId) {
        SceneNode parent = findNode(newParentId);
        return parent != null && move(nodeId, newParentId, parent.children.size());
    }

    /**
     * Moves/reorders a node at an exact child index. World position is preserved,
     * except when first entering a ROUTE: its local offset is reset so the route
     * can place it into the requested ordered slot.
     */
    public boolean move(String nodeId, String newParentId, int requestedIndex) {
        if (nodeId.equals(newParentId)) return false;
        SceneNode node = findNode(nodeId);
        SceneNode newParent = findNode(newParentId);
        SceneNode oldParent = findParent(nodeId);
        if (node == null || newParent == null || oldParent == null) return false;
        if (!node.type.canReparent()) return false;
        if (!newParent.type.acceptsChildren() || !newParent.type.allowedChildTypes().contains(node.type)) return false;
        if (isSameOrDescendant(nodeId, newParentId)) return false; // cycle

        SceneTransform nodeWorld = worldTransform(nodeId);
        int oldIndex = oldParent.children.indexOf(node);
        int index = Math.max(0, Math.min(requestedIndex, newParent.children.size()));
        if (oldParent == newParent && oldIndex >= 0 && oldIndex < index) index--;
        if (oldParent == newParent && oldIndex == index) return false;
        oldParent.children.remove(node);
        index = Math.max(0, Math.min(index, newParent.children.size()));
        newParent.children.add(index, node);

        boolean enteringRoute = oldParent != newParent && newParent.type == SceneNodeType.ROUTE
                && SceneRouteLayout.isLayoutChild(node);
        if (enteringRoute) {
            node.transform.px = 0;
            node.transform.py = 0;
            node.transform.pz = 0;
            node.transform.rx = 0;
            node.transform.ry = 0;
            node.transform.rz = 0;
        } else if (oldParent != newParent && node.hasTransform()) {
            node.transform = SceneTransform.toLocal(parentWorldTransform(node), nodeWorld);
        }
        return true;
    }

    /** Renames a node id if the new value is unique and valid. */
    public boolean renameNodeId(String nodeId, String newId) {
        if (newId == null || newId.isBlank() || nodeId.equals(newId)) return false;
        SceneNode node = findNode(nodeId);
        if (node == null || node.type == SceneNodeType.SCENE) return false;
        String trimmed = newId.trim();
        if (findNode(trimmed) != null) return false;
        node.id = trimmed;
        SceneNodeIds.syncResourceDefId(node);
        return true;
    }
}
