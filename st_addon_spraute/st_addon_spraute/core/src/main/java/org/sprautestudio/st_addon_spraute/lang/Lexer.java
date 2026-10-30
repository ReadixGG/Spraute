package org.sprautestudio.st_addon_spraute.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokenizer for the Spraute Story language (.sst). Never throws: malformed input
 * produces {@link TokenType#ERROR} tokens so the editor can still highlight it.
 */
public final class Lexer {
    public static final Set<String> KEYWORDS = Set.of(
            "script", "character", "location", "var", "label", "goto",
            "if", "else", "while", "repeat", "set", "choice", "parallel",
            "true", "false", "and", "or", "not", "player", "stop",
            "func", "call", "return", "async", "await", "cancel", "on", "switch",
            "for", "in", "every", "break", "continue"
    );

    private static final String[] OPERATORS = {
            "==", "!=", "<=", ">=", "&&", "||", "->", "+=", "-=",
            "=", "<", ">", "+", "-", "*", "/", "%", "!", ":", "."
    };

    private final String src;
    private final boolean keepComments;
    private int pos, line, col;
    private final List<Token> out = new ArrayList<>();

    private Lexer(String src, boolean keepComments) {
        this.src = src;
        this.keepComments = keepComments;
    }

    /** Tokens for the parser: comments dropped, EOF appended. */
    public static List<Token> tokenize(String src) {
        return new Lexer(src, false).run();
    }

    /** Tokens for highlighting: comments kept, EOF appended. */
    public static List<Token> tokenizeAll(String src) {
        return new Lexer(src, true).run();
    }

    private List<Token> run() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                add(TokenType.NEWLINE, pos, pos + 1, null);
                advance(1);
            } else if (c == ' ' || c == '\t' || c == '\r') {
                advance(1);
            } else if (c == '/' && peek(1) == '/') {
                int s = pos;
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                comment(s);
            } else if (c == '#') {
                int s = pos;
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                comment(s);
            } else if (c == '/' && peek(1) == '*') {
                blockComment();
            } else if (c == '"') {
                string();
            } else if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) {
                number();
            } else if (Character.isLetter(c) || c == '_') {
                ident();
            } else if (c == '{') {
                single(TokenType.LBRACE);
            } else if (c == '}') {
                single(TokenType.RBRACE);
            } else if (c == '(') {
                single(TokenType.LPAREN);
            } else if (c == ')') {
                single(TokenType.RPAREN);
            } else if (c == '[') {
                single(TokenType.LBRACKET);
            } else if (c == ']') {
                single(TokenType.RBRACKET);
            } else if (c == ',') {
                single(TokenType.COMMA);
            } else if (c == ';') {
                // ';' works as a statement separator, same as a newline
                add(TokenType.NEWLINE, pos, pos + 1, null);
                advance(1);
            } else {
                operator();
            }
        }
        out.add(new Token(TokenType.EOF, "", src.length(), src.length(), line, col, null));
        return out;
    }

    private char peek(int off) {
        int p = pos + off;
        return p < src.length() ? src.charAt(p) : '\0';
    }

    /** Moves forward over {@code n} chars that contain no newline. */
    private void advance(int n) {
        for (int i = 0; i < n; i++) {
            if (src.charAt(pos) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
            pos++;
        }
    }

    private void add(TokenType type, int start, int end, Object value) {
        out.add(new Token(type, src.substring(start, end), start, end, line, col, value));
    }

    private void single(TokenType type) {
        add(type, pos, pos + 1, null);
        advance(1);
    }

    private void comment(int start) {
        int len = pos - start;
        pos = start;
        if (keepComments) add(TokenType.COMMENT, start, start + len, null);
        advance(len);
    }

    private void blockComment() {
        int start = pos, sl = line, sc = col;
        int endIdx = src.indexOf("*/", pos + 2);
        int end = endIdx < 0 ? src.length() : endIdx + 2;
        if (keepComments) out.add(new Token(TokenType.COMMENT, src.substring(start, end), start, end, sl, sc, null));
        advance(end - pos);
    }

    private void string() {
        int start = pos, sl = line, sc = col;
        StringBuilder sb = new StringBuilder();
        int p = pos + 1;
        boolean closed = false;
        while (p < src.length()) {
            char c = src.charAt(p);
            if (c == '"') {
                closed = true;
                p++;
                break;
            }
            if (c == '\n') break;
            if (c == '\\' && p + 1 < src.length()) {
                char n = src.charAt(p + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '{' -> sb.append("\\{");
                    default -> sb.append('\\').append(n);
                }
                p += 2;
                continue;
            }
            sb.append(c);
            p++;
        }
        out.add(new Token(closed ? TokenType.STRING : TokenType.ERROR, src.substring(start, p), start, p, sl, sc,
                closed ? sb.toString() : "Незакрытая строка"));
        advance(p - pos);
    }

    private void number() {
        int start = pos;
        int p = pos;
        while (p < src.length() && Character.isDigit(src.charAt(p))) p++;
        if (p < src.length() && src.charAt(p) == '.' && p + 1 < src.length() && Character.isDigit(src.charAt(p + 1))) {
            p++;
            while (p < src.length() && Character.isDigit(src.charAt(p))) p++;
        }
        double value = Double.parseDouble(src.substring(start, p));
        // duration suffix: 20t (ticks), 1.5s (seconds), 2m (minutes), 500ms
        int q = p;
        while (q < src.length() && Character.isLetter(src.charAt(q))) q++;
        String suffix = src.substring(p, q);
        if (!suffix.isEmpty()) {
            Integer ticks = switch (suffix) {
                case "t" -> (int) Math.round(value);
                case "s" -> (int) Math.round(value * 20);
                case "m" -> (int) Math.round(value * 20 * 60);
                case "ms" -> (int) Math.round(value / 50.0);
                default -> null;
            };
            if (ticks != null) {
                add(TokenType.DURATION, start, q, ticks);
            } else {
                out.add(new Token(TokenType.ERROR, src.substring(start, q), start, q, line, col,
                        "Неизвестная единица времени '" + suffix + "' (используйте t, s, m или ms)"));
            }
            advance(q - pos);
            return;
        }
        add(TokenType.NUMBER, start, p, value);
        advance(p - pos);
    }

    private void ident() {
        int start = pos;
        int p = pos;
        while (p < src.length() && (Character.isLetterOrDigit(src.charAt(p)) || src.charAt(p) == '_')) p++;
        String word = src.substring(start, p);
        add(KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENT, start, p, null);
        advance(p - pos);
        if (word.equals("js")) jsBlock();
    }

    /**
     * {@code js { … }}: JavaScript as it is, up to the brace that closes it (braces in its strings and
     * comments don't count) — one string token. Braces in it are kept from the story's {name} filling in.
     */
    private void jsBlock() {
        int p = pos;
        while (p < src.length() && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) p++;
        if (p >= src.length() || src.charAt(p) != '{') return;
        int start = p, depth = 0, sl = line, sc = col + (p - pos);
        boolean closed = false;
        while (p < src.length()) {
            char c = src.charAt(p);
            if (c == '"' || c == '\'' || c == '`') {
                char q = c;
                p++;
                while (p < src.length() && src.charAt(p) != q) {
                    if (src.charAt(p) == '\\') p++;
                    p++;
                }
                p++;
                continue;
            }
            if (c == '/' && p + 1 < src.length() && src.charAt(p + 1) == '/') {
                while (p < src.length() && src.charAt(p) != '\n') p++;
                continue;
            }
            if (c == '/' && p + 1 < src.length() && src.charAt(p + 1) == '*') {
                int e = src.indexOf("*/", p + 2);
                p = e < 0 ? src.length() : e + 2;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    p++;
                    closed = true;
                    break;
                }
            }
            p++;
        }
        p = Math.min(p, src.length());
        String code = closed ? src.substring(start + 1, p - 1) : src.substring(start + 1);
        advance(start - pos);
        out.add(new Token(closed ? TokenType.STRING : TokenType.ERROR, src.substring(start, p), start, p, line, col,
                closed ? code.replace("{", "\\{") : "Незакрытый блок js { … }"));
        advance(p - pos);
    }

    private void operator() {
        for (String op : OPERATORS) {
            if (src.startsWith(op, pos)) {
                add(TokenType.OPERATOR, pos, pos + op.length(), null);
                advance(op.length());
                return;
            }
        }
        out.add(new Token(TokenType.ERROR, src.substring(pos, pos + 1), pos, pos + 1, line, col,
                "Неожиданный символ '" + src.charAt(pos) + "'"));
        advance(1);
    }
}
