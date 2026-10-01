package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.ui.VideoFrameCache;

/**
 * In-mod «Экспорт проекта» — same ZIP layout as Spraute Studio map export:
 * {@code mods/<spraute*.jar>}, selected {@code spraute_engine/...}, optional {@code saves/<world>/}.
 */
public final class ProjectExportPanel {
    private ProjectExportPanel() {}

    private static final ImString mapName = new ImString(96);
    private static final ImBoolean includeWorld = new ImBoolean(false);
    private static final ImBoolean TMP_CB = new ImBoolean();
    private static final Set<String> selectedSpraute = new HashSet<>();
    private static final List<ModEntry> mods = new ArrayList<>();
    private static final List<WorldEntry> worlds = new ArrayList<>();
    private static String selectedMod = "";
    private static String selectedWorld = "";
    private static String status = "";
    private static String error = "";
    private static boolean exporting;
    private static boolean needsRefresh = true;
    private static boolean openRequested;

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
        if (!show.get()) return;
        if (needsRefresh) {
            needsRefresh = false;
            refresh();
        }

        ImGui.setNextWindowSize(640f, 620f, ImGuiCond.FirstUseEver);
        if (!ImGui.begin(EditorI18n.t("Экспорт проекта###ProjectExport"), show, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        ImGui.textWrapped(EditorI18n.t("ZIP как в Spraute Studio: JAR мода, выбранное содержимое spraute_engine и мир по желанию."));
        ImGui.spacing();

        ImGui.text(EditorI18n.t("Название"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##export-name", mapName);

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("JAR Spraute Engine (mods/)");
        ImGui.beginChild("##export-mods", 0f, 90f, true);
        if (mods.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("Нет jar с «spraute» в имени в папке mods"));
        } else {
            for (ModEntry m : mods) {
                boolean sel = m.name.equals(selectedMod);
                if (ImGui.radioButton(m.name + "##mod-" + m.name, sel)) {
                    selectedMod = m.name;
                }
                ImGui.sameLine();
                ImGui.textDisabled(formatBytes(m.size));
            }
        }
        ImGui.endChild();

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Содержимое spraute_engine/"));
        if (ImGui.smallButton(EditorI18n.t("Выбрать всё"))) selectAllTopLevel();
        ImGui.sameLine();
        if (ImGui.smallButton(EditorI18n.t("Снять всё"))) selectedSpraute.clear();
        ImGui.beginChild("##export-spraute", 0f, 280f, true);
        Path sprauteRoot = gameDir().resolve("spraute_engine");
        if (!Files.isDirectory(sprauteRoot)) {
            ImGui.textDisabled(EditorI18n.t("Папка spraute_engine не найдена"));
        } else {
            ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 26f);
            renderTreeLevel(sprauteRoot, "", 0);
            ImGui.popStyleVar();
        }
        ImGui.endChild();

        ImGui.spacing();
        ImGui.checkbox(EditorI18n.t("Включить мир (saves/)"), includeWorld);
        if (includeWorld.get()) {
            ImGui.beginChild("##export-worlds", 0f, 100f, true);
            if (worlds.isEmpty()) {
                ImGui.textDisabled(EditorI18n.t("Нет миров с level.dat"));
            } else {
                for (WorldEntry w : worlds) {
                    boolean sel = w.name.equals(selectedWorld);
                    if (ImGui.radioButton(w.name + "##world-" + w.name, sel)) {
                        selectedWorld = w.name;
                    }
                }
            }
            ImGui.endChild();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(summaryLine());
        if (!error.isBlank()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.45f, 0.4f, 1f);
            ImGui.textWrapped(error);
            ImGui.popStyleColor();
        }
        if (!status.isBlank()) {
            ImGui.textColored(0.45f, 0.85f, 0.55f, 1f, status);
        }

        ImGui.spacing();
        boolean canExport = !exporting && !sanitizeName(mapName.get()).isBlank()
                && !selectedMod.isBlank() && !selectedSpraute.isEmpty()
                && (!includeWorld.get() || !selectedWorld.isBlank());
        if (!canExport) ImGui.beginDisabled();
        if (ImGui.button(exporting ? EditorI18n.t("Создание…") : EditorI18n.t("Создать ZIP"), 160f, 0f)) {
            runExport();
        }
        if (!canExport) ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Обновить список"), 140f, 0f)) refresh();
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Закрыть"), 100f, 0f)) show.set(false);

        ImGui.end();
    }

    private static void refresh() {
        error = "";
        status = "";
        if (mapName.get().isBlank()) mapName.set(EditorI18n.t("Мой проект"));
        mods.clear();
        worlds.clear();
        Path gd = gameDir();
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

        if (selectedSpraute.isEmpty()) selectAllTopLevel();
    }

    private static void selectAllTopLevel() {
        selectedSpraute.clear();
        Path root = gameDir().resolve("spraute_engine");
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.list(root)) {
            stream.forEach(p -> selectedSpraute.add(p.getFileName().toString().replace('\\', '/')));
        } catch (IOException ignored) {}
    }

