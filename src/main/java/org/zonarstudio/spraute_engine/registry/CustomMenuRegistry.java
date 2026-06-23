package org.zonarstudio.spraute_engine.registry;

import net.minecraft.client.gui.screens.MenuScreens;
//? if >=1.20.1 {
import net.minecraft.core.registries.Registries;
//?} else {
/*import net.minecraft.core.Registry;
*///?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegisterEvent;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.ui.SprauteContainerMenu;
import org.zonarstudio.spraute_engine.client.SprauteContainerScreen;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CustomMenuRegistry {
    public static MenuType<SprauteContainerMenu> SPRAUTE_CONTAINER;

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(
                //? if >=1.20.1 {
                Registries.MENU
                //?} else {
                /*Registry.MENU_REGISTRY*/
                //?}
        )) {
            SPRAUTE_CONTAINER = IForgeMenuType.create((windowId, inv, data) -> new SprauteContainerMenu(windowId, inv, data.readUtf()));
            event.register(
                    //? if >=1.20.1 {
                    Registries.MENU
                    //?} else {
                    /*Registry.MENU_REGISTRY*/
                    //?}
            , new ResourceLocation(Spraute_engine.MODID, "container"), () -> SPRAUTE_CONTAINER);
        }
    }

    @Mod.EventBusSubscriber(modid = Spraute_engine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientHooks {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(SPRAUTE_CONTAINER, SprauteContainerScreen::new);
            });
        }
    }
}