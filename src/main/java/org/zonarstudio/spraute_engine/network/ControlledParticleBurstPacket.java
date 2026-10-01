package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One emitter burst with per-particle position/velocity, gravity and optional lifetime.
 * Gravity/lifetime/collide/fade are applied on the client after spawn.
 */
public class ControlledParticleBurstPacket {
    public final String particleType;
    public final float gravity;
    /** Ticks; {@code 0} = keep particle-type default. */
    public final int lifetime;
    /** When true, particles collide with blocks ({@code Particle.hasPhysics}). */
    public final boolean collide;
    /** Ticks of alpha fade-out at end of life; {@code 0} = hard cut. */
    public final int fade;
    /** Interleaved x,y,z,vx,vy,vz. */
    public final float[] data;

    public ControlledParticleBurstPacket(String particleType, float gravity, int lifetime,
                                         boolean collide, int fade, float[] data) {
        this.particleType = particleType == null ? "" : particleType;
        this.gravity = gravity;
        this.lifetime = Math.max(0, lifetime);
        this.collide = collide;
        this.fade = Math.max(0, fade);
        this.data = data == null ? new float[0] : data;
    }

    /** @deprecated use constructor with fade */
    public ControlledParticleBurstPacket(String particleType, float gravity, int lifetime,
                                         boolean collide, float[] data) {
        this(particleType, gravity, lifetime, collide, 0, data);
    }

    /** @deprecated use constructor with collide/fade */
    public ControlledParticleBurstPacket(String particleType, float gravity, int lifetime, float[] data) {
        this(particleType, gravity, lifetime, false, 0, data);
    }

    /** @deprecated use constructor with lifetime/collide/fade */
    public ControlledParticleBurstPacket(String particleType, float gravity, float[] data) {
        this(particleType, gravity, 0, false, 0, data);
    }

    public static void encode(ControlledParticleBurstPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.particleType);
        buf.writeFloat(msg.gravity);
        buf.writeVarInt(msg.lifetime);
        buf.writeBoolean(msg.collide);
        buf.writeVarInt(msg.fade);
        int count = msg.data.length / 6;
        buf.writeVarInt(count);
        for (int i = 0; i < count * 6; i++) {
            buf.writeFloat(msg.data[i]);
        }
    }

    public static ControlledParticleBurstPacket decode(FriendlyByteBuf buf) {
        String type = buf.readUtf();
        float gravity = buf.readFloat();
        int lifetime = Math.max(0, buf.readVarInt());
        boolean collide = buf.readBoolean();
        int fade = Math.max(0, buf.readVarInt());
        int count = Math.max(0, Math.min(512, buf.readVarInt()));
        float[] data = new float[count * 6];
        for (int i = 0; i < data.length; i++) {
            data[i] = buf.readFloat();
        }
        return new ControlledParticleBurstPacket(type, gravity, lifetime, collide, fade, data);
    }

    public static void handle(ControlledParticleBurstPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.ClientParticleManager.handleControlledBurst(msg)
                )
        );
        ctx.get().setPacketHandled(true);
    }
}
