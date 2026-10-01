package org.zonarstudio.spraute_engine.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.zonarstudio.spraute_engine.entity.SprauteHitboxEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Adds scene HITBOX carriers to vanilla movement collision shapes. This runs
 * inside Entity.collide's normal block/entity resolution, before movement is
 * applied, so colliders are immovable and walkable rather than post-tick pushes.
 */
@Mixin(Level.class)
public abstract class LevelCollisionMixin implements EntityGetter {
    /**
     * Level normally inherits this default method from EntityGetter. Defining
     * the override on Level lets us retain vanilla entity shapes and append the
     * scene colliders without replacing Entity movement itself.
     */
    @Override
    public List<VoxelShape> getEntityCollisions(Entity movingEntity, AABB bounds) {
        Predicate<Entity> predicate = movingEntity == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(movingEntity::canCollideWith);
        List<Entity> vanillaEntities = getEntities(
                movingEntity, bounds.inflate(1.0E-7), predicate);
        List<VoxelShape> vanilla = new ArrayList<>(vanillaEntities.size());
        for (Entity entity : vanillaEntities) {
            vanilla.add(Shapes.create(entity.getBoundingBox()));
        }
        if (movingEntity instanceof SprauteHitboxEntity) return vanilla;
        Level level = (Level) (Object) this;
        List<SprauteHitboxEntity> colliders = level.getEntitiesOfClass(
                SprauteHitboxEntity.class, bounds.inflate(0.001),
                SprauteHitboxEntity::isSolidCollider);
        if (colliders.isEmpty()) return vanilla;

        List<VoxelShape> result = new ArrayList<>(vanilla);
        for (SprauteHitboxEntity collider : colliders) {
            AABB colliderBox = collider.getBoundingBox();
            if (colliderBox.intersects(bounds.inflate(1.0E-7))) {
                result.add(Shapes.create(colliderBox));
            }
        }
        return result;
    }
}
