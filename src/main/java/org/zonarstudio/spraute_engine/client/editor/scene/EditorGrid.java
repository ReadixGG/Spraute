package org.zonarstudio.spraute_engine.client.editor.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneTransform;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Editor world grid: visibility, cell size, move/rotate/size snap.
 * Defaults: 1-block cells, snap on, 15° rotation steps.
 */
public final class EditorGrid {
    private static boolean visible = true;
    private static boolean snapMove = true;
    private static boolean snapRotate = true;
    private static boolean snapSize = true;
    private static float cellSize = 1f;
    private static float rotateStepDeg = 15f;
    private static int extentCells = 24;
    private static float planeY = 0f;
    private static boolean planeFollowSelection = true;
    private static boolean settingsOpen;

    // GUI (canvas) grid: pixel grid drawn over canvases in the viewport editor.
    private static boolean uiVisible = true;
    private static boolean uiSnap = true;
    private static int uiCellPx = 5;

    private EditorGrid() {}

    public static boolean isVisible() { return visible; }
    public static void setVisible(boolean v) { visible = v; }

    public static boolean isSnapMove() { return snapMove; }
    public static void setSnapMove(boolean v) { snapMove = v; }

    public static boolean isSnapRotate() { return snapRotate; }
    public static void setSnapRotate(boolean v) { snapRotate = v; }
    public static boolean isSnapSize() { return snapSize; }
    public static void setSnapSize(boolean v) { snapSize = v; }

    public static float cellSize() { return Math.max(0.0625f, cellSize); }
    public static void setCellSize(float v) { cellSize = Math.max(0.0625f, v); }

    public static float rotateStepDeg() { return Math.max(0.5f, rotateStepDeg); }
    public static void setRotateStepDeg(float v) { rotateStepDeg = Math.max(0.5f, Math.min(180f, v)); }

    public static int extentCells() { return Math.max(2, Math.min(128, extentCells)); }
    public static void setExtentCells(int v) { extentCells = Math.max(2, Math.min(128, v)); }

    public static float planeY() { return planeY; }
    public static void setPlaneY(float v) { planeY = v; }

    public static boolean isPlaneFollowSelection() { return planeFollowSelection; }
    public static void setPlaneFollowSelection(boolean v) { planeFollowSelection = v; }

    public static boolean isUiGridVisible() { return uiVisible; }
    public static void setUiGridVisible(boolean v) { uiVisible = v; }

    public static boolean isUiSnap() { return uiSnap; }
    public static void setUiSnap(boolean v) { uiSnap = v; }

    public static int uiCellPx() { return Math.max(1, uiCellPx); }
    public static void setUiCellPx(int v) { uiCellPx = Math.max(1, Math.min(64, v)); }

    /** Snaps a GUI pixel value to the UI grid (no-op when UI snapping is off). */
    public static float snapUiPx(float px) {
        if (!uiSnap) return px;
        int cell = uiCellPx();
        return Math.round(px / cell) * cell;
    }

    public static double snapCoord(double value) {
        if (!snapMove) return value;
        double cell = cellSize();
        return Math.round(value / cell) * cell;
    }

    public static double snapAngle(double degrees) {
        if (!snapRotate) return degrees;
        double step = rotateStepDeg();
        return Math.round(degrees / step) * step;
    }

    public static double snapSize(double value) {
        if (!snapSize) return value;
        double cell = cellSize();
        return Math.max(cell, Math.round(value / cell) * cell);
    }

    /** Snap Bedrock model pixels using the same world grid (16 px = 1 block). */
    public static float snapModelPixels(float pixels, boolean size) {
        boolean enabled = size ? snapSize : snapMove;
        if (!enabled) return pixels;
        float step = cellSize() * 16f;
        float snapped = Math.round(pixels / step) * step;
        return size ? Math.max(step, snapped) : snapped;
    }

    public static void snapTransformMove(SceneTransform world) {
        if (!snapMove || world == null) return;
        world.px = snapCoord(world.px);
        world.py = snapCoord(world.py);
        world.pz = snapCoord(world.pz);
    }

    public static void snapTransformRotate(SceneTransform world) {
        if (!snapRotate || world == null) return;
        world.rx = snapAngle(world.rx);
        world.ry = snapAngle(world.ry);
        world.rz = snapAngle(world.rz);
    }

