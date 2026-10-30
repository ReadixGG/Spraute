package org.sprautestudio.st_addon_spraute.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of everything the Spraute Story language knows: commands, keywords, functions. */
public final class Specs {
    private Specs() {}

    private static final Map<String, CommandSpec> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, FunctionSpec> FUNCTIONS = new LinkedHashMap<>();
    private static final Map<String, String> KEYWORD_DOCS = new LinkedHashMap<>();

    public static final String CAT_SPEECH = "Речь";
    public static final String CAT_WAIT = "Ожидание";
    public static final String CAT_CHARACTER = "Персонажи";
    public static final String CAT_PLAYER = "Игрок и мир";
    public static final String CAT_SCREEN = "Экран";
    public static final String CAT_LOGIC = "Логика";
    public static final String CAT_ANIM = "Анимации";
    public static final String CAT_GUI = "Интерфейс";
    public static final String CAT_CAMERA = "Камера";
    public static final String CAT_FX = "Эффекты";
    public static final String CAT_BOSS = "Бой и боссы";

    public static final List<String> CATEGORIES = List.of(CAT_SPEECH, CAT_WAIT, CAT_CHARACTER, CAT_ANIM, CAT_CAMERA, CAT_FX, CAT_BOSS, CAT_GUI, CAT_PLAYER, CAT_SCREEN, CAT_LOGIC);

