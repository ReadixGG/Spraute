/**
 * Модель GUI и генерация кода `create ui { ... }` — строго по движку Spraute:
 *  - виджеты и их свойства: UiTemplate.java (build* методы) + SprauteScriptScreen.parseOneWidget
 *  - root-свойства: UiTemplate.buildFromRuntime (size, background/bg, id, pos, canClose, dimBackground)
 *  - разрешённые имена свойств: ScriptParser.WIDGET_PROPERTY_NAMES
 * Дефолты в описаниях совпадают с движком: свойство, равное дефолту, не пишется в код.
 */

export const ENGINE_ROOT_DEFAULTS = {
  w: 200,
  h: 150,
  background: '#C0101010',
  canClose: true,
  dimBackground: false,
};

/**
 * Описание типов виджетов движка.
 * args: позиционные аргументы конструктора text("id","txt")
 * props: имя -> { def, kind } — kind: coord|size|color|float|int|bool|str|enum|list4
 * container: может иметь детей (clip, scroll)
 */
export const WIDGET_DEFS = {
  text: {
    label: 'Текст', icon: 'title',
    args: [
      { name: 'id', def: 'label' },
      { name: 'text', def: 'Текст' },
    ],
    defaults: { w: 0, h: 10 },
    props: {
      color: { def: '#EAEAEA', kind: 'color' },
      scale: { def: 1, kind: 'float' },
      wrap: { def: '', kind: 'coord' },
      align: { def: 'left', kind: 'enum', options: ['left', 'center', 'right'] },
      anchorX: { def: 0, kind: 'float' },
      anchorY: { def: 0, kind: 'float' },
      maxLines: { def: 0, kind: 'int' },
      maxChars: { def: 0, kind: 'int' },
      tooltip: { def: '', kind: 'str' },
    },
  },
  button: {
    label: 'Кнопка', icon: 'smart_button',
    args: [
      { name: 'id', def: 'btn' },
      { name: 'label', def: 'Кнопка' },
    ],
    defaults: { w: 100, h: 22 },
    props: {
      color: { def: '#55336688', kind: 'color' },
      hover: { def: '#66447799', kind: 'color' },
      texture: { def: '', kind: 'texture' },
      labelWrap: { def: 0, kind: 'int' },
      labelScale: { def: 1, kind: 'float' },
      subLabel: { def: '', kind: 'str' },
      subScale: { def: 0.65, kind: 'float' },
      slice_borders: { def: 0, kind: 'int' },
      slice_scale: { def: 1, kind: 'float' },
      tooltip: { def: '', kind: 'str' },
    },
    events: ['onClick'],
  },
  image: {
    label: 'Картинка', icon: 'image',
    args: [
      { name: 'id', def: 'img' },
      { name: 'texture', def: 'textures/gui/icon.png' },
    ],
    defaults: { w: 32, h: 32 },
    props: {
      slice_borders: { def: 0, kind: 'int' },
      slice_scale: { def: 1, kind: 'float' },
      tooltip: { def: '', kind: 'str' },
    },
  },
  rect: {
    label: 'Прямоугольник', icon: 'rectangle',
    args: [{ name: 'id', def: 'rect' }],
    defaults: { w: 32, h: 32 },
    props: {
      color: { def: '#FFFFFFFF', kind: 'color' },
    },
  },
  panel: {
    label: 'Панель', icon: 'dashboard',
    args: [{ name: 'id', def: 'panel' }],
    defaults: { w: 32, h: 32 },
    props: {
      color: { def: '#FFFFFFFF', kind: 'color' },
    },
  },
  divider: {
    label: 'Разделитель', icon: 'horizontal_rule',
    args: [{ name: 'id', def: 'div' }],
    defaults: { w: 100, h: 1 },
    props: {
      color: { def: '#44FFFFFF', kind: 'color' },
    },
  },
  gridBg: {
    label: 'Сетка-фон', icon: 'grid_4x4',
    args: [{ name: 'id', def: 'grid' }],
    defaults: { w: 100, h: 100 },
    props: {
      gridType: { def: 'hv', kind: 'enum', options: ['hv', 'h', 'v'] },
      cellSize: { def: 20, kind: 'int' },
      thickness: { def: 1, kind: 'int' },
      color: { def: '#44FFFFFF', kind: 'color' },
    },
  },
  input: {
    label: 'Поле ввода', icon: 'edit',
    args: [
      { name: 'id', def: 'input' },
      { name: 'placeholder', def: '...' },
    ],
    defaults: { w: 100, h: 16 },
    props: {
      text: { def: '', kind: 'str' },
      color: { def: '#FFFFFF', kind: 'color' },
      bgColor: { def: '#FF000000', kind: 'color' },
      outlineColor: { def: '#FFAAAAAA', kind: 'color' },
      scale: { def: 1, kind: 'float' },
      maxChars: { def: 32, kind: 'int' },
      inputType: { def: 'text', kind: 'enum', options: ['text', 'number'] },
      tooltip: { def: '', kind: 'str' },
    },
  },
  item: {
    label: 'Предмет', icon: 'category',
    args: [
      { name: 'id', def: 'item' },
      { name: 'item', def: 'minecraft:diamond' },
    ],
    defaults: { w: 16, h: 16, sizeSingle: true },
    props: {
      tooltip: { def: '', kind: 'str' },
    },
  },
  block: {
    label: 'Блок', icon: 'deployed_code',
    args: [
      { name: 'id', def: 'block' },
      { name: 'block', def: 'minecraft:stone' },
    ],
    defaults: { w: 16, h: 16, sizeSingle: true },
    props: {},
  },
  entity: {
    label: 'Сущность/НИП', icon: 'person',
    args: [
      { name: 'entity', def: '_eventNpc' },
      { name: 'id', def: 'npc_view' },
    ],
    defaults: { w: 64, h: 96 },
    props: {
      scale: { def: 1, kind: 'float' },
      autoScale: { def: false, kind: 'bool' },
      feetCrop: { def: 0.38, kind: 'float' },
      nameTag: { def: false, kind: 'bool' },
      noLookAt: { def: false, kind: 'bool' },
      noFollowCursor: { def: false, kind: 'bool' },
      noHurtAnim: { def: false, kind: 'bool' },
      animation: { def: '', kind: 'str' },
      modelGeo: { def: '', kind: 'str' },
      modelTexture: { def: '', kind: 'texture' },
      modelAnim: { def: '', kind: 'str' },
      modelIdle: { def: '', kind: 'str' },
      clipEntity: { def: false, kind: 'bool' },
      tooltip: { def: '', kind: 'str' },
    },
  },
  slot: {
    label: 'Слот', icon: 'check_box_outline_blank',
    args: [{ name: 'id', def: 'slot' }],
    defaults: { w: 18, h: 18, noSize: true },
    props: {},
  },
  playerInventory: {
    label: 'Инвентарь игрока', icon: 'backpack',
    args: [{ name: 'id', def: 'inv' }],
    defaults: { w: 162, h: 76, noSize: true },
    props: {},
  },
  group: {
    label: 'Группа', icon: 'folder',
    args: [{ name: 'id', def: 'group' }],
    defaults: { w: 100, h: 100 },
    container: true,
    props: {
      alpha: { def: 1, kind: 'float' },
      rotation: { def: 0, kind: 'float' },
      pivotX: { def: 0.5, kind: 'float' },
      pivotY: { def: 0.5, kind: 'float' },
    },
  },
  clip: {
    label: 'Клип (контейнер)', icon: 'crop',
    args: [{ name: 'id', def: 'clip' }],
    defaults: { w: 100, h: 100 },
    container: true,
    props: {
      alpha: { def: 1, kind: 'float' },
    },
  },
  scroll: {
    label: 'Скролл', icon: 'swap_vert',
    args: [{ name: 'id', def: 'scroll' }],
    defaults: { w: 100, h: 100 },
    container: true,
    props: {
      contentH: { def: '', kind: 'coord' },
      color: { def: '#00000000', kind: 'color' },
      scrollbar: { def: true, kind: 'bool' },
      autoScrollbar: { def: false, kind: 'bool' },
    },
  },
};

