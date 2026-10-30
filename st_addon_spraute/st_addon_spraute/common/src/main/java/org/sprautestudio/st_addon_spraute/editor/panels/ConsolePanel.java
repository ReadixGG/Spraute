package org.sprautestudio.st_addon_spraute.editor.panels;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.ClientState;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Server output of the stories plus a command line with history. */
public class ConsolePanel extends Widget {
    private final TextField input = new TextField().placeholder("Команда, например: script start 5001   (↑↓ — история)");
    private final Button clear = Button.icon(Icon.TRASH, "Очистить", () -> ClientState.get().clearLog());
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private double scroll;
    private boolean stickToBottom = true;
    private int seenVersion = -1;
    private static final int LINE = 11;
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");

    public ConsolePanel() {
        add(input);
        add(clear);
        input.icon = Icon.TERMINAL;
        input.onSubmit = s -> {
            String cmd = s.trim();
            if (cmd.isEmpty()) return;
            if (history.isEmpty() || !history.get(history.size() - 1).equals(cmd)) history.add(cmd);
            historyIndex = -1;
            ClientState.get().command(cmd);
            input.setText("");
            stickToBottom = true;
        };
        input.keyHook = (key, mods) -> {
            if (key == GLFW.GLFW_KEY_UP && !history.isEmpty()) {
                historyIndex = historyIndex < 0 ? history.size() - 1 : Math.max(0, historyIndex - 1);
                input.setText(history.get(historyIndex));
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN && historyIndex >= 0) {
                historyIndex++;
                input.setText(historyIndex >= history.size() ? "" : history.get(historyIndex));
                if (historyIndex >= history.size()) historyIndex = -1;
                return true;
            }
            return false;
        };
    }

    public void focusInput() {
        input.requestFocus();
    }

    @Override
    public void layout() {
        input.bounds(x + 6, y + h - 22, w - 34, 18);
        clear.bounds(x + w - 24, y + h - 22, 18, 18);
    }

    private int logH() {
        return h - 26;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        List<ClientState.LogLine> lines = ClientState.get().log();
        int total = lines.size() * LINE + 6;
        int max = Math.max(0, total - logH());
        if (seenVersion != ClientState.get().logVersion()) {
            seenVersion = ClientState.get().logVersion();
            if (stickToBottom) scroll = max;
        }
        scroll = Math.max(0, Math.min(max, scroll));
        c.pushClip(x, y, w, logH());
        if (lines.isEmpty()) {
            c.text("Здесь появятся сообщения сюжетов, ошибки и вывод команд /script.", x + 8, y + 8, Theme.TEXT_MUTED);
        }
        int first = (int) (scroll / LINE);
        for (int i = first; i < lines.size(); i++) {
            int ly = y + 4 + i * LINE - (int) scroll;
            if (ly > y + logH()) break;
            ClientState.LogLine l = lines.get(i);
            int color = switch (l.level()) {
                case Packets.Log.ERROR -> Theme.ERROR;
                case Packets.Log.WARNING -> Theme.WARNING;
                case Packets.Log.SUCCESS -> Theme.SUCCESS;
                case Packets.Log.OUTPUT -> Theme.TEXT;
                default -> Theme.TEXT_DIM;
            };
            String ts = time.format(new Date(l.time()));
            c.text(ts, x + 8, ly, Theme.TEXT_MUTED);
            int sx = x + 8 + c.width(ts) + 6;
            String src = l.source();
            c.text(src, sx, ly, Theme.ACCENT_HOVER);
            sx += c.width(src) + 6;
            c.text(c.ellipsize(l.text(), x + w - sx - 6), sx, ly, color);
        }
        c.popClip();
        c.fill(x, y + h - 26, w, 1, Theme.BORDER);
    }

    @Override
    public String tooltipText(int mx, int my) {
        List<ClientState.LogLine> lines = ClientState.get().log();
        int idx = (int) ((my - y - 4 + scroll) / LINE);
        if (my > y + logH() || idx < 0 || idx >= lines.size()) return null;
        String t = lines.get(idx).text();
        return t.length() > 60 ? t : null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        List<ClientState.LogLine> lines = ClientState.get().log();
        int max = Math.max(0, lines.size() * LINE + 6 - logH());
        scroll = Math.max(0, Math.min(max, scroll - delta * LINE * 3));
        stickToBottom = scroll >= max - 1;
        return true;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (button == 1 && my < y + logH()) {
            List<ClientState.LogLine> lines = ClientState.get().log();
            int idx = (int) ((my - y - 4 + scroll) / LINE);
            if (idx >= 0 && idx < lines.size()) {
                UI.setClipboard(lines.get(idx).text());
                UI ui = UI.get();
                if (ui != null) ui.toastInfo("Строка скопирована");
            }
            return true;
        }
        return false;
    }
}
