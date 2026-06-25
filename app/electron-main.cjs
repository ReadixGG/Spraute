const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs').promises;
const fsNative = require('fs');

let store;
let mainWindow;
let assetWatchers = [];
let assetWatchNotifyTimer = null;

function stopAssetWatchers() {
  for (const w of assetWatchers) {
    try { w.close(); } catch (_) {}
  }
  assetWatchers = [];
}

function notifyRendererAssetsChanged() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('assets-changed');
    mainWindow.webContents.send('workspace-changed');
  }
}

function scheduleAssetWatchNotify() {
  clearTimeout(assetWatchNotifyTimer);
  assetWatchNotifyTimer = setTimeout(notifyRendererAssetsChanged, 350);
}

function watchAssetDir(absDir) {
  if (!fsNative.existsSync(absDir)) return;
  try {
    const w = fsNative.watch(absDir, { recursive: true }, () => scheduleAssetWatchNotify());
    assetWatchers.push(w);
  } catch (_) {
    try {
      const w = fsNative.watch(absDir, () => scheduleAssetWatchNotify());
      assetWatchers.push(w);
    } catch (_2) {}
  }
}

function startAssetWatchers(mcPath) {
  stopAssetWatchers();
  if (!mcPath) return;
  const sprautePath = path.join(mcPath, 'spraute_engine');
  watchAssetDir(sprautePath);
}

function initStore() {
  const Store = require('electron-store');
  store = new Store({ name: 'spraute-studio' });
}

const isDev = !app.isPackaged;

// Отключаем GPU-ускорение для совместимости с некоторыми системами
app.disableHardwareAcceleration();

if (process.platform === 'win32') {
  app.setAppUserModelId('org.zonarstudio.spraute.studio');
}

function safeJoin(root, rel) {
  const rootR = path.resolve(root);
  const joined = path.resolve(rootR, rel === '' || rel == null ? '.' : rel);
  if (joined !== rootR && !joined.startsWith(rootR + path.sep)) {
    throw new Error('Invalid path');
  }
  return joined;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#040e1f',
    title: 'Spraute Studio',
    show: false,
    autoHideMenuBar: true,
    titleBarStyle: 'hidden', // скрывает нативную шапку (Windows/macOS)
    titleBarOverlay: { // кастомные кнопки управления окном (Windows)
      color: '#040e1f',
      symbolColor: '#dbe6fe',
      height: 32
    },
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false,
      // Иначе при loadFile(dist) скрипт type=module с file:// часто не выполняется — пустой экран
      webSecurity: false,
    },
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));

  if (isDev) {
    mainWindow.loadURL('http://127.0.0.1:5173');
    if (process.env.SPRAUTE_DEVTOOLS === '1') {
      mainWindow.webContents.openDevTools({ mode: 'detach' });
    }
  } else {
    mainWindow.loadFile(path.join(__dirname, 'dist', 'index.html'));
  }
}

app.whenReady().then(async () => {
  initStore();
  await ensurePluginsMigrated();
  await ensureBundledProcodeBlocks();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
    // Принудительное завершение через 500 мс на случай зависших процессов (например, автоапдейтер или таймеры)
    setTimeout(() => {
      process.exit(0);
    }, 500);
  }
});

ipcMain.handle('dialog:select-minecraft-folder', async () => {
  const { canceled, filePaths } = await dialog.showOpenDialog({
    properties: ['openDirectory', 'createDirectory'],
    title: 'Выберите папку Minecraft (корень .minecraft или аналог)',
  });
  if (canceled || !filePaths[0]) return null;
  return filePaths[0];
});

ipcMain.handle('dialog:select-image-file', async () => {
  const { canceled, filePaths } = await dialog.showOpenDialog({
    properties: ['openFile'],
    title: 'Выберите фоновое изображение',
    filters: [
      { name: 'Images', extensions: ['jpg', 'png', 'gif', 'webp', 'jpeg'] },
      { name: 'All Files', extensions: ['*'] }
    ]
  });
  if (canceled || !filePaths[0]) return null;
  return filePaths[0];
});

ipcMain.handle('store:get', (_e, key) => store.get(key));
ipcMain.handle('store:set', (_e, key, value) => {
  store.set(key, value);
});

