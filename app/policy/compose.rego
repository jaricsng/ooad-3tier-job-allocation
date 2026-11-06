# POLICY AS CODE: container rules for compose.yaml (CIS Docker Benchmark ideas, scaled to a classroom app).
# Run: make policy
package compose

import rego.v1

services := input.services

# C1: images use an explicit, non-latest tag (reproducible builds).
deny contains msg if {
	some name, svc in services
	not contains(svc.image, ":")
	msg := sprintf("service %s: image %s has no tag", [name, svc.image])
}

deny contains msg if {
	some name, svc in services
	endswith(svc.image, ":latest")
	msg := sprintf("service %s: image must not use :latest", [name])
}

# C2: a digest pin is better still (supply chain); advisory only, because Docker Hub digests change per platform.
warn contains msg if {
	some name, svc in services
	not contains(svc.image, "@sha256:")
	msg := sprintf("service %s: consider pinning %s by digest (@sha256:...)", [name, svc.image])
}

# C3: no privileged containers.
deny contains msg if {
	some name, svc in services
	svc.privileged == true
	msg := sprintf("service %s: privileged mode is not allowed", [name])
}

# C4: published ports listen on localhost only (the database is never exposed to the network).
deny contains msg if {
	some name, svc in services
	some port in svc.ports
	not startswith(sprintf("%v", [port]), "127.0.0.1:")
	msg := sprintf("service %s: port %v must be bound to 127.0.0.1", [name, port])
}

# C5: data lives in named volumes, not bind mounts of host folders.
deny contains msg if {
	some name, svc in services
	some v in svc.volumes
	source := split(sprintf("%v", [v]), ":")[0]
	regex.match(`^[./~]`, source)
	msg := sprintf("service %s: bind mount %v is not allowed; use a named volume", [name, v])
}

# C6: credentials come from variables, never literals in a committed file.
secret_keys := {"POSTGRES_PASSWORD", "DB_PASSWORD", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD"}

deny contains msg if {
	some name, svc in services
	some key, value in svc.environment
	key in secret_keys
	not startswith(sprintf("%v", [value]), "${")
	msg := sprintf("service %s: %s must come from a variable such as ${DB_PASSWORD}", [name, key])
}

# C7: every service has a health check (Makefile and orchestration wait on it).
deny contains msg if {
	some name, svc in services
	not svc.healthcheck
	msg := sprintf("service %s: healthcheck is required", [name])
}

# C8: processes cannot gain new privileges.
warn contains msg if {
	some name, svc in services
	not "no-new-privileges:true" in object.get(svc, "security_opt", [])
	msg := sprintf("service %s: add security_opt no-new-privileges:true", [name])
}
