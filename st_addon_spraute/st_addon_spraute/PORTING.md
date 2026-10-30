# Устройство движка и перенос на другие версии и загрузчики

## Три модуля

| Модуль | Что внутри | Что видит при компиляции |
| ------ | ---------- | ------------------------ |
| `core/` | Язык `.sst`, математика и разбор моделей и анимаций, документы проекта (катсцены, квесты, GUI, FX). Есть тесты. | Только Java и gson |
| `common/` | Весь движок в игре: рантайм, редактор, UI, сеть, ассеты, миксины. | Minecraft без загрузчика (VanillaGradle) |
| `forge/` | `platform/forge/`, `mods.toml`, запуск игры. Отсюда выходит jar. | Minecraft + Forge |

Границы держит сама сборка, а не договорённость. Код с Forge в `common` не скомпилируется, код с Minecraft в `core` тоже.
Это касается и методов, которые Forge незаметно дописывает в классы игры: `Entity.getPersistentData()`, `KeyMapping.getKey()` и другие.

Пакеты сквозные: `org.sprautestudio.st_addon_spraute.lang` лежит в `core`, а `...runtime` в `common`.
Классы `core` и `common` компилируются прямо в модуле загрузчика и попадают в один jar. Задача `checkJar` проверяет, что они там есть.

## Команды

```bash
./gradlew -p st_addon_spraute/st_addon_spraute build              # всё: тесты, проверка jar
./gradlew -p st_addon_spraute/st_addon_spraute :core:test         # только тесты ядра
./gradlew -p st_addon_spraute/st_addon_spraute runServer          # сервер без окна, папка run/
./gradlew -p st_addon_spraute/st_addon_spraute runClient
```

Jar: `build/libs/st_addon_spraute-<версия>.jar`.

## Куда класть новый код

1. Не нужна игра: в `core`, и лучше с тестом.
2. Нужна игра: в `common`.
3. Нужно то, чего нет в игре без загрузчика: метод в `Platform` или `ClientPlatform` плюс его реализация в `forge`.
4. Нужно новое событие игры: `Hook` в `Hooks` или `ClientHooks`, плюс одна строка `bridge(...)` в `ForgeEvents` или `ForgeClientEvents`.

Новый класс со слушателями получает `public static void registerHooks()` и строку в `Modules` (`ClientModules`, если он клиентский).
`ClientModules` загружается только на клиенте, поэтому выделенный сервер не трогает клиентские классы.

Обработчик пишется как обычно, с теми же именами методов события, что у Forge:

```java
public static void registerHooks() {
    Hooks.LIVING_ATTACK.add(Priority.HIGH, Fights::onAttack);
}

private static void onAttack(Hooks.LivingAttack e) {
    if (e.getAmount() > 10) e.setCanceled(true);
}
```

## Новая версия Minecraft

1. Создать `versions/<версия>.properties` по образцу `versions/1.20.1.properties`: версии игры, Java, Forge, маппинги и диапазоны.
2. Сначала посмотреть, что сломалось в самом движке. Модуль Forge при этом не настраивается, поэтому его версия ещё не важна:
   ```bash
   ./gradlew -p st_addon_spraute/st_addon_spraute -Pmc=<версия> :common:compileJava --configure-on-demand
   ```
3. Исправить `common`, затем собрать полностью: `-Pmc=<версия> build`. Если Forge поменял события, правки будут только в `platform/forge`.

Пробный прогон на 1.20.4: первый проход компилятора дал 24 ошибки в 15 файлах `common`, в основном `Bridges` (скорборд) и экраны.
После их исправления могут появиться новые, это оценка масштаба, а не точный объём. Версия Forge в пробе не была подобрана, сборка самого Forge-модуля под 1.20.4 не проверялась.

Сейчас поддерживается одна версия. Если версий станет несколько одновременно, то понадобятся две вещи, которых пока нет:

- разные имена jar: сейчас сборки разных версий попадут в один и тот же файл;
- способ держать код разных версий в одном исходнике. В основном репозитории для этого есть Stonecutter (см. `MULTIVERSION.md`), здесь он не подключён.

## Новый загрузчик

1. Создать модуль рядом с `forge/` и добавить его в `settings.gradle`. Исходники `core` и `common` подключаются так же, как в `forge/build.gradle`.
2. Реализовать `Platform` и `ClientPlatform`.
3. Вызывать `Hook.fire(...)` из событий загрузчика: всего 75 событий, образец в `ForgeEvents` и `ForgeClientEvents`. Если у загрузчика нет приоритетов, есть `fire(event)`, он пройдёт их все по порядку.
4. На старте: `Platform.install(...)`, на клиенте `ClientPlatform.install(...)`, затем `St_addon_spraute.init()`, и только потом подключение событий: слушатели регистрируются внутри `init()`.

### Что потребует отдельной работы

Эти места привязаны к Forge и не решаются одной реализацией метода:

| Что | Где | В чём дело |
| --- | --- | ---------- |
| Имена в миксинах | `common/.../mixin/*` | Указаны `remap = false` и пара имён SRG + официальное. Загрузчику с другими именами в рантайме (Fabric: intermediary) нужны свои. NeoForge на официальных именах должен подойти, но это не проверялось. |
| Звуки проекта | `ProjectSounds.Instance.getStream` | Метод дописан в `SoundInstance` самим Forge, без него собственные звуки проекта не заиграют. Нужен миксин или аналог. |
| Доп. хитбоксы персонажей | `BodyPart`, `Platform.bodyPart`, `Platform.modelEntity` | В Forge это `PartEntity`. В других загрузчиках хитбоксы придётся встраивать в выбор цели самим. |
| Данные на сущности | `Platform.persistentData` | Forge хранит их и сохраняет в NBT сам. Иначе нужен миксин сохранения. Для уже идущих миров формат должен совпасть. |
| Игрок-заглушка | `Platform.fakePlayer` | Нужен для команды `use` от лица NPC. |
| Клавиши | `ClientPlatform.gameKey` | Контекст «только в игре» есть не везде. |
| HUD | `ClientHooks.RenderHudPart` | Части HUD у Forge именованные (`minecraft:player_health`...). У других загрузчиков их придётся выделять самим. Имена перечислены в `HudParts`, а `drawOriginal` рисует часть с другим положением. |
| JavaScript сюжетов | `runtime/JsEngine` | Nashorn в игру приносит Forge. Другому загрузчику надо положить его в jar. |
| `on forge "..."` в сюжетах | `Platform.listenLoaderEvent` | События Forge по имени. Для другого загрузчика по умолчанию ошибка: у него свои имена событий. |
