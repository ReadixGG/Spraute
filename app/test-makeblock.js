import fs from 'fs';

let content = fs.readFileSync('./src/visual.js', 'utf8');

content = content.replace(/export /g, '');
content = content.replace(/import .*?;\n/g, '');

content = `
const Blockly = { Blocks: {}, Theme: { defineTheme: ()=>{} }, Themes: { Dark: {} }, Generator: function(){ this.forBlock={}; } };
` + content;

content += `
[...AWAIT_EVENTS, ...PLAYER_ACTIONS, ...VALUE_ACTIONS, ...UI_ACTIONS, ...PARTICLE_ACTIONS, ...NPC_ACTIONS].forEach(act => {
    try {
        makeBlockWithShadows(act);
    } catch(e) {
        console.error("FAILED for act:", act.type, e);
    }
});
makeBlockWithShadows({ type: "spraute_action_get_nearest_player", args: ["ANCHOR"], defaultArgs: ["target"] });
console.log("All makeBlockWithShadows done.");
`;
fs.writeFileSync('test-run.js', content);
