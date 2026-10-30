package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.models.EntityLibrary;
import org.sprautestudio.st_addon_spraute.runtime.models.PartShape;
import org.sprautestudio.st_addon_spraute.runtime.models.SprautePart;
import org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Attacks by hitboxes: a box in front of a body (turned with it) or a part of a spraute model (riding its
 * bone) hurts whoever it touches while the blow lasts — everyone once. Parts can also hurt on contact.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class AttackBoxes {
    private AttackBoxes() {
    }

    private static final class Attack {
        final UUID who;
        final int scriptId, start, end;
        final String part;
        final Vector3f size, offset;
        final float damage;
        final double push;
        final UUID except;
        final BossMoves.Fx impact;
        final boolean show;
        final Set<UUID> hit = new HashSet<>();

        Attack(Entity who, String part, Vector3f size, Vector3f offset, float damage, double push, int start, int end, Entity except,
               BossMoves.Fx impact, boolean show, int scriptId) {
            this.who = who.getUUID();
            this.part = part;
            this.size = size;
            this.offset = offset;
            this.damage = damage;
            this.push = push;
            this.start = start;
            this.end = end;
            this.except = except != null ? except.getUUID() : null;
            this.impact = impact;
            this.show = show;
            this.scriptId = scriptId;
        }
    }

    private static final List<Attack> ATTACKS = new ArrayList<>();
    /** Who the last blow of a body hit: how many. */
    private static final Map<UUID, Integer> LAST_HITS = new HashMap<>();
    /** Contact damage of parts: when each victim may be hurt again. */
    private static final Map<String, Integer> CONTACT_READY = new HashMap<>();

    private static int now() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s != null ? s.getTickCount() : 0;
    }

    /**
     * A blow: after delay ticks the box is live for ticks ticks. part — a part of a spraute model (its box,
     * even if the part is switched off for being hit), else a box size (w, h, d) at offset (right, up, forward)
     * from the feet, turned with the body.
     */
    public static void attack(Entity who, String part, Vector3f size, Vector3f offset, float damage, double push, int delay, int ticks,
                              Entity except, BossMoves.Fx impact, boolean show, int scriptId) {
        if (part != null && !(who instanceof SprauteModelEntity me && me.partNames().contains(part)))
            throw new SprauteRuntimeException("attack: у " + who.getName().getString() + " нет части «" + part + "»"
                    + (who instanceof SprauteModelEntity me ? ". Есть: " + String.join(", ", me.partNames()) : " (части есть только у сущностей spraute:…)"));
        int s = now() + Math.max(0, delay);
        ATTACKS.add(new Attack(who, part, size, offset, damage, push, s, s + Math.max(1, ticks), except, impact, show, scriptId));
        LAST_HITS.put(who.getUUID(), 0);
    }

    /** A blow of this body is still waiting or live. */
    public static boolean active(Entity e) {
        if (e == null) return false;
        for (Attack a : ATTACKS) if (a.who.equals(e.getUUID())) return true;
        return false;
    }

    public static int hits(Entity e) {
        return e == null ? 0 : LAST_HITS.getOrDefault(e.getUUID(), 0);
    }

    public static void stop(Entity e) {
        ATTACKS.removeIf(a -> a.who.equals(e.getUUID()));
    }

    public static void stopScript(int scriptId) {
        ATTACKS.removeIf(a -> a.scriptId == scriptId);
    }

    // ------------------------------------------------------------------ boxes

    /** The box of a blow now, in the body's space. */
    private static PartShape shape(Entity e, Attack a) {
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        if (a.part != null && e instanceof SprauteModelEntity me) {
            List<EntityLibrary.Def.Part> defs = me.partList();
            for (int i = 0; i < defs.size(); i++) {
                if (!defs.get(i).name.equals(a.part)) continue;
                PartShape sh = me.shape(i);
                return sh != null ? sh : PartShape.of(defs.get(i), 1f, yaw, me.freshPose(defs.get(i).bone));
            }
            return null;
        }
        Quaternionf rot = new Quaternionf().rotationY((float) -Math.toRadians(yaw));
        // right is the body's -X: the game looks along +Z at yaw 0
        Vector3f center = rot.transform(new Vector3f(-a.offset.x, a.offset.y, a.offset.z));
        return new PartShape(center, rot, new Vector3f(a.size).mul(0.5f));
    }

    /** Whether a turned box (in the space of a body at origin) touches an upright box. */
    public static boolean touches(PartShape sh, Vec3 origin, AABB box) {
        AABB around = sh.bounds(origin.x, origin.y, origin.z);
        if (!around.intersects(box)) return false;
        Quaternionf inv = new Quaternionf(sh.rot()).conjugate();
        Vec3 c = box.getCenter().subtract(origin);
        Vector3f local = inv.transform(new Vector3f((float) c.x, (float) c.y, (float) c.z).sub(sh.center()));
        float hx = (float) box.getXsize() / 2, hy = (float) box.getYsize() / 2, hz = (float) box.getZsize() / 2;
        Vector3f[] axes = {inv.transform(new Vector3f(1, 0, 0)), inv.transform(new Vector3f(0, 1, 0)), inv.transform(new Vector3f(0, 0, 1))};
        float[] l = {local.x, local.y, local.z}, h = {sh.half().x, sh.half().y, sh.half().z};
        for (int k = 0; k < 3; k++) {
            float ext = Math.abs(axes[0].get(k)) * hx + Math.abs(axes[1].get(k)) * hy + Math.abs(axes[2].get(k)) * hz;
            if (Math.abs(l[k]) > h[k] + ext) return false;
        }
        return true;
    }

    private static boolean own(Entity body, Entity other) {
        return other == body || other instanceof SprautePart p && p.getParent() == body;
    }

    private static void hurt(ServerLevel lv, Entity by, Entity victim, float damage, double push) {
        if (damage > 0) {
            var src = by instanceof LivingEntity le ? lv.damageSources().mobAttack(le) : lv.damageSources().generic();
            victim.hurt(src, damage);
        }
        if (push > 0) {
            Vec3 d = victim.position().subtract(by.position());
            d = new Vec3(d.x, 0, d.z);
            d = d.lengthSqr() < 1e-6 ? by.getLookAngle() : d.normalize();
            victim.push(d.x * push, 0.3 * push, d.z * push);
            victim.hurtMarked = true;
        }
    }

    private static void outline(ServerLevel lv, PartShape sh, Vec3 origin) {
        Vector3f[] c = sh.corners();
        int[][] edges = {{0, 1}, {2, 3}, {0, 2}, {1, 3}, {4, 5}, {6, 7}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1f, 0.15f, 0.1f), 0.7f);
        for (int[] e : edges) {
            for (int i = 0; i <= 4; i++) {
                Vector3f p = new Vector3f(c[e[0]]).lerp(c[e[1]], i / 4f);
                lv.sendParticles(red, origin.x + p.x, origin.y + p.y, origin.z + p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static Entity find(UUID id) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        for (ServerLevel lv : s.getAllLevels()) {
            Entity e = lv.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ATTACKS.isEmpty()) return;
        int n = now();
        for (Iterator<Attack> it = ATTACKS.iterator(); it.hasNext(); ) {
            Attack a = it.next();
            if (n < a.start) continue;
            Entity e = find(a.who);
            if (e == null || !e.isAlive() || n >= a.end || !(e.level() instanceof ServerLevel lv)) {
                it.remove();
                continue;
            }
            PartShape sh = shape(e, a);
            if (sh == null) continue;
            Vec3 o = e.position();
            if (a.show) outline(lv, sh, o);
            for (Entity v : lv.getEntities(e, sh.bounds(o.x, o.y, o.z).inflate(0.1), x -> x instanceof LivingEntity && x.isAlive())) {
                if (own(e, v) || v.getUUID().equals(a.except) || a.hit.contains(v.getUUID())) continue;
                if (!touches(sh, o, v.getBoundingBox())) continue;
                a.hit.add(v.getUUID());
                LAST_HITS.merge(a.who, 1, Integer::sum);
                hurt(lv, e, v, a.damage, a.push);
                if (a.impact != null && a.impact.json() != null) {
                    Vec3 at = v.getBoundingBox().getCenter();
                    SprauteNetwork.sendToAudience(a.impact.scriptId(), new Packets.FxPlay("", a.impact.project(), a.impact.json(), at.x, at.y, at.z, -1, a.impact.scale(), a.impact.scriptId()));
                }
            }
        }
    }

    /** Parts that hurt on contact (part boss "лезвие" attack 4): once in half a second per victim. */
    public static void contact(SprauteModelEntity me) {
        if (!(me.level() instanceof ServerLevel lv) || !me.isAlive()) return;
        List<EntityLibrary.Def.Part> defs = me.partList();
        int n = now();
        Vec3 o = me.position();
        for (int i = 0; i < defs.size(); i++) {
            float dmg = me.partAttack(defs.get(i).name);
            PartShape sh = me.shape(i);
            if (dmg <= 0 || sh == null) continue;
            for (Entity v : lv.getEntities(me, sh.bounds(o.x, o.y, o.z).inflate(0.1), x -> x instanceof LivingEntity && x.isAlive())) {
                if (own(me, v) || !touches(sh, o, v.getBoundingBox())) continue;
                String key = me.getUUID() + "/" + v.getUUID();
                if (CONTACT_READY.getOrDefault(key, 0) > n) continue;
                CONTACT_READY.put(key, n + 10);
                hurt(lv, me, v, dmg, 0.4);
            }
        }
        if (CONTACT_READY.size() > 512) CONTACT_READY.values().removeIf(t -> t <= n);
    }
}
