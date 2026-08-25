# Banking service

An in-process Kotlin component that manages accounts and processes deposits, withdrawals, transfers and balance queries on top of an append-only ledger. Solution to `senior_engineer_test.md`.

## Run

```sh
./gradlew test
```

A JDK must be on the path to bootstrap Gradle; the build targets JDK 25 through the Gradle toolchain. Kotlin 2.4, Gradle 9.3, `kotlin.test` on JUnit 5. No other dependencies.

## Layout

```
src/main/kotlin/banking/
  LedgerService.kt            public API, validation, concurrency control
  domain/
    Money.kt                  signed BigInteger minor units + java.util.Currency
    Account.kt                accountId, userId, currency — no balance
    AccountId.kt              value class over kotlin.uuid.Uuid
    TransactionId.kt          value class over kotlin.uuid.Uuid, distinct from AccountId
    UserId.kt                 value class over kotlin.uuid.Uuid
    LedgerEntry.kt            transactionId, accountId, amount, type, occurredAt, recordedAt
    TransactionType.kt        DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT
    BankingException.kt       sealed hierarchy of domain errors
  persistence/
    LedgerStore.kt            get / put(vararg) / putIfAbsent
    InMemoryLedgerStore.kt    ConcurrentHashMap<AccountId, AccountRecord>
    AccountRecord.kt          account + balance + entries
```

## API

```kotlin
val service = LedgerService()                                  // clock and store are injectable

val id = service.createAccount(userId, Money.ofMinorUnits(1_000, usd))
service.deposit(id, Money.ofMinorUnits(250, usd))
service.withdraw(id, Money.ofMinorUnits(100, usd))             // InsufficientFunds if too large
service.transfer(id, otherId, Money.ofMinorUnits(50, usd))     // one write, paired ledger entries
service.balance(id)                                            // Money
service.history(id)                                            // List<LedgerEntry>, oldest first
```

Every failure is a `BankingException` subtype: `InvalidAmount`, `AccountNotFound`, `InsufficientFunds`, `CurrencyMismatch`, `SameAccountTransfer`. A failed operation changes nothing. Each place that throws writes its own message, so a rejected transfer says whether it was the source or the destination currency that did not match.

## Design

**Money only does maths.** Amounts are whole numbers of the currency's smallest unit — cents for USD — held as `BigInteger` and tagged with a `java.util.Currency`. No floating point, so nothing rounds by accident. Adding or comparing two different currencies throws. `Money` knows nothing about banking: it can be negative, because subtracting a larger amount from a smaller one is ordinary maths.

You cannot call the constructor. Amounts are built through `Money.ofMinorUnits(500, usd)` — 500 cents — or `Money.ofMajorUnits(BigDecimal("5.00"), usd)` — 5 dollars. Every place that creates money therefore says which unit it means, and `Money(500, usd)` will not compile. `ofMajorUnits` converts using the currency's own number of decimal places, so it is right for yen (0 decimals) and Kuwaiti dinar (3), not just cents. If the amount is more precise than the currency allows — `5.005` USD — it throws instead of quietly rounding it away.

**Each entry records two times.** `occurredAt` is when the transaction happened; `recordedAt` is when the row was written down.

**Concurrency.** Each account has its own lock, created on first use and held while an operation reads the balance, checks it and writes the result. A transfer takes both accounts' locks, always in the same order sorted by id, preventing deadlock. Reading a balance or history takes no lock at all: records are immutable, so a reader always sees a complete, finished version.

The effect is that operations on one account happen one at a time and in order, balances never go negative, and no update is ever lost. One gap: a reader looking at *two* accounts during a transfer can briefly see the money in neither, because the two records are written one after the other. No money is created or destroyed either way. .

### Business Logic Decisions 

| Decision | Choice                                                 |
|---|--------------------------------------------------------|
| Opening an account with zero | rejected — as requested                                |
| Transfer to the same account | rejected — it would mean nothing in the ledger         |
| Moving money between currencies | rejected — exchange rates are out of scope             |
| Reporting failures | exceptions rather than a `Result` type                 |
| Recording a transfer | two entries sharing one `TransactionId`                |
| Time | an injected `java.time.Clock`, so tests are repeatable |
| Negative `Money` | allowed because negative money itself make sense       |
| Accounts per customer | unlimited, including several in the same currency      |

## Tests

67 tests, nothing beyond `kotlin.test` on JUnit 5.

| File | Count | Covers |
|---|---|---|
| `LedgerServiceTest` | 35 | every operation and how it fails, history, currency mismatches, timestamps |
| `MoneyTest` | 20 | arithmetic, currency guards, both factories, construction |
| `LedgerServiceConcurrencyTest` | 4 | racing withdrawals, separate accounts, transfers, reads during writes |
| `AccountRecordTest` | 4 | balance follows the entries; the record cannot be built any other way |
| `LedgerEntryTest` | 2 | which way each transaction type moves money |
| `AccountIdTest` | 2 | ids are unique, and account ids are not transaction ids |

## Known limitations

- A reader looking at two accounts mid-transfer can briefly see the money in neither. A database would make the two writes one transaction.
- Each new entry copies the whole list, so an account with *n* entries costs O(n) per write.
- `put` overwrites whatever is there. The service never misuses it, but nothing in the type system prevents it.
- No idempotency key, so a retried transfer or account creation after a timeout would happen twice.
- `ofMinorUnits` takes the number it is given; only `ofMajorUnits` checks it against the currency's decimal places.

## How AI was used

AI tools were used throughout, following a process I maintain as a public set of development guides: <https://github.com/bcwongaa/LLM-dev-guides>. Applying them to this task exposed several gaps, which I fixed in the guides as I went.

1. **Scope.** Decided what to build and what to leave out — no database, no API layer, in-memory storage — before any code was written.
2. **Setup.** Built the Gradle and Kotlin module, JDK toolchain and test harness by hand, so the generated code had a fixed target instead of choosing its own stack.
3. **Specification, then a first pass.** Wrote the domain model and test suite as briefs, kept in `docs/ai/`, and had Grok Build implement them. Immediately pass through one review via Claude code as well to catch bugs before I started reading.
4. **Test review.** Went through each generated test file to check it tested what it claimed. Several could not fail at all; those were fixed before I trusted the implementation.
5. **Code review and challenge.** Questioned naming, structure and any decision I disagreed with, and asked for the reasoning instead of accepting the output. Letting `Money` go negative, splitting the timestamp in two, and locking down how an account record is built all came out of that. Suggestions beyond the scope of the exercise were dropped.
6. **Re-checking.** Ran the full suite after every edit, and checked the compiler-enforced rules by deliberately breaking them to confirm the right test failed.
7. **Documentation.** Written last, then checked against the source.

AI supplied speed on the mechanical work: scaffolding, the first pass of the types and tests, and repetitive refactoring. The design decisions are mine.
