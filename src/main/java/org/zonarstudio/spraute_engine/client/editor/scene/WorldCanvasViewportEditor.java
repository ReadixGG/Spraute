package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.SprauteScriptScreen;
import org.zonarstudio.spraute_engine.client.WorldCanvasTextureManager;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneTransform;
import org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing a world-space canvas directly in the 3D viewport: the panel's widgets are outlined
 * where they actually are in the world, can be clicked to select, and — like a flat 2D canvas —
 * get the same Unity-style move/rotate/scale gizmo {@link CanvasViewportEditor} draws (Q/W/E/R
 * toolbar tools), just projected onto the panel's plane in the world instead of drawn flat over
 * the screen. The world-space counterpart of {@link CanvasViewportEditor}.
 *
 * <p>Geometry comes from the two things that already define the panel, so the overlay can never
 * drift from what is drawn: {@link WorldCanvasTextureManager#panelAxes} replays the renderer's
 * own orientation, and {@link SprauteScriptScreen#editorPreviewBounds} reports the widget rects
 * the panel texture was laid out with. Gizmo geometry is built in canvas-pixel space (the same
 * space the widgets themselves live in) and projected point-by-point through {@link #toScreen},
 * so it warps correctly with perspective instead of floating as a flat 2D overlay.
 */
public final class WorldCanvasViewportEditor {
    private enum Drag {
        NONE, MOVE_X, MOVE_Y, MOVE_FREE, ROTATE,
        /** Right / bottom / BR keep the top-left corner fixed. */
        SCALE_W, SCALE_H, SCALE_WH,
        /** Left / top / other corners keep the opposite edge fixed. */
        SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL
    }

    // ImGui packs colors as 0xAABBGGRR, not RGBA.
    /** Panel border: white, faint. */
    private static final int COL_PANEL = 0x66FFFFFF;
    /** Idle widget: muted blue-grey. */
    private static final int COL_WIDGET = 0x55C8AA8C;
    /** Hovered widget: amber. */
    private static final int COL_HOVER = 0xCC79D4FF;
    /** Selected widget: bright blue. */
    private static final int COL_SELECTED = 0xFFFFA84B;

    private static Drag drag = Drag.NONE;
    private static String dragNodeId;
    private static double grabX, grabY;   // mouse on the panel at drag start, canvas pixels
    private static float startPx, startPy, startRz;
    private static int startW, startH;
    private static boolean undoPushed;

    /** TEMP diagnostic: prints once per selection change why the widget gizmo did/didn't draw. */
    private static String lastDebuggedSelection;

    private WorldCanvasViewportEditor() {}

    /** Drop an in-progress drag (editor open/close transitions). */
    public static void cancelInteraction() {
        drag = Drag.NONE;
        dragNodeId = null;
        undoPushed = false;
    }

    /** A panel placed in the world: everything needed to map canvas pixels ⇄ world points. */
    private record Panel(SceneNode node, Vec3 center, Vec3 right, Vec3 up,
                         float worldW, float worldH, int pixelW, int pixelH) {

        Vec3 toWorld(double gx, double gy) {
            double u = (gx / pixelW - 0.5) * worldW;
            double v = (0.5 - gy / pixelH) * worldH;
            return center.add(right.scale(u)).add(up.scale(v));
        }

        /** Canvas-pixel coordinates of a point on the panel plane. */
        double[] toPixels(Vec3 point) {
            Vec3 local = point.subtract(center);
            double u = local.dot(right);
            double v = local.dot(up);
            return new double[]{(u / worldW + 0.5) * pixelW, (0.5 - v / worldH) * pixelH};
        }

        /** Where the ray crosses the panel plane, or null (parallel / behind the camera). */
        Vec3 rayHit(Vec3 origin, Vec3 dir) {
            Vec3 normal = right.cross(up);
            double denom = dir.dot(normal);
            if (Math.abs(denom) < 1.0e-6) return null;
            double t = center.subtract(origin).dot(normal) / denom;
            if (t <= 0.01 || t > 256) return null;
            return origin.add(dir.scale(t));
        }
    }

    private static boolean everCalled;

    public static boolean render(float vx0, float vy0, float vx1, float vy1) {
        if (!everCalled) {
            everCalled = true;
            org.zonarstudio.spraute_engine.script.ScriptConsole.info("world-canvas-gizmo",
                    "render() reached for the first time this session");
        }
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return false;
        String selectedId = SceneEditorSession.selectedNodeId();

        // Only the canvas being worked on is outlined — the rest of the scene stays clean.
        SceneNode canvas = selectedId == null ? null : activeCanvas(doc, selectedId);
        if (canvas == null) {
            debugSelection(doc, selectedId, null, null);
            // Nothing of this panel is selected yet: clicking it in the world selects it, the
            // same way you'd click any object, instead of having to find it in the Hierarchy.
            return selectOnClick(doc, vx0, vy0, vx1, vy1);
        }
        Panel panel = panelOf(doc, canvas);
        if (panel == null) {
            debugSelection(doc, selectedId, canvas, null);
            return false;
        }
        debugSelection(doc, selectedId, canvas, panel);

        ImDrawList dl = ImGui.getWindowDrawList();
        boolean popupOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                | imgui.flag.ImGuiPopupFlags.AnyPopupLevel);
        dl.pushClipRect(vx0, vy0, vx1, vy1, true);

        String key = WorldCanvasEditorPreview.previewKey(canvas);
        List<SceneNode> widgets = new ArrayList<>();
        collectWidgets(canvas, widgets);

        outlineRect(dl, panel, 0, 0, panel.pixelW(), panel.pixelH(), COL_PANEL, 1.5f);

        double[] mouse = mousePixels(panel, vx0, vy0, vx1, vy1);
        SceneNode hovered = null;
        for (SceneNode widget : widgets) {
            float[] bounds = SprauteScriptScreen.editorPreviewBounds(key, widget.id);
            if (bounds == null) continue;
            boolean inside = mouse != null
                    && mouse[0] >= bounds[0] && mouse[0] <= bounds[0] + bounds[2]
                    && mouse[1] >= bounds[1] && mouse[1] <= bounds[1] + bounds[3];
            // Later siblings draw on top, so the last hit is the one the user sees.
            if (inside) hovered = widget;
        }

        for (SceneNode widget : widgets) {
            float[] bounds = SprauteScriptScreen.editorPreviewBounds(key, widget.id);
            if (bounds == null) continue;
            boolean selected = widget.id.equals(selectedId);
            int color = selected ? COL_SELECTED : widget == hovered ? COL_HOVER : COL_WIDGET;
            outlineRect(dl, panel, bounds[0], bounds[1], bounds[2], bounds[3], color,
                    selected ? 2f : 1f);
            if (selected || widget == hovered) {
                labelAt(dl, panel, bounds[0], bounds[1], widget.name, color);
            }
        }

        // The current selection gets the same move/rotate/scale gizmo a flat 2D canvas widget
        // does — the canvas node itself is excluded, it already has the normal 3D object gizmo.
        SceneNode selectedNode = doc.findNode(selectedId);
        SceneNode selectedWidget = selectedNode != null && selectedNode.type.isUiWidget() ? selectedNode : null;

        boolean consumed = !popupOpen && handleInteraction(doc, panel, key, hovered, mouse, dl, selectedWidget);
        dl.popClipRect();
        return consumed;
    }

    /** TEMP diagnostic: logs once per selection change why the widget gizmo did/didn't draw. */
    private static void debugSelection(SceneDocument doc, String selectedId, SceneNode canvas, Panel panel) {
        if (selectedId == null || selectedId.equals(lastDebuggedSelection)) return;
        lastDebuggedSelection = selectedId;
        SceneNode node = doc.findNode(selectedId);
        StringBuilder sb = new StringBuilder("[world-canvas-gizmo] selected=").append(selectedId)
                .append(" type=").append(node == null ? "null-node" : node.type);
        if (canvas == null) {
            sb.append(" -> activeCanvas()==null (");
            SceneNode walk = node;
            if (walk == null) {
                sb.append("selected node not found in doc");
            } else {
                boolean foundCanvasAncestor = false;
                while (walk != null) {
                    if (SceneWorldCanvasRuntime.isWorldCanvas(walk)) {
                        foundCanvasAncestor = true;
                        sb.append("ancestor canvas '").append(walk.name).append("': active=").append(walk.active)
                                .append(" previewEnabled=").append(walk.previewEnabled)
                                .append(" locked=").append(doc.isEffectivelyLocked(walk));
                        break;
                    }
                    walk = doc.findParent(walk.id);
                }
                if (!foundCanvasAncestor) sb.append("no CANVAS(world) ancestor found");
            }
            sb.append(")");
        } else if (panel == null) {
            sb.append(" -> canvas resolved (").append(canvas.name)
                    .append(") but panelOf() returned null (worldTransform/camera/size unavailable)");
        } else {
            sb.append(" -> OK: canvas=").append(canvas.name)
                    .append(" pixelW=").append(panel.pixelW()).append(" pixelH=").append(panel.pixelH())
                    .append(" isUiWidget=").append(node != null && node.type.isUiWidget());
        }
        org.zonarstudio.spraute_engine.script.ScriptConsole.info("world-canvas-gizmo", sb.toString());
    }

    // ---- interaction --------------------------------------------------------

    private static boolean handleInteraction(SceneDocument doc, Panel panel, String key, SceneNode hovered,
                                              double[] mouse, ImDrawList dl, SceneNode selectedWidget) {
        if (!EditorViewportInput.interactionAllowed()) {
            cancelInteraction();
            return false;
        }
        // The canvas' own move/rotate/scale gizmo (the normal 3D object gizmo, on the panel
        // itself) wins over the panel surface, so grabbing an axis that happens to cross the
        // panel still moves the canvas instead of a widget underneath it.
        if (EditorGizmo.isDragging() || EditorGizmo.hoverAxis() != null) {
            drag = Drag.NONE;
            dragNodeId = null;
            return false;
        }

        // Active drag continues even if the ray momentarily misses the panel this frame.
        if (drag != Drag.NONE) {
            SceneNode dragged = dragNodeId == null ? null : doc.findNode(dragNodeId);
            if (ImGui.isMouseDown(0)) {
                if (dragged != null && mouse != null) applyDrag(doc, panel, key, dragged, mouse);
            } else {
                drag = Drag.NONE;
                dragNodeId = null;
                undoPushed = false;
            }
            if (dragged != null) drawGizmo(dl, panel, dragged, key, mouse, true);
            return true;
        }

        // Gizmo hover/click for the current selection.
        Drag hoveredHandle = Drag.NONE;
        if (selectedWidget != null && !doc.isEffectivelyLocked(selectedWidget)) {
            hoveredHandle = drawGizmo(dl, panel, selectedWidget, key, mouse, false);
            if (hoveredHandle != Drag.NONE && mouse != null && ImGui.isMouseClicked(0)) {
                beginDrag(hoveredHandle, selectedWidget, mouse);
                return true;
            }
        }

        if (mouse == null || !ImGui.isMouseClicked(0)) return hoveredHandle != Drag.NONE;
        boolean onPanel = mouse[0] >= 0 && mouse[0] <= panel.pixelW()
                && mouse[1] >= 0 && mouse[1] <= panel.pixelH();
        if (!onPanel) return hoveredHandle != Drag.NONE;

        SceneNode target = hovered;
        if (target == null) {
            // Clicking the panel itself selects the canvas — its own 3D object gizmo takes over.
            SceneEditorSession.select(panel.node().id);
            return true;
        }
        SceneEditorSession.select(target.id);
        if (doc.isEffectivelyLocked(target)) return true;
        // Immediately allow free-dragging the fresh selection, same as the 2D canvas editor.
        beginDrag(Drag.MOVE_FREE, target, mouse);
        return true;
    }

    private static void beginDrag(Drag mode, SceneNode widget, double[] mouse) {
        drag = mode;
        dragNodeId = widget.id;
        grabX = mouse[0];
        grabY = mouse[1];
        startPx = widget.transform != null ? (float) widget.transform.px : 0f;
        startPy = widget.transform != null ? (float) widget.transform.py : 0f;
        startRz = widget.transform != null ? (float) widget.transform.rz : 0f;
        if (widget.type == SceneNodeType.UI_GRID) {
            startW = widget.gridCellW;
            startH = widget.gridCellH;
        } else {
            startW = widget.uiW;
            startH = widget.uiH;
        }
        if (!undoPushed) {
            SceneEditorSession.pushUndo();
            undoPushed = true;
        }
    }

    /** Moves/rotates/scales a widget by the mouse delta measured on the panel, in canvas pixels. */
    private static void applyDrag(SceneDocument doc, Panel panel, String key, SceneNode widget, double[] mouse) {
        if (widget.transform == null) return;
        double dx = mouse[0] - grabX;
        double dy = mouse[1] - grabY;
        if (!undoPushed && (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5)) {
            SceneEditorSession.pushUndo();
            undoPushed = true;
        }
        float[] parent = parentBounds(doc, panel, key, widget);
        float ux = widget.uiPercentX ? 100f / Math.max(1f, parent[2]) : 1f;
        float uy = widget.uiPercentY ? 100f / Math.max(1f, parent[3]) : 1f;
        switch (drag) {
            case MOVE_X -> widget.transform.px = Math.round(snapUnits(startPx, (float) dx, ux));
            case MOVE_Y -> widget.transform.py = Math.round(snapUnits(startPy, (float) dy, uy));
            case MOVE_FREE -> {
                widget.transform.px = Math.round(snapUnits(startPx, (float) dx, ux));
                widget.transform.py = Math.round(snapUnits(startPy, (float) dy, uy));
            }
            case ROTATE -> {
                float[] bounds = SprauteScriptScreen.editorPreviewBounds(key, widget.id);
                double cx = bounds != null ? bounds[0] + bounds[2] / 2.0 : grabX;
                double cy = bounds != null ? bounds[1] + bounds[3] / 2.0 : grabY;
                double a0 = Math.atan2(grabY - cy, grabX - cx);
                double a1 = Math.atan2(mouse[1] - cy, mouse[0] - cx);
                float deg = (float) Math.toDegrees(a1 - a0);
                float value = startRz + deg;
                if (ImGui.getIO().getKeyShift()) value = Math.round(value / 15f) * 15f;
                widget.transform.rz = Math.round(value * 10f) / 10f;
            }
            case SCALE_W, SCALE_H, SCALE_WH, SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL ->
                    applyScale(widget, parent, (float) dx, (float) dy);
            default -> {}
        }
        SceneEditorSession.markDirty();
    }

    /** Shift locks aspect ratio (same idea as the world gizmo uniform cube). */
    private static void applyScale(SceneNode n, float[] parent, float dx, float dy) {
        boolean left = drag == Drag.SCALE_L || drag == Drag.SCALE_TL || drag == Drag.SCALE_BL;
        boolean top = drag == Drag.SCALE_T || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR;
        boolean width = drag == Drag.SCALE_W || drag == Drag.SCALE_WH || drag == Drag.SCALE_L
                || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR || drag == Drag.SCALE_BL;
        boolean height = drag == Drag.SCALE_H || drag == Drag.SCALE_WH || drag == Drag.SCALE_T
                || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR || drag == Drag.SCALE_BL;
        float wf = widthFactor(n, parent);
        float hf = heightFactor(n, parent);
        int newW = Math.round(snapUnits(startW, left ? -dx : dx, wf));
        int newH = Math.round(snapUnits(startH, top ? -dy : dy, hf));
        if (ImGui.getIO().getKeyShift() && startW > 0 && startH > 0) {
            float factor;
            if (width && !height) {
                factor = newW / (float) startW;
            } else if (height && !width) {
                factor = newH / (float) startH;
            } else {
                float fw = newW / (float) startW;
                float fh = newH / (float) startH;
                factor = Math.abs(fw - 1f) >= Math.abs(fh - 1f) ? fw : fh;
            }
            factor = Math.max(1f / Math.max(startW, startH), factor);
            newW = Math.max(1, Math.round(startW * factor));
            newH = Math.max(1, Math.round(startH * factor));
        }
        if (width) setWidth(n, newW);
        if (height) setHeight(n, newH);

        // Left/top handles keep the opposite edge fixed.
        if (left) {
            float movedPx = (startW - storedWidth(n)) / Math.max(0.0001f, wf);
            float ux = n.uiPercentX ? 100f / Math.max(1, parent[2]) : 1f;
            n.transform.px = Math.round(startPx + movedPx * ux);
        }
        if (top) {
            float movedPx = (startH - storedHeight(n)) / Math.max(0.0001f, hf);
            float uy = n.uiPercentY ? 100f / Math.max(1, parent[3]) : 1f;
            n.transform.py = Math.round(startPy + movedPx * uy);
        }
    }

    private static int storedWidth(SceneNode n) {
        return n.type == SceneNodeType.UI_GRID ? n.gridCellW : n.uiW;
    }

    private static int storedHeight(SceneNode n) {
        return n.type == SceneNodeType.UI_GRID ? n.gridCellH : n.uiH;
    }

    private static boolean isScaleDrag(Drag value) {
        return switch (value) {
            case SCALE_W, SCALE_H, SCALE_WH, SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL -> true;
            default -> false;
        };
    }

    /** Converts a canvas-pixel width delta into the node's stored unit (px or %). */
    private static float widthFactor(SceneNode n, float[] parent) {
        if (n.type == SceneNodeType.UI_GRID) {
            return 1f / Math.max(1, n.gridColumns);
        }
        return n.uiPercentW ? 100f / Math.max(1f, parent[2]) : 1f;
    }

    private static float heightFactor(SceneNode n, float[] parent) {
        if (n.type == SceneNodeType.UI_GRID) {
            int count = 0;
            for (SceneNode c : n.children) if (c.type.isUiWidget()) count++;
            int rows = Math.max(1, (count + Math.max(1, n.gridColumns) - 1) / Math.max(1, n.gridColumns));
            return 1f / rows;
        }
        return n.uiPercentH ? 100f / Math.max(1f, parent[3]) : 1f;
    }

    private static void setWidth(SceneNode n, int value) {
        if (n.type == SceneNodeType.UI_GRID) n.gridCellW = Math.max(1, value);
        else n.uiW = Math.max(1, value);
    }

    private static void setHeight(SceneNode n, int value) {
        if (n.type == SceneNodeType.UI_GRID) n.gridCellH = Math.max(1, value);
        else n.uiH = Math.max(1, value);
    }

    private static String scaleLabel(SceneNode n) {
        if (n.type == SceneNodeType.UI_GRID) return n.gridCellW + " x " + n.gridCellH;
        return n.uiW + " x " + n.uiH;
    }

    /**
     * Moves/scales in canvas-pixel space with UI grid snapping, then converts back to the
     * node's stored unit (px or %). Alt bypasses snapping — same behavior as the 2D canvas.
     */
    private static float snapUnits(float startUnits, float dPixels, float unitsPerPixel) {
        if (unitsPerPixel == 0f) return startUnits;
        float px = startUnits / unitsPerPixel + dPixels;
        return snapPx(px) * unitsPerPixel;
    }

    private static float snapPx(float px) {
        if (ImGui.getIO().getKeyAlt()) return px;
        return EditorGrid.snapUiPx(px);
    }

    /** Bounds of the widget's container — the canvas itself for a direct child. */
    private static float[] parentBounds(SceneDocument doc, Panel panel, String key, SceneNode widget) {
        SceneNode parent = doc.findParent(widget.id);
        if (parent != null && parent.type.isUiWidget()) {
            float[] bounds = SprauteScriptScreen.editorPreviewBounds(key, parent.id);
            if (bounds != null) return bounds;
        }
        return new float[]{0f, 0f, panel.pixelW(), panel.pixelH()};
    }

    // ---- gizmo ----------------------------------------------------------------

    /**
     * Draws the same Unity-style gizmo {@link CanvasViewportEditor} draws for a flat widget —
     * move arrows + center square, rotate ring, or scale handles depending on the active
     * toolbar tool — except every point is built in canvas-pixel space and projected through
     * {@link #toScreen}, so it sits correctly on the panel from any angle or distance.
     */
    private static Drag drawGizmo(ImDrawList dl, Panel panel, SceneNode widget, String key,
                                  double[] mouse, boolean active) {
        float[] bounds = SprauteScriptScreen.editorPreviewBounds(key, widget.id);
        if (bounds == null) return Drag.NONE;
        double bx = bounds[0], by = bounds[1], bw = bounds[2], bh = bounds[3];
        double cx = bx + bw / 2.0, cy = by + bh / 2.0;
        // How many canvas pixels correspond to one screen pixel here, so handles stay a
        // roughly constant on-screen size regardless of how far away or how large the panel is.
        double scale = panelPixelsPerScreenPixel(panel);

        EditorGizmo.Mode mode = EditorGizmo.mode();
        Drag hovered = Drag.NONE;

        if (mode == EditorGizmo.Mode.MOVE || mode == EditorGizmo.Mode.SELECT) {
            double len = 44 * scale;
            double armTip = 8 * scale;
            double half = 5 * scale;
            int red = ImGui.getColorU32(0.95f, 0.30f, 0.25f, 1f);
            int green = ImGui.getColorU32(0.35f, 0.85f, 0.35f, 1f);
            drawLine(dl, panel, cx - len, cy, cx + len, cy, red, 2.5f);
            drawTri(dl, panel, cx + len + armTip, cy, cx + len, cy - half, cx + len, cy + half, red);
            drawTri(dl, panel, cx - len - armTip, cy, cx - len, cy - half, cx - len, cy + half, red);
            drawLine(dl, panel, cx, cy - len, cx, cy + len, green, 2.5f);
            drawTri(dl, panel, cx, cy + len + armTip, cx - half, cy + len, cx + half, cy + len, green);
            drawTri(dl, panel, cx, cy - len - armTip, cx - half, cy - len, cx + half, cy - len, green);
            int boxColor = ImGui.getColorU32(0.95f, 0.85f, 0.3f, active && drag == Drag.MOVE_FREE ? 1f : 0.85f);
            drawQuadFilled(dl, panel, cx - half, cy - half, cx + half, cy + half, boxColor);

            if (mouse != null) {
                if (nearPanel(mouse, cx - half, cy - half, cx + half, cy + half, 2 * scale)) {
                    hovered = Drag.MOVE_FREE;
                } else if (nearSegmentPanel(mouse, cx - len - armTip, cy, cx + len + armTip, cy, 6 * scale)) {
                    hovered = Drag.MOVE_X;
                } else if (nearSegmentPanel(mouse, cx, cy - len - armTip, cx, cy + len + armTip, 6 * scale)) {
                    hovered = Drag.MOVE_Y;
                }
            }
        } else if (mode == EditorGizmo.Mode.ROTATE) {
            double radius = Math.max(24 * scale, Math.max(bw, bh) / 2.0 + 14 * scale);
            int ring = ImGui.getColorU32(0.35f, 0.65f, 0.95f, 1f);
            drawCirclePanel(dl, panel, cx, cy, radius, ring, 2.5f);
            float rz = widget.transform != null ? (float) widget.transform.rz : 0f;
            double a = Math.toRadians(rz);
            drawLine(dl, panel, cx, cy, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    ImGui.getColorU32(0.35f, 0.65f, 0.95f, 0.6f), 1.5f);
            if (mouse != null) {
                double dist = Math.hypot(mouse[0] - cx, mouse[1] - cy);
                if (Math.abs(dist - radius) < 8 * scale) hovered = Drag.ROTATE;
            }
            if (active && drag == Drag.ROTATE) {
                Float[] labelAt = toScreen(panel, cx + radius + 6 * scale, cy - 6 * scale);
                if (labelAt != null) {
                    dl.addText(labelAt[0], labelAt[1], ring,
                            String.format(java.util.Locale.ROOT, "%.1f°", rz));
                }
            }
        } else if (mode == EditorGizmo.Mode.SCALE) {
            int blue = ImGui.getColorU32(0.35f, 0.65f, 0.95f, 1f);
            int corner = ImGui.getColorU32(0.95f, 0.85f, 0.3f, 1f);
            double hs = 5 * scale;
            double x0 = bx, y0 = by, x1 = bx + bw, y1 = by + bh;
            double[][] handles = {
                    {x0, y0}, {cx, y0}, {x1, y0},
                    {x0, cy},           {x1, cy},
                    {x0, y1}, {cx, y1}, {x1, y1}
            };
            Drag[] handleDrag = {
                    Drag.SCALE_TL, Drag.SCALE_T, Drag.SCALE_TR,
                    Drag.SCALE_L,                Drag.SCALE_W,
                    Drag.SCALE_BL, Drag.SCALE_H, Drag.SCALE_WH
            };
            for (int i = 0; i < handles.length; i++) {
                boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                drawQuadFilled(dl, panel, handles[i][0] - hs, handles[i][1] - hs,
                        handles[i][0] + hs, handles[i][1] + hs, isCorner ? corner : blue);
            }
            if (mouse != null) {
                // Corners first so they win over edges when the box is small.
                for (int i = 0; i < handles.length && hovered == Drag.NONE; i++) {
                    boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                    if (!isCorner) continue;
                    if (nearPanel(mouse, handles[i][0] - hs, handles[i][1] - hs,
                            handles[i][0] + hs, handles[i][1] + hs, 2 * scale)) hovered = handleDrag[i];
                }
                for (int i = 0; i < handles.length && hovered == Drag.NONE; i++) {
                    boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                    if (isCorner) continue;
                    if (nearPanel(mouse, handles[i][0] - hs, handles[i][1] - hs,
                            handles[i][0] + hs, handles[i][1] + hs, 2 * scale)) hovered = handleDrag[i];
                }
            }
            if (active && isScaleDrag(drag)) {
                Float[] labelAt = toScreen(panel, x1 + 8 * scale, y1 + 4 * scale);
                if (labelAt != null) dl.addText(labelAt[0], labelAt[1], blue, scaleLabel(widget));
            }
        }
        return hovered;
    }

    /** Canvas pixels a single screen pixel spans here — keeps gizmo chrome a constant screen size. */
    private static double panelPixelsPerScreenPixel(Panel panel) {
        double cx = panel.pixelW() / 2.0, cy = panel.pixelH() / 2.0;
        Float[] a = toScreen(panel, cx, cy);
        Float[] b = toScreen(panel, cx + 10, cy);
        if (a == null || b == null) return 1.0;
        double screenDist = Math.hypot(b[0] - a[0], b[1] - a[1]);
        if (screenDist < 0.5) return 1.0;
        return 10.0 / screenDist;
    }

    private static boolean nearPanel(double[] mouse, double x0, double y0, double x1, double y1, double pad) {
        return mouse[0] >= x0 - pad && mouse[0] <= x1 + pad && mouse[1] >= y0 - pad && mouse[1] <= y1 + pad;
    }

    private static boolean nearSegmentPanel(double[] mouse, double x0, double y0,
                                            double x1, double y1, double tolerance) {
        double dx = x1 - x0, dy = y1 - y0;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, ((mouse[0] - x0) * dx + (mouse[1] - y0) * dy) / lengthSq));
        double nx = x0 + t * dx, ny = y0 + t * dy;
        return Math.hypot(mouse[0] - nx, mouse[1] - ny) <= tolerance;
    }

    // ---- geometry -----------------------------------------------------------

    /** Picks the nearest world canvas under the mouse on click, so panels select like objects. */
    private static boolean selectOnClick(SceneDocument doc, float vx0, float vy0, float vx1, float vy1) {
        if (!ImGui.isMouseClicked(0) || !EditorViewportInput.interactionAllowed()) return false;
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                | imgui.flag.ImGuiPopupFlags.AnyPopupLevel)) return false;
        List<SceneNode> canvases = new ArrayList<>();
        collectCanvases(doc.root, doc, canvases);
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (SceneNode node : canvases) {
            Panel panel = panelOf(doc, node);
            if (panel == null) continue;
            double[] pixels = mousePixels(panel, vx0, vy0, vx1, vy1);
            if (pixels == null) continue;
            if (pixels[0] < 0 || pixels[0] > panel.pixelW() || pixels[1] < 0 || pixels[1] > panel.pixelH()) continue;
            double dist = panel.center().distanceToSqr(
                    Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = node.id;
            }
        }
        if (best == null) return false;
        SceneEditorSession.select(best);
        return true;
    }

    private static void collectCanvases(SceneNode parent, SceneDocument doc, List<SceneNode> out) {
        for (SceneNode child : parent.children) {
            if (!child.active) continue;
            if (SceneWorldCanvasRuntime.isWorldCanvas(child)
                    && child.previewEnabled && !doc.isEffectivelyLocked(child)) {
                out.add(child);
                continue;
            }
            collectCanvases(child, doc, out);
        }
    }

    /** The world canvas the current selection belongs to, or null. */
    private static SceneNode activeCanvas(SceneDocument doc, String selectedId) {
        SceneNode node = doc.findNode(selectedId);
        while (node != null) {
            if (SceneWorldCanvasRuntime.isWorldCanvas(node)) {
                return node.active && node.previewEnabled && !doc.isEffectivelyLocked(node) ? node : null;
            }
            node = doc.findParent(node.id);
        }
        return null;
    }

    private static Panel panelOf(SceneDocument doc, SceneNode canvas) {
        SceneTransform world = doc.worldTransform(canvas.id);
        if (world == null) return null;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) return null;
        float[] size = WorldCanvasEditorPreview.panelSize(canvas, world);
        // The pixel grid must be the one the texture was laid out with, not the configured one,
        // or every outline sits slightly off the widget it belongs to.
        int[] pixels = WorldCanvasEditorPreview.panelPixels(canvas);
        Vec3[] axes = WorldCanvasTextureManager.panelAxes(
                SceneWorldCanvasRuntime.modeOf(canvas.canvasWorldOrientation),
                (float) world.ry, (float) world.rx, (float) world.rz, camera);
        return new Panel(canvas, new Vec3(world.px, world.py, world.pz), axes[0], axes[1],
                Math.max(0.01f, size[0]), Math.max(0.01f, size[1]), pixels[0], pixels[1]);
    }

    /** Mouse position in canvas pixels, or null when it misses the panel or the viewport. */
    private static double[] mousePixels(Panel panel, float vx0, float vy0, float vx1, float vy1) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        if (mx < vx0 || mx > vx1 || my < vy0 || my > vy1) return null;
        Minecraft mc = Minecraft.getInstance();
        double toGui = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double toGuiY = (double) mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
        Vec3[] ray = EditorGizmo.mouseRay(mx * toGui, my * toGuiY);
        Vec3 hit = panel.rayHit(ray[0], ray[1]);
        return hit == null ? null : panel.toPixels(hit);
    }

    private static void collectWidgets(SceneNode parent, List<SceneNode> out) {
        for (SceneNode child : parent.children) {
            if (!child.type.isUiWidget() || !child.active) continue;
            out.add(child);
            collectWidgets(child, out);
        }
    }

    // ---- drawing ------------------------------------------------------------

    private static void outlineRect(ImDrawList dl, Panel panel, float x, float y, float w, float h,
                                    int color, float thickness) {
        Float[] a = toScreen(panel, x, y);
        Float[] b = toScreen(panel, x + w, y);
        Float[] c = toScreen(panel, x + w, y + h);
        Float[] d = toScreen(panel, x, y + h);
        if (a == null || b == null || c == null || d == null) return;
        dl.addLine(a[0], a[1], b[0], b[1], color, thickness);
        dl.addLine(b[0], b[1], c[0], c[1], color, thickness);
        dl.addLine(c[0], c[1], d[0], d[1], color, thickness);
        dl.addLine(d[0], d[1], a[0], a[1], color, thickness);
    }

    private static void labelAt(ImDrawList dl, Panel panel, float x, float y, String text, int color) {
        Float[] at = toScreen(panel, x, y);
        if (at == null || text == null || text.isEmpty()) return;
        dl.addText(at[0] + 3f, at[1] - 14f, color, text);
    }

    private static void drawLine(ImDrawList dl, Panel panel, double x0, double y0, double x1, double y1,
                                 int color, float thickness) {
        Float[] a = toScreen(panel, x0, y0);
        Float[] b = toScreen(panel, x1, y1);
        if (a == null || b == null) return;
        dl.addLine(a[0], a[1], b[0], b[1], color, thickness);
    }

    private static void drawTri(ImDrawList dl, Panel panel, double x0, double y0, double x1, double y1,
                                double x2, double y2, int color) {
        Float[] a = toScreen(panel, x0, y0);
        Float[] b = toScreen(panel, x1, y1);
        Float[] c = toScreen(panel, x2, y2);
        if (a == null || b == null || c == null) return;
        dl.addTriangleFilled(a[0], a[1], b[0], b[1], c[0], c[1], color);
    }

    private static void drawQuadFilled(ImDrawList dl, Panel panel, double x0, double y0, double x1, double y1,
                                       int color) {
        Float[] a = toScreen(panel, x0, y0);
        Float[] b = toScreen(panel, x1, y0);
        Float[] c = toScreen(panel, x1, y1);
        Float[] d = toScreen(panel, x0, y1);
        if (a == null || b == null || c == null || d == null) return;
        dl.addQuadFilled(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1], color);
    }

    private static void drawCirclePanel(ImDrawList dl, Panel panel, double cx, double cy, double radius,
                                        int color, float thickness) {
        int segments = 40;
        Float[] prev = null;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments;
            Float[] p = toScreen(panel, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius);
            if (p != null && prev != null) dl.addLine(prev[0], prev[1], p[0], p[1], color, thickness);
            prev = p;
        }
    }

    /** Canvas pixel → world → the viewport's own (ImGui) screen pixels. */
    private static Float[] toScreen(Panel panel, double gx, double gy) {
        double[] gui = EditorGizmo.projectWorldToGui(panel.toWorld(gx, gy));
        if (gui == null) return null;
        Minecraft mc = Minecraft.getInstance();
        double sx = (double) mc.getWindow().getScreenWidth() / Math.max(1, mc.getWindow().getGuiScaledWidth());
        double sy = (double) mc.getWindow().getScreenHeight() / Math.max(1, mc.getWindow().getGuiScaledHeight());
        return new Float[]{(float) (gui[0] * sx), (float) (gui[1] * sy)};
    }
}
