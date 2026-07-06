package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.UpdateSprauteUiWidgetPacket;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.SprauteContainerMenu;
import org.zonarstudio.spraute_engine.ui.UiTracker;

import java.util.List;

/** Дополнительные функции для работы с GUI: слоты контейнера, ввод, скролл, состояние. */
public final class UiFunctions {

    private UiFunctions() {}

    static Player resolvePlayer(Object target, CommandSourceStack source) {
        return ItemStackScriptUtil.resolvePlayer(target, source);
    }

    static SprauteContainerMenu containerOf(Player player) {
        if (player == null) return null;
        if (player.containerMenu instanceof SprauteContainerMenu m) return m;
        return null;
    }

    static int resolveSlotIndex(SprauteContainerMenu menu, Object slotRef) {
        if (menu == null || slotRef == null) return -1;
        int size = menu.getCustomContainer().getContainerSize();
        if (slotRef instanceof Number n) {
            int i = n.intValue();
            return i >= 0 && i < size ? i : -1;
        }
        String s = String.valueOf(slotRef).trim();
        if (s.isEmpty()) return -1;
        int byId = menu.slotIndexOf(s);
        if (byId >= 0) return byId;
        try {
            int i = Integer.parseInt(s);
            return i >= 0 && i < size ? i : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    static Item parseItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return net.minecraft.world.item.Items.AIR;
        ResourceLocation rl = itemId.contains(":")
                ? new ResourceLocation(itemId)
                : new ResourceLocation("minecraft", itemId);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                item = block.asItem();
            }
        }
        return item != null ? item : net.minecraft.world.item.Items.AIR;
    }

    // ---- состояние UI ----

    public static class UiIsOpen implements ScriptFunction {
        @Override public String getName() { return "uiIsOpen"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player player = args.isEmpty() ? null : resolvePlayer(args.get(0), source);
            if (player == null) return false;
            return UiTracker.isOpen(player.getUUID()) || containerOf(player) != null;
        }
    }

    public static class UiContainerOpen implements ScriptFunction {
        @Override public String getName() { return "uiContainerOpen"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player player = args.isEmpty() ? null : resolvePlayer(args.get(0), source);
            return containerOf(player) != null;
        }
    }

    // ---- слоты GUI-контейнера ----

    public static class UiSlotItem implements ScriptFunction {
        @Override public String getName() { return "uiSlotItem"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return "";
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return "";
            return itemIdOf(menu.getCustomContainer().getItem(idx));
        }
    }

    public static class UiSlotIsEmpty implements ScriptFunction {
        @Override public String getName() { return "uiSlotIsEmpty"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return true;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return true;
            return menu.getCustomContainer().getItem(idx).isEmpty();
        }
    }

    public static class UiSlotCount implements ScriptFunction {
        /** Количество предметов в стаке слота GUI. */
        @Override public String getName() { return "uiSlotCount"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return 0;
            return menu.getCustomContainer().getItem(idx).getCount();
        }
    }

    public static class UiSlotSlots implements ScriptFunction {
        /** Число кастомных слотов в открытом GUI-контейнере. */
        @Override public String getName() { return "uiSlotSlots"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Player player = args.isEmpty() ? null : resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            return menu != null ? menu.getCustomContainer().getContainerSize() : 0;
        }
    }

    public static class UiSlotHasItem implements ScriptFunction {
        @Override public String getName() { return "uiSlotHasItem"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return false;
            ItemStack stack = menu.getCustomContainer().getItem(idx);
            if (stack.isEmpty()) return false;
            String want = String.valueOf(args.get(2)).trim();
            if (want.isEmpty()) return false;
            String have = itemIdOf(stack);
            return have.equalsIgnoreCase(want)
                    || have.equalsIgnoreCase(want.contains(":") ? want : "minecraft:" + want);
        }
    }

    public static class UiSetSlot implements ScriptFunction {
        @Override public String getName() { return "uiSetSlot"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return null;
            Item item = parseItem(String.valueOf(args.get(2)));
            int count = 1;
            if (args.size() > 3 && args.get(3) instanceof Number n) count = Math.max(1, n.intValue());
            if (item == net.minecraft.world.item.Items.AIR) {
                menu.getCustomContainer().setItem(idx, ItemStack.EMPTY);
            } else {
                menu.getCustomContainer().setItem(idx, new ItemStack(item, count));
            }
            menu.getCustomContainer().setChanged();
            return null;
        }
    }

    public static class UiClearSlot implements ScriptFunction {
        @Override public String getName() { return "uiClearSlot"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return null;
            menu.getCustomContainer().setItem(idx, ItemStack.EMPTY);
            menu.getCustomContainer().setChanged();
            return null;
        }
    }

    // ---- поля ввода ----

    public static class UiGetInput implements ScriptFunction {
        @Override public String getName() { return "uiGetInput"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return "";
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return "";
            return UiTracker.getInput(player.getUUID(), String.valueOf(args.get(1)));
        }
    }

    public static class UiSetInput implements ScriptFunction {
        @Override public String getName() { return "uiSetInput"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || !(resolvePlayer(args.get(0), source) instanceof ServerPlayer sp)) return null;
            String widgetId = String.valueOf(args.get(1));
            String text = String.valueOf(args.get(2));
            if (widgetId.isEmpty()) return null;
            UiTracker.setInput(sp.getUUID(), widgetId, text);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new UpdateSprauteUiWidgetPacket(widgetId, "text", text));
            return null;
        }
    }

    // ---- скролл ----

    public static class UiScrollGet implements ScriptFunction {
        @Override public String getName() { return "uiScrollGet"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0;
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return 0;
            return (double) UiTracker.getScroll(player.getUUID(), String.valueOf(args.get(1)));
        }
    }

    public static class UiScrollSet implements ScriptFunction {
        @Override public String getName() { return "uiScrollSet"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || !(resolvePlayer(args.get(0), source) instanceof ServerPlayer sp)) return null;
            String widgetId = String.valueOf(args.get(1));
            float offset = ((Number) args.get(2)).floatValue();
            if (widgetId.isEmpty()) return null;
            UiTracker.setScroll(sp.getUUID(), widgetId, offset);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new UpdateSprauteUiWidgetPacket(widgetId, "scrollOffset", String.valueOf(offset)));
            return null;
        }
    }
}
