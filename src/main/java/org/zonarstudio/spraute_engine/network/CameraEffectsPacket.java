package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.CameraEffectsClient;

import java.util.function.Supplier;

/**
 * One-shot camera motion (Cinemachine Impulse / Unreal CameraShake) — not post-process.
 */
public final class CameraEffectsPacket {
    public static final byte ACTION_SHAKE = 0;
    public static final byte ACTION_PUNCH = 1;
    public static final byte ACTION_STOP = 2;

    public final byte action;
    public final float a;
    public final float b;
    public final float c;
    public final float d;

    public CameraEffectsPacket(byte action, float a, float b, float c, float d) {
        this.action = action;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public static CameraEffectsPacket shake(float intensity, float duration, float frequency) {
        return new CameraEffectsPacket(ACTION_SHAKE, intensity, duration, frequency, 0f);
    }

    public static CameraEffectsPacket punch(float yaw, float pitch, float roll) {
        return new CameraEffectsPacket(ACTION_PUNCH, yaw, pitch, roll, 0f);
    }

    public static CameraEffectsPacket stop() {
        return new CameraEffectsPacket(ACTION_STOP, 0f, 0f, 0f, 0f);
    }

    public static void encode(CameraEffectsPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeFloat(msg.a);
        buf.writeFloat(msg.b);
        buf.writeFloat(msg.c);
        buf.writeFloat(msg.d);
    }

    public static CameraEffectsPacket decode(FriendlyByteBuf buf) {
        return new CameraEffectsPacket(buf.readByte(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat());
    }

    public static void handle(CameraEffectsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (msg.action) {
                case ACTION_SHAKE -> CameraEffectsClient.shake(msg.a, msg.b, msg.c);
                case ACTION_PUNCH -> CameraEffectsClient.punch(msg.a, msg.b, msg.c);
                case ACTION_STOP -> CameraEffectsClient.stopTransient();
                default -> {}
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
