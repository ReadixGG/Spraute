package org.sprautestudio.spraute_engine.editor.panels;

import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.ui.Box;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.PopupMenu;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Visual front-end for every /script command: per-story controls plus global actions. */
public class StoryPanel extends Widget {
    private final Box toolbar = Box.row().gap(3).pad(6, 3);
    private final TextField search = new TextField().placeholder("Фильтр").icon(Icon.SEARCH);
    private final StoryList list = new StoryList();
    private String filter = "Все";
    public Runnable showConsole;
    public IntConsumer openInEditor;

    public StoryPanel() {
        add(toolbar);
        add(list);
        tool(Icon.REFRESH, "Обновить", () -> ClientState.get().requestStatus());
        tool(Icon.STOP, "Остановить все сюжеты (/script stop all)", () -> run("script stop all")).iconColor(Theme.ERROR);
        Button startMany = new Button("Запустить…", () -> Dialog.prompt("Запустить сюжеты", "Id через пробел или запятую:", "",
                v -> v.matches("[0-9 ,]+") ? null : "Только числа", v -> run("script start all \"" + v.replace(",", " ").trim() + "\"")))
                .icon(Icon.PLAY).style(Button.Style.GHOST);
        startMany.autoWidth();
        startMany.tooltip("/script start all <id...>");
        toolbar.add(startMany);
        menuButton("Категории", Icon.LIST, pm -> {
            List<String> cats = ClientState.get().status().categories();
            if (cats.isEmpty()) pm.item("(нет категорий)", null).enabled(() -> false);
            for (String c : cats) pm.item("Запустить " + c, Icon.PLAY, null, () -> run("script start category " + c)).color(Theme.SUCCESS);
            if (!cats.isEmpty()) pm.separator();
            for (String c : cats) pm.item("Остановить " + c, Icon.STOP, null, () -> run("script stop category " + c)).color(Theme.ERROR);
        });
        menuButton("Остановить", Icon.STOP, pm -> {
            pm.item("Все сюжеты", Icon.STOP, null, () -> run("script stop all"));
            pm.item("Только сюжеты (scripts)", Icon.STOP, null, () -> run("script stop scripts"));
            pm.item("Фоновые (backscripts)", Icon.STOP, null, () -> run("script stop backscripts"));
        });
        menuButton("Окружение", Icon.GEAR, pm -> {
            pm.item("Состояние окружения", Icon.INFO, null, () -> runShow("script environment"));
            pm.item("Активные сюжеты", Icon.LIST, null, () -> runShow("script environment active"));
        });
        Dropdown<String> filterBox = new Dropdown<>(() -> List.of("Все", "Активные"), () -> filter, s -> s, s -> filter = s);
        filterBox.prefW = 96;
        toolbar.add(new Widget()).flex = 1;
        toolbar.add(filterBox);
        search.prefW = 130;
        toolbar.add(search);
    }

    private Button tool(Icon icon, String tooltip, Runnable action) {
        return toolbar.add(Button.icon(icon, tooltip, action));
    }

    private void menuButton(String text, Icon icon, java.util.function.Consumer<PopupMenu> build) {
        Button b = new Button(text, null).icon(icon).style(Button.Style.GHOST);
        b.onClick = () -> {
            PopupMenu pm = new PopupMenu();
            build.accept(pm);
            pm.show(b.x, b.y + b.h + 1, b);
        };
        b.autoWidth();
        b.prefW += 4;
        toolbar.add(b);
    }

    private static void run(String command) {
        ClientState.get().command(command);
    }

    private void runShow(String command) {
        run(command);
        if (showConsole != null) showConsole.run();
    }

