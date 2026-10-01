package org.zonarstudio.spraute_engine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef;

import java.util.Locale;

/**
 * Custom armor from {@code create item} with {@code armor_type} / {@code armor_texture}.
 */
public class ScriptCustomArmorItem extends ArmorItem {
    private final String displayName;
    private final String armorTextureBase;
    private final boolean hideWornModel;
    private final String itemId;
    private final String skinOverlay;
    private final String skinHideLayer;
    private final EquipmentSlot wornSlot;

    //? if >=1.20.1 {
    public ScriptCustomArmorItem(ArmorMaterial material, Type type, Properties props,
                                 String displayName, String armorTextureBase, boolean hideWornModel,
                                 String itemId, String skinOverlay, String skinHideLayer) {
        super(material, type, props);
        this.displayName = displayName;
        this.armorTextureBase = armorTextureBase;
        this.hideWornModel = hideWornModel;
        this.itemId = itemId == null ? "" : itemId;
        this.skinOverlay = skinOverlay == null ? "" : skinOverlay;
        this.skinHideLayer = skinHideLayer == null ? "" : skinHideLayer;
        this.wornSlot = type.getSlot();
    }
    //?} else {
    /*public ScriptCustomArmorItem(ArmorMaterial material, EquipmentSlot slot, Properties props,
                                 String displayName, String armorTextureBase, boolean hideWornModel,
                                 String itemId, String skinOverlay, String skinHideLayer) {
        super(material, slot, props);
        this.displayName = displayName;
        this.armorTextureBase = armorTextureBase;
        this.hideWornModel = hideWornModel;
        this.itemId = itemId == null ? "" : itemId;
        this.skinOverlay = skinOverlay == null ? "" : skinOverlay;
        this.skinHideLayer = skinHideLayer == null ? "" : skinHideLayer;
        this.wornSlot = slot;
    }
    *///?}

    public static ScriptCustomArmorItem create(CustomItemDef def, Properties props, String displayName) {
        ArmorSlot slot = ArmorSlot.parse(def.armorType);
        if (slot == null) {
            throw new IllegalArgumentException("Unknown armor_type: " + def.armorType);
        }
        int durability = def.durability != null ? def.durability : slot.defaultDurability;
        int defense = def.armorDefense != null ? def.armorDefense : slot.defaultDefense;
        float toughness = def.armorToughness != null ? def.armorToughness : 0f;
        float knockback = def.armorKnockback != null ? def.armorKnockback : 0f;
        String texBase = normalizeArmorTextureBase(def);
        ArmorMaterial material = new ScriptArmorMaterial(def.id, durability, defense, toughness, knockback, slot);

        Properties armorProps = props.durability(durability);
        boolean hideWorn = resolveHideWornModel(def);
        String overlay = def.skinOverlay == null ? "" : def.skinOverlay.trim();
        String hideLayer = def.skinHideLayer == null ? "" : def.skinHideLayer.trim();
        //? if >=1.20.1 {
        return new ScriptCustomArmorItem(material, slot.toType(), armorProps, displayName, texBase, hideWorn,
                def.id, overlay, hideLayer);
        //?} else {
        /*return new ScriptCustomArmorItem(material, slot.toEquipmentSlot(), armorProps, displayName, texBase, hideWorn,
                def.id, overlay, hideLayer);
        *///?}
    }

    public static boolean resolveHideWornModel(CustomItemDef def) {
        if (def != null && def.hideArmor != null) return def.hideArmor;
        return def == null || def.armorTexture == null || def.armorTexture.isBlank();
    }

    public boolean hidesWornModel() {
        return hideWornModel;
    }

    public static boolean hidesWornModel(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ScriptCustomArmorItem armor
                && armor.hidesWornModel();
    }

    public String clothingOverlayId() {
        String id = itemId.isBlank() ? "item" : itemId;
        return "clothes_" + id;
    }

    public String skinOverlay() {
        return skinOverlay;
    }

    public boolean hasSkinOverlay() {
        return !skinOverlay.isBlank();
    }

    public int resolveHideMask() {
        String raw = skinHideLayer;
        if (raw.isBlank() || "auto".equalsIgnoreCase(raw)) {
            EquipmentSlot slot = wornSlot;
            raw = switch (slot) {
                case HEAD -> "hat";
                case CHEST -> "torso";
                case LEGS -> "pants";
                default -> "none";
            };
        }
        if ("none".equalsIgnoreCase(raw)) return 0;
        return org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.parseParts(raw);
    }

