package org.sprautestudio.spraute_engine.editor.assets;

import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.runtime.Images;
import org.sprautestudio.spraute_engine.runtime.models.ModelLibrary;
import org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity;
import org.sprautestudio.spraute_engine.runtime.orb.Orbs;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.ListView;

import java.util.List;
import java.util.function.Consumer;

/** A custom model of the project: live preview, its animations to play, how to use it in a story. */
public class ModelTab extends EditorTab {
    public final Project.ModelRef model;
    private final ModelView view = new ModelView();
    private final ListView<String> anims;
    private final Button character, reload, folder;
    private String key = "";

    private final Consumer<String> createEntity;

    public ModelTab(Project project, Project.ModelRef model, Consumer<String> createEntity) {
        super(project, null);
        this.model = model;
        this.createEntity = createEntity;
        add(view);
        view.emptyText = "Войдите в мир, чтобы увидеть модель";
        anims = add(new ListView<>((c, a, x, y, w, h, sel, hov) -> {
            c.icon(Icon.FILM, x + 6, y + (h - 9) / 2, a.equals(view.playing()) ? Theme.SUCCESS : Theme.TEXT_MUTED);
            c.textEllipsized(a, x + 20, y + (h - 8) / 2, w - 24, sel ? Theme.TEXT : Theme.TEXT_DIM);
        }));
        anims.rowHeight = 18;
        anims.background = Theme.PANEL;
        anims.onActivate = this::play;
        anims.onSelect = this::play;
        character = add(new Button("Создать сущность с этой моделью", () -> createEntity.accept(model.name())).icon(Icon.USER).style(Button.Style.PRIMARY));
        reload = add(new Button("Перезагрузить файлы", this::refresh).icon(Icon.REFRESH).style(Button.Style.GHOST));
        reload.tooltip("Перечитать модель, текстуру и анимации после правки в Blockbench");
        folder = add(new Button("Папка модели", () -> net.minecraft.Util.getPlatform().openFile(model.dir().toFile())).icon(Icon.FOLDER).style(Button.Style.GHOST));
        for (Button b : List.of(character, reload, folder)) b.autoWidth();
        refresh();
    }

    private void refresh() {
        ModelLibrary.scan();
        ModelLibrary.Model m = ModelLibrary.find(project.name(), model.name());
        key = m != null ? m.key() : "";
        String k = key;
        view.setType(Orbs.MODEL.get(), e -> {
            if (e instanceof SprauteModelEntity me) me.setModel(k);
        });
        anims.setItems(ModelLibrary.animations(m));
        UI ui = UI.get();
        if (ui != null && m == null) ui.toastError("В папке модели нет файла .geo.json");
    }

    private void play(String a) {
        if (a == null) return;
        String err = view.play("spraute:" + project.name() + "/" + model.name() + "/" + a, "action", true, 1f);
        UI ui = UI.get();
        if (err != null && ui != null) ui.toastError(err);
    }

    @Override
    public String title() {
        return model.name();
    }

    @Override
    public void layout() {
        int lw = Math.min(240, w / 3);
        view.bounds(x, y + 30, w - lw, h - 30 - 34);
        anims.bounds(x + w - lw, y + 30, lw, h - 30 - 34);
        int bx = x + 10, by = y + h - 27;
        for (Button b : List.of(character, reload, folder)) {
            b.bounds(bx, by, b.prefW, 20);
            bx += b.prefW + 6;
        }
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        ModelLibrary.Model m = ModelLibrary.byKey(key);
        c.text(model.name(), x + 10, y + 10, Theme.TEXT);
        String info = m == null ? "нет .geo.json" : "файлы модели · анимаций: " + anims.items().size()
                + (m.texture() == null ? "   ·   нет текстуры (.png)" : "");
        c.text(info, x + 20 + c.width(model.name()), y + 10, m == null ? Theme.ERROR : Theme.TEXT_MUTED);
        c.fill(x, y + 29, w, 1, Theme.BORDER);
        int lw = Math.min(240, w / 3);
        c.fill(x + w - lw, y + 30, 1, h - 64, Theme.BORDER);
        if (anims.items().isEmpty()) c.textCentered("нет анимаций (.animation.json)", x + w - lw / 2, y + 50, Theme.TEXT_MUTED);
        c.fill(x, y + h - 34, w, 34, Theme.PANEL);
        c.fill(x, y + h - 34, w, 1, Theme.BORDER);
    }

    @Override
    public Icon icon() {
        return Icon.CUBE;
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
    }
}
