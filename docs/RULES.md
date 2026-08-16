# Rules for this project

Decisions I've made for the Addi lead validation challenge specifically. How I
work in general is in `BRAIN.md`; the requirements are in `docs/brief.pdf`.

If something isn't here and isn't in `BRAIN.md`, it isn't decided — ask me
rather than guess.

## Stack

Java 25 with `--enable-preview`, because structured concurrency is still preview
and it's the concurrency model this problem wants. Gradle, single module. No
framework: the exercise doesn't need one and plain Java is the better
demonstration.

Configuration is explicit and dependency injection is by hand in a composition
root. No annotations, no autoconfiguration, no reflection magic. I want the
wiring readable in one file.

Hexagonal architecture and DDD, with ADTs in the domain.

The local database is an in-memory `ConcurrentHashMap` behind a repository port,
seeded from versioned fixtures. The bureau cache and the manual review queue are
file-backed — that's my reading of "persistent/durable" given the brief rules
out external databases.

Docker exists so a reviewer can run the CLI without installing a JDK 25 or
fighting preview flags. It must never become a dependency of the build or the
tests; the app always runs natively through Gradle too. Build and runtime stages
pin the same JDK image, since classes compiled with `--enable-preview` only run
on the same release.

Checks: format, build, unit tests, integration tests and the context budget,
behind one aggregate `check` task. That task is the gate and CI runs it on every
PR.

Everything in English — code, commits, specs, docs, log messages.

## Environment, verified before starting

Java 25.0.4 Temurin via SDKMAN, with `JAVA_HOME` pointing at it. Gradle 9.7.0,
also via SDKMAN. Docker 29.6.2. All confirmed working together.

Two things that will waste your time if you don't know them:

**Gradle 9 won't create a wrapper in an empty directory.** `gradle wrapper`
fails with "does not contain a Gradle build". Write `settings.gradle.kts` first,
then run it. That's the order spec 00 has to use.

**`StructuredTaskScope` in Java 25 is opened with a factory, not a
constructor.** The Java 21 shape (`new StructuredTaskScope<>()`, with
`ShutdownOnFailure` subclasses) does not compile here. Java 25 is the fifth
preview and the API is:

```java
try (var scope = StructuredTaskScope.open()) {
    var registry = scope.fork(() -> ...);
    var judicial = scope.fork(() -> ...);
    scope.join();
    use(registry.get(), judicial.get());
}
```

I verified this on my machine before starting: two 300ms branches complete in
307ms, so the parallelism is real. If you write the Java 21 API, that's a
training-data artefact — use the shape above.

## What I've decided about the problem

The brief says "three distinct validations" and then lists four. It's four.

Registry validation involves three data sources: the CLI input, the local DB
record, and the external registry. The input is only an identity claim. The
comparison is the local DB record against the external registry record, keyed by
national ID, on birthdate, first name and last name.

Email is not part of the registry match — a civil registry doesn't hold email
addresses. It's format-validated at parse time and carried onto the prospect.

A lead whose national ID isn't in the local DB is rejected, not an input error.
The CLI syntax was valid; the business precondition wasn't.

Judicial records reject on any count of one or more. The brief says "no records"
with no severity dimension, so I'm not inventing a threshold.

The score rule is strict: 60 rejects, 61 converts. "Greater than 60" read
literally.

The score stays genuinely random in production. The randomness port is seeded
from configuration and defaults to a fresh seed per process; tests inject a
fixed seed. Determinism is a test property, not a business one. Same for the
clock and for the simulated latency the brief requires — all three are
injectable ports, otherwise TDD goes flaky.

Bureau cache: flat file keyed by national ID, TTL defaulting to 24h and
configurable. Only terminal outcomes get cached. An unavailable bureau is never
cached, because caching an outage turns a transient failure into a persistent
one. A corrupt file simply isn't treated as a valid cache — a corrupt file
doesn't model anything real about a distributed cache and complicating it buys
nothing.

Cache and review-queue files are written atomically: temp file plus rename. A
partially written file is indistinguishable from a corrupt one.

Registry and judicial run under a scope-level timeout, default 2s, configurable.
On timeout the scope cancels both branches and both report a timeout failure,
which maps to pending manual review. A timeout is a failure of the outside
world, never a rejection.

Manual review is a **product feature**, not a step in my process — it's the
brief's "trigger a manual review flow", and the analyst it serves works at Addi,
not here. Like everything else it's verified by tests; I never exercise it by
hand.

Manual review is a resume, not an override. The brief doesn't define what the
flow does, so: approving means the analyst
verified the failed step out of band and it came back clean. It does not mean
the lead converts. The pipeline resumes from the checkpoint with that outcome
injected, runs the remaining steps, and can still end rejected if the score
lands under 60 — only the score rule converts a lead. Rejecting closes the case.
A review case is a checkpoint rather than a ticket: it persists the lead, the
outcomes already resolved, why it stopped, and which step is pending. The same
orchestrator serves both paths, so a resumed run is an ordinary run entering
with a pre-resolved prefix and there's no duplicated logic. A manual approval is
never written to the bureau cache, because an analyst decision isn't a bureau
response and mustn't expire on a TTL. A resolved case is terminal. If a resumed
run hits another unavailable dependency it produces a new case with a later
checkpoint.

The pending step of a checkpoint is the first unresolved step in canonical
order, so a timeout that killed both parallel branches needs no special
representation.

Approving a pending score step can't inject a value — an analyst can't attest a
numeric score. It re-runs the score port and the rule still applies.

Exit codes are part of the CLI contract: converted, rejected, pending manual
review and input error are distinct codes, so a script can branch on the
decision without parsing text.

External system failures are driven by versioned fixtures keyed by national ID
plus an injectable failure policy, not by real randomness, so demo cases and
integration tests are reproducible.

Nothing is idempotent across invocations. Running the same lead twice runs the
pipeline twice; only the bureau cache short-circuits work. Converted prospects
aren't persisted — the local DB is in-memory and the process is single-shot, so
conversion is observable through stdout and the exit code. Only the bureau cache
and the review queue need to survive an invocation, because their business
purpose requires it.

## Order

The thing this brief is actually about is registry and judicial in parallel,
then bureau, then score. I want that running by the third spec, with stubbed
adapters if that's what it takes, and everything after it deepening something a
reviewer can already see.

Roughly: setup and CI; domain types with the pipeline and all four ports
stubbed; the real simulated adapters plus structured concurrency; the CLI for
`validate-lead` only; then the bureau cache, manual review, and the score rule;
then the demo and the final docs.

The rejection cause type gains a case when manual review lands and another when
the score rule lands. Both break every exhaustive match over it. That's fine and
each site gets fixed deliberately.

## What I'm not building

The brief says a simple CLI is sufficient and explicitly rules out a UI or a
client-server solution. I'm treating that as a scope limit, not an invitation.
Last time I wrote a 216-line argument parser for four commands, three of which
had no behaviour behind them for another four specs.

No external databases, no message queues — the brief rules those out.

Docker, CI, ADRs and a demo command are additions the brief never asked for.
They're deliberate and each has to earn its place, and I want them labelled as
mine rather than defended as if the client had asked for them.
