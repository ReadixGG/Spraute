package org.zonarstudio.spraute_engine.client.editor.visual;

import com.mojang.logging.LogUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Spraute script code from visual blocks: {@code template:} lines with
 * {@code {var}} / {@code "{var}"} substitution and {@code code:} JavaScript bodies
 * executed with Rhino (given {@code _getVal} / {@code _str} helpers, as in Studio).
 */
public final class VisualBlockCodegen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern TMPL_STR_VAR = Pattern.compile("\"\\{([a-zA-Z0-9_]+)\\}\"");
    private static final Pattern TMPL_VAR = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
    /** player.name / getFirstPlayer().name */
    private static final Pattern PROP_PATH = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)+$");
    /** foo() / foo(bar) — already an expression */
    private static final Pattern CALL_EXPR = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_]*\\(.*\\)$");

    /** Supplies values for a concrete block instance during generation. */
    public interface ValueResolver {
        /** Field value, attached value-block code, or nested statement code for statement inputs. */
        String getVal(String name);
        /** True when the name is a value/statement socket with content. */
        boolean isFilled(String name);
        /** Raw field values (for condition evaluation). */
        Map<String, String> fieldValues();
        /** True when a value-block is plugged into this socket (do not re-quote). */
        default boolean isSocketConnected(String name) { return false; }
    }

    private VisualBlockCodegen() {}

    /** Generates the code of one block. Statements end with a newline, values don't. */
    public static String generate(VisualBlockDef def, ValueResolver resolver) {
        Map<String, String> values = resolver.fieldValues();
        Set<String> filled = new LinkedHashSet<>();
        for (String n : def.valueInputNames()) if (resolver.isFilled(n)) filled.add(n);
        for (String n : def.statementInputNames()) if (resolver.isFilled(n)) filled.add(n);
        for (var e : values.entrySet()) if (e.getValue() != null && !e.getValue().isBlank()) filled.add(e.getKey());

        StringBuilder jsBody = new StringBuilder();
        String template = null;
        boolean hasCode = false;
        for (VisualBlockDef.BodyItem item : def.body) {
            if (item.kind == VisualBlockDef.ItemKind.ROW) continue;
            boolean active = true;
            for (VisualBlockDef.Cond c : item.conds) {
                if (!c.eval(values, filled)) { active = false; break; }
            }
            if (!active) continue;
            if (item.kind == VisualBlockDef.ItemKind.TEMPLATE) {
                if (template == null) template = item.text;
            } else {
                hasCode = true;
                jsBody.append(item.text).append('\n');
            }
        }

        String result;
        if (hasCode) {
            result = runJs(def.fullId, jsBody.toString(), resolver);
        } else if (template != null) {
            result = renderTemplate(template, resolver);
        } else {
            result = "";
        }

        if (def.shape == VisualBlockDef.Shape.VALUE) {
            return result == null ? "" : result.replaceAll("\\s*\\n\\s*", " ").trim();
        }
        if (result == null) result = "";
        if (!result.isEmpty() && !result.endsWith("\n")) result += "\n";
        return result;
    }

    private static String renderTemplate(String template, ValueResolver resolver) {
        // "{var}" — string literal, unless a value-block / expression is plugged in
        StringBuilder sb = new StringBuilder();
        Matcher m = TMPL_STR_VAR.matcher(template);
        int last = 0;
        while (m.find()) {
            sb.append(template, last, m.start());
            sb.append(strOrExpr(m.group(1), resolver));
            last = m.end();
        }
        sb.append(template.substring(last));
        String stage = sb.toString();

        StringBuilder out = new StringBuilder();
        m = TMPL_VAR.matcher(stage);
        last = 0;
        while (m.find()) {
            out.append(stage, last, m.start());
            String v = resolver.getVal(m.group(1));
            out.append(v == null ? "" : v);
            last = m.end();
        }
        out.append(stage.substring(last));
        return out.toString();
    }

    /**
     * Quote as a string literal, or pass through when the slot holds an expression
     * (plugged value-block, {@code player.name}, parenthesized concat, call, …).
     */
    private static String strOrExpr(String name, ValueResolver resolver) {
        String raw = nullToEmpty(resolver.getVal(name));
        if (resolver.isSocketConnected(name)) {
            String t = raw.trim();
            return t.isEmpty() ? "\"\"" : t;
        }
        return maybeExprOrLiteral(raw);
    }

    private static String maybeExprOrLiteral(String raw) {
        String s = raw == null ? "" : raw;
        if (s.isBlank()) return "\"\"";
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) return t;
        if (t.startsWith("'") && t.endsWith("'") && t.length() >= 2) return t;
        // Explicit expression prefix from the field: =player.name / =name_player + "?"
        if (t.startsWith("=")) {
            String expr = t.substring(1).trim();
            return expr.isEmpty() ? "\"\"" : expr;
        }
        if (t.startsWith("(") && t.endsWith(")")) return t;
        if (PROP_PATH.matcher(t).matches()) return t;
        if (CALL_EXPR.matcher(t).matches()) return t;
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }

    private static String strLiteral(String raw) {
        return maybeExprOrLiteral(raw);
    }

    private static String runJs(String blockId, String js, ValueResolver resolver) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1); // interpreted mode: no bytecode generation, safe in MC classloaders
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject.putProperty(scope, "_getVal", new JsFn((args) ->
                    args.length > 0 ? nullToEmpty(resolver.getVal(String.valueOf(args[0]))) : ""));
            ScriptableObject.putProperty(scope, "_str", new JsFn((args) -> {
                if (args.length == 0) return "\"\"";
                return strOrExpr(String.valueOf(args[0]), resolver);
            }));
            String wrapped = "(function(){\n" + js + "\n})()";
            Object result = cx.evaluateString(scope, wrapped, blockId, 1, null);
            if (result == null || result instanceof org.mozilla.javascript.Undefined) return "";
            return Context.toString(result);
        } catch (Exception e) {
            LOGGER.error("[VisualBlocks] CodeGen error in {}: {}", blockId, e.getMessage());
            return "/* CodeGen error in " + blockId + ": " + e.getMessage() + " */\n";
        } finally {
            Context.exit();
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static final class JsFn extends org.mozilla.javascript.BaseFunction {
        interface Impl { String call(Object[] args); }
        private final Impl impl;

        JsFn(Impl impl) { this.impl = impl; }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            return impl.call(args);
        }
    }
}
