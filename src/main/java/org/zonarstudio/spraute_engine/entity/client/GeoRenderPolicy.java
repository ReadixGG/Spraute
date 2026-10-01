package org.zonarstudio.spraute_engine.entity.client;

import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.config.SprauteConfig;

/** Screen-space LOD and animation budget shared by NPC and MODEL renderers. */
public final class GeoRenderPolicy {
    private GeoRenderPolicy() {}

    public static Decision decide(double distance, float radiusBlocks, boolean lodDisabled) {
        SprauteConfig cfg=SprauteConfig.get();
        if (lodDisabled || !cfg.isGeoLodEnabled()) return new Decision(0,1,Float.MAX_VALUE);
        float screenH=Math.max(240, Minecraft.getInstance().getWindow().getHeight());
        float pixels=(float)(Math.max(.05f,radiusBlocks)*screenH/Math.max(.25,distance));
        int lod;
        if (pixels >= 140f) lod=0;
        else if (pixels >= 48f) lod=1;
        else if (pixels >= 10f) lod=2;
        else if (pixels >= cfg.geoMinPixels) lod=3;
        else lod=4;
        int interval = lod <= 1 ? 1 : lod == 2 ? Math.max(1,cfg.geoMidAnimationInterval)
                : Math.max(1,cfg.geoFarAnimationInterval);
        return new Decision(lod,interval,pixels);
    }

    public record Decision(int lod, int animationInterval, float projectedPixels) {
        public boolean hidden() { return lod >= 4; }
    }
}
