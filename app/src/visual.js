import * as Blockly from 'blockly/core';
import 'blockly/blocks';

// ================= ГЕНЕРАТОР КОДА =================
export const SprauteGenerator = new Blockly.Generator('Spraute');

SprauteGenerator.scrub_ = function(block, code, opt_thisOnly) {
  const nextBlock = block.nextConnection && block.nextConnection.targetBlock();
  const nextCode = opt_thisOnly ? '' : SprauteGenerator.blockToCode(nextBlock);
  return code + nextCode;
};

const COLORS = { SYSTEM: '#a855f7' };

Blockly.Blocks['spraute_raw_code'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Выполнить код:")
        .appendField(new Blockly.FieldTextInput("say(player, \"Привет\")"), "CODE");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.SYSTEM);
  }
};
SprauteGenerator.forBlock['spraute_raw_code'] = function(block) {
  return `${block.getFieldValue('CODE')}\n`;
};

// ================= ДИНАМИЧЕСКИЕ ДАННЫЕ =================
export let currentNpcs = [];
export let currentAnimations = [];

export function updateDynamicLists(npcs, anims) {
  if (npcs && npcs.length > 0) currentNpcs = npcs.map(n => [n, n]);
  if (anims && anims.length > 0) currentAnimations = anims.map(a => [a, a]);
}

function getNpcsDropdown() {
  const filtered = currentNpcs.filter(n => n[1] !== '_event_npc').map(n => ["НИП: " + n[0], n[1]]);
  return filtered.length > 0 ? filtered : [["НИП: npc", "npc"]];
}

// ================= ПАРСЕР #\ БЛОКОВ =================
export let customCategories = {};
export let customParsers = [];

export function clearCustomCategories() {
  customCategories = {};
  customParsers = [];
}

/**
 * Парсит текст с #\ комментариями и регистрирует блоки в Blockly.
 * @param {string} text — содержимое .spr файла
 * @param {string} pluginNamespace — префикс плагина, например "myplugin"
 */
