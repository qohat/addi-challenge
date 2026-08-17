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

Seven sessions implemented no spec, so no spec row carries their own cost:
`/run-spec` fills a row when it closes a spec, and none of these closed one of
their own. The cost is real, so it is priced by the same script and counted in
the project total.

| Session | Produced | Model | Requests | Tokens | Cost |
|---|---|---|---:|---:|---:|
| `0dd4af1c` | Nothing — a false start before the bootstrap | claude-opus-5 | 7 | 364,371 | $1.02 |
| `9c968601` | Session 00, the bootstrap | claude-opus-5 | 70 | 7,731,852 | $6.81 |
| `3ed3e39a` | Session 01, ADR 0008 and specs 00 to 03 | claude-opus-5 | 23 | 1,969,675 | $3.12 |
| `580b4740` | Session 07, closed out spec 04's records and wrote spec 05 | claude-opus-5 | 36 | 2,311,075 | $2.39 |
| `4032a67d` | Session 09, wrote spec 06 | claude-opus-5 | 64 | 5,951,178 | $5.05 |
| `492118b2` | Session 13, closed out spec 08 | claude-opus-5 | 25 | 1,397,270 | $1.43 |
| `c14a1c38` | Session 14, the compliance document and the Windows instructions | claude-opus-5 | 31 | 2,800,172 | $3.02 |
| **Subtotal** | | | **256** | **22,525,593** | **$22.84** |

The first row bought nothing. It is eleven minutes of model and tool
configuration before the first commit, abandoned and started again, and it is in
the table because an attempt that was thrown away still cost $1.02. The last two
are the other end of the same honesty: closeouts and documentation that came
after the ninth spec merged, which no spec row can absorb.

Session 09 is the most expensive planning session that produced a single spec,
and it cost more than three of the five merged specs. It was the session that
turned 06 from a title into fixed shapes — the file format, the three queue
methods, the resume rule, the exits — which is work that has to happen either
here or inside the implementing session. Paying it here is the cheaper half of
the trade only if 06 then comes in near a medium; that is the bet this row is
recording, and 06's own row is what settles it.

## Summary

| Spec | Session | Model | Requests | Tokens | Cost |
|---|---|---|---:|---:|---:|
| 00 | `d617a871` | claude-opus-5 | 59 | 4,088,542 | $3.41 |
| 01 | `e76bafcf` | claude-opus-5 | 35 | 2,470,033 | $2.56 |
| 02 | `9f3baa51` | claude-opus-5 | 33 | 2,160,355 | $2.20 |
| 03 | `40bfad38` | claude-opus-5 | 59 | 6,508,949 | $6.22 |
| 04 | `4ea44317` | claude-opus-5 | 47 | 4,270,261 | $4.28 |
| 05 | `f55a26ac` | claude-sonnet-5 | 60 | 7,211,970 | $3.70 |
| 06 | `49e92674` | claude-opus-5 | 61 | 6,122,044 | $5.47 |
| 07 | `f51cdff0` | claude-opus-5 | 73 | 6,423,241 | $5.42 |
| 08 | `dd3ee7b3` | claude-opus-5 | 65 | 5,849,596 | $4.91 |
| **Subtotal** | | | **492** | **45,104,991** | **$38.17** |

Spec 05 is the only session not run on `claude-opus-5`, and deliberately so: it
was rated low, and a low spec is the one place to test whether the specification
carries a cheaper model. Its row is not comparable to the four above it and is
read in the breakdown, not in this column.

**Project total: $61.01** — $22.84 planning plus $38.17 specs. Every session that
ran against this repository is in one of the two tables, including the false start
and the sessions that came after the last spec merged. Read the specs table alone
and the project looks like $38.17, which would be flattering and wrong: 37% of
the bill was spent outside a spec.

One session sits outside both tables, and always will: this one, the bookkeeping
session that wrote the rows above. It cannot price itself, for the same reason
every other row is filled in by the session after it.

## What the estimate was worth

The last thing this file predicted was "$5 to $6 for 08 puts the project near
$56". 08 came in at $4.91, so the forecast held on the row it was about — the
first one here made from more than one comparable row.

The project figure it implied did not, and not because the estimate was off. The
project is $61.01, because three sessions had never been priced at all: the false
start, and the two sessions after 08 merged that closed its row and wrote the
compliance and Windows documentation. Every forecast in this file was a forecast
about specs, and the thing that moved the total was work that was not a spec.

