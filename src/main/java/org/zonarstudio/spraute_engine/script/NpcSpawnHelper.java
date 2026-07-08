package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.entity.ModEntities;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

import java.util.List;
import java.util.Map;

/** Общая логика спавна/обновления НИПа из property-map (create npc / spawnNpcPrefab). */
public final class NpcSpawnHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcSpawnHelper() {}

    public static SprauteNpcEntity spawn(
            String instanceId,
            Map<String, List<Object>> props,
            CommandSourceStack source,
            Map<String, Object> variables) {
        if (instanceId == null || instanceId.isBlank() || props == null || source == null) return null;
        try {
            String name = props.containsKey("name") ? String.valueOf(props.get("name").get(0)) : instanceId;
            int hp = props.containsKey("hp") ? ((Number) props.get("hp").get(0)).intValue() : 20;
            double speed = props.containsKey("speed") ? ((Number) props.get("speed").get(0)).doubleValue() : 0.3;
            boolean showName = !props.containsKey("showName") || Boolean.TRUE.equals(props.get("showName").get(0));
            boolean collision = !props.containsKey("collision") || Boolean.TRUE.equals(props.get("collision").get(0))
                    || "true".equalsIgnoreCase(String.valueOf(props.get("collision").get(0)));

            List<Object> posArgs = props.get("pos");
            double x = 0, y = 64, z = 0;
            if (posArgs != null && !posArgs.isEmpty()) {
                Object first = posArgs.get(0);
                if (first instanceof List<?> l && l.size() >= 3) {
                    x = ((Number) l.get(0)).doubleValue();
                    y = ((Number) l.get(1)).doubleValue();
                    z = ((Number) l.get(2)).doubleValue();
                } else if (posArgs.size() >= 3) {
                    x = ((Number) posArgs.get(0)).doubleValue();
                    y = ((Number) posArgs.get(1)).doubleValue();
                    z = ((Number) posArgs.get(2)).doubleValue();
                }
            }

            List<Object> rotArgs = props.get("rotate");
            float yaw = 0, pitch = 0;
            if (rotArgs != null && !rotArgs.isEmpty()) {
                Object first = rotArgs.get(0);
                if (first instanceof List<?> l && l.size() >= 2) {
                    yaw = ((Number) l.get(0)).floatValue();
                    pitch = ((Number) l.get(1)).floatValue();
                } else if (rotArgs.size() >= 2) {
                    yaw = ((Number) rotArgs.get(0)).floatValue();
                    pitch = ((Number) rotArgs.get(1)).floatValue();
                }
            }

            List<Object> dimArgs = props.get("dimension");
            String dimensionId = null;
            if (dimArgs != null && !dimArgs.isEmpty()) {
                dimensionId = String.valueOf(dimArgs.get(0));
            }

            ServerLevel level = source.getLevel();
            if (dimensionId != null) {
                //? if >=1.20.1 {
                var resKey = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
                //?} else {
                /*var resKey = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.Registry.DIMENSION_REGISTRY,
                        new net.minecraft.resources.ResourceLocation(dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId));
                *///?}
                ServerLevel dim = source.getLevel().getServer().getLevel(resKey);
                if (dim != null) level = dim;
            }

            if (level == null) return null;

            Entity existing = NpcManager.getEntity(instanceId, level);
            SprauteNpcEntity npc;
            if (existing instanceof SprauteNpcEntity e) {
                npc = e;
            } else {
                if (existing != null) existing.discard();
                npc = ModEntities.SPRAUTE_NPC.get().create(level);
            }
            if (npc == null) return null;

            npc.setCustomName(Component.literal(name));
            npc.setCustomNameVisible(showName);
            npc.setHasCollision(collision);
            npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(hp);
            npc.setHealth(hp);
            npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);
            npc.moveTo(x, y, z, yaw, pitch);
            npc.setYRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setYHeadRot(yaw);
            npc.setXRot(pitch);

            if (props.containsKey("model")) {
                npc.setModel(String.valueOf(props.get("model").get(0)));
            }
            if (props.containsKey("texture")) {
                npc.setTexture(String.valueOf(props.get("texture").get(0)));
            }
            if (props.containsKey("animation")) {
                npc.setAnimation(String.valueOf(props.get("animation").get(0)));
            }
            if (props.containsKey("idleAnim")) {
                npc.setIdleAnim(String.valueOf(props.get("idleAnim").get(0)));
            }
            if (props.containsKey("walkAnim")) {
                npc.setWalkAnim(String.valueOf(props.get("walkAnim").get(0)));
            }
            if (props.containsKey("flyIdleAnim")) {
                npc.setFlyIdleAnim(String.valueOf(props.get("flyIdleAnim").get(0)));
            }
            if (props.containsKey("flyWalkAnim")) {
                npc.setFlyWalkAnim(String.valueOf(props.get("flyWalkAnim").get(0)));
            }
            if (props.containsKey("swimIdleAnim")) {
                npc.setSwimIdleAnim(String.valueOf(props.get("swimIdleAnim").get(0)));
            }
            if (props.containsKey("swimWalkAnim")) {
                npc.setSwimWalkAnim(String.valueOf(props.get("swimWalkAnim").get(0)));
            }
            if (props.containsKey("dropItem") || props.containsKey("dropMin") || props.containsKey("dropMax") || props.containsKey("dropChance")) {
                npc.customDrops.clear();
                String dItem = props.containsKey("dropItem") ? String.valueOf(props.get("dropItem").get(0)) : "minecraft:air";
                int dMin = props.containsKey("dropMin") ? ((Number) props.get("dropMin").get(0)).intValue() : 1;
                int dMax = props.containsKey("dropMax") ? ((Number) props.get("dropMax").get(0)).intValue() : 1;
                int dChance = props.containsKey("dropChance") ? ((Number) props.get("dropChance").get(0)).intValue() : 100;
                npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(dItem, dMin, dMax, dChance, false, null));
            }
            if (props.containsKey("hitbox")) {
                List<Object> hb = props.get("hitbox");
                if (hb != null && hb.size() >= 2) {
                    float hw = ((Number) hb.get(0)).floatValue();
                    float hh = ((Number) hb.get(1)).floatValue();
                    float ox = 0f, oy = 0f, oz = 0f;
                    if (hb.size() >= 5) {
                        ox = ((Number) hb.get(2)).floatValue();
                        oy = ((Number) hb.get(3)).floatValue();
                        oz = ((Number) hb.get(4)).floatValue();
                    }
                    npc.setHitbox(hw, hh, ox, oy, oz);
                }
            }
            if (existing != npc) level.addFreshEntity(npc);

            NpcManager.track(instanceId, npc.getUUID());
            if (variables != null) {
                variables.put(instanceId, npc);
            }
            return npc;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn NPC '{}': {}", instanceId, e.getMessage());
            return null;
        }
    }
}
