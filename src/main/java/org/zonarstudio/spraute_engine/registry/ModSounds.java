package org.zonarstudio.spraute_engine.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zonarstudio.spraute_engine.Spraute_engine;

/** Dummy streaming events so SoundEngine calls {@code SoundInstance#getStream}. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Spraute_engine.MODID);

    public static final RegistryObject<SoundEvent> UI_VIDEO = SOUNDS.register("ui_video", () -> {
        ResourceLocation id = new ResourceLocation(Spraute_engine.MODID, "ui_video");
        //? if >=1.20.1 {
        return SoundEvent.createVariableRangeEvent(id);
        //?} else {
        /*return new SoundEvent(id);
        *///?}
    });

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
