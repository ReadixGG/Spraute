package org.sprautestudio.spraute_engine.platform.fabric;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.sprautestudio.spraute_engine.platform.PacketChannel;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** All the engine's packets in one Fabric channel: each starts with its number. */
final class FabricChannel implements PacketChannel {
    private record Entry<T>(int id, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read,
                            BiConsumer<T, Context> handler, Direction direction) {
        void handle(FriendlyByteBuf buf, Context context) {
            handler.accept(read.apply(buf), context);
        }
    }

    final ResourceLocation id;
    private final List<Entry<?>> byId = new ArrayList<>();
    private final Map<Class<?>, Entry<?>> byClass = new HashMap<>();

    FabricChannel(ResourceLocation id) {
        this.id = id;
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, sender) -> {
            Entry<?> e = entry(buf);
            if (e == null || e.direction() == Direction.TO_CLIENT) return;
            e.handle(buf, new Context() {
                @Override
                public ServerPlayer sender() {
                    return player;
                }

                @Override
                public void enqueue(Runnable work) {
                    server.execute(work);
                }
            });
        });
        if (Platform.get().isClient()) FabricClientChannel.listen(this);
    }

    private Entry<?> entry(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        return n >= 0 && n < byId.size() ? byId.get(n) : null;
    }

    /** A packet from the server, on a client. */
    void received(FriendlyByteBuf buf, Context context) {
        Entry<?> e = entry(buf);
        if (e != null && e.direction() != Direction.TO_SERVER) e.handle(buf, context);
    }

    @Override
    public <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read,
                             BiConsumer<T, Context> handler, Direction direction) {
        Entry<T> e = new Entry<>(byId.size(), write, read, handler, direction);
        byId.add(e);
        byClass.put(type, e);
    }

    @SuppressWarnings("unchecked")
    FriendlyByteBuf encode(Object message) {
        Entry<Object> e = (Entry<Object>) byClass.get(message.getClass());
        if (e == null) throw new IllegalArgumentException("Not a packet of the engine: " + message.getClass());
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(e.id());
        e.write().accept(message, buf);
        return buf;
    }

    @Override
    public void sendToServer(Object message) {
        FabricClientChannel.send(this, message);
    }

    @Override
    public void sendTo(ServerPlayer player, Object message) {
        ServerPlayNetworking.send(player, id, encode(message));
    }

    @Override
    public void sendToEveryone(Object message) {
        MinecraftServer server = Platform.get().server();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendTo(p, message);
    }
}
