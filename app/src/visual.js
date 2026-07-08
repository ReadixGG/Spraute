// Импортируем полный пакет blockly (включает contextmenu_items, locale en, blocks).
// blockly/core не регистрирует дефолтные пункты контекстного меню — используем полный пакет.
import * as Blockly from 'blockly';
import { FieldMultilineInput } from '@blockly/field-multilineinput';
// blockly уже включает blocks и en locale, дополнительно форсируем
import * as En from 'blockly/msg/en';
import { registerGuiBlocks, GUI_COLOR } from './gui-blocks.js';
Blockly.setLocale(En);

// ================= ГЕНЕРАТОР КОДА =================
export const SprauteGenerator = new Blockly.Generator('Spraute');

/** Blockly valueToCode бросает ошибку на пустом слоте — для копирования/вставки и GUI-блоков нужен мягкий вариант. */
const _nativeValueToCode = SprauteGenerator.valueToCode.bind(SprauteGenerator);
SprauteGenerator.valueToCode = function(block, name, order, fallback) {
  if (!block?.getInput(name)) return fallback ?? '';
  if (!block.getInputTargetBlock(name)) return fallback ?? '';
  try {
    const code = _nativeValueToCode(block, name, order);
    return code == null || code === '' ? (fallback ?? '') : code;
  } catch (e) {
    return fallback ?? '';
  }
};

const _nativeStatementToCode = SprauteGenerator.statementToCode.bind(SprauteGenerator);
SprauteGenerator.statementToCode = function(block, name, fallback) {
  if (!block?.getInput(name)) return fallback ?? '';
  try {
    const code = _nativeStatementToCode(block, name);
    return code == null ? (fallback ?? '') : code;
  } catch (e) {
    return fallback ?? '';
  }
};

/** Перед paste/duplicate: Blockly вызывает loadExtraState до loadFields — не затирать val_* дефолтами. */
export function beginBlocklyRestore(workspace) {
  if (workspace) workspace._sprauteRestoringBlocks = true;
}

export function endBlocklyRestore(workspace) {
  if (!workspace) return;
  workspace._sprauteRestoringBlocks = false;
  for (const block of workspace.getAllBlocks(false)) {
    if (typeof block.syncValFromFields_ === 'function') block.syncValFromFields_();
    if (typeof block.updateShape_ === 'function') block.updateShape_();
  }
  onSprauteAnimContextChanged(workspace);
}

SprauteGenerator.scrub_ = function(block, code, opt_thisOnly) {
  const nextBlock = block.nextConnection && block.nextConnection.targetBlock();
  const nextCode = opt_thisOnly ? '' : SprauteGenerator.blockToCode(nextBlock);
  let out = code == null ? '' : String(code);
  if (out && nextCode && !out.endsWith('\n')) out += '\n';
  return out + (nextCode || '');
};

/** Гарантирует перевод строки в конце statement-вывода блока. */
function ensureStatementTrailingNewline(code) {
  if (code == null) return '';
  const text = String(code);
  if (!text) return '';
  return text.endsWith('\n') ? text : text + '\n';
}

/** Blockly FieldCheckbox uses TRUE/FALSE; Spraute scripts require lowercase true/false. */
function normalizeBlocklyLiteral(value) {
  if (value === 'TRUE') return 'true';
  if (value === 'FALSE') return 'false';
  return value;
}

const COLORS = { SYSTEM: '#a855f7' };

registerGuiBlocks(SprauteGenerator);

Blockly.Blocks['spraute_raw_code'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Выполнить код:");
    this.appendDummyInput()
        .appendField(new FieldMultilineInput("say(player, \"Привет\")"), "CODE");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.SYSTEM);
  }
};
SprauteGenerator.forBlock['spraute_raw_code'] = function(block) {
  return `${block.getFieldValue('CODE')}\n`;
};

// Блок для произвольного value-выражения (когда не нашли подходящий value-блок)
Blockly.Blocks['spraute_raw_value'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("выражение")
        .appendField(new Blockly.FieldTextInput("значение"), "EXPR");
    this.setOutput(true, null);
    this.setColour(COLORS.SYSTEM);
  }
};
SprauteGenerator.forBlock['spraute_raw_value'] = function(block) {
  return [block.getFieldValue('EXPR') || "", 0];
};

// ================= ДИНАМИЧЕСКИЕ ДАННЫЕ =================
export let currentNpcs = [];
export let currentNpcPrefabs = [];
export let currentAnimations = [];
export let currentAnimFiles = [];
export let currentModels = [];
export let currentTextures = [];
/** Путь файла анимации → имена клипов внутри него. */
export let animsByFile = {};

/** Файл модели в geo/ — .geo.json, .geo или .json (не animation). */
export function isGeoModelFileName(fileName) {
  if (!fileName || typeof fileName !== 'string') return false;
  if (/\.geo\.json$/i.test(fileName)) return true;
  if (/\.geo$/i.test(fileName)) return true;
  if (/\.json$/i.test(fileName) && !/\.animation\.json$/i.test(fileName)) return true;
  return false;
}

/** Подписи dropdown: путь относительно geo/, без дубликатов по value. */
export function buildModelDropdownOptions(models) {
  const paths = [];
  const seen = new Set();
  for (const raw of models || []) {
    const p = String(raw).replace(/\\/g, '/');
    const key = p.toLowerCase();
    if (!p || seen.has(key)) continue;
    seen.add(key);
    paths.push(p);
  }
  paths.sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));

  const labelUse = {};
  return paths.map(p => {
    let label = (p.startsWith('geo/') ? p.slice(4) : p)
      .replace(/\.geo\.json$/i, '')
      .replace(/\.geo$/i, '')
      .replace(/\.json$/i, '');
    const key = label.toLowerCase();
    labelUse[key] = (labelUse[key] || 0) + 1;
    if (labelUse[key] > 1) {
      label = `${label} (${labelUse[key]})`;
    }
    return [label, p];
  });
}

export function updateDynamicLists(npcs, anims, models, textures, animFiles, byFile, npcPrefabs) {
  if (npcs != null) currentNpcs = npcs.length > 0 ? npcs.map(n => [n, n]) : [];
  if (npcPrefabs != null) currentNpcPrefabs = npcPrefabs.length > 0 ? npcPrefabs.map(n => [n, n]) : [];
  if (anims   && anims.length > 0)   currentAnimations = anims.map(a => [a, a]);
  if (animFiles && animFiles.length > 0) {
    currentAnimFiles = animFiles.map(p => {
      const base = p.split('/').pop() || p;
      const label = base.replace(/\.animation\.json$/i, '').replace(/\.json$/i, '');
      return [label, p];
    });
  }
  if (byFile != null) animsByFile = byFile;
  if (models != null) {
    currentModels = models.length > 0
      ? buildModelDropdownOptions(models)
      : [];
  }
  if (textures && textures.length > 0) currentTextures = textures.map(t => [t.split('/').pop().replace(/\.(png|jpg|jpeg)$/,''), t]);
}

function normAnimFilePath(p) {
  if (!p) return '';
  return String(p).replace(/\\/g, '/');
}

function lookupAnimsInFile(animFile) {
  const path = normAnimFilePath(animFile);
  if (!path) return null;
  let names = animsByFile[path];
  if (names?.length) return names;
  const base = path.split('/').pop().toLowerCase();
  for (const [k, v] of Object.entries(animsByFile)) {
    if (k.split('/').pop().toLowerCase() === base && v?.length) return v;
  }
  return null;
}

/** Id НИПа → путь к файлу анимации из блоков «создать НИП». */
export function buildNpcAnimFileMap(workspace) {
  const map = {};
  if (!workspace) return map;
  for (const block of workspace.getAllBlocks(false)) {
    if (!block.type?.endsWith('npc_create')) continue;
    const id = readBlockFieldVal(block, 'id');
    const anim = readBlockFieldVal(block, 'animation');
    if (id && anim) map[id] = normAnimFilePath(anim);
  }
  return map;
}

function resolveAnimFileForBlock(self) {
  if (!self?.workspace) return '';
  if (self.type?.endsWith('npc_create')) {
    return normAnimFilePath(readBlockFieldVal(self, 'animation'));
  }
  const npcId = readBlockFieldVal(self, 'npc');
  if (npcId) {
    return buildNpcAnimFileMap(self.workspace)[npcId] || '';
  }
  return '';
}

function getAnimsForAnimFile(animFile) {
  const names = lookupAnimsInFile(animFile);
  if (names?.length) return names.map(a => [a, a]);
  if (normAnimFilePath(animFile)) return [['(нет клипов в файле)', '']];
  return getAnimsDropdown();
}


function readBlockFieldVal(self, fieldName) {
  if (!self) return '';
  try {
    const v = self.getFieldValue(fieldName);
    if (v != null && String(v).trim() !== '') return String(v);
  } catch (e) {}
  const cached = self[`val_${fieldName}`];
  return cached != null && String(cached).trim() !== '' ? String(cached) : '';
}

function ensureDropdownValue(options, current, labelFor) {
  if (current == null || String(current).trim() === '') return options;
  const val = String(current);
  if (options.some(o => o[1] === val)) return options;
  return [[labelFor(val), val], ...options];
}

function getNpcsDropdown() {
  const filtered = currentNpcs
    .filter(n => n[1] && n[1] !== '_eventNpc')
    .map(n => ["НИП: " + n[0], n[1]]);
  return filtered.length > 0 ? filtered : [["(создайте НИП)", ""]];
}

function getNpcsDropdownFor(_self, _fieldName) {
  return getNpcsDropdown();
}

function getNpcPrefabsDropdown() {
  const filtered = currentNpcPrefabs
    .filter(n => n[1])
    .map(n => ["префаб: " + n[0], n[1]]);
  return filtered.length > 0 ? filtered : [["(создайте префаб)", ""]];
}

function getNpcPrefabsDropdownFor(self, fieldName) {
  return ensureDropdownValue(
    getNpcPrefabsDropdown(),
    readBlockFieldVal(self, fieldName),
    v => v
  );
}

function getAnimsDropdown() {
  const filtered = currentAnimations.filter(a => a[1] !== '(нет анимаций)').map(a => [a[0], a[1]]);
  return filtered.length > 0 ? filtered : [["idle", "idle"]];
}

function getAnimsDropdownFor(self, fieldName) {
  const animFile = resolveAnimFileForBlock(self);
  const options = animFile ? getAnimsForAnimFile(animFile) : getAnimsDropdown();
  return ensureDropdownValue(
    options,
    readBlockFieldVal(self, fieldName),
    v => v
  );
}

function clampAnimFieldToFile(block, fieldName) {
  if (!block?.getField(fieldName)) return;
  const animFile = resolveAnimFileForBlock(block);
  if (!animFile) return;
  const options = getAnimsForAnimFile(animFile);
  const valid = new Set(options.map(o => o[1]).filter(Boolean));
  const cur = readBlockFieldVal(block, fieldName);
  if (!cur || valid.has(cur)) return;
  const fallback = options.find(o => o[1])?.[1] || 'idle';
  try {
    block.setFieldValue(fallback, fieldName);
    block[`val_${fieldName}`] = fallback;
  } catch (e) {}
}