export function parseCustomBlocks(text, pluginNamespace = "", isPreview = false) {
  if (!text || !text.trim()) return;

  // Разбиваем на блоки по маркеру "block:"
  const blockChunks = text.split(/(?:^|\n)#\\?\s*block:\s*/);
  
  for (let i = 1; i < blockChunks.length; i++) {
    const chunk = "block: " + blockChunks[i];
    try {
      _registerBlockFromChunk(chunk, pluginNamespace, isPreview);
    } catch(err) {
      console.error("[BlockParser] Failed to parse block chunk:", err, "\nChunk:", chunk.substring(0, 200));
    }
  }
}

function _parseLine(rawLine) {
  // Убираем #\ prefix если есть
  let m = rawLine.match(/^#\\?\s?(.*)/);
  return m ? m[1] : rawLine;
}

function _registerBlockFromChunk(chunk, namespace, isPreview) {
  const allLines = chunk.split('\n');
  const lines = allLines.map(_parseLine);

  let rawId = "", category = "Custom", color = "#555555", shape = "statement";
  let bodyLines = [];
  
  let uiLines = [], dynamicUiLines = [], codeGenLines = [], codeParseLines = [];
  let section = "META";
  let isLegacy = false;

  for (let raw of lines) {
    const trimmed = raw.trim();
    if (trimmed.startsWith('//') || trimmed === '') continue;

    if (trimmed === '[UI]')          { section = 'UI'; isLegacy = true; continue; }
    if (trimmed === '[DYNAMIC_UI]')  { section = 'DYNAMIC_UI'; isLegacy = true; continue; }
    if (trimmed === '[CODE_GEN]')    { section = 'CODE_GEN'; isLegacy = true; continue; }
    if (trimmed === '[CODE_PARSE]')  { section = 'CODE_PARSE'; isLegacy = true; continue; }

    if (section === 'META') {
      if (trimmed.startsWith('block:'))    { rawId    = trimmed.slice(6).trim(); continue; }
      else if (trimmed.startsWith('category:')) { category = trimmed.slice(9).trim(); continue; }
      else if (trimmed.startsWith('color:'))    { color    = trimmed.slice(6).trim(); continue; }
      else if (trimmed.startsWith('shape:'))    { shape    = trimmed.slice(6).trim(); continue; }
      else {
        section = 'BODY';
      }
    }

    if (isLegacy) {
      switch (section) {
        case 'UI':           uiLines.push(raw); break;
        case 'DYNAMIC_UI':   dynamicUiLines.push(raw); break;
        case 'CODE_GEN':     codeGenLines.push(raw); break;
        case 'CODE_PARSE':   codeParseLines.push(raw); break;
      }
    } else if (section === 'BODY') {
      bodyLines.push(raw);
    }
  }

  if (!rawId) return;
  const fullId = namespace ? `${namespace}.${rawId}` : rawId;

  if (!isPreview) {
    if (!customCategories[category]) customCategories[category] = { color, blocks: [] };
    if (!customCategories[category].blocks.includes(fullId)) {
      customCategories[category].blocks.push(fullId);
    }
  }

  function compileUiLine(lineStr, inputName) {
    const line = lineStr.trim();
    let js = "";
    if (line.startsWith('row:')) {
      const rowStr = line.slice(4).trim();
      js += `self.slotToggles_ = self.slotToggles_ || {};\n`;
      js += `var row = self.appendDummyInput(${JSON.stringify(inputName)});\n`;
      js += `self.dynamicInputNames_.push(${JSON.stringify(inputName)});\n`;
      
      const realTokens = rowStr.match(/"[^"]*"|\[[^\]]+\]|\([^)]+\)/g) || [];
      for (const tok of realTokens) {
        if (tok.startsWith('"')) {
          js += `row.appendField(${tok});\n`;
        } else if (tok.startsWith('[')) {
          const inner = tok.slice(1, -1);
          const colonIdx = inner.indexOf(':');
          const name = inner.slice(0, colonIdx).trim();
          const typeDef = inner.slice(colonIdx + 1).trim();
          const contName = inputName + '_cont_' + name;
          
          js += `var toggleIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNSIgaGVpZ2h0PSIxNSIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgOWwtMyAzIDMgM200LTZsMyAzLTMgMyIvPjwvc3ZnPg==";\n`;
          js += `if (self.slotToggles_[${JSON.stringify(name)}]) {\n`;
          js += `  row = self.appendValueInput(${JSON.stringify(name)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(name)});\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=false; self.updateShape_(); }));\n`;
          js += `  row = self.appendDummyInput(${JSON.stringify(contName)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(contName)});\n`;
          js += `} else {\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=true; self.updateShape_(); }));\n`;
          if (typeDef === 'dropdown_npc') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getNpcsDropdown(); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else {
            const optsMatch = typeDef.match(/^dropdown\((.*)\)$/s);
            if (optsMatch) {
              const optsArr = optsMatch[1].split(',').map(o => {
                const parts = o.split(':').map(s => s.trim());
                return [parts[0], parts.length > 1 ? parts[1] : parts[0]];
              });
              js += `  row.appendField(new Blockly.FieldDropdown(${JSON.stringify(optsArr)}, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
            }
          }
          js += `}\n`;
        } else if (tok.startsWith('(')) {
          const inner = tok.slice(1, -1);
          const parts = inner.split(':').map(s => s.trim());
          const name = parts[0], def = parts[2] || "";
          const contName = inputName + '_cont_' + name;
          
          js += `var toggleIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNSIgaGVpZ2h0PSIxNSIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgOWwtMyAzIDMgM200LTZsMyAzLTMgMyIvPjwvc3ZnPg==";\n`;
          js += `if (self.slotToggles_[${JSON.stringify(name)}]) {\n`;
          js += `  row = self.appendValueInput(${JSON.stringify(name)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(name)});\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=false; self.updateShape_(); }));\n`;
          js += `  row = self.appendDummyInput(${JSON.stringify(contName)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(contName)});\n`;
          js += `} else {\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=true; self.updateShape_(); }));\n`;
          js += `  row.appendField(new Blockly.FieldTextInput(self[${JSON.stringify("val_"+name)}] || ${JSON.stringify(def)}), ${JSON.stringify(name)});\n`;
          js += `}\n`;
        }
      }
    } else if (line.startsWith('input:')) {
      const m = line.match(/^input:\s*(\w+)\s*\(type:\s*(\w+)\)(?:\s+"([^"]+)")?/);
      if (m) {
        const [, iname, itype, label=""] = m;
        if (itype === 'value') {
          js += `var inp = self.appendValueInput(${JSON.stringify(iname)});\n`;
        } else {
          js += `var inp = self.appendStatementInput(${JSON.stringify(iname)});\n`;
        }
        if (label) js += `inp.appendField(${JSON.stringify(label)});\n`;
        js += `self.dynamicInputNames_.push(${JSON.stringify(iname)});\n`;
      }
    }
    return js;
  }

  let dynUiJs = "";
  let codeGenJs = "";
  let extractedTemplates = [];
  let conditionVars = new Set();

  if (isLegacy) {
    let dynCounter = 0;
    for (const raw of uiLines) {
      if (raw.trim()) dynUiJs += compileUiLine(raw.trim(), `DYN_${dynCounter++}`);
    }
    const indentStack = [];
    for (const raw of dynamicUiLines) {
      if (!raw.trim()) continue;
      const indent = raw.match(/^\s*/)[0].length;
      while (indentStack.length > 0 && indentStack[indentStack.length-1] >= indent) {
        dynUiJs += "}\n";
        indentStack.pop();
      }
      const line = raw.trim();
      if (line.startsWith('if ') && line.endsWith(':')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/"[^"]*"|'[^']*'|\b([a-zA-Z_]\w*)\b/g, (m, g1) => {
          if (!g1) return m;
          if (['true','false','null','undefined'].includes(g1)) return g1;
          conditionVars.add(g1);
          return `(self[${JSON.stringify("val_"+g1)}] || self.getFieldValue(${JSON.stringify(g1)}))`;
        });
        dynUiJs += `if (${cond}) {\n`;
        indentStack.push(indent);
      } else {
        dynUiJs += compileUiLine(line, `DYN_${dynCounter++}`);
      }
    }
    while (indentStack.length > 0) { dynUiJs += "}\n"; indentStack.pop(); }
    codeGenJs = codeGenLines.map(l => _parseLine(l)).join('\n');
    
    if (codeParseLines.length > 0) {
      const pCode = codeParseLines.map(l => _parseLine(l)).join('\n');
      if (pCode.trim().startsWith('"') || pCode.trim().startsWith('`')) {
        extractedTemplates.push(pCode.trim().slice(1, -1));
      } else {
        if (!isPreview) {
          try {
            customParsers.push({ id: fullId, plugin: namespace, fn: new Function('text', pCode) });
          } catch(e) {}
        }
      }
    }
  } else {
    const dynIndentStack = [];
    const codeIndentStack = [];
    let dynCounter = 0;

    for (const raw of bodyLines) {
      if (!raw.trim()) continue;
      const indent = raw.match(/^\s*/)[0].length;
      const line = raw.trim();
      
      while (dynIndentStack.length > 0 && dynIndentStack[dynIndentStack.length-1] >= indent) {
        dynUiJs += "}\n"; dynIndentStack.pop();
      }
      while (codeIndentStack.length > 0 && codeIndentStack[codeIndentStack.length-1] >= indent) {
        codeGenJs += "}\n"; codeIndentStack.pop();
      }

      if (line.startsWith('if ') && line.endsWith(':')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/\bor\b/g, '||').replace(/\band\b/g, '&&').replace(/\bnot\b/g, '!');
        
        // Temporarily extract string literals
        let strLits = [];
        let condNoStr = cond.replace(/"[^"]*"|'[^']*'/g, (m) => {
            strLits.push(m);
            return `__STR${strLits.length-1}__`;
        });

        let uiCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
          if (['true','false','null','undefined'].includes(m) || m.startsWith('__STR')) return m;
          conditionVars.add(m);
          return `(self[${JSON.stringify("val_"+m)}] || self.getFieldValue(${JSON.stringify(m)}))`;
        });
        
        let codeCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
          if (['true','false','null','undefined'].includes(m) || m.startsWith('__STR')) return m;
          return `_getVal(${JSON.stringify(m)})`;
        });
        
        // Put strings back
        let uiCond = uiCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
        let codeCond = codeCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
        
        dynUiJs += `if (${uiCond}) {\n`;
        dynIndentStack.push(indent);
        
        codeGenJs += `if (${codeCond}) {\n`;
        codeIndentStack.push(indent);
        
      } else if (line.startsWith('template:')) {
        let tmpl = line.slice(9).trim();
        let actualTmpl = tmpl.replace(/\\n/g, '\n').replace(/\\t/g, '\t');
        extractedTemplates.push(actualTmpl);
        let jsTmpl = actualTmpl.replace(/{([a-zA-Z0-9_]+)}/g, (match, v) => {
            return `\${_getVal(${JSON.stringify(v)})}`;
        });
        codeGenJs += `return \`${jsTmpl}\\n\`;\n`;
      } else if (line.startsWith('code:')) {
        codeGenJs += line.slice(5).trim() + "\n";
      } else if (line.startsWith('row:') || line.startsWith('input:')) {
        dynUiJs += compileUiLine(line, `DYN_${dynCounter++}`);
      }
    }
    while (dynIndentStack.length > 0) { dynUiJs += "}\n"; dynIndentStack.pop(); }
    while (codeIndentStack.length > 0) { codeGenJs += "}\n"; codeIndentStack.pop(); }
  }

  const hasDyn = dynUiJs.trim().length > 0;
  const condVarsArray = Array.from(conditionVars);

  Blockly.Blocks[fullId] = {
    init: function() {
      const self = this;
      this.dynamicInputNames_ = [];
      this.trackedFields_ = [];
      this.conditionVars_ = condVarsArray;
      
      this.setColour(color);
      this.setInputsInline(true);
      if (shape === 'value') {
        this.setOutput(true, null);
      } else {
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
      }

      try {
        const initFn = new Function('Blockly', 'getNpcsDropdown', 'self', staticUiJs || "");
        initFn(Blockly, getNpcsDropdown, self);
      } catch(e) { console.error(`[Block ${fullId}] Init UI error:`, e); }

      if (shape === 'wrapper') {
        this.appendStatementInput("DO");
      }

      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (field.name) {
            this.trackedFields_.push(field.name);
            this[`val_${field.name}`] = field.getValue();
          }
        }
      }
      if (hasDyn) this.updateShape_();
    },

    validateField: function(name, newValue) {
      const old = this[`val_${name}`];
      this[`val_${name}`] = newValue;
      if (hasDyn && old !== newValue && this.conditionVars_.includes(name)) {
        const self = this;
        setTimeout(() => { if (self.workspace) self.updateShape_(); }, 0);
      }
      return newValue;
    },

    mutationToDom: function() {
      const container = document.createElement('mutation');
      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (field.name) {
             const val = this[`val_${field.name}`] || field.getValue();
             if (val != null) container.setAttribute(`f_${field.name}`, val);
          }
        }
      }
      if (this.slotToggles_) {
        for (const key in this.slotToggles_) {
           if (this.slotToggles_[key]) {
             container.setAttribute(`t_${key}`, "true");
           }
        }
      }
      return container;
    },

    domToMutation: function(xml) {
      this.slotToggles_ = {};
      for (const attr of xml.attributes) {
        if (attr.name.startsWith('f_')) {
          this[`val_${attr.name.slice(2)}`] = attr.value;
        } else if (attr.name.startsWith('t_')) {
          this.slotToggles_[attr.name.slice(2)] = (attr.value === "true");
        }
      }
      if (hasDyn) this.updateShape_();
    },

    onchange: function(e) {
      if (e.blockId !== this.id) return;
      if (e.type === Blockly.Events.BLOCK_CHANGE && e.name) {
        this[`val_${e.name}`] = e.newValue;
      }
    },

    updateShape_: function() {
      if (!dynUiJs) return;
      const toRemove = [...this.dynamicInputNames_];
      for (const n of toRemove) {
        if (this.getInput(n)) this.removeInput(n);
      }
      this.dynamicInputNames_ = [];
      const self = this;
      try {
        const dynFn = new Function('Blockly', 'getNpcsDropdown', 'self', dynUiJs);
        dynFn(Blockly, getNpcsDropdown, self);
      } catch(e) { console.error(`[Block ${fullId}] Dynamic UI error:`, e); }

      if (shape === 'wrapper' && self.getInput("DO")) {
         var doConn = self.getInput("DO").connection.targetConnection;
         self.removeInput("DO", true);
         var doInp = self.appendStatementInput("DO");
         if (doConn) doInp.connection.connect(doConn);
      }
    }
  };

  SprauteGenerator.forBlock[fullId] = function(block) {
    function _getVal(name) {
      if (block.getInput(name) && block.getInput(name).type === 3) {
        return SprauteGenerator.statementToCode(block, name) || "";
      }
      let target = block.getInputTargetBlock(name);
      if (target) {
        let gen = SprauteGenerator.blockToCode(target);
        if (Array.isArray(gen)) return gen[0] || "";
        return gen || "";
      }
      let v = block.getFieldValue(name);
      if (v === null || v === undefined) v = block[`val_${name}`];
      return v === null || v === undefined ? "" : v;
    }

    let ctx = "";
    if (isLegacy) {
        const allFields = [];
        for (const input of block.inputList) {
          for (const field of input.fieldRow) {
            if (field.name) allFields.push(field.name);
          }
        }
        for (const f of allFields) {
          const v = block.getFieldValue(f);
          ctx += `var ${f} = ${JSON.stringify(v === null ? "" : v)};\n`;
        }
        for (const input of block.inputList) {
          if (input.name && !allFields.includes(input.name)) {
            let gen = "";
            if (input.type === 3) {
              gen = SprauteGenerator.statementToCode(block, input.name);
            } else {
              const target = block.getInputTargetBlock(input.name);
              gen = target ? SprauteGenerator.blockToCode(target) : "";
              if (Array.isArray(gen)) gen = gen[0];
            }
            ctx += `var ${input.name} = ${JSON.stringify(gen || "")};\n`;
          }
        }
    }

    let finalCodeGen = codeGenJs;
    if (!finalCodeGen && extractedTemplates.length === 1 && !isLegacy && !hasDyn) {
        finalCodeGen = `return \`${extractedTemplates[0].replace(/{([a-zA-Z0-9_]+)}/g, (m, v) => `\${_getVal(${JSON.stringify(v)})}`)}\\n\`;\n`;
    }

    const fullCode = (isLegacy ? ctx : "") + finalCodeGen;
    try {
      const fn = new Function('_getVal', 'block', 'SprauteGenerator', fullCode);
      const res = fn(_getVal, block, SprauteGenerator);
      if (shape === 'value') return [res ?? "", 0];
      return res ?? "";
    } catch(e) {
      return `/* CodeGen error in ${fullId}: ${e.message} */\n`;
    }
  };

  if (!isPreview && extractedTemplates.length > 0) {
    for (const tmpl of extractedTemplates) {
      let regexStr = tmpl.replace(/[.*+?^$()|[\]\\]/g, '\\$&');
      const vars = [];
      regexStr = regexStr.replace(/{([a-zA-Z0-9_]+)}/g, (match, varName) => {
        vars.push(varName);
        return '([\\s\\S]*?)';
      });
      regexStr = regexStr.replace(/\s+/g, '\\s*');
      regexStr = `^\\s*${regexStr}`;
      
      const pCode = `
        const m = text.match(/${regexStr}/);
        if (m) {
          const fields = {};
          const statements = {};
          const varNames = ${JSON.stringify(vars)};
          for (let i = 0; i < varNames.length; i++) {
            const v = varNames[i];
            const val = m[i+1].trim();
            if (v === v.toUpperCase() && v.length > 1) {
              statements[v] = val;
            } else {
              fields[v] = val;
            }
          }
          return { length: m[0].length, fields, statements };
        }
        return null;
      `;
      try {
        customParsers.push({ id: fullId, plugin: namespace, fn: new Function('text', pCode) });
      } catch(e) { console.error(`[Block ${fullId}] CodeParse error:`, e); }
    }
  }
}

