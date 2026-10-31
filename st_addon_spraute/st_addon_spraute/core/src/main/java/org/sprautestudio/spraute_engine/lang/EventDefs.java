package org.sprautestudio.spraute_engine.lang;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game events a story can wait for ({@code wait break "minecraft:stone"}) or handle in the background
 * ({@code on break { say ... event("block") }}). Each has an optional filter and data readable with
 * {@code event("field")} while the handler (or the code after the wait) runs.
 */
public final class EventDefs {
    private EventDefs() {}

    /**
     * @param filter   kind of the optional filter, or null
     * @param matches  data fields the filter is compared with
     * @param title    words for blocks: "игрок сломал блок"
     * @param fields   data fields this event fills
     */
    public record Def(String name, String title, String doc, ParamType filter, String filterLabel, List<String> matches, List<String> fields) {}

    public static final Map<String, Def> ALL = new LinkedHashMap<>();

    /** Russian titles of data fields. */
    public static final Map<String, String> FIELDS = new LinkedHashMap<>();

    private static void def(String name, String title, String doc, ParamType filter, String filterLabel, List<String> matches, String... fields) {
        ALL.put(name, new Def(name, title, doc, filter, filterLabel, matches, List.of(fields)));
    }

    static {
        FIELDS.put("player", "ник игрока");
        FIELDS.put("block", "блок");
        FIELDS.put("item", "предмет");
        FIELDS.put("count", "количество");
        FIELDS.put("entity", "тип сущности");
        FIELDS.put("character", "персонаж сюжета");
        FIELDS.put("name", "имя сущности");
        FIELDS.put("amount", "число (урон, опыт, высота...)");
        FIELDS.put("source", "причина");
        FIELDS.put("slot", "слот");
        FIELDS.put("level", "уровень");
        FIELDS.put("id", "id (достижение, эффект, контейнер)");
        FIELDS.put("from", "откуда");
        FIELDS.put("to", "куда");
        FIELDS.put("biome", "биом");
        FIELDS.put("text", "текст (чат)");
        FIELDS.put("value", "значение (emit)");
        FIELDS.put("shop", "магазин");
        FIELDS.put("quest", "квест");
        FIELDS.put("zone", "зона");
        FIELDS.put("killer", "убийца (id сущности или player)");
        FIELDS.put("hit", "куда попал: entity, block, none");
        FIELDS.put("key", "клавиша");
        FIELDS.put("onground", "на земле");
        FIELDS.put("fx", "эффект");
        FIELDS.put("who", "кто (персонаж)");
        FIELDS.put("by", "кем (player или персонаж)");
        FIELDS.put("damage", "урон");
        FIELDS.put("hp", "здоровье в бою");
        FIELDS.put("blocked", "заблокировал");
        FIELDS.put("task", "задача (id)");
        FIELDS.put("offer", "товар (id)");
        FIELDS.put("type", "buy / sell / trade");
        FIELDS.put("part", "часть хитбокса");
        FIELDS.put("x", "X");
        FIELDS.put("y", "Y");
        FIELDS.put("z", "Z");

        List<String> block = List.of("block"), item = List.of("item"), ent = List.of("entity", "character"), id = List.of("id");
        String pos = "x,y,z";
        // ---- blocks
        def("break", "игрок сломал блок", "Игрок сломал блок.", ParamType.ITEM, "блок", block, "player", "block", "item", "x", "y", "z");
        def("place", "игрок поставил блок", "Игрок поставил блок.", ParamType.ITEM, "блок", block, "player", "block", "x", "y", "z");
        def("punch", "игрок ударил по блоку", "Игрок ударил по блоку (левый клик).", ParamType.ITEM, "блок", block, "player", "block", "item", "x", "y", "z");
        def("useblock", "игрок нажал на блок", "Правый клик по блоку: дверь, рычаг, сундук, кнопка...", ParamType.ITEM, "блок", block, "player", "block", "item", "x", "y", "z");
        def("bonemeal", "игрок удобрил", "Игрок использовал костную муку на блоке.", ParamType.ITEM, "блок", block, "player", "block", "x", "y", "z");
        def("bucket", "игрок набрал ведро", "Игрок наполнил или вылил ведро.", ParamType.ITEM, "предмет", item, "player", "item", "x", "y", "z");
        // ---- creatures
        def("interact", "игрок взаимодействовал с", "Правый клик по сущности: персонаж сюжета, житель, животное...", ParamType.TARGET, "кем", ent,
                "player", "entity", "character", "name", "item", "x", "y", "z");
        def("attack", "игрок ударил", "Игрок ударил сущность.", ParamType.TARGET, "кого", ent, "player", "entity", "character", "name", "item", "x", "y", "z");
        def("kill", "игрок убил", "Игрок убил сущность.", ParamType.TARGET, "кого", ent, "player", "entity", "character", "name", "item", "x", "y", "z");
        def("hurt", "игрок получил урон", "Игрок получил урон. amount — сколько, source — причина, entity — кто ударил.", ParamType.TARGET, "от кого", ent,
                "player", "amount", "source", "entity", "character", "x", "y", "z");
        def("keypress", "игрок нажал клавишу", "Клавиша в игре (не в меню): \"jump\", \"sneak\", \"sprint\" — эти действия на любой клавише; "
                        + "остальные по имени: \"r\", \"f\", \"g\", \"1\", \"left.alt\"… onground — стоял ли на земле. on keypress \"jump\" { … }",
                ParamType.TEXT, "клавиша", List.of("key"), "player", "key", "onground", "x", "y", "z");
        def("casthit", "снаряд попал", "Снаряд (cast) долетел: hit — \"entity\" (в существо), \"block\" или \"none\" (улетел). "
                        + "Кто: entity, character, name, ref; где: x y z; block — блок; damage — урон; id — имя снаряда (по умолчанию — имя эффекта).",
                ParamType.TEXT, "снаряд", List.of("id"), "id", "fx", "hit", "entity", "character", "name", "block", "damage", "x", "y", "z");
        def("npcdeath", "умер нпс или моб", "Умер персонаж сюжета или любой моб — кто бы ни убил. by — кто: \"player\", имя персонажа, id моба "
                        + "или \"\" (упал, сгорел, утонул…); source — причина (player, mob, fall, lava, fight…). on npcdeath alice { log event(\"by\") }.",
                ParamType.TARGET, "кто", ent, "entity", "character", "name", "by", "killer", "source", "x", "y", "z");
        def("death", "игрок умер", "Игрок умер. source — причина, entity — убийца.", null, null, List.of(), "player", "source", "entity", "character", "x", "y", "z");
        def("respawn", "игрок возродился", "Игрок возродился после смерти.", null, null, List.of(), "player", "x", "y", "z");
        def("tame", "игрок приручил", "Игрок приручил животное.", ParamType.TARGET, "кого", ent, "player", "entity", "name", "x", "y", "z");
        def("breed", "игрок развёл животных", "От животных игрока родился детёныш.", ParamType.TARGET, "кого", ent, "player", "entity", "x", "y", "z");
        def("mount", "игрок сел на", "Игрок сел верхом или в транспорт.", ParamType.TARGET, "на кого", ent, "player", "entity", "character", "name");
        def("dismount", "игрок слез с", "Игрок слез с животного или транспорта.", ParamType.TARGET, "с кого", ent, "player", "entity", "character", "name");
        def("trade", "игрок поторговал", "Игрок совершил сделку с жителем. item — что купил.", ParamType.ITEM, "товар", item, "player", "item", "count", "entity", "character");
        // ---- items
        def("pickup", "игрок подобрал", "Игрок подобрал предмет с земли.", ParamType.ITEM, "предмет", item, "player", "item", "count");
        def("drop", "игрок выбросил", "Игрок выбросил предмет.", ParamType.ITEM, "предмет", item, "player", "item", "count");
        def("use", "игрок использовал", "Правый клик предметом в руке.", ParamType.ITEM, "предмет", item, "player", "item");
        def("eat", "игрок съел", "Игрок доел еду или выпил зелье.", ParamType.ITEM, "предмет", item, "player", "item");
        def("craft", "игрок скрафтил", "Игрок скрафтил предмет.", ParamType.ITEM, "предмет", item, "player", "item", "count");
        def("smelt", "игрок переплавил", "Игрок забрал предмет из печи.", ParamType.ITEM, "предмет", item, "player", "item", "count");
        def("equip", "игрок надел", "Игрок взял в руку или надел предмет. slot — head, chest, legs, feet, mainhand, offhand.", ParamType.ITEM, "предмет", item,
                "player", "item", "slot");
        def("select", "игрок выбрал слот", "Игрок переключил ячейку хотбара. slot — номер 1–9.", ParamType.ITEM, "предмет", item, "player", "item", "slot");
        def("shoot", "игрок выстрелил", "Игрок выпустил стрелу из лука или арбалета.", null, null, List.of(), "player", "item", "amount");
        def("fish", "игрок поймал рыбу", "Игрок что-то выловил удочкой.", ParamType.ITEM, "улов", item, "player", "item", "count");
        def("opencontainer", "игрок открыл контейнер", "Игрок открыл сундук, печь, верстак... id — тип окна.", null, null, List.of(), "player", "id");
        def("closecontainer", "игрок закрыл контейнер", "Игрок закрыл окно контейнера.", null, null, List.of(), "player", "id");
        // ---- the player
        def("jump", "игрок прыгнул", "Игрок прыгнул.", null, null, List.of(), "player", "x", "y", "z");
        def("sneak", "игрок присел", "Игрок зажал присед.", null, null, List.of(), "player", "x", "y", "z");
        def("sprint", "игрок побежал", "Игрок начал бежать.", null, null, List.of(), "player", "x", "y", "z");
        def("fall", "игрок упал", "Игрок приземлился с высоты больше 3 блоков. amount — высота падения.", null, null, List.of(), "player", "amount", "x", "y", "z");
        def("swim", "игрок вошёл в воду", "Игрок оказался в воде.", null, null, List.of(), "player", "x", "y", "z");
        def("burn", "игрок загорелся", "Игрок загорелся.", null, null, List.of(), "player", "x", "y", "z");
        def("lowhealth", "у игрока мало здоровья", "Здоровье игрока упало ниже трети.", null, null, List.of(), "player", "amount");
        def("sleep", "игрок лёг спать", "Игрок лёг в кровать.", null, null, List.of(), "player", "x", "y", "z");
        def("wake", "игрок проснулся", "Игрок встал с кровати.", null, null, List.of(), "player");
        def("levelup", "игрок получил уровень", "Изменился уровень опыта игрока. level — новый уровень.", null, null, List.of(), "player", "level");
        def("xp", "игрок получил опыт", "Игрок подобрал шар опыта. amount — сколько.", null, null, List.of(), "player", "amount");
        def("effect", "на игрока наложен эффект", "На игрока наложен эффект (зелье, маяк...). id — эффект, level — сила.", ParamType.EFFECT, "эффект", id,
                "player", "id", "level");
        def("advancement", "игрок получил достижение", "Игрок получил достижение. id — например minecraft:story/mine_stone.", ParamType.TEXT, "достижение", id,
                "player", "id");
        def("join", "игрок зашёл", "Игрок зашёл на сервер (в мир).", null, null, List.of(), "player");
        // ---- the world
        def("dimension", "игрок сменил мир", "Игрок перешёл в другое измерение. to — куда: minecraft:the_nether...", ParamType.TEXT, "куда", List.of("to"),
                "player", "from", "to");
        def("biome", "игрок вошёл в биом", "Игрок пришёл в другой биом.", ParamType.TEXT, "биом", List.of("biome"), "player", "biome", "from");
        def("day", "наступил день", "Утро: начался новый день.", null, null, List.of(), "player");
        def("night", "наступила ночь", "Наступила ночь.", null, null, List.of(), "player");
        def("rain", "начался дождь", "Пошёл дождь или гроза.", null, null, List.of(), "player");
        def("clear", "дождь кончился", "Дождь закончился.", null, null, List.of(), "player");
        // ---- story particles and coins
        def("orb", "игрок подобрал частицу", "Игрок подобрал частицу сюжета (orb spawn). id — её имя, amount — ценность, count — монеты, level — опыт.",
                ParamType.TEXT, "частица", List.of("id"), "player", "id", "amount", "count", "level", "x", "y", "z");
        def("orbexpire", "частица исчезла", "Частица сюжета исчезла, никем не подобранная.", ParamType.TEXT, "частица", List.of("id"), "id", "amount", "x", "y", "z");
        def("coins", "изменились монеты", "Изменилось число монет игрока. amount — на сколько (меньше нуля — потратил), level — сколько стало.",
                null, null, List.of(), "player", "amount", "level");
        // ---- cutscenes
        def("cutscene", "катсцена закончилась", "Катсцена доиграла или её пропустили. id — имя катсцены.", ParamType.TEXT, "катсцена", List.of("id"), "player", "id");
        def("cutsceneskip", "катсцену пропустили", "Игрок пропустил катсцену (пробелом).", ParamType.TEXT, "катсцена", List.of("id"), "player", "id");
        def("cutscenemark", "метка катсцены", "Катсцена дошла до кадра с меткой. id — имя метки.", ParamType.TEXT, "метка", List.of("id"), "player", "id");
        def("purchase", "сделка в магазине", "Игрок купил, продал или обменял в магазине. offer — id товара, amount — сколько раз, count — сколько предметов.",
                ParamType.TEXT, "магазин", List.of("shop"), "player", "shop", "offer", "item", "count", "amount", "type");
        def("fightstart", "бой начался", "Персонаж начал драться (fight).", ParamType.TEXT, "персонаж", List.of("who"), "who", "hp");
        def("fighthit", "удар по бойцу", "По персонажу попали в бою. by — кто (player или персонаж), damage — урон, hp — сколько осталось, blocked — заблокировал.",
                ParamType.TEXT, "персонаж", List.of("who"), "who", "by", "damage", "hp", "blocked");
        def("parthit", "попали в часть хитбокса", "Удар, стрела или снаряд попали в персонажа с хитбоксами из редактора сущности. "
                        + "part — имя части (\"body\" — основной хитбокс), damage — урон после множителя части, by — кто (player или имя). "
                        + "on parthit boss { if event(\"part\") == \"голова\" { say boss \"Ай!\" nowait } }",
                ParamType.TARGET, "кого", ent, "part", "damage", "by", "entity", "character", "name", "x", "y", "z");
        def("fightdodge", "боец увернулся", "Персонаж увернулся от удара.", ParamType.TEXT, "персонаж", List.of("who"), "who", "by");
        def("fightdown", "боец повержен", "Здоровье персонажа в бою кончилось. by — кто добил.", ParamType.TEXT, "персонаж", List.of("who"), "who", "by");
        def("fightflee", "боец убегает", "Персонаж струсил и бежит (стиль: «Убегает при»).", ParamType.TEXT, "персонаж", List.of("who"), "who", "hp");
        def("fightwin", "боец победил", "Цель персонажа повержена, погибла или ушла — бой окончен.", ParamType.TEXT, "персонаж", List.of("who"), "who", "target", "hp");
        def("forge", "событие Forge или другого мода", "Любое событие Forge или мода по имени класса: on forge \"LivingJumpEvent\" { … }. "
                        + "Поля — то, что отдаёт событие: entity (существо, + entitytype), amount, pos, itemStack… — смотрите event(\"…\"); "
                        + "event — короткое имя события, class — полное.",
                ParamType.TEXT, "событие", List.of("event", "class"), "event", "class", "entity", "character", "player");
        def("pathend", "персонаж дошёл до конца пути", "Персонаж прошёл путь (walk) до конца. path — имя пути; кто — character, name, entity.",
                ParamType.TEXT, "путь", List.of("path"), "path", "character", "name", "entity", "x", "y", "z");
        def("zoneenter", "игрок вошёл в зону", "Игрок вошёл в зону из папки «Зоны». zone — её имя.", ParamType.TEXT, "зона", List.of("zone"), "player", "zone");
        def("zoneleave", "игрок вышел из зоны", "Игрок вышел из зоны.", ParamType.TEXT, "зона", List.of("zone"), "player", "zone");
        def("queststart", "выдан квест", "Игроку выдан квест (quest start).", ParamType.TEXT, "квест", List.of("quest"), "player", "quest");
        def("questtask", "задача квеста сделана", "Задача квеста выполнена до конца. task — её id.", ParamType.TEXT, "квест", List.of("quest"), "player", "quest", "task");
        def("questdone", "квест выполнен", "Квест выполнен (сам или quest done); награда уже выдана.", ParamType.TEXT, "квест", List.of("quest"), "player", "quest");
        def("questfail", "квест провален", "Квест провален (quest fail).", ParamType.TEXT, "квест", List.of("quest"), "player", "quest");
        def("signal", "получен сигнал", "Своё событие: его посылает emit \"имя\" из любого сюжета. Значение — event(\"value\").",
                ParamType.TEXT, "имя", List.of("name"), "name", "value", "from");
        def("leftair", "игрок кликнул в воздух", "Левый клик в пустоту.", null, null, List.of(), "player");
        def("cameraend", "полёт камеры закончился", "Камера долетела и остановилась.", null, null, List.of(), "x", "y", "z");
        def("servertick", "тик сервера", "Каждый тик сервера.", null, null, List.of());
        def("playertick", "тик игрока", "Каждый тик каждого игрока.", null, null, List.of(), "player");
        def("newday", "новый игровой день", "Часы мира перешли через полночь. amount — номер дня.", null, null, List.of(), "amount");
        def("scriptstart", "сюжет запущен", "Запустился сюжет. id — его номер.", ParamType.TEXT, "сюжет", List.of("id"), "id");
        def("scriptstop", "сюжет остановлен", "Сюжет остановился. id — его номер.", ParamType.TEXT, "сюжет", List.of("id"), "id");
        def("checkpoint", "пройдена точка сохранения", "Сюжет прошёл точку сохранения. id — её имя.", ParamType.TEXT, "точка", List.of("id"), "id");
        def("charspawn", "персонаж появился", "Тело персонажа появилось в мире.", null, null, List.of(), "id", "name", "x", "y", "z");
        def("charremove", "персонаж удалён", "Тело персонажа убрано из мира.", null, null, List.of(), "id", "name", "x", "y", "z");
        def("behaviorstart", "поведение началось", "У персонажа началось поведение. type — его тип.", ParamType.TEXT, "тип", List.of("type"), "type", "name");
        def("behaviorend", "поведение закончилось", "У персонажа закончилось поведение. type — его тип.", ParamType.TEXT, "тип", List.of("type"), "type", "name");
        def("explosion", "рядом взрыв", "Взрыв в 24 блоках от игрока.", null, null, List.of(), "player", "x", "y", "z");
    }

    public static Def get(String name) {
        return ALL.get(name);
    }

    /** Spec syntax of the wait command: the filter, if any, is optional. */
    public static String syntax(Def d) {
        if (d.filter() == null) return "";
        String kind = switch (d.filter()) {
            case ITEM -> "item";
            case TARGET -> "target";
            case EFFECT -> "effect";
            default -> "text";
        };
        return "[<what:" + kind + ">]";
    }

    public static String fieldsDoc(Def d) {
        StringBuilder sb = new StringBuilder();
        for (String f : d.fields()) sb.append(sb.length() > 0 ? ", " : "").append(f).append(" — ").append(FIELDS.getOrDefault(f, f));
        return sb.toString();
    }
}
