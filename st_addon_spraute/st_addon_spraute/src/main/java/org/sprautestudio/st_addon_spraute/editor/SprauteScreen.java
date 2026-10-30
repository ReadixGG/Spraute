package org.sprautestudio.st_addon_spraute.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.assets.AssetsTab;
import org.sprautestudio.st_addon_spraute.editor.panels.ConsolePanel;
import org.sprautestudio.st_addon_spraute.editor.panels.ExplorerPanel;
import org.sprautestudio.st_addon_spraute.editor.panels.ProblemsPanel;
import org.sprautestudio.st_addon_spraute.editor.panels.SidePanel;
import org.sprautestudio.st_addon_spraute.editor.panels.StoryPanel;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.project.ScriptTemplates;
import org.sprautestudio.st_addon_spraute.editor.project.Workspace;
import org.sprautestudio.st_addon_spraute.editor.tabs.BlocksTab;
import org.sprautestudio.st_addon_spraute.editor.tabs.DocsTab;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.editor.tabs.ScriptTab;
import org.sprautestudio.st_addon_spraute.editor.visual.VisualScript;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.ui.Box;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.code.LanguageSupport;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dialog;
import org.sprautestudio.st_addon_spraute.ui.widgets.MenuBar;
import org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu;
import org.sprautestudio.st_addon_spraute.ui.widgets.SplitPane;
import org.sprautestudio.st_addon_spraute.ui.widgets.TabBar;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The Spraute IDE window. */
public final class SprauteScreen extends UI {
    private static SprauteScreen instance;

    public static void open() {
        org.sprautestudio.st_addon_spraute.editor.engine.GameShot.capture();
        if (instance == null) instance = new SprauteScreen();
        Minecraft.getInstance().setScreen(instance);
    }

    /** The project open in the editor, or "" (for the assistant). */
    public static String currentProjectName() {
        return instance != null && instance.project != null ? instance.project.name() : "";
    }

    /** A file was written by someone else (the assistant): show the new text where nothing is unsaved. */
    public static void fileChangedOutside(java.nio.file.Path p) {
        SprauteScreen s = instance;
        if (s == null || s.project == null) return;
        try {
            s.explorer.reload();
        } catch (Throwable ignored) {
        }
        for (EditorTab t : new ArrayList<>(s.tabs)) {
            if (t.file == null || !t.file.path().toAbsolutePath().normalize().equals(p.toAbsolutePath().normalize()) || t.isDirty()) continue;
            Project.FileRef f = t.file;
            s.removeTab(t);
            if (Files.exists(p)) s.openFile(f);
        }
    }

    /** Opens a project's file in the editor (for the assistant). */
    public static String openForAssistant(java.nio.file.Path projectDir, java.nio.file.Path file) {
        open();
        SprauteScreen s = instance;
        if (s.project == null || !s.project.dir.toAbsolutePath().normalize().equals(projectDir.toAbsolutePath().normalize())) s.openProject(projectDir);
        if (file == null) return "Открыт проект";
        String rel = projectDir.relativize(file).toString().replace('\\', '/');
        Project.Kind k = Project.Kind.ofFile(rel);
        if (!rel.endsWith(k.ext)) return "Этот файл не открывается в редакторе";
        s.openFile(new Project.FileRef(file, k));
        return "Открыто: " + rel;
    }

    private static final int TOP = 26, STATUS = 16;

    private Project project;
    private final List<EditorTab> tabs = new ArrayList<>();

    // chrome
    private final TopBar topBar = new TopBar();
    private final MenuBar menu = new MenuBar();
    private final Box runBox = Box.row().gap(3);
    private final StatusBar statusBar = new StatusBar();

    // workbench
    private final ExplorerPanel explorer;
    private final Widget editorArea = new EditorArea();
    private final TabBar editorTabs = new TabBar();
    private final SidePanel side;
    private final Widget bottom = new BottomArea();
    private final TabBar bottomTabs = new TabBar();
    private final ProblemsPanel problems;
    private final ConsolePanel console = new ConsolePanel();
    private final StoryPanel stories = new StoryPanel();
    private final SplitPane centerSplit, verticalSplit, mainSplit;
    private final WelcomePage welcome;
    private final TabBar.Tab problemsTab, consoleTab, storiesTab;
    private int tickCounter;
    private int imageCount = -1;

    private SprauteScreen() {
        super(Component.literal("Spraute"));
        Workspace.Settings s = Workspace.settings();
        setUiScale(s.physicalScale());

        explorer = new ExplorerPanel(new ExplorerPanel.Actions() {
            @Override
            public void open(Project.FileRef f) {
                openFile(f);
            }

            @Override
            public void newScript() {
                createScript(false);
            }

            @Override
            public void newBlocks() {
                createScript(true);
            }

            @Override
            public void rename(Project.FileRef f) {
                renameFile(f);
            }

            @Override
            public void duplicate(Project.FileRef f) {
                try {
                    project.duplicate(f);
                    explorer.reload();
                } catch (IOException e) {
                    toastError(e.getMessage());
                }
            }

            @Override
            public void delete(Project.FileRef f) {
                deleteFile(f);
            }

            @Override
            public void deploy(Project.FileRef f, boolean start) {
                EditorTab t = openFile(f);
                if (t != null) SprauteScreen.this.deploy(t, start);
            }

            @Override
            public void openDocs() {
                SprauteScreen.this.openDocs();
            }

            @Override
            public void openAssets(AssetsTab.Mode mode) {
                SprauteScreen.this.openAssets(mode);
            }

            @Override
            public void openModel(Project.ModelRef model) {
                SprauteScreen.this.openModel(model);
            }

            @Override
            public void openEntity(Project.EntityRef entity) {
                SprauteScreen.this.openEntity(entity);
            }

            @Override
            public void newEntity() {
                SprauteScreen.this.createEntity(null);
            }

            @Override
            public void deleteEntity(Project.EntityRef entity) {
                Dialog.confirm("Удалить сущность", "Сущность «" + entity.name() + "» будет удалена.", "Удалить", true, () -> {
                    for (EditorTab t : new ArrayList<>(tabs)) {
                        if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.EntityTab et && et.ref.path().equals(entity.path())) removeTab(t);
                    }
                    try {
                        Files.deleteIfExists(entity.path());
                    } catch (IOException e) {
                        toastError(e.getMessage());
                    }
                    org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
                    explorer.reload();
                });
            }

            @Override
            public void importModels() {
                SprauteScreen.this.importModelsDialog();
            }

            @Override
            public void deleteModel(Project.ModelRef model) {
                Dialog.confirm("Удалить модель", "Папка модели «" + model.name() + "» будет удалена.", "Удалить", true, () -> {
                    for (EditorTab t : new ArrayList<>(tabs)) {
                        if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.ModelTab mt && mt.model.dir().equals(model.dir())) removeTab(t);
                    }
                    try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(model.dir())) {
                        for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
                    } catch (IOException e) {
                        toastError(e.getMessage());
                    }
                    org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
                    explorer.reload();
                });
            }

            @Override
            public void openEngine() {
                SprauteScreen.this.openEngine();
            }

            @Override
            public void newGui() {
                createGui();
            }

            @Override
            public void newCutscene() {
                createCutscene();
            }

            @Override
            public void newEffect() {
                createEffect();
            }

            @Override
            public void newShop() {
                createShop();
            }

            @Override
            public void newQuest() {
                createQuest();
            }

            @Override
            public void newZone() {
                createZone();
            }

            @Override
            public void newPath() {
                createPath();
            }

            @Override
            public void move(Project.FileRef f, java.nio.file.Path dir) {
                if (f.path().getParent().equals(dir)) return;
                for (EditorTab t : new ArrayList<>(tabs)) if (t.file != null && t.file.path().equals(f.path())) {
                    if (t.isDirty()) save(t);
                    removeTab(t);
                }
                for (GuiWindow gw : new ArrayList<>(guiWindows)) {
                    if (gw.tab().file != null && gw.tab().file.path().equals(f.path())) {
                        if (gw.tab().isDirty()) save(gw.tab());
                        guiWindows.remove(gw);
                        closeOverlay(gw.window());
                    }
                }
                try {
                    project.moveTo(f, dir);
                    toastSuccess("Перемещено: " + f.fileName());
                } catch (IOException e) {
                    toastError(e.getMessage());
                }
                explorer.reload();
            }

            @Override
            public void newFight() {
                createFight();
            }

            @Override
            public void openImage(Project.ImageRef image) {
                SprauteScreen.this.openImage(image);
            }

            @Override
            public void importImages() {
                SprauteScreen.this.importImagesDialog();
            }

            @Override
            public void renameImage(Project.ImageRef image) {
                SprauteScreen.this.renameImage(image);
            }

            @Override
            public void newImage() {
                SprauteScreen.this.newImageDialog();
            }

            @Override
            public void stSkin(Project.ImageRef image) {
                org.sprautestudio.st_addon_spraute.editor.assets.FaceSkinDialog.open(project, image.path(), image.name(), made -> {
                    explorer.reload();
                    openImage(made);
                });
            }

            @Override
            public void deleteImage(Project.ImageRef image) {
                SprauteScreen.this.deleteImage(image);
            }

            @Override
            public void importSounds() {
                if (project == null) return;
                pickFiles("Добавить звуки в проект", new String[]{"*.ogg", "*.wav", "*.mp3", "*.m4a", "*.aac", "*.wma", "*.flac"},
                        "Звуки (OGG, WAV, MP3, M4A, AAC, WMA, FLAC)", SprauteScreen.this::importSounds);
            }

