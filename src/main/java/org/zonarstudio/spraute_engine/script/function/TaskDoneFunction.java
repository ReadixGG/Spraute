package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * task_done("task_id") — true если задача завершена или прервана.
 */
public class TaskDoneFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "task_done";
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