    /** Compact toolbar buttons (same row as gizmo tools) + settings popup. */
    public static void renderToolbarControls() {
        String tooltip = EditorI18n.t("Сетка  [") + (visible ? EditorI18n.t("видна") : EditorI18n.t("скрыта"))
                + " · " + trimNumber(cellSize()) + EditorI18n.t(" бл.")
                + " · " + trimNumber(rotateStepDeg()) + "°]";
        if (EditorIcons.iconToggleButton("grid-3x3", "grid-visible", 18f, tooltip, visible)) {
            visible = !visible;
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("settings", "grid-settings", 18f, EditorI18n.t("Настройки сетки"),
                0.72f, 0.76f, 0.82f, 1f)) {
            settingsOpen = true;
            ImGui.openPopup("##editor-grid-settings");
        }
        renderSettingsPopup();
    }

    private static void renderSettingsPopup() {
        if (settingsOpen) {
            ImGui.setNextWindowSize(360f, 0f, ImGuiCond.Appearing);
        }
        if (ImGui.beginPopup("##editor-grid-settings")) {
            ImGui.textDisabled(EditorI18n.t("Настройки сетки редактора"));
            ImGui.separator();
            if (ImGui.checkbox(EditorI18n.t("Показывать сетку"), visible)) visible = !visible;
            if (ImGui.checkbox(EditorI18n.t("Привязка перемещения"), snapMove)) snapMove = !snapMove;
            if (ImGui.checkbox(EditorI18n.t("Привязка поворота"), snapRotate)) snapRotate = !snapRotate;
            if (ImGui.checkbox(EditorI18n.t("Привязка размера"), snapSize)) snapSize = !snapSize;
            ImGui.separator();

            float[] cell = {cellSize};
            if (ImGui.sliderFloat(EditorI18n.t("Размер ячейки"), cell, 0.125f, 8f, "%.3f")) {
                setCellSize(cell[0]);
            }
            ImGui.sameLine();
            if (ImGui.smallButton("1")) setCellSize(1f);
            ImGui.sameLine();
            if (ImGui.smallButton("0.5")) setCellSize(0.5f);
            ImGui.sameLine();
            if (ImGui.smallButton("0.25")) setCellSize(0.25f);

            float[] rot = {rotateStepDeg};
            if (ImGui.sliderFloat(EditorI18n.t("Шаг угла °"), rot, 1f, 90f, "%.1f")) {
                setRotateStepDeg(rot[0]);
            }
            ImGui.sameLine();
            if (ImGui.smallButton("15°")) setRotateStepDeg(15f);
            ImGui.sameLine();
            if (ImGui.smallButton("45°")) setRotateStepDeg(45f);
            ImGui.sameLine();
            if (ImGui.smallButton("90°")) setRotateStepDeg(90f);

            int[] ext = {extentCells};
            if (ImGui.sliderInt(EditorI18n.t("Радиус (ячеек)"), ext, 4, 64)) {
                setExtentCells(ext[0]);
            }

            if (ImGui.checkbox(EditorI18n.t("Плоскость у выделения"), planeFollowSelection)) {
                planeFollowSelection = !planeFollowSelection;
            }
            float[] py = {planeY};
            if (ImGui.sliderFloat(EditorI18n.t("Y плоскости"), py, -64f, 320f, "%.1f")) {
                planeY = py[0];
            }

            ImGui.separator();
            ImGui.textDisabled(EditorI18n.t("Гизмо"));
            boolean showGizmos = EditorGizmo.gizmosEnabled();
            if (ImGui.checkbox(EditorI18n.t("Показывать гизмо"), showGizmos)) {
                org.zonarstudio.spraute_engine.config.SprauteConfig.get().editorShowGizmos = !showGizmos;
                org.zonarstudio.spraute_engine.config.SprauteConfig.save();
            }
            boolean showPlayerCam = EditorGizmo.playerCameraGizmosEnabled();
            if (ImGui.checkbox(EditorI18n.t("Показывать гизмо камеры игроков"), showPlayerCam)) {
                org.zonarstudio.spraute_engine.config.SprauteConfig.get().editorShowPlayerCameraGizmos = !showPlayerCam;
                org.zonarstudio.spraute_engine.config.SprauteConfig.save();
            }
            boolean showOverlays = EditorGizmo.overlaysEnabled();
            if (ImGui.checkbox(EditorI18n.t("Показывать наложения"), showOverlays)) {
                org.zonarstudio.spraute_engine.config.SprauteConfig.get().editorShowOverlays = !showOverlays;
                org.zonarstudio.spraute_engine.config.SprauteConfig.save();
            }

            ImGui.separator();
            ImGui.textDisabled(EditorI18n.t("Сетка GUI (канвасы)"));
            if (ImGui.checkbox(EditorI18n.t("Показывать сетку GUI"), uiVisible)) uiVisible = !uiVisible;
            if (ImGui.checkbox(EditorI18n.t("Привязка GUI к сетке"), uiSnap)) uiSnap = !uiSnap;
            int[] uiCell = {uiCellPx};
            if (ImGui.sliderInt(EditorI18n.t("Шаг GUI (px)"), uiCell, 1, 32)) {
                setUiCellPx(uiCell[0]);
            }
            ImGui.sameLine();
            if (ImGui.smallButton("5")) setUiCellPx(5);
            ImGui.sameLine();
            if (ImGui.smallButton("10")) setUiCellPx(10);

            ImGui.separator();
            if (ImGui.button(EditorI18n.t("Закрыть"), 120f, 0f)) {
                settingsOpen = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else {
            settingsOpen = false;
        }
    }

    public static boolean isSettingsOpen() {
        return settingsOpen;
    }

    private static String trimNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 1.0E-4f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        if (!visible || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double y = resolvePlaneY(cam);
        double cell = cellSize();
        int extent = extentCells();
        double originX = Math.round(cam.x / cell) * cell;
        double originZ = Math.round(cam.z / cell) * cell;
        double half = extent * cell;

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        // Major lines every 1 block (or every cell if cell >= 1), minor = cell.
        float majorEvery = Math.max(1f, cellSize());

        for (int i = -extent; i <= extent; i++) {
            double x = originX + i * cell;
            double z0 = originZ - half;
            double z1 = originZ + half;
            boolean major = isMajorLine(x, majorEvery) || i == 0;
            float a = major ? 0.55f : 0.22f;
            float r = i == 0 ? 0.95f : (major ? 0.55f : 0.40f);
            float g = i == 0 ? 0.35f : (major ? 0.58f : 0.42f);
            float b = i == 0 ? 0.35f : (major ? 0.62f : 0.48f);
            line(pose, lines, x, y, z0, x, y, z1, r, g, b, a);
        }
        for (int i = -extent; i <= extent; i++) {
            double z = originZ + i * cell;
            double x0 = originX - half;
            double x1 = originX + half;
            boolean major = isMajorLine(z, majorEvery) || i == 0;
            float a = major ? 0.55f : 0.22f;
            float r = i == 0 ? 0.35f : (major ? 0.55f : 0.40f);
            float g = i == 0 ? 0.95f : (major ? 0.58f : 0.42f);
            float b = i == 0 ? 0.35f : (major ? 0.62f : 0.48f);
            line(pose, lines, x0, y, z, x1, y, z, r, g, b, a);
        }

        pose.popPose();
    }

    private static double resolvePlaneY(Vec3 cam) {
        if (planeFollowSelection) {
            SceneNode selected = SceneEditorSession.selectedNode();
            if (selected != null && selected.type.hasTransform() && !selected.type.isUi()) {
                var doc = SceneEditorSession.document();
                if (doc != null) {
                    SceneTransform world = doc.worldTransform(selected.id);
                    if (world != null) return world.py;
                }
            }
            return Math.floor(cam.y);
        }
        return planeY;
    }

    private static boolean isMajorLine(double coord, double majorEvery) {
        double r = Math.round(coord / majorEvery) * majorEvery;
        return Math.abs(coord - r) < 1.0E-4;
    }

    private static void line(PoseStack pose, VertexConsumer out,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        var last = pose.last();
        float nx = 0f, ny = 1f, nz = 0f;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-6) {
            nx = (float) (dx / len);
            ny = (float) (dy / len);
            nz = (float) (dz / len);
        }
        out.vertex(last.pose(), (float) x0, (float) y0, (float) z0)
                .color(r, g, b, a).normal(last.normal(), nx, ny, nz).endVertex();
        out.vertex(last.pose(), (float) x1, (float) y1, (float) z1)
                .color(r, g, b, a).normal(last.normal(), nx, ny, nz).endVertex();
    }
}
