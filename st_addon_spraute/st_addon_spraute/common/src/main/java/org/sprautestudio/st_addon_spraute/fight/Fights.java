package org.sprautestudio.st_addon_spraute.fight;

import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.Anims;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.LoopMode;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.Platform;
import org.sprautestudio.st_addon_spraute.platform.Priority;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.*;

/**
 * The fights on the server. Every fighter is a character with a style: it chases its target, faces it,
 * picks an attack by weight, hits at the moment set in the style (checked by distance and angle),
 * rests; when hit it may dodge, block or flinch, and falls when its fight health runs out.
 * Its own hits go through the game (players and other mobs are hurt as usual); hits on it are
 * taken here, so even an invulnerable story character can fight.
 */
public final class Fights {
    public static void registerHooks() {
        Hooks.ENTITY_LEAVE.add(Fights::onLeave);
        Hooks.ENTITY_JOIN.add(Fights::onJoin);
        Hooks.SERVER_TICK.add(Fights::onTick);
        Hooks.LIVING_ATTACK.add(Priority.HIGH, Fights::onAttack);
    }

    private Fights() {
    }

    enum State { CHASE, ATTACK, BLOCK, DODGE, HURT, FLEE, DOWN }

    static final class Fighter {
        final UUID id;
        final String name;
        final int scriptId;
        final FightDef def;
        UUID target;
        float hp;
        State state = State.CHASE;
        int timer, length, windup, cooldown, pathAt;
        FightDef.Attack attack;
        boolean hitDone;
        /** The combat stance is playing now. */
        boolean stanceOn;
        String lastBy = "";
        ServerBossEvent bar;
        /** Damage of its attacks multiplied by this (fight tune … power). */
        float power = 1;
        /** Server tick until which the fight leaves the body alone: a boss move (leap, charge…) drives it. */
        int heldUntil;

        Fighter(UUID id, String name, int scriptId, FightDef def) {
            this.id = id;
            this.name = name;
            this.scriptId = scriptId;
            this.def = def;
            this.hp = def.health;
        }
    }

    private static final Map<UUID, Fighter> FIGHTERS = new LinkedHashMap<>();

