package org.zonarstudio.spraute_engine.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.locale.Language;

import java.util.List;
import java.util.Locale;

/**
 * Editor / engine UI strings follow the Minecraft language setting.
 * Russian source literals stay in code; English comes from {@link EditorI18nCatalog}.
 * Plugins are not covered here — see {@link org.zonarstudio.spraute_engine.client.editor.visual.PluginI18n}.
 */
public final class EditorI18n {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static Boolean loggedLangOnce;

    private EditorI18n() {}

    /** Call on resource reload / language change so the next probe logs again. */
    public static void invalidateLanguageCache() {
        loggedLangOnce = null;
        EditorI18nCatalog.invalidate();
        org.zonarstudio.spraute_engine.script.SprauteMessages.invalidateCatalog();
    }
    /** Error / status prefixes that may have a dynamic suffix (id, path, exception). */
    private static final List<String> DYNAMIC_PREFIXES = List.of(
            "⚠ Не найдены блоки (показаны красным): ",
            "⚠ устаревший блок: ",
            "// тип «",
            "Ошибка чтения: ",
            "Ошибка сохранения: ",
            "Ошибка записи: ",
            "Ошибка записи размера: ",
            "Ошибка экспорта: ",
            "Ошибка: ",
            "Ошибка компиляции '",
            "Скрипт не найден: '",
            "Загружено скриптов: ",
            "Не удалось открыть сцену: ",
            "Не удалось открыть ",
            "Не удалось прочитать сцену: ",
            "Не удалось сохранить: ",
            "Не удалось создать файл: ",
            "Не удалось открыть папку: ",
            "Не удалось открыть диалог: ",
            "Не удалось загрузить модель",
            "Не удалось загрузить текстуру",
            "Не удалось создать папку: ",
            "Не удалось скопировать: ",
            "Плагин не найден: ",
            "Модель не найдена: ",
            "Файл сцены не найден: ",
            "Укажите ID мода",
            "Для ZIP нужен JAR Spraute Engine в папке mods",
            "В мод: ",
            "Файл скрипта не найден: scripts/",
            "Файл не найден: scripts/",
            "Не найдено на диске: ",
            "Уже существует: ",
            "Не удалось переместить: ",
            "Папка вне проекта: ",
            "Пропуск (вложенность): ",
            "Импортировано файлов: ",
            "Не удалось получить список с сервера: ",
            "Не удалось записать на сервер: ",
            "Не удалось скачать: ",
            "Не удалось прочитать для отправки: ",
            "Ошибка проводника: ",
            "Файл слишком большой для сервера: ",
            "Файл уже есть",
            "Сцена уже есть",
            "Скрипт уже есть",
            "Имя пустое",
            "Ключ уже есть: ",
            "Язык уже есть: ",
            "Скопировано: ",
            "Корень проекта удалить нельзя",
            "Нужен файл .ogg или .mp3: ",
            "Не удалось скопировать аудио: ",
            "Аудио импортировано: "
    );

    /** Sandwich patterns: prefix + dynamic middle + suffix (all Russian keys in catalog). */
    private static final String[][] SANDWICHES = {
            {"// тип «", "» не найден — обновите блок"},
            {"// тип «", "» не найден — добавь SCRIPT с declare node type в эту сцену и сохрани .spr"},
    };

    /** True when the active Minecraft language is English (en_*). */
    public static boolean english() {
        boolean en = detectEnglish();
        if (loggedLangOnce == null || loggedLangOnce != en) {
            loggedLangOnce = en;
            LOGGER.info("[Spraute Engine] Editor language: {} (code={})",
                    en ? "English" : "Russian/other", selectedLanguageCode());
        }
        return en;
    }

