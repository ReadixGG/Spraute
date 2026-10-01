package org.zonarstudio.spraute_engine.scene;

import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live server-side state of one scene HITBOX node. */
public final class SceneHitboxRuntime {
    public volatile boolean solid;
    public volatile boolean trigger;
    public volatile float width;
    public volatile float height;
    public volatile float depth;
    public volatile float offsetX;
    public volatile float offsetY;
    public volatile float offsetZ;

    private final ConcurrentHashMap<UUID, Boolean> perPlayerSolid = new ConcurrentHashMap<>();
    private final Set<UUID> touching = ConcurrentHashMap.newKeySet();
    /** Last server-authoritative position where the player did not intersect this hitbox. */
    private final Map<UUID, Vec3> lastOutsidePosition = new ConcurrentHashMap<>();
    /**
     * Set when this hitbox is a per-player Local World clone (see {@link SceneNode#ownerPlayerUuid}).
     * Non-null means the hitbox is never solid/touchable for anyone but this player — the
     * physical collider entity may still exist in the shared world, but only the owner ever
     * collides/triggers on it.
     */
    final String ownerPlayerUuid;

    SceneHitboxRuntime(SceneNode node) {
        this.ownerPlayerUuid = node.ownerPlayerUuid;
        syncFromNode(node);
    }

    void syncFromNode(SceneNode node) {
        solid = node.hitboxSolid;
        trigger = node.hitboxTrigger;
        width = node.hitboxWidth;
        height = node.hitboxHeight;
        depth = node.hitboxDepth;
        offsetX = node.hitboxOffsetX;
        offsetY = node.hitboxOffsetY;
        offsetZ = node.hitboxOffsetZ;
    }

    public boolean isSolidFor(UUID playerId) {
        if (ownerPlayerUuid != null
                && (playerId == null || !ownerPlayerUuid.equals(playerId.toString()))) {
            // Local World clone: solid/collidable for its owner only.
            return false;
        }
        Boolean override = perPlayerSolid.get(playerId);
        if (override != null) return override;
        return solid;
    }

    public void setSolidForPlayer(UUID playerId, boolean value) {
        if (playerId != null) perPlayerSolid.put(playerId, value);
    }

    public void clearSolidForPlayer(UUID playerId) {
        if (playerId != null) perPlayerSolid.remove(playerId);
    }

    public boolean isTouching(UUID playerId) {
        return playerId != null && touching.contains(playerId);
    }

    Set<UUID> touchingPlayers() {
        return Collections.unmodifiableSet(touching);
    }

    boolean markTouching(UUID playerId, boolean inside) {
        if (playerId == null) return false;
        if (inside) return touching.add(playerId);
        return touching.remove(playerId);
    }

    void clearTouching() {
        touching.clear();
        lastOutsidePosition.clear();
    }

    Vec3 lastOutsidePosition(UUID playerId) {
        return playerId == null ? null : lastOutsidePosition.get(playerId);
    }

    void rememberOutsidePosition(UUID playerId, Vec3 position) {
        if (playerId != null && position != null) lastOutsidePosition.put(playerId, position);
    }
}
