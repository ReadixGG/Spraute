package org.sprautestudio.spraute_engine.gui.client;

import net.minecraft.client.Minecraft;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.gui.GuiDoc;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Story interfaces shown as overlays on top of the game ({@code overlay show "name"}): drawn over
 * the HUD, they never take the mouse or the keyboard. Changes from the story ({@code gui text},
 * {@code gui move}...) reach them like an open interface.
 */
public final class HudOverlays {
    public static void registerHooks() {
        ClientHooks.RENDER_GUI.add(HudOverlays::onRender);
        ClientHooks.CLIENT_TICK.add(HudOverlays::onTick);
        ClientHooks.LOGGING_OUT.add(HudOverlays::onLogout);
    }

    private HudOverlays() {}

    private record Shown(int scriptId, String name, GuiView view) {}

    private static final Map<String, Shown> SHOWN = new LinkedHashMap<>();
    private static long lastFrame = System.nanoTime();

    private static String key(int scriptId, String name) {
        return scriptId + "/" + name;
    }

    public static void show(Packets.OverlayShow msg) {
        GuiView v = new GuiView(GuiDoc.fromJson(msg.json()), msg.project());
        v.drawBackground = false;
        v.restartAppear();
        SHOWN.put(key(msg.scriptId(), msg.name()), new Shown(msg.scriptId(), msg.name(), v));
    }

    public static void hide(Packets.OverlayHide msg) {
        if (msg.name().isEmpty()) SHOWN.values().removeIf(s -> s.scriptId == msg.scriptId());
        else SHOWN.remove(key(msg.scriptId(), msg.name()));
    }

    public static void update(Packets.GuiUpdate msg) {
        for (Shown s : SHOWN.values()) if (s.scriptId == msg.scriptId()) s.view.update(msg.element(), msg.prop(), msg.value());
    }

    public static void onRender(ClientHooks.RenderGui event) {
        draw(event.getGuiGraphics(), event.getWindow());
    }

    private static void draw(net.minecraft.client.gui.GuiGraphics graphics, com.mojang.blaze3d.platform.Window window) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int sw = window.getGuiScaledWidth(), sh = window.getGuiScaledHeight();
        Canvas c = new Canvas(graphics, mc.font, 1f);
        // the player's HUD from the interface editor (it goes with the game's HUD: "camera hide minecraft" hides it)
        GuiView engineHud = org.sprautestudio.spraute_engine.engine.client.EngineClient.hudLayout();
        if (engineHud != null && mc.player != null && !mc.player.isSpectator()
                && !org.sprautestudio.spraute_engine.cutscene.client.CameraDirector.hidesMinecraft()) {
            try {
                engineHud.bounds(0, 0, sw, sh);
                engineHud.layout();
                engineHud.render(c, -10000, -10000, dt);
                c.flush();
            } catch (RuntimeException ignored) {
            }
        }
        if (SHOWN.isEmpty() || org.sprautestudio.spraute_engine.cutscene.client.CameraDirector.hidesCustom()) return;
        for (Shown s : SHOWN.values()) {
            try {
                s.view.bounds(0, 0, sw, sh);
                s.view.layout();
                s.view.render(c, -10000, -10000, dt);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void onTick(ClientHooks.ClientTick event) {
        if (event.phase == TickPhase.END) for (Shown s : SHOWN.values()) s.view.tick();
    }

    public static void onLogout(ClientHooks.LoggingOut event) {
        SHOWN.clear();
    }
}