function normalizeAnimFieldsOnBlock(block) {
  if (!block || block.isDisposed()) return;
  if (block.type?.endsWith('npc_create')) {
    clampAnimFieldToFile(block, 'idleAnim');
    clampAnimFieldToFile(block, 'walkAnim');
    return;
  }
  if (block.getField('anim')) {
    clampAnimFieldToFile(block, 'anim');
    return;
  }
  const prop = readBlockFieldVal(block, 'prop');
  if (block.getField('value') && (prop === 'idleAnim' || prop === 'walkAnim')) {
    clampAnimFieldToFile(block, 'value');
  }
}

/** После смены файла анимации или НИПа — подогнать клипы и обновить dropdown. */
export function onSprauteAnimContextChanged(workspace, _sourceBlock) {
  if (!workspace || workspace._sprauteRestoringBlocks) return;
  for (const block of workspace.getAllBlocks(false)) {
    normalizeAnimFieldsOnBlock(block);
  }
  refreshDynamicDropdownFields(workspace);
}

function getAnimFilesDropdown() {
  return currentAnimFiles.length > 0
    ? currentAnimFiles
    : [["npc_classic", "animations/npc_classic.animation.json"]];
}

function getAnimFilesDropdownFor(self, fieldName) {
  return ensureDropdownValue(
    getAnimFilesDropdown(),
    readBlockFieldVal(self, fieldName),
    v => (v.split('/').pop() || v).replace(/\.animation\.json$/i, '').replace(/\.json$/i, '')
  );
}

function getModelsDropdown() {
  return currentModels.length > 0 ? currentModels : [["defolt", "geo/defolt.geo.json"]];
}

function getModelsDropdownFor(self, fieldName) {
  return ensureDropdownValue(
    getModelsDropdown(),
    readBlockFieldVal(self, fieldName),
    v => (v.split('/').pop() || v).replace(/\.geo\.json$/i, '')
  );
}

function getTexturesDropdown() {
  return currentTextures.length > 0 ? currentTextures : [["defolt", "textures/entity/defolt.png"]];
}

function getTexturesDropdownFor(self, fieldName) {
  return ensureDropdownValue(
    getTexturesDropdown(),
    readBlockFieldVal(self, fieldName),
    v => (v.split('/').pop() || v).replace(/\.(png|jpg|jpeg)$/i, '')
  );
}

/** Id из `create npc id` в тексте .spr */
export function extractCreateNpcIdsFromSpr(text) {
  const ids = [];
  if (!text) return ids;
  const seen = new Set();
  for (const m of text.matchAll(/create\s+npc\s+([A-Za-z_][A-Za-z0-9_]*)/g)) {
    if (!seen.has(m[1])) {
      seen.add(m[1]);
      ids.push(m[1]);
    }
  }
  return ids;
}

/** Id из `create npc_prefab id` в тексте .spr */
export function extractCreateNpcPrefabIdsFromSpr(text) {
  const ids = [];
  if (!text) return ids;
  const seen = new Set();
  for (const m of text.matchAll(/create\s+npc_prefab\s+([A-Za-z_][A-Za-z0-9_]*)/g)) {
    if (!seen.has(m[1])) {
      seen.add(m[1]);
      ids.push(m[1]);
    }
  }
  return ids;
}

