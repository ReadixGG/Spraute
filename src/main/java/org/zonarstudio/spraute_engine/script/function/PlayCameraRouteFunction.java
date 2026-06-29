package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * playCameraRoute(player, routeName, [lockMovement, hideGui, returnSmooth, totalSeconds, afterEnd, lookMode, lookX, lookY, lookZ])
 * totalSeconds: 0 = from route file
 */
public class PlayCameraRouteFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "playCameraRoute";
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
        if (args.size() < 2) return 0f;
        ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
        if (player == null) return 0f;

        String routeName = String.valueOf(args.get(1));
        CameraRouteScriptUtil.PlayOptions options = CameraRouteScriptUtil.parsePlayOptions(args, 2);

        try {
            return CameraRouteScriptUtil.playRoute(player, routeName, options);
        } catch (Exception e) {
            LOGGER.warn("[Spraute] playCameraRoute failed for {}: {}", routeName, e.getMessage());
            return 0f;
        }
    }
}
