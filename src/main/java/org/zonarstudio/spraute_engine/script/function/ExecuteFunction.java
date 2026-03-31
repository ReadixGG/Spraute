package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * execute(command) — выполняет команду от лица текущего источника.
 * execute(command, executor) — выполняет команду от лица указанного игрока/сущности.
 */
public class ExecuteFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override public String getName() { return "execute"; }
    @Override public int getArgCount() { return 1; }
    @Override public Class<?>[] getArgTypes() { return new Class[]{String.class}; }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty()) return 0;
        String cmd = String.valueOf(args.get(0)).trim();
        if (cmd.isEmpty()) return 0;

        CommandSourceStack execSource = source;
        if (args.size() >= 2) {
            Object executor = args.get(1);
            net.minecraft.world.entity.Entity entity = resolveExecutorEntity(source, executor);
            if (entity != null) {
                execSource = source.withEntity(entity);
            }
        }

        String toRun = cmd.startsWith("/") ? cmd.substring(1).trim() : cmd;
        try {
            return source.getServer().getCommands().performPrefixedCommand(execSource, toRun);
        } catch (Exception e) {
            LOGGER.warn("[Script] execute failed: {} — {}", cmd, e.getMessage());
            return 0;
        }
    }

    private net.minecraft.world.entity.Entity resolveExecutorEntity(CommandSourceStack source, Object executor) {
        if (executor instanceof net.minecraft.world.entity.Entity e) return e;
        if (!(executor instanceof String idOrKeyword) || source.getLevel() == null) return null;

        if ("player".equalsIgnoreCase(idOrKeyword)) {
            net.minecraft.world.entity.Entity origin = source.getEntity();
            if (origin != null) return source.getLevel().getNearestPlayer(origin, 64.0);
            net.minecraft.world.phys.Vec3 pos = source.getPosition();
            return source.getLevel().getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
        }
        if ("npc".equalsIgnoreCase(idOrKeyword)) {
            return findNearestEntity(source, e -> e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity);
        }
        if ("mob".equalsIgnoreCase(idOrKeyword)) {
            return findNearestEntity(source, e ->
                    e instanceof net.minecraft.world.entity.LivingEntity
                            && !(e instanceof net.minecraft.world.entity.player.Player)
                            && !(e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity));
        }
        if ("any".equalsIgnoreCase(idOrKeyword)) {
            return findNearestEntity(source, e -> e instanceof net.minecraft.world.entity.LivingEntity);
        }

        java.util.UUID npcUuid = NpcManager.get(idOrKeyword);
        if (npcUuid != null) {
            net.minecraft.world.entity.Entity npc = source.getLevel().getEntity(npcUuid);
            if (npc != null) return npc;
        }
        ServerPlayer player = source.getLevel().getServer().getPlayerList().getPlayerByName(idOrKeyword);
        return player;
    }

    private net.minecraft.world.entity.Entity findNearestEntity(
            CommandSourceStack source,
            java.util.function.Predicate<net.minecraft.world.entity.Entity> filter) {
        if (source.getLevel() == null) return null;
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        net.minecraft.world.entity.Entity origin = source.getEntity();
        if (origin != null) {
            List<net.minecraft.world.entity.Entity> entities = level.getEntities(origin,
                    origin.getBoundingBox().inflate(64.0),
                    e -> e != null && e.isAlive() && filter.test(e));
            net.minecraft.world.entity.Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (net.minecraft.world.entity.Entity e : entities) {
                double d = origin.distanceToSqr(e);
                if (d < best) {
                    best = d;
                    nearest = e;
                }
            }
            return nearest;
        }

        net.minecraft.world.phys.Vec3 pos = source.getPosition();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos, pos).inflate(64.0);
        List<net.minecraft.world.entity.Entity> entities = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                e -> e != null && e.isAlive() && filter.test(e));
        net.minecraft.world.entity.Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity e : entities) {
            double d = e.distanceToSqr(pos);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }
}
