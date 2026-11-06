package governance

import rego.v1

ok := {
	"components": [
		{"file": "a-1.jar", "name": "postgresql", "license": "BSD-2-Clause", "scope": "runtime"},
		{"file": "junit-4.jar", "name": "junit", "license": "EPL-1.0", "scope": "test"},
	],
	"pinned_jars": ["a-1.jar", "junit-4.jar"],
	"jars_in_lib": ["a-1.jar", "junit-4.jar"],
	"notices_text": "pgjdbc ... JUnit ...",
	"ai_practice_headings": ["2. Risk register", "4. Human review checklist for AI-assisted changes", "5. Disclosure and provenance"],
	"pr_template_text": "- [ ] AI-assisted: which parts",
	"files": [
		"LICENSE", "README.md", "SECURITY.md", "THIRD_PARTY_NOTICES.md", ".gitleaks.toml",
		"docs/runbook.md", "docs/user-guide.md", "docs/architecture.md", "security/components.csv",
		"docs/sso.md", "docs/security-testing.md", "docs/ai-assisted-engineering.md",
			"src/main/resources/db/migration/postgresql/V1__x.sql", "src/main/resources/db/migration/mysql/V1__x.sql",
	],
}

test_compliant_project_passes if count(deny) == 0 with input as ok

test_unpinned_jar_is_denied if {
	count(deny) > 0 with input as object.union(ok, {"jars_in_lib": ["a-1.jar", "junit-4.jar", "rogue.jar"]})
}

test_undeclared_component_is_denied if {
	some msg in deny with input as object.union(ok, {"pinned_jars": ["a-1.jar", "junit-4.jar", "b.jar"], "jars_in_lib": ["a-1.jar", "junit-4.jar", "b.jar"]})
	startswith(msg, "G2")
}

test_unapproved_licence_is_denied if {
	bad := object.union(ok, {"components": [{"file": "a-1.jar", "name": "postgresql", "license": "AGPL-3.0-only", "scope": "runtime"}, {"file": "junit-4.jar", "name": "junit", "license": "EPL-1.0", "scope": "test"}]})
	some msg in deny with input as bad
	startswith(msg, "G3")
}

test_test_only_licence_in_runtime_is_denied if {
	bad := object.union(ok, {"components": [{"file": "a-1.jar", "name": "postgresql", "license": "BSD-2-Clause", "scope": "runtime"}, {"file": "junit-4.jar", "name": "junit", "license": "EPL-1.0", "scope": "runtime"}]})
	some msg in deny with input as bad
	startswith(msg, "G3")
}

test_missing_attribution_is_denied if {
	some msg in deny with input as object.union(ok, {"notices_text": "pgjdbc only"})
	startswith(msg, "G4")
}

test_missing_security_policy_is_denied if {
	files := [f | some f in ok.files; f != "SECURITY.md"]
	some msg in deny with input as object.union(ok, {"files": files})
	msg == "G6: required file SECURITY.md is missing"
}

test_migration_missing_for_one_product_is_denied if {
	files := array.concat(ok.files, ["src/main/resources/db/migration/postgresql/V2__y.sql"])
	some msg in deny with input as object.union(ok, {"files": files})
	startswith(msg, "G7")
}

test_ai_practice_without_risk_register_is_denied if {
	bad := object.union(ok, {"ai_practice_headings": ["4. Human review checklist", "5. Disclosure and provenance"]})
	some msg in deny with input as bad
	startswith(msg, "G8")
}

test_pr_template_without_ai_disclosure_is_denied if {
	some msg in deny with input as object.union(ok, {"pr_template_text": "## Summary"})
	startswith(msg, "G8")
}
