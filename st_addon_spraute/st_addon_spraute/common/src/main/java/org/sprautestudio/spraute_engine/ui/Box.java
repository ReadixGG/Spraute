package org.sprautestudio.spraute_engine.ui;

/**
 * Flex-style container: lays children out in a row or column. Children with {@code flex > 0}
 * share the remaining space, others use their preferred size.
 */
public class Box extends Widget {
    public enum Dir { ROW, COLUMN }
    public enum Align { STRETCH, START, CENTER, END }

    public Dir dir;
    public int gap;
    public int padL, padT, padR, padB;
    public Align align = Align.STRETCH;
    public Align justify = Align.START;
    public int background;
    public int border;
    public int radius;

    public Box(Dir dir) {
        this.dir = dir;
    }

    public static Box row() {
        return new Box(Dir.ROW);
    }

    public static Box column() {
        return new Box(Dir.COLUMN);
    }

    public Box gap(int gap) {
        this.gap = gap;
        return this;
    }

    public Box pad(int all) {
        return pad(all, all, all, all);
    }

    public Box pad(int horizontal, int vertical) {
        return pad(horizontal, vertical, horizontal, vertical);
    }

    public Box pad(int l, int t, int r, int b) {
        padL = l;
        padT = t;
        padR = r;
        padB = b;
        return this;
    }

    public Box align(Align a) {
        this.align = a;
        return this;
    }

    public Box justify(Align a) {
        this.justify = a;
        return this;
    }

    public Box background(int color) {
        this.background = color;
        return this;
    }

    public Box border(int color) {
        this.border = color;
        return this;
    }

    public Box radius(int r) {
        this.radius = r;
        return this;
    }

    @Override
    public void layout() {
        boolean row = dir == Dir.ROW;
        int innerX = x + padL, innerY = y + padT;
        int innerW = w - padL - padR, innerH = h - padT - padB;
        int main = row ? innerW : innerH;
        int fixed = 0;
        float flexSum = 0;
        int count = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            count++;
            if (c.flex > 0) flexSum += c.flex;
            else fixed += Math.max(0, row ? c.prefW : c.prefH);
        }
        if (count == 0) return;
        int gaps = gap * (count - 1);
        int remaining = Math.max(0, main - fixed - gaps);
        int pos = row ? innerX : innerY;
        if (flexSum == 0) {
            int used = fixed + gaps;
            if (justify == Align.CENTER) pos += (main - used) / 2;
            else if (justify == Align.END) pos += main - used;
        }
        float flexAcc = 0;
        int flexUsed = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            int size;
            if (c.flex > 0) {
                flexAcc += c.flex;
                int target = Math.round(remaining * flexAcc / flexSum);
                size = target - flexUsed;
                flexUsed = target;
            } else {
                size = Math.max(0, row ? c.prefW : c.prefH);
            }
            int cross = row ? innerH : innerW;
            int pref = row ? c.prefH : c.prefW;
            int cs = align == Align.STRETCH || pref < 0 ? cross : Math.min(cross, pref);
            int co = switch (align) {
                case CENTER -> (cross - cs) / 2;
                case END -> cross - cs;
                default -> 0;
            };
            if (row) c.bounds(pos, innerY + co, size, cs);
            else c.bounds(innerX + co, pos, cs, size);
            pos += size + gap;
            c.layout();
        }
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (background != 0 || border != 0) {
            if (border != 0) c.roundBorder(x, y, w, h, radius, background, border);
            else c.round(x, y, w, h, radius, background);
        }
    }
}
