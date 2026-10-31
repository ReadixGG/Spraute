package org.sprautestudio.spraute_engine.core.anim;

import org.sprautestudio.spraute_engine.core.model.SpModelInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named animation layers of one body, later layers over earlier ones. The server only says what plays
 * (encode / decode travel in the entity's synced data), the client poses the model.
 */
public final class AnimationSystem {
    /** id -> an animation: where they are found is the game's business. */
    private static java.util.function.Function<String, java.util.Optional<Animation>> library = id -> java.util.Optional.empty();

    public static void setLibrary(java.util.function.Function<String, java.util.Optional<Animation>> found) {
        library = found;
    }

    /** The action layer replaces what is under it; all the others add to the pose. */
    private static final String REPLACING = "action";
    /** Worked out on the spot by the body itself: not saved. */
    private static final List<String> SERVICE = List.of("movement", "turn", "EYES", "LOOK_LEFT", "LOOK_RIGHT");
    private static final float END_FADE = 0.3f;

    public final class Layer {
        final String name;
        private Playing current;
        /** Going away, oldest first: a quick change of clips cuts none of them off. */
        private final List<Playing> fading = new ArrayList<>();
        private Animation last = Animation.NULL;
        private long startSerial;

        Layer(String name) {
            this.name = name;
        }

        /** The animation started here last (it may have played out). */
        public Animation activeAnimation() {
            return last;
        }
    }

    private static final class Playing {
        final ConfiguredAnimation ca;
        /** Where in the clip it is, seconds; moved on as the frames go, at the speed of the moment. */
        private double clock;
        private long last;
        /** A multiplier on the clip's speed: the feet keep up with how fast the body really goes. */
        float rate = 1;
        final long start;
        /** Fading out since (nanos), 0 — not fading. */
        long fadeFrom;
        float fadeSeconds;
        /** Restored: stands on its last frame straight away. */
        boolean settled;

        Playing(ConfiguredAnimation ca) {
            this.ca = ca;
            this.start = System.nanoTime();
            this.last = start;
            this.clock = ca.startTime();
        }

        double clockNow() {
            return clock;
        }

        float elapsed(long now) {
            clock += (now - last) / 1e9 * ca.speed() * rate;
            last = now;
            return (float) clock;
        }
    }

    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private long serial;
    private boolean dirty;

    public AnimationSystem() {
        for (String n : List.of("movement", "turn", "ambient")) layers.put(n, new Layer(n));
        for (int i = 0; i <= 20; i++) layers.put("ANIMATION_" + i, new Layer("ANIMATION_" + i));
        for (String n : List.of("action", "EYES", "EMOTION_COMBINE", "LOOK_LEFT", "LOOK_RIGHT", "emotion")) layers.put(n, new Layer(n));
    }

    public Layer getLayer(String name) {
        return layers.get(name);
    }

    public List<String> getLayerNames() {
        return new ArrayList<>(layers.keySet());
    }

    public void startAnimationAt(String layer, ConfiguredAnimation ca) {
        Layer l = layers.computeIfAbsent(layer, Layer::new);
        l.startSerial = ++serial;
        start(l, ca);
        dirty = true;
    }

    private void start(Layer l, ConfiguredAnimation ca) {
        long now = System.nanoTime();
        release(l, Math.max(ca.transitionTime(), 0.001f), now);
        l.last = ca.animation();
        l.current = ca.animation().isNull() ? null : new Playing(ca);
    }

    /** The playing animation starts fading out. */
    private static void release(Layer l, float seconds, long now) {
        if (l.current != null) {
            Playing p = l.current;
            l.current = null;
            if (seconds <= 0) return;
            p.fadeFrom = now;
            p.fadeSeconds = seconds;
            l.fading.add(p);
        }
    }

    /** How fast this layer's clip goes against its own pace. */
    public void setRate(String layer, float rate) {
        Layer l = layers.get(layer);
        if (l != null && l.current != null) l.current.rate = rate;
    }

    /** 0..1 eased at both ends: a blend starts and ends without a jerk. */
    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    public void stopAnimation(String layer, float fade) {
        startAnimationAt(layer, Animation.NULL.configure().transitionTime(fade));
    }

    /** What plays, for the entity's synced data; "" for nothing has ever played. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Layer l : layers.values()) {
            if (l.startSerial == 0) continue;
            Playing p = l.current;
            sb.append(l.name).append('\t').append(l.startSerial).append('\t');
            if (p != null) sb.append(p.ca.animation().id()).append('\t').append(p.ca.loopMode().ordinal()).append('\t').append(p.ca.speed()).append('\t').append(p.ca.transitionTime()).append('\t').append(p.ca.startTime());
            else sb.append("\t0\t1\t").append(0.2f).append("\t0");
            sb.append('\n');
        }
        return sb.toString();
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** What stays on the layers, for the save: played-out clips and service layers are not kept. */
    public String encodeSaved() {
        StringBuilder sb = new StringBuilder();
        for (Layer l : layers.values()) {
            Playing p = l.current;
            if (p == null || SERVICE.contains(l.name) || p.ca.loopMode() == LoopMode.ONCE) continue;
            sb.append(l.name).append('\t').append(p.ca.animation().id()).append('\t').append(p.ca.loopMode().ordinal()).append('\t').append(p.ca.speed()).append('\n');
        }
        return sb.toString();
    }

    /** Back from a save: at once in the final pose, with no transition. */
    public void restore(String saved) {
        for (String row : saved.split("\n")) {
            String[] f = row.split("\t", -1);
            if (f.length < 4) continue;
            try {
                var a = library.apply(f[1]);
                if (a.isEmpty()) continue;
                ConfiguredAnimation ca = a.get().configure().transitionTime(0);
                ca.loopMode(LoopMode.values()[Integer.parseInt(f[2])]).speed(Float.parseFloat(f[3]));
                Layer l = layers.computeIfAbsent(f[0], Layer::new);
                l.startSerial = ++serial;
                start(l, ca);
                if (ca.loopMode() == LoopMode.HOLD_ON_LAST_FRAME) l.current.settled = true;
                dirty = true;
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** The first news for a new body on a client: what stays is already in place, no transitions. */
    private boolean firstNews = true;

    /** Starts what the server started since the last time. */
    public void decode(String data) {
        boolean instant = firstNews;
        firstNews = false;
        for (String row : data.split("\n")) {
            String[] f = row.split("\t", -1);
            if (f.length < 6) continue;
            try {
                long s = Long.parseLong(f[1]);
                Layer l = layers.computeIfAbsent(f[0], Layer::new);
                if (l.startSerial == s) continue;
                l.startSerial = s;
                ConfiguredAnimation ca = (f[2].isEmpty() ? Animation.NULL : library.apply(f[2]).orElse(Animation.NULL)).configure();
                ca.loopMode(LoopMode.values()[Integer.parseInt(f[3])]).speed(Float.parseFloat(f[4])).transitionTime(Float.parseFloat(f[5]));
                if (f.length > 6) ca.startTime(Float.parseFloat(f[6]));
                if (instant) {
                    // a clip that was playing once when the body came into view is not caught up with
                    if (ca.loopMode() == LoopMode.ONCE) continue;
                    ca.transitionTime(0);
                }
                start(l, ca);
                if (instant && ca.loopMode() == LoopMode.HOLD_ON_LAST_FRAME && l.current != null) l.current.settled = true;
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Puts every layer's pose on the model. */
    public void apply(SpModelInstance instance) {
        long now = System.nanoTime();
        for (Layer l : layers.values()) {
            boolean additive = !l.name.equals(REPLACING);
            float fadeIn = 1;
            if (l.current != null) {
                Playing p = l.current;
                float t = p.ca.transitionTime();
                fadeIn = t <= 0 ? 1 : smooth((now - p.start) / 1e9f / t);
            }
            for (java.util.Iterator<Playing> it = l.fading.iterator(); it.hasNext(); ) {
                Playing p = it.next();
                float t = (now - p.fadeFrom) / 1e9f / p.fadeSeconds;
                if (t >= 1) {
                    it.remove();
                    continue;
                }
                // a replacing layer blends the new clip over the old, which stays whole under it till then;
                // on an adding layer both are summed, so the old one must go as the new one comes
                pose(instance, p, now, additive || l.current == null ? 1 - smooth(t) : 1, additive);
            }
            if (l.current != null) {
                Playing p = l.current;
                pose(instance, p, now, fadeIn, additive);
                if (p.ca.loopMode() == LoopMode.ONCE && p.clockNow() >= p.ca.animation().lengthSeconds()) release(l, END_FADE, now);
            }
        }
    }

    private static void pose(SpModelInstance instance, Playing p, long now, float weight, boolean additive) {
        var clip = p.ca.animation().clip();
        if (clip == null || weight <= 0) return;
        float time = p.settled ? clip.getLengthSec() : p.elapsed(now);
        boolean loop = p.ca.loopMode() == LoopMode.LOOP;
        if (!loop) time = Math.min(time, clip.getLengthSec());
        clip.apply(instance, time, weight, loop, additive);
    }
}
