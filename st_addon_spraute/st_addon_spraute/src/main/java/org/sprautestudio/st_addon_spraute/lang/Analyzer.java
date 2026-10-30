package org.sprautestudio.st_addon_spraute.lang;

import org.sprautestudio.st_addon_spraute.lang.Ast.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic checks over a parsed program plus a symbol table for editor features. */
public final class Analyzer {
    public enum SymbolKind { CHARACTER, LOCATION, VARIABLE, LABEL, PARALLEL, FUNCTION }

    public record Symbol(String name, SymbolKind kind, int start, int end, String detail) {}

    /** Optional registry lookups (the client fills these from game registries). */
    public static final class Registries {
        public Predicate<String> sounds, items, effects, entities;
        /** Names of the project pictures. */
        public Predicate<String> images;
        /** Names of the project's own sounds (its "sounds" folder). */
        public Predicate<String> projectSounds;
        /** Names of the project GUIs and ids of their elements. */
        public Predicate<String> guis, guiElements;
        /** Accepts full ("ns:path!key") and short ("path") animation ids. */
        public Predicate<String> animations;
        /** Custom models of the project ("spraute:<name>"). */
        public Predicate<String> models;
    }

    public static final class Result {
        public final Parser.Result parse;
        public final List<Diagnostic> diagnostics = new ArrayList<>();
        public final Map<String, Symbol> characters = new LinkedHashMap<>();
        public final Map<String, Symbol> locations = new LinkedHashMap<>();
        public final Map<String, Symbol> vars = new LinkedHashMap<>();
        public final Map<String, Symbol> labels = new LinkedHashMap<>();
        public final Map<String, Symbol> functions = new LinkedHashMap<>();
        /** Named async/parallel branches. */
        public final Map<String, Symbol> branches = new LinkedHashMap<>();
        public final List<Symbol> outline = new ArrayList<>();

        Result(Parser.Result parse) {
            this.parse = parse;
        }

        public Program program() {
            return parse.program;
        }

        public boolean hasErrors() {
            for (Diagnostic d : diagnostics) if (d.isError()) return true;
            return false;
        }

        public int errorCount() {
            int n = 0;
            for (Diagnostic d : diagnostics) if (d.isError()) n++;
            return n;
        }

        public int warningCount() {
            int n = 0;
            for (Diagnostic d : diagnostics) if (d.severity == Diagnostic.Severity.WARNING) n++;
            return n;
        }

        /** Finds the declaration of a name of any kind. */
        public Symbol lookup(String name) {
            Symbol s = characters.get(name);
            if (s == null) s = locations.get(name);
            if (s == null) s = vars.get(name);
            if (s == null) s = labels.get(name);
            if (s == null) s = functions.get(name);
            if (s == null) s = branches.get(name);
            return s;
        }
    }

    private static final Pattern INTERPOLATION = Pattern.compile("(?<!\\\\)\\{([^{}]*)}");
    private static final Pattern RESOURCE_ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    private final String src;
    private final int[] lineStarts;
    private final Result result;
    private final Registries registries;

    private Analyzer(String src, Parser.Result parse, Registries registries) {
        this.src = src;
        this.lineStarts = Parser.computeLineStarts(src);
        this.result = new Result(parse);
        this.registries = registries == null ? new Registries() : registries;
    }

    /** Names that are a player in any story: its own, and the one who caused the event being handled. */
    private static boolean isPlayerWord(String name) {
        return name.equals("player") || name.equals("actor");
    }

    public static Result analyze(String src) {
        return analyze(src, null);
    }

    public static Result analyze(String src, Registries registries) {
        Parser.Result parse = Parser.parse(src);
        Analyzer a = new Analyzer(src, parse, registries);
        a.result.diagnostics.addAll(parse.diagnostics);
        a.run();
        a.result.diagnostics.sort((x, y) -> Integer.compare(x.start, y.start));
        return a.result;
    }

