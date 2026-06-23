/** Генерация Spraute-кода create ui из модели GUI (.sprv). */

function sprQuote(s) {
  return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
}

export function formatSprValue(v) {
  if (v === null || v === undefined) return 'null';
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (typeof v === 'number' && Number.isFinite(v)) return String(v);
  if (Array.isArray(v)) return '[' + v.map(formatSprValue).join(', ') + ']';
  if (typeof v === 'string' && /^-?\d+(\.\d+)?$/.test(v.trim())) return v.trim();
  return sprQuote(String(v));
}

function formatWidgetArgs(kind, args) {
  if (!args || args.length === 0) {
    if (kind === 'clip' || kind === 'scroll' || kind === 'panel') return '';
    return '';
  }
  return args.map(a => {
    if (typeof a === 'number') return String(a);
    return sprQuote(String(a));
  }).join(', ');
}

function widgetHasBody(widget) {
  const props = widget.props && Object.keys(widget.props).length > 0;
  const kids = widget.children && widget.children.length > 0;
  const ev = widget.events && Object.keys(widget.events).length > 0;
  return props || kids || ev;
}

export function widgetToSprLines(widget, indent = 1) {
  const pad = '    '.repeat(indent);
  const kind = widget.kind || 'rect';
  const args = formatWidgetArgs(kind, widget.args || []);
  const head = args ? `${kind}(${args})` : `${kind}()`;
  const lines = [];

  if (!widgetHasBody(widget)) {
    lines.push(`${pad}${head}`);
    return lines;
  }

  lines.push(`${pad}${head} {`);
  for (const [k, v] of Object.entries(widget.props || {})) {
    if (v === '' || v === null || v === undefined) continue;
    lines.push(`${pad}    ${k} = ${formatSprValue(v)}`);
  }
  for (const [evName, evBody] of Object.entries(widget.events || {})) {
    if (!evBody || !String(evBody).trim()) continue;
    lines.push(`${pad}    ${evName} {`);
    for (const line of String(evBody).split('\n')) {
      const t = line.trim();
      if (t) lines.push(`${pad}        ${t}`);
    }
    lines.push(`${pad}    }`);
  }
  for (const child of widget.children || []) {
    lines.push(...widgetToSprLines(child, indent + 1));
  }
  lines.push(`${pad}}`);
  return lines;
}

export function guiModelToSprCode(model) {
  if (!model || !model.name) return '// GUI: укажите имя\n';
  const lines = [`create ui ${model.name} {`];
  const root = model.root || {};
  if (root.size) lines.push(`    size = ${formatSprValue(root.size)}`);
  if (root.background) lines.push(`    background = ${formatSprValue(root.background)}`);
  if (root.bg) lines.push(`    bg = ${formatSprValue(root.bg)}`);
  if (root.id) lines.push(`    id = ${formatSprValue(root.id)}`);
  if (root.pos) lines.push(`    pos = ${formatSprValue(root.pos)}`);
  if (root.canClose === true) lines.push('    canClose = true');
  if (root.canClose === false) lines.push('    canClose = false');

  for (const w of model.widgets || []) {
    lines.push(...widgetToSprLines(w, 1));
  }
  lines.push('}');
  return lines.join('\n');
}

export function createEmptyGuiModel(name = 'my_ui') {
  return {
    version: 1,
    kind: 'gui',
    name,
    openMode: 'gui',
    root: {
      size: ['100%', '100%'],
      background: '#00000000',
      canClose: true,
    },
    widgets: [],
  };
}

export function normalizeGuiModel(raw) {
  const m = raw && typeof raw === 'object' ? raw : createEmptyGuiModel();
  if (!m.root) m.root = { size: ['100%', '100%'], background: '#00000000' };
  if (!m.widgets) m.widgets = [];
  if (!m.name) m.name = 'my_ui';
  if (!m.openMode) m.openMode = 'gui';
  m.kind = 'gui';
  m.version = 1;
  return m;
}
