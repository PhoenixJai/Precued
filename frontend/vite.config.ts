import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,        // bind 0.0.0.0, not just loopback — lets coworkers reach :5173 at all
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",   // fine as-is — this runs on YOUR machine, proxying to YOUR backend
        changeOrigin: true,
      },
    },
  },
});