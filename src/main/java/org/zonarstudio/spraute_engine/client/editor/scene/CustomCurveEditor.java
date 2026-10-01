package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.scene.custom.ScriptCurve;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Inspector graph for {@code field … curve} on custom nodes. */
final class CustomCurveEditor {
    private static final float GRAPH_H = 110f;
    private static final float HIT_R = 9f;
    private static final String[] EASE_IDS = {
            "linear", "easeInOut", "easeOut", "easeIn", "bounceOut", "elasticOut"
    };

    private String dragId = "";
    private int dragIndex = -1;
    private boolean dragMoved = false;
    private String selectId = "";
    private int selectIndex = 0;
    private final float[] tBuf = new float[1];
    private final float[] vBuf = new float[1];

    boolean render(String id, Object current, Consumer<Object> onChange) {
        List<Map<String, Object>> keys = ScriptCurve.normalize(current);
        if (keys.size() < 2) keys = ScriptCurve.identity();
        if (!id.equals(selectId)) {
            selectId = id;
            selectIndex = 0;
            dragIndex = -1;
            dragMoved = false;
        }
        if (selectIndex < 0 || selectIndex >= keys.size()) selectIndex = 0;

        boolean changed = false;
        float w = Math.max(80f, ImGui.getContentRegionAvailX());
        float h = GRAPH_H;
        ImGui.invisibleButton("##curve-graph-" + id, w, h);
        float x0 = ImGui.getItemRectMinX();
        float y0 = ImGui.getItemRectMinY();
        float x1 = ImGui.getItemRectMaxX();
        float y1 = ImGui.getItemRectMaxY();
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemActivated()) SceneEditorSession.pushUndo();

