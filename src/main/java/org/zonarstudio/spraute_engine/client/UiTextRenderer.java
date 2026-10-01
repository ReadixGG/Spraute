package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Unified effect renderer for text, button labels and input text. */
public final class UiTextRenderer {
    private record Glyph(Style style, int codePoint, int width) {}

    private UiTextRenderer() {}

    //? if >=1.20.1 {
    public static void drawLine(net.minecraft.client.gui.GuiGraphics graphics, Font font,
                                FormattedCharSequence line, float x, float y, int color,
                                UiTextStyle textStyle, float alpha) {
        UiTextStyle style = textStyle == null ? UiTextStyle.NONE : textStyle;
        boolean pushed = style.pulse();
        if (pushed) {
            float pulse = pulseScale(style);
            float cx = x + font.width(line) * 0.5f;
            float cy = y + font.lineHeight * 0.5f;
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().scale(pulse, pulse, 1f);
            x = -font.width(line) * 0.5f;
            y = -font.lineHeight * 0.5f;
        }
        if (style.hasBackdrop()) {
            int w = font.width(line);
            graphics.fill((int) x - 2, (int) y - 1, (int) x + w + 2, (int) y + font.lineHeight + 1,
                    withAlpha(0xB0181A20, alpha));
        }
        boolean perGlyph = style.animatedOrGradient() || style.underlineColor() != 0
                || UiTextMarkup.hasSpanEffects(line);
        if (!perGlyph) {
            drawWhole(graphics, font, line, x, y, color, style, alpha);
            if (pushed) graphics.pose().popPose();
            return;
        }
        drawGlyphs(graphics, font, line, x, y, color, style, alpha);
        if (pushed) graphics.pose().popPose();
    }

    private static void drawWhole(net.minecraft.client.gui.GuiGraphics graphics, Font font,
                                  FormattedCharSequence line, float x, float y, int color,
                                  UiTextStyle style, float alpha) {
        drawEffectPasses((ox, oy, c, shadow) -> {
            graphics.pose().pushPose();
            graphics.pose().translate(x + ox, y + oy, 0);
            graphics.drawString(font, line, 0, 0, withAlpha(c, alpha), shadow);
            graphics.pose().popPose();
        }, color, style);
    }

