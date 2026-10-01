package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.HudHideOverrides;
import org.zonarstudio.spraute_engine.script.HudHideParts;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * hideHud(player, partOrList [, hidden]) — скрыть часть классического HUD (сердца, голод, …).
 * showHud(player [, partOrList]) — показать; без частей снимает все скрытия.
 * isHudHidden(player, part) — скрыта ли часть.
 */
public final class HudHideFunctions {
    private HudHideFunctions() {}

    private static ServerPlayer requirePlayer(Object arg, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(arg, source);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    public static class HideHud implements ScriptFunction {
        @Override public String getName() { return "hideHud"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            List<String> parts = HudHideParts.expand(args.get(1));
            if (parts.isEmpty()) return false;
            boolean hidden = args.size() < 3 || toBool(args.get(2), true);
            HudHideOverrides.setHidden(player, parts, hidden);
            return true;
        }
    }

    public static class ShowHud implements ScriptFunction {
        @Override public String getName() { return "showHud"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            if (args.size() < 2) {
                HudHideOverrides.clear(player);
                return true;
            }
            List<String> parts = HudHideParts.expand(args.get(1));
            if (parts.isEmpty()) {
                HudHideOverrides.clear(player);
                return true;
            }
            HudHideOverrides.setHidden(player, parts, false);
            return true;
        }
    }

    public static class IsHudHidden implements ScriptFunction {
        @Override public String getName() { return "isHudHidden"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = requirePlayer(args.get(0), source);
            if (player == null) return false;
            String part = HudHideParts.canonicalize(String.valueOf(args.get(1)));
            return part != null && HudHideOverrides.isHidden(player, part);
        }
    }

    private static boolean toBool(Object value, boolean defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return defaultVal;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "off".equalsIgnoreCase(s)
                || "no".equalsIgnoreCase(s) || "показать".equalsIgnoreCase(s)) {
            return false;
        }
        return true;
    }
}
