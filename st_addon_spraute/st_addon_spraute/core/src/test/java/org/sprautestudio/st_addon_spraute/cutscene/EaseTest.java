package org.sprautestudio.st_addon_spraute.cutscene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaseTest {
    @Test
    void everyCurveGoesFromStartToEnd() {
        assertFalse(Ease.names().isEmpty());
        for (String name : Ease.names()) {
            assertTrue(Ease.known(name), name);
            assertEquals(0, Ease.apply(name, 0), 1e-6, name);
            assertEquals(1, Ease.apply(name, 1), 1e-6, name);
        }
    }
}
