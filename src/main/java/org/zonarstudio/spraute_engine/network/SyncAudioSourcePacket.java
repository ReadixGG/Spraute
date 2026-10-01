package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sync / stop a scene AUDIO_SOURCE on the client. */
public final class SyncAudioSourcePacket {
    public final boolean stop;
    public final String sceneId;
    public final String nodeId;
    public final String soundId;
    public final String shape;
    public final float fullRadius;
    public final float maxDistance;
    public final float volume;
    public final float pitch;
    public final boolean loop;
    public final boolean replayOnEnter;
    /** Bumps when script calls play()/startPlay() so the client force-restarts. */
    public final int playGeneration;
    public final float x, y, z;
    public final float sx, sy, sz;
    /** Under a PLAYER: 2D, owner-only. World sources stay positional. */
    public final boolean personal;

    private SyncAudioSourcePacket(boolean stop, String sceneId, String nodeId, String soundId,
                                  String shape, float fullRadius, float maxDistance,
                                  float volume, float pitch, boolean loop, boolean replayOnEnter,
                                  int playGeneration,
                                  float x, float y, float z, float sx, float sy, float sz,
                                  boolean personal) {
        this.stop = stop;
        this.sceneId = sceneId == null ? "" : sceneId;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.soundId = soundId == null ? "" : soundId;
        this.shape = shape == null ? "sphere" : shape;
        this.fullRadius = fullRadius;
        this.maxDistance = maxDistance;
        this.volume = volume;
        this.pitch = pitch;
        this.loop = loop;
        this.replayOnEnter = replayOnEnter;
        this.playGeneration = playGeneration;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.personal = personal;
    }

    public static SyncAudioSourcePacket upsert(String sceneId, String nodeId, String soundId,
                                               String shape, float fullRadius, float maxDistance,
                                               float volume, float pitch, boolean loop,
                                               boolean replayOnEnter, int playGeneration,
                                               float x, float y, float z, float sx, float sy, float sz,
                                               boolean personal) {
        return new SyncAudioSourcePacket(false, sceneId, nodeId, soundId, shape,
                fullRadius, maxDistance, volume, pitch, loop, replayOnEnter, playGeneration,
                x, y, z, sx, sy, sz, personal);
    }

    public static SyncAudioSourcePacket upsert(String sceneId, String nodeId, String soundId,
                                               String shape, float fullRadius, float maxDistance,
                                               float volume, float pitch, boolean loop,
                                               boolean replayOnEnter, int playGeneration,
                                               float x, float y, float z, float sx, float sy, float sz) {
        return upsert(sceneId, nodeId, soundId, shape, fullRadius, maxDistance, volume, pitch, loop,
                replayOnEnter, playGeneration, x, y, z, sx, sy, sz, false);
    }

    public static SyncAudioSourcePacket stop(String sceneId, String nodeId) {
        return new SyncAudioSourcePacket(true, sceneId, nodeId, "", "sphere",
                0f, 0f, 0f, 1f, false, false, 0, 0f, 0f, 0f, 1f, 1f, 1f, false);
    }

    public static void encode(SyncAudioSourcePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.stop);
        buf.writeUtf(msg.sceneId);
        buf.writeUtf(msg.nodeId);
        if (msg.stop) return;
        buf.writeUtf(msg.soundId);
        buf.writeUtf(msg.shape);
        buf.writeFloat(msg.fullRadius);
        buf.writeFloat(msg.maxDistance);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
        buf.writeBoolean(msg.loop);
        buf.writeBoolean(msg.replayOnEnter);
        buf.writeVarInt(msg.playGeneration);
        buf.writeFloat(msg.x);
        buf.writeFloat(msg.y);
        buf.writeFloat(msg.z);
        buf.writeFloat(msg.sx);
        buf.writeFloat(msg.sy);
        buf.writeFloat(msg.sz);
        buf.writeBoolean(msg.personal);
    }

    public static SyncAudioSourcePacket decode(FriendlyByteBuf buf) {
        boolean stop = buf.readBoolean();
        String sceneId = buf.readUtf(32767);
        String nodeId = buf.readUtf(32767);
        if (stop) return stop(sceneId, nodeId);
        return upsert(sceneId, nodeId, buf.readUtf(32767), buf.readUtf(64),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean());
    }

    public static void handle(SyncAudioSourcePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.audio.ClientAudioSourceManager.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
