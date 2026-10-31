type TicketStatus = "IN_PROGRESS" | "PENDING_APPROVAL" | "RESOLVED";
type UserRole = "ADMIN" | "AGENT" | "REQUESTER";

describe("Optional resolution approval", () => {
  it("shows the pending state after an assigned agent requests resolution", () => {
    openTicketAs("AGENT", 301, "IN_PROGRESS", 2);
    cy.intercept("GET", "/api/auth/csrf", {
      statusCode: 200,
      body: { headerName: "X-XSRF-TOKEN", token: "test-token" },
    });
    cy.intercept("POST", "/api/tickets/301/transition", {
      delay: 250,
      statusCode: 200,
      body: ticket(301, "PENDING_APPROVAL", 2),
    }).as("requestResolution");

    cy.contains("button", "Resolver chamado").click();
    cy.contains("button", "Resolvendo…").should("be.disabled");
    cy.wait("@requestResolution")
      .its("request.body")
      .should("deep.equal", { command: "RESOLVE" });
    cy.get('[aria-label="Status do chamado: Aguardando aprovação"]').should(
      "be.visible",
    );
    cy.get('[role="status"]').should(
      "contain.text",
      "Resolução enviada para aprovação.",
    );
  });

  it("lets an administrator approve a pending resolution on mobile", () => {
    cy.viewport(390, 844);
    openTicketAs("ADMIN", 302, "PENDING_APPROVAL", 2);
    cy.intercept("GET", "/api/auth/csrf", {
      statusCode: 200,
      body: { headerName: "X-XSRF-TOKEN", token: "test-token" },
    });
    cy.intercept("POST", "/api/tickets/302/transition", {
      delay: 250,
      statusCode: 200,
      body: ticket(302, "RESOLVED", 2),
    }).as("approveResolution");

    cy.contains("button", "Aprovar resolução").should("be.visible").click();
    cy.contains("button", "Aprovando…").should("be.disabled");
    cy.wait("@approveResolution")
      .its("request.body")
      .should("deep.equal", { command: "APPROVE" });
    cy.get('[aria-label="Status do chamado: Resolvido"]').should("be.visible");
    cy.get('[role="status"]').should("contain.text", "Resolução aprovada.");
  });

  it("lets an administrator reject a pending resolution", () => {
    openTicketAs("ADMIN", 303, "PENDING_APPROVAL", 2);
    cy.intercept("GET", "/api/auth/csrf", {
      statusCode: 200,
      body: { headerName: "X-XSRF-TOKEN", token: "test-token" },
    });
    cy.intercept("POST", "/api/tickets/303/transition", {
      statusCode: 200,
      body: ticket(303, "IN_PROGRESS", 2),
    }).as("rejectResolution");

    cy.contains("button", "Rejeitar resolução").should("be.visible").click();
    cy.wait("@rejectResolution")
      .its("request.body")
      .should("deep.equal", { command: "REJECT" });
    cy.get('[aria-label="Status do chamado: Em andamento"]').should(
      "be.visible",
    );
    cy.get('[role="status"]').should(
      "contain.text",
      "Resolução rejeitada; o chamado voltou para atendimento.",
    );
  });

  it("shows the real pending status without approval actions to non-admin users", () => {
    openTicketAs("AGENT", 304, "PENDING_APPROVAL", 2);
    cy.get('[aria-label="Status do chamado: Aguardando aprovação"]').should(
      "be.visible",
    );
    cy.contains("button", "Aprovar resolução").should("not.exist");
    cy.contains("button", "Rejeitar resolução").should("not.exist");

    openTicketAs("REQUESTER", 305, "PENDING_APPROVAL", 2);
    cy.get('[aria-label="Status do chamado: Aguardando aprovação"]').should(
      "be.visible",
    );
    cy.contains("button", "Aprovar resolução").should("not.exist");
    cy.contains("button", "Rejeitar resolução").should("not.exist");
  });

  it("keeps pending tickets visible and filterable in the list", () => {
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
    cy.intercept("GET", "/api/tickets*", (request) => {
      request.reply({
        statusCode: 200,
        body: {
          content: [ticket(306, "PENDING_APPROVAL", 2)],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });
    }).as("ticketList");

    cy.visit("/tickets");
    cy.wait("@ticketList");
    cy.get(".ticket-table")
      .contains("td", "Aguardando aprovação")
      .should("be.visible");
    cy.contains(".table-heading", "1 chamado").should("be.visible");
    cy.intercept(
      {
        method: "GET",
        pathname: "/api/tickets",
        query: { status: "PENDING_APPROVAL" },
      },
      {
        statusCode: 200,
        body: {
          content: [ticket(306, "PENDING_APPROVAL", 2)],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      },
    ).as("filteredTicketList");
    cy.get('[aria-label="Filtrar chamados por status"]').select(
      "PENDING_APPROVAL",
    );
    cy.wait("@filteredTicketList")
      .its("request.url")
      .should("include", "status=PENDING_APPROVAL");
    cy.get(".ticket-table")
      .contains("td", "Aguardando aprovação")
      .should("be.visible");
  });
});

function openTicketAs(
  role: UserRole,
  ticketId: number,
  status: TicketStatus,
  assignedAgentId: number | null,
) {
  cy.intercept("GET", "/api/auth/me", {
    statusCode: 200,
    body: {
      id: role === "AGENT" ? 2 : role === "ADMIN" ? 3 : 1,
      organizationId: 41,
      name: `E2E ${role}`,
      email: `${role.toLowerCase()}@example.test`,
      role,
    },
  });
  cy.intercept("GET", `/api/tickets/${ticketId}`, {
    statusCode: 200,
    body: ticket(ticketId, status, assignedAgentId),
  });
  cy.intercept("GET", `/api/tickets/${ticketId}/history`, {
    statusCode: 200,
    body: [],
  });
  cy.visit(`/tickets/${ticketId}`);
  cy.contains("h1", `Ticket ${ticketId}`).should("be.visible");
}

function ticket(
  id: number,
  status: TicketStatus,
  assignedAgentId: number | null,
) {
  const now = "2026-10-31T18:00:00Z";
  return {
    id,
    organizationId: 41,
    title: `Ticket ${id}`,
    description: "Test resolution review.",
    requesterId: 1,
    requesterName: "E2E REQUESTER",
    assignedAgentId,
    assignedAgentName: assignedAgentId ? "E2E AGENT" : null,
    assetId: null,
    assetNumber: null,
    priority: "MEDIUM",
    status,
    createdAt: now,
    updatedAt: now,
    dueAt: "2026-11-01T18:00:00Z",
    resolvedAt: status === "RESOLVED" ? now : null,
    closedAt: null,
    overdue: false,
  };
}
