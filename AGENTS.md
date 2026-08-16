# Operating rules

Read at the start of a session. Style rules are in `CONTRIBUTING.md` and get
read later, when there is Java to write.

## Boundaries

Permissions are skipped so the speed of this approach survives, which makes
these rules rather than intentions.

- Nothing is written outside the project root.
- **Exception, read-only:** `scripts/cost.py` and
  `scripts/context-budget.py --measured` read the session logs at
  `$CLAUDE_CONFIG_DIR/projects/<repo-slug>/*.jsonl`, outside the repo. They read
  and never write.
- No recursive deletes. No history rewriting. No force pushes.
- No new dependency without asking first — that is an architecture decision, not
  a convenience.
- No MCP servers and no `.mcp.json`, ever. Tool definitions sit at position zero
  of the cache prefix and are re-read on every request whether called or not,
  and any change to the tool set invalidates the whole cache. A repo-specific
  query is a shell script instead.

## Session workflow

One spec per session, opened clean, exported before it closes.

1. Close out the previous spec first — a session's log is only complete once
   that session has closed. `/run-spec` does this.
2. The spec commit lands on `main` before its worktree exists. Never both in one
   PR.
3. Worktree per spec at `.trees/NN-slug`.
4. Tests written from the spec, watched to fail, then the code.
5. `./gradlew check` passes before the PR exists.
6. One PR per spec, split into reviewable commits. The author reads and merges
   it. Nothing advances without that.

A session past roughly forty requests, or touching two specs, has gone wrong.
Stop it and open a new one.

The model is chosen when the session opens, never mid-session — caches are
per-model, and switching at turn 30 throws the entire prefix away.

## Commands

    ./gradlew check                      the gate: format, build, tests, budgets
    ./gradlew test --tests '*NameTest'   one test class
    ./gradlew run --args='...'           the CLI, natively
    python3 scripts/context-budget.py    line budgets, exit 1 on breach
    python3 scripts/cost.py --list       sessions, to find an id
    python3 scripts/cost.py --session X  cost of one spec

Spec 00 note: Gradle 9 refuses `gradle wrapper` in an empty directory and fails
with "does not contain a Gradle build". Write `settings.gradle.kts` first.

## Reading a failing test

Never re-run the suite to see a message that is already on disk.

    Grep '<failure' build/test-results/test/*.xml -l    which class failed
    Read that one file                                  the message and trace

`build/reports/tests/test/index.html` is for humans, not for reading here.

## Searching without burning context

The codebase is queried, not read.

- **Grep** for usages, symbols, strings. It returns matching lines.
- **Glob** for paths. It returns paths.
- **Read with an offset** for a slice of a file already known.
- **A subagent** when the search is wide and the answer is small — its reading
  and its dead ends happen in its own context and only the answer comes back.
  Not when the raw material itself is needed; that is paying to have it
  summarised.
- Never a directory tree. No `ls -R`, no `tree`, no bare `find .`.

## What to load when

| Doing | Load |
|---|---|
| Orienting | this file, `specs/README.md`, the spec, `git log --oneline -15` |
| Writing Java | `CONTRIBUTING.md` |
| Questioning a decision | `docs/adr/` |
| Hitting an ambiguity | `docs/assumptions.md`, then add a line to it |

## Documentation duties

Every spec states whether it needs docs and why; if it says no, nothing is
written. A decision with reasoning becomes an ADR. A one-line call where the
brief was silent becomes a line in `docs/assumptions.md`, tagged with the spec
that hit it. Session exports go to `docs/ai/sessions/NN-slug.txt`, costs to
`docs/ai/cost.md`.
