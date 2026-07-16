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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
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
    private final int bgArgb;
    /** When false, skip the vanilla Screen background dimming (used for cinematic dialogs). */
    private boolean dimBackground = true;
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
    /** True for HUD chat overlays ({@link #openOverlay}); false for full {@link #open} screens. */
    private final boolean hudOverlay;

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
        this(root, false);
    }

    private SprauteScriptScreen(JsonObject root, boolean hudOverlay) {
        super(Component.empty());
        this.root = root;
        this.hudOverlay = hudOverlay;
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
            if (applyWidgetPatchInList(sw.children, widgetId, field, value)) return w;
        }
        if (w instanceof ClipW cw) {
            if (applyWidgetPatchInList(cw.children, widgetId, field, value)) return w;
        }
        if (w instanceof GroupW gw) {
            if (applyWidgetPatchInList(gw.children, widgetId, field, value)) return w;
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

    private static int[] texturePixelSize(ResourceLocation rl) {
        return texturePixelSize(rl, null);
    }

    private static int[] texturePixelSize(ResourceLocation rl, String pathHint) {
        int[] fromDisk = readTextureSizeFromWorkspace(pathHint, rl);
        if (fromDisk != null) {
            return fromDisk;
        }
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
        // Edges — tile along the long axis (border unit b×b → bs on screen).
        if (midW > 0 && srcMidW > 0) {
            nineTileH(guiGraphics, rl, ix + bs, iy, midW, bs, b, 0, b, b, bs, tw, th);
            nineTileH(guiGraphics, rl, ix + bs, iy + h - bs, midW, bs, b, th - b, b, b, bs, tw, th);
        }
        if (midH > 0 && srcMidH > 0) {
            nineTileV(guiGraphics, rl, ix, iy + bs, bs, midH, 0, b, b, b, bs, tw, th);
            nineTileV(guiGraphics, rl, ix + w - bs, iy + bs, bs, midH, tw - b, b, b, b, bs, tw, th);
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
                return switch (field) { case "x" -> rw.x; case "y" -> rw.y; case "w" -> rw.w; case "h" -> rw.h; default -> 0f; };
            }
            if (w instanceof ImageW iw) {
                return switch (field) { case "x" -> iw.x; case "y" -> iw.y; case "w" -> iw.w; case "h" -> iw.h; default -> 0f; };
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
            if (w instanceof InputW inpw) {
                return switch (field) { case "x" -> inpw.x; case "y" -> inpw.y; case "w" -> inpw.w; case "h" -> inpw.h; default -> 0f; };
            }
            if (w instanceof ItemW iw) {
                return switch (field) { case "x" -> iw.x; case "y" -> iw.y; case "size", "w", "h" -> iw.size; default -> 0f; };
            }
            if (w instanceof ClipW cw) {
                return switch (field) { case "x" -> cw.x; case "y" -> cw.y; case "w" -> cw.w; case "h" -> cw.h; case "alpha" -> cw.alpha; default -> 0f; };
            }
            if (w instanceof GroupW gw) {
                return switch (field) { case "x" -> gw.x; case "y" -> gw.y; case "w" -> gw.w; case "h" -> gw.h; case "alpha" -> gw.alpha; default -> 0f; };
            }
            if (w instanceof RotatedW rw) {
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
        if (w instanceof GroupW gw) {
            for (Widget gcw : gw.children) {
                Widget found = findWidgetByIdRec(gcw, id);
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
                    case "x" -> new ButtonW(bw.id, (int)Float.parseFloat(value.trim()), bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "y" -> new ButtonW(bw.id, bw.x, (int)Float.parseFloat(value.trim()), bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "w" -> new ButtonW(bw.id, bw.x, bw.y, (int)Float.parseFloat(value.trim()), bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "h" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, (int)Float.parseFloat(value.trim()), bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "label" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, value, bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "color" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), parseColor(value), bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "hover" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, parseColor(value), bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "texture" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, value, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
                    case "tooltip" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, value, bw.labelWrap(), bw.labelScale(), bw.subScale(), bw.sliceBorders(), bw.sliceScale(), bw.hoverChildren(), bw.hoverPw(), bw.hoverPh());
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
                    case "x" -> new EntityW((int)Float.parseFloat(value.trim()), ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "y" -> new EntityW(ew.x, (int)Float.parseFloat(value.trim()), ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "w" -> new EntityW(ew.x, ew.y, (int)Float.parseFloat(value.trim()), ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "h" -> new EntityW(ew.x, ew.y, ew.w, (int)Float.parseFloat(value.trim()), ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "scale" -> new EntityW(ew.x, ew.y, ew.w, ew.h, Float.parseFloat(value.trim()), ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "feetCrop" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, Float.parseFloat(value.trim()), ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "anchorX" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, clamp01(Float.parseFloat(value.trim())), ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "anchorY" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, parseAnchorYPatch(value), ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
                    case "crop" -> {
                        float[] c = parseCropPatch(value);
                        yield c != null ? new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity) : w;
                    }
                    case "viewport" -> {
                        float[] vp = parseViewportPatch(value);
                        if (vp != null) {
                            float[] c = viewportCornersToCrop(vp);
                            yield new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim, ew.hideNameTag, ew.noLookAt, ew.noFollowCursor, ew.noHurtAnim, ew.renderBones, ew.skinPlayerUuid, ew.modelGeo, ew.modelTexture, ew.modelAnim, ew.modelIdle, ew.autoScale, ew.clipEntity);
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
                return switch (field) {
                    case "x" -> { ScrollW n = new ScrollW((int)Float.parseFloat(value.trim()), sw.y, sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "y" -> { ScrollW n = new ScrollW(sw.x, (int)Float.parseFloat(value.trim()), sw.w, sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "w" -> { ScrollW n = new ScrollW(sw.x, sw.y, (int)Float.parseFloat(value.trim()), sw.h, sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "h" -> { ScrollW n = new ScrollW(sw.x, sw.y, sw.w, (int)Float.parseFloat(value.trim()), sw.contentH, sw.bgColor, sw.tooltip, sw.id, sw.showBar, sw.autoBar); n.scrollOffset = sw.scrollOffset; n.children.addAll(sw.children); yield n; }
                    case "scrolloffset", "offset", "scrolly" -> {
                        float off = Float.parseFloat(value.trim());
                        float maxScroll = Math.max(0, sw.contentH - sw.h);
                        sw.scrollOffset = Math.max(0, Math.min(off, maxScroll));
                        yield sw;
                    }
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
            if (w instanceof GroupW gw) {
                return switch (field) {
                    case "x" -> { GroupW n = new GroupW((int)Float.parseFloat(value.trim()), gw.y, gw.w, gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "y" -> { GroupW n = new GroupW(gw.x, (int)Float.parseFloat(value.trim()), gw.w, gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "w" -> { GroupW n = new GroupW(gw.x, gw.y, (int)Float.parseFloat(value.trim()), gw.h, gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "h" -> { GroupW n = new GroupW(gw.x, gw.y, gw.w, (int)Float.parseFloat(value.trim()), gw.alpha, gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
                    case "alpha" -> { GroupW n = new GroupW(gw.x, gw.y, gw.w, gw.h, Float.parseFloat(value.trim()), gw.tooltip, gw.id); n.children.addAll(gw.children); yield n; }
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
                float sliceScale = w.has("slice_scale") ? w.get("slice_scale").getAsFloat() : 1f;
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
                    w.has("slice_borders") ? w.get("slice_borders").getAsInt() : 0,
                    w.has("slice_scale") ? w.get("slice_scale").getAsFloat() : 1f,
                    hoverChildren, hpw, hph);
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
                yield new EntityW(x, y, ww, hh, scale, uuid, feetCrop, tooltip, wid, crop[0], crop[1], crop[2], crop[3], anchorX, anchorY, disableAnim, hideNameTag, noLookAt, noFollowCursor, noHurtAnim, renderBones, skinPlayerUuid, modelGeo, modelTexture, modelAnim, modelIdle, autoScale, clipEntity);
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
            case "group" -> {
                float alpha = w.has("alpha") ? w.get("alpha").getAsFloat() : 1.0f;
                GroupW group = new GroupW(x, y, ww, hh, alpha, tooltip, wid);
                if (w.has("children")) {
                    JsonArray children = w.getAsJsonArray("children");
                    for (JsonElement cel : children) {
                        if (!cel.isJsonObject()) continue;
                        JsonObject cw = cel.getAsJsonObject();
                        String ct = cw.has("tooltip") ? cw.get("tooltip").getAsString() : null;
                        Widget child = parseOneWidget(cw, ct, ww, hh);
                        if (child != null) group.children.add(child);
                    }
                }
                yield group;
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
    public void renderBackground(GuiGraphics guiGraphics) {
        if (dimBackground) super.renderBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        scissorGuiGraphics = guiGraphics;
        processAnimations();
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
        // Full (non-HUD) screens like the chat history draw ABOVE the HUD chat overlays,
        // so don't paint the chat bars on top of them.
        if (hudOverlay) {
            renderActiveOverlays(Minecraft.getInstance(), partialTick, guiGraphics);
        }
    }
    //?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        processAnimations();
        if (dimBackground) renderBackground(poseStack);
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

    /** Topmost button under cursor in this panel (includes scroll children). */
    private String findButtonAt(double mouseX, double mouseY) {
        int ax0 = left;
        int ay0 = top;
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
                                return bw.id;
                            }
                        }
                    }
                }
            }
            if (w instanceof ButtonW bw && bw.id != null && !bw.id.isEmpty()) {
                int bx = ax0 + bw.x;
                int by = ay0 + bw.y;
                if (mouseX >= bx && mouseX < bx + bw.w && mouseY >= by && mouseY < by + bw.h) {
                    return bw.id;
                }
            }
        }
        return null;
    }

    /** Hover target: button or input with id. */
    private String findHoverTargetAt(double mouseX, double mouseY) {
        int ax0 = left;
        int ay0 = top;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w instanceof ScrollW sw) {
                int sx = ax0 + sw.x;
                int sy = ay0 + sw.y;
                if (mouseX >= sx && mouseX < sx + sw.w && mouseY >= sy && mouseY < sy + sw.h) {
                    for (int j = sw.children.size() - 1; j >= 0; j--) {
                        Widget child = sw.children.get(j);
                        String id = hoverTargetId(child, sx, sy - (int) sw.scrollOffset, mouseX, mouseY);
                        if (id != null) return id;
                    }
                }
            }
            String id = hoverTargetId(w, ax0, ay0, mouseX, mouseY);
            if (id != null) return id;
        }
        return null;
    }

    private static String hoverTargetId(Widget w, int ax0, int ay0, double mouseX, double mouseY) {
        if (w instanceof ButtonW bw && bw.id != null && !bw.id.isEmpty()) {
            int bx = ax0 + bw.x;
            int by = ay0 + bw.y;
            if (mouseX >= bx && mouseX < bx + bw.w && mouseY >= by && mouseY < by + bw.h) return bw.id;
        } else if (w instanceof InputW inpw && inpw.id != null && !inpw.id.isEmpty()) {
            int bx = ax0 + inpw.x;
            int by = ay0 + inpw.y;
            if (mouseX >= bx && mouseX < bx + inpw.w && mouseY >= by && mouseY < by + inpw.h) return inpw.id;
        }
        return null;
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
            SprauteScriptScreen overlay = entries.get(i).getValue();
            overlay.layoutPosition(sw, sh);
            String id = overlay.findHoverTargetAt(mouseX, mouseY);
            if (id != null) return id;
        }
        return null;
    }

    private static String findTopmostHover(double mouseX, double mouseY, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay) {
            screen.layoutPosition(sw, sh);
            return screen.findHoverTargetAt(mouseX, mouseY);
        }
        return findTopmostHoverAcrossOverlays(mouseX, mouseY, sw, sh);
    }

    private static void tickOverlayHover() {
        if (++hoverTickCounter < 2) return;
        hoverTickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getWindow() == null) return;

        boolean hasUi = !activeOverlays.isEmpty()
                || (mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay);
        if (!hasUi) {
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
        tickOverlayHover();
    }

    @SubscribeEvent
    public static void onGlobalMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || activeOverlays.isEmpty()) return;
        if (mc.screen instanceof SprauteScriptScreen screen && !screen.hudOverlay) return;

        double[] m = scaledMouse(mc);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        String widgetId = findTopmostButtonAcrossOverlays(m[0], m[1], sw, sh);
        if (widgetId != null) {
            sendUiClick(widgetId, button);
            event.setCanceled(true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int ax0 = left;
            int ay0 = top;
            boolean clickedInput = false;
            String clickedButton = findButtonAt(mouseX, mouseY);
            if (clickedButton != null) {
                sendUiClick(clickedButton, button);
                return true;
            }
            for (int i = widgets.size() - 1; i >= 0; i--) {
                Widget w = widgets.get(i);
                if (w instanceof ScrollW sw) {
                    int sx = ax0 + sw.x;
                    int sy = ay0 + sw.y;
                    if (mouseX >= sx && mouseX < sx + sw.w && mouseY >= sy && mouseY < sy + sw.h) {
                        for (int j = sw.children.size() - 1; j >= 0; j--) {
                            Widget child = sw.children.get(j);
                            if (child instanceof InputW inpw && inpw.id != null && !inpw.id.isEmpty()) {
                                int bx = sx + inpw.x;
                                int by = sy + inpw.y - (int) sw.scrollOffset;
                                if (mouseX >= bx && mouseX < bx + inpw.w && mouseY >= by && mouseY < by + inpw.h) {
                                    activeInputId = inpw.id;
                                    clickedInput = true;
                                }
                            }
                        }
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
            if (clickedInput) return true;
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
                    if (sw.id != null && !sw.id.isEmpty()) {
                        ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket(
                                SprauteUiActionPacket.ACTION_SCROLL, sw.id, (int) sw.scrollOffset));
                    }
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
                          int sliceBorders, float sliceScale, int srcU, int srcV, int srcW, int srcH) implements Widget {
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
                renderNineSlice(guiGraphics, rl, ax0 + x, ay0 + y, w, h, sliceBorders, sliceScale, texture);
            } else {
                int[] src = resolveImageSrcRect(texture, srcU, srcV, srcW, srcH);
                blitImageTexture(guiGraphics, rl, ax0 + x, ay0 + y, w, h, src[0], src[1], src[2], src[3]);
            }

            guiGraphics.setColor(1f, 1f, 1f, 1f);
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
                renderNineSlice(poseStack, rl, ax0 + x, ay0 + y, w, h, sliceBorders, sliceScale, texture);
            } else {
                int[] src = resolveImageSrcRect(texture, srcU, srcV, srcW, srcH);
                blitImageTexture(poseStack, rl, ax0 + x, ay0 + y, w, h, src[0], src[1], src[2], src[3]);
            }

            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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
                           int labelWrap, float labelScale, float subScale, int sliceBorders, float sliceScale,
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
                if (sliceBorders > 0) {
                    renderNineSlice(guiGraphics, rl, bx, by, w, h, sliceBorders, sliceScale, texture);
                } else {
                    int[] ts = texturePixelSize(rl);
                    SprauteGuiDraw.blitRegion(guiGraphics, rl, bx, by, w, h, 0, 0, ts[0], ts[1], ts[0], ts[1]);
                }
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
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                if (sliceBorders > 0) {
                    renderNineSlice(poseStack, rl, bx, by, w, h, sliceBorders, sliceScale, texture);
                } else {
                    int[] ts = texturePixelSize(rl);
                    SprauteGuiDraw.blitRegion(poseStack, bx, by, w, h, 0, 0, ts[0], ts[1], ts[0], ts[1]);
                }
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

    /**
     * Build (or fetch from cache) a render-only NPC dummy directly from a model+texture spec.
     * Lets the GUI show any NPC's model even when the live entity isn't loaded (e.g. chat history).
     */
    private static org.zonarstudio.spraute_engine.entity.SprauteNpcEntity getOrCreateDummyFromSpec(
            String geo, String texture, String anim, String idle, boolean noLookAt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        String safeGeo = geo != null ? geo : "geo/defolt.geo.json";
        String safeTex = texture != null ? texture : "textures/entity/defolt.png";
        String safeAnim = anim != null && !anim.isEmpty() ? anim : "animations/npc_classic.animation.json";
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
            boolean autoScale, boolean clipEntity
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
                if (e instanceof LivingEntity living) {
                    if (useDummy && living instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
                        renderTarget = getOrCreateDummy(living, noLookAt, disableAnim, noHurtAnim, skinPlayerUuid);
                    } else {
                        renderTarget = living;
                    }
                } else if (modelGeo != null && !modelGeo.isEmpty()) {
                    // Live entity not loaded — render from the stored model/texture spec.
                    org.zonarstudio.spraute_engine.entity.SprauteNpcEntity spec =
                            getOrCreateDummyFromSpec(modelGeo, modelTexture, modelAnim, modelIdle, noLookAt);
                    if (spec == null) {
                        SprauteGuiDraw.fill(guiGraphics, left, top, right, bottom, 0x66000000);
                        return;
                    }
                    renderTarget = spec;
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
                        || (modelGeo != null && !modelGeo.isEmpty())
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

                // clipEntity keeps the active GL scissor so the head is cropped to its box
                // (used by chat history rows); otherwise the entity is allowed to overflow.
                if (clipEntity) {
                    drawEntity.run();
                } else {
                    runWithoutScissor(drawEntity);
                }

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
            String id = (overlayId != null && !overlayId.isEmpty()) ? overlayId : "";
            if (id.isEmpty() && root.has("id")) {
                id = root.get("id").getAsString();
            }
            boolean chatOverlay = "spraute_chat_bar".equals(id)
                    || "spraute_chat".equals(id)
                    || id.startsWith("spraute_chat_");
            if (chatOverlay) {
                activeOverlays.entrySet().removeIf(e ->
                        "spraute_chat_bar".equals(e.getKey())
                                || "spraute_chat".equals(e.getKey())
                                || e.getKey().startsWith("spraute_chat_"));
                // Drop only stale fade-OUT patches; keep fade-in (~ANIM:...:1.0).
                pendingWidgetPatches.removeIf(p ->
                        "chat_clip".equals(p.widgetId)
                                && p.field != null && "alpha".equalsIgnoreCase(p.field.trim())
                                && p.value != null && p.value.startsWith("~ANIM:")
                                && (p.value.endsWith(":0.0") || p.value.endsWith(":0")));
            } else if (!id.isEmpty()) {
                activeOverlays.remove(id);
            }
            SprauteScriptScreen overlay = new SprauteScriptScreen(root, true);
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

        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        int imx = (int) mouseX;
        int imy = (int) mouseY;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        scissorGuiGraphics = guiGraphics;

        for (Map.Entry<String, SprauteScriptScreen> e : activeOverlays.entrySet()) {
            SprauteScriptScreen overlay = e.getValue();
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
                w.render(overlay, guiGraphics, overlay.left, overlay.top, imx, imy, partialTick);
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (CameraHandler.shouldHideScriptGui()) return;
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        if (activeOverlays.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        // ChatScreen draws after HUD — overlay is rendered in onChatScreenRender instead.
        if (mc.screen instanceof ChatScreen) return;
        // SprauteScriptScreen renders overlays at the end of its own render() pass.
        if (mc.screen instanceof SprauteScriptScreen) return;

        //? if >=1.20.1 {
        renderActiveOverlays(mc, event.getPartialTick(), event.getGuiGraphics());
        //?} else {
        /*renderActiveOverlays(mc, event.getPartialTick(), event.getPoseStack());
        *///?}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChatScreenRender(ScreenEvent.Render.Post event) {
        if (CameraHandler.shouldHideScriptGui()) return;
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
