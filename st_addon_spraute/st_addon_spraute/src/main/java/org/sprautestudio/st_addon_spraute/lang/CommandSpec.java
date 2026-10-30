package org.sprautestudio.st_addon_spraute.lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Description of one statement command. The syntax string is the single source of truth
 * for parsing, validation, completion, documentation and visual-node generation.
 *
 * <p>Syntax format: {@code <name:type>} is a positional parameter, a bare word starts a
 * keyword group ({@code to <target:loc>}), {@code [...]} makes a group optional and
 * {@code <name:type...>} accepts several values.</p>
 */
public final class CommandSpec {
    public static final class Param {
        public final String name;
        public final ParamType type;
        public final boolean vararg;

        Param(String name, ParamType type, boolean vararg) {
            this.name = name;
            this.type = type;
            this.vararg = vararg;
        }
    }

    public static final class Group {
        public final String keyword;
        public final boolean optional;
        public final List<Param> params = new ArrayList<>();

        Group(String keyword, boolean optional) {
            this.keyword = keyword;
            this.optional = optional;
        }

        /** A bare optional word without values, e.g. {@code [nowait]}. */
        public boolean isFlag() {
            return keyword != null && params.isEmpty();
        }
    }

    public final String key;
    public final String head;
    public final String sub;
    public final String category;
    public final String syntax;
    public final String doc;
    public final List<Group> groups;

    public CommandSpec(String key, String category, String syntax, String doc) {
        this.key = key;
        int sp = key.indexOf(' ');
        this.head = sp < 0 ? key : key.substring(0, sp);
        this.sub = sp < 0 ? null : key.substring(sp + 1);
        this.category = category;
        this.syntax = syntax;
        this.doc = doc;
        this.groups = Collections.unmodifiableList(parseSyntax(syntax));
    }

    private static List<Group> parseSyntax(String syntax) {
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean inOptional = false;
        for (String raw : syntax.trim().isEmpty() ? new String[0] : syntax.trim().split("\\s+")) {
            String w = raw;
            if (w.startsWith("[")) {
                inOptional = true;
                current = new Group(null, true);
                groups.add(current);
                w = w.substring(1);
                if (w.isEmpty()) continue;
            }
            boolean closes = w.endsWith("]");
            if (closes) w = w.substring(0, w.length() - 1);
            if (w.startsWith("<")) {
                String inner = w.substring(1, w.length() - 1);
                boolean vararg = inner.endsWith("...");
                if (vararg) inner = inner.substring(0, inner.length() - 3);
                String[] nt = inner.split(":");
                Param p = new Param(nt[0], ParamType.byName(nt[1]), vararg);
                if (current == null || (!inOptional && current.keyword == null && !current.params.isEmpty())) {
                    current = new Group(null, false);
                    groups.add(current);
                }
                current.params.add(p);
                if (!inOptional && current.keyword == null) current = null;
            } else if (!w.isEmpty()) {
                if (inOptional && current != null && current.keyword == null && current.params.isEmpty()) {
                    groups.remove(current);
                    current = new Group(w, true);
                    groups.add(current);
                } else {
                    current = new Group(w, inOptional);
                    groups.add(current);
                }
            }
            if (closes) {
                inOptional = false;
                current = null;
            }
        }
        return groups;
    }

    public List<Group> flags() {
        List<Group> list = new ArrayList<>();
        for (Group g : groups) if (g.isFlag()) list.add(g);
        return list;
    }

    public List<Param> allParams() {
        List<Param> list = new ArrayList<>();
        for (Group g : groups) list.addAll(g.params);
        return list;
    }

    public Param param(String name) {
        for (Group g : groups) for (Param p : g.params) if (p.name.equals(name)) return p;
        return null;
    }

    public Group groupOf(String paramName) {
        for (Group g : groups) for (Param p : g.params) if (p.name.equals(paramName)) return g;
        return null;
    }

    /** Human-readable signature, e.g. {@code move <персонаж> to <точка> [speed <число>]}. */
    public String signature() {
        StringBuilder sb = new StringBuilder(key);
        for (Group g : groups) {
            sb.append(' ');
            if (g.optional) sb.append('[');
            if (g.keyword != null) sb.append(g.keyword);
            for (int i = 0; i < g.params.size(); i++) {
                Param p = g.params.get(i);
                if (g.keyword != null || i > 0) sb.append(' ');
                sb.append('<').append(p.name).append(p.vararg ? "..." : "").append('>');
            }
            if (g.optional) sb.append(']');
        }
        return sb.toString();
    }

    /** Editor snippet with {@code ${n:placeholder}} markers for the required parameters. */
    public String snippet() {
        StringBuilder sb = new StringBuilder(key);
        int n = 1;
        for (Group g : groups) {
            if (g.optional) continue;
            sb.append(' ');
            if (g.keyword != null) sb.append(g.keyword).append(' ');
            for (int i = 0; i < g.params.size(); i++) {
                Param p = g.params.get(i);
                if (i > 0) sb.append(' ');
                String ph = switch (p.type) {
                    case TEXT, SOUND, ITEM, EFFECT, ENTITY, ANIM, GUI, GUIEL, IMAGE -> "\"" + p.name + "\"";
                    case DURATION -> "1s";
                    case LOC -> "(0, 64, 0)";
                    default -> p.name;
                };
                if (p.type == ParamType.TEXT || p.type == ParamType.SOUND || p.type == ParamType.ITEM
                        || p.type == ParamType.EFFECT || p.type == ParamType.ENTITY || p.type == ParamType.ANIM
                        || p.type == ParamType.GUI || p.type == ParamType.GUIEL || p.type == ParamType.IMAGE || p.type == ParamType.CUTSCENE || p.type == ParamType.VFX || p.type == ParamType.SHOP || p.type == ParamType.QUEST || p.type == ParamType.ZONE || p.type == ParamType.FIGHT || p.type == ParamType.PATH) {
                    sb.append("\"${").append(n++).append(':').append(p.name).append("}\"");
                } else {
                    sb.append("${").append(n++).append(':').append(ph).append('}');
                }
            }
        }
        return sb.toString();
    }
}
