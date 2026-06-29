package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * stopCamera(player) — плавный возврат за 0.5 сек.
 * stopCamera(player, smoothTime) — плавный возврат; {@code 0} = мгновенно.
 */
public class StopCameraFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "stopCamera";
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
        if (player == null) return null;

        float smooth = 0.5f;
        if (args.size() > 1 && args.get(1) instanceof Number n) {
            smooth = n.floatValue();
        }
        CameraScriptUtil.sendStop(player, smooth);
        return (double) smooth; // allow await time(stopCamera(...))
    }
}
