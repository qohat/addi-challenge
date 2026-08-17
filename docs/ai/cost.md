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

## Planning sessions

Three sessions produced no code and so no spec row will ever carry them:
`/run-spec` fills a row when it closes a spec, and these closed none. The cost is
real, so it is priced by the same script and counted in the project total.

| Session | Produced | Model | Requests | Tokens | Cost |
|---|---|---|---:|---:|---:|
| `9c968601` | Session 00, the bootstrap | claude-opus-5 | 70 | 7,731,852 | $6.81 |
| `3ed3e39a` | Session 01, ADR 0008 and specs 00 to 03 | claude-opus-5 | 23 | 1,969,675 | $3.12 |
| `580b4740` | Session 07, closed out spec 04's records and wrote spec 05 | claude-opus-5 | 36 | 2,311,075 | $2.39 |
| **Subtotal** | | | **129** | **12,012,602** | **$12.32** |

## Summary

| Spec | Session | Model | Requests | Tokens | Cost |
|---|---|---|---:|---:|---:|
| 00 | `d617a871` | claude-opus-5 | 59 | 4,088,542 | $3.41 |
| 01 | `e76bafcf` | claude-opus-5 | 35 | 2,470,033 | $2.56 |
| 02 | `9f3baa51` | claude-opus-5 | 33 | 2,160,355 | $2.20 |
| 03 | `40bfad38` | claude-opus-5 | 59 | 6,508,949 | $6.22 |
| 04 | `4ea44317` | claude-opus-5 | 47 | 4,270,261 | $4.28 |
| **Subtotal** | | | **233** | **19,498,140** | **$18.67** |

**Project total: $30.99** — $12.32 planning plus $18.67 specs. Both tables, or the
number understates the project by everything spent before the first line of Java.

## What is left

Four specs remain. The four merged mediums came in at $2.20, $2.56, $3.41 and
$4.28 and the one high at $6.22, so 05 (low) sits under a medium, 06 (high) near
the high, and 07 and 08 (medium) in the medium band: roughly $16 more, and about
$42 for the project. A projection, not a budget — nothing is cancelled for
exceeding it, and the point of writing it down is to be wrong in public.

## Breakdown

One section per spec, holding the table `scripts/cost.py --session <id>` prints.

### 00. Build, checks and CI

claude-opus-5 — 59 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 118 | $0.00 |
| output | 28,913 | $0.72 |
| cache read | 3,990,566 | $2.00 |
| cache write 1h | 68,945 | $0.69 |
| **Subtotal** | **4,088,542** | **$3.41** |

No domain code merged, so this is the price of the gate: the toolchain, the
three places preview has to be enabled, CI and the Dockerfile. Output is 0.7% of
tokens — the session was mostly reading its own build failures.

### 01. Domain types, pipeline and four stubbed ports

claude-opus-5 — 35 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 70 | $0.00 |
| output | 27,875 | $0.70 |
| cache read | 2,374,611 | $1.19 |
| cache write 1h | 67,477 | $0.67 |
| **Subtotal** | **2,470,033** | **$2.56** |

Cheaper than the gate that produced no domain code, on 40% fewer requests, and
this one merged every type the project sits on. ADR 0008 had already fixed the
shapes, so the session spent its tokens transcribing rather than deciding — which
is the argument for writing the shapes down before the session that needs them.

### 02. CLI `validate-lead` and composition root

claude-opus-5 — 33 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 66 | $0.00 |
| output | 21,778 | $0.54 |
| cache read | 2,077,012 | $1.04 |
| cache write 1h | 61,499 | $0.61 |
| **Subtotal** | **2,160,355** | **$2.20** |

The cheapest spec so far and the first one a reviewer can run. Three commits, a
parser, a renderer and twenty lines of wiring: the spec had already fixed the
`Invocation` shape, the exit code map and the four prose lines, so there was
nothing left to decide. The trend across 00, 01 and 02 is the specification
getting more precise, not the work getting smaller.

### 03. Simulated adapters and structured concurrency

claude-opus-5 — 59 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 118 | $0.00 |
| output | 70,612 | $1.77 |
| cache read | 6,308,064 | $3.15 |
| cache write 1h | 130,155 | $1.30 |
| **Subtotal** | **6,508,949** | **$6.22** |

The most expensive spec so far, and the only one so far rated high: three times
the output of spec 02 across four commits, a thousand lines, and the fork-join
shape the whole brief turns on. The extra cost is the concurrency test — proving
two adapters are inside their calls at the same time without sleeping took more
iterations than any other criterion. Where 00 to 02 were transcription, this
session was designing, which is what the effort field predicts.

### 04. Bureau cache

claude-opus-5 — 47 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 94 | $0.00 |
| output | 47,906 | $1.20 |
| cache read | 4,119,770 | $2.06 |
| cache write 1h | 102,491 | $1.02 |
| **Subtotal** | **4,270,261** | **$4.28** |

The most expensive medium, and above the trend 00 to 02 was setting. Three
commits and 407 lines, but twelve acceptance criteria — most of them a way the
cache file can be wrong rather than a way it works — and each one is a test
written, watched to fail, then made to pass. Rated medium because no shape was
left open, which held; the price was breadth, not design.

## What the number includes

With one session per spec, each figure measures session overhead — orientation,
planning, review, closeout — as much as implementation. Pretending otherwise
would be worse than the overhead itself.

Cache reads dominate. On the previous project 96.5% of tokens were cache reads
and 0.5% were output, which is why the budgets in `scripts/context-budget.py`
exist at all.

Measured resident context per session: `python3 scripts/context-budget.py --measured`.
