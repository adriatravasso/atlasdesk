const e2ePassword = "E2e-Test-Password-2026!";

describe("Ticket handling", () => {
  it("lets the assigned agent resolve a ticket and its requester close it", function () {
    cy.env([
      "ADMIN_EMAIL",
      "ADMIN_PASSWORD",
      "ADMIN_ORGANIZATION_ID",
      "E2E_AGENT_EMAIL",
      "E2E_REQUESTER_EMAIL",
      "E2E_WORKFLOW_TICKET_TITLE",
    ]).then(function (env) {
      const adminEmail = env.ADMIN_EMAIL as string | undefined;
      const adminPassword = env.ADMIN_PASSWORD as string | undefined;
      const organizationId = env.ADMIN_ORGANIZATION_ID as string | undefined;
      const agentEmail = env.E2E_AGENT_EMAIL as string | undefined;
      const requesterEmail = env.E2E_REQUESTER_EMAIL as string | undefined;
      const title = env.E2E_WORKFLOW_TICKET_TITLE as string | undefined;
      if (
        !adminEmail ||
        !adminPassword ||
        !organizationId ||
        !agentEmail ||
        !requesterEmail ||
        !title
      ) {
        (this as unknown as Mocha.Context).skip();
        return;
      }

      login(organizationId, adminEmail, adminPassword);
      createUser("AGENT", agentEmail, "ATL E2E Agent");
      createUser("REQUESTER", requesterEmail, "ATL E2E Requester");

      cy.contains("button", "Sair").click();
      login(organizationId, requesterEmail, e2ePassword);
      cy.visit("/tickets/new");
      cy.get('input[name="title"]').type(title);
      cy.get('textarea[name="description"]').type(
        "Fluxo de atendimento criado pelo Cypress ATL-0014.",
      );
      cy.get('select[name="priority"]').select("MEDIUM");
      cy.contains("button", "Abrir chamado").click();
      cy.contains("h1", title).should("be.visible");
      cy.get('[aria-label="Status do chamado: Aberto"]').should("be.visible");
      cy.contains("button", "Assumir chamado").should("not.exist");
      cy.contains("button", "Resolver chamado").should("not.exist");
      cy.contains("button", "Encerrar chamado").should("not.exist");
      let ticketPath = "";
      cy.location("pathname").then((pathname) => {
        ticketPath = pathname;
      });
      cy.visit("/tickets");
      cy.contains("a", title).should("be.visible");

      cy.contains("button", "Sair").click();
      login(organizationId, agentEmail, e2ePassword);
      cy.viewport(390, 844);
      cy.then(() => cy.visit(ticketPath));
      cy.get('[aria-label="Status do chamado: Aberto"]').should("be.visible");
      cy.contains("button", "Assumir chamado").click();
      cy.contains("Responsável").parent().should("contain.text", "ATL E2E Agent");
      cy.intercept(
        { method: "POST", url: /\/api\/tickets\/\d+\/transition/, times: 1 },
        { statusCode: 409, body: { error: "conflict", detail: "stale state" } },
      ).as("staleTransition");
      cy.contains("button", "Iniciar atendimento").click();
      cy.wait("@staleTransition");
      cy.get('[role="alert"]').should(
        "contain.text",
        "O chamado mudou enquanto você o atendia.",
      );
      cy.get('[aria-label="Status do chamado: Aberto"]').should("be.visible");
      cy.contains("button", "Iniciar atendimento").click();
      cy.get('[aria-label="Status do chamado: Em andamento"]').should(
        "be.visible",
      );
      cy.contains("button", "Resolver chamado").click();
      cy.get('[aria-label="Status do chamado: Resolvido"]').should("be.visible");
      cy.contains("button", "Encerrar chamado").should("not.exist");
      cy.contains("Chamado resolvido").should("be.visible");
      cy.contains("button", "Sair").click();

      login(organizationId, requesterEmail, e2ePassword);
      cy.then(() => cy.visit(ticketPath));
      cy.get('[aria-label="Status do chamado: Resolvido"]').should("be.visible");
      cy.on("window:confirm", () => true);
      cy.contains("button", "Encerrar chamado").click();
      cy.get('[aria-label="Status do chamado: Fechado"]').should("be.visible");
      cy.contains("Chamado encerrado").should("be.visible");
      cy.contains("button", "Iniciar atendimento").should("not.exist");
      cy.contains("button", "Resolver chamado").should("not.exist");
      cy.get('[aria-label="Linha do tempo do chamado"]')
        .should("contain.text", "Chamado criado")
        .and("contain.text", "Chamado resolvido")
        .and("contain.text", "Chamado encerrado");
    });
  });
});

function login(organizationId: string, email: string, password: string) {
  cy.visit("/login");
  cy.get("#organizationId").clear().type(organizationId);
  cy.get("#email").clear().type(email);
  cy.get("#password").clear().type(password);
  cy.contains("button", "Entrar").click();
  cy.contains("Sessão ativa").should("be.visible");
}

function createUser(role: "ADMIN" | "AGENT" | "REQUESTER", email: string, name: string) {
  cy.visit("/users");
  cy.contains("button", "Novo usuário").click();
  cy.get('input[name="name"]').type(name);
  cy.get('input[name="email"]').type(email);
  cy.get('select[name="role"]').select(role);
  cy.get('input[name="password"]').type(e2ePassword);
  cy.contains("button", "Cadastrar").click();
  cy.get('[role="status"]').should("contain.text", "foi cadastrado");
}
