package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.ClientPlayerVars;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerVarsPacket;
import org.zonarstudio.spraute_engine.scene.PlayerVarsOps;
import org.zonarstudio.spraute_engine.scene.SceneNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Inspector tables for player.data / player.savedData and PLAYERS defaults. */
final class PlayerVarsInspector {
    private final ImString newName = new ImString(64);
    private final ImString newValue = new ImString(256);
    private int newScope;
    private int newType;
    private final Map<String, ImString> valueBuffers = new LinkedHashMap<>();
    private String focusedId = "";
    private long lastSnapTime;

    void renderDefaults(SceneNode node, Consumer<Runnable> applyEdit) {
        if (!EditorInspectorUi.beginSection("players-vars", EditorI18n.t("Переменные при старте"), false)) {
            return;
        }
        SceneNode.ensurePlayerVarMaps(node);
        EditorInspectorUi.hint(EditorI18n.t(
                "Выдаются игроку при входе / запуске сцены, только если ключа ещё нет. savedData сохраняется в мире, data — до рестарта."));
        EditorInspectorUi.scriptApi(EditorI18n.t("Из скрипта"),
                "_scene.node(\"players\").savedDataDefaults[\"coins\"] = 0\n"
                        + "_scene.node(\"players\").dataDefaults[\"intro\"] = true\n"
                        + "player.savedData[\"coins\"]");

        renderMap(EditorI18n.t("Сессия · player.data"), node.playerDataDefaults, false, true, applyEdit, node);
        ImGui.spacing();
        renderMap(EditorI18n.t("Сейв · player.savedData"), node.playerSavedDefaults, true, true, applyEdit, node);
        renderAddRow(true, (saved, name, value) -> applyEdit.accept(() -> {
            SceneNode.ensurePlayerVarMaps(node);
            (saved ? node.playerSavedDefaults : node.playerDataDefaults).put(name, value);
            SceneEditorSession.syncLiveNode(node.id);
        }));
        EditorInspectorUi.endSection();
    }

    void renderLive(SceneNode node) {
        if (node == null || node.playerUuid == null || node.playerUuid.isBlank()) return;
        if (!EditorInspectorUi.beginSection("player-vars", EditorI18n.t("Переменные"), false)) {
            return;
        }
        ClientPlayerVars.requestIfStale(node.playerUuid);
        ClientPlayerVars.Snapshot snap = ClientPlayerVars.get(node.playerUuid);
        EditorInspectorUi.hint(EditorI18n.t(
                "Личные переменные этого игрока. Те же словари, что player.data и player.savedData."));
        EditorInspectorUi.scriptApi(EditorI18n.t("Из скрипта"),
                "player.data[\"intro\"] = true\nplayer.savedData[\"coins\"] = 10\n_node.data[\"intro\"]");
        if (snap == null) {
            ImGui.textDisabled(EditorI18n.t("Загрузка…"));
            EditorInspectorUi.endSection();
            return;
        }
        if (snap.time() != lastSnapTime) {
            lastSnapTime = snap.time();
            if (focusedId.isEmpty()) valueBuffers.clear();
        }
        renderLiveMap(EditorI18n.t("Сессия · player.data"), snap.data(), false, node.playerUuid);
        ImGui.spacing();
        renderLiveMap(EditorI18n.t("Сейв · player.savedData"), snap.saved(), true, node.playerUuid);
        renderAddRow(false, (saved, name, value) ->
                ModNetwork.CHANNEL.sendToServer(PlayerVarsPacket.set(node.playerUuid, saved, name, value)));
        EditorInspectorUi.endSection();
    }

