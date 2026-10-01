# Spraute Engine — язык скриптов (`.spr`)

Справка собрана по исходникам: `ScriptParser`, `ScriptCompiler`, `ScriptExecutor`, `FunctionRegistry`, функции в `script/function/`.

---

## Синтаксис и ключевые слова

| Ключевое слово | Назначение |
|----------------|------------|
| `val` | Локальная переменная |
| `global val` | Глобальная переменная (в рамках выполнения скрипта) |
| `world val` | Переменная сейва (общая для всех измерений; хранится в `ScriptWorldData` на overworld, переживает перезапуск сервера) |
| `import("name")` | Подключает `fun` из скрипта `name.spr` и **запускает** его, если он ещё не running |
| `await` | Ожидание события / времени / задачи, а также `await npc.moveTo(...)` / `await npc.flyTo(...)` |
| `create npc` | Блок создания NPC |
| `create model` | Блок создания geo-модели (без AI) |
| `create ui` | Блок описания UI (шаблон + обработчики) |
| `create block` | Декларация кастомного блока (дроп, руда в мире — см. раздел «Создание кастомных ресурсов») |
| `create item` | Декларация кастомного предмета (см. раздел "Создание кастомных ресурсов") |
| `create craft` | Рецепт крафта (`any` / `slots`, замена ингредиентов) |
| `create drop` | Кастомный дроп при ломании блока (в т.ч. ванильного) |
| `create particle` | Декларация кастомного партикла (см. раздел "Создание кастомных ресурсов") |
| `create world` | Кастомное измерение (небо, туман, облака, время) |
| `camera` | Декларативная катсцена (`camera id { pos, smoothTime, flyTime, … }`) |
| `if` / `else` / `else if` | Условия |
| `while` | Цикл |
| `for (i in range(...))` | Цикл **только** по `range()` (см. ниже) |
| `fun` / `return` | Пользовательские функции |
| `on` | Обработчик события в фоне |
| `every` | Периодический таймер |
| `stop` | Остановка обработчика `on` / `every` |
| `async` | Асинхронный блок |
| `true` / `false` | Литералы |

Операторы: `+`, `-`, `*`, `/`, `//`, `%`, `**`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&`, `||`, `!`, строковая конкатенация через `+`.  
Комментарии в коде **только** через `#` до конца строки.  
`/` — обычное деление; `//` — **целочисленное деление вниз** (floor), как в Python: для двух `int` результат `int` (`Math.floorDiv`), иначе `Math.floor(a/b)` как `double`. Это **не** корень из числа (для степени используйте `**`, например `x ** 0.5`). `%` — остаток: для двух `int` — `Math.floorMod`, для `double` — `%`.

---

## Сцены (`/spraute run` / `/spraute stop`)

`/spraute run <id>` загружает **`spraute_engine/scenes/<id>.scene.json`** (не `.spr` напрямую):

1. Спавнит NPC / MODEL / HITBOX / EMPTY с координатами и параметрами из файла.
2. Запускает SCRIPT-ноды с `runOnSceneStart = true`.
3. Повторный `/spraute run` той же сцены: unload (сущности + остановка scene-скриптов) → load заново.

`/spraute stop <id>` — выгружает сцену: **все объекты исчезают**, scene-скрипты останавливаются.  
`/spraute stop` без аргумента — останавливает **все** активные сцены.

В теле scene-скрипта доступны:

| Переменная | Значение |
|------------|----------|
| `_scene` | `SceneView` активной сцены |
| `_node` | родитель SCRIPT-ноды (`SceneNodeView`) |
| `_scriptNode` | сама SCRIPT-нода |

### Паритет сцена ↔ скрипт

Всё, что есть в иерархии сцены, можно создать через `create` в скрипте (и наоборот).  
Если скрипт запущен из сцены (`_scene`), `create` **привязывает** объекты к этой сцене — они уйдут вместе с `/spraute stop`.

```spr
# родитель: нода сцены, id/имя, или _node по умолчанию
create empty arena {
    x = 0
    y = 64
    z = 0
}

create npc guard {
    model = "geo/defolt.geo.json"
    parent = arena          # или parent = _node
    pos = 10, 64, 0
}

create model prop {
    model = "geo/crate.geo.json"
    parent = guard
    x = 0
    y = 1
    z = 0
}

create hitbox zone {
    parent = arena
    width = 3
    height = 2
    depth = 3
    solid = true
    trigger = true
    x = 0
    y = 64
    z = 5
}

create light lamp {
    parent = arena
    level = 15
    enabled = true
    x = 0
    y = 66
    z = 5
}
```

Вне сцены: `create npc` / `create model` работают как раньше (свободный спавн).  
`create hitbox` / `create empty` / `create light` / `create particleEmitter` требуют активную сцену (`_scene`), `parent` на ноду сцены, или `scene = "id"`.

`create light` ставит невидимый `minecraft:light` (уровень 0–15, только белый). Цветной свет в ванили нет — пока не делаем.

### Кастомные разделы и ноды (`declare`)

В `.spr`, привязанном к **SCRIPT-ноде открытой (активной) сцены**, можно объявить **раздел** иерархии и **тип ноды** (без позиции в мире / на канвасе). Скрипты вне этой сцены в Hierarchy не попадают. После **сохранения скрипта** типы появляются в Hierarchy → меню раздела.

Встроенный пример — `economy.spr`: нода **Экономика → Торговля** на NPC. ПКМ открывает **UI из economy.spr** (`ecoOpenTrade`), монеты — `player.savedData["coins"]`. Скрипт `economy` должен быть запущен (SCRIPT в сцене).

```spr
declare section Экономика

declare node type economy {
    section = "Экономика"
    label = "Торговля"
    icon = "item"

    field canTrade bool {
        label = "Можно трейдить"
        default = true
    }

    field title text {
        label = "Название"
        default = "Торговля"
    }

    list items {
        label = "Товары"
        addLabel = "Добавить предмет"
        field item item { label = "Предмет" }
        field name text { label = "Имя" default = "" }
        field buyPrice number { label = "Купить за" default = 10 }
        field sellPrice number { label = "Продать за" default = 5 }
        field canBuy bool { label = "Покупка" default = true }
        field canSell bool { label = "Продажа" default = true }
    }
}
```

**Поля:** `text`, `number`, `bool`, `color`, `item`, `block`, `dropdown(метка: value, …)`, `button` (с `appendList = "items"`), **`list`** — повторяемые карточки.

```spr
create economy shop {
    parent = myNpc
    canTrade = true
    title = "Кузнец"
    items = [
        { item = "minecraft:iron_ingot", name = "Железо", buyPrice = 20, sellPrice = 8, canBuy = true, canSell = true }
    ]
}
```

Логика ПКМ — в `economy.spr` (`on interact` → `ecoOpenShopNode` → `ecoOpenTrade`). События: `registerEvent("tradeBuy")` / `fireEvent("tradeBuy", …)` / `on tradeBuy`.

Данные ноды: `shop.title`, `shop.items`, `shop.data` (вся map).

Координаты ноды/NPC:

```spr
_node.x = 10          # основной короткий API
_node.transform.x = 10 # то же самое (живой alias)
guard.x                # у create npc / id сущности — плоский доступ
guard.transform.x      # тоже работает
```

Id и связи, когда объект уже есть:

```spr
_node.id               # id ноды сцены
_node.type             # npc / model / camera / …
_node.parent           # родительская нода (или null)
_node.parentId         # id родителя ("" если корень)
_node.children         # список дочерних нод
_node.scene            # сцена
_node.sceneId
_node.uuid             # UUID заспавненной сущности, если есть

create npc guard { … }
guard.id               # "guard" (id в иерархии или ключ create npc)
guard.node             # SceneNodeView, если NPC из сцены (иначе null)
guard.uuid
sceneNodeForEntity(guard)  # то же, что guard.node
```

Полезные функции: `sceneLoad`, `sceneUnload`, `sceneGetNode`, `sceneFindByTag`, `sceneView`, `sceneNodeForEntity`, …

Библиотеки без сцены по-прежнему: `startScript("name")` / `import("name")` / `autorun`.

### GUI-ноды сцены (Канвас)

В редакторе сцен доступна нода **Канвас** (`CANVAS`) — корень GUI. На неё вешаются виджеты: **Сетка** (`UI_GRID`), **Текст** (`UI_TEXT`), **Кнопка** (`UI_BUTTON`), **Картинка** (`UI_IMAGE`), **SVG** (`UI_SVG`), **Видео** (`UI_VIDEO`), **Панель** (`UI_RECT`), **Поле ввода** (`UI_INPUT`), **Скролл** (`UI_SCROLL`), **Разделитель** (`UI_DIVIDER`), **Предмет/Блок** (`UI_ITEM`), **Рендер** (`UI_ENTITY`), **Слот** (`UI_SLOT`), **Инвентарь игрока** (`UI_INVENTORY`).

- Координаты `x/y` из трансформа виджета — это **пиксели** относительно родителя, `rz` — поворот в градусах, `sx/sy` — масштаб. Для каждой оси (X/Y/W/H) можно переключить единицы **px ↔ %** (проценты считаются от родителя/канваса).
- В инспекторе канваса и виджетов есть **интерактивный предпросмотр**: виджеты видны сразу, клик выбирает, перетаскивание двигает.
- Цвета выбираются палитрой с альфа-каналом; градиент настраивается редактором: угол (0–360°), основной цвет слева, конечный справа, можно добавлять промежуточные цвета, живое превью.
- Свойства канваса: размер (`ширина/высота`), фон, `overlay` (оверлей поверх экрана вместо полноэкранного UI), `autoShow` (показать всем при загрузке сцены и новым игрокам при входе), `canClose`, затемнение фона, **слой** (`layer`): канвасы рисуются по возрастанию слоя, слой < 0 — под интерфейсом Minecraft.
- Все виджеты поддерживают стили как в `create ui`: `radius`, `gradient`, `gradientDir`, `border`, `borderColor`, `borderGradient`, `borderGradientDir`, `outerBorder`, `outerBorderColor`, `outerBorderGradient`, `outerBorderGradientDir`, `shadow`. `gradientDir` теперь принимает и **угол в градусах** (`gradientDir = "45"`), а `gradient` — несколько цветов через запятую (`gradient = "#FF8800FF,#FF0044AA"`). Если в `gradient` указано 2+ цвета, основной цвет виджета не участвует — заливка идёт только по цветам градиента (в редакторе оба начальных цвета задаются прямо в меню градиента). `borderGradient` работает так же для внутренней рамки, `outerBorderGradient` — для наружной обводки.
- **Сетка** раскладывает детей по ячейкам: колонки, ширина/высота ячейки, отступы X/Y, растягивание детей под ячейку.
- **Скрипты и аниматоры внутри GUI**: канвасу и любому виджету можно добавить дочернюю ноду «Скрипт» (запускается со сценой, `_node` = канвас/виджет) и «Аниматор». GUI-аниматор проигрывает клип из `.animation.json`: у аниматора-ребёнка канваса имя кости в клипе = идентификатор виджета (одним клипом можно анимировать несколько виджетов, как риг), аниматор-ребёнок виджета двигает сам виджет любой костью. Каналы: `position` → смещение X/Y в пикселях (Y Blockbench «вверх» → на экране «вверх»), `rotation.z` → поворот в градусах, `scale` → множитель ширины/высоты. Луп берётся из клипа; работает и в превью редактора.
- **SVG** (`UI_SVG`): файл из `textures/**/*.svg`, растеризация на клиенте. Свойства: `source` / `svg`, `color` (подмена `currentColor`), `stroke`, `strokeWidth`, `recolor`. Обновление открытого UI: `uiUpdate(player, id, "source"|"color"|"stroke"|"strokeWidth"|"recolor", …)`.
- **Видео** (`UI_VIDEO`): MP4/H.264, декод на клиенте. Свойства: `video` / `source`, `autoplay`/`playing`, `loop`, `enabled`/`playerEnabled`, `audio`/`videoAudio`, `volume`. Управление из скрипта — `uiVideoPlay`, `uiVideoPause`, … (см. раздел UI ниже).
- **Слоты и инвентарь**: нода «Слот» — контейнерный слот 18x18 px (id ноды = id слота), нода «Инвентарь игрока» — сетка 9x3 + хотбар (162x76 px). Канвас, в котором есть хотя бы один слот/инвентарь, открывается как **контейнер-меню** (то же, что `slot(...)` / `playerInventory(...)` в `create ui`), поэтому он не может быть оверлеем, а размеры/позиции слотов лучше держать в пикселях. Со слотами работают те же функции: `uiSlotItem`, `uiSlotIsEmpty`, `uiSlotCount`, `uiSlotHasItem`, `uiSetSlot`, `uiClearSlot`.
- **Проверка кастомных предметов в слоте**:

```text
uiSlotIsCustom(player, "мой_слот")            // true, если в слоте лежит кастомный (create item/block) предмет
uiSlotCustomId(player, "мой_слот")            // id кастомного предмета без неймспейса ("" если пусто/не кастомный)
uiSlotHasCustom(player, "мой_слот", "ruby")   // true, если лежит именно этот кастомный предмет
uiSlotHasItem(player, "мой_слот", "ruby")     // теперь тоже находит кастомные предметы без неймспейса
```

- При выгрузке сцены (`/spraute stop`) все канвасы сцены закрываются у игроков.

### Префабы

Любую ноду сцены (с её детьми) можно сохранить как **префаб** — файл `spraute_engine/prefabs/<имя>.prefab.json`:

- **Создание**: перетащите ноду из «Иерархии» в панель «Проект» (зона «Перетащите ноду сюда, чтобы создать префаб»), либо ПКМ по ноде → «Сохранить как префаб».
- **Использование в редакторе**: перетащите файл `[Prefab]` из «Проекта» в «Иерархию» (на нужного родителя) или двойной клик — добавится к выбранной ноде/корню. Все id внутри пересоздаются, конфликтов не будет.
- **Использование из скриптов**:

```text
var post = prefabSpawn("моя_сцена", "guard_post", 120, 64, -35)   // результат — нода (SceneNodeView), можно сразу в переменную
var turret = prefabSpawnIn("моя_сцена", "turret", post, 0, 3, 0)  // спавн внутри другой ноды (координаты локальные, как в иерархии)
prefabList()                                                       // список всех префабов

// с нодой дальше работаем как с любой нодой сцены:
post.teleport(130, 64, -35)          // переместить (вместе с заспавненными детьми)
post.transform.x = 130               // или через трансформ
var guard = post.child("guard")      // ребёнок по имени/id
guard.animation = "animations/wave.animation.json"
post.addTag("outpost")
post.reparent(otherNode)             // сменить родителя (мировая позиция сохраняется)
post.remove()                        // удалить со всеми детьми
```

Заспавненный префаб ведёт себя как обычная часть сцены: NPC/модели/билборды спавнятся, хитбоксы регистрируются, скрипты с «запуском при старте» запускаются (`_node` = родитель), канвасы с autoShow показываются, всё выгружается вместе со сценой (`/spraute stop`).

Канвас генерирует **тот же UI JSON**, что и `create ui`, поэтому управляется теми же функциями:

```spr
uiOpen(player, "my_canvas")        # открыть канвас (имя/id ноды активной сцены)
uiOpen(player, "scene1:my_canvas") # с явным указанием сцены
uiClose(player, "my_canvas")       # закрыть канвас (оверлей или полноэкранный)
uiClose(player)                    # закрыть полноэкранный UI как обычно
overlayOpen(player, "my_canvas")   # тоже работает (учитывает флаг overlay канваса)
```

`uiOpen`/`overlayOpen` сначала пытаются найти канвас в активных сценах (по имени/ id ноды или `SceneNodeView`), иначе аргумент трактуется как обычный шаблон/JSON. Без активной сцены канвас не найдётся — сцена должна быть загружена.

