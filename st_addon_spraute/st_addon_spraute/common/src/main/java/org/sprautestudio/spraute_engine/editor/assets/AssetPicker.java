package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.sprautestudio.spraute_engine.lang.ParamType;
import org.sprautestudio.spraute_engine.runtime.Anims;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
import org.sprautestudio.spraute_engine.ui.widgets.ListView;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Modal picker for an animation, a model (entity type) or a sound, with a live preview. */
public final class AssetPicker {
    private AssetPicker() {}

    private record Item(String id, String title, String detail, Object data) {}

    /** One of a project model's own animations (played by its name on that model). */
    private record OwnAnim(String key, String name) {}

    /**
     * The character an animation is being picked for ("spraute:npc", "spraute:Робот"; null — none):
     * the preview shows it. Set just before {@link #open}; used once.
     */
    public static String previewEntity;

    public static void open(ParamType kind, String current, Consumer<String> onPick, String entity) {
        previewEntity = entity;
        open(kind, current, onPick);
    }

    public static void open(ParamType kind, String current, Consumer<String> onPick) {
        String forEntity = previewEntity;
        previewEntity = null;
        UI ui = UI.get();
        if (ui == null) return;
        if (kind == ParamType.ITEM) {
            ItemPicker.open(current, onPick);
            return;
        }
        String title = switch (kind) {
            case ANIM -> "Выбор анимации";
            case ENTITY -> "Выбор модели";
            case ITEM -> "Выбор предмета";
            default -> "Выбор звука";
        };
        Icon icon = kind == ParamType.ANIM ? Icon.FILM : kind == ParamType.ENTITY || kind == ParamType.ITEM ? Icon.CUBE : Icon.NOTE;
        Dialog d = new Dialog(title, icon).width(Math.min(680, ui.vw - 30));
        Body body = new Body(kind, current, forEntity, id -> {
            d.close();
            onPick.accept(id);
        });
        body.prefH = Math.min(340, ui.vh - 120);
        d.body.add(body);
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        d.addButton("Выбрать", Button.Style.PRIMARY, () -> {
            Item it = body.list.selectedItem();
            if (it != null) {
                d.close();
                onPick.accept(it.id);
            }
        });
        d.onEnter = () -> {
            Item it = body.list.selectedItem();
            if (it != null) {
                d.close();
                onPick.accept(it.id);
            }
        };
        d.open();
        body.search.requestFocus();
    }

    private static final class Body extends Widget {
        final ParamType kind;
        final TextField search = new TextField().placeholder("Поиск").icon(Icon.SEARCH);
        final ListView<Item> list;
        final ModelView view;
        final List<Item> all = new ArrayList<>();
        /** What the preview shows (for the caption), and a project model's key when it is one. */
        String shown = "", modelKey = "";

