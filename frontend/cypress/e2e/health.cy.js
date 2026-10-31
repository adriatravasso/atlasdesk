describe("AtlasDesk service status", () => {
  it("shows the application and confirms its API connection", () => {
    cy.visit("/");
    cy.contains("h1", "A clearer view of IT operations.").should("be.visible");
    cy.get('[role="status"]').should("contain.text", "API connected");
  });
});
