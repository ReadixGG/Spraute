package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer that tokenizes .spr script files into a list of ScriptTokens.
 * <p>Строковые комментарии только через {@code #} до конца строки.
 * Оператор {@code //} — целочисленное деление (floor), не комментарий.
 */
public class ScriptLexer {

    private final String source;
    private int pos;
    private int line;

    public ScriptLexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
    }

    /**
     * Tokenize the entire source into a list of tokens.
     */
    public List<ScriptToken> tokenize() {
        List<ScriptToken> tokens = new ArrayList<>();

        while (pos < source.length()) {
            char c = source.charAt(pos);

            // Skip spaces and tabs
            if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
                continue;
            }

            // Newline
            if (c == '\n') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.NEWLINE, "\\n", line));
                line++;
                pos++;
                continue;
            }

            // Single-line comment: # ...
            if (c == '#') {
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }

            // Braces and Parentheses
            if (c == '(') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.LPAREN, "(", line));
                pos++;
                continue;
            }
            if (c == ')') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.RPAREN, ")", line));
                pos++;
                continue;
            }
            if (c == '{') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.LBRACE, "{", line));
                pos++;
                continue;
            }
            if (c == '}') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.RBRACE, "}", line));
                pos++;
                continue;
            }
            if (c == ',') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.COMMA, ",", line));
                pos++;
                continue;
            }
            if (c == '[') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.LBRACKET, "[", line));
                pos++;
                continue;
            }
            if (c == ']') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.RBRACKET, "]", line));
                pos++;
                continue;
            }
            if (c == '.') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.DOT, ".", line));
                pos++;
                continue;
            }

            // Assignment and Comparators
            if (c == '=') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.EQ, "==", line));
                    pos += 2;
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.ASSIGN, "=", line));
                    pos++;
                }
                continue;
            }
            if (c == '!') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.NEQ, "!=", line));
                    pos += 2;
                    continue;
                }
                tokens.add(new ScriptToken(ScriptToken.TokenType.NOT, "!", line));
                pos++;
                continue;
            }
            if (c == '&') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '&') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.AND, "&&", line));
                    pos += 2;
                    continue;
                }
            }
            if (c == '|') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '|') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.OR, "||", line));
                    pos += 2;
                    continue;
                }
            }
            if (c == '>') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.GTE, ">=", line));
                    pos += 2;
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.GT, ">", line));
                    pos++;
                }
                continue;
            }
            if (c == '<') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.LTE, "<=", line));
                    pos += 2;
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.LT, "<", line));
                    pos++;
                }
                continue;
            }

            // Arithmetic
            if (c == '+') {
                tokens.add(new ScriptToken(ScriptToken.TokenType.PLUS, "+", line));
                pos++;
                continue;
            }
            if (c == '-') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '>') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.ARROW, "->", line));
                    pos += 2;
                } else if (pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1))) {
                    tokens.add(readNumber());
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.MINUS, "-", line));
                    pos++;
                }
                continue;
            }
            if (c == '*') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.STAR_STAR, "**", line));
                    pos += 2;
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.STAR, "*", line));
                    pos++;
                }
                continue;
            }
            if (c == '/') {
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.SLASH_SLASH, "//", line));
                    pos += 2;
                } else {
                    tokens.add(new ScriptToken(ScriptToken.TokenType.SLASH, "/", line));
                    pos++;
                }
                continue;
            }

            // String literal
            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            // Numbers (handled in - too)
            if (Character.isDigit(c) || c == '.') {
                tokens.add(readNumber());
                continue;
            }

            // Identifier (function names, variables, keywords)
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifier());
                continue;
            }

            // Skip unknown Unicode characters silently (em-dashes, special symbols in comments etc.)
            pos++;
            continue;
        }

        tokens.add(new ScriptToken(ScriptToken.TokenType.EOF, "", line));
        return tokens;
    }

    private ScriptToken readNumber() {
        int start = pos;
        boolean hasDot = false;
        
        if (source.charAt(pos) == '-') pos++;
        
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '.') {
                if (hasDot) break;
                hasDot = true;
            } else if (!Character.isDigit(c)) {
                break;
            }
            pos++;
        }
        
        String value = source.substring(start, pos);
        return new ScriptToken(ScriptToken.TokenType.NUMBER, value, line);
    }

    private ScriptToken readString() {
        int startLine = line;
        pos++; // skip opening "
        StringBuilder sb = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (c == '\\' && pos + 1 < source.length()) {
                char next = source.charAt(pos + 1);
                switch (next) {
                    case '"':  sb.append('"');  pos += 2; continue;
                    case '\\': sb.append('\\'); pos += 2; continue;
                    case 'n':  sb.append('\n'); pos += 2; continue;
                    default:   sb.append(c);    pos++;    continue;
                }
            }

            if (c == '"') {
                pos++; // skip closing "
                return new ScriptToken(ScriptToken.TokenType.STRING, sb.toString(), startLine);
            }

            if (c == '\n') {
                line++;
            }

            sb.append(c);
            pos++;
        }

        throw new ScriptException("Unterminated string literal", startLine);
    }

    private ScriptToken readIdentifier() {
        int start = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            pos++;
        }
        String value = source.substring(start, pos);
        
        if (value.equals("val")) {
            return new ScriptToken(ScriptToken.TokenType.VAL, value, line);
        }
        if (value.equals("var")) {
            return new ScriptToken(ScriptToken.TokenType.VAR, value, line);
        }
        if (value.equals("await")) {
            return new ScriptToken(ScriptToken.TokenType.AWAIT, value, line);
        }
        if (value.equals("create")) {
            return new ScriptToken(ScriptToken.TokenType.CREATE, value, line);
        }
        if (value.equals("npc")) {
            return new ScriptToken(ScriptToken.TokenType.NPC, value, line);
        }
        if (value.equals("ui")) {
            return new ScriptToken(ScriptToken.TokenType.UI, value, line);
        }
        if (value.equals("true")) {
            return new ScriptToken(ScriptToken.TokenType.TRUE, value, line);
        }
        if (value.equals("false")) {
            return new ScriptToken(ScriptToken.TokenType.FALSE, value, line);
        }
        if (value.equals("null")) {
            return new ScriptToken(ScriptToken.TokenType.NULL, value, line);
        }
        if (value.equals("while")) {
            return new ScriptToken(ScriptToken.TokenType.WHILE, value, line);
        }
        if (value.equals("for")) {
            return new ScriptToken(ScriptToken.TokenType.FOR, value, line);
        }
        if (value.equals("in")) {
            return new ScriptToken(ScriptToken.TokenType.IN, value, line);
        }
        if (value.equals("if")) {
            return new ScriptToken(ScriptToken.TokenType.IF, value, line);
        }
        if (value.equals("else")) {
            return new ScriptToken(ScriptToken.TokenType.ELSE, value, line);
        }
        if (value.equals("fun")) {
            return new ScriptToken(ScriptToken.TokenType.FUN, value, line);
        }
        if (value.equals("return")) {
            return new ScriptToken(ScriptToken.TokenType.RETURN, value, line);
        }
        if (value.equals("on")) {
            return new ScriptToken(ScriptToken.TokenType.ON, value, line);
        }
        if (value.equals("every")) {
            return new ScriptToken(ScriptToken.TokenType.EVERY, value, line);
        }
        if (value.equals("stop")) {
            return new ScriptToken(ScriptToken.TokenType.STOP, value, line);
        }
        if (value.equals("async")) {
            return new ScriptToken(ScriptToken.TokenType.ASYNC, value, line);
        }
        if (value.equals("task")) {
            return new ScriptToken(ScriptToken.TokenType.TASK, value, line);
        }
        if (value.equals("global")) {
            return new ScriptToken(ScriptToken.TokenType.GLOBAL, value, line);
        }
        if (value.equals("world")) {
            return new ScriptToken(ScriptToken.TokenType.WORLD, value, line);
        }
        if (value.equals("include")) {
            return new ScriptToken(ScriptToken.TokenType.INCLUDE, value, line);
        }
        
        return new ScriptToken(ScriptToken.TokenType.IDENTIFIER, value, line);
    }
}
