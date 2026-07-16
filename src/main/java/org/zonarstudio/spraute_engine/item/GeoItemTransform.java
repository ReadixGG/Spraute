package org.zonarstudio.spraute_engine.item;

/**
 * Offset / rotation / scale for rendering a geo item in a specific display context.
 * Order: ox, oy, oz, rx, ry, rz, scale (degrees for rotations).
 */
public final class GeoItemTransform {
    public static final GeoItemTransform IDENTITY = new GeoItemTransform(0, 0, 0, 0, 0, 0, 1);

    public final float ox, oy, oz;
    public final float rx, ry, rz;
    public final float scale;

    public GeoItemTransform(float ox, float oy, float oz, float rx, float ry, float rz, float scale) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.scale = scale <= 0 ? 1f : scale;
    }

    public static GeoItemTransform parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("[,;\\s]+");
        if (parts.length < 7) return null;
        try {
            return new GeoItemTransform(
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim()),
                    Float.parseFloat(parts[3].trim()),
                    Float.parseFloat(parts[4].trim()),
                    Float.parseFloat(parts[5].trim()),
                    Float.parseFloat(parts[6].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static GeoItemTransform orDefault(GeoItemTransform t, GeoItemTransform fallback) {
        return t != null ? t : fallback;
    }
}
