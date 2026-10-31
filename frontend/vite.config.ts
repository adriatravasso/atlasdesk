import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "VITE_");

  return {
    plugins: [react()],
    server: {
      host: "0.0.0.0",
      allowedHosts: ["frontend"],
      proxy: {
        "/api": env.VITE_API_TARGET || "http://localhost:8080",
      },
    },
  };
});
