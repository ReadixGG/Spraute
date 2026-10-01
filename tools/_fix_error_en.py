# -*- coding: utf-8 -*-
import json
from pathlib import Path

p = Path(r"D:\nnn\Spraute_Engine\src\main\resources\assets\spraute_engine\lang\editor_en.json")
m = json.loads(p.read_text(encoding="utf-8"))
fixes = {
    "⚠ Не найдены блоки (показаны красным): ": "⚠ No blocks found (shown in red): ",
    "⚠ устаревший блок: ": "⚠ obsolete block: ",
    "// тип «": '// type "',
    "» не найден — обновите блок": '" not found — update the block',
    "» не найден — добавь SCRIPT с declare node type в эту сцену и сохрани .spr":
        '" not found — add a SCRIPT with declare node type to this scene and save .spr',
    "Ошибка чтения: ": "Read error: ",
    "Ошибка сохранения: ": "Save error: ",
    "Ошибка записи: ": "Write error: ",
    "Ошибка записи размера: ": "Size write error: ",
    "Ошибка экспорта: ": "Export error: ",
    "Ошибка: ": "Error: ",
    "Не удалось открыть сцену: ": "Failed to open scene: ",
    "Не удалось открыть ": "Failed to open ",
    "Не удалось прочитать сцену: ": "Failed to read scene: ",
    "Не удалось сохранить: ": "Failed to save: ",
    "Не удалось создать файл: ": "Failed to create file: ",
    "Не удалось открыть папку: ": "Failed to open folder: ",
    "Не удалось открыть диалог: ": "Failed to open dialog: ",
    "Не удалось загрузить модель": "Failed to load model",
    "Не удалось загрузить текстуру": "Failed to load texture",
    "Плагин не найден: ": "Plugin not found: ",
    "Модель не найдена: ": "Model not found: ",
    "Файл сцены не найден: ": "Scene file not found: ",
    "Не найдено на диске: ": "Not found on disk: ",
    "Уже существует: ": "Already exists: ",
}
m.update(fixes)
p.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("updated", len(fixes), "total", len(m))
