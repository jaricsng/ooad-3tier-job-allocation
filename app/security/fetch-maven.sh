#!/usr/bin/env bash
# Downloads the newest release of a Maven artifact within a version line into lib/, verifies it against
# Maven Central's published SHA-1 and prints the SHA-256 line for lib.sha256.
# Needs network access to repo1.maven.org (run it on a developer machine, not in a locked-down CI).
#   usage: security/fetch-maven.sh <group> <artifact> <version-prefix>
#   e.g.   security/fetch-maven.sh org.postgresql postgresql 42.7.
set -euo pipefail
group="$1"; artifact="$2"; prefix="$3"
base="https://repo1.maven.org/maven2/${group//.//}/${artifact}"
version=$(curl -fsS "${base}/maven-metadata.xml" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' \
  | grep -E "^${prefix//./\\.}" | grep -viE 'rc|alpha|beta|pr|m[0-9]' | sort -V | tail -1)
[ -n "$version" ] || { echo "no release of ${group}:${artifact} matching ${prefix}" >&2; exit 1; }
jar="${artifact}-${version}.jar"
cd "$(dirname "$0")/../lib"
curl -fsSO "${base}/${version}/${jar}"
echo "$(curl -fsS "${base}/${version}/${jar}.sha1" | cut -c1-40)  ${jar}" | shasum -a 1 -c - >&2
shasum -a 256 "${jar}"
