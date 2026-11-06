package config

import rego.v1

dev := {"APP_PROFILE": "dev", "APP_BIND": "127.0.0.1", "COOKIE_SECURE": "false", "APP_DEMO_DATA": "true", "DB_PASSWORD": "joballoc"}

prod := {"APP_PROFILE": "prod", "APP_BIND": "0.0.0.0", "COOKIE_SECURE": "true", "APP_DEMO_DATA": "false", "DB_PASSWORD": "", "DB_URL": "jdbc:postgresql://db.internal:5432/joballoc?sslmode=verify-full", "SSO_BASE_URL": "https://jobs.example.edu"}

test_dev_defaults_pass if count(deny) == 0 with input as dev

test_prod_template_passes if count(deny) == 0 with input as prod

test_public_bind_needs_secure_cookie if {
	deny["APP_BIND=0.0.0.0 listens beyond localhost: COOKIE_SECURE must be true"] with input as object.union(dev, {"APP_BIND": "0.0.0.0"})
}

test_prod_without_demo_data if {
	deny["APP_PROFILE=prod requires APP_DEMO_DATA=false"] with input as object.union(prod, {"APP_DEMO_DATA": "true"})
}

test_prod_password_never_committed if {
	count(deny) == 1 with input as object.union(prod, {"DB_PASSWORD": "S3cret"})
}

test_real_secret_in_dev_file_denied if {
	count(deny) == 1 with input as object.union(dev, {"DB_PASSWORD": "S3cret"})
}

test_prod_postgres_without_verified_tls_is_denied if {
	count(deny) == 1 with input as object.union(prod, {"DB_URL": "jdbc:postgresql://db.internal:5432/joballoc?sslmode=require"})
}

test_prod_mysql_with_verified_tls_passes if {
	count(deny) == 0 with input as object.union(prod, {"DB_URL": "jdbc:mysql://db.internal:3306/joballoc?sslMode=VERIFY_IDENTITY"})
}

test_prod_mysql_without_tls_is_denied if {
	count(deny) == 1 with input as object.union(prod, {"DB_URL": "jdbc:mysql://db.internal:3306/joballoc"})
}

test_dev_needs_no_tls if count(deny) == 0 with input as object.union(dev, {"DB_URL": "jdbc:mysql://localhost:3306/joballoc"})

test_prod_in_memory_is_denied_with_one_clear_message if {
	msgs := deny with input as object.union(prod, {"DB_URL": "memory:"})
	count(msgs) == 1
	some m in msgs
	contains(m, "loses all data")
}

test_dev_in_memory_is_allowed if count(deny) == 0 with input as object.union(dev, {"DB_URL": "memory:"})

test_committed_sso_secret_is_denied if {
	some msg in deny with input as object.union(dev, {"SSO_GOOGLE_CLIENT_SECRET": "GOCSPX-abc"})
	contains(msg, "SSO_GOOGLE_CLIENT_SECRET")
}

test_prod_sso_over_http_is_denied if {
	some msg in deny with input as object.union(prod, {"SSO_BASE_URL": "http://jobs.example.edu"})
	contains(msg, "SSO_BASE_URL")
}
