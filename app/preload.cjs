const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('spraute', {
  selectMinecraftFolder: () => ipcRenderer.invoke('dialog:select-minecraft-folder'),
  selectImageFile: () => ipcRenderer.invoke('dialog:select-image-file'),
  storeGet: (key) => ipcRenderer.invoke('store:get', key),
  storeSet: (key, value) => ipcRenderer.invoke('store:set', key, value),
  listDir: (relPath) => ipcRenderer.invoke('fs:list', relPath),
  readFile: (relPath, encoding) => ipcRenderer.invoke('fs:read', relPath, encoding),
  writeFile: (relPath, content) => ipcRenderer.invoke('fs:write', relPath, content),
  mkdir: (relPath) => ipcRenderer.invoke('fs:mkdir', relPath),
  unlink: (relPath) => ipcRenderer.invoke('fs:unlink', relPath),
  rmdir: (relPath) => ipcRenderer.invoke('fs:rmdir', relPath),
  rename: (oldRelPath, newRelPath) => ipcRenderer.invoke('fs:rename', oldRelPath, newRelPath),
  exists: (relPath) => ipcRenderer.invoke('fs:exists', relPath),
  copy: (srcRel, destRel) => ipcRenderer.invoke('fs:copy', srcRel, destRel),
  showInExplorer: (relPath) => ipcRenderer.invoke('app:show-in-explorer', relPath),
  initWorkspace: (mcPath) => ipcRenderer.invoke('app:init-workspace', mcPath),
  onUpdateProgress: (callback) => {
    ipcRenderer.removeAllListeners('update-progress');
    ipcRenderer.on('update-progress', (_e, msg) => callback(msg));
  },
  onStudioUpdateAvailable: (callback) => {
    ipcRenderer.removeAllListeners('studio-update-available');
    ipcRenderer.on('studio-update-available', (_e, info) => callback(info));
  },
  onStudioUpdateDownloaded: (callback) => {
    ipcRenderer.removeAllListeners('studio-update-downloaded');
    ipcRenderer.on('studio-update-downloaded', (_e) => callback());
  },
  onStudioUpdateProgress: (callback) => {
    ipcRenderer.removeAllListeners('studio-update-dl-progress');
    ipcRenderer.on('studio-update-dl-progress', (_e, progressObj) => callback(progressObj));
  },
  downloadStudioUpdate: () => ipcRenderer.invoke('studio-update:download'),
  installStudioUpdate: () => ipcRenderer.invoke('studio-update:install'),
  setTitleBarColors: (color, symbolColor) => ipcRenderer.invoke('app:set-titlebar', color, symbolColor)
});