/** Id экземпляров из spawnNpcPrefab("prefab", "instance", ...) в тексте .spr */
export function extractSpawnNpcInstanceIdsFromSpr(text) {
  const ids = [];
  if (!text) return ids;
  const seen = new Set();
  for (const m of text.matchAll(/spawnNpcPrefab\s*\(\s*"[^"]*"\s*,\s*"([^"]+)"/g)) {
    if (!seen.has(m[1])) {
      seen.add(m[1]);
      ids.push(m[1]);
    }
  }
  return ids;
}

/** Id НИПов из блоков «создать НИП» / «спавн префаба» в открытом workspace. */
export function extractNpcIdsFromWorkspace(workspace) {
  const ids = [];
  if (!workspace) return ids;
  for (const block of workspace.getAllBlocks(false)) {
    const type = block.type || '';
    if (type.endsWith('npc_create')) {
      const id = readBlockFieldVal(block, 'id');
      if (id) ids.push(id);
    } else if (type.endsWith('npc_prefab_spawn')) {
      const inst = readBlockFieldVal(block, 'instance');
      if (inst) ids.push(inst);
    }
  }
  return ids;
}

/** Id префабов из блоков «префаб НИП» в workspace. */
export function extractNpcPrefabIdsFromWorkspace(workspace) {
  const ids = [];
  if (!workspace) return ids;
  for (const block of workspace.getAllBlocks(false)) {
    if (!block.type?.endsWith('npc_prefab_define')) continue;
    const id = readBlockFieldVal(block, 'id');
    if (id) ids.push(id);
  }
  return ids;
}

/** Собрать id для dropdown: импорты + блоки create npc / spawn в текущем скрипте. */
export function buildNpcDropdownIds(workspace, importedNpcIds) {
  const ids = new Set();
  for (const id of (importedNpcIds || [])) {
    if (id && id !== '_eventNpc') ids.add(String(id));
  }
  if (workspace) {
    for (const id of extractNpcIdsFromWorkspace(workspace)) ids.add(id);
    try {
      const code = generateWorkspaceCode(workspace);
      for (const id of extractCreateNpcIdsFromSpr(code)) ids.add(id);
      for (const id of extractSpawnNpcInstanceIdsFromSpr(code)) ids.add(id);
    } catch (e) {}
  }
  return [...ids];
}

/** Собрать id префабов для dropdown_npc_prefab. */
export function buildNpcPrefabDropdownIds(workspace) {
  const ids = new Set();
  if (workspace) {
    for (const id of extractNpcPrefabIdsFromWorkspace(workspace)) ids.add(id);
    try {
      for (const id of extractCreateNpcPrefabIdsFromSpr(generateWorkspaceCode(workspace))) ids.add(id);
    } catch (e) {}
  }
  return [...ids];
}

/** Обновить списки НИПов (create npc + spawn) и префабов, обновить dropdown. */
export function syncNpcDropdownsFromWorkspace(workspace, cache) {
  const npcs = buildNpcDropdownIds(workspace, cache?.importedNpcIds);
  const prefabs = buildNpcPrefabDropdownIds(workspace);
  updateDynamicLists(
    npcs,
    cache?.anims,
    cache?.models,
    cache?.textures,
    cache?.animFiles,
    cache?.animsByFile,
    prefabs
  );
  if (workspace) refreshDynamicDropdownFields(workspace);
  return npcs;
}

/** Id из блоков npc_create в .sprv (не dropdown «выбор НИПа»). */
export function extractNpcCreateIdsFromBlocklyXml(xmlDom) {
  const ids = new Set();
  if (!xmlDom) return [];
  const blocks = xmlDom.getElementsByTagName('block');
  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const type = block.getAttribute('type') || '';
    if (!type.endsWith('npc_create')) continue;
    for (let j = 0; j < block.childNodes.length; j++) {
      const child = block.childNodes[j];
      if (child.nodeName === 'field' && child.getAttribute('name') === 'id') {
        const val = (child.textContent || '').trim();
        if (val) ids.add(val);
      }
    }
    const mutation = block.getElementsByTagName('mutation')[0];
    if (mutation) {
      const fid = mutation.getAttribute('f_id');
      if (fid?.trim()) ids.add(fid.trim());
    }
  }
  return [...ids];
}

/** После обновления списков — восстановить значения dropdown из val_* / mutation. */
export function refreshDynamicDropdownFields(workspace) {
  if (!workspace) return;
  for (const block of workspace.getAllBlocks(false)) {
    block.syncValFromFields_?.();
    for (const key of Object.getOwnPropertyNames(block)) {
      if (!key.startsWith('val_')) continue;
      const fname = key.slice(4);
      const cached = block[key];
      if (cached == null || String(cached).trim() === '') continue;
      try {
        block.setFieldValue(String(cached), fname);
        block[key] = String(cached);
      } catch (e) {}
    }
  }
}

function getDimensionDropdown() {
  return [
    ["Обычный мир", "overworld"],
    ["Ад", "nether"],
    ["Край", "the_end"],
  ];
}

// ================= ПАРСЕР #\ БЛОКОВ =================
export let customCategories = {};
export let customParsers = [];
export let pluginCategoryOrder = [];
/** fullId блока → строки для начала скрипта (import, startScript и т.д.) */
export let blockWriteStart = new Map();

export function clearCustomCategories() {
  customCategories = {};
  customParsers = [];
  pluginCategoryOrder = [];
  blockWriteStart = new Map();
}

const _missingBlockPlaceholders = new Set();

/** Заглушка для типов блоков из .sprv, которых больше нет в плагинах (переименование/удаление). */
export function registerMissingBlockType(type) {
  if (!type || _missingBlockPlaceholders.has(type)) return;
  if (Blockly.Blocks[type] && typeof Blockly.Blocks[type].init === 'function') return;

  _missingBlockPlaceholders.add(type);
  const label = type.includes('.') ? type.split('.').pop() : type;
  Blockly.Blocks[type] = {
    init: function () {
      this.appendDummyInput()
        .appendField('⚠ устаревший блок')
        .appendField(label, 'MISSING_LABEL');
      this.appendDummyInput()
        .appendField(new FieldMultilineInput(`// тип «${type}» не найден — обновите блок`), 'CODE');
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour('#b91c1c');
      this.setTooltip(`Блок «${type}» отсутствует в плагинах. Замените на актуальный из палитры.`);
    },
  };
  SprauteGenerator.forBlock[type] = function (block) {
    const code = block.getFieldValue('CODE') || '';
    return `/* MISSING BLOCK: ${type} */\n${code}\n`;
  };
}

/** Старые .sprv могли хранить type с «:» вместо «.» (procode:npc_chat → procode.npc_chat). */
function normalizeLegacyBlockType(type) {
  if (!type || !type.includes(':')) return type;
  const dot = type.replace(/:/g, '.');
  if (Blockly.Blocks[dot] && typeof Blockly.Blocks[dot].init === 'function') return dot;
  return type;
}

/** Регистрирует заглушки для всех неизвестных type= в XML перед domToWorkspace. */
export function prepareBlocklyXmlForLoad(xmlDom) {
  const missing = [];
  if (!xmlDom) return missing;
  const blocks = xmlDom.getElementsByTagName('block');
  const seen = new Set();
  for (let i = 0; i < blocks.length; i++) {
    let type = blocks[i].getAttribute('type');
    if (!type || seen.has(type)) continue;
    const normalized = normalizeLegacyBlockType(type);
    if (normalized !== type) blocks[i].setAttribute('type', normalized);
    type = normalized;
    seen.add(type);
    if (Blockly.Blocks[type] && typeof Blockly.Blocks[type].init === 'function') continue;
    registerMissingBlockType(type);
    missing.push(type);
  }
  return missing;
}

export function registerPluginCategoryOrder(categories) {
  if (!Array.isArray(categories)) return;
  for (const cat of categories) {
    if (!cat) continue;
    const idx = pluginCategoryOrder.indexOf(cat);
    if (idx !== -1) pluginCategoryOrder.splice(idx, 1);
    pluginCategoryOrder.push(cat);
  }
}

export function applyPluginCategoryColors(colors) {
  if (!colors || typeof colors !== 'object') return;
  for (const [name, color] of Object.entries(colors)) {
    if (customCategories[name]) customCategories[name].color = color;
  }
}

export function sortPluginBlocks(namespace, orderByCategory) {
  if (!orderByCategory || typeof orderByCategory !== 'object') return;
  for (const [catName, blockIds] of Object.entries(orderByCategory)) {
    if (!customCategories[catName] || !Array.isArray(blockIds)) continue;
    const blocks = customCategories[catName].blocks;
    const remaining = [...blocks];
    const sorted = [];
    for (const rawId of blockIds) {
      const fullId = namespace ? `${namespace}.${rawId}` : rawId;
      const idx = remaining.indexOf(fullId);
      if (idx !== -1) {
        sorted.push(remaining[idx]);
        remaining.splice(idx, 1);
      }
    }
    customCategories[catName].blocks = [...sorted, ...remaining];
  }
}

/** Имя скрипта из import("x") (или устаревший startScript("x")). */
function parseWriteStartScriptName(line) {
  const m = line.trim().match(/^(?:startScript|import)\(\s*["']([^"']+)["']\s*\)$/);
  return m ? m[1] : null;
}

/** Собирает уникальные write_start строки из типов блоков, присутствующих в workspace. */
export function collectWriteStartLines(workspace) {
  if (!workspace) return '';
  const seenLines = new Set();
  const scriptOrder = [];
  const otherLines = [];

  for (const block of workspace.getAllBlocks(false)) {
    const wsLines = blockWriteStart.get(block.type);
    if (!wsLines) continue;
    for (const line of wsLines) {
      const trimmed = line.trim();
      if (!trimmed || seenLines.has(trimmed)) continue;
      seenLines.add(trimmed);

      const scriptName = parseWriteStartScriptName(trimmed);
      if (scriptName) {
        if (!scriptOrder.includes(scriptName)) scriptOrder.push(scriptName);
        continue;
      }
      otherLines.push(trimmed);
    }
  }

  const lines = scriptOrder.map(name => `import("${name}")`);
  lines.push(...otherLines);
  return lines.join('\n');
}

/** Генерация кода workspace + префикс write_start из использованных блоков. */
export function generateWorkspaceCode(workspace) {
  if (!workspace) return '';
  const body = SprauteGenerator.workspaceToCode(workspace);
  const prefix = collectWriteStartLines(workspace);
  if (!prefix) return body;
  if (!body || !body.trim()) return prefix + '\n';
  return prefix + '\n' + body;
}

/**
 * Парсит текст с #\ комментариями и регистрирует блоки в Blockly.
 * @param {string} text — содержимое .spr файла
 * @param {string} pluginNamespace — префикс плагина, например "myplugin"
 */
export function parseCustomBlocks(text, pluginNamespace = "", isPreview = false) {
  if (!text || !text.trim()) return;

  // Разбиваем на блоки по маркеру "block:"
  const blockChunks = text.split(/(?:^|\n)#\\?\s*block:\s*/);
  
  for (let i = 1; i < blockChunks.length; i++) {
    const chunk = "block: " + blockChunks[i];
    try {
      _registerBlockFromChunk(chunk, pluginNamespace, isPreview);
    } catch(err) {
      console.error("[BlockParser] Failed to parse block chunk:", err, "\nChunk:", chunk.substring(0, 200));
    }
  }
}

function _parseLine(rawLine) {
  // Убираем #\ prefix если есть
  let m = rawLine.match(/^#\\?\s?(.*)/);
  return m ? m[1] : rawLine;
}

function _registerBlockFromChunk(chunk, namespace, isPreview) {
  const allLines = chunk.split('\n');
  const lines = allLines.map(_parseLine);

  let rawId = "", category = "Custom", color = "#555555", shape = "statement";
  let bodyLines = [];
  let writeStartLines = [];
  
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
      else if (trimmed.startsWith('write_start:')) {
        const line = trimmed.slice(13).trim();
        if (line) writeStartLines.push(line);
        continue;
      }
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
  const fullId = namespace ? `${namespace}.${rawId}` : rawId;

  if (!isPreview) {
    if (!customCategories[category]) {
      customCategories[category] = { color, blocks: [] };
    } else if (customCategories[category].color) {
      color = customCategories[category].color;
    } else if (color && color !== '#555555') {
      customCategories[category].color = color;
    }
    if (!customCategories[category].blocks.includes(fullId)) {
      customCategories[category].blocks.push(fullId);
    }
    if (writeStartLines.length > 0) {
      blockWriteStart.set(fullId, [...writeStartLines]);
    }
  }

  function compileUiLine(lineStr, inputName) {
    const line = lineStr.trim();
    let js = "";
    if (line.startsWith('row:')) {
      const rowStr = line.slice(4).trim();
      js += `self.slotToggles_ = self.slotToggles_ || {};\n`;
      js += `var row = self.appendDummyInput(${JSON.stringify(inputName)});\n`;
      js += `self.dynamicInputNames_.push(${JSON.stringify(inputName)});\n`;
      
      const rowTokenRe = /input:\s*(\w+)\s*\(type:\s*(value|statement)\)|"[^"]*"|\[[^\]]+\]|\([^)]+\)/gi;
      let tailCounter = 0;
      let afterValueInput = false;
      let tm;
      while ((tm = rowTokenRe.exec(rowStr)) !== null) {
        const tok = tm[0];
        if (/^input:/i.test(tok)) {
          afterValueInput = true;
          const iname = tm[1];
          const itype = tm[2].toLowerCase();
          if (itype === 'value') {
            valueInputNames.add(iname);
            js += `row = self.appendValueInput(${JSON.stringify(iname)});\n`;
          } else {
            js += `row = self.appendStatementInput(${JSON.stringify(iname)});\n`;
          }
          js += `self.dynamicInputNames_.push(${JSON.stringify(iname)});\n`;
          continue;
        }
        if (afterValueInput) {
          const tailName = inputName + '_tail_' + tailCounter++;
          js += `row = self.appendDummyInput(${JSON.stringify(tailName)});\n`;
          js += `self.dynamicInputNames_.push(${JSON.stringify(tailName)});\n`;
          afterValueInput = false;
        }
        if (tok.startsWith('"')) {
          js += `row.appendField(${tok});\n`;
        } else if (tok.startsWith('[')) {
          const inner = tok.slice(1, -1);
          const colonIdx = inner.indexOf(':');
          const name = inner.slice(0, colonIdx).trim();
          const typeDef = inner.slice(colonIdx + 1).trim();
          const contName = inputName + '_cont_' + name;
          
          js += `var toggleIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNSIgaGVpZ2h0PSIxNSIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgOWwtMyAzIDMgM200LTZsMyAzLTMgMyIvPjwvc3ZnPg==";\n`;
          js += `if (self.slotToggles_[${JSON.stringify(name)}]) {\n`;
          js += `  row = self.appendValueInput(${JSON.stringify(name)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(name)});\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=false; self.updateShape_(); }));\n`;
          js += `  row = self.appendDummyInput(${JSON.stringify(contName)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(contName)});\n`;
          js += `} else {\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=true; self.updateShape_(); }));\n`;
          if (typeDef === 'dropdown_npc') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getNpcsDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_anim') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getAnimsDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_animfile') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getAnimFilesDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_model') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getModelsDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_texture') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getTexturesDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_npc_prefab') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getNpcPrefabsDropdownFor(self, ${JSON.stringify(name)}); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef === 'dropdown_dimension') {
            js += `  row.appendField(new Blockly.FieldDropdown(function(){ return getDimensionDropdown(); }, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
          } else if (typeDef.startsWith('checkbox')) {
            const cbMatch = typeDef.match(/^checkbox(?:\((true|false)\))?$/i);
            const defChecked = cbMatch && cbMatch[1] ? cbMatch[1].toLowerCase() === 'true' : false;
            js += `  row.appendField(new Blockly.FieldCheckbox(${JSON.stringify(defChecked ? 'TRUE' : 'FALSE')}), ${JSON.stringify(name)});\n`;
          } else {
            const optsMatch = typeDef.match(/^dropdown\((.*)\)$/s);
            if (optsMatch) {
              const optsArr = optsMatch[1].split(',').map(o => {
                const parts = o.split(':').map(s => s.trim());
                return [parts[0], parts.length > 1 ? parts[1] : parts[0]];
              });
              js += `  row.appendField(new Blockly.FieldDropdown(${JSON.stringify(optsArr)}, function(v){ self.validateField(${JSON.stringify(name)}, v); return v; }), ${JSON.stringify(name)});\n`;
            }
          }
          js += `}\n`;
        } else if (tok.startsWith('(')) {
          const inner = tok.slice(1, -1);
          const parts = inner.split(':').map(s => s.trim());
          const name = parts[0], typeDef = parts[1] || 'text', def = parts.slice(2).join(':').trim() || "";
          const contName = inputName + '_cont_' + name;
          
          js += `var toggleIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNSIgaGVpZ2h0PSIxNSIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTAgOWwtMyAzIDMgM200LTZsMyAzLTMgMyIvPjwvc3ZnPg==";\n`;
          js += `if (self.slotToggles_[${JSON.stringify(name)}]) {\n`;
          js += `  row = self.appendValueInput(${JSON.stringify(name)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(name)});\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=false; self.updateShape_(); }));\n`;
          js += `  row = self.appendDummyInput(${JSON.stringify(contName)});\n`;
          js += `  self.dynamicInputNames_.push(${JSON.stringify(contName)});\n`;
          js += `} else {\n`;
          js += `  row.appendField(new Blockly.FieldImage(toggleIcon, 15, 15, "*", function(){ self.slotToggles_[${JSON.stringify(name)}]=true; self.updateShape_(); }));\n`;
          const defLit = JSON.stringify(def.replace(/\\n/g, '\n').replace(/\\t/g, '\t'));
          if (typeDef === 'textmultiline') {
            js += `  row.appendField(new FieldMultilineInput(self[${JSON.stringify("val_"+name)}] || ${defLit}), ${JSON.stringify(name)});\n`;
          } else {
            js += `  row.appendField(new Blockly.FieldTextInput(self[${JSON.stringify("val_"+name)}] || ${defLit}), ${JSON.stringify(name)});\n`;
          }
          js += `}\n`;
        }
      }
    } else if (line.startsWith('input:')) {
      const m = line.match(/^input:\s*(\w+)\s*\(type:\s*(\w+)\)(?:\s+"([^"]+)")?/);
      if (m) {
        const [, iname, itype, label=""] = m;
        if (itype === 'value') {
          valueInputNames.add(iname);
          js += `var inp = self.appendValueInput(${JSON.stringify(iname)});\n`;
        } else {
          js += `var inp = self.appendStatementInput(${JSON.stringify(iname)});\n`;
        }
        if (label) js += `inp.appendField(${JSON.stringify(label)});\n`;
        js += `self.dynamicInputNames_.push(${JSON.stringify(iname)});\n`;
      }
    }
    return js;
  }

  let dynUiJs = "";
  let codeGenJs = "";
  let extractedTemplates = [];
  let conditionVars = new Set();
  let filledConditionVars = new Set();
  // Метаданные для корректного round-trip (код -> блоки):
  const valueInputNames = new Set();   // имена value-инпутов (вложенные блоки, а не поля)
  const templateConds = [];            // параллельно extractedTemplates: дискриминаторы {var: value}

  // Разбирает условие if в список простых равенств var=="value" (только чистые AND).
  function parseSimpleEqs(origCond) {
    if (/\bor\b/.test(origCond) || /\bnot\b/.test(origCond) || origCond.includes('!=')) return [];
    const eqs = [];
    for (const part of origCond.split(/\band\b/)) {
      const m = part.match(/^\s*([a-zA-Z_]\w*)\s*==\s*"([^"]*)"\s*$/);
      if (m) eqs.push({ v: m[1], value: m[2] });
    }
    return eqs;
  }
  const condStack = []; // {indent, eqs}

  if (isLegacy) {
    let dynCounter = 0;
    for (const raw of uiLines) {
      if (raw.trim()) dynUiJs += compileUiLine(raw.trim(), `DYN_${dynCounter++}`);
    }
    const indentStack = [];
    for (const raw of dynamicUiLines) {
      if (!raw.trim()) continue;
      const indent = raw.match(/^\s*/)[0].length;
      while (indentStack.length > 0 && indentStack[indentStack.length-1] >= indent) {
        dynUiJs += "}\n";
        indentStack.pop();
      }
      const line = raw.trim();
      if (line.startsWith('if ') && line.endsWith(':')) {
        let cond = line.slice(3, -1).trim();
        cond = cond.replace(/"[^"]*"|'[^']*'|\b([a-zA-Z_]\w*)\b/g, (m, g1) => {
          if (!g1) return m;
          if (['true','false','null','undefined'].includes(g1)) return g1;
          conditionVars.add(g1);
          return `(self[${JSON.stringify("val_"+g1)}] || self.getFieldValue(${JSON.stringify(g1)}))`;
        });
        dynUiJs += `if (${cond}) {\n`;
        indentStack.push(indent);
      } else {
        dynUiJs += compileUiLine(line, `DYN_${dynCounter++}`);
      }
    }
    while (indentStack.length > 0) { dynUiJs += "}\n"; indentStack.pop(); }
    codeGenJs = codeGenLines.map(l => _parseLine(l)).join('\n');
    
    if (codeParseLines.length > 0) {
      const pCode = codeParseLines.map(l => _parseLine(l)).join('\n');
      if (pCode.trim().startsWith('"') || pCode.trim().startsWith('`')) {
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

    let pendingLabel = null;
    for (let _bi = 0; _bi < bodyLines.length; _bi++) {
      const raw = bodyLines[_bi];
      if (!raw.trim()) continue;
      const indent = raw.match(/^\s*/)[0].length;
      const line = raw.trim();
      
      while (dynIndentStack.length > 0 && dynIndentStack[dynIndentStack.length-1] >= indent) {
        dynUiJs += "}\n"; dynIndentStack.pop();
      }
      while (codeIndentStack.length > 0 && codeIndentStack[codeIndentStack.length-1] >= indent) {
        codeGenJs += "}\n"; codeIndentStack.pop();
      }
      while (condStack.length > 0 && condStack[condStack.length-1].indent >= indent) {
        condStack.pop();
      }

      if (/^if_filled\s+([a-zA-Z_]\w*)\s*:$/.test(line)) {
        pendingLabel = null;
        const fieldName = line.match(/^if_filled\s+([a-zA-Z_]\w*)\s*:$/)[1];
        filledConditionVars.add(fieldName);
        dynUiJs += `if (_filled(${JSON.stringify(fieldName)})) {\n`;
        dynIndentStack.push(indent);
      } else if (line.startsWith('if ') && line.endsWith(':')) {
        pendingLabel = null;
        const origCond = line.slice(3, -1).trim();
        condStack.push({ indent, eqs: parseSimpleEqs(origCond) });
        let cond = origCond;
        cond = cond.replace(/\bor\b/g, '||').replace(/\band\b/g, '&&').replace(/\bnot\b/g, '!');
        
        // Temporarily extract string literals
        let strLits = [];
        let condNoStr = cond.replace(/"[^"]*"|'[^']*'/g, (m) => {
            strLits.push(m);
            return `__STR${strLits.length-1}__`;
        });

        let uiCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
          if (['true','false','null','undefined','TRUE','FALSE'].includes(m) || m.startsWith('__STR')) return m;
          conditionVars.add(m);
          return `(self[${JSON.stringify("val_"+m)}] || self.getFieldValue(${JSON.stringify(m)}))`;
        });
        
        let codeCondNoStr = condNoStr.replace(/\b([a-zA-Z_]\w*)\b/g, (m) => {
          if (['true','false','null','undefined','TRUE','FALSE'].includes(m) || m.startsWith('__STR')) return m === 'TRUE' ? 'true' : m === 'FALSE' ? 'false' : m;
          return `_getVal(${JSON.stringify(m)})`;
        });
        
        // Put strings back
        let uiCond = uiCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
        let codeCond = codeCondNoStr.replace(/__STR(\d+)__/g, (m, idx) => strLits[idx]);
        
        dynUiJs += `if (${uiCond}) {\n`;
        dynIndentStack.push(indent);
        
        codeGenJs += `if (${codeCond}) {\n`;
        codeIndentStack.push(indent);
        
      } else if (line.startsWith('template:')) {
        let tmpl = line.slice(9).trim();
        let actualTmpl = tmpl.replace(/\\n/g, '\n').replace(/\\t/g, '\t');
        extractedTemplates.push(actualTmpl);
        // Снимок активных дискриминаторов для этого шаблона
        const conds = {};
        for (const c of condStack) for (const e of c.eqs) conds[e.v] = e.value;
        templateConds.push(conds);
        let jsTmpl = actualTmpl
          .replace(/"\{([a-zA-Z0-9_]+)\}"/g, (match, v) => `\${_str(${JSON.stringify(v)})}`)
          .replace(/\{([a-zA-Z0-9_]+)\}/g, (match, v) => `\${_getVal(${JSON.stringify(v)})}`);
        if (shape === 'value') {
          codeGenJs += `return \`${jsTmpl}\`;\n`;
        } else {
          codeGenJs += `return \`${jsTmpl}\\n\`;\n`;
        }
      } else if (line.startsWith('code:')) {
        codeGenJs += line.slice(5).trim() + "\n";
      } else if (line.startsWith('row:') || line.startsWith('input:')) {
        // Lookahead: if row is only text labels and next non-empty line is input: xxx (type: value), merge them
        if (line.startsWith('row:')) {
          const rowContent = line.slice(4).trim();
          // Check if row contains ONLY quoted strings (no [ ] or ( ) field tokens)
          const isLabelOnly = /^("(?:[^"\\]|\\.)*"\s*)+$/.test(rowContent);
          if (isLabelOnly) {
            // Find next non-empty body line
            const remaining = bodyLines.slice(_bi + 1);
            let nextLine = null;
            for (const nr of remaining) {
              const nt = nr.trim();
              if (nt && !nt.startsWith('if ') && !nt.startsWith('template:') && !nt.startsWith('code:')) {
                nextLine = nt; break;
              } else if (nt) break;
            }
            if (nextLine && nextLine.startsWith('input:')) {
              const m2 = nextLine.match(/^input:\s*(\w+)\s*\(type:\s*value\)/);
              if (m2) {
                // Skip this row — it'll be merged in the input: handling below
                pendingLabel = rowContent.replace(/"/g, '').trim();
                dynCounter++;
                continue;
              }
            }
          }
        }
        if (line.startsWith('input:')) {
          const m2 = line.match(/^input:\s*(\w+)\s*\(type:\s*(\w+)\)/);
          if (m2 && m2[2] === 'value' && pendingLabel) {
            const iname = m2[1];
            valueInputNames.add(iname);
            dynUiJs += `var inp = self.appendValueInput(${JSON.stringify(iname)});\n`;
            dynUiJs += `inp.appendField(${JSON.stringify(pendingLabel)});\n`;
            dynUiJs += `self.dynamicInputNames_.push(${JSON.stringify(iname)});\n`;
            pendingLabel = null;
            continue;
          }
          pendingLabel = null;
        }
        dynUiJs += compileUiLine(line, `DYN_${dynCounter++}`);
      }
    }
    while (dynIndentStack.length > 0) { dynUiJs += "}\n"; dynIndentStack.pop(); }
    while (codeIndentStack.length > 0) { codeGenJs += "}\n"; codeIndentStack.pop(); }
  }

  if (filledConditionVars.size > 0) {
    dynUiJs = `var _filled = function(n){
  if (self.workspace && self.workspace._sprauteRestoringBlocks && self._sprauteForceFilled_ && self._sprauteForceFilled_[n]) return true;
  if (self._sprautePendingChildIds_ && self._sprautePendingChildIds_[n]) return true;
  try {
    var inp = self.getInput(n);
    if (inp && inp.type === 1 && inp.connection && inp.connection.targetBlock()) return true;
  } catch (e) {}
  if (self.filledWatchFields_ && self.filledWatchFields_.indexOf(n) >= 0) return false;
  var cached = self["val_"+n];
  if (cached != null && String(cached).trim().length > 0) return true;
  try {
    var fv = self.getFieldValue(n);
    if (fv != null && String(fv).trim().length > 0) return true;
  } catch (e) {}
  return false;
};\n` + dynUiJs;
  }

  const hasDyn = dynUiJs.trim().length > 0;
  const condVarsArray = Array.from(conditionVars);
  const filledVarsArray = Array.from(filledConditionVars);
  const valueInputNamesArray = Array.from(valueInputNames);

  Blockly.Blocks[fullId] = {
    init: function() {
      const self = this;
      this.dynamicInputNames_ = [];
      this.dynamicValueInputNames_ = valueInputNamesArray;
      this.trackedFields_ = [];
      this.conditionVars_ = condVarsArray;
      this.filledWatchFields_ = filledVarsArray;
      this._sprauteForceFilled_ = filledVarsArray.length ? {} : null;
      this._sprauteShape_ = shape;
      
      this.setColour(color);
      this.setInputsInline(false);
      if (shape === 'value') {
        this.setOutput(true, null);
      } else {
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
      }

      try {
        const initFn = new Function('Blockly', 'FieldMultilineInput', 'getNpcsDropdown', 'getNpcsDropdownFor', 'getAnimsDropdown', 'getAnimsDropdownFor', 'getAnimFilesDropdown', 'getAnimFilesDropdownFor', 'getModelsDropdown', 'getModelsDropdownFor', 'getTexturesDropdown', 'getTexturesDropdownFor', 'getDimensionDropdown', 'self', "");
        initFn(Blockly, FieldMultilineInput, getNpcsDropdown, getNpcsDropdownFor, getAnimsDropdown, getAnimsDropdownFor, getAnimFilesDropdown, getAnimFilesDropdownFor, getModelsDropdown, getModelsDropdownFor, getTexturesDropdown, getTexturesDropdownFor, getDimensionDropdown, self);
      } catch(e) { console.error(`[Block ${fullId}] Init UI error:`, e); }

      if (shape === 'wrapper') {
        this.appendStatementInput("DO");
      }

      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (field.name) {
            this.trackedFields_.push(field.name);
            this[`val_${field.name}`] = field.getValue();
          }
        }
      }
      if (hasDyn) {
        if (!this.workspace?._sprauteRestoringBlocks) {
          this.updateShape_();
        }
        // При загрузке .sprv форму строит domToMutation (после чтения f_/c_ из mutation)
      }
    },

    /** Только добавляет поля/слоты из dynUiJs без пересборки DO (для загрузки .sprv). */
    applyDynUiOnly_: function() {
      if (!dynUiJs) return;
      const self = this;
      try {
        const dynFn = new Function('Blockly', 'FieldMultilineInput', 'getNpcsDropdown', 'getNpcsDropdownFor', 'getAnimsDropdown', 'getAnimsDropdownFor', 'getAnimFilesDropdown', 'getAnimFilesDropdownFor', 'getModelsDropdown', 'getModelsDropdownFor', 'getTexturesDropdown', 'getTexturesDropdownFor', 'getDimensionDropdown', 'self', dynUiJs);
        dynFn(Blockly, FieldMultilineInput, getNpcsDropdown, getNpcsDropdownFor, getAnimsDropdown, getAnimsDropdownFor, getAnimFilesDropdown, getAnimFilesDropdownFor, getModelsDropdown, getModelsDropdownFor, getTexturesDropdown, getTexturesDropdownFor, getDimensionDropdown, self);
      } catch (e) {
        console.error(`[Block ${fullId}] applyDynUiOnly error:`, e);
      }
    },

    /** Непрерывная цепочка if_filled: слот N+1 только если слот N реально заполнен. */
    pruneFilledWatchState_: function() {
      const fields = this.filledWatchFields_;
      if (!fields?.length) return;
      this._sprauteForceFilled_ = this._sprauteForceFilled_ || {};
      const restoring = !!this.workspace?._sprauteRestoringBlocks;

      for (let i = 0; i < fields.length; i++) {
        const n = fields[i];
        const filled = isValueSlotFilled(this, n) || (restoring && this._sprauteForceFilled_[n]);
        if (!filled) {
          for (let j = i; j < fields.length; j++) {
            delete this._sprauteForceFilled_[fields[j]];
            delete this[`val_${fields[j]}`];
          }
          break;
        }
        this._sprauteForceFilled_[n] = true;
      }
    },

    expandFilledWatchChain_: function() {
      this.pruneFilledWatchState_();
    },

    clearFilledWatchFrom_: function(fromInputName) {
      const fields = this.filledWatchFields_;
      if (!fields?.length) return;
      const idx = fields.indexOf(fromInputName);
      if (idx < 0) return;
      for (let i = idx; i < fields.length; i++) {
        const n = fields[i];
        if (this._sprauteForceFilled_) delete this._sprauteForceFilled_[n];
        delete this[`val_${n}`];
      }
    },

    syncFilledWatchMutation_: function() {
      this.pruneFilledWatchState_();
    },

    /** Сохраняет код из value-слотов в val_* перед пересборкой формы. */
    snapshotValueInputs_: function() {
      const dynNames = this.dynamicValueInputNames_;
      for (const input of this.inputList) {
        if (!input.name || input.type !== 1 || !input.connection) continue;
        if (dynNames?.length && !dynNames.includes(input.name)) continue;
        const child = input.connection.targetBlock();
        if (!child) continue;
        try {
          let gen = SprauteGenerator.blockToCode(child);
          if (Array.isArray(gen)) gen = gen[0];
          const s = gen == null ? '' : String(gen).trim();
          if (s) this[`val_${input.name}`] = s;
        } catch (e) {}
      }
    },

    syncCodeSlotCache_: function() {
      if (!this.dynamicValueInputNames_?.length) return;
      this.snapshotValueInputs_();
    },

    validateField: function(name, newValue) {
      const old = this[`val_${name}`];
      this[`val_${name}`] = newValue;
      const reshape = hasDyn && !this._restoringShape_ && old !== newValue &&
        (this.conditionVars_.includes(name) || this.filledWatchFields_.includes(name));
      if (reshape) {
        const self = this;
        setTimeout(() => { if (self.workspace) self.updateShape_(); }, 0);
      }
      if (!this._restoringShape_ && old !== newValue && (name === 'animation' || name === 'npc' || name === 'prop')) {
        const ws = this.workspace;
        const src = this;
        setTimeout(() => {
          if (ws && !ws._sprauteRestoringBlocks) onSprauteAnimContextChanged(ws, src);
        }, 0);
      }
      return newValue;
    },

    syncValFromFields_: function() {
      const restoring = !!(this.workspace?._sprauteRestoringBlocks || this._restoringShape_);
      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (!field.name) continue;
          try {
            if (restoring) {
              const cached = this[`val_${field.name}`];
              if (cached != null && String(cached).trim() !== '') continue;
            }
            const v = field.getValue();
            if (v != null && v !== '') this[`val_${field.name}`] = v;
          } catch(e) {}
        }
      }
    },

    mutationToDom: function() {
      this.syncValFromFields_();
      this.syncCodeSlotCache_();
      const container = Blockly.utils.xml.createElement('mutation');
      const saved = new Set();
      for (const key of Object.getOwnPropertyNames(this)) {
        if (!key.startsWith('val_')) continue;
        const fname = key.slice(4);
        const val = this[key];
        if (val != null && val !== '') {
          container.setAttribute(`f_${fname}`, val);
          saved.add(fname);
        }
      }
      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (field.name && !saved.has(field.name)) {
            const val = field.getValue();
            if (val != null && val !== '') {
              container.setAttribute(`f_${field.name}`, val);
              this[`val_${field.name}`] = val;
            }
          }
        }
      }
      if (this.slotToggles_) {
        for (const key in this.slotToggles_) {
           if (this.slotToggles_[key]) {
             container.setAttribute(`t_${key}`, "true");
           }
        }
      }
      if (this.filledWatchFields_?.length) {
        this._sprauteForceFilled_ = this._sprauteForceFilled_ || {};
        for (const n of this.filledWatchFields_) {
          if (isValueSlotFilled(this, n)) this._sprauteForceFilled_[n] = true;
          if (this._sprauteForceFilled_[n]) {
            container.setAttribute(`v_${n}`, 'true');
          }
        }
      }
      if (this.dynamicValueInputNames_?.length) {
        for (const n of this.dynamicValueInputNames_) {
          const cached = this[`val_${n}`];
          if (cached != null && String(cached).trim() !== '') {
            container.setAttribute(`c_${n}`, String(cached));
          }
        }
      }
      return container;
    },

    domToMutation: function(xml) {
      this.slotToggles_ = {};
      this._sprauteForceFilled_ = {};
      for (const attr of xml.attributes) {
        if (attr.name.startsWith('f_')) {
          this[`val_${attr.name.slice(2)}`] = attr.value;
        } else if (attr.name.startsWith('t_')) {
          this.slotToggles_[attr.name.slice(2)] = (attr.value === "true");
        } else if (attr.name.startsWith('v_')) {
          if (attr.value === 'true') this._sprauteForceFilled_[attr.name.slice(2)] = true;
        } else if (attr.name.startsWith('c_')) {
          this[`val_${attr.name.slice(2)}`] = attr.value;
        }
      }
      this.expandFilledWatchChain_();
      // При загрузке .sprv — развернуть все сохранённые слоты до подключения вложенных блоков.
      if (hasDyn) {
        this._restoringShape_ = true;
        try {
          this.updateShape_();
        } finally {
          this._restoringShape_ = false;
        }
      }
    },

    /** JSON-сериализация (Ctrl+C/V) — до подключения дочерних блоков. */
    saveExtraState: function() {
      this.syncValFromFields_();
      this.syncCodeSlotCache_();
      const state = {};
      for (const key of Object.getOwnPropertyNames(this)) {
        if (!key.startsWith('val_')) continue;
        const fname = key.slice(4);
        const val = this[key];
        if (val != null && val !== '') state[fname] = val;
      }
      for (const input of this.inputList) {
        for (const field of input.fieldRow) {
          if (!field.name || state[field.name] != null) continue;
          try {
            const val = field.getValue();
            if (val != null && val !== '') state[field.name] = val;
          } catch (e) {}
        }
      }
      if (this.slotToggles_) {
        const toggles = {};
        for (const key in this.slotToggles_) {
          if (this.slotToggles_[key]) toggles[key] = true;
        }
        if (Object.keys(toggles).length) state._toggles = toggles;
      }
      if (this.filledWatchFields_?.length) {
        this._sprauteForceFilled_ = this._sprauteForceFilled_ || {};
        const filled = {};
        for (const n of this.filledWatchFields_) {
          if (isValueSlotFilled(this, n)) this._sprauteForceFilled_[n] = true;
          if (this._sprauteForceFilled_[n]) filled[n] = true;
        }
        if (Object.keys(filled).length) state._filledSlots = filled;
      }
      if (this.dynamicValueInputNames_?.length) {
        const codeSlots = {};
        for (const n of this.dynamicValueInputNames_) {
          const cached = this[`val_${n}`];
          if (cached != null && String(cached).trim() !== '') codeSlots[n] = String(cached);
        }
        if (Object.keys(codeSlots).length) state._codeSlots = codeSlots;
      }
      return state;
    },

    loadExtraState: function(state) {
      if (!state || typeof state !== 'object') return;
      this.slotToggles_ = {};
      this._sprauteForceFilled_ = {};
      if (state._toggles) {
        for (const key in state._toggles) {
          if (state._toggles[key]) this.slotToggles_[key] = true;
        }
      }
      if (state._filledSlots) {
        for (const key in state._filledSlots) {
          if (state._filledSlots[key]) this._sprauteForceFilled_[key] = true;
        }
        this.expandFilledWatchChain_();
      }
      if (state._codeSlots) {
        for (const [n, code] of Object.entries(state._codeSlots)) {
          if (code != null && String(code).trim() !== '') this[`val_${n}`] = String(code);
        }
      }
      for (const [fname, val] of Object.entries(state)) {
        if (fname === '_toggles' || fname === '_filledSlots' || fname === '_codeSlots') continue;
        this[`val_${fname}`] = val;
      }
      if (hasDyn) {
        this._restoringShape_ = true;
        try {
          this.updateShape_();
        } finally {
          this._restoringShape_ = false;
        }
      }
    },

    onchange: function(e) {
      if (e.blockId !== this.id) return;
      if (e.type !== Blockly.Events.BLOCK_CHANGE) return;
      if (e.element === 'collapsed') {
        if (!e.newValue && this._sprauteReshapeWhenExpanded_) {
          this._sprauteReshapeWhenExpanded_ = false;
          const self = this;
          setTimeout(() => { if (self.workspace) self.updateShape_(); }, 0);
        }
        return;
      }
      if (e.name) {
        this[`val_${e.name}`] = e.newValue;
        if (hasDyn && !this._restoringShape_ && this.filledWatchFields_.includes(e.name)) {
          const self = this;
          setTimeout(() => { if (self.workspace) self.updateShape_(); }, 0);
        }
      }
    },

    updateShape_: function() {
      if (!dynUiJs) return;
      const self = this;
      if (self._sprauteReshaping) return;

      // Пересборка формы на свёрнутом блоке ломает типы полей и отваливает вложенные блоки.
      if (typeof self.isCollapsed === 'function' && self.isCollapsed()) {
        self._sprauteReshapeWhenExpanded_ = true;
        return;
      }
      self._sprauteReshapeWhenExpanded_ = false;

      self._sprauteReshaping = true;
      try {
      // Актуальные значения полей (в т.ч. применённые Blockly из XML после domToMutation)
      this.syncValFromFields_();
      this.snapshotValueInputs_();
      if (self.filledWatchFields_?.length && !self.workspace?._sprauteRestoringBlocks) {
        self.syncFilledWatchMutation_();
      }

      // 1. Сохраняем id вложенных блоков ДО удаления инпутов
      const savedChildIds = {};
      const savedStmtChains = {};
      for (const input of this.inputList) {
        if (!input.name || !input.connection) continue;
        const child = input.connection.targetBlock();
        if (child && !child.isDisposed()) {
          savedChildIds[input.name] = child.id;
          if (input.type === 3) {
            const chain = [];
            let cur = child;
            while (cur && !cur.isDisposed()) {
              chain.push(cur.id);
              cur = cur.nextConnection?.targetBlock() || null;
            }
            savedStmtChains[input.name] = chain;
          }
        }
      }

      // 2. Удаляем старые динамические инпуты (quiet=true: дети отсоединяются, но не уничтожаются)
      const toRemove = [...this.dynamicInputNames_];
      for (const n of toRemove) {
        if (this.getInput(n)) this.removeInput(n, true);
      }
      this.dynamicInputNames_ = [];

      // 3. Пересоздаём форму (_filled видит слоты по id до восстановления связей)
      self._sprautePendingChildIds_ = { ...savedChildIds };
      try {
        const dynFn = new Function('Blockly', 'FieldMultilineInput', 'getNpcsDropdown', 'getNpcsDropdownFor', 'getAnimsDropdown', 'getAnimsDropdownFor', 'getAnimFilesDropdown', 'getAnimFilesDropdownFor', 'getModelsDropdown', 'getModelsDropdownFor', 'getTexturesDropdown', 'getTexturesDropdownFor', 'getDimensionDropdown', 'self', dynUiJs);
          dynFn(Blockly, FieldMultilineInput, getNpcsDropdown, getNpcsDropdownFor, getAnimsDropdown, getAnimsDropdownFor, getAnimFilesDropdown, getAnimFilesDropdownFor, getModelsDropdown, getModelsDropdownFor, getTexturesDropdown, getTexturesDropdownFor, getDimensionDropdown, self);
      } catch(e) { console.error(`[Block ${fullId}] Dynamic UI error:`, e); }
      self._sprautePendingChildIds_ = null;

      // 4. Восстанавливаем подключения по id блока
      const ws = self.workspace;
      for (const n in savedChildIds) {
        const inp = self.getInput(n);
        const child = ws && ws.getBlockById(savedChildIds[n]);
        if (!inp?.connection || !child || child.isDisposed()) continue;
        try {
          if (inp.type === 1 && child.outputConnection) {
            if (!inp.connection.targetConnection) inp.connection.connect(child.outputConnection);
          } else if (inp.type === 3 && child.previousConnection) {
            if (!inp.connection.targetConnection) inp.connection.connect(child.previousConnection);
            const chain = savedStmtChains[n];
            if (chain && chain.length > 1) {
              for (let ci = 0; ci < chain.length - 1; ci++) {
                const a = ws.getBlockById(chain[ci]);
                const b = ws.getBlockById(chain[ci + 1]);
                if (a?.nextConnection && b?.previousConnection && !a.nextConnection.targetConnection) {
                  a.nextConnection.connect(b.previousConnection);
                }
              }
            }
          }
        } catch (e) {}
      }

      // DO создаётся в init() до динамических полей; всегда переносим в конец (и при загрузке .sprv).
      if (shape === 'wrapper' && self.getInput("DO")) {
         const doIdx = self.inputList.findIndex(i => i.name === 'DO');
         if (doIdx >= 0 && doIdx < self.inputList.length - 1) {
           var doConn = self.getInput("DO").connection.targetConnection;
           self.removeInput("DO", true);
           var doInp = self.appendStatementInput("DO");
           if (doConn) doInp.connection.connect(doConn);
           const doChain = savedStmtChains.DO;
           if (doChain && doChain.length > 1 && ws) {
             for (let ci = 0; ci < doChain.length - 1; ci++) {
               const a = ws.getBlockById(doChain[ci]);
               const b = ws.getBlockById(doChain[ci + 1]);
               if (a?.nextConnection && b?.previousConnection && !a.nextConnection.targetConnection) {
                 a.nextConnection.connect(b.previousConnection);
               }
             }
           }
         }
      }

      // 5. Восстанавливаем значения полей из val_* (не трогаем value-слоты — только dropdown/text)
      self._restoringShape_ = true;
      try {
        const skipNames = new Set(self.dynamicValueInputNames_ || []);
        for (const key of Object.getOwnPropertyNames(self)) {
          if (!key.startsWith('val_')) continue;
          const fname = key.slice(4);
          if (skipNames.has(fname)) continue;
          const fval = self[key];
          if (fval == null || fval === '') continue;
          try { self.setFieldValue(fval, fname); } catch(e) {}
        }
      } finally {
        self._restoringShape_ = false;
      }

      if (self.filledWatchFields_?.length) {
        self._sprauteFilledPrev = self._sprauteFilledPrev || {};
        for (const n of self.filledWatchFields_) {
          self._sprauteFilledPrev[n] = isValueSlotFilled(self, n);
        }
        if (!self.workspace?._sprauteRestoringBlocks) {
          self.pruneFilledWatchState_();
        }
      }
      } finally {
        self._sprauteReshaping = false;
      }
    }
  };

  SprauteGenerator.forBlock[fullId] = function(block) {
    function _getVal(name) {
      if (block.getInput(name) && block.getInput(name).type === 3) {
        return SprauteGenerator.statementToCode(block, name, "") || "";
      }
      let target = block.getInputTargetBlock(name);
      if (target) {
        try {
          let gen = SprauteGenerator.blockToCode(target);
          if (Array.isArray(gen)) return gen[0] || "";
          return gen || "";
        } catch (e) {}
      }
      let cached = block[`val_${name}`];
      if (cached != null && String(cached).trim() !== '') return normalizeBlocklyLiteral(String(cached).trim());
      try {
        if (block.getField(name)) {
          let v = block.getFieldValue(name);
          if (v != null && v !== '') return normalizeBlocklyLiteral(v);
        }
      } catch (e) {}
      const fallback = block[`val_${name}`] ?? "";
      return fallback === '' ? '' : normalizeBlocklyLiteral(String(fallback));
    }

    /** Строковый литерал для шаблонов: не дублирует кавычки у блока «текст». */
    function _str(name) {
      const raw = String(_getVal(name) ?? '').trim();
      if (!raw) return '""';
      if (raw.startsWith('"') && raw.endsWith('"')) return raw;
      if (raw.startsWith("'") && raw.endsWith("'")) return raw;
      return JSON.stringify(raw);
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
          ctx += `var ${f} = ${JSON.stringify(v === null ? "" : v)};\n`;
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
            ctx += `var ${input.name} = ${JSON.stringify(gen || "")};\n`;
          }
        }
    }

    let finalCodeGen = codeGenJs;
    if (!finalCodeGen && extractedTemplates.length === 1 && !isLegacy && !hasDyn) {
        const tmplJs = extractedTemplates[0]
          .replace(/"\{([a-zA-Z0-9_]+)\}"/g, (m, v) => `\${_str(${JSON.stringify(v)})}`)
          .replace(/\{([a-zA-Z0-9_]+)\}/g, (m, v) => `\${_getVal(${JSON.stringify(v)})}`);
        if (shape === 'value') {
          finalCodeGen = `return \`${tmplJs}\`;\n`;
        } else {
          finalCodeGen = `return \`${tmplJs}\\n\`;\n`;
        }
    }

    const fullCode = (isLegacy ? ctx : "") + finalCodeGen;
    try {
      const fn = new Function('_getVal', '_str', 'block', 'SprauteGenerator', fullCode);
      const res = fn(_getVal, _str, block, SprauteGenerator);
      if (shape === 'value') return [String(res ?? '').replace(/\s*\n\s*/g, ' ').trim(), 0];
      return ensureStatementTrailingNewline(res);
    } catch(e) {
      return `/* CodeGen error in ${fullId}: ${e.message} */\n`;
    }
  };

  if (!isPreview && extractedTemplates.length > 0) {
    for (let ti = 0; ti < extractedTemplates.length; ti++) {
      const tmpl = extractedTemplates[ti];
      const conds = templateConds[ti] || {};
      let regexStr = tmpl.replace(/[.*+?^$()|[\]\\]/g, '\\$&');
      const vars = [];
      regexStr = regexStr.replace(/{([a-zA-Z0-9_]+)}/g, (match, varName) => {
        vars.push(varName);
        return '([\\s\\S]*?)';
      });
      regexStr = regexStr.replace(/\s+/g, '\\s*');
      regexStr = `^\\s*${regexStr}`;

      const valueVarsList = vars.filter(v => valueInputNames.has(v));

      const pCode = `
        const m = text.match(/${regexStr}/i);
        if (m) {
          const fields = {};
          const statements = {};
          const valueExprs = {};
          const conds = ${JSON.stringify(conds)};
          const varNames = ${JSON.stringify(vars)};
          const valueVars = ${JSON.stringify(valueVarsList)};
          for (let i = 0; i < varNames.length; i++) {
            const v = varNames[i];
            const val = m[i+1].trim();
            if (v === v.toUpperCase() && v.length > 1) {
              statements[v] = val;
            } else if (valueVars.includes(v)) {
              valueExprs[v] = val;
            } else {
              fields[v] = val;
            }
          }
          return { length: m[0].length, fields, statements, valueExprs, conds };
        }
        return null;
      `;
      try {
        customParsers.push({
          id: fullId, plugin: namespace, shape,
          fn: new Function('text', pCode),
          priority: tmpl.replace(/\{[^}]+\}/g, '').length
        });
      } catch(e) { console.error(`[Block ${fullId}] CodeParse error:`, e); }
    }
  }
}

