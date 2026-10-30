package org.sprautestudio.st_addon_spraute.runtime.orb;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

/** Registration of the addon's entity types. */
public final class Orbs {
    private Orbs() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, St_addon_spraute.MODID);

    public static final RegistryObject<EntityType<SprauteOrb>> ORB = ENTITIES.register("orb",
            () -> EntityType.Builder.<SprauteOrb>of(SprauteOrb::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(8).updateInterval(2).build("orb"));

    public static final RegistryObject<EntityType<CameraEntity>> CAMERA = ENTITIES.register("camera",
            () -> EntityType.Builder.<CameraEntity>of(CameraEntity::new, MobCategory.MISC).sized(0.001f, 0.001f)
                    .noSave().noSummon().clientTrackingRange(0).build("camera"));

    /** Characters with a model from a Spraute project (spraute:<model> in scripts). */
    public static final RegistryObject<EntityType<org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity>> MODEL = ENTITIES.register("model",
            () -> EntityType.Builder.<org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity>of(
                            org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.9f).clientTrackingRange(10).build("model"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> e.put(MODEL.get(),
                net.minecraft.world.entity.Mob.createMobAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.25)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 48).build()));
    }
}
