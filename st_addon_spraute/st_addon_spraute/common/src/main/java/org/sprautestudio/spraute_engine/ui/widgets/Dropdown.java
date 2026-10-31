package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Select box: shows the current value and opens a popup with the options. */
public class Dropdown<T> extends Widget implements UI.CursorProvider {
    private final Supplier<List<T>> options;
    private final Supplier<T> value;
    private final Consumer<T> onPick;
    private final Function<T, String> labeler;
    public String placeholder = "—";
    private boolean searchable;

    public Dropdown(Supplier<List<T>> options, Supplier<T> value, Function<T, String> labeler, Consumer<T> onPick) {
        this.options = options;
        this.value = value;
        this.labeler = labeler;
        this.onPick = onPick;
        this.prefH = 18;
    }

    public Dropdown<T> searchable() {
        searchable = true;
        return this;
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_HAND;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.roundBorder(x, y, w, h, 3, Theme.lerp(Theme.INPUT, Theme.RAISED, hoverAnim), Theme.BORDER_STRONG);
        T v = value.get();
        String s = v == null ? placeholder : labeler.apply(v);
        c.textEllipsized(s, x + 6, y + (h - 8) / 2, w - 22, v == null ? Theme.TEXT_MUTED : Theme.TEXT);
        c.icon(Icon.CHEVRON_DOWN, x + w - 14, y + (h - 9) / 2, Theme.TEXT_DIM);
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button != 0) return false;
        List<T> choices = options.get();
        if (searchable || choices.size() > 18) {
            openSearch(choices);
            return true;
        }
        PopupMenu pm = new PopupMenu();
        T current = value.get();
        for (T opt : choices) {
            PopupMenu.Item it = pm.item(labeler.apply(opt), () -> onPick.accept(opt));
            it.checked(() -> opt.equals(current));
        }
        if (pm.isEmpty()) pm.item("(пусто)", null).enabled(() -> false);
        pm.show(x, y + h + 1, this);
        return true;
    }

    private void openSearch(List<T> choices) {
        UI ui = UI.get();
        if (ui == null) return;
        Dialog dialog = new Dialog("Выбор из списка", Icon.SEARCH).width(Math.min(440, ui.vw - 30));
        TextField search = new TextField().placeholder("Поиск по названию или ID").icon(Icon.SEARCH);
        dialog.body.add(search);
        ListView<T> list = new ListView<>((c, opt, x, y, w, h, selected, hovered) ->
                c.textEllipsized(labeler.apply(opt), x + 8, y + (h - 8) / 2, w - 16, selected ? Theme.TEXT : Theme.TEXT_DIM));
        list.prefH = Math.max(100, Math.min(250, ui.vh - 130));
        list.background = Theme.INPUT;
        list.emptyText = "Совпадений нет";
        dialog.body.add(list);
        Runnable pick = () -> {
            T selected = list.selectedItem();
            if (selected == null) return;
            dialog.close();
            onPick.accept(selected);
        };
        list.onActivate = selected -> pick.run();
        search.onChange = query -> {
            List<T> found = new ArrayList<>();
            for (T opt : choices) {
                String label = labeler.apply(opt);
                String id = String.valueOf(opt);
                if (matches(query, label, id)) found.add(opt);
            }
            list.setItems(found);
            if (list.selectedItem() == null && !found.isEmpty()) list.select(0);
        };
        search.onChange.accept("");
        T current = value.get();
        if (current != null && list.items().contains(current)) list.select(list.items().indexOf(current));
        dialog.addButton("Отмена", Button.Style.DEFAULT, dialog::close);
        dialog.addButton("Выбрать", Button.Style.PRIMARY, pick);
        dialog.onEnter = pick;
        dialog.open();
    }

    private static boolean matches(String query, String label, String id) {
        if (query == null || query.isBlank()) return true;
        String hay = (label + " " + id).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        for (String word : query.toLowerCase(Locale.ROOT).trim().replace('_', ' ').replace('-', ' ').split("\\s+"))
            if (!hay.contains(word)) return false;
        return true;
    }
}
