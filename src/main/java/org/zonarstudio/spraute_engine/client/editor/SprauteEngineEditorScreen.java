package org.zonarstudio.spraute_engine.client.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTabItemFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.20.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorAssetIndex;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorBonePicker;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorGrid;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorInspectorUi;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorMarqueeSelect;
import org.zonarstudio.spraute_engine.client.editor.scene.HierarchyPanel;
import org.zonarstudio.spraute_engine.client.editor.scene.InspectorPanel;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput;
import org.zonarstudio.spraute_engine.client.editor.scene.EditorWorldViewport;
import org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession;
import org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession;
import org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.script.ScriptConsole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dear ImGui-powered in-game editor shell. */
public final class SprauteEngineEditorScreen extends Screen {
    private static final float STATUS_BAR_HEIGHT = 24f;

    private static float statusBarHeight() {
        return STATUS_BAR_HEIGHT * SprauteImGuiBackend.uiScale();
    }

    private final List<Path> scripts = new ArrayList<>();
    private final List<Path> scenes = new ArrayList<>();
    private final List<ScriptTab> scriptTabs = new ArrayList<>();
    private final List<VisualTab> visualTabs = new ArrayList<>();
    private final List<TextureTab> textureTabs = new ArrayList<>();
    private final List<ModelTab> modelTabs = new ArrayList<>();
    private Path requestedScriptTab;

    private final HierarchyPanel hierarchyPanel = new HierarchyPanel();
    private final InspectorPanel inspectorPanel = new InspectorPanel();
    private final org.zonarstudio.spraute_engine.client.editor.scene.TimelinePanel timelinePanel =
            new org.zonarstudio.spraute_engine.client.editor.scene.TimelinePanel();
    private final ImString newSceneName = new ImString(64);
    private final ImString projectSearch = new ImString(128);
    private final ImString projectNameBuf = new ImString(256);
    private final ImBoolean showHierarchy = new ImBoolean(true);
    private final ImBoolean showInspector = new ImBoolean(true);
    private final ImBoolean showProject = new ImBoolean(true);
    private final ImBoolean showTimeline = new ImBoolean(true);
    private final ImBoolean showConsole = new ImBoolean(true);
    private final ImBoolean showPlugins = new ImBoolean(false);
    private final ImBoolean showSettings = new ImBoolean(false);
    private final ImBoolean showProjectExport = new ImBoolean(false);
    private final ImBoolean showSceneExport = new ImBoolean(false);
    private final ImBoolean showLocalization = new ImBoolean(false);
    private boolean openBlockSyntaxHelp;
    private final org.zonarstudio.spraute_engine.client.editor.scene.ScriptConsolePanel consolePanel =
            new org.zonarstudio.spraute_engine.client.editor.scene.ScriptConsolePanel();
    private boolean openNewScenePopup;
    private boolean openExitConfirmation;
    private boolean exitConfirmed;
    private boolean openProjectRename;
    private boolean openProjectDelete;
    private boolean openProjectCreate;
    private Path projectCtxPath;
    private boolean projectCtxIsDir;
    private Path projectCreateParent;
    /** script | folder | text | scene | texture */
    private String projectCreateKind = "script";
    private int projectCreateTexSize = 16;
    private String projectPopupError = "";
    private double lastMouseX;
    private double lastMouseY;
    private float worldViewportMinX;
    private float worldViewportMinY;
    private float worldViewportMaxX;
    private float worldViewportMaxY;
    private boolean worldTabActive = true;
    private final ProjectOsFileDrop projectOsDrop = new ProjectOsFileDrop();

    public SprauteEngineEditorScreen() {
        super(Component.literal("Spraute Engine"));
    }

    @Override
    protected void init() {
        if (RemoteProjectClient.isRemote()) RemoteProjectClient.requestList();
        reloadProject();
        projectOsDrop.setOnImported(this::onProjectImported);
        projectOsDrop.install();
    }

    //? if >=1.20.1 {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fly look/move runs on RenderTickEvent.START (before world capture).
        if (worldViewportActiveAndImGuiOk()) {
            // Real canvases go into the main framebuffer first, so the world
            // capture picks them up exactly as they render in game.
            renderCanvasPreviews(graphics, partialTick);
            EditorWorldViewport.flushCapture();
        }
        graphics.fill(0, 0, width, height, 0xFF121417);
        if (!SprauteImGuiBackend.isAvailable()) {
            drawImGuiUnavailable(graphics);
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateGizmoHover(mouseX, mouseY);
        renderOverlay();
    }

    private void drawImGuiUnavailable(GuiGraphics graphics) {
        graphics.drawString(font, "Spraute Editor: ImGui failed to load", 24, 40, 0xFFFF6666, false);
        graphics.drawString(font, "Press Esc to close", 24, 56, 0xFFCCCCCC, false);
        String err = SprauteImGuiBackend.initError();
        if (err != null) {
            int y = 80;
            for (String line : wrapErrorLines(err, width - 48)) {
                graphics.drawString(font, line, 24, y, 0xFFFFAAAA, false);
                y += 12;
            }
        }
    }

    private void renderCanvasPreviews(GuiGraphics graphics, float partialTick) {
        var doc = SceneEditorSession.document();
        if (doc == null) {
            org.zonarstudio.spraute_engine.client.SprauteScriptScreen.retainEditorPreviews(java.util.Set.of());
            return;
        }
        var canvases = canvasesByLayer(doc.root);
        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.retainEditorPreviews(
                canvases.stream().map(canvas -> canvas.id).collect(java.util.stream.Collectors.toSet()));
        for (SceneNode canvas : canvases) {
            org.zonarstudio.spraute_engine.client.SprauteScriptScreen
                    .renderEditorPreview(canvas.id,
                            org.zonarstudio.spraute_engine.scene.SceneCanvasBuilder.buildJson(canvas, doc.root),
                            graphics, partialTick);
        }
    }
    //?} else {
    /*@Override
    public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        // Fly look/move runs on RenderTickEvent.START (before world capture).
        if (worldViewportActiveAndImGuiOk()) {
            // Real canvases go into the main framebuffer first, so the world
            // capture picks them up exactly as they render in game.
            renderCanvasPreviews(graphics, partialTick);
            EditorWorldViewport.flushCapture();
        }
        net.minecraft.client.gui.GuiComponent.fill(graphics, 0, 0, width, height, 0xFF121417);
        if (!SprauteImGuiBackend.isAvailable()) {
            drawImGuiUnavailable(graphics);
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateGizmoHover(mouseX, mouseY);
        renderOverlay();
    }

    private void drawImGuiUnavailable(PoseStack graphics) {
        drawString(graphics, font, "Spraute Editor: ImGui failed to load", 24, 40, 0xFFFF6666);
        drawString(graphics, font, "Press Esc to close", 24, 56, 0xFFCCCCCC);
        String err = SprauteImGuiBackend.initError();
        if (err != null) {
            int y = 80;
            for (String line : wrapErrorLines(err, width - 48)) {
                drawString(graphics, font, line, 24, y, 0xFFFFAAAA);
                y += 12;
            }
        }
    }

    private void renderCanvasPreviews(PoseStack graphics, float partialTick) {
        var doc = SceneEditorSession.document();
        if (doc == null) {
            org.zonarstudio.spraute_engine.client.SprauteScriptScreen.retainEditorPreviews(java.util.Set.of());
            return;
        }
        var canvases = canvasesByLayer(doc.root);
        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.retainEditorPreviews(
                canvases.stream().map(canvas -> canvas.id).collect(java.util.stream.Collectors.toSet()));
        for (SceneNode canvas : canvases) {
            org.zonarstudio.spraute_engine.client.SprauteScriptScreen
                    .renderEditorPreview(canvas.id,
                            org.zonarstudio.spraute_engine.scene.SceneCanvasBuilder.buildJson(canvas, doc.root),
                            graphics, partialTick);
        }
    }
    *///?}

    private boolean worldViewportActiveAndImGuiOk() {
        return worldTabActive && SprauteImGuiBackend.isAvailable();
    }

