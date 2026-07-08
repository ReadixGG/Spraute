/**
 * Полноэкранный редактор GUI.
 * Рендер холста повторяет клиент движка (SprauteScriptScreen):
 *  - экран = разрешение монитора пользователя / GUI-масштаб (как в Minecraft);
 *  - панель create ui: size (px или %), background #AARRGGBB, по умолчанию по центру;
 *  - координаты/размеры виджетов: px относительно панели или "% от родителя" (readCoord);
 *  - цвета #RRGGBB / #AARRGGBB (parseColor);
 *  - порядок отрисовки: layer, затем порядок в дереве (order).
 */
import {
  WIDGET_DEFS, createWidget, createEmptyGuiModel, normalizeGuiModel,
  walkGuiWidgets, findGuiWidget, findGuiWidgetParentList,
  engineColorToCss, resolveCoord, guiModelToSprCode,
  MC_LINE_HEIGHT, MC_FONT_RENDER_SCALE, GUI_MC_FONT_FAMILY, mcTextWidth, guiUid,
  groupGuiWidgets, ungroupGuiWidget,
} from './gui-model.js';
import { applyGuiModelToBlock, readGuiModelFromBlock } from './gui-blocks.js';
import { drawNineSlice, texturePreviewScale } from './gui-nineslice.js';

const NINESLICE_WIDGET_TYPES = new Set(['image', 'button']);
const SLICE_PROP_KEYS = new Set(['slice_borders', 'slice_scale']);

/** Подсказки к свойствам (тултип при наведении на подпись поля). */
const PROP_HINTS = {
  // общие
  x: 'Позиция по горизонтали в игровых пикселях от левого края панели. Можно писать проценты: "50%" — от ширины родителя.',
  y: 'Позиция по вертикали в игровых пикселях от верхнего края панели. Можно "50%" — от высоты родителя.',
  w: 'Ширина в игровых пикселях, либо процент от родителя: "100%".',
  h: 'Высота в игровых пикселях, либо процент от родителя: "100%".',
  size: 'Размер (квадрат) в игровых пикселях, например 16 для предмета.',
  layer: 'Слой отрисовки: чем больше число, тем виджет выше (поверх остальных). Может быть отрицательным (фон).',
  color: 'Цвет в формате #RRGGBB или #AARRGGBB (AA — прозрачность: FF непрозрачно, 00 невидимо). Пример: #C0101010 — тёмный полупрозрачный.',
  tooltip: 'Всплывающая подсказка в игре при наведении курсора на виджет.',
  scale: 'Масштаб: 1 — обычный размер, 0.5 — вдвое меньше, 2 — вдвое больше.',
  // text
  wrap: 'Ширина переноса текста в пикселях (или "80%"). Пусто — не переносить.',
  align: 'Выравнивание текста: left — слева, center — по центру, right — справа (работает с wrap).',
  anchorX: 'Точка привязки по X (0..1): 0 — левый край текста в точке pos, 0.5 — центр, 1 — правый край.',
  anchorY: 'Точка привязки по Y (0..1): 0 — верх, 0.5 — середина, 1 — низ.',
  maxLines: 'Максимум строк текста, лишнее обрежется. 0 — без ограничения.',
  maxChars: 'Максимум символов. 0 — без ограничения (для поля ввода по умолчанию 32).',
  // button
  hover: 'Цвет кнопки при наведении курсора (#AARRGGBB).',
  texture: 'Путь к текстуре из папки проекта, например textures/gui/button.png. Заменяет цветную заливку.',
  labelWrap: 'Ширина переноса надписи кнопки в пикселях. 0 — не переносить.',
  labelScale: 'Масштаб надписи кнопки (1 — обычный).',
  subLabel: 'Вторая строка мелким текстом под основной надписью.',
  subScale: 'Масштаб второй строки (по умолчанию 0.65).',
  slice_borders: '9-slice: ширина неизменяемых краёв текстуры в пикселях (как sliceBorders в скрипте). Углы фиксированы, края тайлятся, центр растягивается. 0 — растянуть всю текстуру.',
  slice_scale: 'Множитель толщины края на экране (sliceScale). 0.42 — тонкая рамка, 1 — 1:1 с borders.',
  onClick: 'Код движка, выполняющийся при клике. Игрок доступен как _eventPlayer. Пример: chat(_eventPlayer, "клик!")',
  // input
  text: 'Начальный текст в поле ввода.',
  placeholder: 'Серая подсказка, видна пока поле пустое.',
  bgColor: 'Цвет фона поля ввода (#AARRGGBB).',
  outlineColor: 'Цвет рамки поля ввода (#AARRGGBB).',
  inputType: 'text — любой текст, number — только цифры.',
  // gridBg
  gridType: 'Какие линии рисовать: hv — сетка (горизонталь + вертикаль), h — только горизонтальные, v — только вертикальные.',
  cellSize: 'Размер ячейки сетки в пикселях.',
  thickness: 'Толщина линий сетки в пикселях.',
  // scroll
  contentH: 'Полная высота содержимого в пикселях. Если больше высоты скролла — появится прокрутка.',
  scrollbar: 'Показывать полосу прокрутки.',
  autoScrollbar: 'Показывать полосу прокрутки только когда контент не влезает.',
  // clip
  alpha: 'Прозрачность всего содержимого клипа: 1 — непрозрачно, 0 — невидимо.',
  rotation: 'Поворот группы в градусах. Можно анимировать через uiAnimate.',
  pivotX: 'Точка поворота по X (0..1): 0 — левый край, 0.5 — центр, 1 — правый.',
  pivotY: 'Точка поворота по Y (0..1): 0 — верх, 0.5 — центр, 1 — низ.',
  // entity
  autoScale: 'Автоматически подобрать масштаб модели по рамке виджета (size/crop).',
  feetCrop: 'Обрезка модели снизу (0..1): 0.38 — стандарт, прячет ноги ниже рамки.',
  nameTag: 'Показывать имя над головой.',
  noLookAt: 'НИП не поворачивает голову к игроку.',
  noFollowCursor: 'Модель не следит за курсором мыши.',
  noHurtAnim: 'Отключить красную вспышку урона.',
  animation: 'Имя анимации, или false — отключить анимацию вовсе.',
  modelGeo: 'Файл геометрии (geo/*.geo.json) — рендер модели без живой сущности.',
  modelTexture: 'Текстура модели (textures/entity/...).',
  modelAnim: 'Файл анимаций модели.',
  modelIdle: 'Имя idle-анимации.',
  clipEntity: 'Обрезать модель по рамке виджета.',
  // аргументы
  id: 'Уникальный идентификатор виджета — по нему работают uiUpdate, uiAnimate и события (on uiClick).',
  label: 'Надпись на кнопке.',
  item: 'ID предмета, например minecraft:diamond или apple.',
  block: 'ID блока, например minecraft:stone.',
  entity: 'Сущность: переменная НИПа (_eventNpc, npc из create npc) или игрок.',
};

/** Описания виджетов для палитры. */
const WIDGET_HINTS = {
  text: 'Строка текста. Поддерживает перенос (wrap), выравнивание и масштаб.',
  button: 'Кликабельная кнопка с надписью, hover-цветом и обработчиком onClick.',
  image: 'Картинка из папки textures проекта. Поддерживает 9-slice.',
  rect: 'Цветной прямоугольник — фон, рамки, подложки.',
  panel: 'То же, что прямоугольник (синоним rect).',
  divider: 'Тонкая горизонтальная линия-разделитель.',
  gridBg: 'Декоративная сетка из линий (фон в клетку) — удобно для инвентарей и «чертёжного» стиля. Ничего не хранит, чисто визуал.',
  input: 'Поле ввода текста. Читается через await uiInput / on uiInput.',
  item: 'Иконка предмета Minecraft (3D-рендер как в инвентаре).',
  block: 'Иконка блока Minecraft.',
  entity: '3D-модель сущности/НИПа прямо в интерфейсе.',
  slot: 'Настоящий слот инвентаря 18×18 — в него можно класть предметы (режим контейнера).',
  playerInventory: 'Инвентарь игрока целиком (9×3 + хотбар).',
  clip: 'Контейнер: обрезает всё, что выходит за его границы. Дети позиционируются относительно него.',
  group: 'Группа: контейнер без обрезки. Удобно для совместного перемещения и анимации нескольких виджетов.',
  scroll: 'Прокручиваемая область. contentH — полная высота содержимого.',
};

const st = {
  open: false,
  block: null,
  model: null,
  selectedUid: null,
  selectedUids: new Set(),
  guiScale: 2,
  textures: [],
  texDataUrls: new Map(),
  texImages: new Map(),
  drag: null, // { mode: 'move'|'resize', uid, startMx, startMy, startPos, startSize, handle }
  zoom: 1,
  unitMode: '%',        // '%' | 'px' — единицы для новых виджетов и перетаскивания
  gridSnap: true,       // привязка к сетке редактора
  gridStep: 5,          // шаг сетки редактора в игровых px
  gridShow: true,       // показывать сетку на панели
  widgetSnap: true,     // привязка к краям/центрам других виджетов при перетаскивании
  scrollOffsets: new Map(), // uid скролла -> смещение (игровые px), только для предпросмотра
  clipboard: null,      // скопированное поддерево виджета
  pasteCount: 0,
};

/** Округление с привязкой к сетке редактора (в игровых px). */
function snap(v) {
  if (!st.gridSnap || st.gridStep <= 1) return Math.round(v);
  return Math.round(v / st.gridStep) * st.gridStep;
}

/** Игровые px → целые CSS px на холсте (сетка и виджеты совпадают). */
function gameToDisp(v, scale) {
  return Math.round(Number(v) * scale);
}

let _textMeasureCanvas;

/** Реальная ширина строки в CSS px (minecraft.ttf), а не таблица ванильных глифов. */
function measureMcFontWidth(text, fontSizePx) {
  if (!_textMeasureCanvas) _textMeasureCanvas = document.createElement('canvas');
  const ctx = _textMeasureCanvas.getContext('2d');
  if (!ctx) return mcTextWidth(text) * MC_FONT_RENDER_SCALE;
  ctx.font = `${fontSizePx}px Minecraft, monospace`;
  return ctx.measureText(String(text ?? '')).width;
}

