/**
 * Переключатель поле ↔ value-слот (те же стрелочки, что у блоков плагинов ProCode).
 */
import * as Blockly from 'blockly';

/** Тот же SVG, что генерируется для .spr-блоков в visual.js */
export const SPRAUTE_TOGGLE_ICON =
  'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNSIgaGVpZ2h0PSIxNSIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgOWwtMyAzIDMgM200LTZsMyAzLTMgMyIvPjwvc3ZnPg==';

function normalizeLiteral(value) {
  if (value === 'TRUE') return 'true';
  if (value === 'FALSE') return 'false';
  return value;
}

function syncFieldVals(block) {
  for (const input of block.inputList) {
    for (const field of input.fieldRow) {
      if (!field.name) continue;
      try {
        const v = field.getValue();
        if (v != null) block[`val_${field.name}`] = v;
      } catch (e) { /* ignore */ }
    }
  }
}

/** Blockly 12: setOnChange заменяет предыдущий обработчик — цепочка через массив. */
export function chainBlockOnChange(block, fn) {
  if (!block._sprauteChangeChain_) {
    block._sprauteChangeChain_ = [];
    block.setOnChange(function (e) {
      const chain = this._sprauteChangeChain_;
      if (!chain) return;
      for (const f of chain) {
        try { f.call(this, e); } catch (err) { /* ignore */ }
      }
    });
  }
  block._sprauteChangeChain_.push(fn);
}

function slotTogglesSnapshot(slotToggles) {
  const snap = {};
  if (!slotToggles) return snap;
  for (const [k, v] of Object.entries(slotToggles)) {
    if (v) snap[k] = true;
  }
  return snap;
}

function slotTogglesEqual(a, b) {
  const ka = Object.keys(a || {}).sort();
  const kb = Object.keys(b || {}).sort();
  if (ka.length !== kb.length) return false;
  for (let i = 0; i < ka.length; i++) {
    if (ka[i] !== kb[i]) return false;
  }
  return true;
}

function applyToggleFieldVals(block) {
  for (const row of block._toggleRows_ || []) {
    for (const part of row.parts) {
      if (part.type !== 'field' || block.slotToggles_?.[part.name]) continue;
      const cached = block[`val_${part.name}`];
      if (cached == null) continue;
      try {
        const field = block.getField(part.name);
        if (!field) continue;
        field.setValue(part.fieldType === 'number' ? Number(cached) : String(cached));
      } catch (e) { /* ignore */ }
    }
  }
}

function makeFieldValidator(block, part) {
  const name = part.name;
  return function (v) {
    block[`val_${name}`] = v;
    if (typeof part.validator === 'function') return part.validator(v);
    return v;
  };
}

function snapshotValueInputs(block, generator) {
  if (!generator || !block._toggleValueNames_?.length) return;
  for (const name of block._toggleValueNames_) {
    const input = block.getInput(name);
    const child = input?.connection?.targetBlock();
    if (!child) continue;
    try {
      let gen = generator.blockToCode(child);
      if (Array.isArray(gen)) gen = gen[0];
      const s = gen == null ? '' : String(gen).trim();
      if (s) block[`val_${name}`] = s;
    } catch (e) { /* ignore */ }
  }
}

function appendToggleField(block, row, part, created) {
  const name = part.name;
  block.slotToggles_ = block.slotToggles_ || {};
  if (block.slotToggles_[name]) {
    row = block.appendValueInput(name);
    created.push(name);
    if (!block._toggleValueNames_.includes(name)) block._toggleValueNames_.push(name);
    row.appendField(new Blockly.FieldImage(SPRAUTE_TOGGLE_ICON, 15, 15, '*', () => {
      block.slotToggles_[name] = false;
      rebuildSprauteToggleShape(block);
    }));
    const cont = `${part.rowInput || 'row'}_c_${name}`;
    row = block.appendDummyInput(cont);
    created.push(cont);
    return row;
  }
  row.appendField(new Blockly.FieldImage(SPRAUTE_TOGGLE_ICON, 15, 15, '*', () => {
    block.slotToggles_[name] = true;
    rebuildSprauteToggleShape(block);
  }));
  const cached = block[`val_${name}`];
  if (part.fieldType === 'number') {
    const spec = part.numberSpec || {};
    const def = cached != null && cached !== '' ? Number(cached) : Number(part.default ?? 0);
    row.appendField(new Blockly.FieldNumber(
      def,
      spec.min ?? -Infinity,
      spec.max ?? Infinity,
      spec.precision ?? 1,
      makeFieldValidator(block, part)
    ), name);
  } else {
    const def = cached != null ? String(cached) : String(part.default ?? '');
    row.appendField(new Blockly.FieldTextInput(def, makeFieldValidator(block, part)), name);
  }
  return row;
}

