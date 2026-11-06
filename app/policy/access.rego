# POLICY AS CODE: access-control rules for the business facade and the HTTP routes.
# Input: target/policy-input/access.json, exported from the real code by PolicyInputExport
# (reflection on AllocationService annotations + the live route table). Run: make policy
package access

import rego.v1

# Facade operations that only read data. Every other operation changes data.
read_prefixes := ["find", "get", "list", "load", "policy"]

is_read(name) if {
	some p in read_prefixes
	startswith(name, p)
}

# Routes that anyone may call without signing in.
public_allowlist := {
	"GET /", "GET /health", "GET /static/{file}", "GET /login", "POST /login",
	# Single sign-on must be reachable before signing in. Each callback is protected by state (single
	# use, bound to the browser by a cookie), nonce and a fully verified ID token; see SsoService.
	"GET /sso/{provider}", "GET /sso/{provider}/callback", "POST /sso/{provider}/callback",
}

# R1: every facade operation declares who may call it (no accidental public business method).
deny contains msg if {
	some op in input.operations
	op.role == null
	msg := sprintf("facade operation %s has no @RequiresRole", [op.name])
}

# R2: operations that change data require SUPERVISOR (least privilege, OWASP A01).
deny contains msg if {
	some op in input.operations
	not is_read(op.name)
	op.role != "SUPERVISOR"
	msg := sprintf("operation %s changes data but requires only %v", [op.name, op.role])
}

# R3: operations that change data run in one transaction (all-or-nothing).
deny contains msg if {
	some op in input.operations
	not is_read(op.name)
	not op.transactional
	msg := sprintf("operation %s changes data but is not @Transactional", [op.name])
}

# R4: only allow-listed routes are public.
deny contains msg if {
	some r in input.routes
	r.access == "PUBLIC"
	key := sprintf("%s %s", [r.method, r.path])
	not key in public_allowlist
	msg := sprintf("route %s is PUBLIC but not in the allow-list", [key])
}

# R5: everything under /api/ uses API access (session or Basic auth, JSON errors).
deny contains msg if {
	some r in input.routes
	startswith(r.path, "/api/")
	r.access != "API"
	msg := sprintf("API route %s %s must use API access, not %s", [r.method, r.path, r.access])
}

# R6: the API is versioned.
deny contains msg if {
	some r in input.routes
	startswith(r.path, "/api/")
	not startswith(r.path, "/api/v1/")
	msg := sprintf("API route %s is not under a version prefix (/api/v1/)", [r.path])
}
