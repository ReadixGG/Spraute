package org.sprautestudio.st_addon_spraute.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes every element type of a Spraute GUI and all of its properties. The schema drives the
 * editor inspector, default values, the runtime renderer and the {@code gui set} command.
 */
public final class GuiSchema {
    private GuiSchema() {}

    public enum Kind { INT, NUMBER, TEXT, COLOR, BOOL, CHOICE, IMAGE, ENTITY, ITEM, ANIM, SOUND }

    public record Prop(String key, String label, Kind kind, String def, List<String> choices, String group, String doc) {}

    public record Type(String key, String title, String doc, List<Prop> props) {
        public Prop prop(String key) {
            for (Prop p : props) if (p.key.equals(key)) return p;
            return null;
        }
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();
    private static final List<Prop> DOC_PROPS = new ArrayList<>();

    public static final List<String> ANCHORS = List.of("top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right");
    public static final List<String> APPEAR = List.of("none", "fade", "slide_up", "slide_down", "slide_left", "slide_right", "zoom", "pop");

    // ------------------------------------------------------------------ builder

    private static final class B {
        final List<Prop> props = new ArrayList<>();
        String group = "Основное";

        B group(String g) {
            group = g;
            return this;
        }

        B p(String key, String label, Kind kind, String def, String doc) {
            props.add(new Prop(key, label, kind, def, List.of(), group, doc));
            return this;
        }

        B c(String key, String label, String def, List<String> choices, String doc) {
            props.add(new Prop(key, label, Kind.CHOICE, def, choices, group, doc));
            return this;
        }
    }

    private static B common(int w, int h) {
        return common(w, h, "center");
    }

    private static B common(int w, int h, String anchor) {
        B b = new B().group("Положение")
                .c("anchor", "привязка", anchor, ANCHORS, "Точка экрана, от которой отсчитываются X и Y (и сторона элемента, которой он к ней прижат)")
                .p("x", "X", Kind.INT, "0", "Сдвиг по горизонтали от точки привязки. Можно в процентах ширины родителя: 10%")
                .p("y", "Y", Kind.INT, "0", "Сдвиг по вертикали от точки привязки. Можно в процентах: -5%")
                .p("w", "ширина", Kind.INT, String.valueOf(w), "Ширина в пикселях макета или в процентах родителя: 50%")
                .p("h", "высота", Kind.INT, String.valueOf(h), "Высота в пикселях макета или в процентах родителя: 100%")
                .c("stretch", "до краёв экрана", "none", List.of("none", "width", "height", "both"),
                        "width — на всю ширину экрана, height — на всю высоту, both — на весь экран. Тянется и за рамку макета, "
                                + "когда экран шире или выше него")
                .p("rotation", "поворот°", Kind.NUMBER, "0", "Поворот вокруг центра, в градусах")
                .group("Вид")
                .p("visible", "видимый", Kind.BOOL, "true", "Скрытые элементы можно показать командой gui show")
                .p("opacity", "непрозрачность", Kind.NUMBER, "1", "0 — полностью прозрачный, 1 — непрозрачный")
                .p("tooltip", "подсказка", Kind.TEXT, "", "Текст при наведении мыши")
                .p("hoverScale", "масштаб при наведении", Kind.NUMBER, "1", "Например 1.05 — элемент чуть увеличивается под мышью")
                .group("Появление")
                .c("appear", "анимация", "none", APPEAR, "Как элемент появляется при открытии интерфейса")
                .p("appearDelay", "задержка, с", Kind.NUMBER, "0", "Через сколько секунд после открытия начинается появление")
                .p("appearTime", "длительность, с", Kind.NUMBER, "0.3", "Длительность анимации появления");
        return b;
    }

    /** Types made for the engine's windows (HUD parts, answers, speaker…): only the engine settings offer them. */
    public static boolean engineOnly(String type) {
        return type != null && (type.startsWith("hud_") || type.startsWith("journal_") || type.startsWith("shop_") || type.equals("choice_answers")
                || type.equals("speaker") || type.equals("quest_icon"));
    }

    /** What a story interface can be built of. */
    public static List<Type> uiTypes() {
        List<Type> out = new ArrayList<>();
        for (Type t : TYPES.values()) if (!engineOnly(t.key())) out.add(t);
        return out;
    }

