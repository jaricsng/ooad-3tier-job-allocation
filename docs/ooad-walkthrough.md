# OOAD Walkthrough — Workload Job Allocation (3-Tier Web App)

## 1. Layers vs tiers

| Logical layer (code) | Physical tier (deployment) |
|---|---|
| UI Service: controllers, DTOs, validation | Tier 1: browser + web / UI service node |
| Business: services, domain objects, policies | Tier 2: application server |
| Persistence: DAO interfaces and implementations | Tier 3: database server (the DAO code runs in Tier 2) |

Rule: dependencies point downward only.

## 2. The eight-step UI-driven method

| # | Step | What you do | Output |
|---|---|---|---|
| 1 | Use cases and storyboard | Name the actor goal; sketch the screen | Use-case brief, wireframe (`01-wireframe`) |
| 2 | UI events → system operations | For each control, ask what the system must do | System sequence diagram |
| 3 | View models (DTOs) | List exactly what each screen region shows or sends | `JobSummaryDTO`, `WorkerCandidateDTO`, `AllocateRequestDTO`, `AllocationResultDTO` |
| 4 | UI Service (controller / BFF) | Map operations to endpoints; validate; authenticate; authorise | `AllocationController`, OpenAPI contract |
| 5 | Operation contracts | Pre- and post-conditions | Contracts (test oracles) |
| 6 | Domain model + GRASP | Assign behaviour to the objects that own the data | Class diagrams (`02-class`, `06-domain`) |
| 7 | Persistence (DAO) | One DAO per aggregate; optimistic locking | DAO interfaces, schema |
| 8 | Traceability | UI → API → service → domain → DAO | Matrix (section 6) |

### UI events → system operations

| UI event | System operation |
|---|---|
| Load / [Refresh] | `getPendingJobs(filter)` |
| Select job row | `getEligibleWorkers(jobId)` |
| [Allocate] | `allocate(jobId, workerId, version)` |
| [Auto-Allocate] | `autoAllocate(jobIds)` |
| [Unassign] | `unassign(allocationId)` |

## 3. Operation contract: `allocate(jobId, workerId, jobVersion, actor) : Allocation`

**Pre-conditions**

- The job exists and its status is PENDING.
- The worker has every required skill.
- The worker's remaining capacity is at least the job's effort.
- The actor holds the SUPERVISOR role.
- `jobVersion` matches the stored version.

**Post-conditions**

- An Allocation is created (ACTIVE) and linked to the Job and the Worker.
- `Job.status` is ALLOCATED and its version is incremented.
- An audit event JOB_ALLOCATED is appended.
- On any failure there is no state change; the API returns 409 (stale version) or 422 (rule violation).

## 4. GRASP decisions

| Principle | Decision |
|---|---|
| Information Expert | `Worker.canTake(job)`: Worker knows its skills and load |
| Creator | `Worker.assign()` creates the `Allocation` |
| Controller | `AllocationController` / `AllocationService` receive the system operations |
| Polymorphism | `AllocationPolicy` varies the ranking rule |
| Pure Fabrication | DAO classes |
| Low Coupling | Interfaces between tiers |

## 5. Design patterns: identify the smell, then apply the pattern

| Smell in the naive design | Pattern | Family | Applied as | Diagram |
|---|---|---|---|---|
| UI, rules and SQL tangled in one handler | ECB | Analysis (Jacobson) | Boundary / Control / Entity | `10-ecb` |
| Controller orchestrates many DAOs | Facade | GoF structural | `AllocationService` | `11-facade` |
| `switch` on the allocation rule | Strategy | GoF behavioural | `AllocationPolicy` | `07-strategy` |
| `new JdbcXxxDAO()` inside services | Abstract Factory | GoF creational | `DAOFactory` | `12-factory` |
| A new DB connection per call | Singleton | GoF creational | `ConnectionPool` | `13-singleton` |
| Authorisation and transaction code repeated | Proxy | GoF structural | `SecuredTransactionalProxy` | `14-proxy` |
| SQL in business code; JDBC boilerplate | DAO + Template Method | PoEAA + GoF | `JobDAO`, `AbstractJdbcDAO` | `08-dao` |
| Side effects hard-wired into `allocate()` | Observer | GoF behavioural | `JobAllocated` listeners | `09-events` |

### Discussion questions

1. **ECB**: Is `AllocationController` a boundary or a control object? Does the answer change between analysis and design?
2. **Facade**: When does a facade become a "God service"? How would you split `AllocationService` as it grows?
3. **Strategy**: Who should choose the policy: the supervisor on screen, configuration, or the job's department?
4. **Abstract Factory**: Factory or dependency-injection container: which would you use here, and what does each cost?
5. **Singleton**: Why do frameworks prefer a DI-managed singleton scope to a static `getInstance()`?
6. **Proxy**: Where else in this app would a protection, virtual (lazy-load) or caching proxy help?
7. **DAO**: DAO or Repository? What changes if the store moves from SQL to a document database?
8. **Observer**: Synchronous or asynchronous listeners? What happens to the audit record if the e-mail fails?

### Anti-patterns to watch for

- **Anemic domain model**: rules sit in services; entities only have getters and setters.
- **God facade**: one service with dozens of methods across many use cases.
- **Singleton abuse**: hidden global state that is hard to test and to run concurrently.
- **Pattern-first design**: patterns added without a smell to remove.

