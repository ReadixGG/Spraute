package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures every live PLAYER node has exactly one CAMERA child and keeps
 * PLAYERS-node defaults in sync with authoring edits.
 */
public final class PlayerCameraNodes {
    private PlayerCameraNodes() {}

    public static SceneNode ensureCamera(SceneNode player, SceneNode playersRoot) {
        return ensureCamera(player, playersRoot, null);
    }

    public static SceneNode ensureCamera(SceneNode player, SceneNode playersRoot, SceneDocument document) {
        if (player == null || player.type != SceneNodeType.PLAYER) return null;
        SceneNode camera = findCamera(player);
        if (camera == null) {
            camera = SceneNode.liveCamera(player.playerUuid, player.name, document);
            applyDefaults(camera, playersRoot);
            player.children.add(camera);
        }
        ensurePostProcess(null, camera, playersRoot, document);
        return camera;
    }

    public static void ensureAll(SceneNode playersRoot) {
        if (playersRoot == null || playersRoot.type != SceneNodeType.PLAYERS) return;
        for (SceneNode child : playersRoot.children) {
            if (child.type == SceneNodeType.PLAYER) ensureCamera(child, playersRoot, null);
        }
    }

    public static SceneNode findCamera(SceneNode player) {
        if (player == null) return null;
        for (SceneNode child : player.children) {
            if (child.type == SceneNodeType.CAMERA) return child;
        }
        return null;
    }

    public static SceneNode findPostProcess(SceneNode camera) {
        if (camera == null) return null;
        for (SceneNode child : camera.children) {
            if (child.type == SceneNodeType.POST_PROCESS) return child;
        }
        return null;
    }

    /**
     * First POST_PROCESS under the CAMERA, created on demand (same idea as {@link #ensureCamera}).
     * Settings are stored on the PLAYERS node (PLAYER subtrees are not persisted).
     */
    public static SceneNode ensurePostProcess(SceneActiveState state, SceneNode camera) {
        SceneNode players = state != null && state.document != null
                ? state.document.ensurePlayersNode() : null;
        return ensurePostProcess(state, camera, players);
    }

    public static SceneNode ensurePostProcess(SceneActiveState state, SceneNode camera, SceneNode playersRoot) {
        return ensurePostProcess(state, camera, playersRoot, state != null ? state.document : null);
    }

    public static SceneNode ensurePostProcess(SceneActiveState state, SceneNode camera, SceneNode playersRoot,
                                              SceneDocument document) {
        if (camera == null || camera.type != SceneNodeType.CAMERA) return null;
        SceneNode post = findPostProcess(camera);
        if (post != null) {
            migratePostDefaults(post, playersRoot);
            if (state != null) state.registerPostProcess(post);
            return post;
        }
        post = SceneNode.postProcess("Post");
        SceneDocument doc = document != null ? document : (state != null ? state.document : null);
        String preferred = camera.id == null || camera.id.isBlank() ? "post" : camera.id + "_post";
        post.id = doc != null ? SceneNodeIds.unique(doc, preferred) : preferred;
        // Identity — do not stamp PLAYERS defaults onto every live camera.
        post.post().global = true;
        post.post().enabled = true;
        camera.children.add(post);
        if (state != null) {
            state.registerPostProcess(post);
            SceneRuntime.trackNodeKeys(state.sceneId, post);
        }
        return post;
    }

    /**
     * Persist this camera's post onto PLAYERS for save (PLAYER trees are stripped).
     * Does not copy onto other live players — post is per-camera.
     */
    public static void onPostAuthoringChanged(SceneDocument document, SceneNode post) {
        if (document == null || post == null || post.type != SceneNodeType.POST_PROCESS) return;
        if (!ScenePostProcessRuntime.isCameraBound(document, post)) return;
        copyPostToDefaults(post, document.ensurePlayersNode());
    }

    public static void copyPostToDefaults(SceneNode post, SceneNode playersRoot) {
        if (post == null || playersRoot == null) return;
        post.post().copyInto(playersRoot.post());
        playersRoot.post().global = true;
    }

    /** Editor C2S: write camera-bound post onto one live player (or PLAYERS defaults if uuid blank). */
    public static void applyPostAuthoring(PostProcessSettings settings) {
        applyPostAuthoring("", settings);
    }

