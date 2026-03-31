package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptWorldData;

import java.util.List;

public class GetVarFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "getVar";
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
        if (args.isEmpty()) return null;
        String name = String.valueOf(args.get(0));

        // Note: Context doesn't expose variables directly to functions, 
        // but we can at least query globals if we want, or we can just 
        // implement this as a special case in ScriptExecutor. 
        // Actually, let's just make it return null for now, since 
        // variables are stored in ScriptExecutor which isn't accessible here.
        return null;
    }
}
