package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

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
            int count = 1;
            if (args.size() > 2 && args.get(2) instanceof Number n) {
                count = n.intValue();
            }

            String name = null;
            if (args.size() > 3 && args.get(3) instanceof String n && !n.isEmpty()) name = n;

            List<?> lore = null;
            if (args.size() > 4 && args.get(4) instanceof List<?> loreList) lore = loreList;

            Map<?, ?> nbtMap = null;
            String extraSnbt = null;
            if (args.size() > 5) {
                if (args.get(5) instanceof Map<?, ?> m) nbtMap = m;
                else if (args.get(5) instanceof String s) extraSnbt = s;
            }

            ItemStack stack = ItemStackScriptUtil.createStack(itemId, count, name, lore, nbtMap);
            if (stack.isEmpty()) return null;
            if (extraSnbt != null) ItemStackScriptUtil.applySnbt(stack, extraSnbt);

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

            return org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.stackToMap(
                    stack, player.getInventory().selected);
        }
    }

    /** {@code itemDisplayName("minecraft:diamond")} → localized item name for chat/UI. */
    public static class GetItemDisplayName implements ScriptFunction {
        @Override public String getName() { return "itemDisplayName"; }

        @Override public int getArgCount() { return 1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            return org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.displayNameForItemId(String.valueOf(args.get(0)));
        }
    }
}
