package org.zonarstudio.spraute_engine.client.post;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

/**
 * Fullscreen composite shader (color grade / vignette / bloom / CA / grain).
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PostProcessShaders {
    private static ShaderInstance composite;

    private PostProcessShaders() {}

    public static ShaderInstance composite() {
        return composite;
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            ResourceLocation loc = new ResourceLocation(Spraute_engine.MODID, "post/composite");
            //? if >=1.20.1 {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), loc, DefaultVertexFormat.POSITION_TEX),
                    shader -> composite = shader);
            //?} else {
            /*event.registerShader(new ShaderInstance(event.getResourceManager(), loc, DefaultVertexFormat.POSITION_TEX),
                    shader -> composite = shader);
            *///?}
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("[Spraute Post] Failed to load composite shader: {}", e.toString());
            composite = null;
        }
    }
}
