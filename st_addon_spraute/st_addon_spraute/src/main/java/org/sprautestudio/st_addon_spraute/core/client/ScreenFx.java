package org.sprautestudio.st_addon_spraute.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

/** Blackouts, flashes, closing eyes and a colour over the whole screen. */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class ScreenFx {
    private ScreenFx() {
    }

    private static String kind = "";
    private static int in, hold, out, argb, age;
    private static int tint;

    public static void play(String k, int fadeIn, int stay, int fadeOut, int color) {
        if (k.equals("color")) {
            tint = color;
            return;
        }
        kind = k;
        in = fadeIn;
        hold = stay;
        out = fadeOut;
        argb = color;
        age = 0;
    }

    /** 0..1 how covered the screen is now. */
    private static float amount(float partial) {
        float t = age + partial;
        if (t < in) return t / Math.max(1, in);
        if (t < in + hold) return 1;
        if (t < in + hold + out) return 1 - (t - in - hold) / Math.max(1, out);
        return 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || kind.isEmpty()) return;
        if (++age > in + hold + out) kind = "";
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if ((tint >>> 24) != 0) g.fill(0, 0, w, h, tint);
        if (kind.isEmpty()) return;
        float a = amount(e.getPartialTick());
        if (a <= 0) return;
        if (kind.equals("eyes")) {
            // two lids closing from above and below
            int lid = Math.round(h / 2f * a);
            g.fill(0, 0, w, lid, argb);
            g.fill(0, h - lid, w, h, argb);
        } else {
            int alpha = Math.round((argb >>> 24) * a);
            g.fill(0, 0, w, h, alpha << 24 | argb & 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        kind = "";
        tint = 0;
    }
}
