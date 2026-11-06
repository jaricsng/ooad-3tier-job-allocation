#!/usr/bin/env bash
# Self-test of the secret-leak gate: plants one example of each secret type in a temporary folder
# and fails unless Gitleaks (with .gitleaks.toml) catches every one and ignores the dev default.
# The secrets are assembled at run time, so this script itself never contains one.
set -euo pipefail
gitleaks="$1"; dir=$(mktemp -d); report="$dir.json"
trap 'rm -rf "$dir" "$report"' EXIT
pw="Pr0d""Secret!"
printf 'DB_URL=jdbc:postgresql://db:5432/app?user=app&pass''word=%s\n' "$pw" > "$dir/app.env"
printf 'DB_PASS''WORD=%s\n' "$pw" > "$dir/prod.env"
printf 'DB_PASS''WORD=joballoc\n' > "$dir/dev.env"
printf 'SSO_GOOGLE_CLIENT_SEC''RET=GOCSPX-%s\n' "abcdefghijklmnopqrstuv12" > "$dir/sso.env"
printf 'admin,pbkdf2-sha256$210000$%s$%s,SUPERVISOR\n' "abcdefghijklmnopqrstuv==" "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef=" > "$dir/users.csv"
printf -- '-----BEGIN RSA PRIV''ATE KEY-----\nMIIEowIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF8PbnGy0AHB7Lz3bYG0fTo0ykBAbYQhb\n-----END RSA PRIV''ATE KEY-----\n' > "$dir/id_rsa"
printf 'aws_access_key_id = AKIA%s\n' "IOSFODNN7EXAMPLQ" > "$dir/aws.ini"
"$gitleaks" dir "$dir" --config .gitleaks.toml --no-banner --redact --report-format json --report-path "$report" \
  --exit-code 0 >/dev/null 2>&1
found=$(python3 -c 'import json,sys; print(" ".join(sorted({f["RuleID"] for f in json.load(open(sys.argv[1]))})))' "$report")
expected="aws-access-token db-password-assignment jdbc-url-with-password pbkdf2-hash-outside-seed private-key"
echo "caught: $found"
for rule in $expected; do
  case " $found " in *" $rule "*) ;; *) echo "FAIL: rule $rule did not fire"; exit 1 ;; esac
done
grep -q "dev.env" "$report" && { echo "FAIL: the documented dev default was flagged"; exit 1; }
echo "PASS: every planted secret caught; the dev default was not flagged"
