type TicketStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "PENDING_APPROVAL"
  | "RESOLVED"
  | "CLOSED";

describe("Ticket SLA indicators", () => {
  const dueAt = "2026-01-15T18:30:00Z";

  it("shows an overdue deadline in the browser timezone in the list and detail", () => {
    cy.viewport(390, 844);
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
    cy.intercept("GET", "/api/tickets*", {
      statusCode: 200,
      body: {
        content: [ticket(401, "OPEN", dueAt, true)],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });

    cy.visit("/tickets");
    cy.document()
      .its("documentElement.scrollWidth")
      .should("be.lte", 390);
    cy.get('[aria-label="Legenda de status e prioridade"]')
      .should("be.visible")
      .and("contain.text", "Aguardando aprovação")
      .and("contain.text", "Alta")
      .and("contain.text", "Atrasado");
    cy.get(".ticket-table")
      .contains("td", "Atrasado")
      .should("be.visible");
    assertLocalDeadline(dueAt);

    openTicket(401, "OPEN", dueAt, true);
    cy.get(".ticket-detail-card .ticket-overdue")
      .should("be.visible")
      .and("contain.text", "Atrasado");
    assertLocalDeadline(dueAt);
  });

  it("does not show a false overdue warning for resolved tickets", () => {
    openTicket(402, "RESOLVED", dueAt, false);

    cy.get('[aria-label="Status do chamado: Resolvido"]').should("be.visible");
    cy.get(".ticket-detail-card .ticket-overdue").should("not.exist");
    assertLocalDeadline(dueAt);
  });

  it("shows a clear fallback when the API has no SLA deadline", () => {
    openTicket(403, "IN_PROGRESS", null, false);

    cy.get(".ticket-metadata").should("contain.text", "Sem prazo SLA");
    cy.get(".ticket-detail-card .ticket-overdue").should("not.exist");
  });
});

function openTicket(
  id: number,
  status: TicketStatus,
  dueAt: string | null,
  overdue: boolean,
) {
  cy.intercept("GET", "/api/auth/me", {
    statusCode: 200,
    body: {
      id: 2,
      organizationId: 41,
      name: "E2E AGENT",
      email: "agent@example.test",
      role: "AGENT",
    },
  });
  cy.intercept("GET", `/api/tickets/${id}`, {
    statusCode: 200,
    body: ticket(id, status, dueAt, overdue),
  });
  cy.visit(`/tickets/${id}`);
  cy.contains("h1", `Ticket ${id}`).should("be.visible");
}

function ticket(
  id: number,
  status: TicketStatus,
  dueAt: string | null,
  overdue: boolean,
) {
  return {
    id,
    organizationId: 41,
    title: `Ticket ${id}`,
    description: "SLA indicator E2E scenario.",
    requesterId: 1,
    requesterName: "E2E REQUESTER",
    assignedAgentId: 2,
    assignedAgentName: "E2E AGENT",
    assetId: null,
    assetNumber: null,
    priority: "HIGH",
    status,
    createdAt: "2026-01-15T10:30:00Z",
    updatedAt: "2026-01-15T10:30:00Z",
    dueAt,
    resolvedAt: status === "RESOLVED" ? "2026-01-15T17:00:00Z" : null,
    closedAt: status === "CLOSED" ? "2026-01-15T17:30:00Z" : null,
    overdue,
  };
}

function assertLocalDeadline(utcValue: string) {
  cy.window().then((window) => {
    const localDeadline = new window.Intl.DateTimeFormat("pt-BR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new window.Date(utcValue));
    const utcDeadline = new window.Intl.DateTimeFormat("pt-BR", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "UTC",
    }).format(new window.Date(utcValue));

    expect(localDeadline).not.to.equal(utcDeadline);
    cy.get(`time[datetime="${utcValue}"]`).should(
      "have.text",
      localDeadline,
    );
  });
}
