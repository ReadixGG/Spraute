package org.zonarstudio.spraute_engine.script;

import org.zonarstudio.spraute_engine.script.function.FunctionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Export-time .spr obfuscation: strip comments, minify, rename val/var/fun/global/world/for
 * bindings and on/every handler ids. Strings, API names, create/camera ids and
 * {@code on}/{@code await} event names stay intact. Not encryption.
 */
public final class ScriptObfuscator {
    private ScriptObfuscator() {}

    public static Map<String, String> buildRenameMap(Collection<String> sources) {
        Set<String> frozen = new HashSet<>();
        for (String name : FunctionRegistry.allNames()) {
            freeze(frozen, name);
        }
        for (String extra : CONTEXT_IDS) freeze(frozen, extra);

        List<List<ScriptToken>> all = new ArrayList<>();
        for (String src : sources) {
            if (src == null) continue;
            all.add(new ScriptLexer(src).tokenize());
        }
        for (List<ScriptToken> tokens : all) {
            collectFrozenIds(tokens, frozen);
            collectCreateScopedFrozen(tokens, frozen);
        }

        Set<String> declared = new TreeSet<>();
        for (List<ScriptToken> tokens : all) collectDeclared(tokens, frozen, declared);

        Map<String, String> map = new HashMap<>();
        int n = 0;
        for (String name : declared) {
            String repl;
            do {
                repl = "o" + n++;
            } while (isFrozen(frozen, repl) || declared.contains(repl) || map.containsValue(repl));
            map.put(name, repl);
        }
        return map;
    }

    public static String obfuscate(String source, Map<String, String> names) {
        if (source == null) return "";
        List<ScriptToken> tokens = new ScriptLexer(source).tokenize();
        return emit(tokens, names == null ? Map.of() : names);
    }

    private static final String[] CONTEXT_IDS = {
            "player", "npc", "mob", "event", "first", "custom",
            "_eventPlayer", "_node", "_scene", "_this", "_event",
            "any", "self", "world", "overworld", "nether", "end",
            "autorun", "declare", "section", "node", "type"
    };

