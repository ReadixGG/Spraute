package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeIds;
import org.zonarstudio.spraute_engine.scene.resource.SceneBlockDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneDropDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneItemDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneParticleDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneProjectileDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneTabDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneWorldDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/** Inspector sections for RES_BLOCK / RES_ITEM / RES_CRAFT / RES_TAB / RES_DROP. */
public final class ResourceInspector {
    private final CraftRecipeEditor craftEditor = new CraftRecipeEditor();
    private final TabItemsEditor tabEditor = new TabItemsEditor();
    private final DropRulesEditor dropRulesEditor = new DropRulesEditor();
    private final ImString nameBuf = new ImString(128);
    private final ImString strBuf = new ImString(256);
    private final ImString texSearch = new ImString(64);
    private final ImString modelSearch = new ImString(64);
    private final float[] single = new float[1];
    private final float[] size3 = new float[3];
    private final int[] intBuf = new int[1];
    private Object resFactory;
    private boolean hasResetDefault;
    private Object resetDefault;

    public void render(SceneNode node) {
        if (node == null || !node.type.isResourceDef()) return;
        hasResetDefault = false;
        resetDefault = null;
        resFactory = switch (node.type) {
            case RES_BLOCK -> new SceneBlockDef();
            case RES_ITEM -> new SceneItemDef();
            case RES_CRAFT -> new org.zonarstudio.spraute_engine.scene.resource.SceneCraftDef();
            case RES_TAB -> new SceneTabDef();
            case RES_DROP -> new SceneDropDef();
            case RES_WORLD -> new SceneWorldDef();
            case RES_PARTICLE -> new SceneParticleDef();
            case RES_PROJECTILE -> new SceneProjectileDef();
            default -> null;
        };
        EditorInspectorUi.hint(EditorI18n.t("Регистрация при старте MC · нужен перезапуск после правок"));

        if (EditorInspectorUi.beginSection("res-object", EditorI18n.t("Объект"), true)) {
            nameBuf.set(node.name == null ? "" : node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "res-name", nameBuf)) {
                node.name = nameBuf.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.endSection();
        }

        switch (node.type) {
            case RES_BLOCK -> renderBlock(node);
            case RES_ITEM -> renderItem(node);
            case RES_CRAFT -> {
                if (node.craftDef == null) node.craftDef = new org.zonarstudio.spraute_engine.scene.resource.SceneCraftDef();
                node.craftDef.id = node.id;
                if (EditorInspectorUi.beginSection("res-craft", EditorI18n.t("Рецепт"), true)) {
                    craftEditor.render(node.craftDef);
                    EditorInspectorUi.endSection();
                }
            }
            case RES_TAB -> renderTab(node);
            case RES_DROP -> renderDrop(node);
            case RES_WORLD -> renderWorld(node);
            case RES_PARTICLE -> renderParticle(node);
            case RES_PROJECTILE -> renderProjectile(node);
            default -> {}
        }
    }

