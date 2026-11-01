import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    baseUrl: "http://e2e-frontend:5173",
    specPattern: "cypress/e2e/**/*.cy.ts",
    supportFile: "cypress/support/e2e.ts",
    screenshotsFolder: "artifacts/screenshots",
    videosFolder: "artifacts/videos",
    video: true,
    screenshotOnRunFailure: true,
  },
  reporter: "mocha-junit-reporter",
  reporterOptions: {
    mochaFile: "artifacts/reports/results-[hash].xml",
    toConsole: true,
  },
  allowCypressEnv: false,
});
