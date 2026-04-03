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
        return 5;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[] { String.class, Integer.class, Double.class, Double.class, Double.class };
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 5) return null;
        
        String texture = String.valueOf(args.get(0));
        int amount = ((Number) args.get(1)).intValue();
        double x = ((Number) args.get(2)).doubleValue();
        double y = ((Number) args.get(3)).doubleValue();
        double z = ((Number) args.get(4)).doubleValue();

        ServerLevel level = source.getLevel();
        if (level != null) {
            SprauteOrbEntity orb = new SprauteOrbEntity(level, x, y, z, amount, texture);
            level.addFreshEntity(orb);
            return orb.getUUID().toString();
        }
        return null;
    }
}