package org.zonarstudio.spraute_engine.client.editor.visual;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the visual block DSL used by Spraute Studio plugin files
 * ({@code plugins/<name>/blocks/*.spr}). Java port of {@code parseCustomBlocks}
 * from {@code app/src/visual.js}.
 *
 * <p>The historical {@code #\ } prefix on every line is <b>optional</b>: lines are
 * accepted both with and without it, so plugin authors can write plain
 * {@code block: my_block} files.
 */
public final class VisualBlockDslParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Splits chunks on lines starting a new block (with optional #\ prefix). */
    private static final Pattern BLOCK_SPLIT = Pattern.compile("(?:^|\\n)#\\\\?\\s*block:\\s*|(?:^|\\n)\\s*block:\\s*");
    private static final Pattern LINE_PREFIX = Pattern.compile("^#\\\\?\\s?(.*)", Pattern.DOTALL);
    private static final Pattern ROW_TOKEN = Pattern.compile(
            "input:\\s*(\\w+)\\s*\\(type:\\s*(value|statement)\\)|\"[^\"]*\"|\\[[^\\]]+\\]|\\([^)]+\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_LINE = Pattern.compile("^input:\\s*(\\w+)\\s*\\(type:\\s*(\\w+)\\)(?:\\s+\"([^\"]+)\")?");
    private static final Pattern IF_FILLED = Pattern.compile("^if_filled\\s+([a-zA-Z_]\\w*)\\s*:$");

    private VisualBlockDslParser() {}

    /** Parses all block definitions from one .spr file body. */
    public static List<VisualBlockDef> parse(String text, String pluginNamespace) {
        List<VisualBlockDef> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String[] chunks = BLOCK_SPLIT.split(text);
        for (int i = 1; i < chunks.length; i++) {
            try {
                VisualBlockDef def = parseChunk("block: " + chunks[i], pluginNamespace);
                if (def != null) out.add(def);
            } catch (Exception e) {
                LOGGER.error("[VisualBlocks] Failed to parse block chunk in plugin '{}': {}", pluginNamespace, e.toString());
            }
        }
        return out;
    }

    private static String stripPrefix(String rawLine) {
        Matcher m = LINE_PREFIX.matcher(rawLine);
        return m.matches() ? m.group(1) : rawLine;
    }

    private record CondFrame(int indent, VisualBlockDef.Cond cond) {}

    private static VisualBlockDef parseChunk(String chunk, String namespace) {
        String[] rawLines = chunk.split("\n");
        String rawId = "";
        String category = "Custom";
        String color = "#555555";
        String shape = "statement";
        List<String> writeStart = new ArrayList<>();
        List<String[]> bodyRaw = new ArrayList<>(); // [strippedLine, indentAsString]

        boolean inMeta = true;
        for (String raw : rawLines) {
            String line = stripPrefix(raw);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
            // Legacy sections are not supported in the mod; skip such blocks safely.
            if (trimmed.equals("[UI]") || trimmed.equals("[DYNAMIC_UI]")
                    || trimmed.equals("[CODE_GEN]") || trimmed.equals("[CODE_PARSE]")) {
                LOGGER.warn("[VisualBlocks] Legacy section syntax in block '{}' is not supported by the in-game editor", rawId);
                return null;
            }
            if (inMeta) {
                if (trimmed.startsWith("block:")) { rawId = trimmed.substring(6).trim(); continue; }
                if (trimmed.startsWith("category:")) { category = trimmed.substring(9).trim(); continue; }
                if (trimmed.startsWith("color:")) { color = trimmed.substring(6).trim(); continue; }
                if (trimmed.startsWith("shape:")) { shape = trimmed.substring(6).trim().toLowerCase(java.util.Locale.ROOT); continue; }
                if (trimmed.startsWith("write_start:")) {
                    String ws = trimmed.substring(12).trim();
                    if (!ws.isEmpty()) writeStart.add(ws);
                    continue;
                }
                inMeta = false;
            }
            int indent = 0;
            while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) indent++;
            bodyRaw.add(new String[]{trimmed, String.valueOf(indent)});
        }

        if (rawId.isEmpty()) return null;

        VisualBlockDef def = new VisualBlockDef(rawId, namespace);
        def.category = category;
        def.colorHex = color;
        def.shape = switch (shape) {
            case "value" -> VisualBlockDef.Shape.VALUE;
            case "wrapper" -> VisualBlockDef.Shape.WRAPPER;
            default -> VisualBlockDef.Shape.STATEMENT;
        };
        def.writeStart.addAll(writeStart);

        Deque<CondFrame> condStack = new ArrayDeque<>();
        String pendingLabel = null;

        for (int bi = 0; bi < bodyRaw.size(); bi++) {
            String line = bodyRaw.get(bi)[0];
            int indent = Integer.parseInt(bodyRaw.get(bi)[1]);
            while (!condStack.isEmpty() && condStack.peekLast().indent() >= indent) condStack.pollLast();
            List<VisualBlockDef.Cond> conds = condStack.stream().map(CondFrame::cond).toList();

            Matcher mf = IF_FILLED.matcher(line);
            if (mf.matches()) {
                pendingLabel = null;
                condStack.addLast(new CondFrame(indent, new VisualBlockDef.CondFilled(mf.group(1))));
                continue;
            }
            if (line.startsWith("if ") && line.endsWith(":")) {
                pendingLabel = null;
                String src = line.substring(3, line.length() - 1).trim();
                VisualBlockDef.CondExpr.Node node = new CondParser(src).parse();
                condStack.addLast(new CondFrame(indent, new VisualBlockDef.CondExpr(node, src)));
                continue;
            }
            if (line.startsWith("template:")) {
                String tmpl = line.substring(9).trim().replace("\\n", "\n").replace("\\t", "\t");
                def.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.TEMPLATE, null, tmpl, conds));
                pendingLabel = null;
                continue;
            }
            if (line.startsWith("code:")) {
                def.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.CODE, null, line.substring(5).trim(), conds));
                pendingLabel = null;
                continue;
            }
            if (line.startsWith("row:")) {
                String rowStr = line.substring(4).trim();
                // Row that is only text labels followed by input:(type: value) merges into that input's label.
                if (rowStr.matches("^(\"(?:[^\"\\\\]|\\\\.)*\"\\s*)+$") && bi + 1 < bodyRaw.size()) {
                    String next = bodyRaw.get(bi + 1)[0];
                    Matcher mNext = INPUT_LINE.matcher(next);
                    if (mNext.find() && "value".equals(mNext.group(2))) {
                        pendingLabel = rowStr.replace("\"", "").trim();
                        continue;
                    }
                }
                List<VisualBlockDef.Token> tokens = parseRowTokens(rowStr);
                if (!tokens.isEmpty()) {
                    def.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.ROW, tokens, null, conds));
                }
                pendingLabel = null;
                continue;
            }
            Matcher mi = INPUT_LINE.matcher(line);
            if (mi.find()) {
                String name = mi.group(1);
                String type = mi.group(2);
                String label = mi.group(3) != null ? mi.group(3) : pendingLabel;
                VisualBlockDef.Token tok = "value".equals(type)
                        ? new VisualBlockDef.ValueInput(name, label == null ? "" : label)
                        : new VisualBlockDef.StatementInput(name, label == null ? "" : label);
                def.body.add(new VisualBlockDef.BodyItem(VisualBlockDef.ItemKind.ROW, List.of(tok), null, conds));
                pendingLabel = null;
            }
        }
        return def;
    }

    // ==================== Row tokens ====================

    private static List<VisualBlockDef.Token> parseRowTokens(String rowStr) {
        List<VisualBlockDef.Token> tokens = new ArrayList<>();
        Matcher m = ROW_TOKEN.matcher(rowStr);
        while (m.find()) {
            String tok = m.group();
            if (tok.toLowerCase(java.util.Locale.ROOT).startsWith("input:")) {
                String name = m.group(1);
                String type = m.group(2).toLowerCase(java.util.Locale.ROOT);
                tokens.add("value".equals(type)
                        ? new VisualBlockDef.ValueInput(name, "")
                        : new VisualBlockDef.StatementInput(name, ""));
            } else if (tok.startsWith("\"")) {
                tokens.add(new VisualBlockDef.Label(tok.substring(1, tok.length() - 1)));
            } else if (tok.startsWith("[")) {
                VisualBlockDef.Token t = parseBracketToken(tok.substring(1, tok.length() - 1));
                if (t != null) tokens.add(t);
            } else if (tok.startsWith("(")) {
                String inner = tok.substring(1, tok.length() - 1);
                String[] parts = inner.split(":", 3);
                String name = parts[0].trim();
                String type = parts.length > 1 ? parts[1].trim() : "text";
                String def = parts.length > 2 ? parts[2].trim() : "";
                def = def.replace("\\n", "\n").replace("\\t", "\t");
                tokens.add(new VisualBlockDef.Field(name, type, def));
            }
        }
        return tokens;
    }

    /** {@code name: dropdown(...)} | {@code name: dropdown_model} | {@code name: checkbox(true)} */
    private static VisualBlockDef.Token parseBracketToken(String inner) {
        int colon = inner.indexOf(':');
        if (colon < 0) return null;
        String name = inner.substring(0, colon).trim();
        String typeDef = inner.substring(colon + 1).trim();

        Matcher cb = Pattern.compile("^checkbox(?:\\((true|false)\\))?$", Pattern.CASE_INSENSITIVE).matcher(typeDef);
        if (cb.matches()) {
            boolean checked = cb.group(1) != null && cb.group(1).equalsIgnoreCase("true");
            return new VisualBlockDef.Checkbox(name, checked);
        }
        Matcher dd = Pattern.compile("^dropdown\\((.*)\\)$", Pattern.DOTALL).matcher(typeDef);
        if (dd.matches()) {
            List<String[]> options = new ArrayList<>();
            for (String opt : dd.group(1).split(",")) {
                String[] parts = opt.split(":");
                String label = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : label;
                if (!label.isEmpty()) options.add(new String[]{label, value});
            }
            String defVal = options.isEmpty() ? "" : options.get(0)[1];
            return new VisualBlockDef.Dropdown(name, options, null, defVal);
        }
        if (typeDef.startsWith("dropdown_")) {
            return new VisualBlockDef.Dropdown(name, List.of(), typeDef.substring("dropdown_".length()), "");
        }
        return null;
    }

    // ==================== Condition expressions ====================

    /**
     * Recursive-descent parser for {@code if} conditions:
     * {@code or > and > not > (==|!=) > primary(ident|string|paren)}.
     */
    private static final class CondParser {
        private final String s;
        private int pos;

        CondParser(String s) { this.s = s; }

        VisualBlockDef.CondExpr.Node parse() {
            VisualBlockDef.CondExpr.Node n = parseOr();
            skipWs();
            return n;
        }

        private VisualBlockDef.CondExpr.Node parseOr() {
            VisualBlockDef.CondExpr.Node l = parseAnd();
            while (matchWord("or")) l = new VisualBlockDef.CondExpr.BinOp("or", l, parseAnd());
            return l;
        }

        private VisualBlockDef.CondExpr.Node parseAnd() {
            VisualBlockDef.CondExpr.Node l = parseNot();
            while (matchWord("and")) l = new VisualBlockDef.CondExpr.BinOp("and", l, parseNot());
            return l;
        }

        private VisualBlockDef.CondExpr.Node parseNot() {
            if (matchWord("not")) return new VisualBlockDef.CondExpr.NotOp(parseNot());
            return parseCmp();
        }

        private VisualBlockDef.CondExpr.Node parseCmp() {
            VisualBlockDef.CondExpr.Node l = parsePrimary();
            skipWs();
            if (pos + 1 < s.length()) {
                String two = s.substring(pos, Math.min(pos + 2, s.length()));
                if (two.equals("==") || two.equals("!=")) {
                    pos += 2;
                    return new VisualBlockDef.CondExpr.BinOp(two, l, parsePrimary());
                }
            }
            return l;
        }

        private VisualBlockDef.CondExpr.Node parsePrimary() {
            skipWs();
            if (pos >= s.length()) return new VisualBlockDef.CondExpr.Lit("");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                VisualBlockDef.CondExpr.Node n = parseOr();
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return n;
            }
            if (c == '"' || c == '\'') {
                int end = s.indexOf(c, pos + 1);
                if (end < 0) end = s.length();
                String lit = s.substring(pos + 1, end);
                pos = Math.min(end + 1, s.length());
                return new VisualBlockDef.CondExpr.Lit(lit);
            }
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
            String word = s.substring(start, pos);
            if (word.isEmpty()) { pos++; return new VisualBlockDef.CondExpr.Lit(""); }
            if (word.equals("true") || word.equals("false")) return new VisualBlockDef.CondExpr.Lit(word);
            return new VisualBlockDef.CondExpr.Ident(word);
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private boolean matchWord(String w) {
            skipWs();
            if (s.regionMatches(pos, w, 0, w.length())) {
                int end = pos + w.length();
                boolean boundary = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
                if (boundary) { pos = end; return true; }
            }
            return false;
        }
    }
}
