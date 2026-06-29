# Мультиверсия (Stonecutter) — памятка

Мод собирается под несколько версий Minecraft из **одного исходника** в `src/main/java`.
Версионные различия оформляются комментариями-гардами Stonecutter, а не копиями файлов.

## Стек
- Stonecutter `dev.kikugie.stonecutter:0.7.11`
- Gradle `8.8`, ForgeGradle `[6.0,6.2)`, Java `17`
- Поддерживаемые версии перечислены в `settings.gradle`, активная — в `stonecutter.gradle`.
- Версионные свойства (версия MC/Forge, `pack_format` и т.п.) лежат в `versions/<версия>/gradle.properties`.

## Команды
```bash
# Сменить активную версию (правит исходники под неё)
gradlew "Set 1.20.1 active"     # или 1.19.2

# Компиляция конкретной версии
gradlew :1.20.1:compileJava

# Сборка jar обеих версий (рекомендуется после правок)
gradlew :1.19.2:build :1.20.1:build -x test
```
Готовые jar: `versions/<версия>/build/libs/spraute_engine-1.2-<mc>.jar`.

> **Паритет функций:** `create block` (дроп, `drops`, руда `is_ore`), `create craft` (`any`/`slots`, теги и списки замен), `create drop`, `create item`, `create tab` — одинаковый синтаксис в `.spr` на **1.19.2** и **1.20.1**. Различия только во внутреннем API Minecraft (гарды Stonecutter / compat-классы).

> Активная версия = та, чья ветка в исходниках сейчас **раскомментирована**. Сейчас активна `1.20.1`.

## Синтаксис гарда
Активная ветка раскомментирована, неактивная — в блочном комментарии.
```java
//? if >=1.20.1 {
guiGraphics.drawString(font, text, x, y, color);
//?} else {
/*GuiComponent.drawString(poseStack, font, text, x, y, color);
*///?}
```
Условия: `>=1.20.1`, `<1.20.1`, `>=1.20.1 <1.21` и т.п.

## Как добавить новую функцию сразу на все версии
1. Пиши общий код как обычно — то, что одинаково на всех версиях, гардить не нужно.
2. Различающийся API оборачивай гардом (см. выше) **или** выноси в compat-хелпер.
3. Проверь компиляцию каждой версии: `gradlew :1.19.2:compileJava` и `:1.20.1:compileJava`.

## Готовые compat-хелперы (`org.zonarstudio.spraute_engine.compat`)
- `SprauteEntityCompat.level(entity)` — поле `level` стало приватным в 1.20.1.
- `SprauteEntityCompat.onGround(entity)` — `onGround` (в 1.19.2 → `isOnGround()`).
- `SprauteEntityCompat.serverLevel(player)` — `getLevel()` → `serverLevel()`.
- `SprauteEntityCompat.getItemThrower(itemEntity)` — в 1.20.1 геттер удалён (доступ через рефлексию).
- `SprauteRenderCompat.rotateX/Y/Z(...)`, `renderItemInHand/renderFixedItem(...)` — оси/кватернионы и `ItemDisplayContext`.
- `SprauteGuiDraw` — рисование GUI (`GuiGraphics` ↔ `PoseStack`/`GuiComponent`).

Если новый различающийся вызов встречается часто — добавь метод в подходящий compat-класс,
чтобы не плодить десятки точечных гардов.

## Частые отличия 1.19.2 → 1.20.1
| 1.19.2 | 1.20.1 |
|--------|--------|
| `PoseStack` в `render(...)` | `GuiGraphics` |
| `new ResourceLocation(s)` | то же (но `parse()` только с 1.21) |
| `commandSource.sendSuccess(component, b)` | `sendSuccess(() -> component, b)` |
| `new SoundEvent(rl)` | `SoundEvent.createVariableRangeEvent(rl)` |
| `Registry.X_REGISTRY` | `Registries.X` |
| `Vector3f`/`Quaternion` | `com.mojang.math.Axis` / `org.joml.Quaternionf` |
| `Material.STONE` в `Properties.of(...)` | `Properties.of()` (Material удалён) |
| `new BlockPos(vec3)` | `BlockPos.containing(vec3)` |
| креатив-вкладки через анонимный класс | `CreativeModeTab.builder()` + `RegisterEvent` |
