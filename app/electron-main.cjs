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
  
  if (!isDev) {
    autoUpdater.checkForUpdates().catch(err => {
      console.error('Ошибка проверки обновлений:', err);
    });
  }

  autoUpdater.on('update-available', (info) => {
    if (mainWindow) {
      mainWindow.webContents.send('studio-update-available', info);
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
  if (process.platform !== 'darwin') app.quit();
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

ipcMain.handle('app:show-in-explorer', async (_e, relPath) => {
  const root = getWorkspaceRoot();
  const file = safeJoin(root, relPath);
  shell.showItemInFolder(file);
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
        log(`Найдена версия мода: ${serverVersion}. Загрузка...`);
        // Теперь скачиваем файл с названием, включающим версию:
        const modFileName = `spraute_engine-${serverVersion}.jar`;
        const modRes = await fetch(`${BASE_URL}/${modFileName}`);
        
        if (modRes.ok) {
          // Удаляем старые версии мода
          const mods = await fs.readdir(modsPath).catch(() => []);
          for (const m of mods) {
            if (m.toLowerCase().includes('spraute') && m.endsWith('.jar')) {
              await fs.unlink(path.join(modsPath, m)).catch(() => {});
            }
          }
          // Сохраняем новый мод
          const dest = path.join(modsPath, modFileName);
          const buf = await modRes.arrayBuffer();
          await fs.writeFile(dest, Buffer.from(buf));
          
          await fs.writeFile(localVersionPath, serverVersion);
          log('Мод успешно обновлен!');
        } else {
          log(`Файл мода ${modFileName} не найден на сервере (404).`);
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
