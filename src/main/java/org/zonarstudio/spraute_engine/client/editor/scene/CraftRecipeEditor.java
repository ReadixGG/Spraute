package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import org.zonarstudio.spraute_engine.registry.CraftJsonBuilder;
import org.zonarstudio.spraute_engine.scene.resource.SceneCraftDef;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/** Visual craft recipe editor: 3×3 grid / shapeless list / furnace — item pickers only. */
public final class CraftRecipeEditor {
    private final float[] countBuf = new float[1];
    private final float[] xpBuf = new float[1];
    private final int[] timeBuf = new int[1];

    public void render(SceneCraftDef def) {
        if (def == null) return;
        def.ensureSlots();

        EditorInspectorUi.fieldLabel(EditorI18n.t("Тип интерфейса"));
        String type = CraftJsonBuilder.normalizeType(def.type);
        String shown = switch (type) {
            case "shapeless" -> EditorI18n.t("Без формы");
            case "smelting" -> EditorI18n.t("Печь");
            case "blasting" -> EditorI18n.t("Плавильная печь");
            case "smoking" -> EditorI18n.t("Коптильня");
            case "campfire_cooking" -> EditorI18n.t("Костёр");
            default -> EditorI18n.t("Верстак 3×3");
        };
        if (ImGui.beginCombo("##craft-iface", shown)) {
            pickType(def, "slots", EditorI18n.t("Верстак 3×3"), type);
            pickType(def, "any", EditorI18n.t("Без формы"), type);
            pickType(def, "smelting", EditorI18n.t("Печь"), type);
            pickType(def, "blasting", EditorI18n.t("Плавильная печь"), type);
            pickType(def, "smoking", EditorI18n.t("Коптильня"), type);
            pickType(def, "campfire_cooking", EditorI18n.t("Костёр"), type);
            ImGui.endCombo();
        }

        type = CraftJsonBuilder.normalizeType(def.type);
        if ("shaped".equals(type)) {
            renderGrid(def);
        } else if ("shapeless".equals(type)) {
            renderShapeless(def);
        } else {
            renderFurnace(def);
        }

        EditorInspectorUi.hint(EditorI18n.t("Кликни слот / поле — выбор из реестра предметов"));
        EditorInspectorUi.hint(EditorI18n.t("Изменения вступят после перезапуска Minecraft"));
    }

    private void pickType(SceneCraftDef def, String value, String label, String currentNorm) {
        String norm = CraftJsonBuilder.normalizeType(value);
        boolean selected = norm.equals(currentNorm)
                || (value.equals(def.type) && !currentNorm.equals("shaped") && !currentNorm.equals("shapeless"));
        if ("slots".equals(value)) selected = "shaped".equals(currentNorm);
        if ("any".equals(value)) selected = "shapeless".equals(currentNorm);
        if (ImGui.selectable(label, selected)) {
            SceneEditorSession.pushUndo();
            def.type = value;
            SceneEditorSession.markDirty();
        }
    }

    private void renderGrid(SceneCraftDef def) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Сетка 3×3"));
        float slot = 40f;
        float gap = 4f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (col > 0) ImGui.sameLine(0, gap);
                int idx = row * 3 + col;
                EditorItemPicker.slotButton("##cslot" + idx, def.slots[idx], slot, id -> {
                    SceneEditorSession.pushUndo();
                    def.slots[idx] = id;
                    SceneEditorSession.markDirty();
                });
            }
        }
        ImGui.spacing();
        ImGui.text("→");
        ImGui.sameLine();
        renderResult(def);
    }

    private void renderShapeless(SceneCraftDef def) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Ингредиенты"));
        for (int i = 0; i < def.ingredients.size(); i++) {
            final int index = i;
            String cur = def.ingredients.get(i);
            ImGui.pushID("ing-" + i);
            EditorItemPicker.slotButton("##slot", cur, 28f, id -> {
                SceneEditorSession.pushUndo();
                if (id == null || id.isBlank()) {
                    def.ingredients.remove(index);
                } else {
                    def.ingredients.set(index, id);
                }
                SceneEditorSession.markDirty();
            });
            ImGui.sameLine();
            ImGui.text(cur == null || cur.isBlank() ? EditorI18n.t("(пусто)") : shortName(cur));
            ImGui.sameLine();
            if (ImGui.smallButton("×")) {
                SceneEditorSession.pushUndo();
                def.ingredients.remove(index);
                SceneEditorSession.markDirty();
            }
            ImGui.popID();
        }
        if (ImGui.button(EditorI18n.t("+ Добавить предмет"))) {
            ImGui.openPopup("##add-ing");
        }
        if (ImGui.beginPopup("##add-ing")) {
            EditorItemPicker.renderPopupList("", false, id -> {
                if (id == null || id.isBlank()) return;
                SceneEditorSession.pushUndo();
                def.ingredients.add(id);
                SceneEditorSession.markDirty();
            });
            ImGui.endPopup();
        }
        ImGui.spacing();
        renderResult(def);
    }

    private void renderFurnace(SceneCraftDef def) {
        EditorItemPicker.combo(EditorI18n.t("Ингредиент"), def.ingredient, EditorItemIcons.items(), true, false,
                id -> def.ingredient = id);
        ImGui.text("→");
        renderResult(def);
        xpBuf[0] = def.xp;
        if (EditorInspectorUi.dragFloat(EditorI18n.t("Опыт (xp)"), "craft-xp", xpBuf, 0.01f, 0f, 10f)) {
            def.xp = xpBuf[0];
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
        timeBuf[0] = def.time;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Время (тики)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.dragInt("##craft-time", timeBuf, 1, 1, 10_000)) {
            def.time = timeBuf[0];
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void renderResult(SceneCraftDef def) {
        EditorItemPicker.combo(EditorI18n.t("Результат"), def.result, EditorItemIcons.items(), true, false,
                id -> def.result = id);
        countBuf[0] = def.count;
        if (EditorInspectorUi.dragFloat(EditorI18n.t("Количество"), "craft-count", countBuf, 1f, 1f, 64f)) {
            def.count = Math.max(1, Math.round(countBuf[0]));
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private static String shortName(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
