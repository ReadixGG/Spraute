"""Simple dark interface textures for the quest journal and the shop: soft corners, a barely visible gradient.

python tools/ui_textures.py <output folder> [preview.png]

Sizes follow what the windows expect. Writes ui_*.png; their names go in the engine's texture settings.
"""
import os
import sys

import numpy as np
from PIL import Image

TOP, BOTTOM = (31, 33, 40), (24, 25, 31)
EDGE = (56, 59, 69)
HOVER = (40, 43, 52), (31, 33, 40)
ON = (49, 47, 44), (37, 35, 34)
EDGE_ON = (150, 124, 72)
DIM = (24, 25, 29), (19, 20, 23)
AMBER, GREEN, RED, GREY = (217, 178, 90), (116, 186, 120), (201, 87, 90), (150, 154, 164)

GLYPHS = {
    'active': ('..###..', '.#...#.', '#..#..#', '#..##.#', '#.....#', '.#...#.', '..###..'),
    'done': ('.......', '......#', '.....##', '#...##.', '##.##..', '.###...', '..#....'),
    'fail': ('#.....#', '##...##', '.##.##.', '..###..', '.##.##.', '##...##', '#.....#'),
    'lock': ('..###..', '.#...#.', '.#...#.', '#######', '###.###', '###.###', '#######'),
    'up': ('...#...', '..###..', '.#####.'),
    'down': ('.#####.', '..###..', '...#...'),
    'left': ('..#', '.##', '###', '.##', '..#'),
    'right': ('#..', '##.', '###', '##.', '#..'),
}


def mask(w, h, r):
    """Rounded corners, cut pixel by pixel."""
    m = np.ones((h, w), bool)
    for y in range(min(r, h)):
        for x in range(min(r, w)):
            if (r - x - .5) ** 2 + (r - y - .5) ** 2 > r * r:
                m[y, x] = m[y, w - 1 - x] = m[h - 1 - y, x] = m[h - 1 - y, w - 1 - x] = False
    return m


def panel(w, h, r=3, colours=(TOP, BOTTOM), edge=EDGE):
    a = np.zeros((h, w, 4), np.uint8)
    t = np.linspace(0, 1, h)[:, None]
    for i in range(3):
        a[..., i] = np.round(colours[0][i] + (colours[1][i] - colours[0][i]) * t)
    a[..., 3] = 255
    outer = mask(w, h, r)
    if w > 2 and h > 2:
        inner = np.zeros((h, w), bool)
        inner[1:-1, 1:-1] = mask(w - 2, h - 2, max(0, r - 1))
        a[outer & ~inner, :3] = edge
    a[~outer] = 0
    return a


def put(a, name, x, y, colour):
    for j, row in enumerate(GLYPHS[name]):
        for i, c in enumerate(row):
            if c == '#':
                a[y + j, x + i] = (*colour, 255)


