import { normalizeGuiModel, createEmptyGuiModel } from './gui-codegen.js';
import { applyGuiDataToBlock, guiSprvPathFor, guiDirForScript } from './gui-blocks.js';

const WIDGET_PALETTE = [
  { kind: 'rect', label: 'Прямоугольник', icon: 'crop_square' },
  { kind: 'text', label: 'Текст', icon: 'title', defaultArgs: ['label', 'Текст'] },
  { kind: 'button', label: 'Кнопка', icon: 'smart_button', defaultArgs: ['btn', 'Кнопка'] },
  { kind: 'image', label: 'Картинка', icon: 'image', defaultArgs: ['img', 'textures/gui/icon.png'] },
  { kind: 'input', label: 'Поле ввода', icon: 'edit', defaultArgs: ['input'] },
  { kind: 'clip', label: 'Клип', icon: 'content_cut', defaultArgs: ['clip'] },
  { kind: 'scroll', label: 'Скролл', icon: 'view_list', defaultArgs: ['scroll'] },
  { kind: 'panel', label: 'Панель', icon: 'dashboard', defaultArgs: ['panel'] },
];

let editorState = {
  open: false,
  block: null,
  scriptPath: '',
  model: null,
  selectedId: null,
  textures: [],
};

function uid() {
  return 'w_' + Math.random().toString(36).slice(2, 9);
}

function walkWidgets(widgets, fn, parent = null) {
  if (!widgets) return;
  for (let i = 0; i < widgets.length; i++) {
    const w = widgets[i];
    fn(w, parent, widgets, i);
    if (w.children) walkWidgets(w.children, fn, w);
  }
}

function findWidget(model, id) {
  let found = null;
  walkWidgets(model.widgets, w => { if (w.id === id) found = w; });
  return found;
}

function defaultPropsForKind(kind) {
  const base = {
    pos: ['10%', '10%'],
    size: ['20%', '10%'],
    layer: 0,
  };
  switch (kind) {
    case 'text':
      return { ...base, color: '#EAEAEA', scale: 1.0, align: 'left' };
    case 'button':
      return { ...base, size: ['24%', '8%'], color: '#55336688', hover: '#66447799' };
    case 'rect':
    case 'panel':
      return { ...base, color: '#88000000' };
    case 'image':
      return { ...base, texture: 'textures/gui/icon.png' };
    case 'input':
      return { ...base, size: ['30%', '6%'], placeholder: '...' };
    case 'clip':
    case 'scroll':
      return { pos: ['0%', '0%'], size: ['100%', '100%'] };
    default:
      return base;
  }
}

function createWidget(kind, paletteItem) {
  const args = paletteItem?.defaultArgs ? [...paletteItem.defaultArgs] : [kind];
  if (kind === 'rect' || kind === 'panel') args[0] = args[0] || kind + '_' + uid().slice(2, 6);
  return {
    id: uid(),
    kind,
    args,
    props: defaultPropsForKind(kind),
    children: kind === 'clip' || kind === 'scroll' || kind === 'panel' ? [] : undefined,
    events: {},
  };
}

async function ensureGuiDir(dir) {
  if (!window.spraute) return;
  try {
    await window.spraute.mkdir(dir);
  } catch (_) { /* exists */ }
}

export async function loadGuiSprv(relPath) {
  const raw = await window.spraute.readFile(relPath, 'utf8');
  const data = JSON.parse(raw);
  if (data.kind === 'gui' || data.widgets) return normalizeGuiModel(data);
  throw new Error('Не файл GUI (.sprv)');
}

export async function saveGuiSprv(relPath, model) {
  const payload = normalizeGuiModel(model);
  await window.spraute.writeFile(relPath, JSON.stringify(payload, null, 2));
}

export async function listGuiSprvFiles(scriptPath) {
  const dir = guiDirForScript(scriptPath);
  try {
    const items = await window.spraute.listDir(dir);
    return items.filter(i => !i.isDir && i.name.endsWith('.sprv')).map(i => i.rel);
  } catch {
    return [];
  }
}

