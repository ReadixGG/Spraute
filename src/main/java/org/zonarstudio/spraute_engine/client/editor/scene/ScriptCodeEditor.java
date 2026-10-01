package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.zonarstudio.spraute_engine.client.UiTextMarkup;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.UiTextMarkupMenu;

/**
 * Spraute (.spr) code editor: graphite background, line gutter, syntax highlight.
 * Transparent {@code InputTextMultiline} handles editing; glyphs/caret/selection are drawn by us
 * (ImGui caret uses Text color, which must stay transparent to hide default glyphs).
 *
 * <p>ImGui cursor/selection indices are UTF-8 byte offsets; Java {@link String} uses UTF-16 chars.
 * All overlay math converts through {@link #utf8ToChar}/{@link #charToUtf8}.
 */
public final class ScriptCodeEditor {
    private static final float PAD_X = 8f;
    private static final float PAD_Y = 4f;
    private static final float GUTTER_PAD = 10f;
    private static final int TAB_SPACES = 4;

    private static final int COL_BG = rgba(0x1E2228, 1f);
    private static final int COL_GUTTER_BG = rgba(0x191C21, 1f);
    private static final int COL_GUTTER_TEXT = rgba(0x6A7380, 1f);
    private static final int COL_GUTTER_BORDER = rgba(0x59616D, 0.28f);
    private static final int COL_CARET = rgba(0xE8ECF2, 1f);
    private static final int COL_SEL = rgba(0x3B72AE, 0.50f);
    private static final int COL_DEFAULT = rgba(0xE3E6EB, 1f);
    private static final int COL_KEYWORD = rgba(0xC792EA, 1f);
    private static final int COL_STRING = rgba(0xC3E88D, 1f);
    private static final int COL_COMMENT = rgba(0x7F8B99, 1f);
    private static final int COL_NUMBER = rgba(0xF78C6C, 1f);
    private static final int COL_FUNCTION = rgba(0x82AAFF, 1f);
    private static final int COL_PROPERTY = rgba(0xFFCB6B, 1f);
    private static final int COL_OPERATOR = rgba(0x89DDFF, 1f);
    private static final int COL_PUNCT = rgba(0xA6ACB9, 1f);

    private static final Set<String> KEYWORDS = Set.of(
            "val", "var", "fun", "on", "create", "npc", "ui", "camera", "if", "else", "elif",
            "while", "for", "in", "return", "break", "continue", "true", "false", "null",
            "await", "time", "say", "async", "try", "catch", "import", "global", "world",
            "autorun", "every", "and", "or", "not", "model", "hitbox", "empty", "particle", "particleEmitter",
            "command", "playFreeze", "playOnce", "playLoop", "stop", "alwaysLookAt", "lookAt", "svg"
    );

    private static final Set<String> PROPERTIES = Set.of(
            "name", "hp", "speed", "pos", "rotate", "showName", "collision", "model", "texture",
            "idleAnim", "walkAnim", "head", "size", "background", "bg", "canClose", "can_close",
            "anchor", "anchorX", "anchorY", "scale", "crop", "feetCrop", "color", "hover",
            "radius", "gradient", "gradientDir", "border", "borderColor", "shadow", "wrap",
            "align", "tooltip", "layer", "order", "id", "text", "button", "slot", "image",
            "svg", "source", "stroke", "strokeWidth", "recolor",
            "rect", "progress", "pitch", "yaw", "lookX", "lookY", "lookZ", "animation",
            "dimension", "dim", "smooth", "track", "target", "placeholder", "label"
    );

    private static final Map<String, EditorState> STATES = new HashMap<>();

    private ScriptCodeEditor() {}

