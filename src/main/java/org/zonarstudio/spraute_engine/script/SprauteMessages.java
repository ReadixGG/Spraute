package org.zonarstudio.spraute_engine.script;

import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine UI / script error strings for server chat and the editor console.
 * Russian literals are the canonical stored form; English comes from {@code editor_en.json}.
 */
public final class SprauteMessages {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Pattern COMPILE =
            Pattern.compile("^Ошибка компиляции '([^']*)': (.*)$", Pattern.DOTALL);
    private static final Pattern RUNTIME =
            Pattern.compile("^Ошибка выполнения( \\(строка (\\d+)\\))?: (.*)$", Pattern.DOTALL);
    private static final Pattern SCRIPT_NOT_FOUND =
            Pattern.compile("^Скрипт не найден: '([^']*)'$");
    private static final Pattern KEYBIND_HANDLER =
            Pattern.compile("^Обработчик keybind '([^']*)': (.*)$", Pattern.DOTALL);
    private static final Pattern CREATE_UI_FAIL =
            Pattern.compile("^create ui '([^']*)' не создан: (.*) — переменная '([^']*)' не определена$", Pattern.DOTALL);

    private static final List<String> DYNAMIC_PREFIXES = List.of(
            "Не удалось просканировать scripts: ",
            "Не удалось скопировать: ",
            "Не удалось создать папку: ",
            "Не удалось запустить: ",
            "Не удалось запустить (конфликт instance?)",
            "Импортировано файлов: ",
            "Пропуск (вложенность): ",
            "Папка вне проекта: ",
            "Загружено скриптов: ",
            "ScriptManager не инициализирован",
            "Ничего не импортировано",
            "Пустой код",
            "Нет соединения с сервером",
            "Скрипт не найден: '",
            "Ошибка компиляции '",
            "Ошибка выполнения"
    );

    private static Map<String, String> catalog = Map.of();
    private static Map<String, String> reverse = Map.of();
    private static boolean catalogLoaded;

    private SprauteMessages() {}

    public static boolean isEnglish(CommandSourceStack source) {
        if (source != null) {
            try {
                ServerPlayer player = source.getPlayer();
                if (player != null) return ScriptLangCatalog.isEnglish(player);
            } catch (Throwable ignored) {
            }
        }
        return isEnglishClient();
    }

