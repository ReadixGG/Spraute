package org.zonarstudio.spraute_engine.client.assetsync;

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
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class AssetSyncOverlay {
    private AssetSyncOverlay() {}

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!AssetSyncClient.isOverlayVisible()) return;

        Minecraft mc = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        //? if >=1.20.1 {
        GuiGraphics g = event.getGuiGraphics();
        //?} else {
        /*PoseStack poseStack = event.getPoseStack();
        *///?}

        Font font = mc.font;
        float ratio = AssetSyncClient.overlayRatio();
        if (AssetSyncClient.isLiveHud()) {
            Component title = Component.translatable("spraute_engine.asset_sync.overlay.live");
            Component line2;
            if ("spraute_engine.asset_sync.overlay.reload".equals(AssetSyncClient.overlayPhaseKey())) {
                line2 = Component.translatable("spraute_engine.asset_sync.overlay.reload");
            } else {
                line2 = Component.translatable("spraute_engine.asset_sync.overlay.remain",
                        String.valueOf(AssetSyncClient.overlayFilesRemaining()),
                        AssetSyncRules.formatBytes(AssetSyncClient.overlayBytesRemaining()));
            }
            int panelW = 176;
            int panelH = 44;
            int x = width - 8 - panelW;
            int y = 8;
            int barW = panelW - 16;
            int barH = 6;
            int bx = x + 8;
            int by = y + 28;
            int filled = Math.max(0, Math.min(barW, Math.round(barW * ratio)));
            //? if >=1.20.1 {
            g.fill(x, y, x + panelW, y + panelH, 0xCC111111);
            g.drawString(font, title, x + 8, y + 6, 0xFFFFFF, false);
            g.drawString(font, line2, x + 8, y + 16, 0xCCCCCC, false);
            g.fill(bx, by, bx + barW, by + barH, 0xFF2A2A2A);
            g.fill(bx, by, bx + filled, by + barH, 0xFF4ADE80);
            //?} else {
            /*GuiComponent.fill(poseStack, x, y, x + panelW, y + panelH, 0xCC111111);
            font.drawShadow(poseStack, title.getString(), x + 8, y + 6, 0xFFFFFF);
            font.drawShadow(poseStack, line2.getString(), x + 8, y + 16, 0xCCCCCC);
            GuiComponent.fill(poseStack, bx, by, bx + barW, by + barH, 0xFF2A2A2A);
            GuiComponent.fill(poseStack, bx, by, bx + filled, by + barH, 0xFF4ADE80);
            *///?}
            return;
        }

        //? if >=1.20.1 {
        g.fill(0, 0, width, height, 0xEE111111);
        //?} else {
        /*GuiComponent.fill(poseStack, 0, 0, width, height, 0xEE111111);
        *///?}

        Component title = Component.translatable("spraute_engine.asset_sync.overlay.title");
        Component phase = Component.translatable(AssetSyncClient.overlayPhaseKey());
        String counts = AssetSyncClient.overlayCounts();
        String size = AssetSyncRules.formatBytes(AssetSyncClient.overlayBytes());

        int cx = width / 2;
        int cy = height / 2;
        //? if >=1.20.1 {
        g.drawCenteredString(font, title, cx, cy - 36, 0xFFFFFF);
        g.drawCenteredString(font, phase, cx, cy - 20, 0xCCCCCC);
        int barW = 220;
        int barH = 10;
        int bx = cx - barW / 2;
        int by = cy;
        g.fill(bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        int filled = Math.max(0, Math.min(barW, Math.round(barW * ratio)));
        g.fill(bx, by, bx + filled, by + barH, 0xFF4ADE80);
        g.drawCenteredString(font, Component.literal(counts + "  ·  " + size), cx, cy + 16, 0xAAAAAA);
        //?} else {
        /*GuiComponent.fill(poseStack, 0, 0, width, height, 0xEE111111);
        String titleStr = title.getString();
        String phaseStr = phase.getString();
        String sub = counts + "  ·  " + size;
        font.drawShadow(poseStack, titleStr, cx - font.width(titleStr) / 2f, cy - 36, 0xFFFFFF);
        font.drawShadow(poseStack, phaseStr, cx - font.width(phaseStr) / 2f, cy - 20, 0xCCCCCC);
        int barW = 220;
        int barH = 10;
        int bx = cx - barW / 2;
        int by = cy;
        GuiComponent.fill(poseStack, bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        int filled = Math.max(0, Math.min(barW, Math.round(barW * ratio)));
        GuiComponent.fill(poseStack, bx, by, bx + filled, by + barH, 0xFF4ADE80);
        font.drawShadow(poseStack, sub, cx - font.width(sub) / 2f, cy + 16, 0xAAAAAA);
        *///?}
    }
}
