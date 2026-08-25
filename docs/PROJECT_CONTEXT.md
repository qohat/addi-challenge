# Project context

What the brief demands, what was added on top of it, and what is already decided
about the problem. Style lives in `CONTRIBUTING.md`, reasoning in `docs/adr/`;
this file states decisions and never re-argues them.

## What the brief requires

A lead becomes a prospect only by passing four validations, in this order:

    registry --+
               +--> bureau --> score --> converted
    judicial --+

- **National registry** — the person exists in the external registry and their
  data matches the local database.
- **Judicial records** — no records in the national archives system.
- **Compliance bureau (OFAC/sanctions)** — with a persistent cache over its
  responses, and graceful handling or a manual review flow when it is down.
- **Qualification score** — random 0..100, converts only when strictly above 60.

Registry and judicial are non-dependent and must run in parallel. Bureau needs
both to have succeeded. Score needs a clean bureau result.

Also required: a CLI, with no UI and no client-server; external systems as
functions that succeed or fail, with simulated latency; no external database and
no message queue; specs committed before their implementation; a README covering
the whole process; and the AI conversation history, prompt logs and context
files as part of the submission.

## What is mine, not the brief's

- Docker, so a reviewer can run the CLI without installing JDK 25. It never
  becomes a dependency of the build or the tests (ADR 0007).
- CI, running the same aggregate `check` task that runs locally.
- ADRs, this context layer, and a `demo` command covering every outcome path.
- The semantics of manual review, which the brief names but never defines
  (ADR 0004).
- A fraud check between the bureau and the score, asked for after the submission
  closed. It answers a boolean from `fraud.csv`, false when it has no row.

## Decisions about the problem

**Four validations, not three.** The brief says three and then lists four.

**Registry compares the local database record against the external registry
record**, keyed by national ID, on birthdate, first name and last name. The CLI
supplies only the ID; it is an identity claim, not a third set of data.

**Email is not part of the match** — a civil registry does not hold email
addresses. It is format-validated when the local database is seeded, and carried
onto the prospect.

**A national ID absent from the local database is a business rejection**, not an
input error. The CLI syntax was valid; the precondition was not.

**Judicial rejects on any count of one or more.** The brief gives no severity
dimension, so none is invented.

**The score rule is strict**: 60 rejects, 61 converts.

**The score stays genuinely random in production.** Randomness, the clock and
the simulated latency are ports; tests inject fixed values. Determinism is a
test property, not a business one (ADR 0006).

**External behaviour is fixture-driven**, keyed by national ID, each row
carrying its own latency alongside its outcome. Demo runs and integration tests
are reproducible, and one fixture can be slow enough to fire the real timeout.

**Bureau cache**: flat file keyed by national ID, TTL defaulting to 24h and
configurable, written atomically. Only terminal outcomes are cached; an
unavailable bureau never is, because caching an outage turns a transient failure
into a permanent one. A corrupt file is simply not a valid cache (ADR 0003). A
sanctions hit is a terminal rejection and is cached. The cache is read at the
bureau step, not as a pipeline-level short-circuit, because the brief fixes the
dependency order.

**Registry and judicial run under a scope-level timeout**, default 2s and
configurable. On timeout the scope cancels both branches and both report a
timeout — a failure of the outside world, never a rejection (ADR 0005).

**Manual review is a resume, not an override** (ADR 0004). Approving means an
analyst verified the failed step out of band and it came back clean — not that
the lead converts. The pipeline resumes from the checkpoint with that outcome
injected and can still end rejected on the score. A case persists the lead, the
outcomes already resolved, why it stopped, and which step is pending; the
pending step is the first unresolved step in canonical order, so a timeout that
killed both parallel branches needs no special representation. A resolved case
is terminal; a resumed run hitting another unavailable dependency produces a new
case with a later checkpoint. An analyst approval is never written to the bureau
cache — it is not a bureau response and must not expire on a TTL. Approving a
pending score step re-runs the score port, because an analyst cannot attest a
number. It is a product feature serving an analyst at Addi, verified by tests
like everything else and never exercised by hand.

**Nothing is idempotent across invocations.** Running the same lead twice runs
the pipeline twice; only the bureau cache short-circuits work. Converted
prospects are not persisted — the local database is in-memory and the process is
single-shot, so conversion is observable through stdout and the exit code. Only
the bureau cache and the review queue survive an invocation, because their
business purpose requires it.

## The CLI surface

Minimal on purpose: the brief says a simple CLI is sufficient and rules out a
UI. Each command gets its parsing in the spec that gives it behaviour, so no
flag exists before the behaviour behind it.

- `validate-lead --id <nationalId>` — the lead already exists in the CRM, which
  is the local database, so the ID selects the record and every other field is
  read from it.
- `review resolve <case> --approve|--reject` — the only analyst command. The
  queue directory is browsable with `ls` and `cat`.
- `demo` — every outcome path, from fixtures.

Exit codes are part of the contract, so a script can branch on the decision
without parsing text: 0 converted, 1 rejected, 2 pending manual review, 3 input
error. Runtime state — the bureau cache and the review queue — lives in a
configurable directory, `./data` by default, and is gitignored.
