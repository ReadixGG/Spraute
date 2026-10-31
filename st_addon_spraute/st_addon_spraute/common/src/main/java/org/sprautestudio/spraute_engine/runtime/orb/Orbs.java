package org.sprautestudio.spraute_engine.runtime.orb;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.util.function.Supplier;

/** Registration of the addon's entity types. */
public final class Orbs {
    private Orbs() {}

    public static final Supplier<EntityType<SprauteOrb>> ORB = Platform.get().entityType("orb",
            () -> EntityType.Builder.<SprauteOrb>of(SprauteOrb::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(8).updateInterval(2).build("orb"));

    public static final Supplier<EntityType<CameraEntity>> CAMERA = Platform.get().entityType("camera",
            () -> EntityType.Builder.<CameraEntity>of(CameraEntity::new, MobCategory.MISC).sized(0.001f, 0.001f)
                    .noSave().noSummon().clientTrackingRange(0).build("camera"));

    /** Characters with a model from a Spraute project (spraute:<model> in scripts). */
    public static final Supplier<EntityType<org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity>> MODEL = Platform.get().entityType("model",
            () -> EntityType.Builder.<org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity>of(
                            Platform.get()::modelEntity, MobCategory.CREATURE)
                    .sized(0.6f, 1.9f).clientTrackingRange(10).build("model"));

    /** Touching this class registers the types above. */
    public static void init() {
        Platform.get().entityAttributes(MODEL, () -> net.minecraft.world.entity.Mob.createMobAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.25)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 48).build());
    }
}
