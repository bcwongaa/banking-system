package banking.domain

import java.math.BigInteger
import java.util.Currency

data class Money(
    val amountMinor: BigInteger,
    val currency: Currency,
) : Comparable<Money> {
    constructor(amountMinor: Long, currency: Currency) : this(amountMinor.toBigInteger(), currency)

    init {
        require(amountMinor.signum() >= 0) { "Money amount must be non-negative" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = amountMinor.add(other.amountMinor))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        require(amountMinor >= other.amountMinor) { "Money amount must be non-negative" }
        return copy(amountMinor = amountMinor.subtract(other.amountMinor))
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
}