function pct(v) {
  if (typeof v === 'string' && v.endsWith('%')) return parseFloat(v) || 0;
  if (typeof v === 'number') return v;
  return parseFloat(String(v)) || 0;
}

function toPctStr(n) {
  const v = Math.max(0, Math.min(100, Number(n) || 0));
  return v.toFixed(1).replace(/\.0$/, '') + '%';
}

function getEl(id) {
  return document.getElementById(id);
}

function renderCanvas() {
  const canvas = getEl('gui-editor-canvas');
  if (!canvas || !editorState.model) return;
  canvas.innerHTML = '';
  const root = document.createElement('div');
  root.className = 'gui-canvas-root';
  root.style.cssText = 'position:relative;width:100%;height:100%;background:' + (editorState.model.root?.background || '#1a1a2e') + ';overflow:hidden;border-radius:8px;';

  walkWidgets(editorState.model.widgets, w => {
    const el = document.createElement('div');
    el.className = 'gui-widget' + (editorState.selectedId === w.id ? ' selected' : '');
    el.dataset.wid = w.id;
    const pos = w.props?.pos || ['0%', '0%'];
    const size = w.props?.size || ['10%', '10%'];
    el.style.left = toPctStr(pct(pos[0]));
    el.style.top = toPctStr(pct(pos[1]));
    el.style.width = toPctStr(pct(size[0]));
    el.style.height = toPctStr(pct(size[1]));
    el.style.zIndex = String(w.props?.layer || 0);

    let label = w.kind;
    if (w.kind === 'text' || w.kind === 'button') label = (w.args?.[1] || w.args?.[0] || w.kind).slice(0, 24);
    if (w.kind === 'image') label = '🖼';
    el.innerHTML = `<span class="gui-widget-label">${label}</span>`;

    if (w.kind === 'rect' || w.kind === 'panel') {
      el.style.background = w.props?.color || '#444';
    }
    if (w.kind === 'button') {
      el.style.background = w.props?.color || '#55336688';
      el.style.display = 'flex';
      el.style.alignItems = 'center';
      el.style.justifyContent = 'center';
      el.style.color = '#fff';
      el.style.fontSize = '11px';
    }
    if (w.kind === 'text') {
      el.style.color = w.props?.color || '#EAEAEA';
      el.style.fontSize = '12px';
      el.style.overflow = 'hidden';
    }
    if (w.kind === 'image' && w.props?.texture) {
      el.style.backgroundImage = `url(spraute://${w.props.texture})`;
      el.style.backgroundSize = 'contain';
      el.style.backgroundRepeat = 'no-repeat';
      el.style.backgroundPosition = 'center';
    }
    if (w.kind === 'clip' || w.kind === 'scroll') {
      el.style.border = '1px dashed rgba(56,189,248,0.5)';
      el.style.background = 'rgba(56,189,248,0.08)';
    }

    el.addEventListener('mousedown', e => {
      e.stopPropagation();
      selectWidget(w.id);
      startDrag(e, w);
    });
    root.appendChild(el);
  });

  root.addEventListener('mousedown', () => selectWidget(null));
  canvas.appendChild(root);
}

function selectWidget(id) {
  editorState.selectedId = id;
  renderCanvas();
  renderProps();
  renderWidgetTree();
}

function renderWidgetTree() {
  const tree = getEl('gui-editor-tree');
  if (!tree || !editorState.model) return;
  tree.innerHTML = '';
  const ul = document.createElement('ul');
  ul.className = 'gui-tree-list';

  function addNode(w, depth) {
    const li = document.createElement('li');
    li.style.paddingLeft = (depth * 12) + 'px';
    li.className = editorState.selectedId === w.id ? 'active' : '';
    li.textContent = `${w.kind} (${w.args?.[0] || w.id})`;
    li.onclick = () => selectWidget(w.id);
    ul.appendChild(li);
    if (w.children) w.children.forEach(c => addNode(c, depth + 1));
  }
  (editorState.model.widgets || []).forEach(w => addNode(w, 0));
  tree.appendChild(ul);
}

