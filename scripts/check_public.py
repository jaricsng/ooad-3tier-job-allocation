"""Gate: nothing private (see private/README.md) is committed to the public repository.

Checks every file git would commit (or, before `git init`, every file not excluded below):
  1. private/ is git-ignored and none of its files would be committed;
  2. the slide decks hold no HTML comments other than Marp directives (speaker notes live in private/);
  3. no file contains a term from private/denylist.txt (one case-sensitive term per line; # for comments).
Exit status 1 on any finding.
"""
import pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKIP_DIRS = {".git", "node_modules", "target", "tools", "build", "private", "coverage", ".sync",
             "test-results", "playwright-report", "screenshots", "venv", ".venv"}
DIRECTIVE = re.compile(r"^\s*_?[A-Za-z]+:\s")


def candidate_files():
    try:
        out = subprocess.run(["git", "-C", str(ROOT), "ls-files", "-co", "--exclude-standard", "-z"],
                             check=True, capture_output=True).stdout.decode()
        return [ROOT / f for f in out.split("\0") if f]
    except (subprocess.CalledProcessError, FileNotFoundError):
        return [p for p in ROOT.rglob("*") if p.is_file() and not SKIP_DIRS & set(p.relative_to(ROOT).parts)
                and p.name not in {".env", ".DS_Store"}]


def main():
    problems = []
    ignore = (ROOT / ".gitignore").read_text().splitlines() if (ROOT / ".gitignore").exists() else []
    if "private/" not in ignore:
        problems.append(".gitignore: 'private/' is not ignored")
    files = candidate_files()
    rel = [f.relative_to(ROOT).as_posix() for f in files]
    problems += [f"{r}: private file would be committed" for r in rel if r.startswith("private/")]
    for deck in sorted((ROOT / "slides").glob("*.md")):
        for m in re.finditer(r"<!--(.*?)-->", deck.read_text(), re.S):
            if not DIRECTIVE.match(m.group(1).strip()):
                line = deck.read_text()[:m.start()].count("\n") + 1
                problems.append(f"{deck.relative_to(ROOT)}:{line}: speaker note in the public deck (run: python3 private/notes.py extract)")
    denylist = ROOT / "private" / "denylist.txt"
    terms = [t.strip() for t in denylist.read_text().splitlines()
             if t.strip() and not t.startswith("#")] if denylist.exists() else []
    for f, r in zip(files, rel):
        try:
            text = f.read_text()
        except (UnicodeDecodeError, OSError):
            continue
        for t in terms:
            if t in text:
                problems.append(f"{r}: contains a denylisted term (see private/denylist.txt)")
                break
    for p in problems:
        print("check-public:", p)
    print(f"check-public: {len(files)} files checked, {len(terms)} denylisted terms, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
