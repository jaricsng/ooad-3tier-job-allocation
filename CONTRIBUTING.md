# Contributing

Thank you for picking this project up. It is a teaching example, so every change should keep three things true: **the code works, the gates pass, and the diagrams and documents still describe the code.** This page is the front door; it points to the guides rather than repeating them.

## 1. Get it running (15 minutes)

| Step | Command (from the repository root) | Guide |
|---|---|---|
| Prerequisites | JDK 21+ (the newest JDK also works), Docker Desktop (database, and the pinned Semgrep image used by `make check`), Node.js 18+, Python 3.12+ | [`app/docs/runbook.md` §2](app/docs/runbook.md#2-install-prerequisites-macos) |
| Pinned gate tools | `make tools` (once; pulls the Semgrep image too; `make tools-dast` for the ZAP scan) | [`app/docs/security-testing.md`](app/docs/security-testing.md) |
| Database + app | `make db-up` then `make run` → <http://127.0.0.1:8080> (`supervisor` / `Supervisor#2026`) | [`app/README.md` → Quick start](app/README.md#quick-start) |
| No Docker | `make run-memory` (in-memory storage) | [runbook §12](app/docs/runbook.md#12-in-memory-storage-no-database) |
| Git hooks | `pipx install pre-commit && pre-commit install` | [`.pre-commit-config.yaml`](.pre-commit-config.yaml) |

**Docker Desktop file sharing:** `make check` mounts the project folder into the Semgrep container. If the project lives outside the folders Docker shares (default: `/Users` on macOS), add it under *Settings → Resources → File sharing*, or `make sast` stops with "Docker cannot mount …".

Application targets work from the repository root or from `app/`: the root `Makefile` forwards them. Only `make all` and `make clean` differ (root: teaching materials; use `make -C app clean` for the application).

Optional features you must set up yourself, because they need **your own** accounts:

- **Single sign-on** (Google, Microsoft, Apple): register your own OAuth clients → [`app/docs/sso.md` → Try it on your laptop](app/docs/sso.md#try-it-on-your-laptop-step-by-step).
- **GitHub settings** on your fork (secret scanning, branch protection): `scripts/github-settings.sh OWNER/REPO`.

## 2. Find your way around

| You want to… | Read |
|---|---|
| Understand the design (tiers, patterns, decisions D1–D26) | [`app/docs/architecture.md`](app/docs/architecture.md), [`docs/ooad-walkthrough.md`](docs/ooad-walkthrough.md) |
| See every diagram and what it shows | [`README.md` → Diagram index](README.md), sources in `diagrams/src/` |
| Know the naming, formatting and design rules | [`app/docs/coding-conventions.md`](app/docs/coding-conventions.md) |
| Operate, configure, migrate or roll back | [`app/docs/runbook.md`](app/docs/runbook.md) |
| Know which security gate checks what | [`app/docs/security-testing.md`](app/docs/security-testing.md), [`app/SECURITY.md`](app/SECURITY.md) |
| Use AI coding assistants responsibly here | [`app/docs/ai-assisted-engineering.md`](app/docs/ai-assisted-engineering.md) |
| See why things were refactored the way they are | [`app/docs/refactoring-review.md`](app/docs/refactoring-review.md) |

## 3. Workflow

1. **Branch** from an up-to-date `main`: `git switch main && git pull && git switch -c <type>/<short-name>` (`feat/`, `fix/`, `docs/`, `ci/`, `build/`).
2. **Change code, tests, diagrams and documents together** (section 4). A pull request that changes behaviour without its documentation is incomplete.
3. **Run the gate** before committing (the pre-commit hook runs it too): `make check` and `make check-public`. Add `make test-int-all` when storage or HTTP behaviour changes, `make e2e` when the UI changes, `make security` before a release.
4. **Commit** with a [Conventional Commits](https://www.conventionalcommits.org/) message, e.g. `feat(domain): …`, `fix(ci): …`, `docs(uml): …`.
5. **Open a pull request**: fill in the template, including the **AI assistance** section. CI runs `check` (Java 21) and `check-latest-jdk` (newest JDK); both must pass. Squash-merge.

Never commit secrets (keys, passwords, client secrets, `.env`). The secret-leak gate, `.gitignore` and GitHub push protection all guard this, but they are a safety net, not the plan.

## 4. Keep code, diagrams and documents in sync

| When you change… | Also update |
|---|---|
| A domain rule or entity | `diagrams/src/06-domain.puml`, `02-class.puml`, tests in `app/src/test/…/domain`, `docs/ooad-walkthrough.md` (operation contract), slides |
| A use case or screen | `15-usecase.puml`, `01-wireframe.puml`, `16-activity-allocate.puml`, `app/docs/user-guide.md` |
| A package, layer or dependency | `03-component.puml`, `05-architecture.puml`, `ArchitectureTest`, `app/docs/architecture.md` |
| The database schema | a new migration **for every product** (`db/migration/postgresql/` and `mysql/`, same version), `26-db-schema.puml`, `db/memory/*.csv` if seed data changes, runbook §10 for the rollout |
| An HTTP endpoint | `src/main/resources/api/openapi.yaml` (contract test enforces it), `23-api-integration-sequence.puml`, `app/docs/api-integration.md` |
| A setting | `config/Setting.java`, then `make config-template`; runbook §4 |
| A third-party library | `lib/` + `lib.sha256`, `security/components.csv`, `THIRD_PARTY_NOTICES.md` (licence and attribution) |
| A diagram | render with `make diagrams` and follow the [UML 2.5.1 notation rules](README.md#uml-251-notation-used) (decision diamonds with `[guards]`, reply arrows dashed and open, frames, keywords) |

## 5. Common extensions (start here)

| Extension | Where | Notes |
|---|---|---|
| Another SQL database (e.g. Oracle) | new `SqlDialect` + entry in `DialectFactory`, migrations folder, driver jar | The same integration tests then run against it; see walkthrough §8 |
| Another allocation policy | implement `AllocationPolicy`, register in `PolicyFactory` | Strategy pattern; add a `PolicyTest` |
| Another OpenID Connect provider | `SSO_OIDC_*` settings (no code) or a new entry in `OidcProviders` | Keep the ID-token checks in `IdTokenVerifier` |
| A new security or governance rule | Semgrep rule + rule test, Rego policy + test, or an audit control in `audit/controls.json` | Every rule ships with a test that proves it fires |
| Per-job authorisation (OWASP API1) | business tier, `SecurityInterceptor` | Listed as "not built yet" in the walkthrough: a good first larger contribution |

## 6. Reporting problems

- Bugs and ideas: open a GitHub issue with steps to reproduce.
- Security vulnerabilities: **do not** open an issue; use **Security → Report a vulnerability** (see [`SECURITY.md`](SECURITY.md)).

## 7. Licence

By contributing you agree that code is licensed under MIT and teaching content under CC BY 4.0, as described in the [README](README.md#licence-and-attribution).