function renderProps() {
  const panel = getEl('gui-editor-props');
  if (!panel) return;
  const w = editorState.selectedId ? findWidget(editorState.model, editorState.selectedId) : null;

  if (!w) {
    const root = editorState.model?.root || {};
    panel.innerHTML = `
      <h4 class="text-xs font-bold text-on-variant uppercase mb-2">Корень UI</h4>
      <label class="gui-prop-row"><span>Ширина</span><input data-root="size0" value="${root.size?.[0] || '100%'}" /></label>
      <label class="gui-prop-row"><span>Высота</span><input data-root="size1" value="${root.size?.[1] || '100%'}" /></label>
      <label class="gui-prop-row"><span>Фон</span><input data-root="background" value="${root.background || '#00000000'}" /></label>
      <label class="gui-prop-row"><span>canClose</span><select data-root="canClose"><option value="true" ${root.canClose !== false ? 'selected' : ''}>да</option><option value="false" ${root.canClose === false ? 'selected' : ''}>нет</option></select></label>
    `;
    panel.querySelectorAll('[data-root]').forEach(inp => {
      inp.onchange = () => {
        if (!editorState.model.root.size) editorState.model.root.size = ['100%', '100%'];
        if (inp.dataset.root === 'size0') editorState.model.root.size[0] = inp.value;
        if (inp.dataset.root === 'size1') editorState.model.root.size[1] = inp.value;
        if (inp.dataset.root === 'background') editorState.model.root.background = inp.value;
        if (inp.dataset.root === 'canClose') editorState.model.root.canClose = inp.value === 'true';
        renderCanvas();
      };
    });
    return;
  }

  const texOpts = editorState.textures.map(t => `<option value="${t}" ${w.props?.texture === t ? 'selected' : ''}>${t.split('/').pop()}</option>`).join('');

  panel.innerHTML = `
    <h4 class="text-xs font-bold text-primary mb-2">${w.kind} · ${w.args?.[0] || ''}</h4>
    <label class="gui-prop-row"><span>ID (arg0)</span><input data-p="arg0" value="${w.args?.[0] || ''}" /></label>
    ${w.kind === 'text' || w.kind === 'button' ? `<label class="gui-prop-row"><span>Текст</span><input data-p="arg1" value="${w.args?.[1] || ''}" /></label>` : ''}
    ${w.kind === 'image' ? `<label class="gui-prop-row"><span>Текстура</span><select data-p="texture"><option value="">—</option>${texOpts}</select></label>` : ''}
    <label class="gui-prop-row"><span>pos X</span><input data-p="pos0" value="${w.props?.pos?.[0] || '0%'}" /></label>
    <label class="gui-prop-row"><span>pos Y</span><input data-p="pos1" value="${w.props?.pos?.[1] || '0%'}" /></label>
    <label class="gui-prop-row"><span>size W</span><input data-p="size0" value="${w.props?.size?.[0] || '10%'}" /></label>
    <label class="gui-prop-row"><span>size H</span><input data-p="size1" value="${w.props?.size?.[1] || '10%'}" /></label>
    <label class="gui-prop-row"><span>color</span><input data-p="color" value="${w.props?.color || ''}" /></label>
    <label class="gui-prop-row"><span>scale</span><input data-p="scale" type="number" step="0.1" value="${w.props?.scale ?? ''}" /></label>
    <label class="gui-prop-row"><span>layer</span><input data-p="layer" type="number" value="${w.props?.layer ?? 0}" /></label>
    <button type="button" class="gui-btn-danger mt-3" id="gui-delete-widget">Удалить виджет</button>
  `;

  panel.querySelectorAll('[data-p]').forEach(inp => {
    inp.onchange = () => applyPropChange(w, inp.dataset.p, inp.value);
  });
  getEl('gui-delete-widget')?.addEventListener('click', () => deleteWidget(w.id));
}

