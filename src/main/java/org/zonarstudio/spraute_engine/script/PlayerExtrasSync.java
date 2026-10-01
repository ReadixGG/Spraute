package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerExtrasSyncPacket;

/** Клиентская копия силы прыжка и множителя копания (инспектор + предикт прыжка). */
public final class PlayerExtrasSync {
    private PlayerExtrasSync() {}

    public static void send(ServerPlayer player) {
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerExtrasSyncPacket(
                        PlayerJumpStrengthOverrides.get(player),
                        PlayerDigSpeedOverrides.get(player)));
    }
}