    public static void applyPostAuthoring(String playerUuid, PostProcessSettings settings) {
        if (settings == null) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        PostProcessSettings value = settings.copy();
        value.global = true;
        boolean specific = playerUuid != null && !playerUuid.isBlank();
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            if (!specific) {
                value.copyInto(players.post());
                players.post().global = true;
                continue;
            }
            ServerLevel level = SceneRuntime.resolveLevelForSync(server, state.runtimeDimension);
            for (SceneNode child : players.children) {
                if (child.type != SceneNodeType.PLAYER || !playerUuid.equals(child.playerUuid)) continue;
                SceneNode camera = ensureCamera(child, players, state.document);
                SceneNode post = ensurePostProcess(state, camera, players);
                if (post == null) continue;
                value.copyInto(post.post());
                post.post().global = true;
                copyPostToDefaults(post, players);
                if (level != null) {
                    ScenePostProcessRuntime runtime = state.postProcessRuntime(post.id);
                    if (runtime != null) runtime.syncNow(state, level, post);
                }
            }
        }
    }

    /** One-way: live editor post → PLAYERS, so Play/save keep bloom after PLAYER nodes are stripped. */
    private static void migratePostDefaults(SceneNode post, SceneNode playersRoot) {
        if (post == null || playersRoot == null) return;
        if (!playersRoot.post().hasAnyOverride() && post.post().hasAnyOverride()) {
            copyPostToDefaults(post, playersRoot);
        }
    }

    /**
     * Prefab / paste: root CAMERA, or first CAMERA descendant under a wrapper.
     * Used when dropping a camera prefab onto a PLAYER so it becomes that player's camera.
     */
    public static SceneNode findCameraIn(SceneNode root) {
        if (root == null) return null;
        if (root.type == SceneNodeType.CAMERA) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findCameraIn(child);
            if (found != null) return found;
        }
        return null;
    }

    /** Detaches {@code node} from its parent inside {@code root}'s subtree (not from the document). */
    public static boolean detachInTree(SceneNode root, SceneNode node) {
        if (root == null || node == null || root == node) return false;
        for (int i = 0; i < root.children.size(); i++) {
            SceneNode child = root.children.get(i);
            if (child == node) {
                root.children.remove(i);
                return true;
            }
            if (detachInTree(child, node)) return true;
        }
        return false;
    }

    /**
     * Document-only: remove every CAMERA under the player and attach {@code camera}
     * as the sole player camera (stable id {@code camera_<uuid>} when possible).
     */
    public static SceneNode installCamera(SceneNode player, SceneNode camera) {
        if (player == null || player.type != SceneNodeType.PLAYER
                || camera == null || camera.type != SceneNodeType.CAMERA) {
            return null;
        }
        List<SceneNode> remove = new ArrayList<>();
        for (SceneNode child : player.children) {
            if (child.type == SceneNodeType.CAMERA && child != camera) remove.add(child);
        }
        player.children.removeAll(remove);
        if (!player.children.contains(camera)) {
            player.children.add(0, camera);
        }
        stabilizeCameraId(player, camera);
        return camera;
    }

    /**
     * Live scene: unregister old CAMERA subtrees, then {@link #installCamera}.
     * Caller still must {@code spawnSubtree} the new camera.
     */
    public static SceneNode installCamera(SceneActiveState state, SceneNode player, SceneNode camera) {
        if (state == null) return installCamera(player, camera);
        if (player == null || player.type != SceneNodeType.PLAYER
                || camera == null || camera.type != SceneNodeType.CAMERA) {
            return null;
        }
        List<SceneNode> remove = new ArrayList<>();
        for (SceneNode child : player.children) {
            if (child.type == SceneNodeType.CAMERA && child != camera) remove.add(child);
        }
        for (SceneNode old : remove) {
            state.removeNodeSubtree(old.id);
        }
        return installCamera(player, camera);
    }

    private static void stabilizeCameraId(SceneNode player, SceneNode camera) {
        if (camera == null || (camera.id != null && !camera.id.isBlank())) return;
        String name = player == null ? null : player.name;
        camera.id = SceneNodeIds.allocateLive(null, SceneNodeType.CAMERA, name);
    }

    public static void applyDefaults(SceneNode camera, SceneNode playersRoot) {
        if (camera == null || playersRoot == null) return;
        PlayerCameraPose.Rest rest = PlayerCameraPose.Rest.fromPlayers(playersRoot);
        applyRest(camera, rest);
    }

    public static void applyRest(SceneNode camera, PlayerCameraPose.Rest rest) {
        if (camera == null || rest == null) return;
        camera.transform.px = rest.x();
        camera.transform.py = rest.y();
        camera.transform.pz = rest.z();
        camera.transform.ry = rest.yaw();
        camera.transform.rx = rest.pitch();
        camera.cameraFov = rest.fov();
        camera.cameraDistance = rest.distance();
        applyRig(camera, rest.rig());
    }

    public static void applyRig(SceneNode node, PlayerCameraPose.Rig rig) {
        if (node == null || rig == null) return;
        node.cameraFollowPlayer = rig.followPlayer();
        node.cameraLockYaw = rig.lockYaw();
        node.cameraLockPitch = rig.lockPitch();
        node.cameraOrbit = rig.orbit();
        node.cameraOrbitYaw = rig.orbitYaw();
        node.cameraOrbitPitch = rig.orbitPitch();
        node.cameraPivotX = (float) rig.pivotX();
        node.cameraPivotY = (float) rig.pivotY();
        node.cameraPivotZ = (float) rig.pivotZ();
        node.cameraCollide = rig.collide();
        node.cameraShowHand = rig.showHand();
        node.cameraClearOcclusion = rig.clearOcclusion();
        node.cameraOcclusionAlpha = rig.occlusionAlpha();
        node.cameraOcclusionRadius = rig.occlusionRadius();
        node.cameraMoveRelative = rig.moveRelative();
        node.cameraSeparateTurn = rig.separateTurn();
        node.cameraScreenRaycast = rig.screenRaycast();
        node.cameraLookAtInteract = rig.lookAtInteract();
        node.cameraTurnSmooth = rig.turnSmooth();
        node.cameraDisableEffects = rig.disableEffects();
        node.cameraWalkBob = rig.walkBob();
        node.cameraWalkBobAmount = rig.walkBobAmount();
        node.cameraHideGui = rig.hideGui();
        node.cameraLockX = rig.lockX();
        node.cameraLockY = rig.lockY();
        node.cameraLockZ = rig.lockZ();
        node.cameraWorldX = rig.worldX();
        node.cameraWorldY = rig.worldY();
        node.cameraWorldZ = rig.worldZ();
    }

    public static void copyToDefaults(SceneNode camera, SceneNode playersRoot) {
        if (camera == null || playersRoot == null || playersRoot.type != SceneNodeType.PLAYERS) return;
        playersRoot.cameraRestX = (float) camera.transform.px;
        playersRoot.cameraRestY = (float) camera.transform.py;
        playersRoot.cameraRestZ = (float) camera.transform.pz;
        playersRoot.cameraRestYaw = (float) camera.transform.ry;
        playersRoot.cameraRestPitch = (float) camera.transform.rx;
        playersRoot.cameraFov = camera.cameraFov;
        playersRoot.cameraDistance = camera.cameraDistance;
        applyRig(playersRoot, PlayerCameraPose.Rig.from(camera));
    }

    public static void applyDefaultsToAll(SceneNode playersRoot) {
        if (playersRoot == null) return;
        PlayerCameraPose.Rest rest = PlayerCameraPose.Rest.fromPlayers(playersRoot);
        for (SceneNode child : playersRoot.children) {
            if (child.type != SceneNodeType.PLAYER) continue;
            SceneNode camera = ensureCamera(child, playersRoot);
            applyRest(camera, rest);
        }
    }

    /**
     * PLAYER cameras are not written to disk. Live per-player pose must not
     * overwrite PLAYERS defaults (that replayed one camera to everyone on Play).
     */
    public static void flushAuthoring(SceneDocument document) {
    }

    public static SceneNodeView viewFor(Player player, SceneActiveState preferred) {
        if (player == null) return null;
        String uuid = player.getUUID().toString();
        if (preferred != null) {
            SceneNodeView view = viewIn(preferred, uuid);
            if (view != null) return view;
        }
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            SceneNodeView view = viewIn(state, uuid);
            if (view != null) return view;
        }
        return null;
    }

    public static SceneNodeView viewFor(ServerPlayer player) {
        return viewFor(player, null);
    }

    private static SceneNodeView viewIn(SceneActiveState state, String uuid) {
        if (state == null || state.document == null || uuid == null) return null;
        SceneNode players = state.document.ensurePlayersNode();
        for (SceneNode child : players.children) {
            if (child.type != SceneNodeType.PLAYER || !uuid.equals(child.playerUuid)) continue;
            SceneNode camera = ensureCamera(child, players, state.document);
            return camera == null ? null : state.nodeView(camera);
        }
        return null;
    }
}