Виджеты канваса — обычные виджеты UI: их id равен имени ноды, поэтому все ui-функции работают и с нодой, и со строкой:

```spr
val canvas = sceneGetNode("scene1", "my_canvas")
val title = sceneGetNode("scene1", "my_canvas.title")

uiOpen(player, canvas)                    # нода канваса вместо имени
uiUpdate(player, title, "text", "Привет") # нода-виджет вместо id
uiUpdate(player, "title", "text", "Привет") # или просто имя ноды
val txt = uiGetInput(player, "name_input")  # поля ввода, скролл — так же
```

`on uiClick("кнопка")` ловит клики по кнопкам канваса по имени ноды.

---

## Встроенные функции (`FunctionRegistry`)

Имена **регистронезависимы**.

### `chat(message)`
Сообщение в чат **источнику** скрипта: игроку — `sendSystemMessage`, иначе `sendSuccess`.

### `npc(name, hp, speed, x, y, z, yaw, pitch)`
Создать NPC в мире, зарегистрировать по **отображаемому имени** в `NpcManager`, вывести сообщение об успехе.  
Типы аргументов: строка, целое HP, скорость (double), целые координаты и углы.

### `say(who, message)`
Глобальное сообщение всем: имя форматируется по конфигу / `ScriptContext` (`setNamesColor`), текст `message` дописывается.  
`who` — строка (id NPC), сущность, или другое (приводится к строке).

### `setNamesColor(color)`
Цвет имени в `say()` для **текущего** скрипта: hex `"#RRGGBB"`, именованный (`"gold"`, `"red"`, …) или `"reset"` (сброс к конфигу).

### `getNearestPlayer(anchor)`
Ближайший игрок в радиусе **50** блоков от сущности или NPC с данным id (строка). Возвращает сущность или `null`.

### `getSlot(player, slot)`
ID предмета в слоте инвентаря (`""` если пусто).  
Слоты: **0–8** хотбар, **9–35** инвентарь, **36–39** броня (ноги…голова), **40** оффхенд.  
`player` — объект `Player` или **ник** игрока (строка).

### `hasItem(player, item_id)`
Есть ли предмет с данным registry id **хотя бы в одном** слоте (включая броню и оффхенд).

### `countItem(player, item_id)`
Суммарное количество предмета по всем слотам.

### `getPlayer(name)`
Получает объект игрока по его нику.
Пример: `val p = getPlayer("Notch");`

### `healEntity(entity, amount)` / `heal(entity, amount)`
Лечит сущность, **не меняя** max HP (в отличие от `entity.hp = ...`, которое выставляет и max, и текущее).

### `getPlayerFacing(entity)`
Возвращает словарь направления взгляда (см. `player.facing()`). Работает для игрока и НИПа.

### `playersInRadius(x, y, z, radius)` / `playersNear(anchor, radius)`
Список игроков в сфере. `anchor` — игрок, НИП, id или сущность.

### `entitiesInRadius(x, y, z, radius, [filter])` / `entitiesNear(anchor, radius, [filter])`
Список сущностей в сфере. `filter`: `any`, `player`, `npc`, `mob`, `living`.

### `getPlayerMotion(player)` / `setPlayerMotion(player, vx, vy, vz)`
Вектор движения (скорость) игрока в **блоках за тик** — то же, что `getDeltaMovement` / `setDeltaMovement` в Minecraft.

* `getPlayerMotion(player)` → список `[vx, vy, vz]` или `null`, если игрок не найден.
* `setPlayerMotion(player, vx, vy, vz)` — задать скорость; можно передать список: `setPlayerMotion(player, [0.5, 0.2, -0.3])`.

Алиасы: `getPlayerMoveVector` / `setPlayerMoveVector`, `get_player_motion` / `set_player_motion`.

```text
val p = getPlayer("Steve")
val m = getPlayerMotion(p)
setPlayerMotion(p, 0, 0.5, 0)   # подбросить вверх
```

### Параметры игрока (атрибуты)

| Функция | Что делает | Ванильный ориентир |
|---------|------------|-------------------|
| `getPlayerMovementSpeed` / `setPlayerMovementSpeed` | Скорость ходьбы | ~`0.1` |
| `getPlayerJumpStrength` / `setPlayerJumpStrength` | Сила прыжка | ~`0.42` |
| `getPlayerStepHeight` / `setPlayerStepHeight` | На сколько блоков может подняться без прыжка | ~`0.6` |
| `getPlayerAttackDamage` / `setPlayerAttackDamage` | Урон рукой / оружием | ~`1` |
| `getPlayerDigSpeed` / `setPlayerDigSpeed` | Множитель скорости копания | `1.0` = ваниль |

`get*` возвращает **текущее итоговое** значение (с модификаторами). `set*` задаёт **базовое** значение атрибута (кроме копания — там множитель).

Алиасы в snake_case: `get_player_movement_speed`, `set_player_movement_speed`, и т.д.

```text
setPlayerMovementSpeed(player, 0.15)
setPlayerJumpStrength(player, 0.5)
setPlayerStepHeight(player, 1.0)
setPlayerAttackDamage(player, 8)
setPlayerDigSpeed(player, 2.0)
```

### `setBlock(x, y, z, block_id)`
Устанавливает блок по указанным координатам.
Пример: `setBlock(10, 65, -5, "minecraft:stone")`

### Свойства кастомных блоков (`create block`)

Работают только для блоков Spraute (`spraute_engine:id` или коротко `id`).

| Функция | Описание |
|---------|----------|
| `setBlockHardness(block_id, hardness)` | Меняет прочность ломания. `< 0` — неломаемый. |
| `getBlockHardness(block_id)` | Текущая прочность (или `null`, если блок не кастомный). |
| `setBlockSolid(block_id, true/false)` | Твёрдый ли блок (коллизия). `false` — можно пройти сквозь. |
| `getBlockSolid(block_id)` | `true`/`false`/`null`. |

```
setBlockHardness("magic_stone", 0.5)
setBlockSolid("magic_stone", false)
```

### Кинематографическая камера

Управляет видом **конкретного игрока** (клиентская камера, не привязка к НИПу). Нода **CAMERA** игрока — это `player.camera` (то же API, что у CAMERA в иерархии). После сцены вызывайте `stopCamera(player)` / `player.camera.reset()`.

**Плавность перелёта** — одна настройка на CAMERA-ноде. Если в вызове **нет** явного времени, берётся она. Явное время в вызове побеждает. `0` секунд = мгновенно (snap).

| Свойство | Алиасы | По умолчанию | Смысл |
|----------|--------|--------------|--------|
| `player.camera.flySmooth` | `fly_smooth`, `moveSmooth`, `cameraSmooth` | `true` | `false` — вызовы без времени прыгают мгновенно |
| `player.camera.flyTime` | `fly_time`, `smoothTime`, `flyDuration` | `0.8` | Секунды перелёта по умолчанию; `0` = резко |

Это **не** `turnSmooth` (тот крутит тело игрока при `separateTurn`).

Инспектор камеры: «Плавный перелёт» + «Время перелёта (сек)». То же пишется в скрипте и блоках ProCode.

Возвращаемое число секунд можно ждать: `await time(setCamera(...))`, `await time(player.camera.moveTo(...))`.

#### Глобальные функции

| Функция | Описание |
|---------|----------|
| `setCamera(player, x, y, z [, yaw, pitch, holdTime, smoothTime, lockMovement, hideGui, lockBlocks])` | Поставить камеру. Без `smoothTime` — `flyTime` ноды; **`smoothTime = 0` — мгновенно**. `holdTime` — сколько держать, затем возврат |
| `setCameraLookAt(player, camX, camY, camZ, lookX, lookY, lookZ, [hold, smoothTime, track, …])` | Камера в точке смотрит на координаты |
| `setCameraLookAt(player, camX, camY, camZ, entity, [hold, smoothTime, track, …])` | Смотрит на игрока/НИПа/моба; `track=true` — слежение |
| `animateCamera(player, x, y, z, yaw, pitch [, smoothTime, lockMovement, hideGui, lockBlocks])` | Перелёт активной камеры |
| `animateCameraLookAt(player, x, y, z, target [, smoothTime, track, …])` | Перелёт + взгляд |
| `cameraLookAt(player, x, y, z [, track, smoothTime])` | Доворот уже активной камеры на точку |
| `cameraLookAt(player, npcOrTarget [, track, smoothTime])` | То же на сущность. Без времени — `flyTime` |
| `stopCamera(player)` | Вернуть вид игроку за 0.5 сек |
| `stopCamera(player, smoothTime)` | Возврат за указанное время; `0` — мгновенно |
| `resetCamera(player)` | Мгновенно отключить камеру |
| `playCameraRoute(player, routeName, [lock, hideGui, returnSmooth, totalSec, afterEnd, …])` | Пролёт по файлу `spraute_engine/routes/`; возвращает длительность |
| `await cameraRoute(player, routeName, …)` | То же, скрипт ждёт окончания |
| `cameraWaypointTime(routeName, pointIndex)` | Секунда от старта маршрута, когда камера на точке (1-based) |
| `cameraRouteStopAtPoint(player, routeName, pointIndex, [returnSmooth])` | Остановить маршрут на точке; `returnSmooth=0` — резко. Обычно `await time(...)` |
| `shakeCamera(player, intensity [, duration [, frequency]])` | Тряска |
| `punchCamera(player, yaw, pitch [, roll])` | Короткий удар в градусах |
| `stopCameraShake(player)` | Снять тряску и удар |

Для маршрута именованные хвосты: `"lockBlocks", true|false`, `"interp", "linear"|"smooth"|"smooth_camera"` (`"route"` = как в файле), `"transition", сек` (подлёт к первой точке, `0` = сразу), `"lookAt", x, y, z`, `"return"` / `"stay"`.

`totalSec = 0` — длительность из файла. `afterEnd`: `"return"` (вернуться к игроку) или `"stay"` (остаться на последней точке).

**Команды маршрута (в игре):**

| Команда | Описание |
|---------|----------|
| `/spraute route preview <имя>` | Предпросмотр маршрута для себя |
| `/spraute route list` | Список сохранённых маршрутов |

Маршруты создаются предметом **Камера** в креативе и сохраняются в `spraute_engine/routes/<имя>.json`. Сцена может вести камеру по **ROUTE-ноде**: `player.camera.followRoute(route, …)`.

**Параметры при старте камеры** (если не указаны иначе):

| Параметр | По умолчанию | Значения |
|----------|--------------|----------|
| `lockMovement` | `true` | Блокировать WASD/прыжок/присед |
| `hideGui` | `"minecraft"` | `"none"` / `"minecraft"` / `"all"` |
| `lockBlocks` | `true` | Блокировать ломание / установку / ПКМ по блокам |

#### `player.camera` (нода CAMERA)

Тот же объект, что CAMERA-ребёнок игрока в сцене. Свойства и методы совпадают.

| Свойство / метод | Описание |
|------------------|----------|
| `flySmooth` / `flyTime` | Дефолт плавности и секунд перелёта (см. выше) |
| `moveTo(x, y, z [, yaw, pitch, time, lock, hideGui, lockBlocks, afterEnd, …])` | Катсцена: подлёт. Без `time` — `flyTime`. `afterEnd`: `"stay"` / `"return"` / `"hold"`; для `return`/`hold` дальше секунды возврата (и удержания) |
| `lookAt(x, y, z [, track, time, …])` / `lookAt(target [, track, time, …])` | Взгляд; без `time` — `flyTime`; `0` — резко. `stopLook()` |
| `followRoute(route, duration, lookAlong, lock, hideGui, lockBlocks, afterEnd, …)` | Пролёт по ROUTE-ноде или имени файла. `duration=0` — авто. Хвост `"interp", "linear"|"smooth"|"smooth_camera"` |
| `returnToPlayer([time])` / `reset()` | Плавно вернуть / мгновенно сбросить |
| `followPlayer`, `x`/`y`/`z`, `yaw`/`pitch`, `fov`, `distance` | Rest-поза (мир или от глаз) |
| `lockX`/`lockY`/`lockZ`, `worldX`/`worldY`/`worldZ`, `holdAt(x,y,z)`, `lockAxis(...)` | Не следить по осям |
| `orbit`, `setOrbit(...)`, `collide`, `showHand`, `hideGui` | Орбита, коллизия, рука, HUD |
| `moveRelative`, `separateTurn`, `turnSmooth` | WASD от камеры; мышь крутит камеру, тело — куда идёт (`turnSmooth` 0…1 — не время перелёта) |
| `screenRaycast`, `lookAtInteract`, `disableEffects`, `walkBob` | Прицел по экрану, тело на взаимодействие, ванильный bobbing |
| `shake(...)` / `punch(...)` / `stopShake()` | Как глобальные `shakeCamera` / `punchCamera` |
| `post.bloom` / `post.vignette` / … | Пост-эффекты CAMERA |

Блок `camera id { ... }` — декларативный старт катсцены. Без `smooth`/`smoothTime` берётся `flySmooth`/`flyTime` игрока. Алиасы в блоке: `flySmooth`, `flyTime`. `time` здесь — **удержание** (hold), не длительность перелёта.

```text
camera intro {
  target = player
  pos = 100, 80, 200
  rotate = 45, -15
  lookAt = knight
  track = true
  time = 5
  smooth = true
  smoothTime = 1.0
  lockMovement = true
  lockBlocks = true
  hideGui = "minecraft"
}
```

Алиасы функций: `set_camera`, `set_camera_look_at`, `animate_camera`, `animate_camera_look_at`, `camera_look_at`, `stop_camera`, `reset_camera`, `play_camera_route`, `camera_waypoint_time`, `camera_route_stop_at_point`, `shake_camera`, `punch_camera`, `stop_camera_shake`.

```text
player.camera.flySmooth = true
player.camera.flyTime = 1.2

setCamera(player, 10, 80, 10, 90, 0, 3, 0.8)
setCamera(player, 10, 80, 10, 90, 0, 0, 0)        # мгновенно
cameraLookAt(player, knight, true)                 # flyTime ноды
cameraLookAt(player, knight, true, 0)              # резко
player.camera.moveTo(10, 80, 10, 90, 0)            # flyTime
player.camera.lookAt(knight, true, 0.4)
player.camera.followRoute(path, 0, true, true, "minecraft", true, "return", 0.8, "interp", "smooth")

setCameraLookAt(player, 10, 80, 10, knight, 5, 1.0, true, true, "minecraft", true)
animateCamera(player, 20, 85, 15, 120, -10, 1.5)
playCameraRoute(player, "intro")
playCameraRoute(player, "intro", true, "minecraft", 0.8, 0, "return", "interp", "smooth_camera", "transition", 0.5, "lockBlocks", false)
await cameraRoute(player, "intro", true, "minecraft", 0.8)
await time(cameraRouteStopAtPoint(player, "intro", 2, 0.8))
shakeCamera(player, 0.5, 0.4)
stopCamera(player)
resetCamera(player)
```

### `player.heldItem([hand])` и `player.heldItemNbt([hand])`
*   `player.heldItem("right")` или `player.heldItem("left")` (также `"main_hand"`, `"offhand"`) — возвращает ID предмета, который игрок держит в руке. Если не указать аргумент, проверяет правую (основную) руку.
*   `player.heldItemNbt("right")` — возвращает NBT-теги предмета в виде строки.

### `giveItem(player, item_id, [count], [name], [lore_array], [nbt_dict])`
Выдаёт игроку предмет(ы).
* `count` — количество (по умолчанию 1).
* `name` — кастомное название предмета (можно использовать `&` для цветов).
* `lore_array` — массив строк описания `["Строка 1", "Строка 2"]`.
* `nbt_dict` — словарь кастомных NBT-тегов `{"tag_name": "value", "spraute_no_drop": true}`.
*Заметка: Если указать NBT-тег `"spraute_no_drop": true`, то игрок не сможет выкинуть этот предмет из инвентаря (кнопкой Q или перетаскиванием).*

