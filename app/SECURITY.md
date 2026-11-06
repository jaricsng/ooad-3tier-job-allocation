# Security Policy

## Supported versions

Only the latest version on the main branch receives security fixes. This is a teaching application;
do not run it with real personal data without a review against your organisation's policies.

## Reporting a vulnerability

Please report suspected vulnerabilities privately to the maintainer (see the repository owner's
contact details), not in a public issue. Include the steps to reproduce, the affected URL or class,
and the impact you expect. You will get an acknowledgement within 5 working days.

## How security is checked

| Gate | Command | Blocks on |
|---|---|---|
| Secret leaks (keys, passwords, tokens, private keys) | `make secrets` (also in `make check` and the pre-commit hook) | Any finding not covered by a documented exception in `.gitleaks.toml` |
| SAST (static analysis) | `make sast` | Any finding of `security/semgrep/java-security.yml` without a justified `nosemgrep` |
| SCA (vulnerable dependencies) | `make sca` | HIGH/CRITICAL advisories without a dated exception in `security/sca-exceptions.csv` |
| Governance (licences, pinning, required files, AI-assisted engineering practice) | `make governance` | Any `deny` in `policy/governance.rego` (G1–G8) |
| DAST (running app) | `make dast` | Any High-risk ZAP alert |
| Pen test (scripted OWASP WSTG probes) | `make pentest` | Any failed probe |
| All of the above, with an evidence pack | `make audit` | Any failed control in `audit/controls.json` |

The design controls (CSRF, CSP, PBKDF2, lockout, parameterised SQL, append-only audit) are described in
`README.md` and `docs/runbook.md` §9. Further reading:

- [`docs/security-testing.md`](docs/security-testing.md): how each gate works, findings F1–F5 and their resolution, audit as code;
- [`docs/sso.md`](docs/sso.md): single sign-on threat model and provider registration;
- [`docs/ai-assisted-engineering.md`](docs/ai-assisted-engineering.md): risks of AI-assisted development, the human review checklist and disclosure rules.
