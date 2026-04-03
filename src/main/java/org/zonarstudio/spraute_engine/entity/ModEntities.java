package org.zonarstudio.spraute_engine.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zonarstudio.spraute_engine.Spraute_engine;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Spraute_engine.MODID);

    public static final RegistryObject<EntityType<SprauteNpcEntity>> SPRAUTE_NPC =
            ENTITIES.register("spraute_npc",
                    () -> EntityType.Builder.<SprauteNpcEntity>of((type, level) -> new SprauteNpcEntity(type, level), MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .build("spraute_npc"));

    public static final RegistryObject<EntityType<SprauteOrbEntity>> SPRAUTE_ORB =
            ENTITIES.register("spraute_orb",
                    () -> EntityType.Builder.<SprauteOrbEntity>of((type, level) -> new SprauteOrbEntity(type, level), MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("spraute_orb"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
