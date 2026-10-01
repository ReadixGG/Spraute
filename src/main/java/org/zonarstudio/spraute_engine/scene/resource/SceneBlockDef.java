package org.zonarstudio.spraute_engine.scene.resource;

import java.util.ArrayList;
import java.util.List;

/** Scene payload mirroring {@code create block}. */
public final class SceneBlockDef {
    public String id = "";
    /** texture | model | ore — controls which look fields the inspector shows. */
    public String lookKind = "texture";
    /** single | top_sides | all — for texture/ore look kinds. */
    public String texMode = "single";
    public String model = "";
    public String texture = "";
    public String textureUp = "";
    public String textureDown = "";
    public String textureSides = "";
    public String textureNorth = "";
    public String textureSouth = "";
    public String textureWest = "";
    public String textureEast = "";
    public boolean hasCollision = true;
    public int lightEmission;
    /** Destroy time (stone≈1.5, iron ore≈3, obsidian≈50). {@code < 0} = unbreakable. */
    public float hardness = 1.5f;
    /** Preferred dig tool: "" | pickaxe | axe | shovel | hoe */
    public String requiredTool = "";
    /** Min tool tier: 0=wood … 3=diamond · 4=netherite. Used when {@link #requiresCorrectTool}. */
    public int requiredMiningLevel;
    /** If true, wrong/low-tier tool digs slowly and drops nothing. */
    public boolean requiresCorrectTool;
    public String displayName = "";
    public String dropItem = "";
    public int dropCount = 1;
    public boolean dropReplace;
    /** Multi-drop table (preferred). Legacy {@link #dropItem}/{@link #dropCount} migrate via {@link #ensureDropDefaults()}. */
    public List<SceneDropRule> drops = new ArrayList<>();
    /** Experience orbs awarded when the block is broken (silk touch → 0). */
    public int dropXp;
    public String tab = "";
    public boolean directional = true;
    public float[] hitbox;
    public int sizeW = 1;
    public int sizeD = 1;
    public int sizeH = 1;
    public boolean isOre;
    public int oreVeinSize = 8;
    public int oreMinY = -64;
    public int oreMaxY = 64;
    public int oreChances = 10;
    public String oreDimension = "minecraft:overworld";

    /** Infer lookKind/texMode from filled fields (old scenes without these keys). */
    public void ensureLookDefaults() {
        if (lookKind == null || lookKind.isBlank()) {
            if (model != null && !model.isBlank()) lookKind = "model";
            else if (isOre) lookKind = "ore";
            else lookKind = "texture";
        }
        if (texMode == null || texMode.isBlank()) {
            if (notBlank(textureNorth) || notBlank(textureSouth)
                    || notBlank(textureWest) || notBlank(textureEast)) {
                texMode = "all";
            } else if (notBlank(textureUp) || notBlank(textureSides) || notBlank(textureDown)) {
                texMode = "top_sides";
            } else {
                texMode = "single";
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Migrate legacy single drop into {@link #drops}. */
    public void ensureDropDefaults() {
        if (drops == null) drops = new ArrayList<>();
        if (drops.isEmpty() && dropItem != null && !dropItem.isBlank()) {
            drops.add(new SceneDropRule(dropItem, dropCount, dropCount, 100));
        }
        for (SceneDropRule r : drops) {
            if (r == null) continue;
            if (r.min < 0) r.min = 0;
            if (r.max < r.min) r.max = r.min;
            if (r.chance < 0) r.chance = 0;
            if (r.chance > 100) r.chance = 100;
        }
    }

    public SceneBlockDef copy() {
        SceneBlockDef c = new SceneBlockDef();
        c.id = id;
        c.lookKind = lookKind;
        c.texMode = texMode;
        c.model = model;
        c.texture = texture;
        c.textureUp = textureUp;
        c.textureDown = textureDown;
        c.textureSides = textureSides;
        c.textureNorth = textureNorth;
        c.textureSouth = textureSouth;
        c.textureWest = textureWest;
        c.textureEast = textureEast;
        c.hasCollision = hasCollision;
        c.lightEmission = lightEmission;
        c.hardness = hardness;
        c.requiredTool = requiredTool;
        c.requiredMiningLevel = requiredMiningLevel;
        c.requiresCorrectTool = requiresCorrectTool;
        c.displayName = displayName;
        c.dropItem = dropItem;
        c.dropCount = dropCount;
        c.dropReplace = dropReplace;
        c.drops = new ArrayList<>();
        if (drops != null) {
            for (SceneDropRule r : drops) {
                if (r != null) c.drops.add(r.copy());
            }
        }
        c.dropXp = dropXp;
        c.tab = tab;
        c.directional = directional;
        if (hitbox != null) c.hitbox = hitbox.clone();
        c.sizeW = sizeW;
        c.sizeD = sizeD;
        c.sizeH = sizeH;
        c.isOre = isOre;
        c.oreVeinSize = oreVeinSize;
        c.oreMinY = oreMinY;
        c.oreMaxY = oreMaxY;
        c.oreChances = oreChances;
        c.oreDimension = oreDimension;
        return c;
    }
}