            @Override
            public void playSound(Project.SoundRef sound) {
                org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.stop("__editor_preview");
                org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.play(project.name(), sound.name(), 0, 0, 0, false, 1, 1, false, "__editor_preview");
            }

            @Override
            public void renameSound(Project.SoundRef sound) {
                Dialog.prompt("Переименовать звук", "Новое имя (используется в скриптах):", sound.name(), v -> v.isBlank() ? "Введите имя" : null, v -> {
                    try {
                        project.renameSound(sound, v);
                        explorer.reload();
                        toastInfo("Не забудьте обновить sound \"" + sound.name() + "\" в скриптах");
                    } catch (IOException e) {
                        toastError(e.getMessage());
                    }
                });
            }

            @Override
            public void deleteSound(Project.SoundRef sound) {
                Dialog.confirm("Удалить звук", "Звук «" + sound.name() + "» будет удалён из проекта.", "Удалить", true, () -> {
                    try {
                        Files.deleteIfExists(sound.path());
                    } catch (IOException e) {
                        toastError(e.getMessage());
                    }
                    explorer.reload();
                });
            }

            @Override
            public void openSoundsFolder() {
                if (project == null) return;
                try {
                    Files.createDirectories(project.soundsDir());
                } catch (IOException ignored) {
                }
                net.minecraft.Util.getPlatform().openFile(project.soundsDir().toFile());
            }