    /** What an engine window can be built of: its own live parts first, then plain decoration. */
    public static List<Type> windowTypes(String role) {
        List<Type> out = new ArrayList<>();
        for (Type t : TYPES.values()) {
            String k = t.key();
            boolean own = switch (role) {
                case "hud" -> k.startsWith("hud_");
                case "choice" -> k.equals("choice_answers") || k.equals("speaker");
                case "toast" -> k.equals("quest_icon");
                case "quest" -> k.startsWith("journal_");
                case "shop" -> k.startsWith("shop_");
                default -> false;
            };
            if (own) out.add(t);
        }
        for (String k : List.of("group", "panel", "image", "text", "progress", "item", "clip")) {
            Type t = TYPES.get(k);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** The HUD parts: the engine draws them with the player's state; their look is the same as in the engine settings. */
    public static boolean isHud(String type) {
        return type != null && type.startsWith("hud_");
    }

    private static void hud(String key, String title, String doc, int w, int h, int x, int y) {
        String anchor = key.equals("coins") ? "top_right" : "bottom";
        B b = common(w, h, anchor);
        // the part's usual place: the editor's add button puts it there
        List<Prop> ps = b.props;
        for (int i = 0; i < ps.size(); i++) {
            Prop p = ps.get(i);
            if (p.key().equals("x")) ps.set(i, new Prop("x", p.label(), p.kind(), String.valueOf(key.equals("coins") ? -6 : x), p.choices(), p.group(), p.doc()));
            if (p.key().equals("y")) ps.set(i, new Prop("y", p.label(), p.kind(), String.valueOf(key.equals("coins") ? 6 : y), p.choices(), p.group(), p.doc()));
        }
        b.group("Часть HUD")
                .c("style", "стиль", "default", org.sprautestudio.st_addon_spraute.engine.EngineConfig.styles(key),
                        "default — как в игре (вписывается в рамку элемента); bar — полоска; icons — ряд своих иконок; "
                                + "classic — хотбар Spraute; slots — свои ячейки; icon — значок и число")
                .p("count", "иконок", Kind.INT, "10", "Сколько иконок в ряду (стиль icons)")
                .p("pad", "отступ заливки", Kind.INT, "1", "Отступ заливки полоски от краёв (стиль bar)")
                .c("direction", "куда заполняется", "right", List.of("right", "left", "up", "down"),
                        "Стиль bar: right — вправо, left — влево, up — снизу вверх, down — сверху вниз")
                .p("gap", "промежуток", Kind.INT, "0", "Между иконками или ячейками")
                .p("reverse", "справа налево", Kind.BOOL, key.equals("food") || key.equals("air") ? "true" : "false", "Иконки заполняются справа")
                .p("showText", "число", Kind.BOOL, "false", "Подписать значение (стиль bar)")
                .p("textColor", "цвет числа", Kind.COLOR, "#FFFFFFFF", "")
                .group("Текстуры")
                .p("back", "фон", Kind.IMAGE, "", "bar — фон полоски; slots — ячейка; icon — подложка")
                .p("fill", "заливка", Kind.IMAGE, "", "bar — заливка; slots — закрытая ячейка; icon — значок")
                .p("frame", "рамка", Kind.IMAGE, "", "bar — рамка поверх; slots — выбранная ячейка")
                .p("full", "иконка: полная", Kind.IMAGE, "", "Стиль icons")
                .p("half", "иконка: половина", Kind.IMAGE, "", "Стиль icons")
                .p("empty", "иконка: пустая", Kind.IMAGE, "", "Стиль icons");
        type("hud_" + key, title, doc, b);
    }

    private static void type(String key, String title, String doc, B b) {
        TYPES.put(key, new Type(key, title, doc, Collections.unmodifiableList(b.props)));
    }

    static {
        DOC_PROPS.add(new Prop("title", "название", Kind.TEXT, "Интерфейс", List.of(), "Интерфейс", "Имя для редактора"));
        DOC_PROPS.add(new Prop("width", "ширина макета", Kind.INT, "384", List.of(), "Интерфейс", "Размер области, в которой расставлены элементы"));
        DOC_PROPS.add(new Prop("height", "высота макета", Kind.INT, "216", List.of(), "Интерфейс", "Размер области, в которой расставлены элементы"));
        DOC_PROPS.add(new Prop("layout", "масштаб", Kind.CHOICE, "fit", List.of("fit", "screen"), "Интерфейс",
                "fit — макет масштабируется под окно целиком; screen — элементы прижимаются к краям экрана, размер в пикселях интерфейса"));
        DOC_PROPS.add(new Prop("background", "затемнение фона", Kind.COLOR, "#66000000", List.of(), "Интерфейс", "Цвет поверх мира под интерфейсом"));
        DOC_PROPS.add(new Prop("closeOnEsc", "закрывать по Esc", Kind.BOOL, "true", List.of(), "Поведение", "Можно ли закрыть интерфейс клавишей Esc"));
        DOC_PROPS.add(new Prop("pause", "пауза игры", Kind.BOOL, "false", List.of(), "Поведение", "Ставить ли одиночную игру на паузу, пока открыт интерфейс"));
        DOC_PROPS.add(new Prop("openSound", "звук открытия", Kind.SOUND, "", List.of(), "Поведение", "Звук при открытии"));

        // ---------------------------------------------------------------- the player's HUD: the engine's own parts
        hud("health", "HUD · Здоровье", "Здоровье игрока: сердечки, круглая шкала Spraute (если включена система здоровья), полоска или свои иконки",
                81, 9, -50, -30);
        hud("food", "HUD · Еда", "Сытость игрока", 81, 9, 50, -30);
        hud("armor", "HUD · Броня", "Броня игрока (видна, когда она есть)", 81, 9, -50, -40);
        hud("air", "HUD · Воздух", "Воздух под водой (виден, только пока кончается)", 81, 9, 50, -40);
        hud("exp", "HUD · Опыт", "Полоса опыта и уровень", 182, 5, 0, -24);
        hud("hotbar", "HUD · Хотбар", "Ячейки быстрого доступа", 182, 22, 0, 0);
        hud("coins", "HUD · Монеты", "Монеты Spraute (видны, когда система монет включена)", 56, 18, 0, 0);
        type("hud_quests", "HUD · Цели квеста", "Отслеживаемый квест и его задачи (то, что было справа на экране)",
                common(150, 60, "top_right").group("Цели")
                        .p("titleColor", "цвет названия", Kind.COLOR, "#FFFFD25A", "")
                        .p("textColor", "цвет задач", Kind.COLOR, "#FFFFFFFF", "")
                        .p("doneColor", "выполненные", Kind.COLOR, "#FF7BE08A", "")
                        .p("optionalColor", "необязательные", Kind.COLOR, "#FFB8C0CC", "")
                        .p("textScale", "размер задач", Kind.NUMBER, "0.8", "")
                        .p("maxTasks", "сколько задач", Kind.INT, "5", "")
                        .c("align", "выравнивание", "right", List.of("left", "center", "right"), "")
                        .p("shadow", "тень", Kind.BOOL, "true", ""));

        // ---------------------------------------------------------------- the engine windows' live parts
        type("choice_answers", "Окно · Варианты ответа", "Варианты меню выбора, столбиком сверху вниз: наведение мышью, клик, "
                        + "клавиши 1–9 и стрелки. Высота каждого — по тексту",
                common(160, 120, "top_left").group("Варианты")
                        .c("style", "стиль", "classic", List.of("classic", "flat", "image"),
                                "classic — кнопки Spraute; flat — цветные плашки; image — свои картинки")
                        .p("gap", "промежуток", Kind.INT, "4", "")
                        .p("minHeight", "мин. высота", Kind.INT, "18", "")
                        .p("padding", "отступ текста", Kind.INT, "6", "")
                        .p("textScale", "размер текста", Kind.NUMBER, "0.75", "")
                        .p("numbers", "номера", Kind.BOOL, "false", "Подписывать «1.», «2.» перед вариантами")
                        .group("Цвета")
                        .p("textColor", "текст", Kind.COLOR, "#FF42C7FF", "")
                        .p("hoverTextColor", "текст под мышью", Kind.COLOR, "#FFFFFFFF", "")
                        .p("color", "фон (flat)", Kind.COLOR, "#C0101420", "")
                        .p("hoverColor", "фон под мышью (flat)", Kind.COLOR, "#E0203048", "")
                        .p("borderColor", "рамка (flat)", Kind.COLOR, "#FF2E4058", "")
                        .p("hoverBorderColor", "рамка под мышью (flat)", Kind.COLOR, "#FF42C7FF", "")
                        .p("radius", "скругление (flat)", Kind.INT, "3", "")
                        .group("Картинки (image)")
                        .p("image", "кнопка", Kind.IMAGE, "", "")
                        .p("hoverImage", "кнопка под мышью", Kind.IMAGE, "", ""));
        type("speaker", "Окно · Говорящий", "Тот, кто задаёт вопрос (или игрок — в настройках меню выбора): во весь рост или голова",
                common(98, 98, "top_left").group("Говорящий")
                        .c("framing", "кадр", "body", List.of("body", "head"), "body — во весь рост, смотрит на мышь; head — только голова")
                        .p("scale", "масштаб", Kind.NUMBER, "1", "")
                        .p("background", "фон", Kind.COLOR, "#00000000", ""));
        type("quest_icon", "Окно · Значок квеста", "Значок уведомления квеста: новый, выполнен, провален — сам по событию",
                common(21, 23, "top_left").group("Значок")
                        .p("image", "свой лист", Kind.IMAGE, "", "Лист 63×46 как у Spraute (значки в ряд); пусто — его"));

        // ---------------------------------------------------------------- the quest journal's live parts
        type("journal_tabs", "Журнал · Вкладки", "Три вкладки: текущие, выполненные, проваленные. Квадраты в ряд по высоте элемента",
                common(75, 23, "top_left").group("Вкладки")
                        .p("gap", "промежуток", Kind.INT, "3", "")
                        .group("Текстуры")
                        .p("current", "текущие", Kind.IMAGE, "", "Лист 23×69: обычная, под мышью, выбранная — сверху вниз")
                        .p("done", "выполненные", Kind.IMAGE, "", "Лист 23×69")
                        .p("failed", "проваленные", Kind.IMAGE, "", "Лист 23×69"));
        type("journal_list", "Журнал · Список квестов", "Квесты открытой вкладки: кнопки столбиком, колёсико прокручивает, клик выбирает",
                common(136, 195, "top_left").group("Список")
                        .p("entryHeight", "высота строки", Kind.INT, "35", "")
                        .p("gap", "промежуток", Kind.INT, "2", "")
                        .p("padding", "отступ", Kind.INT, "2", "")
                        .p("icons", "значки", Kind.BOOL, "true", "Значок вкладки слева в строке")
                        .p("scrollbar", "полоса прокрутки", Kind.BOOL, "false", "")
                        .group("Цвета")
                        .p("textColor", "название", Kind.COLOR, "#FFFFFFFF", "")
                        .p("mutedColor", "подпись", Kind.COLOR, "#FF9AA4B4", "")
                        .p("accentColor", "отслеживаемый", Kind.COLOR, "#FFFFD25A", "")
                        .group("Текстуры")
                        .p("button", "строка", Kind.IMAGE, "", "")
                        .p("buttonHover", "строка под мышью", Kind.IMAGE, "", "")
                        .p("buttonOn", "выбранная строка", Kind.IMAGE, "", "")
                        .p("iconSheet", "значки", Kind.IMAGE, "", "Лист 63×46 как у Spraute"));
        type("journal_title", "Журнал · Заголовок квеста", "Название выбранного квеста (клик — отслеживать), кто выдал и «★ отслеживается»",
                common(222, 30, "top_left").group("Заголовок")
                        .p("trackHint", "«отслеживать»", Kind.BOOL, "true", "Подсказка над названием")
                        .p("giver", "кто выдал", Kind.BOOL, "true", "")
                        .group("Цвета")
                        .p("textColor", "название", Kind.COLOR, "#FFFFFFFF", "")
                        .p("accentColor", "отслеживается", Kind.COLOR, "#FFFFD25A", "")
                        .p("hoverColor", "под мышью", Kind.COLOR, "#FF6EC3FF", "")
                        .p("mutedColor", "кто выдал", Kind.COLOR, "#FF9AA4B4", ""));
        type("journal_page", "Журнал · Страница квеста", "Описание, задачи, награда и свои разделы квеста. Колёсико прокручивает",
                common(232, 163, "top_left").group("Страница")
                        .p("textScale", "размер текста", Kind.NUMBER, "0.7", "")
                        .group("Цвета")
                        .p("textColor", "задачи", Kind.COLOR, "#FFFFFFFF", "")
                        .p("bodyColor", "описание", Kind.COLOR, "#FFD8DEE8", "")
                        .p("headerColor", "заголовки", Kind.COLOR, "#FF6EC3FF", "")
                        .p("accentColor", "прогресс, награда", Kind.COLOR, "#FFFFD25A", "")
                        .p("rewardColor", "текст награды", Kind.COLOR, "#FFFFE7A3", "")
                        .p("doneColor", "выполнено", Kind.COLOR, "#FF7BE08A", "")
                        .p("failColor", "провалено", Kind.COLOR, "#FFFF6B6B", "")
                        .group("Текстуры")
                        .p("taskLine", "полоса задачи", Kind.IMAGE, "", ""));
        type("journal_state", "Журнал · Итог квеста", "«Выполнен» или «Провален» у законченного квеста (прижат вправо)",
                common(130, 10, "top_left").group("Итог")
                        .p("textScale", "размер", Kind.NUMBER, "0.75", "")
                        .p("doneColor", "выполнен", Kind.COLOR, "#FF7BE08A", "")
                        .p("failColor", "провален", Kind.COLOR, "#FFFF6B6B", ""));

        // ---------------------------------------------------------------- the shop's live parts
        type("shop_goods", "Магазин · Товары", "Карточки товаров рядами, цена под каждой. Колёсико прокручивает, клик выбирает",
                common(182, 140, "top_left").group("Сетка")
                        .p("columns", "в ряду", Kind.INT, "5", "")
                        .p("cellWidth", "ширина карточки", Kind.INT, "30", "")
                        .p("cellHeight", "высота карточки", Kind.INT, "43", "")
                        .p("gapX", "промежуток X", Kind.INT, "6", "")
                        .p("gapY", "промежуток Y", Kind.INT, "7", "")
                        .p("padX", "отступ X", Kind.INT, "2", "")
                        .p("padY", "отступ Y", Kind.INT, "5", "")
                        .group("Цвета")
                        .p("priceColor", "цена", Kind.COLOR, "#FFE2D7C4", "")
                        .p("sellColor", "продажа", Kind.COLOR, "#FF7BE08A", "")
                        .p("mutedColor", "«пусто»", Kind.COLOR, "#FF8C91A8", "")
                        .group("Текстуры")
                        .p("cell", "карточка", Kind.IMAGE, "", "")
                        .p("cellOn", "выбранная", Kind.IMAGE, "", "")
                        .p("locked", "заблокирована", Kind.IMAGE, "", "")
                        .p("sold", "распродана", Kind.IMAGE, "", ""));
        type("shop_slider", "Магазин · Ползунок", "Ползунок прокрутки товаров: ездит сверху вниз по элементу",
                common(9, 162, "top_left").group("Ползунок")
                        .p("thumbHeight", "высота ползунка", Kind.INT, "10", "")
                        .p("always", "всегда виден", Kind.BOOL, "false", "")
                        .p("image", "картинка", Kind.IMAGE, "", ""));
        type("shop_item", "Магазин · Выбранный товар", "Крупный предмет выбранного товара и «x сколько»",
                common(40, 32, "top_left").group("Товар")
                        .p("amount", "«x сколько»", Kind.BOOL, "true", "")
                        .p("textColor", "цвет", Kind.COLOR, "#FFFFFFFF", ""));
        type("shop_info", "Магазин · Цена и итог", "Название, цена, количество, итог и состояние сделки («Готово к покупке!»)",
                common(150, 76, "top_left").group("Цвета")
                        .p("textColor", "текст", Kind.COLOR, "#FFFFFFFF", "")
                        .p("priceColor", "цена", Kind.COLOR, "#FFFFD25A", "")
                        .p("sellColor", "продажа", Kind.COLOR, "#FF7BE08A", "")
                        .p("okColor", "можно", Kind.COLOR, "#FF55FF55", "")
                        .p("badColor", "нельзя", Kind.COLOR, "#FFFF5555", ""));
        type("shop_arrows", "Магазин · Стрелки количества", "−5 −1 +1 +5: сколько раз совершить сделку",
                common(82, 15, "top_left").group("Текстуры")
                        .p("minus5", "−5", Kind.IMAGE, "", "").p("minus1", "−1", Kind.IMAGE, "", "")
                        .p("plus1", "+1", Kind.IMAGE, "", "").p("plus5", "+5", Kind.IMAGE, "", "")
                        .p("minus5Hover", "−5 под мышью", Kind.IMAGE, "", "").p("minus1Hover", "−1 под мышью", Kind.IMAGE, "", "")
                        .p("plus1Hover", "+1 под мышью", Kind.IMAGE, "", "").p("plus5Hover", "+5 под мышью", Kind.IMAGE, "", ""));
        type("shop_deal", "Магазин · Кнопка сделки", "КУПИТЬ / ПРОДАТЬ / ОБМЕНЯТЬ",
                common(88, 14, "top_left").group("Кнопка")
                        .p("textScale", "размер текста", Kind.NUMBER, "0.75", "")
                        .p("textColor", "цвет текста", Kind.COLOR, "#FFFFFFFF", "")
                        .p("image", "кнопка", Kind.IMAGE, "", "")
                        .p("hoverImage", "под мышью", Kind.IMAGE, "", ""));
        type("shop_desc", "Магазин · Описание товара", "Описание выбранного товара, сколько строк влезет",
                common(106, 20, "top_left").group("Описание")
                        .p("textScale", "размер текста", Kind.NUMBER, "0.6", "")
                        .p("textColor", "цвет", Kind.COLOR, "#FF8C91A8", ""));
        type("shop_money", "Магазин · Деньги игрока", "Монета и сколько их у игрока (прижато вправо)",
                common(90, 13, "top_left").group("Деньги")
                        .p("textColor", "цвет", Kind.COLOR, "#FFFFD25A", ""));

        type("group", "Группа", "Папка для элементов: двигается, скрывается и появляется целиком. Ничего не рисует",
                common(160, 100));

        type("clip", "Кроп-зона", "Обрезает всё, что внутри неё, по своим границам",
                common(160, 100).group("Вид зоны")
                        .p("background", "фон", Kind.COLOR, "#00000000", "")
                        .p("radius", "скругление", Kind.INT, "0", "")
                        .p("borderColor", "рамка", Kind.COLOR, "#00000000", "")
                        .p("borderWidth", "толщина рамки", Kind.INT, "0", ""));

        type("scroll", "Панель прокрутки", "Содержимое прокручивается колесом мыши и ползунком; лишнее обрезается",
                common(160, 120).group("Прокрутка")
                        .c("direction", "направление", "vertical", List.of("vertical", "horizontal", "both"), "")
                        .p("contentWidth", "ширина содержимого", Kind.INT, "0", "0 — по крайним элементам")
                        .p("contentHeight", "высота содержимого", Kind.INT, "0", "0 — по крайним элементам")
                        .p("padding", "отступ в конце", Kind.INT, "6", "Запас после последнего элемента")
                        .p("speed", "шаг колеса", Kind.INT, "24", "Сколько пикселей за щелчок колеса")
                        .p("smooth", "плавно", Kind.BOOL, "true", "")
                        .p("scrollbar", "ползунок", Kind.BOOL, "true", "")
                        .p("barColor", "цвет ползунка", Kind.COLOR, "#AA8B6CFF", "")
                        .p("barWidth", "ширина ползунка", Kind.INT, "4", "")
                        .group("Вид панели")
                        .p("background", "фон", Kind.COLOR, "#80101116", "")
                        .p("radius", "скругление", Kind.INT, "4", "")
                        .p("borderColor", "рамка", Kind.COLOR, "#FF2E3140", "")
                        .p("borderWidth", "толщина рамки", Kind.INT, "1", ""));

        type("panel", "Панель", "Прямоугольник: фон, рамка, скругление, тень, градиент",
                common(120, 70).group("Заливка")
                        .p("color", "цвет", Kind.COLOR, "#E01C1E26", "Основной цвет фона")
                        .c("gradient", "градиент", "none", List.of("none", "vertical", "horizontal"), "Переход от основного цвета ко второму")
                        .p("color2", "второй цвет", Kind.COLOR, "#E0101116", "Конечный цвет градиента")
                        .group("Рамка")
                        .p("borderWidth", "толщина рамки", Kind.INT, "1", "0 — без рамки")
                        .p("borderColor", "цвет рамки", Kind.COLOR, "#FF3D4157", "")
                        .p("radius", "скругление", Kind.INT, "4", "Радиус углов")
                        .p("shadow", "тень", Kind.INT, "6", "Размер мягкой тени, 0 — без тени"));

        type("image", "Картинка", "Картинка проекта или текстура игры",
                common(64, 64).group("Картинка")
                        .p("image", "картинка", Kind.IMAGE, "", "Имя картинки из папки «Картинки» или путь к текстуре: minecraft:textures/item/apple.png")
                        .p("tint", "оттенок", Kind.COLOR, "#FFFFFFFF", "Цвет, на который умножается картинка")
                        .c("fit", "вписывание", "stretch", List.of("stretch", "contain", "cover", "tile"), "Как картинка заполняет рамку")
                        .p("flipX", "отразить по X", Kind.BOOL, "false", "")
                        .p("flipY", "отразить по Y", Kind.BOOL, "false", "")
                        .group("Область текстуры")
                        .p("u0", "u от", Kind.NUMBER, "0", "Часть текстуры: доля ширины 0..1")
                        .p("v0", "v от", Kind.NUMBER, "0", "Часть текстуры: доля высоты 0..1")
                        .p("u1", "u до", Kind.NUMBER, "1", "")
                        .p("v1", "v до", Kind.NUMBER, "1", ""));

        type("text", "Текст", "Надпись с переносом, выравниванием, стилями и печатной машинкой",
                common(140, 20).group("Текст")
                        .p("text", "текст", Kind.TEXT, "Текст", "Поддерживает \\n для новой строки. Из сюжета: gui text \"id\" \"...\"")
                        .p("color", "цвет", Kind.COLOR, "#FFFFFFFF", "")
                        .p("scale", "размер", Kind.NUMBER, "1", "Масштаб шрифта")
                        .c("align", "выравнивание", "left", List.of("left", "center", "right"), "")
                        .c("valign", "по вертикали", "top", List.of("top", "middle", "bottom"), "")
                        .p("wrap", "переносить", Kind.BOOL, "true", "Переносить строки по ширине элемента")
                        .c("overflow", "что не влезло", "visible", List.of("visible", "clip", "ellipsis"),
                                "visible — видно и за рамкой; clip — обрезать по рамке; ellipsis — обрезать и поставить «…»")
                        .p("lineSpacing", "межстрочный", Kind.INT, "1", "")
                        .p("shadow", "тень", Kind.BOOL, "true", "")
                        .group("Стиль")
                        .p("bold", "жирный", Kind.BOOL, "false", "")
                        .p("italic", "курсив", Kind.BOOL, "false", "")
                        .p("underline", "подчёркнутый", Kind.BOOL, "false", "")
                        .p("strike", "зачёркнутый", Kind.BOOL, "false", "")
                        .p("background", "фон", Kind.COLOR, "#00000000", "Подложка под текстом")
                        .p("padding", "отступ", Kind.INT, "0", "")
                        .p("typewriter", "печать, симв/с", Kind.NUMBER, "0", "Текст появляется по буквам; 0 — сразу"));

        type("button", "Кнопка", "Кнопка с событием для сюжета: wait button \"id\"",
                common(110, 22).group("Кнопка")
                        .p("text", "текст", Kind.TEXT, "Кнопка", "")
                        .p("textColor", "цвет текста", Kind.COLOR, "#FFFFFFFF", "")
                        .p("textScale", "размер текста", Kind.NUMBER, "1", "")
                        .p("enabled", "активна", Kind.BOOL, "true", "Неактивную кнопку нельзя нажать")
                        .c("action", "действие", "event", List.of("event", "close", "event_close"),
                                "event — сообщить сюжету; close — закрыть интерфейс; event_close — и то и другое")
                        .p("key", "клавиша", Kind.TEXT, "", "Горячая клавиша: 1, 2, E, SPACE, ENTER...")
                        .p("sound", "звук", Kind.SOUND, "minecraft:ui.button.click", "")
                        .group("Цвета")
                        .p("color", "фон", Kind.COLOR, "#FF2B2F45", "")
                        .p("hoverColor", "под мышью", Kind.COLOR, "#FF3A4066", "")
                        .p("pressColor", "нажата", Kind.COLOR, "#FF8B6CFF", "")
                        .p("disabledColor", "неактивна", Kind.COLOR, "#FF22242E", "")
                        .p("borderColor", "рамка", Kind.COLOR, "#FF4A5070", "")
                        .p("hoverBorderColor", "рамка под мышью", Kind.COLOR, "#FFA08AFF", "")
                        .p("borderWidth", "толщина рамки", Kind.INT, "1", "")
                        .p("radius", "скругление", Kind.INT, "4", "")
                        .group("Иконка")
                        .p("icon", "иконка", Kind.IMAGE, "", "Картинка слева от текста")
                        .p("iconSize", "размер иконки", Kind.INT, "14", ""));

        type("entity", "Сущность", "Живая 3D-модель сущности с анимацией",
                common(70, 110).group("Модель")
                        .p("entity", "сущность", Kind.ENTITY, "spraute:npc", "Тип сущности")
                        .p("scale", "масштаб", Kind.NUMBER, "1", "")
                        .p("yaw", "поворот", Kind.NUMBER, "200", "Поворот модели по горизонтали")
                        .p("pitch", "наклон", Kind.NUMBER, "5", "")
                        .p("followMouse", "смотрит на мышь", Kind.BOOL, "false", "Как в окне инвентаря")
                        .p("spin", "вращение, °/с", Kind.NUMBER, "0", "Автоматическое вращение")
                        .group("Анимация")
                        .p("anim", "анимация", Kind.ANIM, "", "Анимация анимаций на модели")
                        .p("animLayer", "слой", Kind.TEXT, "action", "")
                        .p("animLoop", "цикл", Kind.BOOL, "true", "")
                        .p("animSpeed", "скорость", Kind.NUMBER, "1", "")
                        .group("Фон")
                        .p("background", "фон", Kind.COLOR, "#00000000", ""));

        type("item", "Предмет", "Иконка предмета",
                common(32, 32).group("Предмет")
                        .p("item", "предмет", Kind.ITEM, "minecraft:diamond", "")
                        .p("count", "количество", Kind.INT, "1", "")
                        .p("showCount", "показывать число", Kind.BOOL, "true", "")
                        .p("background", "фон", Kind.COLOR, "#00000000", ""));

        type("slot", "Слот", "Ячейка для предмета. Игрок кладёт в неё предметы из элемента «Инвентарь» и забирает их. "
                        + "Сюжет: slotitem(\"id\"), slotcount(\"id\"), gui slot \"id\" \"minecraft:apple\" count 3, wait slot \"id\"",
                common(20, 20).group("Слот")
                        .c("mode", "режим", "storage", List.of("storage", "take", "put", "display"),
                                "storage — класть и забирать; take — только забрать (награда); put — только положить (сдать предмет); display — только показ")
                        .p("item", "предмет", Kind.ITEM, "", "Что лежит в слоте при первом открытии")
                        .p("count", "количество", Kind.INT, "1", "")
                        .p("filter", "только предмет", Kind.ITEM, "", "Пусто — подходит любой предмет")
                        .p("max", "максимум", Kind.INT, "64", "Сколько предметов помещается")
                        .group("Вид")
                        .c("style", "стиль", "vanilla", List.of("vanilla", "flat", "none"), "vanilla — как ячейка Minecraft, flat — плоская, none — без фона")
                        .p("texture", "картинка фона", Kind.IMAGE, "", "Своя картинка ячейки вместо стиля")
                        .p("background", "цвет (flat)", Kind.COLOR, "#AA000000", "")
                        .p("hoverColor", "подсветка", Kind.COLOR, "#80FFFFFF", "")
                        .p("ghost", "подсказка-предмет", Kind.ITEM, "", "Полупрозрачный предмет в пустом слоте: что сюда положить"));

        type("inventory", "Инвентарь игрока", "Ячейки инвентаря игрока: из них игрок берёт предметы и кладёт в слоты",
                common(162, 76).group("Инвентарь")
                        .c("rows", "ячейки", "all", List.of("all", "main", "hotbar"), "all — рюкзак и хотбар, main — только рюкзак, hotbar — только хотбар")
                        .p("cell", "размер ячейки", Kind.INT, "18", "")
                        .group("Вид")
                        .c("style", "стиль", "vanilla", List.of("vanilla", "flat", "none"), "")
                        .p("texture", "картинка ячейки", Kind.IMAGE, "", "")
                        .p("background", "цвет (flat)", Kind.COLOR, "#AA000000", "")
                        .p("hoverColor", "подсветка", Kind.COLOR, "#80FFFFFF", ""));

        type("progress", "Полоса", "Полоса прогресса, здоровья, загрузки",
                common(120, 10).group("Значение")
                        .p("value", "значение", Kind.NUMBER, "0.6", "От 0 до 1. Из сюжета: gui progress \"id\" 0.4")
                        .c("bind", "показывает", "none", List.of("none", "health", "food", "armor", "air", "exp"),
                                "Сама показывает здоровье, еду, броню, воздух или опыт игрока (none — значение выше или из сюжета)")
                        .c("direction", "направление", "right", List.of("right", "left", "up", "down"), "")
                        .p("smooth", "плавность", Kind.NUMBER, "8", "Скорость плавного изменения, 0 — мгновенно")
                        .group("Цвета")
                        .p("color", "заполнение", Kind.COLOR, "#FF4CC38A", "")
                        .p("color2", "конец градиента", Kind.COLOR, "", "Пусто — без градиента")
                        .p("background", "фон", Kind.COLOR, "#FF16171D", "")
                        .p("borderColor", "рамка", Kind.COLOR, "#FF3D4157", "")
                        .p("borderWidth", "толщина рамки", Kind.INT, "1", "")
                        .p("radius", "скругление", Kind.INT, "3", "")
                        .group("Надпись")
                        .p("label", "текст", Kind.TEXT, "", "{p} — проценты. Например: {p}%")
                        .p("textColor", "цвет текста", Kind.COLOR, "#FFFFFFFF", ""));

        type("input", "Поле ввода", "Поле, куда игрок пишет текст. Сюжет читает его функцией input(\"id\")",
                common(140, 20).group("Поле")
                        .p("text", "текст", Kind.TEXT, "", "Начальное значение")
                        .p("placeholder", "подсказка", Kind.TEXT, "Введите текст", "Серый текст в пустом поле")
                        .p("maxLength", "макс. длина", Kind.INT, "64", "")
                        .p("textColor", "цвет текста", Kind.COLOR, "#FFFFFFFF", "")
                        .p("background", "фон", Kind.COLOR, "#FF12131A", "")
                        .p("borderColor", "рамка", Kind.COLOR, "#FF3D4157", "")
                        .p("focusColor", "рамка в фокусе", Kind.COLOR, "#FF8B6CFF", "")
                        .p("radius", "скругление", Kind.INT, "3", ""));
    }

    public static boolean isContainer(String type) {
        return type.equals("group") || type.equals("clip") || type.equals("scroll");
    }

    public static Type type(String key) {
        return TYPES.get(key);
    }

    public static List<Type> types() {
        return new ArrayList<>(TYPES.values());
    }

    public static List<Prop> docProps() {
        return Collections.unmodifiableList(DOC_PROPS);
    }

    public static Prop docProp(String key) {
        for (Prop p : DOC_PROPS) if (p.key.equals(key)) return p;
        return null;
    }
}