let _uid = 0;
export function guiUid() {
  _uid = (_uid + 1) % 0xffffff;
  return 'w' + Date.now().toString(36).slice(-4) + _uid.toString(36);
}

export function createEmptyGuiModel(name = 'my_ui') {
  return {
    kind: 'gui',
    name,
    openMode: 'gui', // gui | overlay
    root: {
      size: ['60%', '70%'],
      background: '#C0101010',
      canClose: true,
      dimBackground: false,
    },
    widgets: [],
  };
}

export function createWidget(type) {
  const def = WIDGET_DEFS[type];
  if (!def) return null;
  const w = {
    uid: guiUid(),
    type,
    args: def.args.map(a => a.def),
    pos: [10, 10],
    size: def.defaults.noSize ? null : [def.defaults.w, def.defaults.h],
    layer: 0,
    props: {},
    events: {},
    children: def.container ? [] : undefined,
  };
  // Уникальный id в args[0] (для entity id — второй аргумент)
  const idIdx = type === 'entity' ? 1 : 0;
  w.args[idIdx] = def.args[idIdx].def + '_' + w.uid.slice(-4);
  if (type === 'entity') {
    w.props.autoScale = true;
  }
  return w;
}

export function normalizeGuiModel(raw) {
  const m = createEmptyGuiModel(raw?.name || 'my_ui');
  if (!raw || typeof raw !== 'object') return m;
  m.openMode = raw.openMode === 'overlay' ? 'overlay' : 'gui';
  if (raw.root && typeof raw.root === 'object') {
    m.root = { ...m.root, ...raw.root };
  }
  const fixWidget = (w) => {
    if (!w || !WIDGET_DEFS[w.type]) return null;
    const def = WIDGET_DEFS[w.type];
    const out = {
      uid: w.uid || guiUid(),
      type: w.type,
      args: Array.isArray(w.args) ? [...w.args] : def.args.map(a => a.def),
      pos: Array.isArray(w.pos) ? [...w.pos] : [0, 0],
      size: def.defaults.noSize ? null : (Array.isArray(w.size) ? [...w.size] : [def.defaults.w, def.defaults.h]),
      layer: Number(w.layer) || 0,
      props: { ...(w.props || {}) },
      events: { ...(w.events || {}) },
      children: def.container ? (Array.isArray(w.children) ? w.children.map(fixWidget).filter(Boolean) : []) : undefined,
    };
    while (out.args.length < def.args.length) out.args.push(def.args[out.args.length].def);
    return out;
  };
  m.widgets = Array.isArray(raw.widgets) ? raw.widgets.map(fixWidget).filter(Boolean) : [];
  return m;
}

