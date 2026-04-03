let tmpl = '{npc}.look({mode}, "{target_npc}")';
let regexStr = tmpl.replace(/[.*+?^$()|[\]\\]/g, '\\$&');
console.log("After escape:", regexStr);
const vars = [];
regexStr = regexStr.replace(/\\\{([a-zA-Z0-9_]+)\\\}/g, (match, varName) => {
    vars.push(varName);
    return '([\\s\\S]*?)';
});
console.log("After vars:", regexStr, vars);
