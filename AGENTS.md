# AtlasDesk engineering rules

- Use Java 21, Spring Boot 3.5.x, React 18.3.1, TypeScript strict mode, Vite, PostgreSQL 16, Maven Wrapper, npm, and native CSS in the project containers.
- Docker Compose is the required development and validation environment. Do not install application runtimes or dependencies on the host; use the Compose services for builds, tests, lint, and migrations.
- Keep backend and frontend source bind-mounted for development, and preserve database and dependency caches in named volumes. Use the `e2e` Compose profile for Cypress.
- Keep a modular monolith with clear separation of concerns. Apply KISS, DRY, and YAGNI; do not add abstractions, layers, or dependencies without a current need.
- Use standard Java and TypeScript conventions, validate inputs at boundaries, and prefer framework and language features already available.
- Do not add business features ahead of their task or introduce heavyweight UI, state, or networking libraries without an explicit requirement.
- Preserve existing code and history. Keep changes limited to the active task and inspect the diff before committing.
- Name task branches `ATL-NNNN`; use Conventional Commit messages such as `feat(ATL-0002): add organization persistence`.
- Develop each task on its own branch from the latest `main`, then push and open a PR targeting `main`. Follow the active task's explicit merge instruction; never merge when it requires human review first.
- Do not create a version tag or release unless the active task and human approval explicitly authorize it.
- Add or update automated tests for behavior changes, and run the applicable tests, lint, type checks, and build inside their Compose containers before publishing.
- Add dependencies only when required, prefer Spring Boot's managed versions, and keep lockfiles committed for reproducible npm installs.
- Verify GitHub ownership and repository visibility before any remote write. Never publish credentials or generated output.
