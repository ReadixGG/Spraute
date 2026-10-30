package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.Supplier;

/**
 * A floating window over the UI: drag by the title bar, resize from edges and corners, maximize
 * (button or double click on the title), roll up into the title bar, close. Comes to the front on click.
 */
public class Window extends Widget implements UI.OverlayLayout, UI.CursorProvider, UI.RaiseOnClick {
    public static final int HEADER = 22;
    private static final int EDGE = 5, MIN_W = 320, MIN_H = 200;

    private final Supplier<String> title;
    private final Icon icon;
    private final int iconColor;
    private final Widget content;
    private int wx, wy, ww, wh;
    private boolean maximized, rolledUp, placed;
    /** Space kept free at the top and bottom of the screen when maximized (the IDE's bars). */
    public int insetTop, insetBottom;
    public Runnable onClose;
    // interaction
    private int mode; // 0 none, 1 move, 2 resize
    private int edges; // bit 1 left, 2 right, 4 top, 8 bottom
    private double grabX, grabY;
    private int[] start;

    public Window(Supplier<String> title, Icon icon, int iconColor, Widget content) {
        this.title = title;
        this.icon = icon;
        this.iconColor = iconColor;
        this.content = add(content);
        focusable = true;
    }

    public Widget content() {
        return content;
    }

    public void open(int vw, int vh) {
        UI ui = UI.get();
        if (ui == null) return;
        ui.openOverlay(this, false, false, null);
    }

    public void close() {
        UI ui = UI.get();
        if (ui != null) ui.closeOverlay(this);
        if (onClose != null) onClose.run();
    }

    public void toFront() {
        UI ui = UI.get();
        if (ui != null) ui.raiseOverlay(this);
    }

    public void toggleMaximize() {
        maximized = !maximized;
        rolledUp = false;
        relayout();
    }

    private void relayout() {
        UI ui = UI.get();
        if (ui != null) layoutOverlay(ui.vw, ui.vh);
    }

    @Override
    public void layoutOverlay(int vw, int vh) {
        if (!placed) {
            ww = Math.max(MIN_W, vw * 86 / 100);
            wh = Math.max(MIN_H, (vh - insetTop - insetBottom) * 88 / 100);
            wx = (vw - ww) / 2;
            wy = insetTop + Math.max(0, (vh - insetTop - insetBottom - wh) / 2);
            placed = true;
        }
        int avail = vh - insetTop - insetBottom;
        if (maximized) {
            bounds(0, insetTop, vw, avail);
        } else {
            ww = Math.min(ww, vw);
            wh = Math.min(wh, avail);
            wx = Math.max(-ww + 80, Math.min(vw - 80, wx));
            wy = Math.max(insetTop, Math.min(insetTop + avail - HEADER, wy));
            bounds(wx, wy, ww, rolledUp ? HEADER : wh);
        }
        content.visible = !rolledUp;
        content.bounds(x + 1, y + HEADER, w - 2, Math.max(0, h - HEADER - 1));
        content.layout();
    }

    @Override
    public void layout() {
        relayout();
    }

    // ---------------------------------------------------------------- painting

    private int buttonX(int i) {
        return x + w - 22 * (i + 1);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (!maximized) c.shadow(x, y, w, h, 6, 12);
        boolean front = hasFocusWithin();
        c.roundBorder(x, y, w, h, maximized ? 0 : 6, Theme.PANEL, front ? 0xFF4A4F70 : Theme.BORDER_STRONG);
        c.round(x + 1, y + 1, w - 2, HEADER - 1, maximized ? 0 : 5, front ? 0xFF22253A : Theme.BG_DEEP);
        c.fill(x + 1, y + HEADER - 1, w - 2, 1, Theme.BORDER);
        int tx = x + 9;
        if (icon != null) {
            c.icon(icon, tx, y + (HEADER - 9) / 2, iconColor);
            tx += 14;
        }
        c.textEllipsized(title.get(), tx, y + (HEADER - 8) / 2, w - (tx - x) - 74, front ? Theme.TEXT : Theme.TEXT_DIM);
        // close, maximize, roll up
        String[] tips = {"close", "max", "roll"};
        for (int i = 0; i < tips.length; i++) {
            int bx = buttonX(i), by = y + 3;
            boolean hov = mx >= bx && mx < bx + 20 && my >= by && my < by + 16 && isHoveredDeep();
            if (hov) c.round(bx, by, 20, 16, 3, i == 0 ? 0xCCF0607A : 0x22FFFFFF);
            int cx = bx + 6, cy = by + 4;
            int col = hov ? 0xFFFFFFFF : Theme.TEXT_DIM;
            switch (i) {
                case 0 -> c.icon(Icon.CLOSE, cx, cy, col);
                case 1 -> {
                    c.outline(cx, cy, 9, 8, col);
                    c.fill(cx, cy, 9, 2, col);
                }
                default -> c.fill(cx, cy + 6, 9, 2, col);
            }
        }
    }

