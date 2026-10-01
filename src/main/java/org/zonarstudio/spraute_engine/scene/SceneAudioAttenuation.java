package org.zonarstudio.spraute_engine.scene;

/**
 * Shared distance → volume math for AUDIO_SOURCE (runtime + editor).
 * Transform scale stretches the shape on each axis.
 */
public final class SceneAudioAttenuation {
    private SceneAudioAttenuation() {}

    public static String normalizeShape(String raw) {
        if (raw == null || raw.isBlank()) return "sphere";
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "cube", "box", "aabb" -> "cube";
            default -> "sphere";
        };
    }

    /**
     * Normalized distance in [0, +∞): 0 at center, 1 on the outer surface,
     * {@code > 1} outside the source.
     */
    public static float normalizedDistance(SceneNode node, SceneTransform world,
                                           double px, double py, double pz) {
        if (node == null || world == null) return Float.POSITIVE_INFINITY;
        float max = Math.max(0.05f, node.audioMaxDistance);
        float sx = (float) Math.max(1e-4, Math.abs(world.sx));
        float sy = (float) Math.max(1e-4, Math.abs(world.sy));
        float sz = (float) Math.max(1e-4, Math.abs(world.sz));
        float dx = (float) (px - world.px) / (max * sx);
        float dy = (float) (py - world.py) / (max * sy);
        float dz = (float) (pz - world.pz) / (max * sz);
        if ("cube".equals(normalizeShape(node.audioShape))) {
            return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        }
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Inner full-volume boundary as a fraction of outer (0..1). */
    public static float innerRatio(SceneNode node) {
        if (node == null) return 0f;
        float max = Math.max(0.05f, node.audioMaxDistance);
        float full = Math.max(0f, Math.min(node.audioFullRadius, max));
        return full / max;
    }

    /** Volume multiplier for a listener at world position (includes {@link SceneNode#audioVolume}). */
    public static float volumeAt(SceneNode node, SceneTransform world,
                                 double px, double py, double pz) {
        if (node == null || !node.audioEnabled || !node.active) return 0f;
        float base = Math.max(0f, node.audioVolume);
        if (base <= 0f) return 0f;
        float t = normalizedDistance(node, world, px, py, pz);
        if (t >= 1f) return 0f;
        float inner = innerRatio(node);
        if (t <= inner) return base;
        float span = Math.max(1e-4f, 1f - inner);
        float fall = 1f - (t - inner) / span;
        return base * Math.max(0f, fall);
    }

    /** Outer half-extents in world blocks (after scale). */
    public static float[] outerExtents(SceneNode node, SceneTransform world) {
        float max = Math.max(0.05f, node == null ? 16f : node.audioMaxDistance);
        float sx = world == null ? 1f : (float) Math.abs(world.sx);
        float sy = world == null ? 1f : (float) Math.abs(world.sy);
        float sz = world == null ? 1f : (float) Math.abs(world.sz);
        return new float[]{max * Math.max(1e-4f, sx), max * Math.max(1e-4f, sy), max * Math.max(1e-4f, sz)};
    }

    /** Inner (full volume) half-extents in world blocks (after scale). */
    public static float[] innerExtents(SceneNode node, SceneTransform world) {
        float full = Math.max(0.05f, node == null ? 4f : node.audioFullRadius);
        float max = Math.max(full, node == null ? 16f : node.audioMaxDistance);
        full = Math.min(full, max);
        float sx = world == null ? 1f : (float) Math.abs(world.sx);
        float sy = world == null ? 1f : (float) Math.abs(world.sy);
        float sz = world == null ? 1f : (float) Math.abs(world.sz);
        return new float[]{full * Math.max(1e-4f, sx), full * Math.max(1e-4f, sy), full * Math.max(1e-4f, sz)};
    }
}