        Body(ParamType kind, String current, String entity, Consumer<String> choose) {
            this.kind = kind;
            add(search);
            list = add(new ListView<>((c, it, x, y, w, h, sel, hov) -> {
                c.textEllipsized(it.title, x + 8, y + 3, w - 14, sel ? Theme.TEXT : Theme.TEXT_DIM);
                c.textEllipsized(it.detail, x + 8, y + 13, w - 14, Theme.TEXT_MUTED);
            }));
            list.rowHeight = 24;
            list.background = Theme.INPUT;
            list.onActivate = it -> choose.accept(it.id);
            view = kind == ParamType.SOUND || kind == ParamType.ITEM ? null : add(new ModelView());
            switch (kind) {
                case ANIM -> {
                    // the character's own model: its animations first, by their names
                    if (entity != null && !entity.isBlank()) showEntity(entity.replace("\"", "").trim());
                    if (!modelKey.isEmpty()) {
                        var look = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.look(modelKey);
                        if (look != null && look.model() != null)
                            for (String n : org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.animations(look.model()))
                                all.add(new Item(n, n + "  · анимация этой модели", shown, new OwnAnim(modelKey, n)));
                    }
                    for (Anims.Entry a : AssetIndex.animations()) {
                        all.add(new Item(a.id(), a.fileName() + (a.id().contains("!") ? " · " + a.key() : ""),
                                a.folder() + String.format(Locale.ROOT, " · %.1f с", a.seconds()) + (a.loops() ? " ↻" : ""), a));
                    }
                    if (shown.isEmpty()) {
                        AssetIndex.Model m = AssetIndex.defaultPreviewModel();
                        if (m != null) view.setType(m.type());
                    }
                    view.emptyText = "Нет модели для превью";
                }
                case ENTITY -> {
                    // the project's own models first
                    for (var t : org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.all())
                        all.add(new Item("spraute:" + t.name(), t.name() + "  · сущность проекта", "spraute:" + t.name(), t));
                    for (AssetIndex.Model m : AssetIndex.models()) all.add(new Item(m.idString(), m.title(), m.idString(), m));
                }
                case ITEM -> {
                    for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).toString();
                        String title;
                        try {
                            title = it.getDescription().getString();
                        } catch (RuntimeException e) {
                            title = id;
                        }
                        all.add(new Item(id, title, id, it));
                    }
                }
                default -> {
                    for (AssetIndex.Sound s : AssetIndex.sounds()) all.add(new Item(s.id().toString(), s.id().getPath(), s.id().getNamespace(), s));
                }
            }
            list.onSelect = this::preview;
            search.onChange = s -> refresh();
            refresh();
            String cur = current == null ? "" : current.replace("\"", "").trim();
            if (!cur.isEmpty()) {
                List<Item> items = list.items();
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id.equals(cur)) {
                        list.select(i);
                        list.ensureVisible(i);
                        preview(items.get(i));
                        break;
                    }
                }
            }
        }

        void refresh() {
            String q = search.text();
            List<Item> out = new ArrayList<>();
            for (Item it : all) if (AssetIndex.matches(q, it.id, it.title)) out.add(it);
            list.setItems(out);
        }

        /** The preview shows this character's model (a game entity or a project one). */
        void showEntity(String id) {
            if (view == null) return;
            if (id.startsWith("spraute:")) {
                String n = id.substring(8);
                if (n.equals("npc")) {
                    modelKey = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.BUILTIN;
                    shown = id;
                    view.setType(org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get());
                    return;
                }
                for (var t : org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.all()) {
                    if (!t.name().equalsIgnoreCase(n)) continue;
                    String key = t.key();
                    modelKey = key;
                    shown = id;
                    view.setType(org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get(), e -> {
                        if (e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me) me.setModel(key);
                    });
                    return;
                }
                return;
            }
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "spraute:" + id);
            var t = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (t != null) {
                view.setType(t);
                shown = rl.toString();
            }
        }

        void preview(Item it) {
            if (it == null) return;
            if (it.data instanceof OwnAnim oa && view != null) {
                var look = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.look(oa.key());
                var a = look != null ? look.animation(oa.name()) : java.util.Optional.<org.sprautestudio.spraute_engine.core.anim.Animation>empty();
                a.ifPresent(an -> view.play(an.configure(), oa.name()));
            } else if (it.data instanceof Anims.Entry a && view != null) view.play(a.id(), "action", true, 1f);
            else if (it.data instanceof AssetIndex.Model m && view != null) view.setType(m.type());
            else if (it.data instanceof org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.Type t && view != null)
                view.setType(org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get(), e -> {
                    if (e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me) me.setModel(t.key());
                });
            else if (it.data instanceof AssetIndex.Sound s) {
                try {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(s.id()), 1f));
                } catch (RuntimeException ignored) {
                }
            }
        }

        @Override
        public void layout() {
            int listW = view == null ? w : w * 52 / 100;
            search.bounds(x, y, listW, 18);
            list.bounds(x, y + 22, listW, h - 22);
            if (view != null) view.bounds(x + listW + 8, y, w - listW - 8, h);
            super.layout();
        }

        @Override
        protected void paintOver(Canvas c, int mx, int my, float dt) {
            c.outline(list.x, list.y, list.w, list.h, Theme.BORDER);
            if (view != null) c.outline(view.x, view.y, view.w, view.h, Theme.BORDER);
            if (view != null && kind == ParamType.ANIM && !shown.isEmpty())
                c.textEllipsized("на модели: " + shown, view.x + 6, view.y + 5, view.w - 12, Theme.TEXT_MUTED);
        }
    }
}
