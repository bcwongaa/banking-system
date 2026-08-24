package banking.persistence

import banking.domain.Account
import banking.domain.LedgerEntry
import banking.domain.Money

// The balance is a projection of `entries`, not an independent fact. Construction is closed so the
// two cannot be set apart from each other: every balance is produced by applying an entry.
@ConsistentCopyVisibility
data class AccountRecord private constructor(
    val account: Account,
    val balance: Money,
    val entries: List<LedgerEntry>,
) {
    fun applied(ledgerEntry: LedgerEntry): AccountRecord =
        copy(balance = balance + ledgerEntry.signedAmount, entries = entries + ledgerEntry)

    companion object {
        fun opened(account: Account, openingEntry: LedgerEntry): AccountRecord =
            AccountRecord(account, openingEntry.signedAmount, listOf(openingEntry))
    }
}
