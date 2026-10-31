package org.sprautestudio.spraute_engine.fx.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.fx.FxDef;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.Priority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plays effects on the client: emits and moves the particles every frame and draws them as
 * camera-facing, stretched, lying or beam sprites in one batch per texture and blend; screen
 * flashes and camera shake on top. Effects follow an entity when played with one.
 */
public final class FxEngine {
    public static void registerHooks() {
        ClientHooks.RENDER_LEVEL.add(FxEngine::onRenderLevel);
        ClientHooks.CAMERA_ANGLES.add(Priority.LOWEST, FxEngine::onAnglesFinal);
        ClientHooks.RENDER_GUI.add(FxEngine::onGui);
        ClientHooks.CAMERA_ANGLES.add(Priority.LOW, FxEngine::onAngles);
        ClientHooks.LOGGING_OUT.add(FxEngine::onLogout);
    }

    private FxEngine() {
    }

    /** Hard cap on particles alive in all effects. */
    private static final int MAX_TOTAL = 40000;
    private static final Random RND = new Random();
    private static final List<Inst> INSTANCES = new ArrayList<>();
    private static long lastFrame = System.nanoTime();
    private static int total;

    /** One live particle. Position is relative to the effect's centre for local layers. */
    private static final class P {
        double x, y, z, vx, vy, vz;
        float age, life, sizeMul, rot, spin, seed;
        float dx, dy, dz;
        int color = -1;
    }

    private static final class LayerState {
        final FxDef.Layer l;
        final int[] colors;
        final List<P> ps = new ArrayList<>();
        float accum;
        double nextBurst;
        boolean burstDone;

        /** The layer's turn (null — none) and the axis its particles orbit around. */
        final org.joml.Quaternionf rot;
        final Vector3f axis;

        LayerState(FxDef.Layer l) {
            this.l = l;
            if (l.rotX != 0 || l.rotY != 0 || l.rotZ != 0) {
                rot = new org.joml.Quaternionf().rotateY((float) Math.toRadians(l.rotY)).rotateX((float) Math.toRadians(l.rotX))
                        .rotateZ((float) Math.toRadians(l.rotZ));
                axis = rot.transform(new Vector3f(0, 1, 0));
            } else {
                rot = null;
                axis = null;
            }
            colors = new int[l.color.length];
            for (int i = 0; i < colors.length; i++) colors[i] = FxDef.parseColor(l.color[i]);
        }
    }

    private static final class Inst {
        final String id, project;
        /** The story that played it (-1 — the editor). */
        int scriptId = -1;
        final FxDef def;
        final float scale;
        final int entity;
        double ox, oy, oz;
        /** Flying (a cast): speed per tick, the drop per tick, how long it may fly at most (s). */
        double vx, vy, vz, g;
        boolean moving;
        float flyLeft;
        double t;
        boolean stopping;
        float fade = 1;
        final List<LayerState> layers = new ArrayList<>();

        Inst(String id, String project, FxDef def, double x, double y, double z, int entity, float scale) {
            this.id = id;
            this.project = project;
            this.def = def;
            this.ox = x;
            this.oy = y;
            this.oz = z;
            this.entity = entity;
            this.scale = scale;
            for (FxDef.Layer l : def.layers) if (l.enabled) layers.add(new LayerState(l));
        }

        boolean finished() {
            if (stopping && fade <= 0) return true;
            if (def.duration <= 0 || t < def.duration) return false;
            for (LayerState s : layers) {
                if (!s.ps.isEmpty()) return false;
                if (!s.l.type.equals("particles") && t < s.l.delay + layerTime(s.l, def)) return false;
            }
            return true;
        }
    }

    private static float layerTime(FxDef.Layer l, FxDef d) {
        if (l.time > 0) return l.time;
        return d.duration > 0 ? Math.max(0, d.duration - l.delay) : Float.MAX_VALUE;
    }

    // ================================================================== API

    /** Starts an effect; {@code entity} (or -1) is followed. Same id replaces the running one. */
    public static void play(String id, String project, FxDef def, double x, double y, double z, int entity, float scale) {
        play(id, project, def, x, y, z, entity, scale, -1);
    }

    public static void play(String id, String project, FxDef def, double x, double y, double z, int entity, float scale, int scriptId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (id != null && !id.isEmpty()) stop(id, scriptId, true);
        Inst in = new Inst(id == null ? "" : id, project, def, x, y, z, entity, Math.max(0.01f, scale));
        in.scriptId = scriptId;
        follow(in, 1);
        INSTANCES.add(in);
        if (def.sound != null && !def.sound.isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(def.sound.contains(":") ? def.sound : "minecraft:" + def.sound);
            if (rl != null) {
                mc.level.playLocalSound(in.ox, in.oy, in.oz, SoundEvent.createVariableRangeEvent(rl), SoundSource.AMBIENT,
                        def.soundVolume, def.soundPitch, false);
            }
        }
    }

