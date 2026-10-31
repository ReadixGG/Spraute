package org.sprautestudio.spraute_engine.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemTextTest {
    @Test
    void anIdGetsItsNamespace() {
        assertEquals("minecraft:stick", ItemText.id("stick"));
        assertEquals("mod:stick", ItemText.id(" mod:stick "));
        assertEquals("", ItemText.id(null));
    }

    @Test
    void nbtIsWhatFollowsTheId() {
        String spec = "diamond_sword{Damage:5,Enchantments:[{id:\"sharpness\",lvl:3s}]}";
        assertEquals("minecraft:diamond_sword", ItemText.id(spec));
        assertEquals("{Damage:5,Enchantments:[{id:\"sharpness\",lvl:3s}]}", ItemText.nbt(spec));
        assertEquals("{}", ItemText.nbt("stick{}"));
    }

    @Test
    void aVariableInTheIdIsNotNbt() {
        assertEquals("minecraft:{name}", ItemText.id("{name}"));
        assertEquals("", ItemText.nbt("{name}"));
    }

    @Test
    void noNbtNoError() {
        ItemText.setNbtCheck(text -> "bad");
        try {
            assertNull(ItemText.error("stick"));
            assertEquals("bad", ItemText.error("stick{a:1}"));
        } finally {
            ItemText.setNbtCheck(text -> null);
        }
    }
}
