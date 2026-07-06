/**
 * Встроенные блоки для работы с GUI движка Spraute.
 * Генерируемый код строго соответствует функциям движка:
 *   create ui { ... }        — UiTemplate / ScriptParser.parseUiBlock
 *   uiOpen / overlayOpen     — UiOpenFunction / OverlayOpenFunction (player, template)
 *   uiClose / overlayClose   — UiCloseFunction / OverlayCloseFunction
 *   uiUpdate                 — UiUpdateFunction (player, widgetId, field, value)
 *   uiAnimate                — UiAnimateFunction (player, widgetId, field, target, duration[, easing])
 */
import * as Blockly from 'blockly';
import { normalizeGuiModel, guiModelToSprCode } from './gui-model.js';
import {
  initSprauteToggleBlock,
  finishSprauteToggleBlock,
  getSprauteBlockVal,
  getSprauteBlockStr,
  slotExprFromVal,
  chainBlockOnChange,
} from './block-toggle.js';

export const GUI_COLOR = '#14b8a6';

const EDIT_ICON = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24">' +
  '<rect x="1" y="1" width="22" height="22" rx="5" fill="#0ea5e9"/>' +
  '<rect x="1" y="1" width="22" height="22" rx="5" fill="none" stroke="#e0f2fe" stroke-width="1.5"/>' +
  '<path d="M15.6 6.8l1.6 1.6-6.8 6.8-2.2.6.6-2.2 6.8-6.8z" fill="#ffffff"/>' +
  '<path d="M7 18h10" stroke="#ffffff" stroke-width="1.6" stroke-linecap="round"/>' +
  '</svg>'
);

function guiToggles(block, generator, rows, anchor = 'TAIL') {
  initSprauteToggleBlock(block, { rows, anchor, generator });
}

function fieldText(name, def, label) {
  const p = { type: 'field', name, fieldType: 'text', default: def };
  return label ? [{ type: 'label', text: label }, p] : [p];
}

function fieldNum(name, def, label, spec) {
  const p = { type: 'field', name, fieldType: 'number', default: def, numberSpec: spec };
  return label ? [{ type: 'label', text: label }, p] : [p];
}

function playerCodeFor(block, generator, whoField, playerInput) {
  const who = block.getFieldValue(whoField) || 'event';
  if (who === 'first') return 'getFirstPlayer()';
  if (who === 'custom') {
    return generator.valueToCode(block, playerInput, 0, '_eventPlayer') || '_eventPlayer';
  }
  return '_eventPlayer';
}

function addWhoRow(block, whoField, label) {
  block.appendDummyInput()
    .appendField(label)
    .appendField(new Blockly.FieldDropdown([
      ['кликнувший', 'event'],
      ['первый', 'first'],
      ['указать', 'custom'],
    ], (v) => {
      block._sprauteWhoUpdate?.(v);
      return v;
    }), whoField);
}

function setupWhoShape(block, whoField, playerInput) {
  block._sprauteWhoUpdate = (v) => {
    const has = !!block.getInput(playerInput);
    if (v === 'custom' && !has) {
      block.appendValueInput(playerInput).appendField('игрок');
      if (block.getInput('TAIL')) block.moveInputBefore(playerInput, 'TAIL');
    } else if (v !== 'custom' && has) {
      block.removeInput(playerInput);
    }
  };
  const orig = block.saveExtraState;
  block.saveExtraState = function () {
    const st = orig ? (orig.call(this) || {}) : {};
    st.who = this.getFieldValue(whoField);
    return st;
  };
  const origLoad = block.loadExtraState;
  block.loadExtraState = function (st) {
    if (origLoad) origLoad.call(this, st);
    if (st?.who) {
      this.setFieldValue(st.who, whoField);
      this._sprauteWhoUpdate?.(st.who);
    }
  };
}

/** Blockly.Xml (v12) не вызывает saveExtraState — храним JSON в <mutation><gui>…</gui></mutation>. */
function guiJsonFromMutation(el) {
  if (!el) return null;
  const gui = el.getElementsByTagName?.('gui')?.[0];
  const raw = gui?.textContent || el.getAttribute?.('gui_json');
  return raw && String(raw).trim() ? String(raw) : null;
}

function guiJsonToMutation(block) {
  const container = Blockly.utils.xml.createElement('mutation');
  const gui = Blockly.utils.xml.createElement('gui');
  gui.appendChild(Blockly.utils.xml.createTextNode(block._guiJson || '{}'));
  container.appendChild(gui);
  return container;
}

