# 0006. Determinism is a test property, not a business one

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

Three sources of nondeterminism are required by the brief, not incidental to it:
the qualification score is "a random score between 0 and 100", the external
systems must "simulate latency", and anything with a TTL needs a clock.

Tests have to be deterministic. The tempting fix is to make the system
deterministic — seed the score generator from a constant, or make the score a
hash of the national ID. That is the wrong repair. It removes a behaviour the
brief specified in order to make testing convenient, and it makes the demo lie:
the same lead would score the same forever, which is not what "random" means.

The same trap in a smaller form is the timeout. If latency is a fixed global
constant, the only way to exercise the 2s scope timeout is to shrink the timeout
in a test, and the timeout path is never demonstrated against the real value.

## Decision

The score stays genuinely random in production. The randomness port is seeded
from configuration and defaults to a fresh seed per process.

Randomness, the clock and the simulated latency are all ports. Tests inject
fixed seeds, a fixed instant and zero or controlled delays. Determinism is
obtained by injection at the boundary, never by removing variance from the
domain.

External system behaviour is fixture-driven, keyed by national ID, with each
fixture row carrying its own latency alongside its outcome, and combined with an
injectable failure policy. Demo runs and integration tests are therefore
reproducible, and one fixture can be slow enough to fire the real timeout at its
real configured value.

The domain stays pure, so tests come out deterministic by construction rather
than by effort. If something is hard to test deterministically, an effect leaked
where it should not have, and the fix is the leak.

## Consequences

Two properties can be tested that are usually only asserted: that the timeout
fires at the configured value, and that the score is genuinely variable across
runs while the rule applied to it is exact.

No test needs a retry, a sleep or a timing tolerance, which is the usual source
of a flaky suite.

The demo becomes a data-authoring exercise rather than a code-writing one. Every
outcome path — convert, reject at each of the four steps, timeout into review,
cache hit, resume — is a fixture row, and adding a case does not touch Java.

The cost is three more ports and a fixture format to maintain, on a project
where a static `Random` would have been one line. It is also possible to write a
fixture that is internally inconsistent, and nothing catches that but a test.

Fresh-seed-per-process means the same command can give different answers on
consecutive runs. That is correct and it will surprise someone; the exit code
contract exists partly so a script can cope with it.

## Alternatives rejected

- **Seed the score from the national ID** — makes the system deterministic to
  make tests easy, and contradicts "random" in the brief.
- **A fixed global seed in production** — the same, less honestly.
- **Mock the random source with a mocking framework** — a dependency to do what
  a five-line fake record does, and it would only cover the score, leaving the
  clock and latency untouched.
- **Real randomness in the external system failures** — makes the demo
  unrepeatable and the integration tests flaky, for no gain over fixtures.
- **A global latency constant with the timeout shrunk in tests** — the timeout
  path is then only ever exercised at a value the product never uses.
