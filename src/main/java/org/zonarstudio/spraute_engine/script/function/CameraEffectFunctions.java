package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.CameraEffects;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * Additive camera motion: shake / punch / stop.
 * <pre>
 * shakeCamera(player, intensity [, duration [, frequency]])
 * punchCamera(player, yaw, pitch [, roll])
 * stopCameraShake(player)
 * </pre>
 */
public final class CameraEffectFunctions {
    private CameraEffectFunctions() {}

    private static float num(List<Object> args, int i, float fallback) {
        if (args == null || i >= args.size()) return fallback;
        Object v = args.get(i);
        return v instanceof Number n ? n.floatValue() : fallback;
    }

    public static class ShakeCamera implements ScriptFunction {
        @Override public String getName() { return "shakeCamera"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            CameraEffects.shake(player, num(args, 1, 0.4f), num(args, 2, 0.45f), num(args, 3, 18f));
            return null;
        }
    }

    public static class PunchCamera implements ScriptFunction {
        @Override public String getName() { return "punchCamera"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            CameraEffects.punch(player, num(args, 1, 0f), num(args, 2, 0f), num(args, 3, 0f));
            return null;
        }
    }

    public static class StopCameraShake implements ScriptFunction {
        @Override public String getName() { return "stopCameraShake"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player != null) CameraEffects.stop(player);
            return null;
        }
    }
}
