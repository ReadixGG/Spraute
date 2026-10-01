package org.zonarstudio.spraute_engine.scene.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scene payload mirroring {@code create drop} — custom drops for a block or a mob.
 * {@code replace = true} disables the vanilla/base loot for that target.
 */
public final class SceneDropDef {
    public String id = "";
    /** {@code block} or {@code mob}. */
    public String target = "block";
    public String block = "minecraft:stone";
    public String mob = "minecraft:zombie";
    /** When true — clear vanilla/base drops before applying custom rules. */
    public boolean replace;
    /** XP orbs when the block is broken / mob dies. */
    public int xp;
    public List<SceneDropRule> drops = new ArrayList<>();

    /** Legacy single-drop fields (migrated by {@link #ensureDrops()}). */
    public String item = "minecraft:cobblestone";
    public int min = 1;
    public int max = 1;
    public int chance = 100;
    public String nbt = "";

    public boolean isMob() {
        return "mob".equalsIgnoreCase(normalizeTarget(target));
    }

    public static String normalizeTarget(String t) {
        if (t == null || t.isBlank()) return "block";
        String s = t.trim().toLowerCase(Locale.ROOT);
        if ("mob".equals(s) || "entity".equals(s) || "npc".equals(s)) return "mob";
        return "block";
    }

    public void ensureDrops() {
        target = normalizeTarget(target);
        if (drops == null) drops = new ArrayList<>();
        if (drops.isEmpty() && item != null && !item.isBlank()) {
            drops.add(new SceneDropRule(item, min, max, chance));
        }
        for (SceneDropRule r : drops) {
            if (r == null) continue;
            if (r.min < 0) r.min = 0;
            if (r.max < r.min) r.max = r.min;
            if (r.chance < 0) r.chance = 0;
            if (r.chance > 100) r.chance = 100;
        }
    }

    public SceneDropDef copy() {
        SceneDropDef c = new SceneDropDef();
        c.id = id;
        c.target = target;
        c.block = block;
        c.mob = mob;
        c.replace = replace;
        c.xp = xp;
        c.item = item;
        c.min = min;
        c.max = max;
        c.chance = chance;
        c.nbt = nbt;
        c.drops = new ArrayList<>();
        if (drops != null) {
            for (SceneDropRule r : drops) {
                if (r != null) c.drops.add(r.copy());
            }
        }
        return c;
    }
}
