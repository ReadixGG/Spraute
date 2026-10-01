package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;

import java.util.function.Supplier;

/** Starts or stops a complete scene from the editor hierarchy. */
public final class SceneActionPacket {
    private final String sceneId;
    private final boolean start;

    public SceneActionPacket(String sceneId, boolean start) {
        this.sceneId = sceneId == null ? "" : sceneId;
        this.start = start;
    }

    public static void encode(SceneActionPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.sceneId);
        buffer.writeBoolean(message.start);
    }

    public static SceneActionPacket decode(FriendlyByteBuf buffer) {
        return new SceneActionPacket(buffer.readUtf(32767), buffer.readBoolean());
    }

    public static void handle(SceneActionPacket message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2) || message.sceneId.isBlank()) return;
            if (message.start) {
                SceneRuntime.load(message.sceneId, player.createCommandSourceStack());
            } else {
                SceneRuntime.unload(message.sceneId, player.createCommandSourceStack());
            }
        });
        context.setPacketHandled(true);
    }
}