### `getHeldItem(player)`
Возвращает словарь (dict) с информацией о предмете в главной руке игрока. Если рука пуста, возвращает `null`.
* Ключи словаря: `"id"`, `"count"`, `"name"`, `"slot"`, `"lore"` (список), `"attack_damage"`, `"durability"` / `"max_durability"` (если есть), `"nbt"` (словарь).
* *Пример:* `val held = getHeldItem(player); if (held != null && held.id == "minecraft:stick") { ... }`

### Инвентарь игрока

Слоты: **0–8** хотбар, **9–35** основной инвентарь, **36–39** броня (ботинки→шлем), **40** вторая рука.  
Алиасы слота: `"main"` / `"right"` (выбранный хотбар), `"offhand"` / `"left"`.

| Функция | Описание |
|---------|----------|
| `getItemInSlot(player, slot)` | Словарь предмета в слоте или `null` |
| `getPlayerInventory(player)` | Список словарей всех непустых слотов |
| `isSlotEmpty(player, slot)` | Пуст ли слот |
| `hasItemInSlot(player, slot, item_id)` | Совпадает ли ID предмета в слоте |
| `findItemSlot(player, item_id)` | Первый слот с предметом или `-1` |
| `setItemInSlot(player, slot, item_id, [count], [name], [lore], [nbt])` | Положить/заменить предмет |
| `setItemCount(player, slot, count)` | Изменить количество (`0` — очистить) |
| `clearItemSlot(player, slot)` | Очистить слот |
| `removeItem(player, item_id, [count])` | Забрать предметы; возвращает сколько забрали |
| `getItemName` / `setItemName` | Название (отображаемое) |
| `getItemLore` / `setItemLore` | Описание (список строк, `&` → цвет) |
| `getItemAttackDamage` / `setItemAttackDamage` | Бонус урона предмета (модификатор атрибута) |
| `getItemNbt` / `setItemNbt` | Чтение / запись NBT-тегов (словарь) |

Уже есть: `getSlot`, `hasItem`, `countItem`, `giveItem`, `player.slot(n)`, `player.hasItem(id)`.

```text
val items = getPlayerInventory(player)
for item in items {
    chat(item.id + " x" + item.count + " slot " + item.slot)
}

val slot = findItemSlot(player, "minecraft:diamond_sword")
if (slot >= 0) {
    setItemName(player, slot, "&6Легендарный меч")
    setItemLore(player, slot, ["&7Очень острый", "&c+урон"])
    setItemAttackDamage(player, slot, 12)
}

removeItem(player, "minecraft:dirt", 64)
```

### `execute(command)` / `execute(command, executor)`
Выполнить команду сервера (без или с префикса `/`).  
Второй аргумент — сущность-исполнитель: объект сущности, ник игрока, id NPC из `NpcManager`, или ключевые слова: `"player"`, `"npc"`, `"mob"`, `"any"` (ближайшие в радиусе, логика как в `execute`).

### `taskDone(task_id)`
`true`, если для данного id асинхронной задачи контекст сообщает «завершено» (`ScriptContext.isTaskDone`). Используется вместе с `async` / `await task(...)`.

### `intStr(x)` / `wholeStr(x)`
Форматирование числа для текста: целые без хвоста `.0`, дробные — короткая запись; нечисловые строки возвращаются как есть.

### `random()` / `random(max)` / `random(min, max)`
Генерация случайного числа.
- Без аргументов: возвращает дробное число от 0.0 до 1.0.
- С одним аргументом: от 0 до `max` (исключительно для дробных, включительно для целых, если передано целое число).
- С двумя аргументами: в диапазоне от `min` до `max` (включительно для целых чисел, если оба аргумента целые).

### `lerp` / `lerpAngle` / `lerpColor` / `ease` / `lerpTo`
Интерполяция и tween. Можно указать **и время, и плавность**.

**Мгновенно (фактор `t` 0..1):**
| Функция | Описание |
|---------|----------|
| `lerp(a, b, t)` | Число |
| `lerp(a, b, t, easing)` | Число + кривая плавности на `t` |
| `lerpAngle(a, b, t [, easing])` | Угол по кратчайшей дуге |
| `lerpColor(from, to, t [, easing])` | `#RRGGBB` / `#AARRGGBB` |
| `ease(t, mode)` | Только кривая: `linear`, `easeIn`, `easeOut`, `easeInOut`, `bounceOut`, `elasticOut` |
| `curveEval(curve, t)` / `curve_eval` | Значение **поля-кривой** CUSTOM-ноды или ссылки на кривую при `t` от 0 до 1 (как в редакторе) |

**Во времени (tween свойства) — время + easing:**
| Функция | Описание |
|---------|----------|
| `lerp(target, prop, end, timeSec)` | Плавно за `timeSec` секунд |
| `lerp(target, prop, end, timeSec, easing)` | Время + плавность |
| `lerp(target, prop, end, easing, timeSec)` | Порядок easing/time можно менять |
| `lerpTo(...)` | То же, что timed-`lerp` |
| `lerpStop(target [, prop])` | Остановить |

Возвращает секунды → `await time(lerp(...))`.

```spr
# Фактор
x = lerp(0, 10, 0.5)
x = lerp(0, 10, 0.5, "easeOut")

# Время + плавность на свойстве
await time(lerp(guard, "x", 120, 1.5, "easeOut"))
await time(lerp(guard, "yaw", 90, "easeInOut", 0.8))
lerpTo(panel, "color", "#80FFFFFF", 0.4, "easeIn")

# Кривая из declare node type / поля ноды
y = curveEval(myCurveField, 0.35)
```

Цели: сущность / id NPC, нода сцены, игрок, `dict`.
Авто: `yaw`/`pitch`/`rotation*` → угол; `#...` → цвет.

### `fadeIn` / `fadeOut`
Экран загрузки / затемнение (клиентский оверлей).

| Функция | Описание |
|---------|----------|
| `fadeIn([player], [timeSec], [text], [subtitle])` | Затемнить экран. Первый аргумент — игрок или ник; далее опционально длительность (сек), заголовок, подзаголовок |
| `fadeOut([player])` | Убрать затемнение у игрока |

Алиас: `fade_out` → `fadeOut`.

### `list()` / `listCreate()`
Создаёт пустой изменяемый список (то же самое). Также можно писать литерал: `val a = []`.

### `dict()` / `dictCreate()` / `dict(key1, val1, key2, val2, ...)`
- `dict()` и `dictCreate()` — пустой словарь.
| `dict("name", npcName, "hp", 10)` — словарь из пар «ключ, значение» (чётное число аргументов; ключи приводятся к строке).

### Взаимодействие со строками и числами
| Метод / Функция | Описание |
|-----------------|----------|
| `strLen(string)` | Возвращает длину строки. |
| `strWidth(string)` | Возвращает ширину строки в пикселях шрифта Minecraft (учитывает форматирование `§`). Полезно для точного расчёта размеров UI-элементов. |
| `strNewlineCount(string)` | Возвращает количество символов новой строки `\n` в строке. |
| `replace(string, target, replacement)` | Заменяет все вхождения `target` на `replacement` в строке `string`. |

### Взаимодействие с Java API (Reflection)
Движок предоставляет возможность взаимодействовать с Java-кодом напрямую из скриптов, однако из соображений безопасности доступ ограничен "белым списком" (allowlist) классов.

| Функция | Описание |
|---------|----------|
| `java_class(class_name)` | Возвращает объект Java-класса по его полному имени (например, `"java.util.Random"`). Если класс не в белом списке, вернет `null` и выведет предупреждение в консоль сервера. Можно вызывать статические методы напрямую: `java_class("java.lang.Math").max(1, 2)`. |
| `java_new(class, arg1, ...)` | Создает новый экземпляр переданного Java-класса (полученного через `java_class`), передавая в конструктор указанные аргументы. Типы аргументов приводятся автоматически. |
| `sendPacket(player, java_packet)` | Отправляет указанный Java-пакет игроку `player`. Игрок может быть строковым никнеймом или объектом. Пакет должен быть экземпляром `net.minecraft.network.protocol.Packet`. |

*Пример использования:*
```javascript
val RandomClass = java_class("java.util.Random")
val randomObj = java_new(RandomClass)
val randomInt = randomObj.nextInt(100) # Вызов Java-метода напрямую
chat("Случайное число из Java: " + wholeStr(randomInt))

val Math = java_class("java.lang.Math")
chat("Максимум из 1 и 5: " + wholeStr(Math.max(1, 5)))

val Lifecycle = java_class("net.minecraftforge.server.ServerLifecycleHooks")
val server = Lifecycle.getCurrentServer()

val SetCameraPacket = java_class("net.minecraft.network.protocol.game.ClientboundSetCameraPacket")
val packet = java_new(SetCameraPacket, camera_npc.java) # Использование .java для развертывания Entity
sendPacket(player, packet)
```

### Снапшоты структур и мира
| Функция | Описание |
|---------|----------|
| `save_snapshot(name, [radius])` | Сохраняет структуры и блоки в мире вокруг игрока (или источника команды) в радиусе `radius` чанков в файл снапшота `name` (`.spraute` файл в папке `world/spraute_data/snapshots`). По умолчанию радиус = 0 (только текущий чанк). |
| `load_snapshot(name)` | Загружает блоки и сущности из сохраненного снапшота `name` обратно в мир по тем же координатам, по которым они были сохранены. |

### Прочие встроенные функции
| Функция | Описание |
|---------|----------|
| `setColor(color)` | Меняет цвет имени NPC для последующих вызовов `say()` в этом скрипте. Форматы: `#FF5500`, `"gold"`, `"reset"`. |
| `getVar(name)` | Возвращает значение переменной (устарело, лучше использовать напрямую имя переменной или индексы `[]`). |
| `spawnOrb(texture, amount, x, y, z, [dimension])` | Спавнит кастомную частицу (орб, аналогичную опыту), которая притягивается к игроку и которую можно подобрать. Возвращает `UUID` заспавненной сущности. Орб существует 5 минут. Значение `amount` передастся в событие `on orbPickup`. Опционально можно указать измерение (например, `"minecraft:overworld"`). |
| `removeOrbs(texture)` | Удаляет все орбы (вызванные через `spawnOrb`) с указанной текстурой в радиусе 256 блоков от источника выполнения. Возвращает количество удаленных орбов. |
| `cancelEvent()` | Отменяет текущее событие: `on placeBlock`, `on openChest`, `on openDoor`, `on breakBlock`, `on clickBlock`, `on jump`, `on attack`, `on sprint` (и `on action` тех же типов). |
| `addMobDrop(mob_id, item_id, [min=1], [max=1], [chance=100], [replace=false], [nbt_string])` | Добавляет кастомный предмет в лут ванильного или модового моба (например: `"minecraft:zombie"`, `"minecraft:diamond"`, `1`, `1`, `50`). Аргумент `replace` отменяет выпадение оригинального дропа, оставляя только кастомный. NBT строка указывается опционально (как в `/give`). |
| `addBlockDrop(block_id, item_id, [min=1], [max=1], [chance=100], [replace=false], [nbt_string])` | Настраивает кастомный дроп при ломании блока игроком. Если `replace` = `true`, блок будет сломан без оригинального дропа (только с кастомным предметом). NBT строка указывается опционально (как в `/give`). |
| `startScript(name, [args_dict])` | Запускает другой скрипт параллельно. Опционально можно передать словарь аргументов, которые станут локальными переменными в запущенном скрипте (например: `startScript("scriptY", { "target": npc_id })`). |
| `stopScript(name)` | Останавливает выполнение указанного скрипта, если он запущен. |
| `autorun()` / `autorun("name")` | Автозапуск скрипта при старте сервера. Без аргумента — текущий скрипт. В начале файла можно писать директиву `autorun` (без скобок). |
| `runOnJoin("script")` | Запустить скрипт при входе любого игрока (замена `on_join` в `triggers.json`). |
| `runOnFirstJoin("script")` | Запустить скрипт при первом входе игрока в мир. |
| `runAfter("finished", "next")` | После завершения скрипта `finished` автоматически запустить `next`. |

### `playSound(player, sound_id, [volume], [pitch], [loop])`
Проигрывает звук для указанного игрока.
- `sound_id` — стандартный звук Minecraft (например, `"minecraft:entity.zombie.ambient"`) или кастомный звук из папки. Чтобы воспроизвести свой звук, положите `.ogg` файл в `minecraft/spraute_engine/sounds/` (например, `mysound.ogg`) и вызовите `playSound(player, "spraute_engine:mysound")`.
- `volume` — громкость (по умолчанию `1.0`).
- `pitch` — высота звука (по умолчанию `1.0`).
- `loop` — `true` — зациклить (остановить через `stopSound(player, sound_id)`). По умолчанию `false`.

### `stopSound(player, [sound_id])`
Останавливает воспроизведение звука у игрока.
- Если `sound_id` указан, останавливает конкретный звук.
- Если `sound_id` не указан (`stopSound(player)`), останавливает вообще все звуки у этого игрока.

---

## Встроенная библиотека: `spraute_chat.spr`
Библиотека для кастомного чата NPC: горизонтальная плашка над хотбаром с головой NPC и плавной анимацией прозрачности, плюс история сообщений по кнопке B.

Чтобы использовать, запустите скрипт и импортируйте его функции:
```javascript
startScript("spraute_chat")
import("spraute_chat")
```

`startScript` запускает скрипт чата в фоновом режиме (регистрирует обработчик `on keybind("B")` для истории сообщений), а `import` импортирует из него функцию `npc_chat`.

Затем вы можете вызывать функцию `npc_chat`:
```javascript
npc_chat(player, my_guard, "Проход закрыт, уходи отсюда!", "#FF5555")
```

Плашка чата появляется **над хотбаром, полоской HP и голода**:
- **Слева** — голова NPC (отрисовывается через `entity` виджет с `crop`, по центру высоты сообщения).
- **Справа** — текст в формате `[Имя цветное]: Текст сообщения`.
- Плашка **плавно появляется** (fade-in за 0.3 сек) и **плавно исчезает** (fade-out за 0.5 сек) через 5 секунд.

Если игрок нажмёт кнопку `B` на клавиатуре, откроется история из последних 50 сообщений кастомного чата с прокруткой (каждое сообщение с иконкой головы NPC)!

---

### `uiOpen(player, шаблон)`
- **`uiOpen(player, переменная)`** — открыть экран из шаблона, созданного блоком **`create ui { ... }`** (см. раздел ниже).
- **`uiOpen(player, json)`** — сырая JSON-строка (legacy).

Сервер проверяет размер, подставляет UUID для виджетов `entity`.  
`player` — объект игрока или ник.

### `uiClose(player)`
Закрыть скриптовый UI у игрока (без лишнего события «закрыто» на сервере при программном закрытии).

### `overlayOpen(player, шаблон_или_json)`
Открывает UI шаблон в качестве **HUD наложения** поверх экрана (без забора мышки и без паузы игры). Поддерживает те же виджеты, что и `uiOpen`.

### `overlayClose(player)`
Закрывает текущее HUD наложение у игрока.

### `uiIsOpen(player)`
`true`, если у игрока открыт скриптовый UI или контейнерное меню со слотами.

### `uiGetInput(player, widget_id)` / `uiSetInput(player, widget_id, text)`
Текст поля ввода (`input` / `UI_INPUT`) на клиенте у открытого UI.

### `uiScrollGet(player, widget_id)` / `uiScrollSet(player, widget_id, offset)`
Текущий и заданный **вертикальный** offset прокрутки контейнера `scroll` / `UI_SCROLL`.

