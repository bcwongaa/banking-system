package banking.domain

import java.time.Instant

// Bitemporal: occurredAt is when the business event happened, recordedAt is when this row was
// written. They only diverge once entries can be backdated, imported, or settled asynchronously.
data class LedgerEntry(
    val transactionId: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val type: TransactionType,
    val occurredAt: Instant,
    val recordedAt: Instant,
)
