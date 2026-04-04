package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.entity.SprauteOrbEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class SpawnOrbFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "spawnOrb";
    }

    @Override
    public int getArgCount() {
        return -1; // 5 or 6 args
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[] { String.class, Integer.class, Double.class, Double.class, Double.class, String.class };
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 5) return null;
        
        String texture = String.valueOf(args.get(0));
        int amount = ((Number) args.get(1)).intValue();
        double x = ((Number) args.get(2)).doubleValue();
        double y = ((Number) args.get(3)).doubleValue();
        double z = ((Number) args.get(4)).doubleValue();
        
        String dimensionId = null;
        if (args.size() >= 6) {
            dimensionId = String.valueOf(args.get(5));
        }

        ServerLevel level = source.getLevel();
        if (dimensionId != null && level != null) {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
            ServerLevel dim = level.getServer().getLevel(resKey);
            if (dim != null) level = dim;
        }
        if (level != null) {
            SprauteOrbEntity orb = new SprauteOrbEntity(level, x, y, z, amount, texture);
            level.addFreshEntity(orb);
            return orb.getUUID().toString();
        }
        return null;
    }
}