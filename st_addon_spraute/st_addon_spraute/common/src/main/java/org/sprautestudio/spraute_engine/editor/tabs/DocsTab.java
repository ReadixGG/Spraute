package org.sprautestudio.spraute_engine.editor.tabs;

import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.CommandSpec;
import org.sprautestudio.spraute_engine.lang.ParamType;
import org.sprautestudio.spraute_engine.lang.Specs;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Built-in language reference generated from the specs. */
public class DocsTab extends EditorTab {
    private record Entry(int kind, String title, String body, int color) {} // kind: 0 heading, 1 item, 2 paragraph

    private final TextField search = new TextField().placeholder("Поиск по справке").icon(Icon.SEARCH);
    private final List<Entry> entries = new ArrayList<>();
    private double scroll, target;
    private int contentH;

    public DocsTab() {
        super(null, null);
        add(search);
        search.onChange = s -> target = scroll = 0;
        build();
    }

    private void build() {
        entries.add(new Entry(0, "Spraute Story — язык сюжетов", null, Theme.ACCENT));
        entries.add(new Entry(2, null, """
                Файл .sst описывает один сюжет Spraute. Первая строка — заголовок:
                    script 5001 "Название"
                Затем объявления (character, location, var) и команды — по одной на строку.
                Метки (label) видны в панели «Сюжеты»: можно запустить сюжет с метки или перейти к ней.
                Время: 20t — тики, 1.5s — секунды, 2m — минуты. Строки поддерживают подстановки: "Привет, {playername()}".
                Горячие клавиши: Ctrl+Space — подсказки, Ctrl+клик / F12 — к объявлению, Alt+P — ваши координаты,
                Ctrl+/ — комментарий, Ctrl+D — дублировать строку, Alt+↑↓ — переместить строку, Ctrl+Shift+F — форматировать,
                F5 — загрузить и запустить, F6 — только загрузить, Shift+F5 — остановить.""", 0));
        entries.add(new Entry(0, "Ключевые слова", null, Theme.CODE_KEYWORD));
        for (Map.Entry<String, String> e : Specs.keywordDocs().entrySet()) {
            entries.add(new Entry(1, e.getKey(), e.getValue(), Theme.CODE_KEYWORD));
        }
        for (String cat : Specs.CATEGORIES) {
            entries.add(new Entry(0, "Команды: " + cat, null, Theme.category(cat)));
            for (CommandSpec s : Specs.commands()) {
                if (!s.category.equals(cat)) continue;
                StringBuilder body = new StringBuilder(s.doc);
                for (CommandSpec.Param p : s.allParams()) {
                    body.append("\n  ").append(p.name).append(" — ").append(p.type.title);
                }
                entries.add(new Entry(1, s.signature(), body.toString(), Theme.CODE_COMMAND));
            }
        }
        entries.add(new Entry(0, "Функции в выражениях", null, Theme.CODE_FUNCTION));
        for (Specs.FunctionSpec f : Specs.functions()) {
            entries.add(new Entry(1, f.signature() + " → " + f.returns(), f.doc(), Theme.CODE_FUNCTION));
        }
        entries.add(new Entry(0, "Типы значений", null, Theme.CODE_NUMBER));
        for (ParamType t : ParamType.values()) entries.add(new Entry(1, t.title, t.doc, Theme.CODE_NUMBER));
        entries.add(new Entry(0, "Эмоции диалогов", null, Theme.CODE_STRING));
        entries.add(new Entry(2, null, String.join(", ", Specs.EMOTIONS), 0));
    }

    @Override
    public String title() {
        return "Справочник";
    }

    @Override
    public void layout() {
        search.bounds(x + 16, y + 8, Math.min(320, w - 32), 18);
    }

    private boolean matches(Entry e, String q) {
        if (q.isEmpty() || e.kind == 0) return true;
        String all = ((e.title == null ? "" : e.title) + " " + (e.body == null ? "" : e.body)).toLowerCase();
        return all.contains(q);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (Math.abs(target - scroll) > 0.5) scroll += (target - scroll) * Math.min(1, dt * 18);
        else scroll = target;
        c.fill(x, y, w, h, Theme.CODE_BG);
        int top = y + 34;
        c.pushClip(x, top, w, h - 34);
        int width = Math.min(w - 48, 620);
        int left = x + 24;
        int yy = top + 4 - (int) scroll;
        String q = search.text().trim().toLowerCase();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!matches(e, q)) continue;
            if (e.kind == 0 && !q.isEmpty()) {
                // hide headings without matching items
                boolean any = false;
                for (int j = i + 1; j < entries.size() && entries.get(j).kind != 0; j++) if (matches(entries.get(j), q)) any = true;
                if (!any) continue;
            }
            int eh = height(c, e, width);
            if (yy + eh > top && yy < y + h) paintEntry(c, e, left, yy, width);
            yy += eh;
        }
        contentH = yy + (int) scroll - top + 20;
        c.popClip();
        c.fill(x, top - 1, w, 1, Theme.BORDER);
    }

    private int height(Canvas c, Entry e, int width) {
        return switch (e.kind) {
            case 0 -> 30;
            case 1 -> 14 + (e.body == null ? 0 : c.wrappedHeight(e.body, width - 16, 2)) + 8;
            default -> c.wrappedHeight(e.body, width, 2) + 8;
        };
    }

    private void paintEntry(Canvas c, Entry e, int left, int yy, int width) {
        switch (e.kind) {
            case 0 -> {
                c.round(left, yy + 12, 4, 12, 2, e.color);
                c.pushTransform(left + 10, yy + 13, 1.25f);
                c.text(e.title, 0, 0, Theme.TEXT);
                c.popTransform();
            }
            case 1 -> {
                c.round(left, yy, width, height(c, e, width) - 4, 4, Theme.PANEL);
                c.text(e.title, left + 8, yy + 4, e.color);
                if (e.body != null) c.wrapped(e.body, left + 8, yy + 16, width - 16, Theme.TEXT_DIM, 2);
            }
            default -> c.wrapped(e.body, left, yy, width, Theme.TEXT_DIM, 2);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        target = Math.max(0, Math.min(Math.max(0, contentH - (h - 34)), target - delta * 40));
        return true;
    }

    @Override
    public Icon icon() {
        return Icon.BOOK;
    }

    @Override
    public int iconColor() {
        return Theme.ACCENT_2;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public void save() {
    }

    @Override
    public String source() {
        return null;
    }

    @Override
    public int scriptId() {
        return -1;
    }

    @Override
    public List<Problem> problems() {
        return List.of();
    }

    @Override
    public Analyzer.Result analysis() {
        return null;
    }

    @Override
    public void reveal(int offset) {
    }

    @Override
    public void focusContent() {
        search.requestFocus();
    }
}
