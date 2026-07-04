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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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
    
    // FadeIn properties
    private static String text = null;
    private static String subtitle = null;
    private static String texture = null;
    private static int color = 0x000000;
    private static float timeIn = 1.0f;
    private static float visibleTime = 2.0f;
    private static boolean autoFadeOut = false;
    private static boolean manualFadeOutTriggered = false;
    private static long manualFadeOutStartTime = 0;
    private static float manualFadeOutStartAlpha = 1.0f;

    public static void triggerLogin() {
        active = true;
        isLogin = true;
        startTime = System.currentTimeMillis();
    }

    public static void triggerFadeIn(Map<String, Object> props) {
        if (props != null && Boolean.TRUE.equals(props.get("triggerFadeOut"))) {
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
        if (props.containsKey("color") && props.get("color") instanceof Number n) {
            color = n.intValue() & 0xFFFFFF; // Keep RGB
        }
        
        timeIn = props.containsKey("time") && props.get("time") instanceof Number n ? n.floatValue() : 1.0f;
        visibleTime = props.containsKey("visibleTime") && props.get("visibleTime") instanceof Number n ? n.floatValue()
                : props.containsKey("visible_time") && props.get("visible_time") instanceof Number n2 ? n2.floatValue() : 2.0f;
        
        autoFadeOut = false;
        if (props.containsKey("fadeout") && props.get("fadeout") instanceof Boolean b) {
            autoFadeOut = b;
        } else if (props.containsKey("fadeout") && props.get("fadeout") instanceof String s) {
            autoFadeOut = Boolean.parseBoolean(s);
        }
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
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!active) return;

        long elapsed = System.currentTimeMillis() - startTime;
        
        if (isLogin) {
            renderLoginScreen(event, elapsed);
        } else {
            renderFadeInScreen(event, elapsed);
        }
    }

    private static void renderLoginScreen(RenderGuiOverlayEvent.Post event, long elapsed) {
        if (elapsed > 5000) {
            active = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        //? if >=1.20.1 {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        //?} else {
        /*PoseStack poseStack = event.getPoseStack();
        *///?}

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
        //? if >=1.20.1 {
        guiGraphics.fill(0, 0, width, height, darkColor);
        //?} else {
        /*GuiComponent.fill(poseStack, 0, 0, width, height, darkColor);
        *///?}

        if (logoAlpha > 0) {
            int texWidth = 1356;
            int texHeight = 470;
            
            // Задаем ширину логотипа на экране (например, 250 пикселей)
            int logoWidth = 250;
            int logoHeight = (int) (logoWidth * ((float) texHeight / texWidth));
            
            int x = (width - logoWidth) / 2;
            int y = (height - logoHeight) / 2;

            //? if >=1.20.1 {
            net.minecraft.resources.ResourceLocation texLoc = new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, "textures/gui/logo.png");
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, logoAlpha);
            guiGraphics.blit(texLoc, x, y, logoWidth, logoHeight, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            //?} else {
            /*com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, logoAlpha);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, new net.minecraft.resources.ResourceLocation(Spraute_engine.MODID, "textures/gui/logo.png"));
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

            poseStack.pushPose();
            GuiComponent.blit(poseStack, x, y, logoWidth, logoHeight, 0, 0, texWidth, texHeight, texWidth, texHeight);
            poseStack.popPose();
            
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            *///?}
        }
    }
    
    private static void renderFadeInScreen(RenderGuiOverlayEvent.Post event, long elapsed) {
        if (manualFadeOutTriggered) {
            long fadeOutDurationMs = Math.max(1L, (long) (timeIn * 1000));
            long fadeOutElapsed = System.currentTimeMillis() - manualFadeOutStartTime;
            if (fadeOutElapsed >= fadeOutDurationMs) {
                active = false;
                return;
            }
            float alpha = manualFadeOutStartAlpha * (1.0f - (float) fadeOutElapsed / fadeOutDurationMs);
            renderFadeOverlay(event, alpha);
            return;
        }

        float alpha = computeFadeAlpha(elapsed);
        if (alpha <= 0.001f && shouldDeactivateAfterAutoFade(elapsed)) {
            active = false;
            return;
        }

        renderFadeOverlay(event, alpha);
    }

    /** Darkening amount: 0 = clear screen, 1 = fully dark. */
    private static float computeFadeAlpha(long elapsed) {
        long timeInMs = Math.max(0L, (long) (timeIn * 1000));
        long visibleTimeMs = Math.max(0L, (long) (visibleTime * 1000));

        if (timeInMs <= 0) {
            if (autoFadeOut && elapsed >= visibleTimeMs) {
                long fadeOutElapsed = elapsed - visibleTimeMs;
                if (fadeOutElapsed >= 500) return 0f;
                return 1.0f - (fadeOutElapsed / 500f);
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
        long fadeOutDurationMs = timeInMs;
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
            return elapsed >= visibleTimeMs + 500;
        }
        return elapsed >= timeInMs + visibleTimeMs + timeInMs;
    }

    private static void renderFadeOverlay(RenderGuiOverlayEvent.Post event, float alpha) {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (alpha <= 0.001f) return;
        
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        //? if >=1.20.1 {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        //?} else {
        /*PoseStack poseStack = event.getPoseStack();
        *///?}
        Minecraft mc = Minecraft.getInstance();
        
        int bgColor = ((int) (alpha * 255) << 24) | (color & 0xFFFFFF);
        //? if >=1.20.1 {
        guiGraphics.fill(0, 0, width, height, bgColor);
        //?} else {
        /*GuiComponent.fill(poseStack, 0, 0, width, height, bgColor);
        *///?}
        
        if (alpha > 0) {
            if (texture != null && !texture.isEmpty()) {
                //? if >=1.20.1 {
                net.minecraft.resources.ResourceLocation texLoc = new net.minecraft.resources.ResourceLocation(texture.contains(":") ? texture : "minecraft:" + texture);
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                int tw = 256;
                int th = 256;
                int tx = (width - tw) / 2;
                int ty = (height - th) / 2;
                guiGraphics.blit(texLoc, tx, ty, 0, 0, tw, th, tw, th);
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                //?} else {
                /*com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                net.minecraft.resources.ResourceLocation texLoc = new net.minecraft.resources.ResourceLocation(texture.contains(":") ? texture : "minecraft:" + texture);
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texLoc);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

                // Изображение центрируется, если размер не указан. Допустим размер фиксированный или растянут?
                // Сделаем размер небольшим (256x256) в центре.
                int tw = 256;
                int th = 256;
                int tx = (width - tw) / 2;
                int ty = (height - th) / 2;

                poseStack.pushPose();
                GuiComponent.blit(poseStack, tx, ty, 0, 0, tw, th, tw, th);
                poseStack.popPose();
                
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                *///?}
            }

            int fontColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;
            Font font = mc.font;
            
            if (text != null && !text.isEmpty()) {
                //? if >=1.20.1 {
                guiGraphics.pose().pushPose();
                float scale = 3.0f;
                int textWidth = font.width(text);
                guiGraphics.pose().translate(width / 2.0f, height / 2.0f - (subtitle != null ? 20 : 0), 0);
                guiGraphics.pose().scale(scale, scale, 1.0f);
                guiGraphics.drawString(font, text, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor, true);
                guiGraphics.pose().popPose();
                //?} else {
                /*poseStack.pushPose();
                float scale = 3.0f;
                int textWidth = font.width(text);
                poseStack.translate(width / 2.0f, height / 2.0f - (subtitle != null ? 20 : 0), 0);
                poseStack.scale(scale, scale, 1.0f);
                font.drawShadow(poseStack, text, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor);
                poseStack.popPose();
                *///?}
            }
            
            if (subtitle != null && !subtitle.isEmpty()) {
                //? if >=1.20.1 {
                guiGraphics.pose().pushPose();
                float scale = 1.5f;
                int textWidth = font.width(subtitle);
                guiGraphics.pose().translate(width / 2.0f, height / 2.0f + (text != null ? 20 : 0), 0);
                guiGraphics.pose().scale(scale, scale, 1.0f);
                guiGraphics.drawString(font, subtitle, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor, true);
                guiGraphics.pose().popPose();
                //?} else {
                /*poseStack.pushPose();
                float scale = 1.5f;
                int textWidth = font.width(subtitle);
                poseStack.translate(width / 2.0f, height / 2.0f + (text != null ? 20 : 0), 0);
                poseStack.scale(scale, scale, 1.0f);
                font.drawShadow(poseStack, subtitle, -textWidth / 2.0f, -font.lineHeight / 2.0f, fontColor);
                poseStack.popPose();
                *///?}
            }
        }
    }
}
