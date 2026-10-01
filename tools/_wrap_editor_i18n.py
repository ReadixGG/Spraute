# -*- coding: utf-8 -*-
"""Wrap Cyrillic string literals in editor Java sources with EditorI18n.t(...).
Skips Java text blocks (\"\"\") and case-label contexts are still wrapped —
re-check switch cases manually after running.
"""
from __future__ import annotations

import re
from pathlib import Path

EDITOR_ROOT = Path(r"D:\nnn\Spraute_Engine\src\main\java\org\zonarstudio\spraute_engine\client\editor")
IMPORT_LINE = "import org.zonarstudio.spraute_engine.client.editor.EditorI18n;"
SKIP = {"EditorI18n.java", "EditorI18nCatalog.java"}
CYR = re.compile(r"[\u0400-\u04FF]")
STR = re.compile(r'"((?:\\.|[^"\\])*)"')
TEXT_BLOCK = re.compile(r'"""')


def mask_text_blocks(text: str) -> tuple[str, list[str]]:
    """Replace \"\"\"...\"\"\" regions with placeholders so inner quotes are not wrapped."""
    parts: list[str] = []
    out = []
    i = 0
    while True:
        start = text.find('"""', i)
        if start < 0:
            out.append(text[i:])
            break
        out.append(text[i:start])
        end = text.find('"""', start + 3)
        if end < 0:
            out.append(text[start:])
            break
        end += 3
        parts.append(text[start:end])
        out.append(f"__TEXTBLOCK_{len(parts)-1}__")
        i = end
    return "".join(out), parts


def wrap_content(text: str) -> tuple[str, int]:
    masked, blocks = mask_text_blocks(text)
    out = []
    i = 0
    count = 0
    while i < len(masked):
        m = STR.search(masked, i)
        if not m:
            out.append(masked[i:])
            break
        out.append(masked[i : m.start()])
        raw = m.group(0)
        inner = m.group(1)
        before = masked[max(0, m.start() - 40) : m.start()].rstrip()
        already = before.endswith("EditorI18n.t(")
        # Do not wrap switch case labels: `case "…"` must stay constant
        case_ctx = re.search(r"\bcase\s*$", before) is not None
        if CYR.search(inner) and not already and not case_ctx:
            out.append(f"EditorI18n.t({raw})")
            count += 1
        else:
            out.append(raw)
        i = m.end()
    result = "".join(out)
    for idx, block in enumerate(blocks):
        result = result.replace(f"__TEXTBLOCK_{idx}__", block)
    return result, count


def ensure_import(path: Path, text: str) -> str:
    if path.parent == EDITOR_ROOT:
        return text
    if IMPORT_LINE in text or "EditorI18n.t(" not in text:
        return text
    lines = text.splitlines(keepends=True)
    insert_at = 0
    last_import = -1
    for li, line in enumerate(lines):
        if line.startswith("package "):
            insert_at = li + 1
        elif line.startswith("import "):
            last_import = li
    if last_import >= 0:
        insert_at = last_import + 1
    lines.insert(insert_at, IMPORT_LINE + "\n")
    return "".join(lines)


def main() -> None:
    total = 0
    files = 0
    for path in sorted(EDITOR_ROOT.rglob("*.java")):
        if path.name in SKIP:
            continue
        original = path.read_text(encoding="utf-8")
        new_text, n = wrap_content(original)
        if n == 0:
            continue
        new_text = ensure_import(path, new_text)
        path.write_text(new_text, encoding="utf-8")
        files += 1
        total += n
        print(f"{n:4d}  {path.relative_to(EDITOR_ROOT)}")
    print(f"wrapped {total} strings in {files} files")


if __name__ == "__main__":
    main()
