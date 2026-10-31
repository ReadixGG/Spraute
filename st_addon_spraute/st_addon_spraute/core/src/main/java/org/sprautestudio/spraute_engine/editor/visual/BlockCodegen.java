package org.sprautestudio.spraute_engine.editor.visual;

import org.sprautestudio.spraute_engine.editor.visual.VisualScript.Block;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.CommandSpec;
import org.sprautestudio.spraute_engine.lang.Diagnostic;
import org.sprautestudio.spraute_engine.lang.ParamType;

import java.util.ArrayList;
import java.util.List;

/** Compiles a block script into .sst source, remembering which block produced each line. */
public final class BlockCodegen {
    public static final class Result {
        public final String text;
        public final List<Block> lineOwners;
        /** Problems found before compiling (missing start block, empty inputs...). */
        public final List<String> problems;
        public final Analyzer.Result analysis;

        Result(String text, List<Block> lineOwners, List<String> problems) {
            this.text = text;
            this.lineOwners = lineOwners;
            this.problems = problems;
            this.analysis = Analyzer.analyze(text);
        }

        public boolean hasErrors() {
            return !problems.isEmpty() || analysis.hasErrors();
        }

        public int errorCount() {
            return problems.size() + analysis.errorCount();
        }
    }

    private final StringBuilder out = new StringBuilder();
    private final List<Block> owners = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    private BlockCodegen() {}

    public static Result generate(VisualScript script) {
        BlockCodegen g = new BlockCodegen();
        g.run(script);
        Result r = new Result(g.out.toString(), g.owners, g.problems);
        g.markErrors(r);
        return r;
    }

    private void line(int indent, String text, Block owner) {
        out.append("    ".repeat(indent)).append(text).append('\n');
        owners.add(owner);
    }

    private void run(VisualScript script) {
        clearErrors(script);
        VisualScript.Stack main = script.mainStack();
        if (main == null) {
            problems.add("Нет блока «когда начинается сюжет» — перетащите его из категории «Поток»");
            return;
        }
        Block start = main.blocks.get(0);
        String id = start.field("id").trim();
        if (!id.matches("\\d+")) fail(start, "Id сюжета должен быть числом");
        String auto = start.field("auto").trim();
        line(0, "script " + (id.isEmpty() ? "0" : id) + " " + quote(start.field("title"))
                + (auto.equals("при входе") ? " autostart" : auto.equals("один раз") ? " autostart once" : "")
                + (isYes(start.field("solo")) ? " solo" : ""), start);
        line(0, "", null);
        List<Block> body = main.blocks.subList(1, main.blocks.size());
        List<VisualScript.Stack> funcs = new ArrayList<>();
        for (VisualScript.Stack s : script.stacks) if (!s.blocks.isEmpty() && s.blocks.get(0).type.equals("func")) funcs.add(s);
        int before = owners.size();
        declarations(body);
        for (VisualScript.Stack s : funcs) declarations(s.blocks.subList(1, s.blocks.size()));
        if (owners.size() > before) line(0, "", null);
        // event handlers first, so they are listening from the very start
        for (VisualScript.Stack s : script.stacks) {
            if (s.blocks.isEmpty() || !s.blocks.get(0).typeInfo().isEvent()) continue;
            // a loose event block (or an old-style event hat with blocks under it)
            Block head = s.blocks.get(0);
            line(0, command(head, head.typeInfo().spec()).replaceFirst("^wait ", "on ") + " {", head);
            if (!head.slots.isEmpty()) statements(head.slots.get(0), 1);
            statements(s.blocks.subList(1, s.blocks.size()), 1);
            line(0, "}", head);
        }
        statements(body, 0);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (VisualScript.Stack s : funcs) {
            Block head = s.blocks.get(0);
            String name = required(head, "name");
            if (!names.add(name)) fail(head, "Функция «" + name + "» уже есть");
            line(0, "", null);
            line(0, "func " + name + "(" + head.field("params").trim() + ") {", head);
            statements(s.blocks.subList(1, s.blocks.size()), 1);
            line(0, "}", head);
        }
    }

