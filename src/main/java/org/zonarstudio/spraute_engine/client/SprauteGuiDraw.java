package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

public final class SprauteGuiDraw {
    private SprauteGuiDraw() {}

    //? if >=1.20.1 {
    public static void fill(net.minecraft.client.gui.GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }
    public static void blit(net.minecraft.client.gui.GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h, int tw, int th) {
        g.blit(tex, x, y, u, v, w, h, tw, th);
    }
    public static void blit(net.minecraft.client.gui.GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int tw, int th) {
        g.blit(tex, x, y, (int) u, (int) v, w, h, tw, th);
    }
    public static void blitRegion(net.minecraft.client.gui.GuiGraphics g, ResourceLocation tex,
                                  int x, int y, int dw, int dh, int su, int sv, int sw, int sh, int tw, int th) {
        g.blit(tex, x, y, dw, dh, su, sv, sw, sh, tw, th);
    }
    public static void drawCenteredString(net.minecraft.client.gui.GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }
    public static void enableScissor(net.minecraft.client.gui.GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }
    public static void disableScissor(net.minecraft.client.gui.GuiGraphics g) {
        g.disableScissor();
    }
    //?} else {
    /*public static void fill(PoseStack pose, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }
    public static void blit(PoseStack pose, int x, int y, int u, int v, int w, int h, int tw, int th) {
        net.minecraft.client.gui.GuiComponent.blit(pose, x, y, u, v, w, h, tw, th);
    }
    public static void blit(PoseStack pose, int x, int y, float u, float v, int w, int h, int tw, int th) {
        net.minecraft.client.gui.GuiComponent.blit(pose, x, y, u, v, w, h, tw, th);
    }
    public static void blitRegion(PoseStack pose, int x, int y, int dw, int dh, int su, int sv, int sw, int sh, int tw, int th) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        net.minecraft.client.gui.GuiComponent.blit(pose, x, y, dw, dh, (float) su, (float) sv, sw, sh, tw, th);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
    public static void drawCenteredString(PoseStack pose, Font font, String text, int x, int y, int color) {
        net.minecraft.client.gui.GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }
    public static void enableScissor(int x0, int y0, int x1, int y1) {
        net.minecraft.client.gui.GuiComponent.enableScissor(x0, y0, x1, y1);
    }
    public static void disableScissor() {
        net.minecraft.client.gui.GuiComponent.disableScissor();
    }
    *///?}
}
