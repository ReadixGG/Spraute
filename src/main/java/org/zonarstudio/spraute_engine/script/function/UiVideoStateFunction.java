package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.UiTracker;

import java.util.List;

/** Query current UI video time / duration / playing / ended for a player. */
public final class UiVideoStateFunction implements ScriptFunction {
    public enum Kind { TIME, DURATION, PLAYING, ENDED }

    private final String name;
    private final Kind kind;

    public UiVideoStateFunction(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getArgCount() {
        return 2;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 2) return kind == Kind.TIME || kind == Kind.DURATION ? 0 : false;
        Player player = UiFunctions.resolvePlayer(args.get(0), source);
        if (player == null) return kind == Kind.TIME || kind == Kind.DURATION ? 0 : false;
        UiTracker.VideoState state = UiTracker.getVideo(player.getUUID(), UiUpdateFunction.widgetIdOf(args.get(1)));
        return switch (kind) {
            case TIME -> state.time();
            case DURATION -> state.duration();
            case PLAYING -> state.playing();
            case ENDED -> state.ended();
        };
    }
}
