package org.zonarstudio.spraute_engine.scene.custom;

import org.zonarstudio.spraute_engine.script.LerpMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animation-curve value for custom node fields ({@code field x curve { }}).
 * Stored in {@code customData} as a list of {@code {t, v, ease}} maps.
 * Scripts: {@code _node.fade.eval(t)} or {@code curveEval(_node.fade, t)}.
 */
public final class ScriptCurve {
    public final List<Map<String, Object>> keys;

    public ScriptCurve(Object raw) {
        this.keys = normalize(raw);
    }

    public static ScriptCurve wrap(Object raw) {
        if (raw instanceof ScriptCurve c) return c;
        return new ScriptCurve(raw);
    }

    public List<Map<String, Object>> toStorage() {
        return copy(keys);
    }

    public double eval(double t) {
        return eval(keys, t);
    }

    public static List<Map<String, Object>> identity() {
        return fromPreset("linear");
    }

    public static List<Map<String, Object>> fromPreset(String preset) {
        String ease = normalizeEase(preset);
        List<Map<String, Object>> keys = new ArrayList<>(2);
        keys.add(key(0, 0, ease));
        keys.add(key(1, 1, "linear"));
        return keys;
    }

    public static List<Map<String, Object>> normalize(Object raw) {
        List<Map<String, Object>> keys = parseKeys(raw);
        if (keys.size() < 2) return identity();
        keys.sort((a, b) -> Double.compare(num(a, "t", 0), num(b, "t", 0)));
        for (Map<String, Object> k : keys) {
            double t = clamp01(num(k, "t", 0));
            k.put("t", t);
            k.put("v", num(k, "v", 0));
            k.put("ease", normalizeEase(str(k, "ease", "linear")));
        }
        return keys;
    }

