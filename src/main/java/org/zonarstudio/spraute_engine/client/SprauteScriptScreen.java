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
import org.zonarstudio.spraute_engine.network.SprauteUiActionPacket;
import org.zonarstudio.spraute_engine.ui.SprauteUiJson;

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

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Script-driven overlay: panels, images, text, buttons, entity preview.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class SprauteScriptScreen extends Screen {

    private static final java.util.Map<String, SprauteScriptScreen> activeOverlays = new java.util.LinkedHashMap<>();
    /** Patches received before overlay widgets exist (race with overlayOpen packet). */
    private static final java.util.List<PendingWidgetPatch> pendingWidgetPatches = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Legacy accessor — returns first overlay or null. */
    public static SprauteScriptScreen activeOverlay = null;

    private record PendingWidgetPatch(String widgetId, String field, String value) {}

    private final JsonObject root;
    private final int panelW;
    private final int panelH;
    private final int bgArgb;
    private final List<Widget> widgets = new ArrayList<>();
    private int left;
    private int top;
    public static final java.util.Set<String> monitorOverlaps = new java.util.HashSet<>();
    public static final java.util.Map<String, Boolean> overlapState = new java.util.HashMap<>();
    private String activeInputId = null;
    /** When true, do not notify server (programmatic close / replace). */
    private boolean suppressClosePacket;
    private boolean canClose = true;
    public float currentAlpha = 1.0f;

    //? if >=1.20.1 {
    private static GuiGraphics scissorGuiGraphics;
    //?}

    public static int applyAlpha(int color, float alpha) {
        if (alpha >= 1.0f) return color;
        if (alpha <= 0.0f) return color & 0x00FFFFFF;
        int a = (color >> 24) & 0xFF;
        a = (int) (a * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public SprauteScriptScreen(JsonObject root) {
        super(Component.empty());
        this.root = root;
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
        this.canClose = !root.has("canClose") || root.get("canClose").getAsBoolean();
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
    }

    void applyScrollOffsets(Map<String, Float> offsets) {
        if (offsets == null || offsets.isEmpty()) return;
        for (Widget w : widgets) {
            applyScrollOffset(w, offsets);
        }
    }

    private static void applyScrollOffset(Widget w, Map<String, Float> offsets) {
        if (!(w instanceof ScrollW sw) || sw.id == null || !offsets.containsKey(sw.id)) {
            return;
        }
        float maxScroll = Math.max(0, sw.contentH - sw.h);
        float off = offsets.get(sw.id);
        sw.scrollOffset = Math.max(0, Math.min(off, maxScroll));
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
        }
    }

    private static void flushPendingWidgetPatches(SprauteScriptScreen target) {
        if (pendingWidgetPatches.isEmpty()) return;
        java.util.Iterator<PendingWidgetPatch> it = pendingWidgetPatches.iterator();
        while (it.hasNext()) {
            PendingWidgetPatch p = it.next();
            if (target.applyWidgetPatch(p.widgetId, p.field, p.value)) {
                it.remove();
            }
        }
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
                    animations.computeIfAbsent(widgetId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .add(new AnimState(f, startVal, endVal, (long) (durationSec * 1000L), easing));
                    return true;
                }
            } catch (Exception e) {
            }
            return false;
        }

        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            if (widgetId.equals(widgetIdOf(w))) {
                Widget patched = patchWidget(w, f, v);
                if (patched != w) {
                    widgets.set(i, patched);
                }
                return true;
            }
            if (w instanceof ScrollW sw) {
                for (int j = 0; j < sw.children.size(); j++) {
                    Widget child = sw.children.get(j);
                    if (widgetId.equals(widgetIdOf(child))) {
                        Widget patched = patchWidget(child, f, v);
                        if (patched != child) {
                            sw.children.set(j, patched);
                        }
                        return true;
                    }
                }
            }
            if (w instanceof ClipW cw) {
                for (int j = 0; j < cw.children.size(); j++) {
                    Widget child = cw.children.get(j);
                    if (widgetId.equals(widgetIdOf(child))) {
                        Widget patched = patchWidget(child, f, v);
                        if (patched != child) {
                            cw.children.set(j, patched);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int[] texturePixelSize(ResourceLocation rl) {
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (opt.isPresent()) {
                try (var stream = opt.get().open();
                     com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    return new int[]{img.getWidth(), img.getHeight()};
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{256, 256};
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
        String key = stripImageTextureRegion(path);
        if (key != null && key.startsWith("player_skin:")) {
            ResourceLocation skin = PlayerSkinTextures.resolveFromTextureKey(key);
            if (skin != null) return skin;
        }
        return SprauteUiJson.textureRl(key);
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
        }
        return r;
    }

    //? if >=1.20.1 {
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
            SprauteGuiDraw.blit(guiGraphics, rl, dx, dy, 0f, 0f, dw, dh, texSize[0], texSize[1]);
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
            SprauteGuiDraw.blit(poseStack, dx, dy, 0, 0, dw, dh, texSize[0], texSize[1]);
        }
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
        return "";
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
                return switch (field) { case "x" -> rw.x; case "y" -> rw.y; case "w" -> rw.w; case "h" -> rw.h; default -> 0f; };
            }
            if (w instanceof ImageW iw) {
                return switch (field) { case "x" -> iw.x; case "y" -> iw.y; case "w" -> iw.w; case "h" -> iw.h; default -> 0f; };
            }
            if (w instanceof EntityW ew) {
                return switch (field) { case "x" -> ew.x; case "y" -> ew.y; case "w" -> ew.w; case "h" -> ew.h; case "scale" -> ew.scale; default -> 0f; };
            }
            if (w instanceof ScrollW sw) {
                return switch (field) { case "x" -> sw.x; case "y" -> sw.y; case "w" -> sw.w; case "h" -> sw.h; default -> 0f; };
            }
            if (w instanceof InputW inpw) {
                return switch (field) { case "x" -> inpw.x; case "y" -> inpw.y; case "w" -> inpw.w; case "h" -> inpw.h; default -> 0f; };
            }
            if (w instanceof ItemW iw) {
                return switch (field) { case "x" -> iw.x; case "y" -> iw.y; case "size", "w", "h" -> iw.size; default -> 0f; };
            }
            if (w instanceof ClipW cw) {
                return switch (field) { case "x" -> cw.x; case "y" -> cw.y; case "w" -> cw.w; case "h" -> cw.h; case "alpha" -> cw.alpha; default -> 0f; };
            }
        } catch (Exception e) {}
        return 0f;
    }

    private Widget findWidgetByIdRec(Widget w, String id) {
        if (id.equals(widgetIdOf(w))) return w;
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

    private void processAnimations() {
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
                }
            }
            list.removeAll(toRemove);
        }
        // Cleanup empty lists
        animations.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
    }

    private int resolveSize(String val, boolean isWidth) {
        String v = val.trim();
        if (v.endsWith("%")) {
            float pct = Float.parseFloat(v.substring(0, v.length() - 1));
            return (int) (pct / 100f * (isWidth ? panelW : panelH));
        }
        return (int) Float.parseFloat(v);
    }

    private Widget patchWidget(Widget w, String field, String value) {
        if (field.isEmpty()) return w;
        try {
            if (w instanceof TextW tw) {
                return switch (field) {
                    case "x" -> new TextW((int)Float.parseFloat(value.trim()), tw.y, tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "y" -> new TextW(tw.x, (int)Float.parseFloat(value.trim()), tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "text" -> new TextW(tw.x, tw.y, value, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "color" -> new TextW(tw.x, tw.y, tw.text, parseColor(value), tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "scale" -> new TextW(tw.x, tw.y, tw.text, tw.color, Float.parseFloat(value.trim()), tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "tooltip" -> new TextW(tw.x, tw.y, tw.text, tw.color, tw.scale, value, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    default -> w;
                };
            }
            if (w instanceof ButtonW bw) {
                return switch (field) {
                    case "x" -> new ButtonW(bw.id, (int)Float.parseFloat(value.trim()), bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "y" -> new ButtonW(bw.id, bw.x, (int)Float.parseFloat(value.trim()), bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "w" -> new ButtonW(bw.id, bw.x, bw.y, (int)Float.parseFloat(value.trim()), bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "h" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, (int)Float.parseFloat(value.trim()), bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "label" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, value, bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "color" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), parseColor(value), bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "hover" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, parseColor(value), bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "texture" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, value, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "tooltip" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, value, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    default -> w;
                };
            }
            if (w instanceof RectW rw) {
                return switch (field) {
                    case "x" -> new RectW(resolveSize(value, true), rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id);
                    case "y" -> new RectW(rw.x, resolveSize(value, false), rw.w, rw.h, rw.color, rw.tooltip, rw.id);
                    case "w" -> new RectW(rw.x, rw.y, resolveSize(value, true), rw.h, rw.color, rw.tooltip, rw.id);
                    case "h" -> new RectW(rw.x, rw.y, rw.w, resolveSize(value, false), rw.color, rw.tooltip, rw.id);
                    case "color" -> new RectW(rw.x, rw.y, rw.w, rw.h, parseColor(value), rw.tooltip, rw.id);
                    case "tooltip" -> new RectW(rw.x, rw.y, rw.w, rw.h, rw.color, value, rw.id);
                    default -> w;
                };
            }
            if (w instanceof ImageW iw) {
                return switch (field) {
                    case "x" -> new ImageW((int)Float.parseFloat(value.trim()), iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    case "y" -> new ImageW(iw.x, (int)Float.parseFloat(value.trim()), iw.w, iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    case "w" -> new ImageW(iw.x, iw.y, (int)Float.parseFloat(value.trim()), iw.h, iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    case "h" -> new ImageW(iw.x, iw.y, iw.w, (int)Float.parseFloat(value.trim()), iw.texture, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    case "texture" -> new ImageW(iw.x, iw.y, iw.w, iw.h, value, iw.tooltip, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    case "tooltip" -> new ImageW(iw.x, iw.y, iw.w, iw.h, iw.texture, value, iw.id, iw.sliceBorders, iw.sliceScale, iw.srcU, iw.srcV, iw.srcW, iw.srcH);
                    default -> w;
                };
            }
            if (w instanceof ItemW iw) {
                return switch (field) {
                    case "x" -> new ItemW((int)Float.parseFloat(value.trim()), iw.y, iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "y" -> new ItemW(iw.x, (int)Float.parseFloat(value.trim()), iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "size", "w", "h" -> new ItemW(iw.x, iw.y, (int)Float.parseFloat(value.trim()), iw.itemId, iw.tooltip, iw.id);
                    case "item", "block" -> new ItemW(iw.x, iw.y, iw.size, value, iw.tooltip, iw.id);
                    case "tooltip" -> new ItemW(iw.x, iw.y, iw.size, iw.itemId, value, iw.id);
                    default -> w;
                };
            }
            if (w instanceof EntityW ew) {
                return switch (field) {
                    case "x" -> new EntityW((int)Float.parseFloat(value.trim()), ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "y" -> new EntityW(ew.x, (int)Float.parseFloat(value.trim()), ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "w" -> new EntityW(ew.x, ew.y, (int)Float.parseFloat(value.trim()), ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "h" -> new EntityW(ew.x, ew.y, ew.w, (int)Float.parseFloat(value.trim()), ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "scale" -> new EntityW(ew.x, ew.y, ew.w, ew.h, Float.parseFloat(value.trim()), ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "feetCrop" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, Float.parseFloat(value.trim()), ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "anchorX" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, clamp01(Float.parseFloat(value.trim())), ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "anchorY" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, parseAnchorYPatch(value), ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                    case "crop" -> {
                        float[] c = parseCropPatch(value);
                        yield c != null ? new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid) : w;
                    }
                    case "viewport" -> {
                        float[] vp = parseViewportPatch(value);
                        if (vp != null) {
                            float[] c = viewportCornersToCrop(vp);
                            yield new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid);
                        }
                        yield w;
                    }
                    default -> w;
                };
            }
            if (w instanceof InputW inpw) {
                return switch (field) {
                    case "x" -> new InputW(inpw.id, (int)Float.parseFloat(value.trim()), inpw.y, inpw.w, inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type);
                    case "y" -> new InputW(inpw.id, inpw.x, (int)Float.parseFloat(value.trim()), inpw.w, inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type);
                    case "w" -> new InputW(inpw.id, inpw.x, inpw.y, (int)Float.parseFloat(value.trim()), inpw.h, inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type);
                    case "h" -> new InputW(inpw.id, inpw.x, inpw.y, inpw.w, (int)Float.parseFloat(value.trim()), inpw.text, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type);
                    case "text" -> new InputW(inpw.id, inpw.x, inpw.y, inpw.w, inpw.h, value, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type);
                    default -> w;
                };
            }
            if (w instanceof ScrollW sw) {
                // ScrollW is a class, we could just mutate or replace it. But we should make it a new object since patchWidget replaces it
                return switch (field) {
                    case "x" -> { ScrollW n = new ScrollW((int)Float.parseFloat(value.trim()), sw.y, sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "y" -> { ScrollW n = new ScrollW(sw.x, (int)Float.parseFloat(value.trim()), sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "w" -> { ScrollW n = new ScrollW(sw.x, sw.y, (int)Float.parseFloat(value.trim()), sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "h" -> { ScrollW n = new ScrollW(sw.x, sw.y, sw.w, (int)Float.parseFloat(value.trim()), sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    default -> w;
                };
            }
            if (w instanceof ClipW cw) {
                return switch (field) {
                    case "x" -> { ClipW n = new ClipW((int)Float.parseFloat(value.trim()), cw.y, cw.w, cw.h, cw.alpha, cw.tooltip, cw.id); n.children.addAll(cw.children); yield n; }
                    case "y" -> { ClipW n = new ClipW(cw.x, (int)Float.parseFloat(value.trim()), cw.w, cw.h, cw.alpha, cw.tooltip, cw.id); n.children.addAll(cw.children); yield n; }
                    case "w" -> { ClipW n = new ClipW(cw.x, cw.y, (int)Float.parseFloat(value.trim()), cw.h, cw.alpha, cw.tooltip, cw.id); n.children.addAll(cw.children); yield n; }
                    case "h" -> { ClipW n = new ClipW(cw.x, cw.y, cw.w, (int)Float.parseFloat(value.trim()), cw.alpha, cw.tooltip, cw.id); n.children.addAll(cw.children); yield n; }
                    case "alpha" -> { ClipW n = new ClipW(cw.x, cw.y, cw.w, cw.h, Float.parseFloat(value.trim()), cw.tooltip, cw.id); n.children.addAll(cw.children); yield n; }
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
        if (!root.has("widgets")) return;
        JsonArray arr = root.getAsJsonArray("widgets");
        List<WidgetEntry> entries = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject w = el.getAsJsonObject();
            int layer = w.has("layer") ? w.get("layer").getAsInt() : 0;
            int order = w.has("order") ? w.get("order").getAsInt() : 0;
            String tooltip = w.has("tooltip") ? w.get("tooltip").getAsString() : null;
            if (tooltip != null && tooltip.isEmpty()) tooltip = null;
            Widget built = parseOneWidget(w, tooltip, panelW, panelH);
            if (built != null) {
                entries.add(new WidgetEntry(layer, order, built));
            }
        }
        entries.sort(Comparator.comparingInt((WidgetEntry e) -> e.layer).thenComparingInt(e -> e.order));
        for (WidgetEntry e : entries) {
            widgets.add(e.widget);
        }
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

    private Widget parseOneWidget(JsonObject w, String tooltip, int pw, int ph) {
        String type = w.has("type") ? w.get("type").getAsString().toLowerCase() : "";
        int x = readCoord(w, "x", pw);
        int y = readCoord(w, "y", ph);
        int ww = readCoord(w, "w", pw);
        int hh = readCoord(w, "h", ph);
        String wid = w.has("id") ? w.get("id").getAsString() : "";
        
        float rotation = w.has("rotation") ? w.get("rotation").getAsFloat() : 0f;
        float[] pivot = parsePivot(w);
        
        Widget parsed = switch (type) {
            case "rect", "panel" -> new RectW(x, y, ww, hh, parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFFFF"), tooltip, wid);
            case "gridBg" -> new GridBgW(x, y, ww, hh, 
                    w.has("gridType") ? w.get("gridType").getAsString() : "hv",
                    w.has("cellSize") ? w.get("cellSize").getAsInt() : 20,
                    w.has("thickness") ? w.get("thickness").getAsInt() : 1,
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), tooltip, wid);
            case "image" -> {
                int sliceBorders = w.has("slice_borders") ? w.get("slice_borders").getAsInt() : 0;
                int sliceScale = w.has("slice_scale") ? w.get("slice_scale").getAsInt() : 1;
                int[] src = parseImageSrc(w);
                yield new ImageW(x, y, ww, hh, w.has("texture") ? w.get("texture").getAsString() : "minecraft:textures/misc/unknown_pack.png", tooltip, wid, sliceBorders, sliceScale, src[0], src[1], src[2], src[3]);
            }
            case "text" -> {
                float[] anchors = parseAnchor(w);
                yield new TextW(x, y, w.has("text") ? w.get("text").getAsString() : "", parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFF"), w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip, wid,
                        w.has("wrap") ? readCoord(w, "wrap", pw) : 0,
                        w.has("align") ? w.get("align").getAsString().toLowerCase() : "left",
                        w.has("maxLines") ? w.get("maxLines").getAsInt() : 0,
                        w.has("maxChars") ? w.get("maxChars").getAsInt() : 0,
                        anchors[0], anchors[1]);
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
                        for (JsonElement hel : hp.getAsJsonArray("children")) {
                            if (!hel.isJsonObject()) continue;
                            Widget cw = parseOneWidget(hel.getAsJsonObject(), null, hpw, hph);
                            if (cw != null) hoverChildren.add(cw);
                        }
                    }
                }
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
                    w.has("labelScale") ? w.get("labelScale").getAsFloat() : 1f,
                    w.has("subScale") ? w.get("subScale").getAsFloat() : 0.65f,
                    hoverChildren, hpw, hph);
            }
            case "entity" -> {
                UUID uuid = null;
                if (w.has("entityUuid")) {
                    try {
                        uuid = UUID.fromString(w.get("entityUuid").getAsString());
                    } catch (Exception ignored) {}
                }
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
                        float ay = a.get(1).getAsFloat();
                        anchorY = ay < 0f ? -1f : ay;
                    }
                } else {
                    if (w.has("anchorX")) anchorX = clamp01(w.get("anchorX").getAsFloat());
                    if (w.has("anchorY")) {
                        float ay = w.get("anchorY").getAsFloat();
                        anchorY = ay < 0f ? -1f : ay;
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
                yield new EntityW(x, y, ww, hh, scale, uuid, feetCrop, tooltip, wid, crop[0], crop[1], crop[2], crop[3], anchorX, anchorY, disableAnim, hideNameTag, noLookAt, noFollowCursor, noHurtAnim, renderBones, skinPlayerUuid);
            }
            case "scroll" -> {
                int contentH = readCoord(w, "contentH", ph);
                int scrollBg = parseColor(w.has("color") ? w.get("color").getAsString() : "#00000000");
                boolean autoBar = w.has("autoScrollbar") && w.get("autoScrollbar").getAsBoolean();
                boolean showBar = autoBar || (!w.has("scrollbar") || w.get("scrollbar").getAsBoolean());
                ScrollW scroll = new ScrollW(x, y, ww, hh, contentH, scrollBg, tooltip, wid, showBar, autoBar);
                if (w.has("children")) {
                    JsonArray children = w.getAsJsonArray("children");
                    for (JsonElement cel : children) {
                        if (!cel.isJsonObject()) continue;
                        JsonObject cw = cel.getAsJsonObject();
                        String ct = cw.has("tooltip") ? cw.get("tooltip").getAsString() : null;
                        Widget child = parseOneWidget(cw, ct, ww, contentH);
                        if (child != null) scroll.children.add(child);
                    }
                }
                yield scroll;
            }
            case "clip" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                ClipW clip = new ClipW(x, y, ww, hh, alpha, tooltip, wid);
                if (w.has("children")) {
                    JsonArray children = w.getAsJsonArray("children");
                    for (JsonElement cel : children) {
                        if (!cel.isJsonObject()) continue;
                        JsonObject cw = cel.getAsJsonObject();
                        String ct = cw.has("tooltip") ? cw.get("tooltip").getAsString() : null;
                        Widget child = parseOneWidget(cw, ct, ww, hh);
                        if (child != null) clip.children.add(child);
                    }
                }
                yield clip;
            }
            case "input" -> new InputW(wid, x, y, ww, hh,
                    w.has("text") ? w.get("text").getAsString() : "",
                    w.has("placeholder") ? w.get("placeholder").getAsString() : "",
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFF"),
                    parseColor(w.has("bgColor") ? w.get("bgColor").getAsString() : "#FF000000"),
                    parseColor(w.has("outlineColor") ? w.get("outlineColor").getAsString() : "#FFAAAAAA"),
                    w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip,
                    w.has("maxChars") ? w.get("maxChars").getAsInt() : 32,
                    w.has("inputType") ? w.get("inputType").getAsString().toLowerCase() : "text");
            case "divider" -> new DividerW(x, y, ww, parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), wid);
            case "item", "block" -> {
                String itemId = "minecraft:stone";
                if (w.has("item")) itemId = w.get("item").getAsString();
                else if (w.has("block")) itemId = w.get("block").getAsString();
                int itemSize = w.has("size") ? w.get("size").getAsInt() : (w.has("w") ? w.get("w").getAsInt() : (ww > 0 ? ww : 16));
                yield new ItemW(x, y, itemSize, itemId, tooltip, wid);
            }
            default -> null;
        };
        
        if (parsed != null && rotation != 0f) {
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

    private static float clamp01(float f) {
        return Math.min(1f, Math.max(0f, f));
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
        if (s == null || s.isEmpty()) return 0xFFFFFFFF;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        try {
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {}
        return 0xFFFFFFFF;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        scissorGuiGraphics = guiGraphics;
        processAnimations();
        renderBackground(guiGraphics);
        int ax0 = left;
        int ay0 = top;
        SprauteGuiDraw.fill(guiGraphics, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb);
        for (Widget w : widgets) {
            w.render(this, guiGraphics, ax0, ay0, mouseX, mouseY, partialTick);
        }
        ButtonW hoveredWithPanel = null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w instanceof ButtonW bw && bw.hasHoverPanel() && bw.contains(this, ax0, ay0, mouseX, mouseY)) {
                hoveredWithPanel = bw;
                break;
            }
        }
        if (hoveredWithPanel != null) {
            // defer to after super.render so hover panel draws above all widgets
        } else {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                String tip = w.tooltip();
                if (tip != null && !tip.isEmpty() && w.contains(this, ax0, ay0, mouseX, mouseY)) {
                    guiGraphics.renderTooltip(this.font, Component.literal(tip), mouseX, mouseY);
                    break;
                }
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
        renderBackground(poseStack);
        int ax0 = left;
        int ay0 = top;
        GuiComponent.fill(poseStack, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb);
        for (Widget w : widgets) {
            w.render(this, poseStack, ax0, ay0, mouseX, mouseY, partialTick);
        }
        ButtonW hoveredWithPanel = null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w instanceof ButtonW bw && bw.hasHoverPanel() && bw.contains(this, ax0, ay0, mouseX, mouseY)) {
                hoveredWithPanel = bw;
                break;
            }
        }
        if (hoveredWithPanel == null) {
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                String tip = w.tooltip();
                if (tip != null && !tip.isEmpty() && w.contains(this, ax0, ay0, mouseX, mouseY)) {
                    renderTooltip(poseStack, Component.literal(tip), mouseX, mouseY);
                    break;
                }
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int ax0 = left;
            int ay0 = top;
            boolean clickedInput = false;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                if (w instanceof ScrollW sw) {
                    int sx = ax0 + sw.x;
                    int sy = ay0 + sw.y;
                    if (mouseX >= sx && mouseX < sx + sw.w && mouseY >= sy && mouseY < sy + sw.h) {
                        for (int j = sw.children.size() - 1; j >= 0; j--) {
                            Widget child = sw.children.get(j);
                            if (child instanceof ButtonW bw && bw.id != null && !bw.id.isEmpty()) {
                                int bx = sx + bw.x;
                                int by = sy + bw.y - (int) sw.scrollOffset;
                                if (mouseX >= bx && mouseX < bx + bw.w && mouseY >= by && mouseY < by + bw.h) {
                                    ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(bw.id, false));
                                    return true;
                                }
                            } else if (child instanceof InputW inpw && inpw.id != null && !inpw.id.isEmpty()) {
                                int bx = sx + inpw.x;
                                int by = sy + inpw.y - (int) sw.scrollOffset;
                                if (mouseX >= bx && mouseX < bx + inpw.w && mouseY >= by && mouseY < by + inpw.h) {
                                    activeInputId = inpw.id;
                                    clickedInput = true;
                                }
                            }
                        }
                    }
                }
                if (w instanceof ButtonW bw && bw.id != null && !bw.id.isEmpty()) {
                    int bx = ax0 + bw.x;
                    int by = ay0 + bw.y;
                    if (mouseX >= bx && mouseX < bx + bw.w && mouseY >= by && mouseY < by + bw.h) {
                        ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(bw.id, false));
                        return true;
                    }
                } else if (w instanceof InputW inpw && inpw.id != null && !inpw.id.isEmpty()) {
                    int bx = ax0 + inpw.x;
                    int by = ay0 + inpw.y;
                    if (mouseX >= bx && mouseX < bx + inpw.w && mouseY >= by && mouseY < by + inpw.h) {
                        activeInputId = inpw.id;
                        clickedInput = true;
                    }
                }
            }
            if (!clickedInput) activeInputId = null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int ax0 = left;
        int ay0 = top;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w instanceof ScrollW sw) {
                int sx = ax0 + sw.x;
                int sy = ay0 + sw.y;
                if (mouseX >= sx && mouseX < sx + sw.w && mouseY >= sy && mouseY < sy + sw.h) {
                    sw.scrollOffset -= (float) (delta * 12.0);
                    float maxScroll = Math.max(0, sw.contentH - sw.h);
                    sw.scrollOffset = Math.max(0, Math.min(sw.scrollOffset, maxScroll));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeInputId != null) {
            if (keyCode == 259) { // Backspace
                for (int i = 0; i < widgets.size(); i++) {
                    Widget w = widgets.get(i);
                    if (w instanceof InputW inpw && inpw.id.equals(activeInputId) && !inpw.text.isEmpty()) {
                        String newText = inpw.text.substring(0, inpw.text.length() - 1);
                        widgets.set(i, new InputW(inpw.id, inpw.x, inpw.y, inpw.w, inpw.h, newText, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type));
                        ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket(inpw.id + ":" + newText, false));
                        break;
                    }
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
            for (int i = 0; i < widgets.size(); i++) {
                Widget w = widgets.get(i);
                if (w instanceof InputW inpw && inpw.id.equals(activeInputId)) {
                    if (inpw.text.length() < inpw.maxChars) {
                        String newText = inpw.text + codePoint;
                        widgets.set(i, new InputW(inpw.id, inpw.x, inpw.y, inpw.w, inpw.h, newText, inpw.placeholder, inpw.color, inpw.bgColor, inpw.outlineColor, inpw.scale, inpw.tooltip, inpw.maxChars, inpw.type));
                        ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket(inpw.id + ":" + newText, false));
                    }
                    break;
                }
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (!suppressClosePacket) {
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(new org.zonarstudio.spraute_engine.network.SprauteUiActionPacket("", true));
        }
        monitorOverlaps.clear();
        overlapState.clear();
        suppressClosePacket = false;
        super.onClose();
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
        default String getId() { return ""; }
        default float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) { return null; }
        default float[] getBaseOBB(float px, float py, float pw, float ph) {
            return new float[]{ px, py, px + pw, py, px + pw, py + ph, px, py + ph };
        }
    }

    private record RectW(int x, int y, int w, int h, int color, String tooltip, String id) implements Widget {
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
            SprauteGuiDraw.fill(guiGraphics, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, applyAlpha(color, screen.currentAlpha));
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            GuiComponent.fill(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, applyAlpha(color, screen.currentAlpha));
        }
        *///?}
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

    private record ImageW(int x, int y, int w, int h, String texture, String tooltip, String id,
                          int sliceBorders, int sliceScale, int srcU, int srcV, int srcW, int srcH) implements Widget {
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
            ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
            if (screen.currentAlpha < 1.0f) {
                guiGraphics.setColor(1f, 1f, 1f, screen.currentAlpha);
            }

            if (sliceBorders > 0) {
                int b = sliceBorders;
                int bs = b * sliceScale;
                int ix = ax0 + x;
                int iy = ay0 + y;

                SprauteGuiDraw.blit(guiGraphics, rl, ix, iy, 0, 0, bs, bs, 256, 256);
                SprauteGuiDraw.blit(guiGraphics, rl, ix + w - bs, iy, 256 - b, 0, bs, bs, 256, 256);
                SprauteGuiDraw.blit(guiGraphics, rl, ix, iy + h - bs, 0, 256 - b, bs, bs, 256, 256);
                SprauteGuiDraw.blit(guiGraphics, rl, ix + w - bs, iy + h - bs, 256 - b, 256 - b, bs, bs, 256, 256);

                if (w - bs * 2 > 0) {
                    blitScaled(guiGraphics, rl, ix + bs, iy, w - bs * 2, bs, b, 0, 256 - b * 2, b);
                    blitScaled(guiGraphics, rl, ix + bs, iy + h - bs, w - bs * 2, bs, b, 256 - b, 256 - b * 2, b);
                }
                if (h - bs * 2 > 0) {
                    blitScaled(guiGraphics, rl, ix, iy + bs, bs, h - bs * 2, 0, b, b, 256 - b * 2);
                    blitScaled(guiGraphics, rl, ix + w - bs, iy + bs, bs, h - bs * 2, 256 - b, b, b, 256 - b * 2);
                }
                if (w - bs * 2 > 0 && h - bs * 2 > 0) {
                    blitScaled(guiGraphics, rl, ix + bs, iy + bs, w - bs * 2, h - bs * 2, b, b, 256 - b * 2, 256 - b * 2);
                }
            } else {
                int[] src = resolveImageSrcRect(texture, srcU, srcV, srcW, srcH);
                blitImageTexture(guiGraphics, rl, ax0 + x, ay0 + y, w, h, src[0], src[1], src[2], src[3]);
            }

            guiGraphics.setColor(1f, 1f, 1f, 1f);
        }

        private void blitScaled(GuiGraphics guiGraphics, ResourceLocation rl, int x, int y, int width, int height, float uOffset, float vOffset, int uWidth, int vHeight) {
            guiGraphics.blit(rl, x, y, width, height, uOffset, vOffset, uWidth, vHeight, 256, 256);
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, screen.currentAlpha);
            RenderSystem.setShaderTexture(0, rl);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (sliceBorders > 0) {
                int b = sliceBorders;
                int bs = b * sliceScale;
                int ix = ax0 + x;
                int iy = ay0 + y;

                // Draw corners
                GuiComponent.blit(poseStack, ix, iy, 0, 0, bs, bs, 256, 256); // Top-Left
                GuiComponent.blit(poseStack, ix + w - bs, iy, 256 - b, 0, bs, bs, 256, 256); // Top-Right
                GuiComponent.blit(poseStack, ix, iy + h - bs, 0, 256 - b, bs, bs, 256, 256); // Bottom-Left
                GuiComponent.blit(poseStack, ix + w - bs, iy + h - bs, 256 - b, 256 - b, bs, bs, 256, 256); // Bottom-Right

                // Draw edges (scaled appropriately)
                if (w - bs * 2 > 0) {
                    blitScaled(poseStack, ix + bs, iy, w - bs * 2, bs, b, 0, 256 - b * 2, b); // Top
                    blitScaled(poseStack, ix + bs, iy + h - bs, w - bs * 2, bs, b, 256 - b, 256 - b * 2, b); // Bottom
                }
                if (h - bs * 2 > 0) {
                    blitScaled(poseStack, ix, iy + bs, bs, h - bs * 2, 0, b, b, 256 - b * 2); // Left
                    blitScaled(poseStack, ix + w - bs, iy + bs, bs, h - bs * 2, 256 - b, b, b, 256 - b * 2); // Right
                }

                // Draw center
                if (w - bs * 2 > 0 && h - bs * 2 > 0) {
                    blitScaled(poseStack, ix + bs, iy + bs, w - bs * 2, h - bs * 2, b, b, 256 - b * 2, 256 - b * 2); // Center
                }
            } else {
                int[] src = resolveImageSrcRect(texture, srcU, srcV, srcW, srcH);
                blitImageTexture(poseStack, rl, ax0 + x, ay0 + y, w, h, src[0], src[1], src[2], src[3]);
            }

            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        private void blitScaled(PoseStack poseStack, int x, int y, int width, int height, float uOffset, float vOffset, int uWidth, int vHeight) {
            com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            com.mojang.math.Matrix4f matrix4f = poseStack.last().pose();
            bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(matrix4f, (float)x, (float)(y + height), 0.0F).uv(uOffset / 256.0F, (vOffset + (float)vHeight) / 256.0F).endVertex();
            bufferbuilder.vertex(matrix4f, (float)(x + width), (float)(y + height), 0.0F).uv((uOffset + (float)uWidth) / 256.0F, (vOffset + (float)vHeight) / 256.0F).endVertex();
            bufferbuilder.vertex(matrix4f, (float)(x + width), (float)y, 0.0F).uv((uOffset + (float)uWidth) / 256.0F, vOffset / 256.0F).endVertex();
            bufferbuilder.vertex(matrix4f, (float)x, (float)y, 0.0F).uv(uOffset / 256.0F, vOffset / 256.0F).endVertex();
            com.mojang.blaze3d.vertex.Tesselator.getInstance().end();
        }
        *///?}
    }

    private record TextW(int x, int y, String text, int color, float scale, String tooltip, String id, int wrapWidth, String align, int maxLines, int maxChars, float anchorX, float anchorY) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public String getId() { return id; }
        @Override public float[] getOBB(SprauteScriptScreen screen, int ax0, int ay0) {
            float[] bounds = getBounds(screen);
            float tw = bounds[0];
            float th = bounds[1];
            float offsetX = -tw * anchorX;
            float offsetY = -th * anchorY;
            float lx = ax0 + x + offsetX;
            float ly = ay0 + y + offsetY;
            return getBaseOBB(lx, ly, tw, th);
        }
        @Override
        public String tooltip() {
            return tooltip;
        }

        private float[] getBounds(SprauteScriptScreen screen) {
            String renderText = text != null ? text.replace("&", "§") : "";
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }
            float totalW = 0;
            float totalH = 0;
            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = screen.font.split(net.minecraft.network.chat.Component.literal(renderText), effWrap);
                if (maxLines > 0 && lines.size() > maxLines) lines = lines.subList(0, maxLines);
                for (var line : lines) {
                    float w = screen.font.width(line);
                    if (w > totalW) totalW = w;
                }
                totalH = lines.size() * screen.font.lineHeight;
            } else {
                totalW = screen.font.width(renderText);
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
            float lx = ax0 + x + offsetX;
            float ly = ay0 + y + offsetY;
            return mx >= lx && mx < lx + tw && my >= ly && my < ly + th;
        }

        //? if >=1.20.1 {
        @Override
        public void render(SprauteScriptScreen screen, GuiGraphics guiGraphics, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            String renderText = text != null ? text.replace("&", "§") : "";
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }

            float[] bounds = getBounds(screen);
            float offsetX = -bounds[0] * anchorX / scale;
            float offsetY = -bounds[1] * anchorY / scale;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(ax0 + x + offsetX * scale, ay0 + y + offsetY * scale, 0);
            guiGraphics.pose().scale(scale, scale, 1f);
            int drawColor = applyAlpha(color & 0xFFFFFF | (color & 0xFF000000), screen.currentAlpha);

            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = screen.font.split(net.minecraft.network.chat.Component.literal(renderText), effWrap);
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
                    guiGraphics.drawString(screen.font, line, (int) drawX, lineY, drawColor);
                    lineY += screen.font.lineHeight;
                    linesDrawn++;
                }
            } else {
                guiGraphics.drawString(screen.font, renderText, 0, 0, drawColor);
            }
            guiGraphics.pose().popPose();
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            String renderText = text != null ? text.replace("&", "§") : "";
            if (maxChars > 0 && renderText.length() > maxChars) {
                renderText = renderText.substring(0, maxChars) + "...";
            }

            float[] bounds = getBounds(screen);
            float offsetX = -bounds[0] * anchorX / scale;
            float offsetY = -bounds[1] * anchorY / scale;

            poseStack.pushPose();
            poseStack.translate(ax0 + x + offsetX * scale, ay0 + y + offsetY * scale, 0);
            poseStack.scale(scale, scale, 1f);
            int drawColor = applyAlpha(color & 0xFFFFFF | (color & 0xFF000000), screen.currentAlpha);

            if (wrapWidth > 0) {
                int effWrap = (int) (wrapWidth / scale);
                List<net.minecraft.util.FormattedCharSequence> lines = screen.font.split(net.minecraft.network.chat.Component.literal(renderText), effWrap);
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
                    screen.font.draw(poseStack, line, drawX, lineY, drawColor);
                    lineY += screen.font.lineHeight;
                    linesDrawn++;
                }
            } else {
                screen.font.draw(poseStack, renderText, 0, 0, drawColor);
            }
            poseStack.popPose();
        }
        *///?}
    }

    private record InputW(String id, int x, int y, int w, int h, String text, String placeholder, int color, int bgColor, int outlineColor, float scale, String tooltip, int maxChars, String type) implements Widget {
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

            SprauteGuiDraw.fill(guiGraphics, bx, by, bx + w, by + h, outlineColor);
            SprauteGuiDraw.fill(guiGraphics, bx + 1, by + 1, bx + w - 1, by + h - 1, bgColor);

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
                guiGraphics.drawString(screen.font, placeholder, 0, 0, color & 0x77FFFFFF);
            } else {
                guiGraphics.drawString(screen.font, displayText, 0, 0, color);
            }
            guiGraphics.pose().popPose();
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;

            GuiComponent.fill(poseStack, bx, by, bx + w, by + h, outlineColor);
            GuiComponent.fill(poseStack, bx + 1, by + 1, bx + w - 1, by + h - 1, bgColor);

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
                screen.font.draw(poseStack, placeholder, 0, 0, color & 0x77FFFFFF);
            } else {
                screen.font.draw(poseStack, displayText, 0, 0, color);
            }
            poseStack.popPose();
        }
        *///?}
    }

    private record ButtonW(String id, int x, int y, int w, int h, String label, String subLabel, int color, int hoverColor, String texture, String tooltip,
                           int labelWrap, float labelScale, float subScale,
                           List<Widget> hoverChildren, int hoverPw, int hoverPh) implements Widget {
        ButtonW {
            if (hoverChildren == null) hoverChildren = List.of();
        }

        boolean hasHoverPanel() {
            return hoverPw > 0 && hoverPh > 0 && !hoverChildren.isEmpty();
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
            boolean over = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
            if (texture != null && !texture.isEmpty()) {
                ResourceLocation rl = SprauteScriptScreen.resolveWidgetTexture(texture);
                if (over) {
                    guiGraphics.setColor(1.1f, 1.1f, 1.1f, 1f);
                }
                SprauteGuiDraw.blit(guiGraphics, rl, bx, by, 0f, 0f, w, h, 256, 256);
                guiGraphics.setColor(1f, 1f, 1f, 1f);
            } else {
                SprauteGuiDraw.fill(guiGraphics, bx, by, bx + w, by + h, over ? hoverColor : color);
            }
            int pad = 3;
            if (labelWrap > 0 && label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                String renderSubLabel = subLabel != null ? subLabel.replace("&", "§") : null;
                float ls = labelScale > 0.05f ? labelScale : 1f;
                float ss = subScale > 0.05f ? subScale : 0.65f;
                boolean hasSub = renderSubLabel != null && !renderSubLabel.isEmpty();
                int effWrap = Math.max(4, (int) (labelWrap / ls));
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                        screen.font.split(net.minecraft.network.chat.Component.literal(renderLabel), effWrap);
                int nLines = lines.size();
                float titleBlockPx = nLines * screen.font.lineHeight * ls;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(bx + pad, by + pad, 0);
                guiGraphics.pose().scale(ls, ls, 1f);
                int lineY = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    guiGraphics.drawString(screen.font, line, 0, lineY, 0xFFFFFFFF);
                    lineY += screen.font.lineHeight;
                }
                guiGraphics.pose().popPose();
                if (hasSub) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(bx + pad, by + pad + titleBlockPx + 1, 0);
                    guiGraphics.pose().scale(ss, ss, 1f);
                    guiGraphics.drawString(screen.font, net.minecraft.network.chat.Component.literal(renderSubLabel), 0, 0, 0xFFAAAAAA);
                    guiGraphics.pose().popPose();
                }
            } else if (label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                int tw = screen.font.width(renderLabel);
                guiGraphics.drawString(screen.font, renderLabel, bx + (w - tw) / 2, by + (h - 8) / 2, 0xFFFFFFFF);
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
                GuiComponent.blit(poseStack, bx, by, 0f, 0f, w, h, 256, 256);
            } else {
                GuiComponent.fill(poseStack, bx, by, bx + w, by + h, over ? hoverColor : color);
            }
            int pad = 3;
            if (labelWrap > 0 && label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                String renderSubLabel = subLabel != null ? subLabel.replace("&", "§") : null;
                float ls = labelScale > 0.05f ? labelScale : 1f;
                float ss = subScale > 0.05f ? subScale : 0.65f;
                boolean hasSub = renderSubLabel != null && !renderSubLabel.isEmpty();
                int effWrap = Math.max(4, (int) (labelWrap / ls));
                java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                        screen.font.split(net.minecraft.network.chat.Component.literal(renderLabel), effWrap);
                int nLines = lines.size();
                float titleBlockPx = nLines * screen.font.lineHeight * ls;
                poseStack.pushPose();
                poseStack.translate(bx + pad, by + pad, 0);
                poseStack.scale(ls, ls, 1f);
                int lineY = 0;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    screen.font.draw(poseStack, line, 0, lineY, 0xFFFFFFFF);
                    lineY += screen.font.lineHeight;
                }
                poseStack.popPose();
                if (hasSub) {
                    poseStack.pushPose();
                    poseStack.translate(bx + pad, by + pad + titleBlockPx + 1, 0);
                    poseStack.scale(ss, ss, 1f);
                    screen.font.draw(poseStack, net.minecraft.network.chat.Component.literal(renderSubLabel), 0, 0, 0xFFAAAAAA);
                    poseStack.popPose();
                }
            } else if (label != null && !label.isEmpty()) {
                String renderLabel = label.replace("&", "§");
                int tw = screen.font.width(renderLabel);
                screen.font.draw(poseStack, renderLabel, bx + (w - tw) / 2f, by + (h - 8) / 2f, 0xFFFFFFFF);
            }
        }
        *///?}
    }

    private static class ClipW implements Widget {
        final int x, y, w, h;
        final String tooltip;
        final String id;
        float alpha = 1.0f;
        final List<Widget> children = new ArrayList<>();

        ClipW(int x, int y, int w, int h, float alpha, String tooltip, String id) {
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
            pushScissor(sx, sy, sx + w, sy + h);
            try {
                for (Widget child : children) {
                    child.render(screen, guiGraphics, sx, sy, mouseX, mouseY, partialTick);
                }
            } finally {
                popScissor();
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
            pushScissor(sx, sy, sx + w, sy + h);
            try {
                for (Widget child : children) {
                    child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
                }
            } finally {
                popScissor();
            }
            screen.currentAlpha = oldAlpha;
        }
        *///?}
    }

    private static class ScrollW implements Widget {
        final int x, y, w, h, contentH;
        final int bgColor;
        final String tooltip;
        final String id;
        final boolean showBar;
        final boolean autoBar;
        final List<Widget> children = new ArrayList<>();
        float scrollOffset = 0;

        ScrollW(int x, int y, int w, int h, int contentH, int bgColor, String tooltip, String id, boolean showBar, boolean autoBar) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.contentH = Math.max(h, contentH);
            this.bgColor = bgColor; this.tooltip = tooltip; this.id = id;
            this.showBar = showBar; this.autoBar = autoBar;
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
            int sx = ax0 + x, sy = ay0 + y;
            int currentBgColor = applyAlpha(bgColor, screen.currentAlpha);
            if ((currentBgColor & 0xFF000000) != 0) {
                SprauteGuiDraw.fill(guiGraphics, sx, sy, sx + w, sy + h, currentBgColor);
            }
            pushScissor(sx, sy, sx + w, sy + h);
            try {
                int offsetY = -(int) scrollOffset;
                for (Widget child : children) {
                    child.render(screen, guiGraphics, sx, sy + offsetY, mouseX, mouseY, partialTick);
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
                SprauteGuiDraw.fill(guiGraphics, barX, barY, barX + barW, barY + barH, applyAlpha(0x88AAAAAA, screen.currentAlpha));
            }
        }
        //?} else {
        /*@Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int sx = ax0 + x, sy = ay0 + y;
            int currentBgColor = applyAlpha(bgColor, screen.currentAlpha);
            if ((currentBgColor & 0xFF000000) != 0) {
                GuiComponent.fill(poseStack, sx, sy, sx + w, sy + h, currentBgColor);
            }
            pushScissor(sx, sy, sx + w, sy + h);
            try {
                int offsetY = -(int) scrollOffset;
                for (Widget child : children) {
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
                GuiComponent.fill(poseStack, barX, barY, barX + barW, barY + barH, applyAlpha(0x88AAAAAA, screen.currentAlpha));
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

    private record ItemW(int x, int y, int size, String itemId, String tooltip, String id) implements Widget {
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
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
            ResourceLocation rl = itemId != null && itemId.contains(":") ? new ResourceLocation(itemId) : new ResourceLocation("minecraft", itemId != null ? itemId : "stone");
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                Block block = ForgeRegistries.BLOCKS.getValue(rl);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    item = block.asItem();
                }
            }
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return;
            }
            ItemStack stack = new ItemStack(item);

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
            ResourceLocation rl = itemId.contains(":") ? new ResourceLocation(itemId) : new ResourceLocation("minecraft", itemId);
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                Block block = ForgeRegistries.BLOCKS.getValue(rl);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    item = block.asItem();
                }
            }
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return;
            }
            ItemStack stack = new ItemStack(item);

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
    /** Config template for chat player head — same geo/anim as a typical chat NPC (defolt + idle). */
    private static final UUID UI_CHAT_NPC_TEMPLATE_ID = UUID.fromString("00000000-0000-4000-8000-0000000a0001");
    /** Render dummy cache key for skin avatars (separate from template entity uuid). */
    private static final UUID UI_SKIN_AVATAR_DUMMY_ID = UUID.fromString("00000000-0000-4000-8000-0000000a0002");

    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getChatNpcTemplate(Minecraft mc) {
        org.zonarstudio.spraute_engine.entity.SprauteNpcEntity tpl = uiDummyCache.get(UI_CHAT_NPC_TEMPLATE_ID);
        if (tpl == null) {
            tpl = new org.zonarstudio.spraute_engine.entity.SprauteNpcEntity(
                    org.zonarstudio.spraute_engine.entity.ModEntities.SPRAUTE_NPC.get(), mc.level);
            tpl.setModel("geo/defolt.geo.json");
            tpl.setTexture("textures/entity/defolt.png");
            tpl.setAnimation("animations/npc_classic.animation.json");
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
        modelView.scale(1.0f, 1.0f, -1.0f);
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0, 0.0, 1000.0);
        poseStack.scale(scale, scale, scale);
        Quaternion flip = Vector3f.ZP.rotationDegrees(180.0f);
        Quaternion tilt = Vector3f.XP.rotationDegrees(g * 20.0f);
        flip.mul(tilt);
        poseStack.mulPose(flip);

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
        RenderSystem.runAsFancy(() -> {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, poseStack, bufferSource, 15728880);
        });
        bufferSource.endBatch();
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
    }
    *///?}

    private static final java.util.Stack<int[]> scissorStack = new java.util.Stack<>();

    public static void pushScissor(int x0, int y0, int x1, int y1) {
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
            boolean noHurtAnim, String[] renderBones, UUID skinPlayerUuid
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
            } else if (entityUuid == null) {
                SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                return;
            } else {
                Entity e = findEntityByUuid(mc.level, entityUuid);
                if (!(e instanceof LivingEntity living)) {
                    SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                    return;
                }
                if (useDummy && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                    renderTarget = getOrCreateDummy(living, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                } else {
                    renderTarget = living;
                }
            }

            float sx0f = left + w * cropL;
            float sy0f = top + h * cropT;
            float sx1f = left + w * (1f - cropR);
            float sy1f = top + h * (1f - cropB);
            int sx0 = (int) Math.floor(sx0f);
            int sy0 = (int) Math.floor(sy0f);
            int sx1 = (int) Math.ceil(sx1f);
            int sy1 = (int) Math.ceil(sy1f);
            pushScissor(sx0, sy0, sx1, sy1);
            try {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = renderBones;
                }

                int cx = (int) (left + w * anchorX);
                int cy;
                if (anchorY >= 0f) {
                    cy = (int) (top + h * anchorY);
                } else {
                    float t = Math.min(1f, Math.max(0f, feetCrop));
                    float anchorFrac = 0.48f + t * 0.20f;
                    cy = top + (int) (h * anchorFrac);
                }
                int sc = Math.max(8, (int) (Math.min(w, h) * 0.44f * scale));

                boolean prevDisable = SprauteScriptScreen.disableEntityAnimations;
                boolean prevHideName = SprauteScriptScreen.hideEntityNameTag;
                SprauteScriptScreen.disableEntityAnimations = disableAnim;
                SprauteScriptScreen.hideEntityNameTag = hideNameTag;

                boolean dontFollow = noLookAt || noFollowCursor;
                float lookX = dontFollow ? 0f : (float) cx - mouseX;
                float lookY = dontFollow ? 0f : (float) (cy - 50) - mouseY;

                // Dummy entities (chat head: noLookAt etc.) use the manual ModelViewStack path, which is
                // self-contained and works inside a HUD overlay (RenderGuiOverlayEvent) — exactly as on 1.19.2.
                // InventoryScreen.renderEntityInInventory is GuiGraphics-bound and corrupts the framebuffer
                // when called from the HUD overlay, so it is only used for the live-entity (in-screen) case.
                if (useDummy) {
                    renderEntityDirect(guiGraphics, cx, cy, sc, lookX, lookY, renderTarget);
                } else {
                    float f = (float) Math.atan(lookX / 40.0f);
                    float g = (float) Math.atan(lookY / 40.0f);
                    Quaternionf pose = new Quaternionf().rotateZYX(0.0f, (float) Math.PI - f * 20.0f * ((float) Math.PI / 180.0f), (float) Math.PI);
                    Quaternionf camera = new Quaternionf().rotateX(g * 20.0f * ((float) Math.PI / 180.0f));
                    InventoryScreen.renderEntityInInventory(guiGraphics, cx, cy, sc, pose, camera, renderTarget);
                }

                SprauteScriptScreen.disableEntityAnimations = prevDisable;
                SprauteScriptScreen.hideEntityNameTag = prevHideName;
            } finally {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = null;
                }
                popScissor();
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
            } else if (entityUuid == null) {
                GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                return;
            } else {
            Entity e = findEntityByUuid(mc.level, entityUuid);
            if (!(e instanceof LivingEntity living)) {
                GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                return;
            }
            if (useDummy && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                renderTarget = getOrCreateDummy(living, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
            } else {
                renderTarget = living;
            }
            }

            float sx0f = left + w * cropL;
            float sy0f = top + h * cropT;
            float sx1f = left + w * (1f - cropR);
            float sy1f = top + h * (1f - cropB);
            int sx0 = (int) Math.floor(sx0f);
            int sy0 = (int) Math.floor(sy0f);
            int sx1 = (int) Math.ceil(sx1f);
            int sy1 = (int) Math.ceil(sy1f);
            pushScissor(sx0, sy0, sx1, sy1);
            try {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = renderBones;
                }

                int cx = (int) (left + w * anchorX);
                int cy;
                if (anchorY >= 0f) {
                    cy = (int) (top + h * anchorY);
                } else {
                    float t = Math.min(1f, Math.max(0f, feetCrop));
                    float anchorFrac = 0.48f + t * 0.20f;
                    cy = top + (int) (h * anchorFrac);
                }
                int sc = Math.max(8, (int) (Math.min(w, h) * 0.44f * scale));

                boolean prevDisable = SprauteScriptScreen.disableEntityAnimations;
                boolean prevHideName = SprauteScriptScreen.hideEntityNameTag;
                SprauteScriptScreen.disableEntityAnimations = disableAnim;
                SprauteScriptScreen.hideEntityNameTag = hideNameTag;

                boolean dontFollow = noLookAt || noFollowCursor;
                float lookX = dontFollow ? 0f : (float) cx - mouseX;
                float lookY = dontFollow ? 0f : (float) (cy - 50) - mouseY;

                if (useDummy) {
                    renderEntityDirect(cx, cy, sc, lookX, lookY, renderTarget);
                } else {
                    InventoryScreen.renderEntityInInventory(cx, cy, sc, lookX, lookY, renderTarget);
                }

                SprauteScriptScreen.disableEntityAnimations = prevDisable;
                SprauteScriptScreen.hideEntityNameTag = prevHideName;
            } finally {
                if (renderTarget instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
                    npc.uiRenderBones = null;
                }
                popScissor();
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
            SprauteScriptScreen overlay = new SprauteScriptScreen(root);
            Minecraft mc = Minecraft.getInstance();
            overlay.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            String id = (overlayId != null && !overlayId.isEmpty()) ? overlayId : "";
            if (id.isEmpty() && root.has("id")) {
                id = root.get("id").getAsString();
            }
            if (id.isEmpty()) id = "_default";
            activeOverlays.put(id, overlay);
            activeOverlay = overlay;
            flushPendingWidgetPatches(overlay);
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[Spraute] Invalid Overlay JSON: " + e.getMessage()), false);
            }
        }
    }

    public static void closeOverlayIfActive() {
        activeOverlays.clear();
        activeOverlay = null;
        uiDummyCache.clear();
    }

    public static void closeOverlay(String overlayId) {
        if (overlayId == null || overlayId.isEmpty()) {
            closeOverlayIfActive();
            return;
        }
        activeOverlays.remove(overlayId);
        if (activeOverlays.isEmpty()) {
            activeOverlay = null;
        } else {
            activeOverlay = activeOverlays.values().stream().reduce((a, b) -> b).orElse(null);
        }
    }

    //? if >=1.20.1 {
    private static void renderActiveOverlays(Minecraft mc, float partialTick, GuiGraphics guiGraphics) {
        if (activeOverlays.isEmpty()) return;
        if (mc.screen instanceof SprauteScriptScreen) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        scissorGuiGraphics = guiGraphics;

        for (SprauteScriptScreen overlay : activeOverlays.values()) {
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
                SprauteGuiDraw.fill(guiGraphics, overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
            }

            for (Widget w : overlay.widgets) {
                w.render(overlay, guiGraphics, overlay.left, overlay.top, 0, 0, partialTick);
            }
        }
    }
    //?} else {
    /*private static void renderActiveOverlays(Minecraft mc, float partialTick, PoseStack poseStack) {
        if (activeOverlays.isEmpty()) return;
        if (mc.screen instanceof SprauteScriptScreen) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        for (SprauteScriptScreen overlay : activeOverlays.values()) {
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
                GuiComponent.fill(poseStack, overlay.left, overlay.top, overlay.left + overlay.panelW, overlay.top + overlay.panelH, overlay.bgArgb);
            }

            for (Widget w : overlay.widgets) {
                w.render(overlay, poseStack, overlay.left, overlay.top, 0, 0, partialTick);
            }
        }
    }
    *///?}

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        if (activeOverlays.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        // ChatScreen draws after HUD — overlay is rendered in onChatScreenRender instead.
        if (mc.screen instanceof ChatScreen) return;

        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics());
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack());
        *///?}
    }

    @SubscribeEvent
    public static void onChatScreenRender(ScreenEvent.Render.Post event) {
        if (activeOverlays.isEmpty()) return;
        if (!(event.getScreen() instanceof ChatScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics());
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack());
        *///?}
    }
}