    /** An effect that flies from a point (a cast): the server says when it hits. */
    public static void launch(String id, String project, FxDef def, double x, double y, double z, double vx, double vy, double vz, double g,
                              float scale, int scriptId) {
        play(id, project, def, x, y, z, -1, scale, scriptId);
        if (INSTANCES.isEmpty()) return;
        Inst in = INSTANCES.get(INSTANCES.size() - 1);
        in.vx = vx;
        in.vy = vy;
        in.vz = vz;
        in.g = g;
        in.moving = true;
        in.flyLeft = 30;
    }

    /** Stops effects with this id ("" — all); they fade out, or vanish at once. */
    public static void stop(String id, boolean now) {
        stop(id, -1, now);
    }

    /** As above, only the effects of one story (scriptId -1 — of any). */
    public static void stop(String id, int scriptId, boolean now) {
        for (Iterator<Inst> it = INSTANCES.iterator(); it.hasNext(); ) {
            Inst in = it.next();
            if (scriptId >= 0 && in.scriptId != scriptId) continue;
            if (id == null || id.isEmpty() || in.id.equals(id)) {
                if (now) {
                    for (LayerState s : in.layers) total -= s.ps.size();
                    it.remove();
                } else in.stopping = true;
            }
        }
    }

    public static boolean running(String id) {
        for (Inst in : INSTANCES) if (in.id.equals(id) && !in.stopping) return true;
        return false;
    }

    public static int particles() {
        return total;
    }

    // ================================================================== simulation

    private static void follow(Inst in, float partial) {
        if (in.entity < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level != null ? mc.level.getEntity(in.entity) : null;
        if (e == null) return;
        Vec3 p = e.getPosition(partial);
        in.ox = p.x;
        in.oy = p.y;
        in.oz = p.z;
    }

    private static float rnd(float spread) {
        return (RND.nextFloat() * 2 - 1) * spread;
    }

    private static void emit(Inst in, LayerState s, int n) {
        FxDef.Layer l = s.l;
        float sc = in.scale;
        double evenStart = RND.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n && s.ps.size() < l.max && total < MAX_TOTAL; i++) {
            P p = new P();
            // where on the shape
            double px = 0, py = 0, pz = 0;
            float r = l.radius * sc, ri = Math.min(l.radiusInner, l.radius) * sc;
            switch (l.shape) {
                case "sphere", "shell" -> {
                    Vector3f d = randomUnit();
                    double rr = l.shape.equals("shell") ? r : Math.cbrt(ri * ri * ri + RND.nextDouble() * (r * r * r - ri * ri * ri));
                    px = d.x * rr;
                    py = d.y * rr;
                    pz = d.z * rr;
                }
                case "disc", "column" -> {
                    double a = RND.nextDouble() * Math.PI * 2, rr = Math.sqrt(ri * ri + RND.nextDouble() * (r * r - ri * ri));
                    px = Math.cos(a) * rr;
                    pz = Math.sin(a) * rr;
                    if (l.shape.equals("column")) py = RND.nextDouble() * l.height * sc;
                }
                case "ring" -> {
                    double a = l.even ? evenStart + Math.PI * 2 * i / n : RND.nextDouble() * Math.PI * 2;
                    px = Math.cos(a) * r;
                    pz = Math.sin(a) * r;
                }
                case "box" -> {
                    px = rnd(r);
                    py = RND.nextDouble() * l.height * sc;
                    pz = rnd(r);
                }
                case "line" -> py = RND.nextDouble() * l.height * sc;
                default -> {
                }
            }
            // which way
            Vector3f dir;
            switch (l.direction) {
                case "up", "axis" -> dir = new Vector3f(0, 1, 0);
                case "down" -> dir = new Vector3f(0, -1, 0);
                case "random" -> dir = randomUnit();
                case "none" -> dir = new Vector3f();
                case "tangent" -> {
                    dir = new Vector3f((float) -pz, 0, (float) px);
                    if (dir.lengthSquared() < 1e-6) dir = randomUnit();
                    dir.normalize();
                }
                case "cone" -> dir = cone(l.angle);
                default -> {
                    if (l.shape.equals("cone")) dir = cone(l.angle);
                    else {
                        boolean flat = l.shape.equals("disc") || l.shape.equals("ring") || l.shape.equals("column");
                        dir = new Vector3f((float) px, flat ? 0 : (float) py, (float) pz);
                        if (dir.lengthSquared() < 1e-6) dir = randomUnit();
                        dir.normalize();
                    }
                }
            }
            if (s.rot != null) {
                Vector3f turned = s.rot.transform(new Vector3f((float) px, (float) py, (float) pz));
                px = turned.x;
                py = turned.y;
                pz = turned.z;
                dir = s.rot.transform(new Vector3f(dir));
            }
            float sp = l.speed * sc * (1 + rnd(l.speedRandom));
            p.x = px + l.offsetX * sc;
            p.y = py + l.offsetY * sc;
            p.z = pz + l.offsetZ * sc;
            if (!l.local) {
                p.x += in.ox;
                p.y += in.oy;
                p.z += in.oz;
            }
            p.vx = dir.x * sp;
            p.vy = dir.y * sp;
            p.vz = dir.z * sp;
            Vector3f axis = l.direction.equals("none") || dir.lengthSquared() < 1e-6 ? new Vector3f(0, 1, 0) : dir;
            p.dx = axis.x;
            p.dy = axis.y;
            p.dz = axis.z;
            p.life = Math.max(0.02f, l.life * (1 + rnd(l.lifeRandom)));
            p.sizeMul = 1 + rnd(l.sizeRandom);
            p.rot = RND.nextFloat() * 360;
            p.spin = l.spin + rnd(l.spinRandom);
            p.seed = RND.nextFloat() * 1000;
            if (l.colorRandom) p.color = s.colors[RND.nextInt(s.colors.length)];
            s.ps.add(p);
            total++;
        }
    }

