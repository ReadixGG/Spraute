package org.zonarstudio.spraute_engine.client.editor;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.zonarstudio.spraute_engine.script.ScriptConsole;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * OS → Project explorer file drop via GLFW {@code glfwSetDropCallback}.
 * Hit-tests the last Project panel layout (folder/file rows) at drop cursor.
 */
public final class ProjectOsFileDrop {
    private static final String SOURCE = "Project";

    private final Object lock = new Object();
    private final List<Hit> hits = new ArrayList<>();
    private float winMinX;
    private float winMinY;
    private float winMaxX;
    private float winMaxY;
    private boolean windowLive;
    private Path projectRoot;
    private Consumer<List<Path>> onImported;

    private GLFWDropCallback dropCallback;
    private GLFWDropCallback previousCallback;
    private boolean installed;

    public void setOnImported(Consumer<List<Path>> onImported) {
        this.onImported = onImported;
    }

    public void beginFrame(Path root) {
        synchronized (lock) {
            hits.clear();
            windowLive = false;
            projectRoot = root != null ? root.toAbsolutePath().normalize() : null;
        }
    }

    public void noteWindowBounds(float minX, float minY, float maxX, float maxY) {
        synchronized (lock) {
            windowLive = true;
            winMinX = minX;
            winMinY = minY;
            winMaxX = maxX;
            winMaxY = maxY;
        }
    }

    /** Folder row: drop copies into this directory. */
    public void noteFolderHit(Path dir, float minX, float minY, float maxX, float maxY) {
        if (dir == null) return;
        synchronized (lock) {
            hits.add(new Hit(dir.toAbsolutePath().normalize(), minX, minY, maxX, maxY, true));
        }
    }

    /** File row: drop copies into the file's parent directory. */
    public void noteFileHit(Path file, float minX, float minY, float maxX, float maxY) {
        if (file == null || file.getParent() == null) return;
        synchronized (lock) {
            hits.add(new Hit(file.getParent().toAbsolutePath().normalize(), minX, minY, maxX, maxY, false));
        }
    }

    /**
     * Highlights the folder under the mouse only while an OS file drag is active
     * (Explorer → game). Detected on Windows as: LMB down globally, but ImGui
     * never saw the press (button-down happened in another window).
     */
    public void drawHoverHighlight() {
        if (!isExternalOsFileDrag()) return;

        final List<Hit> snapshot;
        final Path root;
        final boolean live;
        synchronized (lock) {
            if (!windowLive || hits.isEmpty()) return;
            snapshot = List.copyOf(hits);
            root = projectRoot;
            live = windowLive;
        }
        if (!live || root == null) return;

        float mx = ImGui.getIO().getMousePosX();
        float my = ImGui.getIO().getMousePosY();
        Hit under = null;
        for (Hit hit : snapshot) {
            if (mx >= hit.minX && mx <= hit.maxX && my >= hit.minY && my <= hit.maxY) {
                under = hit;
            }
        }
        if (under == null) return;

        Hit visual = under;
        if (!under.folderRow) {
            for (Hit hit : snapshot) {
                if (hit.folderRow && hit.dir.equals(under.dir)) {
                    visual = hit;
                    break;
                }
            }
        }

        ImDrawList dl = ImGui.getForegroundDrawList();
        int fill = ImGui.getColorU32(0.18f, 0.62f, 0.38f, 0.32f);
        int border = ImGui.getColorU32(0.35f, 0.92f, 0.55f, 0.95f);
        int textCol = ImGui.getColorU32(0.55f, 1f, 0.72f, 1f);
        float pad = 1f;
        dl.addRectFilled(visual.minX - pad, visual.minY - pad,
                visual.maxX + pad, visual.maxY + pad, fill, 3f);
        dl.addRect(visual.minX - pad, visual.minY - pad,
                visual.maxX + pad, visual.maxY + pad, border, 3f, 0, 1.5f);

        String label = folderLabel(root, under.dir);
        String hint = EditorI18n.t("сюда: ") + label;
        float textW = ImGui.calcTextSize(hint).x;
        float tx = Math.min(visual.maxX - textW - 6f, visual.maxX - 8f);
        if (tx < visual.minX + 4f) tx = visual.minX + 4f;
        float ty = visual.minY + Math.max(0f, (visual.maxY - visual.minY - ImGui.getTextLineHeight()) * 0.5f);
        dl.addText(tx, ty, textCol, hint);
    }

