package org.sprautestudio.st_addon_spraute.ui.code;

import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Auto-completion list shown under the caret, with a documentation side panel. */
public class CompletionPopup extends Widget implements UI.OverlayLayout, UI.CursorProvider {
    private List<LanguageSupport.Completion> items = new ArrayList<>();
    private int selected;
    private int scroll;
    private int anchorX, anchorY, lineH;
    private static final int ROW = 14, MAX_ROWS = 10, LIST_W = 210, DOC_W = 200;
    private final Consumer<LanguageSupport.Completion> onAccept;

    public CompletionPopup(Consumer<LanguageSupport.Completion> onAccept) {
        this.onAccept = onAccept;
    }

    public void setItems(List<LanguageSupport.Completion> items) {
        this.items = items;
        selected = 0;
        scroll = 0;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void anchor(int x, int y, int lineH) {
        this.anchorX = x;
        this.anchorY = y;
        this.lineH = lineH;
        UI ui = UI.get();
        if (ui != null) layoutOverlay(ui.vw, ui.vh);
    }

    public LanguageSupport.Completion selected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public void move(int delta) {
        if (items.isEmpty()) return;
        selected = Math.floorMod(selected + delta, items.size());
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + MAX_ROWS) scroll = selected - MAX_ROWS + 1;
    }

    private int listH() {
        return Math.min(items.size(), MAX_ROWS) * ROW + 4;
    }

    @Override
    public void layoutOverlay(int vw, int vh) {
        int hgt = listH();
        int py = anchorY + lineH + 2;
        if (py + hgt > vh - 4) py = anchorY - hgt - 2;
        int totalW = LIST_W + DOC_W;
        int px = Math.min(anchorX, vw - totalW - 4);
        bounds(Math.max(2, px), py, totalW, Math.max(hgt, 60));
    }

    @Override
    public Widget hitTest(double mx, double my) {
        if (!visible) return null;
        if (mx >= x && mx < x + LIST_W && my >= y && my < y + listH()) return this;
        return null;
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_HAND;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (items.isEmpty()) return;
        int lh = listH();
        c.shadow(x, y, LIST_W, lh, 4, 6);
        c.roundBorder(x, y, LIST_W, lh, 4, Theme.RAISED, Theme.BORDER_STRONG);
        int end = Math.min(items.size(), scroll + MAX_ROWS);
        for (int i = scroll; i < end; i++) {
            LanguageSupport.Completion it = items.get(i);
            int ry = y + 2 + (i - scroll) * ROW;
            if (i == selected) c.round(x + 2, ry, LIST_W - 4, ROW, 3, Theme.ACCENT_DIM);
            c.icon(it.kind().icon, x + 6, ry + 2, it.kind().color);
            int detailW = it.detail() != null ? Math.min(80, c.width(it.detail())) : 0;
            c.textEllipsized(it.label(), x + 19, ry + 3, LIST_W - 28 - detailW, Theme.TEXT);
            if (it.detail() != null) {
                c.textEllipsized(it.detail(), x + LIST_W - 6 - detailW, ry + 3, detailW, Theme.TEXT_MUTED);
            }
        }
        if (items.size() > MAX_ROWS) {
            int barH = Math.max(10, lh * MAX_ROWS / items.size());
            int barY = y + (lh - barH) * scroll / Math.max(1, items.size() - MAX_ROWS);
            c.fill(x + LIST_W - 3, barY, 2, barH, 0x55FFFFFF);
        }
        LanguageSupport.Completion sel = selected();
        if (sel != null && sel.doc() != null && !sel.doc().isEmpty()) {
            int dx = x + LIST_W + 4;
            String title = sel.label();
            int bodyH = c.wrappedHeight(sel.doc(), DOC_W - 16, 2);
            int dh = Math.min(220, bodyH + 26);
            c.shadow(dx, y, DOC_W, dh, 4, 6);
            c.roundBorder(dx, y, DOC_W, dh, 4, Theme.PANEL, Theme.BORDER_STRONG);
            c.textEllipsized(title, dx + 8, y + 6, DOC_W - 16, sel.kind().color);
            c.fill(dx + 8, y + 17, DOC_W - 16, 1, Theme.BORDER);
            c.pushClip(dx, y + 19, DOC_W, dh - 20);
            c.wrapped(sel.doc(), dx + 8, y + 22, DOC_W - 16, Theme.TEXT_DIM, 2);
            c.popClip();
        }
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        int idx = scroll + (int) ((my - y - 2) / ROW);
        if (idx >= 0 && idx < items.size()) {
            if (idx == selected) onAccept.accept(items.get(idx));
            else selected = idx;
        }
        return true;
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int button) {
        LanguageSupport.Completion s = selected();
        if (s != null) onAccept.accept(s);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, items.size() - MAX_ROWS), scroll - (int) Math.signum(delta) * 2));
        return true;
    }
}
