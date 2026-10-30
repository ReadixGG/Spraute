"""Shades a flat Minecraft skin the way a skin artist does. The window is in skin_shader_app.py; this is also a command:

python tools/skin_shader.py <skin.png | folder> [-o out.png | folder] [--strength .65] [--folds .88] [--grade 1] [--hue-shift 2.5] [--saturation .76] [--seed N]
                            [--no-hair-tips] [--skin-base <file | folder>] [--preview sheet.png]

Needs: pip install pillow numpy

Works on the standard skin layout in the top-left 64x64 of the texture; the rest of the file and alpha are left alone.
There is no noise and no pillow shading here. Tones are whole steps, placed as shapes a body really has:
  * form maps: the chest, the hollow of the neck, the nose, the roll of a sleeve, drawn by hand per side of a part;
  * fold strokes: a shadow line with a lit edge, going out from where cloth is pulled (armpit, elbow, knee, belt);
  * contact shadows: what lies above a border of colour drops a hard shadow on what is under it;
  * hair: locks with a gap between them, a shine in the upper part, dark tips, a jagged hairline;
  * small shapes on cloth (belts, cuffs, ties, collars) are treated as bands with a lit edge, without folds;
  * the shades a skin already has (a lighter pixel every other pixel) stay: a step of tone is applied to each pixel's
    own colour, and only a sharp change of colour counts as a border.
Base colours are first brought to better ones (see grade; --grade 0 keeps them as drawn). Each is the middle of a ramp: shadows turn to blue-violet (skin: rose) and get richer, lights turn
to warm yellow (skin: peach) and get paler. See apply_tone; --hue-shift and --saturation set how far.
"""
import argparse
import hashlib
import os
import sys
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

DEFAULT_SKIN_BASE = r'D:\TG\Storytelling-1.20.1-20.1.33-patched\assets\storytelling\textures\entity\city_steve'

MATERIALS = ('skin', 'hair', 'cloth', 'band', 'trim', 'eye', 'keep')

# name, u, v, width, height, depth, group
PARTS = (
    ('head', 0, 0, 8, 8, 8, 'head'), ('hat', 32, 0, 8, 8, 8, 'head'),
    ('body', 16, 16, 8, 12, 4, 'body'), ('jacket', 16, 32, 8, 12, 4, 'body'),
    ('right_arm', 40, 16, 4, 12, 4, 'right_arm'), ('right_sleeve', 40, 32, 4, 12, 4, 'right_arm'),
    ('left_arm', 32, 48, 4, 12, 4, 'left_arm'), ('left_sleeve', 48, 48, 4, 12, 4, 'left_arm'),
    ('right_leg', 0, 16, 4, 12, 4, 'right_leg'), ('right_pants', 0, 32, 4, 12, 4, 'right_leg'),
    ('left_leg', 16, 48, 4, 12, 4, 'left_leg'), ('left_pants', 0, 48, 4, 12, 4, 'left_leg'),
)
OVERLAY_OF = {'hat': 'head', 'jacket': 'body', 'right_sleeve': 'right_arm', 'left_sleeve': 'left_arm', 'right_pants': 'right_leg', 'left_pants': 'left_leg'}

TONE_STEP = (.02, .04)  # how far a step of tone moves lightness: constant, and per lightness
# Where hues go, in Oklab degrees, and how far per step of tone
HUE_SHADOW, HUE_LIGHT = np.radians(285), np.radians(100)
HUE_SKIN_SHADOW, HUE_SKIN_LIGHT = np.radians(22), np.radians(80)
# Hues that work: wine, terracotta, ochre, olive, forest, steel, navy, plum
PRO_HUES = np.radians(np.array((20, 50, 85, 125, 155, 215, 262, 305), float))
SKIN_HUE = np.radians(52)
HUE_STEP, HUE_STEP_SKIN = np.radians(5), np.radians(4)
EDGE = .085       # Oklab distance that makes a border of forms; below it the difference is the material's own
SPECK = 2         # regions up to this many pixels are drawn on their surroundings, not forms of their own
SPECK_KEEP = .42  # unless they differ this much: a button or a stud stands out


@dataclass
class Options:
    strength: float = .65
    folds: float = .88
    hair_tips: bool = True
    skin_relief: float = 1.07
    grade: float = 1.0
    hue_shift: float = 2.5
    saturation: float = .76
    seed: int = 1
    # (x, y) -> material, painted by hand; wins over what is told from the colours
    materials: dict = field(default_factory=dict)


# ------------------------------------------------------------------------------------------------ colour
def srgb_to_oklab(rgb):
    c = rgb / 255.0
    c = np.where(c <= .04045, c / 12.92, ((c + .055) / 1.055) ** 2.4)
    l = .4122214708 * c[..., 0] + .5363325363 * c[..., 1] + .0514459929 * c[..., 2]
    m = .2119034982 * c[..., 0] + .6806995451 * c[..., 1] + .1073969566 * c[..., 2]
    s = .0883024619 * c[..., 0] + .2817188376 * c[..., 1] + .6299787005 * c[..., 2]
    l, m, s = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return np.stack((.2104542553 * l + .7936177850 * m - .0040720468 * s,
                     1.9779984951 * l - 2.4285922050 * m + .4505937099 * s,
                     .0259040371 * l + .7827717662 * m - .8086757660 * s), -1)


