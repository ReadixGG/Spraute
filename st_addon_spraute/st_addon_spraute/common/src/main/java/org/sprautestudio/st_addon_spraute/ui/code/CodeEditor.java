package org.sprautestudio.st_addon_spraute.ui.code;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code editor widget: syntax/semantic highlighting, diagnostics, completion with snippets,
 * signature help, hover docs, find/replace, minimap, bracket matching and line operations.
 */
public class CodeEditor extends Widget implements UI.CursorProvider {
    public static final int LINE_H = 12;
    private static final int PAD_TOP = 4;
    private static final int MINIMAP_W = 64;

    private TextDocument doc;
    private LanguageSupport lang;
    private LanguageSupport.Analysis analysis;
    private int analyzedVersion = -1;
    private int caret, anchor;
    private int preferredX = -1;
    private double scrollX, scrollY, targetScrollY;
    private long blinkStart = System.currentTimeMillis();
    public boolean readOnly;
    public boolean showMinimap = true;
    public Consumer<CodeEditor> onChange;
    public Consumer<CodeEditor> onCaretMove;
    public Runnable onSave;
    /** Called when the user asks for the "current player position" insertion (Alt+P). */
    public java.util.function.Supplier<String> positionProvider;
    /** Adds items to the right-click menu (the caret is already where the user clicked). */
    public java.util.function.Consumer<org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu> contextExtras;

    private final CompletionPopup popup = new CompletionPopup(this::acceptCompletion);
    private boolean popupOpen;
    private boolean popupExplicit;
    private LanguageSupport.Signature signature;

    private final FindBar findBar = new FindBar(this);
    private final List<int[]> matches = new ArrayList<>();
    private int currentMatch = -1;

    private List<int[]> occurrences = new ArrayList<>();
    private int occurrenceVersion = -1, occurrenceCaret = -1;
    private boolean draggingSelection, draggingMinimap;
    private int dragMode; // 1 = char, 2 = word, 3 = line
    private int dragOriginStart, dragOriginEnd;

    // snippet session
    private List<int[]> snippetStops;
    private int snippetIndex;

    public CodeEditor(String text, LanguageSupport lang) {
        this.doc = new TextDocument(text);
        this.lang = lang;
        this.focusable = true;
        add(findBar);
        reanalyze();
    }

    // ===================================================================== API

    public String text() {
        return doc.text();
    }

    public TextDocument document() {
        return doc;
    }

    public void setText(String text) {
        doc = new TextDocument(text);
        caret = anchor = 0;
        scrollY = targetScrollY = scrollX = 0;
        reanalyze();
    }

    /** Replaces all text keeping undo history (e.g. after formatting). */
    public void replaceAllText(String text) {
        int c = caret;
        doc.setText(text, c);
        caret = anchor = Math.min(c, doc.length());
        changed();
    }

    public boolean isDirty() {
        return doc.isDirty();
    }

    public void markSaved() {
        doc.markSaved();
    }

    public LanguageSupport.Analysis analysis() {
        ensureAnalysis();
        return analysis;
    }

    public List<LanguageSupport.Problem> problems() {
        ensureAnalysis();
        return analysis == null ? List.of() : analysis.problems();
    }

    public int caret() {
        return caret;
    }

    public int caretLine() {
        return doc.lineOf(caret);
    }

    public int caretCol() {
        return doc.colOf(caret);
    }

    public int selectionLength() {
        return Math.abs(caret - anchor);
    }

    public String selectedText() {
        return doc.substring(Math.min(caret, anchor), Math.max(caret, anchor));
    }

    public void gotoOffset(int offset, int selectLength) {
        offset = clamp(offset);
        anchor = offset;
        caret = clamp(offset + selectLength);
        preferredX = -1;
        revealCaret(true);
        requestFocus();
        caretMoved();
    }

    public void gotoLine(int line) {
        gotoOffset(doc.lineStart(Math.max(0, Math.min(doc.lineCount() - 1, line))), 0);
    }

    public String lineText(int line) {
        return doc.line(line);
    }

    /** Replaces a whole line (undoable). */
    public void replaceLine(int line, String text) {
        if (readOnly) return;
        anchor = doc.lineStart(line);
        caret = doc.lineEnd(line);
        replaceSelection(text, TextDocument.KIND_OTHER);
    }

    public void insertAtCaret(String s) {
        if (readOnly) return;
        if (s.contains("${")) insertSnippet(Math.min(caret, anchor), Math.max(caret, anchor), s);
        else replaceSelection(s, TextDocument.KIND_OTHER);
        requestFocus();
    }

    /**
     * Inserts a whole statement as its own line: into the caret line when it is blank, otherwise
     * below it, keeping the current indentation.
     */
    public void insertLine(String statement) {
        if (readOnly) return;
        int ln = doc.lineOf(caret);
        String cur = doc.line(ln);
        int ws = 0;
        while (ws < cur.length() && (cur.charAt(ws) == ' ' || cur.charAt(ws) == '\t')) ws++;
        String indent = cur.substring(0, ws);
        if (cur.endsWith("{")) indent += "    ";
        if (cur.isBlank()) {
            gotoOffset(doc.lineStart(ln), cur.length());
            replaceSelection(indent + statement, TextDocument.KIND_OTHER);
        } else {
            gotoOffset(doc.lineEnd(ln), 0);
            replaceSelection("\n" + indent + statement, TextDocument.KIND_OTHER);
        }
        doc.breakUndoGroup();
        requestFocus();
    }

    public boolean canUndo() {
        return doc.canUndo();
    }

    public boolean canRedo() {
        return doc.canRedo();
    }

    public void undo() {
        int[] sel = doc.undo();
        if (sel != null) {
            caret = clamp(sel[0]);
            anchor = clamp(sel[1]);
            afterExternalEdit();
        }
    }

    public void redo() {
        int[] sel = doc.redo();
        if (sel != null) {
            caret = clamp(sel[0]);
            anchor = clamp(sel[1]);
            afterExternalEdit();
        }
    }

    private void afterExternalEdit() {
        snippetStops = null;
        closePopup();
        reanalyze();
        revealCaret(true);
        if (onChange != null) onChange.accept(this);
        caretMoved();
    }

    public void openFind(boolean replace) {
        findBar.show(replace, selectionLength() > 0 ? selectedText() : findBar.find.text());
    }

    public void promptGotoLine() {
        Dialog.prompt("Перейти к строке", "Номер строки (1–" + doc.lineCount() + "):", String.valueOf(caretLine() + 1), v -> {
            try {
                int n = Integer.parseInt(v);
                return n >= 1 && n <= doc.lineCount() ? null : "Нет такой строки";
            } catch (NumberFormatException e) {
                return "Введите число";
            }
        }, v -> gotoLine(Integer.parseInt(v) - 1));
    }

    public void format() {
        if (readOnly) return;
        String formatted = lang.format(doc.text());
        if (formatted != null && !formatted.equals(doc.text())) {
            int line = caretLine(), col = caretCol();
            doc.setText(formatted, caret);
            caret = anchor = doc.offsetOf(line, col);
            changed();
        }
    }

    // ================================================================ analysis

    private void reanalyze() {
        analysis = lang.analyze(doc.text());
        analyzedVersion = doc.version();
    }

    private void ensureAnalysis() {
        if (analyzedVersion != doc.version()) reanalyze();
    }

    private void changed() {
        reanalyze();
        blinkStart = System.currentTimeMillis();
        updateSnippetAfterEdit();
        if (findBar.visible) updateMatches(false);
        if (onChange != null) onChange.accept(this);
        caretMoved();
    }

    private void caretMoved() {
        blinkStart = System.currentTimeMillis();
        ensureAnalysis();
        signature = hasFocusWithin() || isFocused() ? lang.signature(doc.text(), caret, analysis) : null;
        if (snippetStops != null && !insideSnippet()) snippetStops = null;
        if (onCaretMove != null) onCaretMove.accept(this);
    }