    private static Vector3f randomUnit() {
        double z = RND.nextDouble() * 2 - 1, a = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
        return new Vector3f((float) (r * Math.cos(a)), (float) z, (float) (r * Math.sin(a)));
    }

    private static Vector3f cone(float angle) {
        double c = Math.cos(Math.toRadians(Math.max(0, Math.min(180, angle))));
        double z = c + RND.nextDouble() * (1 - c), a = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(Math.max(0, 1 - z * z));
        return new Vector3f((float) (r * Math.cos(a)), (float) z, (float) (r * Math.sin(a)));
    }

    private static float wave(float seed, float t) {
        return (float) (Math.sin(t * 1.7 + seed) * 0.6 + Math.sin(t * 3.1 + seed * 1.3) * 0.4);
    }

    private static void update(Inst in, float dt) {
        in.t += dt;
        if (in.moving && !in.stopping) {
            in.ox += in.vx * 20 * dt;
            in.oy += in.vy * 20 * dt;
            in.oz += in.vz * 20 * dt;
            in.vy -= in.g * 20 * dt;
            in.flyLeft -= dt;
            if (in.flyLeft <= 0) in.stopping = true;
        }
        if (in.stopping) {
            in.fade -= dt / 0.6f;
            // a fading effect makes no new particles and its old ones go quickly
            for (LayerState s : in.layers) for (P p : s.ps) p.life = Math.min(p.life, p.age + 0.6f);
        }
        float sc = in.scale;
        for (LayerState s : in.layers) {
            FxDef.Layer l = s.l;
            double lt = in.t - l.delay;
            float lim = layerTime(l, in.def);
            if (l.type.equals("particles") && !in.stopping && lt >= 0 && (in.def.duration <= 0 || in.t < in.def.duration) && lt <= lim) {
                if (!s.burstDone && l.burst > 0) {
                    emit(in, s, l.burst);
                    s.burstDone = true;
                    s.nextBurst = lt + l.burstEvery;
                } else if (l.burstEvery > 0 && l.burst > 0 && lt >= s.nextBurst) {
                    emit(in, s, l.burst);
                    s.nextBurst = lt + l.burstEvery;
                }
                if (l.rate > 0) {
                    s.accum += l.rate * dt;
                    int n = (int) s.accum;
                    s.accum -= n;
                    emit(in, s, n);
                }
            }
            // move
            double cx = l.local ? 0 : in.ox, cy = l.local ? 0 : in.oy, cz = l.local ? 0 : in.oz;
            double orbit = Math.toRadians(l.orbit) * dt;
            double cosO = Math.cos(orbit), sinO = Math.sin(orbit);
            float dragK = Math.max(0, 1 - l.drag * dt);
            for (Iterator<P> it = s.ps.iterator(); it.hasNext(); ) {
                P p = it.next();
                p.age += dt;
                if (p.age >= p.life) {
                    it.remove();
                    total--;
                    continue;
                }
                p.vy -= l.gravity * sc * dt;
                if (l.turbulence != 0) {
                    float k = l.turbulence * sc * dt;
                    p.vx += wave(p.seed, p.age * 2) * k;
                    p.vy += wave(p.seed + 17, p.age * 2) * k * 0.6f;
                    p.vz += wave(p.seed + 41, p.age * 2) * k;
                }
                if (l.attract != 0) {
                    double rx = p.x - cx, ry = p.y - cy - l.offsetY * sc, rz = p.z - cz, len = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    if (len > 1e-4) {
                        double k = l.attract * sc * dt / len;
                        p.vx -= rx * k;
                        p.vy -= ry * k;
                        p.vz -= rz * k;
                    }
                }
                p.vx *= dragK;
                p.vy *= dragK;
                p.vz *= dragK;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.z += p.vz * dt;
                if (orbit != 0 && s.axis != null) {
                    // around the turned axis through the layer's centre (Rodrigues)
                    double ox = cx + l.offsetX * sc, oy = cy + l.offsetY * sc, oz = cz + l.offsetZ * sc;
                    Vector3f k = s.axis;
                    double rx = p.x - ox, ry = p.y - oy, rz = p.z - oz;
                    double dot = k.x * rx + k.y * ry + k.z * rz;
                    double crx = k.y * rz - k.z * ry, cry = k.z * rx - k.x * rz, crz = k.x * ry - k.y * rx;
                    p.x = ox + rx * cosO + crx * sinO + k.x * dot * (1 - cosO);
                    p.y = oy + ry * cosO + cry * sinO + k.y * dot * (1 - cosO);
                    p.z = oz + rz * cosO + crz * sinO + k.z * dot * (1 - cosO);
                    double vdot = k.x * p.vx + k.y * p.vy + k.z * p.vz;
                    double cvx = k.y * p.vz - k.z * p.vy, cvy = k.z * p.vx - k.x * p.vz, cvz = k.x * p.vy - k.y * p.vx;
                    double nvx = p.vx * cosO + cvx * sinO + k.x * vdot * (1 - cosO);
                    double nvy = p.vy * cosO + cvy * sinO + k.y * vdot * (1 - cosO);
                    p.vz = p.vz * cosO + cvz * sinO + k.z * vdot * (1 - cosO);
                    p.vx = nvx;
                    p.vy = nvy;
                } else if (orbit != 0) {
                    double rx = p.x - cx, rz = p.z - cz;
                    p.x = cx + rx * cosO - rz * sinO;
                    p.z = cz + rx * sinO + rz * cosO;
                    double vx = p.vx * cosO - p.vz * sinO;
                    p.vz = p.vx * sinO + p.vz * cosO;
                    p.vx = vx;
                }
                p.rot += p.spin * dt;
            }
        }
    }

