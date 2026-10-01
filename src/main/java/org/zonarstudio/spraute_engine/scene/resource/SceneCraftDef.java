package org.zonarstudio.spraute_engine.scene.resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene payload mirroring {@code create craft}.
 * {@code type}: {@code slots}/{@code shaped}, {@code any}/{@code shapeless},
 * or {@code smelting}/{@code blasting}/{@code smoking}/{@code campfire_cooking}.
 */
public final class SceneCraftDef {
    public String id = "";
    public String type = "slots";
    /** 3×3 crafting grid (index 0 = slot_1). Empty string = empty slot. */
    public String[] slots = new String[]{"", "", "", "", "", "", "", "", ""};
    public List<String> ingredients = new ArrayList<>();
    public String ingredient = "";
    public String result = "minecraft:stone";
    public int count = 1;
    public float xp = 0.1f;
    public int time = 200;

    public SceneCraftDef copy() {
        SceneCraftDef c = new SceneCraftDef();
        c.id = id;
        c.type = type;
        c.slots = slots == null ? new String[9] : slots.clone();
        if (c.slots.length != 9) {
            String[] padded = new String[9];
            for (int i = 0; i < 9; i++) padded[i] = i < c.slots.length && c.slots[i] != null ? c.slots[i] : "";
            c.slots = padded;
        }
        c.ingredients = new ArrayList<>(ingredients == null ? List.of() : ingredients);
        c.ingredient = ingredient;
        c.result = result;
        c.count = count;
        c.xp = xp;
        c.time = time;
        return c;
    }

    public void ensureSlots() {
        if (slots == null || slots.length != 9) {
            String[] padded = new String[9];
            for (int i = 0; i < 9; i++) {
                padded[i] = slots != null && i < slots.length && slots[i] != null ? slots[i] : "";
            }
            slots = padded;
        }
        if (ingredients == null) ingredients = new ArrayList<>();
    }
}