/** Имя шаблона как идентификатор (без кавычек) — для поиска блока «создать GUI». */
function guiTemplateId(nameExpr) {
  const s = String(nameExpr ?? '').trim();
  if (!s) return '';
  if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith("'") && s.endsWith("'"))) {
    return s.slice(1, -1);
  }
  if (/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(s)) return s;
  return '';
}

function findGuiCreateBlock(workspace, templateId) {
  if (!workspace || !templateId) return null;
  for (const b of workspace.getAllBlocks(false)) {
    if (b.type !== 'spraute_gui_create') continue;
    const n = getSprauteBlockVal(b, null, 'GUI_NAME') || 'my_ui';
    if (guiTemplateId(n) === templateId) return b;
  }
  return null;
}

function openModeFromCreateBlock(createBlock) {
  if (!createBlock) return 'gui';
  try {
    if (createBlock.getFieldValue('OPEN_MODE') === 'overlay') return 'overlay';
  } catch (e) { /* ignore */ }
  try {
    const raw = createBlock._guiJson;
    if (raw && raw.trim() && raw !== '{}') {
      const model = JSON.parse(raw);
      if (model.openMode === 'overlay') return 'overlay';
    }
  } catch (e) { /* ignore */ }
  return 'gui';
}

/** Режим открытия (gui | overlay) из блока «создать GUI» с тем же именем шаблона. */
function resolveGuiOpenMode(block, generator, guiNameField = 'GUI_NAME') {
  const nameExpr = getSprauteBlockVal(block, generator, guiNameField) || 'my_ui';
  const templateId = guiTemplateId(nameExpr) || nameExpr;
  return openModeFromCreateBlock(findGuiCreateBlock(block.workspace, templateId));
}

function modeHintText(mode, kind = 'open') {
  if (kind === 'close') return mode === 'overlay' ? '→ overlayClose' : '→ uiClose';
  return mode === 'overlay' ? '→ overlayOpen' : '→ uiOpen';
}

function refreshGuiModeHint(block, generator, guiNameField = 'GUI_NAME', kind = 'open') {
  const hint = modeHintText(resolveGuiOpenMode(block, generator, guiNameField), kind);
  try {
    const field = block.getField('MODE_HINT');
    if (field) field.setValue(hint);
  } catch (e) { /* ignore */ }
}

function refreshGuiModeDependents(workspace, templateNameExpr) {
  if (!workspace) return;
  const id = guiTemplateId(templateNameExpr) || String(templateNameExpr ?? '').trim();
  if (!id) return;
  for (const b of workspace.getAllBlocks(false)) {
    if (b.type !== 'spraute_gui_open' && b.type !== 'spraute_gui_close') continue;
    const n = guiTemplateId(getSprauteBlockVal(b, null, 'GUI_NAME') || '');
    if (n === id) {
      refreshGuiModeHint(b, null, 'GUI_NAME', b.type === 'spraute_gui_close' ? 'close' : 'open');
    }
  }
}

function attachGuiModeHint(block, generator, guiNameField = 'GUI_NAME', kind = 'open') {
  chainBlockOnChange(block, function (ev) {
    if (!ev) return;
    if (ev.type === Blockly.Events.FINISHED_LOADING) {
      refreshGuiModeHint(block, generator, guiNameField, kind);
      return;
    }
    if (ev.blockId === block.id) {
      refreshGuiModeHint(block, generator, guiNameField, kind);
      return;
    }
    if (ev.type === Blockly.Events.BLOCK_CHANGE) {
      const changed = block.workspace?.getBlockById(ev.blockId);
      if (changed?.type === 'spraute_gui_create'
          && (ev.name === 'OPEN_MODE' || ev.name === 'GUI_NAME')) {
        const myId = guiTemplateId(getSprauteBlockVal(block, generator, guiNameField) || 'my_ui');
        const theirId = guiTemplateId(getSprauteBlockVal(changed, null, 'GUI_NAME') || 'my_ui');
        if (myId && myId === theirId) refreshGuiModeHint(block, generator, guiNameField, kind);
      }
    }
  });
  refreshGuiModeHint(block, generator, guiNameField, kind);
}