    @Override
    public void layout() {
        toolbar.bounds(x, y, w, 24);
        toolbar.layout();
        list.bounds(x, y + 24, w, h - 24);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.PANEL);
        c.fill(x, y + 23, w, 1, Theme.BORDER);
    }

    @Override
    public void tick() {
        super.tick();
        if (visible && isShown() && ClientState.get().statusAge() > 1000) ClientState.get().requestStatus();
    }

    private List<Packets.ScriptEntry> visibleEntries() {
        String q = search.text().trim().toLowerCase();
        List<Packets.ScriptEntry> out = new ArrayList<>();
        for (Packets.ScriptEntry e : ClientState.get().status().scripts()) {
            switch (filter) {
                case "Активные" -> {
                    if (!e.active) continue;
                }
                default -> {
                }
            }
            if (!q.isEmpty() && !(("#" + e.id + " " + e.title + " " + e.project).toLowerCase().contains(q))) continue;
            out.add(e);
        }
        // active first
        out.sort((a, b) -> a.active != b.active ? (a.active ? -1 : 1) : Integer.compare(a.id, b.id));
        return out;
    }

    // ------------------------------------------------------------------ list

    private final class StoryList extends Widget implements UI.CursorProvider {
        private static final int ROW = 24, BTN = 18;
        private double scroll;
        private static final String[] ACTIONS = {"run", "pause", "setup", "goto", "trigger", "more"};

        private int buttonsX() {
            return x + w - ACTIONS.length * (BTN + 2) - 8;
        }

        private int actionAt(double mx) {
            int bx = buttonsX();
            for (int i = 0; i < ACTIONS.length; i++) {
                int ax = bx + i * (BTN + 2);
                if (mx >= ax && mx < ax + BTN) return i;
            }
            return -1;
        }

        @Override
        public int cursor(int mx, int my) {
            return actionAt(mx) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            List<Packets.ScriptEntry> entries = visibleEntries();
            Packets.Status st = ClientState.get().status();
            c.pushClip(x, y, w, h);
            if (!st.ready()) {
                c.wrapped(net.minecraft.client.Minecraft.getInstance().getConnection() == null
                        ? "Войдите в мир, чтобы управлять сюжетами."
                        : "У вас нет прав оператора, или мир ещё загружается.",
                        x + 10, y + 10, w - 20, Theme.TEXT_MUTED, 2);
            }
            int max = Math.max(0, entries.size() * ROW - h);
            scroll = Math.max(0, Math.min(max, scroll));
            int first = (int) (scroll / ROW);
            for (int i = first; i < entries.size(); i++) {
                int ry = y + i * ROW - (int) scroll;
                if (ry > y + h) break;
                paintRow(c, entries.get(i), ry, mx, my);
            }
            c.popClip();
        }

        private void paintRow(Canvas c, Packets.ScriptEntry e, int ry, int mx, int my) {
            boolean hov = isHovered() && my >= ry && my < ry + ROW;
            if (hov) c.fill(x, ry, w, ROW, Theme.HOVER);
            c.fill(x + 8, ry + ROW - 1, w - 16, 1, 0x10FFFFFF);
            int dot = e.active ? (e.paused ? Theme.WARNING : Theme.SUCCESS) : Theme.TEXT_MUTED;
            c.round(x + 10, ry + 9, 6, 6, 3, dot);
            c.text("#" + e.id, x + 22, ry + 8, Theme.TEXT_DIM);
            int tx = x + 64;
            int bx = buttonsX();
            int progressW = 110;
            int titleMax = bx - tx - progressW - 16;
            String title = e.title.isEmpty() ? "Сюжет " + e.id : e.title;
            if (e.spraute) {
                int bw = c.width("Spraute") + 8;
                c.round(tx, ry + 6, bw, 12, 4, Theme.alpha(Theme.ACCENT, 0.25f));
                c.text("Spraute", tx + 4, ry + 8, Theme.ACCENT_HOVER);
                tx += bw + 6;
                titleMax -= bw + 6;
            }
            c.textEllipsized(title, tx, ry + 8, titleMax, e.active ? Theme.TEXT : Theme.TEXT_DIM);
            // progress
            int px = bx - progressW - 10;
            if (e.active && e.actionCount > 0) {
                float p = Math.min(1f, (float) e.actionIndex / e.actionCount);
                c.round(px, ry + 15, progressW, 4, 2, Theme.BORDER);
                c.round(px, ry + 15, Math.max(4, (int) (progressW * p)), 4, 2, e.paused ? Theme.WARNING : Theme.ACCENT_2);
                String stage = e.stage == null || e.stage.equals("null") ? "" : e.stage;
                c.textEllipsized(e.actionIndex + "/" + e.actionCount + (stage.isEmpty() ? "" : " · " + stage), px, ry + 4, progressW, Theme.TEXT_MUTED);
            } else {
                c.text(e.active ? "активен" : "не запущен", px, ry + 8, Theme.TEXT_MUTED);
            }
            // buttons
            for (int i = 0; i < ACTIONS.length; i++) {
                int ax = bx + i * (BTN + 2), ay = ry + 3;
                boolean hot = hov && mx >= ax && mx < ax + BTN && my >= ay && my < ay + BTN;
                boolean enabled = isEnabled(ACTIONS[i], e);
                if (hot && enabled) c.round(ax, ay, BTN, BTN, 3, Theme.RAISED_HOVER);
                Icon icon;
                int color;
                switch (ACTIONS[i]) {
                    case "run" -> {
                        icon = e.active ? Icon.STOP : Icon.PLAY;
                        color = e.active ? Theme.ERROR : Theme.SUCCESS;
                    }
                    case "pause" -> {
                        icon = e.paused ? Icon.PLAY : Icon.PAUSE;
                        color = Theme.WARNING;
                    }
                    case "setup" -> {
                        icon = Icon.SETUP;
                        color = Theme.INFO;
                    }
                    case "goto" -> {
                        icon = Icon.TAG;
                        color = Theme.CODE_LABEL;
                    }
                    case "trigger" -> {
                        icon = Icon.BOLT;
                        color = Theme.WARNING;
                    }
                    default -> {
                        icon = Icon.MENU;
                        color = Theme.TEXT_DIM;
                    }
                }
                c.icon(icon, ax + (BTN - 9) / 2, ay + (BTN - 9) / 2, enabled ? color : Theme.alpha(color, 0.3f));
            }
        }

        private boolean isEnabled(String action, Packets.ScriptEntry e) {
            return switch (action) {
                case "pause", "trigger" -> e.active;
                default -> true;
            };
        }

        @Override
        public String tooltipText(int mx, int my) {
            Packets.ScriptEntry e = entryAt(my);
            if (e == null) return null;
            int a = actionAt(mx);
            if (a < 0) return "#" + e.id + " " + e.title + (e.project.isEmpty() ? "" : "\nПроект: " + e.project)
                    + (e.active ? "\nЭтап: " + e.stage + "\nДействие " + e.actionIndex + " из " + e.actionCount : "");
            return switch (ACTIONS[a]) {
                case "run" -> e.active ? "Остановить (/script stop " + e.id + ")" : "Запустить (/script start " + e.id + ")";
                case "pause" -> e.paused ? "Продолжить (/script resume)" : "Пауза (/script pause)";
                case "setup" -> "Подготовить (/script setup " + e.id + ")";
                case "goto" -> e.active ? "Перейти к метке (/script goto)" : "Запустить с метки (/script start <id> <метка>)";
                case "trigger" -> "Триггер (/script trigger)";
                default -> "Ещё: индекс, фаза, действия, справка, данные";
            };
        }

        private Packets.ScriptEntry entryAt(double my) {
            List<Packets.ScriptEntry> entries = visibleEntries();
            int idx = (int) ((my - y + scroll) / ROW);
            return idx >= 0 && idx < entries.size() ? entries.get(idx) : null;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            Packets.ScriptEntry e = entryAt(my);
            if (e == null) return true;
            int a = actionAt(mx);
            if (button == 1 || (a >= 0 && ACTIONS[a].equals("more"))) {
                moreMenu(e, (int) mx, (int) my);
                return true;
            }
            if (a < 0 || !isEnabled(ACTIONS[a], e)) return true;
            switch (ACTIONS[a]) {
                case "run" -> run(e.active ? "script stop " + e.id : "script start " + e.id);
                case "pause" -> run(e.paused ? "script resume " + e.id : "script pause " + e.id);
                case "setup" -> run("script setup " + e.id);
                case "goto" -> labelsMenu(e, (int) mx, (int) my);
                case "trigger" -> {
                    if (e.spraute) run("script trigger " + e.id + " next");
                    else Dialog.prompt("Триггер", "Имя триггера сюжета #" + e.id + ":", "", v -> v.isEmpty() ? "Введите имя" : null,
                            v -> run("script trigger " + e.id + " " + v));
                }
                default -> {
                }
            }
            return true;
        }

        private void labelsMenu(Packets.ScriptEntry e, int mx, int my) {
            PopupMenu pm = new PopupMenu();
            if (e.labels.isEmpty()) pm.item(e.active ? "(меток нет)" : "(метки видны у запущенного сюжета)", null).enabled(() -> false);
            for (String label : e.labels) {
                pm.item(label, Icon.TAG, null, () -> run(e.active ? "script goto " + e.id + " " + label : "script start " + e.id + " " + label))
                        .color(Theme.CODE_LABEL);
            }
            pm.separator();
            pm.item("Ввести метку…", Icon.EDIT, null, () -> Dialog.prompt("Метка", "Имя метки:", "", v -> v.isEmpty() ? "Введите имя" : null,
                    v -> run(e.active ? "script goto " + e.id + " " + v : "script start " + e.id + " " + v)));
            pm.show(mx, my);
        }

        private void moreMenu(Packets.ScriptEntry e, int mx, int my) {
            PopupMenu pm = new PopupMenu();
            pm.item(e.active ? "Остановить" : "Запустить", e.active ? Icon.STOP : Icon.PLAY, null,
                    () -> run(e.active ? "script stop " + e.id : "script start " + e.id));
            pm.item("Подготовить (setup)", Icon.SETUP, null, () -> run("script setup " + e.id));
            pm.item("Перейти к действию №…", Icon.SKIP, null, () -> Dialog.prompt("Переход", "Номер действия (0–" + Math.max(0, e.actionCount - 1) + "):",
                    String.valueOf(e.actionIndex), v -> v.matches("\\d+") ? null : "Введите число", v -> run("script jump " + e.id + " " + v)))
                    .enabled(() -> e.active);
            pm.separator();
            pm.item("Фаза (phase)", Icon.INFO, null, () -> runShow("script phase " + e.id)).enabled(() -> e.active);
            pm.item("Действия (actions)", Icon.LIST, null, () -> runShow("script actions " + e.id));
            pm.item("Данные (data)", Icon.FILE, null, () -> runShow("script data " + e.id)).enabled(() -> e.active);
            if (e.spraute) {
                pm.separator();
                if (openInEditor != null) pm.item("Открыть в редакторе", Icon.EDIT, null, () -> openInEditor.accept(e.id));
                pm.item("Выгрузить из игры", Icon.TRASH, null, () -> Dialog.confirm("Выгрузить сюжет",
                        "Сюжет #" + e.id + " будет остановлен и удалён из мира. Файл в проекте останется.", "Выгрузить", true,
                        () -> ClientState.get().undeploy(e.id))).color(Theme.ERROR);
            }
            pm.show(mx, my);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            scroll -= delta * ROW * 2;
            return true;
        }
    }
}
