package org.sprautestudio.st_addon_spraute.platform.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.sprautestudio.st_addon_spraute.platform.Cancellable;
import org.sprautestudio.st_addon_spraute.platform.Hook;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
import org.sprautestudio.st_addon_spraute.platform.Priority;
import org.sprautestudio.st_addon_spraute.platform.TickPhase;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Forge's events as the engine's {@link Hooks}. */
final class ForgeEvents {
    private ForgeEvents() {
    }

    static TickPhase phase(TickEvent.Phase phase) {
        return phase == TickEvent.Phase.START ? TickPhase.START : TickPhase.END;
    }

    static EventPriority priority(Priority p) {
        return EventPriority.valueOf(p.name());
    }

    static <F extends Event, E> void bridge(Class<F> type, Hook<E> hook, Function<F, E> wrap) {
        bridge(type, hook, wrap, null);
    }

    /** A listener on Forge's bus for each priority the engine uses, so that the engine keeps its place among other mods. */
    static <F extends Event, E> void bridge(Class<F> type, Hook<E> hook, Function<F, E> wrap, BiConsumer<E, F> back) {
        for (Priority p : hook.priorities()) {
            MinecraftForge.EVENT_BUS.addListener(priority(p), hook.receivesCanceled(), type, f -> fire(p, hook, wrap, back, f));
        }
    }

    static <F extends Event, E> void fire(Priority p, Hook<E> hook, Function<F, E> wrap, BiConsumer<E, F> back, F f) {
        E e = wrap.apply(f);
        hook.fire(p, e);
        if (e instanceof Cancellable c && c.isCanceled()) f.setCanceled(true);
        if (back != null) back.accept(e, f);
    }

