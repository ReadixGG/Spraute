package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;
import org.zonarstudio.spraute_engine.scene.PlayerCameraSync;

import java.util.function.Supplier;

/** Syncs the per-player CAMERA rest pose. C2S from the editor; S2C applies on the client. */
public final class PlayerCameraRestPacket {
    private final String playerUuid;
    private final PlayerCameraPose.Rest rest;
    private final boolean authoring;

    public PlayerCameraRestPacket(String playerUuid, PlayerCameraPose.Rest rest, boolean authoring) {
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        this.rest = rest == null ? PlayerCameraPose.VANILLA : rest;
        this.authoring = authoring;
    }

    public static PlayerCameraRestPacket clientApply(String playerUuid, PlayerCameraPose.Rest rest) {
        return new PlayerCameraRestPacket(playerUuid, rest, false);
    }

    public static PlayerCameraRestPacket editor(String playerUuid, PlayerCameraPose.Rest rest) {
        return new PlayerCameraRestPacket(playerUuid, rest, true);
    }

    public PlayerCameraPose.Rest rest() {
        return rest;
    }

    public static void encode(PlayerCameraRestPacket message, FriendlyByteBuf buffer) {
        PlayerCameraPose.Rest rest = message.rest;
        PlayerCameraPose.Rig rig = rest.rig();
        buffer.writeUtf(message.playerUuid);
        buffer.writeDouble(rest.x());
        buffer.writeDouble(rest.y());
        buffer.writeDouble(rest.z());
        buffer.writeFloat(rest.yaw());
        buffer.writeFloat(rest.pitch());
        buffer.writeFloat(rest.fov());
        buffer.writeFloat(rest.distance());
        buffer.writeBoolean(rig.followPlayer());
        buffer.writeBoolean(rig.lockYaw());
        buffer.writeBoolean(rig.lockPitch());
        buffer.writeBoolean(rig.orbit());
        buffer.writeFloat(rig.orbitYaw());
        buffer.writeFloat(rig.orbitPitch());
        buffer.writeDouble(rig.pivotX());
        buffer.writeDouble(rig.pivotY());
        buffer.writeDouble(rig.pivotZ());
        buffer.writeBoolean(rig.collide());
        buffer.writeBoolean(rig.showHand());
        buffer.writeBoolean(rig.clearOcclusion());
        buffer.writeFloat(rig.occlusionAlpha());
        buffer.writeBoolean(rig.moveRelative());
        buffer.writeBoolean(rig.separateTurn());
        buffer.writeBoolean(rig.screenRaycast());
        buffer.writeFloat(rig.occlusionRadius());
        buffer.writeBoolean(rig.lookAtInteract());
        buffer.writeFloat(rig.turnSmooth());
        buffer.writeBoolean(rig.disableEffects());
        buffer.writeBoolean(rig.walkBob());
        buffer.writeFloat(rig.walkBobAmount());
        buffer.writeByte(rig.hideGui());
        buffer.writeBoolean(rig.lockX());
        buffer.writeBoolean(rig.lockY());
        buffer.writeBoolean(rig.lockZ());
        buffer.writeDouble(rig.worldX());
        buffer.writeDouble(rig.worldY());
        buffer.writeDouble(rig.worldZ());
        buffer.writeBoolean(message.authoring);
    }

