package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.custom.CustomFieldDef;
import org.zonarstudio.spraute_engine.scene.custom.CustomFieldKind;
import org.zonarstudio.spraute_engine.scene.custom.CustomListDef;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry;
import org.zonarstudio.spraute_engine.scene.custom.CustomNodeTypeDef;
import org.zonarstudio.spraute_engine.scene.custom.ScriptCurve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/** Schema-driven inspector for {@code SceneNodeType.CUSTOM}. */
public final class CustomNodeInspector {
    private final ImString textBuf = new ImString(256);
    private final ImString texSearch = new ImString(64);
    private final float[] numBuf = new float[1];
    private final int[] intBuf = new int[1];
    private final CustomCurveEditor curveEditor = new CustomCurveEditor();
    private CustomNodeTypeDef activeDef;

    public void render(SceneNode node) {
        if (node == null) return;
        var doc = SceneEditorSession.document();
        if (doc != null) CustomNodeRegistry.bindEditorScene(doc);
        else CustomNodeRegistry.ensureParsed();
        CustomNodeTypeDef def = doc != null
                ? CustomNodeRegistry.get(doc, node.customTypeId)
                : CustomNodeRegistry.get(node.customTypeId);
        if (def == null) {
            EditorInspectorUi.hint(EditorI18n.t("Тип «") + node.customTypeId
                    + EditorI18n.t("» не найден — добавь SCRIPT с declare node type в эту сцену и сохрани .spr"));
            return;
        }
        if (node.customData == null) node.customData = new LinkedHashMap<>();
        applyDefaults(node, def);

        EditorInspectorUi.hint(def.displayLabel() + " · " + def.id
                + (def.runtime == null || def.runtime.isBlank() ? "" : " · runtime=" + def.runtime));
        if (def.createsItem()) {
            EditorInspectorUi.hint(EditorI18n.t("Регистрация при старте MC · нужен перезапуск после правок"));
            EditorInspectorUi.hint("spraute_engine:" + (node.id == null ? "" : node.id));
        }

        renderSchema(node, def);
    }

    /**
     * Extra inspector sections declared by child SCRIPT / VISUAL_SCRIPT nodes
     * ({@code declare inspector { … }}). Data lives on the parent ({@code _node.field}).
     */
    public void renderAttached(SceneNode node) {
        if (node == null) return;
        var doc = SceneEditorSession.document();
        if (doc != null) CustomNodeRegistry.bindEditorScene(doc);
        else CustomNodeRegistry.ensureParsed();
        List<CustomNodeTypeDef> defs = CustomNodeRegistry.inspectorsFor(doc, node);
        if (defs.isEmpty()) return;
        if (node.customData == null) node.customData = new LinkedHashMap<>();
        int i = 0;
        for (CustomNodeTypeDef def : defs) {
            applyDefaults(node, def);
            String secId = "insp-" + (def.sourceScript == null ? "" : def.sourceScript)
                    + "-" + def.id + "-" + i++;
            if (EditorInspectorUi.beginSection(secId, def.inspectorTitle(), true)) {
                renderSchema(node, def);
                EditorInspectorUi.endSection();
            }
        }
    }

