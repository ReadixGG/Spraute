package org.sprautestudio.st_addon_spraute.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.*;

/**
 * Spells and other things that fly: an effect launched from a point in a direction. The server moves
 * it tick by tick and looks what it meets (creatures, then blocks); the players' side flies the same
 * effect by itself. On a hit: damage, a push, the impact effect, and the "casthit" event for stories.
 */
public final class Casts {
    public static void registerHooks() {
        Hooks.SERVER_TICK.add(Casts::onTick);
    }

    private Casts() {
    }

    public static final class Cast {
        public String id = "", fx = "", project = "", impactJson = "";
        /** The flying effect's own name on the players' side (unique, unlike id). */
        public String fxId = nextId();
        public Vec3 pos, vel;
        public double gravity, radius = 0.4;
        public float damage = 4, knockback = 0.4f, scale = 1;
        public int life = 60, scriptId = -1;
        public Entity owner;
        public ServerLevel level;
        /** Goes through creatures (hits every one once) instead of stopping at the first. */
        public boolean pierce;
        final Set<Entity> hit = new HashSet<>();
    }

    private static final List<Cast> FLYING = new ArrayList<>();
    private static int counter;

    public static String nextId() {
        return "cast_" + (++counter);
    }

    /** Launches: the effect starts flying on every player's screen. */
    public static void launch(Cast c, String fxJson) {
        FLYING.add(c);
        SprauteNetwork.sendToAudience(c.scriptId, new Packets.FxLaunch(c.fxId, c.project, fxJson, c.pos.x, c.pos.y, c.pos.z, c.vel.x, c.vel.y, c.vel.z,
                c.gravity, c.scale, c.scriptId));
    }

    /** Stops the casts of a story (it was stopped). */
    public static void stopScript(int scriptId) {
        FLYING.removeIf(c -> c.scriptId == scriptId);
    }

    public static void unload() {
        FLYING.clear();
    }

    public static void onTick(Hooks.ServerTick e) {
        if (e.phase != TickPhase.END || FLYING.isEmpty()) return;
        for (Iterator<Cast> it = FLYING.iterator(); it.hasNext(); ) {
            Cast c = it.next();
            try {
                if (step(c)) it.remove();
            } catch (RuntimeException ex) {
                it.remove();
                St_addon_spraute.LOGGER.warn("Spraute cast {}", c.id, ex);
            }
        }
    }

    /** One tick of flight; true — it is over. */
    private static boolean step(Cast c) {
        Vec3 from = c.pos, to = from.add(c.vel);
        // blocks first: how far it gets this tick
        Entity ctx = c.owner != null ? c.owner : SprauteRuntime.playerOf(c.scriptId);
        BlockHitResult bh = ctx == null ? null : c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
        Vec3 end = bh != null && bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        // creatures along the way, nearest first
        AABB box = new AABB(from, end).inflate(c.radius + 1);
        List<Entity> hits = new ArrayList<>();
        for (Entity en : c.level.getEntities(c.owner, box, en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && !c.hit.contains(en))) {
            AABB bb = en.getBoundingBox().inflate(c.radius);
            if (bb.contains(from) || bb.clip(from, end).isPresent()) hits.add(en);
        }
        hits.sort(Comparator.comparingDouble(en -> en.distanceToSqr(from)));
        for (Entity en : hits) {
            Vec3 at = en.getBoundingBox().inflate(c.radius).clip(from, end).orElse(en.getBoundingBox().getCenter());
            strike(c, en, at);
            if (!c.pierce) {
                finish(c, at);
                return true;
            }
        }
        if (bh != null && bh.getType() == HitResult.Type.BLOCK) {
            report(c, "block", null, bh.getLocation(), bh.getBlockPos());
            finish(c, bh.getLocation());
            return true;
        }
        c.pos = to;
        c.vel = c.vel.add(0, -c.gravity, 0);
        if (--c.life <= 0) {
            report(c, "none", null, c.pos, null);
            finish(c, null);
            return true;
        }
        return false;
    }

    private static void strike(Cast c, Entity en, Vec3 at) {
        c.hit.add(en);
        if (c.damage > 0 && en instanceof LivingEntity le) {
            var src = c.owner != null ? c.level.damageSources().indirectMagic(c.owner, c.owner) : c.level.damageSources().magic();
            le.hurt(src, c.damage);
            if (c.knockback > 0) {
                Vec3 dir = c.vel.multiply(1, 0, 1);
                if (dir.lengthSqr() > 1e-6) {
                    dir = dir.normalize();
                    le.knockback(c.knockback, -dir.x, -dir.z);
                    le.hurtMarked = true;
                }
            }
        }
        report(c, "entity", en, at, null);
        if (c.pierce && !c.impactJson.isEmpty()) impact(c, at);
    }

    private static void impact(Cast c, Vec3 at) {
        SprauteNetwork.sendToAudience(c.scriptId, new Packets.FxPlay("", c.project, c.impactJson, at.x, at.y, at.z, -1, c.scale, c.scriptId));
    }

    private static void finish(Cast c, Vec3 at) {
        SprauteNetwork.sendToAudience(c.scriptId, new Packets.FxStop(c.fxId, -1));
        if (at != null && !c.impactJson.isEmpty()) impact(c, at);
    }

    private static void report(Cast c, String kind, Entity target, Vec3 at, BlockPos block) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", c.id);
        d.put("fx", c.fx);
        d.put("hit", kind);
        d.put("damage", (double) (kind.equals("entity") ? c.damage : 0));
        d.put("x", at.x);
        d.put("y", at.y);
        d.put("z", at.z);
        if (target != null) {
            d.put("__entity", target);
            d.put("entity", target instanceof net.minecraft.world.entity.player.Player ? "player"
                    : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())));
            d.put("name", target.getName().getString());
        } else {
            d.put("entity", "");
            d.put("name", "");
        }
        d.put("block", block != null ? String.valueOf(BuiltInRegistries.BLOCK.getKey(c.level.getBlockState(block).getBlock())) : "");
        GameEvents.post("casthit", SprauteRuntime.playerOf(c.scriptId), d);
    }

    public static boolean flying(String id) {
        for (Cast c : FLYING) if (c.id.equals(id)) return true;
        return false;
    }
}
