# Cost per spec

Computed by `scripts/cost.py` from the session logs, never by asking a model to
read its own logs. The logs write one record per content block of the same API
response, so summing them naively roughly doubles the number; the script dedups
by `requestId` and prices cache writes by TTL, since 5-minute and 1-hour writes
are 1.25x and 2x input and confusing them is a 60% error on that line.

One session per spec means one log file per spec, so a spec's cost is its
session's cost and there are no overlapping windows to carve up.

Filled in at the *start* of the next session by `/run-spec`, because a session's
log is not complete until that session has closed.

## Why this is its own file

Not for context reasons — eight rows of dollar amounts is about fifty bytes, and
recording it as a budget problem would be wrong. It is here because the useful
artefact is the full per-spec breakdown: the token split across input, output,
cache read and cache write, and the note on what each number actually includes.
That is what a reviewer wants to see and none of it fits in a column.
`specs/README.md` keeps status and dependencies, which is the operational half.

## Summary

| Spec | Session | Model | Requests | Tokens | Cost |
|---|---|---|---:|---:|---:|
| | | | | | |

## Breakdown

One section per spec, holding the table `scripts/cost.py --session <id>` prints.

## What the number includes

With one session per spec, each figure measures session overhead — orientation,
planning, review, closeout — as much as implementation. Pretending otherwise
would be worse than the overhead itself.

Cache reads dominate. On the previous project 96.5% of tokens were cache reads
and 0.5% were output, which is why the budgets in `scripts/context-budget.py`
exist at all.

Measured resident context per session: `python3 scripts/context-budget.py --measured`.
