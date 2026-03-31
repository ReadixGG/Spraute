import json

path = r'run/spraute_engine/animations/npc_classic.animation.json'
with open(path, 'r', encoding='utf-8') as f:
    data = json.load(f)

def fix_kf(obj):
    if isinstance(obj, dict):
        if 'post' in obj and 'pre' not in obj:
            obj['vector'] = obj.pop('post')
        for k, v in obj.items():
            fix_kf(v)
    elif isinstance(obj, list):
        for item in obj:
            fix_kf(item)
            print("fixed")

fix_kf(data)

with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent='\t')
