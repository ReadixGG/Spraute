package org.sprautestudio.spraute_engine.hitbox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.Priority;
import org.sprautestudio.spraute_engine.platform.TickPhase;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * How a character's body behaves: solid like a wall, can't be pushed, walked through like a ghost,
 * can't be aimed at; takes damage or not, can die or not. Kept in the entity's own data (so it
 * survives saving) and sent to the players, since collisions are also worked out on their side.
 * The game's checks read it through small mixins (EntityHitboxMixin, LivingEntityHitboxMixin).
 */
public final class Hitboxes {
    public static void registerHooks() {
        Hooks.SERVER_TICK.add(Hitboxes::onTick);
        Hooks.ENTITY_JOIN.add(Hitboxes::onJoin);
        Hooks.ENTITY_LEAVE.add(Hitboxes::onLeave);
        Hooks.START_TRACKING.add(Hitboxes::onTrack);
        Hooks.LIVING_ATTACK.add(Priority.HIGH, Hitboxes::onAttack);
    }

    private Hitboxes() {
    }

    /** Can't walk through it, can stand on it, can't push it. */
    public static final int SOLID = HitboxFlags.SOLID;
    /** Not moved by pushes (but still in the way). */
    public static final int FIXED = HitboxFlags.FIXED;
    /** Walked through: nobody pushes anybody. */
    public static final int GHOST = HitboxFlags.GHOST;
    /** Can't be aimed at or hit with the hand. */
    public static final int NOPICK = HitboxFlags.NOPICK;
    /** Takes damage (Spraute's characters don't, by default). */
    public static final int DAMAGE = HitboxFlags.DAMAGE;
    /** Never takes damage. */
    public static final int NODAMAGE = HitboxFlags.NODAMAGE;
    /** Can die from damage. */
    public static final int MORTAL = HitboxFlags.MORTAL;

    private static final String TAG = "SprauteHitbox";
    private static final Map<Integer, Integer> SERVER = new ConcurrentHashMap<>();
    /** Filled from the server's packets. */
    public static final Map<Integer, Integer> CLIENT = new ConcurrentHashMap<>();

    /** The flags of an entity on this side (0 — as the game makes it). */
    public static int flags(Entity e) {
        Map<Integer, Integer> m = e.level() != null && e.level().isClientSide ? CLIENT : SERVER;
        if (m.isEmpty()) return 0;
        Integer f = m.get(e.getId());
        return f == null ? 0 : f;
    }

    // ------------------------------------------------------------------ words of the stories

    public static int parse(int flags, List<String> words) {
        return HitboxFlags.parse(flags, words);
    }

    public static String describe(int f) {
        return HitboxFlags.describe(f);
    }

    // ------------------------------------------------------------------ setting (server)

    public static void set(Entity e, int flags) {
        if (flags == 0) SERVER.remove(e.getId());
        else SERVER.put(e.getId(), flags);
        Platform.get().persistentData(e).putInt(TAG, flags);
        applyDamage(e, flags);
        SprauteNetwork.sendToAll(new Packets.HitboxSync(e.getId(), flags));
    }

    /** Spraute's own switches for damage and death, when the entity has them. */
    private static void applyDamage(Entity e, int flags) {
        if (!(e instanceof org.sprautestudio.spraute_engine.core.entity.AnimatedEntity ae)) return;
        if ((flags & DAMAGE) != 0) ae.setVulnerable(true);
        else if ((flags & NODAMAGE) != 0) ae.setVulnerable(false);
        if ((flags & MORTAL) != 0) ae.canDie = true;
        else if ((flags & (DAMAGE | NODAMAGE)) != 0) ae.canDie = false;
    }

    private record Pending(Supplier<Entity> entity, List<String> words, int[] ttl) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** For a character just spawned: applies as soon as its entity is in the world (tries for 5 s). */
    public static void applyWhenReady(Supplier<Entity> entity, List<String> words) {
        PENDING.add(new Pending(entity, words, new int[]{100}));
    }

    public static void onTick(Hooks.ServerTick ev) {
        if (ev.phase != TickPhase.END || PENDING.isEmpty()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            Entity e;
            try {
                e = p.entity().get();
            } catch (RuntimeException ex) {
                e = null;
            }
            if (e != null) {
                try {
                    set(e, parse(flags(e), p.words()));
                } catch (IllegalArgumentException ignored) {
                }
                it.remove();
            } else if (--p.ttl()[0] <= 0) it.remove();
        }
    }

    // ------------------------------------------------------------------ keeping it

    public static void onJoin(Hooks.EntityJoin ev) {
        if (ev.getLevel().isClientSide) return;
        Entity e = ev.getEntity();
        if (!Platform.get().persistentData(e).contains(TAG)) return;
        int f = Platform.get().persistentData(e).getInt(TAG);
        if (f != 0) SERVER.put(e.getId(), f);
    }

    public static void onLeave(Hooks.EntityLeave ev) {
        if (ev.getLevel().isClientSide) CLIENT.remove(ev.getEntity().getId());
        else SERVER.remove(ev.getEntity().getId());
    }

    public static void onTrack(Hooks.StartTracking ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Integer f = SERVER.get(ev.getTarget().getId());
        if (f != null && f != 0) SprauteNetwork.sendTo(p, new Packets.HitboxSync(ev.getTarget().getId(), f));
    }

    /** "nodamage": hits don't land at all (even on entities that would take them). */
    public static void onAttack(Hooks.LivingAttack ev) {
        if (ev.getEntity().level().isClientSide || SERVER.isEmpty()) return;
        if ((flags(ev.getEntity()) & NODAMAGE) != 0 && !ev.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) ev.setCanceled(true);
    }

    public static void clearServer() {
        SERVER.clear();
        PENDING.clear();
    }
}
