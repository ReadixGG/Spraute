package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Visual item picker: a searchable grid of item icons, filtered by mod. */
public final class ItemPicker {
    private ItemPicker() {}

    private record Entry(String id, String title, String mod, ItemStack stack) {}

    private static List<Entry> cache;

    private static List<Entry> all() {
        if (cache != null) return cache;
        List<Entry> list = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;
            var key = BuiltInRegistries.ITEM.getKey(it);
            String title;
            try {
                title = it.getDescription().getString();
            } catch (RuntimeException e) {
                title = key.toString();
            }
            list.add(new Entry(key.toString(), title, key.getNamespace(), new ItemStack(it)));
        }
        cache = list;
        return list;
    }

    public static void open(String current, Consumer<String> onPick) {
        UI ui = UI.get();
        if (ui == null) return;
        Dialog d = new Dialog("Выбор предмета", Icon.CUBE).width(Math.min(640, ui.vw - 30));
        Grid grid = new Grid(current, id -> {
            d.close();
            onPick.accept(id);
        });
        grid.prefH = Math.min(360, ui.vh - 120);
        d.body.add(grid);
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        Runnable ok = () -> {
            if (grid.selected != null) {
                d.close();
                onPick.accept(grid.selected.id);
            }
        };
        d.addButton("Выбрать", Button.Style.PRIMARY, ok);
        d.onEnter = ok;
        d.open();
        grid.search.requestFocus();
    }

    private static final class Grid extends Widget {
        static final int CELL = 20;
        final TextField search = new TextField().placeholder("Поиск по названию или id").icon(Icon.SEARCH);
        final Dropdown<String> mod;
        final Consumer<String> choose;
        String modFilter = "";
        List<Entry> shown = new ArrayList<>();
        Entry selected, hovered;
        double scroll;
        long lastClick;
        Entry lastClicked;

        Grid(String current, Consumer<String> choose) {
            this.choose = choose;
            add(search);
            TreeSet<String> mods = new TreeSet<>();
            for (Entry e : all()) mods.add(e.mod);
            List<String> modList = new ArrayList<>();
            modList.add("");
            modList.addAll(mods);
            mod = add(new Dropdown<>(() -> modList, () -> modFilter, m -> m.isEmpty() ? "Все моды" : m, m -> {
                modFilter = m;
                refresh();
            }));
            search.onChange = s -> refresh();
            refresh();
            String cur = current == null ? "" : current.replace("\"", "").trim();
            if (!cur.isEmpty() && !cur.contains(":")) cur = "minecraft:" + cur;
            for (Entry e : shown) {
                if (e.id.equals(cur)) {
                    selected = e;
                    break;
                }
            }
        }

        void refresh() {
            String q = search.text();
            List<Entry> out = new ArrayList<>();
            for (Entry e : all()) {
                if (!modFilter.isEmpty() && !e.mod.equals(modFilter)) continue;
                if (AssetIndex.matches(q, e.id, e.title)) out.add(e);
            }
            shown = out;
            scroll = 0;
        }

        int cols() {
            return Math.max(1, (w - 8) / CELL);
        }

        int gridY() {
            return y + 24;
        }

        int gridH() {
            return h - 24 - 30;
        }

        int maxScroll() {
            int rows = (shown.size() + cols() - 1) / cols();
            return Math.max(0, rows * CELL - gridH());
        }

        @Override
        public void layout() {
            int mw = Math.min(150, w / 3);
            search.bounds(x, y, w - mw - 6, 18);
            mod.bounds(x + w - mw, y, mw, 18);
            super.layout();
        }

        Entry at(double mx, double my) {
            int gy = gridY();
            if (mx < x || mx >= x + cols() * CELL || my < gy || my >= gy + gridH()) return null;
            int col = (int) ((mx - x) / CELL), row = (int) ((my - gy + scroll) / CELL);
            int i = row * cols() + col;
            return i >= 0 && i < shown.size() ? shown.get(i) : null;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            int gy = gridY(), gh = gridH(), cols = cols();
            c.fill(x, gy, w, gh, Theme.INPUT);
            c.outline(x, gy, w, gh, Theme.BORDER);
            hovered = at(mx, my);
            c.pushClip(x, gy, w, gh);
            int first = (int) (scroll / CELL) * cols;
            int last = Math.min(shown.size(), first + (gh / CELL + 2) * cols);
            for (int i = first; i < last; i++) {
                Entry e = shown.get(i);
                int cx = x + (i % cols) * CELL, cy = gy + (i / cols) * CELL - (int) scroll;
                if (e == selected) c.round(cx, cy, CELL, CELL, 3, Theme.ACCENT);
                else if (e == hovered) c.round(cx, cy, CELL, CELL, 3, Theme.RAISED);
                c.flush();
                c.g.renderItem(e.stack, cx + 2, cy + 2);
            }
            c.popClip();
            if (shown.isEmpty()) c.textCentered("Ничего не найдено", x + w / 2, gy + gh / 2 - 4, Theme.TEXT_MUTED);
            // details of the hovered or selected item
            Entry info = hovered != null ? hovered : selected;
            int iy = gy + gh + 6;
            if (info != null) {
                c.flush();
                c.g.renderItem(info.stack, x + 2, iy + 2);
                c.text(info.title, x + 24, iy + 2, Theme.TEXT);
                c.text(info.id, x + 24, iy + 12, Theme.TEXT_MUTED);
            }
            String count = shown.size() + " предметов";
            c.text(count, x + w - c.width(count), iy + 7, Theme.TEXT_MUTED);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            if (super.mousePressed(mx, my, button)) return true;
            Entry e = at(mx, my);
            if (e == null) return false;
            long now = System.currentTimeMillis();
            if (e == lastClicked && now - lastClick < 350) {
                choose.accept(e.id);
                return true;
            }
            selected = e;
            lastClicked = e;
            lastClick = now;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (my >= gridY()) {
                scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * CELL * 2));
                return true;
            }
            return super.mouseScrolled(mx, my, delta);
        }

        @Override
        public String tooltipText(int mx, int my) {
            Entry e = at(mx, my);
            return e != null ? e.title + "\n" + e.id : super.tooltipText(mx, my);
        }
    }
}
