package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Синхронизация maxUpStep на клиент (1.19.2). */
public class StepHeightSyncPacket {

    private final float stepHeight;

    public StepHeightSyncPacket(float stepHeight) {
        this.stepHeight = stepHeight;
    }

    public static void encode(StepHeightSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.stepHeight);
    }

    public static StepHeightSyncPacket decode(FriendlyByteBuf buf) {
        return new StepHeightSyncPacket(buf.readFloat());
    }

    public static void handle(StepHeightSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            org.zonarstudio.spraute_engine.client.ClientStepHeightHolder.set(msg.stepHeight);
        }));
        ctx.get().setPacketHandled(true);
    }
}
