package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.network.BoneParticlePacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.particle.ParticleSystemSpec;
import org.zonarstudio.spraute_engine.scene.SceneActiveState;
import org.zonarstudio.spraute_engine.scene.SceneParticleEmitter;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;
import org.zonarstudio.spraute_engine.script.SceneScriptSpawn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Script primitives for both vanilla particle ids and custom PNG textures.
 * Extra trailing args on any shape: lifetime, size, color (custom engine only).
 */
public class ParticleFunctions {
    private static final int CUSTOM_SPEC_CACHE_CAP = 64;
    private static final Map<CustomSpecKey, ParticleSystemSpec> CUSTOM_SPECS =
            new LinkedHashMap<>(CUSTOM_SPEC_CACHE_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CustomSpecKey, ParticleSystemSpec> eldest) {
                    return size() > CUSTOM_SPEC_CACHE_CAP;
                }
            };

    private record CustomSpecKey(String texture, int lifetime, int size, String color, int capacity) {}

    private static ParticleOptions getParticle(String id) {
        ResourceLocation rl = new ResourceLocation(id.contains(":") ? id : "minecraft:" + id);
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        return type instanceof ParticleOptions options ? options : null;
    }

    private static double getDouble(Object obj) {
        return obj instanceof Number num ? num.doubleValue() : 0.0;
    }

    private static float getFloat(Object obj) {
        return obj instanceof Number num ? num.floatValue() : 0.0f;
    }

    private static int getInt(Object obj) {
        return obj instanceof Number num ? num.intValue() : 0;
    }

    public static class Spawn implements ScriptFunction {
        @Override public String getName() { return "particleSpawn"; }
        @Override public int getArgCount() { return 9; }
        @Override public Class<?>[] getArgTypes() { return types(9); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = source.getLevel();
            String id = str(args.get(0));
            double x = getDouble(args.get(1)), y = getDouble(args.get(2)), z = getDouble(args.get(3));
            int count = Math.max(1, getInt(args.get(4)));
            if (!isTexture(id)) {
                ParticleOptions opt = getParticle(id);
                if (opt != null) {
                    level.sendParticles(opt, x, y, z, count,
                            getDouble(args.get(5)), getDouble(args.get(6)), getDouble(args.get(7)), getDouble(args.get(8)));
                }
                return null;
            }
            Points points = new Points(count);
            for (int i = 0; i < count; i++) points.add(x, y, z);
            emit(level, id, points, args, 5);
            return null;
        }
    }

    public static class Line implements ScriptFunction {
        @Override public String getName() { return "particleLine"; }
        @Override public int getArgCount() { return 12; }
        @Override public Class<?>[] getArgTypes() { return types(12); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            emit(source.getLevel(), str(args.get(0)), line(
                    getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    getDouble(args.get(4)), getDouble(args.get(5)), getDouble(args.get(6)),
                    getInt(args.get(7))), args, 8);
            return null;
        }
    }

    public static class Circle implements ScriptFunction {
        @Override public String getName() { return "particleCircle"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return types(10); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            emit(source.getLevel(), str(args.get(0)), ring(
                    getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    getDouble(args.get(4)), Math.max(1, getInt(args.get(5))), 0, 360), args, 6);
            return null;
        }
    }

    public static class Ring implements ScriptFunction {
        @Override public String getName() { return "particleRing"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return types(10); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new Circle().execute(args, source, context);
        }
    }

    public static class Spiral implements ScriptFunction {
        @Override public String getName() { return "particleSpiral"; }
        @Override public int getArgCount() { return 11; }
        @Override public Class<?>[] getArgTypes() { return types(11); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = getDouble(args.get(4)), height = getDouble(args.get(5));
            int count = Math.max(1, getInt(args.get(6)));
            Points points = new Points(count);
            for (int i = 0; i < count; i++) {
                double t = count == 1 ? 0 : (double) i / (count - 1);
                double angle = 4 * Math.PI * t;
                points.add(cx + radius * Math.cos(angle), cy + height * t, cz + radius * Math.sin(angle));
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 7);
            return null;
        }
    }

    public static class Helix implements ScriptFunction {
        @Override public String getName() { return "particleHelix"; }
        @Override public int getArgCount() { return 11; }
        @Override public Class<?>[] getArgTypes() { return types(11); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return new Spiral().execute(args, source, context);
        }
    }

    /** Volume box around center (x,y,z) with size w,h,d. */
    public static class Box implements ScriptFunction {
        @Override public String getName() { return "particleBox"; }
        @Override public int getArgCount() { return 12; }
        @Override public Class<?>[] getArgTypes() { return types(12); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double x = getDouble(args.get(1)), y = getDouble(args.get(2)), z = getDouble(args.get(3));
            double w = Math.abs(getDouble(args.get(4))), h = Math.abs(getDouble(args.get(5))), d = Math.abs(getDouble(args.get(6)));
            int count = Math.max(1, getInt(args.get(7)));
            int side = Math.max(1, (int) Math.ceil(Math.cbrt(count)));
            Points points = new Points(side * side * side);
            for (int ix = 0; ix < side && points.n < count; ix++) {
                for (int iy = 0; iy < side && points.n < count; iy++) {
                    for (int iz = 0; iz < side && points.n < count; iz++) {
                        double u = side == 1 ? 0.5 : ix / (double) (side - 1);
                        double v = side == 1 ? 0.5 : iy / (double) (side - 1);
                        double t = side == 1 ? 0.5 : iz / (double) (side - 1);
                        points.add(x + (u - 0.5) * w, y + (v - 0.5) * h, z + (t - 0.5) * d);
                    }
                }
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 8);
            return null;
        }
    }

    /** 12 edges of a box. */
    public static class BoxWire implements ScriptFunction {
        @Override public String getName() { return "particleBoxWire"; }
        @Override public int getArgCount() { return 12; }
        @Override public Class<?>[] getArgTypes() { return types(12); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double x = getDouble(args.get(1)), y = getDouble(args.get(2)), z = getDouble(args.get(3));
            double hw = Math.abs(getDouble(args.get(4))) * 0.5;
            double hh = Math.abs(getDouble(args.get(5))) * 0.5;
            double hd = Math.abs(getDouble(args.get(6))) * 0.5;
            int per = Math.max(2, getInt(args.get(7)));
            double[] xs = {x - hw, x + hw};
            double[] ys = {y - hh, y + hh};
            double[] zs = {z - hd, z + hd};
            Points points = new Points(per * 12);
            for (double yv : ys) for (double zv : zs) addLine(points, xs[0], yv, zv, xs[1], yv, zv, per);
            for (double xv : xs) for (double zv : zs) addLine(points, xv, ys[0], zv, xv, ys[1], zv, per);
            for (double xv : xs) for (double yv : ys) addLine(points, xv, yv, zs[0], xv, yv, zs[1], per);
            emit(source.getLevel(), str(args.get(0)), points, args, 8);
            return null;
        }
    }

    public static class Sphere implements ScriptFunction {
        @Override public String getName() { return "particleSphere"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return types(10); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = Math.max(0, getDouble(args.get(4)));
            int count = Math.max(1, getInt(args.get(5)));
            int shells = Math.max(1, (int) Math.round(Math.cbrt(count)));
            int per = Math.max(1, count / shells);
            Points points = new Points(count + per);
            for (int s = 1; s <= shells && points.n < count; s++) {
                double r = radius * s / shells;
                int n = s == shells ? Math.max(1, count - points.n) : per;
                fibonacci(points, cx, cy, cz, r, n);
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 6);
            return null;
        }
    }

    public static class Shell implements ScriptFunction {
        @Override public String getName() { return "particleShell"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return types(10); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            Points points = new Points(Math.max(1, getInt(args.get(5))));
            fibonacci(points, getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    Math.max(0, getDouble(args.get(4))), Math.max(1, getInt(args.get(5))));
            emit(source.getLevel(), str(args.get(0)), points, args, 6);
            return null;
        }
    }

    public static class Disc implements ScriptFunction {
        @Override public String getName() { return "particleDisc"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return types(10); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = Math.max(0, getDouble(args.get(4)));
            int count = Math.max(1, getInt(args.get(5)));
            Points points = new Points(count);
            double golden = Math.PI * (3 - Math.sqrt(5));
            for (int i = 0; i < count; i++) {
                double r = radius * Math.sqrt(count == 1 ? 0 : i / (double) (count - 1));
                double a = i * golden;
                points.add(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 6);
            return null;
        }
    }

    /**
     * Cone from origin along (dirX,dirY,dirZ). {@code angle} is half-angle in degrees,
     * {@code length} is height.
     */
    public static class Cone implements ScriptFunction {
        @Override public String getName() { return "particleCone"; }
        @Override public int getArgCount() { return 14; }
        @Override public Class<?>[] getArgTypes() { return types(14); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double ox = getDouble(args.get(1)), oy = getDouble(args.get(2)), oz = getDouble(args.get(3));
            double[] axis = {getDouble(args.get(4)), getDouble(args.get(5)), getDouble(args.get(6))};
            double length = Math.max(0.01, getDouble(args.get(7)));
            double angle = Math.toRadians(Math.max(0, getDouble(args.get(8))));
            int count = Math.max(1, getInt(args.get(9)));
            int rings = Math.max(2, (int) Math.round(Math.sqrt(count)));
            int per = Math.max(3, count / rings);
            Points points = new Points(count + rings);
            for (int r = 0; r < rings && points.n < count; r++) {
                double t = r / (double) (rings - 1);
                double h = t * length;
                double rad = Math.tan(angle) * h;
                int n = r == 0 ? 1 : Math.min(per, count - points.n);
                if (r == rings - 1) n = Math.max(1, count - points.n);
                for (int i = 0; i < n; i++) {
                    double a = n == 1 ? 0 : (Math.PI * 2 * i) / n;
                    double[] p = orient(axis, Math.cos(a) * rad, h, Math.sin(a) * rad);
                    points.add(ox + p[0], oy + p[1], oz + p[2]);
                }
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 10);
            return null;
        }
    }

    public static class Cylinder implements ScriptFunction {
        @Override public String getName() { return "particleCylinder"; }
        @Override public int getArgCount() { return 11; }
        @Override public Class<?>[] getArgTypes() { return types(11); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = Math.max(0, getDouble(args.get(4)));
            double height = getDouble(args.get(5));
            int count = Math.max(1, getInt(args.get(6)));
            int rings = Math.max(2, (int) Math.round(Math.sqrt(count)));
            int per = Math.max(3, count / rings);
            Points points = new Points(count + rings);
            for (int r = 0; r < rings && points.n < count; r++) {
                double t = r / (double) (rings - 1);
                int n = r == rings - 1 ? Math.max(1, count - points.n) : Math.min(per, count - points.n);
                for (int i = 0; i < n; i++) {
                    double a = (Math.PI * 2 * i) / n;
                    points.add(cx + Math.cos(a) * radius, cy + t * height, cz + Math.sin(a) * radius);
                }
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 7);
            return null;
        }
    }

    /** Arc in XZ around Y, {@code startDeg}..{@code endDeg}. */
    public static class Arc implements ScriptFunction {
        @Override public String getName() { return "particleArc"; }
        @Override public int getArgCount() { return 12; }
        @Override public Class<?>[] getArgTypes() { return types(12); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            emit(source.getLevel(), str(args.get(0)), ring(
                    getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    getDouble(args.get(4)), Math.max(2, getInt(args.get(7))),
                    getDouble(args.get(5)), getDouble(args.get(6))), args, 8);
            return null;
        }
    }

    public static class Grid implements ScriptFunction {
        @Override public String getName() { return "particleGrid"; }
        @Override public int getArgCount() { return 11; }
        @Override public Class<?>[] getArgTypes() { return types(11); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double w = Math.abs(getDouble(args.get(4))), d = Math.abs(getDouble(args.get(5)));
            double step = Math.max(0.1, getDouble(args.get(6)));
            int nx = Math.max(1, (int) Math.round(w / step) + 1);
            int nz = Math.max(1, (int) Math.round(d / step) + 1);
            Points points = new Points(nx * nz);
            for (int ix = 0; ix < nx; ix++) {
                for (int iz = 0; iz < nz; iz++) {
                    double u = nx == 1 ? 0.5 : ix / (double) (nx - 1);
                    double v = nz == 1 ? 0.5 : iz / (double) (nz - 1);
                    points.add(cx + (u - 0.5) * w, cy, cz + (v - 0.5) * d);
                }
            }
            emit(source.getLevel(), str(args.get(0)), points, args, 7);
            return null;
        }
    }

    public static class Triangle implements ScriptFunction {
        @Override public String getName() { return "particleTriangle"; }
        @Override public int getArgCount() { return 15; }
        @Override public Class<?>[] getArgTypes() { return types(15); }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            int per = Math.max(2, getInt(args.get(10)));
            Points points = new Points(per * 3);
            addLine(points, getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    getDouble(args.get(4)), getDouble(args.get(5)), getDouble(args.get(6)), per);
            addLine(points, getDouble(args.get(4)), getDouble(args.get(5)), getDouble(args.get(6)),
                    getDouble(args.get(7)), getDouble(args.get(8)), getDouble(args.get(9)), per);
            addLine(points, getDouble(args.get(7)), getDouble(args.get(8)), getDouble(args.get(9)),
                    getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)), per);
            emit(source.getLevel(), str(args.get(0)), points, args, 11);
            return null;
        }
    }

    public static class StartBone implements ScriptFunction {
        @Override public String getName() { return "particleStartBone"; }
        @Override public int getArgCount() { return 9; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class, String.class, String.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String taskId = (String) args.get(0);
            String npcNameOrUuid = (String) args.get(1);
            String boneName = (String) args.get(2);
            String particleType = (String) args.get(3);
            int count = getInt(args.get(4));
            float dx = getFloat(args.get(5));
            float dy = getFloat(args.get(6));
            float dz = getFloat(args.get(7));
            float speed = getFloat(args.get(8));

            Entity entity = null;
            try {
                UUID uuid = UUID.fromString(npcNameOrUuid);
                entity = source.getLevel().getEntity(uuid);
            } catch (Exception e) {
                entity = NpcManager.getEntity(npcNameOrUuid, source.getLevel());
            }

            if (entity != null) {
                BoneParticlePacket pkt = new BoneParticlePacket(0, taskId, entity.getId(), boneName, particleType, count, dx, dy, dz, speed);
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
            }
            return taskId;
        }
    }

    public static class StopBone implements ScriptFunction {
        @Override public String getName() { return "particleStopBone"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String taskId = (String) args.get(0);
            BoneParticlePacket pkt = new BoneParticlePacket(1, taskId, 0, "", "", 0, 0, 0, 0, 0);
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
            return null;
        }
    }

    static boolean isTexture(String id) {
        if (id == null || id.isBlank()) return false;
        String value = id.trim().replace('\\', '/');
        return value.endsWith(".png") || value.contains("/") || value.contains("textures");
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Class<?>[] types(int count) {
        Class<?>[] out = new Class<?>[count];
        out[0] = String.class;
        for (int i = 1; i < count; i++) out[i] = Object.class;
        return out;
    }

    private static List<Object> extra(List<Object> args, int from) {
        if (args == null || args.size() <= from) return List.of();
        return args.subList(from, args.size());
    }

    /** {@code motionFrom} = index of dx,dy,dz,speed. Remaining args are custom lifetime/size/color. */
    private static void emit(ServerLevel level, String id, Points points, List<Object> args, int motionFrom) {
        if (level == null || points == null || points.n <= 0) return;
        double dx = getDouble(args.get(motionFrom));
        double dy = getDouble(args.get(motionFrom + 1));
        double dz = getDouble(args.get(motionFrom + 2));
        double speed = getDouble(args.get(motionFrom + 3));
        List<Object> rest = extra(args, motionFrom + 4);
        if (isTexture(id)) {
            float[] data = new float[points.n * 6];
            for (int i = 0, r = 0, w = 0; i < points.n; i++) {
                data[w++] = points.xyz[r++];
                data[w++] = points.xyz[r++];
                data[w++] = points.xyz[r++];
                data[w++] = (float) (dx * speed);
                data[w++] = (float) (dy * speed);
                data[w++] = (float) (dz * speed);
            }
            emitCustom(level, id, data, rest);
            return;
        }
        ParticleOptions opt = getParticle(id);
        if (opt == null) return;
        for (int i = 0, r = 0; i < points.n; i++) {
            level.sendParticles(opt, points.xyz[r++], points.xyz[r++], points.xyz[r++], 1, dx, dy, dz, speed);
        }
    }

    private static void emitCustom(ServerLevel level, String texture, float[] data, List<Object> extra) {
        if (level == null || data == null || data.length < 6) return;
        float lifetime = extra.size() > 0 ? Math.max(0.05f, getFloat(extra.get(0))) : 1f;
        float size = extra.size() > 1 ? Math.max(0.001f, getFloat(extra.get(1))) : 0.12f;
        String color = extra.size() > 2 && extra.get(2) != null ? String.valueOf(extra.get(2)) : "#FFFFFFFF";
        // Optional 4th extra arg: a stable id. Same id across calls re-fires/updates the
        // same PARTICLE scene node instead of spawning a new one (e.g. a per-tick effect).
        String key = extra.size() > 3 && extra.get(3) != null ? String.valueOf(extra.get(3)).trim() : null;
        // Consecutive primitives with the same texture share one client system
        // (for example all lines of the stealth fan), so this must not be sized
        // from a single packet.
        int capacity = 4096;
        ParticleSystemSpec cached = cachedCustomSpec(texture, lifetime, size, color, capacity);

        SceneActiveState state = ScriptExecutor.currentSceneState();
        if (state != null) {
            // The cached spec instance is shared across every call with the same
            // texture/lifetime/size/color; a scene node owns its particleSystem long-term
            // (an inspector could edit it later), so it must not alias the shared cache entry.
            ParticleSystemSpec nodeSpec = cached.copy();
            // Root itself only accepts PLAYERS/GLOBAL_WORLD/LOCAL_WORLD since the worlds split —
            // a script-fired burst with no scene node context lands in the shared world by default.
            String parentId = state.document.ensureGlobalWorldNode().id;
            SceneScriptSpawn.fireScriptParticleBurst(level, state, parentId, key, nodeSpec, data);
            return;
        }
        // No active scene (console/global script context) — nothing to attach a node to,
        // keep the old fire-and-forget network burst.
        SceneParticleEmitter.emitCustomPoints(level, "script:" + texture, cached, data);
    }

    private static ParticleSystemSpec cachedCustomSpec(String texture, float lifetime, float size,
                                                       String color, int capacity) {
        CustomSpecKey key = new CustomSpecKey(texture, Float.floatToIntBits(lifetime),
                Float.floatToIntBits(size), color, capacity);
        synchronized (CUSTOM_SPECS) {
            ParticleSystemSpec cached = CUSTOM_SPECS.get(key);
            if (cached != null) return cached;
            ParticleSystemSpec spec = ParticleSystemSpec.defaults();
            spec.renderer.texture = texture;
            spec.renderer.fullbright = true;
            spec.emission.loop = false;
            spec.emission.rate = 0f;
            spec.emission.maxParticles = capacity;
            spec.initial.lifetimeMin = lifetime;
            spec.initial.lifetimeMax = lifetime;
            spec.initial.speedMin = 0f;
            spec.initial.speedMax = 0f;
            spec.initial.sizeMin = size;
            spec.initial.sizeMax = size;
            spec.initial.colorMin = color;
            spec.initial.colorMax = color;
            spec.lifetime.sizeStart = 1f;
            spec.lifetime.sizeEnd = 1f;
            spec.lifetime.colorStart = color;
            spec.lifetime.colorEnd = color;
            spec.lifetime.colorEndMin = color;
            spec.lifetime.colorEndMax = color;
            spec.motion.gravityY = 0f;
            CUSTOM_SPECS.put(key, spec);
            return spec;
        }
    }

    private static Points line(double x1, double y1, double z1, double x2, double y2, double z2, int count) {
        int steps = Math.max(0, count);
        Points points = new Points(steps + 1);
        addLine(points, x1, y1, z1, x2, y2, z2, steps);
        return points;
    }

    private static void addLine(Points points, double x1, double y1, double z1,
                                double x2, double y2, double z2, int count) {
        int steps = Math.max(0, count);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            points.add(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t);
        }
    }

    private static Points ring(double cx, double cy, double cz, double radius, int count, double startDeg, double endDeg) {
        int n = Math.max(1, count);
        Points points = new Points(n);
        double start = Math.toRadians(startDeg);
        double span = Math.toRadians(endDeg - startDeg);
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : i / (double) (n - (Math.abs(endDeg - startDeg) >= 359.9 ? 0 : 1));
            if (Math.abs(endDeg - startDeg) >= 359.9) t = i / (double) n;
            double a = start + span * t;
            points.add(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius);
        }
        return points;
    }

    private static void fibonacci(Points points, double cx, double cy, double cz, double radius, int count) {
        int n = Math.max(1, count);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = n == 1 ? 0 : 1 - (i / (double) (n - 1)) * 2;
            double r = Math.sqrt(Math.max(0, 1 - y * y)) * radius;
            double a = golden * i;
            points.add(cx + Math.cos(a) * r, cy + y * radius, cz + Math.sin(a) * r);
        }
    }

    /** Map local (x, along, z) onto world axis. */
    private static double[] orient(double[] axis, double lx, double along, double lz) {
        double[] a = {axis[0], axis[1], axis[2]};
        double len = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (len < 1e-8) {
            a[0] = 0;
            a[1] = 1;
            a[2] = 0;
            len = 1;
        }
        a[0] /= len;
        a[1] /= len;
        a[2] /= len;
        double[] tmp = Math.abs(a[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = {
                tmp[1] * a[2] - tmp[2] * a[1],
                tmp[2] * a[0] - tmp[0] * a[2],
                tmp[0] * a[1] - tmp[1] * a[0]
        };
        double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        if (ul < 1e-8) u = new double[]{1, 0, 0};
        else {
            u[0] /= ul;
            u[1] /= ul;
            u[2] /= ul;
        }
        double[] v = {
                a[1] * u[2] - a[2] * u[1],
                a[2] * u[0] - a[0] * u[2],
                a[0] * u[1] - a[1] * u[0]
        };
        return new double[]{
                u[0] * lx + a[0] * along + v[0] * lz,
                u[1] * lx + a[1] * along + v[1] * lz,
                u[2] * lx + a[2] * along + v[2] * lz
        };
    }

    private static final class Points {
        float[] xyz;
        int n;

        Points(int cap) {
            xyz = new float[Math.max(1, cap) * 3];
        }

        void add(double x, double y, double z) {
            if (n * 3 + 3 > xyz.length) {
                float[] next = new float[xyz.length * 2];
                System.arraycopy(xyz, 0, next, 0, n * 3);
                xyz = next;
            }
            int i = n * 3;
            xyz[i] = (float) x;
            xyz[i + 1] = (float) y;
            xyz[i + 2] = (float) z;
            n++;
        }
    }
}
