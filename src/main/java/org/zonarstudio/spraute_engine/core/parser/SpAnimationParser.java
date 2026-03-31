package org.zonarstudio.spraute_engine.core.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;

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
                if (!rot.isEmpty() || !pos.isEmpty()) {
                    boneTracks.put(boneEntry.getKey(), new BoneTrack(rot, pos));
                }
            }
        }

        return new AnimationClip(lengthSec, loop, boneTracks);
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
                keyframes.add(new Keyframe(time, vec));
            }
            keyframes.sort(Comparator.comparingDouble(k -> k.time));
            return new KeyframeTrack(keyframes);
        }

        return KeyframeTrack.EMPTY;
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
            return byLowerName.get(name.toLowerCase(Locale.ROOT));
        }

        public int size() {
            return byName.size();
        }
    }

    public static final class AnimationClip {
        private final float lengthSec;
        private final boolean loop;
        private final Map<String, BoneTrack> bones;

        public AnimationClip(float lengthSec, boolean loop, Map<String, BoneTrack> bones) {
            this.lengthSec = lengthSec;
            this.loop = loop;
            this.bones = bones;
        }

        public float getLengthSec() {
            return lengthSec;
        }

        public boolean isLoop() {
            return loop;
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
                    SpVec3 dst = instance.boneAnimRotation.get(boneEntry.getKey());
                    if (dst != null) {
                        track.rotation.sample(t, sampled);
                        float tx = sampled.x;
                        float ty = -sampled.y;
                        float tz = -sampled.z;
                        if (additive) {
                            dst.add(tx * weight, ty * weight, tz * weight);
                        } else {
                            dst.x = lerp(dst.x, tx, weight);
                            dst.y = lerp(dst.y, ty, weight);
                            dst.z = lerp(dst.z, tz, weight);
                        }
                    }
                }

                if (!track.position.isEmpty()) {
                    SpVec3 dst = instance.boneAnimPosition.get(boneEntry.getKey());
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

    public static final class BoneTrack {
        private final KeyframeTrack rotation;
        private final KeyframeTrack position;

        public BoneTrack(KeyframeTrack rotation, KeyframeTrack position) {
            this.rotation = rotation;
            this.position = position;
        }
    }

    public static final class KeyframeTrack {
        private static final KeyframeTrack EMPTY = new KeyframeTrack(List.of());
        private final float[] times;
        private final SpVec3[] values;

        public KeyframeTrack(List<Keyframe> keyframes) {
            if (keyframes == null || keyframes.isEmpty()) {
                this.times = new float[0];
                this.values = new SpVec3[0];
                return;
            }
            this.times = new float[keyframes.size()];
            this.values = new SpVec3[keyframes.size()];
            for (int i = 0; i < keyframes.size(); i++) {
                Keyframe kf = keyframes.get(i);
                this.times[i] = kf.time;
                this.values[i] = kf.value;
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
            out.set(
                v0.x + (v1.x - v0.x) * alpha,
                v0.y + (v1.y - v0.y) * alpha,
                v0.z + (v1.z - v0.z) * alpha
            );
        }
    }

    public static final class Keyframe {
        private final float time;
        private final SpVec3 value;

        public Keyframe(float time, SpVec3 value) {
            this.time = time;
            this.value = value;
        }
    }
}
