package org.sprautestudio.spraute_engine.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rays for stories: what a player or a character looks at, what stands between two points,
 * whether one can see the other. Blocks are hit by their shape (grass and flowers count),
 * water is looked through.
 */
public final class Raycast {
    private Raycast() {
    }

    /** One hit: where, how far, and what (a block, an entity or nothing). */
    public record Hit(String kind, Vec3 at, double dist, BlockPos block, String face, Entity entity) {
    }

    private static Entity entity(Object v) {
        return v instanceof EntityRef r ? r.require() : null;
    }

    /** The point a ray starts from or aims at: an entity's eyes, else the point itself. */
    private static Vec3 eyes(Object v) {
        Entity e = entity(v);
        return e != null ? e.getEyePosition() : WorldApi.vec(v);
    }

    /** Where to aim at an entity: the middle of its body. */
    private static Vec3 aimAt(Object v) {
        Entity e = entity(v);
        return e != null ? e.getBoundingBox().getCenter() : WorldApi.vec(v);
    }

    public static Hit trace(Vec3 from, Vec3 to, Entity ignore, Entity ignore2, boolean entities) {
        ServerLevel level = ignore != null && ignore.level() instanceof ServerLevel sl ? sl : WorldApi.level();
        Entity ctx = ignore != null ? ignore : SprauteRuntime.player();
        BlockHitResult bh = ctx == null ? null : level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ctx));
        Vec3 end = bh != null && bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        Hit best = null;
        if (entities) {
            double bestD = from.distanceTo(end);
            AABB box = new AABB(from, end).inflate(1);
            for (Entity e : level.getEntities((Entity) null, box, e -> e != ignore && e != ignore2 && !e.isSpectator() && e.isAlive()
                    && (e.isPickable() || e instanceof LivingEntity))) {
                AABB bb = e.getBoundingBox().inflate(Math.max(0.1, e.getPickRadius()));
                Optional<Vec3> p = bb.contains(from) ? Optional.of(from) : bb.clip(from, end);
                if (p.isEmpty()) continue;
                double d = from.distanceTo(p.get());
                if (d < bestD) {
                    bestD = d;
                    best = new Hit("entity", p.get(), d, null, "", e);
                }
            }
        }
        if (best != null) return best;
        if (bh != null && bh.getType() == HitResult.Type.BLOCK)
            return new Hit("block", bh.getLocation(), from.distanceTo(bh.getLocation()), bh.getBlockPos(), bh.getDirection().getName(), null);
        return new Hit("none", to, from.distanceTo(to), null, "", null);
    }

    /** From an entity's eyes along its view. */
    public static Hit look(Object who, double dist) {
        Entity e = entity(who);
        if (e == null) throw new SprauteRuntimeException("Смотреть может только сущность: игрок или персонаж");
        Vec3 from = e.getEyePosition();
        return trace(from, from.add(e.getViewVector(1).scale(dist)), e, null, true);
    }

    /** raycast(who, [dist]) or raycast(from, to): the hit as a map. */
    public static Object raycast(Object a, Object b, double dist) {
        Hit h;
        if (b == null || b instanceof Double) h = look(a, b instanceof Double d ? d : dist);
        else h = trace(eyes(a), aimAt(b), entity(a), entity(b), true);
        return map(h);
    }

    public static Object lookBlock(Object who, double dist) {
        Hit h = look(who, dist);
        Entity e = entity(who);
        // look past entities to the block behind
        if (h.kind().equals("entity")) {
            Vec3 from = e.getEyePosition();
            h = trace(from, from.add(e.getViewVector(1).scale(dist)), e, null, false);
        }
        return h.block() == null ? "" : WorldApi.list(Vec3.atLowerCornerOf(h.block()));
    }

    public static Object lookEntity(Object who, double dist) {
        Hit h = look(who, dist);
        return h.entity() == null ? "" : new EntityRef(h.entity());
    }

    /** Nothing solid between a's eyes and b (entities do not block the view). */
    public static boolean canSee(Object a, Object b) {
        Hit h = trace(eyes(a), aimAt(b), entity(a), entity(b), false);
        return !h.kind().equals("block");
    }

    /** Does the entity look at the target: at that entity, or within a block and a half of that point, with nothing in between. */
    public static boolean looksAt(Object who, Object target, double dist) {
        Entity t = entity(target);
        if (t != null) {
            Hit h = look(who, dist);
            return h.entity() == t;
        }
        Entity e = entity(who);
        if (e == null) return false;
        Vec3 p = WorldApi.vec(target), from = e.getEyePosition(), dir = e.getViewVector(1);
        Vec3 to = p.subtract(from);
        double along = to.dot(dir);
        if (along <= 0 || along > dist) return false;
        double off = to.subtract(dir.scale(along)).length();
        return off <= Math.max(1.5, along * 0.05) && canSee(new EntityRef(e), target);
    }

    private static Map<String, Object> map(Hit h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hit", h.kind());
        m.put("pos", WorldApi.list(h.at()));
        m.put("dist", Math.round(h.dist() * 100) / 100.0);
        if (h.block() != null) {
            m.put("block", String.valueOf(BuiltInRegistries.BLOCK.getKey(WorldApi.level().getBlockState(h.block()).getBlock())));
            m.put("blockpos", WorldApi.list(Vec3.atLowerCornerOf(h.block())));
            m.put("face", h.face());
        } else {
            m.put("block", "");
            m.put("blockpos", "");
            m.put("face", "");
        }
        m.put("entity", h.entity() != null ? new EntityRef(h.entity()) : "");
        return m;
    }
}
