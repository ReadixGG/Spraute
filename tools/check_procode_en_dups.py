import json
import re
from collections import Counter
from pathlib import Path

p = Path(r"D:\nnn\Spraute_Engine\app\bundled_plugins\procode\lang\en.json")
text = p.read_text(encoding="utf-8")
keys = re.findall(r'^\s*"((?:\\.|[^"\\])*)"\s*:', text, re.M)
dups = [(k, v) for k, v in Counter(keys).items() if v > 1]
out = Path(__file__).resolve().parent / "procode_en_dups.txt"
lines = [f"duplicate count {len(dups)}"]
for k, v in sorted(dups, key=lambda x: -x[1]):
    lines.append(f"{v}\t{k}")
out.write_text("\n".join(lines), encoding="utf-8")
print("wrote", out)
try:
    json.loads(text)
    print("python json ok")
except Exception as e:
    print("python json error:", e)
