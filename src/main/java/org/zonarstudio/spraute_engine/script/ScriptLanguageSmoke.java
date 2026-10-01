package org.zonarstudio.spraute_engine.script;

import java.util.List;

/**
 * Lightweight language smoke checks (no Minecraft world required).
 * Run via: gradle :1.20.1:runScriptLanguageSmoke
 */
public final class ScriptLanguageSmoke {
    private ScriptLanguageSmoke() {}

    static int failures = 0;

    public static void main(String[] args) {
        expectParseOk("""
                create npc a {
                    model = "player"
                    x = 1
                    hitboxPreset = "player"
                    width = 0.6
                    height = 1.8
                }
                a.x = 2
                a.transform.x = 3
                await a.moveTo(4, 64, 0, 1.0)
                print(a.transform.y)
                """);

        expectParseOk("""
                create model prop {
                    model = "block/stone"
                    x = 0
                    y = 70
                    z = 0
                }
                prop.transform.rotationY = 90
                """);

        expectParseFail("create npc bad { model = }");

        // keyword used as property after dot must parse
        expectParseOk("""
                create npc k { model = "player" }
                print(k.model)
                """);

        expectParseOk("""
                create empty arena { x = 0 y = 64 z = 0 }
                create npc guard {
                    model = "player"
                    parent = arena
                    pos = 1, 64, 0
                }
                create hitbox zone {
                    parent = arena
                    width = 2
                    height = 2
                    depth = 2
                    solid = true
                }
                create model prop {
                    model = "block/stone"
                    parent = guard
                    y = 1
                }
                """);

        expectParseOk("""
                await leave()
                on leave() -> player_left {
                    print(_eventPlayer)
                }
                """);

        if (failures > 0) {
            System.err.println("ScriptLanguageSmoke FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ScriptLanguageSmoke OK");
    }

    static void expectParseOk(String src) {
        try {
            compile(src);
            System.out.println("  OK parse/compile");
        } catch (Exception e) {
            failures++;
            System.err.println("  FAIL expected OK: " + e.getMessage());
        }
    }

    static void expectParseFail(String src) {
        try {
            compile(src);
            failures++;
            System.err.println("  FAIL expected parse error");
        } catch (Exception e) {
            System.out.println("  OK rejected bad input");
        }
    }

    static CompiledScript compile(String src) {
        ScriptLexer lexer = new ScriptLexer(src);
        List<ScriptToken> tokens = lexer.tokenize();
        ScriptParser parser = new ScriptParser(tokens);
        List<ScriptNode> nodes = parser.parse();
        return new ScriptCompiler().compile("smoke", nodes);
    }
}
