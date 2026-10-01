# -*- coding: utf-8 -*-
"""Build lang/en.json for bundled plugins. Uses cache + Google Translate."""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(r"D:\nnn\Spraute_Engine\app\bundled_plugins")
CACHE = Path(r"D:\nnn\Spraute_Engine\tools\_plugin_en_cache.json")
CYR = re.compile(r"[\u0400-\u04FF]")
STR = re.compile(r'"((?:\\.|[^"\\])*)"')
DROP = re.compile(r"dropdown\(([^)]*)\)")
CAT_LINE = re.compile(r"(?m)^\s*(?:#\\)?\s*category:\s*(.+?)\s*$")

# Hand-tuned common UI terms (override MT)
OVERRIDES = {
    "НПС": "NPC",
    "НПС Группы": "NPC Groups",
    "События": "Events",
    "Ожидание": "Waiting",
    "Асинхронность": "Async",
    "Игрок": "Player",
    "Инвентарь": "Inventory",
    "Мир": "World",
    "Измерения": "Dimensions",
    "Переменные": "Variables",
    "Логика": "Logic",
    "Циклы": "Loops",
    "Математика": "Math",
    "Текст": "Text",
    "Списки": "Lists",
    "Словари": "Dictionaries",
    "Функции": "Functions",
    "Действия": "Actions",
    "Интерфейс": "UI",
    "Диалоги": "Dialogs",
    "Камера": "Camera",
    "Частицы": "Particles",
    "Снаряды": "Projectiles",
    "Экономика": "Economy",
    "Квесты": "Quests",
    "Структуры": "Structures",
    "Иерархия": "Hierarchy",
    "сек.": "sec.",
    "сек": "sec",
    "да": "yes",
    "нет": "no",
    "и": "and",
    "или": "or",
    "не": "not",
    "правда": "true",
    "ложь": "false",
    "мир": "world",
    # Resources / scene create (partial EN was leaving these in Russian)
    "Ресурсы": "Resources",
    "создать блок": "create block",
    "создать предмет": "create item",
    "создать свет": "create light",
    "создать empty": "create empty",
    "создать пустую ноду": "create empty node",
    "создать хитбокс": "create hitbox",
    "создать эмиттер частиц": "create particle emitter",
    "родитель": "parent",
    "уровень 0–15": "level 0–15",
    "свет 0–15": "light 0–15",
    "включён": "enabled",
    "прочность ломания": "break hardness",
    "прочность": "durability",
    "инструмент": "tool",
    "мин. уровень": "min. level",
    "макс. стак": "max stack",
    "необяз.": "opt.",
    "необязательно": "optional",
    "отключить базовый дроп": "disable base drop",
    "сетка 3×3": "3×3 grid",
    "вкладка креатива": "creative tab",
    "одна на все": "one for all",
    "верх и бока": "top & sides",
    "все грани": "all faces",
    "твёрдость": "hardness",
    "твёрдый": "solid",
    "триггер": "trigger",
    "шир.": "W",
    "выс.": "H",
    "глуб.": "D",
    "рысканье": "yaw",
    "тангаж": "pitch",
    "результат": "result",
    "предметы": "items",
    "по одному id на строку": "one id per line",
    "пустой слот — оставить пустым": "empty slot — leave blank",
    "опыт": "XP",
    "форма": "shape",
    "цикл": "loop",
    "вылет": "emit",
    "объём спавна": "spawn volume",
    "разброс%": "spread%",
    "гравитация": "gravity",
    "конус°": "cone°",
    "жизнь тики": "lifetime ticks",
    "направление": "direction",
    "уровень добычи": "mining level",
    "скорость атаки": "attack speed",
    "текстура брони": "armor texture",
    "плоская текстура": "flat texture",
    "3D-модель": "3D model",
    "geo-модель": "geo model",
    "GUI / иконка": "GUI / icon",
    "1 лицо": "1st person",
    "3 лицо": "3rd person",
    "на земле": "on ground",
    "сам_блок": "self",
    "ничего": "none",
    "без формы": "shapeless",
    "печь": "furnace",
    "плавильня": "blast furnace",
    "коптильня": "smoker",
    "костёр": "campfire",
    "тиков": "ticks",
    "время": "time",
    "ингредиент": "ingredient",
    "ингредиенты": "ingredients",
    "защита": "defense",
    "отбрасывание": "knockback",
    "как у алмаза 2": "like diamond 2",
    "0=дерево … 3=алмаз": "0=wood … 3=diamond",
    "попыток/чанк": "tries/chunk",
    "жила": "vein",
    "руда": "ore",
    "бока": "sides",
    "верх": "top",
    "низ": "bottom",
    "север": "north",
    "юг": "south",
    "запад": "west",
    "восток": "east",
    "запасная": "fallback",
    "интервал": "interval",
    "точка": "point",
    "линия": "line",
    "шар": "sphere",
    "спираль": "spiral",
    "в_одну_сторону": "directional",
    "взрыв": "burst",
    "меч": "sword",
    "кирка": "pickaxe",
    "топор": "axe",
    "лопата": "shovel",
    "мотыга": "hoe",
    "шлем": "helmet",
    "нагрудник": "chestplate",
    "поножи": "leggings",
    "ботинки": "boots",
}

