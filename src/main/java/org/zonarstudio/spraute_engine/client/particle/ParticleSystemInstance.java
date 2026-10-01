package org.zonarstudio.spraute_engine.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.particle.ParticleEffectDef;
import org.zonarstudio.spraute_engine.particle.ParticleMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One deterministic emitter. Package-private mutation is owned by SprauteParticleEngine. */
public final class ParticleSystemInstance {
    public final String id;
    public final String effectId;
    public ParticleEffectDef effect;
    public long seed;
    public double x, y, z;
    public float yaw, pitch;
    public float scale = 1f;
    public boolean paused;
    public boolean stopped;
    public String bindNode = "";
    public String bindBone = "";

    final List<ParticleInstance> particles = new ArrayList<>();
    private long serial;
    private float elapsed;
    private float emitCarry;
    private double lastX, lastY, lastZ;
    private final List<Integer> burstCycles = new ArrayList<>();
    private AABB bounds;

    ParticleSystemInstance(String id, String effectId, ParticleEffectDef effect, long seed) {
        this.id = id;
        this.effectId = effectId == null ? "" : effectId;
        this.effect = effect == null ? new ParticleEffectDef() : effect.copy();
        this.seed = seed == 0 ? this.effect.emission.seed : seed;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        for (int i = 0; i < this.effect.emission.bursts.size(); i++) burstCycles.add(0);
    }

    void tick(SprauteParticleEngine engine) {
        if (paused) return;
        float previous = elapsed;
        elapsed += 1f / 20f;
        ParticleEffectDef.Emission emission = effect.emission;
        if (!stopped && elapsed >= emission.delay) {
            float activeTime = elapsed - emission.delay;
            boolean active = emission.loop || emission.duration <= 0 || activeTime <= emission.duration;
            if (active) {
                emitCarry += Math.max(0f, emission.rate) / 20f;
                double distance = Math.sqrt((x - lastX) * (x - lastX)
                        + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ));
                emitCarry += distance * Math.max(0f, emission.rateDistance);
                int count = (int) emitCarry;
                emitCarry -= count;
                if (count > 0) burst(engine, count);
                tickBursts(engine, activeTime, previous - emission.delay);
            } else if (particles.isEmpty()) {
                stopped = true;
            }
        }
        lastX = x;
        lastY = y;
        lastZ = z;

