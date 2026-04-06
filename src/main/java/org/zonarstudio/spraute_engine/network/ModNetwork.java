package org.zonarstudio.spraute_engine.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.zonarstudio.spraute_engine.Spraute_engine;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Spraute_engine.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, KeybindPressedPacket.class,
                KeybindPressedPacket::encode,
                KeybindPressedPacket::decode,
                KeybindPressedPacket::handle);
        CHANNEL.registerMessage(id++, OpenSprauteUiPacket.class,
                OpenSprauteUiPacket::encode,
                OpenSprauteUiPacket::decode,
                OpenSprauteUiPacket::handle);
        CHANNEL.registerMessage(id++, CloseSprauteUiPacket.class,
                CloseSprauteUiPacket::encode,
                CloseSprauteUiPacket::decode,
                CloseSprauteUiPacket::handle);
        CHANNEL.registerMessage(id++, SprauteUiActionPacket.class,
                SprauteUiActionPacket::encode,
                SprauteUiActionPacket::decode,
                SprauteUiActionPacket::handle);
        CHANNEL.registerMessage(id++, UpdateSprauteUiWidgetPacket.class,
                UpdateSprauteUiWidgetPacket::encode,
                UpdateSprauteUiWidgetPacket::decode,
                UpdateSprauteUiWidgetPacket::handle);
        CHANNEL.registerMessage(id++, SyncDebugStatePacket.class,
                SyncDebugStatePacket::encode,
                SyncDebugStatePacket::decode,
                SyncDebugStatePacket::handle);
        CHANNEL.registerMessage(id++, DebugActionPacket.class,
                DebugActionPacket::encode,
                DebugActionPacket::decode,
                DebugActionPacket::handle);
        CHANNEL.registerMessage(id++, BoneParticlePacket.class,
                BoneParticlePacket::encode,
                BoneParticlePacket::decode,
                BoneParticlePacket::handle);
        CHANNEL.registerMessage(id++, OpenSprauteOverlayPacket.class,
                OpenSprauteOverlayPacket::encode,
                OpenSprauteOverlayPacket::decode,
                OpenSprauteOverlayPacket::handle);
        CHANNEL.registerMessage(id++, CloseSprauteOverlayPacket.class,
                CloseSprauteOverlayPacket::encode,
                CloseSprauteOverlayPacket::decode,
                CloseSprauteOverlayPacket::handle);
        CHANNEL.registerMessage(id++, SyncLoadScreenPacket.class,
                SyncLoadScreenPacket::encode,
                SyncLoadScreenPacket::decode,
                SyncLoadScreenPacket::handle);
    }
}
