package org.zonarstudio.spraute_engine.script;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.entity.ModEntities;
import org.zonarstudio.spraute_engine.entity.ModelManager;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;

import java.util.List;
import java.util.Map;

/** Spawn/update geo-model entities from property maps ({@code create model}). */
public final class ModelSpawnHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ModelSpawnHelper() {}

    public static SprauteModelEntity spawn(
            String instanceId,
            Map<String, List<Object>> props,
            CommandSourceStack source,
            Map<String, Object> variables) {
        if (instanceId == null || instanceId.isBlank() || props == null || source == null) return null;
        try {
            String name = props.containsKey("name") ? String.valueOf(props.get("name").get(0)) : instanceId;
            boolean showName = !props.containsKey("showName") || Boolean.TRUE.equals(props.get("showName").get(0))
                    || "true".equalsIgnoreCase(String.valueOf(props.get("showName").get(0)));

            double x = 0, y = 64, z = 0;
            List<Object> posArgs = props.get("pos");
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

            float yaw = 0, pitch = 0;
            List<Object> rotArgs = props.get("rotate");
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

            String dimensionId = null;
            List<Object> dimArgs = props.get("dimension");
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

            Entity existing = ModelManager.getEntity(instanceId, level);
            boolean protectSceneId = existing != null
                    && org.zonarstudio.spraute_engine.scene.SceneRuntime.isSceneOwned(existing);
            if (protectSceneId) {
                LOGGER.warn("[create model] id '{}' belongs to a scene model — spawning a separate entity without stealing the id", instanceId);
                existing = null;
            }
            SprauteModelEntity model;
            if (existing instanceof SprauteModelEntity e) {
                model = e;
            } else {
                if (existing != null) existing.discard();
                model = ModEntities.SPRAUTE_MODEL.get().create(level);
            }
            if (model == null) return null;

            model.setCustomName(Component.literal(name));
            model.setCustomNameVisible(showName);
            model.moveTo(x, y, z, yaw, pitch);
            model.setYRot(yaw);
            model.setXRot(pitch);
            model.setModelRotation(
                    numberProp(props, "rotationX", 0f),
                    numberProp(props, "rotationY", 0f),
                    numberProp(props, "rotationZ", 0f));
            if (props.containsKey("scale")) {
                model.setModelScale(numberProp(props, "scale", 1f));
            } else {
                model.setModelScale(
                        numberProp(props, "scaleX", 1f),
                        numberProp(props, "scaleY", 1f),
                        numberProp(props, "scaleZ", 1f));
            }

            if (props.containsKey("model")) {
                model.setModel(String.valueOf(props.get("model").get(0)));
            }
            if (props.containsKey("texture")) {
                model.setTexture(String.valueOf(props.get("texture").get(0)));
            }
            if (props.containsKey("animation")) {
                model.setAnimation(String.valueOf(props.get("animation").get(0)));
            }
            if (props.containsKey("idleAnim")) {
                model.setIdleAnim(String.valueOf(props.get("idleAnim").get(0)));
            }
            if (props.containsKey("noGravity")) {
                model.setNoGravity(booleanProp(props, "noGravity", true));
            }
            if (props.containsKey("glowing")) {
                model.setGlowingTag(booleanProp(props, "glowing", false));
            }
            if (props.containsKey("translucent") || props.containsKey("transparent")) {
                boolean on = booleanProp(props, props.containsKey("translucent") ? "translucent" : "transparent", true);
                model.setTranslucent(on);
            }
            if (props.containsKey("opacity") && props.get("opacity").get(0) instanceof Number n) {
                model.setOpacity(n.floatValue());
            }
            if (props.containsKey("lod") || props.containsKey("lodEnabled")) {
                String key=props.containsKey("lodEnabled") ? "lodEnabled" : "lod";
                Object raw=props.get(key).get(0);
                model.setLodEnabled(!(raw instanceof String s && "off".equalsIgnoreCase(s))
                        && booleanProp(props,key,true));
            }
            if (props.containsKey("renderDistance")) {
                model.setRenderDistance(numberProp(props,"renderDistance",0f));
            }
            if (props.containsKey("forceVisible")) {
                model.setForceVisible(booleanProp(props,"forceVisible",false));
            }
            if (props.containsKey("silent")) {
                model.setSilent(booleanProp(props, "silent", false));
            }

            if (existing != model) level.addFreshEntity(model);
            if (!protectSceneId) {
                ModelManager.track(instanceId, model.getUUID());
            }
            if (variables != null) variables.put(instanceId, model);
            return model;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn model '{}': {}", instanceId, e.getMessage());
            return null;
        }
    }

    private static float numberProp(Map<String, List<Object>> props, String key, float fallback) {
        List<Object> values = props.get(key);
        if (values == null || values.isEmpty() || !(values.get(0) instanceof Number number)) return fallback;
        return number.floatValue();
    }

    private static boolean booleanProp(Map<String, List<Object>> props, String key, boolean fallback) {
        List<Object> values = props.get(key);
        if (values == null || values.isEmpty()) return fallback;
        Object value = values.get(0);
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