def oklab_to_srgb(lab):
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    l = (L + .3963377774 * a + .2158037573 * b) ** 3
    m = (L - .1055613458 * a - .0638541728 * b) ** 3
    s = (L - .0894841775 * a - 1.2914855480 * b) ** 3
    c = np.stack((4.0767416621 * l - 3.3077115913 * m + .2309699292 * s,
                  -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
                  -.0041960863 * l - .7034186147 * m + 1.7076147010 * s), -1)
    c = np.clip(c, 0, 1)
    c = np.where(c <= .0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - .055)
    return np.clip(np.round(c * 255), 0, 255).astype(np.uint8)


def grade(lab, kind_of, amount=1.0):
    """The base colour itself brought to what looks right on a skin: raw saturated colours calmed, hues moved towards
    good ones, pure black and white taken off the ends, skin brought towards a natural tone. It is smooth over colour,
    so neighbours stay neighbours and the shades drawn on the cloth stay. 'keep' is left alone."""
    if amount <= 0:
        return lab
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    skin = kind_of == 'skin'
    C, h = np.hypot(a, b), np.arctan2(b, a)
    cap = np.where(skin, .085, .12)
    C2 = cap * np.tanh(C / cap)
    gap = (PRO_HUES.reshape((-1,) + (1,) * h.ndim) - h + np.pi) % (2 * np.pi) - np.pi
    nearest = np.take_along_axis(gap, np.abs(gap).argmin(axis=0)[None], axis=0)[0]
    h2 = h + nearest * .5 * np.clip(C / .04, 0, 1)
    h2 = np.where(skin, h + ((SKIN_HUE - h + np.pi) % (2 * np.pi) - np.pi) * .6, h2)
    C2 = np.where(skin, C2 + (.07 - C2) * .5, C2)
    a2, b2 = C2 * np.cos(h2), C2 * np.sin(h2)
    # what has no colour of its own gets a little: dark goes cool, light goes warm
    grey = np.clip(1 - C / .025, 0, 1)
    a2 = a2 + grey * np.where(L < .5, .012 * np.cos(HUE_SHADOW), .007 * np.cos(HUE_LIGHT))
    b2 = b2 + grey * np.where(L < .5, .012 * np.sin(HUE_SHADOW), .007 * np.sin(HUE_LIGHT))
    # only the very ends move: a dark suit has to stay dark
    L2 = L + np.maximum(0, .1 * (1 - L / .3)) - np.maximum(0, L - .85) * .4
    done = np.stack((L2, a2, b2), -1)
    k = (np.where(kind_of == 'eye', .5, 1.0) * amount)[..., None]
    return np.where((kind_of == 'keep')[..., None], lab, lab + (done - lab) * k)


def toward(h, target, amount):
    """Hue h turned to target by the shorter way, by no more than amount."""
    d = (target - h + np.pi) % (2 * np.pi) - np.pi
    return np.sign(d) * np.minimum(np.abs(d), amount)


def apply_tone(lab, t, kind_of, hue_shift=1.0, saturation=1.0):
    """A colour ramp built on each pixel's own colour, which stays exactly itself at tone 0. Going down, the hue turns
    to blue-violet (skin: to rose) and saturation first rises, then fades near black. Going up, the hue turns to warm
    yellow (skin: to peach) and saturation falls. A colour with almost no hue of its own (grey, black, white) has
    nothing to turn, so it is tinted the same way."""
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    step = TONE_STEP[0] + TONE_STEP[1] * L
    shadow, light = np.maximum(-t, 0), np.maximum(t, 0)
    skin = kind_of == 'skin'
    C, h = np.hypot(a, b), np.arctan2(b, a)
    # a pale colour swings far in hue for a small change of colour, and beige would go pink and green
    turn = np.where(kind_of == 'eye', .25, 1.0) * np.clip(C / .09, .35, 1) * hue_shift
    h = h + toward(h, np.where(skin, HUE_SKIN_SHADOW, HUE_SHADOW), shadow * np.where(skin, HUE_STEP_SKIN, HUE_STEP) * turn)
    h = h + toward(h, np.where(skin, HUE_SKIN_LIGHT, HUE_LIGHT), light * np.where(skin, HUE_STEP_SKIN, HUE_STEP) * turn)
    rich = np.where(skin, .07, .13) * np.minimum(shadow, 2) - .06 * np.maximum(shadow - 2, 0) - .07 * light
    C = C * (1 + rich * saturation)
    grey = np.maximum(0, 1 - np.hypot(a, b) / .06) * hue_shift
    a2 = C * np.cos(h) + grey * (shadow * .0045 * np.cos(HUE_SHADOW) + light * .0035 * np.cos(HUE_LIGHT))
    b2 = C * np.sin(h) + grey * (shadow * .0045 * np.sin(HUE_SHADOW) + light * .0035 * np.sin(HUE_LIGHT))
    return oklab_to_srgb(np.stack((np.clip(L + t * step, .03, .985), a2, b2), -1))


def palette_of(image, opts, count=6, tones=(-3, -2, -1, 0, 1, 2)):
    """The most used colours of the skin with the ramps made from them: [(colour, material, [rgb per tone]), ...]."""
    a = np.asarray(image.convert('RGBA'))[:64, :64]
    colours, counts = np.unique(a[a[..., 3] > 128][:, :3], axis=0, return_counts=True)
    order = np.argsort(-counts)
    skin_ref = skin_reference(srgb_to_oklab(a[..., :3].astype(float)), a[..., 3])
    out = []
    for i in order[:count]:
        lab = srgb_to_oklab(colours[i].astype(float))
        kind = 'skin' if skin_ref is not None and np.linalg.norm((lab - skin_ref) * (1, 1.6, 1.6)) < .1 else 'cloth'
        cells = grade(np.tile(lab, (len(tones), 1)), np.full(len(tones), kind, dtype=object), opts.grade)
        ramp = apply_tone(cells, np.array(tones, float) * opts.strength, np.full(len(tones), kind, dtype=object), opts.hue_shift, opts.saturation)
        out.append((tuple(int(x) for x in colours[i]), kind, [tuple(int(x) for x in c) for c in ramp]))
    return out


