package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Context / drop-down menu shown as an overlay. */
public class PopupMenu extends Widget implements UI.OverlayLayout, UI.CursorProvider {
    public static final class Item {
        public final String text;
        public final Icon icon;
        public final String shortcut;
        public final Runnable action;
        public final boolean separator;
        public int iconColor = Theme.TEXT_DIM;
        public BooleanSupplier enabled;
        public BooleanSupplier checked;

        Item(String text, Icon icon, String shortcut, Runnable action, boolean separator) {
            this.text = text;
            this.icon = icon;
            this.shortcut = shortcut;
            this.action = action;
            this.separator = separator;
        }

        public Item enabled(BooleanSupplier e) {
            this.enabled = e;
            return this;
        }

        public Item checked(BooleanSupplier c) {
            this.checked = c;
            return this;
        }

        public Item color(int c) {
            this.iconColor = c;
            return this;
        }

        boolean isEnabled() {
            return enabled == null || enabled.getAsBoolean();
        }
    }

    private final List<Item> items = new ArrayList<>();
    private int anchorX, anchorY;
    private int hoverIndex = -1;
    private static final int ROW = 17, SEP = 7, PAD = 4;
    /** Long lists scroll: the full height of the rows and how far they are scrolled. */
    private int contentH, scroll;
    /** Typed letters jump to the first matching row. */
    private String typed = "";
    private long typedAt;
    public Runnable onClosed;

    public Item item(String text, Icon icon, String shortcut, Runnable action) {
        Item it = new Item(text, icon, shortcut, action, false);
        items.add(it);
        return it;
    }

    public Item item(String text, Runnable action) {
        return item(text, null, null, action);
    }

    public void separator() {
        if (!items.isEmpty() && !items.get(items.size() - 1).separator) items.add(new Item("", null, null, null, true));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public PopupMenu show(int x, int y) {
        return show(x, y, null);
    }

    public PopupMenu show(int x, int y, Widget owner) {
        UI ui = UI.get();
        if (ui == null) return this;
        anchorX = x;
        anchorY = y;
        UI.Overlay o = ui.openOverlay(this, false, true, onClosed);
        o.owner = owner;
        return this;
    }

    public void close() {
        UI ui = UI.get();
        if (ui != null) ui.closeOverlay(this);
    }

    @Override
    public void layoutOverlay(int vw, int vh) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int width = 120;
        for (Item it : items) {
            if (it.separator) continue;
            int iw = 26 + font.width(it.text) + (it.shortcut != null ? font.width(it.shortcut) + 20 : 0) + 10;
            width = Math.max(width, iw);
        }
        int height = PAD * 2;
        for (Item it : items) height += it.separator ? SEP : ROW;
        contentH = height;
        boolean scrolls = height > vh - 4;
        if (scrolls) width += 6;
        // below the anchor, above it, or (too long for both) as tall as fits with a scrollbar
        int below = vh - 2 - anchorY, above = anchorY - 2;
        int py, ph = Math.min(height, vh - 4);
        if (height <= below) py = anchorY;
        else if (height <= above) py = anchorY - height;
        else if (below >= above) {
            ph = Math.min(height, Math.max(below, 60));
            py = Math.min(anchorY, vh - 2 - ph);
        } else {
            ph = Math.min(height, above);
            py = anchorY - ph;
        }
        int px = Math.min(anchorX, vw - width - 2);
        bounds(Math.max(2, px), Math.max(2, py), width, ph);
        clampScroll();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, contentH - h));
    }

    private int rowTop(int idx) {
        int yy = PAD;
        for (int i = 0; i < idx; i++) yy += items.get(i).separator ? SEP : ROW;
        return yy;
    }

    private void reveal(int idx) {
        if (idx < 0) return;
        int top = rowTop(idx);
        if (top - PAD < scroll) scroll = top - PAD;
        if (top + ROW + PAD > scroll + h) scroll = top + ROW + PAD - h;
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (contentH <= h) return true;
        scroll -= (int) (delta * ROW * 2);
        clampScroll();
        return true;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (Character.isISOControl(ch)) return false;
        long now = System.currentTimeMillis();
        typed = (now - typedAt > 900 ? "" : typed) + Character.toLowerCase(ch);
        typedAt = now;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!it.separator && it.text.toLowerCase().contains(typed)) {
                hoverIndex = i;
                reveal(i);
                break;
            }
        }
        return true;
    }

    private int indexAt(double my) {
        if (my < y || my >= y + h) return -1;
        int yy = y + PAD - scroll;
        for (int i = 0; i < items.size(); i++) {
            int hgt = items.get(i).separator ? SEP : ROW;
            if (my >= yy && my < yy + hgt) return items.get(i).separator ? -1 : i;
            yy += hgt;
        }
        return -1;
    }

    @Override
    public int cursor(int mx, int my) {
        return indexAt(my) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.shadow(x, y, w, h, 5, 6);
        c.roundBorder(x, y, w, h, 5, Theme.RAISED, Theme.BORDER_STRONG);
        if (isHovered() && (mx != lastMx || my != lastMy)) hoverIndex = indexAt(my);
        lastMx = mx;
        lastMy = my;
        c.pushClip(x, y + 1, w, h - 2);
        int yy = y + PAD - scroll;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.separator) {
                c.fill(x + 6, yy + SEP / 2, w - 12, 1, Theme.BORDER);
                yy += SEP;
                continue;
            }
            boolean en = it.isEnabled();
            if (i == hoverIndex && en) c.round(x + 3, yy, w - 6, ROW, 3, Theme.ACCENT_DIM);
            boolean checked = it.checked != null && it.checked.getAsBoolean();
            Icon ic = checked ? Icon.CHECK : it.icon;
            if (ic != null) c.icon(ic, x + 9, yy + 4, en ? (checked ? Theme.ACCENT_2 : it.iconColor) : Theme.TEXT_MUTED);
            c.text(it.text, x + 24, yy + 5, en ? Theme.TEXT : Theme.TEXT_MUTED);
            if (it.shortcut != null) {
                c.text(it.shortcut, x + w - 10 - c.width(it.shortcut), yy + 5, Theme.TEXT_MUTED);
            }
            yy += ROW;
        }
        c.popClip();
        if (contentH > h) {
            int th = Math.max(16, h * h / contentH);
            int ty = y + (int) ((long) (h - th) * scroll / Math.max(1, contentH - h));
            c.round(x + w - 5, ty + 2, 3, th - 4, 1, Theme.BORDER_STRONG);
        }
    }

    private int lastMx = Integer.MIN_VALUE, lastMy;

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int idx = indexAt(my);
        if (idx >= 0 && contains(mx, my)) activate(idx);
        return true;
    }

    private void activate(int idx) {
        Item it = items.get(idx);
        if (!it.isEnabled()) return;
        close();
        if (it.action != null) it.action.run();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int dir = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            int i = hoverIndex;
            for (int n = 0; n < items.size(); n++) {
                i = (i + dir + items.size()) % items.size();
                if (!items.get(i).separator) break;
            }
            hoverIndex = i;
            reveal(i);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && hoverIndex >= 0) {
            activate(hoverIndex);
            return true;
        }
        return false;
    }
}
