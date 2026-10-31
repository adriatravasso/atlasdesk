import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    baseUrl: "http://frontend:5173",
    specPattern: "cypress/e2e/**/*.cy.ts",
    supportFile: false,
    video: false,
  },
  allowCypressEnv: false,
});
