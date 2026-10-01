package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.util.ArrayList;
import java.util.List;

/**
 * Box (marquee) multi-select for the world viewport in {@link EditorGizmo.Mode#SELECT}.
 * Drag on empty space draws a rectangle; nodes whose screen bounds intersect it are selected.
 * Does not start fly-camera capture — use F / other tools for that.
 */
public final class EditorMarqueeSelect {
    private static final float CLICK_SLOP_PX = 4f;

    private static boolean active;
    private static boolean additive;
    private static double startGuiX, startGuiY;
    private static double curGuiX, curGuiY;

    private EditorMarqueeSelect() {}

    public static boolean isActive() {
        return active;
    }

    /** Start a marquee from an empty-viewport press in Select mode. */
    public static boolean begin(double guiMouseX, double guiMouseY, boolean additiveSelect) {
        if (EditorGizmo.mode() != EditorGizmo.Mode.SELECT) return false;
        if (!EditorGizmo.containsViewport(guiMouseX, guiMouseY)) return false;
        active = true;
        additive = additiveSelect;
        startGuiX = curGuiX = guiMouseX;
        startGuiY = curGuiY = guiMouseY;
        if (!additive) {
            SceneEditorSession.clearSelection();
            AnimatorTimelineSession.selectBone(null);
        }
        return true;
    }

    /** Update while LMB is held; commit on release. */
    public static void update(double guiMouseX, double guiMouseY) {
        if (!active) return;
        curGuiX = guiMouseX;
        curGuiY = guiMouseY;
        if (!ImGui.isMouseDown(0)) {
            commit();
            active = false;
        }
    }

    public static void cancel() {
        active = false;
    }

    /**
     * Draw the selection rectangle.
     * {@code guiScaleX/Y} = guiScaledSize / framebufferSize (GUI → ImGui screen).
     */
    public static void draw(float guiScaleX, float guiScaleY) {
        if (!active || !isDragPastSlop()) return;
        float sx = Math.max(1e-6f, guiScaleX);
        float sy = Math.max(1e-6f, guiScaleY);
        float x0 = (float) Math.min(startGuiX, curGuiX) / sx;
        float y0 = (float) Math.min(startGuiY, curGuiY) / sy;
        float x1 = (float) Math.max(startGuiX, curGuiX) / sx;
        float y1 = (float) Math.max(startGuiY, curGuiY) / sy;
        ImDrawList dl = ImGui.getForegroundDrawList();
        dl.addRectFilled(x0, y0, x1, y1, ImGui.getColorU32(0.35f, 0.65f, 1f, 0.14f));
        dl.addRect(x0, y0, x1, y1, ImGui.getColorU32(0.45f, 0.78f, 1f, 0.95f), 0f, 0, 1.5f);
    }

    private static void commit() {
        if (!isDragPastSlop()) {
            // Plain click on empty space: selection already cleared in begin() when !additive.
            return;
        }
        double minX = Math.min(startGuiX, curGuiX);
        double minY = Math.min(startGuiY, curGuiY);
        double maxX = Math.max(startGuiX, curGuiX);
        double maxY = Math.max(startGuiY, curGuiY);
        List<String> hit = collectNodesInRect(minX, minY, maxX, maxY);
        SceneEditorSession.selectIds(hit, additive);
        AnimatorTimelineSession.selectBone(null);
    }

    private static boolean isDragPastSlop() {
        double dx = curGuiX - startGuiX;
        double dy = curGuiY - startGuiY;
        return dx * dx + dy * dy >= CLICK_SLOP_PX * CLICK_SLOP_PX;
    }

    private static List<String> collectNodesInRect(double minX, double minY, double maxX, double maxY) {
        List<String> out = new ArrayList<>();
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return out;
        doc.walk((node, parentWorld) -> {
            if (!isMarqueeSelectable(node.type) || !node.previewEnabled) return;
            if (doc.isEffectivelyLocked(node)) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            AABB box = EditorGizmo.nodeBounds(node, world);
            if (screenBoundsIntersectRect(box, minX, minY, maxX, maxY)) {
                out.add(node.id);
            }
        });
        return out;
    }

    private static boolean isMarqueeSelectable(SceneNodeType type) {
        return type == SceneNodeType.NPC
                || type == SceneNodeType.MODEL
                || type == SceneNodeType.HITBOX
                || type == SceneNodeType.PARTICLE
                || type == SceneNodeType.LIGHT
                || type == SceneNodeType.AUDIO_SOURCE
                || type == SceneNodeType.BILLBOARD;
    }

    /** True when the node's projected AABB overlaps the GUI-space marquee. */
    private static boolean screenBoundsIntersectRect(AABB box, double minX, double minY,
                                                     double maxX, double maxY) {
        double sx0 = Double.POSITIVE_INFINITY;
        double sy0 = Double.POSITIVE_INFINITY;
        double sx1 = Double.NEGATIVE_INFINITY;
        double sy1 = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            double[] p = EditorGizmo.projectWorldToGui(new Vec3(x, y, z));
            if (p == null) continue;
            any = true;
            sx0 = Math.min(sx0, p[0]);
            sy0 = Math.min(sy0, p[1]);
            sx1 = Math.max(sx1, p[0]);
            sy1 = Math.max(sy1, p[1]);
        }
        if (!any) {
            double[] c = EditorGizmo.projectWorldToGui(new Vec3(
                    (box.minX + box.maxX) * 0.5,
                    (box.minY + box.maxY) * 0.5,
                    (box.minZ + box.maxZ) * 0.5));
            if (c == null) return false;
            return c[0] >= minX && c[0] <= maxX && c[1] >= minY && c[1] <= maxY;
        }
        return sx0 <= maxX && sx1 >= minX && sy0 <= maxY && sy1 >= minY;
    }
}
