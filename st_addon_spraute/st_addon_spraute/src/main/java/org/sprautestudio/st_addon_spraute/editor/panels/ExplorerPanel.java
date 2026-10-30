package org.sprautestudio.st_addon_spraute.editor.panels;

import org.sprautestudio.st_addon_spraute.editor.assets.AssetsTab;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu;
import org.sprautestudio.st_addon_spraute.ui.widgets.TreeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Project file tree. */
public class ExplorerPanel extends Widget {
    public interface Actions {
        void open(Project.FileRef f);

        void newScript();

        void newBlocks();

        void rename(Project.FileRef f);

        void duplicate(Project.FileRef f);

        void delete(Project.FileRef f);

        void deploy(Project.FileRef f, boolean start);

        void openDocs();

        void openAssets(AssetsTab.Mode mode);

        void openEngine();

        void newGui();

        void newCutscene();

        void newEffect();

        void newShop();

        void newQuest();

        void newZone();

        default void newPath() {
        }

        void newFight();

        /** Moves a file into another folder (open editors of it are saved and closed first). */
        void move(Project.FileRef f, java.nio.file.Path dir);

        void openImage(Project.ImageRef image);

        void openModel(Project.ModelRef model);

        void openEntity(Project.EntityRef entity);

        void newEntity();

        void deleteEntity(Project.EntityRef entity);

        void importModels();

        void deleteModel(Project.ModelRef model);

        void importImages();

        void renameImage(Project.ImageRef image);

        /** An empty picture to draw in the texture editor. */
        default void newImage() {
        }

        /** A Minecraft skin made into a Spraute character texture (128×64). */
        default void stSkin(Project.ImageRef image) {
        }

        void deleteImage(Project.ImageRef image);

        void openImagesFolder();

        default void importSounds() {
        }

        default void playSound(Project.SoundRef sound) {
        }

        default void renameSound(Project.SoundRef sound) {
        }

        default void deleteSound(Project.SoundRef sound) {
        }

        default void openSoundsFolder() {
        }
    }

    private record Folder(Project.Kind kind) implements TreeView.Folder {}

    /** A user's subfolder inside a kind's folder. */
    private record SubFolder(Project.Kind kind, java.nio.file.Path dir) implements TreeView.Folder {}

    /** A group of folders in the tree (it has no folder on disk). */
    private record Group(String name) implements TreeView.Folder {}

    private record AssetsFolder() implements TreeView.Folder {}

    private record ImagesFolder() implements TreeView.Folder {}

    private record SoundsFolder() implements TreeView.Folder {}

    private record EngineNode() {}

    private record ModelsFolder() implements TreeView.Folder {}

    private record EntitiesFolder() implements TreeView.Folder {}

    private final TreeView tree = new TreeView();
    private final Button addScript, addBlocks, refresh, addImage;
    private final Actions actions;
    private Project project;

