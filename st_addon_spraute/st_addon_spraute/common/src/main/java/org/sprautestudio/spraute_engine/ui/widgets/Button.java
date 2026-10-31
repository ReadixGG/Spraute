package org.sprautestudio.spraute_engine.ui.widgets;

import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class Button extends Widget implements UI.CursorProvider {
    public enum Style { DEFAULT, PRIMARY, GHOST, DANGER, SUCCESS }

    private Supplier<String> text;
    public Icon icon;
    public Style style = Style.DEFAULT;
    public Runnable onClick;
    public int iconColor = 0;
    /** When non-null the button renders as a toggle that is "on" while this returns true. */
    public BooleanSupplier active;
    public BooleanSupplier enabledWhen;
    private float pressAnim;

    public Button(String text, Runnable onClick) {
        this(() -> text, onClick);
    }

    public Button(Supplier<String> text, Runnable onClick) {
        this.text = text;
        this.onClick = onClick;
        this.prefH = 18;
        this.prefW = -1;
    }

    public static Button icon(Icon icon, String tooltip, Runnable onClick) {
        Button b = new Button("", onClick);
        b.icon = icon;
        b.style = Style.GHOST;
        b.prefW = 18;
        b.tooltip(tooltip);
        return b;
    }

    public Button icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public Button style(Style s) {
        this.style = s;
        return this;
    }

    public Button iconColor(int c) {
        this.iconColor = c;
        return this;
    }

    public Button toggle(BooleanSupplier active) {
        this.active = active;
        return this;
    }

    public Button enabledWhen(BooleanSupplier b) {
        this.enabledWhen = b;
        return this;
    }

    public Button autoWidth() {
        String s = text.get();
        int tw = net.minecraft.client.Minecraft.getInstance().font.width(s);
        this.prefW = tw + (icon != null ? (s.isEmpty() ? 18 : 27) : 16);
        return this;
    }

    public String text() {
        return text.get();
    }

    private boolean isEnabledNow() {
        return enabled && (enabledWhen == null || enabledWhen.getAsBoolean());
    }

    @Override
    public int cursor(int mx, int my) {
        return isEnabledNow() ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        boolean on = active != null && active.getAsBoolean();
        boolean en = isEnabledNow();
        pressAnim += (isPressed() ? 1 : -1) * dt * 12;
        pressAnim = Math.max(0, Math.min(1, pressAnim));
        int bg, fg, border = 0;
        switch (style) {
            case PRIMARY -> {
                bg = Theme.lerp(Theme.ACCENT, Theme.ACCENT_HOVER, hoverAnim);
                fg = Theme.TEXT_ON_ACCENT;
            }
            case DANGER -> {
                bg = Theme.lerp(0xFF8A2C3E, Theme.ERROR, hoverAnim);
                fg = Theme.TEXT_ON_ACCENT;
            }
            case SUCCESS -> {
                bg = Theme.lerp(0xFF2D7A55, Theme.SUCCESS, hoverAnim);
                fg = Theme.TEXT_ON_ACCENT;
            }
            case GHOST -> {
                bg = on ? Theme.ACCENT_DIM : Theme.alpha(0x22FFFFFF, hoverAnim);
                fg = on ? Theme.TEXT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverAnim);
            }
            default -> {
                bg = on ? Theme.ACCENT_DIM : Theme.lerp(Theme.RAISED, Theme.RAISED_HOVER, hoverAnim);
                fg = Theme.TEXT;
                border = Theme.BORDER_STRONG;
            }
        }
        if (!en) {
            bg = style == Style.GHOST ? 0 : Theme.RAISED;
            fg = Theme.TEXT_MUTED;
        }
        int inset = (int) (pressAnim * 1);
        if ((bg >>> 24) != 0) {
            if (border != 0) c.roundBorder(x + inset, y + inset, w - 2 * inset, h - 2 * inset, 3, bg, Theme.lerp(border, Theme.ACCENT, hoverAnim * 0.4f));
            else c.round(x + inset, y + inset, w - 2 * inset, h - 2 * inset, 3, bg);
        }
        if (isFocused()) c.outline(x, y, w, h, Theme.ACCENT_DIM);
        String s = text.get();
        int tw = s.isEmpty() ? 0 : c.width(s);
        int contentW = tw + (icon != null ? (s.isEmpty() ? Icon.SIZE : Icon.SIZE + 5) : 0);
        int cx = x + Math.max(4, (w - contentW) / 2);
        int cy = y + (h - 8) / 2;
        if (icon != null) {
            int ic = !en ? Theme.TEXT_MUTED : iconColor != 0 ? iconColor : fg;
            c.icon(icon, cx, y + (h - Icon.SIZE) / 2, ic);
            cx += Icon.SIZE + 5;
        }
        if (!s.isEmpty()) c.text(c.ellipsize(s, w - (cx - x) - 4), cx, cy, fg);
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        return button == 0 && isEnabledNow();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && contains(mx, my) && isEnabledNow() && onClick != null) onClick.run();
        return true;
    }
}
