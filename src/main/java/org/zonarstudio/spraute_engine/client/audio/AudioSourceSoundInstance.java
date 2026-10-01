package org.zonarstudio.spraute_engine.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.network.SyncAudioSourcePacket;

/**
 * Looping (or one-shot) ambient sound whose volume follows AUDIO_SOURCE falloff
 * relative to the listening player each tick.
 */
public final class AudioSourceSoundInstance extends AbstractTickableSoundInstance {
    private final String soundKey;
    private SyncAudioSourcePacket data;
    private boolean forceStopped;

    AudioSourceSoundInstance(SoundEvent event, SoundSource source, SyncAudioSourcePacket data) {
        super(event, source, net.minecraft.util.RandomSource.create());
        this.soundKey = data.soundId;
        this.data = data;
        this.looping = data.loop;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = Math.max(0.01f, data.pitch);
        this.x = data.x;
        this.y = data.y;
        this.z = data.z;
        this.relative = data.personal;
        // Custom falloff — disable vanilla attenuation.
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    boolean matchesSound(String soundId) {
        return soundKey != null && soundKey.equals(soundId);
    }

    void applyPacket(SyncAudioSourcePacket msg) {
        this.data = msg;
        this.looping = msg.loop;
        this.pitch = Math.max(0.01f, msg.pitch);
        this.x = msg.x;
        this.y = msg.y;
        this.z = msg.z;
        this.relative = msg.personal;
    }

    void forceStop() {
        forceStopped = true;
        stop();
    }

    boolean finished() {
        return forceStopped;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents ev = ClientWorkspaceSounds.events(manager, getLocation());
        if (ev != null) this.sound = ev.getSound(this.random);
        return ev;
    }

    @Override
    public java.util.concurrent.CompletableFuture<AudioStream> getStream(
            SoundBufferLibrary library, Sound sound, boolean looping) {
        return ClientWorkspaceSounds.stream(getLocation(), looping, library, sound);
    }

    @Override
    public void tick() {
        if (forceStopped) {
            stop();
            return;
        }
        if (data == null) {
            this.volume = 0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            this.volume = 0f;
            return;
        }
        if (data.personal) {
            this.volume = Math.max(0f, data.volume);
            this.relative = true;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.pitch = Math.max(0.01f, data.pitch);
            return;
        }
        float vol = ClientAudioSourceManager.volumeFor(
                data, player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        this.volume = Math.max(0f, vol);
        this.x = data.x;
        this.y = data.y;
        this.z = data.z;
        this.pitch = Math.max(0.01f, data.pitch);
    }
}
