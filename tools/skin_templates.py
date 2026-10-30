"""Flat skin templates for the story «Тринадцатый удар», drawn as text, and their shaded versions.

python tools/skin_templates.py <output folder>

Writes <name>_flat.png and <name>.png, 128x128, for the NPC rig: the standard layout plus the rig's face atlas (eye whites,
pupils, brows, inner mouth). Every colour of a palette says what material it is, so the shader does not have to guess.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skin_shader as engine

LAYOUT = {  # part: (u, v, width, height, depth)
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'right_arm': (40, 16, 4, 12, 4), 'right_sleeve': (40, 32, 4, 12, 4),
    'left_arm': (32, 48, 4, 12, 4), 'left_sleeve': (48, 48, 4, 12, 4),
    'right_leg': (0, 16, 4, 12, 4), 'right_pants': (0, 32, 4, 12, 4),
    'left_leg': (16, 48, 4, 12, 4), 'left_pants': (0, 48, 4, 12, 4),
}


def face_rect(part, face):
    u, v, w, h, d = LAYOUT[part]
    return {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
            'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}[face]


class Canvas:
    def __init__(self, palette):
        self.palette = palette
        self.a = np.zeros((128, 128, 4), np.uint8)
        self.materials = {}

    def colour(self, c):
        value = self.palette[c][0].lstrip('#')
        return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

    def draw(self, x0, y0, rows, where='?'):
        for j, row in enumerate(rows):
            for i, c in enumerate(row):
                if c == ' ':
                    continue
                self.a[y0 + j, x0 + i] = self.colour(c)
                if self.palette[c][1]:
                    self.materials[(x0 + i, y0 + j)] = self.palette[c][1]

    def face(self, part, face, rows):
        x, y, w, h = face_rect(part, face)
        if isinstance(rows, str):
            rows = [rows * w] * h
        if len(rows) != h or any(len(r) != w for r in rows):
            raise SystemExit('%s %s: expected %dx%d, got %s' % (part, face, w, h, [len(r) for r in rows]))
        self.draw(x, y, rows)

    def box(self, part, faces):
        for face, rows in faces.items():
            self.face(part, face, rows)

    def limb(self, right, left, faces):
        """The same drawing on both sides: the outer side of each is its outer side, the rest is mirrored."""
        self.box(right, {'front': faces['front'], 'back': faces['back'], 'right': faces['out'], 'left': faces['in'], 'top': faces['cap'], 'bottom': faces['sole']})
        flip = lambda rows: [r[::-1] for r in rows] if not isinstance(rows, str) else rows
        self.box(left, {'front': flip(faces['front']), 'back': flip(faces['back']), 'left': flip(faces['out']), 'right': flip(faces['in']), 'top': faces['cap'], 'bottom': faces['sole']})

    def ring(self, part, row, ch, height=1):
        """A band all the way round a layer: a belt, a cuff, a hem."""
        for face in ('front', 'back', 'right', 'left'):
            x, y, w, _ = face_rect(part, face)
            self.draw(x, y + row, [ch * w] * height)

    def atlas(self, white, white_low, iris, iris_low, brow, lip, inner, teeth='#EDE6DA'):
        """The rig's separate eye, brow and mouth pieces. The closed mouth, the rig's only piece over the face at rest,
        stays empty: no line across a clean face."""
        rgb = lambda h: tuple(int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        for x in range(116, 128):
            self.a[61, x] = rgb(white)
            self.a[62, x] = rgb(white)
            self.a[63, x] = rgb(white_low)
        for x in range(120, 128):
            self.a[1, x] = rgb(iris)
            self.a[2, x] = rgb(iris_low)
        for x in (121, 122, 125, 126):
            self.a[0, x] = rgb(iris)
        for x, y in ((1, 0), (2, 0), (0, 3), (1, 3)):
            self.a[y, x] = rgb(brow)
        self.a[5, 4] = rgb(teeth)
        for x in range(4, 8):
            self.a[6, x] = self.a[7, x] = rgb(inner)
        self.a[7, 5] = self.a[7, 6] = rgb(lip)


# ------------------------------------------------------------------------------------------------ the characters
# Plain masses of colour on purpose: no drawn folds, stripes, mouths or noses. Shading gives the form, the rig gives the mouth.
# What sits on a body (fringes, belts, cuffs, hems, aprons) is on the second layer, so it stands out and drops a shadow.


def monk(p, cowl_up=False):
    """The cut both Ансельм and his true form share."""
    c = Canvas(p)
    if cowl_up:
        c.box('head', {
            'front': ['oooooooo', 'oOOOOOOo', 'oOssssOo', 'ossssssO'.replace('O', 'o'), 'swpsspws', 'swpsspws', 'ssssssss', 'ssssssss'],
            'right': ['oooooooo'] * 8, 'left': ['oooooooo'] * 8, 'back': 'o', 'top': 'o', 'bottom': 'o'})
        c.box('hat', {'front': ['oooooooo', 'o      o', '        ', '        ', '        ', '        ', '        ', '        '],
                      'right': ['oooooooo'] * 8, 'left': ['oooooooo'] * 8, 'back': 'o', 'top': 'o'})
    else:
        c.box('head', {
            'front': ['hhhhhhhh', 'hsssssss', 'ssssssss', 'sbbssbbs', 'swpsspws', 'swpsspws', 'gggssggg', 'gggggggg'],
            'right': ['hhhhhhhh', 'hhhhhhhh', 'hhhhhhss', 'hhhhhsss', 'hhhhhsss', 'hhhhssss', 'hhhggggg', 'hhgggggg'],
            'left': ['hhhhhhhh', 'hhhhhhhh', 'sshhhhhh', 'ssshhhhh', 'ssshhhhh', 'sssshhhh', 'ggggghhh', 'gggggghh'],
            'back': 'h', 'top': ['hhhhhhhh', 'hhsssshh', 'hssssssh', 'hssssssh', 'hssssssh', 'hssssssh', 'hhsssshh', 'hhhhhhhh'], 'bottom': 'g'})
    return c


def anselm():
    """Брат Ансельм: an old monk in a wool cassock, with a rope belt, a silver cross and a long grey beard. Not what he seems."""
    p = {'s': ('#C49A84', 'skin'), 'h': ('#B4AEA6', 'hair'), 'g': ('#C8C2B8', 'hair'), 'b': ('#8F8A84', 'trim'),
         'w': ('#D8D4CA', 'eye'), 'p': ('#3A4048', 'eye'),
         'c': ('#57483A', 'cloth'), 'C': ('#6E5C4A', 'band'), 'r': ('#B8A27C', 'band'), 'k': ('#C4C8CE', 'trim'), 'l': ('#4A3A2A', 'band')}
    c = monk(p)
    c.box('body', {'front': 'c', 'back': 'c', 'right': 'c', 'left': 'c', 'top': 'C', 'bottom': 'c'})
    c.face('hat', 'front', ['hhhhhhhh', 'h hh  hh', '        ', '        ', '        ', '        ', 'gg    gg', 'ggg  ggg'])
    c.box('jacket', {'front': ['CCggggCC', 'CCggggCC', '   gg   ', '        ', '   kk   ', '  kkkk  ', '   kk   ', 'rrrrrrrr', '        ', '        ', '        ', '        ']})
    c.ring('jacket', 0, 'C', 2)
    c.face('jacket', 'front', ['CCggggCC', 'CCggggCC', '   gg   ', '        ', '   kk   ', '  kkkk  ', '   kk   ', '        ', '        ', '        ', '        ', '        '])
    c.ring('jacket', 7, 'r')
    sleeve = ['cccc'] * 9 + ['ssss'] * 3
    c.limb('right_arm', 'left_arm', {'front': sleeve, 'back': sleeve, 'out': sleeve, 'in': sleeve, 'cap': 'c', 'sole': 's'})
    c.ring('right_sleeve', 7, 'C', 2)
    c.ring('left_sleeve', 7, 'C', 2)
    robe = ['cccc'] * 10 + ['ssss', 'llll']
    c.limb('right_leg', 'left_leg', {'front': robe, 'back': robe, 'out': robe, 'in': robe, 'cap': 'c', 'sole': 'l'})
    c.ring('right_pants', 8, 'C', 2)
    c.ring('left_pants', 8, 'C', 2)
    c.atlas('#D8D4CA', '#EDE9DF', '#3A4048', '#59616B', '#8F8A84', '#A0695A', '#5A1F1F')
    return c, dict(LOOK)


def anselm_boss():
    """Ансельм when the disguise is gone: the same cassock burnt black, the cowl up over dead grey skin, eyes of the Звонарь's red."""
    p = {'s': ('#8C8580', 'skin'), 'o': ('#241C20', 'hair'), 'O': ('#3A2A2E', 'band'), 'w': ('#FF3A4A', 'keep'), 'p': ('#FFB0A8', 'keep'),
         'c': ('#2A2024', 'cloth'), 'C': ('#6E1820', 'band'), 'r': ('#8E1E28', 'band'), 'k': ('#C4C8CE', 'trim'), 'l': ('#17120F', 'band')}
    c = monk(p, cowl_up=True)
    c.box('body', {'front': ['oossssoo', 'oossssoo', 'cccsscc c'.replace(' ', '')[:8]] + ['cccccccc'] * 9,
                   'back': ['oooooooo', 'oooooooo'] + ['cccccccc'] * 10, 'right': ['oooo', 'oooo'] + ['cccc'] * 10, 'left': ['oooo', 'oooo'] + ['cccc'] * 10, 'top': 'o', 'bottom': 'c'})
    c.ring('jacket', 0, 'o')
    c.face('jacket', 'front', ['oo    oo', 'o      o', '        ', '        ', '   kk   ', '  kkkk  ', '   kk   ', '        ', 'cc cc cc', 'c  cc  c', ' c  c c ', '  c  c  '])
    c.face('jacket', 'back', ['oooooooo', '        ', '        ', '        ', '        ', '        ', '        ', '        ', 'cc cc cc', 'c  cc  c', ' c  c c ', '  c  c  '])
    c.face('jacket', 'right', ['oooo', '    ', '    ', '    ', '    ', '    ', '    ', '    ', 'cc c', 'c  c', ' c  ', '  c '])
    c.face('jacket', 'left', ['oooo', '    ', '    ', '    ', '    ', '    ', '    ', '    ', 'c cc', 'c  c', '  c ', ' c  '])
    c.ring('jacket', 7, 'r')
    sleeve = ['cccc'] * 9 + ['ssss'] * 3
    c.limb('right_arm', 'left_arm', {'front': sleeve, 'back': sleeve, 'out': sleeve, 'in': sleeve, 'cap': 'c', 'sole': 's'})
    c.ring('right_sleeve', 7, 'C', 2)
    c.ring('left_sleeve', 7, 'C', 2)
    robe = ['cccc'] * 10 + ['llll', 'llll']
    c.limb('right_leg', 'left_leg', {'front': robe, 'back': robe, 'out': robe, 'in': robe, 'cap': 'c', 'sole': 'l'})
    c.ring('right_pants', 9, 'C')
    c.ring('left_pants', 9, 'C')
    c.atlas('#FF3A4A', '#FF3A4A', '#FFB0A8', '#FFD8D0', '#241C20', '#3A2A2E', '#0E0A0C')
    # no colour picking here: it would bring the dead grey back to a living skin
    return c, dict(LOOK, grade=0)


