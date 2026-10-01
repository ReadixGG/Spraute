package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.List;
import java.util.Map;

public class ScriptManagementFunctions {

    public static class StartScriptFunction implements ScriptFunction {
        @Override public String getName() { return "startScript"; }
        @Override public int getArgCount() { return -1; } // 1 or 2 args
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Map.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            String scriptName = String.valueOf(args.get(0));
            Map<String, Object> vars = new java.util.HashMap<>();
            if (args.size() > 1 && args.get(1) instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) map;
                vars.putAll(castMap);
            }
            ScriptManager.seedInheritedSceneContext(vars);
            return ScriptManager.getInstance().run(scriptName, source, vars);
        }
    }

    public static class StopScriptFunction implements ScriptFunction {
        @Override public String getName() { return "stopScript"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            String scriptName = String.valueOf(args.get(0));
            return ScriptManager.getInstance().stopScript(scriptName);
        }
    }
}
