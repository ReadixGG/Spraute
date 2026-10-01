package org.zonarstudio.spraute_engine.script;

import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shared script helpers for geo-model entities (NPC and standalone model). */
public final class GeoEntityScriptUtil {
    private GeoEntityScriptUtil() {}

    public static boolean isGeoEntity(Entity entity) {
        return entity instanceof SprauteNpcEntity || entity instanceof SprauteModelEntity;
    }

    public static boolean executeAnimMethod(Entity entity, String method, List<Object> args) {
        if (entity instanceof SprauteNpcEntity npc) {
            if ("setidleanim".equals(method)) {
                if (!args.isEmpty()) npc.setIdleAnim(String.valueOf(args.get(0)));
                else npc.setIdleAnim("");
                return true;
            }
            return executeAnim(npc::playOnce, npc::playOnce, npc::playLoop, npc::playLoop,
                    npc::playFreeze, npc::playFreeze,
                    npc::stopOverlayAnimation, npc::stopOverlayAnimation, method, args);
        }
        if (entity instanceof SprauteModelEntity model) {
            if ("setidleanim".equals(method)) {
                if (!args.isEmpty()) model.setIdleAnim(String.valueOf(args.get(0)));
                else model.setIdleAnim("");
                return true;
            }
            return executeAnim(model::playOnce, model::playOnce, model::playLoop, model::playLoop,
                    model::playFreeze, model::playFreeze,
                    model::stopOverlayAnimation, model::stopOverlayAnimation, method, args);
        }
        return false;
    }

    private static boolean executeAnim(
            Consumer<String> playOnceDefault,
            BiConsumer<String, Boolean> playOnceExplicit,
            Consumer<String> playLoopDefault,
            BiConsumer<String, Boolean> playLoopExplicit,
            Consumer<String> playFreezeDefault,
            BiConsumer<String, Boolean> playFreezeExplicit,
            Runnable stopOverlay,
            Consumer<String> stopNamed,
            String method,
            List<Object> args) {
        return switch (method) {
            case "playonce" -> {
                playAnim(args, playOnceDefault, playOnceExplicit);
                yield true;
            }
            case "playloop" -> {
                playAnim(args, playLoopDefault, playLoopExplicit);
                yield true;
            }
            case "playfreeze" -> {
                playAnim(args, playFreezeDefault, playFreezeExplicit);
                yield true;
            }
            case "stopoverlay" -> {
                stopOverlay.run();
                yield true;
            }
            case "stop" -> {
                if (!args.isEmpty()) stopNamed.accept(String.valueOf(args.get(0)));
                yield true;
            }
            default -> false;
        };
    }

    private static void playAnim(List<Object> args, Consumer<String> defaults, BiConsumer<String, Boolean> explicit) {
        if (args.isEmpty()) return;
        String name = String.valueOf(args.get(0));
        if (args.size() < 2) {
            // Use entity default: NPC additive=true, MODEL additive=false (replace).
            defaults.accept(name);
        } else {
            explicit.accept(name, isAdditiveAnimationArg(args.get(1)));
        }
    }

    private static boolean isAdditiveAnimationArg(Object arg) {
        if (arg instanceof Boolean b) return b;
        if (arg instanceof String s) {
            String lower = s.toLowerCase(Locale.ROOT);
            return !"false".equals(lower) && !"0".equals(lower) && !"replace".equals(lower);
        }
        if (arg instanceof Number n) return n.intValue() != 0;
        return true;
    }

    public static String getModel(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModel();
        if (entity instanceof SprauteModelEntity model) return model.getModel();
        return "";
    }

    public static String getTexture(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getTexture();
        if (entity instanceof SprauteModelEntity model) return model.getTexture();
        return "";
    }

    public static String getAnimation(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getAnimation();
        if (entity instanceof SprauteModelEntity model) return model.getAnimation();
        return "";
    }

    public static void setModel(Entity entity, String value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setModel(value);
        else if (entity instanceof SprauteModelEntity model) model.setModel(value);
    }

