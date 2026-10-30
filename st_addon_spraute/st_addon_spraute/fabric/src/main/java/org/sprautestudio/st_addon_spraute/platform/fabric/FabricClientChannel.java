package org.sprautestudio.st_addon_spraute.platform.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.sprautestudio.st_addon_spraute.platform.PacketChannel;

/** The client's side of {@link FabricChannel}: a class of its own, so that a dedicated server never loads it. */
final class FabricClientChannel {
    private FabricClientChannel() {
    }

    static void listen(FabricChannel channel) {
        ClientPlayNetworking.registerGlobalReceiver(channel.id, (client, handler, buf, sender) -> channel.received(buf, new PacketChannel.Context() {
            @Override
            public ServerPlayer sender() {
                return null;
            }

            @Override
            public void enqueue(Runnable work) {
                client.execute(work);
            }
        }));
    }

    static void send(FabricChannel channel, Object message) {
        ClientPlayNetworking.send(channel.id, channel.encode(message));
    }
}
