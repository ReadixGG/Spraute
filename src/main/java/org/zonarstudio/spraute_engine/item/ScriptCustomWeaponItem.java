package org.zonarstudio.spraute_engine.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Plain script item with custom attack attributes. */
public class ScriptCustomWeaponItem extends ScriptCustomItem {
    private static final UUID ATTACK_DAMAGE_ID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA9");

    private final float damage;
    private final float attackSpeed;

    public ScriptCustomWeaponItem(Properties properties, String displayName, float damage, float attackSpeed) {
        this(properties, displayName, damage, attackSpeed, false);
    }

    public ScriptCustomWeaponItem(Properties properties, String displayName, float damage, float attackSpeed, boolean geoVisual) {
        super(properties, displayName, geoVisual);
        this.damage = damage;
        this.attackSpeed = attackSpeed;
    }

    //? if >=1.20.1 {
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create(super.getDefaultAttributeModifiers(slot));
        applyWeaponModifiers(slot, map);
        return map;
    }
    //?} else {
    /*@Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create(super.getAttributeModifiers(slot, stack));
        applyWeaponModifiers(slot, map);
        return map;
    }*/
    //?}

    private void applyWeaponModifiers(EquipmentSlot slot, Multimap<Attribute, AttributeModifier> map) {
        if (slot == EquipmentSlot.MAINHAND) {
            if (damage != 0f) {
                map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        ATTACK_DAMAGE_ID, "spraute_weapon_damage", damage, AttributeModifier.Operation.ADDITION));
            }
            if (attackSpeed != 0f) {
                map.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                        ATTACK_SPEED_ID, "spraute_weapon_speed", attackSpeed, AttributeModifier.Operation.ADDITION));
            }
        }
    }
}
