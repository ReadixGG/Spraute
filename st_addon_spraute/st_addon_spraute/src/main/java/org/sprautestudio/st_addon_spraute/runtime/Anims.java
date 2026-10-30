package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.parser.SpAnimationParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Lookup of animations by script id. Ids are either full ({@code st_addon_spraute:entity/humanoid!walk})
 * or short ({@code entity/humanoid}): the short form means the file in the mod's own namespace and its
 * only (or first) animation. {@code spraute:Model/walk} — an animation of a project model.
 */
public final class Anims {
    private Anims() {}

    public static final String DEFAULT_NS = St_addon_spraute.MODID;
    /** What the gesture commands are called in the rig's animation file. */
    private static final Map<String, String> GESTURES = Map.of("nod", "nodsYes", "nope", "nodsNo", "laugh", "laughter", "sigh", "idle_sigh", "shake", "shiver", "refuse", "refuse");

    /** One animation known to the game. */
    public record Entry(ResourceLocation container, String key, String id, double seconds, boolean loops) {
        /** Folder of the container ("entity") for grouping. */
        public String folder() {
            String p = container.getPath();
            int i = p.lastIndexOf('/');
            return container.getNamespace() + ":" + (i < 0 ? "" : p.substring(0, i));
        }

        public String fileName() {
            String p = container.getPath();
            return p.substring(p.lastIndexOf('/') + 1);
        }
    }

    /** The animation files shipped in the mod: file id -> clips. */
    private static Map<ResourceLocation, SpAnimationParser.AnimationSet> files;
    private static List<Entry> cache;

    private static Map<ResourceLocation, SpAnimationParser.AnimationSet> files() {
        if (files != null) return files;
        Map<ResourceLocation, SpAnimationParser.AnimationSet> out = new LinkedHashMap<>();
        try {
            Path root = ModList.get().getModFileById(St_addon_spraute.MODID).getFile().findResource("assets", St_addon_spraute.MODID, "animations");
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    for (Path f : (Iterable<Path>) walk.filter(p -> p.getFileName().toString().endsWith(".animation.json")).sorted()::iterator) {
                        String rel = root.relativize(f).toString().replace('\\', '/').replace(".animation.json", "");
                        try (InputStream in = Files.newInputStream(f)) {
                            out.put(new ResourceLocation(St_addon_spraute.MODID, rel), SpAnimationParser.parse(in));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot read the built-in animations", e);
        }
        files = out;
        return out;
    }

    private static Animation clip(ResourceLocation file, String key, SpAnimationParser.AnimationClip c) {
        return new Animation(file + "!" + key, c);
    }

    public static Optional<Animation> resolve(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String s = id.trim();
        if (s.startsWith("spraute:")) return org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.resolve(s);
        String key = null;
        int bang = s.indexOf('!');
        if (bang >= 0) {
            key = s.substring(bang + 1);
            s = s.substring(0, bang);
        }
        ResourceLocation rl = ResourceLocation.tryParse(s.contains(":") ? s : DEFAULT_NS + ":" + s);
        if (rl == null) return Optional.empty();
        SpAnimationParser.AnimationSet set = files().get(rl);
        if (set == null) return Optional.empty();
        if (key != null) {
            var c = set.get(key);
            return c == null ? Optional.empty() : Optional.of(clip(rl, key, c));
        }
        List<String> keys = new ArrayList<>(set.names());
        keys.removeIf(SpAnimationParser::isMetaClipName);
        if (keys.isEmpty()) return Optional.empty();
        Collections.sort(keys);
        String last = rl.getPath().substring(rl.getPath().lastIndexOf('/') + 1);
        String found = keys.get(0);
        for (String k : keys) if (k.equals(last) || k.endsWith("." + last) || k.endsWith("_" + last)) found = k;
        return Optional.of(clip(rl, found, set.get(found)));
    }

    /** A built-in animation of the default model by its clip name. */
    public static Optional<Animation> humanoid(String name) {
        return resolve(DEFAULT_NS + ":entity/humanoid!" + name);
    }

    /** A gesture for this body: its own animation of that name, else the built-in one. */
    public static Animation gesture(AnimatedEntity e, String name) {
        if (e instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity me && me.look() != null) {
            Optional<Animation> own = me.look().animation(name);
            if (own.isPresent()) return own.get();
        }
        return humanoid(GESTURES.getOrDefault(name, name)).orElse(null);
    }

    /** Puts an emotion on the face layer: a model's own clip "emotion_<name>", else the default model's. */
    public static void showEmotion(AnimatedEntity e, String name) {
        String clip = org.sprautestudio.st_addon_spraute.core.anim.Emotions.clip(name);
        if (clip == null) return;
        Optional<Animation> a = Optional.empty();
        if (e instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity me && me.look() != null) {
            a = me.look().animation("emotion_" + name.toLowerCase(java.util.Locale.ROOT));
            if (a.isEmpty() && !clip.isEmpty()) a = me.look().animation(clip);
        }
        if (a.isEmpty() && !clip.isEmpty()) a = humanoid(clip);
        if (a.isPresent()) e.startAnimation("emotion", a.get().configure().transitionTime(0.25f).loopMode(org.sprautestudio.st_addon_spraute.core.anim.LoopMode.HOLD_ON_LAST_FRAME));
        else e.getAnimationSystem().stopAnimation("emotion", 0.25f);
    }

    public static boolean exists(String id) {
        try {
            return resolve(id).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Length of one playback in ticks at the given speed. */
    public static int lengthTicks(Animation a, double speed) {
        if (speed <= 0) speed = 1;
        return (int) Math.ceil(a.lengthSeconds() * 20.0 / speed);
    }

    /** All built-in animations, sorted by id. */
    public static synchronized List<Entry> all() {
        if (cache != null) return cache;
        List<Entry> list = new ArrayList<>();
        files().forEach((rl, set) -> {
            TreeSet<String> keys = new TreeSet<>(set.names());
            keys.removeIf(SpAnimationParser::isMetaClipName);
            for (String k : keys) {
                var c = set.get(k);
                if (c != null) list.add(new Entry(rl, k, idFor(rl, k, keys.size()), c.getLengthSec(), c.isLoop()));
            }
        });
        list.sort((x, y) -> x.id().compareTo(y.id()));
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    /** Shortest id that resolves to this animation. */
    public static String idFor(ResourceLocation container, String key, int keysInContainer) {
        String base = container.getNamespace().equals(DEFAULT_NS) ? container.getPath() : container.toString();
        return keysInContainer == 1 ? base : base + "!" + key;
    }
}
