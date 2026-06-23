package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTagFunctions {

    /** Хранилище тегов: UUID игрока → набор тегов. Живёт до перезапуска сервера. */
    private static final Map<UUID, Set<String>> TAGS = new ConcurrentHashMap<>();

    public static void clearAll() { TAGS.clear(); }

    private static Set<String> tagsOf(ServerPlayer player) {
        return TAGS.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
    }

    /** addPlayerTag(player, tag) — добавить тег игроку */
    public static class AddTag implements ScriptFunction {
        @Override public String getName() { return "addPlayerTag"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolve(args.get(0), source);
            if (player == null) return null;
            tagsOf(player).add(String.valueOf(args.get(1)));
            return null;
        }
    }

    /** removePlayerTag(player, tag) — убрать тег у игрока */
    public static class RemoveTag implements ScriptFunction {
        @Override public String getName() { return "removePlayerTag"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolve(args.get(0), source);
            if (player == null) return null;
            tagsOf(player).remove(String.valueOf(args.get(1)));
            return null;
        }
    }

    /** hasPlayerTag(player, tag) → boolean — есть ли тег у игрока */
    public static class HasTag implements ScriptFunction {
        @Override public String getName() { return "hasPlayerTag"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = resolve(args.get(0), source);
            if (player == null) return false;
            return tagsOf(player).contains(String.valueOf(args.get(1)));
        }
    }

    /** getPlayersByTag(tag) → List<ServerPlayer> — список игроков с тегом */
    public static class GetByTag implements ScriptFunction {
        @Override public String getName() { return "getPlayersByTag"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            List<Object> result = new ArrayList<>();
            if (args.isEmpty() || source.getServer() == null) return result;
            String tag = String.valueOf(args.get(0));
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                Set<String> tags = TAGS.get(p.getUUID());
                if (tags != null && tags.contains(tag)) result.add(p);
            }
            return result;
        }
    }

    /** getPlayerTags(player) → List<String> — все теги игрока */
    public static class GetTags implements ScriptFunction {
        @Override public String getName() { return "getPlayerTags"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return new ArrayList<>();
            ServerPlayer player = resolve(args.get(0), source);
            if (player == null) return new ArrayList<>();
            return new ArrayList<>(tagsOf(player));
        }
    }

    private static ServerPlayer resolve(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (source.getServer() == null) return null;
        String name = String.valueOf(arg);
        return source.getServer().getPlayerList().getPlayerByName(name);
    }
}
