package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class SpawnBillboardFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "spawnBillboard";
    }

    @Override
    public int getArgCount() {
        return -1; // variable args
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[] { String.class, Double.class, Double.class, Double.class, Double.class, Double.class, Boolean.class, String.class };
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        // args: texture, x, y, z, width, height, seeThrough, dimension(optional)
        if (args.size() < 7) return null;
        
        String texture = String.valueOf(args.get(0));
        double x = ((Number) args.get(1)).doubleValue();
        double y = ((Number) args.get(2)).doubleValue();
        double z = ((Number) args.get(3)).doubleValue();
        float width = ((Number) args.get(4)).floatValue();
        float height = ((Number) args.get(5)).floatValue();
        boolean seeThrough = (Boolean) args.get(6);
        
        String dimensionId = null;
        if (args.size() >= 8) {
            dimensionId = String.valueOf(args.get(7));
        }

        ServerLevel level = source.getLevel();
        if (dimensionId != null && level != null) {
            //? if >=1.20.1 {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
            //?} else {
            /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
            *///?}
            ServerLevel dim = level.getServer().getLevel(resKey);
            if (dim != null) level = dim;
        }
        
        if (level != null) {
            SprauteBillboardEntity billboard = new SprauteBillboardEntity(level, x, y, z, texture, width, height, seeThrough);
            level.addFreshEntity(billboard);
            return billboard.getUUID().toString();
        }
        return null;
    }
}
