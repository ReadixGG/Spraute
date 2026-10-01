package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    /** Force fog RGB to the create-world palette (biome fog otherwise overrides). */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ResourceLocation dim = level.dimension().location();
        if (!Spraute_engine.MODID.equals(dim.getNamespace())) return;
        CustomWorldRegistry.CustomWorldDef def = CustomWorldRegistry.resolveWorldDef(dim.getPath());
        if (def == null) return;
        float r = ((def.fogColor >> 16) & 0xFF) / 255f;
        float g = ((def.fogColor >> 8) & 0xFF) / 255f;
        float b = (def.fogColor & 0xFF) / 255f;
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof SprauteBillboardEntity) {
            BillboardClientState.clearSuppressRendering();
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        java.util.UUID id=event.getEntity().getUUID();
        if (event.getEntity() instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity) {
            org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer.removeInstance(id);
        } else if (event.getEntity() instanceof org.zonarstudio.spraute_engine.entity.SprauteModelEntity) {
            org.zonarstudio.spraute_engine.entity.client.SprauteModelRenderer.removeInstance(id);
        }
    }

    @SubscribeEvent
    public static void onClientLoggedIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        // OP on dedicated: pull server file list (upload is manual from the Project panel).
        Minecraft.getInstance().tell(() -> {
            if (org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()) {
                org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.requestList();
            }
            org.zonarstudio.spraute_engine.client.ui.VideoPrewarm.scanLocalAutoStartScenes();
            org.zonarstudio.spraute_engine.client.assetsync.RegistryHandshakeClient.promptRestartIfNeeded();
        });
    }

    @SubscribeEvent
    public static void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (!org.zonarstudio.spraute_engine.client.assetsync.RegistryHandshakeClient
                .shouldReplaceDisconnectScreen(event.getNewScreen())) {
            return;
        }
        org.zonarstudio.spraute_engine.client.assetsync.RegistryHandshakeClient.consumeReplaceDisconnect();
        event.setNewScreen(new org.zonarstudio.spraute_engine.client.assetsync.RegistryRestartDisconnectScreen(
                event.getScreen()));
    }

    @SubscribeEvent
    public static void onClientLoggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.isInitialized()) {
            org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.saveIniNow();
        }
        ClientSceneRuntime.clear();
        org.zonarstudio.spraute_engine.client.post.PostProcessClient.clear();
        org.zonarstudio.spraute_engine.client.assetsync.AssetSyncClient.reset();
        org.zonarstudio.spraute_engine.client.assetsync.ClientAssetHotReload.reset();
        org.zonarstudio.spraute_engine.client.update.ModUpdateClient.reset();
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.reset();
        org.zonarstudio.spraute_engine.client.ui.VideoPrewarm.reset();
        org.zonarstudio.spraute_engine.client.PlayerSkinComposer.clear();
        org.zonarstudio.spraute_engine.client.PlayerSkinLayersClient.clear();
        org.zonarstudio.spraute_engine.client.ClientPlayerVars.clear();
        org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer.clearInstances();
        org.zonarstudio.spraute_engine.entity.client.SprauteModelRenderer.clearInstances();
        org.zonarstudio.spraute_engine.entity.client.GeoRenderStats.reset();
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        org.zonarstudio.spraute_engine.client.assetsync.AssetSyncClient.tick();
        org.zonarstudio.spraute_engine.client.assetsync.ClientAssetHotReload.tick();
        org.zonarstudio.spraute_engine.client.update.ModUpdateClient.tick();
        org.zonarstudio.spraute_engine.client.PlayerSkinComposer.tick();
    }
}
