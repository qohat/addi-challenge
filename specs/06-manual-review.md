# 06. Manual review: checkpoint, queue, resume

## Goal

A run that stops on an unavailable dependency leaves a case on disk, and an
analyst resolves it with `review resolve <case> --approve|--reject`. Approving
resumes the pipeline from the checkpoint with the failed step taken as clean and
can still end rejected; rejecting closes the case with a cause of its own. The
brief's manual review flow then exists end to end.

## Scope

- The review queue on disk: one file per case, its format, its atomic write.
- The resume rule: which step an approved case restarts at.
- The `review resolve` command, its two flags and its output.
- The rejection cause an analyst produces, and the case a resumed run opens.

## Not in scope

- `review list` or `review show` — `ls` and `cat`, assumption `[bootstrap]`.
- An analyst forcing a conversion, or re-opening a resolved case. ADR 0004
  rejected both: only the score converts, and a failed resume opens a new case.
- The demo path through this flow and its fixtures — spec 07.
- Any change to `Decisions.terminal`, `Pipeline` or the ports: the pipeline
  already returns `PendingManualReview` and resumes through `run(lead, from)`.

## Design decisions

**The queue is a concrete adapter, not a port.** `FileReviewQueue(Path
directory)` in `.adapter`, wired in `Main` over `config.data().resolve("review")`
— the shape of `CachingComplianceBureau`, also a file with no port of its own.

```java
CaseId open(Checkpoint checkpoint, Instant now) throws IOException
Optional<ReviewCase> find(CaseId id)
void resolve(ReviewCase reviewCase, CaseStatus status) throws IOException
```

Tests point it at a temp directory and read the files back. The instant is a
parameter, not a `Clock` field: the queue stamps what it is given, and `Main`
passes `Clock.systemUTC().instant()` as it does for the cache.

**A case ID is `<nationalId>-<epochMillis>` and is also the filename**, no
extension (ADR 0003). Epoch millis rather than the ISO instant, which carries
colons a Windows filename cannot hold; the ISO form is inside as `openedAt`.

**The file is `key=value` lines, not CSV**, one per line, in this order, parsed
by splitting on the first `=`: `id`, `openedAt`, `status`, `pending`, `reason`,
`leadId`, `firstName`, `lastName`, `birthDate`, `email`. A checkpoint reason is
adapter prose and will contain commas, so the fixtures' convention cannot hold
it. The lead is stored in full rather than read from the CRM on resume: ADR 0004
says the case persists the lead, and the CRM record may since have changed. A
missing file, a missing key, an unparseable instant or date and an unknown status
word all read as no case: `find` returns empty and throws nothing, as the cache
treats an unreadable row.

**A write is temp file then `ATOMIC_MOVE`, and its failure is never swallowed.**
`CachingComplianceBureau.write` extracted to a package-private `Atomic.write(Path
file, List<String> lines) throws IOException` both callers use; the cache keeps
catching, the queue propagates. Losing a cache entry costs a screening, losing a
case loses the analyst's only record of it (ADR 0003).

**Approving resumes at the step after the pending one**, as a `resumeFrom()`
method on `Checkpoint` returning the next `Step` in declaration order, or `SCORE`
itself when `SCORE` is pending. A clean outcome for the pending step means the
run continues past it, and the prefix is implied by the step (ADR 0008). The
score is the exception because an analyst cannot attest a number, so approving it
re-runs the port (ADR 0004). `pending = REGISTRY` resumes at `JUDICIAL`, which
runs for real, so a timeout that killed both branches needs no special case.

**One new cause, `RejectionCause.ReviewRejected(CaseId caseId)`**, built in
`Main` and never by the switch, so `Decisions` does not change. `Cli.reason`
gains its last arm, `"manual review case " + caseId.value() + " was rejected by
an analyst."`.

