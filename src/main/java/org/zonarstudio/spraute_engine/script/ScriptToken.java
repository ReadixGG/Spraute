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
        INCLUDE,      // include keyword
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

    public ScriptToken(TokenType type, String value, int line) {
        this.type = type;
        this.value = value;
        this.line = line;
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

    @Override
    public String toString() {
        return "Token{" + type + ", '" + value + "', line=" + line + "}";
    }
}
