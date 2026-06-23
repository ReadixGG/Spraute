package org.zonarstudio.spraute_engine.script.function;



import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.entity.player.Player;

import org.zonarstudio.spraute_engine.script.ScriptContext;



import java.util.List;



public class ChatFunction implements ScriptFunction {



    private static Player resolvePlayer(Object target, CommandSourceStack source) {

        if (target instanceof Player p) return p;

        if (target instanceof String name && source.getLevel() != null) {

            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);

        }

        return null;

    }



    @Override

    public String getName() {

        return "chat";

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



        String message;

        ServerPlayer target = null;



        if (args.size() >= 2) {

            Player resolved = resolvePlayer(args.get(0), source);

            if (resolved instanceof ServerPlayer sp) {

                target = sp;

            }

            message = String.valueOf(args.get(1));

        } else {

            message = String.valueOf(args.get(0));

            if (source.getEntity() instanceof ServerPlayer sp) {

                target = sp;

            }

        }



        Component component = Component.literal(message);



        if (target != null) {

            target.sendSystemMessage(component);

        } else if (source.getServer() != null) {

            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {

                player.sendSystemMessage(component);

            }

        } else {

            //? if >=1.20.1 {

            source.sendSuccess(() -> component, false);

            //?} else {

            /*source.sendSuccess(component, false);

            *///?}

        }

        return null;

    }

}


