package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.client.ui.VideoFrameCache;
import org.zonarstudio.spraute_engine.resource.StoryPackManifest;
import org.zonarstudio.spraute_engine.resource.StoryPackWriter;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Export one scene as a dual-version Forge story JAR (lowcodefml, 1.19.2 + 1.20.1).
 */
public final class SceneExportPanel {
    private SceneExportPanel() {}

    private static final ImString displayName = new ImString(96);
    private static final ImString modId = new ImString(64);
    private static final ImString version = new ImString(32);
    private static final ImString authors = new ImString(96);
    private static final ImString license = new ImString(80);
    private static final ImString homepage = new ImString(160);
    private static final ImString credits = new ImString(160);
    private static final ImString description = new ImString(2048);
    private static final ImString worldInstallName = new ImString(96);
    private static final ImString logoSearch = new ImString(128);
    private static final ImBoolean includeWorld = new ImBoolean(false);
    private static final ImBoolean copyToMods = new ImBoolean(true);
    private static final ImBoolean obfuscate = new ImBoolean(true);
    private static final ImBoolean TMP_CB = new ImBoolean();
    private static final Set<String> skippedAuto = new HashSet<>();
    private static final Set<String> extraSelected = new HashSet<>();
    private static final List<ModEntry> mods = new ArrayList<>();
    private static final List<WorldEntry> worlds = new ArrayList<>();
    private static final List<String> sceneIds = new ArrayList<>();
    private static String selectedScene = "";
    private static String selectedMod = "";
    private static String selectedWorld = "";
    private static String selectedLogo = "";
    private static String status = "";
    private static String error = "";
    private static volatile boolean exporting;
    private static volatile float exportProgress;
    private static volatile String exportDetail = "";
    private static String exportTitle = "";
    private static boolean needsRefresh = true;
    private static boolean openRequested;
    private static boolean modIdTouched;
    private static SceneExportCollector.Result deps;

    public static void requestOpen() {
        openRequested = true;
        needsRefresh = true;
    }

    public static boolean consumeOpenRequest() {
        boolean r = openRequested;
        openRequested = false;
        return r;
    }

