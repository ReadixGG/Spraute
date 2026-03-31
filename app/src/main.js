import { EditorView, basicSetup } from "codemirror";
import { EditorState } from "@codemirror/state";
import { javascript } from "@codemirror/lang-javascript";
import { syntaxHighlighting, defaultHighlightStyle } from "@codemirror/language";
import { HighlightStyle } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";
import { search, searchKeymap } from "@codemirror/search";
import { keymap } from "@codemirror/view";
import { history, historyKeymap } from "@codemirror/commands";

import { StreamLanguage, LanguageSupport } from "@codemirror/language";
import { linter, lintGutter } from "@codemirror/lint";
import { autocompletion, completeAnyWord, snippetCompletion, completionKeymap, acceptCompletion } from "@codemirror/autocomplete";

const sprauteLanguage = StreamLanguage.define({
  token(stream, state) {
    if (stream.eatSpace()) return null;
    if (stream.match(/^#.*/)) return "comment";
    if (stream.match(/^"[^"]*"/)) return "string";
    if (stream.match(/^"[^"]*$/)) return "error";
    if (stream.match(/^-?\d+(?:\.\d+)?/)) return "number";
    if (stream.match(/^(val|fun|on|create|npc|ui|if|else|elif|while|for|in|return|break|continue|true|false|null|await|time|say|playFreeze|playOnce|playLoop|stop|alwaysLookAt|lookAt)\b/)) return "keyword";
    if (stream.match(/^(text|button|pos|anchor|size|color|scale|model|texture|animation|name|hp|show_name|background|slot|image|rect|progress)\b/)) return "propertyName";
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
  "name", "hp", "speed", "pos", "rotate", "show_name", "collision", "model", "texture", "idle_anim", "walk_anim", "head",
  
  // UI параметры
  "size", "background", "bg", "can_close",
  "anchor", "anchor_x", "anchor_y", "scale", "crop", "feet_crop",
  "color", "hover", "bg_color", "outline_color",
  "wrap", "align", "tooltip", "layer", "order", "id",
  "max_lines", "max_chars", "input_type", "placeholder",
  "content_h", "scrollbar",
  "grid_type", "cell_size", "thickness",
  
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
  "damage(${1:amount})",
  "teleport(${1:x}, ${2:y}, ${3:z})"
];

const sprauteFunctionCompletions = sprauteFunctionsList.map(sig => {
  const name = sig.split('(')[0];
  return snippetCompletion(sig, {label: name, detail: "method", type: "function"});
});

const sprauteSnippets = [
  // Базовые конструкции
  snippetCompletion("if (${1:condition}) {\n  ${2}\n}", {label: "if", detail: "block", type: "keyword"}),
  snippetCompletion("else {\n  ${1}\n}", {label: "else", detail: "block", type: "keyword"}),
  snippetCompletion("elif (${1:condition}) {\n  ${2}\n}", {label: "elif", detail: "block", type: "keyword"}),
  snippetCompletion("while (${1:condition}) {\n  ${2}\n}", {label: "while", detail: "block", type: "keyword"}),
  snippetCompletion("for (${1:item} in ${2:list}) {\n  ${3}\n}", {label: "for", detail: "block", type: "keyword"}),
  snippetCompletion("fun ${1:name}(${2:args}) {\n  ${3}\n}", {label: "fun", detail: "definition", type: "keyword"}),
  snippetCompletion("on ${1:event}(${2:args}) {\n  ${3}\n}", {label: "on", detail: "event handler", type: "keyword"}),
  snippetCompletion("create ui ${1:name} {\n  ${2}\n}", {label: "create ui", detail: "definition", type: "keyword"}),
  snippetCompletion('create npc ${1:name} {\n  name = "${2:Display Name}"\n  hp = ${3:20}\n  model = "geo/defolt.geo.json"\n  texture = "textures/entity/defolt.png"\n  animation = "animations/npc_classic.animation.json"\n  pos = [${4:0, 64, 0}]\n  ${5}\n}', {label: "create npc", detail: "definition", type: "keyword"}),
  
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
      if (char === '"' && doc[i-1] !== '\\') inString = false;
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

  // Функция синхронизации и запуска
  async function runUpdaterAndStart(path, background = false) {
    if (!background) {
      document.getElementById('initial-title').classList.remove('hidden');
      showView('view-updater');
    } else {
      // Сразу показываем студию
      document.getElementById('initial-title').classList.add('hidden');
      document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';
      showView('view-studio');
      loadDirectory('');
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
      }
    });

    await window.spraute.initWorkspace(path);
    await window.spraute.storeSet('minecraftPath', path);

    if (!background) {
      // Даем пользователю полторы секунды прочитать, что всё ок
      setTimeout(() => {
        const updaterView = document.getElementById('view-updater');
        updaterView.classList.remove('opacity-100', 'translate-y-0');
        updaterView.classList.add('opacity-0', '-translate-y-4');
        
        setTimeout(() => {
          document.getElementById('initial-title').classList.add('hidden');
          document.getElementById('studio-path-display').innerText = path + '\\spraute_engine';
          showView('view-studio');
          loadDirectory('');
        }, 500);
      }, 1500);
    } else {
      setStatus("Обновление завершено");
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
          alert(`Ошибка перемещения: ${err.message}`);
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

// Вкладки
let openTabs = []; // { path: string, name: string, isImage: boolean, isDirty: boolean, state: EditorState|null }
let activeTabPath = null;

function renderTabs() {
  const container = document.getElementById('tabs-container');
  if (openTabs.length === 0) {
    container.classList.add('hidden');
    document.getElementById('empty-state').classList.remove('hidden');
    document.getElementById('editor-mount').innerHTML = '';
    if (currentEditor) {
      currentEditor.destroy();
      currentEditor = null;
    }
    currentOpenFile = null;
    activeTabPath = null;
    return;
  }
  
  container.classList.remove('hidden');
  document.getElementById('empty-state').classList.add('hidden');
  
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
    let contentToSave;
    if (path === activeTabPath && currentEditor) {
      contentToSave = currentEditor.state.doc.toString();
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
    alert(`Ошибка при сохранении ${tab.name}: ${e.message}`);
  }
}

async function saveActiveTab() {
  if (activeTabPath) {
    await saveTab(activeTabPath);
  }
}

// Слушатель Ctrl+S
document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    saveActiveTab();
  }
});

async function switchToTab(path) {
  if (currentEditor && activeTabPath) {
    const activeTab = openTabs.find(t => t.path === activeTabPath);
    if (activeTab && !activeTab.isImage) {
      activeTab.state = currentEditor.state;
    }
  }

  activeTabPath = path;
  renderTabs();
  
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
      
    } catch (e) {
      editorMount.innerHTML = `<div class="p-4 text-red-400">Ошибка чтения файла: ${e.message}</div>`;
    }
  }
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
    alert('Файл с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.writeFile(newPath, '# Новый скрипт\n');
    loadDirectory(''); // Перезагружаем корень
  } catch(e) {
    alert('Ошибка: ' + e.message);
  }
});

