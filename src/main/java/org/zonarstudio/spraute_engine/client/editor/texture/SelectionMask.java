package org.zonarstudio.spraute_engine.client.editor.texture;

import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.IntRect;
import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.PixelClip;

/**
 * Pixel selection mask with Photoshop-style combine modes.
 */
public final class SelectionMask implements PixelClip {
    public enum Mode { REPLACE, ADD, SUBTRACT, INTERSECT }

    public final int w;
    public final int h;
    private final boolean[] bits;
    private int count;
    private int minX, minY, maxX, maxY;
    private boolean boundsDirty = true;

    public SelectionMask(int w, int h) {
        this.w = Math.max(1, w);
        this.h = Math.max(1, h);
        this.bits = new boolean[this.w * this.h];
    }

    @Override
    public boolean test(int x, int y) {
        return contains(x, y);
    }

    public boolean contains(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        return bits[y * w + x];
    }

    public boolean isEmpty() {
        return count <= 0;
    }

    public void clear() {
        if (count == 0) return;
        java.util.Arrays.fill(bits, false);
        count = 0;
        boundsDirty = true;
    }

    public void applyBits(boolean[] src, Mode mode) {
        if (src == null || src.length != bits.length) return;
        Mode m = mode == null ? Mode.REPLACE : mode;
        switch (m) {
            case REPLACE -> {
                count = 0;
                for (int i = 0; i < bits.length; i++) {
                    bits[i] = src[i];
                    if (src[i]) count++;
                }
                boundsDirty = true;
            }
            case ADD -> {
                for (int i = 0; i < bits.length; i++) {
                    if (src[i]) set(i % w, i / w, true);
                }
            }
            case SUBTRACT -> {
                for (int i = 0; i < bits.length; i++) {
                    if (src[i]) set(i % w, i / w, false);
                }
            }
            case INTERSECT -> {
                for (int i = 0; i < bits.length; i++) {
                    if (bits[i] && !src[i]) set(i % w, i / w, false);
                }
            }
        }
    }

    public void applyRect(IntRect rect, Mode mode) {
        if (rect == null || rect.isEmpty()) return;
        IntRect r = rect.clipped(w, h);
        if (r.isEmpty()) {
            if (mode == Mode.REPLACE || mode == Mode.INTERSECT) clear();
            return;
        }
        Mode m = mode == null ? Mode.REPLACE : mode;
        switch (m) {
            case REPLACE -> {
                clear();
                fillRect(r, true);
            }
            case ADD -> fillRect(r, true);
            case SUBTRACT -> fillRect(r, false);
            case INTERSECT -> {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (bits[y * w + x] && !r.contains(x, y)) set(x, y, false);
                    }
                }
            }
        }
    }

    public void offset(int dx, int dy) {
        if (dx == 0 && dy == 0 || isEmpty()) return;
        boolean[] next = new boolean[bits.length];
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!bits[y * w + x]) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                next[ny * w + nx] = true;
                n++;
            }
        }
        System.arraycopy(next, 0, bits, 0, bits.length);
        count = n;
        boundsDirty = true;
    }

    public IntRect bounds() {
        if (isEmpty()) return null;
        if (boundsDirty) recomputeBounds();
        return new IntRect(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public boolean hits(int px, int py, int pad) {
        if (isEmpty()) return false;
        if (contains(px, py)) return true;
        int p = Math.max(0, pad);
        if (p == 0) return false;
        for (int dy = -p; dy <= p; dy++) {
            for (int dx = -p; dx <= p; dx++) {
                if (contains(px + dx, py + dy)) return true;
            }
        }
        return false;
    }

    public boolean[] extract(IntRect rect) {
        if (rect == null || rect.isEmpty()) return new boolean[0];
        boolean[] out = new boolean[rect.w * rect.h];
        for (int y = 0; y < rect.h; y++) {
            for (int x = 0; x < rect.w; x++) {
                out[y * rect.w + x] = contains(rect.x + x, rect.y + y);
            }
        }
        return out;
    }

    public void replaceWith(int destX, int destY, int srcW, int srcH, boolean[] srcMask) {
        clear();
        if (srcW <= 0 || srcH <= 0) return;
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int i = y * srcW + x;
                boolean on = srcMask == null || (i < srcMask.length && srcMask[i]);
                if (on) set(destX + x, destY + y, true);
            }
        }
    }

    public SelectionMask copy() {
        SelectionMask m = new SelectionMask(w, h);
        System.arraycopy(bits, 0, m.bits, 0, bits.length);
        m.count = count;
        m.minX = minX;
        m.minY = minY;
        m.maxX = maxX;
        m.maxY = maxY;
        m.boundsDirty = boundsDirty;
        return m;
    }

    private void fillRect(IntRect r, boolean value) {
        for (int y = r.y; y < r.y + r.h; y++) {
            for (int x = r.x; x < r.x + r.w; x++) {
                set(x, y, value);
            }
        }
    }

    private void set(int x, int y, boolean value) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        if (bits[i] == value) return;
        bits[i] = value;
        count += value ? 1 : -1;
        boundsDirty = true;
    }

    private void recomputeBounds() {
        boundsDirty = false;
        if (count <= 0) {
            minX = minY = 0;
            maxX = maxY = -1;
            return;
        }
        minX = w;
        minY = h;
        maxX = -1;
        maxY = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (!bits[row + x]) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
    }
}