    public static void render(ImBoolean show) {
        if (exporting) show.set(true);
        if (!show.get()) return;
        if (needsRefresh) {
            needsRefresh = false;
            refresh();
        }

        ImGui.setNextWindowSize(980f, 760f, ImGuiCond.FirstUseEver);
        if (!ImGui.begin(EditorI18n.t("Экспорт сюжета как мод###SceneExport"), show, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        // Snapshot: clicking «Создать» sets exporting in this same frame.
        boolean uiLocked = exporting;
        if (uiLocked) ImGui.beginDisabled();

        drawHeader();
        ImGui.spacing();

        float footer = 88f;
        float avail = ImGui.getContentRegionAvailY() - footer;
        if (avail < 280f) avail = 280f;
        float leftW = Math.max(360f, ImGui.getContentRegionAvailX() * 0.46f);

        ImGui.beginChild("##story-export-left", leftW, avail, true);
        renderLeft();
        ImGui.endChild();
        ImGui.sameLine();
        ImGui.beginChild("##story-export-right", 0f, avail, true);
        renderRight();
        ImGui.endChild();

        ImGui.spacing();
        ImGui.separator();
        List<String> packed = packFiles();
        ImGui.textDisabled(summaryLine(packed));
        if (deps != null && !deps.missing().isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.72f, 0.35f, 1f);
            ImGui.textWrapped(EditorI18n.t("Не найдено на диске: ") + String.join(", ", deps.missing()));
            ImGui.popStyleColor();
        }
        if (!error.isBlank()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.45f, 0.4f, 1f);
            ImGui.textWrapped(error);
            ImGui.popStyleColor();
        }
        if (!status.isBlank()) {
            ImGui.textColored(0.45f, 0.85f, 0.55f, 1f, status);
        }

        boolean canJar = !exporting
                && !StoryPackManifest.sanitizeModId(modId.get()).isBlank()
                && !selectedScene.isBlank()
                && !packed.isEmpty()
                && (!includeWorld.get() || !selectedWorld.isBlank());
        if (!canJar) ImGui.beginDisabled();
        ImGui.pushStyleColor(ImGuiCol.Button, 0.22f, 0.48f, 0.32f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.28f, 0.58f, 0.40f, 1f);
        if (ImGui.button(exporting ? EditorI18n.t("Создание…") : EditorI18n.t("Создать JAR-мод"), 180f, 32f)) {
            runJarExport();
        }
        ImGui.popStyleColor(2);
        if (!canJar) ImGui.endDisabled();

        ImGui.sameLine();
        boolean canZip = !exporting && !selectedMod.isBlank() && !packed.isEmpty();
        if (!canZip) ImGui.beginDisabled();
        if (ImGui.button(EditorI18n.t("ZIP-архив"), 120f, 32f)) runZipExport();
        if (!canZip) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("Старый формат: mods/ + spraute_engine/ + мир. Нужен JAR движка."));
        }

        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Обновить"), 110f, 32f)) refresh();
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Закрыть"), 100f, 32f)) show.set(false);

        if (uiLocked) ImGui.endDisabled();
        drawBusyOverlay();
        ImGui.end();
    }

    private static void drawHeader() {
        float w = ImGui.getContentRegionAvailX();
        float h = 58f;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h, ImGui.getColorU32(0.11f, 0.14f, 0.12f, 0.98f), 8f);
        dl.addRect(x, y, x + w, y + h, ImGui.getColorU32(0.32f, 0.62f, 0.42f, 0.45f), 8f, 0, 1.2f);
        dl.addRectFilled(x + 8f, y + 10f, x + 12f, y + h - 10f, ImGui.getColorU32(0.40f, 0.82f, 0.52f, 1f), 2f);
        dl.addText(x + 22f, y + 10f, ImGui.getColorU32(0.92f, 0.96f, 0.93f, 1f),
                EditorI18n.t("Сцена в мод"));
        dl.addText(x + 22f, y + 30f, ImGui.getColorU32(0.62f, 0.72f, 0.64f, 1f),
                EditorI18n.t("JAR кладётся в mods/. Сюжет читается из мода, в редакторе его нет. Удалил JAR — сюжет пропал."));
        ImGui.dummy(w, h);
    }

    private static void renderLeft() {
        ImGui.text(EditorI18n.t("Сцена"));
        ImGui.beginChild("##story-scenes", 0f, 92f, true);
        if (sceneIds.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("Нет сцен в spraute_engine/scenes"));
        } else {
            for (String id : sceneIds) {
                boolean sel = id.equals(selectedScene);
                if (ImGui.radioButton(id + "##sc-" + id, sel) && !sel) {
                    selectedScene = id;
                    onScenePicked(id);
                }
            }
        }
        ImGui.endChild();
        if (ImGui.smallButton(EditorI18n.t("Текущая сцена")) && SceneEditorSession.document() != null
                && SceneEditorSession.document().id != null) {
            selectedScene = SceneEditorSession.document().id;
            onScenePicked(selectedScene);
        }

        ImGui.spacing();
        labeledInput(EditorI18n.t("Название мода"), "##dn", displayName);
        if (ImGui.isItemDeactivatedAfterEdit() && !modIdTouched) {
            modId.set(StoryPackManifest.sanitizeModId(displayName.get()));
        }
        labeledInput(EditorI18n.t("ID мода"), "##mid", modId);
        if (ImGui.isItemEdited()) modIdTouched = true;
        ImGui.sameLine();
        ImGui.textDisabled(StoryPackManifest.sanitizeModId(modId.get()));

        labeledInput(EditorI18n.t("Версия"), "##ver", version);
        labeledInput(EditorI18n.t("Авторы"), "##auth", authors);
        labeledInput(EditorI18n.t("Лицензия"), "##lic", license);
        labeledInput(EditorI18n.t("Сайт"), "##url", homepage);
        labeledInput(EditorI18n.t("Благодарности"), "##crd", credits);

        ImGui.textColored(0.58f, 0.62f, 0.68f, 1f, EditorI18n.t("Описание"));
        ImGui.inputTextMultiline("##desc", description, 0f, 88f, ImGuiInputTextFlags.AllowTabInput);

        ImGui.spacing();
        ImGui.textColored(0.58f, 0.62f, 0.68f, 1f, EditorI18n.t("Картинка мода (логотип)"));
        renderLogoPicker();

        ImGui.spacing();
        ImGui.checkbox(EditorI18n.t("Включить мир (копия в saves/, если папки ещё нет)"), includeWorld);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t(
                    "Мир Minecraft нельзя держать только в JAR. Копия в saves/ останется, даже если мод удалить. Сцены и скрипты живут в JAR."));
        }
        if (includeWorld.get()) {
            labeledInput(EditorI18n.t("Имя папки в saves"), "##winst", worldInstallName);
            ImGui.beginChild("##story-worlds", 0f, 88f, true);
            if (worlds.isEmpty()) {
                ImGui.textDisabled(EditorI18n.t("Нет миров с level.dat"));
            } else {
                for (WorldEntry w : worlds) {
                    boolean sel = w.name.equals(selectedWorld);
                    if (ImGui.radioButton(w.name + "##world-" + w.name, sel)) {
                        selectedWorld = w.name;
                        if (worldInstallName.get().isBlank()) worldInstallName.set(w.name);
                    }
                }
            }
            ImGui.endChild();
        }

        ImGui.spacing();
        ImGui.checkbox(EditorI18n.t("Обфусцировать скрипты (.spr)"), obfuscate);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t(
                    "Убирает комментарии и визуальные .sprv, переименовывает переменные и функции. Строки (диалоги, пути к файлам) не трогает. Это не шифрование — только затрудняет чтение."));
        }
        ImGui.textDisabled(EditorI18n.t("Только для JAR. ZIP остаётся как есть."));

        ImGui.spacing();
        ImGui.checkbox(EditorI18n.t("Скопировать JAR в mods/"), copyToMods);
        ImGui.textDisabled(EditorI18n.t("После копирования перезапусти игру, чтобы мод подхватился."));
    }

    private static void renderRight() {
        ImGui.text(EditorI18n.t("Файлы сцены (выбраны автоматически)"));
        if (ImGui.smallButton(EditorI18n.t("Все"))) skippedAuto.clear();
        ImGui.sameLine();
        if (ImGui.smallButton(EditorI18n.t("Ничего")) && deps != null) {
            skippedAuto.addAll(deps.paths());
        }
        renderDepsList();

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Ещё файлы из папок"));
        ImGui.sameLine();
        ImGui.textDisabled(EditorI18n.t("(если авто-сбор не хватил)"));
        Path sprauteRoot = gameDir().resolve("spraute_engine");
        ImGui.beginChild("##story-extra", 0f, 0f, true);
        if (!Files.isDirectory(sprauteRoot)) {
            ImGui.textDisabled(EditorI18n.t("Папка spraute_engine не найдена"));
        } else {
            ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 22f);
            renderExtraTree(sprauteRoot, "", 0);
            ImGui.popStyleVar();
        }
        ImGui.endChild();
    }

    private static void renderLogoPicker() {
        String shown = selectedLogo.isBlank() ? EditorI18n.t("(нет)") : selectedLogo;
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##logo", shown)) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.08f, 0.10f, 0.14f, 1f);
            ImGui.inputTextWithHint("##logo-search", EditorI18n.t("Поиск…"), logoSearch);
            ImGui.popStyleColor();
            String filter = logoSearch.get().toLowerCase(Locale.ROOT);

            if (ImGui.selectable(EditorI18n.t("(нет)"), selectedLogo.isBlank())) {
                selectedLogo = "";
            }
            for (String option : EditorAssetIndex.textures()) {
                if (!option.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
                var tex = EditorTexturePreview.cached(option);
                if (tex != null) {
                    ImGui.image(tex.glId(), 18f, 18f);
                } else {
                    ImGui.dummy(18f, 18f);
                }
                ImGui.sameLine();
                if (ImGui.selectable(option, option.equals(selectedLogo))) {
                    selectedLogo = option;
                }
                if (ImGui.isItemHovered()) {
                    var hover = EditorTexturePreview.get(option);
                    if (hover != null) {
                        ImGui.beginTooltip();
                        float[] size = EditorTexturePreview.fit(hover, 192f, 192f);
                        ImGui.image(hover.glId(), size[0], size[1]);
                        ImGui.textDisabled(hover.width() + " x " + hover.height());
                        ImGui.endTooltip();
                    }
                }
            }
            ImGui.endCombo();
        }
        if (!selectedLogo.isBlank()) {
            var tex = EditorTexturePreview.get(selectedLogo);
            if (tex != null) {
                float[] size = EditorTexturePreview.fit(tex, 96f, 96f);
                ImGui.image(tex.glId(), size[0], size[1]);
                ImGui.sameLine();
                ImGui.textDisabled(tex.width() + " x " + tex.height());
            }
        }
    }

    private static void labeledInput(String label, String id, ImString buf) {
        ImGui.textColored(0.58f, 0.62f, 0.68f, 1f, label);
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText(id, buf);
    }

    private static void renderDepsList() {
        ImGui.beginChild("##story-export-deps", 0f, 220f, true);
        if (deps == null || deps.paths().isEmpty()) {
            ImGui.textDisabled(selectedScene.isBlank()
                    ? EditorI18n.t("Выберите сцену")
                    : EditorI18n.t("Зависимости не найдены"));
            ImGui.endChild();
            return;
        }
        Map<String, List<String>> byCat = new LinkedHashMap<>();
        for (String p : deps.paths()) {
            byCat.computeIfAbsent(SceneExportCollector.categoryOf(p), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<String>> e : byCat.entrySet()) {
            String cat = e.getKey();
            List<String> files = e.getValue();
            boolean open = ImGui.treeNodeEx(
                    "     " + EditorI18n.t(cat) + " (" + files.size() + ")###cat-" + cat,
                    ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth);
            float[] tint = tintForCategory(cat);
            EditorIcons.drawOverTreeLabel(iconForCategory(cat), 14f, tint);
            if (open) {
                for (String rel : files) {
                    boolean on = !skippedAuto.contains(rel);
                    TMP_CB.set(on);
                    if (ImGui.checkbox("##dep-" + rel, TMP_CB)) {
                        if (TMP_CB.get()) skippedAuto.remove(rel);
                        else skippedAuto.add(rel);
                    }
                    ImGui.sameLine(0f, 6f);
                    String kind = EditorIcons.projectFileKind(Path.of(rel));
                    float[] fileTint = EditorIcons.tintForProjectFile(kind);
                    ImGui.selectable("     " + rel + "###depn-" + rel);
                    EditorIcons.drawOverSelectableLabel(EditorIcons.forProjectFile(kind), 14f, fileTint);
                }
                ImGui.treePop();
            }
        }
        ImGui.endChild();
    }

    private static void renderExtraTree(Path absDir, String rel, int depth) {
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(absDir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        String n = p.getFileName().toString();
                        if (n.startsWith(".") || "cache".equalsIgnoreCase(n)) return;
                        if (Files.isDirectory(p)) dirs.add(p);
                        else files.add(p);
                    });
        } catch (IOException e) {
            ImGui.textDisabled(EditorI18n.t("Ошибка чтения: ") + e.getMessage());
            return;
        }
        for (Path child : dirs) {
            String name = child.getFileName().toString();
            String childRel = rel.isEmpty() ? name : rel + "/" + name;
            ImGui.pushID(childRel);
            extraCheckbox(childRel);
            ImGui.sameLine(0f, 6f);
            float[] folderTint = EditorIcons.tintForProjectFolder(name);
            boolean open = ImGui.treeNodeEx("     " + name + "/###dir",
                    ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth);
            EditorIcons.drawOverTreeLabel(EditorIcons.forProjectFolder(name), 14f, folderTint);
            if (open) {
                renderExtraTree(child, childRel, depth + 1);
                ImGui.treePop();
            }
            ImGui.popID();
        }
        for (Path child : files) {
            String name = child.getFileName().toString();
            String childRel = rel.isEmpty() ? name : rel + "/" + name;
            ImGui.pushID(childRel);
            extraCheckbox(childRel);
            ImGui.sameLine(0f, 6f);
            String kind = EditorIcons.projectFileKind(child);
            float[] tint = EditorIcons.tintForProjectFile(kind);
            ImGui.selectable("     " + name + "###file");
            EditorIcons.drawOverSelectableLabel(EditorIcons.forProjectFile(kind), 14f, tint);
            ImGui.popID();
        }
    }

    private static void extraCheckbox(String childRel) {
        boolean exact = extraSelected.contains(childRel);
        boolean covered = extraCovered(childRel);
        boolean under = covered && !exact;
        TMP_CB.set(covered);
        if (under) ImGui.beginDisabled();
        boolean changed = ImGui.checkbox("##c", TMP_CB);
        if (under) ImGui.endDisabled();
        if (changed && !under) {
            if (TMP_CB.get()) {
                extraSelected.add(childRel);
                extraSelected.removeIf(p -> !p.equals(childRel) && p.startsWith(childRel + "/"));
            } else {
                extraSelected.remove(childRel);
            }
        }
    }

    private static boolean extraCovered(String rel) {
        if (extraSelected.contains(rel)) return true;
        String[] parts = rel.split("/");
        StringBuilder anc = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) anc.append('/');
            anc.append(parts[i]);
            if (extraSelected.contains(anc.toString())) return true;
        }
        return false;
    }

    private static String iconForCategory(String cat) {
        return switch (cat) {
            case "Сцены" -> "scene";
            case "Скрипты" -> "scroll-text";
            case "Модели" -> "model_file";
            case "Текстуры" -> "image_file";
            case "Анимации" -> "anim";
            case "Префабы" -> "plugin";
            default -> "folder";
        };
    }

    private static float[] tintForCategory(String cat) {
        return switch (cat) {
            case "Сцены" -> new float[]{0.41f, 0.66f, 1f, 1f};
            case "Скрипты" -> new float[]{0.72f, 0.58f, 0.96f, 1f};
            case "Модели" -> new float[]{0.95f, 0.72f, 0.38f, 1f};
            case "Текстуры" -> new float[]{0.45f, 0.85f, 0.95f, 1f};
            case "Анимации" -> new float[]{0.98f, 0.62f, 0.85f, 1f};
            case "Префабы" -> new float[]{0.45f, 0.90f, 0.60f, 1f};
            default -> new float[]{0.86f, 0.78f, 0.45f, 1f};
        };
    }

    private static void onScenePicked(String id) {
        skippedAuto.clear();
        if (displayName.get().isBlank() || sceneIds.contains(displayName.get().trim())) {
            displayName.set(id);
        }
        if (!modIdTouched) modId.set(StoryPackManifest.sanitizeModId(id));
        recollect();
    }

    private static void refresh() {
        error = "";
        status = "";
        mods.clear();
        worlds.clear();
        sceneIds.clear();
        Path gd = gameDir();

        if (version.get().isBlank()) version.set("1.0.0");
        if (license.get().isBlank()) license.set("All Rights Reserved");

        for (String id : SceneRepository.listSceneIds(gd)) sceneIds.add(id);
        if (SceneEditorSession.document() != null && SceneEditorSession.document().id != null) {
            String openId = SceneEditorSession.document().id;
            if (sceneIds.contains(openId)) selectedScene = openId;
        }
        if ((selectedScene.isBlank() || !sceneIds.contains(selectedScene)) && !sceneIds.isEmpty()) {
            selectedScene = sceneIds.get(0);
        }
        if (!selectedScene.isBlank()) {
            if (displayName.get().isBlank()) displayName.set(selectedScene);
            if (modId.get().isBlank()) modId.set(StoryPackManifest.sanitizeModId(selectedScene));
        }

        Path modsDir = gd.resolve("mods");
        if (Files.isDirectory(modsDir)) {
            try (Stream<Path> stream = Files.list(modsDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.toLowerCase(Locale.ROOT).endsWith(".jar")
                                    && n.toLowerCase(Locale.ROOT).contains("spraute");
                        })
                        .sorted(Comparator.comparingLong((Path p) -> {
                            try { return Files.getLastModifiedTime(p).toMillis(); }
                            catch (IOException e) { return 0L; }
                        }).reversed())
                        .forEach(p -> {
                            try {
                                mods.add(new ModEntry(p.getFileName().toString(), Files.size(p)));
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
        if (!mods.isEmpty() && (selectedMod.isBlank() || mods.stream().noneMatch(m -> m.name.equals(selectedMod)))) {
            selectedMod = mods.get(0).name;
        }

        Path saves = gd.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (Stream<Path> stream = Files.list(saves)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> Files.isRegularFile(p.resolve("level.dat")))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(p -> worlds.add(new WorldEntry(p.getFileName().toString())));
            } catch (IOException ignored) {}
        }
        if (!worlds.isEmpty() && (selectedWorld.isBlank()
                || worlds.stream().noneMatch(w -> w.name.equals(selectedWorld)))) {
            selectedWorld = worlds.get(0).name;
        }
        if (worldInstallName.get().isBlank() && !selectedWorld.isBlank()) {
            worldInstallName.set(selectedWorld);
        }

        recollect();
    }

    private static void recollect() {
        deps = null;
        if (selectedScene.isBlank()) return;
        Path gd = gameDir();
        try {
            Path file = SceneRepository.scenePath(gd, selectedScene);
            SceneDocument doc;
            if (SceneEditorSession.document() != null
                    && selectedScene.equals(SceneEditorSession.document().id)) {
                doc = SceneEditorSession.document();
            } else if (Files.isRegularFile(file)) {
                doc = SceneRepository.load(file);
            } else {
                error = EditorI18n.t("Файл сцены не найден: ") + selectedScene;
                return;
            }
            deps = SceneExportCollector.collect(gd, doc);
        } catch (Exception e) {
            error = EditorI18n.t("Не удалось прочитать сцену: ") + e.getMessage();
        }
    }

    private static List<String> packFiles() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (deps != null) {
            for (String p : deps.paths()) {
                if (!skippedAuto.contains(p)) out.add(p);
            }
        }
        Path root = gameDir().resolve("spraute_engine").normalize();
        for (String rel : extraSelected) {
            Path abs = root.resolve(rel).normalize();
            if (!abs.startsWith(root) || !Files.exists(abs)) continue;
            if (Files.isDirectory(abs)) {
                try (var walk = Files.walk(abs)) {
                    walk.filter(Files::isRegularFile).forEach(f ->
                            out.add(root.relativize(f).toString().replace('\\', '/')));
                } catch (IOException ignored) {}
            } else {
                out.add(rel.replace('\\', '/'));
            }
        }
        return new ArrayList<>(out);
    }

    private static String summaryLine(List<String> files) {
        List<String> parts = new ArrayList<>();
        if (!selectedScene.isBlank()) parts.add(EditorI18n.t("сцена «") + selectedScene + "»");
        parts.add(files.size() + EditorI18n.t(" файл(ов)"));
        if (includeWorld.get() && !selectedWorld.isBlank()) parts.add(EditorI18n.t("мир"));
        String id = StoryPackManifest.sanitizeModId(modId.get());
        if (!id.isBlank()) parts.add(id);
        return parts.isEmpty() ? EditorI18n.t("Выберите сцену") : EditorI18n.t("В мод: ") + String.join(", ", parts);
    }

    private static void saveOpenSceneIfNeeded() {
        if (SceneEditorSession.document() != null
                && selectedScene.equals(SceneEditorSession.document().id)
                && SceneEditorSession.isDirty()) {
            SceneEditorSession.save();
        }
        Path sceneFile = SceneRepository.scenePath(gameDir(), selectedScene);
        if (!Files.isRegularFile(sceneFile) && SceneEditorSession.document() != null
                && selectedScene.equals(SceneEditorSession.document().id)) {
            SceneEditorSession.save();
        }
    }

    private static void runJarExport() {
        error = "";
        status = "";
        String id = StoryPackManifest.sanitizeModId(modId.get());
        if (id.isBlank()) {
            error = EditorI18n.t("Укажите ID мода (латиница, цифры, _)");
            return;
        }
        List<String> files = packFiles();
        if (files.isEmpty()) {
            error = EditorI18n.t("Нет файлов для экспорта — выберите сцену");
            return;
        }
        if (includeWorld.get() && selectedWorld.isBlank()) {
            error = EditorI18n.t("Выберите мир или отключите «Включить мир»");
            return;
        }
        saveOpenSceneIfNeeded();
        final Path logo;
        if (!selectedLogo.isBlank()) {
            Path p = gameDir().resolve("spraute_engine").resolve(selectedLogo).normalize();
            logo = Files.isRegularFile(p) ? p : null;
        } else {
            logo = null;
        }
        String ver = version.get().isBlank() ? "1.0.0" : version.get().trim();
        String fileName = id + "-" + StoryPackManifest.sanitizeFileToken(ver) + ".jar";
        Path outDir = gameDir().resolve("exports");
        Path outJar = outDir.resolve(fileName);
        Path gd = gameDir();
        boolean copyMods = copyToMods.get();
        boolean doObfuscate = obfuscate.get();
        String display = displayName.get();
        String desc = description.get();
        String authorsText = authors.get();
        String creditsText = credits.get();
        String licenseText = license.get();
        String homepageText = homepage.get();
        String scene = selectedScene;
        String world = includeWorld.get() ? selectedWorld : null;
        String worldInstall = includeWorld.get() ? worldInstallName.get() : null;
        List<String> sourceFiles = List.copyOf(files);
        startExport(EditorI18n.t("Создание мода"), () -> {
            exportProgress = 0.04f;
            exportDetail = EditorI18n.t("Декод видео…");
            List<String> packed = VideoFrameCache.attachPackedCaches(
                    gd.resolve("spraute_engine"), sourceFiles, (p, eta) -> {
                        exportProgress = 0.04f + 0.14f * Math.max(0f, Math.min(1f, p));
                        exportDetail = EditorI18n.t("Декод видео…");
                    });
            StoryPackWriter.Spec spec = new StoryPackWriter.Spec(
                    id,
                    display,
                    ver,
                    desc,
                    authorsText,
                    creditsText,
                    licenseText,
                    homepageText,
                    scene,
                    packed,
                    logo,
                    world,
                    worldInstall,
                    doObfuscate
            );
            Files.createDirectories(outDir);
            StoryPackWriter.write(gd, outJar, spec, SceneExportPanel::onWriterProgress);
            if (copyMods) {
                exportProgress = 0.98f;
                exportDetail = EditorI18n.t("Копирование в mods/…");
                Path modsDir = gd.resolve("mods");
                Files.createDirectories(modsDir);
                Files.copy(outJar, modsDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
            exportProgress = 1f;
            exportDetail = EditorI18n.t("Готово");
            return new ExportResult(outJar,
                    EditorI18n.t("Готово: ") + outJar
                            + (copyMods ? EditorI18n.t("  ·  скопировано в mods/") : "")
                            + (doObfuscate ? EditorI18n.t("  ·  скрипты обфусцированы") : ""));
        });
    }

    private static void runZipExport() {
        error = "";
        status = "";
        if (selectedMod.isBlank()) {
            error = EditorI18n.t("Для ZIP нужен JAR Spraute Engine в папке mods");
            return;
        }
        List<String> files = packFiles();
        if (files.isEmpty()) {
            error = EditorI18n.t("Нет файлов для экспорта — выберите сцену");
            return;
        }
        saveOpenSceneIfNeeded();
        String name = StoryPackManifest.sanitizeFileToken(displayName.get());
        if (name.isBlank()) name = selectedScene;
        Path outDir = gameDir().resolve("exports");
        Path outZip = outDir.resolve(name + ".zip");
        String modName = selectedMod;
        List<String> sourceFiles = List.copyOf(files);
        String world = includeWorld.get() ? selectedWorld : null;
        startExport(EditorI18n.t("Создание ZIP"), () -> {
            exportProgress = 0.04f;
            exportDetail = EditorI18n.t("Декод видео…");
            List<String> packed = VideoFrameCache.attachPackedCaches(
                    gameDir().resolve("spraute_engine"), sourceFiles, (p, eta) -> {
                        exportProgress = 0.04f + 0.10f * Math.max(0f, Math.min(1f, p));
                        exportDetail = EditorI18n.t("Декод видео…");
                    });
            exportProgress = 0.15f;
            exportDetail = EditorI18n.t("Упаковка ZIP…");
            Files.createDirectories(outDir);
            ProjectExportPanel.createZip(outZip, modName, packed, world);
            exportProgress = 1f;
            exportDetail = EditorI18n.t("Готово");
            return new ExportResult(outZip, EditorI18n.t("Готово: ") + outZip);
        });
    }

    @FunctionalInterface
    private interface ExportJob {
        ExportResult run() throws Exception;
    }

    private record ExportResult(Path file, String statusLine) {}

    private static void startExport(String title, ExportJob job) {
        if (exporting) return;
        exportTitle = title;
        exportProgress = 0.02f;
        exportDetail = EditorI18n.t("Подготовка…");
        exporting = true;
        Thread thread = new Thread(() -> {
            try {
                ExportResult result = job.run();
                Minecraft.getInstance().execute(() -> finishExport(result, null));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> finishExport(null, e));
            }
        }, "spraute-story-export");
        thread.setDaemon(true);
        thread.start();
    }

    private static void finishExport(ExportResult result, Exception errorEx) {
        exporting = false;
        exportProgress = 0f;
        exportDetail = "";
        if (errorEx != null) {
            error = EditorI18n.t("Ошибка экспорта: ") + errorEx.getMessage();
            status = "";
            return;
        }
        if (result != null) {
            status = result.statusLine();
            ProjectExportPanel.revealFile(result.file());
        }
    }

    private static void onWriterProgress(StoryPackWriter.Phase phase, float fraction, String detail) {
        exportProgress = fraction;
        exportDetail = switch (phase) {
            case COLLECT -> EditorI18n.t("Сбор файлов…");
            case OBFUSCATE -> EditorI18n.t("Обфускация: ") + detail;
            case META -> EditorI18n.t("Запись манифеста…");
            case PACK -> detail.isBlank()
                    ? EditorI18n.t("Упаковка…")
                    : EditorI18n.t("Упаковка: ") + detail;
            case WORLD -> detail.isBlank()
                    ? EditorI18n.t("Копирование мира…")
                    : EditorI18n.t("Мир: ") + detail;
        };
    }

    private static void drawBusyOverlay() {
        if (!exporting) return;
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float ww = ImGui.getWindowWidth();
        float wh = ImGui.getWindowHeight();
        ImDrawList fg = ImGui.getForegroundDrawList();
        fg.addRectFilled(wx, wy, wx + ww, wy + wh, ImGui.getColorU32(0.04f, 0.05f, 0.06f, 0.78f));

        float cardW = Math.min(440f, Math.max(280f, ww - 64f));
        float cardH = 148f;
        float cx = wx + (ww - cardW) * 0.5f;
        float cy = wy + (wh - cardH) * 0.5f;
        fg.addRectFilled(cx, cy, cx + cardW, cy + cardH, ImGui.getColorU32(0.11f, 0.14f, 0.13f, 0.98f), 10f);
        fg.addRect(cx, cy, cx + cardW, cy + cardH, ImGui.getColorU32(0.32f, 0.62f, 0.42f, 0.55f), 10f, 0, 1.2f);

        float spinCx = cx + cardW - 32f;
        float spinCy = cy + 28f;
        double t = ImGui.getTime();
        for (int i = 0; i < 8; i++) {
            double a = t * 6.0 + i * (Math.PI / 4.0);
            float x = spinCx + (float) Math.cos(a) * 9f;
            float y = spinCy + (float) Math.sin(a) * 9f;
            float alpha = 0.25f + 0.75f * ((i / 8f + (float) (t * 1.4)) % 1f);
            fg.addCircleFilled(x, y, 2.4f, ImGui.getColorU32(0.45f, 0.85f, 0.55f, alpha));
        }

        ImGui.setCursorScreenPos(cx + 20f, cy + 16f);
        ImGui.beginGroup();
        ImGui.textColored(0.92f, 0.96f, 0.93f, 1f, exportTitle);
        ImGui.dummy(1f, 8f);
        float bar = Math.max(0.03f, exportProgress);
        ImGui.progressBar(bar, cardW - 40f, 18f);
        int pct = Math.round(Math.max(0f, Math.min(1f, exportProgress)) * 100f);
        String detail = exportDetail == null || exportDetail.isBlank()
                ? EditorI18n.t("Идёт создание, игра не зависает")
                : exportDetail;
        ImGui.pushTextWrapPos(cx + cardW - 20f);
        ImGui.textDisabled(pct + "%  ·  " + detail);
        ImGui.popTextWrapPos();
        ImGui.endGroup();
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    private record ModEntry(String name, long size) {}
    private record WorldEntry(String name) {}
}
