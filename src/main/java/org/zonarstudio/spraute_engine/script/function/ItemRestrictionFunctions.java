package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ItemUsageRestrictions;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** Script API: forbid / allow item usage per player (no level checks). */
public final class ItemRestrictionFunctions {

    private ItemRestrictionFunctions() {}

    private static ServerPlayer requireServerPlayer(Object target, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(target, source);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    public static class ForbidItem implements ScriptFunction {
        @Override public String getName() { return "forbidItem"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            String action = args.size() > 2 ? String.valueOf(args.get(2)) : "all";
            ItemUsageRestrictions.forbid(player, String.valueOf(args.get(1)), action);
            return true;
        }
    }

    public static class AllowItem implements ScriptFunction {
        @Override public String getName() { return "allowItem"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            String action = args.size() > 2 ? String.valueOf(args.get(2)) : "all";
            ItemUsageRestrictions.allow(player, String.valueOf(args.get(1)), action);
            return true;
        }
    }

    public static class ClearItemRestrictions implements ScriptFunction {
        @Override public String getName() { return "clearItemRestrictions"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            ItemUsageRestrictions.clear(player);
            return true;
        }
    }

    public static class IsItemForbidden implements ScriptFunction {
        @Override public String getName() { return "isItemForbidden"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;
            String itemId = String.valueOf(args.get(1));
            String actionStr = args.size() > 2 ? String.valueOf(args.get(2)) : "all";
            if ("all".equalsIgnoreCase(actionStr.trim())) {
                return ItemUsageRestrictions.isForbidden(player, itemId, ItemUsageRestrictions.Action.USE)
                        || ItemUsageRestrictions.isForbidden(player, itemId, ItemUsageRestrictions.Action.MINE)
                        || ItemUsageRestrictions.isForbidden(player, itemId, ItemUsageRestrictions.Action.ATTACK)
                        || ItemUsageRestrictions.isForbidden(player, itemId, ItemUsageRestrictions.Action.WEAR);
            }
            ItemUsageRestrictions.Action action = ItemUsageRestrictions.Action.parse(actionStr);
            return ItemUsageRestrictions.isForbidden(player, itemId, action);
        }
    }
}
