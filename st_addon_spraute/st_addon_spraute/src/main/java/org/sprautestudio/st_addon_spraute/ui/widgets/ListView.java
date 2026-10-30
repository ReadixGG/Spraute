package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Virtualized list with keyboard navigation and custom row painting. */
public class ListView<T> extends Widget implements UI.CursorProvider {
    public interface RowPainter<T> {
        void paint(Canvas c, T item, int x, int y, int w, int h, boolean selected, boolean hovered);
    }

    public interface ContextHandler<T> {
        void open(T item, int mx, int my);
    }

    private List<T> items = new ArrayList<>();
    public int rowHeight = 18;
    public RowPainter<T> painter;
    public Consumer<T> onSelect;
    public Consumer<T> onActivate;
    public ContextHandler<T> onContext;
    public String emptyText = "";
    public boolean activateOnSingleClick;
    public int background = 0;
    private int selected = -1;
    private double scroll, targetScroll;
    private boolean draggingBar;

    public ListView(RowPainter<T> painter) {
        this.painter = painter;
        this.focusable = true;
    }

    public void setItems(List<T> items) {
        T sel = selectedItem();
        this.items = new ArrayList<>(items);
        selected = sel != null ? this.items.indexOf(sel) : -1;
        clampScroll();
    }

    public List<T> items() {
        return items;
    }

    public T selectedItem() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public int selectedIndex() {
        return selected;
    }

    public void select(int index) {
        if (items.isEmpty()) {
            selected = -1;
            return;
        }
        selected = Math.max(0, Math.min(items.size() - 1, index));
        ensureVisible(selected);
        if (onSelect != null) onSelect.accept(items.get(selected));
    }

    public void ensureVisible(int index) {
        int top = index * rowHeight;
        if (top < targetScroll) targetScroll = top;
        else if (top + rowHeight > targetScroll + h) targetScroll = top + rowHeight - h;
        clampScroll();
    }

    private int maxScroll() {
        return Math.max(0, items.size() * rowHeight - h);
    }

    private void clampScroll() {
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private int rowAt(double my) {
        int idx = (int) Math.floor((my - y + scroll) / rowHeight);
        return idx >= 0 && idx < items.size() ? idx : -1;
    }

    @Override
    public int cursor(int mx, int my) {
        return rowAt(my) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (Math.abs(targetScroll - scroll) > 0.5) scroll += (targetScroll - scroll) * Math.min(1, dt * 18);
        else scroll = targetScroll;
        if (background != 0) c.fill(x, y, w, h, background);
        c.pushClip(x, y, w, h);
        if (items.isEmpty() && !emptyText.isEmpty()) {
            c.wrapped(emptyText, x + 8, y + 8, w - 16, Theme.TEXT_MUTED, 2);
        }
        int first = Math.max(0, (int) (scroll / rowHeight));
        int last = Math.min(items.size() - 1, (int) ((scroll + h) / rowHeight));
        int hoverRow = isHovered() ? rowAt(my) : -1;
        boolean bar = maxScroll() > 0;
        int rw = w - (bar ? ScrollView.BAR : 0);
        for (int i = first; i <= last; i++) {
            int ry = y + i * rowHeight - (int) Math.round(scroll);
            boolean sel = i == selected;
            if (sel) c.fill(x, ry, rw, rowHeight, hasFocusWithin() ? Theme.SELECTION : Theme.SELECTION_INACTIVE);
            else if (i == hoverRow) c.fill(x, ry, rw, rowHeight, Theme.HOVER);
            painter.paint(c, items.get(i), x, ry, rw, rowHeight, sel, i == hoverRow);
        }
        c.popClip();
        if (bar) {
            int total = items.size() * rowHeight;
            int barH = Math.max(16, h * h / total);
            int barY = y + (int) ((h - barH) * (scroll / Math.max(1, maxScroll())));
            c.round(x + w - ScrollView.BAR + 1, barY, ScrollView.BAR - 2, barH, 2, draggingBar ? 0x88FFFFFF : 0x44FFFFFF);
        }
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button == 0 && maxScroll() > 0 && mx >= x + w - ScrollView.BAR - 1) {
            draggingBar = true;
            mouseDragged(mx, my, button, 0, 0);
            return true;
        }
        int row = rowAt(my);
        if (row >= 0) {
            selected = row;
            if (onSelect != null) onSelect.accept(items.get(row));
            if (button == 1 && onContext != null) onContext.open(items.get(row), (int) mx, (int) my);
            else if (button == 0 && activateOnSingleClick && onActivate != null) onActivate.accept(items.get(row));
            return true;
        }
        if (button == 1 && onContext != null) {
            onContext.open(null, (int) mx, (int) my);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int button) {
        int row = rowAt(my);
        if (button == 0 && row >= 0 && onActivate != null && !activateOnSingleClick) {
            selected = row;
            onActivate.accept(items.get(row));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!draggingBar) return false;
        int total = items.size() * rowHeight;
        int barH = Math.max(16, h * h / Math.max(1, total));
        double t = (my - y - barH / 2.0) / Math.max(1, h - barH);
        targetScroll = scroll = Math.max(0, Math.min(maxScroll(), t * maxScroll()));
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll() <= 0) return false;
        targetScroll -= delta * rowHeight * 3;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> select(selected <= 0 ? 0 : selected - 1);
            case GLFW.GLFW_KEY_DOWN -> select(selected + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> select(selected - Math.max(1, h / rowHeight));
            case GLFW.GLFW_KEY_PAGE_DOWN -> select(selected + Math.max(1, h / rowHeight));
            case GLFW.GLFW_KEY_HOME -> select(0);
            case GLFW.GLFW_KEY_END -> select(items.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (selectedItem() != null && onActivate != null) onActivate.accept(selectedItem());
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