    public static void setTexture(Entity entity, String value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setTexture(value);
        else if (entity instanceof SprauteModelEntity model) model.setTexture(value);
    }

    public static void setAnimation(Entity entity, String value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setAnimation(value);
        else if (entity instanceof SprauteModelEntity model) model.setAnimation(value);
    }

    public static float getModelRotationX(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelRotationX();
        if (entity instanceof SprauteModelEntity model) return model.getModelRotationX();
        return entity.getXRot();
    }

    public static float getModelRotationY(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelRotationY();
        if (entity instanceof SprauteModelEntity model) return model.getModelRotationY();
        return entity.getYRot();
    }

    public static float getModelRotationZ(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelRotationZ();
        if (entity instanceof SprauteModelEntity model) return model.getModelRotationZ();
        return 0f;
    }

    public static float getModelScaleX(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelScaleX();
        if (entity instanceof SprauteModelEntity model) return model.getModelScaleX();
        return 1f;
    }

    public static float getModelScaleY(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelScaleY();
        if (entity instanceof SprauteModelEntity model) return model.getModelScaleY();
        return 1f;
    }

    public static float getModelScaleZ(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getModelScaleZ();
        if (entity instanceof SprauteModelEntity model) return model.getModelScaleZ();
        return 1f;
    }

    public static void setModelRotationX(Entity entity, float value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setModelRotationX(value);
        else if (entity instanceof SprauteModelEntity model) model.setModelRotationX(value);
    }

    public static void setModelRotationY(Entity entity, float value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setModelRotationY(value);
        else if (entity instanceof SprauteModelEntity model) model.setModelRotationY(value);
    }

    public static void setModelRotationZ(Entity entity, float value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setModelRotationZ(value);
        else if (entity instanceof SprauteModelEntity model) model.setModelRotationZ(value);
    }

    public static void setModelScale(Entity entity, float x, float y, float z) {
        if (entity instanceof SprauteNpcEntity npc) npc.setModelScale(x, y, z);
        else if (entity instanceof SprauteModelEntity model) model.setModelScale(x, y, z);
    }

    public static void setTranslucent(Entity entity, boolean value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setTranslucent(value);
        else if (entity instanceof SprauteModelEntity model) model.setTranslucent(value);
    }

    public static boolean isTranslucent(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.isTranslucent();
        if (entity instanceof SprauteModelEntity model) return model.isTranslucent();
        return true;
    }

    public static void setOpacity(Entity entity, float value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setOpacity(value);
        else if (entity instanceof SprauteModelEntity model) model.setOpacity(value);
    }

    public static float getOpacity(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getOpacity();
        if (entity instanceof SprauteModelEntity model) return model.getOpacity();
        return 1f;
    }

    public static boolean usesTranslucentGeo(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.usesTranslucentGeo();
        if (entity instanceof SprauteModelEntity model) return model.usesTranslucentGeo();
        return true;
    }

    public static void setLodEnabled(Entity entity, boolean value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setLodEnabled(value);
        else if (entity instanceof SprauteModelEntity model) model.setLodEnabled(value);
    }
    public static boolean isLodEnabled(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.isLodEnabled();
        if (entity instanceof SprauteModelEntity model) return model.isLodEnabled();
        return false;
    }
    public static void setRenderDistance(Entity entity, float value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setRenderDistance(value);
        else if (entity instanceof SprauteModelEntity model) model.setRenderDistance(value);
    }
    public static float getRenderDistance(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.getRenderDistance();
        if (entity instanceof SprauteModelEntity model) return model.getRenderDistance();
        return 0f;
    }
    public static void setForceVisible(Entity entity, boolean value) {
        if (entity instanceof SprauteNpcEntity npc) npc.setForceVisible(value);
        else if (entity instanceof SprauteModelEntity model) model.setForceVisible(value);
    }
    public static boolean isForceVisible(Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) return npc.isForceVisible();
        if (entity instanceof SprauteModelEntity model) return model.isForceVisible();
        return false;
    }
}
