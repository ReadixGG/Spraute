package org.zonarstudio.spraute_engine.scene.resource;

/** One weighted drop entry: item id, count range, chance %. */
public final class SceneDropRule {
    /** Sentinel: drop the block/NPC's own item (blocks only). */
    public static final String SELF = "self";

    public String itemId = "minecraft:stone";
    public int min = 1;
    public int max = 1;
    /** 0–100 */
    public int chance = 100;

    public SceneDropRule() {}

    public SceneDropRule(String itemId, int min, int max, int chance) {
        this.itemId = itemId == null ? "" : itemId;
        this.min = Math.max(0, min);
        this.max = Math.max(this.min, max);
        this.chance = Math.max(0, Math.min(100, chance));
    }

    public static SceneDropRule of(String itemId, int min, int max, int chance) {
        return new SceneDropRule(itemId, min, max, chance);
    }

    public static boolean isSelf(String itemId) {
        if (itemId == null) return false;
        String s = itemId.trim();
        return s.equalsIgnoreCase(SELF) || s.equalsIgnoreCase("this") || s.equalsIgnoreCase("itself");
    }

    public SceneDropRule copy() {
        return new SceneDropRule(itemId, min, max, chance);
    }
}
