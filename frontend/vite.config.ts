import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import packageMetadata from "./package.json";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "VITE_");

  return {
    plugins: [react()],
    define: {
      __APP_VERSION__: JSON.stringify(packageMetadata.version),
    },
    server: {
      host: "0.0.0.0",
      allowedHosts: ["frontend", "e2e-frontend"],
      proxy: {
        "/api": env.VITE_API_TARGET || "http://localhost:8080",
      },
    },
  };
});
