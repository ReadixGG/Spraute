package org.zonarstudio.spraute_engine.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.util.List;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "33";

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
        CHANNEL.registerMessage(id++, SprauteUiMonitorOverlapPacket.class,
                SprauteUiMonitorOverlapPacket::encode,
                SprauteUiMonitorOverlapPacket::decode,
                SprauteUiMonitorOverlapPacket::handle);
        CHANNEL.registerMessage(id++, SprauteUiOverlapActionPacket.class,
                SprauteUiOverlapActionPacket::encode,
                SprauteUiOverlapActionPacket::decode,
                SprauteUiOverlapActionPacket::handle);
        CHANNEL.registerMessage(id++, CameraPacket.class,
                CameraPacket::encode,
                CameraPacket::decode,
                CameraPacket::handle);
        CHANNEL.registerMessage(id++, StepHeightSyncPacket.class,
                StepHeightSyncPacket::encode,
                StepHeightSyncPacket::decode,
                StepHeightSyncPacket::handle);
        CHANNEL.registerMessage(id++, CameraRoutePlayPacket.class,
                CameraRoutePlayPacket::encode,
                CameraRoutePlayPacket::decode,
                CameraRoutePlayPacket::handle);
        CHANNEL.registerMessage(id++, SprauteResetPresentationPacket.class,
                SprauteResetPresentationPacket::encode,
                SprauteResetPresentationPacket::decode,
                SprauteResetPresentationPacket::handle);
        CHANNEL.registerMessage(id++, SyncCustomBlockPropsPacket.class,
                SyncCustomBlockPropsPacket::encode,
                SyncCustomBlockPropsPacket::decode,
                SyncCustomBlockPropsPacket::handle);
        CHANNEL.registerMessage(id++, SceneActionPacket.class,
                SceneActionPacket::encode,
                SceneActionPacket::decode,
                SceneActionPacket::handle);
        CHANNEL.registerMessage(id++, ControlledParticleBurstPacket.class,
                ControlledParticleBurstPacket::encode,
                ControlledParticleBurstPacket::decode,
                ControlledParticleBurstPacket::handle);
        CHANNEL.registerMessage(id++, ConsoleEvalPacket.class,
                ConsoleEvalPacket::encode,
                ConsoleEvalPacket::decode,
                ConsoleEvalPacket::handle);
        CHANNEL.registerMessage(id++, PlayerControlPacket.class,
                PlayerControlPacket::encode,
                PlayerControlPacket::decode,
                PlayerControlPacket::handle);
        CHANNEL.registerMessage(id++, SyncAudioSourcePacket.class,
                SyncAudioSourcePacket::encode,
                SyncAudioSourcePacket::decode,
                SyncAudioSourcePacket::handle);
        CHANNEL.registerMessage(id++, PlayScriptSoundPacket.class,
                PlayScriptSoundPacket::encode,
                PlayScriptSoundPacket::decode,
                PlayScriptSoundPacket::handle);
        CHANNEL.registerMessage(id++, PlayerAudioSettingsPacket.class,
                PlayerAudioSettingsPacket::encode,
                PlayerAudioSettingsPacket::decode,
                PlayerAudioSettingsPacket::handle);
        CHANNEL.registerMessage(id++, OpenEditorPacket.class,
                OpenEditorPacket::encode,
                OpenEditorPacket::decode,
                OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, SyncActiveScenesPacket.class,
                SyncActiveScenesPacket::encode,
                SyncActiveScenesPacket::decode,
                SyncActiveScenesPacket::handle);
        CHANNEL.registerMessage(id++, SceneDeployPacket.class,
                SceneDeployPacket::encode,
                SceneDeployPacket::decode,
                SceneDeployPacket::handle);
        CHANNEL.registerMessage(id++, AssetManifestPacket.class,
                AssetManifestPacket::encode,
                AssetManifestPacket::decode,
                AssetManifestPacket::handle);
        CHANNEL.registerMessage(id++, AssetSyncRequestPacket.class,
                AssetSyncRequestPacket::encode,
                AssetSyncRequestPacket::decode,
                AssetSyncRequestPacket::handle);
        CHANNEL.registerMessage(id++, AssetChunkPacket.class,
                AssetChunkPacket::encode,
                AssetChunkPacket::decode,
                AssetChunkPacket::handle);
        CHANNEL.registerMessage(id++, AssetSyncFinishedPacket.class,
                AssetSyncFinishedPacket::encode,
                AssetSyncFinishedPacket::decode,
                AssetSyncFinishedPacket::handle);
        CHANNEL.registerMessage(id++, AssetSyncStatusPacket.class,
                AssetSyncStatusPacket::encode,
                AssetSyncStatusPacket::decode,
                AssetSyncStatusPacket::handle);
        CHANNEL.registerMessage(id++, WorkspaceBrowserOpPacket.class,
                WorkspaceBrowserOpPacket::encode,
                WorkspaceBrowserOpPacket::decode,
                WorkspaceBrowserOpPacket::handle);
        CHANNEL.registerMessage(id++, WorkspaceBrowserListPacket.class,
                WorkspaceBrowserListPacket::encode,
                WorkspaceBrowserListPacket::decode,
                WorkspaceBrowserListPacket::handle);
        CHANNEL.registerMessage(id++, WorkspaceBrowserReplyPacket.class,
                WorkspaceBrowserReplyPacket::encode,
                WorkspaceBrowserReplyPacket::decode,
                WorkspaceBrowserReplyPacket::handle);
        CHANNEL.registerMessage(id++, HudHidePacket.class,
                HudHidePacket::encode,
                HudHidePacket::decode,
                HudHidePacket::handle);
        CHANNEL.registerMessage(id++, ModUpdateHelloPacket.class,
                ModUpdateHelloPacket::encode,
                ModUpdateHelloPacket::decode,
                ModUpdateHelloPacket::handle);
        CHANNEL.registerMessage(id++, ModUpdateOfferPacket.class,
                ModUpdateOfferPacket::encode,
                ModUpdateOfferPacket::decode,
                ModUpdateOfferPacket::handle);
        CHANNEL.registerMessage(id++, ModUpdateRequestPacket.class,
                ModUpdateRequestPacket::encode,
                ModUpdateRequestPacket::decode,
                ModUpdateRequestPacket::handle);
        CHANNEL.registerMessage(id++, ModUpdateChunkPacket.class,
                ModUpdateChunkPacket::encode,
                ModUpdateChunkPacket::decode,
                ModUpdateChunkPacket::handle);
        CHANNEL.registerMessage(id++, SyncPostProcessPacket.class,
                SyncPostProcessPacket::encode,
                SyncPostProcessPacket::decode,
                SyncPostProcessPacket::handle);
        CHANNEL.registerMessage(id++, SprauteUiVideoStatePacket.class,
                SprauteUiVideoStatePacket::encode,
                SprauteUiVideoStatePacket::decode,
                SprauteUiVideoStatePacket::handle);
        CHANNEL.registerMessage(id++, PrewarmVideosPacket.class,
                PrewarmVideosPacket::encode,
                PrewarmVideosPacket::decode,
                PrewarmVideosPacket::handle);
        CHANNEL.registerMessage(id++, PlayerNodeEditPacket.class,
                PlayerNodeEditPacket::encode,
                PlayerNodeEditPacket::decode,
                PlayerNodeEditPacket::handle);
        CHANNEL.registerMessage(id++, PlayerCameraRestPacket.class,
                PlayerCameraRestPacket::encode,
                PlayerCameraRestPacket::decode,
                PlayerCameraRestPacket::handle);
        CHANNEL.registerMessage(id++, PlayerSkinSyncPacket.class,
                PlayerSkinSyncPacket::encode,
                PlayerSkinSyncPacket::decode,
                PlayerSkinSyncPacket::handle);
        CHANNEL.registerMessage(id++, PlayerSkinLayersPacket.class,
                PlayerSkinLayersPacket::encode,
                PlayerSkinLayersPacket::decode,
                PlayerSkinLayersPacket::handle);
        CHANNEL.registerMessage(id++, CameraEffectsPacket.class,
                CameraEffectsPacket::encode,
                CameraEffectsPacket::decode,
                CameraEffectsPacket::handle);
        CHANNEL.registerMessage(id++, PlayerExtrasSyncPacket.class,
                PlayerExtrasSyncPacket::encode,
                PlayerExtrasSyncPacket::decode,
                PlayerExtrasSyncPacket::handle);
        CHANNEL.registerMessage(id++, PlayerPostProcessPacket.class,
                PlayerPostProcessPacket::encode,
                PlayerPostProcessPacket::decode,
                PlayerPostProcessPacket::handle);
        CHANNEL.registerMessage(id++, PlayerVarsPacket.class,
                PlayerVarsPacket::encode,
                PlayerVarsPacket::decode,
                PlayerVarsPacket::handle);
        CHANNEL.messageBuilder(RegistrySnapshotLoginPacket.class, id++, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(RegistrySnapshotLoginPacket::getLoginIndex, RegistrySnapshotLoginPacket::setLoginIndex)
                .encoder(RegistrySnapshotLoginPacket::encode)
                .decoder(RegistrySnapshotLoginPacket::decode)
                .consumerNetworkThread(RegistrySnapshotLoginPacket::handle)
                .noResponse()
                .buildLoginPacketList(isLocal -> {
                    if (isLocal) return List.of();
                    return List.of(Pair.of("registry_snapshot", RegistrySnapshotLoginPacket.fromServerWorkspace()));
                })
                .add();
        CHANNEL.registerMessage(id++, SprauteParticleBurstPacket.class,
                SprauteParticleBurstPacket::encode,
                SprauteParticleBurstPacket::decode,
                SprauteParticleBurstPacket::handle);
    }
}
