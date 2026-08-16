# 02. The `validate-lead` command and the composition root

## Goal

`./gradlew run --args='validate-lead --id 1001'` prints one line of prose and
exits with the code that matches the decision. The stubs still supply the
outcomes, so every run converts, but the path from a command line to an exit
code is real and complete, and every spec after this one deepens something a
reviewer can already run.

## Scope

- Hand-written argument parsing for one command.
- `Main`, the exit code map, and the stdout/stderr split.
- Rendering a `Decision` as one line of prose.
- A composition root that builds the whole object graph in one place.
- Usage text.

## Not in scope

- `review resolve` and `demo` — specs 06 and 07.
- Any flag whose behaviour does not exist yet. No `--data-dir`, no `--timeout`,
  no `--fixtures`: each arrives with the spec that gives it something to do.
- Real outcomes. Registry, judicial, bureau and score are still stubs — spec 03.

## Design decisions

**Parsing is a function over `String[]` returning a sealed type**, not a
library. CLAUDE.md forbids a dependency for what a switch over three tokens
does.

```java
sealed interface Invocation {
    record ValidateLead(NationalId id) implements Invocation {}
    record InputError(String message)  implements Invocation {}
}
```

`Invocation` gains a case per command in specs 06 and 07.

**`NationalId.parse(String)` returns `Optional<NationalId>` and accepts any
non-blank token containing no whitespace.** Nothing stricter: an ID that is
well formed and absent from the database is a business rejection
(`docs/assumptions.md`), so the syntactic rule only has to catch empty input.

**Exit codes come from one exhaustive switch over `Decision`** in `Main`:
`Converted` 0, `Rejected` 1, `PendingManualReview` 2. `InputError` is 3 and
never reaches that switch, which is why `Decision` has no fourth case.

**stdout carries the decision line and nothing else. stderr carries usage and
input errors.** On exit 3 stdout is empty, so a script can read stdout exactly
when the exit code says there is something to read.

**One line per decision**, prose, no keys and values
(`docs/assumptions.md`):

```
Lead 1001 converted to prospect. Score 75.
Lead 1001 rejected: not found in the local database.
Lead 1001 rejected: national registry data does not match on birthDate, lastName.
Lead 1001 pending manual review at step BUREAU: connection refused.
```

Rendering is a pure function from `Decision` to `String` in the CLI layer, not
in the domain, with an exhaustive switch over `RejectionCause` inside it. That
switch gains a case in spec 05 and another in spec 06, and spec 06 appends the
case ID to the pending line.

**The object graph is built in `Main`, in one method, top to bottom** (ADR
0001). No other class constructs an adapter, and there is no `Wiring` class to
introduce until the graph outgrows a screen.

**The CLI is tested without spawning a JVM.** Parsing, rendering and the exit
code map are pure functions tested directly; one end-to-end test runs the wired
application in-process against captured streams.

## Acceptance criteria

1. `validate-lead --id 1001` for a seeded lead exits 0 and prints the conversion
   line including the score.
2. `validate-lead --id 9999` for an unseeded ID exits 1 and prints the
   not-in-the-local-database line.
3. The exit code map returns 2 for `PendingManualReview` — a unit test over a
   constructed decision, since the stubs cannot produce one yet.
4. `validate-lead` with no `--id` exits 3, prints usage on stderr and prints
   nothing on stdout.
5. `validate-lead --id ""`, `--id "   "` and `--id "12 34"` exit 3.
6. An unknown command exits 3 with usage on stderr.
7. No arguments at all exits 3 with usage on stderr.
8. Rendering produces a non-empty line for every current `Decision` case and
   every current `RejectionCause` case.
9. The conversion line names the score, so a reviewer can see the number the
   decision was made on.

## Effort

medium. Every shape is fixed here or in ADR 0008; the work is the parser, the
prose and the wiring.

## Docs

One line in `docs/assumptions.md` tagged `[02]`: a national ID is any non-blank
token without whitespace, and everything stricter is a business rejection rather
than an input error. No ADR — ADR 0001 already covers the hand-wired
composition root.
