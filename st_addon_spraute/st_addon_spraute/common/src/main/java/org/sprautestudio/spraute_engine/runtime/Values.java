package org.sprautestudio.spraute_engine.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lists ({@code [1, 2, 3]}, also points {@code (x, y, z)}) and dictionaries ({@code {name: "Алиса"}})
 * of the language: indexing, properties ({@code .len}, {@code .x}), methods ({@code .add(v)}) and
 * how they print and compare.
 */
public final class Values {
    private Values() {
    }

    private static final Random RND = new Random();

    // ------------------------------------------------------------------ what can be walked through

    /** Items a {@code for} goes through: a list, a dictionary's keys, 0..n-1 of a number, letters of a text. */
    public static List<Object> iterable(Object v) {
        if (v instanceof List<?> l) return new ArrayList<>(l);
        if (v instanceof Map<?, ?> m) return new ArrayList<>(m.keySet());
        if (v instanceof Double d) {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < Math.min(100000, d.intValue()); i++) out.add((double) i);
            return out;
        }
        if (v instanceof String s) {
            List<Object> out = new ArrayList<>();
            s.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp))));
            return out;
        }
        throw new SprauteRuntimeException("for: ожидался список, словарь, число или текст");
    }

    private static int idx(List<?> l, Object i) {
        int n = (int) Math.floor(Eval.num(i));
        if (n < 0) n += l.size();
        return n;
    }

    // ------------------------------------------------------------------ a[i]

    public static Object index(Object c, Object i) {
        if (c instanceof List<?> l) {
            int n = idx(l, i);
            if (n < 0 || n >= l.size()) throw new SprauteRuntimeException("Индекс " + Eval.str(i) + " вне списка (в нём " + l.size() + ")");
            return l.get(n);
        }
        if (c instanceof Map<?, ?> m) {
            Object v = m.get(Eval.str(i));
            return v == null ? "" : v;
        }
        if (c instanceof String s) {
            int n = (int) Math.floor(Eval.num(i));
            if (n < 0) n += s.length();
            return n >= 0 && n < s.length() ? String.valueOf(s.charAt(n)) : "";
        }
        throw new SprauteRuntimeException("[ ] работает только со списками, словарями и текстом");
    }

    @SuppressWarnings("unchecked")
    public static void setIndex(Object c, Object i, Object v) {
        if (c instanceof List<?> l) {
            List<Object> list = (List<Object>) l;
            int n = idx(list, i);
            if (n == list.size()) {
                list.add(v);
                return;
            }
            if (n < 0 || n > list.size()) throw new SprauteRuntimeException("Индекс " + Eval.str(i) + " вне списка (в нём " + list.size() + ")");
            list.set(n, v);
            return;
        }
        if (c instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).put(Eval.str(i), v);
            return;
        }
        throw new SprauteRuntimeException("set x[...]: переменная должна быть списком или словарём (var x = [] или var x = {})");
    }

    // ------------------------------------------------------------------ a.prop and a.method(...)

    public static Object property(Object c, String name) {
        switch (name) {
            case "len", "size", "count", "length" -> {
                if (c instanceof List<?> l) return (double) l.size();
                if (c instanceof Map<?, ?> m) return (double) m.size();
                return (double) Eval.str(c).length();
            }
            case "empty" -> {
                return !Eval.truthy(c);
            }
            case "x", "y", "z" -> {
                if (c instanceof List<?> l) {
                    int i = name.charAt(0) - 'x';
                    if (i < l.size()) return l.get(i);
                    throw new SprauteRuntimeException("." + name + ": в точке нет такой координаты");
                }
                if (c instanceof Map<?, ?> m && m.containsKey(name)) return m.get(name);
                throw new SprauteRuntimeException("." + name + " — у точек (x, y, z)");
            }
            case "first" -> {
                return c instanceof List<?> l && !l.isEmpty() ? l.get(0) : "";
            }
            case "last" -> {
                return c instanceof List<?> l && !l.isEmpty() ? l.get(l.size() - 1) : "";
            }
            case "keys" -> {
                return c instanceof Map<?, ?> m ? new ArrayList<Object>(m.keySet()) : new ArrayList<>();
            }
            case "values" -> {
                return c instanceof Map<?, ?> m ? new ArrayList<Object>(m.values()) : new ArrayList<>();
            }
            default -> {
                if (c instanceof Map<?, ?> m) {
                    Object v = m.get(name);
                    return v == null ? "" : v;
                }
                throw new SprauteRuntimeException("Неизвестное свойство ." + name);
            }
        }
    }

    /** Runs a method; the ones that change the collection change it in place. */
    @SuppressWarnings("unchecked")
    public static Object method(Object c, String name, List<Object> a) {
        if (c instanceof Map<?, ?> mm) {
            Map<String, Object> m = (Map<String, Object>) mm;
            switch (name) {
                case "put", "set" -> {
                    need(name, a, 2);
                    m.put(Eval.str(a.get(0)), a.get(1));
                    return c;
                }
                case "get" -> {
                    need(name, a, 1);
                    Object v = m.get(Eval.str(a.get(0)));
                    return v != null ? v : a.size() > 1 ? a.get(1) : "";
                }
                case "has", "contains" -> {
                    need(name, a, 1);
                    return m.containsKey(Eval.str(a.get(0)));
                }
                case "remove" -> {
                    need(name, a, 1);
                    Object v = m.remove(Eval.str(a.get(0)));
                    return v == null ? "" : v;
                }
                case "clear" -> {
                    m.clear();
                    return c;
                }
                case "keys", "values", "len", "size", "count" -> {
                    return property(c, name);
                }
                default -> throw new SprauteRuntimeException("У словаря нет метода ." + name + "(). Есть: put, get, has, remove, clear, keys, values");
            }
        }
        if (!(c instanceof List<?>)) {
            if (name.equals("has") || name.equals("contains")) return Eval.str(c).toLowerCase().contains(Eval.str(a.isEmpty() ? "" : a.get(0)).toLowerCase());
            if (name.equals("split")) return split(Eval.str(c), a.isEmpty() ? " " : Eval.str(a.get(0)));
            if (name.equals("len") || name.equals("size")) return property(c, name);
            throw new SprauteRuntimeException("." + name + "() работает со списками (var x = [])");
        }
        List<Object> l = (List<Object>) c;
        switch (name) {
            case "add", "push" -> {
                l.addAll(a);
                return c;
            }
            case "insert" -> {
                need(name, a, 2);
                int i = Math.max(0, Math.min(l.size(), idx(l, a.get(0))));
                l.add(i, a.get(1));
                return c;
            }
            case "remove" -> {
                need(name, a, 1);
                for (int i = 0; i < l.size(); i++) {
                    if (Eval.same(l.get(i), a.get(0))) {
                        l.remove(i);
                        return true;
                    }
                }
                return false;
            }
            case "removeat" , "removeAt" -> {
                need(name, a, 1);
                int i = idx(l, a.get(0));
                return i >= 0 && i < l.size() ? l.remove(i) : "";
            }
            case "pop" -> {
                return l.isEmpty() ? "" : l.remove(l.size() - 1);
            }
            case "clear" -> {
                l.clear();
                return c;
            }
            case "get" -> {
                need(name, a, 1);
                int i = idx(l, a.get(0));
                return i >= 0 && i < l.size() ? l.get(i) : a.size() > 1 ? a.get(1) : "";
            }
            case "set" -> {
                need(name, a, 2);
                setIndex(l, a.get(0), a.get(1));
                return c;
            }
            case "has", "contains" -> {
                need(name, a, 1);
                for (Object x : l) if (Eval.same(x, a.get(0))) return true;
                return false;
            }
            case "indexof" , "indexOf" -> {
                need(name, a, 1);
                for (int i = 0; i < l.size(); i++) if (Eval.same(l.get(i), a.get(0))) return (double) i;
                return -1.0;
            }
            case "sort" -> {
                l.sort(Values::compare);
                return c;
            }
            case "reverse" -> {
                Collections.reverse(l);
                return c;
            }
            case "shuffle" -> {
                Collections.shuffle(l, RND);
                return c;
            }
            case "random", "pick" -> {
                return l.isEmpty() ? "" : l.get(RND.nextInt(l.size()));
            }
            case "join" -> {
                return join(l, a.isEmpty() ? ", " : Eval.str(a.get(0)));
            }
            case "len", "size", "count", "first", "last" -> {
                return property(c, name);
            }
            case "sum" -> {
                double s = 0;
                for (Object x : l) s += Eval.num(x);
                return s;
            }
            case "min", "max" -> {
                if (l.isEmpty()) return 0.0;
                double best = Eval.num(l.get(0));
                for (Object x : l) best = name.equals("min") ? Math.min(best, Eval.num(x)) : Math.max(best, Eval.num(x));
                return best;
            }
            case "copy" -> {
                return new ArrayList<>(l);
            }
            default -> throw new SprauteRuntimeException("У списка нет метода ." + name + "(). Есть: add, insert, remove, removeAt, pop, clear, "
                    + "get, set, has, indexOf, sort, reverse, shuffle, random, join, sum, min, max, copy");
        }
    }

    private static void need(String name, List<Object> a, int n) {
        if (a.size() < n) throw new SprauteRuntimeException("." + name + "(): нужно аргументов: " + n);
    }

    public static List<Object> split(String s, String sep) {
        List<Object> out = new ArrayList<>();
        if (sep.isEmpty()) {
            s.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp))));
            return out;
        }
        for (String p : s.split(java.util.regex.Pattern.quote(sep), -1)) out.add(p);
        return out;
    }

    public static String join(List<?> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (Object x : l) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(Eval.str(x));
        }
        return sb.toString();
    }

    public static int compare(Object a, Object b) {
        try {
            return Double.compare(Eval.num(a), Eval.num(b));
        } catch (SprauteRuntimeException e) {
            return Eval.str(a).compareTo(Eval.str(b));
        }
    }

    public static List<Object> range(double from, double to, double step) {
        if (step == 0) throw new SprauteRuntimeException("range: шаг не может быть 0");
        List<Object> out = new ArrayList<>();
        for (double v = from; step > 0 ? v < to : v > to; v += step) {
            out.add(v);
            if (out.size() > 100000) throw new SprauteRuntimeException("range: слишком длинный список");
        }
        return out;
    }

    // ------------------------------------------------------------------ points as vectors

    public static List<Double> vec(Object v) {
        if (v instanceof EntityRef r) v = WorldApi.list(r.require().position());
        if (!(v instanceof List<?> l) || l.size() < 2) throw new SprauteRuntimeException("Ожидалась точка (x, y, z)");
        List<Double> out = new ArrayList<>();
        for (Object x : l) out.add(Eval.num(x));
        return out;
    }

    public static List<Object> vecOp(Object a, Object b, char op) {
        List<Double> x = vec(a);
        List<Object> out = new ArrayList<>();
        if (op == '*' || op == '/') {
            double k = Eval.num(b);
            for (double v : x) out.add(op == '*' ? v * k : v / k);
            return out;
        }
        List<Double> y = vec(b);
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) out.add(op == '+' ? x.get(i) + y.get(i) : x.get(i) - y.get(i));
        return out;
    }

    public static double length(Object a) {
        double s = 0;
        for (double v : vec(a)) s += v * v;
        return Math.sqrt(s);
    }

    // ------------------------------------------------------------------ printing and comparing

    public static String format(Object v) {
        if (v instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                Object x = l.get(i);
                sb.append(x instanceof String s ? "\"" + s + "\"" : Eval.str(x));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (sb.length() > 1) sb.append(", ");
                Object x = e.getValue();
                sb.append(e.getKey()).append(": ").append(x instanceof String s ? "\"" + s + "\"" : Eval.str(x));
            }
            return sb.append('}').toString();
        }
        return String.valueOf(v);
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ saving

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    public static final String JSON_MARK = "§json:";

    public static String toJson(Object v) {
        return JSON_MARK + GSON.toJson(plain(v));
    }

    /** Entity references become {"$entity": uuid, "type": ...} for saving. */
    private static Object plain(Object v) {
        if (v instanceof EntityRef r) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("$entity", r.uuid.toString());
            m.put("type", r.type);
            return m;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(plain(x));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), plain(e.getValue()));
            return out;
        }
        return v;
    }

    /** A saved list or dictionary back (numbers come back as numbers). */
    public static Object fromJson(String s) {
        try {
            return normalize(GSON.fromJson(s.substring(JSON_MARK.length()), Object.class));
        } catch (Exception e) {
            return s;
        }
    }

    private static Object normalize(Object v) {
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(normalize(x));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            if (m.get("$entity") instanceof String id) {
                try {
                    return new EntityRef(java.util.UUID.fromString(id), String.valueOf(m.get("type")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), normalize(e.getValue()));
            return out;
        }
        return v;
    }
}
