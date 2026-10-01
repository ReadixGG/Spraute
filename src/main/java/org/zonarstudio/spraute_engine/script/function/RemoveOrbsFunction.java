package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.entity.SprauteOrbEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.util.SprauteOrbTextures;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class RemoveOrbsFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "removeOrbs";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty() || source.getLevel() == null) return 0;
        String tex = SprauteOrbTextures.normalize(String.valueOf(args.get(0)));
        ServerLevel level = source.getLevel();
        
        int removed = 0;
        // Search for orbs in a reasonable radius, e.g., 256 blocks around the execution source
        AABB aabb = new AABB(source.getPosition(), source.getPosition()).inflate(256.0);
        for (SprauteOrbEntity orb : level.getEntitiesOfClass(SprauteOrbEntity.class, aabb)) {
            if (SprauteOrbTextures.matches(tex, orb.getTexture())) {
                orb.discard();
                removed++;
            }
        }
        return removed;
    }
}
