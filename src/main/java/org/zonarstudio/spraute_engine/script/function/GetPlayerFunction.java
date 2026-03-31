package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class GetPlayerFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "getPlayer";
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
        if (args.isEmpty() || source.getLevel() == null) return null;
        String name = String.valueOf(args.get(0));
        ServerPlayer player = source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        return player;
    }
}
