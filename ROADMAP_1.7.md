# Spraute Engine 1.7 — План разработки

**Документ:** мастер-план релиза 1.7  
**Версия документа:** 1.3  
**Дата:** 2026-07-18  
**Платформы:** Minecraft Forge 1.19.2 + 1.20.1 (Stonecutter)  
**Целевая версия мода:** `1.7` (полный релиз, без MVP-отсечения)  
**Предыдущий релиз:** `1.6` / `1.6.0` (Studio)

---

## Содержание

1. [Резюме и стратегический поворот](#1-резюме-и-стратегический-поворот)
2. [Принятые архитектурные решения](#2-принятые-архитектурные-решения)
3. [Цели релиза 1.7](#3-цели-релиза-17)
4. [Инвентаризация: что есть сейчас](#4-инвентаризация-что-есть-сейчас)
5. [Матрица миграции Studio → In-Game Editor](#5-матрица-миграции-studio--in-game-editor)
6. [Архитектура 1.7](#6-архитектура-17)
7. [Технологический стек](#7-технологический-стек)
8. [Парсер плагинов ProCode](#8-парсер-плагинов-procode)
9. [Node Graph: логика + GUI-ноды](#9-node-graph-логика--gui-ноды)
10. [Дизайн-система (Kinetic Laboratory в ImGui)](#10-дизайн-система-kinetic-laboratory-в-imgui)
11. [Фазы разработки](#11-фазы-разработки)
12. [Форматы данных](#12-форматы-данных)
13. [API скриптов и обратная совместимость](#13-api-скриптов-и-обратная-совместимость)
14. [Структура файлов и пакетов](#14-структура-файлов-и-пакетов)
15. [Архивация Spraute Studio](#15-архивация-spraute-studio)
16. [CI/CD и релиз](#16-cicd-и-релиз)
17. [Тестирование](#17-тестирование)
18. [Риски и митигация](#18-риски-и-митигация)
19. [Дорожная карта по времени](#19-дорожная-карта-по-времени)

---

## 1. Резюме и стратегический поворот

### 1.1 Главное изменение

**Spraute Studio (Electron, `app/`) прекращает существование как отдельный продукт.**  
Весь функционал переносится **внутрь Minecraft** — **Spraute Engine Editor** (клавиша **N**, `/spraute engine`).

| Было (до 1.7) | Станет (1.7) |
|---------------|--------------|
| Мод + отдельное приложение Studio | **Только мод** |
| HTML/CSS + CodeMirror + Blockly | **Dear ImGui** (imgui-java) |
| Blockly рисует блоки | **Свой Node Graph** на ImGui — рисуем только визуал узлов |
| JS-генератор в `#\ code:` строках блоков | **Java CodeEmitter** — переписываем генерацию |
| Блоки в основном как плагины `.spr` | **Парсер плагинов** читает `blocks/*.spr` + `plugin.json` |
| GUI-канвас в Studio | **GUI-ноды** в том же Node Graph (`ui_panel`, `ui_button`, …) |
| `autorun` | **Scenes** (рекомендуемый путь; `autorun` не ломаем) |
| Ванильный `GuiGraphics` для игрового UI | **ImGui backend** (+ vanilla fallback) |
| Папка `app/` в main | **`archive/studio-1.6/`** — только для истории |

### 1.2 Зачем

- Один продукт — один JAR.
- Редактирование в контексте мира.
- Единый UI-стек (ImGui) для редактора и игрового интерфейса.
- Плагины ProCode остаются расширяемыми без Blockly и без Electron.

### 1.3 Что НЕ меняется

- Язык **`.spr`**, компилятор, executor, события.
- Папка **`spraute_engine/`** в корне инстанса.
- Stonecutter: **1.19.2 + 1.20.1**.
- Пакеты `uiOpen`, `overlayOpen`, патчи виджетов.

---

## 2. Принятые архитектурные решения

> Зафиксировано по итогам обсуждения 2026-07-18.

| # | Тема | Решение |
|---|------|---------|
| D1 | Визуальные блоки | **Свой Node Graph на ImGui.** Blockly и MCEF не используем. |
| D2 | Определения блоков | **Плагины** (`blocks/*.spr` + metadata). Пишем **парсер плагинов**; codegen переписываем на Java. |
| D3 | GUI в редакторе | **GUI-ноды** в Node Graph (не отдельный drag-canvas как в Studio). Встроены в движок, не в плагины. |
| D4 | Scope релиза | **Полный перенос Studio → 1.7.** Один релиз `v1.7`, без отсечения в 1.7.1. |
| D5 | Доступ к редактору | **Только у того, кто редактирует** — локальный клиент, локальный `gameDir/spraute_engine/`. Без сетевой синхронизации файлов. |
| D6 | Папка `app/` | **Перенести в `archive/studio-1.6/`** для истории; убрать из CI и README как актуальный продукт. |
| D7 | Текстовый редактор | Custom ImGui + tokenizer `.spr` (без Chromium). |
| D8 | Экспорт карты | Входит в полный релиз (порт `export-map.js`). |
| D9 | Legacy `#\ code:` (JS) | **Автомиграция** на `template:` + Java codegen. JS в runtime не выполняем. |
| D10 | Префикс `#\` в блоках | **Убирается при миграции.** Целевой формат — plain-директивы `block:`, `row:`, … |

---

## 3. Цели релиза 1.7

**Всё ниже обязательно для релиза `v1.7`.** Нет отдельного MVP.

### 3.1 Движок и UI

- [ ] ImGui: рендер, ввод, тема Kinetic Laboratory
- [ ] Игровой UI (`uiOpen`, `overlayOpen`, notifications, chat-HUD) на ImGui
- [ ] Vanilla fallback через конфиг

### 3.2 In-Game Editor

- [ ] Открытие: **N**, `/spraute engine`
- [ ] Docking: Project | Code | Node Graph | Hierarchy | Inspector | Console
- [ ] Файловый менеджер `spraute_engine/**`
- [ ] Текстовый редактор `.spr` + hot reload
- [ ] **Node Graph** — логические блоки из плагинов
- [ ] **GUI-ноды** — сборка `create ui` через граф
- [ ] Live preview GUI (`uiOpen` на локального игрока)
- [ ] Scenes + Hierarchy
- [ ] Inspector объектов сцены + gizmo-маркеры
- [ ] Console с фильтром `[Spraute]`

### 3.3 Плагины и экспорт

- [ ] Парсер ProCode-плагинов (bundled + пользовательские)
- [ ] **Автомиграция** legacy → `template:` + снятие `#\` (§8.7)
- [ ] Импорт/экспорт плагинов (zip)
- [ ] Экспорт карты (zip `spraute_engine/` + опционально mods)
- [ ] Документация блоков в редакторе

### 3.4 Закрытие 1.6

- [ ] notifications, block_props, armor + ProCode + docs

### 3.5 Definition of Done

- [ ] Пользователь **никогда не открывает Studio** — весь workflow в игре
- [ ] Все bundled ProCode-блоки работают в Node Graph
- [ ] GUI собирается нодами и генерирует рабочий `create ui`
- [ ] `archive/studio-1.6/` в репо; CI только mod JAR
- [ ] GitHub Release `v1.7`: JAR 1.19.2 + 1.20.1

---

## 4. Инвентаризация: что есть сейчас

### 4.1 Движок (мод)

| Компонент | Класс / путь | Назначение |
|-----------|--------------|------------|
| Скрипты | `ScriptManager` | `gameDir/spraute_engine/scripts/` |
| Игровой UI | `SprauteScriptScreen` | JSON-виджеты, ~3600 строк |
| UI API | `UiFunctions`, packets | `uiOpen`, slots, input |
| Ассеты | `ExternalAssetPack` | geo, textures, animations |

### 4.2 Studio (`app/` → архив)

| Модуль | Что переносим | Куда |
|--------|---------------|------|
| `bundled_plugins/procode/blocks/*.spr` | Метаданные блоков | Парсер + `editor_plugins/` |
| `visual.js` Blockly | **Не переносим** | Заменяем Node Graph |
| `gui-editor.js` | Логика виджетов | GUI-ноды + `GuiCodegen` |
| `main.js` snippets | Список автодополнения | `SprEditorCompletionProvider` |
| `export-map.js` | Логика zip | `MapExporter` |
| `DESIGN.md` | Палитра | `SprauteImGuiTheme` |
| `electron-main.cjs` plugin IPC | Импорт zip | `PluginImporter` |

### 4.3 Незакоммиченные хвосты 1.6

- notification, block_props, armor — закрыть в Фазе 0.

---

## 5. Матрица миграции Studio → In-Game Editor

| # | Studio | Модуль 1.7 | Фаза |
|---|--------|------------|------|
| 1 | Workspace picker | Авто: `gameDir/spraute_engine/` | — |
| 2 | File tree | `ProjectPanel` | 3 |
| 3 | CodeMirror | `SprEditorPanel` | 4 |
| 4 | Blockly workspace | `NodeGraphPanel` | 6–7 |
| 5 | GUI canvas | **GUI-ноды** в `NodeGraphPanel` | 7 |
| 6 | Plugin zip import/export | `PluginImporter` / `Exporter` | 8 |
| 7 | Export map | `MapExporter` | 8 |
| 8 | Asset dropdowns | `AssetIndexService` | 4, 7 |
| 9 | Block docs | `BlockDocsPanel` | 7 |
| 10 | — | `HierarchyPanel` (Scenes) | 5 |
| 11 | — | `InspectorPanel` | 9 |
| 12 | Theme | `SprauteImGuiTheme` | 1 |

---

## 6. Архитектура 1.7

### 6.1 Схема

```
┌──────────────────────────────────────────────────────────────────┐
│                   Minecraft Client (Forge)                        │
├──────────────────────────────────────────────────────────────────┤
│  Spraute Engine Editor (overlay, key N)                          │
│  ├─ ProjectPanel      — файлы spraute_engine/                    │
│  ├─ SprEditorPanel    — текст .spr (вкладка «Код»)               │
│  ├─ NodeGraphPanel    — логика (плагины) + GUI-ноды (движок)    │
│  ├─ HierarchyPanel    — Scenes                                   │
│  ├─ InspectorPanel    — свойства ноды / объекта сцены            │
│  └─ ConsolePanel      — логи                                     │
├──────────────────────────────────────────────────────────────────┤
│  Editor Services                                                  │
│  ├─ EditorFileService      — локальный FS (только gameDir)       │
│  ├─ PluginRegistry         — парсер blocks/*.spr                 │
│  ├─ NodeGraphSerializer    — .graph.json ↔ .spr                  │
│  ├─ GuiNodeCodegen         — GUI-ноды → create ui { }            │
│  ├─ ScriptHotReloadService                                       │
│  └─ AssetIndexService      — models, textures, anims, npc ids    │
├──────────────────────────────────────────────────────────────────┤
│  Spraute ImGui Layer                                             │
│  ├─ SprauteImGuiBackend / Input / Theme / Textures               │
├──────────────────────────────────────────────────────────────────┤
│  Game UI Runtime                                                 │
│  ├─ SprauteScriptScreen (state)                                  │
│  └─ SprauteImGuiRenderer                                         │
├──────────────────────────────────────────────────────────────────┤
│  Server / Integrated Server                                      │
│  ├─ ScriptManager, SceneRegistry                                 │
│  └─ spraute_engine/scripts/, scenes/                             │
└──────────────────────────────────────────────────────────────────┘
```

### 6.2 Редактор — только локально (D5)

- Редактор работает **только на клиенте**, который его открыл.
- Файлы читаются/пишутся в **`Minecraft.getInstance().gameDirectory/spraute_engine/`**.
- На **dedicated server** без локального клиента редактора нет — скрипты правят на машине сервера отдельно или через integrated server.
- Другие игроки в MP **не видят** редактор и не получают изменения файлов по сети.
- Скрипты на сервере подхватываются после reload/restart сервера (как сейчас).

### 6.3 Режимы вкладок файла

Один `.spr` или `.graph.json` открывается в двух представлениях:

| Вкладка | Содержимое |
|---------|------------|
| **Код** | Текст `.spr` |
| **Граф** | Node Graph (если есть `.graph.json` или файл создан визуально) |

Синхронизация: сохранение графа → генерирует/обновляет `.spr`. Ручное редактирование кода может инвалидировать граф (предупреждение пользователю).

### 6.4 Поток: Node Graph → .spr

```
1. Пользователь собирает граф в NodeGraphPanel
2. PluginRegistry даёт BlockDefinition для каждого типа ноды
3. GraphCodegen обходит граф (topological sort по exec-пинам)
4. Для каждой ноды: BlockDefinition.emitCode(node, context) → строки .spr
5. GuiNodeCodegen обрабатывает поддерево ui_* нод → блок create ui { }
6. Результат записывается в scripts/<name>.spr
7. ScriptHotReloadService.reload()
```

---

## 7. Технологический стек

| Слой | Технология |
|------|------------|
| UI редактора + игровой UI | Dear ImGui via [imgui-java](https://github.com/SpaiR/imgui-java) |
| Node Graph | ImGui node editor (custom или imnodes-style на ImDrawList) |
| Текстовый редактор | Custom tokenizer + ImGui multiline / color ranges |
| Плагины блоков | Парсер директив в `blocks/*.spr` + Java codegen |
| GUI | Встроенные ноды в движке (не плагины) |
| Файлы | `java.nio.file` — локальный `spraute_engine/` |
| Scenes | JSON `.scene` |
| Граф | JSON `.graph.json` |

**Не используем:** Electron, Blockly, MCEF, Chromium, CodeMirror в runtime.

---

## 8. Парсер плагинов ProCode

### 8.1 Структура плагина

```
spraute_engine/plugins/<plugin_id>/
  plugin.json          # опционально для сторонних; bundled — может быть implicit
  categories.json      # порядок и цвета категорий
  blocks_order.json    # порядок блоков в палитре
  blocks/
    npc_create.spr     # определение блока (metadata + codegen template)
    on_join.spr
    ...
  scripts/             # опционально: .spr библиотеки плагина
```

**Bundled** (в JAR): `src/main/resources/editor_plugins/procode/` — та же структура.  
При первом запуске редактора копируется в `spraute_engine/plugins/procode/` если нет.

### 8.2 Формат определения блока (`blocks/*.spr`)

Файлы в `blocks/` — **только метаданные ноды**, не исполняемый `.spr`.  
В 1.7 директивы пишутся **без префикса `#\`** (D10).

#### Целевой формат (1.7+)

```spr
block: npc_create
category: НПС
color: #22c55e
shape: statement

row: "создать НИП" (id: text: my_npc)
row: "имя" (name: text: НИП)
row: "HP" (hp: number: 20)
row: "модель" [model: dropdown_model]

template: npc_create
docs: |
  Создаёт НИП в мире с моделью и анимациями.
```

#### Legacy формат (Studio 1.6) — только для импорта

```spr
#\ block: npc_create
#\ category: НПС
#\ row: ...
#\ code: ...   # JS, удаляется при миграции
```

Парсер **`ProcodeBlockParser`** принимает **оба** варианта на чтение; мигратор переписывает в целевой.

#### Правила файла

- Строка-директива: `ключ: значение` или `row: ...` / `docs: |` (multiline)
- Пустые строки — разделители секций
- Обычный комментарий: `# текст` (один `#`, без `\`) — игнорируется парсером
- Строки `#\ ...` — legacy; после миграции в файле не остаются

#### Поля metadata

| Директива | Описание |
|-----------|----------|
| `block` | Уникальный id (`plugin_id/block_id` при коллизии) |
| `category` | Категория в палитре |
| `color` | Цвет заголовка ноды (#RRGGBB) |
| `shape` | `statement` \| `hat` \| `value` \| `event` |
| `row` | Строка UI ноды — поля ввода (см. §8.3) |
| `inputs` / `outputs` | Exec-пины: `exec`, `exec_if`, `value_bool`, … |
| `docs` | Markdown-подсказка для BlockDocsPanel |
| `template` | Id зарегистрированного Java-шаблона codegen (**целевой формат**) |
| `code` | **Legacy (Studio 1.6):** JS — удаляется при автомиграции (см. §8.7) |

### 8.3 Синтаксис `row` (порт из Studio)

```
row: "метка" (fieldId: type: default)
row: "метка" [fieldId: type]
row: "текст" (a: number: 0) "ещё" (b: text: hello)
```

**Типы полей:**

| type | UI в ноде | Источник значений |
|------|-----------|-------------------|
| `text` | InputText | статическое / пользователь |
| `number` | InputFloat | |
| `dropdown(a, b, c)` | Combo | статический список |
| `dropdown_model` | Combo | `AssetIndexService.models` |
| `dropdown_texture` | Combo | textures |
| `dropdown_anim` | Combo | animations |
| `dropdown_animfile` | Combo | animation files |
| `dropdown_npc` | Combo | npc ids из графа/проекта |
| `bool` | Checkbox | |

### 8.4 PluginRegistry (Java)

```java
class PluginRegistry {
    void loadAll(Path pluginsRoot);           // scan plugins/
    BlockDefinition get(String blockId);
    List<BlockDefinition> byCategory(String cat);
    List<String> categories();               // из categories.json
}

class BlockDefinition {
    String id, category, color, shape;
    List<RowSpec> rows;
    List<PinSpec> inputs, outputs;
    String templateId;                       // → CodeTemplateRegistry
    String docs;
}
```

### 8.5 Codegen — переписывание (не порт JS)

**Принцип:** каждый bundled-блок получает Java-класс `XxxBlockTemplate implements BlockCodeTemplate`.

```java
interface BlockCodeTemplate {
    void emit(NodeInstance node, CodeEmitter out);
}
```

Пример: `NpcCreateBlockTemplate` читает поля `id`, `name`, `hp`, … из `node.fields` и пишет:

```spr
create npc my_npc {
    name = "НИП"
    hp = 20
    ...
}
```

**Миграция блоков:** ~80+ файлов в `procode/blocks/`. См. §8.7 — автомиграция при загрузке.

### 8.6 plugin.json

```json
{
  "name": "WChoice",
  "id": "wchoice",
  "author": "ZonarStudio",
  "version": "1.0.0",
  "description": "Кинематографические диалоги",
  "enabled": true
}
```

`PluginRegistry` пропускает плагины с `"enabled": false`.

### 8.7 Автомиграция плагинов Studio 1.6 → 1.7 (D9, D10)

> **Решение:** `template:` + Java codegen; plain-директивы без `#\`.  
> Legacy `#\ code:` (JS) **не исполняется**. Миграция **автоматическая** при загрузке.

#### Когда срабатывает

`ProcodePluginMigrator` вызывается:

1. При **первом открытии редактора** (N) — скан `spraute_engine/plugins/**`
2. При **импорте zip** плагина (`PluginImporter`)
3. При **копировании bundled** из JAR в `spraute_engine/plugins/procode/`
4. Опционально: Gradle-task `migrateProcodePlugins` для предмиграции в репозитории

#### Алгоритм (на один файл `blocks/*.spr`)

```
1. Прочитать файл; определить legacy, если есть строки с префиксом "#\"
2. ProcodeBlockParser.parse(content)  // понимает оба формата
3. Если уже format 1.7 (нет "#\", есть template:) → пропуск
4. Иначе миграция:
   a. backup → blocks/<name>.spr.bak-1.6
   b. block id = X из директивы block:
   c. lookup TemplateRegistry.resolve(X)
   d. Если шаблон найден:
      - собрать новый текст файла:
        * каждая legacy-строка "#\ foo: bar" → "foo: bar"
        * "#\" или "#\ " (пустая) → пустая строка
        * удалить все строки code: (и "#\ code:")
        * добавить/заменить: template: X
      - записать файл
      - log: "[Spraute] Migrated block X (format 1.7, no #\\)"
   e. Если шаблон НЕ найден:
      - всё равно снять "#\" с директив (D10), если есть block id
      - WARNING: codegen недоступен до публикации template
```

#### Пример до / после

**До (1.6):**
```spr
#\ block: on_join
#\ category: События
#\ shape: hat
#\ code: return "on join() { ... }"
```

**После (1.7):**
```spr
block: on_join
category: События
shape: hat

template: on_join
```

#### TemplateRegistry

Каждый bundled-блок Studio 1.6 **обязан** иметь запись:

| `block` id | Java class | Источник |
|------------|------------|----------|
| `npc_create` | `NpcCreateBlockTemplate` | `procode/blocks/npc_create.spr` |
| `on_join` | `OnJoinBlockTemplate` | … |
| … | … | все ~80+ bundled |

Id шаблона **совпадает** с `block:` — мигратор не требует ручного маппинга.

Сторонние плагины с **уникальными** block id:

- Если автор использовал только metadata + стандартные поля `row` — достаточно опубликовать **Java-шаблон** в своём плагине (будущее API) или запросить включение в community pack.
- На релиз 1.7: все **официальные** плагины (procode, WChoice, Raya, NpcRelations) мигрируются полностью.

#### Предмиграция в репозитории (до релиза)

Чтобы пользователи получили уже чистые файлы:

```bash
# tools/migrate_procode_blocks.py (или Gradle task)
# Читает archive/studio-1.6/bundled_plugins/**/blocks/*.spr
# Пишет в src/main/resources/editor_plugins/:
#   - без префикса #\
#   - с template:, без code:
```

Ручная проверка diff — один раз. В игре мигратор остаётся для **старых копий** у пользователей.

#### Версионирование плагина

`plugin.json` после миграции:

```json
{
  "format": 2,
  "block_format": "plain",
  "migrated_from": "1.6",
  "migrated_at": "2026-..."
}
```

`format: 1` / `block_format: "legacy"` / строки с `#\` — триггер миграции.

#### Гарантии для пользователя

| Ситуация | Поведение |
|----------|-----------|
| Обновился с 1.6, старые `plugins/` на диске | Автомиграция: `#\` снят, `template:` добавлен, `.bak-1.6` |
| Импорт zip из Studio 1.6 | То же при импорте |
| Свежая установка 1.7 | Bundled уже plain + `template:` |
| Сломавшийся неизвестный блок | Warning, остальные блоки плагина работают |

#### Что НЕ делаем

- Не встраиваем Nashorn/GraalJS для исполнения legacy `#\ code:` в игре.
- Не оставляем dual-path codegen (JS + Java) в релизе 1.7.

---

## 9. Node Graph: логика + GUI-ноды

### 9.1 Общая модель графа

```json
{
  "format": 1,
  "script": "scripts/quest_main.spr",
  "nodes": [
    {
      "id": "n1",
      "type": "procode/on_join",
      "x": 120, "y": 80,
      "fields": { "player": "" }
    },
    {
      "id": "n2",
      "type": "engine/ui_panel",
      "x": 400, "y": 80,
      "fields": { "name": "dialog", "w": 300, "h": 200, "bg": "#C0101010" }
    }
  ],
  "edges": [
    { "from": "n1:exec_out", "to": "n2:exec_in" },
    { "from": "n2:child_out", "to": "n3:parent_in" }
  ]
}
```

### 9.2 Типы нод

| Префикс | Источник | Примеры |
|---------|----------|---------|
| `procode/` | Плагины | `on_join`, `npc_create`, `await_chat` |
| `engine/ui_*` | **Встроено в движок** | `ui_panel`, `ui_button`, `ui_text`, `ui_image`, `ui_input`, `ui_scroll`, `ui_entity`, `ui_slot` |
| `engine/event` | Встроено | `on_uiclick`, `on_uiinput` — привязка к id виджета |

**GUI-ноды не в плагинах** — иначе сложность координации preview/codegen. Плагины остаются для игровой логики.

### 9.3 Иерархия GUI-нод

```
ui_panel (create ui root)
  ├─ ui_text
  ├─ ui_button  → on_uiclick handler (exec pin)
  ├─ ui_image
  ├─ ui_scroll
  │    └─ ui_text (children через parent_in/child_out)
  └─ ui_entity
```

- Пин `parent_in` / `children_out` — вложенность (как children в JSON UI).
- Inspector справа редактирует поля выбранной ноды (как PROP_HINTS в gui-editor.js).

### 9.4 GuiNodeCodegen

Порт логики из `gui-model.js` → `guiModelToSprCode`:

```spr
create ui dialog {
    size = 300, 200
    background = "#C0101010"
    widgets = [
        { type: "text", x: 10, y: 10, text: "Привет", ... }
        ...
    ]
    on click "btn_ok" { ... }   # из связанной ноды on_uiclick
}
```

### 9.5 Live preview

- Кнопка **Preview** в NodeGraphPanel
- Собирает временный JSON из GUI-нод → `uiOpen(localPlayer, json)` без сохранения
- Обновление при изменении полей (debounce 300ms)

### 9.6 NodeGraphPanel (ImGui)

- Pan: middle mouse / Alt+drag
- Zoom: scroll
- Выделение, drag нод
- Контекстное меню: Add node → категории из PluginRegistry + «GUI»
- Palette слева (как toolbox Blockly)
- Snapping к сетке 8px (опционально)

---

## 10. Дизайн-система (Kinetic Laboratory в ImGui)

Порт `archive/studio-1.6/DESIGN.md` → `SprauteImGuiTheme.java`.

| Токен | HEX | Использование |
|-------|-----|---------------|
| `surface` | `#040e1f` | WindowBg |
| `surface-container-low` | `#0a1628` | Sidebar |
| `primary` | `#d1ff9f` | Акцент, exec-пины |
| `secondary` | `#ac8aff` | Event-ноды |
| `tertiary` | `#47c4ff` | GUI-ноды |
| `on-surface` | `#dbe6fe` | Текст |

**Правила:** без 1px границ между панелями; скругления ~6px; Inter + JetBrains Mono; дефолтная тема ImGui запрещена.

**Ноды:** заголовок = `color` из BlockDefinition; GUI-ноды = `tertiary`; event = `secondary`.

---

## 11. Фазы разработки

### Фаза 0 — Хвосты 1.6 (1–2 нед.)

- [ ] 0.1 Commit: notification, block_props, armor
- [ ] 0.2 Smoke 1.19.2 / 1.20.1
- [ ] 0.3 Ветка `1.7-dev`
- [ ] 0.4 Перенести `app/` → `archive/studio-1.6/` (можно в конце, но до релиза)

---

### Фаза 1 — ImGui Core (2–3 нед.)

- [ ] 1.1 imgui-java + natives (Stonecutter)
- [ ] 1.2–1.5 `SprauteImGui`, Backend, Input, Theme
- [ ] 1.6 TTF шрифты
- [ ] 1.7 `/spraute imgui test`

---

### Фаза 2 — ImGui Game UI (3–4 нед.)

- [ ] 2.1 `SprauteImGuiRenderer` + все виджеты (panel, text, button, image, input, scroll, slot, entity, 9-slice)
- [ ] 2.2 `overlayOpen`, анимации, notifications
- [ ] 2.3 Config `ui_backend`

---

### Фаза 3 — Editor Shell + FS (3–4 нед.)

- [ ] 3.1 `SprauteEngineEditor`, key **N**, `/spraute engine`
- [ ] 3.2 Docking layout
- [ ] 3.3 `EditorFileService` — **только локальный** `gameDir/spraute_engine/`
- [ ] 3.4 `ProjectPanel`, `EditorTabBar`, Ctrl+S
- [ ] 3.5 Инициализация структуры папок
- [ ] 3.6 `ConsolePanel` (базовый)

---

### Фаза 4 — Code Editor + Hot Reload (3–4 нед.)

- [ ] 4.1 `SprEditorPanel` + tokenizer `.spr`
- [ ] 4.2 Autocomplete из `FunctionRegistry` + snippets
- [ ] 4.3 `ScriptHotReloadService`
- [ ] 4.4 `AssetIndexService`

---

### Фаза 5 — Scenes (2–3 нед.)

- [ ] 5.1 `SceneRegistry`, формат `.scene`
- [ ] 5.2 `HierarchyPanel`
- [ ] 5.3 `sceneLoad` / `sceneUnload` API
- [ ] 5.4 Миграция `autorun` → `__boot.scene`

---

### Фаза 6 — Node Graph Core (4–5 нед.)

- [ ] 6.1 `NodeGraphPanel` — рендер, pan, zoom, selection
- [ ] 6.2 Модель графа, `NodeGraphSerializer` (.graph.json)
- [ ] 6.3 Exec/data pins, связи, валидация типов
- [ ] 6.4 `GraphCodegen` — обход графа → `.spr`
- [ ] 6.5 Вкладки Код ↔ Граф
- [ ] 6.6 Palette (категории)

---

### Фаза 7 — Plugin Parser + Block Templates (5–7 нед.)

- [ ] 7.1 `ProcodeBlockParser` — plain + legacy `#\` (read); write только plain
- [ ] 7.2 `PluginRegistry` — load bundled + user plugins
- [ ] 7.3 `BlockCodeTemplate` / `TemplateRegistry` — все bundled block id
- [ ] 7.4 Java templates для **всех** bundled blocks (пакетами по категориям)
- [ ] 7.5 `ProcodePluginMigrator` — `code:` → `template:`, снять `#\` (§8.7) + `.bak-1.6`
- [ ] 7.6 Gradle/`tools/migrate_procode_blocks` — предмиграция в `editor_plugins/`
- [ ] 7.7 Миграция WChoice, Raya, NpcRelations
- [ ] 7.8 Динамические dropdowns (models, npc, …)
- [ ] 7.9 `BlockDocsPanel`

**Чекпоинт:** плагин из Studio 1.6 zip → импорт → все блоки в Node Graph без ручных правок.

---

### Фаза 8 — GUI-ноды (3–4 нед.)

- [ ] 8.1 Определения `engine/ui_*` нод (встроенные, не плагины)
- [ ] 8.2 Parent/child связи для вложенности виджетов
- [ ] 8.3 `GuiNodeCodegen` → `create ui { }`
- [ ] 8.4 Inspector полей (порт PROP_HINTS)
- [ ] 8.5 Live preview `uiOpen`
- [ ] 8.6 `on_uiclick` / `on_uiinput` ноды + привязка к widget id

---

### Фаза 9 — Inspector сцены + Gizmo (2–3 нед.)

- [ ] 9.1 `InspectorPanel` для scene objects и graph nodes
- [ ] 9.2 Маркеры в мире (pos из scene)
- [ ] 9.3 Play mode: editor collapsed, сцена работает

---

### Фаза 10 — Plugins Import/Export + Map Export (2–3 нед.)

- [ ] 10.1 `PluginImporter` / `Exporter` (zip)
- [ ] 10.2 `MapExporter` (порт export-map.js)
- [ ] 10.3 UI: Editor → File → Export…

---

### Фаза 11 — Релиз (2 нед.)

- [ ] 11.1 `mod_version=1.7`, README, `metods.md`, CHANGELOG
- [ ] 11.2 `MIGRATION_STUDIO_TO_INGAME.md`
- [ ] 11.3 CI: `build-mod.yml` вместо Studio workflow
- [ ] 11.4 Полный чеклист тестирования §17
- [ ] 11.5 Tag `v1.7`, GitHub Release

---

## 12. Форматы данных

### 12.1 Scene (`.scene`)

Без изменений от v1.0 документа — см. предыдущую версию §9.1.

### 12.2 Graph (`.graph.json`)

См. §9.1.

### 12.3 Client config (`config/spraute_client.toml`)

```toml
[ui]
backend = "imgui"

[editor]
keybind = "N"
auto_reload_on_save = true
# Редактор доступен любому локальному пользователю на своём gameDir (D5)
# Отдельных OP-проверок нет — редактирует тот, кто открыл N на своём клиенте
```

---

## 13. API скриптов и обратная совместимость

### Новое в 1.7

- `sceneLoad`, `sceneUnload`, `sceneIsActive`, `sceneListLoaded`

### Без изменений

- Весь UI API, `create *`, события, `autorun`

### Deprecated

| API | Замена |
|-----|--------|
| Spraute Studio | In-game Editor (N) |
| `autorun` (рекомендация) | Scenes |

---

## 14. Структура файлов и пакетов

```
src/main/java/.../
  client/imgui/
  client/editor/
  client/editor/panels/       # Project, Code, NodeGraph, Hierarchy, Inspector, Console
  client/editor/plugins/      # PluginRegistry, ProcodeBlockParser
  client/editor/graph/        # NodeGraph, GraphCodegen, serializers
  client/editor/gui/          # GuiNodeDefs, GuiNodeCodegen
  client/editor/code/         # SprEditor, tokenizer, completion
  client/editor/files/        # EditorFileService
  scene/

src/main/resources/
  editor_plugins/procode/     # bundled blocks (из archive)
  assets/spraute_engine/fonts/

<gameDir>/spraute_engine/
  scripts/
  scenes/
  plugins/
  graphs/                     # опционально: .graph.json рядом или здесь
```

```
archive/studio-1.6/           # бывший app/ — только история
  DESIGN.md
  src/
  bundled_plugins/
  ...
```

---

## 15. Архивация Spraute Studio

### Действия

| Что | Действие |
|-----|----------|
| `app/` | `git mv app archive/studio-1.6` |
| `.github/workflows/build.yml` | Удалить → `build-mod.yml` |
| README | Убрать инструкции `npm run dev` |
| `docs/DESIGN.md` | Симлинк или копия из archive |

### Что НЕ делаем

- Не поддерживаем Studio в CI
- Не выпускаем Studio 1.7 installer

### Миграция пользователей

1. Обновить mod до 1.7
2. Файлы `spraute_engine/` **на месте**
3. **N** в игре — тот же проект
4. Плагины в `plugins/` — подхватываются парсером
5. Studio можно удалить с диска

---

## 16. CI/CD и релиз

### build-mod.yml

- Gradle build 1.19.2 + 1.20.1
- Release on tag `v*`: upload JARs

### Артефакты v1.7

- `spraute_engine-1.7-1.19.2.jar`
- `spraute_engine-1.7-1.20.1.jar`
- `CHANGELOG.md`, `MIGRATION_STUDIO_TO_INGAME.md`

---

## 17. Тестирование

| # | Сценарий |
|---|----------|
| T1 | N → новый script → save → run |
| T2 | Node graph → codegen → валидный .spr |
| T3 | GUI-ноды → create ui → uiOpen preview |
| T4 | Все bundled ProCode категории — smoke |
| T5 | Импорт плагина zip Studio 1.6 → автомиграция → блоки в графе |
| T5b | Повторное открытие N — миграция не дублируется |
| T6 | Export map zip |
| T7 | Scene load + Hierarchy |
| T8 | ImGui game UI regression |
| T9 | Hot reload |
| T10 | 1.19.2 + 1.20.1 |
| T11 | armor, block_props, notifications |
| T12 | Редактор на integrated server — локальные файлы |
| T13 | Dedicated: серверные скрипты не ломаются (редактор не обязан работать на клиенте DS) |

---

## 18. Риски и митигация

| Риск | Митигация |
|------|-----------|
| Миграция 80+ block templates | Пакетная миграция по категориям; CI smoke на каждую |
| Node graph UX хуже Blockly | Итерации UX; palette + docs |
| GUI-ноды сложнее canvas | Иерархия parent/child; live preview |
| OpenGL / ImGui конфликты | State guard; vanilla fallback |
| Срок 6+ месяцев | Жёсткий порядок фаз; не начинать 1.8 до 1.7 |
| Legacy `#\ code:` JS у сторонних авторов | `ProcodePluginMigrator` + warning; docs для авторов плагинов |

---

## 19. Дорожная карта по времени

**Полный релиз 1.7: ~24–28 недель (6–7 месяцев)**

| Недели | Фазы | Результат |
|--------|------|-----------|
| 1–2 | 0, 1 | ImGui работает |
| 3–6 | 2 | Game UI на ImGui |
| 5–8 | 3, 4 | Editor shell + code + reload |
| 9–11 | 5 | Scenes |
| 12–16 | 6 | Node Graph core |
| 17–23 | 7 | Plugin parser + все block templates |
| 24–27 | 8, 9 | GUI-ноды + Inspector |
| 28–29 | 10 | Export |
| 30–31 | 11 | Релиз v1.7 |

Параллелизация: Фаза 2 и 3 частично параллельны (−2–3 недели при 2 разработчиках).

---

## Приложение A — Команды

| Команда | Описание |
|---------|----------|
| `/spraute engine` | Редактор |
| `/spraute run <id>` | Загрузить сцену (`spraute_engine/scenes/<id>.scene.json`) |
| `/spraute stop [id]` | Выгрузить сцену (объекты + скрипты); без id — все активные |

---

## Приложение B — Панели редактора

| Панель | Studio аналог |
|--------|---------------|
| `ProjectPanel` | Sidebar file tree |
| `SprEditorPanel` | CodeMirror tab |
| `NodeGraphPanel` | Blockly + GUI canvas (объединены) |
| `HierarchyPanel` | — (новое) |
| `InspectorPanel` | GUI properties + scene objects |
| `ConsolePanel` | Terminal |

---

*Версия 1.3 — D10: снятие префикса `#\` при миграции блоков. Документ живёт в корне репозитория.*
