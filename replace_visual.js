const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, 'app/src/visual.js');
let code = fs.readFileSync(file, 'utf8');

const startStr = "function _registerBlockFromChunk(chunk, namespace, isPreview) {";
const endStr = "// ================= ТЕМА =================";

const startIndex = code.indexOf(startStr);
const endIndex = code.indexOf(endStr);

if (startIndex === -1 || endIndex === -1) {
    console.log("Could not find start or end index.");
    process.exit(1);
}

const newFunction = `function _registerBlockFromChunk(chunk, namespace, isPreview) {
  const allLines = chunk.split('\\n');
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
  const fullId = namespace ? \`\${namespace}.\${rawId}\` : rawId;

  if (!isPreview) {
    if (!customCategories[category]) customCategories[category] = { color, blocks: [] };
    if (!customCategories[category].blocks.includes(fullId)) {
      customCategories[category].blocks.push(fullId);
    }
  }

  function compileUiLine(lineStr, inputName, isDyn) {
    const line = lineStr.trim();
    let js = "";
    if (line.startsWith('row:')) {
      const rowStr = line.slice(4).trim();
      js += \`var row = self.appendDummyInput(\${JSON.stringify(inputName)});\\n\`;
      const tokens = rowStr.match(/"[^"]*"|\\['\\][^\\]]+\\['\\]]|\\([^)]+\\)/g) || [];
      // Let's use simpler regex that was there: /"[^"]*"|\\[[^\\]]+\\]|\\([^)]+\\)/g
      const realTokens = rowStr.match(/"[^"]*"|\\[[^\\]]+\\]|\\([^)]+\\)/g) || [];
      for (const tok of realTokens) {
        if (tok.startsWith('"')) {
          js += \`row.appendField(\${tok});\\n\`;
        } else if (tok.startsWith('[')) {
          const inner = tok.slice(1, -1);
          const colonIdx = inner.indexOf(':');
          const name = inner.slice(0, colonIdx).trim();
          const typeDef = inner.slice(colonIdx + 1).trim();
          if (typeDef === 'dropdown_npc') {
            js += \`row.appendField(new Blockly.FieldDropdown(function(){ return getNpcsDropdown(); }, function(v){ self.validateField(\${JSON.stringify(name)}, v); return v; }), \${JSON.stringify(name)});\\n\`;
          } else {
            const optsMatch = typeDef.match(/^dropdown\\((.*)\\)$/s);
            if (optsMatch) {
              const optsArr = optsMatch[1].split(',').map(o => {
                const parts = o.split(':').map(s => s.trim());
                return [parts[0], parts.length > 1 ? parts[1] : parts[0]];
              });
              js += \`row.appendField(new Blockly.FieldDropdown(\${JSON.stringify(optsArr)}, function(v){ self.validateField(\${JSON.stringify(name)}, v); return v; }), \${JSON.stringify(name)});\\n\`;
            }
          }
        } else if (tok.startsWith('(')) {
          const inner = tok.slice(1, -1);
          const parts = inner.split(':').map(s => s.trim());
          const name = parts[0], def = parts[2] || "";
          js += \`row.appendField(new Blockly.FieldTextInput(self[\${JSON.stringify("val_"+name)}] || \${JSON.stringify(def)}), \${JSON.stringify(name)});\\n\`;
        }
      }
      if (isDyn) js += \`self.dynamicInputNames_.push(\${JSON.stringify(inputName)});\\n\`;
    } else if (line.startsWith('input:')) {
      const m = line.match(/^input:\\s*(\\w+)\\s*\\(type:\\s*(\\w+)\\)(?:\\s+"([^"]+)")?/);
      if (m) {
        const [, iname, itype, label=""] = m;
        if (itype === 'value') {
          js += \`var inp = self.appendValueInput(\${JSON.stringify(iname)});\\n\`;
        } else {
          js += \`var inp = self.appendStatementInput(\${JSON.stringify(iname)});\\n\`;
        }
        if (label) js += \`inp.appendField(\${JSON.stringify(label)});\\n\`;
        if (isDyn) js += \`self.dynamicInputNames_.push(\${JSON.stringify(iname)});\\n\`;
      }
    }
    return js;
  }

  let staticUiJs = "";
  let dynUiJs = "";
  let codeGenJs = "";
  let extractedTemplates = [];

  if (isLegacy) {
    let staticCounter = 0;
    for (const raw of uiLines) {
      if (raw.trim()) staticUiJs += compileUiLine(raw.trim(), \`STAT_\${staticCounter++}\`, false);
    }
    const indentStack = [];
    let dynCounter = 0;
    for (const raw of dynamicUiLines) {
      if (!raw.trim()) continue;
      const indent = raw.match(/^\\s*/)[0].length;
      while (indentStack.length > 0 && indentStack[indentStack.length-1] >= indent) {
        dynUiJs += "}\\n";
        indentStack.pop();
      }
      const line = raw.trim();
      if (line.startsWith('if ') && line.endsWith(':')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/"[^"]*"|'[^']*'|\\b([a-zA-Z_]\\w*)\\b/g, (m, g1) => {
          if (!g1) return m;
          if (['true','false','null','undefined'].includes(g1)) return g1;
          return \`(self[\${JSON.stringify("val_"+g1)}] || self.getFieldValue(\${JSON.stringify(g1)}))\`;
        });
        dynUiJs += \`if (\${cond}) {\\n\`;
        indentStack.push(indent);
      } else {
        dynUiJs += compileUiLine(line, \`DYN_\${dynCounter++}\`, true);
      }
    }
    while (indentStack.length > 0) { dynUiJs += "}\\n"; indentStack.pop(); }
    codeGenJs = codeGenLines.map(l => _parseLine(l)).join('\\n');
    
    if (codeParseLines.length > 0) {
      const pCode = codeParseLines.map(l => _parseLine(l)).join('\\n');
      if (pCode.trim().startsWith('"') || pCode.trim().startsWith('\`')) {
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
    let staticCounter = 0;

    for (const raw of bodyLines) {
      if (!raw.trim()) continue;
      const indent = raw.match(/^\\s*/)[0].length;
      const line = raw.trim();
      
      while (dynIndentStack.length > 0 && dynIndentStack[dynIndentStack.length-1] >= indent) {
        dynUiJs += "}\\n"; dynIndentStack.pop();
      }
      while (codeIndentStack.length > 0 && codeIndentStack[codeIndentStack.length-1] >= indent) {
        codeGenJs += "}\\n"; codeIndentStack.pop();
      }

      if (line.startsWith('if ') && line.endsWith(':')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/\\bor\\b/g, '||').replace(/\\band\\b/g, '&&').replace(/\\bnot\\b/g, '!');
        
        let uiCond = cond.replace(/"[^"]*"|'[^']*'|\\b([a-zA-Z_]\\w*)\\b/g, (m, g1) => {
          if (!g1) return m;
          if (['true','false','null','undefined'].includes(g1)) return g1;
          return \`(self[\${JSON.stringify("val_"+g1)}] || self.getFieldValue(\${JSON.stringify(g1)}))\`;
        });
        
        let codeCond = cond.replace(/"[^"]*"|'[^']*'|\\b([a-zA-Z_]\\w*)\\b/g, (m, g1) => {
          if (!g1) return m;
          if (['true','false','null','undefined'].includes(g1)) return g1;
          return \`_getVal(\${JSON.stringify(g1)})\`;
        });
        
        dynUiJs += \`if (\${uiCond}) {\\n\`;
        dynIndentStack.push(indent);
        
        codeGenJs += \`if (\${codeCond}) {\\n\`;
        codeIndentStack.push(indent);
        
      } else if (line.startsWith('template:')) {
        let tmpl = line.slice(9).trim();
        let actualTmpl = tmpl.replace(/\\\\n/g, '\\n').replace(/\\\\t/g, '\\t');
        extractedTemplates.push(actualTmpl);
        let jsTmpl = actualTmpl.replace(/{([a-zA-Z0-9_]+)}/g, (match, v) => {
            return \`\\\${\\_getVal(\${JSON.stringify(v)})}\`;
        });
        codeGenJs += \`return \\\`\${jsTmpl}\\\\n\\\`;\\n\`;
      } else if (line.startsWith('code:')) {
        codeGenJs += line.slice(5).trim() + "\\n";
      } else if (line.startsWith('row:') || line.startsWith('input:')) {
        if (dynIndentStack.length === 0) {
           staticUiJs += compileUiLine(line, \`STAT_\${staticCounter++}\`, false);
        } else {
           dynUiJs += compileUiLine(line, \`DYN_\${dynCounter++}\`, true);
        }
      }
    }
    while (dynIndentStack.length > 0) { dynUiJs += "}\\n"; dynIndentStack.pop(); }
    while (codeIndentStack.length > 0) { codeGenJs += "}\\n"; codeIndentStack.pop(); }
  }

  const hasDyn = dynUiJs.trim().length > 0;

  Blockly.Blocks[fullId] = {
    init: function() {
      const self = this;
      this.dynamicInputNames_ = [];
      this.trackedFields_ = [];
      
      this.setColour(color);
      if (shape === 'value') {
        this.setOutput(true, null);
      } else {
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
      }
      if (shape === 'wrapper') {
        this.appendStatementInput("DO");
      }

      try {
        const initFn = new Function('Blockly', 'getNpcsDropdown', 'self', staticUiJs || "");
        initFn(Blockly, getNpcsDropdown, self);
      } catch(e) { console.error(\`[Block \${fullId}] Init UI error:\`, e); }

      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (field.name) {
            this.trackedFields_.push(field.name);
            this[\`val_\${field.name}\`] = field.getValue();
          }
        }
      }
      if (hasDyn) this.updateShape_();
    },

    validateField: function(name, newValue) {
      const old = this[\`val_\${name}\`];
      this[\`val_\${name}\`] = newValue;
      if (hasDyn && old !== newValue) {
        const self = this;
        setTimeout(() => { if (self.workspace) self.updateShape_(); }, 0);
      }
      return newValue;
    },

    mutationToDom: function() {
      const container = document.createElement('mutation');
      for (const f of this.trackedFields_) {
        const val = this[\`val_\${f}\`] || (this.getField(f) ? this.getFieldValue(f) : null);
        if (val != null) container.setAttribute(\`f_\${f}\`, val);
      }
      return container;
    },

    domToMutation: function(xml) {
      for (const attr of xml.attributes) {
        if (attr.name.startsWith('f_')) {
          this[\`val_\${attr.name.slice(2)}\`] = attr.value;
        }
      }
      if (hasDyn) this.updateShape_();
    },

    onchange: function(e) {
      if (e.blockId !== this.id) return;
      if (e.type === Blockly.Events.BLOCK_CHANGE && e.name) {
        this[\`val_\${e.name}\`] = e.newValue;
      }
    },

    updateShape_: function() {
      if (!dynUiJs) return;
      const toRemove = [...this.dynamicInputNames_].filter(n => n.startsWith('DYN_'));
      for (const n of toRemove) {
        if (this.getInput(n)) this.removeInput(n);
      }
      this.dynamicInputNames_ = this.dynamicInputNames_.filter(n => !n.startsWith('DYN_'));
      const self = this;
      try {
        const dynFn = new Function('Blockly', 'getNpcsDropdown', 'self', dynUiJs);
        dynFn(Blockly, getNpcsDropdown, self);
      } catch(e) { console.error(\`[Block \${fullId}] Dynamic UI error:\`, e); }
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
      if (v === null || v === undefined) v = block[\`val_\${name}\`];
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
          ctx += \`var \${f} = \${JSON.stringify(v === null ? "" : v)};\\n\`;
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
            ctx += \`var \${input.name} = \${JSON.stringify(gen || "")};\\n\`;
          }
        }
    }

    let finalCodeGen = codeGenJs;
    if (!finalCodeGen && extractedTemplates.length === 1 && !isLegacy && !hasDyn) {
        finalCodeGen = \`return \\\`\${extractedTemplates[0].replace(/{([a-zA-Z0-9_]+)}/g, (m, v) => \`\\\${\\_getVal(\${JSON.stringify(v)})}\`)}\\\\n\\\`;\\n\`;
    }

    const fullCode = (isLegacy ? ctx : "") + finalCodeGen;
    try {
      const fn = new Function('_getVal', 'block', 'SprauteGenerator', fullCode);
      const res = fn(_getVal, block, SprauteGenerator);
      if (shape === 'value') return [res ?? "", 0];
      return res ?? "";
    } catch(e) {
      return \`/* CodeGen error in \${fullId}: \${e.message} */\\n\`;
    }
  };

  if (!isPreview && extractedTemplates.length > 0) {
    for (const tmpl of extractedTemplates) {
      let regexStr = tmpl.replace(/[.*+?^$()|[\\]\\\\]/g, '\\\\$&');
      const vars = [];
      regexStr = regexStr.replace(/\\\\{([a-zA-Z0-9_]+)\\\\}/g, (match, varName) => {
        vars.push(varName);
        return '([\\\\s\\\\S]*?)';
      });
      regexStr = regexStr.replace(/\\s+/g, '\\\\s*');
      regexStr = \`^\\\\s*\${regexStr}\`;
      
      const pCode = \`
        const m = text.match(/\${regexStr}/);
        if (m) {
          const fields = {};
          const statements = {};
          const varNames = \${JSON.stringify(vars)};
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
      \`;
      try {
        customParsers.push({ id: fullId, plugin: namespace, fn: new Function('text', pCode) });
      } catch(e) { console.error(\`[Block \${fullId}] CodeParse error:\`, e); }
    }
  }
}
`;

code = code.substring(0, startIndex) + newFunction + "\n" + code.substring(endIndex);
fs.writeFileSync(file, code, 'utf8');
console.log("Successfully replaced visual.js");
