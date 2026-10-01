package org.zonarstudio.spraute_engine.particle;

import java.util.ArrayList;
import java.util.List;

/**
 * Inline, versioned Particle System data. A PARTICLE node owns this whole spec:
 * no Forge particle registration and no separate particle resource are required.
 */
public final class ParticleSystemSpec {
    public int version = 1;
    public boolean custom = true;

    public Renderer renderer = new Renderer();
    public Emission emission = new Emission();
    public Shape shape = new Shape();
    public Initial initial = new Initial();
    public Motion motion = new Motion();
    public Lifetime lifetime = new Lifetime();
    public Collision collision = new Collision();
    public Trail trail = new Trail();
    public Gameplay gameplay = new Gameplay();
    /** Real, moving block-light carried by this system's live gameplay proxies. */
    public Light light = new Light();
    public List<Burst> bursts = new ArrayList<>();
    /** Nested particle systems spawned on a per-particle lifecycle event (birth/death/collision). */
    public List<SubEmitter> subEmitters = new ArrayList<>();

    public static ParticleSystemSpec defaults() {
        return new ParticleSystemSpec();
    }

    public ParticleSystemSpec harden() {
        if (renderer == null) renderer = new Renderer();
        if (emission == null) emission = new Emission();
        if (shape == null) shape = new Shape();
        if (initial == null) initial = new Initial();
        if (motion == null) motion = new Motion();
        if (lifetime == null) lifetime = new Lifetime();
        if (collision == null) collision = new Collision();
        if (trail == null) trail = new Trail();
        if (gameplay == null) gameplay = new Gameplay();
        if (light == null) light = new Light();
        if (bursts == null) bursts = new ArrayList<>();
        if (subEmitters == null) subEmitters = new ArrayList<>();
        // Particle-as-light needs authoritative server positions — only the gameplay proxy
        // system tracks those, so turning a light on implicitly turns proxies on too.
        if (light.enabled) gameplay.enabled = true;
        if (initial.colorMin == null || initial.colorMin.isBlank()) initial.colorMin = lifetime.colorStart;
        if (initial.colorMax == null || initial.colorMax.isBlank()) initial.colorMax = initial.colorMin;
        if (lifetime.colorEndMin == null || lifetime.colorEndMin.isBlank()) {
            lifetime.colorEndMin = lifetime.colorEnd;
        }
        if (lifetime.colorEndMax == null || lifetime.colorEndMax.isBlank()) {
            lifetime.colorEndMax = lifetime.colorEndMin;
        }
        return this;
    }

    public ParticleSystemSpec copy() {
        harden();
        ParticleSystemSpec c = new ParticleSystemSpec();
        c.version = version;
        c.custom = custom;
        c.renderer = renderer.copy();
        c.emission = emission.copy();
        c.shape = shape.copy();
        c.initial = initial.copy();
        c.motion = motion.copy();
        c.lifetime = lifetime.copy();
        c.collision = collision.copy();
        c.trail = trail.copy();
        c.gameplay = gameplay.copy();
        c.light = light.copy();
        c.bursts = new ArrayList<>();
        for (Burst burst : bursts) c.bursts.add(burst == null ? new Burst() : burst.copy());
        c.subEmitters = new ArrayList<>();
        for (SubEmitter sub : subEmitters) c.subEmitters.add(sub == null ? new SubEmitter() : sub.copy());
        return c;
    }

    public static final class Renderer {
        /** Workspace texture, e.g. textures/particle/spark.png. Also the skin for {@code model} mode. */
        public String texture = "";
        /** billboard | stretched | velocity | axis | cross | mesh | model */
        public String mode = "billboard";
        /** {@code model} mode only: workspace .geo model path (same format as an NPC's model field). */
        public String model = "";
        /** alpha | additive | cutout */
        public String blend = "alpha";
        public boolean fullbright = true;
        public boolean depthTest = true;
        public boolean depthWrite;
        public boolean cull;
        /** RGB multiplier applied to the particle's own color before it hits the blend stage.
         *  1 = unchanged. Most useful with {@code blend = "additive"}, where a brighter,
         *  more saturated color reads as a stronger glow once it stacks onto the scene behind it. */
        public float glowStrength = 1f;
        /** 0 = hard sprite edge (unchanged, zero extra cost). 0..1 fades the sprite's own silhouette
         *  toward transparent from center to rim (a vertex-alpha radial falloff, not a scene-depth
         *  intersection fade), so a billboard reads as a soft round blob instead of a flat rectangle
         *  even with a hard-edged texture. */
        public float softEdge;
        public float stretch = 1f;
        public float pivotX = 0.5f;
        public float pivotY = 0.5f;
        public int sheetColumns = 1;
        public int sheetRows = 1;
        public float sheetFps = 20f;
        public boolean sheetLoop = true;
        public boolean randomStartFrame;

