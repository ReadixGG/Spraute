package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Чтение и изменение предметов в инвентаре игрока.
 * Слоты: 0–8 хотбар, 9–35 инвентарь, 36–39 броня, 40 вторая рука.
 * Алиасы слота: {@code main}/{@code right}, {@code offhand}/{@code left}.
 */
public class PlayerInventoryFunctions {

    private static ServerPlayer requireServerPlayer(Object target, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(target, source);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    private static ItemStack requireStack(ServerPlayer player, Object slotArg) {
        int slot = ItemStackScriptUtil.resolveSlot(player, slotArg);
        if (!ItemStackScriptUtil.isValidSlot(slot)) return null;
        ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
        return stack.isEmpty() ? null : stack;
    }

    private static int requireSlot(ServerPlayer player, Object slotArg) {
        int slot = ItemStackScriptUtil.resolveSlot(player, slotArg);
        return ItemStackScriptUtil.isValidSlot(slot) ? slot : -1;
    }

    public static class GetItemInSlot implements ScriptFunction {
        @Override public String getName() { return "getItemInSlot"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return null;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return null;
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            return stack.isEmpty() ? null : ItemStackScriptUtil.stackToMap(stack, slot);
        }
    }

    public static class GetPlayerInventory implements ScriptFunction {
        @Override public String getName() { return "getPlayerInventory"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return new ArrayList<>();
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return new ArrayList<>();
            List<Object> items = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    items.add(ItemStackScriptUtil.stackToMap(stack, slot));
                }
            }
            return items;
        }
    }

    public static class IsSlotEmpty implements ScriptFunction {
        @Override public String getName() { return "isSlotEmpty"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return true;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return true;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return true;
            return ItemStackScriptUtil.getStack(player, slot).isEmpty();
        }
    }

    public static class HasItemInSlot implements ScriptFunction {
        @Override public String getName() { return "hasItemInSlot"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            if (stack.isEmpty()) return false;
            return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(String.valueOf(args.get(2)));
        }
    }

    public static class FindItemSlot implements ScriptFunction {
        @Override public String getName() { return "findItemSlot"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return -1;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return -1;
            return ItemStackScriptUtil.findFirstSlot(player, String.valueOf(args.get(1)));
        }
    }

    public static class SetItemInSlot implements ScriptFunction {
        @Override public String getName() { return "setItemInSlot"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;

            String itemId = String.valueOf(args.get(2));
            int count = 1;
            if (args.size() > 3 && args.get(3) instanceof Number n) count = n.intValue();

            String name = null;
            if (args.size() > 4 && args.get(4) instanceof String s && !s.isEmpty()) name = s;

            List<?> lore = null;
            if (args.size() > 5 && args.get(5) instanceof List<?> l) lore = l;

            Map<?, ?> nbt = null;
            if (args.size() > 6 && args.get(6) instanceof Map<?, ?> m) nbt = m;

            ItemStack stack = ItemStackScriptUtil.createStack(itemId, count, name, lore, nbt);
            if (stack.isEmpty()) return false;
            ItemStackScriptUtil.setStack(player, slot, stack);
            return true;
        }
    }

    public static class SetItemCount implements ScriptFunction {
        @Override public String getName() { return "setItemCount"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            if (stack.isEmpty()) return false;
            int count = ((Number) args.get(2)).intValue();
            if (count <= 0) {
                ItemStackScriptUtil.setStack(player, slot, ItemStack.EMPTY);
            } else {
                stack.setCount(count);
                ItemStackScriptUtil.syncInventory(player);
            }
            return true;
        }
    }

    public static class ClearItemSlot implements ScriptFunction {
        @Override public String getName() { return "clearItemSlot"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;
            ItemStackScriptUtil.setStack(player, slot, ItemStack.EMPTY);
            return true;
        }
    }

    public static class RemoveItem implements ScriptFunction {
        @Override public String getName() { return "removeItem"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return 0;
            String itemId = String.valueOf(args.get(1));
            int count = Integer.MAX_VALUE;
            if (args.size() > 2 && args.get(2) instanceof Number n) count = n.intValue();
            return ItemStackScriptUtil.removeItems(player, itemId, count);
        }
    }

    public static class GetItemName implements ScriptFunction {
        @Override public String getName() { return "getItemName"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return "";
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return "";
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return "";
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            return stack.isEmpty() ? "" : stack.getHoverName().getString();
        }
    }

    public static class SetItemName implements ScriptFunction {
        @Override public String getName() { return "setItemName"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            if (stack.isEmpty()) return false;
            ItemStackScriptUtil.setCustomName(stack, String.valueOf(args.get(2)));
            ItemStackScriptUtil.syncInventory(player);
            return true;
        }
    }

    public static class GetItemLore implements ScriptFunction {
        @Override public String getName() { return "getItemLore"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return new ArrayList<>();
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return new ArrayList<>();
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return new ArrayList<>();
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            if (stack.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(ItemStackScriptUtil.getLore(stack));
        }
    }

    public static class SetItemLore implements ScriptFunction {
        @Override public String getName() { return "setItemLore"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, List.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            int slot = requireSlot(player, args.get(1));
            if (slot < 0) return false;
            ItemStack stack = ItemStackScriptUtil.getStack(player, slot);
            if (stack.isEmpty()) return false;
            if (!(args.get(2) instanceof List<?> lore)) return false;
            ItemStackScriptUtil.setLore(stack, lore);
            ItemStackScriptUtil.syncInventory(player);
            return true;
        }
    }

    public static class GetItemAttackDamage implements ScriptFunction {
        @Override public String getName() { return "getItemAttackDamage"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0.0;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return 0.0;
            ItemStack stack = requireStack(player, args.get(1));
            return stack == null ? 0.0 : ItemStackScriptUtil.getAttackDamageBonus(stack);
        }
    }

    public static class SetItemAttackDamage implements ScriptFunction {
        @Override public String getName() { return "setItemAttackDamage"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            ItemStack stack = requireStack(player, args.get(1));
            if (stack == null) return false;
            if (!(args.get(2) instanceof Number n)) return false;
            ItemStackScriptUtil.setAttackDamageBonus(stack, n.doubleValue());
            ItemStackScriptUtil.syncInventory(player);
            return true;
        }
    }

    public static class GetItemNbt implements ScriptFunction {
        @Override public String getName() { return "getItemNbt"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return null;
            ItemStack stack = requireStack(player, args.get(1));
            if (stack == null || !stack.hasTag()) return null;
            return ItemStackScriptUtil.nbtToMap(stack.getTag());
        }
    }

    public static class SetItemNbt implements ScriptFunction {
        @Override public String getName() { return "setItemNbt"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, Map.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            ItemStack stack = requireStack(player, args.get(1));
            if (stack == null) return false;
            if (!(args.get(2) instanceof Map<?, ?> nbt)) return false;
            ItemStackScriptUtil.applyNbtKeys(stack, nbt);
            ItemStackScriptUtil.syncInventory(player);
            return true;
        }
    }
}
