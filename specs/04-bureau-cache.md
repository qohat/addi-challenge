# 04. The bureau cache

## Goal

A lead the compliance bureau has already answered for is not screened again. A
terminal bureau answer is written to a file under `./data`, keyed by national ID,
and read back until it expires, so a second `validate-lead` for the same person
decides the same way with none of the bureau's latency and does it in a later
process. This is the persistent and durable mechanism the brief asks for.

## Scope

- A caching decorator over the compliance bureau, behind the existing port.
- The cache file: its format, its atomic write, its expiry rule.
- The data directory, the TTL and the clock.

## Not in scope

- A cache over registry, judicial or score. The brief asks for one over bureau
  responses and gives the other three no such requirement.
- The score threshold — spec 05. Any score still converts.
- The review queue, which shares the data directory but not this file, and the
  rule that an analyst approval is never written to the cache — spec 06.
- Eviction, compaction, size limits, and any concurrent-writer story beyond what
  rename gives. ADR 0003 accepts reading the file whole at this scale.
- A `--no-cache` flag or a `cache clear` command. Deleting the file is both of
  those and needs no code.

## Design decisions

**The cache is a decorator, not a new port.** `CachingComplianceBureau(
ComplianceBureau delegate, Path file, Duration ttl, Clock clock)` implements
`ComplianceBureau` and wraps `FixtureComplianceBureau` in the composition root.
The pipeline, the domain and `BureauOutcome` are untouched: whether an answer
came from the cache is not a different decision, and nothing above the
composition root can tell.

**The clock is `java.time.Clock`.** It is already the standard library's
injection point — `Clock.systemUTC()` in `Main`, `Clock.fixed` in tests. A
`port.Clock` with one `Instant now()` would be that class with the name changed.
`Latency` and `Randomness` exist because `Thread.sleep` and `Random` are not
interfaces; this one is.

**`Main` uses the system clock and no test injects one end to end.** Adding a
`Clock` parameter to `Main.run` buys one thing, and an end-to-end expiry test
gets it more honestly by writing a cache file with an old instant into the data
directory, which is also the only way to prove the file is read rather than
remembered.

**One file, `bureau-cache.csv` in the data directory**, header row, the same
comma-separated unquoted convention as the fixtures:

| Column | Meaning |
|---|---|
| `id` | national ID, the key |
| `recordedAt` | ISO-8601 instant, from the clock |
| `outcome` | `CLEAR` or `SANCTIONED` |
| `list` | the sanctions list, empty for `CLEAR` |

One row per person, last write wins. A sanctions list containing a comma is
unrepresentable, exactly as it already is in `bureau.csv`.

**Only `Clear` and `Sanctioned` are written.** An `Unavailable` leaves the file
exactly as it was, including a stale row for the same person, because caching an
outage turns a transient failure into a permanent one for the length of the TTL
(ADR 0003). A sanctions hit is terminal and is cached like any other answer.

**A read failure is a miss, never an error.** No file, no row, an unparseable
instant, an unknown outcome word, the wrong number of columns, an unreadable
directory: all of them mean not cached, the delegate runs, and nothing
propagates into the domain (ADR 0005).

**A write rewrites the whole file, atomically.** Read the rows that parse,
replace or append the one for this ID, write a sibling temp file, then
`ATOMIC_MOVE` over the target. The data directory is created if it is absent. A
row that did not parse is dropped by that rewrite, which is the only pruning
there is; expired rows for other people stay until something rewrites them,
since they already read as a miss.

**Expiry is `recordedAt.plus(ttl)` against now, and the boundary is expired.**
One comparison, no tolerance window.

**`Config` gains `Path data`, defaulting to `./data`, and `Duration
bureauCacheTtl`, defaulting to 24 hours.** Still no CLI flags: tests and the demo
are the only callers that need to change them.

## Acceptance criteria

1. A second `screen` of the same lead inside the TTL returns the same outcome,
   the delegate is not called again, and no latency is requested.
2. The first `screen` of a clean lead writes one row carrying the ID, the fixed
   clock's instant, `CLEAR` and an empty list.
3. `Sanctioned` is cached with its list and served from the file on the second
   call without the delegate.
4. An `Unavailable` writes nothing: with no prior entry the file still does not
   exist, and the next `screen` calls the delegate again.
5. An `Unavailable` after an expired entry leaves that row byte for byte as it
   was.
6. An entry recorded exactly one TTL ago is a miss; one millisecond less is a
   hit.
7. A file written by one instance is served by a second instance built over the
   same path whose delegate fails the test if it is called.
8. A garbage row, an unparseable instant and an unknown outcome word are each a
   miss with no exception escaping, and the next write leaves a file holding the
   rows that parsed plus the new entry.
9. Caching one lead does not disturb another's row: both survive a write, and
   the untouched one keeps its instant.
10. After a write the data directory holds `bureau-cache.csv` and nothing else,
    and it is created when it was absent.
11. End to end, `validate-lead` for a bureau-clean lead, then the same command
    with `bureau.csv` deleted, converts both times and prints the identical line.
12. End to end, an entry older than the TTL written by hand into the data
    directory is ignored and the run decides from the fixture.

## Effort

medium. ADR 0003 fixed the mechanism and spec 03 fixed the port, so what is left
is a file format, a boundary rule and the wiring. Nothing here leaves a shape for
05 or 06 to inherit except the data directory, which they only reuse.

## Docs

Two lines in `docs/assumptions.md` tagged `[04]`: the clock is `java.time.Clock`
rather than a port of our own, and a row that does not parse is dropped by the
next write rather than repaired. No ADR — ADR 0003 carries the reasoning and this
spec only fixes columns and a boundary. The README is spec 08.
