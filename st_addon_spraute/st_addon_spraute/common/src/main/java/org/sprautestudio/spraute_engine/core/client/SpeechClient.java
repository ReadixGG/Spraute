package org.sprautestudio.spraute_engine.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.cutscene.client.CameraDirector;
import org.sprautestudio.spraute_engine.engine.client.ChatHeads;
import org.sprautestudio.spraute_engine.engine.client.EngineClient;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The speech plate: the current line at the bottom of the screen. It comes at once, stays about ten seconds and
 * fades; a new line replaces it. The story's waiting on F8 is the server's business and does not touch the plate.
 */
public final class SpeechClient {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(SpeechClient::onTick);
        ClientHooks.RENDER_GUI.add(SpeechClient::onRender);
        ClientHooks.LOGGING_OUT.add(SpeechClient::onLogout);
    }

    private SpeechClient() {
    }

    public record Line(String name, int color, String icon, String text, String hint, boolean think) {
    }

    private static final int STAY_TICKS = 200, FADE_TICKS = 15;
    private static final int ICON = 20, PAD = 4, BOTTOM = 50, BOTTOM_CINEMA = 25, TOP = 24, BACK_ALPHA = 0x80;
    private static final float MAX_WIDTH = 0.6f;
    private static final int MAX_HISTORY = 500, DEFAULT_NAME_COLOR = 0xFFD25A;

    private static final Map<Integer, List<Line>> HISTORY = new HashMap<>();
    private static int newest = -1;
    private static Line current;
    private static int age;

    /** The key that moves speech on; the stories know it as F8. */
    public static final KeyMapping ADVANCE = new KeyMapping("key.spraute_engine.advance", InputConstants.KEY_F8, "key.categories.spraute_engine");
    private static final KeyMapping LOG_KEY = new KeyMapping("key.spraute_engine.chat_history", InputConstants.KEY_J, "key.categories.spraute_engine");

    public static final class Keys {
        public static void registerHooks() {
            ClientHooks.REGISTER_KEYS.add(Keys::register);
        }

        public static void register(ClientHooks.RegisterKeys e) {
            e.register(LOG_KEY);
            e.register(ADVANCE);
        }
    }

    /** The record of the newest running story; null — no story runs. */
    public static List<Line> history() {
        return newest < 0 ? null : HISTORY.computeIfAbsent(newest, k -> new ArrayList<>());
    }

    public static void newest(int scriptId) {
        newest = scriptId;
    }

    public static void receive(Packets.Speech msg) {
        Line l = new Line(msg.name(), msg.color(), msg.icon(), msg.text(), msg.hint(), msg.think());
        List<Line> h = HISTORY.computeIfAbsent(msg.scriptId(), k -> new ArrayList<>());
        h.add(l);
        if (h.size() > MAX_HISTORY) h.remove(0);
        if (msg.historyOnly()) return;
        current = l;
        age = 0;
    }

    public static void clear(int scriptId) {
        current = null;
        HISTORY.remove(scriptId);
    }

    /** The icon's texture: speech icons are named as paths under the chat textures. */
    public static ResourceLocation iconTexture(String icon) {
        return new ResourceLocation(SprauteEngine.MODID, "textures/gui/chat/" + (icon == null || icon.isEmpty() ? "generic_man" : icon) + ".png");
    }

    public static void onTick(ClientHooks.ClientTick e) {
        if (e.phase != TickPhase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // the timer goes on behind an open screen
        if (current != null && ++age > STAY_TICKS + FADE_TICKS) current = null;
        while (LOG_KEY.consumeClick()) if (mc.screen == null && history() != null) mc.setScreen(new ChatLogScreen());
    }

    /** [Name]: text, the name coloured, the rest white. */
    static Component message(Line l) {
        return message(l, true);
    }

    static Component message(Line l, boolean hint) {
        int color = l.color() >= 0 ? l.color() : EngineClient.config().chatLog.nameColor >= 0 ? EngineClient.config().chatLog.nameColor : DEFAULT_NAME_COLOR;
        MutableComponent c = Component.empty();
        if (!l.name().isEmpty()) {
            Style named = Style.EMPTY.withColor(TextColor.fromRgb(color));
            c.append(Component.literal("[" + l.name() + "]").withStyle(named));
            if (hint && !l.hint().isEmpty()) c.append(Component.literal(" " + l.hint().replace("F8", ADVANCE.getTranslatedKeyMessage().getString())).withStyle(named));
            c.append(Component.literal(": "));
        }
        c.append(l.think() ? Component.literal(l.text()).withStyle(net.minecraft.ChatFormatting.ITALIC) : Component.literal(l.text()));
        return c;
    }

    /** Draws a plate fitted to its text, no wider than {@code maxW}; returns its height. */
    static int plate(GuiGraphics g, Font font, Line l, int centerX, int y, int maxW, int backAlpha, float alpha) {
        return plate(g, font, l, centerX, y, maxW, backAlpha, alpha, true);
    }

    static int plate(GuiGraphics g, Font font, Line l, int centerX, int y, int maxW, int backAlpha, float alpha, boolean hint) {
        List<FormattedCharSequence> rows = font.split(message(l, hint), Math.max(40, maxW - ICON - PAD * 3));
        int tw = 0;
        for (FormattedCharSequence r : rows) tw = Math.max(tw, font.width(r));
        int w = ICON + tw + PAD * 3, textH = rows.size() * font.lineHeight, h = Math.max(ICON, textH) + PAD * 2;
        int x = centerX - w / 2, a = Math.max(4, Math.round(alpha * 255)) << 24;
        g.fill(x, y, x + w, y + h, Math.round(backAlpha * alpha) << 24);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, alpha);
        ChatHeads.drawIcon(g, iconTexture(l.icon()), x + PAD, y + (h - ICON) / 2, ICON, ICON);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        int ty = y + (h - textH) / 2;
        for (FormattedCharSequence r : rows) {
            g.drawString(font, r, x + ICON + PAD * 2, ty, a | 0xFFFFFF, true);
            ty += font.lineHeight;
        }
        return h;
    }

    static int plateHeight(Font font, Line l, int maxW) {
        return Math.max(ICON, font.split(message(l), Math.max(40, maxW - ICON - PAD * 3)).size() * font.lineHeight) + PAD * 2;
    }

    public static void onRender(ClientHooks.RenderGui e) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.options.hideGui || mc.screen != null) return;
        GuiGraphics g = e.getGuiGraphics();
        float t = age + e.getPartialTick() - STAY_TICKS;
        float alpha = t <= 0 ? 1 : Math.max(0, 1 - t / FADE_TICKS);
        if (alpha <= 0) return;
        int maxW = Math.round(g.guiWidth() * MAX_WIDTH);
        int h = plateHeight(mc.font, current, maxW);
        var cfg = EngineClient.config().chatLog;
        int y = switch (cfg.speechPlace == null ? "bottom" : cfg.speechPlace) {
            case "center" -> (g.guiHeight() - h) / 2;
            case "top" -> TOP;
            default -> g.guiHeight() - (CameraDirector.active() && CameraDirector.hidesMinecraft() ? BOTTOM_CINEMA : BOTTOM) - h;
        };
        g.pose().pushPose();
        try {
            // Both the plate and its 3D portrait stay in front of fades and cinematic bars.
            g.pose().translate(0, 0, 400);
            plate(g, mc.font, current, g.guiWidth() / 2, y - cfg.speechShift, maxW, BACK_ALPHA, alpha);
        } finally {
            g.pose().popPose();
        }
    }

    public static void onLogout(ClientHooks.LoggingOut e) {
        PlayerSkins.clear();
        current = null;
        HISTORY.clear();
        newest = -1;
    }
}
