# 0003. Persistence without a database

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

The brief pulls in two directions. It rules out external databases and message
queues, and it asks for a "simple Persistent/Durable mechanism" for the
compliance bureau responses so external calls are optimised and latency is
absorbed. It also asks for a manual review flow, which is only meaningful if a
case outlives the invocation that created it.

So three things need storing, and they do not need the same storage:

- The local database of leads. Read-only during a run, seeded from fixtures,
  and by the brief's own framing it is "our local database" rather than
  something this CLI owns.
- The bureau cache. Must survive the process, or it optimises nothing.
- The review queue. Must survive the process, or manual review is theatre.

An embedded database (H2, SQLite, MapDB) would cover all three, but it is a
dependency, it needs a schema and a migration story, and "no external databases"
reads as an instruction to keep infrastructure out of the exercise rather than
to swap the socket for a file handle.

## Decision

Three different mechanisms, each matched to what it actually needs.

**Local database**: a `ConcurrentHashMap` behind a repository port, seeded at
startup from versioned fixtures. In-memory, because nothing writes to it.

**Bureau cache**: a flat file keyed by national ID, holding the outcome and the
time it was recorded. TTL defaults to 24h and is configurable. Only terminal
outcomes are cached. An unavailable bureau is never cached.

**Review queue**: one file per case in a directory, the filename being the case
ID, `<nationalId>-<timestamp>` from the clock port. Multiple open cases per
person are therefore possible, which is required, since a resumed run that hits
another unavailable dependency produces a new case with a later checkpoint.

Both file-backed stores are written atomically: write a temp file, then rename.
A corrupt file is not treated as a valid cache entry and is simply ignored.

Both live in a configurable directory, `./data` by default, gitignored, and
pointed at a temp directory by tests.

## Consequences

No dependency, no schema, no migrations, and a reviewer can `cat` the cache and
the queue to see exactly what the system decided. Tests get real persistence
behaviour against a temp directory rather than a mock.

Never caching an unavailable bureau is the load-bearing part. Caching an outage
converts a transient failure into a persistent one for the length of the TTL,
which is the failure mode this cache exists to prevent, not to cause.

Atomicity by rename matters because a partially written file is
indistinguishable from a corrupt one, and both would be silently discarded —
losing a review case rather than a cache entry, which is not recoverable.

The costs are real. There is no concurrent-writer story beyond what rename
gives, no compaction, and the cache file is read whole. At this scale that is
fine; at any real volume it is not, and this ADR would need superseding.

Treating a corrupt file as a cache miss also hides corruption rather than
reporting it. Deliberate: a corrupt cache file does not model anything real
about a distributed cache, and handling it properly buys nothing here.

## Alternatives rejected

- **H2, SQLite or MapDB** — a dependency plus a schema for three key-value
  lookups, and against the spirit of "no external databases".
- **In-memory cache only** — does not survive the process, so it satisfies
  neither "persistent/durable" nor the point of caching in a single-shot CLI.
- **Java serialization** — brittle across class changes and a deserialization
  attack surface for no gain over a text format.
- **One file for the whole review queue** — every write rewrites every case, and
  concurrent resolves would clobber each other.
