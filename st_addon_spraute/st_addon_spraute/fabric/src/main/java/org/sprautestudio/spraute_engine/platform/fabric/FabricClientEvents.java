package org.sprautestudio.spraute_engine.platform.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.Hooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;

import java.util.ArrayList;
import java.util.List;

/** Fabric API's client events as the engine's {@link ClientHooks}. What it has no event for is fired by the mixins in {@code mixin.client}. */
final class FabricClientEvents {
    private FabricClientEvents() {
    }

    private record Layer(ResourceLocation id, ClientHooks.HudLayer draw) {
    }

    private static final List<Layer> LAYERS = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static void install() {
        ClientTickEvents.START_CLIENT_TICK.register(c -> ClientHooks.CLIENT_TICK.fire(new ClientHooks.ClientTick(TickPhase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(c -> ClientHooks.CLIENT_TICK.fire(new ClientHooks.ClientTick(TickPhase.END)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientHooks.LOGGING_IN.fire(new ClientHooks.LoggingIn()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientHooks.LOGGING_OUT.fire(new ClientHooks.LoggingOut()));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            ClientHooks.ChatFromPlayer e = new ClientHooks.ChatFromPlayer();
            ClientHooks.CHAT_FROM_PLAYER.fire(e);
            return !e.isCanceled();
        });
        ClientEntityEvents.ENTITY_LOAD.register(FabricParts::loaded);
        ClientEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            FabricParts.unloaded(e, level);
            Hooks.ENTITY_LEAVE.fire(new Hooks.EntityLeave(e, level));
        });
        // the engine's client commands never read their source, and Brigadier does not look at its type
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
                ClientHooks.CLIENT_COMMANDS.fire(new ClientHooks.ClientCommands((CommandDispatcher<CommandSourceStack>) (CommandDispatcher<?>) dispatcher)));

        HudRenderCallback.EVENT.register((g, partial) -> {
            var window = Minecraft.getInstance().getWindow();
            for (Layer l : LAYERS) {
                ClientHooks.RenderHudPart part = HudParts.part(l.id(), g, partial);
                ClientHooks.RENDER_HUD_PART.fire(part);
                if (!part.isCanceled()) l.draw().draw(g, partial, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
            ClientHooks.RENDER_GUI.fire(new ClientHooks.RenderGui(g, partial, window));
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(c -> ClientHooks.RENDER_LEVEL.fire(new ClientHooks.RenderLevel(ClientHooks.LevelStage.AFTER_PARTICLES,
                c.matrixStack(), c.projectionMatrix(), c.tickDelta(), c.camera())));
        WorldRenderEvents.LAST.register(c -> ClientHooks.RENDER_LEVEL.fire(new ClientHooks.RenderLevel(ClientHooks.LevelStage.AFTER_WEATHER,
                c.matrixStack(), c.projectionMatrix(), c.tickDelta(), c.camera())));

        ClientHooks.REGISTER_KEYS.fire(KeyBindingHelper::registerKeyBinding);
        ClientHooks.REGISTER_HUD.fire((above, name, layer) -> LAYERS.add(new Layer(new ResourceLocation(SprauteEngine.MODID, name), layer)));
        ClientHooks.REGISTER_RENDERERS.fire(new ClientHooks.RegisterRenderers() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> renderer) {
                EntityRendererRegistry.register(type, renderer);
            }
        });
        ClientHooks.CLIENT_SETUP.fire(new ClientHooks.ClientSetup());
    }
}
