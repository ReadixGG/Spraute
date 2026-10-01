package org.zonarstudio.spraute_engine.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.SyncAudioSourcePacket;
import org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client registry of looping scene audio sources with custom falloff. */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class ClientAudioSourceManager {
    private static final Map<String, AudioSourceSoundInstance> ACTIVE = new ConcurrentHashMap<>();
    /** Last synced config per node (kept after one-shot ends so replay-on-enter still works). */
    private static final Map<String, SyncAudioSourcePacket> CONFIGS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WAS_IN_ZONE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_PLAY_GEN = new ConcurrentHashMap<>();

    private ClientAudioSourceManager() {}

    public static void handle(SyncAudioSourcePacket msg) {
        if (msg == null || msg.nodeId == null || msg.nodeId.isBlank()) return;
        if (msg.stop) {
            stop(msg.nodeId);
            CONFIGS.remove(msg.nodeId);
            WAS_IN_ZONE.remove(msg.nodeId);
            LAST_PLAY_GEN.remove(msg.nodeId);
            return;
        }
        CONFIGS.put(msg.nodeId, msg);
        Integer prevGen = LAST_PLAY_GEN.put(msg.nodeId, msg.playGeneration);
        boolean forceRestart = prevGen != null && prevGen != msg.playGeneration;
        upsert(msg, forceRestart);
    }

    public static void stop(String nodeId) {
        AudioSourceSoundInstance inst = ACTIVE.remove(nodeId);
        if (inst != null) {
            inst.forceStop();
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(inst);
            }
        }
    }

    public static void stopAll() {
        for (String id : ACTIVE.keySet()) stop(id);
        ACTIVE.clear();
        CONFIGS.clear();
        WAS_IN_ZONE.clear();
        LAST_PLAY_GEN.clear();
    }

    private static void upsert(SyncAudioSourcePacket msg, boolean forceRestart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        AudioSourceSoundInstance existing = ACTIVE.get(msg.nodeId);
        if (!forceRestart
                && existing != null
                && !existing.finished()
                && existing.matchesSound(msg.soundId)
                && existing.isLooping() == msg.loop) {
            existing.applyPacket(msg);
            return;
        }
        stop(msg.nodeId);
        startFresh(msg);
    }

    private static void startFresh(SyncAudioSourcePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        ResourceLocation rl = ClientWorkspaceSounds.location(msg.soundId);
        if (rl == null) return;
        ClientWorkspaceSounds.prepare(msg.soundId);
        SoundEvent event = ClientWorkspaceSounds.event(rl);
        AudioSourceSoundInstance inst = new AudioSourceSoundInstance(event, SoundSource.AMBIENT, msg);
        ACTIVE.put(msg.nodeId, inst);
        mc.getSoundManager().play(inst);
    }

    /** Restart from the beginning using the last known config. */
    static void restart(String nodeId) {
        SyncAudioSourcePacket cfg = CONFIGS.get(nodeId);
        if (cfg == null || cfg.stop) return;
        stop(nodeId);
        startFresh(cfg);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;
        purgeFinished();
        tickReplayOnEnter(mc.player);
    }

    private static void tickReplayOnEnter(Player player) {
        double px = player.getX();
        double py = player.getY() + player.getEyeHeight();
        double pz = player.getZ();
        for (Map.Entry<String, SyncAudioSourcePacket> e : CONFIGS.entrySet()) {
            SyncAudioSourcePacket cfg = e.getValue();
            if (cfg == null || !cfg.replayOnEnter || cfg.personal) continue;
            String id = e.getKey();
            boolean inside = volumeFor(cfg, px, py, pz) > 0.0001f;
            Boolean was = WAS_IN_ZONE.get(id);
            if (was == null) {
                WAS_IN_ZONE.put(id, inside);
                continue;
            }
            if (inside && !was) {
                restart(id);
            }
            WAS_IN_ZONE.put(id, inside);
        }
    }

    /** Volume for a synthetic node built from a live packet (editor-less). */
    static float volumeFor(SyncAudioSourcePacket data, double px, double py, double pz) {
        if (data == null) return 0f;
        if (data.personal) return Math.max(0f, data.volume);
        SceneNode fake = new SceneNode();
        fake.audioEnabled = true;
        fake.active = true;
        fake.audioShape = data.shape;
        fake.audioFullRadius = data.fullRadius;
        fake.audioMaxDistance = data.maxDistance;
        fake.audioVolume = data.volume;
        SceneTransform world = new SceneTransform();
        world.px = data.x;
        world.py = data.y;
        world.pz = data.z;
        world.sx = data.sx;
        world.sy = data.sy;
        world.sz = data.sz;
        return SceneAudioAttenuation.volumeAt(fake, world, px, py, pz);
    }

    /** Drop stopped instances from the map. */
    public static void purgeFinished() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        Iterator<Map.Entry<String, AudioSourceSoundInstance>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AudioSourceSoundInstance> e = it.next();
            AudioSourceSoundInstance inst = e.getValue();
            if (inst.finished()) {
                it.remove();
                continue;
            }
            if (!inst.isLooping() && !mc.getSoundManager().isActive(inst)) {
                it.remove();
            }
        }
    }
}
