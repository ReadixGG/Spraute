package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.editor.visual.PluginI18n;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Plugin manager window (Studio-style): list of installed visual block plugins
 * with enable/disable toggles, zip install, skeleton creation and folder access.
 */
public final class PluginManagerPanel {
    private PluginManagerPanel() {}

    private static final ImString search = new ImString(128);
    private static final ImString createName = new ImString(64);
    private static final ImString createAuthor = new ImString(64);
    private static final ImString createDesc = new ImString(512);
    private static final ImString editName = new ImString(64);
    private static final ImString editAuthor = new ImString(64);
    private static final ImString editVersion = new ImString(32);
    private static final ImString editDesc = new ImString(512);
    private static String editFolder;
    private static String status = "";
    private static String popupError = "";
    private static boolean openCreate;
    private static boolean openEdit;
    private static boolean openRequested;

    /** Any editor panel can ask the screen to show the plugin manager window. */
    public static void requestOpen() { openRequested = true; }

    public static boolean consumeOpenRequest() {
        boolean r = openRequested;
        openRequested = false;
        return r;
    }

    public static void render(ImBoolean show) {
        if (!show.get()) return;
        ImGui.setNextWindowSize(620f, 560f, ImGuiCond.FirstUseEver);
        if (!ImGui.begin(EditorI18n.t("Плагины###PluginManager"), show, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        renderToolbar();
        ImGui.spacing();

        String filter = search.get().trim().toLowerCase(Locale.ROOT);
        List<VisualBlockRegistry.PluginInfo> plugins = VisualBlockRegistry.plugins();
        int shown = 0;
        ImGui.beginChild("##plugin-list", 0f, -28f, false);
        for (VisualBlockRegistry.PluginInfo p : plugins) {
            if (!filter.isEmpty()
                    && !p.name.toLowerCase(Locale.ROOT).contains(filter)
                    && !PluginI18n.pluginName(p).toLowerCase(Locale.ROOT).contains(filter)
                    && !PluginI18n.pluginDescription(p).toLowerCase(Locale.ROOT).contains(filter)
                    && !p.folder.toLowerCase(Locale.ROOT).contains(filter)) continue;
            renderPluginCard(p);
            shown++;
        }
        if (shown == 0) {
            ImGui.dummy(1f, 12f);
            ImGui.textDisabled(plugins.isEmpty()
                    ? EditorI18n.t("Плагины не найдены — создайте новый или установите .zip")
                    : EditorI18n.t("Ничего не найдено по запросу"));
        }
        ImGui.endChild();

        // status line
        ImGui.separator();
        if (!status.isBlank()) {
            ImGui.textColored(0.55f, 0.85f, 0.6f, 1f, status);
        } else {
            ImGui.textDisabled(plugins.size() + EditorI18n.t(" плагинов  ·  папка: spraute_engine/plugins"));
        }

        renderCreatePopup();
        renderEditPopup();
        ImGui.end();
    }

    // ==================== Toolbar ====================

    private static void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 8f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f, 6f);
        if (button(EditorI18n.t("Создать"), 0.24f, 0.52f, 0.38f)) {
            createName.set("");
            createAuthor.set("");
            createDesc.set("");
            popupError = "";
            openCreate = true;
        }
        ImGui.sameLine();
        if (button(EditorI18n.t("Установить"), 0.22f, 0.38f, 0.62f)) {
            installViaFileDialog();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Выбрать файл .splugin или .zip"));
        ImGui.sameLine();
        if (button(EditorI18n.t("Папка"), 0.32f, 0.34f, 0.40f)) {
            openFolder(VisualBlockRegistry.pluginsDir());
        }
        ImGui.sameLine();
        if (button(EditorI18n.t("Обновить"), 0.32f, 0.34f, 0.40f)) {
            VisualBlockRegistry.reload();
            status = EditorI18n.t("Плагины перезагружены");
        }
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.055f, 0.065f, 0.085f, 1f);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##plugin-search", EditorI18n.t("Поиск плагинов..."), search);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private static boolean button(String label, float r, float g, float b) {
        ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r * 1.25f, g * 1.25f, b * 1.25f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, r * 0.85f, g * 0.85f, b * 0.85f, 1f);
        boolean clicked = ImGui.button(label);
        ImGui.popStyleColor(3);
        return clicked;
    }

    // ==================== Plugin card ====================

    private static void renderPluginCard(VisualBlockRegistry.PluginInfo p) {
        ImGui.pushID("plugin-" + p.folder);
        float w = ImGui.getContentRegionAvailX();
        float h = 124f;
        float x0 = ImGui.getCursorScreenPosX();
        float y0 = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();

        // card background + accent bar
        int accent = p.enabled
                ? (p.bundled ? col(56, 189, 248) : col(74, 222, 128))
                : col(120, 126, 138);
        dl.addRectFilled(x0, y0, x0 + w, y0 + h, ImGui.getColorU32(0.117f, 0.129f, 0.152f, 1f), 9f);
        dl.addRect(x0, y0, x0 + w, y0 + h, withAlpha(accent, 70), 9f, 0, 1f);
        dl.addRectFilled(x0 + 6f, y0 + 8f, x0 + 10f, y0 + h - 8f, accent, 2f);

        // title row
        float tx = x0 + 20f;
        dl.addText(tx, y0 + 9f, ImGui.getColorU32(0.96f, 0.97f, 0.99f, 1f), PluginI18n.pluginName(p));
        float nameW = ImGui.calcTextSize(PluginI18n.pluginName(p)).x;
        String badge = p.bundled ? EditorI18n.t("встроенный") : (p.version.isBlank() ? "" : "v" + p.version);
        if (!badge.isBlank()) {
            dl.addText(tx + nameW + 10f, y0 + 9f, withAlpha(accent, 210), badge);
        }
        if (!p.enabled) {
            dl.addText(tx + nameW + 10f + (badge.isBlank() ? 0f : ImGui.calcTextSize(badge).x + 10f),
                    y0 + 9f, col(248, 113, 113), EditorI18n.t("выключен"));
        }

        // info rows
        String info = p.blockCount + EditorI18n.t(" блоков · ") + p.categoryCount + EditorI18n.t(" категорий")
                + (p.scriptCount > 0 ? " · " + p.scriptCount + EditorI18n.t(" скрипт.") : "")
                + (p.author.isBlank() ? "" : EditorI18n.t(" · автор: ") + p.author);
        dl.addText(tx, y0 + 29f, ImGui.getColorU32(0.62f, 0.66f, 0.72f, 1f), info);
        String desc = PluginI18n.pluginDescription(p);
        if (desc.isBlank()) desc = "—";
        if (desc.length() > 90) desc = desc.substring(0, 90) + "…";
        dl.addText(tx, y0 + 48f, ImGui.getColorU32(0.52f, 0.55f, 0.62f, 1f), desc);

        // order arrows (parse priority, like Studio)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 7f);
        float bw = 108f;
        float ax = x0 + w - bw - 12f - 34f;
        ImGui.pushStyleColor(ImGuiCol.Button, 1f, 1f, 1f, 0.06f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.14f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.22f);
        ImGui.setCursorScreenPos(ax, y0 + 30f);
        if (ImGui.button("▲", 24f, 24f)) VisualBlockRegistry.movePlugin(p.folder, -1);
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Выше (приоритет загрузки)"));
        ImGui.setCursorScreenPos(ax, y0 + 58f);
        if (ImGui.button("▼", 24f, 24f)) VisualBlockRegistry.movePlugin(p.folder, +1);
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Ниже"));
        ImGui.popStyleColor(3);

        // right-side controls
        ImGui.setCursorScreenPos(x0 + w - bw - 12f, y0 + 8f);
        if (p.enabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.30f, 0.16f, 0.16f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.42f, 0.20f, 0.20f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.24f, 0.24f, 1f);
            if (ImGui.button(EditorI18n.t("Выключить"), bw, 22f)) {
                VisualBlockRegistry.setEnabled(p.folder, false);
                status = EditorI18n.t("Плагин «") + p.name + EditorI18n.t("» выключен");
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.16f, 0.32f, 0.22f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.20f, 0.44f, 0.28f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.24f, 0.52f, 0.34f, 1f);
            if (ImGui.button(EditorI18n.t("Включить"), bw, 22f)) {
                VisualBlockRegistry.setEnabled(p.folder, true);
                status = EditorI18n.t("Плагин «") + p.name + EditorI18n.t("» включен");
            }
        }
        ImGui.popStyleColor(3);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.26f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.26f, 0.29f, 0.34f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.30f, 0.33f, 0.39f, 1f);
        ImGui.setCursorScreenPos(x0 + w - bw - 12f, y0 + 34f);
        if (ImGui.button(EditorI18n.t("Настройки"), bw, 22f)) {
            editFolder = p.folder;
            editName.set(p.name);
            editAuthor.set(p.author);
            editVersion.set(p.version);
            editDesc.set(p.description);
            popupError = "";
            openEdit = true;
        }
        ImGui.setCursorScreenPos(x0 + w - bw - 12f, y0 + 60f);
        if (ImGui.button(EditorI18n.t("Скачать"), bw, 22f)) {
            exportViaFileDialog(p);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Скачать плагин как .splugin на устройство"));
        ImGui.setCursorScreenPos(x0 + w - bw - 12f, y0 + 86f);
        if (ImGui.button(EditorI18n.t("Папка"), bw, 22f)) {
            openFolder(VisualBlockRegistry.pluginsDir().resolve(p.folder));
        }
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();

        // consume vertical space of the card
        ImGui.setCursorScreenPos(x0, y0);
        ImGui.dummy(w, h + 6f);
        ImGui.popID();
    }

    // ==================== Popups ====================

    private static void renderCreatePopup() {
        if (openCreate) {
            openCreate = false;
            ImGui.openPopup(EditorI18n.t("Создать плагин###plugin-create"));
        }
        ImGui.setNextWindowSize(430f, 0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(EditorI18n.t("Создать плагин###plugin-create"), ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.textDisabled(EditorI18n.t("Появится в spraute_engine/plugins/<имя>"));
        ImGui.spacing();
        ImGui.text(EditorI18n.t("Имя (латиница/цифры/-/_)"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pc-name", createName);
        ImGui.text(EditorI18n.t("Автор"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pc-author", createAuthor);
        ImGui.text(EditorI18n.t("Описание"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pc-desc", createDesc);
        if (!popupError.isBlank()) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f, popupError);
        }
        ImGui.spacing();
        if (ImGui.button(EditorI18n.t("Создать"), 120f, 0f)) {
            String err = VisualBlockRegistry.createPlugin(
                    createName.get().trim(), createAuthor.get().trim(), createDesc.get().trim());
            if (err == null) {
                status = EditorI18n.t("Плагин «") + createName.get().trim() + EditorI18n.t("» создан");
                ImGui.closeCurrentPopup();
            } else {
                popupError = err;
            }
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Отмена"), 120f, 0f)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    /** Native OS file chooser (.splugin/.zip), then install into plugins/. */
    private static void installViaFileDialog() {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer patterns = stack.mallocPointer(2);
            patterns.put(stack.UTF8("*.splugin"));
            patterns.put(stack.UTF8("*.zip"));
            patterns.flip();
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    EditorI18n.t("Установить плагин"), "", patterns, EditorI18n.t("Плагины Spraute (.splugin, .zip)"), false);
            if (picked == null) return;
            String err = VisualBlockRegistry.installZip(Path.of(picked));
            status = err == null
                    ? EditorI18n.t("Плагин установлен: ") + Path.of(picked).getFileName()
                    : err;
        } catch (Throwable t) {
            status = EditorI18n.t("Не удалось открыть диалог: ") + t.getMessage();
        }
    }

    /** Save-dialog → pack plugin folder into .splugin on the user's device. */
    private static void exportViaFileDialog(VisualBlockRegistry.PluginInfo p) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer patterns = stack.mallocPointer(2);
            patterns.put(stack.UTF8("*.splugin"));
            patterns.put(stack.UTF8("*.zip"));
            patterns.flip();
            String suggested = p.folder + ".splugin";
            String picked = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                    EditorI18n.t("Сохранить плагин"), suggested, patterns, EditorI18n.t("Плагины Spraute (.splugin, .zip)"));
            if (picked == null || picked.isBlank()) return;
            Path dest = Path.of(picked);
            String name = dest.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".splugin") && !lower.endsWith(".zip")) {
                dest = dest.resolveSibling(name + ".splugin");
            }
            String err = VisualBlockRegistry.exportZip(p.folder, dest);
            status = err == null ? EditorI18n.t("Сохранено: ") + dest.toAbsolutePath() : err;
        } catch (Throwable t) {
            status = EditorI18n.t("Не удалось сохранить: ") + t.getMessage();
        }
    }

    /** Studio-style plugin settings editor (name, author, version, description). */
    private static void renderEditPopup() {
        if (openEdit) {
            openEdit = false;
            ImGui.openPopup(EditorI18n.t("Настройки плагина###plugin-edit"));
        }
        ImGui.setNextWindowSize(440f, 0f, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(EditorI18n.t("Настройки плагина###plugin-edit"), ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.textDisabled("plugins/" + (editFolder == null ? "?" : editFolder) + "/plugin.json");
        ImGui.textDisabled(EditorI18n.t("Скрипты: plugins/.../scripts → scripts/plugins/<имя>/"));
        ImGui.spacing();
        ImGui.text(EditorI18n.t("Название"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pe-name", editName);
        ImGui.text(EditorI18n.t("Автор"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pe-author", editAuthor);
        ImGui.text(EditorI18n.t("Версия"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pe-version", editVersion);
        ImGui.text(EditorI18n.t("Описание"));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputText("##pe-desc", editDesc);
        if (!popupError.isBlank()) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f, popupError);
        }
        ImGui.spacing();
        if (ImGui.button(EditorI18n.t("Сохранить"), 130f, 0f)) {
            String err = VisualBlockRegistry.updateMeta(editFolder,
                    editName.get(), editAuthor.get(), editVersion.get(), editDesc.get());
            if (err == null) {
                status = EditorI18n.t("Настройки плагина сохранены");
                ImGui.closeCurrentPopup();
            } else {
                popupError = err;
            }
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Скрипты"), 110f, 0f)) {
            openFolder(VisualBlockRegistry.pluginsDir().resolve(editFolder).resolve("scripts"));
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Блоки"), 100f, 0f)) {
            openFolder(VisualBlockRegistry.pluginsDir().resolve(editFolder).resolve("blocks"));
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Скачать"), 100f, 0f)) {
            if (editFolder != null) {
                VisualBlockRegistry.PluginInfo info = null;
                for (VisualBlockRegistry.PluginInfo p : VisualBlockRegistry.plugins()) {
                    if (editFolder.equals(p.folder)) { info = p; break; }
                }
                if (info != null) {
                    ImGui.closeCurrentPopup();
                    exportViaFileDialog(info);
                }
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Скачать как .splugin"));
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Отмена"), 90f, 0f)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    // ==================== Utils ====================

    private static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String target = dir.toAbsolutePath().normalize().toString();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", target).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", target).start();
            } else {
                new ProcessBuilder("xdg-open", target).start();
            }
        } catch (Exception e) {
            status = EditorI18n.t("Не удалось открыть папку: ") + e.getMessage();
        }
    }

    private static int col(int r, int g, int b) {
        return ImGui.getColorU32(r / 255f, g / 255f, b / 255f, 1f);
    }

    private static int withAlpha(int abgr, int a) {
        return (abgr & 0x00FFFFFF) | (a << 24);
    }
}