function getWorkspaceRoot() {
  const mcPath = store.get('minecraftPath');
  if (!mcPath) throw new Error('Папка Minecraft не задана');
  return path.join(mcPath, 'spraute_engine');
}

/** Плагины Studio — вне папки проекта, сохраняются при смене Minecraft */
function getPluginsRoot() {
  return path.join(app.getPath('userData'), 'plugins');
}

function isPluginRelPath(relPath) {
  const norm = String(relPath ?? '').replace(/\\/g, '/');
  return norm === 'plugins' || norm.startsWith('plugins/');
}

function toPluginLocalRel(relPath) {
  const norm = String(relPath ?? '').replace(/\\/g, '/');
  if (norm === 'plugins') return '';
  if (norm.startsWith('plugins/')) return norm.slice('plugins/'.length);
  return relPath;
}

function resolveAbsPath(relPath) {
  if (isPluginRelPath(relPath)) {
    return safeJoin(getPluginsRoot(), toPluginLocalRel(relPath));
  }
  return safeJoin(getWorkspaceRoot(), relPath);
}

let lastMigratedMcPath = null;

async function ensurePluginsMigrated() {
  const studioRoot = getPluginsRoot();
  await fs.mkdir(studioRoot, { recursive: true });

  const mcPath = store.get('minecraftPath');
  if (!mcPath || lastMigratedMcPath === mcPath) return;
  lastMigratedMcPath = mcPath;

  const legacyRoot = path.join(mcPath, 'spraute_engine', 'plugins');
  if (!(await pathExists(legacyRoot))) return;

  const entries = await fs.readdir(legacyRoot, { withFileTypes: true }).catch(() => []);
  for (const entry of entries) {
    const src = path.join(legacyRoot, entry.name);
    const dest = path.join(studioRoot, entry.name);
    try {
      if (!(await pathExists(dest))) {
        await fs.cp(src, dest, { recursive: true });
      }
    } catch (e) {
      console.error('Plugin migration failed for', entry.name, e);
    }
  }
}

/** Категории procode: «Игрок и Мир» → «Игрок» / «Мир» / «Инвентарь». */
const PROCODE_MIR_BLOCKS = new Set([
  'set_block', 'find_safe_block', 'spawn_orb', 'execute_cmd', 'start_script',
]);
const PROCODE_INVENTORY_BLOCKS = new Set([
  'give_item', 'has_item', 'has_item_stmt', 'count_item', 'count_item_stmt',
  'player_held', 'player_held_stmt',
  'get_slot', 'get_slot_count', 'get_slot_nbt',
  'get_item_in_slot', 'get_player_inventory', 'has_item_in_slot', 'is_slot_empty', 'find_item_slot',
  'set_item_in_slot', 'set_item_in_slot_named', 'set_item_count', 'clear_item_slot', 'remove_item',
  'get_item_name', 'set_item_name', 'get_item_lore', 'set_item_lore',
  'get_item_attack_damage', 'set_item_attack_damage', 'get_item_nbt', 'set_item_nbt',
]);

function procodeCategoryForBlock(blockId, legacyCategory) {
  if (blockId && PROCODE_MIR_BLOCKS.has(blockId)) return 'Мир';
  if (blockId && PROCODE_INVENTORY_BLOCKS.has(blockId)) return 'Инвентарь';
  if (legacyCategory === 'Слоты') return 'Инвентарь';
  if (legacyCategory === 'Игрок и Мир') return 'Игрок';
  return legacyCategory;
}