# ------------------------------------------------------------------------------------------------ layout
class Patch:
    """A connected piece of a part: the four sides as one strip that closes on itself, or a cap on top or below."""

    def __init__(self, part, kind, tx, ty, wrap, faces):
        self.part, self.kind, self.tx, self.ty, self.wrap, self.faces = part, kind, tx, ty, wrap, faces
        self.h, self.w = tx.shape
        self.group = part[6]

    def x_of(self, face):
        """The strip column where that side starts."""
        hit = np.nonzero(self.faces[0] == face)[0]
        return int(hit[0]) if len(hit) else None


def patches_of(part):
    name, u, v, w, h, d, _ = part
    sides = np.arange(2 * d + 2 * w)
    faces = np.array(['right'] * d + ['front'] * w + ['left'] * d + ['back'] * w)
    tx, ty = np.meshgrid(u + sides, v + d + np.arange(h))
    yield Patch(part, 'sides', tx, ty, True, np.tile(faces, (h, 1)))
    for kind, x0 in (('top', u + d), ('bottom', u + d + w)):
        tx, ty = np.meshgrid(x0 + np.arange(w), v + np.arange(d))
        yield Patch(part, kind, tx, ty, False, np.full((d, w), kind))


def seed_of(*parts):
    return int(hashlib.md5('/'.join(str(p) for p in parts).encode()).hexdigest()[:8], 16)


# ------------------------------------------------------------------------------------------------ the drawn shapes
TONES = {'#': -3, '%': -2, '-': -1, '.': 0, '+': 1, '*': 2}


def tone_map(*rows):
    return np.array([[TONES[c] for c in r] for r in rows], float)


# The light is above and a little to the left of whoever looks at the front.
FORM = {
    ('body', 'front'): tone_map(
        '-.-%%-.-', '.+.--.+.', '.++..++-', '.++.+++-', '-.+..+.-', '.-....-.',
        '-..-...-', '-..--..-', '-.....--', '-.+..+.-', '-.+..+.-', '%-.-.-.%'),
    ('body', 'back'): tone_map(
        '-......-', '.+.--.+.', '.++..++-', '.+.-.++-', '.+.-..+-', '-..-...-',
        '-.....--', '--..-.--', '-.....-%', '-.+..+.-', '-.+..+.-', '%-.-.-.%'),
    ('body', 'right'): tone_map(
        '%--%', '-..-', '-..-', '--.-', '%-.-', '%-.-', '%-..', '%--.', '%-..', '-...', '-..-', '%--%'),
    ('body', 'left'): tone_map(
        '%--%', '-..-', '-..%', '-.--', '-.-%', '-.-%', '..-%', '.--%', '..-%', '...-', '-..-', '%--%'),
    ('head', 'front'): tone_map(
        '-++++.--', '-+++..--', '-.....--', '-.-..-.-', '-......-', '.+.+-.+-', '.++--.+-', '-......%'),
    ('head', 'right'): tone_map(
        '-......-', '-.+....-', '-.+...--', '..+..---', '.++..-.-', '.+...-.-', '.....---', '-..---%%'),
    ('head', 'left'): tone_map(
        '-......-', '-....+.-', '--...+.-', '---..+.-', '-.-..++.', '-.-...+.', '---.....', '%%---..-'),
    ('head', 'back'): tone_map(
        '-......-', '.+....+-', '.++...+-', '.+....--', '......--', '-.....--', '-.---.-%', '%-%%%-%%'),
}
SLEEVE = {
    'front': ('.++-', '.++-', '.+.-', '.+.-', '-..-', '%-.-', '-..%', '.+.-', '.+.-', '.+.-', '-..-', '%--%'),
    'back': ('.+.-', '.+.-', '.+.-', '-..-', '-..-', '%-.%', '-..-', '.+.-', '.+.-', '-..-', '-..-', '%--%'),
    'out': ('.+.-', '.+.-', '.+.-', '.+.-', '-..-', '--.-', '-..-', '.+.-', '.+.-', '.+.-', '-..-', '%--%'),
    'in': ('%--%', '%--%', '-.--', '-.-%', '-.-%', '%--%', '-.-%', '-.-%', '-.-%', '-.-%', '-.-%', '%%%%'),
}
TROUSER = {
    'front': ('--%-', '.+.-', '.+.-', '.+.-', '.+--', '-..-', '%.--', '.+.-', '.+.-', '.+--', '.+.-', '%-%%'),
    'back': ('-%--', '.+.-', '.+.-', '-.+-', '--.-', '%--%', '-.+-', '.+.-', '.+.-', '-+.-', '.+.-', '%%-%'),
    'out': ('--.-', '.+.-', '.+.-', '.+.-', '-..-', '-..-', '%--.', '.+.-', '.+.-', '.+.-', '.+.-', '%%--'),
    'in': ('%%%%', '%--%', '%-.%', '%-.%', '%-.%', '%--%', '%--%', '%-.%', '%-.%', '%-.%', '%-.%', '%%%%'),
}
for group, source in (('right_arm', SLEEVE), ('left_arm', SLEEVE), ('right_leg', TROUSER), ('left_leg', TROUSER)):
    out_side, in_side = ('right', 'left') if group.startswith('right') else ('left', 'right')
    FORM[(group, 'front')] = tone_map(*source['front'])
    FORM[(group, 'back')] = tone_map(*source['back'])
    FORM[(group, out_side)] = tone_map(*source['out'])
    FORM[(group, in_side)] = tone_map(*source['in'])
