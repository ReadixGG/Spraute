package org.sprautestudio.st_addon_spraute.editor.assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Audio the game cannot read (MP3, M4A, AAC, WMA, FLAC) turned into WAV by Windows' own Media Foundation — no extra
 * programs. Runs a small PowerShell script shipped in the mod.
 */
public final class SoundConvert {
    private SoundConvert() {
    }

    public static final List<String> CONVERTIBLE = List.of(".mp3", ".m4a", ".aac", ".wma", ".flac");

    public static boolean convertible(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        for (String ext : CONVERTIBLE) if (n.endsWith(ext)) return true;
        return false;
    }

    private static Path script;

    private static synchronized Path script() throws IOException {
        if (script != null && Files.isRegularFile(script)) return script;
        Path p = Files.createTempFile("spraute_towav", ".ps1");
        try (InputStream in = SoundConvert.class.getResourceAsStream("/assets/st_addon_spraute/tools/towav.ps1")) {
            if (in == null) throw new IOException("нет скрипта конвертации в моде");
            Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
        }
        p.toFile().deleteOnExit();
        script = p;
        return p;
    }

    /** Converts source into dir/name.wav (blocking, up to two minutes); the result's path. */
    public static Path toWav(Path source, Path dir, String name) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win"))
            throw new IOException("Конвертация есть только в Windows. Сохраните файл как OGG или WAV");
        Files.createDirectories(dir);
        String file = name + ".wav";
        Process pr = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                script().toString(), "-in", source.toAbsolutePath().toString(), "-outDir", dir.toAbsolutePath().toString(), "-outName", file)
                .redirectErrorStream(true).start();
        String out;
        try {
            if (!pr.waitFor(120, TimeUnit.SECONDS)) {
                pr.destroyForcibly();
                throw new IOException("конвертация длится слишком долго");
            }
            out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("прервано");
        }
        Path res = dir.resolve(file);
        if (pr.exitValue() != 0 || !Files.isRegularFile(res) || Files.size(res) < 64) {
            Files.deleteIfExists(res);
            String last = out.isEmpty() ? "неизвестная ошибка" : out.lines().reduce((a, b) -> b).orElse(out);
            throw new IOException("Windows не смогла прочитать файл: " + last);
        }
        return res;
    }
}
