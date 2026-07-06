package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SpAnimationCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, SpAnimationParser.AnimationSet> CACHE = new ConcurrentHashMap<>();
    private static final SpAnimationParser.AnimationSet EMPTY = new SpAnimationParser.AnimationSet(Map.of());

    private SpAnimationCache() {}

    public static SpAnimationParser.AnimationSet getOrLoad(String animationPath) {
        if (animationPath == null || animationPath.isEmpty()) {
            return EMPTY;
        }
        return CACHE.computeIfAbsent(animationPath, SpAnimationCache::loadAnimation);
    }

    private static SpAnimationParser.AnimationSet loadAnimation(String animationPath) {
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(animationPath, SprauteResourcePath.Kind.ANIMATION);
        if (!parsed.ok()) {
            LOGGER.warn("[Spraute Engine] Invalid animation path '{}': {}", animationPath, parsed.invalidChars());
            SprauteResourcePath.warnClientPlayerOnce("anim:" + animationPath, parsed);
            return EMPTY;
        }
        ResourceLocation loc = parsed.location();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    SpAnimationParser.AnimationSet set = SpAnimationParser.parse(is);
                    LOGGER.info("[Spraute Engine] Loaded animation '{}' ({} clips)", animationPath, set.size());
                    return set;
                }
            } else {
                LOGGER.warn("[Spraute Engine] Animation not found: {}", loc);
            }
        } catch (Exception e) {
            LOGGER.error("[Spraute Engine] Failed to load animation '{}': {}", animationPath, e.getMessage());
        }
        return EMPTY;
    }
}
