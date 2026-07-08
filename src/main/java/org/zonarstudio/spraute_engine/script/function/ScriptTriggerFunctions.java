package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptTriggerRegistry;

import java.util.List;

public final class ScriptTriggerFunctions {

    private ScriptTriggerFunctions() {}

    public static class AutorunFunction implements ScriptFunction {
        @Override public String getName() { return "autorun"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String target;
            if (args.isEmpty()) {
                target = context != null ? context.getScriptName() : null;
                if (target == null || target.isEmpty()) return false;
            } else {
                target = String.valueOf(args.get(0)).trim();
            }
            ScriptTriggerRegistry.get().addAutorun(target);
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.isRunning() && source != null) {
                org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().run(target, source);
            }
            return true;
        }
    }

    public static class RunOnJoinFunction implements ScriptFunction {
        @Override public String getName() { return "runOnJoin"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ScriptTriggerRegistry.get().setOnJoin(String.valueOf(args.get(0)));
            return true;
        }
    }

    public static class RunOnFirstJoinFunction implements ScriptFunction {
        @Override public String getName() { return "runOnFirstJoin"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ScriptTriggerRegistry.get().setOnFirstJoin(String.valueOf(args.get(0)));
            return true;
        }
    }

    public static class RunAfterFunction implements ScriptFunction {
        @Override public String getName() { return "runAfter"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ScriptTriggerRegistry.get().setRunAfter(String.valueOf(args.get(0)), String.valueOf(args.get(1)));
            return true;
        }
    }
}
