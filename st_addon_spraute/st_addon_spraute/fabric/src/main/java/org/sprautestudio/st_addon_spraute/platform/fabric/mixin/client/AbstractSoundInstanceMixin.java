package org.sprautestudio.st_addon_spraute.platform.fabric.mixin.client;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.sprautestudio.st_addon_spraute.platform.CustomSoundStream;

import java.util.concurrent.CompletableFuture;

/** Fabric API asks every sound for its stream, through this method: the sounds the engine made answer with their own. */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin implements FabricSoundInstance {
    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, ResourceLocation id, boolean looping) {
        if ((Object) this instanceof CustomSoundStream own) return own.getStream(library, ((SoundInstance) this).getSound(), looping);
        return FabricSoundInstance.super.getAudioStream(library, id, looping);
    }
}
