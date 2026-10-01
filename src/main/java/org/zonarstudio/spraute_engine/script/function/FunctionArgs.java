package org.zonarstudio.spraute_engine.script.function;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft coercion for registered script functions. Validates arity when declared,
 * and converts numbers/strings to the declared parameter types without hard casts.
 */
public final class FunctionArgs {
    private FunctionArgs() {}

    public static List<Object> coerce(ScriptFunction function, List<Object> args) {
        if (function == null) return args == null ? List.of() : args;
        List<Object> input = args == null ? List.of() : args;
        int expected = function.getArgCount();
        Class<?>[] types = function.getArgTypes();

        if (expected >= 0 && input.size() < expected) {
            throw new RuntimeException("Function '" + function.getName() + "' expects "
                    + expected + " argument(s), got " + input.size());
        }

        if (types == null || types.length == 0) return input;

        List<Object> out = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            Object value = input.get(i);
            if (i < types.length) {
                out.add(coerceValue(value, types[i], function.getName(), i));
            } else {
                out.add(value);
            }
        }
        return out;
    }

    private static Object coerceValue(Object value, Class<?> type, String functionName, int index) {
        if (value == null || type == null || type == Object.class) return value;
        if (type.isInstance(value)) return value;

        if (type == String.class) {
            return String.valueOf(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.doubleValue() != 0;
            String s = String.valueOf(value).trim();
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            Number number = asNumber(value);
            if (number == null) {
                throw new RuntimeException("Function '" + functionName + "' argument #" + (index + 1)
                        + " expects a number, got " + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            if (type == Integer.class || type == int.class) return number.intValue();
            if (type == Long.class || type == long.class) return number.longValue();
            if (type == Float.class || type == float.class) return number.floatValue();
            if (type == Double.class || type == double.class) return number.doubleValue();
            if (type == Short.class || type == short.class) return number.shortValue();
            if (type == Byte.class || type == byte.class) return number.byteValue();
            return number.doubleValue();
        }
        return value;
    }

    private static Number asNumber(Object value) {
        if (value instanceof Number n) return n;
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            try {
                if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
                    return Double.parseDouble(trimmed);
                }
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
