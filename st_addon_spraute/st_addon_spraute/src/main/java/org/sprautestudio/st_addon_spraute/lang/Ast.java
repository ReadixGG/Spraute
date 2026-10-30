package org.sprautestudio.st_addon_spraute.lang;

import java.util.ArrayList;
import java.util.List;

/** Syntax tree of a Spraute Story (.sst) program. */
public final class Ast {
    private Ast() {}

    public abstract static class Node {
        public int start, end;

        @SuppressWarnings("unchecked")
        public <T extends Node> T at(int start, int end) {
            this.start = start;
            this.end = end;
            return (T) this;
        }
    }

    // ---------------------------------------------------------------- program

    public static final class Program extends Node {
        public Header header;
        public final List<CharacterDecl> characters = new ArrayList<>();
        public final List<LocationDecl> locations = new ArrayList<>();
        public final List<VarDecl> vars = new ArrayList<>();
        public final List<FuncDecl> funcs = new ArrayList<>();
        public final List<Stmt> body = new ArrayList<>();

        public FuncDecl func(String name) {
            for (FuncDecl f : funcs) if (f.name.equals(name)) return f;
            return null;
        }
    }

    /** {@code func name(a, b) { ... }} — parameters are ordinary story variables. */
    public static final class FuncDecl extends Node {
        public String name;
        public int nameStart, nameEnd;
        public final List<String> params = new ArrayList<>();
        public final List<Stmt> body = new ArrayList<>();
    }

    public static final class Header extends Node {
        public int id;
        public String title = "";
        /** script 5001 "…" autostart [once]: started by itself when a player enters the world. */
        public boolean autostart, once;
        public int idStart, idEnd;
    }

    public static final class CharacterDecl extends Node {
        public String name;
        public int nameStart, nameEnd;
        /** Entity type id, e.g. "spraute:npc", or null when {@link #existing} is set. */
        public Expr type;
        /** Reference id of an already registered Spraute character. */
        public Expr existing;
        public Expr displayName, speed, skin;
        /** Colour of the name in speech and the chat history: "#FF8800" or a colour name. */
        public Expr color;
        /** How its body behaves, applied at spawn: "solid damage", "ghost"… */
        public Expr hitbox;
        /** Health given at spawn (max and current). */
        public Expr health;
        /** Speech icon: a project picture or a built-in icon ("anu_icon"). */
        public Expr icon;
        /** Face shown in choice dialogs: a project picture or a built-in icon. */
        public Expr portrait;
        /** Zoom of the head shown in chat when the character has no picture (1 — normal). */
        public Expr headScale;
        /** Shift of the head in the chat frame (percent of the frame) and its turn in degrees. */
        public Expr headX, headY, headYaw;
        /** Bone of the model shown in chat: "auto" (the head), "all", or a bone name. */
        public Expr headBone;
    }

    public static final class LocationDecl extends Node {
        public String name;
        public int nameStart, nameEnd;
        public Expr pos;
        public Expr yaw, pitch, dimension;
    }

    public static final class VarDecl extends Node {
        public String name;
        public int nameStart, nameEnd;
        public Expr init;
    }

    // ------------------------------------------------------------- statements

    public abstract static class Stmt extends Node {}

    public static final class Arg extends Node {
        /** Keyword that introduced this argument ("to", "speed"...), null for positional. */
        public String keyword;
        public Expr value;
        /** Name of the spec parameter this argument was bound to. */
        public String param;
    }

    public static final class CommandStmt extends Stmt {
        /** Full spec key, e.g. "move" or "wait flag". */
        public String key;
        public int nameStart, nameEnd;
        public final List<Arg> args = new ArrayList<>();

        public Expr arg(String param) {
            for (Arg a : args) if (param.equals(a.param)) return a.value;
            return null;
        }
    }

    public static final class LabelStmt extends Stmt {
        public String name;
    }

    public static final class GotoStmt extends Stmt {
        public String name;
        public int nameStart, nameEnd;
    }

    public static final class StopStmt extends Stmt {}

    /** {@code for x in list { }}: x takes each item (keys of a dictionary, 0..n-1 of a number). */
    public static final class ForStmt extends Stmt {
        public String var;
        public int varStart, varEnd;
        public Expr iter;
        public final List<Stmt> body = new ArrayList<>();
    }

    /** {@code every 5t { }} (and {@code on tick { }}): the body runs again and again in the background. */
    public static final class EveryStmt extends Stmt {
        public Expr interval;
        public final List<Stmt> body = new ArrayList<>();
    }

    public static final class BreakStmt extends Stmt {}

    public static final class ContinueStmt extends Stmt {}

    /** {@code enemies.add(z)}: a method that changes a list or dictionary variable. */
    public static final class MethodStmt extends Stmt {
        public String target;
        public int targetStart, targetEnd;
        public String method;
        public final List<Expr> args = new ArrayList<>();
    }