    public static PlayerCameraRestPacket decode(FriendlyByteBuf buffer) {
        String uuid = buffer.readUtf(64);
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        float fov = buffer.readFloat();
        float distance = buffer.readFloat();
        boolean followPlayer = buffer.readBoolean();
        boolean lockYaw = buffer.readBoolean();
        boolean lockPitch = buffer.readBoolean();
        boolean orbit = buffer.readBoolean();
        float orbitYaw = buffer.readFloat();
        float orbitPitch = buffer.readFloat();
        double pivotX = buffer.readDouble();
        double pivotY = buffer.readDouble();
        double pivotZ = buffer.readDouble();
        boolean collide = buffer.readBoolean();
        boolean showHand = buffer.readBoolean();
        boolean clearOcclusion = false;
        float occlusionAlpha = 0f;
        float occlusionRadius = 0f;
        boolean moveRelative = false;
        boolean separateTurn = false;
        boolean screenRaycast = false;
        boolean lookAtInteract = false;
        float turnSmooth = 0f;
        boolean disableEffects = false;
        boolean walkBob = false;
        float walkBobAmount = 1f;
        byte hideGui = 0;
        // Protocol 22+: clearOcclusion + alpha before authoring. Older packets only had authoring.
        if (buffer.readableBytes() > 1) {
            clearOcclusion = buffer.readBoolean();
            occlusionAlpha = buffer.readFloat();
        }
        // Protocol 24+: moveRelative before authoring.
        if (buffer.readableBytes() > 1) {
            moveRelative = buffer.readBoolean();
        }
        // Protocol 25+: separateTurn before authoring.
        if (buffer.readableBytes() > 1) {
            separateTurn = buffer.readBoolean();
        }
        // Protocol 26+: screenRaycast before authoring.
        if (buffer.readableBytes() > 1) {
            screenRaycast = buffer.readBoolean();
        }
        // Protocol 27+: occlusionRadius before authoring.
        if (buffer.readableBytes() > 4) {
            occlusionRadius = buffer.readFloat();
        }
        // Protocol 28+: lookAtInteract before authoring.
        if (buffer.readableBytes() > 1) {
            lookAtInteract = buffer.readBoolean();
        }
        // Protocol 29+: turnSmooth before authoring.
        if (buffer.readableBytes() > 4) {
            turnSmooth = buffer.readFloat();
        }
        // Protocol 30+: disableEffects / walkBob / amount before authoring.
        if (buffer.readableBytes() > 1) {
            disableEffects = buffer.readBoolean();
        }
        if (buffer.readableBytes() > 1) {
            walkBob = buffer.readBoolean();
        }
        if (buffer.readableBytes() > 4) {
            walkBobAmount = buffer.readFloat();
        }
        // Protocol 31+: hideGui before authoring.
        if (buffer.readableBytes() > 1) {
            hideGui = buffer.readByte();
        }
        boolean lockX = false;
        boolean lockY = false;
        boolean lockZ = false;
        double worldX = 0;
        double worldY = 0;
        double worldZ = 0;
        // Per-axis world hold: 3 bools + 3 doubles before authoring.
        if (buffer.readableBytes() > 1) {
            lockX = buffer.readBoolean();
        }
        if (buffer.readableBytes() > 1) {
            lockY = buffer.readBoolean();
        }
        if (buffer.readableBytes() > 1) {
            lockZ = buffer.readBoolean();
        }
        if (buffer.readableBytes() > 8) {
            worldX = buffer.readDouble();
        }
        if (buffer.readableBytes() > 8) {
            worldY = buffer.readDouble();
        }
        if (buffer.readableBytes() > 8) {
            worldZ = buffer.readDouble();
        }
        boolean authoring = buffer.readBoolean();
        PlayerCameraPose.Rig rig = new PlayerCameraPose.Rig(followPlayer, lockYaw, lockPitch,
                orbit, orbitYaw, orbitPitch, pivotX, pivotY, pivotZ, collide, showHand,
                clearOcclusion, occlusionAlpha, occlusionRadius, moveRelative, separateTurn,
                screenRaycast, lookAtInteract, turnSmooth,
                disableEffects, walkBob, walkBobAmount, hideGui,
                lockX, lockY, lockZ, worldX, worldY, worldZ);
        return new PlayerCameraRestPacket(uuid,
                new PlayerCameraPose.Rest(x, y, z, yaw, pitch, fov, distance, rig), authoring);
    }

    public static void handle(PlayerCameraRestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                if (!sender.hasPermissions(2)) return;
                if (message.authoring && (message.playerUuid == null || message.playerUuid.isBlank())) {
                    // PLAYERS-node defaults: copy onto every live player.
                    PlayerCameraSync.applyAuthoring(message.rest());
                } else {
                    String uuid = message.playerUuid == null || message.playerUuid.isBlank()
                            ? sender.getUUID().toString() : message.playerUuid;
                    PlayerCameraSync.applyPlayer(uuid, message.rest());
                }
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        org.zonarstudio.spraute_engine.client.CameraHandler.applyRestPacket(
                                message.playerUuid, message.rest()));
            }
        });
        context.setPacketHandled(true);
    }
}
