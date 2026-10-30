package org.sprautestudio.st_addon_spraute.runtime.models;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.model.SpGeoModel;
import org.sprautestudio.st_addon_spraute.core.parser.SpAnimationParser;
import org.sprautestudio.st_addon_spraute.core.parser.SpGeoParser;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Custom models of Spraute projects: every folder in a project's "models" holds a Bedrock geometry
 * (.geo.json), a texture (.png) and animations (.animation.json), as Blockbench exports them.
 * Files are parsed when first needed and again when they change on disk — no restart after adding a model.
 */
public final class ModelLibrary {
    private ModelLibrary() {}

    /** A model: its key is "project/name"; the type id used in scripts is "spraute:name". */
    public record Model(String project, String name, Path dir, Path geoFile, List<Path> animFiles, Path texture) {
        public String key() {
            return project + "/" + name;
        }

        public ResourceLocation textureId() {
            return new ResourceLocation(St_addon_spraute.MODID, "textures/models/" + Images.slug(project) + "/" + Images.slug(name) + ".png");
        }

        public SpGeoModel geometry() {
            return parsedGeo(geoFile);
        }
    }

    private record Stamped<T>(long stamp, T value) {
    }

    private static final Map<String, Model> MODELS = new LinkedHashMap<>();
    private static final Map<Path, Stamped<SpGeoModel>> GEOS = new HashMap<>();
    private static final Map<Path, Stamped<SpAnimationParser.AnimationSet>> ANIMS = new HashMap<>();
    private static final Map<String, SpGeoModel> BUILTINS = new HashMap<>();

