package org.zonarstudio.spraute_engine.scene;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Unity Volume / Unreal Post Process Volume settings living on a scene node.
 * Intensity {@code 0} / override off = identity (no visual change).
 */
public final class PostProcessSettings {
    public boolean enabled = true;
    /** Unbound volume (affects the whole camera). Local uses the node box. */
    public boolean global = true;
    public int priority;
    public float weight = 1f;
    /** Fade outside the local box, in blocks. */
    public float blendDistance = 8f;

    public boolean grade;
    public float exposure;
    public float contrast;
    public float saturation;
    public float temperature;
    public float tint;
    public float filterR = 1f;
    public float filterG = 1f;
    public float filterB = 1f;

    public boolean vignette;
    public float vignetteIntensity = 0.4f;
    public float vignetteSmooth = 0.45f;

    public boolean bloom;
    public float bloomIntensity = 0.35f;
    public float bloomThreshold = 0.75f;

    public boolean chromatic;
    public float chromaticAmount = 0.004f;

    public boolean grain;
    public float grainAmount = 0.06f;

    public boolean sharpen;
    public float sharpenAmount = 0.2f;

    public PostProcessSettings copy() {
        PostProcessSettings c = new PostProcessSettings();
        copyInto(c);
        return c;
    }

    public void copyInto(PostProcessSettings c) {
        if (c == null) return;
        c.enabled = enabled;
        c.global = global;
        c.priority = priority;
        c.weight = weight;
        c.blendDistance = blendDistance;
        c.grade = grade;
        c.exposure = exposure;
        c.contrast = contrast;
        c.saturation = saturation;
        c.temperature = temperature;
        c.tint = tint;
        c.filterR = filterR;
        c.filterG = filterG;
        c.filterB = filterB;
        c.vignette = vignette;
        c.vignetteIntensity = vignetteIntensity;
        c.vignetteSmooth = vignetteSmooth;
        c.bloom = bloom;
        c.bloomIntensity = bloomIntensity;
        c.bloomThreshold = bloomThreshold;
        c.chromatic = chromatic;
        c.chromaticAmount = chromaticAmount;
        c.grain = grain;
        c.grainAmount = grainAmount;
        c.sharpen = sharpen;
        c.sharpenAmount = sharpenAmount;
    }

    public static PostProcessSettings identity() {
        PostProcessSettings s = new PostProcessSettings();
        // Authoring defaults are intentionally visible when an override is enabled,
        // but the blend accumulator must start at a true zero-effect state.
        s.vignetteIntensity = 0f;
        s.bloomIntensity = 0f;
        s.chromaticAmount = 0f;
        s.grainAmount = 0f;
        s.sharpenAmount = 0f;
        return s;
    }

    public boolean hasAnyOverride() {
        return grade || vignette || bloom || chromatic || grain || sharpen;
    }

    public boolean isVisuallyIdentity() {
        if (!enabled || weight <= 0.001f || !hasAnyOverride()) return true;
        if (grade && (Math.abs(exposure) > 0.001f || Math.abs(contrast) > 0.001f
                || Math.abs(saturation) > 0.001f || Math.abs(temperature) > 0.001f
                || Math.abs(tint) > 0.001f
                || Math.abs(filterR - 1f) > 0.001f || Math.abs(filterG - 1f) > 0.001f
                || Math.abs(filterB - 1f) > 0.001f)) return false;
        if (vignette && vignetteIntensity > 0.001f) return false;
        if (bloom && bloomIntensity > 0.001f) return false;
        if (chromatic && chromaticAmount > 0.0001f) return false;
        if (grain && grainAmount > 0.001f) return false;
        if (sharpen && sharpenAmount > 0.001f) return false;
        return true;
    }

    /** Camera influence 0–1 for this volume at world position. */
    public float influence(double cx, double cy, double cz, SceneTransform world) {
        if (!enabled || !hasAnyOverride()) return 0f;
        float w = Mth.clamp(weight, 0f, 1f);
        if (w <= 0f) return 0f;
        if (global) return w;
        double dist = distanceOutside(world, cx, cy, cz);
        if (blendDistance <= 0.001f) return dist <= 0.0 ? w : 0f;
        return w * (1f - Mth.clamp((float) (dist / blendDistance), 0f, 1f));
    }

