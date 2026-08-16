# 03. Simulated adapters and structured concurrency

## Goal

The stubs are gone. Registry and judicial run at the same time under
`StructuredTaskScope.open()` with a scope-level timeout, all four systems answer
from fixtures that carry their own latency, and the registry comparison is real.
From here `validate-lead` can convert, reject at any of the four steps, or come
back pending because something was down or too slow. This is the spec the brief
is actually about.

## Scope

- Four fixture-driven adapters, keyed by national ID.
- The fixture files, their loader, and the lead seed.
- The registry comparison as a pure domain function.
- The parallel stage with a configured timeout.
- A latency port and a randomness port.
- A `Config` record built in the composition root.

## Not in scope

- The bureau cache — spec 04. The bureau adapter reads its fixture every time.
- The score threshold — spec 05. A score still converts whatever it is.
- Persisting a pending case — spec 06. Pending is printed and exits 2; nothing
  reaches disk.
- The demo command — spec 07.

## Design decisions

**Fixtures are one CSV per system** in `src/main/resources/fixtures/`, header
row, comma separated, split by hand. No parser dependency: the format exists to
be read by a reviewer, not to be general.

| File | Columns |
|---|---|
| `leads.csv` | `id,firstName,lastName,birthDate,email` |
| `registry.csv` | `id,firstName,lastName,birthDate,latencyMs,status` |
| `judicial.csv` | `id,recordCount,latencyMs,status` |
| `bureau.csv` | `id,sanctionsList,latencyMs,status` |
| `score.csv` | `id,latencyMs,status` |

`status` is `UP` or `DOWN`, and `DOWN` produces that step's `Unavailable`. A
missing row means the system has nothing on this person: registry `NotFound`,
judicial `Clear`, bureau `Clear`, score `UP` with no latency. A missing
`leads.csv` row is `LeadNotInDatabase`.

**`score.csv` carries no value.** The number comes from the randomness port on
every call, because the brief says random and ADR 0006 says determinism is
obtained by injection rather than by removing variance.

**The fixture directory is a `Config` value, not a CLI flag.** Tests point it at
their own directory and spec 07 ships its own; nothing outside the process needs
to choose one.

**The registry comparison is domain, not adapter.** The adapter reads a
`RegistryRecord(NationalId id, String firstName, String lastName, LocalDate
birthDate)` from the fixture and hands it to a pure function that compares it
against the `Lead` on first name, last name and birth date, returning `Matched`
or `Mismatch` with the differing field names in that order. Names compare
case-insensitively after trimming. Email is not compared
(`docs/assumptions.md`). The comparison is the interesting rule in this spec and
it is testable without touching a file.

**The parallel stage**, in the orchestration layer, never in the domain:

```java
try (var scope = StructuredTaskScope.<StepOutcome, Void>open(
        Joiner.awaitAll(),
        cf -> cf.withTimeout(config.parallelTimeout()))) {
    var registry = scope.fork(() -> registryPort.check(lead));
    var judicial = scope.fork(() -> judicialPort.check(lead));
    scope.join();
    ...
}
```

`awaitAll` is the joiner because the adapters return values and never throw; a
joiner that unwraps exceptions would be describing a failure mode this design
has removed. `join()` throws `StructuredTaskScope.TimeoutException` when the
scope times out, and both subtasks are cancelled. Any subtask whose `state()` is
not `SUCCESS` is read as that step's `Unavailable("timeout after 2s")`, which
covers cancellation without a second code path. No partial result is used: the
next step needs both, and the checkpoint names `REGISTRY` because it is first in
canonical order (ADR 0002, ADR 0004).

**Effects are ports.** `Latency.pause(Duration)` and `Randomness.nextInt(int
bound)`, both injected. The score adapter calls `nextInt(101)` for an inclusive
0..100. No clock port yet — nothing has a TTL until spec 04.

**`Config` holds the parallel timeout (default 2s), the fixture directory and an
optional seed**, and is constructed in `Main`. No new CLI flags: configurable
means injectable, and tests are the only caller that needs to change these.

**Adapters catch everything.** An unreadable file, a malformed row, a bad
number: caught at the adapter and returned as that step's `Unavailable` with the
message. Nothing throws into the domain (ADR 0005).

## Acceptance criteria

1. Registry and judicial are inside their calls simultaneously. A fake latency
   port blocks each caller on a shared `CountDownLatch(2)` so neither is
   released until both have arrived; sequential execution cannot pass it. No
   sleeps, no elapsed-time tolerance.
2. With the registry fixture slower than the configured timeout, `join()` times
   out, both branches report `Unavailable`, the decision is
   `PendingManualReview` at `REGISTRY`, and the CLI exits 2.
3. That holds even when judicial finished successfully first: a completed
   sibling result is not used.
4. A registry row differing on birth date yields `Mismatch(["birthDate"])`; a
   row differing on two fields lists both in field order; a row differing only
   in letter case or surrounding spaces matches.
5. A national ID with no `registry.csv` row exits 1 with `RegistryNotFound`.
6. `judicial.csv` with `recordCount` 1 rejects with `JudicialRecords(1)`; 0 is
   `Clear` and the run continues.
7. `bureau.csv` with a non-empty sanctions list rejects with `Sanctioned`;
   `status=DOWN` gives pending at `BUREAU` and exit 2.
8. `score.csv` with `status=DOWN` gives pending at `SCORE`.
9. Each adapter requests exactly the latency its fixture row states, asserted
   through the fake latency port. No test uses the real one.
10. A seeded randomness port gives the same score twice; the score is always
    within 0..100 inclusive.
11. A malformed row and a missing fixture file each produce that step's
    `Unavailable`, and no exception escapes any port.
12. The clean path still converts end to end through the CLI.

## Effort

high. The concurrency shape, the fixture format and the comparison rule are all
fixed here, and specs 04 to 07 sit on them.

## Docs

Three lines in `docs/assumptions.md` tagged `[03]`: a missing fixture row means
the system has nothing on that person; the registry adapter supplies a record
and the comparison is a pure domain function; the score value is never
fixtured. No ADR — ADR 0002 and ADR 0006 carry the reasoning, and this spec only
fixes columns.
