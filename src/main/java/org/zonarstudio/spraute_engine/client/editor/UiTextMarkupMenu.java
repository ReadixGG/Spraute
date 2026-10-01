package org.zonarstudio.spraute_engine.client.editor;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.zonarstudio.spraute_engine.client.UiTextMarkup;

/**
 * Shared RMB picker for inline {@link UiTextMarkup} tags (inspector, visual fields, code editor).
 */
public final class UiTextMarkupMenu {
    public record Effect(String tag, String label) {}

    public static final Effect[] EFFECTS = {
            new Effect("bold", "Жирный"),
            new Effect("italic", "Курсив"),
            new Effect("underline", "Подчёркивание"),
            new Effect("strikethrough", "Зачёркивание"),
            new Effect("shadow", "Тень"),
            new Effect("outline", "Обводка"),
            new Effect("glow", "Свечение"),
            new Effect("gradient", "Градиент"),
            new Effect("rainbow", "Радуга"),
            new Effect("wave", "Волна"),
            new Effect("pulse", "Пульс"),
            new Effect("shake", "Тряска"),
            new Effect("wiggle", "Покачивание"),
    };

    private UiTextMarkupMenu() {}

    /** Renders selectable effect rows. Returns the chosen tag, or {@code null}. */
    public static String drawItems(String text, int selStart, int selEnd) {
        String chosen = null;
        for (Effect e : EFFECTS) {
            boolean on = UiTextMarkup.selectionWrapped(text, selStart, selEnd, e.tag);
            if (on) {
                ImGui.pushStyleColor(ImGuiCol.Header, 0.25f, 0.45f, 0.68f, 0.85f);
                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.30f, 0.52f, 0.78f, 0.95f);
            }
            if (ImGui.selectable(EditorI18n.t(e.label) + "##mk-fx-" + e.tag, on)) {
                chosen = e.tag;
            }
            if (on) ImGui.popStyleColor(2);
        }
        return chosen;
    }

    public static int utf8ToChar(String text, int bytePos) {
        if (text == null || bytePos <= 0) return 0;
        int bytes = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int len = utf8Bytes(cp);
            if (bytes + len > bytePos) break;
            bytes += len;
            i += Character.charCount(cp);
            if (bytes == bytePos) break;
        }
        return i;
    }

    public static int charToUtf8(String text, int charPos) {
        if (text == null || charPos <= 0) return 0;
        int c = Math.min(charPos, text.length());
        int bytes = 0;
        int i = 0;
        while (i < c) {
            int cp = text.codePointAt(i);
            bytes += utf8Bytes(cp);
            i += Character.charCount(cp);
        }
        return bytes;
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }
}
