package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.world.Location;

/** The invisible seats characters and the player sit on: made when needed, gone when they get up. */
public final class Seats {
    private Seats() {
    }

    private static final String TAG = "SpraputeSeat";

    /** Sits at a point (the body's turn locked to it) or where it stands. */
    public static void sit(Entity e, Location at) {
        stand(e);
        if (!(e.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ArmorStand seat = EntityType.ARMOR_STAND.create(level);
        if (seat == null) return;
        double x = at != null ? at.x : e.getX(), y = at != null ? at.y : e.getY(), z = at != null ? at.z : e.getZ();
        seat.moveTo(x, y - 0.5, z, at != null ? at.yaw : e.getYRot(), 0);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.getPersistentData().putBoolean(TAG, true);
        level.addFreshEntity(seat);
        e.startRiding(seat, true);
        if (e instanceof AnimatedEntity ae) {
            ae.setSitting(true);
            if (at != null) ae.setBodyLocked(true);
        }
    }

    public static void stand(Entity e) {
        Entity seat = e.getVehicle();
        if (seat != null && seat.getPersistentData().getBoolean(TAG)) {
            e.stopRiding();
            seat.discard();
        }
        if (e instanceof AnimatedEntity ae) {
            ae.setSitting(false);
            ae.setBodyLocked(false);
        }
    }
}
