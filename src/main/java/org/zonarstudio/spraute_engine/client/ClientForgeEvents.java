package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.client.gui.SprauteSettingsScreen;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("spraute")
                .then(Commands.literal("settings")
                    .executes(context -> {
                        // Open screen on the next tick to avoid issues during command execution
                        Minecraft.getInstance().tell(() -> {
                            Minecraft.getInstance().setScreen(new SprauteSettingsScreen());
                        });
                        return 1;
                    })
                )
        );
    }
}