    // ================================================================== drawing

    private record Sprite(Inst in, LayerState s, P p, double dist) {
    }

    public static void onRenderLevel(ClientHooks.RenderLevel e) {
        // after clouds and weather: drawn earlier, the clouds (written later) would cover the particles
        if (e.getStage() != ClientHooks.LevelStage.AFTER_WEATHER) return;
        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        if (INSTANCES.isEmpty() || mc.level == null) return;
        // the IDE pauses a single-player game: its previews still play
        if (mc.isPaused() && !(mc.screen instanceof org.sprautestudio.spraute_engine.editor.SprauteScreen)) dt = 0;
        float partial = e.getPartialTick();
        for (Iterator<Inst> it = INSTANCES.iterator(); it.hasNext(); ) {
            Inst in = it.next();
            follow(in, partial);
            update(in, dt);
            if (in.finished()) {
                for (LayerState s : in.layers) total -= s.ps.size();
                it.remove();
            }
        }

        Camera cam = e.getCamera();
        // at this stage the pose has no view turn in it: build it as the game does (roll, pitch, yaw)
        Matrix4f view = new Matrix4f()
                .rotateZ((float) Math.toRadians(viewRoll))
                .rotateX((float) Math.toRadians(viewPitch))
                .rotateY((float) Math.toRadians(viewYaw + 180));
        // and the global model-view cleared for the draw, whatever this stage left in it
        com.mojang.blaze3d.vertex.PoseStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            drawAll(INSTANCES, cam.getPosition(), new Vector3f(cam.getLeftVector()), new Vector3f(cam.getUpVector()), view, true);
        } finally {
            mv.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        }
    }

    private static float viewYaw, viewPitch, viewRoll;

    /** The final camera angles of this frame (after cutscenes and others changed them). */
    public static void onAnglesFinal(ClientHooks.CameraAngles e) {
        viewYaw = e.getYaw();
        viewPitch = e.getPitch();
        viewRoll = e.getRoll();
    }

    /** Draws the particles of these effects as seen from {@code cp} (left/up: the view's axes in the world). */
    private static void drawAll(List<Inst> instances, Vec3 cp, Vector3f left, Vector3f up, Matrix4f pose, boolean depth) {
        // group by texture and blend; smoke (alpha) far to near first, then the glowing ones
        Map<String, List<Sprite>> alpha = new HashMap<>(), add = new HashMap<>();
        for (Inst in : instances) {
            for (LayerState s : in.layers) {
                if (s.ps.isEmpty()) continue;
                boolean additive = !s.l.blend.equals("alpha");
                String key = s.l.texture + "|" + in.project;
                List<Sprite> list = (additive ? add : alpha).computeIfAbsent(key, k -> new ArrayList<>());
                for (P p : s.ps) {
                    double wx = s.l.local ? p.x + in.ox : p.x, wy = s.l.local ? p.y + in.oy : p.y, wz = s.l.local ? p.z + in.oz : p.z;
                    double ddx = wx - cp.x, ddy = wy - cp.y, ddz = wz - cp.z;
                    list.add(new Sprite(in, s, p, additive ? 0 : ddx * ddx + ddy * ddy + ddz * ddz));
                }
            }
        }
        RenderSystem.enableBlend();
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            RenderSystem.defaultBlendFunc();
            for (Map.Entry<String, List<Sprite>> g : alpha.entrySet()) {
                g.getValue().sort(Comparator.comparingDouble(Sprite::dist).reversed());
                drawGroup(g.getKey(), g.getValue(), cp, left, up, pose, false);
            }
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            for (Map.Entry<String, List<Sprite>> g : add.entrySet()) drawGroup(g.getKey(), g.getValue(), cp, left, up, pose, true);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    // ================================================================== preview in the editor

    /** Effects shown in the editor's viewport, simulated apart from the world around (0, 0, 0). */
    private static final List<Inst> PREVIEW = new ArrayList<>();
    private static long previewFrame = System.nanoTime();

    public static void previewPlay(String project, FxDef def, float scale) {
        previewStop();
        PREVIEW.add(new Inst("preview", project, def, 0, 0, 0, -1, Math.max(0.01f, scale)));
    }

    public static void previewStop() {
        for (Inst in : PREVIEW) for (LayerState s : in.layers) total -= s.ps.size();
        PREVIEW.clear();
    }

    public static boolean previewRunning() {
        return !PREVIEW.isEmpty();
    }

    /** Seconds the preview has been playing, or -1. */
    public static double previewTime() {
        return PREVIEW.isEmpty() ? -1 : PREVIEW.get(0).t;
    }

    public static int previewParticles() {
        int n = 0;
        for (Inst in : PREVIEW) for (LayerState s : in.layers) n += s.ps.size();
        return n;
    }

    /** Farthest particle from the centre (blocks), beams by their start: for fitting the view. */
    public static float previewExtent() {
        float m = 0;
        for (Inst in : PREVIEW) {
            for (LayerState s : in.layers) {
                for (P p : s.ps) {
                    double d = Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z) + FxDef.curve(s.l.size, p.age / p.life) * in.scale * 0.5;
                    if (d > m) m = (float) d;
                }
            }
        }
        return m;
    }

    /**
     * Plays and draws the preview in a GUI rectangle: an orbiting camera ({@code yaw}, {@code pitch}
     * in degrees) {@code ppb} pixels per block, centred on (cx, groundY). Same drawing as in the world.
     */
    public static void renderPreview(net.minecraft.client.gui.GuiGraphics g, float cx, float groundY, float ppb, float yaw, float pitch,
                                     int vx, int vy, int vw, int vh, boolean paused) {
        long now = System.nanoTime();
        float dt = paused ? 0 : (float) Math.min(0.1, (now - previewFrame) / 1e9);
        previewFrame = now;
        for (Iterator<Inst> it = PREVIEW.iterator(); it.hasNext(); ) {
            Inst in = it.next();
            update(in, dt);
            if (in.finished()) {
                for (LayerState s : in.layers) total -= s.ps.size();
                it.remove();
            }
        }
        // the shake of the effect shakes the preview camera too
        float shake = screenOf(PREVIEW, "shake", null, false);
        if (shake > 0) {
            double t = now / 1e9 * 22;
            float k = shake * shake;
            yaw += (float) (Math.sin(t * 1.13) + Math.sin(t * 2.71) * 0.5) * 3.5f * k;
            pitch += (float) (Math.sin(t * 1.37 + 2) + Math.sin(t * 3.3) * 0.5) * 2.5f * k;
        }
        Matrix4f rot = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
        Matrix4f inv = new Matrix4f(rot).invert();
        Vector3f right = inv.transformDirection(new Vector3f(1, 0, 0)), up = inv.transformDirection(new Vector3f(0, 1, 0));
        Vector3f toward = inv.transformDirection(new Vector3f(0, 0, 1));
        Vec3 cp = new Vec3(toward.x * 2000, toward.y * 2000, toward.z * 2000);
        Matrix4f m = new Matrix4f(g.pose().last().pose()).translate(cx, groundY, 50).scale(ppb, -ppb, 0.001f).mul(rot)
                .translate((float) cp.x, (float) cp.y, (float) cp.z);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        // the ground: a grid of blocks and a person for scale
        grid(m, cp, ppb);
        person(m, cp, right);
        drawAll(PREVIEW, cp, new Vector3f(right).negate(), up, m, false);
        RenderSystem.enableDepthTest();
        float[] rgb = new float[3];
        float a = screenOf(PREVIEW, "flash", rgb, false);
        if (a > 0.003f) {
            int c = ((int) (a * 255) << 24) | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
            g.fill(vx, vy, vx + vw, vy + vh, c);
        }
    }

    private static void grid(Matrix4f m, Vec3 cp, float ppb) {
        // one line a block when close, every 5 or 10 when far
        int step = ppb >= 6 ? 1 : ppb >= 1.5f ? 5 : 10;
        int n = step * 12;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = -n; i <= n; i += step) {
            int a = i == 0 ? 90 : (i % (step * 5) == 0 ? 55 : 30);
            line(bb, m, cp, i, -n, i, n, a);
            line(bb, m, cp, -n, i, n, i, a);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void line(BufferBuilder bb, Matrix4f m, Vec3 cp, float x0, float z0, float x1, float z1, int a) {
        bb.vertex(m, (float) (x0 - cp.x), (float) -cp.y, (float) (z0 - cp.z)).color(255, 255, 255, a).endVertex();
        bb.vertex(m, (float) (x1 - cp.x), (float) -cp.y, (float) (z1 - cp.z)).color(255, 255, 255, a).endVertex();
    }

    /** A grey figure 1.8 blocks tall two blocks aside: how big the effect is next to a player. */
    private static void person(Matrix4f m, Vec3 cp, Vector3f right) {
        Vector3f r = new Vector3f(right.x, 0, right.z);
        if (r.lengthSquared() < 1e-6) r.set(1, 0, 0);
        r.normalize();
        float px = -r.x * 2.2f, pz = -r.z * 2.2f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        box(bb, m, cp, px, pz, r, 0.3f, 0, 1.35f, 150);
        box(bb, m, cp, px, pz, r, 0.22f, 1.4f, 1.85f, 170);
        BufferUploader.drawWithShader(bb.end());
    }

    private static void box(BufferBuilder bb, Matrix4f m, Vec3 cp, float px, float pz, Vector3f r, float hw, float y0, float y1, int a) {
        float ax = r.x * hw, az = r.z * hw;
        bb.vertex(m, (float) (px - ax - cp.x), (float) (y0 - cp.y), (float) (pz - az - cp.z)).color(170, 176, 190, a).endVertex();
        bb.vertex(m, (float) (px + ax - cp.x), (float) (y0 - cp.y), (float) (pz + az - cp.z)).color(170, 176, 190, a).endVertex();
        bb.vertex(m, (float) (px + ax - cp.x), (float) (y1 - cp.y), (float) (pz + az - cp.z)).color(170, 176, 190, a).endVertex();
        bb.vertex(m, (float) (px - ax - cp.x), (float) (y1 - cp.y), (float) (pz - az - cp.z)).color(170, 176, 190, a).endVertex();
    }

    private static void drawGroup(String key, List<Sprite> sprites, Vec3 cp, Vector3f left, Vector3f up, Matrix4f pose, boolean additive) {
        if (sprites.isEmpty()) return;
        int bar = key.indexOf('|');
        ResourceLocation tex = FxTextures.get(key.substring(0, bar), key.substring(bar + 1));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Sprite sp : sprites) quad(bb, sp, cp, left, up, pose, additive);
        BufferUploader.drawWithShader(bb.end());
    }

    private static void quad(BufferBuilder bb, Sprite sp, Vec3 cp, Vector3f left, Vector3f up, Matrix4f m, boolean additive) {
        Inst in = sp.in();
        FxDef.Layer l = sp.s().l;
        P p = sp.p();
        float t = p.age / p.life;
        float size = FxDef.curve(l.size, t) * p.sizeMul * in.scale;
        float a = FxDef.curve(l.alpha, t) * Math.max(0, in.fade);
        if (size <= 0 || a <= 0.002f) return;
        int rgb = p.color >= 0 ? p.color : FxDef.gradient(sp.s().colors, t);
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float k = l.intensity;
        if (additive && k > 1) {
            float w = Math.min(1, (k - 1) * 0.5f);
            r += (1 - r) * w;
            g += (1 - g) * w;
            b += (1 - b) * w;
            a = Math.min(1, a * k);
        } else a = Math.min(1, a * Math.max(0, k));
        float x = (float) ((l.local ? p.x + in.ox : p.x) - cp.x);
        float y = (float) ((l.local ? p.y + in.oy : p.y) - cp.y);
        float z = (float) ((l.local ? p.z + in.oz : p.z) - cp.z);
        int ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255), ai = (int) (a * 255);
        float hs = size / 2;
        switch (l.render) {
            case "flat" -> {
                double c = Math.cos(Math.toRadians(p.rot)) * hs, s = Math.sin(Math.toRadians(p.rot)) * hs;
                // the sprite's two sides in its plane (lying flat, or turned with the layer)
                Vector3f av = new Vector3f((float) c, 0, (float) s), bv = new Vector3f((float) -s, 0, (float) c);
                if (sp.s().rot != null) {
                    sp.s().rot.transform(av);
                    sp.s().rot.transform(bv);
                }
                v(bb, m, x - av.x - bv.x, y - av.y - bv.y, z - av.z - bv.z, 0, 0, ri, gi, bi, ai);
                v(bb, m, x + av.x - bv.x, y + av.y - bv.y, z + av.z - bv.z, 1, 0, ri, gi, bi, ai);
                v(bb, m, x + av.x + bv.x, y + av.y + bv.y, z + av.z + bv.z, 1, 1, ri, gi, bi, ai);
                v(bb, m, x - av.x + bv.x, y - av.y + bv.y, z - av.z + bv.z, 0, 1, ri, gi, bi, ai);
            }
            case "beam" -> {
                Vector3f axis = new Vector3f(p.dx, p.dy, p.dz);
                float len = l.beamLength * in.scale;
                Vector3f toCam = new Vector3f(-x, -y, -z);
                Vector3f side = axis.cross(toCam, new Vector3f());
                if (side.lengthSquared() < 1e-8) side.set(1, 0, 0);
                side.normalize().mul(hs);
                float mx = x + p.dx * len * 0.7f, my = y + p.dy * len * 0.7f, mz = z + p.dz * len * 0.7f;
                float ex = x + p.dx * len, ey = y + p.dy * len, ez = z + p.dz * len;
                // full strength along most of it, fading out at the far end
                v(bb, m, x - side.x, y - side.y, z - side.z, 0, 0, ri, gi, bi, ai);
                v(bb, m, x + side.x, y + side.y, z + side.z, 1, 0, ri, gi, bi, ai);
                v(bb, m, mx + side.x, my + side.y, mz + side.z, 1, 0.7f, ri, gi, bi, ai);
                v(bb, m, mx - side.x, my - side.y, mz - side.z, 0, 0.7f, ri, gi, bi, ai);
                v(bb, m, mx - side.x, my - side.y, mz - side.z, 0, 0.7f, ri, gi, bi, ai);
                v(bb, m, mx + side.x, my + side.y, mz + side.z, 1, 0.7f, ri, gi, bi, ai);
                v(bb, m, ex + side.x, ey + side.y, ez + side.z, 1, 1, ri, gi, bi, 0);
                v(bb, m, ex - side.x, ey - side.y, ez - side.z, 0, 1, ri, gi, bi, 0);
            }
            case "stretch" -> {
                Vector3f vel = new Vector3f((float) p.vx, (float) p.vy, (float) p.vz);
                float speed = vel.length();
                Vector3f axis = speed > 1e-4 ? vel.div(speed) : new Vector3f(p.dx, p.dy, p.dz);
                float len = size + speed * l.stretch;
                Vector3f toCam = new Vector3f(-x, -y, -z);
                Vector3f side = axis.cross(toCam, new Vector3f());
                if (side.lengthSquared() < 1e-8) side.set(left);
                side.normalize().mul(hs * 0.5f);
                float hx = axis.x * len / 2, hy = axis.y * len / 2, hz = axis.z * len / 2;
                v(bb, m, x - hx - side.x, y - hy - side.y, z - hz - side.z, 0, 0, ri, gi, bi, ai);
                v(bb, m, x + hx - side.x, y + hy - side.y, z + hz - side.z, 1, 0, ri, gi, bi, ai);
                v(bb, m, x + hx + side.x, y + hy + side.y, z + hz + side.z, 1, 1, ri, gi, bi, ai);
                v(bb, m, x - hx + side.x, y - hy + side.y, z - hz + side.z, 0, 1, ri, gi, bi, ai);
            }
            default -> {
                double c = Math.cos(Math.toRadians(p.rot)), s = Math.sin(Math.toRadians(p.rot));
                // right = -left; rotate the sprite in the view plane
                float rx = (float) (-left.x * c + up.x * s) * hs, ry = (float) (-left.y * c + up.y * s) * hs, rz = (float) (-left.z * c + up.z * s) * hs;
                float ux = (float) (left.x * s + up.x * c) * hs, uy = (float) (left.y * s + up.y * c) * hs, uz = (float) (left.z * s + up.z * c) * hs;
                v(bb, m, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, ri, gi, bi, ai);
                v(bb, m, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, ri, gi, bi, ai);
                v(bb, m, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, ri, gi, bi, ai);
                v(bb, m, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, ri, gi, bi, ai);
            }
        }
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float z, float u, float vv, int r, int g, int b, int a) {
        bb.vertex(m, x, y, z).uv(u, vv).color(r, g, b, a).endVertex();
    }

    // ================================================================== screen: flash and shake

    /** Strength of the screen layers right now, weaker with the distance from the camera. */
    private static float screen(String type, float[] rgb) {
        return screenOf(INSTANCES, type, rgb, true);
    }

    private static float screenOf(List<Inst> instances, String type, float[] rgb, boolean byDistance) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cp = mc.gameRenderer.getMainCamera().getPosition();
        float best = 0;
        for (Inst in : instances) {
            for (LayerState s : in.layers) {
                FxDef.Layer l = s.l;
                if (!l.type.equals(type)) continue;
                double lt = in.t - l.delay;
                float len = Math.max(0.05f, l.time > 0 ? l.time : 0.6f);
                if (lt < 0 || lt > len) continue;
                float k = (float) (1 - lt / len);
                float v = l.strength * k * k * Math.max(0, in.fade);
                if (l.range > 0 && byDistance) {
                    double d = cp.distanceTo(new Vec3(in.ox, in.oy, in.oz));
                    v *= (float) Math.max(0, 1 - d / (l.range * in.scale));
                }
                if (v > best) {
                    best = v;
                    if (rgb != null) {
                        int c = FxDef.parseColor(l.flashColor);
                        rgb[0] = ((c >> 16) & 255) / 255f;
                        rgb[1] = ((c >> 8) & 255) / 255f;
                        rgb[2] = (c & 255) / 255f;
                    }
                }
            }
        }
        return Math.min(1, best);
    }

    public static void onGui(ClientHooks.RenderGui e) {
        if (INSTANCES.isEmpty()) return;
        float[] rgb = new float[3];
        float a = screen("flash", rgb);
        if (a <= 0.003f) return;
        int c = ((int) (a * 255) << 24) | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        e.getGuiGraphics().fill(0, 0, w, h, c);
    }

    /** Runs after the cutscene camera so the shake adds to it. */
    public static void onAngles(ClientHooks.CameraAngles e) {
        if (INSTANCES.isEmpty()) return;
        float s = screen("shake", null);
        if (s <= 0.001f) return;
        double t = System.nanoTime() / 1e9 * 22;
        float k = s * s;
        e.setYaw(e.getYaw() + (float) (Math.sin(t * 1.13) + Math.sin(t * 2.71) * 0.5) * 3.5f * k);
        e.setPitch(e.getPitch() + (float) (Math.sin(t * 1.37 + 2) + Math.sin(t * 3.3) * 0.5) * 2.5f * k);
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 0.91 + 5) + Math.sin(t * 2.2) * 0.5) * 2.5f * k);
    }

    public static void onLogout(ClientHooks.LoggingOut e) {
        INSTANCES.clear();
        total = 0;
    }
}
