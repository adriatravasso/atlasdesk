describe("Ticket change history", () => {
  it("shows a chronological timeline with actor and timestamp to the requester", () => {
    const ticketId = 501;
    const history = [
      {
        id: 1,
        eventType: "CREATED",
        actorId: 1,
        actorName: "E2E REQUESTER",
        description: "Chamado criado",
        occurredAt: "2026-10-31T12:00:00Z",
      },
      {
        id: 2,
        eventType: "ASSIGNED",
        actorId: 3,
        actorName: "E2E ADMIN",
        description: "Responsável atribuído: E2E AGENT",
        occurredAt: "2026-10-31T12:05:00Z",
      },
      {
        id: 3,
        eventType: "STARTED",
        actorId: 2,
        actorName: "E2E AGENT",
        description: "Atendimento iniciado",
        occurredAt: "2026-10-31T12:10:00Z",
      },
    ];
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
    cy.intercept("GET", `/api/tickets/${ticketId}`, {
      statusCode: 200,
      body: {
        id: ticketId,
        organizationId: 41,
        title: `Ticket ${ticketId}`,
        description: "History timeline E2E scenario.",
        requesterId: 1,
        requesterName: "E2E REQUESTER",
        assignedAgentId: 2,
        assignedAgentName: "E2E AGENT",
        assetId: null,
        assetNumber: null,
        priority: "MEDIUM",
        status: "IN_PROGRESS",
        createdAt: "2026-10-31T12:00:00Z",
        updatedAt: "2026-10-31T12:10:00Z",
        dueAt: "2026-11-01T12:00:00Z",
        resolvedAt: null,
        closedAt: null,
        overdue: false,
      },
    });
    cy.intercept("GET", `/api/tickets/${ticketId}/history`, {
      statusCode: 200,
      body: history,
    }).as("ticketHistory");

    cy.visit(`/tickets/${ticketId}`);
    cy.wait("@ticketHistory");
    cy.get('[aria-label="Linha do tempo do chamado"] .ticket-timeline li')
      .should("have.length", 3)
      .then(($events) => {
        const descriptions = [...$events].map(
          (event) => event.querySelector("strong")?.textContent,
        );
        expect(descriptions).to.deep.equal([
          "Chamado criado",
          "Responsável atribuído: E2E AGENT",
          "Atendimento iniciado",
        ]);
      });
    cy.get('[aria-label="Linha do tempo do chamado"]')
      .should("contain.text", "E2E REQUESTER")
      .and("contain.text", "E2E ADMIN")
      .and("contain.text", "E2E AGENT");
    cy.get('time[datetime="2026-10-31T12:00:00Z"]').should("be.visible");
  });
});