document.getElementById('ctx-new-folder').addEventListener('click', async () => {
  const name = await appPrompt('Имя новой папки:');
  if (!name) return;
  const dirPath = currentCtxIsDir ? currentCtxRelPath : '';
  const newPath = dirPath ? `${dirPath}/${name}` : name;
  
  const exists = await window.spraute.exists(newPath);
  if (exists) {
    alert('Папка с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.mkdir(newPath);
    loadDirectory('');
  } catch(e) {
    alert('Ошибка: ' + e.message);
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
    alert('Ошибка: ' + e.message);
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
    alert('Ошибка: ' + e.message);
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
  
  if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
    if (selectedItemPath) {
      clipboardPath = selectedItemPath;
      clipboardIsDir = selectedItemIsDir;
      setStatus(`Скопировано: ${clipboardPath}`);
    }
  }
  
  if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
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
      alert('Ошибка при вставке: ' + e.message);
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
    alert('Файл с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.writeFile(newPath, '# Новый скрипт\n');
    setStatus(`Файл ${finalName} создан`);
    loadDirectory('');
  } catch(e) {
    alert('Ошибка: ' + e.message);
  }
});

document.getElementById('btn-root-new-folder').addEventListener('click', async () => {
  const name = await appPrompt('Имя новой папки:');
  if (!name) return;
  
  const dirPath = selectedItemIsDir ? selectedItemPath : (selectedItemPath.includes('/') ? selectedItemPath.substring(0, selectedItemPath.lastIndexOf('/')) : '');
  const newPath = dirPath ? `${dirPath}/${name}` : name;

  const exists = await window.spraute.exists(newPath);
  if (exists) {
    alert('Папка с таким именем уже существует!');
    return;
  }

  try {
    await window.spraute.mkdir(newPath);
    setStatus(`Папка ${name} создана`);
    loadDirectory('');
  } catch(e) {
    alert('Ошибка: ' + e.message);
  }
});

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

window.spraute.onStudioUpdateAvailable((info) => {
  document.getElementById('studio-update-version').innerText = `Версия: ${info.version}`;
  document.getElementById('studio-update-notes').innerHTML = info.releaseNotes || 'Улучшения стабильности и новые функции.';
  
  studioUpdateModal.classList.remove('hidden');
  setTimeout(() => {
    studioUpdateBox.classList.remove('scale-95', 'opacity-0');
  }, 10);
});

btnStudioUpdateSkip.addEventListener('click', () => {
  studioUpdateBox.classList.add('scale-95', 'opacity-0');
  setTimeout(() => {
    studioUpdateModal.classList.add('hidden');
  }, 300);
});

btnStudioUpdateDownload.addEventListener('click', () => {
  window.spraute.downloadStudioUpdate();
  btnStudioUpdateSkip.style.display = 'none';
  btnStudioUpdateDownload.style.display = 'none';
  studioUpdateProgressContainer.classList.remove('hidden');
  studioUpdateProgressContainer.classList.add('flex');
});

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

btnStudioUpdateInstall.addEventListener('click', () => {
  window.spraute.installStudioUpdate();
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
      await loadDirectory('');
      setStatus(`Перемещено в корень: ${srcName}`);
    } catch (err) {
      alert(`Ошибка перемещения: ${err.message}`);
    }
  }
});

// Запуск при загрузке DOM
document.addEventListener('DOMContentLoaded', init);