    private void renderMap(String title, Map<String, Object> map, boolean saved, boolean defaults,
                           Consumer<Runnable> applyEdit, SceneNode node) {
        ImGui.textDisabled(title);
        if (map == null || map.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("пусто"));
            return;
        }
        for (String key : new ArrayList<>(map.keySet())) {
            Object value = map.get(key);
            String rowId = (saved ? "s:" : "d:") + key;
            ImString buf = buffer(rowId, PlayerVarsOps.formatValue(value));
            ImGui.pushID(rowId);
            ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() * 0.38f));
            ImGui.text(key);
            ImGui.sameLine();
            ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() - 28f));
            int flags = ImGuiInputTextFlags.EnterReturnsTrue;
            if (ImGui.inputText("##v", buf, flags) || ImGui.isItemDeactivatedAfterEdit()) {
                if (defaults && applyEdit != null) {
                    String type = PlayerVarsOps.inferType(value);
                    applyEdit.accept(() -> {
                        map.put(key, PlayerVarsOps.parseValue(type, buf.get()));
                        SceneEditorSession.syncLiveNode(node.id);
                    });
                }
            }
            if (ImGui.isItemActive()) focusedId = rowId;
            else if (rowId.equals(focusedId) && !ImGui.isItemActive()) focusedId = "";
            ImGui.sameLine();
            if (ImGui.smallButton("×")) {
                if (defaults && applyEdit != null) {
                    applyEdit.accept(() -> {
                        map.remove(key);
                        SceneEditorSession.syncLiveNode(node.id);
                    });
                }
            }
            ImGui.popID();
        }
    }

    private void renderLiveMap(String title, Map<String, Object> map, boolean saved, String uuid) {
        ImGui.textDisabled(title);
        if (map == null || map.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("пусто"));
            return;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            String rowId = (saved ? "ls:" : "ld:") + key;
            ImString buf = buffer(rowId, PlayerVarsOps.formatValue(e.getValue()));
            ImGui.pushID(rowId);
            ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() * 0.38f));
            ImGui.text(key);
            ImGui.sameLine();
            ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() - 28f));
            if (ImGui.inputText("##v", buf, ImGuiInputTextFlags.EnterReturnsTrue)
                    || ImGui.isItemDeactivatedAfterEdit()) {
                String type = PlayerVarsOps.inferType(e.getValue());
                ModNetwork.CHANNEL.sendToServer(PlayerVarsPacket.set(
                        uuid, saved, key, PlayerVarsOps.parseValue(type, buf.get())));
            }
            if (ImGui.isItemActive()) focusedId = rowId;
            else if (rowId.equals(focusedId) && !ImGui.isItemActive()) focusedId = "";
            ImGui.sameLine();
            if (ImGui.smallButton("×")) {
                ModNetwork.CHANNEL.sendToServer(PlayerVarsPacket.remove(uuid, saved, key));
            }
            ImGui.popID();
        }
    }

    private void renderAddRow(boolean defaults, AddHandler onAdd) {
        ImGui.spacing();
        ImGui.setNextItemWidth(90f);
        String scopeShown = newScope == 1
                ? EditorI18n.t("сейв")
                : EditorI18n.t("сессия");
        if (ImGui.beginCombo("##var-scope", scopeShown)) {
            if (ImGui.selectable(EditorI18n.t("сессия · data"), newScope == 0)) newScope = 0;
            if (ImGui.selectable(EditorI18n.t("сейв · savedData"), newScope == 1)) newScope = 1;
            ImGui.endCombo();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(80f);
        String typeShown = switch (newType) {
            case 1 -> EditorI18n.t("строка");
            case 2 -> EditorI18n.t("да/нет");
            default -> EditorI18n.t("число");
        };
        if (ImGui.beginCombo("##var-type", typeShown)) {
            if (ImGui.selectable(EditorI18n.t("число"), newType == 0)) newType = 0;
            if (ImGui.selectable(EditorI18n.t("строка"), newType == 1)) newType = 1;
            if (ImGui.selectable(EditorI18n.t("да/нет"), newType == 2)) newType = 2;
            ImGui.endCombo();
        }
        ImGui.setNextItemWidth(Math.max(70f, ImGui.getContentRegionAvailX() * 0.42f));
        ImGui.inputTextWithHint("##var-name", EditorI18n.t("имя…"), newName);
        ImGui.sameLine();
        ImGui.setNextItemWidth(Math.max(60f, ImGui.getContentRegionAvailX() - 84f));
        ImGui.inputTextWithHint("##var-value", EditorI18n.t("значение…"), newValue);
        ImGui.sameLine();
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Добавить"), 76f)) {
            String name = newName.get() == null ? "" : newName.get().trim();
            if (!name.isEmpty() && name.indexOf(' ') < 0) {
                String type = switch (newType) {
                    case 1 -> "string";
                    case 2 -> "bool";
                    default -> "number";
                };
                Object value = PlayerVarsOps.parseValue(type, newValue.get());
                onAdd.add(newScope == 1, name, value);
                newName.set("");
                newValue.set("");
            }
        }
    }

    private ImString buffer(String id, String text) {
        ImString buf = valueBuffers.get(id);
        if (buf == null) {
            buf = new ImString(256);
            valueBuffers.put(id, buf);
            buf.set(text == null ? "" : text);
        } else if (!id.equals(focusedId)) {
            buf.set(text == null ? "" : text);
        }
        return buf;
    }

    @FunctionalInterface
    private interface AddHandler {
        void add(boolean saved, String name, Object value);
    }
}