    public ExplorerPanel(Actions actions) {
        this.actions = actions;
        add(tree);
        addScript = add(Button.icon(Icon.CODE, "Новый текстовый скрипт (Ctrl+N)", actions::newScript));
        addBlocks = add(Button.icon(Icon.GRAPH, "Новый блочный скрипт", actions::newBlocks));
        refresh = add(Button.icon(Icon.REFRESH, "Обновить", this::reload));
        addImage = add(Button.icon(Icon.IMAGE, "Добавить картинки (или перетащите файлы в окно)", actions::importImages));
        tree.background = Theme.PANEL;
        tree.onOpen = n -> {
            if (n.data instanceof Project.FileRef f) actions.open(f);
            if (n.data instanceof AssetsTab.Mode m) actions.openAssets(m);
            if (n.data instanceof Project.ImageRef img) actions.openImage(img);
            if (n.data instanceof Project.SoundRef snd) actions.playSound(snd);
            if (n.data instanceof EngineNode) actions.openEngine();
            if (n.data instanceof Project.ModelRef m) actions.openModel(m);
            if (n.data instanceof Project.EntityRef en) actions.openEntity(en);
        };
        tree.onNodeContext = (n, mx, my) -> {
            PopupMenu pm = new PopupMenu();
            if (n != null && (n.data instanceof ImagesFolder || n.data instanceof Project.ImageRef)) {
                if (n.data instanceof Project.ImageRef img) {
                    pm.item("Открыть", Icon.IMAGE, null, () -> actions.openImage(img));
                    pm.item("Копировать имя", Icon.COPY, null, () -> net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(img.name()));
                    pm.separator();
                    pm.item("Сделать текстурой Spraute (128×64)", Icon.USER, null, () -> actions.stSkin(img));
                    pm.item("Переименовать", Icon.EDIT, null, () -> actions.renameImage(img));
                    pm.item("Удалить", Icon.TRASH, null, () -> actions.deleteImage(img)).color(Theme.ERROR);
                    pm.separator();
                }
                pm.item("Добавить картинки…", Icon.PLUS, null, actions::importImages).color(Theme.SUCCESS);
                pm.item("Новая картинка…", Icon.PLUS, null, actions::newImage);
                pm.item("Открыть папку картинок", Icon.FOLDER, null, actions::openImagesFolder);
                pm.show(mx, my);
                return;
            }
            if (n != null && (n.data instanceof SoundsFolder || n.data instanceof Project.SoundRef)) {
                if (n.data instanceof Project.SoundRef snd) {
                    pm.item("Прослушать", Icon.PLAY, null, () -> actions.playSound(snd));
                    pm.item("Копировать команду", Icon.COPY, null,
                            () -> net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard("sound \"" + snd.name() + "\""));
                    pm.separator();
                    pm.item("Переименовать", Icon.EDIT, null, () -> actions.renameSound(snd));
                    pm.item("Удалить", Icon.TRASH, null, () -> actions.deleteSound(snd)).color(Theme.ERROR);
                    pm.separator();
                }
                pm.item("Добавить звуки…", Icon.PLUS, null, actions::importSounds).color(Theme.SUCCESS);
                pm.item("Остановить звуки", Icon.STOP, null, () -> org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.stop(""));
                pm.item("Открыть папку звуков", Icon.FOLDER, null, actions::openSoundsFolder);
                pm.show(mx, my);
                return;
            }
            if (n != null && (n.data instanceof EntitiesFolder || n.data instanceof Project.EntityRef)) {
                if (n.data instanceof Project.EntityRef en) {
                    pm.item("Открыть", Icon.USER, null, () -> actions.openEntity(en));
                    pm.item("Копировать id для персонажа", Icon.COPY, null,
                            () -> net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard("spraute:" + en.name()));
                    pm.separator();
                    pm.item("Удалить", Icon.TRASH, null, () -> actions.deleteEntity(en)).color(Theme.ERROR);
                    pm.separator();
                }
                pm.item("Новая сущность…", Icon.PLUS, null, actions::newEntity).color(Theme.SUCCESS);
                pm.show(mx, my);
                return;
            }
            if (n != null && (n.data instanceof ModelsFolder || n.data instanceof Project.ModelRef)) {
                if (n.data instanceof Project.ModelRef m) {
                    pm.item("Открыть", Icon.CUBE, null, () -> actions.openModel(m));
                    pm.item("Копировать id для персонажа", Icon.COPY, null,
                            () -> net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard("spraute:" + m.name()));
                    pm.item("Папка модели", Icon.FOLDER, null, () -> net.minecraft.Util.getPlatform().openFile(m.dir().toFile()));
                    pm.separator();
                    pm.item("Удалить", Icon.TRASH, null, () -> actions.deleteModel(m)).color(Theme.ERROR);
                    pm.separator();
                }
                pm.item("Добавить файлы модели (Blockbench)…", Icon.PLUS, null, actions::importModels).color(Theme.SUCCESS);
                pm.show(mx, my);
                return;
            }
            if (n != null && n.data instanceof AssetsTab.Mode m) {
                pm.item("Открыть", Icon.CUBE, null, () -> actions.openAssets(m));
                pm.show(mx, my);
                return;
            }
            if (n != null && (n.data instanceof Folder || n.data instanceof SubFolder)) {
                Project.Kind kind = n.data instanceof Folder fo ? fo.kind() : ((SubFolder) n.data).kind();
                java.nio.file.Path dir = n.data instanceof SubFolder sf ? sf.dir() : project.kindDir(kind);
                pm.item("Новая папка…", Icon.FOLDER, null, () -> newFolder(dir)).color(Theme.SUCCESS);
                if (n.data instanceof SubFolder sf) {
                    pm.item("Переименовать папку", Icon.EDIT, null, () -> renameFolder(sf.dir()));
                    pm.item("Удалить папку (пустую)", Icon.TRASH, null, () -> deleteFolder(sf.dir())).color(Theme.ERROR);
                }
                pm.item("Открыть в проводнике Windows", Icon.FOLDER, null, () -> {
                    try {
                        java.nio.file.Files.createDirectories(dir);
                    } catch (java.io.IOException ignored) {
                    }
                    net.minecraft.Util.getPlatform().openFile(dir.toFile());
                });
                pm.separator();
            }
            if (n != null && n.data instanceof Project.FileRef f) {
                pm.item("Открыть", Icon.FILE, null, () -> actions.open(f));
                pm.item("Загрузить в игру", Icon.UPLOAD, null, () -> actions.deploy(f, false));
                pm.item("Загрузить и запустить", Icon.PLAY, null, () -> actions.deploy(f, true)).color(Theme.SUCCESS);
                pm.separator();
                pm.item("Переименовать", Icon.EDIT, null, () -> actions.rename(f));
                pm.item("Дублировать", Icon.COPY, null, () -> actions.duplicate(f));
                pm.item("Переместить в папку…", Icon.FOLDER, null, () -> moveMenu(f, mx, my));
                pm.item("Удалить", Icon.TRASH, null, () -> actions.delete(f)).color(Theme.ERROR);
                pm.separator();
            }
            pm.item("Новый текстовый скрипт", Icon.CODE, null, actions::newScript).color(Theme.CODE_COMMAND);
            pm.item("Новый блочный скрипт", Icon.GRAPH, null, actions::newBlocks).color(Theme.WARNING);
            pm.item("Новый интерфейс (GUI)", Icon.MENU, null, actions::newGui).color(0xFF5DA9F6);
            pm.item("Новая катсцена", Icon.FILM, null, actions::newCutscene).color(0xFFFF8F5A);
            pm.item("Новый эффект", Icon.BOLT, null, actions::newEffect).color(0xFFFFC94A);
            pm.item("Новый магазин", Icon.TAG, null, actions::newShop).color(0xFF7BE08A);
            pm.item("Новый квест", Icon.FLAG, null, actions::newQuest).color(0xFFFFD25A);
            pm.item("Новая зона", Icon.PIN, null, actions::newZone).color(0xFF4FC3F7);
            pm.item("Новый путь", Icon.ARROW_RIGHT, null, actions::newPath).color(0xFFFFAA33);
            pm.item("Новый стиль боя", Icon.BOLT, null, actions::newFight).color(0xFFFF6B6B);
            pm.item("Справочник языка", Icon.BOOK, "F1", actions::openDocs);
            pm.show(mx, my);
        };
    }

