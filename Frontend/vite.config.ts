import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],

  server: {
    proxy: {
      "/api": {
        target: "http://157.22.189.188:8082",
        changeOrigin: true,
      },

      "/auth-api": {
        target: "http://157.22.189.188:8082",
        changeOrigin: true,

        rewrite: (path) => path.replace(/^\/auth-api/, "/api/v1/sso-ident"),
      },

      "/booking-api": {
        target: "http://localhost:8080",
        changeOrigin: true,

        rewrite: (path) => path.replace(/^\/booking-api/, "/booking"),
      },
    },
  },
});
