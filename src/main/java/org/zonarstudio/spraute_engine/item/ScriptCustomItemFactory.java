package org.zonarstudio.spraute_engine.item;

import net.minecraft.world.item.*;
import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef;

import java.util.Locale;

public final class ScriptCustomItemFactory {
    private ScriptCustomItemFactory() {}

    public enum ToolType {
        NONE,
        SWORD,
        PICKAXE,
        AXE,
        SHOVEL,
        HOE;

        static ToolType fromDef(CustomItemDef def) {
            if (def.toolType != null && !def.toolType.isBlank()) {
                return parse(def.toolType);
            }
            if (def.sword) return SWORD;
            if (def.pickaxe) return PICKAXE;
            if (def.axe) return AXE;
            if (def.shovel) return SHOVEL;
            if (def.hoe) return HOE;
            return NONE;
        }

        static ToolType parse(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "sword", "меч" -> SWORD;
                case "pickaxe", "pick", "кирка" -> PICKAXE;
                case "axe", "топор" -> AXE;
                case "shovel", "лопата" -> SHOVEL;
                case "hoe", "мотыга" -> HOE;
                default -> NONE;
            };
        }
    }

    public static Item create(CustomItemDef def, Item.Properties props, String displayName) {
        ToolType type = ToolType.fromDef(def);
        if (type != ToolType.NONE) {
            return createTool(type, def, applyToolDurability(def, props), displayName);
        }
        Item.Properties finalProps = props;
        if (def.durability != null && def.durability > 0) {
            finalProps = finalProps.durability(def.durability);
        }
        if (def.damage != null) {
            float speed = def.attackSpeed != null ? def.attackSpeed : 0f;
            return new ScriptCustomWeaponItem(finalProps, displayName, def.damage, speed);
        }
        return new ScriptCustomItem(finalProps, displayName);
    }

    private static Item.Properties applyToolDurability(CustomItemDef def, Item.Properties props) {
        int durability = def.durability != null ? def.durability : 250;
        return props.durability(durability);
    }

    private static ScriptItemTier tierFor(CustomItemDef def) {
        float speed = def.miningSpeed != null ? def.miningSpeed : 6f;
        int level = def.miningLevel != null ? def.miningLevel : 2;
        int durability = def.durability != null ? def.durability : 250;
        return new ScriptItemTier(durability, speed, level);
    }

    private static Item createTool(ToolType type, CustomItemDef def, Item.Properties props, String displayName) {
        ScriptItemTier tier = tierFor(def);
        float attackSpeed = def.attackSpeed != null ? def.attackSpeed : defaultAttackSpeed(type);
        float damage = def.damage != null ? def.damage : defaultDamage(type);
        return switch (type) {
            case SWORD -> new ScriptCustomSwordItem(tier, Math.round(damage), attackSpeed, props, displayName);
            case PICKAXE -> new ScriptCustomPickaxeItem(tier, Math.round(damage), attackSpeed, props, displayName);
            case AXE -> new ScriptCustomAxeItem(tier, damage, attackSpeed, props, displayName);
            case SHOVEL -> new ScriptCustomShovelItem(tier, damage, attackSpeed, props, displayName);
            case HOE -> new ScriptCustomHoeItem(tier, Math.round(damage), attackSpeed, props, displayName);
            case NONE -> new ScriptCustomItem(props, displayName);
        };
    }

    private static float defaultDamage(ToolType type) {
        return switch (type) {
            case SWORD -> 5f;
            case PICKAXE -> 4f;
            case AXE -> 6f;
            case SHOVEL -> 2.5f;
            case HOE -> 0f;
            case NONE -> 0f;
        };
    }

    private static float defaultAttackSpeed(ToolType type) {
        return switch (type) {
            case SWORD -> -2.4f;
            case PICKAXE -> -2.8f;
            case AXE -> -3.0f;
            case SHOVEL -> -3.0f;
            case HOE -> -1.0f;
            case NONE -> 0f;
        };
    }

    public static final class ScriptCustomSwordItem extends SwordItem {
        private final String displayName;

        public ScriptCustomSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties props, String displayName) {
            super(tier, attackDamage, attackSpeed, props);
            this.displayName = displayName;
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
        }

        //? if >=1.20.1 {
        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
        }
        //?}

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            return ScriptItemNames.scriptUse(level, player, hand);
        }
    }

    public static final class ScriptCustomPickaxeItem extends PickaxeItem {
        private final String displayName;

        public ScriptCustomPickaxeItem(Tier tier, int attackDamage, float attackSpeed, Properties props, String displayName) {
            super(tier, attackDamage, attackSpeed, props);
            this.displayName = displayName;
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
        }

        //? if >=1.20.1 {
        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
        }
        //?}

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            return ScriptItemNames.scriptUse(level, player, hand);
        }
    }

    public static final class ScriptCustomAxeItem extends AxeItem {
        private final String displayName;

        public ScriptCustomAxeItem(Tier tier, float attackDamage, float attackSpeed, Properties props, String displayName) {
            super(tier, attackDamage, attackSpeed, props);
            this.displayName = displayName;
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
        }

        //? if >=1.20.1 {
        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
        }
        //?}

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            return ScriptItemNames.scriptUse(level, player, hand);
        }
    }

    public static final class ScriptCustomShovelItem extends ShovelItem {
        private final String displayName;

        public ScriptCustomShovelItem(Tier tier, float attackDamage, float attackSpeed, Properties props, String displayName) {
            super(tier, attackDamage, attackSpeed, props);
            this.displayName = displayName;
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
        }

        //? if >=1.20.1 {
        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
        }
        //?}

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            return ScriptItemNames.scriptUse(level, player, hand);
        }
    }

    public static final class ScriptCustomHoeItem extends HoeItem {
        private final String displayName;

        public ScriptCustomHoeItem(Tier tier, int attackDamage, float attackSpeed, Properties props, String displayName) {
            super(tier, attackDamage, attackSpeed, props);
            this.displayName = displayName;
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return ScriptItemNames.resolveName(displayName, stack, () -> super.getName(stack));
        }

        //? if >=1.20.1 {
        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return ScriptItemNames.resolveName(displayName, ItemStack.EMPTY, () -> super.getDescription());
        }
        //?}

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            return ScriptItemNames.scriptUse(level, player, hand);
        }
    }
}
