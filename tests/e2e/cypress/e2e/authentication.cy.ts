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
