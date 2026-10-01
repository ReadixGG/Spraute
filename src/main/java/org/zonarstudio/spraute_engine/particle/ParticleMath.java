package org.zonarstudio.spraute_engine.particle;

import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministic helpers shared by legacy migration instances. */
public final class ParticleMath {
    private ParticleMath() {}

    public static long mixSeed(long seed, long serial) {
        long z = seed + 0x9E3779B97F4A7C15L * (serial + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static int randomColor(int min, int max, Random random) {
        return lerpColor(min, max, random == null ? 0f : random.nextFloat());
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = a >>> 24 & 255, ar = a >>> 16 & 255, ag = a >>> 8 & 255, ab = a & 255;
        int ba = b >>> 24 & 255, br = b >>> 16 & 255, bg = b >>> 8 & 255, bb = b & 255;
        return Mth.clamp((int) Mth.lerp(t, aa, ba), 0, 255) << 24
                | Mth.clamp((int) Mth.lerp(t, ar, br), 0, 255) << 16
                | Mth.clamp((int) Mth.lerp(t, ag, bg), 0, 255) << 8
                | Mth.clamp((int) Mth.lerp(t, ab, bb), 0, 255);
    }

    /**
     * Evaluates simple constants, [start,end] arrays/lists and keyframe maps.
     * Unknown values safely evaluate to zero.
     */
    public static float curve(Object value, float t) {
        t = Mth.clamp(t, 0f, 1f);
        if (value instanceof Number number) return number.floatValue();
        if (value instanceof float[] points && points.length > 0) {
            return sample(points, t);
        }
        if (value instanceof double[] points && points.length > 0) {
            float[] converted = new float[points.length];
            for (int i = 0; i < points.length; i++) converted[i] = (float) points[i];
            return sample(converted, t);
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            float[] points = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object point = list.get(i);
                if (point instanceof Number number) points[i] = number.floatValue();
                else if (point instanceof Map<?, ?> map && map.get("value") instanceof Number number) {
                    points[i] = number.floatValue();
                }
            }
            return sample(points, t);
        }
        return 0f;
    }

    private static float sample(float[] points, float t) {
        if (points.length == 1) return points[0];
        float position = t * (points.length - 1);
        int left = Math.min(points.length - 2, (int) position);
        return Mth.lerp(position - left, points[left], points[left + 1]);
    }
}
