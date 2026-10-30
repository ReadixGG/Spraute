package org.sprautestudio.st_addon_spraute.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector;
import org.sprautestudio.st_addon_spraute.engine.client.ChatHeads;
import org.sprautestudio.st_addon_spraute.net.Packets;

import java.util.ArrayList;
import java.util.List;

/** The speech window: the story's current line at the bottom of the screen, and the history of what was said. */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class SpeechClient {
    private SpeechClient() {
    }

    public record Line(String name, int color, String icon, String text, String hint, boolean think, boolean waits) {
    }

    /** How long a line stays when nothing replaces it. */
    private static final int MIN_TICKS = 60, TICKS_PER_CHAR = 2;
    private static final int ICON = 32, PAD = 8, MAX_HISTORY = 200;

    private static final List<Line> HISTORY = new ArrayList<>();
    private static Line current;
    private static int left;

    private static final KeyMapping LOG_KEY = new KeyMapping("key.st_addon_spraute.speech_log", InputConstants.KEY_H, "key.categories.st_addon_spraute");

    @Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent e) {
            e.register(LOG_KEY);
        }
    }

    public static List<Line> history() {
        return HISTORY;
    }

    public static void receive(Packets.Speech msg) {
        current = new Line(msg.name(), msg.color(), msg.icon(), msg.text(), msg.hint(), msg.think(), msg.waits());
        left = Math.max(MIN_TICKS, msg.text().length() * TICKS_PER_CHAR);
        HISTORY.add(current);
        if (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
    }

    public static void clear() {
        current = null;
        HISTORY.clear();
    }

    /** The icon's texture: speech icons are named as paths under the chat textures. */
    public static ResourceLocation iconTexture(String icon) {
        return new ResourceLocation(St_addon_spraute.MODID, "textures/gui/chat/" + (icon == null || icon.isEmpty() ? "generic_man" : icon) + ".png");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // a line the story waits on stays until F8
        if (current != null && !current.waits() && --left <= 0) current = null;
        while (LOG_KEY.consumeClick()) if (mc.screen == null) mc.setScreen(new ChatLogScreen());
    }

    @SubscribeEvent
    public static void onKey(net.minecraftforge.client.event.InputEvent.Key e) {
        if (e.getKey() == GLFW.GLFW_KEY_F8 && e.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null && current != null && current.waits()) current = null;
    }

    /** One message: the head, the name, the text; returns its height. */
    static int drawLine(GuiGraphics g, Font font, Line l, int x, int y, int w, boolean hint) {
        int textX = x + ICON + PAD;
        List<FormattedCharSequence> rows = font.split(l.think() ? Component.literal(l.text()).withStyle(net.minecraft.ChatFormatting.ITALIC) : Component.literal(l.text()), w - ICON - PAD);
        int height = Math.max(ICON, 12 + rows.size() * 10);
        ChatHeads.drawIcon(g, iconTexture(l.icon()), x, y, ICON, ICON);
        int color = l.color() >= 0 ? 0xFF000000 | l.color() : 0xFFFFD25A;
        g.drawString(font, l.name(), textX, y, color, true);
        if (hint && !l.hint().isEmpty()) g.drawString(font, l.hint(), x + w - font.width(l.hint()), y, 0xFFB0B8C8, true);
        int ty = y + 12;
        for (FormattedCharSequence r : rows) {
            g.drawString(font, r, textX, ty, 0xFFFFFFFF, true);
            ty += 10;
        }
        return height;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.options.hideGui || mc.screen instanceof ChatLogScreen || CameraDirector.hidesSpraute()) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = Math.min(g.guiWidth() - 20, 360), x = (g.guiWidth() - w) / 2;
        int rows = mc.font.split(Component.literal(current.text()), w - ICON - PAD * 3).size();
        int h = Math.max(ICON, 12 + rows * 10) + PAD * 2;
        int y = g.guiHeight() - h - 44;
        g.fill(x, y, x + w, y + h, 0xC0101218);
        drawLine(g, mc.font, current, x + PAD, y + PAD, w - PAD * 2, true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
    }
}