CAP = {'top': 1, 'bottom': -3}
# The fist of an arm in skin: the knuckles' bar across the side that looks forward.
FIST = {9: '....', 10: '.%%-', 11: '.--.'}


# (x0, y0, x1, y1, how likely, how dark): lines going out from where the cloth is pulled, in the coordinates of a side.
STROKES = {
    ('body', 'front'): ((0, 2, 2, 5, .9, -2), (7, 2, 5, 5, .9, -2), (3, 4, 2, 7, .5, -1), (5, 5, 4, 8, .4, -1), (1, 9, 1, 11, .8, -1),
                        (3, 8, 3, 11, .8, -2), (6, 8, 6, 11, .7, -1), (5, 9, 4, 11, .4, -1), (2, 7, 0, 8, .6, -1), (5, 7, 7, 8, .6, -1)),
    ('body', 'back'): ((1, 2, 3, 4, .9, -2), (6, 2, 4, 4, .9, -2), (3, 5, 3, 8, .7, -1), (1, 8, 1, 11, .7, -1), (5, 8, 5, 11, .8, -2), (2, 7, 0, 8, .5, -1)),
    ('body', 'right'): ((1, 2, 1, 5, .7, -1), (2, 7, 1, 10, .7, -1)),
    ('body', 'left'): ((2, 2, 2, 5, .7, -1), (1, 7, 2, 10, .7, -1)),
    ('arm', 'front'): ((3, 4, 1, 6, .9, -2), (0, 5, 2, 7, .8, -1), (1, 1, 1, 3, .5, -1), (2, 8, 2, 10, .6, -1)),
    ('arm', 'back'): ((0, 4, 2, 6, .8, -2), (3, 5, 1, 7, .6, -1), (2, 8, 2, 10, .5, -1)),
    ('arm', 'out'): ((0, 5, 3, 6, .9, -1), (2, 1, 2, 3, .5, -1), (1, 7, 1, 10, .6, -1)),
    ('arm', 'in'): ((0, 4, 3, 6, .7, -1),),
    ('leg', 'front'): ((3, 0, 1, 2, .8, -2), (0, 5, 2, 6, .9, -2), (3, 6, 1, 7, .7, -1), (2, 1, 2, 4, .6, -1), (1, 8, 1, 11, .8, -1), (3, 8, 2, 11, .5, -1)),
    ('leg', 'back'): ((0, 0, 2, 2, .7, -1), (3, 4, 1, 6, .8, -2), (1, 7, 2, 11, .7, -1)),
    ('leg', 'out'): ((3, 5, 1, 6, .7, -1), (2, 1, 1, 4, .6, -1), (2, 8, 2, 11, .7, -1)),
    ('leg', 'in'): ((1, 2, 1, 5, .5, -1), (2, 7, 2, 10, .5, -1)),
}


def line(x0, y0, x1, y1):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(int(round(x0 + (x1 - x0) * i / n)), int(round(y0 + (y1 - y0) * i / n))) for i in range(n + 1)]


def side_role(group, face):
    if group.startswith('right') and face in ('right', 'left'):
        return 'out' if face == 'right' else 'in'
    if group.startswith('left') and face in ('right', 'left'):
        return 'out' if face == 'left' else 'in'
    return face


def fold_strokes(patch, mask, seed, amount):
    """Each fold is a line of shadow with a lit edge on the side of the light."""
    t = np.zeros((patch.h, patch.w))
    if patch.kind != 'sides' or amount <= 0:
        return t
    kind = {'right_arm': 'arm', 'left_arm': 'arm', 'right_leg': 'leg', 'left_leg': 'leg'}.get(patch.group, patch.group)
    for face in ('front', 'back', 'right', 'left'):
        x_at = patch.x_of(face)
        strokes = STROKES.get((kind, side_role(patch.group, face) if kind != 'body' else face), ())
        r = np.random.RandomState((seed + seed_of(face)) & 0x7fffffff)
        width = int((patch.faces[0] == face).sum())
        for x0, y0, x1, y1, chance, dark in strokes:
            if r.random_sample() >= chance * min(amount, 1.3):
                continue
            jog = r.randint(-1, 2) if abs(x1 - x0) <= 1 else 0
            for x, y in line(x0 + jog, y0, x1 + jog, y1):
                if not (0 <= x < width and 0 <= y < patch.h):
                    continue
                sx = x_at + x
                if not mask[y, sx]:
                    continue
                t[y, sx] = min(t[y, sx], dark)
                if x > 0 and mask[y, sx - 1]:
                    t[y, sx - 1] = max(t[y, sx - 1], 1)
    return t


def locks_of(width, seed):
    r = np.random.RandomState(seed & 0x7fffffff)
    out, x = [], 0
    while x < width:
        w = int(r.choice((2, 2, 3)))
        if width - x - w == 1: w += 1
        out.append((x, min(x + w, width)))
        x += w
    return out


