package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.client.Minecraft;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.runtime.Images;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.widgets.Button;

import java.util.List;
import java.util.function.Consumer;

/** Preview of a project picture with the snippets that use it. */
public class ImageTab extends EditorTab {
    public final Project.ImageRef image;
    private final Button copyIcon, copyPortrait, insertCharacter;

    public ImageTab(Project project, Project.ImageRef image, Consumer<String> insertCode) {
        super(project, null);
        this.image = image;
        copyIcon = add(new Button("icon \"" + image.name() + "\"", () -> copy("icon \"" + image.name() + "\"")).icon(Icon.COPY).style(Button.Style.GHOST));
        copyIcon.tooltip("Иконка в окне речи (say). Допишите в объявление персонажа");
        copyPortrait = add(new Button("portrait \"" + image.name() + "\"", () -> copy("portrait \"" + image.name() + "\"")).icon(Icon.COPY).style(Button.Style.GHOST));
        copyPortrait.tooltip("Лицо в диалоге с выбором (choice). Допишите в объявление персонажа");
        insertCharacter = add(new Button("Персонаж с этой картинкой", () -> {
            String var = Images.slug(image.name());
            insertCode.accept("character " + var + " = \"spraute:npc\" name \"" + image.name().replace("\"", "'")
                    + "\" icon \"" + image.name() + "\" portrait \"" + image.name() + "\"");
        }).icon(Icon.USER).style(Button.Style.PRIMARY));
        for (Button b : List.of(copyIcon, copyPortrait, insertCharacter)) b.autoWidth();
    }

    private void copy(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        UI ui = UI.get();
        if (ui != null) ui.toastInfo("Скопировано: " + s);
    }

    @Override
    public String title() {
        return image.name();
    }

    @Override
    public void layout() {
        int bx = x + 12, by = y + h - 30;
        for (Button b : List.of(copyIcon, copyPortrait, insertCharacter)) {
            b.bounds(bx, by, b.prefW, 20);
            bx += b.prefW + 6;
        }
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        ProjectImages.Loaded tex = ProjectImages.load(project, image);
        int areaY = y + 34, areaH = h - 34 - 40;
        c.text(image.name(), x + 12, y + 10, Theme.TEXT);
        String info = tex == null ? "не удалось прочитать файл" : tex.width() + " × " + tex.height() + "   " + image.path().getFileName();
        c.text(info, x + 18 + c.width(image.name()), y + 10, Theme.TEXT_MUTED);
        c.fill(x, areaY - 4, w, 1, Theme.BORDER);
        if (tex == null) {
            c.textCentered("Картинка не читается. Поддерживаются PNG, JPG, BMP и GIF", x + w / 2, areaY + areaH / 2, Theme.ERROR);
            return;
        }
        // fit into the area keeping the aspect ratio, never upscale pixel art beyond 4x
        double k = Math.min(Math.min((w - 40) / (double) tex.width(), (areaH - 20) / (double) tex.height()), 4.0);
        int iw = Math.max(1, (int) (tex.width() * k)), ih = Math.max(1, (int) (tex.height() * k));
        int ix = x + (w - iw) / 2, iy = areaY + (areaH - ih) / 2;
        // checkerboard for transparency
        int cell = 8;
        c.pushClip(ix, iy, iw, ih);
        for (int yy = 0; yy < ih; yy += cell) {
            for (int xx = 0; xx < iw; xx += cell) c.fill(ix + xx, iy + yy, cell, cell, ((xx + yy) / cell) % 2 == 0 ? 0xFF2A2C38 : 0xFF22242E);
        }
        c.popClip();
        c.g.blit(tex.texture(), ix, iy, iw, ih, 0, 0, tex.width(), tex.height(), tex.width(), tex.height());
        c.outline(ix - 1, iy - 1, iw + 2, ih + 2, Theme.BORDER_STRONG);
        c.fill(x, y + h - 40, w, 40, Theme.PANEL);
        c.fill(x, y + h - 40, w, 1, Theme.BORDER);
    }

    @Override
    public Icon icon() {
        return Icon.IMAGE;
    }

    @Override
    public int iconColor() {
        return Theme.INFO;
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
