package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сила прыжка + множитель копания для локального игрока. */
public class PlayerExtrasSyncPacket {
    private final float jumpPower;
    private final double digSpeed;

    public PlayerExtrasSyncPacket(float jumpPower, double digSpeed) {
        this.jumpPower = jumpPower;
        this.digSpeed = digSpeed;
    }

    public static void encode(PlayerExtrasSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.jumpPower);
        buf.writeDouble(msg.digSpeed);
    }

    public static PlayerExtrasSyncPacket decode(FriendlyByteBuf buf) {
        return new PlayerExtrasSyncPacket(buf.readFloat(), buf.readDouble());
    }

    public static void handle(PlayerExtrasSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.ClientPlayerExtrasHolder.apply(msg.jumpPower, msg.digSpeed)));
        ctx.get().setPacketHandled(true);
    }
}