    // ================================================================ geometry

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int gutterW() {
        int digits = Math.max(2, String.valueOf(doc.lineCount()).length());
        return digits * 6 + 22;
    }

    private boolean minimapShown() {
        return showMinimap && w > 360;
    }

    private int textLeft() {
        return x + gutterW() + 6;
    }

    private int textAreaW() {
        return w - gutterW() - 6 - (minimapShown() ? MINIMAP_W : 0) - 6;
    }

    private int visibleLines() {
        return Math.max(1, (h - PAD_TOP) / LINE_H);
    }

    private int maxScrollY() {
        return Math.max(0, doc.lineCount() * LINE_H - h + PAD_TOP + LINE_H * 3);
    }

    private int lineY(int line) {
        return y + PAD_TOP + line * LINE_H - (int) Math.round(scrollY);
    }

    private int colX(int line, int col) {
        String l = doc.line(line);
        col = Math.min(col, l.length());
        return textLeft() - (int) Math.round(scrollX) + font().width(l.substring(0, col));
    }

    private int offsetAt(double mx, double my) {
        int line = (int) Math.floor((my - y - PAD_TOP + scrollY) / LINE_H);
        line = Math.max(0, Math.min(doc.lineCount() - 1, line));
        String l = doc.line(line);
        double rel = mx - textLeft() + scrollX;
        int acc = 0;
        for (int i = 0; i < l.length(); i++) {
            int cw = font().width(String.valueOf(l.charAt(i)));
            if (rel < acc + cw / 2.0) return doc.lineStart(line) + i;
            acc += cw;
        }
        return doc.lineEnd(line);
    }

    private int clamp(int o) {
        return Math.max(0, Math.min(doc.length(), o));
    }

    public void revealCaret(boolean center) {
        int line = doc.lineOf(caret);
        int top = line * LINE_H;
        if (center && (top < targetScrollY || top + LINE_H > targetScrollY + h - PAD_TOP)) {
            targetScrollY = top - h / 3.0;
        } else if (top < targetScrollY) {
            targetScrollY = top;
        } else if (top + LINE_H * 2 > targetScrollY + h - PAD_TOP) {
            targetScrollY = top + LINE_H * 2 - h + PAD_TOP;
        }
        targetScrollY = Math.max(0, Math.min(maxScrollY(), targetScrollY));
        int cx = font().width(doc.line(line).substring(0, doc.colOf(caret)));
        int area = textAreaW();
        if (cx - scrollX > area - 10) scrollX = cx - area + 30;
        if (cx - scrollX < 0) scrollX = Math.max(0, cx - 30);
    }

    @Override
    public int cursor(int mx, int my) {
        if (minimapShown() && mx >= x + w - MINIMAP_W) return UI.CURSOR_HAND;
        for (Object[] hit : chipHits) {
            int hx = (int) hit[0], hy = (int) hit[1], hw = (int) hit[2], hh = (int) hit[3];
            if (mx >= hx && mx < hx + hw && my >= hy && my < hy + hh) return UI.CURSOR_HAND;
        }
        if (mx < textLeft() - 4) return UI.CURSOR_ARROW;
        return UI.CURSOR_IBEAM;
    }

    @Override
    public void layout() {
        int bh = findBar.barHeight();
        findBar.bounds(x + w - FindBar.W - 12 - (minimapShown() ? MINIMAP_W : 0), y + 2, FindBar.W, bh);
        findBar.layout();
    }

    // ================================================================== render

