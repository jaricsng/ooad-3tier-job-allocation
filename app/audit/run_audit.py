#!/usr/bin/env python3
"""Audit as code: runs every control in audit/controls.json and writes an evidence pack:

  target/audit/<UTC timestamp>/
    report.md          human-readable result per control, with the standards it supports
    results.json       machine-readable results (for dashboards or a GRC tool)
    logs/<ID>.log      full output of each check
    evidence/...       copies of the evidence files each control names
    MANIFEST.sha256    SHA-256 of every file above: any later change is detectable

Usage: python3 audit/run_audit.py [--skip network,zap,mysql,database,browser]
Exit code 1 if any control that ran failed. Skipped controls are listed with the reason.
"""
import hashlib
import json
import os
import pathlib
import platform
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parent.parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tool_versions():
    def first_line(cmd):
        try:
            out = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=30)
            return (out.stdout or out.stderr).strip().splitlines()[0]
        except (OSError, IndexError, subprocess.TimeoutExpired):
            return "not available"
    return {"java": first_line(["java", "-version"]), "python": platform.python_version(),
            "os": f"{platform.system()} {platform.release()} {platform.machine()}",
            "tools.sha256": sha256(ROOT / "tools.sha256"), "lib.sha256": sha256(ROOT / "lib.sha256")}


def main(argv):
    skip = set()
    if "--skip" in argv:
        skip = {s.strip() for s in argv[argv.index("--skip") + 1].split(",") if s.strip()}
    catalogue = json.loads((ROOT / "audit/controls.json").read_text())
    started = datetime.now(timezone.utc)
    pack = ROOT / "target/audit" / started.strftime("%Y%m%dT%H%M%SZ")
    (pack / "logs").mkdir(parents=True)
    results = []
    for c in catalogue["controls"]:
        blocked = sorted(set(c["requires"]) & skip)
        if blocked:
            results.append({**c, "status": "SKIPPED", "reason": f"requires {', '.join(blocked)} (skipped by --skip)"})
            print(f"  SKIP  {c['id']:7} {c['title']}")
            continue
        t0 = time.monotonic()
        proc = subprocess.run(c["check"], shell=True, cwd=ROOT, capture_output=True, text=True)
        seconds = round(time.monotonic() - t0, 1)
        (pack / "logs" / f"{c['id']}.log").write_text(f"$ {c['check']}\nexit {proc.returncode}\n\n{proc.stdout}\n{proc.stderr}")
        copied = []
        for ev in c["evidence"]:
            src = ROOT / ev
            if src.is_file():
                dst = pack / "evidence" / ev
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
                copied.append(ev)
            elif src.is_dir():
                shutil.copytree(src, pack / "evidence" / ev, dirs_exist_ok=True)
                copied.append(ev + "/")
        status = "PASS" if proc.returncode == 0 else "FAIL"
        results.append({**c, "status": status, "exit": proc.returncode, "seconds": seconds, "evidence_collected": copied})
        print(f"  {status}  {c['id']:7} {c['title']}  ({seconds}s)")
    finished = datetime.now(timezone.utc)
    summary = {s: sum(1 for r in results if r["status"] == s) for s in ("PASS", "FAIL", "SKIPPED")}
    meta = {"started": started.isoformat(timespec="seconds"), "finished": finished.isoformat(timespec="seconds"),
            "host": platform.node(), "user": os.environ.get("USER", "unknown"), "skip": sorted(skip),
            "versions": tool_versions(), "summary": summary, "frameworks": catalogue["frameworks"]}
    (pack / "results.json").write_text(json.dumps({"meta": meta, "controls": results}, indent=2))
    (pack / "report.md").write_text(report(meta, results))
    manifest = [f"{sha256(p)}  {p.relative_to(pack)}" for p in sorted(pack.rglob("*")) if p.is_file()]
    (pack / "MANIFEST.sha256").write_text("\n".join(manifest) + "\n")
    print(f"Audit: {summary['PASS']} passed, {summary['FAIL']} failed, {summary['SKIPPED']} skipped -> {pack}/report.md")
    return 1 if summary["FAIL"] else 0


def report(meta, results):
    lines = ["# Audit evidence report", "",
             f"Run {meta['started']} to {meta['finished']} on `{meta['host']}` by `{meta['user']}`.", "",
             f"**Result: {meta['summary']['PASS']} passed, {meta['summary']['FAIL']} failed, "
             f"{meta['summary']['SKIPPED']} skipped.**", "",
             "| Control | Result | Check | Standards | Evidence |", "|---|---|---|---|---|"]
    for r in results:
        refs = "; ".join(f"{k} {', '.join(v)}" for k, v in r["refs"].items())
        ev = ", ".join(f"`{e}`" for e in r.get("evidence_collected", [])) or ("—" if r["status"] != "SKIPPED" else r["reason"])
        lines.append(f"| **{r['id']}** {r['title']} | {r['status']} | `{r['check']}` | {refs} | {ev} |")
    lines += ["", "## Environment", ""] + [f"- {k}: `{v}`" for k, v in meta["versions"].items()]
    lines += ["", "## Frameworks", ""] + [f"- {k}: {v}" for k, v in meta["frameworks"].items()]
    lines += ["", "Integrity: `MANIFEST.sha256` lists the SHA-256 of every file in this pack "
              "(`shasum -a 256 -c MANIFEST.sha256` inside the folder verifies it).", ""]
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
