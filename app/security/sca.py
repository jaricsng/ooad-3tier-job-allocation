#!/usr/bin/env python3
"""Software composition analysis: looks up every SBOM component in the OSV database (osv.dev, which
aggregates GitHub advisories, NVD-derived data and ecosystem feeds) and fails on unaccepted findings.

Gate: any HIGH or CRITICAL vulnerability fails, unless security/sca-exceptions.csv accepts it with a
reason and an expiry date (an expired acceptance fails again). Results: target/security/sca.json.
Usage: python3 security/sca.py   (make sca; needs network access to api.osv.dev)
"""
import csv
import json
import pathlib
import sys
import urllib.request
from datetime import date

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "target" / "security"
API = "https://api.osv.dev/v1"
BLOCKING = {"HIGH", "CRITICAL"}


def post(path, body):
    req = urllib.request.Request(API + path, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def get(path):
    with urllib.request.urlopen(API + path, timeout=30) as r:
        return json.load(r)


def severity(vuln):
    level = (vuln.get("database_specific") or {}).get("severity")
    if level:
        return level.upper().replace("MODERATE", "MEDIUM")
    return "UNKNOWN"


def exceptions():
    path = ROOT / "security/sca-exceptions.csv"
    rows = [r for r in csv.reader(l for l in path.read_text().splitlines() if l.strip() and not l.startswith("#"))]
    return {r[0]: {"component": r[1], "reason": r[2], "expires": r[3]} for r in rows}


def main():
    sbom = json.loads((OUT / "sbom.cdx.json").read_text())
    comps = sbom["components"]
    batch = post("/querybatch", {"queries": [{"package": {"purl": c["purl"]}} for c in comps]})
    accepted, today = exceptions(), date.today().isoformat()
    findings, failures = [], []
    for comp, result in zip(comps, batch["results"]):
        for v in result.get("vulns", []):
            vuln = get("/vulns/" + v["id"])
            level = severity(vuln)
            ex = accepted.get(v["id"])
            status = "open"
            if ex and ex["expires"] >= today:
                status = f"accepted until {ex['expires']}: {ex['reason']}"
            elif ex:
                status = f"acceptance EXPIRED {ex['expires']}"
            finding = {"component": comp["purl"], "id": v["id"], "aliases": vuln.get("aliases", []),
                       "severity": level, "summary": vuln.get("summary", ""), "status": status}
            findings.append(finding)
            if level in BLOCKING and not status.startswith("accepted"):
                failures.append(finding)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sca.json").write_text(json.dumps({"date": today, "components": len(comps), "findings": findings}, indent=2))
    print(f"SCA: {len(comps)} components checked against OSV, {len(findings)} known vulnerabilities")
    for f in findings:
        print(f"  {f['severity']:8} {f['id']:22} {f['component']}  [{f['status']}]  {f['summary'][:70]}")
    if failures:
        print(f"FAIL: {len(failures)} HIGH/CRITICAL not accepted (upgrade, or record a dated exception)")
        return 1
    print("PASS: no unaccepted HIGH or CRITICAL vulnerabilities")
    return 0


if __name__ == "__main__":
    sys.exit(main())
