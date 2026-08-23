package banking.domain

import java.time.Instant

data class LedgerEntry(
    val transactionId: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val type: TransactionType,
    val timestamp: Instant,
)
