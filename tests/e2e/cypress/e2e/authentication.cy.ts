describe("AtlasDesk authentication", () => {
  it("rejects invalid credentials with clear feedback", () => {
    cy.visit("/login");
    cy.get("#organizationId").type("1");
    cy.get("#email").type("missing@localhost.test");
    cy.get("#password").type("invalid-password");
    cy.contains("button", "Entrar").click();
    cy.get('[role="alert"]').should("contain.text", "inválidos");
  });

  it("keeps a real session after reload and logs out through the API", () => {
    cy.env(["ADMIN_EMAIL", "ADMIN_PASSWORD", "ADMIN_ORGANIZATION_ID"]).then(
      function (env) {
        const email = env.ADMIN_EMAIL as string | undefined;
        const password = env.ADMIN_PASSWORD as string | undefined;
        const organizationId = env.ADMIN_ORGANIZATION_ID as string | undefined;
        if (!email || !password || !organizationId) {
          (this as unknown as Mocha.Context).skip();
          return;
        }

        cy.visit("/users");
        cy.location("pathname").should("eq", "/login");
        cy.get("#organizationId").type(organizationId);
        cy.get("#email").type(email);
        cy.get("#password").type(password);
        cy.contains("button", "Entrar").click();

        cy.location("pathname").should("eq", "/users");
        cy.contains("h1", "Usuários").should("be.visible");
        cy.contains("Sessão ativa").should("be.visible");
        cy.contains("button", "Sair").should("be.visible");
        cy.reload();
        cy.contains("Sessão ativa").should("be.visible");
        cy.contains("h1", "Usuários").should("be.visible");
        cy.contains("button", "Sair").click();
        cy.location("pathname").should("eq", "/login");
        cy.visit("/users");
        cy.location("pathname").should("eq", "/login");
      },
    );
  });
});
