package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient;
import org.zonarstudio.spraute_engine.resource.FfmpegAudioConvert;
import org.zonarstudio.spraute_engine.resource.WorkspaceSoundIndex;
import org.zonarstudio.spraute_engine.script.ScriptConsole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Copies {@code .ogg}/{@code .mp3} into {@code spraute_engine/sounds/} and refreshes the picker. */
public final class EditorSoundImport {
    private static final String SOURCE = "Audio";

    private EditorSoundImport() {}

    public static List<String> pickAndImport() {
        List<String> ids = new ArrayList<>();
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer patterns = stack.mallocPointer(2);
            patterns.put(stack.UTF8("*.ogg"));
            patterns.put(stack.UTF8("*.mp3"));
            patterns.flip();
            String picked = TinyFileDialogs.tinyfd_openFileDialog(
                    EditorI18n.t("Импортировать аудио"),
                    "",
                    patterns,
                    EditorI18n.t("Аудио (.ogg, .mp3)"),
                    true);
            if (picked == null || picked.isBlank()) return ids;
            for (String raw : picked.split("\\|")) {
                if (raw == null || raw.isBlank()) continue;
                String id = importFile(Path.of(raw.trim()));
                if (id != null && !id.isBlank()) ids.add(id);
            }
        } catch (Throwable t) {
            ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось открыть диалог: ") + t.getMessage());
        }
        return ids;
    }

    public static String importFile(Path src) {
        if (src == null) return null;
        Path abs;
        try {
            abs = src.toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
        if (!Files.isRegularFile(abs) || !WorkspaceSoundIndex.isAudioFile(abs.getFileName().toString())) {
            ScriptConsole.warn(SOURCE, EditorI18n.t("Нужен файл .ogg или .mp3: ") + abs.getFileName());
            return null;
        }
        Path destDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve("sounds");
        try {
            Files.createDirectories(destDir);
            Path dest = uniqueDest(destDir, abs.getFileName().toString());
            Files.copy(abs, dest, StandardCopyOption.COPY_ATTRIBUTES);
            if (RemoteProjectClient.isRemote()) RemoteProjectClient.pushIfRemote(dest);
            EditorAssetIndex.refreshSounds();
            WorkspaceSoundIndex.Entry entry = findByFile(dest);
            if (entry != null && entry.mp3()) {
                WorkspaceSoundIndex.resolvePlayableOgg(
                        destDir.getParent(), entry.pathId());
                if (!FfmpegAudioConvert.available()) {
                    ScriptConsole.warn(SOURCE, EditorI18n.t(
                            "MP3 импортирован, но для воспроизведения нужен ffmpeg (PATH или spraute_engine/tools/ffmpeg)"));
                }
            }
            String id = entry == null ? "" : entry.fullId();
            if (!id.isBlank()) {
                org.zonarstudio.spraute_engine.client.audio.ClientWorkspaceSounds.prepare(id);
            }
            ScriptConsole.info(SOURCE, EditorI18n.t("Аудио импортировано: ")
                    + dest.getFileName()
                    + (id.isBlank() ? "" : " → " + id));
            return id;
        } catch (IOException e) {
            ScriptConsole.error(SOURCE, EditorI18n.t("Не удалось скопировать аудио: ") + e.getMessage());
            return null;
        }
    }

    private static WorkspaceSoundIndex.Entry findByFile(Path dest) {
        if (dest == null) return null;
        Path abs = dest.toAbsolutePath().normalize();
        for (WorkspaceSoundIndex.Entry e : WorkspaceSoundIndex.list()) {
            if (e.file() != null && e.file().toAbsolutePath().normalize().equals(abs)) return e;
        }
        return null;
    }

    private static Path uniqueDest(Path dir, String fileName) throws IOException {
        String safe = sanitizeFileName(fileName);
        if (safe.isBlank()) safe = "sound.ogg";
        Path dest = dir.resolve(safe);
        if (!Files.exists(dest)) return dest;
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        for (int i = 1; i < 10_000; i++) {
            Path candidate = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("too many name collisions: " + safe);
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_");
    }

    static String selectableLabel(String label, String id) {
        String shown = label == null || label.isBlank() ? id : label;
        if (shown.contains("##")) shown = shown.replace("##", "#");
        return shown;
    }
}
