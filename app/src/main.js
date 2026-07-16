import { EditorView, basicSetup } from "codemirror";
import { lineNumbers, highlightActiveLineGutter, highlightActiveLine, drawSelection } from "@codemirror/view";
import { EditorState, Transaction } from "@codemirror/state";
import { javascript } from "@codemirror/lang-javascript";
import { syntaxHighlighting, defaultHighlightStyle } from "@codemirror/language";
import { HighlightStyle } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";
import { search, searchKeymap, openSearchPanel } from "@codemirror/search";
import { keymap } from "@codemirror/view";
import { history, historyKeymap, undo, redo } from "@codemirror/commands";

import { StreamLanguage, LanguageSupport } from "@codemirror/language";
import { linter, lintGutter } from "@codemirror/lint";
import { autocompletion, completeAnyWord, snippetCompletion, completionKeymap, acceptCompletion, startCompletion } from "@codemirror/autocomplete";

import * as Blockly from 'blockly';
import { SprauteGenerator, generateWorkspaceCode, SprauteTheme, applyBlocklyThemeColors, updateDynamicLists, parseCustomBlocks, getDynamicToolbox, customCategories, clearCustomCategories, registerPluginCategoryOrder, applyPluginCategoryColors, sortPluginBlocks, attachBlocklyContextMenu, attachDynamicBlockReshapeListener, extractNpcCreateIdsFromBlocklyXml, extractNpcIdsFromWorkspace, extractCreateNpcIdsFromSpr, buildNpcDropdownIds, refreshDynamicDropdownFields, syncNpcDropdownsFromWorkspace, onSprauteAnimContextChanged, beginBlocklyRestore, endBlocklyRestore, prepareBlocklyXmlForLoad, isGeoModelFileName } from './visual.js';

import { visualBlocksDocs } from './docs.js';
import { initGuiEditor, setupGuiEditorBridge } from './gui-editor.js';
import { initExportMap } from './export-map.js';

function formatStudioVersion(raw) {
  if (!raw) return 'v?';
  const m = String(raw).match(/^(\d+)\.(\d+)(?:\.(\d+))?/);
  if (!m) return `v${raw}`;
  if (!m[3] || m[3] === '0') return `v${m[1]}.${m[2]}`;
  return `v${m[1]}.${m[2]}.${m[3]}`;
}

function applyStudioVersionLabel() {
  const el = document.getElementById('studio-version-label');
  if (!el) return;
  el.textContent = formatStudioVersion(typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : null);
}

applyStudioVersionLabel();

