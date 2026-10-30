package org.sprautestudio.st_addon_spraute.editor.panels;

import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.code.LanguageSupport;
import org.sprautestudio.st_addon_spraute.ui.widgets.ListView;

import java.util.List;
import java.util.function.Supplier;

/** Errors and warnings of all open tabs; click to navigate. */
public class ProblemsPanel extends ListView<EditorTab.Problem> {
    private final Supplier<List<EditorTab.Problem>> source;
    private long lastRefresh;

    public ProblemsPanel(Supplier<List<EditorTab.Problem>> source) {
        super(null);
        this.source = source;
        rowHeight = 16;
        background = Theme.CODE_BG;
        emptyText = "Проблем не найдено — сюжет готов к запуску.";
        painter = (c, p, x, y, w, h, sel, hov) -> {
            Icon icon = p.severity() == LanguageSupport.Severity.ERROR ? Icon.ERROR
                    : p.severity() == LanguageSupport.Severity.WARNING ? Icon.WARNING : Icon.INFO;
            int color = p.severity() == LanguageSupport.Severity.ERROR ? Theme.ERROR
                    : p.severity() == LanguageSupport.Severity.WARNING ? Theme.WARNING : Theme.INFO;
            c.icon(icon, x + 8, y + 3, color);
            int whereW = c.width(p.where()) + 10;
            c.textEllipsized(p.message(), x + 22, y + 4, w - 30 - whereW, Theme.TEXT);
            c.text(p.where(), x + w - whereW, y + 4, Theme.TEXT_MUTED);
        };
        activateOnSingleClick = true;
        onActivate = p -> {
            if (p.reveal() != null) p.reveal().run();
        };
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastRefresh > 400 && isShown()) {
            lastRefresh = now;
            setItems(source.get());
        }
    }
}