### Видео-виджет (`type: video`, `UI_VIDEO`)
| Функция | Описание |
|---------|----------|
| `uiVideoPlay(player, widget_id)` | Воспроизведение (`uiVideoResume` / `ui_video_resume` — то же) |
| `uiVideoPause(player, widget_id)` | Пауза |
| `uiVideoStop(player, widget_id)` | Стоп, время в 0 |
| `uiVideoRestart(player, widget_id)` | С начала |
| `uiVideoEnable(player, widget_id, bool)` | Включить/выключить декодер у игрока |
| `uiVideoTime(player, widget_id)` | Текущее время (сек) |
| `uiVideoDuration(player, widget_id)` | Длительность (сек) |
| `uiVideoPlaying(player, widget_id)` | Играет ли сейчас |
| `uiVideoEnded(player, widget_id)` | Дошло ли до конца |

Алиасы с подчёркиванием: `ui_video_play`, `ui_video_pause`, `ui_video_stop`, …

### `uiUpdate(player, widget_id, field, value)`
Пока у игрока открыт скриптовый UI **или HUD наложение (overlay)**, отправляет на **клиент** обновление поля виджета с данным **`id`** в JSON (строки, числа и цвета передаются как текст). Таким образом можно обновлять любой элемент без переоткрытия экрана.

### `uiAnimate(player, widget_id, field, target_value, duration_seconds, [easing])`
Плавно анимирует свойство виджета с данным **`id`** до значения `target_value` за `duration_seconds` секунд. Работает для числовых свойств: `x`, `y`, `w`, `h`, `scale`, `alpha` и других.

Дополнительный параметр `easing` задаёт тип интерполяции (по умолчанию `"linear"`):
- `"linear"` — равномерное движение без ускорения.
- `"ease_in"` — плавное ускорение в начале.
- `"ease_out"` — плавное замедление в конце.
- `"ease_in_out"` — плавное ускорение в начале и замедление в конце.
- `"bounce_out"` — эффект отскока (мячика) в конце анимации.
- `"elastic_out"` — эффект пружины в конце анимации.

Например: `uiAnimate(player, "my_btn", "y", 150, 1.5, "bounce_out")` (кнопка переместится на Y=150 за 1.5 секунды с отскоком).

| Виджет | `field` | `value` / `target_value` |
|--------|---------|--------|
| `text` | `x`, `y`, `text`, `color`, `scale` | число, число, текст, `#RRGGBB` / `#AARRGGBB`, число |
| `input` | `x`, `y`, `w`, `h`, `text` | числа, текст |
| `button` | `x`, `y`, `w`, `h`, `label`, `color`, `hover`, `texture`, `tooltip` | числа, подпись, цвета, путь текстуры, текст (поддерживает `\n`) |
| `rect` / `panel` | `x`, `y`, `w`, `h`, `color`, `tooltip` | числа, цвет, текст |
| `clip` | `x`, `y`, `w`, `h` | числа |
| `image` | `x`, `y`, `w`, `h`, `texture`, `tooltip` | числа, путь текстуры, текст |
| `svg` | `x`, `y`, `w`, `h`, `source`, `color`, `stroke`, `strokeWidth`, `recolor`, `tooltip` | вектор из workspace; см. канвас `UI_SVG` |
| `video` | `x`, `y`, `w`, `h`, `source`, `playing`, `loop`, `playerEnabled`, `audio`, `volume`, `tooltip` | MP4/H.264; см. `uiVideo*` |
| `item` | `x`, `y`, `size`, `item`, `tooltip` | числа, id предмета, текст |
| `entity` | `x`, `y`, `w`, `h`, `scale`, `feet_crop`, `crop`, `anchor_x`, `anchor_y`, `viewport` | числа; `crop` / `viewport` — четыре числа (строка или `[...]`); отрицательный `anchor_y` в `uiUpdate` — снова `feet_crop` |

Работает только если виджет в разметке был с **непустым** `id` в JSON.

#### Формат JSON UI
Корневой объект:

- `w`, `h` — размер панели (пиксели GUI), по умолчанию 200×150.
- `bg` — цвет фона панели: `#RRGGBB` или `#AARRGGBB`.
- `widgets` — массив виджетов (рисуются по полям **`layer`** и **`order`**; при отсутствии — порядок в массиве).

Типы виджетов (`type`):

| `type` | Поля |
|--------|------|
| `rect` / `panel` | `x`, `y`, `w`, `h`, `color`; опционально `id`, `layer`, `order`, `tooltip` |
| `clip` | `x`, `y`, `w`, `h`, `children` (массив виджетов внутри области обрезки); опционально `id`, `layer`, `order`, `tooltip`. Все виджеты в `children` будут обрезаться по границам `clip`. |
| `image` | `x`, `y`, `w`, `h`, `texture` (`namespace:path` или путь без неймспейса, который по умолчанию будет искаться в `minecraft:`, например `textures/gui/btn.png` -> `minecraft:textures/gui/btn.png`); опционально `id`, `layer`, `order`, `tooltip`, `slice_borders` (размер рамки 9-slice, 0 для отключения), `slice_scale` (масштаб рамки 9-slice, по умолчанию 1) |
| `text` | `x`, `y`, `text`, `color`, опционально `scale`, `wrap` (макс ширина в пикселях), `max_lines` (макс кол-во строк при переносе), `max_chars` (обрезка символов с ...), `anchor` (строка: `center`, `right`, `bottom_left` и т.д. или массив `[x, y]`), `id`, `layer`, `order`, `tooltip` |
| `input` | `id` (обязательно), `x`, `y`, `w`, `h`, опционально `text`, `placeholder`, `color` (текст), `bg_color` (фон), `outline_color` (рамка), `scale`, `max_chars`, `input_type` (`text` или `password`), `tooltip`, `layer`, `order` |
| `button` | `id` (для клика), `x`, `y`, `w`, `h`, опционально `label`; либо цвета `color` / `hover`, либо `texture` (кнопка-картинка); опционально `layer`, `order`, `tooltip` |
| `entity` | `x`, `y`, `w`, `h`, `entity` (строка: UUID, `npc:script_id`, `player`, …), `scale`, **`crop = [l,t,r,b]`** — доли **0–1**: сколько **срезать слева, сверху, справа, снизу** от ячейки (снизу половину: `[0,0,0,0.5]`); **`anchor = [x,y]`** или `anchor_x` / `anchor_y` — точка якоря в **полной** ячейке; без якоря — **`feet_crop`**; `animation = false` для отключения анимации в UI; `nameTag = true/false` — показывать имя NPC (по умолчанию **скрыто**); **`noLookAt = true/false`** — сбросить поворот NPC из мира (рендерить с нейтральной позой, игнорируя yaw/pitch сущности); **`noFollowCursor = true/false`** — не поворачивать голову вслед за мышью; **`noHurtAnim = true/false`** — отключить покраснение при получении урона; `renderBones` — строка (одна кость, например `"Head"`) или массив строк (список костей, например `["Head", "LeftArm"]`), чтобы рендерить только эти кости и их дочерние (если не указано, рендерятся все кости); `id`, `layer`, `order`, `tooltip` |
| `item` (или `block`) | `x`, `y`, `w` (размер в пикселях, по умолчанию 16), `item` (строка, например `"minecraft:apple"` или `"stone"`); опционально `id`, `layer`, `order`, `tooltip` |
| `svg` | `x`, `y`, `w`, `h`, `source` (путь к `.svg` в workspace); опционально `color`, `stroke`, `strokeWidth`, `recolor`, `id`, `layer`, `order`, `tooltip` |
| `video` | `x`, `y`, `w`, `h`, `source` (MP4); опционально `playing`/`autoplay`, `loop`, `playerEnabled`/`enabled`, `audio`, `volume`, `id`, `layer`, `order`, `tooltip` |

После клика по кнопке или ESC см. `await uiClick`. Переменная `_uiClosed`: `true`, если закрыли ESC (или пустой id при закрытии).

---

## Глобальная функция `range` (не в реестре)

Используется **только** в `for`:

- `range(n)` — `i` принимает значения `0, 1, …, n - 1` (цикл при `i < n`).
- `range(start, end)` — `i` от `start` до `end - 1` включительно (условие `i < end`).

---

## Блок `create npc id { ... }`

Создаёт или **обновляет** NPC с строковым **script id** (ключ в `NpcManager`).

Свойства внутри `{ }` (через `=`; значения можно перечислять через `,`):

| Свойство | Описание |
|----------|----------|
| `name` | Отображаемое имя |
| `hp` | Здоровье и макс. HP |
| `speed` | Скорость движения |
| `walkTurnSpeed` | Скорость поворота тела при ходьбе (°/тик, по умолчанию 6) |
| `idleTurnSpeed` | Скорость поворота тела в стойке / при взгляде (°/тик, по умолчанию 6) |
| `dimension` | Измерение для спавна (например, `"minecraft:overworld"`). Если не указано, спавн в текущем мире |
| `pos` | `x, y, z` (числа) |
| `rotate` | `yaw, pitch` |
| `show_name` | Показывать имя (по умолчанию true, если не указано) |
| `collision` | Наличие коллизии (по умолчанию true). При false другие существа могут проходить сквозь NPC |
| `hitbox` | `[ширина, высота]` или `[ширина, высота, ox, oy, oz]` — основной хитбокс |
| `hitboxPreset` / `hitbox_preset` | `"player"`, `"small"` или `"large"` — применяется при спавне |
| `width` / `height` (или `w` / `h`) | Альтернатива `hitbox` без offset |
| `model`, `texture`, `animation`, `idleAnim`, `walkAnim` | Ресурсы Spraute NPC (ключевое слово `model` допустимо как имя свойства) |
| `drop_item` | Строка, ID предмета, который выпадает при смерти NPC (например, `"minecraft:iron_sword"`). Добавление нескольких предметов см. метод `addDrop` |
| `drop_min`, `drop_max` | Числа, минимальное и максимальное количество предметов в дропе |
| `drop_chance` | Число (от 0 до 100), шанс выпадения дропа |
| `scale` / `scaleX`, `scaleY`, `scaleZ` | Масштаб модели |
| `rotationX`, `rotationY`, `rotationZ` | Доп. поворот модели (градусы) |
| `pathArriveRadius` / `pathArrive` | Точность прибытия по земле (блоки) |
| `pathArriveVert` | Допуск по высоте при ходьбе |
| `pathStepHeight`, `pathMaxJump`, `pathMaxDrop`, `pathMaxRange`, `pathMaxNodes` | Параметры наземного pathfinding |
| `pathDiagonal`, `pathAvoidWater`, `pathAvoidLava`, `pathSnapOnArrive` | Флаги маршрута по земле |
| `flyArriveRadius` / `flyArrive`, `flyMaxRange`, `flyMaxNodes`, `flyDiagonal`, `flyAvoidWater`, `flyAvoidLava`, `flySnapOnArrive` | Аналоги для воздушного pathfinding |
| `flyIdleAnim`, `flyWalkAnim`, `swimIdleAnim`, `swimWalkAnim` | Анимации полёта/плавания |
| `translucent` / `transparent`, `opacity`, `lod` / `lodEnabled`, `renderDistance`, `forceVisible` | Прозрачность и отрисовка |

Костные хитбоксы задаются **после** спавна: `npc.addBoneHitbox(...)`. В рантайме: `npc.flySmoothing = 0.35` — сглаживание ускорения в полёте (0.02…1).

Переменная с **id** блока получает ссылку на сущность.

---

## Префабы NPC: `create npc_prefab` + `spawnNpcPrefab`

**Префаб** — шаблон НИПа без спавна. Один раз описываете внешность и статы, потом спавните копии с разными id и позициями.

### `create npc_prefab id { ... }`

Регистрирует шаблон (те же свойства, что у `create npc`, **кроме `pos`** — позиция задаётся при спавне).

```javascript
create npc_prefab goblin {
    name = "Гоблин"
    hp = 30
    model = "geo/defolt.geo.json"
    texture = "textures/entity/goblin.png"
    animation = "animations/npc_classic.animation.json"
    speed = 0.35
    dropItem = "minecraft:emerald"
    dropChance = 50
}
```

### `spawnNpcPrefab(prefabId, instanceId, x, y, z [, overrides])`

Спавнит экземпляр по шаблону. `instanceId` — уникальный id в `NpcManager` (как у `create npc`). Опциональный `overrides` — словарь полей для подстройки:

```javascript
spawnNpcPrefab("goblin", "mob_01", 10, 64, 20)
spawnNpcPrefab("goblin", "mob_02", 12, 64, 22, dict("hp", 50, "name", "Берсерк"))
```

Алиас: `spawn_npc_prefab(...)`.

---

## Создание кастомных ресурсов

Вы можете создавать собственные блоки, предметы и партиклы напрямую через `.spr` скрипты (обычно они описываются в начале или в отдельном файле в папке `scripts/`). Движок автоматически распарсит эти блоки при загрузке сервера и создаст для них нужные ресурсы и реестры.

### Блок `create particle id { ... }`

Создаёт кастомный тип частицы (`spraute_engine:id`) и ноду **RES_PARTICLE** в иерархии активной сцены (как `create block` / ресурсы в редакторе). Ту же ноду можно добавить вручную: Иерархия → Ресурсы → Частица (тип).

Свойства (через `=`):
- `texture` (строка) — путь к текстуре, например `"textures/particle/my_particle.png"`. Внешние текстуры из `spraute_engine/textures/` подхватываются автоматически.
- `parent` / `scene` / `id` — как у других `create …` (сцена для иерархии).

Пример:
```javascript
create particle my_magic_spark {
    texture = "textures/particle/spark.png"
}
```
*Спавн: `particleSpawn("spraute_engine:my_magic_spark", x, y, z, …)` или эмиттер с `type = "spraute_engine:my_magic_spark"`. Новый id типа нужен перезапуск MC (Forge registry).*

### Блок `create block id { ... }`

Создает новый кастомный блок. В игре он будет доступен как `spraute_engine:id`.

Свойства (через `=`):
- `model` (строка) — путь к кастомной модели (если не указано, сгенерируется куб).
- `texture` (строка) — путь к текстуре со всех сторон.
- `texture_up`, `texture_down`, `texture_north`, `texture_south`, `texture_west`, `texture_east` (строки) — если нужно задать разные текстуры для разных сторон (переопределяют `texture`).
- `solid` / `collision` (true/false) — твёрдый ли блок (коллизия). По умолчанию `true`. При `false` через блок можно ходить. `solid` и `collision` — одно и то же; если указаны оба, приоритет у `solid`.
- `hitbox` — форма коллизии и обводки:
  - **6 чисел** — точные границы в пикселях блока (0–16): `[minX, minY, minZ, maxX, maxY, maxZ]`
  - **3 числа** — `[ширина, глубина, высота]` в долях блока (`1` = весь блок), по центру X/Z, от пола
  - **2 числа** — `[ширина, высота]` — квадрат в плане, по центру (удобно для плит, столбов)
  - Примеры: `hitbox = [1, 0.5]` — плита на полблока; `hitbox = [0, 0, 0, 16, 8, 16]` — то же в полном формате
