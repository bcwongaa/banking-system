package banking

import banking.domain.AccountId
import banking.domain.InsufficientFunds
import banking.domain.Money
import banking.domain.UserId
import java.util.Currency
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

        val succeeded = results.count { it == Outcome.SUCCESS }
        val rejected = results.count { it == Outcome.REJECTED }
        assertEquals(workers, succeeded + rejected)
        assertTrue(succeeded > 0)
        assertTrue(rejected > 0)
        assertEquals(10, succeeded)
        assertEquals(usd(0), service.balance(accountId))
        assertEquals(projectBalance(service, accountId), service.balance(accountId))
        assertTrue(service.balance(accountId) >= usd(0))
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
        assertTrue(service.balance(first) >= usd(0))
        assertTrue(service.balance(second) >= usd(0))
        assertEquals(projectBalance(service, first), service.balance(first))
        assertEquals(projectBalance(service, second), service.balance(second))
    }

    @Test
    fun balance_reads_during_mutations_do_not_corrupt_state() {
        val service = LedgerService()
        val accountId = service.createAccount(userId(), usd(500))
        val stop = AtomicInteger(0)
        val seenNegative = AtomicInteger(0)
        val readers = Executors.newFixedThreadPool(4)
        val readerStart = CountDownLatch(4)
        repeat(4) {
            readers.submit {
                readerStart.countDown()
                while (stop.get() == 0) {
                    val balance = service.balance(accountId)
                    if (balance < usd(0)) {
                        seenNegative.incrementAndGet()
                    }
                }
            }
        }
        assertTrue(readerStart.await(5, TimeUnit.SECONDS))
        runConcurrent(20) {
            try {
                service.withdraw(accountId, usd(10))
                Outcome.SUCCESS
            } catch (_: InsufficientFunds) {
                Outcome.REJECTED
            }
        }
        stop.set(1)
        readers.shutdown()
        assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(0, seenNegative.get())
        assertEquals(usd(300), service.balance(accountId))
        assertEquals(projectBalance(service, accountId), service.balance(accountId))
    }

    private enum class Outcome { SUCCESS, REJECTED }

    private fun runConcurrent(workers: Int, task: (Int) -> Outcome): List<Outcome> {
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val outcomes = Array<Outcome?>(workers) { null }
        val pool = Executors.newFixedThreadPool(workers)
        repeat(workers) { index ->
            pool.submit {
                start.await()
                try {
                    outcomes[index] = task(index)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(15, TimeUnit.SECONDS), "concurrent work timed out")
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        return outcomes.map { checkNotNull(it) }
    }

    private fun projectBalance(service: LedgerService, accountId: AccountId): Money {
        val entries = service.history(accountId)
        val currency = entries.first().amount.currency
        return entries.fold(Money(0, currency)) { running, entry ->
            when (entry.type) {
                banking.domain.TransactionType.DEPOSIT,
                banking.domain.TransactionType.TRANSFER_IN,
                -> running + entry.amount
                banking.domain.TransactionType.WITHDRAWAL,
                banking.domain.TransactionType.TRANSFER_OUT,
                -> running - entry.amount
            }
        }
    }

    private fun usd(cents: Long): Money = Money(cents, usd)

    private fun userId(): UserId = UserId(Uuid.random())
}
