package org.zonarstudio.spraute_engine.script;

import java.util.Locale;

/** Shared lerp / easing helpers for scripts and UI. */
public final class LerpMath {
    private LerpMath() {}

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    /** Shortest-path angle lerp in degrees. */
    public static double lerpAngle(double a, double b, double t) {
        double delta = wrapDegrees(b - a);
        return a + delta * clamp01(t);
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static double clamp01(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t;
    }

    public static double ease(double t, String mode) {
        t = clamp01(t);
        if (mode == null || mode.isBlank()) return t;
        return switch (mode.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "easein", "ease_in" -> t * t;
            case "easeout", "ease_out" -> t * (2 - t);
            case "easeinout", "ease_in_out" -> t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
            case "bounceout", "bounce_out" -> bounceOut(t);
            case "elasticout", "elastic_out" -> elasticOut(t);
            default -> t; // linear
        };
    }

    private static double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        }
        if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        }
        t -= 2.625 / d1;
        return n1 * t * t + 0.984375;
    }

    private static double elasticOut(double t) {
        if (t == 0 || t == 1) return t;
        double c4 = (2 * Math.PI) / 3;
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
    }

    public static boolean looksLikeColor(Object v) {
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return false;
        if (s.charAt(0) == '#') {
            int len = s.length() - 1;
            return len == 3 || len == 4 || len == 6 || len == 8;
        }
        return false;
    }

    public static boolean isAngleProp(String prop) {
        if (prop == null) return false;
        String p = prop.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (p) {
            case "yaw", "pitch", "roll",
                 "rotationx", "rotx",
                 "rotationy", "roty",
                 "rotationz", "rotz" -> true;
            default -> false;
        };
    }

    /** Returns #RRGGBB or #AARRGGBB matching the richer of the two inputs. */
    public static String lerpColor(Object from, Object to, double t) {
        int[] a = parseColor(from);
        int[] b = parseColor(to);
        if (a == null || b == null) return String.valueOf(to);
        t = clamp01(t);
        int aa = (int) Math.round(lerp(a[0], b[0], t));
        int rr = (int) Math.round(lerp(a[1], b[1], t));
        int gg = (int) Math.round(lerp(a[2], b[2], t));
        int bb = (int) Math.round(lerp(a[3], b[3], t));
        boolean withAlpha = hasExplicitAlpha(from) || hasExplicitAlpha(to) || aa != 255;
        if (withAlpha) {
            return String.format(Locale.ROOT, "#%02X%02X%02X%02X", aa, rr, gg, bb);
        }
        return String.format(Locale.ROOT, "#%02X%02X%02X", rr, gg, bb);
    }

    private static boolean hasExplicitAlpha(Object v) {
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        if (!s.startsWith("#")) return false;
        int len = s.length() - 1;
        return len == 4 || len == 8;
    }

    /** ARGB components 0..255, or null if unparseable. */
    public static int[] parseColor(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.charAt(0) == '#') s = s.substring(1);
        try {
            return switch (s.length()) {
                case 3 -> new int[]{
                        255,
                        expandNibble(s.charAt(0)),
                        expandNibble(s.charAt(1)),
                        expandNibble(s.charAt(2))
                };
                case 4 -> new int[]{
                        expandNibble(s.charAt(0)),
                        expandNibble(s.charAt(1)),
                        expandNibble(s.charAt(2)),
                        expandNibble(s.charAt(3))
                };
                case 6 -> new int[]{
                        255,
                        Integer.parseInt(s.substring(0, 2), 16),
                        Integer.parseInt(s.substring(2, 4), 16),
                        Integer.parseInt(s.substring(4, 6), 16)
                };
                case 8 -> new int[]{
                        Integer.parseInt(s.substring(0, 2), 16),
                        Integer.parseInt(s.substring(2, 4), 16),
                        Integer.parseInt(s.substring(4, 6), 16),
                        Integer.parseInt(s.substring(6, 8), 16)
                };
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static int expandNibble(char c) {
        int n = Character.digit(c, 16);
        if (n < 0) throw new IllegalArgumentException();
        return n * 17;
    }
}
