package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemFunctions {

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    public static class GiveItem implements ScriptFunction {
        @Override
        public String getName() { return "giveItem"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            Player player = resolvePlayer(args.get(0), source);
            if (!(player instanceof ServerPlayer sp)) return null;

            String itemId = String.valueOf(args.get(1));
            ResourceLocation rl = itemId.contains(":") ? new ResourceLocation(itemId) : new ResourceLocation("minecraft", itemId);
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(rl);
            
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                Block block = ForgeRegistries.BLOCKS.getValue(rl);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    item = block.asItem();
                }
            }
            if (item == null || item == net.minecraft.world.item.Items.AIR) return null;

            int count = 1;
            if (args.size() > 2 && args.get(2) instanceof Number n) {
                count = n.intValue();
            }

            ItemStack stack = new ItemStack(item, count);

            if (args.size() > 3 && args.get(3) instanceof String name && !name.isEmpty()) {
                stack.setHoverName(Component.literal(name.replace("&", "§")));
            }

            if (args.size() > 4 && args.get(4) instanceof List<?> loreList) {
                CompoundTag display = stack.getOrCreateTagElement("display");
                ListTag lore = new ListTag();
                for (Object o : loreList) {
                    lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(String.valueOf(o).replace("&", "§")))));
                }
                display.put("Lore", lore);
            }

            if (args.size() > 5 && args.get(5) instanceof Map<?, ?> nbtMap) {
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

            sp.getInventory().add(stack);
            sp.containerMenu.broadcastChanges();
            return null;
        }
    }

    public static class GetHeldItem implements ScriptFunction {
        @Override
        public String getName() { return "getHeldItem"; }

        @Override
        public int getArgCount() { return 1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return null;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return null;

            Map<String, Object> dict = new HashMap<>();
            String regName = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            dict.put("id", regName);
            dict.put("count", stack.getCount());
            dict.put("name", stack.getHoverName().getString());
            
            if (stack.hasTag()) {
                Map<String, Object> nbtMap = new HashMap<>();
                for (String key : stack.getTag().getAllKeys()) {
                    nbtMap.put(key, stack.getTag().get(key).getAsString());
                }
                dict.put("nbt", nbtMap);
            }

            return dict;
        }
    }
}
