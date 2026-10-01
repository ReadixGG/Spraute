package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.SprauteScriptScreen;
import org.zonarstudio.spraute_engine.client.WorldCanvasTextureManager;
import org.zonarstudio.spraute_engine.scene.SceneCanvasBuilder;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneTransform;
import org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime;

import java.util.HashMap;
import java.util.Map;

/**
 * Editor ghost of a world-space CANVAS (scene not running): the same texture + plane the runtime
 * entity draws, built straight from the edited document — so widget edits show up on the panel in
 * the world as you make them.
 */
final class WorldCanvasEditorPreview {
    /** Rebuild the JSON from the tree a few times a second, not every frame. */
    private static final long REBUILD_EVERY_NS = 100_000_000L;
    /** Drop the cached JSON of a canvas that stopped being previewed. */
    private static final long FORGET_AFTER_NS = 5_000_000_000L;

    private record CachedJson(String json, long builtNs) {}

    private static final Map<String, CachedJson> JSON = new HashMap<>();
    private static long lastSweepNs;

    private WorldCanvasEditorPreview() {}

    /** Texture cache key of a node's editor ghost (shared with the in-world widget overlay). */
    static String previewKey(SceneNode node) {
        return WorldCanvasTextureManager.KEY_PREFIX + "n" + node.id;
    }

    /**
     * The panel's pixel grid — the coordinate space its widgets are laid out in. Read back from
     * the preview the texture was actually built with, so the in-world overlay lands exactly on
     * the drawn widgets; the node's configured size is the fallback before the first build.
     */
    static int[] panelPixels(SceneNode node) {
        float[] bounds = SprauteScriptScreen.editorPreviewBounds(previewKey(node), null);
        if (bounds != null && bounds[2] >= 1f && bounds[3] >= 1f) {
            return new int[]{Math.round(bounds[2]), Math.round(bounds[3])};
        }
        return new int[]{Math.max(1, node.uiCanvasW), Math.max(1, node.uiCanvasH)};
    }

    /** Panel size in blocks: {@code [width, height]}, height derived from the pixel aspect. */
    static float[] panelSize(SceneNode node, SceneTransform world) {
        float width = (float) (Math.max(0.01f, node.canvasWorldWidth) * Math.abs(world.sx));
        float height = node.canvasWorldHeight;
        if (height > 0f) {
            height *= (float) Math.abs(world.sy);
        } else {
            int[] pixels = panelPixels(node);
            height = width * pixels[1] / (float) pixels[0];
        }
        return new float[]{width, height};
    }

    static void render(SceneDocument doc, SceneNode node, SceneTransform world, PoseStack pose, Vec3 cam) {
        long now = System.nanoTime();
        sweep(now);
        String key = previewKey(node);
        CachedJson cached = JSON.get(node.id);
        String json;
        if (cached != null && now - cached.builtNs() < REBUILD_EVERY_NS) {
            json = cached.json();
        } else {
            json = SceneCanvasBuilder.buildJson(node, doc.root);
            JSON.put(node.id, new CachedJson(json, now));
        }
        if (json.isEmpty()) return;

        double dx = world.px - cam.x, dy = world.py - cam.y, dz = world.pz - cam.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double max = node.canvasWorldMaxDistance;
        if (distSq > max * max) return;

        WorldCanvasTextureManager.Handle handle = WorldCanvasTextureManager.request(
                key, json, node.canvasWorldResolution, distSq);
        if (handle == null) return;

        float[] size = panelSize(node, world);
        int mode = SceneWorldCanvasRuntime.modeOf(node.canvasWorldOrientation);

        pose.pushPose();
        pose.translate(dx, dy, dz);
        WorldCanvasTextureManager.drawPanel(pose, handle, size[0], node.canvasWorldHeight > 0f ? size[1] : 0f, mode,
                (float) world.ry, (float) world.rx, (float) world.rz,
                Minecraft.getInstance().gameRenderer.getMainCamera(), node.id.hashCode());
        pose.popPose();
    }

    /** Forget canvases nobody previewed for a while (deleted nodes, closed scenes). */
    private static void sweep(long now) {
        if (now - lastSweepNs < FORGET_AFTER_NS) return;
        lastSweepNs = now;
        JSON.entrySet().removeIf(entry -> now - entry.getValue().builtNs() > FORGET_AFTER_NS);
    }
}