function wrapTextLines(textStr, wrapGameW, tScale) {
  const s = String(textStr ?? '');
  if (!wrapGameW || wrapGameW <= 0) return [s];
  const lines = [];
  let line = '';
  for (const ch of s) {
    if (ch === '\n') {
      lines.push(line);
      line = '';
      continue;
    }
    const test = line + ch;
    if (line && mcTextWidth(test) * tScale > wrapGameW) {
      lines.push(line);
      line = ch;
    } else {
      line = test;
    }
  }
  if (line.length || !lines.length) lines.push(line);
  return lines;
}

/** Размер рамки текста: измерение шрифта редактора, с запасом под wrap. */
function textWidgetSizePx(w, pw, ph) {
  const def = WIDGET_DEFS.text;
  const p = (k) => (w.props?.[k] !== undefined && w.props?.[k] !== '') ? w.props[k] : def.props?.[k]?.def;
  const tScale = Number(p('scale')) || 1;
  const textStr = String(w.args?.[1] ?? '');
  const wrapW = p('wrap') ? resolveCoord(p('wrap'), pw) : 0;
  const fontSizeRef = MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE * tScale;
  const lines = wrapW > 0 ? wrapTextLines(textStr, wrapW, tScale) : [textStr];
  let maxMeasured = 0;
  for (const line of lines) {
    maxMeasured = Math.max(maxMeasured, measureMcFontWidth(line, fontSizeRef));
  }
  let lineCount = lines.length;
  const maxLines = Number(p('maxLines')) || 0;
  if (maxLines > 0) lineCount = Math.min(lineCount, maxLines);
  const boxW = Math.max(1, Math.ceil(maxMeasured / MC_FONT_RENDER_SCALE));
  const boxH = Math.max(1, lineCount * MC_LINE_HEIGHT * tScale);
  return { w: boxW, h: boxH };
}

