package org.zonarstudio.spraute_engine.client.imgui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

public final class SprauteImGuiTheme {
    private SprauteImGuiTheme() {}

    public static void apply() {
        ImGuiStyle s = ImGui.getStyle();
        s.setWindowRounding(5f);
        s.setChildRounding(4f);
        s.setFrameRounding(5f);
        s.setPopupRounding(6f);
        s.setScrollbarRounding(8f);
        s.setGrabRounding(5f);
        s.setTabRounding(5f);
        s.setWindowBorderSize(0f);
        s.setChildBorderSize(0f);
        s.setFrameBorderSize(0f);
        s.setPopupBorderSize(1f);
        s.setTabBorderSize(0f);
        s.setWindowPadding(12f, 10f);
        s.setFramePadding(9f, 6f);
        s.setItemSpacing(8f, 6f);
        s.setItemInnerSpacing(6f, 4f);
        s.setCellPadding(4f, 2f);
        s.setTouchExtraPadding(0f, 0f);
        s.setIndentSpacing(17f);
        s.setColumnsMinSpacing(6f);
        s.setScrollbarSize(6f);
        s.setGrabMinSize(8f);
        s.setLogSliderDeadzone(4f);
        s.setTabMinWidthForCloseButton(0f);
        s.setSeparatorTextPadding(20f, 3f);
        s.setDisplayWindowPadding(19f, 19f);
        s.setDisplaySafeAreaPadding(3f, 3f);
        s.setMouseCursorScale(1f);
        // Must reset: scaleAllSizes multiplies this, and the settings slider
        // reapplies scale every frame while dragging — otherwise the dock
        // splitters grow until they cover the whole UI.
        s.setDockingSeparatorSize(2f);
        // Small on purpose: docked Hierarchy/Inspector must stay shrinkable.
        // scaleAllSizes compounds this if it is not reset here.
        s.setWindowMinSize(48f, 24f);
        s.setWindowTitleAlign(0f, 0.5f);
        s.setSelectableTextAlign(0f, 0.5f);
        s.setHoverDelayShort(0.12f);
        s.setHoverDelayNormal(0.32f);

        // Neutral graphite surfaces: depth comes from tone, not bright borders.
        color(s, ImGuiCol.WindowBg, 0x17191D, 0.97f);
        color(s, ImGuiCol.ChildBg, 0x1C1F24, 0.96f);
        color(s, ImGuiCol.PopupBg, 0x25282E, 0.99f);
        color(s, ImGuiCol.Border, 0x59616D, 0.25f);
        color(s, ImGuiCol.BorderShadow, 0x000000, 0f);
        color(s, ImGuiCol.Text, 0xE3E6EB, 1f);
        color(s, ImGuiCol.TextDisabled, 0x858B95, 1f);
        color(s, ImGuiCol.FrameBg, 0x292C32, 1f);
        color(s, ImGuiCol.FrameBgHovered, 0x333740, 1f);
        color(s, ImGuiCol.FrameBgActive, 0x3B414C, 1f);
        color(s, ImGuiCol.TitleBg, 0x141619, 1f);
        color(s, ImGuiCol.TitleBgActive, 0x202329, 1f);
        color(s, ImGuiCol.TitleBgCollapsed, 0x141619, 0.92f);
        color(s, ImGuiCol.MenuBarBg, 0x121417, 0.98f);
        color(s, ImGuiCol.Button, 0x2B2F36, 1f);
        color(s, ImGuiCol.ButtonHovered, 0x3A404A, 1f);
        color(s, ImGuiCol.ButtonActive, 0x46505E, 1f);
        color(s, ImGuiCol.Header, 0x30343B, 0.95f);
        color(s, ImGuiCol.HeaderHovered, 0x3B424D, 1f);
        color(s, ImGuiCol.HeaderActive, 0x46515F, 1f);
        color(s, ImGuiCol.CheckMark, 0x68A9FF, 1f);
        color(s, ImGuiCol.SliderGrab, 0x7D8796, 1f);
        color(s, ImGuiCol.SliderGrabActive, 0x68A9FF, 1f);
        color(s, ImGuiCol.ResizeGrip, 0x68A9FF, 0.15f);
        color(s, ImGuiCol.ResizeGripHovered, 0x68A9FF, 0.55f);
        color(s, ImGuiCol.ResizeGripActive, 0x68A9FF, 0.85f);
        color(s, ImGuiCol.Tab, 0x1B1D21, 1f);
        color(s, ImGuiCol.TabHovered, 0x343941, 1f);
        color(s, ImGuiCol.TabActive, 0x292D34, 1f);
        color(s, ImGuiCol.TabUnfocused, 0x181A1E, 1f);
        color(s, ImGuiCol.TabUnfocusedActive, 0x24272D, 1f);
        color(s, ImGuiCol.DockingPreview, 0x68A9FF, 0.42f);
        color(s, ImGuiCol.DockingEmptyBg, 0x111316, 0.35f);
        color(s, ImGuiCol.Separator, 0x626A76, 0.22f);
        color(s, ImGuiCol.SeparatorHovered, 0x68A9FF, 0.45f);
        color(s, ImGuiCol.SeparatorActive, 0x68A9FF, 0.75f);
        color(s, ImGuiCol.ScrollbarBg, 0x111316, 0.25f);
        color(s, ImGuiCol.ScrollbarGrab, 0x555C67, 0.65f);
        color(s, ImGuiCol.ScrollbarGrabHovered, 0x747D89, 0.9f);
        color(s, ImGuiCol.ScrollbarGrabActive, 0x68A9FF, 0.95f);
        color(s, ImGuiCol.NavHighlight, 0x68A9FF, 0.8f);
        color(s, ImGuiCol.DragDropTarget, 0x68A9FF, 0.95f);
        color(s, ImGuiCol.TextSelectedBg, 0x3B72AE, 0.55f);
        color(s, ImGuiCol.TableHeaderBg, 0x25282E, 1f);
        color(s, ImGuiCol.TableBorderStrong, 0x5C6470, 0.28f);
        color(s, ImGuiCol.TableBorderLight, 0x5C6470, 0.14f);
        color(s, ImGuiCol.TableRowBg, 0x1C1F24, 0f);
        color(s, ImGuiCol.TableRowBgAlt, 0xFFFFFF, 0.025f);
        color(s, ImGuiCol.ModalWindowDimBg, 0x08090B, 0.72f);
    }

    private static void color(ImGuiStyle style, int slot, int rgb, float a) {
        style.setColor(slot,
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f,
                a);
    }
}
