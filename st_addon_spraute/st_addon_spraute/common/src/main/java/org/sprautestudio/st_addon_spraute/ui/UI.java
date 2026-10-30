package org.sprautestudio.st_addon_spraute.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minecraft screen hosting a Spraute widget tree: virtual scaling, overlays (menus, popups,
 * modal dialogs), focus, hover, tooltips, toasts and cursor shapes.
 */
public abstract class UI extends Screen {
    private static UI current;

    public static UI get() {
        return current;
    }

    public static final class Overlay {
        public final Widget widget;
        public final boolean modal;
        public final boolean closeOnOutside;
        public final Runnable onClose;
        /** Widget that opened the overlay; clicks on it don't count as "outside". */
        public Widget owner;

        Overlay(Widget widget, boolean modal, boolean closeOnOutside, Runnable onClose) {
            this.widget = widget;
            this.modal = modal;
            this.closeOnOutside = closeOnOutside;
            this.onClose = onClose;
        }
    }

    private record Toast(String text, int color, Icon icon, long born, long life) {}

    protected final Widget root = new Widget();
    private final List<Overlay> overlays = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();
    private Widget hovered, focused, pressed;
    private int pressedButton = -1;
    private long lastClickTime;
    private double lastClickX, lastClickY;
    private int clickCount;
    private long hoverStart;
    private Widget tooltipFor;
    private long lastFrame = System.nanoTime();
    /** Physical pixels per virtual pixel (may be fractional); 0 = follow Minecraft's GUI scale. */
    private double uiScale;
    private float factor = 1f;
    public int vw, vh;
    private int mouseVX, mouseVY;
    private long cursorShape = 0;
    private int currentCursor = GLFW.GLFW_ARROW_CURSOR;
    private boolean needsLayout = true;

    public static final int CURSOR_ARROW = GLFW.GLFW_ARROW_CURSOR;
    public static final int CURSOR_IBEAM = GLFW.GLFW_IBEAM_CURSOR;
    public static final int CURSOR_HAND = GLFW.GLFW_HAND_CURSOR;
    public static final int CURSOR_HRESIZE = GLFW.GLFW_HRESIZE_CURSOR;
    public static final int CURSOR_VRESIZE = GLFW.GLFW_VRESIZE_CURSOR;
    public static final int CURSOR_MOVE = GLFW.GLFW_CROSSHAIR_CURSOR;

    protected UI(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------ scale

    public void setUiScale(double physicalScale) {
        this.uiScale = physicalScale;
        needsLayout = true;
    }

    public double uiScale() {
        return uiScale;
    }

    private void updateScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        double phys = uiScale <= 0 ? gui : uiScale;
        factor = (float) (phys / gui);
        int nvw = (int) Math.floor(width / factor);
        int nvh = (int) Math.floor(height / factor);
        if (nvw != vw || nvh != vh) {
            vw = nvw;
            vh = nvh;
            needsLayout = true;
        }
    }

    public float factor() {
        return factor;
    }

    public void relayout() {
        needsLayout = true;
    }

    private void doLayout() {
        root.bounds(0, 0, vw, vh);
        root.layout();
        for (Overlay o : overlays) {
            if (o.widget instanceof OverlayLayout ol) ol.layoutOverlay(vw, vh);
            else o.widget.layout();
        }
        needsLayout = false;
    }

    /** Overlays implementing this come to the front when clicked (windows). */
    public interface RaiseOnClick {}

    /** Moves an overlay above the others. */
    public void raiseOverlay(Widget widget) {
        for (int i = 0; i < overlays.size(); i++) {
            Overlay o = overlays.get(i);
            if (o.widget == widget && i != overlays.size() - 1) {
                overlays.remove(i);
                overlays.add(o);
                return;
            }
        }
    }

    /** Overlays implementing this get the viewport size to position themselves. */
    public interface OverlayLayout {
        void layoutOverlay(int viewportW, int viewportH);
    }

