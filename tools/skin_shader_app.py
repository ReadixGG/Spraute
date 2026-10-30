"""The window of the skin shader: python tools/skin_shader_app.py [skin.png]

Open a flat skin, see it before and after on a body that turns with the mouse, move the sliders, and where the program
took a thing for what it is not, click that colour on the texture with the right material. Needs: pip install pillow numpy
"""
import os
import random
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

import numpy as np
from PIL import Image, ImageTk

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skin_shader as engine

BG, PANEL, TEXT, MUTED, ACCENT = '#16181d', '#20232a', '#e6e6e6', '#9aa0aa', '#5b8def'
VIEW = 520
AUTO = 'auto'
MATERIALS = (('cloth', 'Ткань', '#6fa3ff'), ('band', 'Пояс, манжета', '#ffb454'), ('trim', 'Мелкая деталь', '#ffd866'), ('skin', 'Кожа', '#ff8f8f'),
             ('hair', 'Волосы', '#c792ea'), ('eye', 'Глаз', '#7fdbca'), ('keep', 'Не трогать', '#cccccc'), (AUTO, 'Авто (стереть)', '#555a66'))
MATERIAL_COLOUR = {m: c for m, _, c in MATERIALS}


class App(tk.Tk):
    def __init__(self, path=None):
        super().__init__()
        self.title('Spraute: шейдер скинов')
        self.configure(bg=BG)
        self.geometry('1450x900')
        self.minsize(1200, 880)
        self.path = None
        self.flat = None
        self.shaded = None
        self.materials = {}
        self.history = []
        self.yaw, self.pitch = 25.0, 10.0
        self.drag = None
        self.pending = None
        self.pics = {}
        self.skin_base = engine.DEFAULT_SKIN_BASE
        self.build()
        if path:
            self.load(path)
        else:
            self.status.set('Откройте плоский скин: кнопка «Открыть» слева сверху.')

    # ---------------------------------------------------------------------------------------- layout
    def build(self):
        style = ttk.Style(self)
        style.theme_use('clam')
        style.configure('.', background=PANEL, foreground=TEXT, fieldbackground=BG, bordercolor=BG, lightcolor=PANEL, darkcolor=PANEL)
        style.configure('TButton', background='#2d3139', padding=(10, 6))
        style.map('TButton', background=[('active', '#3a3f49')])
        style.configure('Accent.TButton', background=ACCENT, foreground='#ffffff')
        style.map('Accent.TButton', background=[('active', '#7aa5f5')])
        style.configure('TRadiobutton', background=PANEL, foreground=TEXT)
        style.map('TRadiobutton', background=[('active', PANEL)])
        style.configure('TCheckbutton', background=PANEL, foreground=TEXT)
        style.map('TCheckbutton', background=[('active', PANEL)])
        style.configure('TScale', background='#8a93a3', troughcolor='#343843')
        style.configure('Head.TLabel', font=('Segoe UI', 11, 'bold'))
        style.configure('Muted.TLabel', foreground=MUTED)

        bar = tk.Frame(self, bg=PANEL)
        bar.pack(side='top', fill='x')
        for text, command, kind in (('Открыть', self.open_file, 'Accent.TButton'), ('Сохранить', self.save, 'TButton'), ('Сохранить как…', self.save_as, 'TButton'),
                                    ('Обработать папку…', self.batch, 'TButton')):
            ttk.Button(bar, text=text, command=command, style=kind).pack(side='left', padx=(8, 0), pady=8)
        self.file_label = ttk.Label(bar, text='', style='Muted.TLabel')
        self.file_label.pack(side='left', padx=14)

        main = tk.Frame(self, bg=BG)
        main.pack(fill='both', expand=True)
        views = tk.Frame(main, bg=BG)
        views.pack(side='left', fill='both', expand=True, padx=8, pady=8)
        self.canvas_before = self.view(views, 'До', 0)
        self.canvas_after = self.view(views, 'После', 1)
        turn = tk.Frame(views, bg=BG)
        turn.grid(row=2, column=0, columnspan=2, pady=(6, 0))
        for text, yaw in (('Спереди', 0), ('Три четверти', 35), ('Сбоку', 90), ('Сзади', 180)):
            ttk.Button(turn, text=text, command=lambda y=yaw: self.turn_to(y)).pack(side='left', padx=3)
        tk.Label(turn, text='  тяните фигуру мышью, чтобы повернуть', bg=BG, fg=MUTED).pack(side='left')
        self.palette = tk.Canvas(views, width=2 * VIEW, height=64, bg=BG, highlightthickness=0)
        self.palette.grid(row=3, column=0, columnspan=2, pady=(8, 0))

        side = tk.Frame(main, bg=PANEL, width=380)
        side.pack(side='right', fill='y')
        side.pack_propagate(False)
        ttk.Label(side, text='Настройки', style='Head.TLabel').pack(anchor='w', padx=14, pady=(12, 4))
        self.strength = self.slider(side, 'Сила теней', 0, 2, engine.Options.strength)
        self.folds = self.slider(side, 'Складки на ткани', 0, 1.3, engine.Options.folds)
        self.relief = self.slider(side, 'Рельеф кожи', 0, 1.5, engine.Options.skin_relief)
        self.grade = self.slider(side, 'Подбор цвета', 0, 1.5, engine.Options.grade)
        self.hue = self.slider(side, 'Сдвиг оттенка', 0, 2.5, engine.Options.hue_shift)
        self.saturation = self.slider(side, 'Насыщенность теней', 0, 2.5, engine.Options.saturation)
        self.tips = tk.BooleanVar(value=True)
        ttk.Checkbutton(side, text='Рваная линия волос', variable=self.tips, command=self.changed).pack(anchor='w', padx=14, pady=(4, 2))
        row = tk.Frame(side, bg=PANEL)
        row.pack(fill='x', padx=14, pady=(6, 10))
        ttk.Label(row, text='Вариант').pack(side='left')
        self.seed = tk.IntVar(value=1)
        self.seed_box = ttk.Spinbox(row, from_=1, to=99999, width=7, textvariable=self.seed, command=self.changed)
        self.seed_box.pack(side='left', padx=8)
        self.seed_box.bind('<Return>', lambda _: self.changed())
        ttk.Button(row, text='Другой', command=self.another).pack(side='left')

        ttk.Label(side, text='Разметка материалов', style='Head.TLabel').pack(anchor='w', padx=14, pady=(4, 0))
        ttk.Label(side, text='Выберите материал и кликните по цвету на текстуре.', style='Muted.TLabel').pack(anchor='w', padx=14)
        self.brush = tk.StringVar(value='cloth')
        grid = tk.Frame(side, bg=PANEL)
        grid.pack(fill='x', padx=14, pady=4)
        for n, (m, label, colour) in enumerate(MATERIALS):
            cell = tk.Frame(grid, bg=PANEL)
            cell.grid(row=n // 2, column=n % 2, sticky='w', padx=(0, 12))
            tk.Frame(cell, bg=colour, width=10, height=10).pack(side='left', padx=(0, 4))
            ttk.Radiobutton(cell, text=label, value=m, variable=self.brush).pack(side='left')
        self.tolerance = self.slider(side, 'Разность цвета', 0, 30, 3, live=False)
        ttk.Label(side, text='0 — только точно такой цвет, больше — и похожие на него.', style='Muted.TLabel').pack(anchor='w', padx=14)
        row = tk.Frame(side, bg=PANEL)
        row.pack(fill='x', padx=14, pady=(2, 0))
        self.contiguous = tk.BooleanVar(value=True)
        self.one_part = tk.BooleanVar(value=True)
        ttk.Checkbutton(row, text='Только смежные', variable=self.contiguous).pack(side='left', padx=(0, 10))
        ttk.Checkbutton(row, text='Только эта часть тела', variable=self.one_part).pack(side='left')
        self.texture = tk.Canvas(side, width=256, height=256, bg=BG, highlightthickness=0, cursor='crosshair')
        self.texture.pack(padx=14, pady=(6, 4))
        self.texture.bind('<Button-1>', self.paint)
        row = tk.Frame(side, bg=PANEL)
        row.pack(anchor='w', padx=14)
        ttk.Button(row, text='Отменить', command=self.undo).pack(side='left', padx=(0, 6))
        ttk.Button(row, text='Сбросить разметку', command=self.clear_paint).pack(side='left')
        self.bind('<Control-z>', lambda _: self.undo())

        self.status = tk.StringVar()
        tk.Label(self, textvariable=self.status, bg=PANEL, fg=MUTED, anchor='w', padx=12, pady=4).pack(side='bottom', fill='x')

    def view(self, parent, title, column):
        tk.Label(parent, text=title, bg=BG, fg=TEXT, font=('Segoe UI', 11, 'bold')).grid(row=0, column=column)
        c = tk.Canvas(parent, width=VIEW, height=VIEW, bg='#1a1d23', highlightthickness=0, cursor='fleur')
        c.grid(row=1, column=column, padx=4)
        c.bind('<ButtonPress-1>', self.press)
        c.bind('<B1-Motion>', self.move)
        return c

    def slider(self, parent, title, low, high, value, live=True):
        var = tk.DoubleVar(value=value)
        row = tk.Frame(parent, bg=PANEL)
        row.pack(fill='x', padx=14, pady=2)
        ttk.Label(row, text=title, width=21).pack(side='left')
        shown = ttk.Label(row, text='%.2f' % value, width=5, style='Muted.TLabel')
        shown.pack(side='right')

        def moved(v):
            shown.configure(text='%.2f' % float(v))
            if live:
                self.changed()

        ttk.Scale(row, from_=low, to=high, variable=var, command=moved).pack(side='left', fill='x', expand=True, padx=6)
        return var

    # ---------------------------------------------------------------------------------------- work
    def load(self, path):
        try:
            image = Image.open(path).convert('RGBA')
            if image.width < 64 or image.height < 64:
                raise ValueError('нужен скин не меньше 64×64, а тут %d×%d' % image.size)
        except Exception as e:
            messagebox.showerror('Не удалось открыть', str(e))
            return
        self.path, self.flat = path, image
        self.materials = {}
        self.history = []
        self.seed.set(engine.seed_of(os.path.basename(path)) % 99999 + 1)
        self.file_label.configure(text=os.path.basename(path))
        self.refresh()

    def options(self):
        return engine.Options(strength=self.strength.get(), folds=self.folds.get(), hair_tips=self.tips.get(), skin_relief=self.relief.get(),
                              grade=self.grade.get(), hue_shift=self.hue.get(), saturation=self.saturation.get(), seed=self.seed_value(), materials=self.materials)

    def seed_value(self):
        try:
            return int(self.seed.get())
        except (tk.TclError, ValueError):
            return 1

    def changed(self):
        if self.flat is None:
            return
        if self.pending:
            self.after_cancel(self.pending)
        self.pending = self.after(120, self.refresh)

    def another(self):
        self.seed.set(random.randint(1, 99999))
        self.changed()

    def refresh(self):
        self.pending = None
        if self.flat is None:
            return
        opts = self.options()
        relief = engine.load_skin_relief(self.skin_base, opts.seed)
        self.shaded = engine.shade(self.flat, opts, relief)
        self.status.set('Готово. Рельеф кожи: ' + ('из базы city_steve' if relief else 'база не найдена, только общая форма'))
        self.draw_views()
        self.draw_texture()
        self.draw_palette(opts)

    def draw_palette(self, opts):
        """Each main colour of the skin and the ramp made from it; the framed cell is the original."""
        self.palette.delete('all')
        ramps = engine.palette_of(self.flat, opts)
        cell = min(26, (2 * VIEW - 10 * len(ramps)) // max(1, 6 * len(ramps)))
        for n, (_, _, ramp) in enumerate(ramps):
            x0 = n * (6 * cell + 10)
            for i, c in enumerate(ramp):
                self.palette.create_rectangle(x0 + i * cell, 22, x0 + (i + 1) * cell, 22 + cell, fill='#%02x%02x%02x' % c, width=0)
            self.palette.create_rectangle(x0 + 3 * cell, 22, x0 + 4 * cell, 22 + cell, outline='#ffffff', width=1)
        self.palette.create_text(0, 10, anchor='w', text='Палитра: основные цвета скина и их оттенки, белая рамка — основной цвет после подбора', fill=MUTED)

    def draw_views(self):
        if self.flat is None:
            return
        scale = VIEW / 40
        for key, canvas, image in (('before', self.canvas_before, self.flat), ('after', self.canvas_after, self.shaded)):
            pic = engine.render(image, self.yaw, self.pitch, scale=int(scale)).resize((VIEW, VIEW), Image.NEAREST)
            self.pics[key] = ImageTk.PhotoImage(pic)
            canvas.delete('all')
            canvas.create_image(0, 0, anchor='nw', image=self.pics[key])

    def draw_texture(self):
        crop = self.flat.crop((0, 0, 64, 64))
        board = Image.new('RGBA', (64, 64), (34, 37, 44, 255))
        board.paste(crop, (0, 0), crop)
        a = np.asarray(board).copy()
        for (x, y), m in self.materials.items():
            tint = MATERIAL_COLOUR[m].lstrip('#')
            rgb = np.array([int(tint[i:i + 2], 16) for i in (0, 2, 4)])
            a[y, x, :3] = (a[y, x, :3] * .45 + rgb * .55).astype(np.uint8)
        self.pics['texture'] = ImageTk.PhotoImage(Image.fromarray(a, 'RGBA').resize((256, 256), Image.NEAREST))
        self.texture.delete('all')
        self.texture.create_image(0, 0, anchor='nw', image=self.pics['texture'])

    # ---------------------------------------------------------------------------------------- hands
    def press(self, e):
        self.drag = (e.x, e.y, self.yaw, self.pitch)

    def move(self, e):
        if not self.drag:
            return
        x, y, yaw, pitch = self.drag
        self.yaw = yaw + (e.x - x) * .6
        self.pitch = max(-40, min(50, pitch + (e.y - y) * .4))
        self.draw_views()

    def turn_to(self, yaw):
        self.yaw, self.pitch = yaw, 10.0
        self.draw_views()

    def paint(self, e):
        if self.flat is None:
            return
        x, y = e.x // 4, e.y // 4
        filled = self.fill_from(x, y)
        if not filled:
            self.status.set('Здесь на текстуре пусто.')
            return
        self.history.append(dict(self.materials))
        for p in filled:
            if self.brush.get() == AUTO:
                self.materials.pop(p, None)
            else:
                self.materials[p] = self.brush.get()
        self.refresh()
        self.status.set('Размечено пикселей: %d' % len(filled))

    def fill_from(self, x, y):
        """What a click takes: pixels close to the clicked colour, not to their neighbour, or a chain of small
        differences would run over the whole skin. By default they must touch it and be on the same part of the body."""
        a = np.asarray(self.flat.crop((0, 0, 64, 64)))
        if not (0 <= x < 64 and 0 <= y < 64) or a[y, x, 3] <= 128:
            return []
        lab = engine.srgb_to_oklab(a[..., :3].astype(float))
        near = (np.linalg.norm(lab - lab[y, x], axis=-1) <= self.tolerance.get() / 100 + 1e-6) & (a[..., 3] > 128)
        if self.one_part.get():
            near &= self.part_mask(x, y)
        if not self.contiguous.get():
            return [(int(px), int(py)) for py, px in zip(*np.nonzero(near))]
        seen, stack = {(x, y)}, [(x, y)]
        while stack:
            cx, cy = stack.pop()
            for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                if 0 <= nx < 64 and 0 <= ny < 64 and (nx, ny) not in seen and near[ny, nx]:
                    seen.add((nx, ny))
                    stack.append((nx, ny))
        return list(seen)

    def part_mask(self, x, y):
        """The texture of the part of the body that pixel belongs to."""
        for part in engine.PARTS:
            patches = list(engine.patches_of(part))
            if any(((p.tx == x) & (p.ty == y)).any() for p in patches):
                mask = np.zeros((64, 64), bool)
                for p in patches:
                    mask[p.ty, p.tx] = True
                return mask
        return np.ones((64, 64), bool)

    def undo(self):
        if self.history:
            self.materials = self.history.pop()
            self.refresh()

    def clear_paint(self):
        if self.materials:
            self.history.append(dict(self.materials))
        self.materials = {}
        self.refresh()

    # ---------------------------------------------------------------------------------------- files
    def open_file(self):
        path = filedialog.askopenfilename(title='Плоский скин', filetypes=(('PNG', '*.png'),))
        if path:
            self.load(path)

    def default_output(self):
        return os.path.splitext(self.path)[0] + '_shaded.png'

    def save(self):
        if self.shaded is None:
            return
        out = self.default_output()
        self.shaded.save(out)
        self.status.set('Сохранено: ' + out)

    def save_as(self):
        if self.shaded is None:
            return
        out = filedialog.asksaveasfilename(defaultextension='.png', initialfile=os.path.basename(self.default_output()), filetypes=(('PNG', '*.png'),))
        if out:
            self.shaded.save(out)
            self.status.set('Сохранено: ' + out)

    def batch(self):
        source = filedialog.askdirectory(title='Папка с плоскими скинами')
        if not source:
            return
        target = filedialog.askdirectory(title='Куда сохранить')
        if not target:
            return
        done = 0
        for f in sorted(os.listdir(source)):
            if not f.lower().endswith('.png'):
                continue
            image = Image.open(os.path.join(source, f)).convert('RGBA')
            if image.width < 64 or image.height < 64:
                continue
            seed = engine.seed_of(f) % 99999 + 1
            opts = engine.Options(strength=self.strength.get(), folds=self.folds.get(), hair_tips=self.tips.get(), skin_relief=self.relief.get(),
                                  grade=self.grade.get(), hue_shift=self.hue.get(), saturation=self.saturation.get(), seed=seed)
            engine.shade(image, opts, engine.load_skin_relief(self.skin_base, seed)).save(os.path.join(target, f))
            done += 1
        self.status.set('Обработано скинов: %d, папка %s' % (done, target))


if __name__ == '__main__':
    App(sys.argv[1] if len(sys.argv) > 1 else None).mainloop()
