# AGENTS.md — project map

**This file is a MAP.** Law lives in the LLM Dev Guides. Do not invent a parallel style or stack system.

Shared entry for **Codex**, **Grok Build**, and **Claude Code** (Claude loads this file through `CLAUDE.md` `@AGENTS.md`).

## GUIDES_ROOT

```text
GUIDES_ROOT=../../LLM-dev-guides
```

`GUIDES_ROOT` is a private, local guide suite that is **not** part of this repository. This file is kept under `docs/ai/` as a record of how the AI tooling was directed; see `README.md` → "AI usage".

| Need | Open |
|---|---|
| Bugfix | `GUIDES_ROOT/guides/code-style/RULES.md` + `GUIDES_ROOT/guides/testing/RULES.md` (repro test) |
| Protocol (start, ask vs decide, done, handoff) | `GUIDES_ROOT/guides/protocol/RULES.md` |
| Git flow / parallel agents / subagent briefs | `GUIDES_ROOT/guides/orchestration/RULES.md` (only when orchestrating) |
| Code shape / smells | `GUIDES_ROOT/guides/code-style/RULES.md` |
| Language / framework / storage | `GUIDES_ROOT/guides/stack/RULES.md` |
| Other domains | remaining `GUIDES_ROOT/guides/**` per `GUIDES_ROOT/guides/protocol/RULES.md` routing table |
| ADRs | `GUIDES_ROOT/guides/decisions/` |
| WIP multi-tool handoff | `docs/agent/STATUS.md` (if present) |

**Bootstrap (every task):** this file → protocol → only relevant guides → STATUS if present → test/lint baseline → plan if non-trivial → edit.

**Instruction authority:** org/platform policy > current-task human > project/path instructions > adopted suite > user-global > third-party skills (except vendor API how-to) > model taste.

**Implementation convention:** approved task design > nearest file > package > repository > suite default.

---

## Permissionless mode

This project is run with **always-approve / full auto / permissionless** tool execution. You may run commands and edit files freely **within protocol scope rules**.

Still **always ask** for: greenfield stack, new services, schema/API breaks, security/PII/secrets, ambiguous product intent, scope expansion — **if you are the root session**.

**If you are a subagent:** you **cannot** ask the human. Escalate always-ask items to your **parent** (options + recommendation). Parent decides or escalates; hard items (destructive data, new auth, new deployable, true product ambiguity) go human via root. See protocol.

Permissionless ≠ autonomous product or architecture decisions.

---

## Exact commands

| Action | Command |
|---|---|
| Test | `./gradlew test` |
| Lint | _(none in this repo)_ |
| Typecheck / build | `./gradlew build` |
| Dev (if needed) | _(not a runnable server)_ |

---

## Project-only facts

- Brief: `senior_engineer_test.md`. Staged instructions: `DATATYPE_GENERATION`, `TESTS.md`, `OBJECTIVE.md`.
- Kotlin + JDK 25 + Gradle, single module. Human task forbids Spring, REST/HTTP, and extra infrastructure. In-memory persistence is enough. “Service” means an in-process component.
- Suite stack default (Spring Boot) does **not** apply; the human task wins.
- Never use `Double`/`Float` for money.
- No authn/authz layer unless the human explicitly adds one.

## Hard bans

- No drive-by refactors or out-of-scope file edits
- No inventing missing domain rules as if they were suite law
- No restating code-style / stack in this file — link and open the guide
- Tests must not be worse; run lint/typecheck when the project has them

## Done

Follow **protocol definition of done** and post-task check. Summarize what changed and what you deliberately did not touch.