    private void clearErrors(VisualScript script) {
        for (VisualScript.Stack s : script.stacks) clearErrors(s.blocks);
    }

    private void clearErrors(List<Block> blocks) {
        for (Block b : blocks) {
            b.error = false;
            b.errorText = null;
            for (List<Block> slot : b.slots) clearErrors(slot);
            clearErrors(new ArrayList<>(b.inputs.values()));
        }
    }

    private void fail(Block b, String message) {
        b.error = true;
        b.errorText = b.errorText == null ? message : b.errorText + "\n" + message;
        problems.add(message);
    }

    private void declarations(List<Block> blocks) {
        for (Block b : blocks) {
            switch (b.type) {
                case "character" -> {
                    StringBuilder sb = new StringBuilder("character ").append(required(b, "name")).append(" = ").append(requiredQuoted(b, "type"));
                    if (has(b, "display")) sb.append(" name ").append(requiredQuoted(b, "display"));
                    if (has(b, "color")) sb.append(" color ").append(requiredQuoted(b, "color"));
                    if (has(b, "hitbox")) sb.append(" hitbox ").append(requiredQuoted(b, "hitbox"));
                    if (has(b, "speed")) sb.append(" speed ").append(paren(value(b, "speed")));
                    if (has(b, "icon")) sb.append(" icon ").append(requiredQuoted(b, "icon"));
                    if (has(b, "portrait")) sb.append(" portrait ").append(requiredQuoted(b, "portrait"));
                    for (String hk : new String[]{"headscale", "headx", "heady", "headyaw"})
                        if (has(b, hk)) sb.append(" ").append(hk).append(" ").append(paren(value(b, hk)));
                    if (has(b, "headbone")) sb.append(" headbone ").append(requiredQuoted(b, "headbone"));
                    line(0, sb.toString(), b);
                }
                case "location" -> {
                    StringBuilder sb = new StringBuilder("location ").append(required(b, "name")).append(" = ").append(required(b, "pos"));
                    if (!b.field("yaw").isBlank()) sb.append(" yaw ").append(b.field("yaw").trim());
                    line(0, sb.toString(), b);
                }
                case "var" -> line(0, "var " + required(b, "name") + " = " + required(b, "value"), b);
                default -> {
                }
            }
            for (List<Block> slot : b.slots) declarations(slot);
        }
    }

    // ---------------------------------------------------------------- values

    private static boolean has(Block b, String field) {
        return b.inputs.containsKey(field) || !b.field(field).isBlank();
    }

    /** Code of a field: the plugged value block or the typed text (unquoted). */
    private String value(Block b, String field) {
        Block in = b.inputs.get(field);
        if (in != null) return expr(in);
        return b.field(field).trim();
    }

    private static String paren(String code) {
        if (code.matches("-?[\\w.]+") || (code.startsWith("(") && code.endsWith(")"))) return code;
        return "(" + code + ")";
    }

    /** A quoted text field: literal text gets quotes, a value block is used as an expression. */
    private String requiredQuoted(Block b, String field) {
        Block in = b.inputs.get(field);
        if (in != null) return paren(expr(in));
        return quote(required(b, field));
    }

    /** Expression code of a value block. */
    String expr(Block r) {
        BlockTypes.Type t = r.typeInfo();
        if (t == null || t.code() == null) {
            fail(r, "Этот блок нельзя вставить в поле");
            return "0";
        }
        return fill(r, template(r));
    }

    /** The code template of a block: the selected mode of a block with modes, else its fixed code. */
    private static String template(Block b) {
        BlockTypes.Variant v = BlockTypes.variant(b.type);
        if (v != null) return v.template(b.field(v.field()).trim());
        return b.typeInfo().code();
    }

    private static final java.util.regex.Pattern OPTIONAL = java.util.regex.Pattern.compile("\\[([^\\[\\]]*)]");
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("%(\\w+)");