- `directional` (true/false) — поворачивать блок при установке по взгляду игрока (по умолчанию `true`). Кастомный `hitbox` тоже крутится вместе с блоком.
- `light` (число) — уровень свечения от 0 до 15.
- `hardness` (число) — прочность блока при ломании (по умолчанию `1.5`). Отрицательное значение = неломаемый. Можно менять в рантайме: `setBlockHardness`.
- `name` (строка) — отображаемое имя блока в инвентаре (поддерживает `§` / `&` для цвета).
- `drop` (строка) — id предмета при ломании (`spraute_engine:battery`, `minecraft:raw_iron`, `none` — ничего). По умолчанию выпадает сам блок.
- `drop_count` (число) — количество для `drop` (по умолчанию `1`).
- `drop_replace` (true/false) — если `true`, сам блок не выпадает, только кастомный дроп. Автоматически `true` при указании `drop` или `drops`.
- `drops` (массив) — несколько дропов с шансом: `[["minecraft:coal", мин, макс, шанс_%], ...]`.
- **Генерация руд в мире** (нужен **новый мир** или неисследованные чанки):
  - `is_ore` (true/false) — генерировать жилу в мире.
  - `ore_vein` (число) — размер жилы (по умолчанию `8`).
  - `ore_min` (число) — минимальная высота (по умолчанию `-64`).
  - `ore_max` (число) — максимальная высота (по умолчанию `64`).
  - `ore_chances` (число) — попыток генерации на чанк (по умолчанию `10`).
  - `ore_dimension` (строка) — `"minecraft:overworld"`, `"minecraft:the_nether"`, `"minecraft:the_end"`.
- `tab` (строка) — id вкладки креатива из `create tab`.

**Пример руды с кастомным дропом:**
```
create block raya_crystal_ore {
    texture      = "textures/block/crystal_ore.png"
    hardness     = 4.0
    is_ore       = true
    ore_vein     = 6
    ore_min      = -32
    ore_max      = 48
    ore_chances  = 4
    drop         = "battery"
    drop_count   = 1
}
```

**Дроп для блока или моба — `create drop`:**
```
create drop stone_coal {
    block   = "minecraft:stone"
    item    = "minecraft:coal"
    min     = 1
    max     = 2
    chance  = 5
    replace = false   # true = отключить базовый (ванильный) дроп
    xp      = 0
}

create drop zombie_bones {
    mob     = "minecraft:zombie"
    item    = "minecraft:bone"
    min     = 1
    max     = 3
    chance  = 100
    replace = true    # только кастомный дроп, без ванильного
}
```

В редакторе сцен то же самое — нода **Drop** (`RES_DROP`): цель «Блок» / «Моб», чекбокс «Отключить базовый дроп».

### Блок `create world id { ... }`

Пустое измерение `spraute_engine:id` с кастомным небом/туманом. В редакторе — нода **Мир** (`RES_WORLD`).

```
create world lobby {
    sky        = "#1b2838"   # цвет неба (реально рисуется, не только туман)
    fog        = "#0f1720"   # опционально; иначе автотемнее от sky
    ambient    = 0.4         # ambient_light измерения 0–1
    fixed_time = 6000        # тик; -1 = сутки
    skylight   = true
    clouds     = false       # отключить облака
}
```

Телепорт: `teleportToWorld(player, "lobby", x, y, z)`. Нужен **перезапуск Minecraft** после правок.

### Блок `create item id { ... }`

Создает новый кастомный предмет. В игре он будет доступен как `spraute_engine:id`.

Свойства (через `=`):
- `model` (строка) — путь к кастомной 3D-модели (опционально).
- `texture` (строка) — путь к текстуре предмета (если модели нет, сгенерируется плоский предмет как ванильные).
- `name` (строка) — отображаемое имя (поддерживает `§` / `&` для цвета).
- `tab` (строка) — id вкладки креатива из `create tab`.
- `maxStackSize` (число) — максимальный размер стака (от 1 до 64, по умолчанию `64`).
- `tool_type` (строка) — тип инструмента: `sword`, `pickaxe`, `axe`, `shovel`, `hoe` (или `меч`, `кирка`, …). Вместо этого можно задать флаги `sword = true`, `pickaxe = true` и т.д.
- `damage` (число) — бонус урона в ближнем бою (как в подсказке ванильного предмета: меч железа = `5`). Для обычного предмета без `tool_type` добавляет урон через атрибуты.
- `attack_speed` (число) — модификатор скорости атаки (меч железа: `-2.4`).
- `durability` (число) — прочность инструмента / брони (по умолчанию `250` для инструментов).
- `mining_speed` (число) — скорость копания для кирки/топора/лопаты/мотыги (по умолчанию `6`, как железо).
- `mining_level` (число) — уровень добычи: `0` = дерево, `1` = камень, `2` = железо, `3` = алмаз (по умолчанию `2`).
- **Броня:**
  - `armor_type` — `helmet` / `chestplate` / `leggings` / `boots` (или `шлем`, `нагрудник`, `поножи`, `ботинки`).
  - `armor_texture` — база текстуры на теле: `"textures/models/armor/crystal"` → файлы `crystal_layer_1.png` (шлем/нагрудник/ботинки) и `crystal_layer_2.png` (поножи) в `spraute_engine/textures/models/armor/`.
  - `defense` / `armor_defense` — очки защиты (шлем ≈ 2, нагрудник ≈ 6).
  - `toughness` / `armor_toughness` — твёрдость брони (алмаз = `2`).
  - `knockback_resistance` — сопротивление отбрасыванию (незерит ≈ `0.1`).
  - `texture` — иконка в инвентаре (как у обычного предмета).

Пример меча:

```text
create item iron_dagger {
    texture = "textures/item/iron_dagger.png"
    name = "§7Железный кинжал"
    tool_type = "sword"
    damage = 5
    attack_speed = -2.4
    durability = 200
    tab = "weapons"
}
```

Пример кирки:

```text
create item copper_pick {
    texture = "textures/item/copper_pick.png"
    name = "Медная кирка"
    pickaxe = true
    damage = 3
    mining_speed = 5
    mining_level = 1
    durability = 180
}
```

Пример брони (один набор — одна `armor_texture` на все части):

```text
create item crystal_helmet {
    texture = "textures/item/crystal_helmet.png"
    name = "§bКристальный шлем"
    armor_type = "helmet"
    armor_texture = "textures/models/armor/crystal"
    defense = 2
    durability = 220
}

create item crystal_chestplate {
    texture = "textures/item/crystal_chestplate.png"
    armor_type = "chestplate"
    armor_texture = "textures/models/armor/crystal"
    defense = 6
    durability = 320
}
```

---

## Блок `create ui name { ... }`

Объявляет **динамический шаблон UI** и кладёт его в переменную `name`. Внутри блока поддерживаются **циклы**, **условия**, **переменные** и **вызовы функций** — виджеты строятся на лету при выполнении `create ui`.

**Корень** (свойства через `=`):

| Свойство | Описание |
|----------|----------|
| `id` | Опционально — строковый id панели в JSON |
| `size` | `[ширина, высота]` пикселей (литерал списка или выражение). Поддерживает проценты: `["100%", "100%"]` (от размера экрана). |
| `pos` | `[x, y]` — позиция панели. Каждый элемент поддерживает: число (пиксели), `"center"` (по центру), `"50%"` (проценты от размера экрана). **Отрицательные значения** для overlay: `pos = ["center", -40]` = центр по X, 40 пикселей от нижнего края экрана. |
| `background` / `bg` | Цвет фона `#AARRGGBB` и т.д. |
| `can_close` | `true` / `false` (по умолчанию `true`). Запрещает закрытие UI по кнопке ESC. |
| `radius` | Радиус скругления углов панели в пикселях (0 — прямые углы). |
| `gradient` | Второй цвет градиента фона (`#AARRGGBB`); заливка идёт от `background` к `gradient`. |
| `gradientDir` | Направление градиента: `"v"` (сверху вниз, по умолчанию) или `"h"` (слева направо). |
| `border` | Толщина внутренней рамки панели в пикселях (рисуется внутрь от края). |
| `borderColor` | Цвет внутренней рамки (`#AARRGGBB`). |
| `borderGradient` | Градиент внутренней рамки: цвета через запятую (`"#FF8800FF,#FF0044AA"`). Если указан, `borderColor` не используется. |
| `borderGradientDir` | Направление градиента рамки: `"v"`, `"h"` или угол в градусах. |
| `outerBorder` | Толщина наружной обводки в пикселях (рисуется снаружи от края). |
| `outerBorderColor` | Цвет наружной обводки (`#AARRGGBB`). |
| `outerBorderGradient` | Градиент наружной обводки: цвета через запятую. Если указан, `outerBorderColor` не используется. |
| `outerBorderGradientDir` | Направление градиента обводки: `"v"`, `"h"` или угол в градусах. |
| `shadow` | Размер мягкой тени под панелью в пикселях. |
| `layer` | Слой оверлея (целое, по умолчанию `0`). Оверлеи рисуются по возрастанию слоя; **слой < 0 рендерится под интерфейсом Minecraft** (хотбар, здоровье, чат). |

**Виджеты** — вызовы `text(...)`, `button(...)`, `entity(...)`, `image(...)`, `svg(...)`, `video(...)`, `rect(...)`, `scroll(...)`, `divider(...)`, `block(...)` с блоком `{ ... }`:

- Внутри виджета: `pos`, `size`, `scale`, **`crop = [l,t,r,b]`**, **`anchor = [x,y]`**, `feet_crop`, `color`, `wrap`, `align`, `tooltip`, …
- **Стиль (rect/panel, button, input, scroll):** `radius` (скругление углов, px), `gradient` (один цвет — градиент от `color` к нему; 2+ цвета через запятую — только цвета градиента), `gradientDir` (`"v"`/`"h"`/угол), `border` (внутренняя рамка, px), `borderColor`, `borderGradient`, `borderGradientDir`, `outerBorder` (наружная обводка, px), `outerBorderColor`, `outerBorderGradient`, `outerBorderGradientDir`, `shadow` (мягкая тень, px). Пример:

```text
rect("card") {
    pos = [10, 10]
    size = [160, 60]
    color = "#E01A1A22"
    gradient = "#E0333344"
    radius = 8
    border = 1
    borderColor = "#66FFFFFF"
    outerBorder = 2
    outerBorderColor = "#AA000000"
    shadow = 6
}
```

- В **`pos` / `size`** можно смешивать числа и строки с **`%`**: например `pos = ["20%", "10%"]` — проценты от **ширины/высоты панели**.
- **`on_click { ... }`** — тело выполняется **на сервере** при клике по кнопке.
- **`scroll("id") { ... }`** — контейнер с прокруткой. Свойства: `content_h`, `scrollbar`. Внутри — дочерние виджеты (а также `while`, `if`, `for`).

**Динамический код внутри `create ui`:**

```text
create ui my_ui {
    size = [400, 200]
    background = "#FF000000"

    var i = 0
    while (i < listSize(items)) {
        val item = list_get(items, i)
        button("btn_" + i, dict_get(item, "name")) {
            pos = [10, i * 24]
            size = [180, 20]
            color = "#55336688"
            hover = "#66447799"
        }
        i = i + 1
    }

    if (show_details) {
        text("details", detail_text) {
            pos = [200, 10]
            color = "#FFFFFF"
            wrap = 180
        }
    }
}
```

Открытие: `uiOpen(player, имя_переменной_шаблона)`.

**Виджеты:** `text`, `input`, `button`, `entity`, `image`, `svg`, `video`, `item` (или `block`), `rect` / `panel`, `grid_bg` (сетка), `scroll`, `clip` (обрезка). Серверные `on_click`, проценты в `pos`/`size`, **`uiUpdate`** для смены свойств на клиенте.

**SVG:** `svg("id", "textures/gui/icon.svg") { pos = [0,0]; size = [32,32]; color = "#FFFFFFFF" }` — опционально `stroke`, `strokeWidth`, `recolor`.

**Видео:** `video("cutscene", "videos/intro.mp4") { size = [320,180]; loop = false; autoplay = true; audio = true; volume = 0.8 }`.

### `grid_bg` - Красивая фоновая сетка
Рисует линии сетки (горизонтальные, вертикальные или обе).
Свойства:
- `grid_type`: `"h"` (горизонтальная), `"v"` (вертикальная) или `"hv"` (обе). По умолчанию `"hv"`.
- `cell_size`: размер ячейки в пикселях (по умолчанию `20`).
- `thickness`: толщина линий (по умолчанию `1`).
- `color`: цвет линий (например, `"#44FFFFFF"`).

**Портрет NPC:** **`pos` + `size`** — прямоугольник ячейки; **`scale`** — масштаб модели в этой ячейке; **`crop = [l,t,r,b]`** — на сколько **урезать кадр с каждого края** (доли от ширины/высоты ячейки), без сжатия модели; **`anchor = [x,y]`** — где ставить якорь отрисовки в **той же** полной ячейке (если не задан — **`feet_crop`**). **`nameTag = false`** (по умолчанию) — скрыть имя NPC в рендере; **`noLookAt = true`** — сбросить мировой поворот NPC (рендерить в нейтральной позе, игнорируя yaw/pitch из мира); **`noFollowCursor = true`** — не поворачивать голову за мышью; **`noHurtAnim = true`** — отключить покраснение при уроне; **`animation = false`** — отключить анимации. Старое **`viewport`** в скрипте по-прежнему можно писать — в JSON оно превращается в `crop`.

---

### Кастомные Команды ---

Можно регистрировать кастомные команды для сервера прямо из скрипта.
Такая команда будет доступна всем игрокам (или с нужным permission, если вы добавите проверку).

```
create command mycmd {
    val p = _eventPlayer
    val args = _eventArgs # Список аргументов, переданных после команды

    if (p != null) {
        if (size(args) > 0) {
            val firstArg = args[0]
            chat(p, "Команда вызвана с аргументом: " + firstArg)
        } else {
            chat(p, "Команда вызвана без аргументов.")
        }
        playSound(p, "minecraft:entity.experience_orb.pickup", 1.0, 1.0)
    }
}
```

**Особенности `create command`:**
- Регистрирует реальную серверную команду, которая отображается в автодополнении (TAB) у игроков.
- Поддерживает произвольное количество аргументов (жадная строка). Аргументы в кавычках (например, `"Привет мир"`) считаются за один аргумент.
- Движок автоматически конвертирует аргументы: числа становятся числами (`Double`), `true`/`false` становятся `Boolean`, а всё остальное остаётся строками (`String`).
- Код внутри выполняется **асинхронно** как новая задача, поэтому там можно использовать `await`.
- Внутри блока доступна локальная переменная `_eventPlayer` (тип `ServerPlayer`) и `_eventArgs` (список `List<Object>`).

knight.playOnce("laughter")  # смех, когда кричит ПОГНАЛИ
say(knight, "ЕЕЕЕ! ПОГНАЛИ!")
await keybind("f")


say(readix_avatar, "Удачи в приключениях! Еще увидимся.")
await keybind("f")

knight.playLoop("talking2")
readix_avatar.playLoop("talking1")
readix_avatar.alwaysLookAt(knight)
knight.alwaysLookAt(readix_avatar)

###  Кастомные Блоки, Предметы, Вкладки и Крафты и т.д. ---

Вы можете создавать свои блоки, предметы, творческие вкладки и даже рецепты (крафты) прямо в `.spr` скриптах! Это читается движком на этапе загрузки (до запуска самого кода), поэтому эти элементы будут зарегистрированы в игре как настоящие модовые предметы.

```
create tab my_tab {
    name = "§dМагические предметы"
    icon = "minecraft:diamond"
}

create item magic_stick {
    name = "§eМагическая палочка"
    model = "spraute_engine:item/stick"
    texture = "spraute_engine:item/stick"
    maxStackSize = 1
    tab = "my_tab"
}

create block magic_stone {
    name = "§bМагический камень"
    model = "spraute_engine:block/stone"
    texture = "spraute_engine:block/stone"
    collision = true
    hardness = 2.0
    light = 15
    drop = "spraute_engine:magic_stick"
    tab = "my_tab"
}

# Руда в мире + дроп предмета вместо блока:
create block magic_ore {
    texture = "textures/block/magic_ore.png"
    hardness = 3.0
    is_ore = true
    ore_vein = 7
    ore_min = -16
    ore_max = 64
    ore_chances = 6
    drop = "magic_stick"
    drop_replace = true
    tab = "my_tab"
}

# Плита на половину высоты:
create block magic_slab {
    texture = "textures/block/magic_slab.png"
    hardness = 2.0
    hitbox = [1, 0.5]
    tab = "my_tab"
}

# Узкая колонна 0.4×0.4×1.2 блока:
create block magic_pillar {
    texture = "textures/block/pillar.png"
    hitbox = [0.4, 0.4, 1.2]
}
```

