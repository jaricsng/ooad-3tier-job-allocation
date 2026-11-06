## Summary

<!-- What changes and why. Link the issue or requirement. -->

## AI assistance (required; see docs/ai-assisted-engineering.md)

- [ ] No AI assistance was used in this change.
- [ ] AI-assisted: <!-- which parts, e.g. "SSO flow and tests" -->
- [ ] A person reviewed every AI-assisted file against the checklist in docs/ai-assisted-engineering.md section 4.
- [ ] Security-sensitive code (authentication, sessions, cryptography, SQL, input handling) had a second human reviewer.

## Checks

- [ ] `make check` passes locally (conventions, tests, policies, API contract, secrets, SAST, governance).
- [ ] Integration tests run where storage or HTTP behaviour changed (`make test-int-all`).
- [ ] Docs and diagrams updated in the same change (runbook, user guide, architecture, PlantUML).
- [ ] New third-party components are pinned, listed in `security/components.csv` and in `THIRD_PARTY_NOTICES.md`.
- [ ] Migrations exist for every database product, and the rollout is expand/contract (see runbook section 10).