    /** Fills %field placeholders; [optional parts] are dropped when one of their fields is empty. */
    private String fill(Block b, String template) {
        java.util.regex.Matcher m = OPTIONAL.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1);
            boolean keep = true;
            java.util.regex.Matcher p = PLACEHOLDER.matcher(inner);
            while (p.find()) if (!has(b, p.group(1))) keep = false;
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(keep ? inner : ""));
        }
        m.appendTail(sb);
        String code = sb.toString();
        List<BlockTypes.Field> fields = new ArrayList<>(b.typeInfo().fields());
        fields.sort((a, c) -> c.name().length() - a.name().length());
        for (BlockTypes.Field f : fields) {
            if (code.contains("%" + f.name())) code = code.replace("%" + f.name(), argument(b, f));
        }
        return code;
    }

    private String argument(Block r, BlockTypes.Field f) {
        Block in = r.inputs.get(f.name());
        if (in != null) {
            String e = paren(expr(in));
            return f.kind() == ParamType.DURATION ? "(" + e + " * 20)" : e;
        }
        String v = r.field(f.name()).trim();
        if (f.kind() == null || f.kind() == ParamType.VAR) {
            if (v.isEmpty()) fail(r, "Выберите переменную");
            return v.isEmpty() ? "_" : v;
        }
        return switch (f.kind()) {
            case CHAR, LABEL, TARGET, LOC, KEY, EMOTION, LAYER, MOVETYPE, FUNC, BRANCH, SPEAKER, DURATION, CHOICE, SYSTEM, EASE -> {
                if (v.isEmpty()) {
                    fail(r, "Не заполнено поле «" + (f.label().isEmpty() ? f.name() : f.label()) + "»");
                    yield "_";
                }
                yield v;
            }
            case BOOL -> v.equalsIgnoreCase("да") || v.equalsIgnoreCase("true") ? "true" : v.isEmpty() || v.equalsIgnoreCase("нет") ? "false" : paren(v);
            case NUMBER, INT, SCRIPT -> v.isEmpty() ? "0" : v.matches("-?\\d+(\\.\\d+)?") ? v : paren(v);
            case EXPR -> {
                if (v.isEmpty()) {
                    fail(r, "Пустое поле в блоке «" + r.typeInfo().key() + "»");
                    yield "0";
                }
                yield paren(v);
            }
            default -> quoteValue(r.field(f.name())); // typed text keeps its spaces
        };
    }

    /** Text typed into a text slot: numbers stay numbers, everything else becomes a string. */
    private static String quoteValue(String v) {
        if (v.trim().matches("-?\\d+(\\.\\d+)?")) return v.trim();
        return quote(v);
    }

    /** Code of a command parameter, or "" when empty. */
    private String paramCode(Block b, CommandSpec.Param p) {
        Block in = b.inputs.get(p.name);
        if (in != null) {
            String e = expr(in);
            return p.type == ParamType.DURATION ? "(" + paren(e) + " * 20)" : paren(e);
        }
        String v = b.field(p.name).trim();
        return v.isEmpty() ? "" : format(p.type, p.vararg, v);
    }

    private String required(Block b, String field) {
        Block in = b.inputs.get(field);
        if (in != null) return expr(in);
        String v = b.field(field).trim();
        if (v.isEmpty()) {
            BlockTypes.Field f = b.typeInfo().field(field);
            fail(b, "Не заполнено поле «" + (f != null ? f.label() : field) + "»");
            return "_";
        }
        return v;
    }

    private void statements(List<Block> blocks, int indent) {
        for (Block b : blocks) statement(b, indent);
    }

    private void statement(Block b, int indent) {
        BlockTypes.Type t = b.typeInfo();
        switch (b.type) {
            case "start" -> fail(b, "Второй блок начала сюжета внутри сюжета");
            case "func" -> fail(b, "Шапку функции нельзя вкладывать — оставьте её отдельной стопкой");
            case "call" -> line(indent, "call " + required(b, "name") + "(" + b.field("args").trim() + ")", b);
            case "asynccall" -> line(indent, "async call " + required(b, "name") + "(" + b.field("args").trim() + ")"
                    + (b.field("as").isBlank() ? "" : " as " + b.field("as").trim()), b);
            case "async" -> block(b, indent, b.field("name").isBlank() ? "async" : "async " + b.field("name").trim());
            case "await" -> line(indent, "await " + required(b, "name"), b);
            case "cancel" -> line(indent, "cancel " + required(b, "name"), b);
            case "return" -> line(indent, "return", b);
            case "character", "location", "var" -> {
            }
            case "comment" -> line(indent, "// " + b.field("text"), b);
            case "label" -> line(indent, "label " + required(b, "name"), b);
            case "goto" -> line(indent, "goto " + required(b, "label"), b);
            case "stop" -> line(indent, "stop", b);
            case "code" -> {
                for (String l : required(b, "code").split(";|\n")) if (!l.isBlank()) line(indent, l.trim(), b);
            }
            case "set" -> line(indent, "set " + required(b, "var") + " " + (b.field("op").isBlank() ? "=" : b.field("op").trim())
                    + " " + required(b, "value"), b);
            case "change" -> line(indent, "set " + required(b, "var") + " += " + required(b, "value"), b);
            case "switch" -> {
                line(indent, "switch " + required(b, "value") + " {", b);
                if (b.options.isEmpty()) fail(b, "Добавьте хотя бы один вариант (ПКМ по блоку)");
                for (int i = 0; i < b.options.size(); i++) {
                    String o = b.options.get(i).trim();
                    line(indent + 1, (o.isEmpty() ? "\"\"" : o.startsWith("\"") || o.matches("-?\\d+(\\.\\d+)?") ? o : quote(o)) + " {", b);
                    statements(b.slots.get(i), indent + 2);
                    line(indent + 1, "}", b);
                }
                List<Block> otherwise = b.slots.get(b.slots.size() - 1);
                if (!otherwise.isEmpty()) {
                    line(indent + 1, "else {", b);
                    statements(otherwise, indent + 2);
                    line(indent + 1, "}", b);
                }
                line(indent, "}", b);
            }
            case "if", "ifelse" -> {
                line(indent, "if " + required(b, "cond") + " {", b);
                statements(b.slots.get(0), indent + 1);
                if (b.type.equals("ifelse")) {
                    line(indent, "} else {", b);
                    statements(b.slots.get(1), indent + 1);
                }
                line(indent, "}", b);
            }
            case "while" -> block(b, indent, "while " + required(b, "cond"));
            case "for" -> block(b, indent, "for " + required(b, "var") + " in " + required(b, "list"));
            case "as" -> block(b, indent, "as " + required(b, "who"));
            case "every" -> block(b, indent, "every " + required(b, "time"));
            case "break" -> line(indent, "break", b);
            case "continue" -> line(indent, "continue", b);
            case "propset" -> line(indent, "set " + required(b, "who") + "." + required(b, "prop") + " = " + required(b, "value"), b);
            case "method" -> line(indent, required(b, "who") + "." + required(b, "method") + "(" + b.field("args").trim() + ")", b);
            case "listadd" -> line(indent, required(b, "var") + ".add(" + required(b, "value") + ")", b);
            case "listremove" -> line(indent, required(b, "var") + ".remove(" + required(b, "value") + ")", b);
            case "listclear" -> line(indent, required(b, "var") + ".clear()", b);
            case "setat" -> line(indent, "set " + required(b, "var") + "[" + required(b, "index") + "] = " + required(b, "value"), b);
            case "repeat" -> block(b, indent, "repeat " + required(b, "count"));
            case "parallel" -> block(b, indent, "parallel " + required(b, "name"));
            case "choice" -> {
                StringBuilder sb = new StringBuilder("choice ").append(required(b, "who")).append(' ').append(requiredQuoted(b, "text"));
                if (!b.field("emotion").isBlank()) sb.append(" emotion ").append(b.field("emotion").trim());
                line(indent, sb + " {", b);
                if (b.options.isEmpty()) fail(b, "Добавьте хотя бы один вариант ответа (ПКМ по блоку)");
                for (int i = 0; i < b.options.size(); i++) {
                    line(indent + 1, quote(b.options.get(i)) + " {", b);
                    statements(b.slots.get(i), indent + 2);
                    line(indent + 1, "}", b);
                }
                line(indent, "}", b);
            }
            default -> {
                if (t.isValue()) fail(b, "Блок-значение нужно вставить в поле другого блока");
                else if (BlockTypes.variant(b.type) != null) line(indent, fill(b, template(b)), b);
                else if (t.isEvent()) {
                    line(indent, command(b, t.spec()).replaceFirst("^wait ", "on ") + " {", b);
                    statements(b.slots.isEmpty() ? List.of() : b.slots.get(0), indent + 1);
                    line(indent, "}", b);
                }
                else if (t.spec() != null) line(indent, command(b, t.spec()), b);
            }
        }
    }

    private static boolean isYes(String value) {
        String v = value.trim().toLowerCase();
        return v.equals("true") || v.equals("да");
    }

    private void block(Block b, int indent, String head) {
        line(indent, head + " {", b);
        statements(b.slots.get(0), indent + 1);
        line(indent, "}", b);
    }

    private String command(Block b, CommandSpec spec) {
        StringBuilder sb = new StringBuilder(spec.key);
        for (CommandSpec.Group g : spec.groups) {
            if (g.isFlag()) {
                if (isYes(b.field(g.keyword))) sb.append(' ').append(g.keyword);
                continue;
            }
            List<String> values = new ArrayList<>();
            boolean any = false;
            for (CommandSpec.Param p : g.params) {
                String v = paramCode(b, p);
                if (!v.isEmpty()) any = true;
                values.add(v);
            }
            if (!any && g.optional) continue;
            sb.append(' ');
            if (g.keyword != null) sb.append(g.keyword).append(' ');
            for (int i = 0; i < g.params.size(); i++) {
                CommandSpec.Param p = g.params.get(i);
                String v = values.get(i);
                if (v.isEmpty()) {
                    BlockTypes.Field f = b.typeInfo().field(p.name);
                    fail(b, "Не заполнено поле «" + (f != null ? f.label() : p.name) + "»");
                    v = "_";
                }
                if (i > 0) sb.append(' ');
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private static String format(ParamType type, boolean vararg, String v) {
        boolean quoted = type == ParamType.TEXT || type == ParamType.SOUND || type == ParamType.ITEM
                || type == ParamType.EFFECT || type == ParamType.ENTITY || type == ParamType.ANIM
                || type == ParamType.GUI || type == ParamType.GUIEL || type == ParamType.IMAGE || type == ParamType.CUTSCENE || type == ParamType.VFX || type == ParamType.SHOP || type == ParamType.QUEST || type == ParamType.ZONE || type == ParamType.FIGHT || type == ParamType.PATH;
        if (!quoted) return v;
        if (vararg) {
            StringBuilder sb = new StringBuilder();
            for (String part : v.split(",")) {
                if (part.isBlank()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(quote(part.trim()));
            }
            return sb.toString();
        }
        return quote(v);
    }

    static String quote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Maps analyzer diagnostics back onto the blocks that produced the lines. */
    private void markErrors(Result r) {
        for (Diagnostic d : r.analysis.diagnostics) {
            if (d.severity != Diagnostic.Severity.ERROR) continue;
            Block owner = d.line < owners.size() ? owners.get(d.line) : null;
            if (owner == null) continue;
            owner.error = true;
            owner.errorText = owner.errorText == null ? d.message : owner.errorText + "\n" + d.message;
        }
    }
}
