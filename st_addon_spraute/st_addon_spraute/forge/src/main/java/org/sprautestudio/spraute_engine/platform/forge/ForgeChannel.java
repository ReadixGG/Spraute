package org.sprautestudio.spraute_engine.platform.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sprautestudio.spraute_engine.platform.PacketChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class ForgeChannel implements PacketChannel {
    private final SimpleChannel channel;
    private int nextId;

    ForgeChannel(ResourceLocation id, String protocol) {
        channel = NetworkRegistry.newSimpleChannel(id, () -> protocol,
                v -> v.equals(protocol) || v.equals(NetworkRegistry.ABSENT.version()) || v.equals(NetworkRegistry.ACCEPTVANILLA),
                v -> v.equals(protocol) || v.equals(NetworkRegistry.ABSENT.version()) || v.equals(NetworkRegistry.ACCEPTVANILLA));
    }

    @Override
    public <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read,
                             BiConsumer<T, Context> handler, Direction direction) {
        channel.registerMessage(nextId++, type, write, read, (msg, ctx) -> {
            NetworkEvent.Context c = ctx.get();
            handler.accept(msg, new Context() {
                @Override
                public ServerPlayer sender() {
                    return c.getSender();
                }

                @Override
                public void enqueue(Runnable work) {
                    c.enqueueWork(work);
                }
            });
            c.setPacketHandled(true);
        }, direction == Direction.BOTH ? Optional.empty() : Optional.of(direction == Direction.TO_SERVER ? NetworkDirection.PLAY_TO_SERVER : NetworkDirection.PLAY_TO_CLIENT));
    }

    @Override
    public void sendToServer(Object message) {
        channel.sendToServer(message);
    }

    @Override
    public void sendTo(ServerPlayer player, Object message) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    @Override
    public void sendToEveryone(Object message) {
        channel.send(PacketDistributor.ALL.noArg(), message);
    }
}
