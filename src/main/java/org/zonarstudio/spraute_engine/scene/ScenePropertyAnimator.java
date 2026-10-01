package org.zonarstudio.spraute_engine.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Applies {@code spraute_properties} tracks from Bedrock .animation.json files to scene nodes. */
final class ScenePropertyAnimator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Path, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(long modified, SpAnimationParser.AnimationSet set) {}

    private ScenePropertyAnimator() {}

    static void tick(SceneActiveState state, ServerLevel level) {
        if (state == null || level == null || state.document == null || state.document.root == null) return;
        state.animationTicks++;
        float elapsed = state.animationTicks / 20f;
        CommandSourceStack source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        state.document.walk((node, ignoredWorldTransform) -> {
            if (node.type != SceneNodeType.ANIMATOR || !node.active || node.animatorFile == null
                    || node.animatorFile.isBlank()) return;
            SpAnimationParser.AnimationSet set = load(level.getServer(), node.animatorFile);
            if (set == null || set.size() == 0) return;
            String clipName = node.animatorClip;
            if (clipName == null || clipName.isBlank() || SpAnimationParser.isMetaClipName(clipName)
                    || set.get(clipName) == null) {
                clipName = set.firstPlayableName(clipName);
            }
            SpAnimationParser.AnimationClip clip = set.get(clipName);
            if (clip == null || clip.properties().isEmpty()) return;
            float t = resolveTime(clip, elapsed);
            for (Map.Entry<String, Map<String, SpAnimationParser.PropertyTrack>> target
                    : clip.properties().entrySet()) {
                SceneNode parent = state.document.findParent(node.id);
                String targetId = "$parent".equals(target.getKey()) && parent != null
                        ? parent.id : target.getKey();
                SceneNodeView view = state.nodeView(targetId);
                if (view == null) continue;
                for (Map.Entry<String, SpAnimationParser.PropertyTrack> property
                        : target.getValue().entrySet()) {
                    JsonElement sampled = property.getValue().sample(t);
                    Object value = javaValue(sampled);
                    if (value != null) {
                        String appliedKey = node.id + "\0" + targetId + "\0" + property.getKey();
                        Object previous = state.animatedValues.put(appliedKey, value);
                        if (!value.equals(previous)) view.setProperty(property.getKey(), value, source);
                    }
                }
            }
        });
    }

    private static float resolveTime(SpAnimationParser.AnimationClip clip, float elapsed) {
        float length = clip.getLengthSec();
        if (length <= 0f) return Math.max(0f, elapsed);
        if (clip.isLoop()) {
            float t = elapsed % length;
            return t < 0f ? t + length : t;
        }
        return Math.min(Math.max(0f, elapsed), length);
    }

    private static Object javaValue(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsDouble();
        return primitive.getAsString();
    }

    private static SpAnimationParser.AnimationSet load(MinecraftServer server, String workspacePath) {
        try {
            Path root = server.getServerDirectory().toPath().resolve("spraute_engine").normalize();
            Path file = root.resolve(workspacePath.replace('\\', '/')).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            long modified = Files.getLastModifiedTime(file).toMillis();
            Cached cached = CACHE.get(file);
            if (cached != null && cached.modified == modified) return cached.set;
            try (InputStream in = Files.newInputStream(file)) {
                SpAnimationParser.AnimationSet set = SpAnimationParser.parse(in);
                CACHE.put(file, new Cached(modified, set));
                return set;
            }
        } catch (Exception e) {
            LOGGER.debug("[Spraute Animator] Cannot load {}: {}", workspacePath, e.getMessage());
            return null;
        }
    }
}
