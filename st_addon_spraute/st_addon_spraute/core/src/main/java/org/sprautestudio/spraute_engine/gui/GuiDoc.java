package org.sprautestudio.spraute_engine.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Spraute GUI (.sgui): document properties plus a list of elements drawn in order. */
public final class GuiDoc {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Element {
        public String id;
        public String type;
        /** Explicitly set properties; everything else falls back to the schema default. */
        public Map<String, String> props = new LinkedHashMap<>();
        /** Editor only: element cannot be selected on the canvas. */
        public boolean locked;
        /** Child elements of containers (group, clip, scroll); positions are relative to this element. */
        public List<Element> children = new ArrayList<>();

        public boolean isContainer() {
            return GuiSchema.isContainer(type);
        }

        public GuiSchema.Type schema() {
            GuiSchema.Type t = GuiSchema.type(type);
            return t != null ? t : GuiSchema.type("panel");
        }

        public String get(String key) {
            String v = props.get(key);
            if (v != null) return v;
            GuiSchema.Prop p = schema().prop(key);
            return p != null ? p.def() : "";
        }

        public void set(String key, String value) {
            GuiSchema.Prop p = schema().prop(key);
            if (p != null && value.equals(p.def())) props.remove(key);
            else props.put(key, value);
        }

        public int i(String key) {
            return GuiDoc.toInt(get(key));
        }

        public float f(String key) {
            return GuiDoc.toFloat(get(key));
        }

        public boolean b(String key) {
            return GuiDoc.toBool(get(key));
        }

        public int color(String key) {
            return GuiDoc.toColor(get(key));
        }

        public Element copy() {
            Element e = new Element();
            e.id = id;
            e.type = type;
            e.props = new LinkedHashMap<>(props);
            e.locked = locked;
            for (Element c : children) e.children.add(c.copy());
            return e;
        }
    }

    public Map<String, String> props = new LinkedHashMap<>();
    public List<Element> elements = new ArrayList<>();

    public String get(String key) {
        String v = props.get(key);
        if (v != null) return v;
        GuiSchema.Prop p = GuiSchema.docProp(key);
        return p != null ? p.def() : "";
    }

    public void set(String key, String value) {
        GuiSchema.Prop p = GuiSchema.docProp(key);
        if (p != null && value.equals(p.def())) props.remove(key);
        else props.put(key, value);
    }

    public int width() {
        return Math.max(16, toInt(get("width")));
    }

    public int height() {
        return Math.max(16, toInt(get("height")));
    }

    public Element element(String id) {
        if (id == null) return null;
        for (Element e : all()) if (id.equals(e.id)) return e;
        return null;
    }

    /** Every element of the tree, parents before children. */
    public List<Element> all() {
        List<Element> out = new ArrayList<>();
        collect(elements, out);
        return out;
    }

    private static void collect(List<Element> list, List<Element> out) {
        for (Element e : list) {
            out.add(e);
            collect(e.children, out);
        }
    }

    public List<String> ids() {
        List<String> list = new ArrayList<>();
        for (Element e : all()) list.add(e.id);
        return list;
    }

    /** The list that holds e (the root list or a container's children), or null. */
    public List<Element> listOf(Element e) {
        if (elements.contains(e)) return elements;
        for (Element p : all()) if (p.children.contains(e)) return p.children;
        return null;
    }

    public Element parentOf(Element e) {
        for (Element p : all()) if (p.children.contains(e)) return p;
        return null;
    }

    /** True when {@code inner} is {@code outer} or somewhere inside it. */
    public static boolean contains(Element outer, Element inner) {
        if (outer == inner) return true;
        for (Element c : outer.children) if (contains(c, inner)) return true;
        return false;
    }

    /** Gives every element of a pasted subtree an id that is not used yet. */
    public void renameUnique(Element e) {
        if (element(e.id) != null) e.id = freshId(e.type);
        for (Element c : e.children) renameUnique(c);
    }

    /** A free id like "button3". */
    public String freshId(String type) {
        int n = 1;
        while (element(type + n) != null) n++;
        return type + n;
    }

    public Element add(String type) {
        Element e = new Element();
        e.type = type;
        e.id = freshId(type);
        elements.add(e);
        return e;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static GuiDoc fromJson(String json) {
        GuiDoc d = null;
        try {
            d = GSON.fromJson(json, GuiDoc.class);
        } catch (RuntimeException ignored) {
        }
        if (d == null) d = new GuiDoc();
        if (d.props == null) d.props = new LinkedHashMap<>();
        if (d.elements == null) d.elements = new ArrayList<>();
        fix(d, d.elements);
        return d;
    }

    private static void fix(GuiDoc d, List<Element> list) {
        list.removeIf(e -> e == null);
        for (Element e : list) {
            if (e.props == null) e.props = new LinkedHashMap<>();
            if (e.children == null) e.children = new ArrayList<>();
            if (e.type == null || GuiSchema.type(e.type) == null) e.type = "panel";
            if (e.id == null) e.id = d.freshId(e.type);
            if (!e.isContainer()) e.children.clear();
            fix(d, e.children);
        }
    }

    /** Starter layout: a window with a title, a portrait, text and two buttons. */
    public static GuiDoc example(String title) {
        GuiDoc d = new GuiDoc();
        d.set("title", title);
        Element bg = d.add("panel");
        bg.id = "window";
        bg.set("w", "300");
        bg.set("h", "170");
        bg.set("appear", "pop");
        Element head = d.add("text");
        head.id = "title";
        head.set("text", title);
        head.set("y", "-68");
        head.set("w", "280");
        head.set("align", "center");
        head.set("scale", "1.3");
        head.set("color", "#FFA08AFF");
        Element who = d.add("entity");
        who.id = "portrait";
        who.set("x", "-100");
        who.set("y", "4");
        who.set("w", "70");
        who.set("h", "100");
        who.set("followMouse", "true");
        Element text = d.add("text");
        text.id = "message";
        text.set("text", "Привет! Это интерфейс Spraute.\\nКнопки сообщают сюжету, какую нажали.");
        text.set("x", "40");
        text.set("y", "-20");
        text.set("w", "190");
        text.set("h", "60");
        text.set("typewriter", "40");
        Element ok = d.add("button");
        ok.id = "yes";
        ok.set("text", "Да");
        ok.set("x", "-10");
        ok.set("y", "55");
        ok.set("w", "90");
        ok.set("key", "1");
        Element no = d.add("button");
        no.id = "no";
        no.set("text", "Нет");
        no.set("x", "90");
        no.set("y", "55");
        no.set("w", "90");
        no.set("key", "2");
        no.set("action", "event_close");
        return d;
    }

    // ----------------------------------------------------------------- parsing

    public static int toInt(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static float toFloat(String s) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static boolean toBool(String s) {
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("да") || v.equals("yes") || v.equals("on");
    }

    /** "#RGB", "#RRGGBB" or "#AARRGGBB" to ARGB; empty or invalid gives 0 (transparent). */
    public static int toColor(String s) {
        String v = s.trim();
        if (v.startsWith("#")) v = v.substring(1);
        try {
            if (v.length() == 3) {
                int r = Integer.parseInt(v.substring(0, 1), 16), g = Integer.parseInt(v.substring(1, 2), 16), b = Integer.parseInt(v.substring(2, 3), 16);
                return 0xFF000000 | (r * 17 << 16) | (g * 17 << 8) | b * 17;
            }
            if (v.length() == 6) return 0xFF000000 | Integer.parseInt(v, 16);
            if (v.length() == 8) return (int) Long.parseLong(v, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    public static String colorString(int argb) {
        return String.format("#%08X", argb);
    }
}
