const fs = require('fs');
const path = require('path');

const markdown = fs.readFileSync(path.join(__dirname, 'docs/visual_blocks_builder.md'), 'utf8');

const jsCode = `export const visualBlocksDocs = \`${markdown.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$/g, '\\$')}\`;\n`;

fs.writeFileSync(path.join(__dirname, 'app/src/docs.js'), jsCode, 'utf8');
console.log('docs.js generated');