export function walkGuiWidgets(widgets, fn, parent = null) {
  if (!widgets) return;
  for (let i = 0; i < widgets.length; i++) {
    const w = widgets[i];
    fn(w, parent, widgets, i);
    if (w.children) walkGuiWidgets(w.children, fn, w);
  }
}

export function findGuiWidget(model, uid) {
  let found = null;
  walkGuiWidgets(model.widgets, (w) => { if (w.uid === uid) found = w; });
  return found;
}

export function findGuiWidgetParentList(model, uid) {
  let res = null;
  walkGuiWidgets(model.widgets, (w, parent, list, idx) => {
    if (w.uid === uid) res = { parent, list, idx };
  });
  return res;
}

/** Границы виджета в пикселях относительно родителя. */
export function widgetBoundsPx(w, parentW, parentH) {
  const def = WIDGET_DEFS[w.type];
  if (!def) return { x: 0, y: 0, w: 10, h: 10 };
  const x = resolveCoord(w.pos?.[0] ?? 0, parentW);
  const y = resolveCoord(w.pos?.[1] ?? 0, parentH);
  let ww = def.defaults.w ?? 50;
  let hh = def.defaults.h ?? 50;
  if (w.size) {
    if (def.defaults.sizeSingle) {
      ww = hh = resolveCoord(w.size[0], parentW);
    } else {
      ww = resolveCoord(w.size[0], parentW);
      hh = resolveCoord(w.size[1], parentH);
    }
  }
  return { x, y, w: ww, h: hh };
}

