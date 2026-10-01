package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.audio.ClientWorkspaceSounds;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Shared sound dropdown: import button, custom files first (newest on top), then Minecraft. */
public final class EditorSoundPicker {
    private EditorSoundPicker() {}

    public static void renderList(String current, ImString search, Consumer<String> onPick) {
        if (ImGui.button(EditorI18n.t("Импортировать .ogg / .mp3") + "##snd-import")) {
            List<String> ids = EditorSoundImport.pickAndImport();
            if (!ids.isEmpty()) {
                String id = ids.get(0);
                ClientWorkspaceSounds.prepare(id);
                onPick.accept(id);
                ImGui.closeCurrentPopup();
                return;
            }
        }
        ImGui.sameLine();
        ImGui.textDisabled(EditorI18n.t("или перетащите в окно"));

        ImGui.inputTextWithHint("##snd-search", EditorI18n.t("Поиск звука…"), search);
        String filter = search.get().trim().toLowerCase(Locale.ROOT);
        List<String> custom = EditorAssetIndex.customSounds();
        ImGui.beginChild("##snd-list", 360f, 280f, true);
        if (ImGui.selectable(EditorI18n.t("(не выбран)"), current == null || current.isEmpty())) {
            onPick.accept("");
            ImGui.closeCurrentPopup();
        }
        ImGui.separator();
        ImGui.textDisabled(EditorI18n.t("Свои (.ogg / .mp3)"));
        int customShown = 0;
        for (String id : custom) {
            String label = EditorAssetIndex.soundLabel(id);
            if (!filter.isEmpty()
                    && !id.toLowerCase(Locale.ROOT).contains(filter)
                    && !label.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            customShown++;
            if (ImGui.selectable(EditorSoundImport.selectableLabel(label, id) + "##c-" + id, id.equals(current))) {
                ClientWorkspaceSounds.prepare(id);
                onPick.accept(id);
                ImGui.closeCurrentPopup();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(id);
            }
        }
        if (customShown == 0) {
            ImGui.textDisabled(EditorI18n.t("положите файл в spraute_engine/sounds/"));
        }
        ImGui.separator();
        ImGui.textDisabled(EditorI18n.t("Minecraft"));
        int vanillaShown = 0;
        for (String id : EditorAssetIndex.sounds()) {
            if (custom.contains(id)) continue;
            if (!filter.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if (++vanillaShown > 250) {
                ImGui.textDisabled(EditorI18n.t("…уточните поиск"));
                break;
            }
            if (ImGui.selectable(id + "##v", id.equals(current))) {
                onPick.accept(id);
                ImGui.closeCurrentPopup();
            }
        }
        ImGui.endChild();
    }
}