**`Cli.Invocation` gains `ResolveReview(CaseId caseId, CaseStatus resolution)`**,
`--approve` parsing to `APPROVED` and `--reject` to `REJECTED` — the parsed value
is the status the file gets, which a boolean would not be. Four tokens exactly,
and `USAGE` gains the line and the new exit code.

**A case that cannot be resolved is an input error, not a decision**: unknown,
unreadable, or already `APPROVED` or `REJECTED` all print to stderr and exit 3,
because `Decision` describes a lead's fate and none of these is one. **An
`IOException` out of `open` or `resolve` exits 4**, because exiting 2 would claim
a case a reviewer will not find.

**`Main` prints the case ID on a second line** — `Cli.opened(CaseId)`, rendering
`Case <id> opened.` — whenever a run opens a case, so `render` stays pure and
keeps its signature. `--approve` prints the resumed decision line and that line
if the resume opened a case; `--reject` prints the decision line alone, which
already names the case. The status is written before an approved resume runs: a
crash mid-resume must not leave a case approvable twice.

## Acceptance criteria

`ReviewQueueTest`, on a temp directory:

1. `open` on an absent directory creates it and writes exactly one file, named by
   the returned case ID, which is the national ID and the instant's epoch millis.
2. A case survives the round trip: every lead field, `openedAt`, `pending`,
   `reason` and status `OPEN`, unchanged. A reason containing commas survives it.
3. Two `open` calls for the same lead at different instants are two files, both
   findable; `resolve` leaves one file, `APPROVED`, with its case ID, `openedAt`
   and checkpoint unchanged.
4. An unknown ID, a file missing a key, an unparseable `openedAt` and an unknown
   status word each give an empty `find` and throw nothing.

`CheckpointTest`:

5. `resumeFrom` is `JUDICIAL` for `REGISTRY`, `BUREAU` for `JUDICIAL`, `SCORE`
   for `BUREAU`, and `SCORE` for `SCORE` — the last because approval re-runs it.

`CliTest`:

6. `review resolve X --approve` parses to `ResolveReview(new CaseId("X"),
   APPROVED)` and `--reject` to `REJECTED`; `review`, `review resolve X`,
   `review resolve X --maybe` and `review list` are each an `InputError`.
7. Rendering `Rejected(new ReviewRejected(new CaseId("1020304050-17553")))` for
   `1020304050` gives exactly `Lead 1020304050 rejected: manual review case
   1020304050-17553 was rejected by an analyst.`, and the completeness test over
   every cause gains it.

`MainTest`, end to end on fixtures it writes:

8. A down bureau exits 2, prints `Case <id> opened.` below the decision line, and
   leaves that file holding `status=OPEN` and `pending=BUREAU`.
9. Approving it with the bureau still down exits 0, prints `Lead 1020304050
   converted to prospect. Score 67.`, leaves the file `APPROVED`, and writes no
   bureau cache row for that lead — an approval is not a bureau answer.
10. Rejecting a case exits 1, prints the analyst rejection line and leaves the
    file `REJECTED`; resolving an already-resolved case exits 3 and leaves its
    file byte for byte as it was; an unknown case exits 3 and writes nothing.
11. With bureau and score both down, approving the bureau case exits 2 and leaves
    two files: the first `APPROVED`, a second `OPEN` at `pending=SCORE`. With
    `data/review` occupied by a regular file, a down bureau exits 4 on stderr.

## Effort

medium. The queue row said high because it was written before any of this
existed; every shape is fixed above — the file format, the three queue methods,
the resume rule, the cause, the parse and the new exit paths.

## Docs

Three lines in `docs/assumptions.md` tagged `[06]`: the case timestamp is epoch
millis because ISO instants carry colons a Windows filename cannot hold; a case
file is `key=value` because a checkpoint reason contains commas; a case that
cannot be resolved is an input error, and exit 4 is a case that could not be
written. No ADR — ADR 0004 decided the semantics and ADR 0003 the storage, and
this spec only fixes their columns. The README is spec 08.
