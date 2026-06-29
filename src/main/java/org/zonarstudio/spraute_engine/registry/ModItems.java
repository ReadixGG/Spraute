package org.zonarstudio.spraute_engine.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.item.CameraItem;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Spraute_engine.MODID);

    public static final RegistryObject<CameraItem> CAMERA = ITEMS.register("camera",
            () -> new CameraItem(cameraProperties()));

    private static Item.Properties cameraProperties() {
        //? if >=1.20.1 {
        return new Item.Properties().stacksTo(1);
        //?} else {
        /*return new Item.Properties().stacksTo(1).tab(ModCreativeTabs.SPRAUTE_ENGINE);*/
        //?}
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
