package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** {@code str_len(value)} — длина строки (для расчёта UI и т.п.). */
public class StrLenFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "strLen";
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
        if (args.isEmpty()) return 0;
        return String.valueOf(args.get(0)).length();
    }
}
