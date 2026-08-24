package banking

import banking.domain.AccountId
import banking.domain.AccountNotFound
import banking.domain.CurrencyMismatch
import banking.domain.InsufficientFunds
import banking.domain.InvalidAmount
import banking.domain.LedgerEntry
import banking.domain.Money
import banking.domain.SameAccountTransfer
import banking.domain.TransactionType
import banking.domain.UserId
import banking.persistence.AccountRecord
import banking.persistence.InMemoryLedgerStore
import banking.persistence.LedgerStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LedgerServiceTest {
    private val usd: Currency = Currency.getInstance("USD")
    private val eur: Currency = Currency.getInstance("EUR")
    private val instant: Instant = Instant.parse("2026-08-23T12:00:00Z")
    private val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun creating_an_account_with_a_valid_initial_deposit_sets_that_balance() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(500))
        assertEquals(usd(500), service.balance(accountId))
    }

    @Test
    fun creating_an_account_with_zero_initial_deposit_fails() {
        val service = service()
        assertFailsWith<InvalidAmount> {
            service.createAccount(userId(), usd(0))
        }
    }

    @Test
    fun generated_account_ids_are_unique() {
        val service = service()
        val ids = List(200) { service.createAccount(userId(), usd(1)) }.toSet()
        assertEquals(200, ids.size)
    }

    @Test
    fun a_newly_created_account_can_be_queried_immediately() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertEquals(usd(100), service.balance(accountId))
        assertEquals(1, service.history(accountId).size)
    }

    @Test
    fun a_valid_deposit_increases_balance() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.deposit(accountId, usd(40))
        assertEquals(usd(140), service.balance(accountId))
    }

    @Test
    fun multiple_deposits_accumulate() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.deposit(accountId, usd(20))
        service.deposit(accountId, usd(30))
        assertEquals(usd(150), service.balance(accountId))
    }

    @Test
    fun zero_deposit_is_rejected() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertFailsWith<InvalidAmount> { service.deposit(accountId, usd(0)) }
        assertEquals(usd(100), service.balance(accountId))
    }

    @Test
    fun negative_amounts_are_rejected_by_every_mutation() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(100))
        val negative = usd(-1)

        assertFailsWith<InvalidAmount> { service.deposit(source, negative) }
        assertFailsWith<InvalidAmount> { service.withdraw(source, negative) }
        assertFailsWith<InvalidAmount> { service.transfer(source, destination, negative) }
        assertFailsWith<InvalidAmount> { service.createAccount(userId(), negative) }

        assertEquals(usd(100), service.balance(source))
        assertEquals(usd(100), service.balance(destination))
    }

    @Test
    fun deposit_into_a_missing_account_is_rejected() {
        val service = service()
        assertFailsWith<AccountNotFound> {
            service.deposit(AccountId.generate(), usd(10))
        }
    }

    @Test
    fun deposit_creates_a_deposit_ledger_entry() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.deposit(accountId, usd(25))
        val entry = service.history(accountId).last()
        assertEquals(TransactionType.DEPOSIT, entry.type)
        assertEquals(usd(25), entry.amount)
        assertEquals(accountId, entry.accountId)
        assertEquals(instant, entry.occurredAt)
    }

    @Test
    fun a_valid_withdrawal_decreases_balance() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.withdraw(accountId, usd(40))
        assertEquals(usd(60), service.balance(accountId))
    }

    @Test
    fun withdrawing_the_entire_balance_succeeds() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.withdraw(accountId, usd(100))
        assertEquals(usd(0), service.balance(accountId))
    }

    @Test
    fun withdrawal_exceeding_funds_fails_and_does_not_change_balance() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertFailsWith<InsufficientFunds> { service.withdraw(accountId, usd(101)) }
        assertEquals(usd(100), service.balance(accountId))
    }

    @Test
    fun zero_withdrawal_is_rejected() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertFailsWith<InvalidAmount> { service.withdraw(accountId, usd(0)) }
        assertEquals(usd(100), service.balance(accountId))
    }

    @Test
    fun withdrawal_from_a_missing_account_fails() {
        val service = service()
        assertFailsWith<AccountNotFound> {
            service.withdraw(AccountId.generate(), usd(10))
        }
    }

    @Test
    fun successful_withdrawal_creates_a_withdrawal_ledger_entry() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        service.withdraw(accountId, usd(40))
        val entry = service.history(accountId).last()
        assertEquals(TransactionType.WITHDRAWAL, entry.type)
        assertEquals(usd(40), entry.amount)
        assertEquals(instant, entry.occurredAt)
    }

    @Test
    fun failed_withdrawal_does_not_create_a_ledger_entry() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        val before = service.history(accountId)
        assertFailsWith<InsufficientFunds> { service.withdraw(accountId, usd(101)) }
        assertEquals(before, service.history(accountId))
    }

    @Test
    fun a_valid_transfer_moves_funds_between_accounts() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        service.transfer(source, destination, usd(30))
        assertEquals(usd(70), service.balance(source))
        assertEquals(usd(50), service.balance(destination))
    }

    @Test
    fun a_full_balance_transfer_succeeds() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(1))
        service.transfer(source, destination, usd(100))
        assertEquals(usd(0), service.balance(source))
        assertEquals(usd(101), service.balance(destination))
    }

    @Test
    fun transfer_with_insufficient_funds_leaves_both_balances_unchanged() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        assertFailsWith<InsufficientFunds> { service.transfer(source, destination, usd(101)) }
        assertEquals(usd(100), service.balance(source))
        assertEquals(usd(20), service.balance(destination))
    }

    @Test
    fun zero_transfer_is_rejected() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        assertFailsWith<InvalidAmount> { service.transfer(source, destination, usd(0)) }
        assertEquals(usd(100), service.balance(source))
        assertEquals(usd(20), service.balance(destination))
    }

    @Test
    fun transfer_from_a_missing_source_is_rejected() {
        val service = service()
        val destination = service.createAccount(userId(), usd(20))
        assertFailsWith<AccountNotFound> {
            service.transfer(AccountId.generate(), destination, usd(10))
        }
        assertEquals(usd(20), service.balance(destination))
    }

    @Test
    fun transfer_to_a_missing_destination_is_rejected() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        assertFailsWith<AccountNotFound> {
            service.transfer(source, AccountId.generate(), usd(10))
        }
        assertEquals(usd(100), service.balance(source))
    }

    @Test
    fun transfer_to_the_same_account_is_rejected() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertFailsWith<SameAccountTransfer> {
            service.transfer(accountId, accountId, usd(10))
        }
        assertEquals(usd(100), service.balance(accountId))
        assertEquals(1, service.history(accountId).size)
    }

    @Test
    fun a_successful_transfer_creates_paired_ledger_entries_with_the_same_transaction_id() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        service.transfer(source, destination, usd(30))
        val out = service.history(source).last()
        val incoming = service.history(destination).last()
        assertEquals(TransactionType.TRANSFER_OUT, out.type)
        assertEquals(TransactionType.TRANSFER_IN, incoming.type)
        assertEquals(usd(30), out.amount)
        assertEquals(usd(30), incoming.amount)
        assertEquals(out.transactionId, incoming.transactionId)
        assertEquals(instant, out.occurredAt)
        assertEquals(instant, incoming.occurredAt)
    }

    @Test
    fun transfer_commits_both_sides_in_a_single_store_write() {
        val store = RecordingStore()
        val service = LedgerService(clock = clock, ledgerStore = store)
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        store.writes.clear()
        service.transfer(source, destination, usd(30))
        val write = store.writes.single()
        assertEquals(setOf(source, destination), write.map { it.account.accountId }.toSet())
    }

    @Test
    fun failed_transfer_does_not_create_ledger_entries() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        val sourceBefore = service.history(source)
        val destBefore = service.history(destination)
        assertFailsWith<InsufficientFunds> { service.transfer(source, destination, usd(101)) }
        assertEquals(sourceBefore, service.history(source))
        assertEquals(destBefore, service.history(destination))
    }

    @Test
    fun materialized_balance_agrees_with_ledger_history() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(50))
        service.deposit(source, usd(25))
        service.withdraw(source, usd(10))
        service.transfer(source, destination, usd(40))
        assertEquals(project(service.history(source)), service.balance(source))
        assertEquals(project(service.history(destination)), service.balance(destination))
    }

    @Test
    fun transfer_preserves_total_money_across_the_involved_accounts() {
        val service = service()
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(50))
        service.transfer(source, destination, usd(40))
        assertEquals(usd(150), service.balance(source) + service.balance(destination))
    }

    @Test
    fun account_balances_never_become_negative_through_the_public_api() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(10))
        assertFailsWith<InsufficientFunds> { service.withdraw(accountId, usd(11)) }
        assertTrue(service.balance(accountId) >= usd(0))
    }

    @Test
    fun deposit_of_a_mismatched_currency_is_rejected() {
        val service = service()
        val accountId = service.createAccount(userId(), usd(100))
        assertFailsWith<CurrencyMismatch> { service.deposit(accountId, eur(10)) }
        assertEquals(usd(100), service.balance(accountId))
    }

    @Test
    fun transfer_across_currencies_is_rejected() {
        val service = service()
        val usdAccount = service.createAccount(userId(), usd(100))
        val eurAccount = service.createAccount(userId(), eur(100))
        assertFailsWith<CurrencyMismatch> { service.transfer(usdAccount, eurAccount, usd(10)) }
        assertEquals(usd(100), service.balance(usdAccount))
        assertEquals(eur(100), service.balance(eurAccount))
    }

    @Test
    fun balance_of_a_missing_account_fails() {
        val service = service()
        assertFailsWith<AccountNotFound> { service.balance(AccountId.generate()) }
    }

    @Test
    fun both_legs_of_a_transfer_share_one_occurrence_instant() {
        val service = LedgerService(clock = TickingClock(instant))
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        service.transfer(source, destination, usd(30))

        val out = service.history(source).last()
        val incoming = service.history(destination).last()
        assertEquals(out.occurredAt, incoming.occurredAt)
    }

    @Test
    fun each_leg_of_a_transfer_is_recorded_as_its_own_write() {
        val service = LedgerService(clock = TickingClock(instant))
        val source = service.createAccount(userId(), usd(100))
        val destination = service.createAccount(userId(), usd(20))
        service.transfer(source, destination, usd(30))

        val out = service.history(source).last()
        val incoming = service.history(destination).last()
        assertNotEquals(out.recordedAt, incoming.recordedAt)
    }

    @Test
    fun an_entry_is_recorded_no_earlier_than_it_occurred() {
        val service = LedgerService(clock = TickingClock(instant))
        val accountId = service.createAccount(userId(), usd(100))
        service.deposit(accountId, usd(25))

        val entry = service.history(accountId).last()
        assertTrue(entry.recordedAt >= entry.occurredAt)
    }

    private fun service(): LedgerService = LedgerService(clock = clock)

    // A fixed clock cannot tell occurredAt and recordedAt apart; this one advances on every read.
    private class TickingClock(private var current: Instant) : Clock() {
        override fun instant(): Instant = current.also { current = current.plusMillis(1) }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private class RecordingStore(
        private val inner: InMemoryLedgerStore = InMemoryLedgerStore(),
    ) : LedgerStore by inner {
        val writes = mutableListOf<List<AccountRecord>>()

        override fun put(vararg records: AccountRecord) {
            writes += records.toList()
            inner.put(*records)
        }
    }

    private fun usd(cents: Long): Money = Money.ofMinorUnits(cents, usd)

    private fun eur(cents: Long): Money = Money.ofMinorUnits(cents, eur)

    private fun userId(): UserId = UserId(Uuid.random())

    private fun project(entries: List<LedgerEntry>): Money {
        val currency = entries.first().amount.currency
        return entries.fold(Money.ofMinorUnits(0, currency)) { running, entry ->
            when (entry.type) {
                TransactionType.DEPOSIT, TransactionType.TRANSFER_IN -> running + entry.amount
                TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT -> running - entry.amount
            }
        }
    }
}
