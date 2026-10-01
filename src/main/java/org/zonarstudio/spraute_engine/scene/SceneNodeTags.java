package org.zonarstudio.spraute_engine.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Tag helpers for scene nodes (editor + runtime scripts). */
public final class SceneNodeTags {
    private SceneNodeTags() {}

    public static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    public static void ensureTags(SceneNode node) {
        if (node.tags == null) node.tags = new ArrayList<>();
    }

    public static List<String> copyTags(SceneNode node) {
        if (node == null || node.tags == null || node.tags.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(node.tags));
    }

    public static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String tag = normalizeTag(part);
            if (!tag.isEmpty() && !out.contains(tag)) out.add(tag);
        }
        return out;
    }

    public static List<String> parseTags(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String tag = normalizeTag(String.valueOf(item));
                if (!tag.isEmpty() && !out.contains(tag)) out.add(tag);
            }
            return out;
        }
        return parseTags(String.valueOf(value));
    }

    public static void setTags(SceneNode node, Object value) {
        ensureTags(node);
        node.tags.clear();
        node.tags.addAll(parseTags(value));
    }

    public static boolean hasTag(SceneNode node, String tag) {
        String wanted = normalizeTag(tag);
        if (wanted.isEmpty() || node == null || node.tags == null) return false;
        for (String existing : node.tags) {
            if (wanted.equals(normalizeTag(existing))) return true;
        }
        return false;
    }

    public static boolean addTag(SceneNode node, String tag) {
        String normalized = normalizeTag(tag);
        if (normalized.isEmpty() || node == null) return false;
        ensureTags(node);
        if (hasTag(node, normalized)) return false;
        node.tags.add(normalized);
        return true;
    }

    public static boolean removeTag(SceneNode node, String tag) {
        String wanted = normalizeTag(tag);
        if (wanted.isEmpty() || node == null || node.tags == null) return false;
        return node.tags.removeIf(existing -> wanted.equals(normalizeTag(existing)));
    }

    public static void clearTags(SceneNode node) {
        if (node == null) return;
        ensureTags(node);
        node.tags.clear();
    }

    public static int tagCount(SceneNode node) {
        return node == null || node.tags == null ? 0 : node.tags.size();
    }

    public static SceneNode findByTag(SceneNode root, String tag) {
        if (root == null) return null;
        String wanted = normalizeTag(tag);
        if (wanted.isEmpty()) return null;
        if (hasTag(root, wanted)) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findByTag(child, wanted);
            if (found != null) return found;
        }
        return null;
    }

    public static List<SceneNode> findAllByTag(SceneNode root, String tag) {
        if (root == null) return List.of();
        String wanted = normalizeTag(tag);
        if (wanted.isEmpty()) return List.of();
        List<SceneNode> out = new ArrayList<>();
        collectByTag(root, wanted, out);
        return out;
    }

    private static void collectByTag(SceneNode node, String wanted, List<SceneNode> out) {
        if (hasTag(node, wanted)) out.add(node);
        for (SceneNode child : node.children) collectByTag(child, wanted, out);
    }
}