// ================= КАСТОМНОЕ КОНТЕКСТНОЕ МЕНЮ BLOCKLY =================
let _ctxBlock = null;

/** Blockly 12+: duplicate() удалён — копируем через clipboard API. */
function duplicateBlocklyBlock(block, dx = 30, dy = 30) {
  if (!block || typeof block.toCopyData !== 'function') return null;
  if (typeof block.isDuplicatable === 'function' && !block.isDuplicatable()) return null;
  const copyData = block.toCopyData();
  if (!copyData) return null;
  const ws = block.workspace;
  if (!ws) return null;
  const xy = block.getRelativeToSurfaceXY();
  const coord = new Blockly.utils.Coordinate(xy.x + dx, xy.y + dy);
  return Blockly.clipboard.paste(copyData, ws, coord);
}
let _ctxCloseHandler = null;

function hideBlocklyCtxMenu() {
  const el = document.getElementById('blockly-ctx-menu');
  if (el) el.classList.add('hidden');
  if (_ctxCloseHandler) {
    document.removeEventListener('pointerdown', _ctxCloseHandler, true);
    _ctxCloseHandler = null;
  }
  _ctxBlock = null;
}

function showBlocklyCtxMenu(block, x, y) {
  const el = document.getElementById('blockly-ctx-menu');
  if (!el) return;
  _ctxBlock = block;

  const isCollapsed = block.isCollapsed();
  const isFav = isFavorite(block.type);

  const items = [
    {
      icon: '⭐', label: isFav ? 'Убрать из избранного' : 'В избранное',
      action() { isFav ? removeFavorite(block.type) : addFavorite(block.type); refreshToolbox(); }
    },
    { separator: true },
    {
      icon: isCollapsed ? '▼' : '▲',
      label: isCollapsed ? 'Развернуть блок' : 'Свернуть блок',
      action() { block.setCollapsed(!block.isCollapsed()); }
    },
    {
      icon: '⧉', label: 'Дублировать',
      action() {
        const ws = block.workspace;
        beginBlocklyRestore(ws);
        try {
          duplicateBlocklyBlock(block);
        } finally {
          setTimeout(() => endBlocklyRestore(ws), 0);
        }
      }
    },
    {
      icon: '💬', label: 'Добавить комментарий',
      action() { block.setCommentText(block.getCommentText() == null ? '' : null); }
    },
    { separator: true },
    {
      icon: '🗑', label: 'Удалить блок', danger: true,
      action() {
        Blockly.Events.setGroup(true);
        block.dispose(true, true);
        Blockly.Events.setGroup(false);
      }
    },
  ];

  el.innerHTML = items.map((item, i) => {
    if (item.separator) return `<div class="my-1 border-t border-white/10"></div>`;
    return `<button data-idx="${i}" class="w-full text-left px-3 py-1.5 flex items-center gap-2 hover:bg-primary/20 transition-colors rounded-lg mx-1 ${item.danger ? 'text-red-400 hover:text-red-300 hover:bg-red-500/20' : ''}">
      <span class="text-base w-5 text-center">${item.icon}</span>
      <span>${item.label}</span>
    </button>`;
  }).join('');

  // Attach click handlers
  el.querySelectorAll('button[data-idx]').forEach(btn => {
    const idx = parseInt(btn.dataset.idx);
    btn.addEventListener('pointerdown', (e) => {
      e.stopPropagation();
      hideBlocklyCtxMenu();
      items[idx].action();
    });
  });

  // Position
  el.classList.remove('hidden');
  const vw = window.innerWidth, vh = window.innerHeight;
  const menuW = 180, menuH = el.offsetHeight || 240;
  const left = x + menuW > vw ? x - menuW : x;
  const top  = y + menuH > vh ? y - menuH : y;
  el.style.left = `${left}px`;
  el.style.top  = `${top}px`;

  // Close on outside click
  setTimeout(() => {
    _ctxCloseHandler = (e) => {
      if (!el.contains(e.target)) hideBlocklyCtxMenu();
    };
    document.addEventListener('pointerdown', _ctxCloseHandler, true);
  }, 0);
}

