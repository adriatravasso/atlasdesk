describe("Organization SLA settings", () => {
  it("lets an administrator update and restore the SLA policy", function () {
    cy.env(["ADMIN_EMAIL", "ADMIN_PASSWORD", "ADMIN_ORGANIZATION_ID"]).then(function (env) {
      const email = env.ADMIN_EMAIL as string | undefined;
      const password = env.ADMIN_PASSWORD as string | undefined;
      const organizationId = env.ADMIN_ORGANIZATION_ID as string | undefined;
      if (!email || !password || !organizationId) {
        (this as unknown as Mocha.Context).skip();
        return;
      }

      cy.visit("/login");
      cy.get("#organizationId").type(organizationId);
      cy.get("#email").type(email);
      cy.get("#password").type(password);
      cy.contains("button", "Entrar").click();
      cy.visit("/settings");
      cy.contains("h1", "Configurações").should("be.visible");
      cy.get<HTMLInputElement>('input[name="slaLowHours"]').should("have.attr", "min", "1").and("have.attr", "max", "720");
      cy.get<HTMLInputElement>('input[name="slaMediumHours"]').should("have.attr", "min", "1").and("have.attr", "max", "720");

      cy.get<HTMLInputElement>('input[name="slaHighHours"]').invoke("val").then((value) => {
        const original = Number(value);
        const updated = original === 720 ? original - 1 : original + 1;
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
