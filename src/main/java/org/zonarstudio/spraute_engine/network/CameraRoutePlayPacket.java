package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;
import org.zonarstudio.spraute_engine.script.CameraRouteScriptUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CameraRoutePlayPacket {

    public final float totalDuration;
    public final float returnSmoothTime;
    public final boolean lockMovement;
    public final byte hideGui;
    public final boolean overrideLook;
    public final double lookAtX, lookAtY, lookAtZ;
    public final byte endMode;
    public final double[] xs, ys, zs;
    public final float[] yaws, pitches;
    /** Per-sample timestamps in seconds. Same length as xs/ys/zs. Empty = uniform distribution. */
    public final float[] timeline;
    /** Smooth crossfade from the current camera position to the route start (seconds). 0 = instant cut. */
    public final float transitionSec;

    public CameraRoutePlayPacket(float totalDuration, float returnSmoothTime,
                                 boolean lockMovement, byte hideGui,
                                 boolean overrideLook, double lookAtX, double lookAtY, double lookAtZ,
                                 byte endMode,
                                 double[] xs, double[] ys, double[] zs,
                                 float[] yaws, float[] pitches,
                                 float[] timeline,
                                 float transitionSec) {
        this.totalDuration = totalDuration;
        this.returnSmoothTime = returnSmoothTime;
        this.lockMovement = lockMovement;
        this.hideGui = hideGui;
        this.overrideLook = overrideLook;
        this.lookAtX = lookAtX;
        this.lookAtY = lookAtY;
        this.lookAtZ = lookAtZ;
        this.endMode = endMode;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.yaws = yaws;
        this.pitches = pitches;
        this.timeline = timeline != null ? timeline : new float[0];
        this.transitionSec = Math.max(0f, transitionSec);
    }

    public static CameraRoutePlayPacket from(List<CameraRoutePath.Sample> samples,
                                             float totalDuration, float returnSmoothTime,
                                             boolean lockMovement, byte hideGui,
                                             CameraRouteScriptUtil.LookAtOverride lookAt,
                                             CameraRouteEndMode endMode,
                                             float[] timeline) {
        int n = samples.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        float[] yaws = new float[n];
        float[] pitches = new float[n];
        for (int i = 0; i < n; i++) {
            CameraRoutePath.Sample s = samples.get(i);
            xs[i] = s.x(); ys[i] = s.y(); zs[i] = s.z();
            yaws[i] = s.yaw(); pitches[i] = s.pitch();
        }
        boolean override = lookAt != null;
        CameraRouteEndMode mode = endMode != null ? endMode : CameraRouteEndMode.RETURN_TO_PLAYER;
        return new CameraRoutePlayPacket(totalDuration, returnSmoothTime, lockMovement, hideGui,
                override, override ? lookAt.x() : 0, override ? lookAt.y() : 0, override ? lookAt.z() : 0,
                (byte) mode.ordinal(), xs, ys, zs, yaws, pitches,
                timeline != null ? timeline : new float[0], 0f);
    }

    /** Factory with transition (smooth cross-fade from active camera to route start). */
    public static CameraRoutePlayPacket from(List<CameraRoutePath.Sample> samples,
                                             float totalDuration, float returnSmoothTime,
                                             boolean lockMovement, byte hideGui,
                                             CameraRouteScriptUtil.LookAtOverride lookAt,
                                             CameraRouteEndMode endMode,
                                             float[] timeline,
                                             float transitionSec) {
        int n = samples.size();
        double[] xs = new double[n]; double[] ys = new double[n]; double[] zs = new double[n];
        float[] yaws = new float[n]; float[] pitches = new float[n];
        for (int i = 0; i < n; i++) {
            CameraRoutePath.Sample s = samples.get(i);
            xs[i] = s.x(); ys[i] = s.y(); zs[i] = s.z();
            yaws[i] = s.yaw(); pitches[i] = s.pitch();
        }
        boolean override = lookAt != null;
        CameraRouteEndMode mode = endMode != null ? endMode : CameraRouteEndMode.RETURN_TO_PLAYER;
        return new CameraRoutePlayPacket(totalDuration, returnSmoothTime, lockMovement, hideGui,
                override, override ? lookAt.x() : 0, override ? lookAt.y() : 0, override ? lookAt.z() : 0,
                (byte) mode.ordinal(), xs, ys, zs, yaws, pitches,
                timeline != null ? timeline : new float[0], transitionSec);
    }

    /** Backward-compat factory without timeline (uniform distribution). */
    public static CameraRoutePlayPacket from(List<CameraRoutePath.Sample> samples,
                                             float totalDuration, float returnSmoothTime,
                                             boolean lockMovement, byte hideGui,
                                             CameraRouteScriptUtil.LookAtOverride lookAt,
                                             CameraRouteEndMode endMode) {
        return from(samples, totalDuration, returnSmoothTime, lockMovement, hideGui, lookAt, endMode, null, 0f);
    }

    public List<CameraRoutePath.Sample> toSamples() {
        List<CameraRoutePath.Sample> out = new ArrayList<>(xs.length);
        for (int i = 0; i < xs.length; i++) {
            out.add(new CameraRoutePath.Sample(xs[i], ys[i], zs[i], yaws[i], pitches[i]));
        }
        return out;
    }

    public float[] getTimeline() {
        return timeline.length == xs.length ? timeline : null;
    }

    public static void encode(CameraRoutePlayPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.totalDuration);
        buf.writeFloat(msg.returnSmoothTime);
        buf.writeBoolean(msg.lockMovement);
        buf.writeByte(msg.hideGui);
        buf.writeBoolean(msg.overrideLook);
        if (msg.overrideLook) {
            buf.writeDouble(msg.lookAtX);
            buf.writeDouble(msg.lookAtY);
            buf.writeDouble(msg.lookAtZ);
        }
        buf.writeByte(msg.endMode);
        buf.writeVarInt(msg.xs.length);
        for (int i = 0; i < msg.xs.length; i++) {
            buf.writeDouble(msg.xs[i]);
            buf.writeDouble(msg.ys[i]);
            buf.writeDouble(msg.zs[i]);
            buf.writeFloat(msg.yaws[i]);
            buf.writeFloat(msg.pitches[i]);
        }
        // Timeline (may be empty)
        boolean hasTimeline = msg.timeline.length == msg.xs.length;
        buf.writeBoolean(hasTimeline);
        if (hasTimeline) {
            for (float t : msg.timeline) buf.writeFloat(t);
        }
        buf.writeFloat(msg.transitionSec);
    }

    public static CameraRoutePlayPacket decode(FriendlyByteBuf buf) {
        float totalDuration = buf.readFloat();
        float returnSmoothTime = buf.readFloat();
        boolean lockMovement = buf.readBoolean();
        byte hideGui = buf.readByte();
        boolean overrideLook = buf.readBoolean();
        double lookAtX = 0, lookAtY = 0, lookAtZ = 0;
        if (overrideLook) {
            lookAtX = buf.readDouble();
            lookAtY = buf.readDouble();
            lookAtZ = buf.readDouble();
        }
        byte endMode = buf.isReadable() ? buf.readByte() : 0;
        int n = buf.readVarInt();
        double[] xs = new double[n]; double[] ys = new double[n]; double[] zs = new double[n];
        float[] yaws = new float[n]; float[] pitches = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readDouble(); ys[i] = buf.readDouble(); zs[i] = buf.readDouble();
            yaws[i] = buf.readFloat(); pitches[i] = buf.readFloat();
        }
        float[] timeline = new float[0];
        if (buf.isReadable()) {
            boolean hasTimeline = buf.readBoolean();
            if (hasTimeline && n > 0) {
                timeline = new float[n];
                for (int i = 0; i < n; i++) timeline[i] = buf.readFloat();
            }
        }
        float transitionSec = buf.isReadable() ? buf.readFloat() : 0f;
        return new CameraRoutePlayPacket(totalDuration, returnSmoothTime, lockMovement, hideGui,
                overrideLook, lookAtX, lookAtY, lookAtZ, endMode, xs, ys, zs, yaws, pitches, timeline, transitionSec);
    }

    private static CameraRouteEndMode decodeEndMode(byte raw) {
        CameraRouteEndMode[] values = CameraRouteEndMode.values();
        if (raw >= 0 && raw < values.length) return values[raw];
        return CameraRouteEndMode.RETURN_TO_PLAYER;
    }

    public static void handle(CameraRoutePlayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.cameraroute.CameraRoutePlayback.start(
                                msg.toSamples(), msg.getTimeline(),
                                msg.totalDuration, msg.returnSmoothTime,
                                msg.lockMovement, msg.hideGui,
                                msg.overrideLook, msg.lookAtX, msg.lookAtY, msg.lookAtZ,
                                decodeEndMode(msg.endMode), msg.transitionSec)));
        ctx.get().setPacketHandled(true);
    }
}