def hair_tones(patch, mask, seed):
    """Locks: each one a shape of its own, lit in the upper part, dark at the tip, with a gap to the next."""
    t = np.zeros((patch.h, patch.w))
    r = np.random.RandomState((seed + 3) & 0x7fffffff)
    if patch.kind == 'bottom':
        return t - 3
    for x0, x1 in locks_of(patch.w, seed):
        own = int(r.choice((-1, 0, 0, 1)))
        shine_at = r.uniform(.2, .45)
        for x in range(x0, x1):
            ys = np.nonzero(mask[:, x])[0]
            if len(ys) == 0:
                continue
            middle = (x0 + x1 - 1) / 2
            for i, y in enumerate(ys):
                u = i / max(len(ys) - 1, 1)
                v = own
                if abs(x - middle) <= .5 and abs(u - shine_at) < .2:
                    v += 2 if patch.kind == 'sides' else 1
                if u > .8 and len(ys) >= 3:
                    v -= 1
                if x == x1 - 1 and u > .4:
                    v -= 1
                t[y, x] = v
    if patch.kind == 'top':
        t += 1
    return t


# ------------------------------------------------------------------------------------------------ regions
def label_regions(lab, mask, wrap):
    h, w = mask.shape
    labels = np.full((h, w), -1)
    n = 0
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or labels[sy, sx] >= 0:
                continue
            labels[sy, sx] = n
            stack = [(sy, sx)]
            while stack:
                y, x = stack.pop()
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if wrap: nx %= w
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and labels[ny, nx] < 0 and np.linalg.norm(lab[y, x] - lab[ny, nx]) < EDGE:
                        labels[ny, nx] = n
                        stack.append((ny, nx))
            n += 1
    return labels, n


def absorb_specks(labels, n, lab, mask, wrap):
    """Lone pixels of another shade on a surface belong to that surface: they keep their colour, not a form."""
    h, w = labels.shape
    for r in range(n):
        ys, xs = np.nonzero(labels == r)
        if len(ys) == 0 or len(ys) > SPECK:
            continue
        around = {}
        for y, x in zip(ys, xs):
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if wrap: nx %= w
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and labels[ny, nx] != r:
                    around[labels[ny, nx]] = around.get(labels[ny, nx], 0) + 1
        if not around:
            continue
        host = max(around, key=around.get)
        if np.linalg.norm(lab[ys, xs].mean(0) - lab[labels == host].mean(0)) < SPECK_KEEP:
            labels[ys, xs] = host
    return labels


def skin_reference(lab, alpha):
    """The colour of the skin: most of the lower half of the face, or of the hands if the face is hidden."""
    for box in ((slice(12, 16), slice(9, 15)), (slice(28, 32), slice(44, 48))):
        m = alpha[box] > 128
        if m.sum() >= 4:
            c = np.median(lab[box][m], axis=0)
            if c[1] > .008 and c[2] > .008 and .4 < c[0] < .95:
                return c
    return None


def classify(patch, labels, n, lab, skin_ref):
    kinds = {}
    head = patch.group == 'head'
    for r in range(n):
        m = labels == r
        if not m.any():
            continue
        c = np.median(lab[m], axis=0)
        area = int(m.sum())
        ys, xs = np.nonzero(m)
        tall, wide = ys.max() - ys.min() + 1, len(np.unique(xs))
        if skin_ref is not None and np.linalg.norm((c - skin_ref) * (1, 1.6, 1.6)) < .1:
            kinds[r] = 'skin'
        elif head:
            on_face = patch.kind == 'sides' and (patch.faces[ys, xs] == 'front').all() and patch.part[0] == 'head'
            if on_face and area <= 6:
                kinds[r] = 'eye' if ys.max() <= 5 else 'trim'
            else:
                kinds[r] = 'hair'
        elif tall <= 2 and wide >= 3:
            kinds[r] = 'band'
        elif area < 10:
            kinds[r] = 'trim'
        else:
            kinds[r] = 'cloth'
    return kinds


_relief_cache = {}


def load_skin_relief(path, seed):
    """Per body part, each base skin pixel's distance from the part's middle tone, in steps of tone. Two or three bases
    are mixed by seed and slightly changed: the anatomy stays, the pixels are not those of the base."""
    key = (path, seed)
    if key in _relief_cache:
        return _relief_cache[key]
    if not path or not os.path.exists(path):
        return None
    files = [path] if os.path.isfile(path) else sorted(os.path.join(path, f) for f in os.listdir(path) if f.startswith('city_steve_base') and f[15:16].isdigit() and f.endswith('.png'))
    if not files:
        return None
    r = np.random.RandomState(seed & 0x7fffffff)
    chosen = [files[i] for i in r.choice(len(files), min(3, len(files)), replace=False)]
    weights = r.dirichlet(np.ones(len(chosen)) * .7)
    images = [(np.asarray(Image.open(f).convert('RGBA')), k) for f, k in zip(chosen, weights)]
    relief = {}
    for part in PARTS:
        if part[0] in OVERLAY_OF:
            continue
        for patch in patches_of(part):
            total, weight = np.zeros((patch.h, patch.w)), np.zeros((patch.h, patch.w))
            for a, k in images:
                alpha = a[patch.ty, patch.tx, 3] > 128
                if alpha.sum() < 4:
                    continue
                L = srgb_to_oklab(a[patch.ty, patch.tx, :3].astype(float))[..., 0]
                steps = (L - np.median(L[alpha])) / (TONE_STEP[0] + TONE_STEP[1] * np.median(L[alpha]))
                total += np.where(alpha, steps * k, 0)
                weight += np.where(alpha, k, 0)
            if weight.max() <= 0:
                continue
            value = np.where(weight > 0, total / np.maximum(weight, 1e-6), np.nan)
            # the holes of the base, where its eyes go, take their neighbours' tone
            while np.isnan(value).any():
                filled = value.copy()
                for y, x in zip(*np.nonzero(np.isnan(value))):
                    around = value[max(0, y - 1):y + 2, max(0, x - 1):x + 2]
                    if (~np.isnan(around)).any():
                        filled[y, x] = np.nanmean(around)
                value = filled
            relief[(part[6], patch.kind)] = value * r.uniform(.9, 1.1)
    _relief_cache[key] = relief
    return relief


