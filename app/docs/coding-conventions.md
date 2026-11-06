# Coding Conventions, Configuration as Code and Policy as Code

Every rule in this document is enforced by a tool. Nothing depends on memory or goodwill. The baseline is the **Google Java Style Guide** and the **Oracle (Sun) Code Conventions**, adapted in two places (4-space indents, 120 columns) to match this code base.

| Gate | Command | Source of the rules | Fails the build on |
|---|---|---|---|
| Formatting basics | your editor | `.editorconfig` | (editor applies it as you type) |
| Formatting and identifier form | `make lint` | `config/checkstyle/checkstyle.xml` (Checkstyle 14.4.0) | any violation |
| Names that state their role | `make test` (`NamingConventionTest`) | Java test code | a class in the wrong role, a non-question boolean method, a misnamed exception |
| Architecture | `make test` (`ArchitectureTest`) | Java test code | a layer importing a layer it must not |
| Configuration schema | `make test` (`ConfigurationTest`) | `edu.ooad.joballoc.config.Setting` | invalid defaults, or `config/app.env.example` out of date |
| Policies | `make policy` | `policy/*.rego` (tested by `policy/*_test.rego`) | any `deny` rule |
| API contract | `make api-lint`, `make test` (`OpenApiContractTest`) | `api/.spectral.yaml`, `openapi.yaml` | contract errors or drift |
| All of the above, no database | `make check` | — | any of the above; run it before every commit |

Tools are pinned and verified like the application's jars: `make tools` downloads Checkstyle and Conftest into `tools/` (git-ignored) and checks each file against `tools.sha256`.

## 1. Formatting

| Rule | Convention | Checked by |
|---|---|---|
| Encoding and lines | UTF-8, LF line endings, final newline, no trailing whitespace, no tabs | `.editorconfig`, `NewlineAtEndOfFile`, `FileTabCharacter`, `trailingWhitespace` |
| Indentation | 4 spaces per block level; **8** for a continuation line; `case` indented 4 inside `switch` | `Indentation` |
| Line length | ≤ 120 columns (imports, URLs and HTML in view templates exempt) | `LineLength` |
| Braces | K&R: `{` at the end of the line, `}` on its own line; braces on **every** `if`/`for`/`while`, even one-liners | `LeftCurly`, `RightCurly`, `NeedBraces` |
| Line wrapping | Break **before** an operator (`+`, `&&`, `?`, `:`) and before a `.` in a call chain; break **after** a comma | `OperatorWrap`, `SeparatorWrap` |
| Whitespace | One space around binary operators and after `,` `;` and keywords; none inside `( )` or `< >`; none before `(` of a call | `WhitespaceAround`, `WhitespaceAfter`, `ParenPad`, `GenericWhitespace`, `MethodParamPad` |
| Blank lines | One blank line between members, imports groups and types; never two in a row. Consecutive **one-line** accessors may be grouped | `EmptyLineSeparator`, `blankLineAfterMember`, `noDoubleBlankLines` |
| Imports | No `*`; none unused or duplicated. Three groups, each sorted, one blank line between: ① project and third-party, ② `java.*`/`javax.*`, ③ `static` | `AvoidStarImport`, `UnusedImports`, `CustomImportOrder` |
| Member order | Static constants (public → private), static fields, instance fields, constructors, then methods; **overloads kept together** | `DeclarationOrder`, `OverloadMethodsDeclarationOrder` |
| One type per file | The file is named after its single top-level type | `OneTopLevelClass`, `OuterTypeFilename` |
| Statements | One statement per line, one variable per declaration, no assignment inside an expression, `switch` has `default` | `OneStatementPerLine`, `MultipleVariableDeclarations`, `InnerAssignment`, `MissingSwitchDefault` |
| Literals | No unexplained numbers in production code: name them (`HOURS_PER_DAY`, `HttpStatus.CONFLICT`). `-1, 0, 1, 2` are allowed; tests may use literals | `MagicNumber` |
| Long literals and modifiers | `L` not `l`; modifiers in JLS order (`public static final`); no redundant modifiers | `UpperEll`, `ModifierOrder`, `RedundantModifier` |

A correctly formatted method, showing each rule:

```java
public Response allocate(Request req) {                                      // K&R brace
    AllocateRequestDTO body = Json.read(req.body(), AllocateRequestDTO.class);
    String ifMatch = req.header("If-Match");
    if (body.jobId() == null || body.workerId() == null                     // break before ||,
            || (body.jobVersion() == null && ifMatch == null)) {            // continuation +8
        throw new BadRequestException("jobId, workerId and jobVersion (or an If-Match header) are required");
    }
    long version = body.jobVersion() != null ? body.jobVersion() : ETags.version(ifMatch);
    Allocation a = service.allocate(body.jobId(), body.workerId(), version);
    return Response.json(HttpStatus.CREATED, AllocationResultDTO.from(a, "Allocated"))
            .header("Location", "/api/v1/allocations/" + a.getAllocationId());   // break before "."
}
```

