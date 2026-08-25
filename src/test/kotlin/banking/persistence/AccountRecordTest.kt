package banking.persistence

import banking.domain.Account
import banking.domain.AccountId
import banking.domain.LedgerEntry
import banking.domain.Money
import banking.domain.TransactionId
import banking.domain.TransactionType
import banking.domain.UserId
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class AccountRecordTest {
    private val usd: Currency = Currency.getInstance("USD")

    @Test
    fun an_opened_record_takes_its_balance_from_the_opening_entry() {
        val opening = entry(TransactionType.DEPOSIT, 500)
        val record = AccountRecord.opened(account(), opening)
        assertEquals(Money.ofMinorUnits(500, usd), record.balance)
        assertEquals(listOf(opening), record.entries)
    }

    @Test
    fun applying_an_entry_moves_the_balance_and_the_history_together() {
        val record = AccountRecord.opened(account(), entry(TransactionType.DEPOSIT, 500))
            .applied(entry(TransactionType.WITHDRAWAL, 200))
            .applied(entry(TransactionType.TRANSFER_IN, 50))

        assertEquals(Money.ofMinorUnits(350, usd), record.balance)
        assertEquals(3, record.entries.size)
    }

    // Reflection: the guarantee is compile-time, so it cannot be asserted from compiling code.
    // These catch a future edit reopening a way to build a record whose balance contradicts its entries.
    @Test
    fun a_record_cannot_be_constructed_with_a_balance_that_contradicts_its_entries() {
        val declared = AccountRecord::class.java.declaredConstructors.single { it.parameterTypes.size == 3 }
        assertFalse(Modifier.isPublic(declared.modifiers))
    }

    @Test
    fun copy_is_not_a_way_to_move_the_balance_without_the_history() {
        val generatedCopy = AccountRecord::class.java.declaredMethods.single { it.name == "copy" }
        assertFalse(Modifier.isPublic(generatedCopy.modifiers))
    }

    private fun account(): Account = Account(AccountId.generate(), UserId(Uuid.random()), usd)

    private fun entry(type: TransactionType, minorUnits: Long): LedgerEntry = LedgerEntry(
        transactionId = TransactionId.generate(),
        accountId = AccountId.generate(),
        amount = Money.ofMinorUnits(minorUnits, usd),
        type = type,
        occurredAt = Instant.EPOCH,
        recordedAt = Instant.EPOCH,
    )
}