// ================= ТЕМА =================
export const SprauteTheme = Blockly.Theme.defineTheme('spraute_dark', {
  base: Blockly.Themes.Dark,
  blockStyles: {},
  categoryStyles: {},
  componentStyles: {
    workspaceBackgroundColour: 'transparent',
    toolboxBackgroundColour: 'var(--color-surface, #0b1a2f)',
    toolboxForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutBackgroundColour: 'var(--color-bg, #040e1f)',
    flyoutForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutOpacity: 0.95,
    scrollbarColour: 'var(--color-primary, #38bdf8)',
    insertionMarkerColour: '#fff',
    insertionMarkerOpacity: 0.3,
    scrollbarOpacity: 0.4,
    cursorColour: 'var(--color-primary, #d0d0d0)',
    blackBackground: 'var(--color-bg, #040e1f)'
  }
});

export function applyBlocklyThemeColors(colors) {
  if (!Blockly.getMainWorkspace()) return;
  const theme = Blockly.Theme.defineTheme('spraute_dynamic', {
    base: Blockly.Themes.Dark,
    blockStyles: {}, categoryStyles: {},
    componentStyles: {
      workspaceBackgroundColour: 'transparent',
      toolboxBackgroundColour: colors.surface || '#0b1a2f',
      toolboxForegroundColour: colors.text || '#ffffff',
      flyoutBackgroundColour: colors.bg || '#040e1f',
      flyoutForegroundColour: colors.text || '#ffffff',
      flyoutOpacity: 0.95,
      scrollbarColour: colors.primary || '#38bdf8',
      insertionMarkerColour: '#fff',
      insertionMarkerOpacity: 0.3,
      scrollbarOpacity: 0.4,
      cursorColour: colors.primary || '#d0d0d0',
      blackBackground: colors.bg || '#040e1f'
    }
  });
  Blockly.getMainWorkspace().setTheme(theme);
}

