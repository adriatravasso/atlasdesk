import { loadAccounts, loginThroughUi } from "../support/commands.js";

describe("Operational dashboard", () => {
  const metrics = {
    totalTickets: 12,
    openTickets: 4,
    inProgressTickets: 3,
    pendingApprovalTickets: 2,
    resolvedTickets: 2,
    closedTickets: 1,
    overdueTickets: 3,
    priorityDistribution: { low: 5, medium: 4, high: 3 },
    activeAssets: 7,
  };

  it("shows requester scoped indicators and responsive priority bars", () => {
    cy.viewport(1280, 900);
    cy.intercept("GET", "/api/auth/me", {
      statusCode: 200,
      body: {
        id: 1,
        organizationId: 41,
        name: "E2E REQUESTER",
        email: "requester@example.test",
        role: "REQUESTER",
      },
    });
    cy.intercept("GET", "/api/dashboard", {
      statusCode: 200,
      body: metrics,
    }).as("dashboard");
    cy.intercept({ method: "GET", pathname: "/api/tickets" }, {
      statusCode: 200,
      body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });

    cy.visit("/");
    cy.wait("@dashboard");
    cy.contains("h1", "Seus chamados").should("be.visible");
    cy.contains("Os indicadores de chamados mostram apenas suas solicitações.")
      .should("be.visible");
    cy.contains(".dashboard-card", "Total de chamados")
      .should("contain.text", "12")
      .find("a")
      .should("have.attr", "href", "/tickets");
    cy.contains(".dashboard-card", "Atrasados")
      .should("contain.text", "3")
      .find("a")
      .should("not.exist");
    cy.get('[role="progressbar"]').should("have.length", 3);
    cy.get('[aria-label="Prioridade alta"]').should(
      "have.attr",
      "aria-valuenow",
      "3",
    );
    cy.contains(".dashboard-card a", "Abertos").click();
    cy.location("pathname").should("eq", "/tickets");
    cy.location("search").should("eq", "?status=OPEN");
    cy.get('[aria-label="Filtrar chamados por status"]').should(
      "have.value",
      "OPEN",
    );

    cy.visit("/");
    cy.viewport(390, 844);
    cy.contains("h1", "Seus chamados").should("be.visible");
    cy.get(".dashboard-metrics").should("be.visible");
    cy.document()
      .its("documentElement.scrollWidth")
      .should("be.lte", 390);
  });

  it("shows an empty state and allows retry after an API error", () => {
    cy.intercept("GET", "/api/auth/me", {
      statusCode: 200,
      body: {
        id: 3,
        organizationId: 41,
        name: "E2E ADMIN",
        email: "admin@example.test",
        role: "ADMIN",
      },
    });
    cy.intercept("GET", "/api/dashboard", {
      statusCode: 503,
      body: { detail: "Dashboard unavailable" },
    }).as("dashboardFailure");

    cy.visit("/");
    cy.wait("@dashboardFailure");
    cy.get('[role="alert"]')
      .should("contain.text", "Dashboard unavailable")
      .and("contain.text", "Tentar novamente");

    cy.intercept("GET", "/api/dashboard", {
      statusCode: 200,
      body: {
        totalTickets: 0,
        openTickets: 0,
        inProgressTickets: 0,
        pendingApprovalTickets: 0,
        resolvedTickets: 0,
        closedTickets: 0,
        overdueTickets: 0,
        priorityDistribution: { low: 0, medium: 0, high: 0 },
        activeAssets: 0,
      },
    }).as("dashboardRetry");
    cy.contains("button", "Tentar novamente").click();
    cy.wait("@dashboardRetry");
    cy.contains("Ainda não há chamados para exibir nesta visão.").should(
      "be.visible",
    );
    cy.contains(".dashboard-card", "Total de chamados").should(
      "contain.text",
      "0",
    );
    cy.contains("A distribuição aparecerá quando houver chamados.").should(
      "be.visible",
    );
  });

  it("renders coherent metrics from the authenticated backend", () => {
    loadAccounts().then(({ organizationId, admin, password }) => {
      loginThroughUi(organizationId, admin, password, "/");
      cy.intercept("GET", "/api/dashboard").as("liveDashboard");
      cy.visit("/");
      cy.wait("@liveDashboard").then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
        const result = response?.body as typeof metrics;
        expect(result.totalTickets).to.equal(
          result.openTickets + result.inProgressTickets + result.pendingApprovalTickets +
            result.resolvedTickets + result.closedTickets,
        );
        expect(result.totalTickets).to.equal(
          result.priorityDistribution.low + result.priorityDistribution.medium +
            result.priorityDistribution.high,
        );
        expect(result.activeAssets).to.be.at.least(0);
      });
      cy.contains("h1", "Visão da organização").should("be.visible");
    });
  });
});
