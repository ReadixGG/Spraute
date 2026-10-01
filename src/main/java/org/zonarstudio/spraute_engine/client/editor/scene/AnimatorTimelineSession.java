package org.zonarstudio.spraute_engine.client.editor.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.scene.SceneNodeIds;
import org.zonarstudio.spraute_engine.scene.SceneNodeScriptProps;

/**
 * State for the Timeline panel: which animator node is open, which clip is
 * edited, playhead position and the currently selected bone. The world preview
 * pulls the edited clip from here so keyframe edits are visible immediately.
 */
public final class AnimatorTimelineSession {
    /** Playhead / keyframe snapping granularity. */
    public static final int SNAP_OFF = 0;
    public static final int SNAP_FRAME = 1;
    public static final int SNAP_HALF_FRAME = 2;

    private static final int HISTORY_LIMIT = 100;
    private static String animatorNodeId;
    private static EditorAnimationDocument document;
    private static String clipName = "";
    private static float time;
    private static boolean playing;
    private static long lastFrameNanos;
    private static String selectedBone;
    private static String lastError;
    private static float playbackSpeed = 1f;
    private static float fps = 20f;
    private static int snapMode = SNAP_FRAME;
    private static boolean timelineFocused;
    private static boolean restPoseEditing;
    private static boolean dirty;
    private static long dirtySinceMs;
    // Selected keyframe (for copy/paste and interpolation editing).
    private static String selKeyBone;
    private static int selKeyChannel = -1;
    private static float selKeyTime = -1f;
    private static final Deque<EditorAnimationDocument> undoStack = new ArrayDeque<>();
    private static final Deque<EditorAnimationDocument> redoStack = new ArrayDeque<>();
    private static final Pattern NODE_PROPERTY = Pattern.compile("_node\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Map<String, Object> propertyBaseline = new LinkedHashMap<>();

    private AnimatorTimelineSession() {}

    public static boolean isOpen() {
        return animatorNodeId != null && document != null;
    }

    public static String animatorNodeId() { return animatorNodeId; }
    public static EditorAnimationDocument document() { return document; }
    public static String clipName() { return clipName; }
    public static float time() { return time; }
    public static boolean isPlaying() { return playing; }
    public static String selectedBone() { return selectedBone; }
    public static String lastError() {
        return lastError == null ? null : org.zonarstudio.spraute_engine.client.editor.EditorI18n.localize(lastError);
    }
    public static void setError(String error) { lastError = error; }
    public static boolean isTimelineFocused() { return timelineFocused; }
    public static void setTimelineFocused(boolean focused) { timelineFocused = focused; }
    public static boolean isRestPoseEditing() { return restPoseEditing; }

    /**
     * The open timeline is editing a {@code .animation.json} (clips, bone keys,
     * property channels) — not the scene graph and not the model's bind pose.
     */
    public static boolean isAuthoring() {
        return isOpen() && !restPoseEditing;
    }
    public static void setRestPoseEditing(boolean editing) {
        restPoseEditing = editing;
        if (editing) playing = false;
    }

    public static void selectBone(String bone) {
        if (bone == null || bone.isBlank()) {
            selectedBone = null;
            return;
        }
        selectedBone = bone;
        if (isOpen() && clip() != null) {
            restPoseEditing = false;
            SceneNode target = targetNode();
            if (target != null) SceneEditorSession.select(target.id);
        }
    }

    /** Select a virtual bone node and open its owner's animator when available. */
    public static void selectBone(SceneNode owner, String bone) {
        if (bone == null || bone.isBlank()) {
            selectedBone = null;
            return;
        }
        if (owner != null && !targets(owner.id)) {
            for (SceneNode child : owner.children) {
                if (child.type == SceneNodeType.ANIMATOR
                        && child.animatorFile != null && !child.animatorFile.isBlank()) {
                    open(child);
                    break;
                }
            }
        }
        selectedBone = bone;
        if (owner != null && targets(owner.id)) {
            restPoseEditing = false;
            SceneEditorSession.select(owner.id);
        } else if (owner != null) {
            restPoseEditing = true;
            SceneEditorSession.select(owner.id);
        }
    }

    public static float playbackSpeed() { return playbackSpeed; }
    public static void setPlaybackSpeed(float speed) { playbackSpeed = Math.max(0.05f, speed); }
    public static float fps() { return fps; }
    public static void setFps(float value) { fps = Math.max(1f, Math.min(240f, value)); }
    public static int snapMode() { return snapMode; }
    public static void setSnapMode(int mode) { snapMode = Math.max(SNAP_OFF, Math.min(SNAP_HALF_FRAME, mode)); }

    /** Grid step in seconds the playhead and keyframes snap to, or 0 when snapping is off. */
    public static float snapStep() {
        return switch (snapMode) {
            case SNAP_FRAME -> 1f / fps;
            case SNAP_HALF_FRAME -> 0.5f / fps;
            default -> 0f;
        };
    }

    /** Playhead position expressed in frames of the configured fps. */
    public static float frame() { return time * fps; }

    /** Move the playhead by whole frames, clamped to the clip. */
    public static void stepFrames(int frames) {
        playing = false;
        setTime(snapTime(time + frames / fps));
    }

    /** Jump to the nearest keyframe of the whole clip in the given direction. */
    public static boolean jumpToKey(int direction) {
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null || direction == 0) return false;
        Float best = null;
        for (EditorAnimationDocument.BoneChannels channels : clip.bones.values()) {
            for (int c = 0; c < 3; c++) best = closer(best, channels.channel(c).navigableKeySet(), direction);
        }
        for (Map<String, EditorAnimationDocument.PropertyChannel> target : clip.properties.values()) {
            for (EditorAnimationDocument.PropertyChannel channel : target.values()) {
                best = closer(best, channel.keys.navigableKeySet(), direction);
            }
        }
        if (best == null) return false;
        playing = false;
        setTime(best);
        return true;
    }

