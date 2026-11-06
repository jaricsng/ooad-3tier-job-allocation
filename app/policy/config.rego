# POLICY AS CODE: what configuration values are allowed per environment.
# Input: committed .env-style files (config/*.env.example), parsed with --parser dotenv.
# The SHAPE of each value (type, range) is validated in Java (edu.ooad.joballoc.config.Setting);
# this file decides what is ALLOWED. Run: make policy
package config

import rego.v1

profile := object.get(input, "APP_PROFILE", "dev")

loopback := {"127.0.0.1", "localhost", "::1"}

dev_password := "joballoc"

# P1: listening beyond localhost means real users, so cookies must be Secure (and HSTS is sent).
deny contains msg if {
	not input.APP_BIND in loopback
	object.get(input, "COOKIE_SECURE", "false") != "true"
	msg := sprintf("APP_BIND=%s listens beyond localhost: COOKIE_SECURE must be true", [input.APP_BIND])
}

# P2: production never loads demo data.
deny contains msg if {
	profile == "prod"
	object.get(input, "APP_DEMO_DATA", "true") != "false"
	msg := "APP_PROFILE=prod requires APP_DEMO_DATA=false"
}

# P3: production cookies are Secure even on loopback (TLS terminates at a proxy in front).
deny contains msg if {
	profile == "prod"
	object.get(input, "COOKIE_SECURE", "false") != "true"
	msg := "APP_PROFILE=prod requires COOKIE_SECURE=true"
}

# P4: a committed production file never contains the password; it is injected at deploy time.
deny contains msg if {
	profile == "prod"
	object.get(input, "DB_PASSWORD", "") != ""
	msg := "APP_PROFILE=prod: leave DB_PASSWORD empty in the file and inject it from a secret store"
}

# P5: no real secret in any committed file (only the documented dev default is allowed).
deny contains msg if {
	profile == "dev"
	pw := object.get(input, "DB_PASSWORD", dev_password)
	not pw in {dev_password, ""}
	msg := "DB_PASSWORD in a committed file must be the dev default or empty"
}

# P6: production talks to a real database host, not localhost (advisory).
warn contains msg if {
	profile == "prod"
	contains(object.get(input, "DB_URL", ""), "localhost")
	msg := "APP_PROFILE=prod: DB_URL points at localhost"
}

# P7: production encrypts database traffic AND checks the server's certificate (stops a
# man-in-the-middle). Each driver spells this differently, which is exactly why it is a policy:
#   PostgreSQL (pgjdbc): sslmode=verify-full    MySQL (Connector/J): sslMode=VERIFY_IDENTITY
deny contains msg if {
	profile == "prod"
	url := object.get(input, "DB_URL", "")
	url != "memory:"
	not verified_tls(url)
	msg := sprintf("APP_PROFILE=prod: DB_URL must enforce verified TLS (%s)", [url])
}

verified_tls(url) if {
	startswith(url, "jdbc:postgresql://")
	contains(url, "sslmode=verify-full")
}

verified_tls(url) if {
	startswith(url, "jdbc:mysql://")
	contains(url, "sslMode=VERIFY_IDENTITY")
}

# P8: production data must survive a restart: in-memory storage is for demos and tests only.
deny contains msg if {
	profile == "prod"
	object.get(input, "DB_URL", "") == "memory:"
	msg := "APP_PROFILE=prod: DB_URL=memory: loses all data on restart; use PostgreSQL or MySQL"
}

# P9: single sign-on secrets are injected at deploy time, never committed; production SSO runs on HTTPS
# (identity providers require https redirect URIs, and the session it creates must be Secure).
deny contains msg if {
	some key, value in input
	startswith(key, "SSO_")
	endswith(key, "_SECRET")
	value != ""
	msg := sprintf("%s must be empty in a committed file; inject it from a secret store", [key])
}

deny contains msg if {
	profile == "prod"
	not startswith(object.get(input, "SSO_BASE_URL", ""), "https://")
	msg := "APP_PROFILE=prod: SSO_BASE_URL must be an https:// URL"
}
