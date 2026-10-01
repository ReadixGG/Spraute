package org.zonarstudio.spraute_engine.client.assetsync;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown after the dedicated server sent an updated create-item/block registry snapshot.
 * Forge cannot register new Item IDs mid-session — the player must relaunch the game.
 */
public class RegistryRestartPromptScreen extends Screen {
    public RegistryRestartPromptScreen() {
        super(Component.translatable("spraute_engine.registry_sync.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 20;
        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.registry_sync.quit"),
                b -> {
                    if (this.minecraft != null) this.minecraft.stop();
                }).bounds(cx - 120, y, 240, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.registry_sync.later"),
                b -> onClose()).bounds(cx - 120, y, 240, 20).build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.registry_sync.quit"),
                b -> {
                    if (this.minecraft != null) this.minecraft.stop();
                }));
        y += 24;
        this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.registry_sync.later"),
                b -> onClose()));
        *///?}
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 48, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body1"),
                this.width / 2, 72, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body2"),
                this.width / 2, 84, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body3"),
                this.width / 2, 96, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 48, 0xFFFFFF);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body1"),
                this.width / 2, 72, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body2"),
                this.width / 2, 84, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body3"),
                this.width / 2, 96, 0xAAAAAA);
        super.render(poseStack, mouseX, mouseY, partialTick);
        *///?}
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
