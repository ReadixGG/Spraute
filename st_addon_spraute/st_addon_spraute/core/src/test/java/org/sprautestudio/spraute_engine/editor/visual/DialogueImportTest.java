package org.sprautestudio.spraute_engine.editor.visual;

import org.junit.jupiter.api.Test;
import org.sprautestudio.spraute_engine.lang.Analyzer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueImportTest {
    @Test
    void textAndBlocksUseTheSameSpeakersAndTransitions() {
        String existing = "script 9003 \"Разговор\"\ncharacter alice = \"spraute:npc\" name \"Алиса\"\n";
        var lines = DialogueImport.parse("Алиса: Привет!\nБорис: Где мы?\nАлиса: Здесь.");
        var transitions = List.of(
                new DialogueImport.Transition(DialogueImport.Trigger.KEY, "H"),
                new DialogueImport.Transition(DialogueImport.Trigger.NPC, "Борис"));
        var plan = DialogueImport.plan(existing, lines, transitions);

        assertTrue(plan.text().contains("character speaker_1 = \"spraute:npc\" name \"Борис\""));
        assertTrue(plan.text().contains("say alice \"Привет!\" nowait\nwait key H"));
        assertTrue(plan.text().contains("say speaker_1 \"Где мы?\" nowait\nwait click speaker_1"));
        assertFalse(Analyzer.analyze(plan.text()).hasErrors(), plan.text());

        VisualScript visual = VisualScript.create(9003, "Разговор");
        VisualScript.Stack stack = visual.mainStack();
        stack.blocks.clear();
        VisualScript.Block start = VisualScript.Block.create("start");
        start.fields.put("id", "9003");
        start.fields.put("title", "Разговор");
        stack.blocks.add(start);
        VisualScript.Block alice = VisualScript.Block.create("character");
        alice.fields.put("name", "alice");
        alice.fields.put("type", "spraute:npc");
        alice.fields.put("display", "Алиса");
        stack.blocks.add(alice);
        stack.blocks.addAll(plan.blocks());
        BlockCodegen.Result generated = BlockCodegen.generate(visual);
        assertFalse(generated.hasErrors(), generated.text + " " + generated.problems);
        assertTrue(generated.text.contains("say alice \"Привет!\" nowait\nwait key H"), generated.text);
        assertTrue(generated.text.contains("wait click speaker_1"), generated.text);
        assertEquals(1, generated.text.split("character alice =", -1).length - 1);
    }

    @Test
    void f8UsesTheNormalSayWaitAndBadInputIsRejected() {
        var plan = DialogueImport.plan("script 1 \"Test\"", DialogueImport.parse("player: Hello\nAlice: Hi"),
                List.of(new DialogueImport.Transition(DialogueImport.Trigger.F8, "")));
        assertTrue(plan.text().contains("say player \"Hello\"\nsay speaker_1 \"Hi\""));
        assertFalse(plan.text().contains("wait key F8"));
        assertThrows(IllegalArgumentException.class, () -> DialogueImport.parse("Alice Hello"));
    }
}