### Рецепты (Крафты)
Вы можете добавлять рецепты прямо в скрипт с помощью `create craft`.

**Два режима верстака:**
- `type = "any"` (или `shapeless`) — ингредиенты в любом порядке, слоты не важны.
- `type = "slots"` (или `shaped`, `grid`) — фиксированная схема в сетке 3×3.

**Замена предметов (любой из списка / тег):**
- Тег Minecraft: `"#minecraft:planks"`, `"#minecraft:logs"`
- Свой список: `["minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks"]`

**Крафт по слотам (slots / shaped):**
```
create craft my_sword {
    type = "slots"
    pattern_1 = " I "
    pattern_2 = " I "
    pattern_3 = " S "
    key_I = "minecraft:iron_ingot"
    key_S = ["minecraft:stick", "minecraft:bamboo"]  # любая палка/бамбук
    result = "spraute_engine:magic_stick"
    count = 1
}
```

**Крафт без слотов (any / shapeless):**
```
create craft my_shapeless {
    type = "any"
    ingredients = ["minecraft:iron_ingot", "#minecraft:planks"]
    result = "minecraft:iron_sword"
    count = 1
}
```

**Переплавка (smelting, blasting, smoking, campfire_cooking):**
```
create craft iron_ingot {
    type = "smelting"
    ingredient = "#minecraft:logs"
    result = "minecraft:charcoal"
    xp = 0.7
    time = 200
}
```

# --- 8. Кастомные Инвентари (Контейнеры со слотами) ---

Теперь `create ui` поддерживает настоящие слоты Minecraft!
Вы можете создать меню, в которое игроки смогут перетаскивать свои предметы, прямо как в сундуке или верстаке.

Для этого укажите в корне UI параметр `type = "container"` и используйте виджеты `slot` и `player_inventory`.

```spr
create ui my_workbench {
    type = "container"
    size = [176, 166]
    background = "#00000000" # Прозрачный фон
    
    # Можно отрисовать классическую текстуру сундука или раздатчика!
    image("bg") {
        pos = [0, 0]
        size = [176, 166]
        texture = "minecraft:textures/gui/container/dispenser.png"
    }

    # Инвентарь игрока (автоматически добавляет 36 слотов)
    player_inventory {
        pos = [8, 84]
    }

    # Кастомные слоты (начиная с индекса 0)
    slot(0) { pos = [62, 20] }
    slot(1) { pos = [80, 20] }
    slot(2) { pos = [98, 20] }
    
    # Кнопка для обработки рецепта
    button("craft_btn", "Скрафтить") {
        pos = [62, 50]
        size = [54, 20]
    }
}
```

### Динамическое отображение предметов и 3D-моделей на блоке (Например: ПК на столе)

Кастомные блоки, созданные через `create block`, поддерживают визуальное отображение предметов и полноценных 3D-моделей прямо на блоке. Сами кастомные блоки при установке теперь **автоматически поворачиваются в зависимости от того, куда смотрит игрок**. 

Если вы добавляете дисплей (предмет или модель) на блок, его вращение и смещение будут автоматически учитывать поворот самого блока, поэтому вам не нужно считать углы вручную! Вы просто настраиваете смещение для блока, стоящего "лицом" (на север), а движок сам повернёт все элементы, если блок стоит боком.

*   `openBlockUi(player, x, y, z, ui_template)` — открывает `ui_template` для игрока. Предметы, положенные в слоты, сохраняются во внутреннем инвентаре кастомного блока! Если вы используете обычный `uiOpen`, предметы выпадают при закрытии, но `openBlockUi` их сохраняет.
*   `getBlockSlot(x, y, z, slot_index)` — возвращает ID предмета (например, `"minecraft:diamond"`), лежащего в указанном слоте кастомного блока. Если пусто, возвращает `""`.
*   `setBlockSlot(x, y, z, slot, item_id, [count])` — положить предмет в слот визуального блока (`create block`) без UI.
*   `setChestLoot(x, y, z, slot, item_id, [count])` — настроить лут **ванильного** сундука/бочки/шалкера по координатам (сохраняется в мире, заполняется при **первом** открытии).
*   `clearChestLoot(x, y, z)` — удалить настроенный лут с позиции.
*   `setBlockDisplay(x, y, z, id, item_id, ox, oy, oz, rx, ry, rz, scale)` — устанавливает визуальное отображение предмета на самом блоке!
    *   `id` — уникальное строковое имя для этого отображения (чтобы потом его можно было удалить, например `"monitor"`).
    *   `item_id` — ID предмета для отображения (например, `"spraute_engine:monitor"`).
    *   `ox, oy, oz` — смещение от центра блока.
    *   `rx, ry, rz` — вращение по осям X, Y, Z в градусах.
    *   `scale` — масштаб (например, `1.0` — обычный размер).
*   `setBlockDisplayModel(x, y, z, id, model, texture, ox, oy, oz, rx, ry, rz, scale)` — то же самое, но отображает кастомную 3D-модель (через `.geo.json`) вместо предмета.
    *   `model` — путь к модели, например `"geo/monitor.geo.json"` (ищется в папке модуля или можно указать с `modid:`).
    *   `texture` — путь к текстуре, например `"textures/entity/monitor.png"`.
*   `setBlockDisplayBlock(x, y, z, id, block_id, ox, oy, oz, rx, ry, rz, scale)` — отображает другой блок поверх текущего (полезно, если вы создали блок через `create block` и хотите отрендерить его на столе).
*   `removeBlockDisplay(x, y, z, id)` — удаляет визуальное отображение с указанным `id` с блока.

**Пример скрипта для стола с ПК:**
```spr
# Когда игрок кликает по столу
on clickBlock("spraute_engine:pc_table") -> t {
    val bx = _eventX
    val by = _eventY
    val bz = _eventZ
    
    # Открываем UI стола и привязываем его к инвентарю этого блока
    # В отличие от обычного uiOpen, предметы из слотов никуда не выпадут
    openBlockUi(_eventPlayer, bx, by, bz, pc_table_ui)
    
    # Ждем закрытия UI
    await uiClose(_eventPlayer)
    
    # После закрытия проверяем 0-й слот (допустим, там монитор)
    val monitor_item = getBlockSlot(bx, by, bz, 0)
    
    if (monitor_item != "") {
        # Отображаем 3D-модель монитора на столе. 
        # Стол сам повернёт монитор правильно, так как знает своё направление!
        setBlockDisplayModel(bx, by, bz, "monitor", "geo/monitor.geo.json", "textures/entity/monitor.png", 0.0, 1.2, 0.0, 0.0, 0.0, 0.0, 1.0)
    } else {
        removeBlockDisplay(bx, by, bz, "monitor")
    }
}
```


## Присваивание свойств: `id.property = value`

Работает для сущностей из `NpcManager` (`id` — строка). Свойства:

| Свойство | Кому | Заметки |
|----------|------|---------|
| `name` | любая | `Component` из строки |
| `show_name` | любая | boolean / строка `"true"` |
| `hp` | живая | выставляет и max HP, и текущее |
| `speed` | живая | `MOVEMENT_SPEED` |
| `x`, `y`, `z` | любая | `setPos` по одной оси |
| `yaw`, `pitch` | любая | для LivingEntity голова/тело синхронизируются с yaw |
| `model`, `texture`, `idle_anim`, `walk_anim` | **SprauteNpcEntity** | — |

---

## Чтение свойств: `object.property`

Если `object` — переменная с игроком:

- `name`, `hp`, `x`, `y`, `z`, `pitch`, `yaw`, `look_x`, `look_y`, `look_z` (вектор взгляда), `uuid`, `java`, `camera` (CAMERA-нода, см. «Кинематографическая камера»)

Если — NPC (строковый id или сущность):

- `name`, `show_name`, `hp`, `max_hp`, `speed`, `x`, `y`, `z`, `yaw`, `pitch`, `model`, `texture`, `uuid`, `java`

Неизвестное имя свойства — `null` / предупреждение в лог.

---

## Цепочки вызовов и свойств (Chaining)

Движок поддерживает неограниченную вложенность вызовов свойств и методов, а также индексов массивов. Это полезно при работе со списками, словарями или `java`-объектами.

Пример:
```javascript
val entity_uuid = player.java().getUUID().toString()
val my_var = some_list[0].property.my_method()
my_obj.nested_obj.x = 10
```

---

## Методы сущностей

### Любая сущность
`a.distance_to(b)` - Расстояние до другой сущности (`b` — объект или разрешённый id / `player` / `npc` / …).
`a.java()` - Возвращает оригинальный Java-объект `Entity` (например, ServerPlayer) для использования в Java API.
`a.uuid()` - Возвращает строковый UUID сущности.

### Игрок: `player.method(...)`

| Метод | Описание |
|-------|----------|
| `slot(n)` | Registry id предмета в слоте или `""` |
| `slotCount(n)` | Количество в слоте |
| `slotNbt(n)` | NBT строкой или `""` |
| `hasItem(item_id)` | Есть ли предмет |
| `countItem(item_id)` | Суммарное количество |
| `raycast([max_dist])` | Пускает луч взгляда (по умолчанию макс. дистанция 50). Возвращает словарь с полем `"type"` (`"entity"`, `"block"` или `"miss"`). Для сущности: `hit["entity"]`, `hit["x"]`, `hit["y"]`, `hit["z"]`. Для блока: `hit["block"]` (id), `hit["x"]`, `hit["y"]`, `hit["z"]`. |
| `facing()` / `direction()` / `lookVector()` | Направление взгляда (работает у игрока и любой LivingEntity). Словарь: `yaw`, `pitch`, `direction` (`north`/`south`/`east`/`west`/`up`/`down`), `horizontal` (без учёта pitch), `dx`/`dy`/`dz` (единичный вектор), `hx`/`hz` (шаг −1/0/1 для копания по горизонтали). |
| `damage(amount)` | Наносит урон игроку (или любой другой LivingEntity). `amount` по умолчанию 1.0. |
| `teleport(x, y, z)` | Телепортирует сущность (игрока, NPC, моба) на указанные координаты. Альтернативное название: `tp(x, y, z)`. |
| `camera` | CAMERA-нода игрока (`player.camera`). Катсцена, rest-поза, `flySmooth` / `flyTime`, `moveTo` / `lookAt` / `followRoute` — раздел «Кинематографическая камера». |

### Spraute NPC: `npcId.method(...)` (в т.ч. после `create npc`)

Методы **без учёта регистра** в `executeCallMethod`.

#### Анимации
| Метод | Аргументы |
|-------|-----------|
| `playOnce(anim [, additive])` | Один раз. NPC: additive по умолчанию; MODEL: replace (idle гасится) |
| `playLoop(anim [, additive])` | Цикл. То же про additive. На MODEL реально запускает клип из `.animation.json` |
| `playFreeze(anim [, additive])` | До последнего кадра и стоп |
| `stopOverlay()` | сброс оверлея |
| `stop("animName")` | остановить оверлей, если имя совпадает |

Нужен файл анимации на сущности (`animation = "..."`). Имя клипа можно короткое (`idle`) или полное (`animation.npc.idle`).

```spr
statue.playLoop("idle")           # MODEL: заменить позу
npc.playLoop("talking1")          # NPC: additive поверх idle
statue.playLoop("wave", true)     # MODEL: аддитивно поверх idle
statue.stop("idle")
```

| `setAdditiveWeight(w)` | 0…1, процедурный вес |
| `setHitbox(w, h)` | Размер основного хитбокса (ширина × высота, в блоках) |
| `setHitbox(w, h, ox, oy, oz)` | Размер + смещение центра хитбокса (блоки) |
| `setHitboxOffset(ox, oy, oz)` | Смещение основного хитбокса |
| `resetHitbox()` | Сброс к 0.6 × 1.8 без смещения |
| `setHitboxPreset("player" \| "small" \| "large")` | Стандартные пресеты размеров |
| `addBoneHitbox(bone, w, h, d)` | Доп. хитбокс у кости (следует за анимацией) |
| `addBoneHitbox(id, bone, w, h, d, ox, oy, oz)` | Костный хитбокс с id и смещением (смещение в 1/16 блока) |
| `removeBoneHitbox(id)` | Удалить костный хитбокс |
| `clearBoneHitboxes()` | Убрать все костные хитбоксы |
| `showHitboxDebug(true/false)` | Подсветка хитбоксов в мире (отладка) |
| `setDeathAnim(anim)` | Название кастомной анимации смерти (по умолчанию `"death"`) |

Второй аргумент анимации:  
`true` / `"add"` / `"additive"` — **накопительное** смешивание (по умолчанию, если аргумента нет — **additive**).  
`false` / `"replace"` / `"set"` / `"noadd"` — **lerp к ключам** оверлея (не суммировать с idle по костям).

#### Движение / следование / плавание / полет
| Метод | Описание |
|-------|----------|
| `moveTo(x, y, z)` или `moveto` | Опционально 4-й аргумент — скорость (по умолчанию 1.0). С **`await`** блокирует, пока NPC не подойдёт к точке. |
| `alwaysMoveTo(x, y, z)` или `alwaysMoveTo(entity)` | Постоянно обновлять путь к точке или сущности. Опционально последний аргумент — скорость. |
| `stopMove()` | Остановка постоянного следования, заданного через alwaysMoveTo. |
| `followUntil(target, dist?)` | С **`await`** — ждать, пока не окажется ближе `dist` (по умолчанию 2.0) от цели. `target` — сущность или строка/id. |
| `setSwimming(bool)` | Включает режим "амфибии", позволяя NPC плавать в воде |
| `setSwimIdleAnim(anim)` | Анимация бездействия в воде |
| `setSwimWalkAnim(anim)` | Анимация движения в воде |
| `setFlying(bool)` | Вкл/выкл полёт (при выкл. также останавливает движение) |
| `flyTo(x, y, z, [speed])` | Включить полёт и лететь к точке. С **`await`** — ждать прибытия |
| `flyTo(entity, [speed])` | Включить полёт и лететь к сущности. С **`await`** — ждать сближения |
| `alwaysFlyTo(x, y, z, [speed])` | Постоянно лететь к точке |
| `alwaysFlyTo(entity, [speed])` | Постоянно лететь за сущностью |
| `setFlyIdleAnim(anim)` | Анимация зависания в воздухе |
| `setFlyWalkAnim(anim)` | Анимация полета |
| `followRoute(route [, speed])` / `walkRoute` | Идти по **ROUTE-ноде** сцены (или ссылке на неё). С **`await`** — ждать конца. Последнее число — скорость (по умолчанию 1) |
| `alwaysFollowRoute(route [, speed])` | Зациклить ходьбу по маршруту |
| `flyRoute(route [, speed])` | Полёт по сглаженной polyline маршрута |
| `alwaysFlyRoute(route [, speed])` | Зациклить полёт по маршруту |
| `flySmoothing` (свойство) | 0.02…1 — плавность ускорения в воздухе (метод `setFlySmoothing` / присвоение) |

```text
knight.setFlying(true)
knight.flyTo(10, 80, 10, 1.2)
await knight.flyTo(20, 90, 5)
knight.alwaysFlyTo(player, 1.0)
knight.setFlying(false)

await guard.followRoute(patrol_route, 1.0)
guard.alwaysFlyRoute(sky_path, 1.2)
```

