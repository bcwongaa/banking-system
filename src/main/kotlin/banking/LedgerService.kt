package banking

import banking.domain.Account
import banking.domain.AccountId
import banking.domain.AccountNotFound
import banking.domain.CurrencyMismatch
import banking.domain.InsufficientFunds
import banking.domain.InvalidAmount
import banking.domain.LedgerEntry
import banking.domain.Money
import banking.domain.SameAccountTransfer
import banking.domain.TransactionId
import banking.domain.TransactionType
import banking.domain.UserId
import banking.persistence.AccountRecord
import banking.persistence.InMemoryLedgerStore
import banking.persistence.LedgerStore
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class LedgerService(
    private val clock: Clock = Clock.systemUTC(),
    private val store: LedgerStore = InMemoryLedgerStore(),
) {
    private val locks = ConcurrentHashMap<AccountId, ReentrantLock>()

    fun createAccount(userId: UserId, initialDeposit: Money): AccountId {
        requirePositive(initialDeposit)
        val accountId = AccountId.generate()
        val account = Account(accountId, userId, initialDeposit.currency)
        val record = AccountRecord(
            account = account,
            balance = initialDeposit,
            entries = listOf(newEntry(accountId, initialDeposit, TransactionType.DEPOSIT)),
        )
        check(store.putIfAbsent(record)) { "Generated account id collided" }
        return accountId
    }

    fun deposit(accountId: AccountId, amount: Money) {
        withLock(accountId) {
            val record = ensureRecord(accountId)
            requirePositive(amount)
            requireMatchingCurrency(record.account, amount)
            store.put(
                record.copy(
                    balance = record.balance + amount,
                    entries = record.entries + newEntry(accountId, amount, TransactionType.DEPOSIT),
                ),
            )
        }
    }

    fun withdraw(accountId: AccountId, amount: Money) {
        withLock(accountId) {
            val record = ensureRecord(accountId)
            requirePositive(amount)
            requireMatchingCurrency(record.account, amount)
            if (record.balance < amount) throw InsufficientFunds()
            store.put(
                record.copy(
                    balance = record.balance - amount,
                    entries = record.entries + newEntry(accountId, amount, TransactionType.WITHDRAWAL),
                ),
            )
        }
    }

    fun transfer(sourceId: AccountId, destinationId: AccountId, amount: Money) {
        if (sourceId == destinationId) throw SameAccountTransfer()
        requirePositive(amount)
        withLocks(sourceId, destinationId) {
            val source = ensureRecord(sourceId)
            val destination = ensureRecord(destinationId)
            requireMatchingCurrency(source.account, amount)
            requireMatchingCurrency(destination.account, amount)
            if (source.balance < amount) throw InsufficientFunds()
            val transactionId = TransactionId.generate()
            val timestamp = Instant.now(clock)
            store.put(
                source.copy(
                    balance = source.balance - amount,
                    entries = source.entries + newEntry(
                        accountId = sourceId,
                        amount = amount,
                        type = TransactionType.TRANSFER_OUT,
                        transactionId = transactionId,
                        timestamp = timestamp,
                    ),
                ),
            )
            store.put(
                destination.copy(
                    balance = destination.balance + amount,
                    entries = destination.entries + newEntry(
                        accountId = destinationId,
                        amount = amount,
                        type = TransactionType.TRANSFER_IN,
                        transactionId = transactionId,
                        timestamp = timestamp,
                    ),
                ),
            )
        }
    }

    fun balance(accountId: AccountId): Money = ensureRecord(accountId).balance

    fun history(accountId: AccountId): List<LedgerEntry> = ensureRecord(accountId).entries.toList()

    private fun newEntry(
        accountId: AccountId,
        amount: Money,
        type: TransactionType,
        transactionId: TransactionId = TransactionId.generate(),
        timestamp: Instant = Instant.now(clock),
    ): LedgerEntry = LedgerEntry(
        transactionId = transactionId,
        accountId = accountId,
        amount = amount,
        type = type,
        timestamp = timestamp,
    )

    private fun ensureRecord(accountId: AccountId): AccountRecord =
        store.get(accountId) ?: throw AccountNotFound(accountId)

    private fun requirePositive(amount: Money) {
        if (amount.amountMinor.signum() <= 0) throw InvalidAmount()
    }

    private fun requireMatchingCurrency(account: Account, amount: Money) {
        if (account.currency != amount.currency) throw CurrencyMismatch()
    }

    private fun lockFor(accountId: AccountId): ReentrantLock =
        locks.computeIfAbsent(accountId) { ReentrantLock() }

    private inline fun <T> withLock(accountId: AccountId, action: () -> T): T {
        val lock = lockFor(accountId)
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    private inline fun <T> withLocks(
        firstId: AccountId,
        secondId: AccountId,
        action: () -> T,
    ): T {
        val (first, second) = ordered(firstId, secondId)
        val firstLock = lockFor(first)
        val secondLock = lockFor(second)
        firstLock.lock()
        try {
            secondLock.lock()
            try {
                return action()
            } finally {
                secondLock.unlock()
            }
        } finally {
            firstLock.unlock()
        }
    }

    private fun ordered(firstId: AccountId, secondId: AccountId): Pair<AccountId, AccountId> =
        if (firstId.value.toString() <= secondId.value.toString()) {
            firstId to secondId
        } else {
            secondId to firstId
        }
}
