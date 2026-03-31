package org.zonarstudio.spraute_engine.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.zonarstudio.spraute_engine.config.SprauteConfig;

import java.awt.Color;

public class SprauteSettingsScreen extends Screen {

    private EditBox formatBox;
    private EditBox hexBox;
    private ColorSlider redSlider;
    private ColorSlider greenSlider;
    private ColorSlider blueSlider;

    // Current color state
    private int r = 255;
    private int g = 255;
    private int b = 255;

    public SprauteSettingsScreen() {
        super(Component.literal("Spraute Engine Settings"));
    }

    @Override
    protected void init() {
        SprauteConfig config = SprauteConfig.get();
        
        // Load initial color
        try {
            Color c = Color.decode(config.nameColor);
            r = c.getRed();
            g = c.getGreen();
            b = c.getBlue();
        } catch (Exception e) {
            r = 255; g = 255; b = 255;
        }

        // Title
        int y = 40;
        int center = this.width / 2;

        // Name Format
        this.addRenderableWidget(new Button(center - 100, y, 200, 20, Component.literal("Format: " + config.nameFormat), button -> {
            // Placeholder for label, actually we want an EditBox below
        })).active = false; // Just a label workaround or use drawString

        y += 25;
        formatBox = new EditBox(this.font, center - 100, y, 200, 20, Component.literal("Name Format"));
        formatBox.setMaxLength(32);
        formatBox.setValue(config.nameFormat);
        this.addRenderableWidget(formatBox);

        // Color Sliders
        y += 30;
        redSlider = new ColorSlider(center - 100, y, 200, 20, Component.literal("Red"), r, 0xFF0000);
        this.addRenderableWidget(redSlider);
        
        y += 25;
        greenSlider = new ColorSlider(center - 100, y, 200, 20, Component.literal("Green"), g, 0x00FF00);
        this.addRenderableWidget(greenSlider);

        y += 25;
        blueSlider = new ColorSlider(center - 100, y, 200, 20, Component.literal("Blue"), b, 0x0000FF);
        this.addRenderableWidget(blueSlider);

        // Hex Box
        y += 30;
        hexBox = new EditBox(this.font, center - 60, y, 120, 20, Component.literal("Hex Color"));
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%02X%02X%02X", r, g, b));
        hexBox.setResponder(this::onHexChanged);
        this.addRenderableWidget(hexBox);

        // Save Button
        this.addRenderableWidget(new Button(center - 50, this.height - 40, 100, 20, Component.literal("Save & Close"), button -> {
            onClose();
        }));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        
        // Draw labels
        drawCenteredString(poseStack, this.font, "Name Format (use $Name)", this.width / 2, formatBox.y - 12, 0xAAAAAA);
        drawCenteredString(poseStack, this.font, "Name Color", this.width / 2, redSlider.y - 12, 0xAAAAAA);

        // Update colors from sliders if they were dragged
        updateColorFromSliders();

        // Preview Box
        int previewSize = 40;
        int previewX = this.width / 2 + 110;
        int previewY = redSlider.y;
        fill(poseStack, previewX, previewY, previewX + previewSize, previewY + previewSize + 50, (0xFF << 24) | (r << 16) | (g << 8) | b);
        drawCenteredString(poseStack, this.font, "Preview", previewX + 20, previewY - 12, 0xFFFFFF);

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void updateColorFromSliders() {
        if (redSlider == null) return;
        
        int nr = redSlider.getIntValue();
        int ng = greenSlider.getIntValue();
        int nb = blueSlider.getIntValue();

        if (nr != r || ng != g || nb != b) {
            r = nr;
            g = ng;
            b = nb;
            // Update hex box without triggering listener loop
            hexBox.setValue(String.format("#%02X%02X%02X", r, g, b));
        }
    }

    private void onHexChanged(String hex) {
        try {
            Color c = Color.decode(hex);
            r = c.getRed();
            g = c.getGreen();
            b = c.getBlue();
            
            // Update sliders
            redSlider.setValue(r / 255.0);
            greenSlider.setValue(g / 255.0);
            blueSlider.setValue(b / 255.0);
            
            redSlider.updateMessage();
            greenSlider.updateMessage();
            blueSlider.updateMessage();
        } catch (Exception e) {
            // Invalid hex, ignore
        }
    }

    @Override
    public void onClose() {
        SprauteConfig config = SprauteConfig.get();
        config.nameFormat = formatBox.getValue();
        config.nameColor = String.format("#%02X%02X%02X", r, g, b);
        SprauteConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // Inner class for Color Slider
    class ColorSlider extends AbstractSliderButton {
        private final Component label;
        private final int colorMask;

        public ColorSlider(int x, int y, int width, int height, Component label, int initialValue, int colorMask) {
            super(x, y, width, height, Component.empty(), initialValue / 255.0);
            this.label = label;
            this.colorMask = colorMask;
            this.updateMessage();
        }

        public int getIntValue() {
            return (int) (this.value * 255);
        }
        
        public void setValue(double value) {
            this.value = Mth.clamp(value, 0.0, 1.0);
            applyValue();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(label.copy().append(": " + getIntValue()));
        }

        @Override
        protected void applyValue() {
            // Value is already updated
        }
        
        public void updateMessagePublic() {
             this.updateMessage();
        }
    }
}
