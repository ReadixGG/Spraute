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
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("spraute_npc"));

    public static final RegistryObject<EntityType<SprauteOrbEntity>> SPRAUTE_ORB =
            ENTITIES.register("spraute_orb",
                    () -> EntityType.Builder.<SprauteOrbEntity>of((type, level) -> new SprauteOrbEntity(type, level), MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(6)
                            .updateInterval(1)
                            .build("spraute_orb"));

    public static final RegistryObject<EntityType<SprauteBillboardEntity>> SPRAUTE_BILLBOARD =
            ENTITIES.register("spraute_billboard",
                    () -> EntityType.Builder.<SprauteBillboardEntity>of((type, level) -> new SprauteBillboardEntity(type, level), MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("spraute_billboard"));

    public static final RegistryObject<EntityType<SprauteProjectileEntity>> SPRAUTE_PROJECTILE =
            ENTITIES.register("spraute_projectile",
                    () -> EntityType.Builder.<SprauteProjectileEntity>of((type, level) -> new SprauteProjectileEntity(type, level), MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("spraute_projectile"));

    public static final RegistryObject<EntityType<SprauteModelEntity>> SPRAUTE_MODEL =
            ENTITIES.register("spraute_model",
                    () -> EntityType.Builder.<SprauteModelEntity>of((type, level) -> new SprauteModelEntity(type, level), MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("spraute_model"));

    public static final RegistryObject<EntityType<SprauteWorldCanvasEntity>> SPRAUTE_WORLD_CANVAS =
            ENTITIES.register("spraute_world_canvas",
                    () -> EntityType.Builder.<SprauteWorldCanvasEntity>of((type, level) -> new SprauteWorldCanvasEntity(type, level), MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("spraute_world_canvas"));

    public static final RegistryObject<EntityType<SprauteHitboxEntity>> SPRAUTE_HITBOX =
            ENTITIES.register("spraute_hitbox",
                    () -> EntityType.Builder.<SprauteHitboxEntity>of(SprauteHitboxEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("spraute_hitbox"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
