package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.Specs;
import org.sprautestudio.spraute_engine.runtime.Anims;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Checkbox;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.ListView;
import org.sprautestudio.spraute_engine.ui.widgets.PopupMenu;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Asset browser: models with a live 3D preview, the animation library played right on the
 * preview model, and game sounds. Everything can be copied or inserted into the current script.
 */
public class AssetsTab extends EditorTab {
    public enum Mode { MODELS, ANIMATIONS, SOUNDS }

    private static final int TOP = 30;

    private Mode mode = Mode.MODELS;
    private final Consumer<String> insertCode;
    private final Supplier<String> characterName;

    private final Button modelsBtn, animsBtn, soundsBtn;
    // models column
    private final TextField modelSearch = new TextField().placeholder("Модель: название или ID").icon(Icon.SEARCH);
    private boolean onlySpraute = true;
    private final Checkbox onlySt;
    private final ListView<AssetIndex.Model> modelList;
    // preview
    private final ModelView view = new ModelView();
    private final Button resetView, spin, stopAnims, copyModel, insertCharacter;
    // animations column
    private final TextField animSearch = new TextField().placeholder("Анимация: имя, файл или ID").icon(Icon.SEARCH);
    private final ListView<Anims.Entry> animList;
    private String layer = "action";
    private boolean loop;
    private float speed = 1;
    private final Dropdown<String> layerPick;
    private final Dropdown<Float> speedPick;
    private final Checkbox loopBox;
    private final Button insertAnim, copyAnim;
    // sounds
    private final TextField soundSearch = new TextField().placeholder("Поиск звука").icon(Icon.SEARCH);
    private final ListView<AssetIndex.Sound> soundList;
    private final Button playSound, insertSound, copySound;

    private String status = "";
    private int statusColor = Theme.TEXT_DIM;

