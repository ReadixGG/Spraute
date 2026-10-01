package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.mixin.CameraInvoker;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Additive camera motion: walk bob, land punch, Perlin shake, view punch.
 * Applied on the camera transform (Unity Cinemachine / Unreal CameraShake), not as post-process.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class CameraEffectsClient {
    private static final int MAX_SHAKES = 8;
    private static final List<Shake> SHAKES = new ArrayList<>();
    private static float punchYaw;
    private static float punchPitch;
    private static float punchRoll;
    private static float bobPhase;
    private static float bobFade;
    private static float landPunch;
    private static boolean wasOnGround = true;
    private static double lastFall;
    private static long lastNanos = System.nanoTime();

    private CameraEffectsClient() {}

    public static boolean suppressVanillaBob() {
        PlayerCameraPose.Rig rig = rig();
        return rig != null && (rig.disableEffects() || rig.walkBob());
    }

    public static boolean suppressVanillaHurt() {
        PlayerCameraPose.Rig rig = rig();
        return rig != null && rig.disableEffects();
    }

    public static void shake(float intensity, float duration, float frequency) {
        if (intensity < 0.001f || duration < 0.02f) return;
        if (SHAKES.size() >= MAX_SHAKES) SHAKES.remove(0);
        float freq = frequency > 0.1f ? frequency : 18f;
        SHAKES.add(new Shake(Math.min(2.5f, intensity), duration, freq,
                (float) (System.nanoTime() * 1e-9)));
    }

    public static void punch(float yaw, float pitch, float roll) {
        punchYaw += yaw;
        punchPitch += pitch;
        punchRoll += roll;
    }

    public static void stopTransient() {
        SHAKES.clear();
        punchYaw = 0f;
        punchPitch = 0f;
        punchRoll = 0f;
    }

    public static void clear() {
        stopTransient();
        bobPhase = 0f;
        bobFade = 0f;
        landPunch = 0f;
        wasOnGround = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        punchYaw *= 0.72f;
        punchPitch *= 0.72f;
        punchRoll *= 0.72f;
        if (Math.abs(punchYaw) < 0.01f) punchYaw = 0f;
        if (Math.abs(punchPitch) < 0.01f) punchPitch = 0f;
        if (Math.abs(punchRoll) < 0.01f) punchRoll = 0f;
        landPunch *= 0.78f;
        if (landPunch < 0.01f) landPunch = 0f;
    }

    private static Sample lastSample = new Sample();

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (SprauteEditorState.isRenderingEditorCamera()) return;
        lastSample = sample();
        Sample s = lastSample;
        if (s.isIdentity()) return;
        float yaw = event.getYaw() + s.yaw;
        float pitch = Mth.clamp(event.getPitch() + s.pitch, -90f, 90f);
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(event.getRoll() + s.roll);
        Camera camera = event.getCamera();
        if (camera == null) return;
        Vec3 pos = camera.getPosition();
        Vec3 boom = offset(s.x, s.y, s.z, yaw, pitch);
        CameraInvoker inv = (CameraInvoker) camera;
        inv.spraute$setPosition(pos.x + boom.x, pos.y + boom.y, pos.z + boom.z);
        inv.spraute$setRotation(yaw, pitch);
    }

    public static void applyFov(ViewportEvent.ComputeFov event) {
        if (SprauteEditorState.isRenderingEditorCamera()) return;
        float fov = lastSample.fov;
        if (Math.abs(fov) < 0.01f) return;
        event.setFOV(event.getFOV() + fov);
    }

    private static Sample sample() {
        Sample s = new Sample();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) * 1e-9f);
        lastNanos = now;

        float t = (float) (now * 1e-9);
        Iterator<Shake> it = SHAKES.iterator();
        while (it.hasNext()) {
            Shake shake = it.next();
            float age = t - shake.start;
            if (age >= shake.duration) {
                it.remove();
                continue;
            }
            float u = age / shake.duration;
            float env = 1f - u;
            env *= env * (3f - 2f * env);
            float amp = shake.intensity * env;
            float w = shake.frequency * age;
            s.yaw += noise(w, shake.seed + 1.1f) * amp * 10f;
            s.pitch += noise(w * 1.13f, shake.seed + 2.7f) * amp * 7f;
            s.roll += noise(w * 0.91f, shake.seed + 4.3f) * amp * 5f;
            s.x += noise(w * 0.77f, shake.seed + 6.1f) * amp * 0.07f;
            s.y += noise(w * 1.31f, shake.seed + 8.9f) * amp * 0.06f;
            s.z += noise(w * 1.07f, shake.seed + 11.3f) * amp * 0.05f;
        }

        s.yaw += punchYaw;
        s.pitch += punchPitch;
        s.roll += punchRoll;

        PlayerCameraPose.Rig rig = rig();
        boolean cinematic = CameraHandler.isActive();
        if (rig != null && rig.walkBob() && rig.walkBobAmount() > 0.001f && !cinematic) {
            addWalkBob(s, rig.walkBobAmount(), dt);
        }
        return s;
    }

    private static void addWalkBob(Sample s, float amount, float dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isPassenger() || player.getAbilities().flying) {
            bobFade = Math.max(0f, bobFade - dt * 6f);
            return;
        }
        float speed = (float) player.getDeltaMovement().horizontalDistance();
        boolean ground = SprauteEntityCompat.onGround(player);
        double fall = player.getDeltaMovement().y;
        if (!wasOnGround && ground && lastFall < -0.28) {
            landPunch = Math.min(1.4f, landPunch + (float) (-lastFall) * 0.85f);
        }
        wasOnGround = ground;
        lastFall = fall;

        float target = ground ? Mth.clamp(speed / 0.21f, 0f, 1.5f) : 0f;
        bobFade += (target - bobFade) * Math.min(1f, dt * 10f);
        if (bobFade > 0.01f && ground) {
            bobPhase += dt * (8.5f + speed * 18f);
        }
        float a = amount * bobFade;
        s.y += Math.sin(bobPhase) * 0.038f * a;
        s.x += Math.cos(bobPhase * 0.5) * 0.018f * a;
        s.pitch += (float) Math.cos(bobPhase) * 0.42f * a;
        s.roll += (float) Math.sin(bobPhase * 0.5) * 0.28f * a;
        s.y -= landPunch * 0.07f * amount;
        s.pitch += landPunch * 2.8f * amount;
        s.fov += landPunch * 1.2f * amount;
        if (player.isSprinting() && bobFade > 0.2f) {
            s.fov += 1.1f * amount * Math.min(1f, bobFade);
        }
    }

    private static float noise(float t, float seed) {
        return (float) (Math.sin(t * 2.399 + seed) * 0.55
                + Math.sin(t * 5.173 + seed * 1.7) * 0.30
                + Math.sin(t * 11.31 + seed * 2.3) * 0.15);
    }

    private static Vec3 offset(double x, double y, double z, float yaw, float pitch) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90f);
        Vec3 up = forward.cross(right).scale(-1);
        return right.scale(x).add(up.scale(y)).add(forward.scale(-z));
    }

    private static PlayerCameraPose.Rig rig() {
        PlayerCameraPose.Rest rest = CameraHandler.restPose();
        return rest == null ? null : rest.rig();
    }

    private static final class Shake {
        final float intensity;
        final float duration;
        final float frequency;
        final float seed;
        final float start;

        Shake(float intensity, float duration, float frequency, float start) {
            this.intensity = intensity;
            this.duration = duration;
            this.frequency = frequency;
            this.seed = start * 13.17f;
            this.start = start;
        }
    }

    private static final class Sample {
        float x, y, z;
        float yaw, pitch, roll;
        float fov;

        boolean isIdentity() {
            return Math.abs(x) < 1e-5 && Math.abs(y) < 1e-5 && Math.abs(z) < 1e-5
                    && Math.abs(yaw) < 1e-4 && Math.abs(pitch) < 1e-4 && Math.abs(roll) < 1e-4
                    && Math.abs(fov) < 1e-3;
        }
    }
}
