package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import java.util.List;

public interface ScriptFunction {
    String getName();
    int getArgCount();
    Class<?>[] getArgTypes();
    /**
     * Executes the function.
     * @param args    The arguments.
     * @param source  The command source.
     * @param context Per-script mutable context (name color overrides etc.).
     * @return The result of the function, or null if void.
     */
    Object execute(List<Object> args, CommandSourceStack source, ScriptContext context);
}
