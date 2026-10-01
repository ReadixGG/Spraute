# Spraute Engine — гайд для AI-агентов

**Читай этот файл в начале каждого нового чата.**  
Цель: не тупить, не лезть куда не надо, менять код расширяемо и в правильном слое архитектуры.

Связанные документы (детали по теме):


| Документ                                                 | Когда открывать                                 |
| -------------------------------------------------------- | ----------------------------------------------- |
| `ROADMAP_1.7.md`                                         | Стратегия 1.7, миграция Studio → ImGui, форматы |
| `MULTIVERSION.md`                                        | Stonecutter, гарды `//? if`, compat-хелперы     |
| `metods.md`                                              | Язык `.spr`, API скриптов                       |
| `docs/visual_blocks.md`, `docs/visual_blocks_builder.md` | DSL визуальных блоков                           |


---

## 0. Жёсткие правила (не нарушать)

1. **Билд только по явной просьбе пользователя**
  Триггеры: «делай билд», «собери мод», «build», «скомпилируй jar».  
   После правок **не** запускай `gradlew build` сам. Можно сказать: «пересобери, когда скажешь».  
   `compileJava` — тоже только если пользователь просит проверить компиляцию или это критично и он согласен.
2. **Не лезь в Electron Studio (**`app/`**), кроме ProCode-блоков, и то сейчас все визуальное программирование так-же в моде**
  По умолчанию **не** открывай и **не** правь:
  - `app/src/`, Vite/Electron конфиги, HTML/CSS Studio
  - `app/node_modules/`, `app/dist/`, `app/dist-electron/`
  - чужие плагины в `app/bundled_plugins/` кроме `procode` (если задача не про них)
   **Разрешено без отдельной просьбы**, когда задача про визуальные блоки движка:
  - `app/bundled_plugins/procode/` (`blocks/*.spr`, `lang/en.json`, `blocks_order.json`, `plugin.json`, …)
3. **Основной код мода — только** `src/`
  Канонический исходник: `src/main/java` + `src/main/resources`.  
   Не править руками `versions/*/build/`, `versions/*/run/`, root `build/`, `run/`, `bin/`.
4. **Делай расширяемо и по существующим точкам расширения**
  Не хардкодь одноразовые ветки «для этого скрипта».  
   Новые фичи — через registry / тип ноды / пакет / `ScriptFunction` / блок `.spr` + `template:`.  
   См. §6.
5. **Обе версии Minecraft**
  Правки MC API должны жить на **1.19.2 и 1.20.1**.  
   Предпочитай `org.zonarstudio.spraute_engine.compat.*`, а не россыпь `//? if` по всему коду.
6. **Язык общения**
  С пользователем — **русский** (если он не пишет на английском).  
   Идентификаторы кода — английские. UI-строки редактора часто RU-source + перевод через i18n.
7. **Коммиты / PR / push** — только если пользователь явно попросил.
8. **Не раздувай дифф**
  Не рефакторь соседний код «заодно». Не пиши markdown-доки, если не просили (этот файл — исключение по запросу).
9. САМОЕ ВАЖНОЕ! НОДЫ И CREATE ЧЕРЕЗ КОД ДОЛЖНЫ ОДИНАКОВЫМИ БЫТЬ ДЛЯ КОДА, ПОЛНАЯ ЗАВИСИМОСТЬ КАК СЕЙЧАС У NPC, НОДА=КОД=НОДА=КОД

---



## 1. Что это за проект

**Spraute Engine** — Minecraft Forge мод (`spraute_engine`, версия продукта **1.7**) для сюжетных/RPG проектов:

- скрипты `.spr`
- сцены (иерархия нод, префабы)
- визуальный ProCode (блоки → `.spr`)
- in-game редактор на **Dear ImGui** (клавиша N / `/spraute engine`)
- NPC, камера/катсцены, UI, квесты, экономика, частицы и т.д.


|                          |                                                                                |
| ------------------------ | ------------------------------------------------------------------------------ |
| Группа                   | `org.zonarstudio`                                                              |
| Версии MC                | **1.19.2** + **1.20.1** через **Stonecutter**                                  |
| Java                     | 17                                                                             |
| Точка входа              | `src/main/java/org/zonarstudio/spraute_engine/Spraute_engine.java`             |
| Рантайм-воркспейс игрока | `<gameDir>/spraute_engine/` (`scripts/`, `scenes/`, `plugins/`, `prefabs/`, …) |


