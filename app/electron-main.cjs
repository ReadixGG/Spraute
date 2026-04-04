const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs').promises;
const { autoUpdater } = require('electron-updater');

let store;
let mainWindow;

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
  createWindow();

  // Настройка автообновления самой студии
  autoUpdater.autoDownload = false;
  
  let isStartupCheck = true;

  const checkAppUpdates = () => {
    if (!isDev) {
      autoUpdater.checkForUpdates().catch(err => {
        console.error('Ошибка проверки обновлений:', err);
      });
    }
  };

  checkAppUpdates();
  
  // Проверять обновления каждые 30 минут
  setInterval(() => {
    isStartupCheck = false;
    checkAppUpdates();
  }, 30 * 60 * 1000);

  autoUpdater.on('update-available', (info) => {
    if (mainWindow) {
      mainWindow.webContents.send('studio-update-available', { ...info, isStartupCheck });
    }
  });

  autoUpdater.on('download-progress', (progressObj) => {
    if (mainWindow) {
      mainWindow.webContents.send('studio-update-dl-progress', progressObj);
    }
  });

  autoUpdater.on('update-downloaded', () => {
    if (mainWindow) {
      mainWindow.webContents.send('studio-update-downloaded');
    }
  });

  ipcMain.handle('studio-update:download', () => {
    autoUpdater.downloadUpdate();
  });

  ipcMain.handle('studio-update:install', () => {
    autoUpdater.quitAndInstall(false, true);
  });

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

ipcMain.handle('fs:list', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const dir = safeJoin(root, relPath);
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
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  const stat = await fs.stat(file).catch(() => null);
  if (!stat || !stat.isFile()) throw new Error('Не файл');
  return fs.readFile(file, encoding);
});

ipcMain.handle('fs:write', async (_e, relPath, content) => {
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  await fs.writeFile(file, content, 'utf8');
});

ipcMain.handle('fs:writeBase64', async (_e, relPath, base64) => {
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  const buffer = Buffer.from(base64, 'base64');
  await fs.writeFile(file, buffer);
});

// Добавим экспорт плагина (создание ZIP)
ipcMain.handle('plugin:export', async (event, pluginName) => {
  try {
    const AdmZip = require('adm-zip');
    const root = getWorkspaceRoot();
    const pluginPath = safeJoin(root, 'plugins', pluginName);
    
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

ipcMain.handle('plugin:import', async (event, base64Data, filename) => {
  try {
    const AdmZip = require('adm-zip');
    const root = getWorkspaceRoot();
    
    // Создаем временный файл
    const tempZipPath = safeJoin(root, 'plugins', `_temp_${Date.now()}.zip`);
    const buffer = Buffer.from(base64Data, 'base64');
    await fs.writeFile(tempZipPath, buffer);

    const zip = new AdmZip(tempZipPath);
    
    // Пытаемся получить имя плагина из plugin.json внутри архива
    let pluginName = filename.replace('.splugin', '').replace('.zip', '');
    const zipEntries = zip.getEntries();
    
    // Проверим, есть ли внутри папка с именем плагина или файлы лежат в корне
    let hasRootPluginJson = false;
    let rootFolder = null;
    
    for (const entry of zipEntries) {
      if (entry.entryName === 'plugin.json') hasRootPluginJson = true;
      if (entry.isDirectory && entry.entryName.split('/').length === 2 && entry.entryName.includes('/')) {
         // Возможно, плагин запакован в папку
         if (!rootFolder) rootFolder = entry.entryName.split('/')[0];
      }
    }

    if (hasRootPluginJson) {
      // Файлы лежат в корне архива. Читаем plugin.json чтобы узнать имя
      const jsonEntry = zip.getEntry('plugin.json');
      if (jsonEntry) {
        const jsonStr = jsonEntry.getData().toString('utf8');
        try {
          const data = JSON.parse(jsonStr);
          if (data.name) pluginName = data.name;
        } catch(e) {}
      }
      
      const destPath = safeJoin(root, 'plugins', pluginName);
      await fs.mkdir(destPath, { recursive: true });
      zip.extractAllTo(destPath, true);
    } else if (rootFolder) {
       // Плагин запакован в папку
       pluginName = rootFolder;
       const destPath = safeJoin(root, 'plugins'); // Распаковываем прямо в plugins, папка создастся сама
       zip.extractAllTo(destPath, true);
    } else {
       // Фолбэк, просто распакуем в папку с именем файла
       const destPath = safeJoin(root, 'plugins', pluginName);
       await fs.mkdir(destPath, { recursive: true });
       zip.extractAllTo(destPath, true);
    }

    // Удаляем временный файл
    await fs.unlink(tempZipPath);

    return { success: true, name: pluginName };
  } catch (err) {
    return { success: false, error: err.message };
  }
});

ipcMain.handle('fs:mkdir', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const dir = safeJoin(root, relPath);
  await fs.mkdir(dir, { recursive: true });
});

ipcMain.handle('fs:unlink', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  await fs.unlink(file);
});

ipcMain.handle('fs:rmdir', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const dir = safeJoin(root, relPath);
  await fs.rm(dir, { recursive: true, force: true });
});

