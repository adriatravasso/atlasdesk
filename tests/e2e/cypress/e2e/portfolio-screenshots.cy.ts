import { loadAccounts, loginThroughUi } from "../support/commands.js";

describe("AtlasDesk portfolio walkthrough screenshots", () => {
  beforeEach(() => {
    cy.viewport(1280, 900);
    cy.resetE2eData();
  });

  it("captures real application screens through a representative workflow", () => {
    cy.visit("/login");
    cy.get(".public-demo").should("be.visible");
    cy.screenshot("portfolio-login", { capture: "viewport" });

    loadAccounts().then(
      ({ organizationId, admin, agent, requester, password }) => {
        loginThroughUi(organizationId, admin, password, "/");

        cy.visit("/assets");
        cy.contains("h1", "Ativos").should("be.visible");
        cy.contains("button", "Novo ativo").click();
        cy.get('input[name="assetNumber"]').type("PORTFOLIO-LT-001");
        cy.get('input[name="name"]').type("Notebook de atendimento");
        cy.get('input[name="type"]').type("Notebook");
        cy.get('textarea[name="description"]').type(
          "Equipamento de demonstração do ambiente isolado.",
        );
        cy.contains("button", "Cadastrar").click();
        cy.contains("tr", "PORTFOLIO-LT-001").should("be.visible");
        cy.scrollTo("top", { ensureScrollable: false });
        cy.screenshot("portfolio-assets", { capture: "viewport" });

        cy.visit("/settings");
        cy.contains("h1", "Configurações").should("be.visible");
        cy.get('input[name="slaHighHours"]').should("have.value", "4");
        cy.scrollTo("top", { ensureScrollable: false });
        cy.screenshot("portfolio-settings", { capture: "viewport" });

        cy.visit("/users");
        cy.contains("h1", "Usuários").should("be.visible");
        cy.contains("admin@atlasdesk.test").should("be.visible");
        cy.scrollTo("top", { ensureScrollable: false });
        cy.screenshot("portfolio-users", { capture: "viewport" });

        loginThroughUi(organizationId, requester, password, "/");
        cy.visit("/tickets/new");
        cy.get('input[name="title"]').type(
          "Notebook reinicia após atualização",
        );
        cy.get('textarea[name="description"]').type(
          "O equipamento reinicia durante a inicialização. A falha começou após a última atualização.",
        );
        cy.get('select[name="priority"]').select("HIGH");
        cy.get('select[name="assetId"]').select("1");
        cy.contains("button", "Abrir chamado").click();
        cy.contains("h1", "Notebook reinicia após atualização").should(
          "be.visible",
        );

        cy.location("pathname").then((ticketPath) => {
          loginThroughUi(organizationId, agent, password, "/");
          cy.visit(ticketPath);
          cy.contains("button", "Assumir chamado").click();
          cy.contains("button", "Iniciar atendimento").click();
          cy.get('[aria-label="Status do chamado: Em andamento"]').should(
            "be.visible",
          );
          cy.contains("button", "Resolver chamado").click();
          cy.get(
            '[aria-label="Status do chamado: Aguardando aprovação"]',
          ).should("be.visible");

          loginThroughUi(organizationId, admin, password, "/");
          cy.visit(ticketPath);
          cy.get('[aria-label="Linha do tempo do chamado"]')
            .should("contain.text", "Atendimento iniciado")
            .and("contain.text", "Resolução enviada para aprovação");
          cy.contains("button", "Aprovar resolução").click();
          cy.get('[aria-label="Status do chamado: Resolvido"]').should(
            "be.visible",
          );
          cy.get('[aria-label="Linha do tempo do chamado"]')
            .should("contain.text", "Resolução aprovada");
          cy.screenshot("portfolio-ticket-workflow", {
            capture: "viewport",
          });
          cy.get('[aria-label="Linha do tempo do chamado"]')
            .scrollIntoView()
            .should("be.visible");
          cy.screenshot("portfolio-ticket-history", {
            capture: "viewport",
          });

          cy.visit("/");
          cy.contains("h1", "Visão da organização").should("be.visible");
          cy.document()
            .its("documentElement.scrollWidth")
            .should("be.lte", 1280);
          cy.contains(".dashboard-card", "Resolvidos").should(
            "contain.text",
            "1",
          );
          cy.contains(".dashboard-card", "Ativos cadastrados").should(
            "contain.text",
            "1",
          );
          cy.screenshot("portfolio-dashboard", { capture: "viewport" });
        });
      },
    );
  });
});
