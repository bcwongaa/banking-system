package banking.persistence

import banking.domain.Account
import banking.domain.LedgerEntry
import banking.domain.Money

data class AccountRecord(
    val account: Account,
    val balance: Money,
    val entries: List<LedgerEntry>,
)
