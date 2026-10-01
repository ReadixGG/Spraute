package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
        return new Class<?>[]{String.class, Integer.class, Double.class, Double.class, Double.class, Double.class, Double.class, Double.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        String name = String.valueOf(args.get(0));
        int hp = ((Number) args.get(1)).intValue();
        double speed = ((Number) args.get(2)).doubleValue();
        double x = ((Number) args.get(3)).doubleValue();
        double y = ((Number) args.get(4)).doubleValue();
        double z = ((Number) args.get(5)).doubleValue();
        float yaw = ((Number) args.get(6)).floatValue();
        float pitch = ((Number) args.get(7)).floatValue();

        ServerLevel level = source.getLevel();
        if (level != null) {
            SprauteNpcEntity npc = ModEntities.SPRAUTE_NPC.get().create(level);
            if (npc != null) {
                npc.setCustomName(Component.literal(name));
                npc.setCustomNameVisible(true);
                npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(hp);
                npc.setHealth(hp);
                npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);

                // Keep legacy block-center snap for integer-looking coords.
                double spawnX = looksIntegral(x) ? x + 0.5 : x;
                double spawnZ = looksIntegral(z) ? z + 0.5 : z;
                npc.moveTo(spawnX, y, spawnZ, yaw, pitch);
                npc.setYRot(yaw);
                npc.setYBodyRot(yaw);
                npc.setYHeadRot(yaw);
                npc.setXRot(pitch);

                level.addFreshEntity(npc);
                NpcManager.track(name, npc.getUUID());

                // Scene-script context: claim NPC so /spraute stop removes it.
                var vars = org.zonarstudio.spraute_engine.script.ScriptExecutor.currentVariables();
                var sceneState = org.zonarstudio.spraute_engine.script.SceneScriptSpawn.resolveState(vars, java.util.Map.of());
                if (sceneState != null) {
                    java.util.Map<String, java.util.List<Object>> props = new java.util.HashMap<>();
                    props.put("name", java.util.List.of(name));
                    props.put("pos", java.util.List.of(spawnX, y, spawnZ));
                    props.put("yaw", java.util.List.of(yaw));
                    String parentId = org.zonarstudio.spraute_engine.script.SceneScriptSpawn
                            .resolveParentId(sceneState, vars, props);
                    org.zonarstudio.spraute_engine.script.SceneScriptSpawn.attachEntity(
                            sceneState, parentId, name,
                            org.zonarstudio.spraute_engine.scene.SceneNodeType.NPC,
                            props, npc, vars);
                }

                //? if >=1.20.1 {
                source.sendSuccess(() -> Component.literal("§a[Spraute]§r NPC '" + name + "' spawned at " + x + " " + y + " " + z), false);
                //?} else {
                /*source.sendSuccess(Component.literal("§a[Spraute]§r NPC '" + name + "' spawned at " + x + " " + y + " " + z), false);
                *///?}
                return npc;
            }
        }
        return null;
    }

    private static boolean looksIntegral(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6;
    }
}
