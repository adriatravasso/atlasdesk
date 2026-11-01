# Cypress E2E

A execução e os comandos ficam no [guia de desenvolvimento](../../docs/development.md). Rode a partir da raiz com Docker Compose e o perfil `e2e`:

```powershell
docker compose --profile e2e run --rm e2e
```

O perfil sobe PostgreSQL, backend e frontend próprios, usa dados sintéticos e limpa somente seu banco E2E. A suíte exercita autenticação e isolamento de organizações, tickets, ativos, dashboard, SLA, aprovação e histórico. Também cobre o fluxo público de demonstração com login real, leitura e criação de chamado.

As capturas de portfólio são geradas pelo spec `cypress/e2e/portfolio-screenshots.cy.ts` e salvas em `docs/screenshots/`. Vídeos e relatórios de execução são gravados em `artifacts/`.
