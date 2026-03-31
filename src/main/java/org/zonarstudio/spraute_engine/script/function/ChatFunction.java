package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import java.util.List;

public class ChatFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{String.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        String message = (String) args.get(0);
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(message));
        } else {
            source.sendSuccess(Component.literal(message), false);
        }
        return null;
    }
}