ipcMain.handle('fs:rename', async (_e, oldRelPath, newRelPath) => {
  const root = getWorkspaceRoot();
  const oldPath = safeJoin(root, oldRelPath);
  const newPath = safeJoin(root, newRelPath);
  await fs.rename(oldPath, newPath);
});

ipcMain.handle('fs:exists', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
});

ipcMain.handle('fs:copy', async (_e, srcRel, destRel) => {
  const root = getWorkspaceRoot();
  const srcPath = safeJoin(root, srcRel);
  const destPath = safeJoin(root, destRel);
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
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  shell.showItemInFolder(file);
});

ipcMain.handle('app:open-external', async (_e, url) => {
  shell.openExternal(url);
});

// Система обновлений и инициализации
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

  const log = (msg) => { event.sender.send('update-progress', msg); };

  log('Проверка структуры папок...');
  
  const autoUpdate = store.get('autoUpdate') !== false;
  if (!autoUpdate) {
    log('Автообновление отключено в настройках. Пропуск.');
    return;
  }

  const BASE_URL = 'http://85.239.59.203';
  log(`Подключение к серверу ${BASE_URL}...`);
  
  try {
    // 1. Проверяем версию мода
    const versionRes = await fetch(`${BASE_URL}/spraute_version.txt`).catch(() => null);
    if (versionRes && versionRes.ok) {
      const serverVersion = (await versionRes.text()).trim();
      const localVersionPath = path.join(sprautePath, 'version.txt');
      let localVersion = '';
      try { localVersion = (await fs.readFile(localVersionPath, 'utf-8')).trim(); } catch (e) {}

      if (serverVersion !== localVersion) {
        log(`Найдена версия мода: ${serverVersion}.`);
        
        let modNotes = 'Описание обновления недоступно.';
        try {
          const notesRes = await fetch(`${BASE_URL}/mod_release_notes.md`);
          if (notesRes.ok) {
            modNotes = await notesRes.text();
          }
        } catch (e) {}

        if (mainWindow) {
          mainWindow.webContents.send('mod-update-available', {
            version: serverVersion,
            notes: modNotes
          });
        }
      } else {
        log(`Мод актуален (версия ${localVersion}).`);
      }
    } else {
      log('Сервер недоступен, пропускаем обновление мода.');
    }

    // 2. Синхронизируем базовые ассеты и документацию напрямую из корня
    const syncFiles = [
      { url: 'metods.md', dest: path.join(sprautePath, 'metods.md'), type: 'text' },
      { url: 'defolt.geo.json', dest: path.join(geoPath, 'defolt.geo.json'), type: 'text' },
      { url: 'npc_classic.animation.json', dest: path.join(animPath, 'npc_classic.animation.json'), type: 'text' },
      { url: 'defolt.png', dest: path.join(texPath, 'defolt.png'), type: 'binary' }
    ];

    for (const file of syncFiles) {
      log(`Проверка: ${file.url}...`);
      try {
        const res = await fetch(`${BASE_URL}/${file.url}`);
        if (res.ok) {
          if (file.type === 'text') {
            const text = await res.text();
            await fs.writeFile(file.dest, text);
          } else {
            const buf = await res.arrayBuffer();
            await fs.writeFile(file.dest, Buffer.from(buf));
          }
        }
      } catch (e) {
        log(`Не удалось скачать ${file.url}`);
      }
    }
    log('Ассеты синхронизированы.');
    
  } catch (error) {
    log(`Ошибка: ${error.message}`);
  }

  log('Запуск Spraute Studio...');
  return { success: true };
});

