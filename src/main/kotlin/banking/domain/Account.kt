package banking.domain

import java.util.Currency

data class Account(
    val accountId: AccountId,
    val userId: UserId,
    val currency: Currency,
)