    private static java.util.List<String> wrapErrorLines(String err, int maxPx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (err == null || err.isBlank()) return out;
        int maxChars = Math.max(24, maxPx / 6);
        for (String part : err.replace('\r', ' ').split("\\n")) {
            String s = part.trim();
            while (s.length() > maxChars) {
                out.add(s.substring(0, maxChars));
                s = s.substring(maxChars);
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void forEachCanvas(SceneNode node, java.util.function.Consumer<SceneNode> action) {
        if (node.type == org.zonarstudio.spraute_engine.scene.SceneNodeType.CANVAS) action.accept(node);
        for (SceneNode child : node.children) forEachCanvas(child, action);
    }

    /**
     * Active SCREEN-space canvases in draw order: sorted by layer ascending, like at runtime.
     * World-space canvases ({@code canvasWorldSpace}) are excluded — they are rendered as a plane
     * in the 3D world (WorldCanvasEditorPreview / the runtime entity), never flat over the whole
     * viewport. Drawing them here too used to double them up: a correct perspective-warped panel
     * in the world PLUS a flat, undistorted copy stamped over the screen.
     */
    private static java.util.List<SceneNode> canvasesByLayer(SceneNode root) {
        java.util.List<SceneNode> canvases = new java.util.ArrayList<>();
        forEachCanvas(root, canvas -> {
            if (canvas.active && !canvas.canvasWorldSpace) canvases.add(canvas);
        });
        canvases.sort(java.util.Comparator.comparingInt(c -> c.canvasLayer));
        return canvases;
    }

    /** Draws the complete editor while Minecraft owns the mouse in flight mode. */
    public void renderOverlay() {
        SprauteImGuiBackend.render(this::renderImGui);
        SceneEditorSession.tickAutosave();
        AnimatorTimelineSession.tickAutosave();
    }

    private void renderImGui() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY(), ImGuiCond.Always);
        // Reserve space for the status bar instead of drawing it over docked panels.
        ImGui.setNextWindowSize(viewport.getSizeX(),
                Math.max(1f, viewport.getSizeY() - statusBarHeight()), ImGuiCond.Always);
        int hostFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.MenuBar;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.begin("Spraute Engine Dockspace", hostFlags);
        renderMenu();
        int dockId = ImGui.getID("SprauteDockspace");
        SprauteEditorDocking.ensureLayout(dockId);
        ImGui.dockSpace(dockId, 0f, 0f, ImGuiDockNodeFlags.None);
        renderNewScenePopup();
        renderExitConfirmation();
        renderProjectModals();
        ImGui.end();
        ImGui.popStyleVar();

        ImGuiIO io = ImGui.getIO();
        float w = io.getDisplaySizeX();
        float h = io.getDisplaySizeY();
        renderHierarchy(w, h);
        Path createdScript = SceneEditorSession.consumeRequestedScript();
        if (createdScript != null) {
            reloadProject();
            openScript(createdScript);
        }
        Path createdVisual = SceneEditorSession.consumeRequestedVisualScript();
        if (createdVisual != null) {
            reloadProject();
            openVisualScript(createdVisual);
        }
        renderInspector(w, h);
        renderProject(w, h);
        renderTimeline(w, h);
        renderConsole(w, h);
        renderWorkspace(w, h);
        if (org.zonarstudio.spraute_engine.client.editor.scene.PluginManagerPanel.consumeOpenRequest()) {
            showPlugins.set(true);
        }
        org.zonarstudio.spraute_engine.client.editor.scene.PluginManagerPanel.render(showPlugins);
        org.zonarstudio.spraute_engine.client.editor.scene.EditorSettingsPanel.render(showSettings);
        if (org.zonarstudio.spraute_engine.client.editor.scene.ProjectExportPanel.consumeOpenRequest()) {
            showProjectExport.set(true);
        }
        org.zonarstudio.spraute_engine.client.editor.scene.ProjectExportPanel.render(showProjectExport);
        if (org.zonarstudio.spraute_engine.client.editor.scene.SceneExportPanel.consumeOpenRequest()) {
            showSceneExport.set(true);
        }
        org.zonarstudio.spraute_engine.client.editor.scene.SceneExportPanel.render(showSceneExport);
        org.zonarstudio.spraute_engine.client.editor.scene.LocalizationPanel.render(showLocalization);
        renderBlockSyntaxHelp();
        org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.renderHud();
        renderStatusBar(w, h);
    }

    /** Small help window about the .spr visual block DSL. */
    private void renderBlockSyntaxHelp() {
        if (openBlockSyntaxHelp) {
            openBlockSyntaxHelp = false;
            ImGui.openPopup(EditorI18n.t("Синтаксис блоков###block-syntax"));
        }
        ImGui.setNextWindowSize(560f, 0f, imgui.flag.ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(EditorI18n.t("Синтаксис блоков###block-syntax"),
                imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.textWrapped(EditorI18n.t("Блоки описываются в файлах blocks/*.spr плагина (формат Spraute Studio, префикс #\\ необязателен):"));
        ImGui.spacing();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, 0.055f, 0.065f, 0.085f, 1f);
        ImGui.beginChild("##syntax-example", 0f, 210f, true);
        ImGui.textDisabled(EditorI18n.t("""
                block: my_block          — id блока
                category: Моя категория  — раздел палитры
                color: #38bdf8           — цвет
                shape: statement | value | wrapper

                row: "текст" (поле: text: значение) [выбор: dropdown(да: true, нет: false)]
                input: player (type: value)     — сокет для другого блока
                if поле == "значение":          — условные строки

                template: chat("{поле}")        — генерация кода
                code: return '...';             — либо JS-генератор"""));
        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.textDisabled(EditorI18n.t("Полные примеры: spraute_engine/plugins/procode/blocks/*.spr"));
        ImGui.spacing();
        if (ImGui.button(EditorI18n.t("Понятно"), 130f, 0f)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void renderMenu() {
        if (!ImGui.beginMenuBar()) return;
        // The dockspace host uses zero window padding. Override it while menus
        // are built so popup entries are not glued to their left edge.
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12f, 9f);
        ImGui.textColored(0.41f, 0.66f, 1f, 1f, "SPRAUTE");
        ImGui.sameLine(0f, 3f);
        ImGui.textDisabled("ENGINE");
        ImGui.separator();
        if (ImGui.beginMenu(EditorI18n.t("Файл"))) {
            if (ImGui.menuItem(EditorI18n.t("Новая текстура…"))) {
                projectCreateParent = projectRoot().resolve("textures");
                projectCreateKind = "texture";
                projectCreateTexSize = 16;
                projectNameBuf.set("new_texture");
                projectPopupError = "";
                openProjectCreate = true;
            }
            if (ImGui.menuItem(EditorI18n.t("Новая сцена"))) {
                newSceneName.set("");
                openNewScenePopup = true;
            }
            boolean hasScene = SceneEditorSession.document() != null;
            boolean canSave = hasScene && !SceneEditorSession.isLiveRuntime();
            if (ImGui.menuItem(EditorI18n.t("Сохранить сцену"), "Ctrl+S", false, canSave)) {
                SceneEditorSession.save();
                AnimatorTimelineSession.save();
            }
            if (SceneEditorSession.isLiveRuntime()) {
                ImGui.textDisabled(EditorI18n.t("  (сцена запущена — сохранение выкл.)"));
            }
            if (ImGui.menuItem(EditorI18n.t("Закрыть сцену"), "", false, hasScene)) SceneEditorSession.close();
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Плагины…"))) showPlugins.set(true);
            if (ImGui.menuItem(EditorI18n.t("Локализация…"))) showLocalization.set(true);
            if (ImGui.menuItem(EditorI18n.t("Открыть папку плагинов"))) {
                revealInSystemExplorer(
                        org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockRegistry.pluginsDir(), true);
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Экспорт проекта…"))) {
                org.zonarstudio.spraute_engine.client.editor.scene.ProjectExportPanel.requestOpen();
                showProjectExport.set(true);
            }
            if (ImGui.menuItem(EditorI18n.t("Экспорт сюжета как мод…"))) {
                org.zonarstudio.spraute_engine.client.editor.scene.SceneExportPanel.requestOpen();
                showSceneExport.set(true);
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Обновить проект"))) {
                if (RemoteProjectClient.isRemote()) RemoteProjectClient.requestList();
                else reloadProject();
            }
            if (ImGui.menuItem(EditorI18n.t("Сбросить раскладку панелей"))) SprauteEditorDocking.resetLayout();
            if (ImGui.menuItem(EditorI18n.t("Закрыть редактор"), "N")) onClose();
            ImGui.endMenu();
        }
        if (ImGui.beginMenu(EditorI18n.t("Правка"))) {
            if (ImGui.menuItem(EditorI18n.t("Отменить"), "Ctrl+Z", false, org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.canUndo())) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.undo();
            }
            if (ImGui.menuItem(EditorI18n.t("Повторить"), "Ctrl+Y", false, org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.canRedo())) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.redo();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu(EditorI18n.t("Настройки"))) {
            if (ImGui.menuItem(EditorI18n.t("Движок…"), "", showSettings.get())) showSettings.set(true);
            ImGui.endMenu();
        }
        if (ImGui.beginMenu(EditorI18n.t("Окна"))) {
            if (ImGui.menuItem(EditorI18n.t("Иерархия"), "", showHierarchy.get())) showHierarchy.set(!showHierarchy.get());
            if (ImGui.menuItem(EditorI18n.t("Инспектор"), "", showInspector.get())) showInspector.set(!showInspector.get());
            if (ImGui.menuItem(EditorI18n.t("Проект"), "", showProject.get())) showProject.set(!showProject.get());
            if (ImGui.menuItem(EditorI18n.t("Таймлайн"), "", showTimeline.get())) showTimeline.set(!showTimeline.get());
            if (ImGui.menuItem(EditorI18n.t("Консоль"), "", showConsole.get())) showConsole.set(!showConsole.get());
            if (ImGui.menuItem(EditorI18n.t("Плагины"), "", showPlugins.get())) showPlugins.set(!showPlugins.get());
            if (ImGui.menuItem(EditorI18n.t("Локализация"), "", showLocalization.get())) showLocalization.set(!showLocalization.get());
            if (ImGui.menuItem(EditorI18n.t("Настройки"), "", showSettings.get())) showSettings.set(!showSettings.get());
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Показать все"))) {
                showHierarchy.set(true);
                showInspector.set(true);
                showProject.set(true);
                showTimeline.set(true);
                showConsole.set(true);
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu(EditorI18n.t("Справка"))) {
            if (ImGui.menuItem(EditorI18n.t("Менеджер плагинов"))) showPlugins.set(true);
            if (ImGui.menuItem(EditorI18n.t("Как писать блоки (.spr)"))) openBlockSyntaxHelp = true;
            ImGui.separator();
            if (!RemoteProjectClient.isRemote()
                    && ImGui.menuItem(EditorI18n.t("Открыть папку воркспейса"))) {
                revealInSystemExplorer(projectRoot(), true);
            }
            ImGui.endMenu();
        }
        if (SceneEditorSession.document() != null) {
            ImGui.separator();
            if (SceneEditorSession.isLiveRuntime()) {
                EditorIcons.imageInline("radio", 14f, 1f, 0.55f, 0.2f, 1f);
                ImGui.sameLine(0f, 4f);
                ImGui.textColored(1f, 0.55f, 0.2f, 1f, "LIVE");
                ImGui.sameLine(0f, 5f);
            }
            String label = SceneEditorSession.document().id + (SceneEditorSession.isDirty() ? " *" : "");
            ImGui.textColored(0.72f, 0.76f, 0.82f, 1f, "SCENE");
            ImGui.sameLine(0f, 5f);
            ImGui.textColored(0.41f, 0.66f, 1f, 1f, label);
        }
        if (SceneEditorSession.lastError() != null) {
            ImGui.separator();
            ImGui.textColored(1f, 0.4f, 0.4f, 1f, SceneEditorSession.lastError());
        }
        renderMenuScaleControls();
        ImGui.popStyleVar();
        ImGui.endMenuBar();
    }

    private void renderMenuScaleControls() {
        float scale = SprauteImGuiBackend.uiScale();
        String pct = Math.round(scale * 100f) + "%";
        float btn = ImGui.getFrameHeight();
        float gap = ImGui.getStyle().getItemSpacingX();
        float textW = ImGui.calcTextSize(pct).x;
        float total = btn + gap + textW + gap + btn + 10f;
        float x = ImGui.getWindowWidth() - total;
        if (x > ImGui.getCursorPosX() + 16f) {
            ImGui.sameLine(x);
        } else {
            ImGui.sameLine();
        }
        if (ImGui.smallButton("-##menu-ui-scale-dec")) {
            SprauteImGuiBackend.nudgeUiScale(-1);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("Уменьшить интерфейс"));
        }
        ImGui.sameLine(0f, gap);
        ImGui.textDisabled(pct);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("Масштаб интерфейса"));
        }
        ImGui.sameLine(0f, gap);
        if (ImGui.smallButton("+##menu-ui-scale-inc")) {
            SprauteImGuiBackend.nudgeUiScale(1);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("Увеличить интерфейс"));
        }
    }

    private void renderStatusBar(float width, float height) {
        ImGui.setNextWindowPos(0f, height - statusBarHeight(), ImGuiCond.Always);
        ImGui.setNextWindowSize(width, statusBarHeight(), ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoInputs;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 9f, 4f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, .055f, .060f, .069f, .98f);
        ImGui.begin("##spraute-status", flags);
        boolean sceneOpen = SceneEditorSession.document() != null;
        boolean live = SceneEditorSession.isLiveRuntime();
        if (live) {
            ImGui.textColored(1f, 0.55f, 0.2f, 1f, EditorI18n.t("●  СЦЕНА ЗАПУЩЕНА (live)"));
        } else {
            ImGui.textColored(sceneOpen ? .42f : .55f, sceneOpen ? .78f : .57f,
                    sceneOpen ? .64f : .60f, 1f, sceneOpen ? EditorI18n.t("●  СЦЕНА ГОТОВА") : EditorI18n.t("○  СЦЕНА НЕ ОТКРЫТА"));
        }
        ImGui.sameLine();
        ImGui.textDisabled(live
                ? EditorI18n.t("  |  правки не сохраняются   /spraute stop   N: закрыть редактор")
                : EditorI18n.t("  |  N: закрыть редактор   Ctrl+S: сохранить   Ctrl+Z/Ctrl+Y: история"));
        String fps = Math.round(ImGui.getIO().getFramerate()) + " FPS";
        float fpsWidth = ImGui.calcTextSize(fps).x;
        ImGui.sameLine(Math.max(ImGui.getCursorPosX(), ImGui.getWindowWidth() - fpsWidth - 14f));
        ImGui.textDisabled(fps);
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar();
    }

    private void renderNewScenePopup() {
        if (openNewScenePopup) {
            ImGui.openPopup(EditorI18n.t("Новая сцена"));
            openNewScenePopup = false;
        }
        if (EditorPopups.beginModal(EditorI18n.t("Новая сцена"), 360f)) {
            ImGui.text(EditorI18n.t("Идентификатор сцены (имя файла):"));
            ImGui.setNextItemWidth(-1f);
            ImGui.inputText("##scene-name", newSceneName);
            String id = newSceneName.get().trim().replaceAll("[^\\w\\-]", "_");
            if (!id.isEmpty()) ImGui.textDisabled("scenes/" + id + SceneRepository.EXTENSION);
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (id.isEmpty()) ImGui.beginDisabled();
            if (ImGui.button(EditorI18n.t("Создать"), 120f, 0f) && !id.isEmpty()) {
                SceneEditorSession.newScene(id);
                reloadProject();
                ImGui.closeCurrentPopup();
            }
            if (id.isEmpty()) ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) ImGui.closeCurrentPopup();
            EditorPopups.endModal();
        }
    }

    private void renderExitConfirmation() {
        if (openExitConfirmation) {
            ImGui.openPopup(EditorI18n.t("Выход из редактора"));
            openExitConfirmation = false;
        }
        if (!EditorPopups.beginModal(EditorI18n.t("Выход из редактора"), 380f)) return;

        ImGui.text(EditorI18n.t("Вы хотите сохранить сцену?"));
        String sceneId = SceneEditorSession.document() != null
                ? SceneEditorSession.document().id : "";
        if (!sceneId.isBlank()) ImGui.textDisabled(EditorI18n.t("Сцена: ") + sceneId);
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        boolean canSave = !SceneEditorSession.isLiveRuntime();
        if (!canSave) ImGui.beginDisabled();
        if (ImGui.button(EditorI18n.t("Сохранить"), 110f, 0f)) {
            SceneEditorSession.save();
            AnimatorTimelineSession.save();
            exitConfirmed = true;
            ImGui.closeCurrentPopup();
            closeEditorNow();
        }
        if (!canSave) ImGui.endDisabled();

        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Не сохранять"), 120f, 0f)) {
            exitConfirmed = true;
            ImGui.closeCurrentPopup();
            closeEditorNow();
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Отмена"), 90f, 0f)) {
            ImGui.closeCurrentPopup();
        }
        if (!canSave) {
            ImGui.textDisabled(EditorI18n.t("Сцена запущена — сохранение отключено"));
        }
        EditorPopups.endModal();
    }

    private void renderHierarchy(float w, float h) {
        if (!showHierarchy.get()) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (ImGui.begin(EditorI18n.t("Иерархия###Hierarchy"), showHierarchy)) {
            hierarchyPanel.render();
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderInspector(float w, float h) {
        if (!showInspector.get()) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (ImGui.begin(EditorI18n.t("Инспектор###Inspector"), showInspector)) {
            inspectorPanel.render();
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderProject(float w, float h) {
        if (!showProject.get()) {
            projectOsDrop.beginFrame(null);
            return;
        }
        if (HierarchyPanel.projectNeedsReload) {
            HierarchyPanel.projectNeedsReload = false;
            reloadProject();
        }
        projectOsDrop.beginFrame(projectRoot());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (!ImGui.begin(EditorI18n.t("Проект###Project"), showProject)) {
            ImGui.end();
            ImGui.popStyleVar();
            return;
        }
        projectOsDrop.noteWindowBounds(
                ImGui.getWindowPosX(), ImGui.getWindowPosY(),
                ImGui.getWindowPosX() + ImGui.getWindowSizeX(),
                ImGui.getWindowPosY() + ImGui.getWindowSizeY());
        if (EditorIcons.iconButton("refresh-cw", "project-refresh", 16f, EditorI18n.t("Обновить проект"))) {
            if (RemoteProjectClient.isRemote()) RemoteProjectClient.requestList();
            else reloadProject();
            org.zonarstudio.spraute_engine.client.editor.scene.EditorTexturePreview.clear();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("plus", "project-create-btn", 16f, EditorI18n.t("Создать файл / папку"))) {
            projectCreateParent = projectRoot();
            projectCreateKind = "script";
            projectNameBuf.set("new_script");
            projectPopupError = "";
            openProjectCreate = true;
        }
        if (!RemoteProjectClient.isRemote()) {
            ImGui.sameLine();
            if (EditorIcons.iconButton("square-arrow-out-up-right", "project-open-root", 16f,
                    EditorI18n.t("Открыть в проводнике ОС"))) {
                revealInSystemExplorer(projectRoot(), true);
            }
        }
        ImGui.sameLine();
        if (RemoteProjectClient.isRemote()) {
            ImGui.sameLine();
            if (EditorIcons.iconButton("file-input", "project-push-server", 16f,
                    EditorI18n.t("Залить локальные скрипты/сцены на сервер"))) {
                int n = RemoteProjectClient.pushLocalProject(false);
                if (n > 0) {
                    ScriptConsole.info("Project", EditorI18n.t("На сервер залито локальных файлов: ") + n);
                } else {
                    ScriptConsole.info("Project", EditorI18n.t("Нечего заливать — локальных runtime-файлов нет"));
                }
            }
            ImGui.sameLine();
            ImGui.textColored(0.45f, 0.82f, 0.55f, 1f, EditorI18n.t("Сервер"));
            ImGui.sameLine();
            ImGui.textDisabled(EditorI18n.t("файлы dedicated"));
        }
        ImGui.sameLine();
        EditorIcons.image("search", 14f, 0.65f, 0.68f, 0.74f, 1f);
        ImGui.sameLine(0f, 4f);
        ImGui.setNextItemWidth(Math.max(140f, ImGui.getContentRegionAvailX() * .28f));
        ImGui.inputTextWithHint("##project-search", EditorI18n.t("Поиск файлов..."), projectSearch);
        ImGui.sameLine();
        ImGui.textDisabled(scenes.size() + EditorI18n.t(" сцен  /  ") + scripts.size() + EditorI18n.t(" скриптов"));
        String filter = projectSearch.get().trim().toLowerCase(java.util.Locale.ROOT);
        ImGui.spacing();

        Path root = projectRoot();
        if (!RemoteProjectClient.isRemote()) {
            try {
                Files.createDirectories(org.zonarstudio.spraute_engine.scene.PrefabRepository.prefabsDir(
                        Minecraft.getInstance().gameDirectory.toPath()));
            } catch (IOException ignored) {}
        }

        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 26f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6f, 3f);
        if (RemoteProjectClient.isRemote() && RemoteProjectClient.isLoading()) {
            ImGui.textDisabled(EditorI18n.t("Загрузка списка с сервера…"));
        } else if (RemoteProjectClient.isRemote() && !RemoteProjectClient.hasList()) {
            String err = RemoteProjectClient.lastError();
            ImGui.textDisabled(err.isBlank()
                    ? EditorI18n.t("Нет списка с сервера — нажмите обновить")
                    : err);
        } else if (filter.isEmpty()) {
            if (RemoteProjectClient.isRemote()) renderRemoteDir("", 0);
            else renderProjectDir(root, 0);
        } else if (RemoteProjectClient.isRemote()) {
            List<org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.Entry> matches =
                    new ArrayList<>();
            for (var e : RemoteProjectClient.entries()) {
                if (e.dir || isHiddenGeneratedSprRel(e.path)) continue;
                if (e.path.toLowerCase(java.util.Locale.ROOT).contains(filter)) matches.add(e);
                if (matches.size() >= 500) break;
            }
            for (var e : matches) {
                renderProjectFile(projectPath(e.path), e.path);
            }
            if (matches.isEmpty()) ImGui.textDisabled(EditorI18n.t("Ничего не найдено"));
        } else {
            try (var stream = Files.walk(root)) {
                List<Path> matches = stream.filter(Files::isRegularFile)
                        .filter(p -> !isHiddenGeneratedSpr(p))
                        .filter(p -> root.relativize(p).toString().replace('\\', '/')
                                .toLowerCase(java.util.Locale.ROOT).contains(filter))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(500)
                        .toList();
                for (Path file : matches) {
                    renderProjectFile(file, root.relativize(file).toString().replace('\\', '/'));
                }
                if (matches.isEmpty()) ImGui.textDisabled(EditorI18n.t("Ничего не найдено"));
            } catch (IOException e) {
                ImGui.textDisabled(EditorI18n.t("Ошибка чтения: ") + e.getMessage());
            }
        }
        ImGui.popStyleVar(2);

        // Remaining empty space is also a drop target (easy to hit).
        float leftover = ImGui.getContentRegionAvailY();
        if (leftover > 24f) {
            acceptNodeAsPrefabDrop("##project-fill-drop", -1f, leftover);
        }

        projectOsDrop.drawHoverHighlight();

        ImGui.end();
        ImGui.popStyleVar();
    }

    /**
     * Draws a drop target that turns a Hierarchy node payload into a prefab file.
     * Label starting with "##" is drawn as an invisible fill (no text).
     */
    private void acceptNodeAsPrefabDrop(String label, float width, float height) {
        boolean invisible = label.startsWith("##");
        if (invisible) {
            ImGui.invisibleButton(label, width < 0 ? ImGui.getContentRegionAvailX() : width, height);
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.14f, 0.22f, 0.18f, 1f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.18f, 0.32f, 0.24f, 1f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.20f, 0.38f, 0.28f, 1f);
            ImGui.button(label, width, height);
            ImGui.popStyleColor(3);
        }
        if (ImGui.beginDragDropTarget()) {
            String nodeId = ImGui.acceptDragDropPayload(HierarchyPanel.PAYLOAD_TYPE);
            if (nodeId != null) {
                String created = HierarchyPanel.saveNodeAsPrefab(SceneEditorSession.document(), nodeId);
                if (created != null) reloadProject();
            }
            ImGui.endDragDropTarget();
        }
    }

    /** Recursive folder tree of the dedicated-server workspace. */
    private void renderRemoteDir(String parentRel, int depth) {
        List<org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.Entry> kids =
                RemoteProjectClient.children(parentRel);
        List<org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.Entry> dirs = new ArrayList<>();
        List<org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.Entry> files = new ArrayList<>();
        for (var e : kids) {
            if (e.dir) dirs.add(e);
            else if (!isHiddenGeneratedSprRel(e.path)) files.add(e);
        }
        var guideDl = ImGui.getWindowDrawList();
        float guideX = ImGui.getCursorScreenPosX() - 16f;
        float guideTop = ImGui.getCursorScreenPosY() - 1f;
        float guideBottom = guideTop;
        int guideCol = ImGui.getColorU32(1f, 1f, 1f, 0.13f);
        for (var sub : dirs) {
            Path subPath = projectPath(sub.path);
            String folderName = org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.nameOf(sub.path);
            boolean special = EditorIcons.isSpecialProjectFolder(folderName);
            int flags = imgui.flag.ImGuiTreeNodeFlags.OpenOnArrow
                    | imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
            float[] folderTint = EditorIcons.tintForProjectFolder(folderName);
            if (special) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, folderTint[0], folderTint[1], folderTint[2], folderTint[3]);
            }
            boolean open = ImGui.treeNodeEx("     " + folderName + "/###dir-" + sub.path, flags);
            String folderIcon = EditorIcons.forProjectFolder(folderName);
            if (open && "folder".equals(folderIcon)) folderIcon = "folder-open";
            EditorIcons.drawOverTreeLabel(folderIcon, 14f, folderTint);
            projectOsDrop.noteFolderHit(subPath,
                    ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                    ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY());
            if (special) ImGui.popStyleColor();
            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
            beginProjectPathDrag(subPath, folderName + "/", true);
            renderProjectDirContextMenu(subPath);
            acceptProjectFolderDrop(subPath);

            if (open) {
                renderRemoteDir(sub.path, depth + 1);
                ImGui.treePop();
            }
        }
        for (var file : files) {
            Path filePath = projectPath(file.path);
            renderProjectFile(filePath, org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.nameOf(file.path));
            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
        }
        if (depth > 0 && guideBottom > guideTop) {
            guideDl.addLine(guideX, guideTop, guideX, guideBottom, guideCol, 1f);
        }
    }

    /** Recursive folder tree of the whole spraute_engine workspace, with indent guide lines. */
    private void renderProjectDir(Path dir, int depth) {
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .forEach(p -> {
                        if (Files.isDirectory(p)) dirs.add(p);
                        else if (!isHiddenGeneratedSpr(p)) files.add(p);
                    });
        } catch (IOException e) {
            ImGui.textDisabled(EditorI18n.t("Ошибка чтения: ") + e.getMessage());
            return;
        }
        // Indent guides: a vertical trunk at the parent's indent plus a tick to every entry.
        var guideDl = ImGui.getWindowDrawList();
        float guideX = ImGui.getCursorScreenPosX() - 16f;
        float guideTop = ImGui.getCursorScreenPosY() - 1f;
        float guideBottom = guideTop;
        int guideCol = ImGui.getColorU32(1f, 1f, 1f, 0.13f);
        for (Path sub : dirs) {
            String folderName = sub.getFileName().toString();
            boolean special = EditorIcons.isSpecialProjectFolder(folderName);
            int flags = imgui.flag.ImGuiTreeNodeFlags.OpenOnArrow
                    | imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
            float[] folderTint = EditorIcons.tintForProjectFolder(folderName);
            if (special) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, folderTint[0], folderTint[1], folderTint[2], folderTint[3]);
            }
            boolean open = ImGui.treeNodeEx("     " + folderName + "/###dir-" + sub, flags);
            String folderIcon = EditorIcons.forProjectFolder(folderName);
            if (open && "folder".equals(folderIcon)) folderIcon = "folder-open";
            EditorIcons.drawOverTreeLabel(folderIcon, 14f, folderTint);
            projectOsDrop.noteFolderHit(sub,
                    ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                    ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY());
            if (special) ImGui.popStyleColor();
            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
            beginProjectPathDrag(sub, folderName + "/", true);
            renderProjectDirContextMenu(sub);
            acceptProjectFolderDrop(sub);

            if (open) {
                renderProjectDir(sub, depth + 1);
                ImGui.treePop();
            }
        }
        for (Path file : files) {
            renderProjectFile(file, file.getFileName().toString());
            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
        }
        // Vertical trunk from the parent folder down to the last entry.
        if (depth > 0 && guideBottom > guideTop) {
            guideDl.addLine(guideX, guideTop, guideX, guideBottom, guideCol, 1f);
        }
    }

    /** One file row in the project tree: icon, preview tooltip and double-click open. */
    private void renderProjectFile(Path file, String shownName) {
        String kind = projectFileKind(file);

        boolean lockedByOther = RemoteProjectClient.isRemote() && needsExclusiveLock(kind)
                && RemoteProjectClient.isLockedByOther(file);
        String lockName = RemoteProjectClient.isRemote() ? RemoteProjectClient.lockHolder(file) : "";
        boolean isOpen = switch (kind) {
            case "scene" -> file.equals(SceneEditorSession.documentPath());
            case "script", "text", "anim" -> scriptTabs.stream().anyMatch(t -> t.path.equals(file));
            case "vscript" -> visualTabs.stream().anyMatch(t -> t.path.equals(file));
            case "texture" -> textureTabs.stream().anyMatch(t -> t.path.equals(file));
            case "model" -> modelTabs.stream().anyMatch(t -> t.path.equals(file));
            default -> false;
        };
        float[] tint = EditorIcons.tintForProjectFile(kind);
        String label = shownName;
        if (lockedByOther && !lockName.isEmpty()) {
            label = shownName + "  ·  " + lockName;
        }
        if (lockedByOther) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.72f, 0.55f, 0.38f, 1f);
        }
        ImGui.selectable("     " + label + "###file-" + file, isOpen);
        if (lockedByOther) ImGui.popStyleColor();
        EditorIcons.drawOverSelectableLabel(EditorIcons.forProjectFile(kind), 14f, tint);
        projectOsDrop.noteFileHit(file,
                ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY());
        boolean hovered = ImGui.isItemHovered();
        if (hovered && kind.equals("texture")) {
            var tex = org.zonarstudio.spraute_engine.client.editor.scene.EditorTexturePreview.getAbsolute(file);
            if (tex != null) {
                ImGui.beginTooltip();
                float[] size = org.zonarstudio.spraute_engine.client.editor.scene.EditorTexturePreview.fit(tex, 192f, 192f);
                ImGui.image(tex.glId(), size[0], size[1]);
                ImGui.textDisabled(tex.width() + " x " + tex.height() + EditorI18n.t("  ·  ПКМ: меню · двойной клик: открыть · тяни в папку"));
                ImGui.endTooltip();
            }
        } else if (hovered && lockedByOther) {
            ImGui.setTooltip(EditorI18n.t("Открыто: %s", lockName));
        } else if (hovered && !kind.equals("prefab")) {
            ImGui.setTooltip(EditorI18n.t("ПКМ: меню · двойной клик: открыть · тяни в папку"));
        }
        beginProjectPathDrag(file, shownName, false);
        renderProjectFileContextMenu(file, kind);
        // Dropping a Hierarchy node onto an existing prefab file also creates a new prefab
        // (unique name) — convenient when the folder is scrolled out of view.
        if (ImGui.beginDragDropTarget()) {
            String nodeId = ImGui.acceptDragDropPayload(HierarchyPanel.PAYLOAD_TYPE);
            if (nodeId != null) {
                String created = HierarchyPanel.saveNodeAsPrefab(SceneEditorSession.document(), nodeId);
                if (created != null) reloadProject();
            }
            ImGui.endDragDropTarget();
        }
        if (hovered && ImGui.isMouseDoubleClicked(0)) {
            openProjectPath(file, kind);
        }
    }

    private static boolean needsExclusiveLock(String kind) {
        return "script".equals(kind) || "text".equals(kind) || "anim".equals(kind)
                || "vscript".equals(kind) || "texture".equals(kind) || "model".equals(kind);
    }

    private static String projectFileKind(Path file) {
        return EditorIcons.projectFileKind(file);
    }

    private void beginProjectPathDrag(Path path, String preview, boolean folder) {
        if (path == null) return;
        if (folder && isWorkspaceRootFolder(path)) return;
        int flags = folder ? ImGuiDragDropFlags.SourceNoHoldToOpenOthers : 0;
        if (!ImGui.beginDragDropSource(flags)) return;
        String rel = projectRelative(path);
        ImGui.setDragDropPayload(HierarchyPanel.PROJECT_PATH_PAYLOAD, rel);
        ImGui.text(preview == null || preview.isBlank() ? rel : preview);
        ImGui.endDragDropSource();
    }

    private void acceptProjectFolderDrop(Path destDir) {
        if (ImGui.beginDragDropTarget()) {
            String nodeId = ImGui.acceptDragDropPayload(HierarchyPanel.PAYLOAD_TYPE);
            if (nodeId != null) {
                String created = HierarchyPanel.saveNodeAsPrefab(SceneEditorSession.document(), nodeId);
                if (created != null) reloadProject();
            }
            String rel = ImGui.acceptDragDropPayload(HierarchyPanel.PROJECT_PATH_PAYLOAD);
            if (rel != null) moveProjectEntry(rel, destDir);
            ImGui.endDragDropTarget();
        }
    }

    private String projectRelative(Path path) {
        if (path == null) return "";
        if (RemoteProjectClient.isRemote()) {
            String rel = RemoteProjectClient.relativeOf(path);
            return rel == null ? "" : rel.replace('\\', '/');
        }
        try {
            return projectRoot().relativize(path.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return path.getFileName().toString();
        }
    }

    private boolean isWorkspaceRootFolder(Path path) {
        if (path == null) return false;
        Path parent = path.getParent();
        if (parent == null) return false;
        try {
            if (!parent.toAbsolutePath().normalize().equals(projectRoot().toAbsolutePath().normalize())) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.ROOT_DIRS
                .contains(path.getFileName().toString());
    }

    private void moveProjectEntry(String sourceRel, Path destDir) {
        if (sourceRel == null || destDir == null) return;
        String rel = sourceRel.replace('\\', '/').trim();
        if (rel.isEmpty() || rel.contains("..")) return;
        Path root = projectRoot().toAbsolutePath().normalize();
        Path src = root.resolve(rel).normalize();
        if (!src.startsWith(root) || src.equals(root)) return;
        Path destParent = destDir.toAbsolutePath().normalize();
        if (!destParent.startsWith(root)) return;
        if (src.equals(destParent) || destParent.startsWith(src)) return;
        Path srcParent = src.getParent();
        if (srcParent != null && srcParent.equals(destParent)) return;
        Path dest = destParent.resolve(src.getFileName());
        if (RemoteProjectClient.isRemote()) {
            if (RemoteProjectClient.hasPath(projectRelative(dest))) {
                ScriptConsole.warn("Project", EditorI18n.t("Уже существует: ") + dest.getFileName());
                return;
            }
        } else if (Files.exists(dest)) {
            ScriptConsole.warn("Project", EditorI18n.t("Уже существует: ") + dest.getFileName());
            return;
        }
        try {
            if (RemoteProjectClient.isRemote()) {
                RemoteProjectClient.rename(projectRelative(src), projectRelative(dest));
                moveSiblingGeneratedSprRemote(src, dest);
            } else {
                Files.createDirectories(destParent);
                Files.move(src, dest);
                moveSiblingGeneratedSpr(src, dest);
            }
            notifyProjectPathMoved(src, dest);
            reloadProject();
        } catch (IOException e) {
            ScriptConsole.warn("Project", EditorI18n.t("Не удалось переместить: ") + e.getMessage());
        }
    }

    private static void moveSiblingGeneratedSpr(Path from, Path to) {
        String fn = from.getFileName().toString();
        if (!fn.toLowerCase(java.util.Locale.ROOT).endsWith(".sprv")) return;
        Path sprFrom = from.resolveSibling(fn.substring(0, fn.length() - 5) + ".spr");
        Path sprTo = to.resolveSibling(to.getFileName().toString()
                .substring(0, to.getFileName().toString().length() - 5) + ".spr");
        try {
            if (Files.isRegularFile(sprFrom) && !Files.exists(sprTo)) {
                Files.move(sprFrom, sprTo);
            }
        } catch (IOException ignored) {
        }
    }

    private void moveSiblingGeneratedSprRemote(Path from, Path to) {
        String fn = from.getFileName().toString();
        if (!fn.toLowerCase(java.util.Locale.ROOT).endsWith(".sprv")) return;
        Path sprFrom = from.resolveSibling(fn.substring(0, fn.length() - 5) + ".spr");
        Path sprTo = to.resolveSibling(to.getFileName().toString()
                .substring(0, to.getFileName().toString().length() - 5) + ".spr");
        String fromRel = projectRelative(sprFrom);
        String toRel = projectRelative(sprTo);
        if (fromRel.isBlank() || toRel.isBlank()) return;
        if (RemoteProjectClient.hasPath(fromRel) && !RemoteProjectClient.hasPath(toRel)) {
            RemoteProjectClient.rename(fromRel, toRel);
        }
    }

    private void notifyProjectPathMoved(Path from, Path to) {
        retargetOpenTabsDeep(from, to);
        Path scene = SceneEditorSession.documentPath();
        if (scene == null || from == null || to == null) return;
        if (scene.equals(from)) {
            SceneEditorSession.notifyPathRenamed(from, to);
        } else if (scene.startsWith(from)) {
            SceneEditorSession.notifyPathRenamed(scene, to.resolve(from.relativize(scene)));
        }
    }

    private void retargetOpenTabsDeep(Path from, Path to) {
        remapTab(from, to, scriptTabs);
        for (TextureTab tab : textureTabs) {
            Path next = remapPath(from, to, tab.path);
            if (next != null && !next.equals(tab.path)) {
                tab.path = next;
                tab.session.setPath(next);
            }
        }
        for (ModelTab tab : modelTabs) {
            Path next = remapPath(from, to, tab.path);
            if (next != null) tab.path = next;
        }
        for (VisualTab tab : visualTabs) {
            Path next = remapPath(from, to, tab.path);
            if (next != null) tab.path = next;
        }
    }

    private static void remapTab(Path from, Path to, List<ScriptTab> tabs) {
        for (ScriptTab tab : tabs) {
            Path next = remapPath(from, to, tab.path);
            if (next != null) tab.path = next;
        }
    }

    private static Path remapPath(Path from, Path to, Path current) {
        if (from == null || to == null || current == null) return null;
        if (current.equals(from)) return to;
        if (current.startsWith(from)) return to.resolve(from.relativize(current));
        return null;
    }

    private void openProjectPath(Path file, String kind) {
        if ("prefab".equals(kind) && RemoteProjectClient.isRemote()) {
            String rel = RemoteProjectClient.relativeOf(file);
            RemoteProjectClient.ensureLocal(rel, () ->
                    HierarchyPanel.instantiatePrefab(prefabNameFromPath(file),
                            SceneEditorSession.selectedNodeId()));
            return;
        }
        if (isProjectFileOpen(file, kind)) {
            if (RemoteProjectClient.isRemote()) {
                RemoteProjectClient.ensureLocal(RemoteProjectClient.relativeOf(file),
                        () -> openProjectPathLocal(file, kind));
            } else {
                openProjectPathLocal(file, kind);
            }
            return;
        }
        if (RemoteProjectClient.isRemote() && needsExclusiveLock(kind)) {
            String rel = RemoteProjectClient.relativeOf(file);
            RemoteProjectClient.requestLock(rel, ok -> {
                if (!ok) {
                    ScriptConsole.warn("Project", EditorI18n.t("Файл уже открыт у %s",
                            RemoteProjectClient.lockHolder(file)));
                    return;
                }
                RemoteProjectClient.ensureLocal(rel, () -> openProjectPathLocal(file, kind));
            });
            return;
        }
        if (RemoteProjectClient.isRemote()) {
            RemoteProjectClient.ensureLocal(RemoteProjectClient.relativeOf(file),
                    () -> openProjectPathLocal(file, kind));
            return;
        }
        openProjectPathLocal(file, kind);
    }

    private boolean isProjectFileOpen(Path file, String kind) {
        return switch (kind) {
            case "scene" -> file.equals(SceneEditorSession.documentPath());
            case "script", "text", "anim" -> scriptTabs.stream().anyMatch(t -> t.path.equals(file));
            case "vscript" -> visualTabs.stream().anyMatch(t -> t.path.equals(file));
            case "texture" -> textureTabs.stream().anyMatch(t -> t.path.equals(file));
            case "model" -> modelTabs.stream().anyMatch(t -> t.path.equals(file));
            default -> false;
        };
    }

    private void openProjectPathLocal(Path file, String kind) {
        switch (kind) {
            case "scene" -> SceneEditorSession.open(file);
            case "script", "text", "anim" -> openScript(file);
            case "vscript" -> openVisualScript(file);
            case "texture" -> openTexture(file);
            case "model" -> openModel(file);
            case "prefab" -> HierarchyPanel.instantiatePrefab(prefabNameFromPath(file),
                    SceneEditorSession.selectedNodeId());
            default -> {}
        }
    }

    private void renderProjectFileContextMenu(Path file, String tag) {
        if (!ImGui.beginPopupContextItem("##proj-file-ctx-" + file)) return;
        ImGui.textDisabled(file.getFileName().toString());
        ImGui.separator();
        if (ImGui.menuItem(EditorI18n.t("Открыть"))) {
            openProjectPath(file, tag);
        }
        if (ImGui.menuItem(EditorI18n.t("Переименовать"))) {
            projectCtxPath = file;
            projectCtxIsDir = false;
            projectNameBuf.set(file.getFileName().toString());
            projectPopupError = "";
            openProjectRename = true;
        }
        if (ImGui.menuItem(EditorI18n.t("Удалить"))) {
            projectCtxPath = file;
            projectCtxIsDir = false;
            openProjectDelete = true;
            projectPopupError = "";
        }
        ImGui.separator();
        if (!RemoteProjectClient.isRemote() && ImGui.menuItem(EditorI18n.t("Показать в проводнике"))) {
            revealInSystemExplorer(file, false);
        }
        ImGui.endPopup();
    }

    private void renderProjectDirContextMenu(Path dir) {
        if (!ImGui.beginPopupContextItem("##proj-dir-ctx-" + dir)) return;
        ImGui.textDisabled(dir.getFileName() + "/");
        ImGui.separator();
        if (ImGui.menuItem(EditorI18n.t("Новый скрипт…"))) {
            projectCreateParent = dir;
            projectCreateKind = "script";
            projectNameBuf.set("new_script");
            projectPopupError = "";
            openProjectCreate = true;
        }
        if (ImGui.menuItem(EditorI18n.t("Новый визуальный скрипт…"))) {
            projectCreateParent = dir;
            projectCreateKind = "vscript";
            projectNameBuf.set("new_visual");
            projectPopupError = "";
            openProjectCreate = true;
        }
        if (ImGui.menuItem(EditorI18n.t("Новый текстовый файл…"))) {
            projectCreateParent = dir;
            projectCreateKind = "text";
            projectNameBuf.set("notes.txt");
            projectPopupError = "";
            openProjectCreate = true;
        }
        if (ImGui.menuItem(EditorI18n.t("Новая папка…"))) {
            projectCreateParent = dir;
            projectCreateKind = "folder";
            projectNameBuf.set("new_folder");
            projectPopupError = "";
            openProjectCreate = true;
        }
        if (ImGui.menuItem(EditorI18n.t("Новая текстура…"))) {
            projectCreateParent = dir;
            projectCreateKind = "texture";
            projectCreateTexSize = 16;
            projectNameBuf.set("new_texture");
            projectPopupError = "";
            openProjectCreate = true;
        }
        boolean isRoot = dir.equals(projectRoot());
        if (!isRoot) {
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Переименовать"))) {
                projectCtxPath = dir;
                projectCtxIsDir = true;
                projectNameBuf.set(dir.getFileName().toString());
                projectPopupError = "";
                openProjectRename = true;
            }
            if (ImGui.menuItem(EditorI18n.t("Удалить папку"))) {
                projectCtxPath = dir;
                projectCtxIsDir = true;
                openProjectDelete = true;
                projectPopupError = "";
            }
        }
        ImGui.separator();
        if (!RemoteProjectClient.isRemote() && ImGui.menuItem(EditorI18n.t("Открыть в проводнике"))) {
            revealInSystemExplorer(dir, true);
        }
        ImGui.endPopup();
    }

    private void renderProjectModals() {
        if (openProjectRename) {
            ImGui.openPopup(EditorI18n.t("Переименовать###ProjectRename"));
            openProjectRename = false;
        }
        if (openProjectDelete) {
            ImGui.openPopup(EditorI18n.t("Удалить###ProjectDelete"));
            openProjectDelete = false;
        }
        if (openProjectCreate) {
            ImGui.openPopup(EditorI18n.t("Создать###ProjectCreate"));
            openProjectCreate = false;
        }

        if (EditorPopups.beginModal(EditorI18n.t("Переименовать###ProjectRename"), 380f)) {
            ImGui.text(EditorI18n.t("Новое имя:"));
            ImGui.setNextItemWidth(-1f);
            ImGui.inputText("##proj-rename", projectNameBuf);
            if (!projectPopupError.isEmpty()) {
                ImGui.textColored(1f, 0.45f, 0.4f, 1f, projectPopupError);
            }
            if (ImGui.button("OK", 100f, 0f) || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter)) {
                if (applyProjectRename()) ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) ImGui.closeCurrentPopup();
            EditorPopups.endModal();
        }

        if (EditorPopups.beginModal(EditorI18n.t("Удалить###ProjectDelete"), 400f)) {
            String name = projectCtxPath != null ? projectCtxPath.getFileName().toString() : "?";
            ImGui.textWrapped(projectCtxIsDir
                    ? EditorI18n.t("Удалить папку «") + name + EditorI18n.t("» и всё содержимое?")
                    : EditorI18n.t("Удалить файл «") + name + "»?");
            if (!projectPopupError.isEmpty()) {
                ImGui.textColored(1f, 0.45f, 0.4f, 1f, projectPopupError);
            }
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.55f, 0.18f, 0.16f, 1f);
            if (ImGui.button(EditorI18n.t("Удалить"), 100f, 0f)) {
                if (applyProjectDelete()) ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor();
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) ImGui.closeCurrentPopup();
            EditorPopups.endModal();
        }

        if (EditorPopups.beginModal(EditorI18n.t("Создать###ProjectCreate"), 400f)) {
            ImGui.text(EditorI18n.t("Тип:"));
            if (ImGui.radioButton(EditorI18n.t("Скрипт (.spr)"), "script".equals(projectCreateKind))) {
                projectCreateKind = "script";
                if (!projectNameBuf.get().endsWith(".spr")) projectNameBuf.set("new_script");
            }
            ImGui.sameLine();
            if (ImGui.radioButton(EditorI18n.t("Визуальный (.sprv)"), "vscript".equals(projectCreateKind))) {
                projectCreateKind = "vscript";
                projectNameBuf.set("new_visual");
            }
            ImGui.sameLine();
            if (ImGui.radioButton(EditorI18n.t("Текст"), "text".equals(projectCreateKind))) {
                projectCreateKind = "text";
                projectNameBuf.set("notes.txt");
            }
            ImGui.sameLine();
            if (ImGui.radioButton(EditorI18n.t("Папка"), "folder".equals(projectCreateKind))) {
                projectCreateKind = "folder";
                projectNameBuf.set("new_folder");
            }
            ImGui.sameLine();
            if (ImGui.radioButton(EditorI18n.t("Сцена"), "scene".equals(projectCreateKind))) {
                projectCreateKind = "scene";
                projectNameBuf.set("new_scene");
            }
            ImGui.sameLine();
            if (ImGui.radioButton(EditorI18n.t("Текстура (.png)"), "texture".equals(projectCreateKind))) {
                projectCreateKind = "texture";
                if (!projectNameBuf.get().endsWith(".png")) projectNameBuf.set("new_texture");
            }
            Path parent = projectCreateParent != null ? projectCreateParent : projectRoot();
            if (("script".equals(projectCreateKind) || "vscript".equals(projectCreateKind))
                    && !parent.toString().replace('\\', '/').contains("/scripts")) {
                parent = projectRoot().resolve("scripts");
            }
            if ("scene".equals(projectCreateKind)) {
                parent = SceneRepository.scenesDir(Minecraft.getInstance().gameDirectory.toPath());
            }
            if ("texture".equals(projectCreateKind)
                    && !parent.toString().replace('\\', '/').contains("/textures")) {
                parent = projectRoot().resolve("textures");
            }
            if ("texture".equals(projectCreateKind)) {
                ImGui.text(EditorI18n.t("Размер"));
                ImGui.sameLine();
                for (int size : org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.sizePresets()) {
                    if (ImGui.radioButton(size + "px##texsz", projectCreateTexSize == size)) {
                        projectCreateTexSize = size;
                    }
                    ImGui.sameLine();
                }
                ImGui.newLine();
            }
            ImGui.textDisabled(EditorI18n.t("Папка: ") + projectRoot().relativize(parent).toString().replace('\\', '/'));
            ImGui.text(EditorI18n.t("Имя:"));
            ImGui.setNextItemWidth(-1f);
            ImGui.inputText("##proj-create-name", projectNameBuf);
            if (!projectPopupError.isEmpty()) {
                ImGui.textColored(1f, 0.45f, 0.4f, 1f, projectPopupError);
            }
            if (ImGui.button(EditorI18n.t("Создать"), 100f, 0f) || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter)) {
                if (applyProjectCreate()) ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) ImGui.closeCurrentPopup();
            EditorPopups.endModal();
        }
    }

    private boolean applyProjectRename() {
        if (projectCtxPath == null) return true;
        String name = sanitizeFileName(projectNameBuf.get());
        if (name.isBlank()) {
            projectPopupError = EditorI18n.t("Имя пустое");
            return false;
        }
        Path target = projectCtxPath.getParent().resolve(name);
        if (RemoteProjectClient.isRemote()) {
            if (RemoteProjectClient.hasPath(RemoteProjectClient.relativeOf(target))) {
                projectPopupError = EditorI18n.t("Уже существует: ") + name;
                return false;
            }
        } else if (Files.exists(target)) {
            projectPopupError = EditorI18n.t("Уже существует: ") + name;
            return false;
        }
        try {
            if (RemoteProjectClient.isRemote()) {
                RemoteProjectClient.rename(
                        RemoteProjectClient.relativeOf(projectCtxPath),
                        RemoteProjectClient.relativeOf(target));
            } else {
                Files.move(projectCtxPath, target);
            }
            retargetOpenTabs(projectCtxPath, target);
            SceneEditorSession.notifyPathRenamed(projectCtxPath, target);
            reloadProject();
            projectCtxPath = target;
            return true;
        } catch (IOException e) {
            projectPopupError = e.getMessage();
            return false;
        }
    }

    private boolean applyProjectDelete() {
        if (projectCtxPath == null) return true;
        if (projectCtxPath.equals(projectRoot())) {
            projectPopupError = EditorI18n.t("Корень проекта удалить нельзя");
            return false;
        }
        try {
            if (RemoteProjectClient.isRemote()) {
                if (projectCtxIsDir) {
                    try (var walk = Files.walk(projectCtxPath)) {
                        List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
                        for (Path p : all) {
                            if (Files.isRegularFile(p)) closeTabsForPath(p);
                        }
                    } catch (IOException ignored) {
                    }
                } else {
                    closeTabsForPath(projectCtxPath);
                }
                RemoteProjectClient.delete(RemoteProjectClient.relativeOf(projectCtxPath));
            } else if (projectCtxIsDir) {
                try (var walk = Files.walk(projectCtxPath)) {
                    List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
                    for (Path p : all) {
                        if (Files.isRegularFile(p)) closeTabsForPath(p);
                        Files.deleteIfExists(p);
                    }
                }
            } else {
                closeTabsForPath(projectCtxPath);
                Files.deleteIfExists(projectCtxPath);
            }
            if (projectCtxPath.equals(SceneEditorSession.documentPath())
                    || (projectCtxIsDir && SceneEditorSession.documentPath() != null
                    && SceneEditorSession.documentPath().startsWith(projectCtxPath))) {
                SceneEditorSession.close();
            }
            reloadProject();
            return true;
        } catch (IOException e) {
            projectPopupError = e.getMessage();
            return false;
        }
    }

    private boolean applyProjectCreate() {
        String raw = sanitizeFileName(projectNameBuf.get());
        if (raw.isBlank()) {
            projectPopupError = EditorI18n.t("Имя пустое");
            return false;
        }
        Path parent = projectCreateParent != null ? projectCreateParent : projectRoot();
        try {
            Files.createDirectories(parent);
            Path created;
            switch (projectCreateKind) {
                case "folder" -> {
                    created = parent.resolve(raw);
                    Files.createDirectories(created);
                }
                case "scene" -> {
                    Path scenesDir = SceneRepository.scenesDir(Minecraft.getInstance().gameDirectory.toPath());
                    Files.createDirectories(scenesDir);
                    String id = raw.endsWith(SceneRepository.EXTENSION)
                            ? raw.substring(0, raw.length() - SceneRepository.EXTENSION.length()) : raw;
                    id = id.replaceAll("[^\\w\\-]", "_");
                    created = SceneRepository.scenePath(Minecraft.getInstance().gameDirectory.toPath(), id);
                    if (Files.exists(created)) {
                        projectPopupError = EditorI18n.t("Сцена уже есть");
                        return false;
                    }
                    var doc = org.zonarstudio.spraute_engine.scene.SceneDocument.create(id);
                    SceneRepository.saveAtomic(doc, created);
                }
                case "vscript" -> {
                    Path scriptsDir = parent;
                    if (!scriptsDir.toAbsolutePath().normalize().startsWith(
                            projectRoot().resolve("scripts").toAbsolutePath().normalize())) {
                        scriptsDir = projectRoot().resolve("scripts");
                        Files.createDirectories(scriptsDir);
                    }
                    String name = raw.endsWith(".sprv") ? raw : raw + ".sprv";
                    created = scriptsDir.resolve(name);
                    if (Files.exists(created)) {
                        projectPopupError = EditorI18n.t("Файл уже есть");
                        return false;
                    }
                    Files.writeString(created, "{\"version\":1,\"blocks\":[]}",
                            java.nio.charset.StandardCharsets.UTF_8);
                    org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor.ensureRuntimeSpr(created);
                    openVisualScript(created);
                }
                case "text" -> {
                    String name = raw.contains(".") ? raw : raw + ".txt";
                    created = parent.resolve(name);
                    if (Files.exists(created)) {
                        projectPopupError = EditorI18n.t("Файл уже есть");
                        return false;
                    }
                    Files.writeString(created, "", java.nio.charset.StandardCharsets.UTF_8);
                }
                case "texture" -> {
                    Path texDir = parent;
                    if (!texDir.toAbsolutePath().normalize().startsWith(
                            projectRoot().resolve("textures").toAbsolutePath().normalize())) {
                        texDir = projectRoot().resolve("textures");
                        Files.createDirectories(texDir);
                    }
                    String name = raw.toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? raw : raw + ".png";
                    created = texDir.resolve(name);
                    if (Files.exists(created)) {
                        projectPopupError = EditorI18n.t("Текстура уже есть");
                        return false;
                    }
                    org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument
                            .writeBlank(created, projectCreateTexSize);
                    openTexture(created);
                }
                default -> { // script
                    Path scriptsDir = parent;
                    if (!scriptsDir.toAbsolutePath().normalize().startsWith(
                            projectRoot().resolve("scripts").toAbsolutePath().normalize())) {
                        scriptsDir = projectRoot().resolve("scripts");
                        Files.createDirectories(scriptsDir);
                    }
                    String name = raw.endsWith(".spr") ? raw : raw + ".spr";
                    created = scriptsDir.resolve(name);
                    if (Files.exists(created)) {
                        projectPopupError = EditorI18n.t("Скрипт уже есть");
                        return false;
                    }
                    Files.writeString(created, "# " + name + "\n", java.nio.charset.StandardCharsets.UTF_8);
                    openScript(created);
                }
            }
            if (RemoteProjectClient.isRemote()) {
                if ("folder".equals(projectCreateKind)) {
                    String rel = RemoteProjectClient.relativeOf(created);
                    if (org.zonarstudio.spraute_engine.resource.WorkspaceBrowserRules.sanitize(rel, true) == null) {
                        projectPopupError = EditorI18n.t("Папка должна быть внутри scripts, geo, textures…");
                        return false;
                    }
                    RemoteProjectClient.mkdir(rel);
                } else {
                    RemoteProjectClient.pushIfRemote(created);
                    if ("vscript".equals(projectCreateKind)) {
                        Path spr = org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor.ensureRuntimeSpr(created);
                        RemoteProjectClient.pushIfRemote(spr);
                    }
                    if ("texture".equals(projectCreateKind)) {
                        RemoteProjectClient.pushIfRemote(
                                org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.sidecar(created));
                    }
                }
            }
            reloadProject();
            return true;
        } catch (IOException e) {
            projectPopupError = e.getMessage();
            return false;
        }
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_");
    }

    private void retargetOpenTabs(Path from, Path to) {
        for (ScriptTab tab : scriptTabs) {
            if (tab.path.equals(from)) tab.path = to;
        }
        for (TextureTab tab : textureTabs) {
            if (tab.path.equals(from)) {
                tab.path = to;
                tab.session.setPath(to);
            }
        }
        for (ModelTab tab : modelTabs) {
            if (tab.path.equals(from)) tab.path = to;
        }
        for (VisualTab tab : visualTabs) {
            if (tab.path.equals(from)) tab.path = to;
        }
    }

    private void closeTabsForPath(Path path) {
        scriptTabs.removeIf(t -> t.path.equals(path));
        textureTabs.removeIf(t -> {
            if (t.path.equals(path)) {
                if (t.session.isDirty()) t.session.save();
                t.session.dispose();
                return true;
            }
            return false;
        });
        modelTabs.removeIf(t -> t.path.equals(path));
        visualTabs.removeIf(t -> {
            if (t.path.equals(path)) {
                t.editor.dispose();
                return true;
            }
            return false;
        });
        RemoteProjectClient.unlock(path);
    }

    private static void revealInSystemExplorer(Path path, boolean folder) {
        if (path == null) return;
        try {
            Path target = path.toAbsolutePath().normalize();
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) {
                if (folder || Files.isDirectory(target)) {
                    new ProcessBuilder("explorer.exe", target.toString()).start();
                } else {
                    new ProcessBuilder("explorer.exe", "/select,", target.toString()).start();
                }
            } else if (os.contains("mac")) {
                Path open = folder || Files.isDirectory(target) ? target : target.getParent();
                new ProcessBuilder("open", open.toString()).start();
            } else {
                Path open = folder || Files.isDirectory(target) ? target : target.getParent();
                new ProcessBuilder("xdg-open", open.toString()).start();
            }
        } catch (Exception ignored) {
        }
    }

    private static String prefabNameFromPath(Path file) {
        String fn = file.getFileName().toString();
        return fn.endsWith(org.zonarstudio.spraute_engine.scene.PrefabRepository.EXTENSION)
                ? fn.substring(0, fn.length() - org.zonarstudio.spraute_engine.scene.PrefabRepository.EXTENSION.length())
                : fn;
    }

    private void renderTimeline(float w, float h) {
        AnimatorTimelineSession.setTimelineFocused(false);
        if (!showTimeline.get()) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (ImGui.begin(EditorI18n.t("Таймлайн###Timeline"), showTimeline)) {
            AnimatorTimelineSession.setTimelineFocused(
                    ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows));
            timelinePanel.render();
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderConsole(float w, float h) {
        if (!showConsole.get()) return;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (ImGui.begin(EditorI18n.t("Консоль###Console"), showConsole)) {
            consolePanel.render();
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderWorkspace(float w, float h) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.begin(EditorI18n.t("Сцена###Workspace"));

        if (ImGui.beginTabBar("main-workspace-tabs")) {
            // World is the permanent main tab: no ImBoolean means there is no close button.
            if (ImGui.beginTabItem("World")) {
                worldTabActive = true;
                renderWorldTab();
                ImGui.endTabItem();
            } else {
                worldTabActive = false;
            }

            List<ScriptTab> closed = new ArrayList<>();
            for (ScriptTab tab : scriptTabs) {
                int flags = tab.path.equals(requestedScriptTab) ? ImGuiTabItemFlags.SetSelected : 0;
                if (ImGui.beginTabItem(tab.path.getFileName().toString() + "###" + tab.path,
                        tab.open, flags)) {
                    renderScriptTab(tab);
                    ImGui.endTabItem();
                }
                if (!tab.open.get()) closed.add(tab);
            }
            scriptTabs.removeAll(closed);
            for (ScriptTab tab : closed) {
                RemoteProjectClient.unlock(tab.path);
            }
            requestedScriptTab = null;

            List<VisualTab> closedVisual = new ArrayList<>();
            boolean visualTabOpen = false;
            for (VisualTab tab : visualTabs) {
                String title = tab.path.getFileName().toString()
                        + (tab.editor.isDirty() ? " *" : "") + "###vs-" + tab.path;
                if (ImGui.beginTabItem(title, tab.open, 0)) {
                    visualTabOpen = true;
                    // Hotkeys/clipboard are claimed inside render() only while focused,
                    // so a selected hierarchy node can still be deleted/undone.
                    tab.editor.render();
                    ImGui.endTabItem();
                }
                if (!tab.open.get()) closedVisual.add(tab);
            }
            for (VisualTab tab : closedVisual) {
                tab.editor.dispose();
                RemoteProjectClient.unlock(tab.path);
            }
            visualTabs.removeAll(closedVisual);
            if (!visualTabOpen) {
                org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor
                        .clearHotkeyOwner();
            }

            List<TextureTab> closedTex = new ArrayList<>();
            boolean textureTabOpen = false;
            for (TextureTab tab : textureTabs) {
                String title = tab.path.getFileName().toString()
                        + (tab.session.isDirty() ? " *" : "") + "###tex-" + tab.path;
                if (ImGui.beginTabItem(title, tab.open, 0)) {
                    textureTabOpen = true;
                    renderTextureTab(tab);
                    ImGui.endTabItem();
                }
                if (!tab.open.get()) closedTex.add(tab);
            }
            textureTabs.removeAll(closedTex);
            for (TextureTab tab : closedTex) {
                if (tab.session.isDirty()) tab.session.save();
                tab.session.dispose();
                RemoteProjectClient.unlock(tab.path);
            }
            if (!textureTabOpen) {
                org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.clearHotkeyOwner();
            }

            List<ModelTab> closedModels = new ArrayList<>();
            for (ModelTab tab : modelTabs) {
                if (ImGui.beginTabItem(tab.path.getFileName().toString() + "###model-" + tab.path, tab.open, 0)) {
                    renderModelTab(tab);
                    ImGui.endTabItem();
                }
                if (!tab.open.get()) closedModels.add(tab);
            }
            modelTabs.removeAll(closedModels);
            for (ModelTab tab : closedModels) {
                RemoteProjectClient.unlock(tab.path);
            }
            ImGui.endTabBar();
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void renderWorldTab() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.105f, 0.115f, 0.132f, 0.96f);
        ImGui.beginChild("##scene-toolbar", 0f, 72f, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        SceneNode selected = SceneEditorSession.selectedNode();
        ImGui.textDisabled(SceneEditorSession.document() == null ? EditorI18n.t("СЦЕНА НЕ ОТКРЫТА")
                : SceneEditorSession.document().id.toUpperCase(java.util.Locale.ROOT));
        if (SceneEditorSession.isLiveRuntime()) {
            ImGui.sameLine();
            ImGui.textColored(1f, 0.55f, 0.2f, 1f, EditorI18n.t("  ● ЗАПУЩЕНА"));
            ImGui.sameLine();
            ImGui.textDisabled(EditorI18n.t("(live · не сохраняется)"));
        }
        if (selected != null) {
            ImGui.sameLine();
            ImGui.textDisabled(" / ");
            ImGui.sameLine();
            ImGui.textColored(.86f, .88f, .92f, 1f, selected.name);
        }
        ImGui.spacing();
        if (EditorIcons.iconToggleButton("mouse-pointer-2", "gizmo-select", 18f, EditorI18n.t("Выбор  [Q]"),
                EditorGizmo.mode() == EditorGizmo.Mode.SELECT)) {
            EditorGizmo.setMode(EditorGizmo.Mode.SELECT);
        }
        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("move", "gizmo-move", 18f, EditorI18n.t("Перемещение  [W]"),
                EditorGizmo.mode() == EditorGizmo.Mode.MOVE)) {
            EditorGizmo.setMode(EditorGizmo.Mode.MOVE);
        }
        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("rotate-3d", "gizmo-rotate", 18f, EditorI18n.t("Поворот  [E]"),
                EditorGizmo.mode() == EditorGizmo.Mode.ROTATE)) {
            EditorGizmo.setMode(EditorGizmo.Mode.ROTATE);
        }
        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("scale-3d", "gizmo-scale", 18f, EditorI18n.t("Масштаб  [R]"),
                EditorGizmo.mode() == EditorGizmo.Mode.SCALE)) {
            EditorGizmo.setMode(EditorGizmo.Mode.SCALE);
        }
        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("move-3d", "gizmo-local", 18f, EditorI18n.t("Локальные оси"),
                EditorGizmo.isLocalSpace())) {
            EditorGizmo.setLocalSpace(!EditorGizmo.isLocalSpace());
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("eye", "camera-capture", 18f, EditorI18n.t("Камера  [F]"),
                0.55f, 0.78f, 1f, 1f)) {
            EditorViewportInput.toggleCapture();
        }
        ImGui.sameLine();
        EditorGrid.renderToolbarControls();
        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.textDisabled(viewportHintText());
        float availW = Math.max(4f, ImGui.getContentRegionAvailX());
        float availH = Math.max(4f, ImGui.getContentRegionAvailY());
        float sourceAspect = EditorWorldViewport.sourceAspect();
        // "Contain": keep the complete world frame visible when the dock is resized.
        float imageW = availW;
        float imageH = imageW / sourceAspect;
        if (imageH > availH) {
            imageH = availH;
            imageW = imageH * sourceAspect;
        }
        float offsetX = Math.max(0f, (availW - imageW) * 0.5f);
        float offsetY = Math.max(0f, (availH - imageH) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + offsetY);

        worldViewportMinX = ImGui.getCursorScreenPosX();
        worldViewportMinY = ImGui.getCursorScreenPosY();
        worldViewportMaxX = worldViewportMinX + imageW;
        worldViewportMaxY = worldViewportMinY + imageH;
        // ImGui uses window pixels, while Screen mouse callbacks use GUI-scaled coordinates.
        float guiScaleX = (float) Minecraft.getInstance().getWindow().getGuiScaledWidth()
                / Math.max(1, Minecraft.getInstance().getWindow().getScreenWidth());
        float guiScaleY = (float) Minecraft.getInstance().getWindow().getGuiScaledHeight()
                / Math.max(1, Minecraft.getInstance().getWindow().getScreenHeight());
        float guiViewportX = worldViewportMinX * guiScaleX;
        float guiViewportY = worldViewportMinY * guiScaleY;
        float guiViewportW = imageW * guiScaleX;
        float guiViewportH = imageH * guiScaleY;
        EditorViewportInput.setViewportRect(guiViewportX, guiViewportY, guiViewportW, guiViewportH);
        EditorGizmo.setViewportRect(guiViewportX, guiViewportY, guiViewportW, guiViewportH);

        EditorWorldViewport.requestCapture(Math.max(4, (int) imageW), Math.max(4, (int) imageH));
        int tex = EditorWorldViewport.textureId();
        if (tex > 0) {
            ImGui.image(tex, imageW, imageH, 0f, 1f, 1f, 0f);
        } else {
            ImGui.dummy(imageW, imageH);
        }
        org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.drawOnViewport(
                worldViewportMinX, worldViewportMinY, worldViewportMaxX, worldViewportMaxY);
        // GUI canvases live on top of the world image; when the mouse works
        // with a widget or its gizmo, the world picking below must not run.
        boolean canvasConsumed = org.zonarstudio.spraute_engine.client.editor.scene.CanvasViewportEditor
                .render(worldViewportMinX, worldViewportMinY, worldViewportMaxX, worldViewportMaxY);
        // World-space canvases are edited on their own plane in the world, not over the viewport.
        // Non-short-circuit: both overlays must draw, whoever ends up taking the mouse.
        canvasConsumed |= org.zonarstudio.spraute_engine.client.editor.scene.WorldCanvasViewportEditor
                .render(worldViewportMinX, worldViewportMinY, worldViewportMaxX, worldViewportMaxY);
        if (EditorMarqueeSelect.isActive()) {
            EditorMarqueeSelect.update(lastMouseX, lastMouseY);
        }
        if (!canvasConsumed && EditorViewportInput.interactionAllowed()
                && !EditorMarqueeSelect.isActive()
                && ImGui.isItemHovered() && ImGui.isMouseClicked(0) && !EditorGizmo.isDragging()) {
            if (!EditorGizmo.beginDrag(lastMouseX, lastMouseY)) {
                String picked = EditorGizmo.pickNode(lastMouseX, lastMouseY);
                boolean ctrl = ImGui.getIO().getKeyCtrl();
                String bone = ImGui.isMouseDoubleClicked(0)
                        ? EditorBonePicker.pickBone(picked, lastMouseX, lastMouseY) : null;
                if (bone != null && picked != null) {
                    SceneEditorSession.select(picked);
                    org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(
                            SceneEditorSession.selectedNode(), bone);
                } else if (picked != null) {
                    if (ctrl) SceneEditorSession.toggleSelect(picked);
                    else SceneEditorSession.select(picked);
                    org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
                } else if (EditorGizmo.mode() == EditorGizmo.Mode.SELECT) {
                    // Select tool: marquee / clear — never steal the cursor into fly-cam.
                    EditorMarqueeSelect.begin(lastMouseX, lastMouseY, ctrl);
                } else if (!ctrl) {
                    // Move/Rotate/Scale: empty world click grabs the camera.
                    SceneEditorSession.select(null);
                    org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
                    EditorViewportInput.beginCapture();
                }
            }
        }
        EditorMarqueeSelect.draw(guiScaleX, guiScaleY);
    }

    private static String viewportHintText() {
        if (org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.isActive()) {
            return org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.hudHintLine();
        }
        if (EditorViewportInput.isCaptured()) {
            return EditorI18n.t("Управление камерой — колесо: скорость   Esc: курсор")
                    + "  ×" + formatFlySpeed(EditorViewportInput.speedScale());
        }
        if (EditorGizmo.mode() == EditorGizmo.Mode.SELECT) {
            return EditorI18n.t("Выбор: ЛКМ — нода   тяни — рамка   Ctrl — добавить   F — камера");
        }
        return EditorI18n.t("ЛКМ по миру: управление камерой   ЛКМ по NPC: выбрать");
    }

    private static String formatFlySpeed(float scale) {
        if (Math.abs(scale - Math.round(scale)) < 0.05f) {
            return Integer.toString(Math.round(scale));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", scale);
    }

    private void updateGizmoHover(double mouseX, double mouseY) {
        EditorGizmo.updateHover(mouseX, mouseY);
    }

    private String scriptNameFromPath(Path script) {
        Path scriptsRoot = projectRoot().resolve("scripts");
        String name = scriptsRoot.relativize(script).toString().replace('\\', '/');
        if (name.endsWith(".sprv")) name = name.substring(0, name.length() - 5);
        else if (name.endsWith(".spr")) name = name.substring(0, name.length() - 4);
        if (name.startsWith("plugins/")) {
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
        }
        return name;
    }

    /** Hide generated .spr when a sibling .sprv exists — user works with the visual file. */
    private static boolean isHiddenGeneratedSpr(Path file) {
        String fn = file.getFileName().toString();
        if (!fn.toLowerCase(java.util.Locale.ROOT).endsWith(".spr")) return false;
        Path sprv = file.resolveSibling(fn.substring(0, fn.length() - 4) + ".sprv");
        if (RemoteProjectClient.isRemote()) {
            return RemoteProjectClient.hasPath(RemoteProjectClient.relativeOf(sprv));
        }
        return Files.isRegularFile(sprv);
    }

    private static boolean isHiddenGeneratedSprRel(String rel) {
        if (rel == null || !rel.toLowerCase(java.util.Locale.ROOT).endsWith(".spr")) return false;
        String sprv = rel.substring(0, rel.length() - 4) + ".sprv";
        return RemoteProjectClient.hasPath(sprv);
    }

    private Path projectPath(String rel) {
        return RemoteProjectClient.localPath(rel);
    }

    private void onProjectImported(List<Path> imported) {
        if (RemoteProjectClient.isRemote() && imported != null) {
            for (Path dest : imported) {
                pushDropped(dest);
            }
        }
        reloadProject();
    }

    private static void pushDropped(Path dest) {
        if (dest == null) return;
        try {
            if (Files.isRegularFile(dest)) {
                RemoteProjectClient.pushIfRemote(dest);
            } else if (Files.isDirectory(dest)) {
                try (var walk = Files.walk(dest)) {
                    walk.filter(Files::isRegularFile).forEach(RemoteProjectClient::pushIfRemote);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void renderScriptTab(ScriptTab tab) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .118f, .133f, .157f, .99f); // #1E2228
        ImGui.beginChild("script-tab-bg-" + tab.path, 0f, 0f, false);
        EditorIcons.imageInline("scroll-text", 14f, 0.72f, 0.58f, 0.96f, 1f, false);
        ImGui.sameLine(0f, 6f);
        ImGui.textColored(.65f, .70f, .78f, 1f, tab.path.getFileName().toString());
        ImGui.sameLine();
        ImGui.textDisabled(tab.path.getParent().toString());
        ImGui.sameLine();
        if (EditorIcons.iconButton("save", "script-save-" + tab.path, 16f, EditorI18n.t("Сохранить  Ctrl+S"))) {
            saveScript(tab);
        }
        if (tab.dirty) {
            ImGui.sameLine();
            float alpha = .72f + .22f * (float) Math.sin(ImGui.getTime() * 3.4);
            ImGui.textColored(1f, .68f, .30f, alpha, EditorI18n.t("НЕ СОХРАНЕНО"));
        }
        ImGui.spacing();
        if (org.zonarstudio.spraute_engine.client.editor.scene.ScriptCodeEditor.render(
                tab.path.toString(), tab.content)) {
            tab.dirty = true;
        }
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    /** Save open .spr / .sprv tabs so Play and dedicated deploy see the latest files. */
    public static void flushPendingScripts() {
        org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor.saveAllDirty();
        org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.saveAllDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SprauteEngineEditorScreen editor) {
            editor.scriptTabs.stream().filter(tab -> tab.dirty).forEach(editor::saveScript);
        }
    }

    private void saveScript(ScriptTab tab) {
        try {
            Files.writeString(tab.path, tab.content.get(), StandardCharsets.UTF_8);
            tab.dirty = false;
            RemoteProjectClient.pushIfRemote(tab.path);
            SceneDocument openDoc = SceneEditorSession.document();
            if (openDoc != null) {
                org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.invalidate(openDoc.id);
                org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(openDoc);
            } else {
                org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.reload();
            }
            org.zonarstudio.spraute_engine.script.ScriptManager scripts =
                    org.zonarstudio.spraute_engine.script.ScriptManager.getInstance();
            if (scripts != null) {
                scripts.refreshScriptFile(tab.path);
            }
        } catch (IOException e) {
            // Keep the tab dirty; the path and failure remain visible to the user.
        }
    }

    // ---- Viewport interaction (only when ImGui doesn't want the mouse) ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ImGui popups/windows sit above the captured world image. Never let a
        // click handled by UI also select a node or start viewport camera input.
        if (!EditorViewportInput.interactionAllowed()
                || imGuiWantsMouse() || EditorGrid.isSettingsOpen()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && EditorGizmo.containsViewport(mouseX, mouseY)) {
            if (EditorGizmo.beginDrag(mouseX, mouseY)) return true;
            String picked = EditorGizmo.pickNode(mouseX, mouseY);
            boolean ctrl = imgui.ImGui.getIO().getKeyCtrl();
            if (picked != null) {
                if (ctrl) SceneEditorSession.toggleSelect(picked);
                else SceneEditorSession.select(picked);
                org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
                return true;
            }
            if (EditorGizmo.mode() == EditorGizmo.Mode.SELECT) {
                EditorMarqueeSelect.begin(mouseX, mouseY, ctrl);
                return true;
            }
            if (!ctrl) {
                SceneEditorSession.select(null);
                org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
                EditorViewportInput.beginCapture();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && EditorGizmo.isDragging()) {
            EditorGizmo.updateDrag(mouseX, mouseY);
            return true;
        }
        if (button == 0 && EditorMarqueeSelect.isActive()) {
            EditorMarqueeSelect.update(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && EditorGizmo.isDragging()) {
            EditorGizmo.endDrag();
            return true;
        }
        if (button == 0 && EditorMarqueeSelect.isActive()) {
            EditorMarqueeSelect.update(mouseX, mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.isActive()) {
                org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.stop();
                return true;
            }
            if (EditorViewportInput.isCaptured()) {
                EditorViewportInput.endCapture();
                return true;
            }
            if (org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.clearSelectionIfAny()) {
                return true;
            }
            return true;
        }
        if (!hasControlDown()
                && org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.handleKey(keyCode, scanCode)) {
            return true;
        }
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_S) {
                SceneEditorSession.save();
                AnimatorTimelineSession.save();
                scriptTabs.stream().filter(tab -> tab.dirty).forEach(this::saveScript);
                org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor.saveAllDirty();
                org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.saveAllDirty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShiftDown()) org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.redo();
                else org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.undo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorUndo.redo();
                return true;
            }
        }
        if (SprauteImGuiBackend.isInitialized() && !ImGui.getIO().getWantTextInput()) {
            if (EditorViewportInput.isCaptured()
                    || org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.isActive()) {
                if (keyCode == GLFW.GLFW_KEY_F) {
                    EditorViewportInput.toggleCapture();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == GLFW.GLFW_KEY_Q) EditorGizmo.setMode(EditorGizmo.Mode.SELECT);
            else if (keyCode == GLFW.GLFW_KEY_W) EditorGizmo.setMode(EditorGizmo.Mode.MOVE);
            else if (keyCode == GLFW.GLFW_KEY_E) EditorGizmo.setMode(EditorGizmo.Mode.ROTATE);
            else if (keyCode == GLFW.GLFW_KEY_R) EditorGizmo.setMode(EditorGizmo.Mode.SCALE);
            else if (keyCode == GLFW.GLFW_KEY_F) EditorViewportInput.toggleCapture();
            else return super.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean imGuiWantsMouse() {
        return SprauteImGuiBackend.isInitialized() && ImGui.getIO().getWantCaptureMouse();
    }

    private Path projectRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
    }

    private void reloadProject() {
        scripts.clear();
        scenes.clear();
        if (RemoteProjectClient.isRemote()) {
            if (!RemoteProjectClient.hasList()) {
                if (!RemoteProjectClient.isLoading()) RemoteProjectClient.requestList();
                EditorAssetIndex.refresh();
                return;
            }
            for (var e : RemoteProjectClient.entries()) {
                if (e.dir) continue;
                if (e.path.startsWith("scripts/") && e.path.endsWith(".spr") && !isHiddenGeneratedSprRel(e.path)) {
                    scripts.add(projectPath(e.path));
                }
                if (e.path.startsWith("scenes/") && e.path.endsWith(SceneRepository.EXTENSION)) {
                    scenes.add(projectPath(e.path));
                }
            }
            scripts.sort(Comparator.comparing(Path::toString));
            scenes.sort(Comparator.comparing(Path::toString));
            EditorAssetIndex.refresh();
            return;
        }
        Path root = projectRoot().resolve("scripts");
        try {
            Files.createDirectories(root);
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".spr"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(scripts::add);
            }
        } catch (IOException ignored) {
        }
        scenes.addAll(SceneRepository.listScenes(Minecraft.getInstance().gameDirectory.toPath()));
        EditorAssetIndex.refresh();
    }

    private void openTexture(Path path) {
        if (RemoteProjectClient.isRemote()) {
            RemoteProjectClient.requestLock(RemoteProjectClient.relativeOf(path), null);
        }
        for (TextureTab tab : textureTabs) {
            if (tab.path.equals(path)) {
                tab.open.set(true);
                return;
            }
        }
        textureTabs.add(new TextureTab(path));
    }

    private void openModel(Path path) {
        if (RemoteProjectClient.isRemote()) {
            RemoteProjectClient.requestLock(RemoteProjectClient.relativeOf(path), null);
        }
        for (ModelTab tab : modelTabs) {
            if (tab.path.equals(path)) {
                tab.open.set(true);
                return;
            }
        }
        modelTabs.add(new ModelTab(path, projectRoot()));
    }

    private void renderTextureTab(TextureTab tab) {
        tab.session.render();
    }

    private void renderModelTab(ModelTab tab) {
        ImGui.textColored(.65f, .70f, .78f, 1f, tab.path.getFileName().toString());
        ImGui.sameLine();
        ImGui.textDisabled(EditorI18n.t("Bind pose из .geo.json · без animation-файла"));
        ImGui.spacing();

        float availW = Math.max(4f, ImGui.getContentRegionAvailX());
        float availH = Math.max(4f, ImGui.getContentRegionAvailY());
        float panelW = Math.min(330f, Math.max(240f, availW * 0.32f));
        ImGui.beginChild("geo-bones-" + tab.path, panelW, availH, true);
        renderModelBoneEditor(tab);
        ImGui.endChild();
        ImGui.sameLine();

        float previewW = Math.max(4f, availW - panelW - ImGui.getStyle().getItemSpacingX());
        ImGui.beginChild("geo-preview-" + tab.path, previewW, availH, false);
        ImGui.textDisabled(EditorI18n.t("ЛКМ + движение: вращать   колесо: зум"));
        float size = Math.min(previewW, Math.max(4f, ImGui.getContentRegionAvailY()));
        int texId = org.zonarstudio.spraute_engine.client.editor.scene.EditorModelPreviewRenderer.render(tab.node);
        if (texId <= 0) {
            ImGui.textDisabled(EditorI18n.t("Не удалось загрузить модель"));
            ImGui.endChild();
            return;
        }
        ImGui.setCursorPosX(ImGui.getCursorPosX() + Math.max(0f, (previewW - size) * 0.5f));
        ImGui.image(texId, size, size, 0f, 1f, 1f, 0f);
        if (ImGui.isItemHovered()) {
            var io = ImGui.getIO();
            if (ImGui.isMouseDown(0)) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorModelPreviewRenderer
                        .rotate(io.getMouseDeltaX() * 0.6f, io.getMouseDeltaY() * 0.6f);
            }
            float wheel = io.getMouseWheel();
            if (wheel != 0f) {
                org.zonarstudio.spraute_engine.client.editor.scene.EditorModelPreviewRenderer.addZoom(wheel * 0.5f);
            }
        }
        ImGui.endChild();
    }

    private void renderModelBoneEditor(ModelTab tab) {
        var model = org.zonarstudio.spraute_engine.entity.client.SpModelCache.getOrLoad(tab.node.model);
        if (model == null) {
            ImGui.textDisabled(EditorI18n.t("Не удалось загрузить модель"));
            return;
        }
        ImGui.text(EditorI18n.t("Кости"));
        ImGui.sameLine();
        ImGui.textDisabled("(" + model.boneMap.size() + ")");
        if (ImGui.smallButton(EditorI18n.t("+ корневая##model-bone-root"))) {
            String name = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .uniqueBoneName(tab.node.model, "bone");
            tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .addBone(tab.node.model, name, null);
            if (tab.error == null) tab.selectBone(name);
        }
        if (tab.selectedBone != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(EditorI18n.t("+ дочерняя##model-bone-child"))) {
                String name = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                        .uniqueBoneName(tab.node.model, tab.selectedBone + "_bone");
                tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                        .addBone(tab.node.model, name, tab.selectedBone);
                if (tab.error == null) tab.selectBone(name);
            }
        }
        ImGui.separator();
        for (var root : model.rootBones) renderModelBoneTree(tab, root);

        if (tab.selectedBone == null) {
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Выберите кость для редактирования"));
            if (tab.error != null) ImGui.textWrapped(tab.error);
            return;
        }
        float[] rest = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                .readBone(tab.node.model, tab.selectedBone);
        if (rest == null) {
            tab.selectedBone = null;
            return;
        }
        System.arraycopy(rest, 0, tab.pivot, 0, 3);
        System.arraycopy(rest, 3, tab.rotation, 0, 3);
        float[] size = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                .readBoneSize(tab.node.model, tab.selectedBone);
        if (size != null) System.arraycopy(size, 0, tab.boneSize, 0, 3);

        ImGui.separator();
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##model-bone-rename", tab.boneName);
        if (ImGui.smallButton(EditorI18n.t("Переименовать"))) {
            String next = sanitizeGeoBoneName(tab.boneName.get());
            tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .renameBone(tab.node.model, tab.selectedBone, next);
            if (tab.error == null) tab.selectBone(next);
        }
        String parent = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                .readParent(tab.node.model, tab.selectedBone);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Родитель"));
        if (ImGui.beginCombo("##model-bone-parent",
                parent == null || parent.isBlank() ? EditorI18n.t("(корневая кость)") : parent)) {
            if (ImGui.selectable(EditorI18n.t("(корневая кость)"), parent == null || parent.isBlank())) {
                tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                        .reparentBone(tab.node.model, tab.selectedBone, null);
            }
            for (String candidate : org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .boneNames(tab.node.model)) {
                if (candidate.equals(tab.selectedBone)) continue;
                if (ImGui.selectable(candidate, candidate.equals(parent))) {
                    tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                            .reparentBone(tab.node.model, tab.selectedBone, candidate);
                }
            }
            ImGui.endCombo();
        }

        Runnable pushGeoUndo = () -> org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                .pushUndo(tab.node.model);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Position / Pivot (пиксели)"));
        boolean moved = EditorInspectorUi.vector3("model-geo-pivot", tab.pivot, .1f, -100000f, 100000f, pushGeoUndo);
        EditorInspectorUi.fieldLabel("Rotation °");
        boolean rotated = EditorInspectorUi.vector3("model-geo-rotation", tab.rotation, .5f, -36000f, 36000f, pushGeoUndo);
        if (moved || rotated) {
            tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .writeBone(tab.node.model, tab.selectedBone, tab.pivot, tab.rotation);
        }
        EditorInspectorUi.fieldLabel(EditorI18n.t("Size геометрии (пиксели)"));
        if (EditorInspectorUi.vector3("model-geo-size", tab.boneSize, .1f, .001f, 100000f, pushGeoUndo)) {
            tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                    .writeBoneSize(tab.node.model, tab.selectedBone, tab.boneSize);
        }
        EditorInspectorUi.hint(EditorI18n.t("Изменения сразу записываются в .geo.json."));

        ImGui.separator();
        if (!tab.confirmDelete) {
            if (ImGui.smallButton(EditorI18n.t("Удалить кость…"))) tab.confirmDelete = true;
        } else {
            ImGui.textColored(1f, .65f, .25f, 1f, EditorI18n.t("Удалить ветку с дочерними?"));
            if (ImGui.smallButton(EditorI18n.t("Удалить##model-bone-confirm"))) {
                tab.error = org.zonarstudio.spraute_engine.client.editor.scene.GeoBoneEditor
                        .deleteBone(tab.node.model, tab.selectedBone, true);
                if (tab.error == null) tab.selectBone(null);
                tab.confirmDelete = false;
            }
            ImGui.sameLine();
            if (ImGui.smallButton(EditorI18n.t("Отмена##model-bone-cancel"))) tab.confirmDelete = false;
        }
        if (tab.error != null) ImGui.textWrapped(tab.error);
    }

    private void renderModelBoneTree(ModelTab tab,
                                     org.zonarstudio.spraute_engine.core.model.SpBone bone) {
        int flags = imgui.flag.ImGuiTreeNodeFlags.OpenOnArrow
                | imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
        if (bone.children.isEmpty()) flags |= imgui.flag.ImGuiTreeNodeFlags.Leaf;
        if (bone.name.equals(tab.selectedBone)) flags |= imgui.flag.ImGuiTreeNodeFlags.Selected;
        boolean open = ImGui.treeNodeEx("model-bone-" + tab.path + "-" + bone.name, flags, bone.name);
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) tab.selectBone(bone.name);
        if (open) {
            for (var child : bone.children) renderModelBoneTree(tab, child);
            ImGui.treePop();
        }
    }

    private static String sanitizeGeoBoneName(String requested) {
        String value = requested == null ? "" : requested.trim();
        value = value.replaceAll("[^A-Za-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "bone" : value;
    }

    private void openVisualScript(Path path) {
        if (RemoteProjectClient.isRemote()) {
            RemoteProjectClient.requestLock(RemoteProjectClient.relativeOf(path), null);
        }
        for (VisualTab tab : visualTabs) {
            if (tab.path.equals(path)) {
                tab.open.set(true);
                return;
            }
        }
        visualTabs.add(new VisualTab(path));
    }

    private void openScript(Path path) {
        if (RemoteProjectClient.isRemote()) {
            RemoteProjectClient.requestLock(RemoteProjectClient.relativeOf(path), null);
        }
        ScriptTab existing = scriptTabs.stream()
                .filter(tab -> tab.path.equals(path))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.open.set(true);
            requestedScriptTab = path;
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            lines = List.of(EditorI18n.t("Ошибка чтения: ") + e.getMessage());
        }
        scriptTabs.add(new ScriptTab(path, String.join("\n", lines)));
        requestedScriptTab = path;
    }

    @Override
    public void onClose() {
        // Ask only when there are unsaved edits. Live/play mode never prompts
        // (edits are ephemeral; isDirty() is already false while the scene runs).
        if (!exitConfirmed && SceneEditorSession.isDirty()) {
            EditorViewportInput.endCapture();
            openExitConfirmation = true;
            return;
        }
        closeEditorNow();
    }

    @Override
    public void removed() {
        // Also covers replacement by another screen or an abnormal editor exit,
        // where onClose() is not guaranteed to run.
        RemoteProjectClient.unlockAll();
        super.removed();
    }

    private void closeEditorNow() {
        scriptTabs.stream().filter(tab -> tab.dirty).forEach(this::saveScript);
        org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.saveAllDirty();
        org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.disposeAll();
        RemoteProjectClient.unlockAll();
        EditorGizmo.endDrag();
        EditorViewportInput.endCapture();
        EditorWorldViewport.dispose();
        org.zonarstudio.spraute_engine.client.SprauteScriptScreen.clearEditorPreviews();
        projectOsDrop.uninstall();
        if (SprauteImGuiBackend.isInitialized()) {
            org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend.saveIniNow();
        }
        SprauteEditorState.onEditorHidden();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class TextureTab {
        Path path;
        final ImBoolean open = new ImBoolean(true);
        final org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession session;

        TextureTab(Path path) {
            this.path = path;
            this.session = org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession.open(path);
        }
    }

    private static final class ModelTab {
        Path path;
        final ImBoolean open = new ImBoolean(true);
        final SceneNode node;
        final ImString boneName = new ImString(96);
        final float[] pivot = new float[3];
        final float[] rotation = new float[3];
        final float[] boneSize = new float[3];
        String selectedBone;
        String error;
        boolean confirmDelete;

        ModelTab(Path path, Path projectRoot) {
            this.path = path;
            this.node = SceneNode.model(path.getFileName().toString());
            this.node.model = projectRoot.relativize(path).toString().replace('\\', '/');
            // Guess a texture with the same base name so the preview is not blank.
            String base = path.getFileName().toString();
            if (base.endsWith(".geo.json")) base = base.substring(0, base.length() - ".geo.json".length());
            for (String texture : org.zonarstudio.spraute_engine.client.editor.scene.EditorAssetIndex.textures()) {
                String texName = texture.substring(texture.lastIndexOf('/') + 1);
                if (texName.equalsIgnoreCase(base + ".png")) {
                    this.node.texture = texture;
                    break;
                }
            }
        }

        void selectBone(String name) {
            selectedBone = name;
            boneName.set(name == null ? "" : name);
            confirmDelete = false;
            error = null;
        }
    }

    private static final class VisualTab {
        Path path;
        final ImBoolean open = new ImBoolean(true);
        final org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor editor;

        VisualTab(Path path) {
            this.path = path;
            this.editor = new org.zonarstudio.spraute_engine.client.editor.scene.VisualScriptEditor(path);
        }
    }

    private static final class ScriptTab {
        Path path;
        final ImString content;
        final ImBoolean open = new ImBoolean(true);
        boolean dirty;

        ScriptTab(Path path, String content) {
            this.path = path;
            this.content = new ImString(content, Math.max(16_384, content.length() * 4 + 4096));
        }
    }
}
