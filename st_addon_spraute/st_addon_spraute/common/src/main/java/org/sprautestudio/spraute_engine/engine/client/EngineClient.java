package org.sprautestudio.spraute_engine.engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.engine.EngineConfig;
import org.sprautestudio.spraute_engine.platform.ClientHooks;

import java.util.HashMap;
import java.util.Map;

/** Hides, moves or redraws HUD parts according to the world's engine settings. */
public final class EngineClient {
    public static void registerHooks() {
        ClientHooks.LOGGING_OUT.add(EngineClient::onLogout);
        ClientHooks.RENDER_HUD_PART.add(EngineClient::onOverlay);
        ClientHooks.CHAT_FROM_PLAYER.add(EngineClient::onChat);
    }

    private EngineClient() {}

    private static final ResourceLocation HEALTH_OVERLAY = new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "health_overlay");
    private static final ResourceLocation COINS_OVERLAY = new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "currency_overlay");

    private static final Map<ResourceLocation, String> VANILLA = new HashMap<>();

    static {
        VANILLA.put(ClientHooks.HudParts.PLAYER_HEALTH, "health");
        VANILLA.put(ClientHooks.HudParts.ARMOR_LEVEL, "armor");
        VANILLA.put(ClientHooks.HudParts.FOOD_LEVEL, "food");
        VANILLA.put(ClientHooks.HudParts.AIR_LEVEL, "air");
        VANILLA.put(ClientHooks.HudParts.EXPERIENCE_BAR, "exp");
        VANILLA.put(ClientHooks.HudParts.HOTBAR, "hotbar");
    }

    private static EngineConfig config = new EngineConfig();

    public static EngineConfig config() {
        return config;
    }

    public static void receive(String json) {
        config = EngineConfig.fromJson(json);
    }

    public static void onLogout(ClientHooks.LoggingOut event) {
        config = EngineConfig.fromJson("{}");
    }

    private static final java.util.Map<String, String> WINDOW_JSON = new java.util.HashMap<>();
    private static final java.util.Map<String, org.sprautestudio.spraute_engine.gui.client.GuiView> WINDOW_VIEW = new java.util.HashMap<>();

    /**
     * An engine window laid out in the interface editor (hud, choice, toast, zone, chatlog), ready to draw with the
     * player's state, or null — the engine's own look. The same view while the layout does not change.
     */
    public static org.sprautestudio.spraute_engine.gui.client.GuiView window(String role) {
        String json = config.layout(role);
        if (json == null) {
            WINDOW_JSON.remove(role);
            WINDOW_VIEW.remove(role);
            return null;
        }
        if (!json.equals(WINDOW_JSON.get(role))) {
            WINDOW_JSON.put(role, json);
            var v = new org.sprautestudio.spraute_engine.gui.client.GuiView(org.sprautestudio.spraute_engine.gui.GuiDoc.fromJson(json), config.project);
            v.drawBackground = false;
            v.hudState = () -> Minecraft.getInstance().player != null ? state(Minecraft.getInstance().player) : null;
            v.restartAppear();
            WINDOW_VIEW.put(role, v);
        }
        return WINDOW_VIEW.get(role);
    }

    public static org.sprautestudio.spraute_engine.gui.client.GuiView hudLayout() {
        return window("hud");
    }

    /** Draws a window's view over the whole screen. */
    public static void drawWindow(GuiGraphics g, org.sprautestudio.spraute_engine.gui.client.GuiView v, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int sw = g.guiWidth(), sh = g.guiHeight();
        var c = new org.sprautestudio.spraute_engine.ui.Canvas(g, mc.font, 1f);
        v.bounds(0, 0, sw, sh);
        v.layout();
        v.render(c, mx, my, mc.getDeltaFrameTime() / 20f);
        c.flush();
    }

    /** What the HUD shows right now. */
    public static HudPainter.State state(LocalPlayer p) {
        HudPainter.State s = new HudPainter.State();
        s.stHealth = config.health;
        s.hp = p.getHealth();
        s.maxHp = p.getMaxHealth();
        s.armor = p.getArmorValue();
        s.food = p.getFoodData().getFoodLevel();
        s.air = p.getAirSupply();
        s.maxAir = p.getMaxAirSupply();
        s.showAir = p.isEyeInFluid(FluidTags.WATER) || s.air < s.maxAir;
        s.exp = p.experienceProgress;
        s.level = p.experienceLevel;
        s.selected = p.getInventory().selected;
        for (int i = 0; i < 9; i++) s.hotbar[i] = p.getInventory().getItem(i);
        s.offhand = p.getOffhandItem();
        s.hotbarSize = org.sprautestudio.spraute_engine.core.client.SlotsClient.hotbarSize();
        s.coins = org.sprautestudio.spraute_engine.core.system.Coins.shown();
        s.coinsNeed = org.sprautestudio.spraute_engine.core.system.Coins.shownNeed();
        return s;
    }

    public static void onOverlay(ClientHooks.RenderHudPart event) {
        ResourceLocation id = event.id();
        String key = VANILLA.get(id);
        if (id.equals(HEALTH_OVERLAY)) key = "health";
        if (id.equals(COINS_OVERLAY)) key = "coins";
        if (key == null) return;
        // a HUD laid out in the interface editor: it draws every part itself (HudOverlays), the game's are off
        if (hudLayout() != null) {
            event.setCanceled(true);
            return;
        }
        // hearts and the round bar take turns: the system switch decides which one is the health part
        if (key.equals("health") && id.equals(HEALTH_OVERLAY) != config.health) {
            event.setCanceled(true);
            return;
        }
        if (key.equals("coins") && !config.currency) {
            event.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        // a limited hotbar: only the open slots are drawn
        if (key.equals("hotbar") && org.sprautestudio.spraute_engine.core.client.SlotsClient.hotbarSize() < 9 && !player.isSpectator()) {
            event.setCanceled(true);
            if (!mc.options.hideGui) {
                int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
                HudPainter.State st = state(player);
                HudPainter.standard(event.getGuiGraphics(), mc.font, key, HudPainter.rect(config, key, w, h, st), st);
            }
            return;
        }
        EngineConfig.Element e = config.element(key);
        if (!e.visible) {
            event.setCanceled(true);
            return;
        }
        int sw = event.getWindow().getGuiScaledWidth(), sh = event.getWindow().getGuiScaledHeight();
        HudPainter.State s = state(player);
        GuiGraphics g = event.getGuiGraphics();
        if (!EngineConfig.gameDrawn(e.style)) {
            event.setCanceled(true);
            if (mc.options.hideGui || !shown(mc, key, s)) return;
            HudPainter.custom(g, mc.font, config, key, HudPainter.rect(config, key, sw, sh, s), s);
            return;
        }
        if (e.customPos && event.canDrawOriginal()) {
            int[] d = HudPainter.shift(config, key, sw, sh, s);
            if (d[0] != 0 || d[1] != 0) {
                // draw it ourselves, moved; leaving the pose pushed until Post would leak when anyone cancels in between
                event.setCanceled(true);
                g.pose().pushPose();
                g.pose().translate(d[0], d[1], 0);
                try {
                    event.drawOriginal(sw, sh);
                } finally {
                    g.pose().popPose();
                }
            }
        }
    }

    /** Vanilla's rules for when a part appears at all. */
    public static boolean shown(Minecraft mc, String key, HudPainter.State s) {
        if (mc.gameMode == null || mc.player == null || mc.player.isSpectator()) return false;
        boolean survival = mc.gameMode.canHurtPlayer();
        return switch (key) {
            case "health", "food" -> survival;
            case "armor" -> survival && s.armor > 0;
            case "air" -> survival && s.showAir;
            case "exp" -> mc.gameMode.hasExperience();
            default -> true;
        };
    }

    /** "Hide chat": what players write is not shown (the story still gets it). */
    public static void onChat(ClientHooks.ChatFromPlayer event) {
        if (config.chat) event.setCanceled(true);
    }

    /** The engine's own HUD parts: the round health bar and the money. */
    public static final class Overlays {
        public static void registerHooks() {
            ClientHooks.REGISTER_HUD.add(Overlays::register);
        }

        public static void register(ClientHooks.RegisterHud e) {
            e.registerAbove(ClientHooks.HudParts.PLAYER_HEALTH, HEALTH_OVERLAY.getPath(), (g, partial, w, h) -> draw(g, "health", w, h));
            e.registerAbove(ClientHooks.HudParts.PLAYER_HEALTH, COINS_OVERLAY.getPath(), (g, partial, w, h) -> draw(g, "coins", w, h));
        }

        private static void draw(GuiGraphics g, String key, int w, int h) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            // what stands above the HUD keeps out of a fully covered screen
            if (org.sprautestudio.spraute_engine.core.client.ScreenFx.fullyCovered()) return;
            HudPainter.State s = state(mc.player);
            if (shown(mc, key, s)) HudPainter.standard(g, mc.font, key, HudPainter.defaultRect(config, key, w, h, s), s);
        }
    }
}
