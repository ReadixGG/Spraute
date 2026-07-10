package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

public class EntityUtilFunctions {

    private static Entity resolveEntity(Object arg, CommandSourceStack source) {
        return EntityScriptUtil.resolveEntity(arg, source);
    }

    /** setEntityGlowing(entity, true/false) — белый контур сквозь блоки, без зелья свечения */
    public static class SetEntityGlowing implements ScriptFunction {
        @Override public String getName() { return "setEntityGlowing"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Boolean.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            Entity e = resolveEntity(args.get(0), source);
            if (e == null) return false;
            boolean glowing = args.get(1) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(1)));
            SprauteEntityCompat.setGlowing(e, glowing);
            return true;
        }
    }

    /** isEntityGlowing(entity) */
    public static class IsEntityGlowing implements ScriptFunction {
        @Override public String getName() { return "isEntityGlowing"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            Entity e = resolveEntity(args.get(0), source);
            return e != null && SprauteEntityCompat.isGlowing(e);
        }
    }

    /** removeEntity(uuidOrNpc) */
    public static class RemoveEntity implements ScriptFunction {
        @Override public String getName() { return "removeEntity"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e != null) {
                org.zonarstudio.spraute_engine.entity.BillboardManager.removeByUuid(e.getUUID());
                e.remove(Entity.RemovalReason.DISCARDED);
            }
            return null;
        }
    }

    /** setBillboardTexture(uuid, texture) */
    public static class SetBillboardTexture implements ScriptFunction {
        @Override public String getName() { return "setBillboardTexture"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e instanceof SprauteBillboardEntity bb) {
                bb.setTexture(String.valueOf(args.get(1)));
            }
            return null;
        }
    }

    /** teleportEntity(uuid, x, y, z) */
    public static class TeleportEntity implements ScriptFunction {
        @Override public String getName() { return "teleportEntity"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class, Number.class, Number.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e != null) {
                double x = ((Number) args.get(1)).doubleValue();
                double y = ((Number) args.get(2)).doubleValue();
                double z = ((Number) args.get(3)).doubleValue();
                e.teleportTo(x, y, z);
            }
            return null;
        }
    }

    /** healEntity(entity, amount) — лечит без изменения max HP (в отличие от entity.hp = ...) */
    public static class HealEntity implements ScriptFunction {
        @Override public String getName() { return "healEntity"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2 || !(args.get(1) instanceof Number n)) return null;
            Entity e = resolveEntity(args.get(0), source);
            if (e instanceof LivingEntity living) {
                living.heal(n.floatValue());
            }
            return null;
        }
    }

    /** resetFallDistance(entity) — сбросить накопленное падение (нет урона при приземлении) */
    public static class ResetFallDistance implements ScriptFunction {
        @Override public String getName() { return "resetFallDistance"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e != null) {
                org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.resetFallDistance(e);
            }
            return null;
        }
    }

    /** getEntityPos(uuid) → [x, y, z] */
    public static class GetEntityPos implements ScriptFunction {
        @Override public String getName() { return "getEntityPos"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e == null) return null;
            java.util.ArrayList<Object> pos = new java.util.ArrayList<>();
            pos.add(e.getX()); pos.add(e.getY()); pos.add(e.getZ());
            return pos;
        }
    }

    /** getNpc(scriptId) — tracked script NPC still alive in the world */
    public static class GetNpc implements ScriptFunction {
        @Override public String getName() { return "getNpc"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            String id = String.valueOf(args.get(0));
            Entity e = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(id, source.getLevel());
            return e != null && e.isAlive() ? e : null;
        }
    }

    /** registerNpc(scriptId, entity) — adopt an existing NPC entity under a script id */
    public static class RegisterNpc implements ScriptFunction {
        @Override public String getName() { return "registerNpc"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            String id = String.valueOf(args.get(0));
            Entity e = resolveEntity(args.get(1), source);
            if (e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc && npc.isAlive()) {
                org.zonarstudio.spraute_engine.entity.NpcManager.track(id, npc.getUUID());
                return npc;
            }
            return null;
        }
    }

    /** getNearestNpc(anchor, radius, [modelOrTextureHint]) — nearest Spraute NPC; hint matches model/texture substring */
    public static class GetNearestNpc implements ScriptFunction {
        @Override public String getName() { return "getNearestNpc"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getLevel() == null || args.size() < 2 || !(args.get(1) instanceof Number radiusNum)) {
                return null;
            }
            Entity anchor = resolveEntity(args.get(0), source);
            if (anchor == null) return null;
            String hint = args.size() > 2 ? String.valueOf(args.get(2)).toLowerCase() : "";
            double radius = radiusNum.doubleValue();
            double r2 = radius * radius;
            org.zonarstudio.spraute_engine.entity.SprauteNpcEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity e : source.getLevel().getEntities(anchor, anchor.getBoundingBox().inflate(radius),
                    ent -> ent instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity sn
                            && sn.isAlive() && matchesHint(sn, hint))) {
                double d = anchor.distanceToSqr(e);
                if (d <= r2 && d < best) {
                    best = d;
                    nearest = (org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) e;
                }
            }
            return nearest;
        }

        private static boolean matchesHint(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc, String hint) {
            if (hint == null || hint.isEmpty() || "any".equals(hint)) return true;
            String model = npc.getModel() != null ? npc.getModel().toLowerCase() : "";
            String texture = npc.getTexture() != null ? npc.getTexture().toLowerCase() : "";
            String name = npc.getCustomName() != null ? npc.getCustomName().getString().toLowerCase() : "";
            return model.contains(hint) || texture.contains(hint) || name.contains(hint);
        }
    }
}
