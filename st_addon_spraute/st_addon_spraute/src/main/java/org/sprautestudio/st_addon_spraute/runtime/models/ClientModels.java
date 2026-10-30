package org.sprautestudio.st_addon_spraute.runtime.models;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Client side of custom models: textures from the project folders. */
final class ClientModels {
    private ClientModels() {}

    private static final Map<Path, Long> LOADED = new HashMap<>();

    /** The folders of the projects the server this client is on gave it. */
    static Collection<Path> serverProjects() {
        return org.sprautestudio.st_addon_spraute.content.ContentClient.activeDirs();
    }

    static void registerTextures(Collection<ModelLibrary.Model> models) {
        for (ModelLibrary.Model m : models) {
            Path f = m.texture();
            if (f == null) continue;
            long stamp;
            try {
                stamp = Files.getLastModifiedTime(f).toMillis();
            } catch (Exception e) {
                continue;
            }
            Long old = LOADED.put(f, stamp);
            if (old != null && old == stamp) continue;
            try (InputStream in = Files.newInputStream(f)) {
                NativeImage img = NativeImage.read(in);
                Runnable reg = () -> Minecraft.getInstance().getTextureManager().register(m.textureId(), new DynamicTexture(img));
                if (RenderSystem.isOnRenderThread()) reg.run();
                else RenderSystem.recordRenderCall(reg::run);
            } catch (Exception e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read model texture {}", f, e);
            }
        }
    }
}
