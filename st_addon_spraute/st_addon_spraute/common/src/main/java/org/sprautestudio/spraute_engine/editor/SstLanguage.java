package org.sprautestudio.spraute_engine.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.CommandSpec;
import org.sprautestudio.spraute_engine.lang.Diagnostic;
import org.sprautestudio.spraute_engine.lang.Lexer;
import org.sprautestudio.spraute_engine.lang.ParamType;
import org.sprautestudio.spraute_engine.lang.Specs;
import org.sprautestudio.spraute_engine.lang.Token;
import org.sprautestudio.spraute_engine.lang.TokenType;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.code.LanguageSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Editor services for the Spraute Story language. */
public final class SstLanguage implements LanguageSupport {
    /** Supplies "(x, y, z)" of the player for location completion, may be null. */
    public Supplier<String> positionProvider;
    /** Ids of scripts known in the game (for "ensure active" etc.). */
    public Supplier<List<Integer>> knownScripts = List::of;

    private static List<String> sounds, items, effects, entities;
    private static Analyzer.Registries registries;

    private static void loadRegistries() {
        if (sounds != null) return;
        sounds = BuiltInRegistries.SOUND_EVENT.keySet().stream().map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        items = BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        effects = BuiltInRegistries.MOB_EFFECT.keySet().stream().map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        entities = BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        Set<String> s = new HashSet<>(sounds), i = new HashSet<>(items), e = new HashSet<>(effects), en = new HashSet<>(entities);
        registries = new Analyzer.Registries();
        registries.sounds = s::contains;
        registries.items = i::contains;
        registries.effects = e::contains;
        registries.entities = en::contains;
        registries.models = name -> org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.find(null, name) != null
                || org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.find(null, name) != null;
        registries.guis = name -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectGuis.get().containsKey(name);
        registries.guiElements = id -> guiElementIds().contains(id);
        registries.images = name -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectImages.get().contains(name);
        registries.projectSounds = name -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectSounds.get().contains(name);
        registries.animations = id -> customAnimation(id) || org.sprautestudio.spraute_engine.runtime.Anims.all().isEmpty()
                || org.sprautestudio.spraute_engine.runtime.Anims.exists(id);
    }

    private static List<String> animationIds() {
        List<String> ids = new ArrayList<>();
        for (org.sprautestudio.spraute_engine.runtime.Anims.Entry e : org.sprautestudio.spraute_engine.runtime.Anims.all()) ids.add(e.id());
        return ids;
    }

    // ================================================================ analysis

    public static final class SstAnalysis implements Analysis {
        public final Analyzer.Result result;
        private final List<Problem> problems = new ArrayList<>();
        private final int[] colors;

        SstAnalysis(String text) {
            loadRegistries();
            this.result = Analyzer.analyze(text, registries);
            for (Diagnostic d : result.diagnostics) {
                Severity sev = d.severity == Diagnostic.Severity.ERROR ? Severity.ERROR
                        : d.severity == Diagnostic.Severity.WARNING ? Severity.WARNING : Severity.INFO;
                problems.add(new Problem(d.start, d.end, sev, d.message));
            }
            this.colors = highlight(text, result);
        }

        @Override
        public List<Problem> problems() {
            return problems;
        }

        @Override
        public int[] colors() {
            return colors;
        }
    }

    @Override
    public Analysis analyze(String text) {
        return new SstAnalysis(text);
    }

    private static Analyzer.Result result(Analysis a) {
        return a instanceof SstAnalysis s ? s.result : Analyzer.analyze("");
    }

    // ============================================================ highlighting

