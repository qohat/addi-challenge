# Addi lead validation challenge

## What it is

A command-line application that qualifies a sales lead into a prospect. Five
validations run in the order the brief fixes: the national registry and the
judicial archives in parallel, then the compliance bureau behind a durable
cache, then a fraud check, then a qualification score that converts only above
60. A step whose dependency is down is not a rejection — it stops the pipeline
at a checkpoint, opens a manual review case and waits for an analyst.

    registry --+
               +--> bureau --> fraud --> score --> converted
    judicial --+

Java 25 with preview features, Gradle, a single module, no framework and no
infrastructure. The external systems are simulated from CSV fixtures carrying
their own latency, so every outcome below is reproducible.

## How to review this

`docs/COMPLIANCE.md` is the entry point. It walks `docs/brief.md` clause by
clause and, for each one, names the file that answers it and the approach taken.
Thirteen rows are marked partial — met by interpretation, met narrowly, or met
in a way the brief may not have imagined — and every one of them is spelled out
in its *Declared gaps* section, G1 to G13. Reading those gaps first is the
fastest way into the exercise: they are the calls a reviewer is most likely to
want to argue with, stated before being asked. Nothing in that file repeats this
README; where the answer is already here or in an ADR, the row links instead of
re-explaining.

Then the rest, in the order that reaches a judgement fastest:

| | What to open | What it settles |
|---|---|---|
| 1 | `docs/COMPLIANCE.md`, gaps first | Whether the code does what the brief asked, and where to look for each clause |
| 2 | *Run it* below: `installDist`, then `demo` and `./gradlew check` | Eleven scenarios end to end, then the build, 109 tests and the context budgets |
| 3 | `src/main/java/com/addi/lead/Main.java`, then `app/Pipeline.java` and `domain/Decisions.java` | The whole object graph in one method, the fork-join stage, and the single point where any outcome becomes a decision |
| 4 | `docs/adr/`, then `docs/assumptions.md` | Why each decision was made, and every ambiguity that was closed rather than guessed |
| 5 | `specs/README.md` and the merged pull requests | What was built, in what order, and against what written up front |

### Reviewing the pull requests

Eleven merged PRs: one per spec, 00 through 08, plus two closeouts.

    $ gh pr list --state merged
    $ gh pr view 4          # spec 03, structured concurrency, the densest one
    $ gh pr diff 4

Each body says what is in the branch, which of the spec's acceptance criteria it
met, and where the implementation deviated from the spec and why — PR #4 moved
the fixtures out of the jar's resources and into `fixtures/`, and says so under
its own heading. The commits inside a PR are sliced by behaviour rather than by
layer, so each one is a change a reviewer can read on its own.

The ordering is the part the brief asks to be verifiable, and it is in the
history rather than in a claim: the spec is committed to `main` on its own,
before the branch that implements it exists.

    $ git log --oneline --graph -40

`988b557` *Specify the demo command...* lands before `77148f2`, the merge of the
PR that built it, and the same holds for specs 04, 05, 06 and 08.
`specs/README.md` is the queue those commits work through, and G9 in
`docs/COMPLIANCE.md` states the limit of the claim: only specs 00 to 03 were
written up front.

### Reviewing the AI record

`prompts/` holds the prompt that opened each session that had one,
`docs/ai/sessions/` holds a transcript per session, and `docs/ai/cost.md` prices
them from the logs with `scripts/cost.py`. What those artifacts are and are not
— terminal captures rather than raw API logs, and fewer prompt files than
sessions, because a session opened with a checked-in skill has no prose prompt —
is G10. What was learned from them is *Working with AI* below, and what the
whole thing cost is *What it cost*.

## Run it

JDK 25 is the only requirement to run it, or Docker if you would rather not
install one. `./gradlew check` also needs `python3`, for the budget task.

    $ ./gradlew installDist
    $ export PATH="$PWD/build/install/lead-validation/bin:$PATH"

On Windows the same two steps in PowerShell, against the `gradlew.bat` and the
`lead-validation.bat` the distribution already ships:

    PS> .\gradlew.bat installDist
    PS> $env:Path = "$PWD\build\install\lead-validation\bin;$env:Path"
    PS> lead-validation demo

