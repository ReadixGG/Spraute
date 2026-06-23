package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SyncLoadScreenPacket;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FadeOutFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "fadeOut";
    }

    @Override
    public int getArgCount() {
        return 0; // fadeOut()
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        ServerPlayer player = (source.getEntity() instanceof ServerPlayer sp) ? sp : null;
        if (player != null) {
            Map<String, Object> props = new HashMap<>();
            props.put("triggerFadeOut", true);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncLoadScreenPacket(props)
            );
        }
        return null;
    }
}