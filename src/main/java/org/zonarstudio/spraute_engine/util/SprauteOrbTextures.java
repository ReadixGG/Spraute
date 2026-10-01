package org.zonarstudio.spraute_engine.util;

import net.minecraft.resources.ResourceLocation;

/** Canonical orb texture paths — same rules as UI ({@link SprauteResourcePath}). */
public final class SprauteOrbTextures {

    private static final ResourceLocation DEFAULT =
            new ResourceLocation("minecraft", "textures/entity/experience_orb.png");

    private SprauteOrbTextures() {}

    /** Storage/compare form: workspace {@code textures/…} or {@code minecraft:textures/…}. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(trimmed, SprauteResourcePath.Kind.TEXTURE);
        if (parsed.ok()) {
            return SprauteResourcePath.toStoragePath(parsed);
        }
        String sanitized = SprauteResourcePath.sanitizeAssetPath(trimmed);
        if (sanitized.isEmpty()) return "";
        parsed = SprauteResourcePath.parse(sanitized, SprauteResourcePath.Kind.TEXTURE);
        return parsed.ok() ? SprauteResourcePath.toStoragePath(parsed) : sanitized;
    }

    public static ResourceLocation resolve(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT;
        String canonical = normalize(raw);
        if (canonical.isEmpty()) return DEFAULT;
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(canonical, SprauteResourcePath.Kind.TEXTURE);
        if (parsed.ok()) return parsed.location();
        if (canonical.contains(":")) {
            try {
                return new ResourceLocation(canonical);
            } catch (Exception ignored) {
            }
        }
        return DEFAULT;
    }

    public static boolean matches(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return na.equals(nb);
        return na.equalsIgnoreCase(nb);
    }
}
