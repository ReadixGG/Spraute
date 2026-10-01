package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Unity/Godot-style image fill: crop a texture (or solid panel) left-to-right,
 * right-to-left, top-to-bottom, bottom-to-top, or as a radial pie
 * (clockwise / counterclockwise).
 */
public final class UiImageFill {
    public static final String NONE = "none";
    public static final String LTR = "ltr";
    public static final String RTL = "rtl";
    public static final String TTB = "ttb";
    public static final String BTT = "btt";
    public static final String CW = "clockwise";
    public static final String CCW = "counterclockwise";

    public static final String SHAPE_RECT = "rect";
    public static final String SHAPE_CIRCLE = "circle";

    private static final int RADIAL_STEPS = 64;

    private UiImageFill() {}

    public static String normalize(String raw) {
        if (raw == null) return NONE;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (s) {
            case "", "none", "full", "off", "выкл" -> NONE;
            case "ltr", "left_to_right", "lefttoright", "horizontal", "left",
                 "слева_направо", "слева" -> LTR;
            case "rtl", "right_to_left", "righttoleft", "right",
                 "справа_налево", "справа" -> RTL;
            case "ttb", "top_to_bottom", "toptobottom", "vertical", "down", "top",
                 "сверху_вниз", "сверху" -> TTB;
            case "btt", "bottom_to_top", "bottomtotop", "up", "bottom",
                 "снизу_вверх", "снизу" -> BTT;
            case "cw", "clockwise", "clock", "radial", "radial_cw", "circle",
                 "по_часовой", "часовая" -> CW;
            case "ccw", "counterclockwise", "counter_clockwise", "anticlockwise",
                 "counter", "radial_ccw",
                 "против_часовой", "против" -> CCW;
            default -> NONE;
        };
    }

    public static String normalizeOrigin(String raw) {
        if (raw == null) return "bottom";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (s) {
            case "left", "l", "слева" -> "left";
            case "right", "r", "справа" -> "right";
            case "top", "t", "сверху" -> "top";
            case "bottom", "b", "снизу" -> "bottom";
            default -> "bottom";
        };
    }

    /** Crop / clip silhouette: rect (default) or circle/ellipse inscribed in the widget. */
    public static String normalizeShape(String raw) {
        if (raw == null) return SHAPE_RECT;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (s) {
            case "circle", "ellipse", "oval", "round", "disk",
                 "круг", "эллипс", "овал" -> SHAPE_CIRCLE;
            default -> SHAPE_RECT;
        };
    }

    public static boolean isCircle(String shape) {
        return SHAPE_CIRCLE.equals(normalizeShape(shape));
    }

    public static boolean contains(int x, int y, int w, int h, int mx, int my, String shape) {
        if (mx < x || my < y || mx >= x + w || my >= y + h) return false;
        if (!isCircle(shape)) return true;
        float rx = Math.max(1f, w * 0.5f);
        float ry = Math.max(1f, h * 0.5f);
        float nx = (mx - (x + w * 0.5f)) / rx;
        float ny = (my - (y + h * 0.5f)) / ry;
        return nx * nx + ny * ny <= 1f;
    }

    public static float clampAmount(float amount) {
        if (Float.isNaN(amount)) return 1f;
        return Math.max(0f, Math.min(1f, amount));
    }

    /** True when the widget should draw its full unclipped shape. */
    public static boolean isFull(String fill, float amount) {
        float a = clampAmount(amount);
        if (a >= 0.999f) return true;
        String mode = normalize(fill);
        return NONE.equals(mode) && a >= 0.999f;
    }

    public static boolean isRadial(String fill) {
        String mode = normalize(fill);
        return CW.equals(mode) || CCW.equals(mode);
    }

    public static boolean isLinear(String fill) {
        String mode = normalize(fill);
        return LTR.equals(mode) || RTL.equals(mode) || TTB.equals(mode) || BTT.equals(mode);
    }

    /**
     * When fill is empty but amount &lt; 1, default to left-to-right
     * (so {@code uiUpdate(..., "fillAmount", 0.4)} works alone).
     */
    public static String effectiveMode(String fill, float amount) {
        String mode = normalize(fill);
        if (NONE.equals(mode) && clampAmount(amount) < 0.999f) return LTR;
        return mode;
    }

    public static void blitTexture(PoseStack pose, ResourceLocation texture,
                                   int x, int y, int w, int h,
                                   int srcU, int srcV, int srcW, int srcH,
                                   int texW, int texH,
                                   String fill, float amount, String origin, float alpha) {
        blitTexture(pose, texture, x, y, w, h, srcU, srcV, srcW, srcH, texW, texH,
                fill, amount, origin, alpha, SHAPE_RECT);
    }

