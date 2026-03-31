package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: force-close scripted UI if open. */
public class CloseSprauteUiPacket {

    public CloseSprauteUiPacket() {}

    public static void encode(CloseSprauteUiPacket msg, FriendlyByteBuf buf) {}

    public static CloseSprauteUiPacket decode(FriendlyByteBuf buf) {
        return new CloseSprauteUiPacket();
    }

    public static void handle(CloseSprauteUiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.closeIfActive()));
        ctx.get().setPacketHandled(true);
    }
}
