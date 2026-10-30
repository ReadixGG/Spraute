package org.sprautestudio.st_addon_spraute.editor.engine;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.st_addon_spraute.ui.Canvas;

/**
 * The game as it was the moment the editor opened: the studio puts its windows over it, so a layout is seen over the
 * real world and not over an empty board. Without a world (main menu) — a calm dusk gradient.
 */
public final class GameShot {
    private static final ResourceLocation ID = new ResourceLocation("st_addon_spraute", "studio/game_shot");
    private static DynamicTexture texture;
    private static int tw, th;

    private GameShot() {
    }

    /** Takes the game's last frame, if the game (not a screen) is what is shown now. */
    public static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            if (texture != null) texture.close();
            texture = new DynamicTexture(img);
            tw = img.getWidth();
            th = img.getHeight();
            mc.getTextureManager().register(ID, texture);
        } catch (RuntimeException ignored) {
            texture = null;
        }
    }

    /** The shot filling the rectangle (cropped to its proportions), else the gradient. */
    public static void draw(Canvas c, int x, int y, int w, int h) {
        if (texture == null || tw <= 0 || th <= 0) {
            c.g.fillGradient(x, y, x + w, y + h, 0xFF2A3550, 0xFF0E1018);
            c.g.fillGradient(x, y + h * 2 / 3, x + w, y + h, 0x00000000, 0xFF1C2A18);
            return;
        }
        // cover: crop the shot so it fills the area without stretching
        float ar = w / (float) h, sar = tw / (float) th;
        int u = 0, v = 0, uw = tw, vh = th;
        if (sar > ar) {
            uw = Math.round(th * ar);
            u = (tw - uw) / 2;
        } else {
            vh = Math.round(tw / ar);
            v = (th - vh) / 2;
        }
        c.flush();
        c.g.blit(ID, x, y, w, h, u, v, uw, vh, tw, th);
        c.flush();
    }
}
