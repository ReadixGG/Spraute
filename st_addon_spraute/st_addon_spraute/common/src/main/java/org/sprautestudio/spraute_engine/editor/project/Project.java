package org.sprautestudio.spraute_engine.editor.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.editor.visual.VisualScript;
import org.sprautestudio.spraute_engine.lang.Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** A Spraute project folder: project.json, scripts/*.sst and blocks/*.svs. */
public final class Project {
    public enum Kind {
        SCRIPT(".sst"), BLOCKS(".svs"), GUI(".sgui"), CUTSCENE(".scut"), EFFECT(".sfx"), SHOP(".sshop"), QUEST(".squest"), ZONE(".szone"), FIGHT(".sfight"), PATH(".spath");

        public final String ext;

        Kind(String ext) {
            this.ext = ext;
        }

        public static Kind ofFile(String name) {
            for (Kind k : values()) if (name.endsWith(k.ext)) return k;
            return SCRIPT;
        }
    }

    public static final class Meta {
        public String name;
        public long created;
        public long opened;
        public int baseId = 5001;
        public List<String> openFiles = new ArrayList<>();
    }

    public record FileRef(Path path, Kind kind) {
        public String name() {
            String n = path.getFileName().toString();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }

        public String fileName() {
            return path.getFileName().toString();
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final Path dir;
    public Meta meta;

    private Project(Path dir, Meta meta) {
        this.dir = dir;
        this.meta = meta;
    }

    public String name() {
        return meta.name;
    }

    public Path scriptsDir() {
        return dir.resolve("scripts");
    }

    public Path blocksDir() {
        return dir.resolve("blocks");
    }

    public Path guisDir() {
        return dir.resolve("guis");
    }

    /** GUI name -> JSON of every interface of the project (sent to the server on deploy). */
    public java.util.Map<String, String> guiSources() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (FileRef f : files()) {
            if (f.kind() != Kind.GUI) continue;
            try {
                map.put(f.name(), read(f));
            } catch (IOException ignored) {
            }
        }
        return map;
    }

    public Path cutscenesDir() {
        return dir.resolve("cutscenes");
    }

    public Path fightsDir() {
        return dir.resolve("fights");
    }

    public Path pathsDir() {
        return dir.resolve("paths");
    }