#### Управление
| Метод | Описание |
|-------|----------|
| `remove()` | Удаляет NPC из мира |

#### Предметы в руках
| Метод | Описание |
|-------|----------|
| `setItem(hand, item_id)` | `hand`: например `"right"`, `"left"` |
| `setItem(hand, item_id, nbt_string)` | NBT как в `/give` |
| `removeItem(hand)` | очистить руку |
| `removeItem()` | обе руки |

#### Кастомный дроп
| Метод | Описание |
|-------|----------|
| `addDrop(item_id, [min=1], [max=1], [chance=100], [nbt_string])` | Добавляет предмет в список лута, который выпадет при смерти NPC |
| `drop(item_id, [count=1], [check_inventory=false])` | Выбрасывает предмет из NPC в мир (спавнит предмет). Если `check_inventory` = `true`, то предмет будет выброшен *только* если он есть в инвентаре (pickup) у NPC, и при этом будет оттуда изъят. |

#### Подбор предметов NPC
| Метод | Описание |
|-------|----------|
| `pickupOnlyFrom(entity)` | только предметы от этой сущности (бросок) |
| `pickupAny()` | снять фильтр |
| `pickupMax(item_id, max, [nbt])` | не подбирать этот предмет, если у NPC уже `max` штук |
| `clearPickupMax()` | снять все лимиты `pickupMax` |
| `clearPickupMax(item_id, [nbt])` | снять лимит одного предмета |

Лимит «не больше N» также задаётся в **`on pickup(npc, item, max)`** (или 4-м аргументом после NBT) и на время **`await pickup(npc, amount, item)`** (ждать `amount` новых штук и не давать подобрать больше). Несколько лимитов на один предмет складываются как минимум.

#### Взгляд
| Метод | Описание |
|-------|----------|
| `lookAt(x, y, z)` или `lookAt(entity)` | разовый поворот головы |
| `alwaysLookAt(x, y, z)` или `alwaysLookAt(entity)` | постоянно смотреть |
| `stopLookAt()` | снять режим взгляда |
| `setHeadBone(name)` | имя кости головы в модели Bedrock (по умолчанию `"head"`) |

Скорость поворота тела (° за тик, по умолчанию `6`):

| Свойство | Когда |
|----------|-------|
| `walkTurnSpeed` | Пока NPC идёт (тело к направлению пути) |
| `idleTurnSpeed` | Пока стоит и смотрит (`lookAt` / `alwaysLookAt`) |

```text
create npc guard {
  walkTurnSpeed = 12
  idleTurnSpeed = 4
}
guard.walkTurnSpeed = 20
_node.idleTurnSpeed = 8
```

#### Инвентарь NPC (как у игрока по смыслу)
| Метод | Описание |
|-------|----------|
| `countItem(item_id)` | |
| `countItem(item_id, nbt)` | `null` / пустая строка — без учёта NBT |

В **выражениях** у NPC также доступен `countItem` как метод (см. `evaluateExpression`).

### Методы строк и чисел

Для строк (`"text"`) и чисел (`123`) доступны следующие методы:

| Метод | Для типа | Описание |
|-------|----------|----------|
| `toInt()` | Строки и числа | Пытается преобразовать строку в целое число (или возвращает целую часть числа). |
| `toDouble()` | Строки и числа | Пытается преобразовать строку в дробное число (или приводит число к дробному типу). |
| `toString()` | Строки и числа | Возвращает строковое представление. |
| `length()` | Строки | Возвращает длину строки (аналог `strLen`). |
| `split(delimiter)` | Строки | Разбивает строку на список по разделителю (по умолчанию пробел). |
| `contains(substring)` | Строки | Возвращает `true`, если подстрока содержится в строке. |
| `replace(target, replacement)` | Строки | Заменяет все вхождения `target` на `replacement`. |

---

## `await ...`

| Форма | Значение |
|-------|----------|
| `await time(seconds)` | Пауза на число секунд. Можно `await time(setCamera(...))` / `await time(player.camera.moveTo(...))` — функция сама возвращает длительность |
| `await cameraRoute(player, routeName, …)` | Ждать окончания `playCameraRoute` (те же аргументы) |
| `await interact(entity)` | Пока игрок не взаимодействует с целью (id / сущность) |
| `await next` | Продолжение по привязанной клавише диалога |
| `await keybind("key")` | Ожидание нажатия клавиши. Названия кнопок: `"g"`, `"f"`, `"space"`, `"shift"` и т.д. |
| `await death(target)` | Смерть сущности: `target` — id NPC, ник, `player`, `npc`, `mob`, `any` |
| `await kill(killer, [victim])` | Ожидать убийство: `killer` — кто убил (`player`, `npc`, `mob`, id НИПа, сущность); `victim` — кого (`any`, `player`, `npc`, `mob`, id). Возвращает убитую сущность. |
| `await pickup(npc, amount, item_id)` | Ждать, пока NPC **подберёт ещё** `amount` штук (не «станет amount всего»). На время ожидания ставится кап `текущее + amount`. Опционально 4-й аргумент — NBT |
| `await orbPickup(player, amount, [texture])` | Ожидает, пока игрок не подберет нужное количество кастомных орбов (вызванных через `spawnOrb`). |
| `await task("task_id")` | Пока не завершится именованный `async`-блок |
| `await uiClick(player)` | Пока игрок не нажмёт кнопку с `id` или не закроет экран (ESC). Результат — строка `id` кнопки или `"__close__"` при закрытии. Внутри `async { }` **не поддерживается** (ошибка выполнения). |
| `await uiClose(player)` | Ожидать только закрытия интерфейса игроком (по нажатию ESC или через код). |
| `await uiInput(player, [widget_id])` | Ожидать ввод текста в указанный виджет `input` (или в любой, если `widget_id` не указан). Возвращает введенный текст. |
| `await uiTouch(player, id1, id2)` | (или `uiOverlap`) Ожидает, пока два UI виджета с указанными ID не пересекутся на экране игрока. |
| `await position(player, x, y, z, [radius])` | Ожидать пока игрок не окажется в заданном радиусе (по умолчанию 1.5) от точки. Возвращает сущность игрока. |
| `await inventory(player, item_id, [count])` | (или `hasItem`) Ожидать пока у игрока не появится нужное кол-во предмета. Возвращает сущность игрока. |
| `await clickBlock(player, [target])` | Ожидать пока игрок не кликнет (ЛКМ/ПКМ) по блоку. Возвращает `"left"` или `"right"`. Варианты `target`: пусто, `block_id`, `x, y, z` или `x, y, z, block_id`. |
| `await breakBlock(player, [target])` | Ожидать пока игрок не сломает блок. Возвращает `block_id`. Варианты `target` как у `clickBlock`. |
| `await placeBlock(player, [target])` | Ожидать пока игрок не поставит блок. Возвращает `block_id`. Варианты `target` как у `clickBlock`. |
| `await openChest(player, [target])` | Ожидать открытие сундука/бочки/шалкера (ПКМ). Возвращает `block_id`. Фильтры как у `clickBlock`. |
| `await openDoor(player, [target])` | Ожидать открытие двери/люка/калитки (ПКМ). Возвращает `block_id`. Фильтры как у `clickBlock`. |
| `await chat(player, message, [ignore_case=true], [ignore_punct=true])` | Ожидать конкретное сообщение от игрока. `message` может быть строкой или массивом строк. Возвращает сообщение, которое игрок написал. |
| `await jump(player)` | Ожидать прыжок игрока. |
| `await join(player)` | Ожидать вход игрока в мир (логин). |
| `await firstJoin(player)` | Ожидать первый вход игрока в этот мир. |
| `await leave([player])` | Ожидать выход игрока с сервера. Без аргумента — любой игрок. Алиасы: `logout`, `playerLeave`, `player_leave`. В `_eventPlayer` сохраняется вышедший игрок. |
| `await action(player, action_type, [target])` | (или `playerAction`) Ожидать действия игрока (`"use"`, `"eat"`, `"fish"`, `"hoe"`, `"jump"`, `"sleep"`, `"craft"`, `"drop"`). Возвращает объект или ID предмета/блока, если применимо. |

---

## События `on event(...) -> handlerId { }`

Регистрируется обработчик; остановка: `stop(handlerId)`.

Поддерживаемые имена событий (см. `ScriptExecutor`):

### `on interact(target) -> ...`
При взаимодействии с `target`. В теле: **`_eventPlayer`** — игрок.

`wait` / `await` в теле `on …` / `every` работают (если в теле есть await — handler стартует как async). Пока цепочка не закончилась, повторное событие не перезапускает handler с начала — только снимает текущий await. Внутри `fun` по-прежнему без паузы — для ожиданий используй `async { }` или тело `on`/`every`.

### `on keybind("keyName") -> ...`
В теле: **`_eventPlayer`**.

### `on join([player]) -> ...` / `on firstJoin([player]) -> ...`
Срабатывает при входе игрока в мир. `firstJoin` — только при первом входе в этот мир. Без аргумента — любой игрок. Скрипт должен быть запущен вручную или нодой автоматически запускаемой сцены. В теле: **`_eventPlayer`**.

### `on leave([player]) -> ...`
Срабатывает при выходе игрока с сервера. Без аргумента — любой игрок. Алиасы события: `logout`, `playerLeave`, `player_leave`. В теле: **`_eventPlayer`**.

### `on death(targetFilter) -> ...`
В теле: **`_eventEntity`**, **`_eventKiller`**.

### `on kill(killerFilter, [victimFilter]) -> ...`
Срабатывает, когда `killerFilter` убивает сущность. Фильтры как у `death` / `await kill`. В теле: **`_eventEntity`** (жертва), **`_eventKiller`** (убийца).

### `on pickup(npc, item_id)` / `on pickup(npc, item_id, max)` / `on pickup(npc, item_id, nbt, [max])`
Срабатывает, когда у NPC **прибывает** этот предмет. В теле: **`_eventNpc`**, **`_eventItem`** (стек), **`_eventDropper`** (кто выбросил, если известен), **`count`** (сколько подобрали **в этом срабатывании**), **`_eventItemCount`** (сколько **всего** у NPC после подбора).

Число `max` — кап «не больше N штук этого предмета»; пока хендлер активен, NPC не подберёт сверх лимита. `on pickup` сам включает подбор этого предмета (ваниль с `mobGriefing=false` лут не даёт).

### `on uiClick([player]) -> ...`
Срабатывает при клике по кнопке в скриптовом UI. `player` указывать не обязательно (тогда сработает для любого). В теле: **`_eventPlayer`** и **`_eventWidget`** (строковый ID кнопки).

### `on uiClose([player]) -> ...`
Срабатывает при закрытии скриптового UI игроком. `player` опционально. В теле: **`_eventPlayer`**.

### `on uiInput([player], [widget_id]) -> ...`
Срабатывает при вводе текста в поле `input` (каждое изменение текста). В теле: **`_eventPlayer`**, **`_eventWidget`** (ID инпута), **`_eventInput`** (новый текст).

### `on uiTouch(player, id1, id2)` или `on uiOverlap(...) -> ...`
Срабатывает, когда два UI виджета с указанными ID пересекаются на экране игрока. В теле: **`_eventPlayer`**.

### `on position(player, x, y, z, [radius]) -> ...`
Срабатывает, когда игрок заходит в заданный радиус (по умолчанию 1.5). В теле: **`_eventPlayer`**.

### `on inventory(player, item_id, [count])` или `on hasItem(...) -> ...`
Срабатывает, когда у игрока появляется заданное количество предмета. В теле: **`_eventPlayer`**.

### `on clickBlock([target]) -> ...`
Срабатывает при клике ЛКМ/ПКМ по блоку. Варианты `target`: пусто (любой блок), `block_id`, `x, y, z` или `x, y, z, block_id`.
В теле: **`_eventPlayer`**, **`_eventX`**, **`_eventY`**, **`_eventZ`**, **`_eventBlock`** (строковый ID блока), **`_eventAction`** (`"left"` или `"right"`). `cancelEvent()` — отменяет клик (в т.ч. начало копания ЛКМ).

### `on breakBlock([target]) -> ...`
Срабатывает при разрушении блока игроком. Варианты `target` как у `clickBlock`. В теле: **`_eventPlayer`**, **`_eventX`**, **`_eventY`**, **`_eventZ`**, **`_eventBlock`**. `cancelEvent()` — блок не ломается.

### `on placeBlock([target]) -> ...`
Срабатывает при установке блока игроком. Варианты `target` как у `clickBlock`. В теле: **`_eventPlayer`**, **`_eventX`**, **`_eventY`**, **`_eventZ`** (координаты устанавливаемого блока), **`_eventBlock`**. Можно вызвать `cancelEvent()`, чтобы отменить установку.

### `on openChest([target]) -> ...`
Срабатывает при попытке открыть сундук, бочку или шалкер (ПКМ). Фильтры `target` как у `clickBlock`. В теле: **`_eventPlayer`**, **`_eventX`**, **`_eventY`**, **`_eventZ`**, **`_eventBlock`**. `cancelEvent()` — сундук не откроется. При первом открытии применяется лут из `setChestLoot`, если настроен.

### `on openDoor([target]) -> ...`
Срабатывает при попытке открыть дверь, люк или калитку (ПКМ). Фильтры как у `clickBlock`. В теле: **`_eventPlayer`**, **`_eventX`**, **`_eventY`**, **`_eventZ`**, **`_eventBlock`**. `cancelEvent()` — дверь не откроется.

### `on jump([player]) -> ...`
Срабатывает при прыжке игрока. В теле: **`_eventPlayer`**. `cancelEvent()` — отменяет прыжок (сбрасывает вертикальную скорость).

### `on attack([player]) -> ...`
Срабатывает при атаке сущности (ЛКМ по мобу/игроку). В теле: **`_eventPlayer`**, **`_eventTarget`** (атакованная сущность). `cancelEvent()` — удар не проходит.

### `on sprint([player]) -> ...`
Срабатывает в момент **начала** спринта. В теле: **`_eventPlayer`**. `cancelEvent()` — спринт сразу сбрасывается (`setSprinting(false)`).

Для опроса во время бега: **`player.isSprinting`** (чтение) / **`player.isSprinting = false`** (запись).

### `on action([player], action_type, [target])` или `on playerAction(...) -> ...`
Срабатывает при определённом действии игрока.

Типы `action_type`:
- `"use"` — **ПКМ с предметом в руке** (кастомные предметы из `create item` всегда шлют это событие; для ванильных предметов — при успешном использовании).
- `"eat"`, `"fish"`, `"hoe"`, `"jump"`, `"attack"`, `"sprint"`, `"sleep"`, `"craft"`, `"drop"` — остальные действия.
- Для `"attack"` / `"jump"` / `"sprint"` `cancelEvent()` работает так же, как у dedicated `on attack` / `on jump` / `on sprint`.

Формы записи:
```text
on action("use", "magic_wand") -> shoot_wand { ... }          # любой игрок, фильтр по id предмета
on action(player, "use", "magic_wand") -> shoot_wand { ... }  # конкретный игрок
on action("use") -> any_use { ... }                           # любой ПКМ предметом
```

В теле: **`_eventPlayer`** , **`_eventTarget`** — объект предмета/блока (если есть), **`_eventItemId`** — строковый id предмета (например `spraute_engine:magic_wand` или `magic_wand`).

Для кастомного предмета `target` в фильтре можно писать коротко: `"magic_wand"` (без namespace).

### `on chat([player], [message], [ignore_case=true], [ignore_punct=true]) -> ...`
Срабатывает при написании сообщения в чат. Если `player` и `message` не указаны, ловит любые сообщения от любых игроков. `message` может быть строкой или массивом строк.
В теле: **`_eventPlayer`**, **`_eventMessage`** (какое именно сообщение из вариантов было написано).

