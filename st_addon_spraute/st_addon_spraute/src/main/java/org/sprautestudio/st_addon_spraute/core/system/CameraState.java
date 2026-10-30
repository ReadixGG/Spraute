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

    private static final class State {
        boolean active, moving;
        Vec3 position = Vec3.ZERO;
        GameType carriedFrom;
        Vec3 carriedAt;
    }

    private static final Map<java.util.UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean active(ServerPlayer p) {
        return p != null && state(p).active;
    }

    public static Vec3 position(ServerPlayer p) {
        return p == null ? Vec3.ZERO : state(p).position;
    }

    public static void report(ServerPlayer p, double x, double y, double z, boolean on) {
        State st = state(p);
        Vec3 now = new Vec3(x, y, z);
        boolean moved = on && now.distanceToSqr(st.position) > MOVED;
        if (st.moving && !moved) {
            Map<String, Object> d = new HashMap<>();
            d.put("x", x);
            d.put("y", y);
            d.put("z", z);
            GameEvents.fire("cameraend", p, d);
        }
        st.moving = moved;
        st.active = on;
        st.position = now;
        if (st.carriedFrom == null) return;
        if (on) p.teleportTo(x, y, z);
        else carry(p, false);
    }

    public static void carry(ServerPlayer p, boolean on) {
        if (p == null) return;
        State st = state(p);
        if (on && st.carriedFrom == null) {
            st.carriedFrom = p.gameMode.getGameModeForPlayer();
            st.carriedAt = p.position();
            p.setGameMode(GameType.SPECTATOR);
        } else if (!on && st.carriedFrom != null) {
            p.setGameMode(st.carriedFrom);
            p.teleportTo(st.carriedAt.x, st.carriedAt.y, st.carriedAt.z);
            st.carriedFrom = null;
        }
    }

    public static void left(ServerPlayer p) {
        carry(p, false);
        STATES.remove(p.getUUID());
    }

    public static void unload() {
        STATES.clear();
    }
}
