package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorKeybinds;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRouteOps;

/**
 * Fly the editor camera and drop ROUTE_POINT nodes at the view pose.
 * Keybinds are Minecraft {@link KeyMapping}s (Controls menu).
 */
public final class RouteFastPlace {
    private static String routeId;

    private RouteFastPlace() {}

    public static boolean isActive() {
        return route() != null;
    }

    public static boolean isActiveFor(SceneNode route) {
        return route != null && route.id != null && route.id.equals(routeId);
    }

    public static void start(SceneNode route) {
        if (route == null || route.type != SceneNodeType.ROUTE || route.id == null) return;
        routeId = route.id;
        SceneEditorSession.select(route.id);
        EditorViewportInput.beginCapture();
    }

    public static void stop() {
        routeId = null;
        EditorViewportInput.endCapture();
    }

    public static SceneNode route() {
        if (routeId == null) return null;
        SceneDocument document = SceneEditorSession.document();
        if (document == null) return null;
        SceneNode node = document.findNode(routeId);
        if (node == null || node.type != SceneNodeType.ROUTE) {
            routeId = null;
            return null;
        }
        return node;
    }

    public static boolean handleKey(int keyCode, int scanCode) {
        if (!isActive()) return false;
        if (imgui.ImGui.getIO().getWantTextInput()) return false;
        if (matches(SprauteEditorKeybinds.ROUTE_ADD_POINT, keyCode, scanCode)) {
            placeFromCamera();
            return true;
        }
        if (matches(SprauteEditorKeybinds.ROUTE_REMOVE_POINT, keyCode, scanCode)) {
            removeLast();
            return true;
        }
        return false;
    }

    public static void placeFromCamera() {
        SceneNode route = route();
        SceneDocument document = SceneEditorSession.document();
        if (route == null || document == null) return;
        Entity camera = SprauteEditorState.cameraEntity();
        Vec3 pos = SprauteEditorState.viewPosition();
        float yaw = camera != null ? camera.getYRot() : 0f;
        float pitch = camera != null ? camera.getXRot() : 0f;
        SceneEditorSession.pushUndo();
        SceneRouteOps.addPoint(document, route, pos.x, pos.y, pos.z, yaw, pitch);
        SceneRouteOps.saveOwningRoute(document, route);
        SceneEditorSession.markDirty();
        SceneEditorSession.select(route.id);
    }

    public static void removeLast() {
        SceneNode route = route();
        SceneDocument document = SceneEditorSession.document();
        if (route == null || document == null) return;
        int last = SceneRouteOps.points(route).size() - 1;
        if (last < 0) return;
        SceneEditorSession.pushUndo();
        SceneRouteOps.removePoint(route, last);
        SceneRouteOps.saveOwningRoute(document, route);
        SceneEditorSession.markDirty();
        SceneEditorSession.select(route.id);
    }