/** SVG-сетка редактора — линии ровные даже при дробном zoom. */
function buildSnapGridEl(panelW, panelH, scale) {
  const g = document.createElement('div');
  g.className = 'gui-ed-snapgrid';
  const step = st.gridStep;
  if (step <= 1) return g;

  const dispW = gameToDisp(panelW, scale);
  const dispH = gameToDisp(panelH, scale);
  if (dispW < 1 || dispH < 1) return g;

  const parts = [];
  for (let gx = 0; gx <= panelW; gx += step) {
    const x = gameToDisp(gx, scale) + 0.5;
    parts.push(`M${x} 0V${dispH}`);
  }
  for (let gy = 0; gy <= panelH; gy += step) {
    const y = gameToDisp(gy, scale) + 0.5;
    parts.push(`M0 ${y}H${dispW}`);
  }

  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${dispW}" height="${dispH}" shape-rendering="crispEdges">` +
    `<path d="${parts.join(' ')}" fill="none" stroke="rgba(56,189,248,0.38)" stroke-width="1"/></svg>`;
  g.style.backgroundImage = `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
  g.style.backgroundSize = `${dispW}px ${dispH}px`;
  g.style.backgroundRepeat = 'no-repeat';
  return g;
}

const WIDGET_SNAP_THRESH = 6;

function widgetBounds(w, pw, ph) {
  const x = resolveCoord(w.pos?.[0] ?? 0, pw);
  const y = resolveCoord(w.pos?.[1] ?? 0, ph);
  const sz = widgetSizePx(w, pw, ph);
  return { x, y, w: sz.w, h: sz.h, cx: x + sz.w / 2, cy: y + sz.h / 2, r: x + sz.w, b: y + sz.h };
}

function siblingsOf(model, uid) {
  const found = findGuiWidgetParentList(model, uid);
  if (!found) return [];
  return found.list.filter(w => w.uid !== uid);
}

/** Привязка к краям/центрам соседних виджетов и панели при перетаскивании. */
function snapToWidgets(x, y, w, h, others, pw, ph) {
  const xs = [0, pw / 2, pw];
  const ys = [0, ph / 2, ph];
  for (const o of others) {
    const b = widgetBounds(o, pw, ph);
    xs.push(b.x, b.cx, b.r);
    ys.push(b.y, b.cy, b.b);
  }

  const snapAxis = (pos, size, targets) => {
    const points = [pos, pos + size / 2, pos + size];
    let best = null;
    for (const p of points) {
      for (const t of targets) {
        const dist = Math.abs(p - t);
        if (dist <= WIDGET_SNAP_THRESH && (!best || dist < best.dist)) {
          best = { dist, delta: t - p, guide: t };
        }
      }
    }
    return best;
  };

  const sx = snapAxis(x, w, xs);
  const sy = snapAxis(y, h, ys);
  return {
    x: sx ? x + sx.delta : x,
    y: sy ? y + sy.delta : y,
    snappedX: !!sx,
    snappedY: !!sy,
    guides: { vx: sx ? [sx.guide] : [], hy: sy ? [sy.guide] : [] },
  };
}

function appendDragGuides(panelEl, guides, scale) {
  if (!guides || (!guides.vx?.length && !guides.hy?.length)) return;
  const box = document.createElement('div');
  box.className = 'gui-ed-guides';
  for (const x of guides.vx) {
    const line = document.createElement('div');
    line.className = 'gui-ed-guide-v';
    line.style.left = (gameToDisp(x, scale) + 0.5) + 'px';
    box.appendChild(line);
  }
  for (const y of guides.hy) {
    const line = document.createElement('div');
    line.className = 'gui-ed-guide-h';
    line.style.top = (gameToDisp(y, scale) + 0.5) + 'px';
    box.appendChild(line);
  }
  panelEl.appendChild(box);
}

/** px -> строка процентов с 1 знаком (как хранит модель и понимает readCoord движка). */
function pctStr(px, parent) {
  if (!parent) return Math.round(px);
  return (Math.round((px / parent) * 1000) / 10) + '%';
}

const isPct = (v) => typeof v === 'string' && String(v).trim().endsWith('%');

/** Записать координату в модель в текущих единицах поля (сохраняем формат значения). */
function coordInUnit(px, parent, wantPct) {
  return wantPct ? pctStr(px, parent) : Math.round(px);
}

const $ = (id) => document.getElementById(id);

function isSelected(uid) {
  return st.selectedUids.has(uid) || st.selectedUid === uid;
}

function getSelectionUids() {
  if (st.selectedUids.size > 0) return [...st.selectedUids];
  return st.selectedUid ? [st.selectedUid] : [];
}

function clearSelection() {
  st.selectedUids = new Set();
  st.selectedUid = null;
}

function setSelection(uid, { additive = false } = {}) {
  if (additive) {
    if (st.selectedUids.size === 0 && st.selectedUid) st.selectedUids.add(st.selectedUid);
    if (st.selectedUids.has(uid)) {
      if (st.selectedUids.size > 1) st.selectedUids.delete(uid);
    } else {
      st.selectedUids.add(uid);
    }
    st.selectedUid = uid;
  } else {
    st.selectedUids = new Set([uid]);
    st.selectedUid = uid;
  }
}

function deleteSelection() {
  const uids = getSelectionUids();
  if (!uids.length) return;
  const sorted = uids
    .map((uid) => findGuiWidgetParentList(st.model, uid))
    .filter(Boolean)
    .sort((a, b) => b.idx - a.idx);
  for (const found of sorted) found.list.splice(found.idx, 1);
  clearSelection();
  renderAll();
}

function doGroupSelection() {
  const uids = getSelectionUids();
  if (uids.length < 2) return;
  const first = findGuiWidget(st.model, uids[0]);
  const dims = widgetParentDims(first);
  const group = groupGuiWidgets(st.model, uids, dims.w, dims.h);
  if (!group) return;
  setSelection(group.uid);
  renderAll();
}

function doUngroupSelection() {
  const w = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
  if (!w || w.type !== 'group') return;
  const dims = widgetParentDims(w);
  const children = ungroupGuiWidget(st.model, w.uid, dims.w, dims.h);
  if (!children?.length) return;
  setSelection(children[0].uid);
  for (const c of children.slice(1)) st.selectedUids.add(c.uid);
  renderAll();
}

// ================= Открытие / закрытие =================

export async function openGuiEditor(block, textures = []) {
  st.block = block;
  st.textures = textures || [];
  st.model = readGuiModelFromBlock(block) || createEmptyGuiModel();
  st.selectedUid = null;
  st.selectedUids = new Set();
  st.open = true;

  $('gui-editor-overlay')?.classList.remove('hidden');
  document.body.classList.add('gui-editor-open');

  $('gui-ed-name').value = st.model.name || 'my_ui';
  ($('gui-ed-mode-' + (st.model.openMode === 'overlay' ? 'overlay' : 'gui')) || {}).checked = true;
  $('gui-ed-scale').value = String(st.guiScale);

  try {
    await document.fonts?.load?.(`${MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE}px Minecraft`);
  } catch (e) { /* шрифт не найден — monospace */ }

  renderPalette();
  renderRootProps();
  renderAll();
}

export function closeGuiEditor(save = true) {
  if (save && st.block && st.model) {
    syncHeaderIntoModel();
    applyGuiModelToBlock(st.block, st.model);
    if (typeof window.__sprauteGuiChanged === 'function') window.__sprauteGuiChanged();
  }
  st.open = false;
  st.block = null;
  $('gui-editor-overlay')?.classList.add('hidden');
  document.body.classList.remove('gui-editor-open');
}

function syncHeaderIntoModel() {
  st.model.name = ($('gui-ed-name')?.value || 'my_ui').replace(/[^a-zA-Z0-9_]/g, '_') || 'my_ui';
  st.model.openMode = $('gui-ed-mode-overlay')?.checked ? 'overlay' : 'gui';
}

// ================= Геометрия экрана =================

function gameScreenSize() {
  const sw = (window.screen?.width || 1920);
  const sh = (window.screen?.height || 1080);
  return { w: Math.round(sw / st.guiScale), h: Math.round(sh / st.guiScale) };
}

/** Размер панели в игровых px (как UiTemplate: default 200×150, % от экрана). */
function panelSizePx() {
  const scr = gameScreenSize();
  const size = st.model.root?.size;
  let w = 200, h = 150;
  if (Array.isArray(size)) {
    w = Math.round(resolveCoord(size[0], scr.w)) || 200;
    h = Math.round(resolveCoord(size[1], scr.h)) || 150;
  }
  return { w, h };
}

function panelPosPx(panel) {
  const scr = gameScreenSize();
  const pos = st.model.root?.pos;
  if (Array.isArray(pos)) {
    return {
      x: Math.round(resolveCoord(pos[0], scr.w)),
      y: Math.round(resolveCoord(pos[1], scr.h)),
    };
  }
  // как в игре: init() центрирует
  return { x: Math.round((scr.w - panel.w) / 2), y: Math.round((scr.h - panel.h) / 2) };
}

// ================= Рендер =================

function renderAll() {
  renderCanvas();
  renderTree();
  renderProps();
  renderCodePreview();
}

function widgetSizePx(w, pw, ph) {
  const def = WIDGET_DEFS[w.type];
  if (w.type === 'text') return textWidgetSizePx(w, pw, ph);
  if (def.defaults.noSize) {
    if (w.type === 'playerInventory') return { w: 162, h: 76 };
    return { w: 18, h: 18 };
  }
  if (def.defaults.sizeSingle) {
    const s = Math.round(resolveCoord(w.size?.[0] ?? def.defaults.w, pw)) || def.defaults.w;
    return { w: s, h: s };
  }
  return {
    w: Math.round(resolveCoord(w.size?.[0] ?? def.defaults.w, pw)) || def.defaults.w,
    h: Math.round(resolveCoord(w.size?.[1] ?? def.defaults.h, ph)) || def.defaults.h,
  };
}

function buildWidgetEl(w, pw, ph, scale) {
  const def = WIDGET_DEFS[w.type];
  const x = resolveCoord(w.pos?.[0] ?? 0, pw);
  const y = resolveCoord(w.pos?.[1] ?? 0, ph);
  const sz = widgetSizePx(w, pw, ph);
  const p = (k) => (w.props?.[k] !== undefined && w.props?.[k] !== '') ? w.props[k] : def.props?.[k]?.def;

  const el = document.createElement('div');
  el.className = 'gui-wd' + (isSelected(w.uid) ? ' selected' : '') + (st.selectedUids.size > 1 && st.selectedUids.has(w.uid) && st.selectedUid !== w.uid ? ' multi-selected' : '');
  el.dataset.uid = w.uid;
  el.style.left = gameToDisp(x, scale) + 'px';
  el.style.top = gameToDisp(y, scale) + 'px';
  el.style.width = Math.max(1, gameToDisp(sz.w, scale)) + 'px';
  el.style.height = Math.max(1, gameToDisp(sz.h, scale)) + 'px';
  el.style.zIndex = String(1000 + (Number(w.layer) || 0) * 10);

  switch (w.type) {
    case 'rect':
    case 'panel':
      el.style.background = engineColorToCss(p('color'), 'rgba(255,255,255,1)');
      break;
    case 'divider':
      el.style.background = engineColorToCss(p('color'), 'rgba(255,255,255,0.27)');
      el.style.height = Math.max(1, 1 * scale) + 'px';
      break;
    case 'text': {
      const tScale = Number(p('scale')) || 1;
      const textStr = String(w.args?.[1] ?? '');
      const wrapW = p('wrap') ? resolveCoord(p('wrap'), pw) : 0;
      const { w: boxW, h: boxH } = textWidgetSizePx(w, pw, ph);
      const fontPx = MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE * tScale * scale;
      el.style.width = Math.max(2, gameToDisp(boxW * MC_FONT_RENDER_SCALE, scale)) + 'px';
      el.style.height = Math.max(2, gameToDisp(boxH * MC_FONT_RENDER_SCALE, scale)) + 'px';
      el.style.color = engineColorToCss(p('color'), '#EAEAEA');
      el.style.fontSize = fontPx + 'px';
      el.style.lineHeight = fontPx + 'px';
      el.style.fontFamily = GUI_MC_FONT_FAMILY;
      el.style.imageRendering = 'pixelated';
      el.style.whiteSpace = wrapW > 0 ? 'normal' : 'nowrap';
      el.style.textAlign = p('align') || 'left';
      el.style.overflow = 'hidden';
      el.style.textShadow = `${scale}px ${scale}px 0 rgba(0,0,0,.55)`;
      const ax = Number(p('anchorX')) || 0;
      const ay = Number(p('anchorY')) || 0;
      if (ax || ay) el.style.transform = `translate(${-ax * 100}%, ${-ay * 100}%)`;
      el.textContent = textStr;
      break;
    }
    case 'button': {
      const tex = p('texture');
      const sliceB = Number(p('slice_borders')) || 0;
      const sliceSc = p('slice_scale') !== undefined && p('slice_scale') !== '' ? Number(p('slice_scale')) : 1;
      el.style.display = 'flex';
      el.style.flexDirection = 'column';
      el.style.alignItems = 'center';
      el.style.justifyContent = 'center';
      el.style.color = '#fff';
      el.style.fontFamily = GUI_MC_FONT_FAMILY;
      const labelPx = MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE * (Number(p('labelScale')) || 1) * scale;
      el.style.fontSize = labelPx + 'px';
      el.style.lineHeight = labelPx + 'px';
      el.style.textShadow = `${scale}px ${scale}px 0 rgba(0,0,0,.55)`;
      el.style.overflow = 'hidden';
      el.style.position = 'relative';
      const subPx = MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE * (Number(p('subScale')) || 0.65) * scale;
      const labelWrap = document.createElement('div');
      labelWrap.className = 'gui-wd-btn-labels';
      labelWrap.style.cssText = 'position:relative;z-index:1;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;';
      labelWrap.innerHTML = `<span>${escapeHtml(w.args?.[1] ?? '')}</span>` +
        (p('subLabel') ? `<span style="font-size:${subPx}px;line-height:${subPx}px;opacity:.8">${escapeHtml(p('subLabel'))}</span>` : '');
      el.appendChild(labelWrap);
      if (tex) {
        mountWidgetTexture(el, tex, sz.w, sz.h, sliceB, sliceSc, scale, null);
      } else {
        el.style.background = engineColorToCss(p('color'), 'rgba(85,51,102,0.53)');
        el.addEventListener('mouseenter', () => { el.style.background = engineColorToCss(p('hover'), el.style.background); });
        el.addEventListener('mouseleave', () => { el.style.background = engineColorToCss(p('color'), 'rgba(85,51,102,0.53)'); });
      }
      break;
    }
    case 'image': {
      el.style.imageRendering = 'pixelated';
      el.style.position = 'relative';
      el.style.overflow = 'hidden';
      const sliceB = Number(p('slice_borders')) || 0;
      const sliceSc = p('slice_scale') !== undefined && p('slice_scale') !== '' ? Number(p('slice_scale')) : 1;
      mountWidgetTexture(el, w.args?.[1] ?? '', sz.w, sz.h, sliceB, sliceSc, scale, 'rgba(120,120,160,.25)');
      break;
    }
    case 'gridBg': {
      const cellGame = Math.max(2, Number(p('cellSize')) || 20);
      const cellPx = Math.max(2, gameToDisp(cellGame, scale));
      const thPx = Math.max(1, gameToDisp(Number(p('thickness')) || 1, scale));
      const col = engineColorToCss(p('color'), 'rgba(255,255,255,0.27)');
      const gt = p('gridType') || 'hv';
      const layers = [];
      if (gt === 'hv' || gt === 'v') {
        layers.push(`repeating-linear-gradient(90deg, ${col} 0 ${thPx}px, transparent ${thPx}px ${cellPx}px)`);
      }
      if (gt === 'hv' || gt === 'h') {
        layers.push(`repeating-linear-gradient(0deg, ${col} 0 ${thPx}px, transparent ${thPx}px ${cellPx}px)`);
      }
      el.style.background = layers.join(',');
      break;
    }
    case 'input': {
      el.style.background = engineColorToCss(p('bgColor'), '#000');
      el.style.border = `${Math.max(1, scale)}px solid ${engineColorToCss(p('outlineColor'), '#AAA')}`;
      el.style.color = engineColorToCss(p('color'), '#FFF');
      el.style.fontFamily = GUI_MC_FONT_FAMILY;
      const inputPx = MC_LINE_HEIGHT * MC_FONT_RENDER_SCALE * (Number(p('scale')) || 1) * scale;
      el.style.fontSize = inputPx + 'px';
      el.style.lineHeight = inputPx + 'px';
      el.style.display = 'flex';
      el.style.alignItems = 'center';
      el.style.paddingLeft = (2 * scale) + 'px';
      el.style.boxSizing = 'border-box';
      const text = p('text');
      el.innerHTML = text
        ? escapeHtml(text)
        : `<span style="opacity:.45">${escapeHtml(w.args?.[1] ?? '')}</span>`;
      break;
    }
    case 'item':
    case 'block':
      el.classList.add('gui-wd-item');
      el.innerHTML = `<span class="material-symbols-outlined" style="font-size:${Math.max(10, sz.w * scale * 0.8)}px">${w.type === 'item' ? 'category' : 'deployed_code'}</span>`;
      el.title = w.args?.[1] ?? '';
      break;
    case 'slot':
      el.classList.add('gui-wd-slot');
      break;
    case 'playerInventory':
      el.classList.add('gui-wd-inv');
      el.innerHTML = buildInventoryPreview(scale);
      break;
    case 'entity':
      el.classList.add('gui-wd-entity');
      el.innerHTML = `<span class="material-symbols-outlined" style="font-size:${Math.max(14, sz.h * scale * 0.5)}px">person</span>`;
      el.title = String(w.args?.[0] ?? '');
      break;
    case 'clip': {
      el.classList.add('gui-wd-container', 'gui-wd-clip');
      el.style.opacity = String(Number(p('alpha')) || 1);
      break;
    }
    case 'group': {
      el.classList.add('gui-wd-container', 'gui-wd-group');
      el.style.opacity = String(Number(p('alpha')) || 1);
      const rot = Number(p('rotation')) || 0;
      if (rot) {
        const px = (Number(p('pivotX')) || 0.5) * 100;
        const py = (Number(p('pivotY')) || 0.5) * 100;
        el.style.transformOrigin = `${px}% ${py}%`;
        el.style.transform = `rotate(${rot}deg)`;
      }
      break;
    }
    case 'scroll': {
      el.classList.add('gui-wd-container', 'gui-wd-scroll', 'gui-wd-clip');
      el.style.background = engineColorToCss(p('color'), 'rgba(0,0,0,0)');
      break;
    }
  }

  if (w.children) {
    if (w.type === 'scroll') {
      // Дети скролла живут в контентной области contentH и смещаются колесом — как в игре.
      const contentH = w.props?.contentH ? Math.round(resolveCoord(w.props.contentH, ph)) : sz.h;
      const maxOff = Math.max(0, contentH - sz.h);
      let off = Math.min(st.scrollOffsets.get(w.uid) || 0, maxOff);
      st.scrollOffsets.set(w.uid, off);

      const inner = document.createElement('div');
      inner.className = 'gui-wd-scroll-inner';
      inner.style.position = 'absolute';
      inner.style.left = '0';
      inner.style.top = (-off * scale) + 'px';
      inner.style.width = '100%';
      inner.style.height = (contentH * scale) + 'px';
      for (const child of sortByLayer(w.children)) {
        inner.appendChild(buildWidgetEl(child, sz.w, contentH, scale));
      }
      el.appendChild(inner);

      // полоса прокрутки как в игре (справа, пропорциональная)
      if (maxOff > 0) {
        const bar = document.createElement('div');
        bar.className = 'gui-wd-scrollbar';
        const barH = Math.max(8, (sz.h / contentH) * sz.h * scale);
        bar.style.height = barH + 'px';
        bar.style.top = ((off / maxOff) * (sz.h * scale - barH)) + 'px';
        el.appendChild(bar);
      }

      el.addEventListener('wheel', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const cur = st.scrollOffsets.get(w.uid) || 0;
        const next = Math.max(0, Math.min(maxOff, cur + Math.sign(e.deltaY) * 12));
        if (next !== cur) {
          st.scrollOffsets.set(w.uid, next);
          renderCanvas();
        }
      }, { passive: false });
    } else {
      for (const child of sortByLayer(w.children)) {
        el.appendChild(buildWidgetEl(child, sz.w, sz.h, scale));
      }
    }
  }

  el.addEventListener('mousedown', (e) => onWidgetMouseDown(e, w, pw, ph, scale));

  if (isSelected(w.uid) && getSelectionUids().length === 1 && !def.defaults.noSize && w.type !== 'text') {
    for (const h of ['se', 'e', 's']) {
      const hd = document.createElement('div');
      hd.className = 'gui-wd-handle gui-wd-handle-' + h;
      hd.dataset.handle = h;
      hd.addEventListener('mousedown', (e) => {
        e.stopPropagation();
        startDrag(e, w, pw, ph, scale, 'resize', h);
      });
      el.appendChild(hd);
    }
  }
  return el;
}

function buildInventoryPreview(scale) {
  // 9×3 + hotbar, слоты 18px — как ванильный инвентарь
  let html = '<div style="display:flex;flex-direction:column;gap:' + (4 * scale) + 'px">';
  for (const rows of [3, 1]) {
    html += '<div style="display:grid;grid-template-columns:repeat(9,' + (16 * scale) + 'px);gap:' + (2 * scale) + 'px">';
    for (let i = 0; i < rows * 9; i++) {
      html += `<div style="width:${16 * scale}px;height:${16 * scale}px;background:rgba(139,139,139,.35);border:1px solid rgba(55,55,55,.8)"></div>`;
    }
    html += '</div>';
  }
  return html + '</div>';
}

function sortByLayer(widgets) {
  return [...widgets].sort((a, b) => (Number(a.layer) || 0) - (Number(b.layer) || 0));
}

async function ensureTextureUrl(texPath) {
  if (!texPath || texPath.includes(':')) return null;
  if (st.texDataUrls.has(texPath)) return st.texDataUrls.get(texPath);
  if (!window.spraute) return null;
  try {
    const b64 = await window.spraute.readFile(texPath, 'base64');
    const url = `data:image/png;base64,${b64}`;
    st.texDataUrls.set(texPath, url);
    return url;
  } catch {
    return null;
  }
}

async function loadTextureImage(texPath) {
  if (!texPath) return null;
  if (st.texImages.has(texPath)) return st.texImages.get(texPath);
  const url = await ensureTextureUrl(texPath);
  if (!url) return null;
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      st.texImages.set(texPath, img);
      resolve(img);
    };
    img.onerror = () => resolve(null);
    img.src = url;
  });
}

/** Текстура виджета: 9-slice (как SprauteScriptScreen) или растяжение целиком. */
async function mountWidgetTexture(el, texPath, gameW, gameH, borders, sliceScale, displayScale, fallbackCss) {
  const img = await loadTextureImage(texPath);
  if (!img) {
    if (fallbackCss) el.style.background = fallbackCss;
    return;
  }
  const bordersN = Number(borders) || 0;
  const scaleN = sliceScale !== undefined && sliceScale !== '' ? Number(sliceScale) : 1;
  const oldCanvas = el.querySelector('canvas.gui-ns-canvas');
  if (oldCanvas) oldCanvas.remove();
  el.style.backgroundImage = '';
  if (bordersN <= 0) {
    el.style.backgroundImage = `url("${img.src}")`;
    el.style.backgroundSize = '100% 100%';
    el.style.imageRendering = 'pixelated';
    return;
  }
  const canvas = document.createElement('canvas');
  canvas.className = 'gui-ns-canvas';
  canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;image-rendering:pixelated;pointer-events:none;';
  const cw = Math.max(1, gameToDisp(gameW, displayScale));
  const ch = Math.max(1, gameToDisp(gameH, displayScale));
  canvas.width = cw;
  canvas.height = ch;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    ctx.imageSmoothingEnabled = false;
    drawNineSlice(ctx, img, 0, 0, cw, ch, bordersN, scaleN);
  }
  el.insertBefore(canvas, el.firstChild);
}

function widgetTexturePath(w) {
  if (w.type === 'image') return w.args?.[1] ?? '';
  if (w.type === 'button') return w.props?.texture ?? '';
  return '';
}

function appendNineSliceEditor(container, w, def) {
  if (!NINESLICE_WIDGET_TYPES.has(w.type)) return;
  const texPath = widgetTexturePath(w);
  const bordersDef = def.props?.slice_borders?.def ?? 0;
  const scaleDef = def.props?.slice_scale?.def ?? 1;
  const borders = Number(w.props?.slice_borders) || 0;
  const sliceScale = w.props?.slice_scale !== undefined && w.props?.slice_scale !== ''
    ? Number(w.props.slice_scale) : scaleDef;

  const section = document.createElement('div');
  section.className = 'gui-ed-nineslice';
  section.innerHTML = `
    <div class="gui-ed-nineslice-head">9-slice</div>
    <div class="gui-ed-nineslice-preview-wrap">
      <div class="gui-ed-nineslice-preview" data-role="preview">
        <img class="gui-ed-nineslice-img" alt="" hidden />
        <div class="gui-ed-nineslice-lines" data-role="lines" hidden></div>
        <div class="gui-ed-nineslice-empty" data-role="empty">Нет текстуры</div>
      </div>
    </div>
    <label class="gui-ed-prop"${hintAttr('slice_borders')}><span>slice_borders</span>
      <input type="number" min="0" step="1" data-ns="borders" value="${borders}" /></label>
    <label class="gui-ed-prop"${hintAttr('slice_scale')}><span>slice_scale</span>
      <input type="number" min="0.01" step="0.01" data-ns="scale" value="${sliceScale}" /></label>
    <p class="gui-ed-nineslice-hint">Линии — границы среза в px текстуры. На виджете толщина края ≈ <code>round(borders × slice_scale)</code> игровых px.</p>
  `;
  container.appendChild(section);

  const imgEl = section.querySelector('.gui-ed-nineslice-img');
  const linesEl = section.querySelector('[data-role="lines"]');
  const emptyEl = section.querySelector('[data-role="empty"]');
  const previewEl = section.querySelector('[data-role="preview"]');
  const bordersInp = section.querySelector('[data-ns="borders"]');
  const scaleInp = section.querySelector('[data-ns="scale"]');
  let previewScale = 1;
  let texW = 0;
  let texH = 0;

  const syncPropsFromInputs = () => {
    const b = parseInt(bordersInp.value, 10) || 0;
    const sc = parseFloat(scaleInp.value);
    if (b) w.props.slice_borders = b;
    else delete w.props.slice_borders;
    if (!Number.isNaN(sc) && sc !== scaleDef) w.props.slice_scale = sc;
    else delete w.props.slice_scale;
  };

  const paintLines = () => {
    const b = Math.max(0, parseInt(bordersInp.value, 10) || 0);
    if (!texW || !b) {
      linesEl.hidden = true;
      linesEl.innerHTML = '';
      return;
    }
    const maxB = Math.floor(Math.min(texW, texH) / 2);
    const clamped = Math.min(b, maxB);
    const off = clamped * previewScale;
    linesEl.hidden = false;
    linesEl.innerHTML = `
      <div class="gui-ed-ns-line gui-ed-ns-h" data-edge="top" style="top:${off}px"></div>
      <div class="gui-ed-ns-line gui-ed-ns-h" data-edge="bottom" style="bottom:${off}px"></div>
      <div class="gui-ed-ns-line gui-ed-ns-v" data-edge="left" style="left:${off}px"></div>
      <div class="gui-ed-ns-line gui-ed-ns-v" data-edge="right" style="right:${off}px"></div>
    `;
    linesEl.querySelectorAll('.gui-ed-ns-line').forEach((line) => {
      line.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const edge = line.dataset.edge;
        const rect = previewEl.getBoundingClientRect();
        const onMove = (ev) => {
          let nb;
          if (edge === 'top') nb = Math.round((ev.clientY - rect.top) / previewScale);
          else if (edge === 'bottom') nb = Math.round((rect.bottom - ev.clientY) / previewScale);
          else if (edge === 'left') nb = Math.round((ev.clientX - rect.left) / previewScale);
          else nb = Math.round((rect.right - ev.clientX) / previewScale);
          nb = Math.max(0, Math.min(Math.floor(Math.min(texW, texH) / 2), nb));
          bordersInp.value = String(nb);
          paintLines();
          syncPropsFromInputs();
          renderCanvas();
          renderCodePreview();
        };
        const onUp = () => {
          window.removeEventListener('pointermove', onMove);
          window.removeEventListener('pointerup', onUp);
        };
        window.addEventListener('pointermove', onMove);
        window.addEventListener('pointerup', onUp);
      });
    });
  };

  const onFieldChange = () => {
    syncPropsFromInputs();
    paintLines();
    renderCanvas();
    renderCodePreview();
  };
  bordersInp.addEventListener('change', onFieldChange);
  scaleInp.addEventListener('change', onFieldChange);
  bordersInp.addEventListener('input', paintLines);

  loadTextureImage(texPath).then((img) => {
    if (!img) {
      emptyEl.hidden = false;
      emptyEl.textContent = texPath ? 'Текстура не найдена' : 'Нет текстуры';
      return;
    }
    emptyEl.hidden = true;
    imgEl.hidden = false;
    texW = img.naturalWidth;
    texH = img.naturalHeight;
    const ps = texturePreviewScale(texW, texH, 180);
    previewScale = ps.scale;
    imgEl.src = img.src;
    imgEl.style.width = ps.w + 'px';
    imgEl.style.height = ps.h + 'px';
    previewEl.style.width = ps.w + 'px';
    previewEl.style.height = ps.h + 'px';
    paintLines();
  });
}

function renderCanvas() {
  const host = $('gui-ed-canvas');
  if (!host || !st.model) return;
  host.innerHTML = '';

  const scr = gameScreenSize();
  const availW = host.clientWidth - 24;
  const availH = host.clientHeight - 24;
  const scale = Math.max(0.2, Math.min(availW / scr.w, availH / scr.h)) * st.zoom;

  const screenEl = document.createElement('div');
  screenEl.className = 'gui-ed-screen';
  screenEl.style.width = (scr.w * scale) + 'px';
  screenEl.style.height = (scr.h * scale) + 'px';

  // затемнение фона как в игре (dimBackground; у наложения в игре фон не затемняется)
  if (st.model.root?.dimBackground === true && st.model.openMode !== 'overlay') {
    screenEl.style.background = 'linear-gradient(rgba(16,16,16,.75), rgba(16,16,16,.75)), url("data:image/svg+xml,' +
      encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23508050"/><rect width="16" height="16" fill="%23477347"/><rect x="16" y="16" width="16" height="16" fill="%23477347"/></svg>') + '")';
  } else {
    screenEl.style.background = 'url("data:image/svg+xml,' +
      encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="%23508050"/><rect width="16" height="16" fill="%23477347"/><rect x="16" y="16" width="16" height="16" fill="%23477347"/></svg>') + '")';
  }

  const panel = panelSizePx();
  const ppos = panelPosPx(panel);
  const panelEl = document.createElement('div');
  panelEl.className = 'gui-ed-panel';
  panelEl.style.left = gameToDisp(ppos.x, scale) + 'px';
  panelEl.style.top = gameToDisp(ppos.y, scale) + 'px';
  panelEl.style.width = gameToDisp(panel.w, scale) + 'px';
  panelEl.style.height = gameToDisp(panel.h, scale) + 'px';
  panelEl.style.background = engineColorToCss(st.model.root?.background || '#C0101010');

  if (st.gridShow && st.gridStep > 1) {
    panelEl.appendChild(buildSnapGridEl(panel.w, panel.h, scale));
  }

  for (const w of sortByLayer(st.model.widgets || [])) {
    panelEl.appendChild(buildWidgetEl(w, panel.w, panel.h, scale));
  }

  if (st.drag?.guides) appendDragGuides(panelEl, st.drag.guides, scale);

  panelEl.addEventListener('mousedown', (e) => {
    if (e.target === panelEl) {
      clearSelection();
      renderAll();
    }
  });

  screenEl.appendChild(panelEl);
  host.appendChild(screenEl);

  const info = document.createElement('div');
  info.className = 'gui-ed-canvas-info';
  info.textContent = `экран ${scr.w}×${scr.h} · панель ${panel.w}×${panel.h} · масштаб ${(scale).toFixed(2)}`;
  host.appendChild(info);

  host._scale = scale;
}

// ================= Drag / Resize =================

function onWidgetMouseDown(e, w, pw, ph, scale) {
  e.stopPropagation();
  e.preventDefault();
  const additive = e.shiftKey || e.ctrlKey || e.metaKey;
  if (additive) {
    setSelection(w.uid, { additive: true });
  } else if (!isSelected(w.uid)) {
    setSelection(w.uid);
  } else {
    st.selectedUid = w.uid;
  }
  startDrag(e, w, pw, ph, scale, 'move');
  renderAll();
}

function startDrag(e, w, pw, ph, scale, mode, handle = 'se') {
  const def = WIDGET_DEFS[w.type];
  const moveUids = (() => {
    const sel = getSelectionUids();
    return (mode === 'move' && sel.length > 1 && sel.includes(w.uid)) ? sel : [w.uid];
  })();
  const starts = moveUids.map((uid) => {
    const ww = findGuiWidget(st.model, uid);
    const dims = widgetParentDims(ww);
    return {
      uid,
      pw: dims.w,
      ph: dims.h,
      startX: resolveCoord(ww.pos?.[0] ?? 0, dims.w),
      startY: resolveCoord(ww.pos?.[1] ?? 0, dims.h),
      pctX: typeof ww.pos?.[0] === 'string' && String(ww.pos[0]).endsWith('%'),
      pctY: typeof ww.pos?.[1] === 'string' && String(ww.pos[1]).endsWith('%'),
    };
  });
  st.drag = {
    mode, handle, uid: w.uid, pw, ph, scale, moveUids, starts,
    startMx: e.clientX, startMy: e.clientY,
    startX: resolveCoord(w.pos?.[0] ?? 0, pw),
    startY: resolveCoord(w.pos?.[1] ?? 0, ph),
    startW: w.size ? resolveCoord(w.size[0], pw) : 0,
    startH: w.size ? resolveCoord(w.size[def.defaults.sizeSingle ? 0 : 1], ph) : 0,
    pctX: typeof w.pos?.[0] === 'string' && String(w.pos[0]).endsWith('%'),
    pctY: typeof w.pos?.[1] === 'string' && String(w.pos[1]).endsWith('%'),
    pctW: w.size && typeof w.size[0] === 'string' && String(w.size[0]).endsWith('%'),
    pctH: w.size && typeof w.size[1] === 'string' && String(w.size[1]).endsWith('%'),
  };
  window.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', onDragEnd, { once: true });
}

function onDragMove(e) {
  const d = st.drag;
  if (!d) return;
  const w = findGuiWidget(st.model, d.uid);
  if (!w) return;
  const def = WIDGET_DEFS[w.type];
  const dx = (e.clientX - d.startMx) / d.scale;
  const dy = (e.clientY - d.startMy) / d.scale;

  if (d.mode === 'move') {
    const primary = d.starts?.find((s) => s.uid === d.uid) || d.starts?.[0];
    let nx = d.startX + dx;
    let ny = d.startY + dy;
    let snappedX = false;
    let snappedY = false;
    d.guides = null;

    if (st.widgetSnap && primary) {
      const w = findGuiWidget(st.model, primary.uid);
      const sz = widgetSizePx(w, primary.pw, primary.ph);
      const snapped = snapToWidgets(nx, ny, sz.w, sz.h, siblingsOf(st.model, primary.uid), primary.pw, primary.ph);
      nx = snapped.x;
      ny = snapped.y;
      snappedX = snapped.snappedX;
      snappedY = snapped.snappedY;
      d.guides = snapped.guides;
    }

    if (!snappedX && st.gridSnap) nx = snap(nx);
    if (!snappedY && st.gridSnap) ny = snap(ny);

    const dxFinal = nx - d.startX;
    const dyFinal = ny - d.startY;

    for (const s of d.starts || [{ uid: d.uid, pw: d.pw, ph: d.ph, startX: d.startX, startY: d.startY, pctX: d.pctX, pctY: d.pctY }]) {
      const ww = findGuiWidget(st.model, s.uid);
      if (!ww) continue;
      ww.pos = [
        coordInUnit(s.startX + dxFinal, s.pw, s.pctX),
        coordInUnit(s.startY + dyFinal, s.ph, s.pctY),
      ];
    }
  } else {
    let nw = d.startW, nh = d.startH;
    if (d.handle === 'se' || d.handle === 'e') nw = Math.max(1, snap(d.startW + dx));
    if (d.handle === 'se' || d.handle === 's') nh = Math.max(1, snap(d.startH + dy));
    if (def.defaults.sizeSingle) {
      w.size = [coordInUnit(nw, d.pw, d.pctW)];
    } else {
      w.size = [
        coordInUnit(nw, d.pw, d.pctW),
        coordInUnit(nh, d.ph, d.pctH),
      ];
    }
  }
  renderCanvas();
  renderPropsValuesOnly();
}

function onDragEnd() {
  window.removeEventListener('mousemove', onDragMove);
  if (st.drag) {
    st.drag = null;
    renderAll();
  }
}

/** Клон виджета с новыми uid и уникальными id. */
function cloneWidgetTree(widget, { offsetX = 0, offsetY = 0, parentW, parentH } = {}) {
  const clone = normalizeGuiModel({ widgets: [JSON.parse(JSON.stringify(widget))] }).widgets[0];
  walkGuiWidgets([clone], (w) => {
    w.uid = guiUid();
    const idIdx = w.type === 'entity' ? 1 : 0;
    if (w.args?.[idIdx]) {
      const base = String(w.args[idIdx]).replace(/_[a-z0-9]+$/i, '');
      w.args[idIdx] = base + '_' + w.uid.slice(-4);
    }
  });
  if (offsetX || offsetY) {
    const pw = parentW ?? panelSizePx().w;
    const ph = parentH ?? panelSizePx().h;
    const ox = resolveCoord(clone.pos?.[0] ?? 0, pw);
    const oy = resolveCoord(clone.pos?.[1] ?? 0, ph);
    clone.pos = [
      coordInUnit(ox + offsetX, pw, isPct(clone.pos?.[0])),
      coordInUnit(oy + offsetY, ph, isPct(clone.pos?.[1])),
    ];
  }
  return clone;
}

function copySelectedWidget() {
  const w = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
  if (!w) return false;
  st.clipboard = JSON.parse(JSON.stringify(w));
  st.pasteCount = 0;
  return true;
}

function pasteClipboardWidget() {
  if (!st.clipboard) return false;

  const step = st.gridStep > 1 ? st.gridStep : 10;
  st.pasteCount += 1;
  const offset = step * st.pasteCount;

  const target = getPasteTarget();
  const clone = cloneWidgetTree(st.clipboard, {
    offsetX: offset,
    offsetY: offset,
    parentW: target.pw,
    parentH: target.ph,
  });

  if (target.insertAfter != null) {
    target.list.splice(target.insertAfter + 1, 0, clone);
  } else {
    target.list.push(clone);
  }
  st.selectedUid = clone.uid;
  renderAll();
  return true;
}

/** Куда вставить: внутрь выбранного контейнера или рядом с выбранным виджетом. */
function getPasteTarget() {
  const sel = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
  const selDef = sel ? WIDGET_DEFS[sel.type] : null;

  if (sel?.children && selDef?.container) {
    const dims = widgetParentDims(sel);
    const psz = widgetSizePx(sel, dims.w, dims.h);
    let ph = psz.h;
    if (sel.type === 'scroll' && sel.props?.contentH) {
      ph = Math.round(resolveCoord(sel.props.contentH, dims.h)) || psz.h;
    }
    return { list: sel.children, pw: psz.w, ph };
  }

  if (st.selectedUid) {
    const found = findGuiWidgetParentList(st.model, st.selectedUid);
    if (found) {
      const w = found.list[found.idx];
      const dims = widgetParentDims(w);
      return { list: found.list, pw: dims.w, ph: dims.h, insertAfter: found.idx };
    }
  }

  const panel = panelSizePx();
  return { list: st.model.widgets, pw: panel.w, ph: panel.h };
}

// ================= Палитра =================

function renderPalette() {
  const el = $('gui-ed-palette');
  if (!el) return;
  el.innerHTML = '';
  for (const [type, def] of Object.entries(WIDGET_DEFS)) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'gui-ed-pal-btn';
    btn.innerHTML = `<span class="material-symbols-outlined">${def.icon}</span><span>${def.label}</span>`;
    btn.title = (WIDGET_HINTS[type] || '') + `\n\nКод: ${type}(${def.args.map(a => a.name).join(', ')})`;
    btn.addEventListener('click', () => {
      const w = createWidget(type);
      if (!w) return;
      // если выбран контейнер — кладём внутрь
      const sel = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
      let parentW, parentH;
      if (sel?.children) {
        const dims = widgetParentDims(sel);
        const psz = widgetSizePx(sel, dims.w, dims.h);
        parentW = psz.w;
        parentH = sel.type === 'scroll' && sel.props?.contentH
          ? (Math.round(resolveCoord(sel.props.contentH, dims.h)) || psz.h)
          : psz.h;
        sel.children.push(w);
      } else {
        const panel = panelSizePx();
        parentW = panel.w; parentH = panel.h;
        st.model.widgets.push(w);
      }
      // Новые виджеты — в текущих единицах (по умолчанию %)
      if (st.unitMode === '%') {
        w.pos = [pctStr(resolveCoord(w.pos[0], parentW), parentW), pctStr(resolveCoord(w.pos[1], parentH), parentH)];
        if (w.size && !WIDGET_DEFS[type].defaults.sizeSingle && !WIDGET_DEFS[type].defaults.noSize) {
          w.size = [pctStr(resolveCoord(w.size[0], parentW), parentW), pctStr(resolveCoord(w.size[1], parentH), parentH)];
        }
      }
      st.selectedUid = w.uid;
      st.selectedUids = new Set([w.uid]);
      renderAll();
    });
    el.appendChild(btn);
  }
}

// ================= Дерево (слои, вложенность) =================

function renderTree() {
  const el = $('gui-ed-tree');
  if (!el) return;
  el.innerHTML = '';
  const rootUl = document.createElement('ul');
  rootUl.className = 'gui-ed-tree-list';
  buildTreeLevel(st.model.widgets, rootUl, null);
  el.appendChild(rootUl);
}

function buildTreeLevel(widgets, ul, parentW) {
  widgets.forEach((w, idx) => {
    const def = WIDGET_DEFS[w.type];
    const li = document.createElement('li');
    li.draggable = true;
    li.dataset.uid = w.uid;
    const row = document.createElement('div');
    row.className = 'gui-ed-tree-row' + (isSelected(w.uid) ? ' active' : '');
    row.innerHTML =
      `<span class="material-symbols-outlined" style="font-size:14px">${def.icon}</span>` +
      `<span class="gui-ed-tree-name">${escapeHtml(idOf(w))}</span>` +
      `<span class="gui-ed-tree-layer" title="слой">${Number(w.layer) || 0}</span>`;

    const mkBtn = (icon, title, fn) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'gui-ed-tree-btn';
      b.title = title;
      b.innerHTML = `<span class="material-symbols-outlined" style="font-size:13px">${icon}</span>`;
      b.addEventListener('click', (e) => { e.stopPropagation(); fn(); });
      return b;
    };
    row.appendChild(mkBtn('arrow_upward', 'Выше (раньше рисуется)', () => moveInList(widgets, idx, -1)));
    row.appendChild(mkBtn('arrow_downward', 'Ниже (позже рисуется)', () => moveInList(widgets, idx, +1)));
    row.appendChild(mkBtn('content_copy', 'Дублировать', () => duplicateWidget(widgets, idx)));
    row.appendChild(mkBtn('delete', 'Удалить', () => {
      widgets.splice(idx, 1);
      st.selectedUids.delete(w.uid);
      if (st.selectedUid === w.uid) {
        const rest = [...st.selectedUids];
        st.selectedUid = rest.length ? rest[0] : null;
      }
      renderAll();
    }));

    row.addEventListener('click', (e) => {
      setSelection(w.uid, { additive: e.shiftKey || e.ctrlKey || e.metaKey });
      renderAll();
    });

    li.addEventListener('dragstart', (e) => {
      e.stopPropagation();
      e.dataTransfer.setData('text/gui-uid', w.uid);
    });
    li.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.stopPropagation();
      row.classList.add('drop-target');
    });
    li.addEventListener('dragleave', () => row.classList.remove('drop-target'));
    li.addEventListener('drop', (e) => {
      e.preventDefault();
      e.stopPropagation();
      row.classList.remove('drop-target');
      const uid = e.dataTransfer.getData('text/gui-uid');
      if (uid && uid !== w.uid) dropWidget(uid, w);
    });

    li.appendChild(row);
    if (w.children) {
      const sub = document.createElement('ul');
      sub.className = 'gui-ed-tree-list gui-ed-tree-sub';
      buildTreeLevel(w.children, sub, w);
      li.appendChild(sub);
    }
    ul.appendChild(li);
  });
}

function idOf(w) {
  const idIdx = w.type === 'entity' ? 1 : 0;
  return w.args?.[idIdx] || w.type;
}

function moveInList(list, idx, delta) {
  const ni = idx + delta;
  if (ni < 0 || ni >= list.length) return;
  const [item] = list.splice(idx, 1);
  list.splice(ni, 0, item);
  renderAll();
}

function duplicateWidget(list, idx) {
  const w = list[idx];
  const dims = widgetParentDims(w);
  const clone = cloneWidgetTree(w, { offsetX: st.gridStep, offsetY: st.gridStep, parentW: dims.w, parentH: dims.h });
  list.splice(idx + 1, 0, clone);
  st.selectedUid = clone.uid;
  renderAll();
}

/** Перенос виджета: на контейнер — внутрь, иначе — рядом (после цели). */
function dropWidget(srcUid, target) {
  const src = findGuiWidgetParentList(st.model, srcUid);
  if (!src) return;
  // нельзя перенести в собственного потомка
  let bad = false;
  walkGuiWidgets([src.list[src.idx]], (w) => { if (w.uid === target.uid) bad = true; });
  if (bad) return;
  const [item] = src.list.splice(src.idx, 1);
  if (target.children) {
    target.children.push(item);
  } else {
    const t = findGuiWidgetParentList(st.model, target.uid);
    if (t) t.list.splice(t.idx + 1, 0, item);
    else st.model.widgets.push(item);
  }
  renderAll();
}

// ================= Свойства =================

function renderRootProps() {
  const el = $('gui-ed-root-props');
  if (!el) return;
  const r = st.model.root;
  const size = Array.isArray(r.size) ? r.size : ['60%', '70%'];
  el.innerHTML = `
    <label class="gui-ed-prop" title="Ширина окна: число — игровые пиксели, &quot;60%&quot; — процент от экрана игрока."><span>Ширина (px или %)</span><input data-root="size0" value="${escapeAttr(size[0])}" /></label>
    <label class="gui-ed-prop" title="Высота окна: число — игровые пиксели, &quot;70%&quot; — процент от экрана игрока."><span>Высота (px или %)</span><input data-root="size1" value="${escapeAttr(size[1])}" /></label>
    <label class="gui-ed-prop" title="${escapeAttr(PROP_HINTS.color)}"><span>Фон #AARRGGBB</span>${colorInputHtml('root-bg', r.background ?? '#C0101010', 'data-root="background"')}</label>
    <label class="gui-ed-prop gui-ed-prop-row" title="Разрешить игроку закрывать окно клавишей Esc."><input type="checkbox" data-root="canClose" ${r.canClose !== false ? 'checked' : ''} /><span>можно закрыть (Esc)</span></label>
    <label class="gui-ed-prop gui-ed-prop-row" title="Затемнять игровой мир позади окна (только uiOpen; у наложения и контейнеров со слотами — отдельно)."><input type="checkbox" data-root="dimBackground" ${r.dimBackground === true ? 'checked' : ''} /><span>затемнять фон игры</span></label>
  `;
  bindColorInputs(el);
  el.querySelectorAll('[data-root]').forEach(inp => {
    inp.addEventListener('change', () => {
      const key = inp.dataset.root;
      if (key === 'size0' || key === 'size1') {
        const s = Array.isArray(st.model.root.size) ? [...st.model.root.size] : ['60%', '70%'];
        s[key === 'size0' ? 0 : 1] = parseCoordInput(inp.value);
        st.model.root.size = s;
      } else if (key === 'canClose' || key === 'dimBackground') {
        st.model.root[key] = inp.checked;
      } else {
        st.model.root[key] = inp.value;
      }
      renderAll();
    });
  });
}

// ===== Поле цвета: свотч + текст #AARRGGBB + системный выбор + пипетка =====

function splitEngineColor(v) {
  const s = String(v || '').replace(/^#/, '');
  if (/^[0-9a-fA-F]{8}$/.test(s)) return { a: s.slice(0, 2).toUpperCase(), rgb: '#' + s.slice(2) };
  if (/^[0-9a-fA-F]{6}$/.test(s)) return { a: 'FF', rgb: '#' + s };
  return { a: 'FF', rgb: '#ffffff' };
}

function colorInputHtml(uid, value, dataAttr) {
  const a = parseInt(splitEngineColor(value).a, 16);
  return `<div class="gui-ed-color">
    <button type="button" class="gui-ed-color-swatch" title="Открыть палитру цветов"></button>
    <input class="gui-ed-color-text" ${dataAttr} value="${escapeAttr(value ?? '')}" spellcheck="false" />
    <input type="range" class="gui-ed-color-alpha" min="0" max="255" value="${a}" title="Прозрачность (альфа)" />
    <span class="gui-ed-color-alpha-val">${Math.round((a / 255) * 100)}%</span>
    <button type="button" class="gui-ed-color-pick" title="Пипетка — взять цвет с любой точки экрана"><span class="material-symbols-outlined" style="font-size:14px">colorize</span></button>
    <input type="color" class="gui-ed-color-native" tabindex="-1" />
  </div>`;
}

function bindColorInputs(container) {
  container.querySelectorAll('.gui-ed-color').forEach(box => {
    const swatch = box.querySelector('.gui-ed-color-swatch');
    const text = box.querySelector('.gui-ed-color-text');
    const native = box.querySelector('.gui-ed-color-native');
    const pick = box.querySelector('.gui-ed-color-pick');
    const alpha = box.querySelector('.gui-ed-color-alpha');
    const alphaVal = box.querySelector('.gui-ed-color-alpha-val');

    const refreshAlphaUi = () => {
      if (!alpha) return;
      const a = parseInt(splitEngineColor(text.value).a, 16);
      if (!Number.isNaN(a)) {
        alpha.value = String(a);
        if (alphaVal) alphaVal.textContent = Math.round((a / 255) * 100) + '%';
      }
    };

    const refresh = () => {
      swatch.style.background = engineColorToCss(text.value || '#FFFFFFFF', 'rgba(255,255,255,1)');
      refreshAlphaUi();
    };
    refresh();

    const setValue = (v) => {
      text.value = v;
      refresh();
      text.dispatchEvent(new Event('change', { bubbles: true }));
    };

    swatch.addEventListener('click', () => {
      native.value = splitEngineColor(text.value).rgb;
      native.click();
    });
    native.addEventListener('input', () => {
      const { a } = splitEngineColor(text.value);
      setValue('#' + a + native.value.slice(1).toUpperCase());
    });
    alpha?.addEventListener('input', () => {
      const aNum = parseInt(alpha.value, 10);
      const aHex = Number.isNaN(aNum) ? 'FF' : aNum.toString(16).toUpperCase().padStart(2, '0');
      const { rgb } = splitEngineColor(text.value);
      setValue('#' + aHex + rgb.slice(1).toUpperCase());
    });
    pick.addEventListener('click', async () => {
      if (!window.EyeDropper) return;
      try {
        const res = await new window.EyeDropper().open();
        const { a } = splitEngineColor(text.value);
        setValue('#' + a + res.sRGBHex.slice(1).toUpperCase());
      } catch (e) { /* отменено */ }
    });
    text.addEventListener('input', refresh);
  });
}

function parseCoordInput(v) {
  const s = String(v).trim();
  if (/^-?\d+$/.test(s)) return parseInt(s, 10);
  return s;
}

function renderProps() {
  const el = $('gui-ed-props');
  if (!el) return;
  const uids = getSelectionUids();
  if (uids.length > 1) {
    el.innerHTML = `<p class="gui-ed-hint">Выбрано элементов: <strong>${uids.length}</strong>.</p>
      <p class="gui-ed-hint mt-2">Ctrl+G — сгруппировать. Перетаскивание двигает все выделенные.</p>`;
    return;
  }
  const w = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
  if (!w) {
    el.innerHTML = '<p class="gui-ed-hint">Выберите виджет на холсте или в дереве.</p>';
    return;
  }
  const def = WIDGET_DEFS[w.type];
  let html = `<p class="gui-ed-props-title"><span class="material-symbols-outlined" style="font-size:15px">${def.icon}</span> ${def.label} <code>${w.type}</code></p>`;

  def.args.forEach((a, i) => {
    if (a.name === 'texture' || (w.type === 'image' && i === 1)) {
      html += `<label class="gui-ed-prop"${hintAttr('texture')}><span>${a.name}</span>${textureSelect(`arg${i}`, w.args[i])}</label>`;
    } else {
      html += `<label class="gui-ed-prop"${hintAttr(a.name)}><span>${a.name}</span><input data-p="arg${i}" value="${escapeAttr(w.args[i] ?? '')}" /></label>`;
    }
  });

  const coordField = (label, key, value, hintKey) => {
    const pct = isPct(value);
    return `<label class="gui-ed-prop"${hintAttr(hintKey)}><span>${label}</span>
      <div class="gui-ed-coord">
        <input data-p="${key}" value="${escapeAttr(value ?? 0)}" />
        <button type="button" class="gui-ed-unit-btn" data-unit-for="${key}" title="Переключить единицы: пиксели ↔ проценты от родителя">${pct ? '%' : 'px'}</button>
      </div></label>`;
  };

  html += `<div class="gui-ed-prop-grid">
    ${coordField('x', 'pos0', w.pos?.[0] ?? 0, 'x')}
    ${coordField('y', 'pos1', w.pos?.[1] ?? 0, 'y')}`;
  if (w.size && !def.defaults.noSize) {
    if (def.defaults.sizeSingle) {
      html += coordField('size', 'size0', w.size[0], 'size');
    } else {
      html += coordField('w', 'size0', w.size[0], 'w') + coordField('h', 'size1', w.size[1], 'h');
    }
  }
  html += `<label class="gui-ed-prop"${hintAttr('layer')}><span>слой</span><input data-p="layer" type="number" value="${Number(w.layer) || 0}" /></label></div>`;

  for (const [k, meta] of Object.entries(def.props || {})) {
    if (NINESLICE_WIDGET_TYPES.has(w.type) && SLICE_PROP_KEYS.has(k)) continue;
    const val = w.props?.[k] ?? '';
    if (meta.kind === 'bool') {
      const on = val === '' ? meta.def : (val === true || val === 'true');
      html += `<label class="gui-ed-prop gui-ed-prop-row"${hintAttr(k)}><input type="checkbox" data-p="prop:${k}" ${on ? 'checked' : ''} /><span>${k}</span></label>`;
    } else if (meta.kind === 'enum') {
      html += `<label class="gui-ed-prop"${hintAttr(k)}><span>${k}</span><select data-p="prop:${k}">` +
        meta.options.map(o => `<option value="${o}" ${String(val || meta.def) === o ? 'selected' : ''}>${o}</option>`).join('') +
        `</select></label>`;
    } else if (meta.kind === 'texture') {
      html += `<label class="gui-ed-prop"${hintAttr(k)}><span>${k}</span>${textureSelect(`prop:${k}`, val)}</label>`;
    } else if (meta.kind === 'color') {
      html += `<label class="gui-ed-prop"${hintAttr(k)}><span>${k} (#AARRGGBB)</span>${colorInputHtml(`prop-${k}`, val || '', `data-p="prop:${k}" placeholder="${escapeAttr(meta.def)}"`)}</label>`;
    } else {
      html += `<label class="gui-ed-prop"${hintAttr(k)}><span>${k}</span><input data-p="prop:${k}" value="${escapeAttr(val)}" placeholder="${escapeAttr(meta.def)}" /></label>`;
    }
  }

  for (const evName of def.events || []) {
    html += `<label class="gui-ed-prop"${hintAttr(evName)}><span>${evName} (код движка)</span><textarea data-p="ev:${evName}" rows="3" placeholder='chat(_eventPlayer, "клик!")'>${escapeHtml(w.events?.[evName] || '')}</textarea></label>`;
  }

  el.innerHTML = html;
  appendNineSliceEditor(el, w, def);
  bindColorInputs(el);
  el.querySelectorAll('[data-p]').forEach(inp => {
    inp.addEventListener('change', () => applyPropInput(w, inp));
    if (inp.tagName === 'INPUT' && inp.dataset.p?.startsWith('arg')) {
      inp.addEventListener('input', () => applyPropInput(w, inp, { canvasOnly: true }));
    }
  });
  // Переключатели px <-> % (конвертируют текущее значение относительно родителя)
  el.querySelectorAll('[data-unit-for]').forEach(btn => {
    btn.addEventListener('click', () => {
      const key = btn.dataset.unitFor;
      const dims = widgetParentDims(w);
      const isX = key === 'pos0' || key === 'size0';
      const parent = isX ? dims.w : dims.h;
      const list = key.startsWith('pos') ? (w.pos = [...(w.pos || [0, 0])]) : (w.size = [...(w.size || [0, 0])]);
      const idx = key.endsWith('0') ? 0 : 1;
      const cur = list[idx];
      const px = resolveCoord(cur, parent);
      list[idx] = isPct(cur) ? Math.round(px) : pctStr(px, parent);
      renderAll();
    });
  });
}