def agata():
    """Слепая Агата: a young woman in a grey dress and a dark shawl, a headscarf and a braid down her back, her eyes milky white."""
    p = {'s': ('#DDB8A4', 'skin'), 'a': ('#6A5648', 'hair'), 'z': ('#46536A', 'hair'), 'Z': ('#5A6880', 'band'),
         'b': ('#54463A', 'trim'), 'w': ('#E8EEF0', 'eye'), 'p': ('#C9D6DC', 'eye'),
         'v': ('#7A8896', 'cloth'), 'u': ('#C4B9A4', 'cloth'), 'y': ('#3C4656', 'cloth'),
         'r': ('#6B4E3A', 'band'), 'c': ('#D2CCC0', 'band'), 'f': ('#3A2C24', 'band')}
    c = Canvas(p)
    c.box('head', {
        'front': ['zzzzzzzz', 'zzzzzzzz', 'zassssaz', 'zbbssbbz', 'swpsspws', 'swpsspws', 'ssssssss', 'ssssssss'],
        'right': ['zzzzzzzz', 'zzzzzzzz', 'zzzzzzzz', 'zzzzzzza', 'zzzzzzas', 'zzzzzass', 'zzzzzsss', 'zzzzssss'],
        'left': ['zzzzzzzz', 'zzzzzzzz', 'zzzzzzzz', 'azzzzzzz', 'sazzzzzz', 'ssazzzzz', 'ssszzzzz', 'sssszzzz'],
        'back': ['zzzzzzzz', 'zzzzzzzz', 'zzzzzzzz', 'zzzzzzzz', 'zzzzzzzz', 'azzzzzza', 'aazzzzaa', 'aaazzaaa'], 'top': 'z', 'bottom': 's'})
    # the fringe under the scarf: on the front only, no pixels on the sides
    c.face('hat', 'front', ['ZZZZZZZZ', 'ZZZZZZZZ', 'aa aa  a', 'a   a   ', '        ', '        ', '        ', '        '])
    c.box('body', {'front': ['vvvccvvv'] + ['vvvvvvvv'] * 11, 'back': 'v', 'right': 'v', 'left': 'v', 'top': 'v', 'bottom': 'v'})
    c.ring('jacket', 0, 'y', 3)
    c.face('jacket', 'front', ['yy    yy', 'yyy  yyy', 'yyyyyyyy', 'yyyyyyyy', '        ', ' uuuuuu ', ' uuuuuu ', '        ', ' uuuuuu ', ' uuuuuu ', ' uuuuuu ', ' uuuuuu '])
    c.face('jacket', 'back', ['yyyaayyy', 'yyyaayyy', 'yyyaayyy', 'yyyaayyy', ' yyaayy ', '  yaay  ', '   aa   ', '   aa   ', '        ', '        ', '        ', '        '])
    c.ring('jacket', 7, 'r')
    arm = ['vvvv'] * 10 + ['ssss', 'ssss']
    c.limb('right_arm', 'left_arm', {'front': arm, 'back': arm, 'out': arm, 'in': arm, 'cap': 'v', 'sole': 's'})
    for sleeve in ('right_sleeve', 'left_sleeve'):
        c.ring(sleeve, 0, 'y', 3)
        c.ring(sleeve, 9, 'c')
    skirt = ['vvvv'] * 10 + ['ffff', 'ffff']
    c.limb('right_leg', 'left_leg', {'front': skirt, 'back': skirt, 'out': skirt, 'in': skirt, 'cap': 'v', 'sole': 'f'})
    c.atlas('#E8EEF0', '#F4F7F7', '#C9D6DC', '#DDE6EA', '#54463A', '#A8706A', '#5A2626')
    return c, dict(LOOK)


