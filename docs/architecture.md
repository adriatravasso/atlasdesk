# Arquitetura

O AtlasDesk é um monólito modular com API REST. React entrega a interface; Spring Boot autentica sessões, aplica regras de chamados e escopo por organização; PostgreSQL persiste o domínio. A interface usa cookie de sessão HttpOnly e proteção CSRF. Não há JWT.

## C4 — Contexto

```mermaid
flowchart LR
  requester[Solicitante]
  agent[Agente de suporte]
  admin[Administrador da organização]
  atlas["Sistema AtlasDesk\nChamados, ativos, SLA e aprovações"]
  cf["Cloudflare Pages\nEntrega da interface e proxy /api"]
  render["Render\nAPI Spring Boot"]
  neon["Neon\nPostgreSQL"]

  requester -->|consulta e abre chamados| atlas
  agent -->|atende chamados e consulta ativos| atlas
  admin -->|gerencia usuários e configurações| atlas
  atlas -->|interface web hospedada| cf
  cf -->|encaminha chamadas /api| render
  render -->|JDBC e Flyway| neon
```

Os provedores fazem parte da implantação atual documentada. O sistema não depende de um serviço de identidade externo; login, sessão e autorização são internos.

## C4 — Containers conceituais

```mermaid
flowchart LR
  browser["Navegador\nReact 18, TypeScript, Vite"]
  pages["Cloudflare Pages\nArquivos estáticos e função /api/*"]
  api["Render Web Service\nSpring Boot 3.5 / Java 21\nMonólito modular e API REST"]
  db["Neon PostgreSQL\nPersistência relacional"]

  browser -->|HTTPS; interface e sessão| pages
  pages -->|HTTPS; /api/*, cookie e CSRF| api
  api -->|JDBC/TLS; JPA e Flyway| db
```

No desenvolvimento, os mesmos componentes executam como serviços Compose `frontend`, `backend` e `db`. Os containers do diagrama C4 são unidades lógicas de execução, não uma descrição das imagens Docker.

## Domínios

- `organization`: organização e configurações de SLA.
- `user` e `security`: usuários, papéis, login, sessão, CSRF e autorização.
- `ticket`: chamados, estados, prioridade, prazos e histórico.
- `asset`: inventário de ativos de TI.
- `dashboard`: indicadores agregados consultados pela interface.

O escopo de tenant vem da sessão autenticada. Repositórios e serviços verificam a organização para cada recurso; IDs enviados pelo navegador não concedem acesso entre organizações. Veja também o [diagrama de classes](class-diagram.md) e a [matriz de permissões](permissions.md).