    @Override
    public void render(Canvas c, int mx, int my, float dt) {
        if (Math.abs(targetScrollY - scrollY) > 0.5) scrollY += (targetScrollY - scrollY) * Math.min(1, dt * 20);
        else scrollY = targetScrollY;
        super.render(c, mx, my, dt);
        UI ui = UI.get();
        if (popupOpen && ui != null) {
            if (!hasFocusWithin() && !isFocused()) closePopup();
            else {
                int line = doc.lineOf(caret);
                popup.anchor(colX(line, doc.colOf(caret)), lineY(line), LINE_H);
            }
        }
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        ensureAnalysis();
        chipHits.clear();
        c.fill(x, y, w, h, Theme.CODE_BG);
        int gw = gutterW();
        c.fill(x, y, gw, h, Theme.CODE_GUTTER);
        String text = doc.text();
        int[] colors = analysis != null ? analysis.colors() : new int[0];
        int first = Math.max(0, (int) (scrollY / LINE_H));
        int last = Math.min(doc.lineCount() - 1, first + visibleLines() + 1);
        int selFrom = Math.min(caret, anchor), selTo = Math.max(caret, anchor);
        int caretLine = doc.lineOf(caret);
        boolean focused = isFocused();
        updateOccurrences();
        int[] bracket = matchingBracket();
        List<LanguageSupport.Problem> problems = analysis != null ? analysis.problems() : List.of();

        // gutter
        c.pushClip(x, y, gw, h);
        for (int line = first; line <= last; line++) {
            int ly = lineY(line);
            String num = String.valueOf(line + 1);
            c.text(num, x + gw - 8 - c.width(num), ly + 2, line == caretLine ? Theme.CODE_GUTTER_ACTIVE : Theme.CODE_GUTTER_TEXT);
            LanguageSupport.Severity worst = null;
            int ls = doc.lineStart(line), le = doc.lineEnd(line);
            for (LanguageSupport.Problem p : problems) {
                if (p.start() <= le && Math.max(p.end(), p.start() + 1) > ls) {
                    if (worst == null || p.severity().ordinal() < worst.ordinal()) worst = p.severity();
                }
            }
            if (worst != null) {
                int col = worst == LanguageSupport.Severity.ERROR ? Theme.ERROR : worst == LanguageSupport.Severity.WARNING ? Theme.WARNING : Theme.INFO;
                c.round(x + 4, ly + 3, 5, 5, 2, col);
            }
        }
        c.popClip();
        c.fill(x + gw - 1, y, 1, h, Theme.BORDER);

        int areaRight = x + w - (minimapShown() ? MINIMAP_W : 0);
        c.pushClip(x + gw, y, areaRight - x - gw, h);
        int left = textLeft() - (int) Math.round(scrollX);
        for (int line = first; line <= last; line++) {
            int ly = lineY(line);
            int ls = doc.lineStart(line), le = doc.lineEnd(line);
            String l = doc.line(line);
            if (line == caretLine && selFrom == selTo) c.fill(x + gw, ly, areaRight - x - gw, LINE_H, Theme.CODE_LINE);

            // indent guides
            int indent = 0;
            while (indent < l.length() && l.charAt(indent) == ' ') indent++;
            if (l.trim().isEmpty()) indent = guessIndent(line);
            for (int g = 4; g <= indent; g += 4) {
                int gx = left + font().width(" ".repeat(g - 4));
                c.fill(gx, ly, 1, LINE_H, 0x18FFFFFF);
            }

            // find matches & occurrences
            for (int[] m : matches) paintRange(c, line, ls, le, m[0], m[1], m == currentMatchRange() ? 0x66FFCB6B : Theme.CODE_MATCH);
            for (int[] o : occurrences) paintRange(c, line, ls, le, o[0], o[1], Theme.CODE_OCCURRENCE);

            // selection
            if (selFrom != selTo && selFrom <= le && selTo >= ls) {
                int a = Math.max(selFrom, ls) - ls, b = Math.min(selTo, le) - ls;
                int sx = colX(line, a), ex = colX(line, b);
                if (selTo > le) ex += 4;
                c.fill(sx, ly, Math.max(1, ex - sx), LINE_H, focused ? Theme.SELECTION : Theme.SELECTION_INACTIVE);
            }

            // brackets
            if (bracket != null) {
                for (int bo : bracket) {
                    if (bo >= ls && bo < le) {
                        int bx = colX(line, bo - ls);
                        int bw = font().width(String.valueOf(doc.charAt(bo)));
                        c.fill(bx, ly, bw + 1, LINE_H, Theme.CODE_BRACKET);
                    }
                }
            }

            // text runs
            int runStart = 0;
            int xx = left;
            for (int i = 1; i <= l.length(); i++) {
                int ci = ls + i - 1;
                int prevColor = ci < colors.length ? colors[ci] : Theme.CODE_IDENT;
                int nextColor = i < l.length() && ls + i < colors.length ? colors[ls + i] : Theme.CODE_IDENT;
                if (i == l.length() || nextColor != prevColor) {
                    String run = l.substring(runStart, i);
                    if (xx < areaRight && xx + 2000 > x) c.text(run, xx, ly + 2, prevColor);
                    xx += font().width(run);
                    runStart = i;
                }
            }

            // the little buttons after the text of the line
            if (chipProvider != null) {
                int cx = xx + 10;
                for (Chip ch : chips(text, line)) {
                    boolean sw = ch.swatch() != 0;
                    int cw = (ch.label().isEmpty() ? 0 : c.width(ch.label()) + 8) + (sw ? 12 : 0) + (ch.label().isEmpty() ? 2 : 0);
                    if (cx + cw > areaRight) break;
                    boolean hov = mx >= cx && mx < cx + cw && my >= ly + 1 && my < ly + LINE_H - 1;
                    c.round(cx, ly + 1, cw, LINE_H - 2, 3, hov ? 0x664F6BFF : 0x33262B3A);
                    int tx = cx + 4;
                    if (sw) {
                        c.round(cx + 3, ly + 3, 8, LINE_H - 6, 2, 0xFFFFFFFF);
                        c.round(cx + 4, ly + 4, 6, LINE_H - 8, 1, ch.swatch());
                        tx = cx + 14;
                    }
                    if (!ch.label().isEmpty()) c.text(ch.label(), tx, ly + 2, hov ? 0xFFFFFFFF : ch.color());
                    chipHits.add(new Object[]{cx, ly + 1, cw, LINE_H - 2, ch});
                    cx += cw + 4;
                }
            }

            // diagnostics
            for (LanguageSupport.Problem p : problems) {
                int ps = p.start(), pe = Math.max(p.end(), p.start() + 1);
                if (ps > le || pe <= ls) continue;
                int a = Math.max(ps, ls) - ls, b = Math.min(pe, le) - ls;
                int sx = colX(line, a);
                int ex = b > a ? colX(line, b) : sx + 6;
                int col = p.severity() == LanguageSupport.Severity.ERROR ? Theme.CODE_ERROR
                        : p.severity() == LanguageSupport.Severity.WARNING ? Theme.WARNING : Theme.INFO;
                c.squiggle(sx, ly + LINE_H - 2, Math.max(4, ex - sx), col);
            }
        }

        // caret
        if (focused && !readOnly && ((System.currentTimeMillis() - blinkStart) / 530) % 2 == 0) {
            int cx = colX(caretLine, doc.colOf(caret));
            c.fill(cx, lineY(caretLine) + 1, 1, LINE_H - 1, Theme.CODE_CARET);
        }
        // snippet placeholders
        if (snippetStops != null) {
            for (int i = 0; i < snippetStops.size(); i++) {
                int[] s = snippetStops.get(i);
                int line = doc.lineOf(s[0]);
                int sx = colX(line, s[0] - doc.lineStart(line)), ex = colX(line, s[1] - doc.lineStart(line));
                c.outline(sx - 1, lineY(line), Math.max(3, ex - sx + 2), LINE_H, i == snippetIndex ? 0xAA8B6CFF : 0x448B6CFF);
            }
        }
        c.popClip();

        if (minimapShown()) paintMinimap(c, colors, problems);
        if (doc.length() == 0 && !readOnly) {
            c.text("Начните писать сюжет… (Ctrl+Space — подсказки)", textLeft(), y + PAD_TOP + 2, Theme.TEXT_MUTED);
        }
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        // signature help
        if (signature != null && (isFocused()) && !signature.parts().isEmpty()) {
            int line = doc.lineOf(caret);
            int ly = lineY(line);
            StringBuilder sb = new StringBuilder();
            int activeStart = 0, activeEnd = 0;
            for (int i = 0; i < signature.parts().size(); i++) {
                if (i > 0) sb.append(' ');
                if (i == signature.active()) activeStart = c.width(sb.toString());
                sb.append(signature.parts().get(i));
                if (i == signature.active()) activeEnd = c.width(sb.toString());
            }
            String sig = sb.toString();
            String doc = signature.doc();
            int sw = Math.max(c.width(sig), doc != null ? Math.min(300, c.width(doc)) : 0) + 12;
            int docH = doc != null && !doc.isEmpty() ? c.wrappedHeight(doc, sw - 12, 1) + 3 : 0;
            int sh = 14 + docH;
            int sx = Math.max(x + gutterW() + 2, Math.min(colX(line, 0), x + w - sw - 4));
            int sy = ly - sh - 3;
            if (sy < y) sy = ly + LINE_H + 3;
            if (popupOpen) sy = ly - sh - 3 < y ? sy : ly - sh - 3;
            c.shadow(sx, sy, sw, sh, 3, 4);
            c.roundBorder(sx, sy, sw, sh, 3, Theme.RAISED, Theme.BORDER_STRONG);
            c.text(sig, sx + 6, sy + 3, Theme.TEXT_DIM);
            if (activeEnd > activeStart) {
                String active = signature.parts().get(signature.active());
                c.text(active, sx + 6 + activeStart, sy + 3, Theme.ACCENT_2);
                c.fill(sx + 6 + activeStart, sy + 12, activeEnd - activeStart, 1, Theme.ACCENT_2);
            }
            if (docH > 0) c.wrapped(doc, sx + 6, sy + 16, sw - 12, Theme.TEXT_MUTED, 1);
        }
        // horizontal scroll indicator / vertical scrollbar
        int total = doc.lineCount() * LINE_H + LINE_H * 3;
        if (total > h && !minimapShown()) {
            int barH = Math.max(16, h * h / total);
            int barY = y + (int) ((h - barH) * (scrollY / Math.max(1, maxScrollY())));
            c.round(x + w - 5, barY, 4, barH, 2, 0x44FFFFFF);
        }
    }

    private int[] currentMatchRange() {
        return currentMatch >= 0 && currentMatch < matches.size() ? matches.get(currentMatch) : null;
    }

    private void paintRange(Canvas c, int line, int ls, int le, int from, int to, int color) {
        if (to <= ls || from > le) return;
        int a = Math.max(from, ls) - ls, b = Math.min(to, le) - ls;
        int sx = colX(line, a), ex = colX(line, b);
        c.fill(sx, lineY(line), Math.max(1, ex - sx), LINE_H, color);
    }

    private int guessIndent(int line) {
        for (int l = line - 1; l >= 0 && l >= line - 50; l--) {
            String s = doc.line(l);
            if (s.trim().isEmpty()) continue;
            int i = 0;
            while (i < s.length() && s.charAt(i) == ' ') i++;
            return s.trim().endsWith("{") ? i + 4 : i;
        }
        return 0;
    }

