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
    cy.env([
      "ADMIN_EMAIL",
      "ADMIN_PASSWORD",
      "ADMIN_ORGANIZATION_ID",
      "E2E_USER_EMAIL",
      "E2E_ASSET_NUMBER",
      "E2E_TICKET_TITLE",
    ]).then(function (env) {
      const email = env.ADMIN_EMAIL as string | undefined;
      const password = env.ADMIN_PASSWORD as string | undefined;
      const organizationId = env.ADMIN_ORGANIZATION_ID as string | undefined;
      const e2eUserEmail = env.E2E_USER_EMAIL as string | undefined;
      const e2eAssetNumber = env.E2E_ASSET_NUMBER as string | undefined;
      const e2eTicketTitle = env.E2E_TICKET_TITLE as string | undefined;
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

      if (e2eUserEmail) {
        cy.contains("button", "Novo usuário").click();
        cy.get('input[name="name"]').type("ATL E2E User");
        cy.get('input[name="email"]').type(e2eUserEmail);
        cy.get('select[name="role"]').select("REQUESTER");
        cy.get('input[name="password"]').type("E2e-Test-Password-2026!");
        cy.contains("button", "Cadastrar").click();
        cy.get('[role="status"]').should("contain.text", "foi cadastrado");
        cy.contains("tr", e2eUserEmail).within(() => {
          cy.contains("button", "Editar").click();
        });
        cy.get('input[name="name"]').clear().type("ATL E2E User Updated");
        cy.contains("button", "Salvar alterações").click();
        cy.get('[role="status"]').should("contain.text", "foram salvas");
        cy.on("window:confirm", () => true);
        cy.contains("tr", e2eUserEmail).within(() => {
          cy.contains("button", "Desativar").click();
        });
        cy.get('[role="status"]').should("contain.text", "foi desativada");
        cy.contains("tr", e2eUserEmail).should("contain.text", "Inativo");
      }

      if (e2eAssetNumber) {
        cy.visit("/assets");
        cy.contains("h1", "Ativos").should("be.visible");
        cy.viewport(390, 844);
        cy.get('[aria-label="Navegação móvel"]').contains("Ativos").should("be.visible");
        cy.contains("button", "Novo ativo").click();
        cy.get('input[name="assetNumber"]').type(e2eAssetNumber);
        cy.get('input[name="name"]').type("ATL E2E Asset");
        cy.get('input[name="type"]').type("Notebook");
        cy.contains("button", "Cadastrar").click();
        cy.get('[role="status"]').should("contain.text", "foi cadastrado");
        cy.viewport(1280, 720);
        cy.contains("tr", e2eAssetNumber).within(() => {
          cy.contains("button", "Editar").click();
        });
        cy.get('input[name="name"]').clear().type("ATL E2E Asset Updated");
        cy.contains("button", "Salvar alterações").click();
        cy.get('[role="status"]').should("contain.text", "foram salvas");
        cy.on("window:confirm", () => true);
        cy.contains("tr", e2eAssetNumber).within(() => {
          cy.contains("button", "Inativar").click();
        });
        cy.get('[role="status"]').should("contain.text", "foi inativado");
        cy.get('[aria-label="Filtrar por estado"]').select("INACTIVE");
        cy.contains("tr", e2eAssetNumber).should("contain.text", "Inativo");
      }

      if (e2eTicketTitle) {
        cy.visit("/tickets/new");
        cy.contains("h1", "Novo chamado").should("be.visible");
        cy.get('input[name="title"]').type(e2eTicketTitle);
        cy.get('textarea[name="description"]').type("Solicitação criada pelo smoke E2E ATL-0012.");
        cy.get('select[name="priority"]').select("HIGH");
        cy.contains("button", "Abrir chamado").click();
        cy.contains("h1", e2eTicketTitle).should("be.visible");
        cy.contains("Solicitação criada pelo smoke E2E ATL-0012.").should("be.visible");
        cy.contains("Solicitante").parent().should("contain.text", "AtlasDesk Local Admin");
        cy.contains("Prioridade").parent().should("contain.text", "Alta");
        cy.visit("/tickets");
        cy.contains("a", e2eTicketTitle).should("be.visible").click();
        cy.location("pathname").should("match", /\/tickets\/\d+$/);
        cy.contains("h1", e2eTicketTitle).should("be.visible");
      }

      cy.contains("button", "Sair").click();
      cy.location("pathname").should("eq", "/login");
      cy.visit("/users");
      cy.location("pathname").should("eq", "/login");
    });
  });
});
