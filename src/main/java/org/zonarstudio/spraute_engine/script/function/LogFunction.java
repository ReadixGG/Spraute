package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.StringJoiner;

/**
 * log(message) — пишет строку в latest.log (уровень INFO).
 * log(arg1, arg2, ...) — склеивает аргументы через пробел.
 */
public class LogFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "log";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner(" ");
        for (Object arg : args) {
            joiner.add(String.valueOf(arg));
        }
        LOGGER.info("[Script] {}", joiner);
        return null;
    }
}
