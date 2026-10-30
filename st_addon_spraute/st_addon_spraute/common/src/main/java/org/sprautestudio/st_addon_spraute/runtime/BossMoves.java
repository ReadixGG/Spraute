package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.fight.Fights;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.Platform;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.*;

/**
 * Building blocks for bosses, driven by the server tick: moves that take over a body for a while
 * (a leap with a landing blow, a charge through everyone in the way, circling a target), delayed
 * strikes with a warning mark, beams that burn along a line, and boss bars that follow a body's
 * health by themselves. While a move drives a fighter, its fight waits (Fights.hold).
 */
public final class BossMoves {
    public static void registerHooks() {
        Hooks.SERVER_TICK.add(BossMoves::onTick);
    }

    private BossMoves() {
    }

    /** An effect the story resolved (project effect or built-in) and where it came from. */
    public record Fx(String json, String project, int scriptId, float scale) {
    }

    private static void play(Fx fx, Vec3 at) {
        if (fx == null || fx.json == null) return;
        SprauteNetwork.sendToAudience(fx.scriptId, new Packets.FxPlay("", fx.project, fx.json, at.x, at.y, at.z, -1, fx.scale, fx.scriptId));
    }

    private static int now() {
        MinecraftServer s = Platform.get().server();
        return s == null ? 0 : s.getTickCount();
    }

