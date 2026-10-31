module.exports = {
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || "http://frontend:5173",
    specPattern: "cypress/e2e/**/*.cy.js",
    supportFile: false,
    video: false,
  },
  allowCypressEnv: false,
};