def liza():
    """Лиза: a little ghost girl from a hundred years ago: straw hair, a turquoise ribbon, an old dress with an apron."""
    p = {'s': ('#F0D8CC', 'skin'), 'a': ('#C9A86A', 'hair'), 't': ('#55D4C4', 'band'),
         'b': ('#9A7E50', 'trim'), 'w': ('#F2F6F4', 'eye'), 'p': ('#2C6E72', 'eye'),
         'e': ('#5C9E9A', 'cloth'), 'u': ('#E2DDCC', 'cloth'), 'c': ('#EFEBDC', 'band'), 'f': ('#70503C', 'band')}
    c = Canvas(p)
    c.box('head', {
        'front': ['aaaaaaaa', 'aaaaaaaa', 'asaassaa', 'sbbssbbs', 'swpsspws', 'swpsspws', 'ssssssss', 'ssssssss'],
        'right': ['aaaaaaaa', 'aaaaaaaa', 'aaaaaaas', 'aaaaaass', 'aaaaasss', 'taaaasss', 'taaaasss', 'aaaassss'],
        'left': ['aaaaaaaa', 'aaaaaaaa', 'saaaaaaa', 'ssaaaaaa', 'sssaaaaa', 'sssaaaat', 'sssaaaat', 'ssssaaaa'],
        'back': ['aaaaaaaa'] * 5 + ['taaaaaat', 'taaaaaat', 'aaaaaaaa'], 'top': 'a', 'bottom': 'a'})
    # the fringe: on the front only, no pixels on the sides
    c.face('hat', 'front', ['aaaaaaaa', 'aaaaaaaa', 'aaa aa a', 'a a    a', '        ', '        ', '        ', '        '])
    c.box('body', {'front': ['eeesseee'] + ['eeeeeeee'] * 11, 'back': 'e', 'right': 'e', 'left': 'e', 'top': 'e', 'bottom': 'e'})
    c.face('jacket', 'front', ['  cccc  ', '        ', '  uuuu  ', '  uuuu  ', '  uuuu  ', '  uuuu  ', '        ', '  uuuu  ', '  uuuu  ', '  uuuu  ', '  uuuu  ', '  uuuu  '])
    c.face('jacket', 'back', ['  cccc  ', '        ', '        ', '        ', '        ', '        ', '        ', '        ', '        ', '        ', '        ', '        '])
    c.ring('jacket', 6, 't')
    arm = ['eeee', 'eeee', 'eeee'] + ['ssss'] * 9
    c.limb('right_arm', 'left_arm', {'front': arm, 'back': arm, 'out': arm, 'in': arm, 'cap': 'e', 'sole': 's'})
    c.ring('right_sleeve', 3, 'c')
    c.ring('left_sleeve', 3, 'c')
    skirt = ['eeee'] * 7 + ['ssss', 'ssss', 'ssss', 'ffff', 'ffff']
    c.limb('right_leg', 'left_leg', {'front': skirt, 'back': skirt, 'out': skirt, 'in': skirt, 'cap': 'e', 'sole': 'f'})
    c.ring('right_pants', 6, 'c')
    c.ring('left_pants', 6, 'c')
    c.atlas('#F2F6F4', '#FFFFFF', '#2C6E72', '#55BFB4', '#9A7E50', '#C88A84', '#7A3030')
    return c, dict(LOOK)


