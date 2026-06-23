package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.CameraHandler;

import java.util.function.Supplier;

public class CameraPacket {

    public static final byte ACTION_START = 0;
    public static final byte ACTION_STOP = 1;
    public static final byte ACTION_MOVE = 2;

    public static final byte LOOK_NONE = 0;
    public static final byte LOOK_ONCE = 1;
    public static final byte LOOK_TRACK = 2;

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

    public CameraPacket(byte action, double x, double y, double z,
                        float yaw, float pitch, float time,
                        boolean smooth, float smoothTime, String dimension,
                        byte lookAtMode, int lookAtEntityId,
                        double lookAtX, double lookAtY, double lookAtZ) {
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
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension,
                                     byte lookAtMode, int lookAtEntityId,
                                     double lookAtX, double lookAtY, double lookAtZ) {
        return new CameraPacket(ACTION_START, x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ);
    }

    public static CameraPacket start(double x, double y, double z,
                                     float yaw, float pitch, float time,
                                     boolean smooth, float smoothTime, String dimension) {
        return start(x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                LOOK_NONE, -1, 0, 0, 0);
    }

    public static CameraPacket stop() {
        return new CameraPacket(ACTION_STOP, 0, 0, 0, 0, 0, 0, false, 0.5f, "",
                LOOK_NONE, -1, 0, 0, 0);
    }

    public static CameraPacket move(double x, double y, double z,
                                    float yaw, float pitch, float smoothTime) {
        return new CameraPacket(ACTION_MOVE, x, y, z, yaw, pitch, 0, true, smoothTime, "",
                LOOK_NONE, -1, 0, 0, 0);
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
        return new CameraPacket(action, x, y, z, yaw, pitch, time, smooth, smoothTime, dimension,
                lookAtMode, lookAtEntityId, lookAtX, lookAtY, lookAtZ);
    }

    public static void handle(CameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            switch (msg.action) {
                case ACTION_START -> CameraHandler.startCamera(
                        msg.x, msg.y, msg.z,
                        msg.yaw, msg.pitch,
                        msg.time, msg.smooth, msg.smoothTime,
                        msg.dimension,
                        msg.lookAtMode, msg.lookAtEntityId,
                        msg.lookAtX, msg.lookAtY, msg.lookAtZ);
                case ACTION_STOP -> CameraHandler.stopCamera();
                case ACTION_MOVE -> CameraHandler.moveCamera(
                        msg.x, msg.y, msg.z,
                        msg.yaw, msg.pitch,
                        msg.smoothTime);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