ipcMain.handle('app:set-titlebar', (event, color, symbolColor) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win) {
    win.setTitleBarOverlay({
      color: color,
      symbolColor: symbolColor
    });
  }
});

ipcMain.handle('mod-update:download', async (event, serverVersion) => {
  try {
    const mcPath = store.get('minecraftPath');
    const modsPath = path.join(mcPath, 'mods');
    const sprautePath = path.join(mcPath, 'spraute_engine');
    const localVersionPath = path.join(sprautePath, 'version.txt');
    const BASE_URL = 'http://85.239.59.203';
    
    const modFileName = `spraute_engine-${serverVersion}.jar`;
    const modRes = await fetch(`${BASE_URL}/${modFileName}`);
    
    if (modRes.ok) {
      const mods = await fs.readdir(modsPath).catch(() => []);
      for (const m of mods) {
        if (m.toLowerCase().includes('spraute') && m.endsWith('.jar')) {
          await fs.unlink(path.join(modsPath, m)).catch(() => {});
        }
      }
      const dest = path.join(modsPath, modFileName);
      const buf = await modRes.arrayBuffer();
      await fs.writeFile(dest, Buffer.from(buf));
      await fs.writeFile(localVersionPath, serverVersion);
      return { success: true };
    } else {
      return { success: false, error: 'Файл мода не найден на сервере (404)' };
    }
  } catch (e) {
    return { success: false, error: e.message };
  }
});

ipcMain.handle('plugin:market-list', async () => {
  try {
    const res = await fetch(`http://85.239.59.203/plugins/market.json`);
    if (res.ok) {
      return await res.json();
    }
    return [];
  } catch (e) {
    return [];
  }
});

ipcMain.handle('plugin:market-download', async (event, pluginName, fileName) => {
  try {
    const root = getWorkspaceRoot();
    const pluginsDir = safeJoin(root, 'plugins');
    const AdmZip = require('adm-zip');
    
    const res = await fetch(`http://85.239.59.203/plugins/${fileName}`);
    if (!res.ok) throw new Error('Файл плагина не найден на сервере');
    
    const buf = Buffer.from(await res.arrayBuffer());
    const tempZip = safeJoin(pluginsDir, `_temp_${Date.now()}.zip`);
    await fs.writeFile(tempZip, buf);
    
    const zip = new AdmZip(tempZip);
    const destPath = safeJoin(pluginsDir, pluginName);
    
    await fs.rm(destPath, { recursive: true, force: true }).catch(() => {});
    await fs.mkdir(destPath, { recursive: true });
    
    const entries = zip.getEntries();
    let hasRootJson = false;
    let rootFolder = null;
    for (const e of entries) {
       if (e.entryName === 'plugin.json') hasRootJson = true;
       if (e.isDirectory && e.entryName.split('/').length === 2 && e.entryName.includes('/')) {
         if (!rootFolder) rootFolder = e.entryName.split('/')[0];
       }
    }
    
    if (hasRootJson) {
       zip.extractAllTo(destPath, true);
    } else if (rootFolder) {
       zip.extractAllTo(pluginsDir, true);
       if (rootFolder !== pluginName) {
           await fs.rename(safeJoin(pluginsDir, rootFolder), destPath);
       }
    } else {
       zip.extractAllTo(destPath, true);
    }
    
    await fs.unlink(tempZip).catch(()=>{});
    return { success: true };
  } catch (e) {
    return { success: false, error: e.message };
  }
});
