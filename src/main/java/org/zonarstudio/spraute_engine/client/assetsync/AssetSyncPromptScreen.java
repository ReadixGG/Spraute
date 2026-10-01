package org.zonarstudio.spraute_engine.client.assetsync;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.zonarstudio.spraute_engine.resource.AssetSyncRules;

public class AssetSyncPromptScreen extends Screen {
    private final int fileCount;
    private final long totalBytes;
    private final String serverKey;

    public AssetSyncPromptScreen(int fileCount, long totalBytes, String serverKey) {
        super(Component.translatable("spraute_engine.asset_sync.screen.title"));
        this.fileCount = fileCount;
        this.totalBytes = totalBytes;
        this.serverKey = serverKey == null ? "" : serverKey;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 20;
        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.asset_sync.screen.allow"),
                b -> {
                    AssetSyncClient.accept(false);
                    onClose();
                }).bounds(cx - 120, y, 240, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.asset_sync.screen.always"),
                b -> {
                    AssetSyncClient.accept(true);
                    onClose();
                }).bounds(cx - 120, y, 240, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.asset_sync.screen.deny"),
                b -> {
                    AssetSyncClient.deny();
                    onClose();
                }).bounds(cx - 120, y, 240, 20).build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.asset_sync.screen.allow"),
                b -> {
                    AssetSyncClient.accept(false);
                    onClose();
                }));
        y += 24;
        this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.asset_sync.screen.always"),
                b -> {
                    AssetSyncClient.accept(true);
                    onClose();
                }));
        y += 24;
        this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.asset_sync.screen.deny"),
                b -> {
                    AssetSyncClient.deny();
                    onClose();
                }));
        *///?}
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.screen == this) {
            super.onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        AssetSyncClient.deny();
        return true;
    }

    @Override
    public void render(
            //? if >=1.20.1 {
            net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick
            //?} else {
            /*com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick
            *///?}
    ) {
        //? if >=1.20.1 {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body1"),
                this.width / 2, 64, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body2"),
                this.width / 2, 76, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body3"),
                this.width / 2, 88, 0xAAAAAA);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.asset_sync.screen.files",
                        fileCount, AssetSyncRules.formatBytes(totalBytes)),
                this.width / 2, 112, 0xFFFFFF);
        if (!serverKey.isBlank()) {
            graphics.drawCenteredString(this.font,
                    Component.literal(serverKey),
                    this.width / 2, 128, 0x888888);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body1"),
                this.width / 2, 64, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body2"),
                this.width / 2, 76, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.asset_sync.screen.body3"),
                this.width / 2, 88, 0xAAAAAA);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.asset_sync.screen.files",
                        fileCount, AssetSyncRules.formatBytes(totalBytes)),
                this.width / 2, 112, 0xFFFFFF);
        if (!serverKey.isBlank()) {
            drawCenteredString(poseStack, this.font,
                    Component.literal(serverKey),
                    this.width / 2, 128, 0x888888);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
        *///?}
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