export function registerGuiBlocks(SprauteGenerator) {

  // ============ создать GUI (визуальный редактор) ============
  Blockly.Blocks['spraute_gui_create'] = {
    init: function () {
      const block = this;
      this.appendDummyInput('HEAD')
        .appendField(new Blockly.FieldImage(EDIT_ICON, 26, 26, 'Открыть редактор GUI', () => {
          if (typeof window.__sprauteOpenGuiEditor === 'function') {
            window.__sprauteOpenGuiEditor(block);
          }
        }))
        .appendField('создать GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_NAME',
        parts: fieldText('GUI_NAME', 'my_ui', 'имя'),
      }], 'MODE');
      this.appendDummyInput('MODE')
        .appendField('режим')
        .appendField(new Blockly.FieldDropdown([
          ['окно (uiOpen)', 'gui'],
          ['наложение (overlayOpen)', 'overlay'],
        ]), 'OPEN_MODE');
      this.appendStatementInput('EXTRA')
        .appendField('доп. код внутри');
      this._guiJson = '{}';
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Визуальный редактор GUI: кнопка слева. Режим «окно/наложение» задаётся здесь — блоки открыть/закрыть GUI используют его автоматически.');
      finishSprauteToggleBlock(this);
      chainBlockOnChange(this, function (ev) {
        if (!ev || ev.blockId !== this.id) return;
        if (ev.type === Blockly.Events.BLOCK_CHANGE
            && (ev.name === 'OPEN_MODE' || ev.name === 'GUI_NAME')) {
          refreshGuiModeDependents(this.workspace, getSprauteBlockVal(this, null, 'GUI_NAME'));
        }
      });
    },
    /** Сохранение в .sprv через Blockly.Xml */
    mutationToDom: function () {
      return guiJsonToMutation(this);
    },
    domToMutation: function (xmlElement) {
      const raw = guiJsonFromMutation(xmlElement);
      if (raw) this._guiJson = raw;
    },
    /** Копирование/вставка блоков (JSON serialization API) */
    saveExtraState: function () {
      return { json: this._guiJson || '{}' };
    },
    loadExtraState: function (st) {
      if (st?.json) this._guiJson = st.json;
    },
  };

  SprauteGenerator.forBlock['spraute_gui_create'] = function (block) {
    const name = getSprauteBlockVal(block, SprauteGenerator, 'GUI_NAME') || 'my_ui';
    const extra = SprauteGenerator.statementToCode(block, 'EXTRA', '') || '';
    let model = null;
    try {
      const raw = block._guiJson;
      if (raw && raw.trim() && raw.trim() !== '{}') model = normalizeGuiModel(JSON.parse(raw));
    } catch (e) { /* повреждённый JSON — генерируем каркас */ }
    if (!model) {
      model = normalizeGuiModel({ name });
    }
    model.name = name;
    model.openMode = block.getFieldValue('OPEN_MODE') === 'overlay' ? 'overlay' : 'gui';
    return guiModelToSprCode(model, extra) + '\n';
  };

  // ============ открыть GUI ============
  Blockly.Blocks['spraute_gui_open'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'открыть GUI для');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_OPEN',
        parts: fieldText('GUI_NAME', 'my_ui', 'GUI'),
      }], 'MODE_HINT');
      this.appendDummyInput('MODE_HINT')
        .appendField(new Blockly.FieldLabel('→ uiOpen'), 'MODE_HINT');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Открывает GUI. uiOpen / overlayOpen выбирается по режиму в блоке «создать GUI» с тем же именем шаблона.');
      finishSprauteToggleBlock(this);
      attachGuiModeHint(this, SprauteGenerator, 'GUI_NAME');
    },
  };
  SprauteGenerator.forBlock['spraute_gui_open'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const name = getSprauteBlockVal(block, SprauteGenerator, 'GUI_NAME') || 'my_ui';
    const fn = resolveGuiOpenMode(block, SprauteGenerator, 'GUI_NAME') === 'overlay' ? 'overlayOpen' : 'uiOpen';
    return `${fn}(${player}, ${name})\n`;
  };

  // ============ закрыть GUI ============
  Blockly.Blocks['spraute_gui_close'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'закрыть GUI у');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_CLOSE',
        parts: [
          ...fieldText('GUI_NAME', 'my_ui', 'GUI'),
          ...fieldText('OVERLAY_ID', '', 'id наложения'),
        ],
      }], 'MODE_HINT');
      this.appendDummyInput('MODE_HINT')
        .appendField(new Blockly.FieldLabel('→ uiClose'), 'MODE_HINT');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Закрывает GUI. uiClose / overlayClose — по режиму в блоке «создать GUI» с тем же именем. id наложения — только для overlayClose(player, id).');
      finishSprauteToggleBlock(this);
      attachGuiModeHint(this, SprauteGenerator, 'GUI_NAME', 'close');
    },
  };
  SprauteGenerator.forBlock['spraute_gui_close'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    if (resolveGuiOpenMode(block, SprauteGenerator, 'GUI_NAME') === 'overlay') {
      const idRaw = getSprauteBlockVal(block, SprauteGenerator, 'OVERLAY_ID');
      if (!idRaw || idRaw === '""' || idRaw === "''") return `overlayClose(${player})\n`;
      return `overlayClose(${player}, ${getSprauteBlockStr(block, SprauteGenerator, 'OVERLAY_ID')})\n`;
    }
    return `uiClose(${player})\n`;
  };

  // ============ обновить виджет ============
  Blockly.Blocks['spraute_gui_update'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'обновить виджет у');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_UPD',
        parts: [
          ...fieldText('WIDGET_ID', 'my_widget', 'id'),
          ...fieldText('FIELD', 'text', 'поле'),
        ],
      }], 'VALUE');
      this.appendValueInput('VALUE').appendField('значение');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiUpdate(player, id, поле, значение). Поля: text/color/scale, label/hover/texture, x/y/w/h и др.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_update'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const field = getSprauteBlockStr(block, SprauteGenerator, 'FIELD');
    const value = SprauteGenerator.valueToCode(block, 'VALUE', 0, '""') || '""';
    return `uiUpdate(${player}, ${wid}, ${field}, ${value})\n`;
  };

  // ============ анимировать виджет ============
  Blockly.Blocks['spraute_gui_animate'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'анимировать виджет у');
      guiToggles(this, SprauteGenerator, [
        {
          input: 'ROW_ANIM1',
          parts: [
            ...fieldText('WIDGET_ID', 'my_widget', 'id'),
            ...fieldText('FIELD', 'x', 'поле'),
          ],
        },
        {
          input: 'ROW_ANIM2',
          parts: [
            ...fieldText('TARGET', '100', 'до'),
            ...fieldNum('DURATION', 0.5, 'за', { min: 0, max: 600, precision: 0.1 }),
            { type: 'label', text: 'сек' },
          ],
        },
      ], 'TAIL');
      this.appendDummyInput('TAIL')
        .appendField(new Blockly.FieldDropdown([
          ['линейно', 'linear'],
          ['ease in', 'ease_in'],
          ['ease out', 'ease_out'],
          ['ease in-out', 'ease_in_out'],
          ['bounce', 'bounce_out'],
          ['elastic', 'elastic_out'],
        ]), 'EASING');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiAnimate(player, id, поле, цель, сек, easing). Анимируемые поля: x, y, w, h, scale, alpha.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_animate'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const field = getSprauteBlockStr(block, SprauteGenerator, 'FIELD');
    const target = getSprauteBlockStr(block, SprauteGenerator, 'TARGET');
    const dur = getSprauteBlockVal(block, SprauteGenerator, 'DURATION') || 0.5;
    const easing = block.getFieldValue('EASING') || 'linear';
    return `uiAnimate(${player}, ${wid}, ${field}, ${target}, ${dur}, "${easing}")\n`;
  };

  // ============ сетка виджетов (внутрь «создать GUI») ============
  Blockly.Blocks['spraute_gui_grid'] = {
    init: function () {
      this.appendDummyInput('HEAD')
        .appendField('сетка виджетов')
        .appendField(new Blockly.FieldDropdown([
          ['слоты', 'slot'],
          ['предметы', 'item'],
          ['кнопки', 'button'],
          ['прямоугольники', 'rect'],
        ]), 'KIND');
      guiToggles(this, SprauteGenerator, [
        {
          input: 'ROW_GRID1',
          parts: [
            ...fieldText('PREFIX', 'cell', 'id-префикс'),
            ...fieldNum('COLS', 9, 'колонок', { min: 1, max: 64, precision: 1 }),
            ...fieldNum('ROWS', 3, 'строк', { min: 1, max: 64, precision: 1 }),
          ],
        },
        {
          input: 'ROW_GRID2',
          parts: [
            ...fieldNum('X', 10, 'старт x', { min: -10000, max: 10000, precision: 1 }),
            ...fieldNum('Y', 10, 'y', { min: -10000, max: 10000, precision: 1 }),
            ...fieldNum('STEP_X', 18, 'шаг x', { min: 1, max: 10000, precision: 1 }),
            ...fieldNum('STEP_Y', 18, 'y', { min: 1, max: 10000, precision: 1 }),
          ],
        },
        {
          input: 'ROW_GRID3',
          parts: [
            ...fieldNum('CELL_W', 16, 'размер', { min: 1, max: 10000, precision: 1 }),
            { type: 'label', text: '×' },
            ...fieldNum('CELL_H', 16, '', { min: 1, max: 10000, precision: 1 }),
          ],
        },
      ], null);
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Программно создаёт сетку виджетов внутри блока «создать GUI» (циклом for). id: префикс_номер.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_grid'] = function (block) {
    const kind = block.getFieldValue('KIND') || 'slot';
    const prefix = (getSprauteBlockVal(block, SprauteGenerator, 'PREFIX') || 'cell').replace(/[^a-zA-Z0-9_]/g, '_');
    const cols = Math.max(1, Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'COLS')) || 1));
    const rows = Math.max(1, Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'ROWS')) || 1));
    const x = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'X')) || 0);
    const y = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'Y')) || 0);
    const sx = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'STEP_X')) || 18);
    const sy = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'STEP_Y')) || 18);
    const cw = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'CELL_W')) || 16);
    const ch = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'CELL_H')) || 16);
    const total = cols * rows;

    const v = `_gi_${prefix}`;
    const lines = [`for (${v} in range(${total})) {`];
    const posExpr = `pos = [${x} + (${v} % ${cols}) * ${sx}, ${y} + toInt(${v} / ${cols}) * ${sy}]`;
    if (kind === 'slot') {
      lines.push(`    slot("${prefix}_" + ${v}) {`);
      lines.push(`        ${posExpr}`);
      lines.push('    }');
    } else if (kind === 'item') {
      lines.push(`    item("${prefix}_" + ${v}, "minecraft:air") {`);
      lines.push(`        ${posExpr}`);
      lines.push(`        size = ${cw}`);
      lines.push('    }');
    } else if (kind === 'button') {
      lines.push(`    button("${prefix}_" + ${v}, "") {`);
      lines.push(`        ${posExpr}`);
      lines.push(`        size = [${cw}, ${ch}]`);
      lines.push('    }');
    } else {
      lines.push(`    rect("${prefix}_" + ${v}) {`);
      lines.push(`        ${posExpr}`);
      lines.push(`        size = [${cw}, ${ch}]`);
      lines.push(`        color = "#33FFFFFF"`);
      lines.push('    }');
    }
    lines.push('}');
    return lines.join('\n') + '\n';
  };

  // ============ инвентарь игрока (внутрь «создать GUI») ============
  Blockly.Blocks['spraute_gui_player_inventory'] = {
    init: function () {
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_INV',
        parts: [
          ...fieldText('WID', 'inv', 'инвентарь игрока в GUI, id'),
          ...fieldNum('X', 20, 'x', { min: -10000, max: 10000, precision: 1 }),
          ...fieldNum('Y', 80, 'y', { min: -10000, max: 10000, precision: 1 }),
        ],
      }], null);
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Виджет playerInventory внутри блока «создать GUI».');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_player_inventory'] = function (block) {
    const wid = (getSprauteBlockVal(block, SprauteGenerator, 'WID') || 'inv').replace(/[^a-zA-Z0-9_]/g, '_');
    const x = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'X')) || 0);
    const y = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'Y')) || 0);
    return `playerInventory("${wid}") {\n    pos = [${x}, ${y}]\n}\n`;
  };

  // ============ состояние UI ============
  Blockly.Blocks['spraute_gui_is_open'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'GUI открыто у');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Boolean');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiIsOpen(player) — открыт экранный UI или контейнер со слотами.');
    },
  };
  SprauteGenerator.forBlock['spraute_gui_is_open'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    return [`uiIsOpen(${player})`, 0];
  };

  Blockly.Blocks['spraute_gui_container_open'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'контейнер GUI у');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Boolean');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiContainerOpen(player) — открыт GUI с интерактивными слотами (uiOpen/overlayOpen со слотами).');
    },
  };
  SprauteGenerator.forBlock['spraute_gui_container_open'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    return [`uiContainerOpen(${player})`, 0];
  };

  // ============ слоты GUI-контейнера ============

  Blockly.Blocks['spraute_gui_slot_item'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'предмет в слоте GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: fieldText('SLOT', 'slot_0', 'слот id/№'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'String');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSlotItem(player, slotId|index) — ID предмета в кастомном слоте открытого GUI. Пусто → "".');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_slot_item'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    return [`uiSlotItem(${player}, ${slotExpr})`, 0];
  };

  Blockly.Blocks['spraute_gui_slot_empty'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'слот GUI пуст у');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: fieldText('SLOT', 'slot_0', 'слот'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Boolean');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSlotIsEmpty(player, slotId|index)');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_slot_empty'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    return [`uiSlotIsEmpty(${player}, ${slotExpr})`, 0];
  };

  Blockly.Blocks['spraute_gui_slot_stack_count'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'кол-во в слоте GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: fieldText('SLOT', 'slot_0', 'слот'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Number');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSlotCount(player, slotId|index) — размер стака в слоте.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_slot_stack_count'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    return [`uiSlotCount(${player}, ${slotExpr})`, 0];
  };

  Blockly.Blocks['spraute_gui_slot_slots'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'число слотов GUI у');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Number');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSlotSlots(player) — сколько кастомных слотов в открытом GUI-контейнере.');
    },
  };
  SprauteGenerator.forBlock['spraute_gui_slot_slots'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    return [`uiSlotSlots(${player})`, 0];
  };

  Blockly.Blocks['spraute_gui_slot_has'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'в слоте GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: [
          ...fieldText('SLOT', 'slot_0', 'слот'),
          ...fieldText('ITEM', 'minecraft:diamond', 'есть'),
        ],
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Boolean');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSlotHasItem(player, slot, itemId)');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_slot_has'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    const item = getSprauteBlockStr(block, SprauteGenerator, 'ITEM');
    return [`uiSlotHasItem(${player}, ${slotExpr}, ${item})`, 0];
  };

  Blockly.Blocks['spraute_gui_set_slot'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'положить в слот GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: [
          ...fieldText('SLOT', 'slot_0', 'слот'),
          ...fieldText('ITEM', 'minecraft:diamond', 'предмет'),
          ...fieldNum('COUNT', 1, '×', { min: 1, max: 64, precision: 1 }),
        ],
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSetSlot(player, slotId|index, itemId, count) — заполнить слот в открытом GUI.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_set_slot'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    const item = getSprauteBlockStr(block, SprauteGenerator, 'ITEM');
    const count = getSprauteBlockVal(block, SprauteGenerator, 'COUNT') || 1;
    return `uiSetSlot(${player}, ${slotExpr}, ${item}, ${count})\n`;
  };

  Blockly.Blocks['spraute_gui_clear_slot'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'очистить слот GUI');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SLOT',
        parts: fieldText('SLOT', 'slot_0', 'слот'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiClearSlot(player, slotId|index)');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_clear_slot'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const slotExpr = slotExprFromVal(getSprauteBlockVal(block, SprauteGenerator, 'SLOT') || '0');
    return `uiClearSlot(${player}, ${slotExpr})\n`;
  };

  // ============ поля ввода ============
  Blockly.Blocks['spraute_gui_get_input'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'текст поля ввода');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_IN',
        parts: fieldText('WIDGET_ID', 'search', 'id'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'String');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiGetInput(player, widgetId) — последний введённый текст (синхронизируется с клиента).');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_get_input'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    return [`uiGetInput(${player}, ${wid})`, 0];
  };

  Blockly.Blocks['spraute_gui_set_input'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'установить поле ввода');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_IN',
        parts: fieldText('WIDGET_ID', 'search', 'id'),
      }], 'VALUE');
      this.appendValueInput('VALUE').appendField('текст');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiSetInput(player, widgetId, text) — задать текст и обновить на клиенте.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_set_input'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const value = SprauteGenerator.valueToCode(block, 'VALUE', 0, '""') || '""';
    return `uiSetInput(${player}, ${wid}, ${value})\n`;
  };

  // ============ скролл ============
  Blockly.Blocks['spraute_gui_scroll_get'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'позиция скролла');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SCR',
        parts: fieldText('WIDGET_ID', 'list', 'id scroll'),
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setOutput(true, 'Number');
      this.setColour(GUI_COLOR);
      this.setTooltip('uiScrollGet(player, scrollId) — смещение прокрутки в px (обновляется при скролле колёсиком).');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_scroll_get'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    return [`uiScrollGet(${player}, ${wid})`, 0];
  };

  Blockly.Blocks['spraute_gui_scroll_set'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'прокрутить scroll');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_SCR',
        parts: [
          ...fieldText('WIDGET_ID', 'list', 'id'),
          ...fieldNum('OFFSET', 0, 'offset px', { min: 0, max: 100000, precision: 1 }),
        ],
      }], 'TAIL');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiScrollSet(player, scrollId, offsetPx) — мгновенно прокрутить.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_scroll_set'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const off = getSprauteBlockVal(block, SprauteGenerator, 'OFFSET') || 0;
    return `uiScrollSet(${player}, ${wid}, ${off})\n`;
  };

  Blockly.Blocks['spraute_gui_scroll_animate'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'анимировать скролл');
      guiToggles(this, SprauteGenerator, [
        {
          input: 'ROW_SCR1',
          parts: [
            ...fieldText('WIDGET_ID', 'list', 'id'),
            ...fieldNum('TARGET', 100, 'до px', { min: 0, max: 100000, precision: 1 }),
            ...fieldNum('DURATION', 0.5, 'за', { min: 0, max: 600, precision: 0.1 }),
            { type: 'label', text: 'сек' },
          ],
        },
      ], 'TAIL');
      this.appendDummyInput('TAIL')
        .appendField('easing')
        .appendField(new Blockly.FieldDropdown([
          ['линейно', 'linear'],
          ['ease in', 'ease_in'],
          ['ease out', 'ease_out'],
          ['ease in-out', 'ease_in_out'],
        ]), 'EASING');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiAnimate(player, scrollId, "scrollOffset", target, duration, easing)');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_scroll_animate'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const target = getSprauteBlockStr(block, SprauteGenerator, 'TARGET');
    const dur = getSprauteBlockVal(block, SprauteGenerator, 'DURATION') || 0.5;
    const easing = block.getFieldValue('EASING') || 'linear';
    return `uiAnimate(${player}, ${wid}, "scrollOffset", ${target}, ${dur}, "${easing}")\n`;
  };

  // ============ быстрое обновление виджета ============
  Blockly.Blocks['spraute_gui_update_quick'] = {
    init: function () {
      addWhoRow(this, 'WHO', 'изменить виджет');
      guiToggles(this, SprauteGenerator, [{
        input: 'ROW_Q',
        parts: fieldText('WIDGET_ID', 'label', 'id'),
      }], 'FIELD_ROW');
      this.appendDummyInput('FIELD_ROW')
        .appendField('поле')
        .appendField(new Blockly.FieldDropdown([
          ['текст', 'text'],
          ['цвет', 'color'],
          ['масштаб', 'scale'],
          ['подпись кнопки', 'label'],
          ['hover цвет', 'hover'],
          ['текстура', 'texture'],
          ['x', 'x'],
          ['y', 'y'],
          ['ширина', 'w'],
          ['высота', 'h'],
          ['прозрачность', 'alpha'],
          ['подсказка', 'tooltip'],
        ]), 'FIELD');
      this.appendValueInput('VALUE').appendField('значение');
      this.appendDummyInput('TAIL');
      setupWhoShape(this, 'WHO', 'PLAYER');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('uiUpdate с выбором частого поля из списка.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_update_quick'] = function (block) {
    const player = playerCodeFor(block, SprauteGenerator, 'WHO', 'PLAYER');
    const wid = getSprauteBlockStr(block, SprauteGenerator, 'WIDGET_ID');
    const field = block.getFieldValue('FIELD') || 'text';
    const value = SprauteGenerator.valueToCode(block, 'VALUE', 0, '""') || '""';
    return `uiUpdate(${player}, ${wid}, "${field}", ${value})\n`;
  };

  // ============ программный виджет (внутрь create GUI) ============
  Blockly.Blocks['spraute_gui_add_widget'] = {
    init: function () {
      this.appendDummyInput('HEAD')
        .appendField('добавить виджет')
        .appendField(new Blockly.FieldDropdown([
          ['текст', 'text'],
          ['кнопка', 'button'],
          ['прямоугольник', 'rect'],
          ['изображение', 'image'],
          ['слот', 'slot'],
          ['поле ввода', 'input'],
          ['scroll', 'scroll'],
          ['clip', 'clip'],
        ]), 'KIND');
      guiToggles(this, SprauteGenerator, [
        {
          input: 'ROW_W1',
          parts: fieldText('WID', 'w1', 'id'),
        },
        {
          input: 'ROW_W2',
          parts: [
            ...fieldNum('X', 10, 'x', { min: -10000, max: 10000, precision: 1 }),
            ...fieldNum('Y', 10, 'y', { min: -10000, max: 10000, precision: 1 }),
            ...fieldNum('W', 80, 'w', { min: 1, max: 10000, precision: 1 }),
            ...fieldNum('H', 20, 'h', { min: 1, max: 10000, precision: 1 }),
          ],
        },
        {
          input: 'ROW_W3',
          parts: [
            ...fieldText('ARG1', '', 'строка1'),
            ...fieldText('ARG2', '', 'строка2'),
          ],
        },
      ], null);
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Один виджет внутри «создать GUI». Для text/button — ARG1=текст/подпись; image — ARG1=текстура; scroll — H=contentH.');
      finishSprauteToggleBlock(this);
    },
  };
  SprauteGenerator.forBlock['spraute_gui_add_widget'] = function (block) {
    const kind = block.getFieldValue('KIND') || 'text';
    const wid = (getSprauteBlockVal(block, SprauteGenerator, 'WID') || 'w1').replace(/[^a-zA-Z0-9_]/g, '_');
    const x = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'X')) || 0);
    const y = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'Y')) || 0);
    const w = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'W')) || 80);
    const h = Math.round(Number(getSprauteBlockVal(block, SprauteGenerator, 'H')) || 20);
    const a1 = (getSprauteBlockVal(block, SprauteGenerator, 'ARG1') || '').replace(/"/g, '\\"');
    const a2 = (getSprauteBlockVal(block, SprauteGenerator, 'ARG2') || '').replace(/"/g, '\\"');
    const lines = [];
    if (kind === 'text') {
      lines.push(`text("${wid}", "${a1 || 'Текст'}") {`, `    pos = [${x}, ${y}]`, `    scale = 1`, `}`);
    } else if (kind === 'button') {
      lines.push(`button("${wid}", "${a1 || 'OK'}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `}`);
    } else if (kind === 'rect') {
      lines.push(`rect("${wid}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `    color = "${a1 || '#55FFFFFF'}"`, `}`);
    } else if (kind === 'image') {
      lines.push(`image("${wid}", "${a1 || 'minecraft:textures/gui/icons.png'}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `}`);
    } else if (kind === 'slot') {
      lines.push(`slot("${wid}") {`, `    pos = [${x}, ${y}]`, `}`);
    } else if (kind === 'input') {
      lines.push(`input("${wid}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `    placeholder = "${a1}"`, `}`);
    } else if (kind === 'scroll') {
      lines.push(`scroll("${wid}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `    contentH = ${Math.max(h, w)}`, `}`, ...(a2 ? [] : []));
    } else if (kind === 'clip') {
      lines.push(`clip("${wid}") {`, `    pos = [${x}, ${y}]`, `    size = [${w}, ${h}]`, `}`);
    }
    return lines.join('\n') + '\n';
  };
}

/** Записать модель GUI в блок «создать GUI» (вызывается из редактора). */
export function applyGuiModelToBlock(block, model) {
  if (!block || block.type !== 'spraute_gui_create') return;
  try {
    block._guiJson = JSON.stringify(model);
    if (model.name) {
      block[`val_GUI_NAME`] = model.name;
      try { if (block.getField('GUI_NAME')) block.setFieldValue(model.name, 'GUI_NAME'); } catch (e) {}
    }
    if (model.openMode) block.setFieldValue(model.openMode, 'OPEN_MODE');
    refreshGuiModeDependents(block.workspace, model.name);
  } catch (e) {
    console.warn('[GUI] applyGuiModelToBlock:', e);
  }
}

export function readGuiModelFromBlock(block) {
  if (!block || block.type !== 'spraute_gui_create') return null;
  try {
    const raw = block._guiJson;
    const name = getSprauteBlockVal(block, null, 'GUI_NAME') || 'my_ui';
    const model = raw && raw.trim() && raw.trim() !== '{}' ? normalizeGuiModel(JSON.parse(raw)) : normalizeGuiModel({ name });
    model.name = name;
    model.openMode = block.getFieldValue('OPEN_MODE') === 'overlay' ? 'overlay' : 'gui';
    return model;
  } catch (e) {
    return normalizeGuiModel({ name: getSprauteBlockVal(block, null, 'GUI_NAME') || 'my_ui' });
  }
}