function applyPropChange(w, key, val) {
  if (!w.props) w.props = {};
  if (key === 'arg0') { w.args = w.args || []; w.args[0] = val; }
  else if (key === 'arg1') { w.args = w.args || ['', '']; w.args[1] = val; }
  else if (key === 'pos0') { w.props.pos = w.props.pos || ['0%', '0%']; w.props.pos[0] = val; }
  else if (key === 'pos1') { w.props.pos = w.props.pos || ['0%', '0%']; w.props.pos[1] = val; }
  else if (key === 'size0') { w.props.size = w.props.size || ['10%', '10%']; w.props.size[0] = val; }
  else if (key === 'size1') { w.props.size = w.props.size || ['10%', '10%']; w.props.size[1] = val; }
  else if (key === 'scale' || key === 'layer') w.props[key] = parseFloat(val) || 0;
  else w.props[key] = val;
  renderCanvas();
  renderWidgetTree();
}

function deleteWidget(id) {
  function removeFrom(arr) {
    const i = arr.findIndex(x => x.id === id);
    if (i >= 0) { arr.splice(i, 1); return true; }
    for (const w of arr) {
      if (w.children && removeFrom(w.children)) return true;
    }
    return false;
  }
  removeFrom(editorState.model.widgets);
  editorState.selectedId = null;
  renderCanvas();
  renderProps();
  renderWidgetTree();
}

let drag = null;
function startDrag(e, w) {
  const canvas = getEl('gui-editor-canvas');
  const rect = canvas.getBoundingClientRect();
  drag = { w, startX: e.clientX, startY: e.clientY, rect, pos0: pct(w.props?.pos?.[0]), pos1: pct(w.props?.pos?.[1]) };
  const onMove = ev => {
    if (!drag) return;
    const dx = ((ev.clientX - drag.startX) / drag.rect.width) * 100;
    const dy = ((ev.clientY - drag.startY) / drag.rect.height) * 100;
    if (!drag.w.props) drag.w.props = {};
    if (!drag.w.props.pos) drag.w.props.pos = ['0%', '0%'];
    drag.w.props.pos[0] = toPctStr(drag.pos0 + dx);
    drag.w.props.pos[1] = toPctStr(drag.pos1 + dy);
    renderCanvas();
  };
  const onUp = () => {
    drag = null;
    window.removeEventListener('mousemove', onMove);
    window.removeEventListener('mouseup', onUp);
    renderProps();
  };
  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);
}

function renderPalette() {
  const el = getEl('gui-editor-palette');
  if (!el) return;
  el.innerHTML = '';
  for (const item of WIDGET_PALETTE) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'gui-palette-btn';
    btn.innerHTML = `<span class="material-symbols-outlined text-[18px]">${item.icon}</span><span>${item.label}</span>`;
    btn.onclick = () => {
      const w = createWidget(item.kind, item);
      editorState.model.widgets.push(w);
      selectWidget(w.id);
    };
    el.appendChild(btn);
  }
}

async function refreshGuiFileList() {
  const sel = getEl('gui-editor-open-select');
  if (!sel || !editorState.scriptPath) return;
  const files = await listGuiSprvFiles(editorState.scriptPath);
  sel.innerHTML = '<option value="">— новый —</option>' + files.map(f =>
    `<option value="${f}">${f.split('/').pop()}</option>`
  ).join('');
}

function syncModelFromForm() {
  if (!editorState.model) return;
  const nameInp = getEl('gui-editor-name');
  const modeGui = getEl('gui-editor-mode-gui');
  if (nameInp) editorState.model.name = nameInp.value.trim() || 'my_ui';
  editorState.model.openMode = modeGui?.checked ? 'gui' : 'overlay';
}

