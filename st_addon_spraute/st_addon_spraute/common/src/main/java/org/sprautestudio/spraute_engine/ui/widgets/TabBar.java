package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Row of tabs; editor-style (closable, dirty marker) or panel-style (flat). */
public class TabBar extends Widget implements UI.CursorProvider {
    public static final class Tab {
        public Supplier<String> title;
        public Icon icon;
        public int iconColor = Theme.TEXT_DIM;
        public Object data;
        public boolean closable;
        public BooleanSupplier dirty;
        public Supplier<String> badge;
        public int badgeColor = Theme.TEXT_MUTED;

        public Tab(Supplier<String> title, Icon icon, Object data) {
            this.title = title;
            this.icon = icon;
            this.data = data;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int selected = -1;
    private double scrollX;
    public boolean editorStyle = true;
    public Consumer<Tab> onSelect;
    public Consumer<Tab> onClose;
    public TabContext onContext;

    public interface TabContext {
        void open(Tab tab, int mx, int my);
    }

    public List<Tab> tabs() {
        return tabs;
    }

    public Tab add(Tab t) {
        tabs.add(t);
        return t;
    }

    public void removeTab(Tab t) {
        int idx = tabs.indexOf(t);
        if (idx < 0) return;
        tabs.remove(idx);
        if (selected >= tabs.size()) selected = tabs.size() - 1;
        else if (idx < selected) selected--;
    }

    public Tab selected() {
        return selected >= 0 && selected < tabs.size() ? tabs.get(selected) : null;
    }

    public void select(Tab t) {
        selected = tabs.indexOf(t);
        if (t != null && onSelect != null) onSelect.accept(t);
    }

    public Tab find(Object data) {
        for (Tab t : tabs) if (t.data == data || (t.data != null && t.data.equals(data))) return t;
        return null;
    }

    private int tabWidth(Canvas c, Tab t) {
        int tw = (c != null ? c.width(t.title.get()) : net.minecraft.client.Minecraft.getInstance().font.width(t.title.get()));
        int wsum = tw + 16 + (t.icon != null ? 13 : 0) + (t.closable ? 14 : 0);
        if (t.badge != null && t.badge.get() != null && !t.badge.get().isEmpty()) {
            wsum += net.minecraft.client.Minecraft.getInstance().font.width(t.badge.get()) + 8;
        }
        return Math.min(editorStyle ? 180 : 160, wsum);
    }

    private int tabX(int index) {
        int xx = x - (int) scrollX;
        for (int i = 0; i < index; i++) xx += tabWidth(null, tabs.get(i));
        return xx;
    }

    private int indexAt(double mx) {
        int xx = x - (int) scrollX;
        for (int i = 0; i < tabs.size(); i++) {
            int tw = tabWidth(null, tabs.get(i));
            if (mx >= xx && mx < xx + tw) return i;
            xx += tw;
        }
        return -1;
    }

    private boolean onCloseButton(int index, double mx) {
        Tab t = tabs.get(index);
        if (!t.closable) return false;
        int right = tabX(index) + tabWidth(null, t);
        return mx >= right - 16 && mx < right - 3;
    }

    @Override
    public int cursor(int mx, int my) {
        return indexAt(mx) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, editorStyle ? Theme.BG_DEEP : Theme.PANEL);
        c.fill(x, y + h - 1, w, 1, Theme.BORDER);
        int hov = isHovered() ? indexAt(mx) : -1;
        c.pushClip(x, y, w, h);
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            int tx = tabX(i), tw = tabWidth(c, t);
            if (tx > x + w || tx + tw < x) continue;
            boolean sel = i == selected;
            if (editorStyle) {
                if (sel) {
                    c.fill(tx, y, tw, h, Theme.CODE_BG);
                    c.fill(tx, y, tw, 2, Theme.ACCENT);
                } else if (i == hov) {
                    c.fill(tx, y, tw, h - 1, Theme.PANEL);
                }
                c.fill(tx + tw - 1, y + 4, 1, h - 8, Theme.BORDER);
            } else {
                if (sel) c.fill(tx + 4, y + h - 2, tw - 8, 2, Theme.ACCENT);
                else if (i == hov) c.fill(tx + 4, y + h - 2, tw - 8, 1, Theme.BORDER_STRONG);
            }
            int ix = tx + 8;
            int ty = y + (h - 8) / 2;
            if (t.icon != null) {
                c.icon(t.icon, ix, y + (h - 9) / 2, t.iconColor);
                ix += 13;
            }
            String badge = t.badge != null ? t.badge.get() : null;
            int badgeW = badge != null && !badge.isEmpty() ? c.width(badge) + 8 : 0;
            int titleW = tw - (ix - tx) - (t.closable ? 16 : 6) - badgeW;
            c.textEllipsized(t.title.get(), ix, ty, titleW, sel ? Theme.TEXT : Theme.TEXT_DIM);
            if (badgeW > 0) {
                int bx = ix + Math.min(titleW, c.width(t.title.get())) + 4;
                c.round(bx, ty - 2, badgeW - 2, 11, 4, Theme.alpha(t.badgeColor, 0.25f));
                c.text(badge, bx + 3, ty, t.badgeColor);
            }
            if (t.closable) {
                int cx = tx + tw - 14;
                boolean dirty = t.dirty != null && t.dirty.getAsBoolean();
                boolean hotClose = i == hov && onCloseButton(i, mx);
                if (hotClose) c.round(cx - 2, y + (h - 13) / 2, 13, 13, 3, Theme.HOVER);
                if (dirty && !hotClose) c.icon(Icon.DOT, cx, y + (h - 9) / 2, Theme.TEXT);
                else if (sel || i == hov) c.icon(Icon.CLOSE, cx, y + (h - 9) / 2, hotClose ? Theme.TEXT : Theme.TEXT_MUTED);
            }
        }
        c.popClip();
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        int idx = indexAt(mx);
        if (idx < 0) return false;
        Tab t = tabs.get(idx);
        if (button == 2 && t.closable) {
            if (onClose != null) onClose.accept(t);
            return true;
        }
        if (button == 1) {
            select(t);
            if (onContext != null) onContext.open(t, (int) mx, (int) my);
            return true;
        }
        if (button == 0 && onCloseButton(idx, mx)) {
            if (onClose != null) onClose.accept(t);
            return true;
        }
        select(t);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int total = 0;
        for (Tab t : tabs) total += tabWidth(null, t);
        scrollX = Math.max(0, Math.min(Math.max(0, total - w), scrollX - delta * 30));
        return true;
    }
}
