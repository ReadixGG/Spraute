package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

public class EntityUtilFunctions {

    private static Entity resolveEntity(Object arg, CommandSourceStack source) {
        if (arg instanceof Entity e) return e;
        if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
            net.minecraft.server.level.ServerPlayer sp = source.getServer().getPlayerList().getPlayer(p.getUUID());
            if (sp != null) return sp;
        }
        if (source.getLevel() == null) return null;
        if (arg instanceof String s) {
            // Try UUID string
            try {
                UUID uuid = UUID.fromString(s);
                return source.getLevel().getEntity(uuid);
            } catch (IllegalArgumentException ignored) {}
            // Try NPC name
            UUID uuid2 = org.zonarstudio.spraute_engine.entity.NpcManager.get(s);
            if (uuid2 != null) return source.getLevel().getEntity(uuid2);
        }
        return null;
    }

    /** removeEntity(uuidOrNpc) */
    public static class RemoveEntity implements ScriptFunction {
        @Override public String getName() { return "removeEntity"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Entity e = resolveEntity(args.get(0), source);
            if (e != null) e.remove(Entity.RemovalReason.DISCARDED);
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
}
