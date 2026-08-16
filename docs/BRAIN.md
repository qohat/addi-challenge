# How I think and how I work

This is me, not this project. It carries over to whatever I'm building next, so
nothing here should mention a specific domain. Project-specific decisions live
in `RULES.md`.

## How I think about code

I lean heavily functional. Most of the languages I work in aren't functional,
but the discipline carries over and it's the main thing keeping risk out of my
code. It isn't a style preference and I don't want it traded away for
convenience — when someone tells me a rule below is inconvenient, that's usually
the moment it's doing its job.

Effects are explicit and pushed to the edges. The domain is pure: no I/O, no
clock, no randomness, no logging. Every effect enters through a port and gets
executed at the boundary. The layer that orchestrates effects doesn't hide them.

Errors are values. Every expected outcome is a named case in a sealed hierarchy,
returned. Nothing expected is ever signalled by throwing. Modelling the failures
properly is not an afterthought bolted on at the end — it's most of the design,
because the interesting part of any system is what happens when something
doesn't work.

Exceptions are for what's genuinely unrecoverable, and they never cross into the
domain. Caught at the boundary, translated into domain values immediately. Where
the language lets me, custom exceptions carry no stacktrace: filling one in is
expensive and I'm never going to read it.

I don't use a generic `Result<T,E>`. Each step returns its own type named after
its domain, with cases named after what actually happened. It's more expressive
and pattern matching over it has something real to say.

Failures of the domain and failures of the outside world are different things
and they get different branches. A dependency being down is not a business
rejection. Wherever those two collapse into one decision, that collapse happens
in exactly one place, exhaustively matched, so a new failure case can't quietly
land in the wrong bucket. That single point is usually the most important
function in the system and I want it obvious.

Sealed types plus records wherever something has cases. Exhaustive matching,
never a catch-all branch on a closed type — a catch-all defeats the only
compiler check the design leans on. When I add a case later and twenty call
sites break, that's the design working. Each one gets fixed deliberately.

Illegal states should be unrepresentable. A validated thing and an unvalidated
thing are different types. If the type system can prevent it, it prevents it,
and that's cheaper than any test.

Immutable by default. No setters, no mutable collections escaping their
boundary, no shared mutable state across parallel branches. Where mutation is
genuinely needed it stays behind a port.

Total functions. No nulls in the domain. Parse, don't validate: input crosses
the boundary once, becomes a valid type, and everything downstream assumes it's
valid.

Plain language features over libraries. If the language has what I need, using
it directly says more than importing someone else's abstraction.

Concurrency stays in the orchestration layer. Scopes and futures are effectful
and don't compose as pure values, so the domain never sees a task, a future or
a thread. Concurrency exceptions get caught and converted before anything
escapes.

Tests come out deterministic by construction, because the domain is pure and
every effect is an injected port. Clock, latency and randomness are ports with
fixed seeds in tests. If something is hard to test, an effect leaked where it
shouldn't have.

If I catch myself writing a paragraph of comment defending an awkward construct
— a mutable local, a broad catch — the design is fighting me and I fix the
design. On my last project there was a mutable `String flag` in an argument
parser with five lines of comment justifying it. The fix was to carry the field
name on the error value.

## How I work

I work in specs. Each one is a version of the code I could deploy the day it
merges — not a milestone, not a checkpoint, something that runs.

The spec commit lands on the main branch before I create the worktree for its
implementation. Never both in one PR.

Tests first, always. Written from the spec, watched to fail, then the code that
makes them pass.

I write the first three or four specs up front and then write each one at the
end of the session that implemented the previous one, when I know what the code
actually looks like. Last time I wrote thirteen specs before any code and ended
up with a sixty-line section in the queue README explaining why four of them ran
out of numeric order. That section was the evidence the plan hadn't survived
contact.

Specs stay under 150 lines. Over that, either it's doing the implementation's
job or it should be two specs. I once wrote a 356-line spec to move three
version strings into a config file, and labelled it low effort, which tells you
how fast this gets away from you.