The forecast rested on 07, the first session to write its own spec and then
implement it: $5.42 against 06's $10.52 across two sessions, for a spec of
comparable size. 08 is the second row on that shape and it came in under 07, so
the change made after 06 is now two data points rather than one, both pointing
the same way — the cost is in sessions, not in specs.

Nine specs across nine implementing sessions, none rated high after 03, and a
spread from $2.20 to $6.22 narrow enough that the session, not the spec, is the
unit that sets the bill.

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

### 05. Qualification score and conversion

claude-sonnet-5 — 60 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 120 | $0.00 |
| output | 55,674 | $0.84 |
| cache read | 7,029,857 | $2.11 |
| cache write 1h | 126,319 | $0.76 |
| **Subtotal** | **7,211,970** | **$3.70** |

The one session run on a cheaper model, on purpose. A low spec is where the
`specs/README.md` experiment belongs: if the specification is doing the work, the
model should be substitutable, and the cheapest spec is the safest place to find
out. It merged — four commits, 72 lines across nine files, no shape invented and
no criterion dropped — so the specification held.

The price did not. It is the smallest diff in the project and the third most
expensive spec: 60 requests and 7.0M cache read, more of both than any Opus
session except the two rated high. Sonnet's per-token discount is real and worth
about 40%, but this session spent it and more on volume — the same tokens on
Opus would have been $6.17, while Opus did the comparable mediums 02 and 01 for
$2.20 and $2.56 on half the requests. The conclusion is about tokens, not
quality: the cheaper model reached the same merged result and took roughly three
times the reading to get there, so on a spec this well specified there is nothing
left for it to save.

### 06. Manual review: checkpoint, queue, resume

claude-opus-5 — 61 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 122 | $0.00 |
| output | 53,958 | $1.35 |
| cache read | 5,953,413 | $2.98 |
| cache write 1h | 114,551 | $1.15 |
| **Subtotal** | **6,122,044** | **$5.47** |

The largest spec in the project — 552 lines across twelve files, four commits —
and the most expensive medium, above the $4.28 top of the merged band.

Its real price is $10.52, because session 09 spent $5.05 writing it. That row
bet that pre-deciding the file format, the queue methods and the resume rule
would bring the implementation in near a medium; it came in above every medium
instead, so the split cost more than doing both in one session and bought
nothing measurable. The design work was real either way. Paying for a second
session's orientation to hold it was the waste, and `/run-spec` no longer does
that.

### 07. Demo command and fixtures

claude-opus-5 — 73 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 146 | $0.00 |
| output | 47,812 | $1.20 |
| cache read | 6,266,613 | $3.13 |
| cache write 1h | 108,670 | $1.09 |
| **Subtotal** | **6,423,241** | **$5.42** |

The first session to write its spec and then implement it, and the most requests
of any session in the project. The spec is 150 of the 540 lines the session
produced; the code is 113 lines of `Demo.java` over the pipeline that already
existed, plus the fixture rows every outcome needs.

Against 06's $10.52 across two sessions this is the cheaper half of the same
trade, at a smaller diff. The request count is where the merge shows: 73 against
61, one session doing both jobs rather than two sessions each paying to orient.

### 08. Final documentation

claude-opus-5 — 65 requests

| | Tokens | Cost |
|---|---:|---:|
| input | 130 | $0.00 |
| output | 38,367 | $0.96 |
| cache read | 5,700,932 | $2.85 |
| cache write 1h | 110,167 | $1.10 |
| **Subtotal** | **5,849,596** | **$4.91** |

The second session to write its own spec and then implement it, and cheaper than
the first at 65 requests against 73. Four commits after the spec: `ReadmeTest`,
the README itself, the assumption line that says why the README is the one
document with no line budget, and a correction to what section 2 claims a
reviewer sees.

Output is the lowest of the last four sessions — 38k against 05's 56k — while the
merged README is 279 lines, the longest thing in the repository written to be
read by a person. That is what a documentation spec should look like: the work is
deciding what is true and finding the commit that proves it, and that work is
reading. 97.5% of the tokens were cache reads.

This row was filled in by a later bookkeeping session, not by the one it prices.

## What the number includes

With one session per spec, each figure measures session overhead — orientation,
planning, review, closeout — as much as implementation. Pretending otherwise
would be worse than the overhead itself.

Cache reads dominate. On the previous project 96.5% of tokens were cache reads
and 0.5% were output, which is why the budgets in `scripts/context-budget.py`
exist at all.

Measured resident context per session: `python3 scripts/context-budget.py --measured`.
