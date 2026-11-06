package compose

import rego.v1

ok_service := {
	"image": "postgres:16-alpine",
	"ports": ["127.0.0.1:${DB_PORT:-5432}:5432"],
	"volumes": ["data:/var/lib/postgresql/data"],
	"environment": {"POSTGRES_PASSWORD": "${DB_PASSWORD:-joballoc}"},
	"healthcheck": {"test": ["CMD", "true"]},
	"security_opt": ["no-new-privileges:true"],
}

test_compliant_service_passes if count(deny) == 0 with input as {"services": {"db": ok_service}}

test_latest_tag_denied if {
	count(deny) == 1 with input as {"services": {"db": object.union(ok_service, {"image": "postgres:latest"})}}
}

test_public_port_denied if {
	deny["service db: port 5432:5432 must be bound to 127.0.0.1"] with input as {"services": {"db": object.union(ok_service, {"ports": ["5432:5432"]})}}
}

test_bind_mount_denied if {
	count(deny) == 1 with input as {"services": {"db": object.union(ok_service, {"volumes": ["./pgdata:/var/lib/postgresql/data"]})}}
}

test_literal_password_denied if {
	deny["service db: POSTGRES_PASSWORD must come from a variable such as ${DB_PASSWORD}"] with input as {"services": {"db": object.union(ok_service, {"environment": {"POSTGRES_PASSWORD": "secret"}})}}
}

test_privileged_denied if {
	count(deny) == 1 with input as {"services": {"db": object.union(ok_service, {"privileged": true})}}
}
