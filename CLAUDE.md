# Addi lead validation challenge

A CLI that qualifies a sales lead into a prospect by running four validations:
national registry and judicial records in parallel, then the compliance bureau,
then a qualification score. Java 25, Gradle, single module, no framework.

## Hard constraints

- Java 25 with `--enable-preview`. `StructuredTaskScope.open()`, never the Java 21 API.
- No framework, no annotations, no reflection. Wiring is by hand in a composition root.
- No external database, no message queue, no MCP servers, no `.mcp.json`.
- No new dependency without asking — that is an architecture decision.
- Everything in English. No emojis in code, output, commits or PRs.

## Where things live

| Need | File | When |
|---|---|---|
| Operating rules, commands, search discipline | `AGENTS.md` | Every session, first |
| Java style and its reasoning | `CONTRIBUTING.md` | About to write Java, not before |
| The problem and what was decided about it | `docs/PROJECT_CONTEXT.md` | Every session |
| What is merged and what is next | `specs/README.md` | Every session |
| Why a decision was made | `docs/adr/` | Questioning a decision |
| Ambiguities already closed | `docs/assumptions.md` | Before assuming anything |
| The requirements as given | `docs/brief.md` | A requirement is in doubt |

## Orienting a new session

This file loads itself. Then `AGENTS.md` for how to work, `specs/README.md` for
state, the assigned spec for the task, and `git log --oneline -15` for what just
happened. Nothing else.

## Budgets

`python3 scripts/context-budget.py` — 40 lines here, 120 for the files read once
per session, 150 for a spec. Over budget means cut the file, never raise the
limit.
