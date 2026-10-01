package org.zonarstudio.spraute_engine.scene;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allocates human-readable node ids within one scene: {@code npc1}, {@code npc2}, …
 */
public final class SceneNodeIds {
    private SceneNodeIds() {}

    public static String prefix(SceneNodeType type) {
        if (type == null) return "node";
        return switch (type) {
            case SCENE -> "scene";
            case PLAYERS -> "players";
            case GLOBAL_WORLD -> "gworld";
            case LOCAL_WORLD -> "lworld";
            case PLAYER -> "player";
            case CAMERA -> "camera";
            case AUDIO -> "paudio";
            case SKIN_OVERLAY -> "overlay";
            case EMPTY -> "node";
            case NPC -> "npc";
            case MODEL -> "model";
            case BILLBOARD -> "billboard";
            case HITBOX -> "hitbox";
            case PARTICLE -> "particle";
            case LIGHT -> "light";
            case AUDIO_SOURCE -> "audio";
            case POST_PROCESS -> "post";
            case CUSTOM -> "custom";
            case SCRIPT -> "script";
            case VISUAL_SCRIPT -> "vscript";
            case ANIMATOR -> "animator";
            case ROUTE -> "route";
            case ROUTE_POINT -> "point";
            case CANVAS -> "canvas";
            case UI_GRID -> "grid";
            case UI_TEXT -> "text";
            case UI_BUTTON -> "button";
            case UI_IMAGE -> "image";
            case UI_VIDEO -> "video";
            case UI_SVG -> "svg";
            case UI_RECT -> "panel";
            case UI_INPUT -> "input";
            case UI_SCROLL -> "scroll";
            case UI_DIVIDER -> "divider";
            case UI_ITEM -> "uiitem";
            case UI_BLOCK -> "uiblock";
            case UI_ENTITY -> "render";
            case UI_CROP -> "crop";
            case UI_GROUP -> "group";
            case UI_DRAG -> "drag";
            case UI_DROPTARGET -> "droptarget";
            case UI_SLOT -> "slot";
            case UI_INVENTORY -> "inventory";
            case RES_BLOCK -> "block";
            case RES_ITEM -> "item";
            case RES_CRAFT -> "craft";
            case RES_TAB -> "tab";
            case RES_DROP -> "drop";
            case RES_WORLD -> "world";
            case RES_PARTICLE -> "particle";
            case RES_PROJECTILE -> "proj";
        };
    }

    /** Letters, digits and underscores from a player name / hint. */
    public static String slug(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') out.append(c);
            else if (out.length() > 0 && out.charAt(out.length() - 1) != '_') out.append('_');
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == '_') start++;
        while (end > start && out.charAt(end - 1) == '_') end--;
        return start == 0 && end == out.length() ? out.toString() : out.substring(start, end);
    }

    /** Unique id: {@code preferred}, or {@code preferred_2}, … */
    public static String unique(SceneDocument doc, String preferred) {
        String base = preferred == null || preferred.isBlank() ? "node" : preferred.trim();
        Set<String> used = collectIds(doc);
        if (!used.contains(base)) return base;
        for (int i = 2; i < 100_000; i++) {
            String candidate = base + "_" + i;
            if (!used.contains(candidate)) return candidate;
        }
        return base + "_" + System.nanoTime();
    }

    /**
     * Live PLAYER / CAMERA / AUDIO / POST id, same idea as a regular node:
     * {@code player_Steve}, {@code camera_Steve}, or sequential {@code player1} when nameless.
     */
    public static String allocateLive(SceneDocument doc, SceneNodeType type, String playerName) {
        String slug = slug(playerName);
        if (!slug.isEmpty()) return unique(doc, prefix(type) + "_" + slug);
        return allocate(doc, type);
    }

    /** Next free {@code prefixN} in the document (fills gaps). */
    public static String allocate(SceneDocument doc, SceneNodeType type) {
        String p = prefix(type);
        Set<String> used = collectIds(doc);
        Pattern pat = Pattern.compile("^" + Pattern.quote(p) + "(\\d+)$");
        Set<Integer> taken = new HashSet<>();
        for (String id : used) {
            Matcher m = pat.matcher(id);
            if (m.matches()) {
                try {
                    taken.add(Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {}
            }
        }
        for (int i = 1; i < 100_000; i++) {
            if (!taken.contains(i)) {
                String candidate = p + i;
                if (!used.contains(candidate)) return candidate;
            }
        }
        return p + System.nanoTime();
    }

    /**
     * Assigns fresh sequential ids to {@code root} and its whole subtree.
     * Call after {@link SceneNode#deepCopyWithNewIds()} before attaching to the document.
     */
    public static void assignTree(SceneDocument doc, SceneNode root) {
        if (doc == null || root == null) return;
        assignTreeIn(doc, root);
    }

    private static void assignTreeIn(SceneDocument doc, SceneNode node) {
        if (node.type != SceneNodeType.SCENE) {
            node.id = allocate(doc, node.type);
            // Temporarily register so siblings get unique ids; doc.findNode won't see
            // us until attached, so we mark via a side set by walking used ids + pending.
            // allocate() only sees doc — so we temporarily add as orphan under a fake walk.
            // Simpler: stash on a thread-local set consulted by collectIds.
            PENDING_IDS.get().add(node.id);
            syncResourceDefId(node);
        }
        for (SceneNode child : node.children) {
            assignTreeIn(doc, child);
        }
    }

    private static final ThreadLocal<Set<String>> PENDING_IDS =
            ThreadLocal.withInitial(HashSet::new);

    /**
     * Assigns ids to any nodes that still have a blank id (e.g. older scenes or
     * editor bugs that attached nodes without {@link #assignTree}).
     * @return number of nodes that received a new id
     */
    public static int ensureAllIds(SceneDocument doc) {
        if (doc == null || doc.root == null) return 0;
        int[] fixed = {0};
        try {
            doc.walk((n, ignored) -> {
                if (n.type == SceneNodeType.SCENE) return;
                if (n.id != null && !n.id.isBlank()) {
                    PENDING_IDS.get().add(n.id);
                    return;
                }
                n.id = allocate(doc, n.type);
                PENDING_IDS.get().add(n.id);
                syncResourceDefId(n);
                fixed[0]++;
            });
        } finally {
            clearPending();
        }
        return fixed[0];
    }

    public static void clearPending() {
        PENDING_IDS.get().clear();
    }

    private static Set<String> collectIds(SceneDocument doc) {
        Set<String> ids = new HashSet<>(PENDING_IDS.get());
        if (doc != null && doc.root != null) {
            doc.walk((n, t) -> {
                if (n.id != null && !n.id.isBlank()) ids.add(n.id);
            });
        }
        return ids;
    }

    public static void syncResourceDefId(SceneNode node) {
        if (node == null || node.id == null) return;
        if (node.blockDef != null) node.blockDef.id = node.id;
        if (node.itemDef != null) node.itemDef.id = node.id;
        if (node.craftDef != null) node.craftDef.id = node.id;
        if (node.tabDef != null) node.tabDef.id = node.id;
        if (node.dropDef != null) node.dropDef.id = node.id;
        if (node.worldDef != null) node.worldDef.id = node.id;
        if (node.particleDef != null) node.particleDef.id = node.id;
        if (node.projectileDef != null) node.projectileDef.id = node.id;
        if (node.type == SceneNodeType.ROUTE
                && (node.routeFile == null || node.routeFile.isBlank())) {
            node.routeFile = "routes/" + node.id + SceneRouteStorage.EXTENSION;
        }
    }
}