/** Размеры родителя виджета в игровых px (панель или контейнер). */
function widgetParentDims(target) {
  const panel = panelSizePx();
  let result = { w: panel.w, h: panel.h };
  const walk = (widgets, pw, ph) => {
    for (const w of widgets) {
      if (w.uid === target.uid) {
        result = { w: pw, h: ph };
        return true;
      }
      if (w.children) {
        const sz = widgetSizePx(w, pw, ph);
        let ch = sz.h;
        if (w.type === 'scroll' && w.props?.contentH) {
          ch = Math.round(resolveCoord(w.props.contentH, ph)) || sz.h;
        }
        if (walk(w.children, sz.w, ch)) return true;
      }
    }
    return false;
  };
  walk(st.model.widgets || [], panel.w, panel.h);
  return result;
}

function textureSelect(dataKey, current) {
  const opts = ['<option value="">—</option>']
    .concat(st.textures.map(t => `<option value="${escapeAttr(t)}" ${t === current ? 'selected' : ''}>${escapeHtml(t)}</option>`));
  if (current && !st.textures.includes(current)) {
    opts.push(`<option value="${escapeAttr(current)}" selected>${escapeHtml(current)}</option>`);
  }
  return `<select data-p="${dataKey}">${opts.join('')}</select>`;
}

