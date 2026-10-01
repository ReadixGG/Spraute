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

    /** Block break/place reach. Forge 1.20.1 splits it from entity reach. */
    public static Attribute blockReachAttr() {
        //? if >=1.20.1 {
        return net.minecraftforge.common.ForgeMod.BLOCK_REACH.get();
        //?} else {
        /*return net.minecraftforge.common.ForgeMod.REACH_DISTANCE.get();
        *///?}
    }

    public static Double getBlockReach(Player player) {
        if (player == null) return null;
        AttributeInstance inst = player.getAttribute(blockReachAttr());
        return inst == null ? null : inst.getBaseValue();
    }

    public static boolean setBlockReach(Player player, double blocks) {
        double clamped = Math.max(0d, Math.min(1024d, blocks));
        return setAttributeBase(player, blockReachAttr(), clamped);
    }
}
