import { EditorView, basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
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

import * as Blockly from 'blockly/core';
import { SprauteGenerator, textToBlocks, SprauteTheme, applyBlocklyThemeColors, updateDynamicLists, parseCustomBlocks, getDynamicToolbox, customCategories, clearCustomCategories } from './visual.js';

import { visualBlocksDocs } from './docs.js';

const sprauteLanguage = StreamLanguage.define({
  token(stream, state) {
    if (stream.eatSpace()) return null;
    if (stream.match(/^#.*/)) return "comment";
    if (stream.match(/^"[^"]*"/)) return "string";
    if (stream.match(/^"[^"]*$/)) return "error";
    if (stream.match(/^-?\d+(?:\.\d+)?/)) return "number";
    if (stream.match(/^(val|fun|on|create|npc|ui|if|else|elif|while|for|in|return|break|continue|true|false|null|await|time|say|playFreeze|playOnce|playLoop|stop|alwaysLookAt|lookAt)\b/)) return "keyword";
    if (stream.match(/^(text|button|pos|anchor|size|color|scale|model|texture|animation|name|hp|show_name|background|slot|image|rect|progress|pitch|yaw|look_x|look_y|look_z)\b/)) return "propertyName";
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
  "val", "fun", "on", "create", "npc", "ui", "if", "else", "elif", "while", "for", "in", 
  "return", "break", "continue", "true", "false", "null", "await", "time", "say", 
  "playFreeze", "playOnce", "playLoop", "stop", "alwaysLookAt", "lookAt"
].map(kw => ({label: kw, type: "keyword"}));

const sprauteProperties = [
  // Базовые параметры NPC
  "name", "hp", "speed", "pos", "rotate", "showName", "collision", "model", "texture", "idleAnim", "walkAnim", "head",
  
  // Свойства сущностей
  "x", "y", "z", "pitch", "yaw", "look_x", "look_y", "look_z", "uuid", "java", "data", "savedData",
  
  // Параметры кастомных блоков
  "texture_up", "texture_down", "texture_north", "texture_south", "texture_west", "texture_east",
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
  
  // Остальное
  "text", "button", "slot", "image", "rect", "progress"
].map(prop => ({label: prop, type: "property"}));

const sprauteFunctionsList = [
  // Базовые функции
  "chat(${1:message})",
  "npc(${1:name}, ${2:hp}, ${3:speed}, ${4:x}, ${5:y}, ${6:z}, ${7:yaw}, ${8:pitch})",
  "say(${1:who}, ${2:message})",
  "setNamesColor(${1:color})",
  "getNearestPlayer(${1:anchor})",
  "getSlot(${1:player}, ${2:slot})",
  "hasItem(${1:player}, ${2:item_id})",
  "countItem(${1:player}, ${2:item_id})",
  "getPlayer(${1:name})",
  "setBlock(${1:x}, ${2:y}, ${3:z}, ${4:block_id})",
  "heldItem(${1:hand})",
  "heldItemNbt(${1:hand})",
  "giveItem(${1:player}, ${2:item_id}, ${3:count})",
  "getHeldItem(${1:player})",
  "execute(${1:command})",
  "taskDone(${1:task_id})",
  "intStr(${1:x})",
  "wholeStr(${1:x})",
  "random()",
  "listCreate()",
  "dictCreate()",
  "playSound(${1:player}, ${2:sound_id})",
  "stopSound(${1:player})",
  "npc_chat(${1:player}, ${2:npc}, ${3:message}, ${4:color})",

  // UI
  "uiOpen(${1:player}, ${2:template})",
  "uiClose(${1:player})",
  "overlayOpen(${1:player}, ${2:template})",
  "overlayClose(${1:player})",
  "uiUpdate(${1:player}, ${2:widget_id}, ${3:field}, ${4:value})",
  "uiAnimate(${1:player}, ${2:widget_id}, ${3:field}, ${4:value}, ${5:duration})",
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
  "setFlying(${1:flying})",
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
  "fadeOut()"
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
  snippetCompletion('create block ${1:name} {\n  texture = "textures/block/${1}.png"\n  hardness = ${2:1.5}\n  ${3}\n}', {label: "create block", detail: "definition", type: "keyword"}),
  snippetCompletion('create item ${1:name} {\n  texture = "textures/item/${1}.png"\n  ${2}\n}', {label: "create item", detail: "definition", type: "keyword"}),
  snippetCompletion('fadeIn {\n  text = "${1:Title}"\n  subtitle = "${2:Subtitle}"\n  time = ${3:1.0}\n  visible_time = ${4:2.0}\n  fadeout = ${5:true}\n  color = ${6:0x111111}\n}', {label: "fadeIn", detail: "block", type: "keyword"}),
  
  // on События
  snippetCompletion('on interact(${1:target}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on interact", detail: "event", type: "keyword"}),
  snippetCompletion('on keybind("${1:key}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on keybind", detail: "event", type: "keyword"}),
  snippetCompletion('on death(${1:target}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on death", detail: "event", type: "keyword"}),
  snippetCompletion('on pickup(${1:npc}, "${2:item_id}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on pickup", detail: "event", type: "keyword"}),
  snippetCompletion('on uiClick(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on uiClick", detail: "event", type: "keyword"}),
  snippetCompletion('on uiClose(${1:player}) -> ${2:handlerId} {\n  ${3}\n}', {label: "on uiClose", detail: "event", type: "keyword"}),
  snippetCompletion('on uiInput(${1:player}, "${2:widget_id}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on uiInput", detail: "event", type: "keyword"}),
  snippetCompletion('on position(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:radius}) -> ${6:handlerId} {\n  ${7}\n}', {label: "on position", detail: "event", type: "keyword"}),
  snippetCompletion('on inventory(${1:player}, "${2:item_id}", ${3:count}) -> ${4:handlerId} {\n  ${5}\n}', {label: "on inventory", detail: "event", type: "keyword"}),
  snippetCompletion('on clickBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on clickBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on breakBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on breakBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on placeBlock("${1:target}") -> ${2:handlerId} {\n  ${3}\n}', {label: "on placeBlock", detail: "event", type: "keyword"}),
  snippetCompletion('on chat(${1:player}, "${2:message}") -> ${3:handlerId} {\n  ${4}\n}', {label: "on chat", detail: "event", type: "keyword"}),

  // await Ожидания
  snippetCompletion('await time(${1:seconds})', {label: "await time", detail: "wait", type: "keyword"}),
  snippetCompletion('await interact(${1:entity})', {label: "await interact", detail: "wait", type: "keyword"}),
  snippetCompletion('await next', {label: "await next", detail: "wait", type: "keyword"}),
  snippetCompletion('await keybind("${1:key}")', {label: "await keybind", detail: "wait", type: "keyword"}),
  snippetCompletion('await death(${1:target})', {label: "await death", detail: "wait", type: "keyword"}),
  snippetCompletion('await pickup(${1:npc}, ${2:amount}, "${3:item_id}")', {label: "await pickup", detail: "wait", type: "keyword"}),
  snippetCompletion('await task("${1:task_id}")', {label: "await task", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiClick(${1:player})', {label: "await uiClick", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiClose(${1:player})', {label: "await uiClose", detail: "wait", type: "keyword"}),
  snippetCompletion('await uiInput(${1:player}, "${2:widget_id}")', {label: "await uiInput", detail: "wait", type: "keyword"}),
  snippetCompletion('await position(${1:player}, ${2:x}, ${3:y}, ${4:z}, ${5:radius})', {label: "await position", detail: "wait", type: "keyword"}),
  snippetCompletion('await inventory(${1:player}, "${2:item_id}", ${3:count})', {label: "await inventory", detail: "wait", type: "keyword"}),
  snippetCompletion('await clickBlock(${1:player}, "${2:target}")', {label: "await clickBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await breakBlock(${1:player}, "${2:target}")', {label: "await breakBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await placeBlock(${1:player}, "${2:target}")', {label: "await placeBlock", detail: "wait", type: "keyword"}),
  snippetCompletion('await chat(${1:player}, "${2:message}")', {label: "await chat", detail: "wait", type: "keyword"}),

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
    if (!el) {
      el = document.createElement('div');
      el.id = 'visual-transition-overlay';
      el.className = 'absolute inset-0 z-50 bg-background/90 backdrop-blur-sm flex flex-col items-center justify-center gap-3 transition-opacity duration-300';
      el.innerHTML = `
        <div class="relative w-10 h-10">
          <div class="absolute inset-0 rounded-full border-2 border-white/5"></div>
          <div class="absolute inset-0 rounded-full border-2 border-t-primary border-r-transparent border-b-transparent border-l-transparent animate-spin"></div>
        </div>
        <span id="visual-transition-label" class="text-sm text-on-variant font-mono"></span>
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
    if (lbl) lbl.textContent = label || 'Подготовка визуального режима...';
    el.style.display = '';
    el.classList.remove('opacity-0');
  } else if (el) {
    el.classList.add('opacity-0');
    setTimeout(() => { if (el) el.style.display = 'none'; }, 300);
  }
}

// Инициализация
async function init() {
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
          await loadDirectory('');
          setStatus(`Перемещено: ${srcName} → ${item.name}/`);
        } catch (err) {
          appAlert(`Ошибка перемещения: ${err.message}`);
        }
      }
    });
  }
}

// Загрузка дерева файлов (рекурсивная реализация)
async function loadDirectory(relPath, containerEl = null, level = 0, forceExpand = false) {
  const treeContainer = containerEl || document.getElementById('file-tree');
  if (!containerEl) {
    treeContainer.innerHTML = '<div class="text-center text-on-variant mt-4 animate-pulse">Загрузка...</div>';
  }
  
  try {
    const items = await window.spraute.listDir(relPath);
    if (!containerEl) treeContainer.innerHTML = '';
    
    if (items.length === 0 && !containerEl) {
      treeContainer.innerHTML = '<div class="px-4 py-2 text-on-variant italic">Пусто</div>';
      return;
    }

    // Если папка содержит только 1 элемент и это тоже папка — раскрываем автоматически (или если forceExpand)
    const shouldAutoExpand = items.length === 1 && items[0].isDir;

    for (const item of items) {
      const wrapper = document.createElement('div');
      
      const el = document.createElement('div');
      el.className = 'flex items-center gap-2 py-1.5 hover:bg-white/5 cursor-pointer text-on-surface hover:text-white transition-colors group';
      el.style.paddingLeft = `${(level * 16) + 16}px`;
      el.style.paddingRight = '16px';
      el.dataset.filePath = item.rel;
      
      let icon = 'draft';
      let iconColor = 'text-on-variant';
      let isFilled = 0;
      
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
        if (item.name.endsWith('.spr')) { icon = 'description'; iconColor = 'text-primary'; isFilled = 1; }
        else if (item.name.endsWith('.json')) { icon = 'data_object'; iconColor = 'text-yellow-200'; }
        else if (item.name.endsWith('.png')) { icon = 'image'; iconColor = 'text-purple-300'; }
      }
      
      el.innerHTML = `
        <span class="material-symbols-outlined text-[16px] ${iconColor} transition-transform ${item.isDir ? 'group-hover:scale-110' : ''}" style="font-variation-settings: 'FILL' ${isFilled}">${icon}</span>
        <span class="truncate flex-1">${item.name}</span>
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
            if (!isLoaded) {
              await loadDirectory(item.rel, childrenContainer, level + 1, shouldAutoExpand);
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

        // Авто-открытие (если 1 папка внутри)
        if (shouldAutoExpand || forceExpand) {
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
    tab = {
      path: relPath,
      name: fileName,
      isImage: isImage,
      isDirty: false,
      state: null
    };
    openTabs.push(tab);
  }
  await switchToTab(relPath);
}

// Глобальный экземпляр редактора
let currentEditor = null;
let currentOpenFile = null;
let isVisualMode = false;
let blocklyWorkspace = null;
let _suppressDirty = false;

// ====== Система быстрого переключения визуального режима ======
const VisualEngine = {
  _ready: false,
  _blocksCached: false,
  _dynamicDataCached: false,
  _cachedNpcs: ['_event_npc'],
  _cachedAnims: [],
  _scanPromise: null,
  _initPromise: null,
  _lastScanText: '',

  // Инициализация Blockly workspace заранее (скрытый)
  async ensureWorkspace() {
    if (blocklyWorkspace) return blocklyWorkspace;
    if (this._initPromise) return this._initPromise;

    this._initPromise = new Promise((resolve) => {
      const blocklyMount = document.getElementById('blockly-mount');
      if (!blocklyMount) { resolve(null); return; }

      // Временно показываем blockly-mount для inject (Blockly требует видимый контейнер)
      const wasHidden = blocklyMount.classList.contains('hidden');
      if (wasHidden) {
        blocklyMount.style.position = 'absolute';
        blocklyMount.style.left = '-9999px';
        blocklyMount.style.top = '-9999px';
        blocklyMount.style.width = '800px';
        blocklyMount.style.height = '600px';
        blocklyMount.classList.remove('hidden');
        blocklyMount.style.display = 'block';
      }

      blocklyWorkspace = Blockly.inject('blockly-mount', {
        toolbox: getDynamicToolbox(),
        theme: SprauteTheme,
        grid: { spacing: 20, length: 0, snap: true },
        move: { scrollbars: true, drag: true, wheel: true },
        sounds: false
      });

      blocklyWorkspace.addChangeListener((e) => {
        if (_suppressDirty) return;
        if (e.isUiEvent) return;
        const tab = openTabs.find(t => t.path === activeTabPath);
        if (tab && isVisualMode) {
          if (!tab.isDirty) {
            tab.isDirty = true;
            renderTabs();
          }
        }
      });

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

      if (wasHidden) {
        blocklyMount.classList.add('hidden');
        blocklyMount.style.display = 'none';
        blocklyMount.style.position = '';
        blocklyMount.style.left = '';
        blocklyMount.style.top = '';
        blocklyMount.style.width = '';
        blocklyMount.style.height = '';
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

  async _doScan(currentText) {
    let npcs = ['_event_npc'];
    let anims = [];

    try {
      if (window.spraute) {
        const animFiles = await window.spraute.listDir('animations');
        for (const f of animFiles) {
          if (!f.isDir && f.name.endsWith('.json')) {
            const content = await window.spraute.readFile(f.rel, 'utf8');
            try {
              const json = JSON.parse(content);
              if (json.animations) {
                for (const aName in json.animations) {
                  if (!anims.includes(aName)) anims.push(aName);
                }
              }
            } catch(e) {}
          }
        }
      }
    } catch(e) {}

    let fullText = currentText + "\n" + importedScriptsText;

    if (window.spraute) {
      const importRegex = /import\s+["']([^"']+)["']/g;
      let match;
      let queue = [];
      while ((match = importRegex.exec(currentText)) !== null) {
        queue.push(match[1]);
      }
      let visited = new Set();
      while (queue.length > 0) {
        let f = queue.shift();
        if (visited.has(f)) continue;
        visited.add(f);
        try {
          let p = f.endsWith('.spr') ? f : `scripts/${f}.spr`;
          let content = await window.spraute.readFile(p, 'utf8');
          fullText += "\n" + content;
          let m2;
          while ((m2 = importRegex.exec(content)) !== null) {
            queue.push(m2[1]);
          }
        } catch(e) {}
      }
    }

    const matches = fullText.matchAll(/create\s+npc\s+(\w+)/g);
    for (const match of matches) {
      if (!npcs.includes(match[1])) npcs.push(match[1]);
    }
    if (anims.length === 0) anims.push("(нет анимаций)");

    this._cachedNpcs = npcs;
    this._cachedAnims = anims;
    this._dynamicDataCached = true;
    updateDynamicLists(npcs, anims);
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
        let isEnabled = true;
        try {
          const pData = JSON.parse(await window.spraute.readFile(`${p.rel}/plugin.json`, 'utf8'));
          if (pData.enabled === false) isEnabled = false;
        } catch(e){}
        if (!isEnabled) continue;

        const bPath = `plugins/${p.name}/blocks`;
        const bExists = await window.spraute.exists(bPath);
        if (!bExists) continue;
        
        const files = await window.spraute.listDir(bPath);
        for (const f of files) {
          if (!f.isDir && f.name.endsWith('.spr')) {
            const bText = await window.spraute.readFile(f.rel, 'utf8');
            parseCustomBlocks(bText, p.name.toLowerCase().replace(/[^a-z0-9_]/g, '_'));
          }
        }
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
      updateDynamicLists(this._cachedNpcs, this._cachedAnims);
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

  // === Per-file block cache ===
  _fileBlocksCache: new Map(), // path → { xml: string, textHash: string }

  _hashText(text) {
    let h = 0;
    for (let i = 0; i < text.length; i++) {
      h = ((h << 5) - h + text.charCodeAt(i)) | 0;
    }
    return h + '_' + text.length;
  },

  saveFileBlocks(filePath, sourceText) {
    if (!blocklyWorkspace || !filePath) return;
    try {
      const xml = Blockly.Xml.workspaceToDom(blocklyWorkspace);
      const hash = sourceText ? this._hashText(sourceText) : null;
      this._fileBlocksCache.set(filePath, {
        xml: Blockly.Xml.domToText(xml),
        textHash: hash
      });
    } catch(e) {}
  },

  restoreFileBlocks(filePath) {
    if (!blocklyWorkspace || !filePath) return false;
    const cached = this._fileBlocksCache.get(filePath);
    if (!cached) return false;
    const prev = _suppressDirty;
    _suppressDirty = true;
    try {
      blocklyWorkspace.clear();
      const xml = Blockly.utils.xml.textToDom(cached.xml);
      Blockly.Xml.domToWorkspace(xml, blocklyWorkspace);
      return true;
    } catch(e) {
      return false;
    } finally {
      _suppressDirty = prev;
    }
  },

  isCacheValidForText(filePath, text) {
    const cached = this._fileBlocksCache.get(filePath);
    if (!cached || !cached.textHash) return false;
    return cached.textHash === this._hashText(text);
  },

  clearFileCache(filePath) {
    if (filePath) this._fileBlocksCache.delete(filePath);
  },

  // === Live sync — debounced сканирование при редактировании ===
  _liveSyncTimer: null,

  scheduleLiveSync(text, filePath) {
    // Text changed — cached blocks for this file are stale
    if (filePath) {
      const cached = this._fileBlocksCache.get(filePath);
      if (cached && cached.textHash !== this._hashText(text)) {
        this._fileBlocksCache.delete(filePath);
      }
    }
    if (this._liveSyncTimer) clearTimeout(this._liveSyncTimer);
    this._liveSyncTimer = setTimeout(() => {
      this._liveSyncTimer = null;
      if (text !== this._lastScanText) {
        this.scanInBackground(text);
      }
    }, 2000);
  }
};

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
    
    const blocklyMount = document.getElementById('blockly-mount');
    if (blocklyMount) {
      blocklyMount.classList.add('hidden');
      blocklyMount.style.display = 'none';
    }
    
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
    
    isVisualMode = false; // Сбрасываем визуальный режим при закрытии всех вкладок
    const btnToggleVisual = document.getElementById('btn-toggle-visual');
    if (btnToggleVisual) {
      btnToggleVisual.classList.add('hidden');
      btnToggleVisual.innerHTML = '<span class="material-symbols-outlined text-[16px]">extension</span> Визуальный код';
      btnToggleVisual.classList.remove('bg-primary/20', 'text-primary');
    }
    return;
  }
  
  container.classList.remove('hidden');
  document.getElementById('empty-state').classList.add('hidden');
  
  if (activeTabPath && activeTabPath.endsWith('.spr')) {
    const btnToggleVisual = document.getElementById('btn-toggle-visual');
    if (btnToggleVisual) btnToggleVisual.classList.remove('hidden');
  } else {
    const btnToggleVisual = document.getElementById('btn-toggle-visual');
    if (btnToggleVisual) btnToggleVisual.classList.add('hidden');
    if (isVisualMode) toggleVisualMode();
  }
  
  container.innerHTML = '';
  openTabs.forEach(tab => {
    const tabEl = document.createElement('div');
    const isActive = tab.path === activeTabPath;
    
    tabEl.className = `h-full flex items-center px-4 gap-2 cursor-pointer border-r border-white/5 transition-colors max-w-[200px] shrink-0
      ${isActive ? 'bg-background text-primary border-t-2 border-t-primary' : 'bg-surface-container text-on-variant hover:bg-surface-bright border-t-2 border-t-transparent'}`;
    
    // Иконка
    let icon = 'description';
    let isFilled = isActive ? 1 : 0;
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
        // Если у нас включен визуальный режим для всех файлов, и мы переключаемся на скрипт - сохраняем его визуальным
        switchToTab(tab.path).then(async () => {
          if (tab.path.endsWith('.spr')) {
            const visualPref = await window.spraute.storeGet('visualModeEnabled');
            if (visualPref && !isVisualMode) {
              toggleVisualMode();
            } else if (!visualPref && isVisualMode) {
               toggleVisualMode();
            }
          }
        });
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

  // Если закрываем активный таб в визуальном режиме — синхронизируем блоки → код
  if (path === activeTabPath && isVisualMode && blocklyWorkspace && currentEditor) {
    const code = SprauteGenerator.workspaceToCode(blocklyWorkspace);
    // Защита: если генератор вернул пустоту, а в редакторе есть текст — не затираем
    if (code && code.trim().length > 0) {
      VisualEngine.saveFileBlocks(path, code);
      currentEditor.dispatch({
        changes: { from: 0, to: currentEditor.state.doc.length, insert: code }
      });
    }
    tab.state = currentEditor.state;
    if (!tab.isDirty) {
      tab.isDirty = true;
    }
  } else if (path === activeTabPath && currentEditor) {
    tab.state = currentEditor.state;
  }

  if (tab.isDirty) {
    const confirm = await appConfirm(`Сохранить изменения в "${tab.name}" перед закрытием?`);
    if (confirm) {
      await saveTab(tab.path);
    }
  }

  openTabs.splice(tabIndex, 1);
  VisualEngine.clearFileCache(path);

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
    let contentToSave;
    if (path === activeTabPath && isVisualMode && blocklyWorkspace) {
      contentToSave = SprauteGenerator.workspaceToCode(blocklyWorkspace);
      contentToSave = await injectPluginGlobals(contentToSave);
      
      if (currentEditor) {
        currentEditor.dispatch({
          changes: {
            from: 0,
            to: currentEditor.state.doc.length,
            insert: contentToSave
          }
        });
      }
    } else if (path === activeTabPath && currentEditor) {
      contentToSave = currentEditor.state.doc.toString();
      // contentToSave = await injectPluginGlobals(contentToSave); // Можно добавлять и в коде, но лучше только при визуальном
    } else if (tab.state) {
      contentToSave = tab.state.doc.toString();
    } else {
      return; // Нет состояния для сохранения
    }
    
    await window.spraute.writeFile(path, contentToSave);
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
  if (currentEditor && activeTabPath) {
    const activeTab = openTabs.find(t => t.path === activeTabPath);
    if (activeTab && !activeTab.isImage) {
      if (isVisualMode && blocklyWorkspace) {
        const code = SprauteGenerator.workspaceToCode(blocklyWorkspace);
        VisualEngine.saveFileBlocks(activeTabPath, code);
        currentEditor.dispatch({
          changes: { from: 0, to: currentEditor.state.doc.length, insert: code }
        });
      }
      activeTab.state = currentEditor.state;
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
  
  if (tab.isImage) {
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
  } else {
    try {
      editorMount.innerHTML = ''; // Очищаем контейнер
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
        ".cm-activeLineGutter": {
          backgroundColor: "rgba(255,255,255,0.05)"
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
      console.log('[Spraute] Syntax theme loaded:', syntaxThemeName);
      
      let highlightStyles;
      if (syntaxThemeName === 'vscode-dark') {
        highlightStyles = [
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
      } else if (syntaxThemeName === 'monokai') {
        highlightStyles = [
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
      } else if (syntaxThemeName === 'github-dark') {
        highlightStyles = [
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
      } else {
        highlightStyles = [
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
      
      const customHighlightStyle = HighlightStyle.define(highlightStyles);

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
            if (path.endsWith('.spr')) {
              VisualEngine.scheduleLiveSync(update.state.doc.toString(), path);
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
    
    // Если визуальный режим включен, загружаем блоки
    if (isVisualMode) {
      const hasCachedBlocks = VisualEngine.isCacheValidForText(path, stateToUse.doc.toString());
      if (!hasCachedBlocks) {
        showVisualTransition(true, 'Загрузка блоков...');
        await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      }
      await VisualEngine.ensureWorkspace();
      if (blocklyWorkspace) {
        const text = stateToUse.doc.toString();
        _suppressDirty = true;
        try {
          if (hasCachedBlocks) {
            VisualEngine.restoreFileBlocks(path);
          } else {
            try {
              textToBlocks(text, blocklyWorkspace);
            } catch (e) {
              console.error("Parse error", e);
            }
            VisualEngine.saveFileBlocks(path, text);
          }
        } finally { _suppressDirty = false; }
        Blockly.svgResize(blocklyWorkspace);
      }
      if (!hasCachedBlocks) showVisualTransition(false);
    }

    // Фоновый ресканинг для подготовки визуального режима
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
    loadDirectory(''); // Перезагружаем корень
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
    loadDirectory('');
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
    loadDirectory('');
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
    }
    document.getElementById('empty-state').classList.remove('hidden');
    document.getElementById('editor-mount').innerHTML = '';
    loadDirectory('');
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

document.addEventListener('keydown', async (e) => {
  // Отмена обработки если фокус в инпуте или редакторе
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.closest('.cm-editor')) {
    return;
  }
  
  // Если визуальный режим активен, перехватываем горячие клавиши для блоков
  if (isVisualMode && blocklyWorkspace && (e.target.closest('#blockly-mount') || e.target.closest('.blocklyWidgetDiv'))) {
    const selected = Blockly.common.getSelected();
    
    if (e.key === 'Delete' || e.key === 'Backspace') {
      if (selected && selected.isDeletable()) {
        selected.dispose();
        e.preventDefault();
      }
      return;
    }
    
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'c' || e.key === 'с' || e.code === 'KeyC')) {
      if (selected && selected.isDeletable()) {
        Blockly.clipboard.copy(selected);
        e.preventDefault();
      }
      return;
    }
    
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'x' || e.key === 'ч' || e.code === 'KeyX')) {
      if (selected && selected.isDeletable()) {
        Blockly.clipboard.copy(selected);
        selected.dispose();
        e.preventDefault();
      }
      return;
    }
    
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'v' || e.key === 'м' || e.code === 'KeyV')) {
      const pasted = Blockly.clipboard.paste();
      if (pasted) {
        e.preventDefault();
      }
      return;
    }
    
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'z' || e.key === 'я' || e.code === 'KeyZ')) {
      blocklyWorkspace.undo(e.shiftKey);
      e.preventDefault();
      return;
    }
    
    if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === 'y' || e.key === 'н' || e.code === 'KeyY')) {
      blocklyWorkspace.undo(true);
      e.preventDefault();
      return;
    }
  }

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
      loadDirectory('');
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
    loadDirectory('');
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
    loadDirectory('');
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
const cbAutoUpdate = document.getElementById('setting-auto-update');
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
  
  const autoUpdate = await window.spraute.storeGet('autoUpdate');
  cbAutoUpdate.checked = autoUpdate !== false; // По умолчанию включено

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
    
    showStatus('Тема синтаксиса изменена');
  });
}

btnCloseSettings.addEventListener('click', () => {
  settingsBox.classList.add('scale-95', 'opacity-0');
  setTimeout(() => {
    settingsModal.classList.add('hidden');
  }, 200);
});

cbAutoUpdate.addEventListener('change', async (e) => {
  await window.spraute.storeSet('autoUpdate', e.target.checked);
});

btnChangeMcPath.addEventListener('click', async () => {
  const path = await window.spraute.selectMinecraftFolder();
  if (path) {
    inputMcPath.value = path;
    await window.spraute.storeSet('minecraftPath', path);
    // Требуется перезагрузка директории
    document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';
    loadDirectory('');
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

// Логика обновления Spraute Studio
const studioUpdateModal = document.getElementById('studio-update-modal');
const studioUpdateBox = document.getElementById('studio-update-box');
const btnStudioUpdateSkip = document.getElementById('btn-studio-update-skip');
const btnStudioUpdateDownload = document.getElementById('btn-studio-update-download');
const btnStudioUpdateInstall = document.getElementById('btn-studio-update-install');
const studioUpdateProgressContainer = document.getElementById('studio-update-progress-container');
const studioUpdatePercent = document.getElementById('studio-update-percent');
const studioUpdateBar = document.getElementById('studio-update-bar');
const studioUpdateActions = document.getElementById('studio-update-actions');
const studioUpdateInstallActions = document.getElementById('studio-update-install-actions');

let pendingStudioUpdate = null;

if (window.spraute) {
  window.spraute.onStudioUpdateAvailable((info) => {
    document.getElementById('studio-update-version').innerText = `Версия: ${info.version}`;
    document.getElementById('studio-update-notes').innerHTML = info.releaseNotes || 'Улучшения стабильности и новые функции.';
    
    if (info.isStartupCheck) {
      // Крупное окно при старте
      studioUpdateModal.classList.remove('hidden');
      setTimeout(() => {
        studioUpdateBox.classList.remove('scale-95', 'opacity-0');
      }, 10);
    } else {
      // Небольшое окно (toast) во время работы
      pendingStudioUpdate = () => {
        studioUpdateModal.classList.remove('hidden');
        setTimeout(() => {
          studioUpdateBox.classList.remove('scale-95', 'opacity-0');
        }, 10);
      };
      
      document.getElementById('update-toast-title').innerText = 'Обновление Студии';
      document.getElementById('update-toast-desc').innerText = `Доступна версия ${info.version}`;
      const toast = document.getElementById('update-toast');
      toast.classList.remove('translate-y-20', 'opacity-0', 'pointer-events-none');
    }
  });
}

// Обработка кликов по toast
const btnUpdateToastIgnore = document.getElementById('btn-update-toast-ignore');
if (btnUpdateToastIgnore) {
  btnUpdateToastIgnore.addEventListener('click', () => {
    const toast = document.getElementById('update-toast');
    if (toast) toast.classList.add('translate-y-20', 'opacity-0', 'pointer-events-none');
  });
}

const btnUpdateToastShowMain = document.getElementById('btn-update-toast-show-main');
if (btnUpdateToastShowMain) {
  btnUpdateToastShowMain.addEventListener('click', () => {
    const toast = document.getElementById('update-toast');
    if (toast) toast.classList.add('translate-y-20', 'opacity-0', 'pointer-events-none');
    if (pendingStudioUpdate) pendingStudioUpdate();
    if (typeof pendingModUpdate === 'function') pendingModUpdate();
  });
}

// Логика обновления мода
let pendingModUpdate = null;
let currentModVersionToDownload = null;

if (window.spraute) {
  window.spraute.onModUpdateAvailable((info) => {
    const modUpdateVersion = document.getElementById('mod-update-version');
    if (modUpdateVersion) modUpdateVersion.innerText = `Версия: ${info.version}`;
    
    const modUpdateNotes = document.getElementById('mod-update-notes');
    if (modUpdateNotes) modUpdateNotes.innerHTML = info.notes || 'Описание недоступно.';
    
    currentModVersionToDownload = info.version;
    
    const modUpdateModal = document.getElementById('mod-update-modal');
    const modUpdateBox = document.getElementById('mod-update-box');
    if (modUpdateModal && modUpdateBox) {
      modUpdateModal.classList.remove('hidden');
      setTimeout(() => {
        modUpdateBox.classList.remove('scale-95', 'opacity-0');
      }, 10);
    }
  });
}

const modUpdateModal = document.getElementById('mod-update-modal');
const modUpdateBox = document.getElementById('mod-update-box');
const btnModUpdateSkip = document.getElementById('btn-mod-update-skip');
const btnModUpdateDownload = document.getElementById('btn-mod-update-download');

if (btnModUpdateSkip) {
  btnModUpdateSkip.addEventListener('click', () => {
    modUpdateBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
      modUpdateModal.classList.add('hidden');
    }, 300);
  });
}

if (btnModUpdateDownload) {
  btnModUpdateDownload.addEventListener('click', async () => {
    btnModUpdateDownload.innerText = 'Загрузка...';
    btnModUpdateDownload.classList.add('opacity-50', 'pointer-events-none');
    btnModUpdateSkip.classList.add('hidden');
    
    try {
      const res = await window.spraute.downloadModUpdate(currentModVersionToDownload);
      if (res.success) {
        btnModUpdateDownload.innerText = `Мод обновлен до версии ${currentModVersionToDownload}!`;
        btnModUpdateDownload.classList.remove('bg-secondary');
        btnModUpdateDownload.classList.add('bg-green-600');
        
        setTimeout(() => {
          modUpdateBox.classList.add('scale-95', 'opacity-0');
          setTimeout(() => {
            modUpdateModal.classList.add('hidden');
            // Восстанавливаем кнопки
            btnModUpdateDownload.innerText = 'Обновить мод';
            btnModUpdateDownload.classList.add('bg-secondary');
            btnModUpdateDownload.classList.remove('bg-green-600', 'opacity-50', 'pointer-events-none');
            btnModUpdateSkip.classList.remove('hidden');
          }, 300);
        }, 2000);
      } else {
        appAlert('Ошибка при скачивании мода: ' + res.error);
        btnModUpdateDownload.innerText = 'Ошибка';
        setTimeout(() => {
          btnModUpdateDownload.innerText = 'Повторить';
          btnModUpdateDownload.classList.remove('opacity-50', 'pointer-events-none');
          btnModUpdateSkip.classList.remove('hidden');
        }, 2000);
      }
    } catch (err) {
      appAlert(err.message);
    }
  });
}

if (btnStudioUpdateSkip) {
  btnStudioUpdateSkip.addEventListener('click', () => {
    studioUpdateBox.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
      studioUpdateModal.classList.add('hidden');
    }, 300);
  });
}

if (btnStudioUpdateDownload) {
  btnStudioUpdateDownload.addEventListener('click', () => {
    window.spraute.downloadStudioUpdate();
    btnStudioUpdateSkip.style.display = 'none';
    btnStudioUpdateDownload.style.display = 'none';
    studioUpdateProgressContainer.classList.remove('hidden');
    studioUpdateProgressContainer.classList.add('flex');
  });
}

if (window.spraute) {
  window.spraute.onStudioUpdateProgress((progressObj) => {
    const percent = Math.round(progressObj.percent);
    studioUpdatePercent.innerText = `${percent}%`;
    studioUpdateBar.style.width = `${percent}%`;
  });

  window.spraute.onStudioUpdateDownloaded(() => {
    studioUpdateProgressContainer.classList.add('hidden');
    studioUpdateProgressContainer.classList.remove('flex');
    studioUpdateActions.classList.add('hidden');
    studioUpdateInstallActions.classList.remove('hidden');
  });
}

if (btnStudioUpdateInstall) {
  btnStudioUpdateInstall.addEventListener('click', () => {
    window.spraute.installStudioUpdate();
  });
}

async function toggleVisualMode() {
  const btn = document.getElementById('btn-toggle-visual');
  if (!btn) return;
  const editorMount = document.getElementById('editor-mount');
  const blocklyMount = document.getElementById('blockly-mount');
  btn.disabled = true;

  if (!isVisualMode) {
    // === КОД → ВИЗУАЛ ===
    showVisualTransition(true, 'Подготовка визуального режима...');

    isVisualMode = true;
    btn.innerHTML = '<span class="material-symbols-outlined text-[16px]">code</span> Обычный код';
    btn.classList.add('bg-primary/20', 'text-primary');

    // Даём браузеру отрисовать оверлей
    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));

    await VisualEngine.ensureWorkspace();
    VisualEngine.applyCache();

    editorMount.classList.add('hidden');
    editorMount.style.display = 'none';
    blocklyMount.classList.remove('hidden');
    blocklyMount.style.display = 'block';
    blocklyMount.style.width = '';
    blocklyMount.style.height = '';
    document.body.classList.add('visual-mode-active');

    document.querySelectorAll('.blocklyToolboxDiv').forEach(el => {
      if (el) { el.style.display = ''; el.style.width = ''; el.style.height = ''; el.style.overflow = ''; }
    });

    if (currentEditor && blocklyWorkspace) {
      const text = currentEditor.state.doc.toString();
      _suppressDirty = true;
      try {
        if (activeTabPath && VisualEngine.isCacheValidForText(activeTabPath, text)) {
          VisualEngine.restoreFileBlocks(activeTabPath);
        } else {
          try { textToBlocks(text, blocklyWorkspace); } catch (e) { console.error("Parse error", e); }
          if (activeTabPath) VisualEngine.saveFileBlocks(activeTabPath, text);
        }
      } finally { _suppressDirty = false; }

      if (text !== VisualEngine._lastScanText) {
        VisualEngine.scanInBackground(text).then(() => { VisualEngine.applyCache(); });
      }
    }

    if (blocklyWorkspace) Blockly.svgResize(blocklyWorkspace);
    if (window.spraute) window.spraute.storeSet('visualModeEnabled', true);

    showVisualTransition(false);

  } else {
    // === ВИЗУАЛ → КОД ===
    showVisualTransition(true, 'Генерация кода...');

    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));

    isVisualMode = false;
    btn.innerHTML = '<span class="material-symbols-outlined text-[16px]">extension</span> Визуальный код';
    btn.classList.remove('bg-primary/20', 'text-primary');

    if (blocklyWorkspace && currentEditor) {
      const code = SprauteGenerator.workspaceToCode(blocklyWorkspace);
      if (activeTabPath) VisualEngine.saveFileBlocks(activeTabPath, code);
      currentEditor.dispatch({
        changes: { from: 0, to: currentEditor.state.doc.length, insert: code }
      });
    }

    blocklyMount.classList.add('hidden');
    blocklyMount.style.display = 'none';
    blocklyMount.style.width = '0';
    blocklyMount.style.height = '0';
    editorMount.classList.remove('hidden');
    editorMount.style.display = 'block';
    document.body.classList.remove('visual-mode-active');

    Blockly.hideChaff();
    document.querySelectorAll('.blocklyWidgetDiv, .blocklyTooltipDiv, .blocklyDropDownDiv, .blocklyToolboxDiv').forEach(el => {
      if (el) { el.style.display = 'none'; el.style.left = '0'; el.style.top = '0'; el.style.width = '0'; el.style.height = '0'; el.style.overflow = 'hidden'; }
    });

    if (window.spraute) window.spraute.storeSet('visualModeEnabled', false);

    showVisualTransition(false);
  }

  btn.disabled = false;
}

const btnToggleVisual = document.getElementById('btn-toggle-visual');
if (btnToggleVisual) {
  btnToggleVisual.addEventListener('click', toggleVisualMode);
}

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
      await loadDirectory('');
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
let marketPluginsData = [];
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
      let desc = "Пользовательский плагин / библиотека блоков";
      let author = "";
      let isEnabled = true;
      let hasIcon = false;
      try {
        const jsonContent = await window.spraute.readFile(`${p.rel}/plugin.json`, 'utf8');
        const data = JSON.parse(jsonContent);
        if (data.description) desc = data.description;
        if (data.author) author = data.author;
        if (data.enabled === false) isEnabled = false;
      } catch(e) {}
      
      try {
        hasIcon = await window.spraute.exists(`${p.rel}/icon.png`);
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

async function loadMarketPlugins() {
  pluginsList.innerHTML = '<div class="text-center text-on-variant py-8 text-xs">Загрузка из сети...</div>';
  pluginsPagination.innerHTML = '';
  try {
    marketPluginsData = await window.spraute.marketList();
    renderPluginsList();
  } catch (e) {
    pluginsList.innerHTML = `<div class="text-center text-red-400 py-8 text-xs">Ошибка загрузки магазина: ${e.message}</div>`;
  }
}

async function renderPluginsList() {
  let sourceData = currentPluginsTab === 'market' ? marketPluginsData : allPluginsData;
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
    
    if (currentPluginsTab === 'market') {
      const isInstalled = allPluginsData.find(localP => localP.name === p.name);
      let btnHtml = '';
      if (isInstalled) {
         if (p.version && isInstalled.version && p.version !== isInstalled.version) {
           btnHtml = `<button class="px-3 py-1.5 bg-green-500/20 hover:bg-green-500/40 rounded-lg text-xs text-green-400 font-medium btn-plugin-market-download" data-plugin="${p.name}" data-file="${p.fileName}"><span class="material-symbols-outlined text-[14px] align-middle mr-1">update</span>Обновить</button>`;
         } else {
           btnHtml = `<span class="text-xs text-on-variant px-3 py-1.5"><span class="material-symbols-outlined text-[14px] align-middle mr-1">check</span>Установлен</span>`;
         }
      } else {
         btnHtml = `<button class="px-3 py-1.5 bg-primary/20 hover:bg-primary/40 rounded-lg text-xs text-primary font-medium btn-plugin-market-download" data-plugin="${p.name}" data-file="${p.fileName}"><span class="material-symbols-outlined text-[14px] align-middle mr-1">download</span>Скачать</button>`;
      }
      
      html += `
      <div class="flex items-center justify-between p-3 rounded-xl bg-black/20 border border-white/5 hover:border-white/10 transition-colors group">
        <div class="flex items-center gap-3 flex-1 overflow-hidden">
          <div class="w-10 h-10 rounded-lg bg-primary/20 text-primary flex items-center justify-center shrink-0 overflow-hidden shadow-inner">
            ${iconHtml}
          </div>
          <div class="flex-1 overflow-hidden pr-4">
            <div class="font-medium text-white flex items-center gap-2 truncate">
              ${p.name} 
              ${p.version ? `<span class="px-1.5 py-0.5 rounded bg-white/10 text-[10px] text-white shrink-0">v${p.version}</span>` : ''}
              ${p.mcVersion ? `<span class="px-1.5 py-0.5 rounded bg-emerald-500/20 text-[10px] text-emerald-400 shrink-0 border border-emerald-500/20">MC ${p.mcVersion}</span>` : ''}
              ${p.author ? `<span class="px-1.5 py-0.5 rounded bg-white/10 text-[10px] text-white shrink-0">by ${p.author}</span>` : ''}
            </div>
            <div class="text-xs text-on-variant mt-0.5 truncate">${p.desc || ''}</div>
          </div>
        </div>
        <div class="flex gap-2 shrink-0">
          ${btnHtml}
        </div>
      </div>
      `;
    } else {
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
  
  if (target.closest('#tab-my-plugins')) {
    currentPluginsTab = 'my';
    document.getElementById('tab-my-plugins').classList.replace('border-transparent', 'border-primary');
    document.getElementById('tab-my-plugins').classList.replace('text-on-variant', 'text-white');
    document.getElementById('tab-market-plugins').classList.replace('border-primary', 'border-transparent');
    document.getElementById('tab-market-plugins').classList.replace('text-white', 'text-on-variant');
    document.getElementById('plugins-header-actions').classList.remove('hidden');
    document.getElementById('plugins-header-buttons').classList.remove('hidden');
    document.getElementById('plugins-header-text').textContent = 'Здесь вы можете управлять пользовательскими библиотеками блоков и расширениями.';
    currentPluginsPage = 1;
    renderPluginsList();
  }

  if (target.closest('#tab-market-plugins')) {
    currentPluginsTab = 'market';
    document.getElementById('tab-market-plugins').classList.replace('border-transparent', 'border-primary');
    document.getElementById('tab-market-plugins').classList.replace('text-on-variant', 'text-white');
    document.getElementById('tab-my-plugins').classList.replace('border-primary', 'border-transparent');
    document.getElementById('tab-my-plugins').classList.replace('text-white', 'text-on-variant');
    document.getElementById('plugins-header-actions').classList.remove('hidden');
    document.getElementById('plugins-header-text').innerHTML = `Лучшие плагины сообщества. Загружаются напрямую с сервера обновлений.<br/><a href="#" onclick="window.spraute.openExternal('https://t.me/spraute_community/83357'); return false;" class="text-primary hover:underline mt-1 inline-block text-xs">Как добавить сюда свой плагин?</a>`;
    document.getElementById('plugins-header-buttons').classList.add('hidden');
    currentPluginsPage = 1;
    loadMarketPlugins();
  }
  
  if (target.closest('.btn-plugin-market-download')) {
     const btn = target.closest('.btn-plugin-market-download');
     const pName = btn.dataset.plugin;
     const fName = btn.dataset.file;
     btn.innerHTML = '<span class="material-symbols-outlined text-[14px] animate-spin">sync</span>Скачивание...';
     btn.disabled = true;
     try {
        const res = await window.spraute.marketDownload(pName, fName);
        if (res.success) {
           setStatus(`Плагин ${pName} успешно загружен!`);
           await loadPluginsList(); // Refresh local plugins so we know it's installed
           renderPluginsList();
        } else {
           appAlert(`Ошибка скачивания: ${res.error}`);
           btn.innerHTML = '<span class="material-symbols-outlined text-[14px]">download</span>Скачать';
           btn.disabled = false;
        }
     } catch(e) {
        appAlert(`Ошибка: ${e.message}`);
        btn.innerHTML = '<span class="material-symbols-outlined text-[14px]">download</span>Скачать';
        btn.disabled = false;
     }
  }

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
      const hasIcon = await window.spraute.exists(iconPath);
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
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.zip,.spr';
    
    input.onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      
      try {
        const existsPlugins = await window.spraute.exists('plugins');
        if (!existsPlugins) await window.spraute.mkdir('plugins');

        // Если это отдельный .spr файл
        if (file.name.endsWith('.spr')) {
          const baseName = file.name.replace('.spr', '');
          const pPath = `plugins/${baseName}`;
          
          if (await window.spraute.exists(pPath)) {
             appAlert('Плагин с таким именем уже существует!');
             return;
          }
          
          await window.spraute.mkdir(pPath);
          await window.spraute.mkdir(`${pPath}/blocks`);
          
          const reader = new FileReader();
          reader.onload = async (e) => {
             const content = e.target.result;
             await window.spraute.writeFile(`${pPath}/blocks/${file.name}`, content);
             
             const pluginJson = {
               name: baseName,
               author: "Unknown",
               version: "1.0.0",
               mc_version: "any",
               description: "Импортированная библиотека блоков"
             };
             await window.spraute.writeFile(`${pPath}/plugin.json`, JSON.stringify(pluginJson, null, 2));
             
             setStatus(`Плагин ${baseName} успешно установлен`);
             loadPluginsList();
          };
          reader.readAsText(file);
        } else {
          appAlert('Функция распаковки .zip плагинов находится в разработке.');
        }
      } catch(err) {
        appAlert('Ошибка при установке плагина: ' + err.message);
      }
    };
    
    input.click();
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
const inputBuilderCode = document.getElementById('builder-code');
const inputBuilderParse = document.getElementById('builder-parse');
const btnBuilderPreview = document.getElementById('btn-builder-preview');
let previewWorkspace = null;

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
           if (l.match(/^#\\?\s*block:/) || l.match(/^#\\?\s*category:/) || l.match(/^#\\?\s*color:/) || l.match(/^#\\?\s*shape:/)) continue;
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

// --- Импорт Визуальных Блоков и Сбор Данных ---
const btnImportScript = document.getElementById('btn-import-script');
let importedScriptsText = ""; 

if (btnImportScript) {
  btnImportScript.addEventListener('click', async () => {
    const name = await appPrompt('Путь к скрипту для импорта (например, scripts/my_lib.spr):');
    if (!name || !name.trim()) return;
    
    try {
      const content = await window.spraute.readFile(name.trim(), 'utf8');
      importedScriptsText += "\n" + content;
      
      // Фоновый ресканинг
      VisualEngine.scanInBackground(currentEditor ? currentEditor.state.doc.toString() : '').then(() => {
        VisualEngine.applyCache();
      });
      setStatus(`Скрипт ${name} импортирован для визуальных блоков`);
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
