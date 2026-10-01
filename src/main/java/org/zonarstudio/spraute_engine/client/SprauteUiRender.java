package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;

/**
 * ImGui-style vector renderer for script GUIs: rounded rectangles, per-vertex
 * gradients, borders and soft drop shadows drawn with POSITION_COLOR triangles
 * (the same approach ImGui uses in its draw lists). Works on 1.19.2 and 1.20.1.
 */
public final class SprauteUiRender {
    private SprauteUiRender() {}

    private static final int CORNER_SEGMENTS = 8;

    /** Extra widget style: radius / gradient / inner border / outer border / shadow. */
    public record Style(float radius, int[] gradientStops, float gradientAngle,
                        float borderWidth, int borderColor, int[] borderStops, float borderAngle,
                        float shadowSize,
                        float outerBorderWidth, int outerBorderColor, int[] outerBorderStops, float outerBorderAngle) {
        public static final Style NONE = new Style(0f, null, 90f, 0f, 0, null, 90f, 0f, 0f, 0, null, 90f);

        /** Legacy-signature convenience constructor (no border gradient / outer border). */
        public Style(float radius, int[] gradientStops, float gradientAngle,
                     float borderWidth, int borderColor, float shadowSize) {
            this(radius, gradientStops, gradientAngle, borderWidth, borderColor, null, 90f, shadowSize,
                    0f, 0, null, 90f);
        }

        /** Previous canonical form without outer border. */
        public Style(float radius, int[] gradientStops, float gradientAngle,
                     float borderWidth, int borderColor, int[] borderStops, float borderAngle,
                     float shadowSize) {
            this(radius, gradientStops, gradientAngle, borderWidth, borderColor, borderStops, borderAngle, shadowSize,
                    0f, 0, null, 90f);
        }

        public boolean hasGradient() {
            return gradientStops != null && gradientStops.length > 0;
        }

        public boolean hasBorderGradient() {
            return borderStops != null && borderStops.length >= 2;
        }

        public boolean hasOuterBorderGradient() {
            return outerBorderStops != null && outerBorderStops.length >= 2;
        }

        public boolean isPlain() {
            return radius <= 0f && !hasGradient() && borderWidth <= 0f && outerBorderWidth <= 0f && shadowSize <= 0f;
        }
    }

    /** Fills a (possibly rounded, possibly gradient) rectangle with optional shadow and borders. */
    public static void panel(PoseStack pose, float x0, float y0, float x1, float y1, int color, Style st) {
        if (st == null) st = Style.NONE;
        // Shadow follows the outer silhouette (widget + outer outline), not the inner fill alone.
        float outer = Math.max(0f, st.outerBorderWidth());
        if (st.shadowSize() > 0f) {
            shadow(pose, x0 - outer, y0 - outer, x1 + outer, y1 + outer,
                    st.radius() + outer, st.shadowSize());
        }
        int[] stops;
        if (st.hasGradient()) {
            if (st.gradientStops().length >= 2) {
                // Full gradient: all colors come from the gradient itself, base color unused.
                stops = st.gradientStops();
            } else {
                // Legacy single second color: base color -> gradient color.
                stops = new int[]{color, st.gradientStops()[0]};
            }
        } else {
            stops = new int[]{color};
        }
        fillRounded(pose, x0, y0, x1, y1, st.radius(), stops, st.gradientAngle());
        // Outer stroke sits outside the widget rect (expand by thickness).
        if (outer > 0f) {
            float ox0 = x0 - outer, oy0 = y0 - outer, ox1 = x1 + outer, oy1 = y1 + outer;
            float or = st.radius() + outer;
            if (st.hasOuterBorderGradient()) {
                outlineRounded(pose, ox0, oy0, ox1, oy1, or, outer, st.outerBorderStops(), st.outerBorderAngle());
            } else if ((st.outerBorderColor() >>> 24) != 0) {
                outlineRounded(pose, ox0, oy0, ox1, oy1, or, outer, st.outerBorderColor());
            }
        }
        // Inner stroke is drawn inward from the edge.
        if (st.borderWidth() > 0f) {
            if (st.hasBorderGradient()) {
                outlineRounded(pose, x0, y0, x1, y1, st.radius(), st.borderWidth(),
                        st.borderStops(), st.borderAngle());
            } else if ((st.borderColor() >>> 24) != 0) {
                outlineRounded(pose, x0, y0, x1, y1, st.radius(), st.borderWidth(), st.borderColor());
            }
        }
    }