async function migrateProcodeBlockCategories(pluginRoot) {
  const blocksDir = path.join(pluginRoot, 'blocks');
  if (!(await pathExists(blocksDir))) return;

  const obsoleteFiles = ['player_slots.spr'];
  for (const name of obsoleteFiles) {
    const fp = path.join(blocksDir, name);
    if (await pathExists(fp)) {
      await fs.unlink(fp).catch(() => {});
    }
  }

  const files = await fs.readdir(blocksDir).catch(() => []);
  for (const name of files) {
    if (!name.endsWith('.spr')) continue;
    const fp = path.join(blocksDir, name);
    let text = await fs.readFile(fp, 'utf8');
    const blockId = (text.match(/^#\\?\s*block:\s*(\S+)/m) || [])[1];
    const catMatch = text.match(/^#\\?\s*category:\s*(.+)$/m);
    if (!catMatch) continue;
    const legacy = catMatch[1].trim();
    if (legacy !== 'Игрок и Мир' && legacy !== 'Слоты') continue;
    const newCat = procodeCategoryForBlock(blockId, legacy);
    const next = text.replace(/^#\\?\s*category:\s*.+$/m, `#\\ category: ${newCat}`);
    if (next !== text) await fs.writeFile(fp, next, 'utf8');
  }

  const catPath = path.join(pluginRoot, 'categories.json');
  let cats = {};
  if (await pathExists(catPath)) {
    cats = JSON.parse(await fs.readFile(catPath, 'utf8'));
  }
  const legacyColor = cats['Игрок и Мир'] || '#84cc16';
  const newCats = {};
  for (const [key, color] of Object.entries(cats)) {
    if (key === 'Игрок и Мир') {
      newCats['Игрок'] = cats['Игрок'] || '#22c55e';
      newCats['Инвентарь'] = cats['Инвентарь'] || '#f59e0b';
      newCats['Мир'] = cats['Мир'] || legacyColor;
    } else if (key === 'Слоты') {
      // пропуск — объединено в «Инвентарь»
    } else {
      newCats[key] = color;
    }
  }
  if (!newCats['Игрок']) newCats['Игрок'] = '#22c55e';
  if (!newCats['Инвентарь']) newCats['Инвентарь'] = '#f59e0b';
  if (!newCats['Мир']) newCats['Мир'] = legacyColor;
  await fs.writeFile(catPath, JSON.stringify(newCats, null, 2), 'utf8');
}

/** Синхронизирует bundled-блоки procode и мигрирует категории. */
async function ensureBundledProcodeBlocks() {
  const bundledRoot = path.join(__dirname, 'bundled_plugins', 'procode');
  const pluginRoot = path.join(getPluginsRoot(), 'procode');
  const bundledBlocks = path.join(bundledRoot, 'blocks');
  const destBlocks = path.join(pluginRoot, 'blocks');

  if (!(await pathExists(bundledBlocks))) return;
  if (!(await pathExists(pluginRoot))) return;

  await migrateProcodeBlockCategories(pluginRoot);

  await fs.mkdir(destBlocks, { recursive: true });

  const files = await fs.readdir(bundledBlocks).catch(() => []);
  for (const name of files) {
    if (!name.endsWith('.spr')) continue;
    await fs.copyFile(path.join(bundledBlocks, name), path.join(destBlocks, name));
  }

  const bundledCatsPath = path.join(bundledRoot, 'categories.json');
  const destCatsPath = path.join(pluginRoot, 'categories.json');
  if (await pathExists(bundledCatsPath)) {
    try {
      const bundledCats = JSON.parse(await fs.readFile(bundledCatsPath, 'utf8'));
      let destCats = {};
      if (await pathExists(destCatsPath)) {
        destCats = JSON.parse(await fs.readFile(destCatsPath, 'utf8'));
      }
      delete destCats['Игрок и Мир'];
      delete destCats['Слоты'];
      Object.assign(destCats, bundledCats);
      await fs.writeFile(destCatsPath, JSON.stringify(destCats, null, 2), 'utf8');
    } catch (e) {
      console.error('procode categories merge failed:', e);
    }
  }

  await migrateProcodeBlockCategories(pluginRoot);

  const bundledOrderPath = path.join(bundledRoot, 'blocks_order.json');
  const destOrderPath = path.join(pluginRoot, 'blocks_order.json');
  if (!(await pathExists(bundledOrderPath))) return;

  try {
    const bundledOrder = JSON.parse(await fs.readFile(bundledOrderPath, 'utf8'));
    let destOrder = {};
    if (await pathExists(destOrderPath)) {
      destOrder = JSON.parse(await fs.readFile(destOrderPath, 'utf8'));
    }
    delete destOrder['Игрок и Мир'];
    delete destOrder['Слоты'];
    let changed = false;
    for (const [cat, ids] of Object.entries(bundledOrder)) {
      if (!Array.isArray(ids)) continue;
      destOrder[cat] = [...ids];
      changed = true;
    }
    if (changed) {
      await fs.writeFile(destOrderPath, JSON.stringify(destOrder, null, 2), 'utf8');
    }
  } catch (e) {
    console.error('procode blocks_order merge failed:', e);
  }
}

ipcMain.handle('fs:list', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const dir = resolveAbsPath(relPath);
  const stat = await fs.stat(dir).catch(() => null);
  if (!stat || !stat.isDirectory()) return [];
  const names = await fs.readdir(dir);
  const entries = await Promise.all(
    names.map(async (name) => {
      const full = path.join(dir, name);
      try {
        const s = await fs.stat(full);
        return {
          name,
          rel: path.join(relPath || '', name).replace(/\\/g, '/'),
          isDir: s.isDirectory(),
        };
      } catch {
        return null;
      }
    })
  );
  const list = entries.filter(Boolean);
  list.sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
  });
  return list;
});

ipcMain.handle('fs:read', async (_e, relPath, encoding = 'utf8') => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const file = resolveAbsPath(relPath);
  const stat = await fs.stat(file).catch(() => null);
  if (!stat || !stat.isFile()) throw new Error('Не файл');
  return fs.readFile(file, encoding);
});