## 2. Naming

### 2.1 Form of each identifier (Checkstyle)

| Element | Form | Good | Bad |
|---|---|---|---|
| Package | all lower case, no underscores, under `edu.ooad.joballoc` | `persistence.ormdao` | `persistence.ORM_dao` |
| Class, interface, enum, record | `UpperCamelCase` noun or noun phrase | `AllocationService`, `JobSummaryDTO` | `allocationservice`, `Allocation_Service` |
| Abstract class | starts with `Abstract` | `AbstractOrmDAO` | `BaseDao`, `OrmDAOBase` |
| Interface | the role, **no** `I` prefix and no `Impl` on the interface | `AllocationPolicy` (impl: `SkillMatchPolicy`) | `IAllocationPolicy` |
| Method | `lowerCamelCase` **verb** phrase | `findPendingJobs`, `assign`, `markAllocated` | `pendingJobs2`, `doIt`, `Process` |
| Boolean method | a question: `is`, `has`, `can`, `should`, `includes`, `contains`, `matches` | `canTake(job)`, `isAllocatable()` | `takeable()`, `checkSkills()` |
| Field, parameter, local variable | `lowerCamelCase` noun; collections plural | `remainingHours`, `jobs`, `jobById` | `rh`, `jobList2`, `m_jobs`, `_jobs` |
| Boolean field or local | an adjective or `is…` | `eligible`, `dbUp`, `basicAuth` | `flag`, `check` |
| Constant (`static final` value) | `UPPER_SNAKE_CASE` | `MAX_BODY_BYTES`, `HOURS_PER_DAY` | `maxBodyBytes`, `Max_Body` |
| Enum constant | `UPPER_SNAKE_CASE` | `UNPROCESSABLE_CONTENT`, `SKILL_MATCH` | `UnprocessableContent` |
| Type parameter | one capital letter, optionally a digit | `T`, `ID`, `R` | `Type`, `TEntity` |
| Acronyms in names | written as a word (`Http`, `Json`, `Sql`, `Csrf`), at most 2 capitals in a row (so `ETags`, after the header's own spelling `ETag`, is allowed). **Exceptions:** `DAO` and `DTO` (pattern names used throughout the course) and `IT` (integration-test suffix) | `HttpExchangeAdapter`, `JsonParser` | `HTTPExchangeAdapter`, `JSONParser` |
| Lambda, catch and pattern variables | short `lowerCamelCase`; catch variable `e`, or `ignored` when deliberately unused | `w -> w.canTake(job)`, `catch (SQLException e)` | `catch (Exception E)` |

### 2.2 Names that state the role (NamingConventionTest)

A reader should know what a class *is* from its name and package alone.

| Package | Role | Required name | Examples |
|---|---|---|---|
| `web.controller` | Control (ECB) | `…Controller` | `BoardController`, `ApiController` |
| `web.dto` | Data Transfer Object | `…DTO` | `JobSummaryDTO`, `ProblemDTO` |
| `web.view` | Boundary (ECB) | `…View` (plus the `Html` helper) | `BoardView`, `LoginView` |
| `business` | Facade and its implementation | `…Service` / `…ServiceImpl` | `AllocationService`, `AllocationServiceImpl` |
| `business.policy` | Strategy | `…Policy`, `…PolicyFactory` | `SkillMatchPolicy`, `PolicyFactory` |
| `business.event` | Observer | `…Listener` (plus the publisher) | `AuditListener` |
| `business.proxy` | Chain of Responsibility | `…Interceptor` | `SecurityInterceptor` |
| `persistence.dao` | DAO contract | `…DAO`, `…DAOFactory`, `…Exception` | `JobDAO`, `DuplicateKeyException` |
| `persistence.ormdao` | DAO implementation | `Orm…DAO`, `AbstractOrm…DAO`, `Orm…DAOFactory` | `OrmJobDAO` |
| `adapter` | Adapter | `…Adapter` | `TransactionManagerAdapter`, `JdbcDatabaseHealthAdapter` |
| anywhere | Exception | ends with `Exception`, and only exceptions do | `AllocationRuleException` |
| tests | unit / integration | `…Test` / `…IT` (in `it`) | `WorkerTest`, `PersistenceIT` |

Avoid names that say nothing: `Manager`, `Helper`, `Util`, `Data`, `Info`, `Processor`, numbered names (`Service2`). If a class needs one of these words, it probably has more than one responsibility.

### 2.3 Names outside Java

| Artefact | Convention | Example |
|---|---|---|
| Database tables and columns | `snake_case`, singular table names | `allocation.work_date`, `audit_event` |
| Migration scripts | `V<n>__<description>.sql`, never edited after release | `V2__seed_reference_data.sql` |
| REST paths | lower case, plural nouns, `kebab-case`, version prefix; no verbs (except a sub-resource like `auto`) | `/api/v1/jobs/{id}/eligible-workers` |
| JSON fields | `lowerCamelCase` (the DTO component names) | `jobVersion`, `remainingHours` |
| OpenAPI `operationId` | `lowerCamelCase` verb phrase | `allocateJob`, `listPendingJobs` |
| Environment variables | `UPPER_SNAKE_CASE`, prefixed by area | `DB_POOL_SIZE`, `APP_PORT` |
| Rego packages and rules | lower case; `deny` / `warn` with a sentence message | `package access`, `deny contains msg if { … }` |
| Diagrams | `NN-topic.puml`, number = reading order | `14-proxy.puml` |
| Test methods | a sentence in `lowerCamelCase` that states the behaviour | `proxyChecksTheRoleBeforeOpeningATransaction` |

## 3. Design and coding rules

| Area | Convention | Checked by |
|---|---|---|
| Size | Methods ≤ 60 lines, ≤ 9 parameters, cyclomatic complexity ≤ 12 | `MethodLength`, `ParameterNumber`, `CyclomaticComplexity` |
| Design | Utility classes are `final` with a private constructor; fields are private (protected only for a template method); `@Override` always | `HideUtilityClassConstructor`, `FinalClass`, `VisibilityModifier`, `MissingOverride` |
| Immutability | Prefer `record` for DTOs, events and value objects; return unmodifiable collections; `final` fields | review |
| Errors | Never swallow an exception: an empty `catch` names its variable `ignored` and says why. Catch `Throwable` only where a contract requires it, with the reason | `EmptyCatchBlock`, `IllegalCatch` |
| Logging | `java.util.logging`, never `System.out/err` or `printStackTrace()`; never log secrets or query strings; include the request ID | `noConsoleOutput`, `noStackTraceToConsole` |
| Security | `SecureRandom` for tokens; parameterised SQL only; validate input at the boundary (DTOs, `Request` helpers) | `noInsecureRandom`, `PersistenceIT` |
| Null | Return `Optional` or an empty collection rather than `null` from finders | review |
| Time | Inject `Clock`; never call `LocalDate.now()` without one (testable time) | review |

## 4. Comments and Javadoc

- Every public type has Javadoc stating its **responsibility** in one sentence.
- Pattern classes name the pattern in CAPITALS and the "before" smell they remove (for example `PROXY (GoF structural): …`). This keeps the course material and the code in step.
- Comment *why*, not *what*. Delete commented-out code (Git remembers it).
- `TODO` comments carry a reason or a ticket; none in `main`.

## 5. Configuration as code

| Item | Where |
|---|---|
| The schema of every setting (name, default, description, validation, secret flag) | `src/main/java/edu/ooad/joballoc/config/Setting.java` |
| Parsing and fail-fast validation (all problems reported at once) | `AppConfig.from(env)` → `ConfigurationException` |
| Development template, generated from the schema | `config/app.env.example` (regenerate: `make config-template`) |
| Production template (no secrets; `DB_PASSWORD` injected at deploy time) | `config/prod.env.example` |
| Container configuration (credentials from the same variables) | `compose.yaml` |

**Rule of thumb:** the *shape* of a value (type, range, format) is validated in Java at start-up. Whether a value is *allowed* in an environment (for example "production needs secure cookies") is policy, kept in Rego.

To add a setting: add a constant to `Setting`, read it in `AppConfig.from`, then run `make config-template` and `make check`.

## 6. Policy as code

Policies are written in Rego (Open Policy Agent) and run with Conftest. They have their own unit tests, because a policy that never fires is worse than no policy.

| Policy | Input | Rules |
|---|---|---|
| `policy/compose.rego` | `compose.yaml` | Tagged image, not `:latest`; no privileged mode; ports bound to `127.0.0.1`; named volumes only; credentials from variables; health check present. *Warnings:* digest pin, `no-new-privileges` |
| `policy/config.rego` | `config/*.env.example` | Beyond localhost ⇒ `COOKIE_SECURE=true`. `prod` ⇒ no demo data, secure cookies, no password in the file. No real secret in any committed file. *Warning:* `prod` pointing at localhost |
| `policy/access.rego` | `target/policy-input/access.json`, exported from the code by `PolicyInputExport` | Every facade operation has `@RequiresRole`. Operations that change data require SUPERVISOR and are `@Transactional`. Only allow-listed routes are public. `/api/` routes use API access and sit under `/api/v1/` |

The access policy checks facts taken from the **real code**: reflection on `AllocationService` and the live route table, not a hand-kept list. If someone adds a public route or a write operation open to viewers, `make policy` fails.

## 7. Before every commit

```bash
make check        # conventions + 55 unit tests + policies + API lint, about 30 seconds, no database
make test-int     # when you changed persistence or the API (needs: make db-up)
```

If you use the `pre-commit` framework, `.pre-commit-config.yaml` at the repository root runs the same `make check` (as `make -C app check`), plus `make check-public`, which stops private teaching material from being committed.

### Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary` in the imperative, ≤ 72 characters, for example `refactor(persistence): translate SQL errors at the DAO boundary`. Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`. This lets release notes and version tags be generated from history.
