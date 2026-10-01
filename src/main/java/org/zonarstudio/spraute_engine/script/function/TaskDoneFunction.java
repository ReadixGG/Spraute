package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * taskDone("task_id") — true/false: проверка, завершена ли async-задача.
 * Чтобы подождать завершения, нужен {@code await task("task_id")}, не этот вызов.
 */
public class TaskDoneFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "taskDone";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class[]{String.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty()) return false;
        String taskId = String.valueOf(args.get(0));
        return context.isTaskDone(taskId);
    }
}