export async function openGuiEditor(block, scriptPath, textures = []) {
  editorState.block = block;
  editorState.scriptPath = scriptPath || '';
  editorState.textures = textures;
  editorState.open = true;

  let model = createEmptyGuiModel(block?.getFieldValue?.('GUI_NAME') || 'my_ui');
  const path = block?.getFieldValue?.('GUI_PATH');
  const json = block?.getFieldValue?.('GUI_JSON');
  if (path && window.spraute && await window.spraute.exists(path)) {
    try { model = await loadGuiSprv(path); } catch (_) {}
  } else if (json) {
    try { model = normalizeGuiModel(JSON.parse(json)); } catch (_) {}
  }
  editorState.model = model;
  editorState.selectedId = null;

  const overlay = getEl('gui-editor-overlay');
  if (overlay) overlay.classList.remove('hidden');

  const nameInp = getEl('gui-editor-name');
  if (nameInp) nameInp.value = model.name;
  const modeGui = getEl('gui-editor-mode-gui');
  const modeOverlay = getEl('gui-editor-mode-overlay');
  if (modeGui) modeGui.checked = model.openMode !== 'overlay';
  if (modeOverlay) modeOverlay.checked = model.openMode === 'overlay';

  await refreshGuiFileList();
  renderPalette();
  renderCanvas();
  renderProps();
  renderWidgetTree();
}

export function closeGuiEditor() {
  editorState.open = false;
  editorState.block = null;
  getEl('gui-editor-overlay')?.classList.add('hidden');
}

export function initGuiEditor() {
  getEl('gui-editor-close')?.addEventListener('click', closeGuiEditor);
  getEl('gui-editor-overlay')?.addEventListener('click', e => {
    if (e.target.id === 'gui-editor-overlay') closeGuiEditor();
  });

  getEl('gui-editor-mode-gui')?.addEventListener('change', () => {
    if (getEl('gui-editor-mode-gui')?.checked) editorState.model.openMode = 'gui';
  });
  getEl('gui-editor-mode-overlay')?.addEventListener('change', () => {
    if (getEl('gui-editor-mode-overlay')?.checked) editorState.model.openMode = 'overlay';
  });

  getEl('gui-editor-save')?.addEventListener('click', async () => {
    syncModelFromForm();
    if (!editorState.scriptPath) return alert('Сначала откройте .spr скрипт');
    const path = guiSprvPathFor(editorState.scriptPath, editorState.model.name);
    await ensureGuiDir(guiDirForScript(editorState.scriptPath));
    await saveGuiSprv(path, editorState.model);
    if (editorState.block) applyGuiDataToBlock(editorState.block, editorState.model, path);
    await refreshGuiFileList();
    alert('GUI сохранён: ' + path);
  });

  getEl('gui-editor-add-project')?.addEventListener('click', async () => {
    syncModelFromForm();
    if (!editorState.scriptPath) return alert('Сначала откройте .spr скрипт');
    const path = guiSprvPathFor(editorState.scriptPath, editorState.model.name);
    await ensureGuiDir(guiDirForScript(editorState.scriptPath));
    await saveGuiSprv(path, editorState.model);
    if (editorState.block) {
      applyGuiDataToBlock(editorState.block, editorState.model, path);
      editorState.block.setFieldValue(editorState.model.openMode === 'overlay' ? 'overlay' : 'gui', 'OPEN_MODE');
    }
    await refreshGuiFileList();
    closeGuiEditor();
    if (typeof window.__sprauteGuiAddedToProject === 'function') {
      window.__sprauteGuiAddedToProject();
    }
  });

  getEl('gui-editor-open-select')?.addEventListener('change', async e => {
    const p = e.target.value;
    if (!p) return;
    try {
      editorState.model = await loadGuiSprv(p);
      editorState.selectedId = null;
      getEl('gui-editor-name').value = editorState.model.name;
      renderCanvas();
      renderProps();
      renderWidgetTree();
    } catch (err) {
      alert('Ошибка загрузки: ' + err.message);
    }
  });
}

export function setupGuiEditorBridge(getTextures) {
  window.__sprauteOpenGuiEditor = async (block) => {
    const textures = typeof getTextures === 'function' ? getTextures() : [];
    await openGuiEditor(block, window.__sprauteCurrentScriptPath || '', textures);
  };
}