    public static void blitTexture(PoseStack pose, ResourceLocation texture,
                                   int x, int y, int w, int h,
                                   int srcU, int srcV, int srcW, int srcH,
                                   int texW, int texH,
                                   String fill, float amount, String origin, float alpha, String shape) {
        if (w <= 0 || h <= 0 || texture == null) return;
        float a = clampAmount(amount);
        if (a <= 0.001f) return;
        int tw = Math.max(1, texW);
        int th = Math.max(1, texH);
        int su = srcW > 0 ? srcU : 0;
        int sv = srcW > 0 ? srcV : 0;
        int sw = srcW > 0 ? srcW : tw;
        int sh = srcW > 0 ? srcH : th;
        if (sw <= 0) sw = tw;
        if (sh <= 0) sh = th;

        String mode = effectiveMode(fill, a);
        boolean circle = isCircle(shape);
        if (circle) {
            if (isRadial(mode) && !isFull(fill, a)) {
                radialTexture(pose, texture, x, y, w, h, su, sv, sw, sh, tw, th,
                        a, CW.equals(mode), normalizeOrigin(origin), alpha);
                return;
            }
            if (!isFull(fill, a) && !NONE.equals(mode)) {
                int[] box = linearBox(x, y, w, h, su, sv, sw, sh, mode, a);
                pushGuiScissor(box[0], box[1], box[2], box[3]);
                radialTexture(pose, texture, x, y, w, h, su, sv, sw, sh, tw, th,
                        1f, true, "bottom", alpha);
                popGuiScissor();
                return;
            }
            radialTexture(pose, texture, x, y, w, h, su, sv, sw, sh, tw, th,
                    1f, true, "bottom", alpha);
            return;
        }

        if (isFull(fill, a) || NONE.equals(mode)) {
            texturedQuad(pose, texture, x, y, w, h, su, sv, sw, sh, tw, th, alpha);
            return;
        }
        if (isRadial(mode)) {
            radialTexture(pose, texture, x, y, w, h, su, sv, sw, sh, tw, th,
                    a, CW.equals(mode), normalizeOrigin(origin), alpha);
            return;
        }
        linearTexturedQuad(pose, texture, x, y, w, h, su, sv, sw, sh,
                tw, th, mode, a, alpha);
    }

    public static void fillColor(PoseStack pose, int x, int y, int w, int h, int argb,
                                 String fill, float amount, String origin) {
        fillColor(pose, x, y, w, h, argb, fill, amount, origin, SHAPE_RECT);
    }

    public static void fillColor(PoseStack pose, int x, int y, int w, int h, int argb,
                                 String fill, float amount, String origin, String shape) {
        if (w <= 0 || h <= 0) return;
        float a = clampAmount(amount);
        if (a <= 0.001f) return;
        if ((argb & 0xFF000000) == 0) return;
        String mode = effectiveMode(fill, a);
        boolean circle = isCircle(shape);
        if (circle) {
            if (isRadial(mode) && !isFull(fill, a)) {
                radialColor(pose, x, y, w, h, argb, a, CW.equals(mode), normalizeOrigin(origin));
                return;
            }
            if (!isFull(fill, a) && !NONE.equals(mode)) {
                int[] box = linearBox(x, y, w, h, 0, 0, w, h, mode, a);
                pushGuiScissor(box[0], box[1], box[2], box[3]);
                radialColor(pose, x, y, w, h, argb, 1f, true, "bottom");
                popGuiScissor();
                return;
            }
            radialColor(pose, x, y, w, h, argb, 1f, true, "bottom");
            return;
        }
        if (isFull(fill, a) || NONE.equals(mode)) {
            colorQuad(pose, x, y, w, h, argb);
            return;
        }
        if (isRadial(mode)) {
            radialColor(pose, x, y, w, h, argb, a, CW.equals(mode), normalizeOrigin(origin));
            return;
        }
        int[] box = linearBox(x, y, w, h, 0, 0, w, h, mode, a);
        colorQuad(pose, box[0], box[1], box[2], box[3], argb);
    }