const sprauteLanguage = StreamLanguage.define({
  token(stream, state) {
    if (stream.eatSpace()) return null;
    if (stream.match(/^#.*/)) return "comment";
    if (stream.match(/^"[^"]*"/)) return "string";
    if (stream.match(/^"[^"]*$/)) return "error";
    if (stream.match(/^-?\d+(?:\.\d+)?/)) return "number";
    if (stream.match(/^(val|fun|on|create|npc|ui|camera|if|else|elif|while|for|in|return|break|continue|true|false|null|await|time|say|playFreeze|playOnce|playLoop|stop|alwaysLookAt|lookAt)\b/)) return "keyword";
    if (stream.match(/^(text|button|pos|anchor|size|color|scale|model|texture|animation|name|hp|showName|background|slot|image|rect|progress|pitch|yaw|lookX|lookY|lookZ)\b/)) return "propertyName";
    if (stream.match(/^[a-zA-Z_]\w*(?=\s*\()/)) return "function";
    if (stream.match(/^[a-zA-Z_]\w*/)) return "variableName";
    if (stream.match(/^[+\-*\/=<>!&|]+/)) return "operator";
    if (stream.match(/^[{}()\[\],;.:]/)) return "punctuation";
    stream.next();
    return null;
  },
  tokenTable: {
    comment: t.comment,
    string: t.string,
    number: t.number,
    keyword: t.keyword,
    propertyName: t.propertyName,
    function: t.function(t.variableName),
    variableName: t.variableName,
    operator: t.operator,
    punctuation: t.punctuation,
    error: t.invalid
  }
});

const sprauteKeywords = [
  "val", "fun", "on", "create", "npc", "ui", "camera", "if", "else", "elif", "while", "for", "in",
  "return", "break", "continue", "true", "false", "null", "await", "time", "say",
  "playFreeze", "playOnce", "playLoop", "stop", "alwaysLookAt", "lookAt"
].map(kw => ({label: kw, type: "keyword"}));

const sprauteProperties = [
  // Базовые параметры NPC
  "name", "hp", "speed", "pos", "rotate", "showName", "collision", "model", "texture", "idleAnim", "walkAnim", "head",
  
  // Свойства сущностей
  "x", "y", "z", "pitch", "yaw", "lookX", "lookY", "lookZ", "uuid", "java", "data", "savedData",
  
  // Параметры кастомных блоков
  "texture_up", "texture_down", "texture_sides", "texture_north", "texture_south", "texture_west", "texture_east",
  "light", "hardness", "drop", "maxStackSize", "directional",
  "is_ore", "ore_vein", "ore_min", "ore_max", "ore_chances",
  
  // UI параметры
  "size", "background", "bg", "canClose",
  "anchor", "anchorX", "anchorY", "scale", "crop", "feetCrop",
  "rotation", "pivot", "pivotX", "pivotY",
  "color", "hover", "bgColor", "outlineColor",
  "wrap", "align", "tooltip", "layer", "order", "id",
  "maxLines", "maxChars", "inputType", "placeholder",
  "contentH", "scrollbar",
  "gridType", "cellSize", "thickness", "renderBones",
  
  // Камера
  "smooth", "smoothTime", "dimension", "dim", "lookAt", "track", "target",

  // Остальное
  "text", "button", "slot", "image", "rect", "progress"
].map(prop => ({label: prop, type: "property"}));

const sprauteFunctionsList = [
  // Базовые функции
  "chat(${1:message})",
  "chat(${1:player}, ${2:message})",
  "npc(${1:name}, ${2:hp}, ${3:speed}, ${4:x}, ${5:y}, ${6:z}, ${7:yaw}, ${8:pitch})",
  "say(${1:who}, ${2:message})",
  "setNamesColor(${1:color})",
  "getNearestPlayer(${1:anchor})",
  "getSlot(${1:player}, ${2:slot})",
  "hasItem(${1:player}, ${2:item_id})",
  "countItem(${1:player}, ${2:item_id})",
  "getPlayer(${1:name})",
  "placeStructure(${1:name}, ${2:x}, ${3:y}, ${4:z})",
  "saveStructure(${1:name}, ${2:x1}, ${3:y1}, ${4:z1}, ${5:x2}, ${6:y2}, ${7:z2})",
  "spawnBillboard(${1:texture}, ${2:x}, ${3:y}, ${4:z}, ${5:w}, ${6:h}, ${7:seeThrough})",
  "spawnBillboardNamed(${1:id}, ${2:texture}, ${3:x}, ${4:y}, ${5:z}, ${6:w}, ${7:h}, ${8:seeThrough})",
  "spawnBillboardNear(${1:id}, ${2:player}, ${3:texture}, ${4:ox}, ${5:oy}, ${6:oz}, ${7:w}, ${8:h}, ${9:seeThrough})",
  "getBillboard(${1:id})",
  "removeBillboard(${1:id})",
  "setBillboardSize(${1:id}, ${2:w}, ${3:h})",
  "setBillboardSeeThrough(${1:id}, ${2:seeThrough})",
  "teleportBillboard(${1:id}, ${2:x}, ${3:y}, ${4:z})",
  "removeEntity(${1:uuid})",
  "setBillboardTexture(${1:uuid}, ${2:texture})",
  "teleportEntity(${1:uuid}, ${2:x}, ${3:y}, ${4:z})",
  "getEntityPos(${1:uuid})",
  "getProjectilePos(${1:uuid})",
  "createGroup(${1:name})",
  "getGroup(${1:name})",
  "groupAdd(${1:group}, ${2:npc})",
  "groupRemove(${1:group}, ${2:npc})",
  "groupClear(${1:group})",
  "groupSize(${1:group})",
  "getPlayers()",
  "getFirstPlayer()",
  "getLastPlayer()",
  "getPlayerAt(${1:index})",
  "playerCount()",
  "addPlayerTag(${1:player}, ${2:tag})",
  "removePlayerTag(${1:player}, ${2:tag})",
  "hasPlayerTag(${1:player}, ${2:tag})",
  "getPlayersByTag(${1:tag})",
  "getPlayerTags(${1:player})",
  "getPlayerMotion(${1:player})",
  "setPlayerMotion(${1:player}, ${2:vx}, ${3:vy}, ${4:vz})",
  "getPlayerMovementSpeed(${1:player})",
  "setPlayerMovementSpeed(${1:player}, ${2:speed})",
  "getPlayerJumpStrength(${1:player})",
  "setPlayerJumpStrength(${1:player}, ${2:jump})",
  "getPlayerStepHeight(${1:player})",
  "setPlayerStepHeight(${1:player}, ${2:step})",
  "getPlayerAttackDamage(${1:player})",
  "setPlayerAttackDamage(${1:player}, ${2:damage})",
  "getPlayerDigSpeed(${1:player})",
  "setPlayerDigSpeed(${1:player}, ${2:mult})",
  "setPlayerFlight(${1:player}, ${2:true})",
  "getPlayerFlight(${1:player})",
  "setPlayerGamemode(${1:player}, \"${2:survival}\")",
  "getPlayerGamemode(${1:player})",
  "isPlayerGamemode(${1:player}, \"${2:survival}\")",
  "getPlayerFacing(${1:player})",
  "playersInRadius(${1:x}, ${2:y}, ${3:z}, ${4:radius})",
  "playersNear(${1:anchor}, ${2:radius})",
  "entitiesInRadius(${1:x}, ${2:y}, ${3:z}, ${4:radius}, \"${5:any}\")",
  "entitiesNear(${1:anchor}, ${2:radius}, \"${3:any}\")",
  "setBlock(${1:x}, ${2:y}, ${3:z}, ${4:block_id})",
  "useBlock(${1:x}, ${2:y}, ${3:z})",
  "useBlock(${1:npc}, ${2:x}, ${3:y}, ${4:z})",
  "heldItem(${1:hand})",
  "heldItemNbt(${1:hand})",
  "giveItem(${1:player}, ${2:item_id}, ${3:count})",
  "npcThrowItem(${1:npc}, ${2:item_id}, ${3:count})",
  "npcAttack(${1:npc}, ${2:target}, ${3:right|left}, ${4:speed}, ${5:range})",
  "getHeldItem(${1:player})",
  "getItemInSlot(${1:player}, ${2:slot})",
  "getPlayerInventory(${1:player})",
  "isSlotEmpty(${1:player}, ${2:slot})",
  "hasItemInSlot(${1:player}, ${2:slot}, ${3:item_id})",
  "findItemSlot(${1:player}, ${2:item_id})",
  "setItemInSlot(${1:player}, ${2:slot}, ${3:item_id}, ${4:count})",
  "setItemCount(${1:player}, ${2:slot}, ${3:count})",
  "clearItemSlot(${1:player}, ${2:slot})",
  "removeItem(${1:player}, ${2:item_id}, ${3:count})",
  "getItemName(${1:player}, ${2:slot})",
  "setItemName(${1:player}, ${2:slot}, ${3:name})",
  "getItemLore(${1:player}, ${2:slot})",
  "setItemLore(${1:player}, ${2:slot}, ${3:lore})",
  "appendItemLore(${1:player}, ${2:slot}, ${3:lore})",
  "getItemAttackDamage(${1:player}, ${2:slot})",
  "setItemAttackDamage(${1:player}, ${2:slot}, ${3:damage})",
  "getItemNbt(${1:player}, ${2:slot})",
  "setItemNbt(${1:player}, ${2:slot}, ${3:nbt})",
  "execute(${1:command})",
  "execute(${1:command}, ${2:executor})",
  "taskDone(${1:task_id})",
  "intStr(${1:x})",
  "wholeStr(${1:x})",
  "random()",
  "listCreate()",
  "dictCreate()",
  "playSound(${1:player}, ${2:sound_id})",
  "stopSound(${1:player})",
  "npcChat(${1:player}, ${2:npc}, ${3:message}, ${4:color})",

  // UI
  "uiOpen(${1:player}, ${2:template})",
  "uiClose(${1:player})",
  "overlayOpen(${1:player}, ${2:template})",
  "overlayClose(${1:player})",
  "uiUpdate(${1:player}, ${2:widget_id}, ${3:field}, ${4:value})",
  "uiAnimate(${1:player}, ${2:widget_id}, ${3:field}, ${4:value}, ${5:duration})",
  "uiIsOpen(${1:player})",
  "uiContainerOpen(${1:player})",
  "uiSlotItem(${1:player}, ${2:slot_id})",
  "uiSlotIsEmpty(${1:player}, ${2:slot_id})",
  "uiSlotHasItem(${1:player}, ${2:slot_id}, ${3:item_id})",
  "uiSlotCount(${1:player}, ${2:slot_id})",
  "uiSlotSlots(${1:player})",
  "uiSetSlot(${1:player}, ${2:slot_id}, ${3:item_id}, ${4:count})",
  "uiClearSlot(${1:player}, ${2:slot_id})",
  "uiGetInput(${1:player}, ${2:widget_id})",
  "uiSetInput(${1:player}, ${2:widget_id}, ${3:text})",
  "uiScrollGet(${1:player}, ${2:scroll_id})",
  "uiScrollSet(${1:player}, ${2:scroll_id}, ${3:offset})",
  "uiTouch(${1:player}, ${2:id1}, ${3:id2})",
  
  // Игрок Действия
  "action(${1:player}, ${2:action_type}, ${3:target})",
  "playerAction(${1:player}, ${2:action_type}, ${3:target})",

  // Частицы
  "particleSpawn(${1:type}, ${2:x}, ${3:y}, ${4:z}, ${5:count}, ${6:dx}, ${7:dy}, ${8:dz}, ${9:speed})",
  "particleLine(${1:type}, ${2:x1}, ${3:y1}, ${4:z1}, ${5:x2}, ${6:y2}, ${7:z2}, ${8:count}, ${9:dx}, ${10:dy}, ${11:dz}, ${12:speed})",
  "particleCircle(${1:type}, ${2:cx}, ${3:cy}, ${4:cz}, ${5:radius}, ${6:count}, ${7:dx}, ${8:dy}, ${9:dz}, ${10:speed})",
  "particleSpiral(${1:type}, ${2:cx}, ${3:cy}, ${4:cz}, ${5:radius}, ${6:height}, ${7:count}, ${8:dx}, ${9:dy}, ${10:dz}, ${11:speed})",
  "particleStartBone(${1:task_id}, ${2:npc}, ${3:bone_name}, ${4:type}, ${5:count}, ${6:dx}, ${7:dy}, ${8:dz}, ${9:speed})",
  "particleStopBone(${1:task_id})",

  // NPC методы (обычно вызываются как npc.playOnce(...))
  "playOnce(${1:anim})",
  "playLoop(${1:anim})",
  "playFreeze(${1:anim})",
  "stopOverlay()",
  "setAdditiveWeight(${1:weight})",
  "setHitbox(${1:width}, ${2:height})",
  "setHitbox(${1:width}, ${2:height}, ${3:ox}, ${4:oy}, ${5:oz})",
  "setHitboxOffset(${1:ox}, ${2:oy}, ${3:oz})",
  "resetHitbox()",
  "setHitboxPreset(${1:preset})",
  "addBoneHitbox(${1:bone}, ${2:w}, ${3:h}, ${4:d})",
  "addBoneHitbox(${1:id}, ${2:bone}, ${3:w}, ${4:h}, ${5:d}, ${6:ox}, ${7:oy}, ${8:oz})",
  "removeBoneHitbox(${1:id})",
  "clearBoneHitboxes()",
  "showHitboxDebug(${1:on})",
  "setFlying(${1:flying})",
  "flyTo(${1:x}, ${2:y}, ${3:z}, ${4:speed})",
  "flyTo(${1:target}, ${2:speed})",
  "alwaysFlyTo(${1:x}, ${2:y}, ${3:z}, ${4:speed})",
  "alwaysFlyTo(${1:target}, ${2:speed})",
  "setFlyIdleAnim(${1:anim})",
  "setFlyWalkAnim(${1:anim})",
  "setSwimming(${1:swimming})",
  "setSwimIdleAnim(${1:anim})",
  "setSwimWalkAnim(${1:anim})",
  "setDeathAnim(${1:anim})",
  "moveTo(${1:x}, ${2:y}, ${3:z})",
  "alwaysMoveTo(${1:entity_or_pos})",
  "stopMove()",
  "followUntil(${1:target}, ${2:dist})",
  "remove()",
  "setItem(${1:hand}, ${2:item_id})",
  "removeItem(${1:hand})",
  "pickupOnlyFrom(${1:entity})",
  "pickupAny()",
  "lookAt(${1:target})",
  "alwaysLookAt(${1:target})",
  "stopLook()",
  "setHeadBone(${1:bone_name})",

  // Игрок
  "raycast(${1:max_dist})",
  "damage(${1:amount}, ${2:source_entity})",
  "teleport(${1:x}, ${2:y}, ${3:z})",
  
  // Разное
  "cancelEvent()",
  "spawnOrb(${1:texture}, ${2:amount}, ${3:x}, ${4:y}, ${5:z})",
  "removeOrbs(${1:texture})",
  "addMobDrop(${1:mob_id}, ${2:item_id})",
  "addBlockDrop(${1:block_id}, ${2:item_id})",
  "drop(${1:item_id}, ${2:count})",
  "addDrop(${1:item_id})",
  "openBlockUi(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:ui_template})",
  "setBlockDisplay(${1:x}, ${2:y}, ${3:z}, ${4:id}, ${5:item_id}, ${6:ox}, ${7:oy}, ${8:oz}, ${9:rx}, ${10:ry}, ${11:rz}, ${12:scale})",
  "setBlockDisplayModel(${1:x}, ${2:y}, ${3:z}, ${4:id}, ${5:model}, ${6:texture}, ${7:ox}, ${8:oy}, ${9:oz}, ${10:rx}, ${11:ry}, ${12:rz}, ${13:scale})",
  "setBlockDisplayBlock(${1:x}, ${2:y}, ${3:z}, ${4:id}, ${5:block_id}, ${6:ox}, ${7:oy}, ${8:oz}, ${9:rx}, ${10:ry}, ${11:rz}, ${12:scale})",
  "removeBlockDisplay(${1:x}, ${2:y}, ${3:z}, ${4:id})",
  "getBlockSlot(${1:x}, ${2:y}, ${3:z}, ${4:slot})",
  "fadeOut()",

  // Камера
  "setCamera(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:yaw}, ${6:pitch})",
  "setCamera(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:yaw}, ${6:pitch}, ${7:holdTime}, ${8:smoothTime})",
  "setCameraLookAt(${1:player}, ${2:camX}, ${3:camY}, ${4:camZ}, ${5:lookX}, ${6:lookY}, ${7:lookZ}, ${8:holdTime}, ${9:smoothTime})",
  "setCameraLookAt(${1:player}, ${2:camX}, ${3:camY}, ${4:camZ}, ${5:entity}, ${6:holdTime}, ${7:smoothTime}, ${8:track})",
  "animateCamera(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:yaw}, ${6:pitch}, ${7:smoothTime})",
  "animateCameraLookAt(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:target}, ${6:smoothTime}, ${7:track})",
  "stopCamera(${1:player})",
  "stopCamera(${1:player}, ${2:smoothTime})",
  "resetCamera(${1:player})",
  "playCameraRoute(${1:player}, \"${2:route}\")",
  "await cameraRoute(${1:player}, \"${2:route}\")",

  // Полёт НПС — через методы npc.setFlying / npc.flyTo и т.д.
];

function smartSnippetCompletion(template, options) {
  const snip = snippetCompletion(template, options);
  if (typeof snip.apply === 'function') {
    const originalApply = snip.apply;
    snip.apply = (view, completion, from, to) => {
      const nextChar = view.state.sliceDoc(to, to + 1);
      if (nextChar === '(' && template.includes('(')) {
        view.dispatch({
          changes: {from, to, insert: options.label},
          selection: {anchor: from + options.label.length}
        });
      } else {
        originalApply(view, completion, from, to);
      }
    };
  }
  return snip;
}

const sprauteFunctionCompletions = sprauteFunctionsList.map(sig => {
  const name = sig.split('(')[0];
  return smartSnippetCompletion(sig, {label: name, detail: "method", type: "function"});
});

const sprauteSnippets = [
  // Базовые конструкции
  snippetCompletion("if (${1:condition}) {\n  ${2}\n}", {label: "if", detail: "block", type: "keyword"}),
  snippetCompletion("else if (${1:condition}) {\n  ${2}\n}", {label: "else if", detail: "block", type: "keyword"}),
  snippetCompletion("else {\n  ${1}\n}", {label: "else", detail: "block", type: "keyword"}),
  snippetCompletion("while (${1:condition}) {\n  ${2}\n}", {label: "while", detail: "block", type: "keyword"}),
  snippetCompletion("for (${1:item} in ${2:list}) {\n  ${3}\n}", {label: "for", detail: "block", type: "keyword"}),
  snippetCompletion("fun ${1:name}(${2:args}) {\n  ${3}\n}", {label: "fun", detail: "definition", type: "keyword"}),
  snippetCompletion("on ${1:event}(${2:args}) {\n  ${3}\n}", {label: "on", detail: "event handler", type: "keyword"}),
  snippetCompletion("create ui ${1:name} {\n  ${2}\n}", {label: "create ui", detail: "definition", type: "keyword"}),
  snippetCompletion('create npc ${1:name} {\n  name = "${2:Display Name}"\n  hp = ${3:20}\n  model = "geo/defolt.geo.json"\n  texture = "textures/entity/defolt.png"\n  animation = "animations/npc_classic.animation.json"\n  pos = ${4:0, 64, 0}\n  ${5}\n}', {label: "create npc", detail: "definition", type: "keyword"}),
  snippetCompletion('create npc_prefab ${1:goblin} {\n  name = "${2:Гоблин}"\n  hp = ${3:30}\n  model = "geo/defolt.geo.json"\n  texture = "textures/entity/defolt.png"\n  animation = "animations/npc_classic.animation.json"\n  speed = ${4:0.3}\n}', {label: "create npc_prefab", detail: "NPC prefab template", type: "keyword"}),
  snippetCompletion('spawnNpcPrefab("${1:goblin}", "${2:mob_01}", ${3:0}, ${4:64}, ${5:0})', {label: "spawnNpcPrefab", detail: "spawn NPC from prefab", type: "function"}),
  snippetCompletion('create block ${1:name} {\n  texture = "textures/block/${1}.png"\n  hardness = ${2:1.5}\n  ${3}\n}', {label: "create block", detail: "definition", type: "keyword"}),
  snippetCompletion('create item ${1:name} {\n  texture = "textures/item/${1}.png"\n  ${2}\n}', {label: "create item", detail: "definition", type: "keyword"}),
  snippetCompletion('fadeIn {\n  text = "${1:Title}"\n  subtitle = "${2:Subtitle}"\n  time = ${3:1.0}\n  visibleTime = ${4:2.0}\n  fadeout = ${5:false}\n  color = ${6:0x111111}\n}', {label: "fadeIn", detail: "block", type: "keyword"}),
  snippetCompletion('camera ${1:myCamera} {\n  pos = ${2:0}, ${3:70}, ${4:0}\n  rotate = ${5:0}, ${6:0}\n  time = ${7:0}\n  smooth = ${8:true}\n  smoothTime = ${9:0.5}\n  dimension = "${10:minecraft:overworld}"\n}', {label: "camera", detail: "block", type: "keyword"}),
  snippetCompletion('camera ${1:myCamera} {\n  pos = ${2:0}, ${3:70}, ${4:0}\n  lookAt = ${5:npcRef}\n  track = ${6:true}\n  time = ${7:0}\n  smooth = ${8:true}\n  dimension = "${9:minecraft:overworld}"\n}', {label: "camera lookAt", detail: "block", type: "keyword"}),
  
  // on События
  snippetCompletion('on interact(${1:target}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on interact", detail: "event", type: "keyword"}),
  snippetCompletion('on keybind("${1:key}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on keybind", detail: "event", type: "keyword"}),
  snippetCompletion('on death(${1:target}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on death", detail: "event", type: "keyword"}),
  snippetCompletion('on kill("${1:player}", "${2:any}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on kill", detail: "event", type: "keyword"}),
  snippetCompletion('on pickup(${1:npc}, "${2:item_id}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on pickup", detail: "event", type: "keyword"}),
  snippetCompletion('on uiClick(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on uiClick", detail: "event — _eventWidget, _eventMouseButton (left/right/middle)", type: "keyword"}),
  snippetCompletion('on uiClose(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on uiClose", detail: "event", type: "keyword"}),
  snippetCompletion('on uiHover(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on uiHover", detail: "event — _eventWidget, _eventHover (enter/leave)", type: "keyword"}),
  snippetCompletion('on uiHover(${1:player}, "${2:widget_id}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on uiHover widget", detail: "event", type: "keyword"}),
  snippetCompletion('on uiInput(${1:player}, "${2:widget_id}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on uiInput", detail: "event", type: "keyword"}),
  snippetCompletion('on position(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:radius}) -> ${6:handlerId} {\n  ${7}\n}', {label: "on position", detail: "event", type: "keyword"}),
  snippetCompletion('on inventory(${1:player}, "${2:item_id}"${3:, ${4:count}}) -> ${5:handlerId} {\n  ${6}\n}', {label: "on inventory", detail: "event — count optional; _eventPlayer, _eventItemId, _eventItemCount", type: "keyword"}),
  snippetCompletion('on clickBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on clickBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on breakBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on breakBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on placeBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on placeBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on chat(${1:player}, "${2:message}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on chat", detail: "event", type: "keyword"}),
  snippetCompletion('on jump(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on jump", detail: "event", type: "keyword"}),
  snippetCompletion('on uiTouch(${1:player}, "${2:id1}", "${3:id2}") -> ${4:handlerId} {\n  ${5}\n}', {label: "on uiTouch", detail: "event", type: "keyword"}),
  snippetCompletion('on action(${1:player}, "${2:action}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on action", detail: "event", type: "keyword"}),

  // await Ожидания
  snippetCompletion('await time(${1:seconds})', {label: "await time", detail: "wait", type: "keyword"}),
  snippetCompletion('await interact(${1:entity})', {label: "await interact", detail: "wait", type: "keyword"}),
  snippetCompletion('await next', {label: "await next", detail: "wait", type: "keyword"}),
  snippetCompletion('await keybind("${1:key}")', {label: "await keybind", detail: "wait", type: "keyword"}),
  snippetCompletion('await death(${1:target})', {label: "await death", detail: "wait", type: "keyword"}),
  snippetCompletion('await kill("${1:player}", "${2:any}")', {label: "await kill", detail: "wait", type: "keyword"}),
  snippetCompletion('await pickup(${1:npc}, ${2:amount}, "${3:item_id}")', {label: "await pickup", detail: "wait", type: "keyword"}),
  snippetCompletion('await task("${1:task_id}")', {label: "await task", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiClick(${1:player})', {label: "await uiClick", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiClose(${1:player})', {label: "await uiClose", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiInput(${1:player}, "${2:widget_id}")', {label: "await uiInput", detail: "wait", type: "keyword"}),
  snippetCompletion('await position(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:radius})', {label: "await position", detail: "wait", type: "keyword"}),
  snippetCompletion('await inventory(${1:player}, "${2:item_id}"${3:, ${4:count}})', {label: "await inventory", detail: "wait — count optional; sets _eventPlayer, _eventItemId, _eventItemCount", type: "keyword"}),
  snippetCompletion('await clickBlock(${1:player}, "${2:target}")', {label: "await clickBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await breakBlock(${1:player}, "${2:target}")', {label: "await breakBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await placeBlock(${1:player}, "${2:target}")', {label: "await placeBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await chat(${1:player}, "${2:message}")', {label: "await chat", detail: "wait", type: "keyword"}),
  snippetCompletion('await jump(${1:player})', {label: "await jump", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiTouch(${1:player}, "${2:id1}", "${3:id2}")', {label: "await uiTouch", detail: "wait", type: "keyword"}),
  snippetCompletion('await action(${1:player}, "${2:action}")', {label: "await action", detail: "wait", type: "keyword"}),
  snippetCompletion('await orbPickup(${1:player}, ${2:amount})', {label: "await orbPickup", detail: "wait", type: "keyword"}),

  // UI Виджеты (внутри create ui)
  snippetCompletion('text("${1:id}", "${2:text}") {\n  ${3}\n}', {label: "text", detail: "ui widget", type: "function"}),
  snippetCompletion('input("${1:id}") {\n  ${2}\n}', {label: "input", detail: "ui widget", type: "function"}),
  snippetCompletion('button("${1:id}", "${2:label}") {\n  ${3}\n}', {label: "button", detail: "ui widget", type: "function"}),
  snippetCompletion('entity("${1:entity_id}") {\n  ${2}\n}', {label: "entity", detail: "ui widget", type: "function"}),
  snippetCompletion('image("${1:id}", "${2:texture}") {\n  ${3}\n}', {label: "image", detail: "ui widget", type: "function"}),
  snippetCompletion('rect("${1:id}") {\n  ${2}\n}', {label: "rect", detail: "ui widget", type: "function"}),
  snippetCompletion('panel("${1:id}") {\n  ${2}\n}', {label: "panel", detail: "ui widget", type: "function"}),
  snippetCompletion('scroll("${1:id}") {\n  ${2}\n}', {label: "scroll", detail: "ui widget", type: "function"}),
  snippetCompletion('clip("${1:id}") {\n  ${2}\n}', {label: "clip", detail: "ui widget", type: "function"}),
  snippetCompletion('item("${1:id}", "${2:item_id}") {\n  ${3}\n}', {label: "item", detail: "ui widget", type: "function"}),
  snippetCompletion('grid_bg("${1:id}") {\n  ${2}\n}', {label: "grid_bg", detail: "ui widget", type: "function"})
];

function sprauteCompletions(context) {
  let word = context.matchBefore(/\w*/);
  if (word.from == word.to && !context.explicit) return null;
  
  return {
    from: word.from,
    options: [
      ...sprauteSnippets,
      ...sprauteFunctionCompletions,
      ...sprauteKeywords,
      ...sprauteProperties
    ]
  };
}

const sprauteLanguageSupport = new LanguageSupport(sprauteLanguage, [
  sprauteLanguage.data.of({
    autocomplete: sprauteCompletions
  }),
  sprauteLanguage.data.of({
    autocomplete: completeAnyWord
  })
]);

function getSyntaxHighlightStyles(syntaxThemeName) {
  if (syntaxThemeName === 'vscode-dark') {
    return [
      { tag: t.keyword, color: "#569CD6", fontWeight: "bold" },
      { tag: t.string, color: "#CE9178" },
      { tag: t.number, color: "#B5CEA8" },
      { tag: t.comment, color: "#6A9955", fontStyle: "italic" },
      { tag: t.function(t.variableName), color: "#DCDCAA" },
      { tag: t.variableName, color: "#9CDCFE" },
      { tag: t.propertyName, color: "#9CDCFE" },
      { tag: t.operator, color: "#D4D4D4" },
      { tag: t.punctuation, color: "#D4D4D4" },
      { tag: t.invalid, color: "#F44747", textDecoration: "underline wavy" }
    ];
  }
  if (syntaxThemeName === 'monokai') {
    return [
      { tag: t.keyword, color: "#F92672", fontWeight: "bold" },
      { tag: t.string, color: "#E6DB74" },
      { tag: t.number, color: "#AE81FF" },
      { tag: t.comment, color: "#75715E", fontStyle: "italic" },
      { tag: t.function(t.variableName), color: "#A6E22E" },
      { tag: t.variableName, color: "#F8F8F2" },
      { tag: t.propertyName, color: "#A6E22E" },
      { tag: t.operator, color: "#F92672" },
      { tag: t.punctuation, color: "#F8F8F2" },
      { tag: t.invalid, color: "#F8F8F0", backgroundColor: "#F92672" }
    ];
  }
  if (syntaxThemeName === 'github-dark') {
    return [
      { tag: t.keyword, color: "#FF7B72", fontWeight: "bold" },
      { tag: t.string, color: "#A5D6FF" },
      { tag: t.number, color: "#79C0FF" },
      { tag: t.comment, color: "#8B949E", fontStyle: "italic" },
      { tag: t.function(t.variableName), color: "#D2A8FF" },
      { tag: t.variableName, color: "#E6EDF3" },
      { tag: t.propertyName, color: "#79C0FF" },
      { tag: t.operator, color: "#79C0FF" },
      { tag: t.punctuation, color: "#E6EDF3" },
      { tag: t.invalid, color: "#FFA198" }
    ];
  }
  return [
    { tag: t.keyword, color: "var(--color-primary)", fontWeight: "bold" },
    { tag: t.string, color: "var(--color-tertiary)" },
    { tag: t.number, color: "var(--color-secondary)" },
    { tag: t.comment, color: "var(--color-on-variant)", fontStyle: "italic" },
    { tag: t.function(t.variableName), color: "var(--color-primary)" },
    { tag: t.variableName, color: "var(--color-on-surface)" },
    { tag: t.propertyName, color: "var(--color-tertiary)" },
    { tag: t.operator, color: "var(--color-outline)" },
    { tag: t.punctuation, color: "var(--color-on-variant)" },
    { tag: t.invalid, color: "#ff5555" }
  ];
}

function buildCodeMirrorEditorTheme(contentPadding = '1rem 0') {
  return EditorView.theme({
    "&": {
      backgroundColor: "transparent",
      color: "var(--color-on-surface)",
      height: "100%",
      fontSize: "var(--editor-font-size, 14px)",
      fontFamily: "var(--font-mono)",
    },
    ".cm-scroller": {
      fontFamily: "var(--font-mono)",
      backgroundColor: "transparent !important"
    },
    ".cm-content": {
      fontFamily: "var(--font-mono)",
      padding: contentPadding,
      backgroundColor: "transparent !important"
    },
    ".cm-gutters": {
      backgroundColor: "transparent",
      color: "var(--color-on-variant)",
      border: "none",
      borderRight: "1px solid rgba(255, 255, 255, 0.05)",
      paddingRight: "4px"
    },
    ".cm-gutters .cm-lineNumbers .cm-gutterElement": {
      color: "var(--color-on-variant)"
    },
    ".cm-activeLineGutter": {
      backgroundColor: "rgba(255,255,255,0.05)",
      color: "var(--color-on-variant)"
    },
    ".cm-activeLine": {
      backgroundColor: "rgba(255,255,255,0.03) !important"
    },
    ".cm-cursor": {
      borderLeftColor: "var(--color-primary)",
      borderLeftWidth: "2px"
    },
    "&.cm-focused .cm-cursor": {
      borderLeftColor: "var(--color-primary)"
    }
  });
}

const sprauteLinter = linter((view) => {
  let diagnostics = [];
  const doc = view.state.doc.toString();
  
  let stack = [];
  let inString = false;
  let inComment = false;
  
  for (let i = 0; i < doc.length; i++) {
    const char = doc[i];
    
    if (inComment) {
      if (char === '\n') inComment = false;
      continue;
    }
    
    if (inString) {
      // Escape
      if (char === '\\') {
        i++; // skip next char
        continue;
      }
      if (char === '"') inString = false;
      else if (char === '\n') {
        diagnostics.push({
          from: i - 1,
          to: i,
          severity: "error",
          message: "Незакрытая строка"
        });
        inString = false;
      }
      continue;
    }
    
    if (char === '#') {
      inComment = true;
      continue;
    }
    
    if (char === '"') {
      inString = true;
      continue;
    }
    
    if (char === '{' || char === '(' || char === '[') {
      stack.push({ char, pos: i });
    } else if (char === '}' || char === ')' || char === ']') {
      if (stack.length === 0) {
        diagnostics.push({
          from: i,
          to: i + 1,
          severity: "error",
          message: `Лишняя закрывающая скобка '${char}'`
        });
      } else {
        const last = stack.pop();
        const pairs = { '{': '}', '(': ')', '[': ']' };
        if (pairs[last.char] !== char) {
          diagnostics.push({
            from: i,
            to: i + 1,
            severity: "error",
            message: `Ожидалась '${pairs[last.char]}', но найдена '${char}'`
          });
        }
      }
    }
  }

  while (stack.length > 0) {
    const unclosed = stack.pop();
    diagnostics.push({
      from: unclosed.pos,
      to: unclosed.pos + 1,
      severity: "error",
      message: `Незакрытая скобка '${unclosed.char}'`
    });
  }

  return diagnostics;
});

// Локализация
const i18n = {
  ru: {
    folderTitle: "Папка Minecraft / Сервера",
    folderDesc: "Выберите корневую папку игры (например, <code class=\"bg-black/30 px-1.5 py-0.5 rounded font-mono text-tertiary\">.minecraft</code>) или вашего сервера. Студия автоматически будет работать с папкой <code class=\"bg-black/30 px-1.5 py-0.5 rounded font-mono text-primary\">spraute_engine</code> внутри неё.",
    folderLabel: "Выбранный путь",
    browse: "Обзор...",
    start: "Продолжить",
    notSelected: "Не выбрано",
    explorer: "Проводник",
    empty: "Выберите файл для редактирования"
  },
  en: {
    folderTitle: "Minecraft / Server Folder",
    folderDesc: "Select your game root folder (e.g., <code class=\"bg-black/30 px-1.5 py-0.5 rounded font-mono text-tertiary\">.minecraft</code>) or server directory. The Studio will automatically use the <code class=\"bg-black/30 px-1.5 py-0.5 rounded font-mono text-primary\">spraute_engine</code> folder inside it.",
    folderLabel: "Selected Path",
    browse: "Browse...",
    start: "Continue",
    notSelected: "Not selected",
    explorer: "Explorer",
    empty: "Select a file to edit"
  }
};

let currentLang = 'en';

function applyLanguage(lang) {
  currentLang = lang;
  const t = i18n[lang];
  document.getElementById('i18n-folder-title').innerHTML = t.folderTitle;
  document.getElementById('i18n-folder-desc').innerHTML = t.folderDesc;
  document.getElementById('i18n-folder-label').innerText = t.folderLabel;
  document.getElementById('i18n-browse').innerText = t.browse;
  document.getElementById('i18n-start').innerText = t.start;
  document.getElementById('i18n-explorer').innerText = t.explorer;
  document.getElementById('i18n-empty').innerText = t.empty;
  
  const pathLabel = document.getElementById('selected-path');
  if (pathLabel.innerText === i18n['ru'].notSelected || pathLabel.innerText === i18n['en'].notSelected) {
    pathLabel.innerText = t.notSelected;
  }
}

// Утилиты для переключения экранов
function showView(id) {
  ['view-language', 'view-folder', 'view-updater', 'view-studio'].forEach(viewId => {
    const el = document.getElementById(viewId);
    if (viewId === id) {
      el.classList.remove('hidden');
      // Небольшая задержка для анимации
      setTimeout(() => {
        el.classList.remove('opacity-0', 'translate-y-4');
        el.classList.add('opacity-100', 'translate-y-0');
      }, 50);
    } else {
      el.classList.add('hidden', 'opacity-0', 'translate-y-4');
      el.classList.remove('opacity-100', 'translate-y-0');
    }
  });
}

// Оверлей загрузки студии
function showLoadingOverlay(show = true) {
  const overlay = document.getElementById('studio-loading-overlay');
  if (!overlay) return;
  if (show) {
    overlay.classList.remove('opacity-0', 'pointer-events-none');
    overlay.style.display = '';
  } else {
    overlay.classList.add('opacity-0');
    setTimeout(() => {
      overlay.style.display = 'none';
      overlay.classList.add('pointer-events-none');
    }, 500);
  }
}

function updateLoadingOverlay(label, pct) {
  const lbl = document.getElementById('studio-loading-label');
  const bar = document.getElementById('studio-loading-bar');
  if (lbl) lbl.textContent = label;
  if (bar) bar.style.width = Math.min(100, Math.round(pct)) + '%';
}

// Мини-оверлей для переходов визуального режима (поверх editor/blockly)
function showVisualTransition(show, label) {
  let el = document.getElementById('visual-transition-overlay');
  if (show) {
    hideVisualLoadError();
    if (!el) {
      el = document.createElement('div');
      el.id = 'visual-transition-overlay';
      el.className = 'absolute inset-0 z-50 bg-background/90 backdrop-blur-sm flex flex-col items-center justify-center gap-3 transition-opacity duration-300';
      el.innerHTML = `
        <div id="visual-transition-spinner" class="relative w-10 h-10">
          <div class="absolute inset-0 rounded-full border-2 border-white/5"></div>
          <div class="absolute inset-0 rounded-full border-2 border-t-primary border-r-transparent border-b-transparent border-l-transparent animate-spin"></div>
        </div>
        <span id="visual-transition-label" class="text-sm text-on-variant font-mono text-center px-4"></span>
      `;
      const parent = document.getElementById('editor-area') || document.getElementById('editor-mount')?.parentElement;
      if (parent) {
        if (!parent.style.position && getComputedStyle(parent).position === 'static') {
          parent.style.position = 'relative';
        }
        parent.appendChild(el);
      }
    }
    const lbl = el.querySelector('#visual-transition-label');
    const spinner = el.querySelector('#visual-transition-spinner');
    if (lbl) lbl.textContent = label || 'Подготовка визуального режима...';
    if (spinner) spinner.style.display = '';
    el.style.display = '';
    el.classList.remove('opacity-0');
  } else if (el) {
    el.classList.add('opacity-0');
    setTimeout(() => { if (el) el.style.display = 'none'; }, 300);
  }
}

function hideVisualLoadError() {
  const banner = document.getElementById('visual-load-error');
  if (banner) banner.classList.add('hidden');
}

/** Предупреждение при загрузке .sprv — полоса над workspace, не перекрывает блоки. */
function showVisualLoadError(message, { missingBlocks } = {}) {
  showVisualTransition(false);
  const banner = document.getElementById('visual-load-error');
  const textEl = document.getElementById('visual-load-error-text');
  const dismissBtn = document.getElementById('visual-load-error-dismiss');
  if (!banner || !textEl) return;

  let text = message || 'неизвестная ошибка';
  if (missingBlocks?.length) {
    text += '\n\nНе найдены блоки (показаны красным — замените на актуальные):\n• ' + missingBlocks.join('\n• ');
  }
  textEl.textContent = text;
  banner.classList.remove('hidden');

  if (dismissBtn && !dismissBtn._sprauteBound) {
    dismissBtn._sprauteBound = true;
    dismissBtn.addEventListener('click', hideVisualLoadError);
  }
}

// Инициализация
async function init() {
  setupAssetRescanListeners();
  initGuiEditor();
  setupGuiEditorBridge(() => (VisualEngine && VisualEngine._cachedTextures) || []);
  initExportMap({ appAlert, setStatus });
  window.__sprauteGuiChanged = () => {
    try { syncVisualCodePreview().catch(() => {}); } catch (e) {}
    try {
      const tab = openTabs.find(t => t.path === activeTabPath);
      if (tab && tab.isVisualScript && !tab.isDirty) {
        tab.isDirty = true;
        renderTabs();
      }
    } catch (e) {}
  };
  if (!window.spraute) {
    document.body.innerHTML = '<div class="flex items-center justify-center h-full text-white">Please run inside Electron</div>';
    return;
  }

  // Загрузка сохранённых данных
  const savedLang = await window.spraute.storeGet('language');
  let mcPath = await window.spraute.storeGet('minecraftPath');
  
  // Применение темы
  const theme = await window.spraute.storeGet('theme') || 'kinetic-dark';
  const customColors = await window.spraute.storeGet('customColors') || { bg: '#040e1f', surface: '#0b1a2f', primary: '#d1ff9f', secondary: '#ac8aff' };
  
  const applyThemeColors = (colors) => {
    const root = document.documentElement;
    if (colors.bg) root.style.setProperty('--color-bg', colors.bg);
    if (colors.surface) {
      root.style.setProperty('--color-surface', colors.surface);
      root.style.setProperty('--color-surface-container', colors.surface);
      root.style.setProperty('--color-surface-bright', colors.surface);
      root.style.setProperty('--color-surface-low', colors.surface);
    }
    if (colors.primary) root.style.setProperty('--color-primary', colors.primary);
    if (colors.secondary) {
      root.style.setProperty('--color-secondary', colors.secondary);
      root.style.setProperty('--color-tertiary', colors.secondary);
    }
  };
  
  const PRESET_THEMES = {
    'kinetic-dark': { bg: '#040e1f', surface: '#0b1a2f', primary: '#d1ff9f', secondary: '#ac8aff' },
    'laboratory-light': { bg: '#f1f5f9', surface: '#ffffff', primary: '#10b981', secondary: '#8b5cf6' },
    'spraute-classic': { bg: '#1c1917', surface: '#334155', primary: '#facc15', secondary: '#38bdf8' }
  };
  
  if (theme === 'custom') {
    applyThemeColors(customColors);
  } else if (PRESET_THEMES[theme]) {
    applyThemeColors(PRESET_THEMES[theme]);
  }
  
  // Настройки шрифта
  const fontSize = await window.spraute.storeGet('editorFontSize') || 14;
  const fontFamily = await window.spraute.storeGet('editorFontFamily') || "'JetBrains Mono', monospace";
  document.documentElement.style.setProperty('--editor-font-size', `${fontSize}px`);
  document.documentElement.style.setProperty('--font-mono', fontFamily);
  
  // Применение фона
  const bgImg = await window.spraute.storeGet('bgImage');
  const bgOp = await window.spraute.storeGet('bgOpacity');
  applyBgImage(bgImg || '', bgOp || 0.2);

  async function runUpdaterAndStart(path, background = false) {
    if (!background) {
      document.getElementById('initial-title').classList.remove('hidden');
      showView('view-updater');
    } else {
      document.getElementById('initial-title').classList.add('hidden');
      document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';
      showView('view-studio');
      showLoadingOverlay(true);
      updateLoadingOverlay('Загрузка проекта...', 5);
    }
    
    const logs = document.getElementById('updater-logs');
    if (logs) logs.innerHTML = '';
    
    window.spraute.onUpdateProgress((msg) => {
      if (!background && logs) {
        const line = document.createElement('div');
        line.innerText = '> ' + msg;
        logs.appendChild(line);
        logs.scrollTop = logs.scrollHeight;
      } else {
        setStatus(msg);
        updateLoadingOverlay(msg, 20);
      }
    });

    await window.spraute.initWorkspace(path);
    await window.spraute.storeSet('minecraftPath', path);

    if (!background) {
      setTimeout(() => {
        const updaterView = document.getElementById('view-updater');
        updaterView.classList.remove('opacity-100', 'translate-y-0');
        updaterView.classList.add('opacity-0', '-translate-y-4');
        
        setTimeout(async () => {
          document.getElementById('initial-title').classList.add('hidden');
          document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';

          showView('view-studio');
          showLoadingOverlay(true);
          updateLoadingOverlay('Подготовка визуального редактора...', 30);

          await VisualEngine.warmUp('', (label, pct) => {
            updateLoadingOverlay(label, 30 + (pct / 100) * 60);
          });

          updateLoadingOverlay('Загрузка файлов...', 92);
          loadPluginsList();
          loadDirectory('');
          updateLoadingOverlay('Готово!', 100);
          setTimeout(() => showLoadingOverlay(false), 400);
        }, 500);
      }, 1500);
    } else {
      updateLoadingOverlay('Подготовка визуального редактора...', 30);
      await VisualEngine.warmUp('', (label, pct) => {
        updateLoadingOverlay(label, 30 + (pct / 100) * 60);
      });
      updateLoadingOverlay('Загрузка файлов...', 92);
      loadPluginsList();
      loadDirectory('');
      updateLoadingOverlay('Готово!', 100);
      setTimeout(() => showLoadingOverlay(false), 400);
    }
  }

  if (!savedLang) {
    document.getElementById('initial-title').classList.remove('hidden');
    showView('view-language');
  } else if (!mcPath) {
    document.getElementById('initial-title').classList.remove('hidden');
    applyLanguage(savedLang);
    showView('view-folder');
  } else {
    // Если уже сохранено, запускаем студию сразу, а обновление в фоне
    applyLanguage(savedLang);
    runUpdaterAndStart(mcPath, true);
  }

  // Обработчики кнопок языка
  document.querySelectorAll('[data-lang]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const lang = btn.getAttribute('data-lang');
      await window.spraute.storeSet('language', lang);
      applyLanguage(lang);
      
      // Анимация скрытия текущего окна и показа следующего
      const langView = document.getElementById('view-language');
      langView.classList.remove('opacity-100', 'translate-y-0');
      langView.classList.add('opacity-0', '-translate-y-4');
      
      setTimeout(() => {
        showView('view-folder');
      }, 500);
    });
  });

  // Обработчик выбора папки
  document.getElementById('btn-browse').addEventListener('click', async () => {
    const path = await window.spraute.selectMinecraftFolder();
    if (path) {
      mcPath = path;
      document.getElementById('selected-path').innerText = path;
      document.getElementById('selected-path').classList.replace('text-on-surface', 'text-primary');
      document.getElementById('btn-start').removeAttribute('disabled');
    }
  });

    // Обработчик кнопки старта
  document.getElementById('btn-start').addEventListener('click', async () => {
    if (mcPath) {
      document.getElementById('btn-start').setAttribute('disabled', 'true');
      
      const folderView = document.getElementById('view-folder');
      folderView.classList.remove('opacity-100', 'translate-y-0');
      folderView.classList.add('opacity-0', '-translate-y-4');
      
      setTimeout(() => {
        runUpdaterAndStart(mcPath);
      }, 500);
    }
  });

}

// === Custom Modal System ===
function appPrompt(title, defaultValue = '') {
  return new Promise((resolve) => {
    const modal = document.getElementById('app-modal');
    const box = document.getElementById('app-modal-box');
    const titleEl = document.getElementById('modal-title');
    const inputEl = document.getElementById('modal-input');
    const btnCancel = document.getElementById('modal-btn-cancel');
    const btnOk = document.getElementById('modal-btn-ok');

    titleEl.innerText = title;
    inputEl.value = defaultValue;
    inputEl.style.display = 'block';
    
    modal.classList.remove('hidden');
    setTimeout(() => {
      box.classList.remove('scale-95', 'opacity-0');
    }, 10);
    inputEl.focus();
    inputEl.select();

    const cleanup = () => {
      box.classList.add('scale-95', 'opacity-0');
      setTimeout(() => {
        modal.classList.add('hidden');
      }, 200);
      btnOk.onclick = null;
      btnCancel.onclick = null;
      inputEl.onkeydown = null;
    };

    btnOk.onclick = () => {
      resolve(inputEl.value);
      cleanup();
    };

    btnCancel.onclick = () => {
      resolve(null);
      cleanup();
    };

    inputEl.onkeydown = (e) => {
      if (e.key === 'Enter') {
        resolve(inputEl.value);
        cleanup();
      } else if (e.key === 'Escape') {
        resolve(null);
        cleanup();
      }
    };
  });
}

function appAlert(title) {
  return new Promise((resolve) => {
    const modal = document.getElementById('app-modal');
    const box = document.getElementById('app-modal-box');
    const titleEl = document.getElementById('modal-title');
    const inputEl = document.getElementById('modal-input');
    const btnCancel = document.getElementById('modal-btn-cancel');
    const btnOk = document.getElementById('modal-btn-ok');

    titleEl.innerText = title;
    inputEl.style.display = 'none';
    btnCancel.style.display = 'none';
    
    modal.classList.remove('hidden');
    setTimeout(() => {
      box.classList.remove('scale-95', 'opacity-0');
    }, 10);

    const cleanup = () => {
      box.classList.add('scale-95', 'opacity-0');
      setTimeout(() => {
        modal.classList.add('hidden');
      }, 200);
      btnOk.onclick = null;
      btnCancel.style.display = '';
    };

    btnOk.onclick = () => {
      resolve();
      cleanup();
    };
  });
}

function appConfirm(title) {
  return new Promise((resolve) => {
    const modal = document.getElementById('app-modal');
    const box = document.getElementById('app-modal-box');
    const titleEl = document.getElementById('modal-title');
    const inputEl = document.getElementById('modal-input');
    const btnCancel = document.getElementById('modal-btn-cancel');
    const btnOk = document.getElementById('modal-btn-ok');

    titleEl.innerText = title;
    inputEl.style.display = 'none';
    
    modal.classList.remove('hidden');
    setTimeout(() => {
      box.classList.remove('scale-95', 'opacity-0');
    }, 10);

    const cleanup = () => {
      box.classList.add('scale-95', 'opacity-0');
      setTimeout(() => {
        modal.classList.add('hidden');
      }, 200);
      btnOk.onclick = null;
      btnCancel.onclick = null;
    };

    btnOk.onclick = () => {
      resolve(true);
      cleanup();
    };

    btnCancel.onclick = () => {
      resolve(false);
      cleanup();
    };
  });
}
// ===========================

// Система файлов
let currentCtxNode = null;
let currentCtxRelPath = '';
let currentCtxIsDir = false;

// Состояние выделенного элемента
let selectedItemPath = '';
let selectedItemIsDir = true;

// Управление статусом
let statusTimeout = null;
function setStatus(msg) {
  const statusEl = document.getElementById('status-message');
  if (!statusEl) return;
  statusEl.innerHTML = `<span class="material-symbols-outlined text-[14px]">info</span> ${msg}`;
  statusEl.classList.remove('opacity-0');
  if (statusTimeout) clearTimeout(statusTimeout);
  statusTimeout = setTimeout(() => {
    statusEl.classList.add('opacity-0');
  }, 3000);
}

// ====== Drag and Drop ======
let draggedItem = null; // { path, name, isDir }

function setupDragAndDrop(el, item, wrapper) {
  el.setAttribute('draggable', 'true');
  
  el.addEventListener('dragstart', (e) => {
    draggedItem = { path: item.rel, name: item.name, isDir: item.isDir };
    el.classList.add('opacity-50');
    
    // Разрешаем перемещение и копирование (для редактора)
    e.dataTransfer.effectAllowed = 'copyMove';
    
    if (!item.isDir) {
      // Формируем текст, который CodeMirror подхватит автоматически при наведении и сбросе
      const ext = item.name.includes('.') ? item.name.split('.').pop() : '';
      let insertText = `"${item.rel}"`;
      if (ext === 'png' || ext === 'jpg' || ext === 'jpeg') {
        insertText = `texture = "${item.rel}"`;
      } else if (ext === 'json' && item.rel.includes('geo')) {
        insertText = `model = "${item.rel}"`;
      } else if (ext === 'json' && item.rel.includes('animations')) {
        insertText = `animation = "${item.rel}"`;
      }
      e.dataTransfer.setData('text/plain', insertText);
    } else {
      e.dataTransfer.setData('text/plain', item.rel);
    }
  });
  
  el.addEventListener('dragend', () => {
    el.classList.remove('opacity-50');
    document.querySelectorAll('.drag-over').forEach(n => n.classList.remove('drag-over', 'bg-primary/20', 'border', 'border-dashed', 'border-primary'));
    draggedItem = null;
  });
  
  // Только папки могут принимать файлы внутрь себя
  if (item.isDir) {
    el.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.stopPropagation(); // Не пускаем событие к корню
      if (draggedItem && draggedItem.path !== item.rel && !draggedItem.path.startsWith(item.rel + '/')) {
        e.dataTransfer.dropEffect = 'move';
        el.classList.add('drag-over', 'bg-primary/20', 'border', 'border-dashed', 'border-primary');
      }
    });
    
    el.addEventListener('dragleave', (e) => {
      e.stopPropagation();
      el.classList.remove('drag-over', 'bg-primary/20', 'border', 'border-dashed', 'border-primary');
    });
    
    el.addEventListener('drop', async (e) => {
      e.preventDefault();
      e.stopPropagation();
      el.classList.remove('drag-over', 'bg-primary/20', 'border', 'border-dashed', 'border-primary');
      
      if (draggedItem && draggedItem.path !== item.rel) {
        const srcPath = draggedItem.path;
        const srcName = draggedItem.name;
        const destPath = item.rel + '/' + srcName;
        
        if (srcPath.startsWith(item.rel + '/')) return;
        if (srcPath === destPath) return; // файл уже здесь
        
        try {
          const exists = await window.spraute.exists(destPath);
          if (exists) {
            const confirm = await appConfirm(`"${srcName}" уже существует в "${item.name}". Заменить?`);
            if (!confirm) return;
            if (draggedItem.isDir) await window.spraute.rmdir(destPath);
            else await window.spraute.unlink(destPath);
          }
          
          await window.spraute.rename(srcPath, destPath);
          if (srcName.endsWith('.spr')) {
            const oldSprv = sprPathToSprvPath(srcPath);
            const newSprv = sprPathToSprvPath(destPath);
            if (await window.spraute.exists(oldSprv)) {
              await window.spraute.rename(oldSprv, newSprv);
            }
          }
          await reloadFileTree();
          setStatus(`Перемещено: ${srcName} → ${item.name}/`);
        } catch (err) {
          appAlert(`Ошибка перемещения: ${err.message}`);
        }
      }
    });
  }
}

// Загрузка дерева файлов (рекурсивная реализация)
function collectExpandedFolderPaths() {
  const paths = new Set();
  const tree = document.getElementById('file-tree');
  if (!tree) return paths;
  tree.querySelectorAll('[data-file-path]').forEach(el => {
    const wrapper = el.parentElement;
    if (!wrapper || wrapper.children.length < 2) return;
    const childrenContainer = wrapper.children[1];
    if (childrenContainer && !childrenContainer.classList.contains('hidden')) {
      const path = el.dataset.filePath;
      if (path) paths.add(path);
    }
  });
  return paths;
}

async function loadDirectory(relPath, containerEl = null, level = 0, forceExpand = false, expandedSet = null, opts = {}) {
  const silent = opts.silent === true;
  const treeContainer = containerEl || document.getElementById('file-tree');
  if (!containerEl) {
    if (!silent) {
      treeContainer.innerHTML = '<div class="text-center text-on-variant mt-4 animate-pulse">Загрузка...</div>';
    } else {
      treeContainer.innerHTML = '';
    }
  }
  
  try {
    const rawItems = await window.spraute.listDir(relPath);
    const items = rawItems
      .filter(i => !i.name.endsWith('.sprv'))
      .filter(i => (relPath || '') !== '' || i.name !== 'plugins');
    if (!containerEl) treeContainer.innerHTML = '';
    
    if (items.length === 0 && !containerEl) {
      treeContainer.innerHTML = '<div class="px-4 py-2 text-on-variant italic">Пусто</div>';
      return;
    }

    // Если папка содержит только 1 элемент и это тоже папка — раскрываем автоматически (или если forceExpand)
    const shouldAutoExpand = items.length === 1 && items[0].isDir;

    for (const item of items) {
      const shouldExpandThis = forceExpand || shouldAutoExpand || (expandedSet && expandedSet.has(item.rel));
      const wrapper = document.createElement('div');
      
      const el = document.createElement('div');
      el.className = 'flex items-center gap-2 py-1.5 hover:bg-white/5 cursor-pointer text-on-surface hover:text-white transition-colors group';
      el.style.paddingLeft = `${(level * 16) + 16}px`;
      el.style.paddingRight = '16px';
      el.dataset.filePath = item.rel;
      
      let icon = 'draft';
      let iconColor = 'text-on-variant';
      let isFilled = 0;
      
      let isVisualInTree = false;
      if (item.isDir) {
        icon = 'folder';
        iconColor = 'text-secondary';
        isFilled = 1;
        
        // Кастомные иконки для базовых папок (только в корне)
        if (level === 0) {
          if (item.name === 'geo') { icon = 'view_in_ar'; iconColor = 'text-blue-400'; }
          else if (item.name === 'animations') { icon = 'animation'; iconColor = 'text-pink-400'; }
          else if (item.name === 'textures') { icon = 'image'; iconColor = 'text-yellow-400'; }
          else if (item.name === 'scripts') { icon = 'code_blocks'; iconColor = 'text-primary'; }
          else if (item.name === 'plugins') { icon = 'extension'; iconColor = 'text-emerald-400'; }
        }
      } else {
        if (item.name.endsWith('.spr')) {
          isVisualInTree = await window.spraute.exists(sprPathToSprvPath(item.rel));
          if (isVisualInTree) {
            icon = 'widgets';
            iconColor = 'text-secondary';
            isFilled = 1;
          } else {
            icon = 'description';
            iconColor = 'text-primary';
            isFilled = 1;
          }
        } else if (item.name.endsWith('.json')) { icon = 'data_object'; iconColor = 'text-yellow-200'; }
        else if (item.name.endsWith('.png')) { icon = 'image'; iconColor = 'text-purple-300'; }
      }
      
      const nameClass = isVisualInTree
        ? 'truncate flex-1 text-secondary font-medium'
        : 'truncate flex-1';
      
      el.innerHTML = `
        <span class="material-symbols-outlined text-[16px] ${iconColor} transition-transform ${item.isDir ? 'group-hover:scale-110' : ''}" style="font-variation-settings: 'FILL' ${isFilled}">${icon}</span>
        <span class="${nameClass}">${item.name}</span>
      `;
      
      const childrenContainer = document.createElement('div');
      childrenContainer.className = 'hidden flex-col';
      
      wrapper.appendChild(el);
      wrapper.appendChild(childrenContainer);
      treeContainer.appendChild(wrapper);

      // Раскрытие папки
      if (item.isDir) {
        let isLoaded = false;
        
        const toggleFolder = async () => {
          const isHidden = childrenContainer.classList.contains('hidden');
          if (isHidden) {
            childrenContainer.classList.remove('hidden');
            el.querySelector('.material-symbols-outlined').style.transform = 'rotate(90deg)';
            const needsReload = !isLoaded || (opts.refresh && expandedSet && expandedSet.has(item.rel));
            if (needsReload) {
              childrenContainer.innerHTML = '';
              await loadDirectory(item.rel, childrenContainer, level + 1, shouldAutoExpand, expandedSet, opts);
              isLoaded = true;
            }
          } else {
            childrenContainer.classList.add('hidden');
            el.querySelector('.material-symbols-outlined').style.transform = '';
          }
        };

        el.addEventListener('click', async (e) => {
          e.stopPropagation();
          document.querySelectorAll('.bg-primary\\/10').forEach(n => n.classList.remove('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary'));
          el.classList.add('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary');
          selectedItemPath = item.rel;
          selectedItemIsDir = true;
          await toggleFolder();
        });

        // Авто-открытие (если 1 папка внутри или была раскрыта до обновления)
        if (shouldExpandThis) {
          await toggleFolder();
        }
      } else {
        // Клик по файлу
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          // Подсветка активного файла
          document.querySelectorAll('.bg-primary\\/10').forEach(n => n.classList.remove('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary'));
          el.classList.add('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary');
          selectedItemPath = item.rel;
          selectedItemIsDir = false;
          
          openFile(item.rel, item.name);
        });
      }

      // Контекстное меню
      el.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        e.stopPropagation();
        showContextMenu(e.pageX, e.pageY, item.rel, item.isDir, wrapper);
      });
      
      // Drag and Drop
      setupDragAndDrop(el, item, wrapper);
    }
  } catch (err) {
    if (!containerEl) treeContainer.innerHTML = `<div class="px-4 py-2 text-red-400">Ошибка: ${err.message}</div>`;
  }
}

function highlightFileInTree(filePath) {
  if (!filePath) return;
  const tree = document.getElementById('file-tree');
  if (!tree) return;
  tree.querySelectorAll('.bg-primary\\/10').forEach(n => n.classList.remove('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary'));
  const target = tree.querySelector(`[data-file-path="${CSS.escape(filePath)}"]`);
  if (target) {
    target.classList.add('bg-primary/10', 'text-primary', 'border-l-2', 'border-primary');
    selectedItemPath = filePath;
    selectedItemIsDir = false;
  }
}

async function openFile(relPath, fileName) {
  let tab = openTabs.find(t => t.path === relPath);
  if (!tab) {
    const isImage = fileName.toLowerCase().endsWith('.png') || fileName.toLowerCase().endsWith('.jpg') || fileName.toLowerCase().endsWith('.jpeg');
    const isVisualScript = !isImage && relPath.endsWith('.spr') && await isVisualScriptPath(relPath);
    tab = {
      path: relPath,
      name: fileName,
      isImage: isImage,
      isVisualScript,
      isDirty: false,
      state: null,
      visualWorkspaceXml: null
    };
    openTabs.push(tab);
  } else if (relPath.endsWith('.spr') && tab.isVisualScript === undefined) {
    tab.isVisualScript = await isVisualScriptPath(relPath);
  }
  await switchToTab(relPath);
}

// Глобальный экземпляр редактора
let currentEditor = null;
let currentVisualCodeEditor = null;
let currentOpenFile = null;
let blocklyWorkspace = null;
let _visualCodeSyncTimer = null;
let _suppressDirty = false;

const EMPTY_BLOCKLY_XML = '<xml xmlns="https://developers.google.com/blockly/xml"></xml>';

function sprPathToSprvPath(sprPath) {
  return sprPath.replace(/\.spr$/i, '.sprv');
}

async function isVisualScriptPath(sprPath) {
  if (!window.spraute || !sprPath.endsWith('.spr')) return false;
  return await window.spraute.exists(sprPathToSprvPath(sprPath));
}

function captureVisualWorkspaceXml() {
  if (!blocklyWorkspace) return EMPTY_BLOCKLY_XML;
  return Blockly.Xml.domToText(Blockly.Xml.workspaceToDom(blocklyWorkspace));
}

function pruneBlocklyXmlFilledSlots(xmlDom) {
  const blocks = xmlDom.getElementsByTagName('block');
  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const mutation = block.getElementsByTagName('mutation')[0];
    if (!mutation) continue;
    const vNames = [];
    for (let a = 0; a < mutation.attributes.length; a++) {
      const attr = mutation.attributes[a];
      if (attr.name.startsWith('v_')) vNames.push(attr.name.slice(2));
    }
    if (!vNames.length) continue;
    vNames.sort((a, b) => {
      const na = parseInt(String(a).replace(/\D/g, ''), 10);
      const nb = parseInt(String(b).replace(/\D/g, ''), 10);
      if (!Number.isNaN(na) && !Number.isNaN(nb) && na !== nb) return na - nb;
      return String(a).localeCompare(String(b));
    });
    let lastOk = -1;
    for (let k = 0; k < vNames.length; k++) {
      const n = vNames[k];
      const valEl = [...block.getElementsByTagName('value')].find((el) => el.getAttribute('name') === n);
      const hasChild = valEl && (
        valEl.getElementsByTagName('block').length > 0
        || valEl.getElementsByTagName('shadow').length > 0
      );
      const flagged = mutation.getAttribute(`v_${n}`) === 'true';
      if (hasChild || flagged) {
        lastOk = k;
        mutation.setAttribute(`v_${n}`, 'true');
      } else {
        break;
      }
    }
    for (let k = lastOk + 1; k < vNames.length; k++) {
      mutation.removeAttribute(`v_${vNames[k]}`);
    }
  }
}

function augmentBlocklyXmlFilledSlots(xmlDom) {
  const blocks = xmlDom.getElementsByTagName('block');
  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const values = block.getElementsByTagName('value');
    if (!values.length) continue;
    let mutation = block.getElementsByTagName('mutation')[0];
    for (let j = 0; j < values.length; j++) {
      const valEl = values[j];
      const name = valEl.getAttribute('name');
      if (!name) continue;
      const hasChild = valEl.getElementsByTagName('block').length > 0
        || valEl.getElementsByTagName('shadow').length > 0;
      if (!hasChild) continue;
      if (!mutation) {
        mutation = xmlDom.ownerDocument
          ? xmlDom.ownerDocument.createElement('mutation')
          : Blockly.utils.xml.createElement('mutation');
        block.insertBefore(mutation, block.firstChild);
      }
      if (mutation.getAttribute(`v_${name}`) !== 'true') {
        mutation.setAttribute(`v_${name}`, 'true');
      }
    }
  }
  pruneBlocklyXmlFilledSlots(xmlDom);
}

function restoreVisualWorkspaceFromXml(xmlText) {
  if (!blocklyWorkspace || !xmlText) return;
  const prev = _suppressDirty;
  _suppressDirty = true;
  try {
    blocklyWorkspace._sprauteRestoringBlocks = true;
    blocklyWorkspace.clear();
    const xml = Blockly.utils.xml.textToDom(xmlText);
    const preloadNpcIds = extractNpcCreateIdsFromBlocklyXml(xml);
    const preloadList = buildNpcDropdownIds(null, VisualEngine._cachedImportedNpcs);
    for (const id of preloadNpcIds) {
      if (!preloadList.includes(id)) preloadList.push(id);
    }
    if (preloadList.length) {
      updateDynamicLists(
        preloadList,
        VisualEngine._cachedAnims,
        VisualEngine._cachedModels,
        VisualEngine._cachedTextures,
        VisualEngine._cachedAnimFiles,
        VisualEngine._cachedAnimsByFile
      );
    }
    augmentBlocklyXmlFilledSlots(xml);
    const missingBlockTypes = prepareBlocklyXmlForLoad(xml);
    Blockly.Xml.domToWorkspace(xml, blocklyWorkspace);
    hideVisualLoadError();
    if (missingBlockTypes.length) {
      showVisualLoadError(
        'В скрипте есть блоки, которых нет в плагинах.',
        { missingBlocks: missingBlockTypes }
      );
    }
    for (const block of blocklyWorkspace.getAllBlocks(false)) {
      if (typeof block.syncValFromFields_ === 'function') block.syncValFromFields_();
    }
    for (const block of blocklyWorkspace.getAllBlocks(false)) {
      if (typeof block.pruneFilledWatchState_ === 'function') block.pruneFilledWatchState_();
    }
    for (const block of blocklyWorkspace.getAllBlocks(false)) {
      if (typeof block.updateShape_ !== 'function') continue;
      const filled = block.filledWatchFields_?.length || 0;
      const conds = block.conditionVars_?.length || 0;
      // if/while без ветвления по полям: слоты уже созданы в init, пересборка сотрёт cond/DO
      if (block._sprauteShape_ === 'wrapper' && filled === 0 && conds === 0) continue;
      // domToMutation уже вызвал updateShape_; повторная пересборка отваливает value «указать»
      if (filled === 0 && conds > 0 && block._sprauteShape_ !== 'wrapper') continue;
      const wasCollapsed = typeof block.isCollapsed === 'function' && block.isCollapsed();
      if (wasCollapsed) {
        block._sprauteReshapeWhenExpanded_ = false;
        block.setCollapsed(false);
      }
      block.updateShape_();
      if (wasCollapsed) block.setCollapsed(true);
    }
    refreshDynamicDropdownFields(blocklyWorkspace);
    VisualEngine._cachedNpcs = syncNpcDropdownsFromWorkspace(blocklyWorkspace, {
      importedNpcIds: VisualEngine._cachedImportedNpcs,
      anims: VisualEngine._cachedAnims,
      models: VisualEngine._cachedModels,
      textures: VisualEngine._cachedTextures,
      animFiles: VisualEngine._cachedAnimFiles,
      animsByFile: VisualEngine._cachedAnimsByFile
    });
  } finally {
    if (blocklyWorkspace) {
      for (const block of blocklyWorkspace.getAllBlocks(false)) {
        if (typeof block.pruneFilledWatchState_ === 'function') block.pruneFilledWatchState_();
      }
      blocklyWorkspace._sprauteRestoringBlocks = false;
    }
    _suppressDirty = prev;
  }
}

async function readSprvFile(sprPath) {
  const raw = await window.spraute.readFile(sprPathToSprvPath(sprPath), 'utf8');
  const data = JSON.parse(raw);
  return data.blocklyXml || EMPTY_BLOCKLY_XML;
}

async function writeSprvFile(sprPath, xmlText) {
  const payload = {
    version: 1,
    spr: sprPath,
    blocklyXml: xmlText || EMPTY_BLOCKLY_XML
  };
  await window.spraute.writeFile(sprPathToSprvPath(sprPath), JSON.stringify(payload, null, 2));
}

async function createVisualScriptPair(sprPath, initialCode = '# Визуальный скрипт\n') {
  await window.spraute.writeFile(sprPath, initialCode);
  await writeSprvFile(sprPath, EMPTY_BLOCKLY_XML);
}

function setVisualEditorVisible(visible) {
  const layout = document.getElementById('visual-editor-layout');
  const editorMount = document.getElementById('editor-mount');
  if (visible) {
    layout?.classList.remove('hidden');
    if (layout) layout.style.display = 'flex';
    editorMount?.classList.add('hidden');
    if (editorMount) editorMount.style.display = 'none';
    document.body.classList.add('visual-mode-active');
    document.querySelectorAll('.blocklyToolboxDiv').forEach(el => {
      if (el) { el.style.display = ''; el.style.width = ''; el.style.height = ''; el.style.overflow = ''; }
    });
  } else {
    hideVisualLoadError();
    layout?.classList.add('hidden');
    if (layout) layout.style.display = 'none';
    editorMount?.classList.remove('hidden');
    if (editorMount) editorMount.style.display = 'block';
    document.body.classList.remove('visual-mode-active');
    Blockly.hideChaff();
    document.querySelectorAll('.blocklyWidgetDiv, .blocklyTooltipDiv, .blocklyDropDownDiv, .blocklyToolboxDiv').forEach(el => {
      if (el) { el.style.display = 'none'; el.style.left = '0'; el.style.top = '0'; el.style.width = '0'; el.style.height = '0'; el.style.overflow = 'hidden'; }
    });
  }
}

async function syncVisualCodePreview() {
  if (!blocklyWorkspace) return;
  const editor = currentVisualCodeEditor;
  if (!editor) return;
  let code;
  try {
    code = generateWorkspaceCode(blocklyWorkspace);
    code = await injectPluginGlobals(code);
  } catch (e) {
    console.warn('[Spraute] syncVisualCodePreview:', e);
    return;
  }
  if (currentVisualCodeEditor !== editor) return;
  try {
    const doc = editor.state.doc;
    if (doc.toString() === code) return;
    editor.dispatch({
      changes: { from: 0, to: doc.length, insert: code },
      annotations: Transaction.addToHistory.of(false)
    });
  } catch (e) {
    console.warn('[Spraute] syncVisualCodePreview dispatch:', e);
  }
}

function destroyVisualCodeEditor() {
  if (_visualCodeSyncTimer) {
    clearTimeout(_visualCodeSyncTimer);
    _visualCodeSyncTimer = null;
  }
  if (currentVisualCodeEditor) {
    currentVisualCodeEditor.destroy();
    currentVisualCodeEditor = null;
  }
}

async function mountVisualCodeEditor(content) {
  destroyVisualCodeEditor();
  const mount = document.getElementById('visual-code-mount');
  if (!mount) return;
  mount.innerHTML = '';
  const wordWrap = await window.spraute?.storeGet('editorWordWrap');
  const syntaxThemeName = await window.spraute?.storeGet('editorSyntaxTheme') || 'vscode-dark';
  const themeConfig = buildCodeMirrorEditorTheme('0.75rem 0');
  const customHighlightStyle = HighlightStyle.define(getSyntaxHighlightStyles(syntaxThemeName));
  const extensions = [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightActiveLine(),
    drawSelection(),
    themeConfig,
    EditorState.readOnly.of(true),
    sprauteLanguageSupport,
    syntaxHighlighting(customHighlightStyle)
  ];
  if (wordWrap !== false) extensions.push(EditorView.lineWrapping);
  currentVisualCodeEditor = new EditorView({
    state: EditorState.create({ doc: content || '', extensions }),
    parent: mount
  });
}

// ====== Система быстрого переключения визуального режима ======
const VisualEngine = {
  _ready: false,
  _blocksCached: false,
  _dynamicDataCached: false,
  _cachedNpcs: [],
  _cachedImportedNpcs: [],
  _cachedAnims: [],
  _cachedAnimsByFile: {},
  _cachedAnimFiles: [],
  _cachedModels: [],
  _cachedTextures: [],
  _scanPromise: null,
  _assetRescanPromise: null,
  _initPromise: null,
  _lastScanText: '',

  // Инициализация Blockly workspace заранее (скрытый)
  async ensureWorkspace() {
    if (blocklyWorkspace) return blocklyWorkspace;
    if (this._initPromise) return this._initPromise;

    this._initPromise = new Promise((resolve) => {
      const blocklyMount = document.getElementById('blockly-mount');
      if (!blocklyMount) { resolve(null); return; }

      const visualLayout = document.getElementById('visual-editor-layout');
      const wasHidden = visualLayout && visualLayout.classList.contains('hidden');
      if (wasHidden) {
        visualLayout.style.position = 'absolute';
        visualLayout.style.left = '-9999px';
        visualLayout.style.top = '-9999px';
        visualLayout.style.width = '800px';
        visualLayout.style.height = '600px';
        visualLayout.classList.remove('hidden');
        visualLayout.style.display = 'flex';
      }

      blocklyWorkspace = Blockly.inject('blockly-mount', {
        toolbox: getDynamicToolbox(),
        theme: SprauteTheme,
        grid: { spacing: 20, length: 0, snap: true },
        move: { scrollbars: true, drag: true, wheel: true },
        sounds: false,
        collapse: true,
        comments: true,
        disable: false,
        trashcan: true,
        horizontalLayout: false,
        toolboxPosition: 'start',
        css: true,
        zoom: { controls: true, wheel: false, startScale: 1.0, maxScale: 2, minScale: 0.3, scaleSpeed: 1.2 }
      });

      blocklyWorkspace.addChangeListener((e) => {
        if (_suppressDirty) return;
        if (e.isUiEvent) return;
        if (e.type === Blockly.Events.BLOCK_MOVE &&
            e.newParentId === e.oldParentId &&
            e.newInputName === e.oldInputName) return;
        const tab = openTabs.find(t => t.path === activeTabPath);
        if (!tab || !tab.isVisualScript) return;
        clearTimeout(_visualCodeSyncTimer);
        _visualCodeSyncTimer = setTimeout(() => {
          syncVisualCodePreview().catch(e => console.warn('[Spraute] visual code sync:', e));
          VisualEngine.syncNpcsFromWorkspace();
          if (!tab.isDirty) {
            tab.isDirty = true;
            renderTabs();
          }
        }, 150);
      });

      attachDynamicBlockReshapeListener(blocklyWorkspace);

      // Кастомное контекстное меню (position: fixed, не обрезается overflow: hidden)
      attachBlocklyContextMenu(blocklyWorkspace);

      // Блоки по умолчанию раскрыты (многострочные). Сворачивание — вручную через ПКМ.

      // Flyout scrollbar sync
      function syncFlyoutScrollbar() {
        const flyout = blocklyWorkspace.getFlyout ? blocklyWorkspace.getFlyout() : null;
        const isFlyoutOpen = flyout && flyout.isVisible();
        document.querySelectorAll('#blockly-mount svg.blocklyFlyoutScrollbar').forEach(sb => {
          sb.classList.toggle('flyout-scrollbar-visible', !!isFlyoutOpen);
        });
      }
      blocklyWorkspace.addChangeListener((e) => {
        if (e.type === Blockly.Events.TOOLBOX_ITEM_SELECT) {
          syncFlyoutScrollbar();
          setTimeout(syncFlyoutScrollbar, 50);
        }
      });

      if (wasHidden && visualLayout) {
        visualLayout.classList.add('hidden');
        visualLayout.style.display = 'none';
        visualLayout.style.position = '';
        visualLayout.style.left = '';
        visualLayout.style.top = '';
        visualLayout.style.width = '';
        visualLayout.style.height = '';
      }

      this._ready = true;
      resolve(blocklyWorkspace);
    });

    return this._initPromise;
  },

  // Фоновое сканирование — кэширует NPC, анимации и кастомные блоки
  async scanInBackground(currentText) {
    if (currentText === this._lastScanText && this._dynamicDataCached) return;
    if (this._scanPromise) return this._scanPromise;
    
    this._scanPromise = this._doScan(currentText);
    try { await this._scanPromise; } finally { this._scanPromise = null; }
  },

  async _collectImportedNpcIds(currentText) {
    const ids = new Set();
    if (!window.spraute || !currentText) return [];
    const importPatterns = [
      /import\s*\(\s*["']([^"']+)["']\s*\)/g,
      /import\s+["']([^"']+)["']/g
    ];
    const queue = [];
    for (const re of importPatterns) {
      re.lastIndex = 0;
      let m;
      while ((m = re.exec(currentText)) !== null) queue.push(m[1]);
    }
    const visited = new Set();
    while (queue.length > 0) {
      const f = queue.shift();
      if (visited.has(f)) continue;
      visited.add(f);
      try {
        const p = f.endsWith('.spr') ? f : `scripts/${f}.spr`;
        const content = await window.spraute.readFile(p, 'utf8');
        for (const id of extractCreateNpcIdsFromSpr(content)) ids.add(id);
        for (const re of importPatterns) {
          re.lastIndex = 0;
          let m2;
          while ((m2 = re.exec(content)) !== null) queue.push(m2[1]);
        }
      } catch (e) {}
    }
    return [...ids];
  },

  async _scanAssetsFromDisk() {
    let anims = [];
    let animFiles = [];
    let models = [];
    let textures = [];
    const animsByFile = {};

    if (!window.spraute) {
      return { anims, animFiles, models, textures, animsByFile };
    }

    try {
      const animFilesList = await window.spraute.listDir('animations');
      for (const f of animFilesList) {
        if (!f.isDir && f.name.endsWith('.json')) {
          const animPath = f.rel.replace(/\\/g, '/');
          if (!animFiles.includes(animPath)) animFiles.push(animPath);
          try {
            const content = await window.spraute.readFile(f.rel, 'utf8');
            const json = JSON.parse(content);
            if (json.animations) {
              const clipNames = [];
              for (const aName in json.animations) {
                clipNames.push(aName);
                if (!anims.includes(aName)) anims.push(aName);
              }
              animsByFile[animPath] = clipNames;
            }
          } catch (e) {}
        }
      }

      async function collectGeoModels(dir) {
        const seen = new Set();
        try {
          const files = await window.spraute.listDir(dir);
          for (const f of files) {
            if (f.isDir) {
              await collectGeoModels(f.rel);
            } else if (isGeoModelFileName(f.name)) {
              const modelPath = f.rel.replace(/\\/g, '/');
              const key = modelPath.toLowerCase();
              if (!seen.has(key)) {
                seen.add(key);
                models.push(modelPath);
              }
            }
          }
        } catch (e) {}
      }
      await collectGeoModels('geo');

      async function collectTextures(dir) {
        try {
          const files = await window.spraute.listDir(dir);
          for (const f of files) {
            if (f.isDir) {
              await collectTextures(f.rel);
            } else if (/\.(png|jpg|jpeg)$/i.test(f.name)) {
              const texPath = f.rel.replace(/\\/g, '/');
              if (!textures.includes(texPath)) textures.push(texPath);
            }
          }
        } catch (e) {}
      }
      await collectTextures('textures');
    } catch (e) {}

    return { anims, animFiles, models, textures, animsByFile };
  },

  _applyAssetScanResult(assets, npcs) {
    let { anims, animFiles, models, textures, animsByFile } = assets;
    if (anims.length === 0) anims.push('(нет анимаций)');
    if (animFiles.length === 0) animFiles.push('animations/npc_classic.animation.json');
    if (models.length === 0) models.push('geo/defolt.geo.json');
    if (textures.length === 0) textures.push('textures/entity/defolt.png');
    if (!animsByFile) animsByFile = {};

    this._cachedAnims = anims;
    this._cachedAnimFiles = animFiles;
    this._cachedModels = models;
    this._cachedTextures = textures;
    this._cachedAnimsByFile = animsByFile;
    updateDynamicLists(npcs, anims, models, textures, animFiles, animsByFile);
    if (blocklyWorkspace) {
      onSprauteAnimContextChanged(blocklyWorkspace);
    }
  },

  /** Перечитать geo/animations/textures с диска и обновить dropdown (без перезапуска). */
  async rescanAssets() {
    if (this._assetRescanPromise) return this._assetRescanPromise;
    this._assetRescanPromise = (async () => {
      const assets = await this._scanAssetsFromDisk();
      const npcs = this._cachedNpcs?.length ? this._cachedNpcs : ['_eventNpc'];
      this._applyAssetScanResult(assets, npcs);
    })();
    try {
      await this._assetRescanPromise;
    } finally {
      this._assetRescanPromise = null;
    }
  },

  async _doScan(currentText) {
    const assets = await this._scanAssetsFromDisk();

    let importedNpcIds = await this._collectImportedNpcIds(currentText);
    const localNpcIds = new Set(extractCreateNpcIdsFromSpr(currentText));
    if (blocklyWorkspace) {
      for (const id of extractNpcIdsFromWorkspace(blocklyWorkspace)) localNpcIds.add(id);
      try {
        for (const id of extractCreateNpcIdsFromSpr(generateWorkspaceCode(blocklyWorkspace))) localNpcIds.add(id);
      } catch (e) {}
    }
    const npcs = [...new Set([...importedNpcIds, ...localNpcIds])];

    this._cachedImportedNpcs = importedNpcIds;
    this._cachedNpcs = npcs;
    this._applyAssetScanResult(assets, npcs);
    this._dynamicDataCached = true;
    this._lastScanText = currentText;
  },

  // Предзагрузка блоков плагинов — вызывается при старте и при включении/выключении плагина
  async preloadPluginBlocks() {
    if (!window.spraute) return;
    try {
      clearCustomCategories();
      const pluginsExists = await window.spraute.exists('plugins');
      if (!pluginsExists) return;
      
      const plugins = await window.spraute.listDir('plugins');
      for (const p of plugins) {
        if (!p.isDir) continue;
        const pluginJsonPath = `${p.rel}/plugin.json`;
        if (!(await window.spraute.isFile(pluginJsonPath))) continue;
        let isEnabled = true;
        try {
          const pData = JSON.parse(await window.spraute.readFile(pluginJsonPath, 'utf8'));
          if (pData.enabled === false) isEnabled = false;
        } catch(e){}
        if (!isEnabled) continue;

        const bPath = `plugins/${p.name}/blocks`;
        const bExists = await window.spraute.exists(bPath);
        if (!bExists) continue;

        const ns = p.name.toLowerCase().replace(/[^a-z0-9_]/g, '_');
        let catData = null;
        try {
          const catPath = `plugins/${p.name}/categories.json`;
          if (await window.spraute.exists(catPath)) {
            catData = JSON.parse(await window.spraute.readFile(catPath, 'utf8'));
            registerPluginCategoryOrder(Object.keys(catData));
            for (const [catName, catColor] of Object.entries(catData)) {
              if (!customCategories[catName]) {
                customCategories[catName] = { color: catColor, blocks: [] };
              } else {
                customCategories[catName].color = catColor;
              }
            }
          }
        } catch (e) {}

        const files = await window.spraute.listDir(bPath);
        for (const f of files) {
          if (!f.isDir && f.name.endsWith('.spr')) {
            const bText = await window.spraute.readFile(f.rel, 'utf8');
            parseCustomBlocks(bText, ns);
          }
        }

        if (catData) applyPluginCategoryColors(catData);

        try {
          const orderPath = `plugins/${p.name}/blocks_order.json`;
          if (await window.spraute.exists(orderPath)) {
            const orderData = JSON.parse(await window.spraute.readFile(orderPath, 'utf8'));
            sortPluginBlocks(ns, orderData);
          }
        } catch (e) {}
      }
      this._blocksCached = true;

      // Обновляем тулбокс если workspace уже есть
      if (blocklyWorkspace) {
        blocklyWorkspace.updateToolbox(getDynamicToolbox());
      }
    } catch(err) {
      console.error("[VisualEngine] Plugin block preload error:", err);
    }
  },

  // Запуск фоновой подготовки всего
  // Полный прогрев с колбэком прогресса
  async warmUp(currentText, onProgress) {
    const steps = [
      { label: 'Загрузка блоков плагинов...', fn: () => this.preloadPluginBlocks() },
      { label: 'Сканирование проекта...', fn: () => this.scanInBackground(currentText || '') },
      { label: 'Инициализация визуального редактора...', fn: () => this.ensureWorkspace() }
    ];
    for (let i = 0; i < steps.length; i++) {
      if (onProgress) onProgress(steps[i].label, (i / steps.length) * 100);
      await steps[i].fn();
    }
    if (onProgress) onProgress('Готово', 100);
  },

  // Применить закэшированные данные + обновить тулбокс
  _lastToolboxJson: null,

  applyCache() {
    if (this._dynamicDataCached) {
      updateDynamicLists(this._cachedNpcs, this._cachedAnims, this._cachedModels, this._cachedTextures, this._cachedAnimFiles, this._cachedAnimsByFile);
    }
    if (blocklyWorkspace) {
      this.syncNpcsFromWorkspace();
    }
    if (blocklyWorkspace) {
      const toolbox = getDynamicToolbox();
      const json = JSON.stringify(toolbox);
      if (json !== this._lastToolboxJson) {
        this._lastToolboxJson = json;
        blocklyWorkspace.updateToolbox(toolbox);
      }
    }
  },

  _liveSyncTimer: null,

  scheduleLiveSync(text) {
    if (this._liveSyncTimer) clearTimeout(this._liveSyncTimer);
    this._liveSyncTimer = setTimeout(() => {
      this._liveSyncTimer = null;
      if (text !== this._lastScanText) {
        this.scanInBackground(text);
      }
    }, 2000);
  },

  syncNpcsFromWorkspace() {
    if (!blocklyWorkspace) return;
    const run = async () => {
      try {
        const code = generateWorkspaceCode(blocklyWorkspace);
        this._cachedImportedNpcs = await this._collectImportedNpcIds(code);
      } catch (e) {}
      this._cachedNpcs = syncNpcDropdownsFromWorkspace(blocklyWorkspace, {
        importedNpcIds: this._cachedImportedNpcs,
        anims: this._cachedAnims,
        models: this._cachedModels,
        textures: this._cachedTextures,
        animFiles: this._cachedAnimFiles,
        animsByFile: this._cachedAnimsByFile
      });
    };
    run();
  }
};

let _workspaceRefreshTimer = null;

async function refreshFileTreeRealtime() {
  const expanded = collectExpandedFolderPaths();
  const selected = selectedItemPath;
  await loadDirectory('', null, 0, false, expanded, { silent: true, refresh: true });
  if (selected) highlightFileInTree(selected);
  await VisualEngine.rescanAssets();
}

function scheduleWorkspaceRefresh() {
  clearTimeout(_workspaceRefreshTimer);
  _workspaceRefreshTimer = setTimeout(() => {
    refreshFileTreeRealtime().catch(() => {});
  }, 250);
}

async function reloadFileTree() {
  const expanded = collectExpandedFolderPaths();
  await loadDirectory('', null, 0, false, expanded, { refresh: true });
  await VisualEngine.rescanAssets();
}

function setupAssetRescanListeners() {
  if (window._assetRescanListenersSetup) return;
  window._assetRescanListenersSetup = true;
  window.addEventListener('focus', () => scheduleWorkspaceRefresh());
  if (window.spraute?.onAssetsChanged) {
    window.spraute.onAssetsChanged(() => scheduleWorkspaceRefresh());
  }
}

// Вкладки
let openTabs = []; // { path: string, name: string, isImage: boolean, isDirty: boolean, state: EditorState|null }
let activeTabPath = null;

function renderTabs() {
  const container = document.getElementById('tabs-container');
  if (openTabs.length === 0) {
    container.classList.add('hidden');
    document.getElementById('empty-state').classList.remove('hidden');
    document.getElementById('editor-mount').innerHTML = '';
    document.getElementById('editor-mount').classList.add('hidden');
    document.getElementById('editor-mount').style.display = 'none';
    
    const visualLayout = document.getElementById('visual-editor-layout');
    if (visualLayout) {
      visualLayout.classList.add('hidden');
      visualLayout.style.display = 'none';
    }
    
    destroyVisualCodeEditor();
    
    document.querySelectorAll('.blocklyWidgetDiv, .blocklyTooltipDiv, .blocklyDropDownDiv, .blocklyToolboxDiv').forEach(el => {
      if (el) {
        el.style.display = 'none';
        el.style.left = '0';
        el.style.top = '0';
        el.style.width = '0';
        el.style.height = '0';
        el.style.overflow = 'hidden';
      }
    });

    if (currentEditor) {
      currentEditor.destroy();
      currentEditor = null;
    }
    currentOpenFile = null;
    activeTabPath = null;
    document.body.classList.remove('visual-mode-active');
    return;
  }
  
  container.classList.remove('hidden');
  document.getElementById('empty-state').classList.add('hidden');
  
  container.innerHTML = '';
  openTabs.forEach(tab => {
    const tabEl = document.createElement('div');
    const isActive = tab.path === activeTabPath;
    
    tabEl.className = `h-full flex items-center px-4 gap-2 cursor-pointer border-r border-white/5 transition-colors max-w-[200px] shrink-0
      ${isActive ? 'bg-background text-primary border-t-2 border-t-primary' : 'bg-surface-container text-on-variant hover:bg-surface-bright border-t-2 border-t-transparent'}
      ${tab.isVisualScript && !isActive ? 'text-secondary' : ''}`;
    
    // Иконка
    let icon = 'description';
    let isFilled = isActive ? 1 : 0;
    if (tab.isVisualScript) {
      icon = 'widgets';
      isFilled = 1;
    }
    if (tab.name.endsWith('.png') || tab.name.endsWith('.jpg') || tab.name.endsWith('.jpeg')) icon = 'image';
    else if (tab.name.endsWith('.json')) icon = 'data_object';
    
    // Грязный маркер
    const dirtyMarker = tab.isDirty ? '<div class="w-2 h-2 rounded-full bg-white ml-1"></div>' : '';
    
    tabEl.innerHTML = `
      <span class="material-symbols-outlined text-[16px]" style="font-variation-settings: 'FILL' ${isFilled}">${icon}</span>
      <span class="truncate text-xs ${isActive ? 'font-medium' : ''}" title="${tab.path}">${tab.name}</span>
      ${dirtyMarker}
      <button class="ml-auto w-5 h-5 rounded-md flex items-center justify-center hover:bg-white/10 opacity-50 hover:opacity-100 transition-all tab-close-btn">
        <span class="material-symbols-outlined text-[14px]">close</span>
      </button>
    `;
    
    // Клик по вкладке
    tabEl.addEventListener('click', (e) => {
      if (e.target.closest('.tab-close-btn')) return;
      if (tab.path !== activeTabPath) {
        switchToTab(tab.path);
      }
    });
    
    // Клик по закрытию
    const closeBtn = tabEl.querySelector('.tab-close-btn');
    closeBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      await closeTab(tab.path);
    });
    
    container.appendChild(tabEl);
  });
}

async function closeTab(path) {
  const tabIndex = openTabs.findIndex(t => t.path === path);
  if (tabIndex === -1) return;
  const tab = openTabs[tabIndex];

  if (path === activeTabPath) {
    if (tab.isVisualScript && blocklyWorkspace) {
      tab.visualWorkspaceXml = captureVisualWorkspaceXml();
    } else if (currentEditor) {
      tab.state = currentEditor.state;
    }
  }

  if (tab.isDirty) {
    const confirm = await appConfirm(`Сохранить изменения в "${tab.name}" перед закрытием?`);
    if (confirm) {
      await saveTab(tab.path);
    }
  }

  openTabs.splice(tabIndex, 1);

  if (activeTabPath === path) {
    if (openTabs.length > 0) {
      // Переключаемся на предыдущую или первую
      const nextIndex = Math.max(0, tabIndex - 1);
      switchToTab(openTabs[nextIndex].path);
    } else {
      renderTabs();
    }
  } else {
    renderTabs();
  }
}

async function saveTab(path) {
  const tab = openTabs.find(t => t.path === path);
  if (!tab || tab.isImage || !tab.isDirty) return;
  
  try {
    if (tab.isVisualScript) {
      let code;
      let xml;
      if (path === activeTabPath && blocklyWorkspace) {
        code = generateWorkspaceCode(blocklyWorkspace);
        xml = captureVisualWorkspaceXml();
      } else if (tab.visualWorkspaceXml) {
        code = await window.spraute.readFile(path, 'utf8');
        xml = tab.visualWorkspaceXml;
      } else {
        return;
      }
      code = await injectPluginGlobals(code);
      await window.spraute.writeFile(path, code);
      await writeSprvFile(path, xml);
      tab.visualWorkspaceXml = xml;
    } else {
      let contentToSave;
      if (path === activeTabPath && currentEditor) {
        contentToSave = currentEditor.state.doc.toString();
      } else if (tab.state) {
        contentToSave = tab.state.doc.toString();
      } else {
        return;
      }
      await window.spraute.writeFile(path, contentToSave);
    }
    tab.isDirty = false;
    renderTabs();
    setStatus(`Сохранено: ${tab.name}`);
  } catch (e) {
    appAlert(`Ошибка при сохранении ${tab.name}: ${e.message}`);
  }
}

async function injectPluginGlobals(code) {
  if (!window.spraute) return code;
  let finalCode = code;
  let globalsToInject = "";
  
  for (const p of allPluginsData) {
    if (p.isEnabled) {
      try {
        const jsonContent = await window.spraute.readFile(`${p.path}/plugin.json`, 'utf8');
        const data = JSON.parse(jsonContent);
        if (data.global_scripts) {
          const scripts = data.global_scripts.split('\n');
          for (const s of scripts) {
            const trimmed = s.trim();
            if (trimmed && !finalCode.includes(trimmed)) {
              globalsToInject += trimmed + "\n";
            }
          }
        }
      } catch (e) {}
    }
  }
  
  if (globalsToInject) {
    finalCode = globalsToInject + "\n" + finalCode;
  }
  return finalCode;
}

async function saveActiveTab() {
  if (activeTabPath) {
    await saveTab(activeTabPath);
  }
}

// Слушатель Ctrl+S
document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'ы' || e.code === 'KeyS')) {
    e.preventDefault();
    saveActiveTab();
  }
});

let _switchTabLock = null;
async function switchToTab(path) {
  if (_switchTabLock) await _switchTabLock;
  let _resolve;
  _switchTabLock = new Promise(r => { _resolve = r; });

  try {
  if (activeTabPath) {
    const prevTab = openTabs.find(t => t.path === activeTabPath);
    if (prevTab && !prevTab.isImage) {
      if (prevTab.isVisualScript && blocklyWorkspace) {
        prevTab.visualWorkspaceXml = captureVisualWorkspaceXml();
      } else if (currentEditor) {
        prevTab.state = currentEditor.state;
      }
    }
  }

  activeTabPath = path;
  renderTabs();
  highlightFileInTree(path);
  
  const tab = openTabs.find(t => t.path === path);
  if (!tab) return;
  
  const editorMount = document.getElementById('editor-mount');
  
  if (currentEditor) {
    currentEditor.destroy();
    currentEditor = null;
  }
  destroyVisualCodeEditor();
  
  if (tab.isImage) {
    setVisualEditorVisible(false);
    editorMount.classList.remove('hidden');
    editorMount.style.display = 'block';
    try {
      const contentBase64 = await window.spraute.readFile(tab.path, 'base64');
      const ext = tab.name.split('.').pop().toLowerCase();
      editorMount.innerHTML = `
        <div id="img-container" class="w-full h-full flex items-center justify-center bg-black/20 overflow-hidden relative cursor-grab active:cursor-grabbing">
          <img id="img-view" src="data:image/${ext};base64,${contentBase64}" class="max-w-full max-h-full object-contain rounded shadow-lg border border-white/10 checkerboard-bg transition-transform duration-75" style="transform-origin: center;" />
        </div>
      `;
      currentOpenFile = null;

      // Логика зума и панорамирования
      const imgContainer = document.getElementById('img-container');
      const imgView = document.getElementById('img-view');
      let scale = 1;
      let isDragging = false;
      let startX, startY;
      let translateX = 0, translateY = 0;

      const updateTransform = () => {
        imgView.style.transform = `translate(${translateX}px, ${translateY}px) scale(${scale})`;
      };

      imgContainer.addEventListener('wheel', (e) => {
        e.preventDefault();
        const zoomIntensity = 0.1;
        if (e.deltaY < 0) scale += zoomIntensity;
        else scale -= zoomIntensity;
        scale = Math.min(Math.max(0.1, scale), 10); // Ограничение зума
        updateTransform();
      });

      imgContainer.addEventListener('mousedown', (e) => {
        isDragging = true;
        startX = e.clientX - translateX;
        startY = e.clientY - translateY;
      });

      window.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        translateX = e.clientX - startX;
        translateY = e.clientY - startY;
        updateTransform();
      });

      window.addEventListener('mouseup', () => {
        isDragging = false;
      });

    } catch (e) {
      editorMount.innerHTML = `<div class="p-4 text-red-400">Ошибка чтения изображения: ${e.message}</div>`;
    }
  } else if (tab.isVisualScript) {
    try {
      setVisualEditorVisible(true);
      currentOpenFile = path;
      window.__sprauteCurrentScriptPath = path;
      showVisualTransition(true, 'Загрузка визуального скрипта...');
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      await VisualEngine.ensureWorkspace();
      await VisualEngine.preloadPluginBlocks();
      const sprContent = await window.spraute.readFile(path, 'utf8');
      await VisualEngine.scanInBackground(sprContent);
      VisualEngine.applyCache();
      const xml = tab.visualWorkspaceXml || await readSprvFile(path);
      restoreVisualWorkspaceFromXml(xml);
      VisualEngine.syncNpcsFromWorkspace();
      await mountVisualCodeEditor(sprContent);
      await syncVisualCodePreview();
      if (blocklyWorkspace) Blockly.svgResize(blocklyWorkspace);
      const codeForScan = blocklyWorkspace ? generateWorkspaceCode(blocklyWorkspace) : sprContent;
      if (codeForScan !== sprContent) {
        await VisualEngine.scanInBackground(codeForScan);
        VisualEngine.applyCache();
        refreshDynamicDropdownFields(blocklyWorkspace);
      }
      showVisualTransition(false);
    } catch (e) {
      setVisualEditorVisible(false);
      editorMount.innerHTML = `<div class="p-4 text-red-400">Ошибка чтения визуального скрипта: ${e.message}</div>`;
      editorMount.classList.remove('hidden');
      editorMount.style.display = 'block';
    }
  } else {
    try {
      setVisualEditorVisible(false);
      window.__sprauteCurrentScriptPath = '';
      editorMount.classList.remove('hidden');
      editorMount.style.display = 'block';
      editorMount.innerHTML = '';
      currentOpenFile = path;
      
      const content = await window.spraute.readFile(path, 'utf8');
      
      const themeConfig = EditorView.theme({
        "&": {
          backgroundColor: "transparent",
          color: "var(--color-on-surface)",
          height: "100%",
          fontSize: "var(--editor-font-size, 14px)",
          fontFamily: "var(--font-mono)",
        },
        ".cm-scroller": {
          fontFamily: "var(--font-mono)",
          backgroundColor: "transparent !important"
        },
        ".cm-content": {
          fontFamily: "var(--font-mono)",
          padding: "1rem 0",
          paddingBottom: "50vh",
          backgroundColor: "transparent !important"
        },
        ".cm-gutters": {
          backgroundColor: "transparent",
          color: "var(--color-on-variant)",
          border: "none",
          borderRight: "1px solid rgba(255, 255, 255, 0.05)",
          paddingRight: "4px"
        },
        ".cm-gutters .cm-lineNumbers .cm-gutterElement": {
          color: "var(--color-on-variant)"
        },
        ".cm-activeLineGutter": {
          backgroundColor: "rgba(255,255,255,0.05)",
          color: "var(--color-on-variant)"
        },
        ".cm-activeLine": {
          backgroundColor: "rgba(255,255,255,0.03) !important"
        },
        ".cm-cursor": {
          borderLeftColor: "var(--color-primary)",
          borderLeftWidth: "2px"
        },
        "&.cm-focused .cm-selectionBackground, ::selection": {
          backgroundColor: "rgba(255, 255, 255, 0.2) !important",
          color: "inherit"
        },
        ".cm-selectionMatch": {
          backgroundColor: "rgba(255, 255, 255, 0.1) !important"
        },
        ".cm-tooltip": {
          backgroundColor: "var(--color-surface-container)",
          border: "1px solid rgba(255, 255, 255, 0.1)",
          borderRadius: "8px",
          color: "var(--color-on-surface)",
          boxShadow: "0 8px 24px rgba(0,0,0,0.5)"
        },
        ".cm-tooltip-autocomplete": {
          fontFamily: "var(--font-mono)",
          fontSize: "12px",
        },
        ".cm-tooltip-autocomplete > ul": {
          maxHeight: "250px"
        },
        ".cm-tooltip-autocomplete > ul > li": {
          padding: "4px 8px",
          cursor: "pointer",
          borderRadius: "4px",
          margin: "2px",
          display: "flex",
          alignItems: "center"
        },
        ".cm-tooltip-autocomplete > ul > li[aria-selected]": {
          backgroundColor: "var(--color-primary)",
          color: "var(--color-bg)"
        },
        ".cm-completionLabel": {
          fontWeight: "500",
          marginRight: "8px"
        },
        ".cm-completionDetail": {
          color: "var(--color-on-variant)",
          fontStyle: "italic",
          fontSize: "11px",
          marginLeft: "auto"
        },
        ".cm-tooltip-autocomplete > ul > li[aria-selected] .cm-completionDetail": {
          color: "var(--color-bg)",
          opacity: "0.8"
        },
        ".cm-searchMatch": {
          backgroundColor: "var(--color-primary)40",
          outline: "1px solid var(--color-primary)"
        },
        ".cm-searchMatch.cm-searchMatch-selected": {
          backgroundColor: "var(--color-secondary)60",
          outline: "1px solid var(--color-secondary)"
        },
        ".cm-panels": {
          backgroundColor: "transparent",
          color: "var(--color-on-surface)",
          fontFamily: "var(--font-body)",
          position: "absolute",
          top: "0",
          right: "0",
          width: "100%",
          pointerEvents: "none"
        },
        ".cm-panels-top": {
          display: "flex",
          justifyContent: "flex-end", // Поиск теперь справа сверху
          padding: "8px 16px !important",
          backgroundColor: "transparent !important",
          border: "none !important"
        },
        ".cm-search": {
          pointerEvents: "auto",
          display: "flex",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "8px",
          backgroundColor: "var(--color-surface-bright) !important",
          border: "1px solid rgba(255,255,255,0.1) !important",
          borderRadius: "8px",
          padding: "8px !important",
          boxShadow: "0 4px 12px rgba(0,0,0,0.4)"
        },
        ".cm-search input": {
          backgroundColor: "var(--color-surface-low) !important",
          border: "1px solid rgba(255,255,255,0.1) !important",
          color: "var(--color-on-surface) !important",
          borderRadius: "4px !important",
          padding: "4px 8px !important",
          fontSize: "12px !important",
          outline: "none !important"
        },
        ".cm-search input:focus": {
          borderColor: "var(--color-primary) !important"
        },
        ".cm-search input[type=checkbox]": {
          display: "none"
        },
        ".cm-search label": {
          fontSize: "12px !important",
          display: "flex !important",
          alignItems: "center !important",
          gap: "4px !important",
          color: "var(--color-on-variant) !important",
          cursor: "pointer !important",
          padding: "4px 8px !important",
          borderRadius: "4px !important",
          border: "1px solid rgba(255,255,255,0.1) !important",
          backgroundColor: "var(--color-surface-low) !important",
          userSelect: "none !important"
        },
        ".cm-search input[type=checkbox]:checked + label": {
          borderColor: "var(--color-primary) !important",
          color: "var(--color-primary) !important"
        },
        ".cm-button": {
          backgroundColor: "var(--color-surface-bright) !important",
          backgroundImage: "none !important",
          border: "1px solid rgba(255,255,255,0.1) !important",
          color: "var(--color-on-surface) !important",
          borderRadius: "4px !important",
          padding: "4px 10px !important",
          cursor: "pointer !important",
          fontSize: "12px !important",
          textTransform: "capitalize !important",
          transition: "all 0.2s !important",
          whiteSpace: "nowrap"
        },
        ".cm-button:hover": {
          backgroundColor: "var(--color-primary) !important",
          borderColor: "var(--color-primary) !important",
          color: "var(--color-bg) !important"
        },
        ".cm-button:active": {
          backgroundColor: "var(--color-primary) !important",
          color: "var(--color-bg) !important"
        },
        ".cm-textfield": {
          backgroundColor: "var(--color-surface-low) !important",
          border: "1px solid rgba(255,255,255,0.1) !important",
          color: "var(--color-on-surface) !important",
          borderRadius: "4px !important",
          padding: "4px 8px !important",
          fontSize: "12px !important",
          outline: "none !important",
          minWidth: "150px"
        },
        ".cm-selectionMatch": {
          backgroundColor: "var(--color-primary)30"
        }
      });
      
      let wordWrap = await window.spraute.storeGet('editorWordWrap');
      let syntaxThemeName = await window.spraute.storeGet('editorSyntaxTheme') || 'vscode-dark';
      const customHighlightStyle = HighlightStyle.define(getSyntaxHighlightStyles(syntaxThemeName));

      let extensions = [
        basicSetup,
        themeConfig,
        search({top: true}),
        history(),
        keymap.of([
          ...searchKeymap,
          ...historyKeymap,
          ...completionKeymap,
          {key: "Tab", run: acceptCompletion}
        ]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            const currentTab = openTabs.find(t => t.path === path);
            if (currentTab && !currentTab.isDirty) {
              currentTab.isDirty = true;
              renderTabs();
            }
            // Live sync — фоновый ресканинг при редактировании .spr
            if (path.endsWith('.spr') && !tab.isVisualScript) {
              VisualEngine.scheduleLiveSync(update.state.doc.toString());
            }
          }
        })
      ];

      if (path.endsWith('.spr')) {
        extensions.push(
          sprauteLanguageSupport,
          autocompletion(),
          lintGutter(),
          sprauteLinter,
          syntaxHighlighting(customHighlightStyle)
        );
      }

      if (wordWrap !== false) {
        extensions.push(EditorView.lineWrapping);
      }
      
      // Чтобы применить новую тему, нам нужно пересоздать стейт, 
      // но если мы пересоздаем его, мы потеряем историю. 
      // Для простоты, если стейт есть, мы все равно создаем новый, 
      // но с текстом из старого, если мы хотим форсировать смену темы.
      // Но лучше просто пересоздавать всегда, так как пользователь уже согласен перезапустить вкладку.
      
      let stateToUse = tab.state;
      if (!stateToUse) {
        stateToUse = EditorState.create({
          doc: content,
          extensions: extensions
        });
        tab.state = stateToUse;
      }
      // Если стейт уже был, мы просто используем его (сохраняется история и позиция курсора)
      // Чтобы применить новую тему, пользователь должен закрыть и открыть вкладку, либо перезапустить приложение.

    currentEditor = new EditorView({
      state: stateToUse,
      parent: editorMount
    });

    if (path.endsWith('.spr')) {
      VisualEngine.scanInBackground(stateToUse.doc.toString());
    }
    
  } catch (e) {
    editorMount.innerHTML = `<div class="p-4 text-red-400">Ошибка чтения файла: ${e.message}</div>`;
  }
  }
  } finally { _resolve(); _switchTabLock = null; }
}

// Управление контекстным меню
function showContextMenu(x, y, relPath, isDir, node) {
  const menu = document.getElementById('context-menu');
  currentCtxRelPath = relPath;
  currentCtxIsDir = isDir;
  currentCtxNode = node;

  menu.style.left = `${x}px`;
  menu.style.top = `${y}px`;
  menu.classList.remove('hidden');
  
  // Если клик по файлу, скрываем "Создать файл/папку"
  document.getElementById('ctx-new-file').style.display = isDir || relPath === '' ? 'flex' : 'none';
  document.getElementById('ctx-new-visual-script').style.display = isDir || relPath === '' ? 'flex' : 'none';
  document.getElementById('ctx-new-folder').style.display = isDir || relPath === '' ? 'flex' : 'none';
}

document.addEventListener('click', () => {
  document.getElementById('context-menu').classList.add('hidden');
});

// Действия контекстного меню
document.getElementById('ctx-new-file').addEventListener('click', async () => {
  const name = await appPrompt('Имя файла (без расширения будет добавлен .spr):');
  if (!name) return;
  const finalName = name.includes('.') ? name : `${name}.spr`;
  const dirPath = currentCtxIsDir ? currentCtxRelPath : '';
  const newPath = dirPath ? `${dirPath}/${finalName}` : finalName;
  
  const exists = await window.spraute.exists(newPath);
  if (exists) {
    appAlert('Файл с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.writeFile(newPath, '# Новый скрипт\n');
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-new-visual-script').addEventListener('click', async () => {
  const name = await appPrompt('Имя визуального скрипта:');
  if (!name) return;
  const baseName = name.replace(/\.sprv?$/i, '');
  const finalName = baseName.includes('.') ? baseName : `${baseName}.spr`;
  const dirPath = currentCtxIsDir ? currentCtxRelPath : '';
  const newPath = dirPath ? `${dirPath}/${finalName}` : finalName;
  
  const exists = await window.spraute.exists(newPath);
  if (exists) {
    appAlert('Файл с таким именем уже существует!');
    return;
  }

  try {
    await createVisualScriptPair(newPath);
    await reloadFileTree();
    await openFile(newPath, finalName.split('/').pop());
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-new-folder').addEventListener('click', async () => {
  const name = await appPrompt('Имя новой папки:');
  if (!name) return;
  const dirPath = currentCtxIsDir ? currentCtxRelPath : '';
  const newPath = dirPath ? `${dirPath}/${name}` : name;
  
  const exists = await window.spraute.exists(newPath);
  if (exists) {
    appAlert('Папка с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.mkdir(newPath);
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-rename').addEventListener('click', async () => {
  if (!currentCtxRelPath) return;
  const oldName = currentCtxRelPath.split('/').pop();
  const newName = await appPrompt('Новое имя:', oldName);
  if (!newName || newName === oldName) return;
  
  let dirPath = '';
  if (currentCtxRelPath.includes('/')) {
    dirPath = currentCtxRelPath.substring(0, currentCtxRelPath.lastIndexOf('/'));
  }
  const newPath = dirPath ? `${dirPath}/${newName}` : newName;
  
  try {
    await window.spraute.rename(currentCtxRelPath, newPath);
    if (currentCtxRelPath.endsWith('.spr')) {
      const oldSprv = sprPathToSprvPath(currentCtxRelPath);
      if (await window.spraute.exists(oldSprv)) {
        await window.spraute.rename(oldSprv, sprPathToSprvPath(newPath));
      }
    }
    for (const t of openTabs) {
      if (t.path === currentCtxRelPath) {
        t.path = newPath;
        t.name = newName;
      }
    }
    if (activeTabPath === currentCtxRelPath) activeTabPath = newPath;
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-delete').addEventListener('click', async () => {
  if (!currentCtxRelPath) return;
  const confirmed = await appConfirm(`Удалить ${currentCtxRelPath}?`);
  if (!confirmed) return;
  
  try {
    if (currentCtxIsDir) {
      await window.spraute.rmdir(currentCtxRelPath);
    } else {
      await window.spraute.unlink(currentCtxRelPath);
      if (currentCtxRelPath.endsWith('.spr')) {
        const sprv = sprPathToSprvPath(currentCtxRelPath);
        if (await window.spraute.exists(sprv)) await window.spraute.unlink(sprv);
      }
    }
    const closedIdx = openTabs.findIndex(t => t.path === currentCtxRelPath);
    if (closedIdx !== -1) openTabs.splice(closedIdx, 1);
    if (activeTabPath === currentCtxRelPath) {
      activeTabPath = null;
      if (openTabs.length > 0) await switchToTab(openTabs[Math.max(0, closedIdx - 1)].path);
      else renderTabs();
    }
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-show-explorer').addEventListener('click', () => {
  if (!currentCtxRelPath) return;
  window.spraute.showInExplorer(currentCtxRelPath);
});

// Clipboard
let clipboardPath = null;
let clipboardIsDir = false;

function _ignoreEditorKeyTarget(e) {
  const t = e.target;
  if (!t) return false;
  if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA') return true;
  if (t.closest('.cm-editor') || t.closest('#visual-code-mount')) return true;
  return false;
}

function _stopKeyEvent(e) {
  e.preventDefault();
  e.stopPropagation();
  e.stopImmediatePropagation();
}

/** Blockly + русская раскладка; capture-фаза, чтобы не дублировать встроенные shortcuts. */
document.addEventListener('keydown', (e) => {
  if (_ignoreEditorKeyTarget(e)) return;
  if (e.target.closest('#file-tree')) return;
  const activeTab = openTabs.find(t => t.path === activeTabPath);
  if (!activeTab?.isVisualScript || !blocklyWorkspace) return;

  const inVisual = e.target.closest('#visual-editor-layout') ||
    e.target.closest('#blockly-mount') ||
    e.target.closest('.blocklyWidgetDiv');
  if (!inVisual && !Blockly.common.getSelected()) return;

  const selected = Blockly.common.getSelected();
  const mod = e.ctrlKey || e.metaKey;

  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selected && selected.isDeletable()) {
      selected.dispose();
      _stopKeyEvent(e);
    }
    return;
  }

  if (!mod) return;

  const k = e.key.toLowerCase();
  if (e.code === 'KeyC' || k === 'c' || e.key === 'с') {
    if (selected && selected.isDeletable()) {
      Blockly.clipboard.copy(selected);
      _stopKeyEvent(e);
    }
    return;
  }
  if (e.code === 'KeyX' || k === 'x' || e.key === 'ч') {
    if (selected && selected.isDeletable()) {
      Blockly.clipboard.copy(selected);
      selected.dispose();
      _stopKeyEvent(e);
    }
    return;
  }
  if (e.code === 'KeyV' || k === 'v' || e.key === 'м') {
    beginBlocklyRestore(blocklyWorkspace);
    try {
      Blockly.clipboard.paste();
    } finally {
      setTimeout(() => endBlocklyRestore(blocklyWorkspace), 0);
    }
    _stopKeyEvent(e);
    return;
  }
  if (e.code === 'KeyZ' || k === 'z' || e.key === 'я') {
    blocklyWorkspace.undo(e.shiftKey);
    _stopKeyEvent(e);
    return;
  }
  if (e.code === 'KeyY' || k === 'y' || e.key === 'н') {
    blocklyWorkspace.undo(true);
    _stopKeyEvent(e);
    return;
  }
}, true);

document.addEventListener('keydown', async (e) => {
  if (_ignoreEditorKeyTarget(e)) return;
  
  if ((e.ctrlKey || e.metaKey) && (e.key === 'c' || e.key === 'с' || e.code === 'KeyC')) {
    if (selectedItemPath) {
      clipboardPath = selectedItemPath;
      clipboardIsDir = selectedItemIsDir;
      setStatus(`Скопировано: ${clipboardPath}`);
    }
  }
  
  if ((e.ctrlKey || e.metaKey) && (e.key === 'v' || e.key === 'м' || e.code === 'KeyV')) {
    if (!clipboardPath) return;
    
    let destDir = selectedItemIsDir ? selectedItemPath : (selectedItemPath.includes('/') ? selectedItemPath.substring(0, selectedItemPath.lastIndexOf('/')) : '');
    let fileName = clipboardPath.split('/').pop();
    let destPath = destDir ? `${destDir}/${fileName}` : fileName;
    
    // Проверка на конфликт
    const exists = await window.spraute.exists(destPath);
    if (exists) {
      const replace = await appConfirm(`Файл или папка "${fileName}" уже существует в этом месте. Заменить?`);
      if (!replace) return;
    }
    
    try {
      await window.spraute.copy(clipboardPath, destPath);
      setStatus(`Вставлено: ${destPath}`);
      await reloadFileTree();
    } catch(e) {
      appAlert('Ошибка при вставке: ' + e.message);
    }
  }
});

// Кнопки Проводника (Новый файл / папка относительно выделения)
document.getElementById('btn-root-new-file').addEventListener('click', async () => {
  const name = await appPrompt('Имя файла (без расширения будет .spr):');
  if (!name) return;
  const finalName = name.includes('.') ? name : `${name}.spr`;
  
  const dirPath = selectedItemIsDir ? selectedItemPath : (selectedItemPath.includes('/') ? selectedItemPath.substring(0, selectedItemPath.lastIndexOf('/')) : '');
  const newPath = dirPath ? `${dirPath}/${finalName}` : finalName;

  const exists = await window.spraute.exists(newPath);
  if (exists) {
    appAlert('Файл с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.writeFile(newPath, '# Новый скрипт\n');
    setStatus(`Файл ${finalName} создан`);
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

document.getElementById('btn-root-new-folder').addEventListener('click', async () => {
  const name = await appPrompt('Имя новой папки:');
  if (!name) return;
  
  const dirPath = selectedItemIsDir ? selectedItemPath : (selectedItemPath.includes('/') ? selectedItemPath.substring(0, selectedItemPath.lastIndexOf('/')) : '');
  const newPath = dirPath ? `${dirPath}/${name}` : name;

  const exists = await window.spraute.exists(newPath);
  if (exists) {
    appAlert('Папка с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.mkdir(newPath);
    setStatus(`Папка ${name} создана`);
    await reloadFileTree();
  } catch(e) {
    appAlert('Ошибка: ' + e.message);
  }
});

// --- Глобальный Поиск ---
const btnGlobalSearch = document.getElementById('btn-open-global-search');
const globalSearchModal = document.getElementById('global-search-modal');
const globalSearchBox = document.getElementById('global-search-modal-box');
const btnCloseGlobalSearch = document.getElementById('btn-close-global-search');
const globalSearchInput = document.getElementById('global-search-input');
const globalSearchResults = document.getElementById('global-search-results');

if (btnGlobalSearch) {
  btnGlobalSearch.addEventListener('click', () => {
    globalSearchInput.value = '';
    globalSearchResults.innerHTML = '<div class="text-center text-on-variant py-8 text-sm">Введите текст для поиска и нажмите Enter...</div>';
    globalSearchModal.classList.remove('hidden');
    setTimeout(() => {
      globalSearchBox.classList.remove('scale-95', 'opacity-0');
      globalSearchInput.focus();
    }, 10);
  });
}

if (btnCloseGlobalSearch) {
  btnCloseGlobalSearch.addEventListener('click', () => {
    globalSearchBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => globalSearchModal.classList.add('hidden'), 200);
  });
}

if (globalSearchInput) {
  globalSearchInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter') {
      const query = globalSearchInput.value.trim();
      if (!query) return;
      
      globalSearchResults.innerHTML = '<div class="text-center text-on-variant py-8 text-sm flex items-center justify-center gap-2"><span class="material-symbols-outlined animate-spin text-primary">autorenew</span>Поиск...</div>';
      
      try {
        const results = await window.spraute.search(query);
        if (results.length === 0) {
          globalSearchResults.innerHTML = '<div class="text-center text-on-variant py-8 text-sm">Ничего не найдено</div>';
          return;
        }
        
        let html = '';
        for (const res of results) {
          if (res.type === 'file') {
            const matchRegex = new RegExp(`(${query})`, 'gi');
            const highlightedMatch = res.match.replace(matchRegex, '<span class="text-primary bg-primary/20 rounded px-1">$1</span>');
            
            html += `
              <div class="search-result-item p-3 rounded-xl bg-black/20 hover:bg-black/40 border border-white/5 cursor-pointer transition-colors" data-file="${res.file}">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-[16px] text-primary">description</span>
                  <span class="font-mono text-sm text-white">${res.file}</span>
                </div>
                <div class="text-xs text-on-variant mt-1">Файл: <span class="text-white">${highlightedMatch}</span></div>
              </div>
            `;
          } else {
            const matchRegex = new RegExp(`(${query})`, 'gi');
            const highlightedMatch = res.match.replace(matchRegex, '<span class="text-primary bg-primary/20 rounded px-1">$1</span>');
            
            html += `
              <div class="search-result-item p-3 rounded-xl bg-black/20 hover:bg-black/40 border border-white/5 cursor-pointer transition-colors" data-file="${res.file}" data-line="${res.line}">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-[16px] text-secondary">code_blocks</span>
                  <span class="font-mono text-sm text-secondary">${res.file}:${res.line}</span>
                </div>
                <div class="text-xs text-on-variant mt-1 font-mono pl-6 overflow-hidden text-ellipsis whitespace-nowrap">${highlightedMatch}</div>
              </div>
            `;
          }
        }
        globalSearchResults.innerHTML = html;
      } catch(err) {
        globalSearchResults.innerHTML = `<div class="text-center text-red-400 py-8 text-sm">Ошибка поиска: ${err.message}</div>`;
      }
    }
  });
}

if (globalSearchResults) {
  globalSearchResults.addEventListener('click', async (e) => {
    const item = e.target.closest('.search-result-item');
    if (item) {
      const file = item.dataset.file;
      const line = item.dataset.line;
      await openFile(file, file.split('/').pop());
      
      if (line && currentEditor) {
        setTimeout(() => {
          try {
            const l = parseInt(line, 10);
            const doc = currentEditor.state.doc;
            if (l >= 1 && l <= doc.lines) {
              const pos = doc.line(l).from;
              currentEditor.dispatch({
                selection: { anchor: pos, head: pos },
                effects: EditorView.scrollIntoView(pos, { y: 'center' })
              });
            }
          } catch(e) {}
        }, 100);
      }
      
      globalSearchBox.classList.add('scale-95', 'opacity-0');
      setTimeout(() => globalSearchModal.classList.add('hidden'), 200);
    }
  });
}

// Настройки (Settings Modal)
const btnSettings = document.getElementById('btn-open-settings');
const btnCloseSettings = document.getElementById('btn-close-settings');
const settingsModal = document.getElementById('settings-modal');
const settingsBox = document.getElementById('settings-modal-box');

// Элементы настроек
const btnChangeMcPath = document.getElementById('btn-change-mc-path');
const inputMcPath = document.getElementById('setting-mc-path');
const inputBgImage = document.getElementById('setting-bg-image');
const inputBgOpacity = document.getElementById('setting-bg-opacity');
const labelBgOpacity = document.getElementById('setting-bg-opacity-val');

// Элементы темы
const selectTheme = document.getElementById('setting-theme');
const customThemeColors = document.getElementById('custom-theme-colors');
const colorPickers = {
  bg: document.getElementById('color-bg'),
  surface: document.getElementById('color-surface'),
  primary: document.getElementById('color-primary'),
  secondary: document.getElementById('color-secondary'),
};

// Элементы редактора
const inputFontSize = document.getElementById('setting-font-size');
const selectFontFamily = document.getElementById('setting-font-family');
const cbWordWrap = document.getElementById('setting-word-wrap');

const PRESET_THEMES = {
  'kinetic-dark': { bg: '#040e1f', surface: '#0b1a2f', primary: '#d1ff9f', secondary: '#ac8aff', text: '#ffffff' },
  'laboratory-light': { bg: '#f1f5f9', surface: '#ffffff', primary: '#10b981', secondary: '#8b5cf6', text: '#000000' },
  'spraute-classic': { bg: '#1c1917', surface: '#334155', primary: '#facc15', secondary: '#38bdf8', text: '#ffffff' }
};

// Функция для определения контрастности цвета
function getContrastColor(hex) {
  if (!hex) return '#ffffff';
  let r = parseInt(hex.substr(1, 2), 16);
  let g = parseInt(hex.substr(3, 2), 16);
  let b = parseInt(hex.substr(5, 2), 16);
  let yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
  return (yiq >= 128) ? '#000000' : '#ffffff';
}

async function applyThemeColors(colors) {
  const root = document.documentElement;
  if (colors.bg) root.style.setProperty('--color-bg', colors.bg);
  if (colors.surface) {
    root.style.setProperty('--color-surface', colors.surface);
    root.style.setProperty('--color-surface-container', colors.surface);
    root.style.setProperty('--color-surface-bright', colors.surface);
    root.style.setProperty('--color-surface-low', colors.surface);
  }
  if (colors.primary) root.style.setProperty('--color-primary', colors.primary);
  if (colors.secondary) {
    root.style.setProperty('--color-secondary', colors.secondary);
    root.style.setProperty('--color-tertiary', colors.secondary);
  }

  // Обновляем цвет текста, чтобы не сливался на светлом/тёмном фоне
  const textColor = colors.text || getContrastColor(colors.bg);
  root.style.setProperty('--color-on-surface', textColor === '#000000' ? '#1e293b' : '#dbe6fe');
  root.style.setProperty('--color-on-variant', textColor === '#000000' ? '#475569' : '#a0abc2');

  // Обновляем titlebar (передаём surface как фон и text как цвет кнопок)
  if (window.spraute && window.spraute.setTitleBarColors) {
    window.spraute.setTitleBarColors(colors.bg, textColor);
  }
  
  // Обновляем UI колор-пикеров
  for (const k in colorPickers) {
    if (colors[k]) {
      colorPickers[k].value = colors[k];
      document.getElementById(`color-${k}-val`).innerText = colors[k];
    }
  }
}

async function applyEditorSettings(settings) {
  const root = document.documentElement;
  if (settings.fontSize) root.style.setProperty('--editor-font-size', `${settings.fontSize}px`);
  if (settings.fontFamily) root.style.setProperty('--font-mono', settings.fontFamily);
  
  // В будущем: передать настройки переноса строк в CodeMirror 
  // window.currentEditorWordWrap = settings.wordWrap;
}

btnSettings.addEventListener('click', async () => {
  // Загружаем текущие значения
  const mcPath = await window.spraute.storeGet('minecraftPath');
  inputMcPath.value = mcPath || '';
  
  const bgImg = await window.spraute.storeGet('bgImage');
  inputBgImage.value = bgImg || '';

  const bgOpacity = await window.spraute.storeGet('bgOpacity');
  inputBgOpacity.value = bgOpacity || 0.2;
  labelBgOpacity.innerText = inputBgOpacity.value;

  const currentTheme = await window.spraute.storeGet('theme') || 'kinetic-dark';
  selectTheme.value = currentTheme;
  customThemeColors.style.display = currentTheme === 'custom' ? 'grid' : 'none';

  const customColors = await window.spraute.storeGet('customColors') || PRESET_THEMES['kinetic-dark'];
  if (currentTheme === 'custom') {
    applyThemeColors(customColors);
  }

  const fontSize = await window.spraute.storeGet('editorFontSize') || 14;
  inputFontSize.value = fontSize;

  const fontFamily = await window.spraute.storeGet('editorFontFamily') || "'JetBrains Mono', monospace";
  selectFontFamily.value = fontFamily;

  const wordWrap = await window.spraute.storeGet('editorWordWrap');
  cbWordWrap.checked = wordWrap !== false;

  const syntaxTheme = await window.spraute.storeGet('editorSyntaxTheme') || 'vscode-dark';
  const selectSyntaxTheme = document.getElementById('setting-syntax-theme');
  if (selectSyntaxTheme) {
    selectSyntaxTheme.value = syntaxTheme;
  }

  settingsModal.classList.remove('hidden');
  setTimeout(() => {
    settingsBox.classList.remove('scale-95', 'opacity-0');
  }, 10);
});

// Обработчики темы
selectTheme.addEventListener('change', async (e) => {
  const theme = e.target.value;
  await window.spraute.storeSet('theme', theme);
  
  if (theme === 'custom') {
    customThemeColors.style.display = 'grid';
    const customColors = await window.spraute.storeGet('customColors') || PRESET_THEMES['kinetic-dark'];
    applyThemeColors(customColors);
  } else {
    customThemeColors.style.display = 'none';
    if (PRESET_THEMES[theme]) {
      applyThemeColors(PRESET_THEMES[theme]);
    }
  }
});

for (const key in colorPickers) {
  colorPickers[key].addEventListener('input', async (e) => {
    const val = e.target.value;
    document.getElementById(`color-${key}-val`).innerText = val;
    
    let customColors = await window.spraute.storeGet('customColors') || {};
    customColors[key] = val;
    await window.spraute.storeSet('customColors', customColors);
    
    applyThemeColors(customColors);
  });
}

// Обработчики редактора
inputFontSize.addEventListener('input', async (e) => {
  const val = e.target.value;
  await window.spraute.storeSet('editorFontSize', val);
  applyEditorSettings({ fontSize: val });
});

selectFontFamily.addEventListener('change', async (e) => {
  const val = e.target.value;
  await window.spraute.storeSet('editorFontFamily', val);
  applyEditorSettings({ fontFamily: val });
});

cbWordWrap.addEventListener('change', async (e) => {
  const val = e.target.checked;
  await window.spraute.storeSet('editorWordWrap', val);
  // В будущем передать в CodeMirror
});

const selectSyntaxTheme = document.getElementById('setting-syntax-theme');
if (selectSyntaxTheme) {
  selectSyntaxTheme.addEventListener('change', async (e) => {
    const val = e.target.value;
    console.log('[Spraute] Saving syntax theme:', val);
    await window.spraute.storeSet('editorSyntaxTheme', val);
    
    // Сбросить сохранённые состояния всех вкладок, чтобы при следующем открытии применилась новая тема
    for (const tab of openTabs) {
      if (!tab.isImage) {
        tab.state = null;
      }
    }
    
    // Переоткрыть активную вкладку с новой темой
    if (activeTabPath) {
      const currentTab = openTabs.find(t => t.path === activeTabPath);
      if (currentTab && !currentTab.isImage) {
        await switchToTab(activeTabPath);
      }
    }
    
    setStatus('Тема синтаксиса изменена');
  });
}

btnCloseSettings.addEventListener('click', () => {
  settingsBox.classList.add('scale-95', 'opacity-0');
  setTimeout(() => {
    settingsModal.classList.add('hidden');
  }, 200);
});


btnChangeMcPath.addEventListener('click', async () => {
  const path = await window.spraute.selectMinecraftFolder();
  if (path) {
    inputMcPath.value = path;
    await window.spraute.storeSet('minecraftPath', path);
    await window.spraute.initWorkspace(path);
    document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';
    await reloadFileTree();
    await VisualEngine.scanInBackground(currentEditor ? currentEditor.state.doc.toString() : '');
    loadPluginsList();
  }
});

function applyBgImage(url, opacity) {
  const section = document.getElementById('editor-mount').parentElement;
  
  let bgEl = document.getElementById('custom-editor-bg');
  if (!url) {
    if (bgEl) bgEl.style.backgroundImage = '';
    return;
  }
  
  // Если это локальный путь Windows (содержит \ или начинается с буквы диска), 
  // преобразуем его в URL для браузера
  let formattedUrl = url;
  if (url.includes('\\') || /^[a-zA-Z]:/.test(url)) {
    formattedUrl = `file:///${url.replace(/\\/g, '/')}`;
  }

  if (!bgEl) {
    bgEl = document.createElement('div');
    bgEl.id = 'custom-editor-bg';
    // z-10 чтобы быть за редактором (z-20), но поверх фона
    bgEl.className = 'absolute inset-0 pointer-events-none z-10';
    section.insertBefore(bgEl, section.firstChild);
  }
  bgEl.style.backgroundImage = `url("${formattedUrl}")`;
  bgEl.style.backgroundSize = 'cover';
  bgEl.style.backgroundPosition = 'center';
  bgEl.style.opacity = opacity;
}

inputBgImage.addEventListener('input', async (e) => {
  const url = e.target.value;
  await window.spraute.storeSet('bgImage', url);
  applyBgImage(url, inputBgOpacity.value);
});

document.getElementById('btn-select-bg-image').addEventListener('click', async () => {
  const path = await window.spraute.selectImageFile();
  if (path) {
    inputBgImage.value = path;
    await window.spraute.storeSet('bgImage', path);
    applyBgImage(path, inputBgOpacity.value);
  }
});

inputBgOpacity.addEventListener('input', async (e) => {
  const val = e.target.value;
  labelBgOpacity.innerText = val;
  await window.spraute.storeSet('bgOpacity', val);
  applyBgImage(inputBgImage.value, val);
});

// Контекстное меню для пустой области проводника
document.getElementById('file-tree').addEventListener('contextmenu', (e) => {
  if (e.target === document.getElementById('file-tree')) {
    e.preventDefault();
    showContextMenu(e.pageX, e.pageY, '', true, null);
  }
});

// ====== Меню "Управление" ======
document.getElementById('menu-save')?.addEventListener('click', () => {
  saveActiveTab();
});
document.getElementById('menu-find')?.addEventListener('click', () => {
  if (currentEditor) {
    openSearchPanel(currentEditor);
    currentEditor.focus();
  }
});
document.getElementById('menu-replace')?.addEventListener('click', () => {
  if (currentEditor) {
    openSearchPanel(currentEditor);
    currentEditor.focus();
  }
});
document.getElementById('menu-undo')?.addEventListener('click', () => {
  if (currentEditor) {
    undo(currentEditor);
    currentEditor.focus();
  }
});
document.getElementById('menu-redo')?.addEventListener('click', () => {
  if (currentEditor) {
    redo(currentEditor);
    currentEditor.focus();
  }
});
document.getElementById('menu-autocomplete')?.addEventListener('click', () => {
  if (currentEditor) {
    startCompletion(currentEditor);
    currentEditor.focus();
  }
});

// ====== Drag and Drop для корневого каталога (вытащить из папки) ======
const fileTree = document.getElementById('file-tree');

fileTree.addEventListener('dragover', (e) => {
  e.preventDefault();
  // Если событие дошло до корня и перетаскивается элемент
  if (draggedItem && e.target === fileTree) {
    e.dataTransfer.dropEffect = 'move';
    fileTree.classList.add('bg-white/5');
  }
});

fileTree.addEventListener('dragleave', (e) => {
  if (e.target === fileTree) {
    fileTree.classList.remove('bg-white/5');
  }
});

fileTree.addEventListener('drop', async (e) => {
  e.preventDefault();
  fileTree.classList.remove('bg-white/5');
  
  if (draggedItem && e.target === fileTree) {
    const srcPath = draggedItem.path;
    const srcName = draggedItem.name;
    const destPath = srcName; // Корень
    
    // Если файл уже в корне (нет слешей)
    if (!srcPath.includes('/')) return;
    if (srcPath === destPath) return;
    
    try {
      const exists = await window.spraute.exists(destPath);
      if (exists) {
        const confirm = await appConfirm(`"${srcName}" уже существует в корне. Заменить?`);
        if (!confirm) return;
        if (draggedItem.isDir) await window.spraute.rmdir(destPath);
        else await window.spraute.unlink(destPath);
      }
      
      await window.spraute.rename(srcPath, destPath);
      await reloadFileTree();
      setStatus(`Перемещено в корень: ${srcName}`);
    } catch (err) {
      appAlert(`Ошибка перемещения: ${err.message}`);
    }
  }
});

// --- Плагины и Визуальные Библиотеки ---
const btnMenuPlugins = document.getElementById('menu-plugins');
const pluginsModal = document.getElementById('plugins-modal');
const btnClosePlugins = document.getElementById('btn-close-plugins');
const btnCreatePlugin = document.getElementById('btn-create-plugin');
const btnLoadPlugin = document.getElementById('btn-load-plugin');
const pluginsList = document.getElementById('plugins-list');
const pluginsSearch = document.getElementById('plugins-search');
const pluginsPagination = document.getElementById('plugins-pagination');

let allPluginsData = [];
let currentPluginsTab = 'my';
let currentPluginsPage = 1;
const PLUGINS_PER_PAGE = 7;

if (pluginsSearch) {
  pluginsSearch.addEventListener('input', () => {
    currentPluginsPage = 1;
    renderPluginsList();
  });
}

// Элементы модалки создания плагина
const pluginCreateModal = document.getElementById('plugin-create-modal');
const pluginCreateBox = document.getElementById('plugin-create-modal-box');
const btnClosePluginCreate = document.getElementById('btn-close-plugin-create');
const btnPluginCreateCancel = document.getElementById('btn-plugin-create-cancel');
const btnPluginCreateConfirm = document.getElementById('btn-plugin-create-confirm');

const inputPluginName = document.getElementById('plugin-create-name');
const inputPluginAuthor = document.getElementById('plugin-create-author');
const inputPluginMcVersion = document.getElementById('plugin-create-mcversion');
const inputPluginDesc = document.getElementById('plugin-create-desc');

// Элементы модалки настроек плагина
const pluginSettingsModal = document.getElementById('plugin-settings-modal');
const pluginSettingsBox = document.getElementById('plugin-settings-modal-box');
const inputSettingsName = document.getElementById('plugin-settings-name');
const inputSettingsAuthor = document.getElementById('plugin-settings-author');
const inputSettingsMcVersion = document.getElementById('plugin-settings-mcversion');
const inputSettingsDesc = document.getElementById('plugin-settings-desc');
const inputSettingsGlobalScripts = document.getElementById('plugin-settings-global-scripts');
const btnSettingsIcon = document.getElementById('btn-plugin-settings-icon');
const inputSettingsIconFile = document.getElementById('plugin-settings-icon-file');
const imgSettingsIconPreview = document.getElementById('plugin-settings-icon-preview');
const iconSettingsPlaceholder = document.getElementById('plugin-settings-icon-placeholder');
let currentPluginSettingsName = '';

if (inputSettingsIconFile) {
  inputSettingsIconFile.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      window._tempPluginIconBase64 = event.target.result;
      imgSettingsIconPreview.src = event.target.result;
      imgSettingsIconPreview.classList.remove('hidden');
      iconSettingsPlaceholder.classList.add('hidden');
    };
    reader.readAsDataURL(file);
  });
}

// Элементы модалки документации
const builderDocsModal = document.getElementById('builder-docs-modal');
const builderDocsBox = document.getElementById('builder-docs-modal-box');
const btnBuilderDocs = document.getElementById('btn-builder-docs');

if (btnMenuPlugins) {
  btnMenuPlugins.addEventListener('click', () => {
    pluginsModal.classList.remove('hidden');
    setTimeout(() => {
      document.getElementById('plugins-modal-box').classList.remove('scale-95', 'opacity-0');
    }, 10);
    loadPluginsList();
  });
}

if (btnClosePlugins) {
  btnClosePlugins.addEventListener('click', () => {
    document.getElementById('plugins-modal-box').classList.add('scale-95', 'opacity-0');
    setTimeout(() => pluginsModal.classList.add('hidden'), 200);
  });
}

function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function syncImportedPluginScripts(pluginName) {
  const srcScripts = `plugins/${pluginName}/scripts`;
  if (!(await window.spraute.exists(srcScripts))) return;
  const mcPath = await window.spraute.storeGet('minecraftPath');
  if (!mcPath) return;
  if (!(await window.spraute.exists('scripts/plugins'))) {
    await window.spraute.mkdir('scripts/plugins');
  }
  const destScripts = `scripts/plugins/${pluginName}`;
  if (await window.spraute.exists(destScripts)) {
    await window.spraute.rmdir(destScripts);
  }
  await window.spraute.mkdir(destScripts);
  await window.spraute.copy(srcScripts, destScripts);
}

async function syncAllEnabledPluginScripts() {
  for (const p of allPluginsData) {
    if (p.isEnabled) {
      try {
        await syncImportedPluginScripts(p.name);
      } catch (e) {
        console.warn(`Не удалось синхронизировать скрипты плагина ${p.name}:`, e);
      }
    }
  }
}

async function finishPluginInstall(pluginName) {
  try {
    const content = await window.spraute.readFile(`plugins/${pluginName}/plugin.json`, 'utf8');
    const data = JSON.parse(content);
    if (data.enabled !== false) {
      await syncImportedPluginScripts(pluginName);
    }
  } catch (e) {
    console.warn('Не удалось синхронизировать скрипты плагина:', e);
  }
  await VisualEngine.preloadPluginBlocks();
  loadPluginsList();
  setStatus(`Плагин «${pluginName}» установлен`);
}

async function installPluginArchive(file, overwrite = false) {
  if (!window.spraute.importPluginZip) {
    appAlert('Импорт плагинов не поддерживается. Перезапустите Студию.');
    return false;
  }
  const base64 = await readFileAsBase64(file);
  let res = await window.spraute.importPluginZip(base64, file.name, overwrite);
  if (!res.success && res.error === 'exists') {
    if (!(await appConfirm(`Плагин «${res.name}» уже установлен. Заменить?`))) return false;
    res = await window.spraute.importPluginZip(base64, file.name, true);
  }
  if (!res.success) {
    appAlert('Ошибка импорта: ' + (res.error || 'неизвестная ошибка'));
    return false;
  }
  await finishPluginInstall(res.name);
  return true;
}

async function importSingleSprPlugin(file) {
  const baseName = file.name.replace(/\.spr$/i, '');
  const pPath = `plugins/${baseName}`;

  if (await window.spraute.exists(pPath)) {
    if (!(await appConfirm(`Плагин «${baseName}» уже существует. Заменить?`))) return;
    await window.spraute.rmdir(pPath);
  }

  await window.spraute.mkdir(pPath);
  await window.spraute.mkdir(`${pPath}/blocks`);

  const content = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });

  await window.spraute.writeFile(`${pPath}/blocks/${file.name}`, content);
  const pluginJson = {
    name: baseName,
    author: 'Unknown',
    version: '1.0.0',
    mc_version: 'any',
    description: 'Импортированная библиотека блоков'
  };
  await window.spraute.writeFile(`${pPath}/plugin.json`, JSON.stringify(pluginJson, null, 2));
  await finishPluginInstall(baseName);
}

function openPluginFilePicker() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.splugin,.zip,.spr';

  input.onchange = async (ev) => {
    const file = ev.target.files[0];
    if (!file) return;

    try {
      const lower = file.name.toLowerCase();
      if (lower.endsWith('.spr')) {
        await importSingleSprPlugin(file);
      } else if (lower.endsWith('.zip') || lower.endsWith('.splugin')) {
        await installPluginArchive(file);
      } else {
        appAlert('Поддерживаются файлы .splugin, .zip и .spr');
      }
    } catch (err) {
      appAlert('Ошибка при установке плагина: ' + err.message);
    }
  };

  input.click();
}

async function importPluginFromUser() {
  const existsPlugins = await window.spraute.exists('plugins');
  if (!existsPlugins) await window.spraute.mkdir('plugins');

  if (window.spraute.importPluginDialog) {
    try {
      const res = await window.spraute.importPluginDialog();
      if (res.fallback) {
        openPluginFilePicker();
        return;
      }
      if (res.cancelled) return;
      if (!res.success) {
        if (res.error) appAlert('Ошибка импорта: ' + res.error);
        return;
      }
      await finishPluginInstall(res.name);
      return;
    } catch (err) {
      const msg = String(err?.message || err);
      if (!msg.includes('No handler registered')) {
        appAlert('Ошибка при установке плагина: ' + msg);
        return;
      }
    }
  }

  openPluginFilePicker();
}

async function loadPluginsList() {
  if (!pluginsList || !window.spraute) return;
  pluginsList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Загрузка...</div>';
  
  try {
    const exists = await window.spraute.exists('plugins');
    if (!exists) {
      allPluginsData = [];
      renderPluginsList();
      return;
    }
    
    const items = await window.spraute.listDir('plugins');
    const pluginFolders = items.filter(i => i.isDir);
    
    allPluginsData = [];
    for (const p of pluginFolders) {
      const pluginJsonPath = `${p.rel}/plugin.json`;
      if (!(await window.spraute.isFile(pluginJsonPath))) continue;

      let desc = "Пользовательский плагин / библиотека блоков";
      let author = "";
      let isEnabled = true;
      let hasIcon = false;
      try {
        const jsonContent = await window.spraute.readFile(pluginJsonPath, 'utf8');
        const data = JSON.parse(jsonContent);
        if (data.description) desc = data.description;
        if (data.author) author = data.author;
        if (data.enabled === false) isEnabled = false;
      } catch(e) {}
      
      try {
        hasIcon = await window.spraute.isFile(`${p.rel}/icon.png`);
      } catch(e) {}
      
      allPluginsData.push({
        name: p.name,
        path: p.rel,
        desc,
        author,
        isEnabled,
        hasIcon
      });
      
      // Авто-копирование скриптов при инициализации
      if (isEnabled) {
        try {
          const srcScripts = `${p.rel}/scripts`;
          if (await window.spraute.exists(srcScripts)) {
            const destScripts = `scripts/plugins/${p.name}`;
            if (!await window.spraute.exists('scripts/plugins')) {
              await window.spraute.mkdir('scripts/plugins');
            }
            if (!await window.spraute.exists(destScripts)) {
              await window.spraute.mkdir(destScripts);
            }
            await window.spraute.copy(srcScripts, destScripts);
          }
        } catch (e) {}
      }
    }
    
    // Загружаем сохраненный порядок
    let pluginsOrder = [];
    try {
      if (await window.spraute.exists('plugins/plugins_order.json')) {
        pluginsOrder = JSON.parse(await window.spraute.readFile('plugins/plugins_order.json', 'utf8'));
      }
    } catch(e) {}
    
    // Сортируем с учетом порядка
    allPluginsData.sort((a, b) => {
      let ia = pluginsOrder.indexOf(a.name);
      let ib = pluginsOrder.indexOf(b.name);
      if (ia === -1) ia = 999;
      if (ib === -1) ib = 999;
      if (ia === ib) return a.name.localeCompare(b.name);
      return ia - ib;
    });
    
    renderPluginsList();
    await syncAllEnabledPluginScripts();
    await VisualEngine.preloadPluginBlocks();
  } catch (e) {
    pluginsList.innerHTML = `<div class="text-center text-red-400 py-8 text-xs">Ошибка: ${e.message}</div>`;
  }
}

async function savePluginsOrder() {
  try {
    const order = allPluginsData.map(p => p.name);
    await window.spraute.writeFile('plugins/plugins_order.json', JSON.stringify(order, null, 2));
  } catch(e) {
    console.error("Ошибка сохранения порядка плагинов", e);
  }
}


async function renderPluginsList() {
  let sourceData = allPluginsData;
  let filtered = sourceData;
  const q = pluginsSearch ? pluginsSearch.value.toLowerCase().trim() : "";
  if (q) {
    filtered = filtered.filter(p => p.name.toLowerCase().includes(q) || p.desc.toLowerCase().includes(q));
  }
  
  if (filtered.length === 0) {
    pluginsList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Плагинов не найдено.</div>';
    if (pluginsPagination) pluginsPagination.innerHTML = '';
    return;
  }
  
  const totalPages = Math.ceil(filtered.length / PLUGINS_PER_PAGE);
  if (currentPluginsPage > totalPages) currentPluginsPage = totalPages;
  
  const start = (currentPluginsPage - 1) * PLUGINS_PER_PAGE;
  const paginated = filtered.slice(start, start + PLUGINS_PER_PAGE);
  
  let html = '';
  for (const p of paginated) {
    let iconHtml = `<span class="material-symbols-outlined text-[18px]">extension</span>`;
    
    if (p.hasIcon) {
        try {
          if (window.spraute.readFile) {
            const b64 = await window.spraute.readFile(`${p.path}/icon.png`, 'base64');
            iconHtml = `<img src="data:image/png;base64,${b64}" class="w-full h-full object-cover" />`;
          } else {
            iconHtml = `<img src="/api/fs/${p.path}/icon.png" class="w-full h-full object-cover" onerror="this.outerHTML='<span class=\\'material-symbols-outlined text-[18px]\\'>extension</span>'" />`;
          }
        } catch(e) {}
      }

      html += `
      <div class="flex items-center justify-between p-3 rounded-xl ${p.isEnabled ? 'bg-black/20' : 'bg-black/40 opacity-60'} border border-white/5 hover:border-white/10 transition-colors group">
        <div class="flex items-center gap-3 flex-1 overflow-hidden">
          
          <!-- Галочка (Checkbox) включения/выключения -->
          <label class="relative flex items-center cursor-pointer p-1" title="${p.isEnabled ? 'Отключить' : 'Включить'}">
            <input type="checkbox" class="sr-only peer plugin-checkbox-toggle" data-plugin="${p.name}" data-state="${p.isEnabled}" ${p.isEnabled ? 'checked' : ''}>
            <div class="w-5 h-5 border-2 border-white/20 rounded bg-black/40 peer-checked:bg-primary peer-checked:border-primary flex items-center justify-center transition-colors">
              <span class="material-symbols-outlined text-[14px] text-background opacity-0 peer-checked:opacity-100 transition-opacity" style="font-variation-settings: 'wght' 700">check</span>
            </div>
          </label>

          <div class="w-10 h-10 rounded-lg ${p.isEnabled ? 'bg-primary/20 text-primary' : 'bg-white/10 text-on-variant'} flex items-center justify-center shrink-0 overflow-hidden shadow-inner">
            ${iconHtml}
          </div>
          
          <div class="flex-1 overflow-hidden pr-4">
            <div class="font-medium ${p.isEnabled ? 'text-white' : 'text-on-variant'} flex items-center gap-2 truncate">
              ${p.name} 
              ${p.version ? `<span class="px-1.5 py-0.5 rounded bg-white/10 text-[10px] text-white shrink-0">v${p.version}</span>` : ''}
              ${p.author ? `<span class="px-1.5 py-0.5 rounded bg-white/10 text-[10px] text-white shrink-0">by ${p.author}</span>` : ''}
            </div>
            <div class="text-xs text-on-variant mt-0.5 truncate">${p.desc || ''}</div>
          </div>
        </div>

        <div class="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
          <button class="px-3 py-1.5 bg-primary/20 hover:bg-primary/40 rounded-lg text-xs text-primary font-medium btn-edit-plugin-blocks" data-plugin="${p.name}">Блоки</button>
          <button class="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs text-white btn-edit-plugin-settings" data-plugin="${p.name}">
             <span class="material-symbols-outlined text-[14px] block">settings</span>
          </button>
          <div class="flex flex-col gap-1 ml-2">
            <button class="w-6 h-6 bg-white/5 hover:bg-white/10 rounded flex items-center justify-center text-white transition-colors btn-plugin-move-up" data-plugin="${p.name}" title="Сдвинуть вверх (приоритет парсинга)">
              <span class="material-symbols-outlined text-[14px]">arrow_upward</span>
            </button>
            <button class="w-6 h-6 bg-white/5 hover:bg-white/10 rounded flex items-center justify-center text-white transition-colors btn-plugin-move-down" data-plugin="${p.name}" title="Сдвинуть вниз">
              <span class="material-symbols-outlined text-[14px]">arrow_downward</span>
            </button>
          </div>
        </div>
      </div>
      `;
  }
  
  pluginsList.innerHTML = html;
  
  if (pluginsPagination) renderPagination(totalPages);
}

function renderPagination(totalPages) {
  if (!pluginsPagination) return;
  if (totalPages <= 1) {
    pluginsPagination.innerHTML = '';
    return;
  }
  
  let html = `<button class="w-8 h-8 rounded flex items-center justify-center bg-white/5 hover:bg-white/10 text-white transition-colors" ${currentPluginsPage === 1 ? 'disabled style="opacity:0.3"' : ''} onclick="currentPluginsPage--; renderPluginsList();"><span class="material-symbols-outlined text-[16px]">chevron_left</span></button>`;
  
  for(let i=1; i<=totalPages; i++) {
     if (i === 1 || i === totalPages || (i >= currentPluginsPage - 1 && i <= currentPluginsPage + 1)) {
        html += `<button class="w-8 h-8 rounded flex items-center justify-center ${i === currentPluginsPage ? 'bg-primary text-background font-bold' : 'bg-white/5 hover:bg-white/10 text-white'} transition-colors text-xs" onclick="currentPluginsPage=${i}; renderPluginsList();">${i}</button>`;
     } else if (i === currentPluginsPage - 2 || i === currentPluginsPage + 2) {
        html += `<span class="text-on-variant text-xs">...</span>`;
     }
  }
  
  html += `<button class="w-8 h-8 rounded flex items-center justify-center bg-white/5 hover:bg-white/10 text-white transition-colors" ${currentPluginsPage === totalPages ? 'disabled style="opacity:0.3"' : ''} onclick="currentPluginsPage++; renderPluginsList();"><span class="material-symbols-outlined text-[16px]">chevron_right</span></button>`;
  
  pluginsPagination.innerHTML = html;
}

  // Привязка обработчиков плагинов через делегирование (если кнопки были пересозданы) или напрямую
document.addEventListener('click', async (e) => {
  let target = e.target;
  if (target.nodeType === 3) target = target.parentNode;
  if (!target || !target.closest) return;
  
  if (target.closest('.btn-plugin-move-up')) {
    const pluginName = target.closest('.btn-plugin-move-up').dataset.plugin;
    const idx = allPluginsData.findIndex(p => p.name === pluginName);
    if (idx > 0) {
      // Меняем местами
      const temp = allPluginsData[idx - 1];
      allPluginsData[idx - 1] = allPluginsData[idx];
      allPluginsData[idx] = temp;
      await savePluginsOrder();
      renderPluginsList();
    }
  }

  if (target.closest('.btn-plugin-move-down')) {
    const pluginName = target.closest('.btn-plugin-move-down').dataset.plugin;
    const idx = allPluginsData.findIndex(p => p.name === pluginName);
    if (idx < allPluginsData.length - 1 && idx !== -1) {
      // Меняем местами
      const temp = allPluginsData[idx + 1];
      allPluginsData[idx + 1] = allPluginsData[idx];
      allPluginsData[idx] = temp;
      await savePluginsOrder();
      renderPluginsList();
    }
  }

  if (target.closest('.plugin-checkbox-toggle') || target.closest('.btn-toggle-plugin')) {
    const el = target.closest('.plugin-checkbox-toggle') || target.closest('.btn-toggle-plugin');
    const pluginName = el.dataset.plugin;
    
    // Предотвращаем двойное срабатывание (на label и на input)
    if (e.target.tagName.toLowerCase() === 'label' || e.target.closest('label')) {
      if (el.tagName.toLowerCase() !== 'input') {
         // Ждем когда событие дойдет до input
         return;
      }
    }
    
    let currentState;
    if (el.tagName.toLowerCase() === 'input' && el.type === 'checkbox') {
        currentState = !el.checked;
    } else {
        currentState = el.dataset.state === 'true';
    }
    
    try {
      const pPath = `plugins/${pluginName}/plugin.json`;
      const content = await window.spraute.readFile(pPath, 'utf8');
      const data = JSON.parse(content);
      data.enabled = !currentState;
      await window.spraute.writeFile(pPath, JSON.stringify(data, null, 2));
      
      const pData = allPluginsData.find(p => p.name === pluginName);
      if (pData) pData.isEnabled = !currentState;

          // Если плагин включен, копируем его скрипты в spraute_engine/scripts/plugins/pluginName
      if (data.enabled) {
        try {
          const srcScripts = `plugins/${pluginName}/scripts`;
          if (await window.spraute.exists(srcScripts)) {
            const destScripts = `scripts/plugins/${pluginName}`;
            if (!await window.spraute.exists('scripts/plugins')) {
              await window.spraute.mkdir('scripts/plugins');
            }
            if (!await window.spraute.exists(destScripts)) {
              await window.spraute.mkdir(destScripts);
            }
            // Используем window.spraute.copy (если он копирует содержимое)
            await window.spraute.copy(srcScripts, destScripts);
            console.log(`Скрипты плагина ${pluginName} скопированы в рабочую среду.`);
          }
        } catch (e) {
          console.error("Ошибка копирования скриптов плагина:", e);
        }
      }

      renderPluginsList();
      
      // Перезагружаем блоки плагинов и обновляем тулбокс
      await VisualEngine.preloadPluginBlocks();
    } catch(err) {
      console.error("Ошибка переключения: " + err.message);
    }
  }
  
  if (target.closest('.btn-edit-plugin-settings')) {
    const btn = target.closest('.btn-edit-plugin-settings');
    const pluginName = btn.dataset.plugin;
    currentPluginSettingsName = pluginName;
    
    try {
      const pPath = `plugins/${pluginName}/plugin.json`;
      const content = await window.spraute.readFile(pPath, 'utf8');
      const data = JSON.parse(content);
      
      const elName = document.getElementById('plugin-settings-name');
      const elAuthor = document.getElementById('plugin-settings-author');
      const elMcVersion = document.getElementById('plugin-settings-mcversion');
      const elDesc = document.getElementById('plugin-settings-desc');
      const elScripts = document.getElementById('plugin-settings-global-scripts');
      
      if (elName) elName.value = data.name || pluginName;
      if (elAuthor) elAuthor.value = data.author || '';
      if (elMcVersion) elMcVersion.value = data.mc_version || '';
      if (elDesc) elDesc.value = data.description || '';
      if (elScripts) elScripts.value = data.global_scripts || '';
      
      // Иконка
      const iconPath = `plugins/${pluginName}/icon.png`;
      const hasIcon = await window.spraute.isFile(iconPath);
      const elIconFile = document.getElementById('plugin-settings-icon-file');
      if (elIconFile) elIconFile.value = ''; // сброс файла
      window._tempPluginIconBase64 = null; // временная переменная
      
      const elIconPreview = document.getElementById('plugin-settings-icon-preview');
      const elIconPlaceholder = document.getElementById('plugin-settings-icon-placeholder');
      
      if (hasIcon && window.spraute.readFile) {
        const b64 = await window.spraute.readFile(iconPath, 'base64');
        if (elIconPreview && elIconPlaceholder) {
          elIconPreview.src = `data:image/png;base64,${b64}`;
          elIconPreview.classList.remove('hidden');
          elIconPlaceholder.classList.add('hidden');
        }
      } else {
        if (elIconPreview && elIconPlaceholder) {
          elIconPreview.src = '';
          elIconPreview.classList.add('hidden');
          elIconPlaceholder.classList.remove('hidden');
        }
      }
      
      const elModal = document.getElementById('plugin-settings-modal');
      const elBox = document.getElementById('plugin-settings-modal-box');
      if (elModal && elBox) {
        elModal.classList.remove('hidden');
        setTimeout(() => {
          elBox.classList.remove('scale-95', 'opacity-0');
        }, 10);
      }
    } catch(err) {
      appAlert("Ошибка загрузки настроек: " + err.message);
    }
  }

  if (target.closest('#btn-close-plugin-settings') || target.closest('#btn-plugin-settings-cancel')) {
    const elModal = document.getElementById('plugin-settings-modal');
    const elBox = document.getElementById('plugin-settings-modal-box');
    if (elBox && elModal) {
      elBox.classList.add('scale-95', 'opacity-0');
      setTimeout(() => elModal.classList.add('hidden'), 200);
    }
  }

  if (target.closest('#btn-plugin-settings-icon')) {
    const elIconFile = document.getElementById('plugin-settings-icon-file');
    if (elIconFile) elIconFile.click();
  }

  if (target.closest('#btn-plugin-settings-export')) {
    if (window.spraute.exportPluginZip) {
      const res = await window.spraute.exportPluginZip(currentPluginSettingsName);
      if (res && res.success) {
        appAlert("Плагин успешно экспортирован:\n" + res.path);
      } else if (res && res.error !== 'Отменено пользователем') {
        appAlert("Ошибка экспорта: " + res.error);
      }
    } else {
      appAlert("Функция экспорта не поддерживается. Пожалуйста, перезапустите Студию.");
    }
  }

  if (target.closest('#btn-plugin-settings-save')) {
    try {
      const pPath = `plugins/${currentPluginSettingsName}/plugin.json`;
      const content = await window.spraute.readFile(pPath, 'utf8');
      const data = JSON.parse(content);
      
      const elAuthor = document.getElementById('plugin-settings-author');
      const elMcVersion = document.getElementById('plugin-settings-mcversion');
      const elDesc = document.getElementById('plugin-settings-desc');
      const elScripts = document.getElementById('plugin-settings-global-scripts');
      
      if (elAuthor) data.author = elAuthor.value.trim();
      if (elMcVersion) data.mc_version = elMcVersion.value.trim();
      if (elDesc) data.description = elDesc.value.trim();
      if (elScripts) data.global_scripts = elScripts.value; // не тримим, вдруг там пустые строки нужны
      
      await window.spraute.writeFile(pPath, JSON.stringify(data, null, 2));
      
      if (window._tempPluginIconBase64 && window.spraute.writeBase64) {
        // Убираем префикс "data:image/png;base64,"
        const base64Data = window._tempPluginIconBase64.split(',')[1];
        await window.spraute.writeBase64(`plugins/${currentPluginSettingsName}/icon.png`, base64Data);
      }
      
      const elModal = document.getElementById('plugin-settings-modal');
      const elBox = document.getElementById('plugin-settings-modal-box');
      if (elBox && elModal) {
        elBox.classList.add('scale-95', 'opacity-0');
        setTimeout(() => elModal.classList.add('hidden'), 200);
      }
      loadPluginsList(); // Перезагружаем список
    } catch (err) {
      appAlert("Ошибка сохранения: " + err.message);
    }
  }

  if (target.closest('#btn-create-plugin')) {
    inputPluginName.value = '';
    inputPluginAuthor.value = 'Unknown';
    inputPluginDesc.value = '';
    pluginCreateModal.classList.remove('hidden');
    setTimeout(() => {
      pluginCreateBox.classList.remove('scale-95', 'opacity-0');
    }, 10);
  }

  if (target.closest('#btn-close-plugin-create') || target.closest('#btn-plugin-create-cancel')) {
    pluginCreateBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => pluginCreateModal.classList.add('hidden'), 200);
  }

  if (target.closest('#btn-plugin-create-confirm')) {
    const name = inputPluginName.value.trim();
    const author = inputPluginAuthor.value.trim() || 'Unknown';
    const mcVersion = inputPluginMcVersion.value;
    const desc = inputPluginDesc.value.trim();

    if (!name) {
      appAlert('Название плагина не может быть пустым!');
      return;
    }
    
    // Проверка на допустимые символы (только буквы, цифры, _, -)
    if (!/^[a-zA-Z0-9_\-]+$/.test(name)) {
      appAlert('Имя плагина должно содержать только латинские буквы, цифры, тире или нижнее подчеркивание.');
      return;
    }
    
    try {
      const existsPlugins = await window.spraute.exists('plugins');
      if (!existsPlugins) {
        await window.spraute.mkdir('plugins');
      }
      
      const pPath = `plugins/${name}`;
      const existsThisPlugin = await window.spraute.exists(pPath);
      if (existsThisPlugin) {
        appAlert('Плагин с таким именем уже существует!');
        return;
      }

      await window.spraute.mkdir(pPath);
      await window.spraute.mkdir(`${pPath}/blocks`);
      await window.spraute.mkdir(`${pPath}/scripts`);
      
      // Создаем plugin.json
      const pluginJson = {
        name: name,
        author: author,
        version: "1.0.0",
        mc_version: mcVersion,
        description: desc
      };
      await window.spraute.writeFile(`${pPath}/plugin.json`, JSON.stringify(pluginJson, null, 2));

      // Создаем README.md
      const readme = `# Плагин ${name}\n\n**Автор:** ${author}\n**Описание:** ${desc}\n\nСюда вы можете помещать файлы .spr с синтаксисом \`#\\\` для создания визуальных блоков в папку \`blocks/\`.`;
      await window.spraute.writeFile(`${pPath}/README.md`, readme);
      
      pluginCreateBox.classList.add('scale-95', 'opacity-0');
      setTimeout(() => pluginCreateModal.classList.add('hidden'), 200);

      setStatus(`Плагин ${name} успешно создан`);
      loadPluginsList();
    } catch(err) {
      appAlert('Ошибка при создании плагина: ' + err.message);
    }
  }

  if (target.closest('#btn-load-plugin')) {
    try {
      await importPluginFromUser();
    } catch (err) {
      appAlert('Ошибка при установке плагина: ' + err.message);
    }
  }
});

