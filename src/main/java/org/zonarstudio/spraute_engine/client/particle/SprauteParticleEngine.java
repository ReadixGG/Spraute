package org.zonarstudio.spraute_engine.client.particle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.network.SprauteParticleBurstPacket;
import org.zonarstudio.spraute_engine.particle.ParticleSystemSpec;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.client.SpGeoRenderer;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
//? if >=1.20.1 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
*///?}

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Spraute-owned particle simulation and render pass. It never creates a
 * ParticleType and never delegates visual particles to Minecraft.particleEngine.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class SprauteParticleEngine {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    /**
     * Particles born while {@link #PARTICLES} is being iterated (a death/collision sub-emitter
     * fires from inside {@code particle.tick()}). Adding straight to {@code PARTICLES} there
     * invalidates the live iterator — {@code ConcurrentModificationException} on the next
     * {@code remove()} — so they wait here and are merged in once the walk is finished.
     */
    private static final List<Particle> PENDING = new ArrayList<>();
    private static boolean iterating;
    private static final ArrayDeque<Particle> POOL = new ArrayDeque<>();
    /**
     * No fixed particle-count ceiling here anymore — how many particles a system may have alive
     * at once is whatever its own "Лимит системы" (emission.maxParticles) says, full stop.
     * A silent invisible cap used to also drop particles past 4096/system or 8192 overall,
     * regardless of what was configured, which looked like "more than ~4200 just doesn't draw".
     */
    private static final int POOL_CAP = 2048;
    private static final int SPEC_CACHE_CAP = 256;
    private static final int RENDER_TYPE_CACHE_CAP = 128;
    private static final Map<String, CachedSpec> SPEC_CACHE =
            new LinkedHashMap<>(SPEC_CACHE_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedSpec> eldest) {
                    return size() > SPEC_CACHE_CAP;
                }
            };
    private static final Map<String, RenderType> RENDER_TYPES =
            new LinkedHashMap<>(RENDER_TYPE_CACHE_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RenderType> eldest) {
                    return size() > RENDER_TYPE_CACHE_CAP;
                }
            };
    private static Level levelIdentity;
    private static long spawned;
    private static long dropped;

    private SprauteParticleEngine() {}

    public record Stats(int alive, long spawned, long dropped) {}
    private record CachedSpec(String json, ParticleSystemSpec spec) {}

    public static Stats stats() {
        return new Stats(PARTICLES.size() + PENDING.size(), spawned, dropped);
    }

    public static int count(String systemId) {
        int result = 0;
        for (Particle particle : PARTICLES) {
            if (particle.systemId.equals(systemId)) result++;
        }
        for (Particle particle : PENDING) {
            if (particle.systemId.equals(systemId)) result++;
        }
        return result;
    }

    /** Live particles including the ones queued during this tick's walk (cap accounting). */
    private static int liveCount() {
        return PARTICLES.size() + PENDING.size();
    }

    /** Adds a newborn particle, deferring it when {@link #PARTICLES} is mid-iteration. */
    private static void addParticle(Particle particle) {
        if (iterating) PENDING.add(particle);
        else PARTICLES.add(particle);
    }

    public static AABB bounds(String systemId) {
        AABB result = null;
        for (Particle particle : PARTICLES) {
            if (!particle.systemId.equals(systemId)) continue;
            double r = Math.max(0.01, particle.size);
            AABB box = new AABB(particle.x - r, particle.y - r, particle.z - r,
                    particle.x + r, particle.y + r, particle.z + r);
            result = result == null ? box : result.minmax(box);
        }
        return result;
    }

    public static void handle(SprauteParticleBurstPacket packet) {
        if (packet.specJson == null || packet.specJson.isBlank()) {
            clear(packet.systemId);
            return;
        }
        CachedSpec cached = SPEC_CACHE.get(packet.systemId);
        if ("=".equals(packet.specJson)) {
            if (cached == null) return;
            spawn(packet, cached.spec);
            return;
        }
        ParticleSystemSpec spec;
        boolean changed = cached == null || !packet.specJson.equals(cached.json);
        if (changed) {
            try {
                spec = GSON.fromJson(packet.specJson, ParticleSystemSpec.class);
            } catch (RuntimeException e) {
                return;
            }
            if (spec == null || !spec.custom || spec.renderer == null || spec.initial == null) return;
            ensureModules(spec);
            SPEC_CACHE.put(packet.systemId, new CachedSpec(packet.specJson, spec));
            update(packet.systemId, spec);
        } else {
            spec = cached.spec;
        }
        spawn(packet, spec);
    }

    private static void spawn(SprauteParticleBurstPacket packet, ParticleSystemSpec spec) {
        int current = count(packet.systemId);
        int perSystem = Math.max(1, spec.emission.maxParticles);
        Random random = new Random(spec.emission.seed == 0
                ? packet.systemId.hashCode() ^ System.nanoTime()
                : spec.emission.seed ^ spawned);
        for (int i = 0; i + 5 < packet.data.length; i += 6) {
            if (current >= perSystem) {
                dropped++;
                continue;
            }
            Particle particle = POOL.pollFirst();
            if (particle == null) particle = new Particle();
            particle.init(packet.systemId, spec, random,
                    packet.data[i], packet.data[i + 1], packet.data[i + 2],
                    packet.data[i + 3], packet.data[i + 4], packet.data[i + 5]);
            addParticle(particle);
            current++;
            spawned++;
            fireSubEmitters(particle, "birth", 0);
        }
    }

    private static final int MAX_SUB_EMITTER_DEPTH = 3;

    /** Sub-emitters declared on {@code p.spec}, triggered by one of this particle's lifecycle events. */
    private static void fireSubEmitters(Particle p, String trigger, int depth) {
        if (p == null || p.spec == null || p.spec.subEmitters == null || p.spec.subEmitters.isEmpty()) return;
        fireSubEmitters(p.spec.subEmitters, p.x, p.y, p.z, p.vx, p.vy, p.vz, p.systemId, trigger, depth);
    }

    private static void fireSubEmitters(List<ParticleSystemSpec.SubEmitter> subEmitters, double x, double y, double z,
                                        double parentVx, double parentVy, double parentVz,
                                        String parentSystemId, String trigger, int depth) {
        if (subEmitters == null || subEmitters.isEmpty() || depth >= MAX_SUB_EMITTER_DEPTH) return;
        Random random = new Random();
        for (ParticleSystemSpec.SubEmitter sub : subEmitters) {
            if (sub == null || sub.spec == null || !trigger.equals(sub.trigger)) continue;
            if (sub.probability < 1f && random.nextFloat() > Mth.clamp(sub.probability, 0f, 1f)) continue;

            ParticleSystemSpec childSpec = sub.spec;
            ensureModules(childSpec);
            String childSystemId = parentSystemId + ":sub:" + System.identityHashCode(sub);
            CachedSpec cachedChild = SPEC_CACHE.get(childSystemId);
            if (cachedChild == null) {
                SPEC_CACHE.put(childSystemId, new CachedSpec(GSON.toJson(childSpec), childSpec));
            }

            int perSystem = Math.max(1, childSpec.emission.maxParticles);
            int currentCount = count(childSystemId);
            int wantedCount = Math.max(0, sub.count);
            float inherit = Mth.clamp(sub.inheritVelocity, 0f, 1f);
            for (int i = 0; i < wantedCount; i++) {
                if (currentCount >= perSystem) {
                    dropped++;
                    continue;
                }
                Particle particle = POOL.pollFirst();
                if (particle == null) particle = new Particle();
                float vx = (float) (parentVx * inherit);
                float vy = (float) (parentVy * inherit);
                float vz = (float) (parentVz * inherit);
                particle.init(childSystemId, childSpec, random, (float) x, (float) y, (float) z, vx, vy, vz);
                addParticle(particle);
                currentCount++;
                spawned++;
                fireSubEmitters(particle, "birth", depth + 1);
            }
        }
    }

    public static void clear(String systemId) {
        boolean all = systemId == null || systemId.isBlank();
        clearFrom(PARTICLES, systemId, all);
        clearFrom(PENDING, systemId, all);
        if (all) SPEC_CACHE.clear();
        else SPEC_CACHE.remove(systemId);
    }

    private static void clearFrom(List<Particle> list, String systemId, boolean all) {
        Iterator<Particle> iterator = list.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            if (all || particle.systemId.equals(systemId)) {
                iterator.remove();
                recycle(particle);
            }
        }
    }

    public static void update(String systemId, ParticleSystemSpec spec) {
        if (systemId == null || spec == null) return;
        ensureModules(spec);
        CachedSpec cached = SPEC_CACHE.get(systemId);
        SPEC_CACHE.put(systemId, new CachedSpec(cached != null ? cached.json : GSON.toJson(spec), spec));
        for (Particle particle : PARTICLES) {
            if (particle.systemId.equals(systemId)) particle.applySpec(spec);
        }
        for (Particle particle : PENDING) {
            if (particle.systemId.equals(systemId)) particle.applySpec(spec);
        }
    }

    /* Compatibility hooks for the abandoned pre-inline instance prototype. */
    ParticleInstance obtain() {
        return new ParticleInstance();
    }

    void recycle(ParticleInstance particle) {
        if (particle != null) particle.reset();
    }

    void onBirth(ParticleSystemInstance system, ParticleInstance particle) {
    }

    void onDeath(ParticleSystemInstance system, ParticleInstance particle) {
    }

    void onCollision(ParticleSystemInstance system, ParticleInstance particle) {
    }

    void addDropped(int count) {
        dropped += Math.max(0, count);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearParticles();
        SPEC_CACHE.clear();
        RENDER_TYPES.clear();
        levelIdentity = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) {
            clearParticles();
            RENDER_TYPES.clear();
            levelIdentity = mc.level;
        }
        if (mc.isPaused() || mc.level == null) return;
        // particle.tick() can fire death/collision sub-emitters, which spawn new particles.
        // Those go to PENDING while this walk runs and are merged in right after it.
        iterating = true;
        try {
            Iterator<Particle> iterator = PARTICLES.iterator();
            while (iterator.hasNext()) {
                Particle particle = iterator.next();
                if (!particle.tick(mc.level)) {
                    iterator.remove();
                    recycle(particle);
                }
            }
        } finally {
            iterating = false;
        }
        if (!PENDING.isEmpty()) {
            PARTICLES.addAll(PENDING);
            PENDING.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        //? if >=1.20.1 {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        //?} else {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        if (PARTICLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();

        for (Particle particle : PARTICLES) {
            particle.render(pose, buffers, camera, partial, mc);
        }
        buffers.endBatch();
    }

    private static void ensureModules(ParticleSystemSpec spec) {
        spec.harden();
    }

    private static void recycle(Particle particle) {
        particle.reset();
        if (POOL.size() < POOL_CAP) POOL.addLast(particle);
    }

    private static void clearParticles() {
        for (Particle particle : PARTICLES) recycle(particle);
        PARTICLES.clear();
        for (Particle particle : PENDING) recycle(particle);
        PENDING.clear();
    }

    private static final class Particle {
        String systemId;
        ParticleSystemSpec spec;
        float lifetime;
        float baseSize;
        float aspect;
        int colorA;
        int lifeColorStart;
        int lifeColorEnd;
        int startFrame;
        ArrayDeque<Vec3> trail;
        RenderType renderType;
        String renderMode;
        SpModelInstance modelInstance;
        SpGeoModel cachedModel;
        double xo;
        double yo;
        double zo;
        double x;
        double y;
        double z;
        double originX;
        double originY;
        double originZ;
        double vx;
        double vy;
        double vz;
        float age;
        float size;
        float rotation;
        float previousRotation;
        float angularVelocity;

        void init(String systemId, ParticleSystemSpec spec, Random random,
                  float x, float y, float z, float vx, float vy, float vz) {
            this.systemId = systemId;
            this.spec = spec;
            this.age = 0f;
            this.x = this.xo = x;
            this.y = this.yo = y;
            this.z = this.zo = z;
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.lifetime = Math.max(1f,
                    randomRange(random, spec.initial.lifetimeMin, spec.initial.lifetimeMax) * 20f);
            this.baseSize = randomRange(random, spec.initial.sizeMin, spec.initial.sizeMax);
            this.aspect = Math.max(0.01f, spec.initial.aspect);
            this.rotation = this.previousRotation =
                    randomRange(random, spec.initial.rotationMin, spec.initial.rotationMax);
            this.angularVelocity = randomRange(random,
                    spec.motion.angularVelocityMin, spec.motion.angularVelocityMax);
            this.colorA = randomColor(random, spec.initial.colorMin, spec.initial.colorMax);
            this.lifeColorEnd = randomColor(random,
                    spec.lifetime.colorEndMin, spec.lifetime.colorEndMax);
            int frames = Math.max(1, spec.renderer.sheetColumns) * Math.max(1, spec.renderer.sheetRows);
            this.startFrame = spec.renderer.randomStartFrame ? random.nextInt(frames) : 0;
            this.size = baseSize;
            applySpec(spec);
        }

        void applySpec(ParticleSystemSpec spec) {
            this.spec = spec;
            this.lifeColorStart = colorA;
            this.renderMode = normalized(spec.renderer.mode);
            this.renderType = renderType(spec);
        }

        void reset() {
            systemId = null;
            spec = null;
            renderType = null;
            renderMode = null;
            modelInstance = null;
            cachedModel = null;
            age = 0f;
            lifetime = 0f;
            if (trail != null) {
                trail.clear();
                trail = null;
            }
        }

        boolean tick(Level level) {
            if (age > Math.max(1f, lifetime)) {
                fireSubEmitters(this, "death", 0);
                return false;
            }
            xo = x;
            yo = y;
            zo = z;
            previousRotation = rotation;
            age += 1f;

            float dt = 1f / 20f;
            ParticleSystemSpec.Motion motion = spec.motion;
            vx += motion.accelerationX * dt + motion.gravityX;
            vy += motion.accelerationY * dt + motion.gravityY;
            vz += motion.accelerationZ * dt + motion.gravityZ;
            if (motion.noiseStrength != 0f) {
                double phase = (age + systemId.hashCode() * 0.01) * motion.noiseFrequency * 0.13;
                vx += Math.sin(phase * 1.7) * motion.noiseStrength * dt;
                vy += Math.cos(phase * 1.3) * motion.noiseStrength * dt;
                vz += Math.sin(phase * 0.9 + 2.1) * motion.noiseStrength * dt;
            }
            if (motion.vortexStrength != 0f) {
                double oldX = vx;
                vx += -vz * motion.vortexStrength * dt;
                vz += oldX * motion.vortexStrength * dt;
            }
            if (motion.attractorStrength != 0f) {
                double[] target = resolveAttractorTarget(motion.attractorTarget, originX, originY, originZ);
                double dx = target[0] - x;
                double dy = target[1] - y;
                double dz = target[2] - z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 1.0e-4) {
                    double pull = motion.attractorStrength * dt / dist;
                    vx += dx * pull;
                    vy += dy * pull;
                    vz += dz * pull;
                }
            }
            double drag = Mth.clamp(1.0 - motion.drag * dt, 0.0, 1.0);
            vx *= drag;
            vy *= drag;
            vz *= drag;
            if (motion.maxSpeed > 0f) {
                double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (speed > motion.maxSpeed) {
                    double scale = motion.maxSpeed / speed;
                    vx *= scale;
                    vy *= scale;
                    vz *= scale;
                }
            }

            float lifeT = Mth.clamp(age / Math.max(1f, lifetime), 0f, 1f);
            float speedMul = Mth.lerp(curve(spec.lifetime.speedCurve, lifeT),
                    spec.lifetime.speedStart, spec.lifetime.speedEnd);
            double nx = x + vx * speedMul;
            double ny = y + vy * speedMul;
            double nz = z + vz * speedMul;
            if (spec.collision.enabled && spec.collision.blocks) {
                float radius = Math.max(0.001f, spec.collision.radius);
                AABB nextBox = new AABB(nx - radius, ny - radius, nz - radius,
                        nx + radius, ny + radius, nz + radius);
                if (!level.noCollision(nextBox)) {
                    fireSubEmitters(this, "collision", 0);
                    if (spec.collision.killOnCollision) {
                        fireSubEmitters(this, "death", 0);
                        return false;
                    }
                    double friction = Mth.clamp(1f - spec.collision.friction, 0f, 1f);
                    vx = -vx * spec.collision.bounce * friction;
                    vy = -vy * spec.collision.bounce;
                    vz = -vz * spec.collision.bounce * friction;
                    nx = x + vx;
                    ny = y + vy;
                    nz = z + vz;
                }
            }
            x = nx;
            y = ny;
            z = nz;
            rotation += angularVelocity * dt;
            size = baseSize * Mth.lerp(curve(spec.lifetime.sizeCurve, lifeT),
                    spec.lifetime.sizeStart, spec.lifetime.sizeEnd);

            if (spec.trail.enabled) {
                if (trail == null) trail = new ArrayDeque<>();
                trail.addLast(new Vec3(x, y, z));
                int maxTrailPoints = Mth.clamp(spec.trail.maxPoints, 2, 64);
                while (trail.size() > maxTrailPoints) trail.removeFirst();
            }
            return true;
        }

        void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                    float partial, Minecraft mc) {
            double rx = Mth.lerp(partial, xo, x);
            double ry = Mth.lerp(partial, yo, y);
            double rz = Mth.lerp(partial, zo, z);
            if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(rx, ry, rz) > 256d * 256d) return;

            VertexConsumer consumer = buffers.getBuffer(renderType);
            float lifeT = Mth.clamp((age + partial) / Math.max(1f, lifetime), 0f, 1f);
            int color = lerpColor(lifeColorStart, lifeColorEnd, curve(spec.lifetime.colorCurve, lifeT));
            if (spec.renderer.glowStrength != 1f) {
                color = applyGlow(color, spec.renderer.glowStrength);
            }
            int light = spec.renderer.fullbright
                    ? LightTexture.FULL_BRIGHT
                    : LevelRenderer.getLightColor(mc.level,
                            new BlockPos(Mth.floor(rx), Mth.floor(ry), Mth.floor(rz)));

            pose.pushPose();
            pose.translate(rx - camera.x, ry - camera.y, rz - camera.z);
            String mode = renderMode;
            if ("model".equals(mode)) {
                renderModel(pose, consumer, light, color, partial);
                pose.popPose();
                if (spec.trail.enabled && trail != null && trail.size() > 1) {
                    renderTrail(pose, consumer, camera, color, light);
                }
                return;
            }
            if ("velocity".equals(mode) || "axis".equals(mode)) {
                float yaw = (float) (Mth.atan2(vx, vz) * 180f / Math.PI);
                float pitch = (float) (Mth.atan2(vy, Math.sqrt(vx * vx + vz * vz)) * 180f / Math.PI);
                SprauteRenderCompat.rotateY(pose, yaw);
                SprauteRenderCompat.rotateX(pose, -pitch);
            } else {
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                SprauteRenderCompat.rotateY(pose, 180f);
            }
            SprauteRenderCompat.rotateZ(pose, Mth.lerp(partial, previousRotation, rotation));
            float width = Math.max(0.001f, size);
            float height = width * aspect;
            if ("stretched".equals(mode)) {
                height *= 1f + Math.sqrt(vx * vx + vy * vy + vz * vz)
                        * Math.max(0f, spec.renderer.stretch) * 10f;
            }
            drawQuad(pose, consumer, width, height, color, light, frame(age + partial),
                    Math.max(1, spec.renderer.sheetColumns), Math.max(1, spec.renderer.sheetRows),
                    spec.renderer.softEdge);
            if ("cross".equals(mode) || "mesh".equals(mode)) {
                SprauteRenderCompat.rotateY(pose, 90f);
                drawQuad(pose, consumer, width, height, color, light, frame(age + partial),
                        Math.max(1, spec.renderer.sheetColumns), Math.max(1, spec.renderer.sheetRows),
                        spec.renderer.softEdge);
            }
            pose.popPose();

            if (spec.trail.enabled && trail != null && trail.size() > 1) {
                renderTrail(pose, consumer, camera, color, light);
            }
        }

        /**
         * {@code renderer.mode == "model"}: draws {@code renderer.model} (a workspace .geo model,
         * same format/loader as an NPC's model) instead of a billboard quad — a real 3D mesh
         * particle. Unlike the billboard modes above, this does NOT face the camera; only the
         * particle's own spin ({@code rotation}, driven by {@code angularVelocity} as usual) is
         * applied, since a 3D mesh reads as a solid object tumbling in space, not a flat sprite.
         * {@code size} scales the model uniformly (1 = the model's own authored scale).
         */
        private void renderModel(PoseStack pose, VertexConsumer consumer, int light, int color, float partial) {
            // Model mode's texture is a full skin, not a particle sprite — the billboard
            // fallback (a tiny particle dot) would just smear across the whole mesh's UVs, so
            // an unset texture draws nothing instead of that wrong-looking result.
            if (spec.renderer.texture == null || spec.renderer.texture.isBlank()) return;
            SpGeoModel model = SpModelCache.getOrLoad(spec.renderer.model);
            if (model.boneMap.isEmpty()) return;
            if (modelInstance == null || cachedModel != model) {
                modelInstance = new SpModelInstance(model);
                cachedModel = model;
                modelInstance.resetAnims();
                modelInstance.computeTransforms();
            }
            SprauteRenderCompat.rotateY(pose, Mth.lerp(partial, previousRotation, rotation));
            float scale = Math.max(0.001f, size);
            pose.scale(scale, scale, scale);
            int r = color >> 24 & 255;
            int g = color >> 16 & 255;
            int b = color >> 8 & 255;
            int a = color & 255;
            SpGeoRenderer.render(modelInstance, pose, consumer, light, OverlayTexture.NO_OVERLAY,
                    r / 255f, g / 255f, b / 255f, a / 255f, null);
        }

        private int frame(float renderAge) {
            int frames = Math.max(1, spec.renderer.sheetColumns) * Math.max(1, spec.renderer.sheetRows);
            if (frames == 1) return 0;
            int frame = startFrame + (int) (renderAge / 20f * Math.max(0f, spec.renderer.sheetFps));
            return spec.renderer.sheetLoop ? Math.floorMod(frame, frames) : Math.min(frames - 1, frame);
        }

        /**
         * A camera-facing textured ribbon strip along the particle's recent positions —
         * reuses the particle's own sprite (billboard render type), stretched along the
         * trail's length (u: 0 at the oldest point, 1 at the particle itself) so a soft
         * round sprite reads as a fading streak. Width tapers {@code widthEnd} (oldest) →
         * {@code widthStart} (at the particle), and alpha fades out toward the oldest point.
         */
        private void renderTrail(PoseStack pose, VertexConsumer consumer, Vec3 camera, int headColor, int light) {
            int n = trail.size();
            if (n < 2) return;
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            Matrix4f matrix4f = pose.last().pose();
            Matrix3f matrix3f = pose.last().normal();

            Vec3[] points = trail.toArray(new Vec3[0]);
            float halfWidthStart = Math.max(0f, spec.trail.widthStart) * 0.5f;
            float halfWidthEnd = Math.max(0f, spec.trail.widthEnd) * 0.5f;
            int r = headColor >> 24 & 255;
            int g = headColor >> 16 & 255;
            int b = headColor >> 8 & 255;
            int baseA = headColor & 255;

            for (int i = 0; i < n - 1; i++) {
                Vec3 p0 = points[i];
                Vec3 p1 = points[i + 1];
                Vec3 segment = p1.subtract(p0);
                if (segment.lengthSqr() < 1.0e-8) continue;

                float t0 = (float) i / (n - 1);
                float t1 = (float) (i + 1) / (n - 1);
                float hw0 = Mth.lerp(t0, halfWidthEnd, halfWidthStart);
                float hw1 = Mth.lerp(t1, halfWidthEnd, halfWidthStart);
                int a0 = Mth.clamp(Math.round(baseA * t0), 0, 255);
                int a1 = Mth.clamp(Math.round(baseA * t1), 0, 255);

                Vec3 side0 = ribbonSide(segment, camera.subtract(p0));
                Vec3 side1 = ribbonSide(segment, camera.subtract(p1));

                Vec3 left0 = p0.add(side0.scale(hw0));
                Vec3 right0 = p0.subtract(side0.scale(hw0));
                Vec3 left1 = p1.add(side1.scale(hw1));
                Vec3 right1 = p1.subtract(side1.scale(hw1));

                ribbonVertex(consumer, matrix4f, matrix3f, right0, r, g, b, a0, t0, 1f, light);
                ribbonVertex(consumer, matrix4f, matrix3f, left0, r, g, b, a0, t0, 0f, light);
                ribbonVertex(consumer, matrix4f, matrix3f, left1, r, g, b, a1, t1, 0f, light);
                ribbonVertex(consumer, matrix4f, matrix3f, right1, r, g, b, a1, t1, 1f, light);
            }
            pose.popPose();
        }

        /** Perpendicular to the segment, facing the camera — falls back to a fixed axis if degenerate. */
        private static Vec3 ribbonSide(Vec3 segment, Vec3 towardCamera) {
            Vec3 side = segment.cross(towardCamera);
            return side.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : side.normalize();
        }
    }

    private static void drawQuad(PoseStack poseStack, VertexConsumer consumer,
                                 float width, float height, int color, int light, int frame,
                                 int columns, int rows, float softEdge) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        int r = color >> 24 & 255;
        int g = color >> 16 & 255;
        int b = color >> 8 & 255;
        int a = color & 255;
        float u0 = (frame % columns) / (float) columns;
        float v0 = (frame / columns) / (float) rows;
        float u1 = u0 + 1f / columns;
        float v1 = v0 + 1f / rows;
        if (softEdge <= 0f) {
            vertex(consumer, matrix4f, matrix3f, -halfW, -halfH, r, g, b, a, u0, v1, light);
            vertex(consumer, matrix4f, matrix3f, halfW, -halfH, r, g, b, a, u1, v1, light);
            vertex(consumer, matrix4f, matrix3f, halfW, halfH, r, g, b, a, u1, v0, light);
            vertex(consumer, matrix4f, matrix3f, -halfW, halfH, r, g, b, a, u0, v0, light);
            return;
        }
        // Soft edge: subdivide the quad into 4 triangle-fan quads around its center, with alpha
        // fading from full at the center to a dimmer rim value at the outer edge/corners. This is
        // a vertex-color radial falloff (cheap, no shader or RenderType change) — it rounds off the
        // sprite's own silhouette but does not fade against scene depth (no soft intersection with
        // terrain/geometry the way true "soft particles" do).
        int rimA = Mth.clamp(Math.round(a * (1f - Mth.clamp(softEdge, 0f, 1f))), 0, 255);
        float uMid = (u0 + u1) * 0.5f;
        float vMid = (v0 + v1) * 0.5f;
        float uN = uMid, vN = v0, xN = 0f, yN = halfH;
        float uS = uMid, vS = v1, xS = 0f, yS = -halfH;
        float uE = u1, vE = vMid, xE = halfW, yE = 0f;
        float uW = u0, vW = vMid, xW = -halfW, yW = 0f;
        // NE quadrant
        vertex(consumer, matrix4f, matrix3f, 0f, 0f, r, g, b, a, uMid, vMid, light);
        vertex(consumer, matrix4f, matrix3f, xE, yE, r, g, b, rimA, uE, vE, light);
        vertex(consumer, matrix4f, matrix3f, halfW, halfH, r, g, b, rimA, u1, v0, light);
        vertex(consumer, matrix4f, matrix3f, xN, yN, r, g, b, rimA, uN, vN, light);
        // NW quadrant
        vertex(consumer, matrix4f, matrix3f, 0f, 0f, r, g, b, a, uMid, vMid, light);
        vertex(consumer, matrix4f, matrix3f, xN, yN, r, g, b, rimA, uN, vN, light);
        vertex(consumer, matrix4f, matrix3f, -halfW, halfH, r, g, b, rimA, u0, v0, light);
        vertex(consumer, matrix4f, matrix3f, xW, yW, r, g, b, rimA, uW, vW, light);
        // SW quadrant
        vertex(consumer, matrix4f, matrix3f, 0f, 0f, r, g, b, a, uMid, vMid, light);
        vertex(consumer, matrix4f, matrix3f, xW, yW, r, g, b, rimA, uW, vW, light);
        vertex(consumer, matrix4f, matrix3f, -halfW, -halfH, r, g, b, rimA, u0, v1, light);
        vertex(consumer, matrix4f, matrix3f, xS, yS, r, g, b, rimA, uS, vS, light);
        // SE quadrant
        vertex(consumer, matrix4f, matrix3f, 0f, 0f, r, g, b, a, uMid, vMid, light);
        vertex(consumer, matrix4f, matrix3f, xS, yS, r, g, b, rimA, uS, vS, light);
        vertex(consumer, matrix4f, matrix3f, halfW, -halfH, r, g, b, rimA, u1, v1, light);
        vertex(consumer, matrix4f, matrix3f, xE, yE, r, g, b, rimA, uE, vE, light);
    }

    /** Multiplies the particle's RGB by {@code strength} (clamped into 0..255), alpha untouched.
     *  Vertex colors are 8-bit, so this isn't true HDR overbright — but it lets a dim base color
     *  read as fully bright/saturated, which is what actually shows up as "glow" once it stacks
     *  through additive blending against the scene behind it. */
    private static int applyGlow(int color, float strength) {
        int r = color >> 24 & 255;
        int g = color >> 16 & 255;
        int b = color >> 8 & 255;
        int a = color & 255;
        r = Mth.clamp(Math.round(r * strength), 0, 255);
        g = Mth.clamp(Math.round(g * strength), 0, 255);
        b = Mth.clamp(Math.round(b * strength), 0, 255);
        return r << 24 | g << 16 | b << 8 | a;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, int r, int g, int b, int a,
                               float u, float v, int light) {
        consumer.vertex(pose, x, y, 0f)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }

    /** Like {@link #vertex}, but for an explicit world-relative position (a ribbon strip's vertices
     *  each sit at a genuinely different point in space, unlike a billboard quad's local corners). */
    private static void ribbonVertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                                     Vec3 pos, int r, int g, int b, int a, float u, float v, int light) {
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }


    /**
     * Resolves {@code Motion.attractorTarget} for one particle: blank or {@code "origin"}
     * (default) pulls back toward the particle's own spawn point — the natural choice for a
     * self-contained orbiting cluster with no separate anchor. A literal {@code "x,y,z"} world
     * coordinate pulls toward a fixed point instead (e.g. a shared center for several emitters).
     * Anything unparseable falls back to the spawn point rather than disabling the pull.
     */
    private static double[] resolveAttractorTarget(String raw, double originX, double originY, double originZ) {
        String value = raw == null ? "" : raw.trim();
        if (!value.isEmpty() && !"origin".equalsIgnoreCase(value)) {
            String[] parts = value.split(",");
            if (parts.length == 3) {
                try {
                    return new double[]{
                            Double.parseDouble(parts[0].trim()),
                            Double.parseDouble(parts[1].trim()),
                            Double.parseDouble(parts[2].trim())
                    };
                } catch (NumberFormatException ignored) {
                    // fall through to origin
                }
            }
        }
        return new double[]{originX, originY, originZ};
    }

    private static float curve(String curve, float t) {
        return switch (normalized(curve)) {
            case "ease_in" -> t * t;
            case "ease_out" -> 1f - (1f - t) * (1f - t);
            case "ease_in_out" -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
            case "smooth" -> t * t * (3f - 2f * t);
            case "pulse" -> (float) Math.sin(t * Math.PI);
            case "step" -> t < 0.999f ? 0f : 1f;
            default -> t;
        };
    }

    private static float randomRange(Random random, float min, float max) {
        if (max < min) {
            float swap = min;
            min = max;
            max = swap;
        }
        return min + random.nextFloat() * (max - min);
    }

    private static int randomColor(Random random, String minRaw, String maxRaw) {
        int min = parseColor(minRaw, 0xFFFFFFFF);
        int max = parseColor(maxRaw, min);
        return lerpColor(min, max, random.nextFloat());
    }

    /** Internal color order is RGBA so it matches #RRGGBBAA. */
    private static int parseColor(String raw, int fallback) {
        if (raw == null) return fallback;
        String value = raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            if (value.length() == 6) return (int) Long.parseLong(value + "FF", 16);
            if (value.length() == 8) return (int) Long.parseLong(value, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 24 & 255, ag = a >> 16 & 255, ab = a >> 8 & 255, aa = a & 255;
        int br = b >> 24 & 255, bg = b >> 16 & 255, bb = b >> 8 & 255, ba = b & 255;
        return Mth.clamp((int) Mth.lerp(t, ar, br), 0, 255) << 24
                | Mth.clamp((int) Mth.lerp(t, ag, bg), 0, 255) << 16
                | Mth.clamp((int) Mth.lerp(t, ab, bb), 0, 255) << 8
                | Mth.clamp((int) Mth.lerp(t, aa, ba), 0, 255);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static RenderType renderType(ParticleSystemSpec spec) {
        String blend = normalized(spec.renderer.blend);
        String textureKey = spec.renderer.texture == null ? "" : spec.renderer.texture.trim();
        String key = blend + '\0' + textureKey;
        RenderType cached = RENDER_TYPES.get(key);
        if (cached != null) return cached;
        ResourceLocation texture = ParticleTextureCache.resolve(textureKey);
        RenderType created = switch (blend) {
            case "cutout" -> RenderType.entityCutoutNoCull(texture);
            case "additive" -> RenderType.energySwirl(texture, 0f, 0f);
            default -> RenderType.entityTranslucent(texture);
        };
        RENDER_TYPES.put(key, created);
        return created;
    }
}