    private void renderParticle(SceneNode node) {
        if (node.particleDef == null) node.particleDef = new SceneParticleDef();
        SceneParticleDef d = node.particleDef;
        d.id = node.id;
        if (EditorInspectorUi.beginSection("res-particle", EditorI18n.t("Тип частицы | OLD"), true)) {
            EditorInspectorUi.hint(EditorI18n.t("create particle · id ноды = spraute_engine:id · нужен перезапуск MC"));
            rst(resVal("texture")).assetCombo(EditorI18n.t("Текстура"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
            if (ImGui.smallButton(EditorI18n.t("Обновить ассеты"))) EditorAssetIndex.refresh();
            EditorInspectorUi.hint(EditorI18n.t("Спавн: particleSpawn(\"spraute_engine:")
                    + node.id + "\", x, y, z, …) · эмиттер: type = \"spraute_engine:" + node.id + "\"");
            EditorInspectorUi.endSection();
        }
    }

    private void renderProjectile(SceneNode node) {
        if (node.projectileDef == null) node.projectileDef = new SceneProjectileDef();
        SceneProjectileDef d = node.projectileDef;
        d.id = node.id;
        if (EditorInspectorUi.beginSection("res-projectile", EditorI18n.t("Снаряд"), true)) {
            EditorInspectorUi.hint(EditorI18n.t("create projectile · id = id снаряда · shootProjectile(id, …)"));
            rst(resVal("texture")).assetCombo(EditorI18n.t("Текстура"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
            if (ImGui.smallButton(EditorI18n.t("Обновить ассеты"))) EditorAssetIndex.refresh();

            EditorInspectorUi.fieldLabel(EditorI18n.t("Тип отрисовки"));
            String style = d.renderStyle == null ? "billboard" : d.renderStyle.trim().toLowerCase(Locale.ROOT);
            String styleShown = switch (style) {
                case "velocity", "arrow", "flat" -> EditorI18n.t("Плоскость по скорости (стрела)");
                case "cross", "x", "fireball" -> EditorI18n.t("Крест (X, как фаербол)");
                default -> EditorI18n.t("Билборд (к камере)");
            };
            if (ImGui.beginCombo("##proj-style", styleShown)) {
                if (ImGui.selectable(EditorI18n.t("Билборд (к камере)"), "billboard".equals(style) || style.isEmpty())) {
                    SceneEditorSession.pushUndo();
                    d.renderStyle = "billboard";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Плоскость по скорости (стрела)"), "velocity".equals(style) || "arrow".equals(style))) {
                    SceneEditorSession.pushUndo();
                    d.renderStyle = "velocity";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Крест (X, как фаербол)"), "cross".equals(style) || "x".equals(style))) {
                    SceneEditorSession.pushUndo();
                    d.renderStyle = "cross";
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }

            dragFloat(EditorI18n.t("Ширина"), d.width, 0.01f, 0.05f, 4f, v -> d.width = v);
            dragFloat(EditorI18n.t("Высота"), d.height, 0.01f, 0.05f, 4f, v -> d.height = v);
            dragInt(EditorI18n.t("Альфа 0–255"), d.alpha, 0, 255, v -> d.alpha = v);
            checkbox(EditorI18n.t("Сквозной (see_through)"), d.seeThrough, v -> d.seeThrough = v);
            checkbox(EditorI18n.t("Свечение"), d.glow, v -> d.glow = v);
            dragFloat(EditorI18n.t("Гравитация"), d.gravity, 0.001f, 0f, 1f, v -> d.gravity = v);
            dragFloat(EditorI18n.t("Drag"), d.drag, 0.01f, 0f, 1f, v -> d.drag = v);
            dragInt(EditorI18n.t("Lifetime (тики)"), d.lifetime, 1, 20_000, v -> d.lifetime = v);
            dragFloat(EditorI18n.t("Урон"), d.damage, 0.1f, 0f, 100f, v -> d.damage = v);
            dragInt(EditorI18n.t("Пробитие"), d.pierce, 0, 100, v -> d.pierce = v);
            dragFloat(EditorI18n.t("Отбрасывание"), d.knockback, 0.05f, 0f, 10f, v -> d.knockback = v);
            dragFloat(EditorI18n.t("Вращение (°/тик)"), d.rotation, 0.5f, -720f, 720f, v -> d.rotation = v);
            checkbox(EditorI18n.t("Столкновение с блоками"), d.collideBlocks, v -> d.collideBlocks = v);
            checkbox(EditorI18n.t("Столкновение с сущностями"), d.collideEntities, v -> d.collideEntities = v);
            EditorInspectorUi.hint(EditorI18n.t("Регистрация при старте MC из сцены; из скрипта create projectile — сразу"));
            EditorInspectorUi.endSection();
        }
    }

    private void renderWorld(SceneNode node) {
        if (node.worldDef == null) node.worldDef = new SceneWorldDef();
        SceneWorldDef d = node.worldDef;
        d.id = node.id;
        if (EditorInspectorUi.beginSection("res-world", EditorI18n.t("Измерение"), true)) {
            EditorInspectorUi.hint(EditorI18n.t("create world · id ноды = id мира · нужен перезапуск MC"));

            String skyShown = (d.sky == null || d.sky.isBlank()) ? "#87CEEB" : d.sky;
            UiStyleEditor.colorFieldRgb(EditorI18n.t("Цвет неба"), "world-sky", skyShown, v -> d.sky = v);

            boolean fogCustom = d.fog != null && !d.fog.isBlank();
            if (EditorInspectorUi.checkbox(EditorI18n.t("Свой цвет тумана"), fogCustom)) {
                SceneEditorSession.pushUndo();
                if (fogCustom) {
                    d.fog = "";
                } else {
                    d.fog = skyShown;
                }
                SceneEditorSession.markDirty();
                fogCustom = !fogCustom;
            }
            if (fogCustom) {
                UiStyleEditor.colorFieldRgb(EditorI18n.t("Цвет тумана"), "world-fog", d.fog, v -> d.fog = v);
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Туман = затемнённый цвет неба"));
            }

            rst(resVal("ambient")).dragFloat(EditorI18n.t("Освещение (ambient 0–1)"), d.ambient, 0.01f, 0f, 1f, v -> d.ambient = v);

            EditorInspectorUi.fieldLabel(EditorI18n.t("Фиксированное время (тики)"));
            intBuf[0] = (int) d.fixedTime;
            ImGui.setNextItemWidth(-1f);
            if (ImGui.dragInt("##world-fixed-time", intBuf, 10f, -1, 23999)) {
                d.fixedTime = intBuf[0];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.hint(EditorI18n.t("-1 = сутки · 0 = рассвет · 6000 = полдень · 18000 = полночь"));

            rst(resVal("skylight")).checkbox(EditorI18n.t("Небесный свет (skylight)"), d.skylight, v -> d.skylight = v);
            rst(resVal("clouds")).checkbox(EditorI18n.t("Облака"), d.clouds, v -> d.clouds = v);
            if (!d.clouds) {
                EditorInspectorUi.hint(EditorI18n.t("Облака отключены (clouds = false)"));
            }

            EditorInspectorUi.hint(EditorI18n.t("Телепорт: teleportToWorld(player, \"") + node.id + "\", x, y, z)");
            EditorInspectorUi.endSection();
        }
    }

    private void renderBlock(SceneNode node) {
        if (node.blockDef == null) node.blockDef = new SceneBlockDef();
        SceneBlockDef d = node.blockDef;
        d.id = node.id;
        d.ensureLookDefaults();

        String kind = normalizeBlockKind(d.lookKind);
        String texMode = normalizeTexMode(d.texMode);

        if (EditorInspectorUi.beginSection("res-block-look", EditorI18n.t("Внешний вид"), true)) {
            rst(resVal("displayName")).stringField(EditorI18n.t("Отображаемое имя"), d.displayName, v -> d.displayName = v);

            EditorInspectorUi.fieldLabel(EditorI18n.t("Тип внешнего вида"));
            String kindShown = switch (kind) {
                case "model" -> EditorI18n.t("3D-модель");
                case "ore" -> EditorI18n.t("Руда");
                default -> EditorI18n.t("Текстура");
            };
            if (ImGui.beginCombo("##blk-look-kind", kindShown)) {
                if (ImGui.selectable(EditorI18n.t("Текстура"), "texture".equals(kind))) {
                    SceneEditorSession.pushUndo();
                    d.lookKind = "texture";
                    d.isOre = false;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("3D-модель"), "model".equals(kind))) {
                    SceneEditorSession.pushUndo();
                    d.lookKind = "model";
                    d.isOre = false;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Руда"), "ore".equals(kind))) {
                    SceneEditorSession.pushUndo();
                    d.lookKind = "ore";
                    d.isOre = true;
                    if (d.requiredTool == null || d.requiredTool.isBlank()) {
                        d.requiredTool = "pickaxe";
                        d.requiresCorrectTool = true;
                    }
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }

            if ("model".equals(kind)) {
                rst(resVal("model")).assetCombo(EditorI18n.t("Модель (.geo.json)"), d.model, EditorAssetIndex.models(), modelSearch, v -> d.model = v);
                rst(resVal("texture")).assetCombo(EditorI18n.t("Текстура модели"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
            } else {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Режим текстур"));
                String texShown = switch (texMode) {
                    case "top_sides" -> EditorI18n.t("Верх и бока");
                    case "all" -> EditorI18n.t("Все грани");
                    default -> EditorI18n.t("Одна на все");
                };
                if (ImGui.beginCombo("##blk-tex-mode", texShown)) {
                    if (ImGui.selectable(EditorI18n.t("Одна на все"), "single".equals(texMode))) {
                        SceneEditorSession.pushUndo();
                        d.texMode = "single";
                        SceneEditorSession.markDirty();
                    }
                    if (ImGui.selectable(EditorI18n.t("Верх и бока"), "top_sides".equals(texMode))) {
                        SceneEditorSession.pushUndo();
                        d.texMode = "top_sides";
                        SceneEditorSession.markDirty();
                    }
                    if (ImGui.selectable(EditorI18n.t("Все грани"), "all".equals(texMode))) {
                        SceneEditorSession.pushUndo();
                        d.texMode = "all";
                        SceneEditorSession.markDirty();
                    }
                    ImGui.endCombo();
                }
                renderBlockTextures(d, texMode);
            }

            if (ImGui.smallButton(EditorI18n.t("Обновить ассеты"))) EditorAssetIndex.refresh();
            EditorInspectorUi.endSection();
        }

        if (EditorInspectorUi.beginSection("res-block-phys", EditorI18n.t("Физика"), true)) {
            rst(resVal("hasCollision")).checkbox(EditorI18n.t("Твёрдый (коллизия)"), d.hasCollision, v -> d.hasCollision = v);
            rst(resVal("hardness")).dragFloat(EditorI18n.t("Прочность ломания"), d.hardness, 0.05f, -1f, 100f, v -> d.hardness = v);
            EditorInspectorUi.hint(EditorI18n.t("камень≈1.5 · руда≈3 · обсидиан≈50 · <0 = неломаемый"));

            EditorInspectorUi.fieldLabel(EditorI18n.t("Инструмент для добычи"));
            String tool = d.requiredTool == null ? "" : d.requiredTool;
            String toolShown = switch (tool) {
                case "pickaxe" -> EditorI18n.t("Кирка");
                case "axe" -> EditorI18n.t("Топор");
                case "shovel" -> EditorI18n.t("Лопата");
                case "hoe" -> EditorI18n.t("Мотыга");
                default -> EditorI18n.t("Любой / рука");
            };
            if (ImGui.beginCombo("##blk-req-tool", toolShown)) {
                if (ImGui.selectable(EditorI18n.t("Любой / рука"), tool.isBlank())) {
                    SceneEditorSession.pushUndo();
                    d.requiredTool = "";
                    d.requiresCorrectTool = false;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Кирка"), "pickaxe".equals(tool))) {
                    SceneEditorSession.pushUndo();
                    d.requiredTool = "pickaxe";
                    if (!d.requiresCorrectTool) d.requiresCorrectTool = true;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Топор"), "axe".equals(tool))) {
                    SceneEditorSession.pushUndo();
                    d.requiredTool = "axe";
                    if (!d.requiresCorrectTool) d.requiresCorrectTool = true;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Лопата"), "shovel".equals(tool))) {
                    SceneEditorSession.pushUndo();
                    d.requiredTool = "shovel";
                    if (!d.requiresCorrectTool) d.requiresCorrectTool = true;
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Мотыга"), "hoe".equals(tool))) {
                    SceneEditorSession.pushUndo();
                    d.requiredTool = "hoe";
                    if (!d.requiresCorrectTool) d.requiresCorrectTool = true;
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }

            if (!tool.isBlank()) {
                rst(resVal("requiredMiningLevel")).dragInt(EditorI18n.t("Мин. уровень инструмента"), d.requiredMiningLevel, 0, 4, v -> d.requiredMiningLevel = v);
                EditorInspectorUi.hint(EditorI18n.t("0=дерево · 1=камень · 2=железо · 3=алмаз · 4=незерит"));
                rst(resVal("requiresCorrectTool")).checkbox(EditorI18n.t("Только правильным инструментом (дроп)"), d.requiresCorrectTool, v -> d.requiresCorrectTool = v);
            }

            rst(resVal("lightEmission")).dragInt(EditorI18n.t("Свет 0–15"), d.lightEmission, 0, 15, v -> d.lightEmission = v);
            if ("model".equals(kind)) {
                rst(resVal("directional")).checkbox(EditorI18n.t("Поворот к игроку"), d.directional, v -> d.directional = v);
                size3[0] = d.sizeW; size3[1] = d.sizeD; size3[2] = d.sizeH;
                EditorInspectorUi.fieldLabel(EditorI18n.t("Размер multiblock W×D×H"));
                if (EditorInspectorUi.vector3("blk-size", size3, 1f, 1f, 16f)) {
                    d.sizeW = Math.max(1, Math.round(size3[0]));
                    d.sizeD = Math.max(1, Math.round(size3[1]));
                    d.sizeH = Math.max(1, Math.round(size3[2]));
                    SceneEditorSession.markDirty();
                }
                EditorInspectorUi.undoOnActivate();
            }
            EditorInspectorUi.endSection();
        }

        if (EditorInspectorUi.beginSection("res-block-drop", EditorI18n.t("Дроп"), false)) {
            d.ensureDropDefaults();
            rst(resVal("dropReplace")).checkbox(EditorI18n.t("Заменить ванильный дроп"), d.dropReplace, v -> d.dropReplace = v);
            EditorInspectorUi.hint(EditorI18n.t("Выкл = ванильный дроп если таблица пуста/не выпала · Вкл = только таблица"));
            dropRulesEditor.renderPanel(d.drops, true, d.dropXp, v -> d.dropXp = v);
            rst(resVal("tab")).stringField(EditorI18n.t("Вкладка креатива"), d.tab, v -> d.tab = v);
            EditorInspectorUi.endSection();
        }

        if ("ore".equals(kind) && EditorInspectorUi.beginSection("res-block-ore", EditorI18n.t("Руда"), true)) {
            d.isOre = true;
            rst(resVal("oreVeinSize")).dragInt(EditorI18n.t("Жила"), d.oreVeinSize, 1, 64, v -> d.oreVeinSize = v);
            rst(resVal("oreMinY")).dragInt("Min Y", d.oreMinY, -64, 320, v -> d.oreMinY = v);
            rst(resVal("oreMaxY")).dragInt("Max Y", d.oreMaxY, -64, 320, v -> d.oreMaxY = v);
            rst(resVal("oreChances")).dragInt(EditorI18n.t("Попыток/чанк"), d.oreChances, 1, 64, v -> d.oreChances = v);
            rst(resVal("oreDimension")).stringField(EditorI18n.t("Измерение"), d.oreDimension, v -> d.oreDimension = v);
            EditorInspectorUi.endSection();
        }
    }

    private void renderBlockTextures(SceneBlockDef d, String texMode) {
        switch (texMode) {
            case "top_sides" -> {
                rst(resVal("textureUp")).assetCombo(EditorI18n.t("Верх"), d.textureUp, EditorAssetIndex.textures(), texSearch, v -> d.textureUp = v);
                rst(resVal("textureSides")).assetCombo(EditorI18n.t("Бока"), d.textureSides, EditorAssetIndex.textures(), texSearch, v -> d.textureSides = v);
                rst(resVal("texture")).assetCombo(EditorI18n.t("Запасная (опц.)"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
            }
            case "all" -> {
                rst(resVal("textureUp")).assetCombo(EditorI18n.t("Верх"), d.textureUp, EditorAssetIndex.textures(), texSearch, v -> d.textureUp = v);
                rst(resVal("textureDown")).assetCombo(EditorI18n.t("Низ"), d.textureDown, EditorAssetIndex.textures(), texSearch, v -> d.textureDown = v);
                rst(resVal("textureNorth")).assetCombo(EditorI18n.t("Север"), d.textureNorth, EditorAssetIndex.textures(), texSearch, v -> d.textureNorth = v);
                rst(resVal("textureSouth")).assetCombo(EditorI18n.t("Юг"), d.textureSouth, EditorAssetIndex.textures(), texSearch, v -> d.textureSouth = v);
                rst(resVal("textureWest")).assetCombo(EditorI18n.t("Запад"), d.textureWest, EditorAssetIndex.textures(), texSearch, v -> d.textureWest = v);
                rst(resVal("textureEast")).assetCombo(EditorI18n.t("Восток"), d.textureEast, EditorAssetIndex.textures(), texSearch, v -> d.textureEast = v);
            }
            default -> assetCombo(EditorI18n.t("Текстура"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
        }
    }

    private static String normalizeBlockKind(String kind) {
        if (kind == null) return "texture";
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "model", "geo", "3d" -> "model";
            case "ore", "руда" -> "ore";
            default -> "texture";
        };
    }

    private static String normalizeTexMode(String mode) {
        if (mode == null) return "single";
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "top_sides", "top-sides", "sides" -> "top_sides";
            case "all", "faces", "cube" -> "all";
            default -> "single";
        };
    }

    private void renderItem(SceneNode node) {
        if (node.itemDef == null) node.itemDef = new SceneItemDef();
        SceneItemDef d = node.itemDef;
        d.id = node.id;
        d.ensureLookDefaults();

        String look = normalizeItemLook(d.lookKind);

        if (EditorInspectorUi.beginSection("res-item-look", EditorI18n.t("Внешний вид"), true)) {
            rst(resVal("displayName")).stringField(EditorI18n.t("Отображаемое имя"), d.displayName, v -> d.displayName = v);

            EditorInspectorUi.fieldLabel(EditorI18n.t("Тип внешнего вида"));
            String lookShown = "geo".equals(look) ? "3D Bedrock (geo)" : EditorI18n.t("Плоская текстура");
            if (ImGui.beginCombo("##item-look-kind", lookShown)) {
                if (ImGui.selectable(EditorI18n.t("Плоская текстура"), "flat".equals(look))) {
                    SceneEditorSession.pushUndo();
                    d.lookKind = "flat";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable("3D Bedrock (geo)", "geo".equals(look))) {
                    SceneEditorSession.pushUndo();
                    d.lookKind = "geo";
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }

            if ("geo".equals(look)) {
                rst(resVal("geo")).assetCombo(EditorI18n.t("Geo-модель"), d.geo, EditorAssetIndex.models(), modelSearch, v -> d.geo = v);
                rst(resVal("texture")).assetCombo(EditorI18n.t("Текстура"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
            } else {
                rst(resVal("texture")).assetCombo(EditorI18n.t("Иконка / текстура"), d.texture, EditorAssetIndex.textures(), texSearch, v -> d.texture = v);
                rst(resVal("model")).assetCombo(EditorI18n.t("Модель (опц.)"), d.model, EditorAssetIndex.models(), modelSearch, v -> d.model = v);
            }

            rst(resVal("tab")).stringField(EditorI18n.t("Вкладка"), d.tab, v -> d.tab = v);
            rst(resVal("maxStackSize")).dragInt(EditorI18n.t("Макс. стак"), d.maxStackSize, 1, 64, v -> d.maxStackSize = v);
            if (ImGui.smallButton(EditorI18n.t("Обновить ассеты"))) EditorAssetIndex.refresh();
            EditorInspectorUi.endSection();
        }

        if (EditorInspectorUi.beginSection("res-item-type", EditorI18n.t("Тип"), true)) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Инструмент / броня"));
            String tool = d.toolType == null ? "" : d.toolType;
            String armor = d.armorType == null ? "" : d.armorType;
            String shown = !tool.isBlank() ? EditorI18n.t("Инструмент: ") + tool
                    : !armor.isBlank() ? EditorI18n.t("Броня: ") + armor : EditorI18n.t("Обычный");
            if (ImGui.beginCombo("##item-kind", shown)) {
                if (ImGui.selectable(EditorI18n.t("Обычный"), tool.isBlank() && armor.isBlank())) {
                    SceneEditorSession.pushUndo();
                    d.toolType = "";
                    d.armorType = "";
                    SceneEditorSession.markDirty();
                }
                for (String t : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"}) {
                    if (ImGui.selectable(EditorI18n.t("Инструмент: ") + t, t.equals(tool))) {
                        SceneEditorSession.pushUndo();
                        d.toolType = t;
                        d.armorType = "";
                        SceneEditorSession.markDirty();
                    }
                }
                for (String a : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                    if (ImGui.selectable(EditorI18n.t("Броня: ") + a, a.equals(armor))) {
                        SceneEditorSession.pushUndo();
                        d.armorType = a;
                        d.toolType = "";
                        SceneEditorSession.markDirty();
                    }
                }
                ImGui.endCombo();
            }

            if (!tool.isBlank()) {
                rst(resVal("damage")).floatField(EditorI18n.t("Урон"), d.damage, v -> d.damage = v);
                rst(resVal("attackSpeed")).floatField(EditorI18n.t("Скорость атаки"), d.attackSpeed, v -> d.attackSpeed = v);
                rst(resVal("durability")).intField(EditorI18n.t("Прочность"), d.durability, v -> d.durability = v);
                if (!"sword".equals(tool)) {
                    rst(resVal("miningSpeed")).floatField(EditorI18n.t("Скорость копания"), d.miningSpeed, v -> d.miningSpeed = v);
                    rst(resVal("miningLevel")).intField(EditorI18n.t("Уровень добычи"), d.miningLevel, v -> d.miningLevel = v);
                }
            } else if (!armor.isBlank()) {
                rst(resVal("armorTexture")).assetCombo("armor_texture", d.armorTexture, EditorAssetIndex.textures(), texSearch,
                        v -> d.armorTexture = v);
                boolean showArmor = d.armorModel != null ? d.armorModel
                        : d.armorTexture != null && !d.armorTexture.isBlank();
                rst(resVal("armorModel")).checkbox(EditorI18n.t("Модель брони на теле"), showArmor, v -> d.armorModel = v);
                rst(resVal("armorDefense")).intField(EditorI18n.t("Защита"), d.armorDefense, v -> d.armorDefense = v);
                rst(resVal("durability")).intField(EditorI18n.t("Прочность"), d.durability, v -> d.durability = v);
                rst(resVal("armorToughness")).floatField(EditorI18n.t("Твёрдость"), d.armorToughness, v -> d.armorToughness = v);
                rst(resVal("armorKnockback")).floatField(EditorI18n.t("Отбрасывание"), d.armorKnockback, v -> d.armorKnockback = v);
            } else {
                rst(resVal("damage")).floatField(EditorI18n.t("Урон (опц.)"), d.damage, v -> d.damage = v);
                rst(resVal("durability")).intField(EditorI18n.t("Прочность (опц.)"), d.durability, v -> d.durability = v);
            }
            EditorInspectorUi.endSection();
        }

        if ("geo".equals(look) && EditorInspectorUi.beginSection("res-item-geo", "Geo transforms", false)) {
            rst(resVal("handFirst")).stringField(EditorI18n.t("1 лицо (hand_first)"), d.handFirst, v -> d.handFirst = v);
            rst(resVal("handThird")).stringField(EditorI18n.t("3 лицо (hand_third)"), d.handThird, v -> d.handThird = v);
            rst(resVal("handGround")).stringField(EditorI18n.t("На земле"), d.handGround, v -> d.handGround = v);
            rst(resVal("gui")).stringField(EditorI18n.t("GUI / иконка"), d.gui, v -> d.gui = v);
            EditorInspectorUi.hint("ox, oy, oz, rx, ry, rz, scale");
            EditorInspectorUi.endSection();
        }
    }

    private static String normalizeItemLook(String kind) {
        if (kind == null) return "flat";
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "geo", "3d", "bedrock", "model" -> "geo";
            default -> "flat";
        };
    }

    private void renderTab(SceneNode node) {
        if (node.tabDef == null) node.tabDef = new SceneTabDef();
        SceneTabDef d = node.tabDef;
        d.id = node.id;
        if (EditorInspectorUi.beginSection("res-tab", EditorI18n.t("Вкладка"), true)) {
            rst(resVal("displayName")).stringField(EditorI18n.t("Название"), d.displayName, v -> d.displayName = v);
            tabEditor.render(d);
            EditorInspectorUi.hint(EditorI18n.t("Порядок слотов = порядок в креатив-вкладке после перезапуска"));
            EditorInspectorUi.endSection();
        }
    }

    private void renderDrop(SceneNode node) {
        if (node.dropDef == null) node.dropDef = new SceneDropDef();
        SceneDropDef d = node.dropDef;
        d.id = node.id;
        d.ensureDrops();
        if (EditorInspectorUi.beginSection("res-drop", EditorI18n.t("Дроп"), true)) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Цель дропа"));
            String targetShown = d.isMob() ? EditorI18n.t("Моб / сущность") : EditorI18n.t("Блок");
            if (ImGui.beginCombo("##drop-target", targetShown)) {
                if (ImGui.selectable(EditorI18n.t("Блок"), !d.isMob())) {
                    SceneEditorSession.pushUndo();
                    d.target = "block";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Моб / сущность"), d.isMob())) {
                    SceneEditorSession.pushUndo();
                    d.target = "mob";
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }
            EditorInspectorUi.hint(EditorI18n.t("Как create drop · block = \"…\" или mob = \"…\""));

            if (d.isMob()) {
                itemField(EditorI18n.t("Моб (источник)"), d.mob, EditorItemIcons.mobs(), v -> d.mob = v);
            } else {
                itemField(EditorI18n.t("Блок (источник)"), d.block, EditorItemIcons.blocks(), v -> d.block = v);
            }

            rst(resVal("replace")).checkbox(EditorI18n.t("Отключить базовый дроп"), d.replace, v -> d.replace = v);
            EditorInspectorUi.hint(d.isMob()
                    ? EditorI18n.t("Да = очистить ванильный лут моба, оставить только правила ниже")
                    : EditorI18n.t("Да = блок не дропает ванильные предметы, только правила ниже"));

            boolean allowSelf = !d.isMob();
            dropRulesEditor.renderPanel(d.drops, allowSelf, d.xp, v -> d.xp = v);
            rst(resVal("nbt")).stringField(EditorI18n.t("NBT (опц., на все)"), d.nbt, v -> d.nbt = v);
            EditorInspectorUi.endSection();
        }
    }

    private Object resVal(String field) {
        if (resFactory == null || field == null) return null;
        try {
            return resFactory.getClass().getField(field).get(resFactory);
        } catch (Exception e) {
            return null;
        }
    }

    private ResourceInspector rst(Object defaultValue) {
        hasResetDefault = true;
        resetDefault = defaultValue;
        return this;
    }

    private static boolean resEq(Object current, Object def) {
        if (current instanceof Number cn && def instanceof Number dn) {
            return Math.abs(cn.doubleValue() - dn.doubleValue()) < 1e-4;
        }
        if (current instanceof Boolean || def instanceof Boolean) {
            boolean a = Boolean.TRUE.equals(current);
            boolean b = Boolean.TRUE.equals(def);
            return a == b;
        }
        String as = current == null ? "" : String.valueOf(current);
        String bs = def == null ? "" : String.valueOf(def);
        return as.equals(bs);
    }

    private void consumeReset(Object current, Consumer<Object> apply) {
        if (!hasResetDefault) return;
        hasResetDefault = false;
        Object def = resetDefault;
        resetDefault = null;
        EditorInspectorUi.bindReset(!resEq(current, def), () -> {
            SceneEditorSession.pushUndo();
            apply.accept(def);
            SceneEditorSession.markDirty();
        });
        EditorInspectorUi.bindClipboard(current, v -> {
            SceneEditorSession.pushUndo();
            apply.accept(v);
            SceneEditorSession.markDirty();
        });
    }

    private void checkbox(String label, boolean current, Consumer<Boolean> setter) {
        consumeReset(current, v -> setter.accept(v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v))));
        if (EditorInspectorUi.checkbox(label, current)) {
            SceneEditorSession.pushUndo();
            setter.accept(!current);
            SceneEditorSession.markDirty();
        }
    }

    private void dragFloat(String label, float current, float speed, float min, float max, Consumer<Float> setter) {
        consumeReset(current, v -> setter.accept(v instanceof Number n ? n.floatValue() : current));
        single[0] = current;
        if (EditorInspectorUi.dragFloat(label, "rf-" + label, single, speed, min, max)) {
            setter.accept(single[0]);
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void dragInt(String label, int current, int min, int max, Consumer<Integer> setter) {
        consumeReset(current, v -> setter.accept(v instanceof Number n ? n.intValue() : current));
        intBuf[0] = current;
        EditorInspectorUi.fieldLabel(label);
        ImGui.setNextItemWidth(-1f);
        if (ImGui.dragInt("##ri-" + label, intBuf, 1, min, max)) {
            setter.accept(intBuf[0]);
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();
    }

    private void stringField(String label, String current, Consumer<String> setter) {
        consumeReset(current, v -> setter.accept(v == null ? "" : String.valueOf(v)));
        strBuf.set(current == null ? "" : current);
        if (EditorInspectorUi.inputText(label, "rs-" + label, strBuf)) {
            setter.accept(strBuf.get());
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void floatField(String label, Float current, Consumer<Float> setter) {
        consumeReset(current, v -> setter.accept(v instanceof Number n ? n.floatValue() : current));
        single[0] = current == null ? 0f : current;
        if (EditorInspectorUi.dragFloat(label, "rff-" + label, single, 0.05f, -100f, 100f)) {
            setter.accept(single[0]);
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void intField(String label, Integer current, Consumer<Integer> setter) {
        consumeReset(current, v -> setter.accept(v instanceof Number n ? n.intValue() : current));
        intBuf[0] = current == null ? 0 : current;
        EditorInspectorUi.fieldLabel(label);
        ImGui.setNextItemWidth(-1f);
        if (ImGui.dragInt("##rif-" + label, intBuf, 1, 0, 100_000)) {
            setter.accept(intBuf[0]);
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();
    }

    private void itemField(String label, String current, Consumer<String> setter) {
        EditorItemPicker.combo(label, current, setter);
    }

    private void itemField(String label, String current, List<String> options, Consumer<String> setter) {
        EditorItemPicker.combo(label, current, options, true, setter);
    }

    private void assetCombo(String label, String current, List<String> options,
                            ImString search, Consumer<String> setter) {
        boolean texturePreview = options == EditorAssetIndex.textures();
        String shown = current == null || current.isEmpty() ? EditorI18n.t("(не выбрано)") : current;
        consumeReset(current, v -> setter.accept(v == null ? "" : String.valueOf(v)));
        EditorInspectorUi.fieldLabel(label);
        if (ImGui.beginCombo("##ac-" + label, shown)) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.08f, 0.10f, 0.14f, 1f);
            ImGui.inputTextWithHint("##as-" + label, EditorI18n.t("Поиск…"), search);
            ImGui.popStyleColor();
            String filter = search.get().toLowerCase(Locale.ROOT);
            if (ImGui.selectable(EditorI18n.t("(не выбрано)"), shown.equals(EditorI18n.t("(не выбрано)")))) {
                SceneEditorSession.pushUndo();
                setter.accept("");
                SceneEditorSession.markDirty();
            }
            for (String option : options) {
                if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
                if (texturePreview) {
                    var tex = EditorTexturePreview.cached(option);
                    if (tex != null) {
                        ImGui.image(tex.glId(), 18f, 18f);
                        ImGui.sameLine();
                    } else {
                        ImGui.dummy(18f, 18f);
                        ImGui.sameLine();
                    }
                }
                if (ImGui.selectable(option, option.equals(current))) {
                    SceneEditorSession.pushUndo();
                    setter.accept(option);
                    SceneEditorSession.markDirty();
                }
                if (texturePreview && ImGui.isItemHovered()) {
                    var tex = EditorTexturePreview.get(option);
                    if (tex != null) {
                        ImGui.beginTooltip();
                        float[] size = EditorTexturePreview.fit(tex, 192f, 192f);
                        ImGui.image(tex.glId(), size[0], size[1]);
                        ImGui.textDisabled(tex.width() + " x " + tex.height());
                        ImGui.endTooltip();
                    }
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.finishClipboardField();
        if (texturePreview && current != null && !current.isEmpty()) {
            var tex = EditorTexturePreview.get(current);
            if (tex != null) {
                float[] size = EditorTexturePreview.fit(tex, 96f, 96f);
                ImGui.image(tex.glId(), size[0], size[1]);
                ImGui.sameLine();
                ImGui.textDisabled(tex.width() + " x " + tex.height());
            }
        }
    }

    /** Called when node id is renamed in the main inspector. */
    public static void onIdRenamed(SceneNode node) {
        SceneNodeIds.syncResourceDefId(node);
    }
}
