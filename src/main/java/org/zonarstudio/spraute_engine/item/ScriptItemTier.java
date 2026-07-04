package org.zonarstudio.spraute_engine.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Configurable material tier for script-declared tools. */
public final class ScriptItemTier implements Tier {
    private final int uses;
    private final float speed;
    private final int level;

    public ScriptItemTier(int uses, float speed, int level) {
        this.uses = uses;
        this.speed = speed;
        this.level = level;
    }

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return 0f;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }
}
