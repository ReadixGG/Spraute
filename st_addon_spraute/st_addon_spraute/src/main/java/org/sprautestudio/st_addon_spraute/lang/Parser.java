package org.sprautestudio.st_addon_spraute.lang;

import org.sprautestudio.st_addon_spraute.lang.Ast.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for .sst sources. Collects diagnostics instead of throwing and
 * recovers at the next line, so the editor always gets a (partial) tree.
 */
public final class Parser {
    public static final class Result {
        public final Program program;
        public final List<Diagnostic> diagnostics;
        public final List<Token> tokens;

        Result(Program program, List<Diagnostic> diagnostics, List<Token> tokens) {
            this.program = program;
            this.diagnostics = diagnostics;
            this.tokens = tokens;
        }

        public boolean hasErrors() {
            for (Diagnostic d : diagnostics) if (d.isError()) return true;
            return false;
        }
    }

    private static final class ParseError extends RuntimeException {
        ParseError() {
            super(null, null, false, false);
        }
    }

    private final String src;
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final int[] lineStarts;
    private int p;

    private Parser(String src) {
        this.src = src;
        this.tokens = Lexer.tokenize(src);
        this.lineStarts = computeLineStarts(src);
    }

    public static Result parse(String src) {
        Parser parser = new Parser(src);
        Program program = parser.program();
        return new Result(program, parser.diagnostics, parser.tokens);
    }

    /** Parses a standalone expression (used for "{...}" interpolation). Returns null on error. */
    public static Expr parseExpression(String src) {
        Parser parser = new Parser(src);
        try {
            Expr e = parser.expression();
            if (parser.peek().type != TokenType.EOF || !parser.diagnostics.isEmpty()) return null;
            return e;
        } catch (ParseError e) {
            return null;
        }
    }