    public static final class SetStmt extends Stmt {
        public String name;
        public int nameStart, nameEnd;
        /** {@code set list[i] = v}: the index, or null. */
        public Expr index;
        /** {@code set z.health = 50}: the property, or null. */
        public String prop;
        public int propStart, propEnd;
        /** "=", "+=" or "-=". */
        public String op;
        public Expr value;
    }

    public static final class IfStmt extends Stmt {
        public Expr cond;
        public final List<Stmt> then = new ArrayList<>();
        /** Either null, a single nested {@link IfStmt} (else-if) or a plain block. */
        public List<Stmt> otherwise;
    }

    public static final class WhileStmt extends Stmt {
        public Expr cond;
        public final List<Stmt> body = new ArrayList<>();
    }

    public static final class RepeatStmt extends Stmt {
        public Expr count;
        public final List<Stmt> body = new ArrayList<>();
    }

    public static final class ChoiceStmt extends Stmt {
        public String character;
        public int characterStart, characterEnd;
        public Expr text;
        public Expr emotion;
        public final List<ChoiceOption> options = new ArrayList<>();
    }

    public static final class ChoiceOption extends Node {
        public Expr text;
        public final List<Stmt> body = new ArrayList<>();
    }

    /** {@code parallel name { }}, {@code async name { }} or anonymous {@code async { }} (name == null). */
    public static final class ParallelStmt extends Stmt {
        public String name;
        public final List<Stmt> body = new ArrayList<>();
    }

    /** {@code call f(1, 2)}; with {@link #async} it is {@code async call f() [as job]}. */
    public static final class CallStmt extends Stmt {
        public String name;
        public int nameStart, nameEnd;
        public final List<Expr> args = new ArrayList<>();
        public boolean async;
        /** Branch name of an async call, or null. */
        public String as;
        public int asStart, asEnd;
    }

    public static final class ReturnStmt extends Stmt {}

    /**
     * {@code on <wait-command> { ... }}: an event handler. Any {@code wait ...} command works as the
     * event ({@code on button "yes"}, {@code on key H}, {@code on click alice}); the body runs in the
     * background every time the event happens.
     */
    public static final class OnStmt extends Stmt {
        public CommandStmt event;
        public int eventStart, eventEnd;
        public final List<Stmt> body = new ArrayList<>();
    }

    /** {@code switch value { "a", "b" { } "c" { } else { } }}. */
    public static final class SwitchStmt extends Stmt {
        public Expr value;
        public final List<SwitchCase> cases = new ArrayList<>();
        public List<Stmt> otherwise;
    }

    public static final class SwitchCase extends Node {
        public final List<Expr> values = new ArrayList<>();
        public final List<Stmt> body = new ArrayList<>();
    }

    /** {@code await a, b} waits for async branches to finish. */
    public static final class AwaitStmt extends Stmt {
        public final List<String> names = new ArrayList<>();
        public final List<int[]> spans = new ArrayList<>();
    }

    /** {@code cancel job} stops an async branch. */
    public static final class CancelStmt extends Stmt {
        public String name;
        public int nameStart, nameEnd;
    }

    // ------------------------------------------------------------ expressions

    public abstract static class Expr extends Node {}

    public static final class NumberLit extends Expr {
        public double value;
    }

    public static final class DurationLit extends Expr {
        public int ticks;
    }

    public static final class StringLit extends Expr {
        public String value;
    }

    public static final class BoolLit extends Expr {
        public boolean value;
    }

    public static final class Ident extends Expr {
        public String name;
    }

    /** {@code (x, y, z)} */
    public static final class Tuple extends Expr {
        public final List<Expr> items = new ArrayList<>();
    }

    /** {@code [a, b, c]} */
    public static final class ListLit extends Expr {
        public final List<Expr> items = new ArrayList<>();
    }

    /** {@code {name: "Алиса", hp: 20}} */
    public static final class MapLit extends Expr {
        public final List<Expr> keys = new ArrayList<>();
        public final List<Expr> values = new ArrayList<>();
    }

    /** {@code list[i]}, {@code dict["key"]} */
    public static final class Index extends Expr {
        public Expr target, index;
    }

    /** {@code pos.x}, {@code list.len} (args == null) or {@code list.has(x)}. */
    public static final class Member extends Expr {
        public Expr target;
        public String name;
        public int nameStart, nameEnd;
        public List<Expr> args;
    }

    public static final class Unary extends Expr {
        public String op;
        public Expr operand;
    }

    public static final class Binary extends Expr {
        public String op;
        public Expr left, right;
    }

    public static final class Call extends Expr {
        public String name;
        public int nameStart, nameEnd;
        public final List<Expr> args = new ArrayList<>();
    }
}
