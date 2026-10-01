package org.zonarstudio.spraute_engine.client;

//? if <1.20.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=1.20.1 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiComponent;
*///?}
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class LoadScreenOverlay {

    public static boolean active = false;
    private static long startTime = 0;
    
    // Login Screen (old mode)
    private static boolean isLogin = true;

    private static final net.minecraft.resources.ResourceLocation LOGO_TEX =
            new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, "textures/gui/logo.png");
    /** Fallback if texture can't be read; keep in sync with logo.png. */
    private static final int LOGO_TEX_W_FALLBACK = 3432;
    private static final int LOGO_TEX_H_FALLBACK = 856;
    private static int logoTexW = -1;
    private static int logoTexH = -1;
    
    // FadeIn properties
    private static String text = null;
    private static String subtitle = null;
    private static String texture = null;
    private static int color = 0x000000;
    private static float timeIn = 1.0f;
    private static float timeOut = 1.0f;
    private static float visibleTime = 2.0f;
    private static boolean autoFadeOut = false;
    /** Scene that started this fade; cleared when that scene (or all scenes) stop. */
    private static String ownerSceneId = "";
    private static boolean manualFadeOutTriggered = false;
    private static long manualFadeOutStartTime = 0;
    private static float manualFadeOutStartAlpha = 1.0f;
    /** When true, vanilla HUD overlays are cancelled. Default: draw on top, do not hide. */
    private static boolean hideOtherGui = false;
    /**
     * Chat / HUD text sits around Z≈50 and uses see-through batches that flush after
     * a later translucent {@code fill} — looks like the chat is subtracted from the fade.
     * Draw above script overlays ({@code 400}) and every vanilla HUD layer.
     */
    private static final float COVER_Z = 800.0f;

    public static void triggerLogin() {
        active = true;
        isLogin = true;
        hideOtherGui = false;
        startTime = System.currentTimeMillis();
    }

    public static void triggerFadeIn(Map<String, Object> props) {
        if (props != null && Boolean.TRUE.equals(props.get("triggerFadeOut"))) {
            applyFadeOutProps(props);
            triggerFadeOut();
            return;
        }

        active = true;
        isLogin = false;
        startTime = System.currentTimeMillis();
        manualFadeOutTriggered = false;
        manualFadeOutStartAlpha = 1.0f;
        
        text = props.containsKey("text") ? String.valueOf(props.get("text")) : null;
        subtitle = props.containsKey("subtitle") ? String.valueOf(props.get("subtitle")) : null;
        texture = props.containsKey("texture") ? String.valueOf(props.get("texture")) : null;
        
        color = 0x000000; // default
        if (props.containsKey("color")) {
            color = org.zonarstudio.spraute_engine.script.SprauteColors.toRgbInt(props.get("color"));
        }
        
        timeIn = numberProp(props, "time", 1.0f);
        timeOut = numberProp(props, "timeOut",
                numberProp(props, "timeout",
                        numberProp(props, "fadeOutTime", timeIn)));
        visibleTime = numberProp(props, "visibleTime",
                numberProp(props, "visible_time", 2.0f));
        
        autoFadeOut = parseBoolProp(props.get("fadeout"));
        hideOtherGui = parseHideOtherGui(props);
        ownerSceneId = sceneIdFromProps(props);
    }

    private static boolean parseHideOtherGui(Map<String, Object> props) {
        if (props == null) return false;
        if (props.containsKey("hide_gui")) return parseBoolProp(props.get("hide_gui"));
        if (props.containsKey("hideGui")) return parseBoolProp(props.get("hideGui"));
        if (props.containsKey("hideOtherGui")) return parseBoolProp(props.get("hideOtherGui"));
        if (props.containsKey("hide_other_gui")) return parseBoolProp(props.get("hide_other_gui"));
        return false;
    }

    private static void applyFadeOutProps(Map<String, Object> props) {
        if (props == null) return;
        if (props.get("time") instanceof Number n) {
            timeOut = n.floatValue();
        } else {
            Float named = optionalNumber(props, "timeOut");
            if (named == null) named = optionalNumber(props, "timeout");
            if (named == null) named = optionalNumber(props, "fadeOutTime");
            if (named != null) timeOut = named;
        }
        if (hasText(props, "text")) text = String.valueOf(props.get("text"));
        if (hasText(props, "subtitle")) subtitle = String.valueOf(props.get("subtitle"));
    }

    private static boolean hasText(Map<String, Object> props, String key) {
        if (!props.containsKey(key) || props.get(key) == null) return false;
        String s = String.valueOf(props.get(key)).trim();
        return !s.isEmpty() && !"null".equalsIgnoreCase(s);
    }

    private static float numberProp(Map<String, Object> props, String key, float fallback) {
        Float n = optionalNumber(props, key);
        return n != null ? n : fallback;
    }

    private static Float optionalNumber(Map<String, Object> props, String key) {
        if (props == null || !props.containsKey(key)) return null;
        Object raw = props.get(key);
        return raw instanceof Number n ? n.floatValue() : null;
    }

    private static String sceneIdFromProps(Map<String, Object> props) {
        if (props == null) return "";
        Object raw = props.get("sceneId");
        if (raw == null) raw = props.get("scene");
        if (raw == null) return "";
        String id = String.valueOf(raw).trim();
        return id.isEmpty() || "null".equalsIgnoreCase(id) ? "" : id;
    }

    private static boolean parseBoolProp(Object raw) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return "true".equalsIgnoreCase(s.trim());
        return false;
    }

    public static void triggerFadeOut() {
        if (!active || isLogin) return;
        if (manualFadeOutTriggered) return;

        long elapsed = System.currentTimeMillis() - startTime;
        manualFadeOutStartAlpha = computeFadeAlpha(elapsed);
        if (manualFadeOutStartAlpha <= 0.001f) {
            active = false;
            return;
        }

        manualFadeOutTriggered = true;
        manualFadeOutStartTime = System.currentTimeMillis();
    }

    public static void cancel() {
        active = false;
        ownerSceneId = "";
        manualFadeOutTriggered = false;
    }

    /** Drop a scene-owned fade when that scene (or the last scene) is unloaded. */
    public static void onActiveScenesChanged(java.util.Collection<String> sceneIds) {
        if (!active || isLogin) return;
        if (sceneIds == null || sceneIds.isEmpty()) {
            cancel();
            return;
        }
        if (!ownerSceneId.isBlank() && !sceneIds.contains(ownerSceneId)) {
            cancel();
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Optional: really hide HUD instead of covering it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHudOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (active && hideOtherGui) event.setCanceled(true);
    }

    /**
     * Own pass after the HUD — not a NamedGuiOverlay.
     * {@code hideGui=minecraft/all} cancels vanilla/Spraute overlays, but fade stays.
     * Ctrl+Shift+U still {@link #cancel()}'s it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!active) return;
        //? if >=1.20.1 {
        renderActive(event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
                event.getGuiGraphics());
        //?} else {
        /*renderActive(event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
                event.getPoseStack());
        *///?}
    }

    /** ChatScreen / any Screen renders after the HUD pass — cover that too. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!active) return;
        var window = Minecraft.getInstance().getWindow();
        //? if >=1.20.1 {
        renderActive(window.getGuiScaledWidth(), window.getGuiScaledHeight(), event.getGuiGraphics());
        //?} else {
        /*renderActive(window.getGuiScaledWidth(), window.getGuiScaledHeight(), event.getPoseStack());
        *///?}
    }

    //? if >=1.20.1 {
    private static void renderActive(int width, int height, GuiGraphics guiGraphics) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (isLogin) {
            renderLoginScreen(guiGraphics, width, height, elapsed);
        } else {
            renderFadeInScreen(guiGraphics, width, height, elapsed);
        }
    }
    //?} else {
    /*private static void renderActive(int width, int height, PoseStack poseStack) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (isLogin) {
            renderLoginScreen(poseStack, width, height, elapsed);
        } else {
            renderFadeInScreen(poseStack, width, height, elapsed);
        }
    }
    *///?}

    //? if >=1.20.1 {
    private static void beginCover(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, COVER_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endCover(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().popPose();
    }
    //?} else {
    /*private static void beginCover(PoseStack poseStack) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, COVER_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endCover(PoseStack poseStack) {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
    *///?}

    //? if >=1.20.1 {
    private static void renderLoginScreen(GuiGraphics guiGraphics, int width, int height, long elapsed) {
        if (elapsed > 5000) {
            active = false;
            return;
        }

        float darkAlpha = 1.0f;
        if (elapsed > 4000) {
            darkAlpha = 1.0f - ((elapsed - 4000) / 1000f);
        }

        float logoAlpha = 0.0f;
        if (elapsed >= 500 && elapsed < 1500) {
            logoAlpha = (elapsed - 500) / 1000f;
        } else if (elapsed >= 1500 && elapsed < 3000) {
            logoAlpha = 1.0f;
        } else if (elapsed >= 3000 && elapsed < 4000) {
            logoAlpha = 1.0f - ((elapsed - 3000) / 1000f);
        }

        int darkColor = ((int) (darkAlpha * 255) << 24) | 0x111111;
        beginCover(guiGraphics);
        guiGraphics.fill(0, 0, width, height, darkColor);

        if (logoAlpha > 0) {
            ensureLogoTexSize();
            int texWidth = logoTexW;
            int texHeight = logoTexH;
            int logoWidth = 250;
            int logoHeight = Math.max(1, (int) (logoWidth * ((float) texHeight / (float) texWidth)));
            int x = (width - logoWidth) / 2;
            int y = (height - logoHeight) / 2;
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, logoAlpha);
            guiGraphics.blit(LOGO_TEX, x, y, logoWidth, logoHeight, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        endCover(guiGraphics);
    }
    //?} else {
    /*private static void renderLoginScreen(PoseStack poseStack, int width, int height, long elapsed) {
        if (elapsed > 5000) {
            active = false;
            return;
        }

        float darkAlpha = 1.0f;
        if (elapsed > 4000) {
            darkAlpha = 1.0f - ((elapsed - 4000) / 1000f);
        }

        float logoAlpha = 0.0f;
        if (elapsed >= 500 && elapsed < 1500) {
            logoAlpha = (elapsed - 500) / 1000f;
        } else if (elapsed >= 1500 && elapsed < 3000) {
            logoAlpha = 1.0f;
        } else if (elapsed >= 3000 && elapsed < 4000) {
            logoAlpha = 1.0f - ((elapsed - 3000) / 1000f);
        }

        int darkColor = ((int) (darkAlpha * 255) << 24) | 0x111111;
        beginCover(poseStack);
        GuiComponent.fill(poseStack, 0, 0, width, height, darkColor);

        if (logoAlpha > 0) {
            ensureLogoTexSize();
            int texWidth = logoTexW;
            int texHeight = logoTexH;
            int logoWidth = 250;
            int logoHeight = Math.max(1, (int) (logoWidth * ((float) texHeight / (float) texWidth)));
            int x = (width - logoWidth) / 2;
            int y = (height - logoHeight) / 2;
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, logoAlpha);
            RenderSystem.setShaderTexture(0, LOGO_TEX);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GuiComponent.blit(poseStack, x, y, logoWidth, logoHeight, 0, 0, texWidth, texHeight, texWidth, texHeight);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        endCover(poseStack);
    }
    *///?}

    private static void ensureLogoTexSize() {
        if (logoTexW > 0 && logoTexH > 0) return;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var opt = rm.getResource(LOGO_TEX);
            if (opt.isPresent()) {
                try (var stream = opt.get().open();
                     com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    logoTexW = img.getWidth();
                    logoTexH = img.getHeight();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        logoTexW = LOGO_TEX_W_FALLBACK;
        logoTexH = LOGO_TEX_H_FALLBACK;
    }
    
    //? if >=1.20.1 {
    private static void renderFadeInScreen(GuiGraphics guiGraphics, int width, int height, long elapsed) {
        if (manualFadeOutTriggered) {
            long fadeOutDurationMs = fadeOutDurationMs();
            long fadeOutElapsed = System.currentTimeMillis() - manualFadeOutStartTime;
            if (fadeOutElapsed >= fadeOutDurationMs) {
                active = false;
                return;
            }
            float alpha = manualFadeOutStartAlpha * (1.0f - (float) fadeOutElapsed / fadeOutDurationMs);
            renderFadeOverlay(guiGraphics, width, height, alpha);
            return;
        }

        float alpha = computeFadeAlpha(elapsed);
        if (alpha <= 0.001f && shouldDeactivateAfterAutoFade(elapsed)) {
            active = false;
            return;
        }

        renderFadeOverlay(guiGraphics, width, height, alpha);
    }
    //?} else {
    /*private static void renderFadeInScreen(PoseStack poseStack, int width, int height, long elapsed) {
        if (manualFadeOutTriggered) {
            long fadeOutDurationMs = fadeOutDurationMs();
            long fadeOutElapsed = System.currentTimeMillis() - manualFadeOutStartTime;
            if (fadeOutElapsed >= fadeOutDurationMs) {
                active = false;
                return;
            }
            float alpha = manualFadeOutStartAlpha * (1.0f - (float) fadeOutElapsed / fadeOutDurationMs);
            renderFadeOverlay(poseStack, width, height, alpha);
            return;
        }

        float alpha = computeFadeAlpha(elapsed);
        if (alpha <= 0.001f && shouldDeactivateAfterAutoFade(elapsed)) {
            active = false;
            return;
        }

        renderFadeOverlay(poseStack, width, height, alpha);
    }
    *///?}

    /** Darkening amount: 0 = clear screen, 1 = fully dark. */
    private static float computeFadeAlpha(long elapsed) {
        long timeInMs = Math.max(0L, (long) (timeIn * 1000));
        long visibleTimeMs = Math.max(0L, (long) (visibleTime * 1000));

        if (timeInMs <= 0) {
            if (autoFadeOut && elapsed >= visibleTimeMs) {
                long fadeOutElapsed = elapsed - visibleTimeMs;
                long fadeOutDurationMs = defaultFadeOutDurationMs();
                if (fadeOutElapsed >= fadeOutDurationMs) return 0f;
                return 1.0f - (fadeOutElapsed / (float) fadeOutDurationMs);
            }
            return 1.0f;
        }

        if (elapsed < timeInMs) {
            return (float) elapsed / timeInMs;
        }

        if (!autoFadeOut || elapsed < timeInMs + visibleTimeMs) {
            return 1.0f;
        }

        long fadeOutStart = timeInMs + visibleTimeMs;
        long fadeOutDurationMs = fadeOutDurationMs();
        if (elapsed < fadeOutStart + fadeOutDurationMs) {
            return 1.0f - ((elapsed - fadeOutStart) / (float) fadeOutDurationMs);
        }
        return 0f;
    }

    private static boolean shouldDeactivateAfterAutoFade(long elapsed) {
        if (!autoFadeOut) return false;
        long timeInMs = Math.max(0L, (long) (timeIn * 1000));
        long visibleTimeMs = Math.max(0L, (long) (visibleTime * 1000));
        if (timeInMs <= 0) {
            return elapsed >= visibleTimeMs + defaultFadeOutDurationMs();
        }
        return elapsed >= timeInMs + visibleTimeMs + fadeOutDurationMs();
    }

    private static long fadeOutDurationMs() {
        if (timeOut > 0f) return Math.max(1L, (long) (timeOut * 1000));
        return defaultFadeOutDurationMs();
    }

    /** Fade-out duration when {@code timeOut} is unset/instant (0). */
    private static long defaultFadeOutDurationMs() {
        long ms = Math.max(1L, (long) (timeIn * 1000));
        return ms > 1 ? ms : 500L;
    }

    //? if >=1.20.1 {
    private static void renderFadeOverlay(GuiGraphics guiGraphics, int width, int height, float alpha) {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (alpha <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        int bgColor = ((int) (alpha * 255) << 24) | (color & 0xFFFFFF);
        beginCover(guiGraphics);
        guiGraphics.fill(0, 0, width, height, bgColor);

        if (texture != null && !texture.isEmpty()) {
            net.minecraft.resources.ResourceLocation texLoc = new net.minecraft.resources.ResourceLocation(
                    texture.contains(":") ? texture : "minecraft:" + texture);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            int tw = 256;
            int th = 256;
            int tx = (width - tw) / 2;
            int ty = (height - th) / 2;
            guiGraphics.blit(texLoc, tx, ty, 0, 0, tw, th, tw, th);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int fontColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        Font font = mc.font;
        if (text != null && !text.isEmpty()) {
            guiGraphics.pose().pushPose();
            float scale = 3.0f;
            int textWidth = font.width(text);
            guiGraphics.pose().translate(width / 2.0f, height / 2.0f - (subtitle != null ? 20 : 0), 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, text, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor, true);
            guiGraphics.pose().popPose();
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            guiGraphics.pose().pushPose();
            float scale = 1.5f;
            int textWidth = font.width(subtitle);
            guiGraphics.pose().translate(width / 2.0f, height / 2.0f + (text != null ? 20 : 0), 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, subtitle, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor, true);
            guiGraphics.pose().popPose();
        }
        endCover(guiGraphics);
    }
    //?} else {
    /*private static void renderFadeOverlay(PoseStack poseStack, int width, int height, float alpha) {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (alpha <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        int bgColor = ((int) (alpha * 255) << 24) | (color & 0xFFFFFF);
        beginCover(poseStack);
        GuiComponent.fill(poseStack, 0, 0, width, height, bgColor);

        if (texture != null && !texture.isEmpty()) {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            net.minecraft.resources.ResourceLocation texLoc = new net.minecraft.resources.ResourceLocation(
                    texture.contains(":") ? texture : "minecraft:" + texture);
            RenderSystem.setShaderTexture(0, texLoc);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int tw = 256;
            int th = 256;
            int tx = (width - tw) / 2;
            int ty = (height - th) / 2;
            GuiComponent.blit(poseStack, tx, ty, 0, 0, tw, th, tw, th);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int fontColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        Font font = mc.font;
        if (text != null && !text.isEmpty()) {
            poseStack.pushPose();
            float scale = 3.0f;
            int textWidth = font.width(text);
            poseStack.translate(width / 2.0f, height / 2.0f - (subtitle != null ? 20 : 0), 0);
            poseStack.scale(scale, scale, 1.0f);
            font.drawShadow(poseStack, text, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor);
            poseStack.popPose();
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            poseStack.pushPose();
            float scale = 1.5f;
            int textWidth = font.width(subtitle);
            poseStack.translate(width / 2.0f, height / 2.0f + (text != null ? 20 : 0), 0);
            poseStack.scale(scale, scale, 1.0f);
            font.drawShadow(poseStack, subtitle, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor);
            poseStack.popPose();
        }
        endCover(poseStack);
    }
    *///?}
}
