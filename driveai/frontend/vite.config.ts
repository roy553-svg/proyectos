import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Presupuesto de hardware: la radio china debe cargar esto en < 2 s.
 * - Un solo chunk (menos round-trips en 3G/almacenamiento lento).
 * - Sin sourcemaps ni polyfills modernos innecesarios.
 * - target es2019: compatible con el WebView de Android 9 (Chromium 66+).
 */
export default defineConfig({
  plugins: [react()],
  build: {
    target: 'es2019',
    sourcemap: false,
    cssCodeSplit: false,
    reportCompressedSize: false,
    chunkSizeWarningLimit: 300,
    rollupOptions: { output: { manualChunks: undefined } },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
});
