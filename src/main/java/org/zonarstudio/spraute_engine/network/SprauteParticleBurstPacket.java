package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C burst for the autonomous Spraute particle engine. */
public final class SprauteParticleBurstPacket {
    public final String systemId;
    public final String specJson;
    /** Interleaved x,y,z,vx,vy,vz. */
    public final float[] data;

    public SprauteParticleBurstPacket(String systemId, String specJson, float[] data) {
        this.systemId = systemId == null ? "" : systemId;
        this.specJson = specJson == null ? "{}" : specJson;
        this.data = data == null ? new float[0] : data;
    }

    public static void encode(SprauteParticleBurstPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.systemId, 256);
        buf.writeUtf(msg.specJson, 262144);
        int count = Math.min(4096, msg.data.length / 6);
        buf.writeVarInt(count);
        for (int i = 0; i < count * 6; i++) buf.writeFloat(msg.data[i]);
    }

    public static SprauteParticleBurstPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(256);
        String json = buf.readUtf(262144);
        int count = Math.max(0, Math.min(4096, buf.readVarInt()));
        float[] data = new float[count * 6];
        for (int i = 0; i < data.length; i++) data[i] = buf.readFloat();
        return new SprauteParticleBurstPacket(id, json, data);
    }

    public static void handle(SprauteParticleBurstPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
