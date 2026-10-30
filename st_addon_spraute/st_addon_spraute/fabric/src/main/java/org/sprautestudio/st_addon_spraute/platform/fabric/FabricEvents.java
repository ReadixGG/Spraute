package org.sprautestudio.st_addon_spraute.platform.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.Platform;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

/** Fabric API's events as the engine's {@link Hooks}. What it has no event for is fired by the mixins in {@code mixin}. */
final class FabricEvents {
    private FabricEvents() {
    }

    static void install() {
        // the very first of this moment: the engine's own listeners ask the platform for the server
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            ((FabricPlatform) Platform.get()).setServer(s);
            Hooks.SERVER_ABOUT_TO_START.fire(new Hooks.ServerAboutToStart(s));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> Hooks.SERVER_STOPPING.fire(new Hooks.ServerStopping(s)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> Hooks.SERVER_STOPPED.fire(new Hooks.ServerStopped(s)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> Hooks.REGISTER_COMMANDS.fire(new Hooks.RegisterCommands(dispatcher)));
        ServerTickEvents.START_SERVER_TICK.register(s -> Hooks.SERVER_TICK.fire(new Hooks.ServerTick(TickPhase.START, s)));
        ServerTickEvents.END_SERVER_TICK.register(s -> Hooks.SERVER_TICK.fire(new Hooks.ServerTick(TickPhase.END, s)));

        ServerEntityEvents.ENTITY_LOAD.register(FabricParts::loaded);
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            FabricParts.unloaded(e, level);
            Hooks.ENTITY_LEAVE.fire(new Hooks.EntityLeave(e, level));
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            Hooks.BlockBreak e = new Hooks.BlockBreak(pos, state, player);
            Hooks.BLOCK_BREAK.fire(e);
            return !e.isCanceled();
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            Hooks.AttackEntity e = new Hooks.AttackEntity(player, target);
            Hooks.ATTACK_ENTITY.fire(e);
            return e.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            Hooks.LEFT_CLICK_BLOCK.fire(new Hooks.LeftClickBlock(player, level, pos, player.getItemInHand(hand), true));
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Hooks.RIGHT_CLICK_BLOCK.fire(new Hooks.RightClickBlock(player, level, hand, hit.getBlockPos(), player.getItemInHand(hand)));
            return InteractionResult.PASS;
        });
        // the game asks twice for a click on an entity: with the spot on it, and without. Forge's event is the second.
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (hit == null) Hooks.ENTITY_INTERACT.fire(new Hooks.EntityInteract(player, level, hand, target, player.getItemInHand(hand)));
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Hooks.RIGHT_CLICK_ITEM.fire(new Hooks.RightClickItem(player, player.getItemInHand(hand)));
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
            if (entity instanceof Player p) Hooks.SLEEP_IN_BED.fire(new Hooks.SleepInBed(p, pos));
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof Player p) Hooks.WAKE_UP.fire(new Hooks.WakeUp(p));
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) ->
                Hooks.CHANGED_DIMENSION.fire(new Hooks.ChangedDimension(player, from.dimension(), to.dimension())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Hooks.PLAYER_LOGGED_IN.fire(new Hooks.PlayerLoggedIn(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Hooks.PLAYER_LOGGED_OUT.fire(new Hooks.PlayerLoggedOut(handler.player)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Hooks.PLAYER_RESPAWN.fire(new Hooks.PlayerRespawn(newPlayer)));
        EntityTrackingEvents.START_TRACKING.register((target, player) -> Hooks.START_TRACKING.fire(new Hooks.StartTracking(player, target)));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> Hooks.SERVER_CHAT.fire(new Hooks.ServerChat(sender, message.signedContent())));
    }
}
