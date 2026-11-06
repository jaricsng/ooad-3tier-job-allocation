# Operations Runbook — Job Allocation App

All commands run from the `app` folder:

```bash
cd ~/dev/"Software Engineering"/OOAD/3-tier-job-allocation/app
```

## 0. Quick reference

### Everyday commands

| Task | Command | Notes |
|---|---|---|
| Start the database | `make db-up` | Docker Desktop must be running. Waits until ready; creates `joballoc` and `joballoc_test`. |
| Start the app | `make run` | Foreground. Open http://127.0.0.1:8080 when the log shows `Listening on …`. |
| Check the app is up | `curl -s http://127.0.0.1:8080/health` | `{"status":"UP","database":"UP",…}` |
| Stop the app | **Ctrl+C** in the `make run` terminal | Clean shutdown; closes database connections. |
| Stop the app (terminal closed or stuck) | `lsof -ti tcp:8080 \| xargs -r kill` | Stops whatever listens on port 8080. |
| Stop the database (keep data) | `make db-down` | Stop the app first. |
| Fresh database for a class | `make db-reset` | Deletes all data, recreates both databases; demo jobs for today load when the app starts. |
| SQL prompt | `make db-psql` | Inside the container, as `joballoc`. |
| Database log | `make db-logs` | Ctrl+C to stop following. |
| Run every test suite | `make all` | Needs the database; browser tests use port 8099 and can run while the app runs on 8080. |
| Unit tests only (no database) | `make test` | About 2 seconds. |
| Quality gate before a commit (no database) | `make check` | Checkstyle, unit tests, Rego policies, OpenAPI lint. First run: `make tools` (Checkstyle, Conftest) and Node.js for Spectral. |
| API contract | http://127.0.0.1:8080/api/v1/openapi.yaml | Signed in, or with Basic auth. Source: `src/main/resources/api/openapi.yaml`. |
| No database at all (demo, quick try-out) | `make run-memory` | Data is lost when the app stops; see §12. |
| Migration status / migrate without starting | `make db-status` / `make db-migrate` | Uses `DB_URL`; see §10. |
| Post-deploy check | `make smoke` | `/health` must say UP. |
| Use MySQL instead of PostgreSQL | `make db-up-mysql` then `make run-mysql` | MySQL 8.4 in Docker (compose profile `mysql`). Same app, same data model; see §11. |
| Integration tests on MySQL / on both | `make test-int-mysql` / `make test-int-all` | Needs `make db-up-mysql` (and `make db-up` for both). |
| List all targets | `make help` | |

### Demo accounts

| Username | Password | Role |
|---|---|---|
| `supervisor` | `Supervisor#2026` | Can allocate, auto-allocate and unassign |
| `viewer` | `Viewer#2026` | Read only |

### Run in the background (for presenting)

```bash
make build >/dev/null
nohup java -cp "target/classes:lib/*" edu.ooad.joballoc.App > app.log 2>&1 & echo $! > app.pid
tail -f app.log                      # watch the log (Ctrl+C stops watching, not the app)
kill "$(cat app.pid)" && rm app.pid  # stop it
```

### Start-of-class checklist

