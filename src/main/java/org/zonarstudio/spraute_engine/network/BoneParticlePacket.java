package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BoneParticlePacket {
    public final int action; // 0 = START, 1 = STOP
    public final String taskId;
    public final int entityId;
    public final String boneName;
    public final String particleType;
    public final int count;
    public final float dx, dy, dz, speed;

    public BoneParticlePacket(int action, String taskId, int entityId, String boneName, String particleType, int count, float dx, float dy, float dz, float speed) {
        this.action = action;
        this.taskId = taskId;
        this.entityId = entityId;
        this.boneName = boneName;
        this.particleType = particleType;
        this.count = count;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.speed = speed;
    }

    public static void encode(BoneParticlePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.action);
        buf.writeUtf(msg.taskId);
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.boneName);
        buf.writeUtf(msg.particleType);
        buf.writeInt(msg.count);
        buf.writeFloat(msg.dx);
        buf.writeFloat(msg.dy);
        buf.writeFloat(msg.dz);
        buf.writeFloat(msg.speed);
    }

    public static BoneParticlePacket decode(FriendlyByteBuf buf) {
        return new BoneParticlePacket(
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(BoneParticlePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.ClientParticleManager.handleBoneParticlePacket(msg)
                )
        );
        ctx.get().setPacketHandled(true);
    }
}