Every spec carries an effort level, assigned by one question: does it leave any
shape undecided — a type, an interface, a data format, a control flow? If yes
it's high, because whoever executes it is designing and later specs will sit on
whatever they pick. If every shape is fixed but the work needs judgement, it's
medium. If executing it is typing, it's low. That field also decides which model
runs it. If most specs come out high, they're underspecified and I fix the spec,
not the label.

Whatever the project is actually about has to run end to end as early as
possible, with stubs if that's what it takes. Everything after that deepens
something a reviewer can already see running. Last time I merged six specs and
thousands of lines and the headline mechanism still didn't exist, because I'd
ordered the queue by architectural layer instead of by what you can demonstrate.

Stub the ports early. A pipeline returning a hardcoded outcome through the real
types is a real increment, and it pins the interfaces down before four adapters
depend on them.

Build the user-facing surface for one command at a time. Parsing flags for
commands whose behaviour lands four specs later means dead constants and a
not-implemented branch carried around for weeks. Each command gets its parsing
when it gets its behaviour.

Small infrastructure work goes inside the setup spec. Fifteen lines of CI YAML
doesn't need its own spec, session, worktree, PR and cost entry.

## Context

This is the part I care most about and the part I got most wrong before.

Almost all my token spend is re-reading the same context, not generating code.
Measured on my last run: 96.5% of tokens were cache reads and 0.5% were output.
So the size of whatever is permanently loaded determines the bill, and the rest
of this follows from that.

There's exactly one always-resident file. It's a router: what the project is,
pointers to everything else, the hard constraints. Under 40 lines. Anything I
add there gets re-read on every request for the life of the project.

Operating rules and code style load when they're relevant, not up front. The
skill says when — read the style rules when you're about to write code, not
while planning or running tests.

Specs load one at a time, one per session. That's where volume belongs, since
it's the only layer that's actually about the task in hand.

The codebase gets queried, not read. Grep returns matching lines. Glob returns
paths. Reading with an offset returns a slice. Nobody needs to read a directory
tree to find a caller.

Subagents are what I underused. A subagent's searching, reading and dead ends
all happen in its own context and only the answer comes back, so a twelve-file
sweep costs me a paragraph. Use one whenever the search is wide and the answer
is small. Don't use one when I need the raw material itself — I'd just be paying
to have it summarised.

ADRs, the assumptions log, session exports and the README get written and never
read back by an agent. That's free.

Budgets are numbers and they're checked in CI. Forty lines for the router, 120
for the files that load once per session, 150 for a spec. Over budget means cut
the file, not raise the limit. I've caught myself doing the second one.

## No MCP servers

Tool definitions sit at position zero of the cache prefix, ahead of the system
prompt and the conversation, so they get re-read on every single request whether
or not I call them. And any change to the tool set invalidates the whole cache
rather than just the tail, which means a server that reconnects or updates
between runs costs me a full cold rebuild I can't even see in the transcript.

Last time I ran a code-graph MCP server and wrapped it in my own script, so I
had two paths to the same capability and was paying rent on both. Grep, Glob and
subagents are already in that prefix at zero marginal cost, don't change between
sessions, and already return matches and reports rather than files.

So: the coding agent and nothing else. If I need a repo-specific query later
it's a plain shell script called through the shell tool, which is already in the
prefix and costs nothing to add.

## Sessions and models

One spec per session, opened clean, exported before I close it. Where the
process is part of what's being submitted, that export directory is also the
deliverable, so the way I work and the way I submit are the same thing.

I read and approve every PR myself before the next spec starts. Nothing advances
without that. It's the only manual step in the process — the system itself is
verified by its tests, never by me clicking through it.

The model gets chosen when the session opens, never halfway through. Caches are
per-model, so switching at turn 30 throws the entire prefix away. The strongest
model for planning, for writing specs, and for anything that leaves a shape
undecided. A cheaper one for specs that are pure transcription. The cheapest for
exploration subagents, which run in their own context anyway and don't touch my
prefix.

At the end I re-run one already-merged spec on a cheaper model from a clean
session. If the spec was good the result should be equivalent. That measures the
specification rather than the model, and it's the sharpest evidence I have that
the context engineering is real.

## Memory between sessions

