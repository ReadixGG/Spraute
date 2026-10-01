package org.zonarstudio.spraute_engine.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.network.ControlledParticleBurstPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.SprauteParticleBurstPacket;
import org.zonarstudio.spraute_engine.particle.ParticleSystemSpec;
import org.zonarstudio.spraute_engine.script.CinematicViewTracker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Shared particle-emitter shapes/motion for scene PARTICLE nodes (server + client preview).
 * <p>
 * Shapes control spawn positions: {@code point}, {@code line}, {@code sphere}, {@code spiral}.
 * Emit modes control velocity: {@code burst} (explosion all directions) or {@code directional} (cone).
 * Gravity is applied as Particle.gravity after launch — not baked into initial velocity.
 */
public final class SceneParticleEmitter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Scene specs are stable objects; avoid rebuilding the same large JSON every emission tick. */
    private static final Map<ParticleSystemSpec, String> SPEC_JSON =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int PLAYER_SPEC_CACHE_CAP = 64;
    private static final int SYSTEM_SPEC_CACHE_CAP = 256;
    /** Last spec delivered to each client; unchanged bursts use a one-byte marker. */
    private static final Map<UUID, Map<String, SentSpec>> SENT_SPECS =
            new LinkedHashMap<>(PLAYER_SPEC_CACHE_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Map<String, SentSpec>> eldest) {
                    return size() > PLAYER_SPEC_CACHE_CAP;
                }
            };

    private record SentSpec(String json, int bursts) {}

    private SceneParticleEmitter() {}

    public static ParticleOptions resolveType(String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = new ResourceLocation(id.contains(":") ? id.trim() : "minecraft:" + id.trim());
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        return type instanceof ParticleOptions options ? options : null;
    }

    public static String normalizeShape(String shape) {
        if (shape == null || shape.isBlank()) return "point";
        String s = shape.trim().toLowerCase();
        return switch (s) {
            case "line", "линия" -> "line";
            case "sphere", "ball", "шар" -> "sphere";
            case "spiral", "спираль" -> "spiral";
            default -> "point";
        };
    }

    /**
     * Full shape vocabulary for the custom engine ({@code ParticleSystemSpec.Shape.type}),
     * a superset of {@link #normalizeShape}, which only covers the legacy non-custom engine.
     */
    public static String normalizeCustomShape(String shape) {
        if (shape == null || shape.isBlank()) return "point";
        String s = shape.trim().toLowerCase();
        return switch (s) {
            case "line", "линия" -> "line";
            case "box", "куб", "коробка" -> "box";
            case "sphere", "ball", "шар" -> "sphere";
            case "shell", "оболочка" -> "shell";
            case "circle", "круг" -> "circle";
            case "disc", "disk", "диск" -> "disc";
            case "cone", "конус" -> "cone";
            case "cylinder", "цилиндр" -> "cylinder";
            case "spiral", "спираль" -> "spiral";
            case "text", "текст" -> "text";
            case "image", "picture", "картинка", "изображение" -> "image";
            default -> "point";
        };
    }

    public static String normalizeEmitMode(String mode) {
        if (mode == null || mode.isBlank()) return "burst";
        String s = mode.trim().toLowerCase();
        return switch (s) {
            case "directional", "direction", "dir", "cone", "направление", "луч" -> "directional";
            default -> "burst";
        };
    }

    /** Emit one burst at the given world origin (node transform already applied). */
    public static void emitServer(ServerLevel level, SceneNode node, SceneTransform world, RandomSource random) {
        if (level == null || node == null || world == null) return;
        ParticleSystemSpec spec = node.particles(false);
        if (spec != null && spec.custom) {
            emitCustomServer(level, node, world, random, Math.max(1, node.particleCount));
            return;
        }
        ParticleOptions opt = resolveType(node.particleType);
        if (opt == null) return;

        float[] data = buildBurst(node, world, random);
        if (data.length == 0) return;

        ControlledParticleBurstPacket packet = new ControlledParticleBurstPacket(
                node.particleType, Math.max(0f, node.particleGravity), Math.max(0, node.particleLifetime),
                node.particleCollide, Math.max(0, node.particleFade), data);
        deliverBurst(level, world.px, world.py, world.pz, packet, node.ownerPlayerUuid);
    }

    public static void emitCustomServer(ServerLevel level, SceneNode node, SceneTransform world,
                                        RandomSource random, int count) {
        emitCustomServer(level, node, world, random, count, 0L);
    }

    /** Same, but {@code startIndex} carries a running shape-sample offset — see {@link #buildCustomBurst}. */
    public static void emitCustomServer(ServerLevel level, SceneNode node, SceneTransform world,
                                        RandomSource random, int count, long startIndex) {
        ParticleSystemSpec spec = node == null ? null : node.particles(false);
        if (level == null || spec == null || !spec.custom || world == null || count <= 0) return;
        int spawn = Math.min(512, Math.max(1, count));
        float[] data = buildCustomBurst(spec, world, random, spawn, startIndex);
        if (data.length == 0) return;
        SprauteParticleBurstPacket packet = new SprauteParticleBurstPacket(
                node.id, specJson(spec), data);
        deliverCustomBurst(level, world.px, world.py, world.pz, packet, node.ownerPlayerUuid);
        org.zonarstudio.spraute_engine.particle.ParticleGameplayRuntime.spawn(
                level, node.id, spec, data);
    }

    /** Push a live module/texture patch without spawning another particle. */
    public static void syncCustomServer(ServerLevel level, SceneNode node, SceneTransform world) {
        ParticleSystemSpec spec = node == null ? null : node.particles(false);
        if (level == null || spec == null || !spec.custom || world == null) return;
        deliverCustomBurst(level, world.px, world.py, world.pz,
                new SprauteParticleBurstPacket(node.id, refreshSpecJson(spec), new float[0]), node.ownerPlayerUuid);
    }

    /** Script primitives (`particleLine` / `particleSpawn`) when the id is a texture, not a vanilla type. */
    public static void emitCustomPoints(ServerLevel level, String systemId, ParticleSystemSpec spec, float[] data) {
        if (level == null || spec == null || data == null || data.length < 6) return;
        spec.custom = true;
        double[] center = burstCentroid(data);
        deliverCustomBurst(level, center[0], center[1], center[2],
                new SprauteParticleBurstPacket(systemId == null ? "" : systemId, specJson(spec), data), null);
        // Same gameplay-proxy registration a node-based emitCustomServer() burst gets —
        // otherwise script-fired custom particles never get particleBirth/Death/Collision
        // events or overlap queries, unlike identical bursts placed as a scene node.
        org.zonarstudio.spraute_engine.particle.ParticleGameplayRuntime.spawn(level, systemId, spec, data);
    }

    /** Midpoint of burst positions — used for range culling so long lines are not dropped when only the far end is distant. */
    private static double[] burstCentroid(float[] data) {
        double minX = data[0], minY = data[1], minZ = data[2];
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (int i = 6; i + 2 < data.length; i += 6) {
            float x = data[i], y = data[i + 1], z = data[i + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return new double[]{(minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5};
    }

    public static void clearCustomServer(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return;
        org.zonarstudio.spraute_engine.particle.ParticleGameplayRuntime.clear(nodeId);
        synchronized (SENT_SPECS) {
            for (Map<String, SentSpec> specs : SENT_SPECS.values()) specs.remove(nodeId);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SprauteParticleBurstPacket(nodeId, "", new float[0]));
    }

    public static void clearCaches() {
        SPEC_JSON.clear();
        synchronized (SENT_SPECS) {
            SENT_SPECS.clear();
        }
    }

    /** Call after mutating a spec in the inspector so the next burst serializes current values. */
    public static void invalidateSpecJson(ParticleSystemSpec spec) {
        if (spec != null) SPEC_JSON.remove(spec);
    }

    public static void forgetPlayer(UUID playerId) {
        if (playerId == null) return;
        synchronized (SENT_SPECS) {
            SENT_SPECS.remove(playerId);
        }
    }

    /**
     * Deliver a burst to nearby players. During scripted cinematics the client camera
     * can be far from the player body — {@link PacketDistributor#NEAR} alone would drop
     * the packet for the spectator while the effect is on-screen.
     */
    private static void deliverBurst(ServerLevel level, double cx, double cy, double cz,
                                     ControlledParticleBurstPacket packet, String ownerPlayerUuid) {
        double rangeSq = 128.0 * 128.0;
        for (ServerPlayer player : level.players()) {
            if (SprauteEntityCompat.level(player) != level) continue;
            // Local World clone: burst only ever reaches its owner, never every nearby player.
            if (ownerPlayerUuid != null && !ownerPlayerUuid.equals(player.getUUID().toString())) continue;
            if (CinematicViewTracker.isActive(player)) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                continue;
            }
            double dx = player.getX() - cx;
            double dy = player.getY() - cy;
            double dz = player.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    private static void deliverCustomBurst(ServerLevel level, double cx, double cy, double cz,
                                           SprauteParticleBurstPacket packet, String ownerPlayerUuid) {
        double rangeSq = 256.0 * 256.0;
        for (ServerPlayer player : level.players()) {
            if (SprauteEntityCompat.level(player) != level) continue;
            // Local World clone: burst only ever reaches its owner, never every nearby player.
            if (ownerPlayerUuid != null && !ownerPlayerUuid.equals(player.getUUID().toString())) continue;
            double dx = player.getX() - cx;
            double dy = player.getY() - cy;
            double dz = player.getZ() - cz;
            if (CinematicViewTracker.isActive(player) || dx * dx + dy * dy + dz * dz <= rangeSq) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        compactForPlayer(player.getUUID(), packet));
            }
        }
    }

    private static SprauteParticleBurstPacket compactForPlayer(UUID playerId,
                                                                SprauteParticleBurstPacket packet) {
        if (packet.specJson == null || packet.specJson.isBlank() || "=".equals(packet.specJson)) return packet;
        synchronized (SENT_SPECS) {
            Map<String, SentSpec> specs = SENT_SPECS.computeIfAbsent(playerId,
                    ignored -> new LinkedHashMap<>(SYSTEM_SPEC_CACHE_CAP, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, SentSpec> eldest) {
                            return size() > SYSTEM_SPEC_CACHE_CAP;
                        }
                    });
            if (packet.data != null && packet.data.length >= 6) {
                specs.put(packet.systemId, new SentSpec(packet.specJson, 0));
                return packet;
            }
            SentSpec previous = specs.get(packet.systemId);
            if (previous != null && packet.specJson.equals(previous.json)) {
                specs.put(packet.systemId, new SentSpec(previous.json, previous.bursts + 1));
                return new SprauteParticleBurstPacket(packet.systemId, "=", packet.data);
            }
            specs.put(packet.systemId, new SentSpec(packet.specJson, 0));
        }
        return packet;
    }

    /**
     * Client-side emit for editor preview. Uses {@link net.minecraft.client.particle.ParticleEngine}.
     * Call only on the client thread.
     */
    public static void emitClient(net.minecraft.client.multiplayer.ClientLevel level,
                                  net.minecraft.client.particle.ParticleEngine engine,
                                  SceneNode node, SceneTransform world, RandomSource random) {
        emitClient(level, engine, node, world, random, Math.max(1, node.particleCount));
    }

    public static void emitClient(net.minecraft.client.multiplayer.ClientLevel level,
                                  net.minecraft.client.particle.ParticleEngine engine,
                                  SceneNode node, SceneTransform world, RandomSource random, int count) {
        emitClient(level, engine, node, world, random, count, 0L);
    }

    /** Same, but {@code startIndex} carries a running shape-sample offset — see {@link #buildCustomBurst}. */
    public static void emitClient(net.minecraft.client.multiplayer.ClientLevel level,
                                  net.minecraft.client.particle.ParticleEngine engine,
                                  SceneNode node, SceneTransform world, RandomSource random, int count,
                                  long startIndex) {
        if (level == null || engine == null || node == null || world == null || count <= 0) return;
        ParticleSystemSpec spec = node.particles(false);
        if (spec != null && spec.custom) {
            float[] data = buildCustomBurst(spec, world, random, Math.min(512, Math.max(1, count)), startIndex);
            org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.handle(
                    new SprauteParticleBurstPacket(node.id, specJson(spec), data));
            return;
        }
        ParticleOptions opt = resolveType(node.particleType);
        if (opt == null) return;

        float[] data = buildBurst(node, world, random);
        org.zonarstudio.spraute_engine.client.ClientParticleManager.spawnControlledBurst(
                engine, opt, Math.max(0f, node.particleGravity), Math.max(0, node.particleLifetime),
                node.particleCollide, Math.max(0, node.particleFade), data);
    }

    /** Build deterministic initial states for the custom engine. */
    public static float[] buildCustomBurst(ParticleSystemSpec spec, SceneTransform world,
                                           RandomSource random, int count) {
        return buildCustomBurst(spec, world, random, count, 0L);
    }

    /**
     * Same as {@link #buildCustomBurst(ParticleSystemSpec, SceneTransform, RandomSource, int)}, but
     * {@code startIndex} offsets the per-particle shape-sample index. A "text"/"image" shape (and
     * "line"/"spiral") pick their spawn point by index; emission normally spawns just 1-2 particles
     * per tick, so without a running offset every burst re-samples index 0 and every particle lands
     * on the exact same point — the shape never traces out. Callers that emit repeatedly for the same
     * node (the server tick runtime, the editor preview) should keep their own counter and advance it
     * by the returned particle count each call; one-off callers (scripts) can pass 0.
     */
    public static float[] buildCustomBurst(ParticleSystemSpec spec, SceneTransform world,
                                           RandomSource random, int count, long startIndex) {
        if (spec == null || world == null || random == null || count <= 0) return new float[0];
        ParticleSystemSpec.Shape shape = spec.shape == null ? new ParticleSystemSpec.Shape() : spec.shape;
        ParticleSystemSpec.Initial initial = spec.initial == null ? new ParticleSystemSpec.Initial() : spec.initial;
        double[] baseDir = localDirection(world, shape.dirX, shape.dirY, shape.dirZ);
        normalizeInPlace(baseDir);
        float[] out = new float[Math.min(4096, count) * 6];
        for (int i = 0, w = 0; w < out.length; i++) {
            double[] pos = customSpawnPosition(shape, world, random, startIndex + i, out.length / 6);
            pos[0] += symmetric(random) * Math.max(0f, shape.spreadX);
            pos[1] += symmetric(random) * Math.max(0f, shape.spreadY);
            pos[2] += symmetric(random) * Math.max(0f, shape.spreadZ);
            float speed = randomRange(random, Math.max(0f, initial.speedMin), Math.max(0f, initial.speedMax));
            double[] velocity = sampleCone(baseDir, Math.max(0f, shape.angle), random);
            if ("sphere".equalsIgnoreCase(shape.type) || "shell".equalsIgnoreCase(shape.type)) {
                velocity = new double[]{pos[0] - world.px, pos[1] - world.py, pos[2] - world.pz};
                normalizeInPlace(velocity);
            }
            out[w++] = (float) pos[0];
            out[w++] = (float) pos[1];
            out[w++] = (float) pos[2];
            out[w++] = (float) (velocity[0] * speed);
            out[w++] = (float) (velocity[1] * speed);
            out[w++] = (float) (velocity[2] * speed);
        }
        return out;
    }

    private static double[] customSpawnPosition(ParticleSystemSpec.Shape shape, SceneTransform world,
                                                RandomSource random, long index, int count) {
        int i = (int) Math.floorMod(index, Integer.MAX_VALUE);
        String kind = shape.type == null ? "point" : shape.type.trim().toLowerCase();
        return switch (kind) {
            case "line" -> {
                double t = count <= 1 ? 0 : (double) i / (count - 1);
                yield localOffset(world, shape.lineX * (float) t,
                        shape.lineY * (float) t, shape.lineZ * (float) t);
            }
            case "box" -> {
                double x = symmetric(random) * shape.sizeX * 0.5;
                double y = symmetric(random) * shape.sizeY * 0.5;
                double z = symmetric(random) * shape.sizeZ * 0.5;
                if (shape.surfaceOnly) {
                    int face = random.nextInt(3);
                    if (face == 0) x = Math.copySign(shape.sizeX * 0.5, x);
                    if (face == 1) y = Math.copySign(shape.sizeY * 0.5, y);
                    if (face == 2) z = Math.copySign(shape.sizeZ * 0.5, z);
                }
                yield localOffset(world, (float) x, (float) y, (float) z);
            }
            case "sphere", "shell" -> {
                double radius = Math.max(0f, shape.radius);
                if (!shape.surfaceOnly && !"shell".equals(kind)) radius *= Math.cbrt(random.nextDouble());
                double[] p = randomOnSphere(random, radius);
                yield localOffset(world, (float) p[0], (float) p[1], (float) p[2]);
            }
            case "circle", "disc" -> {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = Math.max(0f, shape.radius);
                if (!shape.surfaceOnly && "disc".equals(kind)) radius *= Math.sqrt(random.nextDouble());
                yield localOffset(world, (float) (Math.cos(angle) * radius), 0f,
                        (float) (Math.sin(angle) * radius));
            }
            case "cone" -> {
                double h = random.nextDouble() * Math.max(0f, shape.length);
                double radius = Math.tan(Math.toRadians(Math.max(0f, shape.angle))) * h;
                double angle = random.nextDouble() * Math.PI * 2;
                if (!shape.surfaceOnly) radius *= Math.sqrt(random.nextDouble());
                yield localOffset(world, (float) (Math.cos(angle) * radius), (float) h,
                        (float) (Math.sin(angle) * radius));
            }
            case "cylinder" -> {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = Math.max(0f, shape.radius);
                if (!shape.surfaceOnly) radius *= Math.sqrt(random.nextDouble());
                yield localOffset(world, (float) (Math.cos(angle) * radius),
                        (float) (random.nextDouble() * shape.height), (float) (Math.sin(angle) * radius));
            }
            case "spiral" -> {
                double t = count <= 1 ? 0 : (double) i / (count - 1);
                double angle = t * Math.PI * 4;
                yield localOffset(world, (float) (Math.cos(angle) * shape.radius),
                        (float) (t * shape.height), (float) (Math.sin(angle) * shape.radius));
            }
            case "text" -> {
                double[][] points = org.zonarstudio.spraute_engine.particle.TextureShapeSampler
                        .sampleText(shape.text, shape.sampleResolution, shape.sampleThreshold);
                double[] p = org.zonarstudio.spraute_engine.particle.TextureShapeSampler.pointAt(points, i);
                yield localOffset(world, (float) (p[0] * Math.max(0.01f, shape.sizeX)),
                        (float) (p[1] * Math.max(0.01f, shape.sizeY)), 0f);
            }
            case "image" -> {
                double[][] points = org.zonarstudio.spraute_engine.particle.TextureShapeSampler
                        .sampleImage(shape.image, shape.sampleResolution, shape.sampleThreshold);
                double[] p = org.zonarstudio.spraute_engine.particle.TextureShapeSampler.pointAt(points, i);
                yield localOffset(world, (float) (p[0] * Math.max(0.01f, shape.sizeX)),
                        (float) (p[1] * Math.max(0.01f, shape.sizeY)), 0f);
            }
            default -> new double[]{world.px, world.py, world.pz};
        };
    }

    private static double symmetric(RandomSource random) {
        return random.nextDouble() * 2.0 - 1.0;
    }

    private static String specJson(ParticleSystemSpec spec) {
        String cached = SPEC_JSON.get(spec);
        if (cached != null) return cached;
        String json = GSON.toJson(spec);
        SPEC_JSON.put(spec, json);
        return json;
    }

    private static String refreshSpecJson(ParticleSystemSpec spec) {
        String json = GSON.toJson(spec);
        SPEC_JSON.put(spec, json);
        return json;
    }

    private static float randomRange(RandomSource random, float min, float max) {
        if (max < min) {
            float swap = min;
            min = max;
            max = swap;
        }
        return min + random.nextFloat() * (max - min);
    }

    /** Build interleaved [x,y,z,vx,vy,vz] * count. */
    private static float[] buildBurst(SceneNode node, SceneTransform world, RandomSource random) {
        int count = Math.max(1, node.particleCount);
        String shape = normalizeShape(node.particleShape);
        String mode = normalizeEmitMode(node.particleEmitMode);
        float speed = Math.max(0f, node.particleSpeed);
        float speedSpread = Math.max(0f, node.particleSpeedSpread);
        float sx = Math.max(0f, node.particleSpreadX);
        float sy = Math.max(0f, node.particleSpreadY);
        float sz = Math.max(0f, node.particleSpreadZ);
        float cone = Math.max(0f, node.particleCone);

        double[] baseDir = localDirection(world, node.particleDirX, node.particleDirY, node.particleDirZ);
        normalizeInPlace(baseDir);

        float[] out = new float[count * 6];
        int w = 0;
        for (int i = 0; i < count; i++) {
            double[] pos = spawnPosition(shape, node, world, random, i, count);
            pos[0] += (random.nextDouble() * 2 - 1) * sx;
            pos[1] += (random.nextDouble() * 2 - 1) * sy;
            pos[2] += (random.nextDouble() * 2 - 1) * sz;

            float particleSpeed = speed;
            if (speedSpread > 0f && speed > 0f) {
                float factor = 1f + (float) ((random.nextDouble() * 2 - 1) * speedSpread);
                particleSpeed = Math.max(0f, speed * factor);
            }
            double[] vel = velocityFor(mode, shape, world, pos, baseDir, particleSpeed, cone, random);
            out[w++] = (float) pos[0];
            out[w++] = (float) pos[1];
            out[w++] = (float) pos[2];
            out[w++] = (float) vel[0];
            out[w++] = (float) vel[1];
            out[w++] = (float) vel[2];
        }
        return out;
    }

    private static double[] spawnPosition(String shape, SceneNode node, SceneTransform world,
                                          RandomSource random, int i, int count) {
        return switch (shape) {
            case "line" -> {
                double[] end = localOffset(world, node.particleLineX, node.particleLineY, node.particleLineZ);
                double t = count <= 1 ? 0 : (double) i / (count - 1);
                yield new double[]{
                        world.px + (end[0] - world.px) * t,
                        world.py + (end[1] - world.py) * t,
                        world.pz + (end[2] - world.pz) * t
                };
            }
            case "sphere" -> {
                double r = Math.max(0.05, node.particleRadius);
                double[] p = randomOnSphere(random, r);
                yield new double[]{world.px + p[0], world.py + p[1], world.pz + p[2]};
            }
            case "spiral" -> {
                double r = Math.max(0.05, node.particleRadius);
                double h = node.particleHeight;
                double t = count <= 1 ? 0 : (double) i / (count - 1);
                double angle = 4 * Math.PI * t;
                yield localOffset(world,
                        (float) (r * Math.cos(angle)), (float) (h * t), (float) (r * Math.sin(angle)));
            }
            default -> new double[]{world.px, world.py, world.pz};
        };
    }

    private static double[] velocityFor(String mode, String shape, SceneTransform world, double[] pos,
                                        double[] baseDir, float speed, float coneDeg, RandomSource random) {
        if (speed <= 0f) return new double[]{0, 0, 0};

        double[] dir;
        if ("directional".equals(mode)) {
            dir = sampleCone(baseDir, coneDeg, random);
        } else {
            // Burst / explosion: outward. For sphere spawn, prefer radial from origin.
            if ("sphere".equals(shape)) {
                dir = new double[]{pos[0] - world.px, pos[1] - world.py, pos[2] - world.pz};
                if (lengthSq(dir) < 1.0e-8) dir = randomOnSphere(random, 1.0);
            } else {
                dir = randomOnSphere(random, 1.0);
            }
            normalizeInPlace(dir);
            // Soft randomness so burst isn't perfectly spherical when cone used as jitter
            if (coneDeg > 0.5f) {
                dir = sampleCone(dir, Math.min(coneDeg, 45f), random);
            }
        }
        return new double[]{dir[0] * speed, dir[1] * speed, dir[2] * speed};
    }

    /** Uniform direction inside a cone around {@code axis} (degrees half-angle). */
    private static double[] sampleCone(double[] axis, float coneDeg, RandomSource random) {
        double[] a = new double[]{axis[0], axis[1], axis[2]};
        normalizeInPlace(a);
        if (coneDeg <= 0.01f) return a;

        double maxRad = Math.toRadians(coneDeg);
        double cosMax = Math.cos(maxRad);
        double z = cosMax + (1.0 - cosMax) * random.nextDouble();
        double sinT = Math.sqrt(Math.max(0, 1.0 - z * z));
        double phi = random.nextDouble() * Math.PI * 2.0;
        double x = sinT * Math.cos(phi);
        double y = sinT * Math.sin(phi);

        // Orthonormal basis with `a` as Z
        double[] tmp = Math.abs(a[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = cross(tmp, a);
        normalizeInPlace(u);
        double[] v = cross(a, u);

        return new double[]{
                u[0] * x + v[0] * y + a[0] * z,
                u[1] * x + v[1] * y + a[1] * z,
                u[2] * x + v[2] * y + a[2] * z
        };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double lengthSq(double[] v) {
        return v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
    }

    private static void normalizeInPlace(double[] v) {
        double len = Math.sqrt(lengthSq(v));
        if (len < 1.0e-8) {
            v[0] = 0;
            v[1] = 1;
            v[2] = 0;
            return;
        }
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    /** Apply yaw/pitch/roll of world transform to a local offset (degrees). */
    public static double[] localOffset(SceneTransform world, float lx, float ly, float lz) {
        double[] d = rotateLocal(world, lx, ly, lz);
        return new double[]{world.px + d[0] * world.sx, world.py + d[1] * world.sy, world.pz + d[2] * world.sz};
    }

    /** Rotate a local direction by node orientation (ignores scale/position). */
    public static double[] localDirection(SceneTransform world, float lx, float ly, float lz) {
        return rotateLocal(world, lx, ly, lz);
    }

    private static double[] rotateLocal(SceneTransform world, float lx, float ly, float lz) {
        double yaw = Math.toRadians(world.ry);
        double pitch = Math.toRadians(world.rx);
        double roll = Math.toRadians(world.rz);
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double cosR = Math.cos(roll), sinR = Math.sin(roll);
        double x1 = lx * cosR - ly * sinR;
        double y1 = lx * sinR + ly * cosR;
        double z1 = lz;
        double y2 = y1 * cosP - z1 * sinP;
        double z2 = y1 * sinP + z1 * cosP;
        double x3 = x1 * cosY + z2 * sinY;
        double z3 = -x1 * sinY + z2 * cosY;
        return new double[]{x3, y2, z3};
    }

    private static double[] randomOnSphere(RandomSource random, double radius) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        double theta = 2 * Math.PI * u;
        double phi = Math.acos(2 * v - 1);
        double sinP = Math.sin(phi);
        return new double[]{
                radius * sinP * Math.cos(theta),
                radius * Math.cos(phi),
                radius * sinP * Math.sin(theta)
        };
    }
}
