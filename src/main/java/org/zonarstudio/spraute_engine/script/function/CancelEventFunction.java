package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class CancelEventFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "cancelEvent";
    }

    @Override
    public int getArgCount() {
        return 0;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        context.cancelEvent();
        return null;
    }
}
