package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;

/** Drawing helpers of the engine screens' live parts (journal, shop): textures with fallbacks, scaled text, items. */
public final class LiveDraw {
    private LiveDraw() {
    }

    /** A picture: the part's own (project picture or "mod:path"), else the settings' skin, else Spraute's. */
    public static ResourceLocation tex(String project, String own, EngineConfig.Skin skin, ResourceLocation def) {
        if (own != null && !own.isBlank()) {
            String v = own.trim();
            if (v.contains(":")) {
                ResourceLocation rl = ResourceLocation.tryParse(v);
                if (rl != null) return rl;
            } else return org.sprautestudio.st_addon_spraute.runtime.Images.texture(project, v);
        }
        if (skin != null && skin.isSet()) return Skins.texture(project, skin);
        return def;
    }

    /** The part's own picture of a property, else the skin, else the default. */
    public static ResourceLocation tex(String project, GuiDoc.Element e, String prop, EngineConfig.Skin skin, ResourceLocation def) {
        return tex(project, e.get(prop), skin, def);
    }

    /** A whole picture stretched over the rectangle. */
    public static void image(GuiGraphics g, ResourceLocation rl, int x, int y, int w, int h, float op) {
        if (w <= 0 || h <= 0) return;
        int[] s = Skins.size(rl);
        int tw = s != null && s[0] > 0 ? s[0] : w, th = s != null && s[1] > 0 ? s[1] : h;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, op);
        g.blit(rl, x, y, w, h, 0, 0, tw, th, tw, th);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /**
     * A part (u, v, uw × vh) of a sheet laid out like Spraute's tw × th one, drawn into the rectangle; a picture
     * of another size is read proportionally (a sheet drawn 2× bigger works too).
     */
    public static void sheet(GuiGraphics g, ResourceLocation rl, int x, int y, int w, int h, int u, int v, int uw, int vh, int tw, int th, float op) {
        if (w <= 0 || h <= 0) return;
        int[] s = Skins.size(rl);
        int rw = s != null && s[0] > 0 ? s[0] : tw, rh = s != null && s[1] > 0 ? s[1] : th;
        float kx = rw / (float) tw, ky = rh / (float) th;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, op);
        g.blit(rl, x, y, w, h, u * kx, v * ky, Math.round(uw * kx), Math.round(vh * ky), rw, rh);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public static int a(int color, float op) {
        int al = Math.round((color >>> 24) * Math.max(0, Math.min(1, op)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static void text(GuiGraphics g, Font font, String s, float x, float y, float scale, int color, boolean shadow) {
        if (s == null || s.isEmpty() || (color >>> 24) < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Font font, Component s, float x, float y, float scale, int color, boolean shadow) {
        if ((color >>> 24) < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence s, float x, float y, float scale, int color, boolean shadow) {
        if ((color >>> 24) < 5) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static void item(GuiGraphics g, ItemStack s, float x, float y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    /** Cuts drawing to a rectangle in the current (posed) coordinates. */
    public static void clip(GuiGraphics g, int x, int y, int w, int h) {
        int[] c = ChatHeads.screenRect(g, x, y, w, h);
        g.enableScissor(c[0], c[1], c[2], c[3]);
    }

    public static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
