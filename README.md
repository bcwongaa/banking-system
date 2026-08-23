# Banking service

In-process Kotlin component that manages accounts, deposits, withdrawals, transfers and balance queries on top of an append-only ledger. Solution to `senior_engineer_test.md`.

No framework, no HTTP, no database — the brief asks for a software component, and in-memory storage is sufficient.

## Run

```sh
./gradlew test
```

Requires a JDK on the path to bootstrap Gradle; the build itself targets JDK 25 via the Gradle toolchain (auto-provisioned by the foojay resolver if absent). Kotlin 2.4, Gradle 9.3, `kotlin.test` on JUnit 5.

## Layout

```
src/main/kotlin/banking/
  LedgerService.kt            public API + concurrency control
  domain/
    Money.kt                  BigInteger minor units + java.util.Currency, never negative
    AccountId / TransactionId / UserId.kt   value classes over kotlin.uuid.Uuid
    Account.kt                accountId, userId, currency (no balance)
    LedgerEntry.kt            transactionId, accountId, amount, type, timestamp
    TransactionType.kt        DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT
    BankingException.kt       sealed hierarchy of domain errors
  persistence/
    LedgerStore.kt            get / put(vararg) / putIfAbsent
    InMemoryLedgerStore.kt    ConcurrentHashMap<AccountId, AccountRecord>
    AccountRecord.kt          account + materialized balance + ledger entries (immutable)
src/test/kotlin/banking/
  LedgerServiceTest.kt             behaviour + ledger invariants (fixed clock)
  LedgerServiceConcurrencyTest.kt  races: overdraw, independent accounts, transfers, reads
  domain/MoneyTest.kt, AccountIdTest.kt
```

## API

```kotlin
val service = LedgerService()                       // optional: clock, store
val id = service.createAccount(userId, Money(1_000, USD))
service.deposit(id, Money(250, USD))
service.withdraw(id, Money(100, USD))               // throws InsufficientFunds on overdraft
service.transfer(id, otherId, Money(50, USD))       // atomic, shared TransactionId on both entries
service.balance(id)                                 // Money
service.history(id)                                 // List<LedgerEntry>, oldest first
```

All failures throw a `BankingException` subtype: `InvalidAmount`, `AccountNotFound`, `InsufficientFunds`, `CurrencyMismatch`, `SameAccountTransfer`. A failed operation leaves balances and history untouched.

## Design

**Money.** Integer minor units (`BigInteger`) tagged with a `Currency`. No `Double`/`Float`, no `BigDecimal` scale surprises, no overflow. `Money` is never negative; direction lives in `TransactionType`, not the sign. Arithmetic and comparison across currencies throw.

**Ledger vs balance.** Every mutation appends a `LedgerEntry` and updates a materialized balance; both live in one immutable `AccountRecord` so a read always sees a balance that agrees with its history. Tests assert `balance == fold(history)` after every scenario, including the concurrent ones.

**Concurrency.**
- One `ReentrantLock` per account, created lazily. Single-account operations hold that lock for read-validate-write.
- Transfers lock both accounts in a fixed order (by `Uuid`) to rule out deadlock, then write both updated records in a single `store.put(source, destination)` call.
- `balance()` / `history()` are lock-free: they read the latest committed immutable record.
- Consistency model: each account is linearizable; balances never go negative and writers cannot lose updates. Reads across *two* accounts are not snapshot-isolated — the in-memory store writes the two records of a transfer sequentially, so a reader summing both balances during that window can briefly see the in-flight amount on neither side. Final totals are always conserved.

**Persistence boundary.** `LedgerStore` is the only seam to storage. The service owns locking; the store is a plain record map. `put(vararg)` makes the atomicity requirement of a transfer explicit in the contract, so a database-backed implementation would wrap it in one transaction.

### Decisions not dictated by the brief

| Decision | Choice | Why |
|---|---|---|
| Initial deposit of zero | rejected | brief says "with an initial deposit"; `InvalidAmount` |
| Transfer to self | rejected | `SameAccountTransfer`; no meaningful ledger semantics |
| Cross-currency ops | rejected | `CurrencyMismatch`; FX is out of scope |
| Errors | exceptions, not `Result` | smallest idiomatic Kotlin surface; callers that want values can wrap |
| Transfer history | two entries sharing one `TransactionId` | per-account history stays self-contained, pairing is still recoverable |
| Timestamps | injected `java.time.Clock` | deterministic tests |

## Discussion points for the pairing session

- **Store atomicity vs. reads.** Writers are correct; cross-account reads are not snapshot-isolated (see above). A real database would make `put(vararg)` one transaction; an in-memory equivalent would need a global snapshot (e.g. one immutable map behind an `AtomicReference`) at the cost of copying on every write.
- **History copy cost.** `AccountRecord.entries` is rebuilt on each mutation (`entries + entry`), so an account with *n* entries pays O(n) per write. Fine for the exercise; a persistent collection or a separate append-only ledger removes the ceiling.
- **Lock map growth.** `locks` never shrinks because accounts are never closed. Account lifecycle (close/freeze) is unaddressed.
- **Idempotency.** No client-supplied idempotency key, so a retried `transfer` after a timeout would double-apply.
- **Currency scale.** `Money` does not validate `amountMinor` against `Currency.defaultFractionDigits`; minor units are taken at face value.
- **Result types vs. exceptions** for expected outcomes like `InsufficientFunds`.

## AI usage

The first pass was generated by Grok Build through three staged prompts, in order: domain types (`docs/ai/DATATYPE_GENERATION`), test suite (`docs/ai/TESTS.md`), then service and persistence (`docs/ai/OBJECTIVE.md`), under the agent guidance file `docs/ai/AGENTS.md`. Those files are kept verbatim for transparency.

Claude Code then reviewed that pass against the brief and the prompts. It found no correctness bugs; it flagged and — after my approval — fixed the following:

- transfer wrote the two updated records with two separate store calls; `LedgerStore.put` is now variadic and a transfer is one write (covered by `transfer_commits_both_sides_in_a_single_store_write`, written red-first);
- the concurrent-read test could not fail: its negative-balance check was unreachable (`Money` forbids negatives) and reader exceptions were swallowed in unread futures; it now asserts the set of committed balances and surfaces worker exceptions via `Future.get()`;
- duplicated record-update code collapsed into `AccountRecord.applied(entry)`, `Uuid` ordering via `compareTo` instead of `toString()`, amount validation moved before lock acquisition, a tautological assertion removed.

Every change was reviewed by me, and the full suite was run after each step. This README is co-written; the design decisions above are mine to defend.
