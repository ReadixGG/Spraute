package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** What characters keep doing on their own between story lines: following, wandering; and the clicks stories wait for. */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class Behaviours {
    private Behaviours() {
    }

    private interface Task {
        /** False — finished. */
        boolean tick(Mob m, int age);
    }

    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private static final Map<UUID, Entity> BODIES = new HashMap<>();
    private static final Map<UUID, Long> ENTITY_CLICKS = new HashMap<>();
    private static final Map<BlockPos, Long> BLOCK_CLICKS = new HashMap<>();
    private record Later(int[] left, Runnable r) {
    }
    private static final List<Later> LATER = new ArrayList<>();
    private static int age;

    private static void set(Entity e, Task t) {
        TASKS.put(e.getUUID(), t);
        BODIES.put(e.getUUID(), e);
    }

    public static void clear(Entity e) {
        if (e == null) return;
        TASKS.remove(e.getUUID());
        BODIES.remove(e.getUUID());
        if (e instanceof Mob m) m.getNavigation().stop();
    }

    public static void follow(Entity e, Supplier<? extends Entity> target, double speed, double min, double max) {
        set(e, (m, a) -> {
            Entity t = target.get();
            if (t == null || t.level() != m.level()) return true;
            double d = m.distanceTo(t);
            // too far behind: appears next to the target
            if (d > max) m.teleportTo(t.getX(), t.getY(), t.getZ());
            else if (d <= min) m.getNavigation().stop();
            else if (a % 10 == 0) m.getNavigation().moveTo(t, speed);
            m.getLookControl().setLookAt(t);
            return true;
        });
    }

    public static void wander(Entity e, double speed, double radius) {
        Vec3 home = e.position();
        set(e, (m, a) -> {
            if (a % 100 == 0 && m.getNavigation().isDone() && m.getRandom().nextInt(3) == 0) {
                double angle = m.getRandom().nextDouble() * Math.PI * 2, r = m.getRandom().nextDouble() * radius;
                m.getNavigation().moveTo(home.x + Math.cos(angle) * r, home.y, home.z + Math.sin(angle) * r, speed);
            }
            return true;
        });
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

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        age++;
        for (Iterator<Map.Entry<UUID, Task>> it = TASKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Task> en = it.next();
            Entity body = BODIES.get(en.getKey());
            if (!(body instanceof Mob m) || !m.isAlive() || !en.getValue().tick(m, age)) {
                it.remove();
                BODIES.remove(en.getKey());
            }
        }
        if (LATER.isEmpty()) return;
        for (Later l : new ArrayList<>(LATER)) {
            if (--l.left()[0] > 0) continue;
            LATER.remove(l);
            l.r().run();
        }
    }

    public static void unload() {
        TASKS.clear();
        BODIES.clear();
        ENTITY_CLICKS.clear();
        BLOCK_CLICKS.clear();
        LATER.clear();
    }
}
