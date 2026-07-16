package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.PlayerFlightOverrides;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public final class PlayerFlightFunctions {
    private PlayerFlightFunctions() {}

    private static ServerPlayer resolvePlayer(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
            ServerPlayer byUuid = source.getServer().getPlayerList().getPlayer(p.getUUID());
            if (byUuid != null) return byUuid;
        }
        if (source.getServer() == null) return null;
        return source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
    }

    /** setPlayerFlight(player, enabled) — включить/выключить полёт в выживании */
    public static class SetPlayerFlight implements ScriptFunction {
        @Override public String getName() { return "setPlayerFlight"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Boolean.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            boolean enabled = args.get(1) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(1)));
            PlayerFlightOverrides.set(player, enabled);
            return null;
        }
    }

    /** getPlayerFlight(player) — включён ли скриптовый полёт */
    public static class GetPlayerFlight implements ScriptFunction {
        @Override public String getName() { return "getPlayerFlight"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            return player != null && PlayerFlightOverrides.get(player);
        }
    }
}