Every `lead-validation` command below is then identical on both. The difference
is the gate: `gradlew.bat check` shells out to `python3` for the budget task, and
the python.org installers put `python.exe` and `py.exe` on PATH but no
`python3.exe` — install one that does, or run `gradlew.bat check -x
contextBudget` and lose only the line budgets. Nothing else here is POSIX-only:
paths go through `java.nio.file` throughout, a case ID is
`<nationalId>-<epoch millis>` rather than an ISO instant because a Windows
filename cannot hold the colons, and a CRLF checkout of `fixtures/` parses the
same, because every row's last cell is trimmed.

Start with the demo. Eleven scenarios — a conversion, all seven rejection
causes, a pending case an analyst approves, one they reject, a real 2s timeout
and a cache hit — each run as a real command through the real parser and the
real exit codes, in a fresh temporary directory it names on the first line:

    $ lead-validation demo

`src/test/java/com/addi/lead/DemoTest.java` asserts what it prints: the eleven
scenarios in order, the rendered line of every rejection cause, the timeout, the
analyst rejection, the cache hit, and that the run writes nothing into `data/`.

One lead at a time. The score is drawn fresh on every run — 40 of the 101
possible values convert — so this lead is rejected on the score more often than
not:

    $ lead-validation validate-lead --id 1020304050
    Lead 1020304050 converted to prospect. Score 67.

When a dependency is down the run stops and leaves a case:

    $ lead-validation validate-lead --id 1090001020
    Lead 1090001020 pending manual review at step BUREAU: the compliance bureau is down.
    Case 1090001020-1786940581101 opened.

The queue is `data/review/`, one file per case, browsable with `ls` and `cat`.
An analyst resolves a case by ID:

    $ lead-validation review resolve 1090001020-1786940581101 --approve
    $ lead-validation review resolve 1090001020-1786940581101 --reject

Approving means the analyst verified the failed step out of band and it came
back clean. The pipeline picks up after that step and can still reject on the
score.

stdout carries the decision in prose; the exit code is the machine contract:

| Code | Meaning |
|---|---|
| 0 | Converted to prospect |
| 1 | Rejected |
| 2 | Pending manual review |
| 3 | Input error |
| 4 | The review case could not be written |

Every row is asserted in `src/test/java/com/addi/lead/MainTest.java`, which runs
the wired application in process against fixtures the test wrote — including
`aCaseThatCannotBeWrittenExitsFourOnStderr` for the one code nothing else
reaches.

`./gradlew run` does not preserve these — Gradle reports any non-zero exit as
its own build failure 1. The installed distribution and the Docker image return
them directly.

Everything the project checks, in one task:

    $ ./gradlew check

That is the build, 109 tests, and the context budgets in
`scripts/context-budget.py`, which fail the build when a documentation file
outgrows its line limit.

Without a JDK:

    $ docker build -t lead-validation .
    $ docker run --rm lead-validation demo

## How it works

Hexagonal, hand-wired. Eight ports name the effects — the five external systems,
the lead repository, randomness and the simulated latency — and the whole object
graph is one readable method in `src/main/java/com/addi/lead/Main.java`. The
domain is pure, so every test is deterministic by construction rather than by
sleeping.

Failures are values, never exceptions. Every step returns its own sealed
hierarchy with cases named after what happened, and the one place where a
business rejection and an unavailable dependency collapse into a single decision
is one exhaustive switch. Adding a case there breaks every call site on purpose.

- **Parallel stage.** Registry and judicial are forked into one
  `StructuredTaskScope.open()` with a scope-level timeout, 2s by default. On
  timeout the scope cancels both branches and the run goes to manual review.
  `src/test/java/com/addi/lead/app/PipelineConcurrencyTest.java` proves all
  three claims: `registryAndJudicialAreInsideTheirCallsAtTheSameTime` blocks
  until two callers have arrived, so sequential execution cannot pass it, and
  `aJudicialBranchThatFinishedFirstIsStillNotUsed` covers the half-answer.
- **Bureau cache.** A CSV keyed by national ID with a 24h TTL, written by
  atomic rename. Only terminal answers are cached; an outage never is, because
  caching one turns a transient failure into a permanent one. A corrupt row is
  simply a miss. Each of those is a case in
  `src/test/java/com/addi/lead/adapter/BureauCacheTest.java`, down to
  `theTtlBoundaryIsExpiredAndOneMillisecondInsideItIsAHit`.
