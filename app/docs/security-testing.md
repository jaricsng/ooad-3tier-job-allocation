# Security Testing, Governance and Audit as Code

Every security and governance check is code in this repository: versioned, reviewed, with tests of its own, and run by one command. This document explains each gate, what it found, and how to read the evidence.

| Gate | Command | Rules live in | Blocks on |
|---|---|---|---|
| Secret leaks | `make secrets` | `.gitleaks.toml` (built-in rules + project rules) | Any finding outside documented exceptions |
| SAST | `make sast` | `security/semgrep/java-security.yml` (+ rule tests) | Any finding |
| SCA | `make sca` | `security/components.csv`, `security/sca-exceptions.csv` | Unaccepted HIGH/CRITICAL |
| SBOM | `make sbom` | generated: `target/security/sbom.cdx.json` (CycloneDX 1.5) | — |
| Governance | `make governance` | `policy/governance.rego` (+ tests) | Any `deny` |
| Policy | `make policy` | `policy/compose.rego`, `config.rego`, `access.rego` (+ tests) | Any `deny` |
| DAST | `make dast` | `security/zap/plan.yaml` (OWASP ZAP Automation Framework) | Any High alert or plan error |
| Pen test | `make pentest` | `security/pentest/pentest.py` (OWASP WSTG v4.2) | Any failed probe |
| Audit | `make audit` | `audit/controls.json` | Any failed control |

`make check` (before every commit; also the pre-commit hook) includes secrets, SAST and governance. `make security` runs every security gate. `make audit` runs every control and writes the evidence pack.

## 1. Secret-leak gate

- Gitleaks with its built-in rules (cloud keys, tokens, private keys…) plus **project rules**: JDBC URLs carrying a password, non-empty `*_PASSWORD` and `SSO_*_CLIENT_SECRET` values in config files, and password hashes outside the seed data.
- **Exceptions are narrow and explained:** only the two published classroom demo passwords, plus build, tool and third-party folders.
- **Self-test** (`security/secrets-selftest.sh`): each run plants one example of every secret type in a temporary folder. The gate fails unless all are caught and the documented dev default is not flagged.
- Runs in `make check` and in the pre-commit hook, so a secret is stopped before it reaches history.

## 2. SAST (static analysis)

Semgrep runs from its official container image, pinned by digest in `app/Makefile` (`SEMGREP_IMAGE`), so every laptop and CI use the same engine; `make sast` therefore needs Docker running. The gate fails on any finding **and** on any file Semgrep cannot parse (a "SAST blind spot"). Docker must be able to mount the `app` folder; on macOS, if `make sast` reports "Docker cannot mount …", add the folder (or a parent) under Docker Desktop *Settings → Resources → File sharing*.


Ten Semgrep rules for this code base, each tied to OWASP Top 10 2021 and CWE:
- SQL built by concatenation;
- MD5/SHA-1;
- `java.util.Random` for security;
- OS command execution;
- Java deserialization;
- trust-all TLS;
- JDBC URLs that disable TLS;
- hard-coded secrets;
- `printStackTrace`;
- XML parsers without XXE protection.

`security/semgrep/java-security.java` holds a test for every rule: `ruleid` lines must match and `ok` lines must not. The gate fails if a rule stops working. Result on `src/main/java`: **0 findings**, and no `nosemgrep` suppressions.

## 3. SCA, SBOM and governance

- `security/components.csv` is the single list of third-party components. `make sbom` turns it into a CycloneDX SBOM with SHA-256 hashes, licences and package URLs.
- `make sca` queries OSV (GitHub advisories, NVD-derived data and ecosystem feeds) for every component. **Finding F1** below shows the gate working.
- `policy/governance.rego` checks eight rules:
  - **G1:** every jar is pinned;
  - **G2:** every pin is in the SBOM;
  - **G3:** licences are on the approved list (test-only licences for test jars);
  - **G4:** every component is attributed in `THIRD_PARTY_NOTICES.md`;
  - **G5:** copyleft components have their exception explained;
  - **G6:** required files exist (`SECURITY.md`, runbook, user guide, SSO guide, this document, the AI-assisted engineering practice, the pull-request template…);
  - **G7:** both SQL products have the same migration versions;
  - **G8:** the AI-assisted engineering practice keeps its risk register, review checklist and disclosure sections, and the pull-request template asks for AI disclosure.

## 4. DAST (OWASP ZAP)

`make dast` starts the app on in-memory storage, then runs the ZAP plan:
- spider the UI;
- import the OpenAPI contract and authenticate to the API as the demo supervisor;
- passive scan, then active scan;
- HTML and JSON reports;
- fail on any High alert.

Two alerts are lowered to Informational, **only for 127.0.0.1**: "Basic auth over HTTP" and "HTTP only site". The local test instance has no TLS by design, while production is HTTPS-only (policies P1, P3, P7 and P9). A scan of any other host still reports both at full risk.

## 5. Pen test (scripted, OWASP WSTG v4.2)

`security/pentest/pentest.py` runs 18 probes against a running instance:

