package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.util.function.Supplier;

/** Sync / stop a scene POST_PROCESS stack on one client's camera. */
public final class SyncPostProcessPacket {
    public final boolean clearScene;
    public final boolean stop;
    public final String sceneId;
    public final String nodeId;
    public final PostProcessSettings settings;
    public final SceneTransform world;

    private SyncPostProcessPacket(boolean clearScene, boolean stop, String sceneId, String nodeId,
                                  PostProcessSettings settings, SceneTransform world) {
        this.clearScene = clearScene;
        this.stop = stop;
        this.sceneId = sceneId == null ? "" : sceneId;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.settings = settings == null ? new PostProcessSettings() : settings;
        this.world = world == null ? new SceneTransform() : world;
    }

    public static SyncPostProcessPacket upsert(String sceneId, String nodeId,
                                               PostProcessSettings settings, SceneTransform world) {
        return new SyncPostProcessPacket(false, false, sceneId, nodeId, settings, world);
    }

    public static SyncPostProcessPacket stop(String sceneId, String nodeId) {
        return new SyncPostProcessPacket(false, true, sceneId, nodeId, new PostProcessSettings(), new SceneTransform());
    }

    public static SyncPostProcessPacket clearScene(String sceneId) {
        return new SyncPostProcessPacket(true, false, sceneId, "", new PostProcessSettings(), new SceneTransform());
    }

    public static void encode(SyncPostProcessPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.clearScene);
        buf.writeUtf(msg.sceneId);
        if (msg.clearScene) return;
        buf.writeBoolean(msg.stop);
        buf.writeUtf(msg.nodeId);
        if (msg.stop) return;
        msg.settings.encode(buf);
        buf.writeFloat((float) msg.world.px);
        buf.writeFloat((float) msg.world.py);
        buf.writeFloat((float) msg.world.pz);
        buf.writeFloat((float) msg.world.rx);
        buf.writeFloat((float) msg.world.ry);
        buf.writeFloat((float) msg.world.rz);
        buf.writeFloat((float) msg.world.sx);
        buf.writeFloat((float) msg.world.sy);
        buf.writeFloat((float) msg.world.sz);
    }

    public static SyncPostProcessPacket decode(FriendlyByteBuf buf) {
        boolean clearScene = buf.readBoolean();
        String sceneId = buf.readUtf(32767);
        if (clearScene) return clearScene(sceneId);
        boolean stop = buf.readBoolean();
        String nodeId = buf.readUtf(32767);
        if (stop) return stop(sceneId, nodeId);
        PostProcessSettings settings = PostProcessSettings.decode(buf);
        SceneTransform world = new SceneTransform();
        world.px = buf.readFloat();
        world.py = buf.readFloat();
        world.pz = buf.readFloat();
        world.rx = buf.readFloat();
        world.ry = buf.readFloat();
        world.rz = buf.readFloat();
        world.sx = buf.readFloat();
        world.sy = buf.readFloat();
        world.sz = buf.readFloat();
        return upsert(sceneId, nodeId, settings, world);
    }

    public static void handle(SyncPostProcessPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.post.PostProcessClient.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
