package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.entity.SprauteModelEntity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.GeoEntityScriptUtil;

/**
 * Syncs live scene-node transforms from the (possibly editor-mutated) document
 * onto spawned world entities. Safe to call from the server thread.
 */
public final class SceneLiveSync {
    private SceneLiveSync() {}

    /**
     * Pulls physical NPC/MODEL movement (gravity, AI, script movement) back into
     * the live scene document. Because the walk is parent-first, descendants and
     * child HITBOX nodes immediately inherit the updated transform this tick.
     */
    public static void captureRuntimeEntityTransforms(SceneActiveState state, ServerLevel level) {
        if (state == null || level == null) return;
        state.document.walk((node, parentWorld) -> {
            if (node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL) return;
            Entity entity = state.resolveEntity(node.id, level);
            if (entity == null || !entity.isAlive()) return;

            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            world.px = entity.getX();
            world.py = entity.getY();
            world.pz = entity.getZ();
            world.ry = entity.getYRot();
            SceneTransform actualParentWorld = state.document.parentWorldTransform(node);
            node.transform = SceneTransform.toLocal(actualParentWorld, world);
        });
    }

    public static void syncNodeSubtree(String sceneId, String nodeId) {
        if (sceneId == null || nodeId == null) return;
        SceneActiveState state = SceneRuntime.getActiveState(sceneId);
        if (state == null) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        SceneRuntime.ensureLiveSpawned(sceneId, nodeId, level, source);
        syncSubtree(state, nodeId, level);
        SceneNode edited = state.document.findNode(nodeId);
        if (edited != null && edited.type == SceneNodeType.PLAYERS && server != null) {
            PlayerVarsOps.applyDefaultsFrom(state, server);
        }
    }

    public static void syncSubtree(SceneActiveState state, String rootId, ServerLevel level) {
        if (state == null || rootId == null || level == null) return;
        state.document.walk((node, parentWorld) -> {
            if (!state.document.isSameOrDescendant(rootId, node.id)) return;
            if (!node.hasTransform()) return;
            // Nodes whose entity belongs to a dedicated runtime (Java-registered CUSTOM types,
            // world-space canvases) have their position/rotation rewritten every tick by that
            // runtime. Re-teleporting them here too — from this walk's own parentWorld snapshot
            // — only fights it and can make the entity jump/drift instead of tracking the node.
            if (node.type == SceneNodeType.CUSTOM || SceneWorldCanvasRuntime.isWorldCanvas(node)) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            Entity entity = state.resolveEntity(node.id, level);
            if (entity == null) {
                if (node.type == SceneNodeType.HITBOX) {
                    SceneHitboxRuntime hb = state.hitboxRuntime(node.id);
                    if (hb != null) hb.syncFromNode(node);
                } else if (node.type == SceneNodeType.LIGHT) {
                    SceneLightRuntime light = state.lightRuntime(node.id);
                    if (light != null) light.tick(state, level, node);
                } else if (node.type == SceneNodeType.AUDIO_SOURCE) {
                    SceneAudioRuntime audio = state.audioRuntime(node.id);
                    if (audio != null) audio.syncNow(state, level, node);
                } else if (node.type == SceneNodeType.POST_PROCESS) {
                    ScenePostProcessRuntime post = state.postProcessRuntime(node.id);
                    if (post != null) post.syncNow(state, level, node);
                } else if (node.type == SceneNodeType.PARTICLE && node.particles(false) != null) {
                    SceneParticleEmitter.syncCustomServer(level, node, world);
                }
                return;
            }
            entity.teleportTo(world.px, world.py, world.pz);
            entity.setYRot((float) world.ry);
            entity.setXRot((float) world.rx);
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = (float) world.ry;
                living.yHeadRot = (float) world.ry;
            }
            if (GeoEntityScriptUtil.isGeoEntity(entity)) {
                GeoEntityScriptUtil.setModelRotationX(entity, (float) world.rx);
                GeoEntityScriptUtil.setModelRotationZ(entity, (float) world.rz);
                GeoEntityScriptUtil.setModelScale(entity, (float) world.sx, (float) world.sy, (float) world.sz);
            }
            syncVisuals(node, entity);
        });
    }

    /** Editor property edits appear on live entities immediately. */
    private static void syncVisuals(SceneNode node, Entity entity) {
        if (entity instanceof SprauteNpcEntity npc) {
            NpcNodeBridge.applyNodeToNpc(node, npc);
        } else if (entity instanceof SprauteModelEntity model) {
            if (notBlank(node.model)) model.setModel(node.model);
            if (notBlank(node.texture)) model.setTexture(node.texture);
            if (notBlank(node.animation)) model.setAnimation(node.animation);
            if (notBlank(node.idleAnimation)) model.setIdleAnim(node.idleAnimation);
            model.setCustomName(net.minecraft.network.chat.Component.literal(node.name));
            model.setCustomNameVisible(node.nameVisible);
            model.setSilent(node.silent);
            model.setGlowingTag(node.glowing);
            model.setTranslucent(node.translucent);
            model.setOpacity(node.opacity);
            model.setLodEnabled(node.lodEnabled);
            model.setRenderDistance(node.renderDistance);
            model.setForceVisible(node.forceVisible);
            model.setNoGravity(node.noGravity);
            model.setInvulnerable(node.invulnerable);
        } else if (entity instanceof SprauteBillboardEntity billboard) {
            if (notBlank(node.texture)) billboard.setTexture(SceneRuntime.resolveBillboardTexture(node.texture));
            billboard.setBillboardWidth(Math.max(0.01f, node.billboardWidth));
            billboard.setBillboardHeight(Math.max(0.01f, node.billboardHeight));
            billboard.setSeeThrough(node.billboardSeeThrough);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