            @Override
            public void openImagesFolder() {
                if (project == null) return;
                try {
                    Files.createDirectories(project.imagesDir());
                } catch (IOException ignored) {
                }
                net.minecraft.Util.getPlatform().openFile(project.imagesDir().toFile());
            }
        });
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectImages =
                () -> org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.names(project);
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectSounds = () -> project != null ? project.soundNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectGuis = this::projectGuis;
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectCutscenes = () -> project != null ? project.cutsceneNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectEffects = () -> project != null ? project.effectNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectShops = () -> project != null ? project.shopNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectQuests = () -> project != null ? project.questNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectZones = () -> project != null ? project.zoneNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectPaths = () -> project != null ? project.pathNames() : List.of();
        org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectFights = () -> project != null ? project.fightNames() : List.of();
        org.sprautestudio.st_addon_spraute.zone.client.ZoneClient.projectZones = () -> project != null ? project.zoneSources() : java.util.Map.of();
        side = new SidePanel(this::activeTab);
        problems = new ProblemsPanel(this::collectProblems);

        // editor tabs
        editorTabs.onSelect = t -> showTab((EditorTab) t.data);
        editorTabs.onClose = t -> closeTab((EditorTab) t.data);
        editorTabs.onContext = (t, mx, my) -> {
            EditorTab tab = (EditorTab) t.data;
            PopupMenu pm = new PopupMenu();
            pm.item("Закрыть", Icon.CLOSE, "Ctrl+W", () -> closeTab(tab));
            pm.item("Закрыть другие", Icon.CLOSE, null, () -> {
                for (EditorTab other : new ArrayList<>(tabs)) if (other != tab) closeTab(other);
            });
            pm.item("Закрыть все", Icon.CLOSE, null, () -> {
                for (EditorTab other : new ArrayList<>(tabs)) closeTab(other);
            });
            if (tab.source() != null) {
                pm.separator();
                pm.item("Загрузить и запустить", Icon.PLAY, "F5", () -> deploy(tab, true)).color(Theme.SUCCESS);
            }
            pm.show(mx, my);
        };
        editorArea.add(editorTabs);

        // bottom panel
        bottomTabs.editorStyle = false;
        problemsTab = bottomTabs.add(new TabBar.Tab(() -> "Проблемы", Icon.WARNING, problems));
        problemsTab.badge = () -> {
            int n = 0;
            for (EditorTab.Problem p : collectProblems()) if (p.severity() == LanguageSupport.Severity.ERROR) n++;
            return n == 0 ? "" : String.valueOf(n);
        };
        problemsTab.badgeColor = Theme.ERROR;
        consoleTab = bottomTabs.add(new TabBar.Tab(() -> "Консоль", Icon.TERMINAL, console));
        storiesTab = bottomTabs.add(new TabBar.Tab(() -> "Сюжеты", Icon.PLAY, stories));
        storiesTab.badge = () -> {
            int n = 0;
            for (Packets.ScriptEntry e : ClientState.get().status().scripts()) if (e.active) n++;
            return n == 0 ? "" : n + " ▶";
        };
        storiesTab.badgeColor = Theme.SUCCESS;
        bottomTabs.onSelect = t -> bottom.layout();
        bottom.add(bottomTabs);
        bottom.add(problems);
        bottom.add(console);
        bottom.add(stories);
        bottomTabs.select(storiesTab);
        stories.showConsole = () -> bottomTabs.select(consoleTab);
        stories.openInEditor = this::openScriptById;

        centerSplit = new SplitPane(true, editorArea, side, false, s.rightW);
        centerSplit.minSize = 140;
        verticalSplit = new SplitPane(false, centerSplit, bottom, false, s.bottomH);
        verticalSplit.minSize = 80;
        mainSplit = new SplitPane(true, explorer, verticalSplit, true, s.sidebarW);
        mainSplit.minSize = 120;
        centerSplit.onResize = v -> Workspace.settings().rightW = v;
        verticalSplit.onResize = v -> Workspace.settings().bottomH = v;
        mainSplit.onResize = v -> Workspace.settings().sidebarW = v;
        explorer.visible = s.showSidebar;
        side.visible = s.showRight;
        bottom.visible = s.showBottom;

        welcome = new WelcomePage(this::newProjectDialog, this::createDemo, this::openDocsWithoutProject, info -> openProject(info.dir()),
                info -> Dialog.confirm("Удалить проект", "Проект «" + info.name() + "» и все его файлы будут удалены безвозвратно.", "Удалить", true, () -> {
                    try {
                        Workspace.deleteProject(info.dir());
                        SprauteScreen.this.welcome.reload();
                    } catch (IOException e) {
                        toastError(e.getMessage());
                    }
                }));

        buildMenu();
        buildRunBox();
        root.add(topBar);
        topBar.add(menu);
        topBar.add(runBox);
        root.add(statusBar);
        root.add(welcome);
        root.add(mainSplit);
        mainSplit.visible = false;

        ClientState.get().deployListener = r -> {
            if (r.ok()) toastSuccess(r.messages().isEmpty() ? "Сюжет загружен" : r.messages().get(0));
            else {
                toastError("Не удалось загрузить сюжет #" + r.id());
                bottomTabs.select(consoleTab);
                showBottom(true);
            }
        };

        if (s.lastProject != null) {
            java.nio.file.Path dir = Workspace.projectsDir().resolve(s.lastProject);
            if (Files.isDirectory(dir)) openProject(dir);
        }
    }

    // =================================================================== layout

    private final class TopBar extends Widget {
        @Override
        public void layout() {
            int logoW = 74;
            menu.bounds(x + logoW, y, menu.preferredWidth(), h);
            int rw = 0;
            for (Widget c : runBox.children()) rw += Math.max(0, c.prefW) + runBox.gap;
            runBox.bounds(x + w - rw - 4, y + 4, rw, h - 8);
            runBox.layout();
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.BG_DEEP);
            c.fill(x, y + h - 1, w, 1, Theme.BORDER);
            c.round(x + 8, y + 7, 12, 12, 3, Theme.ACCENT);
            c.round(x + 12, y + 11, 4, 4, 2, Theme.ACCENT_2);
            c.text("Spraute", x + 25, y + 9, Theme.TEXT);
            if (project != null) {
                String name = project.name();
                EditorTab t = activeTab();
                String title = t != null ? name + " — " + t.title() : name;
                int tw = c.width(title);
                int cx = x + (w - tw) / 2;
                if (cx > menu.x + menu.w + 10 && cx + tw < runBox.x - 10) c.text(title, cx, y + 9, Theme.TEXT_DIM);
            }
        }
    }

    private final class EditorArea extends Widget {
        @Override
        public void layout() {
            editorTabs.bounds(x, y, w, 24);
            EditorTab t = activeTab();
            for (EditorTab tab : tabs) tab.visible = tab == t;
            if (t != null) {
                t.bounds(x, y + 24, w, h - 24);
                t.layout();
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.CODE_BG);
            if (tabs.isEmpty()) {
                int cy = y + h / 2 - 40;
                c.textCentered("Откройте скрипт в проводнике или создайте новый", x + w / 2, cy, Theme.TEXT_DIM);
                String[] hints = {"Ctrl+N — новый текстовый скрипт", "Ctrl+Shift+N — новый блочный скрипт",
                        "F5 — загрузить и запустить", "F1 — справочник языка"};
                for (int i = 0; i < hints.length; i++) c.textCentered(hints[i], x + w / 2, cy + 20 + i * 12, Theme.TEXT_MUTED);
            }
        }
    }

    private final class BottomArea extends Widget {
        @Override
        public void layout() {
            bottomTabs.bounds(x, y, w, 22);
            TabBar.Tab sel = bottomTabs.selected();
            for (Widget p : List.of(problems, console, stories)) {
                p.visible = sel != null && sel.data == p;
                p.bounds(x, y + 22, w, h - 22);
                p.layout();
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
        }
    }

    private final class StatusBar extends Widget {
        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.ACCENT_DIM);
            c.fill(x, y, w, 1, Theme.BORDER);
            int tx = x + 8;
            boolean connected = Minecraft.getInstance().getConnection() != null;
            c.round(tx, y + 5, 6, 6, 3, connected && ClientState.get().status().ready() ? Theme.SUCCESS : Theme.WARNING);
            String conn = !connected ? "Нет мира" : ClientState.get().status().ready() ? "Движок готов" : "Spraute недоступен";
            c.text(conn, tx + 10, y + 4, Theme.TEXT);
            tx += c.width(conn) + 22;
            int errors = 0, warnings = 0;
            for (EditorTab.Problem p : collectProblems()) {
                if (p.severity() == LanguageSupport.Severity.ERROR) errors++;
                else if (p.severity() == LanguageSupport.Severity.WARNING) warnings++;
            }
            c.icon(Icon.ERROR, tx, y + 4, errors > 0 ? Theme.ERROR : Theme.TEXT_DIM);
            c.text(String.valueOf(errors), tx + 12, y + 4, Theme.TEXT);
            tx += 12 + c.width(String.valueOf(errors)) + 8;
            c.icon(Icon.WARNING, tx, y + 4, warnings > 0 ? Theme.WARNING : Theme.TEXT_DIM);
            c.text(String.valueOf(warnings), tx + 12, y + 4, Theme.TEXT);

            EditorTab t = activeTab();
            StringBuilder right = new StringBuilder();
            if (t instanceof ScriptTab st) {
                right.append("Стр ").append(st.editor.caretLine() + 1).append(", кол ").append(st.editor.caretCol() + 1);
                if (st.editor.selectionLength() > 0) right.append(" (выделено ").append(st.editor.selectionLength()).append(")");
                right.append("   SST");
            } else if (t instanceof BlocksTab) {
                right.append("Блоки");
            }
            if (t != null && t.scriptId() > 0) {
                Packets.ScriptEntry e = ClientState.get().script(t.scriptId());
                right.append("   #").append(t.scriptId()).append(e == null ? " не загружен" : e.active ? (e.paused ? " на паузе" : " запущен") : " загружен");
            }
            String r = right.toString();
            c.text(r, x + w - c.width(r) - 8, y + 4, Theme.TEXT);
        }
    }

    @Override
    protected void init() {
        super.init();
        root.layout();
    }

    private void layoutRoot() {
        topBar.bounds(0, 0, vw, TOP);
        statusBar.bounds(0, vh - STATUS, vw, STATUS);
        welcome.visible = project == null;
        mainSplit.visible = project != null;
        welcome.bounds(0, TOP, vw, vh - TOP - STATUS);
        mainSplit.bounds(0, TOP, vw, vh - TOP - STATUS);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    {
        // root layout hook: lay out chrome first, then the children
        root.add(new Widget() {
            @Override
            public void layout() {
                layoutRoot();
            }
        });
    }

    // ============================================================ menus

    private void buildMenu() {
        menu.menu("Файл", pm -> {
            pm.item("Новый проект…", Icon.PLUS, null, this::newProjectDialog);
            pm.item("Открыть проект…", Icon.FOLDER, null, this::closeProject);
            pm.separator();
            pm.item("Новый текстовый скрипт", Icon.CODE, "Ctrl+N", () -> createScript(false)).enabled(() -> project != null);
            pm.item("Новый блочный скрипт", Icon.GRAPH, "Ctrl+Shift+N", () -> createScript(true)).enabled(() -> project != null);
            pm.item("Новый интерфейс (GUI)", Icon.MENU, null, this::createGui).enabled(() -> project != null);
            pm.separator();
            pm.item("Сохранить", Icon.SAVE, "Ctrl+S", this::saveActive).enabled(() -> activeTab() != null);
            pm.item("Сохранить всё", Icon.SAVE, "Ctrl+Shift+S", this::saveAll).enabled(() -> project != null);
            pm.item("Закрыть вкладку", Icon.CLOSE, "Ctrl+W", () -> closeTab(activeTab())).enabled(() -> activeTab() != null);
            pm.separator();
            pm.item("Закрыть проект", Icon.HOME, null, this::closeProject).enabled(() -> project != null);
            pm.item("Выйти из редактора", Icon.CLOSE, "Esc", this::onClose);
        });
        menu.menu("Правка", pm -> {
            pm.item("Отменить", Icon.UNDO, "Ctrl+Z", () -> {
                EditorTab t = activeTab();
                if (t != null) t.undo();
            });
            pm.item("Повторить", Icon.REDO, "Ctrl+Y", () -> {
                EditorTab t = activeTab();
                if (t != null) t.redo();
            });
            pm.separator();
            pm.item("Найти", Icon.SEARCH, "Ctrl+F", () -> withEditor(e -> e.editor.openFind(false)));
            pm.item("Заменить", Icon.SEARCH, "Ctrl+H", () -> withEditor(e -> e.editor.openFind(true)));
            pm.item("Перейти к строке", Icon.ARROW_RIGHT, "Ctrl+G", () -> withEditor(e -> e.editor.promptGotoLine()));
            pm.separator();
            pm.item("Закомментировать", Icon.CODE, "Ctrl+/", () -> withEditor(e -> e.editor.toggleComment()));
            pm.item("Форматировать", Icon.LIST, "Ctrl+Shift+F", () -> withEditor(e -> e.editor.format()));
            pm.item("Вставить мою позицию", Icon.PIN, "Alt+P", () -> withEditor(e -> {
                String pos = playerPosition();
                if (pos != null) e.editor.insertAtCaret(pos);
            }));
        });
        menu.menu("Запуск", pm -> {
            pm.item("Загрузить и запустить", Icon.PLAY, "F5", () -> deployActive(true)).color(Theme.SUCCESS).enabled(this::canRun);
            pm.item("Только загрузить в игру", Icon.UPLOAD, "F6", () -> deployActive(false)).enabled(this::canRun);
            pm.item("Остановить", Icon.STOP, "Shift+F5", () -> activeCommand("script stop %d")).color(Theme.ERROR).enabled(this::canRun);
            pm.separator();
            pm.item("Пауза", Icon.PAUSE, null, () -> activeCommand("script pause %d")).enabled(this::canRun);
            pm.item("Продолжить", Icon.PLAY, null, () -> activeCommand("script resume %d")).enabled(this::canRun);
            pm.item("Подготовить (setup)", Icon.SETUP, null, () -> activeCommand("script setup %d")).enabled(this::canRun);
            pm.item("Перейти к метке…", Icon.TAG, null, this::gotoLabelActive).enabled(this::canRun);
            pm.item("Триггер (wait trigger)", Icon.BOLT, null, () -> activeCommand("script trigger %d next")).enabled(this::canRun);
            pm.separator();
            pm.item("Остановить все сюжеты", Icon.STOP, null, () -> ClientState.get().command("script stop all")).color(Theme.ERROR);
            pm.item("Панель сюжетов", Icon.LIST, null, () -> {
                showBottom(true);
                bottomTabs.select(storiesTab);
            });
        });
        menu.menu("Вид", pm -> {
            pm.item("Проводник", Icon.FOLDER, "Ctrl+B", () -> toggle(explorer, mainSplit)).checked(() -> explorer.visible);
            pm.item("Правая панель", Icon.LIST, "Ctrl+Alt+B", () -> toggle(side, centerSplit)).checked(() -> side.visible);
            pm.item("Нижняя панель", Icon.TERMINAL, "Ctrl+J", () -> showBottom(!bottom.visible)).checked(() -> bottom.visible);
            pm.separator();
            pm.item("Модели", Icon.CUBE, "Ctrl+Shift+A", () -> openAssets(AssetsTab.Mode.MODELS)).enabled(() -> project != null);
            pm.item("Анимации", Icon.FILM, null, () -> openAssets(AssetsTab.Mode.ANIMATIONS)).enabled(() -> project != null);
            pm.item("Звуки", Icon.NOTE, null, () -> openAssets(AssetsTab.Mode.SOUNDS)).enabled(() -> project != null);
            pm.item("Мини-карта кода", Icon.CODE, null, () -> {
                Workspace.settings().minimap = !Workspace.settings().minimap;
                for (EditorTab t : tabs) if (t instanceof ScriptTab st) st.editor.showMinimap = Workspace.settings().minimap;
                relayout();
            }).checked(() -> Workspace.settings().minimap);
            pm.separator();
            pm.item("Масштаб интерфейса: " + scaleText() + "…", Icon.SEARCH, "Ctrl+= / Ctrl+−", this::scaleDialog);
            pm.item("Масштаб: как в Minecraft", Icon.SEARCH, null, () -> setPercent(0)).checked(() -> Workspace.settings().uiPercent <= 0);
            for (int pc : new int[]{50, 75, 100, 125, 150, 200, 250, 300}) {
                pm.item("Масштаб: " + pc + "%" + (pc == 100 ? " (как раньше 2x)" : ""), Icon.SEARCH, null, () -> setPercent(pc))
                        .checked(() -> Workspace.settings().uiPercent == pc);
            }
        });
        menu.menu("Справка", pm -> {
            pm.item("Справочник языка", Icon.BOOK, "F1", this::openDocs);
            pm.item("Горячие клавиши", Icon.INFO, null, () -> Dialog.info("Горячие клавиши", """
                    F5 — загрузить и запустить, F6 — загрузить, Shift+F5 — стоп
                    Ctrl+S — сохранить, Ctrl+Shift+S — сохранить всё, Ctrl+W — закрыть вкладку
                    Ctrl+Space — подсказки, Ctrl+клик / F12 — к объявлению
                    Ctrl+F / Ctrl+H — поиск / замена, Ctrl+G — к строке
                    Ctrl+/ — комментарий, Ctrl+D — дублировать, Alt+↑↓ — двигать строки
                    Ctrl+Shift+F — форматировать, Alt+P — вставить свои координаты
                    Ctrl+B / Ctrl+J — проводник / нижняя панель, Ctrl+Tab — следующая вкладка
                    Ctrl+Shift+A — модели, анимации и звуки
                    /editor on — клавиша N (настраивается) открывает и закрывает редактор"""));
            pm.item("О Spraute", Icon.INFO, null, () -> Dialog.info("Spraute", "Spraute — движок сюжетов с визуальным редактором.\n"
                    + "Текстовые скрипты (.sst) и блочные (.svs) запускаются прямо в мире.\n\nt.me/spraute"));
        });
    }

    private void buildRunBox() {
        Button run = new Button("Запустить", () -> deployActive(true)).icon(Icon.PLAY).style(Button.Style.SUCCESS).enabledWhen(this::canRun);
        run.autoWidth();
        run.tooltip("Загрузить в игру и запустить (F5)");
        Button upload = Button.icon(Icon.UPLOAD, "Только загрузить в игру (F6)", () -> deployActive(false)).enabledWhen(this::canRun);
        Button stop = Button.icon(Icon.STOP, "Остановить сюжет (Shift+F5)", () -> activeCommand("script stop %d")).iconColor(Theme.ERROR).enabledWhen(this::canRun);
        Button pause = Button.icon(Icon.PAUSE, "Пауза / продолжить", this::togglePauseActive).iconColor(Theme.WARNING).enabledWhen(this::canRun);
        Button label = Button.icon(Icon.TAG, "Перейти к метке", this::gotoLabelActive).iconColor(Theme.CODE_LABEL).enabledWhen(this::canRun);
        Button close = Button.icon(Icon.CLOSE, "Закрыть редактор (Esc)", this::onClose);
        for (Button b : List.of(upload, stop, pause, label)) b.prefW = 20;
        close.prefW = 20;
        runBox.add(run);
        runBox.add(upload);
        runBox.add(stop);
        runBox.add(pause);
        runBox.add(label);
        Widget sep = new Widget();
        sep.prefW = 6;
        runBox.add(sep);
        runBox.add(close);
    }

    private void toggle(Widget panel, SplitPane split) {
        panel.visible = !panel.visible;
        Workspace.settings().showSidebar = explorer.visible;
        Workspace.settings().showRight = side.visible;
        split.layout();
        relayout();
    }

    private void showBottom(boolean show) {
        bottom.visible = show;
        Workspace.settings().showBottom = show;
        relayout();
    }

    // ============================================================ projects

    private void newProjectDialog() {
        Dialog.prompt("Новый проект", "Название проекта:", "Мой сюжет", v -> {
            if (v.isEmpty()) return "Введите название";
            if (Files.exists(Workspace.projectsDir().resolve(Project.sanitize(v)))) return "Такой проект уже есть";
            return null;
        }, v -> {
            try {
                Project p = Workspace.createProject(v);
                Project.FileRef f = p.newScript("main");
                openProject(p.dir);
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createDemo() {
        String name = "Пример";
        int n = 2;
        while (Files.exists(Workspace.projectsDir().resolve(name))) name = "Пример " + n++;
        try {
            Project p = Workspace.createProject(name);
            Project.FileRef script = new Project.FileRef(p.scriptsDir().resolve("встреча.sst"), Project.Kind.SCRIPT);
            p.write(script, ScriptTemplates.demo(p.meta.baseId));
            Project.FileRef blocks = new Project.FileRef(p.blocksDir().resolve("блоки.svs"), Project.Kind.BLOCKS);
            p.write(blocks, VisualScript.create(p.meta.baseId + 1, "Блочный пример").toJson());
            p.newGui("меню");
            openProject(p.dir);
            openFile(script);
            toastSuccess("Создан проект «" + name + "»");
        } catch (IOException e) {
            toastError(e.getMessage());
        }
    }

    private void openProject(java.nio.file.Path dir) {
        if (project != null) closeProject();
        try {
            project = Project.open(dir);
        } catch (IOException e) {
            toastError("Не удалось открыть проект: " + e.getMessage());
            return;
        }
        Workspace.settings().lastProject = dir.getFileName().toString();
        Workspace.saveSettings();
        explorer.setProject(project);
        org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.register(project);
        for (String rel : new ArrayList<>(project.meta.openFiles)) {
            java.nio.file.Path p = project.dir.resolve(rel);
            if (Files.exists(p)) openFile(new Project.FileRef(p, Project.Kind.ofFile(rel)));
        }
        relayout();
    }

    private void closeProject() {
        if (project != null) {
            saveAll();
            project.meta.openFiles.clear();
            for (EditorTab t : allEditors()) if (t.file != null) project.meta.openFiles.add(project.dir.relativize(t.file.path()).toString().replace('\\', '/'));
            project.saveMeta();
        }
        closeGuiWindows();
        for (EditorTab t : new ArrayList<>(tabs)) removeTab(t);
        project = null;
        Workspace.settings().lastProject = null;
        Workspace.saveSettings();
        welcome.reload();
        relayout();
    }

    // ============================================================ files & tabs

    private EditorTab activeTab() {
        TabBar.Tab t = editorTabs.selected();
        return t == null ? null : (EditorTab) t.data;
    }

    private void withEditor(java.util.function.Consumer<ScriptTab> action) {
        if (activeTab() instanceof ScriptTab st) action.accept(st);
    }

    // ============================================================ GUI windows

    /** GUI editors open in their own floating windows over the IDE. */
    private record GuiWindow(org.sprautestudio.st_addon_spraute.ui.widgets.Window window, EditorTab tab) {}

    private final List<GuiWindow> guiWindows = new ArrayList<>();

    private EditorTab openGuiWindow(Project.FileRef f) {
        for (GuiWindow gw : guiWindows) {
            if (gw.tab().file != null && gw.tab().file.path().equals(f.path())) {
                gw.window().toFront();
                gw.tab().focusContent();
                return gw.tab();
            }
        }
        String content;
        try {
            content = project.read(f);
        } catch (IOException e) {
            toastError("Не удалось открыть файл: " + e.getMessage());
            return null;
        }
        boolean fxk = f.kind() == Project.Kind.EFFECT;
        boolean shopk = f.kind() == Project.Kind.SHOP;
        boolean questk = f.kind() == Project.Kind.QUEST;
        boolean zonek = f.kind() == Project.Kind.ZONE;
        boolean fightk = f.kind() == Project.Kind.FIGHT;
        boolean pathk = f.kind() == Project.Kind.PATH;
        boolean cut = f.kind() == Project.Kind.CUTSCENE;
        EditorTab tab = pathk ? new org.sprautestudio.st_addon_spraute.editor.path.PathTab(project, f, content)
                : fightk ? new org.sprautestudio.st_addon_spraute.editor.fight.FightTab(project, f, content)
                : zonek ? new org.sprautestudio.st_addon_spraute.editor.zone.ZoneTab(project, f, content)
                : questk ? new org.sprautestudio.st_addon_spraute.editor.quest.QuestTab(project, f, content)
                : shopk ? new org.sprautestudio.st_addon_spraute.editor.shop.ShopTab(project, f, content)
                : fxk ? new org.sprautestudio.st_addon_spraute.editor.fx.FxTab(project, f, content)
                : cut ? new org.sprautestudio.st_addon_spraute.editor.cutscene.CutsceneTab(project, f, content)
                : new org.sprautestudio.st_addon_spraute.editor.gui.GuiTab(project, f, content);
        org.sprautestudio.st_addon_spraute.ui.widgets.Window win = new org.sprautestudio.st_addon_spraute.ui.widgets.Window(
                () -> (pathk ? "Путь · " : fightk ? "Стиль боя · " : zonek ? "Зона · " : questk ? "Квест · " : shopk ? "Магазин · " : fxk ? "Эффект · " : cut ? "Катсцена · " : "Интерфейс · ") + tab.title() + (tab.isDirty() ? "  •" : ""),
                pathk ? Icon.ARROW_RIGHT : fightk ? Icon.BOLT : zonek ? Icon.PIN : questk ? Icon.FLAG : shopk ? Icon.TAG : fxk ? Icon.BOLT : cut ? Icon.FILM : Icon.MENU,
                pathk ? 0xFFFFAA33 : fightk ? 0xFFFF6B6B : zonek ? 0xFF4FC3F7 : questk ? 0xFFFFD25A : shopk ? 0xFF7BE08A : fxk ? 0xFFFFC94A : cut ? 0xFFFF8F5A : 0xFF5DA9F6, tab);
        win.insetTop = TOP;
        win.insetBottom = STATUS;
        GuiWindow gw = new GuiWindow(win, tab);
        win.onClose = () -> {
            if (tab.isDirty()) save(tab);
            guiWindows.remove(gw);
            if (tab instanceof org.sprautestudio.st_addon_spraute.editor.zone.ZoneTab zt) zt.closed();
            if (tab instanceof org.sprautestudio.st_addon_spraute.editor.path.PathTab pt) pt.closed();
            if (fxk) {
                org.sprautestudio.st_addon_spraute.fx.client.FxEngine.stop("__editor_preview", true);
                org.sprautestudio.st_addon_spraute.fx.client.FxEngine.previewStop();
            }
        };
        guiWindows.add(gw);
        openOverlay(win, false, false, null);
        // the cutscene editor needs room: open it maximized
        if (cut || fxk || shopk || questk || zonek || fightk || pathk) win.toggleMaximize();
        tab.focusContent();
        return tab;
    }

    private void closeGuiWindows() {
        for (GuiWindow gw : new ArrayList<>(guiWindows)) gw.window().close();
    }

    /** The editor the keyboard is in: a focused GUI window, else the active tab. */
    private EditorTab focusedEditor() {
        Widget f = focused();
        for (GuiWindow gw : guiWindows) if (f != null && gw.window().isAncestorOf(f)) return gw.tab();
        return activeTab();
    }

    private List<EditorTab> allEditors() {
        List<EditorTab> all = new ArrayList<>(tabs);
        for (GuiWindow gw : guiWindows) all.add(gw.tab());
        return all;
    }

    private EditorTab openFile(Project.FileRef f) {
        if (f.kind() == Project.Kind.GUI || f.kind() == Project.Kind.CUTSCENE || f.kind() == Project.Kind.EFFECT
                || f.kind() == Project.Kind.SHOP || f.kind() == Project.Kind.QUEST
                || f.kind() == Project.Kind.ZONE || f.kind() == Project.Kind.FIGHT || f.kind() == Project.Kind.PATH) return openGuiWindow(f);
        for (EditorTab t : tabs) {
            if (t.file != null && t.file.path().equals(f.path())) {
                selectTab(t);
                return t;
            }
        }
        String content;
        try {
            content = project.read(f);
        } catch (IOException e) {
            toastError("Не удалось открыть файл: " + e.getMessage());
            return null;
        }
        EditorTab tab = switch (f.kind()) {
            case SCRIPT -> new ScriptTab(project, f, content, this::playerPosition, this::saveActive);
            case BLOCKS -> new BlocksTab(project, f, content, this::playerPosition, this::convertToText);
            case GUI -> throw new IllegalStateException("GUIs open in windows");
            case CUTSCENE -> throw new IllegalStateException("cutscenes open in windows");
            case EFFECT -> throw new IllegalStateException("effects open in windows");
            case SHOP, QUEST, ZONE, FIGHT, PATH -> throw new IllegalStateException("shops, quests, zones, fights and paths open in windows");
        };
        addTab(tab);
        return tab;
    }

    private void addTab(EditorTab tab) {
        tabs.add(tab);
        editorArea.add(tab);
        TabBar.Tab t = editorTabs.add(new TabBar.Tab(tab::title, tab.icon(), tab));
        t.iconColor = tab.iconColor();
        t.closable = true;
        t.dirty = tab::isDirty;
        selectTab(tab);
    }

    private void selectTab(EditorTab tab) {
        TabBar.Tab t = editorTabs.find(tab);
        if (t != null) editorTabs.select(t);
    }

    private void showTab(EditorTab tab) {
        if (tab instanceof ScriptTab st) lastScript = st;
        editorArea.layout();
        tab.focusContent();
    }

    private void closeTab(EditorTab tab) {
        if (tab == null) return;
        if (tab.isDirty()) {
            Dialog d = new Dialog("Несохранённые изменения", Icon.WARNING);
            org.sprautestudio.st_addon_spraute.ui.widgets.Label l = new org.sprautestudio.st_addon_spraute.ui.widgets.Label(
                    "Сохранить изменения в «" + tab.title() + "»?").color(Theme.TEXT_DIM);
            d.body.add(l);
            d.addButton("Не сохранять", Button.Style.DANGER, () -> {
                d.close();
                removeTab(tab);
            });
            d.addButton("Отмена", Button.Style.DEFAULT, d::close);
            d.addButton("Сохранить", Button.Style.PRIMARY, () -> {
                d.close();
                save(tab);
                removeTab(tab);
            });
            d.open();
            return;
        }
        removeTab(tab);
    }

    private void removeTab(EditorTab tab) {
        TabBar.Tab t = editorTabs.find(tab);
        tabs.remove(tab);
        editorArea.remove(tab);
        if (t != null) editorTabs.removeTab(t);
        TabBar.Tab sel = editorTabs.selected();
        if (sel != null) editorTabs.select(sel);
        editorArea.layout();
    }

    private void openDocs() {
        for (EditorTab t : tabs) {
            if (t instanceof DocsTab) {
                selectTab(t);
                return;
            }
        }
        addTab(new DocsTab());
    }

    private void openModel(Project.ModelRef model) {
        for (EditorTab t : tabs) {
            if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.ModelTab mt && mt.model.dir().equals(model.dir())) {
                selectTab(t);
                return;
            }
        }
        addTab(new org.sprautestudio.st_addon_spraute.editor.assets.ModelTab(project, model, this::createEntity));
    }

    private void openEntity(Project.EntityRef entity) {
        for (EditorTab t : tabs) {
            if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.EntityTab et && et.ref.path().equals(entity.path())) {
                selectTab(t);
                return;
            }
        }
        addTab(new org.sprautestudio.st_addon_spraute.editor.assets.EntityTab(project, entity, this::insertCode, this::importModelsDialog));
    }

    /** Creates an entity type (with a project model, or a built-in townsperson) and opens it. */
    private void createEntity(String model) {
        if (project == null) return;
        Dialog.prompt("Новая сущность", "Имя сущности (в сюжете: character x = \"spraute:имя\"):", model != null ? model : "Житель",
                v -> v.isBlank() ? "Введите имя" : Files.exists(project.entitiesDir().resolve(Project.sanitize(v) + ".sent")) ? "Такая сущность уже есть" : null,
                v -> {
                    try {
                        Project.EntityRef e = project.newEntity(v, model);
                        org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
                        explorer.reload();
                        openEntity(e);
                    } catch (IOException ex) {
                        toastError(ex.getMessage());
                    }
                });
    }

    /**
     * Opens a native file dialog off the render thread: a blocking dialog freezes the game and the
     * integrated server drops the player for timing out.
     */
    private void pickFiles(String title, String[] patterns, String description, java.util.function.Consumer<List<java.nio.file.Path>> onPicked) {
        Thread t = new Thread(() -> {
            String result;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                result = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, description, true);
            } catch (Throwable ex) {
                Minecraft.getInstance().execute(() -> toastError("Не удалось открыть выбор файлов. Перетащите файлы прямо в окно редактора"));
                return;
            }
            if (result == null || result.isBlank()) return;
            List<java.nio.file.Path> paths = new ArrayList<>();
            for (String p : result.split("\\|")) if (!p.isBlank()) paths.add(java.nio.file.Path.of(p));
            Minecraft.getInstance().execute(() -> onPicked.accept(paths));
        }, "Spraute file dialog");
        t.setDaemon(true);
        t.start();
    }

    private void importModelsDialog() {
        if (project == null) return;
        pickFiles("Добавить модель (экспорт Blockbench)", new String[]{"*.geo.json", "*.animation.json", "*.png"},
                "Модель .geo.json, текстура .png, анимации .animation.json", this::importModels);
    }

    private void importModels(List<java.nio.file.Path> paths) {
        if (project == null) {
            toastError("Сначала откройте проект");
            return;
        }
        try {
            List<Project.ModelRef> made = project.importModels(paths);
            org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
            explorer.reload();
            if (made.isEmpty()) {
                toastError("Нужен файл модели .geo.json (экспорт Blockbench: Bedrock Geometry)");
                return;
            }
            // added from an entity's editor: that entity takes the new model right away
            if (activeTab() instanceof org.sprautestudio.st_addon_spraute.editor.assets.EntityTab et) et.useModel(made.get(0).name());
            else for (Project.ModelRef m : made) openModel(m);
            toastSuccess("Модель добавлена: " + made.get(made.size() - 1).name() + ". В сюжете: \"spraute:" + made.get(made.size() - 1).name() + "\"");
        } catch (IOException e) {
            toastError("Не удалось добавить модель: " + e.getMessage());
        }
    }

    /** The engine settings: a window of their own (the studio), back here when it closes. */
    private void openEngine() {
        if (project == null) return;
        for (EditorTab t : new java.util.ArrayList<>(tabs)) {
            if (t instanceof org.sprautestudio.st_addon_spraute.editor.engine.EngineTab) closeTab(t);
        }
        for (GuiWindow gw : guiWindows) {
            if (gw.tab() instanceof org.sprautestudio.st_addon_spraute.editor.engine.EngineStudio) {
                gw.window().toFront();
                gw.tab().focusContent();
                return;
            }
        }
        var tab = new org.sprautestudio.st_addon_spraute.editor.engine.EngineStudio(project);
        org.sprautestudio.st_addon_spraute.ui.widgets.Window win = new org.sprautestudio.st_addon_spraute.ui.widgets.Window(
                () -> "Настройки движка · " + tab.title() + (tab.isDirty() ? "  •" : ""), Icon.GEAR, tab.iconColor(), tab);
        win.insetTop = TOP;
        win.insetBottom = STATUS;
        GuiWindow gw = new GuiWindow(win, tab);
        win.onClose = () -> {
            if (tab.isDirty()) save(tab);
            guiWindows.remove(gw);
        };
        guiWindows.add(gw);
        openOverlay(win, false, false, null);
        win.toggleMaximize();
        tab.focusContent();
    }

    private void openAssets(AssetsTab.Mode mode) {
        for (EditorTab t : tabs) {
            if (t instanceof AssetsTab at) {
                at.setMode(mode);
                selectTab(t);
                return;
            }
        }
        AssetsTab at = new AssetsTab(this::insertCode, this::defaultCharacter);
        at.setMode(mode);
        addTab(at);
    }

    // ============================================================ pictures

    /** A picture opens in its own window, in the texture editor. */
    private void openImage(Project.ImageRef image) {
        for (GuiWindow gw : guiWindows) {
            if (gw.tab() instanceof org.sprautestudio.st_addon_spraute.editor.texture.TextureEditor te && te.image.path().equals(image.path())) {
                gw.window().toFront();
                te.focusContent();
                return;
            }
        }
        var tab = new org.sprautestudio.st_addon_spraute.editor.texture.TextureEditor(project, image);
        org.sprautestudio.st_addon_spraute.ui.widgets.Window win = new org.sprautestudio.st_addon_spraute.ui.widgets.Window(
                () -> "Текстура · " + tab.title() + (tab.isDirty() ? "  •" : ""), Icon.IMAGE, 0xFF5DA9F6, tab);
        win.insetTop = TOP;
        win.insetBottom = STATUS;
        GuiWindow gw = new GuiWindow(win, tab);
        win.onClose = () -> {
            if (tab.isDirty()) tab.save();
            tab.closed();
            guiWindows.remove(gw);
        };
        guiWindows.add(gw);
        openOverlay(win, false, false, null);
        win.toggleMaximize();
        tab.focusContent();
    }

    /** A new empty picture: its name and size. */
    private void newImageDialog() {
        if (project == null) return;
        Dialog d = new Dialog("Новая картинка", Icon.IMAGE);
        d.body.add(new org.sprautestudio.st_addon_spraute.ui.widgets.Label("Имя картинки:").color(Theme.TEXT_DIM));
        org.sprautestudio.st_addon_spraute.ui.widgets.TextField name = new org.sprautestudio.st_addon_spraute.ui.widgets.TextField();
        name.setText("картинка");
        name.selectAll();
        d.body.add(name);
        int[][] sizes = {{16, 16}, {32, 32}, {64, 64}, {128, 64}, {64, 32}, {128, 128}, {256, 256}};
        int[] pick = {3};
        d.body.add(new org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown<>(() -> List.of(0, 1, 2, 3, 4, 5, 6), () -> pick[0],
                i -> sizes[i][0] + "×" + sizes[i][1] + (i == 2 ? " — скин Minecraft" : i == 3 ? " — персонаж" : i == 0 ? " — иконка, предмет" : ""),
                i -> pick[0] = i));
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        Runnable ok = () -> {
            String n = name.text().trim();
            if (n.isEmpty()) return;
            try {
                Files.createDirectories(project.imagesDir());
                java.nio.file.Path p = project.imagesDir().resolve(Project.sanitize(n) + ".png");
                int k = 2;
                while (Files.exists(p)) p = project.imagesDir().resolve(Project.sanitize(n) + " " + k++ + ".png");
                javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(sizes[pick[0]][0], sizes[pick[0]][1], java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", p.toFile());
                d.close();
                Project.ImageRef ref = new Project.ImageRef(p);
                org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.load(project, ref);
                explorer.reload();
                openImage(ref);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        };
        d.addButton("Создать", Button.Style.PRIMARY, ok);
        d.onEnter = ok;
        d.open();
    }

    /** File chooser for pictures (they can also be dropped onto the window). */
    private void importImagesDialog() {
        if (project == null) return;
        String[] patterns = new String[Project.IMAGE_EXTENSIONS.size()];
        for (int i = 0; i < patterns.length; i++) patterns[i] = "*" + Project.IMAGE_EXTENSIONS.get(i);
        pickFiles("Добавить картинки в проект", patterns, "Картинки (PNG, JPG, BMP, GIF)", this::importImages);
    }

    /**
     * A Minecraft skin turned into a Spraute texture among the project's pictures ("<name> ST");
     * null (with a message) when it is not a skin.
     */
    private Project.ImageRef makeFaceSkin(java.nio.file.Path source, String name) {
        if (project == null) return null;
        try {
            java.nio.file.Path out = org.sprautestudio.st_addon_spraute.editor.assets.FaceSkin.convertFile(source, project.imagesDir(), Project.sanitize(name) + " лицо");
            Project.ImageRef ref = new Project.ImageRef(out);
            org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.load(project, ref);
            explorer.reload();
            toastSuccess("Текстура Spraute «" + ref.name() + "» готова: глаза, брови и рот теперь двигаются");
            return ref;
        } catch (IOException e) {
            toastError(e.getMessage());
            return null;
        }
    }

    /** For the entity editor: pick a Minecraft skin, make it a Spraute texture, give its picture name. */
    public static void pickFaceSkin(java.util.function.Consumer<String> done) {
        SprauteScreen s = instance;
        if (s == null || s.project == null) return;
        s.pickFiles("Скин Minecraft (64×64)", new String[]{"*.png"}, "Скин PNG", paths -> {
            for (java.nio.file.Path p : paths) {
                String n = p.getFileName().toString();
                org.sprautestudio.st_addon_spraute.editor.assets.FaceSkinDialog.open(s.project, p, n.endsWith(".png") ? n.substring(0, n.length() - 4) : n, r -> {
                    s.explorer.reload();
                    done.accept(r.name());
                });
            }
        });
    }

    private static boolean looksLikeSkin(java.nio.file.Path p) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
            return img != null && org.sprautestudio.st_addon_spraute.editor.assets.FaceSkin.isSkin(img);
        } catch (Exception e) {
            return false;
        }
    }

    private void importImages(List<java.nio.file.Path> paths) {
        if (project == null) {
            toastError("Сначала откройте проект");
            return;
        }
        Project.ImageRef last = null;
        int n = 0, skipped = 0;
        for (java.nio.file.Path p : paths) {
            if (!Project.isImage(p) || !Files.isRegularFile(p)) {
                skipped++;
                continue;
            }
            try {
                last = project.importImage(p);
                if (org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.load(project, last) == null) {
                    toastError("Не удалось прочитать " + p.getFileName());
                }
                // a Minecraft skin: its Spraute version comes along by itself
                if (p.getFileName().toString().toLowerCase().endsWith(".png") && looksLikeSkin(last.path())) makeFaceSkin(last.path(), last.name());
                n++;
            } catch (IOException e) {
                toastError("Ошибка копирования " + p.getFileName() + ": " + e.getMessage());
            }
        }
        explorer.reload();
        if (n > 0) toastSuccess(n == 1 ? "Картинка «" + last.name() + "» добавлена" : "Добавлено картинок: " + n);
        if (skipped > 0) toastInfo("Пропущено файлов (не картинки): " + skipped);
        if (n == 1) openImage(last);
    }

    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        // model files (and the textures dropped with them) go to models, the rest to pictures
        boolean model = paths.stream().anyMatch(Project::isModelFile);
        java.util.function.Predicate<java.nio.file.Path> audio = p -> Project.isSound(p)
                || org.sprautestudio.st_addon_spraute.editor.assets.SoundConvert.convertible(p);
        List<java.nio.file.Path> sounds = paths.stream().filter(audio).toList();
        if (!sounds.isEmpty()) {
            importSounds(sounds);
            paths = paths.stream().filter(audio.negate()).toList();
            if (paths.isEmpty()) return;
        }
        if (model) importModels(paths);
        else importImages(paths);
    }

    private void importSounds(List<java.nio.file.Path> paths) {
        if (project == null) {
            toastError("Сначала откройте проект");
            return;
        }
        Project target = project;
        Project.SoundRef last = null;
        int n = 0, skipped = 0;
        List<java.nio.file.Path> convert = new ArrayList<>();
        for (java.nio.file.Path p : paths) {
            if (!Files.isRegularFile(p)) continue;
            if (org.sprautestudio.st_addon_spraute.editor.assets.SoundConvert.convertible(p)) {
                convert.add(p);
                continue;
            }
            if (!Project.isSound(p)) {
                skipped++;
                continue;
            }
            try {
                last = target.importSound(p);
                n++;
            } catch (IOException e) {
                toastError("Ошибка копирования " + p.getFileName() + ": " + e.getMessage());
            }
        }
        explorer.reload();
        if (n > 0) toastSuccess(n == 1 ? "Звук «" + last.name() + "» добавлен: sound \"" + last.name() + "\"" : "Добавлено звуков: " + n);
        if (skipped > 0) toastInfo("Пропущено файлов (не звуки): " + skipped);
        if (convert.isEmpty()) return;
        // MP3 and the like: Windows turns them into WAV in the background
        toastInfo(convert.size() == 1 ? "Перевожу «" + convert.get(0).getFileName() + "» в WAV…" : "Перевожу в WAV: " + convert.size() + " файла(ов)…");
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            for (java.nio.file.Path p : convert) {
                String file = p.getFileName().toString();
                String base = Project.sanitize(file.substring(0, file.lastIndexOf('.')));
                String name = base;
                for (int k = 2; Files.exists(target.soundsDir().resolve(name + ".wav")) || Files.exists(target.soundsDir().resolve(name + ".ogg")); k++)
                    name = base + " " + k;
                String fname = name;
                try {
                    org.sprautestudio.st_addon_spraute.editor.assets.SoundConvert.toWav(p, target.soundsDir(), fname);
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        explorer.reload();
                        toastSuccess("Звук «" + fname + "» добавлен: sound \"" + fname + "\"");
                    });
                } catch (IOException e) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> toastError(file + ": " + e.getMessage()));
                }
            }
        });
    }

    private void renameImage(Project.ImageRef image) {
        Dialog.prompt("Переименовать картинку", "Новое имя (используется в скриптах):", image.name(), v -> v.isBlank() ? "Введите имя" : null, v -> {
            try {
                for (EditorTab t : new ArrayList<>(tabs)) {
                    if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.ImageTab it && it.image.path().equals(image.path())) removeTab(t);
                }
                org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.forget(image.path());
                Project.ImageRef renamed = project.renameImage(image, v);
                org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.load(project, renamed);
                explorer.reload();
                toastInfo("Не забудьте обновить icon/portrait \"" + image.name() + "\" в скриптах");
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void deleteImage(Project.ImageRef image) {
        Dialog.confirm("Удалить картинку", "Картинка «" + image.name() + "» будет удалена из проекта.", "Удалить", true, () -> {
            for (EditorTab t : new ArrayList<>(tabs)) {
                if (t instanceof org.sprautestudio.st_addon_spraute.editor.assets.ImageTab it && it.image.path().equals(image.path())) removeTab(t);
            }
            org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.forget(image.path());
            try {
                Files.deleteIfExists(image.path());
            } catch (IOException e) {
                toastError(e.getMessage());
            }
            explorer.reload();
        });
    }

    /** Last text script the user worked in: target for code inserted from the asset browser. */
    private ScriptTab lastScript;

    private void insertCode(String line) {
        ScriptTab target = lastScript != null && tabs.contains(lastScript) ? lastScript : null;
        if (target == null) for (EditorTab t : tabs) if (t instanceof ScriptTab st) target = st;
        if (target == null) {
            Minecraft.getInstance().keyboardHandler.setClipboard(line);
            toastInfo("Нет открытого текстового скрипта — строка скопирована в буфер");
            return;
        }
        selectTab(target);
        target.editor.insertLine(line);
        toastSuccess("Вставлено в " + target.title());
    }

    private String defaultCharacter() {
        ScriptTab target = lastScript != null && tabs.contains(lastScript) ? lastScript : null;
        if (target == null) for (EditorTab t : tabs) if (t instanceof ScriptTab st) target = st;
        Analyzer.Result r = target != null ? target.analysis() : null;
        if (r != null && !r.characters.isEmpty()) return r.characters.keySet().iterator().next();
        return "alice";
    }

    private void openDocsWithoutProject() {
        DocsTab docs = new DocsTab();
        Dialog d = new Dialog("Справочник языка", Icon.BOOK).width(Math.min(700, vw - 40));
        docs.prefH = Math.min(380, vh - 120);
        d.body.add(docs);
        d.addButton("Закрыть", Button.Style.PRIMARY, d::close);
        d.open();
    }

    private void createScript(boolean blocks) {
        if (project == null) return;
        Dialog.prompt(blocks ? "Новый блочный скрипт" : "Новый текстовый скрипт", "Имя файла:", blocks ? "блоки" : "сюжет", v -> {
            if (v.isEmpty()) return "Введите имя";
            String ext = blocks ? ".svs" : ".sst";
            java.nio.file.Path p = (blocks ? project.blocksDir() : project.scriptsDir()).resolve(Project.sanitize(v) + ext);
            return Files.exists(p) ? "Такой файл уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = blocks ? project.newBlocks(v) : project.newScript(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private long guiCacheTime;
    private java.util.Map<String, List<String>> guiCache = java.util.Map.of();

    /** GUI name -> element ids of the open project (cached for a second; open tabs win over files). */
    private java.util.Map<String, List<String>> projectGuis() {
        if (project == null) return java.util.Map.of();
        long now = System.currentTimeMillis();
        if (now - guiCacheTime < 1000) return guiCache;
        guiCacheTime = now;
        java.util.Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        project.guiSources().forEach((name, json) -> map.put(name, org.sprautestudio.st_addon_spraute.gui.GuiDoc.fromJson(json).ids()));
        for (GuiWindow gw : guiWindows) {
            if (gw.tab() instanceof org.sprautestudio.st_addon_spraute.editor.gui.GuiTab gt) map.put(gt.file.name(), gt.elementIds());
        }
        guiCache = map;
        return map;
    }

    private void createFight() {
        if (project == null) return;
        Dialog.prompt("Новый стиль боя", "Имя стиля (в сюжете: fight alice style \"имя\"). Начнётся как кулачный бой — можно взять и другой шаблон.", "мечник", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.fightsDir().resolve(Project.sanitize(v) + Project.Kind.FIGHT.ext)) ? "Такой стиль уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newFight(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    /** The editor's size: 100% is twice Minecraft's pixels (the old 2x), 0 — follow Minecraft. */
    private void setPercent(int pc) {
        Workspace.settings().uiPercent = Math.max(0, Math.min(300, pc));
        Workspace.saveSettings();
        setUiScale(Workspace.settings().physicalScale());
        relayout();
    }

    private String scaleText() {
        int pc = Workspace.settings().uiPercent;
        return pc <= 0 ? "как в Minecraft" : pc + "%";
    }

    private void scaleDialog() {
        int cur = Workspace.settings().uiPercent;
        Dialog.prompt("Масштаб интерфейса", "От 10 до 300 процентов (100 — как раньше «2x», 0 — как в Minecraft):",
                String.valueOf(Math.max(0, cur)), v -> {
                    try {
                        int n = Integer.parseInt(v.trim().replace("%", ""));
                        return n == 0 || (n >= 10 && n <= 300) ? null : "Число от 10 до 300 (или 0)";
                    } catch (NumberFormatException e) {
                        return "Нужно число";
                    }
                }, v -> setPercent(Integer.parseInt(v.trim().replace("%", ""))));
    }

    private void createPath() {
        if (project == null) return;
        Dialog.prompt("Новый путь", "Имя пути (в сюжете: walk alice \"имя\"). Появится рядом с вами; потом — «Редактировать в мире».", "обход", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.pathsDir().resolve(Project.sanitize(v) + Project.Kind.PATH.ext)) ? "Такой путь уже есть" : null;
        }, v -> {
            try {
                var pl = net.minecraft.client.Minecraft.getInstance().player;
                Project.FileRef f = project.newPath(v, pl != null ? pl.position() : net.minecraft.world.phys.Vec3.ZERO);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createZone() {
        if (project == null) return;
        Dialog.prompt("Новая зона", "Имя зоны (в сюжете: on zoneenter \"имя\"). Появится вокруг вас; потом — «Редактировать в мире».", "деревня", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.zonesDir().resolve(Project.sanitize(v) + Project.Kind.ZONE.ext)) ? "Такая зона уже есть" : null;
        }, v -> {
            try {
                var pl = net.minecraft.client.Minecraft.getInstance().player;
                Project.FileRef f = project.newZone(v, pl != null ? pl.position() : net.minecraft.world.phys.Vec3.ZERO);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createQuest() {
        if (project == null) return;
        Dialog.prompt("Новый квест", "Имя квеста (в сюжете: quest start \"имя\"). Начнётся с примера: задачи, счётчик, скрытая задача и награда.", "ворота", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.questsDir().resolve(Project.sanitize(v) + Project.Kind.QUEST.ext)) ? "Такой квест уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newQuest(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createShop() {
        if (project == null) return;
        Dialog.prompt("Новый магазин", "Имя магазина (в сюжете: shop open \"имя\"). Начнётся с примера: еда, снаряжение, скупка и обмен.", "лавка", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.shopsDir().resolve(Project.sanitize(v) + Project.Kind.SHOP.ext)) ? "Такой магазин уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newShop(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createEffect() {
        if (project == null) return;
        Dialog.prompt("Новый эффект", "Имя эффекта (в сюжете: fx play \"имя\" at player). Начнётся с блёсток — "
                + "в редакторе можно взять любой шаблон: ядерный взрыв, луч в небо, орбы…", "мой эффект", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.effectsDir().resolve(Project.sanitize(v) + Project.Kind.EFFECT.ext)) ? "Такой эффект уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newEffect(v, "sparkles");
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createCutscene() {
        if (project == null) return;
        Dialog.prompt("Новая катсцена", "Имя катсцены (в сюжете: cutscene play \"имя\"):", "вступление", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.cutscenesDir().resolve(Project.sanitize(v) + Project.Kind.CUTSCENE.ext)) ? "Такая катсцена уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newCutscene(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void createGui() {
        if (project == null) return;
        Dialog.prompt("Новый интерфейс", "Имя интерфейса (по нему сюжет открывает его: gui open \"имя\"):", "меню", v -> {
            if (v.isBlank()) return "Введите имя";
            return Files.exists(project.guisDir().resolve(Project.sanitize(v) + Project.Kind.GUI.ext)) ? "Такой интерфейс уже есть" : null;
        }, v -> {
            try {
                Project.FileRef f = project.newGui(v);
                explorer.reload();
                openFile(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void convertToText(String code) {
        if (project == null) return;
        EditorTab src = activeTab();
        String base = src != null ? src.title() + " (текст)" : "из блоков";
        try {
            Project.FileRef f = new Project.FileRef(project.scriptsDir().resolve(Project.sanitize(base) + ".sst"), Project.Kind.SCRIPT);
            int n = 2;
            while (Files.exists(f.path())) f = new Project.FileRef(project.scriptsDir().resolve(Project.sanitize(base) + " " + n++ + ".sst"), Project.Kind.SCRIPT);
            project.write(f, code);
            explorer.reload();
            openFile(f);
            toastInfo("Создан текстовый скрипт. Не забудьте сменить id, если оставляете оба.");
        } catch (IOException e) {
            toastError(e.getMessage());
        }
    }

    private void renameFile(Project.FileRef f) {
        Dialog.prompt("Переименовать", "Новое имя:", f.name(), v -> v.isEmpty() ? "Введите имя" : null, v -> {
            try {
                EditorTab open = null;
                for (EditorTab t : tabs) if (t.file != null && t.file.path().equals(f.path())) open = t;
                for (GuiWindow gw : new ArrayList<>(guiWindows)) {
                    if (gw.tab().file != null && gw.tab().file.path().equals(f.path())) {
                        open = gw.tab();
                        gw.window().close();
                    }
                }
                if (open != null) {
                    save(open);
                    if (tabs.contains(open)) removeTab(open);
                }
                Project.FileRef nf = project.rename(f, v);
                explorer.reload();
                if (open != null) openFile(nf);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
        });
    }

    private void deleteFile(Project.FileRef f) {
        Dialog.confirm("Удалить файл", "Файл «" + f.fileName() + "» будет удалён.", "Удалить", true, () -> {
            for (EditorTab t : new ArrayList<>(tabs)) if (t.file != null && t.file.path().equals(f.path())) removeTab(t);
            for (GuiWindow gw : new ArrayList<>(guiWindows)) {
                if (gw.tab().file != null && gw.tab().file.path().equals(f.path())) {
                    guiWindows.remove(gw);
                    closeOverlay(gw.window());
                }
            }
            try {
                project.delete(f);
            } catch (IOException e) {
                toastError(e.getMessage());
            }
            explorer.reload();
        });
    }

    private void openScriptById(int id) {
        if (project == null) return;
        for (EditorTab t : tabs) {
            if (t.scriptId() == id) {
                selectTab(t);
                return;
            }
        }
        for (Project.FileRef f : project.files()) {
            if (f.kind() == Project.Kind.GUI || f.kind() == Project.Kind.CUTSCENE || f.kind() == Project.Kind.EFFECT
                    || f.kind() == Project.Kind.SHOP || f.kind() == Project.Kind.QUEST
                    || f.kind() == Project.Kind.ZONE || f.kind() == Project.Kind.FIGHT || f.kind() == Project.Kind.PATH) continue;
            try {
                String text = project.read(f);
                int fileId = -1;
                if (f.kind() == Project.Kind.SCRIPT) {
                    var header = org.sprautestudio.st_addon_spraute.lang.Parser.parse(text).program.header;
                    if (header != null) fileId = header.id;
                } else {
                    VisualScript.Stack main = VisualScript.fromJson(text).mainStack();
                    if (main != null) fileId = Integer.parseInt(main.blocks.get(0).field("id").trim());
                }
                if (fileId == id) {
                    openFile(f);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        toastInfo("Файл сюжета #" + id + " не найден в текущем проекте");
    }

    // ============================================================ saving

    private void save(EditorTab t) {
        try {
            t.save();
            // a zone, a shop, a quest… changes in the world right away, without starting a story again
            if (project != null && t.source() == null) ClientState.get().sources(project.name(), project.deploySources());
        } catch (IOException e) {
            toastError("Ошибка сохранения: " + e.getMessage());
        }
    }

    private void saveActive() {
        EditorTab t = focusedEditor();
        if (t != null && t.file != null) {
            save(t);
            toastSuccess("Сохранено: " + t.title());
        }
    }

    private void saveAll() {
        for (EditorTab t : allEditors()) if (t.isDirty()) save(t);
        Workspace.saveSettings();
    }

    // ============================================================ running

    private boolean canRun() {
        EditorTab t = activeTab();
        return t != null && t.source() != null && Minecraft.getInstance().getConnection() != null;
    }

    private void deployActive(boolean start) {
        EditorTab t = activeTab();
        if (t != null) deploy(t, start);
    }

    private void deploy(EditorTab t, boolean start) {
        if (t.source() == null || project == null) return;
        if (t.file != null && t.isDirty()) save(t);
        long errors = t.problems().stream().filter(p -> p.severity() == LanguageSupport.Severity.ERROR).count();
        if (errors > 0) {
            toastError("В скрипте ошибок: " + errors + ". Исправьте их перед запуском.");
            showBottom(true);
            bottomTabs.select(problemsTab);
            return;
        }
        org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.register(project);
        ClientState.get().addLocal(Packets.Log.INFO, "Spraute", (start ? "Запуск " : "Загрузка ") + t.title() + "…");
        // the project's engine settings go to the world along with its stories
        java.nio.file.Path engine = org.sprautestudio.st_addon_spraute.editor.engine.EngineTab.file(project);
        if (java.nio.file.Files.exists(engine)) {
            org.sprautestudio.st_addon_spraute.engine.EngineConfig ec = org.sprautestudio.st_addon_spraute.editor.engine.EngineTab.load(project);
            ec.project = project.name();
            ClientState.get().engine(ec.toJson());
        }
        ClientState.get().deploy(project.name(), t.source(), start, project.deploySources());
    }

    private void activeCommand(String pattern) {
        EditorTab t = activeTab();
        if (t == null || t.scriptId() <= 0) {
            toastError("У скрипта нет корректного id");
            return;
        }
        ClientState.get().command(String.format(pattern, t.scriptId()));
    }

    private void togglePauseActive() {
        EditorTab t = activeTab();
        if (t == null) return;
        Packets.ScriptEntry e = ClientState.get().script(t.scriptId());
        activeCommand(e != null && e.paused ? "script resume %d" : "script pause %d");
    }

    private void gotoLabelActive() {
        EditorTab t = activeTab();
        if (t == null) return;
        Analyzer.Result r = t.analysis();
        PopupMenu pm = new PopupMenu();
        Packets.ScriptEntry e = ClientState.get().script(t.scriptId());
        boolean active = e != null && e.active;
        if (r == null || r.labels.isEmpty()) pm.item("В скрипте нет меток (label)", null).enabled(() -> false);
        if (r != null) {
            for (String label : r.labels.keySet()) {
                pm.item(label, Icon.TAG, null, () -> ClientState.get().command(
                        (active ? "script goto " : "script start ") + t.scriptId() + " " + label)).color(Theme.CODE_LABEL);
            }
        }
        pm.show(Math.max(0, mouseX() - 40), TOP);
    }

    private String playerPosition() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        return "(" + (int) Math.floor(p.getX()) + ", " + (int) Math.floor(p.getY()) + ", " + (int) Math.floor(p.getZ()) + ")";
    }

    // ============================================================ problems

    private List<EditorTab.Problem> collectProblems() {
        List<EditorTab.Problem> list = new ArrayList<>();
        for (EditorTab t : tabs) list.addAll(t.problems());
        list.sort((a, b) -> Integer.compare(a.severity().ordinal(), b.severity().ordinal()));
        return list;
    }

    // ============================================================ lifecycle

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 40 == 0 && Minecraft.getInstance().getConnection() != null && ClientState.get().statusAge() > 1800) {
            ClientState.get().requestStatus();
        }
        if (tickCounter % 1200 == 0) saveAll();
        if (tickCounter % 40 == 0 && project != null) {
            int before = imageCount;
            org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages.register(project);
            org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.scan();
            imageCount = project.images().size();
            if (before != imageCount) explorer.reload();
        }
    }

    @Override
    public void onClose() {
        saveAll();
        if (project != null) {
            project.meta.openFiles.clear();
            for (EditorTab t : allEditors()) if (t.file != null) project.meta.openFiles.add(project.dir.relativize(t.file.path()).toString().replace('\\', '/'));
            project.saveMeta();
        }
        Workspace.saveSettings();
        super.onClose();
    }

    @Override
    protected boolean onShortcut(int key, int mods) {
        boolean ctrl = ctrl(mods), shift = shift(mods), alt = alt(mods);
        // the editor's size, by 10%
        if (ctrl && !alt && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)) {
            int now = Workspace.settings().uiPercent > 0 ? Workspace.settings().uiPercent
                    : (int) Math.round(net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale() * 50);
            boolean up = key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD;
            setPercent(Math.max(10, Math.min(300, (now / 10) * 10 + (up ? 10 : -10))));
            toastInfo("Масштаб интерфейса: " + Workspace.settings().uiPercent + "%");
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            if (shift) activeCommand("script stop %d");
            else if (canRun()) deployActive(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F6 && canRun()) {
            deployActive(false);
            return true;
        }
        // the editor key closes the editor again, unless the user is typing
        if (!ctrl && !alt && ClientEvents.editorMode() && ClientEvents.isEditorKey(key) && !typing()) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F1) {
            if (project != null) openDocs();
            else openDocsWithoutProject();
            return true;
        }
        if (!ctrl) return false;
        switch (key) {
            case GLFW.GLFW_KEY_S -> {
                if (shift) saveAll();
                else saveActive();
            }
            case GLFW.GLFW_KEY_W -> {
                EditorTab t = focusedEditor();
                GuiWindow win = null;
                for (GuiWindow gw : guiWindows) if (gw.tab() == t) win = gw;
                if (win != null) win.window().close();
                else closeTab(activeTab());
            }
            case GLFW.GLFW_KEY_N -> createScript(shift);
            case GLFW.GLFW_KEY_A -> {
                if (!shift || project == null) return false;
                openAssets(AssetsTab.Mode.MODELS);
            }
            case GLFW.GLFW_KEY_B -> {
                if (alt) toggle(side, centerSplit);
                else toggle(explorer, mainSplit);
            }
            case GLFW.GLFW_KEY_J -> showBottom(!bottom.visible);
            case GLFW.GLFW_KEY_Y -> {
                EditorTab t = focusedEditor();
                if (t != null) t.redo();
            }
            case GLFW.GLFW_KEY_Z -> {
                EditorTab t = focusedEditor();
                if (t != null) {
                    if (shift) t.redo();
                    else t.undo();
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (tabs.size() > 1) {
                    int i = tabs.indexOf(activeTab());
                    selectTab(tabs.get(Math.floorMod(i + (shift ? -1 : 1), tabs.size())));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean typing() {
        Widget f = focused();
        return f instanceof org.sprautestudio.st_addon_spraute.ui.widgets.TextField
                || f instanceof org.sprautestudio.st_addon_spraute.ui.code.CodeEditor;
    }

    /** Used by the mod to log editor problems. */
    static void log(String msg) {
        St_addon_spraute.LOGGER.info("[Spraute] {}", msg);
    }

    public static Supplier<SprauteScreen> current() {
        return () -> instance;
    }
}
