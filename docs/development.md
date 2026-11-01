# Desenvolvimento

O ambiente de desenvolvimento, build e validação usa Docker Compose. No host, são necessários Git e Docker Compose v2; Java, Maven, Node.js, npm, PostgreSQL, Chrome e Cypress ficam nos containers.

## Iniciar

Na raiz do repositório:

```powershell
Copy-Item .env.example .env
docker compose config --quiet
docker compose up -d --build --wait db backend frontend
```

Abra <http://localhost:5173>. A API fica em <http://localhost:8080> e sua verificação de saúde em <http://localhost:8080/api/health>. Código de backend e frontend é montado do host para edição ao vivo; PostgreSQL e caches usam volumes nomeados.

O perfil `dev` cria `atlasdesk-admin@localhost.test` somente quando `DEV_ADMIN_PASSWORD` está definido no `.env` ignorado pelo Git. Use uma senha local exclusiva. Para explorar os dados locais de demonstração, configure `SPRING_PROFILES_ACTIVE=demo`, `VITE_DEMO_ENABLED=true` e `DEMO_PUBLIC_PASSWORD` no `.env`. O perfil `demo` usa um banco local separado se você o iniciar com `-p atlasdesk-demo`.

O perfil `demo` contém dois conjuntos locais fictícios, incluindo a conta `AGENT` exibida na tela de login quando habilitada. Para iniciá-lo com banco e volumes isolados, use `docker compose -p atlasdesk-demo up -d --build --wait db backend frontend`; a senha continua em `.env`, nunca no repositório. Não use o banco de demonstração para dados reais.

Se uma porta estiver ocupada, altere `POSTGRES_PORT`, `BACKEND_HOST_PORT` ou `FRONTEND_HOST_PORT` no `.env`. Os endereços internos continuam usando os nomes dos serviços Compose.

## Validação

Execute os comandos nos serviços em execução:

```powershell
docker compose exec backend mvn -B -ntp test
docker compose exec backend mvn -B -ntp verify
docker compose exec frontend npm run lint
docker compose exec frontend npm run typecheck
docker compose exec frontend npm run format:check
docker compose exec frontend npm run test:proxy
docker compose exec frontend npm run build
```

O perfil `e2e` usa outro PostgreSQL, backend e frontend. A suíte Cypress pode ser executada sob demanda:

```powershell
docker compose --profile e2e run --rm e2e
```

Ela restaura somente o banco sintético E2E. Para atualizar as imagens do portfólio:

```powershell
docker compose --profile e2e run --rm --entrypoint sh e2e -c "npm ci --prefer-offline && npx cypress run --browser chrome --spec cypress/e2e/portfolio-screenshots.cy.ts"
```

O guia E2E em [tests/e2e/README.md](../tests/e2e/README.md) cobre execução visual e artefatos.

## Encerrar

```powershell
docker compose down
```

Esse comando preserva dados e caches nomeados. Não use `docker compose down -v` para o fluxo normal de desenvolvimento.

O Jenkinsfile executa sua própria pipeline Compose com projeto isolado e não monta o socket Docker nos containers da aplicação.
