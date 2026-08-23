Use the repository's adopted LLM-dev-guides governance system.

Follow the protocol and load only the guides relevant to this task.

The human task is authoritative over suite defaults.

Before implementation:
1. Read the project instructions / AGENTS.md.
2. Follow the protocol.
3. Determine which guides are relevant.
4. Inspect the existing project state.
5. Form an implementation plan.
6. Ask rather than invent requirements when a decision materially affects the task.

Implement the supplied banking-service coding exercise.

Important task constraints from the human:
- Kotlin
- JDK 25
- Gradle
- single module
- no Spring
- no REST/API
- in-memory persistence is sufficient
- service means software component, not deployable service

Focus engineering attention on:
- monetary correctness
- domain invariants
- atomic transfers
- concurrency correctness
- testability
- appropriate separation of persistence from domain logic

Do not introduce infrastructure merely because this is a banking domain.

After implementation:
- run the tests
- review the implementation against the original task
- explain significant design decisions and tradeoffs
- identify anything that should be discussed in the follow-up pair-programming session