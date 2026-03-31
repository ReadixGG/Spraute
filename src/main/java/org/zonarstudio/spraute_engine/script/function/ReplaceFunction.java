package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class ReplaceFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "replace";
    }

    @Override
    public int getArgCount() {
        return 3;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, Object.class, Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 3) return "";
        String text = String.valueOf(args.get(0));
        String target = String.valueOf(args.get(1));
        String replacement = String.valueOf(args.get(2));
        return text.replace(target, replacement);
    }
}