# ------------------------------------------------------------------------------------------------ one patch
def form_tones(patch):
    t = np.zeros((patch.h, patch.w))
    if patch.kind != 'sides':
        t += CAP[patch.kind]
        return t
    for face in ('right', 'front', 'left', 'back'):
        m = FORM.get((patch.group, face))
        x = patch.x_of(face)
        if m is not None and m.shape[0] == patch.h:
            t[:, x:x + m.shape[1]] = m
    return t


def contact_shadows(t, patch, labels, mask, lab, kind_of):
    """What is above a border of colour lies over what is below it; the shadow falls down and to the right."""
    h, w = labels.shape
    for y in range(h):
        for x in range(w):
            if not mask[y, x]:
                continue
            own = labels[y, x]
            if y > 0 and mask[y - 1, x] and labels[y - 1, x] != own and kind_of[y - 1, x] != 'eye':
                t[y, x] -= 2
                if y + 1 < h and labels[y + 1, x] == own and kind_of[y, x] in ('cloth', 'skin'):
                    t[y + 1, x] -= 1
            lx = (x - 1) % w if patch.wrap else x - 1
            if 0 <= lx and mask[y, lx] and labels[y, lx] != own and kind_of[y, lx] != 'eye':
                t[y, x] -= 1
            if y + 1 < h and mask[y + 1, x] and labels[y + 1, x] != own and lab[y, x, 0] > lab[y + 1, x, 0] + .04:
                t[y, x] += 1
    return t


def bands_and_trims(t, patch, labels, mask, kind_of):
    """A belt, a cuff, a tie: a lit upper edge and a dark lower one; nothing else goes on it."""
    for r in np.unique(labels[mask]):
        m = labels == r
        kind = kind_of[m][0]
        if kind not in ('band', 'trim'):
            continue
        ys, xs = np.nonzero(m)
        t[m] = 0
        top = ys == ys.min()
        t[ys[top], xs[top]] = 1
        if ys.max() > ys.min():
            low = ys == ys.max()
            t[ys[low], xs[low]] = -1
        # a tall narrow trim, as a tie, has a shadow down one side
        if kind == 'trim' and ys.max() - ys.min() >= 3:
            right = xs == xs.max()
            t[ys[right], xs[right]] = -1
    return t


def clean_tones(t, labels, mask, wrap, keep=None):
    """A pixel of a tone none of its neighbours has is noise, not shading."""
    h, w = t.shape
    out = t.copy()
    for y in range(h):
        for x in range(w):
            if not mask[y, x] or (keep is not None and keep[y, x]):
                continue
            around = []
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if wrap: nx %= w
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and labels[ny, nx] == labels[y, x]:
                    around.append(t[ny, nx])
            if len(around) >= 2 and t[y, x] not in around:
                out[y, x] = np.median(around)
    return out