    /**
     * True while the user is dragging something with LMB from outside the game
     * (e.g. Explorer files). Inside-game click-drags set ImGui mouseDown too.
     */
    private static boolean isExternalOsFileDrag() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) return false;
        try {
            // VK_LBUTTON = 0x01 — still down for the whole Explorer drag.
            short state = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01);
            boolean globallyDown = (state & 0x8000) != 0;
            if (!globallyDown) return false;
            // Press originated in another window → ImGui never got MouseDown.
            return !ImGui.getIO().getMouseDown(0);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String folderLabel(Path root, Path dir) {
        String rel = relativeDisplay(root, dir);
        if (rel == null || rel.isBlank() || ".".equals(rel)) return "spraute_engine/";
        return rel.endsWith("/") ? rel : rel + "/";
    }

    public void install() {
        if (installed) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        dropCallback = GLFWDropCallback.create((win, count, names) -> {
            if (count <= 0) return;
            String[] paths = new String[count];
            for (int i = 0; i < count; i++) {
                paths[i] = GLFWDropCallback.getName(names, i);
            }
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(win, xpos, ypos);
            float dropX = (float) xpos[0];
            float dropY = (float) ypos[0];
            final List<Hit> snapshot;
            final boolean live;
            final float w0, w1, h0, h1;
            final Path root;
            synchronized (lock) {
                snapshot = List.copyOf(hits);
                live = windowLive;
                w0 = winMinX;
                w1 = winMaxX;
                h0 = winMinY;
                h1 = winMaxY;
                root = projectRoot;
            }
            Minecraft.getInstance().tell(() ->
                    handleDrop(paths, dropX, dropY, snapshot, live, w0, h0, w1, h1, root));
        });
        previousCallback = GLFW.glfwSetDropCallback(window, dropCallback);
        installed = true;
    }

    public void uninstall() {
        if (!installed) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetDropCallback(window, previousCallback);
        if (dropCallback != null) {
            dropCallback.free();
            dropCallback = null;
        }
        previousCallback = null;
        installed = false;
        synchronized (lock) {
            hits.clear();
            windowLive = false;
        }
    }

    private void handleDrop(String[] paths, float dropX, float dropY,
                            List<Hit> snapshot, boolean live,
                            float w0, float h0, float w1, float h1, Path root) {
        if (paths == null || paths.length == 0 || root == null) return;
        if (!live || dropX < w0 || dropX > w1 || dropY < h0 || dropY > h1) {
            importDroppedAudio(paths);
            return;
        }

        Path target = resolveTargetDir(dropX, dropY, snapshot);
        if (target == null) {
            if (RemoteProjectClient.isRemote()) {
                target = root;
            } else {
                target = pickFolderDialog(root);
                if (target == null) return;
            }
        }
        target = target.toAbsolutePath().normalize();
        if (!isUnder(root, target)) {
            ScriptConsole.warn(SOURCE, "Папка вне проекта: " + target);
            return;
        }
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            ScriptConsole.error(SOURCE, "Не удалось создать папку: " + e.getMessage());
            return;
        }

        int copied = 0;
        int skipped = 0;
        List<Path> imported = new ArrayList<>();
        for (String raw : paths) {
            if (raw == null || raw.isBlank()) continue;
            Path src;
            try {
                src = Path.of(raw).toAbsolutePath().normalize();
            } catch (Exception e) {
                skipped++;
                continue;
            }
            if (!Files.exists(src)) {
                skipped++;
                continue;
            }
            boolean dir = Files.isDirectory(src);
            // Don't copy a folder into itself / nested under itself.
            if (dir && (src.equals(target) || isUnder(src, target))) {
                ScriptConsole.warn(SOURCE, "Пропуск (вложенность): " + src.getFileName());
                skipped++;
                continue;
            }
            try {
                // Dedicated: keep the original name and overwrite. uniqueDest would
                // turn an already-synced imya.png into imya (1).png and upload a duplicate.
                boolean overwrite = RemoteProjectClient.isRemote();
                Path destDir = target;
                if (!dir && org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex
                        .isAudioFile(src.getFileName().toString())
                        && !isUnder(root.resolve("sounds"), destDir)
                        && !destDir.equals(root.resolve("sounds").toAbsolutePath().normalize())) {
                    destDir = root.resolve("sounds");
                    Files.createDirectories(destDir);
                }
                Path dest = overwrite
                        ? destSameName(destDir, src.getFileName().toString())
                        : uniqueDest(destDir, src.getFileName().toString(), dir);
                if (dir) {
                    copyDirectory(src, dest, overwrite);
                } else if (overwrite) {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                }
                imported.add(dest);
                copied++;
            } catch (IOException e) {
                ScriptConsole.error(SOURCE, "Не удалось скопировать: "
                        + src.getFileName() + " — " + e.getMessage());
                skipped++;
            }
        }

        refreshImportedAudio(imported);

        if (copied > 0) {
            Path shownDir = imported.isEmpty() ? target : imported.get(imported.size() - 1);
            if (shownDir != null && Files.isRegularFile(shownDir)) shownDir = shownDir.getParent();
            String destLabel = relativeDisplay(root, shownDir == null ? target : shownDir);
            if (destLabel.isEmpty()) destLabel = "spraute_engine";
            ScriptConsole.info(SOURCE, "Импортировано файлов: " + copied
                    + " → " + destLabel);
            if (onImported != null) onImported.accept(imported);
        } else if (skipped > 0) {
            ScriptConsole.warn(SOURCE, "Ничего не импортировано");
        }
    }

    private void importDroppedAudio(String[] paths) {
        if (paths == null) return;
        List<Path> imported = new ArrayList<>();
        for (String raw : paths) {
            if (raw == null || raw.isBlank()) continue;
            Path src;
            try {
                src = Path.of(raw.trim());
            } catch (Exception e) {
                continue;
            }
            if (!Files.isRegularFile(src)
                    || !org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex
                    .isAudioFile(src.getFileName().toString())) {
                continue;
            }
            String id = org.zonarstudio.spraute_engine.client.editor.scene.EditorSoundImport.importFile(src);
            if (id != null && !id.isBlank()) imported.add(src);
        }
        if (!imported.isEmpty() && onImported != null) {
            onImported.accept(imported);
        }
    }

    private static void refreshImportedAudio(List<Path> imported) {
        boolean any = false;
        for (Path dest : imported) {
            if (dest != null && Files.isRegularFile(dest)
                    && org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex
                    .isAudioFile(dest.getFileName().toString())) {
                any = true;
                break;
            }
        }
        if (!any) return;
        org.zonarstudio.spraute_engine.client.editor.scene.EditorAssetIndex.refreshSounds();
        for (var e : org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex.list()) {
            if (e.mp3()) {
                org.zonarstudio.spraute_engine.client.audio.ClientWorkspaceSounds.prepare(e.fullId());
            }
        }
    }

    private static Path resolveTargetDir(float x, float y, List<Hit> snapshot) {
        // Later hits are more nested / drawn on top — last match wins.
        Path found = null;
        for (Hit hit : snapshot) {
            if (x >= hit.minX && x <= hit.maxX && y >= hit.minY && y <= hit.maxY) {
                found = hit.dir;
            }
        }
        return found;
    }

    private static Path pickFolderDialog(Path projectRoot) {
        try {
            String picked = TinyFileDialogs.tinyfd_selectFolderDialog(
                    EditorI18n.t("Куда импортировать файлы?"),
                    projectRoot.toAbsolutePath().toString());
            if (picked == null || picked.isBlank()) return null;
            Path chosen = Path.of(picked).toAbsolutePath().normalize();
            if (!isUnder(projectRoot, chosen)) {
                ScriptConsole.warn(SOURCE, EditorI18n.t("Папка вне проекта: ") + chosen);
                return null;
            }
            return chosen;
        } catch (Throwable t) {
            ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось открыть диалог: ") + t.getMessage());
            return null;
        }
    }

    private static Path destSameName(Path dir, String fileName) {
        String safe = sanitizeFileName(fileName);
        if (safe.isBlank()) safe = "dropped";
        return dir.resolve(safe);
    }

    private static Path uniqueDest(Path dir, String fileName, boolean isDirectory) throws IOException {
        String safe = sanitizeFileName(fileName);
        if (safe.isBlank()) safe = "dropped";
        Path dest = dir.resolve(safe);
        if (!Files.exists(dest)) return dest;
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (!isDirectory && dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        for (int i = 1; i < 10_000; i++) {
            Path candidate = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("too many name collisions: " + safe);
    }

    private static void copyDirectory(Path src, Path dest, boolean overwrite) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                Path targetDir = rel.toString().isEmpty() ? dest : dest.resolve(rel);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                if (overwrite) {
                    Files.copy(file, dest.resolve(rel),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    Files.copy(file, dest.resolve(rel), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isUnder(Path root, Path path) {
        Path r = root.toAbsolutePath().normalize();
        Path p = path.toAbsolutePath().normalize();
        return p.startsWith(r);
    }

    private static String relativeDisplay(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString();
        }
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_");
    }

    private static final class Hit {
        final Path dir;
        final float minX, minY, maxX, maxY;
        final boolean folderRow;

        Hit(Path dir, float minX, float minY, float maxX, float maxY, boolean folderRow) {
            this.dir = dir;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.folderRow = folderRow;
        }
    }
}