    public static boolean isEnglishClient() {
        try {
            Class<?> c = Class.forName("org.zonarstudio.spraute_engine.client.editor.EditorI18n");
            Object v = c.getMethod("english").invoke(null);
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String ui(String russian, boolean english) {
        if (russian == null || russian.isEmpty()) return russian;
        ensureCatalog();
        if (!english) return russian;
        String en = catalog.get(russian);
        return en != null ? en : russian;
    }

    public static String uiClient(String russian) {
        return ui(russian, isEnglishClient());
    }

    /** Localize a console line stored with Russian wrappers and English script details. */
    public static String localizeConsole(String stored, boolean english) {
        if (stored == null || stored.isEmpty()) return stored;

        Matcher compile = COMPILE.matcher(stored);
        if (compile.matches()) {
            return ui("Ошибка компиляции '", english) + compile.group(1)
                    + "': " + ScriptErrorI18n.localize(compile.group(2), english);
        }

        Matcher runtime = RUNTIME.matcher(stored);
        if (runtime.matches()) {
            String where = runtime.group(1) == null ? "" : localizeRuntimeWhere(runtime.group(2), english);
            return ui("Ошибка выполнения", english) + where + ": "
                    + ScriptErrorI18n.localize(runtime.group(3), english);
        }

        Matcher notFound = SCRIPT_NOT_FOUND.matcher(stored);
        if (notFound.matches()) {
            return ui("Скрипт не найден: '", english) + notFound.group(1) + "'";
        }

        Matcher keybind = KEYBIND_HANDLER.matcher(stored);
        if (keybind.matches()) {
            String prefix = english
                    ? "Keybind handler '" + keybind.group(1) + "': "
                    : "Обработчик keybind '" + keybind.group(1) + "': ";
            return prefix + ScriptErrorI18n.localize(keybind.group(2), english);
        }

        Matcher createUi = CREATE_UI_FAIL.matcher(stored);
        if (createUi.matches()) {
            if (english) {
                return "create ui '" + createUi.group(1) + "' failed: "
                        + ScriptErrorI18n.localize(createUi.group(2), english)
                        + " — variable '" + createUi.group(3) + "' is not defined";
            }
            return stored;
        }

        if (stored.startsWith("Загружено скриптов: ")) {
            if (stored.contains(", с ошибками компиляции: ")) {
                String[] parts = stored.split(", с ошибками компиляции: ", 2);
                String head = ui("Загружено скриптов: ", english) + parts[0].substring("Загружено скриптов: ".length());
                return head + ui(", с ошибками компиляции: ", english) + parts[1];
            }
            return ui("Загружено скриптов: ", english) + stored.substring("Загружено скриптов: ".length());
        }

        return applyDynamicPrefix(stored, english);
    }

    public static String localizeConsoleClient(String stored) {
        return localizeConsole(stored, isEnglishClient());
    }

    public static String chatCompileError(String scriptName, String detailEn, boolean english) {
        String detail = ScriptErrorI18n.localize(detailEn, english);
        if (english) {
            return "§c[Spraute] §fCompilation error '" + scriptName + "': §e" + detail;
        }
        return "§c[Spraute] §fОшибка компиляции '" + scriptName + "': §e" + detail;
    }

    public static String chatRuntimeError(String scriptName, int line, String detailEn, boolean english) {
        String detail = ScriptErrorI18n.localize(detailEn, english);
        if (english) {
            String where = line >= 0 ? " at line " + line : "";
            return "§c[Spraute] Script '" + scriptName + "' error" + where + ": §e" + detail;
        }
        String where = line >= 0 ? " (строка " + line + ")" : "";
        return "§c[Spraute] Ошибка выполнения '" + scriptName + "'" + where + ": §e" + detail;
    }

    public static String chatScriptNotFound(String name, boolean english) {
        if (english) return "§c[Spraute] §fScript not found: '" + name + "'";
        return "§c[Spraute] §fСкрипт не найден: '" + name + "'";
    }

    public static String chatRunFailed(String detail, boolean english) {
        String prefix = ui("Не удалось запустить: ", english);
        return "§c[Spraute] §f" + prefix + detail;
    }

    private static String localizeRuntimeWhere(String line, boolean english) {
        if (english) return " (line " + line + ")";
        return " (строка " + line + ")";
    }

    private static String applyDynamicPrefix(String text, boolean english) {
        List<String> sorted = DYNAMIC_PREFIXES.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        for (String ruPrefix : sorted) {
            String enPrefix = catalog.getOrDefault(ruPrefix, ruPrefix);
            if (english) {
                if (text.startsWith(ruPrefix)) {
                    String tail = text.substring(ruPrefix.length());
                    return enPrefix + ScriptErrorI18n.localize(tail, true);
                }
            } else if (text.startsWith(enPrefix) && !enPrefix.equals(ruPrefix)) {
                return ruPrefix + ScriptErrorI18n.localize(text.substring(enPrefix.length()), false);
            }
        }
        if (english) {
            String en = catalog.get(text);
            if (en != null) return en;
        } else {
            String ru = reverse.get(text);
            if (ru != null) return ru;
        }
        return text;
    }

    private static void ensureCatalog() {
        if (catalogLoaded) return;
        catalogLoaded = true;
        try (InputStream in = SprauteMessages.class.getResourceAsStream("/assets/spraute_engine/lang/editor_en.json")) {
            if (in == null) {
                LOGGER.warn("[Spraute Engine] editor_en.json missing for SprauteMessages");
                return;
            }
            Map<String, String> parsed = parseCatalog(in);
            catalog = Collections.unmodifiableMap(parsed);
            Map<String, String> rev = new HashMap<>();
            for (var e : parsed.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                    rev.putIfAbsent(e.getValue(), e.getKey());
                }
            }
            reverse = Collections.unmodifiableMap(rev);
        } catch (Throwable t) {
            LOGGER.error("[Spraute Engine] Failed to load editor_en.json for SprauteMessages: {}", t.toString());
        }
    }

    private static Map<String, String> parseCatalog(InputStream in) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                result.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
        }
        return result;
    }

    /** Invalidate after resource reload (client). */
    public static void invalidateCatalog() {
        catalogLoaded = false;
        catalog = Map.of();
        reverse = Map.of();
    }
}
