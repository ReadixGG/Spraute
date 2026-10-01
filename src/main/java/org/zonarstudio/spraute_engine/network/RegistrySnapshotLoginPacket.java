package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.registry.SyncedRegistrySnapshot;

import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Login-phase snapshot of {@code create item/block/particle}. Written on the client
 * during handshake. Does not wait for an ack ({@code noResponse}) — a reply-index
 * handshake here hung the connection ("Received unexpected index 0").
 */
public final class RegistrySnapshotLoginPacket implements IntSupplier {
    private static final int MAX_BYTES = 512 * 1024;

    private int loginIndex;
    private byte[] utf8;

    public RegistrySnapshotLoginPacket() {
        this.utf8 = new byte[0];
    }

    public RegistrySnapshotLoginPacket(byte[] utf8) {
        this.utf8 = utf8 == null ? new byte[0] : utf8;
    }

    public static RegistrySnapshotLoginPacket fromServerWorkspace() {
        SyncedRegistrySnapshot.write();
        String body = SyncedRegistrySnapshot.currentSpr();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            bytes = new byte[0];
        }
        return new RegistrySnapshotLoginPacket(bytes);
    }

    public int getLoginIndex() {
        return loginIndex;
    }

    public void setLoginIndex(int loginIndex) {
        this.loginIndex = loginIndex;
    }

    @Override
    public int getAsInt() {
        return loginIndex;
    }

    public static void encode(RegistrySnapshotLoginPacket message, FriendlyByteBuf buffer) {
        byte[] data = message.utf8 == null ? new byte[0] : message.utf8;
        buffer.writeVarInt(data.length);
        buffer.writeBytes(data);
    }

    public static RegistrySnapshotLoginPacket decode(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return new RegistrySnapshotLoginPacket();
        }
        int len = buffer.readVarInt();
        if (len < 0 || len > MAX_BYTES || buffer.readableBytes() < len) {
            return new RegistrySnapshotLoginPacket();
        }
        byte[] data = new byte[len];
        buffer.readBytes(data);
        return new RegistrySnapshotLoginPacket(data);
    }

    public static void handle(RegistrySnapshotLoginPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                org.zonarstudio.spraute_engine.client.assetsync.RegistryHandshakeClient.applySnapshot(msg.utf8));
        ctx.get().setPacketHandled(true);
    }
}
