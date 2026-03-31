package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class OverlayCloseFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "overlayClose";
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
        Player player = resolvePlayer(args.get(0), source);
        if (!(player instanceof ServerPlayer sp)) return null;

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new CloseSprauteOverlayPacket());
        return null;
    }

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }
}