def shade_patch(patch, rgb, alpha, opts, skin_ref, relief, cover=None):
    mask = alpha[patch.ty, patch.tx] > 128
    if mask.sum() == 0:
        return None
    lab = srgb_to_oklab(rgb[patch.ty, patch.tx].astype(float))
    labels, n = label_regions(lab, mask, patch.wrap)
    labels = absorb_specks(labels, n, lab, mask, patch.wrap)
    kinds = classify(patch, labels, n, lab, skin_ref)
    kind_of = np.full(labels.shape, '', dtype=object)
    for r, k in kinds.items():
        kind_of[labels == r] = k
    # a pupil of a pixel or two counts as a speck on the face for forms, but its colour is not skin
    if skin_ref is not None:
        strange = (kind_of == 'skin') & (np.linalg.norm((lab - skin_ref) * (1, 1.6, 1.6), axis=-1) > .14)
        on_face = patch.kind == 'sides' and patch.part[0] == 'head'
        kind_of[strange] = 'eye' if on_face else 'trim'
    if opts.materials:
        for y in range(patch.h):
            for x in range(patch.w):
                painted = opts.materials.get((int(patch.tx[y, x]), int(patch.ty[y, x])))
                if painted and mask[y, x]:
                    kind_of[y, x] = painted
    key = (opts.seed, patch.part[0], patch.kind)

    # a straight hairline is not hair: the middle of some locks comes down a pixel over the forehead
    hair_seed = seed_of(opts.seed, 'hair', patch.group, patch.kind)
    if opts.hair_tips and patch.kind == 'sides' and patch.group == 'head':
        for x0, x1 in locks_of(patch.w, hair_seed):
            x = (x0 + x1 - 1) // 2
            if (x0 + x) % 2:
                continue
            for y in range(patch.h - 1):
                if kind_of[y, x] == 'hair' and kind_of[y + 1, x] == 'skin':
                    lab[y + 1, x] = lab[y, x]
                    labels[y + 1, x] = labels[y, x]
                    kind_of[y + 1, x] = 'hair'
                    break

    lab = grade(lab, kind_of, opts.grade)
    t = form_tones(patch)
    cloth = kind_of == 'cloth'
    if cloth.any():
        fold = fold_strokes(patch, cloth, seed_of(*key, 'folds'), opts.folds)
        fold = np.where(fold < 0, fold * 1.3, fold)
        t = np.where(cloth & (fold != 0), np.where(fold < 0, np.minimum(t, 0) * .5 + fold, np.maximum(t, fold)), t)
    skin = kind_of == 'skin'
    if skin.any():
        r = relief.get((patch.group, patch.kind)) if relief else None
        if r is not None and r.shape == t.shape and opts.skin_relief > 0:
            # on a face the drawn form leads: the eyes the base has holes for must not be fought with
            k = .45 if patch.group == 'head' and patch.kind == 'sides' else .85
            t = np.where(skin, t * (.8 if k < .5 else .4) + r * k * opts.skin_relief, t)
    hair = kind_of == 'hair'
    if hair.any():
        t = np.where(hair, hair_tones(patch, hair, hair_seed) + form_tones(patch) * .4, t)
    t = bands_and_trims(t, patch, labels, mask, kind_of)
    t = contact_shadows(t, patch, labels, mask, lab, kind_of)
    if patch.kind == 'sides' and patch.group in ('right_arm', 'left_arm') and skin.any():
        x = patch.x_of('front')
        for y, row in FIST.items():
            for i, c in enumerate(row):
                if skin[y, x + i]:
                    t[y, x + i] += TONES[c]
    eye = kind_of == 'eye'
    if eye.any():
        # eyes are drawn already: only the shadow of the lid over them
        for r in np.unique(labels[eye]):
            ys, xs = np.nonzero((labels == r) & eye)
            t[ys, xs] = np.round(-1 + (ys - ys.min()) / (ys.max() - ys.min()) * 2) if ys.max() > ys.min() else 0
    if cover is not None:
        over = np.zeros_like(cover)
        over[1:] = cover[:-1]
        t = np.where(over & ~cover & mask, t - 2, t)
    t = np.round(t)
    t = clean_tones(t, labels, mask, patch.wrap, keep=eye)
    t = np.where(cloth, np.clip(t, -3, 2), t)
    t = np.where(kind_of == 'keep', 0, t)
    t = np.where(skin, np.clip(t, -3, 2), np.clip(t, -5, 4)) * opts.strength
    return apply_tone(lab, t, kind_of, opts.hue_shift, opts.saturation), mask


# ------------------------------------------------------------------------------------------------ whole skin
def shade(image, opts, relief):
    a = np.asarray(image.convert('RGBA')).copy()
    if a.shape[0] < 64 or a.shape[1] < 64:
        raise SystemExit('expected at least 64x64, the standard skin layout, got %dx%d' % (a.shape[1], a.shape[0]))
    rgb, alpha = a[..., :3], a[..., 3]
    skin_ref = skin_reference(srgb_to_oklab(rgb.astype(float)), alpha)
    out = a.copy()
    by_name = {p[0]: p for p in PARTS}
    outer_of = {v: k for k, v in OVERLAY_OF.items()}
    for part in PARTS:
        for patch in patches_of(part):
            cover = None
            if part[0] in outer_of:
                outer = next(p for p in patches_of(by_name[outer_of[part[0]]]) if p.kind == patch.kind)
                cover = alpha[outer.ty, outer.tx] > 128
            done = shade_patch(patch, rgb, alpha, opts, skin_ref, relief, cover)
            if done is None:
                continue
            colour, mask = done
            out[patch.ty[mask], patch.tx[mask], :3] = colour[mask]
    return Image.fromarray(out, 'RGBA')


# ------------------------------------------------------------------------------------------------ looking at it
PLACE = {'head': (-4, 24, -4), 'body': (-4, 12, -2), 'right_arm': (-8, 12, -2), 'left_arm': (4, 12, -2), 'right_leg': (-4, 0, -2), 'left_leg': (0, 0, -2)}


