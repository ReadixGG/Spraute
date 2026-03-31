package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * Formats a number for display: whole values without a trailing {@code .0}
 * ({@code 27.0} → {@code "27"}). Non-whole numbers keep a short decimal form.
 */
public class IntStrFunction implements ScriptFunction {

    private final String name;

    public IntStrFunction(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty()) return "";
        return formatArg(args.get(0));
    }

    static String formatArg(Object a) {
        if (a == null) return "";
        if (a instanceof Number n) {
            return formatNumber(n);
        }
        String s = String.valueOf(a);
        try {
            return formatNumber(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static String formatNumber(Number n) {
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
            return String.valueOf(n.longValue());
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return String.valueOf(n);
        }
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return String.valueOf((long) Math.rint(d));
        }
        String raw = String.valueOf(n);
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2);
        }
        return raw;
    }
}
