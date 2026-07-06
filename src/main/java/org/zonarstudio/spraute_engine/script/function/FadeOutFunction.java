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
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        ServerPlayer player = resolvePlayer(args, source);
        if (player == null) return null;

        Map<String, Object> props = new HashMap<>();
        props.put("triggerFadeOut", true);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncLoadScreenPacket(props)
        );
        return null;
    }

    private static ServerPlayer resolvePlayer(List<Object> args, CommandSourceStack source) {
        if (!args.isEmpty()) {
            Object first = args.get(0);
            if (first instanceof ServerPlayer sp) return sp;
            if (first instanceof net.minecraft.world.entity.player.Player p && p instanceof ServerPlayer sp) return sp;
            if (first instanceof String name && source.getLevel() != null) {
                ServerPlayer byName = source.getLevel().getServer().getPlayerList().getPlayerByName(name);
                if (byName != null) return byName;
            }
        }
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        return null;
    }
}