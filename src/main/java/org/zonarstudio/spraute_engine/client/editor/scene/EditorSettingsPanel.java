package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient;
import org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend;
import org.zonarstudio.spraute_engine.config.SprauteConfig;

/**
 * Engine settings (NPC chat name format / color). Opened from the editor menu bar.
 */
public final class EditorSettingsPanel {
    private static final float WINDOW_W = 440f;
    private static final float WINDOW_H = 760f;
    private static final ImString nameFormat = new ImString(64);
    private static final float[] nameColor = new float[]{1f, 1f, 1f, 1f};
    private static final int[] maxFileMb = {8};
    private static final int[] maxTotalMb = {150};
    private static final int[] maxFiles = {2000};
    private static final float[] uiScale = {1f};
    private static final ImBoolean autoUpdateMod = new ImBoolean(true);
    private static final ImBoolean autoUpdateRequireRestart = new ImBoolean(false);
    private static boolean dirty;
    private static String status = "";
    private static boolean wasOpen;

    private EditorSettingsPanel() {}

    public static void render(ImBoolean show) {
        if (!show.get()) {
            wasOpen = false;
            return;
        }
        if (!wasOpen) {
            reloadFromConfig();
            wasOpen = true;
        }

        float scale = SprauteImGuiBackend.uiScale();
        ImGui.setNextWindowSize(WINDOW_W * scale, WINDOW_H * scale, ImGuiCond.Always);
        int winFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.AlwaysVerticalScrollbar;
        if (!ImGui.begin(EditorI18n.t("Настройки###EngineSettings"), show, winFlags)) {
            ImGui.end();
            return;
        }

        ImGui.textColored(0.86f, 0.88f, 0.92f, 1f, EditorI18n.t("Интерфейс"));
        ImGui.textDisabled(EditorI18n.t("Шрифт и весь интерфейс редактора. Сохраняется сразу."));
        ImGui.spacing();

        uiScale[0] = SprauteImGuiBackend.uiScale();

        ImGui.text(EditorI18n.t("Масштаб интерфейса"));
        float btn = ImGui.getFrameHeight();
        float spacing = ImGui.getStyle().getItemSpacingX();
        if (ImGui.button("-##ui-scale-dec", btn, btn)) {
            SprauteImGuiBackend.nudgeUiScale(-1);
            uiScale[0] = SprauteImGuiBackend.uiScale();
        }
        ImGui.sameLine(0f, spacing);
        ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() - btn - spacing));
        float[] pct = { uiScale[0] * 100f };
        if (ImGui.sliderFloat("##ui-scale", pct, SprauteConfig.MIN_UI_SCALE * 100f,
                SprauteConfig.MAX_UI_SCALE * 100f, "%.0f%%", imgui.flag.ImGuiSliderFlags.AlwaysClamp)) {
            SprauteImGuiBackend.setUiScale(pct[0] / 100f);
            uiScale[0] = SprauteImGuiBackend.uiScale();
        }
        ImGui.sameLine(0f, spacing);
        if (ImGui.button("+##ui-scale-inc", btn, btn)) {
            SprauteImGuiBackend.nudgeUiScale(1);
            uiScale[0] = SprauteImGuiBackend.uiScale();
        }
        if (ImGui.smallButton(EditorI18n.t("100%"))) {
            SprauteImGuiBackend.setUiScale(1f);
            uiScale[0] = 1f;
        }

        ImGui.spacing();
        ImGui.textDisabled(EditorI18n.t("Оверлеи вьюпорта. Сохраняется сразу."));
        ImGui.spacing();
        boolean showGizmos = SprauteConfig.get().isEditorShowGizmos();
        if (ImGui.checkbox(EditorI18n.t("Показывать гизмо"), showGizmos)) {
            SprauteConfig.get().editorShowGizmos = !showGizmos;
            SprauteConfig.save();
        }
        boolean showPlayerCam = SprauteConfig.get().isEditorShowPlayerCameraGizmos();
        if (ImGui.checkbox(EditorI18n.t("Показывать гизмо камеры игроков"), showPlayerCam)) {
            SprauteConfig.get().editorShowPlayerCameraGizmos = !showPlayerCam;
            SprauteConfig.save();
        }
        boolean showOverlays = SprauteConfig.get().isEditorShowOverlays();
        if (ImGui.checkbox(EditorI18n.t("Показывать наложения"), showOverlays)) {
            SprauteConfig.get().editorShowOverlays = !showOverlays;
            SprauteConfig.save();
        }
        ImGui.textDisabled(EditorI18n.t("Гизмо — стрелки перемещения/поворота/масштаба. Камера игроков — пирамида обзора у CAMERA под игроком. Наложения — пути, рамки выделения, хитбоксы."));

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.86f, 0.88f, 0.92f, 1f, EditorI18n.t("Рендер моделей"));
        SprauteConfig geo=SprauteConfig.get();
        String backend=geo.geoRenderBackend;
        if (ImGui.beginCombo(EditorI18n.t("Backend"), backend.toUpperCase(java.util.Locale.ROOT))) {
            for (String candidate : new String[]{"auto","gpu","compat"}) {
                if (ImGui.selectable(candidate.toUpperCase(java.util.Locale.ROOT), candidate.equals(backend))) {
                    geo.geoRenderBackend=candidate;
                    SprauteConfig.save();
                }
            }
            ImGui.endCombo();
        }
        boolean lod=geo.isGeoLodEnabled();
        if (ImGui.checkbox(EditorI18n.t("Экранный LOD"), lod)) {
            geo.geoLodEnabled=!lod; SprauteConfig.save();
        }
        boolean stats=geo.isGeoRenderStatsEnabled();
        if (ImGui.checkbox(EditorI18n.t("Статистика рендера моделей"), stats)) {
            geo.geoRenderStats=!stats; SprauteConfig.save();
        }
        float[] minPixels={geo.geoMinPixels};
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderFloat(EditorI18n.t("Минимальный размер на экране"), minPixels, .25f, 16f, "%.2f px")) {
            geo.geoMinPixels=minPixels[0]; SprauteConfig.save();
        }
        float[] maxDistance={geo.geoMaxRenderDistance};
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderFloat(EditorI18n.t("Общая дальность моделей"), maxDistance, 0f, 512f, "%.0f")) {
            geo.geoMaxRenderDistance=maxDistance[0]; SprauteConfig.save();
        }
        int[] midInterval={geo.geoMidAnimationInterval};
        if (ImGui.sliderInt(EditorI18n.t("Интервал анимации LOD2"), midInterval, 1, 8)) {
            geo.geoMidAnimationInterval=midInterval[0]; SprauteConfig.save();
        }
        int[] farInterval={geo.geoFarAnimationInterval};
        if (ImGui.sliderInt(EditorI18n.t("Интервал анимации LOD3"), farInterval, 1, 20)) {
            geo.geoFarAnimationInterval=farInterval[0]; SprauteConfig.save();
        }
        float[] cullMargin={geo.geoCullingMargin};
        if (ImGui.sliderFloat(EditorI18n.t("Запас visual bounds"), cullMargin, 0f, 8f, "%.2f")) {
            geo.geoCullingMargin=cullMargin[0]; SprauteConfig.save();
        }
        if (stats) {
            var s=org.zonarstudio.spraute_engine.entity.client.GeoRenderStats.snapshot();
            ImGui.textDisabled(String.format(java.util.Locale.ROOT,
                    "render %d | cull %d | fallback %d | %.2f ms transform | %.2f ms draw",
                    s.rendered(),s.culled(),s.fallback(),s.transformMs(),s.drawMs()));
            ImGui.textDisabled(String.format(java.util.Locale.ROOT,"LOD 0/1/2/3: %d / %d / %d / %d",
                    s.lod0(),s.lod1(),s.lod2(),s.lod3()));
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.86f, 0.88f, 0.92f, 1f, EditorI18n.t("Настройки движка"));
        ImGui.textDisabled(EditorI18n.t("Формат и цвет имени в чате (npcChat / say). $Name — имя говорящего."));
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.text(EditorI18n.t("Формат имени"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.inputText("##name-format", nameFormat)) {
            dirty = true;
            status = "";
        }
        ImGui.textDisabled("[$Name]:");

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Цвет имени"));
        int flags = ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.DisplayHex
                | ImGuiColorEditFlags.PickerHueBar;
        ImGui.setNextItemWidth(-1f);
        if (ImGui.colorEdit4("##name-color", nameColor, flags)) {
            dirty = true;
            status = "";
        }

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Превью"));
        String previewName = nameFormat.get().replace("$Name", "Steve");
        ImGui.textColored(nameColor[0], nameColor[1], nameColor[2], 1f,
                previewName.isBlank() ? "Steve" : previewName);
        ImGui.sameLine();
        ImGui.textDisabled(" Hello!");

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        ImGui.textColored(0.86f, 0.88f, 0.92f, 1f, EditorI18n.t("Синхронизация файлов"));
        ImGui.textDisabled(EditorI18n.t("Лимит отправки ресурсов игрокам (текстуры, модели, звуки). На выделенном сервере сохраняется в конфиг сервера."));
        ImGui.spacing();

        ImGui.text(EditorI18n.t("Максимум на файл (МБ)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderInt("##sync-file-mb", maxFileMb, SprauteConfig.MIN_FILE_MB, SprauteConfig.MAX_FILE_MB)) {
            dirty = true;
            status = "";
        }

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Максимум всего (МБ)"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderInt("##sync-total-mb", maxTotalMb, SprauteConfig.MIN_TOTAL_MB, SprauteConfig.MAX_TOTAL_MB)) {
            dirty = true;
            status = "";
        }

        ImGui.spacing();
        ImGui.text(EditorI18n.t("Максимум файлов"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.sliderInt("##sync-files", maxFiles, SprauteConfig.MIN_FILES, SprauteConfig.MAX_FILES)) {
            dirty = true;
            status = "";
        }
        ImGui.textDisabled(EditorI18n.t("По умолчанию: 8 МБ / файл, 150 МБ всего, 2000 файлов."));

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        ImGui.textColored(0.86f, 0.88f, 0.92f, 1f, EditorI18n.t("Автообновление мода"));
        ImGui.textDisabled(EditorI18n.t("На сервере: отдать игрокам свой JAR. У игрока: скачать, если версия другая. Сохраняется сразу."));
        ImGui.spacing();
        if (ImGui.checkbox(EditorI18n.t("Обновлять мод с сервера"), autoUpdateMod)) {
            SprauteConfig.get().autoUpdateMod = autoUpdateMod.get();
            SprauteConfig.save();
        }
        if (ImGui.checkbox(EditorI18n.t("Обязательный перезапуск"), autoUpdateRequireRestart)) {
            SprauteConfig.get().autoUpdateRequireRestart = autoUpdateRequireRestart.get();
            SprauteConfig.save();
        }
        ImGui.textDisabled(EditorI18n.t("Сервер: игрок с другой версией мода не остаётся в мире. После скачивания JAR его отключает."));

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        boolean saveDisabled = !dirty;
        if (saveDisabled) ImGui.beginDisabled();
        boolean saveClicked = ImGui.button(EditorI18n.t("Сохранить"), 140f, 0f);
        if (saveDisabled) ImGui.endDisabled();
        if (saveClicked && dirty) {
            save();
        }

        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Сбросить"), 120f, 0f)) {
            reloadFromConfig();
            status = "";
        }
        if (!status.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(0.45f, 0.85f, 0.55f, 1f, status);
        }

        ImGui.end();
    }

    private static void reloadFromConfig() {
        SprauteConfig config = SprauteConfig.get();
        nameFormat.set(config.nameFormat != null ? config.nameFormat : "[$Name]:");
        parseHex(config.nameColor != null ? config.nameColor : "#FFFFFF", nameColor);
        if (RemoteProjectClient.isRemote() && RemoteProjectClient.hasSyncLimits()) {
            maxFileMb[0] = RemoteProjectClient.serverMaxFileMb();
            maxTotalMb[0] = RemoteProjectClient.serverMaxTotalMb();
            maxFiles[0] = RemoteProjectClient.serverMaxFiles();
        } else {
            maxFileMb[0] = config.assetSyncMaxFileMb;
            maxTotalMb[0] = config.assetSyncMaxTotalMb;
            maxFiles[0] = config.assetSyncMaxFiles;
        }
        uiScale[0] = config.editorUiScale;
        autoUpdateMod.set(config.isAutoUpdateModEnabled());
        autoUpdateRequireRestart.set(config.isAutoUpdateRequireRestart());
        dirty = false;
    }

    private static void save() {
        try {
            SprauteConfig config = SprauteConfig.get();
            config.nameFormat = nameFormat.get();
            config.nameColor = String.format("#%02X%02X%02X",
                    clamp255(nameColor[0]), clamp255(nameColor[1]), clamp255(nameColor[2]));
            config.assetSyncMaxFileMb = maxFileMb[0];
            config.assetSyncMaxTotalMb = maxTotalMb[0];
            config.assetSyncMaxFiles = maxFiles[0];
            config.autoUpdateMod = autoUpdateMod.get();
            config.autoUpdateRequireRestart = autoUpdateRequireRestart.get();
            config.normalize();
            maxFileMb[0] = config.assetSyncMaxFileMb;
            maxTotalMb[0] = config.assetSyncMaxTotalMb;
            maxFiles[0] = config.assetSyncMaxFiles;
            SprauteConfig.save();
            if (RemoteProjectClient.isRemote()) {
                RemoteProjectClient.pushSyncLimits(
                        config.assetSyncMaxFileMb, config.assetSyncMaxTotalMb, config.assetSyncMaxFiles);
            }
            dirty = false;
            status = EditorI18n.t("Сохранено");
        } catch (Exception e) {
            status = EditorI18n.t("Ошибка сохранения");
        }
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, (int) (v * 255f + 0.5f)));
    }

    private static void parseHex(String hex, float[] out) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() >= 6) {
                out[0] = Integer.parseInt(h.substring(0, 2), 16) / 255f;
                out[1] = Integer.parseInt(h.substring(2, 4), 16) / 255f;
                out[2] = Integer.parseInt(h.substring(4, 6), 16) / 255f;
                out[3] = 1f;
                return;
            }
        } catch (Exception ignored) {
        }
        out[0] = out[1] = out[2] = out[3] = 1f;
    }
}
