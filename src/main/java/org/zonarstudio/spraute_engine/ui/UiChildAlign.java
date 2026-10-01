package org.zonarstudio.spraute_engine.ui;

import java.util.Locale;

/**
 * Child / pack alignment inside a UI container (top-left … bottom-right).
 * {@code top_left} keeps legacy origin at the parent's top-left corner.
 */
public final class UiChildAlign {
    private UiChildAlign() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "top_left";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (s) {
            case "topleft", "tl", "northwest", "nw", "left_top" -> "top_left";
            case "top", "top_center", "topcentre", "north", "n", "center_top", "centre_top" -> "top";
            case "topright", "tr", "northeast", "ne", "right_top" -> "top_right";
            case "left", "center_left", "centre_left", "middle_left", "west", "w" -> "left";
            case "center", "centre", "middle", "mid" -> "center";
            case "right", "center_right", "centre_right", "middle_right", "east", "e" -> "right";
            case "bottomleft", "bl", "southwest", "sw", "left_bottom" -> "bottom_left";
            case "bottom", "bottom_center", "bottomcentre", "south", "s",
                 "center_bottom", "centre_bottom" -> "bottom";
            case "bottomright", "br", "southeast", "se", "right_bottom" -> "bottom_right";
            case "top_left", "top_right", "bottom_left", "bottom_right" -> s;
            default -> "top_left";
        };
    }

    public static boolean isOrigin(String align) {
        return "top_left".equals(normalize(align));
    }

    /** Horizontal factor 0 = left, 0.5 = center, 1 = right. */
    public static float axisX(String align) {
        return switch (normalize(align)) {
            case "top_right", "right", "bottom_right" -> 1f;
            case "top", "center", "bottom" -> 0.5f;
            default -> 0f;
        };
    }

    /** Vertical factor 0 = top, 0.5 = center, 1 = bottom. */
    public static float axisY(String align) {
        return switch (normalize(align)) {
            case "bottom_left", "bottom", "bottom_right" -> 1f;
            case "left", "center", "right" -> 0.5f;
            default -> 0f;
        };
    }

    /** Offset of a child of size (childW, childH) inside a box (boxW, boxH). */
    public static float offsetX(String align, float boxW, float childW) {
        return (boxW - childW) * axisX(align);
    }

    public static float offsetY(String align, float boxH, float childH) {
        return (boxH - childH) * axisY(align);
    }

    public static final String[] OPTIONS = {
            "top_left", "top", "top_right",
            "left", "center", "right",
            "bottom_left", "bottom", "bottom_right"
    };
}
