You are working on the Kotlin banking-service coding exercise in this repository.

Follow the repository's adopted LLM-dev-guides governance and project instructions. Load only the guides relevant to this task, especially testing, architecture, data/domain modelling, and security where applicable.

Your task in this step is to design and implement the test suite for the banking service.

Do not implement or modify production business logic merely to make tests pass.

The service must eventually support:

- Account creation with an initial deposit
- Deposits
- Withdrawals
- Balance queries
- Transfers between accounts
- No overdrafts
- Correct monetary handling
- Correct behaviour for invalid accounts and invalid amounts
- Atomic transfers
- Safe concurrent mutations

The intended domain model contains:

- Account
- AccountId
- TransactionId
- Money
- LedgerEntry
- TransactionType

The ledger should preserve transaction history, while a materialized balance may be used for efficient balance reads.

Generate tests around observable behaviour and domain invariants rather than implementation details.

## Test categories

### Account creation

Test:

- Creating an account with a valid initial deposit.
- Creating an account with zero initial balance should fail.
- Invalid initial deposit.
- Generated account IDs are unique
- Newly created account can be queried immediately.

### Deposits

Test:

- Valid deposit increases balance.
- Multiple deposits accumulate correctly.
- Zero/negative deposits are rejected.
- Deposit into a nonexistent account is rejected.
- Deposit creates the appropriate ledger history.

### Withdrawals

Test:

- Valid withdrawal decreases balance.
- Withdrawal of the entire balance succeeds if allowed.
- Withdrawal exceeding available funds fails.
- Failed withdrawal does not change the balance.
- Invalid amounts are rejected.
- Withdrawal from a nonexistent account fails.
- Successful withdrawal creates the appropriate ledger history.
- Failed withdrawal must not create a committed ledger entry.

### Transfers

Test:

- Valid transfer decreases source balance and increases destination balance.
- Full-balance transfer works if allowed.
- Insufficient source funds are rejected.
- Failed transfer leaves both balances unchanged.
- Invalid amounts are rejected.
- Nonexistent source account is rejected.
- Nonexistent destination account is rejected.
- Transfer to the same account follows the chosen domain rule.
- A successful transfer creates the appropriate paired ledger entries.
- The two sides of a transfer share a transaction identifier where that is the chosen model.

### Ledger invariants

Test that:

- Every successful monetary mutation has corresponding ledger history.
- Failed operations do not create committed ledger entries.
- Materialized balances agree with ledger history.
- Transfer preserves total money across the involved accounts.
- Account balances never become negative.

### Concurrency

Add focused concurrency tests for the important race conditions.

At minimum:

1. Many concurrent withdrawals against one account where the total requested amount exceeds the available balance.

Expected result:

- Some withdrawals succeed.
- Some fail due to insufficient funds.
- The final balance never becomes negative.
- The final balance equals the initial balance minus successful withdrawals.

2. Concurrent independent operations on different accounts should not corrupt state.

3. Concurrent transfers must not create or destroy money.

Do not write flaky timing-based tests. Prefer deterministic synchronization primitives, barriers, latches, repeated tasks, or controlled concurrency where appropriate.

Do not assert thread scheduling or execution order.

## Read concurrency

Tests should verify that balance reads can occur concurrently with mutations without corrupting state.

Do not require reads to be globally serialized unless the service's explicitly documented consistency model requires that.

## Test style

- Prefer behaviour-oriented test names.
- Keep tests deterministic.
- Avoid testing private implementation details.
- Avoid coupling tests to the specific in-memory data structures.
- Use property/invariant-style assertions where they make the behaviour clearer.
- Keep individual tests focused.
- Use parameterized tests where they materially reduce duplication.
- Do not create elaborate test infrastructure unless needed.

Before implementing tests:

1. Inspect the current production API and domain types.
2. Identify ambiguities in the API or domain rules.
3. Do not silently invent major business requirements.
4. Where a requirement is genuinely ambiguous, choose a reasonable interpretation and document it.

After implementation:

- Run the complete test suite.
- Report the tests created.
- Report any behaviour that remains unspecified.
- Identify any concurrency assumptions the production implementation must satisfy.
