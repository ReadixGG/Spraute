package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.util.*;

/**
 * Building blocks for movement and spell mechanics: no fall damage for a while, shockwaves, explosions,
 * lightning, bars at the top of the screen, cooldowns and key presses from the players.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID)
public final class Mechanics {
    private Mechanics() {
    }

    public static int now() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? 0 : s.getTickCount();
    }

    // ------------------------------------------------------------------ no fall damage

    private static final Map<UUID, Integer> FALL_SAFE = new HashMap<>();

    /** No fall damage for this many seconds (0 — clear). */
    public static void fallSafe(Entity e, double seconds) {
        if (seconds <= 0) FALL_SAFE.remove(e.getUUID());
        else FALL_SAFE.put(e.getUUID(), now() + (int) Math.round(seconds * 20));
        e.fallDistance = 0;
    }

    // ------------------------------------------------------------------ no real death

    private static final Set<UUID> UNDYING = new HashSet<>();

    /**
     * The body does not really die: a deadly blow leaves it with half its health instead, and the stories still get
     * "death" (on death { … }). For fights a story must not lose: no respawn far away, nothing reset.
     */
    public static void setUndying(Entity e, boolean on) {
        if (on) UNDYING.add(e.getUUID());
        else UNDYING.remove(e.getUUID());
    }

    public static boolean undying(Entity e) {
        return UNDYING.contains(e.getUUID());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onDeathUndying(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide || !UNDYING.contains(le.getUUID())) return;
        e.setCanceled(true);
        le.setHealth(Math.max(1, le.getMaxHealth() / 2));
        le.clearFire();
        le.fallDistance = 0;
        if (le instanceof Player p) {
            java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
            Entity killer = e.getSource().getEntity();
            if (killer != null) d.put("entity", killer instanceof Player ? "player" : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType())));
            if (killer != null) d.put("__entity", killer);
            d.put("source", e.getSource().getMsgId());
            d.put("x", Math.floor(p.getX()));
            d.put("y", Math.floor(p.getY()));
            d.put("z", Math.floor(p.getZ()));
            GameEvents.post("death", p, d);
        }
    }

    public static double fallSafeLeft(Entity e) {
        Integer until = FALL_SAFE.get(e.getUUID());
        return until == null ? 0 : Math.max(0, (until - now()) / 20.0);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (FALL_SAFE.isEmpty()) return;
        Integer until = FALL_SAFE.get(e.getEntity().getUUID());
        if (until == null) return;
        if (until < now()) {
            FALL_SAFE.remove(e.getEntity().getUUID());
            return;
        }
        e.setDistance(0);
        e.setDamageMultiplier(0);
    }

    // ------------------------------------------------------------------ area

    /**
     * Pushes every living thing in the radius away from the centre (stronger closer), a bit up,
     * hurting it if damage > 0. Returns how many it hit.
     */
    public static int shockwave(ServerLevel level, Vec3 c, double radius, double power, float damage, Entity except, Entity by) {
        int n = 0;
        AABB box = new AABB(c, c).inflate(radius);
        for (Entity en : level.getEntities((Entity) null, box, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            if (en == except) continue;
            Vec3 d = en.position().subtract(c);
            double dist = d.length();
            if (dist > radius) continue;
            double k = power * (1 - dist / radius * 0.7);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : flat.normalize();
            en.push(flat.x * k, 0.35 * k + 0.1, flat.z * k);
            en.hurtMarked = true;
            if (damage > 0) {
                var src = by != null ? level.damageSources().indirectMagic(by, by) : level.damageSources().magic();
                en.hurt(src, damage);
            }
            n++;
        }
        return n;
    }

    public static void explode(ServerLevel level, Vec3 at, float power, boolean fire, boolean breakBlocks, Entity by) {
        level.explode(by, at.x, at.y, at.z, power, fire, breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    public static void lightning(ServerLevel level, Vec3 at, boolean harmless) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(harmless);
        level.addFreshEntity(bolt);
    }

    // ------------------------------------------------------------------ bars at the top of the screen

    private static final Map<String, ServerBossEvent> BARS = new LinkedHashMap<>();

    public static void bar(String id, double value, double max, String title, String color) {
        ServerBossEvent b = BARS.get(id);
        if (b == null) {
            b = new ServerBossEvent(Component.literal(title == null ? id : title), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            BARS.put(id, b);
        }
        if (title != null) b.setName(Component.literal(title));
        if (color != null && !color.isBlank()) {
            BossEvent.BossBarColor c = switch (color.toLowerCase(Locale.ROOT)) {
                case "red", "красный" -> BossEvent.BossBarColor.RED;
                case "blue", "синий" -> BossEvent.BossBarColor.BLUE;
                case "green", "зелёный", "зеленый" -> BossEvent.BossBarColor.GREEN;
                case "yellow", "жёлтый", "желтый" -> BossEvent.BossBarColor.YELLOW;
                case "white", "белый" -> BossEvent.BossBarColor.WHITE;
                case "pink", "розовый" -> BossEvent.BossBarColor.PINK;
                default -> BossEvent.BossBarColor.PURPLE;
            };
            b.setColor(c);
        }
        b.setProgress((float) Math.max(0, Math.min(1, max <= 0 ? 0 : value / max)));
        for (ServerPlayer p : SprauteRuntime.players()) if (!b.getPlayers().contains(p)) b.addPlayer(p);
    }

    public static void hideBar(String id) {
        ServerBossEvent b = BARS.remove(id);
        if (b != null) b.removeAllPlayers();
    }

    public static void actionbar(String text) {
        for (ServerPlayer p : SprauteRuntime.players()) p.displayClientMessage(Component.literal(text), true);
    }

    // ------------------------------------------------------------------ keys

    /** A key the player pressed (from the client): stories hear "keypress". */
    public static void keyPressed(ServerPlayer p, String key) {
        SprauteRuntime.env().ifPresent(env -> env.keyPressed(key));
        if (key.equals("air.left")) {
            GameEvents.post("leftair", p, new HashMap<>());
            return;
        }
        Map<String, Object> d = new HashMap<>();
        d.put("key", key);
        d.put("onground", p.onGround());
        d.put("x", p.getX());
        d.put("y", p.getY());
        d.put("z", p.getZ());
        GameEvents.post("keypress", p, d);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        for (ServerBossEvent b : BARS.values()) if (e.getEntity() instanceof ServerPlayer sp) b.removePlayer(sp);
    }

    public static void unload() {
        for (ServerBossEvent b : BARS.values()) b.removeAllPlayers();
        BARS.clear();
        FALL_SAFE.clear();
    }

    /** Where an entity is looking: the block it looks at, else that far along its view. */
    public static Vec3 aimPoint(Entity e, double dist) {
        Raycast.Hit h = Raycast.look(new EntityRef(e), dist);
        return h.at();
    }

    static boolean isPlayer(Entity e) {
        return e instanceof Player;
    }
}
