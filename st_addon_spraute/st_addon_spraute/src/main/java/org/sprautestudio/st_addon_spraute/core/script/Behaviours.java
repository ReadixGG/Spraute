package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.path.Walkers;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * What characters keep doing on their own between story lines. One behaviour of each type at a time: a new one
 * replaces the old. Starts and ends are events for the stories. Also the clicks stories wait for.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class Behaviours {
    private Behaviours() {
    }

    public enum Type {
        MOVEMENT, LOOK, COMBAT, SPEECH, ACTION, INTERACTION, EMOTION, COMPLEX
    }

    private static final class Running {
        final Entity body;
        final Type type;
        /** False — finished. */
        final BooleanSupplier tick;
        final Runnable cancel;
        boolean paused;

        Running(Entity body, Type type, BooleanSupplier tick, Runnable cancel) {
            this.body = body;
            this.type = type;
            this.tick = tick;
            this.cancel = cancel;
        }
    }

    private static final Map<UUID, Map<Type, Running>> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> ENTITY_CLICKS = new HashMap<>();
    private static final Map<BlockPos, Long> BLOCK_CLICKS = new HashMap<>();
    private record Later(int[] left, Runnable r) {
    }
    private static final List<Later> LATER = new ArrayList<>();

    private static void event(String name, Running r) {
        Map<String, Object> d = new HashMap<>();
        d.put("type", r.type.name().toLowerCase(java.util.Locale.ROOT));
        d.put("name", r.body.getName().getString());
        d.put("__entity", r.body);
        GameEvents.fire(name, null, d);
    }

    /** Starts a behaviour; the one of its type that was going ends. */
    public static void start(Entity e, Type type, BooleanSupplier tick, Runnable cancel) {
        stop(e, type);
        Running r = new Running(e, type, tick, cancel);
        RUNNING.computeIfAbsent(e.getUUID(), k -> new EnumMap<>(Type.class)).put(type, r);
        event("behaviorstart", r);
    }

    public static void stop(Entity e, Type type) {
        if (e == null) return;
        Map<Type, Running> m = RUNNING.get(e.getUUID());
        Running r = m != null ? m.remove(type) : null;
        if (r == null) return;
        if (r.cancel != null) r.cancel.run();
        event("behaviorend", r);
    }

    /** All its behaviours end. */
    public static void clear(Entity e) {
        if (e == null) return;
        for (Type t : Type.values()) stop(e, t);
        Walkers.stop(e);
    }

    public static void setPaused(Entity e, Type type, boolean paused) {
        Map<Type, Running> m = e == null ? null : RUNNING.get(e.getUUID());
        Running r = m != null ? m.get(type) : null;
        if (r != null) r.paused = paused;
    }

    /** A walk is a movement behaviour: it ends with the walk. */
    public static void walking(Entity e) {
        start(e, Type.MOVEMENT, () -> Walkers.walking(e), () -> Walkers.stop(e));
    }

    /** Keeps between min and max from the target; inside min it stands, past max it appears next to it (max below 0 — never). */
    public static void follow(Entity e, Supplier<? extends Entity> target, double speed, double min, double max) {
        Vec3[] going = {null};
        Entity first = target.get();
        if (first != null) lookOnce(e, first.getEyePosition());
        start(e, Type.MOVEMENT, () -> {
            Entity t = target.get();
            if (t == null || t.level() != e.level()) return true;
            double d = e.distanceTo(t);
            if (max >= 0 && d > max) {
                Walkers.stop(e);
                going[0] = null;
                e.teleportTo(t.getX(), t.getY(), t.getZ());
            } else if (d <= min) {
                Walkers.stop(e);
                going[0] = null;
            } else if (going[0] == null || going[0].distanceToSqr(t.position()) > 1 || !Walkers.walking(e)) {
                going[0] = t.position();
                Walkers.go(e, going[0], speed, false);
            }
            return true;
        }, () -> Walkers.stop(e));
    }

    /** Walks to reachable points around where it stands now, with rare pauses. */
    public static void wander(Entity e, double speed, double radius) {
        Vec3 home = e.position();
        int[] pause = {0};
        start(e, Type.MOVEMENT, () -> {
            if (Walkers.walking(e) || --pause[0] > 0) return true;
            pause[0] = 20 + e.level().random.nextInt(100);
            if (!(e instanceof Mob m)) return true;
            double angle = e.level().random.nextDouble() * Math.PI * 2, r = e.level().random.nextDouble() * radius;
            double x = home.x + Math.cos(angle) * r, z = home.z + Math.sin(angle) * r;
            var path = m.getNavigation().createPath(x, home.y, z, 0);
            if (path != null && path.canReach()) {
                var end = path.getEndNode();
                if (end != null) Walkers.go(e, new Vec3(end.x + 0.5, end.y, end.z + 0.5), speed, false);
            }
            return true;
        }, () -> Walkers.stop(e));
    }

    public static void lookOnce(Entity e, Vec3 at) {
        Vec3 d = at.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.setYHeadRot(yaw);
        if (e instanceof net.minecraft.world.entity.LivingEntity le) le.yBodyRot = yaw;
    }

    public static void afterTicks(int ticks, Runnable r) {
        LATER.add(new Later(new int[]{ticks}, r));
    }

    public static long clicks(Entity e) {
        return e == null ? 0 : ENTITY_CLICKS.getOrDefault(e.getUUID(), 0L);
    }

    public static long clicks(BlockPos pos) {
        return BLOCK_CLICKS.getOrDefault(pos, 0L);
    }

    @SubscribeEvent
    public static void onEntityClick(PlayerInteractEvent.EntityInteract e) {
        if (!e.getLevel().isClientSide && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) ENTITY_CLICKS.merge(e.getTarget().getUUID(), 1L, Long::sum);
    }

    @SubscribeEvent
    public static void onBlockClick(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().isClientSide && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) BLOCK_CLICKS.merge(e.getPos(), 1L, Long::sum);
    }

    /** A saved body nobody claims, or an old copy of one made anew: it does not come back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && SprauteRuntime.env().isPresent() && !Characters.belongs(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide) Characters.died(e.getEntity());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Characters.tick(e.getServer());
        for (Map<Type, Running> m : new ArrayList<>(RUNNING.values())) {
            for (Running r : new ArrayList<>(m.values())) {
                if (r.paused) continue;
                if (!r.body.isAlive() || !r.tick.getAsBoolean()) {
                    m.remove(r.type);
                    event("behaviorend", r);
                }
            }
        }
        RUNNING.values().removeIf(Map::isEmpty);
        if (LATER.isEmpty()) return;
        for (Later l : new ArrayList<>(LATER)) {
            if (--l.left()[0] > 0) continue;
            LATER.remove(l);
            l.r().run();
        }
    }

    public static void unload() {
        RUNNING.clear();
        ENTITY_CLICKS.clear();
        BLOCK_CLICKS.clear();
        LATER.clear();
    }
}
