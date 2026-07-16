package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.entity.SprauteProjectileEntity;
import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public final class ProjectileFunctions {
    private ProjectileFunctions() {}

    private static ServerLevel resolveLevel(CommandSourceStack source, String dimensionId) {
        ServerLevel level = source.getLevel();
        if (level == null || dimensionId == null || dimensionId.isBlank()) return level;
        //? if >=1.20.1 {
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
        //?} else {
        /*var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.Registry.DIMENSION_REGISTRY,
                new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
        *///?}
        ServerLevel dim = level.getServer().getLevel(key);
        return dim != null ? dim : level;
    }

    private static SprauteProjectileEntity spawn(String projectileId, ServerLevel level,
                                                  double x, double y, double z,
                                                  double vx, double vy, double vz, Entity owner) {
        CustomProjectileRegistry.ensureParsed();
        CustomProjectileRegistry.ProjectileDef def = CustomProjectileRegistry.get(projectileId);
        if (def == null) return null;
        SprauteProjectileEntity projectile = new SprauteProjectileEntity(
                level, projectileId, x, y, z, new Vec3(vx, vy, vz), owner);
        level.addFreshEntity(projectile);
        return projectile;
    }

    /** spawnProjectile(id, x, y, z, vx, vy, vz, [dimension]) */
    public static class SpawnProjectile implements ScriptFunction {
        @Override public String getName() { return "spawnProjectile"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, Double.class, Double.class, Double.class,
                    Double.class, Double.class, Double.class, String.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 7 || source.getLevel() == null) return null;
            String id = String.valueOf(args.get(0));
            double x = ((Number) args.get(1)).doubleValue();
            double y = ((Number) args.get(2)).doubleValue();
            double z = ((Number) args.get(3)).doubleValue();
            double vx = ((Number) args.get(4)).doubleValue();
            double vy = ((Number) args.get(5)).doubleValue();
            double vz = ((Number) args.get(6)).doubleValue();
            String dim = args.size() >= 8 ? String.valueOf(args.get(7)) : null;
            ServerLevel level = resolveLevel(source, dim);
            SprauteProjectileEntity p = spawn(id, level, x, y, z, vx, vy, vz, null);
            return p != null ? p.getUUID().toString() : null;
        }
    }

    /** shootProjectile(id, shooter, speed, [dimension]) — velocity along look direction */
    public static class ShootProjectile implements ScriptFunction {
        @Override public String getName() { return "shootProjectile"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, Object.class, Double.class, String.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || source.getLevel() == null) return null;
            String id = String.valueOf(args.get(0));
            Entity shooter = EntityScriptUtil.resolveEntity(args.get(1), source);
            if (shooter == null) return null;
            double speed = ((Number) args.get(2)).doubleValue();
            String dim = args.size() >= 4 ? String.valueOf(args.get(3)) : null;
            ServerLevel level = resolveLevel(source, dim);
            if (level == null) return null;

            Vec3 look = shooter.getLookAngle().scale(speed);
            double x = shooter.getX();
            double y = shooter.getEyeY() - 0.1;
            double z = shooter.getZ();
            SprauteProjectileEntity p = spawn(id, level, x, y, z, look.x, look.y, look.z, shooter);
            return p != null ? p.getUUID().toString() : null;
        }
    }

    /** shootProjectileNear(id, shooter, speed, ox, oy, oz, [dimension]) */
    public static class ShootProjectileNear implements ScriptFunction {
        @Override public String getName() { return "shootProjectileNear"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, Object.class, Double.class,
                    Double.class, Double.class, Double.class, String.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 6 || source.getLevel() == null) return null;
            String id = String.valueOf(args.get(0));
            Entity shooter = EntityScriptUtil.resolveEntity(args.get(1), source);
            if (shooter == null) return null;
            double speed = ((Number) args.get(2)).doubleValue();
            double ox = ((Number) args.get(3)).doubleValue();
            double oy = ((Number) args.get(4)).doubleValue();
            double oz = ((Number) args.get(5)).doubleValue();
            String dim = args.size() >= 7 ? String.valueOf(args.get(6)) : null;
            ServerLevel level = resolveLevel(source, dim);
            if (level == null) return null;

            Vec3 look = shooter.getLookAngle().scale(speed);
            SprauteProjectileEntity p = spawn(id, level,
                    shooter.getX() + ox, shooter.getEyeY() + oy, shooter.getZ() + oz,
                    look.x, look.y, look.z, shooter);
            return p != null ? p.getUUID().toString() : null;
        }
    }

    /**
     * getProjectilePos(uuid) → [x, y, z] или null, если снаряд не найден / уничтожен.
     * Аргумент — UUID строкой (возвращается из shootProjectile / spawnProjectile) или сущность.
     */
    public static class GetProjectilePos implements ScriptFunction {
        @Override public String getName() { return "getProjectilePos"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || source.getLevel() == null) return null;
            Entity e = EntityScriptUtil.resolveEntity(args.get(0), source);
            if (!(e instanceof SprauteProjectileEntity proj) || !proj.isAlive()) return null;
            java.util.ArrayList<Object> pos = new java.util.ArrayList<>();
            pos.add(proj.getX());
            pos.add(proj.getY());
            pos.add(proj.getZ());
            return pos;
        }
    }

    /** removeProjectiles([id]) — all custom projectiles in level, or only matching id */
    public static class RemoveProjectiles implements ScriptFunction {
        @Override public String getName() { return "removeProjectiles"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (source.getLevel() == null) return false;
            String id = args.isEmpty() || args.get(0) == null ? null : String.valueOf(args.get(0));
            SprauteProjectileEntity.removeAllInLevel(source.getLevel(), id);
            return true;
        }
    }
}
