package org.sprautestudio.spraute_engine.platform;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;

import java.util.concurrent.CompletableFuture;

/** A sound that plays from its own data, not from the game's sound files: each loader has its way to make the game ask it. */
public interface CustomSoundStream {
    CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping);
}
