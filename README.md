# UI-Driven OOAD for a 3-Tier Web Application — Workload Job Allocation

Teaching materials for a UI-driven object-oriented analysis and design (OOAD) walkthrough, using a generic workload job allocation screen as the case study. All diagrams are UML 2.5.1, written as PlantUML diagrams-as-code.

## Reference implementation (Java)

`app/` contains a working Java 21 three-tier web application built from this design: PostgreSQL persistence through a small annotation-based ORM, a business tier implementing every pattern in the deck, and a server-rendered board plus REST API. Start with [`app/README.md`](app/README.md).

| Document | Purpose |
|---|---|
| [`app/README.md`](app/README.md) | Quick start, pattern-to-class map, security controls, test suites |
| [`app/docs/architecture.md`](app/docs/architecture.md) | As-built Mermaid diagrams, traceability matrix, design decisions |
| [`app/docs/user-guide.md`](app/docs/user-guide.md) | How supervisors and viewers use the board and the API |
| [`app/docs/runbook.md`](app/docs/runbook.md) | Install, configure, operate, back up, troubleshoot |

## Sources of truth

Everything is generated from plain-text sources. Edit only these files:

| Source | Generates |
|---|---|
| `slides/ooad-3tier-patterns.md` (Marp Markdown) | `build/ooad-3tier-patterns.pdf`, `.pptx`, `.html` |
| `docs/ooad-walkthrough.md` (Markdown) | `build/ooad-walkthrough.pdf` |
| `diagrams/src/*.puml` (PlantUML) | `diagrams/png/*.png`, `diagrams/svg/*.svg` |

Never edit files in `build/`, `diagrams/png/` or `diagrams/svg/` by hand.

## Folder layout

| Path | Description |
|---|---|
| `slides/ooad-3tier-patterns.md` | 50-slide deck in Marp Markdown, light theme |
| `docs/ooad-walkthrough.md` | Eight-step method, operation contract, GRASP, design patterns (before → after), traceability matrix, quality controls |
| `diagrams/src/` | PlantUML sources; `style.iuml` is the shared light theme |
| `diagrams/png/`, `diagrams/svg/` | Rendered diagrams (PNG at 180 dpi) |
| `build/` | Generated PDF, PPTX and HTML |
| `Makefile` | Build targets for all of the above |
| `scripts/check_public.py` | `make check-public`: fails if private material (speaker notes, denylisted terms, anything in `private/`) would be committed |
| `.github/` | CI workflow (`check`: public-content gate, quality gate, in-memory integration tests; actions pinned by SHA), Dependabot, pull-request template with AI-assistance disclosure |
| `scripts/github-settings.sh` | Applies the GitHub settings: secret scanning and push protection, Dependabot, private vulnerability reporting, protection of `main` |
| `LICENSE`, `LICENSES/` | MIT for code, CC BY 4.0 for teaching content |
| `.pre-commit-config.yaml` | Pre-commit hooks: `make -C app check` and `make check-public` |
| `private/` | **Git-ignored, never pushed.** Speaker notes, internal reviews and the denylist; `make slides-with-notes` builds your own deck with the notes (see `private/README.md`) |

### Deck outline

| Section | Slides |
|---|---|
| Introduction | Cover, learning outcomes |
| Method | Layers vs tiers, eight steps, wireframe → operations, UI Service, operation contract, GRASP |
| UML models | Use case, domain model, full class diagram, component, sequence, activity |
| Architecture | Layered package diagram, deployment diagram, C4 context / container / component |
| Design patterns | Smell → pattern map; before/after for ECB, Facade, Strategy, Abstract Factory, Singleton, Proxy, DAO + Template Method, Observer; GoF and enterprise pattern maps; anti-patterns; class exercise |
| Verification | Traceability matrix, quality controls per tier, takeaways |

## Diagram index

