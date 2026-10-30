package org.sprautestudio.st_addon_spraute.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.ui.Canvas;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Story interfaces shown as overlays on top of the game ({@code overlay show "name"}): drawn over
 * the HUD, they never take the mouse or the keyboard. Changes from the story ({@code gui text},
 * {@code gui move}...) reach them like an open interface.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class HudOverlays {
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

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
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
        GuiView engineHud = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.hudLayout();
        if (engineHud != null && mc.player != null && !mc.player.isSpectator()
                && !org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.hidesMinecraft()) {
            try {
                engineHud.bounds(0, 0, sw, sh);
                engineHud.layout();
                engineHud.render(c, -10000, -10000, dt);
                c.flush();
            } catch (RuntimeException ignored) {
            }
        }
        if (SHOWN.isEmpty() || org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.hidesCustom()) return;
        for (Shown s : SHOWN.values()) {
            try {
                s.view.bounds(0, 0, sw, sh);
                s.view.layout();
                s.view.render(c, -10000, -10000, dt);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) for (Shown s : SHOWN.values()) s.view.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SHOWN.clear();
    }
}