        bounds = null;
        for (int i = particles.size() - 1; i >= 0; i--) {
            ParticleInstance p = particles.get(i);
            if (!tickParticle(p, engine)) {
                particles.remove(i);
                engine.recycle(p);
                continue;
            }
            double r = Math.max(p.sizeX, p.sizeY);
            AABB part = new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r);
            bounds = bounds == null ? part : bounds.minmax(part);
        }
    }

    private void tickBursts(SprauteParticleEngine engine, float time, float previousTime) {
        for (int i = 0; i < effect.emission.bursts.size(); i++) {
            ParticleEffectDef.Burst b = effect.emission.bursts.get(i);
            int cycles = Math.max(1, b.cycles);
            int completed = burstCycles.get(i);
            while (completed < cycles) {
                float at = b.time + completed * Math.max(0f, b.interval);
                if (time < at || previousTime >= at) break;
                Random r = new Random(ParticleMath.mixSeed(seed, i * 4096L + completed));
                if (r.nextFloat() <= Mth.clamp(b.probability, 0f, 1f)) burst(engine, b.count);
                completed++;
            }
            burstCycles.set(i, completed);
        }
    }

    public void burst(SprauteParticleEngine engine, int requested) {
        int available = Math.max(0, effect.emission.maxParticles - particles.size());
        int count = Math.min(Math.max(0, requested), available);
        for (int i = 0; i < count; i++) {
            ParticleInstance particle = engine.obtain();
            if (particle == null) return;
            spawn(particle);
            particles.add(particle);
            engine.onBirth(this, particle);
        }
        if (requested > count) engine.addDropped(requested - count);
    }

    public void addExplicit(SprauteParticleEngine engine, double px, double py, double pz,
                            double vx, double vy, double vz) {
        if (particles.size() >= effect.emission.maxParticles) {
            engine.addDropped(1);
            return;
        }
        ParticleInstance particle = engine.obtain();
        if (particle == null) return;
        spawn(particle);
        particle.x = particle.oldX = px;
        particle.y = particle.oldY = py;
        particle.z = particle.oldZ = pz;
        particle.vx = vx;
        particle.vy = vy;
        particle.vz = vz;
        particles.add(particle);
        engine.onBirth(this, particle);
    }

    private void spawn(ParticleInstance p) {
        p.reset();
        p.owner = this;
        p.serial = serial++;
        Random random = new Random(ParticleMath.mixSeed(seed, p.serial));
        double[] position = samplePosition(random, p.serial);
        double[] direction = sampleDirection(random, position);
        float speed = range(random, effect.initial.speedMin, effect.initial.speedMax);
        p.x = p.oldX = position[0];
        p.y = p.oldY = position[1];
        p.z = p.oldZ = position[2];
        p.vx = direction[0] * speed;
        p.vy = direction[1] * speed;
        p.vz = direction[2] * speed;
        p.lifetime = Math.max(0.01f, range(random, effect.initial.lifetimeMin, effect.initial.lifetimeMax));
        p.sizeX = range(random, effect.initial.sizeMin, effect.initial.sizeMax) * scale;
        p.sizeY = range(random, effect.initial.sizeYMin, effect.initial.sizeYMax) * scale;
        p.rotation = range(random, effect.initial.rotationMin, effect.initial.rotationMax);
        p.color = ParticleMath.randomColor(effect.initial.colorMin, effect.initial.colorMax, random);
        int frames = Math.max(1, effect.renderer.tilesX * effect.renderer.tilesY);
        p.frame = "random".equals(effect.renderer.sheetMode) ? random.nextInt(frames) : 0;
    }

    private boolean tickParticle(ParticleInstance p, SprauteParticleEngine engine) {
        p.oldX = p.x;
        p.oldY = p.y;
        p.oldZ = p.z;
        p.age += 1f / 20f;
        if (p.age >= p.lifetime) {
            engine.onDeath(this, p);
            return false;
        }
        float t = Mth.clamp(p.age / p.lifetime, 0f, 1f);
        ParticleEffectDef.Motion m = effect.motion;
        p.vx += m.gravityX + m.accelerationX;
        p.vy += m.gravityY + m.accelerationY;
        p.vz += m.gravityZ + m.accelerationZ;
        if (m.attractorStrength != 0 && !"none".equals(m.attractorMode)) {
            double tx = "point".equals(m.attractorMode) ? m.attractorX : x;
            double ty = "point".equals(m.attractorMode) ? m.attractorY : y;
            double tz = "point".equals(m.attractorMode) ? m.attractorZ : z;
            Vec3 to = new Vec3(tx - p.x, ty - p.y, tz - p.z);
            if (to.lengthSqr() > 1.0e-8) {
                to = to.normalize().scale(m.attractorStrength);
                p.vx += to.x;
                p.vy += to.y;
                p.vz += to.z;
            }
        }
        if (m.vortex != 0) {
            double dx = p.x - x;
            double dz = p.z - z;
            p.vx += -dz * m.vortex;
            p.vz += dx * m.vortex;
        }
        if (m.noiseStrength != 0) {
            double phase = p.serial * 1.618 + p.age * m.noiseScroll;
            p.vx += Math.sin(phase * m.noiseFrequency) * m.noiseStrength;
            p.vy += Math.sin((phase + 2.1) * m.noiseFrequency) * m.noiseStrength;
            p.vz += Math.sin((phase + 4.2) * m.noiseFrequency) * m.noiseStrength;
        }
        float drag = Mth.clamp(1f - m.drag, 0f, 1f);
        p.vx *= drag;
        p.vy *= drag;
        p.vz *= drag;
        double speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        if (m.maxSpeed > 0 && speed > m.maxSpeed) {
            double factor = m.maxSpeed / speed;
            p.vx *= factor;
            p.vy *= factor;
            p.vz *= factor;
        }
        double nx = p.x + p.vx;
        double ny = p.y + p.vy;
        double nz = p.z + p.vz;
        if (effect.collision.enabled && collides(nx, ny, nz)) {
            engine.onCollision(this, p);
            if (effect.collision.kill) return false;
            float bounce = Mth.clamp(effect.collision.bounce, 0f, 1f);
            p.vx = -p.vx * bounce;
            p.vy = -p.vy * bounce;
            p.vz = -p.vz * bounce;
            float friction = Mth.clamp(1f - effect.collision.friction, 0f, 1f);
            p.vx *= friction;
            p.vz *= friction;
            p.age += effect.collision.lifetimeLoss;
        } else {
            p.x = nx;
            p.y = ny;
            p.z = nz;
        }
        p.rotation += ParticleMath.curve(effect.overLifetime.rotation, t);
        updateFrame(p, t);
        updateTrail(p);
        return true;
    }

    private boolean collides(double px, double py, double pz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockPos pos = new BlockPos(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private void updateFrame(ParticleInstance p, float t) {
        int frames = Math.max(1, effect.renderer.tilesX * effect.renderer.tilesY);
        if ("lifetime".equals(effect.renderer.sheetMode)) {
            p.frame = Math.min(frames - 1, (int) (t * frames));
        } else if ("fps".equals(effect.renderer.sheetMode)) {
            int frame = (int) (p.age * effect.renderer.sheetFps);
            p.frame = effect.renderer.sheetLoop ? Math.floorMod(frame, frames) : Math.min(frame, frames - 1);
        }
    }

    private void updateTrail(ParticleInstance p) {
        if (!effect.trail.enabled) return;
        Vec3 current = new Vec3(p.x, p.y, p.z);
        if (p.trail.isEmpty() || p.trail.peekLast().distanceToSqr(current)
                >= effect.trail.minDistance * effect.trail.minDistance) {
            p.trail.addLast(current);
        }
        int max = Math.max(2, Math.min(128, (int) (effect.trail.lifetime * 20f)));
        while (p.trail.size() > max) p.trail.removeFirst();
    }

    private double[] samplePosition(Random random, long index) {
        ParticleEffectDef.Shape s = effect.shape;
        double lx = 0, ly = 0, lz = 0;
        String type = s.type == null ? "point" : s.type;
        switch (type) {
            case "line" -> {
                double t = random.nextDouble();
                lx = s.lineX * t;
                ly = s.lineY * t;
                lz = s.lineZ * t;
            }
            case "box" -> {
                lx = signed(random) * s.boxX * 0.5;
                ly = signed(random) * s.boxY * 0.5;
                lz = signed(random) * s.boxZ * 0.5;
                if ("surface".equals(s.emitFrom)) {
                    int face = random.nextInt(3);
                    if (face == 0) lx = Math.copySign(s.boxX * 0.5, lx);
                    if (face == 1) ly = Math.copySign(s.boxY * 0.5, ly);
                    if (face == 2) lz = Math.copySign(s.boxZ * 0.5, lz);
                }
            }
            case "sphere" -> {
                Vec3 dir = randomUnit(random);
                double radius = "surface".equals(s.emitFrom) ? s.radius
                        : Mth.lerp((float) Math.cbrt(random.nextDouble()), s.innerRadius, s.radius);
                lx = dir.x * radius; ly = dir.y * radius; lz = dir.z * radius;
            }
            case "circle" -> {
                double a = random.nextDouble() * Math.PI * 2;
                double r = "edge".equals(s.emitFrom) ? s.radius : Math.sqrt(random.nextDouble()) * s.radius;
                lx = Math.cos(a) * r; lz = Math.sin(a) * r;
            }
            case "cone" -> {
                double h = random.nextDouble() * s.height;
                double r = Math.tan(Math.toRadians(s.angle)) * h * Math.sqrt(random.nextDouble());
                double a = random.nextDouble() * Math.PI * 2;
                lx = Math.cos(a) * r; ly = h; lz = Math.sin(a) * r;
            }
            case "cylinder" -> {
                double a = random.nextDouble() * Math.PI * 2;
                double r = "surface".equals(s.emitFrom) ? s.radius : Math.sqrt(random.nextDouble()) * s.radius;
                lx = Math.cos(a) * r; ly = signed(random) * s.height * 0.5; lz = Math.sin(a) * r;
            }
            case "spiral" -> {
                double t = (index % 256) / 255.0;
                double a = t * Math.PI * 8;
                lx = Math.cos(a) * s.radius; ly = t * s.height; lz = Math.sin(a) * s.radius;
            }
            default -> {}
        }
        Vec3 rotated = rotate(lx * scale, ly * scale, lz * scale);
        return new double[]{x + rotated.x, y + rotated.y, z + rotated.z};
    }

    private double[] sampleDirection(Random random, double[] position) {
        Vec3 axis = new Vec3(effect.initial.directionX, effect.initial.directionY, effect.initial.directionZ);
        if (axis.lengthSqr() < 1.0e-8) axis = new Vec3(0, 1, 0);
        axis = rotate(axis.x, axis.y, axis.z).normalize();
        double angle = Math.toRadians(Math.max(0f, effect.initial.cone));
        double cos = Mth.lerp(random.nextDouble(), Math.cos(angle), 1.0);
        double sin = Math.sqrt(Math.max(0, 1 - cos * cos));
        double phi = random.nextDouble() * Math.PI * 2;
        Vec3 helper = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = helper.cross(axis).normalize();
        Vec3 v = axis.cross(u);
        Vec3 out = u.scale(Math.cos(phi) * sin).add(v.scale(Math.sin(phi) * sin)).add(axis.scale(cos));
        if (effect.shape.alignDirection) {
            Vec3 radial = new Vec3(position[0] - x, position[1] - y, position[2] - z);
            if (radial.lengthSqr() > 1.0e-8) out = radial.normalize();
        }
        return new double[]{out.x, out.y, out.z};
    }

    private Vec3 rotate(double px, double py, double pz) {
        double yr = Math.toRadians(-yaw);
        double pr = Math.toRadians(pitch);
        double x1 = px * Math.cos(yr) - pz * Math.sin(yr);
        double z1 = px * Math.sin(yr) + pz * Math.cos(yr);
        double y2 = py * Math.cos(pr) - z1 * Math.sin(pr);
        double z2 = py * Math.sin(pr) + z1 * Math.cos(pr);
        return new Vec3(x1, y2, z2);
    }

    private static Vec3 randomUnit(Random random) {
        double y = signed(random);
        double a = random.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    private static double signed(Random random) {
        return random.nextDouble() * 2 - 1;
    }

    private static float range(Random random, float min, float max) {
        return min + random.nextFloat() * Math.max(0f, max - min);
    }

    public int particleCount() {
        return particles.size();
    }

    public AABB bounds() {
        return bounds;
    }
}