    // --------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        current = this;
        updateScale();
        needsLayout = true;
    }

    @Override
    public void removed() {
        setCursor(CURSOR_ARROW);
        if (current == this) current = null;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        current = this;
        root.tick();
        for (Overlay o : new ArrayList<>(overlays)) o.widget.tick();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        current = this;
        updateScale();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (needsLayout) doLayout();

        double rawX = Minecraft.getInstance().mouseHandler.xpos() * width / Minecraft.getInstance().getWindow().getScreenWidth();
        double rawY = Minecraft.getInstance().mouseHandler.ypos() * height / Minecraft.getInstance().getWindow().getScreenHeight();
        mouseVX = (int) Math.floor(rawX / factor);
        mouseVY = (int) Math.floor(rawY / factor);
        updateHover();

        g.pose().pushPose();
        g.pose().scale(factor, factor, 1);
        Canvas c = new Canvas(g, font, factor);
        root.render(c, mouseVX, mouseVY, dt);
        float z = 200;
        for (Overlay o : new ArrayList<>(overlays)) {
            c.pushLayer(z);
            if (o.modal) c.fill(0, 0, vw, vh, Theme.OVERLAY_DIM);
            o.widget.render(c, mouseVX, mouseVY, dt);
            c.popLayer();
            z += 50;
        }
        c.pushLayer(z + 100);
        renderToasts(c);
        renderTooltip(c);
        c.popLayer();
        g.pose().popPose();

        Widget cursorSource = pressed != null ? pressed : hovered;
        setCursor(cursorSource != null && cursorSource instanceof CursorProvider cp ? cp.cursor(mouseVX, mouseVY) : CURSOR_ARROW);
    }

    /** Widgets implementing this choose the mouse cursor shape. */
    public interface CursorProvider {
        int cursor(int mx, int my);
    }

    private void setCursor(int shape) {
        if (shape == currentCursor) return;
        currentCursor = shape;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (cursorShape != 0) {
            GLFW.glfwDestroyCursor(cursorShape);
            cursorShape = 0;
        }
        if (shape == CURSOR_ARROW) {
            GLFW.glfwSetCursor(window, 0);
        } else {
            cursorShape = GLFW.glfwCreateStandardCursor(shape);
            GLFW.glfwSetCursor(window, cursorShape);
        }
    }

    private void updateHover() {
        Widget h = hitTestAll(mouseVX, mouseVY);
        if (h != hovered) {
            hovered = h;
            hoverStart = System.currentTimeMillis();
            tooltipFor = null;
        }
    }

    private Widget hitTestAll(double mx, double my) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay o = overlays.get(i);
            Widget hit = o.widget.hitTest(mx, my);
            if (hit != null) return hit;
            if (o.modal) return null;
        }
        return root.hitTest(mx, my);
    }

    private void renderTooltip(Canvas c) {
        if (hovered == null || pressed != null) return;
        if (System.currentTimeMillis() - hoverStart < 550) return;
        String text = hovered.tooltipText(mouseVX, mouseVY);
        if (text == null || text.isEmpty()) return;
        int maxW = 260;
        int tw = 0;
        for (String line : text.split("\n")) tw = Math.max(tw, c.width(line));
        int w = Math.min(maxW, tw) + 12;
        int h = c.wrappedHeight(text, w - 12, 2) + 8;
        int tx = Math.min(mouseVX + 10, vw - w - 4);
        int ty = mouseVY + 16;
        if (ty + h > vh - 4) ty = mouseVY - h - 6;
        c.shadow(tx, ty, w, h, 4, 4);
        c.roundBorder(tx, ty, w, h, 4, Theme.RAISED, Theme.BORDER_STRONG);
        c.wrapped(text, tx + 6, ty + 5, w - 12, Theme.TEXT, 2);
    }

    // ------------------------------------------------------------------ toasts

    public void toast(String text, Icon icon, int color) {
        toasts.add(new Toast(text, color, icon, System.currentTimeMillis(), 3500));
        if (toasts.size() > 5) toasts.remove(0);
    }

    public void toastInfo(String text) {
        toast(text, Icon.INFO, Theme.INFO);
    }

    public void toastSuccess(String text) {
        toast(text, Icon.CHECK, Theme.SUCCESS);
    }

    public void toastError(String text) {
        toast(text, Icon.ERROR, Theme.ERROR);
    }

    private void renderToasts(Canvas c) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.born > t.life);
        int y = vh - 34;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            float age = (now - t.born) / (float) t.life;
            float alpha = age < 0.06f ? age / 0.06f : age > 0.85f ? (1 - age) / 0.15f : 1f;
            int w = Math.min(320, c.width(t.text) + 34);
            int h = 22;
            int x = vw - w - 12 + (int) ((1 - Math.min(1, alpha * 1.5f)) * 30);
            c.shadow(x, y - h, w, h, 5, 4);
            c.round(x, y - h, w, h, 5, Theme.alpha(Theme.RAISED, alpha));
            c.fill(x, y - h + 4, 2, h - 8, Theme.alpha(t.color, alpha));
            c.icon(t.icon, x + 9, y - h + 7, Theme.alpha(t.color, alpha));
            c.textEllipsized(t.text, x + 24, y - h + 7, w - 30, Theme.alpha(Theme.TEXT, alpha));
            y -= h + 6;
        }
    }

    // ---------------------------------------------------------------- overlays

    public Overlay openOverlay(Widget widget, boolean modal, boolean closeOnOutside, Runnable onClose) {
        Overlay o = new Overlay(widget, modal, closeOnOutside, onClose);
        overlays.add(o);
        if (widget instanceof OverlayLayout ol) ol.layoutOverlay(vw, vh);
        else widget.layout();
        return o;
    }

    public void closeOverlay(Widget widget) {
        for (Iterator<Overlay> it = overlays.iterator(); it.hasNext(); ) {
            Overlay o = it.next();
            if (o.widget == widget) {
                it.remove();
                if (focused != null && widget.isAncestorOf(focused)) focused = null;
                if (o.onClose != null) o.onClose.run();
                return;
            }
        }
    }

    public boolean isOverlayOpen(Widget widget) {
        for (Overlay o : overlays) if (o.widget == widget) return true;
        return false;
    }

    public boolean hasModal() {
        for (Overlay o : overlays) if (o.modal) return true;
        return false;
    }

    public void closeAllPopups() {
        for (Overlay o : new ArrayList<>(overlays)) if (!o.modal) closeOverlay(o.widget);
    }

    void onRemoved(Widget w) {
        if (focused != null && w.isAncestorOf(focused)) setFocus(null);
        if (hovered != null && w.isAncestorOf(hovered)) hovered = null;
        if (pressed != null && w.isAncestorOf(pressed)) pressed = null;
    }

    // ------------------------------------------------------------------- focus

    public Widget hovered() {
        return hovered;
    }

    public Widget focused() {
        return focused;
    }

    public Widget pressed() {
        return pressed;
    }

    public int mouseX() {
        return mouseVX;
    }

    public int mouseY() {
        return mouseVY;
    }

    public void setFocus(Widget w) {
        if (w == focused) return;
        Widget old = focused;
        focused = w;
        if (old != null) old.focusLost();
        if (w != null) w.focusGained();
    }

    // ------------------------------------------------------------------- input

    private double vx(double mx) {
        return mx / factor;
    }

    private double vy(double my) {
        return my / factor;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = vx(mouseX), my = vy(mouseY);
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 350 && Math.abs(mx - lastClickX) < 4 && Math.abs(my - lastClickY) < 4) clickCount++;
        else clickCount = 1;
        lastClickTime = now;
        lastClickX = mx;
        lastClickY = my;

        // overlays from the top
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Overlay o = overlays.get(i);
            Widget hit = o.widget.hitTest(mx, my);
            if (hit != null) {
                if (o.widget instanceof RaiseOnClick) raiseOverlay(o.widget);
                return dispatchPress(hit, mx, my, button);
            }
            if (o.owner != null && o.owner.contains(mx, my)) {
                closeOverlay(o.widget);
                return true;
            }
            if (o.closeOnOutside) {
                closeOverlay(o.widget);
                if (o.modal) return true;
                continue;
            }
            if (o.modal) return true;
        }
        Widget hit = root.hitTest(mx, my);
        if (hit == null) {
            setFocus(null);
            return false;
        }
        return dispatchPress(hit, mx, my, button);
    }

    /** Number of quick consecutive clicks (1 = single, 2 = double, 3 = triple). */
    public int clickCount() {
        return clickCount;
    }

    private boolean dispatchPress(Widget hit, double mx, double my, int button) {
        Widget focusTarget = hit;
        while (focusTarget != null && !focusTarget.focusable) focusTarget = focusTarget.parent;
        if (focusTarget != null) setFocus(focusTarget);
        else if (focused != null && !focused.isAncestorOf(hit) && root.isAncestorOf(hit)) setFocus(null);
        for (Widget w = hit; w != null; w = w.parent) {
            if (!w.enabled) continue;
            boolean consumed = clickCount >= 2 && w.mouseDoubleClicked(mx, my, button);
            if (!consumed) consumed = w.mousePressed(mx, my, button);
            if (consumed) {
                pressed = w;
                pressedButton = button;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double mx = vx(mouseX), my = vy(mouseY);
        Widget p = pressed;
        if (p != null && button == pressedButton) {
            pressed = null;
            pressedButton = -1;
            p.mouseReleased(mx, my, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed != null && button == pressedButton) {
            return pressed.mouseDragged(vx(mouseX), vy(mouseY), button, dragX / factor, dragY / factor);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double mx = vx(mouseX), my = vy(mouseY);
        Widget hit = hitTestAll(mx, my);
        for (Widget w = hit; w != null; w = w.parent) {
            if (w.mouseScrolled(mx, my, delta)) return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (Widget w = focused; w != null; w = w.parent) {
            if (w.enabled && w.keyPressed(key, scan, mods)) return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !overlays.isEmpty()) {
            Overlay top = overlays.get(overlays.size() - 1);
            closeOverlay(top.widget);
            return true;
        }
        return onShortcut(key, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        for (Widget w = focused; w != null; w = w.parent) {
            if (w.enabled && w.charTyped(ch, mods)) return true;
        }
        return false;
    }

    /** Screen-wide keyboard shortcuts, called when no focused widget consumed the key. */
    protected abstract boolean onShortcut(int key, int mods);

    // ---------------------------------------------------------------- helpers

    public static boolean ctrl(int mods) {
        return (mods & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public static boolean shift(int mods) {
        return (mods & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public static boolean alt(int mods) {
        return (mods & GLFW.GLFW_MOD_ALT) != 0;
    }

    public static String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    public static void setClipboard(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
    }
}
