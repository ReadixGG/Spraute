package org.zonarstudio.spraute_engine.client.post;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession;
import org.zonarstudio.spraute_engine.network.SyncPostProcessPacket;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.ScenePostProcessRuntime;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blends post-process stacks for the local player camera.
 * Runtime packets are already filtered server-side to the owning player;
 * editor preview reads camera-bound posts from the open document.
 */
public final class PostProcessClient {
    public static final class Volume {
        public final String sceneId;
        public final String nodeId;
        public final PostProcessSettings settings;
        public final SceneTransform world;

        public Volume(String sceneId, String nodeId, PostProcessSettings settings, SceneTransform world) {
            this.sceneId = sceneId == null ? "" : sceneId;
            this.nodeId = nodeId == null ? "" : nodeId;
            this.settings = settings == null ? new PostProcessSettings() : settings;
            this.world = world == null ? new SceneTransform() : world;
        }
    }

    private static final Map<String, Volume> RUNTIME = new ConcurrentHashMap<>();

    private PostProcessClient() {}

    public static void handle(SyncPostProcessPacket msg) {
        if (msg == null) return;
        if (msg.clearScene) {
            RUNTIME.entrySet().removeIf(e -> e.getValue().sceneId.equals(msg.sceneId));
            return;
        }
        if (msg.stop) {
            RUNTIME.remove(key(msg.sceneId, msg.nodeId));
            return;
        }
        PostProcessSettings settings = msg.settings.copy();
        settings.global = true;
        RUNTIME.put(key(msg.sceneId, msg.nodeId),
                new Volume(msg.sceneId, msg.nodeId, settings, new SceneTransform()));
    }

    public static void clear() {
        RUNTIME.clear();
        PostProcessRenderer.dispose();
    }

    public static PostProcessSettings blended(double cx, double cy, double cz) {
        List<Volume> volumes = collect();
        PostProcessSettings out = PostProcessSettings.identity();
        if (volumes.isEmpty()) return out;
        volumes.sort(Comparator.comparingInt(v -> v.settings.priority));
        for (Volume v : volumes) {
            float w = v.settings.influence(cx, cy, cz, v.world);
            if (w > 0.001f) PostProcessSettings.blendInto(out, v.settings, w);
        }
        return out;
    }

    private static List<Volume> collect() {
        SceneDocument doc = SceneEditorSession.document();
        boolean live = SceneEditorSession.isLiveRuntime();
        boolean editorOpen = Minecraft.getInstance().screen
                instanceof org.zonarstudio.spraute_engine.client.editor.SprauteEngineEditorScreen;
        if (doc != null && !live && editorOpen) {
            return fromDocument(doc, true);
        }
        if (!RUNTIME.isEmpty()) return new ArrayList<>(RUNTIME.values());
        if (doc != null && (live || editorOpen)) return fromDocument(doc, !live);
        return List.of();
    }

    private static List<Volume> fromDocument(SceneDocument doc, boolean editorPreview) {
        List<Volume> out = new ArrayList<>();
        doc.walkActive((node, parentWorld) -> {
            if (node.type != SceneNodeType.POST_PROCESS) return;
            if (!ScenePostProcessRuntime.isCameraBound(doc, node)) return;
            if (editorPreview && !node.previewEnabled) return;
            if (!appliesToLocalView(doc, node, editorPreview)) return;
            PostProcessSettings s = node.post().copy();
            if (!s.enabled || !s.hasAnyOverride()) return;
            s.global = true;
            out.add(new Volume(doc.id, node.id, s, new SceneTransform()));
        });
        return out;
    }

    /**
     * Editor authoring: preview the selected camera, otherwise the local player's CAMERA.
     * Live / local play: only the local player's CAMERA stack.
     */
    private static boolean appliesToLocalView(SceneDocument doc, SceneNode post, boolean editorPreview) {
        SceneNode camera = ScenePostProcessRuntime.cameraParent(doc, post);
        if (camera == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (editorPreview) {
            if (SceneEditorSession.isSelected(post.id) || SceneEditorSession.isSelected(camera.id)) {
                return true;
            }
            Entity owner = PlayerCameraPose.findPlayer(doc, camera);
            if (mc.player == null) return false;
            return owner != null && owner.getUUID().equals(mc.player.getUUID());
        }
        Entity owner = PlayerCameraPose.findPlayer(doc, camera);
        if (mc.player == null || owner == null) return false;
        return owner.getUUID().equals(mc.player.getUUID());
    }

    public static Vec3 cameraPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return Vec3.ZERO;
        return mc.gameRenderer.getMainCamera().getPosition();
    }

    private static String key(String sceneId, String nodeId) {
        return (sceneId == null ? "" : sceneId) + '\0' + (nodeId == null ? "" : nodeId);
    }
}

