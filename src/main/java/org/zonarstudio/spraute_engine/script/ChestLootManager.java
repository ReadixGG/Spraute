package org.zonarstudio.spraute_engine.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.zonarstudio.spraute_engine.registry.CustomGeoBlockEntity;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-position chest loot templates (world save) and application on open.
 */
public final class ChestLootManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type SLOT_MAP_TYPE = new TypeToken<Map<String, SlotEntry>>() {}.getType();

    private ChestLootManager() {}

    public static class SlotEntry {
        public String item;
        public int count = 1;

        public SlotEntry() {}

        public SlotEntry(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SlotEntry> getLootMap(ScriptWorldData data, String key, net.minecraft.server.level.ServerLevel level) {
        Object raw = data.get(key, level.getServer(), level);
        if (raw instanceof Map<?, ?> map) {
            Map<String, SlotEntry> out = new HashMap<>();
            for (var e : map.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Map<?, ?> vm) {
                    Object item = vm.get("item");
                    Object count = vm.get("count");
                    if (item != null) {
                        int c = 1;
                        if (count instanceof Number n) c = n.intValue();
                        out.put(String.valueOf(e.getKey()), new SlotEntry(String.valueOf(item), c));
                    }
                } else if (v instanceof SlotEntry se) {
                    out.put(String.valueOf(e.getKey()), se);
                }
            }
            return out;
        }
        if (raw instanceof String s && s.startsWith("j:")) {
            try {
                return GSON.fromJson(s.substring(2), SLOT_MAP_TYPE);
            } catch (Exception ignored) {}
        }
        return new HashMap<>();
    }

    public static void setSlot(ServerLevel level, String dimensionId, BlockPos pos, int slot, String itemId, int count) {
        ScriptWorldData data = ScriptWorldData.get(level);
        String key = BlockInteractionUtil.lootKey(dimensionId, pos);
        Map<String, SlotEntry> map = getLootMap(data, key, level);
        map.put(String.valueOf(slot), new SlotEntry(itemId, Math.max(1, count)));
        data.put(key, map);
        data.remove(BlockInteractionUtil.lootAppliedKey(dimensionId, pos));
    }

    public static void clear(ServerLevel level, String dimensionId, BlockPos pos) {
        ScriptWorldData data = ScriptWorldData.get(level);
        data.remove(BlockInteractionUtil.lootKey(dimensionId, pos));
        data.remove(BlockInteractionUtil.lootAppliedKey(dimensionId, pos));
    }

    public static void applyOnOpen(ServerLevel level, BlockPos clickedPos, BlockState state) {
        String dimId = level.dimension().location().toString();
        BlockPos containerPos = BlockInteractionUtil.resolveContainerPos(level, clickedPos, state);
        ScriptWorldData data = ScriptWorldData.get(level);
        String appliedKey = BlockInteractionUtil.lootAppliedKey(dimId, containerPos);
        if (Boolean.TRUE.equals(data.get(appliedKey, level.getServer(), level))) {
            return;
        }
        String lootKey = BlockInteractionUtil.lootKey(dimId, containerPos);
        Map<String, SlotEntry> map = getLootMap(data, lootKey, level);
        if (map.isEmpty()) return;

        BlockEntity be = level.getBlockEntity(containerPos);
        if (be instanceof RandomizableContainerBlockEntity container) {
            for (var e : map.entrySet()) {
                try {
                    int slot = Integer.parseInt(e.getKey());
                    SlotEntry entry = e.getValue();
                    if (entry == null || entry.item == null) continue;
                    Item item = resolveItem(entry.item);
                    if (item == null) continue;
                    if (slot >= 0 && slot < container.getContainerSize()) {
                        container.setItem(slot, new ItemStack(item, entry.count));
                    }
                } catch (NumberFormatException ignored) {}
            }
            container.setChanged();
            data.put(appliedKey, true);
        }
    }

    public static void setBlockSlot(ServerLevel level, BlockPos pos, int slot, String itemId, int count) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CustomGeoBlockEntity cgbe)) return;
        if (slot < 0 || slot >= cgbe.inventory.getContainerSize()) return;
        Item item = resolveItem(itemId);
        if (item == null) return;
        cgbe.inventory.setItem(slot, new ItemStack(item, Math.max(1, count)));
        cgbe.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
    }

    private static Item resolveItem(String itemId) {
        String full = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(full));
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }
}
