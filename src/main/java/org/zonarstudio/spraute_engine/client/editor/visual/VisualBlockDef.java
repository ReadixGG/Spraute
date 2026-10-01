package org.zonarstudio.spraute_engine.client.editor.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definition of a visual scripting block, parsed from the plugin block DSL
 * ({@code block:}, {@code row:}, {@code template:}, {@code code:}, {@code if ...:}).
 * Mirrors Spraute Studio's parseCustomBlocks model (app/src/visual.js).
 */
public final class VisualBlockDef {

    public enum Shape { STATEMENT, VALUE, WRAPPER }

    // ==================== Row tokens ====================

    /** One element inside a {@code row:} (label, field, dropdown, inline input, ...). */
    public sealed interface Token permits Label, Field, Dropdown, Checkbox, ValueInput, StatementInput {}

    public record Label(String text) implements Token {}

    /**
     * {@code (name: text|number|textmultiline|item|block|mob|texture|color: default)} — editable field,
     * toggleable to a value socket. Types item/block/mob render as a registry picker with icons.
     */
    public record Field(String name, String type, String defaultValue) implements Token {}

    /**
     * {@code [name: dropdown(лейбл: val, ...)]} or {@code [name: dropdown_model]} etc.
     * For dynamic kinds {@link #options} is empty and {@link #dynamicKind} is set
     * (model / texture / animfile / anim / npc / prefab / dimension).
     */
    public record Dropdown(String name, List<String[]> options, String dynamicKind, String defaultValue) implements Token {}

    /** {@code [name: checkbox(true)]} */
    public record Checkbox(String name, boolean defaultChecked) implements Token {}

    /** {@code input: name (type: value)} — always a value socket. */
    public record ValueInput(String name, String label) implements Token {}

    /** {@code input: name (type: statement)} — nested statement chain. */
    public record StatementInput(String name, String label) implements Token {}

    // ==================== Body items ====================

    public enum ItemKind { ROW, TEMPLATE, CODE }

    /**
     * Ordered body entry. {@code conds} are the active {@code if ...:} / {@code if_filled x:}
     * conditions (all must be true for the item to be visible / applied).
     */
    public static final class BodyItem {
        public final ItemKind kind;
        public final List<Token> tokens;      // ROW
        public final String text;             // TEMPLATE (rendered text) / CODE (JS line)
        public final List<Cond> conds;

        public BodyItem(ItemKind kind, List<Token> tokens, String text, List<Cond> conds) {
            this.kind = kind;
            this.tokens = tokens == null ? List.of() : List.copyOf(tokens);
            this.text = text;
            this.conds = conds == null ? List.of() : List.copyOf(conds);
        }
    }

    // ==================== Conditions ====================

    /** Compiled condition of an {@code if ...:} or {@code if_filled x:} DSL line. */
    public sealed interface Cond permits CondFilled, CondExpr {
        boolean eval(Map<String, String> values, Set<String> filled);
    }

    /** {@code if_filled name:} — true when the value socket/field has content. */
    public record CondFilled(String field) implements Cond {
        @Override
        public boolean eval(Map<String, String> values, Set<String> filled) {
            if (filled.contains(field)) return true;
            String v = values.get(field);
            return v != null && !v.trim().isEmpty();
        }
    }

    /** Boolean expression over field values: {@code ==, !=, and, or, not}, string literals. */
    public static final class CondExpr implements Cond {
        public final Node root;
        public final String source;

        public CondExpr(Node root, String source) {
            this.root = root;
            this.source = source;
        }

        @Override
        public boolean eval(Map<String, String> values, Set<String> filled) {
            try {
                return truthy(root.eval(values));
            } catch (Exception e) {
                return false;
            }
        }

        static boolean truthy(String v) {
            return v != null && !v.isEmpty() && !"false".equals(v) && !"FALSE".equals(v);
        }

        public sealed interface Node permits BinOp, NotOp, Ident, Lit {
            String eval(Map<String, String> values);
        }

        public record BinOp(String op, Node l, Node r) implements Node {
            @Override
            public String eval(Map<String, String> values) {
                switch (op) {
                    case "and": return truthy(l.eval(values)) && truthy(r.eval(values)) ? "true" : "false";
                    case "or":  return truthy(l.eval(values)) || truthy(r.eval(values)) ? "true" : "false";
                    case "==":  return java.util.Objects.equals(norm(l.eval(values)), norm(r.eval(values))) ? "true" : "false";
                    case "!=":  return !java.util.Objects.equals(norm(l.eval(values)), norm(r.eval(values))) ? "true" : "false";
                    default:    return "false";
                }
            }

            private static String norm(String v) {
                if (v == null) return "";
                if ("TRUE".equals(v)) return "true";
                if ("FALSE".equals(v)) return "false";
                return v;
            }
        }

        public record NotOp(Node inner) implements Node {
            @Override
            public String eval(Map<String, String> values) {
                return truthy(inner.eval(values)) ? "false" : "true";
            }
        }

        public record Ident(String name) implements Node {
            @Override
            public String eval(Map<String, String> values) {
                String v = values.get(name);
                return v == null ? "" : v;
            }
        }

        public record Lit(String value) implements Node {
            @Override
            public String eval(Map<String, String> values) { return value; }
        }
    }

    // ==================== Definition ====================

    public final String fullId;      // namespace.rawId, e.g. procode.chat
    public final String rawId;
    public final String pluginNamespace;
    public String category = "Custom";
    public String colorHex = "#555555";
    public Shape shape = Shape.STATEMENT;
    public final List<String> writeStart = new ArrayList<>();
    public final List<BodyItem> body = new ArrayList<>();
    /** True for auto-generated placeholders of unknown block types. */
    public boolean missing;
    /** Mod-built-in (not from a plugin); hidden from the palette unless listed in a category. */
    public boolean builtin;

    public VisualBlockDef(String rawId, String pluginNamespace) {
        this.rawId = rawId;
        this.pluginNamespace = pluginNamespace == null ? "" : pluginNamespace;
        this.fullId = this.pluginNamespace.isEmpty() ? rawId : this.pluginNamespace + "." + rawId;
    }

    /** Short display label (localized): first Label token, else rawId. */
    public String displayLabel() {
        String raw = displayLabelRaw();
        if (missing || builtin) return org.zonarstudio.spraute_engine.client.editor.EditorI18n.localize(raw);
        return PluginI18n.t(pluginNamespace, raw);
    }

    /** Unlocalized label from the .spr source (for search matching both languages). */
    public String displayLabelRaw() {
        for (BodyItem it : body) {
            if (it.kind != ItemKind.ROW) continue;
            for (Token t : it.tokens) {
                if (t instanceof Label l && !l.text().isBlank()) return l.text();
            }
        }
        return rawId;
    }

    /** All value-socket input names ({@code input: x (type: value)}). */
    public Set<String> valueInputNames() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (BodyItem it : body) {
            if (it.kind != ItemKind.ROW) continue;
            for (Token t : it.tokens) {
                if (t instanceof ValueInput vi) out.add(vi.name());
            }
        }
        return out;
    }

    /** All statement-input names (nested chains), incl. implicit DO for wrappers. */
    public Set<String> statementInputNames() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (BodyItem it : body) {
            if (it.kind != ItemKind.ROW) continue;
            for (Token t : it.tokens) {
                if (t instanceof StatementInput si) out.add(si.name());
            }
        }
        if (shape == Shape.WRAPPER) out.add("DO");
        return out;
    }

    public boolean hasCode() {
        for (BodyItem it : body) if (it.kind == ItemKind.CODE) return true;
        return false;
    }
}