    public AssetsTab(Consumer<String> insertCode, Supplier<String> characterName) {
        super(null, null);
        this.insertCode = insertCode;
        this.characterName = characterName;

        modelsBtn = add(new Button("Модели", () -> setMode(Mode.MODELS)).icon(Icon.CUBE).toggle(() -> mode == Mode.MODELS).style(Button.Style.GHOST));
        animsBtn = add(new Button("Анимации", () -> setMode(Mode.ANIMATIONS)).icon(Icon.FILM).toggle(() -> mode == Mode.ANIMATIONS).style(Button.Style.GHOST));
        soundsBtn = add(new Button("Звуки", () -> setMode(Mode.SOUNDS)).icon(Icon.NOTE).toggle(() -> mode == Mode.SOUNDS).style(Button.Style.GHOST));
        for (Button b : List.of(modelsBtn, animsBtn, soundsBtn)) b.autoWidth();

        // ------------------------------------------------------------ models
        add(modelSearch);
        modelSearch.onChange = s -> refreshModels();
        onlySt = add(new Checkbox("Только Spraute", () -> onlySpraute, v -> {
            onlySpraute = v;
            refreshModels();
        }));
        modelList = add(new ListView<>((c, m, x, y, w, h, sel, hov) -> {
            c.icon(Icon.CUBE, x + 6, y + (h - 9) / 2, m.builtin() ? Theme.ACCENT_2 : Theme.TEXT_MUTED);
            c.textEllipsized(m.title(), x + 20, y + 3, w - 26, sel ? Theme.TEXT : Theme.TEXT_DIM);
            c.textEllipsized(m.idString(), x + 20, y + 13, w - 26, Theme.TEXT_MUTED);
        }));
        modelList.rowHeight = 24;
        modelList.background = Theme.PANEL;
        modelList.emptyText = "Ничего не найдено";
        modelList.onSelect = m -> {
            view.setType(m.type());
            setStatus(m.idString(), Theme.TEXT_DIM);
        };
        modelList.onActivate = m -> insertCharacter(m);
        modelList.onContext = (m, mx, my) -> {
            PopupMenu pm = new PopupMenu();
            pm.item("Вставить персонажа в скрипт", Icon.USER, null, () -> insertCharacter(m)).color(Theme.CODE_CHARACTER);
            pm.item("Копировать id", Icon.COPY, null, () -> copy(m.idString()));
            pm.show(mx, my);
        };

        // ------------------------------------------------------------ preview
        add(view);
        resetView = add(Button.icon(Icon.REFRESH, "Сбросить вид (двойной клик по модели)", view::resetView));
        spin = add(Button.icon(Icon.PLAY, "Вращать модель", () -> view.setAutoSpin(!view.autoSpin())).toggle(view::autoSpin));
        stopAnims = add(Button.icon(Icon.STOP, "Остановить анимации на превью", () -> {
            view.stopAll();
            setStatus("Анимации остановлены", Theme.TEXT_DIM);
        }).iconColor(Theme.ERROR));
        copyModel = add(Button.icon(Icon.COPY, "Копировать id модели", () -> {
            AssetIndex.Model m = modelList.selectedItem();
            if (m != null) copy(m.idString());
        }));
        insertCharacter = add(new Button("Персонаж в скрипт", () -> {
            AssetIndex.Model m = modelList.selectedItem();
            if (m != null) insertCharacter(m);
        }).icon(Icon.USER).style(Button.Style.PRIMARY).enabledWhen(() -> modelList.selectedItem() != null));
        insertCharacter.autoWidth();

        // ------------------------------------------------------------ animations
        add(animSearch);
        animSearch.onChange = s -> refreshAnims();
        animList = add(new ListView<>((c, a, x, y, w, h, sel, hov) -> {
            boolean now = a.id().equals(view.playing());
            c.icon(now ? Icon.PLAY : Icon.FILM, x + 6, y + (h - 9) / 2, now ? Theme.SUCCESS : a.loops() ? Theme.ACCENT_2 : 0xFFFF8F5A);
            String len = String.format(Locale.ROOT, "%.1fс", a.seconds()) + (a.loops() ? " ↻" : "");
            int lw = c.width(len);
            c.textEllipsized(a.fileName() + (a.id().contains("!") ? " · " + a.key() : ""), x + 20, y + 3, w - 30 - lw, sel ? Theme.TEXT : Theme.TEXT_DIM);
            c.text(len, x + w - lw - 6, y + 3, Theme.TEXT_MUTED);
            c.textEllipsized(a.folder(), x + 20, y + 13, w - 26, Theme.TEXT_MUTED);
        }));
        animList.rowHeight = 24;
        animList.background = Theme.PANEL;
        animList.emptyText = "Анимации не найдены (войдите в мир)";
        animList.activateOnSingleClick = true;
        animList.onActivate = this::preview;
        animList.onContext = (a, mx, my) -> {
            PopupMenu pm = new PopupMenu();
            pm.item("Проиграть на модели", Icon.PLAY, null, () -> preview(a)).color(Theme.SUCCESS);
            pm.item("Вставить anim в скрипт", Icon.CODE, null, () -> insertAnim(a, false)).color(Theme.CODE_COMMAND);
            pm.item("Вставить как мимику (face)", Icon.USER, null, () -> insertAnim(a, true));
            pm.item("Копировать id", Icon.COPY, null, () -> copy(a.id()));
            pm.show(mx, my);
        };
        layerPick = add(new Dropdown<>(() -> Specs.LAYERS, () -> layer, l -> "слой: " + l, l -> {
            layer = l;
            Anims.Entry a = animList.selectedItem();
            if (a != null) preview(a);
        }));
        layerPick.tooltip("Слой анимации. action — всё тело; emotion — лицо; 0–20 — накладываются поверх");
        speedPick = add(new Dropdown<>(() -> List.of(0.25f, 0.5f, 1f, 1.5f, 2f), () -> speed, s -> "×" + trim(s), s -> {
            speed = s;
            Anims.Entry a = animList.selectedItem();
            if (a != null) preview(a);
        }));
        speedPick.tooltip("Скорость проигрывания");
        loopBox = add(new Checkbox("Цикл", () -> loop, v -> {
            loop = v;
            Anims.Entry a = animList.selectedItem();
            if (a != null) preview(a);
        }));
        insertAnim = add(new Button("anim в скрипт", () -> {
            Anims.Entry a = animList.selectedItem();
            if (a != null) insertAnim(a, false);
        }).icon(Icon.CODE).style(Button.Style.PRIMARY).enabledWhen(() -> animList.selectedItem() != null));
        insertAnim.autoWidth();
        copyAnim = add(Button.icon(Icon.COPY, "Копировать id анимации", () -> {
            Anims.Entry a = animList.selectedItem();
            if (a != null) copy(a.id());
        }));

        // ------------------------------------------------------------ sounds
        add(soundSearch);
        soundSearch.onChange = s -> refreshSounds();
        soundList = add(new ListView<>((c, s, x, y, w, h, sel, hov) -> {
            boolean st = s.id().getNamespace().equals(org.sprautestudio.spraute_engine.SprauteEngine.MODID);
            c.icon(Icon.NOTE, x + 6, y + (h - 9) / 2, st ? Theme.ACCENT_2 : Theme.TEXT_MUTED);
            c.textEllipsized(s.id().toString(), x + 20, y + (h - 8) / 2, w - 26, sel ? Theme.TEXT : Theme.TEXT_DIM);
        }));
        soundList.rowHeight = 16;
        soundList.background = Theme.PANEL;
        soundList.activateOnSingleClick = true;
        soundList.onActivate = this::playSound;
        soundList.onContext = (s, mx, my) -> {
            PopupMenu pm = new PopupMenu();
            pm.item("Прослушать", Icon.PLAY, null, () -> playSound(s)).color(Theme.SUCCESS);
            pm.item("Вставить sound в скрипт", Icon.CODE, null, () -> insertCode.accept("sound \"" + s.id() + "\"")).color(Theme.CODE_COMMAND);
            pm.item("Копировать id", Icon.COPY, null, () -> copy(s.id().toString()));
            pm.show(mx, my);
        };
        playSound = add(new Button("Прослушать", () -> {
            AssetIndex.Sound s = soundList.selectedItem();
            if (s != null) playSound(s);
        }).icon(Icon.PLAY).style(Button.Style.SUCCESS).enabledWhen(() -> soundList.selectedItem() != null));
        playSound.autoWidth();
        insertSound = add(new Button("sound в скрипт", () -> {
            AssetIndex.Sound s = soundList.selectedItem();
            if (s != null) insertCode.accept("sound \"" + s.id() + "\"");
        }).icon(Icon.CODE).style(Button.Style.PRIMARY).enabledWhen(() -> soundList.selectedItem() != null));
        insertSound.autoWidth();
        copySound = add(Button.icon(Icon.COPY, "Копировать id звука", () -> {
            AssetIndex.Sound s = soundList.selectedItem();
            if (s != null) copy(s.id().toString());
        }));

        refreshModels();
        refreshAnims();
        refreshSounds();
        AssetIndex.Model def = AssetIndex.defaultPreviewModel();
        if (def != null) {
            int i = modelList.items().indexOf(def);
            if (i >= 0) modelList.select(i);
            view.setType(def.type());
        }
    }