    // ================================================================== folders

    private void newFolder(java.nio.file.Path parent) {
        org.sprautestudio.st_addon_spraute.ui.widgets.Dialog.prompt("Новая папка", "Имя папки", "новая папка", v -> {
            if (v.isBlank()) return "Введите имя";
            if (v.matches(".*[\\\\/:*?\"<>|].*")) return "Без символов \\ / : * ? \" < > |";
            return java.nio.file.Files.exists(parent.resolve(v.trim())) ? "Такая папка уже есть" : null;
        }, v -> {
            try {
                java.nio.file.Files.createDirectories(parent.resolve(v.trim()));
                expandedKeys.add("dir:" + parent.resolve(v.trim()));
                expandedKeys.add("dir:" + parent);
            } catch (java.io.IOException ignored) {
            }
            reload();
        });
    }

    private void renameFolder(java.nio.file.Path dir) {
        org.sprautestudio.st_addon_spraute.ui.widgets.Dialog.prompt("Переименовать папку", "Новое имя", dir.getFileName().toString(), v -> {
            if (v.isBlank()) return "Введите имя";
            if (v.matches(".*[\\\\/:*?\"<>|].*")) return "Без символов \\ / : * ? \" < > |";
            return java.nio.file.Files.exists(dir.resolveSibling(v.trim())) ? "Такая папка уже есть" : null;
        }, v -> {
            try {
                java.nio.file.Files.move(dir, dir.resolveSibling(v.trim()));
            } catch (java.io.IOException ignored) {
            }
            reload();
        });
    }

    private void deleteFolder(java.nio.file.Path dir) {
        try (var s = java.nio.file.Files.list(dir)) {
            if (s.findAny().isPresent()) {
                org.sprautestudio.st_addon_spraute.ui.widgets.Dialog.confirm("Папка не пуста", "Сначала перенесите или удалите файлы из «"
                        + dir.getFileName() + "».", "Понятно", false, () -> {
                });
                return;
            }
            java.nio.file.Files.delete(dir);
        } catch (java.io.IOException ignored) {
        }
        reload();
    }

