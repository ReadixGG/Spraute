package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: enable editor mode after the server OP check; the player opens it with N. */
public final class OpenEditorPacket {
    public OpenEditorPacket() {}

    public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {}

    public static OpenEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenEditorPacket();
    }

    public static void handle(OpenEditorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.enable()));
        ctx.get().setPacketHandled(true);
    }
}
