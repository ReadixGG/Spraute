package org.sprautestudio.st_addon_spraute.editor.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The pictures the engine draws with when a setting has no texture of its own — Spraute's interface
 * (journal, shop, chat history, speech icons…) and Minecraft's HUD sheets — packed into one archive, sorted
 * into folders by the engine settings' sections and named like their fields, with their size in the name,
 * so new textures can be drawn over the real ones.
 */
public final class TextureExport {
    private TextureExport() {
    }

    /** Spraute's own files that fields of the engine settings use: path under textures/gui/ → folder/name. */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        String q = "Журнал квестов/";
        NAMES.put("quests/bg_2.png", q + "Список");
        NAMES.put("quests/bg.png", q + "Карточка");
        NAMES.put("quests/button.png", q + "Кнопка квеста — обычная, наведена, выбрана (сверху вниз)");
        NAMES.put("quests/quest_task_line.png", q + "Полоса задачи (и плашка уведомления квеста)");
        NAMES.put("quests/categories.png", q + "Значки — текущие, выполненные, проваленные (сверху обычные, снизу наведённые)");
        NAMES.put("quests/quest_line.png", q + "Разделитель");
        NAMES.put("quests/quests_current.png", q + "Вкладка «Текущие»");
        NAMES.put("quests/quests_completed.png", q + "Вкладка «Выполненные»");
        NAMES.put("quests/quests_failed.png", q + "Вкладка «Проваленные»");
        String s = "Магазин/";
        NAMES.put("shop/base_1.png", s + "Список товаров");
        NAMES.put("shop/base_2.png", s + "Карточка");
        NAMES.put("shop/product_cell.png", s + "Ячейка");
        NAMES.put("shop/product_cell_glow.png", s + "Ячейка выбрана");
        NAMES.put("shop/buy_button.png", s + "Кнопка");
        NAMES.put("shop/buy_button_light.png", s + "Кнопка наведена");
        NAMES.put("shop/lot_locked.png", s + "Ячейка — закрыто");
        NAMES.put("shop/lot_sold.png", s + "Ячейка — продано");
        NAMES.put("shop/slider.png", s + "Ползунок");
        String d = "Окно выбора/";
        NAMES.put("dialog/dialog_shadow.png", d + "Панель");
        NAMES.put("dialog/dialog_name.png", d + "Полоска под именем");
        NAMES.put("dialog/answer_top.png", d + "Кнопка — верх");
        NAMES.put("dialog/answer_mid.png", d + "Кнопка — середина");
        NAMES.put("dialog/answer_bottom.png", d + "Кнопка — низ");
        NAMES.put("dialog/answer_top_selected.png", d + "Кнопка наведена — верх");
        NAMES.put("dialog/answer_mid_selected.png", d + "Кнопка наведена — середина");
        NAMES.put("dialog/answer_bottom_selected.png", d + "Кнопка наведена — низ");
        String c = "История чата/";
        NAMES.put("chatlog/bg.png", c + "Фон");
        NAMES.put("chatlog/bgframe.png", c + "Рамка");
    }

    private static final String README = """
            Текстуры движка — какими их рисуют Spraute и Minecraft, если в настройках движка поле текстуры пустое.
            Папки — как разделы настроек движка, имена файлов — как поля, в скобках размер картинки.

            Как сделать свою: нарисуйте поверх такой же картинки, положите её в папку проекта images/
            и выберите в поле настроек движка с тем же именем. Картинки, где несколько состояний друг под другом
            (кнопки, значки, вкладки), сохраняйте в той же раскладке; их можно рисовать крупнее — в 2 или 4 раза.

            Журнал квестов/ ... «Журнал квестов» и «Уведомление квеста»
            Магазин/ .......... «Окно магазина»
            Окно выбора/ ...... «Меню выбора» (кнопка там из трёх частей; в поле «Кнопка» — одной картинкой)
            История чата/ ..... «История чата»
            Иконки говорящих/ . иконки окна речи (имя файла — это имя иконки)
            HUD Minecraft/ .... обычные сердца, опыт, хотбар — для стилей «как в Minecraft»
            Прочее Spraute/ и Spraute/ — остальной интерфейс, как он лежит в модах, на случай если пригодится.
            """;

    /** Width × height from a PNG's header, or "" when it cannot be read. */
    private static String size(byte[] png) {
        if (png.length < 24 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return "";
        int w = ((png[16] & 255) << 24) | ((png[17] & 255) << 16) | ((png[18] & 255) << 8) | (png[19] & 255);
        int h = ((png[20] & 255) << 24) | ((png[21] & 255) << 16) | ((png[22] & 255) << 8) | (png[23] & 255);
        return " (" + w + "×" + h + ")";
    }

    /** Where a texture goes in the archive, without the size and the extension. */
    private static String place(ResourceLocation rl) {
        String rel = rl.getPath().substring("textures/gui/".length());
        if (rl.getNamespace().equals("minecraft")) {
            return switch (rel) {
                case "icons.png" -> "HUD Minecraft/Сердца, броня, еда, опыт (icons)";
                case "widgets.png" -> "HUD Minecraft/Хотбар и кнопки (widgets)";
                case "bars.png" -> "HUD Minecraft/Полосы боссов (bars)";
                default -> "HUD Minecraft/" + rel.replace(".png", "");
            };
        }
        if (rl.getNamespace().equals("st_addon_spraute")) return "Spraute/" + rel.replace(".png", "");
        String named = NAMES.get(rel);
        if (named != null) return named;
        if (rel.startsWith("shop/arrow_")) {
            // arrow_-5_glow.png → «Стрелка −5 наведена», как поле настроек
            String step = rel.substring(11).replace(".png", "");
            boolean glow = step.endsWith("_glow");
            if (glow) step = step.substring(0, step.length() - 5);
            if (!step.startsWith("-")) step = "+" + step;
            return "Магазин/Стрелка " + step.replace('-', '−') + (glow ? " наведена" : "");
        }
        if (rel.startsWith("chat/") && rel.indexOf('/', 5) < 0) return "Иконки говорящих/" + rel.substring(5).replace(".png", "");
        return "Прочее Spraute/" + rel.replace(".png", "");
    }

    /** Writes the archive into the project's folder and returns its path. */
    public static Path export(Path projectDir) throws IOException {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Map<String, ResourceLocation> found = new TreeMap<>();
        for (ResourceLocation rl : rm.listResources("textures/gui", r -> r.getPath().endsWith(".png")
                && (r.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID) || r.getNamespace().equals("st_addon_spraute"))).keySet()) {
            found.put(rl.toString(), rl);
        }
        for (String vanilla : new String[]{"icons.png", "widgets.png", "bars.png"}) {
            ResourceLocation rl = new ResourceLocation("minecraft", "textures/gui/" + vanilla);
            found.put(rl.toString(), rl);
        }
        Path out = projectDir.resolve("текстуры_движка.zip");
        java.util.Set<String> used = new java.util.HashSet<>();
        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("ЧИТАЙ_МЕНЯ.txt"));
            zip.write(README.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (ResourceLocation rl : found.values()) {
                java.util.Optional<Resource> res = rm.getResource(rl);
                if (res.isEmpty()) continue;
                byte[] png;
                try (InputStream in = res.get().open()) {
                    png = in.readAllBytes();
                }
                String name = place(rl) + size(png) + ".png";
                if (!used.add(name)) continue;
                zip.putNextEntry(new ZipEntry(name));
                zip.write(png);
                zip.closeEntry();
            }
        }
        return out;
    }
}