// ================= ТУЛБОКС =================
export function getDynamicToolbox() {
  const tb = {
    "kind": "categoryToolbox",
    "contents": [
      {
        "kind": "category",
        "name": "Система",
        "colour": COLORS.SYSTEM,
        "contents": [{ "kind": "block", "type": "spraute_raw_code" }]
      }
    ]
  };
  for (const catName in customCategories) {
    tb.contents.push({
      "kind": "category",
      "name": catName,
      "colour": customCategories[catName].color,
      "contents": customCategories[catName].blocks.map(id => ({ "kind": "block", "type": id }))
    });
  }
  return tb;
}

// ================= ПАРСЕР ТЕКСТА В БЛОКИ =================
export function textToBlocks(text, workspace) {
  workspace.clear();
  let remainingText = text.trim();
  const x = 20, y = 20;

  function parseStatements(str, currentWorkspace) {
    let currentConn = null;
    let firstBlock = null;
    let localRemaining = str.trim();

    while (localRemaining.length > 0) {
      let matched = false;
      
      // Сначала пытаемся применить кастомные парсеры (по порядку плагинов)
      for (const parser of customParsers) {
        try {
          const result = parser.fn(localRemaining);
          if (result && result.length > 0) {
            const newBlock = currentWorkspace.newBlock(parser.id);
            if (result.fields) {
              for (const [k, v] of Object.entries(result.fields)) {
                newBlock.setFieldValue(v, k);
                newBlock[`val_${k}`] = v; // for dynamic ui
              }
            }
            // Вызываем updateShape чтобы создались слоты, если блок динамический
            if (newBlock.updateShape_) newBlock.updateShape_();

            if (result.statements) {
              for (const [k, innerStr] of Object.entries(result.statements)) {
                const innerFirst = parseStatements(innerStr, currentWorkspace);
                if (innerFirst) {
                  const input = newBlock.getInput(k);
                  if (input && input.connection) {
                    input.connection.connect(innerFirst.previousConnection);
                  }
                }
              }
            }
            
            // Если есть поля-значения (вложенные value блоки) - можно поддержать в будущем
            
            newBlock.initSvg();
            newBlock.render();

            if (currentConn) {
              currentConn.connect(newBlock.previousConnection);
            } else {
               firstBlock = newBlock;
            }
            currentConn = newBlock.nextConnection;

            localRemaining = localRemaining.slice(result.length).trim();
            matched = true;
            break;
          }
        } catch(e) {
          console.error(`Parser error in ${parser.id}:`, e);
        }
      }

      if (!matched) {
         // Fallback к построчному сырому коду
         const lineEnd = localRemaining.indexOf('\n');
         let line = "";
         if (lineEnd === -1) {
            line = localRemaining;
            localRemaining = "";
         } else {
            line = localRemaining.slice(0, lineEnd);
            localRemaining = localRemaining.slice(lineEnd + 1).trim();
         }
         
         if (line && !line.startsWith('#')) {
            const rawBlock = currentWorkspace.newBlock('spraute_raw_code');
            rawBlock.setFieldValue(line, 'CODE');
            rawBlock.initSvg();
            rawBlock.render();

            if (currentConn) {
              currentConn.connect(rawBlock.previousConnection);
            } else {
               firstBlock = rawBlock;
            }
            currentConn = rawBlock.nextConnection;
         }
      }
    }
    return firstBlock;
  }

  const rootBlock = parseStatements(remainingText, workspace);
  if (rootBlock) {
    rootBlock.moveBy(x, y);
  }
}

export const toolbox = getDynamicToolbox();
