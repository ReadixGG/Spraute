package org.zonarstudio.spraute_engine.script;

/**
 * Represents a single token produced by the ScriptLexer.
 */
public class ScriptToken {

    public enum TokenType {
        IDENTIFIER,   // function names, variable names
        VAL,          // val keyword
        VAR,          // var keyword (mutable variable)
        AWAIT,        // await keyword
        CREATE,       // create keyword
        NPC,          // npc keyword
        UI,           // ui keyword (create ui)
        COMMAND,      // command keyword (create command)
        TRUE,         // true keyword
        FALSE,        // false keyword
        NULL,         // null literal
        WHILE,        // while keyword
        FOR,          // for keyword
        IN,           // in keyword
        IF,           // if keyword
        ELSE,         // else keyword
        AND,          // && operator
        OR,           // || operator
        NOT,          // ! operator
        FUN,          // fun keyword
        RETURN,       // return keyword
        ON,           // on keyword
        EVERY,        // every keyword
        STOP,         // stop keyword
        ASYNC,        // async keyword
        TASK,         // task keyword (for await task)
        GLOBAL,       // global keyword
        WORLD,        // world keyword (persistent in world)
        INCLUDE,      // include / import keyword
        TRY,          // try keyword
        CATCH,        // catch keyword
        CAMERA,       // camera keyword
        ARROW,        // -> operator
        STRING,       // string literals "..."
        NUMBER,       // 123, 123.45
        DOT,          // .
        ASSIGN,       // =
        PLUS,         // +
        MINUS,        // -
        STAR,         // *
        SLASH,        // /
        SLASH_SLASH,  // // целочисленное деление (floor)
        PERCENT,      // % остаток от деления
        STAR_STAR,    // **
        EQ,           // ==
        NEQ,          // !=
        GT,           // >
        LT,           // <
        GTE,          // >=
        LTE,          // <=
        LPAREN,       // (
        RPAREN,       // )
        LBRACE,       // {
        RBRACE,       // }
        COMMA,        // ,
        LBRACKET,     // [
        RBRACKET,     // ]
        NEWLINE,      // line separator
        EOF           // end of input
    }

    private final TokenType type;
    private final String value;
    private final int line;
    private final int column;

    public ScriptToken(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    public TokenType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return "Token{" + type + ", '" + value + "', line=" + line + ", col=" + column + "}";
    }
}
