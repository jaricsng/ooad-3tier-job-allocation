#!/usr/bin/env python3
"""Generates a CycloneDX 1.5 SBOM (target/security/sbom.cdx.json) and the governance policy input
(target/security/governance.json) from security/components.csv, lib.sha256 and the source tree.

Usage: python3 security/sbom.py   (make sbom)
"""
import csv
import hashlib
import json
import pathlib
import sys
import uuid
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "target" / "security"


def components():
    rows = [r for r in csv.reader(l for l in (ROOT / "security/components.csv").read_text().splitlines()
                                  if l.strip() and not l.startswith("#"))]
    keys = ["file", "group", "name", "version", "ecosystem", "license", "scope"]
    return [dict(zip(keys, r)) for r in rows]


def pinned_jars():
    pins = {}
    for line in (ROOT / "lib.sha256").read_text().splitlines():
        if line.strip() and not line.startswith("#"):
            digest, name = line.split()
            pins[name] = digest
    return pins


def purl(c):
    if c["ecosystem"] == "maven":
        return f"pkg:maven/{c['group']}/{c['name']}@{c['version']}"
    return f"pkg:{c['ecosystem']}/{c['name']}@{c['version']}"


def main():
    comps, pins = components(), pinned_jars()
    sbom_components = []
    for c in comps:
        path = ROOT / ("lib/" + c["file"] if c["file"].endswith(".jar") else c["file"])
        digest = pins.get(c["file"]) or (hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None)
        entry = {
            "type": "library", "bom-ref": purl(c), "name": c["name"], "version": c["version"], "purl": purl(c),
            "scope": "excluded" if c["scope"] == "test" else "required",
            "licenses": [{"expression": c["license"]}],
            "properties": [{"name": "joballoc:scope", "value": c["scope"]}, {"name": "joballoc:file", "value": c["file"]}],
        }
        if c["group"]:
            entry["group"] = c["group"]
        if digest:
            entry["hashes"] = [{"alg": "SHA-256", "content": digest}]
        sbom_components.append(entry)
    sbom = {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "serialNumber": f"urn:uuid:{uuid.uuid4()}", "version": 1,
        "metadata": {"timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                     "component": {"type": "application", "name": "joballoc", "version": app_version(),
                                   "licenses": [{"license": {"id": "MIT"}}]}},
        "components": sbom_components,
    }
    notices = (ROOT / "THIRD_PARTY_NOTICES.md").read_text()
    ai_doc = ROOT / "docs" / "ai-assisted-engineering.md"
    # GitHub reads .github/ at the repository root, which is the app folder or its parent.
    pr_template = next((d / ".github" / "pull_request_template.md" for d in (ROOT, ROOT.parent)
                        if (d / ".github" / "pull_request_template.md").is_file()), ROOT / ".github" / "pull_request_template.md")
    governance = {
        "components": comps,
        "pinned_jars": sorted(pins),
        "jars_in_lib": sorted(p.name for p in (ROOT / "lib").glob("*.jar")),
        "notices_text": notices,
        "ai_practice_headings": [line.lstrip("#").strip() for line in ai_doc.read_text().splitlines()
                                 if line.startswith("#")] if ai_doc.is_file() else [],
        "pr_template_text": pr_template.read_text() if pr_template.is_file() else "",
        "files": sorted(str(p.relative_to(ROOT)) for p in ROOT.rglob("*")
                        if p.is_file() and not any(part in {"target", "tools", "node_modules", ".git"} for part in p.parts)),
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sbom.cdx.json").write_text(json.dumps(sbom, indent=2))
    (OUT / "governance.json").write_text(json.dumps(governance, indent=2))
    print(f"SBOM: {len(sbom_components)} components -> {OUT / 'sbom.cdx.json'}")
    return 0


def app_version():
    text = (ROOT / "src/main/java/edu/ooad/joballoc/config/AppConfig.java").read_text()
    marker = 'VERSION = "'
    return text[text.index(marker) + len(marker):].split('"')[0] if marker in text else "unknown"


if __name__ == "__main__":
    sys.exit(main())
