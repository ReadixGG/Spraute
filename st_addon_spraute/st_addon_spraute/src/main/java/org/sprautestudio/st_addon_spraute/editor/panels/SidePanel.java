package org.sprautestudio.st_addon_spraute.editor.panels;

import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.editor.tabs.ScriptTab;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.lang.CommandSpec;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.ListView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TabBar;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Right side panel: outline of the active script and the command palette with docs. */
public class SidePanel extends Widget {
    private record Row(String header, Analyzer.Symbol symbol) {}

    private record Cmd(String title, String snippet, String doc, String category) {}

    private final TabBar tabs = new TabBar();
    private final ListView<Row> outline;
    private final TextField search = new TextField().placeholder("Поиск команды").icon(Icon.SEARCH);
    private final ListView<Cmd> commands;
    private final Supplier<EditorTab> active;
    private long lastRefresh;
    private Cmd selectedCmd;

    public SidePanel(Supplier<EditorTab> active) {
        this.active = active;
        tabs.editorStyle = false;
        TabBar.Tab tOutline = tabs.add(new TabBar.Tab(() -> "Структура", Icon.LIST, "outline"));
        tabs.add(new TabBar.Tab(() -> "Команды", Icon.BOLT, "commands"));
        tabs.select(tOutline);
        tabs.onSelect = t -> layout();
        add(tabs);

        outline = add(new ListView<>((c, r, x, y, w, h, sel, hov) -> {
            if (r.header != null) {
                c.text(r.header, x + 6, y + 5, Theme.TEXT_MUTED);
                return;
            }
            Analyzer.Symbol s = r.symbol;
            Icon icon;
            int color;
            switch (s.kind()) {
                case CHARACTER -> {
                    icon = Icon.USER;
                    color = Theme.CODE_CHARACTER;
                }
                case LOCATION -> {
                    icon = Icon.PIN;
                    color = Theme.CODE_LOCATION;
                }
                case VARIABLE -> {
                    icon = Icon.DOT;
                    color = Theme.CODE_VARIABLE;
                }
                default -> {
                    icon = Icon.TAG;
                    color = Theme.CODE_LABEL;
                }
            }
            c.icon(icon, x + 12, y + 3, color);
            c.text(s.name(), x + 26, y + 4, Theme.TEXT);
            if (s.detail() != null && !s.detail().isEmpty()) {
                int nx = x + 32 + c.width(s.name());
                c.textEllipsized(s.detail(), nx, y + 4, w - (nx - x) - 6, Theme.TEXT_MUTED);
            }
        }));
        outline.rowHeight = 15;
        outline.background = Theme.PANEL;
        outline.emptyText = "Откройте скрипт, чтобы увидеть персонажей, точки, переменные и метки.";
        outline.activateOnSingleClick = true;
        outline.onActivate = r -> {
            EditorTab tab = active.get();
            if (r.symbol != null && tab != null) tab.reveal(r.symbol.start());
        };

        add(search);
        search.onChange = s -> refreshCommands();
        commands = add(new ListView<>((c, cmd, x, y, w, h, sel, hov) -> {
            c.round(x + 8, y + 5, 4, 7, 2, Theme.category(cmd.category));
            c.textEllipsized(cmd.title, x + 18, y + 4, w - 24, Theme.TEXT);
        }));
        commands.rowHeight = 15;
        commands.background = Theme.PANEL;
        commands.onSelect = cmd -> selectedCmd = cmd;
        commands.onActivate = cmd -> {
            EditorTab tab = active.get();
            if (tab instanceof ScriptTab st) st.editor.insertAtCaret(cmd.snippet + "\n");
        };
        refreshCommands();
    }

    private void refreshCommands() {
        String q = search.text().trim().toLowerCase();
        List<Cmd> list = new ArrayList<>();
        for (CommandSpec s : Specs.commands()) {
            Cmd cmd = new Cmd(s.signature(), s.snippet(), s.doc, s.category);
            if (q.isEmpty() || (s.signature() + " " + s.doc).toLowerCase().contains(q)) list.add(cmd);
        }
        commands.setItems(list);
    }

    private boolean isOutline() {
        TabBar.Tab t = tabs.selected();
        return t == null || "outline".equals(t.data);
    }

    @Override
    public void layout() {
        tabs.bounds(x, y, w, 22);
        boolean o = isOutline();
        outline.visible = o;
        search.visible = commands.visible = !o;
        outline.bounds(x, y + 22, w, h - 22);
        search.bounds(x + 6, y + 26, w - 12, 18);
        int docH = 90;
        commands.bounds(x, y + 48, w, h - 48 - docH);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 500 || !isShown()) return;
        lastRefresh = now;
        EditorTab tab = active.get();
        Analyzer.Result r = tab != null ? tab.analysis() : null;
        List<Row> rows = new ArrayList<>();
        if (r != null) {
            section(rows, "ПЕРСОНАЖИ", r.characters.values());
            section(rows, "ТОЧКИ", r.locations.values());
            section(rows, "ПЕРЕМЕННЫЕ", r.vars.values());
            section(rows, "МЕТКИ", r.labels.values());
        }
        outline.setItems(rows);
    }

    private static void section(List<Row> rows, String title, java.util.Collection<Analyzer.Symbol> symbols) {
        if (symbols.isEmpty()) return;
        rows.add(new Row(title + " (" + symbols.size() + ")", null));
        for (Analyzer.Symbol s : symbols) rows.add(new Row(null, s));
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.PANEL);
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        if (isOutline()) return;
        int docY = y + h - 90;
        c.fill(x, docY, w, 1, Theme.BORDER);
        Cmd cmd = selectedCmd;
        if (cmd == null) {
            c.wrapped("Двойной клик — вставить команду в текущий скрипт.", x + 8, docY + 6, w - 16, Theme.TEXT_MUTED, 2);
            return;
        }
        c.pushClip(x, docY + 1, w, 89);
        c.textEllipsized(cmd.title, x + 8, docY + 6, w - 16, Theme.CODE_COMMAND);
        c.wrapped(cmd.doc, x + 8, docY + 20, w - 16, Theme.TEXT_DIM, 2);
        c.popClip();
    }
}
