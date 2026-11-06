# Workload Job Allocation — Java 3-Tier Reference Implementation

A teaching implementation of the **UI-driven OOAD** walkthrough in `../docs/ooad-walkthrough.md` and the slide deck in `../slides/`. A supervisor allocates pending jobs to workers by skill and daily capacity through a web board and a REST API, backed by PostgreSQL, MySQL or in-memory storage. Users sign in with a password or through Google, Microsoft or Apple (OpenID Connect).

It is written in **plain Java 21 with no frameworks**. The web server, ORM, transaction proxy and security are small, readable classes, so students can see what Spring, Hibernate and Thymeleaf do for them.

| Tier | Package | What lives there |
|---|---|---|
| Presentation (UI Service) | `edu.ooad.joballoc.web` | Front controller, router, sessions, CSRF, server-rendered responsive views (Bootstrap 5.3 CSS, no JavaScript), REST API `/api/v1`, DTOs |
| Business | `edu.ooad.joballoc.business`, `edu.ooad.joballoc.domain` | `AllocationService` facade, service implementation, policies, events, security; rich domain model |
| Persistence | `edu.ooad.joballoc.persistence`, `edu.ooad.joballoc.storage` | DAO interfaces; storage chosen by `DB_URL`: PostgreSQL or MySQL (annotation-based ORM, SQL dialects, connection pool, transactions, migrations) or in-memory DAOs |

## Quick start

Prerequisites: **JDK 21+**, **Docker** (for PostgreSQL, or MySQL), and for browser tests **Node.js 18+** and **Google Chrome**. Full setup is in [`docs/runbook.md`](docs/runbook.md).

```bash
make db-up           # PostgreSQL 16 in Docker, with databases joballoc and joballoc_test
make run             # http://127.0.0.1:8080  (sign in as supervisor / Supervisor#2026)
```

No Docker? Install PostgreSQL 14+ locally and run `make db-setup` once instead.

**No Docker at all:** `make run-memory` runs the app on in-memory storage (data is lost when it stops).

