# -*- coding: utf-8 -*-
import re
import os
import json

root = r"D:\nnn\Spraute_Engine\src\main\java\org\zonarstudio\spraute_engine"
base = os.path.join(root, "client", "editor")
cyr = re.compile(r"[\u0400-\u04FF]")
str_re = re.compile(r'"((?:\\.|[^\\"])*)"')
found = {}

for dp, dns, fns in os.walk(base):
    for fn in fns:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(dp, fn)
        rel = os.path.relpath(path, root).replace("\\", "/")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        for m in str_re.finditer(text):
            s = m.group(1)
            # keep escaped form as in Java source for later replace
            if cyr.search(s) and s.strip():
                found.setdefault(s, []).append(rel)

out = os.path.join(r"D:\nnn\Spraute_Engine\tools", "_ru_strings.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(
        [{"ru": k, "files": v, "len": len(k)} for k, v in sorted(found.items(), key=lambda x: (len(x[0]), x[0]))],
        f,
        ensure_ascii=False,
        indent=2,
    )
print(f"unique={len(found)} written={out}")
