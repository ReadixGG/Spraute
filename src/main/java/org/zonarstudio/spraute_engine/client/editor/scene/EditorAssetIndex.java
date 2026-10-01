package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scans the {@code spraute_engine} asset folder so the Inspector can offer
 * dropdowns for models, textures and animation files.
 * Also caches spawnable particle type ids and sound ids from the Forge registry.
 */
public final class EditorAssetIndex {
    private static final List<String> models = new ArrayList<>();
    private static final List<String> textures = new ArrayList<>();
    private static final List<String> particleTextures = new ArrayList<>();
    private static final List<String> animations = new ArrayList<>();
    private static final List<String> videos = new ArrayList<>();
    private static final List<String> svgs = new ArrayList<>();
    private static final List<String> particles = new ArrayList<>();
    private static final List<String> sounds = new ArrayList<>();
    private static final List<String> customSounds = new ArrayList<>();
    private static final Map<String, String> soundLabels = new HashMap<>();
    private static final Map<String, List<String>> clipsByAnimFile = new HashMap<>();
    private static final Map<String, Long> clipsCacheMs = new HashMap<>();
    private static boolean scanned;
    private static boolean particlesScanned;
    private static boolean soundsScanned;
    private static long soundsScannedAt;
    private static final long CLIPS_CACHE_TTL_MS = 2000L;
    private static final long SOUNDS_CACHE_TTL_MS = 1500L;

    public static final String VANILLA_SQUARE_PARTICLE = "minecraft:textures/particle/generic_0.png";

    private EditorAssetIndex() {}

    public static List<String> models() { ensureScanned(); return models; }
    public static List<String> textures() { ensureScanned(); return textures; }
    /** Workspace PNG/JPG plus the vanilla square particle sprite. */
    public static List<String> particleTextures() { ensureScanned(); return particleTextures; }
    public static List<String> animations() { ensureScanned(); return animations; }
    public static List<String> videos() { ensureScanned(); return videos; }
    public static List<String> svgs() { ensureScanned(); return svgs; }