    private void paintMinimap(Canvas c, int[] colors, List<LanguageSupport.Problem> problems) {
        int mx0 = x + w - MINIMAP_W;
        c.fill(mx0, y, MINIMAP_W, h, Theme.BG_DEEP);
        c.fill(mx0, y, 1, h, Theme.BORDER);
        int lines = doc.lineCount();
        float lineScale = Math.min(2f, (float) (h - 4) / Math.max(1, lines));
        int px = mx0 + 4;
        for (int line = 0; line < lines; line++) {
            int ly = y + 2 + (int) (line * lineScale);
            String l = doc.line(line);
            int ls = doc.lineStart(line);
            int i = 0;
            while (i < l.length()) {
                if (l.charAt(i) == ' ') {
                    i++;
                    continue;
                }
                int j = i;
                int col = ls + i < colors.length ? colors[ls + i] : Theme.CODE_IDENT;
                while (j < l.length() && l.charAt(j) != ' ' && (ls + j >= colors.length || colors[ls + j] == col)) j++;
                int bx = px + i / 2, bw = Math.max(1, (j - i) / 2);
                if (bx < x + w - 2) c.fill(bx, ly, Math.min(bw, x + w - 2 - bx), Math.max(1, (int) lineScale), Theme.alpha(col, 0.55f));
                i = j;
            }
        }
        for (LanguageSupport.Problem p : problems) {
            int line = doc.lineOf(Math.min(p.start(), doc.length()));
            int ly = y + 2 + (int) (line * lineScale);
            c.fill(x + w - 4, ly, 3, 2, p.severity() == LanguageSupport.Severity.ERROR ? Theme.ERROR : Theme.WARNING);
        }
        int vy = y + 2 + (int) (scrollY / LINE_H * lineScale);
        int vh = Math.max(4, (int) (visibleLines() * lineScale));
        c.fill(mx0 + 1, vy, MINIMAP_W - 1, vh, draggingMinimap ? 0x22FFFFFF : 0x14FFFFFF);
    }

    private void minimapScroll(double my) {
        int lines = doc.lineCount();
        float lineScale = Math.min(2f, (float) (h - 4) / Math.max(1, lines));
        double line = (my - y - 2) / lineScale - visibleLines() / 2.0;
        targetScrollY = scrollY = Math.max(0, Math.min(maxScrollY(), line * LINE_H));
    }

    // ============================================================ occurrences

