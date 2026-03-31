package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;

public class ListFunctions {

    public static class Create implements ScriptFunction {
        @Override public String getName() { return "listCreate"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new ArrayList<>();
        }
    }

    /** Алиас для скриптов: {@code list()} то же, что {@code list_create()}. */
    public static class ListAlias implements ScriptFunction {
        @Override public String getName() { return "list"; }
        @Override public int getArgCount() { return 0; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new ArrayList<>();
        }
    }

    public static class Add implements ScriptFunction {
        @Override public String getName() { return "listAdd"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof List list) {
                list.add(args.get(1));
            }
            return null;
        }
    }

    public static class Get implements ScriptFunction {
        @Override public String getName() { return "listGet"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof List list && args.get(1) instanceof Number n) {
                int i = n.intValue();
                if (i >= 0 && i < list.size()) return list.get(i);
            }
            return null;
        }
    }

    public static class Set implements ScriptFunction {
        @Override public String getName() { return "listSet"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class, Object.class}; }
        @SuppressWarnings("unchecked")
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof List list && args.get(1) instanceof Number n) {
                int i = n.intValue();
                if (i >= 0 && i < list.size()) {
                    list.set(i, args.get(2));
                }
            }
            return null;
        }
    }

    public static class Size implements ScriptFunction {
        @Override public String getName() { return "listSize"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof List list) {
                return list.size();
            }
            return 0;
        }
    }

    public static class Remove implements ScriptFunction {
        @Override public String getName() { return "listRemove"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.get(0) instanceof List list && args.get(1) instanceof Number n) {
                int i = n.intValue();
                if (i >= 0 && i < list.size()) {
                    list.remove(i);
                }
            }
            return null;
        }
    }
}
