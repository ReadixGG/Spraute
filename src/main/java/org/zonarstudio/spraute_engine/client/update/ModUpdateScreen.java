package org.zonarstudio.spraute_engine.client.update;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

/** Full-screen download progress and restart prompt after a server mod update. */
public final class ModUpdateScreen extends Screen {
    public ModUpdateScreen() {
        super(Component.translatable("spraute_engine.mod_update.screen.title"));
    }

    @Override
    protected void init() {
        if (!ModUpdateClient.isPendingRestart()) return;
        int cx = this.width / 2;
        int y = this.height / 2 + 28;
        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.mod_update.screen.quit"),
                b -> ModUpdateClient.quitGame())
                .bounds(cx - 140, y, 280, 28)
                .build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 140, y, 280, 28,
                Component.translatable("spraute_engine.mod_update.screen.quit"),
                b -> ModUpdateClient.quitGame()));
        *///?}
    }

    @Override
    public void tick() {
        super.tick();
        if (ModUpdateClient.isPendingRestart() && this.children().isEmpty()) {
            this.init();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !ModUpdateClient.isDownloading() && !ModUpdateClient.isRestartRequired();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (!shouldCloseOnEsc()) return;
        super.onClose();
    }

    @Override
    public void render(
            //? if >=1.20.1 {
            net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick
            //?} else {
            /*com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick
            *///?}
    ) {
        boolean done = ModUpdateClient.isPendingRestart();
        float ratio = ModUpdateClient.overlayRatio();
        String version = ModUpdateClient.overlayVersion();
        Component heading = done
                ? this.title
                : Component.translatable("spraute_engine.mod_update.overlay.download");
        int cx = this.width / 2;
        int cy = this.height / 2;
        //? if >=1.20.1 {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xEE111111);
        graphics.drawCenteredString(this.font, heading, cx, cy - 64, 0xFFFFFF);
        if (done) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("spraute_engine.mod_update.screen.body", version),
                    cx, cy - 40, 0xCCCCCC);
            graphics.drawCenteredString(this.font,
                    Component.translatable("spraute_engine.mod_update.screen.body2"),
                    cx, cy - 26, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.literal(AssetSyncRules.formatBytes(ModUpdateClient.overlayReceived())
                            + " / " + AssetSyncRules.formatBytes(ModUpdateClient.overlayBytes())),
                    cx, cy - 40, 0xAAAAAA);
        }
        int barW = 280;
        int barH = 10;
        int bx = cx - barW / 2;
        int by = cy - 6;
        graphics.fill(bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        int filled = Math.max(0, Math.min(barW, Math.round(barW * ratio)));
        graphics.fill(bx, by, bx + filled, by + barH, done ? 0xFFFBBF24 : 0xFF60A5FA);
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*this.renderBackground(poseStack);
        net.minecraft.client.gui.GuiComponent.fill(poseStack, 0, 0, this.width, this.height, 0xEE111111);
        drawCenteredString(poseStack, this.font, heading, cx, cy - 64, 0xFFFFFF);
        if (done) {
            drawCenteredString(poseStack, this.font,
                    Component.translatable("spraute_engine.mod_update.screen.body", version),
                    cx, cy - 40, 0xCCCCCC);
            drawCenteredString(poseStack, this.font,
                    Component.translatable("spraute_engine.mod_update.screen.body2"),
                    cx, cy - 26, 0xAAAAAA);
        } else {
            String sub = AssetSyncRules.formatBytes(ModUpdateClient.overlayReceived())
                    + " / " + AssetSyncRules.formatBytes(ModUpdateClient.overlayBytes());
            drawCenteredString(poseStack, this.font, Component.literal(sub), cx, cy - 40, 0xAAAAAA);
        }
        int barW = 280;
        int barH = 10;
        int bx = cx - barW / 2;
        int by = cy - 6;
        net.minecraft.client.gui.GuiComponent.fill(poseStack, bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        int filled = Math.max(0, Math.min(barW, Math.round(barW * ratio)));
        net.minecraft.client.gui.GuiComponent.fill(poseStack, bx, by, bx + filled, by + barH,
                done ? 0xFFFBBF24 : 0xFF60A5FA);
        super.render(poseStack, mouseX, mouseY, partialTick);
        *///?}
    }
}