    /** Bundled fully-transparent layers when {@code armor_texture} is unset (clothing overlay). */
    public static final String EMPTY_ARMOR_TEXTURE = "textures/models/armor/empty";

    /** Base path without {@code _layer_N.png}, e.g. {@code textures/models/armor/crystal}. */
    public static String normalizeArmorTextureBase(CustomItemDef def) {
        String raw = def.armorTexture;
        if (raw == null || raw.isBlank()) {
            raw = EMPTY_ARMOR_TEXTURE;
        }
        raw = raw.replace('\\', '/').trim();
        if (raw.endsWith(".png")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        if (raw.endsWith("_layer_1") || raw.endsWith("_layer_2")) {
            raw = raw.substring(0, raw.length() - "_layer_1".length());
        }
        if (!raw.contains("/") && !raw.contains(":")) {
            raw = "textures/models/armor/" + raw;
        }
        return raw;
    }

    /** Full resource path for HumanoidArmorLayer / IClientItemExtensions. */
    public String armorTexturePath(boolean legs, String overlayType) {
        String layer = legs ? "_layer_2" : "_layer_1";
        String overlay = (overlayType != null && !overlayType.isEmpty()) ? "_" + overlayType : "";
        String path = armorTextureBase + layer + overlay + ".png";
        if (path.startsWith("textures/")) {
            return Spraute_engine.MODID + ":" + path;
        }
        if (path.contains(":")) {
            return path;
        }
        return Spraute_engine.MODID + ":textures/models/armor/" + path + ".png";
    }

    @Override
    public Component getName(ItemStack stack) {
        return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
    }

    //? if >=1.20.1 {
    @Override
    public Component getDescription() {
        return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
    }
    //?}

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        var result = super.use(level, player, hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ClothingArmorRuntime.apply(sp);
        }
        return result;
    }

    /** Fallback if a renderer still calls {@code Item#getArmorTexture}. */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return armorTexturePath(slot == EquipmentSlot.LEGS, type);
    }

    public enum ArmorSlot {
        HELMET(11 * 15, 2),
        CHESTPLATE(16 * 15, 6),
        LEGGINGS(15 * 15, 5),
        BOOTS(13 * 15, 2);

        final int defaultDurability;
        final int defaultDefense;

        ArmorSlot(int defaultDurability, int defaultDefense) {
            this.defaultDurability = defaultDurability;
            this.defaultDefense = defaultDefense;
        }

        static ArmorSlot parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "helmet", "head", "шлем" -> HELMET;
                case "chestplate", "chest", "body", "нагрудник", "нагруда" -> CHESTPLATE;
                case "leggings", "legs", "поножи", "штаны" -> LEGGINGS;
                case "boots", "feet", "ботинки" -> BOOTS;
                default -> null;
            };
        }

        //? if >=1.20.1 {
        ArmorItem.Type toType() {
            return switch (this) {
                case HELMET -> Type.HELMET;
                case CHESTPLATE -> Type.CHESTPLATE;
                case LEGGINGS -> Type.LEGGINGS;
                case BOOTS -> Type.BOOTS;
            };
        }
        //?} else {
        /*EquipmentSlot toEquipmentSlot() {
            return switch (this) {
                case HELMET -> EquipmentSlot.HEAD;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case LEGGINGS -> EquipmentSlot.LEGS;
                case BOOTS -> EquipmentSlot.FEET;
            };
        }
        *///?}
    }

    /** Per-piece material; defense/durability come from the item def. */
    public static final class ScriptArmorMaterial implements ArmorMaterial {
        private final String name;
        private final int durability;
        private final int defense;
        private final float toughness;
        private final float knockback;

        public ScriptArmorMaterial(String name, int durability, int defense, float toughness, float knockback, ArmorSlot slot) {
            this.name = name;
            this.durability = Math.max(1, durability);
            this.defense = Math.max(0, defense);
            this.toughness = Math.max(0f, toughness);
            this.knockback = Math.max(0f, knockback);
        }

        //? if >=1.20.1 {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return durability;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return defense;
        }
        //?} else {
        /*@Override
        public int getDurabilityForSlot(EquipmentSlot slot) {
            return durability;
        }

        @Override
        public int getDefenseForSlot(EquipmentSlot slot) {
            return defense;
        }
        *///?}

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            // Fallback path if client extension is missing: assets/<mod>/textures/models/armor/<name>_layer_N.png
            return Spraute_engine.MODID + ":" + name;
        }

        @Override
        public float getToughness() {
            return toughness;
        }

        @Override
        public float getKnockbackResistance() {
            return knockback;
        }
    }
}
