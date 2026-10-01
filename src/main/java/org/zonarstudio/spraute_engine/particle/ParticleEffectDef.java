package org.zonarstudio.spraute_engine.particle;

import org.zonarstudio.spraute_engine.scene.SceneNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility schema for partially migrated RES_PARTICLE definitions.
 * New PARTICLE nodes use {@link ParticleSystemSpec}; this class keeps old files loadable.
 */
public final class ParticleEffectDef {
    public int version = 1;
    public Renderer renderer = new Renderer();
    public Emission emission = new Emission();
    public Shape shape = new Shape();
    public Initial initial = new Initial();
    public Motion motion = new Motion();
    public OverLifetime overLifetime = new OverLifetime();
    public Collision collision = new Collision();
    public Trail trail = new Trail();

    public void normalize() {
        if (renderer == null) renderer = new Renderer();
        if (emission == null) emission = new Emission();
        if (shape == null) shape = new Shape();
        if (initial == null) initial = new Initial();
        if (motion == null) motion = new Motion();
        if (overLifetime == null) overLifetime = new OverLifetime();
        if (collision == null) collision = new Collision();
        if (trail == null) trail = new Trail();
        if (emission.bursts == null) emission.bursts = new ArrayList<>();
        emission.maxParticles = Math.max(1, emission.maxParticles);
        renderer.tilesX = Math.max(1, renderer.tilesX);
        renderer.tilesY = Math.max(1, renderer.tilesY);
        initial.lifetimeMin = Math.max(0.01f, initial.lifetimeMin);
        initial.lifetimeMax = Math.max(initial.lifetimeMin, initial.lifetimeMax);
        initial.sizeMax = Math.max(initial.sizeMin, initial.sizeMax);
        initial.sizeYMax = Math.max(initial.sizeYMin, initial.sizeYMax);
    }

    public ParticleEffectDef copy() {
        normalize();
        ParticleEffectDef c = new ParticleEffectDef();
        c.version = version;
        c.renderer = renderer.copy();
        c.emission = emission.copy();
        c.shape = shape.copy();
        c.initial = initial.copy();
        c.motion = motion.copy();
        c.overLifetime = overLifetime.copy();
        c.collision = collision.copy();
        c.trail = trail.copy();
        return c;
    }

    public static ParticleEffectDef fromLegacy(SceneNode node, String texture) {
        ParticleEffectDef effect = new ParticleEffectDef();
        effect.renderer.texture = texture == null ? "" : texture;
        if (node == null) return effect;
        effect.emission.loop = node.particleLoop;
        effect.emission.rate = 0f;
        Burst burst = new Burst();
        burst.count = Math.max(1, node.particleCount);
        burst.cycles = 1;
        effect.emission.bursts.add(burst);
        effect.shape.type = node.particleShape;
        effect.shape.radius = node.particleRadius;
        effect.shape.height = node.particleHeight;
        effect.shape.lineX = node.particleLineX;
        effect.shape.lineY = node.particleLineY;
        effect.shape.lineZ = node.particleLineZ;
        effect.initial.directionX = node.particleDirX;
        effect.initial.directionY = node.particleDirY;
        effect.initial.directionZ = node.particleDirZ;
        effect.initial.cone = node.particleCone;
        float spread = Math.max(0f, node.particleSpeedSpread);
        effect.initial.speedMin = Math.max(0f, node.particleSpeed * (1f - spread));
        effect.initial.speedMax = Math.max(effect.initial.speedMin, node.particleSpeed * (1f + spread));
        float life = node.particleLifetime > 0 ? node.particleLifetime / 20f : 1.5f;
        effect.initial.lifetimeMin = life;
        effect.initial.lifetimeMax = life;
        effect.motion.gravityY = -Math.max(0f, node.particleGravity) * 0.04f;
        effect.collision.enabled = node.particleCollide;
        effect.normalize();
        return effect;
    }

    public static final class Renderer {
        public String texture = "";
        public String mode = "billboard";
        public String blend = "alpha";
        public boolean fullbright = true;
        public int tilesX = 1;
        public int tilesY = 1;
        public String sheetMode = "lifetime";
        public float sheetFps = 20f;
        public boolean sheetLoop = true;

        Renderer copy() {
            Renderer c = new Renderer();
            c.texture = texture;
            c.mode = mode;
            c.blend = blend;
            c.fullbright = fullbright;
            c.tilesX = tilesX;
            c.tilesY = tilesY;
            c.sheetMode = sheetMode;
            c.sheetFps = sheetFps;
            c.sheetLoop = sheetLoop;
            return c;
        }
    }

    public static final class Emission {
        public boolean loop = true;
        public float delay;
        public float duration = 5f;
        public float rate = 20f;
        public float rateDistance;
        public int maxParticles = 2048;
        public long seed;
        public List<Burst> bursts = new ArrayList<>();

