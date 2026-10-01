package org.zonarstudio.spraute_engine.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomDropRegistry {
    public static class DropRule {
        public String itemId;
        public int min;
        public int max;
        public int chance;
        public boolean replace;
        public String nbt;
        /** XP awarded once when the block is broken (create drop). */
        public int xp;
        
        public DropRule(String itemId, int min, int max, int chance, boolean replace, String nbt) {
            this(itemId, min, max, chance, replace, nbt, 0);
        }

        public DropRule(String itemId, int min, int max, int chance, boolean replace, String nbt, int xp) {
            this.itemId = itemId;
            this.min = min;
            this.max = max;
            this.chance = chance;
            this.replace = replace;
            this.nbt = nbt;
            this.xp = Math.max(0, xp);
        }
    }
    
    public static final Map<String, List<DropRule>> MOB_DROPS = new HashMap<>();
    public static final Map<String, List<DropRule>> BLOCK_DROPS = new HashMap<>();
    
    public static void addMobDrop(String mobId, String itemId, int min, int max, int chance, boolean replace, String nbt) {
        addMobDrop(mobId, itemId, min, max, chance, replace, nbt, 0);
    }

    public static void addMobDrop(String mobId, String itemId, int min, int max, int chance, boolean replace, String nbt, int xp) {
        if (!mobId.contains(":")) mobId = "minecraft:" + mobId;
        MOB_DROPS.computeIfAbsent(mobId, k -> new ArrayList<>()).add(new DropRule(itemId, min, max, chance, replace, nbt, xp));
    }
    
    public static void addBlockDrop(String blockId, String itemId, int min, int max, int chance, boolean replace, String nbt) {
        addBlockDrop(blockId, itemId, min, max, chance, replace, nbt, 0);
    }

    public static void addBlockDrop(String blockId, String itemId, int min, int max, int chance, boolean replace, String nbt, int xp) {
        if (!blockId.contains(":")) blockId = "minecraft:" + blockId;
        BLOCK_DROPS.computeIfAbsent(blockId, k -> new ArrayList<>()).add(new DropRule(itemId, min, max, chance, replace, nbt, xp));
    }
}