### Class exercise: "Swap allocation"

A worker hands an active allocation to a colleague with the same skills. The supervisor must approve, and the swap can be undone.

1. Sketch the wireframe change and list the new UI events.
2. Write the system operations and their pre- and post-conditions.
3. Decide which existing patterns absorb the change and which new one is needed. Hint: undo suggests Command.
4. Update the class and sequence diagrams and the traceability matrix.

## 6. Traceability matrix (paths under `/api/v1`)

| UI element | Endpoint | Service operation | Domain behaviour | DAO calls |
|---|---|---|---|---|
| Pending Jobs grid | `GET /jobs?status=PENDING` | `findPendingJobs` | — | `JobDAO.findPending` |
| Select job row | `GET /jobs/{id}/eligible-workers` | `findEligibleWorkers` | `Worker.canTake`, `AllocationPolicy.rank` | `WorkerDAO.findAllWithAllocations` |
| [Allocate] | `POST /allocations` | `allocate` | `Worker.assign`, `Job.markAllocated` | `AllocationDAO.save`, `JobDAO.update` (version check), `AuditDAO.append` via `AuditListener` |
| [Auto-Allocate] | `POST /allocations/auto` | `autoAllocate` | `AllocationPolicy.rank` → `Worker.assign` | as Allocate |
| [Unassign] | `DELETE /allocations/{id}` | `unassign` | `Allocation.release`, `Job.markPending` | `AllocationDAO.update`, `JobDAO.update` (version check), `AuditDAO.append` via `AuditListener` |

## 7. Quality controls per tier

| Tier | Design control (as built) | Verification (as built) |
|---|---|---|
| UI (View) | Output escaped; CSP `script-src 'none'`; CSRF token on every form | `WebUnitTest`; Playwright end-to-end |
| UI Service | Session sign-in with PBKDF2 password hashes and lockout; role check (`@RequiresRole`) in `SecuredTransactionalProxy`; input validation in DTOs | `ApiSecurityIT`, `SecurityTest` |
| Business / Domain | Rules inside `Worker` and `Job`; one transaction per facade call | `WorkerTest`, `JobTest`, `AllocationServiceTest`, `PolicyTest` |
| Persistence (DAO) | Parameterised SQL; optimistic locking; append-only audit trigger; storage chosen by `DB_URL` | `PersistenceIT` against real PostgreSQL and MySQL test databases; HTTP/API tests also on in-memory storage |
| Architecture | Dependencies point downward only | `ArchitectureTest` fails the build on a violation |

Also built since this walkthrough was first written: OIDC single sign-on (Google, Microsoft, Apple), an OpenAPI 3.1 contract checked by a test, and an OWASP ZAP scan (`make dast`).

**Not built yet, for discussion:** per-job authorisation (OWASP API1:2023 BOLA) and Testcontainers.

## 8. Designed for change: PostgreSQL, MySQL or in-memory storage

The persistence tier is designed so that **one setting, `DB_URL`, switches the storage** without changing or recompiling any other code. The business tier sees only the DAO interfaces and the `TransactionRunner` port, so it cannot tell which storage it is using.

| `DB_URL` | Storage (Abstract Factory) | DAOs | Transactions | Schema |
|---|---|---|---|---|
| `jdbc:postgresql://host:5432/joballoc` | `RelationalStorage` + `PostgresDialect` | `OrmDAOFactory` (mini ORM, JDBC) | Database transaction (`TransactionManagerAdapter`) | Migrations `db/migration/postgresql/V1..V4` |
| `jdbc:mysql://host:3306/joballoc` | `RelationalStorage` + `MySqlDialect` | `OrmDAOFactory` (same classes) | Database transaction (same adapter) | Migrations `db/migration/mysql/V1..V4` (same versions) |
| `memory:` | `InMemoryStorage` | `InMemoryDAOFactory` (guarded DAOs) | Lock + snapshot restored on failure (`InMemoryTransactionAdapter`, Memento) | Reference data from CSV; lost on restart |

How the design achieves this:

1. **Factory Method**: `StorageFactory.create(config)` reads `DB_URL` once, at start-up, and returns a `Storage`.
2. **Abstract Factory**: a `Storage` supplies the DAOs, the transaction runner and the health check *as a family*, so a MySQL DAO can never be combined with the in-memory transaction runner.
3. **Strategy + Factory**: for SQL storage, `DialectFactory` picks the `SqlDialect` from the URL prefix; it holds every product difference (pagination, duplicate-key codes, connection properties).
4. **Dependency inversion**: services depend on `DAOFactory`, the DAO interfaces and `TransactionRunner`, all owned by the business tier; `ArchitectureTest` fails the build if any business class imports a storage implementation.
5. **Fail fast**: any other `DB_URL` (for example Oracle) is rejected at start-up with the list of supported forms.

Evidence that the switch works: the same 34 integration tests pass on PostgreSQL 16 and MySQL 8.4, and 22 HTTP/API tests pass on in-memory storage (`make test-int-all`); `make db-copy` copies all data between any two storages. Diagrams: `27-sql-dialect`, `28-storage-family`; slides "One codebase, two databases" and "Three storages, one switch".

**Discussion:** what would adding Oracle need? (One dialect class, one entry in `DialectFactory`, one folder of migrations, the driver jar and its licence entry; the tests then run against it unchanged.)

