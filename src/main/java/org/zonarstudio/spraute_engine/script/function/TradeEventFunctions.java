package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.List;

/**
 * fireTradeBuy(player, item_id, price) / fireTradeSell(...) — уведомляет все активные скрипты о сделке в трейде.
 */
public final class TradeEventFunctions {

    private TradeEventFunctions() {}

    private static ServerPlayer resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof ServerPlayer sp) return sp;
        if (target instanceof Player p && p instanceof ServerPlayer sp) return sp;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    public static class FireTradeBuy implements ScriptFunction {
        @Override
        public String getName() { return "fireTradeBuy"; }

        @Override
        public int getArgCount() { return 3; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String itemId = String.valueOf(args.get(1));
            int price = ((Number) args.get(2)).intValue();
            ScriptManager.getInstance().onTradeBuy(player, itemId, price);
            return null;
        }
    }

    public static class FireTradeSell implements ScriptFunction {
        @Override
        public String getName() { return "fireTradeSell"; }

        @Override
        public int getArgCount() { return 3; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String itemId = String.valueOf(args.get(1));
            int price = ((Number) args.get(2)).intValue();
            ScriptManager.getInstance().onTradeSell(player, itemId, price);
            return null;
        }
    }
}
