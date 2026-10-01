package org.zonarstudio.spraute_engine.client.editor.scene;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.parser.SpAnimationParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Mutable, editable representation of a Bedrock {@code .animation.json} file
 * for the Timeline panel. Values are stored exactly as they appear in the file
 * (Bedrock sign convention); {@link SpAnimationParser} handles axis conversion
 * on playback.
 */
public final class EditorAnimationDocument {
    public Path file;
    /** Workspace-relative path, e.g. {@code animations/npc.animation.json}. */
    public String workspacePath = "";
    public final Map<String, Clip> clips = new LinkedHashMap<>();

    /** Deep copy used by the animation editor's independent undo/redo history. */
    public EditorAnimationDocument copy() {
        EditorAnimationDocument copy = new EditorAnimationDocument();
        copy.file = file;
        copy.workspacePath = workspacePath;
        for (Map.Entry<String, Clip> entry : clips.entrySet()) {
            copy.clips.put(entry.getKey(), copyClip(entry.getValue()));
        }
        return copy;
    }

    private static Clip copyClip(Clip source) {
        Clip copy = new Clip();
        copy.lengthSec = source.lengthSec;
        copy.loop = source.loop;
        for (Map.Entry<String, BoneChannels> entry : source.bones.entrySet()) {
            BoneChannels channels = new BoneChannels();
            copyChannel(entry.getValue().rotation, channels.rotation);
            copyChannel(entry.getValue().position, channels.position);
            copyChannel(entry.getValue().scale, channels.scale);
            copy.bones.put(entry.getKey(), channels);
        }
        for (Map.Entry<String, Map<String, PropertyChannel>> target : source.properties.entrySet()) {
            Map<String, PropertyChannel> copiedTarget = new LinkedHashMap<>();
            for (Map.Entry<String, PropertyChannel> property : target.getValue().entrySet()) {
                copiedTarget.put(property.getKey(), property.getValue().copy());
            }
            copy.properties.put(target.getKey(), copiedTarget);
        }
        return copy;
    }