    private static void applyDefaults(SceneNode node, CustomNodeTypeDef def) {
        for (var e : def.defaultData().entrySet()) {
            node.customData.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    private void renderSchema(SceneNode node, CustomNodeTypeDef def) {
        CustomNodeTypeDef prev = activeDef;
        activeDef = def;
        try {
            for (CustomFieldDef field : def.fields) {
                if (field == null) continue;
                renderField(node, null, -1, field);
            }
            for (CustomListDef list : def.lists) {
                if (list == null) continue;
                renderList(node, list);
            }
        } finally {
            activeDef = prev;
        }
    }

    @SuppressWarnings("unchecked")
    private void renderList(SceneNode node, CustomListDef list) {
        Object raw = node.customData.get(list.id);
        List<Map<String, Object>> rows;
        if (raw instanceof List<?> existing) {
            rows = new ArrayList<>();
            for (Object o : existing) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (var e : m.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
                    rows.add(copy);
                }
            }
        } else {
            rows = new ArrayList<>();
        }
        node.customData.put(list.id, rows);

        String title = list.label == null || list.label.isBlank() ? list.id : list.label;
        ImGui.dummy(1f, 6f);
        ImGui.textColored(0.75f, 0.80f, 0.90f, 1f, title);
        ImGui.separator();

        if (rows.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.10f, 0.11f, 0.14f, 1f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 8f);
            ImGui.beginChild("##custom-list-empty-" + list.id, -1f, 48f, true);
            ImGui.textColored(0.50f, 0.54f, 0.60f, 1f, EditorI18n.t("Пусто — добавьте запись ниже"));
            ImGui.endChild();
            ImGui.popStyleVar();
            ImGui.popStyleColor();
        }

        for (int i = 0; i < rows.size(); i++) {
            ImGui.pushID("custom-list-" + list.id + "-" + i);
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.12f, 0.14f, 0.18f, 1f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 8f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10f, 10f);
            // Height must be explicit — 0 fills remaining parent space and squashes other cards.
            ImGui.beginChild("##card", -1f, cardHeightForList(list), true);
            ImGui.textDisabled("#" + (i + 1));
            ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - 28f);
            if (ImGui.smallButton("×")) {
                SceneEditorSession.pushUndo();
                rows.remove(i);
                SceneEditorSession.markDirty();
                ImGui.endChild();
                ImGui.popStyleVar(2);
                ImGui.popStyleColor();
                ImGui.popID();
                break;
            }
            Map<String, Object> row = rows.get(i);
            for (CustomFieldDef field : list.fields) {
                if (field == null || field.kind == CustomFieldKind.BUTTON) continue;
                renderField(node, row, i, field);
            }
            ImGui.endChild();
            ImGui.popStyleVar(2);
            ImGui.popStyleColor();
            ImGui.popID();
            ImGui.dummy(1f, 8f);
        }

        String add = list.addLabel == null || list.addLabel.isBlank() ? EditorI18n.t("Добавить") : list.addLabel;
        ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.42f, 0.32f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.52f, 0.40f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.16f, 0.38f, 0.28f, 1f);
        if (ImGui.button("+  " + add, -1f, 32f)) {
            SceneEditorSession.pushUndo();
            Map<String, Object> entry = new LinkedHashMap<>();
            for (CustomFieldDef f : list.fields) {
                if (f == null || f.kind == CustomFieldKind.BUTTON) continue;
                entry.put(f.id, f.resolvedDefault());
            }
            rows.add(entry);
            SceneEditorSession.markDirty();
        }
        ImGui.popStyleColor(3);
    }

    private void renderField(SceneNode node, Map<String, Object> row, int rowIndex, CustomFieldDef field) {
        String label = field.label == null || field.label.isBlank() ? field.id : field.label;
        String idSuffix = row == null ? field.id : field.id + "-" + rowIndex;

        if (field.kind == CustomFieldKind.BUTTON) {
            if (ImGui.button(label + "##btn-" + idSuffix, -1f, 28f)) {
                String listId = field.appendList;
                if (listId != null && !listId.isBlank()) {
                    Object raw = node.customData.get(listId);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = raw instanceof List<?>
                            ? (List<Map<String, Object>>) (List<?>) raw
                            : new ArrayList<>();
                    if (!(raw instanceof List<?>)) node.customData.put(listId, rows);
                    CustomListDef list = activeDef == null ? null : activeDef.list(listId);
                    SceneEditorSession.pushUndo();
                    Map<String, Object> entry = new LinkedHashMap<>();
                    if (list != null) {
                        for (CustomFieldDef f : list.fields) {
                            if (f == null || f.kind == CustomFieldKind.BUTTON) continue;
                            entry.put(f.id, f.resolvedDefault());
                        }
                    }
                    rows.add(entry);
                    SceneEditorSession.markDirty();
                }
            }
            return;
        }

        Object current = row != null ? row.get(field.id) : node.customData.get(field.id);
        if (current == null) current = field.resolvedDefault();
        Object factory = field.resolvedDefault();
        boolean resetDirty = !java.util.Objects.equals(
                current == null ? "" : String.valueOf(current),
                factory == null ? "" : String.valueOf(factory));
        if (field.kind == CustomFieldKind.BOOL) {
            resetDirty = isTruthy(current) != isTruthy(factory);
        } else if (field.kind == CustomFieldKind.NUMBER) {
            double a = current instanceof Number n ? n.doubleValue() : parseDouble(current, 0);
            double b = factory instanceof Number n ? n.doubleValue() : parseDouble(factory, 0);
            resetDirty = Math.abs(a - b) > 1e-4;
        } else if (field.kind == CustomFieldKind.CURVE) {
            resetDirty = !ScriptCurve.same(current, factory);
        }
        EditorInspectorUi.bindReset(resetDirty, () -> {
            SceneEditorSession.pushUndo();
            put(node, row, field.id, factory);
            SceneEditorSession.markDirty();
        });
        EditorInspectorUi.bindClipboard(current, v -> {
            SceneEditorSession.pushUndo();
            put(node, row, field.id, v);
            SceneEditorSession.markDirty();
        });

        switch (field.kind) {
            case BOOL -> {
                boolean v = isTruthy(current);
                if (EditorInspectorUi.checkbox(label, v)) {
                    SceneEditorSession.pushUndo();
                    put(node, row, field.id, !v);
                    SceneEditorSession.markDirty();
                }
            }
            case NUMBER -> {
                EditorInspectorUi.fieldLabel(label);
                double d = current instanceof Number n ? n.doubleValue() : parseDouble(current, 0);
                numBuf[0] = (float) d;
                ImGui.setNextItemWidth(-1f);
                if (ImGui.dragFloat("##num-" + idSuffix, numBuf, 0.25f)) {
                    SceneEditorSession.pushUndo();
                    // Prefer int when whole.
                    Object stored = Math.abs(numBuf[0] - Math.rint(numBuf[0])) < 1e-4
                            ? (int) Math.rint(numBuf[0])
                            : (double) numBuf[0];
                    put(node, row, field.id, stored);
                    SceneEditorSession.markDirty();
                }
                EditorInspectorUi.undoOnActivate();
            }
            case COLOR -> UiStyleEditor.colorField(label, "custom-" + idSuffix, String.valueOf(current),
                    hex -> {
                        SceneEditorSession.pushUndo();
                        put(node, row, field.id, hex);
                    });
            case ITEM -> {
                ImGui.pushID(idSuffix);
                EditorItemPicker.combo(label, String.valueOf(current == null ? "" : current),
                        v -> put(node, row, field.id, v));
                ImGui.popID();
            }
            case BLOCK -> EditorItemPicker.combo(label, String.valueOf(current == null ? "" : current),
                    EditorItemIcons.blocks(), true,
                    v -> {
                        SceneEditorSession.pushUndo();
                        put(node, row, field.id, v);
                        SceneEditorSession.markDirty();
                    });
            case TEXTURE -> {
                ImGui.pushID(idSuffix);
                textureCombo(label, String.valueOf(current == null ? "" : current),
                        v -> {
                            SceneEditorSession.pushUndo();
                            put(node, row, field.id, v);
                            SceneEditorSession.markDirty();
                        });
                ImGui.popID();
            }
            case DROPDOWN -> {
                EditorInspectorUi.fieldLabel(label);
                String cur = String.valueOf(current == null ? "" : current);
                String shown = cur;
                for (var e : field.options.entrySet()) {
                    if (e.getValue().equals(cur)) {
                        shown = e.getKey();
                        break;
                    }
                }
                ImGui.setNextItemWidth(-1f);
                if (ImGui.beginCombo("##dd-" + idSuffix, shown)) {
                    for (var e : field.options.entrySet()) {
                        boolean sel = e.getValue().equals(cur);
                        if (ImGui.selectable(e.getKey(), sel)) {
                            SceneEditorSession.pushUndo();
                            put(node, row, field.id, e.getValue());
                            SceneEditorSession.markDirty();
                        }
                        if (sel) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
            }
            case CURVE -> {
                EditorInspectorUi.fieldLabel(label, EditorI18n.t("Кривая 0…1 → значение"),
                        "_node." + field.id + ".eval(t)\ncurveEval(_node." + field.id + ", t)");
                curveEditor.render(idSuffix, current, v -> {
                    put(node, row, field.id, v);
                    SceneEditorSession.markDirty();
                });
            }
            default -> {
                EditorInspectorUi.fieldLabel(label);
                textBuf.set(String.valueOf(current == null ? "" : current));
                ImGui.setNextItemWidth(-1f);
                if (ImGui.inputText("##txt-" + idSuffix, textBuf)) {
                    put(node, row, field.id, textBuf.get());
                    SceneEditorSession.markDirty();
                }
                EditorInspectorUi.undoOnActivate();
            }
        }
        EditorInspectorUi.finishClipboardField();
        EditorInspectorUi.clearReset();
    }

    private void textureCombo(String label, String current, Consumer<String> setter) {
        List<String> options = EditorAssetIndex.textures();
        String shown = current == null || current.isEmpty() ? EditorI18n.t("(не выбрано)") : current;
        EditorInspectorUi.fieldLabel(label);
        if (ImGui.beginCombo("##tex-" + label, shown)) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.08f, 0.10f, 0.14f, 1f);
            ImGui.inputTextWithHint("##tex-search-" + label, EditorI18n.t("Поиск…"), texSearch);
            ImGui.popStyleColor();
            String filter = texSearch.get().toLowerCase(Locale.ROOT);
            if (ImGui.selectable(EditorI18n.t("(не выбрано)"), current == null || current.isEmpty())) {
                setter.accept("");
            }
            for (String option : options) {
                if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
                var tex = EditorTexturePreview.cached(option);
                if (tex != null) {
                    ImGui.image(tex.glId(), 18f, 18f);
                    ImGui.sameLine();
                } else {
                    ImGui.dummy(18f, 18f);
                    ImGui.sameLine();
                }
                if (ImGui.selectable(option, option.equals(current))) {
                    setter.accept(option);
                }
                if (ImGui.isItemHovered()) {
                    var hover = EditorTexturePreview.get(option);
                    if (hover != null) {
                        ImGui.beginTooltip();
                        float[] size = EditorTexturePreview.fit(hover, 192f, 192f);
                        ImGui.image(hover.glId(), size[0], size[1]);
                        ImGui.textDisabled(hover.width() + " x " + hover.height());
                        ImGui.endTooltip();
                    }
                }
            }
            ImGui.endCombo();
        }
        if (current != null && !current.isEmpty()) {
            var tex = EditorTexturePreview.get(current);
            if (tex != null) {
                float[] size = EditorTexturePreview.fit(tex, 96f, 96f);
                ImGui.image(tex.glId(), size[0], size[1]);
                ImGui.sameLine();
                ImGui.textDisabled(tex.width() + " x " + tex.height());
            }
        }
    }

    private static void put(SceneNode node, Map<String, Object> row, String key, Object value) {
        if (row != null) row.put(key, value);
        else node.customData.put(key, value);
    }

    private static boolean isTruthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static double parseDouble(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Card height for list rows — ImGui child with h=0 consumes all remaining vertical space. */
    private static float cardHeightForList(CustomListDef list) {
        float h = 36f + 20f; // "#N" header row + window padding
        for (CustomFieldDef field : list.fields) {
            if (field == null || field.kind == CustomFieldKind.BUTTON) continue;
            h += fieldRowHeight(field);
        }
        return Math.max(96f, h);
    }

    private static float fieldRowHeight(CustomFieldDef field) {
        return switch (field.kind) {
            case BOOL -> 30f;
            case ITEM -> 108f;
            case TEXTURE -> 128f;
            case BLOCK, COLOR -> 56f;
            case CURVE -> 220f;
            case NUMBER, DROPDOWN, TEXT -> 52f;
            default -> 48f;
        };
    }
}
