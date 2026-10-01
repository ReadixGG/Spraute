package org.zonarstudio.spraute_engine.script;

import com.google.common.collect.Multimap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
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

    /**
     * Item id plus optional SNBT, same shape as {@code /give}:
     * {@code minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s}]}}.
     */
    public record ItemSpec(String id, String nbt) {
        public boolean hasNbt() {
            return nbt != null && !nbt.isBlank();
        }
    }

    public static ItemSpec parseSpec(String raw) {
        if (raw == null) return new ItemSpec("", null);
        String s = raw.trim();
        if (s.isEmpty()) return new ItemSpec("", null);
        int brace = s.indexOf('{');
        if (brace < 0) return new ItemSpec(s, null);
        String id = s.substring(0, brace).trim();
        return new ItemSpec(id, extractBalanced(s, brace));
    }

    public static String formatSpec(String id, String nbt) {
        String cleanId = id == null ? "" : id.trim();
        String wrapped = wrapNbt(nbt);
        return wrapped == null ? cleanId : cleanId + wrapped;
    }

    public static String wrapNbt(String nbt) {
        if (nbt == null) return null;
        String t = nbt.trim();
        if (t.isEmpty()) return null;
        if (!t.startsWith("{")) t = "{" + t;
        if (!t.endsWith("}")) t = t + "}";
        return t;
    }

    public static String unwrapNbt(String nbt) {
        String wrapped = wrapNbt(nbt);
        if (wrapped == null) return "";
        return wrapped.substring(1, wrapped.length() - 1);
    }

    private static String extractBalanced(String s, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return s.substring(start);
    }

    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String id = parseSpec(itemId).id();
        if (id.isBlank()) return null;
        ResourceLocation rl;
        try {
            rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);
        } catch (Exception e) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != Blocks.AIR) {
                item = block.asItem();
            }
        }
        if ((item == null || item == net.minecraft.world.item.Items.AIR) && !id.contains(":")) {
            try {
                ResourceLocation custom = new ResourceLocation("spraute_engine", id);
                item = ForgeRegistries.ITEMS.getValue(custom);
            } catch (Exception ignored) {
                item = null;
            }
        }
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        return item;
    }

    /** Localized hover/display name for an item id (vanilla or mod). Falls back to {@code itemId}. */
    public static String displayNameForItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        Item item = resolveItem(itemId.trim());
        if (item == null) return itemId;
        return new ItemStack(item).getHoverName().getString();
    }

    public static ItemStack createStack(String itemId, int count) {
        return createStack(itemId, count, null, null, null);
    }

    public static ItemStack createStack(String itemId, int count, String extraNbt) {
        ItemStack stack = createStack(itemId, count, null, null, null);
        applySnbt(stack, extraNbt);
        return stack;
    }

    public static ItemStack createStack(String itemId, int count, String name, List<?> lore, Map<?, ?> nbtMap) {
        Item item = resolveItem(itemId);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        applySnbt(stack, parseSpec(itemId).nbt());
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

    public static void applySnbt(ItemStack stack, String nbt) {
        String wrapped = wrapNbt(nbt);
        if (wrapped == null || stack == null || stack.isEmpty()) return;
        try {
            stack.getOrCreateTag().merge(TagParser.parseTag(wrapped));
        } catch (Exception ignored) {
        }
    }

    /** Id match; if the spec (or extra SNBT) has NBT, the stack must contain it as a subset. */
    public static boolean matches(ItemStack stack, String spec) {
        return matches(stack, spec, null);
    }

    public static boolean matches(ItemStack stack, String spec, String extraNbt) {
        if (stack == null || stack.isEmpty() || spec == null || spec.isBlank()) return false;
        Item item = resolveItem(spec);
        if (item == null || stack.getItem() != item) return false;
        ItemSpec parsed = parseSpec(spec);
        if (parsed.hasNbt() && !nbtMatches(stack, parsed.nbt())) return false;
        return extraNbt == null || extraNbt.isBlank() || nbtMatches(stack, extraNbt);
    }

    public static boolean nbtMatches(ItemStack stack, String snbt) {
        String wrapped = wrapNbt(snbt);
        if (wrapped == null) return true;
        if (stack == null || !stack.hasTag()) return false;
        try {
            return tagContains(stack.getTag(), TagParser.parseTag(wrapped));
        } catch (Exception e) {
            return false;
        }
    }

    /** Every key/element of {@code needle} must appear in {@code hay}. */
    static boolean tagContains(Tag hay, Tag needle) {
        if (needle == null) return true;
        if (hay == null) return false;
        if (needle instanceof CompoundTag n && hay instanceof CompoundTag h) {
            for (String key : n.getAllKeys()) {
                if (!h.contains(key) || !tagContains(h.get(key), n.get(key))) return false;
            }
            return true;
        }
        if (needle instanceof ListTag n && hay instanceof ListTag h) {
            for (int i = 0; i < n.size(); i++) {
                Tag need = n.get(i);
                boolean found = false;
                for (int j = 0; j < h.size(); j++) {
                    if (tagContains(h.get(j), need)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
        return hay.equals(needle);
    }

    public static int countMatching(Player player, String spec) {
        return countMatching(player, spec, null);
    }

    public static int countMatching(Player player, String spec, String extraNbt) {
        if (player == null) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack, spec, extraNbt)) total += stack.getCount();
        }
        return total;
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
        if (player == null) return -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (matches(player.getInventory().getItem(i), itemId)) return i;
        }
        return -1;
    }

    public static int removeItems(Player player, String itemId, int count) {
        if (count <= 0 || player == null) return 0;
        int removed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && removed < count; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!matches(stack, itemId)) continue;
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
