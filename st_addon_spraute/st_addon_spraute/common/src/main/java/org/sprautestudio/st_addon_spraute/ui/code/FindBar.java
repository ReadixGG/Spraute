package org.sprautestudio.st_addon_spraute.ui.code;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

/** Find / replace panel docked at the top-right corner of a {@link CodeEditor}. */
public class FindBar extends Widget {
    final TextField find = new TextField().placeholder("Найти");
    final TextField replace = new TextField().placeholder("Заменить");
    private final Button caseBtn, wordBtn, prevBtn, nextBtn, replaceBtn, replaceAllBtn, closeBtn;
    boolean matchCase, wholeWord, showReplace;
    private final CodeEditor editor;
    static final int W = 280;

    FindBar(CodeEditor editor) {
        this.editor = editor;
        visible = false;
        add(find);
        add(replace);
        caseBtn = add(new Button("Aa", () -> {
            matchCase = !matchCase;
            editor.updateMatches(true);
        }).style(Button.Style.GHOST).toggle(() -> matchCase));
        caseBtn.tooltip("Учитывать регистр");
        wordBtn = add(new Button("W", () -> {
            wholeWord = !wholeWord;
            editor.updateMatches(true);
        }).style(Button.Style.GHOST).toggle(() -> wholeWord));
        wordBtn.tooltip("Слово целиком");
        prevBtn = add(Button.icon(Icon.CHEVRON_UP, "Предыдущее (Shift+Enter)", () -> editor.findNext(false)));
        nextBtn = add(Button.icon(Icon.CHEVRON_DOWN, "Следующее (Enter)", () -> editor.findNext(true)));
        closeBtn = add(Button.icon(Icon.CLOSE, "Закрыть (Esc)", this::hide));
        replaceBtn = add(new Button("Заменить", editor::replaceCurrent).style(Button.Style.GHOST));
        replaceAllBtn = add(new Button("Все", editor::replaceAll).style(Button.Style.GHOST));
        replaceAllBtn.tooltip("Заменить все");
        find.onChange = s -> editor.updateMatches(true);
        find.keyHook = (key, mods) -> {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editor.findNext(!UI.shift(mods));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                hide();
                return true;
            }
            return false;
        };
        replace.keyHook = (key, mods) -> {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (UI.ctrl(mods)) editor.replaceAll();
                else editor.replaceCurrent();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                hide();
                return true;
            }
            return false;
        };
    }

    void show(boolean withReplace, String initial) {
        visible = true;
        showReplace = withReplace;
        if (initial != null && !initial.isEmpty() && !initial.contains("\n")) find.setText(initial);
        find.selectAll();
        find.requestFocus();
        editor.layout();
        editor.updateMatches(true);
    }

    void hide() {
        visible = false;
        editor.clearMatches();
        editor.requestFocus();
    }

    int barHeight() {
        return showReplace ? 48 : 26;
    }

    @Override
    public void layout() {
        int fx = x + 6;
        find.bounds(fx, y + 4, W - 112, 18);
        caseBtn.bounds(fx + W - 106, y + 4, 18, 18);
        wordBtn.bounds(fx + W - 86, y + 4, 18, 18);
        prevBtn.bounds(fx + W - 66, y + 4, 18, 18);
        nextBtn.bounds(fx + W - 46, y + 4, 18, 18);
        closeBtn.bounds(fx + W - 26, y + 4, 18, 18);
        replace.visible = replaceBtn.visible = replaceAllBtn.visible = showReplace;
        replace.bounds(fx, y + 26, W - 112, 18);
        replaceBtn.bounds(fx + W - 106, y + 26, 58, 18);
        replaceAllBtn.bounds(fx + W - 46, y + 26, 38, 18);
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.shadow(x, y, w, h, 4, 5);
        c.roundBorder(x, y, w, h, 4, Theme.RAISED, Theme.BORDER_STRONG);
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        String count = editor.matchSummary();
        if (!count.isEmpty()) {
            int cw = c.width(count);
            c.text(count, find.x + find.w - cw - 5, find.y + 5, Theme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        return true;
    }
}