    public static List<Map<String, Object>> copy(List<Map<String, Object>> src) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (src == null) return identity();
        for (Map<String, Object> k : src) {
            out.add(copyKey(k));
        }
        return out.isEmpty() ? identity() : normalize(out);
    }

    public static boolean looksLike(Object raw) {
        if (raw instanceof ScriptCurve) return true;
        List<Map<String, Object>> keys = parseKeys(raw);
        if (keys.size() < 2) return false;
        Map<String, Object> first = keys.get(0);
        return first.containsKey("t") && first.containsKey("v");
    }

    public static boolean same(Object a, Object b) {
        List<Map<String, Object>> ka = normalize(a);
        List<Map<String, Object>> kb = normalize(b);
        if (ka.size() != kb.size()) return false;
        for (int i = 0; i < ka.size(); i++) {
            if (Math.abs(num(ka.get(i), "t", 0) - num(kb.get(i), "t", 0)) > 1e-5) return false;
            if (Math.abs(num(ka.get(i), "v", 0) - num(kb.get(i), "v", 0)) > 1e-5) return false;
            if (!normalizeEase(str(ka.get(i), "ease", "linear"))
                    .equals(normalizeEase(str(kb.get(i), "ease", "linear")))) return false;
        }
        return true;
    }

    public static double eval(Object raw, double t) {
        return eval(normalize(raw), t);
    }

    public static Object unwrap(Object value) {
        if (value instanceof ScriptCurve c) return c.toStorage();
        if (looksLike(value)) return normalize(value);
        return value;
    }

    /** Sentinel: this object is not a curve method call. */
    public static final Object UNHANDLED = new Object();

    /**
     * {@code curve.eval(t)} / {@code .keys}. Unknown methods on a {@link ScriptCurve} yield {@code null};
     * a raw list that only looks like a curve is left to list methods.
     */
    public static Object invoke(Object raw, String method, List<?> args) {
        String cm = method == null ? "" : method.toLowerCase(Locale.ROOT);
        boolean isEval = cm.equals("eval") || cm.equals("evaluate") || cm.equals("sample") || cm.equals("at");
        boolean keysMethod = cm.equals("keys");
        if (!isEval && !keysMethod) {
            return raw instanceof ScriptCurve ? null : UNHANDLED;
        }
        if (!(raw instanceof ScriptCurve) && !looksLike(raw)) return UNHANDLED;
        ScriptCurve curve = wrap(raw);
        if (keysMethod) return curve.keys;
        double t = 0;
        if (args != null && !args.isEmpty() && args.get(0) != null) {
            Object a = args.get(0);
            if (a instanceof Number n) t = n.doubleValue();
            else {
                try { t = Double.parseDouble(String.valueOf(a).trim()); } catch (Exception ignored) {}
            }
        }
        return curve.eval(t);
    }

    public static List<Map<String, Object>> fromDefault(Object defaultValue) {
        if (defaultValue instanceof String s && !s.isBlank()) return fromPreset(s);
        if (looksLike(defaultValue)) return normalize(defaultValue);
        return identity();
    }

    private static double eval(List<Map<String, Object>> keys, double t) {
        if (keys == null || keys.isEmpty()) return 0;
        if (keys.size() == 1) return num(keys.get(0), "v", 0);
        Map<String, Object> first = keys.get(0);
        Map<String, Object> last = keys.get(keys.size() - 1);
        double t0 = num(first, "t", 0);
        double t1 = num(last, "t", 1);
        if (t <= t0) return num(first, "v", 0);
        if (t >= t1) return num(last, "v", 1);
        for (int i = 0; i < keys.size() - 1; i++) {
            Map<String, Object> a = keys.get(i);
            Map<String, Object> b = keys.get(i + 1);
            double ta = num(a, "t", 0);
            double tb = num(b, "t", 1);
            if (t > tb) continue;
            double span = tb - ta;
            double u = span <= 1e-9 ? 1 : (t - ta) / span;
            u = LerpMath.ease(u, str(a, "ease", "linear"));
            return LerpMath.lerp(num(a, "v", 0), num(b, "v", 1), u);
        }
        return num(last, "v", 1);
    }

    private static List<Map<String, Object>> parseKeys(Object raw) {
        List<Map<String, Object>> keys = new ArrayList<>();
        if (raw instanceof ScriptCurve c) {
            for (Map<String, Object> k : c.keys) keys.add(copyKey(k));
            return keys;
        }
        if (!(raw instanceof List<?> list)) return keys;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var e : m.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                keys.add(copy);
            }
        }
        return keys;
    }

    public static Map<String, Object> key(double t, double v, String ease) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("t", t);
        k.put("v", v);
        k.put("ease", normalizeEase(ease));
        return k;
    }

    private static Map<String, Object> copyKey(Map<String, Object> src) {
        Map<String, Object> k = new LinkedHashMap<>();
        if (src == null) return key(0, 0, "linear");
        k.put("t", num(src, "t", 0));
        k.put("v", num(src, "v", 0));
        k.put("ease", normalizeEase(str(src, "ease", "linear")));
        return k;
    }

    public static double num(Map<String, Object> k, String key, double fallback) {
        if (k == null) return fallback;
        Object v = k.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String str(Map<String, Object> k, String key, String fallback) {
        if (k == null || k.get(key) == null) return fallback;
        String s = String.valueOf(k.get(key)).trim();
        return s.isEmpty() ? fallback : s;
    }

    public static String normalizeEase(String raw) {
        if (raw == null || raw.isBlank()) return "linear";
        String k = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (k) {
            case "easein", "ease_in", "in" -> "easeIn";
            case "easeout", "ease_out", "out" -> "easeOut";
            case "easeinout", "ease_in_out", "smooth", "плавно" -> "easeInOut";
            case "bounceout", "bounce_out", "bounce", "отскок" -> "bounceOut";
            case "elasticout", "elastic_out", "elastic", "пружина" -> "elasticOut";
            default -> "linear";
        };
    }

    private static double clamp01(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t;
    }
}