ipcMain.handle('fs:write', async (_e, relPath, content) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const file = resolveAbsPath(relPath);
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, content, 'utf8');
});

ipcMain.handle('fs:writeBase64', async (_e, relPath, base64) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const file = resolveAbsPath(relPath);
  await fs.mkdir(path.dirname(file), { recursive: true });
  const buffer = Buffer.from(base64, 'base64');
  await fs.writeFile(file, buffer);
});

// Добавим экспорт плагина (создание ZIP)
ipcMain.handle('plugin:export', async (event, pluginName) => {
  try {
    const AdmZip = require('adm-zip');
    await ensurePluginsMigrated();
    const pluginPath = safeJoin(getPluginsRoot(), pluginName);
    
    // Проверяем, существует ли папка плагина
    try {
      await fs.access(pluginPath);
    } catch {
      return { success: false, error: 'Папка плагина не найдена' };
    }

    const { dialog } = require('electron');
    const { filePath } = await dialog.showSaveDialog(mainWindow, {
      title: 'Экспорт плагина',
      defaultPath: `${pluginName}.splugin`,
      filters: [{ name: 'Spraute Plugin', extensions: ['splugin'] }, { name: 'ZIP Архивы', extensions: ['zip'] }]
    });

    if (!filePath) return { success: false, error: 'Отменено пользователем' };

    const zip = new AdmZip();
    zip.addLocalFolder(pluginPath); // Внутри архива не нужна дополнительная папка, чтобы извлекать напрямую
    zip.writeZip(filePath);

    return { success: true, path: filePath };
  } catch (err) {
    return { success: false, error: err.message };
  }
});

function sanitizePluginName(name) {
  const trimmed = String(name || '').trim();
  if (!trimmed || /[./\\]/.test(trimmed)) {
    throw new Error('Некорректное имя плагина');
  }
  return trimmed;
}

async function pathExists(absPath) {
  try {
    await fs.access(absPath);
    return true;
  } catch {
    return false;
  }
}

function resolveZipPluginMeta(zip, filename) {
  const zipEntries = zip.getEntries();
  const hasRootPluginJson = zip.getEntry('plugin.json') != null;

  let rootFolder = null;
  const topDirs = new Set();
  for (const entry of zipEntries) {
    if (!entry.isDirectory) continue;
    const clean = entry.entryName.replace(/\/$/, '');
    if (!clean) continue;
    const parts = clean.split('/');
    if (parts.length === 1) topDirs.add(parts[0]);
  }
  if (topDirs.size === 1) {
    rootFolder = [...topDirs][0];
  } else if (!hasRootPluginJson) {
    const prefixes = new Map();
    for (const entry of zipEntries) {
      if (entry.isDirectory) continue;
      const prefix = entry.entryName.split('/')[0];
      if (prefix) prefixes.set(prefix, (prefixes.get(prefix) || 0) + 1);
    }
    if (prefixes.size === 1) rootFolder = [...prefixes.keys()][0];
  }

  let pluginName = path.basename(filename, path.extname(filename));
  let jsonEntry = zip.getEntry('plugin.json');
  if (!jsonEntry && rootFolder) {
    jsonEntry = zip.getEntry(`${rootFolder}/plugin.json`);
  }
  if (jsonEntry) {
    try {
      const data = JSON.parse(jsonEntry.getData().toString('utf8'));
      if (data.name && String(data.name).trim()) pluginName = String(data.name).trim();
    } catch (_) {}
  } else if (rootFolder && !hasRootPluginJson) {
    pluginName = rootFolder;
  }

  return {
    pluginName: sanitizePluginName(pluginName),
    hasRootPluginJson,
    rootFolder
  };
}