function applyPropInput(w, inp, opts = {}) {
  const key = inp.dataset.p;
  const def = WIDGET_DEFS[w.type];
  if (key.startsWith('arg')) {
    w.args[parseInt(key.slice(3), 10)] = inp.value;
  } else if (key === 'pos0' || key === 'pos1') {
    const pos = [...(w.pos || [0, 0])];
    pos[key === 'pos0' ? 0 : 1] = parseCoordInput(inp.value);
    w.pos = pos;
  } else if (key === 'size0' || key === 'size1') {
    const size = [...(w.size || [def.defaults.w, def.defaults.h])];
    size[key === 'size0' ? 0 : 1] = parseCoordInput(inp.value);
    w.size = size;
  } else if (key === 'layer') {
    w.layer = parseInt(inp.value, 10) || 0;
  } else if (key.startsWith('prop:')) {
    const k = key.slice(5);
    const meta = def.props[k];
    if (meta?.kind === 'bool') w.props[k] = inp.checked;
    else if (inp.value === '') delete w.props[k];
    else w.props[k] = meta?.kind === 'int' ? (parseInt(inp.value, 10) || 0)
      : meta?.kind === 'float' ? (parseFloat(inp.value) || 0)
      : inp.value;
  } else if (key.startsWith('ev:')) {
    w.events[key.slice(3)] = inp.value;
  }
  if (opts.canvasOnly) {
    renderCanvas();
    renderCodePreview();
    return;
  }
  renderAll();
}

