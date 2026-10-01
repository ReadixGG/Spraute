package org.zonarstudio.spraute_engine.client.imgui;

import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImFontAtlas;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.config.SprauteConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SprauteImGuiBackend {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static ImGuiImplGlfw GLFW;
    private static ImGuiImplGl3 GL3;
    private static boolean initialized;
    private static boolean initFailed;
    private static String initError;
    private static ImFont codeFont;
    /** True while the editor UI is on screen (or free-flight overlay is drawing ImGui). */
    private static boolean acceptingInput;
    /** Apply theme + FontGlobalScale at the start of the next frame (avoids mid-frame jumps). */
    private static boolean pendingUiScaleApply;
    /** Java-owned imgui.ini path — native fopen cannot open Cyrillic game dirs. */
    private static Path iniPath;
    private static boolean hadSavedLayout;

    private SprauteImGuiBackend() {}

    public static boolean isAvailable() {
        if (initFailed) return false;
        ensureInitialized();
        return initialized;
    }

    /** Human-readable reason when {@link #isAvailable()} is false after a failed init. */
    public static String initError() {
        if (!initFailed) return null;
        if (initError != null && !initError.isBlank()) return initError;
        String nativeErr = ImGuiNativeLoader.lastError();
        return nativeErr != null ? nativeErr : "ImGui init failed";
    }

    public static void render(Runnable content) {
        if (!isAvailable()) return;
        if (pendingUiScaleApply) {
            applyUiScale();
            pendingUiScaleApply = false;
        }
        if (GL3 == null || GLFW == null) return;
        GL3.newFrame();
        GLFW.newFrame();
        ImGui.newFrame();
        content.run();
        ImGui.render();
        GL3.renderDrawData(ImGui.getDrawData());
        flushIniIfNeeded();
    }

    public static float uiScale() {
        return SprauteConfig.get().editorUiScale;
    }

    public static void setUiScale(float scale) {
        SprauteConfig cfg = SprauteConfig.get();
        float prev = cfg.editorUiScale;
        cfg.editorUiScale = scale;
        cfg.normalize();
        if (Math.abs(cfg.editorUiScale - prev) < 0.0001f) return;
        SprauteConfig.save();
        pendingUiScaleApply = true;
    }

    public static void nudgeUiScale(int steps) {
        setUiScale(uiScale() + steps * SprauteConfig.UI_SCALE_STEP);
    }

    /**
     * Re-applies the base theme, then scales padding/rounding and fonts.
     * Must not be called between {@code newFrame} and {@code render} of the same pass
     * if style was already sampled — prefer {@link #setUiScale(float)}.
     */
    public static void applyUiScale() {
        if (!initialized) return;
        applyThemeAndScale();
    }

    /** Theme + scale; safe after {@code ImGui.createContext()} even before {@link #initialized}. */
    private static void applyThemeAndScale() {
        float scale = uiScale();
        SprauteImGuiTheme.apply();
        if (Math.abs(scale - 1f) > 0.001f) {
            ImGui.getStyle().scaleAllSizes(scale);
        }
        // Keep docked panels shrinkable. scaleAllSizes grows WindowMinSize, and
        // FontGlobalScale already enlarges text — a large min size also reflows the dock.
        ImGui.getStyle().setWindowMinSize(48f, 24f);
        // Splitter hit/draw size must stay a few pixels, not accumulate with scale.
        ImGui.getStyle().setDockingSeparatorSize(Math.max(2f, Math.min(4f, 2f * scale)));
        ImGui.getIO().setFontGlobalScale(scale);
    }

    /** Monospace font for the script editor, or null to keep the UI default. */
    public static ImFont codeFont() {
        if (!isAvailable()) return null;
        return codeFont;
    }

    /**
     * GLFW callbacks stay installed for the whole client session. While the editor
     * is closed they would still enqueue every gameplay click/key into ImGui —
     * the backlog then fires as phantom input on the next open. Gate with
     * {@link ImGuiIO#setAppAcceptingEvents(boolean)} and flush the queue on toggle.
     */
    public static void setAcceptingInput(boolean accepting) {
        if (!accepting) {
            // Do not init ImGui just to close the gate.
            if (!initialized) {
                acceptingInput = false;
                return;
            }
            ImGuiIO io = ImGui.getIO();
            io.setAppAcceptingEvents(false);
            flushInputState();
            acceptingInput = false;
            return;
        }
        if (!isAvailable()) return;
        // Always flush when (re)opening so a leftover queue from gameplay cannot
        // replay as phantom clicks/keys on the first editor frames.
        flushInputState();
        ImGui.getIO().setAppAcceptingEvents(true);
        acceptingInput = true;
    }

    public static boolean isAcceptingInput() {
        return acceptingInput;
    }

    /** Drop queued events and zero transient mouse/key edge state. */
    public static void flushInputState() {
        if (!initialized) return;
        ImGuiIO io = ImGui.getIO();
        io.clearEventsQueue();
        boolean[] mouse = io.getMouseDown();
        if (mouse != null) {
            for (int i = 0; i < mouse.length; i++) {
                io.setMouseDown(i, false);
            }
        }
        io.setMouseDelta(0f, 0f);
        io.setMouseWheel(0f);
        io.setMouseWheelH(0f);
        io.setKeyCtrl(false);
        io.setKeyShift(false);
        io.setKeyAlt(false);
        io.setKeySuper(false);
        io.setNavActive(false);
        io.setNavVisible(false);
        boolean[] keys = io.getKeysDown();
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                io.setKeysDown(i, false);
            }
        }
    }

    private static void ensureInitialized() {
        if (initialized || initFailed) return;
        try {
            ImGuiNativeLoader.ensureLoaded();
            Minecraft mc = Minecraft.getInstance();
            ImGui.createContext();
            ImGuiIO io = ImGui.getIO();
            // Keyboard nav steals arrows/Space/Enter and feels like "ghost" input
            // when any key state is desynced after opening from gameplay.
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
            configureIniPersistence(mc, io);
            loadEditorFonts(mc, io);
            applyThemeAndScale();
            loadIniFromDisk();

            long window = mc.getWindow().getWindow();
            // Callback chaining keeps Minecraft input and ImGui input active together.
            GLFW = new ImGuiImplGlfw();
            GL3 = new ImGuiImplGl3();
            GLFW.init(window, true);
            GL3.init("#version 150");
            // Ignore gameplay input until the editor UI actually wants it.
            io.setAppAcceptingEvents(false);
            acceptingInput = false;
            initialized = true;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            initFailed = true;
            initError = describeInitFailure(e);
            LOGGER.error("[Spraute Engine] ImGui failed to init (editor disabled): {}", initError);
        }
    }

    private static String describeInitFailure(Throwable e) {
        if (ImGuiNativeLoader.lastError() != null && !ImGuiNativeLoader.lastError().isBlank()) {
            return ImGuiNativeLoader.lastError();
        }
        if (ImGuiNativeLoader.isDiskFull(e)) {
            return "Недостаточно места на диске. Освободите место и полностью перезапустите Minecraft.";
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage();
        }
        return e.toString();
    }

    /**
     * Native ImGui fopen cannot write Cyrillic paths. Keep IniFilename null and
     * read/write the layout file through Java NIO instead.
     */
    private static void configureIniPersistence(Minecraft mc, ImGuiIO io) {
        io.setIniFilename(null);
        iniPath = mc.gameDirectory.toPath()
                .resolve("config")
                .resolve("spraute_editor_imgui.ini");
    }

    private static void loadIniFromDisk() {
        hadSavedLayout = false;
        if (iniPath == null) return;
        try {
            if (!Files.isRegularFile(iniPath) || Files.size(iniPath) < 80) return;
            String data = Files.readString(iniPath, StandardCharsets.UTF_8);
            if (data == null || data.isBlank()) return;
            ImGui.loadIniSettingsFromMemory(data);
            hadSavedLayout = true;
        } catch (Exception e) {
            LOGGER.warn("[Spraute Engine] ImGui layout load failed: {}", e.toString());
        }
    }

    public static boolean hadSavedLayout() {
        return hadSavedLayout;
    }

    public static Path iniPath() {
        return iniPath;
    }

    public static void saveIniNow() {
        if (!initialized || iniPath == null) return;
        try {
            Files.createDirectories(iniPath.getParent());
            String data = ImGui.saveIniSettingsToMemory();
            if (data == null || data.isBlank()) return;
            Files.writeString(iniPath, data, StandardCharsets.UTF_8);
            hadSavedLayout = true;
            ImGui.getIO().setWantSaveIniSettings(false);
        } catch (Exception e) {
            LOGGER.warn("[Spraute Engine] ImGui layout save failed: {}", e.toString());
        }
    }

    private static void flushIniIfNeeded() {
        if (!initialized) return;
        if (ImGui.getIO().getWantSaveIniSettings()) saveIniNow();
    }

    private static ImFont loadFontTtf(ImFontAtlas atlas, Path fontPath, float sizePx, short[] ranges) {
        if (fontPath == null) return null;
        try {
            byte[] bytes = Files.readAllBytes(fontPath);
            if (bytes.length == 0) return null;
            return atlas.addFontFromMemoryTTF(bytes, sizePx, ranges);
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Failed to read font {}: {}", fontPath, e.getMessage());
            return null;
        } catch (Throwable t) {
            LOGGER.error("[Spraute Engine] ImGui font load failed for {}: {}", fontPath, t.toString());
            return null;
        }
    }

    private static void loadEditorFonts(Minecraft mc, ImGuiIO io) {
        Path fontsDir = mc.gameDirectory.toPath().resolve("spraute_engine").resolve("fonts");
        var ranges = new imgui.ImFontGlyphRangesBuilder();
        ranges.addRanges(io.getFonts().getGlyphRangesCyrillic());
        // Extra symbols used in the visual editor (must be merged in sorted order).
        ranges.addText("…–—→▾▸▲▼✓⚠");
        short[] cyrillic = ranges.buildRanges();
        ImFontAtlas atlas = io.getFonts();
        try {
            Files.createDirectories(fontsDir);
        } catch (IOException ignored) {
        }

        Path uiFont = null;
        try {
            uiFont = firstFontIn(fontsDir);
        } catch (IOException ignored) {
        }
        if (uiFont == null) {
            uiFont = findFirstExisting(systemUiFontCandidates());
        }
        if (uiFont != null) {
            LOGGER.info("[Spraute Engine] ImGui UI font: {}", uiFont.toAbsolutePath());
            if (loadFontTtf(atlas, uiFont, 16f, cyrillic) == null) {
                LOGGER.warn("[Spraute Engine] Falling back to default ImGui font for UI");
                atlas.addFontDefault();
            }
        } else {
            LOGGER.error("[Spraute Engine] No Cyrillic UI font found (put a .ttf in spraute_engine/fonts/) — text may show as ????");
            atlas.addFontDefault();
        }

        Path mono = firstMatchingFont(fontsDir, List.of("mono", "consolas", "cascadia", "jetbrains", "firacode", "sourcecode", "courier", "menlo"));
        if (mono == null) {
            mono = findFirstExisting(systemMonoFontCandidates());
        }
        // Reuse UI font for code if no mono — still better than ProggyClean for Cyrillic comments.
        if (mono == null) {
            mono = uiFont;
        }
        if (mono != null) {
            LOGGER.info("[Spraute Engine] ImGui code font: {}", mono.toAbsolutePath());
            codeFont = loadFontTtf(atlas, mono, 15f, cyrillic);
        }
    }

    private static Path firstFontIn(Path fontsDir) throws IOException {
        if (!Files.isDirectory(fontsDir)) return null;
        try (var files = Files.list(fontsDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".ttf") || name.endsWith(".otf");
                    })
                    // Prefer bundled Noto (Cyrillic) over random first file.
                    .sorted(Comparator
                            .comparing((Path p) -> !p.getFileName().toString().toLowerCase(Locale.ROOT).contains("noto"))
                            .thenComparing(Path::toString))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path firstMatchingFont(Path fontsDir, List<String> needles) {
        try {
            if (!Files.isDirectory(fontsDir)) return null;
            try (var files = Files.list(fontsDir)) {
                return files
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            if (!(name.endsWith(".ttf") || name.endsWith(".otf"))) return false;
                            for (String n : needles) {
                                if (name.contains(n)) return true;
                            }
                            return false;
                        })
                        .sorted(Comparator.comparing(Path::toString))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findFirstExisting(List<String> candidates) {
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            Path path = Path.of(c);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    /** Prefer TTF (not TTC) — imgui-java loads single-font files reliably. */
    private static List<String> systemUiFontCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String windir = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
            return List.of(
                    windir + "\\Fonts\\segoeui.ttf",
                    windir + "\\Fonts\\arial.ttf",
                    windir + "\\Fonts\\tahoma.ttf",
                    windir + "\\Fonts\\calibri.ttf"
            );
        }
        if (os.contains("mac")) {
            return List.of(
                    "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                    "/System/Library/Fonts/Supplemental/Arial.ttf",
                    "/Library/Fonts/Arial Unicode.ttf",
                    "/Library/Fonts/Arial.ttf",
                    "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
                    "/System/Library/Fonts/Supplemental/Verdana.ttf",
                    "/System/Library/Fonts/Supplemental/Georgia.ttf",
                    "/System/Library/Fonts/Supplemental/Tahoma.ttf"
            );
        }
        return List.of(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/usr/share/fonts/noto/NotoSans-Regular.ttf",
                "/usr/share/fonts/truetype/freefont/FreeSans.ttf"
        );
    }

    private static List<String> systemMonoFontCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String windir = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
            return List.of(
                    windir + "\\Fonts\\consola.ttf",
                    windir + "\\Fonts\\CascadiaMono.ttf",
                    windir + "\\Fonts\\cour.ttf"
            );
        }
        if (os.contains("mac")) {
            return List.of(
                    "/System/Library/Fonts/Supplemental/Courier New.ttf",
                    "/Library/Fonts/Courier New.ttf",
                    "/System/Library/Fonts/Supplemental/Andale Mono.ttf"
            );
        }
        return List.of(
                "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
                "/usr/share/fonts/TTF/DejaVuSansMono.ttf"
        );
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
