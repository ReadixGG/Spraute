package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.scene.PlayerAudioOps;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.Locale;

/** {@code playSound} / {@code stopSound} — routed through the player's AUDIO node. */
public class SoundFunctions {

    private static ServerPlayer resolvePlayer(Object target, CommandSourceStack source) {
        return CameraScriptUtil.resolvePlayer(target, source);
    }

    private static float toFloat(Object v, float def) {
        if (v instanceof Number n) return n.floatValue();
        if (v == null) return def;
        try {
            return Float.parseFloat(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean toBool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return def;
        return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on");
    }

    private static ResourceLocation soundId(Object raw) {
        String soundId = String.valueOf(raw == null ? "" : raw).trim();
        if (soundId.isEmpty()) return null;
        return soundId.contains(":")
                ? new ResourceLocation(soundId)
                : new ResourceLocation("minecraft", soundId);
    }

    public static class PlaySound implements ScriptFunction {
        @Override
        public String getName() {
            return "playSound";
        }

        @Override
        public int getArgCount() {
            return -1;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[0];
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer sp = resolvePlayer(args.get(0), source);
            if (sp == null) return null;
            if (soundId(args.get(1)) == null) return null;

            float volume = args.size() > 2 ? Math.max(0f, toFloat(args.get(2), 1f)) : 1f;
            float pitch = args.size() > 3 ? Math.max(0.01f, toFloat(args.get(3), 1f)) : 1f;
            boolean loop = args.size() > 4 && toBool(args.get(4), false);
            PlayerAudioOps.playForPlayer(sp, args.get(1), volume, pitch, loop);
            return null;
        }
    }

    public static class StopSound implements ScriptFunction {
        @Override
        public String getName() {
            return "stopSound";
        }

        @Override
        public int getArgCount() {
            return -1;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[0];
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer sp = resolvePlayer(args.get(0), source);
            if (sp == null) return null;
            if (args.size() >= 2) {
                PlayerAudioOps.stopForPlayer(sp, args.get(1));
            } else {
                PlayerAudioOps.stopForPlayer(sp, null);
            }
            return null;
        }
    }
}
