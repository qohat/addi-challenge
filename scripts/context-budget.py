#!/usr/bin/env python3
"""Context budgets: enforce line limits, and report measured session context.

    scripts/context-budget.py            # check budgets, exit 1 on breach
    scripts/context-budget.py --measured # first-request context per session
"""

import json, os, pathlib, sys

# Budgets track how often a file is loaded, not how important it is.
#
#   Layer 0  re-read on EVERY request  -> 40   (the strict one)
#   Layer 1  read once per session     -> 120
#   Layer 2  read once per session     -> 150  (task-specific, so not waste)
#
# A SKILL.md straddles two layers: its frontmatter description is layer 0,
# because it is what decides whether the skill gets invoked at all, while the
# body is layer 1 and only loads on invocation. Budget the body; keep the
# description short for the same reason CLAUDE.md is short. See AGENTS.md.
#
BUDGETS = {
    "CLAUDE.md":                40,   # layer 0
    "AGENTS.md":               120,   # layer 1
    "CONTRIBUTING.md":         120,   # layer 1
    "docs/PROJECT_CONTEXT.md": 120,   # layer 1
}
SKILL_BUDGET = 120                    # layer 1, one per .claude/skills/*/
SPEC_BUDGET = 150                     # layer 2

BUDGETS.update({str(p): SKILL_BUDGET
                for p in pathlib.Path(".claude/skills").glob("*/SKILL.md")})


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


def project_dir(repo):
    """Session logs, resolved by candidate. Same logic as scripts/cost.py.

    CLAUDE_CONFIG_DIR is only set inside an agent's own shell, so relying on it
    breaks the script in a plain terminal. Try it, then the known config dirs,
    and take the first that actually holds logs for this repo.
    """
    slug = str(pathlib.Path(repo).resolve()).replace("/", "-")
    configured = os.environ.get("CLAUDE_CONFIG_DIR")
    candidates = [pathlib.Path(configured)] if configured else []
    candidates += [pathlib.Path.home() / ".claude-ans", pathlib.Path.home() / ".claude"]
    for base in candidates:
        if (base / "projects" / slug).is_dir():
            return base / "projects" / slug
    return candidates[0] / "projects" / slug


def measured(repo="."):
    logs = project_dir(repo)
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
