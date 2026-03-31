package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import org.zonarstudio.spraute_engine.entity.ModEntities;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import java.util.List;

public class NpcFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "npc";
    }

    @Override
    public int getArgCount() {
        return 8; // name, hp, speed, x, y, z, yaw, pitch
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{String.class, Integer.class, Double.class, Integer.class, Integer.class, Integer.class, Integer.class, Integer.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        String name = (String) args.get(0);
        int hp = (Integer) args.get(1);
        double speed = (Double) args.get(2);
        int x = (Integer) args.get(3);
        int y = (Integer) args.get(4);
        int z = (Integer) args.get(5);
        int yaw = (Integer) args.get(6);
        int pitch = (Integer) args.get(7);

        net.minecraft.server.level.ServerLevel level = source.getLevel();
        if (level != null) {
            SprauteNpcEntity npc = ModEntities.SPRAUTE_NPC.get().create(level);
            if (npc != null) {
                npc.setCustomName(Component.literal(name));
                npc.setCustomNameVisible(true);
                npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(hp);
                npc.setHealth(hp);
                npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                
                npc.moveTo(x + 0.5, y, z + 0.5, yaw, pitch);
                npc.setYRot(yaw);
                npc.setYHeadRot(yaw);
                
                level.addFreshEntity(npc);
                NpcManager.track(name, npc.getUUID());
                source.sendSuccess(Component.literal("§a[Spraute]§r NPC '" + name + "' spawned at " + x + " " + y + " " + z), false);
            }
        }
        return null;
    }
}