    private static boolean detectEnglish() {
        try {
            String code = selectedLanguageCode();
            if (isEnglishCode(code)) return true;

            // Fallback: probe a vanilla menu string (works even if LanguageManager is odd).
            String single = Language.getInstance().getOrDefault("menu.singleplayer");
            if ("Singleplayer".equals(single)) return true;
        } catch (Throwable t) {
            LOGGER.warn("[Spraute Engine] Editor language detect failed: {}", t.toString());
        }
        return false;
    }

    private static String selectedLanguageCode() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return "";
            LanguageManager lm = mc.getLanguageManager();
            if (lm == null) return "";
            //? if >=1.20.1 {
            String code = lm.getSelected();
            //?} else {
            /*var selected = lm.getSelected();
            String code = selected != null ? selected.getCode() : "";
            *///?}
            return code != null ? code : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isEnglishCode(String code) {
        if (code == null || code.isBlank()) return false;
        String c = code.toLowerCase(Locale.ROOT).replace('-', '_');
        // en, en_us, en_gb, minecraft:en_gb, etc.
        if (c.startsWith("en_") || c.equals("en")) return true;
        int colon = c.indexOf(':');
        if (colon >= 0) {
            String after = c.substring(colon + 1);
            return after.startsWith("en_") || after.equals("en");
        }
        return false;
    }

    /** Translate a Russian UI literal when Minecraft language is English. */
    public static String t(String russian) {
        if (russian == null || russian.isEmpty()) return russian;
        if (!english()) return russian;
        String en = EditorI18nCatalog.translate(russian);
        return en != null ? en : russian;
    }

    /**
     * Localize any editor UI / error string for the current Minecraft language.
     * Supports exact keys, dynamic prefix+suffix errors, and reversing English
     * that was baked while the client was on English.
     */
    public static String localize(String text) {
        if (text == null || text.isEmpty()) return text;
        if (english()) {
            String en = EditorI18nCatalog.translate(text);
            if (en != null) return en;
            String via = applySandwich(text, true);
            if (via != null) return via;
            return applyPrefix(text, true);
        }
        String ru = EditorI18nCatalog.reverse(text);
        if (ru != null) return ru;
        String via = applySandwich(text, false);
        if (via != null) return via;
        return applyPrefix(text, false);
    }

    /** Convenience for formatted strings: {@code t("Точек: %s", n)}. */
    public static String t(String russianFmt, Object... args) {
        String fmt = t(russianFmt);
        if (args == null || args.length == 0) return fmt;
        try {
            return String.format(Locale.ROOT, fmt, args);
        } catch (Throwable ignored) {
            return fmt;
        }
    }

    private static String applyPrefix(String text, boolean toEnglish) {
        // Longest prefix first
        List<String> sorted = DYNAMIC_PREFIXES.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        for (String ruPrefix : sorted) {
            String enPrefix = EditorI18nCatalog.translate(ruPrefix);
            if (enPrefix == null) enPrefix = ruPrefix;
            if (toEnglish) {
                if (text.startsWith(ruPrefix)) {
                    return enPrefix + text.substring(ruPrefix.length());
                }
            } else if (text.startsWith(enPrefix)) {
                return ruPrefix + text.substring(enPrefix.length());
            }
        }
        return text;
    }

    private static String applySandwich(String text, boolean toEnglish) {
        for (String[] parts : SANDWICHES) {
            String ruA = parts[0];
            String ruB = parts[1];
            String enA = EditorI18nCatalog.translate(ruA);
            String enB = EditorI18nCatalog.translate(ruB);
            if (enA == null) enA = ruA;
            if (enB == null) enB = ruB;
            if (toEnglish) {
                if (text.startsWith(ruA) && text.endsWith(ruB)
                        && text.length() >= ruA.length() + ruB.length()) {
                    return enA + text.substring(ruA.length(), text.length() - ruB.length()) + enB;
                }
            } else if (text.startsWith(enA) && text.endsWith(enB)
                    && text.length() >= enA.length() + enB.length()) {
                return ruA + text.substring(enA.length(), text.length() - enB.length()) + ruB;
            }
        }
        return null;
    }
}
