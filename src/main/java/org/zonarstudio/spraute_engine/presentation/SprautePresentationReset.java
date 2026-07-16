package org.zonarstudio.spraute_engine.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.network.CloseSprauteOverlayPacket;
import org.zonarstudio.spraute_engine.network.CloseSprauteUiPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SprauteUiActionPacket;
import org.zonarstudio.spraute_engine.network.SyncLoadScreenPacket;
import org.zonarstudio.spraute_engine.script.ScriptManager;
import org.zonarstudio.spraute_engine.ui.SprauteContainerMenu;
import org.zonarstudio.spraute_engine.ui.UiTracker;

import java.util.HashMap;
import java.util.Map;

/** Server-side reset of scripted UI, overlays, fade screens and cinematic camera for one player. */
public final class SprautePresentationReset {

    private SprautePresentationReset() {}

    public static void resetForPlayer(ServerPlayer player) {
        if (player == null) return;

        if (player.containerMenu instanceof SprauteContainerMenu) {
            player.closeContainer();
        }

        UiTracker.markClosed(player.getUUID());

        ScriptManager mgr = ScriptManager.getInstance();
        if (mgr != null) {
            mgr.onUiAction(player, SprauteUiActionPacket.ACTION_CLOSE, "__reset__", -1);
            mgr.cancelUiWaitsForPlayer(player);
        }

        var target = PacketDistributor.PLAYER.with(() -> player);
        ModNetwork.CHANNEL.send(target, new CloseSprauteUiPacket());
        ModNetwork.CHANNEL.send(target, new CloseSprauteOverlayPacket());
        ModNetwork.CHANNEL.send(target, CameraPacket.reset());

        Map<String, Object> fadeOut = new HashMap<>();
        fadeOut.put("triggerFadeOut", true);
        ModNetwork.CHANNEL.send(target, new SyncLoadScreenPacket(fadeOut));

        player.displayClientMessage(
                Component.translatable("spraute_engine.reset_gui.done").withStyle(s -> s.withColor(0xFFFFFF)),
                true);
    }
}