    private static void drawGlyphs(net.minecraft.client.gui.GuiGraphics graphics, Font font,
                                   FormattedCharSequence line, float x, float y, int color,
                                   UiTextStyle style, float alpha) {
        List<Glyph> glyphs = glyphs(font, line);
        float cursor = x;
        double time = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            UiTextStyle merged = UiTextMarkup.fromGlyph(style, glyph.style);
            FormattedCharSequence sequence = Component.literal(new String(Character.toChars(glyph.codePoint)))
                    .withStyle(glyph.style).getVisualOrderText();
            float[] offset = animatedOffset(merged, time, i);
            int fallback = UiTextMarkup.glyphColor(glyph.style, color);
            int colorIndex = i, colorCount = glyphs.size();
            if (merged.gradient() != null || (merged.rainbow() && !style.rainbow())) {
                int start = runStart(glyphs, i);
                colorIndex = i - start;
                colorCount = runLengthFrom(glyphs, start);
            }
            int glyphColor = animatedColor(merged, fallback, time, colorIndex, colorCount);
            float gx = cursor + offset[0], gy = y + offset[1];
            boolean localPulse = merged.pulse() && !style.pulse();
            float pulse = localPulse ? pulseScale(merged) : 1f;
            drawEffectPasses((ox, oy, c, shadow) -> {
                graphics.pose().pushPose();
                if (localPulse) {
                    graphics.pose().translate(gx + glyph.width * 0.5f, gy + font.lineHeight * 0.5f, 0);
                    graphics.pose().scale(pulse, pulse, 1f);
                    graphics.pose().translate(-glyph.width * 0.5f + ox, -font.lineHeight * 0.5f + oy, 0);
                } else {
                    graphics.pose().translate(gx + ox, gy + oy, 0);
                }
                graphics.drawString(font, sequence, 0, 0, withAlpha(c, alpha), shadow);
                graphics.pose().popPose();
            }, glyphColor, merged);
            if (merged.underline() && merged.underlineColor() != 0) {
                int thickness = Math.max(1, Math.round(merged.underlineThickness()));
                int uy = Math.round(gy + font.lineHeight - 1f + merged.underlineOffset());
                graphics.fill(Math.round(gx), uy, Math.round(gx) + glyph.width, uy + thickness,
                        withAlpha(merged.underlineColor(), alpha));
            }
            cursor += glyph.width;
        }
    }
    //?} else {
    /*public static void drawLine(PoseStack pose, Font font,
                                FormattedCharSequence line, float x, float y, int color,
                                UiTextStyle textStyle, float alpha) {
        UiTextStyle style = textStyle == null ? UiTextStyle.NONE : textStyle;
        boolean pushed = style.pulse();
        if (pushed) {
            float pulse = pulseScale(style);
            float cx = x + font.width(line) * 0.5f;
            float cy = y + font.lineHeight * 0.5f;
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.scale(pulse, pulse, 1f);
            x = -font.width(line) * 0.5f;
            y = -font.lineHeight * 0.5f;
        }
        if (style.hasBackdrop()) {
            int w = font.width(line);
            net.minecraft.client.gui.GuiComponent.fill(pose, (int) x - 2, (int) y - 1,
                    (int) x + w + 2, (int) y + font.lineHeight + 1, withAlpha(0xB0181A20, alpha));
        }
        boolean perGlyph = style.animatedOrGradient() || style.underlineColor() != 0
                || UiTextMarkup.hasSpanEffects(line);
        if (!perGlyph) {
            drawWhole(pose, font, line, x, y, color, style, alpha);
            if (pushed) pose.popPose();
            return;
        }
        drawGlyphs(pose, font, line, x, y, color, style, alpha);
        if (pushed) pose.popPose();
    }

    private static void drawWhole(PoseStack pose, Font font, FormattedCharSequence line,
                                  float x, float y, int color, UiTextStyle style, float alpha) {
        drawEffectPasses((ox, oy, c, shadow) -> {
            pose.pushPose();
            pose.translate(x + ox, y + oy, 0);
            if (shadow) font.drawShadow(pose, line, 0, 0, withAlpha(c, alpha));
            else font.draw(pose, line, 0, 0, withAlpha(c, alpha));
            pose.popPose();
        }, color, style);
    }

    private static void drawGlyphs(PoseStack pose, Font font, FormattedCharSequence line,
                                   float x, float y, int color, UiTextStyle style, float alpha) {
        List<Glyph> glyphs = glyphs(font, line);
        float cursor = x;
        double time = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            UiTextStyle merged = UiTextMarkup.fromGlyph(style, glyph.style);
            FormattedCharSequence sequence = Component.literal(new String(Character.toChars(glyph.codePoint)))
                    .withStyle(glyph.style).getVisualOrderText();
            float[] offset = animatedOffset(merged, time, i);
            int fallback = UiTextMarkup.glyphColor(glyph.style, color);
            int colorIndex = i, colorCount = glyphs.size();
            if (merged.gradient() != null || (merged.rainbow() && !style.rainbow())) {
                int start = runStart(glyphs, i);
                colorIndex = i - start;
                colorCount = runLengthFrom(glyphs, start);
            }
            int glyphColor = animatedColor(merged, fallback, time, colorIndex, colorCount);
            float gx = cursor + offset[0], gy = y + offset[1];
            boolean localPulse = merged.pulse() && !style.pulse();
            float pulse = localPulse ? pulseScale(merged) : 1f;
            drawEffectPasses((ox, oy, c, shadow) -> {
                pose.pushPose();
                if (localPulse) {
                    pose.translate(gx + glyph.width * 0.5f, gy + font.lineHeight * 0.5f, 0);
                    pose.scale(pulse, pulse, 1f);
                    pose.translate(-glyph.width * 0.5f + ox, -font.lineHeight * 0.5f + oy, 0);
                } else {
                    pose.translate(gx + ox, gy + oy, 0);
                }
                if (shadow) font.drawShadow(pose, sequence, 0, 0, withAlpha(c, alpha));
                else font.draw(pose, sequence, 0, 0, withAlpha(c, alpha));
                pose.popPose();
            }, glyphColor, merged);
            if (merged.underline() && merged.underlineColor() != 0) {
                int thickness = Math.max(1, Math.round(merged.underlineThickness()));
                int uy = Math.round(gy + font.lineHeight - 1f + merged.underlineOffset());
                net.minecraft.client.gui.GuiComponent.fill(pose, Math.round(gx), uy,
                        Math.round(gx) + glyph.width, uy + thickness,
                        withAlpha(merged.underlineColor(), alpha));
            }
            cursor += glyph.width;
        }
    }
    *///?}

    @FunctionalInterface
    private interface PassDrawer {
        void draw(float offsetX, float offsetY, int color, boolean shadow);
    }

    private static void drawEffectPasses(PassDrawer drawer, int color, UiTextStyle style) {
        if (style.glow()) {
            if (style.pixelGlow()) drawPixelHalo(drawer, style.glowColor(), Math.max(1f, style.glowSize()));
            else drawSoftHalo(drawer, style.glowColor(), Math.max(1f, style.glowSize()), true);
        }
        if (style.outline()) {
            float width = Math.max(0.5f, style.outlineWidth());
            if (style.pixelOutline()) drawPixelHalo(drawer, style.outlineColor(), width);
            else drawSoftHalo(drawer, style.outlineColor(), width, false);
        }
        boolean customShadow = style.shadowColor() != 0
                || style.shadowOffsetX() != 1f || style.shadowOffsetY() != 1f
                || style.shadowSize() != 2f;
        if (style.shadow() && customShadow) {
            int shadowColor = style.shadowColor() == 0 ? 0x88000000 : style.shadowColor();
            if (style.pixelShadow()) {
                drawer.draw(style.shadowOffsetX(), style.shadowOffsetY(), shadowColor, false);
            } else {
                drawSoftShadow(drawer, Math.max(0.5f, style.shadowSize()), shadowColor,
                        style.shadowOffsetX(), style.shadowOffsetY());
            }
            drawer.draw(0, 0, color, false);
            return;
        }
        if (style.shadow() && !style.pixelShadow()) {
            drawSoftShadow(drawer, Math.max(1.5f, style.amount() * 2f),
                    0x58000000, 0.7f, 1.4f);
            drawer.draw(0, 0, color, false);
            return;
        }
        drawer.draw(0, 0, color, style.shadow());
    }

    /** Integer-offset copies — MC-style pixel outline / chunky glow. */
    private static void drawPixelHalo(PassDrawer drawer, int color, float size) {
        int radius = Math.max(1, Math.min(8, Math.round(size)));
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x == 0 && y == 0) continue;
                if (x * x + y * y > radius * radius) continue;
                drawer.draw(x, y, color, false);
            }
        }
    }

    /**
     * Radial samples with alpha falloff. Glow uses more rings so it reads as light,
     * outline stays tighter around the glyph.
     */
    private static void drawSoftHalo(PassDrawer drawer, int color, float radius, boolean glow) {
        int rings = glow ? Math.max(3, Math.min(7, Math.round(radius + 1f))) : Math.max(2, Math.min(4, Math.round(radius + 1f)));
        int dirs = glow ? 16 : 12;
        int baseA = (color >>> 24) & 255;
        if (baseA <= 0) baseA = glow ? 0x66 : 0xFF;
        for (int ring = rings; ring >= 1; ring--) {
            float t = (float) ring / rings;
            float r = radius * t;
            float falloff = glow ? (1f - t) * (1f - t) : (1f - t * 0.35f);
            int a = Math.round(baseA * Math.max(0.12f, falloff) * (glow ? 0.9f : 1f));
            if (a <= 0) continue;
            int c = (color & 0xFFFFFF) | (a << 24);
            double spin = ring * 0.11;
            for (int d = 0; d < dirs; d++) {
                double ang = (Math.PI * 2.0 * d) / dirs + spin;
                drawer.draw((float) Math.cos(ang) * r, (float) Math.sin(ang) * r, c, false);
            }
        }
    }

    private static void drawSoftShadow(PassDrawer drawer, float size, int color, float ox, float oy) {
        int rings = Math.max(3, Math.min(6, Math.round(size + 2f)));
        int baseAlpha = (color >>> 24) & 255;
        int rgb = color & 0xFFFFFF;
        for (int ring = rings; ring >= 1; ring--) {
            float t = (float) ring / rings;
            float grow = size * t;
            float f = 1f - t;
            int a = Math.round(baseAlpha * Math.max(0.12f, f * f));
            if (a <= 0) continue;
            int c = rgb | (a << 24);
            int dirs = 10;
            for (int d = 0; d < dirs; d++) {
                double angle = Math.PI * 2.0 * d / dirs;
                drawer.draw(ox + (float) Math.cos(angle) * grow,
                        oy + (float) Math.sin(angle) * grow, c, false);
            }
        }
    }

    private static List<Glyph> glyphs(Font font, FormattedCharSequence line) {
        List<Glyph> result = new ArrayList<>();
        line.accept((index, style, codePoint) -> {
            FormattedCharSequence seq = Component.literal(new String(Character.toChars(codePoint)))
                    .withStyle(style).getVisualOrderText();
            result.add(new Glyph(style, codePoint, font.width(seq)));
            return true;
        });
        return result;
    }

    private static int runStart(List<Glyph> glyphs, int index) {
        String key = insertionKey(glyphs.get(index));
        int i = index;
        while (i > 0 && insertionKey(glyphs.get(i - 1)).equals(key)) i--;
        return i;
    }

    private static int runLengthFrom(List<Glyph> glyphs, int start) {
        String key = insertionKey(glyphs.get(start));
        int i = start;
        while (i < glyphs.size() && insertionKey(glyphs.get(i)).equals(key)) i++;
        return Math.max(1, i - start);
    }

    private static String insertionKey(Glyph glyph) {
        String ins = glyph.style == null ? null : glyph.style.getInsertion();
        return ins == null ? "" : ins;
    }

    private static float[] animatedOffset(UiTextStyle style, double time, int index) {
        float amount = Math.max(0.1f, style.amount());
        time *= Math.max(0.01f, style.speed());
        float x = 0, y = 0;
        if (style.wave()) y += (float) Math.sin(time * 4.0 + index * 0.65) * 2f * amount;
        if (style.wiggle()) {
            x += (float) Math.sin(time * 5.0 + index * 1.37) * amount;
            y += (float) Math.cos(time * 4.3 + index * 1.11) * amount;
        }
        if (style.shake()) {
            long tick = (long) (time * 20.0);
            long seed = tick * 1103515245L + index * 12345L;
            x += (((seed >>> 8) & 7) - 3) * 0.35f * amount;
            y += (((seed >>> 16) & 7) - 3) * 0.35f * amount;
        }
        return new float[]{x, y};
    }

    private static float pulseScale(UiTextStyle style) {
        double time = System.currentTimeMillis() / 1000.0 * Math.max(0.01f, style.speed());
        return 1f + (float) Math.sin(time * 3.0) * 0.08f * Math.max(0.1f, style.amount());
    }

    private static int animatedColor(UiTextStyle style, int fallback, double time, int index, int count) {
        if (style.rainbow()) {
            float hue = (float) ((time * Math.max(0.01f, style.speed()) * 0.12
                    + index / (double) Math.max(1, count)) % 1.0);
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF);
        }
        int[] gradient = style.gradient();
        if (gradient == null || gradient.length < 2) return fallback;
        float t = count <= 1 ? 0f : index / (float) (count - 1);
        float scaled = t * (gradient.length - 1);
        int segment = Math.min(gradient.length - 2, (int) scaled);
        return lerpColor(gradient[segment], gradient[segment + 1], scaled - segment);
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 255) * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0xFFFFFF) | (a << 24);
    }
}