    private static void copyChannel(TreeMap<Float, Key> source, TreeMap<Float, Key> target) {
        for (Map.Entry<Float, Key> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue().copy());
        }
    }

    public static final class Clip {
        public float lengthSec = 1f;
        public boolean loop = true;
        /** boneName -> channels */
        public final Map<String, BoneChannels> bones = new LinkedHashMap<>();
        /** scene node id -> inspector property id -> keyframes. Stored beside Bedrock bones. */
        public final Map<String, Map<String, PropertyChannel>> properties = new LinkedHashMap<>();
    }

    public static final class PropertyKey {
        public final JsonElement value;
        public int lerp = Key.LERP_LINEAR;

        public PropertyKey(JsonElement value) {
            this.value = value == null ? com.google.gson.JsonNull.INSTANCE : value.deepCopy();
        }

        public PropertyKey(JsonElement value, int lerp) {
            this(value);
            this.lerp = lerp;
        }

        public PropertyKey copy() {
            return new PropertyKey(value, lerp);
        }
    }

    public static final class PropertyChannel {
        /** number, boolean, string, color, vec2, vec3. Non-numeric kinds use step interpolation. */
        public String type = "number";
        public final TreeMap<Float, PropertyKey> keys = new TreeMap<>();

        PropertyChannel copy() {
            PropertyChannel copy = new PropertyChannel();
            copy.type = type;
            for (Map.Entry<Float, PropertyKey> entry : keys.entrySet()) {
                copy.keys.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }
    }

    /** A single keyframe: value + interpolation mode toward the next key. */
    public static final class Key {
        public static final int LERP_LINEAR = SpAnimationParser.Keyframe.LERP_LINEAR;
        public static final int LERP_CATMULLROM = SpAnimationParser.Keyframe.LERP_CATMULLROM;
        public static final int LERP_STEP = SpAnimationParser.Keyframe.LERP_STEP;

        public final float[] v;
        public int lerp = LERP_LINEAR;

        public Key(float[] v) {
            this.v = v;
        }

        public Key(float[] v, int lerp) {
            this.v = v;
            this.lerp = lerp;
        }

        public Key copy() {
            return new Key(v.clone(), lerp);
        }
    }

    public static final class BoneChannels {
        /** time(sec) -> keyframe in file space */
        public final TreeMap<Float, Key> rotation = new TreeMap<>();
        public final TreeMap<Float, Key> position = new TreeMap<>();
        public final TreeMap<Float, Key> scale = new TreeMap<>();

        public boolean isEmpty() {
            return rotation.isEmpty() && position.isEmpty() && scale.isEmpty();
        }

        public TreeMap<Float, Key> channel(int idx) {
            return switch (idx) {
                case 1 -> position;
                case 2 -> scale;
                default -> rotation;
            };
        }
    }

    public static EditorAnimationDocument load(Path file, String workspacePath) throws IOException {
        EditorAnimationDocument doc = new EditorAnimationDocument();
        doc.file = file;
        doc.workspacePath = workspacePath == null ? "" : workspacePath;
        if (file != null && Files.isRegularFile(file)) {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject animations = root.getAsJsonObject("animations");
            if (animations != null) {
                for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    doc.clips.put(entry.getKey(), parseClip(entry.getValue().getAsJsonObject()));
                }
            }
        }
        return doc;
    }

    private static Clip parseClip(JsonObject obj) {
        Clip clip = new Clip();
        clip.lengthSec = obj.has("animation_length") ? safeFloat(obj.get("animation_length")) : 0f;
        clip.loop = parseLoop(obj.get("loop"));
        JsonObject bones = obj.getAsJsonObject("bones");
        if (bones != null) {
            for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                if (!boneEntry.getValue().isJsonObject()) continue;
                JsonObject boneObj = boneEntry.getValue().getAsJsonObject();
                BoneChannels channels = new BoneChannels();
                parseChannel(boneObj.get("rotation"), channels.rotation);
                parseChannel(boneObj.get("position"), channels.position);
                parseChannel(boneObj.get("scale"), channels.scale);
                if (!channels.isEmpty()) clip.bones.put(boneEntry.getKey(), channels);
            }
        }
        JsonObject propertyTargets = obj.getAsJsonObject("spraute_properties");
        if (propertyTargets != null) {
            for (Map.Entry<String, JsonElement> targetEntry : propertyTargets.entrySet()) {
                if (!targetEntry.getValue().isJsonObject()) continue;
                Map<String, PropertyChannel> target = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> propertyEntry
                        : targetEntry.getValue().getAsJsonObject().entrySet()) {
                    if (!propertyEntry.getValue().isJsonObject()) continue;
                    JsonObject propertyObj = propertyEntry.getValue().getAsJsonObject();
                    PropertyChannel channel = new PropertyChannel();
                    if (propertyObj.has("type")) channel.type = propertyObj.get("type").getAsString();
                    JsonObject keys = propertyObj.getAsJsonObject("keys");
                    if (keys != null) {
                        for (Map.Entry<String, JsonElement> keyEntry : keys.entrySet()) {
                            try {
                                float time = Float.parseFloat(keyEntry.getKey());
                                JsonElement value = keyEntry.getValue();
                                int lerp = Key.LERP_LINEAR;
                                if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
                                    JsonObject keyObj = value.getAsJsonObject();
                                    value = keyObj.get("value");
                                    lerp = parseLerp(keyObj);
                                }
                                channel.keys.put(time, new PropertyKey(value, lerp));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    if (!channel.keys.isEmpty()) target.put(propertyEntry.getKey(), channel);
                }
                if (!target.isEmpty()) clip.properties.put(targetEntry.getKey(), target);
            }
        }
        // Bedrock permits animation_length to be omitted. Runtime playback then
        // keeps advancing through all authored keys; the editor must not clamp
        // such clips to 0.01 s.
        float authoredLength = 0f;
        for (BoneChannels channels : clip.bones.values()) {
            if (!channels.rotation.isEmpty()) authoredLength = Math.max(authoredLength, channels.rotation.lastKey());
            if (!channels.position.isEmpty()) authoredLength = Math.max(authoredLength, channels.position.lastKey());
            if (!channels.scale.isEmpty()) authoredLength = Math.max(authoredLength, channels.scale.lastKey());
        }
        for (Map<String, PropertyChannel> target : clip.properties.values()) {
            for (PropertyChannel channel : target.values()) {
                if (!channel.keys.isEmpty()) authoredLength = Math.max(authoredLength, channel.keys.lastKey());
            }
        }
        clip.lengthSec = Math.max(clip.lengthSec, authoredLength);
        if (clip.lengthSec <= 0f) clip.lengthSec = 1f;
        return clip;
    }

    private static void parseChannel(JsonElement el, TreeMap<Float, Key> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonArray()) {
            float[] vec = parseVec(el);
            if (vec != null) out.put(0f, new Key(vec));
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("vector") || obj.has("post") || obj.has("pre")) {
            float[] vec = parseVec(el);
            if (vec != null) out.put(0f, new Key(vec, parseLerp(el)));
            return;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            Float time;
            try {
                time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            float[] vec = parseVec(entry.getValue());
            if (vec != null) out.put(time, new Key(vec, parseLerp(entry.getValue())));
        }
    }

    private static int parseLerp(JsonElement el) {
        if (el == null || !el.isJsonObject()) return Key.LERP_LINEAR;
        JsonElement mode = el.getAsJsonObject().get("lerp_mode");
        if (mode == null || mode.isJsonNull()) return Key.LERP_LINEAR;
        try {
            String s = mode.getAsString().toLowerCase(java.util.Locale.ROOT);
            if (s.contains("catmull") || s.contains("smooth")) return Key.LERP_CATMULLROM;
            if (s.contains("step")) return Key.LERP_STEP;
        } catch (Exception ignored) {
        }
        return Key.LERP_LINEAR;
    }

    private static float[] parseVec(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() < 3) return null;
            return new float[]{safeFloat(arr.get(0)), safeFloat(arr.get(1)), safeFloat(arr.get(2))};
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("vector")) return parseVec(obj.get("vector"));
            if (obj.has("post")) return parseVec(obj.get("post"));
            if (obj.has("pre")) return parseVec(obj.get("pre"));
        }
        return null;
    }

    private static float safeFloat(JsonElement el) {
        try {
            return el.getAsFloat();
        } catch (Exception e) {
            return 0f;
        }
    }

    private static boolean parseLoop(JsonElement el) {
        if (el == null || el.isJsonNull()) return false;
        try {
            if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            String s = el.getAsString();
            return "true".equalsIgnoreCase(s) || "loop".equalsIgnoreCase(s)
                    || "hold_on_last_frame".equalsIgnoreCase(s);
        } catch (Exception e) {
            return false;
        }
    }

    public void save() throws IOException {
        if (file == null) throw new IOException(EditorI18n.t("Файл анимации не задан"));
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        JsonObject animations = new JsonObject();
        for (Map.Entry<String, Clip> entry : clips.entrySet()) {
            animations.add(entry.getKey(), clipToJson(entry.getValue()));
        }
        root.add("animations", animations);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root),
                StandardCharsets.UTF_8);
    }

    private static JsonObject clipToJson(Clip clip) {
        JsonObject obj = new JsonObject();
        obj.addProperty("loop", clip.loop);
        obj.addProperty("animation_length", clip.lengthSec);
        JsonObject bones = new JsonObject();
        for (Map.Entry<String, BoneChannels> boneEntry : clip.bones.entrySet()) {
            BoneChannels channels = boneEntry.getValue();
            if (channels.isEmpty()) continue;
            JsonObject boneObj = new JsonObject();
            if (!channels.rotation.isEmpty()) boneObj.add("rotation", channelToJson(channels.rotation));
            if (!channels.position.isEmpty()) boneObj.add("position", channelToJson(channels.position));
            if (!channels.scale.isEmpty()) boneObj.add("scale", channelToJson(channels.scale));
            bones.add(boneEntry.getKey(), boneObj);
        }
        obj.add("bones", bones);
        JsonObject propertyTargets = new JsonObject();
        for (Map.Entry<String, Map<String, PropertyChannel>> targetEntry : clip.properties.entrySet()) {
            JsonObject target = new JsonObject();
            for (Map.Entry<String, PropertyChannel> propertyEntry : targetEntry.getValue().entrySet()) {
                PropertyChannel channel = propertyEntry.getValue();
                if (channel.keys.isEmpty()) continue;
                JsonObject property = new JsonObject();
                property.addProperty("type", channel.type);
                JsonObject keys = new JsonObject();
                for (Map.Entry<Float, PropertyKey> keyEntry : channel.keys.entrySet()) {
                    PropertyKey key = keyEntry.getValue();
                    if (key.lerp == Key.LERP_LINEAR) {
                        keys.add(trimTime(keyEntry.getKey()), key.value.deepCopy());
                    } else {
                        JsonObject keyObj = new JsonObject();
                        keyObj.add("value", key.value.deepCopy());
                        keyObj.addProperty("lerp_mode",
                                key.lerp == Key.LERP_CATMULLROM ? "catmullrom" : "step");
                        keys.add(trimTime(keyEntry.getKey()), keyObj);
                    }
                }
                property.add("keys", keys);
                target.add(propertyEntry.getKey(), property);
            }
            if (target.size() > 0) propertyTargets.add(targetEntry.getKey(), target);
        }
        if (propertyTargets.size() > 0) obj.add("spraute_properties", propertyTargets);
        return obj;
    }

    private static JsonElement channelToJson(TreeMap<Float, Key> channel) {
        if (channel.size() == 1 && channel.firstKey() == 0f
                && channel.firstEntry().getValue().lerp == Key.LERP_LINEAR) {
            return vecToJson(channel.firstEntry().getValue().v);
        }
        JsonObject obj = new JsonObject();
        for (Map.Entry<Float, Key> entry : channel.entrySet()) {
            obj.add(trimTime(entry.getKey()), keyToJson(entry.getValue()));
        }
        return obj;
    }

    private static JsonElement keyToJson(Key key) {
        if (key.lerp == Key.LERP_LINEAR) return vecToJson(key.v);
        JsonObject obj = new JsonObject();
        obj.add("post", vecToJson(key.v));
        obj.addProperty("lerp_mode", key.lerp == Key.LERP_CATMULLROM ? "catmullrom" : "step");
        return obj;
    }

    private static String trimTime(float time) {
        // Bedrock time keys look like "0.5" / "1.0"; keep them short and stable.
        String s = String.format(java.util.Locale.ROOT, "%.4f", time);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s += "0";
        return s;
    }

    private static JsonArray vecToJson(float[] vec) {
        JsonArray arr = new JsonArray();
        arr.add(vec[0]);
        arr.add(vec[1]);
        arr.add(vec[2]);
        return arr;
    }

    /** Build a playable clip for the world preview from the current edited state. */
    public SpAnimationParser.AnimationClip toRuntimeClip(String clipName) {
        Clip clip = clips.get(clipName);
        if (clip == null) return null;
        Map<String, SpAnimationParser.BoneTrack> tracks = new LinkedHashMap<>();
        for (Map.Entry<String, BoneChannels> entry : clip.bones.entrySet()) {
            BoneChannels ch = entry.getValue();
            tracks.put(entry.getKey(), new SpAnimationParser.BoneTrack(
                    toTrack(ch.rotation), toTrack(ch.position), toTrack(ch.scale)));
        }
        Map<String, Map<String, SpAnimationParser.PropertyTrack>> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, PropertyChannel>> targetEntry : clip.properties.entrySet()) {
            Map<String, SpAnimationParser.PropertyTrack> target = new LinkedHashMap<>();
            for (Map.Entry<String, PropertyChannel> propertyEntry : targetEntry.getValue().entrySet()) {
                List<SpAnimationParser.PropertyKeyframe> keys = new ArrayList<>();
                for (Map.Entry<Float, PropertyKey> keyEntry : propertyEntry.getValue().keys.entrySet()) {
                    PropertyKey key = keyEntry.getValue();
                    keys.add(new SpAnimationParser.PropertyKeyframe(
                            keyEntry.getKey(), key.value.deepCopy(), key.lerp));
                }
                target.put(propertyEntry.getKey(),
                        new SpAnimationParser.PropertyTrack(propertyEntry.getValue().type, keys));
            }
            properties.put(targetEntry.getKey(), target);
        }
        return new SpAnimationParser.AnimationClip(clip.lengthSec, clip.loop, tracks, properties);
    }

    private static SpAnimationParser.KeyframeTrack toTrack(TreeMap<Float, Key> channel) {
        List<SpAnimationParser.Keyframe> keyframes = new ArrayList<>();
        for (Map.Entry<Float, Key> entry : channel.entrySet()) {
            float[] v = entry.getValue().v;
            keyframes.add(new SpAnimationParser.Keyframe(entry.getKey(),
                    new SpVec3(v[0], v[1], v[2]), entry.getValue().lerp));
        }
        return new SpAnimationParser.KeyframeTrack(keyframes);
    }

    /** Sampled channel value at time (linear, file-space); fallback when channel empty. */
    public static float[] sample(TreeMap<Float, Key> channel, float t, float fallback) {
        if (channel.isEmpty()) return new float[]{fallback, fallback, fallback};
        Map.Entry<Float, Key> floor = channel.floorEntry(t);
        Map.Entry<Float, Key> ceil = channel.ceilingEntry(t);
        if (floor == null) return ceil.getValue().v.clone();
        if (ceil == null || floor.getKey().equals(ceil.getKey())) return floor.getValue().v.clone();
        if (floor.getValue().lerp == Key.LERP_STEP) return floor.getValue().v.clone();
        float alpha = (t - floor.getKey()) / (ceil.getKey() - floor.getKey());
        float[] a = floor.getValue().v;
        float[] b = ceil.getValue().v;
        return new float[]{
                a[0] + (b[0] - a[0]) * alpha,
                a[1] + (b[1] - a[1]) * alpha,
                a[2] + (b[2] - a[2]) * alpha};
    }

    public static JsonElement sampleProperty(PropertyChannel channel, float t) {
        if (channel == null || channel.keys.isEmpty()) return null;
        Map.Entry<Float, PropertyKey> floor = channel.keys.floorEntry(t);
        Map.Entry<Float, PropertyKey> ceil = channel.keys.ceilingEntry(t);
        if (floor == null) return ceil.getValue().value.deepCopy();
        if (ceil == null || floor.getKey().equals(ceil.getKey())
                || floor.getValue().lerp == Key.LERP_STEP) {
            return floor.getValue().value.deepCopy();
        }
        JsonElement a = floor.getValue().value;
        JsonElement b = ceil.getValue().value;
        float alpha = (t - floor.getKey()) / (ceil.getKey() - floor.getKey());
        if ("color".equalsIgnoreCase(channel.type)
                && a.isJsonPrimitive() && b.isJsonPrimitive()
                && a.getAsJsonPrimitive().isString() && b.getAsJsonPrimitive().isString()) {
            String mixed = interpolateColor(a.getAsString(), b.getAsString(), alpha);
            if (mixed != null) return new com.google.gson.JsonPrimitive(mixed);
        }
        if (!a.isJsonPrimitive() || !b.isJsonPrimitive()
                || !a.getAsJsonPrimitive().isNumber() || !b.getAsJsonPrimitive().isNumber()) {
            return floor.getValue().value.deepCopy();
        }
        return new com.google.gson.JsonPrimitive(a.getAsDouble() + (b.getAsDouble() - a.getAsDouble()) * alpha);
    }

    private static String interpolateColor(String a, String b, float alpha) {
        try {
            long ca = Long.parseLong(a.replace("#", ""), 16);
            long cb = Long.parseLong(b.replace("#", ""), 16);
            if (a.replace("#", "").length() <= 6) ca |= 0xFF000000L;
            if (b.replace("#", "").length() <= 6) cb |= 0xFF000000L;
            int out = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                int av = (int) ((ca >> shift) & 0xFF);
                int bv = (int) ((cb >> shift) & 0xFF);
                out |= Math.round(av + (bv - av) * alpha) << shift;
            }
            return String.format(java.util.Locale.ROOT, "#%08X", out);
        } catch (Exception ignored) {
            return null;
        }
    }
}