/** Проверка, заполнен ли value-слот (подключённый блок; без устаревшего val_* для if_filled). */
function isValueSlotFilled(block, inputName) {
  if (block._sprautePendingChildIds_?.[inputName]) return true;
  try {
    const inp = block.getInput(inputName);
    if (inp && inp.type === 1 && inp.connection && inp.connection.targetBlock()) return true;
  } catch (e) {}
  if (block.filledWatchFields_?.includes(inputName)) return false;
  const cached = block['val_' + inputName];
  if (cached != null && String(cached).trim().length > 0) return true;
  return false;
}

/** Один слушатель на workspace: пересборка блоков с if_filled при смене вложений. */
let _dynamicReshapeWorkspace = null;

export function attachDynamicBlockReshapeListener(workspace) {
  if (!workspace || workspace === _dynamicReshapeWorkspace) return;
  _dynamicReshapeWorkspace = workspace;

  workspace.addChangeListener((e) => {
    if (workspace._sprauteRestoringBlocks) return;

    if (e.type === Blockly.Events.BLOCK_CHANGE && e.element === 'field') {
      if (e.name === 'animation' || e.name === 'npc' || e.name === 'prop') {
        const block = workspace.getBlockById(e.blockId);
        if (block && !block.isDisposed()) {
          setTimeout(() => {
            if (!workspace._sprauteRestoringBlocks) onSprauteAnimContextChanged(workspace, block);
          }, 0);
        }
      }
      return;
    }

    let parentId = null;
    let inputName = null;
    if (e.type === Blockly.Events.BLOCK_MOVE) {
      if (e.newParentId === e.oldParentId && e.newInputName === e.oldInputName) return;
      parentId = e.newParentId != null ? e.newParentId : e.oldParentId;
      inputName = e.newInputName != null ? e.newInputName : e.oldInputName;
    } else if (e.type === Blockly.Events.BLOCK_DELETE) {
      parentId = e.oldParentId;
      inputName = e.oldInputName;
    } else {
      return;
    }

    if (!parentId || !inputName) return;
    const parent = workspace.getBlockById(parentId);
    if (!parent || parent.isDisposed() || typeof parent.updateShape_ !== 'function') return;
    if (parent._sprauteReshaping) return;
    if (!parent.filledWatchFields_?.includes(inputName)) return;

    clearTimeout(parent._sprauteReshapeDebounce);
    parent._sprauteReshapeDebounce = setTimeout(() => {
      if (parent.isDisposed() || !parent.workspace) return;
      if (parent.isDisposed() || parent._sprauteReshaping) return;

      const prev = parent._sprauteFilledPrev?.[inputName];
      const next = isValueSlotFilled(parent, inputName);
      parent._sprauteFilledPrev = parent._sprauteFilledPrev || {};
      parent._sprauteFilledPrev[inputName] = next;

      if (prev === next) return;
      if (prev === undefined && !next) return;

      if (prev && !next) {
        parent.clearFilledWatchFrom_(inputName);
      }

      parent.updateShape_();
    }, 80);
  });
}

