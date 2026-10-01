package org.zonarstudio.spraute_engine.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRepository;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background decode of UI videos used by auto-start / active scenes. */
public final class VideoPrewarm {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Set<String> WANTED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Spraute-Video-Cache");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private VideoPrewarm() {}

    public static void enqueue(String source) {
        if (source == null) return;
        String normalized = source.replace('\\', '/').trim();
        if (normalized.isEmpty()) return;
        WANTED.add(normalized);
        if (!QUEUED.add(normalized.toLowerCase())) return;
        EXEC.execute(() -> {
            try {
                Path file = VideoFrameCache.resolveMp4(normalized);
                VideoFrameCache.ensure(file, null);
            } catch (IllegalArgumentException ignored) {
                QUEUED.remove(normalized.toLowerCase());
            } catch (Exception e) {
                QUEUED.remove(normalized.toLowerCase());
                LOGGER.warn("[Spraute Video] Prewarm failed for {}: {}", normalized, e.getMessage());
            }
        });
    }

    public static void enqueueAll(Collection<String> sources) {
        if (sources == null) return;
        for (String source : sources) enqueue(source);
    }

    public static void scanLocalAutoStartScenes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return;
        Set<String> found = new LinkedHashSet<>();
        for (Path file : SceneRepository.listPlayableScenes(mc.gameDirectory.toPath())) {
            try {
                SceneDocument doc = SceneRepository.load(file);
                if (doc == null || !doc.autoStart || doc.root == null) continue;
                collect(doc.root, found);
            } catch (Exception e) {
                LOGGER.debug("[Spraute Video] Skip scene {}: {}", file.getFileName(), e.getMessage());
            }
        }
        enqueueAll(found);
        if (!found.isEmpty()) {
            LOGGER.info("[Spraute Video] Prewarming {} auto-start video(s)", found.size());
        }
    }

    public static void collect(SceneNode node, Set<String> out) {
        if (node == null || out == null) return;
        if (node.type == SceneNodeType.UI_VIDEO && node.uiVideo != null && !node.uiVideo.isBlank()) {
            out.add(node.uiVideo.trim().replace('\\', '/'));
        }
        if (node.children == null) return;
        for (SceneNode child : node.children) collect(child, out);
    }

    public static void retryPending() {
        for (String source : List.copyOf(WANTED)) {
            QUEUED.remove(source.toLowerCase());
            enqueue(source);
        }
    }

    public static void reset() {
        QUEUED.clear();
        WANTED.clear();
    }
}