async function importPluginFromBuffer(buffer, filename, overwrite = false) {
  const AdmZip = require('adm-zip');
  await ensurePluginsMigrated();
  const zip = new AdmZip(buffer);
  const { pluginName, hasRootPluginJson, rootFolder } = resolveZipPluginMeta(zip, filename);

  const pluginsPath = getPluginsRoot();
  const destPath = safeJoin(pluginsPath, pluginName);
  const destExists = await pathExists(destPath);

  if (destExists && !overwrite) {
    return { success: false, error: 'exists', name: pluginName };
  }
  if (destExists && overwrite) {
    await fs.rm(destPath, { recursive: true, force: true });
  }

  await fs.mkdir(pluginsPath, { recursive: true });

  if (hasRootPluginJson) {
    await fs.mkdir(destPath, { recursive: true });
    zip.extractAllTo(destPath, true);
  } else if (rootFolder) {
    zip.extractAllTo(pluginsPath, true);
    const extractedPath = safeJoin(pluginsPath, rootFolder);
    if (rootFolder !== pluginName && await pathExists(extractedPath)) {
      if (await pathExists(destPath)) {
        await fs.rm(destPath, { recursive: true, force: true });
      }
      await fs.rename(extractedPath, destPath);
    }
  } else {
    await fs.mkdir(destPath, { recursive: true });
    zip.extractAllTo(destPath, true);
  }

  if (!(await pathExists(safeJoin(destPath, 'plugin.json')))) {
    await fs.rm(destPath, { recursive: true, force: true });
    return { success: false, error: 'В архиве не найден plugin.json' };
  }

  return { success: true, name: pluginName };
}

ipcMain.handle('plugin:import', async (_event, base64Data, filename, overwrite = false) => {
  try {
    const buffer = Buffer.from(base64Data, 'base64');
    return await importPluginFromBuffer(buffer, filename, overwrite);
  } catch (err) {
    return { success: false, error: err.message };
  }
});

ipcMain.handle('plugin:importDialog', async () => {
  try {
    const { canceled, filePaths } = await dialog.showOpenDialog(mainWindow, {
      title: 'Импорт плагина',
      filters: [
        { name: 'Spraute Plugin', extensions: ['splugin', 'zip'] },
        { name: 'ZIP-архивы', extensions: ['zip'] }
      ],
      properties: ['openFile']
    });
    if (canceled || !filePaths || !filePaths[0]) {
      return { success: false, cancelled: true };
    }

    const filePath = filePaths[0];
    const buffer = await fs.readFile(filePath);
    const filename = path.basename(filePath);
    let result = await importPluginFromBuffer(buffer, filename, false);

    if (!result.success && result.error === 'exists') {
      const { response } = await dialog.showMessageBox(mainWindow, {
        type: 'question',
        buttons: ['Заменить', 'Отмена'],
        defaultId: 0,
        cancelId: 1,
        title: 'Плагин уже установлен',
        message: `Плагин «${result.name}» уже существует.`,
        detail: 'Заменить существующую установку?'
      });
      if (response !== 0) return { success: false, cancelled: true };
      result = await importPluginFromBuffer(buffer, filename, true);
    }

    return result;
  } catch (err) {
    return { success: false, error: err.message };
  }
});

ipcMain.handle('fs:mkdir', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const dir = resolveAbsPath(relPath);
  await fs.mkdir(dir, { recursive: true });
});

ipcMain.handle('fs:unlink', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const file = resolveAbsPath(relPath);
  await fs.unlink(file);
});

ipcMain.handle('fs:rmdir', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const dir = resolveAbsPath(relPath);
  await fs.rm(dir, { recursive: true, force: true });
});

ipcMain.handle('fs:rename', async (_e, oldRelPath, newRelPath) => {
  if (isPluginRelPath(oldRelPath) || isPluginRelPath(newRelPath)) await ensurePluginsMigrated();
  const oldPath = resolveAbsPath(oldRelPath);
  const newPath = resolveAbsPath(newRelPath);
  await fs.mkdir(path.dirname(newPath), { recursive: true });
  await fs.rename(oldPath, newPath);
});

