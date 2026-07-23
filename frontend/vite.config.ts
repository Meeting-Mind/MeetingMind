import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const bffProxyTarget = process.env.VITE_BFF_PROXY_TARGET?.trim() || "http://127.0.0.1:8081";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: bffProxyTarget,
        changeOrigin: true
      }
    }
  }
});