    private static String trim(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }

    public void setMode(Mode m) {
        mode = m;
        layout();
        focusContent();
    }

    public Mode mode() {
        return mode;
    }

    private void refreshModels() {
        String q = modelSearch.text();
        List<AssetIndex.Model> list = new ArrayList<>();
        for (AssetIndex.Model m : AssetIndex.models()) {
            if (onlySpraute && !m.builtin() && q.isBlank()) continue;
            if (AssetIndex.matches(q, m.idString(), m.title())) list.add(m);
        }
        AssetIndex.Model sel = modelList.selectedItem();
        modelList.setItems(list);
        if (sel != null && list.contains(sel)) modelList.select(list.indexOf(sel));
    }

    private void refreshAnims() {
        String q = animSearch.text();
        List<Anims.Entry> list = new ArrayList<>();
        for (Anims.Entry a : AssetIndex.animations()) if (AssetIndex.matches(q, a.id(), a.key(), a.fileName(), a.folder())) list.add(a);
        animList.setItems(list);
    }

    private void refreshSounds() {
        String q = soundSearch.text();
        List<AssetIndex.Sound> list = new ArrayList<>();
        for (AssetIndex.Sound s : AssetIndex.sounds()) if (AssetIndex.matches(q, s.id().toString())) list.add(s);
        soundList.setItems(list);
    }

