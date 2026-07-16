package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.Optional;

/**
 * createExplosion(x, y, z, radius, damage, destroyBlocks)
 * — взрыв в точке с заданным радиусом, уроном сущностям и опциональным разрушением блоков.
 */
public class ExplosionFunctions {

    private static double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static float asFloat(Object o) {
        return o instanceof Number n ? n.floatValue() : 0.0f;
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static ExplosionDamageCalculator blockCalculator(boolean destroyBlocks) {
        return new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter reader, BlockPos pos,
                                                               BlockState state, FluidState fluid) {
                return Optional.empty();
            }

            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos,
                                              BlockState state, float power) {
                return destroyBlocks;
            }
        };
    }

    private static void damageEntitiesInRadius(ServerLevel level, DamageSource damageSource,
                                               double x, double y, double z, float radius, float maxDamage) {
        if (maxDamage <= 0.0f) return;
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        double radiusSq = radius * radius;
        for (Entity entity : level.getEntities(null, box)) {
            if (!entity.isAlive()) continue;
            double distSq = entity.distanceToSqr(x, y, z);
            if (distSq >= radiusSq) continue;
            double falloff = 1.0D - Math.sqrt(distSq) / radius;
            entity.hurt(damageSource, (float) (maxDamage * falloff));
        }
    }

    public static class CreateExplosion implements ScriptFunction {
        @Override
        public String getName() {
            return "createExplosion";
        }

        @Override
        public int getArgCount() {
            return 6;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{
                    Number.class, Number.class, Number.class,
                    Number.class, Number.class, Object.class
            };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 6 || source.getLevel() == null) return null;

            double x = asDouble(args.get(0));
            double y = asDouble(args.get(1));
            double z = asDouble(args.get(2));
            float radius = Math.max(0.1f, asFloat(args.get(3)));
            float maxDamage = Math.max(0.0f, asFloat(args.get(4)));
            boolean destroyBlocks = asBool(args.get(5));

            ServerLevel level = source.getLevel();
            ExplosionDamageCalculator calculator = blockCalculator(destroyBlocks);

            //? if >=1.20.1 {
            DamageSource damageSource = level.damageSources().explosion(null);
            level.explode(
                    null,
                    damageSource,
                    calculator,
                    x, y, z,
                    radius,
                    false,
                    destroyBlocks
                            ? net.minecraft.world.level.Level.ExplosionInteraction.TNT
                            : net.minecraft.world.level.Level.ExplosionInteraction.NONE
            );
            damageEntitiesInRadius(level, damageSource, x, y, z, radius, maxDamage);
            //?} else {
            /*DamageSource damageSource = new DamageSource("explosion");
            level.explode(
                    null,
                    damageSource,
                    calculator,
                    x, y, z,
                    radius,
                    false,
                    destroyBlocks ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.NONE
            );
            damageEntitiesInRadius(level, damageSource, x, y, z, radius, maxDamage);
            *///?}

            return null;
        }
    }
}
