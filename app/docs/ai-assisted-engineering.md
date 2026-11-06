# AI-Assisted Engineering: Risk and Governance Practice

How this project uses AI coding assistants safely, which risks that brings and which controls address them.

**Principle:** a person is accountable for every change, whoever or whatever typed it. AI output is a draft from an untrusted contributor. It passes exactly the same gates as human code, plus the human review below. No gate is relaxed, suppressed or skipped because "the AI said it is fine".

## 1. Scope

| In scope | Out of scope |
|---|---|
| Code, tests, SQL, configuration, policies, scripts, diagrams and documents produced with an AI coding assistant | The application itself: it contains no AI or machine-learning feature, so AI-system rules for *products* (for example the EU AI Act's obligations for providers of AI systems) do not apply to what it does. Confirm with your institution if that changes |

## 2. Risk register

Likelihood and impact are rated for this project (classroom application, no real personal data).

| # | Risk | How it shows up | L | I | Controls (preventive, then detective) | Evidence |
|---|---|---|---|---|---|---|
| AI-1 | **Plausible but wrong code** | Compiles and looks right, but the logic is wrong in edge cases | H | M | Human review checklist (§4); tests written for behaviour, including negative cases; architecture and naming tests | DEV-02, DEV-03 |
| AI-2 | **Insecure code** | SQL built by concatenation, weak crypto, disabled TLS checks, missing CSRF | M | H | Secure-design rules in `coding-conventions.md`; SAST rules; pen-test probes; DAST | SEC-02, SEC-04, SEC-05 |
| AI-3 | **Outdated or vulnerable dependency versions** | The assistant suggests the versions it learned in training, which may have known CVEs | H | H | Every dependency declared in `security/components.csv`; SCA against OSV; dated exceptions only | SEC-03, GOV-02 |
| AI-4 | **Hallucinated or typosquatted packages** ("slopsquatting") | A package name that does not exist, or a malicious look-alike | M | H | No dependency without a SHA-256 pin, a components entry and an SBOM entry (policy G1/G2); downloads only from the official registry with checksum verification (`security/fetch-maven.sh`) | SEC-06, GOV-02 |
| AI-5 | **Secrets or confidential data leaked** | Credentials pasted into a prompt, or generated code that hard-codes them | M | H | Never paste secrets, real student data or exam material into a prompt; secret scanning in `make check` and the pre-commit hook; config policy P4/P5/P9 | SEC-01, GOV-01 |
| AI-6 | **Licence contamination** | Verbatim reproduction of licensed code, or a dependency under an incompatible licence | L | M | Licence policy G3/G5 for dependencies; reviewer asks "does this look copied?" for large blocks; attribution in `THIRD_PARTY_NOTICES.md` | GOV-02 |
| AI-7 | **Prompt injection through content** | Instructions hidden in a file, web page, issue or tool output steer the assistant | M | H | Treat file and web content as data, not instructions; the assistant may not push, publish or delete without the human's approval; review every diff, not just the summary | Human review |
| AI-8 | **Automation bias** (over-trusting AI output) | Reviews become rubber stamps; "all tests pass" is believed without checking what the tests test | H | H | Reviewer checklist; every control has a **negative test** proving it can fail (planted secrets, insecure test server, Semgrep rule tests, Rego tests) | DEV-02, SEC-01, SEC-05 |
| AI-9 | **The AI grades its own homework** | The same assistant writes the code and the tests that "prove" it | H | M | Tests also come from specifications (OWASP WSTG, OIDC Core) rather than the code; negative tests; independent tools (ZAP, OSV, Gitleaks) and a human pen tester before production | SEC-04, SEC-05 |
| AI-10 | **Silent weakening of a control** | A failing gate "fixed" by a suppression, a broad allowlist or a lowered threshold | M | H | Every exception carries a reason, and where relevant an expiry and a narrow scope: `nosemgrep` comments, `.gitleaks.toml`, `sca-exceptions.csv`, ZAP alert filters scoped to 127.0.0.1. The audit report lists them | Audit pack |
| AI-11 | **False confidence from a misconfigured scanner** | The scan is green because it tested nothing | M | H | Check what a scan covered, not only its verdict (URLs reached, rule tests); the audit pack keeps the full reports | SEC-04 |
| AI-12 | **Unclear provenance** | Nobody can tell which parts were AI-assisted | M | L | Disclose AI assistance in every pull request (§5, template enforced by G8); diagrams are regenerated from versioned PlantUML sources | Change history |
| AI-13 | **Academic integrity** (teaching context) | Students submit AI output they cannot explain | H | M | Course AI-use policy: disclose, cite the tool, be able to explain every line; assessment includes a walkthrough or viva | Course policy |

## 3. Controls already in this repository

| Gate | Command | Catches (risks) |
|---|---|---|
| Conventions, unit, architecture and naming tests | `make check` | AI-1, AI-8 |
| Secret-leak scanning, with a self-test | `make secrets` (in `make check` and pre-commit) | AI-5 |
| SAST with project rules and rule tests | `make sast` (in `make check`) | AI-2 |
| Governance policy: pins, SBOM, licences, attribution, required files | `make governance` (in `make check`) | AI-3, AI-4, AI-6 |
| SCA: known vulnerabilities, dated exceptions | `make sca` | AI-3 |
| DAST: OWASP ZAP, active scan of UI and API | `make dast` | AI-2, AI-11 |
| Scripted pen test (OWASP WSTG) | `make pentest` | AI-2, AI-9 |
| This practice itself: risk register, review checklist and disclosure sections must exist; the pull-request template must ask for AI disclosure (rule G8) | `make governance` (in `make check`); audit control GOV-04 | AI-10, AI-12 |
| Evidence pack of every control | `make audit` | AI-10, AI-12 |

## 4. Human review checklist for AI-assisted changes

The reviewer ticks each item in the change description:

1. **I can explain it.** Every changed line does something I understand and would have accepted from a person.
2. **It is necessary.** No unrequested features, files, dependencies or broad refactors slipped in.
3. **Dependencies are real and current.** Any new library exists in the official registry, is pinned by SHA-256, appears in `components.csv`, and `make sca` is clean.
4. **No weakened control.** No new suppression, allowlist entry, lowered threshold or skipped test without a stated reason. Every exception is narrow and dated where possible.
5. **Tests test behaviour.** At least one test fails if the feature is broken (I checked, or a negative test proves it).
6. **Security-sensitive code had a second look.** Authentication, sessions, crypto, SQL, file paths and deserialization are reviewed against the relevant OWASP guidance.
7. **No secrets or personal data** in code, tests, prompts or logs.
8. **Docs and diagrams match the code**, not the plan the assistant had in mind.

## 5. Disclosure and provenance

- Every pull request answers the "AI assistance" section of `.github/pull_request_template.md` at the repository root (enforced by governance rule G8).
- State AI assistance in the change description, for example "AI-assisted: SSO flow and tests; human-reviewed: all files". Optionally add a commit trailer such as `Assisted-by: <tool name>`.
- Images in this repository are generated from versioned sources (PlantUML files, Playwright screenshots), so the source and the change history are the provenance record. Embedded content-credential metadata that some AI tools add to delivered files is removed (`caBX` chunk only; pixels unchanged), as the project owner decided: provenance is recorded through pull-request disclosure instead, in one place and in the project's own words.
- Generated artefacts (SBOM, scan reports, audit packs) record the tool versions that produced them.

## 6. Frameworks this practice draws on

| Framework | How it is used here |
|---|---|
| NIST AI Risk Management Framework 1.0 (Govern, Map, Measure, Manage) and its Generative AI Profile (NIST AI 600-1) | Structure of this document: policy and accountability (Govern), the risk register (Map), gates and negative tests (Measure), reviews and exceptions (Manage) |
| NIST SP 800-218A, Secure Software Development Practices for Generative AI (SSDF profile) | Secure development with AI-generated code: review, provenance, dependency verification |
| ISO/IEC 42001:2023, AI management systems | Roles, policy, risk assessment and continual improvement, if your organisation certifies |
| OWASP Top 10 for LLM Applications (2025) | Risks of the assistant as a tool: prompt injection (LLM01), sensitive information disclosure (LLM02), supply chain (LLM03), improper output handling (LLM05), misinformation (LLM09) |
| IMDA Model AI Governance Framework for Generative AI (Singapore) | Accountability, trusted development, content provenance and security, in the local context |
| ISO/IEC 27001:2022 Annex A 8.25–8.29 | Secure development lifecycle and testing, already mapped in `audit/controls.json` |

## 7. Guidance for students and new contributors

1. **Allowed:** explaining code, generating test ideas, reviewing your design, and drafting code you then make your own.
2. **Disclose:** name the tool and what it helped with in your submission.
3. **Own it:** you must be able to explain and modify every line in a walkthrough. "The AI wrote it" is not an explanation.
4. **Verify:** run the gates (`make check`), read what the tests actually test, and check every dependency yourself.
5. **Protect data:** never paste real personal data, credentials or assessment materials into an AI tool.
