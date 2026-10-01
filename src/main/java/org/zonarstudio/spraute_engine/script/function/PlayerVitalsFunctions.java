package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.PlayerVitalsOverrides;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * setPlayerHealthEnabled(player, enabled) — урон/реген; выкл. ещё скрывает сердца.
 * setPlayerHungerEnabled(player, enabled) — голод не падает; выкл. скрывает полоску.
 * setPlayerXpVisible(player, visible) — только полоска и цифра опыта, XP копится как обычно.
 * setPlayerVitalsEnabled(player, enabled) — HP, голод и полоска опыта.
 */
public final class PlayerVitalsFunctions {
    private PlayerVitalsFunctions() {}

    private static ServerPlayer requirePlayer(Object arg, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(arg, source);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    private static boolean asBool(Object value, boolean defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(value).trim();
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "off".equalsIgnoreCase(s)
                || "no".equalsIgnoreCase(s) || "выкл".equalsIgnoreCase(s)) {
            return false;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "on".equalsIgnoreCase(s)
                || "yes".equalsIgnoreCase(s) || "вкл".equalsIgnoreCase(s) || defaultVal;
    }

    public static class SetHealthEnabled implements ScriptFunction {
        @Override public String getName() { return "setPlayerHealthEnabled"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            PlayerVitalsOverrides.setHealthEnabled(player, asBool(args.get(1), true));
            return true;
        }
    }

    public static class SetHungerEnabled implements ScriptFunction {
        @Override public String getName() { return "setPlayerHungerEnabled"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            PlayerVitalsOverrides.setHungerEnabled(player, asBool(args.get(1), true));
            return true;
        }
    }

    public static class SetXpVisible implements ScriptFunction {
        @Override public String getName() { return "setPlayerXpVisible"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            PlayerVitalsOverrides.setXpVisible(player, asBool(args.get(1), true));
            return true;
        }
    }

    public static class SetVitalsEnabled implements ScriptFunction {
        @Override public String getName() { return "setPlayerVitalsEnabled"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            PlayerVitalsOverrides.setVitalsEnabled(player, asBool(args.get(1), true));
            return true;
        }
    }

    public static class IsHealthEnabled implements ScriptFunction {
        @Override public String getName() { return "isPlayerHealthEnabled"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return true;
            ServerPlayer player = requirePlayer(args.get(0), source);
            return player != null && !PlayerVitalsOverrides.isHealthDisabled(player);
        }
    }

    public static class IsHungerEnabled implements ScriptFunction {
        @Override public String getName() { return "isPlayerHungerEnabled"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return true;
            ServerPlayer player = requirePlayer(args.get(0), source);
            return player != null && !PlayerVitalsOverrides.isHungerDisabled(player);
        }
    }

    public static class IsXpVisible implements ScriptFunction {
        @Override public String getName() { return "isPlayerXpVisible"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return true;
            ServerPlayer player = requirePlayer(args.get(0), source);
            return player != null && !PlayerVitalsOverrides.isXpHudDisabled(player);
        }
    }
}