    public List<String> pathNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.PATH) list.add(f.name());
        return list;
    }

    public FileRef newPath(String name, net.minecraft.world.phys.Vec3 at) throws IOException {
        Files.createDirectories(pathsDir());
        FileRef f = new FileRef(pathsDir().resolve(sanitize(name) + Kind.PATH.ext), Kind.PATH);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.path.PathDef.example(at).toJson());
        return f;
    }

    public List<String> fightNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.FIGHT) list.add(f.name());
        return list;
    }

    public FileRef newFight(String name) throws IOException {
        Files.createDirectories(fightsDir());
        FileRef f = new FileRef(fightsDir().resolve(sanitize(name) + Kind.FIGHT.ext), Kind.FIGHT);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.fight.FightDef.example(name).toJson());
        return f;
    }

    /** The folder of a kind of files (its subfolders are the user's own groups). */
    public Path kindDir(Kind k) {
        return switch (k) {
            case SCRIPT -> scriptsDir();
            case BLOCKS -> blocksDir();
            case GUI -> guisDir();
            case CUTSCENE -> cutscenesDir();
            case EFFECT -> effectsDir();
            case SHOP -> shopsDir();
            case QUEST -> questsDir();
            case ZONE -> zonesDir();
            case FIGHT -> fightsDir();
            case PATH -> pathsDir();
        };
    }

    /** Subfolders of a kind's folder, every level, sorted. */
    public List<Path> subfolders(Kind k) {
        List<Path> out = new ArrayList<>();
        Path root = kindDir(k);
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> !p.equals(root) && Files.isDirectory(p))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase())).forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Moves a file into a folder (of its kind); returns the new place. */
    public FileRef moveTo(FileRef f, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path to = dir.resolve(f.path().getFileName());
        if (Files.exists(to)) throw new IOException("Там уже есть файл «" + f.fileName() + "»");
        Files.move(f.path(), to);
        return new FileRef(to, f.kind());
    }

    public Path zonesDir() {
        return dir.resolve("zones");
    }

    public List<String> zoneNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.ZONE) list.add(f.name());
        return list;
    }

    /** name -> JSON of every zone (for drawing them in the world). */
    public java.util.Map<String, String> zoneSources() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (FileRef f : files()) if (f.kind() == Kind.ZONE) {
            try {
                m.put(f.name(), read(f));
            } catch (IOException ignored) {
            }
        }
        return m;
    }

    public FileRef newZone(String name, net.minecraft.world.phys.Vec3 at) throws IOException {
        Files.createDirectories(zonesDir());
        FileRef f = new FileRef(zonesDir().resolve(sanitize(name) + Kind.ZONE.ext), Kind.ZONE);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.zone.ZoneDef.example(name, at).toJson());
        return f;
    }

    public Path questsDir() {
        return dir.resolve("quests");
    }

    public List<String> questNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.QUEST) list.add(f.name());
        return list;
    }

    public FileRef newQuest(String name) throws IOException {
        Files.createDirectories(questsDir());
        FileRef f = new FileRef(questsDir().resolve(sanitize(name) + Kind.QUEST.ext), Kind.QUEST);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.quest.QuestDef.example(name).toJson());
        return f;
    }

    public Path shopsDir() {
        return dir.resolve("shops");
    }

    public List<String> shopNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.SHOP) list.add(f.name());
        return list;
    }

    public FileRef newShop(String name) throws IOException {
        Files.createDirectories(shopsDir());
        FileRef f = new FileRef(shopsDir().resolve(sanitize(name) + Kind.SHOP.ext), Kind.SHOP);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.shop.ShopDef.example(name).toJson());
        return f;
    }

    public Path effectsDir() {
        return dir.resolve("effects");
    }

    public List<String> effectNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.EFFECT) list.add(f.name());
        return list;
    }

    /** A new effect, a copy of a built-in one. */
    public FileRef newEffect(String name, String preset) throws IOException {
        Files.createDirectories(effectsDir());
        FileRef f = new FileRef(effectsDir().resolve(sanitize(name) + Kind.EFFECT.ext), Kind.EFFECT);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        org.sprautestudio.spraute_engine.fx.FxDef d = org.sprautestudio.spraute_engine.fx.FxPresets.get(preset);
        write(f, (d != null ? d : new org.sprautestudio.spraute_engine.fx.FxDef()).toJson());
        return f;
    }

    public List<String> cutsceneNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.CUTSCENE) list.add(f.name());
        return list;
    }

    public FileRef newCutscene(String name) throws IOException {
        Files.createDirectories(cutscenesDir());
        FileRef f = new FileRef(cutscenesDir().resolve(sanitize(name) + Kind.CUTSCENE.ext), Kind.CUTSCENE);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.cutscene.CutsceneDoc.example(name).toJson());
        return f;
    }

    /** Interfaces and cutscenes ("cutscene:<name>") sent to the server with a story. */
    public java.util.Map<String, String> deploySources() {
        java.util.Map<String, String> map = guiSources();
        for (FileRef f : files()) {
            if (f.kind() != Kind.CUTSCENE && f.kind() != Kind.EFFECT && f.kind() != Kind.SHOP && f.kind() != Kind.QUEST && f.kind() != Kind.ZONE && f.kind() != Kind.FIGHT
                    && f.kind() != Kind.PATH) continue;
            try {
                map.put((f.kind() == Kind.EFFECT ? "fx:" : f.kind() == Kind.SHOP ? "shop:" : f.kind() == Kind.QUEST ? "quest:" : f.kind() == Kind.ZONE ? "zone:"
                        : f.kind() == Kind.FIGHT ? "fight:" : f.kind() == Kind.PATH ? "path:" : "cutscene:") + f.name(), read(f));
            } catch (IOException ignored) {
            }
        }
        return map;
    }

    public List<String> guiNames() {
        List<String> list = new ArrayList<>();
        for (FileRef f : files()) if (f.kind() == Kind.GUI) list.add(f.name());
        return list;
    }

    public FileRef newGui(String name) throws IOException {
        FileRef f = new FileRef(guisDir().resolve(sanitize(name) + Kind.GUI.ext), Kind.GUI);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, org.sprautestudio.spraute_engine.gui.GuiDoc.example(name).toJson());
        return f;
    }

    // ------------------------------------------------------------------ io

    public static Project create(Path dir, String name) throws IOException {
        Files.createDirectories(dir.resolve("scripts"));
        Files.createDirectories(dir.resolve("blocks"));
        Meta m = new Meta();
        m.name = name;
        m.created = m.opened = System.currentTimeMillis();
        Project p = new Project(dir, m);
        p.saveMeta();
        return p;
    }

    public static Project open(Path dir) throws IOException {
        Path metaFile = dir.resolve("project.json");
        Meta m = Files.exists(metaFile) ? GSON.fromJson(Files.readString(metaFile, StandardCharsets.UTF_8), Meta.class) : null;
        if (m == null) {
            m = new Meta();
            m.name = dir.getFileName().toString();
            m.created = System.currentTimeMillis();
        }
        if (m.openFiles == null) m.openFiles = new ArrayList<>();
        Files.createDirectories(dir.resolve("scripts"));
        Files.createDirectories(dir.resolve("blocks"));
        m.opened = System.currentTimeMillis();
        Project p = new Project(dir, m);
        p.saveMeta();
        return p;
    }

    /** Reads the project without touching its "last opened" time. */
    public static Project openReadOnly(Path dir) throws IOException {
        Path metaFile = dir.resolve("project.json");
        Meta m = Files.exists(metaFile) ? GSON.fromJson(Files.readString(metaFile, StandardCharsets.UTF_8), Meta.class) : null;
        if (m == null) {
            m = new Meta();
            m.name = dir.getFileName().toString();
        }
        if (m.openFiles == null) m.openFiles = new ArrayList<>();
        return new Project(dir, m);
    }

    public void saveMeta() {
        try {
            Files.writeString(dir.resolve("project.json"), GSON.toJson(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to save project meta", e);
        }
    }

    public List<FileRef> files() {
        List<FileRef> list = new ArrayList<>();
        collect(scriptsDir(), ".sst", Kind.SCRIPT, list);
        collect(blocksDir(), ".svs", Kind.BLOCKS, list);
        collect(guisDir(), Kind.GUI.ext, Kind.GUI, list);
        collect(cutscenesDir(), Kind.CUTSCENE.ext, Kind.CUTSCENE, list);
        collect(effectsDir(), Kind.EFFECT.ext, Kind.EFFECT, list);
        collect(shopsDir(), Kind.SHOP.ext, Kind.SHOP, list);
        collect(questsDir(), Kind.QUEST.ext, Kind.QUEST, list);
        collect(zonesDir(), Kind.ZONE.ext, Kind.ZONE, list);
        collect(fightsDir(), Kind.FIGHT.ext, Kind.FIGHT, list);
        collect(pathsDir(), Kind.PATH.ext, Kind.PATH, list);
        return list;
    }

    // ------------------------------------------------------------- pictures

    // ------------------------------------------------------------- custom models

    /** A custom model: a folder in models/ with .geo.json, .png and .animation.json files. */
    public record ModelRef(Path dir) {
        public String name() {
            return dir.getFileName().toString();
        }
    }

    /** An entity type of the project: entities/<name>.sent. */
    public record EntityRef(Path path) {
        public String name() {
            String n = path.getFileName().toString();
            return n.endsWith(".sent") ? n.substring(0, n.length() - 5) : n;
        }
    }

    public Path entitiesDir() {
        return dir.resolve("entities");
    }

    public List<EntityRef> entities() {
        List<EntityRef> list = new ArrayList<>();
        if (!Files.isDirectory(entitiesDir())) return list;
        try (java.util.stream.Stream<Path> s = Files.list(entitiesDir())) {
            s.filter(p -> p.toString().endsWith(".sent")).sorted().forEach(p -> list.add(new EntityRef(p)));
        } catch (IOException ignored) {
        }
        return list;
    }

    /** A new entity type; with a project model name it uses that model, else a built-in townsperson. */
    public EntityRef newEntity(String name, String model) throws IOException {
        Files.createDirectories(entitiesDir());
        Path f = entitiesDir().resolve(sanitize(name) + ".sent");
        if (Files.exists(f)) throw new IOException("Такая сущность уже есть");
        org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Def d = new org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Def();
        if (model != null) {
            d.model = model;
            d.texture = "model";
        }
        Files.writeString(f, d.toJson(), java.nio.charset.StandardCharsets.UTF_8);
        return new EntityRef(f);
    }

    public Path modelsDir() {
        return dir.resolve("models");
    }

    public List<ModelRef> models() {
        List<ModelRef> list = new ArrayList<>();
        if (!Files.isDirectory(modelsDir())) return list;
        try (java.util.stream.Stream<Path> s = Files.list(modelsDir())) {
            s.filter(Files::isDirectory).sorted().forEach(p -> list.add(new ModelRef(p)));
        } catch (IOException ignored) {
        }
        return list;
    }

    public static boolean isModelFile(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".geo.json") || n.endsWith(".animation.json") || n.endsWith(".animations.json");
    }

    /**
     * Imports Blockbench exports: every .geo.json becomes a model folder named after it; textures and
     * animations go into the model whose name they start with, or into the only/last model.
     */
    public List<ModelRef> importModels(List<Path> files) throws IOException {
        List<Path> geos = new ArrayList<>(), others = new ArrayList<>();
        for (Path f : files) {
            String n = f.getFileName().toString().toLowerCase();
            if (n.endsWith(".geo.json")) geos.add(f);
            else if (isModelFile(f) || n.endsWith(".png")) others.add(f);
        }
        List<ModelRef> made = new ArrayList<>();
        java.util.Map<String, Path> dirs = new java.util.LinkedHashMap<>();
        for (Path g : geos) {
            String base = g.getFileName().toString();
            base = base.substring(0, base.length() - ".geo.json".length());
            if (base.startsWith("geometry.")) base = base.substring(9);
            Path d = modelsDir().resolve(sanitize(base));
            Files.createDirectories(d);
            // one geometry per model: a new one replaces the old
            try (java.util.stream.Stream<Path> old = Files.list(d)) {
                for (Path o : (Iterable<Path>) old::iterator) if (o.getFileName().toString().toLowerCase().endsWith(".geo.json")) Files.delete(o);
            }
            Files.copy(g, d.resolve(g.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirs.put(base.toLowerCase(), d);
            made.add(new ModelRef(d));
        }
        if (dirs.isEmpty()) {
            // textures or animations for an existing model with a matching name
            for (ModelRef m : models()) dirs.put(m.name().toLowerCase(), m.dir());
        }
        for (Path o : others) {
            String n = o.getFileName().toString().toLowerCase();
            Path target = null;
            for (java.util.Map.Entry<String, Path> e : dirs.entrySet()) if (n.startsWith(e.getKey())) target = e.getValue();
            if (target == null && geos.size() == 1) target = dirs.values().iterator().next();
            if (target == null) continue;
            if (n.endsWith(".png")) {
                // one texture per model
                try (java.util.stream.Stream<Path> old = Files.list(target)) {
                    for (Path p : (Iterable<Path>) old::iterator) if (p.getFileName().toString().toLowerCase().endsWith(".png")) Files.delete(p);
                }
            }
            Files.copy(o, target.resolve(o.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (made.stream().noneMatch(m -> m.dir().equals(o))) {
                Path t = target;
                if (made.stream().noneMatch(m -> m.dir().equals(t))) made.add(new ModelRef(t));
            }
        }
        return made;
    }

    public static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".bmp", ".gif");
    public static final List<String> SOUND_EXTENSIONS = List.of(".ogg", ".wav");

    /** A sound of the project: sounds/<name>.ogg|.wav, played from scripts by its name: sound "Колокол". */
    public record SoundRef(Path path) {
        public String name() {
            String n = path.getFileName().toString();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }
    }

    public Path soundsDir() {
        return dir.resolve("sounds");
    }

    public static boolean isSound(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        for (String ext : SOUND_EXTENSIONS) if (n.endsWith(ext)) return true;
        return false;
    }

    public List<SoundRef> sounds() {
        List<SoundRef> list = new ArrayList<>();
        if (!Files.isDirectory(soundsDir())) return list;
        try (Stream<Path> s = Files.list(soundsDir())) {
            s.filter(Project::isSound).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> list.add(new SoundRef(p)));
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to list {}", soundsDir(), e);
        }
        return list;
    }

    public List<String> soundNames() {
        List<String> out = new ArrayList<>();
        for (SoundRef r : sounds()) out.add(r.name());
        return out;
    }

    /** Copies a sound into the project; returns the new reference (renamed if the name is taken). */
    public SoundRef importSound(Path source) throws IOException {
        Files.createDirectories(soundsDir());
        String file = source.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = sanitize(dot > 0 ? file.substring(0, dot) : file), ext = dot > 0 ? file.substring(dot).toLowerCase() : ".ogg";
        Path target = soundsDir().resolve(base + ext);
        int n = 2;
        while (Files.exists(target)) target = soundsDir().resolve(base + " " + n++ + ext);
        Files.copy(source, target);
        return new SoundRef(target);
    }

    public SoundRef renameSound(SoundRef ref, String newName) throws IOException {
        String file = ref.path().getFileName().toString();
        String ext = file.substring(file.lastIndexOf('.'));
        Path target = soundsDir().resolve(sanitize(newName) + ext);
        if (Files.exists(target)) throw new IOException("Звук с таким именем уже есть");
        Files.move(ref.path(), target);
        return new SoundRef(target);
    }

    /** A picture of the project: images/<name>.<ext>, referenced from scripts by its name. */
    public record ImageRef(Path path) {
        public String name() {
            String n = path.getFileName().toString();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }
    }

    public Path imagesDir() {
        return dir.resolve("images");
    }

    public static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) if (n.endsWith(ext)) return true;
        return false;
    }

    public List<ImageRef> images() {
        List<ImageRef> list = new ArrayList<>();
        if (!Files.isDirectory(imagesDir())) return list;
        try (Stream<Path> s = Files.list(imagesDir())) {
            s.filter(Project::isImage).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> list.add(new ImageRef(p)));
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to list {}", imagesDir(), e);
        }
        return list;
    }

    public ImageRef image(String name) {
        for (ImageRef r : images()) if (r.name().equals(name)) return r;
        return null;
    }

    /** Copies a picture into the project; returns the new reference (renamed if the name is taken). */
    public ImageRef importImage(Path source) throws IOException {
        Files.createDirectories(imagesDir());
        String file = source.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = sanitize(dot > 0 ? file.substring(0, dot) : file), ext = dot > 0 ? file.substring(dot).toLowerCase() : ".png";
        Path target = imagesDir().resolve(base + ext);
        int n = 2;
        while (Files.exists(target)) {
            target = imagesDir().resolve(base + " " + n++ + ext);
        }
        Files.copy(source, target);
        return new ImageRef(target);
    }

    public ImageRef renameImage(ImageRef ref, String newName) throws IOException {
        String file = ref.path().getFileName().toString();
        String ext = file.substring(file.lastIndexOf('.'));
        Path target = imagesDir().resolve(sanitize(newName) + ext);
        if (Files.exists(target)) throw new IOException("Картинка с таким именем уже есть");
        Files.move(ref.path(), target);
        return new ImageRef(target);
    }

    /** The files of a kind, in its folder and the subfolders (sorted by path). */
    private static void collect(Path dir, String ext, Kind kind, List<FileRef> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext))
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString().toLowerCase()))
                    .forEach(p -> out.add(new FileRef(p, kind)));
        } catch (IOException e) {
            SprauteEngine.LOGGER.warn("Failed to list {}", dir, e);
        }
    }

    public String read(FileRef f) throws IOException {
        return Files.readString(f.path(), StandardCharsets.UTF_8);
    }

    public void write(FileRef f, String content) throws IOException {
        Files.createDirectories(f.path().getParent());
        Files.writeString(f.path(), content, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- creation

    /** Smallest script id >= baseId not used by any file of the project. */
    public int nextFreeId() {
        List<Integer> used = new ArrayList<>();
        for (FileRef f : files()) {
            if (f.kind() == Kind.GUI || f.kind() == Kind.CUTSCENE || f.kind() == Kind.EFFECT || f.kind() == Kind.SHOP || f.kind() == Kind.QUEST || f.kind() == Kind.ZONE || f.kind() == Kind.FIGHT
                    || f.kind() == Kind.PATH) continue;
            try {
                String text = read(f);
                if (f.kind() == Kind.SCRIPT) {
                    var r = Parser.parse(text);
                    if (r.program.header != null) used.add(r.program.header.id);
                } else {
                    VisualScript vs = VisualScript.fromJson(text);
                    VisualScript.Stack main = vs.mainStack();
                    if (main != null) used.add(Integer.parseInt(main.blocks.get(0).field("id").trim()));
                }
            } catch (Exception ignored) {
            }
        }
        int id = meta.baseId;
        while (used.contains(id)) id++;
        return id;
    }

    public static String sanitize(String name) {
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "untitled" : s;
    }

    public FileRef newScript(String name) throws IOException {
        int id = nextFreeId();
        FileRef f = new FileRef(scriptsDir().resolve(sanitize(name) + ".sst"), Kind.SCRIPT);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, ScriptTemplates.newScript(id, name));
        return f;
    }

    public FileRef newBlocks(String name) throws IOException {
        int id = nextFreeId();
        FileRef f = new FileRef(blocksDir().resolve(sanitize(name) + ".svs"), Kind.BLOCKS);
        if (Files.exists(f.path())) throw new IOException("Файл уже существует");
        write(f, VisualScript.create(id, name).toJson());
        return f;
    }

    public FileRef rename(FileRef f, String newName) throws IOException {
        String ext = f.kind().ext;
        Path target = f.path().resolveSibling(sanitize(newName) + ext);
        if (Files.exists(target)) throw new IOException("Файл с таким именем уже есть");
        Files.move(f.path(), target);
        return new FileRef(target, f.kind());
    }

    public FileRef duplicate(FileRef f) throws IOException {
        String ext = f.kind().ext;
        int n = 2;
        Path target;
        do {
            target = f.path().resolveSibling(f.name() + " (" + n++ + ")" + ext);
        } while (Files.exists(target));
        Files.copy(f.path(), target);
        return new FileRef(target, f.kind());
    }

    public void delete(FileRef f) throws IOException {
        Files.deleteIfExists(f.path());
    }
}