// --- Редактор Блоков Плагина ---
const pluginBlocksModal = document.getElementById('plugin-blocks-modal');
const pluginBlocksBox = document.getElementById('plugin-blocks-modal-box');
const pluginBlocksList = document.getElementById('plugin-blocks-list');
const pluginBlocksTitle = document.getElementById('plugin-blocks-title');
let currentEditingPlugin = null;

// Категории плагина
const categoriesModal = document.getElementById('categories-modal');
const categoriesBox = document.getElementById('categories-modal-box');
const categoriesList = document.getElementById('categories-list');
const inputNewCatName = document.getElementById('new-category-name');
const inputNewCatColor = document.getElementById('new-category-color');
let pluginCategories = {}; // {name: color}

async function loadPluginCategories(pluginName) {
  pluginCategories = { "Мои блоки": "#38bdf8" }; // Дефолтная категория
  try {
    const catPath = `plugins/${pluginName}/categories.json`;
    if (await window.spraute.exists(catPath)) {
      const content = await window.spraute.readFile(catPath, 'utf8');
      pluginCategories = { ...pluginCategories, ...JSON.parse(content) };
    }
  } catch(e) {}
  renderCategories();
  updateCategorySelect();
}

async function savePluginCategories(pluginName) {
  try {
    const catPath = `plugins/${pluginName}/categories.json`;
    await window.spraute.writeFile(catPath, JSON.stringify(pluginCategories, null, 2));
  } catch(e) { console.error("Save categories error:", e); }
}

