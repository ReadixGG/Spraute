package org.zonarstudio.spraute_engine.particle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.script.ScriptCustomEvents;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Optional capped server proxy for particle gameplay queries and overlap events. */
public final class ParticleGameplayRuntime {
    public static final String BIRTH = "particleBirth";
    public static final String DEATH = "particleDeath";
    public static final String COLLISION = "particleCollision";
    public static final String ENTER = "particleEnter";
    public static final String EXIT = "particleExit";

    private static final Map<String, List<Proxy>> BY_DIMENSION = new HashMap<>();

    static {
        ensureEvents();
    }

    public static void ensureEvents() {
        ScriptCustomEvents.register(BIRTH);
        ScriptCustomEvents.register(DEATH);
        ScriptCustomEvents.register(COLLISION);
        ScriptCustomEvents.register(ENTER);
        ScriptCustomEvents.register(EXIT);
    }

    private ParticleGameplayRuntime() {}

    public static void spawn(ServerLevel level, String systemId, ParticleSystemSpec spec, float[] data) {
        if (level == null || spec == null || spec.gameplay == null || !spec.gameplay.enabled) return;
        String dimension = level.dimension().location().toString();
        List<Proxy> proxies = BY_DIMENSION.computeIfAbsent(dimension, ignored -> new ArrayList<>());
        int current = 0;
        for (Proxy proxy : proxies) if (proxy.systemId.equals(systemId)) current++;
        int cap = Math.max(1, Math.min(4096, spec.gameplay.maxProxies));
        ParticleSystemSpec snapshot = spec.copy();
        for (int i = 0; i + 5 < data.length && current < cap; i += 6, current++) {
            Proxy proxy = new Proxy(systemId, snapshot, data, i);
            proxies.add(proxy);
            fire(BIRTH, proxy, null);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || BY_DIMENSION.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            List<Proxy> proxies = BY_DIMENSION.get(level.dimension().location().toString());
            if (proxies == null || proxies.isEmpty()) continue;
            Map<String, Integer> lightsThisTick = new HashMap<>();
            Iterator<Proxy> iterator = proxies.iterator();
            while (iterator.hasNext()) {
                Proxy proxy = iterator.next();
                if (!proxy.tick(level)) {
                    fire(DEATH, proxy, null);
                    iterator.remove();
                    continue;
                }
                ParticleSystemSpec.Light light = proxy.spec.light;
                if (light != null && light.enabled) {
                    int used = lightsThisTick.getOrDefault(proxy.systemId, 0);
                    if (used < Math.max(0, light.maxLights)) {
                        ParticleLightRuntime.refresh(level, proxy.spec, proxy.x, proxy.y, proxy.z);
                        lightsThisTick.put(proxy.systemId, used + 1);
                    }
                }
            }
        }
        BY_DIMENSION.values().removeIf(List::isEmpty);
    }

    public static List<Vec3> positions(String systemId) {
        List<Vec3> result = new ArrayList<>();
        for (List<Proxy> proxies : BY_DIMENSION.values()) {
            for (Proxy proxy : proxies) {
                if (systemId == null || systemId.isBlank() || proxy.systemId.equals(systemId)) {
                    result.add(new Vec3(proxy.x, proxy.y, proxy.z));
                }
            }
        }
        return result;
    }

    public static int count(String systemId) {
        int count = 0;
        for (List<Proxy> proxies : BY_DIMENSION.values()) {
            for (Proxy proxy : proxies) {
                if (systemId == null || systemId.isBlank() || proxy.systemId.equals(systemId)) count++;
            }
        }
        return count;
    }

    public static void clear(String systemId) {
        for (List<Proxy> proxies : BY_DIMENSION.values()) {
            proxies.removeIf(proxy -> systemId == null || systemId.isBlank() || proxy.systemId.equals(systemId));
        }
        BY_DIMENSION.values().removeIf(List::isEmpty);
    }

    private static void fire(String event, Proxy proxy, ServerPlayer player) {
        List<Object> payload = new ArrayList<>();
        payload.add(proxy.systemId);
        if (player != null) payload.add(player);
        payload.add(proxy.x);
        payload.add(proxy.y);
        payload.add(proxy.z);
        payload.add(proxy.spec.gameplay.tag == null ? "" : proxy.spec.gameplay.tag);
        ScriptManager.getInstance().onCustomEvent(event, payload);
    }

    private static final class Proxy {
        final String systemId;
        final ParticleSystemSpec spec;
        Set<UUID> overlapping;
        Set<UUID> nearbyPlayers;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        int age;
        int lifetime;

        Proxy(String systemId, ParticleSystemSpec spec, float[] data, int offset) {
            this.systemId = systemId;
            this.spec = spec;
            x = data[offset];
            y = data[offset + 1];
            z = data[offset + 2];
            vx = data[offset + 3];
            vy = data[offset + 4];
            vz = data[offset + 5];
            lifetime = Math.max(1, Math.round(Math.max(spec.initial.lifetimeMin, spec.initial.lifetimeMax) * 20f));
        }

        boolean tick(ServerLevel level) {
            if (++age >= lifetime) {
                if (overlapping != null) {
                    for (UUID uuid : overlapping) {
                        ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                        if (player != null) fire(EXIT, this, player);
                    }
                }
                return false;
            }
            ParticleSystemSpec.Motion motion = spec.motion;
            vx += motion.gravityX + motion.accelerationX / 20f;
            vy += motion.gravityY + motion.accelerationY / 20f;
            vz += motion.gravityZ + motion.accelerationZ / 20f;
            double drag = Mth.clamp(1.0 - motion.drag / 20.0, 0.0, 1.0);
            vx *= drag;
            vy *= drag;
            vz *= drag;
            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;
            float radius = Math.max(0.01f, spec.gameplay.radius);
            if (spec.gameplay.collideBlocks && !level.noCollision(new AABB(
                    nx - radius, ny - radius, nz - radius, nx + radius, ny + radius, nz + radius))) {
                fire(COLLISION, this, null);
                if (spec.collision.killOnCollision) return false;
                vx = -vx * spec.collision.bounce;
                vy = -vy * spec.collision.bounce;
                vz = -vz * spec.collision.bounce;
            } else {
                x = nx;
                y = ny;
                z = nz;
            }
            if (spec.gameplay.collideEntities) updatePlayers(level, radius);
            return true;
        }

        private void updatePlayers(ServerLevel level, float radius) {
            if (overlapping == null) {
                overlapping = new HashSet<>();
                nearbyPlayers = new HashSet<>();
            }
            nearbyPlayers.clear();
            AABB bounds = new AABB(x - radius, y - radius, z - radius,
                    x + radius, y + radius, z + radius);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds)) {
                nearbyPlayers.add(player.getUUID());
                if (overlapping.add(player.getUUID())) fire(ENTER, this, player);
            }
            Iterator<UUID> iterator = overlapping.iterator();
            while (iterator.hasNext()) {
                UUID uuid = iterator.next();
                if (nearbyPlayers.contains(uuid)) continue;
                iterator.remove();
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) fire(EXIT, this, player);
            }
        }
    }
}
