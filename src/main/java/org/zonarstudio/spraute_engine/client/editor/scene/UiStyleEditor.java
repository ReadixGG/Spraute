package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiColorEditFlags;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Pro-style GUI controls for the inspector: alpha-aware color pickers,
 * a gradient editor (angle + multiple stops + live preview) and an
 * interactive canvas preview with drag-to-move widgets.
 */
final class UiStyleEditor {
    private UiStyleEditor() {}

    private static final float[] colorTmp = new float[4];
    private static String dragNodeId;
    private static float dragStartPx, dragStartPy;

    // ---- color picker -------------------------------------------------------

    /** Color swatch + picker with alpha bar; value stored as "#AARRGGBB". */
    static void colorField(String label, String id, String current, Consumer<String> setter) {
        colorField(label, id, current, setter, null, null);
    }

    static void colorField(String label, String id, String current, Consumer<String> setter,
                           String description, String scriptApi) {
        EditorInspectorUi.fieldLabel(label, description, scriptApi);
        float[] rgba = hexToRgba(current);
        System.arraycopy(rgba, 0, colorTmp, 0, 4);
        int flags = ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf
                | ImGuiColorEditFlags.DisplayHex | ImGuiColorEditFlags.PickerHueBar;
        ImGui.setNextItemWidth(-1f);
        if (ImGui.colorEdit4("##col-" + id, colorTmp, flags)) {
            setter.accept(rgbaToHex(colorTmp));
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.scriptApi(description, scriptApi);
        EditorInspectorUi.undoOnActivate();
    }

    /** Opaque color picker; value stored as {@code #RRGGBB} (for world sky/fog etc.). */
    static void colorFieldRgb(String label, String id, String current, Consumer<String> setter) {
        EditorInspectorUi.fieldLabel(label);
        float[] rgba = hexToRgba(current);
        System.arraycopy(rgba, 0, colorTmp, 0, 4);
        int flags = ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.DisplayHex
                | ImGuiColorEditFlags.PickerHueBar;
        ImGui.setNextItemWidth(-1f);
        if (ImGui.colorEdit4("##col-rgb-" + id, colorTmp, flags)) {
            setter.accept(rgbToHex(colorTmp));
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    /**
     * Color swatch + picker; value stored as {@code #RRGGBBAA} (particles).
     * @return true if the user changed the color
     */
    static boolean colorEditRgba(String label, String id, String current, Consumer<String> setter) {
        EditorInspectorUi.fieldLabel(label);
        float[] rgba = hexToRgbaPacked(current);
        System.arraycopy(rgba, 0, colorTmp, 0, 4);
        int flags = ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf
                | ImGuiColorEditFlags.DisplayHex | ImGuiColorEditFlags.PickerHueBar;
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.colorEdit4("##col-rgba-" + id, colorTmp, flags);
        if (changed) setter.accept(rgbaToRgbaHex(colorTmp));
        EditorInspectorUi.undoOnActivate();
        return changed;
    }

    /** {@code #RRGGBB} / {@code #RRGGBBAA} → ImGui RGBA floats. */
    static float[] hexToRgbaPacked(String hex) {
        int r = 255, g = 255, b = 255, a = 255;
        if (hex != null) {
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                if (s.length() == 6) {
                    int v = (int) Long.parseLong(s, 16);
                    r = (v >> 16) & 255;
                    g = (v >> 8) & 255;
                    b = v & 255;
                } else if (s.length() == 8) {
                    long v = Long.parseLong(s, 16);
                    r = (int) ((v >> 24) & 255);
                    g = (int) ((v >> 16) & 255);
                    b = (int) ((v >> 8) & 255);
                    a = (int) (v & 255);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
    }

    static String rgbaToRgbaHex(float[] rgba) {
        int r = Math.round(Math.max(0f, Math.min(1f, rgba[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, rgba[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, rgba[2])) * 255f);
        int a = Math.round(Math.max(0f, Math.min(1f, rgba[3])) * 255f);
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", r, g, b, a);
    }

    // ---- gradient editor ----------------------------------------------------

    /**
     * Gradient block: enable toggle, live preview strip, angle control and a
     * stop list. When enabled, the gradient fully replaces the main color:
     * both the start (uiGradientStart) and end (uiGradient) colors are edited
     * right here, extras in uiGradientStops.
     */
    static void gradientEditor(SceneNode node, String idPrefix) {
        boolean enabled = node.uiGradient != null && !node.uiGradient.isBlank();
        if (EditorInspectorUi.checkbox(EditorI18n.t("Градиент"), enabled)) {
            SceneEditorSession.pushUndo();
            if (enabled) {
                node.uiGradient = "";
                node.uiGradientStart = "";
                node.uiGradientStops = "";
            } else {
                // Seed the two initial colors: start = the widget's current color.
                node.uiGradientStart = orDefault(node.uiColor, "#FFFFFFFF");
                node.uiGradient = "#FF2B4C7E";
                if (node.uiGradientAngle < 0f) node.uiGradientAngle = 90f;
            }
            SceneEditorSession.markDirty();
            enabled = !enabled;
        }
        if (!enabled) return;
        // Legacy scenes without a start color: adopt the base color once.
        if (node.uiGradientStart == null || node.uiGradientStart.isBlank()) {
            node.uiGradientStart = orDefault(node.uiColor, "#FFFFFFFF");
        }
        ImGui.textDisabled(EditorI18n.t("Основной цвет не используется — все цвета задаются здесь"));

        // Full stop chain: start color -> mid stops -> end color.
        List<String> stops = new ArrayList<>();
        stops.add(node.uiGradientStart.trim());
        stops.addAll(parseStops(node.uiGradientStops));
        stops.add(node.uiGradient.trim());

        drawGradientPreview(stops, currentAngle(node));

        float[] angle = {currentAngle(node)};
        EditorInspectorUi.fieldLabel(EditorI18n.t("Угол (0° = →, 90° = ↓)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderFloat("##" + idPrefix + "-grad-angle", angle, 0f, 360f, "%.0f°")) {
            node.uiGradientAngle = angle[0];
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();

        ImGui.textDisabled(EditorI18n.t("Цвета (слева → направо):"));

        // Start color.
        if (colorRow(idPrefix + "-grad-start", node.uiGradientStart, value -> node.uiGradientStart = value)) {
            SceneEditorSession.markDirty();
        }

        // Mid stops with delete buttons.
        List<String> mids = parseStops(node.uiGradientStops);
        boolean midsChanged = false;
        for (int i = 0; i < mids.size(); i++) {
            float[] rgba = hexToRgba(mids.get(i));
            System.arraycopy(rgba, 0, colorTmp, 0, 4);
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 30f);
            if (ImGui.colorEdit4("##" + idPrefix + "-grad-mid-" + i, colorTmp,
                    ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.DisplayHex)) {
                mids.set(i, rgbaToHex(colorTmp));
                midsChanged = true;
            }
            EditorInspectorUi.undoOnActivate();
            ImGui.sameLine();
            if (ImGui.smallButton("x##" + idPrefix + "-grad-del-" + i)) {
                SceneEditorSession.pushUndo();
                mids.remove(i);
                midsChanged = true;
                i--;
            }
        }
        if (midsChanged) {
            node.uiGradientStops = String.join(",", mids);
            SceneEditorSession.markDirty();
        }

        // End color of the gradient.
        if (colorRow(idPrefix + "-grad-end", node.uiGradient, value -> node.uiGradient = value)) {
            SceneEditorSession.markDirty();
        }

        if (ImGui.smallButton(EditorI18n.t("+ Добавить цвет##") + idPrefix)) {
            SceneEditorSession.pushUndo();
            List<String> list = parseStops(node.uiGradientStops);
            list.add(node.uiGradient.trim());
            node.uiGradientStops = String.join(",", list);
            SceneEditorSession.markDirty();
        }
    }

    /**
     * Border gradient block: enable toggle, preview, angle and a full stop list
     * stored in uiBorderGradient (comma-separated, min 2 colors). When enabled,
     * the solid border color is ignored.
     */
    static void borderGradientEditor(SceneNode node, String idPrefix) {
        boolean enabled = node.uiBorderGradient != null && !node.uiBorderGradient.isBlank();
        if (EditorInspectorUi.checkbox(EditorI18n.t("Градиент рамки"), enabled)) {
            SceneEditorSession.pushUndo();
            if (enabled) {
                node.uiBorderGradient = "";
            } else {
                // Seed the two initial colors: start = the current solid border color.
                node.uiBorderGradient = orDefault(node.uiBorderColor, "#66FFFFFF") + ",#FF2B4C7E";
            }
            SceneEditorSession.markDirty();
            enabled = !enabled;
        }
        if (!enabled) return;
        ImGui.textDisabled(EditorI18n.t("Цвет рамки не используется — все цвета задаются здесь"));

        List<String> stops = parseStops(node.uiBorderGradient);
        while (stops.size() < 2) stops.add("#FF2B4C7E");

        drawGradientPreview(stops, node.uiBorderGradientAngle);

        float[] angle = {node.uiBorderGradientAngle};
        EditorInspectorUi.fieldLabel(EditorI18n.t("Угол (0° = →, 90° = ↓)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderFloat("##" + idPrefix + "-bgrad-angle", angle, 0f, 360f, "%.0f°")) {
            node.uiBorderGradientAngle = angle[0];
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();

        ImGui.textDisabled(EditorI18n.t("Цвета (слева → направо):"));
        boolean changed = false;
        for (int i = 0; i < stops.size(); i++) {
            float[] rgba = hexToRgba(stops.get(i));
            System.arraycopy(rgba, 0, colorTmp, 0, 4);
            boolean removable = stops.size() > 2;
            ImGui.setNextItemWidth(removable ? ImGui.getContentRegionAvailX() - 30f : -1f);
            if (ImGui.colorEdit4("##" + idPrefix + "-bgrad-" + i, colorTmp,
                    ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.DisplayHex)) {
                stops.set(i, rgbaToHex(colorTmp));
                changed = true;
            }
            EditorInspectorUi.undoOnActivate();
            if (removable) {
                ImGui.sameLine();
                if (ImGui.smallButton("x##" + idPrefix + "-bgrad-del-" + i)) {
                    SceneEditorSession.pushUndo();
                    stops.remove(i);
                    changed = true;
                    i--;
                }
            }
        }
        if (ImGui.smallButton(EditorI18n.t("+ Добавить цвет##") + idPrefix + "-bgrad")) {
            SceneEditorSession.pushUndo();
            stops.add(stops.get(stops.size() - 1));
            changed = true;
        }
        if (changed) {
            node.uiBorderGradient = String.join(",", stops);
            SceneEditorSession.markDirty();
        }
    }

    /**
     * Outer outline gradient: same UI as {@link #borderGradientEditor}, bound to
     * {@code uiOuterBorderGradient} / {@code uiOuterBorderGradientAngle}.
     */
    static void outerBorderGradientEditor(SceneNode node, String idPrefix) {
        boolean enabled = node.uiOuterBorderGradient != null && !node.uiOuterBorderGradient.isBlank();
        if (EditorInspectorUi.checkbox(EditorI18n.t("Градиент обводки"), enabled)) {
            SceneEditorSession.pushUndo();
            if (enabled) {
                node.uiOuterBorderGradient = "";
            } else {
                node.uiOuterBorderGradient = orDefault(node.uiOuterBorderColor, "#66FFFFFF") + ",#FF2B4C7E";
            }
            SceneEditorSession.markDirty();
            enabled = !enabled;
        }
        if (!enabled) return;
        ImGui.textDisabled(EditorI18n.t("Цвет обводки не используется — все цвета задаются здесь"));

        List<String> stops = parseStops(node.uiOuterBorderGradient);
        while (stops.size() < 2) stops.add("#FF2B4C7E");

        drawGradientPreview(stops, node.uiOuterBorderGradientAngle);

        float[] angle = {node.uiOuterBorderGradientAngle};
        EditorInspectorUi.fieldLabel(EditorI18n.t("Угол (0° = →, 90° = ↓)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderFloat("##" + idPrefix + "-obgrad-angle", angle, 0f, 360f, "%.0f°")) {
            node.uiOuterBorderGradientAngle = angle[0];
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();

        ImGui.textDisabled(EditorI18n.t("Цвета (слева → направо):"));
        boolean changed = false;
        for (int i = 0; i < stops.size(); i++) {
            float[] rgba = hexToRgba(stops.get(i));
            System.arraycopy(rgba, 0, colorTmp, 0, 4);
            boolean removable = stops.size() > 2;
            ImGui.setNextItemWidth(removable ? ImGui.getContentRegionAvailX() - 30f : -1f);
            if (ImGui.colorEdit4("##" + idPrefix + "-obgrad-" + i, colorTmp,
                    ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.DisplayHex)) {
                stops.set(i, rgbaToHex(colorTmp));
                changed = true;
            }
            EditorInspectorUi.undoOnActivate();
            if (removable) {
                ImGui.sameLine();
                if (ImGui.smallButton("x##" + idPrefix + "-obgrad-del-" + i)) {
                    SceneEditorSession.pushUndo();
                    stops.remove(i);
                    changed = true;
                    i--;
                }
            }
        }
        if (ImGui.smallButton(EditorI18n.t("+ Добавить цвет##") + idPrefix + "-obgrad")) {
            SceneEditorSession.pushUndo();
            stops.add(stops.get(stops.size() - 1));
            changed = true;
        }
        if (changed) {
            node.uiOuterBorderGradient = String.join(",", stops);
            SceneEditorSession.markDirty();
        }
    }

    /**
     * Text gradient: same stop list as panel/border gradients, stored in
     * {@code uiTextGradient}. No angle — colors run along the string.
     */
    static void textGradientEditor(SceneNode node, String idPrefix) {
        boolean enabled = node.uiTextGradient != null && !node.uiTextGradient.isBlank();
        if (EditorInspectorUi.checkbox(EditorI18n.t("Градиент текста"), enabled,
                EditorI18n.t("Цвета слева направо по строке. Основной цвет текста не используется."),
                "_node.uiTextGradient = \"#FFFF5555,#FF55AAFF\"\n"
                        + "uiUpdate(player, \"" + node.id + "\", \"textGradient\", \"#FFFF5555,#FF55AAFF\")")) {
            SceneEditorSession.pushUndo();
            if (enabled) {
                node.uiTextGradient = "";
            } else {
                node.uiTextGradient = orDefault(node.uiColor, "#FFEAEAEA") + ",#FF55AAFF";
            }
            SceneEditorSession.markDirty();
            enabled = !enabled;
        }
        if (!enabled) return;
        EditorInspectorUi.hint(EditorI18n.t("Цвета слева направо по тексту · основной цвет не используется"));

        List<String> stops = parseStops(node.uiTextGradient);
        while (stops.size() < 2) stops.add("#FF55AAFF");

        drawGradientPreview(stops, 0f, false);

        ImGui.textDisabled(EditorI18n.t("Цвета (слева → направо):"));
        boolean changed = false;
        for (int i = 0; i < stops.size(); i++) {
            float[] rgba = hexToRgba(stops.get(i));
            System.arraycopy(rgba, 0, colorTmp, 0, 4);
            boolean removable = stops.size() > 2;
            ImGui.setNextItemWidth(removable ? ImGui.getContentRegionAvailX() - 30f : -1f);
            if (ImGui.colorEdit4("##" + idPrefix + "-tgrad-" + i, colorTmp,
                    ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.DisplayHex)) {
                stops.set(i, rgbaToHex(colorTmp));
                changed = true;
            }
            EditorInspectorUi.undoOnActivate();
            if (removable) {
                ImGui.sameLine();
                if (ImGui.smallButton("x##" + idPrefix + "-tgrad-del-" + i)) {
                    SceneEditorSession.pushUndo();
                    stops.remove(i);
                    changed = true;
                    i--;
                }
            }
        }
        if (ImGui.smallButton(EditorI18n.t("+ Добавить цвет##") + idPrefix + "-tgrad")) {
            SceneEditorSession.pushUndo();
            stops.add(stops.get(stops.size() - 1));
            changed = true;
        }
        if (changed) {
            node.uiTextGradient = String.join(",", stops);
            SceneEditorSession.markDirty();
        }
    }

    /** Full-width color edit row bound to a hex string; returns true when edited. */
    private static boolean colorRow(String id, String current, Consumer<String> setter) {
        float[] rgba = hexToRgba(current);
        System.arraycopy(rgba, 0, colorTmp, 0, 4);
        ImGui.setNextItemWidth(-1f);
        boolean edited = ImGui.colorEdit4("##" + id, colorTmp,
                ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf | ImGuiColorEditFlags.DisplayHex);
        if (edited) setter.accept(rgbaToHex(colorTmp));
        EditorInspectorUi.undoOnActivate();
        return edited;
    }

    private static float currentAngle(SceneNode node) {
        if (node.uiGradientAngle >= 0f) return node.uiGradientAngle;
        return "h".equalsIgnoreCase(node.uiGradientDir) ? 0f : 90f;
    }

    /** Preview strip rendered with the draw list; angle shown by an arrow overlay. */
    private static void drawGradientPreview(List<String> stops, float angleDeg) {
        drawGradientPreview(stops, angleDeg, true);
    }

    private static void drawGradientPreview(List<String> stops, float angleDeg, boolean arrow) {
        float w = ImGui.getContentRegionAvailX();
        float h = 26f;
        ImVec2 pos = ImGui.getCursorScreenPos();
        ImDrawList dl = ImGui.getWindowDrawList();
        int n = Math.max(1, stops.size() - 1);
        for (int i = 0; i < n; i++) {
            float x0 = pos.x + w * i / n;
            float x1 = pos.x + w * (i + 1) / n;
            int c0 = imguiColor(stops.get(i));
            int c1 = imguiColor(stops.get(Math.min(i + 1, stops.size() - 1)));
            dl.addRectFilledMultiColor(x0, pos.y, x1, pos.y + h, c0, c1, c1, c0);
        }
        dl.addRect(pos.x, pos.y, pos.x + w, pos.y + h, ImGui.getColorU32(1f, 1f, 1f, 0.25f));
        if (arrow) {
            float cx = pos.x + w * 0.5f, cy = pos.y + h * 0.5f;
            double a = Math.toRadians(angleDeg);
            float dx = (float) Math.cos(a) * 12f, dy = (float) Math.sin(a) * 12f;
            int arrowColor = ImGui.getColorU32(1f, 1f, 1f, 0.9f);
            dl.addLine(cx - dx, cy - dy, cx + dx, cy + dy, arrowColor, 2f);
            float hx = cx + dx, hy = cy + dy;
            float bx = -dx / 12f, by = -dy / 12f;
            dl.addTriangleFilled(hx, hy,
                    hx + (bx * 6f - by * 3.5f), hy + (by * 6f + bx * 3.5f),
                    hx + (bx * 6f + by * 3.5f), hy + (by * 6f - bx * 3.5f), arrowColor);
        }
        ImGui.dummy(w, h + 4f);
    }

    static void drawInlineGradientPreview(List<String> stops) {
        drawGradientPreview(stops, 0f, false);
    }

    // ---- canvas preview -----------------------------------------------------

    /**
     * Interactive preview of a canvas: draws the canvas and its widgets scaled
     * to fit, click selects a widget, dragging moves it (updates transform px/py).
     */
    static void canvasPreview(SceneDocument doc, SceneNode canvas) {
        float availW = Math.max(60f, ImGui.getContentRegionAvailX());
        int cw = Math.max(4, canvas.uiCanvasW);
        int ch = Math.max(4, canvas.uiCanvasH);
        float k = Math.min(availW / cw, 260f / ch);
        float pw = cw * k, ph = ch * k;
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList dl = ImGui.getWindowDrawList();

        // Canvas background (checker pattern under transparent colors).
        drawChecker(dl, origin.x, origin.y, pw, ph);
        int bg = imguiColor(orDefault(canvas.uiColor, "#C0101010"));
        float rounding = canvas.uiRadius * k;
        dl.addRectFilled(origin.x, origin.y, origin.x + pw, origin.y + ph, bg, rounding);
        dl.addRect(origin.x, origin.y, origin.x + pw, origin.y + ph,
                ImGui.getColorU32(1f, 1f, 1f, 0.3f), rounding);

        // Reserve the area and use an invisible button as the interaction surface.
        ImGui.invisibleButton("##canvas-preview-" + canvas.id, pw, ph);

        String selectedId = SceneEditorSession.selectedNodeId();
        for (SceneNode child : canvas.children) {
            drawPreviewWidget(dl, doc, child, origin.x, origin.y, k, 0f, 0f, cw, ch, selectedId);
        }
        ImGui.textDisabled(String.format(Locale.ROOT, EditorI18n.t("Превью %d×%d px · клик — выбрать, тянуть — двигать"), cw, ch));
    }

    private static void drawPreviewWidget(ImDrawList dl, SceneDocument doc, SceneNode node,
                                          float ox, float oy, float k,
                                          float offX, float offY, int parentW, int parentH,
                                          String selectedId) {
        if (!node.type.isUiWidget()) return;
        float rawX = node.transform != null ? (float) node.transform.px : 0f;
        float rawY = node.transform != null ? (float) node.transform.py : 0f;
        if (node.uiPercentX) rawX = rawX / 100f * parentW;
        if (node.uiPercentY) rawY = rawY / 100f * parentH;
        float x = offX + rawX;
        float y = offY + rawY;
        float w = node.uiPercentW ? node.uiW / 100f * parentW : node.uiW;
        float h = node.uiPercentH ? node.uiH / 100f * parentH : node.uiH;
        if (node.type == SceneNodeType.UI_TEXT) {
            w = Math.max(w, node.uiText == null ? 30 : node.uiText.length() * 5f * node.uiTextScale);
            h = Math.max(h, 9f * node.uiTextScale);
        }
        if (node.type == SceneNodeType.UI_GRID) {
            int count = 0;
            for (SceneNode c : node.children) if (c.type.isUiWidget()) count++;
            if (count <= 0) count = 1;
            String mode = node.gridMode == null ? "grid" : node.gridMode;
            if ("row".equalsIgnoreCase(mode)) {
                int cellW = node.gridHugContent ? Math.max(1, node.uiW / Math.max(1, count)) : Math.max(1, node.gridCellW);
                int cellH = node.gridHugContent ? Math.max(1, node.uiH) : Math.max(1, node.gridCellH);
                w = Math.max(w, count * cellW + Math.max(0, count - 1) * node.gridGapX);
                h = Math.max(h, cellH);
            } else if ("column".equalsIgnoreCase(mode)) {
                int cellW = node.gridHugContent ? Math.max(1, node.uiW) : Math.max(1, node.gridCellW);
                int cellH = node.gridHugContent ? Math.max(1, node.uiH / Math.max(1, count)) : Math.max(1, node.gridCellH);
                w = Math.max(w, cellW);
                h = Math.max(h, count * cellH + Math.max(0, count - 1) * node.gridGapY);
            } else {
                w = Math.max(w, node.gridColumns * (node.gridCellW + node.gridGapX));
                h = Math.max(h, node.gridCellH + node.gridGapY);
            }
        }

        float sx = ox + x * k, sy = oy + y * k;
        float sw = Math.max(2f, w * k), sh = Math.max(2f, h * k);
        int fill = switch (node.type) {
            case UI_TEXT -> 0;
            case UI_DIVIDER, UI_RECT, UI_BUTTON, UI_SCROLL, UI_INPUT -> imguiColor(orDefault(node.uiColor, "#80FFFFFF"));
            case UI_IMAGE, UI_VIDEO, UI_SVG, UI_ITEM, UI_BLOCK, UI_ENTITY -> ImGui.getColorU32(0.5f, 0.6f, 0.8f, 0.35f);
            case UI_GRID -> ImGui.getColorU32(0.9f, 0.8f, 0.4f, 0.12f);
            default -> ImGui.getColorU32(1f, 1f, 1f, 0.2f);
        };
        float r = node.uiRadius * k;
        if (fill != 0) dl.addRectFilled(sx, sy, sx + sw, sy + sh, fill, r);
        if (node.type == SceneNodeType.UI_TEXT || node.type == SceneNodeType.UI_BUTTON) {
            String label = node.uiText == null ? "" : node.uiText;
            int tc = node.type == SceneNodeType.UI_TEXT
                    ? imguiColor(orDefault(node.uiColor, "#FFEAEAEA"))
                    : ImGui.getColorU32(1f, 1f, 1f, 0.95f);
            dl.addText(sx + 2f + node.uiTextOffsetX * k, sy + Math.max(0f, (sh - ImGui.getTextLineHeight()) / 2f) + node.uiTextOffsetY * k, tc, label);
        }
        boolean selected = node.id.equals(selectedId);
        dl.addRect(sx, sy, sx + sw, sy + sh,
                selected ? ImGui.getColorU32(1f, 0.75f, 0.2f, 1f) : ImGui.getColorU32(1f, 1f, 1f, 0.35f),
                r, 0, selected ? 2f : 1f);

        // Interaction: click to select, drag to move (px units on the canvas).
        ImVec2 mouse = ImGui.getMousePos();
        boolean hovered = mouse.x >= sx && mouse.x <= sx + sw && mouse.y >= sy && mouse.y <= sy + sh;
        if (hovered && ImGui.isMouseClicked(0)) {
            SceneEditorSession.select(node.id);
            dragNodeId = node.id;
            dragStartPx = node.transform != null ? (float) node.transform.px : 0f;
            dragStartPy = node.transform != null ? (float) node.transform.py : 0f;
            SceneEditorSession.pushUndo();
        }
        if (node.id.equals(dragNodeId)) {
            if (ImGui.isMouseDragging(0)) {
                ImVec2 delta = ImGui.getMouseDragDelta(0);
                float unitKx = node.uiPercentX ? 100f / Math.max(1, parentW) : 1f;
                float unitKy = node.uiPercentY ? 100f / Math.max(1, parentH) : 1f;
                if (node.transform != null) {
                    node.transform.px = Math.round(dragStartPx + delta.x / k * unitKx);
                    node.transform.py = Math.round(dragStartPy + delta.y / k * unitKy);
                    SceneEditorSession.markDirty();
                }
            } else if (!ImGui.isMouseDown(0)) {
                dragNodeId = null;
            }
        }

        for (SceneNode child : node.children) {
            drawPreviewWidget(dl, doc, child, ox, oy, k, x, y, Math.round(w), Math.round(h), selectedId);
        }
    }

    private static void drawChecker(ImDrawList dl, float x, float y, float w, float h) {
        float cell = 8f;
        int c1 = ImGui.getColorU32(0.22f, 0.22f, 0.25f, 1f);
        int c2 = ImGui.getColorU32(0.16f, 0.16f, 0.18f, 1f);
        for (float yy = 0; yy < h; yy += cell) {
            for (float xx = 0; xx < w; xx += cell) {
                boolean odd = (((int) (xx / cell)) + ((int) (yy / cell))) % 2 == 0;
                dl.addRectFilled(x + xx, y + yy,
                        Math.min(x + xx + cell, x + w), Math.min(y + yy + cell, y + h),
                        odd ? c1 : c2);
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    static List<String> parseStops(String stops) {
        List<String> list = new ArrayList<>();
        if (stops == null || stops.isBlank()) return list;
        for (String part : stops.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    /** "#AARRGGBB" or "#RRGGBB" -> ImGui float RGBA. */
    static float[] hexToRgba(String hex) {
        long v = 0xFFFFFFFFL;
        if (hex != null) {
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            try {
                if (s.length() == 6) {
                    v = 0xFF000000L | Long.parseLong(s, 16);
                } else if (s.length() == 8) {
                    v = Long.parseLong(s, 16);
                }
            } catch (NumberFormatException ignored) {}
        }
        return new float[]{
                ((v >> 16) & 0xFF) / 255f,
                ((v >> 8) & 0xFF) / 255f,
                (v & 0xFF) / 255f,
                ((v >> 24) & 0xFF) / 255f
        };
    }

    static String rgbaToHex(float[] rgba) {
        int a = Math.round(Math.max(0f, Math.min(1f, rgba[3])) * 255f);
        int r = Math.round(Math.max(0f, Math.min(1f, rgba[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, rgba[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, rgba[2])) * 255f);
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", a, r, g, b);
    }

    static String rgbToHex(float[] rgba) {
        int r = Math.round(Math.max(0f, Math.min(1f, rgba[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, rgba[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, rgba[2])) * 255f);
        return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    }

    private static int imguiColor(String hex) {
        float[] c = hexToRgba(hex);
        return ImGui.getColorU32(c[0], c[1], c[2], c[3]);
    }

    private static String orDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }
}
