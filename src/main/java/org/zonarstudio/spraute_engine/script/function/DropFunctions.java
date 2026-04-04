package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.registry.CustomDropRegistry;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class DropFunctions {

    public static class AddMobDropFunction implements ScriptFunction {
        @Override
        public String getName() { return "addMobDrop"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[] { String.class, String.class, Integer.class, Integer.class, Integer.class, Boolean.class };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            String mobId = String.valueOf(args.get(0));
            String itemId = String.valueOf(args.get(1));
            int min = args.size() > 2 ? ((Number) args.get(2)).intValue() : 1;
            int max = args.size() > 3 ? ((Number) args.get(3)).intValue() : 1;
            int chance = args.size() > 4 ? ((Number) args.get(4)).intValue() : 100;
            boolean replace = args.size() > 5 && isTruthy(args.get(5));
            CustomDropRegistry.addMobDrop(mobId, itemId, min, max, chance, replace);
            return true;
        }

        private boolean isTruthy(Object o) {
            if (o instanceof Boolean b) return b;
            if (o instanceof String s) return s.equalsIgnoreCase("true");
            if (o instanceof Number n) return n.doubleValue() != 0;
            return o != null;
        }
    }

    public static class AddBlockDropFunction implements ScriptFunction {
        @Override
        public String getName() { return "addBlockDrop"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[] { String.class, String.class, Integer.class, Integer.class, Integer.class, Boolean.class };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            String blockId = String.valueOf(args.get(0));
            String itemId = String.valueOf(args.get(1));
            int min = args.size() > 2 ? ((Number) args.get(2)).intValue() : 1;
            int max = args.size() > 3 ? ((Number) args.get(3)).intValue() : 1;
            int chance = args.size() > 4 ? ((Number) args.get(4)).intValue() : 100;
            boolean replace = args.size() > 5 && isTruthy(args.get(5));
            CustomDropRegistry.addBlockDrop(blockId, itemId, min, max, chance, replace);
            return true;
        }

        private boolean isTruthy(Object o) {
            if (o instanceof Boolean b) return b;
            if (o instanceof String s) return s.equalsIgnoreCase("true");
            if (o instanceof Number n) return n.doubleValue() != 0;
            return o != null;
        }
    }
}