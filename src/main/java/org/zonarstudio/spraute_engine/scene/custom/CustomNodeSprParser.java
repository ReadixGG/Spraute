package org.zonarstudio.spraute_engine.scene.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code declare section} / {@code declare node type} blocks from .spr source.
 * Schema-only — not executed by ScriptExecutor.
 */
public final class CustomNodeSprParser {
    private static final Pattern SECTION =
            Pattern.compile("declare\\s+section\\s+(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_TYPE =
            Pattern.compile("declare\\s+node\\s+type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSPECTOR =
            Pattern.compile("declare\\s+inspector(?:\\s+(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_]*)))?\\s*\\{",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD =
            Pattern.compile("field\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?)\\s*\\{",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST =
            Pattern.compile("list\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGN_STR =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ASSIGN_NUM =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern ASSIGN_BOOL =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGN_BARE =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");

    private CustomNodeSprParser() {}

    public static final class ParseResult {
        public final List<String> sections = new ArrayList<>();
        public final List<CustomNodeTypeDef> types = new ArrayList<>();
        public final List<CustomNodeTypeDef> inspectors = new ArrayList<>();
    }

    public static ParseResult parse(String source) {
        ParseResult out = new ParseResult();
        if (source == null || source.isBlank()) return out;

        Matcher sec = SECTION.matcher(source);
        while (sec.find()) {
            String name = unquote(sec.group(1).trim());
            if (!name.isBlank() && !out.sections.contains(name)) out.sections.add(name);
        }

        Matcher nt = NODE_TYPE.matcher(source);
        while (nt.find()) {
            String id = nt.group(1);
            int bodyStart = nt.end();
            int bodyEnd = matchingBrace(source, bodyStart - 1);
            if (bodyEnd < 0) continue;
            String body = source.substring(bodyStart, bodyEnd);
            CustomNodeTypeDef def = parseTypeBody(id, body);
            out.types.add(def);
            if (def.section != null && !def.section.isBlank() && !out.sections.contains(def.section)) {
                out.sections.add(def.section);
            }
        }

        Matcher insp = INSPECTOR.matcher(source);
        int anon = 0;
        while (insp.find()) {
            String quoted = insp.group(1);
            String ident = insp.group(2);
            String id = quoted != null && !quoted.isBlank() ? quoted
                    : ident != null && !ident.isBlank() ? ident : null;
            if (id == null || id.isBlank()) {
                anon++;
                id = anon == 1 ? "inspector" : "inspector" + anon;
            }
            int bodyStart = insp.end();
            int bodyEnd = matchingBrace(source, bodyStart - 1);
            if (bodyEnd < 0) continue;
            String body = source.substring(bodyStart, bodyEnd);
            CustomNodeTypeDef def = parseTypeBody(id, body);
            if (quoted != null && !quoted.isBlank()) {
                if (def.label == null || def.label.isBlank() || def.label.equals(def.id)) def.label = quoted;
                if (def.section == null || def.section.isBlank() || "Кастом".equals(def.section)) {
                    def.section = quoted;
                }
            }
            if (def.section == null || def.section.isBlank() || "Кастом".equals(def.section)) {
                def.section = def.inspectorTitle();
            }
            if (def.label == null || def.label.isBlank()) def.label = def.section;
            out.inspectors.add(def);
        }
        return out;
    }

    private static CustomNodeTypeDef parseTypeBody(String id, String body) {
        CustomNodeTypeDef def = new CustomNodeTypeDef();
        def.id = id;
        Map<String, Object> props = parseAssignments(body);
        if (props.containsKey("section")) def.section = String.valueOf(props.get("section"));
        if (props.containsKey("label")) def.label = String.valueOf(props.get("label"));
        if (props.containsKey("icon")) def.icon = String.valueOf(props.get("icon"));
        if (props.containsKey("runtime")) def.runtime = String.valueOf(props.get("runtime"));
        if (props.containsKey("creates")) def.creates = String.valueOf(props.get("creates"));
        if (props.containsKey("tradeList") || props.containsKey("trade_list")) {
            Object v = props.containsKey("tradeList") ? props.get("tradeList") : props.get("trade_list");
            def.tradeList = String.valueOf(v);
        }
        if (def.label == null || def.label.isBlank()) def.label = id;

        // Strip nested field/list bodies from top-level assignment scan area by walking markers.
        Matcher listM = LIST.matcher(body);
        while (listM.find()) {
            String listId = listM.group(1);
            int start = listM.end();
            int end = matchingBrace(body, start - 1);
            if (end < 0) continue;
            String listBody = body.substring(start, end);
            CustomListDef list = new CustomListDef();
            list.id = listId;
            Map<String, Object> lp = parseAssignments(listBody);
            if (lp.containsKey("label")) list.label = String.valueOf(lp.get("label"));
            else list.label = listId;
            if (lp.containsKey("addLabel") || lp.containsKey("add_label")) {
                Object v = lp.containsKey("addLabel") ? lp.get("addLabel") : lp.get("add_label");
                list.addLabel = String.valueOf(v);
            }
            list.fields.addAll(parseFields(listBody));
            def.lists.add(list);
        }

        def.fields.addAll(parseFields(body));
        // Remove fields that belong to lists (they're nested inside list braces).
        // parseFields scans whole body; filter by checking each field's position is not inside a list.
        def.fields.clear();
        def.fields.addAll(parseTopLevelFields(body));
        return def;
    }

    private static List<CustomFieldDef> parseTopLevelFields(String body) {
        List<CustomFieldDef> out = new ArrayList<>();
        List<int[]> listRanges = new ArrayList<>();
        Matcher listM = LIST.matcher(body);
        while (listM.find()) {
            int start = listM.start();
            int end = matchingBrace(body, listM.end() - 1);
            if (end >= 0) listRanges.add(new int[]{start, end + 1});
        }
        Matcher fieldM = FIELD.matcher(body);
        while (fieldM.find()) {
            int fs = fieldM.start();
            boolean insideList = false;
            for (int[] r : listRanges) {
                if (fs >= r[0] && fs < r[1]) {
                    insideList = true;
                    break;
                }
            }
            if (insideList) continue;
            int start = fieldM.end();
            int end = matchingBrace(body, start - 1);
            if (end < 0) continue;
            out.add(parseField(fieldM.group(1), fieldM.group(2), body.substring(start, end)));
        }
        return out;
    }

    private static List<CustomFieldDef> parseFields(String body) {
        List<CustomFieldDef> out = new ArrayList<>();
        Matcher fieldM = FIELD.matcher(body);
        while (fieldM.find()) {
            int start = fieldM.end();
            int end = matchingBrace(body, start - 1);
            if (end < 0) continue;
            // Skip nested fields that are themselves inside another field (rare).
            out.add(parseField(fieldM.group(1), fieldM.group(2), body.substring(start, end)));
        }
        return out;
    }

    private static CustomFieldDef parseField(String id, String kindRaw, String body) {
        CustomFieldDef f = new CustomFieldDef();
        f.id = id;
        String kind = kindRaw;
        int paren = kindRaw.indexOf('(');
        if (paren > 0 && kindRaw.endsWith(")")) {
            kind = kindRaw.substring(0, paren);
            String opts = kindRaw.substring(paren + 1, kindRaw.length() - 1);
            for (String part : opts.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                int colon = p.indexOf(':');
                if (colon > 0) {
                    String label = unquote(p.substring(0, colon).trim());
                    String value = unquote(p.substring(colon + 1).trim());
                    f.options.put(label, value);
                } else {
                    String v = unquote(p);
                    f.options.put(v, v);
                }
            }
        }
        f.kind = CustomFieldKind.parse(kind);
        if (!f.options.isEmpty() && f.kind == CustomFieldKind.TEXT) {
            f.kind = CustomFieldKind.DROPDOWN;
        }
        Map<String, Object> props = parseAssignments(body);
        if (props.containsKey("label")) f.label = String.valueOf(props.get("label"));
        else f.label = id;
        if (props.containsKey("default")) f.defaultValue = props.get("default");
        if (props.containsKey("appendList") || props.containsKey("append_list")) {
            Object v = props.containsKey("appendList") ? props.get("appendList") : props.get("append_list");
            f.appendList = String.valueOf(v);
        }
        return f;
    }

    private static Map<String, Object> parseAssignments(String body) {
        Map<String, Object> props = new LinkedHashMap<>();
        // Prefer string / bool / number; skip nested braces by removing field/list bodies first.
        String flat = stripNestedBlocks(body);
        Matcher ms = ASSIGN_STR.matcher(flat);
        while (ms.find()) props.put(ms.group(1), ms.group(2));
        Matcher mb = ASSIGN_BOOL.matcher(flat);
        while (mb.find()) {
            if (!props.containsKey(mb.group(1))) {
                props.put(mb.group(1), Boolean.parseBoolean(mb.group(2)));
            }
        }
        Matcher mn = ASSIGN_NUM.matcher(flat);
        while (mn.find()) {
            if (!props.containsKey(mn.group(1))) {
                String n = mn.group(2);
                if (n.contains(".")) props.put(mn.group(1), Double.parseDouble(n));
                else props.put(mn.group(1), Integer.parseInt(n));
            }
        }
        Matcher mBare = ASSIGN_BARE.matcher(flat);
        while (mBare.find()) {
            String key = mBare.group(1);
            if (props.containsKey(key)) continue;
            String val = mBare.group(2);
            if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) continue;
            props.put(key, val);
        }
        return props;
    }

    /** Remove {@code field … {…}} and {@code list … {…}} bodies so top-level assigns stay clean. */
    private static String stripNestedBlocks(String body) {
        StringBuilder sb = new StringBuilder(body);
        // Repeatedly remove innermost-ish by finding field/list and cutting to matching brace.
        boolean changed = true;
        while (changed) {
            changed = false;
            Matcher m = Pattern.compile("(field|list)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+(?:[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?\\s*)?\\{",
                    Pattern.CASE_INSENSITIVE).matcher(sb);
            if (m.find()) {
                int open = m.end() - 1;
                int close = matchingBrace(sb.toString(), open);
                if (close >= 0) {
                    sb.replace(m.start(), close + 1, " ");
                    changed = true;
                }
            }
        }
        return sb.toString();
    }

    static int matchingBrace(String text, int openIndex) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != '{') return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
