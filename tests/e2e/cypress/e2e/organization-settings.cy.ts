import { loadAccounts, loginThroughUi } from "../support/commands.js";

describe("Organization SLA settings", () => {
  it("lets an administrator update and restore the live SLA policy", () => {
    loadAccounts().then(({ organizationId, admin, password }) => {
      loginThroughUi(organizationId, admin, password, "/");
      cy.visit("/settings");
      cy.contains("h1", "Configurações").should("be.visible");
      cy.get('input[name="slaLowHours"]').should("have.attr", "min", "1").and("have.attr", "max", "720");
      cy.get('input[name="slaMediumHours"]').should("have.attr", "min", "1").and("have.attr", "max", "720");

      cy.get<HTMLInputElement>('input[name="slaHighHours"]').invoke("val").then((value) => {
        const original = Number(value);
        const updated = original + 1;
        cy.get('input[name="slaHighHours"]').clear().type(String(updated));
        cy.contains("button", "Salvar configurações").click();
        cy.get('[role="status"]').should("contain.text", "valem para novos chamados");
        cy.get('input[name="slaHighHours"]').should("have.value", String(updated));

        cy.get('input[name="slaHighHours"]').clear().type(String(original));
        cy.contains("button", "Salvar configurações").click();
        cy.get('[role="status"]').should("contain.text", "valem para novos chamados");
        cy.get('input[name="slaHighHours"]').should("have.value", String(original));
      });
    });
  });
});
