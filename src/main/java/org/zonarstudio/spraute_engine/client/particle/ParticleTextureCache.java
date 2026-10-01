package org.zonarstudio.spraute_engine.client.particle;

import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Billboard-style texture ids: {@code textures/foo.png} → {@code spraute_engine:textures/foo.png}.
 * Does not decode PNGs; Minecraft / ExternalAssetPack already serve those paths.
 */
public final class ParticleTextureCache {
    private static final ResourceLocation FALLBACK =
            new ResourceLocation("minecraft", "textures/particle/generic_0.png");
    private static final int MAX_PATHS = 128;
    private static final Map<String, ResourceLocation> PATHS =
            new LinkedHashMap<>(MAX_PATHS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
                    return size() > MAX_PATHS;
                }
            };

    private ParticleTextureCache() {}

    public static ResourceLocation resolve(String raw) {
        String value = raw == null ? "" : raw.trim().replace('\\', '/');
        if (value.isEmpty()) return FALLBACK;
        synchronized (PATHS) {
            ResourceLocation cached = PATHS.get(value);
            if (cached != null) return cached;
        }
        ResourceLocation resolved = resolveUncached(value);
        synchronized (PATHS) {
            PATHS.put(value, resolved);
        }
        return resolved;
    }

    private static ResourceLocation resolveUncached(String value) {
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(value, SprauteResourcePath.Kind.TEXTURE);
        if (parsed.ok()) return parsed.location();
        if (value.contains(":")) {
            try {
                return new ResourceLocation(value);
            } catch (Exception ignored) {
            }
        }
        return FALLBACK;
    }

    public static void clear() {
        synchronized (PATHS) {
            PATHS.clear();
        }
    }

    public static void invalidate(java.nio.file.Path file) {}
}
