package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AreaFunctions {

    private static ServerLevel level(CommandSourceStack source) {
        return source.getLevel();
    }

    private static Vec3 resolveCenter(Object anchor, CommandSourceStack source) {
        if (anchor instanceof Entity e) return e.position();
        if (anchor instanceof List<?> list && list.size() >= 3
                && list.get(0) instanceof Number
                && list.get(1) instanceof Number
                && list.get(2) instanceof Number) {
            return new Vec3(
                    ((Number) list.get(0)).doubleValue(),
                    ((Number) list.get(1)).doubleValue(),
                    ((Number) list.get(2)).doubleValue()
            );
        }
        if (source.getLevel() == null) return null;
        if (anchor instanceof String s) {
            ServerPlayer byName = source.getServer() != null
                    ? source.getServer().getPlayerList().getPlayerByName(s) : null;
            if (byName != null) return byName.position();
            UUID uuid = NpcManager.get(s);
            if (uuid != null) {
                Entity ent = source.getLevel().getEntity(uuid);
                if (ent != null) return ent.position();
            }
        }
        return null;
    }

    private static Vec3 coordsFromArgs(List<Object> args, int offset) {
        if (args.size() >= offset + 3
                && args.get(offset) instanceof Number
                && args.get(offset + 1) instanceof Number
                && args.get(offset + 2) instanceof Number) {
            return new Vec3(
                    ((Number) args.get(offset)).doubleValue(),
                    ((Number) args.get(offset + 1)).doubleValue(),
                    ((Number) args.get(offset + 2)).doubleValue()
            );
        }
        return null;
    }

    private static boolean matchesFilter(Entity entity, String filter) {
        if (filter == null || filter.isEmpty() || "any".equalsIgnoreCase(filter)) return true;
        return switch (filter.toLowerCase()) {
            case "player" -> entity instanceof Player;
            case "npc" -> entity instanceof SprauteNpcEntity;
            case "mob" -> entity instanceof LivingEntity
                    && !(entity instanceof Player)
                    && !(entity instanceof SprauteNpcEntity);
            case "living" -> entity instanceof LivingEntity;
            default -> false;
        };
    }

    private static List<Object> playersAt(ServerLevel level, Vec3 center, double radius) {
        List<Object> out = new ArrayList<>();
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center) <= r2) out.add(p);
        }
        return out;
    }

    private static List<Object> entitiesAt(ServerLevel level, Vec3 center, double radius, String filter) {
        List<Object> out = new ArrayList<>();
        AABB box = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        for (Entity e : level.getEntities((Entity) null, box, ent -> matchesFilter(ent, filter))) {
            if (e.distanceToSqr(center) <= radius * radius) out.add(e);
        }
        return out;
    }

    /** playersInRadius(x, y, z, radius) */
    public static class PlayersInRadius implements ScriptFunction {
        @Override public String getName() { return "playersInRadius"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Number.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = level(source);
            Vec3 center = coordsFromArgs(args, 0);
            if (level == null || center == null || args.size() < 4 || !(args.get(3) instanceof Number r)) {
                return new ArrayList<>();
            }
            return playersAt(level, center, r.doubleValue());
        }
    }

    /** playersNear(anchor, radius) — anchor: игрок, НИП, сущность */
    public static class PlayersNear implements ScriptFunction {
        @Override public String getName() { return "playersNear"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = level(source);
            if (level == null || args.size() < 2 || !(args.get(1) instanceof Number r)) {
                return new ArrayList<>();
            }
            Vec3 center = resolveCenter(args.get(0), source);
            if (center == null) return new ArrayList<>();
            return playersAt(level, center, r.doubleValue());
        }
    }

    /** entitiesInRadius(x, y, z, radius, [filter]) — filter: any, player, npc, mob, living */
    public static class EntitiesInRadius implements ScriptFunction {
        @Override public String getName() { return "entitiesInRadius"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = level(source);
            Vec3 center = coordsFromArgs(args, 0);
            if (level == null || center == null || args.size() < 4 || !(args.get(3) instanceof Number r)) {
                return new ArrayList<>();
            }
            String filter = args.size() > 4 ? String.valueOf(args.get(4)) : "any";
            return entitiesAt(level, center, r.doubleValue(), filter);
        }
    }

    /** entitiesNear(anchor, radius, [filter]) */
    public static class EntitiesNear implements ScriptFunction {
        @Override public String getName() { return "entitiesNear"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = level(source);
            if (level == null || args.size() < 2 || !(args.get(1) instanceof Number r)) {
                return new ArrayList<>();
            }
            Vec3 center = resolveCenter(args.get(0), source);
            if (center == null) return new ArrayList<>();
            String filter = args.size() > 2 ? String.valueOf(args.get(2)) : "any";
            return entitiesAt(level, center, r.doubleValue(), filter);
        }
    }
}
