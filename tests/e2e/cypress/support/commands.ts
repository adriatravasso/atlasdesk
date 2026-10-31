type E2eAccount = {
  organizationId: number;
  otherOrganizationId: number;
  password: string;
  admin: string;
  agent: string;
  requester: string;
  otherAdmin: string;
};

declare global {
  namespace Cypress {
    interface Chainable {
      resetE2eData(): Chainable<number>;
    }
  }
}

Cypress.Commands.add("resetE2eData", () => {
  cy.request<{ headerName: string; token: string }>("GET", "/api/auth/csrf")
    .then(({ body }) =>
      cy.request({
        method: "POST",
        url: "/api/e2e/reset",
        headers: { [body.headerName]: body.token },
      }),
    )
    .its("status")
    .should("eq", 204);
});

export function loginThroughUi(
  organizationId: number,
  email: string,
  password: string,
  destination = "/",
) {
  cy.clearCookies();
  cy.visit("/login");
  cy.get("#organizationId").clear().type(String(organizationId));
  cy.get("#email").clear().type(email);
  cy.get("#password").clear().type(password);
  cy.contains("button", "Entrar").click();
  cy.location("pathname").should("eq", destination);
  cy.contains("Sessão ativa").should("be.visible");
}

export function loadAccounts(): Cypress.Chainable<E2eAccount> {
  return cy.fixture<E2eAccount>("accounts.json");
}
