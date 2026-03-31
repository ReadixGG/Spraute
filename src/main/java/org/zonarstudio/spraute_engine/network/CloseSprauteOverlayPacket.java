package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: force-close scripted overlay if open. */
public class CloseSprauteOverlayPacket {

    public CloseSprauteOverlayPacket() {}

    public static void encode(CloseSprauteOverlayPacket msg, FriendlyByteBuf buf) {}

    public static CloseSprauteOverlayPacket decode(FriendlyByteBuf buf) {
        return new CloseSprauteOverlayPacket();
    }

    public static void handle(CloseSprauteOverlayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.closeOverlayIfActive()));
        ctx.get().setPacketHandled(true);
    }
}