def centred(a, name, colour, dy=0):
    g = GLYPHS[name]
    put(a, name, (a.shape[1] - len(g[0])) // 2, (a.shape[0] - len(g)) // 2 + dy, colour)


def well(a, x, y, w, h):
    """A slightly deeper place on a panel, for an item."""
    a[y:y + h, x:x + w] = panel(w, h, 2, ((17, 18, 22), (20, 21, 25)), (40, 42, 49))


def tab_sheet(glyph, colour):
    states = []
    for colours, edge, shade in (((TOP, BOTTOM), EDGE, .6), (HOVER, EDGE, .85), (ON, EDGE_ON, 1.)):
        s = panel(23, 23, 3, colours, edge)
        centred(s, glyph, tuple(int(c * shade) for c in colour))
        states.append(s)
    return np.vstack(states)


def icon_sheet():
    a = np.zeros((46, 63, 4), np.uint8)
    for col, (glyph, colour) in enumerate((('active', AMBER), ('done', GREEN), ('fail', RED))):
        for row, k in enumerate((.82, 1.)):
            cell = np.zeros((23, 21, 4), np.uint8)
            centred(cell, glyph, tuple(int(c * k) for c in colour))
            a[row * 23:(row + 1) * 23, col * 21:(col + 1) * 21] = cell
    return a


def cell(colours=(TOP, BOTTOM), edge=EDGE):
    a = panel(30, 43, 3, colours, edge)
    well(a, 3, 3, 24, 24)
    return a


def arrow(w, h, name, double, hover):
    a = panel(w, h, 2, HOVER if hover else (TOP, BOTTOM))
    colour = (232, 226, 210) if hover else (176, 172, 162)
    if double:
        g = GLYPHS[name]
        y = (h - len(g)) // 2
        put(a, name, (w - 7) // 2, y, colour)
        put(a, name, (w - 7) // 2 + 4, y, colour)
    else:
        centred(a, name, colour)
    return a


def divider():
    a = np.zeros((2, 129, 4), np.uint8)
    a[..., :3] = EDGE_ON
    a[0, :, 3] = np.round(np.interp(np.arange(129), (0, 16, 112, 128), (0, 170, 170, 0)))
    a[1, :, :3] = (20, 21, 25)
    a[1, :, 3] = a[0, :, 3]
    return a


def locked():
    a = cell(DIM, (40, 42, 48))
    put(a, 'lock', 11, 11, (116, 118, 126))
    return a


def sold():
    a = cell(DIM, (40, 42, 48))
    put(a, 'fail', 11, 11, RED)
    return a


def textures():
    return {
        'ui_journal_list': panel(144, 230),
        'ui_journal_card': panel(240, 225),
        'ui_journal_entry': panel(131, 35, 3),
        'ui_journal_entry_hover': panel(131, 35, 3, HOVER),
        'ui_journal_entry_on': panel(131, 35, 3, ON, EDGE_ON),
        'ui_journal_task': panel(225, 16, 2),
        'ui_journal_divider': divider(),
        'ui_tab_current': tab_sheet('active', AMBER),
        'ui_tab_done': tab_sheet('done', GREEN),
        'ui_tab_failed': tab_sheet('fail', RED),
        'ui_quest_icons': icon_sheet(),
        'ui_shop_list': panel(254, 212),
        'ui_shop_card': panel(176, 262),
        'ui_shop_tab': panel(24, 26, 3),
        'ui_shop_tab_on': panel(24, 26, 3, ON, EDGE_ON),
        'ui_shop_cell': cell(),
        'ui_shop_cell_on': cell(ON, EDGE_ON),
        'ui_shop_locked': locked(),
        'ui_shop_sold': sold(),
        'ui_shop_button': panel(88, 14, 2),
        'ui_shop_button_hover': panel(88, 14, 2, HOVER),
        'ui_shop_deal': panel(88, 14, 2, ON, EDGE_ON),
        'ui_shop_slider': panel(9, 10, 2, ((76, 79, 90), (58, 60, 70)), (92, 95, 106)),
        'ui_arrow_minus1': arrow(9, 13, 'left', False, False),
        'ui_arrow_minus1_hover': arrow(9, 13, 'left', False, True),
        'ui_arrow_plus1': arrow(9, 13, 'right', False, False),
        'ui_arrow_plus1_hover': arrow(9, 13, 'right', False, True),
        'ui_arrow_minus5': arrow(12, 15, 'left', True, False),
        'ui_arrow_minus5_hover': arrow(12, 15, 'left', True, True),
        'ui_arrow_plus5': arrow(12, 15, 'right', True, False),
        'ui_arrow_plus5_hover': arrow(12, 15, 'right', True, True),
    }


def main(out, preview=None):
    os.makedirs(out, exist_ok=True)
    made = textures()
    for name, a in made.items():
        Image.fromarray(a, 'RGBA').save(os.path.join(out, name + '.png'))
    print('written:', len(made))
    if preview:
        sheet = Image.new('RGBA', (1140, 800), (70, 74, 84, 255))
        x, y, row = 8, 8, 0
        for a in made.values():
            pic = Image.fromarray(a, 'RGBA')
            k = 4 if max(pic.size) < 80 else 1
            pic = pic.resize((pic.width * k, pic.height * k), Image.NEAREST)
            if x + pic.width > 1130:
                x, y, row = 8, y + row + 8, 0
            sheet.paste(pic, (x, y), pic)
            x, row = x + pic.width + 8, max(row, pic.height)
        sheet.save(preview)


if __name__ == '__main__':
    main(*sys.argv[1:])
