package banking.domain

import java.math.BigInteger
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    private val usd: Currency = Currency.getInstance("USD")
    private val eur: Currency = Currency.getInstance("EUR")

    @Test
    fun rejects_negative_minor_units() {
        assertFailsWith<IllegalArgumentException> {
            Money(amountMinor = -1, currency = usd)
        }
    }

    @Test
    fun accepts_zero() {
        val zero = Money(amountMinor = 0, currency = usd)
        assertEquals(BigInteger.ZERO, zero.amountMinor)
        assertEquals(usd, zero.currency)
    }

    @Test
    fun adds_same_currency() {
        val sum = Money(40, usd) + Money(15, usd)
        assertEquals(Money(55, usd), sum)
    }

    @Test
    fun refuses_to_add_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money(10, usd) + Money(10, eur)
        }
    }

    @Test
    fun subtracts_same_currency() {
        val difference = Money(40, usd) - Money(15, usd)
        assertEquals(Money(25, usd), difference)
    }

    @Test
    fun refuses_to_subtract_past_zero() {
        assertFailsWith<IllegalArgumentException> {
            Money(10, usd) - Money(11, usd)
        }
    }

    @Test
    fun refuses_to_subtract_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money(10, usd) - Money(1, eur)
        }
    }

    @Test
    fun compare_orders_same_currency() {
        assertTrue(Money(1, usd) < Money(2, usd))
        assertTrue(Money(2, usd) > Money(1, usd))
        assertEquals(0, Money(7, usd).compareTo(Money(7, usd)))
    }

    @Test
    fun refuses_to_compare_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money(1, usd).compareTo(Money(1, eur))
        }
    }

    @Test
    fun adds_amounts_larger_than_a_long() {
        val largerThanLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        val huge = Money(amountMinor = largerThanLong, currency = usd)
        val sum = huge + Money(amountMinor = BigInteger.ONE, currency = usd)
        assertEquals(largerThanLong.add(BigInteger.ONE), sum.amountMinor)
    }
}
