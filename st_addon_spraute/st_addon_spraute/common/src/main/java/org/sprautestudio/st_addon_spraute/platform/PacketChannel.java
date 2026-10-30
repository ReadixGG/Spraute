package org.sprautestudio.st_addon_spraute.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** The engine's own packets. Both sides must register the same packets in the same order. */
public interface PacketChannel {
    enum Direction {TO_SERVER, TO_CLIENT, BOTH}

    /** Who sent a packet, and the way onto the game's thread: a packet is handled on the network's. */
    interface Context {
        /** Null for a packet to the client. */
        ServerPlayer sender();

        void enqueue(Runnable work);
    }

    <T> void register(Class<T> type, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read,
                      BiConsumer<T, Context> handler, Direction direction);

    void sendToServer(Object message);

    void sendTo(ServerPlayer player, Object message);

    void sendToEveryone(Object message);
}
