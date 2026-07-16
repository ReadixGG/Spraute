package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.zonarstudio.spraute_engine.script.PlayerFlightOverrides;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.Locale;

public final class PlayerGamemodeFunctions {
    private PlayerGamemodeFunctions() {}

    private static ServerPlayer resolvePlayer(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
            ServerPlayer byUuid = source.getServer().getPlayerList().getPlayer(p.getUUID());
            if (byUuid != null) return byUuid;
        }
        if (source.getServer() == null) return null;
        return source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
    }

    static GameType parseMode(Object arg) {
        if (arg == null) return null;
        if (arg instanceof GameType gt) return gt;
        if (arg instanceof Number n) return GameType.byId(n.intValue());
        String s = String.valueOf(arg).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "выживание", "выжив" -> GameType.SURVIVAL;
            case "креатив", "творческий" -> GameType.CREATIVE;
            case "приключение" -> GameType.ADVENTURE;
            case "наблюдатель", "спектатор" -> GameType.SPECTATOR;
            case "0" -> GameType.SURVIVAL;
            case "1" -> GameType.CREATIVE;
            case "2" -> GameType.ADVENTURE;
            case "3" -> GameType.SPECTATOR;
            default -> {
                GameType byName = GameType.byName(s);
                if (byName != null) yield byName;
                try {
                    yield GameType.byId(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    static String modeId(GameType type) {
        return type == null ? "" : type.getName();
    }

    /** setPlayerGamemode(player, mode) — survival / creative / adventure / spectator */
    public static class SetPlayerGamemode implements ScriptFunction {
        @Override public String getName() { return "setPlayerGamemode"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            GameType mode = parseMode(args.get(1));
            if (player == null || mode == null) return false;
            player.setGameMode(mode);
            PlayerFlightOverrides.apply(player);
            return true;
        }
    }

    /** getPlayerGamemode(player) → "survival", "creative", … */
    public static class GetPlayerGamemode implements ScriptFunction {
        @Override public String getName() { return "getPlayerGamemode"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return "";
            return modeId(player.gameMode.getGameModeForPlayer());
        }
    }

    /** isPlayerGamemode(player, mode) */
    public static class IsPlayerGamemode implements ScriptFunction {
        @Override public String getName() { return "isPlayerGamemode"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            GameType expected = parseMode(args.get(1));
            if (player == null || expected == null) return false;
            return player.gameMode.getGameModeForPlayer() == expected;
        }
    }
}