1. Start Docker Desktop.
2. `make db-reset` (fresh data) or `make db-up` (keep yesterday's data).
3. `make run` in its own terminal.
4. `curl -s http://127.0.0.1:8080/health` shows `"UP"`.
5. Sign in as `supervisor`; the board shows today's pending jobs.

### End of class

1. Ctrl+C in the app terminal.
2. `make db-down`.

### First-time setup on a new Mac

| Need | Check | Install |
|---|---|---|
| JDK 21 or newer | `javac -version` | Any JDK 21+ (Temurin, Homebrew `openjdk`, …) |
| Docker | `docker version` | Docker Desktop or Colima |
| Node.js 18+ (browser tests only) | `node -v` | nvm or Homebrew |
| Google Chrome (browser tests only) | `ls "/Applications/Google Chrome.app"` | google.com/chrome |

No Homebrew PostgreSQL is needed. If something else already uses port 5432, put the demo database on another port once:

```bash
echo "DB_PORT=5433" > .env      # read by both docker compose and the Makefile
```

`make db-up`, `make run` and the tests then all use 5433 automatically. Delete `.env` to go back to 5432.

## 1. Components

| Component | Detail |
|---|---|
| Application | One JVM process (`edu.ooad.joballoc.App`), JDK 21+, listens on `APP_BIND:APP_PORT` (default `127.0.0.1:8080`) |
| Database | PostgreSQL 16 in Docker (`compose.yaml`, container `joballoc-db`, volume `joballoc_joballoc-data`), or any local PostgreSQL 14+; database `joballoc`, role `joballoc`. **Alternative:** MySQL 8.4 in Docker (container `joballoc-mysql`, volume `joballoc_joballoc-mysql-data`), or any MySQL 8.0.16+ (§11). The `DB_URL` prefix selects the SQL dialect |
| Test database | `joballoc_test`; wiped by the integration tests, never point it at real data |
| Health check | `GET /health` → 200 `{"status":"UP","database":"UP",…}` or 503 when the database is unreachable |
| Logs | Standard error, one line per event, `java.util.logging` format |

## 2. Install prerequisites (macOS)

Recommended: **Docker Desktop** (or Colima) for the database, plus JDK 21+. Nothing else is needed to run the app.

```bash
docker version     # Docker must be running
java -version      # 21 or newer
```

Alternative without Docker:

```bash
# PostgreSQL 16 without letting Homebrew upgrade other packages (avoids the OpenJDK upgrade problem)
HOMEBREW_NO_INSTALL_UPGRADE=1 HOMEBREW_NO_AUTO_UPDATE=1 brew install postgresql@16
brew services start postgresql@16
# Node.js is only needed for the browser tests; Google Chrome must be installed for them too.
java -version   # 21 or newer
```

Postgres.app (https://postgresapp.com) is a no-Homebrew alternative.

## 3. Create the database

With Docker (recommended):

```bash
cd app
make db-up        # pulls postgres:16-alpine on first run, creates joballoc + joballoc_test, waits until ready
make db-down      # stop; data is kept in the volume
make db-reset     # wipe everything and start fresh (handy before a class)
make db-psql      # SQL prompt inside the container
echo "DB_PORT=5433" > .env   # if another PostgreSQL already uses 5432; the Makefile and
                             # docker compose both read .env, so run/test/e2e follow automatically
```

The port is published on 127.0.0.1 only, so the database is not reachable from the network. Data lives in a Docker named volume and no folder from your Mac is mounted, so Docker Desktop file-sharing settings do not matter. The container restarts with Docker unless you stop it.

Without Docker (local PostgreSQL), once:

```bash
cd app
make db-setup                                   # uses: psql -h localhost -U postgres
PSQL_ADMIN="psql -d postgres" make db-setup     # Homebrew installs: your macOS user is the admin
```

The app runs its own migrations (`src/main/resources/db/migration`) at start-up and records them in `schema_version`. Applied migrations never run twice.

## 4. Configuration

Configuration is code. The schema of every setting lives in `edu.ooad.joballoc.config.Setting`. The app validates all settings at start-up and stops with a list of every invalid value, rather than starting half-configured. Templates are committed and checked in CI:

| File | Use |
|---|---|
| `config/app.env.example` | Development defaults, **generated** from the schema (`make config-template`). Copy to `.env` (git-ignored) to override |
| `config/prod.env.example` | Production template. Contains no secret: `DB_PASSWORD` is injected at deploy time from a secret store |
| `policy/config.rego` | What is allowed per environment, checked by `make policy` |

| Variable | Default | Notes |
|---|---|---|
| `APP_PROFILE` | `dev` | `dev` or `prod`. `prod` must have `COOKIE_SECURE=true` and `APP_DEMO_DATA=false`, and no password in the file (policy) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/joballoc` | `jdbc:postgresql://…`, `jdbc:mysql://…` or `memory:`; the prefix selects the storage and the SQL dialect. Production must verify TLS: `?sslmode=verify-full` (PostgreSQL) or `?sslMode=VERIFY_IDENTITY` (MySQL), enforced by policy P7 |
| `DB_USER` / `DB_PASSWORD` | `joballoc` / `joballoc` | `compose.yaml` reads the same variables. The password is never logged |
| `DB_POOL_SIZE` | `10` | 1-100. Maximum connections held by the single pool |
| `APP_PORT` | `8080` | 0-65535; `0` picks a free port (used by tests) |
| `APP_BIND` | `127.0.0.1` | Anything other than loopback requires `COOKIE_SECURE=true` (policy) |
| `COOKIE_SECURE` | `false` | `true` behind HTTPS (adds `Secure` cookies and HSTS) |
| `APP_DEMO_DATA` | `true` | Loads demo jobs for today when none exist |
| `DB_PORT` | `5432` | Host port of the PostgreSQL container (Makefile and compose only) |
| `MYSQL_PORT` | `3306` | Host port of the MySQL container (Makefile and compose only) |
| `MYSQL_ROOT_PASSWORD` | `joballoc-root` | MySQL container admin password (compose only; used by `make db-up-mysql` to create the test database) |
| `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` | `joballoc_test` on localhost | Integration tests only |

A bad value stops the start-up with a message such as:

```
ConfigurationException: Invalid configuration:
  - APP_PORT='80a' is invalid; expected 0-65535
  - DB_URL='jdbc:oracle:thin:@x:1521/y' is invalid; expected jdbc:postgresql://host:port/db or jdbc:mysql://host:port/db
```

## 5. Start, stop, check

```bash
make run                                   # foreground; Ctrl+C stops it cleanly
make package && java -jar target/joballoc.jar   # packaged form (keep lib/ next to target/)
curl -s http://127.0.0.1:8080/health
```

The process closes the connection pool on shutdown (SIGTERM / Ctrl+C).

## 6. Backup and restore

```bash
# Docker
docker compose exec -T db pg_dump -U joballoc -Fc joballoc > joballoc-$(date +%F).dump
docker compose exec -T db pg_restore -U joballoc -d joballoc --clean < joballoc-YYYY-MM-DD.dump
# Local PostgreSQL
pg_dump -h localhost -U joballoc -Fc joballoc > joballoc-$(date +%F).dump
pg_restore -h localhost -U joballoc -d joballoc --clean joballoc-YYYY-MM-DD.dump
# MySQL in Docker (--triggers keeps the append-only audit triggers)
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --triggers joballoc' > joballoc-$(date +%F).sql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot joballoc' < joballoc-YYYY-MM-DD.sql
```

## 7. Change a user password

```bash
printf '%s' 'a-long-new-password' | make -s hash-password      # prints pbkdf2-sha256$210000$…
make db-psql   # Docker; or: psql -h localhost -U joballoc -d joballoc
# then, at the SQL prompt:
#   update app_user set password_hash = '<paste hash>' where username = 'supervisor';
```

Passwords must be at least 12 characters. Restarting the app is not needed.

## 8. Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `Address already in use` at start | Port taken (often a previous run) | `lsof -i :8080`, stop that process, or set `APP_PORT` |
| `/health` returns 503, `database: DOWN` | PostgreSQL stopped or wrong `DB_URL` | `docker compose ps` then `make db-up`; or `pg_isready -h localhost`; check variables |
| `make db-up`: "ports are not available … 5432 … address already in use" | Another PostgreSQL (Homebrew, Postgres.app or another container) uses 5432 | `echo "DB_PORT=5433" > .env`, then `make db-up` again; see what holds the port with `lsof -nP -iTCP:5432 -sTCP:LISTEN` |
| `make db-up` fails: Cannot connect to the Docker daemon | Docker Desktop not running | Start Docker Desktop, then retry |
| `Cannot open database connection` at start | Role or database missing, wrong password | Run `make db-setup`; check `DB_USER`/`DB_PASSWORD` |
| `Migration V… failed` | Schema edited by hand, or a new script has an error | Read the SQL error in the log; fix the script; never edit an applied migration, add a new one. On MySQL each DDL statement commits on its own, so a failed script can leave some tables behind: `make db-reset-mysql` on a dev machine |
| MySQL: `You do not have the SUPER privilege and binary logging is enabled` | The audit triggers need `log_bin_trust_function_creators=1`, or a privileged account | Use the provided `compose.yaml` (it sets the flag). On a managed MySQL, have a DBA account run the migrations |
| MySQL: `Public Key Retrieval is not allowed` | Connecting without TLS to a `caching_sha2_password` account | Keep TLS on (the driver default). Do not add `allowPublicKeyRetrieval=true` outside a throw-away dev database |
| `DB_URL=… is invalid` | Unsupported product prefix | Use `jdbc:postgresql://` or `jdbc:mysql://`; another product needs a new `SqlDialect` (§11) |
| Users report "changed by someone else" often | Two supervisors working on the same date | Expected behaviour (optimistic locking); agree who handles which jobs |
| A user can't sign in | Locked after 5 failures, or wrong password | Wait 5 minutes or restart the app (lockouts are in memory); reset the password (§7) |
| `make db-up`: "mounts denied … not shared from the host" | An old `compose.yaml` that mounted a host folder | Use the current `compose.yaml` (no host mounts); run `make db-up` again |
| Homebrew install fails: "arm64 architecture is required" (OpenJDK) | Homebrew runs as Intel and tried to upgrade OpenJDK | Install with `HOMEBREW_NO_INSTALL_UPGRADE=1`; the app needs only a JDK 21+ that you already have |
| `node`/`pandoc` not found, or wrong version | A conda environment (`(base)` in the prompt) is first on PATH | `conda deactivate`, then retry |
| `make e2e` fails to launch the browser | Google Chrome not installed | Install Chrome, or set `CHROME_PATH` to a Chromium binary |
| `make verify-libs` fails | A jar in `lib/` was replaced or corrupted | Restore the original jar; never update a jar without updating `lib.sha256` in the same change |
| HTTP 500 with "reference …" | Unexpected error | The reference is the request's correlation ID: `grep "requestId=<id>" target/*.log` (or the console) shows the access-log line and the stack trace |
| An API client reports a failure | Any | Ask for the `X-Request-Id` response header (or `requestId` in the problem+json body) and search the log for it. Clients may send their own `X-Request-Id` to correlate across systems |
| API answers 409 "still being processed" | The same `Idempotency-Key` was sent twice concurrently | The client should wait and retry with the same key |
| `ConfigurationException` at start-up | An invalid setting | Fix every value listed in the message (see §4) |
| `make policy` fails | A change broke a rule in `policy/*.rego` | Read the `FAIL` line: it names the file and the rule; fix the change, not the policy, unless the rule itself is wrong (then update the policy **and** its test) |

## 9. Security operations

- Put the app behind a reverse proxy with TLS, then set `COOKIE_SECURE=true` and `APP_BIND=0.0.0.0`.
- Every request is logged once: `requestId=… METHOD /path status=… ms=…` (query strings are not logged, as they may hold personal data).
- Review the audit trail: `select * from audit_event order by occurred_at desc limit 50;` (the table can't be updated or deleted).
- Repeated `Failed sign-in` and `Forbidden` warnings in the log may mean password guessing or probing.
- Update dependencies deliberately: download the new jar, record its SHA-256 in `lib.sha256`, run `make all`.
- Before every release run `make check` (includes the secret-leak scan, SAST and governance) and `make security` (adds SCA, the OWASP ZAP scan and the scripted pen test). `make audit` produces the evidence pack for an auditor. Details: [`docs/security-testing.md`](security-testing.md).
- A Gitleaks finding means a secret may be in the history: **rotate the secret first**, then remove it from the code. Never "fix" it by widening `.gitleaks.toml`.
- An SCA finding: upgrade the component (update `lib.sha256`, `security/components.csv`, `THIRD_PARTY_NOTICES.md`), or add a dated, justified line to `security/sca-exceptions.csv`.
- Expired exceptions fail `make sca` on purpose; review them, do not just extend the date.

### 9.1 Single sign-on (Google, Microsoft, Apple)

1. Register the app with each provider ([`docs/sso.md`](sso.md#registering-the-app-with-each-provider)). The redirect URI is `<SSO_BASE_URL>/sso/<provider>/callback`.
2. Set `SSO_BASE_URL` (HTTPS in production, policy P9) and the provider settings (`SSO_GOOGLE_CLIENT_ID`, `SSO_MICROSOFT_TENANT`, `SSO_APPLE_PRIVATE_KEY_FILE`…). Client secrets and the Apple key come from the secret store, never from a committed file (policy P9, Gitleaks).
3. Give each person who should use SSO an e-mail address on their account: `update app_user set email = 'name@example.org' where username = '…';` (migration V4 adds the column). Leave `SSO_AUTO_PROVISION=false` unless new viewers may sign themselves in.
4. Restart and check that the sign-in page shows the buttons. The log shows `SSO sign-in` lines per provider; `SSO failed` lines give the reason (state, nonce, signature, issuer, audience, unverified e-mail).
5. Rotate the Apple client-secret key and Google/Microsoft client secrets on the providers' schedule (Microsoft secrets expire; at most 24 months).

## 10. Release and rollout

### 10.1 Rules that make rollouts safe

| Rule | Why | Enforced by |
|---|---|---|
| Never edit an applied migration; add a new version | Every database must reach the same schema by the same steps | Checksums in `schema_version`: the app and `make db-status` refuse a changed script |
| Migrations are **expand first, contract later** (add columns/tables in release N, remove old ones in N+1 or later) | The previous app version keeps working on the new schema, so an app rollback needs no database restore | Code review; PostgreSQL V3 is the one exception (it drops the array columns), so it needs a backup |
| The database may never be ahead of the code | An older build on a newer schema may write data the new schema rejects | The app and `make db-status` stop on an `UNKNOWN` version |
| Same tests on every storage | "Supports MySQL" is proven, not claimed | `make test-int-all` |

### 10.2 Rollout steps

| # | Step | Command | Pass when |
|---|---|---|---|
| 1 | Quality gates on the release build | `make check` then `make test-int-all` | All green |
| 2 | Back up the database | §6 | Backup file exists and is not empty |
| 3 | See what will change | `make db-status` | Only `PENDING` and `APPLIED`; no `CHANGED` or `UNKNOWN` |
| 4 | Rehearse the data on the new code (optional) | `TARGET_DB_URL=memory: make db-copy` | Every table copies; nothing is written |
| 5 | Stop the app | Ctrl+C, or stop the service | `/health` no longer answers |
| 6 | Migrate | `make db-migrate` | "Up to date." |
| 7 | Start the new version | `make run` (or the service) | Log shows `Storage: …` and `Listening on …` |
| 8 | Smoke test | `make smoke`, then sign in once as the supervisor | `SMOKE OK`; the board loads |

Steps 6 and 7 can be one step (the app migrates on start). Running them separately shows a migration failure before any user can reach the new version.

### 10.3 Rollback

| Situation | Action |
|---|---|
| App problem, migrations were expand-only | Start the previous version; the schema stays (it is compatible) |
| Migration failed on PostgreSQL | Nothing to undo: the script ran in one transaction and was rolled back. Fix the script, release again |
| Migration failed on MySQL | MySQL commits each DDL statement: restore the backup (§6), fix the script, release again |
| A contract migration ran (dropped or renamed something) | Restore the backup, then start the previous version |
| `make db-status` shows `UNKNOWN` | The code is older than the database: deploy the matching (newer) build, or restore the backup made before that migration |
| `make db-status` shows `CHANGED` | An applied script was edited: restore the file from version control (`git checkout -- <file>`) |

### 10.4 Switching database product (for example PostgreSQL to MySQL)

1. Start the target and create an empty database (`make db-up-mysql` creates `joballoc`).
2. Stop the app so no data changes during the copy.
3. `make db-status` on the source must show every script `APPLIED`.
4. Copy: `TARGET_DB_URL=jdbc:mysql://localhost:3306/joballoc TARGET_DB_USER=joballoc TARGET_DB_PASSWORD=… make db-copy`. The target is migrated first, then every table is copied in one transaction; rows already there (the reference data) are kept.
5. Verify: the copy report shows each table; compare counts (`select count(*) from job` on both).
6. Point `DB_URL` at the target, start the app, `make smoke`.
7. Keep the source read-only until users confirm; it is the rollback.

The copy works for any pair of storages because it uses only the DAO interfaces (Abstract Factory). It was rehearsed from PostgreSQL 16 to MySQL 8.0: identical counts in every table and identical timestamps to the microsecond.

## 11. Running on MySQL

The application supports **PostgreSQL 14+** (default) and **MySQL 8.0.16+** (tested with 8.0 and 8.4 LTS). The `DB_URL` prefix chooses the SQL dialect; nothing else in the code changes.

```bash
make db-up-mysql        # MySQL 8.4 in Docker, databases joballoc + joballoc_test, waits until healthy
make run-mysql          # the app on jdbc:mysql://localhost:3306/joballoc
make test-int-mysql     # the 23 integration tests against MySQL
make e2e-mysql          # the browser tests with the app on MySQL
make db-mysql           # mysql prompt as the app user
make db-down-mysql      # stop (keep data); make db-reset-mysql deletes the data
```

What differs, and where it is handled:

| Concern | PostgreSQL | MySQL | Handled in |
|---|---|---|---|
| Migration scripts | `db/migration/postgresql/` | `db/migration/mysql/` (same version numbers) | `Migrator` + `SqlDialect.migrationFolder()` |
| Duplicate key error | SQLSTATE `23505` | error code `1062` | `SqlDialect.isUniqueViolation` |
| One ACTIVE allocation per job | partial unique index | generated column + unique index | migration scripts |
| Append-only audit | PL/pgSQL trigger | two `SIGNAL` triggers | migration scripts |
| Instants | `timestamptz` | `datetime(6)`, session time zone forced to UTC | `MySqlDialect.connectionProperties` |
| DDL in a transaction | yes (a failed script rolls back) | no (each DDL commits) | documented risk; reset the dev database |

Switching an existing installation does **not** move its data: export from one product and load into the other is a data migration project of its own. For a class, start MySQL fresh; demo data loads at start-up.

To add another product (for example Oracle): write `OracleDialect`, add its URL prefix to `DialectFactory`, write `db/migration/oracle/V1…V3`, add the JDBC driver to `lib/` with its SHA-256 in `lib.sha256` and its licence in `THIRD_PARTY_NOTICES.md`, then run the integration tests against it.

## 12. In-memory storage (no database)

`DB_URL=memory:` runs the whole app without a database: `make run-memory`, `make e2e-memory`, `make test-int-memory`.

- Starts with the same reference data as the SQL databases (skills, workers, the two demo users) from `src/main/resources/db/memory/*.csv`; a unit test checks these files match the SQL seed. Demo jobs load as usual.
- Behaves like the databases: optimistic locking, all-or-nothing transactions (a lock plus a snapshot that is restored on failure), safe for concurrent requests.
- **Everything is lost when the app stops.** Policy P8 forbids `memory:` with `APP_PROFILE=prod`.
- Nothing to migrate or back up: `make db-status` says so.
- Use it for: class demos without Docker, quick UI checks, fast browser tests. Do not use it for data anyone needs tomorrow.
