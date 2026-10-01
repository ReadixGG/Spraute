package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.resource.WorkspaceContent;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SpAnimationCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, SpAnimationParser.AnimationSet> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> STAMPS = new ConcurrentHashMap<>();
    private static final SpAnimationParser.AnimationSet EMPTY = new SpAnimationParser.AnimationSet(Map.of());

    private SpAnimationCache() {}

    /** Drop a cached file after the Timeline editor rewrites it on disk. */
    public static void invalidate(String animationPath) {
        if (animationPath == null || animationPath.isEmpty()) return;
        String key = normalize(animationPath);
        CACHE.remove(key);
        CACHE.remove(animationPath);
        STAMPS.remove(key);
        STAMPS.remove(animationPath);
    }

    public static void clearAll() {
        CACHE.clear();
        STAMPS.clear();
    }

    public static SpAnimationParser.AnimationSet getOrLoad(String animationPath) {
        if (animationPath == null || animationPath.isEmpty()) {
            return EMPTY;
        }
        String key = normalize(animationPath);
        long stamp = sourceStamp(key);
        Long prev = STAMPS.get(key);
        if (prev == null || prev != stamp) {
            CACHE.remove(key);
        }
        return CACHE.computeIfAbsent(key, p -> {
            SpAnimationParser.AnimationSet set = loadAnimation(p);
            STAMPS.put(p, stamp);
            return set;
        });
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').trim();
    }

    private static long sourceStamp(String animationPath) {
        try {
            Path file = WorkspaceContent.resolve(animationPath);
            if (file != null && Files.isRegularFile(file)) {
                return Files.getLastModifiedTime(file).toMillis();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static SpAnimationParser.AnimationSet loadAnimation(String animationPath) {
        try {
            Path disk = WorkspaceContent.resolve(animationPath);
            if (disk != null && Files.isRegularFile(disk)) {
                try (InputStream is = Files.newInputStream(disk)) {
                    SpAnimationParser.AnimationSet set = SpAnimationParser.parse(is);
                    LOGGER.info("[Spraute Engine] Loaded animation '{}' ({} clips)", animationPath, set.size());
                    return set;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Spraute Engine] Disk animation '{}': {}", animationPath, e.getMessage());
        }

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
