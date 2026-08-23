package banking.persistence

import banking.domain.AccountId
import java.util.concurrent.ConcurrentHashMap

class InMemoryLedgerStore : LedgerStore {
    private val records = ConcurrentHashMap<AccountId, AccountRecord>()

    override fun get(accountId: AccountId): AccountRecord? = records[accountId]

    override fun put(record: AccountRecord) {
        records[record.account.accountId] = record
    }

    override fun putIfAbsent(record: AccountRecord): Boolean =
        records.putIfAbsent(record.account.accountId, record) == null
}
