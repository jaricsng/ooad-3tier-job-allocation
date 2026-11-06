package access

import rego.v1

good := {
	"operations": [
		{"name": "allocate", "role": "SUPERVISOR", "transactional": true},
		{"name": "findPendingJobs", "role": "VIEWER", "transactional": true},
	],
	"routes": [
		{"method": "GET", "path": "/health", "access": "PUBLIC"},
		{"method": "POST", "path": "/api/v1/allocations", "access": "API"},
	],
}

test_compliant_design_passes if count(deny) == 0 with input as good

test_missing_role_is_denied if {
	deny["facade operation purge has no @RequiresRole"] with input as {"operations": [{"name": "purge", "role": null, "transactional": true}], "routes": []}
}

test_viewer_cannot_change_data if {
	deny["operation unassign changes data but requires only VIEWER"] with input as {"operations": [{"name": "unassign", "role": "VIEWER", "transactional": true}], "routes": []}
}

test_write_must_be_transactional if {
	deny["operation allocate changes data but is not @Transactional"] with input as {"operations": [{"name": "allocate", "role": "SUPERVISOR", "transactional": false}], "routes": []}
}

test_unlisted_public_route_is_denied if {
	deny["route GET /admin is PUBLIC but not in the allow-list"] with input as {"operations": [], "routes": [{"method": "GET", "path": "/admin", "access": "PUBLIC"}]}
}

test_unversioned_api_is_denied if {
	deny["API route /api/jobs is not under a version prefix (/api/v1/)"] with input as {"operations": [], "routes": [{"method": "GET", "path": "/api/jobs", "access": "API"}]}
}
