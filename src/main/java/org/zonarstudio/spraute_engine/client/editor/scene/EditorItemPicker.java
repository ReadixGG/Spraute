package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.ItemSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Shared item/block registry picker for the scene editor.
 * Always select from the registry (with icons + search) — never free-type ids.
 * Item pickers can attach SNBT: {@code minecraft:diamond_sword{Enchantments:[...]}}.
 */
public final class EditorItemPicker {
    private static final ImString SEARCH = new ImString(64);
    private static final int MAX_SHOWN = 400;
    private static final Map<String, ImString> NBT_BUFS = new HashMap<>();
    private static String activeNbtWidget;

    private EditorItemPicker() {}

    /** Combo field with icon preview and NBT. Returns true if value changed. */
    public static boolean combo(String label, String current, Consumer<String> onChange) {
        return combo(label, current, EditorItemIcons.items(), true, true, onChange);
    }

    public static boolean combo(String label, String current, List<String> options,
                                boolean allowClear, Consumer<String> onChange) {
        return combo(label, current, options, allowClear, false, onChange);
    }

    public static boolean combo(String label, String current, List<String> options,
                                boolean allowClear, boolean allowNbt, Consumer<String> onChange) {
        EditorInspectorUi.fieldLabel(label);
        ItemSpec spec = ItemStackScriptUtil.parseSpec(current);
        var currentIcon = EditorItemIcons.icon(spec.id());
        if (currentIcon != null) {
            ImGui.image(currentIcon.atlasGlId(), 28f, 28f,
                    currentIcon.u0(), currentIcon.v0(), currentIcon.u1(), currentIcon.v1());
            ImGui.sameLine();
        }
        String shown = comboPreview(current);
        boolean changed = false;
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##item-pick-" + label, shown)) {
            changed = renderOptions(current, options, allowClear, onChange);
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered() && current != null && !current.isBlank()) {
            ImGui.setTooltip(current);
        }
        if (allowNbt) {
            changed |= nbtField(label, current, v -> {
                onChange.accept(v);
                SceneEditorSession.markDirty();
            }, SceneEditorSession::pushUndo);
        }
        return changed;
    }

    /**
     * Compact button that opens a popup list (for craft grid slots).
     * {@code onPick} is called with the chosen id (or "" if cleared).
     */
    public static void slotButton(String imguiId, String current, float size, Consumer<String> onPick) {
        ImGui.pushID(imguiId);
        ItemSpec spec = ItemStackScriptUtil.parseSpec(current);
        var icon = EditorItemIcons.icon(spec.id());
        if (icon != null) {
            if (ImGui.imageButton("##img", icon.atlasGlId(), size, size,
                    icon.u0(), icon.v0(), icon.u1(), icon.v1())) {
                ImGui.openPopup("pick");
            }
        } else {
            String label = current == null || current.isBlank() ? "+" : "?";
            if (ImGui.button(label, size, size)) ImGui.openPopup("pick");
        }
        if (ImGui.isItemHovered() && current != null && !current.isBlank()) {
            ImGui.setTooltip(current);
        }
        if (ImGui.beginPopup("pick")) {
            renderPopupList(current, true, onPick);
            ImGui.endPopup();
        }
        ImGui.popID();
    }

    /** Popup body: search + scrollable item list. Closes popup on pick. */
    public static void renderPopupList(String current, boolean allowClear, Consumer<String> onPick) {
        renderPopupList(current, EditorItemIcons.items(), allowClear, false, onPick);
    }

    /** Popup body with a custom options list (e.g. blocks-only or mobs). */
    public static void renderPopupList(String current, List<String> options,
                                       boolean allowClear, Consumer<String> onPick) {
        renderPopupList(current, options, allowClear, false, onPick);
    }

    public static void renderPopupList(String current, List<String> options,
                                       boolean allowClear, boolean allowNbt, Consumer<String> onPick) {
        renderPopupList(current, options, allowClear, allowNbt, onPick, SceneEditorSession::pushUndo);
    }

    public static void renderPopupList(String current, List<String> options,
                                       boolean allowClear, boolean allowNbt, Consumer<String> onPick,
                                       Runnable onNbtActivate) {
        String currentId = ItemStackScriptUtil.parseSpec(current).id();
        if (allowClear && ImGui.selectable(EditorI18n.t("(очистить)"), false)) {
            onPick.accept("");
            ImGui.closeCurrentPopup();
            return;
        }
        ImGui.inputTextWithHint("##item-search", EditorI18n.t("Поиск: stone, diamond…"), SEARCH);
        String filter = SEARCH.get().trim().toLowerCase(Locale.ROOT);
        ImGui.beginChild("##item-list", 300f, 240f, true);
        int shown = 0;
        for (String option : options) {
            if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if (++shown > MAX_SHOWN) {
                ImGui.textDisabled(EditorI18n.t("…уточните поиск"));
                break;
            }
            var icon = EditorItemIcons.icon(option);
            if (icon != null) {
                ImGui.image(icon.atlasGlId(), 16f, 16f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
                ImGui.sameLine();
            }
            if (ImGui.selectable(option, option.equals(currentId))) {
                onPick.accept(keepNbt(option, current));
                ImGui.closeCurrentPopup();
            }
        }
        ImGui.endChild();
        if (allowNbt) {
            nbtField("popup", current, onPick, onNbtActivate);
        }
    }

    /** SNBT field under an item picker. Hidden when no item is selected. */
    public static boolean nbtField(String imguiId, String current, Consumer<String> onChange) {
        return nbtField(imguiId, current, onChange, SceneEditorSession::pushUndo);
    }

    public static boolean nbtField(String imguiId, String current, Consumer<String> onChange, Runnable onActivate) {
        ItemSpec spec = ItemStackScriptUtil.parseSpec(current);
        if (spec.id().isBlank()) return false;
        EditorInspectorUi.fieldLabel(EditorI18n.t("NBT"));
        String bufKey = imguiId + "#" + ImGui.getID("##nbt-slot");
        ImString buf = NBT_BUFS.computeIfAbsent(bufKey, k -> new ImString(2048));
        String inner = ItemStackScriptUtil.unwrapNbt(spec.nbt());
        if (!bufKey.equals(activeNbtWidget) && !inner.equals(buf.get())) {
            buf.set(inner);
        }
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.inputTextWithHint("##nbt-" + imguiId,
                EditorI18n.t("Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]"),
                buf);
        if (ImGui.isItemActivated() && onActivate != null) onActivate.run();
        if (ImGui.isItemActive()) activeNbtWidget = bufKey;
        else if (bufKey.equals(activeNbtWidget)) activeNbtWidget = null;
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("SNBT как в /give. Пусто — любой NBT при проверке, без NBT при выдаче."));
        }
        if (changed) {
            onChange.accept(ItemStackScriptUtil.formatSpec(spec.id(), buf.get()));
            return true;
        }
        return false;
    }

    static String comboPreview(String current) {
        if (current == null || current.isBlank()) return EditorI18n.t("(не выбрано)");
        ItemSpec spec = ItemStackScriptUtil.parseSpec(current);
        if (spec.id().isBlank()) return current;
        return spec.hasNbt() ? spec.id() + " {…}" : spec.id();
    }

    static String keepNbt(String newId, String current) {
        return ItemStackScriptUtil.formatSpec(newId, ItemStackScriptUtil.parseSpec(current).nbt());
    }

    private static boolean renderOptions(String current, List<String> options,
                                         boolean allowClear, Consumer<String> onChange) {
        boolean changed = false;
        String currentId = ItemStackScriptUtil.parseSpec(current).id();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.08f, 0.10f, 0.14f, 1f);
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##search", EditorI18n.t("Поиск: stone, diamond…"), SEARCH);
        ImGui.popStyleColor();
        String filter = SEARCH.get().trim().toLowerCase(Locale.ROOT);

        if (allowClear && ImGui.selectable(EditorI18n.t("(не выбрано)"), current == null || current.isBlank())) {
            SceneEditorSession.pushUndo();
            onChange.accept("");
            SceneEditorSession.markDirty();
            changed = true;
        }

        int shown = 0;
        for (String option : options) {
            if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if (++shown > MAX_SHOWN) {
                ImGui.textDisabled(EditorI18n.t("…уточните поиск (показано ") + MAX_SHOWN + ")");
                break;
            }
            var icon = EditorItemIcons.icon(option);
            if (icon != null) {
                ImGui.image(icon.atlasGlId(), 18f, 18f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
            } else {
                ImGui.dummy(18f, 18f);
            }
            ImGui.sameLine();
            if (ImGui.selectable(option, option.equals(currentId))) {
                SceneEditorSession.pushUndo();
                onChange.accept(keepNbt(option, current));
                SceneEditorSession.markDirty();
                changed = true;
            }
            if (ImGui.isItemHovered() && icon != null) {
                ImGui.beginTooltip();
                ImGui.image(icon.atlasGlId(), 64f, 64f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
                ImGui.text(option);
                ImGui.endTooltip();
            }
        }
        return changed;
    }
}
