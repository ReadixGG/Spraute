package org.sprautestudio.st_addon_spraute.core.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;

import java.util.HashMap;
import java.util.Map;

/**
 * What the server knows of the client's camera: if it is on, where it is, when a flight has ended. In "carry" mode the
 * player becomes a spectator and moves with the camera, so the chunks around it load; their game mode and place come back at the end.
 */
public final class CameraState {
    private CameraState() {
    }

    private static final double MOVED = 0.01;

    private static boolean active, moving;
    private static Vec3 position = Vec3.ZERO;
    private static GameType carriedFrom;
    private static Vec3 carriedAt;

    public static boolean active() {
        return active;
    }

    public static Vec3 position() {
        return position;
    }

    public static void report(ServerPlayer p, double x, double y, double z, boolean on) {
        Vec3 now = new Vec3(x, y, z);
        boolean moved = on && now.distanceToSqr(position) > MOVED;
        if (moving && !moved) {
            Map<String, Object> d = new HashMap<>();
            d.put("x", x);
            d.put("y", y);
            d.put("z", z);
            GameEvents.fire("cameraend", p, d);
        }
        moving = moved;
        active = on;
        position = now;
        if (carriedFrom == null) return;
        if (on) p.teleportTo(x, y, z);
        else carry(p, false);
    }

    public static void carry(ServerPlayer p, boolean on) {
        if (p == null) return;
        if (on && carriedFrom == null) {
            carriedFrom = p.gameMode.getGameModeForPlayer();
            carriedAt = p.position();
            p.setGameMode(GameType.SPECTATOR);
        } else if (!on && carriedFrom != null) {
            p.setGameMode(carriedFrom);
            p.teleportTo(carriedAt.x, carriedAt.y, carriedAt.z);
            carriedFrom = null;
        }
    }

    public static void unload() {
        active = moving = false;
        carriedFrom = null;
    }
}
