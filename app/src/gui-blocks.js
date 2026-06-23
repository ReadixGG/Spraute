import * as Blockly from 'blockly';
import { guiModelToSprCode, normalizeGuiModel, createEmptyGuiModel } from './gui-codegen.js';

const GUI_COLOR = '#14b8a6';

const EDIT_SVG = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="%2338bdf8" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>'
);

function parseGuiJson(block) {
  try {
    const raw = block.getFieldValue('GUI_JSON');
    if (!raw || !String(raw).trim()) return null;
    return normalizeGuiModel(JSON.parse(raw));
  } catch {
    return null;
  }
}

function setGuiJson(block, model) {
  block.setFieldValue(JSON.stringify(model), 'GUI_JSON');
}

export function getGuiDataFromBlock(block) {
  return parseGuiJson(block);
}

export function getGuiSprvPathFromBlock(block) {
  return block.getFieldValue('GUI_PATH') || '';
}

export function registerGuiBlocks(SprauteGenerator) {
  Blockly.Blocks['spraute_gui_create'] = {
    init: function () {
      this.appendDummyInput()
        .appendField('создать GUI')
        .appendField(new Blockly.FieldTextInput('my_ui'), 'GUI_NAME');
      this.appendDummyInput()
        .appendField('режим открытия')
        .appendField(new Blockly.FieldDropdown([
          ['окно (uiOpen)', 'gui'],
          ['наложение (overlayOpen)', 'overlay'],
        ]), 'OPEN_MODE');
      this.appendDummyInput('EDIT_ROW')
        .appendField(new Blockly.FieldImage(EDIT_SVG, 24, 24, 'Редактировать', () => {
          if (typeof window.__sprauteOpenGuiEditor === 'function') {
            window.__sprauteOpenGuiEditor(this.getSourceBlock());
          }
        }));
      this.appendDummyInput()
        .setAlign(Blockly.inputs.Align.RIGHT)
        .appendField('файл:')
        .appendField(new Blockly.FieldLabelSerializable('—'), 'GUI_PATH_LABEL');
      this.appendDummyInput()
        .appendField(new Blockly.FieldTextInput(''), 'GUI_PATH');
      this.appendDummyInput()
        .appendField(new Blockly.FieldTextInput('{}'), 'GUI_JSON');
      this.getField('GUI_PATH').setVisible(false);
      this.getField('GUI_JSON').setVisible(false);
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setTooltip('Визуальный редактор GUI. Данные хранятся в .sprv, код — create ui { ... }');
      this.setOnChange(function (e) {
        if (e.type === Blockly.Events.BLOCK_CREATE || e.type === Blockly.Events.BLOCK_CHANGE) {
          if (e.blockId === this.id && e.name === 'GUI_NAME') {
            const name = this.getFieldValue('GUI_NAME') || 'my_ui';
            const data = parseGuiJson(this) || createEmptyGuiModel(name);
            data.name = name;
            data.openMode = this.getFieldValue('OPEN_MODE') || 'gui';
            setGuiJson(this, data);
          }
          this.updatePathLabel_();
        }
      });
    },
    updatePathLabel_() {
      const p = this.getFieldValue('GUI_PATH');
      const lbl = this.getField('GUI_PATH_LABEL');
      if (lbl) lbl.setValue(p ? p.split('/').pop() : '—');
    },
    mutationToDom: function () {
      const el = Blockly.utils.xml.createElement('mutation');
      el.setAttribute('path', this.getFieldValue('GUI_PATH') || '');
      el.setAttribute('json', this.getFieldValue('GUI_JSON') || '');
      return el;
    },
    domToMutation: function (xml) {
      if (xml.getAttribute('path')) this.setFieldValue(xml.getAttribute('path'), 'GUI_PATH');
      if (xml.getAttribute('json')) this.setFieldValue(xml.getAttribute('json'), 'GUI_JSON');
      this.updatePathLabel_();
    },
    saveExtraState: function () {
      return {
        path: this.getFieldValue('GUI_PATH') || '',
        json: this.getFieldValue('GUI_JSON') || '',
      };
    },
    loadExtraState: function (state) {
      if (state?.path) this.setFieldValue(state.path, 'GUI_PATH');
      if (state?.json) this.setFieldValue(state.json, 'GUI_JSON');
      this.updatePathLabel_();
    },
  };

  SprauteGenerator.forBlock['spraute_gui_create'] = function (block) {
    let data = parseGuiJson(block);
    if (!data) {
      const name = block.getFieldValue('GUI_NAME') || 'my_ui';
      return `// GUI "${name}": нажмите «Редактировать» и «Добавить GUI в проект»\n`;
    }
    data.name = block.getFieldValue('GUI_NAME') || data.name;
    data.openMode = block.getFieldValue('OPEN_MODE') || data.openMode;
    return guiModelToSprCode(data) + '\n';
  };

  Blockly.Blocks['spraute_gui_open'] = {
    init: function () {
      this.appendDummyInput()
        .appendField('открыть GUI');
      this.appendValueInput('PLAYER')
        .setCheck(null)
        .appendField('игроку');
      this.appendDummyInput()
        .appendField('шаблон')
        .appendField(new Blockly.FieldTextInput('my_ui'), 'GUI_NAME');
      this.appendDummyInput()
        .appendField('как')
        .appendField(new Blockly.FieldDropdown([
          ['окно (uiOpen)', 'uiOpen'],
          ['наложение (overlayOpen)', 'overlayOpen'],
        ]), 'OPEN_FN');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setInputsInline(true);
    },
  };

  SprauteGenerator.forBlock['spraute_gui_open'] = function (block) {
    const fn = block.getFieldValue('OPEN_FN') || 'uiOpen';
    const name = block.getFieldValue('GUI_NAME') || 'my_ui';
    const player = SprauteGenerator.valueToCode(block, 'PLAYER', 0) || 'player';
    return `${fn}(${player.trim()}, ${name})\n`;
  };

  Blockly.Blocks['spraute_gui_close'] = {
    init: function () {
      this.appendDummyInput()
        .appendField('закрыть')
        .appendField(new Blockly.FieldDropdown([
          ['окно (uiClose)', 'uiClose'],
          ['наложение (overlayClose)', 'overlayClose'],
        ]), 'CLOSE_FN');
      this.appendValueInput('PLAYER')
        .setCheck(null)
        .appendField('у игрока');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(GUI_COLOR);
      this.setInputsInline(true);
    },
  };

  SprauteGenerator.forBlock['spraute_gui_close'] = function (block) {
    const fn = block.getFieldValue('CLOSE_FN') || 'uiClose';
    const player = SprauteGenerator.valueToCode(block, 'PLAYER', 0) || 'player';
    return `${fn}(${player.trim()})\n`;
  };
}

export function applyGuiDataToBlock(block, model, sprvPath) {
  if (!block || block.type !== 'spraute_gui_create') return;
  const data = normalizeGuiModel(model);
  block.setFieldValue(data.name, 'GUI_NAME');
  block.setFieldValue(data.openMode === 'overlay' ? 'overlay' : 'gui', 'OPEN_MODE');
  block.setFieldValue(JSON.stringify(data), 'GUI_JSON');
  if (sprvPath) block.setFieldValue(sprvPath, 'GUI_PATH');
  if (typeof block.updatePathLabel_ === 'function') block.updatePathLabel_();
}

export function guiDirForScript(sprRelPath) {
  const p = String(sprRelPath || 'scripts/main.spr').replace(/\\/g, '/');
  const i = p.lastIndexOf('/');
  const folder = i >= 0 ? p.slice(0, i) : 'scripts';
  return `${folder}/gui`;
}

export function guiSprvPathFor(sprRelPath, guiName) {
  const safe = String(guiName || 'my_ui').replace(/[^a-zA-Z0-9_]/g, '_');
  return `${guiDirForScript(sprRelPath)}/${safe}.sprv`;
}
