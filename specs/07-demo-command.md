# 07. Demo command and fixtures

## Goal

`demo` runs every outcome the system can produce — a conversion, all seven
rejection causes, a pending case, an analyst approving one and rejecting one, a
timeout and a bureau cache hit — as real commands against the shipped fixtures,
printing each command, its output and its exit code. A reviewer sees the whole
system without being told which national ID does what.

## Scope

- The `demo` command: the scenario list, the transcript format, its exit code.
- The fixture rows every path needs, added to `fixtures/` so each one is also
  reachable by hand with `validate-lead --id`.

## Not in scope

- Any change to the pipeline, the ports, the adapters or the decision types. The
  demo runs the wired application; a path it cannot reach is a bug in this spec's
  fixtures, never a reason to touch business code.
- The README that explains the demo — spec 08.
- A pending case at `SCORE`, and registry or judicial down individually. Pending
  is one outcome and scenario 8 shows it; the resume rule is `CheckpointTest`'s.
- Flags on `demo`: a demo a reviewer has to configure is a worse demo.

## Design decisions

**`Demo` is package-private in `com.addi.lead` and calls `Main.run`.** One method,
`static int run(Config config, PrintStream out, PrintStream err)`, looping over a
`static final List<Scenario>`. Every scenario goes through the real parse, the
real wiring and the real exit code, so the demo cannot drift from the CLI. `Main`
gains one arm, `case Cli.Invocation.Demo ignored -> Demo.run(config, out, err)`.

**A scenario is `record Scenario(String title, long seed, List<String[]> commands)`.**
Commands rather than one command, because two scenarios need a `validate-lead`
and then a `review resolve` against the state the first one left.

**Each scenario carries its own seed and its own data directory**, so no scenario
depends on the order they run in. The directory is `<temp>/N` under one
`Files.createTempDirectory("lead-demo")`, printed on the first line of the
transcript; nothing is deleted, and `./data` is neither read nor written. The
scenario config is `config` with `seed` and `data` replaced, so the fixtures
directory and the timeout stay the caller's.

**Two seed constants**, chosen at implementation: one whose first draw is above
60 and one at or below it. That is the only lever on the score — `[03]` keeps the
value out of the fixtures — and a fresh `RandomNumbers` per `Main.run` makes the
first draw the score every time.

**The token `<case>` in a command is replaced by the single filename in that
scenario's `review` directory** before the command is parsed, so the transcript
shows the real case ID. Exactly one file is expected; more than one or none is an
`IOException` and exits 4.

**The transcript**, one block per scenario, blank line between:

```
== 8. The bureau is down, an analyst approves, the lead converts
$ validate-lead --id 1090001020
Lead 1090001020 pending manual review at step BUREAU: the compliance bureau is down.
Case 1090001020-1755300000000 opened.
exit 2 in 412ms
$ review resolve 1090001020-1755300000000 --approve
Lead 1090001020 converted to prospect. Score 67.
exit 0 in 61ms
```

Elapsed millis because it is the only place the cache hit and the timeout are
visible; the acceptance test blanks it before comparing.

**`demo` exits 0 whenever it ran to the end**, whatever the scenarios exited.
Their codes are output, not the demo's verdict, and the acceptance test is what
fails when one changes. An `IOException` from the temp directory or `<case>`
exits 4, the code that already means the demo could not do its bookkeeping.

**`Cli.Invocation` gains `record Demo()`**, `demo` with any further token an
`InputError`. `USAGE` gains the line.

**The eleven scenarios**, in this order, each on the fixtures below:

1. A clean lead converts — `1020304050`, high seed.
2. The lead is not in the local database — `9999999999`.
3. The lead is not in the national registry — `1050607080`.
4. The registry data does not match — `1060708090`.
5. Judicial records are found — `1070809000`.
6. The lead is on a sanctions list — `1080900010`.
7. The qualification score is too low — `1030405060`, low seed.
8. The bureau is down, an analyst approves, the lead converts — `1090001020`.
9. The bureau is down, an analyst rejects — `1090001020`.
10. The registry does not answer in time — `1100102030`.
11. The bureau cache serves the second run — `1020304050` twice, one directory.

**Six leads are added to `fixtures/`**, each in `leads.csv`, and a row elsewhere
only where the default of assumption `[03]` — no row means nothing on that
person — is not the wanted outcome:

| Lead | Rows added |
|---|---|
| `1050607080` | none; absent from `registry.csv` is `NotFound` |
| `1060708090` | `registry.csv` with a different birth date |
| `1070809000` | `registry.csv` matching, `judicial.csv` count 2 |
| `1080900010` | `registry.csv` matching, `bureau.csv` on the OFAC list |
| `1090001020` | `registry.csv` matching, `bureau.csv` status `DOWN` |
| `1100102030` | `registry.csv` matching, latency 2500ms, above the 2s timeout |

`9999999999` gets no row anywhere, including `leads.csv`. Scenario 10 costs the
demo two seconds of wall clock, which is the point of it.

## Acceptance criteria

`CliTest`:

1. `demo` parses to `Invocation.Demo`; `demo --all` and `demo x` are each an
   `InputError`. `USAGE` names the command.

`DemoTest`, running `Main.run(new String[] {"demo"}, ...)` in process against the
shipped `fixtures/`, with `in \d+ms` blanked out of the captured stdout:

2. It exits 0, and the transcript holds eleven `== N. ` headers in the order
   above, each followed by its commands' output.
3. Every rejection cause appears: the transcript contains the exact rendered line
   for scenarios 2 to 7 and for the analyst rejection in 9, seven lines, one per
   case of `RejectionCause`.
4. Scenario 1 converts and scenario 7 rejects on the score, with the two seeds'
   scores named in the assertion. Scenario 8's `$ review resolve` line carries a
   case ID beginning `1090001020-`, and the line after it converts.
5. Scenario 10 is `pending manual review at step REGISTRY`, exit 2, and opens a
   case; scenario 9 exits 1 and scenario 11 prints the same decision line twice.
6. Nothing is written under `./data`: the demo's `Config` is `defaults()` and the
   directory does not exist after the run.

`FixtureAdaptersTest` or the existing fixture tests:

7. Each of the six new leads reaches `validate-lead --id` on the shipped fixtures
   with the outcome the table above intends, seeded where the score decides it.

## Effort

medium. Every shape is fixed here — the scenario record, the transcript, the exit
code, the eleven scenarios and the six fixture rows. The judgement left is
choosing two seeds and keeping the transcript readable.

## Docs

Two lines in `docs/assumptions.md` tagged `[07]`: the demo runs in a temp
directory rather than `./data`, so it neither depends on nor overwrites a
reviewer's cache and queue; each scenario carries its own seed, so no outcome
depends on the order the scenarios run in. No ADR — nothing here decides
anything the pipeline can see. The README is spec 08.
