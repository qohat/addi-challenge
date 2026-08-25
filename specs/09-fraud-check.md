# 09. Fraud check

## Goal

A fifth validation runs between the compliance bureau and the qualification
score. `Step` gains `FRAUD`, a `FraudCheck` port answers `Assessed(boolean)` or
`Unavailable`, and a flagged lead is rejected before it is ever scored. The
answer is fixture-driven like every other simulated system: it is modified by
editing a row of `fixtures/fraud.csv`, and the default is false.

## Scope

- The `FRAUD` step, its outcome type, its port, its adapter and its rejection
  cause.
- `fraud.csv`, with per-row latency and a `DOWN` status like the other three
  external systems.
- The fallout: every exhaustive switch over `StepOutcome`, `RejectionCause` and
  `Step` stops compiling until its new arm lands, and every fixture directory a
  test writes gains a file.
- One demo scenario, and the renumbering it forces on the five after it.

## Not in scope

- A CLI flag forcing the answer. Configurable means injectable (spec 03); the
  fixture row is how the response is modified, and a flag for what a reviewer
  changes with one line of CSV is a flag to maintain for nobody.
- Whatever the verdict is derived from — a rule, a signal, a model. The service
  returns a boolean, and why it returned it lives outside this system, exactly
  as the sanctions list does.
- A second demo scenario for a fraud service that is down; pending at `FRAUD` is
  the shape of pending at `BUREAU`, which criterion 9 covers. Nor is the answer
  cached — the bureau cache exists because the brief asks for it.

## Design decisions

**`Step` gains `FRAUD` between `BUREAU` and `SCORE`.** Declaration order is
canonical order (ADR 0008), so the sequencing loop, the ordinal comparison in
`Pipeline.run` and "the first unresolved step" follow with no other change.

**The outcome carries the boolean, not the verdict**, the way `ScoreOutcome`
carries the number:

```java
sealed interface FraudOutcome extends StepOutcome {
    record Assessed(boolean fraudulent) implements FraudOutcome {}
    record Unavailable(String reason)   implements FraudOutcome {}
}
```

`StepOutcome` permits it, and the port matches the other four:
`interface FraudCheck { FraudOutcome check(Lead lead); }` in
`com.addi.lead.port`. The rule — flagged rejects — lives at the single collapse
point where a reviewer looks for it, never in the adapter, as two new arms of
`Decisions.terminal` between the bureau block and the score block:

```java
case FraudOutcome.Assessed(var fraudulent) ->
        fraudulent ? rejected(new RejectionCause.FraudDetected()) : Optional.<Decision>empty();
case FraudOutcome.Unavailable(var why) -> review(lead, Step.FRAUD, why);
```

**`RejectionCause.FraudDetected()` has no component**: the response is a boolean,
so nothing could differ between two instances. `Cli.reason` gains
`case RejectionCause.FraudDetected ignored -> "flagged by the fraud check.";` and
the rejection line names it, at exit 1. Neither switch gains a `default`.

**`Checkpoint.resumeFrom` follows the bureau rule, not the score exception**:
`case BUREAU -> Step.FRAUD; case FRAUD -> Step.SCORE;`. An analyst can attest
that a fraud check came back clean out of band, the way they attest a bureau
screening; what they cannot attest is a number (ADR 0004).

**`fraud.csv` is `id,fraudulent,latencyMs,status`**, the column order of
`judicial.csv`. `FixtureFraudCheck(Path fixtures, Latency latency)` pauses the
row's latency, gives `Unavailable("the fraud service is down")` on `DOWN`, and
`Assessed(false)` with no row — the default for a person it has nothing on.

**The flag is parsed, not coerced.** `Fixtures` gains `static boolean flag(String
cell)`, switching on `true` and `false` and throwing on anything else, mirroring
`isDown`; `Boolean.parseBoolean` would read a malformed cell as `false` and hide
it. Here it becomes `Unavailable` like every other bad row (ADR 0005), and the
class javadoc says six files.

**The shipped fixture gets four rows** — `1020304050,false,70,UP`,
`1030405060,false,70,UP`, `1040506070,true,70,UP`, `1090001020,false,70,UP` — so
Lucia Gomez, the one seeded lead with no demo scenario, is the flagged one, and
the lead resuming from an approved bureau case passes the new step. `FixtureDir`
gains the file too: `HEADERS` takes `fraud.csv`, `clean(dir)` writes
`ID + ",false,0,UP"`, and every test writing its own directory needs it — a
missing file is that step's `Unavailable` (`[03]`) and the run goes pending.

