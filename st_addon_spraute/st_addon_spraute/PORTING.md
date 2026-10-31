# Устройство движка и перенос на другие версии и загрузчики

## Модули

| Модуль | Что внутри | Что видит при компиляции |
| ------ | ---------- | ------------------------ |
| `core/` | Язык `.sst`, математика и разбор моделей и анимаций, документы проекта (катсцены, квесты, GUI, FX). Есть тесты. | Только Java и gson |
| `common/` | Весь движок в игре: рантайм, редактор, UI, сеть, ассеты, миксины. | Minecraft без загрузчика (VanillaGradle) |
| `forge/` | `platform/forge/`, `mods.toml`, запуск игры. Отсюда выходит jar для Forge. | Minecraft + Forge |
| `fabric/` | `platform/fabric/`, `fabric.mod.json`, миксины событий, запуск игры. Отсюда выходит jar для Fabric. | Minecraft + Fabric API |

Границы держит сама сборка, а не договорённость. Код с Forge в `common` не скомпилируется, код с Minecraft в `core` тоже.
Это касается и методов, которые Forge незаметно дописывает в классы игры: `Entity.getPersistentData()`, `KeyMapping.getKey()` и другие.

Пакеты сквозные: `org.sprautestudio.spraute_engine.lang` лежит в `core`, а `...runtime` в `common`.
Классы `core` и `common` компилируются прямо в модуле загрузчика и попадают в его jar. Задача `checkJar` проверяет, что они там есть.

## Команды

```bash
./gradlew -p st_addon_spraute/st_addon_spraute build              # всё: тесты, проверка jar
./gradlew -p st_addon_spraute/st_addon_spraute :core:test         # только тесты ядра
./gradlew -p st_addon_spraute/st_addon_spraute :forge:runServer   # сервер без окна, папка run/
./gradlew -p st_addon_spraute/st_addon_spraute :forge:runClient
./gradlew -p st_addon_spraute/st_addon_spraute :fabric:runServer  # папка fabric/run/
./gradlew -p st_addon_spraute/st_addon_spraute :fabric:runClient
./gradlew -p st_addon_spraute/st_addon_spraute :fabric:runAuditClient  # все миксины на классах клиента, без окна игры
```

Имя задачи указывайте вместе с модулем: просто `runServer` запустит оба сервера подряд.

Jar: `build/libs/spraute_engine-<версия>.jar` для Forge и `build/libs/spraute_engine-fabric-<версия>.jar` для Fabric (ему нужен Fabric API).

## Куда класть новый код

1. Не нужна игра: в `core`, и лучше с тестом.
2. Нужна игра: в `common`.
3. Нужно то, чего нет в игре без загрузчика: метод в `Platform` или `ClientPlatform` плюс его реализация в `forge`.
4. Нужно новое событие игры: `Hook` в `Hooks` или `ClientHooks`, плюс одна строка `bridge(...)` в `ForgeEvents` или `ForgeClientEvents`, плюс вызов на Fabric: в `FabricEvents`, `FabricClientEvents` или, если в Fabric API такого события нет, миксин в `platform/fabric/mixin`.

После изменения миксинов запускайте `:fabric:runAuditClient`: несовпадение с кодом игры иначе проявится только вылетом при запуске клиента.

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

1. Создать модуль рядом с `forge/` и `fabric/` и добавить его в `settings.gradle`. Исходники `core` и `common` подключаются так же, как в их `build.gradle`.
2. Реализовать `Platform` и `ClientPlatform`.
3. Вызывать `Hook.fire(...)` из событий загрузчика: всего 75 событий. Образцы: `ForgeEvents` (у загрузчика есть почти всё) и `FabricEvents` с миксинами (нет многого). Если у загрузчика нет приоритетов, есть `fire(event)`, он пройдёт их все по порядку.
4. На старте: `Platform.install(...)`, на клиенте `ClientPlatform.install(...)`, затем `SprauteEngine.init()`, и только потом подключение событий: слушатели регистрируются внутри `init()`.

Миксины `common` указывают официальные имена, а refmap каждого загрузчика переводит их в свои.

## Чем Fabric отличается от Forge

Движок на Fabric тот же. Различия там, где у Fabric нет того, что Forge даёт готовым:

| Что | На Fabric |
| --- | --- |
| `on forge "..."` в сюжетах | Не работает: это события самого Forge. Сюжет получит ошибку. |
| Свои координаты частей HUD, которые рисует игра | Не применяются: у Forge HUD разбит на части, которые можно перерисовать в другом месте. Часть остаётся на месте игры. Скрытие и замена на свои работают. |
| Скрытие HUD в катсценах | Скрываются здоровье, броня, еда, воздух, опыт, хотбар, прицел, эффекты, боссы, скорборд, транспорт, название предмета. Не скрываются виньетка, тыква и подзорная труба, список игроков, отладочный экран: у них нет своей точки в `GuiMixin`. |
| Стадии отрисовки мира | `AFTER_PARTICLES` = `WorldRenderEvents.AFTER_TRANSLUCENT`, `AFTER_WEATHER` = `WorldRenderEvents.LAST`: близко к Forge, но не тот же момент. |
| Доп. хитбоксы персонажей | `FabricPart` и миксины `Level`, `ServerLevel`: показывают части игре так же, как части дракона. |
| Данные на сущности | Хранятся под именем `ForgeData`, как у Forge, поэтому мир можно перенести между загрузчиками. |
| JavaScript сюжетов | Nashorn лежит внутри jar. |

Клиент и сервер должны быть на одном загрузчике: Fabric-клиент не подключится к Forge-серверу и наоборот.
