package org.sprautestudio.spraute_engine.editor.visual;

import org.junit.jupiter.api.Test;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.Specs;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerBlocksTest {
    private static VisualScript.Block block(String type, int slots, String... fields) {
        VisualScript.Block b = new VisualScript.Block();
        b.type = type;
        for (int i = 0; i < fields.length; i += 2) b.fields.put(fields[i], fields[i + 1]);
        for (int i = 0; i < slots; i++) b.slots.add(new ArrayList<>());
        return b;
    }

    private static BlockCodegen.Result generate(VisualScript.Block... blocks) {
        VisualScript script = new VisualScript();
        VisualScript.Stack stack = new VisualScript.Stack();
        stack.blocks.addAll(List.of(blocks));
        script.stacks.add(stack);
        return BlockCodegen.generate(script);
    }

    @Test
    void theBlocksExist() {
        for (String type : List.of("as", "val:actor", "fn:players", "fn:playercount", "fn:playerbyname", "fn:online")) assertNotNull(BlockTypes.get(type), type);
    }

    @Test
    void aStoryForEveryPlayerIsValidCode() {
        VisualScript.Block as = block("as", 1, "who", "p");
        VisualScript.Block each = block("for", 1, "var", "p");
        each.inputs.put("list", block("fn:players", 0));
        each.slots.get(0).add(as);
        as.slots.get(0).add(block("stop", 0));
        BlockCodegen.Result r = generate(block("start", 0, "id", "7100", "title", "Все", "solo", "да", "auto", "при входе"), each);
        assertTrue(r.text.contains("script 7100 \"Все\" autostart solo"), r.text);
        assertTrue(r.text.contains("for p in players() {"), r.text);
        assertTrue(r.text.contains("as p {"), r.text);
        Analyzer.Result checked = Analyzer.analyze(r.text);
        assertFalse(checked.hasErrors(), () -> r.text + checked.diagnostics);
        assertTrue(checked.program().header.solo);
    }

    @Test
    void withoutTheChoiceAStoryIsShared() {
        BlockCodegen.Result r = generate(block("start", 0, "id", "7101", "title", "Все", "solo", "нет"));
        assertFalse(r.text.contains("solo"), r.text);
    }

    @Test
    void theWordsAreExplained() {
        for (String word : List.of("solo", "autostart", "once", "as", "actor")) assertTrue(Specs.keywordDocs().containsKey(word), word);
    }

    @Test
    void aPlayerByNameIsValidCode() {
        Analyzer.Result r = Analyzer.analyze("""
                script 7102 "Ник"
                var p = playerbyname("Readix")
                if p and online("Readix") {
                    as p {
                        give "minecraft:bread"
                    }
                }
                """);
        assertFalse(r.hasErrors(), () -> r.diagnostics.toString());
    }
}