    private static void pushGuiScissor(int x, int y, int w, int h) {
        var win = Minecraft.getInstance().getWindow();
        double s = win.getGuiScale();
        int sx = (int) Math.round(x * s);
        int sy = (int) Math.round(win.getHeight() - (y + h) * s);
        int sw = Math.max(1, (int) Math.round(w * s));
        int sh = Math.max(1, (int) Math.round(h * s));
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    private static void popGuiScissor() {
        RenderSystem.disableScissor();
    }

    /** dest x,y,w,h + src u,v,w,h */
    public static int[] linearBox(int x, int y, int w, int h, int su, int sv, int sw, int sh,
                           String mode, float amount) {
        float t = clampAmount(amount);
        return switch (mode) {
            case RTL -> {
                int dw = Math.max(1, Math.round(w * t));
                int ssw = Math.max(1, Math.round(sw * t));
                yield new int[]{x + w - dw, y, dw, h, su + sw - ssw, sv, ssw, sh};
            }
            case TTB -> {
                int dh = Math.max(1, Math.round(h * t));
                int ssh = Math.max(1, Math.round(sh * t));
                yield new int[]{x, y, w, dh, su, sv, sw, ssh};
            }
            case BTT -> {
                int dh = Math.max(1, Math.round(h * t));
                int ssh = Math.max(1, Math.round(sh * t));
                yield new int[]{x, y + h - dh, w, dh, su, sv + sh - ssh, sw, ssh};
            }
            default -> { // LTR
                int dw = Math.max(1, Math.round(w * t));
                int ssw = Math.max(1, Math.round(sw * t));
                yield new int[]{x, y, dw, h, su, sv, ssw, sh};
            }
        };
    }

    private static float originAngle(String origin) {
        // Screen: +X right, +Y down. 0 = right.
        return switch (normalizeOrigin(origin)) {
            case "right" -> 0f;
            case "bottom" -> (float) (Math.PI * 0.5);
            case "left" -> (float) Math.PI;
            case "top" -> (float) (-Math.PI * 0.5);
            default -> (float) (Math.PI * 0.5);
        };
    }

    private static void radialTexture(PoseStack pose, ResourceLocation texture,
                                      int x, int y, int w, int h,
                                      int su, int sv, int sw, int sh, int tw, int th,
                                      float amount, boolean clockwise, String origin, float alpha) {
        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;
        float start = originAngle(origin);
        float sign = clockwise ? 1f : -1f;
        float segmentCount = RADIAL_STEPS * clampAmount(amount);
        int fullSegments = Math.min(RADIAL_STEPS, (int) Math.floor(segmentCount));
        float partial = segmentCount - fullSegments;
        float segmentAngle = (float) (Math.PI * 2.0 / RADIAL_STEPS);

        setupTex(texture, alpha);
        BufferBuilder buf = beginTex();
        for (int i = 0; i < fullSegments; i++) {
            radialTexTriangle(buf, pose, cx, cy, rx, ry, start, sign,
                    i * segmentAngle, (i + 1) * segmentAngle,
                    su, sv, sw, sh, tw, th, x, y, w, h);
        }
        if (partial > 0.0001f && fullSegments < RADIAL_STEPS) {
            radialTexTriangle(buf, pose, cx, cy, rx, ry, start, sign,
                    fullSegments * segmentAngle, (fullSegments + partial) * segmentAngle,
                    su, sv, sw, sh, tw, th, x, y, w, h);
        }
        endTex(buf);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void radialColor(PoseStack pose, int x, int y, int w, int h, int argb,
                                    float amount, boolean clockwise, String origin) {
        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        float rx = w * 0.5f;
        float ry = h * 0.5f;
        float start = originAngle(origin);
        float sign = clockwise ? 1f : -1f;
        float segmentCount = RADIAL_STEPS * clampAmount(amount);
        int fullSegments = Math.min(RADIAL_STEPS, (int) Math.floor(segmentCount));
        float partial = segmentCount - fullSegments;
        float segmentAngle = (float) (Math.PI * 2.0 / RADIAL_STEPS);

        BufferBuilder buf = beginColor();
        for (int i = 0; i < fullSegments; i++) {
            radialColorTriangle(buf, pose, cx, cy, rx, ry, start, sign,
                    i * segmentAngle, (i + 1) * segmentAngle, argb);
        }
        if (partial > 0.0001f && fullSegments < RADIAL_STEPS) {
            radialColorTriangle(buf, pose, cx, cy, rx, ry, start, sign,
                    fullSegments * segmentAngle, (fullSegments + partial) * segmentAngle, argb);
        }
        endColor(buf);
    }

    private static void radialTexTriangle(BufferBuilder buf, PoseStack pose,
                                          float cx, float cy, float rx, float ry,
                                          float start, float sign, float from, float to,
                                          int su, int sv, int sw, int sh, int tw, int th,
                                          int x, int y, int w, int h) {
        float a0 = start + sign * from;
        float a1 = start + sign * to;
        texVert(buf, pose, cx, cy, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry,
                su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
                su, sv, sw, sh, tw, th, x, y, w, h);
    }

    private static void radialColorTriangle(BufferBuilder buf, PoseStack pose,
                                            float cx, float cy, float rx, float ry,
                                            float start, float sign, float from, float to, int argb) {
        float a0 = start + sign * from;
        float a1 = start + sign * to;
        colorVert(buf, pose, cx, cy, argb);
        colorVert(buf, pose, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry, argb);
        colorVert(buf, pose, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry, argb);
    }

    private static void linearTexturedQuad(PoseStack pose, ResourceLocation texture,
                                           int x, int y, int w, int h,
                                           int su, int sv, int sw, int sh, int tw, int th,
                                           String mode, float amount, float alpha) {
        float t = clampAmount(amount);
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        float u0 = su, v0 = sv, u1 = su + sw, v1 = sv + sh;
        switch (mode) {
            case RTL -> {
                x0 = x + w * (1f - t);
                u0 = su + sw * (1f - t);
            }
            case TTB -> {
                y1 = y + h * t;
                v1 = sv + sh * t;
            }
            case BTT -> {
                y0 = y + h * (1f - t);
                v0 = sv + sh * (1f - t);
            }
            default -> {
                x1 = x + w * t;
                u1 = su + sw * t;
            }
        }
        setupTex(texture, alpha);
        BufferBuilder buf = beginTex();
        texUvVert(buf, pose, x0, y0, u0, v0, tw, th);
        texUvVert(buf, pose, x0, y1, u0, v1, tw, th);
        texUvVert(buf, pose, x1, y1, u1, v1, tw, th);
        texUvVert(buf, pose, x0, y0, u0, v0, tw, th);
        texUvVert(buf, pose, x1, y1, u1, v1, tw, th);
        texUvVert(buf, pose, x1, y0, u1, v0, tw, th);
        endTex(buf);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void texturedQuad(PoseStack pose, ResourceLocation texture,
                                     int x, int y, int w, int h,
                                     int su, int sv, int sw, int sh, int tw, int th, float alpha) {
        setupTex(texture, alpha);
        BufferBuilder buf = beginTex();
        float x1 = x + w;
        float y1 = y + h;
        texVert(buf, pose, x, y, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, x, y1, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, x1, y1, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, x, y, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, x1, y1, su, sv, sw, sh, tw, th, x, y, w, h);
        texVert(buf, pose, x1, y, su, sv, sw, sh, tw, th, x, y, w, h);
        endTex(buf);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void colorQuad(PoseStack pose, int x, int y, int w, int h, int argb) {
        BufferBuilder buf = beginColor();
        float x1 = x + w;
        float y1 = y + h;
        colorVert(buf, pose, x, y, argb);
        colorVert(buf, pose, x, y1, argb);
        colorVert(buf, pose, x1, y1, argb);
        colorVert(buf, pose, x, y, argb);
        colorVert(buf, pose, x1, y1, argb);
        colorVert(buf, pose, x1, y, argb);
        endColor(buf);
    }

    private static void texVert(BufferBuilder buf, PoseStack pose,
                                float px, float py,
                                int su, int sv, int sw, int sh, int tw, int th,
                                int dx, int dy, int dw, int dh) {
        float u = (su + (px - dx) / Math.max(1, dw) * sw) / tw;
        float v = (sv + (py - dy) / Math.max(1, dh) * sh) / th;
        buf.vertex(pose.last().pose(), px, py, 0f).uv(u, v).endVertex();
    }

    private static void texUvVert(BufferBuilder buf, PoseStack pose,
                                  float x, float y, float u, float v, int tw, int th) {
        buf.vertex(pose.last().pose(), x, y, 0f).uv(u / tw, v / th).endVertex();
    }

    private static void colorVert(BufferBuilder buf, PoseStack pose, float x, float y, int argb) {
        buf.vertex(pose.last().pose(), x, y, 0f)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    private static void setupTex(ResourceLocation texture, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
    }

    private static BufferBuilder beginTex() {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        return buf;
    }

    private static BufferBuilder beginColor() {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return buf;
    }

    private static void endTex(BufferBuilder buf) {
        //? if >=1.20.1 {
        BufferUploader.drawWithShader(buf.end());
        //?} else {
        /*buf.end();
        Tesselator.getInstance().end();
        *///?}
        RenderSystem.enableDepthTest();
    }

    private static void endColor(BufferBuilder buf) {
        //? if >=1.20.1 {
        BufferUploader.drawWithShader(buf.end());
        //?} else {
        /*buf.end();
        Tesselator.getInstance().end();
        *///?}
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
