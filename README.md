# Addi lead validation challenge

A CLI that qualifies a sales lead into a prospect. Four validations run in the
order the brief fixes: national registry and judicial records in parallel, then
the compliance bureau with a durable cache, then a qualification score.

Java 25, Gradle, single module, no framework, no external infrastructure.

**This README is a placeholder.** It is written in full at spec 08, once there
is a process to describe. The brief asks it to cover the decisions, the
assumptions, how the output was refined, the full prompt and conversation
history, what worked and what did not with AI, and the pending improvements.

## Where things are, meanwhile

| | |
|---|---|
| The requirements | `docs/brief.md`, transcribed from `docs/brief.pdf` |
| The problem and what is decided about it | `docs/PROJECT_CONTEXT.md` |
| Why each decision was made | `docs/adr/` |
| Ambiguities closed, and by whom | `docs/assumptions.md` |
| What is built and what is next | `specs/README.md` |
| Prompts, session by session | `prompts/` |
| Session transcripts | `docs/ai/sessions/` |
| Cost per spec | `docs/ai/cost.md` |
| How the work is run | `AGENTS.md`, `CONTRIBUTING.md`, `.claude/skills/` |

## Running it

Nothing runs yet. Spec 00 builds the Gradle project, the checks and the
container; spec 02 is the first spec that produces a command a reviewer can
invoke. Instructions land with the code that needs them.
