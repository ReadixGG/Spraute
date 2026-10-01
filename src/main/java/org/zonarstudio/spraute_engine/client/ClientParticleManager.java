package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer;
import org.zonarstudio.spraute_engine.mixin.ParticleAccessor;
import org.zonarstudio.spraute_engine.mixin.ParticleEngineAccessor;
import org.zonarstudio.spraute_engine.network.BoneParticlePacket;
import org.zonarstudio.spraute_engine.network.ControlledParticleBurstPacket;
import org.zonarstudio.spraute_engine.scene.SceneParticleEmitter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class ClientParticleManager {

    private static final Map<String, BoneParticlePacket> boneTasks = new ConcurrentHashMap<>();
    /** Controlled-burst particles that fade alpha near the end of life. */
    private static final List<FadingParticle> FADING = new ArrayList<>();

    private record FadingParticle(Particle particle, int fadeTicks) {}

    public static void handleControlledBurst(ControlledParticleBurstPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || packet == null) return;
        ParticleOptions opt = SceneParticleEmitter.resolveType(packet.particleType);
        if (opt == null) return;
        spawnControlledBurst(mc.particleEngine, opt, packet.gravity, packet.lifetime,
                packet.collide, packet.fade, packet.data);
    }

    /**
     * Spawn particles with exact launch velocity, then apply gravity as Particle.gravity
     * so fall accelerates over lifetime instead of subtracting from the initial burst.
     */
    public static void spawnControlledBurst(ParticleEngine engine, ParticleOptions opt,
                                            float gravity, float[] data) {
        spawnControlledBurst(engine, opt, gravity, 0, false, 0, data);
    }

    public static void spawnControlledBurst(ParticleEngine engine, ParticleOptions opt,
                                            float gravity, int lifetime, float[] data) {
        spawnControlledBurst(engine, opt, gravity, lifetime, false, 0, data);
    }

    public static void spawnControlledBurst(ParticleEngine engine, ParticleOptions opt,
                                            float gravity, int lifetime, boolean collide, float[] data) {
        spawnControlledBurst(engine, opt, gravity, lifetime, collide, 0, data);
    }

    public static void spawnControlledBurst(ParticleEngine engine, ParticleOptions opt,
                                            float gravity, int lifetime, boolean collide,
                                            int fadeTicks, float[] data) {
        if (engine == null || opt == null || data == null) return;
        ParticleEngineAccessor factory = (ParticleEngineAccessor) engine;
        int count = data.length / 6;
        int fade = Math.max(0, fadeTicks);
        for (int i = 0; i < count; i++) {
            int o = i * 6;
            double x = data[o], y = data[o + 1], z = data[o + 2];
            double vx = data[o + 3], vy = data[o + 4], vz = data[o + 5];
            // makeParticle + add: skip ParticleStatus random drops (DECREASED/MINIMAL).
            Particle p = factory.spraute$makeParticle(opt, x, y, z, vx, vy, vz);
            if (p == null) continue;
            p.setParticleSpeed(vx, vy, vz);
            ParticleAccessor acc = (ParticleAccessor) p;
            // Default off: bursts near the ground otherwise vanish on the first hit.
            acc.spraute$setHasPhysics(collide);
            if (gravity > 0f) {
                acc.spraute$setGravity(gravity);
            }
            if (lifetime > 0) {
                acc.spraute$setLifetime(lifetime);
            }
            if (fade > 0) {
                int life = acc.spraute$getLifetime();
                int useFade = Math.min(fade, Math.max(1, life));
                acc.spraute$setAlpha(1f);
                FADING.add(new FadingParticle(p, useFade));
            }
            engine.add(p);
        }
    }

    public static void handleBoneParticlePacket(BoneParticlePacket packet) {
        if (packet.action == 0) { // START
            boneTasks.put(packet.taskId, packet);
        } else if (packet.action == 1) { // STOP
            boneTasks.remove(packet.taskId);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        boneTasks.clear();
        FADING.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;

        tickFadingParticles();

        ParticleEngine engine = mc.particleEngine;

        for (BoneParticlePacket task : boneTasks.values()) {
            Entity entity = level.getEntity(task.entityId);
            if (!(entity instanceof SprauteNpcEntity npc)) continue;

            // Get renderer and model instance to find bone
            var dispatcher = mc.getEntityRenderDispatcher();
            var renderer = dispatcher.getRenderer(npc);
            if (!(renderer instanceof SprauteNpcRenderer npcRenderer)) continue;

            // We need to access SpModelInstance from SprauteNpcRenderer
            // Since it's private in renderer, we can add a getter or find it.
            // Alternatively, since SpModelCache caches models, the bone might just be offset.
            // Let's rely on a public method we will add to SprauteNpcRenderer: getBoneWorldPosition
            SpVec3 pos = npcRenderer.getBoneWorldPosition(npc, task.boneName, mc.getFrameTime());
            if (pos == null) continue; // Bone not found or model not loaded

            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(task.particleType));
            if (type instanceof ParticleOptions options) {
                for (int i = 0; i < task.count; i++) {
                    double vx = mc.player.getRandom().nextGaussian() * task.dx;
                    double vy = mc.player.getRandom().nextGaussian() * task.dy;
                    double vz = mc.player.getRandom().nextGaussian() * task.dz;
                    
                    // If speed is 0, dx/dy/dz act as spread. If speed > 0, they act as direction
                    double sx = (task.speed > 0) ? task.dx * task.speed : vx;
                    double sy = (task.speed > 0) ? task.dy * task.speed : vy;
                    double sz = (task.speed > 0) ? task.dz * task.speed : vz;

                    double px = pos.x;
                    double py = pos.y;
                    double pz = pos.z;

                    if (task.speed == 0) {
                        px += vx;
                        py += vy;
                        pz += vz;
                        sx = 0; sy = 0; sz = 0;
                    }

                    engine.createParticle(options, px, py, pz, sx, sy, sz);
                }
            }
        }
    }

    private static void tickFadingParticles() {
        if (FADING.isEmpty()) return;
        Iterator<FadingParticle> it = FADING.iterator();
        while (it.hasNext()) {
            FadingParticle entry = it.next();
            Particle p = entry.particle();
            ParticleAccessor acc = (ParticleAccessor) p;
            if (acc.spraute$isRemoved()) {
                it.remove();
                continue;
            }
            int life = acc.spraute$getLifetime();
            int age = acc.spraute$getAge();
            int remaining = life - age;
            int fade = entry.fadeTicks();
            if (remaining <= fade) {
                float a = fade <= 0 ? 0f : Math.max(0f, remaining / (float) fade);
                acc.spraute$setAlpha(a);
            }
        }
    }
}
