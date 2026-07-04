import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const pkg = JSON.parse(readFileSync(join(__dirname, 'package.json'), 'utf8'));
/** Убирает crossorigin у script/link — иначе ES-модули с file:// в Electron часто не грузятся */
function stripCrossoriginForElectron() {
  return {
    name: 'strip-crossorigin-electron',
    transformIndexHtml(html) {
      return html.replace(/\s+crossorigin(?:="anonymous")?/gi, '');
    },
  };
}

export default defineConfig({
  base: './',
  root: '.',
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  plugins: [stripCrossoriginForElectron()],
  optimizeDeps: {
    entries: ['./index.html', './code.html'],
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // ES-модули с file:// в Electron: не вставлять modulepreload / лишние crossorigin
    modulePreload: false,
    target: 'esnext',
  },
  server: {
    port: 5173,
    strictPort: true,
    host: '127.0.0.1',
  },
});
