package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.LerpMath;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptLerpRuntime;

import java.util.List;

/** Script lerp / easing / property tween API. */
public final class LerpFunctions {
    private LerpFunctions() {}

    /**
     * Overloads:
     * <ul>
     *   <li>{@code lerp(a, b, t)} — числа, t 0..1</li>
     *   <li>{@code lerp(a, b, t, easing)} — то же с кривой плавности</li>
     *   <li>{@code lerp(target, prop, end, timeSec)} — tween свойства</li>
     *   <li>{@code lerp(target, prop, end, timeSec, easing)} — tween + плавность</li>
     *   <li>{@code lerp(target, prop, end, easing, timeSec)} — порядок easing/time можно менять</li>
     * </ul>
     * Timed-формы возвращают секунды: {@code await time(lerp(...))}.
     */
    public static class Lerp implements ScriptFunction {
        @Override public String getName() { return "lerp"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return 0;

            // Timed property: lerp(target, prop, end, time [, easing]) or (..., easing, time)
            if (isTimedPropertyCall(args)) {
                return runTimed(args, source);
            }

            // Math: lerp(a, b, t [, easing])
            double a = asDouble(args.get(0));
            double b = asDouble(args.get(1));
            double t = asDouble(args.get(2));
            if (args.size() > 3 && args.get(3) != null) {
                t = LerpMath.ease(t, String.valueOf(args.get(3)));
            }
            return LerpMath.lerp(a, b, t);
        }
    }

    /** lerpAngle(a, b, t [, easing]) */
    public static class LerpAngle implements ScriptFunction {
        @Override public String getName() { return "lerpAngle"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return 0;
            double t = asDouble(args.get(2));
            if (args.size() > 3 && args.get(3) != null) {
                t = LerpMath.ease(t, String.valueOf(args.get(3)));
            }
            return LerpMath.lerpAngle(asDouble(args.get(0)), asDouble(args.get(1)), t);
        }
    }

    /** lerpColor(from, to, t [, easing]) */
    public static class LerpColor implements ScriptFunction {
        @Override public String getName() { return "lerpColor"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return "#000000";
            double t = asDouble(args.get(2));
            if (args.size() > 3 && args.get(3) != null) {
                t = LerpMath.ease(t, String.valueOf(args.get(3)));
            }
            return LerpMath.lerpColor(args.get(0), args.get(1), t);
        }
    }

    /** ease(t, mode) — apply easing curve to 0..1. */
    public static class Ease implements ScriptFunction {
        @Override public String getName() { return "ease"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Number.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return 0;
            double t = asDouble(args.get(0));
            String mode = args.size() > 1 && args.get(1) != null ? String.valueOf(args.get(1)) : "linear";
            return LerpMath.ease(t, mode);
        }
    }

    /**
     * lerpTo(target, prop, end, timeSec [, easing])
     * Alias of timed {@code lerp(...)}. Both time and easing supported.
     */
    public static class LerpTo implements ScriptFunction {
        @Override public String getName() { return "lerpTo"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4 || source == null) return 0.0;
            return runTimed(args, source);
        }
    }

    /** lerpStop(target [, prop]) — cancel active tween(s). */
    public static class LerpStop implements ScriptFunction {
        @Override public String getName() { return "lerpStop"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            String prop = args.size() > 1 && args.get(1) != null ? String.valueOf(args.get(1)) : null;
            ScriptLerpRuntime.stop(args.get(0), prop);
            return null;
        }
    }

    /** curveEval(curve, t) — sample a custom-node curve field at t (0..1). */
    public static class CurveEval implements ScriptFunction {
        @Override public String getName() { return "curveEval"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return 0;
            double t = args.size() > 1 ? asDouble(args.get(1)) : 0;
            return org.zonarstudio.spraute_engine.scene.custom.ScriptCurve.eval(args.get(0), t);
        }
    }

    /** True when call looks like lerp(target, propName, end, time/easing...). */
    private static boolean isTimedPropertyCall(List<Object> args) {
        if (args.size() < 4) return false;
        Object a0 = args.get(0);
        Object a1 = args.get(1);
        // Math form: first two are numbers (or numeric strings).
        if (isNumeric(a0) && isNumeric(a1)) return false;
        // Prop must be a string-ish name.
        return a1 != null && !(a1 instanceof Number);
    }

    private static Object runTimed(List<Object> args, CommandSourceStack source) {
        if (source == null || args.size() < 4) return 0.0;
        Object target = args.get(0);
        String prop = args.get(1) == null ? "" : String.valueOf(args.get(1));
        Object end = args.get(2);

        double duration = 0;
        String easing = "linear";
        Object a3 = args.get(3);
        Object a4 = args.size() > 4 ? args.get(4) : null;

        if (args.size() == 4) {
            if (isNumeric(a3)) {
                duration = asDouble(a3);
            } else {
                // lerp(target, prop, end, easing) — time missing → default 1s
                easing = String.valueOf(a3);
                duration = 1.0;
            }
        } else {
            // 5+ args: accept (time, easing) or (easing, time)
            if (isNumeric(a3) && !isNumeric(a4)) {
                duration = asDouble(a3);
                easing = a4 == null ? "linear" : String.valueOf(a4);
            } else if (!isNumeric(a3) && isNumeric(a4)) {
                easing = String.valueOf(a3);
                duration = asDouble(a4);
            } else if (isNumeric(a3) && isNumeric(a4)) {
                duration = asDouble(a3);
                easing = "linear";
            } else {
                easing = a3 == null ? "linear" : String.valueOf(a3);
                duration = a4 == null ? 1.0 : asDouble(a4);
            }
        }

        return ScriptLerpRuntime.start(target, prop, end, duration, easing, source);
    }

    private static boolean isNumeric(Object v) {
        if (v instanceof Number) return true;
        if (v == null) return false;
        try {
            Double.parseDouble(String.valueOf(v).trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