    /** Where a file can go: its kind's folder and every subfolder. */
    private void moveMenu(Project.FileRef f, int mx, int my) {
        PopupMenu pm = new PopupMenu();
        java.nio.file.Path root = project.kindDir(f.kind());
        java.nio.file.Path cur = f.path().getParent();
        pm.item("(корень) " + root.getFileName(), Icon.FOLDER, null, () -> actions.move(f, root)).checked(() -> cur.equals(root));
        for (java.nio.file.Path d : project.subfolders(f.kind())) {
            String label = root.relativize(d).toString().replace('\\', '/');
            pm.item(label, Icon.FOLDER, null, () -> actions.move(f, d)).checked(() -> cur.equals(d));
        }
        pm.separator();
        pm.item("Новая папка…", Icon.PLUS, null, () -> newFolder(cur));
        pm.show(mx, my);
    }

    /** Which folders were opened or closed by hand (kept across reloads). */
    private final java.util.Set<String> expandedKeys = new java.util.HashSet<>(), collapsedKeys = new java.util.HashSet<>();

    private static String key(Object data) {
        if (data instanceof Folder f) return "kind:" + f.kind();
        if (data instanceof SubFolder s) return "dir:" + s.dir();
        if (data instanceof Group g) return "grp:" + g.name();
        if (data instanceof AssetsFolder) return "asset";
        return data == null ? "" : data.getClass().getSimpleName();
    }

    private void remember(List<TreeView.Node> nodes) {
        for (TreeView.Node n : nodes) {
            if (!(n.data instanceof TreeView.Folder)) continue;
            String k = key(n.data);
            if (n.expanded) {
                expandedKeys.add(k);
                collapsedKeys.remove(k);
            } else {
                collapsedKeys.add(k);
                expandedKeys.remove(k);
            }
            remember(n.children);
        }
    }

    private void restore(List<TreeView.Node> nodes) {
        for (TreeView.Node n : nodes) {
            if (!(n.data instanceof TreeView.Folder)) continue;
            String k = key(n.data);
            if (expandedKeys.contains(k)) n.expanded = true;
            else if (collapsedKeys.contains(k)) n.expanded = false;
            // never touched: groups open, kind folders open when they have files, the rest closed
            else n.expanded = false;
            restore(n.children);
        }
    }

    private static int countFiles(TreeView.Node n) {
        int c = 0;
        for (TreeView.Node ch : n.children) c += ch.data instanceof Project.FileRef ? 1 : countFiles(ch);
        return c;
    }

    /** The node of a subfolder (made on the way), under a kind's folder node. */
    private static TreeView.Node folderNode(Map<java.nio.file.Path, TreeView.Node> made, TreeView.Node kindNode, java.nio.file.Path root,
                                            java.nio.file.Path dir, Project.Kind kind, int color) {
        if (dir == null || dir.equals(root)) return kindNode;
        TreeView.Node n = made.get(dir);
        if (n != null) return n;
        TreeView.Node parent = folderNode(made, kindNode, root, dir.getParent(), kind, color);
        n = new TreeView.Node(dir.getFileName().toString(), Icon.FOLDER, new SubFolder(kind, dir));
        n.iconColor = color;
        parent.add(n);
        made.put(dir, n);
        return n;
    }

    public void setProject(Project p) {
        this.project = p;
        reload();
    }

