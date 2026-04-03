const { app, BrowserWindow } = require('electron');
const path = require('path');
const fs = require('fs');

app.whenReady().then(() => {
  const win = new BrowserWindow({
    show: false,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  win.webContents.on('console-message', (event, level, message, line, sourceId) => {
    fs.writeFileSync('electron_error_log.txt', `[LOG]: ${message} (${sourceId}:${line})\n`, { flag: 'a' });
  });

  win.webContents.on('did-fail-load', (e, code, desc) => {
    fs.writeFileSync('electron_error_log.txt', `[FAIL]: ${desc}\n`, { flag: 'a' });
  });

  win.loadFile(path.join(__dirname, 'index.html'));

  setTimeout(() => {
    app.quit();
  }, 3000);
});
