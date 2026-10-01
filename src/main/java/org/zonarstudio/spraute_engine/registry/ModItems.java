package org.zonarstudio.spraute_engine.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.Spraute_engine;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Spraute_engine.MODID);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
