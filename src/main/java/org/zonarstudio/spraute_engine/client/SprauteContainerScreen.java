package org.zonarstudio.spraute_engine.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
//? if >=1.20.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.zonarstudio.spraute_engine.ui.SprauteContainerMenu;

public class SprauteContainerScreen extends AbstractContainerScreen<SprauteContainerMenu> {
    private final SprauteScriptScreen bgScreen;

    public SprauteContainerScreen(SprauteContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        JsonObject root = JsonParser.parseString(menu.json).getAsJsonObject();
        this.bgScreen = new SprauteScriptScreen(root);
        
        // Disable generic background tint since we have items
        //? if <1.20.1 {
        /*this.passEvents = true;
        *///?}
        
        if (root.has("size")) {
            if (root.get("size").isJsonArray()) {
                this.imageWidth = root.getAsJsonArray("size").get(0).getAsInt();
                this.imageHeight = root.getAsJsonArray("size").get(1).getAsInt();
            } else if (root.get("size").isJsonPrimitive()) {
                this.imageWidth = root.get("size").getAsInt();
                this.imageHeight = root.get("size").getAsInt();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance() != null) {
            bgScreen.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    //? if >=1.20.1 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // Draw the scripted UI underneath the slots
        bgScreen.render(g, mouseX, mouseY, partialTick);

        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);

        // Draw the scripted UI underneath the slots
        bgScreen.render(poseStack, mouseX, mouseY, partialTick);

        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }
    *///?}

    //? if >=1.20.1 {
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // bgScreen already renders everything, no need to do anything here
    }
    //?} else {
    /*@Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        // bgScreen already renders everything, no need to do anything here
    }
    *///?}

    //? if >=1.20.1 {
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Disable default labels like "Inventory" unless we want them
    }
    //?} else {
    /*@Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        // Disable default labels like "Inventory" unless we want them
    }
    *///?}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bgScreen.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        bgScreen.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (bgScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (bgScreen.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