    /** Named animation layers of Spraute characters (numbers 0–20 map to ANIMATION_n). */
    public static final List<String> LAYERS = List.of("action", "ambient", "emotion", "eyes",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

    public static final List<String> MOVE_TYPES = List.of("idle", "walk", "fastwalk", "run");
    /** Spraute systems a story can switch on and off. */
    public static final List<String> ENGINE_SYSTEMS = List.of("health", "coins", "time", "inventory", "slots", "chat");

    public static String systemTitle(String id) {
        return switch (id) {
            case "health" -> "полоска здоровья";
            case "coins" -> "монеты (монеты)";
            case "time" -> "время суток";
            case "inventory" -> "свой инвентарь";
            case "slots" -> "блокировка слотов";
            case "chat" -> "скрытие чата";
            default -> id;
        };
    }

    /** Speech window icons shipped with Spraute (st_addon_spraute:textures/gui/chat/NAME.png). */
    public static final List<String> SPEECH_ICONS = List.of("generic_man");

    /** Default speech icon for an entity type id. */
    public static String defaultSpeechIcon(String entityType) {
        return "generic_man";
    }

    /** Real animation layer name for a script layer, or null when unknown. */
    public static String layerName(String layer) {
        String l = layer.trim().toLowerCase();
        if (l.matches("\\d+")) {
            int n = Integer.parseInt(l);
            return n <= 20 ? "ANIMATION_" + n : null;
        }
        return switch (l) {
            case "action" -> "action";
            case "ambient" -> "ambient";
            case "emotion", "face" -> "emotion";
            case "eyes" -> "EYES";
            default -> null;
        };
    }

    public record FunctionSpec(String name, String signature, String returns, String doc) {}

    private static void cmd(String key, String cat, String syntax, String doc) {
        COMMANDS.put(key, new CommandSpec(key, cat, syntax, doc));
    }

    private static void fn(String name, String sig, String returns, String doc) {
        FUNCTIONS.put(name, new FunctionSpec(name, sig, returns, doc));
    }

    static {
        // ---------------------------------------------------------------- speech
        cmd("speaker", CAT_SPEECH, "<slot:int> <who:speaker>",
                "Назначает говорящего для слота речи (0 — слева, 1 — справа). Можно указать персонажа или имя в кавычках.");
        cmd("say", CAT_SPEECH, "<who:speaker> <text:text> [nowait]",
                "Реплика в окне речи Spraute. Говорящий — номер слота или персонаж, привязанный через speaker.\n"
                        + "Сюжет ждёт, пока игрок прочитает реплику и нажмёт F8. С флагом nowait — сразу идёт дальше.");
        cmd("think", CAT_SPEECH, "<text:text> [nowait]",
                "Мысль главного героя. Как и say, ждёт F8; nowait — не ждать.");
        cmd("clearchat", CAT_SPEECH, "",
                "Очищает окно речи.");
        cmd("title", CAT_SPEECH, "<text:text> [sub <subtitle:text>] [time <fadeIn:duration> <stay:duration> <fadeOut:duration>]",
                "Показывает большой заголовок на экране.");
        cmd("message", CAT_SPEECH, "<text:text>",
                "Обычное сообщение в чат всем игрокам.");
        cmd("web", CAT_SPEECH, "<text:text>",
                "Сообщение в «веб»-ленту Spraute (интерфейс симулятора).");
        cmd("warn", CAT_SPEECH, "<text:text>",
                "Предупреждение в веб-ленте Spraute.");

        // ------------------------------------------------------------------ wait
        cmd("wait", CAT_WAIT, "<time:duration>",
                "Пауза. Пример: wait 2s");
        cmd("wait random", CAT_WAIT, "<min:duration> <max:duration>",
                "Пауза случайной длины между min и max.");
        cmd("wait until", CAT_WAIT, "<condition:expr>",
                "Ждёт, пока условие не станет истинным. Пример: wait until near(camp, 4)");
        cmd("wait flag", CAT_WAIT, "<name:text>",
                "Ждёт установки флага сюжета.");
        cmd("wait key", CAT_WAIT, "<key:key>",
                "Ждёт нажатия клавиши игроком.");
        cmd("wait trigger", CAT_WAIT, "",
                "Ждёт команды /script trigger (или кнопки «Триггер» в панели сюжетов).");
        cmd("wait chat", CAT_WAIT, "<words:text...>",
                "Ждёт, пока игрок напишет в чат одно из слов (регистр не важен). Что написал — event(\"text\"). В фоне: on chat \"привет\" { ... }");
        cmd("wait click", CAT_WAIT, "<who:char>",
                "Ждёт клика игрока по персонажу.");
        cmd("wait clickblock", CAT_WAIT, "<pos:loc>",
                "Ждёт клика игрока по блоку.");
        cmd("wait lookat", CAT_WAIT, "<target:target>",
                "Ждёт, пока игрок посмотрит на персонажа, существо или точку: wait lookat alice, wait lookat (10, 70, 5).");
        cmd("wait near", CAT_WAIT, "<target:target> [radius <radius:number>]",
                "Ждёт, пока игрок подойдёт к точке или персонажу (радиус по умолчанию 3).");
        cmd("wait enter", CAT_WAIT, "<from:loc> <to:loc>",
                "Ждёт, пока игрок войдёт в область между двумя углами.");
        cmd("wait exit", CAT_WAIT, "<from:loc> <to:loc>",
                "Ждёт, пока игрок выйдет из области.");
        cmd("wait visible", CAT_WAIT, "<who:char>",
                "Ждёт, пока игрок увидит персонажа.");

        // ------------------------------------------------------------- character
        cmd("spawn", CAT_CHARACTER, "<who:char> at <pos:target>",
                "Создаёт персонажа (если его ещё нет) и ставит к цели: точка, player, другой персонаж или моб (\"minecraft:zombie\").");
        cmd("tp", CAT_CHARACTER, "<who:char> to <pos:target>",
                "Мгновенно переносит персонажа к цели: точка, player, персонаж или моб.");
        cmd("despawn", CAT_CHARACTER, "<who:char>",
                "Убирает персонажа из мира.");
        cmd("move", CAT_CHARACTER, "<who:char> to <target:target> [speed <speed:number>]",
                "Персонаж идёт к цели (точка, player, персонаж или моб); сюжет ждёт, пока он дойдёт. Скорость одна для всех персонажей: "
                        + "1 — обычный шаг (2.6 бл/с), 0.5 — медленно, от 1.6 — быстрый шаг, от 2 — бег (по умолчанию 1).");
        cmd("path", CAT_CHARACTER, "<who:char> <points:loc...> [speed <speed:number>]",
                "Персонаж проходит маршрут через несколько точек. Скорость одна для всех: 1 — обычный шаг (2.6 бл/с), 0.5 — медленно, от 1.6 — быстрый шаг, от 2 — бег.");
        cmd("walk", CAT_CHARACTER, "<who:char> <path:path> [speed <speed:number>] [from <from:number>] [loop] [pingpong] [nowait]",
                "Персонаж идёт по пути из папки «Пути» — от точки к точке, с паузами и анимациями точек. Сюжет ждёт, пока он дойдёт до конца "
                        + "(с nowait — сразу дальше). speed — одна для всех персонажей: 1 — обычный шаг (2.6 бл/с), 0.5 — медленно, от 1.6 — быстрый шаг, от 2 — бег. loop — по кругу, pingpong — туда-обратно (бесконечно, сюжет не ждёт). from 3 — начать с 3-й точки, "
                        + "from 0 — с ближайшей. Конец пути — событие pathend.");
        cmd("stopwalk", CAT_CHARACTER, "<who:char>", "Останавливает персонажа, идущего по пути.");
        cmd("look", CAT_CHARACTER, "<who:char> at <target:target> [always]",
                "Персонаж смотрит на цель: player, другой персонаж, моб (\"minecraft:zombie\" или \"mob\" — ближайший) или точка.\n"
                        + "С флагом always продолжает следить за целью, пока не скажете stoplook.");
        cmd("stoplook", CAT_CHARACTER, "<who:char>",
                "Персонаж перестаёт следить взглядом (после look ... always).");
        cmd("follow", CAT_CHARACTER, "<who:char> [target <target:target>] [speed <speed:number>] [distance <min:number> <max:number>] [noteleport]",
                "Персонаж следует за целью: игрок (по умолчанию) или другой персонаж. distance — ближе min не подходит; "
                        + "дальше max (6) отставший персонаж телепортируется к игроку. noteleport — никогда не телепортироваться, только идти: "
                        + "follow alice speed 1.3 noteleport.");
        cmd("walkspeed", CAT_CHARACTER, "<who:char> <speed:number>",
                "Скорость ходьбы персонажа.");
        cmd("wander", CAT_CHARACTER, "<who:char> [speed <speed:number>] [radius <radius:number>]",
                "Персонаж бродит вокруг случайным образом.");
        cmd("sit", CAT_CHARACTER, "<who:char>",
                "Персонаж садится.");
        cmd("stand", CAT_CHARACTER, "<who:char>",
                "Персонаж встаёт.");
        cmd("name", CAT_CHARACTER, "<who:char> <name:text>",
                "Меняет отображаемое имя персонажа.");
        cmd("hold", CAT_CHARACTER, "<who:char> <item:item>",
                "Даёт персонажу предмет в руку.");
        cmd("health", CAT_CHARACTER, "<who:char> <hp:number>",
                "Максимальное здоровье персонажа.");
        cmd("free", CAT_CHARACTER, "<who:char>",
                "Отпускает персонажа из-под контроля сюжета.");

        // ------------------------------------------------------------ animations
        cmd("anim", CAT_ANIM, "<who:char> <anim:anim> [layer <layer:layer>] [speed <speed:number>] [fade <fade:duration>] [times <times:int>] [loop] [hold] [wait]",
                "Проигрывает анимацию на персонаже.\n"
                        + "layer — слой (по умолчанию action: всё тело, перекрывает стойку; 0–20 — накладываются поверх),\n"
                        + "speed — скорость (1 — обычная), fade — плавный переход, times — проиграть N раз и остановить (сюжет ждёт),\n"
                        + "loop — зациклить, hold — замереть на последнем кадре, wait — дождаться конца анимации.\n"
                        + "Пример: anim alice \"st2/cathand\" layer 3 times 2 wait");
        cmd("face", CAT_ANIM, "<who:char> <anim:anim> [fade <fade:duration>]",
                "Мимика: анимация на слое лица (emotion). Пример: face alice \"st2/emotions/angry\"");
        cmd("stopanim", CAT_ANIM, "<who:char> [layer <layer:layer>] [fade <fade:duration>]",
                "Останавливает анимацию на слое (по умолчанию action).");
        cmd("clearanims", CAT_ANIM, "<who:char> [fade <fade:duration>]",
                "Останавливает все анимации персонажа, включая мимику.");
        cmd("moveanim", CAT_ANIM, "<who:char> <state:movetype> <anim:anim>",
                "Заменяет стандартную анимацию состояния: idle — стойка, walk — ходьба, fastwalk — быстрая ходьба, run — бег.");
        cmd("resetanim", CAT_ANIM, "<who:char>",
                "Возвращает стандартные анимации стойки и ходьбы.");
        cmd("nod", CAT_ANIM, "<who:char> [times <times:int>]",
                "Жест: персонаж кивает.");
        cmd("nope", CAT_ANIM, "<who:char> [times <times:int>]",
                "Жест: персонаж качает головой «нет».");
        cmd("laugh", CAT_ANIM, "<who:char> [times <times:int>] [speed <speed:number>]",
                "Жест: персонаж смеётся.");
        cmd("sigh", CAT_ANIM, "<who:char>",
                "Жест: персонаж вздыхает.");
        cmd("shake", CAT_ANIM, "<who:char> <time:duration>",
                "Жест: персонаж дрожит.");

        // ------------------------------------------------------------------ GUI
        cmd("gui open", CAT_GUI, "<name:gui>",
                "Открывает игроку интерфейс из папки «Интерфейсы» проекта. Сюжет идёт дальше; чтобы дождаться кнопки — wait button.");
        cmd("gui close", CAT_GUI, "",
                "Закрывает открытый интерфейс.");
        cmd("gui text", CAT_GUI, "<el:guiel> <text:text>",
                "Меняет текст элемента (текст, кнопка, поле, подпись полосы). Поддерживает подстановки: \"Здоровье: {hp}\"");
        cmd("gui show", CAT_GUI, "<el:guiel>",
                "Показывает скрытый элемент.");
        cmd("gui hide", CAT_GUI, "<el:guiel>",
                "Скрывает элемент.");
        cmd("gui progress", CAT_GUI, "<el:guiel> <value:expr>",
                "Значение полосы от 0 до 1. Пример: gui progress \"hp\" hp / 20");
        cmd("gui image", CAT_GUI, "<el:guiel> <image:image>",
                "Меняет картинку элемента.");
        cmd("gui scroll", CAT_GUI, "<el:guiel> <y:expr>",
                "Прокручивает панель прокрутки до позиции (в пикселях от начала). Пример: gui scroll \"list\" 0");
        cmd("gui set", CAT_GUI, "<el:guiel> <prop:text> <value:text>",
                "Меняет любое свойство элемента, как в инспекторе редактора: gui set \"title\" \"color\" \"#FFFF5555\"");
        cmd("wait button", CAT_GUI, "[<buttons:guiel...>]",
                "Ждёт нажатия кнопки интерфейса (любой или из списка). Какую нажали — button(). Пример: wait button \"yes\" \"no\"");
        cmd("overlay show", CAT_GUI, "<name:gui>",
                "Показывает интерфейс поверх игры как наложение (свой HUD: счётчики, подсказки, таймеры). Мышь и клавиатура остаются у игрока.\n"
                        + "Менять его можно теми же командами: gui text, gui progress, gui move... Для привязки к краям экрана поставьте интерфейсу масштаб «screen».");
        cmd("overlay hide", CAT_GUI, "[<name:gui>]",
                "Убирает наложение (без имени — все наложения сюжета).");
        cmd("gui move", CAT_GUI, "<el:guiel> x <x:expr> y <y:expr> [time <time:duration>]",
                "Двигает элемент (X, Y от точки привязки, можно в процентах: \"50%\"). С time — плавно. Пример: gui move \"card\" x 0 y -40 time 0.5s");
        cmd("gui resize", CAT_GUI, "<el:guiel> w <w:expr> h <h:expr> [time <time:duration>]",
                "Меняет размер элемента, можно плавно. Пример: gui resize \"bar\" w hp * 10 h 8 time 1s");
        cmd("gui animate", CAT_GUI, "<el:guiel> <prop:text> <value:expr> [time <time:duration>]",
                "Плавно меняет любое числовое свойство: opacity, rotation, value, x, y... Пример: gui animate \"logo\" \"rotation\" 360 time 2s");
        // game events: wait <event> [filter] and on <event> [filter] { }
        for (EventDefs.Def d : EventDefs.ALL.values()) {
            cmd("wait " + d.name(), CAT_WAIT, EventDefs.syntax(d), "Ждёт: " + d.title() + ". " + d.doc()
                    + (d.filter() != null ? "\nФильтр (" + d.filterLabel() + ") необязателен." : "")
                    + "\nДанные — event(\"поле\"): " + EventDefs.fieldsDoc(d)
                    + "\nВ фоне при каждом событии: on " + d.name() + (d.filter() != null ? " ..." : "") + " { ... }");
        }
        // ---- camera and cutscenes
        cmd("cutscene play", CAT_CAMERA, "<name:cutscene> [at <pos:target>] [nowait]",
                "Проигрывает катсцену из папки «Катсцены». Сюжет ждёт её конца (или пропуска), с nowait — идёт дальше.\n"
                        + "at — точка отсчёта для катсцен с относительными координатами.");
        cmd("cutscene stop", CAT_CAMERA, "", "Обрывает катсцену и плавно возвращает камеру игроку.");
        cmd("camera at", CAT_CAMERA, "<pos:target> [look <look:target>] [yaw <yaw:number>] [pitch <pitch:number>] [time <time:duration>] [ease <ease:ease>]",
                "Ставит камеру в точку (у персонажа или игрока — на уровне глаз). С time — плавный перелёт. look — следить взглядом за целью.\n"
                        + "Пример: camera at (10, 70, 5) look alice time 3s ease ease_in_out_sine");
        cmd("camera look", CAT_CAMERA, "<target:target> [time <time:duration>] [ease <ease:ease>]",
                "Камера поворачивается к цели и продолжает за ней следить.");
        cmd("camera turn", CAT_CAMERA, "yaw <yaw:expr> pitch <pitch:expr> [time <time:duration>] [ease <ease:ease>]",
                "Поворачивает камеру на заданные углы (yaw — по горизонтали, pitch — вверх/вниз).");
        cmd("camera follow", CAT_CAMERA, "<who:target> [distance <distance:number>] [height <height:number>] [angle <angle:number>]",
                "Камера следует за целью сзади (angle 180 — спереди, 90 — сбоку).");
        cmd("camera orbit", CAT_CAMERA, "<who:target> [radius <radius:number>] [height <height:number>] [speed <speed:number>]",
                "Камера облетает цель по кругу (speed — градусов в секунду, минус — в другую сторону).");
        cmd("camera reset", CAT_CAMERA, "[time <time:duration>]", "Плавно возвращает камеру игроку.");
        cmd("camera fov", CAT_CAMERA, "<value:expr> [time <time:duration>] [ease <ease:ease>]",
                "Угол обзора (зум): 30 — приближение, 70 — обычно, 110 — широкий угол.");
        cmd("camera roll", CAT_CAMERA, "<value:expr> [time <time:duration>] [ease <ease:ease>]", "Наклон камеры набок, в градусах (0 — ровно).");
        cmd("camera shake", CAT_CAMERA, "<value:expr> [time <time:duration>]", "Тряска камеры: сила 0..1, затухает за time (по умолчанию 1 с).");
        cmd("camera bars", CAT_CAMERA, "<value:expr> [time <time:duration>]",
                "Кинополосы сверху и снизу: доля высоты экрана, 0.11 — как в кино, 0 — убрать.");
        cmd("camera fade", CAT_CAMERA, "<value:expr> [color <color:text>] [time <time:duration>]",
                "Затемнение экрана: 1 — полностью, 0 — снять. color — цвет (\"#000000\", \"#FFFFFF\").");
        cmd("camera hud", CAT_CAMERA, "<value:bool>", "Показать (true) или спрятать (false) интерфейс игрока.");
        cmd("camera hide", CAT_CAMERA, "<what:text>",
                "Какой интерфейс скрыть: \"all\" — весь, \"minecraft\" — хотбар, сердца, прицел, \"storytelling\" — HP и монеты Spraute, "
                        + "\"custom\" — свои наложения (overlay), \"none\" — ничего. Речь и титры видны всегда.");
        cmd("time set", CAT_PLAYER, "<value:expr>",
                "Время суток: число 0..23999 или day, noon, sunset, night, midnight, sunrise. День не сбивается: time set night.");
        cmd("time add", CAT_PLAYER, "<value:expr>", "Сдвигает время вперёд (тики, 1000 — около 50 секунд дня): time add 6000.");
        cmd("time speed", CAT_PLAYER, "<value:expr>", "Скорость дня: 1 — обычная, 3 — втрое быстрее, 0.2 — медленно. Сохраняется в настройках мира.");
        cmd("time freeze", CAT_PLAYER, "[at <value:expr>]", "Останавливает время (at — сначала поставить: time freeze at noon).");
        cmd("time unfreeze", CAT_PLAYER, "", "Время снова идёт.");
        cmd("shop open", CAT_GUI, "<name:shop> [with <who:target>]",
                "Открывает магазин из папки «Магазины»: shop open \"лавка\" with alice. Покупки ловит on purchase \"лавка\" { event(\"offer\") }.");
        cmd("shop restock", CAT_GUI, "<name:shop>", "Возвращает весь запас товаров магазина.");
        cmd("shop stock", CAT_GUI, "<name:shop> <offer:text> <value:expr>", "Сколько товара осталось: shop stock \"лавка\" \"apple\" 5 (-1 — без конца).");
        cmd("shop close", CAT_GUI, "", "Закрывает окно магазина.");
        cmd("fight", CAT_CHARACTER, "<who:char> [style <style:fight>] [target <target:target>]",
                "Персонаж начинает драться: догоняет цель (по умолчанию игрока), бьёт атаками стиля, блокирует, уклоняется, падает без сил. "
                        + "Стиль — из папки «Стили боя» или готовый: \"fists\" (кулачный бой), \"sword\" (мечник), \"brute\" (громила), \"weak\" (слабак). "
                        + "fight alice style \"sword\" target bob — драться с другим персонажем.");
        cmd("fight stop", CAT_CHARACTER, "<who:char>", "Персонаж прекращает бой.");
        cmd("fight heal", CAT_CHARACTER, "<who:char> [<value:expr>]", "Возвращает здоровье боя (всё или сколько указано); поверженный встаёт и дерётся дальше.");
        cmd("fight tune", CAT_BOSS, "<who:target> [armor <armor:number>] [speed <speed:number>] [power <power:number>] [stagger <stagger:number>] "
                        + "[block <block:number>] [dodge <dodge:number>]",
                "Меняет идущий бой на ходу — например, вторая фаза босса. armor — какая доля урона гасится (0..1), speed — скорость погони (1 — шаг, 1.5 — бег), "
                        + "power — множитель урона его ударов (2 — вдвое сильнее), stagger — шанс вздрогнуть от удара (0 — не сбивается), "
                        + "block / dodge — шансы блока и уворота (0..1). Не указанное не меняется.\n"
                        + "if hpfrac(boss) < 0.3 { fight tune boss speed 1.8 power 1.5 stagger 0 }");
        cmd("bossbar show", CAT_BOSS, "<who:target> [title <title:text>] [color <color:text>] [style <style:text>]",
                "Полоса босса сверху экрана, которая сама следит за здоровьем: в бою (fight) — за боевым, иначе — за обычным. "
                        + "Сама исчезает, когда тот погиб, повержен или пропал. color: red, pink, blue, green, yellow, purple, white; "
                        + "style: \"6\", \"10\", \"12\", \"20\" — полоса на делениях. bossbar show boss title \"Тихий Звонарь\" color \"purple\"");
        cmd("bossbar hide", CAT_BOSS, "<who:target>", "Убирает полосу босса.");
        cmd("leap", CAT_BOSS, "<who:target> to <to:target> [height <height:number>] [time <time:duration>] [impact <impact:vfx>] "
                        + "[radius <radius:number>] [damage <damage:number>] [push <push:number>] [nowait]",
                "Прыжок по дуге в точку или к цели (приземляется на пол рядом). height — высота дуги (3), time — сколько в воздухе (0.8s). "
                        + "При приземлении: impact — эффект, radius — ударная волна этого радиуса (0 — без неё), damage — урон, push — отбрасывание (1.2). "
                        + "Пока летит, бой (fight) его не ведёт. Сюжет ждёт приземления, с nowait — сразу дальше.\n"
                        + "leap boss to player height 5 time 1s impact \"explosion\" radius 4 damage 6");
        cmd("charge", CAT_BOSS, "<who:target> to <to:target> [speed <speed:number>] [distance <distance:number>] [damage <damage:number>] "
                        + "[push <push:number>] [trail <trail:vfx>] [nowait]",
                "Таран: несётся по прямой в сторону цели (по земле) и бьёт каждого на пути один раз. speed — блоков в секунду (14), "
                        + "distance — сколько пробежать (по умолчанию до цели и ещё 2 блока), damage — урон (4), push — отбрасывание (1), "
                        + "trail — эффект-след. Стена или обрыв останавливают. Сюжет ждёт конца, с nowait — сразу дальше.\n"
                        + "charge boss to player speed 18 damage 5 trail \"smoke\"");
        cmd("circle", CAT_BOSS, "<who:target> around <center:target> [radius <radius:number>] [speed <speed:number>] [time <time:duration>] [nowait]",
                "Ходит кругом вокруг цели или точки: radius (5), speed — градусов в секунду (90, минус — в другую сторону), time — сколько (3s). "
                        + "Смотрит на центр. Пока кружит, бой его не ведёт — удобно для «кружит и стреляет». Сюжет ждёт, с nowait — сразу дальше.");
        cmd("part", CAT_BOSS, "<who:target> <name:text> [enabled <enabled:bool>] [damage <damage:number>] [attack <attack:number>]",
                "Часть хитбокса персонажа (их задают в редакторе сущности): enabled false — выключить (удары проходят мимо), true — включить; "
                        + "damage — множитель урона по ней для этого персонажа (2 — уязвимое место, 0 — броня, удар только звенит); "
                        + "attack — часть сама ранит всех, кого касается (урон раз в полсекунды), attack 0 — перестаёт.\n"
                        + "part boss \"панцирь\" enabled false  ·  part boss \"глаз\" damage 3  ·  part boss \"лезвие\" attack 4");
        cmd("attack", CAT_BOSS, "<who:target> [part <part:text>] [size <w:number> <h:number> <d:number>] [offset <right:number> <up:number> <forward:number>] "
                        + "[delay <delay:duration>] [time <time:duration>] [damage <damage:number>] [push <push:number>] [impact <impact:vfx>] "
                        + "[except <except:target>] [show] [nowait]",
                "Удар хитбоксом: через delay (0) на время time (0.3s) появляется коробка удара, и все, кого она касается, получают damage (4) "
                        + "и отбрасываются (push 0.5) — каждый один раз за удар. Коробка: size ширина высота глубина (2 2 2) со сдвигом offset "
                        + "вправо вверх вперёд (0 1 1.5) от ног, повёрнута вместе с телом; или part \"имя\" — часть сущности spraute:… "
                        + "(едет на своей кости в анимации, может быть выключена для ударов по ней). show — показать коробку частицами (для настройки). "
                        + "Сюжет ждёт конца удара, если нет nowait.\n"
                        + "anim boss \"удар\"  ·  attack boss part \"клешня\" delay 0.4s time 0.3s damage 8 impact \"explosion\"\n"
                        + "attack alice size 2 1.5 2.5 offset 0 1 1.5 damage 5 show");
        cmd("stopmove", CAT_BOSS, "<who:target>", "Обрывает прыжок, таран или кружение и гасит его лучи (beam). Бой снова ведёт его сам.");
        cmd("strike", CAT_BOSS, "at <pos:target> [radius <radius:number>] [delay <delay:duration>] [damage <damage:number>] [push <push:number>] "
                        + "[warn <warn:vfx>] [impact <impact:vfx>] [by <by:target>] [except <except:target>]",
                "Отложенный удар по месту — «телеграф» босса: эффект warn играет сразу (метка на земле), а через delay (1s) — эффект impact и удар: "
                        + "всех в radius (2.5) отбрасывает (push 0.8) и ранит (damage 4). by — кто бьёт (его не задевает), except — кого ещё не трогать. "
                        + "Сюжет не ждёт — можно поставить много ударов подряд.\n"
                        + "for a in range(0, 360, 45) { strike at around(boss, 5, a) delay 1.2s warn \"метка\" impact \"explosion\" by boss }");
        cmd("beam", CAT_BOSS, "from <from:target> to <to:target> [time <time:duration>] [damage <damage:number>] [range <range:number>] "
                        + "[width <width:number>] [track <track:number>] [particle <particle:text>] [impact <impact:vfx>] [wait]",
                "Луч из глаз персонажа к цели: блоки его обрывают, всех, кого касается, жжёт (damage — урон в секунду, 6). time — сколько горит (2s), "
                        + "range — дальность (24), width — толщина (0.3), track — как быстро поворачивается за движущейся целью, градусов в секунду "
                        + "(0 — сразу; 40 — медленно, от луча можно убежать), particle — частицы луча (end_rod, flame, soul_fire_flame, electric_spark…), "
                        + "impact — эффект там, куда упирается. Сюжет не ждёт, с wait — ждёт, пока луч погаснет.\n"
                        + "beam from boss to player time 3s damage 8 track 35 particle \"soul_fire_flame\"");
        cmd("wait defeated", CAT_WAIT, "<who:char>", "Ждёт, пока персонаж будет повержен в бою.");
        cmd("zone on", CAT_PLAYER, "<name:zone>", "Включает зону из папки «Зоны»: она снова слышит игрока и держит стену.");
        cmd("zone off", CAT_PLAYER, "<name:zone>", "Выключает зону: ни событий, ни стены.");
        cmd("zone wall", CAT_PLAYER, "<name:zone> <mode:text>",
                "Стена по краю зоны: \"in\" — не выпускать изнутри, \"out\" — не пускать внутрь, \"none\" — убрать. zone wall \"арена\" \"in\".");
        cmd("wait zone", CAT_WAIT, "<name:zone>", "Ждёт, пока игрок войдёт в зону: wait zone \"ворота\".");
        cmd("wait outside", CAT_WAIT, "<name:zone>", "Ждёт, пока игрок выйдет из зоны.");
        cmd("quest start", CAT_GUI, "<name:quest>",
                "Выдаёт квест из папки «Квесты»: он появляется в журнале (J) и в углу экрана. Выполненный или проваленный — начнётся заново.");
        cmd("quest progress", CAT_GUI, "<name:quest> <task:text> [<value:expr>]",
                "Продвигает задачу: quest progress \"ворота\" \"zombies\" (на 1) или ... \"planks\" 4. Когда сделаны все обязательные задачи, квест выполняется сам.");
        cmd("quest set", CAT_GUI, "<name:quest> <task:text> <value:expr>", "Ставит, сколько сделано: quest set \"ворота\" \"talk\" 1.");
        cmd("quest reveal", CAT_GUI, "<name:quest> <task:text>", "Открывает скрытую задачу в журнале.");
        cmd("quest done", CAT_GUI, "<name:quest>", "Квест выполнен: выдаётся награда.");
        cmd("quest fail", CAT_GUI, "<name:quest>", "Квест провален.");
        cmd("quest reset", CAT_GUI, "<name:quest>", "Забыть квест, будто его не выдавали.");
        cmd("quest track", CAT_GUI, "<name:quest>", "Показывать этот квест в углу экрана.");
        cmd("quest journal", CAT_GUI, "", "Открывает журнал квестов (как клавиша J).");
        cmd("hitbox", CAT_CHARACTER, "<who:target> <modes:text...>",
                "Как ведёт себя тело персонажа (или игрока, моба). Столкновения: \"normal\" — как обычно, \"solid\" — твёрдый как стена "
                        + "(не пройти, можно встать сверху, не сдвинуть), \"fixed\" — не сдвинуть, \"ghost\" — проходят насквозь. "
                        + "Удары: \"damage\" — урон проходит, \"nodamage\" — не проходит, \"mortal\" — урон проходит и может погибнуть, "
                        + "\"immortal\" — не погибнет. \"nopick\" — нельзя навестись и ударить рукой, \"pick\" — снова можно. "
                        + "hitbox alice \"solid\" \"damage\". При объявлении: character alice = … hitbox \"ghost\".");
        cmd("namecolor", CAT_CHARACTER, "<who:target> <color:text>",
                "Цвет имени в речи и в истории сообщений: namecolor alice \"#FF8800\" или \"gold\", \"красный\"… "
                        + "\"reset\" — свой цвет убран (тогда общий из настроек движка → Чат). Сразу можно задать при объявлении: character alice = … color \"gold\".");
        cmd("skin", CAT_CHARACTER, "<who:target> <skin:image>",
                "Меняет скин персонажа или игрока: картинка проекта (skin alice \"Алиса в пальто\") или текстура игры "
                        + "(skin alice \"st_addon_spraute:textures/entity/humanoid.png\"). skin alice \"reset\" — вернуть обычный.");
        cmd("summon", CAT_PLAYER, "<type:entity> at <pos:target> [into <var:var>]",
                "Призывает сущность. into — переменная, куда положить её (если там список — добавит в него): summon \"minecraft:zombie\" at (0, 64, 0) into enemies.");
        cmd("setblock", CAT_PLAYER, "<pos:target> <block:text>",
                "Ставит блок со свойствами, как /setblock: setblock (1, 64, 2) \"minecraft:lever[facing=south,powered=true]\".");
        cmd("setprop", CAT_PLAYER, "<pos:target> <prop:text> <value:expr>",
                "Меняет одно свойство блока, который уже стоит: setprop (1, 64, 2) \"facing\" \"south\", setprop door \"open\" true.");
        cmd("emit", CAT_LOGIC, "<name:text> [with <value:expr>]",
                "Посылает своё событие во все сюжеты: emit \"волна\" with 3. Принимают его on signal \"волна\" { event(\"value\") } или wait signal \"волна\".");
        cmd("shockwave", CAT_FX, "at <pos:target> [radius <radius:number>] [power <power:number>] [damage <damage:number>] [except <except:target>]",
                "Ударная волна: всех живых в радиусе (6) отбрасывает от центра и чуть вверх (power 1.2), damage — урон. "
                        + "Если центр — существо (shockwave at player), оно само не задевается. except — кого ещё не трогать.");
        cmd("explode", CAT_FX, "at <pos:target> [power <power:number>] [fire] [break]",
                "Взрыв (power 3 — как крипер). Без break блоки не ломаются, fire — поджигает.");
        cmd("lightning", CAT_FX, "at <pos:target> [harmless]", "Молния в точку (harmless — только вспышка и гром, без урона и огня).");
        cmd("bar set", CAT_GUI, "<id:text> <value:expr> [max <max:expr>] [title <title:text>] [color <color:text>]",
                "Полоса сверху экрана (как у босса): мана, заряд, время… bar set \"мана\" mana max 100 title \"Мана\" color \"blue\". "
                        + "Цвета: purple, blue, red, green, yellow, white, pink.");
        cmd("bar hide", CAT_GUI, "<id:text>", "Убирает полосу.");
        cmd("actionbar", CAT_SPEECH, "<text:text>", "Короткая надпись над хотбаром.");
        cmd("notify", CAT_SPEECH, "<text:text> [item <item:item>] [icon <icon:image>] [silent]",
                "Уведомление Spraute слева вверху. Иконка — предмет (notify \"Получен ключ\" item \"minecraft:tripwire_hook\") "
                        + "или картинка: проекта (icon \"ключ\") или игры (icon \"minecraft:textures/item/diamond.png\"). silent — без звука.");
        cmd("cast", CAT_FX, "<name:vfx> [from <from:target>] [to <to:target>] [speed <speed:number>] [damage <damage:number>] [range <range:number>] "
                        + "[gravity <gravity:number>] [size <size:number>] [push <push:number>] [impact <impact:vfx>] [scale <scale:number>] [pierce] [id <id:text>] "
                        + "[count <count:int>] [spread <spread:number>] [ring]",
                "Снаряд: эффект летит и бьёт первого, в кого попал (или в блок). Откуда — from (по умолчанию игрок, из глаз), куда — to (цель или точка) "
                        + "или туда, куда смотрит from. speed — блоков в секунду (20), damage — урон (4), range — дальность (48), gravity — падение (0 — летит прямо), "
                        + "size — толщина для попадания (0.4), push — отбрасывание, impact — эффект на месте попадания, pierce — летит сквозь существ.\n"
                        + "cast \"fireball\" impact \"explosion\" damage 6 id \"огонь\" — и on casthit \"огонь\" { … }.\n"
                        + "Залпом: count — сколько снарядов, spread — веер в градусах (count 5 spread 60 — пять веером на 60°), "
                        + "ring — кольцом во все стороны по горизонтали (cast \"magic_orb\" from boss count 12 ring).");
        cmd("fx play", CAT_FX, "<name:vfx> at <pos:target> [scale <scale:number>] [follow] [id <id:text>]",
                "Эффект частиц: свой из папки «Эффекты» или встроенный (nuke — ядерный взрыв, sky_beam — луч в небо, orbit_orbs — орбы вокруг, "
                        + "fire — огонь, standing_portal — стоячий портал, explosion, magic_circle, portal, fire_tornado, lightning, fireworks, heal, aura, smoke, sparkles).\n"
                        + "scale — размер (2 — вдвое больше), follow — едет за целью, id — имя, чтобы потом остановить: fx stop \"id\".");
        cmd("fx stop", CAT_FX, "<id:text>", "Плавно гасит эффект, запущенный с этим id.");
        cmd("fx clear", CAT_FX, "", "Гасит все эффекты.");
        cmd("camera lock", CAT_CAMERA, "<value:bool>", "Запретить (true) или разрешить (false) игроку ходить и смотреть по сторонам.");
        cmd("orb spawn", CAT_PLAYER, "<id:text> at <pos:target> [count <count:expr>] [value <value:expr>] [coins <coins:expr>] [xp <xp:expr>] "
                        + "[image <image:image>] [item <item:item>] [color <color:text>] [size <size:number>] [radius <radius:number>] "
                        + "[life <life:duration>] [spread <spread:number>] [nopickup] [float]",
                "Создаёт частицы сюжета, как шарики опыта: они летят к игроку и подбираются (событие on orb \"id\").\n"
                        + "coins — сколько монет даёт каждая, xp — сколько опыта, value — своя ценность (event(\"amount\")).\n"
                        + "Вид: image — картинка проекта, item — предмет, иначе светящийся шарик цвета color (\"#66CCFF\"). size — размер в блоках.\n"
                        + "radius — с какого расстояния притягиваются, life — сколько живут (0 — всегда), spread — разлёт.\n"
                        + "nopickup — только украшение (искры, пыльца), float — парят без гравитации.\n"
                        + "Пример: orb spawn \"gem\" at alice count 5 coins 1 color \"#7FE0FF\" spread 1");
        cmd("orb clear", CAT_PLAYER, "[<id:text>]",
                "Убирает частицы сюжета (все или с этим именем).");
        cmd("particles", CAT_SCREEN, "<type:text> at <pos:target> [count <count:expr>] [spread <spread:number>] [speed <speed:number>]",
                "Частицы Minecraft: \"minecraft:flame\", \"heart\", \"happy_villager\", \"end_rod\", \"cloud\", \"enchant\"... Пример: particles \"heart\" at alice count 8 spread 0.5");
        cmd("coins give", CAT_PLAYER, "<amount:expr>", "Даёт игроку монеты.");
        cmd("coins take", CAT_PLAYER, "<amount:expr>", "Забирает у игрока монеты (не меньше нуля).");
        cmd("coins set", CAT_PLAYER, "<amount:expr>", "Задаёт число монет игрока.");
        cmd("coins drop", CAT_PLAYER, "<amount:expr> at <pos:target> [count <count:expr>]",
                "Роняет монеты движка Spraute (их сферы), игрок их подбирает. count — на сколько сфер разделить.");
        cmd("gui slot", CAT_GUI, "<el:guiel> <item:item> [count <count:expr>]",
                "Кладёт предмет в слот интерфейса (заменяет то, что там было). Пример: gui slot \"reward\" \"minecraft:diamond\" count 3");
        cmd("gui clearslot", CAT_GUI, "<el:guiel>",
                "Очищает слот: предмет пропадает (например, игрок его «сдал»).");
        cmd("wait slot", CAT_GUI, "[<slots:guiel...>]",
                "Ждёт, пока игрок положит предмет в слот или заберёт его (любой слот или из списка). Какой — lastslot()");
        cmd("wait guiclose", CAT_GUI, "",
                "Ждёт, пока игрок закроет интерфейс.");

        // ---------------------------------------------------------- player/world
        cmd("enable", CAT_PLAYER, "<sys:system>",
                "Включает систему движка: health (полоска HP), coins (монеты), time (время суток), inventory (свой инвентарь), slots (блокировка слотов).\n"
                        + "Настройка сохраняется в мире, как в «Настройках движка».");
        cmd("disable", CAT_PLAYER, "<sys:system>",
                "Выключает систему движка: health, coins, time, inventory или slots. Пример: disable slots");
        cmd("teleport", CAT_PLAYER, "<pos:target>",
                "Телепортирует игрока к цели: точка, персонаж или моб.");
        cmd("effect", CAT_PLAYER, "<effect:effect> <time:duration> [level <level:int>]",
                "Накладывает эффект на игрока.");
        cmd("sound", CAT_PLAYER, "<sound:sound> [at <pos:loc>] [volume <volume:number>] [pitch <pitch:number>] [loop] [id <id:text>]",
                "Проигрывает звук (по умолчанию у игрока): звук игры (\"minecraft:block.bell.use\") или свой из папки «Звуки» проекта по имени "
                        + "(\"Колокол\", файлы .ogg и .wav). at — в точке мира (стихает с расстоянием; только моно-файлы), volume и pitch — громкость "
                        + "и высота (1 — как есть), loop — по кругу (свои звуки), id — имя, чтобы остановить: stopsound id \"музыка\"");
        cmd("stopsound", CAT_PLAYER, "[id <id:text>]",
                "Останавливает свой звук, запущенный с id, или без id — все свои звуки, играющие по кругу.");
        cmd("give", CAT_PLAYER, "<item:item> [count <count:int>]",
                "Выдаёт игроку предмет.");
        cmd("command", CAT_PLAYER, "<command:text>",
                "Выполняет команду Minecraft от имени сервера. Пример: command \"time set night\"");

        // ---------------------------------------------------------------- screen
        cmd("blackout", CAT_SCREEN, "<time:duration>",
                "Затемнение экрана.");
        cmd("flash", CAT_SCREEN, "<time:duration>",
                "Белая вспышка.");
        cmd("screen", CAT_SCREEN, "<r:int> <g:int> <b:int> <a:int>",
                "Заливает экран цветом (0–255 на канал, a — прозрачность).");
        cmd("eyes", CAT_SCREEN, "<close:duration> <hold:duration> <open:duration>",
                "Эффект закрывающихся глаз: закрыть, подержать, открыть.");

        // ----------------------------------------------------------------- logic
        cmd("js", CAT_LOGIC, "<code:text>",
                "Выполняет JavaScript: js { … } (многострочный код с фигурными скобками) или js \"код в одну строку\". Видны story (переменные "
                        + "сюжета: story.get(\"x\"), story.set(\"x\", 5), story.character(\"alice\"), story.log(…)), player (x y z, health, give(…), "
                        + "effect(…), nbt(…)…) и world (command(…), block(x,y,z), setBlock(…), entities(…), summon(…), time, hasMod(…)). "
                        + "Классы Java — только если оператор мира разрешил: /sprautejs unsafe on.");
        cmd("setnbt", CAT_LOGIC, "<target:target> <path:text> <value:expr>",
                "Меняет NBT существа или блока с данными (сундук…): setnbt kent \"NoAI\" 1, setnbt (1, 64, 2) \"Items[0].Count\" 5. "
                        + "Значение может быть SNBT: \"5b\", \"{CustomName:'\\\"Имя\\\"'}\".");
        cmd("setattr", CAT_LOGIC, "<target:target> <attr:text> <value:number>",
                "Атрибут существа: setattr kent \"generic.attack_damage\" 8 (также generic.movement_speed, generic.max_health, атрибуты модов).");
        cmd("setscore", CAT_LOGIC, "<target:target> <objective:text> <value:number> [add]",
                "Очки табло (scoreboard): setscore player \"kills\" 5; с add — прибавить. Табло создаётся, если его нет.");
        cmd("global set", CAT_LOGIC, "<name:text> <value:expr>",
                "Общая переменная всех сюжетов мира (сохраняется навсегда): global set \"золото\" 10. В другом скрипте: global(\"золото\").");
        cmd("global add", CAT_LOGIC, "<name:text> <value:expr>", "Прибавляет к общей переменной: global add \"золото\" 5 (или -5).");
        cmd("global clear", CAT_LOGIC, "<name:text>", "Удаляет общую переменную.");
        cmd("flag set", CAT_LOGIC, "<name:text> [value <value:text>]",
                "Устанавливает флаг сюжета.");
        cmd("flag clear", CAT_LOGIC, "<name:text>",
                "Снимает флаг сюжета.");
        cmd("checkpoint", CAT_LOGIC, "<name:text>",
                "Точка сохранения: при перезаходе сюжет продолжится отсюда.");
        cmd("restore", CAT_LOGIC, "<name:text>",
                "Возвращает сюжет к точке сохранения.");
        cmd("ensure active", CAT_LOGIC, "<ids:script...>",
                "Гарантирует, что указанные сюжеты запущены.");
        cmd("ensure inactive", CAT_LOGIC, "<ids:script...>",
                "Гарантирует, что указанные сюжеты остановлены.");
        cmd("log", CAT_LOGIC, "<text:text>",
                "Пишет строку в консоль редактора Spraute (для отладки).");

        // -------------------------------------------------------------- keywords
        KEYWORD_DOCS.put("script", "Заголовок файла: script <id> \"Название\". Id — уникальный номер сюжета.\n"
                + "script 5001 \"Вступление\" autostart — сюжет сам запускается, когда игрок заходит в мир (если он ещё не идёт).\n"
                + "script 5001 \"Вступление\" autostart once — только при первом входе в этот мир.");
        KEYWORD_DOCS.put("character", "Объявляет персонажа:\ncharacter alice = \"spraute:npc\" name \"Алиса\" [speed 0.3] [skin \"...\"] [icon \"лицо\"] [portrait \"лицо\"]\n"
                + "icon — картинка в окне речи (say), portrait — лицо в диалоге с выбором (choice).\n"
                + "Картинки добавляются в папку проекта «Картинки»; есть и встроенные: generic_man, anu_icon, blue_man, lo_icon, sky_eye.\n"
                + "character zeit = existing \"zeitheron\" — готовый персонаж ST.");
        KEYWORD_DOCS.put("location", "Объявляет точку: location camp = (100, 64, 200) [yaw 90] [pitch 0] [dim \"minecraft:overworld\"]");
        KEYWORD_DOCS.put("var", "Переменная сюжета: var count = 0");
        KEYWORD_DOCS.put("label", "Метка, на которую можно перейти через goto. Также видна в панели сюжетов (Перейти к метке).");
        KEYWORD_DOCS.put("goto", "Переход к метке: goto start");
        KEYWORD_DOCS.put("if", "Условие:\nif hp > 5 {\n    ...\n} else {\n    ...\n}");
        KEYWORD_DOCS.put("else", "Ветка «иначе» для if.");
        KEYWORD_DOCS.put("while", "Цикл, пока условие истинно:\nwhile not flag(\"done\") { ... }");
        KEYWORD_DOCS.put("repeat", "Повторить блок N раз: repeat 3 { ... }");
        KEYWORD_DOCS.put("for", "Перебор: for enemy in enemies { ... }. Список — каждый элемент; словарь — ключи; число N — 0..N-1; текст — буквы.\n"
                + "for i in range(1, 11) { say 0 \"{i}\" }");
        KEYWORD_DOCS.put("in", "for x in список { ... }");
        KEYWORD_DOCS.put("every", "Повторять в фоне с интервалом, пока идёт сюжет:\nevery 5t { if near(player, 3) { ... } }\n"
                + "on tick { ... } — каждый тик.");
        KEYWORD_DOCS.put("break", "Выйти из цикла for, while или repeat.");
        KEYWORD_DOCS.put("player", "Игрок. В тексте — его ник; как сущность — свойства и методы:\n"
                + "player.health, player.pos, player.look, player.item, player.level\nset player.health = 20\nplayer.give(\"minecraft:diamond\", 3)\n"
                + "Так же работают персонажи (alice.pos) и переменные с сущностями (boss.health).\n"
                + "Свойства: pos x y z eyes look yaw pitch speed onground name type health maxhealth target item alive dist sneaking sprinting level food fire glowing invisible inair flying canfly flyspeed movespeed falldistance fallsafe\n"
                + "Менять (set x.свойство = ...): health maxhealth name pos x y z yaw speed target ai glowing invisible gravity silent invulnerable fire item level food flying canfly flyspeed movespeed falldistance fallsafe\n"
                + "undying — не умирает по-настоящему: смертельный удар оставляет половину здоровья, а сюжет получает on death (set player.undying = true — бой без возрождения вдали)\n"
                + "Методы: kill() remove() tp(pos) damage(n) heal(n) effect(id, сек, ур) cleareffects() push(вектор) dash(сила) launch(сила) knockback(от, сила) pull(к, сила) stop() lookat(цель) give(item, n) equip(слот, item) distance(цель) has(item, n) count(item)");
        KEYWORD_DOCS.put("continue", "Сразу к следующему шагу цикла.");
        KEYWORD_DOCS.put("set", "Изменить переменную: set count = count + 1 (также += и -=)");
        KEYWORD_DOCS.put("choice", "Диалог с вариантами ответа:\nchoice alice \"Пойдёшь со мной?\" {\n    \"Да\" { ... }\n    \"Нет\" { ... }\n}\nПосле него answer() вернёт номер ответа (с 1).");
        KEYWORD_DOCS.put("parallel", "Параллельная ветка, идущая одновременно с основной (то же, что async с именем):\nparallel ambient { ... }");
        KEYWORD_DOCS.put("func", "Функция — кусок сюжета, который можно вызывать много раз:\nfunc greet(who) {\n    say alice \"Привет, {who}!\"\n}\nПараметры — обычные переменные сюжета. Объявляется на верхнем уровне файла.");
        KEYWORD_DOCS.put("call", "Вызов функции и ожидание её конца: call greet(\"Боб\")\nasync call greet(\"Боб\") as job — запустить в фоне.");
        KEYWORD_DOCS.put("return", "Досрочно выйти из функции.");
        KEYWORD_DOCS.put("async", "Асинхронно — не ждать завершения:\nasync { ... }          — фоновая ветка\nasync walk { ... }     — ветка с именем (для await/cancel)\nasync call f() as job  — функция в фоне");
        KEYWORD_DOCS.put("await", "Ждать завершения асинхронных веток: await walk, job");
        KEYWORD_DOCS.put("cancel", "Остановить асинхронную ветку: cancel walk");
        KEYWORD_DOCS.put("on", "Обработчик события — работает в фоне и срабатывает каждый раз:\non button \"yes\" { ... }\non key H { ... }\non click alice { ... }\n"
                + "Событием может быть любая команда wait: button, guiclose, key, click, chat, near, flag, trigger...\nСтавьте обработчики в начало сюжета.");
        KEYWORD_DOCS.put("switch", "Выбор по значению:\nswitch button() {\n    \"yes\" { ... }\n    \"no\", \"cancel\" { ... }\n    else { ... }\n}");
        KEYWORD_DOCS.put("stop", "Завершает сюжет.");
        KEYWORD_DOCS.put("player", "Игрок. Можно использовать как цель: look alice at player");
        KEYWORD_DOCS.put("and", "Логическое И");
        KEYWORD_DOCS.put("or", "Логическое ИЛИ");
        KEYWORD_DOCS.put("not", "Логическое НЕ");
        KEYWORD_DOCS.put("true", "Истина");
        KEYWORD_DOCS.put("false", "Ложь");

        // ------------------------------------------------------------- functions
        fn("random", "random(n)", "число", "Случайное целое от 0 до n-1.");
        fn("chance", "chance(percent)", "да/нет", "Истина с указанной вероятностью в процентах.");
        fn("flag", "flag(\"name\")", "да/нет", "Установлен ли флаг сюжета.");
        fn("walking", "walking(who)", "да/нет", "Идёт ли персонаж по пути (walk).");
        fn("js", "js(code)", "значение", "Значение JavaScript-кода: js(\"return player.health < 5\"), js(\"world.time\"). См. команду js.");
        fn("nbt", "nbt(target, path)", "значение", "NBT существа или блока с данными: nbt(kent, \"Health\"), nbt((1, 64, 2), \"Items[0].id\"). "
                + "Без пути — весь SNBT. Нет такого — \"\".");
        fn("attr", "attr(target, id)", "число", "Атрибут существа или игрока: attr(player, \"generic.max_health\").");
        fn("score", "score(who, objective)", "число", "Очки табло: score(player, \"kills\"). Нет — 0.");
        fn("cmdresult", "cmdresult(command)", "текст", "Выполняет команду Minecraft или мода и возвращает её ответ текстом: cmdresult(\"/time query daytime\").");
        fn("cmdvalue", "cmdvalue(command)", "число", "Выполняет команду и возвращает её числовой результат (как в /execute store): cmdvalue(\"/time query daytime\").");
        fn("hasmod", "hasmod(id)", "да/нет", "Установлен ли мод: hasmod(\"kubejs\").");
        fn("global", "global(\"name\", [default])", "значение", "Общая переменная всех сюжетов (global set). Если её нет — default или 0.");
        fn("hasglobal", "hasglobal(\"name\")", "да/нет", "Есть ли общая переменная.");
        fn("flagvalue", "flagvalue(\"name\")", "текст", "Значение флага сюжета.");
        fn("answer", "answer()", "число", "Номер ответа в последнем choice (с 1).");
        fn("near", "near(target, radius)", "да/нет", "Находится ли игрок рядом с точкой или персонажем.");
        fn("dist", "dist(target)", "число", "Расстояние от игрока до точки или персонажа.");
        fn("health", "health()", "число", "Здоровье игрока.");
        fn("daytime", "daytime()", "число", "Время суток в тиках (0–24000).");
        fn("active", "active(id)", "да/нет", "Запущен ли сюжет с указанным id.");
        fn("running", "running(\"ветка\")", "да/нет", "Выполняется ли сейчас асинхронная ветка.");
        fn("round", "round(x)", "число", "Округляет до целого.");
        fn("floor", "floor(x)", "число", "Округляет вниз.");
        fn("randint", "randint(a, b)", "число", "Случайное целое от a до b включительно.");
        fn("len", "len(text)", "число", "Длина текста.");
        fn("contains", "contains(text, part)", "да/нет", "Содержит ли текст фрагмент (без учёта регистра).");
        fn("upper", "upper(text)", "текст", "Текст заглавными буквами.");
        fn("lower", "lower(text)", "текст", "Текст строчными буквами.");
        fn("num", "num(text)", "число", "Превращает текст в число (например, из поля ввода).");
        fn("button", "button()", "текст", "Id последней нажатой кнопки интерфейса: if button() == \"yes\" { ... }");
        fn("input", "input(\"id\")", "текст", "Текст, который игрок ввёл в поле интерфейса.");
        fn("guiopen", "guiopen()", "да/нет", "Открыт ли сейчас интерфейс сюжета.");
        fn("event", "event(\"поле\")", "текст/число", "Данные последнего события (внутри on ... { } или после wait события): "
                + "player, block, item, count, entity, character (персонаж сюжета), name, amount, source, slot, level, id, from, to, biome, x, y, z");
        fn("incutscene", "incutscene()", "да/нет", "Идёт ли сейчас катсцена этого сюжета.");
        fn("coins", "coins()", "число", "Сколько монет у игрока.");
        fn("slotitem", "slotitem(\"id\")", "текст", "Id предмета в слоте интерфейса или пустой текст. Пример: if slotitem(\"altar\") == \"minecraft:apple\" { ... }");
        fn("slotcount", "slotcount(\"id\")", "число", "Сколько предметов в слоте интерфейса.");
        fn("lastslot", "lastslot()", "текст", "Id слота, который игрок изменил последним.");
        fn("system", "system(\"health\")", "да/нет", "Включена ли система движка: health, coins, time, inventory, slots.");
        fn("playername", "playername()", "текст", "Ник игрока.");
        fn("int", "int(x)", "число", "Отбрасывает дробную часть.");
        fn("str", "str(x)", "текст", "Превращает значение в текст.");
        fn("min", "min(a, b)", "число", "Минимум.");
        fn("max", "max(a, b)", "число", "Максимум.");
        fn("abs", "abs(x)", "число", "Модуль числа.");
        fn("day", "day()", "число", "Номер текущего дня мира (с 0).");
        fn("bought", "bought(shop, offer)", "число", "Сколько раз купили (продали) товар: bought(\"лавка\", \"sword\").");
        fn("stock", "stock(shop, offer)", "число", "Сколько товара осталось (-1 — без конца).");
        fn("hitbox", "hitbox(who)", "текст", "Как сейчас настроено тело: \"solid damage\", \"ghost\", \"normal\"…");
        fn("fighthp", "fighthp(who)", "число", "Здоровье персонажа в бою (0 — не дерётся или повержен).");
        fn("fighting", "fighting(who)", "логическое", "Дерётся ли персонаж сейчас.");
        fn("defeated", "defeated(who)", "логическое", "Повержен ли персонаж в бою.");
        fn("cooldown", "cooldown(name, seconds)", "логическое",
                "Перезарядка: true — можно (и отсчёт начинается заново), false — ещё рано. if cooldown(\"рывок\", 2) { player.dash(1.5) }.");
        fn("cooldownleft", "cooldownleft(name)", "число", "Сколько секунд осталось до готовности.");
        fn("ticks", "ticks()", "число", "Тики сервера с запуска (20 в секунду) — для своих таймеров.");
        fn("infront", "infront(who, [dist])", "точка", "Точка в dist блоках (3) перед игроком или персонажем — на земле, не в блоках: "
                + "spawn mage at infront(player, 3).");
        fn("ground", "ground(point)", "точка", "Опускает точку на пол (или поднимает из земли): ground((player.x + 5, player.y, player.z)).");
        fn("aimpoint", "aimpoint(who, [dist])", "точка", "Куда смотрит игрок или персонаж: блок под прицелом или точка на дальности (48).");
        fn("raycast", "raycast(from, [to], [dist])", "словарь",
                "Луч. raycast(player) — куда смотрит игрок (или персонаж), raycast(player, 10) — не дальше 10 блоков, "
                        + "raycast(alice, bob) или raycast((0, 70, 0), (10, 70, 0)) — от точки к точке. Ответ: hit (\"block\", \"entity\" или \"none\"), "
                        + "pos — точка попадания, dist, block — id блока, blockpos, face (up, north…), entity. "
                        + "var r = raycast(player)  if r.hit == \"block\" { log r.block }");
        fn("lookblock", "lookblock(who, [dist])", "точка", "Блок, на который смотрит игрок или персонаж (сквозь существ), или пусто: var b = lookblock(player)  ·  setblock b \"minecraft:gold_block\".");
        fn("lookentity", "lookentity(who, [dist])", "сущность", "Существо, на которое смотрит игрок или персонаж, или пусто: if lookentity(player) == alice { … }.");
        fn("cansee", "cansee(a, b)", "логическое", "Видит ли a цель b: между глазами и целью нет блоков (существа не мешают). cansee(alice, player).");
        fn("lookingat", "lookingat(who, target, [dist])", "логическое", "Смотрит ли игрок или персонаж на цель: на существо — прямо в него, на точку — примерно в её сторону и без стены между.");
        fn("inzone", "inzone(zone, [who])", "логическое", "Внутри ли зоны: inzone(\"деревня\") — игрок; inzone(\"деревня\", alice) — сущность или точка.");
        fn("zonepoint", "zonepoint(zone)", "точка", "Случайная точка в зоне (на полу): summon(\"minecraft:zombie\", zonepoint(\"арена\")).");
        fn("zonecenter", "zonecenter(zone)", "точка", "Центр зоны (по полу).");
        fn("zones", "zones()", "список", "Зоны проекта, в которых сейчас игрок.");
        fn("queststate", "queststate(quest)", "текст", "Состояние квеста: \"none\" (не выдан), \"active\", \"completed\" или \"failed\".");
        fn("questprogress", "questprogress(quest, task)", "число", "Сколько сделано в задаче: questprogress(\"ворота\", \"zombies\").");   
        fn("summon", "summon(type, pos)", "сущность", "Призывает сущность и возвращает её: var boss = summon(\"minecraft:zombie\", (10, 64, 10)). "
                + "Дальше: boss.health, set boss.name = \"Босс\", boss.kill(), on kill boss { }.");
        fn("entities", "entities(type, center, [radius])", "список", "Сущности рядом (ближние первыми): entities(\"minecraft:zombie\", player, 10). "
                + "Тип: id, \"mob\" — любые мобы, \"player\", \"any\".");
        fn("nearest", "nearest(type, center, [radius])", "сущность", "Ближайшая сущность этого типа или пустое значение.");
        fn("alive", "alive(x)", "да/нет", "Жива ли сущность (и есть ли она ещё в мире).");
        fn("block", "block(pos)", "текст", "Id блока в точке: block((1, 64, 2)) == \"minecraft:stone\".");
        fn("blockprop", "blockprop(pos, prop)", "значение", "Свойство блока: blockprop(pos, \"facing\") — \"north\", blockprop(pos, \"powered\") — да/нет.");
        fn("inbox", "inbox(pos, corner1, corner2)", "да/нет", "Внутри ли точка (или сущность) коробки между двумя углами: inbox(player, (0, 60, 0), (10, 70, 10)).");
        fn("range", "range(a, [b], [step])", "список", "Числа подряд: range(5) — 0..4, range(1, 6) — 1..5, range(0, 10, 2) — 0, 2, 4, 6, 8.");
        fn("list", "list(...)", "список", "Список из значений: list(1, 2, 3) — то же, что [1, 2, 3].");
        fn("has", "has(list, x)", "да/нет", "Есть ли x в списке (или ключ в словаре).");
        fn("indexof", "indexof(list, x)", "число", "Номер x в списке (с 0) или -1.");
        fn("keys", "keys(dict)", "список", "Ключи словаря.");
        fn("values", "values(dict)", "список", "Значения словаря.");
        fn("sum", "sum(list)", "число", "Сумма чисел списка.");
        fn("join", "join(list, [sep])", "текст", "Склеивает список в текст: join([1, 2], \" и \") — \"1 и 2\".");
        fn("split", "split(text, [sep])", "список", "Разрезает текст: split(\"a,b\", \",\") — [\"a\", \"b\"].");
        fn("sorted", "sorted(list)", "список", "Копия списка по возрастанию.");
        fn("pick", "pick(list)", "значение", "Случайный элемент списка.");
        fn("hp", "hp(who)", "число", "Здоровье: в бою (fight) — боевое, иначе обычное. 0 — нет или погиб.");
        fn("maxhp", "maxhp(who)", "число", "Полное здоровье: в бою — из стиля боя, иначе максимум существа.");
        fn("hpfrac", "hpfrac(who)", "число", "Доля здоровья 0..1 — для фаз босса: if hpfrac(boss) < 0.5 { … }.");
        fn("parts", "parts(who)", "список", "Имена частей хитбокса персонажа (из редактора сущности).");
        fn("lastpart", "lastpart(who)", "текст", "Часть, в которую попали последней: имя, \"body\" — основной хитбокс, \"\" — ещё не попадали.");
        fn("parton", "parton(who, name)", "да/нет", "Включена ли часть хитбокса.");
        fn("partpos", "partpos(who, name)", "точка", "Центр части хитбокса сейчас: fx play \"magic_hit\" at partpos(boss, \"глаз\").");
        fn("moving", "moving(who)", "да/нет", "Идёт ли сейчас приём движения: leap, charge или circle.");
        fn("beaming", "beaming(who)", "да/нет", "Горит ли сейчас его луч (beam).");
        fn("attacking", "attacking(who)", "да/нет", "Идёт ли сейчас его удар хитбоксом (attack ... nowait).");
        fn("attackhits", "attackhits(who)", "число", "Скольких задел его последний удар хитбоксом (attack).");
        fn("behind", "behind(who, [dist])", "точка", "Точка за спиной игрока или персонажа в dist блоках (3), на земле: tp boss to behind(player, 2).");
        fn("around", "around(center, radius, angle)", "точка", "Точка на окружности вокруг цели или точки, на земле; angle — градусы (0 — на восток, +X; 90 — на юг, +Z). "
                + "for a in range(0, 360, 60) { strike at around(boss, 6, a) }.");
        fn("randaround", "randaround(center, radius)", "точка", "Случайная точка в круге вокруг цели или точки, на земле.");
        fn("dirto", "dirto(from, to)", "точка", "Направление от одного к другому длиной 1 (по горизонтали): vmul(dirto(boss, player), 4).");
        fn("vadd", "vadd(a, b)", "точка", "Сумма точек: vadd((1, 2, 3), (0, 1, 0)).");
        fn("vsub", "vsub(a, b)", "точка", "Разность точек (направление от b к a).");
        fn("vmul", "vmul(a, k)", "точка", "Точка, умноженная на число.");
        fn("vlen", "vlen(a)", "число", "Длина вектора.");
        fn("vdist", "vdist(a, b)", "число", "Расстояние между точками.");
        fn("vnorm", "vnorm(a)", "точка", "Вектор длиной 1 в ту же сторону.");
        // commands as functions: done at once, with a result
        fn("spawn", "spawn(who, pos)", "результат", "Ставит персонажа в мир сразу, как команда spawn: set ok = spawn(alice, player). Вернёт true.");
        fn("tp", "tp(who, pos)", "результат", "Переносит персонажа (команда tp). Вернёт true.");
        fn("despawn", "despawn(who)", "результат", "Убирает персонажа из мира (команда despawn). Вернёт true.");
        fn("give", "give(\"item\", [count])", "число", "Выдаёт игроку предметы (команда give) и возвращает, сколько выдано: give(\"minecraft:apple\", 3).");
        fn("take", "take(\"item\", [count])", "число", "Забирает у игрока предметы и возвращает, сколько удалось забрать: if take(\"minecraft:emerald\", 5) == 5 { ... }.");
        fn("teleport", "teleport(pos)", "результат", "Телепортирует игрока (команда teleport). Вернёт true.");
        fn("effect", "effect(\"effect\", seconds, [level])", "результат", "Накладывает эффект на игрока (команда effect), время — в секундах: effect(\"minecraft:speed\", 10, 2).");
        fn("setblock", "setblock(pos, \"block\")", "текст", "Ставит блок (команда setblock) и возвращает id блока, который был там раньше.");
        fn("explode", "explode(pos, [power])", "результат", "Взрыв в точке (команда explode), сила по умолчанию 3.");
        fn("lightning", "lightning(pos)", "результат", "Молния в точке (команда lightning).");
        fn("message", "message(text)", "результат", "Сообщение в чат (команда message).");
        fn("actionbar", "actionbar(text)", "результат", "Текст над хотбаром (команда actionbar).");
        fn("command", "command(\"command\")", "текст", "Выполняет команду Minecraft (как команда command) и возвращает её ответ.");
        // what the commands change, read back
        fn("itemcount", "itemcount(\"item\")", "число", "Сколько таких предметов у игрока (то, что выдаёт give): itemcount(\"minecraft:diamond\").");
        fn("hasitem", "hasitem(\"item\")", "да/нет", "Есть ли у игрока хоть один такой предмет.");
        fn("helditem", "helditem(who)", "текст", "Предмет в руке (то, что даёт hold): helditem(player), helditem(alice). \"minecraft:air\" — пусто.");
        fn("haseffect", "haseffect(who, \"effect\")", "да/нет", "Действует ли эффект (команда effect): haseffect(player, \"minecraft:speed\").");
        fn("effectlevel", "effectlevel(who, \"effect\")", "число", "Уровень эффекта (1, 2…), 0 — эффекта нет.");
        fn("effecttime", "effecttime(who, \"effect\")", "число", "Сколько секунд эффекту осталось, 0 — эффекта нет.");
        fn("xplevel", "xplevel()", "число", "Уровень опыта игрока.");
        fn("food", "food()", "число", "Сытость игрока (0–20).");
        fn("gamemode", "gamemode()", "текст", "Режим игры: survival, creative, adventure, spectator.");
        fn("dimension", "dimension(who)", "текст", "Измерение сущности: \"minecraft:overworld\", \"minecraft:the_nether\"…");
        fn("weather", "weather()", "текст", "Погода: clear, rain или thunder.");
        fn("biome", "biome(pos)", "текст", "Биом в точке: biome(player) == \"minecraft:plains\".");
        fn("light", "light(pos)", "число", "Освещённость в точке (0–15).");
        fn("sky", "sky(pos)", "да/нет", "Видно ли небо из точки (не под крышей).");
        fn("posof", "posof(who)", "точка", "Где сейчас сущность или персонаж: (x, y, z). Для точки — она сама.");
        fn("charname", "charname(who)", "текст", "Отображаемое имя (то, что задаёт команда name).");
        fn("targetof", "targetof(who)", "сущность", "Кого атакует моб или персонаж, \"\" — никого.");
    }

    public static Collection<CommandSpec> commands() {
        return Collections.unmodifiableCollection(COMMANDS.values());
    }

    public static CommandSpec command(String key) {
        return COMMANDS.get(key);
    }

    /** True when {@code word} starts at least one command (e.g. "wait", "flag"). */
    public static boolean isCommandHead(String word) {
        for (CommandSpec s : COMMANDS.values()) if (s.head.equals(word)) return true;
        return false;
    }

    public static List<CommandSpec> withHead(String head) {
        List<CommandSpec> list = new ArrayList<>();
        for (CommandSpec s : COMMANDS.values()) if (s.head.equals(head)) list.add(s);
        return list;
    }

    public static Collection<FunctionSpec> functions() {
        return Collections.unmodifiableCollection(FUNCTIONS.values());
    }

    public static FunctionSpec function(String name) {
        return FUNCTIONS.get(name);
    }

    public static Map<String, String> keywordDocs() {
        return Collections.unmodifiableMap(KEYWORD_DOCS);
    }

    public static final List<String> KEYS = List.of(
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "SPACE", "ENTER", "SHIFT", "TAB",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z");

    /** GLFW key code for a key name, or -1. */
    public static int glfwKey(String name) {
        String n = name.toUpperCase();
        if (n.matches("F([1-9]|1[0-2])")) return 290 + Integer.parseInt(n.substring(1)) - 1;
        if (n.length() == 1 && n.charAt(0) >= 'A' && n.charAt(0) <= 'Z') return n.charAt(0);
        if (n.length() == 1 && n.charAt(0) >= '0' && n.charAt(0) <= '9') return n.charAt(0);
        return switch (n) {
            case "SPACE" -> 32;
            case "ENTER" -> 257;
            case "TAB" -> 258;
            case "SHIFT" -> 340;
            case "ESCAPE", "ESC" -> 256;
            default -> -1;
        };
    }

    public static final List<String> EMOTIONS = List.of(
            "CONFUSED", "ANGRY", "ANGRY_HALF", "CURIOUS", "CUTE", "DISGUST", "DISTRUST", "EMBARRASSMENT",
            "FRIGHT", "GIGGLE_NEW_NOMOUTH", "HAPPY_NEW", "INSPIRATION", "INSPIRATION_2", "INSULT", "LAUGH",
            "OFFENDED_LEFT", "PLAYFULNESS", "POSE_DISGUST", "PRIDE_NEW", "PSYCHO_NEW", "REALIZATION", "SAD1",
            "SAD2", "SUS", "PONDERING_LEFT", "SIDE_SURPRISED_LEFT", "TOTAL_SHOCK", "INDIFFERENCE");

    /** Spraute character entity types known to ship with the mod. */
    public static final List<String> ST_ENTITIES = List.of(
            "spraute:npc", "spraute:npc", "spraute:npc",
            "spraute:npc", "spraute:npc", "spraute:npc",
            "spraute:npc", "spraute:npc", "spraute:npc",
            "spraute:npc", "spraute:npc", "spraute:npc",
            "spraute:npc", "spraute:npc");
}
