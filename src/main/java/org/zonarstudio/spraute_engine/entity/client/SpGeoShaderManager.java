package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpGeoShaderManager {
    private static ShaderInstance skinned;

    private SpGeoShaderManager() {}

    public static ShaderInstance skinned() {
        return skinned;
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            ResourceLocation location = new ResourceLocation(Spraute_engine.MODID, "geo/skinned");
            //? if >=1.20.1 {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), location,
                    DefaultVertexFormat.NEW_ENTITY), shader -> skinned = shader);
            //?} else {
            /*event.registerShader(new ShaderInstance(event.getResourceManager(), location,
                    DefaultVertexFormat.NEW_ENTITY), shader -> skinned = shader);
            *///?}
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("[Spraute Geo GPU] Failed to load skinning shader: {}", e.toString());
            skinned = null;
        }
    }
}