function filterTopLevelUids(model, uids) {
  const set = new Set(uids);
  return uids.filter((uid) => {
    let cur = findGuiWidgetParentList(model, uid);
    while (cur?.parent) {
      if (set.has(cur.parent.uid)) return false;
      cur = findGuiWidgetParentList(model, cur.parent.uid);
    }
    return true;
  });
}

/** Объединить несколько виджетов одного родителя в group. */
export function groupGuiWidgets(model, uids, parentW, parentH) {
  if (!uids || uids.length < 2) return null;
  const topUids = filterTopLevelUids(model, uids);
  if (topUids.length < 2) return null;

  const first = findGuiWidgetParentList(model, topUids[0]);
  if (!first) return null;
  const { list } = first;
  for (const uid of topUids) {
    const f = findGuiWidgetParentList(model, uid);
    if (!f || f.list !== list) return null;
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  const items = topUids.map((uid) => {
    const w = findGuiWidget(model, uid);
    const b = widgetBoundsPx(w, parentW, parentH);
    minX = Math.min(minX, b.x);
    minY = Math.min(minY, b.y);
    maxX = Math.max(maxX, b.x + b.w);
    maxY = Math.max(maxY, b.y + b.h);
    return { w, idx: findGuiWidgetParentList(model, uid).idx };
  }).sort((a, b) => a.idx - b.idx);

  const group = createWidget('group');
  group.pos = [minX, minY];
  group.size = [Math.max(1, maxX - minX), Math.max(1, maxY - minY)];

  for (const { w } of items) {
    const b = widgetBoundsPx(w, parentW, parentH);
    w.pos = [b.x - minX, b.y - minY];
    if (w.size && !WIDGET_DEFS[w.type].defaults.sizeSingle) {
      w.size = [b.w, b.h];
    }
  }

  const minIdx = items[0].idx;
  for (let i = items.length - 1; i >= 0; i--) list.splice(items[i].idx, 1);
  group.children = items.map(({ w }) => w);
  list.splice(minIdx, 0, group);
  return group;
}

/** Разгруппировать group — дети возвращаются к родителю. */
export function ungroupGuiWidget(model, groupUid, parentW, parentH) {
  const found = findGuiWidgetParentList(model, groupUid);
  if (!found) return null;
  const group = found.list[found.idx];
  if (!group || group.type !== 'group' || !group.children?.length) return null;

  const gb = widgetBoundsPx(group, parentW, parentH);
  const children = group.children.map((child) => {
    const cb = widgetBoundsPx(child, gb.w, gb.h);
    child.pos = [gb.x + cb.x, gb.y + cb.y];
    return child;
  });
  found.list.splice(found.idx, 1, ...children);
  return children;
}

// ================= Кодогенерация =================

function q(s) {
  return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
}

/** Координата/размер: число как есть, "NN%" в кавычках. */
function coordVal(v) {
  if (typeof v === 'number' && Number.isFinite(v)) return String(Math.round(v));
  const s = String(v).trim();
  if (/^-?\d+$/.test(s)) return s;
  if (/^-?\d+(\.\d+)?%$/.test(s)) return q(s);
  return q(s);
}

function fmtVal(v, kind) {
  if (kind === 'bool') return v ? 'true' : 'false';
  if (kind === 'int') return String(Math.round(Number(v) || 0));
  if (kind === 'float') {
    const n = Number(v) || 0;
    return Number.isInteger(n) ? String(n) : String(n);
  }
  if (kind === 'coord') return coordVal(v);
  return q(v);
}

function isDefault(v, def) {
  if (v === undefined || v === null || v === '') return true;
  if (typeof def === 'number') return Number(v) === def;
  if (typeof def === 'boolean') return v === def || String(v) === String(def);
  return String(v) === String(def);
}

function isIdentifierLike(s) {
  return /^[a-zA-Z_][a-zA-Z0-9_.]*$/.test(String(s));
}

function widgetLines(w, indent) {
  const def = WIDGET_DEFS[w.type];
  if (!def) return [];
  const pad = '    '.repeat(indent);
  // entity(сущность, id): первый аргумент — выражение (переменная НИП/игрок), не строка
  const args = w.args.map((a, i) => {
    if (w.type === 'entity' && i === 0 && isIdentifierLike(a)) return String(a);
    return q(a);
  }).join(', ');
  const lines = [`${pad}${w.type}(${args}) {`];
  lines.push(`${pad}    pos = [${coordVal(w.pos[0])}, ${coordVal(w.pos[1])}]`);
  if (w.size && !def.defaults.noSize && w.type !== 'text') {
    if (def.defaults.sizeSingle) {
      lines.push(`${pad}    size = ${coordVal(w.size[0])}`);
    } else {
      lines.push(`${pad}    size = [${coordVal(w.size[0])}, ${coordVal(w.size[1])}]`);
    }
  }
  if (w.layer) lines.push(`${pad}    layer = ${Math.round(w.layer)}`);
  for (const [k, meta] of Object.entries(def.props || {})) {
    const v = w.props?.[k];
    if (isDefault(v, meta.def)) continue;
    lines.push(`${pad}    ${k} = ${fmtVal(v, meta.kind)}`);
  }
  for (const evName of def.events || []) {
    const body = w.events?.[evName];
    if (!body || !String(body).trim()) continue;
    lines.push(`${pad}    ${evName} {`);
    for (const line of String(body).split('\n')) {
      if (line.trim()) lines.push(`${pad}        ${line.trim()}`);
    }
    lines.push(`${pad}    }`);
  }
  for (const child of w.children || []) {
    lines.push(...widgetLines(child, indent + 1));
  }
  lines.push(`${pad}}`);
  return lines;
}

/**
 * Модель -> код create ui { ... } (без uiOpen — открытие отдельным блоком).
 * extraCode — строки, вставляемые внутрь блока (программные виджеты/циклы).
 */
export function guiModelToSprCode(model, extraCode = '') {
  if (!model?.name) return '// GUI: не задано имя\n';
  const lines = [`create ui ${model.name} {`];
  const r = model.root || {};
  if (Array.isArray(r.size)) {
    lines.push(`    size = [${coordVal(r.size[0])}, ${coordVal(r.size[1])}]`);
  }
  if (r.background && String(r.background) !== ENGINE_ROOT_DEFAULTS.background) {
    lines.push(`    background = ${q(r.background)}`);
  }
  if (Array.isArray(r.pos)) {
    lines.push(`    pos = [${coordVal(r.pos[0])}, ${coordVal(r.pos[1])}]`);
  }
  if (r.canClose === false) lines.push('    canClose = false');
  lines.push(`    dimBackground = ${r.dimBackground === true ? 'true' : 'false'}`);
  for (const w of model.widgets || []) {
    lines.push(...widgetLines(w, 1));
  }
  if (extraCode && extraCode.trim()) {
    for (const line of extraCode.replace(/\s+$/, '').split('\n')) {
      lines.push(line.trim() ? '    ' + line.replace(/\s+$/, '') : '');
    }
  }
  lines.push('}');
  return lines.join('\n');
}

/** Цвет движка #RRGGBB / #AARRGGBB -> css rgba(). Как parseColor в SprauteScriptScreen. */
export function engineColorToCss(s, fallback = 'rgba(255,255,255,1)') {
  if (!s) return fallback;
  const hex = String(s).startsWith('#') ? String(s).slice(1) : String(s);
  let a = 255, r, g, b;
  try {
    if (hex.length === 6) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
    } else if (hex.length === 8) {
      a = parseInt(hex.slice(0, 2), 16);
      r = parseInt(hex.slice(2, 4), 16);
      g = parseInt(hex.slice(4, 6), 16);
      b = parseInt(hex.slice(6, 8), 16);
    } else {
      return fallback;
    }
  } catch {
    return fallback;
  }
  if ([r, g, b].some(v => Number.isNaN(v))) return fallback;
  return `rgba(${r},${g},${b},${(a / 255).toFixed(3)})`;
}

