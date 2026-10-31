package org.sprautestudio.spraute_engine.editor;

import org.sprautestudio.spraute_engine.editor.project.Workspace;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.ListView;
import org.sprautestudio.spraute_engine.ui.widgets.PopupMenu;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/** Start page shown when no project is open. */
public class WelcomePage extends Widget {
    private final Button newProject, demo, docs;
    private final ListView<Workspace.ProjectInfo> projects;
    private final SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private float appear;

    public WelcomePage(Runnable onNew, Runnable onDemo, Runnable onDocs, Consumer<Workspace.ProjectInfo> onOpen,
                       Consumer<Workspace.ProjectInfo> onDelete) {
        newProject = add(new Button("Новый проект", onNew).icon(Icon.PLUS).style(Button.Style.PRIMARY));
        demo = add(new Button("Пример проекта", onDemo).icon(Icon.PLAY));
        docs = add(new Button("Справочник языка", onDocs).icon(Icon.BOOK));
        projects = add(new ListView<>((c, p, x, y, w, h, sel, hov) -> {
            c.round(x + 8, y + 6, 22, 22, 5, Theme.alpha(Theme.ACCENT, 0.25f));
            c.icon(Icon.FOLDER, x + 15, y + 12, Theme.ACCENT_HOVER);
            c.text(p.name(), x + 38, y + 7, Theme.TEXT);
            String info = p.files() + " файлов" + (p.opened() > 0 ? " · открыт " + date.format(new Date(p.opened())) : "");
            c.textEllipsized(info, x + 38, y + 19, w - 46, Theme.TEXT_MUTED);
        }));
        projects.rowHeight = 34;
        projects.emptyText = "Проектов пока нет. Создайте новый или откройте пример.";
        projects.activateOnSingleClick = true;
        projects.onActivate = onOpen;
        projects.onContext = (p, mx, my) -> {
            if (p == null) return;
            PopupMenu pm = new PopupMenu();
            pm.item("Открыть", Icon.FOLDER, null, () -> onOpen.accept(p));
            pm.item("Удалить проект", Icon.TRASH, null, () -> onDelete.accept(p)).color(Theme.ERROR);
            pm.show(mx, my);
        };
        reload();
    }

    public void reload() {
        projects.setItems(Workspace.projects());
    }

    private int cardW() {
        return Math.min(520, w - 40);
    }

    @Override
    public void layout() {
        int cw = cardW();
        int cx = x + (w - cw) / 2;
        int top = y + Math.max(30, h / 2 - 170);
        int bw = (cw - 16) / 3;
        newProject.bounds(cx, top + 92, bw, 22);
        demo.bounds(cx + bw + 8, top + 92, bw, 22);
        docs.bounds(cx + 2 * (bw + 8), top + 92, bw, 22);
        projects.bounds(cx, top + 150, cw, Math.max(60, Math.min(260, y + h - (top + 150) - 30)));
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        appear = Math.min(1, appear + dt * 3);
        c.fill(x, y, w, h, Theme.BG);
        // soft background glow
        for (int i = 0; i < 6; i++) {
            int r = 220 - i * 30;
            c.round(x + w / 2 - r * 2, y + h / 2 - r - 40, r * 4, r * 2, r, Theme.alpha(Theme.ACCENT, 0.025f));
        }
        int cw = cardW();
        int cx = x + (w - cw) / 2;
        int top = y + Math.max(30, h / 2 - 170);
        int slide = (int) ((1 - appear) * 12);
        c.pushTransform(cx, top + slide, 3);
        c.text("Spraute Engine", 0, 0, Theme.TEXT);
        c.popTransform();
        int lw = c.width("Spraute Engine") * 3;
        c.round(cx + lw + 6, top + slide + 6, 8, 8, 4, Theme.ACCENT);
        c.round(cx + lw + 16, top + slide + 6, 8, 8, 4, Theme.ACCENT_2);
        c.text("Редактор сюжетов", cx, top + 36 + slide, Theme.TEXT_DIM);
        c.text("Пишите сценарии кодом или собирайте из блоков — и запускайте прямо в мире.", cx, top + 50 + slide, Theme.TEXT_MUTED);
        c.text("НЕДАВНИЕ ПРОЕКТЫ", cx, top + 136, Theme.TEXT_MUTED);
        c.fill(cx + c.width("НЕДАВНИЕ ПРОЕКТЫ") + 8, top + 140, cw - c.width("НЕДАВНИЕ ПРОЕКТЫ") - 8, 1, Theme.BORDER);
    }
}