    private static void renderTreeLevel(Path absDir, String rel, int depth) {
        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(absDir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        if (Files.isDirectory(p)) dirs.add(p);
                        else if (!isHiddenGeneratedSpr(p)) files.add(p);
                    });
        } catch (IOException e) {
            ImGui.textDisabled(EditorI18n.t("Ошибка чтения: ") + e.getMessage());
            return;
        }
        if (dirs.isEmpty() && files.isEmpty() && depth == 0) {
            ImGui.textDisabled(EditorI18n.t("Пусто"));
            return;
        }

        var guideDl = ImGui.getWindowDrawList();
        float guideX = ImGui.getCursorScreenPosX() - 16f;
        float guideTop = ImGui.getCursorScreenPosY() - 1f;
        float guideBottom = guideTop;
        int guideCol = ImGui.getColorU32(1f, 1f, 1f, 0.13f);

        for (Path child : dirs) {
            String name = child.getFileName().toString();
            String childRel = rel.isEmpty() ? name : rel + "/" + name;
            ImGui.pushID(childRel);
            renderSelectCheckbox(childRel);
            ImGui.sameLine(0f, 6f);

            boolean special = EditorIcons.isSpecialProjectFolder(name);
            float[] folderTint = EditorIcons.tintForProjectFolder(name);
            if (special) {
                ImGui.pushStyleColor(ImGuiCol.Text, folderTint[0], folderTint[1], folderTint[2], folderTint[3]);
            }
            boolean open = ImGui.treeNodeEx("     " + name + "/###dir",
                    ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth);
            String folderIcon = EditorIcons.forProjectFolder(name);
            if (open && "folder".equals(folderIcon)) folderIcon = "folder-open";
            EditorIcons.drawOverTreeLabel(folderIcon, 14f, folderTint);
            if (special) ImGui.popStyleColor();

            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
            if (open) {
                renderTreeLevel(child, childRel, depth + 1);
                ImGui.treePop();
            }
            ImGui.popID();
        }

        for (Path child : files) {
            String name = child.getFileName().toString();
            String childRel = rel.isEmpty() ? name : rel + "/" + name;
            ImGui.pushID(childRel);
            renderSelectCheckbox(childRel);
            ImGui.sameLine(0f, 6f);

            String kind = EditorIcons.projectFileKind(child);
            float[] tint = EditorIcons.tintForProjectFile(kind);
            ImGui.selectable("     " + name + "###file");
            EditorIcons.drawOverSelectableLabel(EditorIcons.forProjectFile(kind), 14f, tint);

            if (depth > 0) {
                float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                guideDl.addLine(guideX, cy, guideX + 9f, cy, guideCol, 1f);
                guideBottom = cy;
            }
            ImGui.popID();
        }

        if (depth > 0 && guideBottom > guideTop) {
            guideDl.addLine(guideX, guideTop, guideX, guideBottom, guideCol, 1f);
        }
    }

    private static void renderSelectCheckbox(String childRel) {
        boolean exact = selectedSpraute.contains(childRel);
        boolean covered = isCovered(childRel);
        boolean underAncestor = covered && !exact;
        TMP_CB.set(covered);
        if (underAncestor) ImGui.beginDisabled();
        boolean changed = ImGui.checkbox("##c", TMP_CB);
        if (underAncestor) ImGui.endDisabled();
        if (changed && !underAncestor) {
            if (TMP_CB.get()) pruneAdd(childRel);
            else selectedSpraute.remove(childRel);
        }
    }

    /** Hide generated .spr when a sibling .sprv exists — same as the project explorer. */
    private static boolean isHiddenGeneratedSpr(Path file) {
        String fn = file.getFileName().toString();
        if (!fn.toLowerCase(Locale.ROOT).endsWith(".spr")) return false;
        Path sprv = file.resolveSibling(fn.substring(0, fn.length() - 4) + ".sprv");
        return Files.isRegularFile(sprv);
    }

    private static boolean isCovered(String rel) {
        if (selectedSpraute.contains(rel)) return true;
        String[] parts = rel.split("/");
        StringBuilder anc = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) anc.append('/');
            anc.append(parts[i]);
            if (selectedSpraute.contains(anc.toString())) return true;
        }
        return false;
    }

    private static void pruneAdd(String rel) {
        selectedSpraute.add(rel);
        selectedSpraute.removeIf(p -> !p.equals(rel) && p.startsWith(rel + "/"));
    }

    private static List<String> minimizedPaths() {
        List<String> norm = selectedSpraute.stream()
                .map(p -> p.replace('\\', '/').replaceAll("^/+", ""))
                .filter(p -> !p.isBlank())
                .distinct()
                .sorted()
                .toList();
        List<String> result = new ArrayList<>();
        for (String p : norm) {
            if (result.stream().anyMatch(r -> p.equals(r) || p.startsWith(r + "/"))) continue;
            result.add(p);
        }
        return result;
    }

    private static String summaryLine() {
        List<String> parts = new ArrayList<>();
        if (!selectedMod.isBlank()) parts.add(EditorI18n.t("мод"));
        if (!selectedSpraute.isEmpty()) parts.add("spraute_engine (" + selectedSpraute.size() + ")");
        if (includeWorld.get() && !selectedWorld.isBlank()) parts.add(EditorI18n.t("мир"));
        return parts.isEmpty() ? EditorI18n.t("Выберите содержимое для архива") : EditorI18n.t("В архив: ") + String.join(", ", parts);
    }

    private static void runExport() {
        error = "";
        status = "";
        String name = sanitizeName(mapName.get());
        if (name.isBlank()) {
            error = EditorI18n.t("Укажите название проекта");
            return;
        }
        if (selectedMod.isBlank()) {
            error = EditorI18n.t("Выберите JAR Spraute Engine в папке mods");
            return;
        }
        List<String> paths = minimizedPaths();
        if (paths.isEmpty()) {
            error = EditorI18n.t("Выберите хотя бы один файл или папку из spraute_engine");
            return;
        }
        if (includeWorld.get() && selectedWorld.isBlank()) {
            error = EditorI18n.t("Выберите мир или отключите «Включить мир»");
            return;
        }

        exporting = true;
        try {
            Path outDir = gameDir().resolve("exports");
            Files.createDirectories(outDir);
            Path outZip = outDir.resolve(name + ".zip");
            createZip(outZip, selectedMod, paths, includeWorld.get() ? selectedWorld : null);
            status = EditorI18n.t("Готово: ") + outZip;
            revealFile(outZip);
        } catch (Exception e) {
            error = EditorI18n.t("Ошибка экспорта: ") + e.getMessage();
        } finally {
            exporting = false;
        }
    }

    static void createZip(Path outZip, String modFileName, List<String> sprautePaths, String worldName)
            throws IOException {
        Path gd = gameDir();
        Path modsRoot = gd.resolve("mods").normalize();
        Path modAbs = modsRoot.resolve(modFileName).normalize();
        if (!modAbs.startsWith(modsRoot) || !Files.isRegularFile(modAbs)) {
            throw new IOException(EditorI18n.t("Выбранный JAR не найден"));
        }
        Path sprauteRoot = gd.resolve("spraute_engine").normalize();
        List<String> packed = VideoFrameCache.attachPackedCaches(sprauteRoot, sprautePaths, null);

        try (OutputStream fos = Files.newOutputStream(outZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putFile(zos, modAbs, "mods/" + modFileName);

            for (String rel : packed) {
                Path abs = sprauteRoot.resolve(rel).normalize();
                if (!abs.startsWith(sprauteRoot) || !Files.exists(abs)) {
                    throw new IOException(EditorI18n.t("Путь недоступен: ") + rel);
                }
                String zipBase = "spraute_engine/" + rel.replace('\\', '/');
                if (Files.isDirectory(abs)) {
                    putFolder(zos, abs, zipBase);
                } else {
                    putFile(zos, abs, zipBase);
                }
            }

            if (worldName != null && !worldName.isBlank()) {
                if (worldName.contains("..") || worldName.contains("/") || worldName.contains("\\")) {
                    throw new IOException(EditorI18n.t("Некорректное имя мира"));
                }
                Path worldAbs = gd.resolve("saves").resolve(worldName).normalize();
                if (!Files.isRegularFile(worldAbs.resolve("level.dat"))) {
                    throw new IOException(EditorI18n.t("Мир не найден или повреждён (нет level.dat)"));
                }
                putFolder(zos, worldAbs, "saves/" + worldName);
            }
        }
    }

    private static void putFolder(ZipOutputStream zos, Path folder, String zipPrefix) throws IOException {
        String prefix = zipPrefix.endsWith("/") ? zipPrefix : zipPrefix + "/";
        zos.putNextEntry(new ZipEntry(prefix));
        zos.closeEntry();
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = folder.relativize(file).toString().replace('\\', '/');
                putFile(zos, file, prefix + rel);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(folder)) return FileVisitResult.CONTINUE;
                String rel = folder.relativize(dir).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(prefix + rel + "/"));
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void putFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    private static String sanitizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
    }

    private static String formatBytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", n / (1024.0 * 1024.0));
    }

    /** Opens the OS file manager with {@code path} selected (also used by scene export). */
    static void revealFile(Path path) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            Path target = path.toAbsolutePath().normalize();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", target.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", target.toString()).start();
            } else {
                Path parent = target.getParent();
                if (parent != null) new ProcessBuilder("xdg-open", parent.toString()).start();
            }
        } catch (Exception ignored) {}
    }

    private record ModEntry(String name, long size) {}
    private record WorldEntry(String name) {}
}