    public static void renderHud() {
        if (!isActive()) return;
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(
                viewport.getWorkPosX() + viewport.getWorkSizeX() * 0.5f,
                viewport.getWorkPosY() + viewport.getWorkSizeY() - 8f,
                imgui.flag.ImGuiCond.Always, 0.5f, 1f);
        ImGui.setNextWindowBgAlpha(0.88f);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoInputs
                | ImGuiWindowFlags.NoDocking;
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.04f, 0.07f, 0.11f, 0.92f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.35f, 0.72f, 0.95f, 0.55f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 8f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 14f, 10f);
        ImGui.begin("##route-fast-place", flags);
        ImGui.textColored(0.55f, 0.88f, 1f, 1f, EditorI18n.t("Быстрый режим пути"));
        SceneNode route = route();
        int count = route == null ? 0 : SceneRouteOps.points(route).size();
        ImGui.sameLine();
        ImGui.textDisabled("  ·  " + (route == null || route.name == null || route.name.isBlank()
                ? EditorI18n.t("Путь") : route.name)
                + "  ·  " + EditorI18n.t("Точек: ") + count);
        ImGui.dummy(1f, 2f);
        hintRow(keyName(SprauteEditorKeybinds.ROUTE_ADD_POINT, "Y"),
                EditorI18n.t("поставить точку"));
        ImGui.sameLine(0f, 18f);
        hintRow(keyName(SprauteEditorKeybinds.ROUTE_REMOVE_POINT, "U"),
                EditorI18n.t("удалить последнюю"));
        ImGui.sameLine(0f, 18f);
        hintRow("Esc", EditorI18n.t("выйти"));
        ImGui.end();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(2);
    }

    /** Key overlay on the 3D viewport so the binds stay visible while flying. */
    public static void drawOnViewport(float x0, float y0, float x1, float y1) {
        if (!isActive() || x1 - x0 < 80f || y1 - y0 < 40f) return;
        imgui.ImDrawList dl = ImGui.getForegroundDrawList();
        String add = keyName(SprauteEditorKeybinds.ROUTE_ADD_POINT, "Y")
                + "  " + EditorI18n.t("поставить точку");
        String rem = keyName(SprauteEditorKeybinds.ROUTE_REMOVE_POINT, "U")
                + "  " + EditorI18n.t("удалить последнюю");
        String esc = "Esc  " + EditorI18n.t("выйти");
        String title = EditorI18n.t("Быстрый режим пути");
        String line = add + "     " + rem + "     " + esc;
        float padX = 14f;
        float padY = 8f;
        float titleH = ImGui.getFontSize();
        float lineH = ImGui.getFontSize();
        float tw = Math.max(ImGui.calcTextSize(title).x, ImGui.calcTextSize(line).x);
        float boxW = tw + padX * 2f;
        float boxH = titleH + lineH + padY * 2f + 6f;
        float bx0 = (x0 + x1 - boxW) * 0.5f;
        float by1 = y1 - 12f;
        float by0 = by1 - boxH;
        int bg = ImGui.getColorU32(0.04f, 0.07f, 0.11f, 0.82f);
        int border = ImGui.getColorU32(0.35f, 0.72f, 0.95f, 0.7f);
        int titleCol = ImGui.getColorU32(0.55f, 0.90f, 1f, 1f);
        int textCol = ImGui.getColorU32(0.92f, 0.95f, 0.98f, 1f);
        dl.addRectFilled(bx0, by0, bx0 + boxW, by1, bg, 8f);
        dl.addRect(bx0, by0, bx0 + boxW, by1, border, 8f, 0, 1.5f);
        dl.addText(bx0 + padX, by0 + padY, titleCol, title);
        dl.addText(bx0 + padX, by0 + padY + titleH + 4f, textCol, line);
    }

    public static String addPointHint() {
        return keyName(SprauteEditorKeybinds.ROUTE_ADD_POINT, "Y")
                + " — " + EditorI18n.t("поставить точку");
    }

    public static String removePointHint() {
        return keyName(SprauteEditorKeybinds.ROUTE_REMOVE_POINT, "U")
                + " — " + EditorI18n.t("удалить последнюю");
    }

    public static String hudHintLine() {
        return addPointHint() + "   ·   " + removePointHint() + "   ·   Esc — "
                + EditorI18n.t("выйти");
    }

    private static void hintRow(String key, String action) {
        ImGui.textColored(1f, 0.92f, 0.45f, 1f, key);
        ImGui.sameLine(0f, 6f);
        ImGui.text(action);
    }

    private static boolean matches(KeyMapping mapping, int keyCode, int scanCode) {
        return mapping != null && mapping.matches(keyCode, scanCode);
    }

    private static String keyName(KeyMapping mapping, String fallback) {
        if (mapping == null) return fallback;
        String name = mapping.getTranslatedKeyMessage().getString();
        if (name == null || name.isBlank() || name.startsWith("key.")) return fallback;
        return name;
    }
}
