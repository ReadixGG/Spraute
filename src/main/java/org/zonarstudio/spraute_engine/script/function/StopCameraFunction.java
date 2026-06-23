package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class StopCameraFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "stopCamera";
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
        ServerPlayer player = null;

        if (!args.isEmpty()) {
            Object arg = args.get(0);
            if (arg instanceof ServerPlayer sp) {
                player = sp;
            } else if (arg instanceof String name && source.getLevel() != null) {
                player = source.getLevel().getServer().getPlayerList().getPlayerByName(name);
            }
        }

        if (player == null) {
            player = (source.getEntity() instanceof ServerPlayer sp) ? sp : null;
        }

        if (player != null) {
            final ServerPlayer target = player;
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> target),
                    CameraPacket.stop()
            );
        }
        return null;
    }
}
