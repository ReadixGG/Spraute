package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.IntConsumer;

/**
 * Two panes with a draggable divider. The size of the "fixed" side is kept in pixels so the
 * other side absorbs window resizes; hiding a side gives all space to the other one.
 */
public class SplitPane extends Widget implements UI.CursorProvider {
    public final boolean horizontal;
    public final boolean fixedFirst;
    public final Widget first, second;
    public int fixedSize;
    public int minSize = 60;
    public IntConsumer onResize;
    private boolean dragging;
    private static final int GRIP = 4;

    /**
     * @param horizontal true: panes side by side; false: stacked
     * @param fixedFirst true: {@code fixedSize} applies to the first pane, otherwise to the second
     */
    public SplitPane(boolean horizontal, Widget first, Widget second, boolean fixedFirst, int fixedSize) {
        this.horizontal = horizontal;
        this.first = add(first);
        this.second = add(second);
        this.fixedFirst = fixedFirst;
        this.fixedSize = fixedSize;
        this.clipChildren = false;
    }

    private Widget fixedPane() {
        return fixedFirst ? first : second;
    }

    @Override
    public void layout() {
        int total = horizontal ? w : h;
        boolean showFirst = first.visible, showSecond = second.visible;
        if (!showFirst || !showSecond) {
            Widget only = showFirst ? first : second;
            only.bounds(x, y, w, h);
            only.layout();
            return;
        }
        int fixed = Math.max(minSize, Math.min(total - minSize, fixedSize));
        int a = fixedFirst ? fixed : total - fixed - 1;
        if (horizontal) {
            first.bounds(x, y, a, h);
            second.bounds(x + a + 1, y, w - a - 1, h);
        } else {
            first.bounds(x, y, w, a);
            second.bounds(x, y + a + 1, w, h - a - 1);
        }
        first.layout();
        second.layout();
    }

    private int dividerPos() {
        return horizontal ? first.x + first.w : first.y + first.h;
    }

    private boolean onGrip(double mx, double my) {
        if (!first.visible || !second.visible) return false;
        int d = dividerPos();
        return horizontal ? Math.abs(mx - d) <= GRIP && my >= y && my < y + h
                : Math.abs(my - d) <= GRIP && mx >= x && mx < x + w;
    }

    @Override
    public Widget hitTest(double mx, double my) {
        if (!visible || !contains(mx, my)) return null;
        if (onGrip(mx, my)) return this;
        return super.hitTest(mx, my);
    }

    @Override
    public int cursor(int mx, int my) {
        return horizontal ? UI.CURSOR_HRESIZE : UI.CURSOR_VRESIZE;
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        if (!first.visible || !second.visible) return;
        int d = dividerPos();
        boolean hot = dragging || (isHovered() && onGrip(mx, my));
        int color = hot ? Theme.ACCENT : Theme.BORDER;
        if (horizontal) c.fill(d, y, hot ? 2 : 1, h, color);
        else c.fill(x, d, w, hot ? 2 : 1, color);
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button == 0 && onGrip(mx, my)) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        int total = horizontal ? w : h;
        int pos = (int) (horizontal ? mx - x : my - y);
        int size = fixedFirst ? pos : total - pos;
        fixedSize = Math.max(minSize, Math.min(total - minSize, size));
        layout();
        if (onResize != null) onResize.accept(fixedSize);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return true;
    }

    public void toggleFixed() {
        fixedPane().visible = !fixedPane().visible;
        layout();
    }
}