def render(image, yaw=0.0, pitch=10.0, scale=8, background=(26, 29, 35, 255)):
    """The skin on the classic body in orthographic view, with sharp texels."""
    tex = np.asarray(image.convert('RGBA'))
    W, H = 40 * scale, 40 * scale
    canvas = np.empty((H, W, 4), np.uint8)
    canvas[:] = background
    depth = np.full((H, W), -1e9)
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))

    def project(p):
        x2 = p[0] * cy + p[2] * sy
        z2 = -p[0] * sy + p[2] * cy
        y2 = p[1] * cp - z2 * sp
        z3 = p[1] * sp + z2 * cp
        return np.array(((x2 + 20) * scale, H - (y2 + 4) * scale, z3))

    for name, u, v, w, h, d, group in PARTS:
        ox, oy, oz = PLACE[group]
        grow = (.5 if name == 'hat' else .25) if name in OVERLAY_OF else 0
        kx, ky, kz = (w + 2 * grow) / w, (h + 2 * grow) / h, (d + 2 * grow) / d
        faces = (
            ((u + d, v + d, w, h), (0, h, d), (1, 0, 0), (0, -1, 0), (0, 0, 1), 1.0),
            ((u, v + d, d, h), (0, h, 0), (0, 0, 1), (0, -1, 0), (-1, 0, 0), .9),
            ((u + d + w, v + d, d, h), (w, h, d), (0, 0, -1), (0, -1, 0), (1, 0, 0), .9),
            ((u + 2 * d + w, v + d, w, h), (w, h, 0), (-1, 0, 0), (0, -1, 0), (0, 0, -1), .96),
            ((u + d, v, w, d), (0, h, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0), 1.06),
        )
        for (tu, tv, tw, th), origin, du, dv, normal, light in faces:
            nz = -normal[0] * sy + normal[2] * cy
            if nz * cp + normal[1] * sp <= 1e-6:
                continue
            start = np.array(origin, float)

            def world(p):
                return np.array((p[0] * kx + ox - grow, p[1] * ky + oy - grow, p[2] * kz + oz - grow))

            p0 = project(world(start))
            pu = project(world(start + du)) - p0
            pv = project(world(start + dv)) - p0
            det = pu[0] * pv[1] - pu[1] * pv[0]
            if abs(det) < 1e-6:
                continue
            xs = [p0[0] + i * pu[0] * tw + j * pv[0] * th for i in (0, 1) for j in (0, 1)]
            ys = [p0[1] + i * pu[1] * tw + j * pv[1] * th for i in (0, 1) for j in (0, 1)]
            x0, x1 = max(0, int(min(xs))), min(W, int(max(xs)) + 2)
            y0, y1 = max(0, int(min(ys))), min(H, int(max(ys)) + 2)
            if x0 >= x1 or y0 >= y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
            dx, dy = gx - p0[0], gy - p0[1]
            s = (dx * pv[1] - dy * pv[0]) / det
            t = (dy * pu[0] - dx * pu[1]) / det
            inside = (s >= 0) & (s < tw) & (t >= 0) & (t < th)
            if not inside.any():
                continue
            colour = tex[np.clip(tv + t.astype(int), 0, tex.shape[0] - 1), np.clip(tu + s.astype(int), 0, tex.shape[1] - 1)]
            z = p0[2] + s * pu[2] + t * pv[2]
            yy, xx = np.nonzero(inside & (colour[..., 3] > 128))
            closer = z[yy, xx] > depth[yy + y0, xx + x0]
            yy, xx = yy[closer], xx[closer]
            depth[yy + y0, xx + x0] = z[yy, xx]
            canvas[yy + y0, xx + x0, :3] = np.clip(colour[yy, xx, :3] * light, 0, 255)
    return Image.fromarray(canvas, 'RGBA')


def preview(before, after, path):
    pics = [render(before, 0), render(after, 0), render(after, 35), render(after, 180)]
    w, h = pics[0].size
    sheet = Image.new('RGBA', (w * len(pics), h))
    for n, p in enumerate(pics):
        sheet.paste(p, (n * w, 0))
    sheet.save(path)


def main(argv=None):
    ap = argparse.ArgumentParser(description='Shades flat Minecraft skins into volume.')
    ap.add_argument('input', help='a skin .png or a folder of them')
    ap.add_argument('-o', '--output', help='output file or folder; by default <name>_shaded.png beside the input')
    ap.add_argument('--strength', type=float, default=Options.strength, help='how far a step of tone goes')
    ap.add_argument('--folds', type=float, default=Options.folds, help='how many folds on cloth, 0 for none')
    ap.add_argument('--grade', type=float, default=1.0, help='how far base colours are brought to better ones, 0 keeps them')
    ap.add_argument('--hue-shift', type=float, default=Options.hue_shift, help='how far shadows and lights turn from the original hue')
    ap.add_argument('--saturation', type=float, default=Options.saturation, help='how much shadows gain and lights lose saturation')
    ap.add_argument('--no-hair-tips', action='store_true', help='keep the hairline as it is drawn')
    ap.add_argument('--seed', type=int, help='another set of folds and locks; by default taken from the file name')
    ap.add_argument('--skin-base', default=DEFAULT_SKIN_BASE, help='a skin, or a folder of city_steve_base*.png, to take skin relief from')
    ap.add_argument('--preview', help='also write a render: before, after, three quarters, back')
    a = ap.parse_args(argv)

    files = [a.input]
    if os.path.isdir(a.input):
        files = [os.path.join(a.input, f) for f in sorted(os.listdir(a.input)) if f.lower().endswith('.png')]
    if not files:
        raise SystemExit('no .png files in ' + a.input)
    for f in files:
        seed = a.seed if a.seed is not None else seed_of(os.path.basename(f)) & 0xffff
        opts = Options(strength=a.strength, folds=a.folds, hair_tips=not a.no_hair_tips, grade=a.grade, hue_shift=a.hue_shift, saturation=a.saturation, seed=seed)
        relief = load_skin_relief(a.skin_base, seed)
        if relief is None:
            print('no skin base found, skin gets only form shading:', a.skin_base)
        before = Image.open(f).convert('RGBA')
        after = shade(before, opts, relief)
        if a.output and (os.path.isdir(a.output) or len(files) > 1):
            os.makedirs(a.output, exist_ok=True)
            out = os.path.join(a.output, os.path.basename(f))
        else:
            out = a.output or os.path.splitext(f)[0] + '_shaded.png'
        after.save(out)
        print(out)
        if a.preview:
            preview(before, after, a.preview if len(files) == 1 else os.path.splitext(out)[0] + '_preview.png')


if __name__ == '__main__':
    sys.exit(main())
