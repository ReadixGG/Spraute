package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

public class GetNearestPlayerFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "getNearestPlayer";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[] { Object.class };
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty() || source.getLevel() == null) return null;
        Entity anchor = null;
        Object target = args.get(0);
        if (target instanceof Entity e) {
            anchor = e;
        } else {
            String npcId = String.valueOf(target);
            UUID uuid = NpcManager.get(npcId);
            if (uuid != null) anchor = source.getLevel().getEntity(uuid);
        }
        if (anchor != null) return source.getLevel().getNearestPlayer(anchor, 50.0);
        return null;
    }
}