    private static Float closer(Float best, java.util.NavigableSet<Float> times, int direction) {
        Float candidate = direction > 0
                ? times.higher(time + 1e-4f)
                : times.lower(time - 1e-4f);
        if (candidate == null) return best;
        if (best == null) return candidate;
        return direction > 0 ? Math.min(best, candidate) : Math.max(best, candidate);
    }

    public static String selKeyBone() { return selKeyBone; }
    public static int selKeyChannel() { return selKeyChannel; }
    public static float selKeyTime() { return selKeyTime; }

    public static void selectKey(String bone, int channel, float t) {
        selKeyBone = bone;
        selKeyChannel = channel;
        selKeyTime = t;
    }

    public static void clearKeySelection() {
        selKeyBone = null;
        selKeyChannel = -1;
        selKeyTime = -1f;
    }

    public static EditorAnimationDocument.Key selectedKey() {
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null || selKeyBone == null || selKeyChannel < 0) return null;
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(selKeyBone);
        if (channels == null) return null;
        return channels.channel(selKeyChannel).get(selKeyTime);
    }

    /** Store the complete animation document before a mutation. */
    public static void pushUndo() {
        if (document == null) return;
        undoStack.push(document.copy());
        while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
        redoStack.clear();
        markDirty();
        EditorUndo.mark(EditorUndo.Domain.ANIM);
    }

    public static boolean canUndo() { return document != null && !undoStack.isEmpty(); }
    public static boolean canRedo() { return document != null && !redoStack.isEmpty(); }

    public static void undo() {
        if (!canUndo()) return;
        redoStack.push(document.copy());
        document = undoStack.pop();
        normalizeAfterHistoryRestore();
    }

    public static void redo() {
        if (!canRedo()) return;
        undoStack.push(document.copy());
        document = redoStack.pop();
        normalizeAfterHistoryRestore();
    }

    private static void normalizeAfterHistoryRestore() {
        playing = false;
        if (!document.clips.containsKey(clipName)
                || org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(clipName)) {
            clipName = pickDefaultClip(document, null);
        }
        EditorAnimationDocument.Clip clip = clip();
        if (clip != null) time = Math.max(0f, Math.min(time, Math.max(0.01f, clip.lengthSec)));
        if (selectedKey() == null) clearKeySelection();
        markDirty();
        applyPropertyPreview();
    }

    /**
     * Put the bone onto the timeline with base keys at the playhead:
     * rotation 0, position 0, scale 1 (or the current sampled pose values).
     */
    public static void addBoneWithBaseKeys(String bone) {
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null || bone == null) return;
        pushUndo();
        EditorAnimationDocument.BoneChannels channels =
                clip.bones.computeIfAbsent(bone, ignored -> new EditorAnimationDocument.BoneChannels());
        float t = quantize(time);
        channels.rotation.put(t, new EditorAnimationDocument.Key(
                EditorAnimationDocument.sample(channels.rotation, t, 0f)));
        channels.position.put(t, new EditorAnimationDocument.Key(
                EditorAnimationDocument.sample(channels.position, t, 0f)));
        channels.scale.put(t, new EditorAnimationDocument.Key(
                EditorAnimationDocument.sample(channels.scale, t, 1f)));
        if (t > clip.lengthSec) clip.lengthSec = t;
        selectedBone = bone;
    }

    public static void setTime(float t) {
        EditorAnimationDocument.Clip clip = clip();
        float max = clip == null ? 60f : Math.max(0.01f, clip.lengthSec);
        time = Math.max(0f, Math.min(t, max));
        applyPropertyPreview();
    }

    public static void setPlaying(boolean value) {
        playing = value;
        lastFrameNanos = System.nanoTime();
    }

    public static EditorAnimationDocument.Clip clip() {
        return document == null ? null : document.clips.get(clipName);
    }

    /** Current file-space transform of a bone at the playhead: pos, rot, scale. */
    public static float[] sampleBoneTransform(String bone) {
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null || bone == null) {
            return new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1};
        }
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(bone);
        if (channels == null) {
            return new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1};
        }
        float[] pos = EditorAnimationDocument.sample(channels.position, time, 0f);
        float[] rot = EditorAnimationDocument.sample(channels.rotation, time, 0f);
        float[] scale = EditorAnimationDocument.sample(channels.scale, time, 1f);
        return new float[]{
                pos[0], pos[1], pos[2],
                rot[0], rot[1], rot[2],
                scale[0], scale[1], scale[2]
        };
    }

    public static void putBonePosition(String bone, float x, float y, float z) {
        putKey(bone, 1, new float[]{x, y, z});
    }

    public static void putBoneRotation(String bone, float x, float y, float z) {
        putKey(bone, 0, new float[]{x, y, z});
    }

    public static void putBoneScale(String bone, float x, float y, float z) {
        putKey(bone, 2, new float[]{x, y, z});
    }

    public static void selectClip(String name) {
        restorePropertyPreview();
        propertyBaseline.clear();
        clipName = name == null ? "" : name;
        time = 0f;
        playing = false;
        SceneNode animator = animatorNode();
        if (animator != null && !clipName.equals(animator.animatorClip)) {
            animator.animatorClip = clipName;
            SceneEditorSession.markDirty();
        }
    }

    /** Rename the active clip, keeping its position in the file order. */
    public static boolean renameClip(String newName) {
        if (document == null || newName == null) return false;
        String name = newName.trim();
        if (name.isEmpty() || name.equals(clipName) || document.clips.containsKey(name)) return false;
        pushUndo();
        Map<String, EditorAnimationDocument.Clip> reordered = new LinkedHashMap<>();
        for (Map.Entry<String, EditorAnimationDocument.Clip> entry : document.clips.entrySet()) {
            reordered.put(entry.getKey().equals(clipName) ? name : entry.getKey(), entry.getValue());
        }
        document.clips.clear();
        document.clips.putAll(reordered);
        clipName = name;
        SceneNode animator = animatorNode();
        if (animator != null) {
            animator.animatorClip = name;
            SceneEditorSession.markDirty();
        }
        return true;
    }

    /** Copy the active clip under a fresh name and select it. */
    public static boolean duplicateClip() {
        EditorAnimationDocument.Clip source = clip();
        if (document == null || source == null) return false;
        String base = clipName + "_copy";
        String name = base;
        for (int i = 2; document.clips.containsKey(name); i++) name = base + i;
        pushUndo();
        EditorAnimationDocument copy = document.copy();
        document.clips.put(name, copy.clips.get(clipName));
        selectClip(name);
        return true;
    }

    /** Open an animator node's file in the timeline. */
    public static void open(SceneNode animatorNode) {
        lastError = null;
        if (animatorNode == null || animatorNode.type != SceneNodeType.ANIMATOR) return;
        String workspacePath = animatorNode.animatorFile;
        if (workspacePath == null || workspacePath.isBlank()) {
            lastError = "У аниматора не выбран файл .animation.json";
            return;
        }
        String normalized = workspacePath.replace('\\', '/');
        if (document != null && animatorNode.id.equals(animatorNodeId)
                && normalized.equals(document.workspacePath)) {
            restPoseEditing = false;
            String preferred = animatorNode.animatorClip;
            if (preferred != null && document.clips.containsKey(preferred)
                    && !org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(preferred)
                    && !preferred.equals(clipName)) {
                selectClip(preferred);
            }
            return;
        }
        if (document != null) {
            if (dirty) save();
        }
        restorePropertyPreview();
        propertyBaseline.clear();
        Path file = workspaceRoot().resolve(normalized).normalize();
        try {
            document = EditorAnimationDocument.load(file, workspacePath);
        } catch (Exception e) {
            lastError = "Не удалось открыть " + workspacePath + ": " + e.getMessage();
            document = null;
            return;
        }
        animatorNodeId = animatorNode.id;
        restPoseEditing = false;
        dirty = false;
        undoStack.clear();
        redoStack.clear();
        playing = false;
        time = 0f;
        selectedBone = null;
        propertyBaseline.clear();
        String preferred = animatorNode.animatorClip;
        if (preferred != null && document.clips.containsKey(preferred)
                && !org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(preferred)) {
            clipName = preferred;
        } else {
            SceneNode owner = SceneEditorSession.document() == null
                    ? null : SceneEditorSession.document().findParent(animatorNode.id);
            clipName = pickDefaultClip(document, owner == null ? null : owner.idleAnimation);
        }
        if (!clipName.isBlank() && (animatorNode.animatorClip == null || animatorNode.animatorClip.isBlank()
                || org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(animatorNode.animatorClip))) {
            animatorNode.animatorClip = clipName;
        }
    }

    public static void close() {
        if (dirty) save();
        restorePropertyPreview();
        animatorNodeId = null;
        document = null;
        clipName = "";
        playing = false;
        selectedBone = null;
        clearKeySelection();
        timelineFocused = false;
        restPoseEditing = false;
        dirty = false;
        undoStack.clear();
        redoStack.clear();
        propertyBaseline.clear();
    }

    private static void markDirty() {
        dirty = true;
        dirtySinceMs = System.currentTimeMillis();
    }

    /** Debounced write of the open {@code .animation.json}; same cadence as scene autosave. */
    public static void tickAutosave() {
        if (!dirty || document == null) return;
        if (org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.isDragging()) return;
        long debounce = org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()
                ? 400L : 2500L;
        if (System.currentTimeMillis() - dirtySinceMs > debounce) save();
    }

    public static Path workspaceRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
    }

    public static SceneNode animatorNode() {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null || animatorNodeId == null) return null;
        return doc.findNode(animatorNodeId);
    }

    /** The node the animator animates: the parent of the ANIMATOR node. */
    public static SceneNode targetNode() {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null || animatorNodeId == null) return null;
        return doc.findParent(animatorNodeId);
    }

    /** Whether the world preview of this scene node should play the edited clip. */
    public static boolean appliesTo(String nodeId) {
        return !restPoseEditing && targets(nodeId);
    }

    /** Whether the currently open animator belongs to this scene node. */
    public static boolean targets(String nodeId) {
        if (!isOpen() || nodeId == null) return false;
        SceneNode target = targetNode();
        return target != null && nodeId.equals(target.id) && clip() != null;
    }

    /** Advance playhead; call once per rendered frame. */
    public static void tickPlayback() {
        if (!playing) return;
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (now - lastFrameNanos) / 1_000_000_000f * playbackSpeed;
        lastFrameNanos = now;
        EditorAnimationDocument.Clip clip = clip();
        float length = clip == null ? 0f : clip.lengthSec;
        time += dt;
        if (length > 0f && time > length) {
            if (clip.loop) {
                time %= length;
            } else {
                time = length;
                playing = false;
            }
        }
        applyPropertyPreview();
    }

    /**
     * Adds the inspector field represented by its script API hint to the active clip.
     * Creates an ANIMATOR child and a compatible .animation.json when the object has none.
     */
    public static boolean addInspectorProperty(SceneNode target, String label, String scriptHint) {
        if (target == null || scriptHint == null) return false;
        Matcher matcher = NODE_PROPERTY.matcher(scriptHint);
        java.util.LinkedHashSet<String> properties = new java.util.LinkedHashSet<>();
        while (matcher.find()) properties.add(matcher.group(1));
        if (properties.isEmpty()) {
            lastError = "У этого поля пока нет идентификатора свойства";
            return false;
        }
        boolean hasAnimator = false;
        for (SceneNode child : target.children) {
            if (child.type == SceneNodeType.ANIMATOR) {
                hasAnimator = true;
                break;
            }
        }
        if (!hasAnimator) SceneEditorSession.pushUndo();
        SceneNode animator = ensureAnimator(target);
        if (animator == null) return false;
        if (!animator.id.equals(animatorNodeId)) open(animator);
        if (document == null) return false;
        if (clipName.isBlank()) {
            clipName = "animation." + safeId(target.id) + ".default";
            document.clips.put(clipName, new EditorAnimationDocument.Clip());
            animator.animatorClip = clipName;
        }
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null) return false;
        pushUndo();
        Map<String, EditorAnimationDocument.PropertyChannel> targetChannels =
                clip.properties.computeIfAbsent(target.id, ignored -> new LinkedHashMap<>());
        float keyTime = quantize(time);
        int added = 0;
        for (String property : properties) {
            Object current = propertyValue(target, property);
            JsonElement json = toJson(current);
            if (json == null) continue;
            EditorAnimationDocument.PropertyChannel channel =
                    targetChannels.computeIfAbsent(property,
                            ignored -> new EditorAnimationDocument.PropertyChannel());
            channel.type = propertyType(current, property);
            int lerp = current instanceof Number || "color".equals(channel.type)
                    ? EditorAnimationDocument.Key.LERP_LINEAR
                    : EditorAnimationDocument.Key.LERP_STEP;
            channel.keys.put(keyTime, new EditorAnimationDocument.PropertyKey(json, lerp));
            propertyBaseline.putIfAbsent(target.id + "\0" + property, current);
            added++;
        }
        if (added == 0) {
            lastError = "Параметр «" + label + "» нельзя анимировать";
            return false;
        }
        if (keyTime > clip.lengthSec) clip.lengthSec = keyTime;
        lastError = null;
        SceneEditorSession.markDirty();
        return true;
    }

    public static boolean hasInspectorProperty(SceneNode target, String scriptHint) {
        if (target == null || scriptHint == null || clip() == null) return false;
        Matcher matcher = NODE_PROPERTY.matcher(scriptHint);
        if (!matcher.find()) return false;
        Map<String, EditorAnimationDocument.PropertyChannel> channels = clip().properties.get(target.id);
        return channels != null && channels.containsKey(matcher.group(1));
    }

    public static boolean putPropertyKeyFromScene(String targetId, String property) {
        EditorAnimationDocument.Clip current = clip();
        SceneDocument scene = SceneEditorSession.document();
        if (current == null || scene == null) return false;
        SceneNode node = scene.findNode(targetId);
        Map<String, EditorAnimationDocument.PropertyChannel> channels = current.properties.get(targetId);
        if (node == null || channels == null) return false;
        EditorAnimationDocument.PropertyChannel channel = channels.get(property);
        Object value = propertyValue(node, property);
        JsonElement json = toJson(value);
        if (channel == null || json == null) return false;
        pushUndo();
        float keyTime = quantize(time);
        channel.keys.put(keyTime, new EditorAnimationDocument.PropertyKey(json,
                value instanceof Number || "color".equals(channel.type)
                        ? EditorAnimationDocument.Key.LERP_LINEAR
                        : EditorAnimationDocument.Key.LERP_STEP));
        if (keyTime > current.lengthSec) current.lengthSec = keyTime;
        return true;
    }

    public static void removePropertyChannel(String targetId, String property) {
        EditorAnimationDocument.Clip current = clip();
        if (current == null) return;
        Map<String, EditorAnimationDocument.PropertyChannel> target = current.properties.get(targetId);
        if (target == null || !target.containsKey(property)) return;
        pushUndo();
        target.remove(property);
        if (target.isEmpty()) current.properties.remove(targetId);
    }

    public static void removePropertyKey(String targetId, String property, float keyTime) {
        EditorAnimationDocument.Clip current = clip();
        if (current == null) return;
        Map<String, EditorAnimationDocument.PropertyChannel> target = current.properties.get(targetId);
        EditorAnimationDocument.PropertyChannel channel = target == null ? null : target.get(property);
        if (channel == null || !channel.keys.containsKey(keyTime)) return;
        pushUndo();
        channel.keys.remove(keyTime);
    }

    private static SceneNode ensureAnimator(SceneNode target) {
        for (SceneNode child : target.children) {
            if (child.type == SceneNodeType.ANIMATOR) {
                if (child.animatorFile == null || child.animatorFile.isBlank()) {
                    child.animatorFile = defaultAnimationPath(target);
                    createAnimationFile(child.animatorFile);
                }
                return child;
            }
        }
        SceneNode animator = SceneNode.animator(EditorI18n.t("Аниматор"));
        animator.animatorFile = defaultAnimationPath(target);
        createAnimationFile(animator.animatorFile);
        SceneDocument scene = SceneEditorSession.document();
        if (scene != null) {
            try {
                SceneNodeIds.assignTree(scene, animator);
            } finally {
                SceneNodeIds.clearPending();
            }
        }
        target.children.add(animator);
        SceneEditorSession.markDirty();
        return animator;
    }

    private static String defaultAnimationPath(SceneNode target) {
        return "animations/" + safeId(target.id == null || target.id.isBlank() ? target.name : target.id)
                + ".animation.json";
    }

    public static String pickDefaultClip(EditorAnimationDocument doc, String preferred) {
        if (doc == null || doc.clips.isEmpty()) return "";
        if (preferred != null && !preferred.isBlank() && doc.clips.containsKey(preferred)
                && !org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(preferred)) {
            return preferred;
        }
        for (String key : doc.clips.keySet()) {
            if (key.equalsIgnoreCase("idle")) return key;
        }
        for (Map.Entry<String, EditorAnimationDocument.Clip> e : doc.clips.entrySet()) {
            if (org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(e.getKey())) continue;
            EditorAnimationDocument.Clip clip = e.getValue();
            if (clip != null && clip.bones != null && !clip.bones.isEmpty()) return e.getKey();
        }
        for (String key : doc.clips.keySet()) {
            if (!org.zonarstudio.spraute_engine.core.parser.SpAnimationParser.isMetaClipName(key)) return key;
        }
        return "";
    }

    private static String safeId(String raw) {
        String value = raw == null ? "object" : raw.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_");
        return value.isBlank() ? "object" : value;
    }

    private static void createAnimationFile(String workspacePath) {
        try {
            Path file = workspaceRoot().resolve(workspacePath).normalize();
            if (!java.nio.file.Files.exists(file)) {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file,
                        "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {}\n}",
                        java.nio.charset.StandardCharsets.UTF_8);
                EditorAssetIndex.refresh();
            }
        } catch (Exception e) {
            lastError = "Не удалось создать файл анимации: " + e.getMessage();
        }
    }

    private static Object propertyValue(SceneNode node, String property) {
        return switch (property.toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> node.transform.px;
            case "y" -> node.transform.py;
            case "z" -> node.transform.pz;
            case "rotationx", "rotx", "pitch" -> node.transform.rx;
            case "rotationy", "roty", "yaw" -> node.transform.ry;
            case "rotationz", "rotz", "roll" -> node.transform.rz;
            case "scalex" -> node.transform.sx;
            case "scaley" -> node.transform.sy;
            case "scalez" -> node.transform.sz;
            default -> {
                Object value = SceneNodeScriptProps.get(node, property.toLowerCase(java.util.Locale.ROOT));
                if (value == null && node.customData != null) {
                    for (Map.Entry<String, Object> entry : node.customData.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(property)) {
                            value = entry.getValue();
                            break;
                        }
                    }
                }
                yield value;
            }
        };
    }

    private static boolean setPropertyValue(SceneNode node, String property, Object value) {
        if (node == null || value == null) return false;
        double number = value instanceof Number n ? n.doubleValue() : 0d;
        switch (property.toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> node.transform.px = number;
            case "y" -> node.transform.py = number;
            case "z" -> node.transform.pz = number;
            case "rotationx", "rotx", "pitch" -> node.transform.rx = number;
            case "rotationy", "roty", "yaw" -> node.transform.ry = number;
            case "rotationz", "rotz", "roll" -> node.transform.rz = number;
            case "scalex" -> node.transform.sx = number;
            case "scaley" -> node.transform.sy = number;
            case "scalez" -> node.transform.sz = number;
            default -> {
                if (SceneNodeScriptProps.set(node,
                        property.toLowerCase(java.util.Locale.ROOT), value, null)) return true;
                if (node.type == SceneNodeType.CUSTOM) {
                    if (node.customData == null) node.customData = new LinkedHashMap<>();
                    String key = property;
                    for (String existing : node.customData.keySet()) {
                        if (existing.equalsIgnoreCase(property)) {
                            key = existing;
                            break;
                        }
                    }
                    node.customData.put(key, value);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    private static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof String s) return new JsonPrimitive(s);
        return null;
    }

    private static String propertyType(Object value, String property) {
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Number) return "number";
        if (property != null && property.toLowerCase(java.util.Locale.ROOT).contains("color")) return "color";
        return "string";
    }

    private static Object fromJson(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsDouble();
        return primitive.getAsString();
    }

    private static void applyPropertyPreview() {
        EditorAnimationDocument.Clip current = clip();
        SceneDocument scene = SceneEditorSession.document();
        if (current == null || scene == null) return;
        for (Map.Entry<String, Map<String, EditorAnimationDocument.PropertyChannel>> target
                : current.properties.entrySet()) {
            SceneNode node = scene.findNode(target.getKey());
            if (node == null) continue;
            for (Map.Entry<String, EditorAnimationDocument.PropertyChannel> property
                    : target.getValue().entrySet()) {
                String key = node.id + "\0" + property.getKey();
                propertyBaseline.putIfAbsent(key, propertyValue(node, property.getKey()));
                setPropertyValue(node, property.getKey(),
                        fromJson(EditorAnimationDocument.sampleProperty(property.getValue(), time)));
            }
        }
    }

    private static void restorePropertyPreview() {
        SceneDocument scene = SceneEditorSession.document();
        if (scene == null) return;
        for (Map.Entry<String, Object> entry : propertyBaseline.entrySet()) {
            int split = entry.getKey().indexOf('\0');
            if (split <= 0) continue;
            SceneNode node = scene.findNode(entry.getKey().substring(0, split));
            setPropertyValue(node, entry.getKey().substring(split + 1), entry.getValue());
        }
    }

    /** Apply the edited clip at the playhead to a preview model instance. */
    public static void applyPreview(SpModelInstance instance) {
        if (document == null) return;
        SpAnimationParser.AnimationClip runtime = document.toRuntimeClip(clipName);
        if (runtime == null) return;
        runtime.apply(instance, time, 1f, false, false);
    }

    /** Write a keyframe (file-space values) at the playhead for the given bone/channel. */
    public static void putKey(String bone, int channelIdx, float[] value) {
        EditorAnimationDocument.Clip clip = clip();
        if (clip == null || bone == null) return;
        EditorAnimationDocument.BoneChannels channels =
                clip.bones.computeIfAbsent(bone, ignored -> new EditorAnimationDocument.BoneChannels());
        float t = quantize(time);
        java.util.TreeMap<Float, EditorAnimationDocument.Key> channel = channels.channel(channelIdx);
        EditorAnimationDocument.Key existing = channel.get(t);
        int lerp = existing != null ? existing.lerp : EditorAnimationDocument.Key.LERP_LINEAR;
        channel.put(t, new EditorAnimationDocument.Key(value.clone(), lerp));
        selectKey(bone, channelIdx, t);
        if (t > clip.lengthSec) clip.lengthSec = t;
        markDirty();
    }

    /** Round a time to the snapping grid; keyframe times always pass through here. */
    public static float quantize(float t) {
        float step = snapStep();
        float snapped = step > 0f ? Math.round(t / step) * step : t;
        return Math.round(snapped * 1000f) / 1000f;
    }

    /** Snap an interactively picked time (scrub, key drag) to the grid. */
    public static float snapTime(float t) {
        return quantize(t);
    }

    public static void save() {
        if (document == null) return;
        try {
            document.save();
            dirty = false;
            lastError = null;
            // Runtime caches keep serving the stale file otherwise.
            org.zonarstudio.spraute_engine.entity.client.SpAnimationCache.invalidate(document.workspacePath);
            EditorAnimClipPreview.invalidate();
        } catch (Exception e) {
            lastError = "Ошибка сохранения: " + e.getMessage();
        }
    }
}
