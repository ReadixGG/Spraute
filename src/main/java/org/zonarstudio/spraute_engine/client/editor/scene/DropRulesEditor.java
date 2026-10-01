package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import org.zonarstudio.spraute_engine.scene.resource.SceneDropRule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Shared inspector UI for multi-item drops — padded cards, stacked fields, larger buttons.
 */
public final class DropRulesEditor {
    private final int[] intBuf = new int[1];

    /** Full drop panel: optional XP field + rule cards. */
    public void renderPanel(List<SceneDropRule> rules, boolean allowSelf, int dropXp, IntConsumer onXp) {
        if (onXp != null) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Опыт (XP)"));
            intBuf[0] = Math.max(0, dropXp);
            ImGui.setNextItemWidth(-1f);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.14f, 0.16f, 0.12f, 1f);
            if (ImGui.dragInt("##drop-xp", intBuf, 0.5f, 0, 10_000)) {
                onXp.accept(Math.max(0, intBuf[0]));
                SceneEditorSession.markDirty();
            }
            ImGui.popStyleColor();
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.hint(EditorI18n.t("0 = без опыта · орбы при ломании / смерти"));
            ImGui.dummy(1f, 8f);
        }
        render(rules, allowSelf);
    }

    public void render(List<SceneDropRule> rules, boolean allowSelf) {
        if (rules == null) return;

        if (rules.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.10f, 0.11f, 0.14f, 1f);
            ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 8f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14f, 14f);
            ImGui.beginChild("##drop-empty", -1f, 56f, true);
            ImGui.textColored(0.50f, 0.54f, 0.60f, 1f, EditorI18n.t("Нет дропов — добавь предмет ниже"));
            ImGui.endChild();
            ImGui.popStyleVar(2);
            ImGui.popStyleColor();
            ImGui.dummy(1f, 10f);
        }

        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            SceneDropRule rule = rules.get(i);
            ImGui.pushID("drop-card-" + i);
            drawCard(rule, allowSelf, () -> {
                SceneEditorSession.pushUndo();
                rules.remove(index);
                SceneEditorSession.markDirty();
            });
            ImGui.popID();
            ImGui.dummy(1f, 12f);
        }

        ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.42f, 0.32f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.52f, 0.40f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.16f, 0.38f, 0.28f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 12f, 8f);
        if (ImGui.button(EditorI18n.t("+  Добавить дроп"), -1f, 36f)) {
            SceneEditorSession.pushUndo();
            rules.add(new SceneDropRule(allowSelf ? SceneDropRule.SELF : "minecraft:stone", 1, 1, 100));
            SceneEditorSession.markDirty();
        }
        ImGui.popStyleVar();
        ImGui.popStyleColor(3);
        ImGui.dummy(1f, 4f);
        EditorInspectorUi.hint(EditorI18n.t("У каждой строки свой предмет, количество и шанс"));
    }

    private void drawCard(SceneDropRule rule, boolean allowSelf, Runnable onRemove) {
        boolean self = SceneDropRule.isSelf(rule.itemId);

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.12f, 0.13f, 0.16f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.40f, 0.44f, 0.52f, 0.45f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14f, 12f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8f, 8f);
        // Auto-ish height via generous fixed size for stacked fields.
        ImGui.beginChild("##card", -1f, 348f, true);

        // —— Header: icon + name + remove ——
        var icon = self ? null : EditorItemIcons.icon(rule.itemId);
        if (icon != null) {
            ImGui.image(icon.atlasGlId(), 40f, 40f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.22f, 0.28f, 1f);
            ImGui.button(self ? "BLK" : "?", 40f, 40f);
            ImGui.popStyleColor();
        }
        ImGui.sameLine(0, 12f);

        float nameMaxW = Math.max(40f, ImGui.getContentRegionAvailX() - 36f);
        ImGui.beginGroup();
        String title = self ? EditorI18n.t("Сам блок")
                : (rule.itemId == null || rule.itemId.isBlank() ? EditorI18n.t("(не выбрано)") : shortName(rule.itemId));
        ImGui.textColored(0.92f, 0.93f, 0.96f, 1f, truncate(title, nameMaxW));
        if (self) {
            ImGui.textColored(0.50f, 0.54f, 0.60f, 1f, EditorI18n.t("выпадает этот блок"));
        } else if (rule.itemId != null && !rule.itemId.isBlank()) {
            ImGui.textColored(0.50f, 0.54f, 0.60f, 1f, truncate(rule.itemId, nameMaxW));
        }
        ImGui.endGroup();

        ImGui.sameLine();
        float avail = ImGui.getContentRegionAvailX();
        if (avail > 28f) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - 28f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.40f, 0.18f, 0.18f, 0.95f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.55f, 0.22f, 0.22f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8f, 6f);
        if (ImGui.button("×##rm", 28f, 28f)) onRemove.run();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);

        ImGui.dummy(1f, 4f);

        // —— Pick buttons (full-width friendly) ——
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f, 7f);
        float btnW = allowSelf
                ? Math.max(80f, (ImGui.getContentRegionAvailX() - 8f) * 0.5f)
                : -1f;
        if (ImGui.button(EditorI18n.t("Выбрать предмет"), btnW, 0f)) ImGui.openPopup("##pick-item");
        if (allowSelf) {
            ImGui.sameLine(0, 8f);
            if (ImGui.button(EditorI18n.t("Сам блок"), btnW, 0f)) {
                SceneEditorSession.pushUndo();
                rule.itemId = SceneDropRule.SELF;
                SceneEditorSession.markDirty();
            }
        }
        ImGui.popStyleVar();

        if (ImGui.beginPopup("##pick-item")) {
            EditorItemPicker.renderPopupList(rule.itemId, false, id -> {
                SceneEditorSession.pushUndo();
                rule.itemId = id;
                SceneEditorSession.markDirty();
            });
            ImGui.endPopup();
        }

        if (!self) {
            EditorItemPicker.nbtField("drop-nbt", rule.itemId, v -> {
                rule.itemId = v;
                SceneEditorSession.markDirty();
            });
        }

        ImGui.dummy(1f, 2f);

        // —— Stats: 3 rows (not one line) ——
        fieldInt(EditorI18n.t("Мин. количество"), rule.min, 0, 64, v -> rule.min = v);
        fieldInt(EditorI18n.t("Макс. количество"), rule.max, 0, 64, v -> rule.max = Math.max(rule.min, v));
        fieldInt(EditorI18n.t("Шанс %"), rule.chance, 0, 100, v -> rule.chance = v);

        ImGui.endChild();
        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private void fieldInt(String label, int current, int min, int max, java.util.function.Consumer<Integer> setter) {
        EditorInspectorUi.fieldLabel(label);
        intBuf[0] = current;
        ImGui.setNextItemWidth(-1f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8f, 6f);
        if (ImGui.dragInt("##f-" + label, intBuf, 0.5f, min, max)) {
            setter.accept(intBuf[0]);
            SceneEditorSession.markDirty();
        }
        ImGui.popStyleVar();
        EditorInspectorUi.undoOnActivate();
    }

    public static List<SceneDropRule> copyList(List<SceneDropRule> src) {
        List<SceneDropRule> out = new ArrayList<>();
        if (src == null) return out;
        for (SceneDropRule r : src) {
            if (r != null) out.add(r.copy());
        }
        return out;
    }

    private static String shortName(String id) {
        String clean = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(id).id();
        if (clean.isBlank()) clean = id;
        int colon = clean.indexOf(':');
        return colon >= 0 ? clean.substring(colon + 1) : clean;
    }

    private static String truncate(String text, float maxWidth) {
        if (text == null) return "";
        if (ImGui.calcTextSizeX(text) <= maxWidth) return text;
        String ellipsis = "…";
        for (int i = text.length() - 1; i > 0; i--) {
            String cut = text.substring(0, i) + ellipsis;
            if (ImGui.calcTextSizeX(cut) <= maxWidth) return cut;
        }
        return ellipsis;
    }
}
