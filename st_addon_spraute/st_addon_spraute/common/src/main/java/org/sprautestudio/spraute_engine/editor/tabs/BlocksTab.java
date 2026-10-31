package org.sprautestudio.spraute_engine.editor.tabs;

import org.sprautestudio.spraute_engine.editor.SstLanguage;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.visual.BlockCodegen;
import org.sprautestudio.spraute_engine.editor.visual.BlockEditor;
import org.sprautestudio.spraute_engine.editor.visual.VisualScript;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.Diagnostic;
import org.sprautestudio.spraute_engine.ui.Box;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.code.CodeEditor;
import org.sprautestudio.spraute_engine.ui.code.LanguageSupport;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Label;
import org.sprautestudio.spraute_engine.ui.widgets.SplitPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scratch-style block editor tab with an optional live preview of the generated code. */
public class BlocksTab extends EditorTab {
    public final BlockEditor blocks;
    private final CodeEditor preview;
    private final SplitPane split;
    private final Box toolbar;
    private boolean dirty;
    private int previewVersion = -1;
    private int changeCounter;

    public BlocksTab(Project project, Project.FileRef file, String json, Supplier<String> position, Consumer<String> convertToText) {
        super(project, file);
        blocks = new BlockEditor(VisualScript.fromJson(json));
        blocks.positionProvider = position;
        blocks.assetPicker = org.sprautestudio.spraute_engine.editor.assets.AssetPicker::open;
        blocks.onChange = b -> {
            changeCounter++;
            if (!dirty) {
                dirty = true;
                if (onDirtyChanged != null) onDirtyChanged.run();
            }
        };
        preview = new CodeEditor("", new SstLanguage());
        preview.readOnly = true;
        preview.showMinimap = false;
        preview.visible = false;
        split = add(new SplitPane(true, blocks, preview, false, 300));
        toolbar = add(Box.row().gap(4).pad(6, 3).background(Theme.PANEL));
        Button code = new Button("Код", () -> {
            preview.visible = !preview.visible;
            split.layout();
        }).icon(Icon.CODE).style(Button.Style.GHOST).toggle(() -> preview.visible);
        code.autoWidth();
        code.tooltip("Показать сгенерированный код SST");
        toolbar.add(code);
        Button toText = new Button("В текстовый скрипт", () -> convertToText.accept(blocks.generated().text)).icon(Icon.FILE).style(Button.Style.GHOST);
        toText.autoWidth();
        toText.tooltip("Создать .sst файл из этих блоков");
        toolbar.add(toText);
        Button home = new Button("К началу", blocks::goHome).icon(Icon.HOME).style(Button.Style.GHOST);
        home.autoWidth();
        toolbar.add(home);
        Label hint = new Label("ЛКМ — перетащить, ПКМ — меню, Ctrl+колесо — масштаб, Ctrl — тащить один блок").color(Theme.TEXT_MUTED);
        hint.flex = 1;
        hint.align = Label.Align.RIGHT;
        toolbar.add(hint);
    }

    @Override
    public void layout() {
        toolbar.bounds(x, y, w, 24);
        toolbar.layout();
        split.bounds(x, y + 24, w, h - 24);
        split.layout();
        blocks.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (preview.visible && previewVersion != changeCounter) {
            previewVersion = changeCounter;
            preview.setText(blocks.generated().text);
        }
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        c.fill(x, y + 23, w, 1, Theme.BORDER);
    }

    @Override
    public Icon icon() {
        return Icon.GRAPH;
    }

    @Override
    public int iconColor() {
        return Theme.WARNING;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void save() throws IOException {
        project.write(file, blocks.script().toJson());
        dirty = false;
        if (onDirtyChanged != null) onDirtyChanged.run();
    }

    @Override
    public String source() {
        return blocks.generated().text;
    }

    @Override
    public int scriptId() {
        BlockCodegen.Result r = blocks.generated();
        return r.analysis.program().header != null ? r.analysis.program().header.id : -1;
    }

    @Override
    public List<Problem> problems() {
        BlockCodegen.Result r = blocks.generated();
        List<Problem> list = new ArrayList<>();
        for (String p : r.problems) list.add(new Problem(LanguageSupport.Severity.ERROR, p, title(), null));
        for (Diagnostic d : r.analysis.diagnostics) {
            LanguageSupport.Severity sev = d.isError() ? LanguageSupport.Severity.ERROR
                    : d.severity == Diagnostic.Severity.WARNING ? LanguageSupport.Severity.WARNING : LanguageSupport.Severity.INFO;
            list.add(new Problem(sev, d.message, title() + " (блоки)", null));
        }
        return list;
    }

    @Override
    public Analyzer.Result analysis() {
        return blocks.generated().analysis;
    }

    @Override
    public void reveal(int offset) {
        blocks.requestFocus();
    }

    @Override
    public void focusContent() {
        blocks.requestFocus();
        UI ui = UI.get();
        if (ui != null) ui.relayout();
    }

    @Override
    public void undo() {
        blocks.undo();
    }

    @Override
    public void redo() {
        blocks.redo();
    }
}