Every session starts clean, so the repo is the memory. Nothing important lives
in an agent's memory store.

A new session orients itself in four cheap steps: the always-resident router
loads by itself, the spec queue says what's merged and what's next, the assigned
spec says what to build, and `git log --oneline -15` says what happened. Fifteen
lines of commit subjects beat any narrative summary and cost almost nothing.

Everything worth remembering already has a canonical home. Merged specs say what
was built, ADRs say why, the assumptions log says which ambiguities got closed,
and the code is the truth. Duplicating any of that into a memory file means two
copies that drift, and the copy nobody reviews is the one that goes stale.

I don't use a persistent memory file for project state, for three reasons. It's
invisible to whoever reviews the repo, since it lives outside it. It degrades
silently — I had a memory telling an agent to answer me in English long after
I'd switched languages, and unlike a wrong ADR it never shows up in a diff. And
it's permanent context: a memory file that grows is the same problem as a router
file that grows, without a budget watching it.

What the memory store is good for is the residue that has no home in the repo —
a correction about how the agent works, something I noticed about the process,
a preference I discovered halfway through. So I treat it as an inbox rather than
a store: if a correction survives two sessions, it gets promoted to wherever it
canonically belongs — the operating rules, the style guide, or an ADR — and
deleted from memory. That way memory never accumulates and anything that matters
ends up visible in the repo.

## Cost

Computed by a checked-in script, never by asking a model to read its own logs.
The logs write one record per content block of the same API response, so summing
them naively roughly doubles the number, and cache writes have to be priced by
TTL — five-minute and one-hour writes are 1.25x and 2x input, and mixing them up
is a 60% error on that line.

One session per spec means one log file per spec, so the cost of a spec is just
the cost of its session and there are no windows to overlap. That's the real fix
— last time my per-spec figures summed to nearly twice the session that
contained them, because I'd run six specs in one eighteen-hour session and had
to carve it up by timestamp afterwards. Windows are the emergency mechanism now,
not the normal one, and when I do use one the session total has to be at least
the sum of what I carve out of it, checked rather than eyeballed.

The closeout happens at the *start* of the next session, not the end of the
current one, because a session's log isn't complete until it's closed. So each
session opens by pricing and marking the previous spec, then does its own work.

A session that passes roughly forty requests, or touches two specs, has gone
wrong. Stop it and open a new one rather than letting it sprawl.

When I report cost I say what the number includes. With one session per spec,
it's measuring session overhead as much as implementation, and pretending
otherwise is worse than the overhead.

## Documentation

Only what matters: architecture, stack, the public interface, how to run it, how
the process works. The README stays lean and depth goes in a docs directory.

Decisions with reasoning are ADRs. I like ADRs and I want them used properly —
context, decision, consequences, and the alternatives I rejected with one line
each on why.

One-line calls where the brief or the requirements were silent go in an
assumptions log, tagged with the spec that hit them. Last time that file grew to
342 lines because I dumped both kinds into it, and most of it was written before
any code existed, which means it wasn't recording assumptions at all — it was
recording a plan.

Every spec states whether it needs docs and why. If it says no, nothing gets
written.

## Permissions

I run with permissions skipped, because approving every file write kills the
speed this whole approach is about. The limits live in the agent's operating
rules instead, so they're rules and not just my intention: nothing outside the
project root except the session logs the cost script reads, no recursive deletes,
no history rewriting, no force pushes, no new dependency without asking me first
since that's an architecture decision.

Everything happens inside git. Worktrees per spec, one PR each split into
reviewable commits, nothing merged without me reading it. Anything wrong is one
git command away from being undone.

## How I want you to talk to me

Short and concise. No emojis anywhere — commits, PRs, code, chat. No filler, no
cheerful preamble. Technical prose, direct.

When I ask a question, answer it before making edits or running anything.

When I give you feedback or an analysis, say explicitly whether you agree or
disagree before telling me what you changed. If you think I'm wrong, argue with
me. I'd rather have the argument now than find out in spec 04.

Don't over-correct. If you got something wrong, fix it in a sentence and move
on — I don't need the post-mortem.
