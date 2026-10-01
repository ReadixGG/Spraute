# -*- coding: utf-8 -*-
import json
from pathlib import Path

p = Path(r"D:\nnn\Spraute_Engine\src\main\resources\assets\spraute_engine\lang\editor_en.json")
m = json.loads(p.read_text(encoding="utf-8"))
ru = """                block: my_block          — id блока
                category: Моя категория  — раздел палитры
                color: #38bdf8           — цвет
                shape: statement | value | wrapper

                row: \"текст\" (поле: text: значение) [выбор: dropdown(да: true, нет: false)]
                input: player (type: value)     — сокет для другого блока
                if поле == \"значение\":          — условные строки

                template: chat(\"{поле}\")        — генерация кода
                code: return '...';             — либо JS-генератор"""
en = """                block: my_block          — block id
                category: My category    — palette section
                color: #38bdf8           — color
                shape: statement | value | wrapper

                row: \"text\" (field: text: value) [choice: dropdown(yes: true, no: false)]
                input: player (type: value)     — socket for another block
                if field == \"value\":            — conditional rows

                template: chat(\"{field}\")       — code generation
                code: return '...';             — or a JS generator"""
m[ru] = en
p.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("ok", len(m))