    // ---------------------------------------------------------------- input

    /** The frame and the title bar belong to the window even where the content lies underneath. */
    @Override
    public Widget hitTest(double mx, double my) {
        if (!visible || !contains(mx, my)) return null;
        if (my < y + HEADER || edgesAt(mx, my) != 0) return this;
        return super.hitTest(mx, my);
    }

    private int edgesAt(double mx, double my) {
        if (maximized || rolledUp) return 0;
        int e = 0;
        if (mx < x + EDGE) e |= 1;
        if (mx >= x + w - EDGE) e |= 2;
        if (my < y + EDGE) e |= 4;
        if (my >= y + h - EDGE) e |= 8;
        return e;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        toFront();
        requestFocus();
        if (button != 0) return true;
        if (my < y + HEADER) {
            for (int i = 0; i < 3; i++) {
                int bx = buttonX(i);
                if (mx >= bx && mx < bx + 20 && my >= y + 3 && my < y + 19) {
                    switch (i) {
                        case 0 -> close();
                        case 1 -> toggleMaximize();
                        default -> {
                            rolledUp = !rolledUp;
                            relayout();
                        }
                    }
                    return true;
                }
            }
        }
        int e = edgesAt(mx, my);
        start = new int[]{x, y, w, h};
        grabX = mx;
        grabY = my;
        if (e != 0) {
            mode = 2;
            edges = e;
        } else if (my < y + HEADER) {
            mode = 1;
        }
        return true;
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int button) {
        if (my < y + HEADER && mx < buttonX(2)) {
            toggleMaximize();
            return true;
        }
        return mousePressed(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mode == 0 || start == null) return true;
        int ddx = (int) Math.round(mx - grabX), ddy = (int) Math.round(my - grabY);
        if (mode == 1) {
            if (maximized) {
                // dragging a maximized window restores it under the cursor
                maximized = false;
                double rel = (grabX - start[0]) / Math.max(1, start[2]);
                wx = (int) (mx - ww * rel);
                wy = (int) my - HEADER / 2;
                start = new int[]{wx, wy, ww, wh};
                grabX = mx;
                grabY = my;
            } else {
                wx = start[0] + ddx;
                wy = start[1] + ddy;
            }
        } else {
            int x0 = start[0], y0 = start[1], x1 = start[0] + start[2], y1 = start[1] + start[3];
            if ((edges & 1) != 0) x0 = Math.min(x1 - MIN_W, x0 + ddx);
            if ((edges & 2) != 0) x1 = Math.max(x0 + MIN_W, x1 + ddx);
            if ((edges & 4) != 0) y0 = Math.min(y1 - MIN_H, y0 + ddy);
            if ((edges & 8) != 0) y1 = Math.max(y0 + MIN_H, y1 + ddy);
            wx = x0;
            wy = y0;
            ww = x1 - x0;
            wh = y1 - y0;
        }
        relayout();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        mode = 0;
        start = null;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Esc must not close the whole window by accident
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    @Override
    public int cursor(int mx, int my) {
        int e = mode == 2 ? edges : edgesAt(mx, my);
        if (e == 1 || e == 2) return UI.CURSOR_HRESIZE;
        if (e == 4 || e == 8) return UI.CURSOR_VRESIZE;
        if (e != 0) return UI.CURSOR_MOVE;
        return UI.CURSOR_ARROW;
    }
}
