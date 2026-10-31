import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    baseUrl: "http://e2e-frontend:5173",
    specPattern: "cypress/e2e/**/*.cy.ts",
    supportFile: "cypress/support/e2e.ts",
    video: false,
    screenshotOnRunFailure: true,
  },
  allowCypressEnv: false,
});