**`Pipeline` takes a sixth port** and gains `case FRAUD -> fraud.check(lead);` in
`outcomeOf`. `Main` wires `new FixtureFraudCheck(fixtures, latency)` beside the
other adapters, uncached.

**The demo gains scenario 7**, `The lead is flagged by the fraud check`, running
`validate-lead --id 1040506070` at the `CONVERTS` seed, in pipeline order after
the sanctions scenario. The five after it shift by one, so `DemoTest.TITLES`
gains the row, every `assertLine` and `block` index from 7 upward moves, and
`runsElevenScenariosInOrderAndExitsZero` is renamed for twelve.

## Acceptance criteria

1. `DecisionsTest.aFraudFlagRejects` — `Assessed(true)` gives
   `Rejected(new FraudDetected())`.
2. `DecisionsTest.aCleanFraudCheckContinues` — `Assessed(false)` gives
   `Optional.empty()`, the only "continue" signal there is.
3. `DecisionsTest.anUnavailableFraudServiceIsAReview` — `Unavailable("down")`
   gives `PendingManualReview` at `FRAUD`, reason unchanged.
4. `CliTest.namesTheFraudCheckOnItsRejectionLine` — rendering
   `Rejected(new FraudDetected())` for `1040506070` gives exactly
   `Lead 1040506070 rejected: flagged by the fraud check.`, and
   `rendersALineForEveryDecisionAndEveryRejectionCause` gains the cause.
5. `CheckpointTest` — `resumeFrom` sends `BUREAU` to `FRAUD`, `FRAUD` to `SCORE`.
6. `PipelineTest.theFraudCheckRunsAfterTheBureauAndBeforeTheScore` — a lead clean
   through the bureau and flagged by fraud returns `Rejected(FraudDetected)` and
   `calls` is `[REGISTRY, JUDICIAL, BUREAU, FRAUD]`: the score is never asked.
7. `PipelineTest.aResumeFromTheFraudStepRunsFraudAndScore` — `run(lead,
   Step.FRAUD)` calls those two ports and no others.
8. `FixtureAdaptersTest.theFraudCheckAnswersItsRowAndDefaultsToFalse` — `true`
   gives `Assessed(true)`, `false` gives `Assessed(false)`, no row gives
   `Assessed(false)` with no latency, and a row's latency is requested.
9. `FixtureAdaptersTest` — `status=DOWN`, a missing `fraud.csv` and
   `fraudulent=maybe` each give `Unavailable`, and nothing escapes the adapter.
10. `MainTest.aFlaggedLeadIsRejectedAndExitsOne` — the shipped fixtures print
    `Lead 1040506070 rejected: flagged by the fraud check.` and exit 1.
11. `MainTest.anApprovedFraudCaseResumesIntoTheScore` — a lead whose fraud row is
    `DOWN` exits 2 with a case pending at `FRAUD`, and `review resolve --approve`
    converts it at seed 0 with score 67.
12. `DemoTest` — twelve scenarios in order, scenario 7 titled `The lead is
    flagged by the fraud check` and holding the line from criterion 10.

## Effort

medium. Every shape is fixed — the enum position, the outcome type, the port, the
cause, both switch arms, the CSV columns, the fixture rows, the twelve tests. The
judgement is in the fallout: every `Pipeline` construction site, every fixture
directory a test writes, every demo index that moves.

## Docs

Two lines in `docs/assumptions.md` tagged `[09]`: `fraudulent=true` means fraud
was detected and rejects, so the default `false` is the answer for a lead the
service has nothing on; and the response is modified through the fixture row
rather than a CLI flag. A two-line bullet in `docs/PROJECT_CONTEXT.md` under
*What is mine, not the brief's* — that file has three left of its 120. In
`README.md`, the flow diagram, the four-validations sentence and the fixture
list; in `docs/COMPLIANCE.md`, the row claiming `Step` has four constants. No
ADR: this is ADR 0008 again — a boolean the domain reads, not a verdict the
adapter decided, is what it already records for the score.
