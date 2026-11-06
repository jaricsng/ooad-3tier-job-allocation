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
| Persistence (DAO) | Parameterised SQL; optimistic locking; append-only audit trigger | `PersistenceIT` against a real PostgreSQL test database |
| Architecture | Dependencies point downward only | `ArchitectureTest` fails the build on a violation |

**Not built yet, for discussion:** OIDC single sign-on, per-job authorisation (OWASP API1:2023 BOLA), an OpenAPI contract, an OWASP ZAP scan and Testcontainers.