### `on orbPickup([player], [texture])`
Срабатывает при подборе игроком кастомной частицы (орба/опыта), созданной через `spawnOrb`.
- `player` (опционально) — ожидаемый игрок (сущность).
- `texture` (опционально) — текстура орба для фильтра (например, `"minecraft:textures/item/diamond.png"`).
В теле обработчика доступны: **`_eventPlayer`** (кто подобрал), **`_eventTexture`** (строка текстуры), **`_eventAmount`** (значение).

---

## `every(seconds) -> handlerId { }`
Периодический запуск тела с заданным интервалом (секунды — выражение).

---

## `async { }` / `async "task_id" { }`
Запуск кода в фоне.  
`await task("task_id")` ждёт завершения.  
`stop_task("task_id")` — прервать.  
`taskDone("task_id")` — проверка завершения (через `ScriptContext`).

---

## Пользовательские функции

```text
fun name(a, b) {
    return a + b
}
```

Вызов: `name(1, 2)` — как обычная функция.

---

## Снаряды

Кастомные снаряды объявляются блоком **`create projectile`** (в `.spr` или нода **RES_PROJECTILE** в сцене → «Ресурсы → Снаряд»), летят как отдельные сущности и вызывают **`on projectileHit`**.

**Тип отрисовки** — свойство `renderStyle` (или устаревшее `billboard = true/false`):

| `renderStyle` | Вид |
|---------------|-----|
| `billboard` (по умолчанию) | Плоский спрайт всегда к камере |
| `velocity`, `arrow` | Плоскость по направлению полёта (как стрела) |
| `cross`, `x`, `fireball` | Две перпендикулярные плоскости (крест, «фаербол») |

### Объявление и запуск

```text
create projectile wand_bolt {
    texture = "textures/projectile/wand_bolt.png"
    renderStyle = "velocity"
    width = 0.25
    height = 0.25
    gravity = 0
    drag = 1.0
    damage = 5
    lifetime = 200
}

create projectile fireball {
    texture = "textures/projectile/fire.png"
    renderStyle = "cross"
    glow = true
    width = 0.6
    height = 0.6
}

# Выстрел по направлению взгляда; возвращает UUID снаряда (строка)
val bolt = shootProjectile("wand_bolt", player, 2.0)

# Или с позицией и скоростью вручную:
spawnProjectile("wand_bolt", x, y, z, vx, vy, vz)
```

| Функция | Описание |
|---------|----------|
| `spawnProjectile(id, x, y, z, vx, vy, vz, [dimension])` | Создать снаряд в точке с заданной скоростью. Возвращает UUID. |
| `shootProjectile(id, shooter, speed, [dimension])` | Выстрел из `shooter` (игрок / НИП) по направлению взгляда. Возвращает UUID. |
| `shootProjectileNear(id, shooter, speed, ox, oy, oz, [dimension])` | То же, со смещением точки вылета. |
| `getProjectilePos(uuid)` | Текущие координаты полёта: список `[x, y, z]` или `null`, если снаряд уничтожен. Аргумент — UUID из `shootProjectile` / `spawnProjectile` или сущность. |
| `removeProjectiles([id])` | Удалить все кастомные снаряды в мире или только с указанным `id`. |

`getEntityPos(uuid)` тоже работает для снаряда, но `getProjectilePos` возвращает `null`, если объект уже не является живым кастомным снарядом.

### Событие попадания

```text
on projectileHit("wand_bolt") -> wand_hit {
    createExplosion(_eventHitX, _eventHitY, _eventHitZ, 3, 8, true)
}

on projectileHit("wand_bolt", "block") -> hit_block { ... }
on projectileHit("wand_bolt", "entity") -> hit_entity { ... }
on projectileHit(any) -> any_hit { ... }
```

В теле: **`_eventProjectileId`**, **`_eventHitType`** (`"block"` / `"entity"`), **`_eventHitX`**, **`_eventHitY`**, **`_eventHitZ`**, **`_eventTarget`** (сущность при попадании в моба/игрока), **`_eventOwner`** (кто выпустил).

`await projectileHit("wand_bolt")` — ждать попадания в основном потоке скрипта.

### Пример: волшебная палочка (ПКМ → невидимый снаряд → частицы → взрыв)

```text
create item magic_wand {
    name = "§dВолшебная палочка"
    texture = "textures/item/magic_wand.png"
    maxStackSize = 1
}

create projectile wand_bolt {
    alpha = 0
    width = 0.01
    height = 0.01
    gravity = 0
    damage = 0
    collide_blocks = true
    collide_entities = true
}

on action("use", "magic_wand") -> shoot_wand {
    val bolt = shootProjectile("wand_bolt", _eventPlayer, 2.0)
    async "wand_trail" {
        while (true) {
            val pos = getProjectilePos(bolt)
            if (pos == null) { return }
            particleSpawn("enchant", listGet(pos, 0), listGet(pos, 1), listGet(pos, 2), 2, 0, 0.05, 0, 0.02)
            await time(0.05)
        }
    }
}

on projectileHit("wand_bolt") -> wand_explode {
    createExplosion(_eventHitX, _eventHitY, _eventHitZ, 3, 8, true)
    particleSpawn("witch", _eventHitX, _eventHitY, _eventHitZ, 15, 0.2, 0.2, 0.2, 0.1)
}
```

> **ProCode:** действие `"use"` есть в блоках «когда игрок» и «ждать действие игрока». Для фильтра по предмету включите «с целью» и укажите id, например `magic_wand`.

---

## Частицы (Партиклы)

Система частиц позволяет создавать как единичные эффекты, так и привязывать их к костям NPC (через отправку пакетов клиенту).
Параметры `dx, dy, dz` задают разброс (spread), а параметр `speed` — скорость разлета (если speed > 0, то dx, dy, dz работают как направление).

### Базовые фигуры (Единоразвый спавн)

- **`particleSpawn(type, x, y, z, count, dx, dy, dz, speed)`**  
  Спавн `count` частиц типа `type` (например, `"minecraft:flame"`) в точке.
- **`particleLine(type, x1, y1, z1, x2, y2, z2, count, dx, dy, dz, speed)`**  
  Спавн линии из частиц от `(x1, y1, z1)` до `(x2, y2, z2)`.
- **`particleCircle(type, cx, cy, cz, radius, count, dx, dy, dz, speed)`**  
  Спавн круга частиц вокруг `(cx, cy, cz)` радиусом `radius`.
- **`particleSpiral(type, cx, cy, cz, radius, height, count, dx, dy, dz, speed)`**  
  Спавн спирали частиц.

*Совет:* Чтобы сделать эти фигуры постоянными (пока скрипт не остановит), используйте цикл:
```text
async "magic_circle" {
    while (true) {
        particleCircle("minecraft:soul", x, y, z, 2.0, 20, 0, 0, 0, 0)
        await time(0.05)
    }
}
// Для остановки:
stop_task("magic_circle")
```

### Привязка к костям NPC (Непрерывный спавн)

- **`particleStartBone(task_id, npc, bone_name, type, count, dx, dy, dz, speed)`**  
  Запускает **непрерывный** спавн частиц на клиенте из конкретной кости NPC (например, `"right_eye"`). Возвращает заданный вами `task_id` (строка). Эффект будет длиться бесконечно, пока вы не остановите его.
- **`particleStopBone(task_id)`**  
  Останавливает непрерывный эффект частиц на кости по его `task_id`.

---

## Разрешение сущностей по строке

В методах и `await` часто можно передать:

- UUID / ник игрока / id NPC из `NpcManager`
- `"player"` — ближайший игрок к источнику скрипта
- `"npc"` — ближайший Spraute NPC
- `"mob"` — ближайший моб (не игрок, не Spraute NPC)
- `"any"` — ближайшее живое существо

---

## Переменные и область видимости

- **Локальные** — в рамках одного «потока» выполнения скрипта (создаются через `val` или `var`).
- **`global`** — общие переменные для всего скрипта.
- **`world`** — общие для всего сейва (overworld + nether + end); сериализуются в `ScriptWorldData` и переживают перезапуск сервера.
- **`player.data`** — уникальный словарь для каждого игрока, хранящийся **только до перезапуска сервера** (сессионные данные).
- **`player.savedData`** — уникальный словарь для каждого игрока, который **навсегда сохраняется в мире** (например, статистика, квесты).
- **`player.onGround`** — стоит ли игрок на земле.
- **`player.isSneaking`** — зажат ли Shift (приседание / медленное копание). Алиасы: `isCrouching` — реальная поза приседа.
- **`player.isSprinting`** — бежит ли игрок. Можно писать: `player.isSprinting = false` (сбросить спринт).
- **`player.countItem(item_id)`** / **`player.hasItem(item_id)`** — методы игрока (работают в `fun`, условиях и выражениях).

### `breakBlockAt(x, y, z, [player])`
Сломать блок с дропом. Если передан `player`, дроп учитывает инструмент и зачарования.



Пример работы с переменными игрока:
```text
val p = getPlayer("Steve")

# Сохраняем счетчик смертей (навсегда)
if (p.savedData["deaths"] == null) {
    p.savedData["deaths"] = 0
}
p.savedData["deaths"] = p.savedData["deaths"] + 1

# Сохраняем временный статус на сессию
p.data["is_in_dialogue"] = true
```

Идентификатор NPC из `NpcManager` без `val` может резолвиться как **строка-id** при обращении к свойствам.

---

## Списки и словари (Индексация)

Теперь в Spraute Engine можно обращаться к элементам списков и словарей через квадратные скобки `[]`:

```text
val my_list = [1, 2, 3]
my_list[0] = 10         # Изменить первый элемент
val x = my_list[0]      # Получить первый элемент

val my_dict = getHeldItem(player)
val name = my_dict["id"]
```

*Примечание:* можно использовать цепочки индексов, например `matrix[0][1]`.

---

## Прочее

- Одиночные вызовы без присваивания: `foo()`, `npc.moveTo(...)`.
- Присваивание: `x = 1`.
- Комментарии: строка от **`#`** до конца строки (`ScriptLexer`).

---

*Файл сгенерирован по состоянию исходников проекта Spraute Engine.*


пример кода для:


create npc readix_avatar {
    name = "ReadixGG"
    model = "geo/defolt.geo.json"
    texture = "textures/entity/defolt.png"
   # animation = "animations/npc_classic.animation.json"
    pos = 8, -60, 7
    show_name = false
    speed = 0.2
}

create npc knight {
    name = "Верный Рыцарь"
    model = "geo/defolt.geo.json"
    texture = "textures/entity/knight.png"
    #animation = "animations/npc_classic.animation.json"
    pos = 8, -60, 9
    show_name = false
    speed = 0.2
}

async "readix_idle" {
    readix_avatar.alwaysLookAt(knight)
}

async "knight_idle" {
    knight.alwaysLookAt(readix_avatar)
}

# Тесты анимаций: hello, sad, hint, question, happy, laughter + stop(), setAdditiveWeight
readix_avatar.setAdditiveWeight(1)  # процедурное дыхание и лёгкое дрожание рук поверх анимаций
knight.setAdditiveWeight(1)

val player = getNearestPlayer(readix_avatar)
chat("§7[!] Подойди к ReadixGG и нажми ПКМ, чтобы начать разговор.")
val interactor = await interact(readix_avatar)

readix_avatar.alwaysLookAt(interactor)
knight.alwaysLookAt(interactor)
readix_avatar.playOnce("hello")
say(readix_avatar, "Привет, " + interactor.name + "! Нажми §e[F]§f, чтобы слушать дальше.")
await keybind("f")

# Диалог с выбором: create ui { ... }
create ui dialog_panel {
    size = [300, 128]
    background = "#F0181828"
    text("q_text", "Нужна твоя помощь с железом. Что скажешь?") {
        pos = [12, 14]
        color = "#EAEAEA"
        scale = 1.0
        layer = 0
    }
    button("ans_help", "Помогу, сколько нужно") {
        pos = [12, 48]
        size = [158, 22]
        color = "#55336688"
        hover = "#66447799"
        layer = 1
    }
    button("ans_later", "Позже") {
        pos = [12, 78]
        size = [158, 22]
        color = "#55336688"
        hover = "#66447799"
        layer = 1
    }
    entity("npc:readix_avatar") {
        id = "portrait"
        # pos/size — ячейка; crop [l,t,r,b] — сколько срезать с краёв; без anchor — feet_crop
        pos = [188, 30]
        size = [96, 98]
        scale = 1.05
        crop = [0, 0.0, 0.0, 0.4]
        anchor_y = 2.1
        layer = 0
    }
}
uiOpen(interactor, dialog_panel)
val dialog_pick = await uiClick(interactor)
uiClose(interactor)
if (dialog_pick == "ans_help") {
    readix_avatar.playOnce("happy")
    say(readix_avatar, "Тогда по рукам — жду слитки!")
} else {
    readix_avatar.playOnce("sad")
    say(readix_avatar, "Ладно, зайди, когда будет время.")
}
await keybind("f")


knight.alwaysLookAt(readix_avatar)
knight.playLoop("sad")  # грустная поза, когда говорит о проблеме
say(knight, "Readix, у нас в кузнице закончилось железо. Без него мы не доделаем твой новый движок.")
await keybind("f")


readix_avatar.alwaysLookAt(interactor)
knight.alwaysLookAt(interactor)
knight.stop("sad")  # снять грусть
readix_avatar.playLoop("hint")  # поза «подсказки» при просьбе о помощи

say(readix_avatar, interactor.name + ", выручишь нас? Нужно 30 слитков железа. Кидай их мне (Q), я подберу.")
await keybind("f")
readix_avatar.stop("hint")

say(knight, "Мы будем ждать тебя у тех скал. Поторопись!")
await keybind("f")


async "readix_run" {
    readix_avatar.moveTo(5, -60, 15)
}

async "knight_run" {
    knight.moveTo(7, -60, 17)
}

await task("readix_run")
await task("knight_run")

readix_avatar.stopLookAt()
knight.stopLookAt()

readix_avatar.alwaysLookAt(knight)
knight.alwaysLookAt(readix_avatar)



# --- 4. Ожидание сдачи ресурсов (подход, кидок, NPC подбирает) ---
# need — цель; iron_quest_active — только пока идёт фаза «ждём 30 слитков» (сообщения и подбор железа включаются с await pickup)
val need = 30
val iron_quest_active = false

on pickup(readix_avatar, "minecraft:iron_ingot") -> iron_progress {
    val have = readix_avatar.countItem("minecraft:iron_ingot")
    val left = need - have
    if (iron_quest_active && left > 0) say(readix_avatar, "Осталось принести " + intStr(left) + ", ты принёс уже " + intStr(have) + "!")
}

val quest_done = false
while (!quest_done) {
    val checker = await interact(readix_avatar)

    readix_avatar.alwaysLookAt(checker)
    knight.alwaysLookAt(checker)

    readix_avatar.pickupOnlyFrom(checker)
    readix_avatar.stop("hint")
    readix_avatar.playLoop("question")
    say(readix_avatar, "Кидай железо сюда (Q)! Нужно 30.")

    iron_quest_active = true
    await pickup(readix_avatar, 30, "minecraft:iron_ingot")
    iron_quest_active = false
    readix_avatar.stop("question")
    readix_avatar.playOnce("happy")

    readix_avatar.pickupAny()
    stop(iron_progress)
    say(readix_avatar, "О, ты принёс всё вовремя! ")
    await keybind("f")
    quest_done = true
}

# --- 5. Финал (NPC смотрят друг на друга и разговаривают) ---


say(readix_avatar, "Работа кипит! Ты лучший помощник.")
await keybind("f")