**Стратегия 1.7:** Electron **Spraute Studio** (`app/`) уходит в архив; рабочий продукт — **один JAR** с ImGui-редактором.  
`app/bundled_plugins/procode` пока остаётся источником определений блоков, которые Gradle кладёт в jar.

---



## 2. Карта репозитория — куда смотреть

```
Spraute_Engine/
├── src/main/java/.../spraute_engine/   ← ОСНОВНОЙ КОД МОДА
├── src/main/resources/                 ← assets, lang, mixins
├── app/
│   ├── bundled_plugins/procode/        ← блоки визуального редактора (можно трогать)
│   └── ...                             ← legacy Studio — НЕ ТРОГАТЬ без просьбы
├── versions/1.19.2/  versions/1.20.1/  ← Stonecutter-проекты, build/libs/*.jar
├── docs/                               ← пользовательские доки по блокам
├── tools/                              ← смоук/миграции
├── ROADMAP_1.7.md
├── MULTIVERSION.md
├── metods.md
├── build.gradle / stonecutter.gradle / settings.gradle
└── AGENTS.md                           ← этот файл
```



### Не править «просто так»

- `versions/*/build/**`, `versions/*/run/**`
- `app/node_modules`, `app/dist*`
- строку active в `stonecutter.gradle` руками (только через `gradlew "Set … active"`)
- чужие игровые копии (`d:\pc_main\...`) — только если пользователь дал путь и просит синхронизировать



### Куда класть готовый мод

После билда (когда пользователь попросил):

```
versions/1.19.2/build/libs/spraute_engine-1.7-1.19.2.jar
versions/1.20.1/build/libs/spraute_engine-1.7-1.20.1.jar
```

Обычная команда:

```bash
./gradlew build --console=plain
# или точечно:
./gradlew :1.19.2:build :1.20.1:build -x test
```

---



## 3. Архитектура пакетов (куда класть изменения)

База: `src/main/java/org/zonarstudio/spraute_engine/`


