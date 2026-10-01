package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Per-emitter tick state for a loaded scene PARTICLE node. */
public final class SceneParticleRuntime {
    public final String nodeId;
    private int cooldown;
    private boolean firedOnce;
    private float customAccumulator;
    private int customAge;
    private boolean customFinished;
    /** Running shape-sample offset for the custom engine (text/image/line/spiral shapes pick their
     *  spawn point by index) — advanced by however many particles each burst actually spawns, so
     *  consecutive one/two-particle-per-tick bursts don't all resample the same point. */
    private long spawnIndex;

    public SceneParticleRuntime(SceneNode node) {
        this.nodeId = node.id;
        this.cooldown = 0;
        this.firedOnce = false;
    }

    /**
     * Fire one burst now. For one-shot emitters ({@code particleLoop = false}) this is the
     * only way to spawn particles after the node is registered — they no longer auto-fire
     * on scene start.
     */
    public void emitNow(SceneActiveState state, ServerLevel level) {
        SceneNode node = state == null ? null : state.document.findNode(nodeId);
        if (node == null || node.type != SceneNodeType.PARTICLE || !node.active || level == null) return;

        SceneTransform world = state.document.worldTransform(nodeId);
        if (world == null) world = node.transform != null ? node.transform : new SceneTransform();
        RandomSource random = level.getRandom();
        var spec = node.particles(false);
        if (spec != null && spec.custom) {
            int count = node.particleCount;
            if (spec.bursts != null && !spec.bursts.isEmpty() && spec.bursts.get(0) != null) {
                count = spec.bursts.get(0).count;
            }
            int spawned = Math.max(1, count);
            SceneParticleEmitter.emitCustomServer(level, node, world, random, spawned, spawnIndex);
            spawnIndex += spawned;
            if (spec.emission == null || !spec.emission.loop) {
                customFinished = true;
                customAge = 0;
                customAccumulator = 0f;
            }
        } else {
            SceneParticleEmitter.emitServer(level, node, world, random);
        }

        firedOnce = true;
        if (loops(node, spec)) {
            cooldown = Math.max(1, node.particleInterval) - 1;
        } else {
            // Stay idle until the next explicit emit() / particleEmit().
            cooldown = 0;
        }
    }

    public void tick(SceneActiveState state, ServerLevel level) {
        SceneNode node = state.document.findNode(nodeId);
        if (node == null || node.type != SceneNodeType.PARTICLE || !node.active) return;
        var spec = node.particles(false);
        if (spec != null && spec.custom) {
            tickCustom(state, level, node, spec);
            return;
        }
        // One-shot emitters only run via emitNow() / node.emit().
        if (!node.particleLoop) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        SceneTransform world = state.document.worldTransform(nodeId);
        if (world == null) world = node.transform != null ? node.transform : new SceneTransform();
        RandomSource random = level.getRandom();
        SceneParticleEmitter.emitServer(level, node, world, random);

        firedOnce = true;
        int interval = Math.max(1, node.particleInterval);
        cooldown = interval - 1;
    }

    private void tickCustom(SceneActiveState state, ServerLevel level, SceneNode node,
                            org.zonarstudio.spraute_engine.particle.ParticleSystemSpec spec) {
        if (spec.emission == null || !spec.emission.enabled || !spec.emission.loop || customFinished) return;
        customAge++;
        int delayTicks = Math.max(0, Math.round(spec.emission.delay * 20f));
        if (customAge <= delayTicks) return;
        int durationTicks = Math.max(1, Math.round(spec.emission.duration * 20f));
        if (customAge > delayTicks + durationTicks) {
            if (spec.emission.loop) {
                customAge = 0;
                customAccumulator = 0f;
            } else {
                customFinished = true;
            }
            return;
        }
        if (spec.emission.rate <= 0f) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            SceneTransform world = state.document.worldTransform(nodeId);
            if (world == null) world = node.transform != null ? node.transform : new SceneTransform();
            int spawned = Math.max(1, node.particleCount);
            SceneParticleEmitter.emitCustomServer(level, node, world, level.getRandom(), spawned, spawnIndex);
            spawnIndex += spawned;
            cooldown = Math.max(1, node.particleInterval) - 1;
            return;
        }
        customAccumulator += spec.emission.rate / 20f;
        int count = (int) customAccumulator;
        if (count <= 0) return;
        customAccumulator -= count;
        SceneTransform world = state.document.worldTransform(nodeId);
        if (world == null) world = node.transform != null ? node.transform : new SceneTransform();
        SceneParticleEmitter.emitCustomServer(level, node, world, level.getRandom(), count, spawnIndex);
        spawnIndex += count;
    }

    private static boolean loops(SceneNode node, org.zonarstudio.spraute_engine.particle.ParticleSystemSpec spec) {
        if (spec != null && spec.custom && spec.emission != null) return spec.emission.loop;
        return node != null && node.particleLoop;
    }
}
