package org.sprautestudio.st_addon_spraute.core.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sprautestudio.st_addon_spraute.core.math.SpVec3;
import org.sprautestudio.st_addon_spraute.core.model.SpModelInstance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SpAnimationParser {
    private SpAnimationParser() {}

    public static AnimationSet parse(InputStream inputStream) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
        return parseRoot(root);
    }

    public static AnimationSet parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return parseRoot(root);
    }

    private static AnimationSet parseRoot(JsonObject root) {
        JsonObject animationsObj = root.getAsJsonObject("animations");
        if (animationsObj == null || animationsObj.size() == 0) {
            return new AnimationSet(Map.of());
        }

        Map<String, AnimationClip> byName = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : animationsObj.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            AnimationClip clip = parseClip(entry.getValue().getAsJsonObject());
            byName.put(entry.getKey(), clip);
        }
        return new AnimationSet(byName);
    }

    private static AnimationClip parseClip(JsonObject obj) {
        float lengthSec = obj.has("animation_length") ? parseFloat(obj.get("animation_length")) : 0f;
        boolean loop = parseLoopFlag(obj.get("loop"));

        Map<String, BoneTrack> boneTracks = new LinkedHashMap<>();
        JsonObject bonesObj = obj.getAsJsonObject("bones");
        if (bonesObj != null) {
            for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
                if (!boneEntry.getValue().isJsonObject()) continue;
                JsonObject boneObj = boneEntry.getValue().getAsJsonObject();
                KeyframeTrack rot = parseChannel(boneObj.get("rotation"));
                KeyframeTrack pos = parseChannel(boneObj.get("position"));
                KeyframeTrack scale = parseChannel(boneObj.get("scale"));
                if (!rot.isEmpty() || !pos.isEmpty() || !scale.isEmpty()) {
                    boneTracks.put(boneEntry.getKey(), new BoneTrack(rot, pos, scale));
                }
            }
        }

        Map<String, Map<String, PropertyTrack>> propertyTracks = new LinkedHashMap<>();
        JsonObject targetsObj = obj.getAsJsonObject("spraute_properties");
        if (targetsObj != null) {
            for (Map.Entry<String, JsonElement> targetEntry : targetsObj.entrySet()) {
                if (!targetEntry.getValue().isJsonObject()) continue;
                Map<String, PropertyTrack> target = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> propertyEntry
                        : targetEntry.getValue().getAsJsonObject().entrySet()) {
                    if (!propertyEntry.getValue().isJsonObject()) continue;
                    JsonObject property = propertyEntry.getValue().getAsJsonObject();
                    String type = property.has("type") ? property.get("type").getAsString() : "number";
                    JsonObject keys = property.getAsJsonObject("keys");
                    if (keys == null) continue;
                    List<PropertyKeyframe> frames = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> keyEntry : keys.entrySet()) {
                        Float time = parseTime(keyEntry.getKey());
                        if (time == null) continue;
                        JsonElement value = keyEntry.getValue();
                        int lerp = Keyframe.LERP_LINEAR;
                        if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
                            lerp = parseLerpMode(value);
                            value = value.getAsJsonObject().get("value");
                        }
                        frames.add(new PropertyKeyframe(time, value.deepCopy(), lerp));
                    }
                    frames.sort(Comparator.comparingDouble(PropertyKeyframe::time));
                    if (!frames.isEmpty()) target.put(propertyEntry.getKey(), new PropertyTrack(type, frames));
                }
                if (!target.isEmpty()) propertyTracks.put(targetEntry.getKey(), target);
            }
        }

        return new AnimationClip(lengthSec, loop, boneTracks, propertyTracks);
    }

    private static KeyframeTrack parseChannel(JsonElement channelEl) {
        if (channelEl == null || channelEl.isJsonNull()) {
            return KeyframeTrack.EMPTY;
        }

        List<Keyframe> keyframes = new ArrayList<>();

        if (channelEl.isJsonArray()) {
            SpVec3 vec = parseVector(channelEl);
            if (vec != null) keyframes.add(new Keyframe(0f, vec));
            return new KeyframeTrack(keyframes);
        }

        if (channelEl.isJsonObject()) {
            JsonObject obj = channelEl.getAsJsonObject();
            SpVec3 single = parseVector(channelEl);
            if (single != null && hasVectorLikeField(obj)) {
                keyframes.add(new Keyframe(0f, single));
                return new KeyframeTrack(keyframes);
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                Float time = parseTime(entry.getKey());
                if (time == null) continue;
                SpVec3 vec = parseVector(entry.getValue());
                if (vec == null) continue;
                keyframes.add(new Keyframe(time, vec, parseLerpMode(entry.getValue())));
            }
            keyframes.sort(Comparator.comparingDouble(k -> k.time));
            return new KeyframeTrack(keyframes);
        }

        return KeyframeTrack.EMPTY;
    }

    /** Blockbench interpolation: linear (default), catmullrom (smooth), step. */
    private static int parseLerpMode(JsonElement el) {
        if (el == null || !el.isJsonObject()) return Keyframe.LERP_LINEAR;
        JsonElement mode = el.getAsJsonObject().get("lerp_mode");
        if (mode == null || mode.isJsonNull()) return Keyframe.LERP_LINEAR;
        try {
            String s = mode.getAsString().toLowerCase(Locale.ROOT);
            if (s.contains("catmull") || s.contains("smooth")) return Keyframe.LERP_CATMULLROM;
            if (s.contains("step")) return Keyframe.LERP_STEP;
        } catch (Exception ignored) {
        }
        return Keyframe.LERP_LINEAR;
    }

    private static boolean hasVectorLikeField(JsonObject obj) {
        return obj.has("vector") || obj.has("post") || obj.has("pre");
    }

    private static Float parseTime(String raw) {
        try {
            return Float.parseFloat(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SpVec3 parseVector(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() < 3) return null;
            return new SpVec3(parseFloat(arr.get(0)), parseFloat(arr.get(1)), parseFloat(arr.get(2)));
        }

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("vector")) return parseVector(obj.get("vector"));
            if (obj.has("post")) return parseVector(obj.get("post"));
            if (obj.has("pre")) return parseVector(obj.get("pre"));
        }

        return null;
    }

    private static float parseFloat(JsonElement el) {
        try {
            if (el == null || el.isJsonNull()) return 0f;
            if (!el.isJsonPrimitive()) return 0f;
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsFloat();
            return Float.parseFloat(el.getAsString());
        } catch (Exception ignored) {
            return 0f;
        }
    }

    /** Blockbench files often start with author/section keys that are not real clips. */
    public static boolean isMetaClipName(String name) {
        if (name == null || name.isBlank()) return true;
        String t = name.trim();
        char c = t.charAt(0);
        return c == '[' || c == '#' || t.startsWith("---") || t.startsWith("//");
    }

    private static boolean parseLoopFlag(JsonElement el) {
        if (el == null || el.isJsonNull()) return false;
        try {
            if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            String s = el.getAsString().toLowerCase(Locale.ROOT);
            return "true".equals(s) || "loop".equals(s) || "hold_on_last_frame".equals(s);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static final class AnimationSet {
        private final Map<String, AnimationClip> byName;
        private final Map<String, AnimationClip> byLowerName;

        public AnimationSet(Map<String, AnimationClip> byName) {
            this.byName = byName;
            this.byLowerName = new LinkedHashMap<>();
            for (Map.Entry<String, AnimationClip> entry : byName.entrySet()) {
                this.byLowerName.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        public AnimationClip get(String name) {
            if (name == null || name.isEmpty()) return null;
            AnimationClip exact = byName.get(name);
            if (exact != null) return exact;
            String lower = name.toLowerCase(Locale.ROOT);
            AnimationClip byLower = byLowerName.get(lower);
            if (byLower != null) return byLower;

            // Short name → Bedrock "animation.foo.bar". Prefer a unique suffix;
            // never grab the first JSON key just because it happens to end with the name.
            AnimationClip unique = null;
            int matches = 0;
            for (Map.Entry<String, AnimationClip> e : byLowerName.entrySet()) {
                String key = e.getKey();
                if (isMetaClipName(key)) continue;
                boolean hit = key.endsWith("." + lower) || key.endsWith("/" + lower);
                if (!hit) {
                    int dot = key.lastIndexOf('.');
                    hit = dot >= 0 && key.substring(dot + 1).equals(lower);
                }
                if (!hit) continue;
                matches++;
                unique = e.getValue();
            }
            return matches == 1 ? unique : null;
        }

        /** Idle, then the first authored clip that actually moves bones. */
        public AnimationClip playable(String preferred) {
            if (preferred != null && !preferred.isBlank() && !isMetaClipName(preferred)) {
                AnimationClip clip = get(preferred);
                if (clip != null && !clip.bones.isEmpty()) return clip;
            }
            AnimationClip idle = get("idle");
            if (idle != null && !idle.bones.isEmpty()) return idle;
            for (Map.Entry<String, AnimationClip> e : byName.entrySet()) {
                if (isMetaClipName(e.getKey())) continue;
                if (e.getValue() != null && !e.getValue().bones.isEmpty()) return e.getValue();
            }
            return null;
        }

        public String firstPlayableName(String preferred) {
            if (preferred != null && !preferred.isBlank() && get(preferred) != null && !isMetaClipName(preferred)) {
                return preferred;
            }
            if (get("idle") != null) {
                for (String key : byName.keySet()) {
                    if (key.equalsIgnoreCase("idle")) return key;
                }
            }
            for (String key : byName.keySet()) {
                if (isMetaClipName(key)) continue;
                AnimationClip clip = byName.get(key);
                if (clip != null && !clip.bones.isEmpty()) return key;
            }
            return "";
        }

        public int size() {
            return byName.size();
        }

        public java.util.Set<String> names() {
            return byName.keySet();
        }
    }

    public static final class AnimationClip {
        private final float lengthSec;
        private final boolean loop;
        private final Map<String, BoneTrack> bones;
        private final Map<String, Map<String, PropertyTrack>> properties;

        public AnimationClip(float lengthSec, boolean loop, Map<String, BoneTrack> bones) {
            this(lengthSec, loop, bones, Map.of());
        }

        public AnimationClip(float lengthSec, boolean loop, Map<String, BoneTrack> bones,
                             Map<String, Map<String, PropertyTrack>> properties) {
            this.lengthSec = lengthSec;
            this.loop = loop;
            this.bones = bones;
            this.properties = properties == null ? Map.of() : properties;
        }

        public float getLengthSec() {
            return lengthSec;
        }

        public boolean isLoop() {
            return loop;
        }

        /** Bone name -> track; also used by the GUI animator (bone name = widget id). */
        public Map<String, BoneTrack> bones() {
            return bones;
        }

        /** Scene node id -> inspector property id -> sampled channel. */
        public Map<String, Map<String, PropertyTrack>> properties() {
            return properties;
        }

        public void apply(SpModelInstance instance, float elapsedSec, float weight) {
            apply(instance, elapsedSec, weight, loop, true);
        }

        /**
         * @param additive if true, accumulate weighted samples (good for deltas / layered mouth on top of idle).
         *                 if false, lerp each animated bone toward this clip's keyframe (good when keyframes are
         *                 absolute poses — avoids double-counting the same bone across idle + overlay).
         */
        public void apply(SpModelInstance instance, float elapsedSec, float weight, boolean playbackLoop) {
            apply(instance, elapsedSec, weight, playbackLoop, true);
        }

        public void apply(SpModelInstance instance, float elapsedSec, float weight, boolean playbackLoop, boolean additive) {
            if (bones.isEmpty() || weight <= 0f) return;
            float t = resolveTime(elapsedSec, playbackLoop);
            SpVec3 sampled = new SpVec3();
            for (Map.Entry<String, BoneTrack> boneEntry : bones.entrySet()) {
                BoneTrack track = boneEntry.getValue();

                if (!track.rotation.isEmpty()) {
                    SpVec3 dst = instance.animRotation(boneEntry.getKey());
                    if (dst != null) {
                        track.rotation.sample(t, sampled);
                        // File Euler as authored in Blockbench. Axis conversion lives in
                        // SpModelInstance (conjugation for per-vertex X flip).
                        if (additive) {
                            dst.add(sampled.x * weight, sampled.y * weight, sampled.z * weight);
                        } else {
                            dst.x = lerp(dst.x, sampled.x, weight);
                            dst.y = lerp(dst.y, sampled.y, weight);
                            dst.z = lerp(dst.z, sampled.z, weight);
                        }
                    }
                }

                if (!track.position.isEmpty()) {
                    SpVec3 dst = instance.animPosition(boneEntry.getKey());
                    if (dst != null) {
                        track.position.sample(t, sampled);
                        if (additive) {
                            dst.add(sampled.x * weight, sampled.y * weight, sampled.z * weight);
                        } else {
                            dst.x = lerp(dst.x, sampled.x, weight);
                            dst.y = lerp(dst.y, sampled.y, weight);
                            dst.z = lerp(dst.z, sampled.z, weight);
                        }
                    }
                }

                if (!track.scale.isEmpty()) {
                    SpVec3 dst = instance.animScale(boneEntry.getKey());
                    if (dst != null) {
                        track.scale.sample(t, sampled);
                        // Absolute clips stacked additively can go negative and mirror/stretch (e.g. eyes).
                        // Zero is allowed (blink). Negative is not.
                        if (additive) {
                            dst.x += (sampled.x - 1f) * weight;
                            dst.y += (sampled.y - 1f) * weight;
                            dst.z += (sampled.z - 1f) * weight;
                        } else {
                            dst.x = lerp(dst.x, sampled.x, weight);
                            dst.y = lerp(dst.y, sampled.y, weight);
                            dst.z = lerp(dst.z, sampled.z, weight);
                        }
                        if (dst.x < 0f) dst.x = 0f;
                        if (dst.y < 0f) dst.y = 0f;
                        if (dst.z < 0f) dst.z = 0f;
                    }
                }
            }
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private float resolveTime(float elapsedSec, boolean playbackLoop) {
            if (lengthSec <= 0f) return Math.max(0f, elapsedSec);
            if (playbackLoop) {
                float mod = elapsedSec % lengthSec;
                if (mod < 0f) mod += lengthSec;
                return mod;
            }
            if (elapsedSec < 0f) return 0f;
            return Math.min(elapsedSec, lengthSec);
        }
    }

    public record PropertyKeyframe(float time, JsonElement value, int lerpMode) {}

    public static final class PropertyTrack {
        private final String type;
        private final List<PropertyKeyframe> frames;

        public PropertyTrack(String type, List<PropertyKeyframe> frames) {
            this.type = type == null ? "number" : type;
            this.frames = frames == null ? List.of() : List.copyOf(frames);
        }

        public String type() { return type; }
        public boolean isEmpty() { return frames.isEmpty(); }

        /** Numbers interpolate; booleans, strings, enums and resources step. */
        public JsonElement sample(float time) {
            if (frames.isEmpty()) return null;
            PropertyKeyframe floor = frames.get(0);
            PropertyKeyframe ceil = floor;
            for (PropertyKeyframe frame : frames) {
                if (frame.time() <= time) floor = frame;
                if (frame.time() >= time) {
                    ceil = frame;
                    break;
                }
                ceil = frame;
            }
            if (floor == ceil || floor.lerpMode() == Keyframe.LERP_STEP
                    || !floor.value().isJsonPrimitive() || !ceil.value().isJsonPrimitive()) {
                return floor.value().deepCopy();
            }
            float span = ceil.time() - floor.time();
            if (span <= 0.000001f) return floor.value().deepCopy();
            double alpha = (time - floor.time()) / span;
            if ("color".equalsIgnoreCase(type)
                    && floor.value().getAsJsonPrimitive().isString()
                    && ceil.value().getAsJsonPrimitive().isString()) {
                String mixed = interpolateColor(floor.value().getAsString(), ceil.value().getAsString(), alpha);
                if (mixed != null) return new com.google.gson.JsonPrimitive(mixed);
            }
            if (!floor.value().getAsJsonPrimitive().isNumber()
                    || !ceil.value().getAsJsonPrimitive().isNumber()) {
                return floor.value().deepCopy();
            }
            double a = floor.value().getAsDouble();
            double b = ceil.value().getAsDouble();
            return new com.google.gson.JsonPrimitive(a + (b - a) * alpha);
        }

        private static String interpolateColor(String a, String b, double alpha) {
            try {
                String ah = a.replace("#", "");
                String bh = b.replace("#", "");
                long ca = Long.parseLong(ah, 16);
                long cb = Long.parseLong(bh, 16);
                if (ah.length() <= 6) ca |= 0xFF000000L;
                if (bh.length() <= 6) cb |= 0xFF000000L;
                int out = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    int av = (int) ((ca >> shift) & 0xFF);
                    int bv = (int) ((cb >> shift) & 0xFF);
                    out |= (int) Math.round(av + (bv - av) * alpha) << shift;
                }
                return String.format(Locale.ROOT, "#%08X", out);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static final class BoneTrack {
        private final KeyframeTrack rotation;
        private final KeyframeTrack position;
        private final KeyframeTrack scale;

        public BoneTrack(KeyframeTrack rotation, KeyframeTrack position, KeyframeTrack scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale != null ? scale : KeyframeTrack.EMPTY;
        }

        public KeyframeTrack rotation() { return rotation; }
        public KeyframeTrack position() { return position; }
        public KeyframeTrack scale() { return scale; }
    }

    public static final class KeyframeTrack {
        private static final KeyframeTrack EMPTY = new KeyframeTrack(List.of());
        private final float[] times;
        private final SpVec3[] values;
        private final int[] lerpModes;

        public KeyframeTrack(List<Keyframe> keyframes) {
            if (keyframes == null || keyframes.isEmpty()) {
                this.times = new float[0];
                this.values = new SpVec3[0];
                this.lerpModes = new int[0];
                return;
            }
            this.times = new float[keyframes.size()];
            this.values = new SpVec3[keyframes.size()];
            this.lerpModes = new int[keyframes.size()];
            for (int i = 0; i < keyframes.size(); i++) {
                Keyframe kf = keyframes.get(i);
                this.times[i] = kf.time;
                this.values[i] = kf.value;
                this.lerpModes[i] = kf.lerpMode;
            }
        }

        public boolean isEmpty() {
            return times.length == 0;
        }

        public void sample(float t, SpVec3 out) {
            if (times.length == 0) {
                out.set(0, 0, 0);
                return;
            }
            if (times.length == 1 || t <= times[0]) {
                out.set(values[0]);
                return;
            }

            int last = times.length - 1;
            if (t >= times[last]) {
                out.set(values[last]);
                return;
            }

            int idx = 0;
            while (idx + 1 < times.length && t > times[idx + 1]) {
                idx++;
            }

            float t0 = times[idx];
            float t1 = times[idx + 1];
            SpVec3 v0 = values[idx];
            SpVec3 v1 = values[idx + 1];
            float alpha = (t - t0) / (t1 - t0);

            // Step: hold the left keyframe value for the whole segment.
            if (lerpModes[idx] == Keyframe.LERP_STEP) {
                out.set(v0);
                return;
            }

            // Catmull-Rom (Blockbench "smooth"): spline through neighbouring keys.
            if (lerpModes[idx] == Keyframe.LERP_CATMULLROM || lerpModes[idx + 1] == Keyframe.LERP_CATMULLROM) {
                SpVec3 p0 = values[Math.max(0, idx - 1)];
                SpVec3 p3 = values[Math.min(last, idx + 2)];
                out.set(
                    catmullRom(alpha, p0.x, v0.x, v1.x, p3.x),
                    catmullRom(alpha, p0.y, v0.y, v1.y, p3.y),
                    catmullRom(alpha, p0.z, v0.z, v1.z, p3.z)
                );
                return;
            }

            out.set(
                v0.x + (v1.x - v0.x) * alpha,
                v0.y + (v1.y - v0.y) * alpha,
                v0.z + (v1.z - v0.z) * alpha
            );
        }

        private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
            float t2 = t * t;
            float t3 = t2 * t;
            return 0.5f * ((2f * p1)
                    + (-p0 + p2) * t
                    + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                    + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
        }
    }

    public static final class Keyframe {
        public static final int LERP_LINEAR = 0;
        public static final int LERP_CATMULLROM = 1;
        public static final int LERP_STEP = 2;

        private final float time;
        private final SpVec3 value;
        private final int lerpMode;

        public Keyframe(float time, SpVec3 value) {
            this(time, value, LERP_LINEAR);
        }

        public Keyframe(float time, SpVec3 value, int lerpMode) {
            this.time = time;
            this.value = value;
            this.lerpMode = lerpMode;
        }
    }
}
