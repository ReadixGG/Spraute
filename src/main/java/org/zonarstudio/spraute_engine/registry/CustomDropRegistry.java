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
        
        public DropRule(String itemId, int min, int max, int chance, boolean replace) {
            this.itemId = itemId;
            this.min = min;
            this.max = max;
            this.chance = chance;
            this.replace = replace;
        }
    }
    
    public static final Map<String, List<DropRule>> MOB_DROPS = new HashMap<>();
    public static final Map<String, List<DropRule>> BLOCK_DROPS = new HashMap<>();
    
    public static void addMobDrop(String mobId, String itemId, int min, int max, int chance, boolean replace) {
        if (!mobId.contains(":")) mobId = "minecraft:" + mobId;
        MOB_DROPS.computeIfAbsent(mobId, k -> new ArrayList<>()).add(new DropRule(itemId, min, max, chance, replace));
    }
    
    public static void addBlockDrop(String blockId, String itemId, int min, int max, int chance, boolean replace) {
        if (!blockId.contains(":")) blockId = "minecraft:" + blockId;
        BLOCK_DROPS.computeIfAbsent(blockId, k -> new ArrayList<>()).add(new DropRule(itemId, min, max, chance, replace));
    }
}