        Renderer copy() {
            Renderer c = new Renderer();
            c.texture = texture;
            c.mode = mode;
            c.model = model;
            c.blend = blend;
            c.fullbright = fullbright;
            c.depthTest = depthTest;
            c.depthWrite = depthWrite;
            c.cull = cull;
            c.glowStrength = glowStrength;
            c.softEdge = softEdge;
            c.stretch = stretch;
            c.pivotX = pivotX;
            c.pivotY = pivotY;
            c.sheetColumns = sheetColumns;
            c.sheetRows = sheetRows;
            c.sheetFps = sheetFps;
            c.sheetLoop = sheetLoop;
            c.randomStartFrame = randomStartFrame;
            return c;
        }
    }

    public static final class Emission {
        public boolean enabled = true;
        public boolean loop = true;
        public boolean prewarm;
        public float delay;
        public float duration = 5f;
        /** Particles per second; 0 keeps legacy burst/interval behavior. */
        public float rate = 20f;
        /** Particles per block travelled by the emitter. */
        public float rateDistance;
        public int maxParticles = 2048;
        public int seed;
        /** local | world */
        public String simulationSpace = "world";

        Emission copy() {
            Emission c = new Emission();
            c.enabled = enabled;
            c.loop = loop;
            c.prewarm = prewarm;
            c.delay = delay;
            c.duration = duration;
            c.rate = rate;
            c.rateDistance = rateDistance;
            c.maxParticles = maxParticles;
            c.seed = seed;
            c.simulationSpace = simulationSpace;
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

    /**
     * A nested particle system spawned at a parent particle's position on one of its
     * lifecycle events. The nested {@link #spec} is a full, independent particle system
     * (its own renderer/shape/initial/motion/…) — e.g. a spark's death sub-emitter can look
     * completely different from the spark itself (an ember puff, a ring of embers, etc).
     */
    public static final class SubEmitter {
        /** birth | death | collision */
        public String trigger = "death";
        /** 0..1 chance to fire, rolled once per triggering event. */
        public float probability = 1f;
        /** Particles spawned per triggering event. */
        public int count = 1;
        /** 0..1 — how much of the parent particle's velocity the sub-burst inherits. */
        public float inheritVelocity = 0f;
        /** The particle system spawned at the trigger point. May itself declare sub-emitters
         *  (depth is capped by the engine to guard against runaway chains). */
        public ParticleSystemSpec spec = new ParticleSystemSpec();

        SubEmitter copy() {
            SubEmitter c = new SubEmitter();
            c.trigger = trigger;
            c.probability = probability;
            c.count = count;
            c.inheritVelocity = inheritVelocity;
            c.spec = spec == null ? new ParticleSystemSpec() : spec.copy();
            return c;
        }
    }

    public static final class Shape {
        /** point | line | box | sphere | shell | circle | disc | cone | cylinder | spiral | text | image */
        public String type = "point";
        public boolean surfaceOnly;
        public float radius = 1f;
        public float innerRadius;
        public float height = 2f;
        public float angle = 25f;
        public float length = 2f;
        public float sizeX = 1f;
        public float sizeY = 1f;
        public float sizeZ = 1f;
        public float lineX;
        public float lineY = 2f;
        public float lineZ;
        /** Local emission direction. */
        public float dirX;
        public float dirY = 1f;
        public float dirZ;
        public float spreadX = 0.1f;
        public float spreadY = 0.1f;
        public float spreadZ = 0.1f;
        /** Literal text sampled into spawn points when {@code type == "text"}. */
        public String text;
        /** Workspace/particle texture path sampled for its alpha mask when {@code type == "image"}. */
        public String image;
        /** Alpha cutoff (0..1) for text/image sampling. */
        public float sampleThreshold = 0.5f;
        /** Sample-grid resolution (longer axis, in cells) for text/image sampling. */
        public int sampleResolution = 48;

        Shape copy() {
            Shape c = new Shape();
            c.type = type;
            c.surfaceOnly = surfaceOnly;
            c.radius = radius;
            c.innerRadius = innerRadius;
            c.height = height;
            c.angle = angle;
            c.length = length;
            c.sizeX = sizeX;
            c.sizeY = sizeY;
            c.sizeZ = sizeZ;
            c.lineX = lineX;
            c.lineY = lineY;
            c.lineZ = lineZ;
            c.dirX = dirX;
            c.dirY = dirY;
            c.dirZ = dirZ;
            c.spreadX = spreadX;
            c.spreadY = spreadY;
            c.spreadZ = spreadZ;
            c.text = text;
            c.image = image;
            c.sampleThreshold = sampleThreshold;
            c.sampleResolution = sampleResolution;
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
        public float aspect = 1f;
        public float rotationMin;
        public float rotationMax = 360f;
        public String colorMin = "#FFFFFFFF";
        public String colorMax = "#FFFFFFFF";
        public float inheritVelocity;

        Initial copy() {
            Initial c = new Initial();
            c.lifetimeMin = lifetimeMin;
            c.lifetimeMax = lifetimeMax;
            c.speedMin = speedMin;
            c.speedMax = speedMax;
            c.sizeMin = sizeMin;
            c.sizeMax = sizeMax;
            c.aspect = aspect;
            c.rotationMin = rotationMin;
            c.rotationMax = rotationMax;
            c.colorMin = colorMin;
            c.colorMax = colorMax;
            c.inheritVelocity = inheritVelocity;
            return c;
        }
    }

    public static final class Motion {
        public float gravityX;
        public float gravityY;
        public float gravityZ;
        public float accelerationX;
        public float accelerationY;
        public float accelerationZ;
        public float drag;
        public float maxSpeed;
        public float angularVelocityMin;
        public float angularVelocityMax;
        public float noiseStrength;
        public float noiseFrequency = 1f;
        public float vortexStrength;
        public float attractorStrength;
        public String attractorTarget = "";

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
            c.angularVelocityMin = angularVelocityMin;
            c.angularVelocityMax = angularVelocityMax;
            c.noiseStrength = noiseStrength;
            c.noiseFrequency = noiseFrequency;
            c.vortexStrength = vortexStrength;
            c.attractorStrength = attractorStrength;
            c.attractorTarget = attractorTarget;
            return c;
        }
    }

    public static final class Lifetime {
        /** linear | ease_in | ease_out | smooth | pulse — shapes the size lerp below. */
        public String sizeCurve = "linear";
        public float sizeStart = 1f;
        public float sizeEnd;
        /** linear | ease_in | ease_out | smooth | pulse — shapes the color/alpha lerp below. */
        public String colorCurve = "linear";
        public String colorStart = "#FFFFFFFF";
        public String colorEnd = "#00FFFFFF";
        /** Random end-color range. Empty values migrate from legacy colorEnd. */
        public String colorEndMin = "";
        public String colorEndMax = "";
        /** linear | ease_in | ease_out | smooth | pulse — shapes the speed-multiplier lerp below. */
        public String speedCurve = "linear";
        public float speedStart = 1f;
        public float speedEnd = 1f;

        Lifetime copy() {
            Lifetime c = new Lifetime();
            c.sizeCurve = sizeCurve;
            c.sizeStart = sizeStart;
            c.sizeEnd = sizeEnd;
            c.colorCurve = colorCurve;
            c.colorStart = colorStart;
            c.colorEnd = colorEnd;
            c.colorEndMin = colorEndMin;
            c.colorEndMax = colorEndMax;
            c.speedCurve = speedCurve;
            c.speedStart = speedStart;
            c.speedEnd = speedEnd;
            return c;
        }
    }

    public static final class Collision {
        public boolean enabled;
        public boolean blocks = true;
        public boolean entities;
        public float radius = 0.05f;
        public float bounce = 0.35f;
        public float friction = 0.2f;
        public boolean killOnCollision;

        Collision copy() {
            Collision c = new Collision();
            c.enabled = enabled;
            c.blocks = blocks;
            c.entities = entities;
            c.radius = radius;
            c.bounce = bounce;
            c.friction = friction;
            c.killOnCollision = killOnCollision;
            return c;
        }
    }

    public static final class Trail {
        public boolean enabled;
        public float lifetime = 0.35f;
        public float widthStart = 1f;
        public float widthEnd;
        public int maxPoints = 16;

        Trail copy() {
            Trail c = new Trail();
            c.enabled = enabled;
            c.lifetime = lifetime;
            c.widthStart = widthStart;
            c.widthEnd = widthEnd;
            c.maxPoints = maxPoints;
            return c;
        }
    }

    public static final class Gameplay {
        public boolean enabled;
        public int maxProxies = 128;
        public float radius = 0.15f;
        public boolean collideBlocks;
        public boolean collideEntities = true;
        public String tag = "";

        Gameplay copy() {
            Gameplay c = new Gameplay();
            c.enabled = enabled;
            c.maxProxies = maxProxies;
            c.radius = radius;
            c.collideBlocks = collideBlocks;
            c.collideEntities = collideEntities;
            c.tag = tag;
            return c;
        }
    }

    /**
     * Particle-as-light: up to {@link #maxLights} of this system's live gameplay proxies
     * (see {@link Gameplay}) each carry a real, moving vanilla block-light
     * ({@code minecraft:light}, white only, 0..15) — the same placement primitive static
     * LIGHT scene nodes use. Capped and TTL-based (see {@code ParticleLightRuntime}), not
     * exactly reconciled: a light lags its particle by a fraction of a second and fades out
     * shortly after the particle dies or the light is turned off, rather than tracking exact
     * particle-to-block ownership every tick.
     */
    public static final class Light {
        public boolean enabled;
        /** Vanilla block-light level, 0..15 (white only — same limitation as the LIGHT node type). */
        public int level = 10;
        /** How many of this system's live proxies get a real light block at once (capped, costly). */
        public int maxLights = 1;

        Light copy() {
            Light c = new Light();
            c.enabled = enabled;
            c.level = level;
            c.maxLights = maxLights;
            return c;
        }
    }
}