**Single sign-on (optional):** a fresh clone or fork shows only the password form, because no identity provider is configured. To add **Sign in with Google / Microsoft**, register your own OAuth client with each provider and put its id in `.env` (the secret preferably in the macOS Keychain): [`docs/sso.md` → Try it on your laptop](docs/sso.md#try-it-on-your-laptop-step-by-step). Apple needs a deployed HTTPS site.

**MySQL instead of PostgreSQL:** `make db-up-mysql` then `make run-mysql`. The `DB_URL` value (`jdbc:postgresql://`, `jdbc:mysql://` or `memory:`) selects the storage and, for SQL, the dialect; the code, tests and UI are the same ([runbook §11](docs/runbook.md#11-running-on-mysql)).

| Command | What it does |
|---|---|
| `make test` | 87 unit tests: domain rules, policies, service contracts, proxy chain, security, HTTP layer, Singleton, configuration, OpenAPI contract, architecture and naming rules, pinned UI assets and CSP-safe views, SQL dialects, migration splitting, in-memory storage (rollback, thread safety, seed drift), data copy, ID-token verification, SSO account linking (no database) |
| `make test-int` | 35 integration tests against PostgreSQL: ORM, optimistic locking, append-only audit, migration checksums, data copy, HTTP API, OWASP checks, login CSRF, SSO against a test identity provider |
| `make test-int-mysql` / `test-int-memory` / `test-int-all` | The same tests on MySQL (35) / in memory (22, SQL-only tests skipped) / on all three |
| `make e2e` | 4 Playwright browser tests in Chrome, including a 390 px phone viewport (starts and stops the app itself) |
| `make check` | Quality gate without a database: Checkstyle conventions, unit tests, Rego policies, OpenAPI lint, secret-leak scan, SAST, governance (run before every commit) |
| `make lint` / `make policy` / `make api-lint` | Coding conventions / policy as code / OpenAPI lint (`make tools` fetches the pinned tools first; Spectral needs Node.js) |
| `make all` | All of the above |
| `make package` | Builds `target/joballoc.jar` |
| `make db-up` / `db-down` / `db-reset` | Start, stop, or wipe and recreate the PostgreSQL container |
| `make db-up-mysql` / `run-mysql` / `e2e-mysql` | Start MySQL 8.4, run the app on it, browser tests on it |
| `make run-memory` / `e2e-memory` | App / browser tests on in-memory storage (no database) |
| `make db-status` / `db-migrate` / `db-copy` / `smoke` | Rollout tools: migration status, migrate without starting, copy all data to `TARGET_DB_URL`, post-deploy check ([runbook §10](docs/runbook.md#10-release-and-rollout)) |
| `make secrets` / `sast` / `sca` / `governance` | Secret-leak gate (Gitleaks) / static analysis (Semgrep) / vulnerable dependencies (OSV, needs network) / governance as code ([security testing](docs/security-testing.md)) |
| `make dast` / `pentest` / `security` | OWASP ZAP scan (`make tools-dast` first) / scripted OWASP WSTG pen test / every security gate |
| `make audit` | Audit as code: runs the 17 controls in `audit/controls.json` and writes a hashed evidence pack to `target/audit/` (`AUDIT_SKIP=network,zap,mysql` where those are unavailable) |
| `make help` | Lists every target |

## Design patterns in the code

Each pattern from the slides is in the code base, and each class's Javadoc explains the "before" smell it removes. The full catalogue, the anti-patterns that were removed and every before/after are in [`docs/refactoring-review.md`](docs/refactoring-review.md).

| Pattern | Where | Class(es) |
|---|---|---|
| ECB (Entity–Control–Boundary) | Analysis → all tiers | `BoardView`/`LoginView` (boundary), `BoardController`/`AllocationServiceImpl` (control), `Job`/`Worker`/`Allocation` (entity) |
| Facade | Business entry point | `AllocationService` (coarse-grained `loadBoard` for the page) |
| Proxy (protection + transaction) | Around the facade | `SecuredTransactionalProxy` (`java.lang.reflect.Proxy`) running an interceptor chain |
| Chain of Responsibility | Inside the proxy | `SecurityInterceptor` → `ExceptionTranslationInterceptor` → `TransactionInterceptor` |
| Adapter | JDK server ⇄ app; persistence ⇄ business ports | `HttpExchangeAdapter`; `TransactionManagerAdapter`, `JdbcDatabaseHealthAdapter` |
| Strategy (+ Template Method default `rank`) | Auto-allocation rules | `AllocationPolicy`, `SkillMatchPolicy`, `LeastLoadedPolicy`, `FirstFitPolicy` |
| Factory | Policy by name; business-tier wiring | `PolicyFactory`; `BusinessTier.create` |
| Abstract Factory (storage family) + Factory Method | Relational database or in-memory storage, chosen from `DB_URL` | `Storage`, `RelationalStorage`, `InMemoryStorage`, `StorageFactory` |
| Memento + Proxy | In-memory transactions (snapshot restored on failure) and thread safety (locking proxy per DAO) | `InMemoryDAOFactory.inTransaction`, `guarded` |
| Strategy + Factory | Database products (PostgreSQL, MySQL) | `SqlDialect`, `PostgresDialect`, `MySqlDialect`, `DialectFactory` (chosen from `DB_URL`) |
| Abstract Factory | Matching DAO families | `DAOFactory`, `OrmDAOFactory`, `InMemoryDAOFactory` |
| Singleton | One connection pool | `ConnectionPool` (fails fast if re-initialised with other settings) |
| DAO + Template Method | Persistence | `GenericDAO` (+ `findByIds`), `JobDAO`…, `AbstractOrmDAO.query()` with the `afterLoad` hook |
| Exception Translation | DAO boundary | `DataAccessException`, `OptimisticLockException`, `DuplicateKeyException` |
| Ports and Adapters (DIP) | Business-owned interfaces | `business.port.TransactionRunner`, `business.port.DatabaseHealth`, package `adapter` |
| Observer | Side effects of allocation | `DomainEventPublisher`, `AuditListener`, `WorkloadMetricsListener`, `JobAllocated` |
| Front Controller (PoEAA) | Web entry point | `FrontController`, `Router` |
| Metadata Mapping, Identity Map, Optimistic Offline Lock (PoEAA) | ORM | `EntityMetadata`, `TransactionManager.TxContext`, `@Version` + `OptimisticLockException` |
| DTO, Query Object (PoEAA) | API contract; shared filter | `web.dto.*` records; `domain.JobCriteria` |
| GRASP Information Expert / Creator | Domain | `Worker.canTake()`, `Worker.assign()` creates `Allocation` |

Diagrams, the traceability matrix and design decisions are in [`docs/architecture.md`](docs/architecture.md).

## Security controls

| Control | Implementation | Verified by |
|---|---|---|
| Authentication | Session cookie (form sign-in) or HTTP Basic for the API; PBKDF2-SHA256 (210,000 iterations) password hashes | `SecurityTest`, `ApiSecurityIT` |
| Single sign-on | OpenID Connect authorization code flow with PKCE, state and nonce; ID token signature and claims verified; accounts linked only by verified e-mail; SSO never creates a supervisor ([`docs/sso.md`](docs/sso.md)) | `IdTokenVerifierTest`, `SsoAccountLinkingTest`, `SsoIT` |
| Brute-force protection | 5 failures lock a username for 5 minutes; generic error message | `SecurityTest` |
| Authorisation | Role checked in the proxy for every service call (VIEWER reads, SUPERVISOR changes) | `AllocationServiceTest`, `ApiSecurityIT` |
| Session hardening | 256-bit random IDs, rotated at sign-in, `HttpOnly`, `SameSite=Strict`, optional `Secure`, 30-minute idle timeout | `ApiSecurityIT` |
| CSRF | Per-session token required on every state-changing form or cookie-authenticated API call; double-submit token on the sign-in form (login CSRF) | `ApiSecurityIT` |
| Injection | All SQL parameterised in one place (`AbstractOrmDAO`) | `PersistenceIT` |
| XSS | All output HTML-escaped; CSP `script-src 'none'` (the UI needs no JavaScript) | `WebUnitTest`, `ApiSecurityIT` |
| Mass assignment | DTOs are explicit; unknown JSON fields rejected | `ApiSecurityIT` |
| Lost updates | `@Version` optimistic locking on jobs, workers and allocations → HTTP 409 | `AllocationServiceTest`, `PersistenceIT`, `ApiSecurityIT` |
| Audit | Append-only `audit_event` table enforced by a database trigger | `PersistenceIT` |
| Supply chain | Vendored jars pinned by SHA-256 (`lib.sha256`, checked on every build); Bootstrap CSS pinned by SHA-256 and served from the app itself (no CDN) | `make verify-libs`, `StaticAssetsTest` |
| Error handling | No stack traces to clients; API errors are RFC 9457 Problem Details carrying the `X-Request-Id` correlation ID, which is also in the access log | `ApiIntegrationPatternsIT` |
| Safe retries | `Idempotency-Key` on POST; stale `If-Match` → 412 | `ApiIntegrationPatternsIT` |
| Configuration and governance | Fail-fast settings schema; Rego policies for containers, configuration (TLS to the database, no secrets committed) and access control; governance rules G1–G8 | `ConfigurationTest`, `make policy`, `make governance` |
| Security testing | Secret-leak, SAST, SCA, DAST and pen-test gates; audit evidence pack mapped to OWASP ASVS, ISO/IEC 27001 and WSTG ([`docs/security-testing.md`](docs/security-testing.md), [`SECURITY.md`](SECURITY.md)) | `make security`, `make audit` |
| AI-assisted engineering | Risk register, human review checklist, disclosure in every pull request ([`docs/ai-assisted-engineering.md`](docs/ai-assisted-engineering.md)) | Governance rule G8, audit control GOV-04 |

## Project layout

```
app/
├── Makefile                 build, test, run, package, database container
├── compose.yaml             PostgreSQL 16 container, plus MySQL 8.4 under the "mysql" profile
├── lib/ + lib.sha256        vendored jars and their checksums
├── src/main/java/…          application (three tiers)
├── src/main/resources/      migrations per product (db/migration/postgresql, …/mysql), demo data, stylesheet
├── src/test/java/…          unit (AllUnitTests) and integration (AllIntegrationTests) suites
├── e2e/                     Playwright browser tests
├── config/                  app.env.example (generated), prod.env.example, Checkstyle rules
├── policy/                  Rego policies (+ tests): compose, configuration, access control, governance
├── security/                Semgrep rules, SBOM/SCA scripts, ZAP plan, pen-test script
├── audit/                   controls.json and the audit runner (evidence packs)
├── api/                     Spectral ruleset for the OpenAPI contract (src/main/resources/api/openapi.yaml)
└── docs/                    architecture, refactoring review, coding conventions, API and integration, user guide, runbook,
                             SSO, security testing, AI-assisted engineering
```

## Licence

Code: MIT (see [`LICENSE`](LICENSE)). The slides, diagrams and documentation of the parent repository are CC BY 4.0 (`../LICENSES/CC-BY-4.0.txt`). Third-party components and their licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
