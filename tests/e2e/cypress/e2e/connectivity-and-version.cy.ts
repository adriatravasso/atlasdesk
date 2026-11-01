import { loadAccounts, loginThroughUi } from "../support/commands.js";

const demoNotice =
  "Esta é uma versão demonstrativa gratuita do AtlasDesk. O servidor pode levar cerca de um minuto para iniciar após um período de inatividade.";

describe("demo startup and application version", () => {
  it("shows the package version in the persistent desktop and mobile navigation", () => {
    cy.viewport(1280, 900);
    cy.resetE2eData();
    loadAccounts().then(({ organizationId, admin, password }) => {
      loginThroughUi(organizationId, admin, password);
      cy.get(".sidebar-foot .app-version")
        .should("be.visible")
        .and("have.text", "AtlasDesk · v1.0.0");
      cy.viewport(375, 812);
      cy.get(".mobile-brand .app-version")
        .should("be.visible")
        .and("have.text", "v1.0.0");
    });
  });

  it("keeps login accessible while the backend starts and recovers automatically", () => {
    cy.intercept("GET", "/api/auth/me", { statusCode: 401 }).as("session");
    cy.intercept("GET", "/api/health", { statusCode: 200 }).as(
      "health-ready",
    );
    cy.intercept(
      { method: "GET", url: "/api/health", times: 2 },
      { statusCode: 503 },
    ).as("health-unavailable");

    cy.visit("/login");
    cy.wait("@health-unavailable");
    cy.wait("@health-unavailable");
    cy.contains(demoNotice).should("be.visible");
    cy.get("#email").should("be.visible");
    cy.get('button[type="submit"]')
      .should("be.disabled")
      .and("contain.text", "Aguardando servidor");
    cy.get(".demo-status-row").should(
      "contain.text",
      "Servidor inicializando",
    );

    cy.wait("@health-ready", { timeout: 15_000 });
    cy.get(".demo-status-row").should("contain.text", "Sistema pronto");
    cy.get('button[type="submit"]').should("be.enabled");
  });

  it("distinguishes a persistent outage and lets the visitor retry", () => {
    cy.intercept("GET", "/api/auth/me", { statusCode: 401 });
    cy.intercept("GET", "/api/health", { statusCode: 503 }).as("health");

    cy.visit("/login");
    cy.wait("@health");
    cy.get(".demo-status-row").should("contain.text", "Servidor inicializando");
    cy.get("#email").should("be.visible");
    cy.contains("button", "Tentar novamente").click();
    cy.wait("@health");
    cy.get(".demo-status-row").should(
      "contain.text",
      "Servidor inicializando",
    );
  });

  it("stops session verification after a request timeout without hiding the login", () => {
    cy.clock();
    cy.intercept("GET", "/api/health", { statusCode: 200 }).as("health");
    cy.intercept("GET", "/api/auth/me", {
      delay: 10_000,
      statusCode: 200,
      body: { id: 1, organizationId: 1, name: "Demo", email: "demo@example.test", role: "ADMIN" },
    }).as("session");

    cy.visit("/login");
    cy.wait("@health");
    cy.tick(8_000);
    cy.get("#email").should("be.visible");
    cy.get('button[type="submit"]').should("be.visible");
    cy.contains("Não foi possível verificar sua sessão").should("be.visible");
  });
});
