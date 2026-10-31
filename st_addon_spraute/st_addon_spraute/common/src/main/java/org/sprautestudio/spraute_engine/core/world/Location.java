package org.sprautestudio.spraute_engine.core.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A point in a dimension, with a direction. */
public final class Location extends Vec3 {
    public static final Location ZERO = new Location(0, 0, 0);

    public final ResourceKey<Level> dimension;
    public final float yaw, pitch;

    public Location(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        super(x, y, z);
        this.dimension = dimension;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location(double x, double y, double z) {
        this(Level.OVERWORLD, x, y, z, 0, 0);
    }

    public Location(Entity e) {
        this(e.level().dimension(), e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot());
    }

    public Location(Level level, Vec3 at) {
        this(level.dimension(), at.x, at.y, at.z, 0, 0);
    }
}
