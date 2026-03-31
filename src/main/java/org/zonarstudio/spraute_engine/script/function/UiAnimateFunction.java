package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.UpdateSprauteUiWidgetPacket;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class UiAnimateFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "uiAnimate";
    }

    @Override
    public int getArgCount() {
        return 5;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, String.class, String.class, String.class, Number.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 5 || source.getLevel() == null) return null;
        Player player = resolvePlayer(args.get(0), source);
        if (!(player instanceof ServerPlayer sp)) {
            LOGGER.warn("[Script] ui_animate: not a server player");
            return null;
        }
        String widgetId = args.get(1) == null ? "" : String.valueOf(args.get(1));
        String field = args.get(2) == null ? "" : String.valueOf(args.get(2));
        String targetValue = args.get(3) == null ? "" : String.valueOf(args.get(3));
        float duration = ((Number) args.get(4)).floatValue();
        String easing = args.size() > 5 ? String.valueOf(args.get(5)) : "linear";

        if (widgetId.isEmpty()) return null;
        
        // Pass duration, easing, and target via a special prefix
        String packetValue = "~ANIM:" + duration + ":" + easing + ":" + targetValue;
        
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new UpdateSprauteUiWidgetPacket(widgetId, field, packetValue));
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
