import { loadAccounts, loginThroughUi } from "../support/commands.js";

describe("Real authentication and session authorization", () => {
  it("rejects invalid credentials from the live backend", () => {
    cy.visit("/login");
    cy.get("#organizationId").type("1");
    cy.get("#email").type("missing@atlasdesk.test");
    cy.get("#password").type("wrong-password");
    cy.contains("button", "Entrar").click();
    cy.get('[role="alert"]').should("contain.text", "inválidos");
  });

  it("shows a clear message when the login rate limit is reached", () => {
    cy.intercept("POST", "/api/auth/login", {
      statusCode: 429,
      headers: { "retry-after": "60" },
      body: { error: "too_many_attempts" },
    });
    cy.visit("/login");
    cy.get("#organizationId").type("1");
    cy.get("#email").type("demo@atlasdesk.test");
    cy.get("#password").type("wrong-password");
    cy.contains("button", "Entrar").click();
    cy.get('[role="alert"]').should("contain.text", "Muitas tentativas");
  });

  it("enters the real, isolated portfolio demo from the public login panel", () => {
    cy.seedPublicDemo();
    cy.visit("/login");
    cy.get(".public-demo").should("be.visible");
    cy.contains("section", "Conheça o AtlasDesk").should("be.visible");
    cy.contains("section", "demo.agent@atlasdesk.test").should("be.visible");
    cy.contains("section", "AtlasDeskDemo2026!").should("be.visible");
    cy.contains("button", "Usar conta demonstrativa").click();
    cy.get("#organizationId").should("have.value", "3");
    cy.get("#email").should("have.value", "demo.agent@atlasdesk.test");
    cy.get("#password").should("have.value", "AtlasDeskDemo2026!");
    cy.contains("button", "Entrar").click();
    cy.contains("h1", "Visão da organização").should("be.visible");

    cy.visit("/tickets");
    cy.contains("[DEMO-ATL-003] Revisar alerta de autenticação").should(
      "be.visible",
    );
    cy.visit("/assets");
    cy.contains("ATLAS-LT-001").should("be.visible");
    cy.request({ method: "GET", url: "/api/users", failOnStatusCode: false })
      .its("status")
      .should("eq", 403);
    cy.visit("/tickets/new");
    cy.get('input[name="title"]').type("Solicitar monitor para a equipe");
    cy.get('textarea[name="description"]').type(
      "Chamado de demonstração criado pela conta pública.",
    );
    cy.get('select[name="priority"]').select("MEDIUM");
    cy.get('select[name="assetId"]').select("1");
    cy.contains("button", "Abrir chamado").click();
    cy.contains("h1", "Solicitar monitor para a equipe").should("be.visible");
    cy.contains("button", "Sair").click();
    cy.location("pathname").should("eq", "/login");
  });

  it("keeps an authenticated session after reload and invalidates it on logout", () => {
    loadAccounts().then(({ organizationId, admin, password }) => {
      loginThroughUi(organizationId, admin, password, "/");
      cy.contains("h1", "Visão da organização").should("be.visible");
      cy.reload();
      cy.contains("Sessão ativa").should("be.visible");
      cy.contains("h1", "Visão da organização").should("be.visible");
      cy.contains("button", "Sair").click();
      cy.location("pathname").should("eq", "/login");
      cy.visit("/users");
      cy.location("pathname").should("eq", "/login");
    });
  });

  it("does not follow an external return path after login", () => {
    loadAccounts().then(({ organizationId, admin, password }) => {
      cy.visit("/login", {
        onBeforeLoad(window) {
          window.history.replaceState(
            {
              usr: { from: { pathname: "//attacker.example" } },
              key: "external-return-path",
              idx: 0,
            },
            "",
            "/login",
          );
        },
      });
      cy.get("#organizationId").type(String(organizationId));
      cy.get("#email").type(admin);
      cy.get("#password").type(password);
      cy.contains("button", "Entrar").click();
      cy.location("origin").should("eq", Cypress.config("baseUrl"));
      cy.location("pathname").should("eq", "/");
      cy.contains("h1", "Visão da organização").should("be.visible");
    });
  });

  it("returns unauthorized for protected API routes without a session", () => {
    cy.request({
      method: "GET",
      url: "/api/users",
      failOnStatusCode: false,
    }).its("status").should("eq", 401);
  });
});
