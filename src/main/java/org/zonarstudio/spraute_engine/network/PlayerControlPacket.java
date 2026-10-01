package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.client.PlayerControlHandler;

import java.util.function.Supplier;

/** Sync independent player move/look/F5 locks and forced facing to the client. */
public class PlayerControlPacket {
    public static final byte ACTION_LOCK_MOVE = 0;
    public static final byte ACTION_LOCK_LOOK = 1;
    public static final byte ACTION_SET_LOOK = 2;
    public static final byte ACTION_CLEAR = 3;
    public static final byte ACTION_LOCK_PERSPECTIVE = 4;
    public static final byte ACTION_LOCK_MOVE_DIRS = 5;

    public final byte action;
    public final boolean locked;
    public final float yaw;
    public final float pitch;

    public PlayerControlPacket(byte action, boolean locked, float yaw, float pitch) {
        this.action = action;
        this.locked = locked;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static PlayerControlPacket lockMove(boolean locked) {
        return new PlayerControlPacket(ACTION_LOCK_MOVE, locked, 0f, 0f);
    }

    public static PlayerControlPacket lockLook(boolean locked, float yaw, float pitch) {
        return new PlayerControlPacket(ACTION_LOCK_LOOK, locked, yaw, pitch);
    }

    public static PlayerControlPacket setLook(float yaw, float pitch) {
        return new PlayerControlPacket(ACTION_SET_LOOK, false, yaw, pitch);
    }

    public static PlayerControlPacket lockPerspective(boolean locked) {
        return new PlayerControlPacket(ACTION_LOCK_PERSPECTIVE, locked, 0f, 0f);
    }

    public static PlayerControlPacket lockMoveDirs(int mask) {
        return new PlayerControlPacket(ACTION_LOCK_MOVE_DIRS, mask != 0, mask, 0f);
    }

    public static PlayerControlPacket clear() {
        return new PlayerControlPacket(ACTION_CLEAR, false, 0f, 0f);
    }

    public static void encode(PlayerControlPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeBoolean(msg.locked);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
    }

    public static PlayerControlPacket decode(FriendlyByteBuf buf) {
        return new PlayerControlPacket(buf.readByte(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(PlayerControlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (msg.action) {
                case ACTION_LOCK_MOVE -> PlayerControlHandler.setMoveLocked(msg.locked);
                case ACTION_LOCK_LOOK -> PlayerControlHandler.setLookLocked(msg.locked, msg.yaw, msg.pitch);
                case ACTION_SET_LOOK -> PlayerControlHandler.setLook(msg.yaw, msg.pitch);
                case ACTION_LOCK_PERSPECTIVE -> PlayerControlHandler.setPerspectiveLocked(msg.locked);
                case ACTION_LOCK_MOVE_DIRS -> PlayerControlHandler.setMoveDirMask(Math.round(msg.yaw));
                case ACTION_CLEAR -> PlayerControlHandler.clear();
                default -> {}
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
