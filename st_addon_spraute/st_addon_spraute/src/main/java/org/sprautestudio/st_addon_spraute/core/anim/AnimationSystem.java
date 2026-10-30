package org.sprautestudio.st_addon_spraute.core.anim;

import org.sprautestudio.st_addon_spraute.core.model.SpModelInstance;
import org.sprautestudio.st_addon_spraute.runtime.Anims;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named animation layers of one body, later layers over earlier ones. The server only says what plays
 * (encode / decode travel in the entity's synced data), the client poses the model.
 */
public final class AnimationSystem {
    /** Face layers add to the pose under them instead of replacing it. */
    private static final List<String> ADDITIVE = List.of("emotion", "EYES", "EMOTION_COMBINE");

    public final class Layer {
        final String name;
        private Playing current, fading;
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
        final long start = System.nanoTime();
        /** Fading out since (nanos), 0 — not fading. */
        long fadeFrom;
        float fadeSeconds;

        Playing(ConfiguredAnimation ca) {
            this.ca = ca;
        }

        float elapsed(long now) {
            return (now - start) / 1e9f * ca.speed();
        }
    }

    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private long serial;
    private boolean dirty;

    public AnimationSystem() {
        for (String n : List.of("movement", "ambient", "action", "emotion", "EYES", "EMOTION_COMBINE")) layers.put(n, new Layer(n));
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
        release(l, ca.transitionTime(), now);
        l.last = ca.animation();
        l.current = ca.animation().isNull() ? null : new Playing(ca);
    }

    /** The playing animation starts fading out; what was fading before is cut off. */
    private static void release(Layer l, float seconds, long now) {
        if (l.current != null) {
            l.fading = l.current;
            l.fading.fadeFrom = now;
            l.fading.fadeSeconds = seconds;
            l.current = null;
        }
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
            if (p != null) sb.append(p.ca.animation().id()).append('\t').append(p.ca.loopMode().ordinal()).append('\t').append(p.ca.speed()).append('\t').append(p.ca.transitionTime());
            else sb.append("\t0\t1\t").append(0.2f);
            sb.append('\n');
        }
        return sb.toString();
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** Starts what the server started since the last time. */
    public void decode(String data) {
        for (String row : data.split("\n")) {
            String[] f = row.split("\t", -1);
            if (f.length < 6) continue;
            try {
                long s = Long.parseLong(f[1]);
                Layer l = layers.computeIfAbsent(f[0], Layer::new);
                if (l.startSerial == s) continue;
                l.startSerial = s;
                ConfiguredAnimation ca = (f[2].isEmpty() ? Animation.NULL : Anims.resolve(f[2]).orElse(Animation.NULL)).configure();
                ca.loopMode(LoopMode.values()[Integer.parseInt(f[3])]).speed(Float.parseFloat(f[4])).transitionTime(Float.parseFloat(f[5]));
                start(l, ca);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Puts every layer's pose on the model. */
    public void apply(SpModelInstance instance) {
        long now = System.nanoTime();
        for (Layer l : layers.values()) {
            boolean additive = ADDITIVE.contains(l.name);
            float fadeIn = 1;
            if (l.current != null) {
                Playing p = l.current;
                float t = p.ca.transitionTime();
                fadeIn = t <= 0 ? 1 : Math.min(1, (now - p.start) / 1e9f / t);
            }
            if (l.fading != null) {
                Playing p = l.fading;
                float t = p.fadeSeconds <= 0 ? 0 : (now - p.fadeFrom) / 1e9f / p.fadeSeconds;
                // the new animation blends over this one; with nothing new, this one fades away by itself
                float w = l.current != null ? 1 : 1 - t;
                if (t >= 1 || p.fadeSeconds <= 0) l.fading = null;
                else pose(instance, p, now, w, additive);
            }
            if (l.current != null) {
                Playing p = l.current;
                pose(instance, p, now, fadeIn, additive);
                if (p.ca.loopMode() == LoopMode.ONCE && p.elapsed(now) >= p.ca.animation().lengthSeconds()) release(l, 0.15f, now);
            }
        }
    }

    private static void pose(SpModelInstance instance, Playing p, long now, float weight, boolean additive) {
        var clip = p.ca.animation().clip();
        if (clip == null || weight <= 0) return;
        float time = p.elapsed(now);
        boolean loop = p.ca.loopMode() == LoopMode.LOOP;
        if (!loop) time = Math.min(time, clip.getLengthSec());
        clip.apply(instance, time, weight, loop, additive);
    }
}
