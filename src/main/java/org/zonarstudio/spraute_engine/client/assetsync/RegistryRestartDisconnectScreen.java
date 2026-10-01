package org.zonarstudio.spraute_engine.client.assetsync;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Replaces Forge's "mod / game version mismatch" screen when the only problem
 * is missing {@code create item/particle} IDs that were just downloaded.
 */
public class RegistryRestartDisconnectScreen extends Screen {
    private final Screen parent;

    public RegistryRestartDisconnectScreen(Screen parent) {
        super(Component.translatable("spraute_engine.registry_sync.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 28;
        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.registry_sync.quit"),
                b -> {
                    if (this.minecraft != null) this.minecraft.stop();
                }).bounds(cx - 120, y, 240, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.toMenu"),
                b -> {
                    if (this.minecraft != null) this.minecraft.setScreen(this.parent);
                }).bounds(cx - 120, y, 240, 20).build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("spraute_engine.registry_sync.quit"),
                b -> {
                    if (this.minecraft != null) this.minecraft.stop();
                }));
        y += 24;
        this.addRenderableWidget(new Button(cx - 120, y, 240, 20,
                Component.translatable("gui.toMenu"),
                b -> {
                    if (this.minecraft != null) this.minecraft.setScreen(this.parent);
                }));
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.disconnect1"),
                this.width / 2, 68, 0xFFCC66);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body1"),
                this.width / 2, 88, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body2"),
                this.width / 2, 100, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.registry_sync.body3"),
                this.width / 2, 112, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.disconnect1"),
                this.width / 2, 68, 0xFFCC66);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body1"),
                this.width / 2, 88, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body2"),
                this.width / 2, 100, 0xCCCCCC);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.registry_sync.body3"),
                this.width / 2, 112, 0xAAAAAA);
        super.render(poseStack, mouseX, mouseY, partialTick);
        *///?}
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
