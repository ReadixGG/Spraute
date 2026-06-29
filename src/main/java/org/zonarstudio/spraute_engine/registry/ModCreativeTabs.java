package org.zonarstudio.spraute_engine.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.zonarstudio.spraute_engine.Spraute_engine;

//? if >=1.20.1 {
import net.minecraft.core.registries.Registries;
//?}

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    //? if >=1.20.1 {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Spraute_engine.MODID);

    public static final RegistryObject<CreativeModeTab> SPRAUTE_ENGINE = TABS.register("spraute_engine",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spraute_engine"))
                    .icon(() -> new ItemStack(ModItems.CAMERA.get()))
                    .displayItems((params, output) -> output.accept(ModItems.CAMERA.get()))
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
    //?} else {
    /*public static final CreativeModeTab SPRAUTE_ENGINE = new CreativeModeTab(Spraute_engine.MODID + ".spraute_engine") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.CAMERA.get());
        }
    };

    public static void register(IEventBus bus) {}
    *///?}
}
