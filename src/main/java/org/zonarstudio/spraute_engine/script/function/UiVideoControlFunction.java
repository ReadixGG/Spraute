package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.UiTracker;

import java.util.List;

/** play/pause/stop/restart/enable controls for a GUI video widget. */
public final class UiVideoControlFunction implements ScriptFunction {
    private final String name;
    private final String action;
    private final boolean enableFunction;

    public UiVideoControlFunction(String name, String action, boolean enableFunction) {
        this.name = name;
        this.action = action;
        this.enableFunction = enableFunction;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getArgCount() {
        return enableFunction ? 3 : 2;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return enableFunction
                ? new Class<?>[]{Object.class, Object.class, Object.class}
                : new Class<?>[]{Object.class, Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < getArgCount()) return null;
        String widgetId = UiUpdateFunction.widgetIdOf(args.get(1));
        Player player = UiFunctions.resolvePlayer(args.get(0), source);
        if (player != null && !widgetId.isEmpty()) {
            applyOptimisticState(player, widgetId);
        }
        String field = enableFunction ? "playerEnabled" : "action";
        Object value = enableFunction ? args.get(2) : action;
        return new UiUpdateFunction().execute(
                java.util.Arrays.asList(args.get(0), args.get(1), field, value), source, context);
    }

    private void applyOptimisticState(Player player, String widgetId) {
        UiTracker.VideoState prev = UiTracker.getVideo(player.getUUID(), widgetId);
        if (enableFunction) return;
        switch (action) {
            case "play", "resume" -> UiTracker.setVideo(
                    player.getUUID(), widgetId, prev.time(), prev.duration(), true, false);
            case "pause" -> UiTracker.setVideo(
                    player.getUUID(), widgetId, prev.time(), prev.duration(), false, prev.ended());
            case "stop" -> UiTracker.setVideo(
                    player.getUUID(), widgetId, 0, prev.duration(), false, false);
            case "restart", "replay" -> UiTracker.setVideo(
                    player.getUUID(), widgetId, 0, prev.duration(), true, false);
            default -> { }
        }
    }
}