function renderCategories() {
  categoriesList.innerHTML = Object.entries(pluginCategories).map(([name, color]) => `
    <div class="flex items-center justify-between p-2 rounded-lg bg-black/20 border border-white/5">
      <div class="flex items-center gap-2">
        <div class="w-5 h-5 rounded" style="background-color: ${color};"></div>
        <span class="text-sm text-white">${name}</span>
      </div>
      <button class="text-red-400 hover:text-red-300 text-xs btn-delete-category" data-cat="${name}">Удалить</button>
    </div>
  `).join('');
}

function updateCategorySelect() {
  const select = document.getElementById('builder-category');
  if (!select) return;
  select.innerHTML = Object.entries(pluginCategories).map(([name, color]) => 
    `<option value="${name}" data-color="${color}">${name}</option>`
  ).join('');
  
  // Добавляем обработчик смены категории
  select.onchange = () => {
    const selected = select.options[select.selectedIndex];
    const catColor = selected.dataset.color || '#38bdf8';
    inputBuilderColor.value = catColor;
    inputBuilderColorPicker.value = catColor;
  };
}

const blockBuilderModal = document.getElementById('block-builder-modal');
const blockBuilderBox = document.getElementById('block-builder-modal-box');
const inputBuilderId = document.getElementById('builder-id');
const inputBuilderCategory = document.getElementById('builder-category'); // теперь это select
const inputBuilderShape = document.getElementById('builder-shape');
const inputBuilderColor = document.getElementById('builder-color');
const inputBuilderColorPicker = document.getElementById('builder-color-picker');
const inputBuilderUi = document.getElementById('builder-ui');
const inputBuilderWriteStart = document.getElementById('builder-write-start');
const inputBuilderCode = document.getElementById('builder-code');
const inputBuilderParse = document.getElementById('builder-parse');
const btnBuilderPreview = document.getElementById('btn-builder-preview');
let previewWorkspace = null;

