package banking.domain

import java.time.Instant
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LedgerEntryTest {
    private val usd: Currency = Currency.getInstance("USD")

    @Test
    fun credits_carry_a_positive_signed_amount() {
        assertEquals(money(500), entry(TransactionType.DEPOSIT).signedAmount)
        assertEquals(money(500), entry(TransactionType.TRANSFER_IN).signedAmount)
    }

    @Test
    fun debits_carry_a_negative_signed_amount() {
        assertEquals(money(-500), entry(TransactionType.WITHDRAWAL).signedAmount)
        assertEquals(money(-500), entry(TransactionType.TRANSFER_OUT).signedAmount)
    }

    private fun money(minorUnits: Long): Money = Money.ofMinorUnits(minorUnits, usd)

    private fun entry(type: TransactionType): LedgerEntry = LedgerEntry(
        transactionId = TransactionId.generate(),
        accountId = AccountId.generate(),
        amount = money(500),
        type = type,
        occurredAt = Instant.EPOCH,
        recordedAt = Instant.EPOCH,
    )
}