    /** Axis-aligned bounds enclosing the rotated local volume (for picking/marquee). */
    public static AABB worldAabb(SceneTransform world) {
        if (world == null) return new AABB(0, 0, 0, 0, 0, 0);
        Vec3[] corners = orientedCorners(world, 0);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 p : corners) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Distance from a point to the rotated box; zero while inside. */
    public static double distanceOutside(SceneTransform world, double x, double y, double z) {
        if (world == null) return Double.POSITIVE_INFINITY;
        Vec3 local = inverseRotate(new Vec3(x - world.px, y - world.py, z - world.pz), world);
        double hx = Math.max(0.05, Math.abs(world.sx) * 0.5);
        double hy = Math.max(0.05, Math.abs(world.sy) * 0.5);
        double hz = Math.max(0.05, Math.abs(world.sz) * 0.5);
        double dx = Math.max(Math.abs(local.x) - hx, 0);
        double dy = Math.max(Math.abs(local.y) - hy, 0);
        double dz = Math.max(Math.abs(local.z) - hz, 0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Rotated box corners; {@code inflate} expands each local half-extent in blocks. */
    public static Vec3[] orientedCorners(SceneTransform world, double inflate) {
        if (world == null) return new Vec3[0];
        double hx = Math.max(0.05, Math.abs(world.sx) * 0.5) + Math.max(0, inflate);
        double hy = Math.max(0.05, Math.abs(world.sy) * 0.5) + Math.max(0, inflate);
        double hz = Math.max(0.05, Math.abs(world.sz) * 0.5) + Math.max(0, inflate);
        Vec3[] out = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vec3 p = new Vec3((i & 1) == 0 ? -hx : hx,
                    (i & 2) == 0 ? -hy : hy,
                    (i & 4) == 0 ? -hz : hz);
            p = rotate(p, world);
            out[i] = p.add(world.px, world.py, world.pz);
        }
        return out;
    }

    /** Same Euler order as editor/world previews: Y, then X, then Z. */
    private static Vec3 rotate(Vec3 v, SceneTransform world) {
        v = rotateZ(v, Math.toRadians(world.rz));
        v = rotateX(v, Math.toRadians(world.rx));
        return rotateY(v, Math.toRadians(world.ry));
    }

    private static Vec3 inverseRotate(Vec3 v, SceneTransform world) {
        v = rotateY(v, Math.toRadians(-world.ry));
        v = rotateX(v, Math.toRadians(-world.rx));
        return rotateZ(v, Math.toRadians(-world.rz));
    }

    private static Vec3 rotateX(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    private static Vec3 rotateY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    private static Vec3 rotateZ(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }

    /** Blend {@code src} into {@code dst} with weight {@code t} (Unity stack: low priority first). */
    public static void blendInto(PostProcessSettings dst, PostProcessSettings src, float t) {
        if (dst == null || src == null || t <= 0f) return;
        t = Mth.clamp(t, 0f, 1f);
        if (src.grade) {
            dst.grade = true;
            dst.exposure = lerp(dst.exposure, src.exposure, t);
            dst.contrast = lerp(dst.contrast, src.contrast, t);
            dst.saturation = lerp(dst.saturation, src.saturation, t);
            dst.temperature = lerp(dst.temperature, src.temperature, t);
            dst.tint = lerp(dst.tint, src.tint, t);
            dst.filterR = lerp(dst.filterR, src.filterR, t);
            dst.filterG = lerp(dst.filterG, src.filterG, t);
            dst.filterB = lerp(dst.filterB, src.filterB, t);
        }
        if (src.vignette) {
            dst.vignette = true;
            dst.vignetteIntensity = lerp(dst.vignetteIntensity, src.vignetteIntensity, t);
            dst.vignetteSmooth = lerp(dst.vignetteSmooth, src.vignetteSmooth, t);
        }
        if (src.bloom) {
            dst.bloom = true;
            dst.bloomIntensity = lerp(dst.bloomIntensity, src.bloomIntensity, t);
            dst.bloomThreshold = lerp(dst.bloomThreshold, src.bloomThreshold, t);
        }
        if (src.chromatic) {
            dst.chromatic = true;
            dst.chromaticAmount = lerp(dst.chromaticAmount, src.chromaticAmount, t);
        }
        if (src.grain) {
            dst.grain = true;
            dst.grainAmount = lerp(dst.grainAmount, src.grainAmount, t);
        }
        if (src.sharpen) {
            dst.sharpen = true;
            dst.sharpenAmount = lerp(dst.sharpenAmount, src.sharpenAmount, t);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeBoolean(global);
        buf.writeVarInt(priority);
        buf.writeFloat(weight);
        buf.writeFloat(blendDistance);
        buf.writeBoolean(grade);
        buf.writeFloat(exposure);
        buf.writeFloat(contrast);
        buf.writeFloat(saturation);
        buf.writeFloat(temperature);
        buf.writeFloat(tint);
        buf.writeFloat(filterR);
        buf.writeFloat(filterG);
        buf.writeFloat(filterB);
        buf.writeBoolean(vignette);
        buf.writeFloat(vignetteIntensity);
        buf.writeFloat(vignetteSmooth);
        buf.writeBoolean(bloom);
        buf.writeFloat(bloomIntensity);
        buf.writeFloat(bloomThreshold);
        buf.writeBoolean(chromatic);
        buf.writeFloat(chromaticAmount);
        buf.writeBoolean(grain);
        buf.writeFloat(grainAmount);
        buf.writeBoolean(sharpen);
        buf.writeFloat(sharpenAmount);
    }

    public static PostProcessSettings decode(FriendlyByteBuf buf) {
        PostProcessSettings s = new PostProcessSettings();
        s.enabled = buf.readBoolean();
        s.global = buf.readBoolean();
        s.priority = buf.readVarInt();
        s.weight = buf.readFloat();
        s.blendDistance = buf.readFloat();
        s.grade = buf.readBoolean();
        s.exposure = buf.readFloat();
        s.contrast = buf.readFloat();
        s.saturation = buf.readFloat();
        s.temperature = buf.readFloat();
        s.tint = buf.readFloat();
        s.filterR = buf.readFloat();
        s.filterG = buf.readFloat();
        s.filterB = buf.readFloat();
        s.vignette = buf.readBoolean();
        s.vignetteIntensity = buf.readFloat();
        s.vignetteSmooth = buf.readFloat();
        s.bloom = buf.readBoolean();
        s.bloomIntensity = buf.readFloat();
        s.bloomThreshold = buf.readFloat();
        s.chromatic = buf.readBoolean();
        s.chromaticAmount = buf.readFloat();
        s.grain = buf.readBoolean();
        s.grainAmount = buf.readFloat();
        s.sharpen = buf.readBoolean();
        s.sharpenAmount = buf.readFloat();
        return s;
    }

    public String fingerprint() {
        return enabled + "|" + global + "|" + priority + "|" + round(weight) + "|" + round(blendDistance)
                + "|" + grade + "|" + round(exposure) + "|" + round(contrast) + "|" + round(saturation)
                + "|" + round(temperature) + "|" + round(tint)
                + "|" + round(filterR) + "|" + round(filterG) + "|" + round(filterB)
                + "|" + vignette + "|" + round(vignetteIntensity) + "|" + round(vignetteSmooth)
                + "|" + bloom + "|" + round(bloomIntensity) + "|" + round(bloomThreshold)
                + "|" + chromatic + "|" + round(chromaticAmount)
                + "|" + grain + "|" + round(grainAmount)
                + "|" + sharpen + "|" + round(sharpenAmount);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static String round(float v) {
        return String.valueOf(Math.round(v * 1000f) / 1000f);
    }
}
