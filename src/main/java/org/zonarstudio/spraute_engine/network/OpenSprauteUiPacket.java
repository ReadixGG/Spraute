package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: open scripted UI (JSON layout).
 */
public class OpenSprauteUiPacket {
    private final String json;

    public OpenSprauteUiPacket(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    public static void encode(OpenSprauteUiPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, SprauteUiPackets.MAX_JSON_CHARS);
    }

    public static OpenSprauteUiPacket decode(FriendlyByteBuf buf) {
        return new OpenSprauteUiPacket(buf.readUtf(SprauteUiPackets.MAX_JSON_CHARS));
    }

    public static void handle(OpenSprauteUiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.open(msg.json)));
        ctx.get().setPacketHandled(true);
    }
}
