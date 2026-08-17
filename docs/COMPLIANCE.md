# Compliance with the brief

`README.md` says what the system is, how it works, what it cost and what is
missing. This file answers a different question: for each clause of
`docs/brief.md`, does the code do what was asked, and where does a reviewer
look. Nothing here is repeated from the README; where the answer is already
written there or in an ADR, the row links instead of re-explaining.

Rows marked **partial** are met by interpretation, met narrowly, or met in a way
the brief may not have imagined. Each one is spelled out in
[Declared gaps](#declared-gaps) below. Reading the gaps first is a reasonable
way to use this file.

## Business logic (brief section 2)

| Requirement | Where | Approach |
|---|---|---|
| Four validations, not three — the brief says "three distinct validations" and lists four | `src/main/java/com/addi/lead/domain/Step.java`, `app/Pipeline.java` | `Step` has four constants and the pipeline runs all four. **Partial** — [G1](#g1) |
| **National Registry:** the person must exist in the external registry | `adapter/FixtureNationalRegistry.java:22`, `domain/RegistryOutcome.java` | A missing `registry.csv` row is `RegistryOutcome.NotFound`, a business rejection. A missing *file* is `Unavailable` — different fact, different branch ([ADR 0005](adr/0005-failure-taxonomy.md)) |
| **National Registry:** their data must match our local database | `domain/RegistryRecord.java`, tested in `src/test/java/com/addi/lead/domain/RegistryRecordTest.java` | The adapter supplies the record; the comparison is a pure domain function returning `Matched` or `Mismatch(fields)` with the fields named. **Partial** — [G2](#g2) |
| **Judicial Records:** no records in the national archives | `adapter/FixtureJudicialRecords.java`, `domain/JudicialOutcome.java` | `RecordCount > 0` is `RecordsFound(count)`; the CLI prints the count. **Partial** — [G3](#g3) |
| **Compliance Bureau (OFAC/Sanctions):** a lightweight check | `adapter/FixtureComplianceBureau.java`, `domain/BureauOutcome.java` | `Clear` or `Sanctioned(list)`, the list name carried through to the rejection line. A sanctions hit is terminal, not a review trigger ([assumption](assumptions.md#reading-the-brief)) |
| **Bureau:** a simple persistent/durable mechanism for bureau responses | `adapter/CachingComplianceBureau.java`, [ADR 0003](adr/0003-persistence-without-a-database.md) | A decorator on the bureau port, not a fifth system: a CSV at `data/bureau-cache.csv` keyed by national ID, 24h TTL, atomic rename via `adapter/Atomic.java`. Only terminal answers are cached. **Partial** — [G4](#g4) |
| **Bureau:** if the service is down, gracefully handle the failure or trigger a manual review flow | `domain/Decisions.java:29`, `adapter/FileReviewQueue.java`, `Main.java:57`, [ADR 0004](adr/0004-manual-review-is-a-resume-not-an-override.md) | The manual review branch. An unavailable dependency at any step opens a case file naming the pending step, exits 2, and `review resolve --approve` resumes the pipeline from that step. **Partial** — [G5](#g5) |
| **Qualification Score:** an internal system provides a random score between 0 and 100 | `adapter/FixtureQualificationScore.java:29`, `port/Randomness.java`, `adapter/RandomNumbers.java` | `randomness.nextInt(101)` — 0 to 100 inclusive, drawn on every call. The number is never fixtured, only seeded ([ADR 0006](adr/0006-determinism-is-a-test-property.md)). **Partial** — [G6](#g6) |
| **Qualification Score:** converted only if the score is greater than 60 | `domain/Decisions.java:14,31`, tested in `domain/DecisionsTest.java` | `value > MINIMUM_SCORE` with `MINIMUM_SCORE = 60`, read literally: 60 rejects, 61 converts ([assumption](assumptions.md#reading-the-brief)) |

## Technical execution constraints (brief section 2)

| Requirement | Where | Approach |
|---|---|---|
| **Parallelism:** registry and judicial must execute in parallel | `app/Pipeline.java:72-88`, [ADR 0002](adr/0002-structured-concurrency-for-the-parallel-stage.md) | One `StructuredTaskScope.open()` (Java 25 preview) forking both branches under a scope-level 2s timeout. Proven by `src/test/java/com/addi/lead/app/PipelineConcurrencyTest.java`, whose `RendezvousLatency` releases nobody until two callers have arrived — sequential execution cannot pass it, and no test sleeps. **Partial** — [G7](#g7) |
| **Compliance Bureau:** requires the successful output of the previous two | `app/Pipeline.java:64,104` | The fan-out's outcomes are collapsed in canonical order and the first terminal decision returns; the bureau runs only if both were clean. A timeout marks *both* branches unavailable, because half an answer is none |
| **Sequential dependency:** the score requires the clean output of the bureau | `app/Pipeline.java:111-123` | `sequential` walks `Step` in ordinal order and stops at the first outcome that decides; the score is only reached after a clean bureau |

## Technical considerations (brief section 4)

| Requirement | Where | Approach |
|---|---|---|
| **Language:** Java (JVM) | `build.gradle.kts`, `src/test/java/com/addi/lead/ToolchainTest.java` | Java 25 with `--enable-preview`, single Gradle module, no framework, no annotations, no reflection ([ADR 0001](adr/0001-hexagonal-with-a-hand-wired-composition-root.md)). A test asserts the runtime version rather than trusting the build |
| **Interface:** a simple CLI, no UI or client-server | `cli/Cli.java`, `Main.java` | Parse and render are pure functions; `Main` holds the wiring and the exit codes and nothing else. Three commands: `validate-lead`, `review resolve`, `demo`. **Partial** — [G8](#g8) |
| **Spec-driven development:** a brief spec before implementing, visible in git history | `specs/`, `specs/README.md`, `.claude/skills/create-spec/SKILL.md` | Nine specs, each committed to `main` on its own before the branch that implements it. Verifiable: spec 07 landed at 22:59 (`988b557`) and its PR merged at 23:14 (`77148f2`); the same ordering holds for 04, 05, 06 and 08. **Partial** — [G9](#g9) |
| **Robust process documentation:** a comprehensive README describing the entire process | `README.md`, checked by `src/test/java/com/addi/lead/ReadmeTest.java` | Sections *How it was built*, *Working with AI* and *What it cost* are the process; the test fails the build if the README teaches a command the CLI would reject or points at a path that moved |
| **AI artifacts:** all conversation history, complete prompt logs, and any context files used | `docs/ai/sessions/` (14 transcripts), `prompts/`, `docs/ai/cost.md`, `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md`, `.claude/skills/` | Every session that touched this repository has a transcript. The context files are in the repository because they are inputs, not documentation of inputs. **Partial** — [G10](#g10) |
| **Infrastructure:** no external databases or message queues | `Main.java:109-123`, [ADR 0003](adr/0003-persistence-without-a-database.md) | Nothing to install and nothing to start: the lead database is in-memory from `fixtures/leads.csv`, the cache and the review queue are files under `data/`. **Partial** — [G11](#g11) |
| **External Systems:** implement as functions that respond with success or failure | `port/NationalRegistry.java`, `port/JudicialRecords.java`, `port/ComplianceBureau.java`, `port/QualificationScore.java` | Four single-method ports, each returning its own sealed outcome hierarchy. Nothing throws across the boundary — failure is a value ([ADR 0005](adr/0005-failure-taxonomy.md)), which is what makes the exhaustive switch in `Decisions` possible. **Partial** — [G12](#g12) |
| **You must simulate latency for these requests** | `port/Latency.java`, `adapter/SleepingLatency.java`, `fixtures/*.csv` column `latencyMs` | Latency is an injected effect, not a hidden `Thread.sleep`: each fixture row carries its own delay, so lead `1100102030` really takes longer than the 2s scope timeout and the demo fires the real timeout rather than a shrunk one. Tests assert the requested duration without waiting for it. **Partial** — [G13](#g13) |

## Repository contents (brief section 5)

| Requirement | Where | Approach |
|---|---|---|
| A README documenting your decisions | `README.md` *How it works*, *Decisions and assumptions*; `docs/adr/` | Eight ADRs, one decision each, linked from the README table |
| ...and your assumptions | `docs/assumptions.md` | 34 one-line calls, each tagged with the spec that hit the ambiguity or `[brief]` for calls made before any code existed |
| ...and how you refined output to meet our standards | `README.md` *Working with AI*, final paragraph | Three mechanisms, none of them prompting: the spec, `./gradlew check` as the same gate locally and in CI, and a human reading every PR |
| Full project history (prompts / AI chat logs) | `prompts/`, `docs/ai/sessions/` | See [G10](#g10) |
| What worked well and wrong using AI | `README.md` *Working with AI* | Four findings, each with a commit or a number behind it: the substitutable-model result, the cost of splitting planning from implementation, unenforced bookkeeping getting skipped, and confident planning around a formatter that cannot run on JDK 25 |
| All pending improvements based on your assessment | `README.md` *Pending improvements* | Split into deliberate scope cuts and real gaps, the latter in the order they would be fixed |

## Declared gaps

<a id="g1"></a>**G1 — "Three distinct validations" is implemented as four.** The brief
says three and then lists four bullets. Four was the reading and the
qualification score is the fourth; if three was meant, one of these steps is not
supposed to exist. [assumptions.md](assumptions.md#reading-the-brief)

<a id="g2"></a>**G2 — The registry match excludes email.** Name and birthdate are
compared; email is not, because a civil registry does not hold email addresses.
It is format-validated at seed time (`domain/Email.java`) and carried onto the
prospect. A reviewer expecting all five CRM fields to be matched will find four.

<a id="g3"></a>**G3 — Judicial rejects on any record count of one or more.** The
brief says "no records" and gives no severity or recency dimension, so none was
invented. A real archive check almost certainly distinguishes a spent traffic
matter from an open proceeding.

<a id="g4"></a>**G4 — The cache is durable, but narrow.** Only the bureau is
cached, because that is the only step the brief asks durability of. The file is
read and rewritten whole on every write, with no eviction and no compaction —
linear in the number of leads ever seen, fine at fixture scale and stated as
such in [ADR 0003](adr/0003-persistence-without-a-database.md). The TTL is fixed
at 24h in `app/Config.java` with no flag. Cross-process safety is only what
atomic rename gives. A row that does not parse is a miss and is dropped by the
next write rather than repaired.

<a id="g5"></a>**G5 — Of the brief's two options, only manual review is
implemented.** There is no "degrade and continue" path: an unavailable
dependency always stops the run at a checkpoint and always opens a case. That is
a deliberate reading — a graceful failure that let a lead through would be a
credit decision made on missing data — but it is one of the two offered
branches, not both. A resumed run also trusts the case file for every step
before the checkpoint and re-executes only the resolved step and its successors,
which is correct while cases are resolved in minutes and wrong once they are
resolved in weeks.

<a id="g6"></a>**G6 — The score is `java.util.Random`, seeded only from
`Config`.** Not `SecureRandom`, and there is no CLI flag for the seed; only the
tests and the demo vary it. In production every run draws fresh, so the same
lead validated twice can convert once and reject once. That is what "random"
means here and it is the reason `README.md` warns that the demo lead is rejected
on the score more often than not.

<a id="g7"></a>**G7 — Parallelism is proven correct, not proven fast, and it
applies to fresh runs only.** `PipelineConcurrencyTest` proves both branches are
genuinely in flight at once and that the scope timeout cancels both; there is no
load or soak test and the thread-per-task assumption is untested above one lead.
A resumed run enters `Pipeline.sequential` and never re-forks, because the steps
before the checkpoint are already answered.

<a id="g8"></a>**G8 — The CLI is smaller than a reviewer may expect.** No
`--help` or `--version`: usage prints to stderr on an input error and nowhere
else. No `review list` or `review show` — the queue is a directory, and `ls` and
`cat` read it. No `--data-dir`, `--timeout` or `--fixtures` flags; `Config` is
injected instead. And the exit code contract holds for the installed
distribution and the Docker image, not for `./gradlew run`, which reports any
non-zero exit as its own build failure 1.

<a id="g9"></a>**G9 — Specs came before implementation, but only 00 to 03 were
written up front.** Specs 04 to 08 were each written at the start of the session
that implemented them — still committed to `main` before the implementing
branch, which is what the brief asks, but written from the code as it had turned
out rather than planned in advance. Spec 06 is the one exception in the other
direction: written in its own session a session early, which cost $5.05 and is
why the practice stopped (`README.md`, *Working with AI*).

<a id="g10"></a>**G10 — The AI artifacts are complete in coverage, uneven in
form.** All 14 session transcripts are in `docs/ai/sessions/`, but they are
terminal captures: the renderer collapses long tool output, so they are a
complete record of the conversation and an abridged record of tool results, not
raw API logs. `prompts/` holds 8 files rather than 14, because the sessions that
were opened with a slash command have no prose prompt to file — their
instruction is the checked-in skill, `.claude/skills/run-spec/SKILL.md` or
`create-spec/SKILL.md`, which is version-controlled and therefore a better
artifact than a transcribed keystroke. Two sessions are unpriced in
`docs/ai/cost.md`: the closeout session that priced spec 08 and the session that
wrote this file, both for the standing reason that a session cannot price its
own log.

<a id="g11"></a>**G11 — "No external database" is met by using the
filesystem.** `data/bureau-cache.csv` and `data/review/<case>` are a persistent
store; they are simply not a *service*. Nothing is installed, nothing is
started, and the whole state is two paths a reviewer can `cat`. If the intent of
the clause was "no persistence at all", this does not meet it — but the same
brief requires a durable mechanism for bureau responses, so it cannot have been.

<a id="g12"></a>**G12 — The external systems are ports backed by CSV fixtures,
not HTTP stubs.** The brief allows "any technique of your choice" and this is
the technique: a single-method interface per system, implemented by an adapter
that reads one file. There is no wire protocol, no serialization and no network
error class, so nothing here exercises retry, connection handling or partial
reads. Swapping an adapter for an HTTP client is a one-line change in the
composition root, and that is the whole argument for the shape — but it has not
been done.

<a id="g13"></a>**G13 — Simulated latency is per fixture row, so an unknown lead
is instant.** A lead with no row in a system's CSV means that system has nothing
on the person, and the adapter returns without pausing
([assumption](assumptions.md#the-simulated-adapters)). Latency is also a single
fixed value per row: no jitter, no distribution, no correlation between calls.
The delay is a real `Thread.sleep` in production and an assertion in tests, so
the suite stays fast without pretending the wait is not there.

## How context was managed

The brief evaluates orchestration, so the numbers behind it, all from
`docs/ai/cost.md`:

- **The bill is re-reading, not writing.** Roughly 96% of all tokens across the
  project were cache reads and well under 1% was output — 97.5% cache reads on
  spec 08, the documentation session. Everything below follows from that ratio.
- **The budgets are in the build.** `scripts/context-budget.py` caps `CLAUDE.md`
  at 40 lines because it is re-read on every request, the three once-per-session
  files at 120, and any spec at 150. It is wired into `./gradlew check`
  (`build.gradle.kts:39-49`) and into CI, so over budget fails the build. Over
  budget means cutting the file, never raising the limit.
- **No MCP servers, ever, and no `.mcp.json`.** Tool definitions sit at position
  zero of the cache prefix and are re-read on every request whether they are
  called or not, and any change to the tool set invalidates the entire cache. A
  repo-specific query is a shell script instead — `scripts/cost.py` and
  `scripts/context-budget.py` are what would otherwise have been a server.
  Session 00 was launched with `--strict-mcp-config` to make that structural
  (`AGENTS.md:19-22`).
- **A well-specified spec made the model substitutable, and that did not make it
  cheaper.** Spec 05 was rated low and run end to end on Sonnet: it merged with
  no shape invented and no criterion dropped, which is the result the experiment
  wanted, at 60 requests and 7.2M tokens against 33 requests and 2.2M for spec
  02 and 35 and 2.5M for spec 01 on Opus. The per-token discount is worth about
  40% and this session spent it and more on re-reading — $3.70 against $2.20 and
  $2.56. The specification carried the cheaper model; there was nothing left for
  it to save.
- **Total: $61.01** across nine implementing sessions and seven that implemented
  no spec — the false start, the planning sessions, the closeouts and the final
  documentation. Non-spec work was 37% of the bill and no projection made during
  the project had a line for it.
