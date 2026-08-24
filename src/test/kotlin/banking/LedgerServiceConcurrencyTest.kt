package banking

import banking.domain.AccountId
import banking.domain.InsufficientFunds
import banking.domain.Money
import banking.domain.TransactionType
import banking.domain.UserId
import java.math.BigInteger
import java.util.Currency
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LedgerServiceConcurrencyTest {
    private val usd: Currency = Currency.getInstance("USD")

    @Test
    fun concurrent_withdrawals_never_overdraw_and_match_successful_debits() {
        val service = LedgerService()
        val accountId = service.createAccount(userId(), usd(1_000))
        val workers = 50
        val debit = usd(100)
        val results = runConcurrent(workers) {
            try {
                service.withdraw(accountId, debit)
                Outcome.SUCCESS
            } catch (_: InsufficientFunds) {
                Outcome.REJECTED
            }
        }

        assertEquals(10, results.count { it == Outcome.SUCCESS })
        assertEquals(40, results.count { it == Outcome.REJECTED })
        assertEquals(usd(0), service.balance(accountId))
        assertEquals(projectBalance(service, accountId), service.balance(accountId))
    }

    @Test
    fun concurrent_operations_on_different_accounts_do_not_corrupt_state() {
        val service = LedgerService()
        val first = service.createAccount(userId(), usd(100))
        val second = service.createAccount(userId(), usd(100))
        val workers = 40
        runConcurrent(workers) { index ->
            val accountId = if (index % 2 == 0) first else second
            service.deposit(accountId, usd(5))
            Outcome.SUCCESS
        }
        assertEquals(usd(200), service.balance(first))
        assertEquals(usd(200), service.balance(second))
        assertEquals(projectBalance(service, first), service.balance(first))
        assertEquals(projectBalance(service, second), service.balance(second))
    }

    @Test
    fun concurrent_transfers_neither_create_nor_destroy_money() {
        val service = LedgerService()
        val first = service.createAccount(userId(), usd(1_000))
        val second = service.createAccount(userId(), usd(1_000))
        val workers = 40
        runConcurrent(workers) { index ->
            if (index % 2 == 0) {
                service.transfer(first, second, usd(7))
            } else {
                service.transfer(second, first, usd(7))
            }
            Outcome.SUCCESS
        }
        val total = service.balance(first) + service.balance(second)
        assertEquals(usd(2_000), total)
        assertEquals(projectBalance(service, first), service.balance(first))
        assertEquals(projectBalance(service, second), service.balance(second))
    }

    @Test
    fun balance_reads_during_mutations_only_observe_committed_balances() {
        val service = LedgerService()
        val accountId = service.createAccount(userId(), usd(500))
        val step = usd(10)
        val stop = AtomicBoolean(false)
        val readerCount = 4
        val readers = Executors.newFixedThreadPool(readerCount)
        val readerStart = CountDownLatch(readerCount)
        val reads = List(readerCount) {
            readers.submit {
                readerStart.countDown()
                while (!stop.get()) {
                    val balance = service.balance(accountId)
                    assertTrue(balance in usd(300)..usd(500), "balance outside committed range: $balance")
                    assertEquals(BigInteger.ZERO, balance.amountMinor.mod(step.amountMinor), "torn balance: $balance")
                }
            }
        }
        assertTrue(readerStart.await(5, TimeUnit.SECONDS))
        runConcurrent(20) {
            service.withdraw(accountId, step)
            Outcome.SUCCESS
        }
        stop.set(true)
        reads.forEach { it.get(5, TimeUnit.SECONDS) }
        readers.shutdown()
        assertEquals(usd(300), service.balance(accountId))
        assertEquals(projectBalance(service, accountId), service.balance(accountId))
    }

    private enum class Outcome { SUCCESS, REJECTED }

    private fun runConcurrent(workers: Int, task: (Int) -> Outcome): List<Outcome> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val futures = List(workers) { index ->
                pool.submit<Outcome> {
                    start.await()
                    task(index)
                }
            }
            start.countDown()
            return futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun projectBalance(service: LedgerService, accountId: AccountId): Money {
        val entries = service.history(accountId)
        val currency = entries.first().amount.currency
        return entries.fold(Money.ofMinorUnits(0, currency)) { running, entry ->
            when (entry.type) {
                TransactionType.DEPOSIT, TransactionType.TRANSFER_IN -> running + entry.amount
                TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT -> running - entry.amount
            }
        }
    }

    private fun usd(cents: Long): Money = Money.ofMinorUnits(cents, usd)

    private fun userId(): UserId = UserId(Uuid.random())
}
