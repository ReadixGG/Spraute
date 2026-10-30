package org.sprautestudio.st_addon_spraute.path;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.util.*;

/**
 * Characters walking paths: each tick the walker heads for its current point (path-finding for mobs,
 * stepping for anything else), stands at it as long as the point says (with its animation and facing),
 * then takes the next one — to the end, in a circle, or there and back.
 * <p>
 * Minecraft's path-finding only says the way (around walls, up stairs); the steps along it are given
 * here, so many blocks a tick, and the game's own collisions, step-up and gravity do the rest. So the
 * speed is exact and the same for everyone: 1 — an ordinary walk (2.6 blocks a second),
 * whatever the character's own speed or animation set is. Points without a pause are passed through without stopping; steps up to a
 * block (slabs, stairs) are walked up, not jumped. One stuck for a few seconds is put onto its point.
 * At the end of a walk the stories get the event pathend.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class Walkers {
    private Walkers() {
    }

    /** Blocks a second at speed 1: an ordinary walk (0.6 of a player's walk, about 2.6 blocks a second). */
    public static final double WALK_BPS = 0.6 * 4.317;

    private static final class Walk {
        final Entity e;
        final String name;
        final PathDef d;
        final String mode;
        final double speed;
        final float oldStep;
        int index, dir = 1, waitLeft, stuck, repath;
        boolean waiting;
        /** A straight move through the air and walls, for flying and special bodies. */
        boolean straight;
        Vec3 last;
        /** The way it was going, to keep facing it while standing. */
        float facing = Float.NaN;
        /** The way around obstacles to the current point (Minecraft's path-finding), and the step of it being walked. */
        net.minecraft.world.level.pathfinder.Path route;
        int node;

        Walk(Entity e, String name, PathDef d, String mode, double speed, int from) {
            this.e = e;
            this.name = name;
            this.d = d;
            this.mode = mode;
            this.speed = speed;
            this.index = Math.max(0, Math.min(from, d.points.size() - 1));
            this.oldStep = e.maxUpStep();
        }
    }

    private static final Map<UUID, Walk> WALKS = new LinkedHashMap<>();
    /** Who keeps looking at something (look … always): the head follows it while the body walks. */
    private static final Map<UUID, java.util.function.Supplier<Vec3>> LOOKS = new HashMap<>();

    public static void lookAt(Entity e, java.util.function.Supplier<Vec3> at) {
        if (e == null) return;
        if (at == null) LOOKS.remove(e.getUUID());
        else LOOKS.put(e.getUUID(), at);
    }

    /** Starts (or restarts) a walk. speed ≤ 0 and mode "" — the path's own. from — the point to begin with (0 — first; -1 — the nearest). */
    public static void start(Entity e, String name, PathDef d, double speed, String mode, int from) {
        if (e == null || d.points.isEmpty()) return;
        startWalk(e, name, d, speed, mode, from, false);
        // a walk is the body's movement behaviour: whatever moved it before ends
        org.sprautestudio.st_addon_spraute.core.script.Behaviours.walking(e);
    }

    /** Walks to one point, for what drives the walking itself (following, wandering). */
    public static void go(Entity e, Vec3 to, double speed, boolean straight) {
        var d = new PathDef();
        d.points.add(new PathDef.Point(to.x, to.y, to.z));
        d.speed = speed;
        startWalk(e, "", d, speed, "once", 0, straight);
    }

    /** A straight move without physics, as a behaviour. */
    public static void fly(Entity e, Vec3 to, double speed) {
        go(e, to, speed, true);
        org.sprautestudio.st_addon_spraute.core.script.Behaviours.walking(e);
    }

    private static void startWalk(Entity e, String name, PathDef d, double speed, String mode, int from, boolean straight) {
        stop(e);
        if (from < 0) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < d.points.size(); i++) {
                double dist = d.points.get(i).vec().distanceToSqr(e.position());
                if (dist < best) {
                    best = dist;
                    from = i;
                }
            }
        }
        Walk w = new Walk(e, name, d, mode == null || mode.isEmpty() ? d.mode : mode, speed > 0 ? speed : d.speed, from);
        // slabs and stairs are walked up, not jumped onto
        e.setMaxUpStep(Math.max(w.oldStep, 1.05f));
        w.straight = straight;
        WALKS.put(e.getUUID(), w);
    }

    public static void stop(Entity e) {
        if (e == null) return;
        Walk w = WALKS.remove(e.getUUID());
        if (w != null) end(w);
    }

    private static void end(Walk w) {
        w.e.setMaxUpStep(w.oldStep);
        Vec3 v = w.e.getDeltaMovement();
        w.e.setDeltaMovement(0, v.y, 0);
        if (w.e instanceof Mob m) m.getNavigation().stop();
    }

    public static boolean walking(Entity e) {
        return e != null && WALKS.containsKey(e.getUUID());
    }

    /** The speed it walks at now (the point's own or the path's), -1 — not walking a path. */
    public static double speedOf(Entity e) {
        Walk w = e == null ? null : WALKS.get(e.getUUID());
        if (w == null || w.waiting) return -1;
        PathDef.Point p = w.d.points.get(w.index);
        return p.speed > 0 ? p.speed : w.speed;
    }

    /** Fast walk from this path speed on (a body moved faster than this shows it by itself, see AnimatedEntity). */
    public static final double FAST_WALK = 1.6;
    /** Run from this speed on. */
    public static final double RUN = 2.0;

    /** The point being walked to (0-based), -1 — not walking. */
    public static int point(Entity e) {
        Walk w = e == null ? null : WALKS.get(e.getUUID());
        return w == null ? -1 : w.index;
    }

    public static void unload() {
        WALKS.clear();
        LOOKS.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || WALKS.isEmpty()) return;
        for (Walk w : new ArrayList<>(WALKS.values())) {
            if (w.e.isRemoved() || !w.e.isAlive()) {
                WALKS.remove(w.e.getUUID());
                continue;
            }
            try {
                step(w);
            } catch (RuntimeException ex) {
                St_addon_spraute.LOGGER.warn("Spraute: path walk", ex);
                WALKS.remove(w.e.getUUID());
                end(w);
            }
        }
    }

    /** Whether the walk stops at this point (a pause, or the very end). */
    private static boolean stopsAt(Walk w, int i) {
        if (w.d.points.get(i).wait > 0) return true;
        int n = w.d.points.size();
        return w.mode.equals("once") && i == n - 1 || w.mode.equals("pingpong") && (i == n - 1 && w.dir > 0 || i == 0 && w.dir < 0);
    }

    private static void step(Walk w) {
        Entity e = w.e;
        PathDef.Point p = w.d.points.get(w.index);
        if (w.waiting) {
            float yaw = p.yaw != null ? p.yaw.floatValue() : w.facing;
            if (!Float.isNaN(yaw)) face(e, yaw);
            else if (LOOKS.containsKey(e.getUUID())) face(e, e.getYRot());
            if (--w.waitLeft > 0) return;
            w.waiting = false;
            if (!p.anim.isBlank()) org.sprautestudio.st_addon_spraute.fight.Fights.play(e, "", false, false);
            if (!next(w)) return;
            p = w.d.points.get(w.index);
        }
        Vec3 to = p.vec();
        double dx = to.x - e.getX(), dz = to.z - e.getZ(), dy = Math.abs(e.getY() - to.y);
        double flat = Math.sqrt(dx * dx + dz * dz);
        boolean stops = stopsAt(w, w.index);
        // passing points are taken early, so it walks on without stopping; stopping points — close
        double reach = stops ? 0.6 : 1.3;
        boolean routeDone = w.route != null && w.node >= w.route.getNodeCount() && w.repath < 35;
        if ((flat <= reach && dy <= 1.6) || (routeDone && flat <= 2.0 && dy <= 2.5)) {
            if (flat > 0.05) w.facing = (float) Math.toDegrees(Math.atan2(-dx, dz));
            arrived(w, p, stops);
            return;
        }
        double sp = p.speed > 0 ? p.speed : w.speed;
        double want = sp * WALK_BPS / 20; // blocks a tick
        float yaw;
        if (e instanceof Mob m && !m.isNoAi() && !w.straight) {
            var nav = m.getNavigation();
            // the way: found again now and then (the world changes, it may be pushed off it)
            if (w.route == null || --w.repath <= 0) {
                w.route = nav.createPath(to.x, to.y, to.z, 0);
                w.node = 1;
                w.repath = 40;
            }
            // nobody else walks it meanwhile: no path-finder steps of its own, no push forward
            if (!nav.isDone()) nav.stop();
            m.setZza(0);
            m.setXxa(0);
            Vec3 aim = to;
            if (w.route != null) {
                while (w.node < w.route.getNodeCount()) {
                    var nd = w.route.getNode(w.node);
                    double nx = nd.x + 0.5 - e.getX(), nz = nd.z + 0.5 - e.getZ();
                    if (nx * nx + nz * nz < 0.3 * 0.3 && Math.abs(nd.y - e.getY()) < 1.2) w.node++;
                    else break;
                }
                // the last steps go to the point itself, not the middle of its block
                if (w.node < w.route.getNodeCount() - 1) {
                    var nd = w.route.getNode(w.node);
                    aim = new Vec3(nd.x + 0.5, nd.y, nd.z + 0.5);
                }
            }
            double ax = aim.x - e.getX(), az = aim.z - e.getZ(), len = Math.sqrt(ax * ax + az * az);
            double stepLen = Math.min(want, len);
            Vec3 v = e.getDeltaMovement();
            if (len > 1e-4) e.setDeltaMovement(ax / len * stepLen, v.y, az / len * stepLen);
            else e.setDeltaMovement(0, v.y, 0);
            yaw = len > 1e-4 ? (float) Math.toDegrees(Math.atan2(-ax, az)) : w.facing;
            if (Float.isNaN(yaw)) yaw = e.getYRot();
            w.facing = yaw;
            float body = net.minecraft.util.Mth.approachDegrees(e instanceof LivingEntity le ? le.yBodyRot : e.getYRot(), yaw, 30);
            e.setYRot(body);
            turn(e, body);
        } else {
            // no AI to move it: a straight step towards the point
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            Vec3 d = to.subtract(e.position());
            double len = d.length(), stepLen = Math.min(len, want);
            Vec3 n = e.position().add(d.scale(stepLen / Math.max(1e-6, len)));
            e.moveTo(n.x, n.y, n.z, yaw, e.getXRot());
            w.facing = yaw;
            face(e, yaw);
        }
        // stuck: no progress for 4 seconds — put it on the point
        if (w.last == null || w.last.distanceToSqr(e.position()) > 0.04) {
            w.last = e.position();
            w.stuck = 0;
        } else if (++w.stuck > 80) {
            e.teleportTo(to.x, to.y, to.z);
            w.stuck = 0;
        }
    }

    /** A walking mob: the body and the head turn, the direction it moves in stays the path-finder's. */
    private static void turn(Entity e, float yaw) {
        float head = yaw;
        var look = LOOKS.get(e.getUUID());
        Vec3 at = null;
        if (look != null) {
            try {
                at = look.get();
            } catch (RuntimeException ignored) {
            }
        }
        if (at != null) {
            double dx = at.x - e.getX(), dy = at.y - e.getEyeY(), dz = at.z - e.getZ();
            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            head = yaw + Math.max(-75, Math.min(75, net.minecraft.util.Mth.wrapDegrees(wantYaw - yaw)));
            e.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        } else e.setXRot(0);
        e.setYHeadRot(head);
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw);
        if (e instanceof Mob m) {
            double hr = Math.toRadians(head);
            m.getLookControl().setLookAt(e.getX() - Math.sin(hr) * 4, at != null ? at.y : e.getEyeY(), e.getZ() + Math.cos(hr) * 4, 360, 90);
        }
    }

    /**
     * Turns the body to yaw; the head the same way, or — when the character keeps looking at something —
     * at it, as far as a neck turns (75° from the body), with the pitch to it.
     */
    private static void face(Entity e, float yaw) {
        float head = yaw, pitch = 0;
        var look = LOOKS.get(e.getUUID());
        Vec3 at = null;
        if (look != null) {
            try {
                at = look.get();
            } catch (RuntimeException ignored) {
            }
        }
        if (at != null) {
            double dx = at.x - e.getX(), dy = at.y - e.getEyeY(), dz = at.z - e.getZ();
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = net.minecraft.util.Mth.wrapDegrees(want - yaw);
            head = yaw + Math.max(-75, Math.min(75, diff));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.setYHeadRot(head);
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw);
        if (e instanceof Mob m) {
            double hr = Math.toRadians(head);
            m.getLookControl().setLookAt(e.getX() - Math.sin(hr) * 4, at != null ? at.y : e.getEyeY(), e.getZ() + Math.cos(hr) * 4, 360, 90);
        }
    }

    private static void arrived(Walk w, PathDef.Point p, boolean stops) {
        Entity e = w.e;
        w.stuck = 0;
        w.last = null;
        if (stops) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0, v.y, 0);
        }
        if (p.wait > 0) {
            w.waiting = true;
            w.waitLeft = (int) Math.round(p.wait * 20);
            if (!p.anim.isBlank()) org.sprautestudio.st_addon_spraute.fight.Fights.play(e, p.anim, true, false);
            return;
        }
        next(w);
    }

    /** Takes the next point; false — the walk is over. */
    private static boolean next(Walk w) {
        int n = w.d.points.size();
        int i = w.index + w.dir;
        if (i >= n || i < 0) {
            switch (w.mode) {
                case "loop" -> i = 0;
                case "pingpong" -> {
                    w.dir = -w.dir;
                    i = n == 1 ? 0 : w.index + w.dir;
                }
                default -> {
                    finish(w);
                    return false;
                }
            }
        }
        w.index = Math.max(0, Math.min(n - 1, i));
        // head for the next one at once: no standing between points
        w.route = null;
        return true;
    }

    private static void finish(Walk w) {
        WALKS.remove(w.e.getUUID());
        end(w);
        if (!Float.isNaN(w.facing)) face(w.e, w.facing);
        Map<String, Object> data = new HashMap<>();
        data.put("path", w.name);
        data.put("entity", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(w.e.getType()).toString());
        data.put("name", w.e.getName().getString());
        data.put("__entity", w.e);
        data.put("x", Math.floor(w.e.getX()));
        data.put("y", Math.floor(w.e.getY()));
        data.put("z", Math.floor(w.e.getZ()));
        if (w.name.isEmpty()) return; // move / path, not a path of the project
        ServerPlayer p = SprauteRuntime.player();
        GameEvents.post("pathend", p, data);
    }
}
