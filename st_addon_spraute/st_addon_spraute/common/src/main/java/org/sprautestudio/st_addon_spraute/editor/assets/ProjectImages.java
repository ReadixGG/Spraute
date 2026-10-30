package org.sprautestudio.st_addon_spraute.editor.assets;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.project.Workspace;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads project pictures into the texture manager under the paths Spraute looks up, so they
 * can be used as speech icons and dialog portraits. Files are reloaded when they change.
 */
public final class ProjectImages {
    private ProjectImages() {}

    public record Loaded(ResourceLocation texture, int width, int height, long modified) {}

    private static final Map<Path, Loaded> LOADED = new HashMap<>();
    /** Largest side of a loaded picture; bigger ones are downscaled. */
    private static final int MAX_SIZE = 512;

    /** Registers (or refreshes) every picture of every project in the workspace. */
    public static void registerAll() {
        for (Workspace.ProjectInfo info : Workspace.projects()) {
            try {
                register(Project.openReadOnly(info.dir()));
            } catch (Exception e) {
                St_addon_spraute.LOGGER.warn("Spraute: failed to load pictures of {}", info.dir(), e);
            }
        }
    }

    public static void register(Project project) {
        for (Project.ImageRef ref : project.images()) load(project, ref);
    }

    /** The loaded texture for a picture, loading it on first use; null when it cannot be read. */
    public static Loaded load(Project project, Project.ImageRef ref) {
        long modified;
        try {
            modified = Files.getLastModifiedTime(ref.path()).toMillis();
        } catch (IOException e) {
            return null;
        }
        Loaded old = LOADED.get(ref.path());
        if (old != null && old.modified == modified) return old;
        ResourceLocation rl = Images.texture(project.name(), ref.name());
        try {
            NativeImage img = read(ref.path());
            Loaded l = new Loaded(rl, img.getWidth(), img.getHeight(), modified);
            Runnable register = () -> Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            if (RenderSystem.isOnRenderThread()) register.run();
            else RenderSystem.recordRenderCall(register::run);
            LOADED.put(ref.path(), l);
            return l;
        } catch (Throwable e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot read picture {}", ref.path(), e);
            return null;
        }
    }

    /** Size of a loaded picture by its texture path, or {0, 0} when unknown. */
    public static int[] size(ResourceLocation texture) {
        for (Loaded l : LOADED.values()) if (l.texture().equals(texture)) return new int[]{l.width(), l.height()};
        return new int[]{0, 0};
    }

    public static void forget(Path path) {
        Loaded l = LOADED.remove(path);
        if (l != null) RenderSystem.recordRenderCall(() -> Minecraft.getInstance().getTextureManager().release(l.texture()));
    }

    private static NativeImage read(Path path) throws IOException {
        if (path.getFileName().toString().toLowerCase().endsWith(".png")) {
            NativeImage png;
            try (var in = Files.newInputStream(path)) {
                png = NativeImage.read(in);
            }
            if (Math.max(png.getWidth(), png.getHeight()) <= MAX_SIZE) return png;
            double k = MAX_SIZE / (double) Math.max(png.getWidth(), png.getHeight());
            NativeImage scaled = new NativeImage(Math.max(1, (int) (png.getWidth() * k)), Math.max(1, (int) (png.getHeight() * k)), false);
            png.resizeSubRectTo(0, 0, png.getWidth(), png.getHeight(), scaled);
            png.close();
            return scaled;
        }
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) throw new IOException("Неподдерживаемый формат картинки");
        int w = src.getWidth(), h = src.getHeight();
        double k = Math.min(1.0, MAX_SIZE / (double) Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, tw, th, false);
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int argb = src.getRGB(Math.min(w - 1, (int) (x / k)), Math.min(h - 1, (int) (y / k)));
                // NativeImage stores ABGR
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                img.setPixelRGBA(x, y, abgr);
            }
        }
        return img;
    }

    /** Names of the pictures of a project (for completion and block dropdowns). */
    public static List<String> names(Project project) {
        List<String> list = new ArrayList<>();
        if (project != null) for (Project.ImageRef r : project.images()) list.add(r.name());
        return list;
    }
}
