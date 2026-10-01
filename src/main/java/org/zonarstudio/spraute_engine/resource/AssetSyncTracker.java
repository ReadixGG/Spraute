package org.zonarstudio.spraute_engine.resource;

import net.minecraft.server.level.ServerPlayer;
import org.zonarstudio.spraute_engine.script.ScriptCustomEvents;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player asset-sync phase for scripts: {@code on}/{@code await assetsAllow},
 * {@code assetsReady}, {@code assetsDeny}, and {@code assetsAllowed(player)}.
 */
public final class AssetSyncTracker {
    public enum Phase { PENDING, ALLOWED, DENIED, READY }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        Phase phase = Phase.PENDING;
        boolean allowed;
        int files;
        boolean allowFired;
        boolean denyFired;
        boolean readyFired;
    }

    private AssetSyncTracker() {}

    public static void clear(UUID playerId) {
        if (playerId != null) STATES.remove(playerId);
    }

    public static boolean isAllowed(UUID playerId) {
        State s = STATES.get(playerId);
        return s != null && s.allowed && (s.phase == Phase.ALLOWED || s.phase == Phase.READY);
    }

    public static boolean isDenied(UUID playerId) {
        State s = STATES.get(playerId);
        if (s == null) return false;
        return s.phase == Phase.DENIED || (s.phase == Phase.READY && !s.allowed);
    }

    public static boolean isReady(UUID playerId) {
        State s = STATES.get(playerId);
        return s != null && (s.phase == Phase.READY || s.phase == Phase.DENIED);
    }

    public static boolean isEngineEvent(String eventName) {
        String key = ScriptCustomEvents.key(eventName);
        return isAllowEvent(key) || isReadyEvent(key) || isDenyEvent(key);
    }

    public static boolean isAllowEvent(String key) {
        return "assetsallow".equals(key) || "assetsallowed".equals(key);
    }

    public static boolean isReadyEvent(String key) {
        return "assetsready".equals(key) || "assetsdone".equals(key);
    }

    public static boolean isDenyEvent(String key) {
        return "assetsdeny".equals(key) || "assetsdenied".equals(key);
    }

    /** Payload if this event already happened for the player; otherwise null. */
    public static List<Object> currentPayload(String eventName, ServerPlayer player) {
        if (player == null) return null;
        State s = STATES.get(player.getUUID());
        if (s == null) return null;
        String key = ScriptCustomEvents.key(eventName);
        if (isAllowEvent(key)) {
            if (!s.allowed || s.phase == Phase.PENDING) return null;
            return List.of(player);
        }
        if (isDenyEvent(key)) {
            if (!isDenied(player.getUUID())) return null;
            return List.of(player);
        }
        if (isReadyEvent(key)) {
            if (!isReady(player.getUUID())) return null;
            return List.of(player, s.files, s.allowed);
        }
        return null;
    }

    public static void markPending(ServerPlayer player) {
        if (player == null) return;
        STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static void markAllowed(ServerPlayer player) {
        if (player == null) return;
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (s.phase == Phase.DENIED || s.phase == Phase.READY) return;
        s.phase = Phase.ALLOWED;
        s.allowed = true;
        fireAllow(player, s);
    }

    public static void markDenied(ServerPlayer player) {
        if (player == null) return;
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (s.phase == Phase.READY && s.readyFired) return;
        s.phase = Phase.DENIED;
        s.allowed = false;
        fireDeny(player, s);
        fireReady(player, s);
    }

    public static void markReady(ServerPlayer player, int files, boolean allowed) {
        if (player == null) return;
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        s.files = files;
        s.allowed = allowed;
        s.phase = Phase.READY;
        if (allowed) fireAllow(player, s);
        else fireDeny(player, s);
        fireReady(player, s);
    }

    public static void markSkipped(ServerPlayer player) {
        markReady(player, 0, true);
    }

    private static void fireAllow(ServerPlayer player, State s) {
        if (s.allowFired) return;
        s.allowFired = true;
        fire("assetsAllow", List.of(player));
    }

    private static void fireDeny(ServerPlayer player, State s) {
        if (s.denyFired) return;
        s.denyFired = true;
        fire("assetsDeny", List.of(player));
    }

    private static void fireReady(ServerPlayer player, State s) {
        if (s.readyFired) return;
        s.readyFired = true;
        fire("assetsReady", List.of(player, s.files, s.allowed));
    }

    private static void fire(String name, List<Object> payload) {
        ScriptManager manager = ScriptManager.getInstance();
        if (manager != null) {
            manager.onCustomEvent(name, payload);
        }
    }
}
