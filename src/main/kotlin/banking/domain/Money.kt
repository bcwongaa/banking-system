package banking.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Currency

@ConsistentCopyVisibility
data class Money private constructor(
    val amountMinor: BigInteger, // The base unit of a currency, for example USD is cents.
    val currency: Currency,
) : Comparable<Money> {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor.add(other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor.subtract(other.amountMinor), currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.currencyCode} vs ${other.currency.currencyCode}"
        }
    }

    companion object {
        fun ofMinorUnits(amountMinor: Long, currency: Currency): Money =
            Money(amountMinor.toBigInteger(), currency)

        fun ofMinorUnits(amountMinor: BigInteger, currency: Currency): Money =
            Money(amountMinor, currency)

        // setScale without a RoundingMode throws rather than silently rounding money away
        fun ofMajorUnits(amountMajor: BigDecimal, currency: Currency): Money {
            val minorUnitDigits = currency.defaultFractionDigits
            require(minorUnitDigits >= 0) {
                "${currency.currencyCode} has no minor unit; use ofMinorUnits"
            }
            return Money(amountMajor.setScale(minorUnitDigits).unscaledValue(), currency)
        }
    }
}
