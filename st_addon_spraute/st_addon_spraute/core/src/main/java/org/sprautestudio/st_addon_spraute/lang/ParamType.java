package org.sprautestudio.st_addon_spraute.lang;

/** Kinds of command parameters; drives validation, completion and node editors. */
public enum ParamType {
    TEXT("текст", "Строка в кавычках. Поддерживает подстановки: \"Привет, {имя}\""),
    INT("целое", "Целое число"),
    NUMBER("число", "Число, можно дробное: 0.35"),
    DURATION("время", "Длительность: 20t (тики), 1.5s (секунды), 2m (минуты)"),
    CHAR("персонаж", "Имя персонажа, объявленного через character"),
    LOC("точка", "Имя точки (location) или координаты (x, y, z)"),
    TARGET("цель", "player — игрок, имя персонажа, \"minecraft:zombie\" — ближайший моб этого типа (\"mob\" — любой), точка или (x, y, z)"),
    SPEAKER("говорящий", "Слот речи (0 или 1), персонаж, player (сам игрок — в чате его лицо) или имя в кавычках"),
    LABEL("метка", "Имя метки, объявленной через label"),
    BOOL("да/нет", "true или false"),
    EXPR("выражение", "Любое выражение: hp > 5 and flag(\"x\")"),
    SOUND("звук", "Id звука: \"minecraft:entity.player.levelup\""),
    ITEM("предмет", "Id предмета: \"minecraft:diamond\""),
    EFFECT("эффект", "Id эффекта: \"minecraft:blindness\""),
    ENTITY("сущность", "Id типа сущности: \"spraute:npc\" или модель проекта: \"spraute:Робот\""),
    EMOTION("эмоция", "Эмоция диалога: HAPPY_NEW, SAD1, ANGRY..."),
    KEY("клавиша", "Клавиша: F8, F9, F10, G, SPACE..."),
    SCRIPT("скрипт", "Числовой id сюжета"),
    ANIM("анимация", "Анимация: \"entity/humanoid!walk\" или своя у модели: \"spraute:Робот/walk\". Смотрите вкладку «Ассеты»"),
    LAYER("слой", "Слой анимации: action (всё тело, перекрывает), ambient, emotion (лицо), eyes или номер 0–20 (накладываемые слои)"),
    MOVETYPE("тип движения", "Состояние персонажа: idle (стоит), walk (идёт), fastwalk (быстро идёт), run (бежит)"),
    FUNC("функция", "Имя функции, объявленной через func"),
    BRANCH("ветка", "Имя асинхронной ветки (async имя { } или async call ... as имя)"),
    IMAGE("картинка", "Картинка из папки «Картинки» проекта или встроенная иконка Spraute"),
    GUI("интерфейс", "Имя интерфейса из папки «Интерфейсы» проекта, в кавычках"),
    GUIEL("элемент", "Id элемента интерфейса в кавычках, например \"yes\""),
    VAR("переменная", "Переменная сюжета (var)"),
    CHOICE("вариант", "Выбор режима блока: меняет то, что блок делает"),
    EASE("плавность", "Плавность движения: smooth, linear, ease_in_out_sine, ease_out_cubic, ease_out_back, ease_out_bounce..."),
    CUTSCENE("катсцена", "Имя катсцены из папки «Катсцены» проекта, в кавычках"),
    SHOP("магазин", "Имя магазина из папки «Магазины» проекта, в кавычках"),
    QUEST("квест", "Имя квеста из папки «Квесты» проекта, в кавычках"),
    ZONE("зона", "Имя зоны из папки «Зоны» проекта, в кавычках"),
    PATH("путь", "Имя пути из папки «Пути» проекта, в кавычках"),
    FIGHT("стиль боя", "Стиль боя из папки «Стили боя» или встроенный: \"fists\", \"sword\", \"brute\", \"weak\""),
    VFX("эффект частиц", "Эффект из папки «Эффекты» проекта или встроенный: \"nuke\", \"sky_beam\", \"orbit_orbs\", \"explosion\"..."),
    SYSTEM("система", "Система движка: health (полоска HP), coins (монеты), time (время суток), inventory (свой инвентарь), slots (блокировка слотов), chat (скрывать чат)");

    public final String title;
    public final String doc;

    ParamType(String title, String doc) {
        this.title = title;
        this.doc = doc;
    }

    public static ParamType byName(String s) {
        return switch (s) {
            case "text" -> TEXT;
            case "int" -> INT;
            case "number" -> NUMBER;
            case "duration" -> DURATION;
            case "char" -> CHAR;
            case "loc" -> LOC;
            case "target" -> TARGET;
            case "speaker" -> SPEAKER;
            case "label" -> LABEL;
            case "bool" -> BOOL;
            case "expr" -> EXPR;
            case "sound" -> SOUND;
            case "item" -> ITEM;
            case "effect" -> EFFECT;
            case "entity" -> ENTITY;
            case "emotion" -> EMOTION;
            case "key" -> KEY;
            case "script" -> SCRIPT;
            case "anim" -> ANIM;
            case "layer" -> LAYER;
            case "movetype" -> MOVETYPE;
            case "func" -> FUNC;
            case "branch" -> BRANCH;
            case "image" -> IMAGE;
            case "gui" -> GUI;
            case "guiel" -> GUIEL;
            case "var" -> VAR;
            case "choice" -> CHOICE;
            case "ease" -> EASE;
            case "cutscene" -> CUTSCENE;
            case "vfx" -> VFX;
            case "shop" -> SHOP;
            case "quest" -> QUEST;
            case "zone" -> ZONE;
            case "path" -> PATH;
            case "fight" -> FIGHT;
            case "system" -> SYSTEM;
            default -> throw new IllegalArgumentException("unknown param type " + s);
        };
    }
}