    static int[] highlight(String text, Analyzer.Result r) {
        int[] colors = new int[text.length()];
        Arrays.fill(colors, Theme.CODE_IDENT);
        List<Token> tokens = Lexer.tokenizeAll(text);
        boolean statementStart = true;
        String head = null;
        CommandSpec spec = null;
        int tokenInStatement = 0;
        boolean scriptLine = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            int color;
            switch (t.type) {
                case COMMENT -> color = Theme.CODE_COMMENT;
                case STRING -> color = Theme.CODE_STRING;
                case NUMBER, DURATION -> color = Theme.CODE_NUMBER;
                case KEYWORD -> color = tokenInStatement == 1 && head != null && Specs.command(head + " " + t.text) != null
                        ? Theme.CODE_COMMAND
                        : t.text.equals("true") || t.text.equals("false") || t.text.equals("player")
                        ? Theme.CODE_NUMBER : t.text.equals("and") || t.text.equals("or") || t.text.equals("not")
                        ? Theme.CODE_OPERATOR : Theme.CODE_KEYWORD;
                case OPERATOR, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, LBRACE, RBRACE -> color = Theme.CODE_OPERATOR;
                case ERROR -> color = Theme.CODE_ERROR;
                case IDENT -> {
                    color = Theme.CODE_IDENT;
                    Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                    if (statementStart && Specs.isCommandHead(t.text)) {
                        color = Theme.CODE_COMMAND;
                        head = t.text;
                        spec = Specs.command(t.text);
                    } else if (tokenInStatement == 1 && head != null && Specs.command(head + " " + t.text) != null) {
                        color = Theme.CODE_COMMAND;
                        spec = Specs.command(head + " " + t.text);
                    } else if (next != null && next.type == TokenType.LPAREN && next.start == t.end && Specs.function(t.text) != null) {
                        color = Theme.CODE_FUNCTION;
                    } else if (r.characters.containsKey(t.text)) {
                        color = Theme.CODE_CHARACTER;
                    } else if (r.locations.containsKey(t.text)) {
                        color = Theme.CODE_LOCATION;
                    } else if (r.labels.containsKey(t.text) || r.branches.containsKey(t.text)) {
                        color = Theme.CODE_LABEL;
                    } else if (r.functions.containsKey(t.text)) {
                        color = Theme.CODE_FUNCTION;
                    } else if (r.vars.containsKey(t.text)) {
                        color = Theme.CODE_VARIABLE;
                    } else if (spec != null && isGroupKeyword(spec, t.text)) {
                        color = Theme.CODE_PARAM_KW;
                    } else if (t.text.equals("as") && (tokenInStatement >= 2 || statementStart)) {
                        color = Theme.CODE_KEYWORD;
                    } else if (scriptLine && (t.text.equals("solo") || t.text.equals("autostart") || t.text.equals("once"))) {
                        color = Theme.CODE_KEYWORD;
                    } else if (t.text.equals("actor")) {
                        color = Theme.CODE_NUMBER;
                    } else if (t.text.equals("name") || t.text.equals("speed") || t.text.equals("skin") || t.text.equals("existing")
                            || t.text.equals("yaw") || t.text.equals("pitch") || t.text.equals("dim") || t.text.equals("emotion")) {
                        color = Theme.CODE_PARAM_KW;
                    }
                }
                default -> color = Theme.CODE_IDENT;
            }
            if (t.type != TokenType.NEWLINE && t.type != TokenType.EOF) {
                for (int k = t.start; k < t.end && k < colors.length; k++) colors[k] = color;
                if (t.type == TokenType.STRING) highlightInterpolation(text, t, colors);
            }
            if (t.type == TokenType.COMMENT) continue;
            if (t.type == TokenType.NEWLINE || t.type == TokenType.LBRACE || t.type == TokenType.RBRACE) {
                statementStart = true;
                head = null;
                spec = null;
                tokenInStatement = 0;
                scriptLine = false;
            } else {
                if (statementStart && t.text.equals("script")) scriptLine = true;
                statementStart = false;
                tokenInStatement++;
            }
        }
        return colors;
    }

    private static void highlightInterpolation(String text, Token t, int[] colors) {
        int depth = -1;
        for (int k = t.start + 1; k < t.end - 1; k++) {
            char ch = text.charAt(k);
            if (ch == '\\') {
                k++;
                continue;
            }
            if (ch == '{') depth = k;
            if (depth >= 0) colors[k] = Theme.CODE_INTERP;
            if (ch == '}') depth = -1;
        }
    }

    private static boolean isGroupKeyword(CommandSpec spec, String word) {
        for (CommandSpec.Group g : spec.groups) if (word.equals(g.keyword)) return true;
        return false;
    }

    // ============================================================ context

    /** What the caret is positioned at inside a statement. */
    private static final class Context {
        List<Token> tokens = new ArrayList<>(); // tokens of the current statement before the typed word
        int wordStart;
        String typed = "";
        boolean inString;
        int stringContentStart;
        CommandSpec spec;
        int specTokens; // tokens consumed by the command name
    }

    private static Context context(String text, int caret) {
        Context ctx = new Context();
        // statement start: last newline / brace before caret outside strings
        int ls = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        String line = text.substring(ls, caret);
        List<Token> lineTokens = Lexer.tokenizeAll(line);
        List<Token> stmt = new ArrayList<>();
        for (Token t : lineTokens) {
            if (t.type == TokenType.EOF || t.type == TokenType.NEWLINE) continue;
            if (t.type == TokenType.LBRACE || t.type == TokenType.RBRACE) {
                stmt.clear();
                continue;
            }
            stmt.add(t);
        }
        // string being typed: unterminated string token at the end
        if (!stmt.isEmpty()) {
            Token last = stmt.get(stmt.size() - 1);
            if (last.type == TokenType.ERROR && last.text.startsWith("\"")) {
                ctx.inString = true;
                ctx.stringContentStart = ls + last.start + 1;
                ctx.typed = line.substring(last.start + 1);
                ctx.wordStart = ctx.stringContentStart;
                stmt.remove(stmt.size() - 1);
                ctx.tokens = stmt;
                resolveSpec(ctx);
                return ctx;
            }
        }
        int ws = caret;
        while (ws > ls && isWord(text.charAt(ws - 1))) ws--;
        ctx.wordStart = ws;
        ctx.typed = text.substring(ws, caret);
        // drop the token being typed
        if (!stmt.isEmpty() && !ctx.typed.isEmpty()) {
            Token last = stmt.get(stmt.size() - 1);
            if (ls + last.end == caret) stmt.remove(stmt.size() - 1);
        }
        ctx.tokens = stmt;
        resolveSpec(ctx);
        return ctx;
    }

    private static void resolveSpec(Context ctx) {
        List<Token> t = ctx.tokens;
        if (t.isEmpty() || t.get(0).type != TokenType.IDENT) return;
        if (t.size() >= 2 && (t.get(1).type == TokenType.IDENT || t.get(1).type == TokenType.KEYWORD)) {
            CommandSpec s = Specs.command(t.get(0).text + " " + t.get(1).text);
            if (s != null) {
                ctx.spec = s;
                ctx.specTokens = 2;
                return;
            }
        }
        ctx.spec = Specs.command(t.get(0).text);
        ctx.specTokens = 1;
    }

    private static boolean isWord(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    /** Result of matching the already typed arguments against a spec. */
    private record ArgState(CommandSpec.Param next, String keywordNeeded, List<String> freeKeywords, int activePart) {}

    private static ArgState argState(CommandSpec spec, List<Token> args) {
        // collapse tuples/lists into single values
        List<Token> values = new ArrayList<>();
        int depth = 0;
        for (Token t : args) {
            if (t.type == TokenType.LPAREN || t.type == TokenType.LBRACKET) {
                if (depth == 0) values.add(t);
                depth++;
            } else if (t.type == TokenType.RPAREN || t.type == TokenType.RBRACKET) {
                depth--;
            } else if (depth == 0 && !(t.type == TokenType.OPERATOR && t.text.equals("-"))) {
                values.add(t);
            }
        }
        List<CommandSpec.Group> groups = spec.groups;
        boolean[] used = new boolean[groups.size()];
        CommandSpec.Group current = null;
        int paramIdx = 0;
        int currentGroupIndex = -1;
        for (Token v : values) {
            int kw = -1;
            if (v.type == TokenType.IDENT || v.type == TokenType.KEYWORD) {
                for (int i = 0; i < groups.size(); i++) {
                    if (!used[i] && v.text.equals(groups.get(i).keyword)) {
                        kw = i;
                        break;
                    }
                }
            }
            if (kw >= 0) {
                used[kw] = true;
                current = groups.get(kw);
                currentGroupIndex = kw;
                paramIdx = 0;
                continue;
            }
            if (current != null && paramIdx < current.params.size()) {
                if (current.params.get(paramIdx).vararg) continue;
                paramIdx++;
                if (paramIdx < current.params.size()) continue;
                current = null;
                continue;
            }
            current = null;
            for (int i = 0; i < groups.size(); i++) {
                if (!used[i] && groups.get(i).keyword == null) {
                    used[i] = true;
                    currentGroupIndex = i;
                    if (groups.get(i).params.get(0).vararg) {
                        current = groups.get(i);
                        paramIdx = 0;
                    }
                    break;
                }
            }
        }
        CommandSpec.Param next = null;
        String needKw = null;
        int active = -1;
        if (current != null && paramIdx < current.params.size()) {
            next = current.params.get(paramIdx);
            active = currentGroupIndex;
        } else {
            for (int i = 0; i < groups.size(); i++) {
                if (used[i]) continue;
                CommandSpec.Group g = groups.get(i);
                if (g.keyword == null) {
                    next = g.params.get(0);
                    active = i;
                    break;
                }
                if (!g.optional) {
                    needKw = g.keyword;
                    active = i;
                    break;
                }
            }
        }
        List<String> free = new ArrayList<>();
        boolean requiredLeft = false;
        for (int i = 0; i < groups.size(); i++) if (!used[i] && !groups.get(i).optional) requiredLeft = true;
        for (int i = 0; i < groups.size(); i++) {
            if (!used[i] && groups.get(i).keyword != null && (groups.get(i).optional ? !requiredLeft || next == null : true)) {
                free.add(groups.get(i).keyword);
            }
        }
        return new ArgState(next, needKw, free, active);
    }

    // ============================================================ completion

    @Override
    public List<Completion> complete(String text, int caret, Analysis analysis, boolean explicit) {
        Analyzer.Result r = result(analysis);
        Context ctx = context(text, caret);
        List<Completion> out = new ArrayList<>();
        int rs = ctx.wordStart;
        String typed = ctx.typed;

        if (ctx.inString) {
            ParamType type = stringParamType(ctx);
            if (type == null) return List.of();
            loadRegistries();
            List<String> pool = switch (type) {
                case SOUND -> sounds;
                case ITEM -> items;
                case EFFECT -> effects;
                case ENTITY -> {
                    List<String> l = new java.util.ArrayList<>();
                    for (var t : org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.all()) l.add("spraute:" + t.name());
                    l.addAll(merge(Specs.ST_ENTITIES, entities));
                    yield l;
                }
                case ANIM -> animationIds();
                case GUI -> new ArrayList<>(org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectGuis.get().keySet());
                case GUIEL -> guiElementIds();
                case IMAGE -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.imageChoices();
                default -> List.of();
            };
            for (String id : pool) out.add(new Completion(id, Kind.VALUE, type.title, id, null, rs, 1));
            return filter(out, typed, 200);
        }

        List<Token> toks = ctx.tokens;
        if (toks.isEmpty()) {
            if (typed.isEmpty() && !explicit) return List.of();
            statementCompletions(out, rs, r);
            return filter(out, typed, 80);
        }
        Token first = toks.get(0);
        if (first.type == TokenType.KEYWORD) {
            switch (first.text) {
                case "script" -> {
                    if (toks.size() >= 2) {
                        Set<String> written = new HashSet<>();
                        for (Token w : toks) written.add(w.text);
                        if (!written.contains("solo")) out.add(new Completion("solo", Kind.KEYWORD, "у каждого игрока свой", "solo", Specs.keywordDocs().get("solo"), rs, 0));
                        if (!written.contains("autostart")) {
                            out.add(new Completion("autostart", Kind.KEYWORD, "сам при входе", "autostart", Specs.keywordDocs().get("autostart"), rs, 0));
                            out.add(new Completion("autostart once", Kind.KEYWORD, "сам один раз", "autostart once", Specs.keywordDocs().get("once"), rs, 1));
                        } else if (!written.contains("once") && toks.get(toks.size() - 1).text.equals("autostart")) {
                            out.add(new Completion("once", Kind.KEYWORD, "только первый вход", "once", Specs.keywordDocs().get("once"), rs, 0));
                        }
                    }
                }
                case "goto" -> {
                    if (toks.size() == 1) for (Analyzer.Symbol s : r.labels.values()) out.add(sym(s, rs));
                }
                case "call" -> {
                    if (toks.size() == 1) functionCompletions(out, rs, r);
                    else exprCompletions(out, rs, r);
                }
                case "async" -> {
                    if (toks.size() == 1) {
                        out.add(new Completion("call", Kind.KEYWORD, "функция в фоне", "call ", Specs.keywordDocs().get("async"), rs, 0));
                        out.add(new Completion("{ }", Kind.KEYWORD, "фоновая ветка", "{\n    $0\n}", "Анонимная асинхронная ветка", rs, 1));
                        out.add(new Completion("имя { }", Kind.KEYWORD, "ветка с именем", "${1:ветка} {\n    $0\n}", "Ветка с именем — её можно ждать (await) и отменять (cancel)", rs, 1));
                    } else if (toks.size() == 2 && toks.get(1).text.equals("call")) {
                        functionCompletions(out, rs, r);
                    } else if (toks.size() >= 3 && toks.get(1).text.equals("call")) {
                        out.add(new Completion("as", Kind.KEYWORD, "имя ветки", "as ${1:job}", "Дать фоновому вызову имя для await/cancel", rs, 0));
                        exprCompletions(out, rs, r);
                    }
                }
                case "on" -> {
                    if (toks.size() == 1) {
                        for (CommandSpec s : Specs.withHead("wait")) {
                            if (s.sub == null || s.sub.equals("random") || s.sub.equals("until")) continue;
                            String rest = s.snippet().substring(s.head.length() + 1);
                            out.add(new Completion(s.sub, Kind.KEYWORD, "событие", rest + " {\n    $0\n}", "on " + s.signature().substring(5) + "\n" + s.doc, rs, 0));
                        }
                    }
                }
                case "switch" -> {
                    if (toks.size() == 1) exprCompletions(out, rs, r);
                }
                case "await", "cancel" -> {
                    for (Analyzer.Symbol s : r.branches.values()) out.add(sym(s, rs));
                }
                case "set" -> {
                    if (toks.size() == 1) for (Analyzer.Symbol s : r.vars.values()) out.add(sym(s, rs));
                    else if (toks.size() >= 3) exprCompletions(out, rs, r);
                    else if (toks.size() == 2) {
                        out.add(new Completion("=", Kind.KEYWORD, null, "= ", null, rs, 1));
                        out.add(new Completion("+=", Kind.KEYWORD, null, "+= ", null, rs, 1));
                        out.add(new Completion("-=", Kind.KEYWORD, null, "-= ", null, rs, 1));
                    }
                }
                case "if", "while", "repeat" -> exprCompletions(out, rs, r);
                case "choice" -> {
                    if (toks.size() == 1) for (Analyzer.Symbol s : r.characters.values()) out.add(sym(s, rs));
                    else if (toks.size() == 3) out.add(new Completion("emotion", Kind.KEYWORD, "эмоция", "emotion ", "Эмоция говорящего в диалоге", rs, 1));
                    else if (toks.size() == 4 && toks.get(3).text.equals("emotion")) emotions(out, rs);
                }
                case "character" -> {
                    if (toks.size() == 3) {
                        out.add(new Completion("existing", Kind.KEYWORD, "готовый персонаж ST", "existing \"${1:id}\"", "Использовать персонажа, уже описанного в Spraute", rs, 0));
                        for (String e : Specs.ST_ENTITIES) out.add(new Completion("\"" + e + "\"", Kind.VALUE, "сущность", "\"" + e + "\"", null, rs, 1));
                    } else if (toks.size() >= 4) {
                        out.add(new Completion("name", Kind.KEYWORD, "имя", "name \"${1:Имя}\"", "Отображаемое имя персонажа", rs, 0));
                        out.add(new Completion("speed", Kind.KEYWORD, "скорость", "speed ${1:0.3}", "Стандартная скорость ходьбы", rs, 0));
                        out.add(new Completion("skin", Kind.KEYWORD, "текстура", "skin \"${1:spraute_engine:textures/entity/humanoid.png}\"", "Текстура персонажа", rs, 0));
                        for (String ic : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.imageChoices()) {
                            out.add(new Completion("icon \"" + ic + "\"", Kind.KEYWORD, "иконка речи", "icon \"" + ic + "\"", "Картинка в окне речи (say)", rs, 1));
                            out.add(new Completion("portrait \"" + ic + "\"", Kind.KEYWORD, "лицо в диалоге", "portrait \"" + ic + "\"", "Лицо в диалоге с выбором (choice)", rs, 1));
                        }
                    }
                }
                case "location" -> {
                    if (toks.size() == 3) locationLiterals(out, rs);
                    else if (toks.size() >= 4) {
                        out.add(new Completion("yaw", Kind.KEYWORD, "поворот", "yaw ${1:0}", "Поворот по горизонтали (градусы)", rs, 0));
                        out.add(new Completion("pitch", Kind.KEYWORD, "наклон", "pitch ${1:0}", "Наклон головы (градусы)", rs, 0));
                        out.add(new Completion("dim", Kind.KEYWORD, "измерение", "dim \"${1:minecraft:overworld}\"", "Измерение мира", rs, 0));
                    }
                }
                default -> {
                }
            }
            return filter(out, typed, 80);
        }
        if (first.type != TokenType.IDENT) return List.of();
        if (first.text.equals("as") && toks.size() == 1) {
            exprCompletions(out, rs, r);
            return filter(out, typed, 80);
        }

        // sub-command after a head ("wait " -> flag/until/...)
        if (toks.size() == 1 && Specs.withHead(first.text).size() > 1) {
            for (CommandSpec s : Specs.withHead(first.text)) {
                if (s.sub == null) continue;
                String rest = s.snippet().substring(s.head.length() + 1);
                out.add(new Completion(s.sub, Kind.COMMAND, s.signature().substring(s.head.length() + 1), rest, s.doc, rs, 0));
            }
        }
        CommandSpec spec = ctx.spec;
        if (spec == null) return filter(out, typed, 80);
        if (ctx.specTokens == 1 && spec.sub == null && toks.size() == 1 && Specs.withHead(first.text).size() > 1 && typed.isEmpty() && !explicit) {
            // "wait " — show sub-commands plus duration values
            durations(out, rs);
            return filter(out, typed, 80);
        }
        ArgState st = argState(spec, toks.subList(ctx.specTokens, toks.size()));
        if (st.keywordNeeded != null) {
            out.add(new Completion(st.keywordNeeded, Kind.KEYWORD, "обязательно", st.keywordNeeded + " ", null, rs, 0));
        }
        if (st.next != null) valueCompletions(out, st.next.type, rs, r);
        for (String kw : st.freeKeywords) {
            if (kw.equals(st.keywordNeeded)) continue;
            CommandSpec.Group g = null;
            for (CommandSpec.Group gg : spec.groups) if (kw.equals(gg.keyword)) g = gg;
            String detail = g != null ? g.params.stream().map(p -> p.type.title).collect(Collectors.joining(" ")) : null;
            out.add(new Completion(kw, Kind.KEYWORD, detail, kw + " ", null, rs, 2));
        }
        return filter(out, typed, 120);
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> list = new ArrayList<>(a);
        for (String s : b) if (!list.contains(s)) list.add(s);
        return list;
    }

    /**
     * What a quoted value here means, for the code editor's pickers: "color", "hitbox", or a ParamType name in lower case
     * ("item", "entity", "anim", "sound", "image", "quest", "shop", "zone", "fight", "cutscene", "gui", "vfx"…); null — unknown.
     * {@code quote} is the offset of the value's opening quote.
     */
    public static String valueKindAt(String text, int quote) {
        Context ctx = context(text, quote);
        List<Token> t = ctx.tokens;
        if (t.isEmpty()) return null;
        if (t.get(0).isWord("character")) {
            if (t.size() == 3) return "entity";
            return switch (t.get(t.size() - 1).text) {
                case "icon", "portrait", "skin" -> "image";
                case "color" -> "color";
                case "hitbox" -> "hitbox";
                default -> null;
            };
        }
        if (ctx.spec == null) return null;
        if (ctx.spec.key.equals("hitbox")) return "hitbox";
        ArgState st = argState(ctx.spec, t.subList(ctx.specTokens, t.size()));
        if (st.next == null) return null;
        if (st.next.name.equals("color")) return "color";
        return st.next.type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ParamType stringParamType(Context ctx) {
        List<Token> t = ctx.tokens;
        if (t.isEmpty()) return null;
        if (t.get(0).isWord("character") && t.size() == 3) return ParamType.ENTITY;
        if (ctx.spec == null) return null;
        ArgState st = argState(ctx.spec, t.subList(ctx.specTokens, t.size()));
        if (st.next == null) return null;
        return switch (st.next.type) {
            case SOUND, ITEM, EFFECT, ENTITY, ANIM, GUI, GUIEL, IMAGE -> st.next.type;
            default -> null;
        };
    }

    private void statementCompletions(List<Completion> out, int rs, Analyzer.Result r) {
        Set<String> heads = new HashSet<>();
        for (CommandSpec s : Specs.commands()) {
            if (s.sub != null && Specs.command(s.head) == null && heads.add(s.head)) {
                out.add(new Completion(s.head, Kind.COMMAND, "…", s.head + " ", "Варианты: " + Specs.withHead(s.head).stream()
                        .map(x -> x.sub).collect(Collectors.joining(", ")), rs, 1));
            }
            out.add(new Completion(s.key, Kind.COMMAND, s.category, s.snippet(), s.signature() + "\n\n" + s.doc + paramDocs(s), rs, s.sub == null ? 1 : 2));
        }
        out.add(snippet("label", "label ${1:имя}", rs));
        out.add(snippet("goto", "goto ${1:метка}", rs));
        out.add(snippet("if", "if ${1:условие} {\n    $0\n}", rs));
        out.add(snippet("if else", "if ${1:условие} {\n    $2\n} else {\n    $0\n}", rs));
        out.add(snippet("while", "while ${1:условие} {\n    $0\n}", rs));
        out.add(snippet("repeat", "repeat ${1:3} {\n    $0\n}", rs));
        out.add(snippet("for", "for ${1:x} in ${2:список} {\n    $0\n}", rs));
        out.add(snippet("script autostart", "script ${1:5001} \"${2:Название}\" autostart", rs));
        out.add(snippet("script solo", "script ${1:5001} \"${2:Название}\" solo", rs));
        out.add(snippet("as", "as ${1:actor} {\n    $0\n}", rs));
        out.add(snippet("for players", "for p in players() {\n    as p {\n        $0\n    }\n}", rs));
        out.add(snippet("for range", "for ${1:i} in range(${2:10}) {\n    $0\n}", rs));
        out.add(snippet("every", "every ${1:20t} {\n    $0\n}", rs));
        out.add(snippet("on tick", "on tick {\n    $0\n}", rs));
        out.add(snippet("on signal", "on signal \"${1:имя}\" {\n    $0\n}", rs));
        out.add(snippet("emit", "emit \"${1:имя}\" with ${2:значение}", rs));
        out.add(snippet("var list", "var ${1:items} = []", rs));
        out.add(snippet("var dict", "var ${1:data} = {${2:ключ}: ${3:значение}}", rs));
        out.add(snippet("set", "set ${1:переменная} = ${2:значение}", rs));
        out.add(snippet("choice", "choice ${1:персонаж} \"${2:Вопрос?}\" {\n    \"${3:Да}\" {\n        $0\n    }\n    \"${4:Нет}\" {\n    }\n}", rs));
        out.add(snippet("parallel", "parallel ${1:ветка} {\n    $0\n}", rs));
        out.add(snippet("func", "func ${1:имя}(${2}) {\n    $0\n}", rs));
        out.add(snippet("call", "call ${1:функция}()", rs));
        out.add(snippet("async", "async {\n    $0\n}", rs));
        out.add(snippet("async call", "async call ${1:функция}() as ${2:job}", rs));
        out.add(snippet("await", "await ${1:ветка}", rs));
        out.add(snippet("cancel", "cancel ${1:ветка}", rs));
        out.add(snippet("return", "return", rs));
        out.add(snippet("on button", "on button \"${1:yes}\" {\n    $0\n}", rs));
        out.add(snippet("on key", "on key ${1:H} {\n    $0\n}", rs));
        out.add(snippet("on click", "on click ${1:персонаж} {\n    $0\n}", rs));
        out.add(snippet("switch", "switch ${1:button()} {\n    \"${2:yes}\" {\n        $0\n    }\n    else {\n    }\n}", rs));
        for (Analyzer.Symbol f : r.functions.values()) {
            out.add(new Completion("call " + f.name(), Kind.FUNCTION, "функция" + f.detail(), callSnippet(f), "Вызвать функцию " + f.name() + f.detail(), rs, 1));
        }
        out.add(snippet("stop", "stop", rs));
        out.add(snippet("character", "character ${1:имя} = \"${2:spraute:npc}\" name \"${3:Имя}\"", rs));
        out.add(snippet("location", "location ${1:точка} = ${2:(0, 64, 0)}", rs));
        out.add(snippet("var", "var ${1:имя} = ${2:0}", rs));
        out.add(snippet("script", "script ${1:5001} \"${2:Название}\"", rs));
    }

    private void functionCompletions(List<Completion> out, int rs, Analyzer.Result r) {
        for (Analyzer.Symbol f : r.functions.values()) {
            String insert = callSnippet(f).substring("call ".length());
            out.add(new Completion(f.name(), Kind.FUNCTION, f.detail(), insert, "Функция " + f.name() + f.detail(), rs, 0));
        }
    }

    /** "call name(${1:a}, ${2:b})" for a function symbol whose detail is "(a, b)". */
    private static String callSnippet(Analyzer.Symbol f) {
        String inner = f.detail() == null ? "" : f.detail().replace("(", "").replace(")", "").trim();
        if (inner.isEmpty()) return "call " + f.name() + "()";
        StringBuilder sb = new StringBuilder("call ").append(f.name()).append('(');
        String[] ps = inner.split(",\\s*");
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("${").append(i + 1).append(':').append(ps[i]).append('}');
        }
        return sb.append(')').toString();
    }

    private static Completion snippet(String keyword, String body, int rs) {
        return new Completion(keyword, Kind.KEYWORD, "ключевое слово", body, Specs.keywordDocs().getOrDefault(keyword.split(" ")[0], ""), rs, 0);
    }

    private static String paramDocs(CommandSpec s) {
        StringBuilder sb = new StringBuilder();
        for (CommandSpec.Param p : s.allParams()) {
            sb.append("\n• ").append(p.name).append(" — ").append(p.type.title);
        }
        return sb.length() == 0 ? "" : "\n" + sb;
    }

    private Completion sym(Analyzer.Symbol s, int rs) {
        Kind k = switch (s.kind()) {
            case CHARACTER -> Kind.CHARACTER;
            case LOCATION -> Kind.LOCATION;
            case LABEL -> Kind.LABEL;
            case VARIABLE -> Kind.VARIABLE;
            case PARALLEL -> Kind.LABEL;
            case FUNCTION -> Kind.FUNCTION;
        };
        return new Completion(s.name(), k, s.detail(), s.name(), null, rs, 0);
    }

    /** Ready ways to say "where": in front on the ground, a shift from the player, where he looks… */
    private static void placeSnippets(List<Completion> out, int rs) {
        out.add(new Completion("infront(player, 3)", Kind.FUNCTION, "перед игроком, на земле", "infront(${1:player}, ${2:3})",
                "Точка в 3 блоках перед игроком, стоящая на полу.", rs, 1));
        out.add(new Completion("(player.x + 5, player.y, player.z)", Kind.VALUE, "сдвиг от игрока", "(player.x + ${1:5}, player.y, player.z${2})",
                "Координаты, посчитанные от игрока.", rs, 1));
        out.add(new Completion("aimpoint(player, 20)", Kind.FUNCTION, "куда смотрит игрок", "aimpoint(player, ${1:20})", null, rs, 1));
        out.add(new Completion("ground((x, y, z))", Kind.FUNCTION, "точку — на пол", "ground(${1:(player.x + 5, player.y, player.z)})", null, rs, 2));
        out.add(new Completion("vadd(player.pos, vmul(player.look, 3))", Kind.FUNCTION, "вдоль взгляда (может быть в воздухе)",
                "vadd(player.pos, vmul(player.look, ${1:3}))", null, rs, 2));
    }

    private void valueCompletions(List<Completion> out, ParamType type, int rs, Analyzer.Result r) {
        switch (type) {
            case CHAR -> r.characters.values().forEach(s -> out.add(sym(s, rs)));
            case LOC -> {
                r.locations.values().forEach(s -> out.add(sym(s, rs)));
                placeSnippets(out, rs);
                locationLiterals(out, rs);
            }
            case TARGET -> {
                r.characters.values().forEach(s -> out.add(sym(s, rs)));
                r.locations.values().forEach(s -> out.add(sym(s, rs)));
                out.add(new Completion("player", Kind.KEYWORD, "игрок", "player", "Игрок", rs, 1));
                out.add(new Completion("actor", Kind.KEYWORD, "игрок события", "actor", Specs.keywordDocs().get("actor"), rs, 1));
                placeSnippets(out, rs);
                locationLiterals(out, rs);
            }
            case SPEAKER -> {
                r.characters.values().forEach(s -> out.add(sym(s, rs)));
                out.add(new Completion("0", Kind.VALUE, "слот слева", "0", null, rs, 1));
                out.add(new Completion("1", Kind.VALUE, "слот справа", "1", null, rs, 1));
                out.add(new Completion("\"Имя\"", Kind.VALUE, "имя текстом", "\"${1:Имя}\"", null, rs, 2));
            }
            case LABEL -> r.labels.values().forEach(s -> out.add(sym(s, rs)));
            case DURATION -> durations(out, rs);
            case BOOL -> {
                out.add(new Completion("true", Kind.VALUE, null, "true", null, rs, 1));
                out.add(new Completion("false", Kind.VALUE, null, "false", null, rs, 1));
            }
            case EXPR -> exprCompletions(out, rs, r);
            case EMOTION -> emotions(out, rs);
            case KEY -> {
                for (String k : Specs.KEYS) out.add(new Completion(k, Kind.VALUE, "клавиша", k, null, rs, 1));
            }
            case SCRIPT -> {
                for (Integer id : knownScripts.get()) out.add(new Completion(String.valueOf(id), Kind.VALUE, "сюжет", String.valueOf(id), null, rs, 1));
            }
            case SOUND, ITEM, EFFECT, ENTITY -> out.add(new Completion("\"…\"", Kind.VALUE, type.title, "\"${1:minecraft:}\"", type.doc, rs, 1));
            case ANIM -> out.add(new Completion("\"…\"", Kind.VALUE, type.title, "\"${1:st2/}\"", type.doc, rs, 1));
            case GUI -> {
                for (String g : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectGuis.get().keySet()) {
                    out.add(new Completion("\"" + g + "\"", Kind.VALUE, "интерфейс", "\"" + g + "\"", null, rs, 0));
                }
            }
            case EASE -> {
                for (String e : org.sprautestudio.spraute_engine.cutscene.Ease.names())
                    out.add(new Completion(e, Kind.VALUE, org.sprautestudio.spraute_engine.cutscene.Ease.TITLES.get(e), e, type.doc, rs, 1));
            }
            case CUTSCENE -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectCutscenes.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "катсцена", "\"" + n + "\"", null, rs, 0));
            }
            case SHOP -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectShops.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "магазин", "\"" + n + "\"", null, rs, 0));
            }
            case FIGHT -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectFights.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "стиль боя", "\"" + n + "\"", null, rs, 0));
                for (var e : org.sprautestudio.spraute_engine.fight.FightDef.PRESET_TITLES.entrySet())
                    out.add(new Completion("\"" + e.getKey() + "\"", Kind.VALUE, e.getValue(), "\"" + e.getKey() + "\"", null, rs, 1));
            }
            case ZONE -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectZones.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "зона", "\"" + n + "\"", null, rs, 0));
            }
            case PATH -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectPaths.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "путь", "\"" + n + "\"", null, rs, 0));
            }
            case QUEST -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectQuests.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "квест", "\"" + n + "\"", null, rs, 0));
            }
            case VFX -> {
                for (String n : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectEffects.get())
                    out.add(new Completion("\"" + n + "\"", Kind.VALUE, "эффект проекта", "\"" + n + "\"", null, rs, 0));
                for (var p : org.sprautestudio.spraute_engine.fx.FxPresets.ALL.values())
                    out.add(new Completion("\"" + p.name() + "\"", Kind.VALUE, p.title(), "\"" + p.name() + "\"", p.doc(), rs, 1));
            }
            case GUIEL -> {
                for (String id : guiElementIds()) out.add(new Completion("\"" + id + "\"", Kind.VALUE, "элемент", "\"" + id + "\"", null, rs, 0));
            }
            case IMAGE -> {
                for (String im : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.imageChoices()) {
                    out.add(new Completion("\"" + im + "\"", Kind.VALUE, "картинка", "\"" + im + "\"", null, rs, 1));
                }
            }
            case LAYER -> {
                for (String l : Specs.LAYERS) out.add(new Completion(l, Kind.VALUE, "слой", l, layerDoc(l), rs, 1));
            }
            case SYSTEM -> {
                for (String m : Specs.ENGINE_SYSTEMS) out.add(new Completion(m, Kind.VALUE, Specs.systemTitle(m), m, ParamType.SYSTEM.doc, rs, 1));
            }
            case MOVETYPE -> {
                for (String m : Specs.MOVE_TYPES) out.add(new Completion(m, Kind.VALUE, "тип движения", m, ParamType.MOVETYPE.doc, rs, 1));
            }
            case TEXT -> out.add(new Completion("\"…\"", Kind.VALUE, "текст", "\"${1:текст}\"", ParamType.TEXT.doc, rs, 3));
            default -> {
            }
        }
    }

    private static List<String> guiElementIds() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (List<String> l : org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectGuis.get().values()) ids.addAll(l);
        return new ArrayList<>(ids);
    }

    private static String layerDoc(String l) {
        return switch (l) {
            case "action" -> "Основной слой: анимация всего тела, перекрывает стойку и ходьбу";
            case "ambient" -> "Фоновый слой, накладывается поверх";
            case "emotion" -> "Мимика лица";
            case "eyes" -> "Глаза";
            default -> "Накладываемый слой ANIMATION_" + l + ": складывается с остальными анимациями";
        };
    }

    private void locationLiterals(List<Completion> out, int rs) {
        if (positionProvider != null) {
            String pos = positionProvider.get();
            if (pos != null) out.add(new Completion("моя позиция " + pos, Kind.VALUE, "координаты игрока", pos, "Текущие координаты вашего персонажа", rs, 0));
        }
        out.add(new Completion("(x, y, z)", Kind.VALUE, "координаты", "(${1:0}, ${2:64}, ${3:0})", null, rs, 2));
    }

    private static void durations(List<Completion> out, int rs) {
        for (String d : List.of("1s", "2s", "3s", "5s", "10s", "20t", "0.5s")) {
            out.add(new Completion(d, Kind.VALUE, "время", d, ParamType.DURATION.doc, rs, 1));
        }
    }

    private static void emotions(List<Completion> out, int rs) {
        for (String e : Specs.EMOTIONS) out.add(new Completion(e, Kind.VALUE, "эмоция", e, null, rs, 1));
    }

    private void exprCompletions(List<Completion> out, int rs, Analyzer.Result r) {
        r.vars.values().forEach(s -> out.add(sym(s, rs)));
        r.characters.values().forEach(s -> out.add(sym(s, rs)));
        r.locations.values().forEach(s -> out.add(sym(s, rs)));
        for (Specs.FunctionSpec f : Specs.functions()) {
            String insert = f.signature().endsWith("()") ? f.name() + "()" : f.name() + "(${1})";
            out.add(new Completion(f.name(), Kind.FUNCTION, f.returns(), insert, f.signature() + "\n\n" + f.doc(), rs, 1));
        }
        for (String k : List.of("and", "or", "not", "true", "false", "player", "actor")) {
            out.add(new Completion(k, Kind.KEYWORD, null, k + (k.equals("not") ? " " : ""), Specs.keywordDocs().get(k), rs, 3));
        }
    }

    private static List<Completion> filter(List<Completion> list, String typed, int limit) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<Object[]> scored = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Completion c : list) {
            if (!seen.add(c.label() + "|" + c.insert())) continue;
            String l = c.label().toLowerCase(Locale.ROOT);
            int score;
            if (t.isEmpty()) score = 0;
            else if (l.equals(t)) score = -3;
            else if (l.startsWith(t)) score = -2;
            else if (l.contains(t)) score = 1;
            else if (subsequence(t, l)) score = 2;
            else continue;
            scored.add(new Object[]{c, score});
        }
        scored.sort(Comparator.<Object[]>comparingInt(o -> (Integer) o[1])
                .thenComparingInt(o -> ((Completion) o[0]).priority())
                .thenComparing(o -> ((Completion) o[0]).label().length())
                .thenComparing(o -> ((Completion) o[0]).label()));
        List<Completion> result = new ArrayList<>();
        for (Object[] o : scored) {
            result.add((Completion) o[0]);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static boolean subsequence(String needle, String hay) {
        int j = 0;
        for (int i = 0; i < hay.length() && j < needle.length(); i++) if (hay.charAt(i) == needle.charAt(j)) j++;
        return j == needle.length();
    }

    // ================================================================= hover

    @Override
    public String hover(String text, int offset, Analysis analysis) {
        Analyzer.Result r = result(analysis);
        int s = offset, e = offset;
        while (s > 0 && isWord(text.charAt(s - 1))) s--;
        while (e < text.length() && isWord(text.charAt(e))) e++;
        if (s == e) return null;
        String word = text.substring(s, e);
        // statement head?
        int ls = text.lastIndexOf('\n', Math.max(0, s - 1)) + 1;
        String before = text.substring(ls, s).trim();
        if (before.endsWith("{")) before = "";
        if (before.isEmpty() && Specs.isCommandHead(word)) {
            int ne = e;
            while (ne < text.length() && text.charAt(ne) == ' ') ne++;
            int we = ne;
            while (we < text.length() && isWord(text.charAt(we))) we++;
            CommandSpec spec = Specs.command(word + " " + text.substring(ne, we));
            if (spec == null) spec = Specs.command(word);
            if (spec != null) return spec.signature() + "\n" + spec.doc + paramDocs(spec);
            return "Команда " + word + ": " + Specs.withHead(word).stream().map(x -> x.key).collect(Collectors.joining(", "));
        }
        if (Specs.keywordDocs().containsKey(word) && Lexer.KEYWORDS.contains(word)) return word + "\n" + Specs.keywordDocs().get(word);
        Specs.FunctionSpec f = Specs.function(word);
        if (f != null && e < text.length() && text.charAt(e) == '(') return f.signature() + " → " + f.returns() + "\n" + f.doc();
        Analyzer.Symbol sym = r.lookup(word);
        if (sym != null) {
            String kind = switch (sym.kind()) {
                case CHARACTER -> "Персонаж";
                case LOCATION -> "Точка";
                case VARIABLE -> "Переменная";
                case LABEL -> "Метка";
                case PARALLEL -> "Асинхронная ветка";
                case FUNCTION -> "Функция";
            };
            String detail = sym.detail() == null || sym.detail().isEmpty() ? "" : " = " + sym.detail();
            return kind + " " + word + detail + "\nCtrl+клик — перейти к объявлению";
        }
        if (Specs.keywordDocs().containsKey(word)) return word + "\n" + Specs.keywordDocs().get(word);
        return null;
    }

    // ============================================================= signature

    @Override
    public Signature signature(String text, int caret, Analysis analysis) {
        Context ctx = context(text, caret);
        if (ctx.spec == null || ctx.tokens.isEmpty()) return null;
        CommandSpec spec = ctx.spec;
        if (spec.groups.isEmpty()) return null;
        ArgState st = argState(spec, ctx.tokens.subList(ctx.specTokens, ctx.tokens.size()));
        List<String> parts = new ArrayList<>();
        parts.add(spec.key);
        for (CommandSpec.Group g : spec.groups) {
            StringBuilder sb = new StringBuilder();
            if (g.optional) sb.append('[');
            if (g.keyword != null) sb.append(g.keyword);
            for (int i = 0; i < g.params.size(); i++) {
                CommandSpec.Param p = g.params.get(i);
                if (g.keyword != null || i > 0) sb.append(' ');
                sb.append('<').append(p.name).append(':').append(p.type.title).append(p.vararg ? "..." : "").append('>');
            }
            if (g.optional) sb.append(']');
            parts.add(sb.toString());
        }
        String doc = spec.doc;
        if (st.next != null) doc = st.next.name + " — " + st.next.type.doc;
        return new Signature(parts, st.activePart >= 0 ? st.activePart + 1 : -1, doc);
    }

    // ============================================================ definition

    @Override
    public int definition(String text, int offset, Analysis analysis) {
        int s = offset, e = offset;
        while (s > 0 && isWord(text.charAt(s - 1))) s--;
        while (e < text.length() && isWord(text.charAt(e))) e++;
        if (s == e) return -1;
        Analyzer.Symbol sym = result(analysis).lookup(text.substring(s, e));
        return sym == null ? -1 : sym.start();
    }

    @Override
    public String lineComment() {
        return "//";
    }

    // ================================================================ format

    @Override
    public String format(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].strip();
            int d = depth;
            if (t.startsWith("}")) d = Math.max(0, d - 1);
            if (!t.isEmpty()) sb.append("    ".repeat(d)).append(t);
            if (i < lines.length - 1) sb.append('\n');
            boolean inStr = false;
            for (int k = 0; k < t.length(); k++) {
                char ch = t.charAt(k);
                if (ch == '\\' && inStr) {
                    k++;
                    continue;
                }
                if (ch == '"') inStr = !inStr;
                if (inStr) continue;
                if (ch == '/' && k + 1 < t.length() && t.charAt(k + 1) == '/') break;
                if (ch == '#') break;
                if (ch == '{') depth++;
                if (ch == '}') depth = Math.max(0, depth - 1);
            }
        }
        return sb.toString();
    }

    /** An animation of one of the project's own models ("walk" of any custom model). */
    private static boolean customAnimation(String id) {
        for (var m : org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.all()) {
            if (org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.animation(m, id).isPresent()) return true;
        }
        return false;
    }
}
