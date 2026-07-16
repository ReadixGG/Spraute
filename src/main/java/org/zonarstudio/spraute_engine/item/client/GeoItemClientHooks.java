package org.zonarstudio.spraute_engine.item.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Wires {@link GeoItemRenderer} into script items via {@code Item#initializeClient}. */
public final class GeoItemClientHooks {
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (GeoItemRenderer.INSTANCE == null) {
                var mc = Minecraft.getInstance();
                GeoItemRenderer.INSTANCE = new GeoItemRenderer(
                        mc.getBlockEntityRenderDispatcher(),
                        mc.getEntityModels());
            }
            return GeoItemRenderer.INSTANCE;
        }
    };

    private GeoItemClientHooks() {}

    public static void initClientIfGeo(boolean geo, Consumer<IClientItemExtensions> consumer) {
        if (geo) {
            consumer.accept(EXTENSIONS);
        }
    }
}