    /** @return true if the buffer was modified */
    public static boolean render(String id, ImString content) {
        String text = content.get();
        if (text == null) text = "";
        EditorState state = STATES.computeIfAbsent(id, k -> new EditorState());
        // InputText + our overlay both assume '\n' only. Windows \r\n shifts caret/selection.
        if (text.indexOf('\r') >= 0) {
            int oldCursor = state.cursor;
            int removedBeforeCursor = 0;
            for (int i = 0; i < text.length() && i < oldCursor; i++) {
                if (text.charAt(i) == '\r') removedBeforeCursor++;
            }
            content.set(text.replace("\r\n", "\n").replace("\r", "\n"));
            text = content.get();
            if (text == null) text = "";
            state.cursor = clamp(oldCursor - removedBeforeCursor, 0, text.length());
            state.selStart = state.cursor;
            state.selEnd = state.cursor;
            state.pendingCursor = state.cursor;
        }

        ImFont codeFont = SprauteImGuiBackend.codeFont();
        if (codeFont != null) ImGui.pushFont(codeFont);

        // Match InputTextMultiline: line step is FontSize when ItemSpacing.y is 0.
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        float lineH = ImGui.getFontSize();
        float availW = Math.max(40f, ImGui.getContentRegionAvailX());
        float availH = Math.max(40f, ImGui.getContentRegionAvailY());

        List<String> lines = splitLines(text);
        int lineCount = Math.max(1, lines.size());
        float gutterW = Math.max(36f, ImGui.calcTextSize(String.valueOf(lineCount)).x + GUTTER_PAD * 2f);

        float maxLineW = 0f;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, textWidth(line));
        }
        float textW = Math.max(availW - gutterW - 4f, maxLineW + 48f);
        // +1 line so Enter can scroll onto a fresh row in the same frame.
        float textH = Math.max((lineCount + 1) * lineH + PAD_Y * 2f, availH - 4f);

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.118f, 0.133f, 0.157f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.beginChild("##script-code-" + id, availW, availH, true,
                ImGuiWindowFlags.AlwaysHorizontalScrollbar);

        ImDrawList dl = ImGui.getWindowDrawList();
        float winX = ImGui.getWindowPosX();
        float winY = ImGui.getWindowPosY();
        float winW = ImGui.getWindowWidth();
        float winH = ImGui.getWindowHeight();

        float gutterRight = winX + gutterW;
        float clipT = winY;
        float clipB = winY + winH;
        float clipR = winX + winW;
        dl.addRectFilled(winX, winY, clipR, clipB, COL_BG);

        ImGui.setCursorPos(gutterW, 0f);
        float fieldX = ImGui.getCursorScreenPosX();
        float fieldY = ImGui.getCursorScreenPosY();
        float textOriginX = fieldX + PAD_X;
        float textOriginY = fieldY + PAD_Y;

        float scrollY = ImGui.getScrollY();
        int first = Math.max(0, (int) Math.floor(scrollY / lineH) - 1);
        int last = Math.min(lineCount - 1, (int) Math.ceil((scrollY + winH) / lineH) + 1);

        // Clip to the visible text pane (right of the sticky gutter). fieldX/textW
        // scroll with content, so they would otherwise leak over line numbers.
        dl.pushClipRect(gutterRight, clipT, clipR, clipB, true);
        drawSelection(dl, lines, state, textOriginX, textOriginY, lineH, text);
        for (int i = first; i <= last; i++) {
            String line = i < lines.size() ? lines.get(i) : "";
            float y = textOriginY + i * lineH;
            drawHighlightedLine(dl, line, textOriginX, y);
        }
        dl.popClipRect();

        dl.pushClipRect(winX, clipT, gutterRight, clipB, true);
        dl.addRectFilled(winX, winY, gutterRight, winY + winH, COL_GUTTER_BG);
        dl.addLine(gutterRight - 1f, winY, gutterRight - 1f, winY + winH, COL_GUTTER_BORDER, 1f);
        for (int i = first; i <= last; i++) {
            float y = textOriginY + i * lineH;
            String num = String.valueOf(i + 1);
            float numW = ImGui.calcTextSize(num).x;
            dl.addText(gutterRight - GUTTER_PAD - numW, y, COL_GUTTER_TEXT, num);
        }
        dl.popClipRect();

        // Freeze caret while completion list is navigating with arrows / Enter.
        int lockedCursor = state.cursor;
        int lockedSelS = state.selStart;
        int lockedSelE = state.selEnd;

        // Navigate the list BEFORE InputText eats ↑↓ / wheel for caret / scroll.
        boolean navFreeze = false;
        if (state.completionOpen && !state.completionItems.isEmpty()) {
            int n = state.completionItems.size();
            int prevIdx = state.completionIndex;
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow, true)) {
                state.completionIndex = Math.min(n - 1, state.completionIndex + 1);
                navFreeze = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.UpArrow, true)) {
                state.completionIndex = Math.max(0, state.completionIndex - 1);
                navFreeze = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.PageDown, true)) {
                state.completionIndex = Math.min(n - 1, state.completionIndex + 8);
                navFreeze = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.PageUp, true)) {
                state.completionIndex = Math.max(0, state.completionIndex - 8);
                navFreeze = true;
            }
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                int delta = wheel > 0f ? -Math.max(1, Math.round(Math.abs(wheel)))
                        : Math.max(1, Math.round(Math.abs(wheel)));
                state.completionIndex = clamp(state.completionIndex + delta, 0, n - 1);
                ImGui.getIO().setMouseWheel(0f);
                navFreeze = true;
            }
            if (state.completionIndex != prevIdx) state.completionScrollTo = true;
            if (navFreeze) {
                state.pendingCursor = lockedCursor;
                state.selStart = lockedSelS;
                state.selEnd = lockedSelE;
                state.cursor = lockedCursor;
            }
        }

        // Tab must not reach InputText: with AllowTabInput ImGui 1.89 often leaves the field
        // "focused" but not Active — caret moves after autocomplete, typing dies until click.
        // Steal Tab while we own the editor, then complete after the widget call.
        if (state.keepFocus && ImGui.isKeyPressed(ImGuiKey.Tab)) {
            if (ImGui.getIO().getKeyShift()) state.tabUnindentPending = true;
            else state.tabCompletePending = true;
            swallowKey(ImGuiKey.Tab);
        }
        if (state.keepFocus && state.completionOpen
                && (ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter))) {
            state.enterCompletePending = true;
            swallowKey(ImGuiKey.Enter);
            swallowKey(ImGuiKey.KeypadEnter);
        }

        if (state.requestFocus) {
            ImGui.setKeyboardFocusHere(0);
            state.requestFocus = false;
        }

        ImGui.setCursorPos(gutterW, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, 0f, 0f, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, PAD_X, PAD_Y);

        // No AllowTabInput — Tab is handled above so focus/active state stays intact.
        int flags = ImGuiInputTextFlags.CallbackAlways
                | ImGuiInputTextFlags.CallbackEdit
                | ImGuiInputTextFlags.CallbackCharFilter;
        boolean changed = ImGui.inputTextMultiline("##script-body-" + id, content, textW, textH,
                flags, state.callback);
        boolean active = ImGui.isItemActive() || ImGui.isItemFocused();
        state.keepFocus = active;
        boolean modified = changed;

        if (ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            state.coordsMenuOpen = true;
            state.coordsMenuJustOpened = true;
            state.coordsMenuX = ImGui.getMousePosX();
            state.coordsMenuY = ImGui.getMousePosY();
            if (state.selStart != state.selEnd) {
                state.coordsMenuSelStart = state.selStart;
                state.coordsMenuSelEnd = state.selEnd;
            } else {
                state.coordsMenuSelStart = state.lastSelStart;
                state.coordsMenuSelEnd = state.lastSelEnd;
            }
        }

        if (navFreeze) {
            state.cursor = lockedCursor;
            state.selStart = lockedSelS;
            state.selEnd = lockedSelE;
            state.pendingCursor = lockedCursor;
        }

        boolean appliedEdit = false;
        if (state.tabCompletePending) {
            state.tabCompletePending = false;
            if (state.completionOpen && !state.completionItems.isEmpty()) {
                applyCompletionEntry(content, state,
                        state.completionItems.get(clamp(state.completionIndex, 0, state.completionItems.size() - 1)));
                state.completionOpen = false;
                modified = true;
                appliedEdit = true;
            } else if (!applyCompletion(content, state, false)) {
                insertAtCursor(content, state, "    ");
                modified = true;
                appliedEdit = true;
            } else {
                modified = true;
                appliedEdit = true;
            }
        }
        if (state.enterCompletePending) {
            state.enterCompletePending = false;
            if (state.completionOpen && !state.completionItems.isEmpty()) {
                applyCompletionEntry(content, state,
                        state.completionItems.get(clamp(state.completionIndex, 0, state.completionItems.size() - 1)));
                state.completionOpen = false;
                modified = true;
                appliedEdit = true;
            }
        }
        if (state.tabUnindentPending) {
            state.tabUnindentPending = false;
            if (unindentLine(content, state)) {
                modified = true;
                appliedEdit = true;
            }
        }
        // Autocomplete rewrites the buffer after InputText returns — keep Active so the next
        // CallbackAlways can apply pendingRewrite and keystrokes keep flowing.
        if (appliedEdit) {
            state.keepFocus = true;
            if (!ImGui.isItemActive()) state.requestFocus = true;
        }
        if (active && ImGui.isKeyPressed(ImGuiKey.Space) && ImGui.getIO().getKeyCtrl()) {
            refreshCompletion(content, state, true);
        }
        if (state.completionDirty) {
            state.completionDirty = false;
            if (active) refreshCompletion(content, state, false);
        }
        if (state.completionOpen && active) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) state.completionOpen = false;
        }
        // Keep list open while the floating menu is hovered (clicking it blurs InputText).
        if (!active && !state.completionWindowHovered) state.completionOpen = false;

        String curText = content.get();
        if (curText == null) curText = "";
        if (modified) {
            if (curText.indexOf('\r') >= 0) {
                content.set(curText.replace("\r\n", "\n").replace("\r", "\n"));
                curText = content.get();
                if (curText == null) curText = "";
            }
            if (curText.indexOf('\t') >= 0) {
                content.set(curText.replace("\t", " ".repeat(TAB_SPACES)));
                curText = content.get();
                if (curText == null) curText = "";
            }
            text = curText;
            lines = splitLines(text);
            lineCount = Math.max(1, lines.size());
            float neededH = Math.max((lineCount + 1) * lineH + PAD_Y * 2f, availH - 4f);
            if (neededH > textH) {
                ImGui.setCursorPos(gutterW, textH);
                ImGui.dummy(1f, neededH - textH);
                textH = neededH;
            }
        }

        ImGui.popStyleVar(1); // FramePadding
        ImGui.popStyleColor(5);

        float caretScreenX = textOriginX;
        float caretScreenY = textOriginY;
        if (active) {
            String cur = content.get();
            if (cur == null) cur = "";
            boolean blinkOn = (((int) (ImGui.getTime() * 2.4f)) & 1) == 0;
            int caret = clamp(state.cursor, 0, cur.length());
            int[] lc = lineColAt(cur, caret);
            List<String> curLines = splitLines(cur);
            String line = lc[0] < curLines.size() ? curLines.get(lc[0]) : "";
            int col = clamp(lc[1], 0, line.length());
            float caretLocalX = textWidth(line.substring(0, col));
            caretScreenX = textOriginX + caretLocalX;
            caretScreenY = textOriginY + lc[0] * lineH;
            state.caretScreenX = caretScreenX;
            state.caretScreenY = caretScreenY;
            state.caretLineH = lineH;
            if (blinkOn || state.selStart != state.selEnd) {
                if (blinkOn && caretScreenX >= gutterRight) {
                    dl.pushClipRect(gutterRight, clipT, clipR, clipB, true);
                    dl.addLine(caretScreenX, caretScreenY + 1f, caretScreenX, caretScreenY + lineH - 2f,
                            COL_CARET, 1.5f);
                    dl.popClipRect();
                }
            }
            // Only when caret moves — otherwise wheel/scrollbar can't scroll away from it.
            if (caret != state.lastEnsuredCaret) {
                ensureCaretVisible(lc[0], caretLocalX, lineH, winH, winW - gutterW);
                state.lastEnsuredCaret = caret;
            }
        }

        ImGui.endChild();
        renderCompletionList(id, content, state);
        if (renderCoordsMenu(id, content, state)) modified = true;
        renderHintBar(curText, state);
        ImGui.popStyleVar(2); // WindowPadding + ItemSpacing
        ImGui.popStyleColor(); // ChildBg
        if (codeFont != null) ImGui.popFont();
        return modified;
    }

    /** Floating coords menu (same pattern as completion — survives mouse move). */
    private static boolean renderCoordsMenu(String id, ImString content, EditorState state) {
        if (!state.coordsMenuOpen) return false;
        boolean modified = false;
        ImGui.setNextWindowPos(state.coordsMenuX, state.coordsMenuY, ImGuiCond.Appearing);
        VisualScriptEditor.pushMenuStyle();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 10f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.086f, 0.094f, 0.117f, 0.99f);
        int flags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoDocking;
        boolean stay = true;
        if (ImGui.begin("##script-coords-menu-" + id, flags)) {
            boolean hasSel = state.coordsMenuSelStart != state.coordsMenuSelEnd;
            if (hasSel) {
                ImGui.textDisabled(EditorI18n.t("Эффекты текста"));
                ImGui.separator();
                String tag = UiTextMarkupMenu.drawItems(content.get(), state.coordsMenuSelStart, state.coordsMenuSelEnd);
                if (tag != null) {
                    UiTextMarkup.Caret caret = UiTextMarkup.toggle(content.get(),
                            state.coordsMenuSelStart, state.coordsMenuSelEnd, tag);
                    queueBufferRewrite(content, state, caret.text(), caret.cursor());
                    state.selStart = caret.selStart();
                    state.selEnd = caret.selEnd();
                    modified = true;
                    stay = false;
                }
            } else {
            String preview = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoords();
            String cx = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('x');
            String cy = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('y');
            String cz = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('z');
            boolean hasBlock = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.hasViewTargetBlock();
            String bx = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('x');
            String by = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('y');
            String bz = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('z');

            ImGui.textDisabled(EditorI18n.t("Камера: ") + preview);
            ImGui.separator();
            if (ImGui.selectable(EditorI18n.t("Камера — X, Y, Z"))) {
                insertOrReplaceAtCursor(content, state, preview);
                modified = true;
                stay = false;
            }
            if (ImGui.selectable(EditorI18n.t("Камера — X"))) {
                insertOrReplaceAtCursor(content, state, cx);
                modified = true;
                stay = false;
            }
            if (ImGui.selectable(EditorI18n.t("Камера — Y"))) {
                insertOrReplaceAtCursor(content, state, cy);
                modified = true;
                stay = false;
            }
            if (ImGui.selectable(EditorI18n.t("Камера — Z"))) {
                insertOrReplaceAtCursor(content, state, cz);
                modified = true;
                stay = false;
            }
            ImGui.separator();
            ImGui.textDisabled(EditorI18n.t("Блок: ") + (hasBlock
                    ? org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoords() : "—"));
            ImGui.separator();
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — X, Y, Z"))) {
                insertOrReplaceAtCursor(content, state,
                        org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoords());
                modified = true;
                stay = false;
            }
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — X"))) {
                insertOrReplaceAtCursor(content, state, bx);
                modified = true;
                stay = false;
            }
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — Y"))) {
                insertOrReplaceAtCursor(content, state, by);
                modified = true;
                stay = false;
            }
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — Z"))) {
                insertOrReplaceAtCursor(content, state, bz);
                modified = true;
                stay = false;
            }
            if (!hasBlock) {
                ImGui.beginDisabled();
                ImGui.selectable(EditorI18n.t("Блок — X, Y, Z"));
                ImGui.selectable(EditorI18n.t("Блок — X"));
                ImGui.selectable(EditorI18n.t("Блок — Y"));
                ImGui.selectable(EditorI18n.t("Блок — Z"));
                ImGui.endDisabled();
            }
            }
            if (!state.coordsMenuJustOpened) {
                if (ImGui.isMouseClicked(0) && !ImGui.isWindowHovered(
                        ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem)) {
                    stay = false;
                }
                if (ImGui.isMouseClicked(1) && !ImGui.isWindowHovered()) stay = false;
                if (ImGui.isKeyPressed(ImGuiKey.Escape)) stay = false;
            }
            ImGui.end();
        }
        ImGui.popStyleColor();
        ImGui.popStyleVar();
        VisualScriptEditor.popMenuStyle();
        state.coordsMenuJustOpened = false;
        state.coordsMenuOpen = stay;
        return modified;
    }

    /** Scroll the code child so the caret line/column stays inside the viewport. */
    private static void ensureCaretVisible(int line, float caretLocalX, float lineH,
                                           float viewH, float viewW) {
        float caretTop = PAD_Y + line * lineH;
        float caretBottom = caretTop + lineH;
        float marginY = lineH * 0.75f;
        float scrollY = ImGui.getScrollY();
        float maxScrollY = ImGui.getScrollMaxY();
        if (caretBottom > scrollY + viewH - marginY) {
            ImGui.setScrollY(Math.min(maxScrollY, caretBottom - viewH + marginY));
        } else if (caretTop < scrollY + marginY) {
            ImGui.setScrollY(Math.max(0f, caretTop - marginY));
        }

        float caretX = PAD_X + caretLocalX;
        float marginX = 32f;
        float scrollX = ImGui.getScrollX();
        float maxScrollX = ImGui.getScrollMaxX();
        if (caretX > scrollX + viewW - marginX) {
            ImGui.setScrollX(Math.min(maxScrollX, caretX - viewW + marginX));
        } else if (caretX < scrollX + marginX) {
            ImGui.setScrollX(Math.max(0f, caretX - marginX));
        }
    }

    private static void drawSelection(ImDrawList dl, List<String> lines, EditorState state,
                                      float originX, float originY, float lineH, String fullText) {
        int a = Math.min(state.selStart, state.selEnd);
        int b = Math.max(state.selStart, state.selEnd);
        if (a == b) return;
        int[] la = lineColAt(fullText, a);
        int[] lb = lineColAt(fullText, b);
        for (int line = la[0]; line <= lb[0]; line++) {
            if (line < 0 || line >= lines.size()) continue;
            String s = lines.get(line);
            int col0 = line == la[0] ? clamp(la[1], 0, s.length()) : 0;
            int col1 = line == lb[0] ? clamp(lb[1], 0, s.length()) : s.length();
            float x0 = originX + textWidth(s.substring(0, col0));
            float x1 = originX + textWidth(s.substring(0, col1));
            if (x1 <= x0) x1 = x0 + 4f;
            float y = originY + line * lineH;
            dl.addRectFilled(x0, y, x1, y + lineH - 1f, COL_SEL);
        }
    }

    private static void renderHintBar(String text, EditorState state) {
        ScriptEditorHints.Entry hint = ScriptEditorHints.hintAt(text, state.cursor);
        ImGui.spacing();
        if (hint == null) {
            ImGui.textDisabled(EditorI18n.t("Tab — выбрать из списка · Ctrl+Space — все подсказки"));
            return;
        }
        ImGui.textColored(0.51f, 0.67f, 1f, 1f, hint.signature());
        ImGui.sameLine();
        ImGui.textDisabled("—");
        ImGui.sameLine();
        ImGui.textWrapped(hint.doc());
    }

    /**
     * Floating completion list (not an ImGui popup) so InputText keeps focus.
     * Popups fight the active InputText and flash open/closed.
     */
    private static void renderCompletionList(String id, ImString content, EditorState state) {
        if (!state.completionOpen || state.completionItems.isEmpty()) return;

        float x = state.caretScreenX;
        float y = state.caretScreenY + Math.max(14f, state.caretLineH);
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSizeConstraints(280f, 0f, 520f, 260f);
        int flags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoDocking;

        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.12f, 0.14f, 0.18f, 0.97f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.35f, 0.42f, 0.55f, 0.85f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 6f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 4f);
        if (ImGui.begin("##script-completion-" + id, flags)) {
            state.completionWindowHovered = ImGui.isWindowHovered(
                    imgui.flag.ImGuiHoveredFlags.ChildWindows | imgui.flag.ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
            ImGui.textDisabled(EditorI18n.t("Дополнение  (↑↓ / колесо · Tab/Enter · Esc)"));
            ImGui.separator();
            float rowH = ImGui.getTextLineHeightWithSpacing() + 2f;
            float listH = Math.min(220f, Math.max(rowH + 4f, state.completionItems.size() * rowH + 4f));
            ImGui.beginChild("##cmp-scroll-" + id, 0f, listH, false,
                    ImGuiWindowFlags.AlwaysVerticalScrollbar);
            for (int i = 0; i < state.completionItems.size(); i++) {
                ScriptEditorHints.Entry e = state.completionItems.get(i);
                boolean selected = i == state.completionIndex;
                if (selected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, 0.25f, 0.40f, 0.62f, 0.95f);
                    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.30f, 0.48f, 0.72f, 1f);
                }
                if (ImGui.selectable(e.signature() + "##cmp-" + i, selected,
                        imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                    applyCompletionEntry(content, state, e);
                    state.completionOpen = false;
                }
                if (ImGui.isItemHovered()) {
                    state.completionIndex = i;
                    ImGui.setTooltip(e.doc());
                }
                if (selected) ImGui.popStyleColor(2);
                if (selected && state.completionScrollTo) {
                    ImGui.setScrollHereY(0.35f);
                }
            }
            if (state.completionScrollTo) state.completionScrollTo = false;
            ImGui.endChild();
        } else {
            state.completionWindowHovered = false;
        }
        ImGui.end();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(2);
    }

    private static void refreshCompletion(ImString content, EditorState state, boolean explicit) {
        String text = content.get();
        if (text == null) text = "";
        int[] bounds = ScriptEditorHints.wordBounds(text, state.cursor);
        String prefix = bounds[0] < bounds[1] ? text.substring(bounds[0], bounds[1]) : "";
        if (!explicit && prefix.isEmpty()) {
            state.completionOpen = false;
            state.completionItems = List.of();
            return;
        }
        state.completionItems = ScriptEditorHints.complete(prefix, text, state.cursor);
        if (state.completionItems.isEmpty()) {
            state.completionOpen = false;
            return;
        }
        // Don't keep the list open if the only match is already fully typed (unless Ctrl+Space).
        if (!explicit && state.completionItems.size() == 1) {
            ScriptEditorHints.Entry only = state.completionItems.get(0);
            String key = only.key();
            int dot = key.lastIndexOf('.');
            String shortKey = dot >= 0 ? key.substring(dot + 1) : key;
            if (shortKey.equalsIgnoreCase(prefix) && !only.insert().contains("\n")
                    && !only.insert().contains("{") && !only.insert().contains("(")) {
                state.completionOpen = false;
                return;
            }
        }
        state.completionIndex = clamp(state.completionIndex, 0, state.completionItems.size() - 1);
        state.completionOpen = true;
        state.completionScrollTo = true;
    }

    private static boolean applyCompletion(ImString content, EditorState state, boolean fromPopup) {
        String text = content.get();
        if (text == null) text = "";
        int[] bounds = ScriptEditorHints.wordBounds(text, state.cursor);
        String word = bounds[0] < bounds[1] ? text.substring(bounds[0], bounds[1]) : "";
        String line = ScriptEditorHints.linePrefixAt(text, state.cursor);
        String prefixKey = !line.isEmpty() ? line : word;
        List<ScriptEditorHints.Entry> items = ScriptEditorHints.complete(word, text, state.cursor);
        if (items.isEmpty()) return false;
        if (!fromPopup) {
            if (state.lastCompletionPrefix != null && state.lastCompletionPrefix.equals(prefixKey)
                    && state.completionCycleIndex >= 0) {
                state.completionCycleIndex = (state.completionCycleIndex + 1) % items.size();
            } else {
                state.completionCycleIndex = 0;
                state.lastCompletionPrefix = prefixKey;
            }
        } else if (state.completionIndex >= 0 && state.completionIndex < items.size()) {
            state.completionCycleIndex = state.completionIndex;
        } else {
            state.completionCycleIndex = 0;
        }
        return applyCompletionEntry(content, state, items.get(state.completionCycleIndex));
    }

    private static boolean applyCompletionEntry(ImString content, EditorState state, ScriptEditorHints.Entry entry) {
        String text = content.get();
        if (text == null) text = "";
        int cursor = clamp(state.cursor, 0, text.length());
        int lineStart = text.lastIndexOf('\n', cursor - 1) + 1;
        String line = text.substring(lineStart, cursor);
        String lineTrim = line.trim().toLowerCase(java.util.Locale.ROOT);
        String key = entry.key().toLowerCase(java.util.Locale.ROOT);

        int replaceStart;
        int replaceEnd = cursor;
        if (!lineTrim.isEmpty() && (key.startsWith(lineTrim) || lineTrim.startsWith(key))) {
            replaceStart = lineStart + line.indexOf(line.trim());
            if (replaceStart < lineStart) replaceStart = lineStart;
        } else {
            int[] bounds = ScriptEditorHints.wordBounds(text, cursor);
            replaceStart = bounds[0];
            replaceEnd = bounds[1];
        }

        ScriptEditorHints.InsertResult ins = ScriptEditorHints.resolveInsert(entry);
        String newText = text.substring(0, replaceStart) + ins.text() + text.substring(replaceEnd);
        int newCursor = replaceStart + ins.cursor();
        queueBufferRewrite(content, state, newText, newCursor);
        state.lastCompletionPrefix = null;
        state.completionCycleIndex = -1;
        state.completionOpen = false;
        return true;
    }

    private static void insertAtCursor(ImString content, EditorState state, String insert) {
        insertOrReplaceAtCursor(content, state, insert);
    }

    private static void insertOrReplaceAtCursor(ImString content, EditorState state, String insert) {
        String text = content.get();
        if (text == null) text = "";
        if (insert == null) insert = "";
        int a = clamp(Math.min(state.selStart, state.selEnd), 0, text.length());
        int b = clamp(Math.max(state.selStart, state.selEnd), 0, text.length());
        if (a == b) {
            a = b = clamp(state.cursor, 0, text.length());
        }
        queueBufferRewrite(content, state, text.substring(0, a) + insert + text.substring(b),
                a + insert.length());
    }

    private static boolean unindentLine(ImString content, EditorState state) {
        String text = content.get();
        if (text == null) return false;
        int c = clamp(state.cursor, 0, text.length());
        int lineStart = text.lastIndexOf('\n', c - 1) + 1;
        int remove = 0;
        while (remove < 4 && lineStart + remove < c && text.charAt(lineStart + remove) == ' ') remove++;
        if (remove == 0) return false;
        queueBufferRewrite(content, state,
                text.substring(0, lineStart) + text.substring(lineStart + remove),
                c - remove);
        return true;
    }

    /**
     * Queue a full-buffer rewrite. {@code content.set} updates the overlay immediately, but while
     * InputText is active ImGui keeps its own UTF-8 buffer and would overwrite ImString next frame —
     * so {@link EditorState#pendingRewrite} is applied in CallbackAlways via delete/insert.
     */
    private static void queueBufferRewrite(ImString content, EditorState state, String newText, int newCursor) {
        if (newText == null) newText = "";
        int caret = clamp(newCursor, 0, newText.length());
        content.set(newText);
        state.pendingRewrite = newText;
        state.pendingCursor = caret;
        state.cursor = caret;
        state.selStart = caret;
        state.selEnd = caret;
        state.keepFocus = true;
        state.requestFocus = true;
    }

    /** Drop a key so ImGui widgets later this frame (esp. InputText) do not see it. */
    private static void swallowKey(int key) {
        var io = ImGui.getIO();
        io.addKeyEvent(key, false);
        int idx = ImGui.getKeyIndex(key);
        if (idx >= 0 && idx < io.getKeysDown().length) {
            io.setKeysDown(idx, false);
        }
    }

    /**
     * Pixel width of {@code text} matching {@link ImDrawList#addText} (font CalcTextSizeA,
     * no ImGui::CalcTextSize integer rounding). Used for caret/selection and highlighter X.
     */
    private static float textWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;
        ImFont font = ImGui.getFont();
        if (font == null) return ImGui.calcTextSizeX(text);
        return font.calcTextSizeAX(ImGui.getFontSize(), Float.MAX_VALUE, 0f, text);
    }

    private static void drawHighlightedLine(ImDrawList dl, String line, float originX, float y) {
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            float x = originX + textWidth(line.substring(0, i));
            if (c == '#') {
                dl.addText(x, y, COL_COMMENT, line.substring(i));
                return;
            }
            if (Character.isWhitespace(c)) {
                int j = i + 1;
                while (j < n && Character.isWhitespace(line.charAt(j))) j++;
                i = j;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    if (line.charAt(j) == '\\' && j + 1 < n) {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == '"') {
                        j++;
                        break;
                    }
                    j++;
                }
                dl.addText(x, y, COL_STRING, line.substring(i, j));
                i = j;
                continue;
            }
            if (c == '-' || Character.isDigit(c)) {
                if (!(c == '-' && (i + 1 >= n || !Character.isDigit(line.charAt(i + 1))))) {
                    int j = i + (c == '-' ? 1 : 0);
                    while (j < n && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '.')) j++;
                    if (j > i && (c != '-' || j > i + 1)) {
                        dl.addText(x, y, COL_NUMBER, line.substring(i, j));
                        i = j;
                        continue;
                    }
                }
            }
            if (isIdentStart(c)) {
                int j = i + 1;
                while (j < n && isIdentPart(line.charAt(j))) j++;
                String tok = line.substring(i, j);
                int color = COL_DEFAULT;
                if (KEYWORDS.contains(tok)) color = COL_KEYWORD;
                else if (PROPERTIES.contains(tok)) color = COL_PROPERTY;
                else if (ScriptEditorHints.lookup(tok) != null) color = COL_FUNCTION;
                else {
                    int k = j;
                    while (k < n && Character.isWhitespace(line.charAt(k))) k++;
                    if (k < n && line.charAt(k) == '(') color = COL_FUNCTION;
                }
                dl.addText(x, y, color, tok);
                i = j;
                continue;
            }
            if ("+-*/%=<>!&|".indexOf(c) >= 0) {
                int j = i + 1;
                while (j < n && "+-*/%=<>!&|".indexOf(line.charAt(j)) >= 0) j++;
                dl.addText(x, y, COL_OPERATOR, line.substring(i, j));
                i = j;
                continue;
            }
            if ("{}()[],;.:".indexOf(c) >= 0) {
                dl.addText(x, y, COL_PUNCT, line.substring(i, i + 1));
                i++;
                continue;
            }
            dl.addText(x, y, COL_DEFAULT, line.substring(i, i + 1));
            i++;
        }
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
        return lines;
    }

    /** Map a Java char index to (line, column) assuming {@code '\n'}-only line breaks. */
    private static int[] lineColAt(String text, int index) {
        if (text == null || text.isEmpty()) return new int[]{0, 0};
        int line = 0;
        int col = 0;
        int n = Math.max(0, Math.min(index, text.length()));
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    /** ImGui UTF-8 byte offset → Java char index. */
    private static int utf8ToChar(String text, int bytePos) {
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

    /** Java char index → ImGui UTF-8 byte offset. */
    private static int charToUtf8(String text, int charPos) {
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

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int rgba(int rgb, float a) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int ai = Math.round(a * 255f) & 0xFF;
        return (ai << 24) | (b << 16) | (g << 8) | r;
    }

    private static char closingBracket(char open) {
        return switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case '"' -> '"';
            default -> 0;
        };
    }

    private static boolean isInsideString(String text, int pos) {
        if (text == null || pos <= 0) return false;
        boolean inString = false;
        int limit = Math.min(pos, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
        }
        return inString;
    }

    /** {@code pos} is ImGui UTF-8 byte offset into {@code data.getBuf()}. */
    private static void handleAutoPair(ImGuiInputTextCallbackData data, char open) {
        char close = closingBracket(open);
        if (close == 0 || data.hasSelection()) return;
        int pos = data.getCursorPos();
        String buf = data.getBuf();
        if (buf == null) buf = "";
        int charPos = utf8ToChar(buf, pos);
        if (open == '"' && isInsideString(buf, charPos)) return;
        data.insertChars(pos, String.valueOf(close));
        data.setCursorPos(pos);
    }

    private static boolean skipOverClosing(ImGuiInputTextCallbackData data, char close) {
        int pos = data.getCursorPos();
        String buf = data.getBuf();
        if (buf == null || pos >= buf.getBytes(StandardCharsets.UTF_8).length) return false;
        // Compare at UTF-8 byte position via char conversion.
        int charPos = utf8ToChar(buf, pos);
        if (charPos >= buf.length() || buf.charAt(charPos) != close) return false;
        if (close == '"' && !isInsideString(buf, charPos)) return false;
        data.setEventChar(0);
        data.setCursorPos(charToUtf8(buf, charPos + 1));
        return true;
    }

    private static final class EditorState {
        /** Cursor / selection in Java char indices. */
        int cursor;
        int selStart;
        int selEnd;
        /** When >= 0, next callback forces ImGui cursor to this Java char index. */
        int pendingCursor = -1;
        /** Full buffer replacement applied inside the InputText callback (see queueBufferRewrite). */
        String pendingRewrite;
        boolean tabCompletePending;
        boolean tabUnindentPending;
        boolean enterCompletePending;
        /** Last frame had keyboard ownership — used to steal Tab before InputText. */
        boolean keepFocus;
        /** Re-activate InputText next frame after autocomplete rewrite. */
        boolean requestFocus;
        boolean completionDirty;
        boolean completionOpen;
        boolean completionScrollTo;
        boolean completionWindowHovered;
        int completionIndex;
        int completionCycleIndex = -1;
        String lastCompletionPrefix;
        List<ScriptEditorHints.Entry> completionItems = List.of();
        float caretScreenX;
        float caretScreenY;
        float caretLineH = 15f;
        /** Last caret index we auto-scrolled to; -1 = never. */
        int lastEnsuredCaret = -1;
        boolean coordsMenuOpen;
        boolean coordsMenuJustOpened;
        float coordsMenuX;
        float coordsMenuY;
        int coordsMenuSelStart;
        int coordsMenuSelEnd;
        int lastSelStart;
        int lastSelEnd;

        final ImGuiInputTextCallback callback = new ImGuiInputTextCallback() {
            @Override
            public void accept(ImGuiInputTextCallbackData data) {
                if (pendingRewrite != null
                        && data.hasEventFlag(ImGuiInputTextFlags.CallbackAlways)) {
                    String rewrite = pendingRewrite;
                    int caret = pendingCursor >= 0 ? pendingCursor : rewrite.length();
                    pendingRewrite = null;
                    pendingCursor = -1;
                    int oldLen = data.getBufTextLen();
                    if (oldLen > 0) data.deleteChars(0, oldLen);
                    data.insertChars(0, rewrite);
                    int b = charToUtf8(rewrite, clamp(caret, 0, rewrite.length()));
                    data.setCursorPos(b);
                    data.setSelectionStart(b);
                    data.setSelectionEnd(b);
                    cursor = clamp(caret, 0, rewrite.length());
                    selStart = cursor;
                    selEnd = cursor;
                    // Keep going so cursor sync stays consistent if more flags are set.
                }

                String buf = data.getBuf();
                if (buf == null) buf = "";

                if (pendingCursor >= 0) {
                    int b = charToUtf8(buf, pendingCursor);
                    data.setCursorPos(b);
                    data.setSelectionStart(b);
                    data.setSelectionEnd(b);
                    pendingCursor = -1;
                }

                cursor = utf8ToChar(buf, data.getCursorPos());
                selStart = utf8ToChar(buf, data.getSelectionStart());
                selEnd = utf8ToChar(buf, data.getSelectionEnd());
                if (selStart != selEnd) {
                    lastSelStart = selStart;
                    lastSelEnd = selEnd;
                }

                if (data.hasEventFlag(ImGuiInputTextFlags.CallbackCharFilter)) {
                    int ev = data.getEventChar();
                    // Tab is swallowed before InputText (see render); discard stray '\t' chars.
                    if (ev == '\t') {
                        data.setEventChar(0);
                        return;
                    }
                    if (completionOpen && (ev == '\n' || ev == '\r')) {
                        data.setEventChar(0);
                        enterCompletePending = true;
                        return;
                    }
                    if (ev > 0 && ev < 128) {
                        char ch = (char) ev;
                        if (ch == ')' || ch == ']' || ch == '}' || ch == '"') {
                            skipOverClosing(data, ch);
                        }
                    }
                }

                if (data.hasEventFlag(ImGuiInputTextFlags.CallbackEdit)) {
                    int ev = data.getEventChar();
                    if (ev > 0 && ev < 128) {
                        char ch = (char) ev;
                        if (ch == '(' || ch == '[' || ch == '{' || ch == '"') {
                            handleAutoPair(data, ch);
                            buf = data.getBuf();
                            if (buf == null) buf = "";
                            cursor = utf8ToChar(buf, data.getCursorPos());
                        }
                    }
                    completionDirty = true;
                }
            }
        };
    }
}