/** Вызывается после inject workspace — подключает перехват ПКМ. */
export function attachBlocklyContextMenu(workspace) {
  // Перехватываем contextmenu на контейнере
  const div = workspace.getInjectionDiv ? workspace.getInjectionDiv() : null;
  const target = div || document.getElementById('blockly-mount');
  if (!target) return;

  target.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    e.stopPropagation();

    // Определяем блок под курсором через Blockly gesture / clientCoords
    const ws = Blockly.getMainWorkspace();
    if (!ws) return;

    // Ищем ближайший блок по SVG-элементу
    let el = e.target;
    let block = null;
    while (el && el !== target) {
      const id = el.getAttribute && el.getAttribute('data-id');
      if (id) {
        const found = ws.getBlockById(id);
        if (found) { block = found; break; }
      }
      el = el.parentElement;
    }

    // Если блок не найден через data-id, пробуем через common.selected
    if (!block) {
      try { block = Blockly.common.getSelected(); } catch {}
    }
    if (!block) return; // клик по пустому workspace — не показываем наше меню

    showBlocklyCtxMenu(block, e.clientX, e.clientY);
  });
}

// ================= ТЕМА =================
export const SprauteTheme = Blockly.Theme.defineTheme('spraute_dark', {
  base: Blockly.Themes.Dark,
  blockStyles: {},
  categoryStyles: {},
  componentStyles: {
    workspaceBackgroundColour: 'transparent',
    toolboxBackgroundColour: 'var(--color-surface, #0b1a2f)',
    toolboxForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutBackgroundColour: 'var(--color-bg, #040e1f)',
    flyoutForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutOpacity: 0.95,
    scrollbarColour: 'var(--color-primary, #38bdf8)',
    insertionMarkerColour: '#fff',
    insertionMarkerOpacity: 0.3,
    scrollbarOpacity: 0.4,
    cursorColour: 'var(--color-primary, #d0d0d0)',
    blackBackground: 'var(--color-bg, #040e1f)'
  }
});

