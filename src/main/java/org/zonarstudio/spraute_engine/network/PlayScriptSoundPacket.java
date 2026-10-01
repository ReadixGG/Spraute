package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client play / stop of a script {@code playSound} (supports looping). */
public final class PlayScriptSoundPacket {
    public final String soundId;
    public final float volume;
    public final float pitch;
    public final boolean loop;
    /** When true: stop {@link #soundId} loop, or all loops if soundId is blank. */
    public final boolean stop;

    public PlayScriptSoundPacket(String soundId, float volume, float pitch, boolean loop, boolean stop) {
        this.soundId = soundId == null ? "" : soundId;
        this.volume = volume;
        this.pitch = pitch;
        this.loop = loop;
        this.stop = stop;
    }

    public static PlayScriptSoundPacket play(String soundId, float volume, float pitch, boolean loop) {
        return new PlayScriptSoundPacket(soundId, volume, pitch, loop, false);
    }

    public static PlayScriptSoundPacket stopOne(String soundId) {
        return new PlayScriptSoundPacket(soundId, 0f, 1f, false, true);
    }

    public static PlayScriptSoundPacket stopAll() {
        return new PlayScriptSoundPacket("", 0f, 1f, false, true);
    }

    public static void encode(PlayScriptSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.soundId);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
        buf.writeBoolean(msg.loop);
        buf.writeBoolean(msg.stop);
    }

    public static PlayScriptSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayScriptSoundPacket(
                buf.readUtf(32767), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PlayScriptSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.audio.ClientScriptSoundManager.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
