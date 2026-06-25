package org.zonarstudio.spraute_engine.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Параметры игрока, общие для 1.19.2 и 1.20.1.
 */
public final class SprautePlayerCompat {
    private SprautePlayerCompat() {}

    public static float getStepHeight(Entity entity) {
        //? if >=1.20.1 {
        return entity.maxUpStep();
        //?} else {
        /*return entity.maxUpStep;
        *///?}
    }

    public static void setStepHeight(Entity entity, float blocks) {
        //? if >=1.20.1 {
        entity.setMaxUpStep(blocks);
        //?} else {
        /*entity.maxUpStep = blocks;
        *///?}
    }

    public static Double getAttributeValue(Player player, Attribute attribute) {
        if (player == null || attribute == null) return null;
        AttributeInstance inst = player.getAttribute(attribute);
        return inst == null ? null : inst.getValue();
    }

    public static boolean setAttributeBase(Player player, Attribute attribute, double value) {
        if (player == null || attribute == null) return false;
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return false;
        inst.setBaseValue(value);
        return true;
    }

    public static Attribute movementSpeedAttr() {
        return Attributes.MOVEMENT_SPEED;
    }

    public static Attribute jumpStrengthAttr() {
        return Attributes.JUMP_STRENGTH;
    }

    public static Attribute attackDamageAttr() {
        return Attributes.ATTACK_DAMAGE;
    }
}