export function applyBlocklyThemeColors(colors) {
  if (!Blockly.getMainWorkspace()) return;
  const theme = Blockly.Theme.defineTheme('spraute_dynamic', {
    base: Blockly.Themes.Dark,
    blockStyles: {}, categoryStyles: {},
    componentStyles: {
      workspaceBackgroundColour: 'transparent',
      toolboxBackgroundColour: colors.surface || '#0b1a2f',
      toolboxForegroundColour: colors.text || '#ffffff',
      flyoutBackgroundColour: colors.bg || '#040e1f',
      flyoutForegroundColour: colors.text || '#ffffff',
      flyoutOpacity: 0.95,
      scrollbarColour: colors.primary || '#38bdf8',
      insertionMarkerColour: '#fff',
      insertionMarkerOpacity: 0.3,
      scrollbarOpacity: 0.4,
      cursorColour: colors.primary || '#d0d0d0',
      blackBackground: colors.bg || '#040e1f'
    }
  });
  Blockly.getMainWorkspace().setTheme(theme);
}

// ================= ИЗБРАННОЕ =================
const FAV_KEY = 'spraute_favorite_blocks';

export function getFavorites() {
  try { return JSON.parse(localStorage.getItem(FAV_KEY) || '[]'); }
  catch { return []; }
}

function saveFavorites(arr) {
  localStorage.setItem(FAV_KEY, JSON.stringify([...new Set(arr)]));
}

export function addFavorite(blockType) {
  const favs = getFavorites();
  if (!favs.includes(blockType)) { favs.push(blockType); saveFavorites(favs); }
}

export function removeFavorite(blockType) {
  saveFavorites(getFavorites().filter(f => f !== blockType));
}

export function isFavorite(blockType) {
  return getFavorites().includes(blockType);
}

/** Обновляет тулбокс с учётом избранного. Вызывается после изменений. */
export function refreshToolbox() {
  const ws = Blockly.getMainWorkspace();
  if (!ws) return;
  ws.updateToolbox(getDynamicToolbox());
}

