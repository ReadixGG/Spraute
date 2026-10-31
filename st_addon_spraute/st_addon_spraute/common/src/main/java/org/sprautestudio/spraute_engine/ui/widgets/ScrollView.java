package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.Widget;

/**
 * Vertical scroll container for one content widget. The content's height comes from
 * {@link #contentHeight()} (default: content.prefH).
 */
public class ScrollView extends Widget {
    protected final Widget content;
    protected double scroll, targetScroll;
    private boolean draggingBar;
    private double dragOffset;
    public static final int BAR = 6;

    public ScrollView(Widget content) {
        this.content = add(content);
    }

    public Widget content() {
        return content;
    }

    protected int contentHeight() {
        return Math.max(0, content.prefH);
    }

    public int maxScroll() {
        return Math.max(0, contentHeight() - h);
    }

    public void scrollTo(double value) {
        targetScroll = Math.max(0, Math.min(maxScroll(), value));
    }

    public void scrollToInstant(double value) {
        targetScroll = scroll = Math.max(0, Math.min(maxScroll(), value));
        layout();
    }

    public void ensureVisible(int top, int bottom) {
        double rel = top - y;
        if (top - (y - scroll) < scroll) scrollTo(top - (y - scroll));
        else if (bottom - (y - scroll) > scroll + h) scrollTo(bottom - (y - scroll) - h);
    }

    @Override
    public void layout() {
        int ch = contentHeight();
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        content.bounds(x, y - (int) Math.round(scroll), w - (ch > h ? BAR : 0), Math.max(ch, h));
        content.layout();
    }

    @Override
    public void render(Canvas c, int mx, int my, float dt) {
        if (Math.abs(targetScroll - scroll) > 0.5) {
            scroll += (targetScroll - scroll) * Math.min(1, dt * 18);
            layout();
        } else if (scroll != targetScroll) {
            scroll = targetScroll;
            layout();
        }
        super.render(c, mx, my, dt);
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        int ch = contentHeight();
        if (ch <= h) return;
        int barH = Math.max(16, h * h / ch);
        int barY = y + (int) ((h - barH) * (scroll / Math.max(1, maxScroll())));
        boolean hot = draggingBar || (isHoveredDeep() && mx >= x + w - BAR - 2);
        c.round(x + w - BAR + 1, barY, BAR - 2, barH, 2, hot ? 0x88FFFFFF : 0x44FFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll() <= 0) return false;
        scrollTo(targetScroll - delta * 36);
        return true;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        int ch = contentHeight();
        if (button == 0 && ch > h && mx >= x + w - BAR - 1) {
            int barH = Math.max(16, h * h / ch);
            int barY = y + (int) ((h - barH) * (scroll / Math.max(1, maxScroll())));
            draggingBar = true;
            dragOffset = (my >= barY && my <= barY + barH) ? my - barY : barH / 2.0;
            mouseDragged(mx, my, button, 0, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!draggingBar) return false;
        int ch = contentHeight();
        int barH = Math.max(16, h * h / ch);
        double t = (my - dragOffset - y) / Math.max(1, h - barH);
        scrollToInstant(t * maxScroll());
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return true;
    }

    /** Colors for subclasses that paint their own backgrounds. */
    protected static int background() {
        return Theme.PANEL;
    }
}
