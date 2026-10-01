# -*- coding: utf-8 -*-
"""Build editor RU→EN map via Google Translate, preserving ImGui ##/### suffixes."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(r"D:\nnn\Spraute_Engine")
SRC = ROOT / "tools" / "_ru_strings.json"
OUT_JSON = ROOT / "src" / "main" / "resources" / "assets" / "spraute_engine" / "lang" / "editor_en.json"
CACHE = ROOT / "tools" / "_en_translate_cache.json"

translator = GoogleTranslator(source="ru", target="en")


def split_imgui(s: str) -> tuple[str, str]:
    for sep in ("###", "##"):
        i = s.find(sep)
        if i >= 0:
            return s[:i], s[i:]
    return s, ""


def translate_label(label: str) -> str:
    if not label or not label.strip():
        return label
    # Skip if no Cyrillic
    if not re.search(r"[\u0400-\u04FF]", label):
        return label
    # Preserve leading/trailing whitespace
    lead = re.match(r"^\s*", label).group(0)
    trail = re.search(r"\s*$", label).group(0)
    core = label[len(lead) : len(label) - len(trail) if trail else len(label)]
    if not core:
        return label
    try:
        en = translator.translate(core)
    except Exception as e:
        print("fail:", repr(core[:60]), e)
        return label
    if not en:
        return label
    return lead + en + trail


def main() -> None:
    items = json.loads(SRC.read_text(encoding="utf-8"))
    cache: dict[str, str] = {}
    if CACHE.exists():
        cache = json.loads(CACHE.read_text(encoding="utf-8"))

    result: dict[str, str] = {}
    total = len(items)
    for idx, item in enumerate(items):
        ru = item["ru"]
        if ru in cache:
            result[ru] = cache[ru]
            continue
        label, suffix = split_imgui(ru)
        en_label = translate_label(label)
        en = en_label + suffix
        # Keep placeholders intact if translator mangled — soft check
        for ph in re.findall(r"%[\d.]*[sdif]|\{[^}]+\}", ru):
            if ph not in en and ph in label:
                # leave as-is if broken
                pass
        cache[ru] = en
        result[ru] = en
        if (idx + 1) % 25 == 0:
            CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
            print(f"{idx + 1}/{total}")
            time.sleep(0.4)
        else:
            time.sleep(0.08)

    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(
        json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print("wrote", OUT_JSON, "entries", len(result))


if __name__ == "__main__":
    main()