    private void updateOccurrences() {
        if (occurrenceVersion == doc.version() && occurrenceCaret == caret) return;
        occurrenceVersion = doc.version();
        occurrenceCaret = caret;
        occurrences = new ArrayList<>();
        if (caret != anchor) return;
        int[] w = wordAt(caret);
        if (w == null || w[1] - w[0] < 2) return;
        String word = doc.substring(w[0], w[1]);
        String text = doc.text();
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) >= 0) {
            int end = idx + word.length();
            boolean leftOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
            boolean rightOk = end >= text.length() || !isWordChar(text.charAt(end));
            if (leftOk && rightOk) occurrences.add(new int[]{idx, end});
            idx = end;
            if (occurrences.size() > 500) break;
        }
        if (occurrences.size() < 2) occurrences.clear();
    }

    private int[] matchingBracket() {
        String open = "({[", close = ")}]";
        int[] cand = {caret - 1, caret};
        for (int pos : cand) {
            if (pos < 0 || pos >= doc.length()) continue;
            char ch = doc.charAt(pos);
            int oi = open.indexOf(ch), ci = close.indexOf(ch);
            if (oi >= 0) {
                int depth = 0;
                for (int i = pos; i < doc.length() && i < pos + 20000; i++) {
                    char d = doc.charAt(i);
                    if (d == ch) depth++;
                    else if (d == close.charAt(oi) && --depth == 0) return new int[]{pos, i};
                }
            } else if (ci >= 0) {
                int depth = 0;
                for (int i = pos; i >= 0 && i > pos - 20000; i--) {
                    char d = doc.charAt(i);
                    if (d == ch) depth++;
                    else if (d == open.charAt(ci) && --depth == 0) return new int[]{i, pos};
                }
            }
        }
        return null;
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private int[] wordAt(int offset) {
        int s = offset, e = offset;
        while (s > 0 && isWordChar(doc.charAt(s - 1))) s--;
        while (e < doc.length() && isWordChar(doc.charAt(e))) e++;
        return s == e ? null : new int[]{s, e};
    }

    // ================================================================== tooltip

    @Override
    public String tooltipText(int mx, int my) {
        if (mx < textLeft() || (minimapShown() && mx >= x + w - MINIMAP_W)) return null;
        int line = (int) Math.floor((my - y - PAD_TOP + scrollY) / LINE_H);
        if (line < 0 || line >= doc.lineCount()) return null;
        int off = offsetAt(mx, my);
        if (off >= doc.lineEnd(line) && mx > colX(line, doc.line(line).length()) + 4) return null;
        StringBuilder sb = new StringBuilder();
        for (LanguageSupport.Problem p : problems()) {
            int pe = Math.max(p.end(), p.start() + 1);
            if (off >= p.start() && off <= pe) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(p.severity() == LanguageSupport.Severity.ERROR ? "Ошибка: " : "Внимание: ").append(p.message());
            }
        }
        String hover = lang.hover(doc.text(), off, analysis);
        if (hover != null && !hover.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(hover);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // =================================================================== mouse

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (minimapShown() && mx >= x + w - MINIMAP_W) {
            draggingMinimap = true;
            minimapScroll(my);
            return true;
        }
        if (button == 1) {
            int off = offsetAt(mx, my);
            if (off < Math.min(caret, anchor) || off > Math.max(caret, anchor)) caret = anchor = off;
            openContextMenu((int) mx, (int) my);
            return true;
        }
        if (button != 0) return false;
        for (Object[] hit : chipHits) {
            int hx = (int) hit[0], hy = (int) hit[1], hw = (int) hit[2], hh = (int) hit[3];
            if (mx >= hx && mx < hx + hw && my >= hy && my < hy + hh) {
                closePopup();
                menuX = hx;
                menuY = hy + hh + 2;
                ((Chip) hit[4]).action().accept(hx, hy + hh + 2);
                return true;
            }
        }
        closePopup();
        doc.breakUndoGroup();
        int off = offsetAt(mx, my);
        if (UI.ctrl(modsNow())) {
            int def = lang.definition(doc.text(), off, analysis);
            if (def >= 0) {
                gotoOffset(def, 0);
                return true;
            }
        }
        int clicks = UI.get() != null ? UI.get().clickCount() : 1;
        if (clicks >= 3) {
            int line = doc.lineOf(off);
            anchor = doc.lineStart(line);
            caret = line + 1 < doc.lineCount() ? doc.lineStart(line + 1) : doc.lineEnd(line);
            dragMode = 3;
        } else if (clicks == 2) {
            int[] w = wordAt(off);
            if (w != null) {
                anchor = w[0];
                caret = w[1];
            }
            dragMode = 2;
        } else {
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) caret = off;
            else caret = anchor = off;
            dragMode = 1;
        }
        dragOriginStart = Math.min(caret, anchor);
        dragOriginEnd = Math.max(caret, anchor);
        draggingSelection = true;
        preferredX = -1;
        caretMoved();
        return true;
    }

    private static int modsNow() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        int mods = 0;
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_CONTROL;
        return mods;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingMinimap) {
            minimapScroll(my);
            return true;
        }
        if (!draggingSelection) return false;
        int off = offsetAt(mx, my);
        if (dragMode == 1) {
            caret = off;
        } else if (dragMode == 2) {
            int[] w = wordAt(off);
            int ws = w != null ? w[0] : off, we = w != null ? w[1] : off;
            if (ws < dragOriginStart) {
                anchor = dragOriginEnd;
                caret = ws;
            } else {
                anchor = dragOriginStart;
                caret = Math.max(we, dragOriginEnd);
            }
        } else {
            int line = doc.lineOf(off);
            int ls = doc.lineStart(line), le = line + 1 < doc.lineCount() ? doc.lineStart(line + 1) : doc.lineEnd(line);
            if (ls < dragOriginStart) {
                anchor = dragOriginEnd;
                caret = ls;
            } else {
                anchor = dragOriginStart;
                caret = le;
            }
        }
        if (my < y + 6) targetScrollY = scrollY = Math.max(0, scrollY - LINE_H);
        if (my > y + h - 6) targetScrollY = scrollY = Math.min(maxScrollY(), scrollY + LINE_H);
        caretMoved();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSelection = false;
        draggingMinimap = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            scrollX = Math.max(0, scrollX - delta * 40);
        } else {
            targetScrollY = Math.max(0, Math.min(maxScrollY(), targetScrollY - delta * LINE_H * 3));
        }
        return true;
    }

    /** Where the last context menu opened (for pickers it starts). */
    public int menuX, menuY;

    /** A little button at the end of a line: a colour swatch and/or a word; clicking runs the action at (x, y below it). */
    public record Chip(String label, int swatch, int color, java.util.function.BiConsumer<Integer, Integer> action) {
    }

    /** The buttons of a line (text, line number) — set by the language's tab; null — none. */
    public java.util.function.BiFunction<String, Integer, java.util.List<Chip>> chipProvider;
    private final java.util.Map<Integer, java.util.List<Chip>> chipCache = new java.util.HashMap<>();
    private int chipCacheFor;
    private final java.util.List<Object[]> chipHits = new java.util.ArrayList<>();

    private java.util.List<Chip> chips(String text, int line) {
        int key = text.hashCode() * 31 + text.length();
        if (key != chipCacheFor) {
            chipCache.clear();
            chipCacheFor = key;
        }
        return chipCache.computeIfAbsent(line, l -> {
            try {
                java.util.List<Chip> r = chipProvider.apply(text, l);
                return r != null ? r : java.util.List.of();
            } catch (RuntimeException e) {
                return java.util.List.of();
            }
        });
    }

    /** Replaces a range of the text (one undo step). */
    public void replaceText(int from, int to, String s) {
        if (readOnly) return;
        anchor = clamp(from);
        caret = clamp(to);
        replaceSelection(s, TextDocument.KIND_OTHER);
    }

    private void openContextMenu(int mx, int my) {
        menuX = mx;
        menuY = my;
        org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu pm = new org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu();
        pm.item("Вырезать", Icon.EDIT, "Ctrl+X", this::cut).enabled(() -> !readOnly);
        pm.item("Копировать", Icon.COPY, "Ctrl+C", this::copy);
        pm.item("Вставить", Icon.FILE, "Ctrl+V", this::paste).enabled(() -> !readOnly);
        pm.separator();
        pm.item("Перейти к объявлению", Icon.ARROW_RIGHT, "F12", () -> {
            int def = lang.definition(doc.text(), caret, analysis);
            if (def >= 0) gotoOffset(def, 0);
        });
        pm.item("Найти", Icon.SEARCH, "Ctrl+F", () -> openFind(false));
        pm.item("Заменить", Icon.SEARCH, "Ctrl+H", () -> openFind(true)).enabled(() -> !readOnly);
        pm.separator();
        pm.item("Закомментировать", Icon.CODE, "Ctrl+/", this::toggleComment).enabled(() -> !readOnly);
        pm.item("Форматировать", Icon.LIST, "Ctrl+Shift+F", this::format).enabled(() -> !readOnly);
        if (positionProvider != null) {
            pm.item("Вставить мою позицию", Icon.PIN, "Alt+P", this::insertPosition).enabled(() -> !readOnly);
        }
        if (contextExtras != null) contextExtras.accept(pm);
        pm.show(mx, my);
    }

    // ================================================================ editing

    private void replaceRange(int from, int to, String s, int newCaret, int kind) {
        int cb = caret, ab = anchor;
        doc.replace(from, to, s, cb, ab, newCaret, newCaret, kind);
        caret = anchor = clamp(newCaret);
        adjustSnippet(from, to - from, TextDocument.normalize(s).length());
        changed();
        revealCaret(false);
    }

    private void replaceSelection(String s, int kind) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        String norm = TextDocument.normalize(s);
        replaceRange(from, to, norm, from + norm.length(), kind);
    }

    public void copy() {
        if (caret != anchor) UI.setClipboard(selectedText());
        else {
            int line = doc.lineOf(caret);
            UI.setClipboard(doc.line(line) + "\n");
        }
    }

    public void cut() {
        if (readOnly) return;
        copy();
        if (caret != anchor) replaceSelection("", TextDocument.KIND_OTHER);
        else {
            int line = doc.lineOf(caret);
            int ls = doc.lineStart(line);
            int le = line + 1 < doc.lineCount() ? doc.lineStart(line + 1) : doc.lineEnd(line);
            replaceRange(ls, le, "", ls, TextDocument.KIND_OTHER);
        }
    }

    public void paste() {
        if (readOnly) return;
        String clip = UI.clipboard();
        if (clip == null || clip.isEmpty()) return;
        doc.breakUndoGroup();
        replaceSelection(clip, TextDocument.KIND_OTHER);
        doc.breakUndoGroup();
    }

    public void selectAll() {
        anchor = 0;
        caret = doc.length();
        caretMoved();
    }

    private String indentOf(int line) {
        String l = doc.line(line);
        int i = 0;
        while (i < l.length() && l.charAt(i) == ' ') i++;
        return l.substring(0, i);
    }

    private void newline() {
        int line = doc.lineOf(caret);
        String indent = indentOf(line);
        int from = Math.min(caret, anchor);
        char before = from > 0 ? doc.charAt(from - 1) : 0;
        char after = Math.max(caret, anchor) < doc.length() ? doc.charAt(Math.max(caret, anchor)) : 0;
        // keep the cursor's indent if it is inside leading whitespace
        if (doc.colOf(from) < indent.length()) indent = indent.substring(0, doc.colOf(from));
        if (before == '{' && after == '}') {
            String ins = "\n" + indent + "    \n" + indent;
            replaceSelection(ins, TextDocument.KIND_OTHER);
            caret = anchor = from + 1 + indent.length() + 4;
            caretMoved();
        } else if (before == '{') {
            replaceSelection("\n" + indent + "    ", TextDocument.KIND_OTHER);
        } else {
            replaceSelection("\n" + indent, TextDocument.KIND_OTHER);
        }
        doc.breakUndoGroup();
    }

    private static final String OPEN = "([{\"", CLOSE = ")]}\"";

    private void typeChar(char ch) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        char next = to < doc.length() ? doc.charAt(to) : 0;
        char prev = from > 0 ? doc.charAt(from - 1) : 0;
        boolean inString = inStringAt(from);
        // skip over an auto-inserted closer
        if (from == to && CLOSE.indexOf(ch) >= 0 && next == ch && (ch != '"' || inString)) {
            caret = anchor = from + 1;
            caretMoved();
            return;
        }
        int oi = OPEN.indexOf(ch);
        if (oi >= 0 && !(ch == '"' && (inString || isWordChar(prev)))) {
            char closer = CLOSE.charAt(oi);
            if (from != to) {
                String sel = doc.substring(from, to);
                replaceRange(from, to, ch + sel + closer, from + 1 + sel.length() + 1, TextDocument.KIND_OTHER);
                anchor = from + 1;
                caret = from + 1 + sel.length();
                caretMoved();
                return;
            }
            boolean nextFree = next == 0 || Character.isWhitespace(next) || CLOSE.indexOf(next) >= 0;
            if (nextFree && (ch != '"' || !inString)) {
                replaceRange(from, to, "" + ch + closer, from + 1, TextDocument.KIND_TYPE);
                return;
            }
        }
        if (ch == '}' && from == to) {
            int line = doc.lineOf(from);
            String before = doc.substring(doc.lineStart(line), from);
            if (before.trim().isEmpty() && before.length() >= 4) {
                int ls = doc.lineStart(line);
                replaceRange(ls, from, before.substring(4) + "}", ls + before.length() - 4 + 1, TextDocument.KIND_TYPE);
                return;
            }
        }
        replaceSelection(String.valueOf(ch), TextDocument.KIND_TYPE);
    }

    private boolean inStringAt(int offset) {
        int line = doc.lineOf(offset);
        String l = doc.substring(doc.lineStart(line), offset);
        boolean in = false;
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (ch == '\\' && in) {
                i++;
                continue;
            }
            if (ch == '"') in = !in;
            if (!in && ch == '/' && i + 1 < l.length() && l.charAt(i + 1) == '/') return false;
        }
        return in;
    }

    private boolean inCommentAt(int offset) {
        int line = doc.lineOf(offset);
        String l = doc.substring(doc.lineStart(line), offset);
        boolean in = false;
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (ch == '"') in = !in;
            if (!in && ((ch == '/' && i + 1 < l.length() && l.charAt(i + 1) == '/') || ch == '#')) return true;
        }
        return false;
    }

    private void backspace(boolean word) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        if (from != to) {
            replaceRange(from, to, "", from, TextDocument.KIND_DELETE);
            return;
        }
        if (from == 0) return;
        if (word) {
            int s = from;
            while (s > 0 && Character.isWhitespace(doc.charAt(s - 1)) && doc.charAt(s - 1) != '\n') s--;
            if (s == from) {
                if (isWordChar(doc.charAt(s - 1))) while (s > 0 && isWordChar(doc.charAt(s - 1))) s--;
                else s--;
            }
            replaceRange(s, from, "", s, TextDocument.KIND_DELETE);
            return;
        }
        char prev = doc.charAt(from - 1);
        char next = from < doc.length() ? doc.charAt(from) : 0;
        int oi = OPEN.indexOf(prev);
        if (oi >= 0 && next == CLOSE.charAt(oi)) {
            replaceRange(from - 1, from + 1, "", from - 1, TextDocument.KIND_DELETE);
            return;
        }
        int line = doc.lineOf(from);
        int ls = doc.lineStart(line);
        String before = doc.substring(ls, from);
        if (!before.isEmpty() && before.trim().isEmpty()) {
            int remove = before.length() % 4 == 0 ? 4 : before.length() % 4;
            replaceRange(from - remove, from, "", from - remove, TextDocument.KIND_DELETE);
            return;
        }
        replaceRange(from - 1, from, "", from - 1, TextDocument.KIND_DELETE);
    }

    private void deleteForward(boolean word) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        if (from != to) {
            replaceRange(from, to, "", from, TextDocument.KIND_DELETE);
            return;
        }
        if (from >= doc.length()) return;
        int e = from + 1;
        if (word) {
            e = from;
            if (isWordChar(doc.charAt(e))) while (e < doc.length() && isWordChar(doc.charAt(e))) e++;
            else e++;
        }
        replaceRange(from, e, "", from, TextDocument.KIND_DELETE);
    }

    private void indentLines(boolean outdent) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        int l1 = doc.lineOf(from), l2 = doc.lineOf(to);
        if (to > from && doc.colOf(to) == 0 && l2 > l1) l2--;
        if (!outdent && from == to) {
            int col = doc.colOf(caret);
            int spaces = 4 - (col % 4);
            replaceSelection(" ".repeat(spaces), TextDocument.KIND_TYPE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        int start = doc.lineStart(l1), end = doc.lineEnd(l2);
        int firstDelta = 0, totalDelta = 0;
        for (int l = l1; l <= l2; l++) {
            String line = doc.line(l);
            String nl;
            if (outdent) {
                int rm = 0;
                while (rm < 4 && rm < line.length() && line.charAt(rm) == ' ') rm++;
                nl = line.substring(rm);
                if (l == l1) firstDelta = -rm;
                totalDelta -= rm;
            } else {
                nl = line.trim().isEmpty() ? line : "    " + line;
                int add = nl.length() - line.length();
                if (l == l1) firstDelta = add;
                totalDelta += add;
            }
            sb.append(nl);
            if (l < l2) sb.append('\n');
        }
        int newAnchor = Math.max(start, from + firstDelta), newCaret = Math.max(start, to + totalDelta);
        int cb = caret, ab = anchor;
        doc.replace(start, end, sb.toString(), cb, ab, newCaret, newAnchor, TextDocument.KIND_OTHER);
        anchor = clamp(newAnchor);
        caret = clamp(newCaret);
        changed();
    }

    public void toggleComment() {
        if (readOnly) return;
        String marker = lang.lineComment();
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        int l1 = doc.lineOf(from), l2 = doc.lineOf(to);
        if (to > from && doc.colOf(to) == 0 && l2 > l1) l2--;
        boolean allCommented = true;
        int minIndent = Integer.MAX_VALUE;
        for (int l = l1; l <= l2; l++) {
            String s = doc.line(l);
            if (s.trim().isEmpty()) continue;
            if (!s.trim().startsWith(marker)) allCommented = false;
            minIndent = Math.min(minIndent, indentOf(l).length());
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;
        StringBuilder sb = new StringBuilder();
        for (int l = l1; l <= l2; l++) {
            String s = doc.line(l);
            if (s.trim().isEmpty()) sb.append(s);
            else if (allCommented) {
                int i = s.indexOf(marker);
                String rest = s.substring(i + marker.length());
                if (rest.startsWith(" ")) rest = rest.substring(1);
                sb.append(s, 0, i).append(rest);
            } else {
                sb.append(s, 0, minIndent).append(marker).append(' ').append(s.substring(minIndent));
            }
            if (l < l2) sb.append('\n');
        }
        int start = doc.lineStart(l1), end = doc.lineEnd(l2);
        int cb = caret, ab = anchor;
        String repl = sb.toString();
        int delta = repl.length() - (end - start);
        doc.replace(start, end, repl, cb, ab, cb, ab, TextDocument.KIND_OTHER);
        if (from == to) caret = anchor = clamp(caret + (doc.lineOf(Math.max(0, caret)) == l1 ? delta : 0));
        else {
            anchor = clamp(start);
            caret = clamp(end + delta);
        }
        changed();
    }

    private void duplicateLine() {
        int line = doc.lineOf(caret);
        int col = doc.colOf(caret);
        String l = doc.line(line);
        int le = doc.lineEnd(line);
        replaceRange(le, le, "\n" + l, le + 1 + col, TextDocument.KIND_OTHER);
    }

    private void deleteLine() {
        int line = doc.lineOf(caret);
        int ls = doc.lineStart(line);
        int le = line + 1 < doc.lineCount() ? doc.lineStart(line + 1) : doc.lineEnd(line);
        if (line + 1 >= doc.lineCount() && ls > 0) ls--;
        replaceRange(ls, le, "", Math.min(ls, doc.length()), TextDocument.KIND_OTHER);
    }

    private void moveLines(int dir) {
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        int l1 = doc.lineOf(from), l2 = doc.lineOf(to);
        if (dir < 0 && l1 == 0) return;
        if (dir > 0 && l2 >= doc.lineCount() - 1) return;
        int start = doc.lineStart(dir < 0 ? l1 - 1 : l1);
        int end = doc.lineEnd(dir < 0 ? l2 : l2 + 1);
        String block = doc.substring(doc.lineStart(l1), doc.lineEnd(l2));
        String other = dir < 0 ? doc.line(l1 - 1) : doc.line(l2 + 1);
        String repl = dir < 0 ? block + "\n" + other : other + "\n" + block;
        int shift = dir < 0 ? -(other.length() + 1) : other.length() + 1;
        int cb = caret, ab = anchor;
        doc.replace(start, end, repl, cb, ab, cb + shift, ab + shift, TextDocument.KIND_OTHER);
        caret = clamp(cb + shift);
        anchor = clamp(ab + shift);
        changed();
        revealCaret(false);
    }

    private void insertPosition() {
        if (positionProvider == null || readOnly) return;
        String pos = positionProvider.get();
        if (pos != null) replaceSelection(pos, TextDocument.KIND_OTHER);
    }

    // =========================================================== navigation

    private void moveCaret(int newPos, boolean select) {
        caret = clamp(newPos);
        if (!select) anchor = caret;
        revealCaret(false);
        caretMoved();
    }

    private void verticalMove(int lines, boolean select) {
        int line = doc.lineOf(caret);
        if (preferredX < 0) preferredX = font().width(doc.line(line).substring(0, doc.colOf(caret)));
        int target = Math.max(0, Math.min(doc.lineCount() - 1, line + lines));
        if (target == line) {
            moveCaret(lines < 0 ? 0 : doc.length(), select);
            return;
        }
        String l = doc.line(target);
        int acc = 0, col = l.length();
        for (int i = 0; i < l.length(); i++) {
            int cw = font().width(String.valueOf(l.charAt(i)));
            if (preferredX < acc + cw / 2.0) {
                col = i;
                break;
            }
            acc += cw;
        }
        int keep = preferredX;
        moveCaret(doc.lineStart(target) + col, select);
        preferredX = keep;
    }

    private int wordLeft(int i) {
        while (i > 0 && Character.isWhitespace(doc.charAt(i - 1))) i--;
        if (i > 0 && isWordChar(doc.charAt(i - 1))) while (i > 0 && isWordChar(doc.charAt(i - 1))) i--;
        else if (i > 0) i--;
        return i;
    }

    private int wordRight(int i) {
        int n = doc.length();
        while (i < n && Character.isWhitespace(doc.charAt(i))) i++;
        if (i < n && isWordChar(doc.charAt(i))) while (i < n && isWordChar(doc.charAt(i))) i++;
        else if (i < n) i++;
        return i;
    }

    // ================================================================== keys

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = UI.ctrl(mods), shift = UI.shift(mods), alt = UI.alt(mods);

        if (popupOpen) {
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    popup.move(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    popup.move(1);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    popup.move(-8);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    popup.move(8);
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                    LanguageSupport.Completion sel = popup.selected();
                    if (sel != null) {
                        acceptCompletion(sel);
                        return true;
                    }
                    closePopup();
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    closePopup();
                    return true;
                }
                default -> {
                }
            }
        }
        if (snippetStops != null) {
            if (key == GLFW.GLFW_KEY_TAB) {
                nextSnippetStop(shift ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                snippetStops = null;
                return true;
            }
        }

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> selectAll();
                case GLFW.GLFW_KEY_C -> copy();
                case GLFW.GLFW_KEY_X -> cut();
                case GLFW.GLFW_KEY_V -> paste();
                case GLFW.GLFW_KEY_Z -> {
                    if (readOnly) return true;
                    if (shift) redo();
                    else undo();
                }
                case GLFW.GLFW_KEY_Y -> {
                    if (!readOnly) redo();
                }
                case GLFW.GLFW_KEY_D -> {
                    if (!readOnly) duplicateLine();
                }
                case GLFW.GLFW_KEY_K -> {
                    if (shift && !readOnly) deleteLine();
                    else return false;
                }
                case GLFW.GLFW_KEY_F -> {
                    if (shift) format();
                    else openFind(false);
                }
                case GLFW.GLFW_KEY_H -> {
                    if (!readOnly) openFind(true);
                }
                case GLFW.GLFW_KEY_G -> promptGotoLine();
                case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> toggleComment();
                case GLFW.GLFW_KEY_SPACE -> {
                    if (!readOnly) requestCompletion(true);
                }
                case GLFW.GLFW_KEY_S -> {
                    if (onSave == null) return false;
                    onSave.run();
                }
                case GLFW.GLFW_KEY_HOME -> moveCaret(0, shift);
                case GLFW.GLFW_KEY_END -> moveCaret(doc.length(), shift);
                case GLFW.GLFW_KEY_LEFT -> moveCaret(wordLeft(caret), shift);
                case GLFW.GLFW_KEY_RIGHT -> moveCaret(wordRight(caret), shift);
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!readOnly) backspace(true);
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (!readOnly) deleteForward(true);
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (readOnly) return true;
                    int line = doc.lineOf(caret);
                    caret = anchor = doc.lineEnd(line);
                    newline();
                }
                case GLFW.GLFW_KEY_UP -> {
                    targetScrollY = Math.max(0, targetScrollY - LINE_H);
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    targetScrollY = Math.min(maxScrollY(), targetScrollY + LINE_H);
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (alt) {
            switch (key) {
                case GLFW.GLFW_KEY_UP -> {
                    if (!readOnly) moveLines(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (!readOnly) moveLines(1);
                    return true;
                }
                case GLFW.GLFW_KEY_P -> {
                    insertPosition();
                    return true;
                }
                default -> {
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                int from = Math.min(caret, anchor);
                moveCaret(!shift && caret != anchor ? from : caret - 1, shift);
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                int to = Math.max(caret, anchor);
                moveCaret(!shift && caret != anchor ? to : caret + 1, shift);
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_UP -> verticalMove(-1, shift);
            case GLFW.GLFW_KEY_DOWN -> verticalMove(1, shift);
            case GLFW.GLFW_KEY_PAGE_UP -> verticalMove(-visibleLines(), shift);
            case GLFW.GLFW_KEY_PAGE_DOWN -> verticalMove(visibleLines(), shift);
            case GLFW.GLFW_KEY_HOME -> {
                int line = doc.lineOf(caret);
                int ls = doc.lineStart(line);
                int first = ls + indentOf(line).length();
                moveCaret(caret == first ? ls : first, shift);
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCaret(doc.lineEnd(doc.lineOf(caret)), shift);
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!readOnly) newline();
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (!readOnly) indentLines(shift);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (readOnly) return true;
                backspace(false);
                if (popupOpen) {
                    if (caret < popupStart()) closePopup();
                    else requestCompletion(popupExplicit);
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!readOnly) deleteForward(false);
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (findBar.visible) {
                    findBar.hide();
                    return true;
                }
                if (caret != anchor) {
                    anchor = caret;
                    return true;
                }
                if (signature != null) {
                    signature = null;
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_F12 -> {
                int def = lang.definition(doc.text(), caret, analysis);
                if (def >= 0) gotoOffset(def, 0);
            }
            case GLFW.GLFW_KEY_F3 -> findNext(!shift);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (readOnly || ch < 32 || ch == 127) return false;
        if (UI.ctrl(mods) && !UI.alt(mods)) return false;
        typeChar(ch);
        if (inCommentAt(caret)) {
            closePopup();
            return true;
        }
        boolean inStr = inStringAt(caret);
        if (popupOpen) {
            if (isWordChar(ch)) requestCompletion(popupExplicit);
            else if (ch == ' ' || ch == '(' || ch == ',') requestCompletion(false);
            else closePopup();
        } else if (!inStr && (lang.isCompletionTrigger(ch) || ch == ' ')) {
            requestCompletion(false);
        } else if (inStr && ch == ':') {
            requestCompletion(false);
        }
        caretMoved();
        return true;
    }

    // ============================================================ completion

    private int popupStartOffset;

    private int popupStart() {
        return popupStartOffset;
    }

    public void requestCompletion(boolean explicit) {
        ensureAnalysis();
        List<LanguageSupport.Completion> items = lang.complete(doc.text(), caret, analysis, explicit);
        UI ui = UI.get();
        if (items.isEmpty() || ui == null) {
            closePopup();
            return;
        }
        // a single exact match is pointless unless explicitly requested
        if (!explicit && items.size() == 1) {
            LanguageSupport.Completion only = items.get(0);
            String typed = doc.substring(only.replaceStart(), caret);
            if (typed.equals(only.label()) || typed.equals(only.insert())) {
                closePopup();
                return;
            }
        }
        popup.setItems(items);
        popupStartOffset = items.get(0).replaceStart();
        popupExplicit = explicit;
        int line = doc.lineOf(caret);
        popup.anchor(colX(line, doc.colOf(caret)), lineY(line), LINE_H);
        if (!popupOpen) {
            ui.openOverlay(popup, false, true, () -> popupOpen = false);
            popupOpen = true;
        }
    }

    public void closePopup() {
        if (!popupOpen) return;
        popupOpen = false;
        UI ui = UI.get();
        if (ui != null) ui.closeOverlay(popup);
    }

    private void acceptCompletion(LanguageSupport.Completion item) {
        closePopup();
        int start = Math.max(0, Math.min(item.replaceStart(), caret));
        // also swallow the rest of the identifier after the caret
        int end = caret;
        while (end < doc.length() && isWordChar(doc.charAt(end))) end++;
        doc.breakUndoGroup();
        insertSnippet(start, end, item.insert());
        doc.breakUndoGroup();
        requestFocus();
        // continue suggesting arguments after a command keyword
        if (item.insert().endsWith(" ") && snippetStops == null) requestCompletion(false);
    }

    // =============================================================== snippets

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\d+):([^}]*)}|\\$(\\d+)");

    private void insertSnippet(int from, int to, String snippet) {
        StringBuilder out = new StringBuilder();
        List<int[]> stops = new ArrayList<>(); // {index, start, end}
        Matcher m = PLACEHOLDER.matcher(snippet);
        int last = 0;
        while (m.find()) {
            out.append(snippet, last, m.start());
            int idx = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(3));
            String ph = m.group(2) != null ? m.group(2) : "";
            int s = out.length();
            out.append(ph);
            stops.add(new int[]{idx == 0 ? Integer.MAX_VALUE : idx, s, out.length()});
            last = m.end();
        }
        out.append(snippet.substring(last));
        // indent continuation lines like the first one
        String indent = indentOf(doc.lineOf(from));
        String text = out.toString().replace("\n", "\n" + indent);
        if (!indent.isEmpty()) {
            // shift stops for added indentation
            for (int[] s : stops) {
                int added = countNewlines(out.substring(0, s[1])) * indent.length();
                int addedEnd = countNewlines(out.substring(0, s[2])) * indent.length();
                s[1] += added;
                s[2] += addedEnd;
            }
        }
        stops.sort((a, b) -> Integer.compare(a[0], b[0]));
        int cb = caret, ab = anchor;
        doc.replace(from, to, text, cb, ab, from + text.length(), from + text.length(), TextDocument.KIND_OTHER);
        if (stops.isEmpty()) {
            caret = anchor = from + text.length();
            snippetStops = null;
        } else {
            snippetStops = new ArrayList<>();
            for (int[] s : stops) snippetStops.add(new int[]{from + s[1], from + s[2]});
            snippetIndex = 0;
            int[] first = snippetStops.get(0);
            anchor = first[0];
            caret = first[1];
            if (snippetStops.size() == 1 && first[0] == first[1]) snippetStops = null;
        }
        reanalyze();
        if (findBar.visible) updateMatches(false);
        if (onChange != null) onChange.accept(this);
        revealCaret(false);
        caretMoved();
    }

    private static int countNewlines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    private void nextSnippetStop(int dir) {
        if (snippetStops == null) return;
        snippetIndex += dir;
        if (snippetIndex < 0) snippetIndex = 0;
        if (snippetIndex >= snippetStops.size()) {
            int[] last = snippetStops.get(snippetStops.size() - 1);
            caret = anchor = last[1];
            snippetStops = null;
            caretMoved();
            return;
        }
        int[] s = snippetStops.get(snippetIndex);
        anchor = s[0];
        caret = s[1];
        caretMoved();
        if (s[0] == s[1]) requestCompletion(false);
    }

    private boolean insideSnippet() {
        if (snippetStops == null) return false;
        int min = Integer.MAX_VALUE, max = -1;
        for (int[] s : snippetStops) {
            min = Math.min(min, s[0]);
            max = Math.max(max, s[1]);
        }
        return caret >= min && caret <= max && doc.lineOf(caret) >= doc.lineOf(min) && doc.lineOf(caret) <= doc.lineOf(max);
    }

    private void adjustSnippet(int offset, int removed, int inserted) {
        if (snippetStops == null) return;
        int delta = inserted - removed;
        for (int i = 0; i < snippetStops.size(); i++) {
            int[] s = snippetStops.get(i);
            if (offset >= s[0] && offset + removed <= s[1]) {
                s[1] += delta;
            } else if (offset < s[0]) {
                s[0] += delta;
                s[1] += delta;
            }
        }
    }

    private void updateSnippetAfterEdit() {
        if (snippetStops != null && !insideSnippet()) snippetStops = null;
    }

    // =============================================================== find

    void updateMatches(boolean jump) {
        matches.clear();
        currentMatch = -1;
        String q = findBar.find.text();
        if (q.isEmpty()) return;
        String text = doc.text();
        String hay = findBar.matchCase ? text : text.toLowerCase();
        String needle = findBar.matchCase ? q : q.toLowerCase();
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            int end = idx + needle.length();
            boolean ok = !findBar.wholeWord
                    || ((idx == 0 || !isWordChar(text.charAt(idx - 1))) && (end >= text.length() || !isWordChar(text.charAt(end))));
            if (ok) matches.add(new int[]{idx, end});
            idx = Math.max(end, idx + 1);
            if (matches.size() > 5000) break;
        }
        if (matches.isEmpty()) return;
        currentMatch = 0;
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i)[0] >= Math.min(caret, anchor)) {
                currentMatch = i;
                break;
            }
        }
        if (jump) selectMatch();
    }

    void clearMatches() {
        matches.clear();
        currentMatch = -1;
    }

    String matchSummary() {
        if (findBar.find.text().isEmpty()) return "";
        if (matches.isEmpty()) return "нет";
        return (currentMatch + 1) + "/" + matches.size();
    }

    private void selectMatch() {
        int[] m = currentMatchRange();
        if (m == null) return;
        anchor = m[0];
        caret = m[1];
        revealCaret(true);
    }

    public void findNext(boolean forward) {
        if (matches.isEmpty()) {
            updateMatches(false);
            if (matches.isEmpty()) return;
        }
        currentMatch = Math.floorMod(currentMatch + (forward ? 1 : -1), matches.size());
        selectMatch();
    }

    void replaceCurrent() {
        if (readOnly) return;
        int[] m = currentMatchRange();
        if (m == null) return;
        String repl = findBar.replace.text();
        replaceRange(m[0], m[1], repl, m[0] + repl.length(), TextDocument.KIND_OTHER);
        updateMatches(false);
        if (!matches.isEmpty()) {
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i)[0] >= m[0] + repl.length()) {
                    currentMatch = i;
                    break;
                }
            }
            selectMatch();
        }
    }

    void replaceAll() {
        if (readOnly || matches.isEmpty()) return;
        String repl = findBar.replace.text();
        StringBuilder sb = new StringBuilder(doc.text());
        for (int i = matches.size() - 1; i >= 0; i--) {
            int[] m = matches.get(i);
            sb.replace(m[0], m[1], repl);
        }
        int count = matches.size();
        replaceAllText(sb.toString());
        updateMatches(false);
        UI ui = UI.get();
        if (ui != null) ui.toastInfo("Заменено: " + count);
    }

    @Override
    public void focusLost() {
        closePopup();
    }

    @Override
    public void focusGained() {
        blinkStart = System.currentTimeMillis();
    }
}
