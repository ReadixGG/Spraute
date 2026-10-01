package org.zonarstudio.spraute_engine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.20.1 {
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Quaternionf;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
*///?}
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.resource.SprauteDefaultNpcAssets;
import org.zonarstudio.spraute_engine.network.SprauteUiActionPacket;
import org.zonarstudio.spraute_engine.network.SprauteUiVideoStatePacket;
import org.zonarstudio.spraute_engine.client.ui.UiSvgTextures;
import org.zonarstudio.spraute_engine.client.ui.UiVideoPlayer;
import org.zonarstudio.spraute_engine.script.SprauteColors;
import org.zonarstudio.spraute_engine.ui.SprauteUiJson;
import org.zonarstudio.spraute_engine.ui.UiChildAlign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Script-driven overlay: panels, images, text, buttons, entity preview.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class SprauteScriptScreen extends Screen {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Last hovered widget id sent to server (HUD overlays). */
    private static String clientHoverWidgetId = null;
    private static int hoverTickCounter = 0;

    private static final java.util.Map<String, SprauteScriptScreen> activeOverlays = new java.util.LinkedHashMap<>();
    /** Patches received before overlay widgets exist (race with overlayOpen packet). */
    private static final java.util.List<PendingWidgetPatch> pendingWidgetPatches = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Legacy accessor — returns first overlay or null. */
    public static SprauteScriptScreen activeOverlay = null;

    private record PendingWidgetPatch(String widgetId, String field, String value) {}

    private final JsonObject root;
    private final int panelW;
    private final int panelH;
    /** Parent size for percent layout patches (editor gizmo drag). 0 = use panel. */
    private int patchParentW;
    private int patchParentH;
    private final int bgArgb;
    private final SprauteUiRender.Style rootStyle;
    /** Overlay z-layer ("layer" in root JSON): sorted ascending, < 0 = below the vanilla HUD. */
    private final int overlayLayer;

    /** Keyframe animator attached to the canvas ("animators" in root JSON). */
    private record NodeAnimator(String file, String clip, String target) {}
    private final java.util.List<NodeAnimator> nodeAnimators = new java.util.ArrayList<>();
    private final long nodeAnimatorStartMs = System.currentTimeMillis();
    /** widgetId -> baseline {x, y, w, h, rotation} captured before the first animator frame. */
    private final Map<String, float[]> nodeAnimatorBase = new java.util.HashMap<>();
    /** When false, skip the vanilla Screen background dimming (used for cinematic dialogs). */
    private boolean dimBackground = true;
    private final List<Widget> widgets = new ArrayList<>();
    private final Map<String, UiVideoPlayer> videoPlayers = new HashMap<>();
    private int videoSyncTick;
    private final Map<String, JsonObject> widgetTextStyles = new HashMap<>();
    private int left;
    private int top;
    public static final java.util.Set<String> monitorOverlaps = new java.util.HashSet<>();
    public static final java.util.Map<String, Boolean> overlapState = new java.util.HashMap<>();
    private String activeInputId = null;
    /** When true, do not notify server (programmatic close / replace). */
    private boolean suppressClosePacket;
    private boolean canClose = true;
    public float currentAlpha = 1.0f;
    /** True for HUD chat overlays ({@link #openOverlay}); false for full {@link #open} screens. */
    private final boolean hudOverlay;

    //? if >=1.20.1 {
    private static GuiGraphics scissorGuiGraphics;
    //?}

    private static UiDragSession uiDragSession;

    private static final class UiDragSession {
        final SprauteScriptScreen screen;
        final DragW widget;
        final int originX;
        final int originY;
        final int grabDx;
        final int grabDy;
        final int startMx;
        final int startMy;
        final int button;
        final String pendingButtonId;
        boolean active;
        boolean startSent;
        DropW hotDrop;

        UiDragSession(SprauteScriptScreen screen, DragW widget, int grabDx, int grabDy,
                      int startMx, int startMy, int button, String pendingButtonId) {
            this.screen = screen;
            this.widget = widget;
            this.originX = widget.x;
            this.originY = widget.y;
            this.grabDx = grabDx;
            this.grabDy = grabDy;
            this.startMx = startMx;
            this.startMy = startMy;
            this.button = button;
            this.pendingButtonId = pendingButtonId;
        }
    }

    /** Parses the extra style props (radius/gradient/border/shadow) from widget JSON. */
    static SprauteUiRender.Style parseStyle(JsonObject w) {
        float radius = w.has("radius") ? w.get("radius").getAsFloat() : 0f;
        int[] gradientStops = null;
        float gradientAngle = 90f;
        if (w.has("gradient")) {
            // "gradient" may hold several comma-separated stops: "#c2" or "#c2,#c3,#c4"
            String[] parts = w.get("gradient").getAsString().split(",");
            java.util.List<Integer> stops = new java.util.ArrayList<>();
            for (String part : parts) {
                String s = part.trim();
                if (!s.isEmpty()) stops.add(parseColor(s));
            }
            if (!stops.isEmpty()) {
                gradientStops = new int[stops.size()];
                for (int i = 0; i < stops.size(); i++) gradientStops[i] = stops.get(i);
            }
            if (w.has("gradientDir")) {
                String dir = w.get("gradientDir").getAsString().trim().toLowerCase();
                if (dir.startsWith("h") || dir.startsWith("г")) {
                    gradientAngle = 0f;
                } else if (dir.startsWith("v")) {
                    gradientAngle = 90f;
                } else {
                    try { gradientAngle = Float.parseFloat(dir); } catch (NumberFormatException ignored) {}
                }
            }
        }
        float border = w.has("border") ? w.get("border").getAsFloat() : 0f;
        int borderColor = w.has("borderColor") ? parseColor(w.get("borderColor").getAsString()) : 0xFFFFFFFF;
        if (border > 0f && !w.has("borderColor")) borderColor = 0x66FFFFFF;
        // Border gradient: "borderGradient" = comma-separated stops, "borderGradientDir" = angle/h/v.
        int[] borderStops = null;
        float borderAngle = 90f;
        if (w.has("borderGradient")) {
            java.util.List<Integer> stops = new java.util.ArrayList<>();
            for (String part : w.get("borderGradient").getAsString().split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) stops.add(parseColor(s));
            }
            if (stops.size() == 1) stops.add(0, borderColor);
            if (stops.size() >= 2) {
                borderStops = new int[stops.size()];
                for (int i = 0; i < stops.size(); i++) borderStops[i] = stops.get(i);
            }
            if (w.has("borderGradientDir")) {
                String dir = w.get("borderGradientDir").getAsString().trim().toLowerCase();
                if (dir.startsWith("h") || dir.startsWith("г")) {
                    borderAngle = 0f;
                } else if (dir.startsWith("v")) {
                    borderAngle = 90f;
                } else {
                    try { borderAngle = Float.parseFloat(dir); } catch (NumberFormatException ignored) {}
                }
            }
        }
        float shadow = w.has("shadow") ? w.get("shadow").getAsFloat() : 0f;
        float outerBorder = w.has("outerBorder") ? w.get("outerBorder").getAsFloat() : 0f;
        int outerBorderColor = w.has("outerBorderColor") ? parseColor(w.get("outerBorderColor").getAsString()) : 0xFFFFFFFF;
        if (outerBorder > 0f && !w.has("outerBorderColor")) outerBorderColor = 0x66FFFFFF;
        int[] outerBorderStops = null;
        float outerBorderAngle = 90f;
        if (w.has("outerBorderGradient")) {
            java.util.List<Integer> stops = new java.util.ArrayList<>();
            for (String part : w.get("outerBorderGradient").getAsString().split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) stops.add(parseColor(s));
            }
            if (stops.size() == 1) stops.add(0, outerBorderColor);
            if (stops.size() >= 2) {
                outerBorderStops = new int[stops.size()];
                for (int i = 0; i < stops.size(); i++) outerBorderStops[i] = stops.get(i);
            }
            if (w.has("outerBorderGradientDir")) {
                String dir = w.get("outerBorderGradientDir").getAsString().trim().toLowerCase();
                if (dir.startsWith("h") || dir.startsWith("г")) {
                    outerBorderAngle = 0f;
                } else if (dir.startsWith("v")) {
                    outerBorderAngle = 90f;
                } else {
                    try { outerBorderAngle = Float.parseFloat(dir); } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (radius <= 0f && gradientStops == null && border <= 0f && outerBorder <= 0f && shadow <= 0f) {
            return SprauteUiRender.Style.NONE;
        }
        return new SprauteUiRender.Style(radius, gradientStops, gradientAngle, border, borderColor, borderStops, borderAngle, shadow,
                outerBorder, outerBorderColor, outerBorderStops, outerBorderAngle);
    }

    /** Style with alpha applied to gradient/border colors so clip-alpha fades affect them too. */
    static SprauteUiRender.Style styleWithAlpha(SprauteUiRender.Style st, float alpha) {
        if (st == null || st.isPlain() || alpha >= 1f) return st;
        int[] stops = null;
        if (st.hasGradient()) {
            stops = new int[st.gradientStops().length];
            for (int i = 0; i < stops.length; i++) stops[i] = applyAlpha(st.gradientStops()[i], alpha);
        }
        int[] borderStops = null;
        if (st.borderStops() != null) {
            borderStops = new int[st.borderStops().length];
            for (int i = 0; i < borderStops.length; i++) borderStops[i] = applyAlpha(st.borderStops()[i], alpha);
        }
        int[] outerBorderStops = null;
        if (st.outerBorderStops() != null) {
            outerBorderStops = new int[st.outerBorderStops().length];
            for (int i = 0; i < outerBorderStops.length; i++) outerBorderStops[i] = applyAlpha(st.outerBorderStops()[i], alpha);
        }
        return new SprauteUiRender.Style(st.radius(), stops, st.gradientAngle(),
                st.borderWidth(), applyAlpha(st.borderColor(), alpha),
                borderStops, st.borderAngle(), st.shadowSize(),
                st.outerBorderWidth(), applyAlpha(st.outerBorderColor(), alpha),
                outerBorderStops, st.outerBorderAngle());
    }

    public static int applyAlpha(int color, float alpha) {
        if (alpha >= 1.0f) return color;
        if (alpha <= 0.0f) return color & 0x00FFFFFF;
        int a = (color >> 24) & 0xFF;
        a = (int) (a * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static String videoWaitTitle(UiVideoPlayer player) {
        String err = player.error();
        if (err != null && !err.isEmpty() && !player.isPreparing()) return err;
        return Component.translatable("spraute_engine.video.decoding").getString();
    }

    private static String videoWaitEta(UiVideoPlayer player) {
        long eta = player.preparingEtaMillis();
        if (eta < 0L) {
            return Component.translatable("spraute_engine.video.decoding_eta_unknown").getString();
        }
        return Component.translatable("spraute_engine.video.decoding_eta", formatEta(eta)).getString();
    }

    private static String formatEta(long millis) {
        long sec = Math.max(0L, (millis + 500L) / 1000L);
        long hours = sec / 3600L;
        long mins = (sec % 3600L) / 60L;
        long rem = sec % 60L;
        if (hours > 0L) {
            return hours + ":" + String.format(java.util.Locale.ROOT, "%02d:%02d", mins, rem);
        }
        return mins + ":" + String.format(java.util.Locale.ROOT, "%02d", rem);
    }

    //? if >=1.20.1 {
    private static void renderVideoWait(GuiGraphics g, int x, int y, int w, int h,
                                        UiVideoPlayer player, float alpha) {
        SprauteGuiDraw.fill(g, x, y, x + w, y + h, applyAlpha(0xAA080A0E, alpha));
        Font font = Minecraft.getInstance().font;
        int cx = x + w / 2;
        int cy = y + h / 2;
        int iconSize = Math.min(40, Math.max(20, Math.min(w, h) / 5));
        ResourceLocation icon = org.zonarstudio.spraute_engine.client.ui.VideoWaitIcon.location();
        if (icon != null && player.isPreparing()) {
            float angle = (System.currentTimeMillis() % 1000L) / 1000f * 360f;
            g.pose().pushPose();
            g.pose().translate(cx, cy - 10, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
            g.pose().translate(-iconSize / 2f, -iconSize / 2f, 0);
            g.setColor(0.72f, 0.82f, 1f, alpha);
            g.blit(icon, 0, 0, 0f, 0f, iconSize, iconSize, iconSize, iconSize);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
            SprauteGuiDraw.drawCenteredString(g, font, videoWaitEta(player), cx, cy + iconSize / 2 + 6,
                    applyAlpha(0xFFE8EEF8, alpha));
        } else {
            SprauteGuiDraw.drawCenteredString(g, font, videoWaitTitle(player), cx, cy - 6,
                    applyAlpha(0xFFFFFFFF, alpha));
            if (player.isPreparing()) {
                SprauteGuiDraw.drawCenteredString(g, font, videoWaitEta(player), cx, cy + 8,
                        applyAlpha(0xFFB8C4D4, alpha));
            }
        }
    }
    //?} else {
    /*private static void renderVideoWait(PoseStack pose, int x, int y, int w, int h,
                                        UiVideoPlayer player, float alpha) {
        SprauteGuiDraw.fill(pose, x, y, x + w, y + h, applyAlpha(0xAA080A0E, alpha));
        Font font = Minecraft.getInstance().font;
        int cx = x + w / 2;
        int cy = y + h / 2;
        int iconSize = Math.min(40, Math.max(20, Math.min(w, h) / 5));
        ResourceLocation icon = org.zonarstudio.spraute_engine.client.ui.VideoWaitIcon.location();
        if (icon != null && player.isPreparing()) {
            float angle = (System.currentTimeMillis() % 1000L) / 1000f * 360f;
            pose.pushPose();
            pose.translate(cx, cy - 10, 0);
            pose.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(angle));
            pose.translate(-iconSize / 2f, -iconSize / 2f, 0);
            RenderSystem.setShaderColor(0.72f, 0.82f, 1f, alpha);
            RenderSystem.setShaderTexture(0, icon);
            GuiComponent.blit(pose, 0, 0, 0f, 0f, iconSize, iconSize, iconSize, iconSize);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            pose.popPose();
            SprauteGuiDraw.drawCenteredString(pose, font, videoWaitEta(player), cx, cy + iconSize / 2 + 6,
                    applyAlpha(0xFFE8EEF8, alpha));
        } else {
            SprauteGuiDraw.drawCenteredString(pose, font, videoWaitTitle(player), cx, cy - 6,
                    applyAlpha(0xFFFFFFFF, alpha));
            if (player.isPreparing()) {
                SprauteGuiDraw.drawCenteredString(pose, font, videoWaitEta(player), cx, cy + 8,
                        applyAlpha(0xFFB8C4D4, alpha));
            }
        }
    }
    *///?}


    public SprauteScriptScreen(JsonObject root) {
        this(root, false, null);
    }

    public int getPanelW() { return panelW; }
    public int getPanelH() { return panelH; }
    public int getPanelLeft() { return left; }
    public int getPanelTop() { return top; }

    private SprauteScriptScreen(JsonObject root, boolean hudOverlay) {
        this(root, hudOverlay, null);
    }

    private SprauteScriptScreen(JsonObject root, boolean hudOverlay,
                                java.util.Map<String, UiVideoPlayer> reusePlayers) {
        super(Component.empty());
        this.root = root;
        this.hudOverlay = hudOverlay;
        if (reusePlayers != null && !reusePlayers.isEmpty()) {
            videoPlayers.putAll(reusePlayers);
        }
        Minecraft mc = Minecraft.getInstance();
        int sw = 854;
        int sh = 480;
        if (mc != null && mc.getWindow() != null) {
            sw = mc.getWindow().getGuiScaledWidth();
            sh = mc.getWindow().getGuiScaledHeight();
        }
        this.panelW = readRootExtent(root, "w", sw, 200);
        this.panelH = readRootExtent(root, "h", sh, 150);
        this.bgArgb = parseColor(root.has("bg") ? root.get("bg").getAsString() : "#C0101010");
        this.rootStyle = parseStyle(root);
        // Z-layer for overlays: sorted ascending; layers < 0 render below the vanilla HUD.
        this.overlayLayer = root.has("layer") ? root.get("layer").getAsInt() : 0;
        if (root.has("animators") && root.get("animators").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("animators")) {
                if (!el.isJsonObject()) continue;
                JsonObject a = el.getAsJsonObject();
                if (!a.has("file")) continue;
                nodeAnimators.add(new NodeAnimator(
                        a.get("file").getAsString(),
                        a.has("clip") ? a.get("clip").getAsString() : "",
                        a.has("target") ? a.get("target").getAsString() : ""));
            }
        }
        this.canClose = !root.has("canClose") || root.get("canClose").getAsBoolean();
        this.dimBackground = !root.has("dimBackground") || root.get("dimBackground").getAsBoolean();
        if (root.has("dim_background")) {
            this.dimBackground = root.get("dim_background").getAsBoolean();
        }
        parseWidgets();
    }

    /** Root {@code w}/{@code h}: pixel number or {@code "100%"} of scaled GUI size. */
    private static int readRootExtent(JsonObject root, String key, int screenDim, int def) {
        if (!root.has(key)) return def;
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull()) return def;
        if (!el.isJsonPrimitive()) return def;
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsInt();
        }
        if (p.isString()) {
            String s = p.getAsString().trim();
            if ("center".equalsIgnoreCase(s)) {
                return def;
            }
            if (s.endsWith("%") && screenDim > 0) {
                try {
                    float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                    return Math.max(0, (int) (pct / 100f * screenDim));
                } catch (NumberFormatException ignored) {
                    return def;
                }
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static void open(String json) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Float> scrollMemory = null;
            if (mc.screen instanceof SprauteScriptScreen prev) {
                scrollMemory = prev.captureScrollOffsets();
                prev.suppressClosePacket = true;
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            SprauteScriptScreen next = new SprauteScriptScreen(root);
            if (scrollMemory != null && !scrollMemory.isEmpty()) {
                next.applyScrollOffsets(scrollMemory);
            }
            mc.setScreen(next);
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[Spraute] Invalid UI JSON: " + e.getMessage()), false);
            }
        }
    }

    /** Remember vertical scroll for each scroll widget id when replacing this screen (e.g. quest list refresh). */
    Map<String, Float> captureScrollOffsets() {
        Map<String, Float> out = new HashMap<>();
        for (Widget w : widgets) {
            captureScrollOffsets(w, out);
        }
        return out;
    }

    private static void captureScrollOffsets(Widget w, Map<String, Float> out) {
        if (w instanceof ScrollW sw && sw.id != null && !sw.id.isEmpty()) {
            out.put(sw.id, sw.scrollOffset);
        }
        List<Widget> kids = nestedUiChildren(w);
        if (kids != null) {
            for (Widget child : kids) captureScrollOffsets(child, out);
        } else if (w instanceof RotatedW rw) {
            captureScrollOffsets(rw.child, out);
        }
    }

    void applyScrollOffsets(Map<String, Float> offsets) {
        if (offsets == null || offsets.isEmpty()) return;
        for (Widget w : widgets) {
            applyScrollOffset(w, offsets);
        }
    }

    private static void applyScrollOffset(Widget w, Map<String, Float> offsets) {
        if (w instanceof ScrollW sw && sw.id != null && offsets.containsKey(sw.id)) {
            float maxScroll = Math.max(0, sw.contentH - sw.h);
            float off = offsets.get(sw.id);
            sw.scrollOffset = Math.max(0, Math.min(off, maxScroll));
        }
        List<Widget> kids = nestedUiChildren(w);
        if (kids != null) {
            for (Widget child : kids) applyScrollOffset(child, offsets);
        } else if (w instanceof RotatedW rw) {
            applyScrollOffset(rw.child, offsets);
        }
    }

    public static void closeIfActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SprauteScriptScreen ss) {
            ss.suppressClosePacket = true;
            mc.setScreen(null);
        }
    }

    /** S2C: {@link org.zonarstudio.spraute_engine.network.UpdateSprauteUiWidgetPacket} */
    public static void applyWidgetPatchFromServer(String widgetId, String field, String value) {
        boolean applied = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SprauteScriptScreen screen) {
            applied = screen.applyWidgetPatch(widgetId, field, value) || applied;
        }
        for (SprauteScriptScreen overlay : activeOverlays.values()) {
            applied = overlay.applyWidgetPatch(widgetId, field, value) || applied;
        }
        if (!applied) {
            pendingWidgetPatches.add(new PendingWidgetPatch(widgetId, field, value));
            if ("chat_clip".equals(widgetId)) {
                boolean fadeOut = value != null && value.startsWith("~ANIM:")
                        && (value.endsWith(":0.0") || value.endsWith(":0"));
                if (!fadeOut) {
                    LOGGER.info("[CHAT-CLIENT] patch PENDING (no overlay yet) widget='{}' field='{}' value='{}'", widgetId, field, value);
                }
            }
        }
    }

    private static void flushPendingWidgetPatches(SprauteScriptScreen target) {
        if (pendingWidgetPatches.isEmpty()) return;
        pendingWidgetPatches.removeIf(p -> target.applyWidgetPatch(p.widgetId, p.field, p.value));
    }

    private boolean applyWidgetPatch(String widgetId, String field, String value) {
        if (widgetId == null || widgetId.isEmpty()) return false;
        String f = field != null ? field.trim().toLowerCase() : "";
        String v = value != null ? value : "";

        if (v.startsWith("~ANIM:")) {
            try {
                String[] parts = v.substring(6).split(":", 3);
                float durationSec = Float.parseFloat(parts[0]);
                String easing = parts[1];
                float endVal = Float.parseFloat(parts[2]);
                Widget targetWidget = findWidgetById(widgetId);
                if (targetWidget != null) {
                    float startVal = getWidgetFieldAsFloat(targetWidget, f);
                    // Replace any in-flight animation on the same widget field.
                    animations.computeIfAbsent(widgetId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .removeIf(a -> f.equals(a.field));
                    animations.get(widgetId)
                            .add(new AnimState(f, startVal, endVal, (long) (durationSec * 1000L), easing));
                    if ("chat_clip".equals(widgetId)) {
                        LOGGER.info("[CHAT-CLIENT] anim ADD widget='{}' field='{}' {}->{} dur={}s", widgetId, f, startVal, endVal, durationSec);
                    }
                    return true;
                }
            } catch (Exception e) {
            }
            return false;
        }

        if (f.equals("scrolloffset") || f.equals("offset") || f.equals("scrolly")) {
            Widget targetWidget = findWidgetById(widgetId);
            if (targetWidget instanceof ScrollW sw) {
                try {
                    float off = Float.parseFloat(v.trim());
                    float maxScroll = Math.max(0, sw.contentH - sw.h);
                    sw.scrollOffset = Math.max(0, Math.min(off, maxScroll));
                    sw.invalidateFreeze();
                    return true;
                } catch (Exception ignored) {}
            }
            return false;
        }

        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            Widget replaced = applyWidgetPatchInWidget(w, widgetId, f, v);
            if (replaced != null) {
                if (replaced != w) widgets.set(i, replaced);
                return true;
            }
        }
        return false;
    }

    /** @return null if not found; parent widget (possibly replaced) if patched in subtree */
    private Widget applyWidgetPatchInWidget(Widget w, String widgetId, String field, String value) {
        if (widgetId.equals(widgetIdOf(w))) {
            Widget patched = patchWidget(w, field, value);
            return patched;
        }
        if (w instanceof RotatedW rw) {
            if (widgetId.equals(widgetIdOf(rw.child))) {
                Widget patched = patchWidget(rw.child, field, value);
                return patched != rw.child
                        ? new RotatedW(patched, rw.rotation, rw.pivotX, rw.pivotY, rw.ow, rw.oh, rw.id)
                        : w;
            }
            Widget inner = applyWidgetPatchInWidget(rw.child, widgetId, field, value);
            if (inner != null) {
                return inner != rw.child
                        ? new RotatedW(inner, rw.rotation, rw.pivotX, rw.pivotY, rw.ow, rw.oh, rw.id)
                        : w;
            }
            return null;
        }
        if (w instanceof ScrollW sw) {
            if (applyWidgetPatchInList(sw.children, widgetId, field, value)) {
                sw.invalidateFreeze();
                return w;
            }
        }
        if (w instanceof ClipW cw) {
            if (applyWidgetPatchInList(cw.children, widgetId, field, value)) {
                cw.invalidateFreeze();
                return w;
            }
        }
        if (w instanceof RectW rw) {
            if (applyWidgetPatchInList(rw.children, widgetId, field, value)) return w;
        }
        if (w instanceof GroupW gw) {
            if (applyWidgetPatchInList(gw.children, widgetId, field, value)) return w;
        }
        if (w instanceof DragW dw) {
            if (applyWidgetPatchInList(dw.children, widgetId, field, value)) return w;
        }
        if (w instanceof DropW drop) {
            if (applyWidgetPatchInList(drop.children, widgetId, field, value)) return w;
        }
        return null;
    }

    private boolean applyWidgetPatchInList(List<Widget> list, String widgetId, String field, String value) {
        for (int i = 0; i < list.size(); i++) {
            Widget w = list.get(i);
            if (widgetId.equals(widgetIdOf(w))) {
                Widget patched = patchWidget(w, field, value);
                if (patched != w) list.set(i, patched);
                return true;
            }
            Widget replaced = applyWidgetPatchInWidget(w, widgetId, field, value);
            if (replaced != null) {
                if (replaced != w) list.set(i, replaced);
                return true;
            }
        }
        return false;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, int[]> TEXTURE_SIZE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, ItemStack> ITEM_STACK_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, ResourceLocation> WIDGET_TEXTURE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static int[] texturePixelSize(ResourceLocation rl) {
        return texturePixelSize(rl, null);
    }

    private static int[] texturePixelSize(ResourceLocation rl, String pathHint) {
        String cacheKey = textureSizeCacheKey(rl, pathHint);
        int[] cached = TEXTURE_SIZE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        int[] fromDisk = readTextureSizeFromWorkspace(pathHint, rl);
        if (fromDisk != null) {
            TEXTURE_SIZE_CACHE.put(cacheKey, fromDisk);
            return fromDisk;
        }
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (opt.isPresent()) {
                try (var stream = opt.get().open();
                     com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    int[] size = new int[]{img.getWidth(), img.getHeight()};
                    TEXTURE_SIZE_CACHE.put(cacheKey, size);
                    return size;
                }
            }
        } catch (Exception ignored) {
        }
        // Do not cache the fallback — texture may load later.
        return new int[]{256, 256};
    }

    private static String textureSizeCacheKey(ResourceLocation rl, String pathHint) {
        if (pathHint != null && !pathHint.isEmpty()) return pathHint;
        return rl != null ? rl.toString() : "";
    }

    /** Reads PNG dimensions from spraute_engine/ workspace (case-insensitive filename). */
    private static int[] readTextureSizeFromWorkspace(String pathHint, ResourceLocation rl) {
        String rel = pathHint;
        if (rel == null || rel.isEmpty()) {
            rel = rl != null ? rl.getNamespace() + ":" + rl.getPath() : "";
        }
        if (rel.contains(":")) {
            rel = rel.split(":", 2)[1];
        }
        rel = rel.replace('\\', '/');
        if (rel.isEmpty()) return null;

        java.nio.file.Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        java.nio.file.Path[] roots = {
                gameDir.resolve("spraute_engine"),
                gameDir.resolve("run").resolve("spraute_engine")
        };
        for (java.nio.file.Path root : roots) {
            java.nio.file.Path disk = findCaseInsensitiveFile(root.resolve(rel));
            if (disk != null && java.nio.file.Files.exists(disk)) {
                try (var stream = java.nio.file.Files.newInputStream(disk);
                     com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    return new int[]{img.getWidth(), img.getHeight()};
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static java.nio.file.Path findCaseInsensitiveFile(java.nio.file.Path candidate) {
        if (candidate != null && java.nio.file.Files.exists(candidate)) {
            return candidate;
        }
        java.nio.file.Path parent = candidate != null ? candidate.getParent() : null;
        if (parent == null || !java.nio.file.Files.isDirectory(parent)) {
            return null;
        }
        String wanted = candidate.getFileName().toString();
        try (var entries = java.nio.file.Files.list(parent)) {
            return entries.filter(p -> p.getFileName().toString().equalsIgnoreCase(wanted))
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String stripImageTextureRegion(String path) {
        if (path != null && path.startsWith("player_skin:")) {
            int slash = path.indexOf('/', "player_skin:".length());
            if (slash > 0) return path.substring(0, slash);
        }
        return path;
    }

    /** Resolves UI texture paths: minecraft:/mod paths, and client keys like player_skin:uuid. */
    static ResourceLocation resolveWidgetTexture(String path) {
        if (path == null || path.isEmpty()) {
            return SprauteUiJson.textureRl("minecraft:textures/misc/unknown_pack.png");
        }
        return WIDGET_TEXTURE_CACHE.computeIfAbsent(path, p -> {
            String key = stripImageTextureRegion(p);
            if (key != null && key.startsWith("player_skin:")) {
                ResourceLocation skin = PlayerSkinTextures.resolveFromTextureKey(key);
                if (skin != null) return skin;
            }
            return SprauteUiJson.textureRl(key);
        });
    }

    /** MC skin coords are in 64×64 space; scales to actual texture size (64, 128, …). */
    static int[] resolveImageSrcRect(String texturePath, int jsonSrcU, int jsonSrcV, int jsonSrcW, int jsonSrcH) {
        if (jsonSrcW > 0) return new int[]{jsonSrcU, jsonSrcV, jsonSrcW, jsonSrcH};
        if (texturePath != null && texturePath.startsWith("player_skin:")) {
            int slash = texturePath.indexOf('/', "player_skin:".length());
            if (slash > 0) {
                String region = texturePath.substring(slash + 1).toLowerCase(Locale.ROOT);
                return switch (region) {
                    case "head" -> new int[]{8, 8, 8, 8};
                    case "hat", "overlay", "head_overlay" -> new int[]{40, 8, 8, 8};
                    default -> new int[]{-1, -1, -1, -1};
                };
            }
        }
        return new int[]{-1, -1, -1, -1};
    }

    /** Sprite rect in actual texture pixels. Explicit {@code src} is pixels; player_skin defaults stay in 64-space. */
    static int[] resolveFillSrcPixels(String texturePath, int jsonSrcU, int jsonSrcV, int jsonSrcW, int jsonSrcH,
                                      int texW, int texH) {
        if (jsonSrcW > 0) {
            return new int[]{jsonSrcU, jsonSrcV, jsonSrcW, Math.max(1, jsonSrcH)};
        }
        int[] implicit = resolveImageSrcRect(texturePath, 0, 0, 0, 0);
        if (implicit[2] > 0) {
            float scale = Math.max(1, texW) / 64f;
            return new int[]{
                    Math.round(implicit[0] * scale),
                    Math.round(implicit[1] * scale),
                    Math.max(1, Math.round(implicit[2] * scale)),
                    Math.max(1, Math.round(implicit[3] * scale))
            };
        }
        return new int[]{0, 0, Math.max(1, texW), Math.max(1, texH)};
    }

    private static int[] parseImageSrc(JsonObject w) {
        int[] r = {-1, -1, -1, -1};
        if (w.has("src")) {
            JsonArray a = w.getAsJsonArray("src");
            if (a.size() >= 4) {
                r[0] = a.get(0).getAsInt();
                r[1] = a.get(1).getAsInt();
                r[2] = a.get(2).getAsInt();
                r[3] = a.get(3).getAsInt();
            }
        } else if (w.has("srcW") || w.has("src_w")) {
            r[0] = w.has("srcU") ? w.get("srcU").getAsInt() : (w.has("src_u") ? w.get("src_u").getAsInt() : 0);
            r[1] = w.has("srcV") ? w.get("srcV").getAsInt() : (w.has("src_v") ? w.get("src_v").getAsInt() : 0);
            r[2] = w.has("srcW") ? w.get("srcW").getAsInt() : w.get("src_w").getAsInt();
            r[3] = w.has("srcH") ? w.get("srcH").getAsInt() : (w.has("src_h") ? w.get("src_h").getAsInt() : 0);
        }
        return r;
    }

    private static String parseFill(JsonObject w) {
        if (w.has("fill")) return w.get("fill").getAsString();
        if (w.has("fillMode")) return w.get("fillMode").getAsString();
        if (w.has("fill_mode")) return w.get("fill_mode").getAsString();
        return "";
    }

    private static float parseFillAmount(JsonObject w) {
        if (w.has("fillAmount")) return w.get("fillAmount").getAsFloat();
        if (w.has("fill_amount")) return w.get("fill_amount").getAsFloat();
        return 1f;
    }

    private static String parseFillOrigin(JsonObject w) {
        if (w.has("fillOrigin")) return w.get("fillOrigin").getAsString();
        if (w.has("fill_origin")) return w.get("fill_origin").getAsString();
        return "bottom";
    }

    private static String parseCropShape(JsonObject w) {
        if (w.has("cropShape")) return w.get("cropShape").getAsString();
        if (w.has("crop_shape")) return w.get("crop_shape").getAsString();
        if (w.has("srcShape")) return w.get("srcShape").getAsString();
        if (w.has("src_shape")) return w.get("src_shape").getAsString();
        return "rect";
    }

    private static int[] parseSrcPatch(String value) {
        if (value == null) return null;
        String[] parts = value.replace('[', ' ').replace(']', ' ').split("[,;\\s]+");
        java.util.List<Integer> nums = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p.isBlank()) continue;
            try { nums.add((int) Double.parseDouble(p.trim())); } catch (NumberFormatException ignored) {}
        }
        if (nums.size() < 4) return null;
        return new int[]{nums.get(0), nums.get(1), nums.get(2), nums.get(3)};
    }

    //? if >=1.20.1 {
    private static void renderNineSlice(GuiGraphics guiGraphics, ResourceLocation rl,
                                        int ix, int iy, int w, int h, int borders, float sliceScale, String textureHint) {
        int[] tex = texturePixelSize(rl, textureHint);
        int tw = tex[0];
        int th = tex[1];
        int b = Math.min(borders, Math.min(tw, th) / 2); // texture-space border (source)
        if (b <= 0) {
            blitImageTexture(guiGraphics, rl, ix, iy, w, h, -1, -1, -1, -1);
            return;
        }
        // Rendered border (destination). sliceScale < 1 makes the border finer/smaller on screen.
        int bs = Math.max(1, Math.round(b * (sliceScale <= 0 ? 1f : sliceScale)));
        bs = Math.min(bs, Math.min(w, h) / 2);
        if (bs <= 0) bs = 1;

        int midW = w - bs * 2;   // destination middle width
        int midH = h - bs * 2;   // destination middle height
        int srcMidW = tw - b * 2;
        int srcMidH = th - b * 2;

        // Corners (source b×b -> dest bs×bs, scaled).
        nine(guiGraphics, rl, ix, iy, bs, bs, 0, 0, b, b, tw, th);
        nine(guiGraphics, rl, ix + w - bs, iy, bs, bs, tw - b, 0, b, b, tw, th);
        nine(guiGraphics, rl, ix, iy + h - bs, bs, bs, 0, th - b, b, b, tw, th);
        nine(guiGraphics, rl, ix + w - bs, iy + h - bs, bs, bs, tw - b, th - b, b, b, tw, th);
        // Edges — single stretch blit (tiling with tiny sliceScale was dozens of draws per widget).
        if (midW > 0 && srcMidW > 0) {
            nine(guiGraphics, rl, ix + bs, iy, midW, bs, b, 0, srcMidW, b, tw, th);
            nine(guiGraphics, rl, ix + bs, iy + h - bs, midW, bs, b, th - b, srcMidW, b, tw, th);
        }
        if (midH > 0 && srcMidH > 0) {
            nine(guiGraphics, rl, ix, iy + bs, bs, midH, 0, b, b, srcMidH, tw, th);
            nine(guiGraphics, rl, ix + w - bs, iy + bs, bs, midH, tw - b, b, b, srcMidH, tw, th);
        }
        // Center — stretch.
        if (midW > 0 && midH > 0 && srcMidW > 0 && srcMidH > 0) {
            nine(guiGraphics, rl, ix + bs, iy + bs, midW, midH, b, b, srcMidW, srcMidH, tw, th);
        }
    }

    /** Scaled region blit: source (su,sv,sw,sh) stretched into dest (x,y,dw,dh). */
    private static void nine(GuiGraphics g, ResourceLocation rl, int x, int y, int dw, int dh,
                             int su, int sv, int sw, int sh, int tw, int th) {
        g.blit(rl, x, y, dw, dh, (float) su, (float) sv, sw, sh, tw, th);
    }

    private static void nineTileH(GuiGraphics g, ResourceLocation rl, int x, int y, int w, int h,
                                  int su, int sv, int sw, int sh, int tileW, int tw, int th) {
        int cx = x;
        int end = x + w;
        int step = Math.max(1, tileW);
        while (cx < end) {
            int dw = Math.min(step, end - cx);
            int srcW = Math.max(1, Math.round(dw * (sw / (float) step)));
            nine(g, rl, cx, y, dw, h, su, sv, srcW, sh, tw, th);
            cx += step;
        }
    }

    private static void nineTileV(GuiGraphics g, ResourceLocation rl, int x, int y, int w, int h,
                                  int su, int sv, int sw, int sh, int tileH, int tw, int th) {
        int cy = y;
        int end = y + h;
        int step = Math.max(1, tileH);
        while (cy < end) {
            int dh = Math.min(step, end - cy);
            int srcH = Math.max(1, Math.round(dh * (sh / (float) step)));
            nine(g, rl, x, cy, w, dh, su, sv, sw, srcH, tw, th);
            cy += step;
        }
    }

    private static void blitImageTexture(GuiGraphics guiGraphics, ResourceLocation rl,
                                         int dx, int dy, int dw, int dh,
                                         int srcU, int srcV, int srcW, int srcH) {
        int[] texSize = texturePixelSize(rl);
        if (srcW > 0) {
            float scale = texSize[0] / 64f;
            int su = Math.round(srcU * scale);
            int sv = Math.round(srcV * scale);
            int sw = Math.max(1, Math.round(srcW * scale));
            int sh = Math.max(1, Math.round(srcH * scale));
            SprauteGuiDraw.blitRegion(guiGraphics, rl, dx, dy, dw, dh, su, sv, sw, sh, texSize[0], texSize[1]);
        } else {
            // Stretch the WHOLE texture into the destination rect. A plain 1:1 blit reads dw×dh texels,
            // which exceeds a small texture (UV > 1) and tiles via GL_REPEAT instead of stretching.
            SprauteGuiDraw.blitRegion(guiGraphics, rl, dx, dy, dw, dh, 0, 0, texSize[0], texSize[1], texSize[0], texSize[1]);
        }
    }
    //?} else {
    /*private static void blitImageTexture(PoseStack poseStack, ResourceLocation rl,
                                         int dx, int dy, int dw, int dh,
                                         int srcU, int srcV, int srcW, int srcH) {
        int[] texSize = texturePixelSize(rl);
        if (srcW > 0) {
            float scale = texSize[0] / 64f;
            int su = Math.round(srcU * scale);
            int sv = Math.round(srcV * scale);
            int sw = Math.max(1, Math.round(srcW * scale));
            int sh = Math.max(1, Math.round(srcH * scale));
            SprauteGuiDraw.blitRegion(poseStack, dx, dy, dw, dh, su, sv, sw, sh, texSize[0], texSize[1]);
        } else {
            // Stretch the WHOLE texture into the destination rect (see 1.20.1 branch note).
            SprauteGuiDraw.blitRegion(poseStack, dx, dy, dw, dh, 0, 0, texSize[0], texSize[1], texSize[0], texSize[1]);
        }
    }*/
    //?}

    //? if <1.20.1 {
    /*private static void renderNineSlice(PoseStack poseStack, ResourceLocation rl,
                                        int ix, int iy, int w, int h, int borders, float sliceScale, String textureHint) {
        int[] tex = texturePixelSize(rl, textureHint);
        int tw = tex[0];
        int th = tex[1];
        int b = Math.min(borders, Math.min(tw, th) / 2);
        if (b <= 0) {
            blitImageTexture(poseStack, rl, ix, iy, w, h, -1, -1, -1, -1);
            return;
        }
        int bs = Math.max(1, Math.round(b * (sliceScale <= 0 ? 1f : sliceScale)));
        bs = Math.min(bs, Math.min(w, h) / 2);
        if (bs <= 0) bs = 1;

        int midW = w - bs * 2;
        int midH = h - bs * 2;
        int srcMidW = tw - b * 2;
        int srcMidH = th - b * 2;

        blitNineSliceRegion(poseStack, rl, ix, iy, bs, bs, 0, 0, b, b, tw, th);
        blitNineSliceRegion(poseStack, rl, ix + w - bs, iy, bs, bs, tw - b, 0, b, b, tw, th);
        blitNineSliceRegion(poseStack, rl, ix, iy + h - bs, bs, bs, 0, th - b, b, b, tw, th);
        blitNineSliceRegion(poseStack, rl, ix + w - bs, iy + h - bs, bs, bs, tw - b, th - b, b, b, tw, th);

        if (midW > 0 && srcMidW > 0) {
            blitNineSliceRegion(poseStack, rl, ix + bs, iy, midW, bs, b, 0, srcMidW, b, tw, th);
            blitNineSliceRegion(poseStack, rl, ix + bs, iy + h - bs, midW, bs, b, th - b, srcMidW, b, tw, th);
        }
        if (midH > 0 && srcMidH > 0) {
            blitNineSliceRegion(poseStack, rl, ix, iy + bs, bs, midH, 0, b, b, srcMidH, tw, th);
            blitNineSliceRegion(poseStack, rl, ix + w - bs, iy + bs, bs, midH, tw - b, b, b, srcMidH, tw, th);
        }
        if (midW > 0 && midH > 0 && srcMidW > 0 && srcMidH > 0) {
            blitNineSliceRegion(poseStack, rl, ix + bs, iy + bs, midW, midH, b, b, srcMidW, srcMidH, tw, th);
        }
    }

    private static void blitNineSliceRegion(PoseStack poseStack, ResourceLocation rl, int x, int y, int width, int height,
                                          int u, int v, int uWidth, int vHeight, int tw, int th) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, rl);
        SprauteGuiDraw.blitRegion(poseStack, x, y, width, height, u, v, uWidth, vHeight, tw, th);
    }*/
    //?}

    private static String widgetIdOf(Widget w) {
        if (w instanceof TextW tw) return tw.id != null ? tw.id : "";
        if (w instanceof ButtonW bw) return bw.id != null ? bw.id : "";
        if (w instanceof RectW rw) return rw.id != null ? rw.id : "";
        if (w instanceof ImageW iw) return iw.id != null ? iw.id : "";
        if (w instanceof EntityW ew) return ew.id != null ? ew.id : "";
        if (w instanceof ScrollW sw) return sw.id != null ? sw.id : "";
        if (w instanceof DividerW dw) return dw.id != null ? dw.id : "";
        if (w instanceof ItemW iw) return iw.id != null ? iw.id : "";
        if (w instanceof InputW inpw) return inpw.id != null ? inpw.id : "";
        if (w instanceof ClipW cw) return cw.id != null ? cw.id : "";
        if (w instanceof GroupW gw) return gw.id != null ? gw.id : "";
        if (w instanceof RotatedW rw) return rw.id != null ? rw.id : "";
        if (w instanceof VideoW vw) return vw.id != null ? vw.id : "";
        String id = w.getId();
        return id != null ? id : "";
    }

    private static class AnimState {
        final String field;
        final float startVal;
        final float endVal;
        final long startTime;
        final long durationMs;
        final String easing;

        AnimState(String field, float startVal, float endVal, long durationMs, String easing) {
            this.field = field;
            this.startVal = startVal;
            this.endVal = endVal;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.easing = easing != null ? easing : "linear";
        }

        float getCurrent() {
            long now = System.currentTimeMillis();
            if (now >= startTime + durationMs) return endVal;
            float t = (float) (now - startTime) / durationMs;
            t = applyEasing(t, easing);
            return startVal + (endVal - startVal) * t;
        }

        private float applyEasing(float t, String easing) {
            return switch (easing.toLowerCase()) {
                case "easein", "ease_in" -> t * t;
                case "easeout", "ease_out" -> t * (2 - t);
                case "easeinout", "ease_in_out" -> t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
                case "bounceout", "bounce_out" -> {
                    float n1 = 7.5625f;
                    float d1 = 2.75f;
                    if (t < 1 / d1) {
                        yield n1 * t * t;
                    } else if (t < 2 / d1) {
                        t -= 1.5f / d1;
                        yield n1 * t * t + 0.75f;
                    } else if (t < 2.5f / d1) {
                        t -= 2.25f / d1;
                        yield n1 * t * t + 0.9375f;
                    } else {
                        t -= 2.625f / d1;
                        yield n1 * t * t + 0.984375f;
                    }
                }
                case "elasticout", "elastic_out" -> {
                    float c4 = (float) (2 * Math.PI) / 3;
                    yield t == 0 ? 0 : t == 1 ? 1 : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75f) * c4) + 1);
                }
                default -> t; // linear
            };
        }

        boolean isDone() {
            return System.currentTimeMillis() >= startTime + durationMs;
        }
    }

    /** Bedrock-px offset of a humanoid head centre above the model origin (feet), for renderBones="Head". */
    private static final float HEAD_ONLY_ORIGIN_OFFSET_PX = 26f;

    private final Map<String, List<AnimState>> animations = new java.util.concurrent.ConcurrentHashMap<>();

    private float getWidgetFieldAsFloat(Widget w, String field) {
        if (w == null) return 0f;
        try {
            if (w instanceof TextW tw) {
                return switch (field) { case "x" -> tw.x; case "y" -> tw.y; case "scale" -> tw.scale; default -> 0f; };
            }
            if (w instanceof ButtonW bw) {
                return switch (field) { case "x" -> bw.x; case "y" -> bw.y; case "w" -> bw.w; case "h" -> bw.h; default -> 0f; };
            }
            if (w instanceof RectW rw) {
                return switch (field) { case "x" -> rw.x; case "y" -> rw.y; case "w" -> rw.w; case "h" -> rw.h; case "fillamount", "fill_amount" -> rw.fillAmount; default -> 0f; };
            }
            if (w instanceof ImageW iw) {
                return switch (field) { case "x" -> iw.x; case "y" -> iw.y; case "w" -> iw.w; case "h" -> iw.h; case "fillamount", "fill_amount" -> iw.fillAmount; default -> 0f; };
            }
            if (w instanceof SvgW svg) {
                return switch (field) { case "x" -> svg.x; case "y" -> svg.y; case "w" -> svg.w; case "h" -> svg.h; case "fillamount", "fill_amount" -> svg.fillAmount; case "strokewidth", "stroke_width" -> svg.strokeWidth; default -> 0f; };
            }
            if (w instanceof EntityW ew) {
                return switch (field) { case "x" -> ew.x; case "y" -> ew.y; case "w" -> ew.w; case "h" -> ew.h; case "scale" -> ew.scale; default -> 0f; };
            }
            if (w instanceof ScrollW sw) {
                return switch (field) {
                    case "x" -> sw.x;
                    case "y" -> sw.y;
                    case "w" -> sw.w;
                    case "h" -> sw.h;
                    case "scrolloffset", "offset", "scrolly" -> sw.scrollOffset;
                    default -> 0f;
                };
            }
            if (w instanceof ClipW cw) {
                return switch (field) {
                    case "x" -> cw.x;
                    case "y" -> cw.y;
                    case "w" -> cw.w;
                    case "h" -> cw.h;
                    case "alpha" -> cw.alpha;
                    case "feather", "cropfeather", "crop_feather", "edgeblur", "edge_blur" -> cw.feather;
                    case "featherl", "feather_l", "featherleft", "feather_left" -> cw.featherL;
                    case "feathert", "feather_t", "feathertop", "feather_top" -> cw.featherT;
                    case "featherr", "feather_r", "featherright", "feather_right" -> cw.featherR;
                    case "featherb", "feather_b", "featherbottom", "feather_bottom" -> cw.featherB;
                    case "feathersmooth", "feather_smooth" -> cw.featherSmooth ? 1f : 0f;
                    default -> 0f;
                };
            }
            if (w instanceof GroupW gw) {
                return switch (field) { case "x" -> gw.x; case "y" -> gw.y; case "w" -> gw.w; case "h" -> gw.h; case "alpha" -> gw.alpha; default -> 0f; };
            }
            if (w instanceof DragW dw) {
                return switch (field) { case "x" -> dw.x; case "y" -> dw.y; case "w" -> dw.w; case "h" -> dw.h; case "alpha" -> dw.alpha; default -> 0f; };
            }
            if (w instanceof DropW drop) {
                return switch (field) { case "x" -> drop.x; case "y" -> drop.y; case "w" -> drop.w; case "h" -> drop.h; case "alpha" -> drop.alpha; default -> 0f; };
            }
            if (w instanceof RotatedW rw) {
                if ("rotation".equals(field)) return rw.rotation;
                return getWidgetFieldAsFloat(rw.child, field);
            }
        } catch (Exception e) {}
        return 0f;
    }

    private Widget findWidgetByIdRec(Widget w, String id) {
        if (id.equals(widgetIdOf(w))) return w;
        if (w instanceof RotatedW rw) {
            return findWidgetByIdRec(rw.child, id);
        }
        if (w instanceof ScrollW sw) {
            for (Widget cw : sw.children) {
                Widget found = findWidgetByIdRec(cw, id);
                if (found != null) return found;
            }
        }
        if (w instanceof ClipW cw) {
            for (Widget ccw : cw.children) {
                Widget found = findWidgetByIdRec(ccw, id);
                if (found != null) return found;
            }
        }
        if (w instanceof RectW rw) {
            for (Widget rcw : rw.children) {
                Widget found = findWidgetByIdRec(rcw, id);
                if (found != null) return found;
            }
        }
        if (w instanceof GroupW gw) {
            for (Widget gcw : gw.children) {
                Widget found = findWidgetByIdRec(gcw, id);
                if (found != null) return found;
            }
        }
        if (w instanceof DragW dw) {
            for (Widget dcw : dw.children) {
                Widget found = findWidgetByIdRec(dcw, id);
                if (found != null) return found;
            }
        }
        if (w instanceof DropW drop) {
            for (Widget dcw : drop.children) {
                Widget found = findWidgetByIdRec(dcw, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Widget findWidgetById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Widget w : widgets) {
            Widget found = findWidgetByIdRec(w, id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Keyframe node animators: every frame samples the clip and writes absolute
     * values (baseline + keyframe) into the widgets. Bone name = widget id for
     * canvas-level animators; widget-level animators drive their target widget
     * with any bone. Position x/y in px (Blockbench Y up -> screen Y down),
     * scale multiplies w/h, rotation.z rotates in degrees.
     */
    private void applyNodeAnimators() {
        if (nodeAnimators.isEmpty()) return;
        float elapsed = (System.currentTimeMillis() - nodeAnimatorStartMs) / 1000f;
        org.zonarstudio.spraute_engine.core.math.SpVec3 v = new org.zonarstudio.spraute_engine.core.math.SpVec3();
        for (NodeAnimator na : nodeAnimators) {
            org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.AnimationSet set =
                    org.zonarstudio.spraute_engine.entity.client.SpAnimationCache.getOrLoad(na.file());
            org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.AnimationClip clip = null;
            if (na.clip() != null && !na.clip().isEmpty()) clip = set.get(na.clip());
            if (clip == null) {
                String fallback = set.firstPlayableName(na.clip());
                if (fallback != null && !fallback.isEmpty()) clip = set.get(fallback);
            }
            if (clip == null) continue;
            float len = clip.getLengthSec();
            float t = len <= 0f ? Math.max(0f, elapsed)
                    : clip.isLoop() ? ((elapsed % len) + len) % len
                    : Math.min(elapsed, len);
            for (Map.Entry<String, org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.BoneTrack> bone : clip.bones().entrySet()) {
                String widgetId = na.target() != null && !na.target().isEmpty() ? na.target() : bone.getKey();
                Widget w = findWidgetById(widgetId);
                if (w == null) continue;
                float[] base = nodeAnimatorBase.computeIfAbsent(widgetId, id -> new float[]{
                        getWidgetFieldAsFloat(w, "x"), getWidgetFieldAsFloat(w, "y"),
                        getWidgetFieldAsFloat(w, "w"), getWidgetFieldAsFloat(w, "h"),
                        getWidgetFieldAsFloat(w, "rotation")});
                org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.BoneTrack track = bone.getValue();
                if (!track.position().isEmpty()) {
                    track.position().sample(t, v);
                    applyWidgetPatch(widgetId, "x", String.valueOf(base[0] + v.x));
                    applyWidgetPatch(widgetId, "y", String.valueOf(base[1] - v.y));
                }
                if (!track.scale().isEmpty()) {
                    track.scale().sample(t, v);
                    if (base[2] > 0f) applyWidgetPatch(widgetId, "w", String.valueOf(base[2] * v.x));
                    if (base[3] > 0f) applyWidgetPatch(widgetId, "h", String.valueOf(base[3] * v.y));
                }
                if (!track.rotation().isEmpty()) {
                    track.rotation().sample(t, v);
                    applyWidgetPatch(widgetId, "rotation", String.valueOf(base[4] + v.z));
                }
            }
            for (Map.Entry<String, Map<String,
                    org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.PropertyTrack>> target
                    : clip.properties().entrySet()) {
                String widgetId = "$parent".equals(target.getKey()) && na.target() != null
                        ? na.target() : target.getKey();
                for (Map.Entry<String,
                        org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.PropertyTrack> property
                        : target.getValue().entrySet()) {
                    com.google.gson.JsonElement sampled = property.getValue().sample(t);
                    if (sampled == null || sampled.isJsonNull() || !sampled.isJsonPrimitive()) continue;
                    String value = sampled.getAsJsonPrimitive().isString()
                            ? sampled.getAsString() : sampled.toString();
                    applyWidgetPatch(widgetId, animatorWidgetField(property.getKey()), value);
                }
            }
        }
    }

    private static String animatorWidgetField(String property) {
        if (property == null) return "";
        return switch (property.trim().toLowerCase(Locale.ROOT)) {
            case "uiw", "uiwidth" -> "w";
            case "uih", "uiheight" -> "h";
            case "uicolor" -> "color";
            case "uihovercolor" -> "hover";
            case "uigradient" -> "gradient";
            case "uiradius" -> "radius";
            case "uiborder" -> "border";
            case "uiouterborder" -> "outerborder";
            case "uishadow" -> "shadow";
            case "uitext" -> "text";
            case "uitextscale" -> "scale";
            case "uitextoffsetx" -> "textoffsetx";
            case "uitextoffsety" -> "textoffsety";
            case "uialign" -> "align";
            case "uiwrap" -> "wrap";
            case "uitexture" -> "texture";
            case "uisvgstroke" -> "stroke";
            case "uisvgstrokewidth" -> "strokewidth";
            case "uisvgrecolor" -> "recolor";
            case "uialpha" -> "alpha";
            case "uifillamount" -> "fillamount";
            case "uivideo" -> "source";
            case "uivideoenabled" -> "enabled";
            case "uivideoplaying" -> "playing";
            case "uivideoloop" -> "loop";
            default -> property.trim().toLowerCase(Locale.ROOT);
        };
    }

    private void processAnimations() {
        applyNodeAnimators();
        for (Map.Entry<String, List<AnimState>> entry : animations.entrySet()) {
            String widgetId = entry.getKey();
            List<AnimState> list = entry.getValue();
            if (list == null) continue;
            // Iterate over a copy to avoid ConcurrentModificationException / UnsupportedOperationException
            List<AnimState> toRemove = new ArrayList<>();
            for (AnimState anim : list) {
                float current = anim.getCurrent();
                applyWidgetPatch(widgetId, anim.field, String.valueOf(current));
                if (anim.isDone()) {
                    toRemove.add(anim);
                    if ("chat_clip".equals(widgetId)) {
                        LOGGER.info("[CHAT-CLIENT] anim DONE widget='{}' field='{}' final={}", widgetId, anim.field, current);
                    }
                }
            }
            list.removeAll(toRemove);
        }
        // Cleanup empty lists
        animations.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
    }

    private int resolveSize(String val, boolean isWidth) {
        String v = val.trim();
        int parent = isWidth
                ? (patchParentW > 0 ? patchParentW : panelW)
                : (patchParentH > 0 ? patchParentH : panelH);
        if (v.endsWith("%")) {
            float pct = Float.parseFloat(v.substring(0, v.length() - 1));
            return (int) (pct / 100f * parent);
        }
        return (int) Float.parseFloat(v);
    }

    private Widget patchWidget(Widget w, String field, String value) {
        if (field.isEmpty()) return w;
        try {
            if (isTextStyleField(field) && w.getId() != null && !w.getId().isBlank()) {
                patchTextStyle(w.getId(), field, value);
                return w;
            }
            if (w instanceof RotatedW rw) {
                if (field.equals("rotation")) {
                    return new RotatedW(rw.child, Float.parseFloat(value.trim()),
                            rw.pivotX, rw.pivotY, rw.ow, rw.oh, rw.id);
                }
                Widget patched = patchWidget(rw.child, field, value);
                return patched != rw.child
                        ? new RotatedW(patched, rw.rotation, rw.pivotX, rw.pivotY, rw.ow, rw.oh, rw.id)
                        : w;
            }
            if (w instanceof VideoW vw) {
                return switch (field.toLowerCase(Locale.ROOT)) {
                    case "x" -> vw.withBounds(resolveSize(value, true), vw.y, vw.w, vw.h);
                    case "y" -> vw.withBounds(vw.x, resolveSize(value, false), vw.w, vw.h);
                    case "w" -> vw.withBounds(vw.x, vw.y, resolveSize(value, true), vw.h);
                    case "h" -> vw.withBounds(vw.x, vw.y, vw.w, resolveSize(value, false));
                    case "source", "video" -> vw.configure(value, vw.playerEnabled, vw.playing, vw.loop);
                    case "playerenabled", "player_enabled", "enabled" ->
                            vw.configure(vw.source, Boolean.parseBoolean(value.trim()), vw.playing, vw.loop);
                    case "playing", "play" ->
                            vw.configure(vw.source, vw.playerEnabled, Boolean.parseBoolean(value.trim()), vw.loop);
                    case "loop" ->
                            vw.configure(vw.source, vw.playerEnabled, vw.playing, Boolean.parseBoolean(value.trim()));
                    case "action", "videoaction", "video_action" -> {
                        String action = value.trim().toLowerCase(Locale.ROOT);
                        if ("stop".equals(action)) {
                            vw.player.stop();
                            yield vw.configure(vw.source, vw.playerEnabled, false, vw.loop);
                        }
                        if ("pause".equals(action)) yield vw.configure(vw.source, vw.playerEnabled, false, vw.loop);
                        if ("play".equals(action) || "resume".equals(action)) {
                            yield vw.configure(vw.source, vw.playerEnabled, true, vw.loop);
                        }
                        if ("restart".equals(action) || "replay".equals(action)) {
                            vw.player.restart();
                            yield vw.configure(vw.source, vw.playerEnabled, true, vw.loop);
                        }
                        yield w;
                    }
                    case "tooltip" -> new VideoW(vw.x, vw.y, vw.w, vw.h, vw.source,
                            vw.playerEnabled, vw.playing, vw.loop, value, vw.id, vw.player);
                    default -> w;
                };
            }
            if (w instanceof TextW tw) {
                return switch (field) {
                    case "x" -> new TextW(resolveSize(value, true), tw.y, tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "y" -> new TextW(tw.x, resolveSize(value, false), tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "text" -> new TextW(tw.x, tw.y, value, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "color" -> new TextW(tw.x, tw.y, tw.text, parseColor(value), tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "scale" -> new TextW(tw.x, tw.y, tw.text, tw.color, Float.parseFloat(value.trim()), tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "tooltip" -> new TextW(tw.x, tw.y, tw.text, tw.color, tw.scale, value, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, tw.textOffsetY);
                    case "textOffsetX", "textoffsetx", "text_offset_x" -> new TextW(tw.x, tw.y, tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, (int) Float.parseFloat(value.trim()), tw.textOffsetY);
                    case "textOffsetY", "textoffsety", "text_offset_y" -> new TextW(tw.x, tw.y, tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.wrapMode, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY, tw.textOffsetX, (int) Float.parseFloat(value.trim()));
                    default -> w;
                };
            }
            if (w instanceof ButtonW bw) {
                return switch (field) {
                    case "x" -> new ButtonW(bw.id, resolveSize(value, true), bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "y" -> new ButtonW(bw.id, bw.x, resolveSize(value, false), bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "w" -> new ButtonW(bw.id, bw.x, bw.y, resolveSize(value, true), bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "h" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, resolveSize(value, false), bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "label" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, value, bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "color" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), parseColor(value), bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "hover" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, parseColor(value), bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "texture" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, value, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "tooltip" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, value, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "labelOffsetX", "labeloffsetx", "label_offset_x", "textOffsetX", "textoffsetx", "text_offset_x" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), (int) Float.parseFloat(value.trim()), bw.labelOffsetY, bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                    case "labelOffsetY", "labeloffsety", "label_offset_y", "textOffsetY", "textoffsety", "text_offset_y" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, (int) Float.parseFloat(value.trim()), bw.align, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
                                        case "align", "labelAlign", "labelalign", "label_align" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.wrapMode(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.labelOffsetX, bw.labelOffsetY, value, bw.hoverChildren(), bw.hoverPw(), bw.hoverPh(), bw.style());
default -> w;
                };
            }
            if (w instanceof RectW rw) {
                return switch (field) {
                    case "x" -> rw.copy(resolveSize(value, true), rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "y" -> rw.copy(rw.x, resolveSize(value, false), rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "w" -> rw.copy(rw.x, rw.y, resolveSize(value, true), rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "h" -> rw.copy(rw.x, rw.y, rw.w, resolveSize(value, false), rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "color" -> rw.copy(rw.x, rw.y, rw.w, rw.h, parseColor(value), rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "tooltip" -> rw.copy(rw.x, rw.y, rw.w, rw.h, rw.color, value, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "fill", "fillmode", "fill_mode" -> rw.copy(rw.x, rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, value, rw.fillAmount, rw.fillOrigin, rw.cropShape);
                    case "fillamount", "fill_amount" -> rw.copy(rw.x, rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, Float.parseFloat(value.trim()), rw.fillOrigin, rw.cropShape);
                    case "fillorigin", "fill_origin" -> rw.copy(rw.x, rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, value, rw.cropShape);
                    case "cropshape", "crop_shape", "srcshape", "src_shape" -> rw.copy(rw.x, rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id, rw.style, rw.fill, rw.fillAmount, rw.fillOrigin, value);
                    default -> w;
                };
            }
            if (w instanceof SvgW sw) {
                return switch (field.toLowerCase(Locale.ROOT)) {
                    case "x" -> sw.withBounds(resolveSize(value, true), sw.y, sw.w, sw.h);
                    case "y" -> sw.withBounds(sw.x, resolveSize(value, false), sw.w, sw.h);
                    case "w" -> sw.withBounds(sw.x, sw.y, resolveSize(value, true), sw.h);
                    case "h" -> sw.withBounds(sw.x, sw.y, sw.w, resolveSize(value, false));
                    case "source", "svg", "texture" -> sw.withSource(value);
                    case "color" -> sw.withColor(value);
                    case "stroke" -> sw.withStroke(value);
                    case "strokewidth", "stroke_width" -> sw.withStrokeWidth(Float.parseFloat(value.trim()));
                    case "recolor" -> sw.withRecolor(parseSvgFlag(value));
                    case "tooltip" -> sw.withTooltip(value);
                    case "fill", "fillmode", "fill_mode" -> sw.withFill(value, sw.fillAmount, sw.fillOrigin, sw.cropShape);
                    case "fillamount", "fill_amount" -> sw.withFill(sw.fill, Float.parseFloat(value.trim()), sw.fillOrigin, sw.cropShape);
                    case "fillorigin", "fill_origin" -> sw.withFill(sw.fill, sw.fillAmount, value, sw.cropShape);
                    case "cropshape", "crop_shape", "srcshape", "src_shape" -> sw.withFill(sw.fill, sw.fillAmount, sw.fillOrigin, value);
                    default -> w;
                };
            }
            if (w instanceof ImageW iw) {
                return switch (field) {
                    case "x" -> new ImageW(resolveSize(value, true), iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "y" -> new ImageW(iw.x, resolveSize(value, false), iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "w" -> new ImageW(iw.x, iw.y, resolveSize(value, true), iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "h" -> new ImageW(iw.x, iw.y, iw.w, resolveSize(value, false), iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "texture" -> new ImageW(iw.x, iw.y, iw.w, iw.h, value, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "tooltip" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, value, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "fill", "fillmode", "fill_mode" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, value, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "fillamount", "fill_amount" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, Float.parseFloat(value.trim()), iw.fillOrigin, iw.cropShape);
                    case "fillorigin", "fill_origin" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, value, iw.cropShape);
                    case "cropshape", "crop_shape", "srcshape", "src_shape" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, value);
                    case "srcu", "src_u" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, (int) Float.parseFloat(value.trim()), iw.srcV, iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "srcv", "src_v" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, (int) Float.parseFloat(value.trim()), iw.srcW, iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "srcw", "src_w" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, (int) Float.parseFloat(value.trim()), iw.srcH, iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "srch", "src_h" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, (int) Float.parseFloat(value.trim()), iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    case "src" -> {
                        int[] src = parseSrcPatch(value);
                        yield src == null ? w : new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, src[0], src[1], src[2], src[3], iw.fill, iw.fillAmount, iw.fillOrigin, iw.cropShape);
                    }
                    default -> w;
                };
            }
            if (w instanceof ItemW iw) {
                return switch (field) {
                    case "x" -> new ItemW(resolveSize(value, true), iw.y, iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "y" -> new ItemW(iw.x, resolveSize(value, false), iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "size", "w", "h" -> new ItemW(iw.x, iw.y, resolveSize(value, !"h".equals(field)), iw.itemId, iw.tooltip, iw.id);
                    case "item", "block" -> new ItemW(iw.x, iw.y, iw.size, value, iw.tooltip, iw.id);
                    case "tooltip" -> new ItemW(iw.x, iw.y, iw.size, iw.itemId, value, iw.id);
                    default -> w;
                };
            }
            if (w instanceof EntityW ew) {
                return switch (field) {
                    case "x" -> new EntityW(resolveSize(value, true), ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "y" -> new EntityW(ew.x, resolveSize(value, false), ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "w" -> new EntityW(ew.x, ew.y, resolveSize(value, true), ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "h" -> new EntityW(ew.x, ew.y, ew.w, resolveSize(value, false), ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "scale" -> new EntityW(ew.x, ew.y, ew.w, ew.h, Float.parseFloat(value.trim()), ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "feetCrop" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, Float.parseFloat(value.trim()), ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "anchorX" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, clamp01(Float.parseFloat(value.trim())), ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "anchorY" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, parseAnchorYPatch(value), ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                    case "crop" -> {
                        float[] c = parseCropPatch(value);
                        yield c != null ? new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender) : w;
                    }
                    case "viewport" -> {
                        float[] vp = parseViewportPatch(value);
                        if (vp != null) {
                            float[] c = viewportCornersToCrop(vp);
                            yield new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity, ew.alwaysRender);
                        }
                        yield w;
                    }
                    default -> w;
                };
            }
            if (w instanceof InputW inpw) {
                return switch (field) {
                    case "x" -> new InputW(inpw.id, resolveSize(value, true), inpw.y, inpw.w, inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type, inpw.style);
                    case "y" -> new InputW(inpw.id, inpw.x, resolveSize(value, false), inpw.w, inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type, inpw.style);
                    case "w" -> new InputW(inpw.id, inpw.x, inpw.y, resolveSize(value, true), inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type, inpw.style);
                    case "h" -> new InputW(inpw.id, inpw.x, inpw.y, inpw.w, resolveSize(value, false), inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type, inpw.style);
                    case "text" -> new InputW(inpw.id, inpw.x, inpw.y, inpw.w, inpw.h, value, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type, inpw.style);
                    default -> w;
                };
            }
            if (w instanceof ScrollW sw) {
                return switch (field) {
                    case "x" -> { sw.destroyFreeze(); ScrollW n = new ScrollW(resolveSize(value, true), sw.y, sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar, sw.style); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "y" -> { sw.destroyFreeze(); ScrollW n = new ScrollW(sw.x, resolveSize(value, false), sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar, sw.style); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "w" -> { sw.destroyFreeze(); ScrollW n = new ScrollW(sw.x, sw.y, resolveSize(value, true), sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar, sw.style); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "h" -> { sw.destroyFreeze(); ScrollW n = new ScrollW(sw.x, sw.y, sw.w, resolveSize(value, false), sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar, sw.style); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "scrolloffset", "offset", "scrolly" -> {
                        float off = Float.parseFloat(value.trim());
                        float maxScroll = Math.max(0, sw.contentH - sw.h);
                        sw.scrollOffset = Math.max(0, Math.min(off, maxScroll));
                        sw.invalidateFreeze();
                        yield sw;
                    }
                    default -> w;
                };
            }
            if (w instanceof ClipW cw) {
                return switch (field) {
                    case "x" -> cw.copyGeom(resolveSize(value, true), cw.y, cw.w, cw.h, cw.alpha);
                    case "y" -> cw.copyGeom(cw.x, resolveSize(value, false), cw.w, cw.h, cw.alpha);
                    case "w" -> cw.copyGeom(cw.x, cw.y, resolveSize(value, true), cw.h, cw.alpha);
                    case "h" -> cw.copyGeom(cw.x, cw.y, cw.w, resolveSize(value, false), cw.alpha);
                    case "alpha" -> cw.copyGeom(cw.x, cw.y, cw.w, cw.h, Float.parseFloat(value.trim()));
                    case "feather", "cropfeather", "crop_feather", "edgeblur", "edge_blur" ->
                            cw.copyFeather(Float.parseFloat(value.trim()), cw.featherL, cw.featherT, cw.featherR, cw.featherB, cw.featherSmooth);
                    case "featherl", "feather_l", "featherleft", "feather_left" ->
                            cw.copyFeather(cw.feather, Float.parseFloat(value.trim()), cw.featherT, cw.featherR, cw.featherB, cw.featherSmooth);
                    case "feathert", "feather_t", "feathertop", "feather_top" ->
                            cw.copyFeather(cw.feather, cw.featherL, Float.parseFloat(value.trim()), cw.featherR, cw.featherB, cw.featherSmooth);
                    case "featherr", "feather_r", "featherright", "feather_right" ->
                            cw.copyFeather(cw.feather, cw.featherL, cw.featherT, Float.parseFloat(value.trim()), cw.featherB, cw.featherSmooth);
                    case "featherb", "feather_b", "featherbottom", "feather_bottom" ->
                            cw.copyFeather(cw.feather, cw.featherL, cw.featherT, cw.featherR, Float.parseFloat(value.trim()), cw.featherSmooth);
                    case "feathersmooth", "feather_smooth" ->
                            cw.copyFeather(cw.feather, cw.featherL, cw.featherT, cw.featherR, cw.featherB, isTruthyPatch(value));
                    default -> w;
                };
            }
            if (w instanceof GroupW gw) {
                return switch (field) {
                    case "x" -> { GroupW n = new GroupW(resolveSize(value, true), gw.y, gw.w, gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "y" -> { GroupW n = new GroupW(gw.x, resolveSize(value, false), gw.w, gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "w" -> { GroupW n = new GroupW(gw.x, gw.y, resolveSize(value, true), gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "h" -> { GroupW n = new GroupW(gw.x, gw.y, gw.w, resolveSize(value, false), gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "alpha" -> { GroupW n = new GroupW(gw.x, gw.y, gw.w, gw.h, Float.parseFloat(value.trim()), gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    default -> w;
                };
            }
            if (w instanceof DragW dw) {
                return switch (field) {
                    case "x" -> { dw.x = resolveSize(value, true); yield dw; }
                    case "y" -> { dw.y = resolveSize(value, false); yield dw; }
                    case "w" -> { dw.w = resolveSize(value, true); yield dw; }
                    case "h" -> { dw.h = resolveSize(value, false); yield dw; }
                    case "alpha" -> { dw.alpha = Float.parseFloat(value.trim()); yield dw; }
                    case "enabled" -> { dw.enabled = isTruthyPatch(value); yield dw; }
                    case "axis" -> { dw.axis = value == null ? "xy" : value.trim().toLowerCase(Locale.ROOT); yield dw; }
                    case "group" -> { dw.group = value == null ? "" : value; yield dw; }
                    case "payload" -> { dw.payload = value == null ? "" : value; yield dw; }
                    case "constrain" -> { dw.constrain = isTruthyPatch(value); yield dw; }
                    case "revert" -> { dw.revert = isTruthyPatch(value); yield dw; }
                    case "threshold" -> { dw.threshold = Math.max(0, (int) Float.parseFloat(value.trim())); yield dw; }
                    default -> w;
                };
            }
            if (w instanceof DropW drop) {
                return switch (field) {
                    case "x" -> { drop.x = resolveSize(value, true); yield drop; }
                    case "y" -> { drop.y = resolveSize(value, false); yield drop; }
                    case "w" -> { drop.w = resolveSize(value, true); yield drop; }
                    case "h" -> { drop.h = resolveSize(value, false); yield drop; }
                    case "alpha" -> { drop.alpha = Float.parseFloat(value.trim()); yield drop; }
                    case "enabled" -> { drop.enabled = isTruthyPatch(value); yield drop; }
                    case "group" -> { drop.group = value == null ? "" : value; yield drop; }
                    default -> w;
                };
            }
        } catch (Exception ignored) {}
        return w;
    }

    /** 1.19.2 Level has no {@code getEntity(UUID)} — resolve by scan near camera. */
    private static Entity findEntityByUuid(net.minecraft.world.level.Level level, UUID uuid) {
        if (level == null || uuid == null) return null;
        net.minecraft.world.entity.player.Player pl = level.getPlayerByUUID(uuid);
        if (pl != null) return pl;
        Minecraft mc = Minecraft.getInstance();
        Entity ref = mc.player != null ? mc.player : mc.cameraEntity;
        if (ref == null) return null;
        var list = level.getEntities(ref, ref.getBoundingBox().inflate(256.0), e -> e.getUUID().equals(uuid));
        return list.isEmpty() ? null : list.get(0);
    }

    private void parseWidgets() {
        if (!root.has("widgets") || !root.get("widgets").isJsonArray()) return;
        widgets.addAll(parseSortedChildren(root.getAsJsonArray("widgets"), panelW, panelH));
    }

    private List<Widget> parseSortedChildren(JsonArray arr, int pw, int ph) {
        return parseSortedChildren(arr, pw, ph, "top_left");
    }

    private List<Widget> parseSortedChildren(JsonArray arr, int pw, int ph, String childAlign) {
        List<WidgetEntry> entries = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject w = el.getAsJsonObject();
            int layer = w.has("layer") ? w.get("layer").getAsInt() : 0;
            int order = w.has("order") ? w.get("order").getAsInt() : 0;
            String tooltip = w.has("tooltip") ? w.get("tooltip").getAsString() : null;
            if (tooltip != null && tooltip.isEmpty()) tooltip = null;
            Widget built = parseOneWidget(w, tooltip, pw, ph, childAlign);
            if (built != null) {
                entries.add(new WidgetEntry(layer, order, built));
            }
        }
        entries.sort(Comparator.comparingInt((WidgetEntry e) -> e.layer).thenComparingInt(e -> e.order));
        List<Widget> out = new ArrayList<>(entries.size());
        for (WidgetEntry e : entries) out.add(e.widget);
        return out;
    }

    private static String parseWrapMode(JsonObject w) {
        if (w == null) return "word";
        String raw = null;
        if (w.has("wrapMode")) raw = w.get("wrapMode").getAsString();
        else if (w.has("wrap_mode")) raw = w.get("wrap_mode").getAsString();
        if (raw == null || raw.isBlank()) return "word";
        String m = raw.trim().toLowerCase(Locale.ROOT);
        if ("char".equals(m) || "letter".equals(m) || "by_char".equals(m) || "bychar".equals(m) || "chars".equals(m)) {
            return "char";
        }
        return "word";
    }

    /** Word wrap via Minecraft font, or hard wrap by characters (markup tags are not measured). */
    private static List<net.minecraft.util.FormattedCharSequence> wrapUiText(
            net.minecraft.client.gui.Font font, String text, int maxWidth, String mode) {
        return wrapUiText(font, text, maxWidth, mode, UiTextStyle.NONE);
    }

    private static List<net.minecraft.util.FormattedCharSequence> wrapUiText(
            net.minecraft.client.gui.Font font, String text, int maxWidth, String mode, UiTextStyle style) {
        if (text == null || text.isEmpty()) return List.of();
        net.minecraft.network.chat.MutableComponent comp = style.component(text);
        if (maxWidth <= 0) {
            return List.of(comp.getVisualOrderText());
        }
        if ("char".equalsIgnoreCase(mode)) {
            return wrapUiTextByChar(font, comp, maxWidth);
        }
        return font.split(comp, maxWidth);
    }

    private static List<net.minecraft.util.FormattedCharSequence> wrapUiTextByChar(
            net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int maxWidth) {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        net.minecraft.network.chat.MutableComponent[] line = {net.minecraft.network.chat.Component.empty()};
        int[] lineW = {0};
        text.getVisualOrderText().accept((index, style, codePoint) -> {
            if (codePoint == '\n') {
                out.add(line[0].getVisualOrderText());
                line[0] = net.minecraft.network.chat.Component.empty();
                lineW[0] = 0;
                return true;
            }
            String ch = new String(Character.toChars(codePoint));
            net.minecraft.network.chat.MutableComponent piece =
                    net.minecraft.network.chat.Component.literal(ch).withStyle(style);
            int w = font.width(piece);
            if (lineW[0] > 0 && lineW[0] + w > maxWidth) {
                out.add(line[0].getVisualOrderText());
                line[0] = net.minecraft.network.chat.Component.empty();
                lineW[0] = 0;
            }
            line[0].append(piece);
            lineW[0] += w;
            return true;
        });
        out.add(line[0].getVisualOrderText());
        return out;
    }

    private static float[] parseAnchor(JsonObject w) {
        float ax = 0f, ay = 0f;
        if (w.has("anchorX")) ax = w.get("anchorX").getAsFloat();
        if (w.has("anchorY")) ay = w.get("anchorY").getAsFloat();
        if (w.has("anchor")) {
            JsonElement el = w.get("anchor");
            if (el.isJsonPrimitive()) {
                String a = el.getAsString().toLowerCase();
                if (a.contains("right")) ax = 1f;
                else if (a.contains("center") || a.contains("middle")) ax = 0.5f;
                if (a.contains("bottom")) ay = 1f;
                else if (a.contains("middle") || a.contains("center")) ay = 0.5f;
                if (a.equals("center")) { ax = 0.5f; ay = 0.5f; }
            } else if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() > 0) ax = arr.get(0).getAsFloat();
                if (arr.size() > 1) ay = arr.get(1).getAsFloat();
            }
        }
        return new float[]{ax, ay};
    }

    private static float[] parsePivot(JsonObject w) {
        float px = 0.5f, py = 0.5f;
        if (w.has("pivotX")) px = w.get("pivotX").getAsFloat();
        if (w.has("pivotY")) py = w.get("pivotY").getAsFloat();
        if (w.has("pivot")) {
            JsonElement el = w.get("pivot");
            if (el.isJsonPrimitive()) {
                String a = el.getAsString().toLowerCase();
                if (a.contains("right")) px = 1f;
                else if (a.contains("center") || a.contains("middle")) px = 0.5f;
                else if (a.contains("left")) px = 0f;
                
                if (a.contains("bottom")) py = 1f;
                else if (a.contains("center") || a.contains("middle")) py = 0.5f;
                else if (a.contains("top")) py = 0f;
                
                if (a.equals("center")) { px = 0.5f; py = 0.5f; }
            } else if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() > 0) px = arr.get(0).getAsFloat();
                if (arr.size() > 1) py = arr.get(1).getAsFloat();
            }
        }
        return new float[]{px, py};
    }

    private UiTextStyle textStyle(String widgetId) {
        return UiTextStyle.fromJson(widgetTextStyles.get(widgetId));
    }

    private static boolean isTextStyleField(String field) {
        if (field == null) return false;
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "effects", "texteffects", "text_effects", "bold", "italic", "underline",
                    "strikethrough", "strike", "code", "shadow", "outline", "glow",
                    "rainbow", "pulse", "wave", "shake", "wiggle", "textgradient",
                    "text_gradient", "outlinecolor", "outline_color", "textoutlinecolor",
                    "glowcolor", "glow_color", "textglowcolor", "effectspeed", "effect_speed",
                    "texteffectspeed", "effectamount", "effect_amount", "texteffectamount",
                    "textglowsize", "text_glow_size", "glowsize", "glow_size",
                    "textoutlinewidth", "text_outline_width", "outlinewidth", "outline_width",
                    "textunderlinecolor", "text_underline_color", "underlinecolor", "underline_color",
                    "textunderlinethickness", "text_underline_thickness", "underlinethickness", "underline_thickness",
                    "textunderlineoffset", "text_underline_offset", "underlineoffset", "underline_offset",
                    "textshadowcolor", "text_shadow_color", "shadowcolor", "shadow_color",
                    "textshadowoffsetx", "text_shadow_offset_x", "shadowoffsetx", "shadow_offset_x",
                    "textshadowoffsety", "text_shadow_offset_y", "shadowoffsety", "shadow_offset_y",
                    "textshadowsize", "text_shadow_size",
                    "glow_pixel", "glow_soft", "outline_pixel", "outline_soft",
                    "shadow_pixel", "shadow_soft" -> true;
            default -> false;
        };
    }

    private void patchTextStyle(String widgetId, String field, String value) {
        if (widgetId == null || widgetId.isBlank()) return;
        JsonObject style = widgetTextStyles.computeIfAbsent(widgetId, ignored -> new JsonObject());
        String key = switch (field.toLowerCase(Locale.ROOT)) {
            case "texteffects", "text_effects" -> "effects";
            case "textgradient", "text_gradient" -> "textGradient";
            case "outlinecolor", "outline_color", "textoutlinecolor" -> "textOutlineColor";
            case "glowcolor", "glow_color", "textglowcolor" -> "textGlowColor";
            case "effectspeed", "effect_speed", "texteffectspeed" -> "textEffectSpeed";
            case "effectamount", "effect_amount", "texteffectamount" -> "textEffectAmount";
            case "textglowsize", "text_glow_size", "glowsize", "glow_size" -> "textGlowSize";
            case "textoutlinewidth", "text_outline_width", "outlinewidth", "outline_width" -> "textOutlineWidth";
            case "textunderlinecolor", "text_underline_color", "underlinecolor", "underline_color" -> "textUnderlineColor";
            case "textunderlinethickness", "text_underline_thickness", "underlinethickness", "underline_thickness" -> "textUnderlineThickness";
            case "textunderlineoffset", "text_underline_offset", "underlineoffset", "underline_offset" -> "textUnderlineOffset";
            case "textshadowcolor", "text_shadow_color", "shadowcolor", "shadow_color" -> "textShadowColor";
            case "textshadowoffsetx", "text_shadow_offset_x", "shadowoffsetx", "shadow_offset_x" -> "textShadowOffsetX";
            case "textshadowoffsety", "text_shadow_offset_y", "shadowoffsety", "shadow_offset_y" -> "textShadowOffsetY";
            case "textshadowsize", "text_shadow_size" -> "textShadowSize";
            default -> field.toLowerCase(Locale.ROOT);
        };
        if ("effects".equals(key)) {
            for (String flag : List.of("bold", "italic", "underline", "strikethrough", "strike",
                    "code", "shadow", "outline", "glow", "rainbow", "pulse", "wave", "shake", "wiggle",
                    "glow_pixel", "glow_soft", "outline_pixel", "outline_soft", "shadow_pixel", "shadow_soft")) {
                style.remove(flag);
            }
        }
        if (switch (key) {
            case "bold", "italic", "underline", "strikethrough", "strike", "code", "shadow",
                    "outline", "glow", "rainbow", "pulse", "wave", "shake", "wiggle",
                    "glow_pixel", "glow_soft", "outline_pixel", "outline_soft",
                    "shadow_pixel", "shadow_soft" -> true;
            default -> false;
        }) {
            style.addProperty(key, Boolean.parseBoolean(value.trim()));
        } else if ("textEffectSpeed".equals(key) || "textEffectAmount".equals(key)
                || "textGlowSize".equals(key) || "textOutlineWidth".equals(key)
                || "textUnderlineThickness".equals(key) || "textUnderlineOffset".equals(key)
                || "textShadowOffsetX".equals(key) || "textShadowOffsetY".equals(key)
                || "textShadowSize".equals(key)) {
            try { style.addProperty(key, Float.parseFloat(value.trim())); } catch (NumberFormatException ignored) {}
        } else {
            style.addProperty(key, value);
        }
    }

    private static String readChildAlign(JsonObject w) {
        if (w.has("childAlign")) return w.get("childAlign").getAsString();
        if (w.has("child_align")) return w.get("child_align").getAsString();
        return "top_left";
    }

    private Widget parseOneWidget(JsonObject w, String tooltip, int pw, int ph) {
        return parseOneWidget(w, tooltip, pw, ph, "top_left");
    }

    private Widget parseOneWidget(JsonObject w, String tooltip, int pw, int ph, String childAlign) {
        String type = w.has("type") ? w.get("type").getAsString().toLowerCase() : "";
        int x = readCoord(w, "x", pw);
        int y = readCoord(w, "y", ph);
        int ww = readCoord(w, "w", pw);
        int hh = readCoord(w, "h", ph);
        if (ww <= 0 && w.has("size")) ww = readCoord(w, "size", pw);
        if (hh <= 0 && w.has("size")) hh = ww;
        if (!UiChildAlign.isOrigin(childAlign)) {
            x = Math.round(UiChildAlign.axisX(childAlign) * pw + x - UiChildAlign.axisX(childAlign) * ww);
            y = Math.round(UiChildAlign.axisY(childAlign) * ph + y - UiChildAlign.axisY(childAlign) * hh);
        }
        String wid = w.has("id") ? w.get("id").getAsString() : "";
        if ("text".equals(type) || "button".equals(type) || "input".equals(type)) {
            widgetTextStyles.put(wid, w.deepCopy());
        }
        
        float rotation = w.has("rotation") ? w.get("rotation").getAsFloat() : 0f;
        float[] pivot = parsePivot(w);
        
        Widget parsed = switch (type) {
            case "rect", "panel" -> {
                RectW rect = new RectW(x, y, ww, hh, parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFFFF"), tooltip, wid, parseStyle(w), parseFill(w), parseFillAmount(w), parseFillOrigin(w), parseCropShape(w));
                if (w.has("children") && w.get("children").isJsonArray()) {
                    rect.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, hh, readChildAlign(w)));
                }
                yield rect;
            }
            case "gridBg" -> new GridBgW(x, y, ww, hh, 
                    w.has("gridType") ? w.get("gridType").getAsString() : "hv",
                    w.has("cellSize") ? w.get("cellSize").getAsInt() : 20,
                    w.has("thickness") ? w.get("thickness").getAsInt() : 1,
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), tooltip, wid);
            case "image" -> {
                int sliceBorders = w.has("slice_borders") ? w.get("slice_borders").getAsInt() : 0;
                float sliceScale = w.has("slice_scale") ? w.get("slice_scale").getAsFloat() : 1f;
                int[] src = parseImageSrc(w);
                yield new ImageW(x, y, ww, hh, w.has("texture") ? w.get("texture").getAsString() : "minecraft:textures/misc/unknown_pack.png", tooltip, wid, sliceBorders, sliceScale, src[0], src[1], src[2], src[3], parseFill(w), parseFillAmount(w), parseFillOrigin(w), parseCropShape(w));
            }
            case "svg" -> {
                String source = w.has("source") ? w.get("source").getAsString()
                        : (w.has("svg") ? w.get("svg").getAsString()
                        : (w.has("texture") ? w.get("texture").getAsString() : ""));
                String color = w.has("color") ? w.get("color").getAsString() : "#FFFFFFFF";
                String stroke = w.has("stroke") ? w.get("stroke").getAsString() : "";
                float strokeWidth = 0f;
                if (w.has("strokeWidth")) strokeWidth = w.get("strokeWidth").getAsFloat();
                else if (w.has("stroke_width")) strokeWidth = w.get("stroke_width").getAsFloat();
                boolean recolor = false;
                if (w.has("recolor")) {
                    var el = w.get("recolor");
                    if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) recolor = el.getAsBoolean();
                    else recolor = parseSvgFlag(el.getAsString());
                }
                yield new SvgW(x, y, ww, hh, source, color, stroke, strokeWidth, recolor, tooltip, wid,
                        parseFill(w), parseFillAmount(w), parseFillOrigin(w), parseCropShape(w));
            }
            case "video" -> {
                String source = w.has("source") ? w.get("source").getAsString() : "";
                boolean playerEnabled = !w.has("playerEnabled") || w.get("playerEnabled").getAsBoolean();
                boolean playing = !w.has("playing") || w.get("playing").getAsBoolean();
                boolean loop = !w.has("loop") || w.get("loop").getAsBoolean();
                UiVideoPlayer player = videoPlayers.computeIfAbsent(wid,
                        ignored -> new UiVideoPlayer(source, playerEnabled, playing, loop));
                player.configure(source, playerEnabled, playing, loop);
                yield new VideoW(x, y, ww, hh, source, playerEnabled, playing, loop, tooltip, wid, player);
            }
            case "text" -> {
                float[] anchors = parseAnchor(w);
                int textOx = w.has("textOffsetX") ? w.get("textOffsetX").getAsInt()
                        : (w.has("text_offset_x") ? w.get("text_offset_x").getAsInt() : 0);
                int textOy = w.has("textOffsetY") ? w.get("textOffsetY").getAsInt()
                        : (w.has("text_offset_y") ? w.get("text_offset_y").getAsInt() : 0);
                yield new TextW(x, y, w.has("text") ? w.get("text").getAsString() : "", parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFF"), w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip, wid,
                        w.has("wrap") ? readCoord(w, "wrap", pw) : 0,
                        parseWrapMode(w),
                        w.has("align") ? w.get("align").getAsString().toLowerCase() : "left",
                        w.has("maxLines") ? w.get("maxLines").getAsInt() : 0,
                        w.has("maxChars") ? w.get("maxChars").getAsInt() : 0,
                        anchors[0], anchors[1], textOx, textOy);
            }
            case "button" -> {
                List<Widget> hoverChildren = new ArrayList<>();
                int hpw = 0;
                int hph = 0;
                if (w.has("hover_panel") && w.get("hover_panel").isJsonObject()) {
                    JsonObject hp = w.getAsJsonObject("hover_panel");
                    hpw = hp.has("w") ? hp.get("w").getAsInt() : 210;
                    hph = hp.has("h") ? hp.get("h").getAsInt() : 96;
                    if (hp.has("children") && hp.get("children").isJsonArray()) {
                        hoverChildren.addAll(parseSortedChildren(hp.getAsJsonArray("children"), hpw, hph));
                    }
                }
                int labelOx = w.has("labelOffsetX") ? w.get("labelOffsetX").getAsInt()
                        : (w.has("label_offset_x") ? w.get("label_offset_x").getAsInt()
                        : (w.has("textOffsetX") ? w.get("textOffsetX").getAsInt() : 0));
                int labelOy = w.has("labelOffsetY") ? w.get("labelOffsetY").getAsInt()
                        : (w.has("label_offset_y") ? w.get("label_offset_y").getAsInt()
                        : (w.has("textOffsetY") ? w.get("textOffsetY").getAsInt() : 0));
                String labelAlign = w.has("align") ? w.get("align").getAsString()
                        : (w.has("labelAlign") ? w.get("labelAlign").getAsString() : "center");
                yield new ButtonW(
                    w.has("id") ? w.get("id").getAsString() : "",
                    x, y, ww, hh,
                    w.has("label") ? w.get("label").getAsString() : "",
                    w.has("subLabel") ? w.get("subLabel").getAsString() : "",
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#55336688"),
                    parseColor(w.has("hover") ? w.get("hover").getAsString() : "#66447799"),
                    w.has("texture") ? w.get("texture").getAsString() : null,
                    tooltip,
                    w.has("labelWrap") ? readCoord(w, "labelWrap", pw) : 0,
                    parseWrapMode(w),
                    w.has("labelScale") ? w.get("labelScale").getAsFloat() : 1f,
                    w.has("subScale") ? w.get("subScale").getAsFloat() : 0.65f,
                    w.has("slice_borders") ? w.get("slice_borders").getAsInt() : 0,
                    w.has("slice_scale") ? w.get("slice_scale").getAsFloat() : 1f,
                    labelOx, labelOy, labelAlign,
                    hoverChildren, hpw, hph, parseStyle(w));
            }
            case "entity" -> {
                UUID uuid = null;
                // Server sends field "entity" (UUID string or "npc:name"), also check legacy "entityUuid"
                String _entityRef = null;
                if (w.has("entityUuid")) {
                    _entityRef = w.get("entityUuid").getAsString();
                } else if (w.has("entity")) {
                    _entityRef = w.get("entity").getAsString();
                }
                if (_entityRef != null) {
                    try { uuid = UUID.fromString(_entityRef); } catch (Exception ignored) {}
                }
                LOGGER.info("[CHAT-CLIENT] entity parse: ref='{}' uuid={}", _entityRef, uuid);
                float scale = w.has("scale") ? w.get("scale").getAsFloat() : 1f;
                float feetCrop = w.has("feetCrop") ? w.get("feetCrop").getAsFloat() : 0.38f;
                float[] crop = new float[]{0f, 0f, 0f, 0f};
                if (w.has("crop")) {
                    JsonArray c = w.getAsJsonArray("crop");
                    if (c.size() >= 4) {
                        crop[0] = clamp01(c.get(0).getAsFloat());
                        crop[1] = clamp01(c.get(1).getAsFloat());
                        crop[2] = clamp01(c.get(2).getAsFloat());
                        crop[3] = clamp01(c.get(3).getAsFloat());
                        normalizeCrop(crop);
                    }
                } else if (w.has("viewport")) {
                    JsonArray va = w.getAsJsonArray("viewport");
                    if (va.size() >= 4) {
                        float[] vp = new float[]{
                                clamp01(va.get(0).getAsFloat()),
                                clamp01(va.get(1).getAsFloat()),
                                clamp01(va.get(2).getAsFloat()),
                                clamp01(va.get(3).getAsFloat())
                        };
                        normalizeViewportCorners(vp);
                        float[] conv = viewportCornersToCrop(vp);
                        System.arraycopy(conv, 0, crop, 0, 4);
                    }
                }
                float anchorX = 0.5f;
                float anchorY = -1f;
                if (w.has("anchor")) {
                    JsonArray a = w.getAsJsonArray("anchor");
                    if (a.size() >= 2) {
                        anchorX = clamp01(a.get(0).getAsFloat());
                        anchorY = a.get(1).getAsFloat(); // unrestricted: <0 = above box, >1 = below box
                    }
                } else {
                    if (w.has("anchorX")) anchorX = clamp01(w.get("anchorX").getAsFloat());
                    if (w.has("anchorY")) {
                        anchorY = w.get("anchorY").getAsFloat(); // unrestricted
                    }
                }
                boolean disableAnim = false;
                if (w.has("animation")) {
                    JsonElement animEl = w.get("animation");
                    if (animEl.isJsonPrimitive()) {
                        if (animEl.getAsJsonPrimitive().isBoolean()) disableAnim = !animEl.getAsBoolean();
                        else if (animEl.getAsString().equalsIgnoreCase("false")) disableAnim = true;
                    }
                }
                boolean hideNameTag = !w.has("nameTag") || !w.get("nameTag").getAsBoolean();
                boolean noLookAt = w.has("noLookAt") && w.get("noLookAt").getAsBoolean();
                boolean noFollowCursor = w.has("noFollowCursor") && w.get("noFollowCursor").getAsBoolean();
                boolean noHurtAnim = w.has("noHurtAnim") && w.get("noHurtAnim").getAsBoolean();
                String[] renderBones = null;
                if (w.has("renderBones")) {
                    JsonElement rbEl = w.get("renderBones");
                    if (rbEl.isJsonArray()) {
                        JsonArray rba = rbEl.getAsJsonArray();
                        renderBones = new String[rba.size()];
                        for (int i = 0; i < rba.size(); i++) renderBones[i] = rba.get(i).getAsString();
                    } else if (rbEl.isJsonPrimitive()) {
                        renderBones = new String[]{rbEl.getAsString()};
                    }
                }
                UUID skinPlayerUuid = null;
                if (w.has("skinPlayerUuid")) {
                    try {
                        skinPlayerUuid = UUID.fromString(w.get("skinPlayerUuid").getAsString());
                    } catch (IllegalArgumentException ignored) {}
                }
                String modelGeo = w.has("modelGeo") ? w.get("modelGeo").getAsString() : null;
                String modelTexture = w.has("modelTexture") ? w.get("modelTexture").getAsString() : null;
                String modelAnim = w.has("modelAnim") ? w.get("modelAnim").getAsString() : null;
                String modelIdle = w.has("modelIdle") ? w.get("modelIdle").getAsString() : null;
                // Special refs: "player" = the viewing player, "mob:<id>" = vanilla mob
                // dummy. Piggyback on modelGeo so the widget record stays unchanged.
                if ((modelGeo == null || modelGeo.isEmpty()) && _entityRef != null && uuid == null) {
                    if ("player".equalsIgnoreCase(_entityRef.trim())) modelGeo = "player:";
                    else if (_entityRef.startsWith("mob:")) modelGeo = _entityRef;
                }
                if ((modelGeo == null || modelGeo.isEmpty()) && w.has("entity")) {
                    String entRef = w.get("entity").getAsString();
                    if (entRef.startsWith("model:")) {
                        String[] parts = entRef.substring(6).split("\\|", -1);
                        if (parts.length > 0 && !parts[0].isEmpty()) modelGeo = parts[0];
                        if (parts.length > 1 && !parts[1].isEmpty()) modelTexture = parts[1];
                        if (parts.length > 2 && !parts[2].isEmpty()) modelAnim = parts[2];
                        if (parts.length > 3 && !parts[3].isEmpty()) modelIdle = parts[3];
                    }
                }
                boolean clipEntity = w.has("clipEntity") && w.get("clipEntity").getAsBoolean();
                boolean autoScale = w.has("autoScale") && w.get("autoScale").getAsBoolean();
                // Default ON: keep drawing from modelGeo when the live NPC is missing or dead.
                boolean alwaysRender = true;
                if (w.has("alwaysRender")) alwaysRender = w.get("alwaysRender").getAsBoolean();
                else if (w.has("always_render")) alwaysRender = w.get("always_render").getAsBoolean();
                yield new EntityW(x, y, ww, hh, scale, uuid, feetCrop, tooltip, wid, crop[0], crop[1], crop[2], crop[3], anchorX, anchorY, disableAnim, hideNameTag, noLookAt, noFollowCursor, noHurtAnim, renderBones, skinPlayerUuid, modelGeo, modelTexture, modelAnim, modelIdle, autoScale, clipEntity, alwaysRender);
            }
            case "scroll" -> {
                int contentH = readCoord(w, "contentH", ph);
                int scrollBg = parseColor(w.has("color") ? w.get("color").getAsString() : "#00000000");
                boolean autoBar = w.has("autoScrollbar") && w.get("autoScrollbar").getAsBoolean();
                boolean showBar = autoBar || (!w.has("scrollbar") || w.get("scrollbar").getAsBoolean());
                ScrollW scroll = new ScrollW(x, y, ww, hh, contentH, scrollBg, tooltip, wid, showBar, autoBar, parseStyle(w));
                if (w.has("children") && w.get("children").isJsonArray()) {
                    scroll.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, contentH, readChildAlign(w)));
                }
                yield scroll;
            }
            case "clip" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                float feather = jsonFloat(w, 0f, "feather", "cropFeather", "crop_feather", "edgeBlur", "edge_blur");
                float featherL = jsonFloat(w, 1f, "featherL", "featherLeft", "feather_l", "feather_left");
                float featherT = jsonFloat(w, 1f, "featherT", "featherTop", "feather_t", "feather_top");
                float featherR = jsonFloat(w, 1f, "featherR", "featherRight", "feather_r", "feather_right");
                float featherB = jsonFloat(w, 1f, "featherB", "featherBottom", "feather_b", "feather_bottom");
                boolean featherSmooth = jsonBool(w, true, "featherSmooth", "feather_smooth");
                ClipW clip = new ClipW(x, y, ww, hh, alpha, tooltip, wid,
                        feather, featherL, featherT, featherR, featherB, featherSmooth);
                if (w.has("children") && w.get("children").isJsonArray()) {
                    clip.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, hh, readChildAlign(w)));
                }
                yield clip;
            }
            case "group" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                GroupW group = new GroupW(x, y, ww, hh, alpha, tooltip, wid);
                if (w.has("children") && w.get("children").isJsonArray()) {
                    group.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, hh, readChildAlign(w)));
                }
                yield group;
            }
            case "drag" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                boolean enabled = jsonBool(w, true, "enabled");
                String axis = w.has("axis") ? w.get("axis").getAsString() : "xy";
                String group = w.has("group") ? w.get("group").getAsString() : "";
                String payload = w.has("payload") ? w.get("payload").getAsString() : "";
                boolean constrain = jsonBool(w, true, "constrain");
                boolean revert = jsonBool(w, true, "revert");
                int threshold = w.has("threshold") ? Math.max(0, w.get("threshold").getAsInt()) : 4;
                DragW drag = new DragW(x, y, ww, hh, alpha, tooltip, wid, enabled, axis, group, payload,
                        constrain, revert, threshold, pw, ph);
                if (w.has("children") && w.get("children").isJsonArray()) {
                    drag.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, hh, readChildAlign(w)));
                }
                yield drag;
            }
            case "drop", "droptarget", "drop_target" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                boolean enabled = jsonBool(w, true, "enabled");
                String group = w.has("group") ? w.get("group").getAsString() : "";
                DropW drop = new DropW(x, y, ww, hh, alpha, tooltip, wid, enabled, group);
                if (w.has("children") && w.get("children").isJsonArray()) {
                    drop.children.addAll(parseSortedChildren(w.getAsJsonArray("children"), ww, hh, readChildAlign(w)));
                }
                yield drop;
            }
            case "input" -> new InputW(wid, x, y, ww, hh,
                    w.has("text") ? w.get("text").getAsString() : "",
                    w.has("placeholder") ? w.get("placeholder").getAsString() : "",
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFF"),
                    parseColor(w.has("bgColor") ? w.get("bgColor").getAsString() : "#FF000000"),
                    parseColor(w.has("outlineColor") ? w.get("outlineColor").getAsString() : "#FFAAAAAA"),
                    w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip,
                    w.has("maxChars") ? w.get("maxChars").getAsInt() : 32,
                    w.has("inputType") ? w.get("inputType").getAsString().toLowerCase() : "text",
                    parseStyle(w));
            case "divider" -> new DividerW(x, y, ww, parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), wid);
            case "slot" -> new SlotW(x, y, wid);
            case "playerinventory", "player_inventory" -> new PlayerInvW(x, y, wid);
            case "item", "block" -> {
                String itemId = "minecraft:stone";
                if (w.has("item")) itemId = w.get("item").getAsString();
                else if (w.has("block")) itemId = w.get("block").getAsString();
                int itemSize = w.has("size") ? readCoord(w, "size", pw)
                        : (w.has("w") ? readCoord(w, "w", pw) : (ww > 0 ? ww : 16));
                yield new ItemW(x, y, itemSize, itemId, tooltip, wid);
            }
            default -> null;
        };
        
        // A present "rotation" property (even 0) always creates the wrapper, so
        // node animators can rotate the widget later.
        if (parsed != null && (rotation != 0f || w.has("rotation"))) {
            float ox = w.has("w") || w.has("size") ? readCoord(w, w.has("size") ? "size" : "w", pw) : 0f;
            float oy = w.has("h") || w.has("size") ? readCoord(w, w.has("size") ? "size" : "h", ph) : 0f;
            if (ox == 0f && parsed instanceof TextW tw) {
                // text width fallback
                ox = 50f; // approximated for anchor
                oy = 10f;
            } else if (ox == 0f) {
                ox = 50f;
                oy = 50f;
            }
            parsed = new RotatedW(parsed, rotation, pivot[0], pivot[1], ox, oy, wid);
        }
        
        return parsed;
    }

    private record RotatedW(Widget child, float rotation, float pivotX, float pivotY, float ow, float oh, String id) implements Widget {
        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            float px = ax0 + child.getX() + ow * pivotX;
            float py = ay0 + child.getY() + oh * pivotY;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(px, py, 0);
            SprauteRenderCompat.rotateZ(guiGraphics.pose(), rotation);
            guiGraphics.pose().translate(-px, -py, 0);

            double angle = Math.toRadians(-rotation);
            float dx = mouseX - px;
            float dy = mouseY - py;
            int localMouseX = (int) (px + dx * Math.cos(angle) - dy * Math.sin(angle));
            int localMouseY = (int) (py + dx * Math.sin(angle) + dy * Math.cos(angle));

            child.render(screen, guiGraphics, ax0, ay0, localMouseX, localMouseY, partialTick);
            guiGraphics.pose().popPose();
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            float px = ax0 + child.getX() + ow * pivotX;
            float py = ay0 + child.getY() + oh * pivotY;
            poseStack.pushPose();
            poseStack.translate(px, py, 0);
            poseStack.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(rotation));
            poseStack.translate(-px, -py, 0);

            double angle = Math.toRadians(-rotation);
            float dx = mouseX - px;
            float dy = mouseY - py;
            int localMouseX = (int) (px + dx * Math.cos(angle) - dy * Math.sin(angle));
            int localMouseY = (int) (py + dx * Math.sin(angle) + dy * Math.cos(angle));

            child.render(screen, poseStack, ax0, ay0, localMouseX, localMouseY, partialTick);
            poseStack.popPose();
        }
        *///?}

        @Override
        public String tooltip() {
            return child.tooltip();
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            float px = ax0 + child.getX() + ow * pivotX;
            float py = ay0 + child.getY() + oh * pivotY;
            double angle = Math.toRadians(-rotation);
            float dx = mx - px;
            float dy = my - py;
            int localMouseX = (int) (px + dx * Math.cos(angle) - dy * Math.sin(angle));
            int localMouseY = (int) (py + dx * Math.sin(angle) + dy * Math.cos(angle));
            return child.contains(screen, ax0, ay0, localMouseX, localMouseY);
        }
        
        @Override
        public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) {
            float[] childObb = child.getOBB(screen, ax0, ay0);
            if (childObb == null) return null;
            float px = ax0 + child.getX() + ow * pivotX;
            float py = ay0 + child.getY() + oh * pivotY;
            float cos = (float) Math.cos(Math.toRadians(rotation));
            float sin = (float) Math.sin(Math.toRadians(rotation));
            for (int i=0; i<8; i+=2) {
                float cx = childObb[i] - px;
                float cy = childObb[i+1] - py;
                childObb[i] = px + cx*cos - cy*sin;
                childObb[i+1] = py + cx*sin + cy*cos;
            }
            return childObb;
        }
        
        @Override public int getX() { return child.getX(); }
        @Override public int getY() { return child.getY(); }
        @Override public String getId() { return id; }
    }

    private record WidgetEntry(int layer, int order, Widget widget) {}

    //? if >=1.20.1 {
    private void renderButtonHoverPanel(GuiGraphics guiGraphics, ButtonW bw, int mouseX, int mouseY) {
        int hw = bw.hoverPw() > 0 ? bw.hoverPw() : 210;
        int hh = bw.hoverPh() > 0 ? bw.hoverPh() : 96;
        int px = mouseX + 10;
        int py = mouseY + 10;
        int sw = width;
        int sh = height;
        if (px + hw > sw - 4) px = mouseX - hw - 10;
        if (py + hh > sh - 4) py = mouseY - hh - 10;
        px = Math.max(4, px);
        py = Math.max(4, py);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500);
        SprauteGuiDraw.enableScissor(guiGraphics, px, py, px + hw, py + hh);
        SprauteGuiDraw.fill(guiGraphics, px - 1, py - 1, px + hw + 1, py + hh + 1, 0xFF5566AA);
        SprauteGuiDraw.fill(guiGraphics, px, py, px + hw, py + hh, 0xFF0D0D14);
        SprauteGuiDraw.fill(guiGraphics, px, py, px + hw, py + hh, 0xFF1A1A2E);
        for (Widget cw : bw.hoverChildren()) {
            if (cw instanceof RectW) continue;
            cw.render(this, guiGraphics, px, py, mouseX, mouseY, 0f);
        }
        SprauteGuiDraw.disableScissor(guiGraphics);
        guiGraphics.pose().popPose();
    }
    //?} else {
    /*private void renderButtonHoverPanel(PoseStack poseStack, ButtonW bw, int mouseX, int mouseY) {
        int hw = bw.hoverPw() > 0 ? bw.hoverPw() : 210;
        int hh = bw.hoverPh() > 0 ? bw.hoverPh() : 96;
        int px = mouseX + 10;
        int py = mouseY + 10;
        int sw = width;
        int sh = height;
        if (px + hw > sw - 4) px = mouseX - hw - 10;
        if (py + hh > sh - 4) py = mouseY - hh - 10;
        px = Math.max(4, px);
        py = Math.max(4, py);
        poseStack.pushPose();
        poseStack.translate(0, 0, 500);
        GuiComponent.fill(poseStack, px - 1, py - 1, px + hw + 1, py + hh + 1, 0xFF5566AA);
        GuiComponent.fill(poseStack, px, py, px + hw, py + hh, 0xFF0D0D14);
        GuiComponent.fill(poseStack, px, py, px + hw, py + hh, 0xFF1A1A2E);
        for (Widget cw : bw.hoverChildren()) {
            if (cw instanceof RectW) continue;
            cw.render(this, poseStack, px, py, mouseX, mouseY, 0f);
        }
        poseStack.popPose();
    }
    *///?}

    /** Pixel, integer, or {@code "25%"} relative to panel width/height. */
    private static int readCoord(JsonObject w, String key, int panelSize) {
        if (!w.has(key)) return 0;
        JsonElement el = w.get(key);
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsInt();
            if (p.isString()) {
                String s = p.getAsString().trim();
                if (s.endsWith("%")) {
                    try {
                        float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                        return (int) (pct / 100f * panelSize);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static float jsonFloat(JsonObject w, float def, String... keys) {
        for (String key : keys) {
            if (w == null || !w.has(key)) continue;
            try {
                return w.get(key).getAsFloat();
            } catch (Exception ignored) {
                try {
                    return Float.parseFloat(w.get(key).getAsString().trim());
                } catch (Exception ignored2) {}
            }
        }
        return def;
    }

    private static boolean jsonBool(JsonObject w, boolean def, String... keys) {
        for (String key : keys) {
            if (w == null || !w.has(key)) continue;
            try {
                JsonElement el = w.get(key);
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
                return isTruthyPatch(el.getAsString());
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static float clamp01(float f) {
        return Math.min(1f, Math.max(0f, f));
    }

    private static boolean isTruthyPatch(String value) {
        if (value == null) return false;
        String s = value.trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }

    private static void normalizeViewportCorners(float[] v) {
        if (v[0] > v[2]) {
            float t = v[0];
            v[0] = v[2];
            v[2] = t;
        }
        if (v[1] > v[3]) {
            float t = v[1];
            v[1] = v[3];
            v[3] = t;
        }
    }

    /** Устаревший viewport [x0,y0,x1,y1] → crop [l,t,r,b]. */
    private static float[] viewportCornersToCrop(float[] vp) {
        float[] c = new float[]{vp[0], vp[1], 1f - vp[2], 1f - vp[3]};
        normalizeCrop(c);
        return c;
    }

    /** l,t,r,b — отступы обрезки от краёв ячейки (доли 0–1). */
    private static void normalizeCrop(float[] c) {
        for (int i = 0; i < 4; i++) {
            c[i] = clamp01(c[i]);
        }
        if (c[0] + c[2] > 1f) {
            float s = c[0] + c[2];
            c[0] /= s;
            c[2] /= s;
        }
        if (c[1] + c[3] > 1f) {
            float s = c[1] + c[3];
            c[1] /= s;
            c[3] /= s;
        }
    }

    /** Для ui_update: отрицательное значение — снова режим feet_crop. */
    private static float parseAnchorYPatch(String value) {
        float f = Float.parseFloat(value.trim());
        if (f < 0f) return -1f;
        return f;
    }

    /** JSON-массив или четыре числа через запятую/пробел (углы viewport). */
    private static float[] parseViewportPatch(String value) {
        String s = value != null ? value.trim() : "";
        if (s.isEmpty()) return null;
        try {
            float[] r = new float[4];
            if (s.startsWith("[")) {
                JsonArray arr = JsonParser.parseString(s).getAsJsonArray();
                if (arr.size() < 4) return null;
                for (int i = 0; i < 4; i++) r[i] = clamp01(arr.get(i).getAsFloat());
            } else {
                String[] p = s.split("[,;\\s]+");
                if (p.length < 4) return null;
                for (int i = 0; i < 4; i++) r[i] = clamp01(Float.parseFloat(p[i].trim()));
            }
            normalizeViewportCorners(r);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /** То же формат строки, что у viewport, но значения — crop l,t,r,b. */
    private static float[] parseCropPatch(String value) {
        String s = value != null ? value.trim() : "";
        if (s.isEmpty()) return null;
        try {
            float[] r = new float[4];
            if (s.startsWith("[")) {
                JsonArray arr = JsonParser.parseString(s).getAsJsonArray();
                if (arr.size() < 4) return null;
                for (int i = 0; i < 4; i++) r[i] = clamp01(arr.get(i).getAsFloat());
            } else {
                String[] p = s.split("[,;\\s]+");
                if (p.length < 4) return null;
                for (int i = 0; i < 4; i++) r[i] = clamp01(Float.parseFloat(p[i].trim()));
            }
            normalizeCrop(r);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseColor(String s) {
        return SprauteColors.toArgbInt(s, 0xFFFFFFFF);
    }

    @Override
    protected void init() {
        super.init();
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    private boolean checkOverlap(Widget w1, Widget w2, int ax0, int ay0) {
        float[] obb1 = w1.getOBB(this, ax0, ay0);
        float[] obb2 = w2.getOBB(this, ax0, ay0);
        if (obb1 == null || obb2 == null) return false;
        
        float[][] edges = new float[][] {
            {obb1[2]-obb1[0], obb1[3]-obb1[1]}, {obb1[4]-obb1[2], obb1[5]-obb1[3]},
            {obb2[2]-obb2[0], obb2[3]-obb2[1]}, {obb2[4]-obb2[2], obb2[5]-obb2[3]}
        };
        for (float[] edge : edges) {
            if (edge[0] == 0 && edge[1] == 0) continue;
            float nx = -edge[1];
            float ny = edge[0];
            float min1 = Float.MAX_VALUE, max1 = -Float.MAX_VALUE;
            for (int i=0; i<8; i+=2) {
                float p = obb1[i]*nx + obb1[i+1]*ny;
                if (p < min1) min1 = p;
                if (p > max1) max1 = p;
            }
            float min2 = Float.MAX_VALUE, max2 = -Float.MAX_VALUE;
            for (int i=0; i<8; i+=2) {
                float p = obb2[i]*nx + obb2[i+1]*ny;
                if (p < min2) min2 = p;
                if (p > max2) max2 = p;
            }
            if (max1 < min2 || max2 < min1) return false;
        }
        return true;
    }

    //? if >=1.20.1 {
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        if (dimBackground) super.renderBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        scissorGuiGraphics = guiGraphics;
        processAnimations();
        int ax0 = left;
        int ay0 = top;
        if (rootStyle.isPlain()) {
            SprauteGuiDraw.fill(guiGraphics, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb);
        } else {
            SprauteUiRender.panel(guiGraphics.pose(), ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb, rootStyle);
        }
        for (Widget w : widgets) {
            w.render(this, guiGraphics, ax0, ay0, mouseX, mouseY, partialTick);
        }
        ButtonW hoveredWithPanel = hitButtonHoverPanel(mouseX, mouseY);
        if (hoveredWithPanel == null) {
            Widget tipW = hitWidget(mouseX, mouseY, w -> {
                String tip = w.tooltip();
                return tip != null && !tip.isEmpty();
            });
            if (tipW != null) {
                guiGraphics.renderTooltip(this.font, Component.literal(tipW.tooltip()), mouseX, mouseY);
            }
        }

        // Process monitored overlaps
        if (!monitorOverlaps.isEmpty()) {
            for (String pair : monitorOverlaps) {
                String[] split = pair.split(":");
                if (split.length == 2) {
                    Widget w1 = findWidgetById(split[0]);
                    Widget w2 = findWidgetById(split[1]);
                    boolean overlaps = false;
                    if (w1 != null && w2 != null) {
                        overlaps = checkOverlap(w1, w2, ax0, ay0);
                    }
                    boolean prev = overlapState.getOrDefault(pair, false);
                    if (overlaps != prev) {
                        overlapState.put(pair, overlaps);
                        ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiOverlapActionPacket(split[0], split[1], overlaps));
                    }
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (hoveredWithPanel != null) {
            renderButtonHoverPanel(guiGraphics, hoveredWithPanel, mouseX, mouseY);
        }
    }
    //?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        processAnimations();
        if (dimBackground) renderBackground(poseStack);
        int ax0 = left;
        int ay0 = top;
        if (rootStyle.isPlain()) {
            GuiComponent.fill(poseStack, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb);
        } else {
            SprauteUiRender.panel(poseStack, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb, rootStyle);
        }
        for (Widget w : widgets) {
            w.render(this, poseStack, ax0, ay0, mouseX, mouseY, partialTick);
        }
        ButtonW hoveredWithPanel = hitButtonHoverPanel(mouseX, mouseY);
        if (hoveredWithPanel == null) {
            Widget tipW = hitWidget(mouseX, mouseY, w -> {
                String tip = w.tooltip();
                return tip != null && !tip.isEmpty();
            });
            if (tipW != null) {
                renderTooltip(poseStack, Component.literal(tipW.tooltip()), mouseX, mouseY);
            }
        }

        // Process monitored overlaps
        if (!monitorOverlaps.isEmpty()) {
            for (String pair : monitorOverlaps) {
                String[] split = pair.split(":");
                if (split.length == 2) {
                    Widget w1 = findWidgetById(split[0]);
                    Widget w2 = findWidgetById(split[1]);
                    boolean overlaps = false;
                    if (w1 != null && w2 != null) {
                        overlaps = checkOverlap(w1, w2, ax0, ay0);
                    }
                    boolean prev = overlapState.getOrDefault(pair, false);
                    if (overlaps != prev) {
                        overlapState.put(pair, overlaps);
                        ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiOverlapActionPacket(split[0], split[1], overlaps));
                    }
                }
            }
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
        if (hoveredWithPanel != null) {
            renderButtonHoverPanel(poseStack, hoveredWithPanel, mouseX, mouseY);
        }
    }
    *///?}

    private void layoutPosition(int sw, int sh) {
        left = (sw - panelW) / 2;
        top = (sh - panelH) / 2;
        if (root.has("x")) {
            int rawX = readRootExtent(root, "x", sw, left);
            left = rawX < 0 ? sw + rawX - panelW : rawX;
        }
        if (root.has("y")) {
            int rawY = readRootExtent(root, "y", sh, top);
            top = rawY < 0 ? sh + rawY - panelH : rawY;
        }
    }

    private static boolean inRect(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Topmost widget under cursor. Clip/scroll reject hits outside their box (same as visual crop). */
    private Widget hitWidget(double mouseX, double mouseY, java.util.function.Predicate<Widget> pred) {
        return hitWidget(widgets, left, top, mouseX, mouseY, pred);
    }

    private Widget hitWidget(List<Widget> list, int ax0, int ay0, double mx, double my,
                             java.util.function.Predicate<Widget> pred) {
        if (list == null) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Widget found = hitWidget(list.get(i), ax0, ay0, mx, my, pred);
            if (found != null) return found;
        }
        return null;
    }

    private Widget hitWidget(Widget w, int ax0, int ay0, double mx, double my,
                             java.util.function.Predicate<Widget> pred) {
        if (w == null) return null;
        if (w instanceof RotatedW rw) {
            float px = ax0 + rw.child.getX() + rw.ow * rw.pivotX;
            float py = ay0 + rw.child.getY() + rw.oh * rw.pivotY;
            double angle = Math.toRadians(-rw.rotation);
            float dx = (float) mx - px;
            float dy = (float) my - py;
            int lx = (int) (px + dx * Math.cos(angle) - dy * Math.sin(angle));
            int ly = (int) (py + dx * Math.sin(angle) + dy * Math.cos(angle));
            return hitWidget(rw.child, ax0, ay0, lx, ly, pred);
        }
        if (w instanceof ScrollW sw) {
            int sx = ax0 + sw.x, sy = ay0 + sw.y;
            if (!inRect(sx, sy, sw.w, sw.h, mx, my)) return null;
            Widget child = hitWidget(sw.children, sx, sy - (int) sw.scrollOffset, mx, my, pred);
            if (child != null) return child;
            return leafHit(sw, ax0, ay0, mx, my, pred);
        }
        if (w instanceof ClipW cw) {
            if (cw.alpha <= 0f) return null;
            int sx = ax0 + cw.x, sy = ay0 + cw.y;
            if (!inRect(sx, sy, cw.w, cw.h, mx, my)) return null;
            Widget child = hitWidget(cw.children, sx, sy, mx, my, pred);
            if (child != null) return child;
            return leafHit(cw, ax0, ay0, mx, my, pred);
        }
        if (w instanceof RectW rw) {
            int sx = ax0 + rw.x, sy = ay0 + rw.y;
            Widget child = hitWidget(rw.children, sx, sy, mx, my, pred);
            if (child != null) return child;
            return leafHit(rw, ax0, ay0, mx, my, pred);
        }
        if (w instanceof GroupW gw) {
            if (gw.alpha <= 0f) return null;
            Widget child = hitWidget(gw.children, ax0 + gw.x, ay0 + gw.y, mx, my, pred);
            if (child != null) return child;
            return leafHit(gw, ax0, ay0, mx, my, pred);
        }
        if (w instanceof DragW dw) {
            if (dw.alpha <= 0f) return null;
            Widget child = hitWidget(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my, pred);
            if (child != null) return child;
            return leafHit(dw, ax0, ay0, mx, my, pred);
        }
        if (w instanceof DropW drop) {
            if (drop.alpha <= 0f) return null;
            Widget child = hitWidget(drop.children, ax0 + drop.x, ay0 + drop.y, mx, my, pred);
            if (child != null) return child;
            return leafHit(drop, ax0, ay0, mx, my, pred);
        }
        return leafHit(w, ax0, ay0, mx, my, pred);
    }

    private Widget leafHit(Widget w, int ax0, int ay0, double mx, double my,
                           java.util.function.Predicate<Widget> pred) {
        if (!pred.test(w)) return null;
        return w.contains(this, ax0, ay0, (int) mx, (int) my) ? w : null;
    }

    private static boolean isClickableButton(Widget w) {
        return w instanceof ButtonW bw && bw.id != null && !bw.id.isEmpty();
    }

    private static boolean isHoverTarget(Widget w) {
        if (w instanceof ButtonW bw) return bw.id != null && !bw.id.isEmpty();
        if (w instanceof InputW inpw) return inpw.id != null && !inpw.id.isEmpty();
        return false;
    }

    private static boolean isClickableInput(Widget w) {
        return w instanceof InputW inpw && inpw.id != null && !inpw.id.isEmpty();
    }

    private ButtonW hitButtonHoverPanel(double mouseX, double mouseY) {
        Widget hit = hitWidget(mouseX, mouseY, w -> w instanceof ButtonW bw && bw.hasHoverPanel());
        return hit instanceof ButtonW bw ? bw : null;
    }

    /** Topmost button under cursor in this panel (scroll + crop + group). */
    private String findButtonAt(double mouseX, double mouseY) {
        Widget hit = hitWidget(mouseX, mouseY, SprauteScriptScreen::isClickableButton);
        return hit == null ? null : hit.getId();
    }

    /** Hover target: button or input with id. */
    private String findHoverTargetAt(double mouseX, double mouseY) {
        Widget hit = hitWidget(mouseX, mouseY, SprauteScriptScreen::isHoverTarget);
        return hit == null ? null : hit.getId();
    }

    private static void sendUiClick(String widgetId, int mouseButton) {
        ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                SprauteUiActionPacket.ACTION_CLICK, widgetId, mouseButton));
    }

    private static double[] scaledMouse(Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        return new double[]{mouseX, mouseY};
    }

    private static String findTopmostButtonAcrossOverlays(double mouseX, double mouseY, int sw, int sh) {
        var entries = new java.util.ArrayList<>(activeOverlays.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (hideSceneOverlayInEditor(entries.get(i).getKey())) continue;
            SprauteScriptScreen overlay = entries.get(i).getValue();
            overlay.layoutPosition(sw, sh);
            String id = overlay.findButtonAt(mouseX, mouseY);
            if (id != null) return id;
        }
        return null;
    }

    private static String findTopmostHoverAcrossOverlays(double mouseX, double mouseY, int sw, int sh) {
        var entries = new java.util.ArrayList<>(activeOverlays.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (hideSceneOverlayInEditor(entries.get(i).getKey())) continue;
            SprauteScriptScreen overlay = entries.get(i).getValue();
            overlay.layoutPosition(sw, sh);
            String id = overlay.findHoverTargetAt(mouseX, mouseY);
            if (id != null) return id;
        }
        return null;
    }

    private static String findTopmostHover(double mouseX, double mouseY, int sw, int sh) {
        String overlayHit = findTopmostHoverAcrossOverlays(mouseX, mouseY, sw, sh);
        if (overlayHit != null) return overlayHit;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay) {
            screen.layoutPosition(sw, sh);
            return screen.findHoverTargetAt(mouseX, mouseY);
        }
        return null;
    }

    private static void tickOverlayHover() {
        if (++hoverTickCounter < 2) return;
        hoverTickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getWindow() == null) return;

        boolean hasUi = !activeOverlays.isEmpty()
                || (mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay);
        if (!hasUi || uiDragSession != null) {
            if (uiDragSession != null) return;
            if (clientHoverWidgetId != null) {
                ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                        SprauteUiActionPacket.ACTION_HOVER_LEAVE, clientHoverWidgetId, -1));
                clientHoverWidgetId = null;
            }
            return;
        }

        double[] m = scaledMouse(mc);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        String hovered = findTopmostHover(m[0], m[1], sw, sh);
        if (java.util.Objects.equals(hovered, clientHoverWidgetId)) return;

        if (clientHoverWidgetId != null) {
            ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                    SprauteUiActionPacket.ACTION_HOVER_LEAVE, clientHoverWidgetId, -1));
        }
        if (hovered != null) {
            ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                    SprauteUiActionPacket.ACTION_HOVER_ENTER, hovered, -1));
        }
        clientHoverWidgetId = hovered;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickUiDrag();
        tickOverlayHover();
        tickVideoStateSync();
    }

    private static void tickVideoStateSync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay) {
            screen.syncVideoStates();
        }
        for (SprauteScriptScreen overlay : activeOverlays.values()) {
            overlay.syncVideoStates();
        }
    }

    private void syncVideoStates() {
        if (videoPlayers.isEmpty()) return;
        videoSyncTick++;
        boolean periodic = videoSyncTick % 5 == 0;
        for (Map.Entry<String, UiVideoPlayer> entry : videoPlayers.entrySet()) {
            String id = entry.getKey();
            UiVideoPlayer player = entry.getValue();
            if (id == null || id.isEmpty() || player == null) continue;
            boolean dirty = player.consumeStateDirty();
            if (!dirty && !periodic) continue;
            ModNetwork.CHANNEL.sendToServer(new SprauteUiVideoStatePacket(
                    id, player.timeSeconds(), player.durationSeconds(), player.isPlaying(), player.hasEnded()));
        }
    }

    @SubscribeEvent
    public static void onGlobalMouseClick(InputEvent.MouseButton.Pre event) {
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (uiDragSession != null) {
                double[] m = scaledMouse(mc);
                endUiDrag(m[0], m[1]);
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (activeOverlays.isEmpty()) return;

        double[] m = scaledMouse(mc);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (beginTopmostOverlayDrag(m[0], m[1], sw, sh, button)) {
            event.setCanceled(true);
            return;
        }
        String widgetId = findTopmostButtonAcrossOverlays(m[0], m[1], sw, sh);
        if (widgetId != null) {
            sendUiClick(widgetId, button);
            event.setCanceled(true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (beginUiDrag(mouseX, mouseY, button)) return true;
            boolean clickedInput = false;
            String clickedButton = findButtonAt(mouseX, mouseY);
            if (clickedButton != null) {
                sendUiClick(clickedButton, button);
                return true;
            }
            Widget inputHit = hitWidget(mouseX, mouseY, SprauteScriptScreen::isClickableInput);
            if (inputHit instanceof InputW inpw) {
                activeInputId = inpw.id;
                clickedInput = true;
            }
            if (!clickedInput) activeInputId = null;
            if (clickedInput) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (uiDragSession != null && uiDragSession.screen == this && uiDragSession.button == button) {
            endUiDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (uiDragSession != null && uiDragSession.screen == this) {
            updateUiDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private static void tickUiDrag() {
        if (uiDragSession == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        double[] m = scaledMouse(mc);
        updateUiDrag(m[0], m[1]);
    }

    private static boolean beginTopmostOverlayDrag(double mouseX, double mouseY, int sw, int sh, int button) {
        var entries = new java.util.ArrayList<>(activeOverlays.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (hideSceneOverlayInEditor(entries.get(i).getKey())) continue;
            SprauteScriptScreen overlay = entries.get(i).getValue();
            overlay.layoutPosition(sw, sh);
            if (overlay.beginUiDrag(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    private boolean beginUiDrag(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (uiDragSession != null) return false;
        DragW drag = findEnabledDragAt(mouseX, mouseY);
        if (drag == null || !drag.enabled) return false;
        int[] parent = locateWidgetParentAbs(drag);
        int grabDx = (int) mouseX - (parent[0] + drag.x);
        int grabDy = (int) mouseY - (parent[1] + drag.y);
        String pending = findButtonAt(mouseX, mouseY);
        uiDragSession = new UiDragSession(this, drag, grabDx, grabDy, (int) mouseX, (int) mouseY, button, pending);
        return true;
    }

    private static void updateUiDrag(double mouseX, double mouseY) {
        UiDragSession s = uiDragSession;
        if (s == null) return;
        DragW drag = s.widget;
        int dx = (int) mouseX - s.startMx;
        int dy = (int) mouseY - s.startMy;
        if (!s.active) {
            int dist2 = dx * dx + dy * dy;
            int th = Math.max(0, drag.threshold);
            if (dist2 < th * th) return;
            s.active = true;
            if (!s.startSent) {
                s.startSent = true;
                ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                        SprauteUiActionPacket.ACTION_DRAG_START,
                        SprauteUiActionPacket.encodeDragStart(drag.id, drag.payload), 0));
            }
        }
        int[] parent = s.screen.locateWidgetParentAbs(drag);
        int nx = s.originX;
        int ny = s.originY;
        if (drag.axisX()) nx = (int) mouseX - parent[0] - s.grabDx;
        if (drag.axisY()) ny = (int) mouseY - parent[1] - s.grabDy;
        if (drag.constrain) {
            nx = Math.max(0, Math.min(nx, Math.max(0, drag.parentW - drag.w)));
            ny = Math.max(0, Math.min(ny, Math.max(0, drag.parentH - drag.h)));
        }
        drag.x = nx;
        drag.y = ny;
        DropW next = s.screen.findDropAt(mouseX, mouseY, drag);
        if (s.hotDrop != null && s.hotDrop != next) s.hotDrop.hot = false;
        if (next != null) next.hot = true;
        s.hotDrop = next;
    }

    private static void endUiDrag(double mouseX, double mouseY) {
        UiDragSession s = uiDragSession;
        if (s == null) return;
        uiDragSession = null;
        if (s.hotDrop != null) s.hotDrop.hot = false;
        DragW drag = s.widget;
        if (!s.active) {
            if (s.pendingButtonId != null && !s.pendingButtonId.isEmpty()) {
                sendUiClick(s.pendingButtonId, s.button);
            }
            return;
        }
        DropW drop = s.screen.findDropAt(mouseX, mouseY, drag);
        String dropId = drop != null && drop.id != null ? drop.id : "";
        if (drop == null && drag.revert) {
            drag.x = s.originX;
            drag.y = s.originY;
        }
        int[] parent = s.screen.locateWidgetParentAbs(drag);
        int absX = parent[0] + drag.x;
        int absY = parent[1] + drag.y;
        ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                SprauteUiActionPacket.ACTION_DROP,
                SprauteUiActionPacket.encodeDrop(drag.id, dropId, absX, absY, drag.payload), 0));
        ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                SprauteUiActionPacket.ACTION_DRAG_END,
                SprauteUiActionPacket.encodeDragEnd(drag.id, dropId), 0));
    }

    private DragW findEnabledDragAt(double mx, double my) {
        return findEnabledDragAt(widgets, left, top, mx, my);
    }

    private DragW findEnabledDragAt(List<Widget> list, int ax0, int ay0, double mx, double my) {
        if (list == null) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            DragW found = findEnabledDragAt(list.get(i), ax0, ay0, mx, my);
            if (found != null) return found;
        }
        return null;
    }

    private DragW findEnabledDragAt(Widget w, int ax0, int ay0, double mx, double my) {
        if (w == null) return null;
        if (w instanceof RotatedW rw) {
            return findEnabledDragAt(rw.child, ax0, ay0, mx, my);
        }
        if (w instanceof ScrollW sw) {
            int sx = ax0 + sw.x, sy = ay0 + sw.y;
            if (!inRect(sx, sy, sw.w, sw.h, mx, my)) return null;
            return findEnabledDragAt(sw.children, sx, sy - (int) sw.scrollOffset, mx, my);
        }
        if (w instanceof ClipW cw) {
            if (cw.alpha <= 0f) return null;
            int sx = ax0 + cw.x, sy = ay0 + cw.y;
            if (!inRect(sx, sy, cw.w, cw.h, mx, my)) return null;
            DragW inner = findEnabledDragAt(cw.children, sx, sy, mx, my);
            return inner;
        }
        if (w instanceof RectW rw) {
            return findEnabledDragAt(rw.children, ax0 + rw.x, ay0 + rw.y, mx, my);
        }
        if (w instanceof GroupW gw) {
            if (gw.alpha <= 0f) return null;
            return findEnabledDragAt(gw.children, ax0 + gw.x, ay0 + gw.y, mx, my);
        }
        if (w instanceof DropW drop) {
            if (drop.alpha <= 0f) return null;
            return findEnabledDragAt(drop.children, ax0 + drop.x, ay0 + drop.y, mx, my);
        }
        if (w instanceof DragW dw) {
            if (dw.alpha <= 0f || !dw.enabled) return findEnabledDragAt(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my);
            if (!dw.contains(this, ax0, ay0, (int) mx, (int) my)) {
                return findEnabledDragAt(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my);
            }
            DragW nested = findEnabledDragAt(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my);
            return nested != null ? nested : dw;
        }
        return null;
    }

    private DropW findDropAt(double mx, double my, DragW dragging) {
        return findDropAt(widgets, left, top, mx, my, dragging);
    }

    private DropW findDropAt(List<Widget> list, int ax0, int ay0, double mx, double my, DragW dragging) {
        if (list == null) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            DropW found = findDropAt(list.get(i), ax0, ay0, mx, my, dragging);
            if (found != null) return found;
        }
        return null;
    }

    private DropW findDropAt(Widget w, int ax0, int ay0, double mx, double my, DragW dragging) {
        if (w == null || w == dragging) return null;
        if (w instanceof RotatedW rw) {
            return findDropAt(rw.child, ax0, ay0, mx, my, dragging);
        }
        if (w instanceof ScrollW sw) {
            int sx = ax0 + sw.x, sy = ay0 + sw.y;
            if (!inRect(sx, sy, sw.w, sw.h, mx, my)) return null;
            DropW inner = findDropAt(sw.children, sx, sy - (int) sw.scrollOffset, mx, my, dragging);
            return inner;
        }
        if (w instanceof ClipW cw) {
            if (cw.alpha <= 0f) return null;
            int sx = ax0 + cw.x, sy = ay0 + cw.y;
            if (!inRect(sx, sy, cw.w, cw.h, mx, my)) return null;
            return findDropAt(cw.children, sx, sy, mx, my, dragging);
        }
        if (w instanceof RectW rw) {
            return findDropAt(rw.children, ax0 + rw.x, ay0 + rw.y, mx, my, dragging);
        }
        if (w instanceof GroupW gw) {
            if (gw.alpha <= 0f) return null;
            return findDropAt(gw.children, ax0 + gw.x, ay0 + gw.y, mx, my, dragging);
        }
        if (w instanceof DragW dw) {
            if (dw == dragging) return null;
            if (dw.alpha <= 0f) return null;
            return findDropAt(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my, dragging);
        }
        if (w instanceof DropW drop) {
            if (drop.alpha <= 0f) return null;
            DropW inner = findDropAt(drop.children, ax0 + drop.x, ay0 + drop.y, mx, my, dragging);
            if (inner != null) return inner;
            if (drop.enabled && dropAccepts(drop, dragging) && drop.contains(this, ax0, ay0, (int) mx, (int) my)) {
                return drop;
            }
        }
        return null;
    }

    private static boolean dropAccepts(DropW drop, DragW drag) {
        if (drop == null || drag == null || !drop.enabled) return false;
        String dg = drag.group == null ? "" : drag.group;
        String og = drop.group == null ? "" : drop.group;
        return og.isEmpty() || dg.isEmpty() || og.equals(dg);
    }

    private int[] locateWidgetParentAbs(Widget target) {
        int[] found = locateWidgetParentAbs(widgets, left, top, target);
        return found != null ? found : new int[]{left, top};
    }

    private int[] locateWidgetParentAbs(List<Widget> list, int ax0, int ay0, Widget target) {
        if (list == null) return null;
        for (Widget w : list) {
            if (w == target) return new int[]{ax0, ay0};
            if (w instanceof RotatedW rw) {
                int[] inner = locateWidgetParentAbs(List.of(rw.child), ax0, ay0, target);
                if (inner != null) return inner;
            }
            if (w instanceof ScrollW sw) {
                int[] inner = locateWidgetParentAbs(sw.children, ax0 + sw.x, ay0 + sw.y - (int) sw.scrollOffset, target);
                if (inner != null) return inner;
            }
            if (w instanceof ClipW cw) {
                int[] inner = locateWidgetParentAbs(cw.children, ax0 + cw.x, ay0 + cw.y, target);
                if (inner != null) return inner;
            }
            if (w instanceof RectW rw) {
                int[] inner = locateWidgetParentAbs(rw.children, ax0 + rw.x, ay0 + rw.y, target);
                if (inner != null) return inner;
            }
            if (w instanceof GroupW gw) {
                int[] inner = locateWidgetParentAbs(gw.children, ax0 + gw.x, ay0 + gw.y, target);
                if (inner != null) return inner;
            }
            if (w instanceof DragW dw) {
                int[] inner = locateWidgetParentAbs(dw.children, ax0 + dw.x, ay0 + dw.y, target);
                if (inner != null) return inner;
            }
            if (w instanceof DropW drop) {
                int[] inner = locateWidgetParentAbs(drop.children, ax0 + drop.x, ay0 + drop.y, target);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseScrollWidget(widgets, left, top, mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean mouseScrollWidget(List<Widget> list, int ax0, int ay0, double mx, double my, double delta) {
        if (list == null) return false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (mouseScrollWidget(list.get(i), ax0, ay0, mx, my, delta)) return true;
        }
        return false;
    }

    private boolean mouseScrollWidget(Widget w, int ax0, int ay0, double mx, double my, double delta) {
        if (w instanceof RotatedW rw) {
            return mouseScrollWidget(rw.child, ax0, ay0, mx, my, delta);
        }
        if (w instanceof ScrollW sw) {
            int sx = ax0 + sw.x, sy = ay0 + sw.y;
            if (mx < sx || mx >= sx + sw.w || my < sy || my >= sy + sw.h) return false;
            if (mouseScrollWidget(sw.children, sx, sy - (int) sw.scrollOffset, mx, my, delta)) return true;
            sw.scrollOffset -= (float) (delta * 12.0);
            float maxScroll = Math.max(0, sw.contentH - sw.h);
            sw.scrollOffset = Math.max(0, Math.min(sw.scrollOffset, maxScroll));
            if (sw.id != null && !sw.id.isEmpty()) {
                ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                        SprauteUiActionPacket.ACTION_SCROLL, sw.id, (int) sw.scrollOffset));
            }
            return true;
        }
        if (w instanceof ClipW cw) {
            if (cw.alpha <= 0f) return false;
            int sx = ax0 + cw.x, sy = ay0 + cw.y;
            if (!inRect(sx, sy, cw.w, cw.h, mx, my)) return false;
            return mouseScrollWidget(cw.children, sx, sy, mx, my, delta);
        }
        if (w instanceof RectW rw) {
            return mouseScrollWidget(rw.children, ax0 + rw.x, ay0 + rw.y, mx, my, delta);
        }
        if (w instanceof GroupW gw) {
            if (gw.alpha <= 0f) return false;
            return mouseScrollWidget(gw.children, ax0 + gw.x, ay0 + gw.y, mx, my, delta);
        }
        if (w instanceof DragW dw) {
            if (dw.alpha <= 0f) return false;
            return mouseScrollWidget(dw.children, ax0 + dw.x, ay0 + dw.y, mx, my, delta);
        }
        if (w instanceof DropW drop) {
            if (drop.alpha <= 0f) return false;
            return mouseScrollWidget(drop.children, ax0 + drop.x, ay0 + drop.y, mx, my, delta);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeInputId != null) {
            if (keyCode == 259) { // Backspace
                Widget w = findWidgetById(activeInputId);
                if (w instanceof InputW inpw && !inpw.text.isEmpty()) {
                    String newText = inpw.text.substring(0, inpw.text.length() - 1);
                    applyWidgetPatch(inpw.id, "text", newText);
                    ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket(inpw.id + ":" + newText, false));
                }
                return true;
            }
        }

        for (var entry : ScriptKeybindListener.KEY_MAP.entrySet()) {
            if (entry.getValue() == keyCode) {
                ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.KeybindPressedPacket(entry.getKey()));
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeInputId != null && net.minecraft.SharedConstants.isAllowedChatCharacter(codePoint)) {
            Widget w = findWidgetById(activeInputId);
            if (w instanceof InputW inpw && inpw.text.length() < inpw.maxChars) {
                String newText = inpw.text + codePoint;
                applyWidgetPatch(inpw.id, "text", newText);
                ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket(inpw.id + ":" + newText, false));
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        destroyScrollFreezes(widgets);
        disposeMedia();
        if (!suppressClosePacket) {
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                    new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket(
                            org.zonarstudio.spraute_engine.network.SprauteUiActionPacket.ACTION_CLOSE, "", -1));
        }
        monitorOverlaps.clear();
        overlapState.clear();
        suppressClosePacket = false;
        super.onClose();
    }

    @Override
    public void removed() {
        disposeMedia();
        super.removed();
    }

    private void disposeMedia() {
        videoPlayers.values().forEach(UiVideoPlayer::close);
        videoPlayers.clear();
    }

    private static void destroyScrollFreezes(List<Widget> list) {
        if (list == null) return;
        for (Widget w : list) {
            if (w instanceof ScrollW sw) {
                sw.destroyFreeze();
                destroyScrollFreezes(sw.children);
            } else if (w instanceof ClipW cw) {
                cw.destroyFreeze();
                destroyScrollFreezes(cw.children);
            } else if (w instanceof RectW rw) {
                destroyScrollFreezes(rw.children);
            } else if (w instanceof GroupW gw) {
                destroyScrollFreezes(gw.children);
            } else if (w instanceof DragW dw) {
                destroyScrollFreezes(dw.children);
            } else if (w instanceof DropW drop) {
                destroyScrollFreezes(drop.children);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canClose;
    }

    private interface Widget {
        //? if >=1.20.1 {
        void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick);
        //?} else {
        /*void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick);
        *///?}

        default String tooltip() {
            return null;
        }

        default boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return false;
        }
        
        default int getX() { return 0; }
        default int getY() { return 0; }
        default int approxHeight() { return 24; }
        default String getId() { return ""; }
        default float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return null; }
        default float[] getBaseOBB(float px, float py, float pw, float ph) {
            return new float[]{ px, py, px + pw, py, px + pw, py + ph, px, py + ph };
        }
    }

    private static class RectW implements Widget {
        final int x, y, w, h, color;
        final String tooltip;
        final String id;
        final SprauteUiRender.Style style;
        final String fill;
        final float fillAmount;
        final String fillOrigin;
        final String cropShape;
        final List<Widget> children = new ArrayList<>();

        RectW(int x, int y, int w, int h, int color, String tooltip, String id,
              SprauteUiRender.Style style, String fill, float fillAmount, String fillOrigin, String cropShape) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.color = color;
            this.tooltip = tooltip; this.id = id;
            this.style = style == null ? SprauteUiRender.Style.NONE : style;
            this.fill = fill == null ? "" : fill;
            this.fillAmount = fillAmount;
            this.fillOrigin = fillOrigin == null ? "bottom" : fillOrigin;
            this.cropShape = cropShape == null ? "rect" : cropShape;
        }

        RectW(int x, int y, int w, int h, int color, String tooltip, String id) {
            this(x, y, w, h, color, tooltip, id, SprauteUiRender.Style.NONE, "", 1f, "bottom", "rect");
        }

        RectW(int x, int y, int w, int h, int color, String tooltip, String id, SprauteUiRender.Style style) {
            this(x, y, w, h, color, tooltip, id, style, "", 1f, "bottom", "rect");
        }

        RectW copy(int nx, int ny, int nw, int nh, int nColor, String nTooltip, String nId,
                   SprauteUiRender.Style nStyle, String nFill, float nAmount, String nOrigin, String nCrop) {
            RectW n = new RectW(nx, ny, nw, nh, nColor, nTooltip, nId, nStyle, nFill, nAmount, nOrigin, nCrop);
            n.children.addAll(children);
            return n;
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override
        public String tooltip() {
            return tooltip;
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return UiImageFill.contains(ax0 + x, ay0 + y, w, h, mx, my, cropShape);
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int c = applyAlpha(color, screen.currentAlpha);
            if (!UiImageFill.isFull(fill, fillAmount) || UiImageFill.isCircle(cropShape)) {
                UiImageFill.fillColor(guiGraphics.pose(), ax0 + x, ay0 + y, w, h, c, fill, fillAmount, fillOrigin, cropShape);
            } else if (style.isPlain()) {
                SprauteGuiDraw.fill(guiGraphics, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, c);
            } else {
                SprauteUiRender.panel(guiGraphics.pose(), ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, c, styleWithAlpha(style, screen.currentAlpha));
            }
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, guiGraphics, sx, sy, mouseX, mouseY, partialTick);
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int c = applyAlpha(color, screen.currentAlpha);
            if (!UiImageFill.isFull(fill, fillAmount) || UiImageFill.isCircle(cropShape)) {
                UiImageFill.fillColor(poseStack, ax0 + x, ay0 + y, w, h, c, fill, fillAmount, fillOrigin, cropShape);
            } else if (style.isPlain()) {
                GuiComponent.fill(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, c);
            } else {
                SprauteUiRender.panel(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, c, styleWithAlpha(style, screen.currentAlpha));
            }
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
            }
        }
        *///?}
    }

    private static List<Widget> nestedUiChildren(Widget w) {
        if (w instanceof RectW rw) return rw.children;
        if (w instanceof ScrollW sw) return sw.children;
        if (w instanceof ClipW cw) return cw.children;
        if (w instanceof GroupW gw) return gw.children;
        if (w instanceof DragW dw) return dw.children;
        if (w instanceof DropW drop) return drop.children;
        return null;
    }

    private record GridBgW(int x, int y, int w, int h, String gridType, int cellSize, int thickness, int color, String tooltip, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int drawColor = applyAlpha(color, screen.currentAlpha);
            if ((drawColor & 0xFF000000) == 0) return;
            int sx = ax0 + x;
            int sy = ay0 + y;

            if (gridType.contains("h")) {
                for (int i = 0; i <= h; i += cellSize) {
                    SprauteGuiDraw.fill(guiGraphics, sx, sy + i, sx + w, sy + i + thickness, drawColor);
                }
            }
            if (gridType.contains("v")) {
                for (int i = 0; i <= w; i += cellSize) {
                    SprauteGuiDraw.fill(guiGraphics, sx + i, sy, sx + i + thickness, sy + h, drawColor);
                }
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int drawColor = applyAlpha(color, screen.currentAlpha);
            if ((drawColor & 0xFF000000) == 0) return;
            int sx = ax0 + x;
            int sy = ay0 + y;

            // Draw horizontal lines
            if (gridType.contains("h")) {
                for (int i = 0; i <= h; i += cellSize) {
                    GuiComponent.fill(poseStack, sx, sy + i, sx + w, sy + i + thickness, drawColor);
                }
            }
            // Draw vertical lines
            if (gridType.contains("v")) {
                for (int i = 0; i <= w; i += cellSize) {
                    GuiComponent.fill(poseStack, sx + i, sy, sx + i + thickness, sy + h, drawColor);
                }
            }
        }
        *///?}
    }

    private static boolean useNineSliceWithFill(int sliceBorders, String cropShape, String fill, float amount) {
        if (sliceBorders <= 0 || UiImageFill.isCircle(cropShape)) return false;
        if (UiImageFill.isFull(fill, amount)) return true;
        return !UiImageFill.isRadial(UiImageFill.effectiveMode(fill, amount));
    }

    private static boolean pushLinearFillScissor(int x, int y, int w, int h, String fill, float amount) {
        float a = UiImageFill.clampAmount(amount);
        if (a <= 0.001f || UiImageFill.isFull(fill, a)) return false;
        String mode = UiImageFill.effectiveMode(fill, a);
        if (UiImageFill.isRadial(mode)) return false;
        int[] box = UiImageFill.linearBox(x, y, w, h, 0, 0, w, h, mode, a);
        pushScissor(box[0], box[1], box[0] + box[2], box[1] + box[3]);
        return true;
    }

    private record ImageW(int x, int y, int w, int h, String texture, String tooltip, String id,
                          int sliceBorders, float sliceScale, int srcU, int srcV, int srcW, int srcH,
                          String fill, float fillAmount, String fillOrigin, String cropShape) implements Widget {
        ImageW(int x, int y, int w, int h, String texture, String tooltip, String id,
               int sliceBorders, float sliceScale, int srcU, int srcV, int srcW, int srcH) {
            this(x, y, w, h, texture, tooltip, id, sliceBorders, sliceScale, srcU, srcV, srcW, srcH, "", 1f, "bottom", "rect");
        }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override
        public String tooltip() {
            return tooltip;
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return UiImageFill.contains(ax0 + x, ay0 + y, w, h, mx, my, cropShape);
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
            if (useNineSliceWithFill(sliceBorders, cropShape, fill, fillAmount)) {
                if (UiImageFill.clampAmount(fillAmount) <= 0.001f) return;
                boolean clip = pushLinearFillScissor(ax0 + x, ay0 + y, w, h, fill, fillAmount);
                if (screen.currentAlpha < 1.0f) {
                    guiGraphics.setColor(1f, 1f, 1f, screen.currentAlpha);
                }
                renderNineSlice(guiGraphics, rl, ax0 + x, ay0 + y, w, h, sliceBorders, sliceScale, texture);
                guiGraphics.setColor(1f, 1f, 1f, 1f);
                if (clip) popScissor();
                return;
            }
            int[] tex = texturePixelSize(rl, texture);
            int[] src = resolveFillSrcPixels(texture, srcU, srcV, srcW, srcH, tex[0], tex[1]);
            UiImageFill.blitTexture(guiGraphics.pose(), rl, ax0 + x, ay0 + y, w, h,
                    src[0], src[1], src[2], src[3], tex[0], tex[1],
                    fill, fillAmount, fillOrigin, screen.currentAlpha, cropShape);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
            if (useNineSliceWithFill(sliceBorders, cropShape, fill, fillAmount)) {
                if (UiImageFill.clampAmount(fillAmount) <= 0.001f) return;
                boolean clip = pushLinearFillScissor(ax0 + x, ay0 + y, w, h, fill, fillAmount);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderColor(1f, 1f, 1f, screen.currentAlpha);
                RenderSystem.setShaderTexture(0, rl);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                renderNineSlice(poseStack, rl, ax0 + x, ay0 + y, w, h, sliceBorders, sliceScale, texture);
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                if (clip) popScissor();
                return;
            }
            int[] tex = texturePixelSize(rl, texture);
            int[] src = resolveFillSrcPixels(texture, srcU, srcV, srcW, srcH, tex[0], tex[1]);
            UiImageFill.blitTexture(poseStack, rl, ax0 + x, ay0 + y, w, h,
                    src[0], src[1], src[2], src[3], tex[0], tex[1],
                    fill, fillAmount, fillOrigin, screen.currentAlpha, cropShape);
        }
        *///?}
    }

    private static boolean parseSvgFlag(String value) {
        if (value == null) return false;
        String s = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    private record SvgW(int x, int y, int w, int h, String source, String color, String stroke,
                        float strokeWidth, boolean recolor, String tooltip, String id,
                        String fill, float fillAmount, String fillOrigin, String cropShape) implements Widget {
        SvgW withBounds(int nx, int ny, int nw, int nh) {
            return new SvgW(nx, ny, Math.max(1, nw), Math.max(1, nh), source, color, stroke,
                    strokeWidth, recolor, tooltip, id, fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withSource(String src) {
            return new SvgW(x, y, w, h, src, color, stroke, strokeWidth, recolor, tooltip, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withColor(String c) {
            return new SvgW(x, y, w, h, source, c, stroke, strokeWidth, recolor, tooltip, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withStroke(String s) {
            return new SvgW(x, y, w, h, source, color, s, strokeWidth, recolor, tooltip, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withStrokeWidth(float sw) {
            return new SvgW(x, y, w, h, source, color, stroke, sw, recolor, tooltip, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withRecolor(boolean r) {
            return new SvgW(x, y, w, h, source, color, stroke, strokeWidth, r, tooltip, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withTooltip(String t) {
            return new SvgW(x, y, w, h, source, color, stroke, strokeWidth, recolor, t, id,
                    fill, fillAmount, fillOrigin, cropShape);
        }
        SvgW withFill(String f, float amount, String origin, String shape) {
            return new SvgW(x, y, w, h, source, color, stroke, strokeWidth, recolor, tooltip, id,
                    f, amount, origin, shape);
        }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public String tooltip() { return tooltip; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) {
            return getBaseOBB(ax0 + x, ay0 + y, w, h);
        }
        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return UiImageFill.contains(ax0 + x, ay0 + y, w, h, mx, my, cropShape);
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            UiSvgTextures.Handle handle = UiSvgTextures.get(source, w, h, color, stroke, strokeWidth, recolor);
            if (handle == null || handle.location() == null) return;
            UiImageFill.blitTexture(guiGraphics.pose(), handle.location(), ax0 + x, ay0 + y, w, h,
                    0, 0, handle.width(), handle.height(), handle.width(), handle.height(),
                    fill, fillAmount, fillOrigin, screen.currentAlpha, cropShape);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            UiSvgTextures.Handle handle = UiSvgTextures.get(source, w, h, color, stroke, strokeWidth, recolor);
            if (handle == null || handle.location() == null) return;
            UiImageFill.blitTexture(poseStack, handle.location(), ax0 + x, ay0 + y, w, h,
                    0, 0, handle.width(), handle.height(), handle.width(), handle.height(),
                    fill, fillAmount, fillOrigin, screen.currentAlpha, cropShape);
        }
        *///?}
    }

    private record VideoW(int x, int y, int w, int h, String source,
                          boolean playerEnabled, boolean playing, boolean loop,
                          String tooltip, String id, UiVideoPlayer player) implements Widget {
        VideoW withBounds(int x, int y, int w, int h) {
            return new VideoW(x, y, Math.max(1, w), Math.max(1, h), source,
                    playerEnabled, playing, loop, tooltip, id, player);
        }

        VideoW configure(String source, boolean enabled, boolean playing, boolean loop) {
            player.configure(source, enabled, playing, loop);
            return new VideoW(x, y, w, h, source, enabled, playing, loop, tooltip, id, player);
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public String tooltip() { return tooltip; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) {
            return getBaseOBB(ax0 + x, ay0 + y, w, h);
        }
        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return playerEnabled && mx >= ax0 + x && mx < ax0 + x + w
                    && my >= ay0 + y && my < ay0 + y + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics,
                           int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (!playerEnabled) return;
            ResourceLocation texture = player.textureLocation();
            int vx = ax0 + x;
            int vy = ay0 + y;
            if (texture == null) {
                SprauteGuiDraw.fill(guiGraphics, vx, vy, vx + w, vy + h,
                        applyAlpha(0xFF080A0E, screen.currentAlpha));
            } else {
                guiGraphics.setColor(1f, 1f, 1f, screen.currentAlpha);
                guiGraphics.blit(texture, vx, vy, 0f, 0f, w, h, w, h);
                guiGraphics.setColor(1f, 1f, 1f, 1f);
            }
            if (player.isPreparing() || (texture == null && player.error() != null && !player.error().isEmpty())) {
                renderVideoWait(guiGraphics, vx, vy, w, h, player, screen.currentAlpha);
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack,
                           int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (!playerEnabled) return;
            ResourceLocation texture = player.textureLocation();
            int vx = ax0 + x;
            int vy = ay0 + y;
            if (texture == null) {
                GuiComponent.fill(poseStack, vx, vy, vx + w, vy + h,
                        applyAlpha(0xFF080A0E, screen.currentAlpha));
            } else {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShaderColor(1f, 1f, 1f, screen.currentAlpha);
                GuiComponent.blit(poseStack, vx, vy, 0f, 0f, w, h, w, h);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            if (player.isPreparing() || (texture == null && player.error() != null && !player.error().isEmpty())) {
                renderVideoWait(poseStack, vx, vy, w, h, player, screen.currentAlpha);
            }
        }
        *///?}
    }

    private record TextW(int x, int y, String text, int color, float scale, String tooltip, String id, int wrapWidth, String wrapMode, String align, int maxLines, int maxChars, float anchorX, float anchorY, int textOffsetX, int textOffsetY) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) {
            float[] bounds = getBounds(screen);
            float tw = bounds[0];
            float th = bounds[1];
            float offsetX = -tw * anchorX;
            float offsetY = -th * anchorY;
            float lx = ax0 + x + offsetX + textOffsetX;
            float ly = ay0 + y + offsetY + textOffsetY;
            return getBaseOBB(lx, ly, tw, th);
        }
        @Override
        public String tooltip() {
            return tooltip;
        }

        private float[] getBounds(SprauteScriptScreen screen) {
            String renderText = text != null ? text.replace("&", "§") : "";
            UiTextStyle textStyle = screen.textStyle(id);
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }
            float totalW = 0;
            float totalH = 0;
            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = wrapUiText(screen.font, renderText, effWrap, wrapMode, textStyle);
                if (maxLines > 0 && lines.size() > maxLines) lines = lines.subList(0, maxLines);
                for (var line : lines) {
                    float w = screen.font.width(line);
                    if (w > totalW) totalW = w;
                }
                totalH = lines.size() * screen.font.lineHeight;
            } else {
                totalW = screen.font.width(textStyle.component(renderText));
                totalH = screen.font.lineHeight;
            }
            return new float[]{totalW * scale, totalH * scale, renderText.length() > 0 ? 1f : 0f};
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            if (tooltip == null || tooltip.isEmpty()) return false;
            float[] bounds = getBounds(screen);
            float tw = bounds[0];
            float th = bounds[1];
            float offsetX = -tw * anchorX;
            float offsetY = -th * anchorY;
            float lx = ax0 + x + offsetX + textOffsetX;
            float ly = ay0 + y + offsetY + textOffsetY;
            return mx >= lx && mx < lx + tw && my >= ly && my < ly + th;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            String renderText = text != null ? text.replace("&", "§") : "";
            UiTextStyle textStyle = screen.textStyle(id);
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }

            float[] bounds = getBounds(screen);
            float offsetX = -bounds[0] * anchorX / scale;
            float offsetY = -bounds[1] * anchorY / scale;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(ax0 + x + offsetX * scale + textOffsetX, ay0 + y + offsetY * scale + textOffsetY, 0);
            guiGraphics.pose().scale(scale, scale, 1f);
            int drawColor = color;

            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = wrapUiText(screen.font, renderText, effWrap, wrapMode, textStyle);
                int lineY = 0;
                int linesDrawn = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    if (maxLines > 0 && linesDrawn >= maxLines) break;
                    float drawX = 0;
                    if ("center".equals(align)) {
                        drawX = (effWrap - screen.font.width(line)) / 2f;
                    } else if ("right".equals(align)) {
                        drawX = effWrap - screen.font.width(line);
                    }
                    UiTextRenderer.drawLine(guiGraphics, screen.font, line, drawX, lineY, drawColor, textStyle, screen.currentAlpha);
                    lineY += screen.font.lineHeight;
                    linesDrawn++;
                }
            } else {
                UiTextRenderer.drawLine(guiGraphics, screen.font, textStyle.component(renderText).getVisualOrderText(),
                        0, 0, drawColor, textStyle, screen.currentAlpha);
            }
            guiGraphics.pose().popPose();
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            String renderText = text != null ? text.replace("&", "§") : "";
            UiTextStyle textStyle = screen.textStyle(id);
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }

            float[] bounds = getBounds(screen);
            float offsetX = -bounds[0] * anchorX / scale;
            float offsetY = -bounds[1] * anchorY / scale;

            poseStack.pushPose();
            poseStack.translate(ax0 + x + offsetX * scale + textOffsetX, ay0 + y + offsetY * scale + textOffsetY, 0);
            poseStack.scale(scale, scale, 1f);
            int drawColor = color;

            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = wrapUiText(screen.font, renderText, effWrap, wrapMode, textStyle);
                int lineY = 0;
                int linesDrawn = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    if (maxLines > 0 && linesDrawn >= maxLines) break;
                    float drawX = 0;
                    if ("center".equals(align)) {
                        drawX = (effWrap - screen.font.width(line)) / 2f;
                    } else if ("right".equals(align)) {
                        drawX = effWrap - screen.font.width(line);
                    }
                    UiTextRenderer.drawLine(poseStack, screen.font, line, drawX, lineY, drawColor, textStyle, screen.currentAlpha);
                    lineY += screen.font.lineHeight;
                    linesDrawn++;
                }
            } else {
                UiTextRenderer.drawLine(poseStack, screen.font, textStyle.component(renderText).getVisualOrderText(),
                        0, 0, drawColor, textStyle, screen.currentAlpha);
            }
            poseStack.popPose();
        }
        *///?}
    }

    private record InputW(String id, int x, int y, int w, int h, String text, String placeholder, int color, int bgColor, int outlineColor, float scale, String tooltip, int maxChars, String type, SprauteUiRender.Style style) implements Widget {
        InputW {
            if (style == null) style = SprauteUiRender.Style.NONE;
        }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override
        public String tooltip() {
            return tooltip;
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x;
            int ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;

            if (style.isPlain()) {
                SprauteGuiDraw.fill(guiGraphics, bx, by, bx + w, by + h, outlineColor);
                SprauteGuiDraw.fill(guiGraphics, bx + 1, by + 1, bx + w - 1, by + h - 1, bgColor);
            } else {
                // Rounded input: outline drawn as a border ring over the rounded background.
                float bw2 = style.borderWidth() > 0f ? style.borderWidth() : 1f;
                int bc = style.borderWidth() > 0f ? style.borderColor() : outlineColor;
                SprauteUiRender.panel(guiGraphics.pose(), bx, by, bx + w, by + h, bgColor,
                        new SprauteUiRender.Style(style.radius(), style.gradientStops(), style.gradientAngle(), bw2, bc, style.borderStops(), style.borderAngle(), style.shadowSize(),
                                style.outerBorderWidth(), style.outerBorderColor(), style.outerBorderStops(), style.outerBorderAngle()));
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(bx + 4, by + (h - screen.font.lineHeight * scale) / 2f, 0);
            guiGraphics.pose().scale(scale, scale, 1f);

            String displayText = text;
            if ("password".equals(type)) {
                displayText = "*".repeat(text.length());
            }

            boolean active = screen.activeInputId != null && screen.activeInputId.equals(id);
            if (active && (System.currentTimeMillis() / 500) % 2 == 0) {
                displayText += "_";
            }

            if (displayText.isEmpty() && placeholder != null && !placeholder.isEmpty() && !active) {
                UiTextStyle textStyle = screen.textStyle(id);
                UiTextRenderer.drawLine(guiGraphics, screen.font, textStyle.component(placeholder).getVisualOrderText(),
                        0, 0, color & 0x77FFFFFF, textStyle, screen.currentAlpha);
            } else {
                UiTextStyle textStyle = screen.textStyle(id);
                UiTextRenderer.drawLine(guiGraphics, screen.font, textStyle.component(displayText).getVisualOrderText(),
                        0, 0, color, textStyle, screen.currentAlpha);
            }
            guiGraphics.pose().popPose();
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;

            if (style.isPlain()) {
                GuiComponent.fill(poseStack, bx, by, bx + w, by + h, outlineColor);
                GuiComponent.fill(poseStack, bx + 1, by + 1, bx + w - 1, by + h - 1, bgColor);
            } else {
                float bw2 = style.borderWidth() > 0f ? style.borderWidth() : 1f;
                int bc = style.borderWidth() > 0f ? style.borderColor() : outlineColor;
                SprauteUiRender.panel(poseStack, bx, by, bx + w, by + h, bgColor,
                        new SprauteUiRender.Style(style.radius(), style.gradientStops(), style.gradientAngle(), bw2, bc, style.borderStops(), style.borderAngle(), style.shadowSize(),
                                style.outerBorderWidth(), style.outerBorderColor(), style.outerBorderStops(), style.outerBorderAngle()));
            }

            poseStack.pushPose();
            poseStack.translate(bx + 4, by + (h - screen.font.lineHeight * scale) / 2f, 0);
            poseStack.scale(scale, scale, 1f);

            String displayText = text;
            if ("password".equals(type)) {
                displayText = "*".repeat(text.length());
            }

            boolean active = screen.activeInputId != null && screen.activeInputId.equals(id);
            if (active && (System.currentTimeMillis() / 500) % 2 == 0) {
                displayText += "_";
            }

            if (displayText.isEmpty() && placeholder != null && !placeholder.isEmpty() && !active) {
                UiTextStyle textStyle = screen.textStyle(id);
                UiTextRenderer.drawLine(poseStack, screen.font, textStyle.component(placeholder).getVisualOrderText(),
                        0, 0, color & 0x77FFFFFF, textStyle, screen.currentAlpha);
            } else {
                UiTextStyle textStyle = screen.textStyle(id);
                UiTextRenderer.drawLine(poseStack, screen.font, textStyle.component(displayText).getVisualOrderText(),
                        0, 0, color, textStyle, screen.currentAlpha);
            }
            poseStack.popPose();
        }
        *///?}
    }

    private record ButtonW(String id, int x, int y, int w, int h, String label, String subLabel, int color, int hoverColor, String texture, String tooltip,
                           int labelWrap, String wrapMode, float labelScale, float subScale, int sliceBorders, float sliceScale,
                           int labelOffsetX, int labelOffsetY, String align,
                           List<Widget> hoverChildren, int hoverPw, int hoverPh, SprauteUiRender.Style style) implements Widget {
        ButtonW {
            if (hoverChildren == null) hoverChildren = List.of();
            if (style == null) style = SprauteUiRender.Style.NONE;
            if (align == null || align.isBlank()) align = "center";
            else align = align.toLowerCase();
        }

        boolean hasHoverPanel() {
            return hoverPw > 0 && hoverPh > 0 && !hoverChildren.isEmpty();
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override
        public String tooltip() {
            return tooltip;
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x;
            int ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;
            boolean over = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
            if (texture != null && !texture.isEmpty()) {
                ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
                if (over) {
                    guiGraphics.setColor(1.1f, 1.1f, 1.1f, 1f);
                }
                if (sliceBorders > 0) {
                    renderNineSlice(guiGraphics, rl, bx, by, w, h, sliceBorders, sliceScale, texture);
                } else {
                    int[] ts = texturePixelSize(rl);
                    SprauteGuiDraw.blitRegion(guiGraphics, rl, bx, by, w, h, 0, 0, ts[0], ts[1], ts[0], ts[1]);
                }
                guiGraphics.setColor(1f, 1f, 1f, 1f);
            } else if (style.isPlain()) {
                SprauteGuiDraw.fill(guiGraphics, bx, by, bx + w, by + h, over ? hoverColor : color);
            } else {
                SprauteUiRender.panel(guiGraphics.pose(), bx, by, bx + w, by + h, over ? hoverColor : color, style);
            }
            int pad = 3;
            UiTextStyle textStyle = screen.textStyle(id);
            if (labelWrap > 0 && label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                String renderSubLabel = subLabel != null ? subLabel.replace("&", "§") : null;
                float ls = labelScale > 0.05f ? labelScale : 1f;
                float ss = subScale > 0.05f ? subScale : 0.65f;
                boolean hasSub = renderSubLabel != null && !renderSubLabel.isEmpty();
                int effWrap = Math.max(4, (int) (labelWrap / ls));
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                        wrapUiText(screen.font, renderLabel, effWrap, wrapMode, textStyle);
                int nLines = lines.size();
                float titleBlockPx = nLines * screen.font.lineHeight * ls;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(bx + pad + labelOffsetX, by + pad + labelOffsetY, 0);
                guiGraphics.pose().scale(ls, ls, 1f);
                int lineY = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    float drawX = 0f;
                    if ("center".equals(align)) drawX = (effWrap - screen.font.width(line)) / 2f;
                    else if ("right".equals(align)) drawX = effWrap - screen.font.width(line);
                    UiTextRenderer.drawLine(guiGraphics, screen.font, line, drawX, lineY,
                            0xFFFFFFFF, textStyle, screen.currentAlpha);
                    lineY += screen.font.lineHeight;
                }
                guiGraphics.pose().popPose();
                if (hasSub) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(bx + pad + labelOffsetX, by + pad + labelOffsetY + titleBlockPx + 1, 0);
                    guiGraphics.pose().scale(ss, ss, 1f);
                    UiTextRenderer.drawLine(guiGraphics, screen.font, textStyle.component(renderSubLabel).getVisualOrderText(),
                            0, 0, 0xFFAAAAAA, textStyle, screen.currentAlpha);
                    guiGraphics.pose().popPose();
                }
            } else if (label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                int tw = screen.font.width(textStyle.component(renderLabel));
                int tx;
                if ("left".equals(align)) tx = bx + pad + labelOffsetX;
                else if ("right".equals(align)) tx = bx + w - pad - tw + labelOffsetX;
                else tx = bx + (w - tw) / 2 + labelOffsetX;
                UiTextRenderer.drawLine(guiGraphics, screen.font, textStyle.component(renderLabel).getVisualOrderText(),
                        tx, by + (h - 8) / 2f + labelOffsetY, 0xFFFFFFFF, textStyle, screen.currentAlpha);
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;
            boolean over = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
            if (texture != null && !texture.isEmpty()) {
                ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderColor(over ? 1.1f : 1f, over ? 1.1f : 1f, over ? 1.1f : 1f, 1f);
                RenderSystem.setShaderTexture(0, rl);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                if (sliceBorders > 0) {
                    renderNineSlice(poseStack, rl, bx, by, w, h, sliceBorders, sliceScale, texture);
                } else {
                    int[] ts = texturePixelSize(rl);
                    SprauteGuiDraw.blitRegion(poseStack, bx, by, w, h, 0, 0, ts[0], ts[1], ts[0], ts[1]);
                }
            } else if (style.isPlain()) {
                GuiComponent.fill(poseStack, bx, by, bx + w, by + h, over ? hoverColor : color);
            } else {
                SprauteUiRender.panel(poseStack, bx, by, bx + w, by + h, over ? hoverColor : color, style);
            }
            int pad = 3;
            UiTextStyle textStyle = screen.textStyle(id);
            if (labelWrap > 0 && label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                String renderSubLabel = subLabel != null ? subLabel.replace("&", "§") : null;
                float ls = labelScale > 0.05f ? labelScale : 1f;
                float ss = subScale > 0.05f ? subScale : 0.65f;
                boolean hasSub = renderSubLabel != null && !renderSubLabel.isEmpty();
                int effWrap = Math.max(4, (int) (labelWrap / ls));
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                        wrapUiText(screen.font, renderLabel, effWrap, wrapMode, textStyle);
                int nLines = lines.size();
                float titleBlockPx = nLines * screen.font.lineHeight * ls;
                poseStack.pushPose();
                poseStack.translate(bx + pad + labelOffsetX, by + pad + labelOffsetY, 0);
                poseStack.scale(ls, ls, 1f);
                int lineY = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    float drawX = 0f;
                    if ("center".equals(align)) drawX = (effWrap - screen.font.width(line)) / 2f;
                    else if ("right".equals(align)) drawX = effWrap - screen.font.width(line);
                    UiTextRenderer.drawLine(poseStack, screen.font, line, drawX, lineY,
                            0xFFFFFFFF, textStyle, screen.currentAlpha);
                    lineY += screen.font.lineHeight;
                }
                poseStack.popPose();
                if (hasSub) {
                    poseStack.pushPose();
                    poseStack.translate(bx + pad + labelOffsetX, by + pad + labelOffsetY + titleBlockPx + 1, 0);
                    poseStack.scale(ss, ss, 1f);
                    UiTextRenderer.drawLine(poseStack, screen.font, textStyle.component(renderSubLabel).getVisualOrderText(),
                            0, 0, 0xFFAAAAAA, textStyle, screen.currentAlpha);
                    poseStack.popPose();
                }
            } else if (label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                int tw = screen.font.width(textStyle.component(renderLabel));
                float tx;
                if ("left".equals(align)) tx = bx + pad + labelOffsetX;
                else if ("right".equals(align)) tx = bx + w - pad - tw + labelOffsetX;
                else tx = bx + (w - tw) / 2f + labelOffsetX;
                UiTextRenderer.drawLine(poseStack, screen.font, textStyle.component(renderLabel).getVisualOrderText(),
                        tx, by + (h - 8) / 2f + labelOffsetY, 0xFFFFFFFF, textStyle, screen.currentAlpha);
            }
        }
        *///?}
    }

    private static class ClipW implements Widget {
        final int x, y, w, h;
        final String tooltip;
        final String id;
        float alpha = 1.0f;
        final float feather;
        final float featherL, featherT, featherR, featherB;
        final boolean featherSmooth;
        final List<Widget> children = new ArrayList<>();
        private final UiScrollFreezeLayer freeze = new UiScrollFreezeLayer();

        ClipW(int x, int y, int w, int h, float alpha, String tooltip, String id) {
            this(x, y, w, h, alpha, tooltip, id, 0f, 1f, 1f, 1f, 1f, true);
        }

        ClipW(int x, int y, int w, int h, float alpha, String tooltip, String id,
              float feather, float featherL, float featherT, float featherR, float featherB, boolean featherSmooth) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.alpha = alpha;
            this.tooltip = tooltip; this.id = id;
            this.feather = Math.max(0f, feather);
            this.featherL = clamp01(featherL);
            this.featherT = clamp01(featherT);
            this.featherR = clamp01(featherR);
            this.featherB = clamp01(featherB);
            this.featherSmooth = featherSmooth;
        }

        private boolean feathered() {
            return feather > 0.25f && w >= 2 && h >= 2
                    && (featherL > 0.001f || featherT > 0.001f || featherR > 0.001f || featherB > 0.001f);
        }

        private float edgePx(float side) {
            return feather * Math.max(0f, Math.min(1f, side));
        }

        ClipW copyGeom(int nx, int ny, int nw, int nh, float na) {
            destroyFreeze();
            ClipW n = new ClipW(nx, ny, nw, nh, na, tooltip, id,
                    feather, featherL, featherT, featherR, featherB, featherSmooth);
            n.children.addAll(children);
            return n;
        }

        ClipW copyFeather(float f, float l, float t, float r, float b, boolean smooth) {
            destroyFreeze();
            ClipW n = new ClipW(x, y, w, h, alpha, tooltip, id, f, l, t, r, b, smooth);
            n.children.addAll(children);
            return n;
        }

        void invalidateFreeze() { freeze.invalidate(); }
        void destroyFreeze() { freeze.destroy(); }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }

        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        private int[] passMouse(int sx, int sy, int mouseX, int mouseY) {
            if (inRect(sx, sy, w, h, mouseX, mouseY)) return new int[]{mouseX, mouseY};
            return new int[]{-1_000_000, -1_000_000};
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            int[] pass = passMouse(sx, sy, mouseX, mouseY);
            int passMx = pass[0], passMy = pass[1];
            if (feathered()) {
                freeze.rebuild(w, h, 0f, 0, (fw, fh) -> {
                    GuiGraphics layer = new GuiGraphics(Minecraft.getInstance(), guiGraphics.bufferSource());
                    int localMx = passMx - sx;
                    int localMy = passMy - sy;
                    for (Widget child : children) {
                        if (child instanceof EntityW) continue;
                        child.render(screen, layer, 0, 0, localMx, localMy, partialTick);
                    }
                    layer.flush();
                });
                freeze.blitFeathered(guiGraphics, sx, sy, w, h, screen.currentAlpha,
                        edgePx(featherL), edgePx(featherT), edgePx(featherR), edgePx(featherB), featherSmooth);
                boolean anyEntity = false;
                for (Widget child : children) {
                    if (child instanceof EntityW) { anyEntity = true; break; }
                }
                if (anyEntity) {
                    pushScissor(sx, sy, sx + w, sy + h);
                    try {
                        for (Widget child : children) {
                            if (child instanceof EntityW) {
                                child.render(screen, guiGraphics, sx, sy, passMx, passMy, partialTick);
                            }
                        }
                    } finally {
                        popScissor();
                    }
                }
            } else {
                pushScissor(sx, sy, sx + w, sy + h);
                try {
                    for (Widget child : children) {
                        child.render(screen, guiGraphics, sx, sy, passMx, passMy, partialTick);
                    }
                } finally {
                    popScissor();
                }
            }
            screen.currentAlpha = oldAlpha;
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            int[] pass = passMouse(sx, sy, mouseX, mouseY);
            int passMx = pass[0], passMy = pass[1];
            if (feathered()) {
                freeze.rebuild(w, h, 0f, 0, (fw, fh) -> {
                    PoseStack layer = new PoseStack();
                    int localMx = passMx - sx;
                    int localMy = passMy - sy;
                    for (Widget child : children) {
                        if (child instanceof EntityW) continue;
                        child.render(screen, layer, 0, 0, localMx, localMy, partialTick);
                    }
                });
                freeze.blitFeathered(poseStack, sx, sy, w, h, screen.currentAlpha,
                        edgePx(featherL), edgePx(featherT), edgePx(featherR), edgePx(featherB), featherSmooth);
                boolean anyEntity = false;
                for (Widget child : children) {
                    if (child instanceof EntityW) { anyEntity = true; break; }
                }
                if (anyEntity) {
                    pushScissor(sx, sy, sx + w, sy + h);
                    try {
                        for (Widget child : children) {
                            if (child instanceof EntityW) {
                                child.render(screen, poseStack, sx, sy, passMx, passMy, partialTick);
                            }
                        }
                    } finally {
                        popScissor();
                    }
                }
            } else {
                pushScissor(sx, sy, sx + w, sy + h);
                try {
                    for (Widget child : children) {
                        child.render(screen, poseStack, sx, sy, passMx, passMy, partialTick);
                    }
                } finally {
                    popScissor();
                }
            }
            screen.currentAlpha = oldAlpha;
        }
        *///?}
    }

    private static class GroupW implements Widget {
        final int x, y, w, h;
        final String tooltip;
        final String id;
        float alpha = 1.0f;
        final List<Widget> children = new ArrayList<>();

        GroupW(int x, int y, int w, int h, float alpha, String tooltip, String id) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.alpha = alpha;
            this.tooltip = tooltip; this.id = id;
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }

        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, guiGraphics, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        *///?}
    }

    private static class DragW implements Widget {
        int x, y, w, h;
        float alpha = 1.0f;
        final String tooltip;
        final String id;
        boolean enabled;
        String axis;
        String group;
        String payload;
        boolean constrain;
        boolean revert;
        int threshold;
        final int parentW;
        final int parentH;
        final List<Widget> children = new ArrayList<>();

        DragW(int x, int y, int w, int h, float alpha, String tooltip, String id,
              boolean enabled, String axis, String group, String payload,
              boolean constrain, boolean revert, int threshold, int parentW, int parentH) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.alpha = alpha;
            this.tooltip = tooltip; this.id = id;
            this.enabled = enabled;
            this.axis = axis == null || axis.isBlank() ? "xy" : axis.trim().toLowerCase(Locale.ROOT);
            this.group = group == null ? "" : group;
            this.payload = payload == null ? "" : payload;
            this.constrain = constrain;
            this.revert = revert;
            this.threshold = Math.max(0, threshold);
            this.parentW = Math.max(1, parentW);
            this.parentH = Math.max(1, parentH);
        }

        boolean axisX() { return !"y".equals(axis); }
        boolean axisY() { return !"x".equals(axis); }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, guiGraphics, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            for (Widget child : children) {
                child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        *///?}
    }

    private static class DropW implements Widget {
        int x, y, w, h;
        float alpha = 1.0f;
        final String tooltip;
        final String id;
        boolean enabled;
        String group;
        boolean hot;
        final List<Widget> children = new ArrayList<>();

        DropW(int x, int y, int w, int h, float alpha, String tooltip, String id, boolean enabled, String group) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.alpha = alpha;
            this.tooltip = tooltip; this.id = id;
            this.enabled = enabled;
            this.group = group == null ? "" : group;
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }
        @Override public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        private void renderHot(SprauteScriptScreen screen, int sx, int sy, Object draw) {
            if (!hot || alpha <= 0f) return;
            int fill = applyAlpha(0x4400DD88, screen.currentAlpha * alpha);
            int line = applyAlpha(0xCC00FFAA, screen.currentAlpha * alpha);
            //? if >=1.20.1 {
            GuiGraphics g = (GuiGraphics) draw;
            SprauteGuiDraw.fill(g, sx, sy, sx + w, sy + h, fill);
            SprauteGuiDraw.fill(g, sx, sy, sx + w, sy + 1, line);
            SprauteGuiDraw.fill(g, sx, sy + h - 1, sx + w, sy + h, line);
            SprauteGuiDraw.fill(g, sx, sy, sx + 1, sy + h, line);
            SprauteGuiDraw.fill(g, sx + w - 1, sy, sx + w, sy + h, line);
            //?} else {
            /*PoseStack ps = (PoseStack) draw;
            GuiComponent.fill(ps, sx, sy, sx + w, sy + h, fill);
            GuiComponent.fill(ps, sx, sy, sx + w, sy + 1, line);
            GuiComponent.fill(ps, sx, sy + h - 1, sx + w, sy + h, line);
            GuiComponent.fill(ps, sx, sy, sx + 1, sy + h, line);
            GuiComponent.fill(ps, sx + w - 1, sy, sx + w, sy + h, line);
            *///?}
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            renderHot(screen, sx, sy, guiGraphics);
            for (Widget child : children) {
                child.render(screen, guiGraphics, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            renderHot(screen, sx, sy, poseStack);
            for (Widget child : children) {
                child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
            }
            screen.currentAlpha = oldAlpha;
        }
        *///?}
    }

    /** Cheap Y cull for scroll children using per-widget approximate height. */
    private static boolean isScrollChildVisible(Widget child, int viewportH, int contentOffsetY) {
        int cy = child.getY() + contentOffsetY;
        int ch = Math.max(8, child.approxHeight());
        return cy < viewportH + 4 && cy + ch > -4;
    }

    private static class ScrollW implements Widget {
        final int x, y, w, h, contentH;
        final int bgColor;
        final String tooltip;
        final String id;
        final boolean showBar;
        final boolean autoBar;
        final SprauteUiRender.Style style;
        final List<Widget> children = new ArrayList<>();
        float scrollOffset = 0;
        /** Viewport freeze: one FBO blit while scroll/hover unchanged. */
        private final UiScrollFreezeLayer freeze = new UiScrollFreezeLayer();

        ScrollW(int x, int y, int w, int h, int contentH, int bgColor, String tooltip, String id, boolean showBar, boolean autoBar) {
            this(x, y, w, h, contentH, bgColor, tooltip, id, showBar, autoBar, SprauteUiRender.Style.NONE);
        }

        ScrollW(int x, int y, int w, int h, int contentH, int bgColor, String tooltip, String id, boolean showBar, boolean autoBar, SprauteUiRender.Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.contentH = Math.max(h, contentH);
            this.bgColor = bgColor; this.tooltip = tooltip; this.id = id;
            this.showBar = showBar; this.autoBar = autoBar;
            this.style = style != null ? style : SprauteUiRender.Style.NONE;
        }

        void invalidateFreeze() {
            freeze.invalidate();
        }

        void destroyFreeze() {
            freeze.destroy();
        }

        private boolean freezeAllowed() {
            for (Widget child : children) {
                if (child instanceof EntityW) return false;
            }
            return w >= 4 && h >= 4 && !children.isEmpty();
        }

        private int hoverKey(SprauteScriptScreen screen, int sx, int sy, int mouseX, int mouseY) {
            int offsetY = -(int) scrollOffset;
            for (int i = 0; i < children.size(); i++) {
                Widget child = children.get(i);
                if (!(child instanceof ButtonW)) continue;
                if (!isScrollChildVisible(child, h, offsetY)) continue;
                if (child.contains(screen, sx, sy + offsetY, mouseX, mouseY)) return i + 1;
            }
            return 0;
        }

        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return h; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, h); }

        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int sx = ax0 + x, sy = ay0 + y;
            int currentBgColor = applyAlpha(bgColor, screen.currentAlpha);
            if ((currentBgColor & 0xFF000000) != 0) {
                if (style.isPlain()) {
                    SprauteGuiDraw.fill(guiGraphics, sx, sy, sx + w, sy + h, currentBgColor);
                } else {
                    SprauteUiRender.panel(guiGraphics.pose(), sx, sy, sx + w, sy + h, currentBgColor, styleWithAlpha(style, screen.currentAlpha));
                }
            }

            int offsetY = -(int) scrollOffset;
            if (freezeAllowed()) {
                int hk = hoverKey(screen, sx, sy, mouseX, mouseY);
                if (!freeze.matches(w, h, scrollOffset, hk)) {
                    freeze.rebuild(w, h, scrollOffset, hk, (fw, fh) -> {
                        GuiGraphics layer = new GuiGraphics(Minecraft.getInstance(), guiGraphics.bufferSource());
                        // Local mouse inside the freeze viewport (content origin at 0, offsetY).
                        int localMx = mouseX - sx;
                        int localMy = mouseY - sy;
                        for (Widget child : children) {
                            if (!isScrollChildVisible(child, fh, offsetY)) continue;
                            child.render(screen, layer, 0, offsetY, localMx, localMy, partialTick);
                        }
                        layer.flush();
                    });
                }
                freeze.blit(guiGraphics, sx, sy, w, h, screen.currentAlpha);
            } else {
                pushScissor(sx, sy, sx + w, sy + h);
                try {
                    for (Widget child : children) {
                        if (!isScrollChildVisible(child, h, offsetY)) continue;
                        child.render(screen, guiGraphics, sx, sy + offsetY, mouseX, mouseY, partialTick);
                    }
                } finally {
                    popScissor();
                }
            }

            boolean shouldShowBar = autoBar ? (contentH > h) : showBar;
            if (shouldShowBar && contentH > h) {
                int barW = 3;
                int barAreaH = h;
                float ratio = (float) h / contentH;
                int barH = Math.max(8, (int) (barAreaH * ratio));
                float maxScroll = contentH - h;
                float scrollPct = maxScroll > 0 ? scrollOffset / maxScroll : 0;
                int barY = sy + (int) ((barAreaH - barH) * scrollPct);
                int barX = sx + w - barW - 1;
                if (style.isPlain()) {
                    SprauteGuiDraw.fill(guiGraphics, barX, barY, barX + barW, barY + barH, applyAlpha(0x88AAAAAA, screen.currentAlpha));
                } else {
                    SprauteUiRender.fillRounded(guiGraphics.pose(), barX, barY, barX + barW, barY + barH,
                            barW * 0.5f, applyAlpha(0x88AAAAAA, screen.currentAlpha), applyAlpha(0x88AAAAAA, screen.currentAlpha), false);
                }
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int sx = ax0 + x, sy = ay0 + y;
            int currentBgColor = applyAlpha(bgColor, screen.currentAlpha);
            if ((currentBgColor & 0xFF000000) != 0) {
                if (style.isPlain()) {
                    GuiComponent.fill(poseStack, sx, sy, sx + w, sy + h, currentBgColor);
                } else {
                    SprauteUiRender.panel(poseStack, sx, sy, sx + w, sy + h, currentBgColor, styleWithAlpha(style, screen.currentAlpha));
                }
            }
            int offsetY = -(int) scrollOffset;
            // Freeze layer is 1.20+ first; 1.19 keeps culled live render.
            pushScissor(sx, sy, sx + w, sy + h);
            try {
                for (Widget child : children) {
                    if (!isScrollChildVisible(child, h, offsetY)) continue;
                    child.render(screen, poseStack, sx, sy + offsetY, mouseX, mouseY, partialTick);
                }
            } finally {
                popScissor();
            }
            boolean shouldShowBar = autoBar ? (contentH > h) : showBar;
            if (shouldShowBar && contentH > h) {
                int barW = 3;
                int barAreaH = h;
                float ratio = (float) h / contentH;
                int barH = Math.max(8, (int) (barAreaH * ratio));
                float maxScroll = contentH - h;
                float scrollPct = maxScroll > 0 ? scrollOffset / maxScroll : 0;
                int barY = sy + (int) ((barAreaH - barH) * scrollPct);
                int barX = sx + w - barW - 1;
                if (style.isPlain()) {
                    GuiComponent.fill(poseStack, barX, barY, barX + barW, barY + barH, applyAlpha(0x88AAAAAA, screen.currentAlpha));
                } else {
                    SprauteUiRender.fillRounded(poseStack, barX, barY, barX + barW, barY + barH,
                            barW * 0.5f, applyAlpha(0x88AAAAAA, screen.currentAlpha), applyAlpha(0x88AAAAAA, screen.currentAlpha), false);
                }
            }
        }
        *///?}
    }

    private record DividerW(int x, int y, int w, int color, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, w, 1); }
        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            SprauteGuiDraw.fill(guiGraphics, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + 1, color);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            GuiComponent.fill(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + 1, color);
        }
        *///?}
    }

    /** Visual background of a container slot (the real slot logic lives in SprauteContainerMenu). */
    private record SlotW(int x, int y, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, 18, 18); }
        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            drawSlotBg(guiGraphics, ax0 + x, ay0 + y, screen.currentAlpha);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            drawSlotBg(poseStack, ax0 + x, ay0 + y, screen.currentAlpha);
        }
        *///?}
    }

    /** Visual background of the 9x3 player inventory + hotbar (slot logic in SprauteContainerMenu). */
    private record PlayerInvW(int x, int y, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, 162, 76); }
        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    drawSlotBg(guiGraphics, ax0 + x + col * 18, ay0 + y + row * 18, screen.currentAlpha);
            for (int col = 0; col < 9; col++)
                drawSlotBg(guiGraphics, ax0 + x + col * 18, ay0 + y + 58, screen.currentAlpha);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    drawSlotBg(poseStack, ax0 + x + col * 18, ay0 + y + row * 18, screen.currentAlpha);
            for (int col = 0; col < 9; col++)
                drawSlotBg(poseStack, ax0 + x + col * 18, ay0 + y + 58, screen.currentAlpha);
        }
        *///?}
    }

    //? if >=1.20.1 {
    /** Vanilla-style inset slot: gray body, dark top-left edge, light bottom-right edge. */
    private static void drawSlotBg(GuiGraphics g, int sx, int sy, float alpha) {
        int body = applyAlpha(0xFF8B8B8B, alpha);
        int dark = applyAlpha(0xFF373737, alpha);
        int light = applyAlpha(0xFFFFFFFF, alpha);
        SprauteGuiDraw.fill(g, sx + 1, sy + 1, sx + 17, sy + 17, body);
        SprauteGuiDraw.fill(g, sx, sy, sx + 17, sy + 1, dark);
        SprauteGuiDraw.fill(g, sx, sy + 1, sx + 1, sy + 17, dark);
        SprauteGuiDraw.fill(g, sx + 17, sy + 1, sx + 18, sy + 18, light);
        SprauteGuiDraw.fill(g, sx + 1, sy + 17, sx + 17, sy + 18, light);
    }
    //?} else {
    /*private static void drawSlotBg(PoseStack ps, int sx, int sy, float alpha) {
        int body = applyAlpha(0xFF8B8B8B, alpha);
        int dark = applyAlpha(0xFF373737, alpha);
        int light = applyAlpha(0xFFFFFFFF, alpha);
        GuiComponent.fill(ps, sx + 1, sy + 1, sx + 17, sy + 17, body);
        GuiComponent.fill(ps, sx, sy, sx + 17, sy + 1, dark);
        GuiComponent.fill(ps, sx, sy + 1, sx + 1, sy + 17, dark);
        GuiComponent.fill(ps, sx + 17, sy + 1, sx + 18, sy + 18, light);
        GuiComponent.fill(ps, sx + 1, sy + 17, sx + 17, sy + 18, light);
    }
    *///?}

    private static ItemStack resolveCachedItemStack(String itemId) {
        String key = itemId == null || itemId.isEmpty() ? "minecraft:stone" : itemId;
        return ITEM_STACK_CACHE.computeIfAbsent(key, id ->
                org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.createStack(id, 1));
    }

    private record ItemW(int x, int y, int size, String itemId, String tooltip, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int approxHeight() { return size > 0 ? size : 16; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return getBaseOBB(ax0 + x, ay0 + y, size, size); }
        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + size && my >= ly && my < ly + size;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ItemStack stack = resolveCachedItemStack(itemId);
            if (stack.isEmpty()) return;

            int drawX = ax0 + x;
            int drawY = ay0 + y;
            int renderSize = size > 0 ? size : 16;

            if (renderSize == 16) {
                // Standard size: renderItem uses screen-space coords, no pose transform needed.
                guiGraphics.renderItem(stack, drawX, drawY);
            } else {
                // Non-standard size: scale via pose, render at (0,0) inside translated pose.
                float s = renderSize / 16f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(drawX, drawY, 150.0f);
                guiGraphics.pose().scale(s, s, 1.0f);
                guiGraphics.renderItem(stack, 0, 0);
                guiGraphics.pose().popPose();
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ItemStack stack = resolveCachedItemStack(itemId);
            if (stack.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            int drawX = ax0 + x;
            int drawY = ay0 + y;
            int renderSize = size > 0 ? size : 16;

            // Используем стандартный подход Forge 1.19.2 для рендера в кастомных GUI
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            poseStack.pushPose();
            poseStack.translate(0, 0, 150.0f); // Поверх всего

            // Поскольку renderAndDecorateItem не принимает PoseStack напрямую, мы масштабируем саму матрицу
            com.mojang.blaze3d.vertex.PoseStack mvStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            mvStack.pushPose();

            // Сдвиг и масштабирование
            mvStack.translate(drawX, drawY, 0);
            float s = renderSize / 16f;
            mvStack.scale(s, s, 1.0f);

            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();

            net.minecraft.client.renderer.entity.ItemRenderer itemRenderer = mc.getItemRenderer();
            float oldZ = itemRenderer.blitOffset;
            itemRenderer.blitOffset = 100.0F;

            // Рисуем
            itemRenderer.renderAndDecorateItem(stack, 0, 0);

            itemRenderer.blitOffset = oldZ;

            // Возвращаем все обратно
            mvStack.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            poseStack.popPose();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }
        *///?}
    }

    public static boolean disableEntityAnimations = false;
    public static boolean hideEntityNameTag = false;

    private static final Map<UUID, org.zonarstudio.spraute_engine.entity.SprauteNpcEntity> uiDummyCache = new HashMap<>();
    /** Config template for chat player head — same geo/anim as the built-in NPC. */
    private static final UUID UI_CHAT_NPC_TEMPLATE_ID = UUID.fromString("00000000-0000-4000-8000-0000000a0001");
    /** Render dummy cache key for skin avatars (separate from template entity uuid). */
    private static final UUID UI_SKIN_AVATAR_DUMMY_ID = UUID.fromString("00000000-0000-4000-8000-0000000a0002");

    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getChatNpcTemplate(Minecraft mc) {
        org.zonarstudio.spraute_engine.entity.SprauteNpcEntity tpl = uiDummyCache.get(UI_CHAT_NPC_TEMPLATE_ID);
        if (tpl == null) {
            tpl = new org.zonarstudio.spraute_engine.entity.SprauteNpcEntity(
                    org.zonarstudio.spraute_engine.entity.ModEntities.SPRAUTE_NPC.get(), mc.level);
            tpl.setModel(SprauteDefaultNpcAssets.MODEL);
            tpl.setTexture(SprauteDefaultNpcAssets.TEXTURE);
            tpl.setAnimation(SprauteDefaultNpcAssets.ANIMATION);
            tpl.setIdleAnim("idle");
            tpl.setWalkAnim("idle");
            uiDummyCache.put(UI_CHAT_NPC_TEMPLATE_ID, tpl);
        }
        return tpl;
    }

    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getOrCreateDummy(
            LivingEntity source, boolean noLookAt, boolean disableAnim, boolean noHurt, UUID skinPlayerUuid) {
        return getOrCreateDummy(source, null, noLookAt, disableAnim, noHurt, skinPlayerUuid);
    }

    /** Render-only client dummies for vanilla/modded mobs shown by "mob:<id>" entity refs. */
    private static final Map<String, LivingEntity> uiMobDummyCache = new HashMap<>();

    private static LivingEntity getOrCreateMobDummy(String typeId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || typeId == null || typeId.isBlank()) return null;
        String key = typeId.trim();
        LivingEntity mob = uiMobDummyCache.get(key);
        //? if >=1.20.1 {
        boolean stale = mob == null || mob.level() != mc.level;
        //?} else {
        /*boolean stale = mob == null || mob.level != mc.level;
        *///?}
        if (stale) {
            var type = net.minecraft.world.entity.EntityType.byString(key).orElse(null);
            Entity created = type != null ? type.create(mc.level) : null;
            if (!(created instanceof LivingEntity living)) return null;
            mob = living;
            uiMobDummyCache.put(key, mob);
        }
        return prepMobDummy(mob);
    }

    private static LivingEntity prepMobDummy(LivingEntity mob) {
        mob.setYRot(180f); mob.yRotO = 180f;
        mob.yHeadRot = 180f; mob.yHeadRotO = 180f;
        mob.yBodyRot = 180f; mob.yBodyRotO = 180f;
        mob.setXRot(0f); mob.xRotO = 0f;
        mob.hurtTime = 0; mob.deathTime = 0;
        mob.setCustomNameVisible(false);
        return mob;
    }

    /**
     * Build (or fetch from cache) a render-only NPC dummy directly from a model+texture spec.
     * Lets the GUI show any NPC's model even when the live entity isn't loaded (e.g. chat history).
     */
    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getOrCreateDummyFromSpec(
            String geo, String texture, String anim, String idle, boolean noLookAt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        String safeGeo = geo != null ? geo : SprauteDefaultNpcAssets.MODEL;
        String safeTex = texture != null ? texture : SprauteDefaultNpcAssets.TEXTURE;
        String safeAnim = anim != null && !anim.isEmpty() ? anim : SprauteDefaultNpcAssets.ANIMATION;
        String safeIdle = idle != null && !idle.isEmpty() ? idle : "idle";
        UUID key = UUID.nameUUIDFromBytes(("spec:" + safeGeo + "|" + safeTex + "|" + safeAnim + "|" + safeIdle).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.zonarstudio.spraute_engine.entity.SprauteNpcEntity dummy = uiDummyCache.get(key);
        if (dummy == null) {
            dummy = new org.zonarstudio.spraute_engine.entity.SprauteNpcEntity(
                    org.zonarstudio.spraute_engine.entity.ModEntities.SPRAUTE_NPC.get(), mc.level);
            dummy.setModel(safeGeo);
            dummy.setTexture(safeTex);
            dummy.setAnimation(safeAnim);
            dummy.setIdleAnim(safeIdle);
            dummy.setWalkAnim("");
            dummy.setTranslucent(true);
            dummy.setOpacity(1f);
            uiDummyCache.put(key, dummy);
        }
        float yaw = noLookAt ? 180f : dummy.getYRot();
        dummy.setYRot(yaw); dummy.yRotO = yaw;
        dummy.yHeadRot = yaw; dummy.yHeadRotO = yaw;
        dummy.yBodyRot = yaw; dummy.yBodyRotO = yaw;
        dummy.setXRot(0f); dummy.xRotO = 0f;
        dummy.hurtTime = 0; dummy.deathTime = 0;
        dummy.setCustomNameVisible(false);
        dummy.clearPlayerSkinOverlay();
        return dummy;
    }

    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getOrCreateDummy(
            LivingEntity source, UUID cacheKey, boolean noLookAt, boolean disableAnim, boolean noHurt,
            UUID skinPlayerUuid) {
        UUID key = cacheKey != null ? cacheKey : source.getUUID();
        Minecraft mc = Minecraft.getInstance();
        org.zonarstudio.spraute_engine.entity.SprauteNpcEntity dummy = uiDummyCache.get(key);
        if (dummy == null) {
            dummy = new org.zonarstudio.spraute_engine.entity.SprauteNpcEntity(
                    org.zonarstudio.spraute_engine.entity.ModEntities.SPRAUTE_NPC.get(), mc.level);
            uiDummyCache.put(key, dummy);
        }
        if (source instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
            dummy.setModel(npc.getModel());
            dummy.setTexture(npc.getTexture());
            dummy.setAnimation(npc.getAnimation());
            dummy.setIdleAnim(npc.getIdleAnim());
            dummy.setWalkAnim("");
            dummy.setTranslucent(npc.isTranslucent());
            dummy.setOpacity(npc.getOpacity());
            dummy.setCustomName(npc.getCustomName());
        }
        if (skinPlayerUuid != null) {
            dummy.setPlayerSkinOverlay(skinPlayerUuid);
        } else if (source instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
            String overlay = npc.getPlayerSkinOverlayUuid();
            if (overlay != null && !overlay.isEmpty()) {
                try {
                    dummy.setPlayerSkinOverlay(UUID.fromString(overlay));
                } catch (IllegalArgumentException ignored) {
                    dummy.clearPlayerSkinOverlay();
                }
            } else {
                dummy.clearPlayerSkinOverlay();
            }
        } else {
            dummy.clearPlayerSkinOverlay();
        }
        // renderEntityInInventory sets yBodyRot=180, yRot=180, yHeadRot=180 but does NOT touch *O fields.
        // Custom renderer lerps between *O and current, so *O must match to prevent interpolation artifacts.
        float neutralYaw = noLookAt ? 180f : source.getYRot();
        float neutralYawO = noLookAt ? 180f : source.yRotO;
        float neutralPitch = noLookAt ? 0f : source.getXRot();
        dummy.setYRot(neutralYaw);
        dummy.yRotO = neutralYawO;
        dummy.yHeadRot = neutralYaw;
        dummy.yHeadRotO = neutralYawO;
        dummy.yBodyRot = neutralYaw;
        dummy.yBodyRotO = neutralYawO;
        dummy.setXRot(neutralPitch);
        dummy.xRotO = noLookAt ? 0f : source.xRotO;
        dummy.hurtTime = 0;
        dummy.deathTime = 0;
        dummy.setCustomNameVisible(false);
        dummy.tickCount = disableAnim ? 0 : source.tickCount;
        dummy.attackAnim = 0f;
        dummy.oAttackAnim = 0f;
        return dummy;
    }

    public static void clearDummyCache() {
        uiDummyCache.clear();
    }

    /**
     * Renders an entity at the given screen position, fully controlling all rotation fields
     * (including *O variants) to prevent interpolation artifacts. This is a replacement for
     * InventoryScreen.renderEntityInInventory when we need precise control over entity pose.
     */
    //? if >=1.20.1 {
    private static void renderEntityDirect(GuiGraphics guiGraphics, int posX, int posY, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0f);
        float g = (float) Math.atan(mouseY / 40.0f);

        // Flush any pending GuiGraphics draws first, so the chat background (batched) is drawn BEFORE the
        // entity (immediate mode). This matches the 1.19.2 path, which renders fine in a HUD overlay.
        guiGraphics.flush();

        // Use ModelViewStack like vanilla InventoryScreen.renderEntityInInventory does in 1.20.1
        PoseStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.translate(posX, posY, 1050.0);
        modelViewStack.mulPoseMatrix(new org.joml.Matrix4f().scaling(scale, scale, -scale));
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();

        // Entity-local pose stack (separate from GuiGraphics pose)
        PoseStack poseStack = new PoseStack();
        SprauteRenderCompat.rotateZ(poseStack, 180.0f);
        SprauteRenderCompat.rotateX(poseStack, g * 20.0f);

        float bodyYaw = 180.0f + f * 20.0f;
        float yaw = 180.0f + f * 40.0f;
        float pitch = -g * 20.0f;

        float oldBodyRot = entity.yBodyRot;
        float oldBodyRotO = entity.yBodyRotO;
        float oldYRot = entity.getYRot();
        float oldYRotO = entity.yRotO;
        float oldXRot = entity.getXRot();
        float oldXRotO = entity.xRotO;
        float oldHeadRot = entity.yHeadRot;
        float oldHeadRotO = entity.yHeadRotO;

        entity.yBodyRot = bodyYaw;
        entity.yBodyRotO = bodyYaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;

        Lighting.setupForEntityInInventory();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        // The entity renders at ModelViewStack z=1050, while the GUI background was drawn at z=0.
        // GUI projection maps z=0 closer to the camera than z=1050, so GL_LEQUAL depth test would
        // reject entity fragments (entity is "behind" the background). Use GL_ALWAYS so the entity
        // always renders on top of previously-drawn GUI elements, matching 1.19.2 behaviour.
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
        com.mojang.blaze3d.systems.RenderSystem.runAsFancy(() -> {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, poseStack, bufferSource, 15728880);
        });
        bufferSource.endBatch();
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        dispatcher.setRenderShadow(true);

        entity.yBodyRot = oldBodyRot;
        entity.yBodyRotO = oldBodyRotO;
        entity.setYRot(oldYRot);
        entity.yRotO = oldYRotO;
        entity.setXRot(oldXRot);
        entity.xRotO = oldXRotO;
        entity.yHeadRot = oldHeadRot;
        entity.yHeadRotO = oldHeadRotO;

        Lighting.setupFor3DItems();

        modelViewStack.popPose();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();

        // Restore GUI render defaults that the entity render pipeline may have left dirty (shader tint,
        // depth mask, blend). Without this, the next batched GuiGraphics flush (text, items) can flicker.
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
    //?} else {
    /*private static void renderEntityDirect(int posX, int posY, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0f);
        float g = (float) Math.atan(mouseY / 40.0f);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(posX, posY, 1050.0);
        // Same as 1.20.1: scale on ModelView (incl. Z flip), not a second pose.scale(+z=1000).
        modelView.scale((float) scale, (float) scale, (float) -scale);
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        SprauteRenderCompat.rotateZ(poseStack, 180.0f);
        SprauteRenderCompat.rotateX(poseStack, g * 20.0f);

        float bodyYaw = 180.0f + f * 20.0f;
        float yaw = 180.0f + f * 40.0f;
        float pitch = -g * 20.0f;

        float oldBodyRot = entity.yBodyRot;
        float oldBodyRotO = entity.yBodyRotO;
        float oldYRot = entity.getYRot();
        float oldYRotO = entity.yRotO;
        float oldXRot = entity.getXRot();
        float oldXRotO = entity.xRotO;
        float oldHeadRot = entity.yHeadRot;
        float oldHeadRotO = entity.yHeadRotO;

        entity.yBodyRot = bodyYaw;
        entity.yBodyRotO = bodyYaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;

        Lighting.setupForEntityInInventory();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        // Depth: entity draws at ModelView z=1050; GUI was at z≈0. Without ALWAYS, LEQUAL can hide the model.
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
        RenderSystem.runAsFancy(() -> {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, poseStack, bufferSource, 15728880);
        });
        bufferSource.endBatch();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        dispatcher.setRenderShadow(true);

        entity.yBodyRot = oldBodyRot;
        entity.yBodyRotO = oldBodyRotO;
        entity.setYRot(oldYRot);
        entity.yRotO = oldYRotO;
        entity.setXRot(oldXRot);
        entity.xRotO = oldXRotO;
        entity.yHeadRot = oldHeadRot;
        entity.yHeadRotO = oldHeadRotO;

        Lighting.setupFor3DItems();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
    *///?}

    private static final java.util.Stack<int[]> scissorStack = new java.util.Stack<>();

    public static void pushScissor(int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        int sw = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int sh = Math.max(1, mc.getWindow().getGuiScaledHeight());
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(sw, x1);
        y1 = Math.min(sh, y1);
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        if (!scissorStack.isEmpty()) {
            int[] parent = scissorStack.peek();
            x0 = Math.max(x0, parent[0]);
            y0 = Math.max(y0, parent[1]);
            x1 = Math.min(x1, parent[2]);
            y1 = Math.min(y1, parent[3]);
            if (x1 < x0) x1 = x0;
            if (y1 < y0) y1 = y0;
        }
        scissorStack.push(new int[]{x0, y0, x1, y1});
        //? if >=1.20.1 {
        if (scissorGuiGraphics != null) {
            SprauteGuiDraw.enableScissor(scissorGuiGraphics, x0, y0, x1, y1);
        }
        //?} else {
        /*GuiComponent.enableScissor(x0, y0, x1, y1);
        *///?}
    }

    public static void popScissor() {
        if (!scissorStack.isEmpty()) {
            scissorStack.pop();
        }
        //? if >=1.20.1 {
        // GuiGraphics maintains its own scissor stack: enableScissor = push, disableScissor = pop
        // (the pop automatically re-applies the parent rect). Each pushScissor() called enableScissor
        // exactly once, so each popScissor() must call disableScissor() exactly once to stay balanced.
        // Re-enabling the parent here would push an EXTRA entry, leaving the scissor stuck enabled
        // after the outermost pop -> the rest of the frame gets clipped to the inner rect (black screen
        // except the crop region).
        if (scissorGuiGraphics == null) return;
        SprauteGuiDraw.disableScissor(scissorGuiGraphics);
        //?} else {
        /*if (scissorStack.isEmpty()) {
            GuiComponent.disableScissor();
        } else {
            int[] parent = scissorStack.peek();
            GuiComponent.enableScissor(parent[0], parent[1], parent[2], parent[3]);
        }
        *///?}
    }

    private static boolean peekDepthTest() {
        return org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
    }

    private static void restoreDepthTest(boolean wasOn) {
        if (wasOn) {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        } else {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }
    }

    /** 3D entity draw uses ModelViewStack and ignores nested GUI scissors — release them temporarily. */
    private static void runWithoutScissor(Runnable action) {
        java.util.List<int[]> saved = new java.util.ArrayList<>(scissorStack);
        while (!scissorStack.isEmpty()) {
            popScissor();
        }
        try {
            action.run();
        } finally {
            for (int[] r : saved) {
                pushScissor(r[0], r[1], r[2], r[3]);
            }
        }
    }

    /** Inventory-style entity preview scale in GUI pixels. */
    private static int computeEntityGuiScale(int w, int h, float scale, boolean autoScale, boolean headOnly,
            float cropL, float cropT, float cropR, float cropB) {
        if (autoScale) {
            float effW = Math.max(1f, w * (1f - cropL - cropR));
            float effH = Math.max(1f, h * (1f - cropT - cropB));
            float aspectW = headOnly ? 0.72f : 1.05f;
            float aspectH = headOnly ? 1.05f : 1.90f;
            float scFromW = effW / aspectW;
            float scFromH = effH / aspectH;
            return Math.max(8, (int) (Math.min(scFromW, scFromH) * scale));
        }
        float cell = Math.min(w, h);
        return headOnly
                ? Math.max(10, (int) (cell * 0.35f * scale))
                : Math.max(8, (int) (cell * 0.44f * scale));
    }

    /**
     * @param cropL..cropB доли 0–1 — сколько срезать слева, сверху, справа, снизу от ячейки {@code size}.
     * @param feetCrop при отрицательном вертикальном якоре — старая формула вертикали.
     * @param anchorX/Y 0–1 — точка якоря в полной ячейке; отрицательный {@code anchorY} включает режим {@code feet_crop}.
     */
    private record EntityW(
            int x, int y, int w, int h,
            float scale, UUID entityUuid, float feetCrop,
            String tooltip, String id,
            float cropL, float cropT, float cropR, float cropB,
            float anchorX, float anchorY, boolean disableAnim,
            boolean hideNameTag, boolean noLookAt, boolean noFollowCursor,
            boolean noHurtAnim, String[] renderBones, UUID skinPlayerUuid,
            String modelGeo, String modelTexture, String modelAnim, String modelIdle,
            boolean autoScale, boolean clipEntity, boolean alwaysRender
    ) implements Widget {
        @Override
        public String tooltip() {
            return tooltip;
        }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x;
            int ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }
        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int left = ax0 + x;
            int top = ay0 + y;
            int right = left + w;
            int bottom = top + h;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                return;
            }

            boolean useDummy = noLookAt || noFollowCursor || noHurtAnim || disableAnim || hideNameTag;
            LivingEntity renderTarget;
            if (entityUuid == null && skinPlayerUuid != null) {
                org.zonarstudio.spraute_engine.entity.SprauteNpcEntity avatar = getOrCreateDummy(
                        getChatNpcTemplate(mc), UI_SKIN_AVATAR_DUMMY_ID, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                if (avatar == null) {
                    SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                    return;
                }
                renderTarget = avatar;
                useDummy = true;
            } else {
                Entity e = entityUuid != null ? findEntityByUuid(mc.level, entityUuid) : null;
                LivingEntity living = e instanceof LivingEntity l ? l : null;
                boolean liveOk = living != null && living.isAlive();
                boolean hasNpcModel = modelGeo != null && !modelGeo.isEmpty()
                        && !"player:".equals(modelGeo) && !modelGeo.startsWith("mob:");

                if (liveOk) {
                    if (useDummy && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                        renderTarget = getOrCreateDummy(living, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                    } else {
                        renderTarget = living;
                    }
                } else if ("player:".equals(modelGeo)) {
                    if (mc.player == null) {
                        SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = mc.player;
                } else if (modelGeo != null && modelGeo.startsWith("mob:")) {
                    LivingEntity mob = getOrCreateMobDummy(modelGeo.substring(4));
                    if (mob == null) {
                        SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = mob;
                } else if (alwaysRender && hasNpcModel) {
                    // Dead / not spawned: draw from the stored model spec (default).
                    org.zonarstudio.spraute_engine.entity.SprauteNpcEntity spec =
                            getOrCreateDummyFromSpec(modelGeo, modelTexture, modelAnim, modelIdle, noLookAt);
                    if (spec == null) {
                        SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = spec;
                    useDummy = true;
                } else if (alwaysRender && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity deadNpc) {
                    // Dead corpse still in world, no modelGeo — clone a healthy dummy.
                    renderTarget = getOrCreateDummy(deadNpc, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                    useDummy = true;
                } else {
                    SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                    return;
                }
            }

            // Only scissor when there is actual crop or clipEntity is explicitly requested.
            // Without crop the scissor equals the widget box and will clip head-only renders
            // whose cy intentionally overflows the widget bounds.
            boolean hasCrop = cropL > 0f || cropT > 0f || cropR > 0f || cropB > 0f;
            boolean doScissor = hasCrop || clipEntity;
            float sx0f = left + w * cropL;
            float sy0f = top + h * cropT;
            float sx1f = left + w * (1f - cropR);
            float sy1f = top + h * (1f - cropB);
            int sx0 = (int) Math.floor(sx0f);
            int sy0 = (int) Math.floor(sy0f);
            int sx1 = (int) Math.ceil(sx1f);
            int sy1 = (int) Math.ceil(sy1f);
            if (doScissor) pushScissor(sx0, sy0, sx1, sy1);
            try {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = renderBones;
                }

                int cx = (int) (left + w * anchorX);
                int cy;
                if (anchorY == -1f) {
                    // Legacy feetCrop auto-mode (no anchor_y specified)
                    float t = Math.min(1f, Math.max(0f, feetCrop));
                    float anchorFrac = 0.48f + t * 0.20f;
                    cy = top + (int) (h * anchorFrac);
                } else {
                    // anchor_y is unrestricted: 0=top, 1=bottom, <0=above box, >1=below box
                    cy = (int) (top + h * anchorY);
                }
                boolean headOnly = renderBones != null && renderBones.length > 0;
                int sc = computeEntityGuiScale(w, h, scale, autoScale, headOnly, cropL, cropT, cropR, cropB);
                if (headOnly) {
                    // The head bone sits ~26 bedrock-px above the model origin (feet), so with only the
                    // head rendered the visible head lands far above `cy`. Push cy down by that offset
                    // (scaled) so the head starts inside the box and anchor_y (0=top .. 1=bottom) actually
                    // moves it across the visible area.
                    cy += Math.round(HEAD_ONLY_ORIGIN_OFFSET_PX / 16f * sc);
                }

                boolean prevDisable = SprauteScriptScreen.disableEntityAnimations;
                boolean prevHideName = SprauteScriptScreen.hideEntityNameTag;
                SprauteScriptScreen.disableEntityAnimations = disableAnim;
                SprauteScriptScreen.hideEntityNameTag = hideNameTag;

                boolean dontFollow = noLookAt || noFollowCursor;
                float lookX = dontFollow ? 0f : (float) cx - mouseX;
                float lookY = dontFollow ? 0f : (float) (cy - 50) - mouseY;

                final int drawCx = cx;
                final int drawCy = cy;
                final int drawSc = sc;
                final LivingEntity drawTarget = renderTarget;
                final boolean drawDummy = useDummy;
                final boolean useDirectRender = drawDummy
                        || headOnly
                        || (modelGeo != null && !modelGeo.isEmpty()
                            && !"player:".equals(modelGeo) && !modelGeo.startsWith("mob:"))
                        || skinPlayerUuid != null;

                Runnable drawEntity = () -> {
                    if (useDirectRender) {
                        renderEntityDirect(guiGraphics, drawCx, drawCy, drawSc, lookX, lookY, drawTarget);
                    } else {
                        float f = (float) Math.atan(lookX / 40.0f);
                        float g = (float) Math.atan(lookY / 40.0f);
                        Quaternionf pose = new Quaternionf().rotateZYX(0.0f, (float) Math.PI - f * 20.0f * ((float) Math.PI / 180.0f), (float) Math.PI);
                        Quaternionf camera = new Quaternionf().rotateX(g * 20.0f * ((float) Math.PI / 180.0f));
                        InventoryScreen.renderEntityInInventory(guiGraphics, drawCx, drawCy, drawSc, pose, camera, drawTarget);
                    }
                };

                // Flush batched 2D (bg) first, then 3D. Restore overlay depth-off so later
                // text (name) paints on top of the head instead of being covered by GL_ALWAYS.
                guiGraphics.flush();
                boolean depthWasOn = peekDepthTest();
                if (clipEntity || !scissorStack.isEmpty()) {
                    drawEntity.run();
                } else {
                    runWithoutScissor(drawEntity);
                }
                restoreDepthTest(depthWasOn);

                SprauteScriptScreen.disableEntityAnimations = prevDisable;
                SprauteScriptScreen.hideEntityNameTag = prevHideName;
            } finally {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = null;
                }
                if (doScissor) popScissor();
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int left = ax0 + x;
            int top = ay0 + y;
            int right = left + w;
            int bottom = top + h;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                return;
            }

            boolean useDummy = noLookAt || noFollowCursor || noHurtAnim || disableAnim || hideNameTag;
            LivingEntity renderTarget;
            if (entityUuid == null && skinPlayerUuid != null) {
                org.zonarstudio.spraute_engine.entity.SprauteNpcEntity avatar = getOrCreateDummy(
                        getChatNpcTemplate(mc), UI_SKIN_AVATAR_DUMMY_ID, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                if (avatar == null) {
                    GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                    return;
                }
                renderTarget = avatar;
                useDummy = true;
            } else {
                Entity e = entityUuid != null ? findEntityByUuid(mc.level, entityUuid) : null;
                LivingEntity living = e instanceof LivingEntity l ? l : null;
                boolean liveOk = living != null && living.isAlive();
                boolean hasNpcModel = modelGeo != null && !modelGeo.isEmpty()
                        && !"player:".equals(modelGeo) && !modelGeo.startsWith("mob:");

                if (liveOk) {
                    if (useDummy && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                        renderTarget = getOrCreateDummy(living, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                    } else {
                        renderTarget = living;
                    }
                } else if ("player:".equals(modelGeo)) {
                    if (mc.player == null) {
                        GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = mc.player;
                } else if (modelGeo != null && modelGeo.startsWith("mob:")) {
                    LivingEntity mob = getOrCreateMobDummy(modelGeo.substring(4));
                    if (mob == null) {
                        GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = mob;
                } else if (alwaysRender && hasNpcModel) {
                    org.zonarstudio.spraute_engine.entity.SprauteNpcEntity spec =
                            getOrCreateDummyFromSpec(modelGeo, modelTexture, modelAnim, modelIdle, noLookAt);
                    if (spec == null) {
                        GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = spec;
                    useDummy = true;
                } else if (alwaysRender && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity deadNpc) {
                    renderTarget = getOrCreateDummy(deadNpc, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                    useDummy = true;
                } else {
                    GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                    return;
                }
            }

            // Match 1.20.1: only scissor when crop or clipEntity — always-scissor clipped geo heads off-screen.
            boolean hasCrop = cropL > 0f || cropT > 0f || cropR > 0f || cropB > 0f;
            boolean doScissor = hasCrop || clipEntity;
            float sx0f = left + w * cropL;
            float sy0f = top + h * cropT;
            float sx1f = left + w * (1f - cropR);
            float sy1f = top + h * (1f - cropB);
            int sx0 = (int) Math.floor(sx0f);
            int sy0 = (int) Math.floor(sy0f);
            int sx1 = (int) Math.ceil(sx1f);
            int sy1 = (int) Math.ceil(sy1f);
            if (doScissor) pushScissor(sx0, sy0, sx1, sy1);
            try {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = renderBones;
                }

                int cx = (int) (left + w * anchorX);
                int cy;
                if (anchorY == -1f) {
                    float t = Math.min(1f, Math.max(0f, feetCrop));
                    float anchorFrac = 0.48f + t * 0.20f;
                    cy = top + (int) (h * anchorFrac);
                } else {
                    cy = (int) (top + h * anchorY);
                }
                boolean headOnly = renderBones != null && renderBones.length > 0;
                int sc = computeEntityGuiScale(w, h, scale, autoScale, headOnly, cropL, cropT, cropR, cropB);
                if (headOnly) {
                    cy += Math.round(HEAD_ONLY_ORIGIN_OFFSET_PX / 16f * sc);
                }

                boolean prevDisable = SprauteScriptScreen.disableEntityAnimations;
                boolean prevHideName = SprauteScriptScreen.hideEntityNameTag;
                SprauteScriptScreen.disableEntityAnimations = disableAnim;
                SprauteScriptScreen.hideEntityNameTag = hideNameTag;

                boolean dontFollow = noLookAt || noFollowCursor;
                float lookX = dontFollow ? 0f : (float) cx - mouseX;
                float lookY = dontFollow ? 0f : (float) (cy - 50) - mouseY;

                final int drawCx = cx;
                final int drawCy = cy;
                final int drawSc = sc;
                final LivingEntity drawTarget = renderTarget;
                final boolean drawDummy = useDummy;
                // Geo / skin / dummy / bone-filter → direct path (InventoryScreen breaks custom geo on 1.19).
                final boolean useDirectRender = drawDummy
                        || headOnly
                        || (modelGeo != null && !modelGeo.isEmpty()
                            && !"player:".equals(modelGeo) && !modelGeo.startsWith("mob:"))
                        || skinPlayerUuid != null
                        || renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;

                Runnable drawEntity = () -> {
                    if (useDirectRender) {
                        renderEntityDirect(drawCx, drawCy, drawSc, lookX, lookY, drawTarget);
                    } else {
                        InventoryScreen.renderEntityInInventory(drawCx, drawCy, drawSc, lookX, lookY, drawTarget);
                    }
                };

                boolean depthWasOn = peekDepthTest();
                if (clipEntity || !scissorStack.isEmpty()) {
                    drawEntity.run();
                } else {
                    runWithoutScissor(drawEntity);
                }
                restoreDepthTest(depthWasOn);

                SprauteScriptScreen.disableEntityAnimations = prevDisable;
                SprauteScriptScreen.hideEntityNameTag = prevHideName;
            } finally {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = null;
                }
                if (doScissor) popScissor();
            }
        }
        *///?}
    }

    public static void openOverlay(String json) {
        openOverlay("", json);
    }

    public static void openOverlay(String overlayId, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String id = (overlayId != null && !overlayId.isEmpty()) ? overlayId : "";
            if (id.isEmpty() && root.has("id")) {
                id = root.get("id").getAsString();
            }
            boolean chatOverlay = "spraute_chat_bar".equals(id)
                    || "spraute_chat".equals(id)
                    || id.startsWith("spraute_chat_");
            SprauteScriptScreen previous = null;
            if (chatOverlay) {
                activeOverlays.entrySet().removeIf(e -> {
                    boolean remove = "spraute_chat_bar".equals(e.getKey())
                            || "spraute_chat".equals(e.getKey())
                            || e.getKey().startsWith("spraute_chat_");
                    if (remove) e.getValue().disposeMedia();
                    return remove;
                });
                // Drop only stale fade-OUT patches; keep fade-in (~ANIM:...:1.0).
                pendingWidgetPatches.removeIf(p ->
                        "chat_clip".equals(p.widgetId)
                                && p.field != null && "alpha".equalsIgnoreCase(p.field.trim())
                                && p.value != null && p.value.startsWith("~ANIM:")
                                && (p.value.endsWith(":0.0") || p.value.endsWith(":0")));
            } else if (!id.isEmpty()) {
                previous = activeOverlays.remove(id);
            }
            java.util.Map<String, UiVideoPlayer> reuse = takeVideoPlayers(previous, root);
            SprauteScriptScreen overlay = new SprauteScriptScreen(root, true, reuse);
            if (previous != null) previous.disposeMedia();
            Minecraft mc = Minecraft.getInstance();
            overlay.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            if (id.isEmpty()) id = "_default";
            activeOverlays.put(id, overlay);
            activeOverlay = overlay;
            LOGGER.info("[CHAT-CLIENT] openOverlay id='{}' chatOverlay={} activeOverlays={} pending={} screen={}",
                    id, chatOverlay, activeOverlays.keySet(), pendingWidgetPatches.size(),
                    mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
            flushPendingWidgetPatches(overlay);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("SprauteOverlay").error(
                    "[Spraute] openOverlay failed (id={}): {}", overlayId, json, e);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[Spraute] Overlay error: " + e.getClass().getSimpleName()
                                + (e.getMessage() != null ? " " + e.getMessage() : "")), false);
            }
        }
    }

    public static void closeOverlayIfActive() {
        activeOverlays.values().forEach(SprauteScriptScreen::disposeMedia);
        activeOverlays.clear();
        activeOverlay = null;
        uiDummyCache.clear();
    }

    public static void closeOverlay(String overlayId) {
        LOGGER.info("[CHAT-CLIENT] closeOverlay id='{}' activeOverlaysBefore={}", overlayId, activeOverlays.keySet());
        if (overlayId == null || overlayId.isEmpty()) {
            closeOverlayIfActive();
            return;
        }
        SprauteScriptScreen removed = activeOverlays.remove(overlayId);
        if (removed != null) removed.disposeMedia();
        if (activeOverlays.isEmpty()) {
            activeOverlay = null;
        } else {
            activeOverlay = activeOverlays.values().stream().reduce((a, b) -> b).orElse(null);
        }
    }

    /**
     * Active overlays sorted by layer (ascending, ties keep open order).
     * belowHud: TRUE = only layers &lt; 0, FALSE = only layers &gt;= 0, null = all.
     */
    private static java.util.List<SprauteScriptScreen> overlaysSorted(Boolean belowHud) {
        java.util.List<SprauteScriptScreen> list = activeOverlays.entrySet().stream()
                .filter(entry -> !hideSceneOverlayInEditor(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (belowHud != null) list.removeIf(o -> (o.overlayLayer < 0) != belowHud);
        list.sort(java.util.Comparator.comparingInt(o -> o.overlayLayer));
        return list;
    }

    private static boolean hideSceneOverlayInEditor(String overlayId) {
        return overlayId != null
                && overlayId.startsWith("scene:")
                && Minecraft.getInstance().screen instanceof
                        org.zonarstudio.spraute_engine.client.editor.SprauteEngineEditorScreen;
    }

    /** HUD overlays skip the world HUD pass while a Screen is open — paint them after the screen. */
    private static boolean shouldPaintOverlaysOnScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof ChatScreen) return true;
        return screen instanceof SprauteScriptScreen ss && !ss.hudOverlay;
    }

    /**
     * Vanilla {@code ChatComponent} draws around pose Z≈50. Our overlays default to Z=0, and
     * translucent GuiGraphics batches can flush after HUD text — so the chat looks "on top"
     * of any transparent overlay even when we render later in {@code RenderGuiEvent.Post}.
     * Lift layer&gt;=0 overlays above the HUD stack.
     */
    private static final float HUD_OVERLAY_Z = 400.0f;

    //? if >=1.20.1 {
    private static void renderActiveOverlays(Minecraft mc, float partialTick, GuiGraphics guiGraphics) {
        renderActiveOverlays(mc, partialTick, guiGraphics, null);
    }

    private static void renderActiveOverlays(Minecraft mc, float partialTick, GuiGraphics guiGraphics, Boolean belowHud) {
        java.util.List<SprauteScriptScreen> overlays = overlaysSorted(belowHud);
        if (overlays.isEmpty()) return;

        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        int imx = (int) mouseX;
        int imy = (int) mouseY;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        scissorGuiGraphics = guiGraphics;

        boolean lifted = false;
        for (SprauteScriptScreen overlay : overlays) {
            boolean wantLift = overlay.overlayLayer >= 0;
            if (wantLift && !lifted) {
                guiGraphics.flush();
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0F, 0.0F, HUD_OVERLAY_Z);
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                lifted = true;
            } else if (!wantLift && lifted) {
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                guiGraphics.flush();
                guiGraphics.pose().popPose();
                lifted = false;
            }

            overlay.left = (sw - overlay.panelW) / 2;
            overlay.top = (sh - overlay.panelH) / 2;
            if (overlay.root.has("x")) {
                int rawX = readRootExtent(overlay.root, "x", sw, overlay.left);
                overlay.left = rawX < 0 ? sw + rawX - overlay.panelW : rawX;
            }
            if (overlay.root.has("y")) {
                int rawY = readRootExtent(overlay.root, "y", sh, overlay.top);
                overlay.top = rawY < 0 ? sh + rawY - overlay.panelH : rawY;
            }

            overlay.processAnimations();

            if (overlay.bgArgb != 0) {
                if (overlay.rootStyle.isPlain()) {
                    SprauteGuiDraw.fill(guiGraphics, overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
                } else {
                    SprauteUiRender.panel(guiGraphics.pose(), overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb, overlay.rootStyle);
                }
            }

            for (Widget w : overlay.widgets) {
                w.render(overlay, guiGraphics, overlay.left, overlay.top, imx, imy, partialTick);
            }
        }
        if (lifted) {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            guiGraphics.flush();
            guiGraphics.pose().popPose();
        }
    }
    //?} else {
    /*private static void renderActiveOverlays(Minecraft mc, float partialTick, PoseStack poseStack) {
        renderActiveOverlays(mc, partialTick, poseStack, null);
    }

    private static void renderActiveOverlays(Minecraft mc, float partialTick, PoseStack poseStack, Boolean belowHud) {
        if (mc.screen instanceof SprauteScriptScreen) return;
        java.util.List<SprauteScriptScreen> overlays = overlaysSorted(belowHud);
        if (overlays.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean lifted = false;

        for (SprauteScriptScreen overlay : overlays) {
            boolean wantLift = overlay.overlayLayer >= 0;
            if (wantLift && !lifted) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.0F, HUD_OVERLAY_Z);
                RenderSystem.disableDepthTest();
                lifted = true;
            } else if (!wantLift && lifted) {
                RenderSystem.enableDepthTest();
                poseStack.popPose();
                lifted = false;
            }

            overlay.left = (sw - overlay.panelW) / 2;
            overlay.top = (sh - overlay.panelH) / 2;
            if (overlay.root.has("x")) {
                int rawX = readRootExtent(overlay.root, "x", sw, overlay.left);
                overlay.left = rawX < 0 ? sw + rawX - overlay.panelW : rawX;
            }
            if (overlay.root.has("y")) {
                int rawY = readRootExtent(overlay.root, "y", sh, overlay.top);
                overlay.top = rawY < 0 ? sh + rawY - overlay.panelH : rawY;
            }

            overlay.processAnimations();

            if (overlay.bgArgb != 0) {
                if (overlay.rootStyle.isPlain()) {
                    GuiComponent.fill(poseStack, overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
                } else {
                    SprauteUiRender.panel(poseStack, overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb, overlay.rootStyle);
                }
            }

            for (Widget w : overlay.widgets) {
                w.render(overlay, poseStack, overlay.left, overlay.top, 0, 0, partialTick);
            }
        }
        if (lifted) {
            RenderSystem.enableDepthTest();
            poseStack.popPose();
        }
    }
    *///?}

    // ---- editor canvas preview ------------------------------------------
    // The scene editor renders CANVAS nodes with the exact same widget code the
    // game uses, so fonts, styles and all inspector parameters match runtime.

    private static final Map<String, SprauteScriptScreen> editorPreviews = new java.util.HashMap<>();
    private static final Map<String, String> editorPreviewJson = new java.util.HashMap<>();

    public static void clearEditorPreviews() {
        editorPreviews.values().forEach(SprauteScriptScreen::disposeMedia);
        editorPreviews.clear();
        editorPreviewJson.clear();
    }

    public static void retainEditorPreviews(java.util.Set<String> activeKeys) {
        java.util.Set<String> keep = activeKeys == null ? java.util.Set.of() : activeKeys;
        // World canvases share this cache but manage their own lifetime (WorldCanvasTextureManager).
        String worldPrefix = WorldCanvasTextureManager.KEY_PREFIX;
        editorPreviews.entrySet().removeIf(entry -> {
            if (keep.contains(entry.getKey()) || entry.getKey().startsWith(worldPrefix)) return false;
            entry.getValue().disposeMedia();
            return true;
        });
        editorPreviewJson.keySet().removeIf(k -> !keep.contains(k) && !k.startsWith(worldPrefix));
    }

    private static SprauteScriptScreen editorPreviewFor(String key, String json) {
        SprauteScriptScreen cached = editorPreviews.get(key);
        if (cached != null && json.equals(editorPreviewJson.get(key))) return cached;
        JsonObject next;
        try {
            next = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return cached;
        }
        if (cached != null && sameUiExceptLayout(cached.root, next)) {
            cached.root.remove("x");
            cached.root.remove("y");
            if (next.has("x")) cached.root.add("x", next.get("x"));
            if (next.has("y")) cached.root.add("y", next.get("y"));
            cached.applyEditorWidgetLayout(next);
            editorPreviewJson.put(key, json);
            return cached;
        }
        try {
            java.util.Map<String, UiVideoPlayer> reuse = takeVideoPlayers(cached, next);
            SprauteScriptScreen overlay = new SprauteScriptScreen(next, true, reuse);
            Minecraft mc = Minecraft.getInstance();
            overlay.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            if (cached != null) cached.disposeMedia();
            editorPreviews.put(key, overlay);
            editorPreviewJson.put(key, json);
            return overlay;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Actual GUI-scaled bounds used by the cached editor preview.
     * The 2D gizmo consumes these instead of duplicating widget layout math.
     */
    public static float[] editorPreviewBounds(String key, String widgetId) {
        SprauteScriptScreen preview = editorPreviews.get(key);
        if (preview == null) return null;
        if (widgetId == null) {
            return new float[]{preview.left, preview.top, preview.panelW, preview.panelH};
        }
        return preview.findEditorWidgetBounds(preview.widgets, widgetId, preview.left, preview.top);
    }

    private float[] findEditorWidgetBounds(List<Widget> list, String widgetId, int ax0, int ay0) {
        for (Widget wrapped : list) {
            Widget widget = wrapped instanceof RotatedW rotated ? rotated.child : wrapped;
            if (widgetId.equals(wrapped.getId()) || widgetId.equals(widget.getId())) {
                float[] obb = widget.getOBB(this, ax0, ay0);
                if (obb != null) {
                    float minX = obb[0], maxX = obb[0], minY = obb[1], maxY = obb[1];
                    for (int i = 2; i < obb.length; i += 2) {
                        minX = Math.min(minX, obb[i]);
                        maxX = Math.max(maxX, obb[i]);
                        minY = Math.min(minY, obb[i + 1]);
                        maxY = Math.max(maxY, obb[i + 1]);
                    }
                    return new float[]{minX, minY, maxX - minX, maxY - minY};
                }
            }

            List<Widget> children = null;
            int childX = ax0 + widget.getX();
            int childY = ay0 + widget.getY();
            if (widget instanceof RectW rect) children = rect.children;
            else if (widget instanceof ScrollW scroll) {
                children = scroll.children;
                childY -= (int) scroll.scrollOffset;
            } else if (widget instanceof ClipW clip) children = clip.children;
            else if (widget instanceof GroupW group) children = group.children;
            else if (widget instanceof DragW dragWidget) children = dragWidget.children;
            else if (widget instanceof DropW drop) children = drop.children;
            if (children != null) {
                float[] found = findEditorWidgetBounds(children, widgetId, childX, childY);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Move matching players out of {@code cached} so a rebuild does not restart playback. */
    private static java.util.Map<String, UiVideoPlayer> takeVideoPlayers(SprauteScriptScreen cached, JsonObject next) {
        java.util.Map<String, UiVideoPlayer> reuse = new java.util.HashMap<>();
        if (cached == null || next == null) return reuse;
        java.util.Set<String> ids = new java.util.HashSet<>();
        collectVideoWidgetIds(next, ids);
        for (String id : ids) {
            UiVideoPlayer player = cached.videoPlayers.remove(id);
            if (player != null) reuse.put(id, player);
        }
        return reuse;
    }

    /** True when JSON differs only by root x/y and widget x/y/w/h (gizmo drag). */
    private static boolean sameUiExceptLayout(JsonObject a, JsonObject b) {
        JsonObject ca = a.deepCopy();
        JsonObject cb = b.deepCopy();
        ca.remove("x");
        ca.remove("y");
        cb.remove("x");
        cb.remove("y");
        stripWidgetLayout(ca);
        stripWidgetLayout(cb);
        return ca.equals(cb);
    }

    private static void stripWidgetLayout(JsonObject object) {
        for (String key : new String[]{"widgets", "children"}) {
            if (!object.has(key) || !object.get(key).isJsonArray()) continue;
            for (JsonElement element : object.getAsJsonArray(key)) {
                if (!element.isJsonObject()) continue;
                JsonObject child = element.getAsJsonObject();
                child.remove("x");
                child.remove("y");
                child.remove("w");
                child.remove("h");
                stripWidgetLayout(child);
            }
        }
    }

    private void applyEditorWidgetLayout(JsonObject node) {
        if (node.has("widgets") && node.get("widgets").isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray("widgets")) {
                if (element.isJsonObject()) {
                    applyEditorWidgetLayoutInner(element.getAsJsonObject(), panelW, panelH, "top_left");
                }
            }
        }
        if (node.has("children") && node.get("children").isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray("children")) {
                if (element.isJsonObject()) {
                    applyEditorWidgetLayoutInner(element.getAsJsonObject(), panelW, panelH, "top_left");
                }
            }
        }
    }

    private void applyEditorWidgetLayoutInner(JsonObject node, int pw, int ph, String parentAlign) {
        int prevW = patchParentW;
        int prevH = patchParentH;
        patchParentW = pw;
        patchParentH = ph;
        try {
            if (node.has("id") && node.has("type")) {
                String id = node.get("id").getAsString();
                if (id != null && !id.isEmpty()) {
                    int ww = node.has("w") ? readCoord(node, "w", pw) : 0;
                    int hh = node.has("h") ? readCoord(node, "h", ph) : 0;
                    if (node.has("size")) {
                        int sz = readCoord(node, "size", pw);
                        if (!node.has("w")) ww = sz;
                        if (!node.has("h")) hh = sz;
                    }
                    int x = node.has("x") ? readCoord(node, "x", pw) : 0;
                    int y = node.has("y") ? readCoord(node, "y", ph) : 0;
                    if (!UiChildAlign.isOrigin(parentAlign)) {
                        x = Math.round(UiChildAlign.axisX(parentAlign) * pw + x - UiChildAlign.axisX(parentAlign) * ww);
                        y = Math.round(UiChildAlign.axisY(parentAlign) * ph + y - UiChildAlign.axisY(parentAlign) * hh);
                        applyWidgetPatch(id, "x", Integer.toString(x));
                        applyWidgetPatch(id, "y", Integer.toString(y));
                    } else {
                        patchLayoutField(id, "x", node);
                        patchLayoutField(id, "y", node);
                    }
                    patchLayoutField(id, "w", node);
                    patchLayoutField(id, "h", node);
                    if (node.has("size")) patchLayoutField(id, "size", node);
                }
            }
            int cw = node.has("w") ? readCoord(node, "w", pw) : pw;
            int ch = node.has("h") ? readCoord(node, "h", ph) : ph;
            if (node.has("size")) {
                int sz = readCoord(node, "size", pw);
                if (!node.has("w")) cw = sz;
                if (!node.has("h")) ch = sz;
            }
            String type = node.has("type") ? node.get("type").getAsString() : "";
            if ("scroll".equals(type) && node.has("contentH")) {
                ch = Math.max(ch, readCoord(node, "contentH", ph));
            }
            String align = readChildAlign(node);
            for (String key : new String[]{"widgets", "children"}) {
                if (!node.has(key) || !node.get(key).isJsonArray()) continue;
                for (JsonElement element : node.getAsJsonArray(key)) {
                    if (element.isJsonObject()) {
                        applyEditorWidgetLayoutInner(element.getAsJsonObject(), Math.max(1, cw), Math.max(1, ch), align);
                    }
                }
            }
        } finally {
            patchParentW = prevW;
            patchParentH = prevH;
        }
    }

    private void patchLayoutField(String id, String field, JsonObject node) {
        if (!node.has(field) || node.get(field).isJsonNull()) return;
        JsonElement el = node.get(field);
        String value = el.isJsonPrimitive() ? el.getAsJsonPrimitive().getAsString() : el.toString();
        applyWidgetPatch(id, field, value);
    }

    private static void collectVideoWidgetIds(JsonObject object, java.util.Set<String> out) {
        if (object == null) return;
        if (object.has("type") && "video".equals(object.get("type").getAsString())
                && object.has("id")) {
            out.add(object.get("id").getAsString());
        }
        for (String key : new String[]{"widgets", "children"}) {
            if (!object.has(key) || !object.get(key).isJsonArray()) continue;
            for (JsonElement element : object.getAsJsonArray(key)) {
                if (element.isJsonObject()) collectVideoWidgetIds(element.getAsJsonObject(), out);
            }
        }
    }

    //? if >=1.20.1 {
    /** Renders one canvas JSON like a HUD overlay (no interaction, no hover). */
    public static void renderEditorPreview(String key, String json, GuiGraphics guiGraphics, float partialTick) {
        SprauteScriptScreen overlay = editorPreviewFor(key, json);
        if (overlay == null) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        scissorGuiGraphics = guiGraphics;
        overlay.left = (sw - overlay.panelW) / 2;
        overlay.top = (sh - overlay.panelH) / 2;
        if (overlay.root.has("x")) {
            int rawX = readRootExtent(overlay.root, "x", sw, overlay.left);
            overlay.left = rawX < 0 ? sw + rawX - overlay.panelW : rawX;
        }
        if (overlay.root.has("y")) {
            int rawY = readRootExtent(overlay.root, "y", sh, overlay.top);
            overlay.top = rawY < 0 ? sh + rawY - overlay.panelH : rawY;
        }
        overlay.processAnimations();
        if (overlay.bgArgb != 0) {
            if (overlay.rootStyle.isPlain()) {
                SprauteGuiDraw.fill(guiGraphics, overlay.left, overlay.top,
                        overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
            } else {
                SprauteUiRender.panel(guiGraphics.pose(), overlay.left, overlay.top,
                        overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb, overlay.rootStyle);
            }
        }
        for (Widget w : overlay.widgets) {
            w.render(overlay, guiGraphics, overlay.left, overlay.top, -10000, -10000, partialTick);
        }
        guiGraphics.flush();
    }
    //?} else {
    /*public static void renderEditorPreview(String key, String json, PoseStack poseStack, float partialTick) {
        SprauteScriptScreen overlay = editorPreviewFor(key, json);
        if (overlay == null) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        overlay.left = (sw - overlay.panelW) / 2;
        overlay.top = (sh - overlay.panelH) / 2;
        if (overlay.root.has("x")) {
            int rawX = readRootExtent(overlay.root, "x", sw, overlay.left);
            overlay.left = rawX < 0 ? sw + rawX - overlay.panelW : rawX;
        }
        if (overlay.root.has("y")) {
            int rawY = readRootExtent(overlay.root, "y", sh, overlay.top);
            overlay.top = rawY < 0 ? sh + rawY - overlay.panelH : rawY;
        }
        overlay.processAnimations();
        if (overlay.bgArgb != 0) {
            if (overlay.rootStyle.isPlain()) {
                GuiComponent.fill(poseStack, overlay.left, overlay.top,
                        overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
            } else {
                SprauteUiRender.panel(poseStack, overlay.left, overlay.top,
                        overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb, overlay.rootStyle);
            }
        }
        for (Widget w : overlay.widgets) {
            w.render(overlay, poseStack, overlay.left, overlay.top, -10000, -10000, partialTick);
        }
    }
    *///?}

    /**
     * Measures the panel size (pixels) the given canvas JSON would render at, without drawing it.
     * World canvases size their render-to-texture target to this — see {@code renderWorldCanvas}.
     */
    public static int[] measureWorldCanvasPanel(String key, String json) {
        SprauteScriptScreen overlay = editorPreviewFor(key, json);
        return overlay == null ? new int[]{300, 180} : new int[]{overlay.panelW, overlay.panelH};
    }

    /** True while a world canvas has something moving (UI animations, node animators, video). */
    public static boolean worldCanvasAnimating(String key) {
        SprauteScriptScreen overlay = editorPreviews.get(key);
        return overlay != null && (!overlay.animations.isEmpty() || !overlay.nodeAnimators.isEmpty()
                || !overlay.videoPlayers.isEmpty());
    }

    /** Frees the cached widget tree (and its video/media) of a world canvas that went away. */
    public static void disposeWorldCanvas(String key) {
        SprauteScriptScreen overlay = editorPreviews.remove(key);
        if (overlay != null) overlay.disposeMedia();
        editorPreviewJson.remove(key);
    }

    //? if >=1.20.1 {
    /**
     * Renders one canvas JSON exactly like {@link #renderEditorPreview}, but flush at (0,0) inside
     * a target already sized to the panel's own dimensions ({@link #measureWorldCanvasPanel}) —
     * no window-relative centering, so it can be drawn into an arbitrary-size off-screen texture
     * for a world-space CANVAS node. No interaction, no hover (same as the editor preview).
     */
    public static void renderWorldCanvas(String key, String json, GuiGraphics guiGraphics, float partialTick) {
        SprauteScriptScreen overlay = editorPreviewFor(key, json);
        if (overlay == null) return;
        overlay.left = 0;
        overlay.top = 0;
        overlay.processAnimations();
        if (overlay.bgArgb != 0) {
            if (overlay.rootStyle.isPlain()) {
                SprauteGuiDraw.fill(guiGraphics, 0, 0, overlay.panelW, overlay.panelH, overlay.bgArgb);
            } else {
                SprauteUiRender.panel(guiGraphics.pose(), 0, 0, overlay.panelW, overlay.panelH, overlay.bgArgb, overlay.rootStyle);
            }
        }
        for (Widget w : overlay.widgets) {
            w.render(overlay, guiGraphics, 0, 0, -10000, -10000, partialTick);
        }
        guiGraphics.flush();
    }
    //?} else {
    /*public static void renderWorldCanvas(String key, String json, PoseStack poseStack, float partialTick) {
        SprauteScriptScreen overlay = editorPreviewFor(key, json);
        if (overlay == null) return;
        overlay.left = 0;
        overlay.top = 0;
        overlay.processAnimations();
        if (overlay.bgArgb != 0) {
            if (overlay.rootStyle.isPlain()) {
                GuiComponent.fill(poseStack, 0, 0, overlay.panelW, overlay.panelH, overlay.bgArgb);
            } else {
                SprauteUiRender.panel(poseStack, 0, 0, overlay.panelW, overlay.panelH, overlay.bgArgb, overlay.rootStyle);
            }
        }
        for (Widget w : overlay.widgets) {
            w.render(overlay, poseStack, 0, 0, -10000, -10000, partialTick);
        }
    }
    *///?}

    /**
     * Layers &gt;= 0 render after the full HUD pass.
     * Must use {@link net.minecraftforge.client.event.RenderGuiEvent.Post} — not CHAT_PANEL Post —
     * otherwise cinematic {@code hideGui=minecraft} cancels the chat overlay and our UI never draws.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (CameraHandler.shouldHideScriptGui()) return;
        if (activeOverlays.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        // ChatScreen draws after HUD — overlay is rendered in onChatScreenRender instead.
        if (mc.screen instanceof ChatScreen) return;
        // SprauteScriptScreen renders overlays at the end of its own render() pass.
        if (mc.screen instanceof SprauteScriptScreen) return;

        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics(), Boolean.FALSE);
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack(), Boolean.FALSE);
        *///?}
    }

    /** Layers below 0 render before the vanilla HUD (hotbar, hearts, chat, ...). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGuiPre(net.minecraftforge.client.event.RenderGuiEvent.Pre event) {
        if (CameraHandler.shouldHideScriptGui()) return;
        if (activeOverlays.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        // With a chat/script screen open the overlays render in those passes instead.
        if (mc.screen instanceof ChatScreen) return;
        if (mc.screen instanceof SprauteScriptScreen) return;

        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics(), Boolean.TRUE);
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack(), Boolean.TRUE);
        *///?}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChatScreenRender(ScreenEvent.Render.Post event) {
        if (CameraHandler.shouldHideScriptGui()) return;
        if (activeOverlays.isEmpty()) return;
        if (!shouldPaintOverlaysOnScreen(event.getScreen())) return;

        Minecraft mc = Minecraft.getInstance();
        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics());
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack());
        *///?}
    }
}
