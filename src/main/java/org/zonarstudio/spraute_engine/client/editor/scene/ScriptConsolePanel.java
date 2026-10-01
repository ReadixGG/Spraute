package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.network.ConsoleEvalPacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptConsole;
import org.zonarstudio.spraute_engine.script.SprauteMessages;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/** ImGui panel that mirrors {@link ScriptConsole} output and runs ad-hoc Spraute code. */
public final class ScriptConsolePanel {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final float INPUT_H = 110f;

    private final ImString input = new ImString(64_000);
    private int lastVersion = -1;
    private String copyFlash;

    public void render() {
        if (EditorIcons.iconButton("eraser", "console-clear-log", 16f, EditorI18n.t("Очистить лог"))) {
            ScriptConsole.clear();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("octagon-alert", "console-copy-errors", 16f, EditorI18n.t("Копировать ошибки"),
                1f, 0.50f, 0.42f, 1f)) {
            copyToClipboard(joinErrors(ScriptConsole.snapshot()), EditorI18n.t("ошибки скопированы"));
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("clipboard-copy", "console-copy-all", 16f, EditorI18n.t("Копировать всё"))) {
            copyToClipboard(joinAll(ScriptConsole.snapshot()), EditorI18n.t("лог скопирован"));
        }
        if (copyFlash != null) {
            ImGui.sameLine();
            ImGui.textColored(0.45f, 0.85f, 0.55f, 1f, copyFlash);
        }

        float availH = Math.max(80f, ImGui.getContentRegionAvailY());
        float logH = Math.max(40f, availH - INPUT_H - 52f);

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.09f, 0.10f, 0.12f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 6f);
        ImGui.beginChild("##script-console-body", 0f, logH, true,
                ImGuiWindowFlags.HorizontalScrollbar);

        List<ScriptConsole.Entry> entries = ScriptConsole.snapshot();
        for (int i = 0; i < entries.size(); i++) {
            ScriptConsole.Entry e = entries.get(i);
            float[] rgba = color(e.level());
            String time = TIME.format(Instant.ofEpochMilli(e.timeMs()));
            String line = "[" + time + "] [" + e.level().name() + "] [" + e.source() + "] "
                    + SprauteMessages.localizeConsoleClient(e.message());
            ImGui.pushID(i);
            ImGui.textColored(rgba[0], rgba[1], rgba[2], rgba[3], line);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(e.level() == ScriptConsole.Level.ERROR
                        ? EditorI18n.t("Клик — копировать ошибку")
                        : EditorI18n.t("Клик — копировать строку"));
                if (ImGui.isItemClicked()) {
                    copyToClipboard(line, EditorI18n.t("скопировано"));
                }
            }
            ImGui.popID();
        }

        int ver = ScriptConsole.version();
        if (ver != lastVersion || ScriptConsole.consumeScrollToBottom()) {
            ImGui.setScrollHereY(1f);
            lastVersion = ver;
        }

        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();

        ImGui.spacing();
        ImGui.textDisabled(EditorI18n.t("Код (несколько строк) · Ctrl+Enter — выполнить"));

        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.14f, 0.17f, 1f);
        ImGui.inputTextMultiline("##console-eval-input", input,
                ImGui.getContentRegionAvailX(), INPUT_H,
                ImGuiInputTextFlags.AllowTabInput | ImGuiInputTextFlags.CtrlEnterForNewLine);
        boolean inputFocused = ImGui.isItemActive() || ImGui.isItemFocused();
        ImGui.popStyleColor();

        if (inputFocused && ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Enter)) {
            runInput();
        }

        if (EditorIcons.iconButton("play", "console-run", 16f, EditorI18n.t("Выполнить  (Ctrl+Enter)"),
                0.45f, 0.85f, 0.55f, 1f)) {
            runInput();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("trash-2", "console-clear-input", 16f, EditorI18n.t("Очистить ввод"))) {
            input.set("");
        }
        ImGui.sameLine();
        ImGui.textDisabled(EditorI18n.t("переменные консоли сохраняются между запусками"));
    }

    private void copyToClipboard(String text, String flash) {
        if (text == null || text.isBlank()) {
            copyFlash = EditorI18n.t("нечего копировать");
            return;
        }
        ImGui.setClipboardText(text);
        copyFlash = flash;
    }

    private static String joinErrors(List<ScriptConsole.Entry> entries) {
        List<String> lines = new ArrayList<>();
        for (ScriptConsole.Entry e : entries) {
            if (e.level() != ScriptConsole.Level.ERROR) continue;
            String time = TIME.format(Instant.ofEpochMilli(e.timeMs()));
            lines.add("[" + time + "] [" + e.source() + "] " + e.message());
        }
        return String.join("\n", lines);
    }

    private static String joinAll(List<ScriptConsole.Entry> entries) {
        List<String> lines = new ArrayList<>();
        for (ScriptConsole.Entry e : entries) {
            String time = TIME.format(Instant.ofEpochMilli(e.timeMs()));
            lines.add("[" + time + "] [" + e.level().name() + "] [" + e.source() + "] " + e.message());
        }
        return String.join("\n", lines);
    }

    private void runInput() {
        String code = input.get();
        if (code == null || code.isBlank()) {
            ScriptConsole.warn("console", "Пустой код");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            ScriptConsole.error("console", "Нет соединения с сервером");
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new ConsoleEvalPacket(code));
    }

    private static float[] color(ScriptConsole.Level level) {
        return switch (level) {
            case ERROR -> new float[]{1f, 0.45f, 0.40f, 1f};
            case WARN -> new float[]{1f, 0.78f, 0.35f, 1f};
            case SCRIPT -> new float[]{0.55f, 0.82f, 1f, 1f};
            case INFO -> new float[]{0.78f, 0.82f, 0.88f, 1f};
        };
    }
}