function builderWriteStartMetaLines() {
  if (!inputBuilderWriteStart) return [];
  return inputBuilderWriteStart.value
    .split('\n')
    .map(l => l.trim())
    .filter(Boolean)
    .map(l => `#\\ write_start: ${l}`);
}

function updateBlockPreview() {
    const rawId = inputBuilderId.value.trim();
    // Используем временный id если пустой
    const blockId = (rawId ? `${currentEditingPlugin}_preview_${rawId}` : `preview_${Date.now()}`).replace(/[^a-z0-9_.]/g, '_');
    
    let content = "";
    let fullUi = inputBuilderUi.value;

    if (!inputBuilderCode.value.trim() && !inputBuilderParse?.value.trim() && !fullUi.includes('[UI]')) {
       // Unified syntax
       let bodyContent = fullUi.split('\n').map(l => '#\\ ' + l).join('\n');
       content = [
          `#\\ block: ${blockId}`,
          `#\\ category: Preview`,
          `#\\ color: ${inputBuilderColor.value}`,
          `#\\ shape: ${inputBuilderShape.value}`,
          ...builderWriteStartMetaLines(),
          `#\\`,
          bodyContent
       ].filter(Boolean).join('\n') + '\n';
    } else {
       // Legacy syntax
       let staticPart = fullUi, dynPart = "";
       if (fullUi.includes('[DYNAMIC_UI]')) {
         const idx = fullUi.indexOf('[DYNAMIC_UI]');
         staticPart = fullUi.slice(0, idx).trim();
         dynPart = fullUi.slice(idx + '[DYNAMIC_UI]'.length).trim();
       }
       
       let uiContent = staticPart.split('\n').map(l => '#\\ ' + l).join('\n');
       let dynContent = dynPart ? dynPart.split('\n').map(l => '#\\ ' + l).join('\n') : '';
       let codeContent = inputBuilderCode.value.split('\n').map(l => '#\\ ' + l).join('\n');
       let parseContent = inputBuilderParse && inputBuilderParse.value ? inputBuilderParse.value.split('\n').map(l => '#\\ ' + l).join('\n') : '';
       
       content = [
          `#\\ block: ${blockId}`,
          `#\\ category: Preview`,
          `#\\ color: ${inputBuilderColor.value}`,
          `#\\ shape: ${inputBuilderShape.value}`,
          ...builderWriteStartMetaLines(),
          `#\\`,
          `#\\ [UI]`,
          uiContent,
          dynContent ? `#\\\n#\\ [DYNAMIC_UI]\n${dynContent}` : '',
          `#\\`,
          `#\\ [CODE_GEN]`,
          codeContent,
          parseContent ? `#\\\n#\\ [CODE_PARSE]\n${parseContent}` : ''
       ].filter(Boolean).join('\n') + '\n';
    }
    
    try {
      // Очищаем регистрацию предыдущего превью блока
      if (window._lastPreviewBlockId && Blockly.Blocks[window._lastPreviewBlockId]) {
        delete Blockly.Blocks[window._lastPreviewBlockId];
        delete SprauteGenerator.forBlock[window._lastPreviewBlockId];
      }
      window._lastPreviewBlockId = blockId;
      
      parseCustomBlocks(content, "", true);
      
      if (!Blockly.Blocks[blockId] || typeof Blockly.Blocks[blockId].init !== 'function') {
        document.getElementById('blockly-preview-mount').innerHTML = 
          '<div class="flex items-center justify-center h-full text-red-400 text-xs p-3">Ошибка: не удалось зарегистрировать блок. Проверьте синтаксис UI.</div>';
        return;
      }
      
      if (!previewWorkspace) {
        previewWorkspace = Blockly.inject('blockly-preview-mount', {
          toolbox: null,
          theme: SprauteTheme,
          readOnly: false,
          scrollbars: false,
          trashcan: false,
          zoom: { controls: false, wheel: false, startScale: 0.9 }
        });
      }
      previewWorkspace.clear();
      const newBlock = previewWorkspace.newBlock(blockId);
      newBlock.initSvg();
      newBlock.render();
      newBlock.moveBy(20, 20);
      
    } catch(e) {
      console.error("Preview Error:", e);
      document.getElementById('blockly-preview-mount').innerHTML = 
        `<div class="flex items-center justify-center h-full text-red-400 text-xs p-3">Ошибка: ${e.message}</div>`;
    }
}