| # | File | UML diagram type | Purpose |
|---|---|---|---|
| 01 | `01-wireframe.puml` | Salt (UI wireframe) | Starting point: Job Allocation Board |
| 02 | `02-class.puml` | Class | As built: all three tiers in one view |
| 03 | `03-component.puml` | Component | As built: provided / required interfaces, ports and adapters |
| 04 | `04-sequence.puml` | Sequence | As built: Allocate from click to commit, with the proxy's interceptor steps (403 / 409 / 422 branches) |
| 05 | `05-architecture.puml` | Package («layer») | As built: three layers, ports and adapters, no layering exceptions |
| 06 | `06-domain.puml` | Class | Domain model only |
| 07 | `07-strategy.puml` | Class | As built: Strategy with a Template Method default `rank()` |
| 08 | `08-dao.puml` | Class | As built: DAO + Template Method + Exception Translation |
| 09 | `09-events.puml` | Class | As built: Observer / domain events (`AuditListener`, `WorkloadMetricsListener`) |
| 10 | `10-ecb.puml` | Robustness (ECB) | Entity–Control–Boundary analysis |
| 11 | `11-facade.puml` | Class | As built: Facade with the coarse-grained `loadBoard()` |
| 12 | `12-factory.puml` | Class | As built: Abstract Factory (`OrmDAOFactory`, `InMemoryDAOFactory`) |
| 13 | `13-singleton.puml` | Class | As built: Singleton (`ConnectionPool`, fail-fast re-initialisation) |
| 14 | `14-proxy.puml` | Class | As built: Proxy + Chain of Responsibility (interceptors) |
| 15 | `15-usecase.puml` | Use case | As built: actors, use cases, «include» / «extend» |
| 16 | `16-activity-allocate.puml` | Activity | As built: Allocate with swimlanes per tier and every decision |
| 17 | `17-deployment.puml` | Deployment | As built: laptop device, Chrome, JVM and Docker execution environments, artifacts, communication paths |
| 18 | `18-pattern-map-gof.puml` | Composite structure (collaboration uses) | Where the GoF creational and structural patterns are applied (incl. Adapter, Decorator) |
| 18b | `18b-pattern-map-gof-behavioural.puml` | Composite structure (collaboration uses) | Where the GoF behavioural patterns are applied (incl. Chain of Responsibility) |
| 19 | `19-pattern-map-poeaa.puml` | Composite structure (collaboration uses) | Where the enterprise (PoEAA), ECB and GRASP patterns are applied |
| 20 | `20-c4-context.puml` | C4 level 1 (not UML) | System context: Supervisor, Viewer, integration client |
| 21 | `21-c4-container.puml` | C4 level 2 (not UML) | Containers = the three tiers: browser, Java web application, PostgreSQL |
| 22 | `22-c4-component.puml` | C4 level 3 (not UML) | Components inside the web application, mapped to Java packages |
| 23 | `23-api-integration-sequence.puml` | Sequence | REST integration patterns: ETag, Idempotency-Key replay, Problem Details |
| 24 | `24-pattern-context.py` → SVG | Context diagram (generated SVG) | **Start here**: the system in context with numbered pattern badges; each badge links to its slide |
| 25 | `25-ui-desktop.png`, `25-ui-mobile.png` | Screenshots (generated by `make e2e`) | As-built responsive UI at desktop and 390 px phone width (slide 41) |
| 26 | `26-db-schema.puml` | Database schema (IE / crow's foot; physical data model) | As built: every table, PK/FK and CHECK; PostgreSQL vs MySQL differences as notes |
| 27 | `27-sql-dialect.puml` | Class | As built: Strategy (`SqlDialect`) + Factory (`DialectFactory`) for PostgreSQL and MySQL |
| 28 | `28-storage-family.puml` | Class | As built: storage family (Abstract Factory) for PostgreSQL, MySQL and in-memory; Memento rollback and locking Proxy in memory; data copy between storages |
| 29 | `29-rollout-activity.puml` | Activity | Release, migration and rollback procedure (runbook §10), with every decision |
| 30 | `30-sso-sequence.puml` | Sequence | As built: single sign-on with OpenID Connect (Google, Microsoft, Apple): PKCE, state, nonce, ID-token checks, account linking (slide 46) |
| 31 | `31-devsecops-gates.puml` | Activity | As built: DevSecOps gates (`make check`, `make security`, `make audit`) and the evidence they leave (slide 47) |

C4 diagrams use the C4-PlantUML standard library (`!include <C4/...>`), bundled with PlantUML 1.2023 and later.

## Building the outputs

Run all commands from this folder (`3-tier-job-allocation`).

PlantUML runs from a jar: set `export PLANTUML="java -jar $HOME/dev/plantuml-1.2026.7.jar"` in `~/.zshrc` (the Makefile reads it). Homebrew's `plantuml` formula is avoided because it forces an OpenJDK upgrade.

| ID | Purpose | Depends on | Concurrency |
|---|---|---|---|
| CMD-OOAD-001 | Install tools | none | Run first |
| CMD-OOAD-002 | Render diagrams | 001 | Run before 003 when a `.puml` changed |
| CMD-OOAD-003 | Build slides (PDF, PPTX, HTML) | 001, 002 | Can run alongside 004 in a separate terminal |
| CMD-OOAD-004 | Build walkthrough PDF | 001 | Can run alongside 003 in a separate terminal |

🟦 **CMD-OOAD-001**: install Graphviz, Pandoc and Node.js (macOS); Google Chrome is also required

```bash
HOMEBREW_NO_INSTALL_UPGRADE=1 brew install graphviz pandoc node && echo "✅ CMD-OOAD-001 done"
```

🟩 **CMD-OOAD-002**: render every diagram to PNG and SVG

```bash
make diagrams && echo "✅ CMD-OOAD-002 done"
```

🟨 **CMD-OOAD-003**: build the slide deck as PDF, PPTX and HTML (Marp CLI uses your installed Chrome)

```bash
make slides && echo "✅ CMD-OOAD-003 done"
```

🟪 **CMD-OOAD-004**: build the walkthrough PDF (Pandoc → HTML styled by `docs/print.css` → Chrome → A4 PDF; no LaTeX needed)

```bash
make doc-pdf && echo "✅ CMD-OOAD-004 done"
```

Preview while editing: install the **Marp for VS Code** extension and open `slides/ooad-3tier-patterns.md`.

## Publishing (public repository)

Run `make check-public` before every commit and push (the pre-commit hook does it for you). It checks that `private/` is ignored, that the decks contain no speaker notes, and that no committed file contains a denylisted term.

## Keeping diagrams and code in sync

- Treat `diagrams/src/*.puml` as the source; never edit the PNG or SVG files by hand.
- In the implementation repository, render the diagrams in CI and fail the build if the committed images differ from a fresh render.
- Enforce the layering rules shown in the class and component diagrams with `import-linter` (Python), `go-arch-lint` (Go) or ArchUnit (Java).

## Licence and attribution

- **Code** (everything under `app/`, including its docs, plus `scripts/`, `private/notes.py`, `diagrams/src/*.py` and the build files): MIT, see [`LICENSE`](LICENSE).
- **Teaching content** (`slides/`, `docs/`, the diagram sources and the rendered diagrams in `diagrams/`): [Creative Commons Attribution 4.0 International](LICENSES/CC-BY-4.0.txt) (CC BY 4.0). Reuse and adapt freely, including commercially, with attribution: "UI-Driven OOAD for a 3-Tier Web Application by Jaric Sng, CC BY 4.0".
- Third-party components and their licences: [`app/THIRD_PARTY_NOTICES.md`](app/THIRD_PARTY_NOTICES.md).
- PlantUML is distributed under GPL/LGPL/MIT/Apache variants; the generated diagrams are not covered by PlantUML's licence.
- Pattern names and definitions follow Gamma et al., *Design Patterns* (GoF), Fowler, *Patterns of Enterprise Application Architecture* (DAO, DTO, Optimistic Offline Lock), Larman, *Applying UML and Patterns* (GRASP), and Jacobson's ECB stereotypes.
