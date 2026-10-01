package org.zonarstudio.spraute_engine.entity.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;

/**
 * Follows a {@link GroundPath} with {@link net.minecraft.world.entity.ai.control.MoveControl}.
 * Does not use vanilla {@code PathNavigation} — last waypoint is the exact fractional goal.
 */
public final class GroundPathNavigator {
    public enum Status { IDLE, MOVING, ARRIVED, FAILED }

    private final GroundPathSettings settings;
    private GroundPath path;
    private int index;
    private Vec3 goal;
    private double speed = 1.0;
    private boolean failed;
    private int repathCooldown;
    private int stuckTicks;
    private double anchorX;
    private double anchorZ;
    private int repathAttempts;
    /** When false, keep full speed into the last A* waypoint (route chaining). */
    private boolean dampLastWaypoint = true;

    public GroundPathNavigator(GroundPathSettings settings) {
        this.settings = settings;
    }

    public void setDampLastWaypoint(boolean damp) {
        this.dampLastWaypoint = damp;
    }

    public GroundPathSettings settings() {
        return settings;
    }

    public boolean isInProgress() {
        return path != null && !failed && goal != null;
    }

    public boolean isFailed() {
        return failed;
    }

    public Vec3 goal() {
        return goal;
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

    /** One-shot move: compute immediately. */
    public void moveTo(Mob mob, double x, double y, double z, double speed) {
        this.goal = new Vec3(x, y, z);
        this.speed = speed;
        this.failed = false;
        this.repathAttempts = 0;
        this.stuckTicks = 0;
        this.anchorX = mob.getX();
        this.anchorZ = mob.getZ();
        this.repathCooldown = 0;
        compute(mob, true);
    }

    /** Moving target (alwaysMove / chase): throttle repath. */
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
        GroundPath found = GroundPathfinder.find(
                SprauteEntityCompat.level(mob), settings,
                mob.position(), new Vec3(x, y, z),
                mob.getBbWidth(), mob.getBbHeight());
        return found != null && !found.isEmpty();
    }

    public Status tick(Mob mob) {
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
        steer(mob, wp, last);
        return Status.MOVING;
    }

    /**
     * Jump only when the first collision lip ahead is taller than {@link GroundPathSettings#stepHeight}.
     * Stairs/slabs are 0.5-high lips — walk, do not jump even if the waypoint sits on the top step.
     */
    private boolean shouldJump(Mob mob, Vec3 wp) {
        if (!SprauteEntityCompat.onGround(mob)) return false;
        double feetY = mob.getY();
        double rise = wp.y - feetY;
        if (rise <= settings.stepHeight + 0.05) return false;
        if (rise > settings.maxJump + 0.35) return false;
        double dx = wp.x - mob.getX();
        double dz = wp.z - mob.getZ();
        double dist = Math.hypot(dx, dz);
        double nearestLip = Double.POSITIVE_INFINITY;
        double[] ahead = {0.2, 0.35, 0.5, 0.7};
        var level = SprauteEntityCompat.level(mob);
        for (double t : ahead) {
            double sx;
            double sz;
            if (dist < 0.08) {
                sx = wp.x;
                sz = wp.z;
            } else {
                if (t > dist) continue;
                sx = mob.getX() + dx / dist * t;
                sz = mob.getZ() + dz / dist * t;
            }
            double lip = GroundPathfinder.firstLipAbove(level, sx, sz, feetY, settings.maxJump + 0.5);
            if (lip > Double.NEGATIVE_INFINITY && lip < nearestLip) nearestLip = lip;
            if (dist < 0.08) break;
        }
        if (nearestLip == Double.POSITIVE_INFINITY) return false;
        return nearestLip - feetY > settings.stepHeight + 0.05;
    }

    private void compute(Mob mob, boolean resetIndex) {
        GroundPath found = GroundPathfinder.find(
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
        // Skip waypoints already behind us.
        while (index < path.size() - 1 && arrived(mob, path.get(index), false)) {
            index++;
        }
    }

    private boolean arrived(Mob mob, Vec3 target, boolean exact) {
        double dx = mob.getX() - target.x;
        double dz = mob.getZ() - target.z;
        double r = exact ? settings.arriveRadius : settings.waypointRadius;
        if (dx * dx + dz * dz > r * r) return false;
        return Math.abs(mob.getY() - target.y) <= settings.arriveVert;
    }

    private void finishArrive(Mob mob) {
        if (settings.snapOnArrive && goal != null && path != null) {
            double dx = mob.getX() - goal.x;
            double dz = mob.getZ() - goal.z;
            if (dx * dx + dz * dz <= settings.arriveRadius * settings.arriveRadius * 4.0) {
                mob.setPos(goal.x, mob.getY(), goal.z);
            }
        }
        halt(mob);
        path = null;
        index = 0;
        failed = false;
    }

    private void steer(Mob mob, Vec3 wp, boolean last) {
        boolean jump = shouldJump(mob, wp);
        if (jump) {
            mob.getJumpControl().jump();
        }
        double distH = Math.hypot(wp.x - mob.getX(), wp.z - mob.getZ());
        double used = speed;
        if (dampLastWaypoint && last && distH < 1.25) {
            used = Math.max(0.18, speed * Math.max(0.22, distH / 1.25));
        }
        // Vanilla MoveControl jumps when wantedY - y > stepHeight and XZ is close.
        // Keep wanted Y at the feet for walkable lips (stairs/slabs) so the mob steps up.
        double wantedY = (!jump && wp.y > mob.getY()) ? mob.getY() : wp.y;
        mob.getMoveControl().setWantedPosition(wp.x, wantedY, wp.z, used);
    }

    private void trackStuck(Mob mob) {
        if (goal != null && mob.position().distanceToSqr(goal) <= 2.25) {
            stuckTicks = 0;
            return;
        }
        double dx = mob.getX() - anchorX;
        double dz = mob.getZ() - anchorZ;
        if (dx * dx + dz * dz > 0.16) {
            stuckTicks = 0;
            anchorX = mob.getX();
            anchorZ = mob.getZ();
        } else {
            stuckTicks++;
        }
    }

    private static void halt(Mob mob) {
        if (mob == null) return;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setZza(0f);
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, v.y, 0);
    }
}
