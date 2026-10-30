package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.List;
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

    public Dropdown(Supplier<List<T>> options, Supplier<T> value, Function<T, String> labeler, Consumer<T> onPick) {
        this.options = options;
        this.value = value;
        this.labeler = labeler;
        this.onPick = onPick;
        this.prefH = 18;
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
        PopupMenu pm = new PopupMenu();
        T current = value.get();
        for (T opt : options.get()) {
            PopupMenu.Item it = pm.item(labeler.apply(opt), () -> onPick.accept(opt));
            it.checked(() -> opt.equals(current));
        }
        if (pm.isEmpty()) pm.item("(пусто)", null).enabled(() -> false);
        pm.show(x, y + h + 1, this);
        return true;
    }
}