- **Manual review.** A case persists the lead, the outcomes already resolved,
  why it stopped and which step is pending. Resolving it is terminal, and an
  analyst approval is never written to the bureau cache — it is not a bureau
  response and must not expire on a TTL. The resume is
  `MainTest.approvingResumesPastTheDownBureauAndConverts`, and
  `approvingIntoADownScoreOpensASecondCase` is what happens when it stops again.
- **Configuration.** `Config` is a record with the timeout, the fixture and data
  directories, the cache TTL and an optional seed. None of them is a flag; only
  tests and the demo vary them.

The reasoning is in `docs/adr/`, one file each:

| ADR | Decision |
|---|---|
| 0001 | Hexagonal architecture with a hand-wired composition root |
| 0002 | Java 25 preview structured concurrency for the parallel stage |
| 0003 | Persistence without a database |
| 0004 | Manual review is a resume, not an override |
| 0005 | Failure taxonomy: rejection and unavailability are different |
| 0006 | Determinism is a test property, not a business one |
| 0007 | Docker is a reviewer convenience, never a build dependency |
| 0008 | Domain shapes and the single collapse point |

## Decisions and assumptions

The brief says "three distinct validations" and then lists four. Four was the
reading, and the qualification score is the fourth. The fraud check is a fifth,
asked for after the submission closed; it is the one validation the brief never
mentions, and `specs/09-fraud-check.md` is where it was specified.

The calls most worth challenging, all of them one-liners in
`docs/assumptions.md`, tagged with the spec that hit them:

- A national ID absent from the local database is a **business rejection**, not
  an input error. The command line was valid; the precondition was not.
- A timeout is **never a rejection**. The outside world failing says nothing
  about the lead.
- Judicial rejects on **any** count of one or more; the brief gives no severity
  dimension, so none was invented.
- "Greater than 60" is read literally: 60 rejects, 61 converts.
- Email is **not** part of the registry match; a civil registry does not hold
  email addresses.
- The score is never fixtured, only seeded, so it stays genuinely random in
  production and fixed in tests.

`docs/PROJECT_CONTEXT.md` holds the rest, and `docs/brief.md` is the
requirements as given.

## How it was built

Nine specs, one per session. A spec is a version of the code that could be
deployed the day it merges, under 150 lines, with its acceptance criteria
written as tests before any code exists. Each one was committed to `main` on its
own, then implemented in a worktree and merged through a PR split into
reviewable commits — the history shows `Specify the demo command...` landing
before the branch that built it, every time. The queue and its ordering are in
`specs/README.md`.

Only specs 00 to 03 were written up front. The rest were written at the start of
the session that implemented them, from the code as it actually turned out
rather than as it had been imagined.

The context an agent loads is budgeted and the budget is in the build.
`CLAUDE.md` is a 37-line router re-read on every request; `AGENTS.md`,
`CONTRIBUTING.md` and `docs/PROJECT_CONTEXT.md` load once per session and are
capped at 120 lines; a spec is capped at 150. Over budget means cutting the
file, never raising the limit. The two repeatable procedures — writing a spec
and running one — are checked-in skills under `.claude/skills/`, so the process
is version-controlled rather than retyped.

## Working with AI

Every claim here has a number, a commit or a file behind it.

**The specification made the model substitutable, and that did not make it
cheaper.** Spec 05 was rated low and deliberately run on Sonnet instead of Opus.
It merged with no shape invented and no criterion dropped, which is the result
the experiment was looking for. It also spent 7.0M tokens across 60 requests
where Opus did the comparable specs 01 and 02 in 2.5M and 2.2M across 35 and 33.
The per-token discount is real and worth about 40%; this session spent it and
more on re-reading. On a spec this well specified there was nothing left for a
cheaper model to save.

**Splitting the planning out of the implementation cost money and bought
nothing.** Spec 06 was written in its own session for $5.05 and implemented in
the next for $5.47 — $10.52, against a merged-medium band that topped out at
$4.28. The bet was that pre-deciding the file format and the resume rule would
bring the implementation in low. It came in above every medium instead. The fix
was to the skill rather than to the estimate (commit `4bf8f2d`); spec 07 then
did both jobs in one session for $5.42.

**The agent will quietly skip bookkeeping that nothing enforces.** Two
consecutive sessions ended without closing out the previous spec, leaving the
queue claiming `specced` for merged work and one spec file uncommitted
(`prompts/05-bookkeeping-mistake.txt`). Nothing in the code was wrong and no
test could have caught it. The fix was to make the closeout the first step of
the skill that starts the next session, because a session's log is not complete
until it has closed.

