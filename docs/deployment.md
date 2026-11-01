# Deploy e demonstração pública

## Componentes atuais

| Camada | Serviço | Configuração documentada |
| --- | --- | --- |
| Frontend e proxy `/api/*` | [Cloudflare Pages](https://atlasdesk.pages.dev) | Repositório `adriatravasso/atlasdesk`, branch `main`, raiz `frontend`, build `npm ci && npm run build`, saída `dist`; Pages Function encaminha para a API |
| API | [Render](https://atlasdesk-api.onrender.com/api/health) | Web Service Docker, imagem definida por `backend/Dockerfile`, perfil `prod`, endpoint de saúde `/api/health` |
| Banco | Neon, projeto `atlasdesk` | PostgreSQL gerenciado; conexão JDBC/TLS configurada no Render e migrations Flyway aplicadas pela API |

O repositório é público. O frontend usa o proxy Pages para manter a API na mesma origem do navegador. URL pública da aplicação: <https://atlasdesk.pages.dev>.

## Habilitar a conta de demonstração

A implementação desta branch adiciona uma conta `AGENT` com dados sintéticos. O login usa a autenticação e o banco reais. Para publicar essa versão, após integração da branch:

1. No Render, habilite `DEMO_SEED_ENABLED=true` e defina `DEMO_PUBLIC_PASSWORD=AtlasDeskDemo2026!`. O valor é uma credencial pública de portfólio, não um segredo administrativo. `DEMO_PUBLIC_EMAIL` pode manter `demo.agent@atlasdesk.test`; `DEMO_ORGANIZATION_NAME` pode manter `AtlasDesk Public Demo`.
2. Leia nos logs de inicialização do Render o ID da organização informado pelo seed. Na build de produção do Cloudflare Pages, configure `VITE_DEMO_ENABLED=true`, `VITE_DEMO_ORGANIZATION_ID=<id da organização demo>`, `VITE_DEMO_EMAIL=demo.agent@atlasdesk.test` e `VITE_DEMO_PASSWORD=AtlasDeskDemo2026!`. Essas variáveis `VITE_` ficam incorporadas no JavaScript entregue publicamente. Nunca coloque nelas secrets privados.
3. Faça um deploy do backend e da página e valide login, dashboard, chamados, ativos e isolamento.

Não foi feito reset do banco, mudança de secrets dos provedores ou deploy de produção nesta task. Até validar a configuração acima, a nova conta e o seed não devem ser considerados disponíveis no site hospedado.

O seed roda apenas no perfil `prod` e quando `DEMO_SEED_ENABLED` está ligado. Ele cria a organização demo se ausente e usuários sintéticos para os papéis necessários. A conta exibida é `AGENT`, portanto pode operar chamados e ativos da própria organização, mas não administrar usuários nem acessar organizações alheias. A senha pública aparece na interface e não pode ser reutilizada em outro ambiente.

## Persistência e restauração

Na inicialização habilitada, o backend remove chamados, histórico e ativos somente da organização `AtlasDesk Public Demo` e recria o conjunto inicial. Usuários e organização são preservados; dados de outras organizações não são tocados. Essa restauração acontece quando a instância inicia ou reinicia, não há cron nem endpoint de reset público. Mudanças feitas por visitantes persistem até a próxima inicialização do backend.

O login limita cada combinação organização/e-mail a 10 tentativas por minuto por instância do backend. Ao exceder o limite, a API retorna HTTP 429 com `Retry-After`; a tela informa para aguardar. O contador usa memória local e é reiniciado junto com o processo. Ao executar múltiplas instâncias, use um limitador compartilhado antes de escalar.

A demo é compartilhada. Alterações concorrentes podem ser vistas por outras pessoas; eventos de encerramento de serviço gratuito podem restaurar dados mais cedo do que o esperado. Não insira dados pessoais ou reais. Para limites de hospedagem, veja abaixo e a [nota de decisões](decisions-and-limitations.md).

## Operação e limites

- Render Free suspende serviços inativos e pode demorar para acordar; não há SLA de produção.
- Neon Free possui limites de armazenamento, compute e conexões; o pool do backend é pequeno por configuração.
- Cloudflare Pages Functions atende as requisições da API e está sujeito aos limites do plano.
- Esta demonstração não oferece backup de negócio, monitoramento externo ou processo de resposta a incidentes.

Consulte [Render Free](https://render.com/docs/free), [preços do Neon](https://neon.com/pricing) e [limites do Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/) antes de alterar o deploy.