        Emission copy() {
            Emission c = new Emission();
            c.loop = loop;
            c.delay = delay;
            c.duration = duration;
            c.rate = rate;
            c.rateDistance = rateDistance;
            c.maxParticles = maxParticles;
            c.seed = seed;
            c.bursts = new ArrayList<>();
            for (Burst burst : bursts) c.bursts.add(burst == null ? new Burst() : burst.copy());
            return c;
        }
    }

    public static final class Burst {
        public float time;
        public int count = 12;
        public int cycles = 1;
        public float interval;
        public float probability = 1f;

        Burst copy() {
            Burst c = new Burst();
            c.time = time;
            c.count = count;
            c.cycles = cycles;
            c.interval = interval;
            c.probability = probability;
            return c;
        }
    }

    public static final class Shape {
        public String type = "point";
        public String emitFrom = "volume";
        public float radius = 1f;
        public float innerRadius;
        public float height = 2f;
        public float angle = 25f;
        public float lineX;
        public float lineY = 2f;
        public float lineZ;
        public float boxX = 1f;
        public float boxY = 1f;
        public float boxZ = 1f;
        public boolean alignDirection;

        Shape copy() {
            Shape c = new Shape();
            c.type = type;
            c.emitFrom = emitFrom;
            c.radius = radius;
            c.innerRadius = innerRadius;
            c.height = height;
            c.angle = angle;
            c.lineX = lineX;
            c.lineY = lineY;
            c.lineZ = lineZ;
            c.boxX = boxX;
            c.boxY = boxY;
            c.boxZ = boxZ;
            c.alignDirection = alignDirection;
            return c;
        }
    }

    public static final class Initial {
        public float lifetimeMin = 1f;
        public float lifetimeMax = 2f;
        public float speedMin = 0.05f;
        public float speedMax = 0.2f;
        public float sizeMin = 0.25f;
        public float sizeMax = 0.5f;
        public float sizeYMin = 0.25f;
        public float sizeYMax = 0.5f;
        public float rotationMin;
        public float rotationMax = 360f;
        public int colorMin = 0xFFFFFFFF;
        public int colorMax = 0xFFFFFFFF;
        public float directionX;
        public float directionY = 1f;
        public float directionZ;
        public float cone = 25f;

        Initial copy() {
            Initial c = new Initial();
            c.lifetimeMin = lifetimeMin;
            c.lifetimeMax = lifetimeMax;
            c.speedMin = speedMin;
            c.speedMax = speedMax;
            c.sizeMin = sizeMin;
            c.sizeMax = sizeMax;
            c.sizeYMin = sizeYMin;
            c.sizeYMax = sizeYMax;
            c.rotationMin = rotationMin;
            c.rotationMax = rotationMax;
            c.colorMin = colorMin;
            c.colorMax = colorMax;
            c.directionX = directionX;
            c.directionY = directionY;
            c.directionZ = directionZ;
            c.cone = cone;
            return c;
        }
    }

    public static final class Motion {
        public float gravityX;
        public float gravityY = -0.04f;
        public float gravityZ;
        public float accelerationX;
        public float accelerationY;
        public float accelerationZ;
        public float drag;
        public float maxSpeed;
        public float attractorStrength;
        public String attractorMode = "none";
        public float attractorX;
        public float attractorY;
        public float attractorZ;
        public float vortex;
        public float noiseStrength;
        public float noiseFrequency = 1f;
        public float noiseScroll = 1f;

        Motion copy() {
            Motion c = new Motion();
            c.gravityX = gravityX;
            c.gravityY = gravityY;
            c.gravityZ = gravityZ;
            c.accelerationX = accelerationX;
            c.accelerationY = accelerationY;
            c.accelerationZ = accelerationZ;
            c.drag = drag;
            c.maxSpeed = maxSpeed;
            c.attractorStrength = attractorStrength;
            c.attractorMode = attractorMode;
            c.attractorX = attractorX;
            c.attractorY = attractorY;
            c.attractorZ = attractorZ;
            c.vortex = vortex;
            c.noiseStrength = noiseStrength;
            c.noiseFrequency = noiseFrequency;
            c.noiseScroll = noiseScroll;
            return c;
        }
    }

    public static final class OverLifetime {
        public Object rotation = 0f;

        OverLifetime copy() {
            OverLifetime c = new OverLifetime();
            c.rotation = rotation;
            return c;
        }
    }

    public static final class Collision {
        public boolean enabled;
        public boolean kill;
        public float bounce = 0.35f;
        public float friction = 0.2f;
        public float lifetimeLoss;

        Collision copy() {
            Collision c = new Collision();
            c.enabled = enabled;
            c.kill = kill;
            c.bounce = bounce;
            c.friction = friction;
            c.lifetimeLoss = lifetimeLoss;
            return c;
        }
    }

    public static final class Trail {
        public boolean enabled;
        public float lifetime = 0.35f;
        public float minDistance = 0.03f;

        Trail copy() {
            Trail c = new Trail();
            c.enabled = enabled;
            c.lifetime = lifetime;
            c.minDistance = minDistance;
            return c;
        }
    }
}
