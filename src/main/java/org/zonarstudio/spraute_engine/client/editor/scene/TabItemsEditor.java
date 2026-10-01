package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.scene.resource.SceneTabDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Creative-tab style item layout editor: drag from palette into the tab grid,
 * reorder by dragging slots. Order in {@link SceneTabDef#items} is the final tab order.
 */
public final class TabItemsEditor {
    private static final String PAYLOAD_PALETTE = "spraute_tab_palette";
    private static final String PAYLOAD_SLOT = "spraute_tab_slot";
    private static final int COLS = 9;
    private static final float SLOT = 36f;
    private static final float GAP = 3f;

    private final ImString search = new ImString(64);
    /** Pending drop applied after the grid loop (avoid mutating while drawing). */
    private Runnable pending;

    public void render(SceneTabDef def) {
        if (def == null) return;
        if (def.items == null) def.items = new ArrayList<>();

        EditorInspectorUi.fieldLabel(EditorI18n.t("Иконка вкладки"));
        iconPicker(def);

        EditorInspectorUi.fieldLabel(EditorI18n.t("Содержимое вкладки"));
        EditorInspectorUi.hint(EditorI18n.t("Перетаскивай предметы · порядок как в креативе"));
        renderTabGrid(def);

        ImGui.spacing();
        EditorInspectorUi.fieldLabel(EditorI18n.t("Палитра предметов"));
        ImGui.inputTextWithHint("##tab-search", EditorI18n.t("Поиск…"), search);
        renderPalette(def);

        if (pending != null) {
            Runnable op = pending;
            pending = null;
            op.run();
        }
    }

    private void iconPicker(SceneTabDef def) {
        var icon = EditorItemIcons.icon(def.icon);
        if (icon != null) {
            if (ImGui.imageButton("##tab-icon", icon.atlasGlId(), 40f, 40f,
                    icon.u0(), icon.v0(), icon.u1(), icon.v1())) {
                ImGui.openPopup("tab-icon-pick");
            }
        } else if (ImGui.button(EditorI18n.t("Иконка…"), 40f, 40f)) {
            ImGui.openPopup("tab-icon-pick");
        }
        if (ImGui.isItemHovered() && def.icon != null && !def.icon.isBlank()) {
            ImGui.setTooltip(def.icon);
        }
        ImGui.sameLine();
        ImGui.textWrapped(def.icon == null || def.icon.isBlank() ? EditorI18n.t("(не выбрано)") : def.icon);

        if (ImGui.beginPopup("tab-icon-pick")) {
            EditorItemPicker.renderPopupList(def.icon, true, id -> {
                SceneEditorSession.pushUndo();
                def.icon = id;
                SceneEditorSession.markDirty();
            });
            ImGui.endPopup();
        }
    }

    private void renderTabGrid(SceneTabDef def) {
        List<String> items = def.items;
        int count = items.size();
        // Extra empty slot to drop new items at the end.
        int cells = count + 1;
        int rows = Math.max(1, (cells + COLS - 1) / COLS);

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.10f, 0.11f, 0.14f, 1f);
        float width = COLS * (SLOT + GAP) + 8f;
        float height = Math.min(rows, 6) * (SLOT + GAP) + 12f;
        ImGui.beginChild("##tab-grid", width, height, true);

        for (int i = 0; i < cells; i++) {
            if (i % COLS != 0) ImGui.sameLine(0, GAP);
            ImGui.pushID(i);
            if (i < count) {
                occupiedSlot(def, i, items.get(i));
            } else {
                emptySlot(def, i);
            }
            ImGui.popID();
        }

        ImGui.endChild();
        ImGui.popStyleColor();

        if (ImGui.smallButton(EditorI18n.t("Очистить вкладку"))) {
            if (!items.isEmpty()) {
                SceneEditorSession.pushUndo();
                items.clear();
                SceneEditorSession.markDirty();
            }
        }
    }

    private void occupiedSlot(SceneTabDef def, int index, String itemId) {
        var icon = EditorItemIcons.icon(itemId);
        if (icon != null) {
            ImGui.imageButton("##s", icon.atlasGlId(), SLOT, SLOT,
                    icon.u0(), icon.v0(), icon.u1(), icon.v1());
        } else {
            ImGui.button("?", SLOT, SLOT);
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(itemId);
            ImGui.textDisabled(EditorI18n.t("Перетащить · ПКМ — убрать"));
            ImGui.endTooltip();
        }

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD_SLOT, Integer.toString(index));
            if (icon != null) {
                ImGui.image(icon.atlasGlId(), 28f, 28f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
                ImGui.sameLine();
            }
            ImGui.text(shortName(itemId));
            ImGui.endDragDropSource();
        }

        acceptDrop(def, index);

        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
            queue(() -> {
                SceneEditorSession.pushUndo();
                if (index >= 0 && index < def.items.size()) {
                    def.items.remove(index);
                    SceneEditorSession.markDirty();
                }
            });
        }
    }

    private void emptySlot(SceneTabDef def, int index) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.26f, 0.32f, 1f);
        ImGui.button("+", SLOT, SLOT);
        ImGui.popStyleColor(2);
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Перетащи предмет сюда"));
        acceptDrop(def, index);
    }

    private void acceptDrop(SceneTabDef def, int targetIndex) {
        if (!ImGui.beginDragDropTarget()) return;

        String fromPalette = ImGui.acceptDragDropPayload(PAYLOAD_PALETTE);
        if (fromPalette != null && !fromPalette.isBlank()) {
            final String id = fromPalette.trim();
            final int at = targetIndex;
            queue(() -> {
                SceneEditorSession.pushUndo();
                insertOrMove(def, id, at, -1);
                SceneEditorSession.markDirty();
            });
        }

        String fromSlot = ImGui.acceptDragDropPayload(PAYLOAD_SLOT);
        if (fromSlot != null) {
            try {
                final int from = Integer.parseInt(fromSlot);
                final int at = targetIndex;
                queue(() -> {
                    SceneEditorSession.pushUndo();
                    reorder(def, from, at);
                    SceneEditorSession.markDirty();
                });
            } catch (NumberFormatException ignored) {
            }
        }

        ImGui.endDragDropTarget();
    }

    private void renderPalette(SceneTabDef def) {
        String filter = search.get().toLowerCase(Locale.ROOT);
        List<String> options = EditorItemIcons.items();
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.09f, 0.12f, 1f);
        ImGui.beginChild("##tab-palette", ImGui.getContentRegionAvailX(), 180f, true);

        int col = 0;
        for (String option : options) {
            if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if (col > 0) ImGui.sameLine(0, GAP);
            ImGui.pushID(option);
            var icon = EditorItemIcons.icon(option);
            if (icon != null) {
                ImGui.imageButton("##p", icon.atlasGlId(), SLOT - 4f, SLOT - 4f,
                        icon.u0(), icon.v0(), icon.u1(), icon.v1());
            } else {
                ImGui.button("?", SLOT - 4f, SLOT - 4f);
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(option);

            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload(PAYLOAD_PALETTE, option);
                if (icon != null) {
                    ImGui.image(icon.atlasGlId(), 28f, 28f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
                    ImGui.sameLine();
                }
                ImGui.text(shortName(option));
                ImGui.endDragDropSource();
            }

            // Double-click adds to end
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                queue(() -> {
                    SceneEditorSession.pushUndo();
                    def.items.add(option);
                    SceneEditorSession.markDirty();
                });
            }
            ImGui.popID();
            col = (col + 1) % COLS;
        }

        ImGui.endChild();
        ImGui.popStyleColor();
        EditorInspectorUi.hint(EditorI18n.t("Перетащи в сетку сверху · двойной клик — в конец"));
    }

    private void queue(Runnable op) {
        pending = op;
    }

    private static void insertOrMove(SceneTabDef def, String itemId, int targetIndex, int fromIndex) {
        List<String> items = def.items;
        if (itemId == null || itemId.isBlank()) return;
        int at = Math.max(0, Math.min(targetIndex, items.size()));
        if (fromIndex >= 0 && fromIndex < items.size()) {
            String moved = items.remove(fromIndex);
            if (fromIndex < at) at--;
            at = Math.max(0, Math.min(at, items.size()));
            items.add(at, moved);
        } else {
            // avoid exact duplicates next to each other? allow duplicates like vanilla tabs can
            items.add(at, itemId);
        }
    }

    private static void reorder(SceneTabDef def, int from, int to) {
        List<String> items = def.items;
        if (from < 0 || from >= items.size()) return;
        if (from == to) return;
        String id = items.remove(from);
        int at = to;
        if (from < to) at--;
        // dropping on the trailing empty slot → append
        if (at >= items.size()) {
            items.add(id);
        } else {
            at = Math.max(0, Math.min(at, items.size()));
            items.add(at, id);
        }
    }

    private static String shortName(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