/**
 * Ширины символов ванильного шрифта Minecraft (advance в игровых px, включая 1px промежуток).
 * Источник: ascii.png ванили; кириллица и все прочие глифы — 6px. Так же меряет Font.width() движка.
 */
const MC_CHAR_WIDTHS = {
  ' ': 4, '!': 2, '"': 5, "'": 3, '(': 5, ')': 5, '*': 5, ',': 2, '.': 2,
  ':': 2, ';': 2, '<': 5, '>': 5, '@': 7, 'I': 4, '[': 4, ']': 4, '`': 3,
  'f': 5, 'i': 2, 'k': 5, 'l': 3, 't': 4, '{': 5, '|': 2, '}': 5, '~': 7,
};

/** Ширина строки в игровых пикселях — как Font.width() в Minecraft. */
export function mcTextWidth(str) {
  let w = 0;
  for (const ch of String(str ?? '')) {
    w += MC_CHAR_WIDTHS[ch] ?? 6;
  }
  return w;
}

/** Высота строки шрифта Minecraft в игровых px. */
export const MC_LINE_HEIGHT = 9;

/** CSS-семейство для текста на холсте GUI (файл: app/public/fonts/minecraft.ttf). */
export const GUI_MC_FONT_FAMILY = 'var(--font-minecraft)';

