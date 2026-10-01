package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.SprauteScriptScreen;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.ui.UiChildAlign;
import org.zonarstudio.spraute_engine.ui.UiGridLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Unity-style 2D canvas editing directly over the world viewport: every CANVAS
 * of the open scene is drawn on top of the captured world image exactly where
 * it will appear at runtime (the viewport acts as the game screen). Widgets are
 * selectable and manipulated with the shared toolbar tools (Q/W/E/R): move
 * arrows, rotation ring and scale handles.
 */
public final class CanvasViewportEditor {
    private enum Drag {
        NONE, MOVE_X, MOVE_Y, MOVE_FREE, ROTATE, MOVE_CANVAS,
        /** Right / bottom / BR keep the top-left corner fixed. */
        SCALE_W, SCALE_H, SCALE_WH,
        /** Left / top / other corners keep the opposite edge fixed. */
        SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL
    }

    /** One laid-out node: screen rect plus data needed to write edits back. */
    private record Placed(SceneNode node, boolean isCanvas,
                          float sx, float sy, float sw, float sh,
                          int parentW, int parentH) {}

    private static Drag drag = Drag.NONE;
    private static String dragNodeId;
    private static float dragMouseX, dragMouseY;
    private static float startPx, startPy, startRz;
    /** Absolute canvas left/top in screen pixels at drag start (decoded from 0=center / negative=from-right). */
    private static float startLeft, startTop;
    private static int startW, startH;
    private static boolean undoPushed;

    private CanvasViewportEditor() {}

    /** Drop an in-progress widget drag (editor open/close transitions). */
    public static void cancelInteraction() {
        drag = Drag.NONE;
        dragNodeId = null;
        undoPushed = false;
    }

    /**
     * Draws all canvases over the viewport rect and handles editing.
     * Returns true when the mouse interaction was consumed by the GUI layer
     * (the world viewport must then skip camera/NPC picking).
     */
    public static boolean render(float vx0, float vy0, float vx1, float vy1) {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        float k = (vx1 - vx0) / guiW;

        List<Placed> placed = new ArrayList<>();
        collectCanvases(doc.root, doc, vx0, vy0, k, guiW, guiH, placed);
        if (placed.isEmpty()) return false;
        sortByLayer(placed);

        // Popups (context menus, modals) must stay on top: draw outlines/gizmos
        // into the viewport window's own draw list, not the foreground one, and
        // freeze interaction while any popup is open.
        ImDrawList dl = ImGui.getWindowDrawList();
        boolean popupOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                | imgui.flag.ImGuiPopupFlags.AnyPopupLevel);
        dl.pushClipRect(vx0, vy0, vx1, vy1, true);

        String selectedId = SceneEditorSession.selectedNodeId();
        Placed selected = null;
        Placed selectedCanvas = null;
        Placed lastCanvas = null;
        for (Placed p : placed) {
            if (p.isCanvas) lastCanvas = p;
            if (p.node.id.equals(selectedId)) {
                selected = p;
                selectedCanvas = lastCanvas;
            }
        }

        // Pixel grid over the canvas being edited (same settings popup as the world grid).
        if (!popupOpen && selectedCanvas != null && EditorGrid.isUiGridVisible()) {
            drawUiGrid(dl, selectedCanvas, k);
        }
        if (!popupOpen) {
            for (Placed p : placed) drawNode(dl, p, k);
        }

