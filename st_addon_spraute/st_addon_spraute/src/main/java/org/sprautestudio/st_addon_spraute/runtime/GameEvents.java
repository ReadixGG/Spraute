package org.sprautestudio.st_addon_spraute.runtime;

import org.sprautestudio.st_addon_spraute.core.script.ScriptInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns game happenings into story events. Every running Spraute story gets each event with its
 * data; {@code wait <event>} and {@code on <event>} pick them up from the story's event log.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class GameEvents {
    private GameEvents() {}

    // ------------------------------------------------------------------ dispatch

    /** For other systems (shops): an event every story can wait for. */
    public static void post(String type, Player player, Map<String, Object> data) {
        fire(type, player, data);
    }

    public static void fire(String type, Player player, Map<String, Object> data) {
        if (player != null && !(player instanceof ServerPlayer)) return; // server side only
        if (player != null) data.putIfAbsent("player", player.getGameProfile().getName());
        SprauteRuntime.env().ifPresent(env -> {
            for (ScriptInstance si : env.scriptInstances.values()) {
                if (si instanceof SprauteScriptInstance s) s.events.add(type, data, s);
            }
        });
    }

    private static Map<String, Object> data() {
        return new LinkedHashMap<>();
    }

    private static String id(BlockState st) {
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
    }

    private static String id(ItemStack st) {
        return st == null || st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private static Map<String, Object> at(Map<String, Object> d, BlockPos p) {
        d.put("x", (double) p.getX());
        d.put("y", (double) p.getY());
        d.put("z", (double) p.getZ());
        return d;
    }

    private static Map<String, Object> at(Map<String, Object> d, Entity e) {
        d.put("x", Math.floor(e.getX()));
        d.put("y", Math.floor(e.getY()));
        d.put("z", Math.floor(e.getZ()));
        return d;
    }

    /** Type, name and (per story, filled later) character of an entity. */
    private static Map<String, Object> entity(Map<String, Object> d, Entity e) {
        if (e == null) return d;
        d.put("entity", e instanceof Player ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        d.put("name", e.getName().getString());
        d.put("__entity", e);
        return d;
    }

    private static Player player(Entity e) {
        return e instanceof Player p ? p : null;
    }

    // ------------------------------------------------------------------ chat

    /**
     * Spraute cancels every chat message and never passes it to its own chat waits, so chat is
     * read here (cancelled messages included) and handed to stories as the "chat" event.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onChat(net.minecraftforge.event.ServerChatEvent e) {
        Map<String, Object> d = data();
        d.put("text", e.getRawText());
        fire("chat", e.getPlayer(), d);
    }

    // ------------------------------------------------------------------ blocks

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled()) return;
        Map<String, Object> d = at(data(), e.getPos());
        d.put("block", id(e.getState()));
        d.put("item", id(e.getPlayer().getMainHandItem()));
        fire("break", e.getPlayer(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof Player p)) return;
        Map<String, Object> d = at(data(), e.getPos());
        d.put("block", id(e.getPlacedBlock()));
        fire("place", p, d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPunch(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Map<String, Object> d = at(data(), e.getPos());
        d.put("block", id(e.getLevel().getBlockState(e.getPos())));
        d.put("item", id(e.getItemStack()));
        fire("punch", e.getEntity(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Map<String, Object> d = at(data(), e.getPos());
        d.put("block", id(e.getLevel().getBlockState(e.getPos())));
        d.put("item", id(e.getItemStack()));
        fire("useblock", e.getEntity(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBonemeal(BonemealEvent e) {
        Map<String, Object> d = at(data(), e.getPos());
        d.put("block", id(e.getBlock()));
        fire("bonemeal", e.getEntity(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBucket(FillBucketEvent e) {
        Map<String, Object> d = at(data(), e.getEntity().blockPosition());
        d.put("item", id(e.getEmptyBucket()));
        fire("bucket", e.getEntity(), d);
    }

    // ------------------------------------------------------------------ creatures

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Map<String, Object> d = at(entity(data(), e.getTarget()), e.getTarget());
        d.put("item", id(e.getItemStack()));
        fire("interact", e.getEntity(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent e) {
        if (e.isCanceled()) return;
        Map<String, Object> d = at(entity(data(), e.getTarget()), e.getTarget());
        d.put("item", id(e.getEntity().getMainHandItem()));
        fire("attack", e.getEntity(), d);
    }

    /** Deaths already told (Forge may announce one death twice): entity -> server tick. */
    private static final Map<java.util.UUID, Integer> DEATHS = new java.util.HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || e.getEntity().level().isClientSide) return;
        int now = Mechanics.now();
        Integer told = DEATHS.get(e.getEntity().getUUID());
        if (told != null && now - told < 100) return;
        DEATHS.put(e.getEntity().getUUID(), now);
        if (DEATHS.size() > 512) DEATHS.values().removeIf(t -> now - t > 100);
        Entity killer = e.getSource().getEntity();
        if (e.getEntity() instanceof Player victim) {
            Map<String, Object> d = at(entity(data(), killer), victim);
            d.put("source", e.getSource().getMsgId());
            fire("death", victim, d);
        } else {
            if (killer instanceof Player p) {
                Map<String, Object> d = at(entity(data(), e.getEntity()), e.getEntity());
                d.put("item", id(p.getMainHandItem()));
                fire("kill", p, d);
            }
            // any death of a character or a mob, whoever (or whatever) did it
            Map<String, Object> d = at(entity(data(), e.getEntity()), e.getEntity());
            d.put("source", e.getSource().getMsgId());
            putKiller(d, killer);
            fire("npcdeath", player(killer) != null ? player(killer) : SprauteRuntime.player(), d);
        }
    }

    /** Who killed: "player", the story's character (filled per story), a mob's id, or "" — a fall, fire, lava… */
    static void putKiller(Map<String, Object> d, Entity killer) {
        if (killer == null) {
            d.put("by", "");
            d.put("killer", "");
            return;
        }
        String type = killer instanceof Player ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString();
        d.put("by", type);
        d.put("killer", type);
        d.put("__killer", killer);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof Player p)) return;
        Map<String, Object> d = at(entity(data(), e.getSource().getEntity()), p);
        d.put("amount", (double) e.getAmount());
        d.put("source", e.getSource().getMsgId());
        fire("hurt", p, d);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        fire("respawn", e.getEntity(), at(data(), e.getEntity()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTame(AnimalTameEvent e) {
        if (e.isCanceled()) return;
        fire("tame", e.getTamer(), at(entity(data(), e.getAnimal()), e.getAnimal()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreed(BabyEntitySpawnEvent e) {
        if (e.isCanceled() || e.getChild() == null) return;
        fire("breed", e.getCausedByPlayer(), at(entity(data(), e.getChild()), e.getChild()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMount(EntityMountEvent e) {
        if (e.isCanceled() || !(e.getEntityMounting() instanceof Player p)) return;
        fire(e.isMounting() ? "mount" : "dismount", p, entity(data(), e.getEntityBeingMounted()));
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent e) {
        Map<String, Object> d = entity(data(), e.getAbstractVillager());
        d.put("item", id(e.getMerchantOffer().getResult()));
        d.put("count", (double) e.getMerchantOffer().getResult().getCount());
        fire("trade", e.getEntity(), d);
    }

    // ------------------------------------------------------------------ items

    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent e) {
        Map<String, Object> d = data();
        d.put("item", id(e.getStack()));
        d.put("count", (double) e.getStack().getCount());
        fire("pickup", e.getEntity(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToss(ItemTossEvent e) {
        if (e.isCanceled()) return;
        ItemStack st = e.getEntity().getItem();
        Map<String, Object> d = data();
        d.put("item", id(st));
        d.put("count", (double) st.getCount());
        fire("drop", e.getPlayer(), d);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickItem e) {
        Map<String, Object> d = data();
        d.put("item", id(e.getItemStack()));
        fire("use", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Map<String, Object> d = data();
        d.put("item", id(e.getItem()));
        fire("eat", p, d);
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent e) {
        Map<String, Object> d = data();
        d.put("item", id(e.getCrafting()));
        d.put("count", (double) e.getCrafting().getCount());
        fire("craft", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent e) {
        Map<String, Object> d = data();
        d.put("item", id(e.getSmelting()));
        d.put("count", (double) e.getSmelting().getCount());
        fire("smelt", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getTo().isEmpty()) return;
        // hotbar switching is reported as "select"
        if (e.getSlot() == EquipmentSlot.MAINHAND) return;
        Map<String, Object> d = data();
        d.put("item", id(e.getTo()));
        d.put("slot", e.getSlot().getName());
        fire("equip", p, d);
    }

    @SubscribeEvent
    public static void onShoot(ArrowLooseEvent e) {
        Map<String, Object> d = data();
        d.put("item", id(e.getBow()));
        d.put("amount", (double) e.getCharge());
        fire("shoot", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onFish(ItemFishedEvent e) {
        for (ItemStack st : e.getDrops()) {
            Map<String, Object> d = data();
            d.put("item", id(st));
            d.put("count", (double) st.getCount());
            fire("fish", e.getEntity(), d);
        }
    }

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open e) {
        Map<String, Object> d = data();
        d.put("id", menuId(e.getContainer()));
        fire("opencontainer", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close e) {
        Map<String, Object> d = data();
        d.put("id", menuId(e.getContainer()));
        fire("closecontainer", e.getEntity(), d);
    }

    private static String menuId(net.minecraft.world.inventory.AbstractContainerMenu m) {
        try {
            var key = BuiltInRegistries.MENU.getKey(m.getType());
            return key != null ? key.toString() : "inventory";
        } catch (RuntimeException ex) {
            return "inventory";
        }
    }

    // ------------------------------------------------------------------ the player

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof Player p) fire("jump", p, at(data(), p));
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getDistance() <= 3) return;
        Map<String, Object> d = at(data(), p);
        d.put("amount", (double) Math.round(e.getDistance() * 10) / 10.0);
        fire("fall", p, d);
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent e) {
        fire("sleep", e.getEntity(), at(data(), e.getPos()));
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent e) {
        fire("wake", e.getEntity(), data());
    }

    @SubscribeEvent
    public static void onLevel(PlayerXpEvent.LevelChange e) {
        Map<String, Object> d = data();
        d.put("level", (double) (e.getEntity().experienceLevel + e.getLevels()));
        fire("levelup", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onXp(PlayerXpEvent.PickupXp e) {
        Map<String, Object> d = data();
        d.put("amount", (double) e.getOrb().getValue());
        fire("xp", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Added e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Map<String, Object> d = data();
        var key = BuiltInRegistries.MOB_EFFECT.getKey(e.getEffectInstance().getEffect());
        d.put("id", key != null ? key.toString() : "");
        d.put("level", (double) (e.getEffectInstance().getAmplifier() + 1));
        fire("effect", p, d);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent e) {
        Map<String, Object> d = data();
        d.put("id", e.getAdvancement().getId().toString());
        fire("advancement", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent e) {
        fire("join", e.getEntity(), data());
        if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) SprauteRuntime.get().playerJoined(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Map<String, Object> d = data();
        d.put("from", e.getFrom().location().toString());
        d.put("to", e.getTo().location().toString());
        fire("dimension", e.getEntity(), d);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        var pos = e.getExplosion().getPosition();
        for (Player p : e.getLevel().players()) {
            if (p.position().distanceTo(pos) > 24) continue;
            Map<String, Object> d = data();
            d.put("x", Math.floor(pos.x));
            d.put("y", Math.floor(pos.y));
            d.put("z", Math.floor(pos.z));
            fire("explosion", p, d);
        }
    }

    /** Cutscene progress reported by the client. */
    public static void camera(ServerPlayer player, int scriptId, String kind, String name) {
        Map<String, Object> d = data();
        d.put("id", name);
        String type = switch (kind) {
            case "skip" -> "cutsceneskip";
            case "mark" -> "cutscenemark";
            default -> "cutscene";
        };
        if (type.equals("cutscene")) {
            SprauteRuntime.env().ifPresent(env -> {
                if (env.scriptInstances.get(scriptId) instanceof SprauteScriptInstance s && s.cutscene.equals(name)) s.cutscene = "";
            });
        }
        fire(type, player, d);
    }

    /** A story orb was picked up or expired. */
    public static void orb(String type, Player player, org.sprautestudio.st_addon_spraute.runtime.orb.SprauteOrb orb) {
        fire(type, player, orb.eventData());
    }

    // ------------------------------------------------------------------ polled states

    private static int lastCoins = Integer.MIN_VALUE;

    /** Last seen state per player: sneaking, sprinting, in water, burning, low health, hotbar slot, biome. */
    private static final Map<UUID, Object[]> STATE = new HashMap<>();
    private static long lastDayTime = -1;
    private static Boolean lastRain;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        String biome = p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
        boolean low = p.getHealth() < p.getMaxHealth() / 3f;
        Object[] now = {p.isShiftKeyDown(), p.isSprinting(), p.isInWater(), p.isOnFire(), low, p.getInventory().selected, biome};
        Object[] was = STATE.put(p.getUUID(), now);
        if (was == null) return;
        if (!(Boolean) was[0] && (Boolean) now[0]) fire("sneak", p, at(data(), p));
        if (!(Boolean) was[1] && (Boolean) now[1]) fire("sprint", p, at(data(), p));
        if (!(Boolean) was[2] && (Boolean) now[2]) fire("swim", p, at(data(), p));
        if (!(Boolean) was[3] && (Boolean) now[3]) fire("burn", p, at(data(), p));
        if (!(Boolean) was[4] && (Boolean) now[4]) {
            Map<String, Object> d = data();
            d.put("amount", (double) p.getHealth());
            fire("lowhealth", p, d);
        }
        if (!was[5].equals(now[5])) {
            Map<String, Object> d = data();
            d.put("slot", (double) ((Integer) now[5] + 1));
            d.put("item", id(p.getInventory().getSelected()));
            fire("select", p, d);
        }
        if (!was[6].equals(now[6])) {
            Map<String, Object> d = data();
            d.put("biome", now[6]);
            d.put("from", was[6]);
            fire("biome", p, d);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        org.sprautestudio.st_addon_spraute.engine.EngineServer.tick(server);
        SprauteRuntime.get().tickAutostart();
        org.sprautestudio.st_addon_spraute.zone.Zones.tick(server);
        var level = server.overworld();
        long t = level.getDayTime() % 24000;
        if (lastDayTime >= 0) {
            if (crossed(lastDayTime, t, 0)) fire("day", null, data());
            if (crossed(lastDayTime, t, 13000)) fire("night", null, data());
        }
        lastDayTime = t;
        try {
            int coins = org.sprautestudio.st_addon_spraute.core.system.Coins.get(SprauteRuntime.player());
            if (lastCoins != Integer.MIN_VALUE && coins != lastCoins) {
                Map<String, Object> d = data();
                d.put("amount", (double) (coins - lastCoins));
                d.put("level", (double) coins);
                var players = server.getPlayerList().getPlayers();
                fire("coins", players.isEmpty() ? null : players.get(0), d);
            }
            lastCoins = coins;
        } catch (Throwable ignored) {
        }
        // custom-model characters get their model as soon as they appear
        SprauteRuntime.env().ifPresent(env -> {
            for (var si : env.scriptInstances.values()) {
                if (!(si instanceof SprauteScriptInstance s) || s.characterModels.isEmpty()) continue;
                s.characterModels.forEach((name, key) -> {
                    var ch = s.characters.get(name);
                    if (ch != null && s.optional(ch).orElse(null) instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity me
                            && !key.equals(me.model())) me.setModel(key);
                });
            }
        });
        boolean rain = level.isRaining();
        if (lastRain != null && lastRain != rain) fire(rain ? "rain" : "clear", null, data());
        lastRain = rain;
    }

    /** True when the day clock moved past {@code mark} (wrapping at midnight); big jumps from time commands count too. */
    private static boolean crossed(long from, long to, long mark) {
        if (from == to) return false;
        if (from < to) return from < mark && mark <= to;
        return mark > from || mark <= to;
    }

    public static void reset() {
        STATE.clear();
        lastDayTime = -1;
        lastRain = null;
        lastCoins = Integer.MIN_VALUE;
    }
}
