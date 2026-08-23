package banking.persistence

import banking.domain.AccountId

interface LedgerStore {
    fun get(accountId: AccountId): AccountRecord?

    fun put(record: AccountRecord)

    fun putIfAbsent(record: AccountRecord): Boolean
}
