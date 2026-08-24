package banking.domain

import java.math.BigDecimal
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
    fun allows_a_negative_amount_because_money_is_signed() {
        val owed = Money.ofMinorUnits(amountMinor = -1, currency = usd)
        assertEquals(BigInteger.valueOf(-1), owed.amountMinor)
        assertTrue(owed < Money.ofMinorUnits(0, usd))
    }

    @Test
    fun accepts_zero() {
        val zero = Money.ofMinorUnits(amountMinor = 0, currency = usd)
        assertEquals(BigInteger.ZERO, zero.amountMinor)
        assertEquals(usd, zero.currency)
    }

    @Test
    fun adds_same_currency() {
        val sum = Money.ofMinorUnits(40, usd) + Money.ofMinorUnits(15, usd)
        assertEquals(Money.ofMinorUnits(55, usd), sum)
    }

    @Test
    fun refuses_to_add_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money.ofMinorUnits(10, usd) + Money.ofMinorUnits(10, eur)
        }
    }

    @Test
    fun subtracts_same_currency() {
        val difference = Money.ofMinorUnits(40, usd) - Money.ofMinorUnits(15, usd)
        assertEquals(Money.ofMinorUnits(25, usd), difference)
    }

    @Test
    fun subtracts_past_zero_into_a_negative_amount() {
        assertEquals(
            Money.ofMinorUnits(-1, usd),
            Money.ofMinorUnits(10, usd) - Money.ofMinorUnits(11, usd),
        )
    }

    @Test
    fun a_negative_and_a_positive_amount_sum_back_to_zero() {
        val debit = Money.ofMinorUnits(-250, usd)
        val credit = Money.ofMinorUnits(250, usd)
        assertEquals(Money.ofMinorUnits(0, usd), debit + credit)
    }

    @Test
    fun refuses_to_subtract_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money.ofMinorUnits(10, usd) - Money.ofMinorUnits(1, eur)
        }
    }

    @Test
    fun compare_orders_same_currency() {
        assertTrue(Money.ofMinorUnits(1, usd) < Money.ofMinorUnits(2, usd))
        assertTrue(Money.ofMinorUnits(2, usd) > Money.ofMinorUnits(1, usd))
        assertEquals(0, Money.ofMinorUnits(7, usd).compareTo(Money.ofMinorUnits(7, usd)))
    }

    @Test
    fun refuses_to_compare_different_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Money.ofMinorUnits(1, usd).compareTo(Money.ofMinorUnits(1, eur))
        }
    }

    @Test
    fun major_units_convert_to_minor_units_for_a_two_decimal_currency() {
        assertEquals(Money.ofMinorUnits(500, usd), Money.ofMajorUnits(BigDecimal("5.00"), usd))
        assertEquals(Money.ofMinorUnits(1_234, usd), Money.ofMajorUnits(BigDecimal("12.34"), usd))
    }

    @Test
    fun major_units_accept_fewer_decimals_than_the_currency_allows() {
        assertEquals(Money.ofMinorUnits(500, usd), Money.ofMajorUnits(BigDecimal("5"), usd))
        assertEquals(Money.ofMinorUnits(500, usd), Money.ofMajorUnits(BigDecimal("5.0"), usd))
    }

    @Test
    fun major_units_respect_a_zero_decimal_currency() {
        val jpy = Currency.getInstance("JPY")
        assertEquals(Money.ofMinorUnits(500, jpy), Money.ofMajorUnits(BigDecimal("500"), jpy))
    }

    @Test
    fun major_units_respect_a_three_decimal_currency() {
        val kwd = Currency.getInstance("KWD")
        assertEquals(Money.ofMinorUnits(5_000, kwd), Money.ofMajorUnits(BigDecimal("5.000"), kwd))
    }

    @Test
    fun major_units_refuse_precision_finer_than_the_currency() {
        assertFailsWith<ArithmeticException> {
            Money.ofMajorUnits(BigDecimal("5.005"), usd)
        }
    }

    @Test
    fun major_units_carry_a_negative_amount_through() {
        assertEquals(Money.ofMinorUnits(-1, usd), Money.ofMajorUnits(BigDecimal("-0.01"), usd))
    }

    @Test
    fun major_units_refuse_a_currency_without_minor_units() {
        val gold = Currency.getInstance("XAU")
        val rejected = assertFailsWith<IllegalArgumentException> {
            Money.ofMajorUnits(BigDecimal("1"), gold)
        }
        assertEquals("XAU has no minor unit; use ofMinorUnits", rejected.message)
    }

    @Test
    fun adds_amounts_larger_than_a_long() {
        val largerThanLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        val huge = Money.ofMinorUnits(amountMinor = largerThanLong, currency = usd)
        val sum = huge + Money.ofMinorUnits(amountMinor = BigInteger.ONE, currency = usd)
        assertEquals(largerThanLong.add(BigInteger.ONE), sum.amountMinor)
    }
}
