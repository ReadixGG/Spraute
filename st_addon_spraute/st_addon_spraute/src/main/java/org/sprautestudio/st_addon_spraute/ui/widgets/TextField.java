package org.sprautestudio.st_addon_spraute.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Single-line text input with selection, clipboard and word navigation. */
public class TextField extends Widget implements UI.CursorProvider {
    public interface KeyHook {
        boolean onKey(int key, int mods);
    }

    private String text = "";
    private int caret, anchor;
    private int scroll;
    public String placeholder = "";
    public Icon icon;
    public Consumer<String> onChange;
    public Consumer<String> onSubmit;
    public Runnable onEscape;
    public KeyHook keyHook;
    public Predicate<Character> filter;
    public int maxLength = 1000;
    public boolean borderless;
    public int textColor = Theme.TEXT;
    private long blinkStart = System.currentTimeMillis();

    public TextField() {
        focusable = true;
        prefH = 18;
    }

    public TextField placeholder(String p) {
        this.placeholder = p;
        return this;
    }

    public TextField icon(Icon i) {
        this.icon = i;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String s) {
        text = s == null ? "" : s;
        caret = anchor = text.length();
        scroll = 0;
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int textX() {
        return x + (borderless ? 2 : 5) + (icon != null ? 13 : 0);
    }

    private int innerW() {
        return w - (textX() - x) - (borderless ? 2 : 5);
    }

    @Override
    public int cursor(int mx, int my) {
        return UI.CURSOR_IBEAM;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        boolean focused = isFocused();
        if (!borderless) {
            int border = focused ? Theme.ACCENT : Theme.lerp(Theme.BORDER, Theme.BORDER_STRONG, hoverAnim);
            c.roundBorder(x, y, w, h, 3, Theme.INPUT, border);
        }
        if (icon != null) c.icon(icon, x + 5, y + (h - Icon.SIZE) / 2, Theme.TEXT_MUTED);
        int tx = textX();
        int ty = y + (h - 8) / 2;
        ensureCaretVisible();
        c.pushClip(tx, y, innerW() + 1, h);
        if (text.isEmpty() && !placeholder.isEmpty()) {
            c.text(placeholder, tx, ty, Theme.TEXT_MUTED);
        }
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        if (from != to) {
            int sx = tx - scroll + font().width(text.substring(0, from));
            int ex = tx - scroll + font().width(text.substring(0, to));
            c.fill(sx, ty - 1, ex - sx, 10, focused ? Theme.SELECTION : Theme.SELECTION_INACTIVE);
        }
        c.text(text, tx - scroll, ty, enabled ? textColor : Theme.TEXT_MUTED);
        if (focused && ((System.currentTimeMillis() - blinkStart) / 530) % 2 == 0) {
            int cx = tx - scroll + font().width(text.substring(0, caret));
            c.fill(cx, ty - 1, 1, 10, Theme.CODE_CARET);
        }
        c.popClip();
    }

    private void ensureCaretVisible() {
        int cx = font().width(text.substring(0, caret));
        int iw = innerW();
        if (cx - scroll > iw) scroll = cx - iw;
        if (cx - scroll < 0) scroll = cx;
        int total = font().width(text);
        if (total - scroll < iw) scroll = Math.max(0, total - iw);
    }

    private int indexAt(double mx) {
        double rel = mx - textX() + scroll;
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            double d = Math.abs(font().width(text.substring(0, i)) - rel);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button != 0) return false;
        int idx = indexAt(mx);
        if (Minecraft.getInstance().screen != null && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            caret = idx;
        } else {
            caret = anchor = idx;
        }
        blinkStart = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int idx = indexAt(mx);
        anchor = wordLeft(idx);
        caret = wordRight(idx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        caret = indexAt(mx);
        return true;
    }

    private void changed() {
        blinkStart = System.currentTimeMillis();
        if (onChange != null) onChange.accept(text);
    }

    public void insert(String s) {
        StringBuilder clean = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '\n' || ch == '\r') continue;
            if (filter != null && !filter.test(ch)) continue;
            clean.append(ch);
        }
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        String next = text.substring(0, from) + clean + text.substring(to);
        if (next.length() > maxLength) return;
        text = next;
        caret = anchor = from + clean.length();
        changed();
    }

    private void deleteRange(int from, int to) {
        if (from == to) return;
        text = text.substring(0, from) + text.substring(to);
        caret = anchor = from;
        changed();
    }

    private int wordLeft(int i) {
        while (i > 0 && !Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        return i;
    }

    private int wordRight(int i) {
        while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) i++;
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) i++;
        return i;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (keyHook != null && keyHook.onKey(key, mods)) return true;
        boolean shift = UI.shift(mods), ctrl = UI.ctrl(mods);
        int from = Math.min(caret, anchor), to = Math.max(caret, anchor);
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit != null) {
                    onSubmit.accept(text);
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (onEscape != null) {
                    onEscape.run();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                int n = ctrl ? wordLeft(caret) : (!shift && from != to ? from : Math.max(0, caret - 1));
                caret = n;
                if (!shift) anchor = caret;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                int n = ctrl ? wordRight(caret) : (!shift && from != to ? to : Math.min(text.length(), caret + 1));
                caret = n;
                if (!shift) anchor = caret;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caret = 0;
                if (!shift) anchor = caret;
            }
            case GLFW.GLFW_KEY_END -> {
                caret = text.length();
                if (!shift) anchor = caret;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (from != to) deleteRange(from, to);
                else if (caret > 0) deleteRange(ctrl ? wordLeft(caret) : caret - 1, caret);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (from != to) deleteRange(from, to);
                else if (caret < text.length()) deleteRange(caret, ctrl ? wordRight(caret) : caret + 1);
            }
            case GLFW.GLFW_KEY_A -> {
                if (!ctrl) return false;
                selectAll();
            }
            case GLFW.GLFW_KEY_C -> {
                if (!ctrl) return false;
                if (from != to) UI.setClipboard(text.substring(from, to));
            }
            case GLFW.GLFW_KEY_X -> {
                if (!ctrl) return false;
                if (from != to) {
                    UI.setClipboard(text.substring(from, to));
                    deleteRange(from, to);
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (!ctrl) return false;
                insert(UI.clipboard());
            }
            default -> {
                return false;
            }
        }
        blinkStart = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (ch < 32 || ch == 127) return false;
        insert(String.valueOf(ch));
        return true;
    }
}
