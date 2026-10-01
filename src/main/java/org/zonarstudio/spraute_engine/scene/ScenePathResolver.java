package org.zonarstudio.spraute_engine.scene;

import java.util.Locale;

/** Resolves scene node paths like {@code area.guard} or full ids {@code test.test.test}. */
public final class ScenePathResolver {
    private ScenePathResolver() {}

    public static SceneNode findNode(SceneDocument doc, String pathOrId) {
        if (doc == null || pathOrId == null) return null;
        String path = pathOrId.trim();
        if (path.isEmpty()) return null;

        SceneNode direct = doc.findNode(path);
        if (direct != null) return direct;

        if (!path.contains(".")) {
            return findByName(doc.root, path);
        }

        String[] parts = path.split("\\.");
        SceneNode current = doc.root;
        for (String part : parts) {
            if (part.isEmpty()) return null;
            SceneNode next = findDirectChild(current, part);
            if (next == null) return null;
            current = next;
        }
        return current == doc.root && parts.length > 0 ? null : current;
    }

    public static SceneNode findDirectChild(SceneNode parent, String idOrName) {
        if (parent == null || idOrName == null) return null;
        for (SceneNode child : parent.children) {
            if (idOrName.equals(child.id)) return child;
        }
        for (SceneNode child : parent.children) {
            if (idOrName.equalsIgnoreCase(child.name)) return child;
        }
        return null;
    }

    public static SceneNode findByName(SceneNode root, String name) {
        if (root == null || name == null || name.isEmpty()) return null;
        if (name.equalsIgnoreCase(root.name)) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findByName(child, name);
            if (found != null) return found;
        }
        return null;
    }

    /** First CUSTOM node with {@code customTypeId} in subtree (depth-first). */
    public static SceneNode findCustomInSubtree(SceneNode root, String typeId) {
        if (root == null || typeId == null || typeId.isEmpty()) return null;
        if (root.type == SceneNodeType.CUSTOM && typeId.equals(root.customTypeId)) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findCustomInSubtree(child, typeId);
            if (found != null) return found;
        }
        return null;
    }

    /** True if {@code ancestor} is on the path from {@code node} to the scene root. */
    public static boolean isAncestorOf(SceneNode ancestor, SceneNode node, SceneDocument doc) {
        if (ancestor == null || node == null || doc == null) return false;
        SceneNode cur = doc.findParent(node.id);
        while (cur != null) {
            if (cur.id.equals(ancestor.id)) return true;
            cur = doc.findParent(cur.id);
        }
        return false;
    }

    public static String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }

    public static boolean matchesIdOrName(SceneNode node, String token) {
        if (node == null || token == null) return false;
        return token.equals(node.id) || token.equalsIgnoreCase(node.name == null ? "" : node.name);
    }

    public static String displayToken(SceneNode node) {
        if (node == null) return "";
        if (node.name != null && !node.name.isBlank()) return node.name;
        return node.id;
    }
}
