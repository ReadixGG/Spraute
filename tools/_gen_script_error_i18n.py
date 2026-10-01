# -*- coding: utf-8 -*-
"""Generate ScriptErrorI18n.java EN→RU map from parser/compiler messages."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(r"D:\nnn\Spraute_Engine")
SCRIPT = ROOT / "src/main/java/org/zonarstudio/spraute_engine/script"
OUT = SCRIPT / "ScriptErrorI18n.java"

# Hand translations for common / template prefixes (exact EN → RU)
EXACT: dict[str, str] = {
    "Invalid assignment target": "Недопустимая цель присваивания",
    "Unterminated string literal": "Незакрытая строковая константа",
    "Expected property name after '.' (got EOF)": "Ожидалось имя свойства после '.' (конец файла)",
    "Expected function or method call after 'await'": "Ожидался вызов функции или метода после 'await'",
    "Expected function call after 'await'": "Ожидался вызов функции после 'await'",
    "Expected script name after 'import'": "Ожидалось имя скрипта после 'import'",
    "Expected 'val' or 'var' after 'global' or 'world'": "Ожидалось 'val' или 'var' после 'global' или 'world'",
    "Expected 'npc', 'model', 'hitbox', 'empty', 'particleEmitter', 'light', 'ui', 'command', custom type, or a static data block after 'create'":
        "Ожидалось 'npc', 'model', 'hitbox', 'empty', 'particleEmitter', 'light', 'ui', 'command', свой тип или блок данных после 'create'",
    "stop_task() requires task id": "stop_task() требует id задачи",
    "await time() requires seconds": "await time() требует секунды",
    "range() requires at least 1 argument": "range() требует хотя бы 1 аргумент",
}

# Prefix templates: EN prefix → RU prefix (dynamic suffix kept)
PREFIXES: list[tuple[str, str]] = [
    ("Expected expression, found ", "Ожидалось выражение, найдено "),
    ("Expected property name after '.' (got ", "Ожидалось имя свойства после '.' (получено "),
    ("Unknown await trigger: ", "Неизвестный триггер await: "),
    ("Expected '(', '=', or '.' after '", "Ожидалось '(', '=' или '.' после '"),
    ("Expected '(', '=', or '{' after '", "Ожидалось '(', '=' или '{' после '"),
    ("Expected '{' to start ", "Ожидалось '{' для начала блока "),
    ("Expected identifier after 'create ", "Ожидался идентификатор после 'create "),
    ("Line ", "Строка "),
]

# Auto-translate Remaining "Expected …" with simple rules
def polish_expected_rest(rest: str) -> str:
    rest = rest.replace(" after ", " после ")
    rest = rest.replace(" to start ", " для начала ")
    rest = rest.replace(" to close ", " для закрытия ")
    rest = rest.replace(" in ", " в ")
    rest = rest.replace(" for ", " для ")
    rest = rest.replace(" or ", " или ")
    rest = rest.replace(" (got ", " (получено ")
    rest = rest.replace(" (got EOF)", " (конец файла)")
    return rest


def auto_ru(en: str) -> str:
    if en in EXACT:
        return EXACT[en]
    m = re.match(r"^Expected (.+)$", en)
    if m:
        return "Ожидалось " + polish_expected_rest(m.group(1))
    m = re.match(r"^await (\w+)\((.*)\) requires (.+)$", en)
    if m:
        need = m.group(3)
        need = need.replace("at least ", "как минимум ")
        need = need.replace("player and ", "игрока и ")
        need = need.replace("entity id", "id сущности")
        need = need.replace("key name", "имя клавиши")
        need = need.replace("task id", "id задачи")
        need = need.replace("seconds", "секунды")
        need = need.replace("killer filter", "фильтр убийцы")
        need = need.replace("projectile id", "id снаряда")
        need = need.replace("arguments", "аргументов")
        return f"await {m.group(1)}({m.group(2)}) требует {need}"
    if en.startswith("await ") and " requires " in en:
        a, b = en.split(" requires ", 1)
        return f"{a} требует {b}"
    if " requires " in en:
        a, b = en.split(" requires ", 1)
        return f"{a} требует {b}"
    return en


def collect_messages() -> set[str]:
    pat = re.compile(r'(?:ScriptException|expect)\([^;]*?"((?:\\.|[^"\\])*)"')
    msgs: set[str] = set()
    for f in SCRIPT.rglob("*.java"):
        if f.name == "ScriptErrorI18n.java":
            continue
        t = f.read_text(encoding="utf-8")
        for m in pat.finditer(t):
            s = m.group(1).encode("utf-8").decode("unicode_escape") if "\\" in m.group(1) else m.group(1)
            # only fully static strings (no dangling incomplete)
            if s and not s.endswith("'") or s.endswith("'"):
                msgs.add(m.group(1).replace("\\\"", '"').replace("\\n", "\n"))
    return msgs


def java_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


def main() -> None:
    msgs = collect_messages()
    exact_map: dict[str, str] = {}
    for en in sorted(msgs):
        # skip incomplete fragments used only as prefixes in code
        if en in ("Expected expression, found ", "Expected property name after '.' (got ",
                  "Unknown await trigger: ", "Expected '(', '=', or '.' after '",
                  "Expected '(', '=', or '{' after '", "Expected '{' to start ",
                  "Expected identifier after 'create "):
            continue
        ru = auto_ru(en)
        if ru != en:
            exact_map[en] = ru
        elif en in EXACT:
            exact_map[en] = EXACT[en]

    exact_map.update(EXACT)

    lines = [
        "package org.zonarstudio.spraute_engine.script;",
        "",
        "import java.util.LinkedHashMap;",
        "import java.util.Map;",
        "",
        "/**",
        " * Localize script parser/compiler error details for the editor console.",
        " * Source messages stay English in code; UI language picks RU/EN wording.",
        " */",
        "public final class ScriptErrorI18n {",
        "    private static final Map<String, String> EN_TO_RU = new LinkedHashMap<>();",
        "    private static final String[][] PREFIX_EN_RU = {",
    ]
    for en, ru in PREFIXES:
        lines.append(f'            {{"{java_escape(en)}", "{java_escape(ru)}"}},')
    lines += [
        "    };",
        "",
        "    static {",
    ]
    for en, ru in sorted(exact_map.items(), key=lambda x: (-len(x[0]), x[0])):
        lines.append(f'        EN_TO_RU.put("{java_escape(en)}", "{java_escape(ru)}");')
    lines += [
        "    }",
        "",
        "    private ScriptErrorI18n() {}",
        "",
        "    /** True when Minecraft client language is English. Safe off-thread / dedicated. */",
        "    public static boolean englishUi() {",
        "        try {",
        "            Class<?> c = Class.forName(\"org.zonarstudio.spraute_engine.client.editor.EditorI18n\");",
        "            Object v = c.getMethod(\"english\").invoke(null);",
        "            return Boolean.TRUE.equals(v);",
        "        } catch (Throwable ignored) {",
        "            return false;",
        "        }",
        "    }",
        "",
        "    /** Localize a compile/runtime detail for the current UI language. */",
        "    public static String localize(String message) {",
        "        if (message == null || message.isEmpty()) return message;",
        "        if (englishUi()) return toEnglish(message);",
        "        return toRussian(message);",
        "    }",
        "",
        "    public static String toRussian(String message) {",
        "        if (message == null || message.isEmpty()) return message;",
        "        String exact = EN_TO_RU.get(message);",
        "        if (exact != null) return exact;",
        "        for (String[] pair : PREFIX_EN_RU) {",
        "            String en = pair[0];",
        "            String ru = pair[1];",
        "            if (message.startsWith(en)) {",
        "                return ru + message.substring(en.length());",
        "            }",
        "        }",
        "        // Reverse whole-line wrappers that already mix RU + EN detail",
        "        return message;",
        "    }",
        "",
        "    public static String toEnglish(String message) {",
        "        if (message == null || message.isEmpty()) return message;",
        "        // Reverse exact RU → EN",
        "        for (var e : EN_TO_RU.entrySet()) {",
        "            if (e.getValue().equals(message)) return e.getKey();",
        "        }",
        "        for (String[] pair : PREFIX_EN_RU) {",
        "            String en = pair[0];",
        "            String ru = pair[1];",
        "            if (message.startsWith(ru)) {",
        "                return en + message.substring(ru.length());",
        "            }",
        "        }",
        "        return message;",
        "    }",
        "}",
        "",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("wrote", OUT, "exact", len(exact_map), "prefixes", len(PREFIXES))


if __name__ == "__main__":
    main()