        double[] range = valueRange(keys);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x1, y1, pack(0.09f, 0.10f, 0.13f, 1f), 5f);
        dl.addRect(x0, y0, x1, y1, pack(0.32f, 0.36f, 0.42f, 0.55f), 5f, 0, 1f);
        for (int g = 1; g < 4; g++) {
            float gx = x0 + (x1 - x0) * (g / 4f);
            float gy = y0 + (y1 - y0) * (g / 4f);
            dl.addLine(gx, y0 + 2, gx, y1 - 2, pack(0.22f, 0.24f, 0.28f, 0.7f), 1f);
            dl.addLine(x0 + 2, gy, x1 - 2, gy, pack(0.22f, 0.24f, 0.28f, 0.7f), 1f);
        }

        int samples = 48;
        float prevX = Float.NaN, prevY = Float.NaN;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double v = ScriptCurve.eval(keys, t);
            float px = toX(t, x0, x1);
            float py = toY(v, range[0], range[1], y0, y1);
            if (!Float.isNaN(prevX)) {
                dl.addLine(prevX, prevY, px, py, pack(0.45f, 0.72f, 1f, 1f), 1.8f);
            }
            prevX = px;
            prevY = py;
        }

        int hit = hovered ? hitKey(keys, range, x0, y0, x1, y1) : -1;
        if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            if (hit >= 0) {
                selectIndex = hit;
                dragIndex = hit;
                dragId = id;
                dragMoved = false;
            } else {
                double t = fromX(ImGui.getMousePosX(), x0, x1);
                double v = fromY(ImGui.getMousePosY(), range[0], range[1], y0, y1);
                keys.add(ScriptCurve.key(t, v, "linear"));
                keys = ScriptCurve.normalize(keys);
                selectIndex = nearestIndex(keys, t);
                dragIndex = selectIndex;
                dragId = id;
                dragMoved = true;
                changed = true;
            }
        }
        if (hovered && hit >= 0 && ImGui.isMouseClicked(ImGuiMouseButton.Right) && keys.size() > 2) {
            keys.remove(hit);
            keys = ScriptCurve.normalize(keys);
            selectIndex = Math.min(selectIndex, keys.size() - 1);
            changed = true;
        }
        if (id.equals(dragId) && dragIndex >= 0 && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            if (dragIndex < keys.size()) {
                double t = fromX(ImGui.getMousePosX(), x0, x1);
                double v = fromY(ImGui.getMousePosY(), range[0], range[1], y0, y1);
                Map<String, Object> k = keys.get(dragIndex);
                double nt = clamp01(t);
                if (Math.abs(nt - ScriptCurve.num(k, "t", 0)) > 1e-4
                        || Math.abs(v - ScriptCurve.num(k, "v", 0)) > 1e-4) {
                    k.put("t", nt);
                    k.put("v", v);
                    dragMoved = true;
                    changed = true;
                }
            }
        }
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            if (id.equals(dragId) && dragIndex >= 0) {
                keys = ScriptCurve.normalize(keys);
                selectIndex = Math.min(selectIndex, keys.size() - 1);
                if (dragMoved) changed = true;
            }
            dragIndex = -1;
            dragId = "";
            dragMoved = false;
        }

        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> k = keys.get(i);
            float px = toX(ScriptCurve.num(k, "t", 0), x0, x1);
            float py = toY(ScriptCurve.num(k, "v", 0), range[0], range[1], y0, y1);
            boolean sel = i == selectIndex;
            float r = sel ? 6.5f : 5f;
            int fill = sel ? pack(1f, 0.78f, 0.32f, 1f) : pack(0.92f, 0.94f, 0.98f, 1f);
            dl.addCircleFilled(px, py, r, fill, 12);
            dl.addCircle(px, py, r, pack(0.12f, 0.13f, 0.16f, 1f), 12, 1.3f);
        }

        EditorInspectorUi.hint(EditorI18n.t("Клик — точка · перетащить · ПКМ — удалить"));

        ImGui.pushStyleColor(ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 0.9f);
        boolean preset = presetButton(EditorI18n.t("линейно"), "linear", onChange);
        ImGui.sameLine();
        preset |= presetButton(EditorI18n.t("плавно"), "easeInOut", onChange);
        ImGui.sameLine();
        preset |= presetButton(EditorI18n.t("в начале"), "easeIn", onChange);
        ImGui.sameLine();
        preset |= presetButton(EditorI18n.t("в конце"), "easeOut", onChange);
        ImGui.popStyleColor();
        if (preset) return true;

        Map<String, Object> sel = keys.get(selectIndex);
        tBuf[0] = (float) ScriptCurve.num(sel, "t", 0);
        vBuf[0] = (float) ScriptCurve.num(sel, "v", 0);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Точка") + " " + (selectIndex + 1) + "/" + keys.size(),
                "_node." + id + ".eval(t)");
        ImGui.setNextItemWidth(Math.max(60f, (w - 8f) * 0.5f));
        if (ImGui.dragFloat("##ct-" + id, tBuf, 0.01f, 0f, 1f, "t %.3f")) {
            SceneEditorSession.pushUndo();
            sel.put("t", (double) tBuf[0]);
            keys = ScriptCurve.normalize(keys);
            changed = true;
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(Math.max(60f, (w - 8f) * 0.5f));
        if (ImGui.dragFloat("##cv-" + id, vBuf, 0.01f, -1000f, 1000f, "v %.3f")) {
            SceneEditorSession.pushUndo();
            sel.put("v", (double) vBuf[0]);
            changed = true;
        }
        if (selectIndex < keys.size() - 1) {
            String ease = ScriptCurve.normalizeEase(ScriptCurve.str(sel, "ease", "linear"));
            String shown = easeLabel(ease);
            ImGui.setNextItemWidth(-1f);
            if (ImGui.beginCombo("##ce-" + id, shown)) {
                for (String e : EASE_IDS) {
                    boolean is = e.equals(ease);
                    if (ImGui.selectable(easeLabel(e), is)) {
                        SceneEditorSession.pushUndo();
                        sel.put("ease", e);
                        changed = true;
                    }
                    if (is) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }
        }

        if (changed) {
            boolean dragging = id.equals(dragId) && dragIndex >= 0;
            onChange.accept(dragging ? keys : ScriptCurve.normalize(keys));
        }
        return changed;
    }

    private boolean presetButton(String label, String ease, Consumer<Object> onChange) {
        if (!ImGui.smallButton(label + "##preset-" + ease)) return false;
        SceneEditorSession.pushUndo();
        onChange.accept(ScriptCurve.fromPreset(ease));
        selectIndex = 0;
        return true;
    }

    private static String easeLabel(String ease) {
        return switch (ease) {
            case "easeInOut" -> EditorI18n.t("плавно");
            case "easeIn" -> EditorI18n.t("в начале");
            case "easeOut" -> EditorI18n.t("в конце");
            case "bounceOut" -> EditorI18n.t("отскок");
            case "elasticOut" -> EditorI18n.t("пружина");
            default -> EditorI18n.t("линейно");
        };
    }

    private int hitKey(List<Map<String, Object>> keys, double[] range,
                       float x0, float y0, float x1, float y1) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        int best = -1;
        float bestD = HIT_R * HIT_R;
        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> k = keys.get(i);
            float dx = mx - toX(ScriptCurve.num(k, "t", 0), x0, x1);
            float dy = my - toY(ScriptCurve.num(k, "v", 0), range[0], range[1], y0, y1);
            float d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestIndex(List<Map<String, Object>> keys, double t) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            double d = Math.abs(ScriptCurve.num(keys.get(i), "t", 0) - t);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static double[] valueRange(List<Map<String, Object>> keys) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Map<String, Object> k : keys) {
            double v = ScriptCurve.num(k, "v", 0);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        for (int i = 0; i <= 8; i++) {
            double v = ScriptCurve.eval(keys, i / 8.0);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0;
            max = 1;
        }
        if (max - min < 1e-4) {
            min -= 0.5;
            max += 0.5;
        } else {
            double pad = (max - min) * 0.12;
            min -= pad;
            max += pad;
        }
        return new double[]{min, max};
    }

    private static float toX(double t, float x0, float x1) {
        return x0 + (float) (clamp01(t) * (x1 - x0));
    }

    private static float toY(double v, double vmin, double vmax, float y0, float y1) {
        double u = (v - vmin) / (vmax - vmin);
        if (u < 0) u = 0;
        if (u > 1) u = 1;
        return y1 - (float) (u * (y1 - y0));
    }

    private static double fromX(float x, float x0, float x1) {
        if (x1 <= x0) return 0;
        return clamp01((x - x0) / (x1 - x0));
    }

    private static double fromY(float y, double vmin, double vmax, float y0, float y1) {
        if (y1 <= y0) return vmin;
        double u = 1.0 - (y - y0) / (y1 - y0);
        return vmin + u * (vmax - vmin);
    }

    private static double clamp01(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t;
    }

    private static int pack(float r, float g, float b, float a) {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a);
    }
}