    /** Clip names inside a workspace {@code .animation.json} file (cached briefly). */
    public static List<String> animationClips(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) return List.of();
        String key = workspacePath.replace('\\', '/').trim();
        long now = System.currentTimeMillis();
        Long cachedAt = clipsCacheMs.get(key);
        if (cachedAt != null && now - cachedAt < CLIPS_CACHE_TTL_MS && clipsByAnimFile.containsKey(key)) {
            return clipsByAnimFile.get(key);
        }
        List<String> clips = loadAnimationClips(key);
        clipsByAnimFile.put(key, clips);
        clipsCacheMs.put(key, now);
        return clips;
    }

    /** Short label for animation file dropdowns (like Spraute Studio). */
    public static String animationFileLabel(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) return "—";
        String name = workspacePath.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("(?i)\\.animation\\.json$", "").replaceAll("(?i)\\.json$", "");
        return name.isEmpty() ? workspacePath : name;
    }

    public static List<String> particles() {
        ensureParticles();
        return particles;
    }

    /** Minecraft registry sounds + workspace {@code sounds/**.ogg|mp3} as {@code spraute_engine:…}. */
    public static List<String> sounds() {
        ensureSounds();
        return sounds;
    }

    /** Only files from {@code spraute_engine/sounds/} (ogg/mp3), already first in {@link #sounds()}. */
    public static List<String> customSounds() {
        ensureSounds();
        return customSounds;
    }

    public static String soundLabel(String id) {
        if (id == null || id.isBlank()) return "—";
        ensureSounds();
        String label = soundLabels.get(id);
        return label == null || label.isBlank() ? id : label;
    }

    public static void refresh() {
        models.clear();
        textures.clear();
        animations.clear();
        videos.clear();
        svgs.clear();
        clipsByAnimFile.clear();
        clipsCacheMs.clear();
        if (org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()
                && org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.hasList()) {
            for (var e : org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.entries()) {
                if (e.dir) continue;
                String p = e.path;
                String lower = p.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".geo.json")) models.add(p);
                else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    if (p.startsWith("textures/")) textures.add(p);
                } else if (lower.endsWith(".animation.json")) animations.add(p);
                else if (p.startsWith("videos/") && lower.endsWith(".mp4")) videos.add(p);
                else if (lower.endsWith(".svg") && p.startsWith("textures/")) svgs.add(p);
            }
            models.sort(Comparator.naturalOrder());
            textures.sort(Comparator.naturalOrder());
            animations.sort(Comparator.naturalOrder());
            videos.sort(Comparator.naturalOrder());
            svgs.sort(Comparator.naturalOrder());
            rebuildParticleTextures();
            scanned = true;
            refreshParticles();
            refreshSounds();
            return;
        }
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
        scanInto(root.resolve("geo"), root, models, ".geo.json");
        scanInto(root.resolve("textures"), root, textures, ".png", ".jpg", ".jpeg");
        scanInto(root.resolve("animations"), root, animations, ".animation.json");
        scanInto(root.resolve("videos"), root, videos, ".mp4");
        scanInto(root.resolve("textures"), root, svgs, ".svg");
        rebuildParticleTextures();
        scanned = true;
        refreshParticles();
        refreshSounds();
    }

    public static void refreshParticles() {
        particles.clear();
        for (ParticleType<?> type : ForgeRegistries.PARTICLE_TYPES) {
            // Only types that are themselves ParticleOptions (no dust/block/item params).
            if (!(type instanceof ParticleOptions)) continue;
            ResourceLocation id = ForgeRegistries.PARTICLE_TYPES.getKey(type);
            if (id != null) particles.add(id.toString());
        }
        particles.sort(Comparator.naturalOrder());
        particlesScanned = true;
    }

    public static void refreshSounds() {
        org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex.invalidate();
        customSounds.clear();
        soundLabels.clear();
        Set<String> uniq = new LinkedHashSet<>();
        if (org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()
                && org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.hasList()) {
            for (var e : org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.entries()) {
                if (e.dir || !e.path.startsWith("sounds/")
                        || !org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex.isAudioFile(e.path)) {
                    continue;
                }
                String full = org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex.fullIdForRelative(e.path);
                if (full.isBlank()) continue;
                uniq.add(full);
                customSounds.add(full);
                int slash = e.path.lastIndexOf('/');
                soundLabels.put(full, slash >= 0 ? e.path.substring(slash + 1) : e.path);
            }
        } else {
            for (var e : org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex.list()) {
                uniq.add(e.fullId());
                customSounds.add(e.fullId());
                soundLabels.put(e.fullId(), e.label());
            }
        }
        for (SoundEvent event : ForgeRegistries.SOUND_EVENTS) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(event);
            if (id != null) uniq.add(id.toString());
        }
        sounds.clear();
        sounds.addAll(customSounds);
        List<String> rest = new ArrayList<>();
        for (String id : uniq) {
            if (!customSounds.contains(id)) rest.add(id);
        }
        rest.sort(String.CASE_INSENSITIVE_ORDER);
        sounds.addAll(rest);
        soundsScanned = true;
        soundsScannedAt = System.currentTimeMillis();
    }

    private static void rebuildParticleTextures() {
        particleTextures.clear();
        particleTextures.add(VANILLA_SQUARE_PARTICLE);
        for (String texture : textures) {
            if (!VANILLA_SQUARE_PARTICLE.equalsIgnoreCase(texture)) {
                particleTextures.add(texture);
            }
        }
    }

    private static void ensureScanned() {
        if (!scanned) refresh();
    }

    private static void ensureParticles() {
        if (!particlesScanned) refreshParticles();
    }

    private static void ensureSounds() {
        if (!soundsScanned || System.currentTimeMillis() - soundsScannedAt >= SOUNDS_CACHE_TTL_MS) {
            refreshSounds();
        }
    }

    private static List<String> loadAnimationClips(String workspacePath) {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("spraute_engine").resolve(workspacePath).normalize();
            EditorAnimationDocument doc = EditorAnimationDocument.load(file, workspacePath);
            return new ArrayList<>(doc.clips.keySet());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void scanInto(Path dir, Path root, List<String> out, String... extensions) {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String ext : extensions) {
                            if (name.endsWith(ext)) return true;
                        }
                        return false;
                    })
                    .sorted()
                    .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ignored) {
        }
    }
}
