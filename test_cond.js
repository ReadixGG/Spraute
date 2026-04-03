let cond = 'mode == "stop"';
let strLits = [];
let condNoStr = cond.replace(/"[^"]*"|'[^']*'/g, (m) => {
    strLits.push(m);
    return `__STR${strLits.length-1}__`;
});
console.log(condNoStr);

let uiCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
  if (['true','false','null','undefined'].includes(m) || m.startsWith('__STR')) return m;
  return `(self["val_"+m] || self.getFieldValue(m))`;
});
console.log(uiCondNoStr);

let uiCond = uiCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
console.log(uiCond);