| Пакет                                                 | Ответственность                                                                     |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `script/`                                             | Лексер → парсер → компилятор → `ScriptExecutor`, `ScriptManager`                    |
| `script/function/`                                    | Встроенные функции `.spr` (`FunctionRegistry`)                                      |
| `scene/`                                              | `SceneRuntime`, `SceneDocument`, `SceneNode`, префабы, hitbox/light/particle/canvas |
| `scene/custom/`                                       | Схема-driven CUSTOM-ноды (`declare node type`)                                      |
| `scene/resource/`                                     | RES_* (block/item/craft/tab/drop/world/**particle**)                                |
| `network/`                                            | `ModNetwork` + пакеты (`encode`/`decode`/`handle`)                                  |
| `client/editor/`                                      | In-game редактор (экраны, state, i18n)                                              |
| `client/editor/scene/`                                | Hierarchy, Inspector, preview, timeline, prefabs                                    |
| `client/editor/visual/`                               | Visual blocks: registry, DSL parser, codegen                                        |
| `client/imgui/`                                       | Backend ImGui                                                                       |
| `client/`                                             | Камера, оверлеи, частицы на клиенте                                                 |
| `entity/`                                             | NPC / model / billboard / projectile + рендер                                       |
| `compat/`                                             | Кросс-версия 1.19.2 ↔ 1.20.1                                                        |
| `registry/`, `ui/`, `command/`, `mixin/`, `resource/` | Подсистемы                                                                          |




### Сцены (mental model)

```
.scene.json  →  SceneDocument (дерево SceneNode)
       ↓  /spraute run / load
  SceneRuntime (сервер)  → спавн сущностей / runtime-объектов
       ↓
  SceneActiveState: nodeId ↔ entity / particle / hitbox / light
       ↓
  SCRIPT / VISUAL_SCRIPT → ScriptExecutor (_scene, _node, …)
       ↓
  prefabSpawn / create* → объекты тегаются к сцене → чистятся на stop
```

- Редакторский preview на клиенте ≠ полноценный серверный spawn.
- Живые правки сцены: `SceneLiveSync`, сетевые scene-action пакеты.
- Префабы: `PrefabRepository` + файлы в `spraute_engine/prefabs/`.



### Скрипты

```
.spr текст → ScriptLexer → ScriptParser → ScriptCompiler → CompiledScript → ScriptExecutor
```

Визуальный граф (`.sprv` / блоки) **генерирует** `.spr` через `template:` блока.  
Рантайм исполняет уже `.spr`, не «граф как есть».

### Сеть и катсцены

- Пакеты регистрируются **последовательно** в `ModNetwork` (`id++`). Не ломай порядок без нужды; protocol version bump — осторожно.
- Скриптовая камера уезжает от тела игрока. Для VFX/пакетов учитывай cinematic view (`CinematicViewTracker`), а не только `PacketDistributor.NEAR` вокруг тела.



### Частицы

- Нода сцены `PARTICLE` + `SceneParticleRuntime` / `SceneParticleEmitter`
- Тип ресурса `RES_PARTICLE` / `create particle`
- Пакеты: `ControlledParticleBurstPacket`, `BoneParticlePacket`
- `emit()` / блок «запустить частицы» — явный one-shot; loop — через tick runtime

---



## 4. `app/` — что можно и что нельзя


| Путь                                 | Политика                                                               |
| ------------------------------------ | ---------------------------------------------------------------------- |
| `app/bundled_plugins/procode/**`     | OK при задачах про блоки/палитру/codegen                               |
| Остальной `app/**` (Electron Studio) | **Не трогать**, пока пользователь явно не попросил                     |
| Другие `app/bundled_plugins/<name>`  | Только если задача про этот плагин; в jar по умолчанию **не** попадают |


Gradle копирует в jar **только** плагины из `bundledPluginNames` (сейчас `['procode']`) задачей `prepareBundledPlugins`.  
При старте игры блоки распаковываются в `<gameDir>/spraute_engine/plugins/`.

Если правишь только `.spr` блоки — пользователю всё равно нужен **билд jar** (когда он скажет), иначе в игре может остаться старая копия из jar.

---



## 5. Мультиверсия (Stonecutter)

- Один исходник в `src/main/java`.
- Гарды:

```java
//? if >=1.20.1 {
// активная ветка
//?} else {
/*старая ветка
*///?}
```

- Активная версия задаётся через Gradle, не руками в файле с маркером `[SC] DO NOT EDIT`.
- Частые различия — уже в `compat/` (`SprauteEntityCompat`, `SprauteRenderCompat`, GUI draw и т.д.).
- Подробности: `MULTIVERSION.md`.

---



## 6. Как правильно расширять (чеклисты)



### 6.1 Новая функция скрипта `.spr`

1. Класс `implements ScriptFunction` в `script/function/`.
2. Регистрация в `FunctionRegistry` (`register` / `registerAlias`).
3. При необходимости — подсказки в `ScriptEditorHints`, строка в `metods.md` (если просят док).
4. Не пихай логику в гигантский `ScriptExecutor`, если это обычная функция API.



### 6.2 Новый визуальный блок ProCode

1. `app/bundled_plugins/procode/blocks/<id>.spr` — `block:`, `category:`, `shape:`, UI, `template:` (валидный `.spr`).
2. Порядок в `blocks_order.json` (категория палитры).
3. RU→EN ключи в `lang/en.json`, если строки на русском.
4. Если API нет — сначала §6.1, потом блок.
5. Предпочитай `template:` вместо legacy `#\ code:` JS (Rhino капризный: без `??` и современного JS).



### 6.3 Новый тип ноды сцены

1. `SceneNodeType` (+ allowed children / flags).
2. Поля в `SceneNode` + сериализация `SceneDocument`.
3. Spawn/unload/tick в `SceneRuntime` / dedicated `*Runtime`.
4. UI: Hierarchy menu + Inspector.
5. Для data-only типов — лучше `CUSTOM` + `declare node type` (`scene/custom/`), без нового enum, если хватает схемы.



### 6.4 Новый сетевой пакет

1. Класс в `network/` с `encode` / `decode` / `handle`.
2. Регистрация в `ModNetwork.register()`.
3. Явные стороны client/server; не слать лишнее на всех игроков.



### 6.5 Клиентский редактор / ImGui

- UI редактора: `client/editor/**` + `client/imgui/**`.
- Не тащи Electron-паттерны из `app/DESIGN.md` слепо — портируй смысл в ImGui.
- Падения ImGui native (диск/DLL) не должны валить весь клиент — обрабатывай ошибки загрузки.

---



## 7. Рабочий процесс агента в новом чате

1. Прочитай **этот файл** (+ релевантный кусок `ROADMAP_1.7.md` / `MULTIVERSION.md` по задаче).
2. Уточни слой: runtime / editor / visual block / network / scene.
3. Ищи существующие аналоги (`FunctionRegistry`, соседние блоки `.spr`, похожие ноды) — копируй паттерн.
4. Меняй минимальный набор файлов.
5. Не билди, пока пользователь не скажет.
6. Отвечай коротко по делу на русском; длинные выкладки — только если просят.



### Типичные ошибки новых агентов


| Ошибка                                                           | Как правильно                                              |
| ---------------------------------------------------------------- | ---------------------------------------------------------- |
| Копаться во всём `app/`                                          | Только `app/bundled_plugins/procode` или явная просьба     |
| Билдить после каждого фикса                                      | Ждать «делай билд»                                         |
| Дублировать логику в `ScriptExecutor`                            | `ScriptFunction` + registry                                |
| Ломать только 1.20.1                                             | compat + обе версии                                        |
| `PacketDistributor.NEAR` в катсцене                              | Учитывать cinematic / слать целевому игроку                |
| Править `versions/*/run` или игровой `plugins` вместо исходников | Править `src/` / `app/bundled_plugins/procode`, потом билд |
| Одноразовый хардкод под один сюжет                               | Расширяемая точка (нода, функция, блок, пакет)             |


---



## 8. Контекст окружения пользователя (часто встречается)

- Игровой инстанс часто: `d:\pc_main\` (логи: `d:\pc_main\logs\latest.log`).
- Воркспейс данных: `d:\pc_main\spraute_engine\`.
- Плагины в игре могут перезаписываться из jar при старте — правка только runtime-копии без билда откатится.
- Пользователь может дать лог после фикса — читай его, чини по фактам, **билд — один раз когда скажет**.

---



## 9. Качество кода (ожидания)

- Совпадай со стилем соседних файлов (импорты, имена, логирование `[Spraute …]`).
- Не добавляй бесполезные комментарии/Javadoc «что делает строка».
- Комментарии — только для неочевидных инвариантов (катсцена vs тело игрока, ownership light-блоков и т.п.).
- Асинхронность скриптов (`await`, `on`, `every`) — не блокируй серверный тред долгими циклами.
- Editor preview не должен ломать серверный runtime и наоборот.

---



## 10. Быстрый «куда идти» по симптомам


| Симптом / задача           | Куда смотреть                                                             |
| -------------------------- | ------------------------------------------------------------------------- |
| Скрипт не компилируется    | `script/` parser/compiler, `metods.md`                                    |
| Функции нет в `.spr`       | `script/function/`, `FunctionRegistry`                                    |
| Блока нет в палитре        | `app/bundled_plugins/procode/blocks`, `blocks_order.json`                 |
| Сцена/префаб/спавн         | `scene/SceneRuntime`, `PrefabRepository`                                  |
| Нода в иерархии/инспекторе | `client/editor/scene/HierarchyPanel`, `InspectorPanel`                    |
| Камера/катсцена            | `script/CameraScriptUtil`, `client/CameraHandler`, `CinematicViewTracker` |
| Частицы                    | `SceneParticleEmitter`, `ClientParticleManager`, particle packets         |
| UI игроку                  | `ui/`, UI packets, script `uiOpen`                                        |
| Краш ImGui/редактора       | `client/imgui/ImGuiNativeLoader`, `SprauteImGuiBackend`                   |
| Различие 1.19 vs 1.20      | `compat/`, `MULTIVERSION.md`                                              |


---

**Итог:** работай в `src/` (+ `procode` блоки), расширяй через registry/типы/пакеты, обе MC-версии, с пользователем по-русски, **билд — только когда скажут.**