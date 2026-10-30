package org.sprautestudio.st_addon_spraute.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerTest {
    private static String errors(Analyzer.Result r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics) if (d.isError()) sb.append(d.message).append('\n');
        return sb.toString();
    }

    @Test
    void aStoryForEveryPlayerReads() {
        Analyzer.Result r = Analyzer.analyze("""
                script 7001 "x" solo autostart once
                var n = 0
                on key H {
                  as actor {
                    say player "hi" nowait
                  }
                }
                for p in players() {
                  as p {
                    title "Глава"
                  }
                }
                set n = playercount()
                """);
        assertFalse(r.hasErrors(), () -> errors(r));
        assertTrue(r.program().header.solo);
    }

    @Test
    void asWithoutAPlayerIsAnError() {
        assertTrue(Analyzer.analyze("script 7003 \"z\"\nas {\n}\n").hasErrors());
    }

    @Test
    void anEmptyStoryIsNoCrash() {
        Analyzer.analyze("");
    }

    @Test
    void itemNbtGoesToTheCheckTheGameGave() {
        String src = "script 7005 \"n\"\ngive \"minecraft:stick{Damage:1}\" count 1\n";
        ItemText.setNbtCheck(text -> "bad");
        try {
            assertTrue(Analyzer.analyze(src).hasErrors());
        } finally {
            ItemText.setNbtCheck(text -> null);
        }
        Analyzer.Result fine = Analyzer.analyze(src);
        assertFalse(fine.hasErrors(), () -> errors(fine));
    }
}