    static void install() {
        bridge(net.minecraftforge.event.server.ServerAboutToStartEvent.class, Hooks.SERVER_ABOUT_TO_START, f -> new Hooks.ServerAboutToStart(f.getServer()));
        bridge(net.minecraftforge.event.server.ServerStoppingEvent.class, Hooks.SERVER_STOPPING, f -> new Hooks.ServerStopping(f.getServer()));
        bridge(net.minecraftforge.event.server.ServerStoppedEvent.class, Hooks.SERVER_STOPPED, f -> new Hooks.ServerStopped(f.getServer()));
        bridge(net.minecraftforge.event.level.LevelEvent.Save.class, Hooks.LEVEL_SAVE, f -> new Hooks.LevelSave(f.getLevel()));
        bridge(net.minecraftforge.event.RegisterCommandsEvent.class, Hooks.REGISTER_COMMANDS, f -> new Hooks.RegisterCommands(f.getDispatcher()));
        bridge(net.minecraftforge.event.TickEvent.ServerTickEvent.class, Hooks.SERVER_TICK, f -> new Hooks.ServerTick(phase(f.phase), f.getServer()));
        bridge(net.minecraftforge.event.TickEvent.PlayerTickEvent.class, Hooks.PLAYER_TICK, f -> new Hooks.PlayerTick(phase(f.phase), f.player));
        bridge(net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent.class, Hooks.ADVANCEMENT_EARN, f -> new Hooks.AdvancementEarn(f.getEntity(), f.getAdvancement()));
        bridge(net.minecraftforge.event.entity.living.AnimalTameEvent.class, Hooks.ANIMAL_TAME, f -> new Hooks.AnimalTame(f.getAnimal(), f.getTamer()));
        bridge(net.minecraftforge.event.entity.player.ArrowLooseEvent.class, Hooks.ARROW_LOOSE, f -> new Hooks.ArrowLoose(f.getEntity(), f.getBow(), f.getCharge()));
        bridge(net.minecraftforge.event.entity.player.AttackEntityEvent.class, Hooks.ATTACK_ENTITY, f -> new Hooks.AttackEntity(f.getEntity(), f.getTarget()));
        bridge(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent.class, Hooks.BABY_SPAWN, f -> new Hooks.BabySpawn(f.getChild(), f.getCausedByPlayer()));
        bridge(net.minecraftforge.event.level.BlockEvent.BreakEvent.class, Hooks.BLOCK_BREAK, f -> new Hooks.BlockBreak(f.getPos(), f.getState(), f.getPlayer()));
        bridge(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class, Hooks.BLOCK_PLACE, f -> new Hooks.BlockPlace(f.getEntity(), f.getPos(), f.getPlacedBlock()));
        bridge(net.minecraftforge.event.entity.player.BonemealEvent.class, Hooks.BONEMEAL, f -> new Hooks.Bonemeal(f.getEntity(), f.getPos(), f.getBlock()));
        bridge(net.minecraftforge.event.entity.EntityJoinLevelEvent.class, Hooks.ENTITY_JOIN, f -> new Hooks.EntityJoin(f.getEntity(), f.getLevel()));
        bridge(net.minecraftforge.event.entity.EntityLeaveLevelEvent.class, Hooks.ENTITY_LEAVE, f -> new Hooks.EntityLeave(f.getEntity(), f.getLevel()));
        bridge(net.minecraftforge.event.entity.EntityMountEvent.class, Hooks.ENTITY_MOUNT, f -> new Hooks.EntityMount(f.getEntityMounting(), f.getEntityBeingMounted(), f.isMounting()));
        bridge(net.minecraftforge.event.level.ExplosionEvent.Detonate.class, Hooks.EXPLOSION_DETONATE, f -> new Hooks.ExplosionDetonate(f.getExplosion(), f.getLevel()));
        bridge(net.minecraftforge.event.entity.player.FillBucketEvent.class, Hooks.FILL_BUCKET, f -> new Hooks.FillBucket(f.getEntity(), f.getEmptyBucket()));
        bridge(net.minecraftforge.event.entity.player.ItemFishedEvent.class, Hooks.ITEM_FISHED, f -> new Hooks.ItemFished(f.getEntity(), f.getDrops()));
        bridge(net.minecraftforge.event.entity.item.ItemTossEvent.class, Hooks.ITEM_TOSS, f -> new Hooks.ItemToss(f.getEntity(), f.getPlayer()));
        bridge(net.minecraftforge.event.entity.living.LivingAttackEvent.class, Hooks.LIVING_ATTACK, f -> new Hooks.LivingAttack(f.getEntity(), f.getSource(), f.getAmount()));
        bridge(net.minecraftforge.event.entity.living.LivingDeathEvent.class, Hooks.LIVING_DEATH, f -> new Hooks.LivingDeath(f.getEntity(), f.getSource()));
        bridge(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish.class, Hooks.USE_ITEM_FINISH, f -> new Hooks.UseItemFinish(f.getEntity(), f.getItem()));
        bridge(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent.class, Hooks.EQUIPMENT_CHANGE, f -> new Hooks.EquipmentChange(f.getEntity(), f.getSlot(), f.getTo()));
        bridge(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent.class, Hooks.LIVING_JUMP, f -> new Hooks.LivingJump(f.getEntity()));
        bridge(net.minecraftforge.event.entity.living.LivingFallEvent.class, Hooks.LIVING_FALL, f -> new Hooks.LivingFall(f.getEntity(), f.getDistance(), f.getDamageMultiplier()),
                (e, f) -> {
                    f.setDistance(e.getDistance());
                    f.setDamageMultiplier(e.getDamageMultiplier());
                });
        bridge(net.minecraftforge.event.entity.living.LivingHurtEvent.class, Hooks.LIVING_HURT, f -> new Hooks.LivingHurt(f.getEntity(), f.getSource(), f.getAmount()));
        bridge(net.minecraftforge.event.entity.living.MobEffectEvent.Added.class, Hooks.EFFECT_ADDED, f -> new Hooks.EffectAdded(f.getEntity(), f.getEffectInstance()));
        bridge(net.minecraftforge.event.entity.player.PlayerContainerEvent.Close.class, Hooks.CONTAINER_CLOSE, f -> new Hooks.ContainerClose(f.getEntity(), f.getContainer()));
        bridge(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open.class, Hooks.CONTAINER_OPEN, f -> new Hooks.ContainerOpen(f.getEntity(), f.getContainer()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent.class, Hooks.ITEM_CRAFTED, f -> new Hooks.ItemCrafted(f.getEntity(), f.getCrafting()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent.class, Hooks.ITEM_PICKUP, f -> new Hooks.ItemPickup(f.getEntity(), f.getStack()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent.class, Hooks.ITEM_SMELTED, f -> new Hooks.ItemSmelted(f.getEntity(), f.getSmelting()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class, Hooks.CHANGED_DIMENSION, f -> new Hooks.ChangedDimension(f.getEntity(), f.getFrom(), f.getTo()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class, Hooks.PLAYER_LOGGED_IN, f -> new Hooks.PlayerLoggedIn(f.getEntity()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class, Hooks.PLAYER_LOGGED_OUT, f -> new Hooks.PlayerLoggedOut(f.getEntity()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class, Hooks.PLAYER_RESPAWN, f -> new Hooks.PlayerRespawn(f.getEntity()));
        bridge(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking.class, Hooks.START_TRACKING, f -> new Hooks.StartTracking(f.getEntity(), f.getTarget()));
        bridge(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, Hooks.ENTITY_INTERACT, f -> new Hooks.EntityInteract(f.getEntity(), f.getLevel(), f.getHand(), f.getTarget(), f.getItemStack()));
        bridge(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.class, Hooks.LEFT_CLICK_BLOCK, f -> new Hooks.LeftClickBlock(f.getEntity(), f.getLevel(), f.getPos(), f.getItemStack(), f.getAction() == net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START));
        bridge(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, Hooks.RIGHT_CLICK_BLOCK, f -> new Hooks.RightClickBlock(f.getEntity(), f.getLevel(), f.getHand(), f.getPos(), f.getItemStack()));
        bridge(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem.class, Hooks.RIGHT_CLICK_ITEM, f -> new Hooks.RightClickItem(f.getEntity(), f.getItemStack()));
        bridge(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent.class, Hooks.SLEEP_IN_BED, f -> new Hooks.SleepInBed(f.getEntity(), f.getPos()));
        bridge(net.minecraftforge.event.entity.player.PlayerWakeUpEvent.class, Hooks.WAKE_UP, f -> new Hooks.WakeUp(f.getEntity()));
        bridge(net.minecraftforge.event.entity.player.PlayerXpEvent.LevelChange.class, Hooks.XP_LEVEL_CHANGE, f -> new Hooks.XpLevelChange(f.getEntity(), f.getLevels()));
        bridge(net.minecraftforge.event.entity.player.PlayerXpEvent.PickupXp.class, Hooks.XP_PICKUP, f -> new Hooks.XpPickup(f.getEntity(), f.getOrb()));
        bridge(net.minecraftforge.event.ServerChatEvent.class, Hooks.SERVER_CHAT, f -> new Hooks.ServerChat(f.getPlayer(), f.getRawText()));
        bridge(net.minecraftforge.event.entity.player.TradeWithVillagerEvent.class, Hooks.TRADE, f -> new Hooks.Trade(f.getEntity(), f.getAbstractVillager(), f.getMerchantOffer()));
    }
}
