package org.zonarstudio.spraute_engine.client.editor;

import imgui.ImGui;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiDir;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the default dock layout once; afterwards ImGui persists it in the .ini file. */
final class SprauteEditorDocking {
    private static final String LAYOUT_VERSION = "4";
    private static boolean checked;

    private SprauteEditorDocking() {}

    static void resetLayout() {
        checked = false;
        Path ini = layoutIni();
        try {
            Files.deleteIfExists(ini);
            Files.deleteIfExists(ini.resolveSibling("spraute_editor_layout.version"));
        } catch (IOException ignored) {
        }
        if (SprauteImGuiBackend.isInitialized()) {
            ImGui.loadIniSettingsFromMemory("");
        }
    }

    static void ensureLayout(int dockspaceId) {
        if (checked) return;
        checked = true;

        Path ini = layoutIni();
        Path version = ini.resolveSibling("spraute_editor_layout.version");
        String stamped = readVersion(version);
        boolean hasIni = SprauteImGuiBackend.hadSavedLayout() || iniLooksValid(ini);

        if (hasIni) {
            if (LAYOUT_VERSION.equals(stamped)) return;
            if (stamped.isEmpty()) {
                writeVersion(version);
                return;
            }
        }

        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
        int root = imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.None);
        imgui.internal.ImGui.dockBuilderSetNodeSize(root,
                ImGui.getMainViewport().getSizeX(), ImGui.getMainViewport().getSizeY());

        imgui.type.ImInt leftColumn = new imgui.type.ImInt();
        imgui.type.ImInt centerAndRight = new imgui.type.ImInt();
        imgui.type.ImInt inspector = new imgui.type.ImInt();
        imgui.type.ImInt center = new imgui.type.ImInt();
        imgui.type.ImInt project = new imgui.type.ImInt();
        imgui.type.ImInt hierarchy = new imgui.type.ImInt();
        imgui.type.ImInt timeline = new imgui.type.ImInt();
        imgui.type.ImInt workspace = new imgui.type.ImInt();
        imgui.type.ImInt console = new imgui.type.ImInt();

        imgui.internal.ImGui.dockBuilderSplitNode(
                root, ImGuiDir.Left, 0.18f, leftColumn, centerAndRight);
        imgui.internal.ImGui.dockBuilderSplitNode(
                centerAndRight.get(), ImGuiDir.Right, 0.22f, inspector, center);
        imgui.internal.ImGui.dockBuilderSplitNode(
                leftColumn.get(), ImGuiDir.Down, 0.45f, project, hierarchy);
        imgui.internal.ImGui.dockBuilderSplitNode(
                center.get(), ImGuiDir.Down, 0.30f, timeline, workspace);
        imgui.internal.ImGui.dockBuilderSplitNode(
                timeline.get(), ImGuiDir.Right, 0.48f, console, timeline);

        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Иерархия###Hierarchy"), hierarchy.get());
        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Проект###Project"), project.get());
        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Таймлайн###Timeline"), timeline.get());
        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Консоль###Console"), console.get());
        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Сцена###Workspace"), workspace.get());
        imgui.internal.ImGui.dockBuilderDockWindow(EditorI18n.t("Инспектор###Inspector"), inspector.get());
        imgui.internal.ImGui.dockBuilderFinish(root);
        writeVersion(version);
        SprauteImGuiBackend.saveIniNow();
    }

    private static Path layoutIni() {
        Path fromBackend = SprauteImGuiBackend.iniPath();
        if (fromBackend != null) return fromBackend;
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("spraute_editor_imgui.ini");
    }

    private static boolean iniLooksValid(Path ini) {
        try {
            return Files.isRegularFile(ini) && Files.size(ini) > 80;
        } catch (IOException e) {
            return false;
        }
    }

    private static String readVersion(Path version) {
        try {
            if (!Files.isRegularFile(version)) return "";
            return Files.readString(version).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeVersion(Path version) {
        try {
            Files.createDirectories(version.getParent());
            Files.writeString(version, LAYOUT_VERSION);
        } catch (IOException ignored) {
        }
    }
}