/**
 * Масштаб отрисовки шрифта в редакторе относительно игровых px.
 * Подобран под minecraft.ttf: 1.5 даёт визуальное совпадение с игрой.
 */
export const MC_FONT_RENDER_SCALE = 1.38;

/** Размер рамки текста в игровых px (как Font.width / высота строки × scale). */
export function textBoxGamePx(w, pw, ph) {
  const def = WIDGET_DEFS.text;
  const p = (k) => (w.props?.[k] !== undefined && w.props?.[k] !== '') ? w.props[k] : def.props?.[k]?.def;
  const tScale = Number(p('scale')) || 1;
  const textStr = String(w.args?.[1] ?? '');
  const wrapW = p('wrap') ? resolveCoord(p('wrap'), pw) : 0;
  const naturalW = mcTextWidth(textStr) * tScale;
  const boxW = wrapW > 0 ? wrapW : naturalW;
  let lines = 1;
  if (wrapW > 0 && naturalW > 0) lines = Math.max(1, Math.ceil(naturalW / wrapW));
  const maxLines = Number(p('maxLines')) || 0;
  if (maxLines > 0) lines = Math.min(lines, maxLines);
  const boxH = lines * MC_LINE_HEIGHT * tScale;
  return { w: Math.max(1, boxW), h: Math.max(1, boxH) };
}

/** Разрешение координаты как readCoord: число -> px, "NN%" -> доля родителя. */
export function resolveCoord(v, parentSize) {
  if (v === undefined || v === null || v === '') return 0;
  if (typeof v === 'number') return v;
  const s = String(v).trim();
  if (s.endsWith('%')) {
    const pct = parseFloat(s.slice(0, -1));
    return Number.isNaN(pct) ? 0 : (pct / 100) * parentSize;
  }
  const n = parseFloat(s);
  return Number.isNaN(n) ? 0 : n;
}
