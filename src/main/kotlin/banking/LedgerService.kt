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
    private val ledgerStore: LedgerStore = InMemoryLedgerStore(),
) {
    private val locks = ConcurrentHashMap<AccountId, ReentrantLock>()

    fun createAccount(userId: UserId, initialDeposit: Money): AccountId {
        if (!isPositiveAmount(initialDeposit)) throw InvalidAmount("Initial deposit must be greater than zero")
        val accountId = AccountId.generate()
        val account = Account(accountId, userId, initialDeposit.currency)
        val record = AccountRecord(
            account = account,
            balance = initialDeposit,
            entries = listOf(newEntry(accountId, initialDeposit, TransactionType.DEPOSIT)),
        )
        check(ledgerStore.putIfAbsent(record)) { "Generated account id collided" }
        return accountId
    }

    fun deposit(accountId: AccountId, amount: Money) {
        if (!isPositiveAmount(amount)) throw InvalidAmount("Deposit amount must be greater than zero")
        lockOneAccount(accountId) {
            val record = ensureGetAccountRecord(accountId)
            if (!isCurrencyMatch(record.account, amount)) {
                throw CurrencyMismatch("Deposit currency does not match the account")
            }
            ledgerStore.put(record.applied(newEntry(accountId, amount, TransactionType.DEPOSIT)))
        }
    }

    fun withdraw(accountId: AccountId, amount: Money) {
        if (!isPositiveAmount(amount)) throw InvalidAmount("Withdrawal amount must be greater than zero")
        lockOneAccount(accountId) {
            val record = ensureGetAccountRecord(accountId)
            if (!isCurrencyMatch(record.account, amount)) {
                throw CurrencyMismatch("Withdrawal currency does not match the account")
            }
            if (record.balance < amount) throw InsufficientFunds()
            ledgerStore.put(record.applied(newEntry(accountId, amount, TransactionType.WITHDRAWAL)))
        }
    }

    fun transfer(sourceId: AccountId, destinationId: AccountId, amount: Money) {
        if (sourceId == destinationId) throw SameAccountTransfer()
        if (!isPositiveAmount(amount)) throw InvalidAmount("Transfer amount must be greater than zero")
        lockTwoAccounts(sourceId, destinationId) {
            val source = ensureGetAccountRecord(sourceId)
            val destination = ensureGetAccountRecord(destinationId)
            if (!isCurrencyMatch(source.account, amount)) {
                throw CurrencyMismatch("Transfer currency does not match the source account")
            }
            if (!isCurrencyMatch(destination.account, amount)) {
                throw CurrencyMismatch("Transfer currency does not match the destination account")
            }
            if (source.balance < amount) throw InsufficientFunds()

            val transactionId = TransactionId.generate()
            val timestamp = Instant.now(clock)
            ledgerStore.put(
                source.applied(
                    newEntry(sourceId, amount, TransactionType.TRANSFER_OUT, transactionId, timestamp),
                ),
                destination.applied(
                    newEntry(destinationId, amount, TransactionType.TRANSFER_IN, transactionId, timestamp),
                ),
            )
        }
    }

    fun balance(accountId: AccountId): Money = ensureGetAccountRecord(accountId).balance

    fun history(accountId: AccountId): List<LedgerEntry> = ensureGetAccountRecord(accountId).entries.toList()

    private fun AccountRecord.applied(entry: LedgerEntry): AccountRecord {
        val newBalance = when (entry.type) {
            TransactionType.DEPOSIT, TransactionType.TRANSFER_IN -> balance + entry.amount
            TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT -> balance - entry.amount
        }
        return copy(balance = newBalance, entries = entries + entry)
    }

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

    private fun ensureGetAccountRecord(accountId: AccountId): AccountRecord =
        ledgerStore.get(accountId) ?: throw AccountNotFound(accountId)

    private fun isPositiveAmount(amount: Money): Boolean = amount.amountMinor.signum() > 0

    private fun isCurrencyMatch(account: Account, amount: Money): Boolean = account.currency == amount.currency

    private fun lockFor(accountId: AccountId): ReentrantLock =
        locks.computeIfAbsent(accountId) { ReentrantLock() }

    private inline fun <T> lockOneAccount(accountId: AccountId, action: () -> T): T {
        val lock = lockFor(accountId)
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    private inline fun <T> lockTwoAccounts(
        firstId: AccountId,
        secondId: AccountId,
        action: () -> T,
    ): T {
        val (first, second) = orderAccount(firstId, secondId)
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

    private fun orderAccount(firstId: AccountId, secondId: AccountId): Pair<AccountId, AccountId> =
        if (firstId.value <= secondId.value) {
            firstId to secondId 
        }
        else {
            secondId to firstId
    }
}
