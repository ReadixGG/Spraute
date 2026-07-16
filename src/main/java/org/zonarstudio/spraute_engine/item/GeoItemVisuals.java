package org.zonarstudio.spraute_engine.item;

import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.Spraute_engine;

/** Client-side lookup for 3D geo item display data (populated from {@link org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef}). */
public final class GeoItemVisuals {
    public final String geoPath;
    public final String texturePath;
    public final GeoItemTransform handFirst;
    public final GeoItemTransform handThird;
    public final GeoItemTransform handGround;
    public final GeoItemTransform gui;

    public GeoItemVisuals(String geoPath, String texturePath,
                          GeoItemTransform handFirst, GeoItemTransform handThird,
                          GeoItemTransform handGround, GeoItemTransform gui) {
        this.geoPath = geoPath;
        this.texturePath = texturePath;
        this.handFirst = GeoItemTransform.orDefault(handFirst, new GeoItemTransform(0, 0, 0, 0, 0, 0, 0.55f));
        this.handThird = GeoItemTransform.orDefault(handThird, GeoItemTransform.IDENTITY);
        this.handGround = GeoItemTransform.orDefault(handGround, GeoItemTransform.IDENTITY);
        this.gui = GeoItemTransform.orDefault(gui, new GeoItemTransform(0, 0, 0, 25, 45, 0, 0.45f));
    }

    public static GeoItemVisuals fromDef(org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.CustomItemDef def) {
        if (def == null || def.geo == null || def.geo.isBlank()) return null;
        String tex = def.texture != null && !def.texture.isBlank() ? def.texture : "";
        return new GeoItemVisuals(def.geo, tex, def.handFirst, def.handThird, def.handGround, def.gui);
    }

    public ResourceLocation textureLocation() {
        if (texturePath == null || texturePath.isBlank()) {
            return new ResourceLocation(Spraute_engine.MODID, "textures/item/missing.png");
        }
        if (texturePath.contains(":")) {
            String[] split = texturePath.split(":", 2);
            String path = split[1];
            if (path.startsWith("textures/")) path = path.substring("textures/".length());
            if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
            return new ResourceLocation(split[0], path);
        }
        String path = texturePath.replace('\\', '/');
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        return new ResourceLocation(Spraute_engine.MODID, path);
    }
}
