package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.zonarstudio.spraute_engine.entity.ModEntities;
import org.zonarstudio.spraute_engine.entity.SprauteHitboxEntity;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.Map;

/** Solid / trigger collision resolution for scene HITBOX nodes. */
public final class SceneHitboxCollision {
    private SceneHitboxCollision() {}

    public static void resolvePlayer(ServerPlayer player, SceneHitboxUtil.OrientedBox box,
                                     SceneHitboxRuntime runtime, String sceneId, String nodeId,
                                     String nodeName, String triggerId, ScriptManager scripts) {
        AABB playerBox = player.getBoundingBox();
        // Solid VoxelShape keeps the player *outside* the box (AABB no longer overlaps).
        // Inflate slightly so a trigger still fires when walking into / standing on the wall.
        if (runtime.solid) playerBox = playerBox.inflate(0.08);
        boolean intersects = SceneHitboxUtil.intersects(playerBox, box);

        if (runtime.trigger) {
            boolean wasTouching = runtime.isTouching(player.getUUID());
            if (intersects && !wasTouching) {
                runtime.markTouching(player.getUUID(), true);
                if (scripts != null) {
                    scripts.onSceneHitboxTouch(player, sceneId, nodeId, nodeName, triggerId, true);
                }
            } else if (!intersects && wasTouching) {
                runtime.markTouching(player.getUUID(), false);
                if (scripts != null) {
                    scripts.onSceneHitboxTouch(player, sceneId, nodeId, nodeName, triggerId, false);
                }
            }
        }

        // Solid movement is handled before movement by LevelCollisionMixin using
        // the synced SprauteHitboxEntity VoxelShape. No post-tick push belongs here.
    }

    public static void tickState(SceneActiveState state, ServerLevel level) {
        if (level == null) return;
        Map<String, SceneHitboxRuntime> hitboxes = state.hitboxMap();
        if (hitboxes.isEmpty()) return;
        ScriptManager scripts = ScriptManager.getInstance();
        for (Map.Entry<String, SceneHitboxRuntime> entry : hitboxes.entrySet()) {
            String nodeId = entry.getKey();
            SceneHitboxRuntime runtime = entry.getValue();
            SceneNode node = state.document.findNode(nodeId);
            if (node == null || node.type != SceneNodeType.HITBOX) continue;

            SceneTransform world = state.document.worldTransform(nodeId);
            if (world == null) continue;
            syncSolidCollider(state, nodeId, runtime, world, level);
            SceneHitboxUtil.OrientedBox box = SceneHitboxUtil.orientedBox(
                    world, runtime.width, runtime.height, runtime.depth,
                    runtime.offsetX, runtime.offsetY, runtime.offsetZ);

            String triggerId = node.hitboxTriggerId == null ? "" : node.hitboxTriggerId.trim();
            for (ServerPlayer player : level.players()) {
                // Local World clone: only its owner ever collides/triggers on it.
                if (runtime.ownerPlayerUuid != null
                        && !runtime.ownerPlayerUuid.equals(player.getUUID().toString())) {
                    continue;
                }
                resolvePlayer(player, box, runtime, state.sceneId, nodeId, node.name, triggerId, scripts);
            }
        }
    }

    private static void syncSolidCollider(SceneActiveState state, String nodeId,
                                          SceneHitboxRuntime runtime, SceneTransform world,
                                          ServerLevel level) {
        SprauteHitboxEntity collider = state.hitboxCollider(nodeId);
        if (!runtime.solid) {
            if (collider != null) state.removeHitboxCollider(nodeId);
            return;
        }

        if (collider == null || !collider.isAlive()) {
            collider = ModEntities.SPRAUTE_HITBOX.get().create(level);
            if (collider == null) return;
            collider.addTag("spraute_scene_" + state.sceneId);
            collider.addTag("spraute_scene_node_" + nodeId);
            if (runtime.ownerPlayerUuid != null) {
                // Local World clone: hidden from every player but its owner (PlayerEvent.StartTracking).
                collider.addTag(SceneNode.LOCAL_OWNER_TAG_PREFIX + runtime.ownerPlayerUuid);
            }
            state.putHitboxCollider(nodeId, collider);
        }

        collider.setPos(world.px, world.py, world.pz);
        collider.setYRot((float) world.ry);
        collider.configure(
                runtime.width * (float) Math.abs(world.sx),
                runtime.height * (float) Math.abs(world.sy),
                runtime.depth * (float) Math.abs(world.sz),
                runtime.offsetX * (float) world.sx,
                runtime.offsetY * (float) world.sy,
                runtime.offsetZ * (float) world.sz,
                true);
        if (!collider.isAddedToWorld()) level.addFreshEntity(collider);
    }
}
