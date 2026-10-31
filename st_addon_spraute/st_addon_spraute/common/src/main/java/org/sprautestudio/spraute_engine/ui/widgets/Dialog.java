package org.sprautestudio.spraute_engine.ui.widgets;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.ui.Box;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Function;

/** Modal dialog window with a title, a content area and a button row. */
public class Dialog extends Widget implements UI.OverlayLayout {
    private final String title;
    private final Icon icon;
    public final Box body = Box.column().gap(6);
    public final Box buttons = Box.row().gap(6).justify(Box.Align.END);
    public int dialogW = 300;
    public Runnable onEnter;
    private static final int HEADER = 24;

    public Dialog(String title, Icon icon) {
        this.title = title;
        this.icon = icon;
        add(body);
        add(buttons);
        focusable = true;
    }

    public Dialog width(int w) {
        this.dialogW = w;
        return this;
    }

    public Button addButton(String text, Button.Style style, Runnable action) {
        Button b = new Button(text, action).style(style);
        b.autoWidth();
        b.prefW = Math.max(70, b.prefW);
        buttons.add(b);
        return b;
    }

    public void open() {
        UI ui = UI.get();
        if (ui == null) return;
        ui.openOverlay(this, true, false, null);
        // focus the first text field if any
        Widget field = findField(body);
        if (field != null) field.requestFocus();
        else requestFocus();
    }

    private static Widget findField(Widget w) {
        if (w instanceof TextField) return w;
        for (Widget c : w.children()) {
            Widget f = findField(c);
            if (f != null) return f;
        }
        return null;
    }

    public void close() {
        UI ui = UI.get();
        if (ui != null) ui.closeOverlay(this);
    }

    private int bodyHeight() {
        int hsum = 0;
        int n = 0;
        for (Widget c : body.children()) {
            if (!c.visible) continue;
            hsum += Math.max(0, c.prefH);
            n++;
        }
        return hsum + Math.max(0, n - 1) * body.gap;
    }

    @Override
    public void layoutOverlay(int vw, int vh) {
        int dw = Math.min(dialogW, vw - 20);
        int dh = HEADER + 10 + bodyHeight() + 12 + 20 + 12;
        bounds((vw - dw) / 2, Math.max(10, (vh - dh) / 2 - 20), dw, dh);
        body.bounds(x + 12, y + HEADER + 10, w - 24, bodyHeight());
        body.layout();
        buttons.bounds(x + 12, y + h - 32, w - 24, 20);
        buttons.layout();
    }

    @Override
    public void layout() {
        UI ui = UI.get();
        if (ui != null) layoutOverlay(ui.vw, ui.vh);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.shadow(x, y, w, h, 6, 10);
        c.roundBorder(x, y, w, h, 6, Theme.PANEL, Theme.BORDER_STRONG);
        c.fill(x + 1, y + HEADER, w - 2, 1, Theme.BORDER);
        int tx = x + 12;
        if (icon != null) {
            c.icon(icon, tx, y + (HEADER - 9) / 2, Theme.ACCENT);
            tx += 14;
        }
        c.text(title, tx, y + (HEADER - 8) / 2, Theme.TEXT);
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && onEnter != null) {
            onEnter.run();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- presets

    public static void confirm(String title, String message, String okText, boolean danger, Runnable onOk) {
        Dialog d = new Dialog(title, danger ? Icon.WARNING : Icon.INFO);
        Label l = new Label(message).wrap().color(Theme.TEXT_DIM);
        l.prefH = net.minecraft.client.Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal(message), 276).size() * 11 + 2;
        d.body.add(l);
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        Runnable ok = () -> {
            d.close();
            onOk.run();
        };
        d.addButton(okText, danger ? Button.Style.DANGER : Button.Style.PRIMARY, ok);
        d.onEnter = ok;
        d.open();
    }

    public static void info(String title, String message) {
        Dialog d = new Dialog(title, Icon.INFO).width(340);
        Label l = new Label(message).wrap().color(Theme.TEXT_DIM);
        l.prefH = net.minecraft.client.Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal(message), 316).size() * 11 + 2;
        d.body.add(l);
        d.addButton("OK", Button.Style.PRIMARY, d::close);
        d.onEnter = d::close;
        d.open();
    }

    /**
     * Text prompt. {@code validator} returns an error message or null when the value is fine.
     */
    public static void prompt(String title, String label, String initial, Function<String, String> validator, Consumer<String> onOk) {
        Dialog d = new Dialog(title, Icon.EDIT);
        Label l = new Label(label).color(Theme.TEXT_DIM);
        d.body.add(l);
        TextField field = new TextField();
        field.setText(initial);
        field.selectAll();
        d.body.add(field);
        Label error = new Label("").color(Theme.ERROR);
        error.prefH = 10;
        d.body.add(error);
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        Runnable ok = () -> {
            String v = field.text().trim();
            String err = validator != null ? validator.apply(v) : null;
            if (err != null) {
                error.setText(err);
                return;
            }
            d.close();
            onOk.accept(v);
        };
        field.onSubmit = s -> ok.run();
        field.onChange = s -> error.setText("");
        d.addButton("OK", Button.Style.PRIMARY, ok);
        d.onEnter = ok;
        d.open();
    }
}