    private void preview(Anims.Entry a) {
        String err = view.play(a.id(), layer, loop || a.loops(), speed);
        if (err != null) setStatus(err, Theme.WARNING);
        else setStatus("▶ " + a.id() + "   (" + String.format(Locale.ROOT, "%.1f", a.seconds()) + " с, слой " + layer + ")", Theme.SUCCESS);
    }

    private void playSound(AssetIndex.Sound s) {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(s.id()), 1f));
            setStatus("♪ " + s.id(), Theme.SUCCESS);
        } catch (RuntimeException e) {
            setStatus("Не удалось проиграть: " + e.getMessage(), Theme.WARNING);
        }
    }

    private void insertCharacter(AssetIndex.Model m) {
        String var = m.id().getPath().replaceAll("[^a-zA-Z0-9_]", "_");
        if (var.isEmpty() || Character.isDigit(var.charAt(0))) var = "npc_" + var;
        insertCode.accept("character " + var + " = \"" + m.idString() + "\" name \"" + m.title().replace("\"", "'") + "\"");
    }

    private void insertAnim(Anims.Entry a, boolean face) {
        String who = characterName.get();
        if (face) insertCode.accept("face " + who + " \"" + a.id() + "\"");
        else {
            StringBuilder sb = new StringBuilder("anim ").append(who).append(" \"").append(a.id()).append('"');
            if (!layer.equals("action")) sb.append(" layer ").append(layer);
            if (speed != 1) sb.append(" speed ").append(trim(speed));
            if (loop) sb.append(" loop");
            else if (!a.loops()) sb.append(" wait");
            insertCode.accept(sb.toString());
        }
    }

    private void copy(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
        UI ui = UI.get();
        if (ui != null) ui.toastInfo("Скопировано: " + s);
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    // ================================================================ layout

    @Override
    public void layout() {
        int bx = x + 10;
        for (Button b : List.of(modelsBtn, animsBtn, soundsBtn)) {
            b.bounds(bx, y + 6, b.prefW, 18);
            bx += b.prefW + 4;
        }
        boolean models = mode != Mode.SOUNDS;
        for (var wdg : List.of(modelSearch, onlySt, modelList, view, resetView, spin, stopAnims, copyModel, insertCharacter,
                animSearch, animList, layerPick, speedPick, loopBox, insertAnim, copyAnim)) wdg.visible = models;
        for (var wdg : List.of(soundSearch, soundList, playSound, insertSound, copySound)) wdg.visible = !models;
        int top = y + TOP, bottom = y + h - 18;
        if (models) {
            // the animation mode gives the library more room
            int leftW = mode == Mode.ANIMATIONS ? Math.max(150, w / 5) : Math.max(170, w * 27 / 100);
            int rightW = mode == Mode.ANIMATIONS ? Math.max(230, w * 36 / 100) : Math.max(200, w * 28 / 100);
            if (leftW + rightW > w - 160) {
                leftW = (w - 160) * 45 / 100;
                rightW = (w - 160) * 55 / 100;
            }
            int lx = x, rx = x + w - rightW, cx = x + leftW + 1, cw = w - leftW - rightW - 2;
            modelSearch.bounds(lx + 6, top + 4, leftW - 12, 18);
            onlySt.bounds(lx + 6, top + 26, leftW - 12, 14);
            modelList.bounds(lx, top + 44, leftW, bottom - top - 44);

            view.bounds(cx, top, cw, bottom - top - 26);
            int ty = bottom - 23;
            resetView.bounds(cx + 6, ty, 20, 20);
            spin.bounds(cx + 28, ty, 20, 20);
            stopAnims.bounds(cx + 50, ty, 20, 20);
            copyModel.bounds(cx + 72, ty, 20, 20);
            insertCharacter.bounds(cx + cw - insertCharacter.prefW - 6, ty, insertCharacter.prefW, 20);

            animSearch.bounds(rx + 6, top + 4, rightW - 12, 18);
            int ry = top + 26;
            int third = (rightW - 18) / 3;
            layerPick.bounds(rx + 6, ry, third + 14, 16);
            speedPick.bounds(rx + 6 + third + 17, ry, third - 10, 16);
            loopBox.bounds(rx + 6 + 2 * third + 12, ry + 1, third, 14);
            animList.bounds(rx, ry + 20, rightW, bottom - ry - 20 - 26);
            copyAnim.bounds(rx + 6, ty, 20, 20);
            insertAnim.bounds(rx + rightW - insertAnim.prefW - 6, ty, insertAnim.prefW, 20);
        } else {
            soundSearch.bounds(x + 10, top + 4, Math.min(360, w - 20), 18);
            soundList.bounds(x, top + 26, w, bottom - top - 26 - 26);
            int ty = bottom - 23;
            playSound.bounds(x + 10, ty, playSound.prefW, 20);
            insertSound.bounds(x + 14 + playSound.prefW, ty, insertSound.prefW, 20);
            copySound.bounds(x + 18 + playSound.prefW + insertSound.prefW, ty, 20, 20);
        }
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y + TOP - 1, w, 1, Theme.BORDER);
        String counts = mode == Mode.SOUNDS ? AssetIndex.sounds().size() + " звуков"
                : AssetIndex.models().size() + " моделей · " + AssetIndex.animations().size() + " анимаций";
        c.text(counts, x + w - c.width(counts) - 10, y + 11, Theme.TEXT_MUTED);
        if (mode != Mode.SOUNDS) {
            int leftR = modelList.x + modelList.w, rightL = animList.x;
            c.fill(leftR, y + TOP, 1, h - TOP - 18, Theme.BORDER);
            c.fill(rightL - 1, y + TOP, 1, h - TOP - 18, Theme.BORDER);
            c.fill(modelList.x, view.y + view.h, modelList.w, 1, 0);
            c.fill(view.x, view.y + view.h, view.w, 26, Theme.PANEL);
            c.fill(animList.x, animList.y + animList.h, animList.w, 26, Theme.PANEL);
            c.fill(modelList.x, y + TOP, modelList.w, 44, Theme.PANEL);
            c.fill(animList.x, y + TOP, animList.w, animList.y - y - TOP, Theme.PANEL);
        } else {
            c.fill(x, soundList.y + soundList.h, w, 26, Theme.PANEL);
        }
    }

    @Override
    protected void paintOver(Canvas c, int mx, int my, float dt) {
        if (mode != Mode.SOUNDS) {
            c.pushClip(view.x, view.y, view.w, view.h);
            AssetIndex.Model m = modelList.selectedItem();
            if (m != null) {
                c.text(m.title(), view.x + 10, view.y + 8, Theme.TEXT);
                c.text(m.idString(), view.x + 10, view.y + 20, Theme.TEXT_MUTED);
                if (created(m)) c.text(view.isAnimated() ? "анимации поддерживаются" : "без анимаций анимаций",
                        view.x + 10, view.y + 32, view.isAnimated() ? Theme.ACCENT_2 : Theme.TEXT_MUTED);
            }
            String hint = "ЛКМ — вращать, колесо — масштаб";
            if (view.w > c.width(hint) + c.width(m != null ? m.title() : "") + 40) c.text(hint, view.x + view.w - c.width(hint) - 8, view.y + 8, 0x66FFFFFF);
            c.popClip();
        }
        // status line
        c.fill(x, y + h - 18, w, 18, Theme.BG_DEEP);
        c.fill(x, y + h - 18, w, 1, Theme.BORDER);
        c.textEllipsized(status, x + 8, y + h - 13, w - 16, statusColor);
    }

    private boolean created(AssetIndex.Model m) {
        return view.type() == m.type() && Minecraft.getInstance().level != null;
    }

    @Override
    public String title() {
        return "Ассеты";
    }

    @Override
    public Icon icon() {
        return Icon.CUBE;
    }

    @Override
    public int iconColor() {
        return 0xFFFF8F5A;
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
        if (mode == Mode.SOUNDS) soundSearch.requestFocus();
        else if (mode == Mode.ANIMATIONS) animSearch.requestFocus();
        else modelSearch.requestFocus();
    }
}
