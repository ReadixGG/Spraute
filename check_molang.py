import json

def check_molang():
    file_path = 'run/spraute_engine/animations/npc_classic.animation.json'
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    def walk(obj, path=""):
        if isinstance(obj, dict):
            for k, v in obj.items():
                walk(v, path + "." + str(k) if path else str(k))
        elif isinstance(obj, list):
            for i, v in enumerate(obj):
                walk(v, path + f"[{i}]")
        elif isinstance(obj, str):
            # Ignore standard format version and lerp modes
            if obj not in ("1.8.0", "catmullrom", "linear", "step"):
                print(f"{path}: {repr(obj)}")

    walk(data)

if __name__ == "__main__":
    check_molang()
