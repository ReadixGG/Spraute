package org.zonarstudio.spraute_engine.script;

/** RGB/ARGB color parsing for scripts and UI (0xRRGGBB, #RRGGBB, decimal). */
public final class SprauteColors {
    private SprauteColors() {}

    public static int toRgbInt(Object raw) {
        return toArgbInt(raw, 0) & 0xFFFFFF;
    }

    public static int parseString(String raw) {
        return toArgbInt(raw, 0) & 0xFFFFFF;
    }

    /**
     * Packed ARGB. Visual-block pickers store {@code 0xRRGGBB}; UI JSON may also
     * get that as a quoted string. Six-digit values get opaque alpha.
     */
    public static int toArgbInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) {
            long v = n.longValue() & 0xFFFFFFFFL;
            if (v <= 0xFFFFFFL) return 0xFF000000 | (int) v;
            return (int) v;
        }
        String t = String.valueOf(raw).trim();
        if (t.isEmpty()) return fallback;
        boolean hexHint = false;
        if (t.startsWith("#")) {
            t = t.substring(1);
            hexHint = true;
        } else if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
            hexHint = true;
        }
        try {
            if (t.indexOf('.') >= 0) {
                long v = (long) Double.parseDouble(t) & 0xFFFFFFFFL;
                if (v <= 0xFFFFFFL) return 0xFF000000 | (int) v;
                return (int) v;
            }
            boolean hex = hexHint || t.matches(".*[a-fA-F].*") || t.length() == 3 || t.length() == 6;
            if (hex) {
                if (t.length() == 3) {
                    int r = expandNibble(t.charAt(0));
                    int g = expandNibble(t.charAt(1));
                    int b = expandNibble(t.charAt(2));
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                if (t.length() == 4) {
                    int a = expandNibble(t.charAt(0));
                    int r = expandNibble(t.charAt(1));
                    int g = expandNibble(t.charAt(2));
                    int b = expandNibble(t.charAt(3));
                    return (a << 24) | (r << 16) | (g << 8) | b;
                }
                if (t.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(t, 16);
                if (t.length() == 8) return (int) Long.parseLong(t, 16);
                return (int) (Long.parseLong(t, 16) & 0xFFFFFFFFL);
            }
            long v = Long.parseLong(t, 10) & 0xFFFFFFFFL;
            if (v <= 0xFFFFFFL) return 0xFF000000 | (int) v;
            return (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int expandNibble(char c) {
        int n = Character.digit(c, 16);
        if (n < 0) throw new NumberFormatException();
        return (n << 4) | n;
    }
}
