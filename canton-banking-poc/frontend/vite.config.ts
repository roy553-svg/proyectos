import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // Proxy hacia la API JSON de cada nodo participante, para que el modo
      // "red en vivo" funcione sin tropezar con CORS.
      "/api/alfa":    { target: "http://localhost:5013", changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/alfa/, "") },
      "/api/beta":    { target: "http://localhost:5023", changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/beta/, "") },
      "/api/gamma":   { target: "http://localhost:5033", changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/gamma/, "") },
      "/api/central": { target: "http://localhost:5043", changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/central/, "") },
    },
  },
});
