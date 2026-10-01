package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.CameraHandler;
import org.zonarstudio.spraute_engine.script.CameraControls;

import java.util.function.Supplier;

public class CameraPacket {

    public static final byte ACTION_START = 0;
    public static final byte ACTION_STOP = 1;
    public static final byte ACTION_MOVE = 2;
    /** Only update the look-at target; do not change camera position. */
    public static final byte ACTION_LOOK = 3;

    public static final byte LOOK_NONE = 0;
    public static final byte LOOK_ONCE = 1;
    public static final byte LOOK_TRACK = 2;

    public static final byte HIDE_NONE = 0;
    public static final byte HIDE_MINECRAFT = 1;
    public static final byte HIDE_ALL = 2;

    public final byte action;
    public final double x, y, z;
    public final float yaw, pitch;
    public final float time;
    public final boolean smooth;
    public final float smoothTime;
    public final String dimension;
    public final byte lookAtMode;
    public final int lookAtEntityId;
    public final double lookAtX, lookAtY, lookAtZ;
    public final boolean lockMovement;
    public final byte hideGui;
    public final boolean lockBlocks;

    public CameraPacket(byte action, double x, double y, double z,
                        float yaw, float pitch, float time,
                        boolean smooth, float smoothTime, String dimension,
                        byte lookAtMode, int lookAtEntityId,
                        double lookAtX, double lookAtY, double lookAtZ,
                        boolean lockMovement, byte hideGui, boolean lockBlocks) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.time = time;
        this.smooth = smooth;
        this.smoothTime = smoothTime;
        this.dimension = dimension;
        this.lookAtMode = lookAtMode;
        this.lookAtEntityId = lookAtEntityId;
        this.lookAtX = lookAtX;
        this.lookAtY = lookAtY;
        this.lookAtZ = lookAtZ;
        this.lockMovement = lockMovement;
        this.hideGui = hideGui;
        this.lockBlocks = lockBlocks;
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension,
                                     byte lookAtMode, int lookAtEntityId,
                                     double lookAtX, double lookAtY, double lookAtZ,
                                     boolean lockMovement, byte hideGui, boolean lockBlocks) {
        return new CameraPacket(ACTION_START, x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ, lockMovement, hideGui, lockBlocks);
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension,
                                     byte lookAtMode, int lookAtEntityId,
                                     double lookAtX, double lookAtY, double lookAtZ,
                                     boolean lockMovement, byte hideGui) {
        return start(x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ,
                lockMovement, hideGui, CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension,
                                     byte lookAtMode, int lookAtEntityId,
                                     double lookAtX, double lookAtY, double lookAtZ) {
        return start(x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ,
                CameraControls.DEFAULT_LOCK_MOVEMENT,
                CameraControls.DEFAULT_HIDE_GUI,
                CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension) {
        return start(x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                LOOK_NONE, -1, 0, 0, 0);
    }

    public static CameraPacket stop() {
        return stop(0.5f);
    }

    /** @param returnSmoothTime seconds to blend back to the player; {@code <= 0} = instant */
    public static CameraPacket stop(float returnSmoothTime) {
        boolean doSmooth = returnSmoothTime > 0f;
        return new CameraPacket(ACTION_STOP, 0, 0, 0, 0, 0, 0, doSmooth, Math.max(returnSmoothTime, 0f), "",
                LOOK_NONE, -1, 0, 0, 0, false, HIDE_NONE, false);
    }

    public static CameraPacket reset() {
        return stop(0f);
    }

    public static CameraPacket move(double x, double y, double z,
                                    float yaw, float pitch, float smoothTime,
                                    byte lookAtMode, int lookAtEntityId,
                                    double lookAtX, double lookAtY, double lookAtZ,
                                    boolean lockMovement, byte hideGui, boolean lockBlocks) {
        return new CameraPacket(ACTION_MOVE, x, y, z, yaw, pitch, 0, true, smoothTime, "",
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ, lockMovement, hideGui, lockBlocks);
    }

    public static CameraPacket move(double x, double y, double z,
                                    float yaw, float pitch, float smoothTime,
                                    byte lookAtMode, int lookAtEntityId,
                                    double lookAtX, double lookAtY, double lookAtZ,
                                    boolean lockMovement, byte hideGui) {
        return move(x, y, z, yaw, pitch, smoothTime, lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ,
                lockMovement, hideGui, CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static CameraPacket move(double x, double y, double z,
                                    float yaw, float pitch, float smoothTime,
                                    byte lookAtMode, int lookAtEntityId,
                                    double lookAtX, double lookAtY, double lookAtZ) {
        return move(x, y, z, yaw, pitch, smoothTime, lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ,
                CameraControls.DEFAULT_LOCK_MOVEMENT,
                CameraControls.DEFAULT_HIDE_GUI,
                CameraControls.DEFAULT_LOCK_BLOCKS);
    }

    public static CameraPacket move(double x, double y, double z,
                                    float yaw, float pitch, float smoothTime) {
        return move(x, y, z, yaw, pitch, smoothTime, LOOK_NONE, -1, 0, 0, 0);
    }

    /** Packet that only changes the look-at direction, without moving the camera. */
    public static CameraPacket look(byte lookAtMode, int lookAtEntityId,
                                    double lookAtX, double lookAtY, double lookAtZ) {
        return look(lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ, 0f);
    }

    public static CameraPacket look(byte lookAtMode, int lookAtEntityId,
                                    double lookAtX, double lookAtY, double lookAtZ,
                                    float smoothTime) {
        boolean smooth = smoothTime > 0f;
        return new CameraPacket(ACTION_LOOK, 0, 0, 0, 0, 0, 0, smooth, Math.max(0f, smoothTime), "",
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ, false, HIDE_NONE, false);
    }

    public static void encode(CameraPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
        buf.writeFloat(msg.time);
        buf.writeBoolean(msg.smooth);
        buf.writeFloat(msg.smoothTime);
        buf.writeUtf(msg.dimension);
        buf.writeByte(msg.lookAtMode);
        buf.writeInt(msg.lookAtEntityId);
        buf.writeDouble(msg.lookAtX);
        buf.writeDouble(msg.lookAtY);
        buf.writeDouble(msg.lookAtZ);
        buf.writeBoolean(msg.lockMovement);
        buf.writeByte(msg.hideGui);
        buf.writeBoolean(msg.lockBlocks);
    }

    public static CameraPacket decode(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        float time = buf.readFloat();
        boolean smooth = buf.readBoolean();
        float smoothTime = buf.readFloat();
        String dimension = buf.readUtf();
        byte lookAtMode = buf.readByte();
        int lookAtEntityId = buf.readInt();
        double lookAtX = buf.readDouble();
        double lookAtY = buf.readDouble();
        double lookAtZ = buf.readDouble();
        boolean lockMovement = buf.isReadable() ? buf.readBoolean()
                : CameraControls.DEFAULT_LOCK_MOVEMENT;
        byte hideGui = buf.isReadable() ? buf.readByte()
                : CameraControls.DEFAULT_HIDE_GUI;
        boolean lockBlocks = buf.isReadable() ? buf.readBoolean()
                : CameraControls.DEFAULT_LOCK_BLOCKS;
        return new CameraPacket(action, x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ, lockMovement, hideGui, lockBlocks);
    }

    public static void handle(CameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyClient(msg)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyClient(CameraPacket msg) {
        switch (msg.action) {
            case ACTION_START -> CameraHandler.startCamera(
                    msg.x, msg.y, msg.z,
                    msg.yaw, msg.pitch,
                    msg.time, msg.smooth, msg.smoothTime,
                    msg.dimension,
                    msg.lookAtMode, msg.lookAtEntityId,
                    msg.lookAtX, msg.lookAtY, msg.lookAtZ,
                    msg.lockMovement, msg.hideGui, msg.lockBlocks);
            case ACTION_STOP -> {
                if (msg.smooth && msg.smoothTime > 0f) {
                    CameraHandler.stopCamera(msg.smoothTime);
                } else {
                    CameraHandler.resetCamera();
                }
            }
            case ACTION_MOVE -> CameraHandler.moveCamera(
                    msg.x, msg.y, msg.z,
                    msg.yaw, msg.pitch,
                    msg.smoothTime,
                    msg.lookAtMode, msg.lookAtEntityId,
                    msg.lookAtX, msg.lookAtY, msg.lookAtZ,
                    msg.lockMovement, msg.hideGui, msg.lockBlocks);
            case ACTION_LOOK -> CameraHandler.setExternalLookAt(
                    msg.lookAtMode, msg.lookAtEntityId,
                    msg.lookAtX, msg.lookAtY, msg.lookAtZ, msg.smoothTime);
        }
    }
}
