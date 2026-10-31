package org.sprautestudio.spraute_engine.platform.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.Priority;

import static org.sprautestudio.spraute_engine.platform.forge.ForgeEvents.bridge;
import static org.sprautestudio.spraute_engine.platform.forge.ForgeEvents.fire;
import static org.sprautestudio.spraute_engine.platform.forge.ForgeEvents.phase;
import static org.sprautestudio.spraute_engine.platform.forge.ForgeEvents.priority;

/** Forge's client events as the engine's {@link ClientHooks}. */
final class ForgeClientEvents {
    private ForgeClientEvents() {
    }

    static void install(IEventBus modBus) {
        bridge(net.minecraftforge.event.TickEvent.ClientTickEvent.class, ClientHooks.CLIENT_TICK, f -> new ClientHooks.ClientTick(phase(f.phase)));
        bridge(net.minecraftforge.event.TickEvent.RenderTickEvent.class, ClientHooks.RENDER_TICK, f -> new ClientHooks.RenderTick(phase(f.phase), f.renderTickTime));
        bridge(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn.class, ClientHooks.LOGGING_IN, f -> new ClientHooks.LoggingIn());
        bridge(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, ClientHooks.LOGGING_OUT, f -> new ClientHooks.LoggingOut());
        bridge(net.minecraftforge.client.event.ClientChatReceivedEvent.Player.class, ClientHooks.CHAT_FROM_PLAYER, f -> new ClientHooks.ChatFromPlayer());
        bridge(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered.class, ClientHooks.INTERACTION_KEY, f -> new ClientHooks.InteractionKey(f.shouldSwingHand()),
                (e, f) -> {
                    f.setSwingHand(e.shouldSwingHand());
                });
        bridge(net.minecraftforge.client.event.InputEvent.Key.class, ClientHooks.KEY, f -> new ClientHooks.Key(f.getKey(), f.getScanCode(), f.getAction(), f.getModifiers()));
        bridge(net.minecraftforge.client.event.InputEvent.MouseButton.Pre.class, ClientHooks.MOUSE_BUTTON, f -> new ClientHooks.MouseButton(f.getButton(), f.getAction()));
        bridge(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent.class, ClientHooks.MOUSE_SCROLL, f -> new ClientHooks.MouseScroll(f.getScrollDelta()));
        bridge(net.minecraftforge.client.event.MovementInputUpdateEvent.class, ClientHooks.MOVEMENT_INPUT, f -> new ClientHooks.MovementInput(f.getInput()));
        bridge(net.minecraftforge.client.event.RegisterClientCommandsEvent.class, ClientHooks.CLIENT_COMMANDS, f -> new ClientHooks.ClientCommands(f.getDispatcher()));
        bridge(net.minecraftforge.client.event.RenderGuiEvent.Post.class, ClientHooks.RENDER_GUI, f -> new ClientHooks.RenderGui(f.getGuiGraphics(), f.getPartialTick(), f.getWindow()));
        bridge(net.minecraftforge.client.event.RenderHandEvent.class, ClientHooks.RENDER_HAND, f -> new ClientHooks.RenderHand());
        bridge(net.minecraftforge.client.event.RenderPlayerEvent.Pre.class, ClientHooks.RENDER_PLAYER, f -> new ClientHooks.RenderPlayer(f.getEntity(), f.getPartialTick()));
        bridge(net.minecraftforge.client.event.ScreenEvent.Opening.class, ClientHooks.SCREEN_OPENING, f -> new ClientHooks.ScreenOpening(f.getNewScreen()),
                (e, f) -> {
                    f.setNewScreen(e.getNewScreen());
                });
        bridge(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles.class, ClientHooks.CAMERA_ANGLES, f -> new ClientHooks.CameraAngles(f.getYaw(), f.getPitch(), f.getRoll()),
                (e, f) -> {
                    f.setYaw(e.getYaw());
                    f.setPitch(e.getPitch());
                    f.setRoll(e.getRoll());
                });
        bridge(net.minecraftforge.client.event.ViewportEvent.ComputeFov.class, ClientHooks.COMPUTE_FOV, f -> new ClientHooks.ComputeFov(f.getFOV()),
                (e, f) -> {
                    f.setFOV(e.getFOV());
                });
        bridge(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty.class, ClientHooks.LEFT_CLICK_EMPTY, f -> new ClientHooks.LeftClickEmpty());

        for (Priority p : ClientHooks.RENDER_HUD_PART.priorities()) {
            MinecraftForge.EVENT_BUS.addListener(priority(p), false, RenderGuiOverlayEvent.Pre.class, f -> fire(p, ClientHooks.RENDER_HUD_PART, ForgeClientEvents::hudPart, null, f));
        }
        for (Priority p : ClientHooks.RENDER_LEVEL.priorities()) {
            MinecraftForge.EVENT_BUS.addListener(priority(p), false, RenderLevelStageEvent.class, f -> {
                ClientHooks.LevelStage stage = f.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES ? ClientHooks.LevelStage.AFTER_PARTICLES
                        : f.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER ? ClientHooks.LevelStage.AFTER_WEATHER : null;
                if (stage == null) return;
                ClientHooks.RENDER_LEVEL.fire(p, new ClientHooks.RenderLevel(stage, f.getPoseStack(), f.getProjectionMatrix(), f.getPartialTick(), f.getCamera()));
            });
        }

        modBus.addListener((FMLClientSetupEvent f) -> ClientHooks.CLIENT_SETUP.fire(new ClientHooks.ClientSetup()));
        modBus.addListener((RegisterKeyMappingsEvent f) -> ClientHooks.REGISTER_KEYS.fire(f::register));
        modBus.addListener((RegisterGuiOverlaysEvent f) -> ClientHooks.REGISTER_HUD.fire((above, name, layer) ->
                f.registerAbove(above, name, (gui, g, partial, w, h) -> layer.draw(g, partial, w, h))));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers f) -> ClientHooks.REGISTER_RENDERERS.fire(new ClientHooks.RegisterRenderers() {
            @Override
            public <T extends net.minecraft.world.entity.Entity> void registerEntityRenderer(net.minecraft.world.entity.EntityType<? extends T> type, net.minecraft.client.renderer.entity.EntityRendererProvider<T> renderer) {
                f.registerEntityRenderer(type, renderer);
            }
        }));
    }

    private static ClientHooks.RenderHudPart hudPart(RenderGuiOverlayEvent.Pre f) {
        return new ClientHooks.RenderHudPart(f.getOverlay().id(), f.getGuiGraphics(), f.getPartialTick(), f.getWindow()) {
            @Override
            public boolean canDrawOriginal() {
                return Minecraft.getInstance().gui instanceof ForgeGui;
            }

            @Override
            public void drawOriginal(int width, int height) {
                f.getOverlay().overlay().render((ForgeGui) Minecraft.getInstance().gui, getGuiGraphics(), getPartialTick(), width, height);
            }
        };
    }
}