    /**
     * Bodies that left the world for good (killed or removed), as opposed to only unloaded with their chunk
     * when the player went far: an unloaded fighter keeps its fight and comes back to it.
     */
    private static final Set<UUID> GONE = java.util.Collections.newSetFromMap(new java.util.LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 4096;
        }
    });

    /** The body is dead or removed for good (not just unloaded far away). */
    public static boolean gone(UUID id) {
        return GONE.contains(id);
    }

    public static void onLeave(Hooks.EntityLeave ev) {
        Entity e = ev.getEntity();
        if (e.level().isClientSide) return;
        Entity.RemovalReason why = e.getRemovalReason();
        if (why != null && why.shouldDestroy()) GONE.add(e.getUUID());
    }

    public static void onJoin(Hooks.EntityJoin ev) {
        if (!ev.getLevel().isClientSide) GONE.remove(ev.getEntity().getUUID());
    }
    /** Beaten fighters (entity -> name), kept for questions and "wait defeated". */
    private static final Map<UUID, String> DOWN = new HashMap<>();
    private static final Random RANDOM = new Random();
    /** Beaten with "die": disappear when the fall has played (ticks left). */
    private static final Map<Entity, Integer> VANISH = new HashMap<>();

    // ================================================================== styles

    public static FightDef style(String project, String name) {
        if (name == null || name.isBlank()) return FightDef.preset("fists");
        String json = SprauteRuntime.get().gui(project, "fight:" + name);
        if (json != null) return FightDef.fromJson(json);
        return FightDef.preset(name);
    }

    // ================================================================== the story's commands

    /** Starts (or restarts with a new style / target) a fight. */
    public static void start(Entity e, String name, int scriptId, FightDef def, Entity target) {
        if (!(e instanceof PathfinderMob)) throw new IllegalArgumentException(name + ": эта сущность не умеет драться (нужен живой персонаж)");
        Fighter old = FIGHTERS.get(e.getUUID());
        Fighter f = new Fighter(e.getUUID(), name, scriptId, def);
        if (old != null && old.def == def) f.hp = old.hp;
        if (old != null && old.bar != null) old.bar.removeAllPlayers();
        f.target = target != null ? target.getUUID() : null;
        DOWN.remove(e.getUUID());
        FIGHTERS.put(e.getUUID(), f);
        if (def.bossBar) {
            f.bar = new ServerBossEvent(Component.literal(def.bossName.isEmpty() ? name : def.bossName), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            for (ServerPlayer p : SprauteRuntime.audience(scriptId)) f.bar.addPlayer(p);
        }
        stance(e, f);
        post("fightstart", f, Map.of());
    }

    public static void stop(Entity e) {
        Fighter f = FIGHTERS.remove(e.getUUID());
        if (f == null) return;
        end(e, f);
    }

    private static void end(Entity e, Fighter f) {
        if (f.bar != null) f.bar.removeAllPlayers();
        if (e instanceof Mob m) m.getNavigation().stop();
        if (e instanceof AnimatedEntity ae && f.state != State.DOWN) play(ae, "", false, false);
    }

    /** The story stopped: its fights end too. */
    public static void stopScript(int scriptId) {
        MinecraftServer s = Platform.get().server();
        for (Iterator<Fighter> it = FIGHTERS.values().iterator(); it.hasNext(); ) {
            Fighter f = it.next();
            if (f.scriptId != scriptId) continue;
            it.remove();
            Entity e = s != null ? find(s, f.id) : null;
            if (e != null) end(e, f);
            else if (f.bar != null) f.bar.removeAllPlayers();
        }
    }

    /** Back on its feet with full (or this much) fight health. */
    public static void heal(Entity e, float amount) {
        Fighter f = FIGHTERS.get(e.getUUID());
        if (f == null) return;
        f.hp = amount < 0 ? f.def.health : Math.min(f.def.health, f.hp + amount);
        if (f.state == State.DOWN) {
            f.state = State.CHASE;
            DOWN.remove(e.getUUID());
            if (e instanceof AnimatedEntity ae) stance(ae, f);
        }
        if (f.bar != null) f.bar.setProgress(f.hp / f.def.health);
    }

    public static boolean fighting(Entity e) {
        Fighter f = FIGHTERS.get(e.getUUID());
        return f != null && f.state != State.DOWN;
    }

    public static boolean defeated(Entity e) {
        return DOWN.containsKey(e.getUUID());
    }

    public static double hp(Entity e) {
        Fighter f = FIGHTERS.get(e.getUUID());
        return f == null ? 0 : Math.max(0, f.hp);
    }

    /** Full fight health of its style (0 — not fighting). */
    public static double maxHp(Entity e) {
        Fighter f = FIGHTERS.get(e.getUUID());
        return f == null ? 0 : f.def.health;
    }

    /** The fight leaves the body to someone else (a boss move) for this many ticks; 0 — gives it back. */
    public static void hold(Entity e, int ticks) {
        Fighter f = FIGHTERS.get(e.getUUID());
        if (f == null) return;
        MinecraftServer s = Platform.get().server();
        f.heldUntil = ticks <= 0 || s == null ? 0 : s.getTickCount() + ticks;
        if (ticks > 0 && e instanceof Mob m) m.getNavigation().stop();
    }

    /**
     * Changes a fight on the go (a boss's second phase): armor 0..1, chase speed, attack power (damage
     * multiplier), stagger / block / dodge chances 0..1. Null leaves a value as it is.
     */
    public static boolean tune(Entity e, Double armor, Double speed, Double power, Double stagger, Double block, Double dodge) {
        Fighter f = FIGHTERS.get(e.getUUID());
        if (f == null) return false;
        if (armor != null) f.def.armor = (float) Math.max(0, Math.min(1, armor));
        if (speed != null) f.def.speed = (float) Math.max(0, speed);
        if (power != null) f.power = (float) Math.max(0, power);
        if (stagger != null) f.def.staggerChance = (float) Math.max(0, Math.min(1, stagger));
        if (block != null) f.def.block.chance = (float) Math.max(0, Math.min(1, block));
        if (dodge != null) f.def.dodge.chance = (float) Math.max(0, Math.min(1, dodge));
        return true;
    }

    public static void unload() {
        for (Fighter f : FIGHTERS.values()) if (f.bar != null) f.bar.removeAllPlayers();
        FIGHTERS.clear();
        DOWN.clear();
        VANISH.clear();
    }

    // ================================================================== animations

    private static Optional<Animation> anim(Entity e, String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try {
            if (e instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity me) {
                var look = org.sprautestudio.st_addon_spraute.runtime.models.EntityLibrary.look(me.model());
                if (look != null) {
                    // a model's own copy of a built-in clip first: its moves are in the pixels of this body
                    int bang = id.indexOf('!');
                    Optional<Animation> a = bang >= 0 ? look.animation(id.substring(bang + 1)) : Optional.empty();
                    if (a.isEmpty()) a = look.animation(id);
                    if (a.isPresent()) return a;
                }
            }
            return Anims.resolve(id);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Plays on the action layer; returns its length in ticks (0 — nothing played). */
    public static int play(Entity e, String id, boolean loop, boolean hold) {
        if (!(e instanceof AnimatedEntity ae)) return 0;
        try {
            if (id == null || id.isBlank()) {
                ae.startAnimation("action", Animation.NULL.configure().transitionTime(0.2f));
                return 0;
            }
            Optional<Animation> a = anim(e, id);
            if (a.isEmpty()) return 0;
            ConfiguredAnimation ca = a.get().configure().transitionTime(0.12f);
            if (loop) ca.loopMode(LoopMode.LOOP);
            else if (hold) ca.loopMode(LoopMode.HOLD_ON_LAST_FRAME);
            ae.startAnimation("action", ca);
            return Math.max(1, Anims.lengthTicks(a.get(), 1));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static void stance(Entity e, Fighter f) {
        f.stanceOn = !f.def.idle.isBlank() && play(e, f.def.idle, true, false) > 0;
    }

    /** Walking: the action layer is let go so the walk shows (unless the style keeps its stance on the move). */
    private static void moving(Entity e, Fighter f) {
        if (f.stanceOn && !f.def.stanceMoving) {
            play(e, "", false, false);
            f.stanceOn = false;
        }
    }

    private static void sound(Entity e, String id, float pitch) {
        if (id == null || id.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        SoundEvent s = rl == null ? null : BuiltInRegistries.SOUND_EVENT.get(rl);
        if (s != null) e.level().playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.HOSTILE, 1, pitch);
    }

    // ================================================================== every tick

    private static Entity find(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public static void onTick(Hooks.ServerTick ev) {
        if (ev.phase != TickPhase.END) return;
        if (!VANISH.isEmpty()) {
            for (Iterator<Map.Entry<Entity, Integer>> it = VANISH.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Entity, Integer> v = it.next();
                v.setValue(v.getValue() - 1);
                if (v.getValue() > 0) continue;
                it.remove();
                Entity e = v.getKey();
                if (e.isAlive() && e.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                    e.discard();
                }
            }
        }
        if (FIGHTERS.isEmpty()) return;
        MinecraftServer s = Platform.get().server();
        if (s == null) return;
        for (Fighter f : new ArrayList<>(FIGHTERS.values())) {
            Entity e = find(s, f.id);
            // unloaded with its chunk (the player went far, died and came back far away): the fight waits for it
            if (e == null && !GONE.contains(f.id)) continue;
            if (e == null || !e.isAlive()) {
                FIGHTERS.remove(f.id);
                if (f.bar != null) f.bar.removeAllPlayers();
                continue;
            }
            if (f.heldUntil > s.getTickCount()) continue;
            try {
                tick(s, (PathfinderMob) e, f);
            } catch (RuntimeException ex) {
                St_addon_spraute.LOGGER.warn("Spraute fight of {}", f.name, ex);
                FIGHTERS.remove(f.id);
                end(e, f);
            }
        }
    }

    private static LivingEntity target(MinecraftServer s, Fighter f) {
        if (f.target == null) {
            // the nearest of those the fight is for
            ServerPlayer best = null;
            Entity self = find(s, f.id);
            for (ServerPlayer p : SprauteRuntime.audience(f.scriptId)) {
                if (p.isSpectator() || !p.isAlive() || self != null && p.level() != self.level()) continue;
                if (best == null || self != null && p.distanceToSqr(self) < best.distanceToSqr(self)) best = p;
            }
            return best;
        }
        Entity t = find(s, f.target);
        return t instanceof LivingEntity l ? l : null;
    }

    private static void face(Mob e, Entity t) {
        double dx = t.getX() - e.getX(), dz = t.getZ() - e.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        e.setYRot(yaw);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
        e.getLookControl().setLookAt(t, 30, 30);
    }

    /** Walks toward (or away from) a point: with the game's pathfinding when the mob thinks, else straight. */
    private static void walk(PathfinderMob e, Fighter f, Vec3 to, Entity chase) {
        double speed = f.def.speed;
        if (!e.isNoAi()) {
            if (f.pathAt-- <= 0 || e.getNavigation().isDone()) {
                f.pathAt = 8;
                if (chase != null) e.getNavigation().moveTo(chase, speed);
                else e.getNavigation().moveTo(to.x, to.y, to.z, speed);
            }
            return;
        }
        Vec3 d = to.subtract(e.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        if (flat.lengthSqr() < 0.04) return;
        Vec3 v = flat.normalize().scale(0.13 * speed);
        double vy = e.getDeltaMovement().y;
        if (e.horizontalCollision && e.onGround()) vy = 0.42;
        e.setDeltaMovement(v.x, vy, v.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        e.setYRot(yaw);
        e.setYBodyRot(yaw);
    }

    private static void stopWalking(PathfinderMob e) {
        if (!e.isNoAi()) e.getNavigation().stop();
        else e.setDeltaMovement(0, e.getDeltaMovement().y, 0);
    }

    private static FightDef.Attack pick(FightDef def) {
        int total = 0;
        for (FightDef.Attack a : def.attacks) total += a.weight;
        int r = RANDOM.nextInt(Math.max(1, total));
        for (FightDef.Attack a : def.attacks) {
            r -= a.weight;
            if (r < 0) return a;
        }
        return def.attacks.get(0);
    }

    private static void tick(MinecraftServer s, PathfinderMob e, Fighter f) {
        if (f.bar != null) {
            f.bar.setProgress(Math.max(0, f.hp / f.def.health));
            for (ServerPlayer p : SprauteRuntime.audience(f.scriptId)) if (!f.bar.getPlayers().contains(p)) f.bar.addPlayer(p);
        }
        if (f.state == State.DOWN) return;
        LivingEntity t = target(s, f);
        // a player who died (and is coming back), went to another world or ran far: the fighter waits, the fight is not over
        if (t instanceof ServerPlayer sp && !sp.isSpectator() && (!sp.isAlive() || sp.isRemoved() || sp.level() != e.level()
                || sp.distanceTo(e) > f.def.giveUp)) {
            stopWalking(e);
            return;
        }
        if (t == null && f.target != null && !GONE.contains(f.target)) {
            // the target is a player who is respawning right now (no body for a moment)
            stopWalking(e);
            return;
        }
        boolean targetGone = t == null || !t.isAlive() || t.isRemoved() || (t instanceof ServerPlayer sp && sp.isSpectator())
                || defeated(t) || (t != null && t.level() != e.level()) || (t != null && t.distanceTo(e) > f.def.giveUp);
        if (targetGone) {
            post("fightwin", f, t != null ? Map.of("target", nameOf(t)) : Map.of());
            FIGHTERS.remove(f.id);
            end(e, f);
            return;
        }
        if (f.cooldown > 0) f.cooldown--;
        double dist = e.distanceTo(t);
        f.timer++;
        switch (f.state) {
            case ATTACK -> {
                f.stanceOn = false;
                stopWalking(e);
                if (!f.hitDone) face(e, t);
                if (!f.hitDone && f.timer >= f.windup) {
                    f.hitDone = true;
                    if (f.attack.lunge > 0) {
                        Vec3 dir = t.position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(f.attack.lunge * 0.35);
                        e.setDeltaMovement(dir.x, e.getDeltaMovement().y, dir.z);
                    }
                    hit(e, f, t);
                }
                if (f.timer >= f.length) back(e, f);
            }
            case BLOCK, DODGE, HURT -> {
                if (f.state != State.DODGE) stopWalking(e);
                face(e, t);
                if (f.timer >= f.length) back(e, f);
            }
            case FLEE -> {
                Vec3 away = e.position().subtract(t.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
                if (dist < 14) walk(e, f, e.position().add(away.scale(10)), null);
                else stopWalking(e);
            }
            default -> {
                if (f.def.fleeAt > 0 && f.hp < f.def.health * f.def.fleeAt) {
                    f.state = State.FLEE;
                    play(e, "", false, false);
                    post("fightflee", f, Map.of());
                    return;
                }
                double reach = minRange(f.def) + t.getBbWidth() / 2;
                if (dist > reach * 0.9) {
                    moving(e, f);
                    walk(e, f, t.position(), t);
                } else {
                    stopWalking(e);
                    face(e, t);
                    if (!f.stanceOn) stance(e, f);
                    if (f.cooldown <= 0) attack(e, f);
                }
            }
        }
    }

    private static double minRange(FightDef d) {
        double m = 99;
        for (FightDef.Attack a : d.attacks) m = Math.min(m, a.range);
        return m == 99 ? 2 : m;
    }

    private static void attack(PathfinderMob e, Fighter f) {
        FightDef.Attack a = pick(f.def);
        f.attack = a;
        f.state = State.ATTACK;
        f.timer = 0;
        f.hitDone = false;
        int len = play(e, a.anim, false, false);
        f.length = a.duration > 0 ? a.duration : Math.max(8, len > 0 ? len : 12);
        f.windup = a.windup > 0 ? Math.min(a.windup, f.length) : Math.max(3, Math.round(f.length * 0.45f));
        if (len == 0 && a.anim.isBlank()) e.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static void back(PathfinderMob e, Fighter f) {
        if (f.state == State.ATTACK && f.attack != null) f.cooldown = f.attack.cooldown;
        f.state = State.CHASE;
        f.timer = 0;
        stance(e, f);
    }

    /** The moment of the hit: lands if the target is still in reach and in front. */
    private static void hit(PathfinderMob e, Fighter f, LivingEntity t) {
        FightDef.Attack a = f.attack;
        double reach = a.range + t.getBbWidth() / 2 + 0.3;
        if (e.distanceTo(t) > reach) {
            sound(e, "minecraft:entity.player.attack.nodamage", 1);
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0, e.getYRot());
        Vec3 to = t.position().subtract(e.position()).multiply(1, 0, 1);
        if (to.lengthSqr() > 0.01 && look.dot(to.normalize()) < 0.3) {
            sound(e, "minecraft:entity.player.attack.nodamage", 1);
            return;
        }
        sound(e, a.sound.isBlank() ? f.def.hitSound : a.sound, 0.9f + RANDOM.nextFloat() * 0.2f);
        t.hurt(e.level().damageSources().mobAttack(e), a.damage * f.power);
        if (a.knockback > 0) {
            t.knockback(a.knockback, e.getX() - t.getX(), e.getZ() - t.getZ());
            t.hurtMarked = true;
        }
    }

    // ================================================================== being hit

    public static void onAttack(Hooks.LivingAttack ev) {
        LivingEntity e = ev.getEntity();
        if (e.level().isClientSide) return;
        Fighter f = FIGHTERS.get(e.getUUID());
        if (f == null) return;
        ev.setCanceled(true);
        if ((org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.flags(e) & org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.NODAMAGE) != 0) return;
        if (f.state == State.DOWN || !(e instanceof PathfinderMob m)) return;
        Entity by = ev.getSource().getEntity();
        // a fighter's own target is the one it answers; anyone else hitting it becomes the target
        if (by instanceof LivingEntity lb && lb != e && f.target != null && !lb.getUUID().equals(f.target) && by instanceof ServerPlayer) f.target = lb.getUUID();
        f.lastBy = by != null ? nameOf(by) : "";
        float dmg = ev.getAmount();
        boolean busy = f.state == State.ATTACK && !f.hitDone && f.timer > 2;
        // dodge
        if (!busy && f.def.dodge.chance > 0 && RANDOM.nextFloat() < f.def.dodge.chance) {
            f.state = State.DODGE;
            f.timer = 0;
            int len = play(m, f.def.dodge.anim, false, false);
            f.length = Math.max(6, len > 0 ? len : 8);
            Vec3 side = by != null ? e.position().subtract(by.position()).multiply(1, 0, 1) : Vec3.ZERO;
            if (side.lengthSqr() < 0.01) side = Vec3.directionFromRotation(0, e.getYRot() + 90);
            side = side.normalize().yRot((RANDOM.nextBoolean() ? 1 : -1) * 1.2f).scale(f.def.dodge.power * 0.3);
            e.setDeltaMovement(side.x, 0.2, side.z);
            sound(e, "minecraft:entity.player.attack.nodamage", 1.3f);
            post("fightdodge", f, Map.of("by", f.lastBy));
            return;
        }
        boolean blocked = false;
        if (!busy && f.def.block.chance > 0 && RANDOM.nextFloat() < f.def.block.chance) {
            blocked = true;
            dmg *= 1 - Math.max(0, Math.min(1, f.def.block.power));
            f.state = State.BLOCK;
            f.timer = 0;
            int len = play(m, f.def.block.anim, false, false);
            f.length = Math.max(6, Math.min(20, len > 0 ? len : 10));
            sound(e, "minecraft:item.shield.block", 0.9f + RANDOM.nextFloat() * 0.2f);
        }
        dmg *= 1 - f.def.armor;
        f.hp -= dmg;
        e.level().broadcastDamageEvent(e, ev.getSource());
        if (by != null) e.knockback(blocked ? 0.15 : 0.35, by.getX() - e.getX(), by.getZ() - e.getZ());
        Map<String, Object> d = new HashMap<>();
        d.put("by", f.lastBy);
        d.put("damage", Math.round(dmg * 10) / 10.0);
        d.put("hp", Math.round(Math.max(0, f.hp) * 10) / 10.0);
        d.put("blocked", blocked);
        post("fighthit", f, d);
        if (f.hp <= 0) {
            defeat(m, f);
            return;
        }
        // flinch: staggerChance also breaks an attack being swung
        if (!blocked && RANDOM.nextFloat() < f.def.staggerChance) {
            int len = play(m, f.def.hurt, false, false);
            if (len > 0 || f.state == State.ATTACK) {
                f.state = State.HURT;
                f.timer = 0;
                f.length = Math.max(5, Math.min(25, len > 0 ? len : 6));
            }
        }
    }

    private static void defeat(PathfinderMob e, Fighter f) {
        f.state = State.DOWN;
        f.hp = 0;
        stopWalking(e);
        DOWN.put(f.id, f.name);
        if (f.bar != null) f.bar.removeAllPlayers();
        int len = play(e, f.def.defeat, false, true);
        sound(e, "minecraft:entity.player.hurt", 0.7f);
        post("fightdown", f, Map.of("by", f.lastBy));
        switch (f.def.defeatMode) {
            case "die" -> {
                FIGHTERS.remove(f.id);
                // beaten to death in a fight: stories hear it as a death too
                Map<String, Object> d = new HashMap<>();
                d.put("__entity", e);
                d.put("entity", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())));
                d.put("name", e.getName().getString());
                d.put("by", f.lastBy);
                d.put("killer", f.lastBy);
                d.put("source", "fight");
                d.put("x", e.getX());
                d.put("y", e.getY());
                d.put("z", e.getZ());
                GameEvents.post("npcdeath", SprauteRuntime.playerOf(f.scriptId), d);
                VANISH.put(e, Math.max(10, len) + 10);
            }
            case "stand" -> {
                FIGHTERS.remove(f.id);
                end(e, f);
            }
            default -> {
                // knockout: lies on its last frame until the story heals it or stops the fight
            }
        }
    }

    // ================================================================== telling the stories

    private static String nameOf(Entity e) {
        if (e instanceof ServerPlayer p) return "player";
        Fighter f = FIGHTERS.get(e.getUUID());
        if (f != null) return f.name;
        String dn = DOWN.get(e.getUUID());
        if (dn != null) return dn;
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    private static void post(String type, Fighter f, Map<String, Object> extra) {
        Map<String, Object> d = new HashMap<>(extra);
        d.put("who", f.name);
        d.putIfAbsent("hp", Math.round(Math.max(0, f.hp) * 10) / 10.0);
        GameEvents.post(type, SprauteRuntime.playerOf(f.scriptId), d);
    }
}
