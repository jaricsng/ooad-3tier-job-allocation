#!/usr/bin/env python3
"""Prints the ZAP alerts by risk from the traditional-json report (used by make dast and make audit)."""
import json
import sys

RISK = {"3": "High", "2": "Medium", "1": "Low", "0": "Informational"}


def main(path):
    try:
        report = json.load(open(path))
    except (OSError, ValueError):
        print(f"DAST: no report at {path}")
        return 1
    alerts = [a for site in report.get("site", []) for a in site.get("alerts", [])]
    counts = {name: 0 for name in RISK.values()}
    for a in sorted(alerts, key=lambda a: -int(a["riskcode"])):
        risk = RISK[a["riskcode"]]
        counts[risk] += 1
        print(f"  {risk:13} {a['name'][:70]:70} instances={len(a.get('instances', []))} cwe={a.get('cweid')}")
    print("DAST summary: " + ", ".join(f"{k} {v}" for k, v in counts.items()))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