document.addEventListener('click', async (e) => {
  let target = e.target;
  if (target.nodeType === 3) target = target.parentNode; // Handle text nodes
  if (!target || !target.closest) return;
  
  // Открыть список блоков плагина
  if (target.closest('.btn-edit-plugin-blocks')) {
    const pluginName = target.closest('.btn-edit-plugin-blocks').dataset.plugin;
    currentEditingPlugin = pluginName;
    pluginBlocksTitle.innerText = pluginName;
    
    await loadPluginCategories(pluginName);
    
    pluginBlocksModal.classList.remove('hidden');
    setTimeout(() => {
      pluginBlocksBox.classList.remove('scale-95', 'opacity-0');
    }, 10);
    
    loadPluginBlocks(pluginName);
  }
  
  // Открыть управление категориями
  if (target.closest('#btn-manage-categories')) {
    categoriesModal.classList.remove('hidden');
    setTimeout(() => categoriesBox.classList.remove('scale-95', 'opacity-0'), 10);
    renderCategories();
  }
  
  // Закрыть управление категориями
  if (target.closest('#btn-close-categories')) {
    categoriesBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => categoriesModal.classList.add('hidden'), 200);
  }
  
  // Добавить категорию
  if (target.closest('#btn-add-category')) {
    const name = inputNewCatName.value.trim();
    const color = inputNewCatColor.value;
    if (name && !pluginCategories[name]) {
      pluginCategories[name] = color;
      await savePluginCategories(currentEditingPlugin);
      renderCategories();
      updateCategorySelect();
      inputNewCatName.value = '';
    }
  }
  
  // Удалить категорию
  if (target.closest('.btn-delete-category')) {
    const catName = target.closest('.btn-delete-category').dataset.cat;
    if (catName !== "Мои блоки") {
      delete pluginCategories[catName];
      await savePluginCategories(currentEditingPlugin);
      renderCategories();
      updateCategorySelect();
    }
  }
  
  // Закрыть список блоков плагина
  if (target.closest('#btn-close-plugin-blocks')) {
    pluginBlocksBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => pluginBlocksModal.classList.add('hidden'), 200);
  }
  
  // Редактировать блок
  if (target.closest('.btn-edit-block')) {
    const btn = target.closest('.btn-edit-block');
    const filePath = btn.dataset.file;
    const fileName = btn.dataset.name;
    
    try {
      const text = await window.spraute.readFile(filePath, 'utf8');
      
      // Парсим метаданные из файла
      const mId = text.match(/^#\\?\s*block:\s*(.+)/m);
      const mCat = text.match(/^#\\?\s*category:\s*(.+)/m);
      const mColor = text.match(/^#\\?\s*color:\s*(.+)/m);
      const mShape = text.match(/^#\\?\s*shape:\s*(.+)/m);
      const writeStartMatches = [...text.matchAll(/^#\\?\s*write_start:\s*(.+)$/gm)];
      
      // Извлекаем UI секцию
      const uiMatch = text.match(/\[UI\]([\s\S]*?)(?:\[DYNAMIC_UI\]|\[CODE_GEN\]|\[CODE_PARSE\]|$)/);
      const dynMatch = text.match(/\[DYNAMIC_UI\]([\s\S]*?)(?:\[CODE_GEN\]|\[CODE_PARSE\]|$)/);
      const codeMatch = text.match(/\[CODE_GEN\]([\s\S]*?)(?:\[CODE_PARSE\]|$)/);
      const parseMatch = text.match(/\[CODE_PARSE\]([\s\S]*?)$/);
      
      function extractSection(match) {
        if (!match) return "";
        return match[1].split('\n')
          .map(l => { const m = l.match(/^#\\?\s?(.*)/); return m ? m[1] : null; })
          .filter(l => l !== null)
          .join('\n').trim();
      }
      
      const rawId = mId ? mId[1].trim() : fileName.replace('.spr','');
      inputBuilderId.value = rawId;
      inputBuilderId.readOnly = false;
      inputBuilderId.dataset.editFile = filePath;
      document.getElementById('builder-id-namespace').textContent = currentEditingPlugin ? `${currentEditingPlugin}.` : '';
      document.getElementById('builder-id-error').classList.add('hidden');
      
      updateCategorySelect();
      if (mCat) {
        const sel = document.getElementById('builder-category');
        for (let i = 0; i < sel.options.length; i++) {
          if (sel.options[i].value === mCat[1].trim()) { sel.selectedIndex = i; break; }
        }
      }
      inputBuilderShape.value = mShape ? mShape[1].trim() : 'statement';
      const col = mColor ? mColor[1].trim() : '#38bdf8';
      inputBuilderColor.value = col;
      inputBuilderColorPicker.value = col;
      if (inputBuilderWriteStart) {
        inputBuilderWriteStart.value = writeStartMatches.map(m => m[1].trim()).join('\n');
      }
      
      // Объединяем UI и DYNAMIC_UI в одно поле для легаси совместимости
      let uiVal = extractSection(uiMatch);
      const dynVal = extractSection(dynMatch);
      if (dynVal) uiVal += '\n[DYNAMIC_UI]\n' + dynVal;
      
      // Если тело написано в новом синтаксисе (без секций)
      if (!uiVal && !extractSection(codeMatch)) {
        // Читаем тело напрямую
        const lines = text.split('\n');
        let inBody = false;
        let body = [];
        for (const l of lines) {
           if (l.match(/^#\\?\s*block:/) || l.match(/^#\\?\s*category:/) || l.match(/^#\\?\s*color:/) || l.match(/^#\\?\s*shape:/) || l.match(/^#\\?\s*write_start:/)) continue;
           if (!inBody && l.trim() !== '') inBody = true;
           if (inBody) {
              const m = l.match(/^#\\?\s?(.*)/);
              if (m) body.push(m[1]);
           }
        }
        inputBuilderUi.value = body.join('\n').trim();
        inputBuilderCode.value = "";
        if (inputBuilderParse) inputBuilderParse.value = "";
      } else {
        inputBuilderUi.value = uiVal;
        inputBuilderCode.value = extractSection(codeMatch);
        if (inputBuilderParse) inputBuilderParse.value = extractSection(parseMatch);
      }
      
      document.getElementById('block-builder-modal-box').querySelector('h2').textContent = `Редактирование: ${rawId}`;
      
      blockBuilderModal.classList.remove('hidden');
      setTimeout(() => {
        blockBuilderBox.classList.remove('scale-95', 'opacity-0');
        setTimeout(updateBlockPreview, 150);
      }, 10);
    } catch(err) {
      appAlert('Ошибка при открытии файла блока: ' + err.message);
    }
  }

  // Удалить плагин (из настроек)
  if (target.closest('#btn-plugin-settings-delete')) {
    if (await appConfirm(`Вы уверены, что хотите удалить этот плагин со всеми его блоками и ресурсами?`)) {
      try {
        const pluginName = currentPluginSettingsName;
        await window.spraute.rmdir(`plugins/${pluginName}`);
        
        // Удаляем из списка порядка
        allPluginsData = allPluginsData.filter(p => p.name !== pluginName);
        await savePluginsOrder();
        
        // Закрываем модалку настроек
        document.getElementById('plugin-settings-modal-box').classList.add('scale-95', 'opacity-0');
        setTimeout(() => document.getElementById('plugin-settings-modal').classList.add('hidden'), 200);
        
        // Перерисовываем список
        loadPluginsList();
      } catch(e) {
        appAlert('Ошибка при удалении плагина: ' + e.message);
      }
    }
  }

  // Открыть конструктор нового блока
  if (target.closest('#btn-create-block')) {
    inputBuilderId.value = '';
    inputBuilderId.readOnly = false;
    document.getElementById('builder-id-namespace').textContent = currentEditingPlugin ? `${currentEditingPlugin}.` : '';
    document.getElementById('builder-id-error').classList.add('hidden');
    updateCategorySelect();
    inputBuilderShape.value = 'statement';
    const firstCatColor = Object.values(pluginCategories)[0] || '#38bdf8';
    inputBuilderColor.value = firstCatColor;
    inputBuilderColorPicker.value = firstCatColor;
    if (inputBuilderWriteStart) inputBuilderWriteStart.value = '';
    
    // Делаем пример более сложным (наведение)
    inputBuilderUi.value = `row: [npc: dropdown_npc] "Смотреть" [mode: dropdown(один раз: lookat, всегда: alwayslookat, перестать: stoplookat)] "на" [target_type: dropdown(НИПа: npc, игрока: player, моба: mob)]
if mode == "stoplookat":
  template: {npc}.stoplookat()
if mode == "lookat" or mode == "alwayslookat":
  if target_type == "npc":
    row: "по имени" [target_npc: dropdown_npc]
    template: {npc}.{mode}("{target_npc}")
  if target_type == "player":
    input: target_player (type: value) "переменная игрока"
    template: {npc}.{mode}({target_player})
  if target_type == "mob":
    row: "моб" (target_mob: text: "zombie")
    template: {npc}.{mode}("{target_mob}")`;
    
    inputBuilderCode.value = ``;
    if (inputBuilderParse) inputBuilderParse.value = '';
    document.getElementById('block-builder-modal-box').querySelector('h2').textContent = 'Конструктор визуального блока';
    
    blockBuilderModal.classList.remove('hidden');
    setTimeout(() => {
      blockBuilderBox.classList.remove('scale-95', 'opacity-0');
      setTimeout(updateBlockPreview, 150);
    }, 10);
  }
  
  // Валидация ID в реальном времени
  if (target.closest('#builder-id')) {
    document.getElementById('builder-id').addEventListener('input', (ev) => {
      const v = ev.target.value;
      const valid = /^[a-z0-9_]*$/.test(v);
      document.getElementById('builder-id-error').classList.toggle('hidden', valid);
      ev.target.value = v.toLowerCase().replace(/[^a-z0-9_]/g, '');
    }, { once: true });
  }
  
  // Закрыть конструктор блока
  if (target.closest('#btn-close-block-builder') || target.closest('#btn-builder-cancel')) {
    blockBuilderBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => blockBuilderModal.classList.add('hidden'), 200);
  }
  
  // Синхронизация цветов в конструкторе
  if (target.closest('#builder-color-picker')) {
    inputBuilderColorPicker.addEventListener('input', () => {
      inputBuilderColor.value = inputBuilderColorPicker.value;
    }, {once: true});
  }
  if (target.closest('#builder-color')) {
    inputBuilderColor.addEventListener('input', () => {
      inputBuilderColorPicker.value = inputBuilderColor.value;
    }, {once: true});
  }

  // Обновление предпросмотра
  if (target.closest('#btn-builder-preview')) {
    updateBlockPreview();
  }

  // Сохранить блок
  if (target.closest('#btn-builder-save')) {
    if (!currentEditingPlugin) return;
    const blockId = inputBuilderId.value.trim();
    if (!blockId) { appAlert("ID блока не может быть пустым!"); return; }
    if (!/^[a-z0-9_]+$/.test(blockId)) {
      appAlert("ID блока может содержать только маленькие латинские буквы, цифры и нижнее подчёркивание!");
      return;
    }
    
    let fullUi = inputBuilderUi.value;
    
    let content = "";
    if (!inputBuilderCode.value.trim() && (!inputBuilderParse || !inputBuilderParse.value.trim()) && !fullUi.includes('[UI]')) {
       // Unified syntax
       let bodyContent = fullUi.split('\n').map(l => '#\\ ' + l).join('\n');
       content = [
          `#\\ block: ${blockId}`,
          `#\\ category: ${inputBuilderCategory.value}`,
          `#\\ color: ${inputBuilderColor.value}`,
          `#\\ shape: ${inputBuilderShape.value}`,
          ...builderWriteStartMetaLines(),
          `#\\`,
          bodyContent
       ].filter(Boolean).join('\n') + '\n';
    } else {
       // Legacy syntax
       let staticPart = fullUi, dynPart = "";
       if (fullUi.includes('[DYNAMIC_UI]')) {
         const idx = fullUi.indexOf('[DYNAMIC_UI]');
         staticPart = fullUi.slice(0, idx).trim();
         dynPart = fullUi.slice(idx + '[DYNAMIC_UI]'.length).trim();
       }
       
       let uiContent = staticPart.split('\n').map(l => '#\\ ' + l).join('\n');
       let dynContent = dynPart ? dynPart.split('\n').map(l => '#\\ ' + l).join('\n') : '';
       let codeContent = inputBuilderCode.value.split('\n').map(l => '#\\ ' + l).join('\n');
       let parseContent = inputBuilderParse && inputBuilderParse.value ? inputBuilderParse.value.split('\n').map(l => '#\\ ' + l).join('\n') : '';
       
       content = [
          `#\\ block: ${blockId}`,
          `#\\ category: ${inputBuilderCategory.value}`,
          `#\\ color: ${inputBuilderColor.value}`,
          `#\\ shape: ${inputBuilderShape.value}`,
          ...builderWriteStartMetaLines(),
          `#\\`,
          `#\\ [UI]`,
          uiContent,
          dynContent ? `#\\\n#\\ [DYNAMIC_UI]\n${dynContent}` : '',
          `#\\`,
          `#\\ [CODE_GEN]`,
          codeContent,
          parseContent ? `#\\\n#\\ [CODE_PARSE]\n${parseContent}` : ''
       ].filter(Boolean).join('\n') + '\n';
    }
    
    try {
      const pPath = `plugins/${currentEditingPlugin}/blocks/${blockId}.spr`;
      await window.spraute.writeFile(pPath, content);
      
      blockBuilderBox.classList.add('scale-95', 'opacity-0');
      setTimeout(() => blockBuilderModal.classList.add('hidden'), 200);
      
      setStatus(`Блок ${currentEditingPlugin}.${blockId} сохранён`);
      loadPluginBlocks(currentEditingPlugin);
    } catch (err) {
      appAlert("Ошибка при сохранении блока: " + err.message);
    }
  }

  // Показать документацию
  if (target.closest('#btn-builder-docs')) {
    e.preventDefault();
    const builderDocsModal = document.getElementById('builder-docs-modal');
    const builderDocsBox = document.getElementById('builder-docs-modal-box');
    if (builderDocsModal && builderDocsBox) {
      builderDocsModal.classList.remove('hidden');
      setTimeout(() => {
        builderDocsBox.classList.remove('scale-95', 'opacity-0');
      }, 10);
      
      Promise.resolve(visualBlocksDocs)
        .then(text => {
          let html = text
            .replace(/^# (.*$)/gim, '<h1 class="text-xl font-bold text-white mb-4">$1</h1>')
            .replace(/^## (.*$)/gim, '<h2 class="text-lg font-bold text-primary mt-6 mb-3 border-b border-white/10 pb-2">$1</h2>')
            .replace(/^### (.*$)/gim, '<h3 class="text-md font-bold text-secondary mt-4 mb-2">$1</h3>')
            .replace(/\*\*(.*)\*\*/gim, '<strong>$1</strong>')
            .replace(/\*(.*)\*/gim, '<em>$1</em>')
            .replace(/```(?:spraute|html|javascript)?\n([\s\S]*?)```/gim, '<pre class="bg-black/30 p-4 rounded-xl border border-white/5 my-4 overflow-x-auto text-xs font-mono"><code>$1</code></pre>')
            .replace(/`([^`]+)`/gim, '<code class="bg-black/20 px-1.5 py-0.5 rounded text-primary">$1</code>')
            .replace(/^\- (.*$)/gim, '<li class="ml-4 list-disc">$1</li>')
            .replace(/\n\n/gim, '<br>');
          document.getElementById('builder-docs-content').innerHTML = html;
        })
        .catch(err => {
          document.getElementById('builder-docs-content').innerHTML = 'Ошибка загрузки документации: ' + err.message;
        });
    }
  }

  // Закрыть модалку документации
  if (target.closest('#btn-close-builder-docs')) {
    const builderDocsModal = document.getElementById('builder-docs-modal');
    const builderDocsBox = document.getElementById('builder-docs-modal-box');
    if (builderDocsBox && builderDocsModal) {
      builderDocsBox.classList.add('scale-95', 'opacity-0');
      setTimeout(() => builderDocsModal.classList.add('hidden'), 200);
    }
  }
});

window.deletePluginBlock = async function(filePath, fileName) {
  if (await appConfirm(`Удалить блок "${fileName}"? Это действие нельзя отменить.`)) {
    try {
      await window.spraute.unlink(filePath);
      loadPluginBlocks(currentEditingPlugin);
    } catch(e) {
      appAlert('Ошибка при удалении: ' + e.message);
    }
  }
};

async function loadPluginBlocks(pluginName) {
  pluginBlocksList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Загрузка...</div>';
  try {
    const blocksPath = `plugins/${pluginName}/blocks`;
    const exists = await window.spraute.exists(blocksPath);
    if (!exists) {
      pluginBlocksList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Блоков пока нет.</div>';
      return;
    }
    
    const files = await window.spraute.listDir(blocksPath);
    const sprFiles = files.filter(f => !f.isDir && f.name.endsWith('.spr'));
    
    if (sprFiles.length === 0) {
      pluginBlocksList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Блоков пока нет. Создайте первый!</div>';
      return;
    }
    
    let html = '';
    for (const f of sprFiles) {
      let bName = f.name;
      let bCat = "Без категории";
      let bColor = "#555555";
      try {
        const text = await window.spraute.readFile(f.rel, 'utf8');
        const mCat = text.match(/#\\\s*category:\s*(.*)/);
        const mColor = text.match(/#\\\s*color:\s*(.*)/);
        if (mCat) bCat = mCat[1].trim();
        if (mColor) bColor = mColor[1].trim();
      } catch(e){}
      
      html += `
      <div class="flex items-center justify-between p-3 rounded-xl bg-black/20 border border-white/5 group">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded border border-white/10 flex items-center justify-center shadow-sm" style="background-color: ${bColor}40;">
            <div class="w-4 h-4 rounded-sm" style="background-color: ${bColor};"></div>
          </div>
          <div>
            <div class="font-mono text-sm text-white">${bName}</div>
            <div class="text-[10px] text-on-variant uppercase tracking-wider mt-0.5">${bCat}</div>
          </div>
        </div>
        <div class="flex gap-2">
          <button class="px-3 py-1.5 bg-primary/20 hover:bg-primary/40 rounded-lg text-xs text-primary transition-colors btn-edit-block" 
            data-file="${f.rel}" data-name="${f.name}">Изменить</button>
          <button class="px-3 py-1.5 bg-red-500/20 hover:bg-red-500/40 rounded-lg text-xs text-red-400 transition-colors" 
            onclick="window.deletePluginBlock('${f.rel.replace(/\\/g, '\\\\\\\\')}', '${f.name}')">Удалить</button>
        </div>
      </div>
      `;
    }
    pluginBlocksList.innerHTML = html;
  } catch(e) {
    pluginBlocksList.innerHTML = `<div class="text-center text-red-400 py-8 text-xs">Ошибка: ${e.message}</div>`;
  }
}

// --- Импорт скрипта для справки (не влияет на список НИПов — только import() в скрипте) ---
const btnImportScript = document.getElementById('btn-import-script');

if (btnImportScript) {
  btnImportScript.addEventListener('click', async () => {
    const name = await appPrompt('Путь к скрипту для импорта (например, scripts/my_lib.spr):');
    if (!name || !name.trim()) return;
    
    try {
      await window.spraute.readFile(name.trim(), 'utf8');
      VisualEngine.scanInBackground(currentEditor ? currentEditor.state.doc.toString() : '').then(() => {
        VisualEngine.applyCache();
      });
      setStatus(`Скрипт ${name} прочитан. Для НИПов в dropdown используйте import("...") в скрипте.`);
    } catch(e) {
      appAlert(`Ошибка при импорте ${name}: ` + e.message);
    }
  });
}

// Legacy — заменён VisualEngine.scanInBackground + VisualEngine.preloadPluginBlocks

window.addEventListener('error', (e) => {
  const errDiv = document.createElement('div');
  errDiv.style = "position:absolute; z-index:9999; top:0; left:0; background:rgba(255,0,0,0.8); color:white; padding:10px; width:100%;";
  errDiv.innerText = "Runtime Error: " + e.message + " in " + e.filename + ":" + e.lineno;
  document.body.appendChild(errDiv);
});
window.addEventListener('unhandledrejection', (e) => {
  if (e.reason && e.reason.toString().includes('decodeAudioData')) {
    e.preventDefault();
    return; // Игнорируем ошибку звука от Blockly
  }
  const errDiv = document.createElement('div');
  errDiv.style = "position:absolute; z-index:9999; top:50px; left:0; background:rgba(255,0,0,0.8); color:white; padding:10px; width:100%;";
  errDiv.innerText = "Promise Rejection: " + e.reason;
  document.body.appendChild(errDiv);
});

// Запуск при загрузке DOM
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