    private static void collectFrozenIds(List<ScriptToken> tokens, Set<String> frozen) {
        for (int i = 0; i < tokens.size(); i++) {
            ScriptToken t = tokens.get(i);
            ScriptToken.TokenType ty = t.getType();
            if (ty == ScriptToken.TokenType.CREATE) {
                int j = nextNonNl(tokens, i + 1);
                if (j < 0) continue;
                freeze(frozen, tokens.get(j).getValue());
                j = nextNonNl(tokens, j + 1);
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                    freeze(frozen, tokens.get(j).getValue());
                }
            } else if (ty == ScriptToken.TokenType.CAMERA
                    || ty == ScriptToken.TokenType.ON
                    || ty == ScriptToken.TokenType.AWAIT) {
                int j = nextNonNl(tokens, i + 1);
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                    freeze(frozen, tokens.get(j).getValue());
                }
            } else if (ty == ScriptToken.TokenType.INCLUDE) {
                int j = nextNonNl(tokens, i + 1);
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.LPAREN) {
                    j = nextNonNl(tokens, j + 1);
                }
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                    freeze(frozen, tokens.get(j).getValue());
                }
            } else if (ty == ScriptToken.TokenType.IDENTIFIER) {
                String v = t.getValue();
                if ("autorun".equals(v)) {
                    int j = nextNonNl(tokens, i + 1);
                    if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                        freeze(frozen, tokens.get(j).getValue());
                    }
                } else if ("declare".equals(v)) {
                    int j = nextNonNl(tokens, i + 1);
                    while (j >= 0) {
                        ScriptToken.TokenType nt = tokens.get(j).getType();
                        if (nt == ScriptToken.TokenType.LBRACE || nt == ScriptToken.TokenType.NEWLINE) break;
                        if (nt == ScriptToken.TokenType.IDENTIFIER) freeze(frozen, tokens.get(j).getValue());
                        j = nextNonNl(tokens, j + 1);
                    }
                }
            }
        }
    }

    /** Property / widget / event names inside create/camera bodies must stay readable to the engine. */
    private static void collectCreateScopedFrozen(List<ScriptToken> tokens, Set<String> frozen) {
        boolean pendingCreate = false;
        int depth = 0;
        int createDepth = -1;
        for (int i = 0; i < tokens.size(); i++) {
            ScriptToken.TokenType ty = tokens.get(i).getType();
            if (ty == ScriptToken.TokenType.CREATE) {
                int j = nextNonNl(tokens, i + 1);
                pendingCreate = j < 0 || tokens.get(j).getType() != ScriptToken.TokenType.COMMAND;
            } else if (ty == ScriptToken.TokenType.CAMERA) {
                pendingCreate = true;
            }
            if (ty == ScriptToken.TokenType.LBRACE) {
                depth++;
                if (pendingCreate) {
                    createDepth = depth;
                    pendingCreate = false;
                }
            }
            if (createDepth >= 0 && depth >= createDepth && ty == ScriptToken.TokenType.IDENTIFIER) {
                ScriptToken.TokenType prev = prevNonNlType(tokens, i);
                int nxt = nextNonNl(tokens, i + 1);
                if (nxt >= 0 && tokens.get(nxt).getType() == ScriptToken.TokenType.ASSIGN
                        && prev != ScriptToken.TokenType.VAL && prev != ScriptToken.TokenType.VAR
                        && prev != ScriptToken.TokenType.GLOBAL && prev != ScriptToken.TokenType.WORLD) {
                    freeze(frozen, tokens.get(i).getValue());
                } else if (nxt >= 0 && tokens.get(nxt).getType() == ScriptToken.TokenType.LBRACE
                        && prev != ScriptToken.TokenType.TRY && prev != ScriptToken.TokenType.CATCH
                        && prev != ScriptToken.TokenType.ELSE && prev != ScriptToken.TokenType.FUN) {
                    freeze(frozen, tokens.get(i).getValue());
                } else if (nxt >= 0 && tokens.get(nxt).getType() == ScriptToken.TokenType.LPAREN
                        && prev != ScriptToken.TokenType.FUN && prev != ScriptToken.TokenType.ON
                        && prev != ScriptToken.TokenType.AWAIT) {
                    int close = skipBalanced(tokens, nxt, ScriptToken.TokenType.LPAREN, ScriptToken.TokenType.RPAREN);
                    int after = close < 0 ? -1 : nextNonNl(tokens, close + 1);
                    if (after >= 0 && tokens.get(after).getType() == ScriptToken.TokenType.LBRACE) {
                        freeze(frozen, tokens.get(i).getValue());
                    }
                }
            }
            if (ty == ScriptToken.TokenType.RBRACE) {
                if (depth == createDepth) createDepth = -1;
                if (depth > 0) depth--;
            }
        }
    }

    private static ScriptToken.TokenType prevNonNlType(List<ScriptToken> tokens, int i) {
        int j = i - 1;
        while (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.NEWLINE) j--;
        return j < 0 ? null : tokens.get(j).getType();
    }

    private static int skipBalanced(List<ScriptToken> tokens, int open, ScriptToken.TokenType openTy,
                                    ScriptToken.TokenType closeTy) {
        if (open < 0 || tokens.get(open).getType() != openTy) return -1;
        int d = 0;
        for (int i = open; i < tokens.size(); i++) {
            ScriptToken.TokenType ty = tokens.get(i).getType();
            if (ty == openTy) d++;
            else if (ty == closeTy) {
                d--;
                if (d == 0) return i;
            }
        }
        return -1;
    }

    private static void collectDeclared(List<ScriptToken> tokens, Set<String> frozen, Set<String> declared) {
        for (int i = 0; i < tokens.size(); i++) {
            ScriptToken.TokenType ty = tokens.get(i).getType();
            if (ty == ScriptToken.TokenType.VAL || ty == ScriptToken.TokenType.VAR
                    || ty == ScriptToken.TokenType.GLOBAL || ty == ScriptToken.TokenType.WORLD
                    || ty == ScriptToken.TokenType.FOR) {
                addNextIdent(tokens, i + 1, frozen, declared);
            } else if (ty == ScriptToken.TokenType.FUN) {
                int nameAt = addNextIdent(tokens, i + 1, frozen, declared);
                if (nameAt < 0) continue;
                int j = nextNonNl(tokens, nameAt + 1);
                if (j < 0 || tokens.get(j).getType() != ScriptToken.TokenType.LPAREN) continue;
                j = nextNonNl(tokens, j + 1);
                while (j >= 0 && tokens.get(j).getType() != ScriptToken.TokenType.RPAREN) {
                    if (tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                        maybeDeclare(tokens.get(j).getValue(), frozen, declared);
                    }
                    j = nextNonNl(tokens, j + 1);
                }
            } else if (ty == ScriptToken.TokenType.CATCH) {
                int j = nextNonNl(tokens, i + 1);
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.LPAREN) {
                    j = nextNonNl(tokens, j + 1);
                }
                if (j >= 0 && tokens.get(j).getType() == ScriptToken.TokenType.IDENTIFIER) {
                    maybeDeclare(tokens.get(j).getValue(), frozen, declared);
                }
            } else if (ty == ScriptToken.TokenType.ARROW) {
                addNextIdent(tokens, i + 1, frozen, declared);
            }
        }
    }

    private static int addNextIdent(List<ScriptToken> tokens, int from, Set<String> frozen, Set<String> declared) {
        int j = nextNonNl(tokens, from);
        if (j < 0 || tokens.get(j).getType() != ScriptToken.TokenType.IDENTIFIER) return -1;
        maybeDeclare(tokens.get(j).getValue(), frozen, declared);
        return j;
    }

    private static void maybeDeclare(String name, Set<String> frozen, Set<String> declared) {
        if (name == null || name.isBlank() || isFrozen(frozen, name)) return;
        declared.add(name);
    }

    private static String emit(List<ScriptToken> tokens, Map<String, String> names) {
        StringBuilder out = new StringBuilder(Math.max(32, tokens.size() * 4));
        ScriptToken prev = null;
        for (ScriptToken t : tokens) {
            if (t.getType() == ScriptToken.TokenType.EOF) break;
            if (t.getType() == ScriptToken.TokenType.NEWLINE) {
                if (prev != null && prev.getType() != ScriptToken.TokenType.NEWLINE) {
                    out.append('\n');
                    prev = t;
                }
                continue;
            }
            String text = textOf(t, names, prev);
            if (prev != null && prev.getType() != ScriptToken.TokenType.NEWLINE && needsSpace(prev, t)) {
                out.append(' ');
            }
            out.append(text);
            prev = t;
        }
        return out.toString().trim() + '\n';
    }

    private static String textOf(ScriptToken t, Map<String, String> names, ScriptToken prev) {
        if (t.getType() == ScriptToken.TokenType.STRING) {
            return '"' + escape(t.getValue()) + '"';
        }
        if (t.getType() == ScriptToken.TokenType.IDENTIFIER) {
            if (prev != null && prev.getType() == ScriptToken.TokenType.DOT) return t.getValue();
            String mapped = names.get(t.getValue());
            return mapped != null ? mapped : t.getValue();
        }
        return t.getValue();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static boolean needsSpace(ScriptToken a, ScriptToken b) {
        return isWord(a.getType()) && isWord(b.getType());
    }

    private static boolean isWord(ScriptToken.TokenType ty) {
        return switch (ty) {
            case IDENTIFIER, VAL, VAR, AWAIT, CREATE, NPC, MODEL, UI, COMMAND, TRUE, FALSE, NULL,
                    WHILE, FOR, IN, IF, ELSE, FUN, RETURN, ON, EVERY, STOP, ASYNC, TASK,
                    GLOBAL, WORLD, INCLUDE, TRY, CATCH, CAMERA, NUMBER -> true;
            default -> false;
        };
    }

    private static void freeze(Set<String> frozen, String name) {
        if (name == null || name.isBlank()) return;
        frozen.add(name);
        frozen.add(name.toLowerCase(Locale.ROOT));
    }

    private static boolean isFrozen(Set<String> frozen, String name) {
        if (name == null) return true;
        return frozen.contains(name) || frozen.contains(name.toLowerCase(Locale.ROOT));
    }

    private static int nextNonNl(List<ScriptToken> tokens, int i) {
        while (i < tokens.size()) {
            ScriptToken.TokenType ty = tokens.get(i).getType();
            if (ty == ScriptToken.TokenType.EOF) return -1;
            if (ty != ScriptToken.TokenType.NEWLINE) return i;
            i++;
        }
        return -1;
    }
}
