package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.parser.SpGeoParser;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of parsed {@link SpGeoModel} instances.
 * Models are loaded lazily from Minecraft's resource system (including ExternalAssetPack).
 */
public final class SpModelCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, SpGeoModel> CACHE = new ConcurrentHashMap<>();
    private static final SpGeoModel EMPTY = new SpGeoModel("empty", 64, 64,
            java.util.Collections.emptyList(), java.util.Collections.emptyMap());

    private SpModelCache() {}

    /** Get or load a model by its path (e.g. "geo/defender.geo.json"). */
    public static SpGeoModel getOrLoad(String modelPath) {
        SpGeoModel cached = CACHE.get(modelPath);
        if (cached != null) return cached;
        return loadModel(modelPath);
    }

    /** Force reload a single model. */
    public static void invalidate(String modelPath) {
        CACHE.remove(modelPath);
    }

    /** Clear entire cache (call on resource reload). */
    public static void clearAll() {
        CACHE.clear();
        LOGGER.info("[Spraute Engine] Model cache cleared");
    }

    private static SpGeoModel loadModel(String modelPath) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Spraute_engine.MODID, modelPath);
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    SpGeoModel model = SpGeoParser.parse(is);
                    CACHE.put(modelPath, model);
                    LOGGER.info("[Spraute Engine] Loaded model '{}' ({} bones)", modelPath, model.boneMap.size());
                    return model;
                }
            } else {
                LOGGER.warn("[Spraute Engine] Model not found: {}", loc);
            }
        } catch (Exception e) {
            LOGGER.error("[Spraute Engine] Failed to load model '{}': {}", modelPath, e.getMessage());
        }
        CACHE.put(modelPath, EMPTY);
        return EMPTY;
    }
}