// Регистрируем пункт контекстного меню один раз при загрузке модуля.
// В Blockly 12 используем ContextMenuRegistry.
(function registerFavouriteMenuItem() {
  const registry = Blockly.ContextMenuRegistry.registry;

  // Удаляем старую регистрацию при hot-reload
  try { registry.unregister('spraute_toggle_favourite'); } catch {}

  registry.register({
    id: 'spraute_toggle_favourite',
    scopeType: Blockly.ContextMenuRegistry.ScopeType.BLOCK,
    displayText(scope) {
      const type = scope.block && scope.block.type;
      return isFavorite(type) ? '★ Убрать из избранного' : '☆ В избранное';
    },
    preconditionFn(scope) {
      // Показываем только для зарегистрированных кастомных блоков
      return scope.block ? 'enabled' : 'hidden';
    },
    callback(scope) {
      const type = scope.block && scope.block.type;
      if (!type) return;
      if (isFavorite(type)) {
        removeFavorite(type);
      } else {
        addFavorite(type);
      }
      refreshToolbox();
    },
    weight: 6,   // Ниже стандартных пунктов (Delete ≈5, Collapse ≈4)
  });
})();

// ================= ТУЛБОКС =================
export function getDynamicToolbox() {
  const favs = getFavorites().filter(id => Blockly.Blocks[id]);

  const tb = {
    "kind": "categoryToolbox",
    "contents": []
  };

  // Категория «Избранное» — всегда первая, скрыта если пусто
  if (favs.length > 0) {
    tb.contents.push({
      "kind": "category",
      "name": "⭐ Избранное",
      "colour": "#f59e0b",
      "contents": favs.map(id => ({ "kind": "block", "type": id }))
    });
  }

  tb.contents.push({
    "kind": "category",
    "name": "Система",
    "colour": COLORS.SYSTEM,
    "contents": [{ "kind": "block", "type": "spraute_raw_code" }]
  });

  tb.contents.push({
    "kind": "category",
    "name": "GUI [BETA/баги]",
    "colour": GUI_COLOR,
    "contents": [
      { "kind": "label", "text": "[BETA/баги]" },
      { "kind": "label", "text": "Создание" },
      { "kind": "block", "type": "spraute_gui_create" },
      { "kind": "block", "type": "spraute_gui_add_widget" },
      { "kind": "block", "type": "spraute_gui_grid" },
      { "kind": "block", "type": "spraute_gui_player_inventory" },
      { "kind": "label", "text": "Открытие" },
      { "kind": "block", "type": "spraute_gui_open" },
      { "kind": "block", "type": "spraute_gui_close" },
      { "kind": "block", "type": "spraute_gui_is_open" },
      { "kind": "block", "type": "spraute_gui_container_open" },
      { "kind": "label", "text": "Виджеты" },
      { "kind": "block", "type": "spraute_gui_update" },
      { "kind": "block", "type": "spraute_gui_update_quick" },
      { "kind": "block", "type": "spraute_gui_animate" },
      { "kind": "label", "text": "Слоты GUI" },
      { "kind": "block", "type": "spraute_gui_slot_item" },
      { "kind": "block", "type": "spraute_gui_slot_empty" },
      { "kind": "block", "type": "spraute_gui_slot_has" },
      { "kind": "block", "type": "spraute_gui_slot_stack_count" },
      { "kind": "block", "type": "spraute_gui_slot_slots" },
      { "kind": "block", "type": "spraute_gui_set_slot" },
      { "kind": "block", "type": "spraute_gui_clear_slot" },
      { "kind": "label", "text": "Ввод и скролл" },
      { "kind": "block", "type": "spraute_gui_get_input" },
      { "kind": "block", "type": "spraute_gui_set_input" },
      { "kind": "block", "type": "spraute_gui_scroll_get" },
      { "kind": "block", "type": "spraute_gui_scroll_set" },
      { "kind": "block", "type": "spraute_gui_scroll_animate" }
    ]
  });

  for (const catName of (pluginCategoryOrder.length > 0
    ? [...pluginCategoryOrder, ...Object.keys(customCategories).filter(c => !pluginCategoryOrder.includes(c))]
    : Object.keys(customCategories))) {
    if (!customCategories[catName]) continue;
    tb.contents.push({
      "kind": "category",
      "name": catName,
      "colour": customCategories[catName].color,
      "contents": customCategories[catName].blocks.map(id => ({ "kind": "block", "type": id }))
    });
  }
  return tb;
}

// ================= ПАРСЕР ТЕКСТА В БЛОКИ =================

function splitArgs(argStr) {
  if (!argStr || !argStr.trim()) return [];
  const args = [];
  let cur = '', depth = 0, inStr = null;
  for (let i = 0; i < argStr.length; i++) {
    const c = argStr[i];
    if (inStr) {
      cur += c;
      if (c === inStr && argStr[i - 1] !== '\\') inStr = null;
      continue;
    }
    if (c === '"' || c === "'") { inStr = c; cur += c; continue; }
    if (c === '(' || c === '[' || c === '{') depth++;
    if (c === ')' || c === ']' || c === '}') depth--;
    if (c === ',' && depth === 0) { args.push(cur.trim()); cur = ''; continue; }
    cur += c;
  }
  if (cur.trim()) args.push(cur.trim());
  return args;
}

function extractBraceBody(text, openBracePos) {
  if (text[openBracePos] !== '{') return null;
  let depth = 0;
  for (let i = openBracePos; i < text.length; i++) {
    const c = text[i];
    if (c === '"' || c === "'") {
      const q = c;
      i++;
      while (i < text.length && (text[i] !== q || text[i - 1] === '\\')) i++;
      continue;
    }
    if (c === '{') depth++;
    if (c === '}') {
      depth--;
      if (depth === 0) return { body: text.slice(openBracePos + 1, i), end: i + 1 };
    }
  }
  return null;
}

function skipWsComments(text, pos) {
  while (pos < text.length) {
    const rest = text.slice(pos);
    const ws = rest.match(/^[\s\r\n]+/);
    if (ws) { pos += ws[0].length; continue; }
    const comment = rest.match(/^#[^\n]*/);
    if (comment) { pos += comment[0].length; continue; }
    break;
  }
  return pos;
}

function getSortedPluginParsers(shape) {
  return customParsers
    .filter(p => !shape || p.shape === shape)
    .sort((a, b) => (b.priority || 0) - (a.priority || 0));
}

function tryBestPluginParse(text, shape) {
  let best = null;
  for (const parser of getSortedPluginParsers(shape)) {
    try {
      const result = parser.fn(text);
      if (result && result.length > 0 && (!best || result.length > best.result.length)) {
        best = { parser, result };
      }
    } catch (e) {}
  }
  return best;
}

export function textToBlocks(text, workspace) {
  try {
    workspace.clear();
    const allBlocks = [];
    const x = 20, y = 20;

    function instantiateBlock(parserId, result, ws) {
      const newBlock = ws.newBlock(parserId);
      allBlocks.push(newBlock);

      const allFieldVals = Object.assign({}, result.conds || {}, result.fields || {});
      for (const [k, v] of Object.entries(allFieldVals)) {
        newBlock[`val_${k}`] = v;
        try { newBlock.setFieldValue(v, k); } catch (e) {}
      }
      if (newBlock.updateShape_) newBlock.updateShape_();
      for (const [k, v] of Object.entries(allFieldVals)) {
        try { newBlock.setFieldValue(v, k); } catch (e) {}
      }

      if (result.valueExprs) {
        for (const [k, expr] of Object.entries(result.valueExprs)) {
          if (!expr) continue;
          const valBlock = parseValueExpression(expr, ws);
          if (valBlock) {
            const input = newBlock.getInput(k);
            if (input?.connection && valBlock.outputConnection) {
              input.connection.connect(valBlock.outputConnection);
            }
          }
        }
      }

      if (result.statements) {
        for (const [k, innerStr] of Object.entries(result.statements)) {
          const innerFirst = parseStatements(innerStr, ws);
          if (innerFirst) {
            const input = newBlock.getInput(k);
            if (input?.connection) input.connection.connect(innerFirst.previousConnection);
          }
        }
      }

      newBlock.initSvg();
      return newBlock;
    }

    function parseValueExpression(expr, ws) {
      const trimmed = (expr || '').trim();
      if (!trimmed) return null;

      const best = tryBestPluginParse(trimmed, 'value');
      if (best && best.result.length >= trimmed.length - 2) {
        return instantiateBlock(best.parser.id, best.result, ws);
      }

      const raw = ws.newBlock('spraute_raw_value');
      allBlocks.push(raw);
      try { raw.setFieldValue(trimmed, 'EXPR'); } catch (e) {}
      raw.initSvg();
      return raw;
    }

    function parseStatements(str, currentWorkspace) {
      let currentConn = null;
      let firstBlock = null;
      let pos = 0;
      const text = str;

      while (pos < text.length) {
        pos = skipWsComments(text, pos);
        if (pos >= text.length) break;

        if (text[pos] === '}') { pos++; continue; }

        const rest = text.slice(pos);
        let consumed = 0;
        let newBlock = null;

        let best = tryBestPluginParse(rest, 'statement');
        if (!best) {
          const bestAny = tryBestPluginParse(rest, null);
          if (bestAny && bestAny.parser.shape !== 'value') best = bestAny;
        }

        if (best) {
          newBlock = instantiateBlock(best.parser.id, best.result, currentWorkspace);
          consumed = best.result.length;

          // Тело в фигурных скобках сразу после совпадения (on/if/async/fun/…)
          const after = skipWsComments(text, pos + consumed);
          if (text[after] === '{') {
            const body = extractBraceBody(text, after);
            if (body) {
              const doInput = newBlock.getInput('DO');
              if (doInput) {
                const innerFirst = parseStatements(body.body, currentWorkspace);
                if (innerFirst && doInput.connection) {
                  doInput.connection.connect(innerFirst.previousConnection);
                }
              }
              consumed = body.end - pos;
            }
          }
        } else {
          const lineEnd = rest.search(/\n/);
          const line = (lineEnd === -1 ? rest : rest.slice(0, lineEnd)).replace(/\r$/, '').replace(/\s+$/, '');
          consumed = lineEnd === -1 ? rest.length : lineEnd + 1;

          if (line && !line.trimStart().startsWith('#')) {
            newBlock = currentWorkspace.newBlock('spraute_raw_code');
            allBlocks.push(newBlock);
            newBlock.setFieldValue(line, 'CODE');
            newBlock.initSvg();
          }
        }

        if (newBlock) {
          if (currentConn) currentConn.connect(newBlock.previousConnection);
          else firstBlock = newBlock;
          currentConn = newBlock.nextConnection;
        }

        pos += consumed || 1;
        if (!consumed) break;
      }

      return firstBlock;
    }

    const rootBlock = parseStatements(text.trim(), workspace);
    if (rootBlock) rootBlock.moveBy(x, y);

    for (const b of allBlocks) {
      try { b.render(); } catch (e) {}
    }
  } catch (e) {
    console.error('Parse error', e);
  }
}

export function isMostlyRawBlocks(workspace) {
  if (!workspace) return false;
  const blocks = workspace.getAllBlocks(false);
  if (blocks.length === 0) return false;
  let raw = 0;
  for (const b of blocks) {
    if (b.type === 'spraute_raw_code' || b.type === 'spraute_raw_value') raw++;
  }
  return raw > 0 && raw / blocks.length >= 0.9;
}

export const toolbox = getDynamicToolbox();
