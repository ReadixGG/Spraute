package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Horizontal application menu ("Файл", "Правка", ...). Menus are rebuilt each time they open. */
public class MenuBar extends Widget implements UI.CursorProvider {
    private record Menu(String title, Consumer<PopupMenu> builder) {}

    private final List<Menu> menus = new ArrayList<>();
    private PopupMenu open;
    private int openIndex = -1;

    public MenuBar menu(String title, Consumer<PopupMenu> builder) {
        menus.add(new Menu(title, builder));
        return this;
    }

    public int preferredWidth() {
        int wsum = 0;
        net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
        for (Menu m : menus) wsum += f.width(m.title) + 14;
        return wsum;
    }

    private int indexAt(double mx) {
        net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
        int xx = x;
        for (int i = 0; i < menus.size(); i++) {
            int mw = f.width(menus.get(i).title) + 14;
            if (mx >= xx && mx < xx + mw) return i;
            xx += mw;
        }
        return -1;
    }

    private int menuX(int index) {
        net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
        int xx = x;
        for (int i = 0; i < index; i++) xx += f.width(menus.get(i).title) + 14;
        return xx;
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_HAND;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        UI ui = UI.get();
        if (open != null && ui != null && !ui.isOverlayOpen(open)) {
            open = null;
            openIndex = -1;
        }
        // hovering another title while a menu is open switches menus
        int hov = isHovered() ? indexAt(mx) : -1;
        if (open != null && hov >= 0 && hov != openIndex) openMenu(hov);
        int xx = x;
        for (int i = 0; i < menus.size(); i++) {
            Menu m = menus.get(i);
            int mw = c.width(m.title) + 14;
            if (i == openIndex) c.round(xx + 1, y + 2, mw - 2, h - 4, 3, Theme.ACCENT_DIM);
            else if (i == hov) c.round(xx + 1, y + 2, mw - 2, h - 4, 3, Theme.HOVER);
            c.text(m.title, xx + 7, y + (h - 8) / 2, i == openIndex || i == hov ? Theme.TEXT : Theme.TEXT_DIM);
            xx += mw;
        }
    }

    private void openMenu(int index) {
        if (open != null) open.close();
        PopupMenu pm = new PopupMenu();
        menus.get(index).builder.accept(pm);
        open = pm;
        openIndex = index;
        pm.show(menuX(index), y + h, this);
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        int idx = indexAt(mx);
        if (idx < 0) return false;
        if (idx == openIndex && open != null) {
            open.close();
            open = null;
            openIndex = -1;
        } else {
            openMenu(idx);
        }
        return true;
    }
}