| WSTG | Probe | Result |
|---|---|---|
| CONF-07/14 | Security headers (CSP without inline script, nosniff, DENY framing, no-referrer, no-store) | Pass |
| INFO-02/08 | No version banner; no stack traces in errors | Pass |
| ATHN-04 | Pages redirect and the API answers 401 without authentication | Pass |
| ATHN-04 | A forged session cookie is rejected | Pass |
| ATHN-02 | Default and weak credentials rejected | Pass |
| SESS-02 | Session cookie `HttpOnly`, `SameSite=Strict`, `Path=/` | Pass |
| SESS-03 | Session fixation: a planted session id is replaced at sign-in | Pass |
| SESS-05 | CSRF on state-changing forms | Pass |
| SESS-05 | **Login CSRF**: a sign-in without the form's token is refused | Pass (after fix F2) |
| SESS-06 | Logout ends the session on the server | Pass |
| ATHZ-02 | A Viewer cannot allocate (API 403, no write buttons) | Pass |
| INPV-05 | SQL injection in query and path parameters | Pass |
| INPV-01 | Reflected XSS (board filters, sign-in form) | Pass |
| ATHZ-01 | Path traversal on static files (5 encodings) | Pass |
| CONF-06 | HTTP method tampering (TRACE, PUT, PATCH, DELETE) | Pass |
| BUSL-01 | Business rule bypass through the API | Pass |
| INPV-11 | Oversized body refused with 413; server stays up | Pass |
| ATHN-03 | Brute-force lockout after 5 failures | Pass |

**The probes can fail:** run against a deliberately insecure server (Python's `http.server`), 12 of 17 probes failed. A scripted probe set is not a substitute for a manual test by a qualified tester before production.

## 6. Findings and their resolution

| # | Found by | Finding | Risk | Resolution | Status |
|---|---|---|---|---|---|
| F1 | SCA | pgjdbc 42.7.4: 3 HIGH (SCRAM PBKDF2 resource use, authentication downgrade); Jackson 2.16.1: 7 HIGH (parser limit bypass, polymorphic type-validator bypass…) | High | Upgraded to pgjdbc **42.7.14** and Jackson **2.22.4**; re-ran every test on all three storages | Fixed |
| F2 | DAST | No anti-CSRF token on the sign-in form (login CSRF, CWE-352) | Medium | Double-submit token: a `JA_LOGIN` cookie (`HttpOnly`, `SameSite=Strict`, `Path=/login`, 10 minutes) plus a hidden field; `POST /login` refuses a mismatch (403); new pen-test probe and integration test | Fixed |
| F3 | DAST | Basic credentials and the site over plain HTTP | High/Medium in the local harness | Production is HTTPS-only (P1, P3, P7, P9; HSTS sent when `COOKIE_SECURE=true`); alerts lowered only for 127.0.0.1, with the reason in the plan | Accepted (local only) |
| F4 | Coverage review | The first DAST plan targeted `/api/v1/api/v1/…`: a green scan that reached no API endpoint | Process | Target URL fixed; reports now checked for the URLs actually scanned | Fixed |
| F5 | Tooling | ZAP's OpenAPI parser rejects a valid OpenAPI 3.1 `license.identifier` | None | The scan uses a copy of the contract without the licence block; the plan still fails on genuine errors | Worked around |
| F6 | CI (first run on GitHub) | SAST crashed before scanning: only `semgrep` was pinned, and a fresh Python 3.12 environment resolved without `pkg_resources`, which semgrep's Python path needs (through opentelemetry). A laptop with an older environment passed | Gate not running = no SAST | `security/requirements.txt` now pins every package to an exact version, including `setuptools` 80.9.0 (the last release with `pkg_resources`); checked on semgrep's Python path (`--legacy`) | Fixed |
| F7 | Audit on macOS | `make dast` printed ZAP's usage text: the repository path contains a space and the Makefile passed it unquoted | DAST not running | Paths quoted in the `dast` recipe; verified from a path with a space | Fixed |
| F8 | Audit on macOS | Pen-test probe WSTG-INPV-11 reported "broken pipe": the server correctly answered 413 after 64 KB and closed, while the probe was still sending 2 MB | False failure (the control works) | The probe declares 2 MB, sends just over the limit, then reads the answer; same check, no race on any OS | Fixed |

| F9 | Dependabot / SAST | (a) Dependabot raised two advisories against `setuptools` 80.9, pinned only because semgrep 1.97 needed `pkg_resources` (F6); single-package bumps of `setuptools`, `opentelemetry-api` and `protobuf` all broke semgrep. (b) While upgrading, Semgrep reported a **parse error** in `IdempotentHandler.java` (pattern-matching `switch`), and `make sast` still passed: a file Semgrep cannot parse is not scanned at all | Vulnerable build dependency; silent SAST blind spot | Semgrep upgraded to 1.184.0. That release has no Intel-Mac build, so Semgrep now runs from its official container image pinned by digest (same engine everywhere; no Python packages for it at all, so no `setuptools`). `make sast` now **fails on any parse error**; the switch rewritten with `instanceof` patterns; coding rule added | Fixed |

Lesson from F6–F8: a gate that crashes or misfires is as bad as no gate. Run the gates on a second machine and in CI before trusting a green result, and read *why* a gate failed before changing it.

## 7. Audit as code

`audit/controls.json` lists every control: its id, the check that proves it, the evidence it leaves, and the standards it supports (OWASP ASVS 4.0.3, ISO/IEC 27001:2022 Annex A, OWASP WSTG; for GOV-04 also ISO/IEC 42001:2023 and the NIST AI RMF). There are 17 controls.

`make audit` runs them all and writes `target/audit/<UTC time>/` containing:
- `report.md`;
- `results.json`;
- one log per control;
- copies of the evidence;
- `MANIFEST.sha256` over every file, so a later change to the pack is detectable (`shasum -a 256 -c MANIFEST.sha256`).

Use `AUDIT_SKIP=network,zap,mysql` on a machine without those; skipped controls are listed with the reason, never silently passed.
