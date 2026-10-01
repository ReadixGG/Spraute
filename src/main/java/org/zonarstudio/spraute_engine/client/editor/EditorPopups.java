package org.zonarstudio.spraute_engine.client.editor;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * Shared look for small modal dialogs: centered, fixed size (auto-fit, not
 * resizable by the user), generous padding and rounded corners.
 */
public final class EditorPopups {
    private static final int MODAL_FLAGS = ImGuiWindowFlags.AlwaysAutoResize
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoSavedSettings;

    private EditorPopups() {}

    /**
     * Begins a styled modal. When it returns true the popup content must end
     * with {@link #endModal()}; when false nothing must be drawn or popped.
     */
    public static boolean beginModal(String title, float minWidth) {
        ImGuiViewport vp = ImGui.getMainViewport();
        ImGui.setNextWindowPos(vp.getCenterX(), vp.getCenterY(), ImGuiCond.Always, 0.5f, 0.5f);
        if (minWidth > 0f) {
            ImGui.setNextWindowSizeConstraints(minWidth, 0f, 4096f, 4096f);
        }
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 18f, 16f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 10f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8f, 8f);
        boolean open = ImGui.beginPopupModal(title, MODAL_FLAGS);
        if (!open) ImGui.popStyleVar(3);
        return open;
    }

    public static boolean beginModal(String title) {
        return beginModal(title, 340f);
    }

    public static void endModal() {
        ImGui.endPopup();
        ImGui.popStyleVar(3);
    }
}
