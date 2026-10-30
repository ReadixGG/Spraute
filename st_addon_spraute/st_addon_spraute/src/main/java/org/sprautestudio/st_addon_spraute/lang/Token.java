package org.sprautestudio.st_addon_spraute.lang;

/**
 * A lexical token. {@code start}/{@code end} are absolute offsets in the source,
 * {@code line}/{@code col} are zero-based and refer to {@code start}.
 */
public final class Token {
    public final TokenType type;
    public final String text;
    public final int start, end;
    public final int line, col;
    /** Parsed value for NUMBER (Double), DURATION (Integer ticks) and STRING (unescaped String). */
    public final Object value;

    public Token(TokenType type, String text, int start, int end, int line, int col, Object value) {
        this.type = type;
        this.text = text;
        this.start = start;
        this.end = end;
        this.line = line;
        this.col = col;
        this.value = value;
    }

    public boolean is(TokenType t) {
        return type == t;
    }

    public boolean is(TokenType t, String s) {
        return type == t && text.equals(s);
    }

    public boolean isWord(String s) {
        return (type == TokenType.IDENT || type == TokenType.KEYWORD) && text.equals(s);
    }

    public boolean isOp(String s) {
        return type == TokenType.OPERATOR && text.equals(s);
    }

    @Override
    public String toString() {
        return type + "'" + text + "'@" + line + ":" + col;
    }
}
