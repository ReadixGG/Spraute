package org.zonarstudio.spraute_engine.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.zonarstudio.spraute_engine.scene.SceneLightRuntime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Particle-as-light: lets a particle system carry a real, moving vanilla block-light
 * ({@code minecraft:light}) instead of just looking bright. Driven by
 * {@link ParticleGameplayRuntime}, which is the only place server-authoritative particle
 * positions exist (client-only visual particles have no server position at all) — a system
 * only lights up while its {@code spec.gameplay} proxies are running, which
 * {@code ParticleSystemSpec.harden()} enables automatically once {@code spec.light.enabled}
 * is set.
 *
 * <p>Deliberately capped and TTL-based rather than exactly reconciled: {@link #refresh}
 * is called at most {@code spec.light.maxLights} times per system per tick (enforced by the
 * caller), each call places/keeps a light block at the proxy's current cell and bumps that
 * cell's expiry a little into the future. A light-weight periodic sweep in {@link #tick}
 * removes any cell nobody has refreshed recently — covering particle death, a whole system
 * being cleared, or a dimension going quiet — without needing to track which particle "owns"
 * which light block. The trade-off is a short (roughly half a second) lag before a light
 * follows its particle or fades out, in exchange for a much simpler and cheaper runtime:
 * real block-light placement recalculates the level's light engine, so this stays rare and
 * capped rather than exact and per-particle.
 */
public final class ParticleLightRuntime {
    private static final int GRACE_TICKS = 10;
    private static final int SWEEP_INTERVAL_TICKS = 20;
    private static final int MAX_TOTAL_LIGHTS = 64;

    private record Placed(String dimension, int level, long expiresAtTick) {}

    private static final Map<BlockPos, Placed> PLACED = new HashMap<>();
    private static long tickCounter;

    private ParticleLightRuntime() {}

    /** Called once per live, light-carrying proxy per tick (already capped to maxLights by the caller). */
    static void refresh(ServerLevel level, ParticleSystemSpec spec, double x, double y, double z) {
        if (level == null || spec == null || spec.light == null || !spec.light.enabled) return;
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int wantedLevel = Mth.clamp(spec.light.level, 0, 15);
        String dimension = level.dimension().location().toString();
        long expiry = tickCounter + GRACE_TICKS;

        Placed existing = PLACED.get(pos);
        if (existing != null && existing.level == wantedLevel && dimension.equals(existing.dimension)) {
            PLACED.put(pos, new Placed(dimension, wantedLevel, expiry));
            return;
        }
        if (existing == null && PLACED.size() >= MAX_TOTAL_LIGHTS) return;
        if (SceneLightRuntime.tryPlace(level, pos, wantedLevel)) {
            PLACED.put(pos, new Placed(dimension, wantedLevel, expiry));
        }
    }

    /** Call once per server tick, after {@link ParticleGameplayRuntime#tick}. */
    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (server == null || PLACED.isEmpty() || tickCounter % SWEEP_INTERVAL_TICKS != 0) return;
        Iterator<Map.Entry<BlockPos, Placed>> it = PLACED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Placed> entry = it.next();
            Placed placed = entry.getValue();
            if (placed.expiresAtTick >= tickCounter) continue;
            it.remove();
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(placed.dimension)) {
                    SceneLightRuntime.clearLightBlock(level, entry.getKey());
                    break;
                }
            }
        }
    }

    /** Editor / debug / dimension-unload safety: force-clears everything this runtime placed. */
    public static void clearAll(MinecraftServer server) {
        if (server == null) {
            PLACED.clear();
            return;
        }
        for (Map.Entry<BlockPos, Placed> entry : PLACED.entrySet()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(entry.getValue().dimension)) {
                    SceneLightRuntime.clearLightBlock(level, entry.getKey());
                    break;
                }
            }
        }
        PLACED.clear();
    }
}