export function rebuildSprauteToggleShape(block) {
  const rows = block._toggleRows_;
  if (!rows?.length || block._sprauteToggleReshaping) return;
  if (typeof block.isCollapsed === 'function' && block.isCollapsed()) return;

  block._sprauteToggleReshaping = true;
  try {
    syncFieldVals(block);
    snapshotValueInputs(block, block._toggleGenerator_);

    const savedChildIds = {};
    for (const name of block._toggleValueNames_ || []) {
      const child = block.getInput(name)?.connection?.targetBlock();
      if (child && !child.isDisposed()) savedChildIds[name] = child.id;
    }

    for (const n of [...(block._toggleDynamicInputs_ || [])]) {
      if (block.getInput(n)) block.removeInput(n, true);
    }
    block._toggleDynamicInputs_ = [];

    const anchor = block._toggleAnchor_;
    for (const rowDef of rows) {
      const created = [];
      let row = block.appendDummyInput(rowDef.input);
      created.push(rowDef.input);
      for (const part of rowDef.parts) {
        if (part.type === 'label') {
          row.appendField(part.text);
        } else if (part.type === 'dropdown') {
          row.appendField(new Blockly.FieldDropdown(part.options), part.name);
        } else if (part.type === 'field') {
          part.rowInput = rowDef.input;
          row = appendToggleField(block, row, part, created);
        }
      }
      block._toggleDynamicInputs_.push(...created);
      if (anchor && block.getInput(anchor)) {
        for (const n of created) {
          if (block.getInput(n)) block.moveInputBefore(n, anchor);
        }
      }
    }

    for (const [name, id] of Object.entries(savedChildIds)) {
      const input = block.getInput(name);
      const child = block.workspace?.getBlockById(id);
      if (input?.connection && child?.outputConnection && !child.isDisposed()) {
        try { child.outputConnection.connect(input.connection); } catch (e) { /* ignore */ }
      }
    }
  } finally {
    block._sprauteToggleReshaping = false;
  }
}

function applyToggleMutationToDom(block, container) {
  syncFieldVals(block);
  snapshotValueInputs(block, block._toggleGenerator_);
  for (const key of Object.getOwnPropertyNames(block)) {
    if (!key.startsWith('val_')) continue;
    const fname = key.slice(4);
    const val = block[key];
    if (val != null && val !== '') container.setAttribute(`f_${fname}`, val);
  }
  if (block.slotToggles_) {
    for (const [k, v] of Object.entries(block.slotToggles_)) {
      if (v) container.setAttribute(`t_${k}`, 'true');
    }
  }
  for (const name of block._toggleValueNames_ || []) {
    const cached = block[`val_${name}`];
    if (cached != null && String(cached).trim() !== '') {
      container.setAttribute(`c_${name}`, String(cached));
    }
  }
}

function loadToggleMutationFromDom(block, el) {
  block.slotToggles_ = block.slotToggles_ || {};
  for (const attr of el.attributes) {
    if (attr.name.startsWith('f_')) block[`val_${attr.name.slice(2)}`] = attr.value;
    else if (attr.name.startsWith('t_')) block.slotToggles_[attr.name.slice(2)] = attr.value === 'true';
    else if (attr.name.startsWith('c_')) block[`val_${attr.name.slice(2)}`] = attr.value;
  }
}

