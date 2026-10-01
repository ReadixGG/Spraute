package org.zonarstudio.spraute_engine.registry;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.zonarstudio.spraute_engine.Spraute_engine;

/**
 * Dedicated clients do not have the server's {@code create item/particle} IDs at first join.
 * Ignore those mappings so Forge does not show a fake "wrong game version" screen;
 * the snapshot is written during login and the player is asked to relaunch.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RegistryHandshakeEvents {
    private RegistryHandshakeEvents() {}

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        boolean any = false;
        any |= ignoreNamespace(event, ForgeRegistries.Keys.ITEMS);
        any |= ignoreNamespace(event, ForgeRegistries.Keys.BLOCKS);
        any |= ignoreNamespace(event, ForgeRegistries.Keys.PARTICLE_TYPES);
        any |= ignoreNamespace(event, ForgeRegistries.Keys.BLOCK_ENTITY_TYPES);
        any |= ignoreNamespace(event, ForgeRegistries.Keys.ENTITY_TYPES);
        if (any) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    org.zonarstudio.spraute_engine.client.assetsync.RegistryHandshakeClient.markIgnoredServerEntries());
        }
    }

    private static <T> boolean ignoreNamespace(MissingMappingsEvent event,
                                               net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key) {
        boolean any = false;
        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, Spraute_engine.MODID)) {
            mapping.ignore();
            any = true;
        }
        return any;
    }
}
