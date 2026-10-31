package org.sprautestudio.spraute_engine.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;

/** Blackouts, flashes, closing eyes and a colour over the whole screen, on a timer of their own. */
public final class ScreenFx {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(ScreenFx::onTick);
        ClientHooks.RENDER_HUD_PART.add(ScreenFx::underChat);
        ClientHooks.RENDER_GUI.add(ScreenFx::onRender);
        ClientHooks.LOGGING_OUT.add(ScreenFx::onLogout);
    }

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

    /** The screen is entirely covered: what stands above the HUD (money) keeps out of it. */
    public static boolean fullyCovered() {
        return (tint >>> 24) == 0xFF
                || !kind.isEmpty() && !kind.equals("pulse_under") && amount(0) >= 1 && (argb >>> 24) == 0xFF;
    }

    /** 0..1 how covered the screen is now. */
    private static float amount(float partial) {
        float t = age + partial;
        if (t < in) return t / Math.max(1, in);
        if (t < in + hold) return 1;
        if (t < in + hold + out) return 1 - (t - in - hold) / Math.max(1, out);
        return 0;
    }

    public static void onTick(ClientHooks.ClientTick e) {
        if (e.phase != TickPhase.END || kind.isEmpty()) return;
        if (++age > in + hold + out) kind = "";
    }

    private static void fill(GuiGraphics g, float partial) {
        if (kind.isEmpty()) return;
        float a = amount(partial);
        if (a <= 0) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (kind.equals("eyes")) {
            // two lids coming together from above and below
            int lid = Math.round(h / 2f * a);
            g.fill(0, 0, w, lid, argb);
            g.fill(0, h - lid, w, h, argb);
        } else {
            int alpha = Math.round((argb >>> 24) * a);
            g.fill(0, 0, w, h, alpha << 24 | argb & 0xFFFFFF);
        }
    }

    /** The variant under the chat: speech stays readable over it. */
    public static void underChat(ClientHooks.RenderHudPart e) {
        if (!e.id().equals(ClientHooks.HudParts.CHAT_PANEL)) return;
        if (fullyCovered()) e.setCanceled(true);
        else if (kind.equals("pulse_under")) fill(e.getGuiGraphics(), e.getPartialTick());
    }

    public static void onRender(ClientHooks.RenderGui e) {
        if (Minecraft.getInstance().screen != null && kind.isEmpty() && tint == 0) return;
        GuiGraphics g = e.getGuiGraphics();
        if ((tint >>> 24) != 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), tint);
        if (!kind.equals("pulse_under")) fill(g, e.getPartialTick());
    }

    public static void onLogout(ClientHooks.LoggingOut e) {
        kind = "";
        tint = 0;
    }
}
