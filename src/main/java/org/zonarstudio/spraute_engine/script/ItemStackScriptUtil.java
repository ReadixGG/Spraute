package org.zonarstudio.spraute_engine.script;

import com.google.common.collect.Multimap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared helpers for scripted player inventory / item stack access. */
public final class ItemStackScriptUtil {
    public static final UUID SPRAUTE_ATTACK_DAMAGE_UUID = UUID.fromString("c4e8f1a2-3b5d-4e6f-9a0b-1c2d3e4f5a6b");

    private ItemStackScriptUtil() {}

    public static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null && source.getLevel().getServer() != null) {
            String trimmed = name.trim();
            if (isAnyPlayerKeyword(trimmed)) {
                if (source.getEntity() instanceof Player p) return p;
                var players = source.getLevel().getServer().getPlayerList().getPlayers();
                return players.isEmpty() ? null : players.get(0);
            }
            return source.getLevel().getServer().getPlayerList().getPlayerByName(trimmed);
        }
        return null;
    }

    static boolean isAnyPlayerKeyword(String value) {
        return "any".equalsIgnoreCase(value)
                || "all".equalsIgnoreCase(value)
                || "*".equals(value)
                || "любой".equalsIgnoreCase(value);
    }

    /** Slots 0–40, or hand aliases: main/right/hand, offhand/left. */
    public static int resolveSlot(Player player, Object slotArg) {
        if (slotArg instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(slotArg).toLowerCase();
        return switch (s) {
            case "main", "main_hand", "right", "hand" -> player.getInventory().selected;
            case "offhand", "left" -> 40;
            default -> -1;
        };
    }

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot <= 40;
    }

    public static ItemStack getStack(Player player, int slot) {
        if (!isValidSlot(slot)) return ItemStack.EMPTY;
        return player.getInventory().getItem(slot);
    }

    public static void setStack(ServerPlayer player, int slot, ItemStack stack) {
        if (!isValidSlot(slot)) return;
        player.getInventory().setItem(slot, stack);
        syncInventory(player);
    }

    public static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static Item resolveItem(String itemId) {
        ResourceLocation rl = itemId.contains(":") ? new ResourceLocation(itemId) : new ResourceLocation("minecraft", itemId);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != Blocks.AIR) {
                item = block.asItem();
            }
        }
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        return item;
    }

    public static ItemStack createStack(String itemId, int count, String name, List<?> lore, Map<?, ?> nbtMap) {
        Item item = resolveItem(itemId);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (name != null && !name.isEmpty()) {
            setCustomName(stack, name);
        }
        if (lore != null && !lore.isEmpty()) {
            setLore(stack, lore);
        }
        if (nbtMap != null && !nbtMap.isEmpty()) {
            applyNbtKeys(stack, nbtMap);
        }
        return stack;
    }

    public static Map<String, Object> stackToMap(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) return null;
        Map<String, Object> dict = new HashMap<>();
        dict.put("slot", slot);
        dict.put("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        dict.put("count", stack.getCount());
        dict.put("name", stack.getHoverName().getString());
        String customName = getCustomName(stack);
        if (customName != null) dict.put("custom_name", customName);
        List<String> lore = getLore(stack);
        if (!lore.isEmpty()) dict.put("lore", lore);
        dict.put("attack_damage", getAttackDamageBonus(stack));
        if (stack.isDamageableItem()) {
            dict.put("durability", stack.getDamageValue());
            dict.put("max_durability", stack.getMaxDamage());
        }
        if (stack.hasTag()) {
            dict.put("nbt", nbtToMap(stack.getTag()));
        }
        return dict;
    }

    public static String getCustomName(ItemStack stack) {
        CompoundTag display = stack.getTagElement("display");
        if (display != null && display.contains("Name", Tag.TAG_STRING)) {
            return Component.Serializer.fromJson(display.getString("Name")).getString();
        }
        return null;
    }

    public static void setCustomName(ItemStack stack, String name) {
        stack.setHoverName(Component.literal(name.replace("&", "§")));
    }

    public static List<String> getLore(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        CompoundTag display = stack.getTagElement("display");
        if (display == null || !display.contains("Lore", Tag.TAG_LIST)) return lines;
        ListTag loreTag = display.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; i < loreTag.size(); i++) {
            lines.add(Component.Serializer.fromJson(loreTag.getString(i)).getString());
        }
        return lines;
    }

    public static void setLore(ItemStack stack, List<?> loreList) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        for (Object o : loreList) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(String.valueOf(o).replace("&", "§")))));
        }
        display.put("Lore", lore);
    }

    /** Appends lore lines; skips exact duplicates already on the stack. */
    public static void appendLore(ItemStack stack, List<?> loreList) {
        if (loreList == null || loreList.isEmpty()) return;
        List<String> existing = getLore(stack);
        boolean changed = false;
        for (Object o : loreList) {
            String line = String.valueOf(o).replace("&", "§");
            if (!existing.contains(line)) {
                existing.add(line);
                changed = true;
            }
        }
        if (changed) {
            setLore(stack, existing);
        }
    }

    public static double getAttackDamageBonus(ItemStack stack) {
        double total = 0;
        Multimap<Attribute, AttributeModifier> map = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (AttributeModifier mod : map.get(Attributes.ATTACK_DAMAGE)) {
            if (mod.getOperation() == AttributeModifier.Operation.ADDITION) {
                total += mod.getAmount();
            }
        }
        return total;
    }

    public static void setAttackDamageBonus(ItemStack stack, double amount) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag modifiers = tag.contains("AttributeModifiers", Tag.TAG_LIST)
                ? tag.getList("AttributeModifiers", Tag.TAG_COMPOUND)
                : new ListTag();
        ListTag newList = new ListTag();
        for (int i = 0; i < modifiers.size(); i++) {
            CompoundTag m = modifiers.getCompound(i);
            if (m.hasUUID("UUID") && m.getUUID("UUID").equals(SPRAUTE_ATTACK_DAMAGE_UUID)) {
                continue;
            }
            newList.add(m);
        }
        if (amount != 0) {
            CompoundTag mod = new CompoundTag();
            mod.putString("AttributeName", attackDamageAttributeId());
            mod.putString("Name", "spraute_attack");
            mod.putDouble("Amount", amount);
            mod.putInt("Operation", 0);
            mod.putUUID("UUID", SPRAUTE_ATTACK_DAMAGE_UUID);
            mod.putString("Slot", "mainhand");
            newList.add(mod);
        }
        if (newList.isEmpty()) {
            tag.remove("AttributeModifiers");
        } else {
            tag.put("AttributeModifiers", newList);
        }
    }

    private static String attackDamageAttributeId() {
        //? if >=1.20.1 {
        return "minecraft:generic.attack_damage";
        //?} else {
        /*return "generic.attack_damage";
        *///?}
    }

    public static Map<String, Object> nbtToMap(CompoundTag tag) {
        Map<String, Object> out = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value instanceof net.minecraft.nbt.NumericTag num) {
                out.put(key, num.getAsDouble());
            } else if (value instanceof net.minecraft.nbt.StringTag str) {
                out.put(key, str.getAsString());
            } else if (value instanceof net.minecraft.nbt.ByteTag b) {
                out.put(key, b.getAsByte() != 0);
            } else {
                out.put(key, value.toString());
            }
        }
        return out;
    }

    public static void applyNbtKeys(ItemStack stack, Map<?, ?> nbtMap) {
        CompoundTag tag = stack.getOrCreateTag();
        for (Map.Entry<?, ?> entry : nbtMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object val = entry.getValue();
            if (val instanceof Boolean b) {
                tag.putBoolean(key, b);
            } else if (val instanceof Number n) {
                if (val instanceof Double || val instanceof Float) {
                    tag.putDouble(key, n.doubleValue());
                } else {
                    tag.putInt(key, n.intValue());
                }
            } else if (val instanceof String s) {
                tag.putString(key, s);
            }
        }
    }

    public static int findFirstSlot(Player player, String itemId) {
        ResourceLocation searchRL = new ResourceLocation(itemId);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(searchRL)) {
                return i;
            }
        }
        return -1;
    }

    public static int removeItems(Player player, String itemId, int count) {
        if (count <= 0) return 0;
        ResourceLocation searchRL = new ResourceLocation(itemId);
        int removed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && removed < count; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !ForgeRegistries.ITEMS.getKey(stack.getItem()).equals(searchRL)) continue;
            int take = Math.min(stack.getCount(), count - removed);
            stack.shrink(take);
            removed += take;
            if (stack.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (removed > 0 && player instanceof ServerPlayer sp) {
            syncInventory(sp);
        }
        return removed;
    }
}
