const chunk = `
row: [npc: dropdown_npc] "Смотреть" [mode: dropdown(один раз: false, всегда: true, перестать: stop)] "на" [target_type: dropdown(НИПа: npc, игрока: player, моба: mob)]
if mode == "stop":
  template: {npc}.look(false)
if mode == "false" or mode == "true":
  if target_type == "npc":
    row: "по имени" [target_npc: dropdown_npc]
    template: {npc}.look({mode}, "{target_npc}")
  if target_type == "player":
    input: target_player (type: value) "переменная игрока"
    template: {npc}.look({mode}, {target_player})
  if target_type == "mob":
    row: "моб" (target_mob: text: "zombie")
    template: {npc}.look({mode}, "{target_mob}")
`;

let dynUiJs = '';
let dynIndentStack = [];
let indent = 0;
let lines = chunk.split('\n');
for (let line of lines) {
    if (!line.trim()) continue;
    let currentIndent = line.match(/^\s*/)[0].length;
    line = line.trim();
    while (dynIndentStack.length > 0 && dynIndentStack[dynIndentStack.length-1] >= currentIndent) {
        dynUiJs += '}\n'; dynIndentStack.pop();
    }
    if (line.startsWith('if ')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/\bor\b/g, '||').replace(/\band\b/g, '&&').replace(/\bnot\b/g, '!');
        
        let strLits = [];
        let condNoStr = cond.replace(/"[^"]*"|'[^']*'/g, (m) => {
            strLits.push(m);
            return `__STR${strLits.length-1}__`;
        });

        let uiCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
          if (['true','false','null','undefined'].includes(m) || m.startsWith('__STR')) return m;
          return `(self["val_" + "${m}"] || self.getFieldValue("${m}"))`;
        });
        
        let uiCond = uiCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
        
        dynUiJs += `if (${uiCond}) {\n`;
        dynIndentStack.push(currentIndent);
    } else if (line.startsWith('row:')) {
        if (dynIndentStack.length > 0) dynUiJs += `// DYN ROW: ${line}\n`;
        else dynUiJs += `// STAT ROW: ${line}\n`;
    } else if (line.startsWith('input:')) {
        if (dynIndentStack.length > 0) dynUiJs += `// DYN INPUT: ${line}\n`;
        else dynUiJs += `// STAT INPUT: ${line}\n`;
    }
}
while (dynIndentStack.length > 0) { dynUiJs += '}\n'; dynIndentStack.pop(); }
console.log(dynUiJs);