        boolean consumed = !popupOpen && handleInteraction(dl, placed, selected, k, vx0, vy0, vx1, vy1);
        dl.popClipRect();
        return consumed;
    }

    // ---- layout ---------------------------------------------------------

    private static void collectCanvases(SceneNode node, SceneDocument doc,
                                        float vx0, float vy0, float k,
                                        int guiW, int guiH, List<Placed> out) {
        if (node.type == SceneNodeType.CANVAS) {
            // A world-space canvas is not a screen GUI: it is drawn as a plane in the world and
            // edited there (WorldCanvasViewportEditor), never flat over the whole viewport.
            if (node.canvasWorldSpace) return;
            int firstPlaced = out.size();
            int pw = Math.max(4, node.uiPercentW
                    ? Math.round(node.uiCanvasW / 100f * guiW) : node.uiCanvasW);
            int ph = Math.max(4, node.uiPercentH
                    ? Math.round(node.uiCanvasH / 100f * guiH) : node.uiCanvasH);
            float rawX = node.transform != null ? (float) node.transform.px : 0f;
            float rawY = node.transform != null ? (float) node.transform.py : 0f;
            float left = decodeCanvasAxis(rawX, pw, guiW, node.uiPercentX);
            float top = decodeCanvasAxis(rawY, ph, guiH, node.uiPercentY);
            float sx = vx0 + left * k;
            float sy = vy0 + top * k;
            out.add(new Placed(node, true, sx, sy, pw * k, ph * k, guiW, guiH));
            for (SceneNode child : node.children) {
                collectWidget(child, sx, sy, k, pw, ph, out);
            }
            // The preview is the source of truth. This also covers nested
            // percentages, child alignment, fixed-size widgets and runtime rounding.
            for (int i = firstPlaced; i < out.size(); i++) {
                Placed old = out.get(i);
                float[] bounds = SprauteScriptScreen.editorPreviewBounds(
                        node.id, old.isCanvas ? null : old.node.id);
                if (bounds == null) continue;
                out.set(i, new Placed(old.node, old.isCanvas,
                        vx0 + bounds[0] * k, vy0 + bounds[1] * k,
                        Math.max(2f, bounds[2] * k), Math.max(2f, bounds[3] * k),
                        old.parentW, old.parentH));
            }
        }
        for (SceneNode child : node.children) {
            if (child.type != SceneNodeType.CANVAS && !child.type.isUiWidget()) {
                collectCanvases(child, doc, vx0, vy0, k, guiW, guiH, out);
            } else if (child.type == SceneNodeType.CANVAS) {
                collectCanvases(child, doc, vx0, vy0, k, guiW, guiH, out);
            }
        }
    }

    /**
     * Draw/pick order matches runtime: canvases by layer, then widgets by layer
     * (stable — same layer keeps hierarchy order). Later in the list = on top.
     */
    private static void sortByLayer(List<Placed> placed) {
        if (placed.size() < 2) return;
        List<List<Placed>> groups = new ArrayList<>();
        List<Placed> current = null;
        for (Placed p : placed) {
            if (p.isCanvas) {
                current = new ArrayList<>();
                current.add(p);
                groups.add(current);
            } else if (current != null) {
                current.add(p);
            }
        }
        groups.sort(Comparator.comparingInt(g -> g.get(0).node.canvasLayer));
        for (List<Placed> group : groups) {
            if (group.size() <= 2) continue;
            Placed canvas = group.get(0);
            List<Placed> widgets = new ArrayList<>(group.subList(1, group.size()));
            widgets.sort(Comparator.comparingInt(p -> p.node.canvasLayer));
            group.clear();
            group.add(canvas);
            group.addAll(widgets);
        }
        placed.clear();
        for (List<Placed> group : groups) placed.addAll(group);
    }

    private static void collectWidget(SceneNode node, float ox, float oy, float k,
                                      int parentW, int parentH, List<Placed> out) {
        if (!node.type.isUiWidget()) return;
        float rawX = node.transform != null ? (float) node.transform.px : 0f;
        float rawY = node.transform != null ? (float) node.transform.py : 0f;
        if (node.uiPercentX) rawX = rawX / 100f * parentW;
        if (node.uiPercentY) rawY = rawY / 100f * parentH;
        float w = node.uiPercentW ? node.uiW / 100f * parentW : node.uiW;
        float h = node.uiPercentH ? node.uiH / 100f * parentH : node.uiH;
        if (node.type == SceneNodeType.UI_TEXT) {
            w = Math.max(w, node.uiText == null ? 30 : node.uiText.length() * 5f * node.uiTextScale);
            h = Math.max(h, 9f * node.uiTextScale);
        }
        float sx = ox + rawX * k;
        float sy = oy + rawY * k;

        if (node.type == SceneNodeType.UI_GRID) {
            List<SceneNode> kids = new ArrayList<>();
            List<UiGridLayout.Item> items = new ArrayList<>();
            int boxW = Math.max(1, Math.round(w));
            int boxH = Math.max(1, Math.round(h));
            for (SceneNode c : node.children) {
                if (!c.type.isUiWidget()) continue;
                kids.add(c);
                int cw = c.uiPercentW ? Math.max(1, Math.round(c.uiW / 100f * boxW)) : Math.max(1, c.uiW);
                int ch = c.uiPercentH ? Math.max(1, Math.round(c.uiH / 100f * boxH)) : Math.max(1, c.uiH);
                items.add(new UiGridLayout.Item(cw, ch));
            }
            int[] content = UiGridLayout.contentSize(
                    node.gridMode, node.gridHugContent, node.gridColumns,
                    node.gridCellW, node.gridCellH, node.gridGapX, node.gridGapY, items);
            int layoutW = Math.max(boxW, content[0]);
            int layoutH = Math.max(boxH, content[1]);
            float gw = layoutW * k;
            float gh = layoutH * k;
            out.add(new Placed(node, false, sx, sy, gw, gh, parentW, parentH));
            List<UiGridLayout.Placement> places = UiGridLayout.compute(
                    node.gridMode, node.gridAlign, node.gridFlow,
                    node.gridHugContent, node.gridStretchCells,
                    node.gridColumns, node.gridCellW, node.gridCellH,
                    node.gridGapX, node.gridGapY, layoutW, layoutH, items);
            for (UiGridLayout.Placement p : places) {
                SceneNode child = kids.get(p.index());
                collectWidget(child, sx + p.x() * k, sy + p.y() * k, k, p.cellW(), p.cellH(), out);
            }
            return;
        }

        out.add(new Placed(node, false, sx, sy, Math.max(2f, w * k), Math.max(2f, h * k), parentW, parentH));
        String childAlign = switch (node.type) {
            case UI_RECT, UI_SCROLL, UI_CROP, UI_GROUP, UI_DRAG, UI_DROPTARGET -> node.uiChildAlign;
            default -> "top_left";
        };
        for (SceneNode child : node.children) {
            if (!child.type.isUiWidget()) {
                collectWidget(child, sx, sy, k, Math.round(w), Math.round(h), out);
                continue;
            }
            float childRawX = child.transform != null ? (float) child.transform.px : 0f;
            float childRawY = child.transform != null ? (float) child.transform.py : 0f;
            float childW = child.uiPercentW ? child.uiW / 100f * w : child.uiW;
            float childH = child.uiPercentH ? child.uiH / 100f * h : child.uiH;
            if (child.uiPercentX) childRawX = childRawX / 100f * w;
            if (child.uiPercentY) childRawY = childRawY / 100f * h;
            float cox = sx + childRawX * k;
            float coy = sy + childRawY * k;
            if (!UiChildAlign.isOrigin(childAlign)) {
                cox = sx + (UiChildAlign.axisX(childAlign) * w + childRawX
                        - UiChildAlign.axisX(childAlign) * childW) * k;
                coy = sy + (UiChildAlign.axisY(childAlign) * h + childRawY
                        - UiChildAlign.axisY(childAlign) * childH) * k;
                // collectWidget will add child transform again — temporarily zero it via copy path:
                // instead pass pre-offset origin and a zeroed virtual parent by clearing offsets:
                collectWidgetAligned(child, cox, coy, k, Math.round(w), Math.round(h), out);
            } else {
                collectWidget(child, sx, sy, k, Math.round(w), Math.round(h), out);
            }
        }
    }

    /** Like {@link #collectWidget} but treats {@code ox/oy} as the child's final top-left (no transform add). */
    private static void collectWidgetAligned(SceneNode node, float ox, float oy, float k,
                                             int parentW, int parentH, List<Placed> out) {
        if (!node.type.isUiWidget()) return;
        float w = node.uiPercentW ? node.uiW / 100f * parentW : node.uiW;
        float h = node.uiPercentH ? node.uiH / 100f * parentH : node.uiH;
        if (node.type == SceneNodeType.UI_TEXT) {
            w = Math.max(w, node.uiText == null ? 30 : node.uiText.length() * 5f * node.uiTextScale);
            h = Math.max(h, 9f * node.uiTextScale);
        }
        out.add(new Placed(node, false, ox, oy, Math.max(2f, w * k), Math.max(2f, h * k), parentW, parentH));
        for (SceneNode child : node.children) {
            collectWidget(child, ox, oy, k, Math.round(w), Math.round(h), out);
        }
    }

    // ---- drawing ----------------------------------------------------------

    /** Pixel grid over the canvas of the current selection: minor = step, major = 4 steps. */
    private static void drawUiGrid(ImDrawList dl, Placed canvas, float k) {
        int stepPx = EditorGrid.uiCellPx();
        float step = stepPx * k;
        if (step < 3f) return; // too dense to be useful at this zoom
        int minor = ImGui.getColorU32(1f, 1f, 1f, 0.07f);
        int major = ImGui.getColorU32(1f, 1f, 1f, 0.16f);
        float x1 = canvas.sx + canvas.sw, y1 = canvas.sy + canvas.sh;
        int i = 0;
        for (float x = canvas.sx; x <= x1 + 0.5f; x += step, i++) {
            dl.addLine(x, canvas.sy, x, y1, i % 4 == 0 ? major : minor, 1f);
        }
        i = 0;
        for (float y = canvas.sy; y <= y1 + 0.5f; y += step, i++) {
            dl.addLine(canvas.sx, y, x1, y, i % 4 == 0 ? major : minor, 1f);
        }
    }

    /**
     * The widgets themselves are rendered by the game (SprauteScriptScreen into
     * the world capture), so nodes look exactly like on the player's screen.
     * ImGui draws chrome only for the hovered node (thin outline + label);
     * the selected node gets its outline from the gizmo pass.
     */
    private static void drawNode(ImDrawList dl, Placed p, float k) {
        SceneNode node = p.node;
        ImVec2 mouse = ImGui.getMousePos();
        boolean hovered = mouse.x >= p.sx && mouse.x <= p.sx + p.sw
                && mouse.y >= p.sy && mouse.y <= p.sy + p.sh;
        SceneDocument doc = SceneEditorSession.document();
        if (!hovered || (doc != null && doc.isEffectivelyLocked(node))) return;
        float rz = node.transform != null ? (float) node.transform.rz : 0f;
        float r = node.uiRadius * k;
        int outline = p.isCanvas
                ? ImGui.getColorU32(0.98f, 0.80f, 0.35f, 0.6f)
                : ImGui.getColorU32(1f, 1f, 1f, 0.45f);
        if (rz != 0f && !p.isCanvas) {
            float cx = p.sx + p.sw / 2f, cy = p.sy + p.sh / 2f;
            float[][] q = rotatedCorners(cx, cy, p.sw, p.sh, rz);
            dl.addQuad(q[0][0], q[0][1], q[1][0], q[1][1], q[2][0], q[2][1], q[3][0], q[3][1], outline, 1f);
        } else {
            dl.addRect(p.sx, p.sy, p.sx + p.sw, p.sy + p.sh, outline, r);
        }
        if (p.isCanvas) {
            dl.addText(p.sx + 4f, p.sy - ImGui.getTextLineHeight() - 2f, outline, node.name);
        }
    }

    // ---- interaction --------------------------------------------------------

    private static boolean handleInteraction(ImDrawList dl, List<Placed> placed, Placed selected,
                                             float k, float vx0, float vy0, float vx1, float vy1) {
        if (!EditorViewportInput.interactionAllowed()) {
            cancelInteraction();
            return false;
        }
        ImVec2 mouse = ImGui.getMousePos();
        boolean mouseInViewport = mouse.x >= vx0 && mouse.x <= vx1 && mouse.y >= vy0 && mouse.y <= vy1;

        // A canvas rect usually covers the whole viewport. The 3D gizmo of a world
        // selection floats above it, so its handles must win: otherwise this layer
        // eats the click, drops the selection and the world click falls through to
        // the fly-camera.
        if (drag == Drag.NONE && EditorGizmo.hoverAxis() != null) return false;

        // Active drag continues even if the cursor leaves the handle.
        if (drag != Drag.NONE) {
            if (ImGui.isMouseDown(0)) {
                applyDrag(mouse.x, mouse.y, k, selected);
            } else {
                drag = Drag.NONE;
                dragNodeId = null;
                undoPushed = false;
            }
            if (selected != null) drawGizmo(dl, selected, k, true);
            return true;
        }

        // Gizmo hover/click for the current selection.
        Drag hovered = Drag.NONE;
        if (selected != null) {
            hovered = drawGizmo(dl, selected, k, false);
            if (hovered != Drag.NONE && ImGui.isMouseClicked(0)) {
                beginDrag(hovered, selected, mouse.x, mouse.y);
                return true;
            }
        }
        if (!mouseInViewport) return false;

        // Selection: topmost widget (later in the list = drawn later = on top).
        // Locked nodes (incl. under locked parents) are only selectable via Hierarchy.
        SceneDocument doc = SceneEditorSession.document();
        Placed hit = null;
        for (Placed p : placed) {
            if (doc != null && doc.isEffectivelyLocked(p.node)) continue;
            if (mouse.x >= p.sx && mouse.x <= p.sx + p.sw && mouse.y >= p.sy && mouse.y <= p.sy + p.sh) {
                // Canvas itself only counts when no widget matches; later
                // widgets are drawn on top so they win.
                if (!p.isCanvas || hit == null) hit = p;
            }
        }
        if (hit != null) {
            // Widgets win over the world, the empty canvas background does not:
            // a 3D node under the cursor stays selectable in mixed 2D/3D scenes.
            if (hit.isCanvas && ImGui.isMouseClicked(0)) {
                double[] gui = toGuiCoords(mouse.x, mouse.y);
                if (EditorGizmo.pickNode(gui[0], gui[1]) != null) return false;
            }
            if (ImGui.isMouseClicked(0)) {
                SceneEditorSession.select(hit.node.id);
                // Immediately allow free-dragging the fresh selection in MOVE mode.
                if (EditorGizmo.mode() == EditorGizmo.Mode.MOVE || EditorGizmo.mode() == EditorGizmo.Mode.SELECT) {
                    beginDrag(hit.isCanvas ? Drag.MOVE_CANVAS : Drag.MOVE_FREE,
                            hit, mouse.x, mouse.y);
                }
            }
            return true;
        }
        // Empty viewport click is owned by the world layer (marquee, ctrl-toggle,
        // fly-camera) — clearing the selection here would break all three.
        return hovered != Drag.NONE;
    }

    /** ImGui window pixels → GUI-scaled coordinates used by world picking. */
    private static double[] toGuiCoords(float mx, float my) {
        var window = Minecraft.getInstance().getWindow();
        return new double[]{
                mx * (double) window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth()),
                my * (double) window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight())
        };
    }

    private static void beginDrag(Drag mode, Placed p, float mx, float my) {
        drag = mode;
        dragNodeId = p.node.id;
        dragMouseX = mx;
        dragMouseY = my;
        SceneNode n = p.node;
        startPx = n.transform != null ? (float) n.transform.px : 0f;
        startPy = n.transform != null ? (float) n.transform.py : 0f;
        startRz = n.transform != null ? (float) n.transform.rz : 0f;
        if (p.isCanvas) {
            startW = n.uiCanvasW;
            startH = n.uiCanvasH;
            float cw = n.uiPercentW ? n.uiCanvasW / 100f * p.parentW : n.uiCanvasW;
            float ch = n.uiPercentH ? n.uiCanvasH / 100f * p.parentH : n.uiCanvasH;
            startLeft = decodeCanvasAxis(startPx, cw, p.parentW, n.uiPercentX);
            startTop = decodeCanvasAxis(startPy, ch, p.parentH, n.uiPercentY);
        } else if (n.type == SceneNodeType.UI_GRID) {
            // Grids are sized by their cells: scaling adjusts the cell size.
            startW = n.gridCellW;
            startH = n.gridCellH;
        } else {
            startW = n.uiW;
            startH = n.uiH;
        }
        if (!undoPushed) {
            SceneEditorSession.pushUndo();
            undoPushed = true;
        }
    }

    private static void applyDrag(float mx, float my, float k, Placed selected) {
        if (selected == null || !selected.node.id.equals(dragNodeId)) return;
        SceneNode n = selected.node;
        if (n.transform == null) return;
        float dx = (mx - dragMouseX) / k;
        float dy = (my - dragMouseY) / k;
        float ux = n.uiPercentX ? 100f / Math.max(1, selected.parentW) : 1f;
        float uy = n.uiPercentY ? 100f / Math.max(1, selected.parentH) : 1f;
        switch (drag) {
            case MOVE_X -> n.transform.px = Math.round(snapUnits(startPx, dx, ux));
            case MOVE_Y -> n.transform.py = Math.round(snapUnits(startPy, dy, uy));
            case MOVE_FREE -> {
                n.transform.px = Math.round(snapUnits(startPx, dx, ux));
                n.transform.py = Math.round(snapUnits(startPy, dy, uy));
            }
            case MOVE_CANVAS -> {
                // Work in absolute left/top pixels so we never cross the
                // 0=center / negative=from-right discontinuity (that jump is the edge jitter).
                n.transform.px = encodeCanvasAxis(snapUi(startLeft + dx), selected.parentW, n.uiPercentX);
                n.transform.py = encodeCanvasAxis(snapUi(startTop + dy), selected.parentH, n.uiPercentY);
            }
            case ROTATE -> {
                float cx = selected.sx + selected.sw / 2f;
                float cy = selected.sy + selected.sh / 2f;
                double a0 = Math.atan2(dragMouseY - cy, dragMouseX - cx);
                double a1 = Math.atan2(my - cy, mx - cx);
                float deg = (float) Math.toDegrees(a1 - a0);
                float value = startRz + deg;
                if (ImGui.getIO().getKeyShift()) value = Math.round(value / 15f) * 15f;
                n.transform.rz = Math.round(value * 10f) / 10f;
            }
            case SCALE_W, SCALE_H, SCALE_WH, SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL ->
                    applyScale(n, selected, dx, dy);
            default -> {}
        }
        SceneEditorSession.markDirty();
    }

    /** Shift locks aspect ratio (same idea as the world gizmo uniform cube). */
    private static void applyScale(SceneNode n, Placed selected, float dx, float dy) {
        boolean left = drag == Drag.SCALE_L || drag == Drag.SCALE_TL || drag == Drag.SCALE_BL;
        boolean top = drag == Drag.SCALE_T || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR;
        boolean width = drag == Drag.SCALE_W || drag == Drag.SCALE_WH || drag == Drag.SCALE_L
                || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR || drag == Drag.SCALE_BL;
        boolean height = drag == Drag.SCALE_H || drag == Drag.SCALE_WH || drag == Drag.SCALE_T
                || drag == Drag.SCALE_TL || drag == Drag.SCALE_TR || drag == Drag.SCALE_BL;
        float wf = widthFactor(n, selected);
        float hf = heightFactor(n, selected);
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
        if (width) setWidth(n, selected, newW);
        if (height) setHeight(n, selected, newH);

        // Left/top handles keep the opposite edge fixed.
        if (left) {
            float movedPx = (startW - storedWidth(n, selected)) / Math.max(0.0001f, wf);
            if (selected.isCanvas) {
                n.transform.px = encodeCanvasAxis(snapUi(startLeft + movedPx), selected.parentW, n.uiPercentX);
            } else {
                float ux = n.uiPercentX ? 100f / Math.max(1, selected.parentW) : 1f;
                n.transform.px = Math.round(startPx + movedPx * ux);
            }
        }
        if (top) {
            float movedPx = (startH - storedHeight(n, selected)) / Math.max(0.0001f, hf);
            if (selected.isCanvas) {
                n.transform.py = encodeCanvasAxis(snapUi(startTop + movedPx), selected.parentH, n.uiPercentY);
            } else {
                float uy = n.uiPercentY ? 100f / Math.max(1, selected.parentH) : 1f;
                n.transform.py = Math.round(startPy + movedPx * uy);
            }
        }
    }

    private static int storedWidth(SceneNode n, Placed p) {
        if (p.isCanvas) return n.uiCanvasW;
        return n.type == SceneNodeType.UI_GRID ? n.gridCellW : n.uiW;
    }

    private static int storedHeight(SceneNode n, Placed p) {
        if (p.isCanvas) return n.uiCanvasH;
        return n.type == SceneNodeType.UI_GRID ? n.gridCellH : n.uiH;
    }

    private static boolean isScaleDrag(Drag value) {
        return switch (value) {
            case SCALE_W, SCALE_H, SCALE_WH, SCALE_L, SCALE_T, SCALE_TL, SCALE_TR, SCALE_BL -> true;
            default -> false;
        };
    }

    /**
     * Moves/scales in canvas-pixel space with UI grid snapping, then converts
     * back to the node's stored unit (px or %). Alt bypasses snapping.
     */
    private static float snapUnits(float startUnits, float dPixels, float unitsPerPixel) {
        if (unitsPerPixel == 0f) return startUnits;
        float px = startUnits / unitsPerPixel + dPixels;
        return snapUi(px) * unitsPerPixel;
    }

    private static float snapUi(float px) {
        if (ImGui.getIO().getKeyAlt()) return px;
        return EditorGrid.snapUiPx(px);
    }

    /** Runtime canvas axis: 0 = centered, negative = inset from the right/bottom. */
    private static float decodeCanvasAxis(float raw, float size, int screen, boolean percent) {
        float v = percent ? raw / 100f * screen : raw;
        if (v == 0f) return (screen - size) / 2f;
        if (v < 0f) return screen + v - size;
        return v;
    }

    /**
     * Store a from-left coordinate. Never write 0 (center) or a negative
     * (from-right) while dragging — those encodings jump the panel to the
     * opposite edge.
     */
    private static float encodeCanvasAxis(float left, int screen, boolean percent) {
        float v = percent ? left / Math.max(1, screen) * 100f : left;
        int r = Math.round(v);
        if (r == 0) r = 1;
        if (r < 0) r = 1;
        return r;
    }

    /** Converts a screen-space (canvas px) width delta into the node's stored unit. */
    private static float widthFactor(SceneNode n, Placed p) {
        if (n.type == SceneNodeType.UI_GRID) {
            // Total grid width delta spreads evenly across columns.
            return 1f / Math.max(1, n.gridColumns);
        }
        return n.uiPercentW && !isPixelSized(n, p) ? 100f / Math.max(1, p.parentW) : 1f;
    }

    private static float heightFactor(SceneNode n, Placed p) {
        if (n.type == SceneNodeType.UI_GRID) {
            int count = 0;
            for (SceneNode c : n.children) if (c.type.isUiWidget()) count++;
            int rows = Math.max(1, (count + Math.max(1, n.gridColumns) - 1) / Math.max(1, n.gridColumns));
            return 1f / rows;
        }
        return n.uiPercentH && !isPixelSized(n, p) ? 100f / Math.max(1, p.parentH) : 1f;
    }

    /** Grid cells (and grid itself) are always stored in pixels. */
    private static boolean isPixelSized(SceneNode n, Placed p) {
        return n.type == SceneNodeType.UI_GRID;
    }

    private static void setWidth(SceneNode n, Placed p, int value) {
        if (p.isCanvas) n.uiCanvasW = Math.max(1, value);
        else if (n.type == SceneNodeType.UI_GRID) n.gridCellW = Math.max(1, value);
        else n.uiW = Math.max(1, value);
    }

    private static void setHeight(SceneNode n, Placed p, int value) {
        if (p.isCanvas) n.uiCanvasH = Math.max(1, value);
        else if (n.type == SceneNodeType.UI_GRID) n.gridCellH = Math.max(1, value);
        else n.uiH = Math.max(1, value);
    }

    private static String scaleLabel(Placed p) {
        SceneNode n = p.node;
        if (p.isCanvas) return n.uiCanvasW + " x " + n.uiCanvasH;
        if (n.type == SceneNodeType.UI_GRID) return n.gridCellW + " x " + n.gridCellH;
        return n.uiW + " x " + n.uiH;
    }

    /**
     * Draws the Unity-style gizmo for the selection and returns which handle is
     * hovered. Move: X/Y arrows + center square. Rotate: ring. Scale: edge and
     * corner handles.
     */
    private static Drag drawGizmo(ImDrawList dl, Placed p, float k, boolean active) {
        ImVec2 mouse = ImGui.getMousePos();
        float cx = p.sx + p.sw / 2f;
        float cy = p.sy + p.sh / 2f;
        // Selection outline.
        int selColor = ImGui.getColorU32(1f, 0.75f, 0.2f, 1f);
        float rz = p.node.transform != null ? (float) p.node.transform.rz : 0f;
        if (rz != 0f && !p.isCanvas) {
            float[][] q = rotatedCorners(cx, cy, p.sw, p.sh, rz);
            dl.addQuad(q[0][0], q[0][1], q[1][0], q[1][1], q[2][0], q[2][1], q[3][0], q[3][1], selColor, 2f);
        } else {
            dl.addRect(p.sx, p.sy, p.sx + p.sw, p.sy + p.sh, selColor, 0f, 0, 2f);
        }

        EditorGizmo.Mode mode = EditorGizmo.mode();
        Drag hovered = Drag.NONE;

        if (mode == EditorGizmo.Mode.MOVE || mode == EditorGizmo.Mode.SELECT) {
            float len = 44f;
            int red = ImGui.getColorU32(0.95f, 0.30f, 0.25f, 1f);
            int green = ImGui.getColorU32(0.35f, 0.85f, 0.35f, 1f);
            // Full cross through the widget center (not an L in the bottom-right).
            dl.addLine(cx - len, cy, cx + len, cy, red, 2.5f);
            dl.addTriangleFilled(cx + len + 8f, cy, cx + len, cy - 5f, cx + len, cy + 5f, red);
            dl.addTriangleFilled(cx - len - 8f, cy, cx - len, cy - 5f, cx - len, cy + 5f, red);
            dl.addLine(cx, cy - len, cx, cy + len, green, 2.5f);
            dl.addTriangleFilled(cx, cy + len + 8f, cx - 5f, cy + len, cx + 5f, cy + len, green);
            dl.addTriangleFilled(cx, cy - len - 8f, cx - 5f, cy - len, cx + 5f, cy - len, green);
            int boxColor = ImGui.getColorU32(0.95f, 0.85f, 0.3f, active && drag == Drag.MOVE_FREE ? 1f : 0.85f);
            dl.addRectFilled(cx - 5f, cy - 5f, cx + 5f, cy + 5f, boxColor);

            if (near(mouse.x, mouse.y, cx - 5f, cy - 5f, cx + 5f, cy + 5f)) {
                hovered = p.isCanvas ? Drag.MOVE_CANVAS : Drag.MOVE_FREE;
            } else if (pointNearSegment(mouse.x, mouse.y, cx - len - 8f, cy, cx + len + 8f, cy, 6f)) {
                hovered = Drag.MOVE_X;
            } else if (pointNearSegment(mouse.x, mouse.y, cx, cy - len - 8f, cx, cy + len + 8f, 6f)) {
                hovered = Drag.MOVE_Y;
            }
            if (p.isCanvas && hovered != Drag.NONE) hovered = Drag.MOVE_CANVAS;
        } else if (mode == EditorGizmo.Mode.ROTATE && !p.isCanvas) {
            float radius = Math.max(24f, Math.max(p.sw, p.sh) / 2f + 14f);
            int ring = ImGui.getColorU32(0.35f, 0.65f, 0.95f, 1f);
            dl.addCircle(cx, cy, radius, ring, 48, 2.5f);
            // Angle marker.
            double a = Math.toRadians(rz);
            dl.addLine(cx, cy, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius,
                    ImGui.getColorU32(0.35f, 0.65f, 0.95f, 0.6f), 1.5f);
            float dist = (float) Math.hypot(mouse.x - cx, mouse.y - cy);
            if (Math.abs(dist - radius) < 8f) hovered = Drag.ROTATE;
            if (active && drag == Drag.ROTATE) {
                dl.addText(cx + radius + 6f, cy - 6f, ring, String.format(java.util.Locale.ROOT, "%.1f°", rz));
            }
        } else if (mode == EditorGizmo.Mode.SCALE) {
            int blue = ImGui.getColorU32(0.35f, 0.65f, 0.95f, 1f);
            int corner = ImGui.getColorU32(0.95f, 0.85f, 0.3f, 1f);
            float hs = 5f;
            float x0 = p.sx, y0 = p.sy, x1 = p.sx + p.sw, y1 = p.sy + p.sh;
            float[][] handles = {
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
                float hx = handles[i][0], hy = handles[i][1];
                boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                dl.addRectFilled(hx - hs, hy - hs, hx + hs, hy + hs, isCorner ? corner : blue);
            }
            // Corners first so they win over edges when the box is small.
            for (int i = 0; i < handles.length; i++) {
                boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                if (!isCorner) continue;
                float hx = handles[i][0], hy = handles[i][1];
                if (near(mouse.x, mouse.y, hx - hs, hy - hs, hx + hs, hy + hs)) hovered = handleDrag[i];
            }
            if (hovered == Drag.NONE) {
                for (int i = 0; i < handles.length; i++) {
                    boolean isCorner = i == 0 || i == 2 || i == 5 || i == 7;
                    if (isCorner) continue;
                    float hx = handles[i][0], hy = handles[i][1];
                    if (near(mouse.x, mouse.y, hx - hs, hy - hs, hx + hs, hy + hs)) hovered = handleDrag[i];
                }
            }
            if (active && isScaleDrag(drag)) {
                dl.addText(x1 + 8f, y1 + 4f, blue, scaleLabel(p));
            }
        }
        return hovered;
    }

    // ---- math / colors -------------------------------------------------------

    private static float[][] rotatedCorners(float cx, float cy, float w, float h, float deg) {
        double a = Math.toRadians(deg);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        float hw = w / 2f, hh = h / 2f;
        float[][] local = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        float[][] out = new float[4][2];
        for (int i = 0; i < 4; i++) {
            out[i][0] = cx + local[i][0] * cos - local[i][1] * sin;
            out[i][1] = cy + local[i][0] * sin + local[i][1] * cos;
        }
        return out;
    }

    private static boolean near(float mx, float my, float x0, float y0, float x1, float y1) {
        return mx >= x0 - 2f && mx <= x1 + 2f && my >= y0 - 2f && my <= y1 + 2f;
    }

    private static boolean pointNearSegment(float px, float py, float x0, float y0,
                                            float x1, float y1, float tolerance) {
        float dx = x1 - x0, dy = y1 - y0;
        float lengthSq = dx * dx + dy * dy;
        float t = lengthSq == 0 ? 0 : Math.max(0f, Math.min(1f, ((px - x0) * dx + (py - y0) * dy) / lengthSq));
        float nx = x0 + t * dx, ny = y0 + t * dy;
        return Math.hypot(px - nx, py - ny) <= tolerance;
    }

}
