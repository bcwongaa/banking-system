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
        val accountRecord = AccountRecord(
            account = account,
            balance = initialDeposit,
            entries = listOf(newEntry(accountId, initialDeposit, TransactionType.DEPOSIT)),
        )
        check(ledgerStore.putIfAbsent(accountRecord)) { "Generated account id collided" }
        return accountId
    }

    fun deposit(accountId: AccountId, amount: Money) {
        if (!isPositiveAmount(amount)) throw InvalidAmount("Deposit amount must be greater than zero")
        lockOneAccount(accountId) {
            val accountRecord = ensureGetAccountRecord(accountId)
            if (!isCurrencyMatch(accountRecord.account, amount)) {
                throw CurrencyMismatch("Deposit currency does not match the account")
            }
            ledgerStore.put(accountRecord.applied(newEntry(accountId, amount, TransactionType.DEPOSIT)))
        }
    }

    fun withdraw(accountId: AccountId, amount: Money) {
        if (!isPositiveAmount(amount)) throw InvalidAmount("Withdrawal amount must be greater than zero")
        lockOneAccount(accountId) {
            val accountRecord = ensureGetAccountRecord(accountId)
            if (!isCurrencyMatch(accountRecord.account, amount)) {
                throw CurrencyMismatch("Withdrawal currency does not match the account")
            }
            if (accountRecord.balance < amount) throw InsufficientFunds()
            ledgerStore.put(accountRecord.applied(newEntry(accountId, amount, TransactionType.WITHDRAWAL)))
        }
    }

    fun transfer(sourceAccount: AccountId, destinationAccount: AccountId, amount: Money) {
        if (sourceAccount == destinationAccount) throw SameAccountTransfer()
        if (!isPositiveAmount(amount)) throw InvalidAmount("Transfer amount must be greater than zero")
        lockTwoAccounts(sourceAccount, destinationAccount) {
            val sourceRecord = ensureGetAccountRecord(sourceAccount)
            val destinationRecord = ensureGetAccountRecord(destinationAccount)
            if (!isCurrencyMatch(sourceRecord.account, amount)) {
                throw CurrencyMismatch("Transfer currency does not match the source account")
            }
            if (!isCurrencyMatch(destinationRecord.account, amount)) {
                throw CurrencyMismatch("Transfer currency does not match the destination account")
            }
            if (sourceRecord.balance < amount) throw InsufficientFunds()

            val transactionId = TransactionId.generate()
            val occurredAt = Instant.now(clock)
            ledgerStore.put(
                sourceRecord.applied(
                    newEntry(sourceAccount, amount, TransactionType.TRANSFER_OUT, transactionId, occurredAt),
                ),
                destinationRecord.applied(
                    newEntry(destinationAccount, amount, TransactionType.TRANSFER_IN, transactionId, occurredAt),
                ),
            )
        }
    }

    fun balance(accountId: AccountId): Money = ensureGetAccountRecord(accountId).balance

    fun history(accountId: AccountId): List<LedgerEntry> = ensureGetAccountRecord(accountId).entries.toList()

    private fun AccountRecord.applied(ledgerEntry: LedgerEntry): AccountRecord {
        val newBalance = when (ledgerEntry.type) {
            TransactionType.DEPOSIT, TransactionType.TRANSFER_IN -> balance + ledgerEntry.amount
            TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT -> balance - ledgerEntry.amount
        }
        return copy(balance = newBalance, entries = entries + ledgerEntry)
    }

    private fun newEntry(
        accountId: AccountId,
        amount: Money,
        type: TransactionType,
        transactionId: TransactionId = TransactionId.generate(),
        occurredAt: Instant = Instant.now(clock),
    ): LedgerEntry = LedgerEntry(
        transactionId = transactionId,
        accountId = accountId,
        amount = amount,
        type = type,
        occurredAt = occurredAt,
        recordedAt = Instant.now(clock),
    )

    private fun ensureGetAccountRecord(accountId: AccountId): AccountRecord =
        ledgerStore.get(accountId) ?: throw AccountNotFound(accountId)

    private fun isPositiveAmount(amount: Money): Boolean = amount.amountMinor.signum() > 0

    private fun isCurrencyMatch(account: Account, amount: Money): Boolean = account.currency == amount.currency

    private fun lockFor(accountId: AccountId): ReentrantLock =
        locks.computeIfAbsent(accountId) { ReentrantLock() }

    private inline fun <T> lockOneAccount(accountId: AccountId, action: () -> T): T {
        val accountLock = lockFor(accountId)
        accountLock.lock()
        try {
            return action()
        } finally {
            accountLock.unlock()
        }
    }

    private inline fun <T> lockTwoAccounts(
        firstId: AccountId,
        secondId: AccountId,
        action: () -> T,
    ): T {
        val (lowerId, higherId) = orderAccount(firstId, secondId)
        val lowerLock = lockFor(lowerId)
        val higherLock = lockFor(higherId)
        lowerLock.lock()
        try {
            higherLock.lock()
            try {
                return action()
            } finally {
                higherLock.unlock()
            }
        } finally {
            lowerLock.unlock()
        }
    }

    private fun orderAccount(firstId: AccountId, secondId: AccountId): Pair<AccountId, AccountId> =
        if (firstId.value <= secondId.value) {
            firstId to secondId
        } else {
            secondId to firstId
        }
}
