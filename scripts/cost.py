#!/usr/bin/env python3
"""Cost of a spec, computed from the claude-code session logs.

The logs write one `assistant` record per content block of the same API
response, each carrying that call's full usage. Summing them double counts.
Dedup by `requestId` first.

    scripts/cost.py                       # whole project, every session
    scripts/cost.py --session 3d0054fe    # one session (id prefix)
    scripts/cost.py --since 2026-08-15T18:39:20Z --until 2026-08-15T18:47:13Z
"""

import argparse, json, pathlib, sys
from collections import defaultdict

# Per-MTok list prices. Cache read is 0.1x input, 5m cache write 1.25x,
# 1h cache write 2x -- derived, so only these two numbers need maintaining.
RATES = {
    "claude-fable-5":    (10.00, 50.00),
    "claude-opus-5":     ( 5.00, 25.00),
    "claude-opus-4-8":   ( 5.00, 25.00),
    "claude-sonnet-5":   ( 3.00, 15.00),
    "claude-sonnet-4-6": ( 3.00, 15.00),
    "claude-haiku-4-5":  ( 1.00,  5.00),
}

FIELDS = ("input", "output", "cache_read", "cache_write_5m", "cache_write_1h")


def project_dir(repo):
    slug = str(pathlib.Path(repo).resolve()).replace("/", "-")
    return pathlib.Path.home() / ".claude" / "projects" / slug


def collect(logs, session, since, until):
    totals = defaultdict(lambda: dict.fromkeys(FIELDS, 0))
    requests = defaultdict(int)
    seen, skipped = set(), 0

    for path in sorted(logs.glob("*.jsonl")):
        if session and not path.stem.startswith(session):
            continue
        for line in path.read_text(errors="ignore").splitlines():
            try:
                rec = json.loads(line)
            except ValueError:
                continue
            usage = (rec.get("message") or {}).get("usage")
            if not usage:
                continue

            stamp = rec.get("timestamp") or ""
            if (since and stamp < since) or (until and stamp > until):
                continue

            rid = rec.get("requestId")
            if rid:
                if rid in seen:
                    skipped += 1
                    continue
                seen.add(rid)

            model = (rec.get("message") or {}).get("model", "unknown")
            created = usage.get("cache_creation") or {}
            b = totals[model]
            b["input"]          += usage.get("input_tokens", 0) or 0
            b["output"]         += usage.get("output_tokens", 0) or 0
            b["cache_read"]     += usage.get("cache_read_input_tokens", 0) or 0
            b["cache_write_5m"] += created.get("ephemeral_5m_input_tokens", 0) or 0
            b["cache_write_1h"] += created.get("ephemeral_1h_input_tokens", 0) or 0
            requests[model] += 1

    return totals, requests, skipped


def price(model, b):
    if model not in RATES:
        return None
    inp, out = RATES[model]
    return {
        "input":          b["input"]          / 1e6 * inp,
        "output":         b["output"]         / 1e6 * out,
        "cache_read":     b["cache_read"]     / 1e6 * inp * 0.1,
        "cache_write_5m": b["cache_write_5m"] / 1e6 * inp * 1.25,
        "cache_write_1h": b["cache_write_1h"] / 1e6 * inp * 2.0,
    }


def listing(logs):
    """One row per session, oldest first. Use it to find the id you want."""
    print("| Session | Started | Ended | Requests | Cost |")
    print("|---|---|---|---:|---:|")
    rows = []
    for path in logs.glob("*.jsonl"):
        totals, requests, _ = collect(logs, path.stem, None, None)
        if not totals:
            continue
        stamps = [
            rec.get("timestamp", "")
            for line in path.read_text(errors="ignore").splitlines()
            if (rec := json.loads(line)) if isinstance(rec, dict)
        ]
        stamps = sorted(s for s in stamps if s)
        cost = sum(sum(c.values()) for m in totals if (c := price(m, totals[m])))
        rows.append((stamps[0] if stamps else "", stamps[-1] if stamps else "",
                     path.stem, sum(requests.values()), cost))
    for start, end, name, reqs, cost in sorted(rows):
        print(f"| `{name[:8]}` | {start[:16]} | {end[:16]} | {reqs} | ${cost:,.2f} |")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--repo", default=".")
    ap.add_argument("--list", action="store_true", help="list sessions and stop")
    ap.add_argument("--session", help="session id or prefix")
    ap.add_argument("--since", help="ISO-8601, inclusive")
    ap.add_argument("--until", help="ISO-8601, inclusive")
    args = ap.parse_args()

    logs = project_dir(args.repo)
    if not logs.is_dir():
        sys.exit(f"no session logs at {logs}")

    if args.list:
        listing(logs)
        return

    totals, requests, skipped = collect(logs, args.session, args.since, args.until)
    if not totals:
        sys.exit("no usage records matched")

    grand, unpriced = 0.0, []
    for model in sorted(totals):
        b, costs = totals[model], price(model, totals[model])
        if costs is None:
            unpriced.append(model)
            continue
        grand += sum(costs.values())
        print(f"\n{model} — {requests[model]} requests")
        print("| | Tokens | Cost |")
        print("|---|---:|---:|")
        for f in FIELDS:
            if b[f]:
                print(f"| {f.replace('_', ' ')} | {b[f]:,} | ${costs[f]:,.2f} |")
        print(f"| **Subtotal** | **{sum(b.values()):,}** | **${sum(costs.values()):,.2f}** |")

    print(f"\n**Total: ${grand:,.2f}**")
    print(f"\n_{skipped} duplicate requestId records skipped._")
    if unpriced:
        print(f"_No rate for: {', '.join(unpriced)}. Add it to RATES._")


if __name__ == "__main__":
    main()
