#!/usr/bin/env python3
"""Context budgets: enforce line limits, and report measured session context.

    scripts/context-budget.py            # check budgets, exit 1 on breach
    scripts/context-budget.py --measured # first-request context per session
"""

import json, pathlib, sys

# Budgets track how often a file is loaded, not how important it is.
#
#   Layer 0  re-read on EVERY request  -> 40   (the strict one)
#   Layer 1  read once per session     -> 120
#   Layer 2  read once per session     -> 150  (task-specific, so not waste)
#
BUDGETS = {
    "CLAUDE.md":                40,   # layer 0
    "AGENTS.md":               120,   # layer 1
    "CONTRIBUTING.md":         120,   # layer 1
    "docs/PROJECT_CONTEXT.md": 120,   # layer 1
}
SPEC_BUDGET = 150                     # layer 2


def check():
    failed = False
    for path, limit in sorted(BUDGETS.items()):
        p = pathlib.Path(path)
        if not p.exists():
            continue
        n = len(p.read_text().splitlines())
        flag = "OVER" if n > limit else "ok"
        if n > limit:
            failed = True
        print(f"{flag:>4}  {n:>4}/{limit:<4} {path}")
    for p in sorted(pathlib.Path("specs").glob("*.md")):
        if p.name == "README.md":
            continue
        n = len(p.read_text().splitlines())
        if n > SPEC_BUDGET:
            failed = True
            print(f"OVER  {n:>4}/{SPEC_BUDGET:<4} {p}")
    return 1 if failed else 0


def measured(repo="."):
    slug = str(pathlib.Path(repo).resolve()).replace("/", "-")
    logs = pathlib.Path.home() / ".claude" / "projects" / slug
    print("| Session | Resident context at first request |")
    print("|---|---:|")
    for path in sorted(logs.glob("*.jsonl")):
        for line in path.read_text(errors="ignore").splitlines():
            try:
                rec = json.loads(line)
            except ValueError:
                continue
            u = (rec.get("message") or {}).get("usage")
            if not u:
                continue
            n = ((u.get("input_tokens") or 0)
                 + (u.get("cache_read_input_tokens") or 0)
                 + (u.get("cache_creation_input_tokens") or 0))
            print(f"| `{path.stem[:8]}` | {n:,} |")
            break
    return 0


if __name__ == "__main__":
    sys.exit(measured() if "--measured" in sys.argv else check())
