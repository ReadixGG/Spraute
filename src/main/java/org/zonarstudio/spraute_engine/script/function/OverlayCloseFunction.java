package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.UiTemplate;

import java.util.List;

public class OverlayCloseFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "overlayClose";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty() || source.getLevel() == null) return null;
        Player player = resolvePlayer(args.get(0), source);
        if (!(player instanceof ServerPlayer sp)) return null;

        String overlayId = "";
        if (args.size() >= 2) {
            Object idArg = args.get(1);
            if (idArg instanceof UiTemplate ut) {
                overlayId = extractId(ut.getJson());
            } else if (idArg != null) {
                overlayId = String.valueOf(idArg);
            }
        }

        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new CloseSprauteOverlayPacket(overlayId));
        org.zonarstudio.spraute_engine.ui.UiTracker.markClosed(sp.getUUID());
        return null;
    }

    private static String extractId(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (root.has("id")) return root.get("id").getAsString();
        } catch (Exception ignored) {}
        return "";
    }

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        return ItemStackScriptUtil.resolvePlayer(target, source);
    }
}