ipcMain.handle('fs:exists', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) {
    await ensurePluginsMigrated();
    const file = resolveAbsPath(relPath);
    try {
      await fs.access(file);
      return true;
    } catch {
      return false;
    }
  }
  const file = resolveAbsPath(relPath);
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
});

ipcMain.handle('fs:copy', async (_e, srcRel, destRel) => {
  if (isPluginRelPath(srcRel) || isPluginRelPath(destRel)) await ensurePluginsMigrated();
  const srcPath = resolveAbsPath(srcRel);
  const destPath = resolveAbsPath(destRel);
  await fs.mkdir(path.dirname(destPath), { recursive: true });
  await fs.cp(srcPath, destPath, { recursive: true });
});

ipcMain.handle('fs:search', async (_e, query) => {
  const root = getWorkspaceRoot();
  const results = [];
  query = query.toLowerCase();

  const textExts = ['.spr', '.json', '.js', '.txt', '.md', '.html', '.splugin'];

  async function walk(dir) {
    const entries = await fs.readdir(dir, { withFileTypes: true }).catch(() => []);
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      const relPath = path.relative(root, fullPath).replace(/\\/g, '/');
      
      // Игнорируем ненужные папки
      if (entry.name === '.git' || entry.name === 'node_modules' || entry.name === 'build' || entry.name === 'dist') continue;

      if (entry.isDirectory()) {
        await walk(fullPath);
      } else if (entry.isFile()) {
        const ext = path.extname(entry.name).toLowerCase();
        if (ext === '.sprv') continue;
        
        // 1. Поиск по имени файла
        if (entry.name.toLowerCase().includes(query)) {
          results.push({ file: relPath, type: 'file', match: entry.name });
        }
        
        // 2. Поиск внутри текстовых файлов
        if (textExts.includes(ext)) {
          try {
            const content = await fs.readFile(fullPath, 'utf8');
            const lines = content.split('\n');
            for (let i = 0; i < lines.length; i++) {
              if (lines[i].toLowerCase().includes(query)) {
                results.push({ 
                  file: relPath, 
                  type: 'content', 
                  line: i + 1, 
                  match: lines[i].trim() 
                });
              }
            }
          } catch(e) {
            // Игнорируем ошибки чтения
          }
        }
      }
    }
  }

  await walk(root);
  return results;
});

ipcMain.handle('app:show-in-explorer', async (_e, relPath) => {
  if (isPluginRelPath(relPath)) await ensurePluginsMigrated();
  const file = resolveAbsPath(relPath);
  shell.showItemInFolder(file);
});

ipcMain.handle('plugin:getStoragePath', async () => {
  await ensurePluginsMigrated();
  return getPluginsRoot();
});

ipcMain.handle('app:open-external', async (_e, url) => {
  shell.openExternal(url);
});

ipcMain.handle('app:init-workspace', async (event, mcPath) => {
  const modsPath = path.join(mcPath, 'mods');
  const sprautePath = path.join(mcPath, 'spraute_engine');
  const scriptsPath = path.join(sprautePath, 'scripts');
  const geoPath = path.join(sprautePath, 'geo');
  const animPath = path.join(sprautePath, 'animations');
  const texPath = path.join(sprautePath, 'textures', 'entity');

  await fs.mkdir(modsPath, { recursive: true }).catch(() => {});
  await fs.mkdir(scriptsPath, { recursive: true }).catch(() => {});
  await fs.mkdir(geoPath, { recursive: true }).catch(() => {});
  await fs.mkdir(animPath, { recursive: true }).catch(() => {});
  await fs.mkdir(texPath, { recursive: true }).catch(() => {});

  lastMigratedMcPath = null;
  await ensurePluginsMigrated();

  const log = (msg) => { event.sender.send('update-progress', msg); };
  log('Структура папок готова.');
  startAssetWatchers(mcPath);
  log('Запуск Spraute Studio...');
  return { success: true };
});

ipcMain.handle('app:set-titlebar', (event, color, symbolColor) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win && typeof win.setTitleBarOverlay === 'function') {
    win.setTitleBarOverlay({
      color: color,
      symbolColor: symbolColor
    });
  }
});

