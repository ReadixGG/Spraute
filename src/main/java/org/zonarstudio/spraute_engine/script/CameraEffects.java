package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.CameraEffectsPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;

/** Server API for additive camera motion (shake / punch). */
public final class CameraEffects {
    private CameraEffects() {}

    public static void shake(ServerPlayer player, float intensity, float duration, float frequency) {
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                CameraEffectsPacket.shake(intensity, duration, frequency));
    }

    public static void punch(ServerPlayer player, float yaw, float pitch, float roll) {
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                CameraEffectsPacket.punch(yaw, pitch, roll));
    }

    public static void stop(ServerPlayer player) {
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                CameraEffectsPacket.stop());
    }
}