    /** Two-color convenience overload (0° = left→right, 90° = top→bottom). */
    public static void fillRounded(PoseStack pose, float x0, float y0, float x1, float y1,
                                   float radius, int c1, int c2, boolean horizontal) {
        fillRounded(pose, x0, y0, x1, y1, radius, new int[]{c1, c2}, horizontal ? 0f : 90f);
    }

    /** Rounded-rect fill with a multi-stop gradient along an arbitrary angle (degrees). */
    public static void fillRounded(PoseStack pose, float x0, float y0, float x1, float y1,
                                   float radius, int[] stops, float angleDeg) {
        if (stops == null || stops.length == 0) return;
        boolean anyVisible = false;
        for (int c : stops) if ((c >>> 24) != 0) { anyVisible = true; break; }
        if (!anyVisible) return;
        float w = x1 - x0, h = y1 - y0;
        if (w <= 0f || h <= 0f) return;
        float r = Math.min(radius, Math.min(w, h) * 0.5f);

        // Gradient axis: project each vertex onto the angle direction, normalized 0..1 over the rect.
        double ang = Math.toRadians(angleDeg);
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float span = Math.abs(dx) * w + Math.abs(dy) * h;
        float ox = dx >= 0 ? x0 : x1;
        float oy = dy >= 0 ? y0 : y1;

        float[] pts = buildPath(x0, y0, x1, y1, r);
        BufferBuilder buf = begin();
        float cx = (x0 + x1) * 0.5f, cy = (y0 + y1) * 0.5f;
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = pts[i * 2], ay = pts[i * 2 + 1];
            float bx = pts[j * 2], by = pts[j * 2 + 1];
            vertex(buf, pose, cx, cy, gradientAt(stops, ((cx - ox) * dx + (cy - oy) * dy) / span));
            vertex(buf, pose, ax, ay, gradientAt(stops, ((ax - ox) * dx + (ay - oy) * dy) / span));
            vertex(buf, pose, bx, by, gradientAt(stops, ((bx - ox) * dx + (by - oy) * dy) / span));
        }
        end(buf);
    }

    /** Color at position t (0..1) across evenly spaced stops. */
    private static int gradientAt(int[] stops, float t) {
        if (stops.length == 1) return stops[0];
        t = Math.max(0f, Math.min(1f, t));
        float scaled = t * (stops.length - 1);
        int idx = Math.min(stops.length - 2, (int) scaled);
        return lerpColor(stops[idx], stops[idx + 1], scaled - idx);
    }

    /** Rounded-rect stroke of the given thickness (drawn inward from the edge). */
    public static void outlineRounded(PoseStack pose, float x0, float y0, float x1, float y1,
                                      float radius, float thickness, int color) {
        if ((color >>> 24) == 0) return;
        outlineRounded(pose, x0, y0, x1, y1, radius, thickness, new int[]{color}, 90f);
    }

    /** Rounded-rect stroke with a multi-stop gradient along an arbitrary angle. */
    public static void outlineRounded(PoseStack pose, float x0, float y0, float x1, float y1,
                                      float radius, float thickness, int[] stops, float angleDeg) {
        if (stops == null || stops.length == 0) return;
        boolean anyVisible = false;
        for (int c : stops) if ((c >>> 24) != 0) { anyVisible = true; break; }
        if (!anyVisible) return;
        float w = x1 - x0, h = y1 - y0;
        if (w <= 0f || h <= 0f) return;
        float r = Math.min(radius, Math.min(w, h) * 0.5f);
        float t = Math.min(thickness, Math.min(w, h) * 0.5f);
        float ri = Math.max(0f, r - t);

        // Gradient axis, same projection scheme as fillRounded.
        double ang = Math.toRadians(angleDeg);
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float span = Math.abs(dx) * w + Math.abs(dy) * h;
        float ox = dx >= 0 ? x0 : x1;
        float oy = dy >= 0 ? y0 : y1;

        // Both paths must have identical vertex counts: force the segmented
        // form for the inner path even when its radius collapses to 0.
        float[] outer = buildPath(x0, y0, x1, y1, r);
        float[] inner = r > 0.5f
                ? buildPathSegmented(x0 + t, y0 + t, x1 - t, y1 - t, ri)
                : buildPath(x0 + t, y0 + t, x1 - t, y1 - t, 0f);
        int n = outer.length / 2;
        BufferBuilder buf = begin();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int co0 = gradientAt(stops, ((outer[i * 2] - ox) * dx + (outer[i * 2 + 1] - oy) * dy) / span);
            int ci0 = gradientAt(stops, ((inner[i * 2] - ox) * dx + (inner[i * 2 + 1] - oy) * dy) / span);
            int ci1 = gradientAt(stops, ((inner[j * 2] - ox) * dx + (inner[j * 2 + 1] - oy) * dy) / span);
            int co1 = gradientAt(stops, ((outer[j * 2] - ox) * dx + (outer[j * 2 + 1] - oy) * dy) / span);
            vertex(buf, pose, outer[i * 2], outer[i * 2 + 1], co0);
            vertex(buf, pose, inner[i * 2], inner[i * 2 + 1], ci0);
            vertex(buf, pose, inner[j * 2], inner[j * 2 + 1], ci1);
            vertex(buf, pose, outer[i * 2], outer[i * 2 + 1], co0);
            vertex(buf, pose, inner[j * 2], inner[j * 2 + 1], ci1);
            vertex(buf, pose, outer[j * 2], outer[j * 2 + 1], co1);
        }
        end(buf);
    }

    /**
     * Soft drop shadow under the given silhouette (already expanded for outer outline).
     * Stacked filled rounded rects with quadratic alpha falloff — not outline rings,
     * so it reads as a blob under the shape rather than a second border.
     */
    public static void shadow(PoseStack pose, float x0, float y0, float x1, float y1,
                              float radius, float size) {
        if (size <= 0f) return;
        float w = x1 - x0, h = y1 - y0;
        if (w <= 0f || h <= 0f) return;
        float r = Math.max(0f, radius);
        // Slight downward offset so it reads as a drop shadow, not a glow.
        float ox = size * 0.15f;
        float oy = size * 0.35f;
        int rings = Math.max(6, Math.min(12, (int) (size * 1.5f)));
        int baseAlpha = 0x58;
        // Outside → inside so softer layers sit behind denser ones.
        for (int ring = rings; ring >= 1; ring--) {
            float t = (float) ring / rings;
            float grow = size * t;
            float f = 1f - t;
            int a = (int) (baseAlpha * f * f);
            if (a <= 0) continue;
            int color = a << 24;
            fillRounded(pose,
                    x0 - grow + ox, y0 - grow + oy,
                    x1 + grow + ox, y1 + grow + oy,
                    r + grow, new int[]{color}, 90f);
        }
    }

    // ---- internals ----

    /** Closed clockwise outline of a rounded rect; corner arcs approximated with segments. */
    private static float[] buildPath(float x0, float y0, float x1, float y1, float r) {
        if (r <= 0.5f) {
            return new float[]{x0, y0, x1, y0, x1, y1, x0, y1};
        }
        return buildPathSegmented(x0, y0, x1, y1, r);
    }

    /** Segmented form regardless of radius (r = 0 degenerates arcs into corner points). */
    private static float[] buildPathSegmented(float x0, float y0, float x1, float y1, float r) {
        r = Math.max(0f, r);
        int seg = CORNER_SEGMENTS;
        float[] pts = new float[(seg + 1) * 4 * 2];
        int idx = 0;
        float[][] corners = {
                {x0 + r, y0 + r, (float) Math.PI},
                {x1 - r, y0 + r, (float) (Math.PI * 1.5)},
                {x1 - r, y1 - r, 0f},
                {x0 + r, y1 - r, (float) (Math.PI * 0.5)}
        };
        for (float[] c : corners) {
            for (int s = 0; s <= seg; s++) {
                float a = c[2] + (float) (Math.PI * 0.5) * s / seg;
                pts[idx++] = c[0] + (float) Math.cos(a) * r;
                pts[idx++] = c[1] + (float) Math.sin(a) * r;
            }
        }
        return pts;
    }

    private static int lerpColor(int c1, int c2, float t) {
        if (c1 == c2) return c1;
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + t * (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)));
        int r = (int) (((c1 >> 16) & 0xFF) + t * (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)));
        int g = (int) (((c1 >> 8) & 0xFF) + t * (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)));
        int b = (int) ((c1 & 0xFF) + t * ((c2 & 0xFF) - (c1 & 0xFF)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static BufferBuilder begin() {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    private static void vertex(BufferBuilder buf, PoseStack pose, float x, float y, int argb) {
        buf.vertex(pose.last().pose(), x, y, 0f)
           .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
           .endVertex();
    }

    private static void end(BufferBuilder buf) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