    static int[] computeLineStarts(String src) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < src.length(); i++) if (src.charAt(i) == '\n') starts.add(i + 1);
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = starts.get(i);
        return arr;
    }

    // ------------------------------------------------------------------ helpers

    private Token peek() {
        return tokens.get(p);
    }

    private Token peek(int off) {
        return tokens.get(Math.min(p + off, tokens.size() - 1));
    }

    private Token next() {
        Token t = tokens.get(p);
        if (t.type != TokenType.EOF) p++;
        return t;
    }

    private Token prev() {
        return tokens.get(Math.max(0, p - 1));
    }

    private boolean atStatementEnd() {
        TokenType t = peek().type;
        return t == TokenType.NEWLINE || t == TokenType.EOF || t == TokenType.RBRACE || t == TokenType.LBRACE;
    }

    void error(String msg, int start, int end) {
        add(Diagnostic.Severity.ERROR, msg, start, end);
    }

    void warn(String msg, int start, int end) {
        add(Diagnostic.Severity.WARNING, msg, start, end);
    }

    private void add(Diagnostic.Severity sev, String msg, int start, int end) {
        int line = lineOf(start);
        diagnostics.add(new Diagnostic(sev, msg, start, end, line, start - lineStarts[line]));
    }

    private int lineOf(int offset) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private ParseError fail(String msg, Token at) {
        int end = at.type == TokenType.NEWLINE || at.type == TokenType.EOF ? at.start + 1 : at.end;
        error(msg, at.start, Math.min(end, Math.max(at.start + 1, src.length())));
        return new ParseError();
    }

    private Token expect(TokenType type, String what) {
        if (peek().type != type) throw fail("Ожидалось " + what, peek());
        return next();
    }

    private Token expectIdent(String what) {
        Token t = peek();
        if (t.type != TokenType.IDENT) throw fail("Ожидалось " + what, t);
        return next();
    }

    private void expectEnd() {
        if (!atStatementEnd()) throw fail("Ожидался конец строки", peek());
    }

    private void skipNewlines() {
        while (peek().type == TokenType.NEWLINE) next();
    }

    private void recover() {
        int depth = 0;
        while (peek().type != TokenType.EOF) {
            TokenType t = peek().type;
            if (t == TokenType.NEWLINE && depth <= 0) return;
            if (t == TokenType.LBRACE) depth++;
            if (t == TokenType.RBRACE) {
                if (depth <= 0) return;
                depth--;
            }
            next();
        }
    }

    // ------------------------------------------------------------------ program

    private Program program() {
        Program program = new Program();
        // lexer errors first
        for (Token t : tokens) {
            if (t.type == TokenType.ERROR) error(String.valueOf(t.value), t.start, t.end);
        }
        skipNewlines();
        while (peek().type != TokenType.EOF) {
            try {
                Token t = peek();
                if (t.isWord("script")) header(program);
                else if (t.isWord("character")) program.characters.add(characterDecl());
                else if (t.isWord("location")) program.locations.add(locationDecl());
                else if (t.isWord("var")) program.vars.add(varDecl());
                else if (t.isWord("func")) {
                    FuncDecl f = funcDecl();
                    if (program.func(f.name) != null) error("Функция '" + f.name + "' уже объявлена", f.nameStart, f.nameEnd);
                    program.funcs.add(f);
                } else if (t.type == TokenType.RBRACE) {
                    error("Лишняя закрывающая скобка }", t.start, t.end);
                    next();
                } else {
                    Stmt s = statement();
                    if (s != null) program.body.add(s);
                }
            } catch (ParseError e) {
                recover();
                if (peek().type == TokenType.RBRACE) next();
            }
            skipNewlines();
        }
        program.at(0, src.length());
        if (program.header == null) warn("Нет заголовка: добавьте строку script <id> \"Название\"", 0, Math.min(1, src.length()));
        return program;
    }

    private void header(Program program) {
        Token kw = next();
        Header h = new Header();
        Token id = peek();
        if (id.type != TokenType.NUMBER) throw fail("Ожидался числовой id сюжета", id);
        next();
        double v = (Double) id.value;
        if (v != Math.floor(v)) error("Id сюжета должен быть целым", id.start, id.end);
        h.id = (int) v;
        h.idStart = id.start;
        h.idEnd = id.end;
        if (peek().type == TokenType.STRING) h.title = (String) next().value;
        if (peek().isWord("autostart")) {
            next();
            h.autostart = true;
            if (peek().isWord("once")) {
                next();
                h.once = true;
            }
        }
        expectEnd();
        if (program.header != null) error("Заголовок script уже объявлен", kw.start, kw.end);
        program.header = h.at(kw.start, prev().end);
    }

    private CharacterDecl characterDecl() {
        Token kw = next();
        CharacterDecl d = new CharacterDecl();
        Token name = expectIdent("имя персонажа");
        d.name = name.text;
        d.nameStart = name.start;
        d.nameEnd = name.end;
        if (!peek().isOp("=")) throw fail("Ожидалось = после имени персонажа", peek());
        next();
        if (peek().isWord("existing")) {
            next();
            d.existing = primary();
        } else {
            d.type = primary();
        }
        Set<String> seen = new HashSet<>();
        while (!atStatementEnd()) {
            Token opt = peek();
            if (!(opt.type == TokenType.IDENT) || !seen.add(opt.text)) throw fail("Неожиданный параметр персонажа", opt);
            next();
            switch (opt.text) {
                case "name" -> d.displayName = primary();
                case "speed" -> d.speed = unary();
                case "skin" -> d.skin = primary();
                case "icon" -> d.icon = primary();
                case "portrait" -> d.portrait = primary();
                case "headscale" -> d.headScale = unary();
                case "headx" -> d.headX = unary();
                case "heady" -> d.headY = unary();
                case "headyaw" -> d.headYaw = unary();
                case "headbone" -> d.headBone = primary();
                case "color" -> d.color = primary();
                case "hitbox" -> d.hitbox = primary();
                case "health", "hp" -> d.health = unary();
                default -> throw fail("Неизвестный параметр персонажа '" + opt.text
                        + "' (есть: name, color, hitbox, health, speed, skin, icon, portrait, headscale, headx, heady, headyaw, headbone)", opt);
            }
        }
        expectEnd();
        return d.at(kw.start, prev().end);
    }

    private LocationDecl locationDecl() {
        Token kw = next();
        LocationDecl d = new LocationDecl();
        Token name = expectIdent("имя точки");
        d.name = name.text;
        d.nameStart = name.start;
        d.nameEnd = name.end;
        if (!peek().isOp("=")) throw fail("Ожидалось = после имени точки", peek());
        next();
        d.pos = primary();
        Set<String> seen = new HashSet<>();
        while (!atStatementEnd()) {
            Token opt = peek();
            if (opt.type != TokenType.IDENT || !seen.add(opt.text)) throw fail("Неожиданный параметр точки", opt);
            next();
            switch (opt.text) {
                case "yaw" -> d.yaw = unary();
                case "pitch" -> d.pitch = unary();
                case "dim" -> d.dimension = primary();
                default -> throw fail("Неизвестный параметр точки '" + opt.text + "' (есть: yaw, pitch, dim)", opt);
            }
        }
        expectEnd();
        return d.at(kw.start, prev().end);
    }

    private VarDecl varDecl() {
        Token kw = next();
        VarDecl d = new VarDecl();
        Token name = expectIdent("имя переменной");
        d.name = name.text;
        d.nameStart = name.start;
        d.nameEnd = name.end;
        if (!peek().isOp("=")) throw fail("Ожидалось =", peek());
        next();
        d.init = expression();
        expectEnd();
        return d.at(kw.start, prev().end);
    }

    // --------------------------------------------------------------- statements

    private List<Stmt> block() {
        expect(TokenType.LBRACE, "{");
        List<Stmt> list = new ArrayList<>();
        skipNewlines();
        while (peek().type != TokenType.RBRACE) {
            if (peek().type == TokenType.EOF) throw fail("Блок не закрыт: не хватает }", peek());
            try {
                Token t = peek();
                if (t.isWord("character") || t.isWord("location") || t.isWord("var") || t.isWord("script") || t.isWord("func")) {
                    throw fail("'" + t.text + "' можно объявлять только на верхнем уровне файла", t);
                }
                Stmt s = statement();
                if (s != null) list.add(s);
            } catch (ParseError e) {
                recover();
            }
            skipNewlines();
        }
        next();
        return list;
    }

    private Stmt statement() {
        Token t = peek();
        if (t.isWord("label")) return label();
        if (t.isWord("goto")) return gotoStmt();
        if (t.isWord("stop")) {
            next();
            expectEnd();
            return new StopStmt().at(t.start, t.end);
        }
        if (t.isWord("set")) return setStmt();
        if (t.isWord("if")) return ifStmt();
        if (t.isWord("while")) {
            next();
            WhileStmt w = new WhileStmt();
            w.cond = expression();
            w.body.addAll(block());
            return w.at(t.start, prev().end);
        }
        if (t.isWord("repeat")) {
            next();
            RepeatStmt r = new RepeatStmt();
            r.count = expression();
            r.body.addAll(block());
            return r.at(t.start, prev().end);
        }
        if (t.isWord("for")) {
            next();
            ForStmt f = new ForStmt();
            Token v = expectIdent("имя переменной цикла");
            f.var = v.text;
            f.varStart = v.start;
            f.varEnd = v.end;
            if (!peek().isWord("in")) throw fail("Ожидалось in: for x in список { }", peek());
            next();
            f.iter = expression();
            f.body.addAll(block());
            return f.at(t.start, prev().end);
        }
        if (t.isWord("every")) {
            next();
            EveryStmt e = new EveryStmt();
            e.interval = expression();
            e.body.addAll(block());
            return e.at(t.start, prev().end);
        }
        if (t.isWord("on") && peek(1).isWord("tick")) {
            next();
            Token tick = next();
            EveryStmt e = new EveryStmt();
            DurationLit one = new DurationLit();
            one.ticks = 1;
            e.interval = one.at(tick.start, tick.end);
            e.body.addAll(block());
            return e.at(t.start, prev().end);
        }
        if (t.isWord("break") || t.isWord("continue")) {
            TokenType after = peek(1).type;
            if (after == TokenType.NEWLINE || after == TokenType.RBRACE || after == TokenType.EOF) {
                next();
                return (t.isWord("break") ? new BreakStmt() : new ContinueStmt()).at(t.start, t.end);
            }
        }
        // enemies.add(z)
        if ((t.type == TokenType.IDENT || t.isWord("player")) && peek(1).isOp(".") && peek(1).start == t.end && peek(2).type == TokenType.IDENT
                && peek(3).type == TokenType.LPAREN) {
            next();
            next();
            Token m = next();
            next();
            MethodStmt ms = new MethodStmt();
            ms.target = t.text;
            ms.targetStart = t.start;
            ms.targetEnd = t.end;
            ms.method = m.text;
            skipNewlines();
            if (peek().type != TokenType.RPAREN) {
                do {
                    skipNewlines();
                    ms.args.add(expression());
                    skipNewlines();
                } while (peek().type == TokenType.COMMA && next() != null);
            }
            expect(TokenType.RPAREN, ")");
            expectEnd();
            return ms.at(t.start, prev().end);
        }
        if (t.isWord("choice")) return choice();
        if (t.isWord("parallel")) {
            next();
            ParallelStmt ps = new ParallelStmt();
            ps.name = expectIdent("имя параллельной ветки").text;
            ps.body.addAll(block());
            return ps.at(t.start, prev().end);
        }
        if (t.isWord("call")) {
            next();
            return callRest(t, false);
        }
        if (t.isWord("on")) return onStmt();
        if (t.isWord("switch")) return switchStmt();
        if (t.isWord("async")) {
            next();
            if (peek().isWord("call")) {
                next();
                return callRest(t, true);
            }
            ParallelStmt ps = new ParallelStmt();
            if (peek().type == TokenType.IDENT) ps.name = next().text;
            if (peek().type != TokenType.LBRACE) throw fail("После async ожидалось: call функция(...), имя ветки или {", peek());
            ps.body.addAll(block());
            return ps.at(t.start, prev().end);
        }
        if (t.isWord("return")) {
            next();
            expectEnd();
            return new ReturnStmt().at(t.start, t.end);
        }
        if (t.isWord("await")) {
            next();
            AwaitStmt a = new AwaitStmt();
            while (true) {
                Token n = expectIdent("имя асинхронной ветки");
                a.names.add(n.text);
                a.spans.add(new int[]{n.start, n.end});
                if (peek().type != TokenType.COMMA) break;
                next();
            }
            expectEnd();
            return a.at(t.start, prev().end);
        }
        if (t.isWord("cancel")) {
            next();
            CancelStmt cs = new CancelStmt();
            Token n = expectIdent("имя асинхронной ветки");
            cs.name = n.text;
            cs.nameStart = n.start;
            cs.nameEnd = n.end;
            expectEnd();
            return cs.at(t.start, n.end);
        }
        if (t.type == TokenType.IDENT) return command();
        throw fail(t.type == TokenType.KEYWORD ? "Здесь нельзя использовать '" + t.text + "'" : "Ожидалась команда", t);
    }

    private OnStmt onStmt() {
        Token kw = next();
        Token ev = peek();
        if (ev.type != TokenType.IDENT && ev.type != TokenType.KEYWORD) throw fail("После on ожидалось событие: button, key, click, guiclose, chat, near...", ev);
        CommandSpec spec = Specs.command("wait " + ev.text);
        if (spec == null) {
            StringBuilder subs = new StringBuilder();
            for (CommandSpec s : Specs.withHead("wait")) if (s.sub != null) subs.append(subs.length() > 0 ? ", " : "").append(s.sub);
            throw fail("Неизвестное событие '" + ev.text + "'. Доступно: " + subs, ev);
        }
        next();
        OnStmt on = new OnStmt();
        CommandStmt cs = new CommandStmt();
        cs.key = spec.key;
        cs.nameStart = ev.start;
        cs.nameEnd = ev.end;
        commandArgs(cs, spec);
        on.event = cs.at(ev.start, prev().end);
        on.eventStart = ev.start;
        on.eventEnd = ev.end;
        on.body.addAll(block());
        return on.at(kw.start, prev().end);
    }

    private SwitchStmt switchStmt() {
        Token kw = next();
        SwitchStmt sw = new SwitchStmt();
        sw.value = expression();
        expect(TokenType.LBRACE, "{");
        skipNewlines();
        while (peek().type != TokenType.RBRACE) {
            if (peek().type == TokenType.EOF) throw fail("switch не закрыт: не хватает }", peek());
            try {
                if (peek().isWord("else")) {
                    Token e = next();
                    if (sw.otherwise != null) error("else уже есть в этом switch", e.start, e.end);
                    sw.otherwise = new ArrayList<>(block());
                } else {
                    SwitchCase c = new SwitchCase();
                    int s = peek().start;
                    while (true) {
                        c.values.add(expression());
                        if (peek().type != TokenType.COMMA) break;
                        next();
                    }
                    c.body.addAll(block());
                    sw.cases.add(c.at(s, prev().end));
                }
            } catch (ParseError e) {
                recover();
            }
            skipNewlines();
        }
        next();
        if (sw.cases.isEmpty()) error("В switch нужен хотя бы один вариант: \"значение\" { ... }", kw.start, kw.end);
        return sw.at(kw.start, prev().end);
    }

    private FuncDecl funcDecl() {
        Token kw = next();
        FuncDecl f = new FuncDecl();
        Token name = expectIdent("имя функции");
        f.name = name.text;
        f.nameStart = name.start;
        f.nameEnd = name.end;
        if (peek().type == TokenType.LPAREN) {
            next();
            while (peek().type != TokenType.RPAREN) {
                if (!f.params.isEmpty()) expect(TokenType.COMMA, ", или )");
                Token p = expectIdent("имя параметра");
                if (f.params.contains(p.text)) error("Параметр '" + p.text + "' повторяется", p.start, p.end);
                f.params.add(p.text);
            }
            next();
        }
        f.body.addAll(block());
        return f.at(kw.start, prev().end);
    }

    private CallStmt callRest(Token kw, boolean async) {
        CallStmt c = new CallStmt();
        c.async = async;
        Token name = expectIdent("имя функции");
        c.name = name.text;
        c.nameStart = name.start;
        c.nameEnd = name.end;
        if (peek().type == TokenType.LPAREN) {
            next();
            while (peek().type != TokenType.RPAREN) {
                if (!c.args.isEmpty()) expect(TokenType.COMMA, ", или )");
                if (atStatementEnd()) throw fail("Не закрыта скобка )", peek());
                c.args.add(expression());
            }
            next();
        }
        if (peek().isWord("as")) {
            if (!async) throw fail("'as' — только для async call", peek());
            next();
            Token n = expectIdent("имя ветки");
            c.as = n.text;
            c.asStart = n.start;
            c.asEnd = n.end;
        }
        expectEnd();
        return c.at(kw.start, prev().end);
    }

    private LabelStmt label() {
        Token kw = next();
        Token name = expectIdent("имя метки");
        if (peek().isOp(":")) next();
        expectEnd();
        LabelStmt l = new LabelStmt();
        l.name = name.text;
        return l.at(kw.start, name.end);
    }

    private GotoStmt gotoStmt() {
        Token kw = next();
        Token name = expectIdent("имя метки");
        expectEnd();
        GotoStmt g = new GotoStmt();
        g.name = name.text;
        g.nameStart = name.start;
        g.nameEnd = name.end;
        return g.at(kw.start, name.end);
    }

    private SetStmt setStmt() {
        Token kw = next();
        Token name = peek().isWord("player") ? next() : expectIdent("имя переменной");
        String prop = null;
        int propStart = 0, propEnd = 0;
        if (peek().isOp(".") && peek().start == name.end && (peek(1).type == TokenType.IDENT || peek(1).type == TokenType.KEYWORD)) {
            next();
            Token p = next();
            prop = p.text;
            propStart = p.start;
            propEnd = p.end;
        }
        Expr index = null;
        if (peek().type == TokenType.LBRACKET && peek().start == name.end) {
            next();
            index = expression();
            expect(TokenType.RBRACKET, "]");
        }
        Token op = peek();
        if (!(op.isOp("=") || op.isOp("+=") || op.isOp("-="))) throw fail("Ожидалось =, += или -=", op);
        next();
        SetStmt s = new SetStmt();
        s.index = index;
        s.prop = prop;
        s.propStart = propStart;
        s.propEnd = propEnd;
        s.name = name.text;
        s.nameStart = name.start;
        s.nameEnd = name.end;
        s.op = op.text;
        s.value = expression();
        expectEnd();
        return s.at(kw.start, prev().end);
    }

    private IfStmt ifStmt() {
        Token kw = next();
        IfStmt s = new IfStmt();
        s.cond = expression();
        s.then.addAll(block());
        // allow "}\nelse" as well as "} else"
        int save = p;
        skipNewlines();
        if (peek().isWord("else")) {
            next();
            if (peek().isWord("if")) {
                List<Stmt> nested = new ArrayList<>();
                nested.add(ifStmt());
                s.otherwise = nested;
            } else {
                s.otherwise = block();
            }
        } else {
            p = save;
        }
        return s.at(kw.start, prev().end);
    }

    private ChoiceStmt choice() {
        Token kw = next();
        ChoiceStmt c = new ChoiceStmt();
        Token who = expectIdent("персонаж, который задаёт вопрос");
        c.character = who.text;
        c.characterStart = who.start;
        c.characterEnd = who.end;
        c.text = primary();
        if (peek().isWord("emotion")) {
            next();
            c.emotion = primary();
        }
        expect(TokenType.LBRACE, "{ с вариантами ответа");
        skipNewlines();
        while (peek().type != TokenType.RBRACE) {
            if (peek().type == TokenType.EOF) throw fail("choice не закрыт: не хватает }", peek());
            try {
                ChoiceOption o = new ChoiceOption();
                int s = peek().start;
                o.text = primary();
                if (peek().isOp("->")) {
                    // "Ответ" -> label   shorthand
                    next();
                    Token target = expectIdent("метка");
                    GotoStmt g = new GotoStmt();
                    g.name = target.text;
                    g.nameStart = target.start;
                    g.nameEnd = target.end;
                    o.body.add(g.at(target.start, target.end));
                    expectEnd();
                } else if (peek().type == TokenType.LBRACE) {
                    o.body.addAll(block());
                } else {
                    expectEnd();
                }
                c.options.add(o.at(s, prev().end));
            } catch (ParseError e) {
                recover();
            }
            skipNewlines();
        }
        next();
        if (c.options.isEmpty()) error("В choice нужен хотя бы один вариант ответа", kw.start, kw.end);
        return c.at(kw.start, prev().end);
    }

    private CommandStmt command() {
        Token head = next();
        CommandStmt cs = new CommandStmt();
        cs.nameStart = head.start;
        cs.nameEnd = head.end;
        CommandSpec spec = null;
        if (peek().type == TokenType.IDENT || peek().type == TokenType.KEYWORD) {
            spec = Specs.command(head.text + " " + peek().text);
            if (spec != null) {
                cs.nameEnd = next().end;
            }
        }
        if (spec == null) spec = Specs.command(head.text);
        if (spec == null) {
            if (Specs.isCommandHead(head.text)) {
                StringBuilder subs = new StringBuilder();
                for (CommandSpec s : Specs.withHead(head.text)) if (s.sub != null) subs.append(subs.length() > 0 ? ", " : "").append(s.sub);
                throw fail("Неизвестный вариант '" + head.text + "'. Доступно: " + subs, head);
            }
            throw fail("Неизвестная команда '" + head.text + "'", head);
        }
        cs.key = spec.key;
        // spawn(alice, player): the command as a call — values in the order of its syntax, flags by name.
        // "teleport(1, 2, 3)" (a point right after the name) does not fit and is read the old way.
        if (spec.key.equals(head.text) && peek().type == TokenType.LPAREN && peek().start == head.end) {
            int save = p, diags = diagnostics.size();
            try {
                if (callArgs(cs, spec)) return cs.at(head.start, prev().end);
            } catch (ParseError ignored) {
            }
            p = save;
            while (diagnostics.size() > diags) diagnostics.remove(diagnostics.size() - 1);
            cs.args.clear();
        }
        commandArgs(cs, spec);
        return cs.at(head.start, prev().end);
    }

    /** Arguments of a command written as a call; false — they do not fit its syntax. */
    private boolean callArgs(CommandStmt cs, CommandSpec spec) {
        next();
        List<Expr> values = new ArrayList<>();
        skipNewlines();
        if (peek().type != TokenType.RPAREN) {
            do {
                skipNewlines();
                values.add(expression());
                skipNewlines();
            } while (peek().type == TokenType.COMMA && next() != null);
        }
        if (peek().type != TokenType.RPAREN) return false;
        Token close = next();
        if (!atStatementEnd()) return false;
        // flags: written by name — walk(alice, "route", nowait)
        for (java.util.Iterator<Expr> it = values.iterator(); it.hasNext(); ) {
            Expr v = it.next();
            if (!(v instanceof Ident id)) continue;
            for (CommandSpec.Group g : spec.groups) {
                if (!g.isFlag() || !g.keyword.equals(id.name)) continue;
                Arg flag = new Arg();
                flag.keyword = g.keyword;
                flag.param = g.keyword;
                BoolLit on = new BoolLit();
                on.value = true;
                flag.value = on.at(v.start, v.end);
                cs.args.add(flag.at(v.start, v.end));
                it.remove();
                break;
            }
        }
        int i = 0;
        for (CommandSpec.Group g : spec.groups) {
            if (g.isFlag()) continue;
            if (i >= values.size()) {
                if (!g.optional) error("Не хватает <" + g.params.get(0).name + ">. Синтаксис: " + spec.signature(), close.start, close.end);
                continue;
            }
            for (CommandSpec.Param param : g.params) {
                if (param.vararg) {
                    while (i < values.size()) cs.args.add(callArg(g.keyword, param, values.get(i++)));
                } else if (i < values.size()) {
                    cs.args.add(callArg(g.keyword, param, values.get(i++)));
                } else {
                    error("Не хватает <" + param.name + ">. Синтаксис: " + spec.signature(), close.start, close.end);
                }
            }
        }
        return i == values.size();
    }

    private static Arg callArg(String keyword, CommandSpec.Param param, Expr value) {
        Arg a = new Arg();
        a.keyword = keyword;
        a.param = param.name;
        a.value = value;
        return a.at(value.start, value.end);
    }

    private void commandArgs(CommandStmt cs, CommandSpec spec) {
        List<CommandSpec.Group> groups = spec.groups;
        boolean[] used = new boolean[groups.size()];
        while (!atStatementEnd()) {
            Token t = peek();
            int gi = -1;
            if (t.type == TokenType.IDENT || t.type == TokenType.KEYWORD) {
                for (int i = 0; i < groups.size(); i++) {
                    if (!used[i] && t.text.equals(groups.get(i).keyword)) {
                        gi = i;
                        break;
                    }
                }
            }
            if (gi >= 0) {
                next();
            } else {
                for (int i = 0; i < groups.size(); i++) {
                    if (!used[i] && groups.get(i).keyword == null) {
                        gi = i;
                        break;
                    }
                }
                if (gi < 0) throw fail("Лишний аргумент для '" + spec.key + "'. Синтаксис: " + spec.signature(), t);
            }
            used[gi] = true;
            CommandSpec.Group g = groups.get(gi);
            if (g.isFlag()) {
                Arg flag = new Arg();
                flag.keyword = g.keyword;
                flag.param = g.keyword;
                BoolLit on = new BoolLit();
                on.value = true;
                flag.value = on.at(prev().start, prev().end);
                cs.args.add(flag.at(prev().start, prev().end));
                continue;
            }
            for (CommandSpec.Param param : g.params) {
                if (param.vararg) {
                    int count = 0;
                    while (!atStatementEnd() && !isUnusedKeyword(groups, used)) {
                        cs.args.add(arg(g.keyword, param));
                        count++;
                    }
                    if (count == 0) throw fail("Ожидалось значение <" + param.name + ">", peek());
                } else {
                    if (atStatementEnd()) throw fail("Не хватает аргумента <" + param.name + "> (" + param.type.title + ")", peek());
                    cs.args.add(arg(g.keyword, param));
                }
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            CommandSpec.Group g = groups.get(i);
            if (!used[i] && !g.optional) {
                String what = g.keyword != null ? "'" + g.keyword + " <" + g.params.get(0).name + ">'" : "<" + g.params.get(0).name + ">";
                Token at = peek();
                error("Не хватает " + what + ". Синтаксис: " + spec.signature(), Math.max(0, at.start - 1), at.start + 1);
            }
        }
    }

    private boolean isUnusedKeyword(List<CommandSpec.Group> groups, boolean[] used) {
        Token t = peek();
        if (t.type != TokenType.IDENT && t.type != TokenType.KEYWORD) return false;
        for (int i = 0; i < groups.size(); i++) if (!used[i] && t.text.equals(groups.get(i).keyword)) return true;
        return false;
    }

    private Arg arg(String keyword, CommandSpec.Param param) {
        Arg a = new Arg();
        a.keyword = keyword;
        a.param = param.name;
        int s = peek().start;
        a.value = param.type == ParamType.EXPR ? expression() : unary();
        return a.at(s, prev().end);
    }

    // -------------------------------------------------------------- expressions

    public Expr expression() {
        return or();
    }

    private Expr or() {
        Expr left = and();
        while (peek().isOp("||") || peek().isWord("or")) {
            next();
            left = bin("or", left, and());
        }
        return left;
    }

    private Expr and() {
        Expr left = not();
        while (peek().isOp("&&") || peek().isWord("and")) {
            next();
            left = bin("and", left, not());
        }
        return left;
    }

    private Expr not() {
        if (peek().isWord("not")) {
            Token t = next();
            Unary u = new Unary();
            u.op = "!";
            u.operand = not();
            return u.at(t.start, u.operand.end);
        }
        return comparison();
    }

    private Expr comparison() {
        Expr left = additive();
        while (true) {
            Token t = peek();
            if (t.isOp("==") || t.isOp("!=") || t.isOp("<") || t.isOp("<=") || t.isOp(">") || t.isOp(">=")) {
                next();
                left = bin(t.text, left, additive());
            } else {
                return left;
            }
        }
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (peek().isOp("+") || peek().isOp("-")) {
            String op = next().text;
            left = bin(op, left, multiplicative());
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (peek().isOp("*") || peek().isOp("/") || peek().isOp("%")) {
            String op = next().text;
            left = bin(op, left, unary());
        }
        return left;
    }

    private Expr bin(String op, Expr l, Expr r) {
        Binary b = new Binary();
        b.op = op;
        b.left = l;
        b.right = r;
        return b.at(l.start, r.end);
    }

    private Expr unary() {
        Token t = peek();
        if (t.isOp("-") || t.isOp("!")) {
            next();
            Expr operand = unary();
            if (t.isOp("-") && operand instanceof NumberLit n) {
                n.value = -n.value;
                return n.at(t.start, n.end);
            }
            Unary u = new Unary();
            u.op = t.text;
            u.operand = operand;
            return u.at(t.start, operand.end);
        }
        return primary();
    }

    /** A value with what follows it right away: list[i], pos.x, list.has(x). */
    private Expr primary() {
        Expr e = atom();
        while (true) {
            Token t = peek();
            if (t.type == TokenType.LBRACKET && t.start == prev().end) {
                next();
                Index ix = new Index();
                ix.target = e;
                ix.index = expression();
                Token close = expect(TokenType.RBRACKET, "]");
                e = ix.at(e.start, close.end);
            } else if (t.isOp(".") && t.start == prev().end && (peek(1).type == TokenType.IDENT || peek(1).type == TokenType.KEYWORD)) {
                next();
                Token n = next();
                Member m = new Member();
                m.target = e;
                m.name = n.text;
                m.nameStart = n.start;
                m.nameEnd = n.end;
                if (peek().type == TokenType.LPAREN && peek().start == n.end) {
                    next();
                    m.args = new ArrayList<>();
                    if (peek().type != TokenType.RPAREN) {
                        do {
                            m.args.add(expression());
                        } while (peek().type == TokenType.COMMA && next() != null);
                    }
                    expect(TokenType.RPAREN, ")");
                }
                e = m.at(e.start, prev().end);
            } else {
                return e;
            }
        }
    }

    private Expr atom() {
        Token t = peek();
        switch (t.type) {
            case NUMBER -> {
                next();
                NumberLit n = new NumberLit();
                n.value = (Double) t.value;
                return n.at(t.start, t.end);
            }
            case DURATION -> {
                next();
                DurationLit d = new DurationLit();
                d.ticks = (Integer) t.value;
                return d.at(t.start, t.end);
            }
            case STRING -> {
                next();
                StringLit s = new StringLit();
                s.value = (String) t.value;
                return s.at(t.start, t.end);
            }
            case KEYWORD -> {
                if (t.text.equals("true") || t.text.equals("false")) {
                    next();
                    BoolLit b = new BoolLit();
                    b.value = t.text.equals("true");
                    return b.at(t.start, t.end);
                }
                if (t.text.equals("player")) {
                    next();
                    Ident id = new Ident();
                    id.name = "player";
                    return id.at(t.start, t.end);
                }
                throw fail("Ожидалось значение, а не '" + t.text + "'", t);
            }
            case IDENT -> {
                next();
                // a call needs "(" right after the name: "near(x, 3)", while "gate (1, 2, 3)" is two values
                if (peek().type == TokenType.LPAREN && peek().start == t.end) {
                    next();
                    Call c = new Call();
                    c.name = t.text;
                    c.nameStart = t.start;
                    c.nameEnd = t.end;
                    if (peek().type != TokenType.RPAREN) {
                        do {
                            c.args.add(expression());
                        } while (peek().type == TokenType.COMMA && next() != null);
                    }
                    Token close = expect(TokenType.RPAREN, ")");
                    return c.at(t.start, close.end);
                }
                Ident id = new Ident();
                id.name = t.text;
                return id.at(t.start, t.end);
            }
            case LPAREN -> {
                next();
                Expr first = expression();
                if (peek().type == TokenType.COMMA) {
                    Tuple tuple = new Tuple();
                    tuple.items.add(first);
                    while (peek().type == TokenType.COMMA) {
                        next();
                        tuple.items.add(expression());
                    }
                    Token close = expect(TokenType.RPAREN, ")");
                    return tuple.at(t.start, close.end);
                }
                Token close = expect(TokenType.RPAREN, ")");
                return first.at(t.start, close.end);
            }
            case LBRACKET -> {
                next();
                ListLit list = new ListLit();
                skipNewlines();
                if (peek().type != TokenType.RBRACKET) {
                    do {
                        skipNewlines();
                        list.items.add(expression());
                        skipNewlines();
                    } while (peek().type == TokenType.COMMA && next() != null);
                }
                skipNewlines();
                Token close = expect(TokenType.RBRACKET, "]");
                return list.at(t.start, close.end);
            }
            case LBRACE -> {
                // a dictionary: {name: "Алиса", hp: 20}
                next();
                MapLit map = new MapLit();
                skipNewlines();
                if (peek().type != TokenType.RBRACE) {
                    do {
                        skipNewlines();
                        Token k = peek();
                        Expr key;
                        if (k.type == TokenType.IDENT || k.type == TokenType.KEYWORD) {
                            next();
                            StringLit s = new StringLit();
                            s.value = k.text;
                            key = s.at(k.start, k.end);
                        } else key = atom();
                        if (!peek().isOp(":")) throw fail("Ожидалось ':' — {ключ: значение}", peek());
                        next();
                        skipNewlines();
                        map.keys.add(key);
                        map.values.add(expression());
                        skipNewlines();
                    } while (peek().type == TokenType.COMMA && next() != null);
                }
                skipNewlines();
                Token close = expect(TokenType.RBRACE, "}");
                return map.at(t.start, close.end);
            }
            default -> throw fail("Ожидалось значение", t);
        }
    }
}
