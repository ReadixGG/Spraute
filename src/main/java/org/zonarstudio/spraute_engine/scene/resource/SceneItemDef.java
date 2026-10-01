package org.zonarstudio.spraute_engine.scene.resource;

/** Scene payload mirroring {@code create item}. */
public final class SceneItemDef {
    public String id = "";
    /** flat | geo — controls which look fields the inspector shows. */
    public String lookKind = "flat";
    public String model = "";
    public String texture = "";
    public String displayName = "";
    public int maxStackSize = 64;
    public String tab = "";
    /** sword / pickaxe / axe / shovel / hoe, or empty for plain item */
    public String toolType = "";
    /** helmet / chestplate / leggings / boots, or empty */
    public String armorType = "";
    public String armorTexture = "";
    /** {@code null} = hide worn mesh when {@code armorTexture} is empty. */
    public Boolean armorModel;
    public Integer armorDefense;
    public Float armorToughness;
    public Float armorKnockback;
    public Float damage;
    public Float attackSpeed;
    public Integer durability;
    public Float miningSpeed;
    public Integer miningLevel;
    public String geo = "";
    /** Display transforms: {@code ox,oy,oz,rx,ry,rz,scale} */
    public String handFirst = "";
    public String handThird = "";
    public String handGround = "";
    public String gui = "";

    /** Infer lookKind from filled fields (old scenes without this key). */
    public void ensureLookDefaults() {
        if (lookKind == null || lookKind.isBlank()) {
            lookKind = (geo != null && !geo.isBlank()) ? "geo" : "flat";
        }
    }

    public SceneItemDef copy() {
        SceneItemDef c = new SceneItemDef();
        c.id = id;
        c.lookKind = lookKind;
        c.model = model;
        c.texture = texture;
        c.displayName = displayName;
        c.maxStackSize = maxStackSize;
        c.tab = tab;
        c.toolType = toolType;
        c.armorType = armorType;
        c.armorTexture = armorTexture;
        c.armorModel = armorModel;
        c.armorDefense = armorDefense;
        c.armorToughness = armorToughness;
        c.armorKnockback = armorKnockback;
        c.damage = damage;
        c.attackSpeed = attackSpeed;
        c.durability = durability;
        c.miningSpeed = miningSpeed;
        c.miningLevel = miningLevel;
        c.geo = geo;
        c.handFirst = handFirst;
        c.handThird = handThird;
        c.handGround = handGround;
        c.gui = gui;
        return c;
    }
}
