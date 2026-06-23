package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;

public class PlayerListFunctions {

    /** getPlayers() → List<ServerPlayer> всех онлайн-игроков */
    public static class GetPlayers implements ScriptFunction {
        @Override public String getName() { return "getPlayers"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getServer() == null) return new ArrayList<>();
            return new ArrayList<>(source.getServer().getPlayerList().getPlayers());
        }
    }

    /** playerCount() → int — количество онлайн-игроков */
    public static class PlayerCount implements ScriptFunction {
        @Override public String getName() { return "playerCount"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getServer() == null) return 0;
            return source.getServer().getPlayerList().getPlayers().size();
        }
    }

    /** getFirstPlayer() → ServerPlayer первый в списке или null */
    public static class GetFirstPlayer implements ScriptFunction {
        @Override public String getName() { return "getFirstPlayer"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getServer() == null) return null;
            List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
            return players.isEmpty() ? null : players.get(0);
        }
    }

    /** getLastPlayer() → ServerPlayer последний в списке или null */
    public static class GetLastPlayer implements ScriptFunction {
        @Override public String getName() { return "getLastPlayer"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getServer() == null) return null;
            List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
            return players.isEmpty() ? null : players.get(players.size() - 1);
        }
    }

    /** getPlayerAt(index) → ServerPlayer игрок по индексу или null */
    public static class GetPlayerAt implements ScriptFunction {
        @Override public String getName() { return "getPlayerAt"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getServer() == null || args.isEmpty()) return null;
            List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
            if (!(args.get(0) instanceof Number n)) return null;
            int i = n.intValue();
            if (i < 0 || i >= players.size()) return null;
            return players.get(i);
        }
    }
}
