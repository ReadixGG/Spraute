package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** Runtime hardness / solid controls for {@code create block} custom blocks. */
public final class BlockPropsFunctions {
    private BlockPropsFunctions() {}

    public static class SetBlockHardness implements ScriptFunction {
        @Override public String getName() { return "setBlockHardness"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2 || args.get(0) == null || !(args.get(1) instanceof Number n)) return false;
            return CustomBlockRegistry.setBlockHardness(String.valueOf(args.get(0)), n.floatValue());
        }
    }

    public static class GetBlockHardness implements ScriptFunction {
        @Override public String getName() { return "getBlockHardness"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || args.get(0) == null) return null;
            Float h = CustomBlockRegistry.getBlockHardness(String.valueOf(args.get(0)));
            return h != null ? h.doubleValue() : null;
        }
    }

    public static class SetBlockSolid implements ScriptFunction {
        @Override public String getName() { return "setBlockSolid"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2 || args.get(0) == null) return false;
            boolean solid = toBool(args.get(1));
            return CustomBlockRegistry.setBlockSolid(String.valueOf(args.get(0)), solid);
        }
    }

    public static class GetBlockSolid implements ScriptFunction {
        @Override public String getName() { return "getBlockSolid"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || args.get(0) == null) return null;
            return CustomBlockRegistry.getBlockSolid(String.valueOf(args.get(0)));
        }
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "да".equals(s);
    }
}