    private static long stamp(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis() ^ Files.size(f);
        } catch (IOException e) {
            return -1;
        }
    }

    private static synchronized SpGeoModel parsedGeo(Path f) {
        long s = stamp(f);
        Stamped<SpGeoModel> old = GEOS.get(f);
        if (old != null && old.stamp == s) return old.value;
        SpGeoModel m = null;
        try (InputStream in = Files.newInputStream(f)) {
            m = SpGeoParser.parse(in);
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot load {}", f, e);
        }
        GEOS.put(f, new Stamped<>(s, m));
        return m;
    }

    private static synchronized SpAnimationParser.AnimationSet parsedAnims(Path f) {
        long s = stamp(f);
        Stamped<SpAnimationParser.AnimationSet> old = ANIMS.get(f);
        if (old != null && old.stamp == s) return old.value;
        SpAnimationParser.AnimationSet set = null;
        try (InputStream in = Files.newInputStream(f)) {
            set = SpAnimationParser.parse(in);
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot load {}", f, e);
        }
        ANIMS.put(f, new Stamped<>(s, set));
        return set;
    }

    /** The default model every story can use, shipped in the mod. */
    public static synchronized SpGeoModel builtinGeometry(String name) {
        SpGeoModel have = BUILTINS.get(name);
        if (have != null) return have;
        try {
            Path f = ModList.get().getModFileById(St_addon_spraute.MODID).getFile().findResource("assets", St_addon_spraute.MODID, "geo", "entity", name + ".geo.json");
            try (InputStream in = Files.newInputStream(f)) {
                have = SpGeoParser.parse(in);
                BUILTINS.put(name, have);
            }
        } catch (IOException | RuntimeException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot load the model {}", name, e);
        }
        return have;
    }

    public static ResourceLocation builtinTexture() {
        return new ResourceLocation(St_addon_spraute.MODID, "textures/entity/humanoid.png");
    }

    // ================================================================== scanning

    /** The folders of all projects known here: the workspace's and what the server gave; on a dedicated server, what authors uploaded. */
    private static List<Path> projectDirs() {
        List<Path> out = new ArrayList<>();
        Path root = null;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                root = org.sprautestudio.st_addon_spraute.editor.project.Workspace.projectsDir();
            } catch (Throwable t) {
                root = null;
            }
        } else {
            root = org.sprautestudio.st_addon_spraute.content.ProjectContent.root();
        }
        if (root != null && Files.isDirectory(root)) {
            try (Stream<Path> projects = Files.list(root)) {
                projects.filter(Files::isDirectory).sorted().forEach(out::add);
            } catch (IOException e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot scan models", e);
            }
        }
        // later ones win: on someone else's server, their project and not a local one of the same name
        if (FMLEnvironment.dist == Dist.CLIENT) out.addAll(ClientModels.serverProjects());
        return out;
    }

    /** Re-reads every project's models. */
    public static synchronized void scan() {
        List<Path> dirs = projectDirs();
        Map<String, Model> found = new LinkedHashMap<>();
        for (Path pd : dirs) {
            String project = projectName(pd);
            Path md = pd.resolve("models");
            if (!Files.isDirectory(md)) continue;
            try (Stream<Path> models = Files.list(md)) {
                for (Path d : (Iterable<Path>) models.filter(Files::isDirectory).sorted()::iterator) {
                    Model m = model(project, d);
                    if (m != null) found.put(m.key(), m);
                }
            } catch (IOException e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot scan models", e);
            }
        }
        MODELS.clear();
        MODELS.putAll(found);
        EntityLibrary.scan(dirs);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientModels.registerTextures(found.values());
    }

    public static String projectNameOf(Path dir) {
        return projectName(dir);
    }

    private static String projectName(Path dir) {
        // read here and not through the editor's classes, which a dedicated server does not have
        try {
            var json = new com.google.gson.Gson().fromJson(Files.readString(dir.resolve("project.json")), com.google.gson.JsonObject.class);
            if (json != null && json.has("name")) return json.get("name").getAsString();
        } catch (Exception ignored) {
        }
        return dir.getFileName().toString();
    }

    private static Model model(String project, Path d) throws IOException {
        Path geo = null, tex = null;
        List<Path> anims = new ArrayList<>();
        try (Stream<Path> files = Files.list(d)) {
            for (Path f : (Iterable<Path>) files.sorted()::iterator) {
                String n = f.getFileName().toString().toLowerCase();
                if (n.endsWith(".geo.json")) {
                    if (geo == null) geo = f;
                } else if (n.endsWith(".animation.json") || n.endsWith(".animations.json")) {
                    anims.add(f);
                } else if (n.endsWith(".png")) {
                    if (tex == null) tex = f;
                }
            }
        }
        if (geo == null) return null;
        return new Model(project, d.getFileName().toString(), d, geo, Collections.unmodifiableList(anims), tex);
    }

    // ================================================================== lookups

    public static java.util.Collection<Model> all() {
        return Collections.unmodifiableCollection(MODELS.values());
    }

    public static Model byKey(String key) {
        return key == null ? null : MODELS.get(key);
    }

    /** A model by name: "project/name", or a name in the given project, or a name in any project. */
    public static Model find(String project, String name) {
        if (name == null) return null;
        String n = name.trim();
        if (n.startsWith("spraute:")) n = n.substring(8);
        Model m = MODELS.get(n);
        if (m != null) return m;
        if (project != null) {
            m = MODELS.get(project + "/" + n);
            if (m != null) return m;
        }
        for (Model o : MODELS.values()) if (o.name().equalsIgnoreCase(n)) return o;
        return null;
    }

    /** Animation names of a model (their keys as written in the files). */
    public static List<String> animations(Model m) {
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        for (Path f : m.animFiles()) {
            SpAnimationParser.AnimationSet s = parsedAnims(f);
            if (s == null) continue;
            for (String k : s.names()) if (!SpAnimationParser.isMetaClipName(k)) out.add(k);
        }
        Collections.sort(out);
        return out;
    }

    /** An animation of a model by its key, or by its last part ("walk" finds "animation.robot.walk"). */
    public static Optional<Animation> animation(Model m, String name) {
        if (m == null || name == null) return Optional.empty();
        String n = name.trim();
        Animation loose = null;
        for (Path f : m.animFiles()) {
            SpAnimationParser.AnimationSet s = parsedAnims(f);
            if (s == null) continue;
            for (String k : s.names()) {
                var clip = s.get(k);
                if (clip == null) continue;
                if (k.equals(n)) return Optional.of(new Animation("spraute:" + m.key() + "/" + k, clip));
                if (loose == null && (k.endsWith("." + n) || k.endsWith("_" + n) || k.equalsIgnoreCase(n)))
                    loose = new Animation("spraute:" + m.key() + "/" + k, clip);
            }
        }
        return Optional.ofNullable(loose);
    }

    /** "spraute:Model/walk" or "Model/walk" (any project). */
    public static Optional<Animation> resolve(String id) {
        if (id == null) return Optional.empty();
        String s = id.startsWith("spraute:") ? id.substring(8) : id;
        int slash = s.lastIndexOf('/');
        if (slash <= 0) return Optional.empty();
        Model m = find(null, s.substring(0, slash));
        return m == null ? Optional.empty() : animation(m, s.substring(slash + 1));
    }
}
