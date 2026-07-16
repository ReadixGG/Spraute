const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('spraute', {
  selectMinecraftFolder: () => ipcRenderer.invoke('dialog:select-minecraft-folder'),
  selectImageFile: () => ipcRenderer.invoke('dialog:select-image-file'),
  storeGet: (key) => ipcRenderer.invoke('store:get', key),
  storeSet: (key, value) => ipcRenderer.invoke('store:set', key, value),
  listDir: (relPath) => ipcRenderer.invoke('fs:list', relPath),
  stat: (relPath) => ipcRenderer.invoke('fs:stat', relPath),
  isFile: async (relPath) => {
    const st = await ipcRenderer.invoke('fs:stat', relPath);
    return !!st?.isFile;
  },
  readFile: async (relPath, encoding) => {
    const st = await ipcRenderer.invoke('fs:stat', relPath);
    if (!st?.isFile) throw new Error('ENOENT');
    return ipcRenderer.invoke('fs:read', relPath, encoding);
  },
  writeFile: (relPath, content) => ipcRenderer.invoke('fs:write', relPath, content),
  writeBase64: (relPath, base64) => ipcRenderer.invoke('fs:writeBase64', relPath, base64),
  getPluginsStoragePath: () => ipcRenderer.invoke('plugin:getStoragePath'),
  exportPluginZip: (pluginName) => ipcRenderer.invoke('plugin:export', pluginName),
  listExportMods: () => ipcRenderer.invoke('export:listMods'),
  listExportDir: (area, relPath) => ipcRenderer.invoke('export:listDir', area, relPath),
  createMapExport: (options) => ipcRenderer.invoke('export:createMapZip', options),
  importPluginZip: (base64Data, filename, overwrite) => ipcRenderer.invoke('plugin:import', base64Data, filename, overwrite),
  importPluginDialog: async () => {
    try {
      return await ipcRenderer.invoke('plugin:importDialog');
    } catch (err) {
      if (String(err?.message || err).includes('No handler registered')) {
        return { success: false, fallback: true };
      }
      throw err;
    }
  },
  mkdir: (relPath) => ipcRenderer.invoke('fs:mkdir', relPath),
  unlink: (relPath) => ipcRenderer.invoke('fs:unlink', relPath),
  rmdir: (relPath) => ipcRenderer.invoke('fs:rmdir', relPath),
  rename: (oldRelPath, newRelPath) => ipcRenderer.invoke('fs:rename', oldRelPath, newRelPath),
  exists: (relPath) => ipcRenderer.invoke('fs:exists', relPath),
  copy: (srcRel, destRel) => ipcRenderer.invoke('fs:copy', srcRel, destRel),
  search: (query) => ipcRenderer.invoke('fs:search', query),
  showInExplorer: (relPath) => ipcRenderer.invoke('app:show-in-explorer', relPath),
  initWorkspace: (mcPath) => ipcRenderer.invoke('app:init-workspace', mcPath),
  onUpdateProgress: (callback) => {
    ipcRenderer.removeAllListeners('update-progress');
    ipcRenderer.on('update-progress', (_e, msg) => callback(msg));
  },
  onAssetsChanged: (callback) => {
    const handler = () => callback();
    ipcRenderer.removeAllListeners('assets-changed');
    ipcRenderer.removeAllListeners('workspace-changed');
    ipcRenderer.on('assets-changed', handler);
    ipcRenderer.on('workspace-changed', handler);
  },

  openExternal: (url) => ipcRenderer.invoke('app:open-external', url),

  setTitleBarColors: (color, symbolColor) => ipcRenderer.invoke('app:set-titlebar', color, symbolColor)
});
