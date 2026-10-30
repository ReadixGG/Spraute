package org.sprautestudio.st_addon_spraute.editor.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.ClientState;
import org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;
import org.sprautestudio.st_addon_spraute.engine.client.HudPainter;
import org.sprautestudio.st_addon_spraute.engine.client.Skins;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Checkbox;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown;
import org.sprautestudio.st_addon_spraute.ui.widgets.ScrollView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Visual editor of Spraute's systems and the HUD: switches for the systems, HUD parts placed
 * by dragging on a virtual game screen, styles with custom textures (plain or 9-slice). Saved as
 * engine.json in the project and applied to the world.
 */
public class EngineTab extends EditorTab {
    public static final String FILE = "engine.json";
    /** Virtual screen: the GUI size of a 1080p window at GUI scale 4 (close to what most people play with). */
    private static final int VW = 480, VH = 270;
    private static final List<String> ANCHORS = List.of("TL", "T", "TR", "L", "C", "R", "BL", "B", "BR");

    private enum Mode { HUD, CHAT, CHOICE, SHOP, ZONE, QUEST }

    private EngineConfig cfg;
    private String savedJson;
    private Mode mode = Mode.HUD;
    private String selected = "health";
    /** Sample fill of every bar in the preview (mouse wheel). */
    private float sample = 0.7f;
    private final Form form = new Form();
    private final ScrollView scroll;
    private final Button apply, reset, hudBtn, chatBtn, choiceBtn, shopBtn, zoneBtn, questBtn;
    /** Where the rows of section buttons end (they wrap when the window is narrow). */
    private int top = 28;

    // dragging on the virtual screen
    private boolean dragging, resizing, moved;
    private double dragX, dragY;
    private int[] dragStart;
    private float k = 1;
    private int sx, sy;

    /** A page of the engine studio (systems, speech, quest, shop) — or null: the whole old tab. */
    private String page;

    /** One page of the engine studio, on the studio's settings (it saves them). */
    public EngineTab(Project project, EngineConfig shared, String page) {
        this(project, shared);
        this.page = page;
        mode = switch (page) {
            case "quest" -> Mode.QUEST;
            case "shop" -> Mode.SHOP;
            case "speech" -> Mode.CHAT;
            default -> Mode.HUD;
        };
        for (Button b : List.of(apply, reset, hudBtn, chatBtn, choiceBtn, shopBtn, zoneBtn, questBtn)) b.visible = false;
        buildForm();
    }

    /** Pages with only a form (nothing to show on the virtual screen). */
    private boolean formOnly() {
        return page != null && (page.equals("systems") || page.equals("speech"));
    }

    public EngineTab(Project project) {
        this(project, load(project));
    }

    private EngineTab(Project project, EngineConfig loaded) {
        super(project, null);
        cfg = loaded;
        cfg.project = project.name();
        savedJson = cfg.toJson();
        scroll = add(new ScrollView(form));
        apply = add(new Button("Сохранить и применить", this::applyToWorld).icon(Icon.UPLOAD).style(Button.Style.PRIMARY));
        apply.tooltip("Ctrl+S. Настройки сохраняются в проект (engine.json) и сразу включаются в текущем мире");
        reset = add(new Button("Сбросить", () -> {
            cfg = EngineConfig.fromJson("{}");
            cfg.project = project.name();
            buildForm();
        }).icon(Icon.REFRESH).style(Button.Style.GHOST));
        reset.tooltip("Вернуть всё по умолчанию: системы выключены, HUD как в обычном Minecraft");
        hudBtn = add(new Button("HUD", () -> {
            mode = Mode.HUD;
            buildForm();
        }).style(Button.Style.GHOST));
        hudBtn.active = () -> mode == Mode.HUD;
        chatBtn = add(new Button("История чата", () -> {
            mode = Mode.CHAT;
            buildForm();
        }).style(Button.Style.GHOST));
        chatBtn.active = () -> mode == Mode.CHAT;
        choiceBtn = add(new Button("Меню выбора", () -> {
            mode = Mode.CHOICE;
            buildForm();
        }).style(Button.Style.GHOST));
        choiceBtn.active = () -> mode == Mode.CHOICE;
        shopBtn = add(new Button("Торговля", () -> {
            mode = Mode.SHOP;
            buildForm();
        }).style(Button.Style.GHOST));
        shopBtn.active = () -> mode == Mode.SHOP;
        zoneBtn = add(new Button("Название зоны", () -> {
            mode = Mode.ZONE;
            buildForm();
        }).style(Button.Style.GHOST));
        zoneBtn.active = () -> mode == Mode.ZONE;
        questBtn = add(new Button("Журнал квестов", () -> {
            mode = Mode.QUEST;
            buildForm();
        }).style(Button.Style.GHOST));
        questBtn.active = () -> mode == Mode.QUEST;
        for (Button b : List.of(apply, reset, hudBtn, chatBtn, choiceBtn, shopBtn, zoneBtn, questBtn)) b.autoWidth();
        buildForm();
    }

    public static Path file(Project project) {
        return project.dir.resolve(FILE);
    }