    private void diag(Diagnostic.Severity sev, String msg, Node at) {
        diag(sev, msg, at.start, at.end);
    }

    private void diag(Diagnostic.Severity sev, String msg, int start, int end) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= start) lo = mid;
            else hi = mid - 1;
        }
        result.diagnostics.add(new Diagnostic(sev, msg, start, end, lo, start - lineStarts[lo]));
    }

    private void error(String msg, Node at) {
        diag(Diagnostic.Severity.ERROR, msg, at);
    }

    private void warn(String msg, Node at) {
        diag(Diagnostic.Severity.WARNING, msg, at);
    }

    // ------------------------------------------------------------------- driver

    private void run() {
        Program prog = result.program();
        if (prog.header != null) {
            if (prog.header.id <= 0) diag(Diagnostic.Severity.ERROR, "Id сюжета должен быть больше 0", prog.header.idStart, prog.header.idEnd);
            else if (prog.header.id < 1000) diag(Diagnostic.Severity.WARNING,
                    "Id меньше 1000 может совпасть с сюжетами Spraute (1, 2, 3, 4, 7, 888...)", prog.header.idStart, prog.header.idEnd);
        }
        for (CharacterDecl c : prog.characters) {
            declare(result.characters, c.name, SymbolKind.CHARACTER, c.nameStart, c.nameEnd,
                    c.existing != null ? "existing " + literal(c.existing) : literal(c.type));
        }
        for (LocationDecl l : prog.locations) {
            declare(result.locations, l.name, SymbolKind.LOCATION, l.nameStart, l.nameEnd, literal(l.pos));
        }
        for (VarDecl v : prog.vars) {
            declare(result.vars, v.name, SymbolKind.VARIABLE, v.nameStart, v.nameEnd, literal(v.init));
        }
        for (FuncDecl f : prog.funcs) {
            declare(result.functions, f.name, SymbolKind.FUNCTION, f.nameStart, f.nameEnd, "(" + String.join(", ", f.params) + ")");
            for (String p : f.params) {
                Symbol existing = result.lookup(p);
                if (existing == null) {
                    Symbol s = new Symbol(p, SymbolKind.VARIABLE, f.nameStart, f.nameEnd, "параметр " + f.name);
                    result.vars.put(p, s);
                } else if (existing.kind() != SymbolKind.VARIABLE) {
                    diag(Diagnostic.Severity.ERROR, "Параметр '" + p + "' совпадает с именем: " + kindName(existing.kind()), f.nameStart, f.nameEnd);
                }
            }
        }
        collectLabels(prog.body);
        for (FuncDecl f : prog.funcs) collectLabels(f.body);

        for (CharacterDecl c : prog.characters) {
            if (c.type != null) checkResource(c.type, ParamType.ENTITY);
            if (c.existing != null && !(c.existing instanceof StringLit)) error("Ожидался id персонажа в кавычках", c.existing);
            if (c.displayName != null) expr(c.displayName);
            if (c.health != null) expr(c.health);
            if (c.hitbox != null) {
                expr(c.hitbox);
                if (c.hitbox instanceof StringLit s) {
                    try {
                        org.sprautestudio.st_addon_spraute.hitbox.HitboxFlags.parse(0, java.util.List.of(s.value));
                    } catch (IllegalArgumentException ex) {
                        error(ex.getMessage(), c.hitbox);
                    }
                }
            }
            if (c.color != null) {
                expr(c.color);
                if (c.color instanceof StringLit s) {
                    try {
                        org.sprautestudio.st_addon_spraute.runtime.Colors.parse(s.value);
                    } catch (RuntimeException ex) {
                        error(ex.getMessage(), c.color);
                    }
                }
            }
            if (c.speed != null) expr(c.speed);
            if (c.skin != null && !(c.skin instanceof StringLit)) error("Ожидался путь к текстуре в кавычках", c.skin);
            checkPicture(c.icon);
            checkPicture(c.portrait);
        }
        for (LocationDecl l : prog.locations) {
            checkCoords(l.pos);
            if (l.yaw != null) expr(l.yaw);
            if (l.pitch != null) expr(l.pitch);
            if (l.dimension != null) checkResource(l.dimension, null);
        }
        for (VarDecl v : prog.vars) expr(v.init);
        statements(prog.body);
        for (FuncDecl f : prog.funcs) {
            inFunction++;
            statements(f.body);
            inFunction--;
        }
    }

    private int inFunction;
    /** How many loops the checked code is inside (for break / continue). */
    private int loopDepth;

    private void branch(String name, int start, int end) {
        if (name == null) return;
        Symbol existing = result.branches.get(name);
        if (existing != null) return; // the same branch may be started from several places
        Symbol other = result.lookup(name);
        if (other != null) {
            diag(Diagnostic.Severity.ERROR, "Имя ветки '" + name + "' уже занято (" + kindName(other.kind()) + ")", start, end);
            return;
        }
        Symbol s = new Symbol(name, SymbolKind.PARALLEL, start, end, "");
        result.branches.put(name, s);
        result.outline.add(s);
    }

    private void declare(Map<String, Symbol> map, String name, SymbolKind kind, int start, int end, String detail) {
        if (name == null) return;
        Symbol existing = result.lookup(name);
        if (existing != null) {
            diag(Diagnostic.Severity.ERROR, "Имя '" + name + "' уже занято (" + kindName(existing.kind()) + ")", start, end);
            return;
        }
        if (Specs.function(name) != null) {
            diag(Diagnostic.Severity.WARNING, "Имя '" + name + "' совпадает со встроенной функцией", start, end);
        }
        Symbol s = new Symbol(name, kind, start, end, detail);
        map.put(name, s);
        result.outline.add(s);
    }

    private static String kindName(SymbolKind k) {
        return switch (k) {
            case CHARACTER -> "персонаж";
            case LOCATION -> "точка";
            case VARIABLE -> "переменная";
            case LABEL -> "метка";
            case PARALLEL -> "асинхронная ветка";
            case FUNCTION -> "функция";
        };
    }

    private String literal(Expr e) {
        if (e == null) return "";
        return src.substring(e.start, Math.min(e.end, src.length()));
    }

    private void collectLabels(List<Stmt> body) {
        for (Stmt s : body) {
            if (s instanceof LabelStmt l) {
                declare(result.labels, l.name, SymbolKind.LABEL, l.start, l.end, "");
            } else if (s instanceof IfStmt i) {
                collectLabels(i.then);
                if (i.otherwise != null) collectLabels(i.otherwise);
            } else if (s instanceof WhileStmt w) {
                collectLabels(w.body);
            } else if (s instanceof RepeatStmt r) {
                collectLabels(r.body);
            } else if (s instanceof ForStmt f) {
                // the loop variable is an ordinary story variable
                if (!result.vars.containsKey(f.var)) declare(result.vars, f.var, SymbolKind.VARIABLE, f.varStart, f.varEnd, "переменная цикла");
                collectLabels(f.body);
            } else if (s instanceof EveryStmt e) {
                collectLabels(e.body);
            } else if (s instanceof ChoiceStmt c) {
                for (ChoiceOption o : c.options) collectLabels(o.body);
            } else if (s instanceof AsStmt as) {
                collectLabels(as.body);
            } else if (s instanceof ParallelStmt ps) {
                branch(ps.name, ps.start, ps.end);
                collectLabels(ps.body);
            } else if (s instanceof CallStmt c && c.async) {
                branch(c.as, c.asStart, c.asEnd);
            } else if (s instanceof OnStmt on) {
                collectLabels(on.body);
            } else if (s instanceof SwitchStmt sw) {
                for (SwitchCase c : sw.cases) collectLabels(c.body);
                if (sw.otherwise != null) collectLabels(sw.otherwise);
            }
        }
    }

    // --------------------------------------------------------------- statements

    private void statements(List<Stmt> body) {
        boolean afterJump = false;
        for (Stmt s : body) {
            if (afterJump && !(s instanceof LabelStmt)) {
                warn("Этот код никогда не выполнится (после goto/stop)", s);
                afterJump = false;
            }
            statement(s);
            if (s instanceof GotoStmt || s instanceof StopStmt || s instanceof ReturnStmt) afterJump = true;
            if (s instanceof LabelStmt) afterJump = false;
        }
    }

    /** A label of the story, or one of the reserved ones: the very beginning and the very end. */
    private boolean isLabel(String name) {
        return result.labels.containsKey(name) || name.equals("начало") || name.equals("конец");
    }

    private void statement(Stmt s) {
        if (s instanceof CommandStmt c) command(c);
        else if (s instanceof GotoStmt g) {
            if (!isLabel(g.name)) {
                diag(Diagnostic.Severity.ERROR, "Метка '" + g.name + "' не найдена", g.nameStart, g.nameEnd);
            }
        } else if (s instanceof SetStmt set) {
            boolean entityProp = set.prop != null && (isPlayerWord(set.name) || result.characters.containsKey(set.name));
            if (!result.vars.containsKey(set.name) && !entityProp) {
                diag(Diagnostic.Severity.ERROR, "Переменная '" + set.name + "' не объявлена (добавьте var " + set.name + " = 0)", set.nameStart, set.nameEnd);
            }
            if (set.index != null) expr(set.index);
            expr(set.value);
        } else if (s instanceof ForStmt f) {
            expr(f.iter);
            loopDepth++;
            statements(f.body);
            loopDepth--;
        } else if (s instanceof EveryStmt e) {
            expr(e.interval);
            int saved = loopDepth;
            loopDepth = 0;
            statements(e.body);
            loopDepth = saved;
        } else if (s instanceof BreakStmt || s instanceof ContinueStmt) {
            if (loopDepth == 0) error((s instanceof BreakStmt ? "break" : "continue") + " можно писать только внутри for, while или repeat", s);
        } else if (s instanceof MethodStmt ms) {
            if (!result.vars.containsKey(ms.target) && !isPlayerWord(ms.target) && !result.characters.containsKey(ms.target)) {
                diag(Diagnostic.Severity.ERROR, "Переменная '" + ms.target + "' не объявлена (добавьте var " + ms.target + " = [])", ms.targetStart, ms.targetEnd);
            }
            for (Expr a : ms.args) expr(a);
        } else if (s instanceof IfStmt i) {
            expr(i.cond);
            statements(i.then);
            if (i.otherwise != null) statements(i.otherwise);
        } else if (s instanceof WhileStmt w) {
            expr(w.cond);
            loopDepth++;
            statements(w.body);
            loopDepth--;
        } else if (s instanceof RepeatStmt r) {
            expr(r.count);
            loopDepth++;
            statements(r.body);
            loopDepth--;
        } else if (s instanceof ChoiceStmt c) {
            if (!result.characters.containsKey(c.character)) {
                diag(Diagnostic.Severity.ERROR, "Персонаж '" + c.character + "' не объявлен", c.characterStart, c.characterEnd);
            }
            expr(c.text);
            if (c.emotion != null) checkEmotion(c.emotion);
            for (ChoiceOption o : c.options) {
                expr(o.text);
                statements(o.body);
            }
        } else if (s instanceof AsStmt as) {
            expr(as.who);
            statements(as.body);
        } else if (s instanceof ParallelStmt ps) {
            statements(ps.body);
        } else if (s instanceof CallStmt c) {
            FuncDecl f = result.program().func(c.name);
            if (f == null) {
                diag(Diagnostic.Severity.ERROR, "Функция '" + c.name + "' не объявлена (func " + c.name + " { ... })", c.nameStart, c.nameEnd);
            } else if (f.params.size() != c.args.size()) {
                diag(Diagnostic.Severity.ERROR, "Функция " + c.name + "(" + String.join(", ", f.params) + ") принимает аргументов: "
                        + f.params.size() + ", передано: " + c.args.size(), c.nameStart, c.nameEnd);
            }
            for (Expr a : c.args) expr(a);
        } else if (s instanceof OnStmt on) {
            command(on.event);
            int saved = loopDepth;
            loopDepth = 0;
            statements(on.body);
            loopDepth = saved;
        } else if (s instanceof SwitchStmt sw) {
            expr(sw.value);
            for (SwitchCase c : sw.cases) {
                for (Expr v : c.values) expr(v);
                statements(c.body);
            }
            if (sw.otherwise != null) statements(sw.otherwise);
        } else if (s instanceof ReturnStmt) {
            if (inFunction == 0) error("return можно использовать только внутри func", s);
        } else if (s instanceof AwaitStmt a) {
            for (int i = 0; i < a.names.size(); i++) {
                if (!result.branches.containsKey(a.names.get(i))) diag(Diagnostic.Severity.ERROR,
                        "Асинхронная ветка '" + a.names.get(i) + "' не найдена (async " + a.names.get(i) + " { } или async call ... as " + a.names.get(i) + ")",
                        a.spans.get(i)[0], a.spans.get(i)[1]);
            }
        } else if (s instanceof CancelStmt c) {
            if (!result.branches.containsKey(c.name)) diag(Diagnostic.Severity.ERROR, "Асинхронная ветка '" + c.name + "' не найдена", c.nameStart, c.nameEnd);
        }
    }

    private void command(CommandStmt c) {
        CommandSpec spec = Specs.command(c.key);
        if (spec == null) return;
        for (Arg a : c.args) {
            CommandSpec.Param p = spec.param(a.param);
            if (p == null) continue;
            // time set night: the words of the time of day are not variables
            if (c.key.startsWith("time ") && a.value instanceof Ident id
                    && java.util.Set.of("sunrise", "dawn", "day", "morning", "noon", "sunset", "evening", "night", "midnight").contains(id.name)) continue;
            checkArg(p, a.value);
        }
        if (c.key.equals("screen")) {
            for (Arg a : c.args) {
                if (a.value instanceof NumberLit n && (n.value < 0 || n.value > 255)) warn("Значение канала цвета должно быть от 0 до 255", a.value);
            }
        }
    }

    private void checkArg(CommandSpec.Param p, Expr e) {
        switch (p.type) {
            case CHAR -> checkCharacter(e);
            case LOC -> checkLocation(e);
            case TARGET -> {
                if (e instanceof Ident id) {
                    if (!isPlayerWord(id.name) && !result.characters.containsKey(id.name) && !result.locations.containsKey(id.name)
                            && !result.vars.containsKey(id.name)) {
                        error("'" + id.name + "' — не персонаж, не точка, не переменная и не player", e);
                    }
                } else if (e instanceof StringLit s) {
                    if (!s.value.equals("mob") && !s.value.equals("any")) checkResource(e, ParamType.ENTITY);
                } else {
                    checkCoords(e);
                }
            }
            case SPEAKER -> {
                if (e instanceof Ident id) {
                    if (!isPlayerWord(id.name) && !result.characters.containsKey(id.name) && !result.vars.containsKey(id.name)) {
                        error("Говорящий '" + id.name + "' не найден. Укажите слот (0/1), персонажа, player или имя в кавычках", e);
                    }
                } else if (e instanceof NumberLit n) {
                    if (n.value != 0 && n.value != 1) warn("Обычно слотов речи два: 0 и 1", e);
                } else {
                    expr(e);
                }
            }
            case DURATION -> {
                if (e instanceof NumberLit) warn("Число без единицы считается тиками (20 тиков = 1 секунда). Добавьте t или s", e);
                else if (!(e instanceof DurationLit)) expr(e);
            }
            case INT, NUMBER, SCRIPT -> {
                if (e instanceof StringLit) error("Ожидалось " + p.type.title + ", а не текст", e);
                else expr(e);
                if (p.type == ParamType.SCRIPT && e instanceof NumberLit n && n.value != Math.floor(n.value)) error("Id сюжета должен быть целым", e);
            }
            case TEXT -> expr(e);
            case EXPR, BOOL -> expr(e);
            case LABEL -> {
                if (e instanceof Ident id && !isLabel(id.name)) error("Метка '" + id.name + "' не найдена", e);
            }
            case SOUND, ITEM, EFFECT, ENTITY -> checkResource(e, p.type);
            case EMOTION -> checkEmotion(e);
            case KEY -> {
                String k = e instanceof Ident id ? id.name : e instanceof StringLit s ? s.value : null;
                if (k == null || Specs.glfwKey(k) < 0) error("Неизвестная клавиша. Примеры: F8, G, SPACE", e);
            }
            case ANIM -> {
                if (!(e instanceof StringLit s)) {
                    error("Ожидался id анимации в кавычках, например \"entity/humanoid!attack\"", e);
                } else if (s.value.isBlank()) {
                    error("Пустой id анимации", e);
                } else if (registries.animations != null && !registries.animations.test(s.value)) {
                    warn("Анимация '" + s.value + "' не найдена. Откройте «Ассеты → Анимации», чтобы выбрать", e);
                }
            }
            case GUI -> {
                if (!(e instanceof StringLit s)) error("Ожидалось имя интерфейса в кавычках: gui open \"меню\"", e);
                else if (registries.guis != null && !registries.guis.test(s.value)) warn("Интерфейса '" + s.value + "' нет в проекте (папка «Интерфейсы»)", e);
            }
            case GUIEL -> {
                if (!(e instanceof StringLit s)) error("Ожидался id элемента в кавычках, например \"yes\"", e);
                else if (registries.guiElements != null && !registries.guiElements.test(s.value)) warn("Элемента '" + s.value + "' нет ни в одном интерфейсе проекта", e);
            }
            case LAYER -> {
                String l = e instanceof Ident id ? id.name : e instanceof NumberLit n ? String.valueOf((int) n.value)
                        : e instanceof StringLit s ? s.value : null;
                if (l == null || Specs.layerName(l) == null) error("Неизвестный слой. Варианты: action, ambient, emotion, eyes, 0–20", e);
            }
            case EASE -> {
                String m = e instanceof Ident id ? id.name : e instanceof StringLit s ? s.value : null;
                if (m == null || !org.sprautestudio.st_addon_spraute.cutscene.Ease.known(m))
                    error("Неизвестная плавность. Например: smooth, linear, ease_in_out_sine, ease_out_back", e);
            }
            case CUTSCENE -> {
                if (!(e instanceof StringLit) && !(e instanceof Ident)) error("Имя катсцены в кавычках", e);
            }
            case SHOP -> {
                if (!(e instanceof StringLit)) error("Имя магазина в кавычках — из папки «Магазины» проекта", e);
            }
            case FIGHT -> {
                if (!(e instanceof StringLit)) error("Стиль боя в кавычках: \"fists\" или свой из папки «Стили боя»", e);
            }
            case ZONE -> {
                if (!(e instanceof StringLit)) error("Имя зоны в кавычках — из папки «Зоны» проекта", e);
            }
            case PATH -> {
                if (!(e instanceof StringLit)) error("Имя пути в кавычках — из папки «Пути» проекта", e);
            }
            case QUEST -> {
                if (!(e instanceof StringLit)) error("Имя квеста в кавычках — из папки «Квесты» проекта", e);
            }
            case VFX -> {
                if (!(e instanceof StringLit)) error("Имя эффекта в кавычках: \"nuke\" или свой из папки «Эффекты»", e);
            }
            case SYSTEM -> {
                String m = e instanceof Ident id ? id.name : e instanceof StringLit s ? s.value : null;
                if (m == null || !Specs.ENGINE_SYSTEMS.contains(m.toLowerCase())) error("Система движка: health, coins, time, inventory, slots или chat", e);
            }
            case MOVETYPE -> {
                String m = e instanceof Ident id ? id.name : e instanceof StringLit s ? s.value : null;
                if (m == null || !Specs.MOVE_TYPES.contains(m.toLowerCase())) error("Тип движения: idle, walk, fastwalk или run", e);
            }
        }
    }

    private void checkCharacter(Expr e) {
        if (e instanceof Ident id) {
            if (!result.characters.containsKey(id.name)) error("Персонаж '" + id.name + "' не объявлен", e);
        } else {
            error("Ожидался персонаж (имя из character ...)", e);
        }
    }

    private void checkLocation(Expr e) {
        if (e instanceof Ident id) {
            if (result.characters.containsKey(id.name)) error("'" + id.name + "' — персонаж, а здесь нужна точка", e);
            else if (!result.locations.containsKey(id.name) && !result.vars.containsKey(id.name)) error("Точка '" + id.name + "' не объявлена", e);
        } else {
            checkCoords(e);
        }
    }

    private void checkCoords(Expr e) {
        if (e instanceof Tuple t) {
            if (t.items.size() != 3) error("Координаты должны быть из трёх чисел: (x, y, z)", e);
            for (Expr item : t.items) expr(item);
        } else if (e instanceof Ident id && (result.locations.containsKey(id.name) || result.vars.containsKey(id.name))) {
            // a location, or a variable holding a point or an entity
        } else if (e instanceof Member || e instanceof Call || e instanceof Index) {
            expr(e);
        } else {
            error("Ожидались координаты (x, y, z) или имя точки", e);
        }
    }

    private void checkPicture(Expr e) {
        if (e == null) return;
        if (!(e instanceof StringLit s)) {
            error("Ожидалось имя картинки в кавычках — из папки «Картинки» проекта", e);
            return;
        }
        if (Specs.SPEECH_ICONS.contains(s.value) || s.value.equals("head")) return;
        // full texture ids of the game and "reset" of the skin command
        if (s.value.contains(":") || s.value.equals("reset") || s.value.equals("default") || s.value.contains("{")) return;
        if (registries.images != null && !registries.images.test(s.value)) {
            warn("Картинки '" + s.value + "' нет в проекте. Добавьте её в «Картинки» (можно перетащить файл в окно)", e);
        }
    }

    private void checkEmotion(Expr e) {
        String name = e instanceof Ident id ? id.name : e instanceof StringLit s ? s.value : null;
        if (name == null || !Specs.EMOTIONS.contains(name.toUpperCase())) error("Неизвестная эмоция. Например: HAPPY_NEW, SAD1, ANGRY", e);
    }

    private void checkResource(Expr e, ParamType type) {
        if (!(e instanceof StringLit s)) {
            error("Ожидался id в кавычках, например \"minecraft:stone\"", e);
            return;
        }
        if (type == ParamType.SOUND && !s.value.contains(":")) {
            // a sound of the project's "sounds" folder, by its name
            if (registries.projectSounds != null && registries.projectSounds.test(s.value)) return;
            if (!RESOURCE_ID.matcher(s.value).matches()) {
                if (registries.projectSounds != null)
                    warn("Звука «" + s.value + "» нет в проекте. Добавьте файл .ogg или .wav в «Звуки» (можно перетащить в окно)", e);
                return;
            }
        }
        if (type == ParamType.ENTITY && s.value.startsWith("spraute:")) {
            if (registries.models != null && !s.value.equals("spraute:npc") && !s.value.equals("spraute:npc_slim") && !registries.models.test(s.value.substring(8)))
                warn("Сущности «" + s.value.substring(8) + "» нет в проекте (папка «Сущности»)", e);
            return;
        }
        if (type == ParamType.ITEM && !org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(s.value).isEmpty()) {
            String bad = org.sprautestudio.st_addon_spraute.runtime.ItemSpec.error(s.value);
            if (bad != null) error("Ошибка в NBT предмета: " + bad, e);
            String item = org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(s.value);
            if (registries.items != null && !registries.items.test(item)) warn("'" + item + "' не найден в игре (" + type.title + ")", e);
            return;
        }
        if (!RESOURCE_ID.matcher(s.value).matches()) {
            error("Некорректный id '" + s.value + "' (только латиница в нижнем регистре, цифры, _ . / -)", e);
            return;
        }
        if (type == null) return;
        Predicate<String> known = switch (type) {
            case SOUND -> registries.sounds;
            case ITEM -> registries.items;
            case EFFECT -> registries.effects;
            case ENTITY -> registries.entities;
            default -> null;
        };
        String full = s.value.contains(":") ? s.value : "minecraft:" + s.value;
        if (known != null && !known.test(full)) warn("'" + full + "' не найден в игре (" + type.title + ")", e);
    }

    // -------------------------------------------------------------- expressions

    private void expr(Expr e) {
        if (e == null) return;
        if (e instanceof Ident id) {
            String n = id.name;
            if (isPlayerWord(n)) return;
            if (result.vars.containsKey(n) || result.characters.containsKey(n) || result.locations.containsKey(n)) return;
            if (Specs.function(n) != null) {
                error("'" + n + "' — функция, вызывайте так: " + Specs.function(n).signature(), e);
                return;
            }
            error("Неизвестное имя '" + n + "'", e);
        } else if (e instanceof StringLit s) {
            checkInterpolation(s);
        } else if (e instanceof Binary b) {
            expr(b.left);
            expr(b.right);
        } else if (e instanceof Unary u) {
            expr(u.operand);
        } else if (e instanceof Tuple t) {
            for (Expr x : t.items) expr(x);
        } else if (e instanceof ListLit l) {
            for (Expr x : l.items) expr(x);
        } else if (e instanceof MapLit m) {
            for (Expr x : m.keys) expr(x);
            for (Expr x : m.values) expr(x);
        } else if (e instanceof Index ix) {
            expr(ix.target);
            expr(ix.index);
        } else if (e instanceof Member m) {
            expr(m.target);
            if (m.args != null) for (Expr x : m.args) expr(x);
        } else if (e instanceof Call c) {
            Specs.FunctionSpec fs = Specs.function(c.name);
            if (fs == null) {
                diag(Diagnostic.Severity.ERROR, "Неизвестная функция '" + c.name + "'", c.nameStart, c.nameEnd);
            } else {
                int expected = arity(fs.signature()), optional = optionalArgs(fs.signature());
                if (c.args.size() > expected || c.args.size() < expected - optional) {
                    diag(Diagnostic.Severity.ERROR, "Функция " + fs.signature() + " принимает аргументов: " + expected, c.nameStart, c.nameEnd);
                }
            }
            for (Expr x : c.args) expr(x);
        }
    }

    /** Arguments written in [brackets] in a function's signature may be left out. */
    private static int optionalArgs(String signature) {
        int n = 0;
        for (char ch : signature.toCharArray()) if (ch == '[') n++;
        return signature.contains("...") ? 99 : n;
    }

    private static int arity(String signature) {
        String inner = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')')).trim();
        if (inner.isEmpty()) return 0;
        return inner.split(",").length;
    }

    private void checkInterpolation(StringLit s) {
        Matcher m = INTERPOLATION.matcher(s.value);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.isEmpty()) continue;
            String base = name.split("[(.\\[ ]", 2)[0];
            if (!result.vars.containsKey(base) && Specs.function(base) == null && !isPlayerWord(base)) {
                warn("В строке подставляется неизвестное имя '{" + name + "}'", s);
            }
        }
    }
}
