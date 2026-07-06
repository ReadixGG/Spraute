package org.zonarstudio.spraute_engine.core.model;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.core.parser.SpGeoParser;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Server-safe cache for geo models and animation sets (no client-only APIs). */
public final class SpResourceCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, SpGeoModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, SpAnimationParser.AnimationSet> ANIM_CACHE = new ConcurrentHashMap<>();
    private static final SpGeoModel EMPTY_MODEL = new SpGeoModel("empty", 64, 64,
            java.util.Collections.emptyList(), java.util.Collections.emptyMap());
    private static final SpAnimationParser.AnimationSet EMPTY_ANIM = new SpAnimationParser.AnimationSet(Map.of());

    private SpResourceCache() {}

    public static SpGeoModel getModel(ResourceManager rm, String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) return EMPTY_MODEL;
        return MODEL_CACHE.computeIfAbsent(modelPath, p -> loadModel(rm, p));
    }

    public static SpAnimationParser.AnimationSet getAnimation(ResourceManager rm, String animationPath) {
        if (animationPath == null || animationPath.isEmpty()) return EMPTY_ANIM;
        return ANIM_CACHE.computeIfAbsent(animationPath, p -> loadAnimation(rm, p));
    }

    public static void clearAll() {
        MODEL_CACHE.clear();
        ANIM_CACHE.clear();
    }

    private static SpGeoModel loadModel(ResourceManager rm, String modelPath) {
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(modelPath, SprauteResourcePath.Kind.MODEL);
        if (!parsed.ok()) {
            LOGGER.warn("[Spraute Engine] Invalid server model path '{}': {}", modelPath, parsed.invalidChars());
            return EMPTY_MODEL;
        }
        ResourceLocation loc = parsed.location();
        try {
            Optional<Resource> res = rm.getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    SpGeoModel model = SpGeoParser.parse(is);
                    LOGGER.info("[Spraute Engine] Loaded server model '{}' ({} bones)", modelPath, model.boneMap.size());
                    return model;
                }
            }
            LOGGER.warn("[Spraute Engine] Server model not found: {}", loc);
        } catch (Exception e) {
            LOGGER.error("[Spraute Engine] Failed to load server model '{}': {}", modelPath, e.getMessage());
        }
        return EMPTY_MODEL;
    }

    private static SpAnimationParser.AnimationSet loadAnimation(ResourceManager rm, String animationPath) {
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(animationPath, SprauteResourcePath.Kind.ANIMATION);
        if (!parsed.ok()) {
            LOGGER.warn("[Spraute Engine] Invalid server animation path '{}': {}", animationPath, parsed.invalidChars());
            return EMPTY_ANIM;
        }
        ResourceLocation loc = parsed.location();
        try {
            Optional<Resource> res = rm.getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    return SpAnimationParser.parse(is);
                }
            }
            LOGGER.warn("[Spraute Engine] Server animation not found: {}", loc);
        } catch (Exception e) {
            LOGGER.error("[Spraute Engine] Failed to load server animation '{}': {}", animationPath, e.getMessage());
        }
        return EMPTY_ANIM;
    }
}