    /** The project's settings, or defaults (everything off) when it has none. */
    public static EngineConfig load(Project project) {
        Path f = file(project);
        try {
            if (Files.exists(f)) return EngineConfig.fromJson(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return EngineConfig.fromJson("{}");
    }

    private void applyToWorld() {
        UI ui = UI.get();
        try {
            save();
        } catch (IOException e) {
            if (ui != null) ui.toastError("Не удалось сохранить: " + e.getMessage());
            return;
        }
        if (ui == null) return;
        if (Minecraft.getInstance().getConnection() == null) ui.toastInfo("Сохранено. В мир применится, когда вы в него войдёте и сохраните снова");
        else ui.toastSuccess("Настройки движка применены");
    }

    // ================================================================== preview state

    private HudPainter.State sampleState() {
        HudPainter.State s = new HudPainter.State();
        s.stHealth = cfg.health;
        s.hp = 20 * sample;
        s.maxHp = 20;
        s.armor = Math.round(20 * sample);
        s.food = Math.round(20 * sample);
        s.air = Math.round(300 * sample);
        s.exp = sample;
        s.level = 7;
        s.coins = 1250;
        s.selected = 1;
        s.hotbarSize = cfg.slots ? 5 : 9;
        ItemStack[] items = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 48),
                new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.MAP), new ItemStack(Items.APPLE, 3),
                ItemStack.EMPTY, new ItemStack(Items.COMPASS), new ItemStack(Items.WATER_BUCKET)};
        System.arraycopy(items, 0, s.hotbar, 0, 9);
        s.offhand = new ItemStack(Items.SHIELD);
        return s;
    }

    /** Parts that exist with the current switches (coins only with the coin system). */
    private boolean present(String key) {
        return !key.equals("coins") || cfg.currency;
    }

    // ================================================================== form

    private void buildForm() {
        form.clear();
        if (page != null && !page.equals("systems")) {
            switch (page) {
                case "quest" -> questForm();
                case "shop" -> shopForm();
                default -> speechForm();
            }
            relayout();
            return;
        }
        form.header("Системы движка");
        form.toggle("Своё здоровье", "Круглая полоска здоровья Spraute вместо сердечек", () -> cfg.health, v -> {
            cfg.health = v;
            buildForm();
        });
        form.toggle("Монеты (монеты)", "Счётчик монет справа вверху", () -> cfg.currency, v -> {
            cfg.currency = v;
            if (!v && selected.equals("coins")) selected = "health";
            buildForm();
        });
        form.toggle("Скрывать чат", "Сообщения игроков не показываются в чате. Сюжет всё равно их получает: on chat",
                () -> cfg.chat, v -> cfg.chat = v);
        form.header("Время суток");
        EngineConfig.Clock clock = cfg.clock;
        {
            form.toggle("Управлять временем", "Движок сам ставит смену дня и ночи при загрузке мира. Выключено — решают /gamerule и /time",
                    () -> clock.manage, v -> {
                        clock.manage = v;
                        buildForm();
                    });
        }
        if (clock.manage) {
            {
                form.dropdown("Время стоит", "Зафиксировать время суток (день не идёт)", () -> FIXED_TIMES, () -> clock.fixed,
                        EngineTab::fixedTitle, v -> {
                            clock.fixed = v;
                            buildForm();
                        });
                if (clock.fixed < 0) form.toggle("Смена дня и ночи", "Время идёт (правило doDaylightCycle)", () -> clock.cycle, v -> clock.cycle = v);
            }
            form.decimal("Скорость дня", () -> clock.speed, v -> clock.speed = (float) Math.max(0.05, Math.min(50, v)));
            form.note("1 — обычные 20 минут, 2 — вдвое быстрее, 0.5 — вдвое медленнее");
        }
        if ("systems".equals(page)) {
            relayout();
            return;
        }
        if (mode == Mode.QUEST) {
            questForm();
        } else if (mode == Mode.ZONE) {
            zoneTitleForm();
        } else if (mode == Mode.SHOP) {
            shopForm();
        } else if (mode == Mode.CHOICE) {
            choiceForm();
        } else if (mode == Mode.CHAT) {
            form.header("История чата");
            form.note("Окно истории сообщений. Пустое поле — стандартная текстура");
            form.skin("Фон", cfg.chatLog.back);
            form.skin("Рамка", cfg.chatLog.frame);
            form.toggle("Рамка поверх сообщений", "Включите, если в середине вашей рамки прозрачная дыра под текст (как у Spraute). "
                    + "Выключено — рамка под сообщениями, сразу над фоном", () -> cfg.chatLog.frameOver, v -> cfg.chatLog.frameOver = v);
            form.header("Имена в речи");
            form.note("Цвет имени говорящего в окне речи и в истории сообщений");
            form.toggle("Один цвет для всех", "Персонажам без своего color — этот цвет. Выключено — обычный цвет Spraute",
                    () -> cfg.chatLog.nameColor >= 0, v -> {
                        cfg.chatLog.nameColor = v ? 0xFFAA00 : -1;
                        buildForm();
                    });
            if (cfg.chatLog.nameColor >= 0) form.color("Цвет имён", () -> 0xFF000000 | cfg.chatLog.nameColor, v -> cfg.chatLog.nameColor = v & 0xFFFFFF);
            form.note("Свой цвет персонажу: character … color \"gold\"  ·  namecolor alice \"#FF8800\"");
            form.toggle("Подсказка «▸ F8»", "У имени говорящего, пока сюжет ждёт нажатия F8 (реплика без nowait)",
                    () -> cfg.chatLog.f8Hint, v -> cfg.chatLog.f8Hint = v);
            form.header("Голова игрока в речи");
            form.decimal("Размер", () -> cfg.chatLog.playerHeadScale, v -> cfg.chatLog.playerHeadScale = (float) Math.max(0.2, Math.min(4, v)));
            form.number("Сдвиг X (%)", () -> cfg.chatLog.playerHeadX, v -> cfg.chatLog.playerHeadX = v);
            form.number("Сдвиг Y (%)", () -> cfg.chatLog.playerHeadY, v -> cfg.chatLog.playerHeadY = v);
            form.note("Новые размеры — со следующего запуска сюжета");
        } else {
            elementForm();
        }
        relayout();
    }

    /** How speech looks: name colours, the F8 hint, the player's head. */
    private void speechForm() {
        form.header("Имена в речи");
        form.note("Цвет имени говорящего в окне речи и в истории сообщений");
        form.toggle("Один цвет для всех", "Персонажам без своего color — этот цвет. Выключено — обычный цвет Spraute",
                () -> cfg.chatLog.nameColor >= 0, v -> {
                    cfg.chatLog.nameColor = v ? 0xFFAA00 : -1;
                    buildForm();
                });
        if (cfg.chatLog.nameColor >= 0) form.color("Цвет имён", () -> 0xFF000000 | cfg.chatLog.nameColor, v -> cfg.chatLog.nameColor = v & 0xFFFFFF);
        form.note("Свой цвет персонажу: character … color \"gold\"  ·  namecolor alice \"#FF8800\"");
        form.toggle("Подсказка «▸ F8»", "У имени говорящего, пока сюжет ждёт нажатия F8 (реплика без nowait)",
                () -> cfg.chatLog.f8Hint, v -> cfg.chatLog.f8Hint = v);
        form.header("Голова игрока в речи");
        form.decimal("Размер", () -> cfg.chatLog.playerHeadScale, v -> cfg.chatLog.playerHeadScale = (float) Math.max(0.2, Math.min(4, v)));
        form.number("Сдвиг X (%)", () -> cfg.chatLog.playerHeadX, v -> cfg.chatLog.playerHeadX = v);
        form.number("Сдвиг Y (%)", () -> cfg.chatLog.playerHeadY, v -> cfg.chatLog.playerHeadY = v);
        form.note("Новые размеры — со следующего запуска сюжета");
        form.header("Меню выбора: поведение");
        form.toggle("Камера к говорящему", "Камера подлетает к тому, кто спрашивает, как в Spraute", () -> cfg.choice.camera, v -> cfg.choice.camera = v);
        form.dropdown("В портрете", "Кого рисует элемент «Говорящий»", () -> CHOICE_SHOWS, () -> cfg.choice.show,
                v -> switch (v) {
                    case "speaker" -> "того, кто спрашивает";
                    case "player" -> "игрока";
                    case "mob" -> "моба (ниже)";
                    default -> "никого";
                }, v -> {
                    cfg.choice.show = v;
                    buildForm();
                });
        if (cfg.choice.show.equals("mob")) form.text("Тип моба", "Id сущности: minecraft:villager, spraute:npc", () -> cfg.choice.mob, v -> cfg.choice.mob = v.trim());
    }

    private static final List<Integer> FIXED_TIMES = List.of(-1, 0, 1000, 6000, 12000, 13000, 18000, 23000);

    private static String fixedTitle(int t) {
        return switch (t) {
            case -1 -> "нет — время идёт";
            case 0 -> "рассвет (0)";
            case 1000 -> "утро (1000)";
            case 6000 -> "полдень (6000)";
            case 12000 -> "закат (12000)";
            case 13000 -> "ночь (13000)";
            case 18000 -> "полночь (18000)";
            case 23000 -> "перед рассветом (23000)";
            default -> String.valueOf(t);
        };
    }

    private static final List<String> CHOICE_STYLES = List.of("spraute");
    private static final List<String> CHOICE_SHOWS = List.of("speaker", "player", "mob", "none");

    /** The part of the shop window being moved and its shift when the drag began. */
    private String shopPart = "list";
    private int[] shopDragStart = {0, 0};

    private static final class ShopPanelParts {
        static final List<String> ALL = org.sprautestudio.st_addon_spraute.shop.client.ShopPanel.PARTS;
    }

    private final org.sprautestudio.st_addon_spraute.shop.client.ShopPanel shopSample =
            new org.sprautestudio.st_addon_spraute.shop.client.ShopPanel(org.sprautestudio.st_addon_spraute.shop.ShopView.sample());

    private void shopForm() {
        EngineConfig.Shop s = cfg.shop;
        form.header("Окно магазина");
        form.note("Как в Spraute. Справа — пример: щёлкайте товары, стрелки, колесо листает. Пусто — текстуры Spraute");
        form.skin("Список товаров (254×212)", s.panel);
        form.skin("Карточка (176×262)", s.detail);
        form.skin("Ячейка (30×43)", s.cell);
        form.skin("Ячейка выбрана", s.cellOn);
        form.skin("Кнопка (88×14)", s.dealButton);
        form.skin("Кнопка наведена", s.buttonHover);
        form.skin("Ячейка — закрыто", s.locked);
        form.skin("Ячейка — продано", s.sold);
        form.skin("Ползунок (9×10)", s.slider);
        form.skin("Стрелка −5", s.arrowMinus5);
        form.skin("Стрелка −5 наведена", s.arrowMinus5Hover);
        form.skin("Стрелка −1", s.arrowMinus1);
        form.skin("Стрелка −1 наведена", s.arrowMinus1Hover);
        form.skin("Стрелка +1", s.arrowPlus1);
        form.skin("Стрелка +1 наведена", s.arrowPlus1Hover);
        form.skin("Стрелка +5", s.arrowPlus5);
        form.skin("Стрелка +5 наведена", s.arrowPlus5Hover);
        form.header("Расположение");
        form.note("Перетаскивайте части окна справа; стрелки — по 1, с Shift — по 10");
        form.dropdown("Двигаю", "Что двигать. «Всё окно» — только отсюда: по превью таскаются части", () -> ShopPanelParts.ALL, () -> shopPart,
                org.sprautestudio.st_addon_spraute.shop.client.ShopPanel::partTitle, v -> {
                    shopPart = v;
                    buildForm();
                });
        form.button("Вернуть на место", null, () -> {
            s.offsets.remove(shopPart);
            buildForm();
        });
        form.toggle("Всё как в Spraute", null, () -> s.offsets.isEmpty(), v -> {
            s.offsets.clear();
            buildForm();
        });
        form.header("Цвета");
        form.color("Название", () -> s.titleColor, v -> s.titleColor = v);
        form.color("Описание", () -> s.mutedColor, v -> s.mutedColor = v);
        form.color("Цена", () -> s.priceColor, v -> s.priceColor = v);
        form.color("Продажа / удача", () -> s.sellColor, v -> s.sellColor = v);
    }

    private final org.sprautestudio.st_addon_spraute.quest.client.QuestScreen questSample = new org.sprautestudio.st_addon_spraute.quest.client.QuestScreen(
            org.sprautestudio.st_addon_spraute.quest.QuestJournal.preview(org.sprautestudio.st_addon_spraute.quest.QuestDef.example("Ворота деревни"), "example/gate"));
    private String questPart = "list";
    private int[] questDragStart = {0, 0};
    private float questK = 1, questOX, questOY;

    private void questForm() {
        EngineConfig.Quests q = cfg.quests;
        form.header("Журнал квестов");
        form.note("Справа — пример. Тяните список и карточку, стрелки — точный сдвиг");
        form.decimal("Размер", () -> q.scale, v -> q.scale = (float) Math.max(0.3, Math.min(1.5, v)));
        form.note("1 — на весь экран (журнал сохраняет форму), 0.8 — меньше");
        form.dropdown("Двигаю", null, () -> List.of("list", "card"), () -> questPart, v -> v.equals("list") ? "список квестов" : "карточку квеста", v -> {
            questPart = v;
            buildForm();
        });
        form.button("Вернуть на место", null, () -> {
            q.offsets.remove(questPart);
            buildForm();
        });
        form.header("Цели на экране");
        form.toggle("Цели справа", "Отслеживаемый квест и его задачи у правого края экрана. Выключено — задачи видны только в журнале (J)",
                () -> q.tracker, v -> q.tracker = v);
        EngineConfig.Quests.Toast t = q.toast;
        form.header("Уведомление квеста");
        form.note("Плашка сверху экрана: новый квест, задача выполнена, квест выполнен…");
        form.toggle("Показывать", "Выключено — никаких плашек, всё только в журнале", () -> t.enabled, v -> {
            t.enabled = v;
            buildForm();
        });
        if (t.enabled) {
            form.toggle("Новый квест", null, () -> t.onStart, v -> t.onStart = v);
            form.text("  надпись", "Пусто — «Новый квест»", () -> t.startTitle, v -> t.startTitle = v);
            form.toggle("Задача выполнена", null, () -> t.onTask, v -> t.onTask = v);
            form.text("  надпись", "Пусто — «Задача выполнена»", () -> t.taskTitle, v -> t.taskTitle = v);
            form.toggle("Новая задача", "Скрытая задача открылась (quest reveal)", () -> t.onReveal, v -> t.onReveal = v);
            form.text("  надпись", "Пусто — «Новая задача»", () -> t.revealTitle, v -> t.revealTitle = v);
            form.toggle("Квест выполнен", null, () -> t.onComplete, v -> t.onComplete = v);
            form.text("  надпись", "Пусто — «Квест выполнен»", () -> t.completeTitle, v -> t.completeTitle = v);
            form.toggle("Квест провален", null, () -> t.onFail, v -> t.onFail = v);
            form.text("  надпись", "Пусто — «Квест провален»", () -> t.failTitle, v -> t.failTitle = v);
            form.decimal("Секунд на экране", () -> t.seconds, v -> t.seconds = (float) Math.max(0.5, Math.min(30, v)));
            form.number("Отступ сверху", () -> t.y, v -> t.y = Math.max(0, Math.min(400, v)));
            form.number("Ширина", () -> t.width, v -> t.width = Math.max(80, Math.min(600, v)));
            form.decimal("Размер", () -> t.scale, v -> t.scale = (float) Math.max(0.3, Math.min(3, v)));
            form.color("Фон", () -> t.background, v -> t.background = (t.background & 0xFF000000) | (v & 0xFFFFFF));
            form.number("Фон: прозрачность (0..255)", () -> (t.background >>> 24) & 0xFF,
                    v -> t.background = (Math.max(0, Math.min(255, v)) << 24) | (t.background & 0xFFFFFF));
            form.toggle("Значок", null, () -> t.icon, v -> t.icon = v);
            form.toggle("Звук", null, () -> t.sound, v -> t.sound = v);
            form.note("Цвета надписи — из «Цвета» ниже: отслеживаемый (новый квест, задача), выполнено, провалено");
            form.button("Показать для проверки", "Плашка «Задача выполнена» появится в игре", () -> org.sprautestudio.st_addon_spraute.quest.client.QuestClient.showToast("task", "Задача выполнена", "Пример задачи"));
        }
        form.header("Текстуры");
        form.note("Пусто — текстуры Spraute");
        form.skin("Список (144×230)", q.list);
        form.skin("Карточка (240×225)", q.card);
        form.skin("Кнопка квеста (131×35)", q.button);
        form.skin("Кнопка наведена", q.buttonHover);
        form.skin("Кнопка выбрана", q.buttonOn);
        form.skin("Полоса задачи (225×16)", q.taskLine);
        form.note("Листы — как в архиве текстур: несколько состояний друг под другом, можно крупнее в 2–4 раза");
        form.skin("Вкладка «Текущие» (23×69)", q.tabCurrent);
        form.skin("Вкладка «Выполненные» (23×69)", q.tabDone);
        form.skin("Вкладка «Проваленные» (23×69)", q.tabFailed);
        form.skin("Значки (63×46)", q.icons);
        form.skin("Разделитель (129×2)", q.divider);
        form.header("Цвета");
        form.color("Текст", () -> q.textColor, v -> q.textColor = v);
        form.color("Приглушённый", () -> q.mutedColor, v -> q.mutedColor = v);
        form.color("Отслеживаемый, счётчики", () -> q.accentColor, v -> q.accentColor = v);
        form.color("Заголовки", () -> q.headerColor, v -> q.headerColor = v);
        form.color("Выполнено", () -> q.doneColor, v -> q.doneColor = v);
        form.color("Провалено", () -> q.failColor, v -> q.failColor = v);
        form.color("Затемнение экрана", () -> q.shadeColor, v -> q.shadeColor = (q.shadeColor & 0xFF000000) | (v & 0xFFFFFF));
        form.number("Затемнение (0..255)", () -> (q.shadeColor >>> 24) & 0xFF, v -> q.shadeColor = (Math.max(0, Math.min(255, v)) << 24) | (q.shadeColor & 0xFFFFFF));
        form.button("Всё как в Spraute", null, () -> {
            cfg.quests = new EngineConfig.Quests();
            buildForm();
        });
    }

    /** The journal's part under a point of the preview (window coordinates), or null. */
    private String questPartAt(double jx, double jy) {
        int[] ol = cfg.quests.off("list"), oc = cfg.quests.off("card");
        if (jx >= 150 + oc[0] && jx < 390 + oc[0] && jy >= 2 + oc[1] && jy < 227 + oc[1]) return "card";
        if (jx >= ol[0] && jx < 144 + ol[0] && jy >= ol[1] && jy < 230 + ol[1]) return "list";
        return null;
    }

    private void zoneTitleForm() {
        EngineConfig.ZoneTitle z = cfg.zoneTitle;
        form.header("Название зоны");
        form.note("Плашка с названием, когда игрок входит в зону. Справа — щёлкните или тяните, чтобы поставить");
        form.decimal("Где по X (0..1)", () -> z.x, v -> z.x = (float) Math.max(0, Math.min(1, v)));
        form.decimal("Где по Y (0..1)", () -> z.y, v -> z.y = (float) Math.max(0, Math.min(1, v)));
        form.decimal("Размер названия", () -> z.scale, v -> z.scale = (float) Math.max(0.3, v));
        form.decimal("Размер подписи", () -> z.subScale, v -> z.subScale = (float) Math.max(0.3, v));
        form.decimal("Сколько секунд", () -> z.seconds, v -> z.seconds = (float) Math.max(0.5, v));
        form.toggle("Жирный", null, () -> z.bold, v -> z.bold = v);
        form.toggle("Тень", null, () -> z.shadow, v -> z.shadow = v);
        form.toggle("Линии под названием", null, () -> z.lines, v -> z.lines = v);
        form.toggle("Буквы сходятся", "Название чуть шире при появлении и сжимается", () -> z.spread, v -> z.spread = v);
        form.header("Цвета");
        form.color("Название", () -> z.titleColor, v -> z.titleColor = v);
        form.color("Подпись", () -> z.subColor, v -> z.subColor = v);
        form.color("Линии", () -> z.lineColor, v -> z.lineColor = (z.lineColor & 0xFF000000) | (v & 0xFFFFFF));
        form.header("Плашка");
        form.toggle("Плашка под текстом", null, () -> z.plate, v -> {
            z.plate = v;
            buildForm();
        });
        if (z.plate) {
            form.color("Цвет плашки", () -> z.plateColor, v -> z.plateColor = (z.plateColor & 0xFF000000) | (v & 0xFFFFFF));
            form.number("Прозрачность (0..255)", () -> (z.plateColor >>> 24) & 0xFF, v -> z.plateColor = (Math.max(0, Math.min(255, v)) << 24) | (z.plateColor & 0xFFFFFF));
            form.number("Отступ", () -> z.padding, v -> z.padding = Math.max(0, v));
            form.skin("Своя текстура плашки", z.plateSkin);
        }
        form.header("Звук");
        form.text("Звук появления", "Id звука, например minecraft:ui.toast.challenge_complete (пусто — без звука)", () -> z.sound, v -> z.sound = v.trim());
        form.button("Всё как было", null, () -> {
            cfg.zoneTitle = new EngineConfig.ZoneTitle();
            buildForm();
        });
    }

    private long zonePreviewAt = System.currentTimeMillis();

    private void choiceForm() {
        EngineConfig.Choice ch = cfg.choice;
        form.header("Меню выбора (choice)");
        form.dropdown("Меню", "Меню выбора в диалоге", () -> CHOICE_STYLES, () -> ch.style,
                v -> v.equals("spraute") ? "своё (движок)" : "Spraute", v -> {
                    ch.style = v;
                    buildForm();
                });
        if (!ch.style.equals("spraute")) {
            relayout();
            return;
        }
        form.toggle("Камера к говорящему", "Как в диалогах Spraute: камера подлетает к лицу того, кто спрашивает, "
                + "а после ответа возвращается", () -> ch.camera, v -> ch.camera = v);
        form.dropdown("Рядом с вопросом", "Кто нарисован слева от вопроса", () -> CHOICE_SHOWS, () -> ch.show, v -> switch (v) {
            case "speaker" -> "тот, кто спрашивает";
            case "player" -> "игрок";
            case "mob" -> "моб (тип ниже)";
            default -> "никто";
        }, v -> {
            ch.show = v;
            buildForm();
        });
        if (ch.show.equals("mob")) form.text("Тип моба", "Id сущности: minecraft:villager, spraute:npc", () -> ch.mob, v -> ch.mob = v.trim());
        if (!ch.show.equals("none")) {
            form.dropdown("Кадр", null, () -> List.of("body", "head"), () -> ch.framing,
                    v -> v.equals("head") ? "только голова (как в чате)" : "во весь рост", v -> ch.framing = v);
            form.header("Персонаж в рамке");
            form.decimal("Масштаб", () -> ch.entityScale, v -> ch.entityScale = (float) Math.max(0.1, v));
            form.number("Сдвиг X", () -> ch.entityX, v -> ch.entityX = v);
            form.number("Сдвиг Y", () -> ch.entityY, v -> ch.entityY = v);
            form.note("Обрезка — сколько пикселей рамки срезать с каждой стороны");
            form.number("Обрезка слева", () -> ch.cropLeft, v -> ch.cropLeft = Math.max(0, v));
            form.number("Обрезка справа", () -> ch.cropRight, v -> ch.cropRight = Math.max(0, v));
            form.number("Обрезка сверху", () -> ch.cropTop, v -> ch.cropTop = Math.max(0, v));
            form.number("Обрезка снизу", () -> ch.cropBottom, v -> ch.cropBottom = Math.max(0, v));
            form.button("Как было", "Масштаб 1, без сдвига, обрезка 4 со всех сторон", () -> {
                ch.entityScale = 1;
                ch.entityX = ch.entityY = 0;
                ch.cropLeft = ch.cropRight = ch.cropTop = ch.cropBottom = 4;
                buildForm();
            });
        }
        form.header("Размер");
        form.decimal("Размер меню", () -> ch.uiScale, v -> ch.uiScale = (float) Math.max(0.3, Math.min(3, v)));
        form.note("1 — как окно Spraute, на любом экране и масштабе интерфейса одинаково");
        form.dropdown("Ширина кнопок", "По тексту — кнопка растягивается под свой ответ", () -> List.of("fixed", "fit"), () -> ch.answerWidth,
                v -> v.equals("fit") ? "по тексту" : "как в Spraute (166)", v -> {
                    ch.answerWidth = v;
                    buildForm();
                });
        if (ch.answerWidth.equals("fit")) {
            form.number("Мин. ширина", () -> ch.answerMinW, v -> ch.answerMinW = Math.max(40, v));
            form.number("Макс. ширина", () -> ch.answerMaxW, v -> ch.answerMaxW = Math.max(60, v));
        }
        form.decimal("Размер текста ответов", () -> ch.answerTextScale, v -> ch.answerTextScale = (float) Math.max(0.4, Math.min(3, v)));
        form.header("Расположение");
        form.note("Перетаскивайте части меню на экране справа (стрелки — сдвиг на 1, с Shift — на 10). Выбрано: "
                + org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.title(choicePart));
        form.button("Вернуть выбранное на место", null, () -> {
            ch.offsets.remove(choicePart);
            buildForm();
        });
        form.toggle("Всё как в Spraute", null, () -> ch.offsets.isEmpty(), v -> {
            ch.offsets.clear();
            buildForm();
        });
        form.header("Текстуры");
        form.note("Пустое поле — текстуры Spraute");
        form.skin("Панель (480×270)", ch.panel);
        form.skin("Поверх портрета", ch.portrait);
        form.skin("Кнопка", ch.button);
        form.skin("Кнопка (наведена)", ch.buttonHover);
        form.toggle("Полоска под именем", "Выключено — под именем спрашивающего ничего нет", () -> ch.nameLine, v -> {
            ch.nameLine = v;
            buildForm();
        });
        if (ch.nameLine) form.skin("Полоска под именем (80×3)", ch.nameplate);
        form.header("Цвета");
        form.color("Имя", () -> ch.nameColor, v -> ch.nameColor = v);
        form.color("Вопрос", () -> ch.textColor, v -> ch.textColor = v);
        form.color("Ответ", () -> ch.answerColor, v -> ch.answerColor = v);
        form.color("Ответ (наведён)", () -> ch.answerHoverColor, v -> ch.answerHoverColor = v);
    }

    private void elementForm() {
        String key = selected;
        EngineConfig.Element e = cfg.element(key);
        form.header(EngineConfig.title(key));
        if (key.equals("health")) form.note(cfg.health ? "Стандартный вид — круглая полоска Spraute" : "Стандартный вид — сердечки Minecraft");
        form.toggle("Показывать", "Выключите, чтобы убрать элемент с экрана", () -> e.visible, v -> e.visible = v);
        form.toggle("Своя позиция", "Выключено — элемент стоит на своём обычном месте. Перетащите его на экране справа, чтобы переставить",
                () -> e.customPos, v -> {
                    if (v) place(key, e, HudPainter.rect(cfg, key, VW, VH, sampleState()));
                    e.customPos = v;
                    buildForm();
                });
        if (e.customPos) {
            form.dropdown("Якорь", "Точка экрана, от которой отсчитывается позиция: при другом размере окна элемент держится её",
                    () -> ANCHORS, () -> e.anchor, EngineTab::anchorTitle, v -> {
                        int[] r = HudPainter.rect(cfg, key, VW, VH, sampleState());
                        e.anchor = v;
                        place(key, e, r);
                        buildForm();
                    });
            form.toggle("В процентах", "Сдвиг от якоря в процентах размера экрана: элемент держит своё место при любом размере окна и масштабе интерфейса",
                    () -> e.percent, v -> {
                        e.percent = v;
                        buildForm();
                    });
            if (e.percent) {
                form.decimal("X, %", () -> e.px, v -> e.px = (float) v);
                form.decimal("Y, %", () -> e.py, v -> e.py = (float) v);
            } else {
                form.number("X", () -> e.x, v -> e.x = v);
                form.number("Y", () -> e.y, v -> e.y = v);
            }
        }
        form.dropdown("Вид", null, () -> EngineConfig.styles(key), () -> e.style, EngineConfig::styleTitle, v -> {
            int[] r = HudPainter.rect(cfg, key, VW, VH, sampleState());
            e.style = v;
            if (!EngineConfig.gameDrawn(v) && !e.customPos) {
                // keep the part where it was
                e.customPos = true;
                place(key, e, r);
            }
            buildForm();
        });
        switch (e.style) {
            case "bar" -> {
                form.number("Ширина", () -> e.width, v -> e.width = Math.max(2, v));
                form.number("Высота", () -> e.height, v -> e.height = Math.max(2, v));
                form.number("Отступ заливки", () -> e.pad, v -> e.pad = Math.max(0, v));
                form.skin("Фон", e.back);
                form.skin("Заливка", e.fill);
                form.skin("Рамка", e.frame);
                form.toggle(key.equals("exp") ? "Уровень над полоской" : "Число на полоске", null, () -> e.text, v -> e.text = v);
                form.color("Цвет текста", () -> e.textColor, v -> e.textColor = v);
            }
            case "icons" -> {
                form.number("Количество", () -> e.count, v -> e.count = Math.max(1, Math.min(40, v)));
                form.number("Размер иконки", () -> e.height, v -> e.height = Math.max(2, v));
                form.number("Промежуток", () -> e.gap, v -> e.gap = v);
                form.toggle("Заполнять справа", "Как еда в Minecraft: убывает слева направо", () -> e.reverse, v -> e.reverse = v);
                form.skin("Полная", e.full);
                form.skin("Половина", e.half);
                form.skin("Пустая", e.empty);
                form.toggle("Число рядом", null, () -> e.text, v -> e.text = v);
            }
            case "slots" -> {
                form.number("Размер слота", () -> e.height, v -> e.height = Math.max(8, v));
                form.number("Промежуток", () -> e.gap, v -> e.gap = v);
                form.skin("Слот", e.back);
                form.skin("Выбранный", e.frame);
                form.skin("Закрытый", e.fill);
                form.note("Закрытые слоты — при включённой блокировке слотов");
            }
            case "icon" -> {
                form.number("Размер иконки", () -> e.height, v -> e.height = Math.max(4, v));
                form.skin("Иконка", e.fill);
                form.skin("Подложка", e.back);
                form.color("Цвет числа", () -> e.textColor, v -> e.textColor = v);
            }
            case "storytelling" -> form.note("Широкая рамка, выделение и замки слотов из движка Spraute");
            default -> form.note("Обычный вид игры. Выберите другой вид, чтобы поставить свои текстуры");
        }
    }

    /** Stores a part's absolute rectangle as an offset from its anchor. */
    private void place(String key, EngineConfig.Element e, int[] r) {
        int[] full = r.length >= 4 ? r : new int[]{r[0], r[1], 0, 0};
        if (r.length < 4) {
            int[] cur = HudPainter.rect(cfg, key, VW, VH, sampleState());
            full[2] = cur[2];
            full[3] = cur[3];
        }
        HudPainter.place(e, full, VW, VH);
    }

    private static String anchorTitle(String a) {
        return switch (a) {
            case "TL" -> "↖ слева сверху";
            case "T" -> "↑ по центру сверху";
            case "TR" -> "↗ справа сверху";
            case "L" -> "← слева по центру";
            case "C" -> "• центр";
            case "R" -> "→ справа по центру";
            case "BL" -> "↙ слева снизу";
            case "B" -> "↓ по центру снизу";
            default -> "↘ справа снизу";
        };
    }

    private void relayout() {
        if (w > 0) layout();
    }

    private List<String> imageNames() {
        List<String> names = new ArrayList<>();
        names.add("");
        for (Project.ImageRef r : project.images()) names.add(r.name());
        return names;
    }

    /** Vertical list of setting rows. */
    private final class Form extends Widget {
        void header(String text) {
            add(new Widget() {
                {
                    prefH = 26;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.fill(x + 8, y + 8, w - 16, 1, Theme.BORDER);
                    c.text(text.toUpperCase(), x + 10, y + 14, Theme.ACCENT);
                }
            });
        }

        void note(String text) {
            add(new Widget() {
                {
                    prefH = 13;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.textEllipsized(text, x + 10, y + 2, w - 16, Theme.TEXT_MUTED);
                }
            }).tooltip(text);
        }

        /** An action: a real button over the row's width. */
        void button(String label, String doc, Runnable action) {
            Button b = new Button(label, action).style(Button.Style.DEFAULT);
            if (doc != null) b.tooltip(doc);
            add(new Widget() {
                final Button btn = add(b);

                {
                    prefH = 24;
                }

                @Override
                public void layout() {
                    btn.bounds(x + 10, y + 2, Math.max(60, w - 20), 20);
                    super.layout();
                }
            });
        }

        void toggle(String label, String doc, BooleanSupplier get, Consumer<Boolean> set) {
            Row r = add(new Row(label));
            r.editor = r.add(new Checkbox("", get, set));
            if (doc != null) r.tooltip(doc);
        }

        <T> void dropdown(String label, String doc, java.util.function.Supplier<List<T>> options, java.util.function.Supplier<T> get,
                          java.util.function.Function<T, String> labeler, Consumer<T> set) {
            Row r = add(new Row(label));
            r.editor = r.add(new Dropdown<>(options, get, labeler, set));
            if (doc != null) r.tooltip(doc);
        }

        void number(String label, IntSupplier get, IntConsumer set) {
            Row r = add(new Row(label));
            TextField f = new TextField() {
                @Override
                public void tick() {
                    super.tick();
                    // follow changes made by dragging on the screen
                    if (!isFocused()) {
                        String s = String.valueOf(get.getAsInt());
                        if (!s.equals(text())) setText(s);
                    }
                }
            };
            f.setText(String.valueOf(get.getAsInt()));
            f.filter = ch -> Character.isDigit(ch) || ch == '-';
            f.onChange = s -> {
                try {
                    set.accept(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void decimal(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
            Row r = add(new Row(label));
            TextField f = new TextField() {
                @Override
                public void tick() {
                    super.tick();
                    if (!isFocused()) {
                        String s = trim((float) get.getAsDouble());
                        if (!s.equals(text())) setText(s);
                    }
                }
            };
            f.setText(trim((float) get.getAsDouble()));
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            f.onChange = s -> {
                try {
                    set.accept(Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void text(String label, String doc, java.util.function.Supplier<String> get, Consumer<String> set) {
            Row r = add(new Row(label));
            TextField f = new TextField();
            f.setText(get.get());
            f.onChange = set::accept;
            r.editor = r.add(f);
            if (doc != null) r.tooltip(doc);
        }

        void color(String label, IntSupplier get, IntConsumer set) {
            Row r = add(new Row(label));
            TextField f = new TextField();
            f.setText(String.format("#%06X", get.getAsInt() & 0xFFFFFF));
            f.onChange = s -> {
                String h = s.trim().replace("#", "");
                try {
                    if (h.length() == 6) set.accept(0xFF000000 | Integer.parseInt(h, 16));
                } catch (NumberFormatException ignored) {
                }
            };
            // a swatch: click — the colour picker
            r.editor = r.add(new Widget() {
                final TextField field = add(f);

                @Override
                public void layout() {
                    field.bounds(x + 24, y, w - 24, h);
                    super.layout();
                }

                @Override
                protected void paint(org.sprautestudio.st_addon_spraute.ui.Canvas c, int mx, int my, float dt) {
                    c.round(x, y, 20, h, 3, Theme.BORDER);
                    c.round(x + 1, y + 1, 18, h - 2, 2, 0xFF000000 | get.getAsInt());
                }

                @Override
                public boolean mousePressed(double mx, double my, int button) {
                    if (mx < x + 22) {
                        org.sprautestudio.st_addon_spraute.ui.widgets.ColorPicker.open(0xFF000000 | get.getAsInt(), x - 150, y + h + 2, v -> {
                            set.accept(0xFF000000 | v);
                            f.setText(String.format("#%06X", v & 0xFFFFFF));
                        });
                        return true;
                    }
                    return super.mousePressed(mx, my, button);
                }
            });
            r.tooltip("Цвет #RRGGBB; щелчок по квадрату — палитра");
        }

        void skin(String label, EngineConfig.Skin skin) {
            add(new SkinRow(label, skin));
        }

        @Override
        public void layout() {
            int cy = y;
            for (Widget c : children()) {
                c.bounds(x, cy, w, c.prefH);
                c.layout();
                cy += c.prefH;
            }
            prefH = cy - y + 8;
        }
    }

    private static final int LABEL_W = 104;

    private class Row extends Widget {
        final String label;
        Widget editor;

        Row(String label) {
            this.label = label;
            prefH = 22;
        }

        @Override
        public void layout() {
            if (editor != null) {
                editor.bounds(x + LABEL_W, y + 2, w - LABEL_W - 10, 18);
                editor.layout();
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.textEllipsized(label, x + 10, y + 7, LABEL_W - 14, Theme.TEXT_DIM);
        }
    }

    /** Picture choice plus the 9-slice switch and its borders. */
    private final class SkinRow extends Row {
        final EngineConfig.Skin skin;
        final Checkbox nine;
        final TextField[] borders = new TextField[5];

        SkinRow(String label, EngineConfig.Skin skin) {
            super(label);
            this.skin = skin;
            Dropdown<String> dd = new Dropdown<>(EngineTab.this::imageNames, () -> skin.image == null || skin.image.isEmpty() ? null : skin.image,
                    v -> v.isEmpty() ? "— нет —" : v, v -> {
                        skin.image = v;
                        sizeBorders();
                    });
            dd.placeholder = "— нет (картинка из «Картинок») —";
            editor = add(dd);
            nine = add(new Checkbox("9-slice", () -> skin.nine, v -> {
                skin.nine = v;
                EngineTab.this.relayout();
            }));
            nine.tooltip("Углы картинки не растягиваются, края тянутся по одной оси, середина — по обеим. Для рамок и панелей");
            String[] names = {"Слева", "Сверху", "Справа", "Снизу", "Масштаб"};
            for (int i = 0; i < 5; i++) {
                TextField f = add(new TextField());
                final int idx = i;
                f.setText(i == 4 ? trim(skin.scale) : String.valueOf(new int[]{skin.left, skin.top, skin.right, skin.bottom}[i]));
                f.filter = ch -> Character.isDigit(ch) || (idx == 4 && ch == '.');
                f.onChange = s -> setBorder(idx, s);
                f.tooltip(i == 4 ? "Масштаб углов: 1 — пиксель картинки = пиксель интерфейса, 2 — вдвое крупнее" : names[i] + ": ширина края в пикселях картинки");
                borders[i] = f;
            }
        }

        /** A fresh picture gets borders of a quarter of its smaller side. */
        private void sizeBorders() {
            if (!skin.isSet()) return;
            for (Project.ImageRef r : project.images()) {
                if (!r.name().equals(skin.image)) continue;
                ProjectImages.Loaded l = ProjectImages.load(project, r);
                if (l == null) return;
                int b = Math.max(1, Math.min(l.width(), l.height()) / 4);
                skin.left = skin.top = skin.right = skin.bottom = b;
                for (int i = 0; i < 4; i++) borders[i].setText(String.valueOf(b));
            }
        }

        private void setBorder(int i, String s) {
            try {
                if (i == 4) {
                    skin.scale = Math.max(0.1f, Float.parseFloat(s.trim()));
                    return;
                }
                int v = Math.max(0, Integer.parseInt(s.trim()));
                switch (i) {
                    case 0 -> skin.left = v;
                    case 1 -> skin.top = v;
                    case 2 -> skin.right = v;
                    default -> skin.bottom = v;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        public void layout() {
            prefH = skin.nine ? 66 : 42;
            int ew = w - LABEL_W - 10;
            editor.bounds(x + LABEL_W, y + 2, ew, 18);
            editor.layout();
            nine.bounds(x + LABEL_W, y + 23, 70, 16);
            int fw = Math.max(24, (ew - 4 * 4) / 5);
            for (int i = 0; i < 5; i++) {
                TextField f = borders[i];
                f.visible = skin.nine;
                f.bounds(x + LABEL_W + i * (fw + 4), y + 44, fw, 18);
                f.layout();
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            super.paint(c, mx, my, dt);
            if (skin.nine) c.text("края и масштаб", x + LABEL_W + 76, y + 27, Theme.TEXT_MUTED);
        }
    }

    private static String trim(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }

    // ================================================================== layout & screen

    private static final int FORM_W = 330;

    private int formW() {
        return formOnly() ? w : Math.min(FORM_W, w / 2);
    }

    @Override
    public void layout() {
        int fw = formW();
        scroll.bounds(x, y, fw, h - 34);
        form.layout();
        scroll.layout();
        apply.bounds(x + 8, y + h - 27, apply.prefW, 20);
        reset.bounds(x + 14 + apply.prefW, y + h - 27, reset.prefW, 20);
        int px = x + fw + 10;
        // the section buttons wrap onto more rows when they don't fit
        int bx = px, by = y + 6;
        for (Button b : List.of(hudBtn, chatBtn, choiceBtn, shopBtn, zoneBtn, questBtn)) {
            if (bx + b.prefW > x + w - 6 && bx > px) {
                bx = px;
                by += 22;
            }
            b.bounds(bx, by, b.prefW, 18);
            bx += b.prefW + 4;
        }
        top = by - y + 22;
        // a page of the studio: the studio has the buttons, the screen starts right at the top
        if (page != null) {
            top = -20;
            scroll.bounds(x, y, fw, h);
            form.layout();
            scroll.layout();
        }
        super.layout();
    }

    /** Places the virtual screen in the free area; returns false when there is no room. */
    private boolean screenArea() {
        int fw = formW();
        int px = x + fw + 10, py = y + top + 26, pw = w - fw - 20, ph = h - top - 26 - 30;
        if (pw < 60 || ph < 40) return false;
        k = Math.min(pw / (float) VW, ph / (float) VH);
        int sw = Math.round(VW * k), sh = Math.round(VH * k);
        sx = px + (pw - sw) / 2;
        sy = py + (ph - sh) / 2;
        return true;
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        int fw = formW();
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y, fw, h, Theme.PANEL);
        c.fill(x + fw, y, 1, h, Theme.BORDER);
        c.fill(x, y + h - 34, fw, 1, Theme.BORDER);
        int px = x + fw + 10;
        // element chips: also the way to reach hidden parts
        if (mode == Mode.HUD) {
            int cx = px;
            for (String key : EngineConfig.ELEMENTS) {
                if (!present(key)) continue;
                String t = EngineConfig.title(key);
                int tw = c.width(t) + 12;
                boolean sel = key.equals(selected), vis = cfg.element(key).visible;
                boolean hov = mx >= cx && mx < cx + tw && my >= y + top + 2 && my < y + top + 18;
                c.round(cx, y + top + 2, tw, 16, 3, sel ? Theme.ACCENT : hov ? Theme.RAISED : Theme.INPUT);
                c.text(t, cx + 6, y + top + 6, sel ? 0xFFFFFFFF : vis ? Theme.TEXT : Theme.TEXT_MUTED);
                cx += tw + 4;
            }
        }
        if (!screenArea()) return;
        int sw = Math.round(VW * k), sh = Math.round(VH * k);
        c.shadow(sx, sy, sw, sh, 4, 8);
        c.pushClip(sx, sy, sw, sh);
        c.pushTransform(sx, sy, k);
        var g = c.g;
        var font = Minecraft.getInstance().font;
        g.fillGradient(0, 0, VW, VH * 2 / 3, 0xFF6FA8DC, 0xFFB9D7F0);
        g.fillGradient(0, VH * 2 / 3, VW, VH, 0xFF5D8C3A, 0xFF3F6327);
        HudPainter.State s = sampleState();
        if (mode == Mode.HUD) {
            for (String key : EngineConfig.ELEMENTS) {
                if (!present(key)) continue;
                EngineConfig.Element e = cfg.element(key);
                int[] r = HudPainter.rect(cfg, key, VW, VH, s);
                if (e.visible) {
                    if (EngineConfig.gameDrawn(e.style)) HudPainter.standard(g, font, key, e.style, r, s);
                    else HudPainter.custom(g, font, cfg, key, r, s);
                } else if (key.equals(selected)) {
                    g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x33FFFFFF);
                }
            }
            int[] r = HudPainter.rect(cfg, selected, VW, VH, s);
            g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFF4FC3F7);
            if (canResize()) g.fill(r[0] + r[2] - 2, r[1] + r[3] - 2, r[0] + r[2] + 2, r[1] + r[3] + 2, 0xFF4FC3F7);
        } else if (mode == Mode.SHOP) {
            g.fill(0, 0, VW, VH, 0x88000000);
            shopSample.style = cfg.shop;
            shopSample.layout(VW, VH);
            shopSample.render(g, font, cfg, vx(mx), vy(my));
            int[] pr = shopSample.partRect(shopPart, font);
            g.renderOutline(pr[0] - 1, pr[1] - 1, pr[2] + 2, pr[3] + 2, 0xFF4FC3F7);
        } else if (mode == Mode.CHOICE) {
            choicePreview(g, font);
        } else if (mode == Mode.QUEST) {
            EngineConfig.Quests q = cfg.quests;
            g.fill(0, 0, VW, VH, q.shadeColor);
            questK = Math.min((VW - 16f) / org.sprautestudio.st_addon_spraute.quest.client.QuestScreen.W, (VH - 16f) / org.sprautestudio.st_addon_spraute.quest.client.QuestScreen.H) * q.scale;
            questOX = (VW - org.sprautestudio.st_addon_spraute.quest.client.QuestScreen.W * questK) / 2f;
            questOY = (VH - org.sprautestudio.st_addon_spraute.quest.client.QuestScreen.H * questK) / 2f;
            questSample.styleFrom = cfg;
            g.pose().pushPose();
            g.pose().translate(questOX, questOY, 0);
            try {
                questSample.paintPreview(g, questK, vx(mx) - questOX, vy(my) - questOY);
            } catch (Throwable ignored) {
            }
            g.pose().popPose();
            int[] o = q.off(questPart);
            int[] pr = questPart.equals("card") ? new int[]{150, 2, 240, 225} : new int[]{0, 0, 144, 230};
            g.renderOutline(Math.round(questOX + (pr[0] + o[0]) * questK) - 1, Math.round(questOY + (pr[1] + o[1]) * questK) - 1,
                    Math.round(pr[2] * questK) + 2, Math.round(pr[3] * questK) + 2, 0xFF4FC3F7);
        } else if (mode == Mode.ZONE) {
            g.fill(0, 0, VW, VH, 0x55000000);
            EngineConfig.ZoneTitle z = cfg.zoneTitle;
            long loop = (long) (z.seconds * 1000) + 700;
            long t = (System.currentTimeMillis() - zonePreviewAt) % loop;
            org.sprautestudio.st_addon_spraute.zone.client.ZoneClient.drawTitle(g, font, VW, VH, z, cfg.project, "Деревня Ольховка", "Тихое место у реки", t);
            int cx = Math.round(VW * z.x), cy = Math.round(VH * z.y);
            g.fill(cx - 3, cy, cx + 4, cy + 1, 0xFF4FC3F7);
            g.fill(cx, cy - 3, cx + 1, cy + 4, 0xFF4FC3F7);
        } else {
            g.fill(0, 0, VW, VH, 0x88000000);
            int[] r = HudPainter.chatRect(VW, VH);
            HudPainter.chatBack(g, cfg, r[0], r[1], r[2], r[3]);
            if (!cfg.chatLog.frameOnTop()) HudPainter.chatFrame(g, cfg, r[0], r[1], r[2], r[3]);
            String[] lines = {"Алиса: Ты наконец проснулся?", "Игрок: Где я?", "Алиса: В деревне у реки. Пойдём,", "покажу дорогу."};
            int ly = VH / 2 - 30;
            for (String l : lines) {
                g.drawString(font, l, r[0] + r[2] / 6, ly, 0xFFFFFFFF, true);
                ly += 14;
            }
            if (cfg.chatLog.frameOnTop()) HudPainter.chatFrame(g, cfg, r[0], r[1], r[2], r[3]);
        }
        c.popTransform();
        c.popClip();
        c.outline(sx - 1, sy - 1, sw + 2, sh + 2, Theme.BORDER_STRONG);
        String hint;
        if (mode == Mode.CHAT) hint = "Примерный вид окна истории сообщений";
        else if (mode == Mode.QUEST) {
            int[] o = cfg.quests.off(questPart);
            hint = (questPart.equals("list") ? "Список квестов" : "Карточка квеста") + "  сдвиг " + o[0] + ", " + o[1] + "   ·   тяните части журнала, стрелки — точный сдвиг";
        }
        else if (mode == Mode.ZONE) hint = "Название зоны: щёлкните или тяните, чтобы поставить его центр  (" + Math.round(cfg.zoneTitle.x * 100) + "%, "
                + Math.round(cfg.zoneTitle.y * 100) + "%)";
        else if (mode == Mode.SHOP) {
            int[] o = shopSample.off(shopPart);
            hint = org.sprautestudio.st_addon_spraute.shop.client.ShopPanel.partTitle(shopPart) + "  сдвиг " + o[0] + ", " + o[1]
                    + "   ·   перетаскивайте части окна, стрелки — точный сдвиг, колесо — листать товары";
        }
        else if (mode == Mode.CHOICE) {
            int[] o = org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.offset(cfg.choice, choicePart);
            hint = org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.title(choicePart) + "  сдвиг " + o[0] + ", " + o[1]
                    + "   ·   перетаскивайте части меню, стрелки — точный сдвиг";
        }
        else {
            int[] r = HudPainter.rect(cfg, selected, VW, VH, s);
            hint = EngineConfig.title(selected) + "  " + r[0] + ", " + r[1] + "  " + r[2] + "×" + r[3]
                    + "   ·   перетаскивайте элементы, стрелки — сдвиг, колёсико — заполненность " + Math.round(sample * 100) + "%";
        }
        c.textEllipsized(hint, px, y + h - 22, w - fw - 20, Theme.TEXT_MUTED);
    }

    private boolean zoneDrag;

    /** Part of the choice menu being moved in the preview. */
    private String choicePart = "answers";
    private int[] choiceDragStart;
    private static final List<String> SAMPLE_ANSWERS = List.of("Да", "Нет");

    /** The choice menu at the virtual screen size, drawn by the same code as in the game. */
    private void choicePreview(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font) {
        EngineConfig.Choice ch = cfg.choice;
        if (!ch.style.equals("spraute")) {
            g.drawCenteredString(font, "Меню выбора Spraute", VW / 2, VH / 2, 0xFFFFFFFF);
            return;
        }
        var subject = ch.show.equals("none") ? null : org.sprautestudio.st_addon_spraute.engine.client.ChoiceScreen.previewSubject(ch);
        org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.draw(g, font, cfg, VW, VH, new org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.Content("Readix", "Пойдёшь со мной?", 999,
                SAMPLE_ANSWERS, 0, subject, VW / 2f, VH / 2f));
        int[] r = org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.rects(ch, font, VW, VH, SAMPLE_ANSWERS).get(choicePart);
        if (r != null) {
            int ry = choicePart.equals("panel") ? r[1] + 190 : r[1], rh = choicePart.equals("panel") ? r[3] - 190 : r[3];
            g.renderOutline(r[0] - 1, ry - 1, r[2] + 2, rh + 2, 0xFF4FC3F7);
        }
    }

    private boolean choicePress(double mx, double my) {
        double px = vx(mx), py = vy(my);
        if (px < 0 || py < 0 || px > VW || py > VH) return false;
        var font = Minecraft.getInstance().font;
        String hit = org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.partAt(cfg.choice, font, VW, VH, SAMPLE_ANSWERS, px, py);
        if (hit == null) return true;
        choicePart = hit;
        int[] o = org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.offset(cfg.choice, hit);
        choiceDragStart = new int[]{o[0], o[1]};
        dragging = true;
        moved = false;
        dragX = px;
        dragY = py;
        buildForm();
        return true;
    }

    private void choiceMove(int dx, int dy, boolean fromStart) {
        int[] base = fromStart ? choiceDragStart : org.sprautestudio.st_addon_spraute.engine.client.ChoiceLayout.offset(cfg.choice, choicePart);
        cfg.choice.offsets.put(choicePart, new int[]{base[0] + dx, base[1] + dy});
    }

    private boolean canResize() {
        return !EngineConfig.gameDrawn(cfg.element(selected).style);
    }

    private double vx(double mx) {
        return (mx - sx) / k;
    }

    private double vy(double my) {
        return (my - sy) / k;
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        if (super.mousePressed(mx, my, button)) return true;
        int fw = formW();
        if (mx >= x + fw && mode == Mode.CHOICE && screenArea() && button == 0) return choicePress(mx, my);
        if (mx >= x + fw && mode == Mode.QUEST && screenArea() && button == 0) {
            double px = vx(mx), py = vy(my);
            if (px < 0 || py < 0 || px > VW || py > VH) return false;
            String part = questPartAt((px - questOX) / questK, (py - questOY) / questK);
            if (part == null) return true;
            if (!part.equals(questPart)) {
                questPart = part;
                buildForm();
            }
            questDragStart = cfg.quests.off(questPart).clone();
            dragging = true;
            moved = false;
            dragX = px;
            dragY = py;
            return true;
        }
        if (mx >= x + fw && mode == Mode.ZONE && screenArea() && button == 0) {
            double px = vx(mx), py = vy(my);
            if (px < 0 || py < 0 || px > VW || py > VH) return false;
            cfg.zoneTitle.x = (float) Math.round(px / VW * 100) / 100f;
            cfg.zoneTitle.y = (float) Math.round(py / VH * 100) / 100f;
            dragging = true;
            moved = true;
            zoneDrag = true;
            return true;
        }
        if (mx >= x + fw && mode == Mode.SHOP && screenArea() && button == 0) {
            double px = vx(mx), py = vy(my);
            if (px < 0 || py < 0 || px > VW || py > VH) return false;
            String part = shopPart.equals("window") ? "window"
                    : shopSample.partAt(px, py, Minecraft.getInstance().font);
            if (part == null) return true;
            if (!part.equals(shopPart)) {
                shopPart = part;
                buildForm();
            }
            shopDragStart = shopSample.off(shopPart).clone();
            dragging = true;
            moved = false;
            dragX = px;
            dragY = py;
            return true;
        }
        if (mx < x + fw || mode != Mode.HUD) return false;
        // chips
        int cx = x + fw + 10;
        var font = Minecraft.getInstance().font;
        for (String key : EngineConfig.ELEMENTS) {
            if (!present(key)) continue;
            int tw = font.width(EngineConfig.title(key)) + 12;
            if (mx >= cx && mx < cx + tw && my >= y + top + 2 && my < y + top + 18) {
                select(key);
                return true;
            }
            cx += tw + 4;
        }
        if (!screenArea() || button != 0) return false;
        double px = vx(mx), py = vy(my);
        if (px < 0 || py < 0 || px > VW || py > VH) return false;
        HudPainter.State s = sampleState();
        int[] sel = HudPainter.rect(cfg, selected, VW, VH, s);
        resizing = canResize() && Math.abs(px - (sel[0] + sel[2])) <= 4 && Math.abs(py - (sel[1] + sel[3])) <= 4;
        if (!resizing) {
            String hit = null;
            List<String> order = new ArrayList<>(EngineConfig.ELEMENTS);
            java.util.Collections.reverse(order);
            for (String key : order) {
                if (!present(key) || !cfg.element(key).visible) continue;
                int[] r = HudPainter.rect(cfg, key, VW, VH, s);
                if (px >= r[0] - 2 && px <= r[0] + r[2] + 2 && py >= r[1] - 2 && py <= r[1] + r[3] + 2) {
                    hit = key;
                    break;
                }
            }
            if (hit == null) return true;
            if (!hit.equals(selected)) select(hit);
        }
        EngineConfig.Element e = cfg.element(selected);
        dragging = true;
        moved = false;
        dragX = px;
        dragY = py;
        dragStart = HudPainter.rect(cfg, selected, VW, VH, s);
        if (resizing) dragStart = new int[]{dragStart[0], dragStart[1], e.width, e.height};
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mx, my, button, dx, dy);
        int ddx = (int) Math.round(vx(mx) - dragX), ddy = (int) Math.round(vy(my) - dragY);
        if (!moved && Math.abs(ddx) + Math.abs(ddy) < 2) return true;
        if (mode == Mode.CHOICE) {
            choiceMove(ddx, ddy, true);
            moved = true;
            return true;
        }
        if (mode == Mode.QUEST) {
            cfg.quests.offsets.put(questPart, new int[]{questDragStart[0] + Math.round(ddx / questK), questDragStart[1] + Math.round(ddy / questK)});
            moved = true;
            return true;
        }
        if (mode == Mode.ZONE && zoneDrag) {
            cfg.zoneTitle.x = (float) Math.max(0, Math.min(1, Math.round(vx(mx) / VW * 100) / 100.0));
            cfg.zoneTitle.y = (float) Math.max(0, Math.min(1, Math.round(vy(my) / VH * 100) / 100.0));
            return true;
        }
        if (mode == Mode.SHOP) {
            cfg.shop.offsets.put(shopPart, new int[]{shopDragStart[0] + ddx, shopDragStart[1] + ddy});
            moved = true;
            return true;
        }
        EngineConfig.Element e = cfg.element(selected);
        if (resizing) {
            switch (e.style) {
                case "icons", "slots", "icon" -> e.height = Math.max(4, dragStart[3] + ddy);
                default -> {
                    e.width = Math.max(4, dragStart[2] + ddx);
                    e.height = Math.max(2, dragStart[3] + ddy);
                }
            }
        } else {
            boolean wasCustom = e.customPos;
            if (!e.customPos) {
                e.customPos = true;
                e.anchor = nearestAnchor(dragStart);
            }
            int nx = dragStart[0] + ddx, ny = dragStart[1] + ddy;
            // snap the part's centre to the screen's centre line
            int cxm = nx + dragStart[2] / 2;
            if (Math.abs(cxm - VW / 2) <= 3) nx = VW / 2 - dragStart[2] / 2;
            place(selected, e, new int[]{nx, ny, dragStart[2], dragStart[3]});
            if (!wasCustom) buildForm();
        }
        moved = true;
        return true;
    }

    /** The anchor of the screen region a rectangle is in, so it keeps its place on other screen sizes. */
    private static String nearestAnchor(int[] r) {
        int cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
        String col = cx < VW / 3 ? "L" : cx > VW * 2 / 3 ? "R" : "";
        String row = cy < VH / 3 ? "T" : cy > VH * 2 / 3 ? "B" : "";
        if (row.isEmpty() && col.isEmpty()) return "C";
        if (row.isEmpty()) return col;
        if (col.isEmpty()) return row;
        return row + col;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            resizing = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx > x + formW() && mode == Mode.SHOP) {
            shopSample.scroll(delta);
            return true;
        }
        if (mx > x + formW() && mode == Mode.HUD) {
            sample = Math.max(0, Math.min(1, sample + (float) delta * 0.05f));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (super.keyPressed(key, scan, mods)) return true;
        if ((mode != Mode.HUD && mode != Mode.CHOICE && mode != Mode.SHOP && mode != Mode.QUEST) || !isHoveredDeep()) return false;
        int step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        int dx = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> -step;
            case GLFW.GLFW_KEY_RIGHT -> step;
            default -> 0;
        };
        int dy = switch (key) {
            case GLFW.GLFW_KEY_UP -> -step;
            case GLFW.GLFW_KEY_DOWN -> step;
            default -> 0;
        };
        if (dx == 0 && dy == 0) return false;
        if (mode == Mode.CHOICE) {
            choiceMove(dx, dy, false);
            return true;
        }
        if (mode == Mode.SHOP) {
            int[] o = shopSample.off(shopPart);
            cfg.shop.offsets.put(shopPart, new int[]{o[0] + dx, o[1] + dy});
            return true;
        }
        if (mode == Mode.QUEST) {
            int[] o = cfg.quests.off(questPart);
            cfg.quests.offsets.put(questPart, new int[]{o[0] + dx, o[1] + dy});
            return true;
        }
        EngineConfig.Element e = cfg.element(selected);
        if (!e.customPos) {
            int[] r = HudPainter.rect(cfg, selected, VW, VH, sampleState());
            e.customPos = true;
            e.anchor = nearestAnchor(r);
            place(selected, e, r);
            buildForm();
        }
        int[] r = HudPainter.rect(cfg, selected, VW, VH, sampleState());
        place(selected, e, new int[]{r[0] + dx, r[1] + dy, r[2], r[3]});
        return true;
    }

    private void select(String key) {
        selected = key;
        buildForm();
    }

    // ================================================================== tab

    @Override
    public String title() {
        return "Настройки движка";
    }

    @Override
    public Icon icon() {
        return Icon.GEAR;
    }

    @Override
    public int iconColor() {
        return Theme.ACCENT;
    }

    @Override
    public boolean isDirty() {
        return !cfg.toJson().equals(savedJson);
    }

    @Override
    public void save() throws IOException {
        cfg.project = project.name();
        String json = cfg.toJson();
        Files.writeString(file(project), json, StandardCharsets.UTF_8);
        savedJson = json;
        if (Minecraft.getInstance().getConnection() != null) ClientState.get().engine(json);
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
