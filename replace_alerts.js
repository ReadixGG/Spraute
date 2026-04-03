const fs = require('fs');
const path = require('path');

const mainJsPath = path.join(__dirname, 'app/src/main.js');
let code = fs.readFileSync(mainJsPath, 'utf8');

// Replace confirm( with await appConfirm(
// Since the instances are inside async functions, this is safe.
code = code.replace(/if \(\s*confirm\(/g, 'if (await appConfirm(');
code = code.replace(/onclick="alert\(([^)]+)\)"/g, `onclick="appAlert($1)"`);

// Replace remaining alert( with appAlert(
code = code.replace(/\balert\(/g, 'appAlert(');

fs.writeFileSync(mainJsPath, code, 'utf8');
console.log('Replaced alerts and confirms');
