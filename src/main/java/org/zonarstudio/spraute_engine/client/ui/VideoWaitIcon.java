package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.client.editor.icons.LucideSvgRasterizer;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Rasterized {@code loader-circle.svg} for in-game video decode wait UI. */
public final class VideoWaitIcon {
    private static final int SIZE = 64;
    private static ResourceLocation location;
    private static DynamicTexture texture;

    private VideoWaitIcon() {}

    public static ResourceLocation location() {
        ensure();
        return location;
    }

    public static int size() {
        return SIZE;
    }

    private static void ensure() {
        if (location != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        try {
            String svg = readSvg();
            if (svg == null) return;
            BufferedImage img = LucideSvgRasterizer.rasterize(svg, SIZE);
            if (img == null) return;
            NativeImage nativeImage = toNative(img);
            texture = new DynamicTexture(nativeImage);
            texture.setFilter(true, false);
            location = mc.getTextureManager().register("spraute_video_loader", texture);
        } catch (Exception ignored) {
        }
    }

    private static String readSvg() {
        ResourceLocation loc = new ResourceLocation(Spraute_engine.MODID,
                "textures/editor/icons/loader-circle.svg");
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = VideoWaitIcon.class.getResourceAsStream(
                "/assets/spraute_engine/textures/editor/icons/loader-circle.svg")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static NativeImage toNative(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }
}
