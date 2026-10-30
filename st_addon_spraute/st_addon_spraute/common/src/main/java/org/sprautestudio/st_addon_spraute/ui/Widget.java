package org.sprautestudio.st_addon_spraute.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base element of the Spraute UI. Bounds are absolute virtual pixels and are assigned by the
 * parent's {@link #layout()}. Mouse/key handlers return true when the event was consumed;
 * unconsumed events bubble to the parent.
 */
public class Widget {
    public int x, y, w, h;
    /** Preferred size used by layout containers (-1 = fill/auto). */
    public int prefW = -1, prefH = -1;
    /** Share of remaining space in a {@link Box} (0 = use preferred size). */
    public float flex;
    public boolean visible = true;
    public boolean enabled = true;
    public boolean focusable;
    public boolean clipChildren = true;
    protected Widget parent;
    protected final List<Widget> children = new ArrayList<>();
    private Supplier<String> tooltip;
    /** 0..1 hover animation, advanced while rendering. */
    protected float hoverAnim;

    // ------------------------------------------------------------- hierarchy

    public <T extends Widget> T add(T child) {
        if (child.parent != null) child.parent.remove(child);
        child.parent = this;
        children.add(child);
        return child;
    }

    public <T extends Widget> T add(int index, T child) {
        if (child.parent != null) child.parent.remove(child);
        child.parent = this;
        children.add(Math.max(0, Math.min(index, children.size())), child);
        return child;
    }

    public void remove(Widget child) {
        if (children.remove(child)) {
            child.parent = null;
            UI ui = UI.get();
            if (ui != null) ui.onRemoved(child);
        }
    }

    public void clear() {
        for (Widget c : new ArrayList<>(children)) remove(c);
    }

    public List<Widget> children() {
        return Collections.unmodifiableList(children);
    }

    public Widget parent() {
        return parent;
    }

    public boolean isAncestorOf(Widget w) {
        for (Widget p = w; p != null; p = p.parent) if (p == this) return true;
        return false;
    }

    public boolean isShown() {
        for (Widget p = this; p != null; p = p.parent) if (!p.visible) return false;
        return true;
    }

    // ---------------------------------------------------------------- bounds

    public Widget bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
        return this;
    }

    public Widget size(int w, int h) {
        this.prefW = w;
        this.prefH = h;
        return this;
    }

    public Widget flex(float f) {
        this.flex = f;
        return this;
    }

    public Widget tooltip(String text) {
        this.tooltip = text == null ? null : () -> text;
        return this;
    }

    public Widget tooltip(Supplier<String> text) {
        this.tooltip = text;
        return this;
    }

    public String tooltipText(int mx, int my) {
        return tooltip == null ? null : tooltip.get();
    }

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Deepest visible widget under the point. */
    public Widget hitTest(double mx, double my) {
        if (!visible || !contains(mx, my)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget hit = children.get(i).hitTest(mx, my);
            if (hit != null) return hit;
        }
        return this;
    }

    // ---------------------------------------------------------------- layout

    /** Positions children inside this widget's bounds. Default: children keep their bounds. */
    public void layout() {
        for (Widget c : children) c.layout();
    }

    // ---------------------------------------------------------------- render

    public void render(Canvas c, int mx, int my, float dt) {
        if (!visible) return;
        boolean hovered = isHovered();
        hoverAnim += (hovered ? 1 : -1) * dt * 8f;
        hoverAnim = Math.max(0, Math.min(1, hoverAnim));
        paint(c, mx, my, dt);
        if (!children.isEmpty()) {
            if (clipChildren) c.pushClip(x, y, w, h);
            for (Widget child : new ArrayList<>(children)) child.render(c, mx, my, dt);
            if (clipChildren) c.popClip();
        }
        paintOver(c, mx, my, dt);
    }

    /** Draws this widget (before children). */
    protected void paint(Canvas c, int mx, int my, float dt) {
    }

    /** Draws on top of the children (borders, scrollbars...). */
    protected void paintOver(Canvas c, int mx, int my, float dt) {
    }

    // ---------------------------------------------------------------- state

    public boolean isHovered() {
        UI ui = UI.get();
        return ui != null && ui.hovered() == this;
    }

    /** Hovered by the mouse, including when a child is hovered. */
    public boolean isHoveredDeep() {
        UI ui = UI.get();
        return ui != null && ui.hovered() != null && isAncestorOf(ui.hovered());
    }

    public boolean isFocused() {
        UI ui = UI.get();
        return ui != null && ui.focused() == this;
    }

    public boolean hasFocusWithin() {
        UI ui = UI.get();
        return ui != null && ui.focused() != null && isAncestorOf(ui.focused());
    }

    public void requestFocus() {
        UI ui = UI.get();
        if (ui != null) ui.setFocus(this);
    }

    public boolean isPressed() {
        UI ui = UI.get();
        return ui != null && ui.pressed() == this;
    }

    // ---------------------------------------------------------------- events

    public boolean mousePressed(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return false;
    }

    public boolean mouseDoubleClicked(double mx, double my, int button) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    public boolean charTyped(char ch, int mods) {
        return false;
    }

    public void focusGained() {
    }

    public void focusLost() {
    }

    public void tick() {
        for (Widget c : new ArrayList<>(children)) c.tick();
    }
}
