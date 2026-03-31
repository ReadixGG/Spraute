import { defineConfig } from 'vite';

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
  plugins: [stripCrossoriginForElectron()],
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
  },
});
