package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.CloseSprauteUiPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** ui_close(player) — закрыть скриптовый UI у игрока. */
public class UiCloseFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "uiClose";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (source.getLevel() == null) return null;
        Player player = null;
        if (!args.isEmpty()) {
            player = resolvePlayer(args.get(0), source);
        } else if (source.getEntity() instanceof Player p) {
            player = p;
        }
        
        if (!(player instanceof ServerPlayer sp)) {
            LOGGER.warn("[Script] ui_close: not a server player");
            return null;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new CloseSprauteUiPacket());
        context.notifyUiClosed(player);
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
