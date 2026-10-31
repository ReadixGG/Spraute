package org.sprautestudio.spraute_engine.editor.tabs;

import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.visual.DialogueImport;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.ui.Box;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.code.CodeEditor;
import org.sprautestudio.spraute_engine.ui.code.LanguageSupport;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.Label;
import org.sprautestudio.spraute_engine.ui.widgets.ScrollView;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/** Import a dialogue into an open text or visual script. */
public final class FromTextTab extends EditorTab {
    private static final LanguageSupport PLAIN = new LanguageSupport() {
        @Override public Analysis analyze(String text) {
            int[] colors = new int[text.length()];
            Arrays.fill(colors, Theme.TEXT);
            return new Analysis() {
                @Override public List<LanguageSupport.Problem> problems() { return List.of(); }
                @Override public int[] colors() { return colors; }
            };
        }
        @Override public List<Completion> complete(String text, int caret, Analysis analysis, boolean explicit) { return List.of(); }
        @Override public String hover(String text, int offset, Analysis analysis) { return null; }
        @Override public Signature signature(String text, int caret, Analysis analysis) { return null; }
        @Override public int definition(String text, int offset, Analysis analysis) { return -1; }
        @Override public String lineComment() { return "#"; }
    };

    private static final class Gap {
        DialogueImport.Trigger trigger = DialogueImport.Trigger.F8;
        String value = "";
    }

    private EditorTab target;
    private final BiConsumer<EditorTab, DialogueImport.Plan> onApply;
    private final CodeEditor input = add(new CodeEditor("", PLAIN));
    private final Label heading = add(new Label("Имя персонажа: реплика — одна реплика на строку").color(Theme.TEXT_DIM));
    private final Label transitionsTitle = add(new Label("Переходы между репликами").color(Theme.TEXT_DIM));
    private final Box rows = Box.column().gap(4).pad(4);
    private final ScrollView transitions = add(new ScrollView(rows));
    private final Box toolbar = add(Box.row().gap(8).pad(8, 4).background(Theme.PANEL));
    private final Label status = new Label("Выберите переход после каждой реплики").color(Theme.TEXT_MUTED);
    private final List<Gap> gaps = new ArrayList<>();

    public FromTextTab(Project project, EditorTab target, BiConsumer<EditorTab, DialogueImport.Plan> onApply) {
        super(project, null);
        this.target = target;
        this.onApply = onApply;
        input.showMinimap = false;
        input.emptyHint = "Вставьте реплики: Алиса: Привет!";
        input.onChange = editor -> rebuild();
        Button apply = new Button("Добавить в скрипт", this::apply).icon(Icon.PLUS).style(Button.Style.PRIMARY);
        apply.autoWidth();
        toolbar.add(apply);
        status.flex = 1;
        toolbar.add(status);
        rebuild();
    }

    public void target(EditorTab target) { this.target = target; }

    private void rebuild() {
        List<String> speakers = new ArrayList<>();
        for (String raw : input.text().split("\\R")) {
            if (raw.isBlank()) continue;
            int colon = raw.indexOf(':');
            speakers.add((colon >= 0 ? raw.substring(0, colon) : raw).trim());
        }
        int count = Math.max(0, speakers.size() - 1);
        while (gaps.size() < count) gaps.add(new Gap());
        while (gaps.size() > count) gaps.remove(gaps.size() - 1);
        rows.clear();
        for (int i = 0; i < count; i++) {
            final int index = i;
            Gap gap = gaps.get(i);
            Box row = Box.row().gap(6);
            row.prefH = 20;
            Label label = new Label("После " + (i + 1) + ": " + speakers.get(i)).color(Theme.TEXT_DIM);
            label.prefW = 150;
            row.add(label);
            Dropdown<DialogueImport.Trigger> trigger = new Dropdown<>(
                    () -> List.of(DialogueImport.Trigger.F8, DialogueImport.Trigger.KEY, DialogueImport.Trigger.NPC),
                    () -> gap.trigger, FromTextTab::triggerName, choice -> {
                        gap.trigger = choice;
                        rebuild();
                    });
            trigger.prefW = 145;
            row.add(trigger);
            TextField value = new TextField().placeholder(gap.trigger == DialogueImport.Trigger.KEY ? "Клавиша, например H" : "Имя NPC");
            value.setText(gap.value);
            value.onChange = text -> gaps.get(index).value = text;
            value.visible = gap.trigger != DialogueImport.Trigger.F8;
            value.flex = 1;
            row.add(value);
            rows.add(row);
        }
        rows.prefH = 8 + count * 24;
        UI ui = UI.get();
        if (ui != null) ui.relayout();
    }

    private static String triggerName(DialogueImport.Trigger trigger) {
        return switch (trigger) {
            case F8 -> "F8";
            case KEY -> "Клавиша";
            case NPC -> "Нажатие по NPC";
        };
    }

    private void apply() {
        try {
            List<DialogueImport.Line> lines = DialogueImport.parse(input.text());
            if (gaps.size() != lines.size() - 1) rebuild();
            List<DialogueImport.Transition> choices = new ArrayList<>();
            for (Gap gap : gaps) choices.add(new DialogueImport.Transition(gap.trigger, gap.value));
            DialogueImport.Plan plan = DialogueImport.plan(target.source(), lines, choices);
            onApply.accept(target, plan);
            status.setText("Добавлено: " + lines.size() + " реплик");
        } catch (IllegalArgumentException | IllegalStateException e) {
            status.setText(e.getMessage());
        }
    }

    @Override public void layout() {
        heading.bounds(x + 8, y + 8, w - 16, 16);
        int editorH = Math.max(90, (h - 70) / 2);
        input.bounds(x + 8, y + 26, w - 16, editorH);
        input.layout();
        transitionsTitle.bounds(x + 8, y + 32 + editorH, w - 16, 16);
        int scrollY = y + 50 + editorH;
        transitions.bounds(x + 8, scrollY, w - 16, Math.max(0, y + h - 32 - scrollY));
        transitions.layout();
        toolbar.bounds(x, y + h - 26, w, 26);
        toolbar.layout();
    }

    @Override public String title() { return "Из текста"; }
    @Override public Icon icon() { return Icon.EDIT; }
    @Override public int iconColor() { return Theme.ACCENT; }
    @Override public boolean isDirty() { return false; }
    @Override public void save() throws IOException {}
    @Override public String source() { return null; }
    @Override public int scriptId() { return -1; }
    @Override public List<Problem> problems() { return List.of(); }
    @Override public Analyzer.Result analysis() { return null; }
    @Override public void reveal(int offset) { input.gotoOffset(offset, 0); }
    @Override public void focusContent() { input.requestFocus(); }
}