**It plans confidently around things that do not work.** Spec 00 specified
Spotless with palantir-java-format; the formatter's last release predates JDK 25
by three months and dies with a `NoSuchMethodError` on a javac internal. The
spec had named a fallback, so it cost exactly one commit (`ba8fc5b`) instead of
an argument. Relatedly, the model reaches for the Java 21
`new StructuredTaskScope<>()` API from training data — `CONTRIBUTING.md` names
that specific mistake for that specific reason.

**Refining the output came from three things, none of them prompting.** The
spec, which fixes every shape before the session starts and is why "medium"
specs stay medium. The gate: `./gradlew check` runs the build, the tests and the
context budgets, and it is the same command in CI. And a human reading every PR
before the next spec begins — the only manual step in the process. Cost is
computed by `scripts/cost.py` from the session logs rather than self-reported,
because a model asked to price its own logs double-counts them.

The full record is in the repository: `prompts/` has the prompt that opened each
session, `docs/ai/sessions/` has the complete transcripts, and `docs/ai/cost.md`
prices every one of them.

## What it cost

$62.10 for the whole thing, and that figure covers every session that ran against
this repository: $38.17 across nine specs, cheapest $2.20 and dearest $6.22, plus
$23.93 across eight sessions that implemented no spec — the false start before the
first commit, the planning sessions, the closeouts, and the final documentation.
Quoting $38.17 would be flattering and wrong; 39% of the bill was spent outside a
spec, and no projection made during the project had a line for it, which is the
single biggest thing this measurement changed.

Roughly 96% of all tokens were cache reads and well under 1% was output — the
bill is what the model re-reads, not what it writes, which is what the line
budgets above are for. Spec 08 cost $4.91, priced by a separate closeout session
afterwards because a session cannot price its own log; that closeout is itself a
row in the planning table. The only session missing from the total is the
bookkeeping session that wrote the rows, for the same reason. The per-spec
breakdown is in `docs/ai/cost.md`.

## Pending improvements

Deliberate scope cuts, kept because they were the right call here and would not
be in a production system:

- **No `--data-dir`, `--timeout` or `--fixtures` flags.** A flag with no user is
  a flag to maintain for nobody; `Config` is injected instead.
- **No `review list` or `review show`.** The queue is a directory and `ls` and
  `cat` already read it.
- **Converted prospects are not persisted.** The lead database is in-memory and
  the process is single-shot, so conversion is stdout and an exit code. A real
  system writes the prospect back to the CRM.
- **Only the bureau is cached.** The brief asks for durability over bureau
  responses and gives the other three steps no such requirement.
- **The demo never deletes its temporary directories.** They are the evidence.

Real gaps, in the order I would fix them:

- **The cache is read and rewritten whole, with no eviction or compaction.**
  Fine at fixture scale and stated as such in ADR 0003; it is linear in the
  number of leads ever seen.
- **Concurrency across processes is only what atomic rename gives.** Two
  processes resolving the same case can both read it open before either writes.
  A single-shot CLI makes it unlikely, not impossible.
- **No structured logging, no metrics, no tracing.** Nothing asked for them and
  nothing would run without them in production, starting with the latency of
  each external call.
- **The parallel stage is proven correct, not fast.** `PipelineConcurrencyTest`
  proves both branches are genuinely in flight at once; there is no load or soak
  test, and the thread-per-task assumption is untested above one lead.
- **A resumed run re-executes the resolved step's successors only.** Everything
  before the checkpoint is trusted from the case file, which is correct while
  cases are resolved in minutes and wrong once they are resolved in weeks.

## Repository map

| | |
|---|---|
| The requirements | `docs/brief.md`, transcribed from `docs/brief.pdf` |
| The brief, clause by clause, and the declared gaps | `docs/COMPLIANCE.md` |
| The problem and what is decided about it | `docs/PROJECT_CONTEXT.md` |
| Why each decision was made | `docs/adr/` |
| Ambiguities closed, and by whom | `docs/assumptions.md` |
| What was built, in order | `specs/` |
| Prompts, session by session | `prompts/` |
| Session transcripts | `docs/ai/sessions/` |
| Cost per spec | `docs/ai/cost.md` |
| The simulated external systems | `fixtures/` |
| How the work is run | `AGENTS.md`, `CONTRIBUTING.md`, `.claude/skills/` |
