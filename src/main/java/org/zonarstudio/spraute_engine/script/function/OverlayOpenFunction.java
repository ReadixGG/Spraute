package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.OpenSprauteOverlayPacket;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.SprauteUiJson;
import org.zonarstudio.spraute_engine.ui.UiTemplate;

import java.util.List;

public class OverlayOpenFunction implements ScriptFunction {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    @Override
    public String getName() {
        return "overlayOpen";
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
        if (args.size() < 2 || source.getLevel() == null) return null;
        Player player = resolvePlayer(args.get(0), source);
        if (!(player instanceof ServerPlayer sp)) return null;

        String json;
        if (args.get(1) instanceof UiTemplate ut) {
            json = ut.getJson();
        } else {
            json = String.valueOf(args.get(1));
        }

        try {
            String prepared = SprauteUiJson.prepareAndSerialize(source.getLevel(), source, json);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenSprauteOverlayPacket(prepared));
        } catch (Exception e) {
            LOGGER.warn("[Script] overlay_open failed: {}", e.getMessage());
        }

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