def ten():
    """Тень: a shape of dark with cracks of violet light and two glowing eyes, no face, torn at the edges. The one skin that
    keeps its drawn texture: it has no clothes or features for the form to read from."""
    p = {'x': ('#1E1A2A', 'cloth'), 'X': ('#141120', 'trim'), 'y': ('#2E2740', 'cloth'), 'v': ('#5A4380', 'trim'), 'g': ('#D8C6FF', 'keep'), 'G': ('#9C82E0', 'keep'), 'h': ('#262034', 'hair')}
    c = Canvas(p)
    c.box('head', {
        'front': ['hhhhhhhh', 'hXXXXXXh', 'XXXXXXXX', 'XXXXXXXX', 'XggXXggX', 'XGGXXGGX', 'XXXXXXXX', 'XXXXXXXX'],
        'right': ['hhhhhhhh', 'hhhhhhhh', 'hhhhhhXX', 'hhhhhXXX', 'hhhhXXXX', 'hhhhXXXX', 'hhhXXXXX', 'hhXXXXXX'],
        'left': ['hhhhhhhh', 'hhhhhhhh', 'XXhhhhhh', 'XXXhhhhh', 'XXXXhhhh', 'XXXXhhhh', 'XXXXXhhh', 'XXXXXXhh'],
        'back': 'h', 'top': 'h', 'bottom': 'X'})
    c.box('hat', {'front': ['h hh h h', ' h  h h ', '        ', '        ', '        ', '        ', '        ', '        '],
                  'back': ['hh hh hh', 'h hh h h', 'hh  h hh', 'h h  h h', ' h   h  ', ' h      ', '        ', '        ']})
    c.box('body', {
        'front': ['xxxXXxxx', 'xyyXXyyx', 'xyyvXyyx', 'xxyXvXxx', 'xXxXvxXx', 'xXxvXxXx', 'xxXvXXxx', 'xxXvxxXx', 'xXxXvxxx', 'xXxxvXxx', 'XxxxXvxX', 'XxXxxvxX'],
        'back': ['xxxxxxxx', 'xyyxxyyx', 'xyyXXyyx', 'xxXvXXxx', 'xXxvXxXx', 'xxXXvxxx', 'xxxXvxxx', 'XxxvXxxX', 'XxxvxxxX', 'xxvXxxXx', 'XxvxxXxX', 'XxxXxxxX'],
        'right': ['xxxx', 'xyyx', 'xyyx', 'xxXx', 'xXxx', 'xXxx', 'xxXx', 'xXxx', 'xXxx', 'xxXx', 'XxxX', 'XxxX'],
        'left': ['xxxx', 'xyyx', 'xyyx', 'xXxx', 'xxXx', 'xxXx', 'xXxx', 'xxXx', 'xxXx', 'xXxx', 'XxxX', 'XxxX'],
        'top': 'x', 'bottom': 'X'})
    c.box('jacket', {'front': ['x      x', '        ', '        ', '        ', '        ', '        ', '        ', 'x      x', 'xx    xx', 'x x  x x', 'x  xx  x', ' x  x x '],
                     'back': ['x      x', '        ', '        ', '        ', '        ', '        ', '        ', 'x      x', 'xx xx xx', 'x x  x x', 'x  xx  x', ' x  x x '],
                     'right': ['    ', '    ', '    ', '    ', '    ', '    ', '    ', 'xxxx', 'xx x', 'x x ', ' x x', 'x   '],
                     'left': ['    ', '    ', '    ', '    ', '    ', '    ', '    ', 'xxxx', 'x xx', ' x x', 'x x ', '   x']})
    arm = ['xyyx', 'xyyx', 'xxXx', 'xXxx', 'xXvx', 'xxvx', 'xXvX', 'xXxx', 'xxXx', 'XxxX', 'XXXX', 'XvXv']
    c.limb('right_arm', 'left_arm', {'front': arm, 'back': arm, 'out': arm, 'in': ['XXXX'] * 11 + ['XvXv'], 'cap': 'x', 'sole': 'X'})
    leg = ['xxXx', 'xyxx', 'xyXx', 'xxXx', 'xXxx', 'xXvx', 'xxvx', 'xXvx', 'XxXx', 'XxxX', 'XXXX', 'X X ']
    c.limb('right_leg', 'left_leg', {'front': leg, 'back': leg, 'out': leg, 'in': ['XXXX'] * 11 + ['X X '], 'cap': 'x', 'sole': 'X'})
    c.atlas('#D8C6FF', '#D8C6FF', '#D8C6FF', '#9C82E0', '#141120', '#141120', '#0A0814')
    return c, dict(LOOK)


LOOK = {}  # the shader's own defaults


CHARACTERS = (('ten', ten), ('anselm', anselm), ('anselm_boss', anselm_boss), ('agata', agata), ('liza', liza))


def main(out):
    os.makedirs(out, exist_ok=True)
    for name, make in CHARACTERS:
        canvas, settings = make()
        flat = Image.fromarray(canvas.a, 'RGBA')
        flat.save(os.path.join(out, name + '_flat.png'))
        seed = engine.seed_of(name) & 0xffff
        opts = engine.Options(seed=seed, materials=canvas.materials, **settings)
        engine.shade(flat, opts, engine.load_skin_relief(engine.DEFAULT_SKIN_BASE, seed)).save(os.path.join(out, name + '.png'))
        print(name, 'ok')


if __name__ == '__main__':
    main(sys.argv[1])
