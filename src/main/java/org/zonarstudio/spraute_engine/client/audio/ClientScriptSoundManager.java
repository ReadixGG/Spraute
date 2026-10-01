package org.zonarstudio.spraute_engine.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.PlayScriptSoundPacket;
import org.zonarstudio.spraute_engine.network.PlayerAudioSettingsPacket;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client personal audio bus: script / {@code player.audio} play (one-shot + loop) on MASTER,
 * with master volume / pitch / mute from the player's AUDIO node.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class ClientScriptSoundManager {
    private static final Map<String, LoopingScriptSound> LOOPING = new ConcurrentHashMap<>();

    private static float masterVolume = 1f;
    private static float masterPitch = 1f;
    private static boolean masterEnabled = true;

    private ClientScriptSoundManager() {}

    public static void applySettings(PlayerAudioSettingsPacket msg) {
        if (msg == null) return;
        masterVolume = Math.max(0f, msg.volume);
        masterPitch = Math.max(0.01f, msg.pitch);
        masterEnabled = msg.enabled;
        for (LoopingScriptSound inst : LOOPING.values()) {
            inst.refreshFromMaster();
        }
    }

    public static void handle(PlayScriptSoundPacket msg) {
        if (msg == null) return;
        if (msg.stop) {
            if (msg.soundId == null || msg.soundId.isBlank()) stopAllLoops();
            else stopLoop(msg.soundId);
            return;
        }
        if (msg.soundId == null || msg.soundId.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        ResourceLocation rl = ClientWorkspaceSounds.location(msg.soundId);
        if (rl == null) return;
        ClientWorkspaceSounds.prepare(msg.soundId);
        SoundEvent event = ClientWorkspaceSounds.event(rl);

        float relativeVolume = Math.max(0f, msg.volume);
        float relativePitch = Math.max(0.01f, msg.pitch);
        if (!msg.loop) {
            if (!masterEnabled || effectiveVolume(relativeVolume) <= 0f) return;
            if (ClientWorkspaceSounds.isWorkspaceFile(msg.soundId)) {
                mc.getSoundManager().play(new WorkspaceUiSound(
                        event, effectivePitch(relativePitch), effectiveVolume(relativeVolume)));
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        event, effectivePitch(relativePitch), effectiveVolume(relativeVolume)));
            }
            return;
        }

        stopLoop(msg.soundId);
        LoopingScriptSound inst = new LoopingScriptSound(event, relativeVolume, relativePitch);
        LOOPING.put(msg.soundId, inst);
        mc.getSoundManager().play(inst);
    }

    private static float effectiveVolume(float relative) {
        return masterEnabled ? Math.max(0f, relative * masterVolume) : 0f;
    }

    private static float effectivePitch(float relative) {
        return Math.max(0.01f, relative * masterPitch);
    }

    public static void stopLoop(String soundId) {
        if (soundId == null || soundId.isBlank()) return;
        LoopingScriptSound inst = LOOPING.remove(soundId);
        if (inst == null) return;
        inst.forceStop();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(inst);
        }
    }

    public static void stopAllLoops() {
        for (String id : LOOPING.keySet()) stopLoop(id);
        LOOPING.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAllLoops();
        masterVolume = 1f;
        masterPitch = 1f;
        masterEnabled = true;
    }

    private static final class LoopingScriptSound extends AbstractTickableSoundInstance {
        private final float relativeVolume;
        private final float relativePitch;
        private boolean forceStopped;

        LoopingScriptSound(SoundEvent event, float relativeVolume, float relativePitch) {
            super(event, SoundSource.MASTER, net.minecraft.util.RandomSource.create());
            this.relativeVolume = relativeVolume;
            this.relativePitch = relativePitch;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            refreshFromMaster();
        }

        void refreshFromMaster() {
            this.volume = effectiveVolume(relativeVolume);
            this.pitch = effectivePitch(relativePitch);
        }

        void forceStop() {
            forceStopped = true;
            stop();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents ev = ClientWorkspaceSounds.events(manager, getLocation());
            if (ev != null) this.sound = ev.getSound(this.random);
            return ev;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
            return ClientWorkspaceSounds.stream(getLocation(), looping, library, sound);
        }

        @Override
        public void tick() {
            if (forceStopped) stop();
        }
    }

    private static final class WorkspaceUiSound extends SimpleSoundInstance {
        WorkspaceUiSound(SoundEvent event, float pitch, float volume) {
            super(event.getLocation(), SoundSource.MASTER, volume, pitch,
                    net.minecraft.util.RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents ev = ClientWorkspaceSounds.events(manager, getLocation());
            if (ev != null) this.sound = ev.getSound(this.random);
            return ev;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
            return ClientWorkspaceSounds.stream(getLocation(), looping, library, sound);
        }
    }
}