    private static Entity find(UUID id) {
        MinecraftServer s = Platform.get().server();
        if (s == null) return null;
        for (ServerLevel lv : s.getAllLevels()) {
            Entity e = lv.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private static void face(Entity e, Vec3 dir) {
        if (dir.x * dir.x + dir.z * dir.z < 1e-6) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    /** Moves the body one step and lets the clients interpolate it. */
    private static void place(Entity e, Vec3 to) {
        Vec3 d = to.subtract(e.position());
        e.setDeltaMovement(d);
        e.setPos(to.x, to.y, to.z);
        e.fallDistance = 0;
        e.hasImpulse = true;
        e.hurtMarked = true;
        if (e instanceof Mob m) m.getNavigation().stop();
    }

    private static void hurt(ServerLevel lv, Entity victim, Entity by, float damage) {
        if (damage <= 0) return;
        var src = by != null ? lv.damageSources().indirectMagic(by, by) : lv.damageSources().magic();
        victim.hurt(src, damage);
    }

    // ================================================================== moves (one per body)

    private abstract static class Move {
        final UUID who;
        final int scriptId;
        int t;

        Move(Entity e, int scriptId) {
            this.who = e.getUUID();
            this.scriptId = scriptId;
        }

        /** One tick; true — the move is over. */
        abstract boolean step(ServerLevel lv, Entity e);

        void end(ServerLevel lv, Entity e) {
        }
    }

    private static final Map<UUID, Move> MOVES = new LinkedHashMap<>();

    private static void begin(Entity e, Move m, int ticks) {
        Move old = MOVES.put(e.getUUID(), m);
        if (old != null && e.level() instanceof ServerLevel lv) old.end(lv, e);
        Fights.hold(e, ticks + 2);
    }

    /** A move (leap, charge, circle) is driving this body now. */
    public static boolean moving(Entity e) {
        return e != null && MOVES.containsKey(e.getUUID());
    }

    /** Stops the body's move and the beams it casts. */
    public static void stop(Entity e) {
        Move m = MOVES.remove(e.getUUID());
        if (m != null && e.level() instanceof ServerLevel lv) m.end(lv, e);
        Fights.hold(e, 0);
        BEAMS.removeIf(b -> b.from.equals(e.getUUID()));
    }

    /** The story stopped: its moves, strikes, beams and bars go too. */
    public static void stopScript(int scriptId) {
        AttackBoxes.stopScript(scriptId);
        MOVES.values().removeIf(m -> m.scriptId == scriptId);
        STRIKES.removeIf(s -> s.scriptId == scriptId);
        BEAMS.removeIf(b -> b.scriptId == scriptId);
        for (Iterator<Bar> it = BARS.values().iterator(); it.hasNext(); ) {
            Bar b = it.next();
            if (b.scriptId != scriptId) continue;
            b.event.removeAllPlayers();
            it.remove();
        }
    }

    // ------------------------------------------------------------------ leap

    private static final class Leap extends Move {
        final Vec3 from, to;
        final double height;
        final int len;
        final Fx impact;
        final double radius, push;
        final float damage;

        Leap(Entity e, int scriptId, Vec3 to, double height, int len, Fx impact, double radius, float damage, double push) {
            super(e, scriptId);
            this.from = e.position();
            this.to = to;
            this.height = height;
            this.len = Math.max(4, len);
            this.impact = impact;
            this.radius = radius;
            this.damage = damage;
            this.push = push;
        }

        @Override
        boolean step(ServerLevel lv, Entity e) {
            t++;
            double k = Math.min(1, t / (double) len);
            Vec3 p = from.lerp(to, k).add(0, 4 * height * k * (1 - k), 0);
            face(e, to.subtract(from));
            place(e, p);
            return k >= 1;
        }

        @Override
        void end(ServerLevel lv, Entity e) {
            if (t < len) return;
            e.setDeltaMovement(Vec3.ZERO);
            play(impact, to);
            if (radius > 0) Mechanics.shockwave(lv, to, radius, push, damage, e, e);
        }
    }

    /**
     * Jumps along an arc to a point (or a body): height of the arc, time in the air. Landing plays the
     * impact effect and, with radius, throws everyone around back and hurts them.
     */
    public static void leap(Entity e, int scriptId, Vec3 to, double height, int ticks, Fx impact, double radius, float damage, double push) {
        begin(e, new Leap(e, scriptId, WorldApi.ground(to), height, ticks, impact, radius, damage, push), ticks);
    }

    // ------------------------------------------------------------------ charge

    private static final class Charge extends Move {
        final Vec3 dir;
        final double step, distance, push;
        final float damage;
        final Fx trail;
        final Set<UUID> hit = new HashSet<>();
        double gone;

        Charge(Entity e, int scriptId, Vec3 dir, double speed, double distance, float damage, double push, Fx trail) {
            super(e, scriptId);
            this.dir = dir;
            this.step = Math.max(0.05, speed / 20.0);
            this.distance = distance;
            this.damage = damage;
            this.push = push;
            this.trail = trail;
        }

        @Override
        boolean step(ServerLevel lv, Entity e) {
            t++;
            Vec3 next = e.position().add(dir.scale(step));
            Vec3 floor = WorldApi.ground(new Vec3(next.x, e.getY() + 1.2, next.z));
            // a wall or a drop: the charge ends there
            if (Math.abs(floor.y - e.getY()) > 1.2) return true;
            next = new Vec3(next.x, floor.y, next.z);
            if (!lv.noCollision(e, e.getBoundingBox().move(next.subtract(e.position())).deflate(0.05))) return true;
            face(e, dir);
            place(e, next);
            gone += step;
            AABB box = e.getBoundingBox().inflate(0.4);
            for (Entity v : lv.getEntities(e, box, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
                if (!hit.add(v.getUUID())) continue;
                hurt(lv, v, e, damage);
                v.push(dir.x * push, 0.25 + push * 0.2, dir.z * push);
                v.hurtMarked = true;
            }
            if (trail != null && t % 3 == 0) play(trail, e.position());
            return gone >= distance;
        }

        @Override
        void end(ServerLevel lv, Entity e) {
            e.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Rushes in a straight line toward a point (and that far past it), hitting each body in the way once. */
    public static void charge(Entity e, int scriptId, Vec3 toward, double speed, double distance, float damage, double push, Fx trail) {
        Vec3 d = toward.subtract(e.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        if (flat.lengthSqr() < 1e-6) flat = Vec3.directionFromRotation(0, e.getYRot());
        double dist = distance > 0 ? distance : flat.length() + 2;
        flat = flat.normalize();
        int ticks = (int) Math.ceil(dist / Math.max(0.05, speed / 20.0)) + 2;
        begin(e, new Charge(e, scriptId, flat, speed, dist, damage, push, trail), ticks);
    }

    // ------------------------------------------------------------------ circle

    private static final class Circle extends Move {
        final UUID centerId;
        final Vec3 centerPoint;
        final double radius, degPerTick;
        final int len;
        double angle;

        Circle(Entity e, int scriptId, Entity center, Vec3 point, double radius, double degPerSecond, int len) {
            super(e, scriptId);
            this.centerId = center != null ? center.getUUID() : null;
            this.centerPoint = point;
            this.radius = Math.max(1, radius);
            this.degPerTick = degPerSecond / 20.0;
            this.len = Math.max(1, len);
            Vec3 c = center != null ? center.position() : point;
            this.angle = Math.toDegrees(Math.atan2(e.getZ() - c.z, e.getX() - c.x));
        }

        @Override
        boolean step(ServerLevel lv, Entity e) {
            t++;
            Entity ce = centerId != null ? find(centerId) : null;
            Vec3 c = ce != null ? ce.position() : centerPoint;
            if (c == null) return true;
            angle += degPerTick;
            double a = Math.toRadians(angle);
            Vec3 want = new Vec3(c.x + Math.cos(a) * radius, e.getY() + 1.2, c.z + Math.sin(a) * radius);
            Vec3 floor = WorldApi.ground(want);
            // not further than a stride per tick: the body walks around, it does not jump through walls
            Vec3 to = new Vec3(floor.x, floor.y, floor.z);
            Vec3 d = to.subtract(e.position());
            double max = 0.6;
            if (d.length() > max) to = e.position().add(d.normalize().scale(max));
            if (Math.abs(floor.y - e.getY()) <= 1.2) place(e, new Vec3(to.x, floor.y, to.z));
            face(e, c.subtract(e.position()));
            return t >= len;
        }
    }

    /** Walks around a body or a point at this radius (degrees per second, minus — the other way) for a while. */
    public static void circle(Entity e, int scriptId, Entity center, Vec3 point, double radius, double degPerSecond, int ticks) {
        begin(e, new Circle(e, scriptId, center, point, radius, degPerSecond, ticks), ticks);
    }

    // ================================================================== delayed strikes

    private static final class Strike {
        final ServerLevel lv;
        final Vec3 at;
        final double radius, push;
        final float damage;
        final int due, scriptId;
        final Fx impact;
        final UUID by, except;

        Strike(ServerLevel lv, Vec3 at, double radius, int due, float damage, double push, Fx impact, Entity by, Entity except, int scriptId) {
            this.lv = lv;
            this.at = at;
            this.radius = radius;
            this.due = due;
            this.damage = damage;
            this.push = push;
            this.impact = impact;
            this.by = by != null ? by.getUUID() : null;
            this.except = except != null ? except.getUUID() : null;
            this.scriptId = scriptId;
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    /**
     * A blow that comes to a place after a delay: the warning effect plays at once, the impact effect and
     * the hit (damage and a push away from the centre) when the time is up. Who strikes is not hurt.
     */
    public static void strike(ServerLevel lv, Vec3 at, double radius, int delayTicks, float damage, double push, Fx warn, Fx impact,
                              Entity by, Entity except, int scriptId) {
        play(warn, at);
        STRIKES.add(new Strike(lv, at, radius, now() + Math.max(0, delayTicks), damage, push, impact, by, except, scriptId));
    }

    private static void tickStrikes() {
        if (STRIKES.isEmpty()) return;
        int n = now();
        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.due > n) continue;
            it.remove();
            play(s.impact, s.at);
            Entity by = s.by != null ? find(s.by) : null;
            Entity except = s.except != null ? find(s.except) : by;
            Mechanics.shockwave(s.lv, s.at, s.radius, s.push, s.damage, except, by);
        }
    }

    // ================================================================== beams

    private static final class Beam {
        final UUID from;
        final UUID targetId;
        final Vec3 targetPoint;
        final int until, scriptId;
        final double range, width, turnPerTick;
        final float dps;
        final ParticleOptions particle;
        final Fx impact;
        Vec3 dir;
        int t;

        Beam(Entity from, Entity target, Vec3 point, int ticks, float dps, double range, double width, double turnDegPerSecond,
             ParticleOptions particle, Fx impact, int scriptId) {
            this.from = from.getUUID();
            this.targetId = target != null ? target.getUUID() : null;
            this.targetPoint = point;
            this.until = now() + Math.max(1, ticks);
            this.dps = dps;
            this.range = range;
            this.width = width;
            this.turnPerTick = turnDegPerSecond <= 0 ? 360 : turnDegPerSecond / 20.0;
            this.particle = particle;
            this.impact = impact;
            this.scriptId = scriptId;
        }
    }

    private static final List<Beam> BEAMS = new ArrayList<>();

    private static ParticleOptions particle(String id) {
        if (id == null || id.isBlank()) return ParticleTypes.END_ROD;
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        var type = rl != null ? BuiltInRegistries.PARTICLE_TYPE.get(rl) : null;
        if (type instanceof SimpleParticleType s) return s;
        throw new SprauteRuntimeException("beam: частица «" + id + "» не подходит (нужна простая: end_rod, flame, soul_fire_flame, electric_spark…)");
    }

    /**
     * A ray from a body's eyes toward a target, clipped by blocks: hurts those it touches (damage per
     * second), turns after a moving target no faster than track degrees a second (0 — at once).
     */
    public static void beam(Entity from, Entity target, Vec3 point, int ticks, float dps, double range, double width, double track,
                            String particle, Fx impact, int scriptId) {
        BEAMS.removeIf(b -> b.from.equals(from.getUUID()));
        BEAMS.add(new Beam(from, target, point, ticks, dps, range, width, track, particle(particle), impact, scriptId));
    }

    /** Some beam is still burning from this body. */
    public static boolean beaming(Entity e) {
        for (Beam b : BEAMS) if (b.from.equals(e.getUUID())) return true;
        return false;
    }

    private static Vec3 turn(Vec3 cur, Vec3 want, double maxDeg) {
        double cos = Math.max(-1, Math.min(1, cur.dot(want)));
        double ang = Math.toDegrees(Math.acos(cos));
        if (ang <= maxDeg || ang < 1e-3) return want;
        double k = maxDeg / ang;
        return cur.lerp(want, k).normalize();
    }

    private static void tickBeams() {
        if (BEAMS.isEmpty()) return;
        int n = now();
        for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
            Beam b = it.next();
            Entity src = find(b.from);
            if (src == null || !src.isAlive() || n >= b.until || !(src.level() instanceof ServerLevel lv)) {
                it.remove();
                continue;
            }
            b.t++;
            Vec3 eye = src.getEyePosition();
            Entity te = b.targetId != null ? find(b.targetId) : null;
            Vec3 aim = te != null ? te.getBoundingBox().getCenter() : b.targetPoint;
            if (aim == null) {
                it.remove();
                continue;
            }
            Vec3 want = aim.subtract(eye);
            want = want.lengthSqr() < 1e-6 ? src.getLookAngle() : want.normalize();
            b.dir = b.dir == null ? want : turn(b.dir, want, b.turnPerTick);
            Vec3 end = eye.add(b.dir.scale(b.range));
            var hitBlock = lv.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, src));
            if (hitBlock.getType() != HitResult.Type.MISS) end = hitBlock.getLocation();
            face(src, b.dir);
            double len = end.distanceTo(eye);
            for (double d = 0.6; d < len; d += 0.45) {
                Vec3 p = eye.add(b.dir.scale(d));
                lv.sendParticles(b.particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
            if (b.t % 5 == 0) {
                AABB area = new AABB(eye, end).inflate(b.width + 1);
                for (Entity v : lv.getEntities(src, area, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
                    if (v.getBoundingBox().inflate(b.width).clip(eye, end).isPresent()) hurt(lv, v, src, b.dps / 4f);
                }
            }
            if (b.t % 10 == 1) play(b.impact, end);
        }
    }

    // ================================================================== boss bars that follow health

    private static final class Bar {
        final ServerBossEvent event;
        final int scriptId;

        Bar(ServerBossEvent event, int scriptId) {
            this.event = event;
            this.scriptId = scriptId;
        }
    }

    private static final Map<UUID, Bar> BARS = new LinkedHashMap<>();

    private static BossEvent.BossBarColor color(String c) {
        if (c == null) return BossEvent.BossBarColor.RED;
        return switch (c.trim().toLowerCase()) {
            case "pink", "розовый" -> BossEvent.BossBarColor.PINK;
            case "blue", "синий" -> BossEvent.BossBarColor.BLUE;
            case "green", "зелёный", "зеленый" -> BossEvent.BossBarColor.GREEN;
            case "yellow", "жёлтый", "желтый" -> BossEvent.BossBarColor.YELLOW;
            case "purple", "фиолетовый" -> BossEvent.BossBarColor.PURPLE;
            case "white", "белый" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    private static BossEvent.BossBarOverlay overlay(String s) {
        if (s == null) return BossEvent.BossBarOverlay.PROGRESS;
        return switch (s.trim()) {
            case "6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }

    /**
     * A boss bar over the screen that follows the body's health by itself: the fight's health while it
     * fights, else its own. Goes away when the body dies, is beaten or leaves the world.
     */
    public static void bar(Entity e, String title, String color, String style, int scriptId) {
        hideBar(e);
        String name = title != null && !title.isBlank() ? title : e.getName().getString();
        ServerBossEvent ev = new ServerBossEvent(Component.literal(name), color(color), overlay(style));
        for (ServerPlayer p : SprauteRuntime.audience(scriptId)) ev.addPlayer(p);
        ev.setProgress((float) frac(e));
        BARS.put(e.getUUID(), new Bar(ev, scriptId));
    }

    public static void hideBar(Entity e) {
        Bar b = BARS.remove(e.getUUID());
        if (b != null) b.event.removeAllPlayers();
    }

    /** Health of a body: the fight's while it fights, else its own (0 if it is not alive). */
    public static double hp(Entity e) {
        if (e == null) return 0;
        if (Fights.fighting(e) || Fights.defeated(e)) return Fights.hp(e);
        return e instanceof LivingEntity le ? Math.max(0, le.getHealth()) : 0;
    }

    public static double maxHp(Entity e) {
        if (e == null) return 0;
        if (Fights.fighting(e) || Fights.defeated(e)) return Fights.maxHp(e);
        return e instanceof LivingEntity le ? le.getMaxHealth() : 0;
    }

    public static double frac(Entity e) {
        double m = maxHp(e);
        return m <= 0 ? 0 : Math.max(0, Math.min(1, hp(e) / m));
    }

    private static void tickBars() {
        if (BARS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Bar> en = it.next();
            Entity e = find(en.getKey());
            ServerBossEvent ev = en.getValue().event;
            // unloaded far away: the bar stays as it was until the body is back
            if (e == null && !Fights.gone(en.getKey())) continue;
            if (e == null || !e.isAlive() || Fights.defeated(e)) {
                ev.removeAllPlayers();
                it.remove();
                continue;
            }
            ev.setProgress((float) frac(e));
            for (ServerPlayer p : SprauteRuntime.players()) if (!ev.getPlayers().contains(p)) ev.addPlayer(p);
        }
    }

    // ================================================================== the tick

    public static void onTick(Hooks.ServerTick ev) {
        if (ev.phase != TickPhase.END) return;
        try {
            if (!MOVES.isEmpty()) {
                for (Iterator<Map.Entry<UUID, Move>> it = MOVES.entrySet().iterator(); it.hasNext(); ) {
                    Move m = it.next().getValue();
                    Entity e = find(m.who);
                    if (e == null || !e.isAlive() || !(e.level() instanceof ServerLevel lv)) {
                        it.remove();
                        continue;
                    }
                    if (m.step(lv, e)) {
                        it.remove();
                        m.end(lv, e);
                        Fights.hold(e, 0);
                    }
                }
            }
            tickStrikes();
            tickBeams();
            if (now() % 2 == 0) tickBars();
        } catch (RuntimeException ex) {
            St_addon_spraute.LOGGER.warn("Spraute boss moves", ex);
        }
    }
}
