package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient;
import org.zonarstudio.spraute_engine.script.ScriptLangCatalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Project string table (Unity / Godot style) over {@code spraute_engine/lang/*.json}.
 * Runtime still uses {@code tr(key)} — this is the editor for those keys.
 */
public final class LocalizationPanel {
    private LocalizationPanel() {}

    private static final ImString search = new ImString(128);
    private static final ImString newKey = new ImString(128);
    private static final ImString newLang = new ImString(16);
    private static final Map<String, ImString> buffers = new HashMap<>();
    private static final Set<String> editing = new HashSet<>();

    private static ScriptLangCatalog.Table table = new ScriptLangCatalog.Table();
    private static boolean loaded;
    private static boolean dirty;
    private static String status = "";
    private static String selectedKey = "";

    public static void render(ImBoolean show) {
        if (!show.get()) return;
        if (!loaded) {
            reload();
            loaded = true;
        }

        ImGui.setNextWindowSize(920f, 580f, ImGuiCond.FirstUseEver);
        if (!ImGui.begin(EditorI18n.t("Локализация###Localization"), show, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        renderToolbar();
        ImGui.spacing();
        renderTable();

        ImGui.separator();
        if (!status.isBlank()) {
            ImGui.textColored(0.55f, 0.85f, 0.6f, 1f, status);
        } else {
            int missing = countMissing();
            ImGui.textDisabled(table.rows.size() + EditorI18n.t(" ключей  ·  ")
                    + table.languages.size() + EditorI18n.t(" языка  ·  lang/*.json")
                    + (missing > 0 ? EditorI18n.t("  ·  пустых: ") + missing : "")
                    + (dirty ? EditorI18n.t("  ·  не сохранено") : ""));
        }
        ImGui.end();
    }

    private static void renderToolbar() {
        if (ImGui.button(EditorI18n.t("Сохранить"))) save();
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Обновить"))) reload();
        ImGui.sameLine();
        ImGui.setNextItemWidth(160f);
        if (ImGui.inputTextWithHint("##loc-search", EditorI18n.t("поиск ключа…"), search)) {
            /* filter live */
        }

        ImGui.sameLine();
        ImGui.textDisabled(EditorI18n.t("fallback"));
        ImGui.sameLine();
        ImGui.setNextItemWidth(80f);
        String fb = table.fallback == null || table.fallback.isBlank() ? "en" : table.fallback;
        if (ImGui.beginCombo("##loc-fallback", fb)) {
            for (String lang : table.languages) {
                boolean sel = lang.equalsIgnoreCase(fb);
                if (ImGui.selectable(lang, sel)) {
                    table.fallback = lang;
                    dirty = true;
                    status = "";
                }
                if (sel) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        ImGui.setNextItemWidth(160f);
        ImGui.inputTextWithHint("##loc-new-key", EditorI18n.t("новый ключ"), newKey);
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Ключ"))) addKey();
        ImGui.sameLine();
        ImGui.setNextItemWidth(70f);
        ImGui.inputTextWithHint("##loc-new-lang", "de", newLang);
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Язык"))) addLanguage();
    }

    private static void renderTable() {
        List<String> langs = table.languages;
        int cols = 2 + Math.max(1, langs.size());
        int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.ScrollY
                | ImGuiTableFlags.Resizable | ImGuiTableFlags.SizingStretchProp;
        if (!ImGui.beginTable("##loc-table", cols, flags, 0f, -28f)) return;

        ImGui.tableSetupScrollFreeze(1, 1);
        ImGui.tableSetupColumn(EditorI18n.t("Ключ"), ImGuiTableColumnFlags.WidthStretch, 0.28f);
        if (langs.isEmpty()) {
            ImGui.tableSetupColumn("en", ImGuiTableColumnFlags.WidthStretch, 0.6f);
        } else {
            for (String lang : langs) {
                ImGui.tableSetupColumn(lang, ImGuiTableColumnFlags.WidthStretch, 0.6f / langs.size());
            }
        }
        ImGui.tableSetupColumn("##loc-del", ImGuiTableColumnFlags.WidthFixed, 52f);
        ImGui.tableHeadersRow();

        String filter = search.get().trim().toLowerCase(Locale.ROOT);
        List<String> keys = new ArrayList<>(table.rows.keySet());
        for (String key : keys) {
            if (!filter.isEmpty() && !key.toLowerCase(Locale.ROOT).contains(filter)
                    && !rowContains(key, filter)) continue;

            ImGui.tableNextRow();
            ImGui.pushID(key);

            ImGui.tableSetColumnIndex(0);
            if (editField("k", key, key, 128)) {
                String next = buf("k", key).get().trim();
                if (renameKey(key, next)) {
                    ImGui.popID();
                    ImGui.endTable();
                    return;
                }
            }
            if (ImGui.isItemClicked()) selectedKey = key;
            if (ImGui.beginPopupContextItem("##loc-key-ctx")) {
                if (ImGui.menuItem(EditorI18n.t("Копировать tr(ключ)"))) {
                    ImGui.setClipboardText("tr(\"" + key + "\")");
                    status = EditorI18n.t("Скопировано: ") + "tr(\"" + key + "\")";
                }
                ImGui.endPopup();
            }

            int col = 1;
            if (langs.isEmpty()) {
                ImGui.tableSetColumnIndex(col);
                ImGui.textDisabled("—");
            } else {
                for (String lang : langs) {
                    ImGui.tableSetColumnIndex(col++);
                    String value = table.get(key, lang);
                    boolean empty = value.isBlank();
                    if (empty) ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.55f, 0.45f, 1f);
                    if (editField("v-" + lang, key, value, 2048)) {
                        table.set(key, lang, buf("v-" + lang, key).get());
                        dirty = true;
                        status = "";
                    }
                    if (empty) ImGui.popStyleColor();
                }
            }

            ImGui.tableSetColumnIndex(cols - 1);
            if (ImGui.smallButton("×")) {
                table.removeKey(key);
                dirty = true;
                status = "";
                if (key.equals(selectedKey)) selectedKey = "";
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Удалить ключ"));

            ImGui.popID();
        }
        ImGui.endTable();
    }

    private static boolean rowContains(String key, String filter) {
        var row = table.rows.get(key);
        if (row == null) return false;
        for (String v : row.values()) {
            if (v != null && v.toLowerCase(Locale.ROOT).contains(filter)) return true;
        }
        return false;
    }

    private static ImString buf(String kind, String key) {
        String id = kind + "\0" + key;
        return buffers.computeIfAbsent(id, k -> new ImString(2048));
    }

    private static boolean editField(String kind, String key, String value, int cap) {
        String id = kind + "\0" + key;
        ImString buf = buffers.computeIfAbsent(id, k -> {
            ImString s = new ImString(Math.max(cap, 64));
            s.set(value == null ? "" : value);
            return s;
        });
        if (!editing.contains(id)) buf.set(value == null ? "" : value);
        boolean changed = ImGui.inputText("##" + kind, buf);
        if (ImGui.isItemActive()) editing.add(id);
        else editing.remove(id);
        return changed;
    }

    private static boolean renameKey(String from, String to) {
        if (to.isBlank() || to.equals(from)) return false;
        if (table.rows.containsKey(to)) {
            status = EditorI18n.t("Ключ уже есть: ") + to;
            return false;
        }
        table.renameKey(from, to);
        dirty = true;
        status = "";
        selectedKey = to;
        buffers.clear();
        editing.clear();
        return true;
    }

    private static void addKey() {
        String key = newKey.get().trim();
        if (key.isBlank()) key = "new.key";
        if (table.rows.containsKey(key)) {
            status = EditorI18n.t("Ключ уже есть: ") + key;
            return;
        }
        for (String lang : table.languages) table.set(key, lang, "");
        if (table.languages.isEmpty()) table.set(key, "en", "");
        newKey.set("");
        selectedKey = key;
        dirty = true;
        status = "";
    }

    private static void addLanguage() {
        String code = newLang.get().trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (code.isBlank()) return;
        if (table.languages.contains(code)) {
            status = EditorI18n.t("Язык уже есть: ") + code;
            return;
        }
        table.languages.add(code);
        for (String key : table.rows.keySet()) table.set(key, code, "");
        newLang.set("");
        dirty = true;
        status = "";
    }

    private static int countMissing() {
        int n = 0;
        for (var row : table.rows.values()) {
            for (String lang : table.languages) {
                String v = row.get(lang);
                if (v == null || v.isBlank()) n++;
            }
        }
        return n;
    }

    private static void reload() {
        table = ScriptLangCatalog.loadProject(workspaceRoot());
        if (table.languages.isEmpty()) {
            table.languages.add("en");
            table.languages.add("ru");
        }
        buffers.clear();
        editing.clear();
        dirty = false;
        status = EditorI18n.t("Загружено из lang/");
        pullRemoteIfNeeded();
    }

    private static void save() {
        try {
            Path root = workspaceRoot();
            ScriptLangCatalog.saveProject(root, table);
            ScriptLangCatalog.invalidate();
            if (RemoteProjectClient.isRemote()) {
                RemoteProjectClient.pushIfRemote(root.resolve("lang").resolve("_meta.json"));
                for (String lang : table.languages) {
                    RemoteProjectClient.pushIfRemote(root.resolve("lang").resolve(lang + ".json"));
                }
            }
            dirty = false;
            status = EditorI18n.t("Сохранено в lang/*.json");
        } catch (Exception e) {
            status = EditorI18n.t("Не удалось сохранить: ") + e.getMessage();
        }
    }

    private static void pullRemoteIfNeeded() {
        if (!RemoteProjectClient.isRemote() || !RemoteProjectClient.hasList()) return;
        for (var e : RemoteProjectClient.entries()) {
            if (e.dir || e.path == null) continue;
            if (!e.path.startsWith("lang/") || !e.path.endsWith(".json")) continue;
            RemoteProjectClient.ensureLocal(e.path, () -> loaded = false);
        }
    }

    private static Path workspaceRoot() {
        return SceneEditorSession.gameDir().resolve("spraute_engine");
    }
}
