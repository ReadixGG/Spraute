package org.zonarstudio.spraute_engine.entity.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;

/**
 * Follows an air {@link GroundPath} with velocity steering.
 * Does not use vanilla {@code FlyingPathNavigation} — last waypoint is the exact fractional goal.
 */
public final class AirPathNavigator {
    public enum Status { IDLE, MOVING, ARRIVED, FAILED }

    private final AirPathSettings settings;
    private GroundPath path;
    private int index;
    private Vec3 goal;
    private double speed = 1.0;
    private boolean failed;
    private int repathCooldown;
    private int stuckTicks;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int repathAttempts;
    private boolean dampLastWaypoint = true;

    public AirPathNavigator(AirPathSettings settings) {
        this.settings = settings;
    }

    public void setDampLastWaypoint(boolean damp) {
        this.dampLastWaypoint = damp;
    }

    public AirPathSettings settings() {
        return settings;
    }

    public boolean isInProgress() {
        return path != null && !failed && goal != null;
    }

    public boolean isFailed() {
        return failed;
    }

    public Vec3 currentSteerTarget() {
        if (path != null && !path.isEmpty() && index < path.size()) {
            return path.get(index);
        }
        return goal;
    }

    public void stop(Mob mob) {
        path = null;
        index = 0;
        goal = null;
        failed = false;
        repathCooldown = 0;
        stuckTicks = 0;
        repathAttempts = 0;
        halt(mob);
    }

    public void moveTo(Mob mob, double x, double y, double z, double speed) {
        this.goal = new Vec3(x, y, z);
        this.speed = speed;
        this.failed = false;
        this.repathAttempts = 0;
        this.stuckTicks = 0;
        this.anchorX = mob.getX();
        this.anchorY = mob.getY();
        this.anchorZ = mob.getZ();
        this.repathCooldown = 0;
        compute(mob, true);
    }

    public void follow(Mob mob, double x, double y, double z, double speed) {
        Vec3 next = new Vec3(x, y, z);
        Vec3 old = this.goal;
        this.goal = next;
        this.speed = speed;
        if (arrived(mob, next, true)) {
            finishArrive(mob);
            return;
        }
        boolean goalMoved = old == null || old.distanceToSqr(next) > 0.36;
        boolean need = path == null || failed || goalMoved;
        if (!need) return;
        if (repathCooldown > 0 && !goalMoved) return;
        failed = false;
        compute(mob, false);
    }

    public boolean canReach(Mob mob, double x, double y, double z) {
        GroundPath found = AirPathfinder.find(
                SprauteEntityCompat.level(mob), settings,
                mob.position(), new Vec3(x, y, z),
                mob.getBbWidth(), mob.getBbHeight());
        return found != null && !found.isEmpty();
    }

    public Status tick(Mob mob, float smoothing) {
        if (goal == null) return Status.IDLE;
        if (arrived(mob, goal, true)) {
            finishArrive(mob);
            return Status.ARRIVED;
        }
        if (path == null || path.isEmpty()) {
            if (repathCooldown <= 0) compute(mob, false);
            else repathCooldown--;
            if (failed || path == null) return Status.FAILED;
        }
        if (index >= path.size()) {
            finishArrive(mob);
            return Status.ARRIVED;
        }
        Vec3 wp = path.get(index);
        boolean last = index == path.size() - 1;
        if (arrived(mob, wp, last)) {
            if (last) {
                finishArrive(mob);
                return Status.ARRIVED;
            }
            index++;
            wp = path.get(index);
            last = index == path.size() - 1;
        }
        trackStuck(mob);
        if (stuckTicks >= settings.stuckTicks) {
            stuckTicks = 0;
            repathAttempts++;
            if (repathAttempts >= 4) {
                failed = true;
                halt(mob);
                return Status.FAILED;
            }
            compute(mob, true);
            if (path == null) return Status.FAILED;
            wp = path.get(Math.min(index, path.size() - 1));
            last = index >= path.size() - 1;
        }
        if (repathCooldown > 0) repathCooldown--;
        steer(mob, wp, last, smoothing);
        return Status.MOVING;
    }

    private void compute(Mob mob, boolean resetIndex) {
        GroundPath found = AirPathfinder.find(
                SprauteEntityCompat.level(mob), settings,
                mob.position(), goal,
                mob.getBbWidth(), mob.getBbHeight());
        repathCooldown = settings.repathTicks;
        if (found == null || found.isEmpty()) {
            path = null;
            failed = true;
            halt(mob);
            return;
        }
        path = found;
        failed = false;
        if (resetIndex || index >= path.size()) index = 0;
        while (index < path.size() - 1 && arrived(mob, path.get(index), false)) {
            index++;
        }
    }

    private boolean arrived(Mob mob, Vec3 target, boolean exact) {
        double dx = mob.getX() - target.x;
        double dy = mob.getY() - target.y;
        double dz = mob.getZ() - target.z;
        double r = exact ? settings.arriveRadius : settings.waypointRadius;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private void finishArrive(Mob mob) {
        if (settings.snapOnArrive && goal != null && path != null) {
            if (mob.position().distanceToSqr(goal) <= settings.arriveRadius * settings.arriveRadius * 4.0) {
                mob.setPos(goal.x, goal.y, goal.z);
            }
        }
        halt(mob);
        path = null;
        index = 0;
        failed = false;
    }

    private void steer(Mob mob, Vec3 target, boolean last, float smoothing) {
        double dx = target.x - mob.getX();
        double dy = target.y - mob.getY();
        double dz = target.z - mob.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double flySpeed = 0.5;
        var attr = mob.getAttribute(Attributes.FLYING_SPEED);
        if (attr != null) flySpeed = attr.getValue();
        double used = speed;
        if (dampLastWaypoint && last && dist < 1.25) {
            used = Math.max(0.18, speed * Math.max(0.22, dist / 1.25));
        }
        double maxStep = flySpeed * Math.max(0.1, used) * 0.5;

        mob.setNoGravity(true);
        double step = Math.min(maxStep, dist);
        Vec3 desired = dist < 1.0e-8 ? Vec3.ZERO : new Vec3(dx, dy, dz).normalize().scale(step);
        Vec3 cur = mob.getDeltaMovement();
        float lerp = Math.max(0.02f, Math.min(1f, smoothing));
        Vec3 next = cur.add(desired.subtract(cur).scale(lerp));
        double cap = dist < 1.0e-8 ? 0.0 : Math.min(maxStep, dist);
        if (cap > 0 && next.length() > cap) {
            next = next.normalize().scale(cap);
        }
        mob.setDeltaMovement(next);
        mob.hasImpulse = true;
    }

    private void trackStuck(Mob mob) {
        if (goal != null && mob.position().distanceToSqr(goal) <= 2.25) {
            stuckTicks = 0;
            return;
        }
        double dx = mob.getX() - anchorX;
        double dy = mob.getY() - anchorY;
        double dz = mob.getZ() - anchorZ;
        if (dx * dx + dy * dy + dz * dz > 0.16) {
            stuckTicks = 0;
            anchorX = mob.getX();
            anchorY = mob.getY();
            anchorZ = mob.getZ();
        } else {
            stuckTicks++;
        }
    }

    private static void halt(Mob mob) {
        if (mob == null) return;
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hasImpulse = true;
    }
}
