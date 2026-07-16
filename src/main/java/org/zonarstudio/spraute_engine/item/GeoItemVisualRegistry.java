package org.zonarstudio.spraute_engine.item;

import net.minecraft.resources.ResourceLocation;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps registered custom items to their 3D geo display data (built from script definitions). */
public final class GeoItemVisualRegistry {
    private static final Map<ResourceLocation, GeoItemVisuals> BY_ITEM = new ConcurrentHashMap<>();

    private GeoItemVisualRegistry() {}

    public static void rebuild() {
        BY_ITEM.clear();
        for (CustomBlockRegistry.CustomItemDef def : CustomBlockRegistry.ITEMS.values()) {
            GeoItemVisuals visuals = GeoItemVisuals.fromDef(def);
            if (visuals != null) {
                BY_ITEM.put(new ResourceLocation(Spraute_engine.MODID, def.id), visuals);
            }
        }
    }

    public static GeoItemVisuals get(ResourceLocation itemId) {
        return BY_ITEM.get(itemId);
    }

    public static boolean hasGeo(ResourceLocation itemId) {
        return BY_ITEM.containsKey(itemId);
    }
}