translator = GoogleTranslator(source="ru", target="en")


def collect_plugin(plugin: Path) -> set[str]:
    found: set[str] = set()
    pj = plugin / "plugin.json"
    if pj.is_file():
        meta = json.loads(pj.read_text(encoding="utf-8"))
        for k in ("description", "name"):
            v = meta.get(k)
            if isinstance(v, str) and CYR.search(v):
                found.add(v)
    cj = plugin / "categories.json"
    if cj.is_file():
        cats = json.loads(cj.read_text(encoding="utf-8"))
        for key, val in cats.items():
            if CYR.search(key):
                found.add(key)
            if isinstance(val, dict):
                note = val.get("note")
                if isinstance(note, str) and CYR.search(note):
                    found.add(note)
    blocks = plugin / "blocks"
    if blocks.is_dir():
        for f in blocks.glob("*.spr"):
            text = f.read_text(encoding="utf-8")
            for m in STR.finditer(text):
                s = m.group(1).replace("\\n", "\n").replace('\\"', '"')
                if CYR.search(s):
                    found.add(s)
            for m in CAT_LINE.finditer(text):
                cat = m.group(1).strip().strip('"')
                if CYR.search(cat):
                    found.add(cat)
            for m in DROP.finditer(text):
                for part in m.group(1).split(","):
                    if ":" not in part:
                        continue
                    lab = part.split(":", 1)[0].strip()
                    if CYR.search(lab):
                        found.add(lab)
    return found


def translate(s: str, cache: dict[str, str]) -> str:
    if s in OVERRIDES:
        return OVERRIDES[s]
    if s in cache:
        return cache[s]
    if not CYR.search(s):
        return s
    try:
        en = translator.translate(s)
        time.sleep(0.05)
    except Exception as e:
        print("fail", repr(s[:50]), e, flush=True)
        return s
    cache[s] = en or s
    return cache[s]


def build(plugin_name: str, cache: dict[str, str]) -> None:
    plugin = ROOT / plugin_name
    strings = sorted(collect_plugin(plugin), key=lambda s: (len(s), s))
    print(plugin_name, "strings", len(strings), flush=True)
    out: dict[str, str] = {}
    pj = plugin / "plugin.json"
    if pj.is_file():
        meta = json.loads(pj.read_text(encoding="utf-8"))
        name = meta.get("name") or plugin_name
        desc = meta.get("description") or ""
        out["plugin.name"] = name if not CYR.search(str(name)) else translate(str(name), cache)
        if desc:
            out["plugin.description"] = translate(desc, cache) if CYR.search(desc) else desc
    for i, s in enumerate(strings):
        out[s] = translate(s, cache)
        if (i + 1) % 50 == 0:
            CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
            print(f"  {i+1}/{len(strings)}", flush=True)
    lang = plugin / "lang"
    lang.mkdir(exist_ok=True)
    (lang / "en.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
    print("wrote", lang / "en.json", len(out), flush=True)


def main() -> None:
    cache: dict[str, str] = {}
    if CACHE.exists():
        cache = json.loads(CACHE.read_text(encoding="utf-8"))
    names = sys.argv[1:] or ["WChoice", "Raya", "NpcRelations", "procode"]
    for n in names:
        build(n, cache)


if __name__ == "__main__":
    main()
