package org.sprautestudio.st_addon_spraute.cutscene.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.cutscene.CutsceneDoc;
import org.sprautestudio.st_addon_spraute.cutscene.Ease;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.orb.CameraEntity;
import org.sprautestudio.st_addon_spraute.runtime.orb.Orbs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runs the camera on the client: cutscenes (keyframes, blending in and out, captions, marks) and
 * camera commands from stories (move, look, follow, orbit, fov, roll, shake), plus the cinema
 * presentation — bars, fades, hidden HUD, locked controls and skipping.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class CameraDirector {
    private CameraDirector() {}

    private enum Mode { NONE, FREE, FOLLOW, ORBIT, SCENE, RETURN, FLY, DIALOG }

    private static Mode mode = Mode.NONE;
    private static CameraEntity cam;
    private static CameraType savedType;
    /** Pose shown last frame (without shake). */
    private static final CutsceneDoc.Pose current = new CutsceneDoc.Pose();
    private static final CutsceneDoc.Pose shown = new CutsceneDoc.Pose();
    private static long lastFrame = System.nanoTime();

    // free moves
    private static final CutsceneDoc.Pose from = new CutsceneDoc.Pose(), to = new CutsceneDoc.Pose();
    private static double tweenStart, tweenTime;
    private static String tweenEase = "smooth";
    /** Keep looking at an entity (id) or a point. */
    private static int lookEntity = -1;
    private static Vec3 lookPoint;

    // follow / orbit
    private static int targetEntity = -1;
    private static double distance = 4, height = 1.5, angle = 0, radius = 5, speed = 20, orbitAngle;

    // cutscene
    private static CutsceneDoc scene;
    private static String sceneName = "";
    private static int sceneScript = -1;
    private static double sceneStart;
    private static Vec3 origin = Vec3.ZERO;
    private static Map<String, Integer> looks = new HashMap<>();
    private static int marksDone;
    private static final CutsceneDoc.Pose entry = new CutsceneDoc.Pose();
    /** Local preview from the editor: events go here instead of the server. */
    private static BiConsumer<String, String> localEvents;

    // return to the player
    private static double returnStart, returnTime;
    private static final CutsceneDoc.Pose returnFrom = new CutsceneDoc.Pose();

    // presentation
    private static final Tween bars = new Tween(), fade = new Tween(), fovAdd = new Tween(), rollAdd = new Tween();
    private static int fadeColor = 0xFF000000;
    private static float trauma, traumaDecay = 1;
    private static boolean locked, skippable;
    /** What is hidden now: none, minecraft, storytelling, custom or all. */
    private static String hudHide = "none";

    public static boolean hidesMinecraft() {
        return hudHide.equals("all") || hudHide.equals("minecraft");
    }

    public static boolean hidesSpraute() {
        return hudHide.equals("all") || hudHide.equals("storytelling");
    }

    public static boolean hidesCustom() {
        return hudHide.equals("all") || hudHide.equals("custom");
    }
    private static float lockYaw, lockPitch;
    private static String caption = "";
    private static double captionAt;

    /** A value moving smoothly to a target. */
    private static final class Tween {
        double fromV, toV, start, time;
        String ease = "smooth";

        void go(double target, double seconds, String e) {
            fromV = value();
            toV = target;
            start = now();
            time = seconds;
            ease = e == null ? "smooth" : e;
        }

        void set(double v) {
            fromV = toV = v;
            time = 0;
        }

        double value() {
            if (time <= 0) return toV;
            double k = (now() - start) / time;
            if (k >= 1) return toV;
            return fromV + (toV - fromV) * Ease.apply(ease, k);
        }
    }

    private static double now() {
        return System.nanoTime() / 1e9;
    }

    // ================================================================== dialog camera (as Spraute's dialogs)

    /** Eye positions and angles the dialog camera passes, a Catmull-Rom spline over them. */
    private static final java.util.List<CutsceneDoc.Pose> dialogPath = new java.util.ArrayList<>();
    private static double dialogStart, dialogTime;
    private static boolean dialogBack, dialogLocked;
    private static String dialogHud = "none";

    /** The flight to the speaker has ended (or there is none). */
    public static boolean dialogArrived() {
        return mode != Mode.DIALOG || dialogBack || now() - dialogStart >= dialogTime;
    }

    /** Ticks of a flight over {@code len} blocks, Spraute's formula: fast for short hops, capped for long ones. */
    private static double flightSeconds(double len) {
        return Math.max(1, (int) (len * Math.exp(-0.05 * len) * 5)) / 20.0;
    }

    /**
     * Flies the camera in front of the speaker's face, the way Spraute's dialog window does: 1.6
     * blocks along its look, a little to the side, turned back at it with a 15° slant; from behind it
     * goes round the side first.
     */
    public static void dialogFlyTo(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (!(entity(entityId) instanceof net.minecraft.world.entity.LivingEntity e) || pl == null || mode == Mode.SCENE) return;
        boolean wasActive = mode != Mode.NONE;
        CutsceneDoc.Pose init = new CutsceneDoc.Pose();
        if (wasActive) init.set(current);
        else {
            playerPose(init, 1);
            Vec3 look = pl.getLookAngle().scale(0.25);
            init.x += look.x;
            init.y += look.y;
            init.z += look.z;
        }
        Vec3 horizontal = e.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 angle = e.getLookAngle().scale(1.6);
        float viewYRot = e.getViewYRot(0), viewXRot = e.getViewXRot(0);
        Vec3 side = horizontal.multiply(0.1, 0, 0.1).yRot((float) Math.toRadians(90));
        Vec3 eye = e.position().add(0, e.getEyeHeight(), 0);
        CutsceneDoc.Pose end = pose(eye.add(angle).add(side), viewYRot + 165, -viewXRot, init.fov);
        double len = Math.sqrt(sq(end.x - init.x) + sq(end.y - init.y) + sq(end.z - init.z));
        if (len <= 0.05) return;
        dialogPath.clear();
        dialogPath.add(init);
        Vec3 between = new Vec3(end.x - init.x, 0, end.z - init.z).normalize();
        if (between.dot(horizontal) > 0.75) {
            // coming from behind: pass by its side
            Vec3 feet = e.position();
            Vec3 a = horizontal.yRot((float) Math.toRadians(-90)).add(feet), b = horizontal.yRot((float) Math.toRadians(90)).add(feet);
            double eyeUp = e.getEyeHeight();
            Vec3 initFeet = new Vec3(init.x, init.y - pl.getEyeHeight(), init.z);
            if (initFeet.distanceTo(a) > initFeet.distanceTo(b)) dialogPath.add(pose(b.add(0, eyeUp, 0), viewYRot + 90, -viewXRot, init.fov));
            else dialogPath.add(pose(a.add(0, eyeUp, 0), viewYRot - 90, -viewXRot, init.fov));
        }
        dialogPath.add(end);
        if (!wasActive) {
            dialogHud = hudHide;
            dialogLocked = locked;
            activate();
        }
        hudHide = "all";
        locked = true;
        dialogBack = false;
        dialogStart = now();
        dialogTime = flightSeconds(len);
        mode = Mode.DIALOG;
    }

    /**
     * While the camera is its own (a dialog, a cutscene, coming back), the game draws the player's body;
     * near the end of a flight back the camera passes through the head. Close to it the body is not drawn.
     */
    @SubscribeEvent
    public static void hideNearPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (mode == Mode.NONE || mode == Mode.FLY) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player) return;
        Vec3 eye = mc.player.getEyePosition(e.getPartialTick());
        double dx = current.x - eye.x, dy = current.y - eye.y, dz = current.z - eye.z;
        if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) e.setCanceled(true);
    }

    /** Flies back to the player when the dialog closes. */
    public static void dialogFlyBack() {
        if (mode != Mode.DIALOG) return;
        CutsceneDoc.Pose init = new CutsceneDoc.Pose().set(current), end = new CutsceneDoc.Pose();
        playerPose(end, 1);
        double len = Math.sqrt(sq(end.x - init.x) + sq(end.y - init.y) + sq(end.z - init.z));
        dialogPath.clear();
        dialogPath.add(init);
        dialogPath.add(end);
        dialogBack = true;
        dialogStart = now();
        dialogTime = len <= 0.05 ? 0 : flightSeconds(len);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static CutsceneDoc.Pose pose(Vec3 p, float yaw, float pitch, float fov) {
        CutsceneDoc.Pose o = new CutsceneDoc.Pose();
        o.x = p.x;
        o.y = p.y;
        o.z = p.z;
        o.yaw = yaw;
        o.pitch = pitch;
        o.fov = fov;
        return o;
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** The dialog camera at a share {@code k} of the flight (already eased). */
    private static CutsceneDoc.Pose dialogAt(double k) {
        int n = dialogPath.size();
        double u = Math.max(0, Math.min(1, k)) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(u));
        double f = u - i;
        CutsceneDoc.Pose p0 = dialogPath.get(Math.max(0, i - 1)), p1 = dialogPath.get(i), p2 = dialogPath.get(i + 1),
                p3 = dialogPath.get(Math.min(n - 1, i + 2));
        CutsceneDoc.Pose o = new CutsceneDoc.Pose().set(p1);
        o.x = catmull(p0.x, p1.x, p2.x, p3.x, f);
        o.y = catmull(p0.y, p1.y, p2.y, p3.y, f);
        o.z = catmull(p0.z, p1.z, p2.z, p3.z, f);
        // angles turn evenly within each part
        o.yaw = p1.yaw + (float) (CutsceneDoc.wrap(p2.yaw - p1.yaw) * f);
        o.pitch = p1.pitch + (float) ((p2.pitch - p1.pitch) * f);
        o.fov = p1.fov + (float) ((p2.fov - p1.fov) * f);
        return o;
    }

    private static double easeInOutQuad(double x) {
        return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
    }

    public static boolean active() {
        return mode != Mode.NONE;
    }

    public static boolean inCutscene() {
        return mode == Mode.SCENE;
    }

    // ================================================================== free flight (editor)

    /** Flying speed in blocks per second. */
    public static double flySpeed = 8;

    /** A free camera flown with the movement keys and the mouse; the player stays where they are. */
    public static void startFly(CutsceneDoc.Pose start) {
        activate();
        if (cam == null) return;
        if (start != null) current.set(start);
        current.roll = 0;
        current.fov = Minecraft.getInstance().options.fov().get();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.setYRot(current.yaw);
            p.setXRot(current.pitch);
            p.yRotO = current.yaw;
            p.xRotO = current.pitch;
        }
        mode = Mode.FLY;
        locked = true;
        hudHide = "all";
        bars.set(0);
    }

    public static boolean flyingOrPreview() {
        return mode == Mode.FLY || mode == Mode.SCENE;
    }

    public static boolean flying() {
        return mode == Mode.FLY;
    }

    /** Puts the flying camera at a pose (and turns the mouse look to it). */
    public static void flyTo(CutsceneDoc.Pose p) {
        if (mode != Mode.FLY) return;
        current.set(p);
        current.roll = 0;
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl != null) {
            pl.setYRot(p.yaw);
            pl.setXRot(p.pitch);
            pl.yRotO = p.yaw;
            pl.xRotO = p.pitch;
        }
    }

    public static void stopFly() {
        if (mode != Mode.FLY) return;
        locked = false;
        hudHide = "none";
        deactivate();
    }

    /** The camera as last shown. */
    public static CutsceneDoc.Pose pose() {
        return new CutsceneDoc.Pose().set(current);
    }

    private static boolean down(net.minecraft.client.KeyMapping k) {
        var key = k.getKey();
        long win = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, key.getValue());
    }

    // ================================================================== camera entity

    private static void activate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (cam == null || cam.level() != mc.level) cam = new CameraEntity(Orbs.CAMERA.get(), mc.level);
        if (mode == Mode.NONE) {
            playerPose(current, 1);
            savedType = mc.options.getCameraType();
            lockYaw = mc.player.getYRot();
            lockPitch = mc.player.getXRot();
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(cam);
    }

    private static void deactivate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        mode = Mode.NONE;
        lookEntity = -1;
        lookPoint = null;
        targetEntity = -1;
        caption = "";
    }

    private static void playerPose(CutsceneDoc.Pose p, float partial) {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return;
        Vec3 e = pl.getEyePosition(partial);
        p.x = e.x;
        p.y = e.y;
        p.z = e.z;
        p.yaw = pl.getViewYRot(partial);
        p.pitch = pl.getViewXRot(partial);
        p.roll = 0;
        p.fov = Minecraft.getInstance().options.fov().get();
        p.shake = 0;
    }

    // ================================================================== commands

    /** Plays a cutscene; {@code origin} offsets relative ones, {@code looks} maps look targets to entity ids. */
    public static void play(int script, String name, CutsceneDoc doc, Vec3 origin, Map<String, Integer> looks, BiConsumer<String, String> local) {
        if (doc.keys.isEmpty()) {
            report(script, "end", name, local);
            return;
        }
        activate();
        if (cam == null) return;
        entry.set(current);
        scene = doc;
        sceneName = name;
        sceneScript = script;
        localEvents = local;
        CameraDirector.origin = doc.relative ? origin : Vec3.ZERO;
        CameraDirector.looks = looks;
        sceneStart = now();
        marksDone = 0;
        mode = Mode.SCENE;
        skippable = doc.skippable;
        locked = doc.lockPlayer;
        hudHide = doc.hideMode();
        bars.go(doc.bars, 0.6, "ease_out_cubic");
        fadeColor = GuiDoc.toColor(doc.fadeColor) | 0xFF000000;
        if (doc.fadeIn > 0) fade.set(1);
    }

    public static void stop(boolean skipped) {
        if (mode == Mode.SCENE) endScene(skipped);
        else returnToPlayer(0.6);
    }

    private static void endScene(boolean skipped) {
        String name = sceneName;
        int script = sceneScript;
        BiConsumer<String, String> local = localEvents;
        CutsceneDoc doc = scene;
        scene = null;
        localEvents = null;
        bars.go(0, 0.5, "ease_in_cubic");
        hudHide = "none";
        locked = false;
        if (doc != null && doc.fadeOut > 0) fade.go(0, Math.max(0.2, doc.fadeOut), "smooth");
        returnToPlayer(skipped ? 0.4 : doc != null ? doc.blendOut : 0.6);
        if (skipped) report(script, "skip", name, local);
        report(script, "end", name, local);
    }

    private static void report(int script, String kind, String name, BiConsumer<String, String> local) {
        if (local != null) local.accept(kind, name);
        else if (script >= 0 && Minecraft.getInstance().getConnection() != null) SprauteNetwork.sendToServer(new Packets.CameraEvent(script, kind, name));
    }

    private static void returnToPlayer(double seconds) {
        if (mode == Mode.NONE) return;
        returnFrom.set(current);
        returnStart = now();
        returnTime = Math.max(0.01, seconds);
        mode = Mode.RETURN;
        lookEntity = -1;
        lookPoint = null;
        caption = "";
    }

    /** A camera command from a story. */
    public static void op(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String op = o.get("op").getAsString();
        double time = num(o, "time", 0);
        String ease = o.has("ease") ? o.get("ease").getAsString() : "smooth";
        switch (op) {
            case "at" -> {
                activate();
                if (mode == Mode.SCENE) return;
                from.set(current);
                to.set(current);
                to.x = num(o, "x", current.x);
                to.y = num(o, "y", current.y);
                to.z = num(o, "z", current.z);
                if (o.has("yaw")) to.yaw = (float) num(o, "yaw", 0);
                if (o.has("pitch")) to.pitch = (float) num(o, "pitch", 0);
                // a set direction and no target: the camera stops following the previous one
                if ((o.has("yaw") || o.has("pitch")) && !o.has("lookEntity") && !o.has("lookX")) {
                    lookEntity = -1;
                    lookPoint = null;
                }
                tween(time, ease);
                setLook(o);
                mode = Mode.FREE;
            }
            case "turn" -> {
                activate();
                if (mode == Mode.SCENE) return;
                from.set(current);
                to.set(current);
                to.yaw = (float) num(o, "yaw", current.yaw);
                to.pitch = (float) num(o, "pitch", current.pitch);
                lookEntity = -1;
                lookPoint = null;
                tween(time, ease);
                if (mode != Mode.FOLLOW && mode != Mode.ORBIT) mode = Mode.FREE;
            }
            case "look" -> {
                activate();
                if (mode == Mode.SCENE) return;
                if (mode != Mode.FOLLOW && mode != Mode.ORBIT) {
                    from.set(current);
                    to.set(current);
                    tween(time, ease);
                    mode = Mode.FREE;
                }
                setLook(o);
            }
            case "follow", "orbit" -> {
                activate();
                if (mode == Mode.SCENE) return;
                targetEntity = (int) num(o, "entity", -1);
                distance = num(o, "distance", 4);
                height = num(o, "height", 1.5);
                angle = num(o, "angle", 0);
                radius = num(o, "radius", 5);
                speed = num(o, "speed", 20);
                orbitAngle = current.yaw;
                mode = op.equals("follow") ? Mode.FOLLOW : Mode.ORBIT;
            }
            case "reset" -> returnToPlayer(Math.max(0.05, time));
            case "stop" -> stop(false);
            case "fov" -> fovAdd.go(num(o, "value", 70) - Minecraft.getInstance().options.fov().get(), time, ease);
            case "roll" -> rollAdd.go(num(o, "value", 0), time, ease);
            case "shake" -> {
                trauma = (float) Math.max(trauma, Math.min(1, num(o, "value", 0.5)));
                traumaDecay = (float) (time > 0 ? 1 / time : 1);
            }
            case "bars" -> bars.go(Math.max(0, Math.min(0.45, num(o, "value", 0.11))), time > 0 ? time : 0.5, ease);
            case "fade" -> {
                if (o.has("color")) fadeColor = GuiDoc.toColor(o.get("color").getAsString()) | 0xFF000000;
                fade.go(Math.max(0, Math.min(1, num(o, "value", 1))), time, ease);
            }
            case "hud" -> {
                var v = o.get("value").getAsJsonPrimitive();
                hudHide = v.isBoolean() ? (v.getAsBoolean() ? "none" : "all") : CutsceneDoc.HIDE_MODES.contains(v.getAsString()) ? v.getAsString() : "none";
            }
            case "lock" -> {
                locked = o.get("value").getAsBoolean();
                if (locked && Minecraft.getInstance().player != null) {
                    lockYaw = Minecraft.getInstance().player.getYRot();
                    lockPitch = Minecraft.getInstance().player.getXRot();
                }
            }
            default -> {
            }
        }
    }

    private static void tween(double time, String ease) {
        tweenStart = now();
        tweenTime = time;
        tweenEase = ease;
    }

    private static void setLook(JsonObject o) {
        if (o.has("lookEntity")) {
            lookEntity = o.get("lookEntity").getAsInt();
            lookPoint = null;
        } else if (o.has("lookX")) {
            lookPoint = new Vec3(num(o, "lookX", 0), num(o, "lookY", 0), num(o, "lookZ", 0));
            lookEntity = -1;
        }
    }

    private static double num(JsonObject o, String k, double def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
    }

    // ================================================================== per frame

    private static Vec3 eyesOf(Entity e, float partial) {
        return e.getEyePosition(partial);
    }

    private static void lookAt(CutsceneDoc.Pose p, Vec3 target) {
        double dx = target.x - p.x, dy = target.y - p.y, dz = target.z - p.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        p.pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
    }

    private static Entity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        return id < 0 || mc.level == null ? null : mc.level.getEntity(id);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        double t = now();
        float dt = (float) Math.min(0.1, t - lastFrame / 1e9);
        lastFrame = System.nanoTime();
        if (mode == Mode.NONE) {
            trauma = Math.max(0, trauma - dt * traumaDecay);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || cam == null) {
            deactivate();
            return;
        }
        float partial = e.renderTickTime;
        CutsceneDoc.Pose d = new CutsceneDoc.Pose().set(current);
        switch (mode) {
            case SCENE -> {
                double st = t - sceneStart;
                CutsceneDoc.Pose p = scene.at(st);
                p.x += origin.x;
                p.y += origin.y;
                p.z += origin.z;
                CutsceneDoc.Key key = scene.keys.get(p.segment);
                if (!key.look.isEmpty()) {
                    Entity le = key.look.equals("player") ? mc.player : entity(looks.getOrDefault(key.look, -1));
                    if (le != null) lookAt(p, eyesOf(le, partial));
                }
                String cap = st < scene.duration() ? key.caption : "";
                if (!cap.equals(caption)) captionAt = t;
                caption = cap;
                // marks as their frames are reached
                while (marksDone < scene.keys.size() && (marksDone <= p.segment || st >= scene.duration())) {
                    String m = scene.keys.get(marksDone).mark;
                    if (!m.isEmpty()) report(sceneScript, "mark", m, localEvents);
                    marksDone++;
                }
                double blend = scene.blendIn <= 0 ? 1 : Math.min(1, st / scene.blendIn);
                d.set(entry).lerp(p, Ease.apply("smooth", blend));
                if (blend >= 1) d.set(p);
                if (scene.fadeIn > 0 && st < scene.fadeIn + 0.05 && fade.toV == 1) fade.go(0, scene.fadeIn, "smooth");
                if (scene.fadeOut > 0 && st >= scene.duration() - scene.fadeOut && fade.toV == 0) fade.go(1, scene.fadeOut, "smooth");
                if (st >= scene.duration()) endScene(false);
            }
            case FREE -> {
                double k = tweenTime <= 0 ? 1 : Math.min(1, (t - tweenStart) / tweenTime);
                d.set(from).lerp(to, Ease.apply(tweenEase, k));
                Vec3 target = lookTarget(partial);
                if (target != null) {
                    CutsceneDoc.Pose look = new CutsceneDoc.Pose().set(d);
                    lookAt(look, target);
                    // turn towards the target as smoothly as the move goes
                    double lk = tweenTime <= 0 ? 1 : Math.min(1, (t - tweenStart) / Math.max(0.3, tweenTime));
                    d.yaw = from.yaw + CutsceneDoc.wrap(look.yaw - from.yaw) * (float) Ease.apply(tweenEase, lk);
                    d.pitch = from.pitch + (look.pitch - from.pitch) * (float) Ease.apply(tweenEase, lk);
                    if (lk >= 1) {
                        d.yaw = look.yaw;
                        d.pitch = look.pitch;
                    }
                }
            }
            case FOLLOW, ORBIT -> {
                Entity te = entity(targetEntity);
                if (te == null) te = mc.player;
                Vec3 base = te.getPosition(partial);
                CutsceneDoc.Pose want = new CutsceneDoc.Pose().set(current);
                double a;
                double r;
                if (mode == Mode.ORBIT) {
                    orbitAngle += speed * dt;
                    a = Math.toRadians(orbitAngle);
                    r = radius;
                } else {
                    a = Math.toRadians(te.getViewYRot(partial) + angle);
                    r = distance;
                }
                want.x = base.x + Math.sin(a) * r;
                want.z = base.z - Math.cos(a) * r;
                want.y = base.y + height;
                Vec3 target = lookTarget(partial);
                lookAt(want, target != null ? target : eyesOf(te, partial));
                double k = 1 - Math.exp(-dt * (mode == Mode.ORBIT ? 12 : 5));
                d.set(current).lerp(want, k);
            }
            case FLY -> {
                LocalPlayer pl = mc.player;
                d.yaw = pl.getViewYRot(partial);
                d.pitch = pl.getViewXRot(partial);
                if (mc.screen == null) {
                    var o = mc.options;
                    double f = (down(o.keyUp) ? 1 : 0) - (down(o.keyDown) ? 1 : 0);
                    double s = (down(o.keyRight) ? 1 : 0) - (down(o.keyLeft) ? 1 : 0);
                    double u = (down(o.keyJump) ? 1 : 0) - (down(o.keyShift) ? 1 : 0);
                    double sp = flySpeed * dt * (down(o.keySprint) ? 3 : 1);
                    double ya = Math.toRadians(d.yaw), pa = Math.toRadians(d.pitch);
                    // forward along the view (so looking down and flying forward goes down), strafe flat
                    double fx = -Math.sin(ya) * Math.cos(pa), fy = -Math.sin(pa), fz = Math.cos(ya) * Math.cos(pa);
                    double rx = -Math.cos(ya), rz = -Math.sin(ya);
                    d.x += (fx * f + rx * s) * sp;
                    d.y += (fy * f + u) * sp;
                    d.z += (fz * f + rz * s) * sp;
                }
            }
            case DIALOG -> {
                double k = dialogTime <= 0 ? 1 : Math.min(1, (t - dialogStart) / dialogTime);
                if (dialogBack) {
                    // the player's view may have moved a little: the last point follows it
                    playerPose(dialogPath.get(dialogPath.size() - 1), partial);
                }
                d.set(dialogAt(easeInOutQuad(k)));
                if (dialogBack && k >= 1) {
                    hudHide = dialogHud;
                    locked = dialogLocked;
                    deactivate();
                    return;
                }
            }
            case RETURN -> {
                CutsceneDoc.Pose pl = new CutsceneDoc.Pose();
                playerPose(pl, partial);
                double k = Math.min(1, (t - returnStart) / returnTime);
                d.set(returnFrom).lerp(pl, Ease.apply("smooth", k));
                if (k >= 1) {
                    deactivate();
                    return;
                }
            }
            default -> {
            }
        }
        current.set(d);
        // presentation on top: fov and roll offsets, shake
        shown.set(d);
        shown.fov = (float) (d.fov + fovAdd.value());
        shown.roll = (float) (d.roll + rollAdd.value());
        trauma = Math.max(0, trauma - dt * traumaDecay);
        float s = Math.max(trauma, d.shake);
        if (s > 0) {
            double tt = t * 18;
            float k = s * s;
            shown.yaw += (float) (noise(tt, 1) * 4 * k);
            shown.pitch += (float) (noise(tt, 2) * 3 * k);
            shown.roll += (float) (noise(tt, 3) * 3 * k);
        }
        cam.setPos(shown.x, shown.y, shown.z);
        cam.xo = cam.xOld = shown.x;
        cam.yo = cam.yOld = shown.y;
        cam.zo = cam.zOld = shown.z;
        cam.setYRot(shown.yaw);
        cam.setXRot(shown.pitch);
        cam.yRotO = shown.yaw;
        cam.xRotO = shown.pitch;
        if (locked && mode != Mode.FLY) holdPlayer();
    }

    private static Vec3 lookTarget(float partial) {
        if (lookEntity >= 0) {
            Entity le = entity(lookEntity);
            if (le != null) return eyesOf(le, partial);
        }
        return lookPoint;
    }

    /** Smooth noise in -1..1. */
    private static double noise(double t, int seed) {
        return (Math.sin(t * 1.13 + seed * 7.1) + Math.sin(t * 2.71 + seed * 3.3) * 0.5 + Math.sin(t * 0.61 + seed) * 0.7) / 2.2;
    }

    /** The player may not turn the view with the mouse now (camera lock, outside free flight). */
    public static boolean locksView() {
        return locked && mode != Mode.FLY && Minecraft.getInstance().screen == null;
    }

    private static void holdPlayer() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setYRot(lockYaw);
        p.setXRot(lockPitch);
        p.yRotO = lockYaw;
        p.xRotO = lockPitch;
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (mode == Mode.NONE) return;
        e.setYaw(shown.yaw);
        e.setPitch(shown.pitch);
        e.setRoll(shown.roll);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (mode != Mode.NONE) e.setFOV(Math.max(5, Math.min(170, shown.fov)));
        else if (fovAdd.value() != 0) e.setFOV(e.getFOV() + fovAdd.value());
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (mode != Mode.NONE) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        if (!locked) return;
        var in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (mode != Mode.SCENE || !skippable || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        if (e.getKey() == GLFW.GLFW_KEY_SPACE || e.getKey() == GLFW.GLFW_KEY_ENTER) endScene(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && locked && mode != Mode.FLY) holdPlayer();
    }

    private static final ResourceLocation ST_HEALTH = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "health_overlay");
    private static final ResourceLocation ST_COINS = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "currency_overlay");

    /** Spraute's HUD parts (its speech, titles, blinks and blackouts are story, not HUD). */
    private static final java.util.Set<String> ST_HUD = java.util.Set.of("health_overlay", "currency_overlay", "medallion_overlay",
            "game_tip_overlay", "item_name");

    /** With the HUD hidden only story overlays (speech, titles) stay. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        boolean cinema = mode != Mode.NONE;
        if (hudHide.equals("none") && !cinema) return;
        ResourceLocation id = e.getOverlay().id();
        if (id.getNamespace().equals("minecraft")) {
            String p = id.getPath();
            boolean crosshair = p.equals("crosshair");
            if (crosshair ? mode != Mode.FLY || hidesMinecraft()
                    : hidesMinecraft() && !p.equals("title_text") && !p.equals("subtitles") && !p.equals("chat_panel")) e.setCanceled(true);
        } else if (id.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID) && hidesSpraute() && ST_HUD.contains(id.getPath())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        Minecraft mc = Minecraft.getInstance();
        double b = bars.value();
        if (b > 0.001) {
            int bh = (int) Math.round(h * b);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (!caption.isEmpty()) {
            float a = (float) Math.min(1, (now() - captionAt) / 0.3);
            int alpha = Math.max(8, Math.round(a * 255));
            int y = h - (int) Math.round(h * Math.max(b, 0.04)) - 14;
            var lines = mc.font.split(net.minecraft.network.chat.Component.literal(caption), Math.min(w - 40, 360));
            y -= (lines.size() - 1) * 10;
            for (var line : lines) {
                int lw = mc.font.width(line);
                g.drawString(mc.font, line, (w - lw) / 2, y, (alpha << 24) | 0xFFFFFF, true);
                y += 10;
            }
        }
        double f = fade.value();
        if (f > 0.001) g.fill(0, 0, w, h, ((int) Math.round(f * 255) << 24) | (fadeColor & 0xFFFFFF));
        if (mode == Mode.SCENE && skippable) {
            String hint = "Пробел — пропустить";
            g.drawString(mc.font, hint, w - mc.font.width(hint) - 6, h - 12, 0x88FFFFFF, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        scene = null;
        deactivate();
        bars.set(0);
        fade.set(0);
        fovAdd.set(0);
        rollAdd.set(0);
        trauma = 0;
        hudHide = "none";
        locked = false;
        cam = null;
    }
}
