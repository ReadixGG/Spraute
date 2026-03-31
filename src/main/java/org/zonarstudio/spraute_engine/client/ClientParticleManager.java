package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.entity.client.SprauteNpcRenderer;
import org.zonarstudio.spraute_engine.network.BoneParticlePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class ClientParticleManager {

    private static final Map<String, BoneParticlePacket> boneTasks = new ConcurrentHashMap<>();

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
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;

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
}