/** Обновляет только числовые поля x/y/w/h при drag, без пересборки панели свойств. */
function renderPropsValuesOnly() {
  const el = $('gui-ed-props');
  const w = st.selectedUid ? findGuiWidget(st.model, st.selectedUid) : null;
  if (!el || !w) return;
  const set = (sel, val) => {
    const inp = el.querySelector(`[data-p="${sel}"]`);
    if (inp && document.activeElement !== inp) inp.value = val;
  };
  set('pos0', w.pos?.[0] ?? 0);
  set('pos1', w.pos?.[1] ?? 0);
  if (w.size) {
    set('size0', w.size[0]);
    if (w.size.length > 1) set('size1', w.size[1]);
  }
}

// ================= Превью кода =================

function renderCodePreview() {
  const el = $('gui-ed-code');
  if (!el) return;
  syncHeaderIntoModel();
  el.textContent = guiModelToSprCode(st.model);
}

// ================= Утилиты =================

function escapeHtml(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, '&quot;');
}
function hintAttr(key) {
  return PROP_HINTS[key] ? ` title="${escapeAttr(PROP_HINTS[key])}"` : '';
}

// ================= Инициализация =================

export function initGuiEditor() {
  $('gui-ed-close')?.addEventListener('click', () => closeGuiEditor(true));
  $('gui-ed-cancel')?.addEventListener('click', () => closeGuiEditor(false));
  $('gui-ed-name')?.addEventListener('change', () => { syncHeaderIntoModel(); renderCodePreview(); });
  $('gui-ed-mode-gui')?.addEventListener('change', () => { syncHeaderIntoModel(); renderAll(); });
  $('gui-ed-mode-overlay')?.addEventListener('change', () => { syncHeaderIntoModel(); renderAll(); });
  $('gui-ed-scale')?.addEventListener('change', (e) => {
    st.guiScale = Math.max(1, Math.min(4, parseInt(e.target.value, 10) || 2));
    renderAll();
  });
  $('gui-ed-zoom-in')?.addEventListener('click', () => { st.zoom = Math.min(3, st.zoom * 1.25); renderCanvas(); });
  $('gui-ed-zoom-out')?.addEventListener('click', () => { st.zoom = Math.max(0.4, st.zoom / 1.25); renderCanvas(); });
  $('gui-ed-zoom-reset')?.addEventListener('click', () => { st.zoom = 1; renderCanvas(); });

  $('gui-ed-units')?.addEventListener('click', () => {
    st.unitMode = st.unitMode === '%' ? 'px' : '%';
    const lbl = $('gui-ed-units-label');
    if (lbl) lbl.textContent = st.unitMode;
    $('gui-ed-units')?.classList.toggle('gui-ed-btn-primary', st.unitMode === '%');
  });
  $('gui-ed-units')?.classList.add('gui-ed-btn-primary');

  const refreshGridBtns = () => {
    $('gui-ed-grid-toggle')?.classList.toggle('active', st.gridShow);
    $('gui-ed-snap-toggle')?.classList.toggle('active', st.gridSnap);
    $('gui-ed-widget-snap-toggle')?.classList.toggle('active', st.widgetSnap);
  };
  $('gui-ed-grid-toggle')?.addEventListener('click', () => { st.gridShow = !st.gridShow; refreshGridBtns(); renderCanvas(); });
  $('gui-ed-snap-toggle')?.addEventListener('click', () => { st.gridSnap = !st.gridSnap; refreshGridBtns(); });
  $('gui-ed-widget-snap-toggle')?.addEventListener('click', () => { st.widgetSnap = !st.widgetSnap; refreshGridBtns(); });
  $('gui-ed-grid-step')?.addEventListener('change', (e) => {
    st.gridStep = Math.max(1, parseInt(e.target.value, 10) || 5);
    renderCanvas();
  });
  refreshGridBtns();

  $('gui-ed-group')?.addEventListener('click', () => doGroupSelection());
  $('gui-ed-ungroup')?.addEventListener('click', () => doUngroupSelection());

  window.addEventListener('resize', () => { if (st.open) renderCanvas(); });

  window.addEventListener('keydown', (e) => {
    if (!st.open) return;
    if (e.key === 'Escape') {
      closeGuiEditor(true);
      e.stopPropagation();
      return;
    }
    const tag = document.activeElement?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

    if ((e.ctrlKey || e.metaKey) && (e.key === 'g' || e.key === 'G')) {
      if (e.shiftKey) doUngroupSelection();
      else doGroupSelection();
      e.preventDefault();
      return;
    }

    if ((e.ctrlKey || e.metaKey) && e.key === 'c' && getSelectionUids().length) {
      copySelectedWidget();
      e.preventDefault();
      return;
    }
    if ((e.ctrlKey || e.metaKey) && e.key === 'v' && st.clipboard) {
      pasteClipboardWidget();
      e.preventDefault();
      return;
    }

    if ((e.key === 'Delete' || e.key === 'Backspace') && getSelectionUids().length) {
      deleteSelection();
      e.preventDefault();
    }
    // стрелки — точное перемещение на 1 px (Shift — 10)
    if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key) && getSelectionUids().length) {
      const step = e.shiftKey ? 10 : 1;
      for (const uid of getSelectionUids()) {
        const w = findGuiWidget(st.model, uid);
        if (!w) continue;
        const dims = widgetParentDims(w);
        let x = resolveCoord(w.pos?.[0] ?? 0, dims.w);
        let y = resolveCoord(w.pos?.[1] ?? 0, dims.h);
        if (e.key === 'ArrowLeft') x -= step;
        if (e.key === 'ArrowRight') x += step;
        if (e.key === 'ArrowUp') y -= step;
        if (e.key === 'ArrowDown') y += step;
        w.pos = [
          coordInUnit(x, dims.w, isPct(w.pos?.[0])),
          coordInUnit(y, dims.h, isPct(w.pos?.[1])),
        ];
      }
      renderAll();
      e.preventDefault();
    }
  }, true);
}

/** Мост для блока: кнопка редактирования вызывает window.__sprauteOpenGuiEditor(block). */
export function setupGuiEditorBridge(getTextures) {
  window.__sprauteOpenGuiEditor = async (block) => {
    const textures = typeof getTextures === 'function' ? (getTextures() || []) : [];
    await openGuiEditor(block, textures);
  };
}
