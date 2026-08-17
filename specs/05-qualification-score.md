# 05. Qualification score and conversion

## Goal

A score of 60 or less rejects the lead instead of converting it. The fourth
validation stops being a formality: `Decisions.terminal` splits its score arm on
the brief's threshold, a new rejection cause carries the number that failed, and
the CLI prints it. After this merges every rule the brief states is implemented
and the pipeline decides for real from end to end.

## Scope

- The threshold rule, at the one site that turns outcomes into decisions.
- A rejection cause carrying the score, and its line of prose.
- The fallout: the two exhaustive switches that stop compiling, and the seeded
  tests that assumed any score converts.

## Not in scope

- The score port, its adapter, the randomness and the seed. Spec 03 built them
  and ADR 0006 fixed them; no file under `adapter/` or `port/` changes.
- A configurable threshold or a `--min-score` flag. 60 is the brief's number and
  no caller varies it.
- Manual review of a low score. It is a terminal rejection like a sanctions hit,
  not a pending case. Spec 06 adds the reviewer's own rejection cause.
- A fixture lead that scores low. The score is never fixtured, only seeded; the
  demo's seed is spec 07.

## Design decisions

**The threshold is a constant on `Decisions`:** `public static final int
MINIMUM_SCORE = 60;`. The rule is `value > MINIMUM_SCORE`, at the site whose
javadoc already promises it, and `Cli` reads the constant rather than repeating
the number. Not a `Config` field: config is for what tests and the demo need to
vary, and this varies for nobody.

**One new cause, `RejectionCause.ScoreTooLow(int value)`**, holding the number
that failed and nothing else. The minimum is a constant, so carrying it in the
record would store a value that cannot differ between two instances.

**The score arm splits and nothing else in `Decisions` moves.** The comment
promising this change goes with it:

    case ScoreOutcome.Scored(var value) ->
            value > MINIMUM_SCORE ? converted(lead, value) : rejected(new RejectionCause.ScoreTooLow(value));

**One new line of prose**, the new arm of `Cli.reason`:

    case RejectionCause.ScoreTooLow(var value) ->
            "qualification score " + value + " is not above " + Decisions.MINIMUM_SCORE + ".";

so the whole line reads `Lead 1020304050 rejected: qualification score 57 is not
above 60.` and the exit code is 1, which already covers every rejection.

**Neither switch gains a `default`.** Both are exhaustive over sealed types, so
the missing arm is a compile error until it lands — that is the mechanism spec
06 relies on when the cause type grows again.

**`MainTest`'s seed moves from 7 to 0 in the `config(Path)` helper.** With the
threshold real, `new Random(7).nextInt(101)` is 57 and every end-to-end
conversion test in that class fails; `new Random(0).nextInt(101)` is 67 and they
pass. Seed 7 becomes the seed of the new rejection test. Both values were run
against the JDK when this spec was written, not predicted — the four tests that
depend on one are `aCleanLeadConvertsAndExitsZero`,
`theFixturesTheProjectShipsConvertEndToEnd`,
`aCachedBureauAnswerOutlivesTheBureauItself` and
`anEntryOlderThanTheTtlIsIgnoredAndTheRunDecidesFromTheFixture`.
`aRegistrySlowerThanTheTimeoutExitsTwo` builds its own `Config` with seed 7 and
never reaches the score, so it stays as it is.

**Nothing else changes.** `Pipeline`, `ScoreOutcome`, `Prospect`, `Step`,
`Config`, the exit code map and the fixtures are untouched. `Pipeline.sequential`
still cannot fall out of its loop, because the score arm decides on both sides of
the branch.

## Acceptance criteria

Each one names the test that proves it. Existing tests keep their names unless
this says otherwise.

1. `DecisionsTest.aScoreAboveTheMinimumConverts` — 61 and 75 each give
   `Converted(new Prospect(LEAD, value))`. This is the existing `aScoreConverts`
   renamed, with 61 added.
2. `DecisionsTest.exactlySixtyRejects` — `Scored(60)` gives
   `Rejected(new ScoreTooLow(60))`. The boundary, in the direction the brief
   states.
3. `DecisionsTest.aLowScoreRejectsWithItsValue` — `Scored(0)` gives
   `Rejected(new ScoreTooLow(0))`, and it is not equal to the decision for
   `Scored(60)`, so the number survives into the cause.
4. `CliTest.namesTheNumberAndTheMinimumOnTheScoreRejectionLine` — rendering
   `Rejected(new ScoreTooLow(57))` for `1020304050` gives exactly
   `Lead 1020304050 rejected: qualification score 57 is not above 60.`
5. `CliTest.rendersALineForEveryDecisionAndEveryRejectionCause` — its list gains
   `new Decision.Rejected(new RejectionCause.ScoreTooLow(12))`, so every cause is
   still covered by the completeness test.
6. `PipelineTest.aLowScoreRejectsAfterAllFourStepsRan` — a pipeline clean through
   the bureau with `Scored(60)` returns `Rejected(new ScoreTooLow(60))` and
   `calls` holds all four steps. A low score is a rejection, never a review.
7. `MainTest.aCleanLeadConvertsAndExitsZero` — with the seed now 0, the line ends
   `converted to prospect. Score 67.` exactly, and the code is 0. The assertion
   tightens from `startsWith` to the full line, because the score is now
   deterministic evidence rather than any number.
8. `MainTest.aLowScoringLeadIsRejectedAndExitsOne` — the same clean fixtures with
   a `Config` seeded 7 print
   `Lead 1020304050 rejected: qualification score 57 is not above 60.` and exit
   1. The whole rule, through the wired application, from a seed.

## Effort

low. Every shape is fixed here: the constant and its home, the new record and its
one field, both switch arms verbatim, the seed change and the eight tests. What
is left is typing and running `./gradlew check`. If the executing session finds
itself designing something, the spec is wrong and the fix is the spec.

## Docs

One line in `docs/assumptions.md` tagged `[05]`: the minimum score is a constant
at the decision site rather than a `Config` field, because the brief fixes it at
60 and nothing varies it. No ADR — the threshold is the brief's rule, not a
decision of ours, and ADR 0006 already carries the randomness reasoning. The
README is spec 08.
