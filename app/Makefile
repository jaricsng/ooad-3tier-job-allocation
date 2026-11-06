# Workload Job Allocation — Java 21, 3-tier, no build tool or framework needed.
# Requirements: JDK 21+ (javac/java), PostgreSQL 14+, and for `make e2e` Node.js 18+ with Google Chrome.
#
#   make help        list targets
#   make test        unit tests (no database)
#   make test-int    integration + API security tests (database joballoc_test)
#   make e2e         browser tests (starts the app on E2E_PORT)
#   make run         start the app on APP_PORT (default 8080)
#   make check       conventions + unit tests + policies (no database): run before every commit

SHELL      := /bin/bash

# Optional local settings (not committed): e.g. DB_PORT=5433 when 5432 is already in use.
# docker compose reads the same .env file, so the container and the app always agree.
-include .env
# Pass every setting from .env to the app (make run, tests) and to docker compose, not only the
# database ones: e.g. SSO_GOOGLE_CLIENT_ID, APP_PORT, POLICY. Real secrets may live here because
# .env is git-ignored and skipped by the secret-leak scan (see .gitleaks.toml).
ENV_KEYS   := $(shell sed -n 's/^[[:space:]]*\([A-Za-z_][A-Za-z0-9_]*\)[[:space:]]*=.*/\1/p' .env 2>/dev/null)
export $(ENV_KEYS)
DB_PORT    ?= 5432
export DB_PORT
export DB_URL      ?= jdbc:postgresql://localhost:$(DB_PORT)/joballoc
export TEST_DB_URL ?= jdbc:postgresql://localhost:$(DB_PORT)/joballoc_test
JAVA       ?= java
JAVAC      ?= javac
RELEASE    ?= 21
LIBS       := lib/*
MAIN_OUT   := target/classes
TEST_OUT   := target/test-classes
MAIN_SRC   := $(shell find src/main/java -name '*.java')
TEST_SRC   := $(shell find src/test/java -name '*.java')
E2E_PORT   ?= 8099
JAVAC_OPTS := --release $(RELEASE) -Xlint:all -Werror -encoding UTF-8

.PHONY: help verify-libs build test test-int e2e all run package db-up db-down db-reset db-logs db-psql db-setup hash-password clean \
        tools lint policy policy-verify config-template check api-lint \
        db-up-mysql db-down-mysql db-reset-mysql db-mysql test-int-mysql run-mysql e2e-mysql test-int-all \
        run-memory e2e-memory test-int-memory db-status db-migrate db-copy smoke \
        tools-dast secrets sast sbom sca governance dast pentest security audit

help:
	@grep -E '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk -F':.*?## ' '{printf "  %-14s %s\n", $$1, $$2}'

verify-libs: ## Check SHA-256 checksums of the vendored jars (supply-chain integrity)
	@cd lib && (command -v shasum >/dev/null && shasum -a 256 -c ../lib.sha256 || sha256sum -c ../lib.sha256)

build: verify-libs ## Compile the application (warnings are errors)
	@rm -rf $(MAIN_OUT) && mkdir -p $(MAIN_OUT)
	$(JAVAC) $(JAVAC_OPTS) -cp "$(LIBS)" -d $(MAIN_OUT) $(MAIN_SRC)
	@cp -R src/main/resources/. $(MAIN_OUT)/

$(TEST_OUT): build
	@rm -rf $(TEST_OUT) && mkdir -p $(TEST_OUT)
	$(JAVAC) $(JAVAC_OPTS) -cp "$(MAIN_OUT):$(LIBS)" -d $(TEST_OUT) $(TEST_SRC)

test: $(TEST_OUT) ## Unit tests: domain rules, policies, service contracts, security, architecture
	$(JAVA) -cp "$(TEST_OUT):$(MAIN_OUT):$(LIBS)" org.junit.runner.JUnitCore edu.ooad.joballoc.AllUnitTests

test-int: $(TEST_OUT) ## Integration tests against PostgreSQL (TEST_DB_URL, default joballoc_test)
	$(JAVA) -cp "$(TEST_OUT):$(MAIN_OUT):$(LIBS)" org.junit.runner.JUnitCore edu.ooad.joballoc.AllIntegrationTests

e2e: build ## Browser tests with Playwright + Chrome (starts the app on E2E_PORT, then stops it)
	@cd e2e && ( [ -d node_modules ] || npm install --no-audit --no-fund )
	@mkdir -p target e2e/screenshots
	@APP_PORT=$(E2E_PORT) $(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.App > target/e2e-app.log 2>&1 & echo $$! > target/e2e-app.pid
	@for i in $$(seq 1 40); do curl -sf http://127.0.0.1:$(E2E_PORT)/health >/dev/null && break; sleep 0.5; done
	@cd e2e && BASE_URL=http://127.0.0.1:$(E2E_PORT) npx playwright test; status=$$?; \
	  kill $$(cat ../target/e2e-app.pid) 2>/dev/null; rm -f ../target/e2e-app.pid; exit $$status

# ---------------------------------------------------------------------------------------------
# Quality gates as code: coding conventions (Checkstyle), policy as code (Conftest/Rego), config
# Tools are downloaded once into tools/ and verified against tools.sha256 (pinned versions).
# ---------------------------------------------------------------------------------------------
CHECKSTYLE_VERSION := 14.4.0
CONFTEST_VERSION   := 0.71.1
TOOLS              := tools
OS_ARCH            := $(shell uname -s)_$(shell uname -m | sed 's/aarch64/arm64/')
CHECKSTYLE_JAR     := $(TOOLS)/checkstyle-$(CHECKSTYLE_VERSION)-all.jar
CONFTEST_TGZ       := conftest_$(CONFTEST_VERSION)_$(OS_ARCH).tar.gz
CONFTEST           := $(TOOLS)/conftest
GH                 := https://github.com
GITLEAKS_VERSION   := 8.21.2
GITLEAKS_TGZ       := gitleaks_$(GITLEAKS_VERSION)_$(shell uname -s | tr A-Z a-z)_$(shell uname -m | sed -e 's/x86_64/x64/' -e 's/aarch64/arm64/').tar.gz
GITLEAKS           := $(TOOLS)/gitleaks
ZAP_VERSION        := 2.17.0
ZAP                := $(TOOLS)/ZAP_$(ZAP_VERSION)/zap.sh
# SAST runs Semgrep from its official container image, pinned by digest: the same engine on every laptop
# and in CI (Semgrep publishes no Intel-Mac binaries from 1.183). Needs Docker running, like the database.
# To upgrade: docker buildx imagetools inspect semgrep/semgrep:<version>, take the index digest, run make sast.
SEMGREP_VERSION    := 1.184.0
SEMGREP_IMAGE      := semgrep/semgrep@sha256:cc1b68266f267878b39739373ea9985b9a78e5d64497dd875c8de100b22695ed
SEMGREP            := docker run --rm --user "$$(id -u):$$(id -g)" -e HOME=/tmp -e SEMGREP_ENABLE_VERSION_CHECK=0 \
                      -v "$(CURDIR):/src" -w /src $(SEMGREP_IMAGE) semgrep
SECURITY_OUT       := target/security
DAST_PORT          ?= 8098

$(CHECKSTYLE_JAR):
	@mkdir -p $(TOOLS)
	curl -fsSL -o $@ $(GH)/checkstyle/checkstyle/releases/download/checkstyle-$(CHECKSTYLE_VERSION)/checkstyle-$(CHECKSTYLE_VERSION)-all.jar
	@cd $(TOOLS) && grep " $(notdir $@)$$" ../tools.sha256 | (command -v shasum >/dev/null && shasum -a 256 -c - || sha256sum -c -)

$(CONFTEST):
	@mkdir -p $(TOOLS)
	curl -fsSL -o $(TOOLS)/$(CONFTEST_TGZ) $(GH)/open-policy-agent/conftest/releases/download/v$(CONFTEST_VERSION)/$(CONFTEST_TGZ)
	@cd $(TOOLS) && grep " $(CONFTEST_TGZ)$$" ../tools.sha256 | (command -v shasum >/dev/null && shasum -a 256 -c - || sha256sum -c -)
	tar -xzf $(TOOLS)/$(CONFTEST_TGZ) -C $(TOOLS) conftest && rm $(TOOLS)/$(CONFTEST_TGZ)

$(GITLEAKS):
	@mkdir -p $(TOOLS)
	curl -fsSL -o $(TOOLS)/$(GITLEAKS_TGZ) $(GH)/gitleaks/gitleaks/releases/download/v$(GITLEAKS_VERSION)/$(GITLEAKS_TGZ)
	@cd $(TOOLS) && grep " $(GITLEAKS_TGZ)$$" ../tools.sha256 | (command -v shasum >/dev/null && shasum -a 256 -c - || sha256sum -c -)
	tar -xzf $(TOOLS)/$(GITLEAKS_TGZ) -C $(TOOLS) gitleaks && rm $(TOOLS)/$(GITLEAKS_TGZ)

$(ZAP):
	@mkdir -p $(TOOLS)
	curl -fsSL -o $(TOOLS)/ZAP_$(ZAP_VERSION)_Crossplatform.zip $(GH)/zaproxy/zaproxy/releases/download/v$(ZAP_VERSION)/ZAP_$(ZAP_VERSION)_Crossplatform.zip
	@cd $(TOOLS) && grep " ZAP_$(ZAP_VERSION)_Crossplatform.zip$$" ../tools.sha256 | (command -v shasum >/dev/null && shasum -a 256 -c - || sha256sum -c -)
	cd $(TOOLS) && unzip -q ZAP_$(ZAP_VERSION)_Crossplatform.zip && rm ZAP_$(ZAP_VERSION)_Crossplatform.zip

semgrep-image: ## Check Docker and pull the pinned Semgrep image (semgrep $(SEMGREP_VERSION))
	@docker info >/dev/null 2>&1 || { echo "SAST needs Docker: start Docker Desktop (Semgrep runs from its pinned container image)"; exit 1; }
	@docker image inspect $(SEMGREP_IMAGE) >/dev/null 2>&1 || docker pull $(SEMGREP_IMAGE)
	@docker run --rm -v "$(CURDIR):/src" $(SEMGREP_IMAGE) true >/dev/null 2>&1 || { \
	  echo "SAST: Docker cannot mount $(CURDIR)."; \
	  echo "  Docker Desktop: Settings > Resources > File sharing > add this folder (or a parent such as $$HOME), then Apply & restart."; \
	  exit 1; }

tools: $(CHECKSTYLE_JAR) $(CONFTEST) $(GITLEAKS) semgrep-image ## Download the pinned gate tools into tools/ (checked against tools.sha256)

tools-dast: $(ZAP) ## Download the pinned OWASP ZAP (about 200 MB) for make dast

lint: $(CHECKSTYLE_JAR) ## Coding conventions: Checkstyle with config/checkstyle/checkstyle.xml (0 violations allowed)
	$(JAVA) -Dconfig_loc=config/checkstyle -jar $(CHECKSTYLE_JAR) -c config/checkstyle/checkstyle.xml src/main/java src/test/java

policy-verify: $(CONFTEST) ## Unit tests of the Rego policies themselves (policy/*_test.rego)
	$(CONFTEST) verify --policy policy

policy: $(CONFTEST) $(TEST_OUT) policy-verify ## Policy as code: compose.yaml, config/*.env.example, access-control facts
	$(CONFTEST) test compose.yaml --policy policy --namespace compose
	$(CONFTEST) test config/app.env.example config/prod.env.example --parser dotenv --policy policy --namespace config
	$(JAVA) -cp "$(TEST_OUT):$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.governance.PolicyInputExport target/policy-input/access.json
	$(CONFTEST) test target/policy-input/access.json --policy policy --namespace access

api-lint: ## OpenAPI contract lint: Spectral with api/.spectral.yaml (needs Node.js 18+; pinned by api/package-lock.json)
	@cd api && ( [ -d node_modules ] || npm ci --no-audit --no-fund )
	cd api && npx spectral lint ../src/main/resources/api/openapi.yaml --ruleset .spectral.yaml --fail-severity=warn

config-template: build ## Regenerate config/app.env.example from the Setting enum (configuration as code)
	$(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.config.ConfigTemplate config/app.env.example

check: lint test policy api-lint secrets sast governance ## Quality gate without a database: conventions, tests, policies, API contract, secrets, SAST, governance

all: lint test test-int policy api-lint e2e ## Every quality gate and test suite

run: build ## Start the app (configure with DB_URL, DB_USER, DB_PASSWORD, APP_PORT; see docs/runbook.md)
	$(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.App

package: build ## Build target/joballoc.jar (run with: java -jar target/joballoc.jar, lib/ alongside)
	@printf 'Main-Class: edu.ooad.joballoc.App\nClass-Path: %s\n' "$$(cd lib && ls *.jar | grep -vE 'junit|hamcrest' | sed 's|^|../lib/|' | tr '\n' ' ')" > target/MANIFEST.MF
	jar --create --file target/joballoc.jar --manifest target/MANIFEST.MF -C $(MAIN_OUT) .
	@echo "Built target/joballoc.jar"

db-up: ## Start PostgreSQL 16 in Docker, wait until ready, create the test database (recommended)
	docker compose up -d --wait db
	@docker compose exec -T db psql -U joballoc -d joballoc -tAc "select 1 from pg_database where datname = 'joballoc_test'" | grep -q 1 \
	  || docker compose exec -T db psql -U joballoc -d joballoc -v ON_ERROR_STOP=1 < docker/initdb/postgresql/01-create-test-database.sql
	@echo "PostgreSQL ready on 127.0.0.1:$${DB_PORT:-5432} (databases joballoc, joballoc_test)"

db-down: ## Stop the PostgreSQL container (data is kept)
	docker compose stop db

db-reset: ## Delete the container AND its data volume, then start a fresh database
	docker compose down -v
	$(MAKE) db-up

db-logs: ## Follow the PostgreSQL container log
	docker compose logs -f db

db-psql: ## Open psql inside the container
	docker compose exec db psql -U joballoc -d joballoc

# ---- MySQL (optional): the same app and tests against MySQL 8.4 -----------------------------------
MYSQL_URL      ?= jdbc:mysql://localhost:$${MYSQL_PORT:-3306}/joballoc
MYSQL_TEST_URL ?= jdbc:mysql://localhost:$${MYSQL_PORT:-3306}/joballoc_test

db-up-mysql: ## Start MySQL 8.4 in Docker (profile "mysql"), wait until ready, create the test database
	docker compose --profile mysql up -d --wait mysql
	@docker compose exec -T mysql sh -c 'MYSQL_PWD="$$MYSQL_ROOT_PASSWORD" mysql -uroot' < docker/initdb/mysql/01-create-test-database.sql
	@echo "MySQL ready on 127.0.0.1:$${MYSQL_PORT:-3306} (databases joballoc, joballoc_test)"

db-down-mysql: ## Stop the MySQL container (data is kept)
	docker compose --profile mysql stop mysql

db-reset-mysql: ## Delete the MySQL container AND its data volume, then start fresh
	docker compose --profile mysql rm -sf mysql
	-docker volume rm joballoc_joballoc-mysql-data
	$(MAKE) db-up-mysql

db-mysql: ## Open the mysql client inside the container as the app user
	docker compose exec mysql sh -c 'MYSQL_PWD="$$MYSQL_PASSWORD" mysql -u"$$MYSQL_USER" joballoc'

test-int-mysql: ## Integration tests against MySQL (needs db-up-mysql)
	TEST_DB_URL="$(MYSQL_TEST_URL)" $(MAKE) test-int

test-int-all: test-int test-int-mysql test-int-memory ## Integration tests on PostgreSQL, MySQL and in-memory storage (both databases up)

run-mysql: ## Start the app on MySQL (needs db-up-mysql)
	DB_URL="$(MYSQL_URL)" $(MAKE) run

e2e-mysql: ## Browser tests with the app running on MySQL (needs db-up-mysql)
	DB_URL="$(MYSQL_URL)" $(MAKE) e2e

# ---- Security and governance gates (SECURITY.md; evidence: make audit) -------------------------
secrets: $(GITLEAKS) ## Secret-leak gate: keys, passwords, tokens, private keys (Gitleaks + .gitleaks.toml), with a self-test
	@mkdir -p $(SECURITY_OUT)
	$(GITLEAKS) dir . --config .gitleaks.toml --no-banner --redact --exit-code 1 --report-format json --report-path $(SECURITY_OUT)/secrets.json
	security/secrets-selftest.sh $(GITLEAKS)

sast: semgrep-image ## SAST: Semgrep with the project's rules (security/semgrep), fails on any finding
	@mkdir -p $(SECURITY_OUT)
	$(SEMGREP) --test --metrics=off security/semgrep/
	$(SEMGREP) scan --config security/semgrep/java-security.yml --metrics=off --error --quiet \
	  --json-output=$(SECURITY_OUT)/sast.json src/main/java
	@# A file Semgrep cannot parse is not scanned at all: treat parse errors as a failed gate, not a pass.
	@python3 -c "import json,sys; e=json.load(open('$(SECURITY_OUT)/sast.json'))['errors']; \
	  [print('SAST blind spot:', x.get('path') or (x.get('spans') or [{}])[0].get('file'), '-', x.get('message','').splitlines()[0]) for x in e]; \
	  sys.exit(1 if e else 0)"

sbom: ## CycloneDX SBOM of every third-party component (target/security/sbom.cdx.json)
	python3 security/sbom.py

sca: sbom ## SCA: known vulnerabilities in dependencies (OSV); fails on unaccepted HIGH/CRITICAL (needs network)
	python3 security/sca.py

governance: sbom $(CONFTEST) ## Governance as code: pinning, SBOM coverage, licences, attribution, required files, migrations
	$(CONFTEST) test $(SECURITY_OUT)/governance.json --policy policy --namespace governance

dast: build $(ZAP) ## DAST: OWASP ZAP (spider, passive + active scan of the UI and the OpenAPI) against the app on memory:
	@mkdir -p $(SECURITY_OUT)/zap
	@DB_URL=memory: APP_PORT=$(DAST_PORT) $(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.App > $(SECURITY_OUT)/dast-app.log 2>&1 & echo $$! > target/dast-app.pid
	@for i in $$(seq 1 40); do curl -sf http://127.0.0.1:$(DAST_PORT)/health >/dev/null && break; sleep 0.5; done
	@sed -e 's#$${DAST_PORT}#$(DAST_PORT)#g' -e 's#$${APP_DIR}#$(CURDIR)#g' security/zap/plan.yaml > $(SECURITY_OUT)/zap/plan.yaml
	@# Scan copy of the contract without the licence block: ZAP's parser wrongly rejects a valid 3.1 licence.
	@sed -e '/^  license:/,/^    identifier:/d' src/main/resources/api/openapi.yaml > $(SECURITY_OUT)/zap/openapi-scan.yaml
	@$(ZAP) -cmd -dir "$(CURDIR)/$(SECURITY_OUT)/zap-home" -autorun "$(CURDIR)/$(SECURITY_OUT)/zap/plan.yaml"; status=$$?; \
	  kill $$(cat target/dast-app.pid) 2>/dev/null; rm -f target/dast-app.pid; \
	  python3 security/zap/summary.py $(SECURITY_OUT)/zap/zap-report.json; exit $$status

pentest: build ## Pen test: scripted OWASP WSTG probes against the app on memory: (security/pentest)
	@mkdir -p $(SECURITY_OUT)
	@DB_URL=memory: APP_PORT=$(DAST_PORT) $(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.App > $(SECURITY_OUT)/pentest-app.log 2>&1 & echo $$! > target/pentest-app.pid
	@for i in $$(seq 1 40); do curl -sf http://127.0.0.1:$(DAST_PORT)/health >/dev/null && break; sleep 0.5; done
	@python3 security/pentest/pentest.py http://127.0.0.1:$(DAST_PORT) $(SECURITY_OUT)/pentest.json; status=$$?; \
	  kill $$(cat target/pentest-app.pid) 2>/dev/null; rm -f target/pentest-app.pid; exit $$status

security: secrets sast sca governance dast pentest ## Every security gate (needs network for sca, ZAP for dast)

audit: ## Audit as code: run every control in audit/controls.json and write an evidence pack to target/audit/
	python3 audit/run_audit.py $(if $(AUDIT_SKIP),--skip $(AUDIT_SKIP))

# ---- In-memory storage (no database): DB_URL=memory: ---------------------------------------------
run-memory: ## Start the app with in-memory storage: no Docker, data is lost when the app stops
	DB_URL=memory: $(MAKE) run

e2e-memory: ## Browser tests with the app on in-memory storage (no database needed)
	DB_URL=memory: $(MAKE) e2e

test-int-memory: ## HTTP/API integration tests on in-memory storage (SQL-specific tests are skipped)
	TEST_DB_URL=memory: $(MAKE) test-int

# ---- Rollout tooling (see docs/runbook.md "Release and rollout") ---------------------------------
TOOL = $(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.tool.DatabaseTool

db-status: build ## Show applied / pending / changed migrations for DB_URL; exit 1 if the history is invalid
	$(TOOL) status

db-migrate: build ## Apply pending migrations for DB_URL without starting the app (rollout step)
	$(TOOL) migrate

db-copy: build ## Copy all data from DB_URL to TARGET_DB_URL (TARGET_DB_URL=memory: is a dry run)
	$(TOOL) copy

smoke: ## Post-deploy check: /health must answer UP (APP_PORT, default 8080)
	@body=$$(curl -fsS http://127.0.0.1:$${APP_PORT:-8080}/health) && echo "$$body" && echo "$$body" | grep -q '"status":"UP"' \
	  && echo "SMOKE OK" || { echo "SMOKE FAILED"; exit 1; }

db-setup: ## Without Docker: create role joballoc and databases joballoc + joballoc_test (PSQL_ADMIN overrides the admin connection)
	$${PSQL_ADMIN:-psql -h localhost -U postgres} -v ON_ERROR_STOP=1 -c "create role joballoc login password 'joballoc'"
	$${PSQL_ADMIN:-psql -h localhost -U postgres} -v ON_ERROR_STOP=1 -c "create database joballoc owner joballoc"
	$${PSQL_ADMIN:-psql -h localhost -U postgres} -v ON_ERROR_STOP=1 -c "create database joballoc_test owner joballoc"

hash-password: build ## Print a PBKDF2 hash for a new password (reads it from stdin)
	@$(JAVA) -cp "$(MAIN_OUT):$(LIBS)" edu.ooad.joballoc.business.security.PasswordHasher

clean: ## Delete build output
	rm -rf target e2e/test-results e2e/playwright-report