    public void reload() {
        if (project == null) {
            tree.setRoots(List.of());
            return;
        }
        List<TreeView.Node> roots = new ArrayList<>();
        TreeView.Node scripts = new TreeView.Node("Текстовые скрипты", Icon.FOLDER, new Folder(Project.Kind.SCRIPT));
        scripts.iconColor = Theme.CODE_COMMAND;
        TreeView.Node blocks = new TreeView.Node("Блочные скрипты", Icon.FOLDER, new Folder(Project.Kind.BLOCKS));
        blocks.iconColor = Theme.WARNING;
        TreeView.Node guis = new TreeView.Node("Интерфейсы", Icon.FOLDER, new Folder(Project.Kind.GUI));
        guis.iconColor = 0xFF5DA9F6;
        TreeView.Node cutscenes = new TreeView.Node("Катсцены", Icon.FOLDER, new Folder(Project.Kind.CUTSCENE));
        cutscenes.iconColor = 0xFFFF8F5A;
        TreeView.Node effects = new TreeView.Node("Эффекты", Icon.FOLDER, new Folder(Project.Kind.EFFECT));
        effects.iconColor = 0xFFFFC94A;
        TreeView.Node shops = new TreeView.Node("Магазины", Icon.FOLDER, new Folder(Project.Kind.SHOP));
        shops.iconColor = 0xFF7BE08A;
        TreeView.Node quests = new TreeView.Node("Квесты", Icon.FOLDER, new Folder(Project.Kind.QUEST));
        quests.iconColor = 0xFFFFD25A;
        TreeView.Node zones = new TreeView.Node("Зоны", Icon.FOLDER, new Folder(Project.Kind.ZONE));
        zones.iconColor = 0xFF4FC3F7;
        TreeView.Node fights = new TreeView.Node("Стили боя", Icon.FOLDER, new Folder(Project.Kind.FIGHT));
        fights.iconColor = 0xFFFF6B6B;
        TreeView.Node paths = new TreeView.Node("Пути", Icon.FOLDER, new Folder(Project.Kind.PATH));
        paths.iconColor = 0xFFFFAA33;
        TreeView.Node images = new TreeView.Node("Картинки", Icon.FOLDER, new ImagesFolder());
        images.iconColor = Theme.INFO;
        for (Project.ImageRef img : project.images()) {
            TreeView.Node n = new TreeView.Node(img.name(), Icon.IMAGE, img);
            n.iconColor = Theme.INFO;
            images.add(n);
        }
        images.badge = String.valueOf(images.children.size());
        TreeView.Node ownSounds = new TreeView.Node("Свои звуки", Icon.FOLDER, new SoundsFolder());
        ownSounds.iconColor = 0xFFB18CFF;
        for (Project.SoundRef snd : project.sounds()) {
            TreeView.Node n = new TreeView.Node(snd.name(), Icon.NOTE, snd);
            n.iconColor = 0xFFB18CFF;
            ownSounds.add(n);
        }
        ownSounds.badge = String.valueOf(ownSounds.children.size());
        TreeView.Node entities = new TreeView.Node("Сущности", Icon.FOLDER, new EntitiesFolder());
        entities.iconColor = Theme.CODE_CHARACTER;
        for (Project.EntityRef er : project.entities()) {
            TreeView.Node n = new TreeView.Node(er.name(), Icon.USER, er);
            n.iconColor = Theme.CODE_CHARACTER;
            entities.add(n);
        }
        entities.badge = String.valueOf(entities.children.size());
        TreeView.Node customModels = new TreeView.Node("Мои модели (Blockbench)", Icon.FOLDER, new ModelsFolder());
        customModels.iconColor = Theme.ACCENT_2;
        for (Project.ModelRef mr : project.models()) {
            TreeView.Node n = new TreeView.Node(mr.name(), Icon.CUBE, mr);
            n.iconColor = Theme.ACCENT_2;
            customModels.add(n);
        }
        customModels.badge = String.valueOf(customModels.children.size());
        TreeView.Node assets = new TreeView.Node("Справочник игры", Icon.FOLDER, new AssetsFolder());
        assets.iconColor = 0xFFFF8F5A;
        remember(tree.roots());
        TreeView.Node models = new TreeView.Node("Модели", Icon.CUBE, AssetsTab.Mode.MODELS);
        models.iconColor = Theme.ACCENT_2;
        TreeView.Node anims = new TreeView.Node("Анимации", Icon.FILM, AssetsTab.Mode.ANIMATIONS);
        anims.iconColor = 0xFFFF8F5A;
        TreeView.Node sounds = new TreeView.Node("Звуки", Icon.NOTE, AssetsTab.Mode.SOUNDS);
        sounds.iconColor = Theme.INFO;
        assets.add(models);
        assets.add(anims);
        assets.add(sounds);
        // the user's subfolders (also the empty ones), under their kind's folder
        Map<java.nio.file.Path, TreeView.Node> made = new java.util.HashMap<>();
        java.util.Map<Project.Kind, TreeView.Node> byKind = new java.util.EnumMap<>(Project.Kind.class);
        byKind.put(Project.Kind.SCRIPT, scripts);
        byKind.put(Project.Kind.BLOCKS, blocks);
        byKind.put(Project.Kind.GUI, guis);
        byKind.put(Project.Kind.CUTSCENE, cutscenes);
        byKind.put(Project.Kind.EFFECT, effects);
        byKind.put(Project.Kind.SHOP, shops);
        byKind.put(Project.Kind.QUEST, quests);
        byKind.put(Project.Kind.ZONE, zones);
        byKind.put(Project.Kind.FIGHT, fights);
        byKind.put(Project.Kind.PATH, paths);
        for (var en : byKind.entrySet())
            for (java.nio.file.Path d : project.subfolders(en.getKey()))
                folderNode(made, en.getValue(), project.kindDir(en.getKey()), d, en.getKey(), en.getValue().iconColor);
        for (Project.FileRef f : project.files()) {
            Icon icon = switch (f.kind()) {
                case SCRIPT -> Icon.CODE;
                case BLOCKS -> Icon.GRAPH;
                case GUI -> Icon.MENU;
                case CUTSCENE -> Icon.FILM;
                case EFFECT -> Icon.BOLT;
                case SHOP -> Icon.TAG;
                case QUEST -> Icon.FLAG;
                case ZONE -> Icon.PIN;
                case FIGHT -> Icon.BOLT;
                case PATH -> Icon.ARROW_RIGHT;
            };
            TreeView.Node n = new TreeView.Node(f.fileName(), icon, f);
            n.iconColor = switch (f.kind()) {
                case SCRIPT -> Theme.CODE_COMMAND;
                case BLOCKS -> Theme.WARNING;
                case GUI -> 0xFF5DA9F6;
                case CUTSCENE -> 0xFFFF8F5A;
                case EFFECT -> 0xFFFFC94A;
                case SHOP -> 0xFF7BE08A;
                case QUEST -> 0xFFFFD25A;
                case ZONE -> 0xFF4FC3F7;
                case FIGHT -> 0xFFFF6B6B;
                case PATH -> 0xFFFFAA33;
            };
            TreeView.Node kindNode = byKind.get(f.kind());
            folderNode(made, kindNode, project.kindDir(f.kind()), f.path().getParent(), f.kind(), kindNode.iconColor).add(n);
        }
        for (TreeView.Node k : byKind.values()) k.badge = String.valueOf(countFiles(k));
        for (TreeView.Node sub : made.values()) sub.badge = String.valueOf(countFiles(sub));
        // groups: what the story is, its world, its systems, the look, the characters and pictures
        TreeView.Node story = group("Сюжеты", Icon.CODE, Theme.CODE_COMMAND, scripts, blocks);
        TreeView.Node world = group("Мир и постановка", Icon.FILM, 0xFFFF8F5A, cutscenes, zones, paths, effects);
        TreeView.Node systems = group("Игровые системы", Icon.FLAG, 0xFFFFD25A, quests, shops, fights);
        TreeView.Node look = group("Интерфейс", Icon.MENU, 0xFF5DA9F6, guis);
        // the model files are raw material of entity types: they live inside "Entities"
        entities.add(customModels);
        TreeView.Node res = group("Персонажи и ресурсы", Icon.USER, Theme.CODE_CHARACTER, entities, images, ownSounds);
        roots.add(story);
        roots.add(world);
        roots.add(systems);
        roots.add(look);
        roots.add(res);
        roots.add(assets);
        TreeView.Node engine = new TreeView.Node("Настройки движка", Icon.GEAR, new EngineNode());
        engine.iconColor = Theme.ACCENT;
        roots.add(engine);
        restore(roots);
        tree.setRoots(roots);
    }

    private static TreeView.Node group(String name, Icon icon, int color, TreeView.Node... folders) {
        TreeView.Node g = new TreeView.Node(name, icon, new Group(name));
        g.iconColor = color;
        int n = 0;
        for (TreeView.Node f : folders) {
            g.add(f);
            n += f.data instanceof Folder ? countFiles(f) : f.children.size();
        }
        g.badge = String.valueOf(n);
        return g;
    }

    @Override
    public void layout() {
        refresh.bounds(x + w - 22, y + 3, 18, 18);
        addBlocks.bounds(x + w - 42, y + 3, 18, 18);
        addScript.bounds(x + w - 62, y + 3, 18, 18);
        addImage.bounds(x + w - 82, y + 3, 18, 18);
        tree.bounds(x, y + 24, w, h - 24);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.PANEL);
        String title = project == null ? "ПРОВОДНИК" : project.name().toUpperCase();
        c.textEllipsized(title, x + 8, y + 8, w - 92, Theme.TEXT_DIM);
        c.fill(x, y + 23, w, 1, Theme.BORDER);
    }
}
