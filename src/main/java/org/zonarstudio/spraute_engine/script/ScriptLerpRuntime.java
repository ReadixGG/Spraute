package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side property tweens started by {@code lerpTo(...)}.
 * Tick once per server tick; replaces previous tween on the same target+property.
 */
public final class ScriptLerpRuntime {
    private static final Map<String, Tween> TWEENS = new ConcurrentHashMap<>();

    private ScriptLerpRuntime() {}

    public enum Mode { NUMBER, ANGLE, COLOR }

    /**
     * Start (or replace) a tween. Returns duration seconds (for {@code await time(lerpTo(...))}).
     */
    public static double start(Object target, String prop, Object endValue, double durationSec,
                               String easing, CommandSourceStack source) {
        if (target == null || prop == null || prop.isBlank() || source == null) return 0;
        if (durationSec < 0) durationSec = 0;

        Object startValue = ScriptPropertyAccess.get(target, prop, source);
        Mode mode = detectMode(prop, startValue, endValue);
        if (mode == Mode.NUMBER || mode == Mode.ANGLE) {
            if (!(endValue instanceof Number) && !parseDouble(endValue).isPresent()) return 0;
            if (startValue == null) startValue = 0;
            if (!(startValue instanceof Number) && !parseDouble(startValue).isPresent()) return 0;
        }
        if (mode == Mode.COLOR) {
            if (!LerpMath.looksLikeColor(endValue)) return 0;
            if (!LerpMath.looksLikeColor(startValue)) startValue = endValue;
        }

        String key = ScriptPropertyAccess.tweenKey(target, prop);
        if (durationSec <= 1e-6) {
            TWEENS.remove(key);
            apply(target, prop, endValue, mode, 1.0, startValue, endValue, source);
            return 0;
        }

        Tween tween = new Tween(key, target, prop, startValue, endValue, mode,
                Math.max(1, (long) Math.round(durationSec * 20.0)),
                easing == null || easing.isBlank() ? "linear" : easing,
                source);
        TWEENS.put(key, tween);
        apply(target, prop, null, mode, 0, startValue, endValue, source);
        return durationSec;
    }

    public static void stop(Object target, String propOrNull) {
        if (target == null) return;
        if (propOrNull != null && !propOrNull.isBlank()) {
            TWEENS.remove(ScriptPropertyAccess.tweenKey(target, propOrNull));
            return;
        }
        String prefix;
        if (target instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView n) {
            prefix = "node:" + n.getSceneId() + ":" + n.getNodeId() + ":";
        } else if (target instanceof Entity e) {
            prefix = "ent:" + e.getUUID() + ":";
        } else if (target instanceof String s) {
            prefix = "id:" + s + ":";
        } else if (target instanceof Map<?, ?>) {
            prefix = "map:" + System.identityHashCode(target) + ":";
        } else {
            prefix = "obj:" + System.identityHashCode(target) + ":";
        }
        List<String> remove = new ArrayList<>();
        for (String k : TWEENS.keySet()) {
            if (k.startsWith(prefix)) remove.add(k);
        }
        for (String k : remove) TWEENS.remove(k);
    }

    public static void tick(MinecraftServer server) {
        if (TWEENS.isEmpty()) return;
        Iterator<Map.Entry<String, Tween>> it = TWEENS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Tween> e = it.next();
            Tween tw = e.getValue();
            if (tw.source == null || tw.source.getLevel() == null
                    || tw.source.getServer() != server) {
                // Keep tweens from other servers? single server usually — drop if level gone.
                if (tw.source == null || tw.source.getLevel() == null) {
                    it.remove();
                }
                continue;
            }
            tw.tick++;
            double rawT = tw.durationTicks <= 0 ? 1.0 : (double) tw.tick / (double) tw.durationTicks;
            double t = LerpMath.ease(rawT, tw.easing);
            boolean done = tw.tick >= tw.durationTicks;
            if (done) t = 1.0;
            if (!apply(tw.target, tw.prop, null, tw.mode, t, tw.start, tw.end, tw.source)) {
                it.remove();
                continue;
            }
            if (done) it.remove();
        }
    }

    public static void clearAll() {
        TWEENS.clear();
    }

    private static Mode detectMode(String prop, Object start, Object end) {
        if (LerpMath.looksLikeColor(end) || LerpMath.looksLikeColor(start)) return Mode.COLOR;
        if (LerpMath.isAngleProp(prop)) return Mode.ANGLE;
        return Mode.NUMBER;
    }

    private static boolean apply(Object target, String prop, Object forcedEnd, Mode mode,
                                 double t, Object start, Object end, CommandSourceStack source) {
        Object value;
        if (forcedEnd != null && t >= 1.0) {
            value = forcedEnd;
        } else {
            value = switch (mode) {
                case COLOR -> LerpMath.lerpColor(start, end, t);
                case ANGLE -> LerpMath.lerpAngle(asDouble(start), asDouble(end), t);
                case NUMBER -> LerpMath.lerp(asDouble(start), asDouble(end), t);
            };
        }
        // Prefer integer-looking numbers when both ends were ints and t≈1?
        if (mode == Mode.NUMBER && value instanceof Double d
                && start instanceof Number sn && end instanceof Number en
                && isWhole(sn.doubleValue()) && isWhole(en.doubleValue())
                && Math.abs(d - Math.rint(d)) < 1e-6) {
            value = (int) Math.rint(d);
        }
        return ScriptPropertyAccess.set(target, prop, value, source);
    }

    private static boolean isWhole(double d) {
        return Math.abs(d - Math.rint(d)) < 1e-9;
    }

    private static double asDouble(Object v) {
        return parseDouble(v).orElse(0);
    }

    private static java.util.OptionalDouble parseDouble(Object v) {
        if (v instanceof Number n) return java.util.OptionalDouble.of(n.doubleValue());
        if (v == null) return java.util.OptionalDouble.empty();
        try {
            return java.util.OptionalDouble.of(Double.parseDouble(String.valueOf(v).trim()));
        } catch (Exception e) {
            return java.util.OptionalDouble.empty();
        }
    }

    private static final class Tween {
        final String key;
        final Object target;
        final String prop;
        final Object start;
        final Object end;
        final Mode mode;
        final long durationTicks;
        final String easing;
        final CommandSourceStack source;
        long tick;

        Tween(String key, Object target, String prop, Object start, Object end, Mode mode,
              long durationTicks, String easing, CommandSourceStack source) {
            this.key = key;
            this.target = target;
            this.prop = prop;
            this.start = start;
            this.end = end;
            this.mode = mode;
            this.durationTicks = durationTicks;
            this.easing = easing;
            this.source = source;
            this.tick = 0;
        }
    }
}
