package org.zonarstudio.spraute_engine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
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
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Script-driven overlay: panels, images, text, buttons, entity preview.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class SprauteScriptScreen extends Screen {

    public static SprauteScriptScreen activeOverlay = null;

    private final JsonObject root;
    private final int panelW;
    private final int panelH;
    private final int bgArgb;
    private final List<Widget> widgets = new ArrayList<>();
    private int left;
    private int top;
    private String activeInputId = null;
    /** When true, do not notify server (programmatic close / replace). */
    private boolean suppressClosePacket;
    private boolean canClose = true;
    public float currentAlpha = 1.0f;

    public static int applyAlpha(int color, float alpha) {
        if (alpha >= 1.0f) return color;
        if (alpha <= 0.0f) return color & 0x00FFFFFF;
        int a = (color >> 24) & 0xFF;
        a = (int) (a * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private SprauteScriptScreen(JsonObject root) {
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
        this.canClose = !root.has("can_close") || root.get("can_close").getAsBoolean();
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
            if (s.endsWith("%") && screenDim > 0) {
                try {
                    float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                    return Math.max(1, (int) (pct / 100f * screenDim));
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SprauteScriptScreen screen) {
            screen.applyWidgetPatch(widgetId, field, value);
        }
        if (activeOverlay != null) {
            activeOverlay.applyWidgetPatch(widgetId, field, value);
        }
    }

    private void applyWidgetPatch(String widgetId, String field, String value) {
        if (widgetId == null || widgetId.isEmpty()) return;
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
                }
            } catch (Exception e) {
            }
            return;
        }

        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i);
            if (widgetId.equals(widgetIdOf(w))) {
                Widget patched = patchWidget(w, f, v);
                if (patched != w) {
                    widgets.set(i, patched);
                }
                return;
            }
            if (w instanceof ScrollW sw) {
                for (int j = 0; j < sw.children.size(); j++) {
                    Widget child = sw.children.get(j);
                    if (widgetId.equals(widgetIdOf(child))) {
                        Widget patched = patchWidget(child, f, v);
                        if (patched != child) {
                            sw.children.set(j, patched);
                        }
                        return;
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
                        return;
                    }
                }
            }
        }
    }

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
                case "ease_in" -> t * t;
                case "ease_out" -> t * (2 - t);
                case "ease_in_out" -> t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
                case "bounce_out" -> {
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
                case "elastic_out" -> {
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

    private static Widget patchWidget(Widget w, String field, String value) {
        if (field.isEmpty()) return w;
        try {
            if (w instanceof TextW tw) {
                return switch (field) {
                    case "x" -> new TextW((int)Float.parseFloat(value.trim()), tw.y, tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "y" -> new TextW(tw.x, (int)Float.parseFloat(value.trim()), tw.text, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "text" -> new TextW(tw.x, tw.y, value, tw.color, tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "color" -> new TextW(tw.x, tw.y, tw.text, parseColor(value), tw.scale, tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    case "scale" -> new TextW(tw.x, tw.y, tw.text, tw.color, Float.parseFloat(value.trim()), tw.tooltip, tw.id, tw.wrapWidth, tw.align, tw.maxLines, tw.maxChars, tw.anchorX, tw.anchorY);
                    default -> w;
                };
            }
            if (w instanceof ButtonW bw) {
                return switch (field) {
                    case "x" -> new ButtonW(bw.id, (int)Float.parseFloat(value.trim()), bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "y" -> new ButtonW(bw.id, bw.x, (int)Float.parseFloat(value.trim()), bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "w" -> new ButtonW(bw.id, bw.x, bw.y, (int)Float.parseFloat(value.trim()), bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "h" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, (int)Float.parseFloat(value.trim()), bw.label(), bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "label" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, value, bw.subLabel(), bw.color, bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "color" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), parseColor(value), bw.hoverColor, bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "hover" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, parseColor(value), bw.texture, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    case "texture" -> new ButtonW(bw.id, bw.x, bw.y, bw.w, bw.h, bw.label(), bw.subLabel(), bw.color, bw.hoverColor, value, bw.tooltip, bw.labelWrap(), bw.labelScale(), bw.subScale());
                    default -> w;
                };
            }
            if (w instanceof RectW rw) {
                return switch (field) {
                    case "x" -> new RectW((int)Float.parseFloat(value.trim()), rw.y, rw.w, rw.h, rw.color, rw.tooltip, rw.id);
                    case "y" -> new RectW(rw.x, (int)Float.parseFloat(value.trim()), rw.w, rw.h, rw.color, rw.tooltip, rw.id);
                    case "w" -> new RectW(rw.x, rw.y, (int)Float.parseFloat(value.trim()), rw.h, rw.color, rw.tooltip, rw.id);
                    case "h" -> new RectW(rw.x, rw.y, rw.w, (int)Float.parseFloat(value.trim()), rw.color, rw.tooltip, rw.id);
                    case "color" -> new RectW(rw.x, rw.y, rw.w, rw.h, parseColor(value), rw.tooltip, rw.id);
                    default -> w;
                };
            }
            if (w instanceof ImageW iw) {
                return switch (field) {
                    case "x" -> new ImageW((int)Float.parseFloat(value.trim()), iw.y, iw.w, iw.h, iw.texture, iw.tooltip, iw.id);
                    case "y" -> new ImageW(iw.x, (int)Float.parseFloat(value.trim()), iw.w, iw.h, iw.texture, iw.tooltip, iw.id);
                    case "w" -> new ImageW(iw.x, iw.y, (int)Float.parseFloat(value.trim()), iw.h, iw.texture, iw.tooltip, iw.id);
                    case "h" -> new ImageW(iw.x, iw.y, iw.w, (int)Float.parseFloat(value.trim()), iw.texture, iw.tooltip, iw.id);
                    case "texture" -> new ImageW(iw.x, iw.y, iw.w, iw.h, value, iw.tooltip, iw.id);
                    default -> w;
                };
            }
            if (w instanceof ItemW iw) {
                return switch (field) {
                    case "x" -> new ItemW((int)Float.parseFloat(value.trim()), iw.y, iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "y" -> new ItemW(iw.x, (int)Float.parseFloat(value.trim()), iw.size, iw.itemId, iw.tooltip, iw.id);
                    case "size", "w", "h" -> new ItemW(iw.x, iw.y, (int)Float.parseFloat(value.trim()), iw.itemId, iw.tooltip, iw.id);
                    case "item", "block" -> new ItemW(iw.x, iw.y, iw.size, value, iw.tooltip, iw.id);
                    default -> w;
                };
            }
            if (w instanceof EntityW ew) {
                return switch (field) {
                    case "x" -> new EntityW((int)Float.parseFloat(value.trim()), ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "y" -> new EntityW(ew.x, (int)Float.parseFloat(value.trim()), ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "w" -> new EntityW(ew.x, ew.y, (int)Float.parseFloat(value.trim()), ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "h" -> new EntityW(ew.x, ew.y, ew.w, (int)Float.parseFloat(value.trim()), ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "scale" -> new EntityW(ew.x, ew.y, ew.w, ew.h, Float.parseFloat(value.trim()), ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "feet_crop" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, Float.parseFloat(value.trim()), ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, ew.anchorY, ew.disableAnim);
                    case "anchor_x" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, clamp01(Float.parseFloat(value.trim())), ew.anchorY, ew.disableAnim);
                    case "anchor_y" -> new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, ew.cropL, ew.cropT, ew.cropR, ew.cropB, ew.anchorX, parseAnchorYPatch(value), ew.disableAnim);
                    case "crop" -> {
                        float[] c = parseCropPatch(value);
                        yield c != null ? new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim) : w;
                    }
                    case "viewport" -> {
                        float[] vp = parseViewportPatch(value);
                        if (vp != null) {
                            float[] c = viewportCornersToCrop(vp);
                            yield new EntityW(ew.x, ew.y, ew.w, ew.h, ew.scale, ew.entityUuid, ew.feetCrop, ew.tooltip, ew.id, c[0], c[1], c[2], c[3], ew.anchorX, ew.anchorY, ew.disableAnim);
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
        if (w.has("anchor_x")) ax = w.get("anchor_x").getAsFloat();
        if (w.has("anchor_y")) ay = w.get("anchor_y").getAsFloat();
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

    private Widget parseOneWidget(JsonObject w, String tooltip, int pw, int ph) {
        String type = w.has("type") ? w.get("type").getAsString().toLowerCase() : "";
        int x = readCoord(w, "x", pw);
        int y = readCoord(w, "y", ph);
        int ww = readCoord(w, "w", pw);
        int hh = readCoord(w, "h", ph);
        String wid = w.has("id") ? w.get("id").getAsString() : "";
        return switch (type) {
            case "rect", "panel" -> new RectW(x, y, ww, hh, parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFFFF"), tooltip, wid);
            case "grid_bg" -> new GridBgW(x, y, ww, hh, 
                    w.has("grid_type") ? w.get("grid_type").getAsString() : "hv",
                    w.has("cell_size") ? w.get("cell_size").getAsInt() : 20,
                    w.has("thickness") ? w.get("thickness").getAsInt() : 1,
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), tooltip, wid);
            case "image" -> new ImageW(x, y, ww, hh, w.has("texture") ? w.get("texture").getAsString() : "minecraft:textures/misc/unknown_pack.png", tooltip, wid);
            case "text" -> {
                float[] anchors = parseAnchor(w);
                yield new TextW(x, y, w.has("text") ? w.get("text").getAsString() : "", parseColor(w.has("color") ? w.get("color").getAsString() : "#FFFFFF"), w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip, wid,
                        w.has("wrap") ? readCoord(w, "wrap", pw) : 0,
                        w.has("align") ? w.get("align").getAsString().toLowerCase() : "left",
                        w.has("max_lines") ? w.get("max_lines").getAsInt() : 0,
                        w.has("max_chars") ? w.get("max_chars").getAsInt() : 0,
                        anchors[0], anchors[1]);
            }
            case "button" -> new ButtonW(
                    w.has("id") ? w.get("id").getAsString() : "",
                    x, y, ww, hh,
                    w.has("label") ? w.get("label").getAsString() : "",
                    w.has("sub_label") ? w.get("sub_label").getAsString() : "",
                    parseColor(w.has("color") ? w.get("color").getAsString() : "#55336688"),
                    parseColor(w.has("hover") ? w.get("hover").getAsString() : "#66447799"),
                    w.has("texture") ? w.get("texture").getAsString() : null,
                    tooltip,
                    w.has("label_wrap") ? readCoord(w, "label_wrap", pw) : 0,
                    w.has("label_scale") ? w.get("label_scale").getAsFloat() : 1f,
                    w.has("sub_scale") ? w.get("sub_scale").getAsFloat() : 0.65f);
            case "entity" -> {
                UUID uuid = null;
                if (w.has("entityUuid")) {
                    try {
                        uuid = UUID.fromString(w.get("entityUuid").getAsString());
                    } catch (Exception ignored) {}
                }
                float scale = w.has("scale") ? w.get("scale").getAsFloat() : 1f;
                float feetCrop = w.has("feet_crop") ? w.get("feet_crop").getAsFloat() : 0.38f;
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
                        anchorY = ay < 0f ? -1f : clamp01(ay);
                    }
                } else {
                    if (w.has("anchor_x")) anchorX = clamp01(w.get("anchor_x").getAsFloat());
                    if (w.has("anchor_y")) anchorY = clamp01(w.get("anchor_y").getAsFloat());
                }
                boolean disableAnim = false;
                if (w.has("animation")) {
                    JsonElement animEl = w.get("animation");
                    if (animEl.isJsonPrimitive()) {
                        if (animEl.getAsJsonPrimitive().isBoolean()) disableAnim = !animEl.getAsBoolean();
                        else if (animEl.getAsString().equalsIgnoreCase("false")) disableAnim = true;
                    }
                }
                yield new EntityW(x, y, ww, hh, scale, uuid, feetCrop, tooltip, wid, crop[0], crop[1], crop[2], crop[3], anchorX, anchorY, disableAnim);
            }
            case "scroll" -> {
                int contentH = readCoord(w, "content_h", ph);
                int scrollBg = parseColor(w.has("color") ? w.get("color").getAsString() : "#00000000");
                boolean autoBar = w.has("auto_scrollbar") && w.get("auto_scrollbar").getAsBoolean();
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
                    parseColor(w.has("bg_color") ? w.get("bg_color").getAsString() : "#FF000000"),
                    parseColor(w.has("outline_color") ? w.get("outline_color").getAsString() : "#FFAAAAAA"),
                    w.has("scale") ? w.get("scale").getAsFloat() : 1f, tooltip,
                    w.has("max_chars") ? w.get("max_chars").getAsInt() : 32,
                    w.has("input_type") ? w.get("input_type").getAsString().toLowerCase() : "text");
            case "divider" -> new DividerW(x, y, ww, parseColor(w.has("color") ? w.get("color").getAsString() : "#44FFFFFF"), wid);
            case "item", "block" -> {
                String itemId = "minecraft:stone";
                if (w.has("item")) itemId = w.get("item").getAsString();
                else if (w.has("block")) itemId = w.get("block").getAsString();
                int itemSize = w.has("size") ? w.get("size").getAsInt() : (ww > 0 ? ww : 16);
                yield new ItemW(x, y, itemSize, itemId, tooltip, wid);
            }
            default -> null;
        };
    }

    private record WidgetEntry(int layer, int order, Widget widget) {}

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
        return clamp01(f);
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

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        processAnimations();
        renderBackground(poseStack);
        int ax0 = left;
        int ay0 = top;
        GuiComponent.fill(poseStack, ax0, ay0, ax0 + panelW, ay0 + panelH, bgArgb);
        for (Widget w : widgets) {
            w.render(this, poseStack, ax0, ay0, mouseX, mouseY, partialTick);
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            String tip = w.tooltip();
            if (tip != null && !tip.isEmpty() && w.contains(this, ax0, ay0, mouseX, mouseY)) {
                renderTooltip(poseStack, Component.literal(tip), mouseX, mouseY);
                break;
            }
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

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
            ModNetwork.CHANNEL.sendToServer(new SprauteUiActionPacket("", true));
        }
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
        void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick);

        default String tooltip() {
            return null;
        }

        default boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            return false;
        }
    }

    private record RectW(int x, int y, int w, int h, int color, String tooltip, String id) implements Widget {
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

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            GuiComponent.fill(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + h, applyAlpha(color, screen.currentAlpha));
        }
    }

    private record GridBgW(int x, int y, int w, int h, String gridType, int cellSize, int thickness, int color, String tooltip, String id) implements Widget {
        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        @Override
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
    }

    private record ImageW(int x, int y, int w, int h, String texture, String tooltip, String id) implements Widget {
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

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ResourceLocation rl = SprauteUiJson.textureRl(texture);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, screen.currentAlpha);
            RenderSystem.setShaderTexture(0, rl);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GuiComponent.blit(poseStack, ax0 + x, ay0 + y, 0f, 0f, w, h, 256, 256);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private record TextW(int x, int y, String text, int color, float scale, String tooltip, String id, int wrapWidth, String align, int maxLines, int maxChars, float anchorX, float anchorY) implements Widget {
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

        @Override
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
    }

    private record InputW(String id, int x, int y, int w, int h, String text, String placeholder, int color, int bgColor, int outlineColor, float scale, String tooltip, int maxChars, String type) implements Widget {
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

        @Override
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
    }

    private record ButtonW(String id, int x, int y, int w, int h, String label, String subLabel, int color, int hoverColor, String texture, String tooltip,
                           int labelWrap, float labelScale, float subScale) implements Widget {
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

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int bx = ax0 + x;
            int by = ay0 + y;
            boolean over = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
            if (texture != null && !texture.isEmpty()) {
                ResourceLocation rl = SprauteUiJson.textureRl(texture);
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

        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            if (alpha <= 0.0f) return;
            float oldAlpha = screen.currentAlpha;
            screen.currentAlpha *= alpha;
            int sx = ax0 + x, sy = ay0 + y;
            GuiComponent.enableScissor(sx, sy, sx + w, sy + h);
            try {
                for (Widget child : children) {
                    child.render(screen, poseStack, sx, sy, mouseX, mouseY, partialTick);
                }
            } finally {
                GuiComponent.disableScissor();
            }
            screen.currentAlpha = oldAlpha;
        }
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

        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + w && my >= ly && my < ly + h;
        }

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int sx = ax0 + x, sy = ay0 + y;
            int currentBgColor = applyAlpha(bgColor, screen.currentAlpha);
            if ((currentBgColor & 0xFF000000) != 0) {
                GuiComponent.fill(poseStack, sx, sy, sx + w, sy + h, currentBgColor);
            }
            GuiComponent.enableScissor(sx, sy, sx + w, sy + h);
            try {
                int offsetY = -(int) scrollOffset;
                for (Widget child : children) {
                    child.render(screen, poseStack, sx, sy + offsetY, mouseX, mouseY, partialTick);
                }
            } finally {
                GuiComponent.disableScissor();
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
    }

    private record DividerW(int x, int y, int w, int color, String id) implements Widget {
        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            GuiComponent.fill(poseStack, ax0 + x, ay0 + y, ax0 + x + w, ay0 + y + 1, color);
        }
    }

    private record ItemW(int x, int y, int size, String itemId, String tooltip, String id) implements Widget {
        @Override
        public String tooltip() { return tooltip; }

        @Override
        public boolean contains(SprauteScriptScreen screen, int ax0, int ay0, int mx, int my) {
            int lx = ax0 + x, ly = ay0 + y;
            return mx >= lx && mx < lx + size && my >= ly && my < ly + size;
        }

        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            ResourceLocation rl = itemId.contains(":") ? new ResourceLocation(itemId) : new ResourceLocation("minecraft", itemId);
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                Block block = ForgeRegistries.BLOCKS.getValue(rl);
                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    item = block.asItem();
                }
            }
            if (item == null || item == net.minecraft.world.item.Items.AIR) return;
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
    }

    public static boolean disableEntityAnimations = false;

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
            float anchorX, float anchorY, boolean disableAnim
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
        @Override
        public void render(SprauteScriptScreen screen, PoseStack poseStack, int ax0, int ay0, int mouseX, int mouseY, float partialTick) {
            int left = ax0 + x;
            int top = ay0 + y;
            int right = left + w;
            int bottom = top + h;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || entityUuid == null) {
                GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                return;
            }
            Entity e = findEntityByUuid(mc.level, entityUuid);
            if (!(e instanceof LivingEntity living)) {
                GuiComponent.fill(poseStack, left, top, right, bottom, 0x66000000);
                return;
            }
            float sx0f = left + w * cropL;
            float sy0f = top + h * cropT;
            float sx1f = left + w * (1f - cropR);
            float sy1f = top + h * (1f - cropB);
            int sx0 = (int) Math.floor(sx0f);
            int sy0 = (int) Math.floor(sy0f);
            int sx1 = (int) Math.ceil(sx1f);
            int sy1 = (int) Math.ceil(sy1f);
            GuiComponent.enableScissor(sx0, sy0, sx1, sy1);
            try {
                int cx = (int) (left + w * anchorX);
                int cy;
                if (anchorY >= 0f) {
                    cy = (int) (top + h * anchorY);
                } else {
                    float t = Math.min(1f, Math.max(0f, feetCrop));
                    float anchorFrac = 0.48f + t * 0.20f;
                    cy = top + (int) (h * anchorFrac);
                }
                int sc = Math.max(18, (int) (Math.min(w, h) * 0.44f * scale));
                boolean prevDisable = SprauteScriptScreen.disableEntityAnimations;
                SprauteScriptScreen.disableEntityAnimations = disableAnim;
                InventoryScreen.renderEntityInInventory(cx, cy, sc, (float) cx - mouseX, (float) (cy - 50) - mouseY, living);
                SprauteScriptScreen.disableEntityAnimations = prevDisable;
            } finally {
                GuiComponent.disableScissor();
            }
        }
    }

    public static void openOverlay(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            activeOverlay = new SprauteScriptScreen(root);
            Minecraft mc = Minecraft.getInstance();
            activeOverlay.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } catch (Exception e) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[Spraute] Invalid Overlay JSON: " + e.getMessage()), false);
            }
        }
    }

    public static void closeOverlayIfActive() {
        activeOverlay = null;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        if (activeOverlay == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SprauteScriptScreen) {
            // Don't draw overlay if a SprauteScriptScreen is currently open, to avoid overlap
            return;
        }

        // Setup dimensions for overlay
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        
        // Default position logic (similar to init())
        activeOverlay.left = (sw - activeOverlay.panelW) / 2;
        activeOverlay.top = (sh - activeOverlay.panelH) / 2;
        if (activeOverlay.root.has("x")) activeOverlay.left = readRootExtent(activeOverlay.root, "x", sw, activeOverlay.left);
        if (activeOverlay.root.has("y")) activeOverlay.top = readRootExtent(activeOverlay.root, "y", sh, activeOverlay.top);

        activeOverlay.processAnimations();

        PoseStack poseStack = event.getPoseStack();
        if (activeOverlay.bgArgb != 0) {
            GuiComponent.fill(poseStack, activeOverlay.left, activeOverlay.top, activeOverlay.left + activeOverlay.panelW, activeOverlay.top + activeOverlay.panelH, activeOverlay.bgArgb);
        }

        for (Widget w : activeOverlay.widgets) {
            w.render(activeOverlay, poseStack, activeOverlay.left, activeOverlay.top, 0, 0, event.getPartialTick());
        }
    }
}
