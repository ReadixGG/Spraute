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
            if (props.containsKey("x") && props.get("x").get(0) instanceof Number n) x = n.doubleValue();
            if (props.containsKey("y") && props.get("y").get(0) instanceof Number n) y = n.doubleValue();
            if (props.containsKey("z") && props.get("z").get(0) instanceof Number n) z = n.doubleValue();

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
            if (props.containsKey("yaw") && props.get("yaw").get(0) instanceof Number n) yaw = n.floatValue();
            else if (props.containsKey("rotationY") && props.get("rotationY").get(0) instanceof Number n) yaw = n.floatValue();
            if (props.containsKey("pitch") && props.get("pitch").get(0) instanceof Number n) pitch = n.floatValue();
            else if (props.containsKey("rotationX") && props.get("rotationX").get(0) instanceof Number n) pitch = n.floatValue();

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
            // Never upsert scene-hierarchy NPCs — create npc must not mutate them even if ids collide.
            boolean protectSceneId = existing != null
                    && org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(existing);
            if (protectSceneId) {
                LOGGER.warn("[create npc] id '{}' belongs to a scene NPC — spawning a separate entity without stealing the id", instanceId);
                existing = null;
            }
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
            float rotationX = numberProp(props, "rotationX", 0f);
            float rotationY = numberProp(props, "rotationY", 0f);
            float rotationZ = numberProp(props, "rotationZ", 0f);
            npc.setModelRotation(rotationX, rotationY, rotationZ);
            if (props.containsKey("scale")) {
                npc.setModelScale(numberProp(props, "scale", 1f));
            } else {
                npc.setModelScale(
                        numberProp(props, "scaleX", 1f),
                        numberProp(props, "scaleY", 1f),
                        numberProp(props, "scaleZ", 1f));
            }

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
            if (props.containsKey("walkTurnSpeed") && props.get("walkTurnSpeed").get(0) instanceof Number n) {
                npc.setWalkTurnSpeed(n.floatValue());
            }
            if (props.containsKey("idleTurnSpeed") && props.get("idleTurnSpeed").get(0) instanceof Number n) {
                npc.setIdleTurnSpeed(n.floatValue());
            }
            applyGroundPathProps(npc, props);
            applyAirPathProps(npc, props);
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
            if (props.containsKey("drops") || props.containsKey("dropItem") || props.containsKey("dropMin")
                    || props.containsKey("dropMax") || props.containsKey("dropChance")) {
                npc.customDrops.clear();
                if (props.containsKey("drops")) {
                    Object raw = props.get("drops").get(0);
                    if (raw instanceof List<?> rows) {
                        for (Object row : rows) {
                            if (!(row instanceof List<?> cells) || cells.isEmpty()) continue;
                            String dItem = String.valueOf(cells.get(0));
                            int dMin = cells.size() > 1 && cells.get(1) instanceof Number n ? n.intValue() : 1;
                            int dMax = cells.size() > 2 && cells.get(2) instanceof Number n ? n.intValue() : dMin;
                            int dChance = cells.size() > 3 && cells.get(3) instanceof Number n ? n.intValue() : 100;
                            npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(
                                    dItem, dMin, Math.max(dMin, dMax), dChance, false, null));
                        }
                    }
                } else {
                    String dItem = props.containsKey("dropItem") ? String.valueOf(props.get("dropItem").get(0)) : "minecraft:air";
                    int dMin = props.containsKey("dropMin") ? ((Number) props.get("dropMin").get(0)).intValue() : 1;
                    int dMax = props.containsKey("dropMax") ? ((Number) props.get("dropMax").get(0)).intValue() : 1;
                    int dChance = props.containsKey("dropChance") ? ((Number) props.get("dropChance").get(0)).intValue() : 100;
                    npc.customDrops.add(new org.zonarstudio.spraute_engine.registry.CustomDropRegistry.DropRule(
                            dItem, dMin, dMax, dChance, false, null));
                }
            }
            if (props.containsKey("dropXp") || props.containsKey("xp") || props.containsKey("experience")
                    || props.containsKey("drop_xp")) {
                int xp = 0;
                if (props.containsKey("dropXp")) xp = ((Number) props.get("dropXp").get(0)).intValue();
                else if (props.containsKey("drop_xp")) xp = ((Number) props.get("drop_xp").get(0)).intValue();
                else if (props.containsKey("xp")) xp = ((Number) props.get("xp").get(0)).intValue();
                else if (props.containsKey("experience")) xp = ((Number) props.get("experience").get(0)).intValue();
                npc.setDropXp(xp);
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
            } else if (props.containsKey("hitboxPreset") || props.containsKey("hitbox_preset")) {
                String preset = String.valueOf(
                        props.containsKey("hitboxPreset")
                                ? props.get("hitboxPreset").get(0)
                                : props.get("hitbox_preset").get(0));
                npc.setHitboxPreset(preset);
            } else if (props.containsKey("width") || props.containsKey("height")
                    || props.containsKey("w") || props.containsKey("h")
                    || props.containsKey("hitboxWidth") || props.containsKey("hitboxHeight")) {
                float hw = firstNumber(props, npc.getHitboxWidth(), "width", "w", "hitboxWidth", "hitboxwidth", "hitbox_width");
                float hh = firstNumber(props, npc.getHitboxHeight(), "height", "h", "hitboxHeight", "hitboxheight", "hitbox_height");
                float ox = firstNumber(props, npc.getHitboxOffsetX(), "offsetX", "hitboxOffsetX", "hitbox_offset_x");
                float oy = firstNumber(props, npc.getHitboxOffsetY(), "offsetY", "hitboxOffsetY", "hitbox_offset_y");
                float oz = firstNumber(props, npc.getHitboxOffsetZ(), "offsetZ", "hitboxOffsetZ", "hitbox_offset_z");
                npc.setHitbox(hw, hh, ox, oy, oz);
            }
            if (props.containsKey("translucent") || props.containsKey("transparent")) {
                Object raw = props.containsKey("translucent") ? props.get("translucent").get(0)
                        : props.get("transparent").get(0);
                npc.setTranslucent(isTruthy(raw));
            }
            if (props.containsKey("opacity") && props.get("opacity").get(0) instanceof Number n) {
                npc.setOpacity(n.floatValue());
            }
            if (props.containsKey("lod") || props.containsKey("lodEnabled")) {
                Object raw=props.containsKey("lodEnabled") ? props.get("lodEnabled").get(0) : props.get("lod").get(0);
                npc.setLodEnabled(!(raw instanceof String s && "off".equalsIgnoreCase(s)) && isTruthy(raw));
            }
            if (props.containsKey("renderDistance") && props.get("renderDistance").get(0) instanceof Number n) {
                npc.setRenderDistance(n.floatValue());
            }
            if (props.containsKey("forceVisible")) npc.setForceVisible(isTruthy(props.get("forceVisible").get(0)));
            if (props.containsKey("loadChunks") || props.containsKey("load_chunks")
                    || props.containsKey("keepChunks") || props.containsKey("keepLoaded")) {
                Object raw = props.containsKey("loadChunks") ? props.get("loadChunks").get(0)
                        : props.containsKey("load_chunks") ? props.get("load_chunks").get(0)
                        : props.containsKey("keepChunks") ? props.get("keepChunks").get(0)
                        : props.get("keepLoaded").get(0);
                npc.setLoadChunks(isTruthy(raw));
            }
            if (existing != npc) level.addFreshEntity(npc);

            // Keep scene node ids bound to hierarchy entities.
            if (!protectSceneId) {
                NpcManager.track(instanceId, npc.getUUID());
            }
            if (variables != null) {
                variables.put(instanceId, npc);
            }
            return npc;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn NPC '{}': {}", instanceId, e.getMessage());
            return null;
        }
    }

    private static float numberProp(Map<String, List<Object>> props, String key, float fallback) {
        List<Object> values = props.get(key);
        if (values == null || values.isEmpty() || !(values.get(0) instanceof Number number)) return fallback;
        return number.floatValue();
    }

    private static float firstNumber(Map<String, List<Object>> props, float fallback, String... keys) {
        for (String key : keys) {
            List<Object> values = props.get(key);
            if (values != null && !values.isEmpty() && values.get(0) instanceof Number number) {
                return number.floatValue();
            }
        }
        return fallback;
    }

    private static void applyGroundPathProps(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc,
                                             Map<String, List<Object>> props) {
        var s = npc.groundPathSettings();
        if (props.containsKey("pathArriveRadius") && props.get("pathArriveRadius").get(0) instanceof Number n) {
            s.arriveRadius = n.doubleValue();
        } else if (props.containsKey("pathArrive") && props.get("pathArrive").get(0) instanceof Number n) {
            s.arriveRadius = n.doubleValue();
        }
        if (props.containsKey("pathArriveVert") && props.get("pathArriveVert").get(0) instanceof Number n) {
            s.arriveVert = n.doubleValue();
        }
        if (props.containsKey("pathStepHeight") && props.get("pathStepHeight").get(0) instanceof Number n) {
            s.stepHeight = n.doubleValue();
        } else if (props.containsKey("stepHeight") && props.get("stepHeight").get(0) instanceof Number n) {
            s.stepHeight = n.doubleValue();
        }
        if (props.containsKey("pathMaxJump") && props.get("pathMaxJump").get(0) instanceof Number n) {
            s.maxJump = n.intValue();
        }
        if (props.containsKey("pathMaxDrop") && props.get("pathMaxDrop").get(0) instanceof Number n) {
            s.maxDrop = n.intValue();
        }
        if (props.containsKey("pathMaxRange") && props.get("pathMaxRange").get(0) instanceof Number n) {
            s.maxRange = n.doubleValue();
        }
        if (props.containsKey("pathMaxNodes") && props.get("pathMaxNodes").get(0) instanceof Number n) {
            s.maxNodes = n.intValue();
        }
        if (props.containsKey("pathDiagonal")) s.allowDiagonal = isTruthy(props.get("pathDiagonal").get(0));
        if (props.containsKey("pathAvoidWater")) s.avoidWater = isTruthy(props.get("pathAvoidWater").get(0));
        if (props.containsKey("pathAvoidLava")) s.avoidLava = isTruthy(props.get("pathAvoidLava").get(0));
        if (props.containsKey("pathSnapOnArrive")) s.snapOnArrive = isTruthy(props.get("pathSnapOnArrive").get(0));
        s.clamp();
        npc.applyPathStepHeight();
    }

    private static void applyAirPathProps(org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc,
                                          Map<String, List<Object>> props) {
        var s = npc.airPathSettings();
        if (props.containsKey("flyArriveRadius") && props.get("flyArriveRadius").get(0) instanceof Number n) {
            s.arriveRadius = n.doubleValue();
        } else if (props.containsKey("flyArrive") && props.get("flyArrive").get(0) instanceof Number n) {
            s.arriveRadius = n.doubleValue();
        }
        if (props.containsKey("flyMaxRange") && props.get("flyMaxRange").get(0) instanceof Number n) {
            s.maxRange = n.doubleValue();
        }
        if (props.containsKey("flyMaxNodes") && props.get("flyMaxNodes").get(0) instanceof Number n) {
            s.maxNodes = n.intValue();
        }
        if (props.containsKey("flyDiagonal")) s.allowDiagonal = isTruthy(props.get("flyDiagonal").get(0));
        if (props.containsKey("flyAvoidWater")) s.avoidWater = isTruthy(props.get("flyAvoidWater").get(0));
        if (props.containsKey("flyAvoidLava")) s.avoidLava = isTruthy(props.get("flyAvoidLava").get(0));
        if (props.containsKey("flySnapOnArrive")) s.snapOnArrive = isTruthy(props.get("flySnapOnArrive").get(0));
        s.clamp();
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value == null) return false;
        String s = String.valueOf(value).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }
}
