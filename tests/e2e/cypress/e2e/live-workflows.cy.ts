import { loadAccounts, loginThroughUi } from "../support/commands.js";

describe("Live organization administration and asset management", () => {
  it("creates, edits and deactivates a user through the real API-backed page", () => {
    loadAccounts().then(({ organizationId, admin, password }) => {
      const email = `e2e-${Date.now()}@atlasdesk.test`;
      loginThroughUi(organizationId, admin, password, "/");
      cy.visit("/users");
      cy.contains("h1", "Usuários").should("be.visible");
      cy.contains("button", "Novo usuário").click();
      cy.get('input[name="name"]').type("Cypress User");
      cy.get('input[name="email"]').type(email);
      cy.get('select[name="role"]').select("REQUESTER");
      cy.get('input[name="password"]').type(password);
      cy.contains("button", "Cadastrar").click();
      cy.get('[role="status"]').should("contain.text", "foi cadastrado");
      cy.contains("tr", email).within(() => cy.contains("button", "Editar").click());
      cy.get('input[name="name"]').clear().type("Cypress User Updated");
      cy.contains("button", "Salvar alterações").click();
      cy.get('[role="status"]').should("contain.text", "foram salvas");
      cy.on("window:confirm", () => true);
      cy.contains("tr", email).within(() => cy.contains("button", "Desativar").click());
      cy.get('[role="status"]').should("contain.text", "foi desativada");
      cy.contains("tr", email).should("contain.text", "Inativo");
    });
  });

  it("enforces administrator permissions and keeps assets isolated by organization", () => {
    loadAccounts().then(({ organizationId, admin, agent, otherOrganizationId, otherAdmin, password }) => {
      loginThroughUi(organizationId, admin, password, "/");
      cy.visit("/assets");
      cy.contains("button", "Novo ativo").click();
      cy.get('input[name="assetNumber"]').type("E2E-ASSET-001");
      cy.get('input[name="name"]').type("Cypress Laptop");
      cy.get('input[name="type"]').type("Notebook");
      cy.contains("button", "Cadastrar").click();
      cy.get('[role="status"]').should("contain.text", "foi cadastrado");
      cy.contains("tr", "E2E-ASSET-001").should("contain.text", "Cypress Laptop");

      loginThroughUi(organizationId, agent, password, "/");
      cy.request({ method: "GET", url: "/api/users", failOnStatusCode: false })
        .its("status").should("eq", 403);

      loginThroughUi(otherOrganizationId, otherAdmin, password, "/");
      cy.request<{ content: Array<{ assetNumber: string }> }>("GET", "/api/assets")
        .its("body.content").should("have.length", 0);
      cy.visit("/assets");
      cy.contains("E2E-ASSET-001").should("not.exist");
    });
  });
});

describe("Live ticket workflow, SLA, approval, history and dashboard", () => {
  it("opens a ticket, follows the approval workflow, and reflects backend metrics", () => {
    loadAccounts().then(({ organizationId, admin, agent, requester, password }) => {
      const title = `E2E Approval ${Date.now()}`;
      loginThroughUi(organizationId, requester, password, "/");
      cy.visit("/tickets/new");
      cy.get('input[name="title"]').type(title);
      cy.get('textarea[name="description"]').type("Live Cypress acceptance workflow.");
      cy.get('select[name="priority"]').select("HIGH");
      cy.contains("button", "Abrir chamado").click();
      cy.contains("h1", title).should("be.visible");
      cy.location("pathname").then((path) => {
        const ticketPath = path;
        const ticketId = path.split("/").pop();
        expect(ticketId).to.match(/^\d+$/);

        cy.request<{ createdAt: string; dueAt: string; status: string }>("GET", `/api/tickets/${ticketId}`)
          .then(({ body }) => {
            expect(body.status).to.equal("OPEN");
            const hours = (Date.parse(body.dueAt) - Date.parse(body.createdAt)) / 3_600_000;
            expect(hours).to.be.within(3.99, 4.01);
          });

        loginThroughUi(organizationId, agent, password, "/");
        cy.visit(ticketPath);
        cy.contains("button", "Assumir chamado").click();
        cy.contains("button", "Iniciar atendimento").click();
        cy.get('[aria-label="Status do chamado: Em andamento"]').should("be.visible");
        cy.contains("button", "Resolver chamado").click();
        cy.get('[aria-label="Status do chamado: Aguardando aprovação"]').should("be.visible");
        cy.request<Array<{ eventType: string }>>("GET", `/api/tickets/${ticketId}/history`)
          .then(({ body }) => {
            expect(body.map((event) => event.eventType)).to.include("RESOLUTION_REQUESTED");
          });

        loginThroughUi(organizationId, admin, password, "/");
        cy.visit(ticketPath);
        cy.contains("button", "Aprovar resolução").click();
        cy.get('[aria-label="Status do chamado: Resolvido"]').should("be.visible");

        loginThroughUi(organizationId, requester, password, "/");
        cy.visit(ticketPath);
        cy.contains("button", "Encerrar chamado").click();
        cy.get('[aria-label="Status do chamado: Fechado"]').should("be.visible");
        cy.get('[aria-label="Linha do tempo do chamado"]')
          .should("contain.text", "Chamado criado")
          .and("contain.text", "Resolução aprovada")
          .and("contain.text", "Chamado encerrado");

        loginThroughUi(organizationId, admin, password, "/");
        cy.intercept("GET", "/api/dashboard").as("dashboardMetrics");
        cy.visit("/");
        cy.wait("@dashboardMetrics").its("response.body").should("include", {
          totalTickets: 1,
          closedTickets: 1,
          activeAssets: 0,
        });
        cy.contains(".dashboard-card", "Total de chamados").should("contain.text", "1");
      });
    });
  });

  it("does not expose another organization's ticket or dashboard data", () => {
    loadAccounts().then(({ organizationId, requester, otherOrganizationId, otherAdmin, password }) => {
      const title = `E2E Tenant Isolation ${Date.now()}`;
      loginThroughUi(organizationId, requester, password, "/");
      cy.visit("/tickets/new");
      cy.get('input[name="title"]').type(title);
      cy.get('textarea[name="description"]').type("Organization isolation check.");
      cy.get('select[name="priority"]').select("MEDIUM");
      cy.contains("button", "Abrir chamado").click();
      cy.location("pathname")
        .should("match", /^\/tickets\/\d+$/)
        .then((path) => {
          const ticketId = path.split("/").pop();
          loginThroughUi(otherOrganizationId, otherAdmin, password, "/");
          cy.request({
            method: "GET",
            url: `/api/tickets/${ticketId}`,
            failOnStatusCode: false,
          })
            .its("status")
            .should("eq", 404);
          cy.request<{ totalTickets: number }>("GET", "/api/dashboard")
            .its("body.totalTickets")
            .should("eq", 0);
          cy.visit("/tickets");
          cy.contains(title).should("not.exist");
        });
    });
  });
});
