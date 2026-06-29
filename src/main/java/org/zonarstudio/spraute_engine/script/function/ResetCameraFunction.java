package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** Мгновенно отключает кинематографическую камеру и возвращает управление игроку. */
public class ResetCameraFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "resetCamera";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        var player = !args.isEmpty()
                ? CameraScriptUtil.resolvePlayer(args.get(0), source)
                : CameraScriptUtil.resolvePlayer(null, source);
        if (player != null) {
            CameraScriptUtil.sendReset(player);
        }
        return null;
    }
}
