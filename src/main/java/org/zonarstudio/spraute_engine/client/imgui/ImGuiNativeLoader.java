package org.zonarstudio.spraute_engine.client.imgui;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads imgui-java natives before any {@link imgui.ImGui} class touches JNI.
 * Also sets {@code imgui.library.path} so ImGui's static init does not unpack
 * a second copy into {@code java.io.tmpdir} (that drive is often the full one).
 */
final class ImGuiNativeLoader {
    private static final String IMGUI_LIB_PATH = "imgui.library.path";
    private static final String IMGUI_LIB_NAME = "imgui.library.name";

    private static boolean loaded;
    private static String lastError;

    private ImGuiNativeLoader() {}

    static String lastError() {
        return lastError;
    }

    static void ensureLoaded() {
        if (loaded) return;
        String libName = System.getProperty("os.arch", "").contains("64") ? "imgui-java64" : "imgui-java32";
        try {
            System.loadLibrary(libName);
            loaded = true;
            lastError = null;
            return;
        } catch (UnsatisfiedLinkError ignored) {
        }

        String ext = nativeExtension();
        ClassLoader loader = ImGuiNativeLoader.class.getClassLoader();
        List<String> tried = new ArrayList<>();
        UnsatisfiedLinkError lastLink = null;
        IOException lastIo = null;

        for (String fileName : candidateNativeFileNames(libName, ext)) {
            String resource = "io/imgui/java/native-bin/" + fileName;
            tried.add(resource);
            if (loader.getResource(resource) == null) continue;
            try {
                Path loadedFile = loadFromResource(loader, resource, stripExtension(fileName), ext);
                pointImGuiAt(loadedFile);
                loaded = true;
                lastError = null;
                return;
            } catch (IOException e) {
                lastIo = e;
                lastError = ioMessage(e);
            } catch (UnsatisfiedLinkError e) {
                lastLink = e;
            }
        }

        if (lastIo != null) {
            lastError = ioMessage(lastIo);
            throw new UnsatisfiedLinkError("Failed to extract ImGui native " + String.join(", ", tried)
                    + ": " + lastError);
        }
        lastError = lastLink != null
                ? lastLink.getMessage()
                : ("ImGui native not found on classpath, tried: " + String.join(", ", tried));
        throw new UnsatisfiedLinkError("Failed to extract ImGui native: " + lastError);
    }

    /** imgui-java 1.89 reads these instead of extracting into %TEMP%. */
    private static void pointImGuiAt(Path nativeFile) {
        if (nativeFile == null) return;
        Path file = nativeFile.toAbsolutePath();
        System.setProperty(IMGUI_LIB_PATH, file.getParent().toString());
        String name = file.getFileName().toString();
        if (!name.isBlank()) {
            System.setProperty(IMGUI_LIB_NAME, name);
        }
    }

    /**
     * Windows locks a loaded DLL, so REPLACE_EXISTING fails if Minecraft (or a leftover
     * process) still holds {@code natives/imgui-java64.dll}. Load the existing file first;
     * if overwrite is blocked, extract next to it under a unique name.
     */
    private static Path loadFromResource(ClassLoader loader, String resource, String libName, String ext)
            throws IOException {
        Path dir = nativeDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(libName + ext);
        if (Files.isRegularFile(target)) {
            try {
                System.load(target.toAbsolutePath().toString());
                return target;
            } catch (UnsatisfiedLinkError ignored) {
                // stale / wrong arch — try overwrite, then a unique file
            }
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing resource " + resource);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            System.load(target.toAbsolutePath().toString());
            return target;
        } catch (IOException locked) {
            Path alt = uniqueNativePath(dir, libName, ext);
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in == null) throw locked;
                Files.copy(in, alt, StandardCopyOption.REPLACE_EXISTING);
            }
            System.load(alt.toAbsolutePath().toString());
            return alt;
        }
    }

    private static Path uniqueNativePath(Path dir, String libName, String ext) throws IOException {
        String pid = Long.toString(ProcessHandle.current().pid());
        Path alt = dir.resolve(libName + "-" + pid + ext);
        if (!Files.exists(alt)) return alt;
        for (int i = 1; i < 32; i++) {
            alt = dir.resolve(libName + "-" + pid + "-" + i + ext);
            if (!Files.exists(alt)) return alt;
        }
        return Files.createTempFile(dir, libName + "-", ext);
    }

    private static String ioMessage(IOException e) {
        if (isDiskFull(e)) {
            return "Недостаточно места на диске. Освободите место и полностью перезапустите Minecraft.";
        }
        String msg = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            msg += ": " + e.getMessage();
        }
        return msg;
    }

    static boolean isDiskFull(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            String lower = m.toLowerCase(Locale.ROOT);
            if (lower.contains("недостаточно места") || lower.contains("there is not enough space")
                    || lower.contains("no space left") || lower.contains("not enough space")) {
                return true;
            }
        }
        return false;
    }

    /**
     * imgui-java ships Windows as {@code imgui-java64.dll}, but macOS/Linux as
     * {@code libimgui-java64.dylib}/{@code .so} (JNI {@code lib} prefix).
     */
    private static String[] candidateNativeFileNames(String libName, String ext) {
        if (".dll".equals(ext)) {
            return new String[]{libName + ext};
        }
        return new String[]{"lib" + libName + ext, libName + ext};
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Prefer game dir over system temp — temp drive is often the one that fills up. */
    private static Path nativeDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath()
                        .resolve("spraute_engine")
                        .resolve("natives");
            }
        } catch (Throwable ignored) {
        }
        return Path.of(System.getProperty("java.io.tmpdir", "."), "spraute_imgui");
    }

    private static String nativeExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return ".dylib";
        if (os.contains("win")) return ".dll";
        return ".so";
    }
}
