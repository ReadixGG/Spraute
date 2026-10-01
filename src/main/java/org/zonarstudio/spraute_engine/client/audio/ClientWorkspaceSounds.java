package org.zonarstudio.spraute_engine.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.sounds.Sound;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.resource.SprautePaths;
import org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Play workspace {@code spraute_engine/sounds/} files immediately after drop/import,
 * without F3+T or re-entering the editor. Minecraft's {@code sounds.json} is only
 * parsed on resource reload; we synthesize the event and stream the ogg from disk.
 */
public final class ClientWorkspaceSounds {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientWorkspaceSounds() {}

    public static ResourceLocation location(String soundId) {
        if (soundId == null || soundId.isBlank()) return null;
        String raw = soundId.trim();
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    public static SoundEvent event(ResourceLocation rl) {
        if (rl == null) return null;
        //? if >=1.20.1 {
        return SoundEvent.createVariableRangeEvent(rl);
        //?} else {
        /*return new SoundEvent(rl);
        *///?}
    }

    public static boolean isWorkspaceFile(String soundId) {
        return WorkspaceSoundIndex.find(soundId) != null;
    }

    /** Convert mp3 → ogg if needed so the next play can open a stream. */
    public static void prepare(String soundId) {
        WorkspaceSoundIndex.Entry entry = WorkspaceSoundIndex.find(soundId);
        if (entry == null) return;
        WorkspaceSoundIndex.resolvePlayableOgg(SprautePaths.workspace(), entry.pathId());
    }

    public static WeighedSoundEvents events(SoundManager manager, ResourceLocation loc) {
        if (loc != null && WorkspaceSoundIndex.find(loc.toString()) != null) {
            return synthetic(loc);
        }
        return manager == null ? null : manager.getSoundEvent(loc);
    }

    public static CompletableFuture<AudioStream> stream(
            ResourceLocation loc, boolean looping, SoundBufferLibrary library, Sound sound) {
        AudioStream custom = open(loc, looping);
        if (custom != null) return CompletableFuture.completedFuture(custom);
        if (library == null || sound == null) return CompletableFuture.completedFuture(null);
        return library.getStream(sound.getPath(), looping);
    }

    public static WeighedSoundEvents synthetic(ResourceLocation loc) {
        WeighedSoundEvents events = new WeighedSoundEvents(loc, null);
        events.addSound(fileSound(loc));
        return events;
    }

    private static Sound fileSound(ResourceLocation loc) {
        String id = loc.toString();
        return new Sound(id,
                net.minecraft.util.valueproviders.ConstantFloat.of(1f),
                net.minecraft.util.valueproviders.ConstantFloat.of(1f),
                1, Sound.Type.FILE, true, false, 16);
    }

    private static AudioStream open(ResourceLocation loc, boolean looping) {
        if (loc == null) return null;
        WorkspaceSoundIndex.Entry entry = WorkspaceSoundIndex.find(loc.toString());
        if (entry == null) return null;
        Path ogg = WorkspaceSoundIndex.resolvePlayableOgg(SprautePaths.workspace(), entry.pathId());
        if (ogg == null || !Files.isRegularFile(ogg)) {
            LOGGER.warn("[Spraute Audio] No playable ogg for {} (mp3 needs ffmpeg in PATH or spraute_engine/tools/ffmpeg)", loc);
            return null;
        }
        try {
            InputStream in = Files.newInputStream(ogg);
            return looping ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in);
        } catch (Exception e) {
            LOGGER.warn("[Spraute Audio] Failed to open {}: {}", ogg.getFileName(), e.getMessage());
            return null;
        }
    }
}
