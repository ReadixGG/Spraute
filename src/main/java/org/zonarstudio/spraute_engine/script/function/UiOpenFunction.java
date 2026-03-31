package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.OpenSprauteUiPacket;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.SprauteUiJson;
import org.zonarstudio.spraute_engine.ui.UiTemplate;

import java.util.List;

/**
 * ui_open(player, template) — open a UI from a create ui template.
 * ui_open(player, jsonString) — open from raw JSON string (legacy).
 */
public class UiOpenFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "uiOpen";
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
        if (!(player instanceof ServerPlayer sp)) {
            LOGGER.warn("[Script] ui_open: not a server player");
            return null;
        }
        String json;
        if (args.get(1) instanceof UiTemplate ut) {
            json = ut.getJson();
        } else {
            json = String.valueOf(args.get(1));
        }
        try {
            String prepared = SprauteUiJson.prepareAndSerialize(source.getLevel(), source, json);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenSprauteUiPacket(prepared));
            if (args.get(1) instanceof UiTemplate ut) {
                context.notifyUiOpened(player, ut);
            }
        } catch (Exception e) {
            LOGGER.warn("[Script] ui_open failed: {}", e.getMessage());
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
