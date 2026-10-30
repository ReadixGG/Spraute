"""Versions of the NPC rig of another build: the same bones and the same skin layout, other proportions.

python tools/npc_rig_variant.py <child | big> <rig.geo.json> <animations.json> <out.geo.json> <out.animation.json>

child: a big head on a small body, 23 pixels tall. big: wide shoulders, heavy arms and a small head, 39 pixels tall, for a boss.
Skins and clips are shared with the grown rig: cube sizes change, texture coordinates do not. Clips rotate bones the same
way, but their position keys are in pixels of the grown body, so they are scaled to the new one in the second file.
"""
import json
import sys

ARMS = ('LeftArm', 'LeftArmKnee', 'LeftHandItem', 'RightArm', 'RightArmKnee', 'RightHandItem')
FACE = ('Head', 'Eyes', 'LEyeBG', 'REye', 'REyeBG', 'LEye', 'Brows', 'LBrow', 'RBrow', 'Mouth', 'SleepMouth', 'Teeth')

PROFILES = {
    # hip and neck heights; widths and depths of the head, the torso and the legs; arm from the torso and its width per pixel
    'child': {'hip': 7.5, 'neck': 16, 'head': .875, 'torso': .75, 'arm_at': .75, 'arm': .7, 'depth': .75, 'keys': .65, 'bounds': (2.5, .9)},
    'big': {'hip': 16, 'neck': 31, 'head': 1.0, 'torso': 1.4, 'arm_at': 1.4, 'arm': 1.5, 'depth': 1.35, 'keys': 1.31, 'bounds': (4.5, 1.6)},
}


def new_y(k, y):
    if y <= 12: return y * k['hip'] / 12
    if y <= 24: return k['hip'] + (y - 12) * (k['neck'] - k['hip']) / 12
    return k['neck'] + (y - 24) * k['head']


def new_x(k, bone, x):
    if bone in FACE: return x * k['head']
    if bone in ARMS:
        s = 1 if x >= 0 else -1
        ax = abs(x)
        return s * (ax * k['arm_at'] if ax <= 4 else 4 * k['arm_at'] + (ax - 4) * k['arm'])
    return x * k['torso']


def new_z(k, bone, z):
    return z * (k['head'] if bone in FACE else k['depth'])


def point(k, bone, p):
    return [round(new_x(k, bone, p[0]), 4), round(new_y(k, p[1]), 4), round(new_z(k, bone, p[2]), 4)]


def rig(k, name, src, out):
    g = json.load(open(src, encoding='utf8'))
    d = g['minecraft:geometry'][0]
    d['description']['identifier'] = 'geometry.npc_' + name
    d['description']['visible_bounds_height'] = k['bounds'][0]
    d['description']['visible_bounds_offset'] = [0, k['bounds'][1], 0]
    for b in d['bones']:
        bone = b['name']
        if 'pivot' in b:
            b['pivot'] = point(k, bone, b['pivot'])
        for c in b.get('cubes', []):
            lo = c['origin']
            hi = [lo[i] + c['size'][i] for i in range(3)]
            a, z = point(k, bone, lo), point(k, bone, hi)
            c['origin'] = a
            c['size'] = [round(z[i] - a[i], 4) for i in range(3)]
            if 'inflate' in c:
                c['inflate'] = round(c['inflate'] * (k['head'] if bone in FACE else k['depth']), 4)
    json.dump(g, open(out, 'w', encoding='utf8', newline='\n'), ensure_ascii=False, indent='\t')


def scale(entry, f):
    if isinstance(entry, list):
        return [x * f if isinstance(x, (int, float)) else x for x in entry]
    out = dict(entry)
    for field in ('pre', 'post', 'vector'):
        if field in out: out[field] = scale(out[field], f)
    return out


def animations(k, src, out):
    data = json.load(open(src, encoding='utf8'))
    for clip in data['animations'].values():
        for bone, channels in clip.get('bones', {}).items():
            keys = channels.get('position')
            if keys is None:
                continue
            f = k['head'] if bone in FACE else k['keys']
            if isinstance(keys, dict) and not any(x in keys for x in ('pre', 'post', 'vector')):
                channels['position'] = {t: scale(v, f) for t, v in keys.items()}
            else:
                channels['position'] = scale(keys, f)
    json.dump(data, open(out, 'w', encoding='utf8', newline='\n'), ensure_ascii=False, indent='\t')


if __name__ == '__main__':
    profile = PROFILES[sys.argv[1]]
    rig(profile, sys.argv[1], sys.argv[2], sys.argv[4])
    animations(profile, sys.argv[3], sys.argv[5])
