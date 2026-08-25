# Spec queue

One spec per session, one session per spec. A spec is a version of the code that
could be deployed the day it merges — not a milestone, not a checkpoint,
something that runs.

Status is the source of truth for where the project is. A new session reads this
file, then its assigned spec, then `git log --oneline -15`.

| # | Title | Depends on | Effort | Priority | Status |
|---|---|---|---|---|---|
| 00 | Build, checks and CI | — | medium | core | merged |
| 01 | Domain types, pipeline, four stubbed ports | 00 | medium | core | merged |
| 02 | CLI `validate-lead` and composition root | 01 | medium | core | merged |
| 03 | Simulated adapters and structured concurrency | 02 | high | core | merged |
| 04 | Bureau cache | 03 | medium | core | merged |
| 05 | Qualification score and conversion | 04 | low | core | merged |
| 06 | Manual review: checkpoint, queue, resume | 05 | medium | core | merged |
| 07 | Demo command and fixtures | 06 | medium | nice-to-have | merged |
| 08 | Final documentation | 07 | medium | core | merged |
| 09 | Fraud check | 08 | medium | requested | specced |

## If time runs out

05, 06 and 08 are core. 05 and 06 are the last two business rules the brief
asks for, and 08 is the README section 5 requires — a submission without it is
missing a stated deliverable, whatever the code does.

07 is the only nice-to-have. A demo command is reviewer convenience: it runs
paths that `validate-lead` already runs, with fixtures that already exist.
Dropping it leaves a coherent system — every rule implemented, every outcome
reachable from the CLI, the README explaining how — and 08 then depends on 06.
It gets dropped first, and it is the only thing that gets dropped.

## Why this order

The queue is ordered by what a reviewer can see working, not by architectural
layer. Ordering by layer once produced six merged specs and thousands of lines
with the headline mechanism still missing.

**00** exists because nothing runs without it. Gradle, the Java 25 toolchain
with `--enable-preview`, formatting, the aggregate `check` task, CI and the
Dockerfile. Small infrastructure belongs inside the setup spec rather than
getting its own session, worktree, PR and cost entry.

**01** writes down every shape the rest of the project sits on: the lead and
prospect types, the per-step outcome hierarchies, the rejection cause type, the
four ports, the checkpoint, and the pipeline that sequences them. All four ports
are stubbed. A pipeline returning a fixed outcome through the real types is a
real increment, and it pins the interfaces down before four adapters depend on
them. The shapes themselves are decided in ADR 0008, which is why this is medium
and not high — executing it is transcription plus the pipeline.

**02** puts a runnable command on top of those stubs. Dummy outcomes travel
through the real types and out through the real exit codes, so from here on
every spec deepens something a reviewer can already run.

**03** is what the brief is actually about: registry and judicial forked in
parallel under a `StructuredTaskScope`, a scope-level timeout, and simulated
adapters driven by fixtures that carry per-row latency — which is how a demo
lead can be slow enough to fire the real timeout rather than a shrunk one.

**04** is the brief's durability requirement, and it comes first of the three
because it is the only one that changes nothing about the pipeline: the cache
sits behind the bureau port, so it lands while the score still converts on any
number and pending still reaches no disk. It also builds the data directory and
the atomic write that 06 reuses for the review queue.

**05 and 06** are the rest of the resilience story, in dependency order rather
than the order the brief lists them. The score rule comes before manual review because
approving a pending score step re-runs the score port, so the rule has to exist
before resume can be specified. At the end of 05 the whole pipeline is real; 06
adds the checkpoint, the queue and `review resolve`.

**07 and 08** are the submission: one command showing every outcome path, then
the README the brief asks for, covering the process, what worked and what did
not with AI, and the pending improvements.

Two types gain a case late and break every exhaustive match over them: the
rejection cause when 05 lands and again when 06 lands. That is the design
working. Each site gets fixed deliberately.

**09** arrives after the submission was closed out: a fifth validation the brief
never asked for, requested on top of the finished system. It is in the queue on
the same terms as everything else — the spec lands before the code — and it is
the first evidence that the design absorbs a new step without a redesign. One
enum constant, one outcome type, one cause, and the arms the compiler demands
before the build goes green again.

## Provisional past 03

Only 00 to 04 are planned with any confidence. Everything below is a title and a
guess at effort, written before the code exists, and it will move. The last
project put thirteen specs in this table up front and ended up with a sixty-line
section explaining why four of them ran out of numeric order — which was the
evidence the plan had not survived contact.

Specs are written one at a time, at the end of the session that implemented the
previous one, when the shape of the code is known. Only the queue is written up
front.

## Effort

Assigned by one question: does the spec leave any shape undecided — a type, an
interface, a data format, a control flow?

- **high** — yes. Whoever executes it is designing, and later specs sit on
  whatever they pick.
- **medium** — every shape is fixed, but the work needs judgement.
- **low** — executing it is typing.

The field also decides which model runs the session. If most specs come out
high, they are underspecified, and the fix is the spec rather than the label.

## After the last spec

Not a spec: re-run one already-merged spec on a cheaper model from a clean
session. If the spec was good the result should be equivalent. That measures the
specification rather than the model, and it is the sharpest available evidence
that the context engineering is real.
