package org.zonarstudio.spraute_engine.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Resolves script entity references: players, NPC names, UUIDs, keywords {@code player}/{@code npc}/{@code mob}. */
public final class EntityScriptUtil {

    private EntityScriptUtil() {}

    public static Entity resolveEntity(Object arg, CommandSourceStack source) {
        if (arg instanceof Entity e) return e;
        if (source == null || source.getLevel() == null) return null;
        ServerLevel level = source.getLevel();

        if (arg instanceof String idOrKeyword) {
            try {
                UUID uuid = UUID.fromString(idOrKeyword);
                Entity byUuid = level.getEntity(uuid);
                if (byUuid != null) return byUuid;
            } catch (IllegalArgumentException ignored) {}

            UUID npcUuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(idOrKeyword);
            if (npcUuid != null) {
                Entity npc = org.zonarstudio.spraute_engine.entity.NpcManager.getEntity(idOrKeyword, level);
                if (npc != null) return npc;
                Entity byId = level.getEntity(npcUuid);
                if (byId != null) return byId;
            }

            if ("player".equalsIgnoreCase(idOrKeyword)) {
                Entity origin = source.getEntity();
                if (origin != null) return level.getNearestPlayer(origin, 64.0);
                var pos = source.getPosition();
                return level.getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
            }
            if ("npc".equalsIgnoreCase(idOrKeyword)) {
                return findNearest(source, e -> e instanceof SprauteNpcEntity);
            }
            if ("mob".equalsIgnoreCase(idOrKeyword)) {
                return findNearest(source, e ->
                        e instanceof LivingEntity
                                && !(e instanceof Player)
                                && !(e instanceof SprauteNpcEntity));
            }

            ServerPlayer byName = level.getServer().getPlayerList().getPlayerByName(idOrKeyword);
            if (byName != null) return byName;
        }

        if (source.getServer() != null) {
            ServerPlayer byName = source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
            if (byName != null) return byName;
        }
        return null;
    }

    private static Entity findNearest(CommandSourceStack source, Predicate<Entity> filter) {
        ServerLevel level = source.getLevel();
        if (level == null) return null;
        Entity origin = source.getEntity();
        if (origin != null) {
            List<Entity> entities = level.getEntities(origin, origin.getBoundingBox().inflate(64.0),
                    e -> e != null && e.isAlive() && filter.test(e));
            Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity e : entities) {
                double d = origin.distanceToSqr(e);
                if (d < best) {
                    best = d;
                    nearest = e;
                }
            }
            return nearest;
        }
        var pos = source.getPosition();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                pos.x - 64, pos.y - 64, pos.z - 64,
                pos.x + 64, pos.y + 64, pos.z + 64);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box,
                e -> e != null && e.isAlive() && filter.test(e));
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : entities) {
            double d = e.distanceToSqr(pos.x, pos.y, pos.z);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }
}
