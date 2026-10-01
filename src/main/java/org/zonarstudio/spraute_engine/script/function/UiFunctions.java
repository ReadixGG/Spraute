package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        Item item = ItemStackScriptUtil.resolveItem(itemId);
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
            return ItemStackScriptUtil.matches(stack, String.valueOf(args.get(2)));
        }
    }

    /** True if the stack in the slot is a custom (script-defined) item of this mod. */
    public static class UiSlotIsCustom implements ScriptFunction {
        @Override public String getName() { return "uiSlotIsCustom"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return false;
            return customIdOf(menu.getCustomContainer().getItem(idx)) != null;
        }
    }

    /** Custom item id (without namespace) of the stack in the slot, "" if empty or not custom. */
    public static class UiSlotCustomId implements ScriptFunction {
        @Override public String getName() { return "uiSlotCustomId"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return "";
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return "";
            String id = customIdOf(menu.getCustomContainer().getItem(idx));
            return id != null ? id : "";
        }
    }

    /** True if the slot holds the given custom item (id with or without the mod namespace). */
    public static class UiSlotHasCustom implements ScriptFunction {
        @Override public String getName() { return "uiSlotHasCustom"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return false;
            Player player = resolvePlayer(args.get(0), source);
            SprauteContainerMenu menu = containerOf(player);
            int idx = resolveSlotIndex(menu, args.get(1));
            if (idx < 0) return false;
            String have = customIdOf(menu.getCustomContainer().getItem(idx));
            if (have == null) return false;
            String want = String.valueOf(args.get(2)).trim();
            int sep = want.indexOf(':');
            if (sep >= 0) want = want.substring(sep + 1);
            return have.equalsIgnoreCase(want);
        }
    }

    /** Custom item id without namespace, or null when the stack is not one of the mod's custom items. */
    static String customIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !org.zonarstudio.spraute_engine.Spraute_engine.MODID.equals(key.getNamespace())) return null;
        return key.getPath();
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
                ItemStack stack = ItemStackScriptUtil.createStack(String.valueOf(args.get(2)), count);
                menu.getCustomContainer().setItem(idx, stack.isEmpty() ? new ItemStack(item, count) : stack);
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
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return "";
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return "";
            return UiTracker.getInput(player.getUUID(), UiUpdateFunction.widgetIdOf(args.get(1)));
        }
    }

    public static class UiSetInput implements ScriptFunction {
        @Override public String getName() { return "uiSetInput"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || !(resolvePlayer(args.get(0), source) instanceof ServerPlayer sp)) return null;
            String widgetId = UiUpdateFunction.widgetIdOf(args.get(1));
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
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return 0;
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return 0;
            return (double) UiTracker.getScroll(player.getUUID(), UiUpdateFunction.widgetIdOf(args.get(1)));
        }
    }

    public static class UiScrollSet implements ScriptFunction {
        @Override public String getName() { return "uiScrollSet"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class, Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || !(resolvePlayer(args.get(0), source) instanceof ServerPlayer sp)) return null;
            String widgetId = UiUpdateFunction.widgetIdOf(args.get(1));
            float offset = ((Number) args.get(2)).floatValue();
            if (widgetId.isEmpty()) return null;
            UiTracker.setScroll(sp.getUUID(), widgetId, offset);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new UpdateSprauteUiWidgetPacket(widgetId, "scrollOffset", String.valueOf(offset)));
            return null;
        }
    }
}