function wrapSprauteToggleMutation(block) {
  if (block._toggleMutationWrapped_) return;
  block._toggleMutationWrapped_ = true;

  const origToDom = block.mutationToDom?.bind(block);
  const origFromDom = block.domToMutation?.bind(block);
  const origSave = block.saveExtraState?.bind(block);
  const origLoad = block.loadExtraState?.bind(block);

  block.mutationToDom = function () {
    let container = origToDom ? origToDom() : null;
    if (!container) container = Blockly.utils.xml.createElement('mutation');
    applyToggleMutationToDom(this, container);
    return container;
  };

  block.domToMutation = function (el) {
    if (origFromDom) origFromDom(el);
    loadToggleMutationFromDom(this, el);
    this._sprauteToggleRestoring_ = true;
    try {
      this.rebuildToggleShape_?.();
    } finally {
      this._sprauteToggleRestoring_ = false;
    }
  };

  block.saveExtraState = function () {
    const st = origSave ? (origSave.call(this) || {}) : {};
    syncFieldVals(this);
    snapshotValueInputs(this, this._toggleGenerator_);
    for (const key of Object.getOwnPropertyNames(this)) {
      if (!key.startsWith('val_')) continue;
      const fname = key.slice(4);
      const val = this[key];
      if (val != null && val !== '') st[fname] = val;
    }
    if (this.slotToggles_) {
      const toggles = {};
      for (const [k, v] of Object.entries(this.slotToggles_)) {
        if (v) toggles[k] = true;
      }
      if (Object.keys(toggles).length) st._toggles = toggles;
    }
    return st;
  };

  block.loadExtraState = function (st) {
    if (origLoad) origLoad.call(this, st);
    if (!st || typeof st !== 'object') return;
    const prevToggles = slotTogglesSnapshot(this.slotToggles_);
    this.slotToggles_ = {};
    if (st._toggles) {
      for (const k in st._toggles) {
        if (st._toggles[k]) this.slotToggles_[k] = true;
      }
    }
    for (const [k, v] of Object.entries(st)) {
      if (k === '_toggles') continue;
      this[`val_${k}`] = v;
    }
    const togglesChanged = !slotTogglesEqual(prevToggles, slotTogglesSnapshot(this.slotToggles_));
    if (togglesChanged || this.workspace?._sprauteRestoringBlocks) {
      this.rebuildToggleShape_?.();
    } else {
      applyToggleFieldVals(this);
    }
  };
}

/**
 * @param {Blockly.Block} block
 * @param {{ rows: Array, anchor?: string, generator?: object }} config
 */
export function initSprauteToggleBlock(block, config) {
  block._toggleRows_ = config.rows || [];
  block._toggleAnchor_ = config.anchor ?? null;
  block._toggleDynamicInputs_ = [];
  block._toggleValueNames_ = [];
  block._toggleGenerator_ = config.generator || null;
  block.slotToggles_ = block.slotToggles_ || {};
  for (const row of block._toggleRows_) {
    for (const part of row.parts) {
      if (part.type === 'field' && !block._toggleValueNames_.includes(part.name)) {
        block._toggleValueNames_.push(part.name);
      }
    }
  }
  block.rebuildToggleShape_ = function () { rebuildSprauteToggleShape(this); };

  chainBlockOnChange(block, function (e) {
    if (e.blockId === this.id && e.type === Blockly.Events.BLOCK_CHANGE && e.name) {
      this[`val_${e.name}`] = e.newValue;
    }
  });
  wrapSprauteToggleMutation(block);
}

export function finishSprauteToggleBlock(block) {
  if (!block.workspace?._sprauteRestoringBlocks && !block._sprauteToggleRestoring_) {
    block.rebuildToggleShape_?.();
  }
}

export function getSprauteBlockVal(block, generator, name) {
  const input = block.getInput(name);
  if (input && input.connection?.targetBlock()) {
    try {
      const code = generator.valueToCode(block, name, 0, '');
      if (code != null && String(code).trim() !== '') return normalizeLiteral(String(code).trim());
    } catch (e) { /* ignore */ }
  }
  const cached = block[`val_${name}`];
  if (cached != null && String(cached).trim() !== '') return normalizeLiteral(String(cached).trim());
  try {
    if (block.getField(name)) {
      const v = block.getFieldValue(name);
      if (v != null && v !== '') return normalizeLiteral(String(v));
    }
  } catch (e) { /* ignore */ }
  return '';
}

export function getSprauteBlockStr(block, generator, name) {
  const raw = String(getSprauteBlockVal(block, generator, name) ?? '').trim();
  if (!raw) return '""';
  if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) return raw;
  return JSON.stringify(raw);
}

export function slotExprFromVal(val) {
  const s = String(val ?? '').trim();
  if (!s) return '0';
  if (/^\d+$/.test(s)) return s;
  if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith("'") && s.endsWith("'"))) return s;
  return s;
}
