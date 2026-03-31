package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DictFunctions {

    public static class Create implements ScriptFunction {
        @Override public String getName() { return "dictCreate"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new HashMap<String, Object>();
        }
    }

    /**
     * {@code dict("a", 1, "b", 2)} — словарь из пар ключ-значение (чётное число аргументов).
     * Пустой вызов {@code dict()} эквивалентен {@code dict_create()}.
     */
    public static class FromPairs implements ScriptFunction {
        @Override public String getName() { return "dict"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i + 1 < args.size(); i += 2) {
                m.put(String.valueOf(args.get(i)), args.get(i + 1));
            }
            return m;
        }
    }

    public static class Set implements ScriptFunction {
        @Override public String getName() { return "dictSet"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof Map map) {
                map.put(String.valueOf(args.get(1)), args.get(2));
            }
            return null;
        }
    }

    public static class Get implements ScriptFunction {
        @Override public String getName() { return "dictGet"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof Map map) {
                return map.get(String.valueOf(args.get(1)));
            }
            return null;
        }
    }

    public static class Remove implements ScriptFunction {
        @Override public String getName() { return "dictRemove"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof Map map) {
                map.remove(String.valueOf(args.get(1)));
            }
            return null;
        }
    }
}
