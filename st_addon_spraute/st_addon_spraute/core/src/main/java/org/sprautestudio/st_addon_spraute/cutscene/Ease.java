package org.sprautestudio.st_addon_spraute.cutscene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Easing curves for camera moves: {@code apply("ease_in_out_sine", t)} maps 0..1 to 0..1. */
public final class Ease {
    private Ease() {}

    /** Name -> Russian title, in menu order. */
    public static final Map<String, String> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put("linear", "равномерно");
        TITLES.put("smooth", "плавно (по умолчанию)");
        for (String f : List.of("sine", "quad", "cubic", "quart", "expo", "circ", "back", "elastic", "bounce")) {
            String ru = switch (f) {
                case "sine" -> "мягко";
                case "quad" -> "умеренно";
                case "cubic" -> "заметно";
                case "quart" -> "сильно";
                case "expo" -> "резко";
                case "circ" -> "по дуге";
                case "back" -> "с отскоком назад";
                case "elastic" -> "пружина";
                default -> "прыжки";
            };
            TITLES.put("ease_in_" + f, "разгон: " + ru);
            TITLES.put("ease_out_" + f, "торможение: " + ru);
            TITLES.put("ease_in_out_" + f, "разгон и торможение: " + ru);
        }
    }

    public static List<String> names() {
        return List.copyOf(TITLES.keySet());
    }

    public static boolean known(String name) {
        return name != null && TITLES.containsKey(name.toLowerCase());
    }

    public static double apply(String name, double t) {
        t = Math.max(0, Math.min(1, t));
        if (name == null) name = "smooth";
        name = name.toLowerCase();
        if (name.equals("linear")) return t;
        if (name.equals("smooth")) return t * t * (3 - 2 * t);
        String kind = name.startsWith("ease_in_out_") ? "inout" : name.startsWith("ease_in_") ? "in" : name.startsWith("ease_out_") ? "out" : "inout";
        String f = name.substring(name.lastIndexOf('_') + 1);
        return switch (kind) {
            case "in" -> in(f, t);
            case "out" -> 1 - in(f, 1 - t);
            default -> t < 0.5 ? in(f, 2 * t) / 2 : 1 - in(f, 2 - 2 * t) / 2;
        };
    }

    /** The ease-in form of a curve. */
    private static double in(String f, double t) {
        return switch (f) {
            case "sine" -> 1 - Math.cos(t * Math.PI / 2);
            case "quad" -> t * t;
            case "cubic" -> t * t * t;
            case "quart" -> t * t * t * t;
            case "expo" -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case "circ" -> 1 - Math.sqrt(1 - t * t);
            case "back" -> 2.70158 * t * t * t - 1.70158 * t * t;
            case "elastic" -> t == 0 || t == 1 ? t : -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI) / 3);
            case "bounce" -> 1 - bounceOut(1 - t);
            default -> t * t * (3 - 2 * t);
        };
    }

    private static double bounceOut(double t) {
        double n = 7.5625, d = 2.75;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75;
        if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375;
        return n * (t -= 2.625 / d) * t + 0.984375;
    }
}
