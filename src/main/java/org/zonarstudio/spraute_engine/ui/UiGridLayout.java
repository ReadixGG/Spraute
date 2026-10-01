package org.zonarstudio.spraute_engine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compile-time layout for UI_GRID / create ui {@code grid}/{@code row}/{@code column}.
 * Modes: {@code grid} (fixed cells), {@code row} / {@code column} (flow; optional hug to child sizes).
 */
public final class UiGridLayout {
    private UiGridLayout() {}

    public record Item(int w, int h) {
        public Item {
            w = Math.max(1, w);
            h = Math.max(1, h);
        }
    }

    /** Position relative to the grid origin; cellW/H is the slot size used for stretch / child parent box. */
    public record Placement(int index, float x, float y, int cellW, int cellH) {}

    public static String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) return "grid";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "row", "horizontal", "h", "hbox" -> "row";
            case "column", "col", "vertical", "v", "vbox" -> "column";
            default -> "grid";
        };
    }

    public static String normalizeFlow(String raw) {
        if (raw == null || raw.isBlank()) return "ltr";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "rtl", "right", "right_to_left", "right-to-left", "reverse" -> "rtl";
            case "btt", "bottom", "bottom_to_top", "bottom-to-top", "up" -> "btt";
            case "ttb", "top", "top_to_bottom", "top-to-bottom", "down" -> "ttb";
            default -> "ltr";
        };
    }

    public static List<Placement> compute(
            String mode, String align, String flow,
            boolean hug, boolean stretch,
            int columns, int cellW, int cellH, int gapX, int gapY,
            int boxW, int boxH,
            List<Item> items) {
        if (items == null || items.isEmpty()) return List.of();
        String m = normalizeMode(mode);
        return switch (m) {
            case "row" -> layoutRow(align, flow, hug, stretch, cellW, cellH, gapX, boxW, boxH, items);
            case "column" -> layoutColumn(align, flow, hug, stretch, cellW, cellH, gapY, boxW, boxH, items);
            default -> layoutGrid(align, hug, stretch, columns, cellW, cellH, gapX, gapY, boxW, boxH, items);
        };
    }

    /** Content size of a computed layout (ignores pack align inside the box). */
    public static int[] contentSize(
            String mode, boolean hug,
            int columns, int cellW, int cellH, int gapX, int gapY,
            List<Item> items) {
        if (items == null || items.isEmpty()) {
            return new int[]{Math.max(1, cellW), Math.max(1, cellH)};
        }
        String m = normalizeMode(mode);
        return switch (m) {
            case "row" -> {
                int tw = 0;
                int th = 1;
                for (int i = 0; i < items.size(); i++) {
                    Item it = items.get(i);
                    int w = hug ? it.w() : Math.max(1, cellW);
                    int h = hug ? it.h() : Math.max(1, cellH);
                    tw += w;
                    if (i > 0) tw += Math.max(0, gapX);
                    th = Math.max(th, h);
                }
                yield new int[]{Math.max(1, tw), Math.max(1, th)};
            }
            case "column" -> {
                int tw = 1;
                int th = 0;
                for (int i = 0; i < items.size(); i++) {
                    Item it = items.get(i);
                    int w = hug ? it.w() : Math.max(1, cellW);
                    int h = hug ? it.h() : Math.max(1, cellH);
                    th += h;
                    if (i > 0) th += Math.max(0, gapY);
                    tw = Math.max(tw, w);
                }
                yield new int[]{Math.max(1, tw), Math.max(1, th)};
            }
            default -> {
                int cols = Math.max(1, columns);
                int rows = Math.max(1, (items.size() + cols - 1) / cols);
                int cw = Math.max(1, cellW);
                int ch = Math.max(1, cellH);
                if (hug) {
                    cw = 1;
                    ch = 1;
                    for (Item it : items) {
                        cw = Math.max(cw, it.w());
                        ch = Math.max(ch, it.h());
                    }
                }
                int tw = cols * cw + Math.max(0, cols - 1) * Math.max(0, gapX);
                int th = rows * ch + Math.max(0, rows - 1) * Math.max(0, gapY);
                yield new int[]{Math.max(1, tw), Math.max(1, th)};
            }
        };
    }

    private static List<Placement> layoutRow(
            String align, String flow, boolean hug, boolean stretch,
            int cellW, int cellH, int gapX, int boxW, int boxH, List<Item> items) {
        int n = items.size();
        int[] widths = new int[n];
        int[] heights = new int[n];
        int totalW = 0;
        int rowH = 1;
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            widths[i] = hug ? it.w() : Math.max(1, cellW);
            heights[i] = hug ? it.h() : Math.max(1, cellH);
            totalW += widths[i];
            if (i > 0) totalW += Math.max(0, gapX);
            rowH = Math.max(rowH, heights[i]);
        }
        if (stretch) {
            for (int i = 0; i < n; i++) heights[i] = rowH;
        }
        float packX = UiChildAlign.offsetX(align, boxW, totalW);
        float packY = UiChildAlign.offsetY(align, boxH, rowH);
        boolean rtl = "rtl".equals(normalizeFlow(flow));
        List<Placement> out = new ArrayList<>(n);
        if (rtl) {
            // Document order, first child on the right.
            float x = packX + totalW;
            for (int i = 0; i < n; i++) {
                x -= widths[i];
                int slotH = stretch ? rowH : heights[i];
                float y = packY + (stretch ? 0f : UiChildAlign.offsetY(align, rowH, heights[i]));
                out.add(new Placement(i, x, y, widths[i], slotH));
                x -= Math.max(0, gapX);
            }
            return out;
        }
        float x = packX;
        for (int i = 0; i < n; i++) {
            int slotH = stretch ? rowH : heights[i];
            float y = packY + (stretch ? 0f : UiChildAlign.offsetY(align, rowH, heights[i]));
            out.add(new Placement(i, x, y, widths[i], slotH));
            x += widths[i] + Math.max(0, gapX);
        }
        return out;
    }

    private static List<Placement> layoutColumn(
            String align, String flow, boolean hug, boolean stretch,
            int cellW, int cellH, int gapY, int boxW, int boxH, List<Item> items) {
        int n = items.size();
        int[] widths = new int[n];
        int[] heights = new int[n];
        int totalH = 0;
        int colW = 1;
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            widths[i] = hug ? it.w() : Math.max(1, cellW);
            heights[i] = hug ? it.h() : Math.max(1, cellH);
            totalH += heights[i];
            if (i > 0) totalH += Math.max(0, gapY);
            colW = Math.max(colW, widths[i]);
        }
        if (stretch) {
            for (int i = 0; i < n; i++) widths[i] = colW;
        }
        float packX = UiChildAlign.offsetX(align, boxW, colW);
        float packY = UiChildAlign.offsetY(align, boxH, totalH);
        boolean btt = "btt".equals(normalizeFlow(flow));
        List<Placement> out = new ArrayList<>(n);
        if (btt) {
            // Document order, first child at the bottom.
            float y = packY + totalH;
            for (int i = 0; i < n; i++) {
                y -= heights[i];
                int slotW = stretch ? colW : widths[i];
                float x = packX + (stretch ? 0f : UiChildAlign.offsetX(align, colW, widths[i]));
                out.add(new Placement(i, x, y, slotW, heights[i]));
                y -= Math.max(0, gapY);
            }
            return out;
        }
        float y = packY;
        for (int i = 0; i < n; i++) {
            int slotW = stretch ? colW : widths[i];
            float x = packX + (stretch ? 0f : UiChildAlign.offsetX(align, colW, widths[i]));
            out.add(new Placement(i, x, y, slotW, heights[i]));
            y += heights[i] + Math.max(0, gapY);
        }
        return out;
    }

    private static List<Placement> layoutGrid(
            String align, boolean hug, boolean stretch,
            int columns, int cellW, int cellH, int gapX, int gapY,
            int boxW, int boxH, List<Item> items) {
        int cols = Math.max(1, columns);
        int n = items.size();
        int rows = Math.max(1, (n + cols - 1) / cols);
        int cw = Math.max(1, cellW);
        int ch = Math.max(1, cellH);
        if (hug) {
            cw = 1;
            ch = 1;
            for (Item it : items) {
                cw = Math.max(cw, it.w());
                ch = Math.max(ch, it.h());
            }
        }
        int totalW = cols * cw + Math.max(0, cols - 1) * Math.max(0, gapX);
        int totalH = rows * ch + Math.max(0, rows - 1) * Math.max(0, gapY);
        float packX = UiChildAlign.offsetX(align, boxW, totalW);
        float packY = UiChildAlign.offsetY(align, boxH, totalH);
        List<Placement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int col = i % cols;
            int row = i / cols;
            float cellX = packX + col * (cw + Math.max(0, gapX));
            float cellY = packY + row * (ch + Math.max(0, gapY));
            Item it = items.get(i);
            int childW = hug ? it.w() : (stretch ? cw : it.w());
            int childH = hug ? it.h() : (stretch ? ch : it.h());
            if (stretch) {
                out.add(new Placement(i, cellX, cellY, cw, ch));
            } else {
                float ox = UiChildAlign.offsetX(align, cw, childW);
                float oy = UiChildAlign.offsetY(align, ch, childH);
                out.add(new Placement(i, cellX + ox, cellY + oy, cw, ch));
            }
        }
        return out;
    }
}
