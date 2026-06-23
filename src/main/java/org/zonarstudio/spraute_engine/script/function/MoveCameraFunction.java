package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class MoveCameraFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "moveCamera";
    }

    @Override
    public int getArgCount() {
        return 7;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, Number.class, Number.class, Number.class, Number.class, Number.class, Number.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 6) return null;

        ServerPlayer player = null;
        Object arg0 = args.get(0);
        if (arg0 instanceof ServerPlayer sp) {
            player = sp;
        } else if (arg0 instanceof String name && source.getLevel() != null) {
            player = source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        if (player == null) {
            player = (source.getEntity() instanceof ServerPlayer sp) ? sp : null;
        }
        if (player == null) return null;

        double x = ((Number) args.get(1)).doubleValue();
        double y = ((Number) args.get(2)).doubleValue();
        double z = ((Number) args.get(3)).doubleValue();
        float yaw = ((Number) args.get(4)).floatValue();
        float pitch = ((Number) args.get(5)).floatValue();
        float smoothTime = args.size() > 6 ? ((Number) args.get(6)).floatValue() : 0.5f;

        final ServerPlayer target = player;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> target),
                CameraPacket.move(x, y, z, yaw, pitch, smoothTime)
        );
        return null;
    }
}
