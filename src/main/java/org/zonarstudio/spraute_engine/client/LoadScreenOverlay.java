package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class LoadScreenOverlay {

    public static boolean active = false;
    private static long startTime = 0;

    public static void trigger() {
        active = true;
        startTime = System.currentTimeMillis();
    }

    public static void cancel() {
        active = false;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!active) return;

        long elapsed = System.currentTimeMillis() - startTime;

        if (elapsed > 5000) {
            active = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        PoseStack poseStack = event.getPoseStack();

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

        int darkColor = ((int) (darkAlpha * 255) << 24) | 0x000000;
        GuiComponent.fill(poseStack, 0, 0, width, height, darkColor);

        if (logoAlpha > 0) {
            String text = "Spraute";
            Font font = mc.font;
            int textWidth = font.width(text);
            
            poseStack.pushPose();
            float scale = 4.0f;
            poseStack.translate(width / 2.0f, height / 2.0f, 0);
            poseStack.scale(scale, scale, 1.0f);
            
            int logoColor = ((int) (logoAlpha * 255) << 24) | 0xFFFFFF;
            font.drawShadow(poseStack, text, -textWidth / 2.0f, -font.lineHeight / 2.0f, logoColor);
            
            poseStack.popPose();
        }
    }
}
